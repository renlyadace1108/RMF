package com.renly.rmf.domain.service

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.renly.rmf.MainActivity
import com.renly.rmf.R
import com.renly.rmf.RmfApplication
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
        // 如果设置为不提醒 (-1)，取消旧闹钟并直接返回
        if (advanceMinutes < 0) {
            cancelReminder(context, scheduleId)
            return
        }

        try {
            val start = try {
                LocalDateTime.parse(startTimeStr, DateTimeFormatter.ISO_DATE_TIME)
            } catch (_: Exception) {
                LocalDateTime.parse(startTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            }
            val reminderTime = start.minusMinutes(advanceMinutes.toLong())
            val epochMillis = reminderTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val startMillis = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            if (epochMillis <= System.currentTimeMillis()) {
                // 已过提醒时间，不予触发
                return
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val msg = if (advanceMinutes == 0) {
                "$title 现在开始 (${start.format(DateTimeFormatter.ofPattern("HH:mm"))})"
            } else {
                "$title 将在 ${start.format(DateTimeFormatter.ofPattern("HH:mm"))} 开始 ($advanceMinutes 分钟后)"
            }

            val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                putExtra(ScheduleAlarmReceiver.EXTRA_TITLE, title)
                putExtra(ScheduleAlarmReceiver.EXTRA_MESSAGE, msg)
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
        if (advanceMinutes < 0) {
            cancelReminder(context, reminderKey)
            return
        }

        try {
            val reminderTime = startTime.minusMinutes(advanceMinutes.toLong())
            val epochMillis = reminderTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val startEpochMillis = startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            if (epochMillis <= System.currentTimeMillis()) {
                return
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val locInfo = if (location.isNotBlank()) " [$location]" else ""
            val msg = if (advanceMinutes == 0) {
                "现在开始上课$locInfo (${startTime.format(DateTimeFormatter.ofPattern("HH:mm"))})"
            } else {
                "$advanceMinutes 分钟后上课$locInfo (${startTime.format(DateTimeFormatter.ofPattern("HH:mm"))})"
            }

            val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                putExtra(ScheduleAlarmReceiver.EXTRA_TITLE, courseName)
                putExtra(ScheduleAlarmReceiver.EXTRA_MESSAGE, msg)
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

    /**
     * 发送即时测试通知 (标准高优先级横幅与通知中心声音振动)
     */
    fun sendStandardTestNotification(context: Context) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            9001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSound = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
        val builder = NotificationCompat.Builder(context, RmfApplication.CHANNEL_SCHEDULE_REMINDER)
            .setSmallIcon(R.drawable.ic_logo)
            .setColor(0xFF00F0FF.toInt())
            .setContentTitle("🔔 [强提醒] 日程到期就绪")
            .setContentText("专注时间块即将开始！声音、震动与高优先级横幅已正常触发。")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(defaultSound)
            .setVibrate(longArrayOf(0, 350, 200, 350))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setSubText("RMF 日程提醒")

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(9001, builder.build())
    }

    /**
     * 发送即时流体云胶囊提醒测试 (调用 ScheduleAlarmReceiver 广播立即呈现流体云胶囊)
     */
    fun sendFluidCloudTestReminder(context: Context, isCourse: Boolean = false) {
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            if (isCourse) {
                putExtra(ScheduleAlarmReceiver.EXTRA_TITLE, "高等数学(AI) 示范课")
                putExtra(ScheduleAlarmReceiver.EXTRA_MESSAGE, "15 分钟后上课 [理科楼 A302]")
                putExtra(ScheduleAlarmReceiver.EXTRA_ID, 9002)
                putExtra(ScheduleAlarmReceiver.EXTRA_IS_COURSE, true)
                putExtra(ScheduleAlarmReceiver.EXTRA_COURSE_LOCATION, "理科楼 A302")
                putExtra(ScheduleAlarmReceiver.EXTRA_COURSE_TEACHER, "张教授")
                putExtra(ScheduleAlarmReceiver.EXTRA_COURSE_START_MILLIS, System.currentTimeMillis() + 15 * 60 * 1000L)
                putExtra(ScheduleAlarmReceiver.EXTRA_COURSE_DURATION_MINUTES, 45)
            } else {
                putExtra(ScheduleAlarmReceiver.EXTRA_TITLE, "深度架构设计与核心编码")
                putExtra(ScheduleAlarmReceiver.EXTRA_MESSAGE, "10 分钟后开始专注，已就绪")
                putExtra(ScheduleAlarmReceiver.EXTRA_ID, 9003)
                putExtra(ScheduleAlarmReceiver.EXTRA_IS_COURSE, false)
                putExtra(ScheduleAlarmReceiver.EXTRA_COURSE_START_MILLIS, System.currentTimeMillis() + 10 * 60 * 1000L)
            }
        }
        context.sendBroadcast(intent)
    }

    /**
     * 启动 ColorOS 流体云前台实时服务 (状态栏实时胶囊 + 锁屏通知 + 倒计时)
     */
    fun startFluidCloudLiveSession(context: Context, title: String = "流体云实时胶囊测试", seconds: Int = 25 * 60) {
        FocusLiveService.start(context, title, seconds)
    }

    /**
     * 停止 ColorOS 流体云实时服务
     */
    fun stopFluidCloudLiveSession(context: Context) {
        FocusLiveService.stop(context)
    }
}
