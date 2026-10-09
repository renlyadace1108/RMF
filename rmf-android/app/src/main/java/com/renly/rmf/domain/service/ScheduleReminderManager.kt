package com.renly.rmf.domain.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ScheduleReminderManager {

    /**
     * 普通通用日程提醒
     */
    fun scheduleReminder(
        context: Context,
        scheduleId: String,
        title: String,
        startTimeStr: String,
        advanceMinutes: Int = 10
    ) {
        try {
            val start = LocalDateTime.parse(startTimeStr, DateTimeFormatter.ISO_DATE_TIME)
            val reminderTime = start.minusMinutes(advanceMinutes.toLong())
            val epochMillis = reminderTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val startMillis = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            if (epochMillis <= System.currentTimeMillis()) {
                // 已过提醒时间，不予触发
                return
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                putExtra(ScheduleAlarmReceiver.EXTRA_TITLE, title)
                putExtra(ScheduleAlarmReceiver.EXTRA_MESSAGE, "$title 将在 ${start.format(DateTimeFormatter.ofPattern("HH:mm"))} 开始 ($advanceMinutes 分钟后)")
                putExtra(ScheduleAlarmReceiver.EXTRA_ID, scheduleId.hashCode())
                putExtra(ScheduleAlarmReceiver.EXTRA_IS_COURSE, false)
                putExtra(ScheduleAlarmReceiver.EXTRA_COURSE_START_MILLIS, startMillis)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                scheduleId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                // 优先使用最高优先级系统闹钟 setAlarmClock，100% 豁免 OPPO ColorOS 深度休眠与后台冻结
                val clockInfo = AlarmManager.AlarmClockInfo(epochMillis, pendingIntent)
                alarmManager.setAlarmClock(clockInfo, pendingIntent)
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        epochMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        epochMillis,
                        pendingIntent
                    )
                }
            }
        } catch (_: Exception) { }
    }

    /**
     * 课表专属流体云上课提醒 (ColorOS Fluid Cloud Capsule & Expanded View)
     */
    fun scheduleCourseReminder(
        context: Context,
        reminderKey: String,
        courseName: String,
        location: String,
        teacher: String,
        startTime: LocalDateTime,
        durationMinutes: Int,
        advanceMinutes: Int = 15
    ) {
        try {
            val reminderTime = startTime.minusMinutes(advanceMinutes.toLong())
            val epochMillis = reminderTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val startEpochMillis = startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            if (epochMillis <= System.currentTimeMillis()) {
                return
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val locInfo = if (location.isNotBlank()) " [$location]" else ""
            val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                putExtra(ScheduleAlarmReceiver.EXTRA_TITLE, courseName)
                putExtra(ScheduleAlarmReceiver.EXTRA_MESSAGE, "$advanceMinutes 分钟后上课$locInfo (${startTime.format(DateTimeFormatter.ofPattern("HH:mm"))})")
                putExtra(ScheduleAlarmReceiver.EXTRA_ID, reminderKey.hashCode())
                putExtra(ScheduleAlarmReceiver.EXTRA_IS_COURSE, true)
                putExtra(ScheduleAlarmReceiver.EXTRA_COURSE_LOCATION, location)
                putExtra(ScheduleAlarmReceiver.EXTRA_COURSE_TEACHER, teacher)
                putExtra(ScheduleAlarmReceiver.EXTRA_COURSE_START_MILLIS, startEpochMillis)
                putExtra(ScheduleAlarmReceiver.EXTRA_COURSE_DURATION_MINUTES, durationMinutes)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminderKey.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                val clockInfo = AlarmManager.AlarmClockInfo(epochMillis, pendingIntent)
                alarmManager.setAlarmClock(clockInfo, pendingIntent)
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        epochMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        epochMillis,
                        pendingIntent
                    )
                }
            }
        } catch (_: Exception) { }
    }

    fun cancelReminder(context: Context, scheduleId: String) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ScheduleAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                scheduleId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        } catch (_: Exception) { }
    }
}
