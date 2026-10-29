package com.renly.rmf.domain.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.CalendarContract
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.renly.rmf.MainActivity
import com.renly.rmf.R
import com.renly.rmf.RmfApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val intentAction = intent.action
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 处理系统日历提醒事件广播兜底 (android.intent.action.EVENT_REMINDER)
        if (intentAction == CalendarContract.ACTION_EVENT_REMINDER || intentAction == "android.intent.action.EVENT_REMINDER") {
            handleSystemCalendarReminderFallback(context, intent)
            return
        }

        // 处理日程闹铃交互动作 (关闭 / 稍后推迟)
        if (intentAction == ACTION_DISMISS_SCHEDULE_ALARM) {
            val notifId = intent.getIntExtra(EXTRA_ID, 1001)
            manager.cancel(notifId)
            return
        }

        if (intentAction == ACTION_SNOOZE_SCHEDULE_ALARM) {
            val scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID) ?: return
            val notifId = intent.getIntExtra(EXTRA_ID, 1001)
            manager.cancel(notifId)

            val app = context.applicationContext as? RmfApplication
            if (app != null) {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val schedule = app.database.scheduleDao().getScheduleById(scheduleId)
                        if (schedule != null) {
                            val snoozedTime = LocalDateTime.now().plusMinutes(10)
                            val snoozedIso = snoozedTime.format(DateTimeFormatter.ISO_DATE_TIME)
                            val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                            val updated = schedule.copy(startTime = snoozedIso, reminderMinutes = 0, updatedAt = nowStr)
                            app.database.scheduleDao().insertOrUpdate(updated)
                            ScheduleReminderManager.scheduleReminder(
                                context = context,
                                scheduleId = updated.id,
                                title = updated.title,
                                startTimeStr = updated.startTime,
                                advanceMinutes = 0,
                                reminderType = ReminderType.fromCode(updated.reminderType)
                            )
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            return
        }

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
                        pendingResult.finish()
                    }
                }
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
                        pendingResult.finish()
                    }
                }
            }
            return
        }

        // 常规提醒分发 (课程 / 日程 / 单点事件提醒)
        dispatchNotification(context, intent)
    }

    private fun dispatchNotification(context: Context, intent: Intent) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "日程时间块"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "即将开始，请准备进入专注状态"
        val notificationId = intent.getIntExtra(EXTRA_ID, 1001)
        val isCourse = intent.getBooleanExtra(EXTRA_IS_COURSE, false)
        val isDirectReminder = intent.getBooleanExtra(EXTRA_IS_DIRECT_REMINDER, false)
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: ""
        val scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID) ?: ""
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
                ReminderType.ALARM -> RmfApplication.CHANNEL_ALARM_CLOCK
                ReminderType.CAPSULE -> RmfApplication.CHANNEL_FLUID_CLOUD
                ReminderType.VIBRATE -> RmfApplication.CHANNEL_REMINDER_VIBRATE_ONLY
                ReminderType.NONE -> RmfApplication.CHANNEL_REMINDER_SILENT
            }
        } else {
            when (reminderType) {
                ReminderType.ALL -> RmfApplication.CHANNEL_SCHEDULE_REMINDER
                ReminderType.ALARM -> RmfApplication.CHANNEL_ALARM_CLOCK
                ReminderType.CAPSULE -> RmfApplication.CHANNEL_FLUID_CLOUD
                ReminderType.VIBRATE -> RmfApplication.CHANNEL_REMINDER_VIBRATE_ONLY
                ReminderType.NONE -> RmfApplication.CHANNEL_REMINDER_SILENT
            }
        }

        val defaultSound = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
        val alarmSound = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_logo)
            .setColor(0xFF00F0FF.toInt())
            .setPriority(if (reminderType == ReminderType.NONE) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MAX)
            .setCategory(
                when {
                    reminderType == ReminderType.ALARM -> NotificationCompat.CATEGORY_ALARM
                    isCourse -> NotificationCompat.CATEGORY_EVENT
                    else -> NotificationCompat.CATEGORY_REMINDER
                }
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOngoing(reminderType == ReminderType.ALARM || isCourse)
            .setContentIntent(pendingIntent)

        val brandLabel = FluidCloudHelper.getSystemBrandLabel()
        builder.setFullScreenIntent(pendingIntent, reminderType == ReminderType.ALARM || reminderType != ReminderType.NONE)
        builder.setSubText(
            when {
                reminderType == ReminderType.ALARM -> "$brandLabel · 强提醒闹钟"
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
                builder.setSound(alarmSound)
                builder.setVibrate(longArrayOf(0, 500, 250, 500, 250, 1000))
                builder.setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
            }
            ReminderType.CAPSULE -> {
                builder.setSound(defaultSound)
                builder.setVibrate(longArrayOf(0, 250, 150, 250))
                builder.setDefaults(NotificationCompat.DEFAULT_ALL)
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

            // 若此事件有周期性重复规则，在触发弹窗的同时异步自动排期下一周期闹钟，确保持续生效
            if (reminderId.isNotBlank()) {
                val app = context.applicationContext as? RmfApplication
                if (app != null) {
                    val pendingResult = try { goAsync() } catch (_: Exception) { null }
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
                        } finally {
                            pendingResult?.finish()
                        }
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

            if (reminderType == ReminderType.ALARM) {
                val dismissIntent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                    action = ACTION_DISMISS_SCHEDULE_ALARM
                    putExtra(EXTRA_ID, notificationId)
                }
                val dismissPi = PendingIntent.getBroadcast(
                    context,
                    notificationId + 80,
                    dismissIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val snoozeIntent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                    action = ACTION_SNOOZE_SCHEDULE_ALARM
                    putExtra(EXTRA_SCHEDULE_ID, scheduleId)
                    putExtra(EXTRA_ID, notificationId)
                }
                val snoozePi = PendingIntent.getBroadcast(
                    context,
                    notificationId + 81,
                    snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                builder.addAction(NotificationCompat.Action.Builder(0, "✓ 关闭闹铃", dismissPi).build())
                builder.addAction(NotificationCompat.Action.Builder(0, "⏰ 稍后推迟 (+10分)", snoozePi).build())
            } else {
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
            }

            builder.setContentTitle(if (reminderType == ReminderType.ALARM) "⏰ 闹钟响铃: $title" else title)
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
    }

    /**
     * 系统日历组件通知广播兜底处理器：
     * 当 OEM 厂商电池管理策略杀后台或推迟三方 AlarmManager 闹钟时，
     * 手机系统级日历提供者 (com.android.providers.calendar) 触发的系统日历广播
     * 将唤醒本接收器，比对属于 RMF 的日程或待办事项，执行 100% 可靠的浮窗与实时活动通知兜底。
     */
    private fun handleSystemCalendarReminderFallback(context: Context, intent: Intent) {
        val app = context.applicationContext as? RmfApplication ?: return
        if (!SystemCalendarSyncService.hasCalendarPermissions(context)) return

        val pendingResult = try { goAsync() } catch (_: Exception) { null }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dataUri = intent.data
                var eventId: Long? = try {
                    dataUri?.lastPathSegment?.toLongOrNull()
                } catch (_: Exception) { null }

                if (eventId == null && dataUri != null) {
                    eventId = dataUri.getQueryParameter("id")?.toLongOrNull()
                        ?: dataUri.getQueryParameter("event_id")?.toLongOrNull()
                }
                if (eventId == null) {
                    val idExtra = intent.getLongExtra("event_id", -1L)
                    if (idExtra > 0) eventId = idExtra
                }
                if (eventId == null) {
                    val idExtra = intent.getLongExtra(CalendarContract.EXTRA_EVENT_ID, -1L)
                    if (idExtra > 0) eventId = idExtra
                }
                if (eventId == null) {
                    val idInt = intent.getIntExtra("id", -1)
                    if (idInt > 0) eventId = idInt.toLong()
                }

                if (eventId == null) return@launch

                var scheduleId: String? = null
                var reminderId: String? = null
                var eventTitle: String = ""
                var eventDesc: String = ""
                var dtStart: Long = 0L

                val projection = arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.SYNC_DATA1,
                    CalendarContract.Events.SYNC_DATA2,
                    CalendarContract.Events.DTSTART
                )

                context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    projection,
                    "${CalendarContract.Events._ID} = ?",
                    arrayOf(eventId.toString()),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        eventTitle = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)) ?: ""
                        eventDesc = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)) ?: ""
                        scheduleId = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.SYNC_DATA1))
                        reminderId = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.SYNC_DATA2))
                        dtStart = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART))
                    }
                }

                // 容错提取: 从描述中提取 RMF 标记
                if (scheduleId.isNullOrBlank() && eventDesc.contains("[RMF_ID:")) {
                    val regex = Regex("\\[RMF_ID:(.*?)\\]")
                    scheduleId = regex.find(eventDesc)?.groupValues?.getOrNull(1)
                }
                if (reminderId.isNullOrBlank() && eventDesc.contains("[RMF_REMINDER_ID:")) {
                    val regex = Regex("\\[RMF_REMINDER_ID:(.*?)\\]")
                    reminderId = regex.find(eventDesc)?.groupValues?.getOrNull(1)
                }

                val db = app.database

                // 1. 匹配到 RMF 日程时间块
                if (!scheduleId.isNullOrBlank()) {
                    val schedule = db.scheduleDao().getScheduleById(scheduleId!!)
                    if (schedule != null && schedule.isDeleted == 0 && schedule.status != "COMPLETED" && schedule.status != "ABANDONED") {
                        val rType = ReminderType.fromCode(schedule.reminderType)
                        val startMillis = try {
                            val ldt = try {
                                LocalDateTime.parse(schedule.startTime, DateTimeFormatter.ISO_DATE_TIME)
                            } catch (_: Exception) {
                                LocalDateTime.parse(schedule.startTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                            }
                            ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        } catch (_: Exception) {
                            if (dtStart > 0) dtStart else System.currentTimeMillis()
                        }

                        val reminderIntent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                            putExtra(EXTRA_TITLE, schedule.title)
                            putExtra(EXTRA_MESSAGE, schedule.description.ifBlank { "${schedule.title} 预定时间已到" })
                            putExtra(EXTRA_ID, schedule.id.hashCode())
                            putExtra(EXTRA_SCHEDULE_ID, schedule.id)
                            putExtra(EXTRA_IS_COURSE, false)
                            putExtra(EXTRA_IS_DIRECT_REMINDER, false)
                            putExtra(EXTRA_COURSE_START_MILLIS, startMillis)
                            putExtra(EXTRA_REMINDER_TYPE, rType.code)
                        }
                        dispatchNotification(context, reminderIntent)
                    }
                    return@launch
                }

                // 2. 匹配到 RMF 自定义事件待办提醒
                if (!reminderId.isNullOrBlank()) {
                    val reminder = db.reminderDao().getReminderById(reminderId!!)
                    if (reminder != null && reminder.isDeleted == 0 && reminder.isCompleted == 0) {
                        val targetEpoch = try {
                            val ldt = try {
                                LocalDateTime.parse(reminder.remindTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                            } catch (_: Exception) {
                                LocalDateTime.parse(reminder.remindTime, DateTimeFormatter.ISO_DATE_TIME)
                            }
                            ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        } catch (_: Exception) {
                            if (dtStart > 0) dtStart else System.currentTimeMillis()
                        }

                        val reminderIntent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                            putExtra(EXTRA_TITLE, reminder.title)
                            putExtra(EXTRA_MESSAGE, reminder.content.ifBlank { "${reminder.title} 时间已到" })
                            putExtra(EXTRA_ID, reminder.id.hashCode())
                            putExtra(EXTRA_IS_DIRECT_REMINDER, true)
                            putExtra(EXTRA_REMINDER_ID, reminder.id)
                            putExtra(EXTRA_COURSE_START_MILLIS, targetEpoch)
                            putExtra(EXTRA_REMINDER_TYPE, reminder.reminderType)
                        }
                        dispatchNotification(context, reminderIntent)
                    }
                    return@launch
                }
            } catch (e: Exception) {
                android.util.Log.w("ScheduleAlarmReceiver", "Fallback reminder error: ${e.message}")
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE_REMINDER = "com.renly.rmf.ACTION_COMPLETE_REMINDER"
        const val ACTION_SNOOZE_REMINDER = "com.renly.rmf.ACTION_SNOOZE_REMINDER"
        const val ACTION_DISMISS_SCHEDULE_ALARM = "com.renly.rmf.ACTION_DISMISS_SCHEDULE_ALARM"
        const val ACTION_SNOOZE_SCHEDULE_ALARM = "com.renly.rmf.ACTION_SNOOZE_SCHEDULE_ALARM"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_ID = "extra_id"
        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
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
