package com.renly.rmf.domain.service

import com.renly.rmf.data.local.entity.ScheduleEntity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class AuditFinding(
    val level: String, // "WARNING", "DANGER", "TIP"
    val title: String,
    val description: String
)

data class AuditReport(
    val score: Int, // 0 - 100 节律健康分
    val findings: List<AuditFinding>,
    val totalDeepWorkMinutes: Int,
    val totalRestMinutes: Int,
    val conflictCount: Int
)

object SchedulerAuditEngine {

    fun audit(schedules: List<ScheduleEntity>): AuditReport {
        val active = schedules.filter { it.isDeleted == 0 }
            .sortedBy { it.startTime }

        val findings = mutableListOf<AuditFinding>()
        var score = 100
        var totalDeepWork = 0
        var totalRest = 0
        var conflictCount = 0

        // 1. 冲突排期检测 (Hard Overlap)
        for (i in 0 until active.size - 1) {
            val curr = active[i]
            val next = active[i + 1]

            val currEnd = try { LocalDateTime.parse(curr.endTime, DateTimeFormatter.ISO_DATE_TIME) } catch (_: Exception) { null }
            val nextStart = try { LocalDateTime.parse(next.startTime, DateTimeFormatter.ISO_DATE_TIME) } catch (_: Exception) { null }

            if (currEnd != null && nextStart != null && currEnd.isAfter(nextStart)) {
                conflictCount++
                score -= 15
                findings.add(
                    AuditFinding(
                        level = "DANGER",
                        title = "检测到时间重叠冲突",
                        description = "「${curr.title}」与「${next.title}」存在排期重合，建议调整避免冲突。"
                    )
                )
            }
        }

        // 2. 统计各类型工作时长与连续深度负荷检测
        var continuousDeepMinutes = 0
        for (item in active) {
            val mins = item.estimatedMinutes
            if (item.workType == "DEEP_WORK") {
                totalDeepWork += mins
                continuousDeepMinutes += mins
                if (continuousDeepMinutes >= 240) { // 连续深度工作 4 小时
                    score -= 10
                    findings.add(
                        AuditFinding(
                            level = "WARNING",
                            title = "警惕精力透支断崖",
                            description = "连续深度工作已超 ${continuousDeepMinutes / 60} 小时，建议在「${item.title}」后插入 15~30 分钟 REST_BUFFER 缓冲休息。"
                        )
                    )
                    continuousDeepMinutes = 0 // 重置避免重复刷屏
                }
            } else if (item.workType == "REST_BUFFER") {
                totalRest += mins
                continuousDeepMinutes = 0 // 缓冲重置连续深度计数
            } else {
                continuousDeepMinutes = 0
            }
        }

        // 3. 全天深度工作总量预警
        if (totalDeepWork > 360) { // 超过 6 小时
            score -= 10
            findings.add(
                AuditFinding(
                    level = "WARNING",
                    title = "单日深度认知负荷过载",
                    description = "全天深度工作达到 ${totalDeepWork / 60} 小时，可能导致晚间认知衰竭与意志力崩溃。"
                )
            )
        }

        if (findings.isEmpty()) {
            findings.add(
                AuditFinding(
                    level = "TIP",
                    title = "节律状态优秀",
                    description = "当前时间流安排张弛有度，无冲突无透支，保持心流状态！"
                )
            )
        }

        return AuditReport(
            score = score.coerceIn(0, 100),
            findings = findings,
            totalDeepWorkMinutes = totalDeepWork,
            totalRestMinutes = totalRest,
            conflictCount = conflictCount
        )
    }
}
