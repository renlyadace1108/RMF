package com.renly.rmf.domain.service

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

data class ParsedScheduleResult(
    val title: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val durationMinutes: Int = 60,
    val priority: String = "MEDIUM",
    val category: String = "WORK",
    val tag: String? = null,
    val previewSummary: String = ""
)

object NaturalLanguageScheduleParser {

    fun parse(text: String): ParsedScheduleResult {
        if (text.isBlank()) {
            val now = LocalDateTime.now().withMinute(0).withSecond(0).plusHours(1)
            return ParsedScheduleResult(
                title = "新时间块",
                startTime = now,
                endTime = now.plusMinutes(60)
            )
        }

        var remaining = text.trim()
        var priority = "MEDIUM"
        var durationMinutes = 60
        var category = "WORK"
        var tag: String? = null

        // 1. 优先级提取: p:高 / p:high / p:urgent / p:紧急 / p:低 / p:low
        val pRegex = Pattern.compile("(?i)\\bp:(高|high|urgent|紧急|中|medium|低|low)\\b")
        val pMatcher = pRegex.matcher(remaining)
        if (pMatcher.find()) {
            val pVal = pMatcher.group(1)?.lowercase() ?: ""
            priority = when (pVal) {
                "高", "high", "urgent", "紧急" -> "HIGH"
                "低", "low" -> "LOW"
                else -> "MEDIUM"
            }
            remaining = remaining.replace(pMatcher.group(0) ?: "", "").trim()
        }

        // 2. 耗时提取: d:45m / d:1.5h / d:60
        val dRegex = Pattern.compile("(?i)\\bd:(\\d+(?:\\.\\d+)?)(m|h)?\\b")
        val dMatcher = dRegex.matcher(remaining)
        if (dMatcher.find()) {
            val num = dMatcher.group(1)?.toDoubleOrNull() ?: 60.0
            val unit = dMatcher.group(2)?.lowercase() ?: "m"
            durationMinutes = if (unit == "h") (num * 60).toInt() else num.toInt()
            remaining = remaining.replace(dMatcher.group(0) ?: "", "").trim()
        }

        // 3. 标签/分类提取: #学习 / #技术栈 / #生活 / #工作
        val tagRegex = Pattern.compile("#([^\\s#]+)")
        val tagMatcher = tagRegex.matcher(remaining)
        if (tagMatcher.find()) {
            tag = tagMatcher.group(1)
            category = if (tag.contains("学") || tag.contains("课") || tag.contains("考") || tag.contains("研")) {
                "STUDY"
            } else if (tag.contains("生活") || tag.contains("买") || tag.contains("休")) {
                "LIFE"
            } else {
                "WORK"
            }
            remaining = remaining.replace(tagMatcher.group(0) ?: "", "").trim()
        }

        // 4. 日期推导: 明天 / 明早 / 今天 / 今晚 / 后天
        var targetDate = LocalDate.now()
        var targetTime = LocalTime.of(9, 0)
        var hasExplicitTime = false

        if (remaining.contains("明早")) {
            targetDate = LocalDate.now().plusDays(1)
            targetTime = LocalTime.of(8, 30)
            hasExplicitTime = true
            remaining = remaining.replace("明早", "").trim()
        } else if (remaining.contains("明天")) {
            targetDate = LocalDate.now().plusDays(1)
            remaining = remaining.replace("明天", "").trim()
        } else if (remaining.contains("后天")) {
            targetDate = LocalDate.now().plusDays(2)
            remaining = remaining.replace("后天", "").trim()
        } else if (remaining.contains("今晚")) {
            targetDate = LocalDate.now()
            targetTime = LocalTime.of(19, 30)
            hasExplicitTime = true
            remaining = remaining.replace("今晚", "").trim()
        } else if (remaining.contains("今天")) {
            targetDate = LocalDate.now()
            remaining = remaining.replace("今天", "").trim()
        }

        // 5. 具体钟点提取: 下午3点 / 14:30 / 15点半 / 上午10:00 / 晚上8点
        val timeRegex = Pattern.compile("(早上|上午|中午|下午|晚上)?\\s*(\\d{1,2})(?:[:点时](\\d{1,2}|半))?")
        val timeMatcher = timeRegex.matcher(remaining)
        if (timeMatcher.find()) {
            val period = timeMatcher.group(1) ?: ""
            var hour = timeMatcher.group(2)?.toIntOrNull() ?: 9
            val minuteGroup = timeMatcher.group(3)
            val minute = when (minuteGroup) {
                "半" -> 30
                null -> 0
                else -> minuteGroup.toIntOrNull() ?: 0
            }

            if ((period == "下午" || period == "晚上") && hour < 12) {
                hour += 12
            } else if (period == "中午" && hour < 11) {
                hour += 12
            }

            if (hour in 0..23 && minute in 0..59) {
                targetTime = LocalTime.of(hour, minute)
                hasExplicitTime = true
                remaining = remaining.replace(timeMatcher.group(0) ?: "", "").trim()
            }
        }

        if (!hasExplicitTime && targetDate == LocalDate.now()) {
            // 默认设置为下一个整点
            targetTime = LocalTime.now().plusHours(1).withMinute(0).withSecond(0)
        }

        val start = LocalDateTime.of(targetDate, targetTime)
        val end = start.plusMinutes(durationMinutes.toLong())
        val cleanTitle = remaining.ifBlank { "日程时间块" }

        val summary = "将在 ${start.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))} - ${end.format(DateTimeFormatter.ofPattern("HH:mm"))} (${durationMinutes}分钟) 开始"

        return ParsedScheduleResult(
            title = cleanTitle,
            startTime = start,
            endTime = end,
            durationMinutes = durationMinutes,
            priority = priority,
            category = category,
            tag = tag,
            previewSummary = summary
        )
    }
}
