package com.renly.rmf.domain.service

import com.renly.rmf.data.local.entity.ScheduleEntity
import com.renly.rmf.data.local.entity.TimeMarkerEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class VarianceInsight(
    val icon: String,
    val title: String,
    val detail: String,
    val level: String = "INFO" // "SUCCESS", "WARNING", "INFO"
)

object TimeMarkerVarianceAnalyzer {

    fun analyzeDay(
        targetDate: LocalDate,
        schedules: List<ScheduleEntity>,
        markers: List<TimeMarkerEntity>
    ): List<VarianceInsight> {
        val insights = mutableListOf<VarianceInsight>()
        val daySchedules = schedules.filter { item ->
            if (item.status == "BACKLOG" || item.isDeleted == 1) return@filter false
            val sDt = parseIso(item.startTime)
            sDt?.toLocalDate() == targetDate
        }.sortedBy { it.startTime }

        val dayMarkers = markers.filter {
            it.isDeleted == 0 && (it.date == targetDate.toString() || parseIso(it.startTime)?.toLocalDate() == targetDate)
        }.sortedBy { it.startTime }

        if (dayMarkers.isEmpty()) {
            insights.add(
                VarianceInsight(
                    icon = "💡",
                    title = "未记录时间标记",
                    detail = "点击下方预设或自定义标记添加起床、就寝或午休，开启实际执行与预计日程的精准偏差分析。",
                    level = "INFO"
                )
            )
            return insights
        }

        // 1. 起床时间分析 (Wake up vs First Schedule)
        val wakeMarker = dayMarkers.firstOrNull { it.name.contains("起床") || it.category == "SLEEP" && it.icon == "🌅" }
        if (wakeMarker != null) {
            val wakeTime = parseIsoTime(wakeMarker.startTime)
            if (wakeTime != null) {
                if (daySchedules.isNotEmpty()) {
                    val firstSchedule = daySchedules.first()
                    val firstSchedTime = parseIsoTime(firstSchedule.startTime)
                    if (firstSchedTime != null) {
                        val diffMinutes = ChronoUnit.MINUTES.between(firstSchedTime, wakeTime)
                        if (diffMinutes > 10) {
                            insights.add(
                                VarianceInsight(
                                    icon = "⚠️",
                                    title = "晨间起床晚于计划日程",
                                    detail = "首个日程「${firstSchedule.title}」定于 %02d:%02d，实际于 %02d:%02d 起床（推迟 %d 分钟），建议适当延后首个任务或调整前夜就寝。".format(
                                        firstSchedTime.hour, firstSchedTime.minute, wakeTime.hour, wakeTime.minute, diffMinutes
                                    ),
                                    level = "WARNING"
                                )
                            )
                        } else if (diffMinutes < -15) {
                            val advanceMinutes = abs(diffMinutes)
                            insights.add(
                                VarianceInsight(
                                    icon = "🌅",
                                    title = "从容早起，预留充沛缓冲",
                                    detail = "实际于 %02d:%02d 起床，比首个日程「${firstSchedule.title}」早 %d 分钟，晨间精力准备充足！".format(
                                        wakeTime.hour, wakeTime.minute, advanceMinutes
                                    ),
                                    level = "SUCCESS"
                                )
                            )
                        } else {
                            insights.add(
                                VarianceInsight(
                                    icon = "🎯",
                                    title = "晨起与首个日程准点对齐",
                                    detail = "实际于 %02d:%02d 起床，与首个计划日程无缝顺承。".format(wakeTime.hour, wakeTime.minute),
                                    level = "SUCCESS"
                                )
                            )
                        }
                    }
                } else {
                    insights.add(
                        VarianceInsight(
                            icon = "🌅",
                            title = "记录起床时间: %02d:%02d".format(wakeTime.hour, wakeTime.minute),
                            detail = "今日暂未安排具体开始时间的日程，自由作息流。",
                            level = "INFO"
                        )
                    )
                }
            }
        }

        // 2. 午休 / 休息时间段分析 (Rest/Nap Range vs Schedule Overlaps)
        val restMarkers = dayMarkers.filter {
            (it.name.contains("午休") || it.name.contains("午睡") || it.category == "REST") && it.markerType == "RANGE"
        }
        for (rest in restMarkers) {
            val rStart = parseIsoTime(rest.startTime)
            val rEnd = parseIsoTime(rest.endTime)
            if (rStart != null && rEnd != null) {
                val napMinutes = ChronoUnit.MINUTES.between(rStart, rEnd)
                // 查找与该午休时间段重叠的日程
                val overlappingSchedules = daySchedules.filter { sched ->
                    val sStart = parseIsoTime(sched.startTime)
                    val sEnd = parseIsoTime(sched.endTime)
                    if (sStart != null && sEnd != null) {
                        sStart < rEnd && sEnd > rStart
                    } else false
                }

                if (overlappingSchedules.isNotEmpty()) {
                    val titles = overlappingSchedules.joinToString("、") { "「${it.title}」" }
                    insights.add(
                        VarianceInsight(
                            icon = "💤",
                            title = "实际${rest.name}挤占预计日程",
                            detail = "实际${rest.name}（%02d:%02d ~ %02d:%02d，共 %d 分钟）与计划日程 %s 发生时间重叠，计划执行被作息中断或后移。".format(
                                rStart.hour, rStart.minute, rEnd.hour, rEnd.minute, napMinutes, titles
                            ),
                            level = "WARNING"
                        )
                    )
                } else {
                    insights.add(
                        VarianceInsight(
                            icon = "💤",
                            title = "规律${rest.name}: %d 分钟".format(napMinutes),
                            detail = "实际于 %02d:%02d ~ %02d:%02d 深度充能，未与工作日程发生冲突，作息安排健康。".format(
                                rStart.hour, rStart.minute, rEnd.hour, rEnd.minute
                            ),
                            level = "SUCCESS"
                        )
                    )
                }
            }
        }

        // 3. 睡觉 / 收工时间分析 (Bedtime / Off-work vs Last Schedule)
        val sleepMarker = dayMarkers.firstOrNull { it.name.contains("睡觉") || it.name.contains("就寝") || it.name.contains("收工") }
        if (sleepMarker != null) {
            val sleepTime = parseIsoTime(sleepMarker.startTime)
            if (sleepTime != null && daySchedules.isNotEmpty()) {
                val lastSchedule = daySchedules.last()
                val lastEndTime = parseIsoTime(lastSchedule.endTime) ?: parseIsoTime(lastSchedule.startTime)
                if (lastEndTime != null) {
                    val diff = ChronoUnit.MINUTES.between(lastEndTime, sleepTime)
                    if (diff > 60) {
                        insights.add(
                            VarianceInsight(
                                icon = "🌙",
                                title = "晚间延展执行 / 休闲时间",
                                detail = "末个日程「${lastSchedule.title}」于 %02d:%02d 结束，实际于 %02d:%02d ${sleepMarker.name}（间隔 %d 分钟），注意保持稳定作息避免睡眠过晚。".format(
                                    lastEndTime.hour, lastEndTime.minute, sleepTime.hour, sleepTime.minute, diff
                                ),
                                level = if (sleepTime.hour >= 23 || sleepTime.hour < 5) "WARNING" else "INFO"
                            )
                        )
                    } else {
                        insights.add(
                            VarianceInsight(
                                icon = "🌙",
                                title = "作息闭环顺畅",
                                detail = "日程结束后及时于 %02d:%02d ${sleepMarker.name}，今日效能圆满收官。".format(sleepTime.hour, sleepTime.minute),
                                level = "SUCCESS"
                            )
                        )
                    }
                }
            }
        }

        // 4. 自定义标记与时间节点汇总
        val customMarkers = dayMarkers.filter {
            it != wakeMarker && it != sleepMarker && !restMarkers.contains(it)
        }
        if (customMarkers.isNotEmpty()) {
            val summary = customMarkers.joinToString(", ") {
                val t = parseIsoTime(it.startTime)?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: ""
                "${it.icon} ${it.name} ($t)"
            }
            insights.add(
                VarianceInsight(
                    icon = "📌",
                    title = "今日其他真实时间节点",
                    detail = summary,
                    level = "INFO"
                )
            )
        }

        return insights
    }

