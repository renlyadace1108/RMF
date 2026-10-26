package com.renly.rmf.domain.service

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.renly.rmf.MainActivity
import com.renly.rmf.R
import com.renly.rmf.RmfApplication
import com.renly.rmf.data.local.entity.ReminderEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ScheduleReminderManager {

    /**
     * 普通通用日程提醒 (精准闹钟对齐，豁免 Doze 深度休眠)
     */
    fun scheduleReminder(
        context: Context,
        scheduleId: String,
        title: String,
        startTimeStr: String,
        advanceMinutes: Int = 10,
        reminderType: ReminderType? = null
    ) {
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

            armExactAlarm(alarmManager, epochMillis, pendingIntent)
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

            armExactAlarm(alarmManager, epochMillis, pendingIntent)
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
     * 单点自定义事件提醒 (支持重复规则自动推进、逾期防漏兜底与无损持久化)
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

            // 若触发时间已过 (可能因关机或深度冻结漏发)
            if (triggerEpoch <= System.currentTimeMillis()) {
                // 1. 若具有周期性重复规则，自动计算下一个周期并排期
                if (!reminder.repeatRule.equals("NONE", ignoreCase = true)) {
                    val nextIso = ReminderRepeatHelper.computeNextOccurrence(reminder.remindTime, reminder.repeatRule)
                    if (nextIso != null) {
                        val app = context.applicationContext as? RmfApplication
                        if (app != null) {
                            CoroutineScope(Dispatchers.IO).launch {
                                val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                app.database.reminderDao().advanceRepeatCycle(reminder.id, nextIso, 0, nowStr)
                                val updated = reminder.copy(remindTime = nextIso, isCompleted = 0, updatedAt = nowStr)
                                scheduleDirectReminder(context, updated)
                            }
                        }
                    }
                }
                // 2. 仅对过去 24 小时内的事件触发补漏提醒，避免久远历史事项无意义骚扰
                if (remindLdt.isAfter(LocalDateTime.now().minusHours(24))) {
                    triggerOverdueReminderNotification(context, reminder)
                }
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

            armExactAlarm(alarmManager, triggerEpoch, pendingIntent)
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
     * 强力注册高优先级精准闹钟 (三级容灾梯队：setAlarmClock -> setExactAndAllowWhileIdle -> setExact)
     */
    private fun armExactAlarm(alarmManager: AlarmManager, triggerEpoch: Long, pendingIntent: PendingIntent) {
        try {
            // 原生 AlarmClock 对齐唤醒，ColorOS 及主流系统零功耗豁免休眠机制
            val clockInfo = AlarmManager.AlarmClockInfo(triggerEpoch, pendingIntent)
            alarmManager.setAlarmClock(clockInfo, pendingIntent)
        } catch (e: Exception) {
            try {
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
            } catch (_: Exception) {
                // 最终备用非精准唤醒 (防极端 ROM 崩溃)
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerEpoch, pendingIntent)
            }
        }
    }

    /**
     * 逾期补漏通知触发器：当关机、深睡或强杀后重新上线，立即呈现漏掉的提醒事项
     */
    fun triggerOverdueReminderNotification(context: Context, reminder: ReminderEntity) {
        try {
            val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notifId = reminder.id.hashCode() + 5000

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notifId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val completeIntent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                action = ScheduleAlarmReceiver.ACTION_COMPLETE_REMINDER
                putExtra(ScheduleAlarmReceiver.EXTRA_REMINDER_ID, reminder.id)
                putExtra(ScheduleAlarmReceiver.EXTRA_ID, notifId)
            }
            val completePendingIntent = PendingIntent.getBroadcast(
                context,
                notifId + 31,
                completeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val snoozeIntent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                action = ScheduleAlarmReceiver.ACTION_SNOOZE_REMINDER
                putExtra(ScheduleAlarmReceiver.EXTRA_REMINDER_ID, reminder.id)
                putExtra(ScheduleAlarmReceiver.EXTRA_ID, notifId)
            }
            val snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                notifId + 32,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val timeLabel = reminder.remindTime.replace("T", " ")
            val bodyText = if (reminder.content.isNotBlank()) {
                "${reminder.content}\n(原定提醒时间: $timeLabel)"
            } else {
                "原定提醒时间: $timeLabel，请及时处理"
            }

            val defaultSound = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            val builder = NotificationCompat.Builder(context, RmfApplication.CHANNEL_SCHEDULE_REMINDER)
                .setSmallIcon(R.drawable.ic_logo)
                .setColor(0xFF00F0FF.toInt())
                .setContentTitle("⚠️ [待处理提醒] ${reminder.title}")
                .setContentText(bodyText)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSound(defaultSound)
                .setVibrate(longArrayOf(0, 300, 200, 300))
                .addAction(0, "✓ 标记完成", completePendingIntent)
                .addAction(0, "⏰ 稍后推迟 10分钟", snoozePendingIntent)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))

            notifManager.notify(notifId, builder.build())
        } catch (_: Exception) { }
    }

    /**
     * 全量重新核验并注册系统中所有待触发提醒、课程与日程 (用于开机、网络变更、同步还原与看门狗周期调度)
     */
    fun rescheduleAll(context: Context) {
        val app = context.applicationContext as? RmfApplication ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = app.database
                val now = LocalDateTime.now()

                // 1. 重新核验并注册自定义事件提醒
                val pendingReminders = db.reminderDao().getPendingRemindersList()
                for (reminder in pendingReminders) {
                    scheduleDirectReminder(context, reminder)
                }

                // 2. 轻量区间查询未来 3 天内日程 (仅查近期待办，杜绝全表扫描与内存峰值)
                val startStr = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                val endStr = now.plusDays(3).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                val upcomingSchedules = db.scheduleDao().getUpcomingSchedulesList(startStr, endStr)
                for (schedule in upcomingSchedules) {
                    try {
                        scheduleReminder(
                            context = context,
                            scheduleId = schedule.id,
                            title = schedule.title,
                            startTimeStr = schedule.startTime,
                            advanceMinutes = 10
                        )
                    } catch (_: Exception) {}
                }

                // 3. 仅核验未来 3 天课程提醒 (精简按需排期，节省内存与唤醒资源)
                CourseReminderScheduler.scheduleUpcomingCourseReminders(
                    context = context,
                    courseDao = db.courseDao(),
                    daysAhead = 3
                )
            } catch (_: Exception) { }
        }
    }

    // =========================================================================
    // ================== 系统权限、保活诊断与 OEM 自启动导航 ===================
    // =========================================================================

    /**
     * 检查是否具备 Android 12+ 精准闹钟权限
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * 检查是否已列入电池白名单 (忽略电池优化，防止深度休眠杀停闹钟)
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    /**
     * 引导用户前往系统授权精准闹钟
     */
    fun requestExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                openAppDetailsSettings(context)
            }
        }
    }

    /**
     * 申请电池优化白名单 (豁免厂商系统后台杀停机制)
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                } catch (_: Exception) {
                    openAppDetailsSettings(context)
                }
            }
        }
    }

    /**
     * 一键穿透打开各大国产厂商 OEM 系统自启动与后台保护设置页 (小米/OPPO/Vivo/华为/荣耀等)
     */
    fun openAutostartSettings(context: Context) {
        val oemIntents = listOf(
            // 小米 / Redmi / HyperOS / MIUI
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")),
            // OPPO / 一加 / Realme / ColorOS
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.oplus.battery", "com.oplus.battery.PowerControlActivity")),
            // vivo / iQOO / OriginOS / FuntouchOS
            Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
            Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            // 华为 / 荣耀 / HarmonyOS / EMUI
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.bootstart.BootStartActivity")),
            // 三星 OneUI
            Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")),
            // 魅族 Flyme
            Intent().setComponent(ComponentName("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"))
        )

        for (intent in oemIntents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }

        // 默认备选：打开应用专属系统设置详情页
        openAppDetailsSettings(context)
    }

    fun openAppDetailsSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
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

    fun startFluidCloudLiveSession(context: Context, title: String = "胶囊通知实时测试", seconds: Int = 25 * 60) {
        FocusLiveService.start(context, title, seconds)
    }

    fun stopFluidCloudLiveSession(context: Context) {
        FocusLiveService.stop(context)
    }
}
