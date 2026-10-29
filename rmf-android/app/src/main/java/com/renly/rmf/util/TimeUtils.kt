package com.renly.rmf.util

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
}
