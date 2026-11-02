package com.renly.rmf.domain.service

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.renly.rmf.BuildConfig
import dalvik.system.BaseDexClassLoader
import dalvik.system.DexClassLoader
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 远端热补丁元信息清单实体 (rmf_patch_info.json)
 */
data class PatchInfo(
    val patchVersion: Int = 0,
    val patchName: String = "",
    val targetHostVersionCode: Int = 0,
    val releaseDate: String = "",
    val changelog: String = "",
    val patchFileId: String = "",
    val patchFileName: String = "",
    val sha256: String = "",
    val fileSize: Long = 0L
)

/**
 * 热补丁检查状态枚举
 */
sealed class HotPatchCheckResult {
    data class PatchAvailable(val patchInfo: PatchInfo) : HotPatchCheckResult()
    data class AlreadyLatest(val currentPatchVersion: Int) : HotPatchCheckResult()
    data class NotAuthorized(val message: String) : HotPatchCheckResult()
    data class NoPatchFound(val message: String) : HotPatchCheckResult()
    data class Error(val error: String) : HotPatchCheckResult()
}

/**
 * 商业级免安装动态热更新引擎 (Dynamic Hot-Patch Engine)
 *
 * 核心技术与安全特性：
 * 1. 动态类加载 (ART Class Prepending)：利用 Java 反射向当前宿主 ClassLoader 的 dexElements 头部动态插入 Patch Dex；
 * 2. 深度契合 Android 14+ / ColorOS 17 DCL 强制只读规范：补丁文件必须显式标记 setReadOnly()，杜绝 SecurityException；
 * 3. 守护神看门狗与自动熔断回滚 (Watchdog Anti-Brick Rollback)：
 *    若补丁在启动 10 秒内引发未捕获崩溃，看门狗将在下次启动时自动熔断并物理删除补丁，永不陷入无限闪退死循环；
 * 4. 宿主版本隔离：若用户覆盖安装了新版宿主 APK，自动失效并清理旧补丁，确保绝对隔离。
 */
object HotPatchManager {

    private const val TAG = "RMF_HotPatch"
    const val PATCH_MANIFEST_NAME = "rmf_patch_info.json"
    private const val PATCH_DIR_NAME = "hotpatch"
    private const val TEMP_PATCH_NAME = "patch_downloading.tmp"

