package com.renly.rmf.domain.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.renly.rmf.RmfApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 开机自启、应用版本升级与系统时区校准单次事件广播：
 * 仅在系统物理重启（RTC 闹钟被硬件清空）或时区变化时被动触发一次闹钟排期，
 * 严禁监听亮屏解锁或电源接入等高频行为，实现真正的 0 常驻、0 闲置耗电。
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
            Intent.ACTION_TIMEZONE_CHANGED
        )

        if (action in validActions) {
            val app = context.applicationContext as? RmfApplication ?: return
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ScheduleReminderManager.rescheduleAll(context)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
