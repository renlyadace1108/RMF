package com.renly.rmf.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.renly.rmf.MainActivity
import com.renly.rmf.R
import com.renly.rmf.RmfApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class RmfAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, RmfAppWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (appWidgetId in allWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.renly.rmf.ACTION_REFRESH_WIDGET"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_rmf_desktop)

            // 点击整个小组件打开主界面
            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)

            // 点击刷新按钮
            val refreshIntent = Intent(context, RmfAppWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context, 1, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh_btn, refreshPendingIntent)

            // 当前日期文本
            val today = LocalDate.now()
            val dateStr = today.format(DateTimeFormatter.ofPattern("MM-dd EEE"))
            views.setTextViewText(R.id.widget_date_text, dateStr)

            // 异步查询数据库装配数据
            val app = context.applicationContext as? RmfApplication
            if (app != null) {
                val db = app.database
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // 1. 北极星目标
                        val goals = db.goalDao().getAllActiveGoals().first()
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
                        val courses = db.courseDao().getAllActiveCourses().first()
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
                        val startOfDay = today.atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        val endOfDay = today.plusDays(1).atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        val schedules = db.scheduleDao().getSchedulesByDateRange(startOfDay, endOfDay).first()
                        val pendingSchedules = schedules.filter { it.status != "COMPLETED" }

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
                            views.setTextViewText(R.id.widget_task1, "今日重点日程均已顺利完成！🎉")
                            views.setTextViewText(R.id.widget_task2, "")
                            views.setTextViewText(R.id.widget_task3, "")
                        }

                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else {
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
