package com.renly.rmf.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.renly.rmf.R
import com.renly.rmf.RmfApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class RmfFocusCapsuleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isNotEmpty()) {
            updateAppWidgets(context, appWidgetManager, appWidgetIds)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAppWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == RmfWidgetManager.ACTION_REFRESH_WIDGET || intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, RmfFocusCapsuleWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (allWidgetIds != null && allWidgetIds.isNotEmpty()) {
                updateAppWidgets(context, appWidgetManager, allWidgetIds)
            }
        }
    }

    companion object {
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            updateAppWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
        }

        fun updateAppWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
            if (appWidgetIds.isEmpty()) return

            val firstId = appWidgetIds[0]
            val options = appWidgetManager.getAppWidgetOptions(firstId)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            if (minHeight >= 180 && minWidth >= 180) {
                RmfFocusFullWidgetProvider.updateAppWidgets(context, appWidgetManager, appWidgetIds)
                return
            }

            val views = RemoteViews(context.packageName, R.layout.widget_rmf_capsule)

            // 根视图点击 -> 打开倒数日页
            views.setOnClickPendingIntent(
                R.id.widget_capsule_root,
                RmfWidgetManager.createNavPendingIntent(context, "countdowns", 300)
            )

            // 倒数日容器点击 -> 深度跳转倒数日页
            views.setOnClickPendingIntent(
                R.id.widget_capsule_countdown_container,
                RmfWidgetManager.createNavPendingIntent(context, "countdowns", 301)
            )

            // 专注按钮 -> 深度跳转专注页
            views.setOnClickPendingIntent(
                R.id.widget_capsule_focus_btn,
                RmfWidgetManager.createNavPendingIntent(context, "focus", 302)
            )

            // 闪念速记按钮 -> 触发快速捕获
            views.setOnClickPendingIntent(
                R.id.widget_capsule_capture_btn,
                RmfWidgetManager.createActionPendingIntent(context, "quick_capture", 303)
            )

            val app = context.applicationContext as? RmfApplication
            if (app != null) {
                val db = app.database
                val today = LocalDate.now()
                widgetScope.launch {
                    try {
                        val countdowns = db.countdownDao().getAllActiveCountdowns().first()
                        val topCountdown = countdowns.firstOrNull { it.isPinned == 1 } ?: countdowns.firstOrNull()
                        if (topCountdown != null) {
                            val target = try {
                                LocalDate.parse(topCountdown.targetDate.take(10))
                            } catch (_: Exception) {
                                today
                            }
                            val diff = java.time.temporal.ChronoUnit.DAYS.between(today, target)
                            views.setTextViewText(R.id.widget_capsule_countdown_title, "⏳ " + topCountdown.title)
                            views.setTextViewText(R.id.widget_capsule_days_num, if (diff >= 0) "$diff" else "${Math.abs(diff)}")
                            views.setTextViewText(R.id.widget_capsule_days_label, if (diff >= 0) " 天后" else " 天前")
                            views.setTextViewText(R.id.widget_capsule_target_date, "目标日: ${topCountdown.targetDate.take(10)}")
                        } else {
                            views.setTextViewText(R.id.widget_capsule_countdown_title, "⏳ 倒数日与里程碑")
                            views.setTextViewText(R.id.widget_capsule_days_num, "--")
                            views.setTextViewText(R.id.widget_capsule_days_label, " 天")
                            views.setTextViewText(R.id.widget_capsule_target_date, "点击添加重要目标倒计时")
                        }

                        appWidgetManager.updateAppWidget(appWidgetIds, views)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else {
                appWidgetManager.updateAppWidget(appWidgetIds, views)
            }
        }
    }
}
