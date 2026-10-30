package com.renly.rmf.domain.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

/**
 * Google Drive 远端版本清单数据实体 (rmf_release_info.json)
 */
data class ReleaseInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val releaseDate: String = "",
    val changelog: String = "",
    val minSupportedVersion: Int = 0,
    val phoneApkFileId: String = "",
    val phoneApkName: String = "",
    val phoneApkSize: Long = 0L,
    val padApkFileId: String = "",
    val padApkName: String = "",
    val padApkSize: Long = 0L,
    val windowsInstallerFileId: String = "",
    val windowsInstallerName: String = "",
    val windowsInstallerSize: Long = 0L,
    val windowsZipFileId: String = "",
    val windowsZipName: String = "",
    val windowsZipSize: Long = 0L
)

/**
 * 更新检查状态枚举与承载对象
 */
sealed class UpdateCheckResult {
    data class UpdateAvailable(
        val releaseInfo: ReleaseInfo,
        val targetFileId: String,
        val targetFileName: String,
        val targetFileSize: Long,
        val isPad: Boolean
    ) : UpdateCheckResult()

    data class AlreadyLatest(
        val currentVersion: String,
        val releaseDate: String = ""
    ) : UpdateCheckResult()

    data class NotAuthorized(
        val message: String
    ) : UpdateCheckResult()

    data class NoReleaseFound(
        val message: String
    ) : UpdateCheckResult()

    data class Error(
        val error: String
    ) : UpdateCheckResult()
}

/**
 * Google Drive 应用内全自动更新服务：
 * 1. 穿透检索 Google Drive 中由开发者发布的 rmf_release_info.json 清单；
 * 2. 智能甄别当前设备类型 (手机版 / 平板版)，获取对应构建的专属 File ID；
 * 3. 稳健多级缓冲分片下载至 cacheDir/rmf_update.apk；
 * 4. 严密契合 Android 8.0+ FileProvider 与 PackageInstaller 规范，实现一键无缝覆盖安装。
 */
object GoogleDriveUpdateService {

    const val RELEASE_MANIFEST_NAME = "rmf_release_info.json"
    const val LOCAL_UPDATE_APK_NAME = "rmf_update.apk"
    const val LOCAL_TEMP_APK_NAME = "rmf_update_downloading.tmp"

    /**
     * 检查 Google Drive 是否存在高于本地版本的正式构建
     */
    suspend fun checkForUpdate(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        if (!GoogleDrivePreferences.isAuthorized(context)) {
            return@withContext UpdateCheckResult.NotAuthorized(
                "尚未授权 Google 账户。请先在「Google 服务与多端互通」中授权，即可通过专属 Google Drive 获取新版本。"
            )
        }
        if (!com.renly.rmf.util.NetworkUtils.isNetworkAvailable(context)) {
            return@withContext UpdateCheckResult.Error("设备离线，无法连接 Google Drive 检查版本更新")
        }

        val syncService = GoogleDriveSyncService(context)
        val driveRes = syncService.getDriveService()
        if (driveRes.isFailure) {
            val err = driveRes.exceptionOrNull()?.message ?: "Google Drive 客户端初始化失败"
            return@withContext UpdateCheckResult.Error(err)
        }
        val drive = driveRes.getOrThrow()

        try {
            // 1. 全局穿透检索 rmf_release_info.json
            val listReq = drive.files().list()
                .setQ("name = '$RELEASE_MANIFEST_NAME' and trashed = false")
                .setSpaces("drive")
                .setOrderBy("modifiedTime desc")
                .setPageSize(5)
                .setFields("files(id, name, modifiedTime, size)")
            val res = listReq.execute()
            var releaseFile = res.files?.firstOrNull()

            // 2. 若全局未查到，回退检索专属 RMF_CloudSync 同步文件夹
            if (releaseFile == null) {
                try {
                    val folderId = syncService.ensureBackupFolder(drive)
                    val fallbackReq = drive.files().list()
                        .setQ("'$folderId' in parents and name = '$RELEASE_MANIFEST_NAME' and trashed = false")
                        .setSpaces("drive")
                        .setOrderBy("modifiedTime desc")
                        .setPageSize(5)
                        .setFields("files(id, name, modifiedTime, size)")
                    val fallbackList = fallbackReq.execute().files
                    releaseFile = fallbackList?.firstOrNull()
                } catch (_: Exception) {}
            }

            if (releaseFile == null) {
                return@withContext UpdateCheckResult.NoReleaseFound("Google Drive 中暂无已发布的版本描述清单 ($RELEASE_MANIFEST_NAME)。")
            }

            // 3. 拉取清单内容
            val outputStream = ByteArrayOutputStream()
            drive.files().get(releaseFile.id).executeMediaAndDownloadTo(outputStream)
            val jsonString = outputStream.toString("UTF-8")

            val releaseInfo = try {
                Gson().fromJson(jsonString, ReleaseInfo::class.java)
            } catch (e: Exception) {
                return@withContext UpdateCheckResult.Error("解析版本清单失败: ${e.localizedMessage}")
            }

            if (releaseInfo == null) {
                return@withContext UpdateCheckResult.Error("版本清单内容为空")
            }

            val currentVersionCode = com.renly.rmf.BuildConfig.VERSION_CODE
            val currentVersionName = com.renly.rmf.BuildConfig.VERSION_NAME

            val hasNewerVersion = releaseInfo.versionCode > currentVersionCode ||
                    (releaseInfo.versionCode == currentVersionCode && releaseInfo.versionName != currentVersionName)

            if (!hasNewerVersion) {
                return@withContext UpdateCheckResult.AlreadyLatest(currentVersionName, releaseInfo.releaseDate)
            }

            val isPad = com.renly.rmf.BuildConfig.IS_PAD_EDITION
            val targetFileId = if (isPad) {
                releaseInfo.padApkFileId.ifBlank { releaseInfo.phoneApkFileId }
            } else {
                releaseInfo.phoneApkFileId.ifBlank { releaseInfo.padApkFileId }
            }
            val targetFileName = if (isPad) {
                releaseInfo.padApkName.ifBlank { "RMF-Pad-v${releaseInfo.versionName}.apk" }
            } else {
                releaseInfo.phoneApkName.ifBlank { "RMF-Phone-v${releaseInfo.versionName}.apk" }
            }
            val targetFileSize = if (isPad) {
                if (releaseInfo.padApkSize > 0) releaseInfo.padApkSize else releaseInfo.phoneApkSize
            } else {
                if (releaseInfo.phoneApkSize > 0) releaseInfo.phoneApkSize else releaseInfo.padApkSize
            }

            if (targetFileId.isBlank()) {
                return@withContext UpdateCheckResult.Error("云端已发布新版本 v${releaseInfo.versionName}，但对应平台的 APK 文件未就绪。")
            }

            return@withContext UpdateCheckResult.UpdateAvailable(
                releaseInfo = releaseInfo,
                targetFileId = targetFileId,
                targetFileName = targetFileName,
                targetFileSize = targetFileSize,
                isPad = isPad
            )
        } catch (e: Exception) {
            return@withContext UpdateCheckResult.Error("检查版本更新失败: ${e.localizedMessage}")
        }
    }

