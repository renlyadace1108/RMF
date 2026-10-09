package com.renly.rmf.domain.fitness

import com.renly.rmf.data.local.dao.FitnessDao
import com.renly.rmf.data.local.dao.ScheduleDao
import com.renly.rmf.data.local.entity.ScheduleEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 健身计划与主页日程（Schedule）双向联动管理器
 * 负责在健身计划变更、动作增删、打卡完成时自动同步至日程流
 */
object FitnessScheduleSyncManager {

    suspend fun syncFitnessPlanToSchedule(
        fitnessDao: FitnessDao,
        scheduleDao: ScheduleDao,
        dateStr: String
    ) {
        val plan = fitnessDao.getPlanByDate(dateStr)
        val records = fitnessDao.getRecordsByDate(dateStr)
        val scheduleId = "fitness_schedule_$dateStr"

        if (plan == null && records.isEmpty()) {
            val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            scheduleDao.softDelete(scheduleId, nowStr)
            return
        }

        val planTitle = plan?.title ?: "每日专业健身训练"
        val durationMins = plan?.targetDurationMinutes ?: 45
        val totalCalories = records.sumOf { it.caloriesBurned }.takeIf { it > 0 } ?: (plan?.caloriesBurned ?: 300.0)

        val targetDate = try {
            LocalDate.parse(dateStr)
        } catch (_: Exception) {
            LocalDate.now()
        }

        val existingSched = scheduleDao.getScheduleById(scheduleId)
        val startDateTime = if (existingSched != null) {
            try {
                LocalDateTime.parse(existingSched.startTime)
            } catch (_: Exception) {
                targetDate.atTime(18, 30)
            }
        } else {
            val now = LocalDateTime.now()
            if (targetDate == LocalDate.now() && now.hour >= 18) {
                now.plusMinutes(5)
            } else {
                targetDate.atTime(18, 30)
            }
        }

        val endDateTime = startDateTime.plusMinutes(durationMins.toLong())
        val startStr = startDateTime.format(DateTimeFormatter.ISO_DATE_TIME)
        val endStr = endDateTime.format(DateTimeFormatter.ISO_DATE_TIME)
        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)

        val sbDesc = StringBuilder()
        sbDesc.append("🏋️ 健身主题: $planTitle · 预计用时: ${durationMins}m\n")
        if (totalCalories > 0) {
            sbDesc.append("🔥 预估消耗: ~${totalCalories.toInt()} kcal\n")
        }
        if (records.isNotEmpty()) {
            sbDesc.append("📋 训练动作清单 (${records.size}项):\n")
            records.forEach { r ->
                val sets = r.getWorkoutSets()
                val completedSets = sets.count { it.isCompleted }
                val setInfo = if (sets.isNotEmpty()) {
                    if (completedSets > 0) "${completedSets}/${sets.size}组完成" else "${sets.size}组"
                } else "${r.setsCount}组"
                val doneTag = if (r.isCompleted == 1 || (sets.isNotEmpty() && completedSets == sets.size)) "✓" else "·"
                sbDesc.append(" $doneTag ${r.exerciseName} ($setInfo)\n")
            }
        }
        if (!plan?.notes.isNullOrBlank()) {
            sbDesc.append("💡 要点: ${plan?.notes}")
        }

        val isCompleted = (plan?.status == "COMPLETED") || (records.isNotEmpty() && records.all { it.isCompleted == 1 })
        val actualMins = if (plan?.actualDurationMinutes != null && plan.actualDurationMinutes > 0) {
            plan.actualDurationMinutes
        } else if (isCompleted) durationMins else 0

        val sched = if (existingSched != null) {
            existingSched.copy(
                title = "🏋️ [健身] $planTitle",
                description = sbDesc.toString().trimEnd(),
                category = "HEALTH",
                workType = "REST_BUFFER",
                priority = "MEDIUM",
                status = if (isCompleted) "COMPLETED" else "PENDING",
                startTime = startStr,
                endTime = endStr,
                estimatedMinutes = durationMins,
                actualMinutes = actualMins,
                isDeleted = 0,
                updatedAt = nowStr
            )
        } else {
            ScheduleEntity(
                id = scheduleId,
                title = "🏋️ [健身] $planTitle",
                description = sbDesc.toString().trimEnd(),
                category = "HEALTH",
                workType = "REST_BUFFER",
                priority = "MEDIUM",
                status = if (isCompleted) "COMPLETED" else "PENDING",
                startTime = startStr,
                endTime = endStr,
                estimatedMinutes = durationMins,
                actualMinutes = actualMins,
                createdAt = nowStr,
                updatedAt = nowStr,
                isDeleted = 0
            )
        }

        scheduleDao.insertOrUpdate(sched)
    }
}
