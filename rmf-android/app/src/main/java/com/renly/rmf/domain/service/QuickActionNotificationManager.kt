package com.renly.rmf.domain.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.renly.rmf.MainActivity
import com.renly.rmf.R
import com.renly.rmf.RmfApplication
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object QuickActionNotificationManager {

    const val NOTIFICATION_ID = 8888
    const val PREF_KEY_QUICK_ACTION_ENABLED = "quick_action_notification_enabled"

    fun updateQuickActionNotification(context: Context) {
        val sp = context.getSharedPreferences("rmf_quick_action_prefs", Context.MODE_PRIVATE)
        val isEnabled = sp.getBoolean(PREF_KEY_QUICK_ACTION_ENABLED, true)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!isEnabled) {
            notificationManager.cancel(NOTIFICATION_ID)
            return
        }

        val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日"))

        // 点击通知主体打开应用
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 快捷打卡动作 1: 醒了 / 起床 (POINT)
        val wakeIntent = Intent(context, QuickActionReceiver::class.java).apply {
            action = QuickActionReceiver.ACTION_MARK_WAKE_UP
        }
        val wakePendingIntent = PendingIntent.getBroadcast(
            context,
            101,
            wakeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 快捷打卡动作 2: 午休打卡 (RANGE)
        val napIntent = Intent(context, QuickActionReceiver::class.java).apply {
            action = QuickActionReceiver.ACTION_MARK_NAP
        }
        val napPendingIntent = PendingIntent.getBroadcast(
            context,
            102,
            napIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 快捷打卡动作 3: 睡了 / 就寝 (POINT)
        val sleepIntent = Intent(context, QuickActionReceiver::class.java).apply {
            action = QuickActionReceiver.ACTION_MARK_SLEEP
        }
        val sleepPendingIntent = PendingIntent.getBroadcast(
            context,
            103,
            sleepIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, RmfApplication.CHANNEL_QUICK_ACTION)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle("⏰ 作息快捷打卡胶囊 · $todayStr")
            .setContentText("免开 App，一键记录当前分钟级作息标记")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                NotificationCompat.Action.Builder(0, "🌅 醒了", wakePendingIntent).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(0, "🛌 午休", napPendingIntent).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(0, "💤 睡了", sleepPendingIntent).build()
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancelNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
