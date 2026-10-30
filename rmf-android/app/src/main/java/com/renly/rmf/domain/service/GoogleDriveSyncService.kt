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

enum class CloudSyncState {
    IDLE,       // 未连接/空闲
    SYNCING,    // 正在双向同步/拉取中
    SUCCESS,    // 同步完成/已与云端保持最新 (🟢)
    ERROR       // 离线/连接超时/异常 (🟡)
}

data class SyncStatusInfo(
    val state: CloudSyncState = CloudSyncState.IDLE,
    val message: String = "未连接",
    val lastSyncTime: String = "",
    val errorDetails: String? = null
)

/**
 * 全局云端同步状态实时持有者，供 Compose 界面 (如右上角指示灯) 订阅驱动
 */
object GoogleDriveSyncStateHolder {
    private val _status = kotlinx.coroutines.flow.MutableStateFlow(SyncStatusInfo())
    val status: kotlinx.coroutines.flow.StateFlow<SyncStatusInfo> = _status

    fun updateStatus(
        state: CloudSyncState,
        message: String,
        lastSyncTime: String = "",
        error: String? = null
    ) {
        _status.value = SyncStatusInfo(state, message, lastSyncTime, error)
    }
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
        // 用户明确要求：“就只保留上传和下载两个，不要比对了”
        // 彻底关闭后台自动竞态上传，由用户完全自主通过「上传到云端」与「从云端下载」确定性操作
        return
    }
}

