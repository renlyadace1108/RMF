package com.renly.rmf.domain.service

import android.content.Context
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.renly.rmf.data.local.RmfDatabase
import com.renly.rmf.data.local.entity.SnapshotMetadataEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

sealed class SyncResult {
    data class Success(val message: String, val timestamp: String) : SyncResult()
    data class Error(val error: String) : SyncResult()
}

/**
 * Android 端自动云端同步中枢：
 * 1. 每次数据产生增删改时，防抖3秒自动向 Google Drive 专属同步文件夹上传一个以时间戳命名的数据快照版本。
 * 2. 严格贯彻“本地就一个版本”的极简安全哲学，用毕即焚，无任何残留 .bak 堆积。
 */
object GoogleDriveAutoSyncManager {
    @Volatile
    var isRestoring: Boolean = false

    @Volatile
    private var hasPendingChange = false

    private var debounceJob: kotlinx.coroutines.Job? = null
    private val syncMutex = kotlinx.coroutines.sync.Mutex()
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    fun requestAutoSync(context: Context) {
        val appContext = context.applicationContext
        if (isRestoring || !GoogleDrivePreferences.isAuthorized(appContext)) {
            return
        }
        hasPendingChange = true
        debounceJob?.cancel()
        debounceJob = scope.launch {
            kotlinx.coroutines.delay(3000L) // 3秒防抖
            if (isRestoring || !hasPendingChange) return@launch

            syncMutex.withLock {
                if (isRestoring || !hasPendingChange) return@withLock
                hasPendingChange = false
                try {
                    val db = RmfDatabase.getInstance(appContext)
                    val syncService = GoogleDriveSyncService(appContext, db)
                    syncService.uploadBackupToDrive()
                } catch (e: Exception) {
                    android.util.Log.w("GoogleDriveAutoSync", "Auto upload error: ${e.message}")
                }
            }
        }
    }
}

