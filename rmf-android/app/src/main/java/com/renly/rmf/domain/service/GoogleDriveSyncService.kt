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

class GoogleDriveSyncService(
    private val context: Context,
    private val database: RmfDatabase
) {
    companion object {
        const val APPLICATION_NAME = "Renly Management Platform"
        const val BACKUP_FOLDER_NAME = "RMF_CloudSync"
        const val BACKUP_FILE_NAME = "rmf_cloud_backup.db"
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar.events"
        const val COMBINED_SCOPES = "https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/calendar.events"
        const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"

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
     * 上传本地 rmf.db 数据库快照至 Google Drive (与 Windows 端 rmf_cloud_backup.db 完全互通)
     */
    suspend fun uploadBackupToDrive(
        onProgress: ((String) -> Unit)? = null
    ): SyncResult = withContext(Dispatchers.IO) {
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

            // 2. 检查云端现有备份文件
            onProgress?.invoke("正在检查云端现存快照版本...")
            val listReq = driveService.files().list()
                .setQ("name = '$BACKUP_FILE_NAME' and '$folderId' in parents and trashed = false")
                .setSpaces("drive")
                .setFields("files(id, name, modifiedTime)")

            val searchRes = listReq.execute()
            val existingFileId = searchRes.files?.firstOrNull()?.id

            // 3. 获取本地 rmf.db 物理文件
            val dbFile = context.getDatabasePath(RmfDatabase.DATABASE_NAME)
            if (!dbFile.exists()) {
                return@withContext SyncResult.Error("本地数据库文件不存在")
            }

            // 强制 SQLite 刷盘 checkpoint (WAL -> 主库文件)
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE);").close()

            val mediaContent = FileContent("application/x-sqlite3", dbFile)

            if (existingFileId != null) {
                onProgress?.invoke("正在覆盖更新至云端快照...")
                val updateMeta = File().apply {
                    name = BACKUP_FILE_NAME
                    description = "RMF Android Database Snapshot ($nowStr)"
                }
                driveService.files().update(existingFileId, updateMeta, mediaContent).execute()
            } else {
                onProgress?.invoke("正在创建并上传云端数据库...")
                val createMeta = File().apply {
                    name = BACKUP_FILE_NAME
                    parents = listOf(folderId)
                    description = "RMF Android Database Backup ($nowStr)"
                }
                driveService.files().create(createMeta, mediaContent).execute()
            }

            GoogleDrivePreferences.setLastSyncTime(context, nowStr)
            return@withContext SyncResult.Success("已成功上传并覆盖至 Google Drive 统一快照！", nowStr)
        } catch (e: Exception) {
            return@withContext SyncResult.Error("云端上传失败: ${e.localizedMessage}")
        }
    }

    /**
     * 从 Google Drive 拉取云端备份并进行数据还原
     */
    suspend fun downloadBackupFromDrive(
        onProgress: ((String) -> Unit)? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        try {
            onProgress?.invoke("正在连接 Google Drive API 并校验凭据...")
            val driveRes = getDriveService()
            if (driveRes.isFailure) {
                return@withContext SyncResult.Error(driveRes.exceptionOrNull()?.message ?: "初始化 Google Drive 客户端失败")
            }
            val driveService = driveRes.getOrThrow()

            onProgress?.invoke("正在检索云端备份镜像...")
            val folderId = ensureBackupFolder(driveService)

            val listReq = driveService.files().list()
                .setQ("name = '$BACKUP_FILE_NAME' and '$folderId' in parents and trashed = false")
                .setSpaces("drive")
                .setFields("files(id, name, modifiedTime, size)")

            val searchRes = listReq.execute()
            val remoteFile = searchRes.files?.firstOrNull()
                ?: return@withContext SyncResult.Error("在 Google Drive 中未发现备份文件 ($BACKUP_FILE_NAME)")

            onProgress?.invoke("正在拉取云端数据流...")
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

            // 先创建本地 .bak 备份副本防丢失
            val backupFile = java.io.File(context.cacheDir, "rmf_pre_restore_${System.currentTimeMillis()}.bak")
            if (targetDbFile.exists()) {
                targetDbFile.copyTo(backupFile, overwrite = true)
            }

            // 关闭当前数据库连接
            database.close()

            // 替换主数据库文件
            tempFile.copyTo(targetDbFile, overwrite = true)
            tempFile.delete()

            // 清理 WAL 临时文件
            java.io.File(targetDbFile.path + "-wal").delete()
            java.io.File(targetDbFile.path + "-shm").delete()

            val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            GoogleDrivePreferences.setLastSyncTime(context, nowStr)
            return@withContext SyncResult.Success("云端数据库已完整还原生效！已兼容同步 Windows 数据。", nowStr)
        } catch (e: Exception) {
            return@withContext SyncResult.Error("拉取还原失败: ${e.localizedMessage}")
        }
    }
}
