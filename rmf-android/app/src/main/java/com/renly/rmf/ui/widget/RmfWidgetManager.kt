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

            // 1. 全景看板小组件
            val mainProvider = ComponentName(context, RmfAppWidgetProvider::class.java)
            val mainIds = appWidgetManager.getAppWidgetIds(mainProvider)
            if (mainIds != null && mainIds.isNotEmpty()) {
                val intent = Intent(context, RmfAppWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, mainIds)
                }
                context.sendBroadcast(intent)
            }

            // 2. 今日日程清单小组件
            val scheduleProvider = ComponentName(context, RmfScheduleWidgetProvider::class.java)
            val scheduleIds = appWidgetManager.getAppWidgetIds(scheduleProvider)
            if (scheduleIds != null && scheduleIds.isNotEmpty()) {
                val intent = Intent(context, RmfScheduleWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, scheduleIds)
                }
                context.sendBroadcast(intent)
            }

            // 3. 专注与倒数胶囊小组件
            val capsuleProvider = ComponentName(context, RmfFocusCapsuleWidgetProvider::class.java)
            val capsuleIds = appWidgetManager.getAppWidgetIds(capsuleProvider)
            if (capsuleIds != null && capsuleIds.isNotEmpty()) {
                val intent = Intent(context, RmfFocusCapsuleWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, capsuleIds)
                }
                context.sendBroadcast(intent)
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
}
