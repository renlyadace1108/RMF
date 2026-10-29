package com.renly.rmf.domain.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.renly.rmf.MainActivity
import com.renly.rmf.R

/**
 * 🛡️ RMF 后台高保活常驻守护服务 (Resident Background Protection Service)
 *
 * 彻底解决手机与平板端切入后台后被系统无情杀掉的问题：
 * 1. 启动轻量级低优先级前台服务 (Foreground Service)，向系统声明最高驻留优先级。
 * 2. 状态栏仅挂载静默极简守护通知，无任何提示音或振动打扰。
 * 3. 支持一键引导加入系统电池优化白名单 (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)。
 * 4. 配合 START_STICKY，即便极端低内存下被操作系统短暂回收，也会在内存恢复后第一时间自动复活。
 */
class RmfResidentService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startResidentForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startResidentForeground()
        return START_STICKY
    }

    private fun startResidentForeground() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "后台常驻守护",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "确保全天候日程闹钟、课程提醒与节律监控在后台稳定运行，防止系统误杀"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle("RMF 节律常驻守护中")
            .setContentText("全天候日程闹钟、课程提醒与后台同步稳定运行中 · 轻触返回")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            android.util.Log.w("RmfResidentService", "startForeground failed: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 若在启用状态下被销毁，自动尝试重启保活
        if (isResidentProtectionEnabled(this)) {
            try {
                val restartIntent = Intent(applicationContext, RmfResidentService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    applicationContext.startForegroundService(restartIntent)
                } else {
                    applicationContext.startService(restartIntent)
                }
            } catch (_: Exception) {}
        }
    }

    companion object {
        const val CHANNEL_ID = "rmf_resident_protection_channel"
        const val NOTIFICATION_ID = 9188
        private const val PREFS_NAME = "rmf_resident_protection_prefs"
        private const val KEY_ENABLED = "resident_protection_enabled"

        private fun getPrefs(context: Context): SharedPreferences {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        /**
         * 是否启用了后台常驻保活（默认开启）
         */
        fun isResidentProtectionEnabled(context: Context): Boolean {
            return getPrefs(context).getBoolean(KEY_ENABLED, true)
        }

        fun setResidentProtectionEnabled(context: Context, enabled: Boolean) {
            getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
            if (enabled) {
                start(context)
            } else {
                stop(context)
            }
        }

        /**
         * 启动后台常驻守护服务
         */
        fun start(context: Context) {
            if (!isResidentProtectionEnabled(context)) return
            try {
                val intent = Intent(context, RmfResidentService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.w("RmfResidentService", "Failed to start resident service: ${e.message}")
            }
        }

        /**
         * 停止后台常驻守护服务
         */
        fun stop(context: Context) {
            try {
                val intent = Intent(context, RmfResidentService::class.java)
                context.stopService(intent)
            } catch (_: Exception) {}
        }

        /**
         * 检测当前是否已加入系统电池优化白名单 (忽略电池优化)
         */
        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
            } else {
                true
            }
        }

        /**
         * 请求加入系统电池优化白名单（直接唤起系统白名单权限弹窗）
         */
        fun requestIgnoreBatteryOptimizations(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    try {
                        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(fallback)
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
