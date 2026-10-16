package com.renly.rmf.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.renly.rmf.R
import com.renly.rmf.RmfApplication
import com.renly.rmf.domain.service.FocusLiveService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * RmfFocusFullWidgetProvider
 * 4x4 全尺寸深度专注与倒数中枢大看板小组件
 */
class RmfFocusFullWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isNotEmpty()) {
            updateAppWidgets(context, appWidgetManager, appWidgetIds)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAppWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == RmfWidgetManager.ACTION_REFRESH_WIDGET || intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, RmfFocusFullWidgetProvider::class.java)
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

            val views = RemoteViews(context.packageName, R.layout.widget_rmf_focus_full)

            // 根视图点击 -> 打开专注页
            views.setOnClickPendingIntent(
                R.id.widget_focus_full_root,
                RmfWidgetManager.createNavPendingIntent(context, "focus", 1300)
            )

            // 刷新按钮
            views.setOnClickPendingIntent(
                R.id.widget_focus_full_refresh_btn,
                RmfWidgetManager.createRefreshPendingIntent(context, RmfFocusFullWidgetProvider::class.java, 1301)
            )

            // 控制台整卡点击 -> 跳转专注页
            views.setOnClickPendingIntent(
                R.id.widget_focus_full_console_card,
                RmfWidgetManager.createNavPendingIntent(context, "focus", 1302)
            )

            // 倒数日卡片点击 -> 跳转倒数日页
            views.setOnClickPendingIntent(
                R.id.widget_focus_full_countdown_container,
                RmfWidgetManager.createNavPendingIntent(context, "countdowns", 1303)
            )

            // 底部操作栏
            views.setOnClickPendingIntent(
                R.id.widget_focus_full_bottom_interrupt,
                RmfWidgetManager.createNavPendingIntent(context, "focus", 1304)
            )
            views.setOnClickPendingIntent(
                R.id.widget_focus_full_bottom_capture,
                RmfWidgetManager.createActionPendingIntent(context, "quick_capture", 1305)
            )
            views.setOnClickPendingIntent(
                R.id.widget_focus_full_bottom_analytics,
                RmfWidgetManager.createNavPendingIntent(context, "focus", 1306)
            )

            val today = LocalDate.now()
            views.setTextViewText(R.id.widget_focus_full_date, today.format(DateTimeFormatter.ofPattern("MM-dd EEE")))

            // 实时专注状态装配
            val isRunning = FocusLiveService.isRunning.value
            val remainingSec = FocusLiveService.remainingSeconds.value
            val initialSec = FocusLiveService.initialTotalSeconds.value
            val currentTitle = FocusLiveService.currentTaskTitle.value
            val isPaused = !isRunning && remainingSec > 0 && remainingSec < initialSec

            val min = remainingSec / 60
            val sec = remainingSec % 60
            val timeText = if (remainingSec > 0) "%02d:%02d".format(min, sec) else "25:00"
            views.setTextViewText(R.id.widget_focus_full_timer_text, timeText)

            if (isRunning) {
                views.setTextViewText(R.id.widget_focus_full_status_badge, "🟢 专注中")
                views.setTextViewText(R.id.widget_focus_full_task_title, currentTitle.ifBlank { "深度专注" })
                views.setTextViewText(R.id.widget_focus_full_toggle_btn, "⏸️ 暂停专注")
                views.setOnClickPendingIntent(
                    R.id.widget_focus_full_toggle_btn,
                    RmfWidgetManager.createFocusActionPendingIntent(context, FocusLiveService.ACTION_PAUSE, 0, "", 1310)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_focus_full_stop_btn,
                    RmfWidgetManager.createFocusActionPendingIntent(context, FocusLiveService.ACTION_STOP, 0, "", 1311)
                )
            } else if (isPaused) {
                views.setTextViewText(R.id.widget_focus_full_status_badge, "⏸️ 已暂停")
                views.setTextViewText(R.id.widget_focus_full_task_title, currentTitle.ifBlank { "深度专注" })
                views.setTextViewText(R.id.widget_focus_full_toggle_btn, "▶️ 继续专注")
                views.setOnClickPendingIntent(
                    R.id.widget_focus_full_toggle_btn,
                    RmfWidgetManager.createFocusActionPendingIntent(context, FocusLiveService.ACTION_RESUME, 0, "", 1312)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_focus_full_stop_btn,
                    RmfWidgetManager.createFocusActionPendingIntent(context, FocusLiveService.ACTION_STOP, 0, "", 1313)
                )
            } else {
                views.setTextViewText(R.id.widget_focus_full_status_badge, "⚪ 待就绪")
                views.setTextViewText(R.id.widget_focus_full_task_title, "深度心流专注")
                views.setTextViewText(R.id.widget_focus_full_toggle_btn, "▶️ 开启专注")
                views.setOnClickPendingIntent(
                    R.id.widget_focus_full_toggle_btn,
                    RmfWidgetManager.createFocusActionPendingIntent(context, FocusLiveService.ACTION_START, 25 * 60, "深度专注", 1314)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_focus_full_stop_btn,
                    RmfWidgetManager.createFocusActionPendingIntent(context, FocusLiveService.ACTION_STOP, 0, "", 1315)
                )
            }

            // 预设时长快捷启动 (25m / 45m / 60m)
            views.setOnClickPendingIntent(
                R.id.widget_focus_full_preset_25,
                RmfWidgetManager.createFocusActionPendingIntent(context, FocusLiveService.ACTION_START, 25 * 60, "番茄专注", 1316)
            )
            views.setOnClickPendingIntent(
                R.id.widget_focus_full_preset_45,
                RmfWidgetManager.createFocusActionPendingIntent(context, FocusLiveService.ACTION_START, 45 * 60, "攻坚专注", 1317)
            )
            views.setOnClickPendingIntent(
                R.id.widget_focus_full_preset_60,
                RmfWidgetManager.createFocusActionPendingIntent(context, FocusLiveService.ACTION_START, 60 * 60, "深度心流", 1318)
            )

            val app = context.applicationContext as? RmfApplication
            if (app != null) {
                val db = app.database
                widgetScope.launch {
                    try {
                        // 1. 倒数日查询
                        val countdowns = db.countdownDao().getAllActiveCountdowns().first()
                        val topCountdown = countdowns.firstOrNull { it.isPinned == 1 } ?: countdowns.firstOrNull()
                        if (topCountdown != null) {
                            val target = try {
                                LocalDate.parse(topCountdown.targetDate.take(10))
                            } catch (_: Exception) {
                                today
                            }
                            val diff = java.time.temporal.ChronoUnit.DAYS.between(today, target)
                            views.setTextViewText(R.id.widget_focus_full_countdown_title, "⏳ " + topCountdown.title)
                            views.setTextViewText(
                                R.id.widget_focus_full_countdown_days,
                                if (diff >= 0) "还剩 $diff 天" else "已过 ${Math.abs(diff)} 天"
                            )
                            views.setTextViewText(R.id.widget_focus_full_countdown_target, "目标日: ${topCountdown.targetDate.take(10)}")
                        } else {
                            views.setTextViewText(R.id.widget_focus_full_countdown_title, "⏳ 倒数日与里程碑")
                            views.setTextViewText(R.id.widget_focus_full_countdown_days, "--")
                            views.setTextViewText(R.id.widget_focus_full_countdown_target, "点击添加重要目标倒计时")
                        }

                        // 2. 今日专注时长战报
                        val todayIso = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        val allFocusRecords = db.focusDao().getAllFocusRecords().first()
                        val todayRecords = allFocusRecords.filter { it.startTime.startsWith(todayIso) }
                        val todayMins = todayRecords.sumOf { it.durationMinutes }
                        val todayCount = todayRecords.size
                        views.setTextViewText(
                            R.id.widget_focus_full_stats,
                            "今日专注 ${todayMins}分钟 · ${todayCount}次心流"
                        )

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
