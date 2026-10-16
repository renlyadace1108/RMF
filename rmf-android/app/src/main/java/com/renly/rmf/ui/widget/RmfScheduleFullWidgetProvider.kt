package com.renly.rmf.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
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

/**
 * RmfScheduleFullWidgetProvider
 * 4x4 全尺寸今日日程攻坚看板小组件（支持8项完整日程纵深排期）
 */
class RmfScheduleFullWidgetProvider : AppWidgetProvider() {

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
            val thisWidget = ComponentName(context, RmfScheduleFullWidgetProvider::class.java)
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

            val views = RemoteViews(context.packageName, R.layout.widget_rmf_schedule_full)

            // 点击整个小组件跳转至日程页
            views.setOnClickPendingIntent(
                R.id.widget_sched_full_root,
                RmfWidgetManager.createNavPendingIntent(context, "schedule", 1200)
            )

            // 快捷新建日程
            views.setOnClickPendingIntent(
                R.id.widget_sched_full_add_btn,
                RmfWidgetManager.createActionPendingIntent(context, "quick_capture", 1201)
            )

            // 刷新按钮
            views.setOnClickPendingIntent(
                R.id.widget_sched_full_refresh_btn,
                RmfWidgetManager.createRefreshPendingIntent(context, RmfScheduleFullWidgetProvider::class.java, 1202)
            )

            // 底部快捷新建
            views.setOnClickPendingIntent(
                R.id.widget_sched_full_bottom_add,
                RmfWidgetManager.createActionPendingIntent(context, "quick_capture", 1203)
            )

            // 底部进入主界面
            views.setOnClickPendingIntent(
                R.id.widget_sched_full_bottom_view_all,
                RmfWidgetManager.createNavPendingIntent(context, "schedule", 1204)
            )

            val today = LocalDate.now()
            views.setTextViewText(R.id.widget_sched_full_date, today.format(DateTimeFormatter.ofPattern("MM-dd EEE")))

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

                        val percent = if (schedules.isNotEmpty()) completedCount * 100 / schedules.size else 0
                        views.setTextViewText(
                            R.id.widget_sched_full_progress,
                            if (schedules.isNotEmpty()) "完成 $completedCount/${schedules.size} 项 ($percent%)" else "暂无日程"
                        )

                        // 优先显示未完成日程，未完成不够时补充已完成日程，满幅展现多达 8 项日程
                        val displayList = (pending + schedules.filter { it.status == "COMPLETED" }).take(8)

                        val rowIds = listOf(
                            Triple(R.id.widget_sched_full_row_1, R.id.widget_sched_full_icon_1, Pair(R.id.widget_sched_full_time_1, R.id.widget_sched_full_title_1)),
                            Triple(R.id.widget_sched_full_row_2, R.id.widget_sched_full_icon_2, Pair(R.id.widget_sched_full_time_2, R.id.widget_sched_full_title_2)),
                            Triple(R.id.widget_sched_full_row_3, R.id.widget_sched_full_icon_3, Pair(R.id.widget_sched_full_time_3, R.id.widget_sched_full_title_3)),
                            Triple(R.id.widget_sched_full_row_4, R.id.widget_sched_full_icon_4, Pair(R.id.widget_sched_full_time_4, R.id.widget_sched_full_title_4)),
                            Triple(R.id.widget_sched_full_row_5, R.id.widget_sched_full_icon_5, Pair(R.id.widget_sched_full_time_5, R.id.widget_sched_full_title_5)),
                            Triple(R.id.widget_sched_full_row_6, R.id.widget_sched_full_icon_6, Pair(R.id.widget_sched_full_time_6, R.id.widget_sched_full_title_6)),
                            Triple(R.id.widget_sched_full_row_7, R.id.widget_sched_full_icon_7, Pair(R.id.widget_sched_full_time_7, R.id.widget_sched_full_title_7)),
                            Triple(R.id.widget_sched_full_row_8, R.id.widget_sched_full_icon_8, Pair(R.id.widget_sched_full_time_8, R.id.widget_sched_full_title_8))
                        )

                        for (i in 0 until 8) {
                            val (rowId, iconId, textPair) = rowIds[i]
                            val (timeId, titleId) = textPair
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
                                    RmfWidgetManager.createNavPendingIntent(context, "schedule", 1210 + i)
                                )
                            } else {
                                if (i == 0 && displayList.isEmpty()) {
                                    views.setViewVisibility(rowId, View.VISIBLE)
                                    views.setTextViewText(iconId, "🎉")
                                    views.setTextViewText(timeId, "")
                                    views.setTextViewText(titleId, "今日全部日程均已顺利攻克！点击添加")
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
