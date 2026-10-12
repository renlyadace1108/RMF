package com.renly.rmf.domain.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.renly.rmf.MainActivity
import com.renly.rmf.R
import com.renly.rmf.RmfApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FocusLiveService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "深度心流"
                val seconds = intent.getIntExtra(EXTRA_SECONDS, 25 * 60)
                startFocusSession(title, seconds)
            }
            ACTION_TOGGLE -> {
                if (_isRunning.value) {
                    pauseFocusSession()
                } else {
                    resumeFocusSession()
                }
            }
            ACTION_PAUSE -> pauseFocusSession()
            ACTION_RESUME -> resumeFocusSession()
            ACTION_STOP -> stopFocusSession()
            ACTION_FINISH -> onFocusCompleted()
        }
        return START_NOT_STICKY
    }

    private var targetEndTimeRealtime: Long = 0L

    private fun startFocusSession(title: String, seconds: Int) {
        _currentTaskTitle.value = title
        _initialTotalSeconds.value = seconds
        _remainingSeconds.value = seconds
        _isRunning.value = true
        targetEndTimeRealtime = SystemClock.elapsedRealtime() + (seconds * 1000L)

        scheduleFinishAlarm(seconds)

        val notification = buildFluidCloudNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        launchTimerSyncLoop()
    }

    private fun pauseFocusSession() {
        cancelFinishAlarm()
        val remaining = maxOf(0, ((targetEndTimeRealtime - SystemClock.elapsedRealtime()) / 1000).toInt())
        _remainingSeconds.value = remaining
        _isRunning.value = false
        timerJob?.cancel()

        val notification = buildFluidCloudNotification()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun resumeFocusSession() {
        if (_remainingSeconds.value > 0) {
            _isRunning.value = true
            targetEndTimeRealtime = SystemClock.elapsedRealtime() + (_remainingSeconds.value * 1000L)
            scheduleFinishAlarm(_remainingSeconds.value)

            val notification = buildFluidCloudNotification()
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
            launchTimerSyncLoop()
        }
    }

    private fun stopFocusSession() {
        cancelFinishAlarm()
        _isRunning.value = false
        _remainingSeconds.value = _initialTotalSeconds.value
        timerJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun onFocusCompleted() {
        cancelFinishAlarm()
        _isRunning.value = false
        _remainingSeconds.value = 0
        timerJob?.cancel()
        showCompletedNotification()
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun scheduleFinishAlarm(seconds: Int) {
        try {
            val alarmManager = getSystemService(ALARM_SERVICE) as? android.app.AlarmManager ?: return
            val finishIntent = Intent(this, FocusLiveService::class.java).apply {
                action = ACTION_FINISH
            }
            val pendingIntent = PendingIntent.getService(
                this,
                999,
                finishIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAtMillis = System.currentTimeMillis() + (seconds * 1000L)
            val clockInfo = android.app.AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntent)
            alarmManager.setAlarmClock(clockInfo, pendingIntent)
        } catch (_: Exception) {}
    }

    private fun cancelFinishAlarm() {
        try {
            val alarmManager = getSystemService(ALARM_SERVICE) as? android.app.AlarmManager ?: return
            val finishIntent = Intent(this, FocusLiveService::class.java).apply {
                action = ACTION_FINISH
            }
            val pendingIntent = PendingIntent.getService(
                this,
                999,
                finishIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        } catch (_: Exception) {}
    }

    private fun launchTimerSyncLoop() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (_isRunning.value) {
                delay(1000)
                val remaining = maxOf(0, ((targetEndTimeRealtime - SystemClock.elapsedRealtime()) / 1000).toInt())
                _remainingSeconds.value = remaining
                if (remaining <= 0) {
                    onFocusCompleted()
                    break
                }
            }
        }
    }

    private fun buildFluidCloudNotification(): Notification {
        val title = _currentTaskTitle.value
        val isRunning = _isRunning.value
        val remSec = _remainingSeconds.value
        val min = remSec / 60
        val sec = remSec % 60
        val timeStr = "%02d:%02d".format(min, sec)

        // 1. 点击跳转回应用专注页
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            100,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. 快捷暂停/继续
        val toggleIntent = Intent(this, FocusLiveService::class.java).apply {
            action = ACTION_TOGGLE
        }
        val togglePendingIntent = PendingIntent.getService(
            this,
            101,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. 快捷结束
        val stopIntent = Intent(this, FocusLiveService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            102,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isLiveUpdateMode = FluidCloudHelper.isPromotedLiveUpdateSupported(this)
        val brandLabel = FluidCloudHelper.getSystemBrandLabel()

        val builder = NotificationCompat.Builder(this, RmfApplication.CHANNEL_FLUID_CLOUD)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle("🎯 深度专注中: $title")
            .setContentText(if (isRunning) "剩余: $timeStr" else "已暂停 ($timeStr)")
            .setSubText("$brandLabel · 实时心流")
            .setColor(0xFF00F0FF.toInt())
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openPendingIntent)

        // 添加标准交互 Action 按钮（ColorOS 17 / HyperOS 展开卡片与小窗交互）
        builder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_logo,
                if (isRunning) "⏸️ 暂停" else "▶️ 继续",
                togglePendingIntent
            ).build()
        )
        builder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_logo,
                "⏹️ 结束",
                stopPendingIntent
            ).build()
        )

        // 原生倒计时与 Chronometer 支持
        if (isRunning) {
            builder.setUsesChronometer(true)
            builder.setWhen(System.currentTimeMillis() + (remSec * 1000L))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setChronometerCountDown(true)
            }
        } else {
            builder.setUsesChronometer(false)
            builder.setWhen(System.currentTimeMillis())
        }

        // 注入 Android 16 (ColorOS 17) Promoted Ongoing 标记
        FluidCloudHelper.applyPromotedOngoing(builder)

        // 注入小米 HyperOS 超级岛 (Super Island) / 焦点通知 (Focus Notification) 原生协议
        FluidCloudHelper.applyXiaomiSuperIslandParams(builder, title, remSec, isRunning)

        if (isLiveUpdateMode) {
            // Android 16 / ColorOS 17 官方 Live Updates 规范:
            // 严禁绑定自定义 RemoteViews，必须使用 BigTextStyle 等原生合规样式，
            // 才能通过 Notification.hasPromotableCharacteristics() 校验并成功在 OPPO Find X9 等设备上触发状态栏胶囊与锁屏实时活动
            val bigTextStyle = NotificationCompat.BigTextStyle()
                .setBigContentTitle("🎯 $title")
                .setSummaryText("$brandLabel · 实时活动")
                .bigText(if (isRunning) "深度专注计时进行中 · 剩余 $timeStr\n$brandLabel 实时活动接入已生效" else "专注已暂停 ($timeStr)，点击继续恢复")
            builder.setStyle(bigTextStyle)
        } else {
            // 小米 HyperOS 及其他系统：展示深度美化的双态自定义卡片（带有磨砂药丸仪表盘与圆角交互按键）
            val capsuleView = RemoteViews(packageName, R.layout.notification_fluid_cloud_capsule).apply {
                setTextViewText(R.id.fluid_capsule_title, "🎯 $title")
                setTextViewText(R.id.fluid_capsule_sub, if (isRunning) "$brandLabel 专注中" else "已暂停")
                setTextViewText(R.id.fluid_capsule_action_btn, if (isRunning) "暂停" else "继续")
                setOnClickPendingIntent(R.id.fluid_capsule_action_btn, togglePendingIntent)

                if (isRunning) {
                    setChronometer(
                        R.id.fluid_capsule_chronometer,
                        SystemClock.elapsedRealtime() + (remSec * 1000L),
                        "%s",
                        true
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        setChronometerCountDown(R.id.fluid_capsule_chronometer, true)
                    }
                } else {
                    setChronometer(
                        R.id.fluid_capsule_chronometer,
                        SystemClock.elapsedRealtime(),
                        timeStr,
                        false
                    )
                }
            }

            val expandedView = RemoteViews(packageName, R.layout.notification_fluid_cloud_expanded).apply {
                setTextViewText(R.id.fluid_expanded_header, "🎯 $brandLabel · 深度专注中")
                setTextViewText(R.id.fluid_expanded_state_badge, if (isRunning) "⚡ 心流进行中" else "⏸️ 已暂停")
                setTextViewText(R.id.fluid_expanded_task_title, title)
                setTextViewText(R.id.fluid_btn_pause, if (isRunning) "⏸️ 暂停专注" else "▶️ 继续专注")
                setOnClickPendingIntent(R.id.fluid_btn_pause, togglePendingIntent)
                setOnClickPendingIntent(R.id.fluid_btn_stop, stopPendingIntent)

                if (isRunning) {
                    setChronometer(
                        R.id.fluid_expanded_chronometer,
                        SystemClock.elapsedRealtime() + (remSec * 1000L),
                        "%s",
                        true
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        setChronometerCountDown(R.id.fluid_expanded_chronometer, true)
                    }
                } else {
                    setChronometer(
                        R.id.fluid_expanded_chronometer,
                        SystemClock.elapsedRealtime(),
                        timeStr,
                        false
                    )
                }
            }

            builder.setCustomContentView(capsuleView)
            builder.setCustomBigContentView(expandedView)
            builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
        }

        val notification = builder.build()
        FluidCloudHelper.finalizeNotification(notification, isLiveUpdateMode)
        return notification
    }

    private fun showCompletedNotification() {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            103,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val brandLabel = FluidCloudHelper.getSystemBrandLabel()
        val completedNotification = NotificationCompat.Builder(this, RmfApplication.CHANNEL_FLUID_CLOUD)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle("🎉 专注已达成！")
            .setContentText("本次专注任务已顺利完成，请适当休息眼睛")
            .setSubText("$brandLabel · 专注达成")
            .setColor(0xFF10B981.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, completedNotification)
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelFinishAlarm()
        serviceScope.cancel()
    }

    companion object {
        const val NOTIFICATION_ID = 2001
        const val ACTION_START = "com.renly.rmf.action.START_FOCUS"
        const val ACTION_TOGGLE = "com.renly.rmf.action.TOGGLE_FOCUS"
        const val ACTION_PAUSE = "com.renly.rmf.action.PAUSE_FOCUS"
        const val ACTION_RESUME = "com.renly.rmf.action.RESUME_FOCUS"
        const val ACTION_STOP = "com.renly.rmf.action.STOP_FOCUS"
        const val ACTION_FINISH = "com.renly.rmf.action.FINISH_FOCUS"

        const val EXTRA_TITLE = "extra_focus_title"
        const val EXTRA_SECONDS = "extra_focus_seconds"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _initialTotalSeconds = MutableStateFlow(25 * 60)
        val initialTotalSeconds: StateFlow<Int> = _initialTotalSeconds.asStateFlow()

        private val _remainingSeconds = MutableStateFlow(25 * 60)
        val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

        private val _currentTaskTitle = MutableStateFlow("深度心流")
        val currentTaskTitle: StateFlow<String> = _currentTaskTitle.asStateFlow()

        fun start(context: Context, title: String = "深度心流", seconds: Int = 25 * 60) {
            val intent = Intent(context, FocusLiveService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SECONDS, seconds)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun toggle(context: Context) {
            val intent = Intent(context, FocusLiveService::class.java).apply {
                action = ACTION_TOGGLE
            }
            context.startService(intent)
        }

        fun pause(context: Context) {
            val intent = Intent(context, FocusLiveService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, FocusLiveService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FocusLiveService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
