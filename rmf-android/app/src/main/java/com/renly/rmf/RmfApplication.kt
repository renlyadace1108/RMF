package com.renly.rmf

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.renly.rmf.data.local.RmfDatabase
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
        // 异步后台执行安全快照与数据库预热，确保不阻塞主线程冷启动
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                com.renly.rmf.domain.service.LosslessUpgradeManager.performAutoUpgradeSafetyBackup(this@RmfApplication)
                database.openHelper.writableDatabase
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

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            manager.createNotificationChannel(fluidChannel)
            manager.createNotificationChannel(soundChannel)
            manager.createNotificationChannel(vibrateChannel)
            manager.createNotificationChannel(silentChannel)
        }
    }

    companion object {
        const val CHANNEL_SCHEDULE_REMINDER = "rmf_schedule_reminder_channel"
        const val CHANNEL_FLUID_CLOUD = "rmf_fluid_cloud_channel"
        const val CHANNEL_REMINDER_SOUND_ONLY = "rmf_reminder_sound_channel"
        const val CHANNEL_REMINDER_VIBRATE_ONLY = "rmf_reminder_vibrate_channel"
        const val CHANNEL_REMINDER_SILENT = "rmf_reminder_silent_channel"
    }
}
