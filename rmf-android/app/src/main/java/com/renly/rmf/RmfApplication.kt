package com.renly.rmf

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
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
            val channel = NotificationChannel(
                CHANNEL_SCHEDULE_REMINDER,
                "日程到期提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "在日程或课程开始前 5/10/15 分钟触发高优先级强提醒"
                enableVibration(true)
            }

            val fluidChannel = NotificationChannel(
                CHANNEL_FLUID_CLOUD,
                "ColorOS 流体云实时服务",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "ColorOS 状态栏流体云胶囊、锁屏卡片与实时专注倒计时"
                enableVibration(false)
                setSound(null, null)
                setShowBadge(true)
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
