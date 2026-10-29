package com.renly.rmf.util

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object TimeUtils {
    // 采用与 Windows 端 (DateTime.UtcNow.ToString("s")) 完全一致的 "yyyy-MM-dd'T'HH:mm:ss" UTC 规范格式
    // 确保 SQLite 在根据 updated_at 字符串进行字典序对比 (Last-Write-Wins) 时两端处于同一时区基准线，杜绝 8 小时本地时差导致的同步丢失与覆盖
    private val UTC_ISO_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    fun currentUtcIsoString(): String {
        return ZonedDateTime.now(ZoneOffset.UTC).format(UTC_ISO_FORMATTER)
    }

    /**
     * 高性能无异常解析本地 ISO 日期时间：
     * 智能分支规避异常抛出与堆栈抓取开销，提升批量循环与列表渲染性能
     */
    fun parseLocalDateTimeSafe(isoStr: String): LocalDateTime? {
        if (isoStr.isBlank()) return null
        return try {
            if (isoStr.endsWith("Z") || isoStr.contains("+") || (isoStr.indexOf('-', 10) > 0)) {
                try {
                    LocalDateTime.parse(isoStr, DateTimeFormatter.ISO_DATE_TIME)
                } catch (_: Exception) {
                    LocalDateTime.parse(isoStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                }
            } else {
                try {
                    LocalDateTime.parse(isoStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                } catch (_: Exception) {
                    LocalDateTime.parse(isoStr, DateTimeFormatter.ISO_DATE_TIME)
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
