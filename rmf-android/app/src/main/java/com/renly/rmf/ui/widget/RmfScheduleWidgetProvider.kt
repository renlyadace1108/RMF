package com.renly.rmf.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.renly.rmf.R
import com.renly.rmf.RmfApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class RmfScheduleWidgetProvider : AppWidgetProvider() {

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
            val thisWidget = ComponentName(context, RmfScheduleWidgetProvider::class.java)
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

            val views = RemoteViews(context.packageName, R.layout.widget_rmf_schedule)

            // 点击整个小组件跳转至日程页
            views.setOnClickPendingIntent(
                R.id.widget_schedule_root,
                RmfWidgetManager.createNavPendingIntent(context, "schedule", 200)
            )

            // 快捷新建日程
            views.setOnClickPendingIntent(
                R.id.widget_schedule_add_btn,
                RmfWidgetManager.createActionPendingIntent(context, "quick_capture", 201)
            )

            // 刷新按钮
            views.setOnClickPendingIntent(
                R.id.widget_schedule_refresh_btn,
                RmfWidgetManager.createRefreshPendingIntent(context, RmfScheduleWidgetProvider::class.java, 202)
            )

            val today = LocalDate.now()
            views.setTextViewText(R.id.widget_schedule_date, today.format(DateTimeFormatter.ofPattern("MM-dd EEE")))

            val app = context.applicationContext as? RmfApplication
            if (app != null) {
                val db = app.database
                widgetScope.launch {
                    try {
                        val startOfDay = today.atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        val endOfDay = today.plusDays(1).atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        val schedules = db.scheduleDao().getSchedulesByDateRange(startOfDay, endOfDay).first()
                        val pending = schedules.filter { it.status != "COMPLETED" }
                        val completedCount = schedules.size - pending.size

                        views.setTextViewText(
                            R.id.widget_schedule_progress,
                            if (schedules.isNotEmpty()) "完成 $completedCount/${schedules.size} 项" else "暂无日程"
                        )

                        // 优先显示未完成日程，未完成不够时补充已完成日程
                        val displayList = (pending + schedules.filter { it.status == "COMPLETED" }).take(4)

                        val rowIds = listOf(
                            R.id.widget_sched_row_1 to Triple(R.id.widget_sched_icon_1, R.id.widget_sched_time_1, R.id.widget_sched_title_1),
                            R.id.widget_sched_row_2 to Triple(R.id.widget_sched_icon_2, R.id.widget_sched_time_2, R.id.widget_sched_title_2),
                            R.id.widget_sched_row_3 to Triple(R.id.widget_sched_icon_3, R.id.widget_sched_time_3, R.id.widget_sched_title_3),
                            R.id.widget_sched_row_4 to Triple(R.id.widget_sched_icon_4, R.id.widget_sched_time_4, R.id.widget_sched_title_4)
                        )

                        for (i in 0 until 4) {
                            val (rowId, viewsTriple) = rowIds[i]
                            val (iconId, timeId, titleId) = viewsTriple
                            if (i < displayList.size) {
                                val item = displayList[i]
                                val isDone = item.status == "COMPLETED"
                                views.setViewVisibility(rowId, View.VISIBLE)
                                views.setTextViewText(iconId, if (isDone) "✅" else "⭕")
                                val timeText = if (item.startTime.length >= 16) item.startTime.substring(11, 16) else ""
                                views.setTextViewText(timeId, timeText)
                                views.setTextViewText(titleId, item.title)
                                views.setOnClickPendingIntent(
                                    rowId,
                                    RmfWidgetManager.createNavPendingIntent(context, "schedule", 210 + i)
                                )
                            } else {
                                if (i == 0 && displayList.isEmpty()) {
                                    views.setViewVisibility(rowId, View.VISIBLE)
                                    views.setTextViewText(iconId, "🎉")
                                    views.setTextViewText(timeId, "")
                                    views.setTextViewText(titleId, "今日全部日程已顺利攻克！点击添加")
                                } else {
                                    views.setViewVisibility(rowId, View.GONE)
                                }
                            }
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
