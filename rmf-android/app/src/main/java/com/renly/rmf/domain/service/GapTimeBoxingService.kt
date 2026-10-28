package com.renly.rmf.domain.service

import com.renly.rmf.data.database.RmfDatabase
import com.renly.rmf.data.local.entity.ScheduleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max

data class TimeGapSlot(
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val durationMinutes: Int,
    val suggestedBacklog: ScheduleEntity? = null,
    val suggestedStart: LocalDateTime = startTime,
    val suggestedEnd: LocalDateTime = if (suggestedBacklog != null && suggestedBacklog.estimatedMinutes > 0) {
        val fit = minOf(durationMinutes, suggestedBacklog.estimatedMinutes)
        startTime.plusMinutes(fit.toLong())
    } else {
        endTime
    }
) {
    val summaryText: String
        get() = if (suggestedBacklog != null) {
            val mins = if (suggestedBacklog.estimatedMinutes > 0) suggestedBacklog.estimatedMinutes else durationMinutes
            val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
            "${startTime.format(timeFmt)}~${endTime.format(timeFmt)} (${durationMinutes}m空闲) · 建议攻坚「${suggestedBacklog.title}」(需${mins}m)"
        } else {
            val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
            "${startTime.format(timeFmt)}~${endTime.format(timeFmt)} (${durationMinutes}m空闲碎片)"
        }
}

/**
 * 碎片空隙时间盒智能填空服务 (Gap-Filling Time-Boxing Engine)
 * 扫描课表与日程间的 20~60+ 分钟空当，智能匹配敏捷待办池并一键卡槽装填
 */
object GapTimeBoxingService {

    fun findDailyFreeGaps(
        date: LocalDate,
        daySchedules: List<ScheduleEntity>,
        backlogItems: List<ScheduleEntity>,
        minGapMinutes: Int = 20,
        dayStartHour: Int = 8,
        dayEndHour: Int = 22
    ): List<TimeGapSlot> {
        var startOfDay = date.atTime(dayStartHour, 0)
        val endOfDay = date.atTime(dayEndHour, 0)

        // 若为今日，则从当前时间（规整到后5分钟）开始扫描
        if (date == LocalDate.now()) {
            val now = LocalDateTime.now()
            val rem = now.minute % 5
            val rounded = if (rem == 0) now else now.plusMinutes((5 - rem).toLong())
            if (rounded.isAfter(startOfDay)) {
                startOfDay = rounded
            }
        }

        if (!startOfDay.isBefore(endOfDay)) return emptyList()

        // 1. 搜集忙碌区间
        val busyIntervals = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
        for (item in daySchedules) {
            if (item.isDeleted == 1 || (item.isBacklog ?: 0) == 1 || item.status == "CANCELLED" || item.status == "ABANDONED") {
                continue
            }
            val sStart = parseDateTime(item.startTime) ?: continue
            val sEnd = parseDateTime(item.endTime) ?: sStart.plusMinutes(max(10, item.estimatedMinutes).toLong())

            val clampedStart = if (sStart.isBefore(startOfDay)) startOfDay else sStart
            val clampedEnd = if (sEnd.isAfter(endOfDay)) endOfDay else sEnd

            if (clampedEnd.isAfter(clampedStart)) {
                busyIntervals.add(clampedStart to clampedEnd)
            }
        }

        // 2. 合并重叠或相邻的忙碌区间
        busyIntervals.sortBy { it.first }
        val mergedBusy = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
        for (interval in busyIntervals) {
            if (mergedBusy.isEmpty()) {
                mergedBusy.add(interval)
            } else {
                val last = mergedBusy.last()
                if (!interval.first.isAfter(last.second)) {
                    val newEnd = if (interval.second.isAfter(last.second)) interval.second else last.second
                    mergedBusy[mergedBusy.size - 1] = last.first to newEnd
                } else {
                    mergedBusy.add(interval)
                }
            }
        }

        // 3. 提取空隙
        val rawGaps = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
        var cursor = startOfDay
        for (busy in mergedBusy) {
            if (busy.first.isAfter(cursor)) {
                val gapMins = ChronoUnit.MINUTES.between(cursor, busy.first).toInt()
                if (gapMins >= minGapMinutes) {
                    rawGaps.add(cursor to busy.first)
                }
            }
            if (busy.second.isAfter(cursor)) {
                cursor = busy.second
            }
        }
        if (endOfDay.isAfter(cursor)) {
            val gapMins = ChronoUnit.MINUTES.between(cursor, endOfDay).toInt()
            if (gapMins >= minGapMinutes) {
                rawGaps.add(cursor to endOfDay)
            }
        }

        // 4. 匹配待办
        val activeBacklog = backlogItems
            .filter { it.status != "COMPLETED" && it.status != "ABANDONED" && it.isDeleted == 0 }
            .sortedByDescending { getPriorityScore(it.priority) }

        val usedIds = mutableSetOf<String>()
        val resultGaps = mutableListOf<TimeGapSlot>()

        for ((gStart, gEnd) in rawGaps) {
            val duration = ChronoUnit.MINUTES.between(gStart, gEnd).toInt()

            // 优先匹配耗时完全契合且优先级最高的待办
            val matched = activeBacklog.firstOrNull {
                !usedIds.contains(it.id) && it.estimatedMinutes in 1..duration
            } ?: activeBacklog.firstOrNull {
                !usedIds.contains(it.id) && it.estimatedMinutes <= 0
            }

            if (matched != null) {
                usedIds.add(matched.id)
            }

            resultGaps.add(
                TimeGapSlot(
                    startTime = gStart,
                    endTime = gEnd,
                    durationMinutes = duration,
                    suggestedBacklog = matched
                )
            )
        }

        return resultGaps
    }

    private fun getPriorityScore(priority: String): Int = when (priority) {
        "URGENT" -> 4
        "HIGH" -> 3
        "MEDIUM" -> 2
        else -> 1
    }

    private fun parseDateTime(str: String?): LocalDateTime? {
        if (str.isNullOrBlank()) return null
        val clean = str.trim().replace(" ", "T")
        return runCatching { LocalDateTime.parse(clean, DateTimeFormatter.ISO_DATE_TIME) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(clean) }.getOrNull()
    }

    suspend fun fillGapWithBacklog(database: RmfDatabase, gapSlot: TimeGapSlot): Boolean = withContext(Dispatchers.IO) {
        val backlog = gapSlot.suggestedBacklog ?: return@withContext false
        val scheduleDao = database.scheduleDao()
        val nowIso = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
        val est = if (backlog.estimatedMinutes > 0) backlog.estimatedMinutes else gapSlot.durationMinutes

        val updated = backlog.copy(
            startTime = gapSlot.suggestedStart.format(DateTimeFormatter.ISO_DATE_TIME),
            endTime = gapSlot.suggestedEnd.format(DateTimeFormatter.ISO_DATE_TIME),
            estimatedMinutes = est,
            isBacklog = 0,
            status = "PENDING",
            syncVersion = (backlog.syncVersion ?: 1) + 1,
            updatedAt = nowIso
        )
        scheduleDao.insertOrUpdate(updated)
        return@withContext true
    }
}