    /**
     * 检查本地是否已有下载好的且大小匹配的 APK 安装包
     */
    fun getCachedApk(context: Context, expectedSize: Long): File? {
        val file = File(context.cacheDir, LOCAL_UPDATE_APK_NAME)
        if (file.exists() && file.length() > 0) {
            if (expectedSize <= 0 || file.length() == expectedSize) {
                return file
            }
        }
        return null
    }

    /**
     * 从 Google Drive 流式拉取安装包并上报下载进度
     */
    suspend fun downloadApk(
        context: Context,
        fileId: String,
        expectedSize: Long,
        onProgress: (progress: Float, currentBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val syncService = GoogleDriveSyncService(context)
            val driveRes = syncService.getDriveService()
            if (driveRes.isFailure) {
                return@withContext Result.failure(driveRes.exceptionOrNull() ?: Exception("Google Drive 初始化失败"))
            }
            val drive = driveRes.getOrThrow()

            var actualSize = expectedSize
            if (actualSize <= 0) {
                try {
                    val meta = drive.files().get(fileId).setFields("size").execute()
                    actualSize = meta.size?.toLong() ?: 0L
                } catch (_: Exception) {}
            }

            val updateFile = File(context.cacheDir, LOCAL_UPDATE_APK_NAME)
            val tempFile = File(context.cacheDir, LOCAL_TEMP_APK_NAME)
            if (tempFile.exists()) tempFile.delete()

            val inputStream = drive.files().get(fileId).executeMediaAsInputStream()
            val buffer = ByteArray(32768)
            var totalRead = 0L
            var lastNotifyTime = 0L

            FileOutputStream(tempFile).use { output ->
                var read = inputStream.read(buffer)
                while (read != -1) {
                    output.write(buffer, 0, read)
                    totalRead += read
                    val now = System.currentTimeMillis()
                    if (now - lastNotifyTime >= 100) {
                        lastNotifyTime = now
                        val progress = if (actualSize > 0) (totalRead.toFloat() / actualSize).coerceIn(0f, 1f) else 0f
                        onProgress(progress, totalRead, actualSize)
                    }
                    read = inputStream.read(buffer)
                }
                output.flush()
            }

            if (updateFile.exists()) updateFile.delete()
            if (!tempFile.renameTo(updateFile)) {
                tempFile.copyTo(updateFile, overwrite = true)
                tempFile.delete()
            }

            onProgress(1f, updateFile.length(), updateFile.length())
            Result.success(updateFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 调起系统包安装器覆盖升级
     */
    fun installApk(context: Context, apkFile: File): Result<Unit> {
        try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                return Result.failure(FileNotFoundException("安装包不存在或已损坏，请重新下载。"))
            }

            // Android 8.0+ 检查「允许安装未知来源应用」权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return Result.failure(SecurityException("需要授予「安装未知应用」权限。系统已自动跳转至设置页面，请允许后返回点击安装。"))
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
