package com.renly.rmf.domain.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * ColorOS 17 (Android 16) 流体云实时活动 (Live Updates / Promoted Ongoing) 核心协同引擎
 *
 * 核心架构决策背景：
 * 从 ColorOS 16/17 起，OPPO 官方流体云全面借道原生 Android 16 的 "Live Updates" (Promoted Ongoing) API 标准。
 * Android 16 系统在 `Notification.hasPromotableCharacteristics()` 内部有严格的硬性校验：
 * 1. 必须在 Manifest 中声明 `android.permission.POST_PROMOTED_NOTIFICATIONS` 权限；
 * 2. 必须设置 `android.requestPromotedOngoing = true` (或调用 setRequestPromotedOngoing)；
 * 3. 严禁绑定自定义 RemoteViews (`contentView` 与 `bigContentView` 必须为空)；
 * 4. 必须使用系统认证的 Promotable 样式 (如 `BigTextStyle`)；
 * 5. 必须为持续进行态 (`setOngoing(true)`) 且拥有有效标题。
 * 若违反上述任一条款，系统将判定为不可提升 (Non-Promotable)，导致无法在 OPPO Find X9 等设备上正常触发流体云胶囊。
 */
object FluidCloudHelper {

    const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
    const val EXTRA_OPLUS_CAPSULE = "oplus.notification.capsule"
    const val PERMISSION_POST_PROMOTED_NOTIFICATIONS = "android.permission.POST_PROMOTED_NOTIFICATIONS"

    /**
     * 判断当前系统是否支持并应启用 Android 16 / ColorOS 17 原生 Live Updates 借道标准
     */
    fun isPromotedLiveUpdateSupported(context: Context? = null): Boolean {
        // 1. Android 16 (API 36+) 或 Android 16 Baklava 预览阶段
        if (Build.VERSION.SDK_INT >= 36) return true
        if (Build.VERSION.SDK_INT >= 34 && Build.VERSION.CODENAME.equals("Baklava", ignoreCase = true)) return true

        // 2. 检测系统 Notification.Builder 是否已内置 setRequestPromotedOngoing 方法
        try {
            if (Notification.Builder::class.java.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType) != null) {
                return true
            }
        } catch (_: Throwable) {}

        // 3. 检测 OPPO / OnePlus / Realme 系列设备的 ColorOS 15/16/17 系统版本
        if (isColorOs16OrHigher()) {
            return true
        }

        return false
    }

    /**
     * 准确检测是否为 OPPO ColorOS 15/16/17 (或 OPLUS 体系同代系统)
     */
    fun isColorOs16OrHigher(): Boolean {
        try {
            val brand = Build.BRAND.orEmpty()
            val manufacturer = Build.MANUFACTURER.orEmpty()
            val isOplusDevice = brand.contains("OPPO", ignoreCase = true) ||
                    brand.contains("OnePlus", ignoreCase = true) ||
                    brand.contains("realme", ignoreCase = true) ||
                    manufacturer.contains("OPPO", ignoreCase = true)

            if (!isOplusDevice) return false

            // OPPO Find X8/X9 系列出厂为 Android 15/16，全面接入 Live Updates
            if (Build.VERSION.SDK_INT >= 35) return true

            val rom = getSystemProperty("ro.build.version.opporom")
            val release = getSystemProperty("ro.oplus.build.version.release")
            val combined = "$rom $release"
            if (combined.contains("15") || combined.contains("16") || combined.contains("17") ||
                combined.contains("V15") || combined.contains("V16") || combined.contains("V17")) {
                return true
            }
        } catch (_: Throwable) {}
        return false
    }

    /**
     * 查询系统属性
     */
    fun getSystemProperty(key: String): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val getMethod = clazz.getMethod("get", String::class.java, String::class.java)
            getMethod.invoke(null, key, "") as? String ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * 为 NotificationCompat.Builder 注入实时活动提升请求与 ColorOS 胶囊标记
     */
    fun applyPromotedOngoing(builder: NotificationCompat.Builder) {
        builder.extras.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
        builder.extras.putBoolean(EXTRA_OPLUS_CAPSULE, true)
        try {
            val method = builder.javaClass.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
            method.invoke(builder, true)
        } catch (_: Throwable) {}
    }

    /**
     * 通知实例最终收口校验
     * 若当前处于 Live Updates 原生模式，强制确保 RemoteViews 为空，保障 100% 通过 hasPromotableCharacteristics() 校验
     */
    fun finalizeNotification(notification: Notification, isLiveUpdateMode: Boolean) {
        notification.extras.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
        notification.extras.putBoolean(EXTRA_OPLUS_CAPSULE, true)
        if (isLiveUpdateMode) {
            notification.contentView = null
            notification.bigContentView = null
            notification.headsUpContentView = null
        }
    }

    /**
     * 检查系统是否允许当前应用发送 Promoted 通知 (Android 16 NotificationManager.canPostPromotedNotifications)
     */
    fun canPostPromotedNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 36) {
            try {
                val manager = context.getSystemService(NotificationManager::class.java)
                val method = manager.javaClass.getMethod("canPostPromotedNotifications")
                return method.invoke(manager) as Boolean
            } catch (_: Throwable) {}
        }
        return true
    }
}
