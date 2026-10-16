package com.renly.rmf.domain.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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

/**
 * FocusLiveService
 * 专注实时活动前台服务 (全局强制使用安卓原生实时活动 Android Live Updates 借道引擎)
 */
class FocusLiveService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "深度心流"
                val seconds = intent.getIntExtra(EXTRA_SECONDS, 25 * 60)
                val schedId = intent.getStringExtra(EXTRA_SCHEDULE_ID)
                val cat = intent.getStringExtra(EXTRA_CATEGORY) ?: "WORK"
                startFocusSession(title, seconds, schedId, cat)
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
            ACTION_RECORD_INTERRUPTION -> {
                sessionInterruptionCount++
                _interruptionCount.value = sessionInterruptionCount
            }
        }
        return START_NOT_STICKY
    }

    private var targetEndTimeRealtime: Long = 0L
    private var sessionStartTimeIso: String = ""
    private var sessionScheduleId: String? = null
    private var sessionCategory: String = "WORK"
    private var sessionInterruptionCount: Int = 0

    private fun startFocusSession(title: String, seconds: Int, scheduleId: String? = null, category: String = "WORK") {
        _currentTaskTitle.value = title
        _initialTotalSeconds.value = seconds
        _remainingSeconds.value = seconds
        _isRunning.value = true
        sessionStartTimeIso = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_DATE_TIME)
        sessionScheduleId = scheduleId
        sessionCategory = category
        sessionInterruptionCount = 0
        _interruptionCount.value = 0
        targetEndTimeRealtime = SystemClock.elapsedRealtime() + (seconds * 1000L)

        scheduleFinishAlarm(seconds)

        val notification = buildFluidCloudNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        launchTimerSyncLoop()
        com.renly.rmf.ui.widget.RmfWidgetManager.refreshAllWidgets(applicationContext)
    }

    private fun pauseFocusSession() {
        if (!_isRunning.value) return
        cancelFinishAlarm()
        val remaining = maxOf(0, ((targetEndTimeRealtime - SystemClock.elapsedRealtime()) / 1000).toInt())
        _remainingSeconds.value = remaining
        _isRunning.value = false
        timerJob?.cancel()

        sessionInterruptionCount++
        _interruptionCount.value = sessionInterruptionCount

        serviceScope.launch(Dispatchers.IO) {
            try {
                val db = com.renly.rmf.data.local.RmfDatabase.getInstance(applicationContext)
                val nowIso = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_DATE_TIME)
                val interruption = com.renly.rmf.data.local.entity.InterruptionEntity(
                    scheduleId = sessionScheduleId,
                    type = "INTERNAL",
                    durationSeconds = 60,
                    timestamp = nowIso,
                    note = "专注暂停打断"
                )
                db.interruptionDao().insert(interruption)
            } catch (_: Exception) {}
        }

        val notification = buildFluidCloudNotification()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
        com.renly.rmf.ui.widget.RmfWidgetManager.refreshAllWidgets(applicationContext)
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
            com.renly.rmf.ui.widget.RmfWidgetManager.refreshAllWidgets(applicationContext)
        }
    }

    private fun stopFocusSession() {
        cancelFinishAlarm()
        val elapsedSeconds = maxOf(0, _initialTotalSeconds.value - _remainingSeconds.value)
        val count = sessionInterruptionCount
        if (elapsedSeconds >= 60) {
            saveRecordToDb(elapsedSeconds / 60, "INTERRUPTED", count)
        }
        _isRunning.value = false
        _remainingSeconds.value = _initialTotalSeconds.value
        timerJob?.cancel()
        sessionInterruptionCount = 0
        _interruptionCount.value = 0

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        com.renly.rmf.ui.widget.RmfWidgetManager.refreshAllWidgets(applicationContext)
    }

    private fun onFocusCompleted() {
        cancelFinishAlarm()
        val elapsedSeconds = _initialTotalSeconds.value
        val count = sessionInterruptionCount
        if (elapsedSeconds >= 60) {
            saveRecordToDb(elapsedSeconds / 60, "COMPLETED", count)
        }
        _isRunning.value = false
        _remainingSeconds.value = 0
        timerJob?.cancel()
        sessionInterruptionCount = 0
        _interruptionCount.value = 0

        showCompletedNotification()
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
        com.renly.rmf.ui.widget.RmfWidgetManager.refreshAllWidgets(applicationContext)
    }

    private fun saveRecordToDb(durMins: Int, status: String, interruptions: Int = sessionInterruptionCount) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val db = com.renly.rmf.data.local.RmfDatabase.getInstance(applicationContext)
                val endTimeIso = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_DATE_TIME)
                val record = com.renly.rmf.data.local.entity.FocusRecordEntity(
                    scheduleId = sessionScheduleId,
                    taskTitle = _currentTaskTitle.value,
                    category = sessionCategory,
                    durationMinutes = durMins,
                    startTime = sessionStartTimeIso.ifBlank { endTimeIso },
                    endTime = endTimeIso,
                    status = status,
                    interruptionCount = interruptions
                )
                db.focusDao().insert(record)
            } catch (_: Exception) {}
        }
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

        // 1. 点击跳转回应用
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

        // 模式 0 (双借道融合)、模式 1 (媒体借道)、模式 2 (原生实时活动芯片)：
        // 核心突破：严禁包含任何 setCustomContentView，确保系统与 Android Live Updates 引擎能直接解析原生芯片！
        val builder = NotificationCompat.Builder(this, RmfApplication.CHANNEL_FLUID_CLOUD)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle("深度专注")
            .setContentText(if (isRunning) "剩余: $timeStr" else "已暂停 ($timeStr)")
            .setSubText(brandLabel)
            .setColor(0xFF00F0FF.toInt())
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openPendingIntent)

        // 添加标准交互 Action 按钮
        builder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_logo,
                if (isRunning) "暂停" else "继续",
                togglePendingIntent
            ).build()
        )
        builder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_logo,
                "结束",
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

        // 注入 Android 16 Promoted Ongoing 标记
        FluidCloudHelper.applyPromotedOngoing(builder)

        if (isLiveUpdateMode) {
            // Android 16 官方 Live Updates 规范:
            // 严禁绑定自定义 RemoteViews，必须使用 BigTextStyle 等原生合规样式，
            // 才能通过 Notification.hasPromotableCharacteristics() 校验并成功在支持设备上触发状态栏胶囊与锁屏实时活动
            val bigTextStyle = NotificationCompat.BigTextStyle()
                .setBigContentTitle("深度专注")
                .setSummaryText(brandLabel)
                .bigText(if (isRunning) "剩余 $timeStr" else "已暂停 ($timeStr)")
            builder.setStyle(bigTextStyle)
        } else {
            // 自定义 RemoteViews（针对未开放 Live Updates 权限的旧版系统回退）
            val capsuleView = RemoteViews(packageName, R.layout.notification_fluid_cloud_capsule).apply {
                setTextViewText(R.id.fluid_capsule_title, "深度专注")
                setTextViewText(R.id.fluid_capsule_sub, if (isRunning) "专注中" else "已暂停")
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
                setTextViewText(R.id.fluid_expanded_header, "深度专注")
                setTextViewText(R.id.fluid_expanded_state_badge, if (isRunning) "专注中" else "已暂停")
                setTextViewText(R.id.fluid_expanded_task_title, title)
                setTextViewText(R.id.fluid_btn_pause, if (isRunning) "暂停" else "继续")
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
        const val ACTION_RECORD_INTERRUPTION = "com.renly.rmf.action.RECORD_INTERRUPTION"

        const val EXTRA_TITLE = "extra_focus_title"
        const val EXTRA_SECONDS = "extra_focus_seconds"
        const val EXTRA_SCHEDULE_ID = "extra_focus_schedule_id"
        const val EXTRA_CATEGORY = "extra_focus_category"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _initialTotalSeconds = MutableStateFlow(25 * 60)
        val initialTotalSeconds: StateFlow<Int> = _initialTotalSeconds.asStateFlow()

        private val _remainingSeconds = MutableStateFlow(25 * 60)
        val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

        private val _currentTaskTitle = MutableStateFlow("深度心流")
        val currentTaskTitle: StateFlow<String> = _currentTaskTitle.asStateFlow()

        private val _interruptionCount = MutableStateFlow(0)
        val interruptionCount: StateFlow<Int> = _interruptionCount.asStateFlow()

        fun start(
            context: Context,
            title: String = "深度心流",
            seconds: Int = 25 * 60,
            scheduleId: String? = null,
            category: String = "WORK"
        ) {
            val intent = Intent(context, FocusLiveService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SECONDS, seconds)
                putExtra(EXTRA_SCHEDULE_ID, scheduleId)
                putExtra(EXTRA_CATEGORY, category)
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

        fun recordInterruption(context: Context) {
            val intent = Intent(context, FocusLiveService::class.java).apply {
                action = ACTION_RECORD_INTERRUPTION
            }
            context.startService(intent)
        }
    }
}
