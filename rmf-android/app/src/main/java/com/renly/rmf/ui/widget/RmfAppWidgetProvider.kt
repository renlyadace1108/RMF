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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class RmfAppWidgetProvider : AppWidgetProvider() {

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
            val thisWidget = ComponentName(context, RmfAppWidgetProvider::class.java)
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

            val views = RemoteViews(context.packageName, R.layout.widget_rmf_desktop)

            // 根视图点击 -> 打开日程主界面
            views.setOnClickPendingIntent(
                R.id.widget_root,
                RmfWidgetManager.createNavPendingIntent(context, "schedule", 100)
            )

            // 快捷刷新按钮
            views.setOnClickPendingIntent(
                R.id.widget_refresh_btn,
                RmfWidgetManager.createRefreshPendingIntent(context, RmfAppWidgetProvider::class.java, 101)
            )

            // 快捷发起专注
            views.setOnClickPendingIntent(
                R.id.widget_quick_focus_btn,
                RmfWidgetManager.createNavPendingIntent(context, "focus", 102)
            )

            // 快捷新建日程 (快速捕获)
            views.setOnClickPendingIntent(
                R.id.widget_quick_add_btn,
                RmfWidgetManager.createActionPendingIntent(context, "quick_capture", 103)
            )

            // 倒数日容器点击 -> 深度跳转倒数日
            views.setOnClickPendingIntent(
                R.id.widget_countdown_container,
                RmfWidgetManager.createNavPendingIntent(context, "countdowns", 104)
            )

            // 北极星目标容器点击 -> 深度跳转目标
            views.setOnClickPendingIntent(
                R.id.widget_north_star_container,
                RmfWidgetManager.createNavPendingIntent(context, "goals", 105)
            )

            // 课表容器点击 -> 深度跳转课表
            views.setOnClickPendingIntent(
                R.id.widget_courses_container,
                RmfWidgetManager.createNavPendingIntent(context, "timetable", 106)
            )

            // 日程容器点击 -> 深度跳转日程
            views.setOnClickPendingIntent(
                R.id.widget_tasks_container,
                RmfWidgetManager.createNavPendingIntent(context, "schedule", 107)
            )

            // 当前日期文本
            val today = LocalDate.now()
            val dateStr = today.format(DateTimeFormatter.ofPattern("MM-dd EEE"))
            views.setTextViewText(R.id.widget_date_text, dateStr)

            // 异步查询数据库批量装配数据 (单次读取，批量推送至全部桌面组件实例)
            val app = context.applicationContext as? RmfApplication
            if (app != null) {
                val db = app.database
                widgetScope.launch {
                    try {
                        val startOfDay = today.atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        val endOfDay = today.plusDays(1).atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

                        // 并行发起4路数据库查询，充分利用 WAL 并发，大幅缩短桌面小组件渲染等待
                        val countdownsDef = async { db.countdownDao().getAllActiveCountdowns().first() }
                        val goalsDef = async { db.goalDao().getAllActiveGoals().first() }
                        val coursesDef = async { db.courseDao().getAllActiveCourses().first() }
                        val schedulesDef = async { db.scheduleDao().getSchedulesByDateRange(startOfDay, endOfDay).first() }

                        val countdowns = countdownsDef.await()
                        val goals = goalsDef.await()
                        val courses = coursesDef.await()
                        val schedules = schedulesDef.await()

                        // 0. 倒数日 / 里程碑
                        val topCountdown = countdowns.firstOrNull { it.isPinned == 1 } ?: countdowns.firstOrNull()
                        if (topCountdown != null) {
                            val target = try {
                                LocalDate.parse(topCountdown.targetDate.take(10))
                            } catch (_: Exception) {
                                today
                            }
                            val diff = java.time.temporal.ChronoUnit.DAYS.between(today, target)
                            views.setTextViewText(R.id.widget_countdown_title, "⏳ " + topCountdown.title)
                            views.setTextViewText(R.id.widget_countdown_days, if (diff >= 0) "还剩 $diff 天" else "已过 ${Math.abs(diff)} 天")
                        } else {
                            views.setTextViewText(R.id.widget_countdown_title, "⏳ 考研 / 期末 / 里程碑倒数日")
                            views.setTextViewText(R.id.widget_countdown_days, "-")
                        }

                        // 1. 北极星目标
                        val northStar = goals.firstOrNull { it.isNorthStar == 1 }
                        if (northStar != null) {
                            views.setTextViewText(R.id.widget_north_star_title, northStar.title)
                            views.setTextViewText(R.id.widget_north_star_progress, "${northStar.progress}%")
                        } else {
                            views.setTextViewText(R.id.widget_north_star_title, "暂未设定置顶北极星目标")
                            views.setTextViewText(R.id.widget_north_star_progress, "-")
                        }

                        // 2. 今日课程
                        val todayDayOfWeek = today.dayOfWeek.value // 1..7
                        val todayCourses = courses.filter { it.dayOfWeek == todayDayOfWeek }
                        if (todayCourses.isNotEmpty()) {
                            val courseBrief = todayCourses.take(2).joinToString(" | ") {
                                "${it.name} (${it.location.ifBlank { "无教室" }})"
                            }
                            views.setTextViewText(R.id.widget_courses_text, courseBrief)
                        } else {
                            views.setTextViewText(R.id.widget_courses_text, "今日无课 / 自主攻坚日")
                        }

                        // 3. 今日日程任务
                        val pendingSchedules = schedules.filter { it.status != "COMPLETED" }
                        val completedCount = schedules.size - pendingSchedules.size

                        val summaryText = if (schedules.isNotEmpty()) {
                            "已完成 $completedCount/${schedules.size} 项 (${(completedCount * 100 / schedules.size)}%)"
                        } else {
                            "暂无日程"
                        }
                        views.setTextViewText(R.id.widget_tasks_summary, summaryText)

                        if (pendingSchedules.isNotEmpty()) {
                            views.setTextViewText(R.id.widget_task1, "1. " + pendingSchedules[0].title)
                            if (pendingSchedules.size > 1) {
                                views.setTextViewText(R.id.widget_task2, "2. " + pendingSchedules[1].title)
                            } else {
                                views.setTextViewText(R.id.widget_task2, "")
                            }
                            if (pendingSchedules.size > 2) {
                                views.setTextViewText(R.id.widget_task3, "3. " + pendingSchedules[2].title)
                            } else {
                                views.setTextViewText(R.id.widget_task3, "")
                            }
                        } else {
                            views.setTextViewText(R.id.widget_task1, if (schedules.isNotEmpty()) "今日重点日程均已顺利完成！🎉" else "今日暂无待排期日程，点击新建")
                            views.setTextViewText(R.id.widget_task2, "")
                            views.setTextViewText(R.id.widget_task3, "")
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
