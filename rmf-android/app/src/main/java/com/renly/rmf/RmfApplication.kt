package com.renly.rmf

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.renly.rmf.data.local.RmfDatabase

class RmfApplication : Application() {

    val database: RmfDatabase by lazy {
        RmfDatabase.getInstance(this)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        // 执行无损升级自动安全快照备份（启动与版本迭代安全屏障）
        com.renly.rmf.domain.service.LosslessUpgradeManager.performAutoUpgradeSafetyBackup(this)
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
                "ColorOS 流体云实时胶囊与提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "ColorOS 状态栏流体云胶囊、锁屏实时卡片与专注倒计时"
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

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            manager.createNotificationChannel(fluidChannel)
        }
    }

    companion object {
        const val CHANNEL_SCHEDULE_REMINDER = "rmf_schedule_reminder_channel"
        const val CHANNEL_FLUID_CLOUD = "rmf_fluid_cloud_channel"
    }
}
