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
    constructor(context: Context) : this(context, RmfDatabase.getInstance(context))
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
                    updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
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

            // 清理 schedules 中的本地 dirty 标记
            try {
                database.openHelper.writableDatabase.execSQL("UPDATE schedules SET is_dirty = 0 WHERE is_dirty = 1;")
            } catch (_: Exception) {}

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
            GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.SUCCESS, "云端已保存新快照", nowStr)
            return@withContext SyncResult.Success("已成功保存云端时间戳版本「$newBackupFileName」！", nowStr)
        } catch (e: Exception) {
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

    private data class MergeTableDef(
        val tableName: String,
        val pkCol: String,
        val tsCol: String?
    )

    private val MERGE_TABLES = listOf(
        MergeTableDef("goals", "id", "created_at"),
        MergeTableDef("study_topics", "id", "updated_at"),
        MergeTableDef("schedules", "id", "updated_at"),
        MergeTableDef("courses", "id", "updated_at"),
        MergeTableDef("course_adjustments", "id", "updated_at"),
        MergeTableDef("interruptions", "id", "timestamp"),
        MergeTableDef("expenses", "id", "created_at"),
        MergeTableDef("daily_reports", "date", "updated_at"),
        MergeTableDef("schedule_tags", "id", "created_at"),
        MergeTableDef("fitness_plans", "id", "updated_at"),
        MergeTableDef("fitness_records", "id", "updated_at"),
        MergeTableDef("habits", "id", "updated_at"),
        MergeTableDef("countdowns", "id", "updated_at"),
        MergeTableDef("reminders", "id", "updated_at"),
        MergeTableDef("notes", "id", "updated_at"),
        MergeTableDef("time_markers", "id", "updated_at"),
        MergeTableDef("focus_records", "id", "start_time"),
        MergeTableDef("timetable_settings", "key", null)
    )

    /**
     * 核心行级时间戳双向增量合并引擎 (Bi-directional Merge Engine):
     * 1. 采用 SQLite ATTACH DATABASE 机制，零全量暴力替换，零死锁无损对齐。
     * 2. 云端新增记录无损合入本地 (INSERT OR IGNORE)。
     * 3. 冲突记录基于更新时间戳 LWW (Last-Write-Wins) 判定，保留更新的一方。
     * 4. 若本地包含离线新记录或更新的修改，标记 hasLocalUnpushed = true 并反向同步，实现全数据链无缝自愈收敛！
     */
    private fun performBiDirectionalMerge(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        tempDbPath: String
    ): Boolean {
        var hasLocalUnpushed = false
        val escapedPath = tempDbPath.replace("'", "''")

        try {
            db.execSQL("DETACH DATABASE remote_db;")
        } catch (_: Exception) {}

        db.execSQL("ATTACH DATABASE '$escapedPath' AS remote_db;")

        try {
            db.beginTransaction()
            try {
                db.execSQL("PRAGMA foreign_keys = OFF;")

                for (def in MERGE_TABLES) {
                    val table = def.tableName
                    val pk = def.pkCol
                    val ts = def.tsCol

                    val mainExists = db.query(
                        "SELECT count(*) FROM main.sqlite_master WHERE type='table' AND name=?",
                        arrayOf(table)
                    ).use { it.moveToFirst() && it.getInt(0) > 0 }

                    val remoteExists = db.query(
                        "SELECT count(*) FROM remote_db.sqlite_master WHERE type='table' AND name=?",
                        arrayOf(table)
                    ).use { it.moveToFirst() && it.getInt(0) > 0 }

                    if (!mainExists || !remoteExists) continue

                    val mainCols = mutableListOf<String>()
                    db.query("PRAGMA main.table_info('$table')").use { cursor ->
                        while (cursor.moveToNext()) {
                            mainCols.add(cursor.getString(1))
                        }
                    }

                    val remoteCols = mutableSetOf<String>()
                    db.query("PRAGMA remote_db.table_info('$table')").use { cursor ->
                        while (cursor.moveToNext()) {
                            remoteCols.add(cursor.getString(1).lowercase())
                        }
                    }

                    val commonCols = mainCols.filter { remoteCols.contains(it.lowercase()) }
                    val actualPk = commonCols.firstOrNull { it.equals(pk, ignoreCase = true) } ?: continue
                    val actualTs = if (ts != null) commonCols.firstOrNull { it.equals(ts, ignoreCase = true) } else null

                    val colListSql = commonCols.joinToString(", ") { "`$it`" }

                    // 检测本地是否有远程不存在的孤立新行
                    val localOnlyCount = db.query(
                        "SELECT COUNT(*) FROM main.`$table` m WHERE NOT EXISTS (SELECT 1 FROM remote_db.`$table` r WHERE r.`$actualPk` = m.`$actualPk`)"
                    ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

                    if (localOnlyCount > 0) {
                        hasLocalUnpushed = true
                    }

                    // 检测本地是否包含更新的离线修改
                    if (actualTs != null) {
                        val localNewerCount = db.query(
                            "SELECT COUNT(*) FROM main.`$table` m JOIN remote_db.`$table` r ON m.`$actualPk` = r.`$actualPk` WHERE (m.`$actualTs` > r.`$actualTs` OR ((r.`$actualTs` IS NULL OR r.`$actualTs` = '') AND (m.`$actualTs` IS NOT NULL AND m.`$actualTs` != '')))"
                        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

                        if (localNewerCount > 0) {
                            hasLocalUnpushed = true
                        }
                    }

                    // 1. 将远程新增行无损合入本地 (INSERT OR IGNORE)
                    db.execSQL("""
                        INSERT OR IGNORE INTO main.`$table` ($colListSql)
                        SELECT $colListSql FROM remote_db.`$table` r
                        WHERE NOT EXISTS (
                            SELECT 1 FROM main.`$table` m WHERE m.`$actualPk` = r.`$actualPk`
                        );
                    """.trimIndent())

                    // 2. 将远程时间戳更新的行更新覆盖本地行 (INSERT OR REPLACE)
                    if (actualTs != null) {
                        db.execSQL("""
                            INSERT OR REPLACE INTO main.`$table` ($colListSql)
                            SELECT $colListSql FROM remote_db.`$table` r
                            WHERE r.`$actualPk` IN (
                                SELECT r2.`$actualPk`
                                FROM remote_db.`$table` r2
                                JOIN main.`$table` m2 ON r2.`$actualPk` = m2.`$actualPk`
                                WHERE (r2.`$actualTs` > m2.`$actualTs` OR ((m2.`$actualTs` IS NULL OR m2.`$actualTs` = '') AND (r2.`$actualTs` IS NOT NULL AND r2.`$actualTs` != '')))
                            );
                        """.trimIndent())
                    } else if (table == "timetable_settings") {
                        db.execSQL("""
                            INSERT OR REPLACE INTO main.`$table` ($colListSql)
                            SELECT $colListSql FROM remote_db.`$table`;
                        """.trimIndent())
                    }
                }

                // 3. 更新快照版本元数据
                val syncDao = database.syncDao()
                val curMeta = syncDao.getMetadata() ?: SnapshotMetadataEntity()
                syncDao.saveMetadata(curMeta.copy(
                    versionCounter = curMeta.versionCounter + 1,
                    updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                ))

                db.execSQL("PRAGMA foreign_keys = ON;")
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } finally {
            try {
                db.execSQL("DETACH DATABASE remote_db;")
            } catch (_: Exception) {}
        }

        return hasLocalUnpushed
    }

    /**
     * 从 Google Drive 专属同步文件夹检索并拉取最新时间戳备份数据库，执行行级双向增量合并 (LWW)
     */
    suspend fun downloadBackupFromDrive(
        onProgress: ((String) -> Unit)? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        GoogleDriveAutoSyncManager.isRestoring = true
        GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.SYNCING, "正在拉取云端快照...")
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

            onProgress?.invoke("正在检索云端专属目录最新镜像...")
            val folderId = ensureBackupFolder(driveService)

            val listReq = driveService.files().list()
                .setQ("'$folderId' in parents and trashed = false and (name contains 'rmf_backup_' or name = '$BACKUP_FILE_NAME')")
                .setSpaces("drive")
                .setFields("files(id, name, createdTime, modifiedTime, size)")
                .setOrderBy("createdTime desc")

            val searchRes = listReq.execute()
            val remoteFile = searchRes.files?.sortedByDescending { it.name }?.firstOrNull()
                ?: run {
                    val err = "在 Google Drive 对应目录中未发现可还原的备份文件"
                    GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.ERROR, err, error = err)
                    return@withContext SyncResult.Error(err)
                }

            onProgress?.invoke("正在拉取最新云端快照 (${remoteFile.name})...")
            FileOutputStream(tempFile).use { output ->
                driveService.files().get(remoteFile.id).executeMediaAndDownloadTo(output)
            }

            if (tempFile.length() < 100) {
                tempFile.delete()
                val err = "云端下载的快照文件异常或为空"
                GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.ERROR, err, error = err)
                return@withContext SyncResult.Error(err)
            }

            // 1. 校验下载数据库文件完整性
            onProgress?.invoke("正在校验云端快照完整性...")
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
                    val err = "云端下载的数据库完整性检验未通过，已取消合并"
                    GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.ERROR, err, error = err)
                    return@withContext SyncResult.Error(err)
                }
            } catch (e: Exception) {
                tempFile.delete()
                val err = "校验云端数据库失败: ${e.message}"
                GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.ERROR, err, error = err)
                return@withContext SyncResult.Error(err)
            }

            // 2. 执行行级时间戳双向增量合并 (Bi-directional Merge)
            onProgress?.invoke("正在执行核心数据表行级时间戳双向增量合并...")
            val writableDb = database.openHelper.writableDatabase
            val hasLocalUnpushed = performBiDirectionalMerge(writableDb, tempFile.absolutePath)

            // 3. 合并完毕，立即清理下载的临时数据库文件
            tempFile.delete()
            cleanupLocalBackupFiles(context)

            // 4. 刷新 Room 观察者，促使所有数据流实时重绘
            try {
                database.invalidationTracker.refreshVersionsAsync()
            } catch (_: Exception) {}

            // 5. 同步云端数据库中的学期设置至本地 TimetablePreferences
            TimetablePreferences.syncFromDatabase(context, database.syncDao())

            // 6. 将全学期课程重新映射投影至日程事件中（让课程在日程画布中立即可见）
            if (TimetablePreferences.isSemesterConfigured(context)) {
                TimetableProjectionService.syncAllToSchedule(
                    context = context,
                    courseDao = database.courseDao(),
                    scheduleDao = database.scheduleDao()
                )
            }

            // 7. 重新调度未来课程上课前通知提醒与全量待触发事件闹钟
            CourseReminderScheduler.scheduleUpcomingCourseReminders(
                context = context,
                courseDao = database.courseDao(),
                daysAhead = 7
            )
            ScheduleReminderManager.rescheduleAll(context)
            try {
                com.renly.rmf.ui.widget.RmfWidgetManager.refreshAllWidgets(context)
            } catch (_: Exception) {}

            val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            GoogleDrivePreferences.setLastSyncTime(context, nowStr)

            // 8. 若本地包含离线未推送的较新变更，自动反向推送合并后的完整快照至云端，实现多端收敛无损
            if (hasLocalUnpushed) {
                onProgress?.invoke("检测到本地包含离线新变更，正在反向增量合并推送至云端...")
                GoogleDriveAutoSyncManager.requestAutoSync(context)
            }

            val statusMsg = if (hasLocalUnpushed) "双向合并完成 (已同步本地新变更)" else "已与云端最新快照保持一致"
            GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.SUCCESS, statusMsg, nowStr)

            return@withContext SyncResult.Success("已成功从云端完成双向增量合并「${remoteFile.name}」！全量数据已实时对齐生效。", nowStr)
        } catch (e: Exception) {
            val err = "拉取合并失败: ${e.localizedMessage}"
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
     * 静默/前台自动双向轻检测与拉取合并：
     * 校验云端镜像时间戳与本地 LastSyncTime，若云端版本更新则自动在后台拉取并执行行级双向增量合并。
     * 支持 force 参数强制触发一次完整云端轻检测与合并。
     */
    suspend fun pullLatestBackupIfNewer(force: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        if (!GoogleDrivePreferences.isAuthorized(context)) {
            GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.IDLE, "未连接")
            return@withContext SyncResult.Error("Google Drive 未授权")
        }

        try {
            GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.SYNCING, "正在检查云端快照更新...")
            val driveRes = getDriveService()
            if (driveRes.isFailure) {
                val err = "连接 Drive 失败: ${driveRes.exceptionOrNull()?.message}"
                GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.ERROR, err, error = err)
                return@withContext SyncResult.Error(err)
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
                ?: run {
                    val err = "云端暂无备份文件"
                    GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.ERROR, err, error = err)
                    return@withContext SyncResult.Error(err)
                }

            val lastSync = GoogleDrivePreferences.getLastSyncTime(context)
            val remoteTimestamp = extractTimestampFromBackupName(remoteFile.name)

            val shouldPull = if (force) {
                true
            } else if (remoteTimestamp != null && lastSync.isNotBlank() && !lastSync.contains("未同步")) {
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
                GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.SUCCESS, "数据已是最新", lastSync)
                return@withContext SyncResult.Success("本地数据已是最新，无需拉取", lastSync)
            }
        } catch (e: Exception) {
            val err = "云端拉取检测异常: ${e.message}"
            GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.ERROR, err, error = err)
            return@withContext SyncResult.Error(err)
        }
    }

    private fun extractTimestampFromBackupName(name: String): String? {
        val regex = Regex("""rmf_backup_(\d{4})(\d{2})(\d{2})_(\d{2})(\d{2})(\d{2})\.db""")
        val match = regex.find(name) ?: return null
        val (y, m, d, h, min, s) = match.destructured
        return "$y-$m-$d $h:$min:$s"
    }
}
