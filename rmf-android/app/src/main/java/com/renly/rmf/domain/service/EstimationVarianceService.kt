package com.renly.rmf.domain.service

import android.content.Context
import com.renly.rmf.data.database.RmfDatabase
import com.renly.rmf.data.local.entity.ScheduleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

data class EstimationVarianceReport(
    val totalTasksEvaluated: Int,
    val totalEstimatedMinutes: Int,
    val totalActualMinutes: Int,
    val varianceMinutes: Int = totalActualMinutes - totalEstimatedMinutes,
    val variancePercentage: Double = if (totalEstimatedMinutes > 0) (varianceMinutes.toDouble() / totalEstimatedMinutes) * 100.0 else 0.0,
    val accuracyScore: Double = 0.0,
    val categoryMetrics: List<CategoryVarianceMetric> = emptyList(),
    val recommendation: String = ""
)

data class CategoryVarianceMetric(
    val category: String,
    val taskCount: Int,
    val estimatedMinutes: Int,
    val actualMinutes: Int,
    val factor: Double = if (estimatedMinutes > 0) actualMinutes.toDouble() / estimatedMinutes else 1.0,
    val statusText: String = when {
        factor > 1.15 -> "超时 +${((factor - 1) * 100).roundToInt()}%"
        factor < 0.85 -> "提前 ${((1 - factor) * 100).roundToInt()}%"
        else -> "准点对齐"
    }
)

/**
 * 计划 vs 实际耗时偏差复盘与任务自愈闭环引擎
 */
object EstimationVarianceService {
    fun analyze(schedules: List<ScheduleEntity>): EstimationVarianceReport {
        val evaluated = schedules.filter {
            it.status == "COMPLETED" && it.estimatedMinutes > 0 && it.actualMinutes > 0
        }

        if (evaluated.isEmpty()) {
            return EstimationVarianceReport(
                totalTasksEvaluated = 0,
                totalEstimatedMinutes = 0,
                totalActualMinutes = 0,
                recommendation = "暂无带有实际用时记录的已完成任务。在任务打卡或专注结束时记录耗时，即可自动生成个人专属偏差校准模型。"
            )
        }

        val totalEst = evaluated.sumOf { it.estimatedMinutes }
        val totalAct = evaluated.sumOf { it.actualMinutes }
        val accurateCount = evaluated.count { abs(it.actualMinutes - it.estimatedMinutes) <= 10 }
        val accuracy = Math.round((accurateCount.toDouble() / evaluated.size * 100.0) * 10.0) / 10.0

        val catMetrics = evaluated.groupBy { it.category.ifBlank { "WORK" } }.map { (cat, list) ->
            CategoryVarianceMetric(
                category = cat,
                taskCount = list.size,
                estimatedMinutes = list.sumOf { it.estimatedMinutes },
                actualMinutes = list.sumOf { it.actualMinutes }
            )
        }

        val overrunningCat = catMetrics.filter { it.factor > 1.2 }.maxByOrNull { it.factor }
        val advice = if (overrunningCat != null) {
            "💡 偏差洞察：你在【${overrunningCat.category}】类任务上实际耗时平均为预估的 ${"%.2f".format(overrunningCat.factor)} 倍（超期 ${((overrunningCat.factor - 1) * 100).roundToInt()}%），排期时建议主动预留 15 分钟缓冲。"
        } else {
            "🌟 节奏卓越：你的任务预估准确率达 ${accuracy.toInt()}%，计划与实际执行高度拟合，精力感知能力非常精准！"
        }

        return EstimationVarianceReport(
            totalTasksEvaluated = evaluated.size,
            totalEstimatedMinutes = totalEst,
            totalActualMinutes = totalAct,
            accuracyScore = accuracy,
            categoryMetrics = catMetrics,
            recommendation = advice
        )
    }

    suspend fun spilloverTasksToToday(database: RmfDatabase, tasks: List<ScheduleEntity>): Int = withContext(Dispatchers.IO) {
        if (tasks.isEmpty()) return@withContext 0
        val scheduleDao = database.scheduleDao()
        val now = LocalDateTime.now()
        var nextStart = now.plusMinutes(15)
        val remMin = nextStart.minute % 15
        if (remMin > 0) nextStart = nextStart.plusMinutes((15 - remMin).toLong())

        val nowIso = now.format(DateTimeFormatter.ISO_DATE_TIME)
        var count = 0
        for (task in tasks) {
            val duration = if (task.estimatedMinutes > 0) task.estimatedMinutes else 30
            val newEnd = nextStart.plusMinutes(duration.toLong())
            val updated = task.copy(
                startTime = nextStart.format(DateTimeFormatter.ISO_DATE_TIME),
                endTime = newEnd.format(DateTimeFormatter.ISO_DATE_TIME),
                estimatedMinutes = duration,
                isDeferred = 1,
                syncVersion = (task.syncVersion ?: 1) + 1,
                updatedAt = nowIso
            )
            scheduleDao.insertOrUpdate(updated)
            nextStart = newEnd.plusMinutes(5)
            count++
        }
        return@withContext count
    }

    suspend fun movePastTasksToBacklog(database: RmfDatabase, tasks: List<ScheduleEntity>): Int = withContext(Dispatchers.IO) {
        if (tasks.isEmpty()) return@withContext 0
        val scheduleDao = database.scheduleDao()
        val nowIso = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
        var count = 0
        for (task in tasks) {
            val updated = task.copy(
                isBacklog = 1,
                syncVersion = (task.syncVersion ?: 1) + 1,
                updatedAt = nowIso
            )
            scheduleDao.insertOrUpdate(updated)
            count++
        }
        return@withContext count
    }
}