class GoogleDriveSyncService(
    private val context: Context,
    private val database: RmfDatabase
) {
    companion object {
        const val APPLICATION_NAME = "Renly Management Platform"
        const val BACKUP_FOLDER_NAME = "RMF_CloudSync"
        const val BACKUP_FILE_NAME = "rmf_cloud_backup.db"
        const val MAX_CLOUD_RETENTION_VERSIONS = 50
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar.events"
        const val COMBINED_SCOPES = "https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/calendar.events"
        const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"

        /**
         * 清理本地所有历史备份与临时快照文件，严格保证“本地就一个版本 (rmf.db)”
         */
        fun cleanupLocalBackupFiles(context: Context) {
            try {
                context.cacheDir.listFiles { _, name ->
                    name.startsWith("rmf_") && (name.endsWith(".bak") || name.endsWith(".tmp") || name.endsWith(".db"))
                }?.forEach { it.delete() }
                val dbDir = context.getDatabasePath(RmfDatabase.DATABASE_NAME).parentFile
                dbDir?.listFiles { _, name ->
                    name.startsWith("rmf_pre_") || name.startsWith("rmf_backup_") || name.endsWith(".bak")
                }?.forEach { it.delete() }
            } catch (_: Exception) {}
        }

        /**
         * 生成供用户浏览器/WebView 授权的 URL
         */
        fun buildAuthorizationUrl(
            clientId: String,
            redirectUri: String = "urn:ietf:wg:oauth:2.0:oob",
            scope: String = COMBINED_SCOPES
        ): String {
            val encodedScope = URLEncoder.encode(scope, "UTF-8")
            val encodedRedirect = URLEncoder.encode(redirectUri, "UTF-8")
            val encodedClient = URLEncoder.encode(clientId.trim(), "UTF-8")
            return "https://accounts.google.com/o/oauth2/v2/auth?" +
                    "client_id=$encodedClient" +
                    "&redirect_uri=$encodedRedirect" +
                    "&response_type=code" +
                    "&scope=$encodedScope" +
                    "&access_type=offline" +
                    "&prompt=consent"
        }

        /**
         * 获取当前可用的 Access Token（必要时自动使用 Refresh Token 刷新）
         */
        suspend fun getValidAccessToken(context: Context): Result<String> = withContext(Dispatchers.IO) {
            val clientId = GoogleDrivePreferences.getClientId(context)
            val clientSecret = GoogleDrivePreferences.getClientSecret(context)
            var accessToken = GoogleDrivePreferences.getAccessToken(context)
            val refreshToken = GoogleDrivePreferences.getRefreshToken(context)

            if (accessToken.isBlank() && refreshToken.isBlank()) {
                return@withContext Result.failure(Exception("尚未授权 Google 账户，请先完成授权。"))
            }

            if ((accessToken.isBlank() || GoogleDrivePreferences.isTokenExpired(context)) && refreshToken.isNotBlank()) {
                val refreshRes = refreshAccessToken(clientId, clientSecret, refreshToken)
                if (refreshRes.isSuccess) {
                    accessToken = refreshRes.getOrThrow()
                    GoogleDrivePreferences.setAccessToken(context, accessToken)
                } else if (accessToken.isBlank()) {
                    return@withContext Result.failure(Exception("Access Token 刷新失败: ${refreshRes.exceptionOrNull()?.message}"))
                }
            }
            Result.success(accessToken)
        }

        /**
         * 使用 Authorization Code 换取 Access Token 与 Refresh Token
         */
        suspend fun exchangeAuthCode(
            clientId: String,
            clientSecret: String,
            authCode: String,
            redirectUri: String = "urn:ietf:wg:oauth:2.0:oob"
        ): Result<Pair<String, String?>> = withContext(Dispatchers.IO) {
            try {
                val url = URL(TOKEN_ENDPOINT)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val postParams = "code=" + URLEncoder.encode(authCode.trim(), "UTF-8") +
                        "&client_id=" + URLEncoder.encode(clientId.trim(), "UTF-8") +
                        "&client_secret=" + URLEncoder.encode(clientSecret.trim(), "UTF-8") +
                        "&redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8") +
                        "&grant_type=authorization_code"

                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(postParams)
                    writer.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    val responseStr = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                    val json = JsonParser.parseString(responseStr).asJsonObject
                    val accessToken = json.get("access_token").asString
                    val refreshToken = if (json.has("refresh_token")) json.get("refresh_token").asString else null
                    Result.success(Pair(accessToken, refreshToken))
                } else {
                    val errorStr = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream)).use { it.readText() }
                    Result.failure(Exception("获取 Token 失败 ($responseCode): $errorStr"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        /**
         * 使用 Refresh Token 刷新 Access Token
         */
        suspend fun refreshAccessToken(
            clientId: String,
            clientSecret: String,
            refreshToken: String
        ): Result<String> = withContext(Dispatchers.IO) {
            try {
                val url = URL(TOKEN_ENDPOINT)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val postParams = "client_id=" + URLEncoder.encode(clientId.trim(), "UTF-8") +
                        "&client_secret=" + URLEncoder.encode(clientSecret.trim(), "UTF-8") +
                        "&refresh_token=" + URLEncoder.encode(refreshToken.trim(), "UTF-8") +
                        "&grant_type=refresh_token"

                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(postParams)
                    writer.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    val responseStr = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                    val json = JsonParser.parseString(responseStr).asJsonObject
                    val newAccessToken = json.get("access_token").asString
                    Result.success(newAccessToken)
                } else {
                    val errorStr = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream)).use { it.readText() }
                    Result.failure(Exception("刷新 Token 失败 ($responseCode): $errorStr"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getValidAccessToken(): Result<String> = getValidAccessToken(context)

    /**
     * 获取或刷新合法的 Drive 客户端实例
     */
    suspend fun getDriveService(): Result<Drive> = withContext(Dispatchers.IO) {
        val tokenRes = getValidAccessToken()
        if (tokenRes.isFailure) {
            return@withContext Result.failure(tokenRes.exceptionOrNull() ?: Exception("尚未授权 Google Drive 账户，请先完成授权。"))
        }
        val accessToken = tokenRes.getOrThrow()

        try {
            val credential = GoogleCredential().setAccessToken(accessToken)
            val drive = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName(APPLICATION_NAME).build()
            Result.success(drive)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 确保 Google Drive 根目录存在 RMF_CloudSync 文件夹
     */
    suspend fun ensureBackupFolder(driveService: Drive): String = withContext(Dispatchers.IO) {
        val listReq = driveService.files().list()
            .setQ("mimeType = 'application/vnd.google-apps.folder' and name = '$BACKUP_FOLDER_NAME' and trashed = false")
            .setSpaces("drive")
            .setFields("files(id, name)")

        val fileList = listReq.execute()
        if (!fileList.files.isNullOrEmpty()) {
            return@withContext fileList.files[0].id
        }

        val folderMetadata = File().apply {
            name = BACKUP_FOLDER_NAME
            mimeType = "application/vnd.google-apps.folder"
        }
        val folder = driveService.files().create(folderMetadata).setFields("id").execute()
        return@withContext folder.id
    }

    /**
     * 上传本地 rmf.db 数据库快照至 Google Drive 专属同步文件夹（以时间戳格式独立命名版本）
     */
    suspend fun uploadBackupToDrive(
        onProgress: ((String) -> Unit)? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        val snapshotTemp = java.io.File(context.cacheDir, "rmf_upload_temp_${System.currentTimeMillis()}.db")
        try {
            onProgress?.invoke("正在连接 Google Drive API 并校验凭据...")
            val driveRes = getDriveService()
            if (driveRes.isFailure) {
                return@withContext SyncResult.Error(driveRes.exceptionOrNull()?.message ?: "初始化 Google Drive 客户端失败")
            }
            val driveService = driveRes.getOrThrow()

            onProgress?.invoke("正在校验并定位云端同步目录...")
            val folderId = ensureBackupFolder(driveService)

            // 1. 更新本地快照物理版本计数器
            val syncDao = database.syncDao()
            val currentMeta = syncDao.getMetadata() ?: SnapshotMetadataEntity()
            val newCounter = currentMeta.versionCounter + 1
            val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            syncDao.saveMetadata(
                currentMeta.copy(
                    deviceId = android.os.Build.MODEL,
                    versionCounter = newCounter,
                    updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                )
            )

            // 2. 获取本地 rmf.db 物理文件并生成安全只读快照
            val dbFile = context.getDatabasePath(RmfDatabase.DATABASE_NAME)
            if (!dbFile.exists()) {
                return@withContext SyncResult.Error("本地数据库文件不存在")
            }

            // 强制 SQLite 刷盘 checkpoint (WAL -> 主库文件)
            try {
                database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE);").close()
            } catch (_: Exception) {}

            dbFile.copyTo(snapshotTemp, overwrite = true)
            val mediaContent = FileContent("application/x-sqlite3", snapshotTemp)

            // 3. 生成带时间戳的专属快照文件名
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val newBackupFileName = "rmf_backup_$timestamp.db"

            onProgress?.invoke("正在上传时间戳快照版本 ($newBackupFileName)...")
            val createMeta = File().apply {
                name = newBackupFileName
                parents = listOf(folderId)
                description = "RMF Android Database Snapshot ($nowStr)"
            }
            driveService.files().create(createMeta, mediaContent).execute()

            // 4. 云端保留策略维护：保留最新的 50 个时间戳版本，清理超额快照
            try {
                val pruneReq = driveService.files().list()
                    .setQ("'$folderId' in parents and trashed = false and name contains 'rmf_backup_'")
                    .setSpaces("drive")
                    .setPageSize(100)
                    .setFields("files(id, name, createdTime)")
                    .setOrderBy("name asc")
                val pruneRes = pruneReq.execute()
                if (pruneRes.files != null && pruneRes.files.size > MAX_CLOUD_RETENTION_VERSIONS) {
                    val sortedToPrune = pruneRes.files.sortedBy { it.name }
                    val toDelete = sortedToPrune.size - MAX_CLOUD_RETENTION_VERSIONS
                    for (i in 0 until toDelete) {
                        try {
                            driveService.files().delete(sortedToPrune[i].id).execute()
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}

            GoogleDrivePreferences.setLastSyncTime(context, nowStr)
            return@withContext SyncResult.Success("已成功保存云端时间戳版本「$newBackupFileName」！", nowStr)
        } catch (e: Exception) {
            return@withContext SyncResult.Error("云端上传失败: ${e.localizedMessage}")
        } finally {
            if (snapshotTemp.exists()) {
                try { snapshotTemp.delete() } catch (_: Exception) {}
            }
            cleanupLocalBackupFiles(context)
        }
    }

    /**
     * 从 Google Drive 专属同步文件夹检索并拉取最新时间戳备份数据库进行无损还原
     */
    suspend fun downloadBackupFromDrive(
        onProgress: ((String) -> Unit)? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        GoogleDriveAutoSyncManager.isRestoring = true
        try {
            onProgress?.invoke("正在连接 Google Drive API 并校验凭据...")
            val driveRes = getDriveService()
            if (driveRes.isFailure) {
                return@withContext SyncResult.Error(driveRes.exceptionOrNull()?.message ?: "初始化 Google Drive 客户端失败")
            }
            val driveService = driveRes.getOrThrow()

            onProgress?.invoke("正在检索云端专属目录最新镜像...")
            val folderId = ensureBackupFolder(driveService)

            val listReq = driveService.files().list()
                .setQ("'$folderId' in parents and trashed = false and (name contains 'rmf_backup_' or name = '$BACKUP_FILE_NAME')")
                .setSpaces("drive")
                .setFields("files(id, name, createdTime, modifiedTime, size)")
                .setOrderBy("createdTime desc")

            val searchRes = listReq.execute()
            val remoteFile = searchRes.files?.sortedByDescending { it.name }?.firstOrNull()
                ?: return@withContext SyncResult.Error("在 Google Drive 对应目录中未发现可还原的备份文件")

            onProgress?.invoke("正在拉取最新云端快照 (${remoteFile.name})...")
            val tempFile = java.io.File(context.cacheDir, "rmf_cloud_temp.db")
            FileOutputStream(tempFile).use { output ->
                driveService.files().get(remoteFile.id).executeMediaAndDownloadTo(output)
            }

            if (tempFile.length() < 100) {
                tempFile.delete()
                return@withContext SyncResult.Error("云端下载的快照文件异常或为空")
            }

            // 校验完整性与替换
            onProgress?.invoke("正在校验数据完整性并合并...")
            val targetDbFile = context.getDatabasePath(RmfDatabase.DATABASE_NAME)

            // 先创建本地临时回滚副本
            val backupFile = java.io.File(context.cacheDir, "rmf_pre_restore_${System.currentTimeMillis()}.bak")
            if (targetDbFile.exists()) {
                targetDbFile.copyTo(backupFile, overwrite = true)
            }

            // 关闭当前数据库连接并重置单例
            try {
                database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE);").close()
            } catch (_: Exception) {}
            RmfDatabase.resetInstance()

            // 替换主数据库文件
            tempFile.copyTo(targetDbFile, overwrite = true)
            tempFile.delete()

            // 成功还原后立即清理临时备份，坚决保证“本地就一个版本”
            if (backupFile.exists()) {
                try { backupFile.delete() } catch (_: Exception) {}
            }
            cleanupLocalBackupFiles(context)

            // 清理 WAL 临时文件
            java.io.File(targetDbFile.path + "-wal").delete()
            java.io.File(targetDbFile.path + "-shm").delete()

            // 对拉取下来的云端/旧版数据库执行无损表结构对齐自愈
            RmfDatabase.preHealExistingDatabaseFile(context, forceMigrationReset = true)

            // 获取最新初始化的数据库连接
            val newDb = RmfDatabase.getInstance(context)

            // 1. 同步云端数据库中的学期设置至本地 TimetablePreferences
            TimetablePreferences.syncFromDatabase(context, newDb.syncDao())

            // 2. 将全学期课程重新映射投影至日程事件中（让课程在日程画布中立即可见）
            if (TimetablePreferences.isSemesterConfigured(context)) {
                TimetableProjectionService.syncAllToSchedule(
                    context = context,
                    courseDao = newDb.courseDao(),
                    scheduleDao = newDb.scheduleDao()
                )
            }

            // 3. 重新调度未来课程上课前通知提醒
            CourseReminderScheduler.scheduleUpcomingCourseReminders(
                context = context,
                courseDao = newDb.courseDao(),
                daysAhead = 7
            )

            // 4. 全量重排所有待触发事件闹钟与桌面小组件
            ScheduleReminderManager.rescheduleAll(context)
            try {
                com.renly.rmf.ui.widget.RmfWidgetManager.refreshAllWidgets(context)
            } catch (_: Exception) {}

            val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            GoogleDrivePreferences.setLastSyncTime(context, nowStr)
            return@withContext SyncResult.Success("已成功从云端恢复最新快照「${remoteFile.name}」！课表与日程已全面同步生效并重新排期提醒。", nowStr)
        } catch (e: Exception) {
            return@withContext SyncResult.Error("拉取还原失败: ${e.localizedMessage}")
        } finally {
            GoogleDriveAutoSyncManager.isRestoring = false
        }
    }

    /**
     * 冷启动无后台静默自动拉取云端数据：
     * 校验云端镜像时间戳与本地 LastSyncTime，若云端版本更新则自动在后台拉取并生效。
     */
    suspend fun pullLatestBackupIfNewer(): SyncResult = withContext(Dispatchers.IO) {
        if (!GoogleDrivePreferences.isAuthorized(context)) {
            return@withContext SyncResult.Error("Google Drive 未授权")
        }

        try {
            val driveRes = getDriveService()
            if (driveRes.isFailure) {
                return@withContext SyncResult.Error("连接 Drive 失败: ${driveRes.exceptionOrNull()?.message}")
            }
            val driveService = driveRes.getOrThrow()
            val folderId = ensureBackupFolder(driveService)

            val listReq = driveService.files().list()
                .setQ("'$folderId' in parents and trashed = false and (name contains 'rmf_backup_' or name = '$BACKUP_FILE_NAME')")
                .setSpaces("drive")
                .setFields("files(id, name, createdTime, modifiedTime, size)")
                .setOrderBy("createdTime desc")

            val searchRes = listReq.execute()
            val remoteFile = searchRes.files?.sortedByDescending { it.name }?.firstOrNull()
                ?: return@withContext SyncResult.Error("云端暂无备份文件")

            val lastSync = GoogleDrivePreferences.getLastSyncTime(context)
            val remoteTimestamp = extractTimestampFromBackupName(remoteFile.name)

            val shouldPull = if (remoteTimestamp != null && lastSync.isNotBlank() && !lastSync.contains("未同步")) {
                remoteTimestamp > lastSync
            } else if (lastSync.isBlank() || lastSync.contains("未同步")) {
                true
            } else {
                val localDb = context.getDatabasePath(RmfDatabase.DATABASE_NAME)
                val remoteEpoch = remoteFile.modifiedTime?.value ?: 0L
                remoteEpoch > (if (localDb.exists()) localDb.lastModified() else 0L)
            }

            if (shouldPull) {
                return@withContext downloadBackupFromDrive()
            } else {
                return@withContext SyncResult.Success("本地数据已是最新，无需拉取", lastSync)
            }
        } catch (e: Exception) {
            return@withContext SyncResult.Error("冷启动拉取检测异常: ${e.message}")
        }
    }

    private fun extractTimestampFromBackupName(name: String): String? {
        val regex = Regex("""rmf_backup_(\d{4})(\d{2})(\d{2})_(\d{2})(\d{2})(\d{2})\.db""")
        val match = regex.find(name) ?: return null
        val (y, m, d, h, min, s) = match.destructured
        return "$y-$m-$d $h:$min:$s"
    }
}
