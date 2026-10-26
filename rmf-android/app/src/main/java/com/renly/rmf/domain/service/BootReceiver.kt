package com.renly.rmf.domain.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.renly.rmf.RmfApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 手机开机自启、系统唤醒、时区变化或解锁屏广播：
 * 重新加载待办事件、日程与课程，自动注册系统级精准闹钟，并启动看门狗服务，
 * 确保即使历经深睡、强杀、时间校准或冷重启，提醒永不失效。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_POWER_CONNECTED
        )

        if (action in validActions) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wakeLock = try {
                powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "rmf:boot_receiver")?.apply {
                    acquire(15000L)
                }
            } catch (_: Exception) {
                null
            }

            // 1. 确保系统后台看门狗周期调度处于就绪状态
            ReminderWatchdogJobService.schedule(context)

            // 2. 全量核验所有待触发提醒、日程与课程
            val app = context.applicationContext as? RmfApplication
            if (app != null) {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        ScheduleReminderManager.rescheduleAll(context)
                        // 重新刷新桌面全部小组件
                        com.renly.rmf.ui.widget.RmfWidgetManager.refreshAllWidgets(context)
                    } finally {
                        try { wakeLock?.release() } catch (_: Exception) {}
                        pendingResult.finish()
                    }
                }
            } else {
                try { wakeLock?.release() } catch (_: Exception) {}
            }
        }
    }
}
