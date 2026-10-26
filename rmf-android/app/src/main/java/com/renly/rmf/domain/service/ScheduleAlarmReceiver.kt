package com.renly.rmf.domain.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.renly.rmf.MainActivity
import com.renly.rmf.R
import com.renly.rmf.RmfApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 获取短时唤醒锁 (10秒)，保证广播分发与数据库写入期间 CPU 不被系统强行打断休眠
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = try {
            powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "rmf:alarm_receiver")?.apply {
                acquire(10000L)
            }
        } catch (_: Exception) {
            null
        }

        val intentAction = intent.action
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 处理自定义事件提醒的交互动作 (完成 / 稍后提醒)
        if (intentAction == ACTION_COMPLETE_REMINDER) {
            val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
            val notifId = intent.getIntExtra(EXTRA_ID, 1001)
            manager.cancel(notifId)

            val app = context.applicationContext as? RmfApplication
            if (app != null) {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val reminder = app.database.reminderDao().getReminderById(reminderId)
                        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

                        if (reminder != null && !reminder.repeatRule.equals("NONE", ignoreCase = true)) {
                            // 周期性提醒：推进到下一个周期，重置为待提醒状态 (is_completed=0)，并排期下一次闹钟
                            val nextIso = ReminderRepeatHelper.computeNextOccurrence(reminder.remindTime, reminder.repeatRule)
                            if (nextIso != null) {
                                app.database.reminderDao().advanceRepeatCycle(reminderId, nextIso, 0, nowStr)
                                val updated = reminder.copy(remindTime = nextIso, isCompleted = 0, updatedAt = nowStr)
                                ScheduleReminderManager.scheduleDirectReminder(context, updated)
                                if (SystemCalendarSyncService.isAutoSyncEnabled(context)) {
                                    SystemCalendarSyncService.syncSingleReminder(context, updated)
                                }
                            } else {
                                app.database.reminderDao().toggleComplete(reminderId, 1, nowStr)
                                SystemCalendarSyncService.deleteReminderEvent(context, reminderId)
                            }
                        } else {
                            // 单次提醒：标记完成
                            app.database.reminderDao().toggleComplete(reminderId, 1, nowStr)
                            SystemCalendarSyncService.deleteReminderEvent(context, reminderId)
                        }
                    } finally {
                        try { wakeLock?.release() } catch (_: Exception) {}
                        pendingResult.finish()
                    }
                }
            } else {
                try { wakeLock?.release() } catch (_: Exception) {}
            }
            return
        }

        if (intentAction == ACTION_SNOOZE_REMINDER) {
            val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
            val notifId = intent.getIntExtra(EXTRA_ID, 1001)
            manager.cancel(notifId)

            val app = context.applicationContext as? RmfApplication
            if (app != null) {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val reminder = app.database.reminderDao().getReminderById(reminderId)
                        if (reminder != null) {
                            val snoozedTime = LocalDateTime.now().plusMinutes(10)
                            val snoozedIso = snoozedTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                            val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                            app.database.reminderDao().updateRemindTime(reminderId, snoozedIso, 0, nowStr)

                            val updatedReminder = reminder.copy(
                                remindTime = snoozedIso,
                                advanceMinutes = 0,
                                updatedAt = nowStr
                            )
                            ScheduleReminderManager.scheduleDirectReminder(context, updatedReminder)
                            if (SystemCalendarSyncService.isAutoSyncEnabled(context)) {
                                SystemCalendarSyncService.syncSingleReminder(context, updatedReminder)
                            }
                        }
                    } finally {
                        try { wakeLock?.release() } catch (_: Exception) {}
                        pendingResult.finish()
                    }
                }
            } else {
                try { wakeLock?.release() } catch (_: Exception) {}
            }
            return
        }

        // 常规提醒分发 (课程 / 日程 / 单点事件提醒)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "日程时间块"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "即将开始，请准备进入专注状态"
        val notificationId = intent.getIntExtra(EXTRA_ID, 1001)
        val isCourse = intent.getBooleanExtra(EXTRA_IS_COURSE, false)
        val isDirectReminder = intent.getBooleanExtra(EXTRA_IS_DIRECT_REMINDER, false)
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: ""
        val courseLocation = intent.getStringExtra(EXTRA_COURSE_LOCATION) ?: ""
        val courseTeacher = intent.getStringExtra(EXTRA_COURSE_TEACHER) ?: ""
        val courseStartTimeMillis = intent.getLongExtra(EXTRA_COURSE_START_MILLIS, 0L)
        val courseDurationMinutes = intent.getIntExtra(EXTRA_COURSE_DURATION_MINUTES, 45)

        // 1. 点击跳转回应用
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val reminderTypeCode = intent.getStringExtra(EXTRA_REMINDER_TYPE)
        val reminderType = if (!reminderTypeCode.isNullOrBlank()) {
            ReminderType.fromCode(reminderTypeCode)
        } else {
            ReminderType.ALL
        }

        val channelId = if (isCourse) {
            when (reminderType) {
                ReminderType.ALL -> RmfApplication.CHANNEL_FLUID_CLOUD
                ReminderType.ALARM -> RmfApplication.CHANNEL_REMINDER_SOUND_ONLY
                ReminderType.VIBRATE -> RmfApplication.CHANNEL_REMINDER_VIBRATE_ONLY
                ReminderType.NONE -> RmfApplication.CHANNEL_REMINDER_SILENT
            }
        } else {
            when (reminderType) {
                ReminderType.ALL -> RmfApplication.CHANNEL_SCHEDULE_REMINDER
                ReminderType.ALARM -> RmfApplication.CHANNEL_REMINDER_SOUND_ONLY
                ReminderType.VIBRATE -> RmfApplication.CHANNEL_REMINDER_VIBRATE_ONLY
                ReminderType.NONE -> RmfApplication.CHANNEL_REMINDER_SILENT
            }
        }

        val defaultSound = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_logo)
            .setColor(0xFF00F0FF.toInt())
            .setPriority(if (reminderType == ReminderType.NONE) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MAX)
            .setCategory(if (isCourse) NotificationCompat.CATEGORY_EVENT else NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOngoing(true)
            .setContentIntent(pendingIntent)

        val brandLabel = FluidCloudHelper.getSystemBrandLabel()
        builder.setFullScreenIntent(pendingIntent, reminderType != ReminderType.NONE)
        builder.setSubText(
            when {
                isCourse -> "$brandLabel · 上课提醒"
                isDirectReminder -> "$brandLabel · 事件提醒"
                else -> "$brandLabel · 日程提醒"
            }
        )

        // 精确配置声音与震动规则
        when (reminderType) {
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

        val isLiveUpdateMode = FluidCloudHelper.isPromotedLiveUpdateSupported(context)
        FluidCloudHelper.applyPromotedOngoing(builder)

        if (isDirectReminder) {
            // ==========================================
            // 自定义事件提醒模式 (实时活动倒计时 + 完成 / 稍后推迟操作)
            // ==========================================
            val completeIntent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                this.action = ACTION_COMPLETE_REMINDER
                putExtra(EXTRA_REMINDER_ID, reminderId)
                putExtra(EXTRA_ID, notificationId)
            }
            val completePendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 31,
                completeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val snoozeIntent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                this.action = ACTION_SNOOZE_REMINDER
                putExtra(EXTRA_REMINDER_ID, reminderId)
                putExtra(EXTRA_ID, notificationId)
            }
            val snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 32,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(
                NotificationCompat.Action.Builder(0, "✓ 完成", completePendingIntent).build()
            )
            builder.addAction(
                NotificationCompat.Action.Builder(0, "⏰ 稍后提醒 (+10分)", snoozePendingIntent).build()
            )

            builder.setContentTitle(title)
                .setContentText(message)

            val targetMillis = intent.getLongExtra(EXTRA_COURSE_START_MILLIS, 0L)
            val remMillis = if (targetMillis > System.currentTimeMillis()) targetMillis - System.currentTimeMillis() else 0L
            if (remMillis > 0) {
                builder.setUsesChronometer(true)
                builder.setWhen(System.currentTimeMillis() + remMillis)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    builder.setChronometerCountDown(true)
                }
            }

            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("🔔 $title")
                    .setSummaryText(brandLabel)
                    .bigText(message)
            )

            // 若此事件有周期性重复规则，在触发弹窗的同时异步自动排期下一周期闹钟，确保持续生效永不失效
            if (reminderId.isNotBlank()) {
                val app = context.applicationContext as? RmfApplication
                if (app != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val reminder = app.database.reminderDao().getReminderById(reminderId)
                            if (reminder != null && !reminder.repeatRule.equals("NONE", ignoreCase = true)) {
                                val nextIso = ReminderRepeatHelper.computeNextOccurrence(reminder.remindTime, reminder.repeatRule)
                                if (nextIso != null) {
                                    val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                    app.database.reminderDao().advanceRepeatCycle(reminderId, nextIso, 0, nowStr)
                                    val updated = reminder.copy(remindTime = nextIso, isCompleted = 0, updatedAt = nowStr)
                                    ScheduleReminderManager.scheduleDirectReminder(context, updated)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        } else if (isCourse) {
            // ==========================================
            // 课程提醒模式 (倒计时 + 实时活动 + 无图标按钮)
            // ==========================================
            val focusIntent = Intent(context, FocusLiveService::class.java).apply {
                action = FocusLiveService.ACTION_START
                putExtra(FocusLiveService.EXTRA_TITLE, "听课专注: $title")
                putExtra(FocusLiveService.EXTRA_SECONDS, courseDurationMinutes * 60)
            }
            val focusPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    context,
                    notificationId + 10,
                    focusIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                PendingIntent.getService(
                    context,
                    notificationId + 10,
                    focusIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            val remainingMillis = if (courseStartTimeMillis > System.currentTimeMillis()) {
                courseStartTimeMillis - System.currentTimeMillis()
            } else {
                0L
            }

            builder.addAction(
                NotificationCompat.Action.Builder(
                    0,
                    "开启专注",
                    focusPendingIntent
                ).build()
            )
            builder.addAction(
                NotificationCompat.Action.Builder(
                    0,
                    "查看详情",
                    pendingIntent
                ).build()
            )

            builder.setContentTitle("上课提醒: $title")
                .setContentText(message)

            if (remainingMillis > 0) {
                builder.setUsesChronometer(true)
                builder.setWhen(System.currentTimeMillis() + remainingMillis)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    builder.setChronometerCountDown(true)
                }
            }

            if (isLiveUpdateMode) {
                val detailStr = StringBuilder()
                if (courseLocation.isNotBlank()) detailStr.append("地点: $courseLocation\n")
                if (courseTeacher.isNotBlank()) detailStr.append("教师: $courseTeacher\n")
                detailStr.append("课程时长: ${courseDurationMinutes}分钟\n$message")

                builder.setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle("上课提醒: $title")
                        .setSummaryText(brandLabel)
                        .bigText(detailStr.toString())
                )
            } else {
                val capsuleView = RemoteViews(context.packageName, R.layout.notification_fluid_cloud_course_capsule).apply {
                    setTextViewText(R.id.fluid_course_capsule_title, title)
                    val locText = if (courseLocation.isNotBlank()) courseLocation else "即将开课"
                    setTextViewText(R.id.fluid_course_capsule_location, locText)
                    setOnClickPendingIntent(R.id.fluid_course_capsule_btn, focusPendingIntent)

                    if (remainingMillis > 0) {
                        setChronometer(
                            R.id.fluid_course_capsule_chronometer,
                            SystemClock.elapsedRealtime() + remainingMillis,
                            "%s",
                            true
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            setChronometerCountDown(R.id.fluid_course_capsule_chronometer, true)
                        }
                    } else {
                        setChronometer(R.id.fluid_course_capsule_chronometer, SystemClock.elapsedRealtime(), "进行中", false)
                    }
                }

                val expandedView = RemoteViews(context.packageName, R.layout.notification_fluid_cloud_course_expanded).apply {
                    setTextViewText(R.id.fluid_course_expanded_header, "课程提醒")
                    setTextViewText(R.id.fluid_course_expanded_title, title)
                    val detailStr = StringBuilder()
                    if (courseLocation.isNotBlank()) detailStr.append("地点: $courseLocation  ")
                    if (courseTeacher.isNotBlank()) detailStr.append("教师: $courseTeacher")
                    setTextViewText(R.id.fluid_course_expanded_detail, detailStr.toString().ifBlank { "无地点信息" })
                    setTextViewText(R.id.fluid_course_expanded_time_range, "时长: ${courseDurationMinutes}分钟")

                    setOnClickPendingIntent(R.id.fluid_course_btn_focus, focusPendingIntent)
                    setOnClickPendingIntent(R.id.fluid_course_btn_view, pendingIntent)

                    if (remainingMillis > 0) {
                        setChronometer(
                            R.id.fluid_course_expanded_chronometer,
                            SystemClock.elapsedRealtime() + remainingMillis,
                            "%s",
                            true
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            setChronometerCountDown(R.id.fluid_course_expanded_chronometer, true)
                        }
                    } else {
                        setChronometer(R.id.fluid_course_expanded_chronometer, SystemClock.elapsedRealtime(), "正在上课", false)
                    }
                }

                builder.setCustomContentView(capsuleView)
                    .setCustomBigContentView(expandedView)
                    .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            }
        } else {
            // ==========================================
            // 通用日程时间块提醒 (倒计时 + 实时活动 + 无图标按钮)
            // ==========================================
            val startMillis = intent.getLongExtra(EXTRA_COURSE_START_MILLIS, 0L)
            val remMillis = if (startMillis > System.currentTimeMillis()) startMillis - System.currentTimeMillis() else 0L

            val focusIntent = Intent(context, FocusLiveService::class.java).apply {
                action = FocusLiveService.ACTION_START
                putExtra(FocusLiveService.EXTRA_TITLE, title)
                putExtra(FocusLiveService.EXTRA_SECONDS, 25 * 60)
            }
            val focusPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    context,
                    notificationId + 20,
                    focusIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                PendingIntent.getService(
                    context,
                    notificationId + 20,
                    focusIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            builder.addAction(
                NotificationCompat.Action.Builder(
                    0,
                    "开启专注",
                    focusPendingIntent
                ).build()
            )
            builder.addAction(
                NotificationCompat.Action.Builder(
                    0,
                    "查看日程",
                    pendingIntent
                ).build()
            )

            builder.setContentTitle(title)
                .setContentText(message)

            if (remMillis > 0) {
                builder.setUsesChronometer(true)
                builder.setWhen(System.currentTimeMillis() + remMillis)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    builder.setChronometerCountDown(true)
                }
            }

            if (isLiveUpdateMode) {
                builder.setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(title)
                        .setSummaryText(brandLabel)
                        .bigText(message)
                )
            } else {
                val schedCapsule = RemoteViews(context.packageName, R.layout.notification_fluid_cloud_schedule_capsule).apply {
                    setTextViewText(R.id.fluid_schedule_capsule_title, title)
                    setTextViewText(R.id.fluid_schedule_capsule_sub, message)
                    setOnClickPendingIntent(R.id.fluid_schedule_capsule_btn, focusPendingIntent)

                    if (remMillis > 0) {
                        setChronometer(
                            R.id.fluid_schedule_capsule_chronometer,
                            SystemClock.elapsedRealtime() + remMillis,
                            "%s",
                            true
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            setChronometerCountDown(R.id.fluid_schedule_capsule_chronometer, true)
                        }
                    } else {
                        setChronometer(R.id.fluid_schedule_capsule_chronometer, SystemClock.elapsedRealtime(), "即刻", false)
                    }
                }

                val schedExpanded = RemoteViews(context.packageName, R.layout.notification_fluid_cloud_schedule_expanded).apply {
                    setTextViewText(R.id.fluid_schedule_expanded_header, "日程提醒")
                    setTextViewText(R.id.fluid_schedule_expanded_title, title)
                    setTextViewText(R.id.fluid_schedule_expanded_desc, message)
                    setOnClickPendingIntent(R.id.fluid_schedule_btn_focus, focusPendingIntent)
                    setOnClickPendingIntent(R.id.fluid_schedule_btn_view, pendingIntent)

                    if (remMillis > 0) {
                        setChronometer(
                            R.id.fluid_schedule_expanded_chronometer,
                            SystemClock.elapsedRealtime() + remMillis,
                            "%s",
                            true
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            setChronometerCountDown(R.id.fluid_schedule_expanded_chronometer, true)
                        }
                    } else {
                        setChronometer(R.id.fluid_schedule_expanded_chronometer, SystemClock.elapsedRealtime(), "进行中", false)
                    }
                }

                builder.setCustomContentView(schedCapsule)
                    .setCustomBigContentView(schedExpanded)
                    .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            }
        }

        val notification = builder.build()
        FluidCloudHelper.finalizeNotification(notification, isLiveUpdateMode)
        manager.notify(notificationId, notification)

        // 释放临时唤醒锁
        try { wakeLock?.release() } catch (_: Exception) {}
    }

    companion object {
        const val ACTION_COMPLETE_REMINDER = "com.renly.rmf.ACTION_COMPLETE_REMINDER"
        const val ACTION_SNOOZE_REMINDER = "com.renly.rmf.ACTION_SNOOZE_REMINDER"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_ID = "extra_id"
        const val EXTRA_IS_COURSE = "extra_is_course"
        const val EXTRA_IS_DIRECT_REMINDER = "extra_is_direct_reminder"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_COURSE_LOCATION = "extra_course_location"
        const val EXTRA_COURSE_TEACHER = "extra_course_teacher"
        const val EXTRA_COURSE_START_MILLIS = "extra_course_start_millis"
        const val EXTRA_COURSE_DURATION_MINUTES = "extra_course_duration_minutes"
        const val EXTRA_REMINDER_TYPE = "extra_reminder_type"
    }
}