class GoogleDriveSyncService(
    private val context: Context,
    private val database: RmfDatabase
) {
    constructor(context: Context) : this(context, RmfDatabase.getInstance(context))
    companion object {
        const val APPLICATION_NAME = "Renly Management Platform"
        const val BACKUP_FOLDER_NAME = "RMF_CloudSync"
        const val BACKUP_FILE_NAME = "data.db"
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
                } else {
                    val errMessage = refreshRes.exceptionOrNull()?.message ?: ""
                    if (errMessage.contains("invalid_grant", ignoreCase = true)) {
                        GoogleDrivePreferences.clearAuth(context)
                        GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.IDLE, "Google 授权已过期，请重新登录")
                        return@withContext Result.failure(Exception("Google 授权凭据已过期或失效 (invalid_grant)。已自动重置本地凭据，请重新登录授权。\n\n💡 提示：若 Google Cloud 应用处于「测试 (Testing)」状态，授权每 7 天会失效。设为「发布应用 (In Production)」即可永久有效。"))
                    }
                    if (accessToken.isBlank()) {
                        return@withContext Result.failure(Exception("Access Token 刷新失败: $errMessage"))
                    }
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
        if (!com.renly.rmf.util.NetworkUtils.isNetworkAvailable(context)) {
            return@withContext SyncResult.Error("设备离线，已跳过快照上传以节省功耗")
        }
        val snapshotTemp = java.io.File(context.cacheDir, "rmf_upload_temp_${System.currentTimeMillis()}.db")
        try {
            GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.SYNCING, "正在连接云端上传新快照...")
            onProgress?.invoke("正在连接 Google Drive API 并校验凭据...")
            val driveRes = getDriveService()
            if (driveRes.isFailure) {
                val err = driveRes.exceptionOrNull()?.message ?: "初始化 Google Drive 客户端失败"
                GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.ERROR, "上传失败: $err", error = err)
                return@withContext SyncResult.Error(err)
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
                    updatedAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString()
                )
            )

            // 2. 获取本地 rmf.db 物理文件并生成安全只读快照
            val dbFile = context.getDatabasePath(RmfDatabase.DATABASE_NAME)
            if (!dbFile.exists()) {
                val err = "本地数据库文件不存在"
                GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.ERROR, err, error = err)
                return@withContext SyncResult.Error(err)
            }

            // 强制 SQLite 刷盘 checkpoint (WAL -> 主库文件)
            try {
                database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE);").close()
            } catch (_: Exception) {}

            dbFile.copyTo(snapshotTemp, overwrite = true)
            val mediaContent = FileContent("application/x-sqlite3", snapshotTemp)

            // 3. 检索云端专属目录中是否已有数据文件 data.db
            onProgress?.invoke("正在检索云端唯一数据文件 ($BACKUP_FILE_NAME)...")
            val listReq = driveService.files().list()
                .setQ("'$folderId' in parents and trashed = false")
                .setSpaces("drive")
                .setPageSize(50)
                .setFields("files(id, name)")
            val fileList = listReq.execute().files ?: emptyList()
            val existingDataFile = fileList.firstOrNull { it.name == BACKUP_FILE_NAME || it.name == "data" || it.name == "rmf_cloud_backup.db" }

            if (existingDataFile != null) {
                onProgress?.invoke("正在更新覆盖云端数据文件 ($BACKUP_FILE_NAME)...")
                val updateMeta = File().apply {
                    name = BACKUP_FILE_NAME
                    description = "RMF Database Snapshot ($nowStr)"
                }
                driveService.files().update(existingDataFile.id, updateMeta, mediaContent).execute()
            } else {
                onProgress?.invoke("正在创建并上传云端数据文件 ($BACKUP_FILE_NAME)...")
                val createMeta = File().apply {
                    name = BACKUP_FILE_NAME
                    parents = listOf(folderId)
                    description = "RMF Database Backup ($nowStr)"
                }
                driveService.files().create(createMeta, mediaContent).execute()
            }

            // 清理 schedules 中的本地 dirty 标记
            try {
                database.openHelper.writableDatabase.execSQL("UPDATE schedules SET is_dirty = 0 WHERE is_dirty = 1;")
            } catch (_: Exception) {}

            // 4. 清理云端同目录下所有旧的历史多余文件，坚决保证云端仅保留唯一的 data.db
            try {
                fileList.filter { it.id != (existingDataFile?.id) && it.name != BACKUP_FILE_NAME }.forEach {
                    try { driveService.files().delete(it.id).execute() } catch (_: Exception) {}
                }
            } catch (_: Exception) {}

            GoogleDrivePreferences.setLastSyncTime(context, nowStr)
            GoogleDrivePreferences.setLastSyncedTimestamp(context, nowStr)
            GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.SUCCESS, "云端已保存最新数据", nowStr)
            return@withContext SyncResult.Success("已成功上传并更新云端数据文件「$BACKUP_FILE_NAME」！", nowStr)
        } catch (e: Exception) {
            val isInvalidGrant = e.message?.contains("invalid_grant", ignoreCase = true) == true
            if (isInvalidGrant) {
                GoogleDrivePreferences.clearAuth(context)
                val friendly = "Google 授权已过期或失效 (invalid_grant)。已自动重置本地缓存，请重新登录授权。\n\n💡 提示：若 Google Cloud 处于测试 (Testing) 模式，授权每 7 天会过期。在 Google Cloud 控制台「OAuth 同意屏幕」中将发布状态改为「发布应用 (In Production)」即可永久有效。"
                GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.IDLE, "Google 授权已过期，请重新登录", error = friendly)
                return@withContext SyncResult.Error(friendly)
            }
            val err = "云端上传失败: ${e.localizedMessage}"
            GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.ERROR, err, error = e.localizedMessage)
            return@withContext SyncResult.Error(err)
        } finally {
            if (snapshotTemp.exists()) {
                try { snapshotTemp.delete() } catch (_: Exception) {}
            }
            cleanupLocalBackupFiles(context)
        }
    }



    /**
     * 生成当前数据库的安全快照文件（执行 WAL checkpoint 刷盘并导出副本）
     */
    fun createSafeDatabaseSnapshot(targetFile: java.io.File): Boolean {
        val dbFile = context.getDatabasePath(RmfDatabase.DATABASE_NAME)
        if (!dbFile.exists()) return false
        try {
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE);").close()
        } catch (_: Exception) {}
        dbFile.copyTo(targetFile, overwrite = true)
        return true
    }

    /**
     * 将外部 SQLite 数据库安全覆盖至本地数据库，并完成自愈对齐、视图刷新与闹钟排期
     */
    suspend fun restoreDatabaseFromFile(sourceFile: java.io.File): Boolean = withContext(Dispatchers.IO) {
        val targetDbFile = context.getDatabasePath(RmfDatabase.DATABASE_NAME)
        try {
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE);").close()
        } catch (_: Exception) {}

        RmfDatabase.resetInstance()

        val rollbackFile = java.io.File(context.cacheDir, "rmf_rollback_temp_${System.currentTimeMillis()}.db")
        if (targetDbFile.exists()) {
            targetDbFile.copyTo(rollbackFile, overwrite = true)
        }

        try {
            sourceFile.copyTo(targetDbFile, overwrite = true)
            java.io.File(targetDbFile.path + "-wal").delete()
            java.io.File(targetDbFile.path + "-shm").delete()
            rollbackFile.delete()

            RmfDatabase.preHealExistingDatabaseFile(context, forceMigrationReset = true)
        } catch (copyErr: Exception) {
            if (rollbackFile.exists()) {
                rollbackFile.copyTo(targetDbFile, overwrite = true)
                rollbackFile.delete()
            }
            throw copyErr
        }

        cleanupLocalBackupFiles(context)

        val newDb = RmfDatabase.getInstance(context)
        try {
            newDb.invalidationTracker.refreshVersionsAsync()
        } catch (_: Exception) {}

        TimetablePreferences.syncFromDatabase(context, newDb.syncDao())

        if (TimetablePreferences.isSemesterConfigured(context)) {
            try {
                TimetableProjectionService.syncAllToSchedule(
                    context = context,
                    courseDao = newDb.courseDao(),
                    scheduleDao = newDb.scheduleDao()
                )
            } catch (_: Exception) {}
        }

        try {
            CourseReminderScheduler.scheduleUpcomingCourseReminders(
                context = context,
                courseDao = newDb.courseDao(),
                daysAhead = 7
            )
            ScheduleReminderManager.rescheduleAll(context)
            com.renly.rmf.ui.widget.RmfWidgetManager.refreshAllWidgets(context)
        } catch (_: Exception) {}

        true
    }

    /**
     * 从 Google Drive 专属同步文件夹检索并拉取唯一数据文件 data.db，安全替换本地数据库
     */
    suspend fun downloadBackupFromDrive(
        onProgress: ((String) -> Unit)? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        GoogleDriveAutoSyncManager.isRestoring = true
        GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.SYNCING, "正在拉取云端数据文件...")
        val tempFile = java.io.File(context.cacheDir, "rmf_cloud_temp_${System.currentTimeMillis()}.db")
        try {
            onProgress?.invoke("正在连接 Google Drive API 并校验凭据...")
            val driveRes = getDriveService()
            if (driveRes.isFailure) {
                val err = driveRes.exceptionOrNull()?.message ?: "初始化 Google Drive 客户端失败"
                GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.ERROR, err, error = err)
                return@withContext SyncResult.Error(err)
            }
            val driveService = driveRes.getOrThrow()

            onProgress?.invoke("正在检索云端专属目录唯一数据文件...")
            val folderId = ensureBackupFolder(driveService)

            val listReq = driveService.files().list()
                .setQ("'$folderId' in parents and trashed = false")
                .setSpaces("drive")
                .setPageSize(50)
                .setFields("files(id, name, createdTime, modifiedTime, size)")

            val searchRes = listReq.execute()
            val fileList = searchRes.files ?: emptyList()
            val remoteFile = fileList.firstOrNull { it.name == BACKUP_FILE_NAME || it.name == "data" }
                ?: fileList.firstOrNull { it.name == "rmf_cloud_backup.db" }
                ?: fileList.firstOrNull { it.name.startsWith("rmf_backup_") }
                ?: fileList.firstOrNull()
                ?: run {
                    val err = "在 Google Drive 对应目录中未发现数据文件 ($BACKUP_FILE_NAME)"
                    GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.ERROR, err, error = err)
                    return@withContext SyncResult.Error(err)
                }

            onProgress?.invoke("正在拉取云端数据文件 (${remoteFile.name})...")
            FileOutputStream(tempFile).use { output ->
                driveService.files().get(remoteFile.id).executeMediaAndDownloadTo(output)
            }

            if (tempFile.length() < 100) {
                tempFile.delete()
                val err = "云端下载的数据文件异常或为空"
                GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.ERROR, err, error = err)
                return@withContext SyncResult.Error(err)
            }

            // 1. 校验下载数据库文件完整性
            onProgress?.invoke("正在校验数据文件完整性...")
            try {
                val testDb = android.database.sqlite.SQLiteDatabase.openDatabase(
                    tempFile.absolutePath,
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                )
                val cur = testDb.rawQuery("PRAGMA integrity_check;", null)
                var integrityOk = false
                if (cur.moveToFirst()) {
                    integrityOk = (cur.getString(0) == "ok")
                }
                cur.close()
                testDb.close()
                if (!integrityOk) {
                    tempFile.delete()
                    val err = "云端下载的数据库完整性检验未通过，已取消载入"
                    GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.ERROR, err, error = err)
                    return@withContext SyncResult.Error(err)
                }
            } catch (e: Exception) {
                tempFile.delete()
                val err = "校验云端数据库失败: ${e.message}"
                GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.ERROR, err, error = err)
                return@withContext SyncResult.Error(err)
            }

            // 2. 核心：直接使用云端唯一数据文件安全覆盖本地数据库
            onProgress?.invoke("正在安全载入云端最新数据文件...")
            restoreDatabaseFromFile(tempFile)

            val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            GoogleDrivePreferences.setLastSyncTime(context, nowStr)
            GoogleDrivePreferences.setLastSyncedTimestamp(context, nowStr)

            val statusMsg = "已与云端数据文件 (data.db) 保持一致"
            GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.SUCCESS, statusMsg, nowStr)

            return@withContext SyncResult.Success("已成功同步云端数据文件「${remoteFile.name}」！全量数据已完全对齐生效。", nowStr)
        } catch (e: Exception) {
            val isInvalidGrant = e.message?.contains("invalid_grant", ignoreCase = true) == true
            if (isInvalidGrant) {
                GoogleDrivePreferences.clearAuth(context)
                val friendly = "Google 授权已过期或失效 (invalid_grant)。已自动重置本地缓存，请在「设置 - Google 服务与多端互通」中重新登录授权。\n\n💡 提示：若 Google Cloud 处于测试 (Testing) 模式，授权每 7 天会过期。在 Google Cloud 控制台「OAuth 同意屏幕」中将发布状态改为「发布应用 (In Production)」即可永久有效。"
                GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.IDLE, "Google 授权已过期，请重新登录", error = friendly)
                return@withContext SyncResult.Error(friendly)
            }
            val err = "拉取同步失败: ${e.localizedMessage}"
            GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.ERROR, err, error = e.localizedMessage)
            return@withContext SyncResult.Error(err)
        } finally {
            if (tempFile.exists()) {
                try { tempFile.delete() } catch (_: Exception) {}
            }
            GoogleDriveAutoSyncManager.isRestoring = false
        }
    }

    /**
     * 极简云端下载拉取同步：直接调用从云端下载恢复唯一数据文件
     */
    suspend fun pullLatestBackupIfNewer(force: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        if (!GoogleDrivePreferences.isAuthorized(context)) {
            GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.IDLE, "未连接")
            return@withContext SyncResult.Error("Google Drive 未授权")
        }
        if (!com.renly.rmf.util.NetworkUtils.isNetworkAvailable(context)) {
            return@withContext SyncResult.Error("设备离线，已跳过云端检测以节省功耗")
        }

        return@withContext downloadBackupFromDrive()
    }
}
