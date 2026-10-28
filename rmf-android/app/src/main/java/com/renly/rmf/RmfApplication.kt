package com.renly.rmf

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.renly.rmf.data.local.RmfDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RmfApplication : Application() {

    val database: RmfDatabase
        get() = RmfDatabase.getInstance(this)

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                android.util.Log.e("RMF_CRASH", "Uncaught exception on thread: ${thread.name}", throwable)
                val crashFile = java.io.File(filesDir, "latest_crash.log")
                val sw = java.io.StringWriter()
                val pw = java.io.PrintWriter(sw)
                throwable.printStackTrace(pw)
                crashFile.writeText("Time: ${java.time.LocalDateTime.now()}\nThread: ${thread.name}\n\n$sw")
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
        createNotificationChannels()

        // 异步后台执行安全快照、系统级看门狗注册与全量闹钟重新核验排期，确保冷启动不阻塞且提醒永不失效
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                com.renly.rmf.domain.service.LosslessUpgradeManager.performAutoUpgradeSafetyBackup(this@RmfApplication)
                database.openHelper.writableDatabase

                // 确保彻底注销旧版后台看门狗任务，杜绝无意义周期性后台自启唤醒
                try {
                    val jobScheduler = getSystemService(android.content.Context.JOB_SCHEDULER_SERVICE) as? android.app.job.JobScheduler
                    jobScheduler?.cancel(9182)
                } catch (_: Exception) {}

                // 轻量核验并注册待触发事件与近 3 天日程闹钟 (纯按需注册至系统硬件 RTC，完成后即刻休眠，0 后台驻留)
                com.renly.rmf.domain.service.ScheduleReminderManager.rescheduleAll(this@RmfApplication)
            } catch (_: Exception) {}
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            // 用户切到后台或系统低内存时收缩 SQLite 内存并截断 WAL，大幅缩减后台常驻占用
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = database.openHelper.writableDatabase
                    db.query("PRAGMA wal_checkpoint(TRUNCATE);").close()
                    db.query("PRAGMA shrink_memory;").close()

                    // 自动清理超过 60 天的陈旧已删除事项与打断记录，杜绝数据库无节制膨胀
                    val cutoffStr = java.time.LocalDateTime.now().minusDays(60).format(java.time.format.DateTimeFormatter.ISO_DATE_TIME)
                    db.execSQL("DELETE FROM interruptions WHERE timestamp < '$cutoffStr';")
                    db.execSQL("DELETE FROM schedules WHERE is_deleted = 1 AND updated_at < '$cutoffStr';")
                } catch (_: Exception) {}

                // 清理临时快照与垃圾碎片
                try {
                    cacheDir.listFiles()?.forEach { file ->
                        if (file.isFile && (file.name.endsWith(".tmp") || file.name.startsWith("rmf_cloud_backup") || file.name.endsWith(".db-shm") || file.name.endsWith(".db-wal"))) {
                            file.delete()
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = database.openHelper.writableDatabase
                db.query("PRAGMA wal_checkpoint(TRUNCATE);").close()
                db.query("PRAGMA shrink_memory;").close()
            } catch (_: Exception) {}
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()
            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val channel = NotificationChannel(
                CHANNEL_SCHEDULE_REMINDER,
                "日程与课程提醒 (强提醒)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "日程时间块与上课到期前高优先级强提醒、震动声音与悬浮横幅"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 350, 200, 350)
                setSound(defaultSoundUri, audioAttributes)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                enableLights(true)
            }

            val fluidChannel = NotificationChannel(
                CHANNEL_FLUID_CLOUD,
                "胶囊通知实时提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "状态栏胶囊通知、锁屏实时卡片与专注倒计时"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                setSound(defaultSoundUri, audioAttributes)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                enableLights(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowBubbles(true)
                }
            }

            val soundChannel = NotificationChannel(
                CHANNEL_REMINDER_SOUND_ONLY,
                "日程提醒 (仅闹钟铃声)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "日程时间块到期时仅播放闹钟响铃，不触发震动"
                enableVibration(false)
                vibrationPattern = longArrayOf(0)
                setSound(defaultSoundUri, audioAttributes)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                enableLights(true)
            }

            val vibrateChannel = NotificationChannel(
                CHANNEL_REMINDER_VIBRATE_ONLY,
                "日程提醒 (仅震动)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "日程时间块到期时仅触发震动，静音不响铃"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 350, 200, 350)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                enableLights(true)
            }

            val silentChannel = NotificationChannel(
                CHANNEL_REMINDER_SILENT,
                "日程提醒 (无提醒/静音)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "静音显示通知胶囊或横幅，无闹钟响铃无震动"
                enableVibration(false)
                vibrationPattern = longArrayOf(0)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }

            val quickActionChannel = NotificationChannel(
                CHANNEL_QUICK_ACTION,
                "作息快捷打卡胶囊",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "通知栏常驻快捷作息打卡：醒了、午休、睡了一键标记"
                enableVibration(false)
                vibrationPattern = longArrayOf(0)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }

            val alarmSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val alarmAudioAttrs = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val alarmChannel = NotificationChannel(
                CHANNEL_ALARM_CLOCK,
                "日程闹钟 (系统强提醒)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "日程时间块到期强提醒闹钟，持续响铃与全屏唤醒"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 1000)
                setSound(alarmSoundUri, alarmAudioAttrs)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                enableLights(true)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            manager.createNotificationChannel(fluidChannel)
            manager.createNotificationChannel(soundChannel)
            manager.createNotificationChannel(vibrateChannel)
            manager.createNotificationChannel(silentChannel)
            manager.createNotificationChannel(quickActionChannel)
            manager.createNotificationChannel(alarmChannel)
        }
    }

    companion object {
        const val CHANNEL_SCHEDULE_REMINDER = "rmf_schedule_reminder_channel"
        const val CHANNEL_FLUID_CLOUD = "rmf_fluid_cloud_channel"
        const val CHANNEL_REMINDER_SOUND_ONLY = "rmf_reminder_sound_channel"
        const val CHANNEL_REMINDER_VIBRATE_ONLY = "rmf_reminder_vibrate_channel"
        const val CHANNEL_REMINDER_SILENT = "rmf_reminder_silent_channel"
        const val CHANNEL_QUICK_ACTION = "rmf_quick_action_channel"
        const val CHANNEL_ALARM_CLOCK = "rmf_alarm_clock_channel"
    }
}
