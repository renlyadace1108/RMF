package com.renly.rmf.domain.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 原生系统 JobService 看门狗：
 * 周期性 (每15-20分钟) 在系统级后台运行，
 * 负责救赎被厂商内存强杀清理掉的精准闹钟与逾期未触发提醒，
 * setPersisted(true) 保证重启后依然保持系统级托管，永不失联。
 */
class ReminderWatchdogJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ScheduleReminderManager.rescheduleAll(applicationContext)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true // 若被系统强行抢占中断，稍后自动重试
    }

    companion object {
        const val JOB_ID = 9182

        fun schedule(context: Context) {
            try {
                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                val isScheduled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    scheduler.getPendingJob(JOB_ID) != null
                } else {
                    scheduler.allPendingJobs.any { it.id == JOB_ID }
                }

                if (!isScheduled) {
                    val component = ComponentName(context, ReminderWatchdogJobService::class.java)
                    val builder = JobInfo.Builder(JOB_ID, component)
                        .setPeriodic(15 * 60 * 1000L) // 15 分钟标准周期
                        .setPersisted(true) // 关机重启后系统自动保留调度
                    scheduler.schedule(builder.build())
                }
            } catch (_: Exception) { }
        }
    }
}
