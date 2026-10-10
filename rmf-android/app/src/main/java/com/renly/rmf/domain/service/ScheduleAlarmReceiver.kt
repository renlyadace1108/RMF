package com.renly.rmf.domain.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.renly.rmf.MainActivity
import com.renly.rmf.R
import com.renly.rmf.RmfApplication

class ScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "日程时间块"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "即将开始，请准备进入专注状态"
        val notificationId = intent.getIntExtra(EXTRA_ID, 1001)
        val isCourse = intent.getBooleanExtra(EXTRA_IS_COURSE, false)
        val courseLocation = intent.getStringExtra(EXTRA_COURSE_LOCATION) ?: ""
        val courseTeacher = intent.getStringExtra(EXTRA_COURSE_TEACHER) ?: ""
        val courseStartTimeMillis = intent.getLongExtra(EXTRA_COURSE_START_MILLIS, 0L)
        val courseDurationMinutes = intent.getIntExtra(EXTRA_COURSE_DURATION_MINUTES, 45)

        // 1. 点击跳转回应用
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (isCourse) RmfApplication.CHANNEL_FLUID_CLOUD else RmfApplication.CHANNEL_SCHEDULE_REMINDER

        val defaultSound = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_logo)
            .setColor(0xFF00F0FF.toInt())
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(if (isCourse) NotificationCompat.CATEGORY_EVENT else NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(defaultSound)
            .setVibrate(longArrayOf(0, 350, 200, 350))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setSubText(if (isCourse) "ColorOS 流体云 · 上课提醒" else "ColorOS 流体云提醒")

        if (isCourse) {
            // 2. 快捷一键进入听课专注 (FocusLiveService)
            val focusIntent = Intent(context, FocusLiveService::class.java).apply {
                action = FocusLiveService.ACTION_START
                putExtra(FocusLiveService.EXTRA_TITLE, "听课专注: $title")
                putExtra(FocusLiveService.EXTRA_SECONDS, courseDurationMinutes * 60)
            }
            val focusPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    context,
                    notificationId + 10,
                    focusIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                PendingIntent.getService(
                    context,
                    notificationId + 10,
                    focusIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            // 计算倒计时 millis
            val remainingMillis = if (courseStartTimeMillis > System.currentTimeMillis()) {
                courseStartTimeMillis - System.currentTimeMillis()
            } else {
                0L
            }

            // 胶囊卡片 RemoteViews
            val capsuleView = RemoteViews(context.packageName, R.layout.notification_fluid_cloud_course_capsule).apply {
                setTextViewText(R.id.fluid_course_capsule_title, "🎓 $title")
                val locText = if (courseLocation.isNotBlank()) "📍 $courseLocation" else "即将开课"
                setTextViewText(R.id.fluid_course_capsule_location, "$locText | 点击进入流体云")
                setOnClickPendingIntent(R.id.fluid_course_capsule_btn, focusPendingIntent)

                if (remainingMillis > 0) {
                    setChronometer(
                        R.id.fluid_course_capsule_chronometer,
                        SystemClock.elapsedRealtime() + remainingMillis,
                        "%s",
                        true
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        setChronometerCountDown(R.id.fluid_course_capsule_chronometer, true)
                    }
                } else {
                    setChronometer(R.id.fluid_course_capsule_chronometer, SystemClock.elapsedRealtime(), "进行中", false)
                }
            }

            // 展开大卡片 RemoteViews
            val expandedView = RemoteViews(context.packageName, R.layout.notification_fluid_cloud_course_expanded).apply {
                setTextViewText(R.id.fluid_course_expanded_title, "🎓 $title")
                val detailStr = StringBuilder()
                if (courseLocation.isNotBlank()) detailStr.append("📍 地点: $courseLocation  ")
                if (courseTeacher.isNotBlank()) detailStr.append("👤 教师: $courseTeacher")
                setTextViewText(R.id.fluid_course_expanded_detail, detailStr.toString().ifBlank { "无地点信息" })
                setTextViewText(R.id.fluid_course_expanded_time_range, "时长: ${courseDurationMinutes}分钟")

                setOnClickPendingIntent(R.id.fluid_course_btn_focus, focusPendingIntent)
                setOnClickPendingIntent(R.id.fluid_course_btn_view, pendingIntent)

                if (remainingMillis > 0) {
                    setChronometer(
                        R.id.fluid_course_expanded_chronometer,
                        SystemClock.elapsedRealtime() + remainingMillis,
                        "%s",
                        true
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        setChronometerCountDown(R.id.fluid_course_expanded_chronometer, true)
                    }
                } else {
                    setChronometer(R.id.fluid_course_expanded_chronometer, SystemClock.elapsedRealtime(), "正在上课", false)
                }
            }

            builder.setContentTitle("🎓 上课提醒: $title")
                .setContentText(message)
                .setCustomContentView(capsuleView)
                .setCustomBigContentView(expandedView)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())

            if (remainingMillis > 0) {
                builder.setUsesChronometer(true)
                builder.setWhen(System.currentTimeMillis() + remainingMillis)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    builder.setChronometerCountDown(true)
                }
            }
        } else {
            // 通用日程也以 ColorOS 流体云实时胶囊与卡片呈现！
            val startMillis = intent.getLongExtra(EXTRA_COURSE_START_MILLIS, 0L)
            val remMillis = if (startMillis > System.currentTimeMillis()) startMillis - System.currentTimeMillis() else 0L

            val focusIntent = Intent(context, FocusLiveService::class.java).apply {
                action = FocusLiveService.ACTION_START
                putExtra(FocusLiveService.EXTRA_TITLE, "专注: $title")
                putExtra(FocusLiveService.EXTRA_SECONDS, 25 * 60)
            }
            val focusPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    context,
                    notificationId + 20,
                    focusIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                PendingIntent.getService(
                    context,
                    notificationId + 20,
                    focusIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            val schedCapsule = RemoteViews(context.packageName, R.layout.notification_fluid_cloud_schedule_capsule).apply {
                setTextViewText(R.id.fluid_schedule_capsule_title, "📌 $title")
                setTextViewText(R.id.fluid_schedule_capsule_sub, message)
                setOnClickPendingIntent(R.id.fluid_schedule_capsule_btn, focusPendingIntent)

                if (remMillis > 0) {
                    setChronometer(
                        R.id.fluid_schedule_capsule_chronometer,
                        SystemClock.elapsedRealtime() + remMillis,
                        "%s",
                        true
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        setChronometerCountDown(R.id.fluid_schedule_capsule_chronometer, true)
                    }
                } else {
                    setChronometer(R.id.fluid_schedule_capsule_chronometer, SystemClock.elapsedRealtime(), "即刻", false)
                }
            }

            val schedExpanded = RemoteViews(context.packageName, R.layout.notification_fluid_cloud_schedule_expanded).apply {
                setTextViewText(R.id.fluid_schedule_expanded_title, "📌 $title")
                setTextViewText(R.id.fluid_schedule_expanded_desc, message)
                setOnClickPendingIntent(R.id.fluid_schedule_btn_focus, focusPendingIntent)
                setOnClickPendingIntent(R.id.fluid_schedule_btn_view, pendingIntent)

                if (remMillis > 0) {
                    setChronometer(
                        R.id.fluid_schedule_expanded_chronometer,
                        SystemClock.elapsedRealtime() + remMillis,
                        "%s",
                        true
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        setChronometerCountDown(R.id.fluid_schedule_expanded_chronometer, true)
                    }
                } else {
                    setChronometer(R.id.fluid_schedule_expanded_chronometer, SystemClock.elapsedRealtime(), "进行中", false)
                }
            }

            builder.setContentTitle("📌 日程提醒: $title")
                .setContentText(message)
                .setCustomContentView(schedCapsule)
                .setCustomBigContentView(schedExpanded)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())

            if (remMillis > 0) {
                builder.setUsesChronometer(true)
                builder.setWhen(System.currentTimeMillis() + remMillis)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    builder.setChronometerCountDown(true)
                }
            }
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, builder.build())
    }

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_ID = "extra_id"
        const val EXTRA_IS_COURSE = "extra_is_course"
        const val EXTRA_COURSE_LOCATION = "extra_course_location"
        const val EXTRA_COURSE_TEACHER = "extra_course_teacher"
        const val EXTRA_COURSE_START_MILLIS = "extra_course_start_millis"
        const val EXTRA_COURSE_DURATION_MINUTES = "extra_course_duration_minutes"
    }
}