    /**
     * 在 Application.attachBaseContext 中以最早时机动态注入热补丁
     */
    fun installPatch(application: Application) {
        if (!HotPatchPreferences.isEnabled(application)) {
            Log.d(TAG, "Hotpatch is disabled by user.")
            return
        }

        val activePath = HotPatchPreferences.getActivePatchPath(application) ?: return
        val patchFile = File(activePath)
        if (!patchFile.exists() || patchFile.length() == 0L) {
            HotPatchPreferences.clearActivePatch(application)
            return
        }

        // 1. 宿主大版本核验：若用户已安装了更新的宿主 APK，自动清除旧版补丁
        val targetHostVersion = HotPatchPreferences.getTargetHostVersion(application)
        if (targetHostVersion != 0 && targetHostVersion != BuildConfig.VERSION_CODE) {
            Log.w(TAG, "Host version mismatch (Target: $targetHostVersion, Current: ${BuildConfig.VERSION_CODE}). Clearing patch.")
            rollbackPatch(application)
            return
        }

        // 2. 看门狗崩溃熔断判定：若上次启动后在测试阶段崩溃，自动熔断回滚
        val stage = HotPatchPreferences.getStabilityStage(application)
        if (stage == HotPatchPreferences.STAGE_TESTING) {
            Log.e(TAG, "Safety Watchdog: Detected startup crash while testing patch. Rolling back to prevent bricking!")
            val version = HotPatchPreferences.getActivePatchVersion(application)
            HotPatchPreferences.setLastRollbackReason(
                application,
                "补丁 (v$version) 在启动测试期引发崩溃，看门狗已自动熔断回滚至原生基线版本。"
            )
            rollbackPatch(application)
            return
        }

        // 3. 执行动态 Dex 预注入
        try {
            // Android 14+ 强制只读校验
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (patchFile.canWrite()) {
                    patchFile.setReadOnly()
                }
            }

            injectDex(application, patchFile)

            // 标记进入启动测试期 (STAGE_TESTING)
            HotPatchPreferences.setStabilityStage(application, HotPatchPreferences.STAGE_TESTING)
            Log.i(TAG, "Hotpatch loaded successfully: ${patchFile.name}")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to inject hotpatch", e)
            HotPatchPreferences.setLastRollbackReason(application, "补丁加载反射异常: ${e.message}")
            rollbackPatch(application)
        }
    }

    /**
     * 在 Application.onCreate 中开启看门狗计时，稳定运行 10 秒后确认补丁合格 (STAGE_STABLE)
     */
    fun onAppStarted(context: Context) {
        if (HotPatchPreferences.getStabilityStage(context) == HotPatchPreferences.STAGE_TESTING) {
            CoroutineScope(Dispatchers.Default).launch {
                delay(10_000)
                if (HotPatchPreferences.getStabilityStage(context) == HotPatchPreferences.STAGE_TESTING) {
                    HotPatchPreferences.setStabilityStage(context, HotPatchPreferences.STAGE_STABLE)
                    Log.i(TAG, "Hotpatch has been marked as STABLE after passing watchdog test.")
                }
            }
        }
    }

    /**
     * 通过反射将补丁 DEX 插入宿主 ClassLoader 的 dexElements 头部
     */
    @Throws(Exception::class)
    private fun injectDex(application: Application, patchFile: File) {
        val baseLoader = application.classLoader as? BaseDexClassLoader
            ?: throw IllegalStateException("ClassLoader is not BaseDexClassLoader")

        val pathListField = BaseDexClassLoader::class.java.getDeclaredField("pathList").apply {
            isAccessible = true
        }
        val hostPathList = pathListField.get(baseLoader)
            ?: throw IllegalStateException("Host pathList is null")

        val dexElementsField = hostPathList.javaClass.getDeclaredField("dexElements").apply {
            isAccessible = true
        }
        val hostElements = dexElementsField.get(hostPathList) as? Array<*>
            ?: throw IllegalStateException("Host dexElements is null")

        // 构建补丁专属隔离 ClassLoader 读取其 Elements
        val optDir = File(application.codeCacheDir, "hotpatch_opt").apply { if (!exists()) mkdirs() }
        val patchLoader = DexClassLoader(
            patchFile.absolutePath,
            optDir.absolutePath,
            null,
            baseLoader
        )
        val patchPathList = pathListField.get(patchLoader)
            ?: throw IllegalStateException("Patch pathList is null")
        val patchElements = dexElementsField.get(patchPathList) as? Array<*>
            ?: throw IllegalStateException("Patch dexElements is null")

        // 元素拼接：补丁元素排在最前 (Prepend)，系统解析类时优先采用补丁中的字节码
        val componentType = hostElements.javaClass.componentType
            ?: throw IllegalStateException("Element componentType is null")
        @Suppress("UNCHECKED_CAST")
        val newElements = java.lang.reflect.Array.newInstance(
            componentType,
            patchElements.size + hostElements.size
        ) as Array<Any?>

        System.arraycopy(patchElements, 0, newElements, 0, patchElements.size)
        System.arraycopy(hostElements, 0, newElements, patchElements.size, hostElements.size)

        dexElementsField.set(hostPathList, newElements)
    }

    /**
     * 检查 Google Drive 是否存在针对当前宿主构建的热补丁
     */
    suspend fun checkForPatch(context: Context): HotPatchCheckResult = withContext(Dispatchers.IO) {
        if (!GoogleDrivePreferences.isAuthorized(context)) {
            return@withContext HotPatchCheckResult.NotAuthorized(
                "请先在「Google 服务与多端互通」中完成授权，即可检索云端热补丁。"
            )
        }
        if (!com.renly.rmf.util.NetworkUtils.isNetworkAvailable(context)) {
            return@withContext HotPatchCheckResult.Error("设备处于离线状态，无法连接云端检索补丁。")
        }

        val syncService = GoogleDriveSyncService(context)
        val driveRes = syncService.getDriveService()
        if (driveRes.isFailure) {
            val err = driveRes.exceptionOrNull()?.message ?: "Google Drive 客户端初始化失败"
            return@withContext HotPatchCheckResult.Error(err)
        }
        val drive = driveRes.getOrThrow()

        try {
            // 1. 全局检索 rmf_patch_info.json
            val listReq = drive.files().list()
                .setQ("name = '$PATCH_MANIFEST_NAME' and trashed = false")
                .setSpaces("drive")
                .setOrderBy("modifiedTime desc")
                .setPageSize(5)
                .setFields("files(id, name, modifiedTime, size)")
            val res = listReq.execute()
            var patchInfoFile = res.files?.firstOrNull()

            // 2. 回退检索同步专属文件夹
            if (patchInfoFile == null) {
                try {
                    val folderId = syncService.ensureBackupFolder(drive)
                    val fallbackReq = drive.files().list()
                        .setQ("'$folderId' in parents and name = '$PATCH_MANIFEST_NAME' and trashed = false")
                        .setSpaces("drive")
                        .setOrderBy("modifiedTime desc")
                        .setPageSize(5)
                        .setFields("files(id, name, modifiedTime, size)")
                    patchInfoFile = fallbackReq.execute().files?.firstOrNull()
                } catch (_: Exception) {}
            }

            if (patchInfoFile == null) {
                HotPatchPreferences.setLastCheckTime(context, formatNow())
                return@withContext HotPatchCheckResult.NoPatchFound("云端暂无已发布的热补丁清单 ($PATCH_MANIFEST_NAME)。")
            }

            // 3. 读取清单内容
            val outputStream = ByteArrayOutputStream()
            drive.files().get(patchInfoFile.id).executeMediaAndDownloadTo(outputStream)
            val jsonString = outputStream.toString("UTF-8")

            val patchInfo = try {
                Gson().fromJson(jsonString, PatchInfo::class.java)
            } catch (e: Exception) {
                return@withContext HotPatchCheckResult.Error("解析补丁清单失败: ${e.localizedMessage}")
            }

            if (patchInfo == null) {
                return@withContext HotPatchCheckResult.Error("补丁清单内容为空")
            }

            HotPatchPreferences.setLastCheckTime(context, formatNow())

            // 4. 核验补丁是否匹配当前宿主 APK
            if (patchInfo.targetHostVersionCode != BuildConfig.VERSION_CODE) {
                return@withContext HotPatchCheckResult.NoPatchFound(
                    "云端补丁适用的宿主版本 (v${patchInfo.targetHostVersionCode}) 与当前应用版本 (v${BuildConfig.VERSION_CODE}) 不一致。"
                )
            }

            val currentPatchVer = HotPatchPreferences.getActivePatchVersion(context)
            if (patchInfo.patchVersion <= currentPatchVer) {
                return@withContext HotPatchCheckResult.AlreadyLatest(currentPatchVer)
            }

            HotPatchCheckResult.PatchAvailable(patchInfo)
        } catch (e: Exception) {
            HotPatchCheckResult.Error("检查热更新失败: ${e.localizedMessage}")
        }
    }

    /**
     * 下载并就地准备热补丁文件
     */
    suspend fun downloadAndApplyPatch(
        context: Context,
        patchInfo: PatchInfo,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val syncService = GoogleDriveSyncService(context)
            val drive = syncService.getDriveService().getOrThrow()

            val patchDir = File(context.codeCacheDir, PATCH_DIR_NAME).apply { if (!exists()) mkdirs() }
            val tempFile = File(patchDir, TEMP_PATCH_NAME)
            val targetFile = File(patchDir, "patch_v${patchInfo.patchVersion}.apk")

            if (tempFile.exists()) tempFile.delete()

            // 1. 分片流式下载
            val inputStream = drive.files().get(patchInfo.patchFileId).executeMediaAsInputStream()
            val buffer = ByteArray(16384)
            var totalRead = 0L
            val actualSize = patchInfo.fileSize

            FileOutputStream(tempFile).use { output ->
                var read = inputStream.read(buffer)
                while (read != -1) {
                    output.write(buffer, 0, read)
                    totalRead += read
                    if (actualSize > 0) {
                        onProgress((totalRead.toFloat() / actualSize).coerceIn(0f, 1f))
                    }
                    read = inputStream.read(buffer)
                }
                output.flush()
            }

            // 2. SHA-256 校验 (若清单提供)
            if (patchInfo.sha256.isNotBlank()) {
                val computedSha = calculateSha256(tempFile)
                if (!computedSha.equals(patchInfo.sha256.trim(), ignoreCase = true)) {
                    tempFile.delete()
                    return@withContext Result.failure(IllegalStateException("补丁哈希校验失败 (SHA-256 不匹配)"))
                }
            }

            // 3. 重命名并标记 Android 14+ 只读
            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            targetFile.setReadOnly()

            // 4. 持久化记录配置
            HotPatchPreferences.setActivePatch(
                context = context,
                version = patchInfo.patchVersion,
                name = patchInfo.patchName,
                path = targetFile.absolutePath,
                targetHostVersion = patchInfo.targetHostVersionCode
            )
            HotPatchPreferences.setLastRollbackReason(context, null)

            onProgress(1f)
            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 回滚热补丁并清除本地文件
     */
    fun rollbackPatch(context: Context) {
        try {
            val activePath = HotPatchPreferences.getActivePatchPath(context)
            if (activePath != null) {
                val file = File(activePath)
                if (file.exists()) {
                    file.delete()
                }
            }
            val patchDir = File(context.codeCacheDir, PATCH_DIR_NAME)
            if (patchDir.exists()) {
                patchDir.deleteRecursively()
            }
            val optDir = File(context.codeCacheDir, "hotpatch_opt")
            if (optDir.exists()) {
                optDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning patch files", e)
        }
        HotPatchPreferences.clearActivePatch(context)
    }

    /**
     * 软重启应用，使热补丁在全新的主进程或栈顶中生效，无需系统安装器
     */
    fun restartApp(context: Context) {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
            if (context is Activity) {
                context.finishAffinity()
            }
            // 杀死当前进程以确保 Application.attachBaseContext 重新走全新类加载
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var len: Int
            while (input.read(buf).also { len = it } != -1) {
                digest.update(buf, 0, len)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun formatNow(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}
