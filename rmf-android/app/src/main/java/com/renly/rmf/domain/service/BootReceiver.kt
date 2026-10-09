package com.renly.rmf.domain.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.renly.rmf.RmfApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 手机开机自启或 ColorOS 系统唤醒广播：
 * 重新加载未来 24 小时内的未完成日程并注册精准系统闹钟，确保重启后提醒永不丢失
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            val app = context.applicationContext as? RmfApplication ?: return
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = app.database
                    val now = LocalDateTime.now()
                    val schedules = db.scheduleDao().getAllRawSchedules().filter {
                        it.isDeleted == 0 && it.status != "COMPLETED"
                    }

                    for (schedule in schedules) {
                        try {
                            val start = LocalDateTime.parse(schedule.startTime, DateTimeFormatter.ISO_DATE_TIME)
                            if (start.isAfter(now)) {
                                ScheduleReminderManager.scheduleReminder(
                                    context = context,
                                    scheduleId = schedule.id,
                                    title = schedule.title,
                                    startTimeStr = schedule.startTime,
                                    advanceMinutes = 10
                                )
                            }
                        } catch (_: Exception) {}
                    }

                    // 重新排期并注册未来有效课程的流体云上课提醒
                    CourseReminderScheduler.scheduleUpcomingCourseReminders(
                        context = context,
                        courseDao = db.courseDao(),
                        daysAhead = 3
                    )
                } catch (_: Exception) {}
            }
        }
    }
}
