package com.renly.rmf.domain.service

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ReminderRepeatHelper {

    /**
     * 根据当前设定时间与重复规则，计算下一次触发的 ISO 时间戳 (保证大于基准时间并对齐自然周期)
     */
    fun computeNextOccurrence(
        currentIso: String,
        repeatRule: String,
        baseTime: LocalDateTime = LocalDateTime.now()
    ): String? {
        if (repeatRule.isBlank() || repeatRule.equals("NONE", ignoreCase = true)) {
            return null
        }

        val startLdt = try {
            LocalDateTime.parse(currentIso, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        } catch (_: Exception) {
            try {
                LocalDateTime.parse(currentIso, DateTimeFormatter.ISO_DATE_TIME)
            } catch (_: Exception) {
                baseTime
            }
        }

        var next = startLdt

        when (repeatRule.uppercase()) {
            "DAILY" -> {
                // 每次加一天，直至严格在基准时间之后
                do {
                    next = next.plusDays(1)
                } while (!next.isAfter(baseTime))
            }

            "WEEKDAYS" -> {
                // 仅周一至周五，跳过周六周日
                do {
                    next = next.plusDays(1)
                } while (!next.isAfter(baseTime) || next.dayOfWeek == DayOfWeek.SATURDAY || next.dayOfWeek == DayOfWeek.SUNDAY)
            }

            "WEEKLY" -> {
                // 每次加 7 天
                do {
                    next = next.plusWeeks(1)
                } while (!next.isAfter(baseTime))
            }

            "MONTHLY" -> {
                // 每次加 1 个月
                do {
                    next = next.plusMonths(1)
                } while (!next.isAfter(baseTime))
            }

            "YEARLY" -> {
                // 每次加 1 年
                do {
                    next = next.plusYears(1)
                } while (!next.isAfter(baseTime))
            }

            else -> return null
        }

        return next.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }

    fun formatRepeatRule(repeatRule: String): String {
        return when (repeatRule.uppercase()) {
            "DAILY" -> "每天"
            "WEEKDAYS" -> "工作日 (周一至周五)"
            "WEEKLY" -> "每周"
            "MONTHLY" -> "每月"
            "YEARLY" -> "每年"
            else -> "单次提醒"
        }
    }
}