    fun buildSummaryTextForAi(
        targetDate: LocalDate,
        schedules: List<ScheduleEntity>,
        markers: List<TimeMarkerEntity>
    ): String {
        val dayMarkers = markers.filter {
            it.isDeleted == 0 && (it.date == targetDate.toString() || parseIso(it.startTime)?.toLocalDate() == targetDate)
        }.sortedBy { it.startTime }

        if (dayMarkers.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("【今日用户打卡记录的真实生活/作息时间标记】\n")
        dayMarkers.forEach { m ->
            val sTime = parseIsoTime(m.startTime)?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: m.startTime
            if (m.markerType == "RANGE" && m.endTime.isNotBlank()) {
                val eTime = parseIsoTime(m.endTime)?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: m.endTime
                sb.append("- ${m.icon} ${m.name} [时间段]: $sTime ~ $eTime")
            } else {
                sb.append("- ${m.icon} ${m.name} [时间点]: $sTime")
            }
            if (m.notes.isNotBlank()) {
                sb.append(" (备注: ${m.notes})")
            }
            sb.append("\n")
        }

        val insights = analyzeDay(targetDate, schedules, markers)
        val warnings = insights.filter { it.level == "WARNING" }
        if (warnings.isNotEmpty()) {
            sb.append("【计划与实际执行偏差洞察】\n")
            warnings.forEach { w ->
                sb.append("• ${w.title}: ${w.detail}\n")
            }
        }

        return sb.toString().trim()
    }

    private fun parseIso(str: String): LocalDateTime? {
        return try {
            if (str.isBlank()) null else LocalDateTime.parse(str)
        } catch (_: Exception) {
            try {
                if (str.length >= 19) LocalDateTime.parse(str.substring(0, 19)) else null
            } catch (_: Exception) { null }
        }
    }

    private fun parseIsoTime(str: String): LocalTime? {
        return try {
            if (str.isBlank()) null
            else if (str.contains("T")) LocalDateTime.parse(str).toLocalTime()
            else if (str.contains(":")) {
                val parts = str.split(":")
                LocalTime.of(parts[0].toInt(), parts[1].toInt())
            } else null
        } catch (_: Exception) { null }
    }
}
