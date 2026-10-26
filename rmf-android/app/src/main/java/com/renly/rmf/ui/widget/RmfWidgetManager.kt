package com.renly.rmf.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.renly.rmf.MainActivity

/**
 * RmfWidgetManager
 * 统一管理桌面小组件数据广播通知与跨页面深度跳转意图
 */
object RmfWidgetManager {

    const val ACTION_REFRESH_WIDGET = "com.renly.rmf.ACTION_REFRESH_WIDGET"
    const val EXTRA_TARGET_ROUTE = "target_route"
    const val EXTRA_TARGET_ACTION = "target_action"

    private var lastRefreshTimestamp: Long = 0L

    /**
     * 发送广播刷新所有已添加到桌面的 RMF 小组件 (带 1 秒防抖，避免生命周期频繁触发广播风暴)
     */
    fun refreshAllWidgets(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastRefreshTimestamp < 1000L) {
            return
        }
        lastRefreshTimestamp = now

        try {
            val appWidgetManager = AppWidgetManager.getInstance(context.applicationContext) ?: return

            val providers = listOf(
                RmfAppWidgetProvider::class.java,
                RmfAppFullWidgetProvider::class.java,
                RmfScheduleWidgetProvider::class.java,
                RmfScheduleFullWidgetProvider::class.java,
                RmfFocusCapsuleWidgetProvider::class.java,
                RmfFocusFullWidgetProvider::class.java
            )

            for (providerClass in providers) {
                val component = ComponentName(context, providerClass)
                val ids = appWidgetManager.getAppWidgetIds(component)
                if (ids != null && ids.isNotEmpty()) {
                    val intent = Intent(context, providerClass).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                    context.sendBroadcast(intent)
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * 创建点击小组件直接深度跳转指定 Tab 的 PendingIntent
     */
    fun createNavPendingIntent(context: Context, route: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_TARGET_ROUTE, route)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 创建点击直接触发特定动作（如快捷捕捉/新建日程）的 PendingIntent
     */
    fun createActionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_TARGET_ACTION, action)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 针对 Android 8.0+ / 平板快捷添加小组件到桌面 (requestPinAppWidget)
     */
    fun requestPinWidget(context: Context, providerClass: Class<*>): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val appWidgetManager = AppWidgetManager.getInstance(context.applicationContext)
            if (appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported) {
                val component = ComponentName(context, providerClass)
                return appWidgetManager.requestPinAppWidget(component, null, null)
            }
        }
        return false
    }

    /**
     * 创建立即刷新小组件的广播 PendingIntent
     */
    fun createRefreshPendingIntent(context: Context, providerClass: Class<*>, requestCode: Int): PendingIntent {
        val intent = Intent(context, providerClass).apply {
            action = ACTION_REFRESH_WIDGET
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 创建直接触发 FocusLiveService 动作（开始/暂停/恢复/停止）的 PendingIntent
     */
    fun createFocusActionPendingIntent(
        context: Context,
        action: String,
        extraSeconds: Int = 25 * 60,
        title: String = "深度专注",
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, com.renly.rmf.domain.service.FocusLiveService::class.java).apply {
            this.action = action
            putExtra(com.renly.rmf.domain.service.FocusLiveService.EXTRA_TITLE, title)
            putExtra(com.renly.rmf.domain.service.FocusLiveService.EXTRA_SECONDS, extraSeconds)
        }
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
