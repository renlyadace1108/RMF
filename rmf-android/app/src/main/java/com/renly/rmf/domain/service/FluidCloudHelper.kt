package com.renly.rmf.domain.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONObject

/**
 * 跨品牌流体云 / 灵动岛 / 实时活动核心协同引擎
 *
 * 核心架构决策背景：
 * 1. 【OPPO ColorOS 16/17 (如 Find X9)】：
 *    官方全面借道原生 Android 16 的 "Live Updates" (Promoted Ongoing) API 标准。
 *    Android 16 系统在 `Notification.hasPromotableCharacteristics()` 内部执行硬性校验：
 *    - 必须声明 `POST_PROMOTED_NOTIFICATIONS` 权限；
 *    - 必须设置 `android.requestPromotedOngoing = true`；
 *    - 严禁绑定自定义 RemoteViews（`contentView` 与 `bigContentView` 必须为空）；
 *    - 必须使用 BigTextStyle 等原生样式。
 *
 * 2. 【小米 Xiaomi HyperOS (澎湃OS 超级岛 / 焦点通知)】：
 *    小米端拥有独立的系统级“超级岛 / 焦点通知”接口协议：
 *    - 通过在 Notification Extras 注入 `miui.focus.param` (V2/V3 JSON 结构模板) 与 `miui.focus.notification = true` 接入超级岛；
 *    - 若系统未开放超级岛白名单权限，则回退呈现深度美化重构的原生卡片（带有磨砂药丸倒计时仪表盘与圆角交互按钮），彻底告别原版简陋视图。
 */
object FluidCloudHelper {

    const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
    const val EXTRA_OPLUS_CAPSULE = "oplus.notification.capsule"
    const val PERMISSION_POST_PROMOTED_NOTIFICATIONS = "android.permission.POST_PROMOTED_NOTIFICATIONS"

    /**
     * 判断当前系统是否支持并应启用 Android 16 / ColorOS 17 原生 Live Updates 借道标准
     * （注意：小米 HyperOS 拥有自己的 miui.focus.param 超级岛接口与专属卡片渲染流，不应被强制剥离 RemoteViews）
     */
    fun isPromotedLiveUpdateSupported(context: Context? = null): Boolean {
        // 小米 HyperOS 不走 Android 16 Promoted 剥离视图逻辑，而是采用美化卡片 + 注入超级岛参数
        if (isXiaomiOrHyperOs()) {
            return false
        }

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
     * 检测是否为小米 HyperOS (澎湃OS) 或 MIUI
     */
    fun isXiaomiOrHyperOs(): Boolean {
        try {
            val brand = Build.BRAND.orEmpty()
            val manufacturer = Build.MANUFACTURER.orEmpty()
            val isMi = brand.contains("Xiaomi", ignoreCase = true) ||
                    brand.contains("Redmi", ignoreCase = true) ||
                    brand.contains("POCO", ignoreCase = true) ||
                    manufacturer.contains("Xiaomi", ignoreCase = true)

            if (isMi) return true

            val miuiVersion = getSystemProperty("ro.miui.ui.version.name")
            val hyperOsVersion = getSystemProperty("ro.mi.os.version.name")
            return miuiVersion.isNotBlank() || hyperOsVersion.isNotBlank()
        } catch (_: Throwable) {}
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
     * 获取当前系统对应的流体云 / 超级岛动态品牌标签文本
     */
    fun getSystemBrandLabel(): String {
        return "胶囊通知"
    }

    /**
     * 为小米 HyperOS 注入超级岛 (Super Island) / 焦点通知 (Focus Notification) 原生协议参数
     */
    fun applyXiaomiSuperIslandParams(
        builder: NotificationCompat.Builder,
        title: String,
        remainingSeconds: Int,
        isRunning: Boolean
    ) {
        if (!isXiaomiOrHyperOs()) return

        val min = remainingSeconds / 60
        val sec = remainingSeconds % 60
        val timeStr = "%02d:%02d".format(min, sec)

        try {
            // 构建小米 HyperOS 官方 V2/V3 规范焦点通知与超级岛 JSON 结构
            val focusJson = JSONObject().apply {
                val paramV2 = JSONObject().apply {
                    put("business", "focus_timer")
                    put("updatable", true)
                    put("orderId", "rmf_focus_live")

                    // 超级岛配置 (param_island)
                    val paramIsland = JSONObject().apply {
                        put("islandProperty", 2) // 2: 交互与倒计时操作岛
                        put("islandOrder", 1)

                        // 摘要态：小岛 (双侧胶囊展示)
                        val smallIsland = JSONObject().apply {
                            val left = JSONObject().apply {
                                put("type", 1)
                                put("text", "🎯")
                            }
                            val right = JSONObject().apply {
                                put("type", 1)
                                put("text", timeStr)
                            }
                            put("left", left)
                            put("right", right)
                        }
                        put("smallIslandArea", smallIsland)

                        // 展开态：大岛 (下拉扩展信息卡)
                        val bigIsland = JSONObject().apply {
                            put("title", "🎯 $title")
                            put("content", if (isRunning) "心流专注进行中 · 剩余 $timeStr" else "专注已暂停 ($timeStr)")
                            put("subContent", "HyperOS 超级岛 · 实时专注")
                        }
                        put("bigIslandArea", bigIsland)
                    }
                    put("param_island", paramIsland)
                }
                put("param_v2", paramV2)
            }

            // 注入小米系统级焦点通知与超级岛 Intent Extras
            builder.extras.putString("miui.focus.param", focusJson.toString())
            builder.extras.putBoolean("miui.focus.notification", true)
            builder.extras.putBoolean("miui.enableFloat", true)
            builder.extras.putBoolean("miui.showAction", true)
            builder.extras.putString("miui.subSystem", "focus")
            builder.extras.putInt("miui.targetSystem", 1)
        } catch (_: Throwable) {}
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
     * 为 NotificationCompat.Builder 注入实时活动提升请求与品牌胶囊标记
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
