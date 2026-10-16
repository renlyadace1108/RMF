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
import com.renly.rmf.data.local.entity.ReminderEntity
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
        advanceMinutes: Int = 10,
        reminderType: ReminderType? = null
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

            val actualReminderType = reminderType ?: ReminderType.ALL

            val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                putExtra(ScheduleAlarmReceiver.EXTRA_TITLE, title)
                putExtra(ScheduleAlarmReceiver.EXTRA_MESSAGE, msg)
                putExtra(ScheduleAlarmReceiver.EXTRA_ID, scheduleId.hashCode())
                putExtra(ScheduleAlarmReceiver.EXTRA_IS_COURSE, false)
                putExtra(ScheduleAlarmReceiver.EXTRA_COURSE_START_MILLIS, startMillis)
                putExtra(ScheduleAlarmReceiver.EXTRA_REMINDER_TYPE, actualReminderType.code)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                scheduleId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                // 优先使用最高优先级系统闹钟 setAlarmClock，豁免系统深度休眠与后台冻结
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
     * 课表专属胶囊通知上课提醒 (Capsule & Expanded View)
     */
    fun scheduleCourseReminder(
        context: Context,
        reminderKey: String,
        courseName: String,
        location: String,
        teacher: String,
        startTime: LocalDateTime,
        durationMinutes: Int,
        advanceMinutes: Int = 15,
        reminderType: ReminderType? = null
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

            val actualReminderType = reminderType ?: ReminderType.ALL

            val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                putExtra(ScheduleAlarmReceiver.EXTRA_TITLE, courseName)
                putExtra(ScheduleAlarmReceiver.EXTRA_MESSAGE, msg)
                putExtra(ScheduleAlarmReceiver.EXTRA_ID, reminderKey.hashCode())
                putExtra(ScheduleAlarmReceiver.EXTRA_IS_COURSE, true)
                putExtra(ScheduleAlarmReceiver.EXTRA_COURSE_LOCATION, location)
                putExtra(ScheduleAlarmReceiver.EXTRA_COURSE_TEACHER, teacher)
                putExtra(ScheduleAlarmReceiver.EXTRA_COURSE_START_MILLIS, startEpochMillis)
                putExtra(ScheduleAlarmReceiver.EXTRA_COURSE_DURATION_MINUTES, durationMinutes)
                putExtra(ScheduleAlarmReceiver.EXTRA_REMINDER_TYPE, actualReminderType.code)
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
     * 单点自定义事件提醒 (支持单点倒计时、实时活动借道与完成/稍后推迟交互)
     */
    fun scheduleDirectReminder(context: Context, reminder: ReminderEntity) {
        if (reminder.isDeleted == 1 || reminder.isCompleted == 1 || reminder.advanceMinutes < 0) {
            cancelDirectReminder(context, reminder.id)
            return
        }

        try {
            val remindLdt = try {
                LocalDateTime.parse(reminder.remindTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            } catch (_: Exception) {
                LocalDateTime.parse(reminder.remindTime, DateTimeFormatter.ISO_DATE_TIME)
            }

            val triggerTime = remindLdt.minusMinutes(reminder.advanceMinutes.toLong())
            val triggerEpoch = triggerTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val targetEpoch = remindLdt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            if (triggerEpoch <= System.currentTimeMillis()) {
                return
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val msg = if (reminder.advanceMinutes == 0) {
                "${reminder.title} 时间已到 (${remindLdt.format(DateTimeFormatter.ofPattern("HH:mm"))})"
            } else {
                "${reminder.title} 将在 ${remindLdt.format(DateTimeFormatter.ofPattern("HH:mm"))} 到期 (${reminder.advanceMinutes}分钟后)"
            }

            val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                putExtra(ScheduleAlarmReceiver.EXTRA_TITLE, reminder.title)
                putExtra(ScheduleAlarmReceiver.EXTRA_MESSAGE, reminder.content.ifBlank { msg })
                putExtra(ScheduleAlarmReceiver.EXTRA_ID, reminder.id.hashCode())
                putExtra(ScheduleAlarmReceiver.EXTRA_IS_DIRECT_REMINDER, true)
                putExtra(ScheduleAlarmReceiver.EXTRA_REMINDER_ID, reminder.id)
                putExtra(ScheduleAlarmReceiver.EXTRA_COURSE_START_MILLIS, targetEpoch)
                putExtra(ScheduleAlarmReceiver.EXTRA_REMINDER_TYPE, reminder.reminderType)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminder.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                // 原生 AlarmClock 对齐唤醒，ColorOS 及主流系统零功耗豁免休眠机制
                val clockInfo = AlarmManager.AlarmClockInfo(triggerEpoch, pendingIntent)
                alarmManager.setAlarmClock(clockInfo, pendingIntent)
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerEpoch,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerEpoch,
                        pendingIntent
                    )
                }
            }
        } catch (_: Exception) { }
    }

    fun cancelDirectReminder(context: Context, reminderId: String) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ScheduleAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminderId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        } catch (_: Exception) { }
    }

    /**
     * 发送即时测试通知 (根据当前全局设定的声音与震动规则进行精准触发)
     */
    fun sendStandardTestNotification(context: Context, reminderType: ReminderType? = null) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            9001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val actualType = reminderType ?: ReminderType.ALL
        val channelId = when (actualType) {
            ReminderType.ALL -> RmfApplication.CHANNEL_SCHEDULE_REMINDER
            ReminderType.ALARM -> RmfApplication.CHANNEL_REMINDER_SOUND_ONLY
            ReminderType.VIBRATE -> RmfApplication.CHANNEL_REMINDER_VIBRATE_ONLY
            ReminderType.NONE -> RmfApplication.CHANNEL_REMINDER_SILENT
        }

        val defaultSound = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_logo)
            .setColor(0xFF00F0FF.toInt())
            .setContentTitle("${actualType.emoji} [${actualType.title}] 日程到期就绪")
            .setContentText("专注时间块即将开始！当前提醒模式: ${actualType.title} (${actualType.description})")
            .setPriority(if (actualType == ReminderType.NONE) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, actualType != ReminderType.NONE)
            .setSubText("RMF 日程提醒 · ${actualType.title}")

        when (actualType) {
            ReminderType.ALL -> {
                builder.setSound(defaultSound)
                builder.setVibrate(longArrayOf(0, 350, 200, 350))
                builder.setDefaults(NotificationCompat.DEFAULT_ALL)
            }
            ReminderType.ALARM -> {
                builder.setSound(defaultSound)
                builder.setVibrate(longArrayOf(0))
                builder.setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_LIGHTS)
            }
            ReminderType.VIBRATE -> {
                builder.setSound(null)
                builder.setVibrate(longArrayOf(0, 350, 200, 350))
                builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
            }
            ReminderType.NONE -> {
                builder.setSound(null)
                builder.setVibrate(longArrayOf(0))
                builder.setDefaults(0)
            }
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(9001, builder.build())
    }

    /**
     * 发送即时胶囊通知提醒测试 (调用 ScheduleAlarmReceiver 广播立即呈现胶囊通知)
     */
    fun sendFluidCloudTestReminder(context: Context, isCourse: Boolean = false, reminderType: ReminderType? = null) {
        val actualType = reminderType ?: ReminderType.ALL
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            putExtra(ScheduleAlarmReceiver.EXTRA_REMINDER_TYPE, actualType.code)
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
     * 启动胶囊通知前台实时服务 (状态栏实时胶囊 + 锁屏通知 + 倒计时)
     */
    fun startFluidCloudLiveSession(context: Context, title: String = "胶囊通知实时测试", seconds: Int = 25 * 60) {
        FocusLiveService.start(context, title, seconds)
    }

    /**
     * 停止胶囊通知实时服务
     */
    fun stopFluidCloudLiveSession(context: Context) {
        FocusLiveService.stop(context)
    }
}
