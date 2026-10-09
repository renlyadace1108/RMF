package com.renly.rmf.domain.service

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.renly.rmf.data.local.RmfDatabase
import com.renly.rmf.data.local.entity.ScheduleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

sealed class CalendarSyncResult {
    data class Success(val message: String, val syncedCount: Int) : CalendarSyncResult()
    data class Error(val error: String) : CalendarSyncResult()
}

class GoogleCalendarSyncService(
    private val context: Context,
    private val database: RmfDatabase
) {
    companion object {
        private const val CALENDAR_EVENTS_API = "https://www.googleapis.com/calendar/v3/calendars/primary/events"
        private const val CALENDAR_IMPORT_API = "https://www.googleapis.com/calendar/v3/calendars/primary/events/import"

        /**
         * 格式化 ISO-8601 本地时间为 Google Calendar RFC 3339 偏移时间格式
         * 例如: "2026-10-08T09:00:00" -> "2026-10-08T09:00:00+08:00"
         */
        fun formatToGoogleDateTime(isoDateTimeStr: String): String {
            return try {
                val ldt = try {
                    LocalDateTime.parse(isoDateTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                } catch (_: Exception) {
                    LocalDateTime.parse(isoDateTimeStr, DateTimeFormatter.ISO_DATE_TIME)
                }
                val zdt = ldt.atZone(ZoneId.systemDefault())
                zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            } catch (_: Exception) {
                isoDateTimeStr
            }
        }

        /**
         * 格式化 ISO-8601 本地时间为 RFC 5545 iCalendar UTC 格式
         * 例如: "2026-10-08T09:00:00" -> "20261008T010000Z"
         */
        fun formatToIcsUtcDateTime(isoDateTimeStr: String): String {
            return try {
                val ldt = try {
                    LocalDateTime.parse(isoDateTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                } catch (_: Exception) {
                    LocalDateTime.parse(isoDateTimeStr, DateTimeFormatter.ISO_DATE_TIME)
                }
                val zdt = ldt.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC)
                zdt.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.US))
            } catch (_: Exception) {
                ""
            }
        }

        /**
         * 解析 ICS 日期字符串 (兼容 UTC、本地浮动及全天日期)
         */
        fun parseIcsDateTime(valueStr: String): LocalDateTime {
            val v = valueStr.trim()
            try {
                if (v.endsWith("Z", ignoreCase = true)) {
                    val dtf = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.US)
                    val ldt = LocalDateTime.parse(v, dtf)
                    val utcZdt = ldt.atZone(ZoneOffset.UTC)
                    return utcZdt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                }
                if (v.contains("T")) {
                    val dtf = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.US)
                    return LocalDateTime.parse(v, dtf)
                }
                if (v.length == 8) {
                    val dtf = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US)
                    return java.time.LocalDate.parse(v, dtf).atTime(9, 0)
                }
                return LocalDateTime.parse(v)
            } catch (_: Exception) {
                return LocalDateTime.now().withMinute(0).withSecond(0)
            }
        }
    }

    /**
     * 将本地所有有效日程双向/推送同步至 Google Calendar API (primary)
     */
    suspend fun pushSchedulesToGoogleCalendar(): CalendarSyncResult = withContext(Dispatchers.IO) {
        val driveService = GoogleDriveSyncService(context, database)
        val tokenRes = driveService.getValidAccessToken()
        if (tokenRes.isFailure) {
            return@withContext CalendarSyncResult.Error("未获取到有效的 Google 授权: ${tokenRes.exceptionOrNull()?.message}")
        }
        val accessToken = tokenRes.getOrThrow()

        val activeSchedules = try {
            database.scheduleDao().getActiveSchedulesList()
        } catch (e: Exception) {
            return@withContext CalendarSyncResult.Error("读取本地日程失败: ${e.message}")
        }

        if (activeSchedules.isEmpty()) {
            return@withContext CalendarSyncResult.Success("本地无待同步日程", 0)
        }

        var successCount = 0
        var failCount = 0
        val zoneIdStr = ZoneId.systemDefault().id

        for (schedule in activeSchedules) {
            try {
                // 构造 Google Calendar 兼容的 Event ID: base32hex/hex (5-1024 字符，小写字母与数字)
                val eventId = "rmf" + schedule.id.replace("-", "").lowercase(Locale.US)

                val startFormatted = formatToGoogleDateTime(schedule.startTime)
                val endFormatted = formatToGoogleDateTime(schedule.endTime)

                val eventObj = JsonObject().apply {
                    addProperty("id", eventId)
                    addProperty("summary", schedule.title)
                    val desc = buildString {
                        append("【RMF 日程】\n")
                        append("分类: ${schedule.category} | 优先级: ${schedule.priority} | 状态: ${schedule.status}\n")
                        if (schedule.description.isNotBlank()) {
                            append("详情: ${schedule.description}\n")
                        }
                        if (schedule.dod.isNotBlank()) {
                            append("完成定义 (DoD): ${schedule.dod}\n")
                        }
                    }
                    addProperty("description", desc)

                    val startObj = JsonObject().apply {
                        addProperty("dateTime", startFormatted)
                        addProperty("timeZone", zoneIdStr)
                    }
                    add("start", startObj)

                    val endObj = JsonObject().apply {
                        addProperty("dateTime", endFormatted)
                        addProperty("timeZone", zoneIdStr)
                    }
                    add("end", endObj)

                    val remindersObj = JsonObject().apply {
                        addProperty("useDefault", false)
                        val overrides = JsonArray().apply {
                            val pop10 = JsonObject().apply {
                                addProperty("method", "popup")
                                addProperty("minutes", 10)
                            }
                            add(pop10)
                        }
                        add("overrides", overrides)
                    }
                    add("reminders", remindersObj)
                }

                val jsonBody = eventObj.toString()

                // 先尝试 PUT 更新现有事件
                val putUrl = URL("$CALENDAR_EVENTS_API/$eventId")
                val putConn = putUrl.openConnection() as HttpURLConnection
                putConn.requestMethod = "PUT"
                putConn.doOutput = true
                putConn.setRequestProperty("Authorization", "Bearer $accessToken")
                putConn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

                OutputStreamWriter(putConn.outputStream, Charsets.UTF_8).use { it.write(jsonBody) }
                val putCode = putConn.responseCode

                if (putCode in 200..299) {
                    successCount++
                } else if (putCode == 404) {
                    // 事件不存在，使用 POST 插入
                    val postUrl = URL(CALENDAR_IMPORT_API)
                    val postConn = postUrl.openConnection() as HttpURLConnection
                    postConn.requestMethod = "POST"
                    postConn.doOutput = true
                    postConn.setRequestProperty("Authorization", "Bearer $accessToken")
                    postConn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

                    OutputStreamWriter(postConn.outputStream, Charsets.UTF_8).use { it.write(jsonBody) }
                    val postCode = postConn.responseCode
                    if (postCode in 200..299) {
                        successCount++
                    } else {
                        // 备用普通 POST
                        val normalPostUrl = URL(CALENDAR_EVENTS_API)
                        val normalConn = normalPostUrl.openConnection() as HttpURLConnection
                        normalConn.requestMethod = "POST"
                        normalConn.doOutput = true
                        normalConn.setRequestProperty("Authorization", "Bearer $accessToken")
                        normalConn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                        OutputStreamWriter(normalConn.outputStream, Charsets.UTF_8).use { it.write(jsonBody) }
                        if (normalConn.responseCode in 200..299) {
                            successCount++
                        } else {
                            failCount++
                        }
                    }
                } else {
                    failCount++
                }
            } catch (_: Exception) {
                failCount++
            }
        }

        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        GoogleDrivePreferences.setCalendarLastSyncTime(context, nowStr)

        if (failCount > 0 && successCount == 0) {
            CalendarSyncResult.Error("推送到 Google Calendar 失败，请检查网络或授权权限")
        } else {
            CalendarSyncResult.Success("已成功同步 $successCount 条日程至 Google Calendar" + if (failCount > 0) " (失败 $failCount 条)" else "", successCount)
        }
    }

    /**
     * 从 Google Calendar 拉取事件并合并入本地 SQLite
     */
    suspend fun pullEventsFromGoogleCalendar(daysAhead: Int = 30): CalendarSyncResult = withContext(Dispatchers.IO) {
        val driveService = GoogleDriveSyncService(context, database)
        val tokenRes = driveService.getValidAccessToken()
        if (tokenRes.isFailure) {
            return@withContext CalendarSyncResult.Error("未获取到有效的 Google 授权: ${tokenRes.exceptionOrNull()?.message}")
        }
        val accessToken = tokenRes.getOrThrow()

        val timeMin = ZonedDateTime.now().minusDays(7).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val timeMax = ZonedDateTime.now().plusDays(daysAhead.toLong()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        val queryParams = "timeMin=" + URLEncoder.encode(timeMin, "UTF-8") +
                "&timeMax=" + URLEncoder.encode(timeMax, "UTF-8") +
                "&singleEvents=true" +
                "&maxResults=250"

        val getUrl = URL("$CALENDAR_EVENTS_API?$queryParams")
        val conn = getUrl.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $accessToken")

        val respCode = conn.responseCode
        if (respCode !in 200..299) {
            val error = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream)).use { it.readText() }
            return@withContext CalendarSyncResult.Error("从 Google Calendar 拉取失败 ($respCode): $error")
        }

        val jsonStr = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
        val rootObj = JsonParser.parseString(jsonStr).asJsonObject
        val items = rootObj.getAsJsonArray("items") ?: JsonArray()

        var importedCount = 0
        val nowIso = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)

        for (elem in items) {
            try {
                val item = elem.asJsonObject
                val status = item.get("status")?.asString ?: "confirmed"
                if (status.equals("cancelled", ignoreCase = true)) continue

                val summary = item.get("summary")?.asString ?: "Google日历日程"
                val description = item.get("description")?.asString ?: ""
                val gId = item.get("id")?.asString ?: UUID.randomUUID().toString()

                val startObj = item.getAsJsonObject("start")
                val endObj = item.getAsJsonObject("end")

                val startStr = startObj?.get("dateTime")?.asString
                    ?: startObj?.get("date")?.asString?.let { "${it}T09:00:00" }
                    ?: continue

                val endStr = endObj?.get("dateTime")?.asString
                    ?: endObj?.get("date")?.asString?.let { "${it}T10:00:00" }
                    ?: continue

                val startLdt = try {
                    ZonedDateTime.parse(startStr).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                } catch (_: Exception) {
                    LocalDateTime.parse(startStr)
                }

                val endLdt = try {
                    ZonedDateTime.parse(endStr).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                } catch (_: Exception) {
                    LocalDateTime.parse(endStr)
                }

                val durationMinutes = java.time.Duration.between(startLdt, endLdt).toMinutes().toInt().coerceAtLeast(15)

                val schedEntity = ScheduleEntity(
                    id = if (gId.startsWith("rmf")) {
                        // 还原或保留
                        gId
                    } else {
                        "gcal_$gId"
                    },
                    title = summary,
                    description = description,
                    category = "WORK",
                    priority = "MEDIUM",
                    status = "PENDING",
                    startTime = startLdt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    endTime = endLdt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    estimatedMinutes = durationMinutes,
                    actualMinutes = 0,
                    workType = "DEEP_WORK",
                    dod = "",
                    energyDelta = 0,
                    goalId = null,
                    studyTopicId = null,
                    colorHex = "#4285F4", // Google 品牌蓝色
                    isDeleted = 0,
                    createdAt = nowIso,
                    updatedAt = nowIso
                )

                database.scheduleDao().insertOrUpdate(schedEntity)
                importedCount++
            } catch (_: Exception) { }
        }

        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        GoogleDrivePreferences.setCalendarLastSyncTime(context, nowStr)

        CalendarSyncResult.Success("已从 Google Calendar 成功导入 $importedCount 条日程", importedCount)
    }

    /**
     * 从 Google Calendar 的 iCal/ICS 订阅地址同步日程 (兼容 Windows GoogleCalendarService)
     */
    suspend fun syncFromIcsUrl(icsUrl: String): CalendarSyncResult = withContext(Dispatchers.IO) {
        if (icsUrl.isBlank()) {
            return@withContext CalendarSyncResult.Error("iCal 订阅地址不能为空")
        }

        try {
            val url = URL(icsUrl.trim())
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            if (conn.responseCode !in 200..299) {
                return@withContext CalendarSyncResult.Error("下载日历订阅失败 (${conn.responseCode})")
            }

            val icsContent = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            val count = parseAndImportIcs(icsContent)

            GoogleDrivePreferences.setCalendarIcsUrl(context, icsUrl.trim())
            val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            GoogleDrivePreferences.setCalendarLastSyncTime(context, nowStr)

            CalendarSyncResult.Success("已从日历订阅导入 $count 条日程", count)
        } catch (e: Exception) {
            CalendarSyncResult.Error("解析日历订阅出错: ${e.message}")
        }
    }

    /**
     * 解析 RFC 5545 iCalendar 内容并写入数据库
     */
    suspend fun parseAndImportIcs(icsContent: String): Int = withContext(Dispatchers.IO) {
        if (icsContent.isBlank()) return@withContext 0

        // 展开折叠行 (依据 RFC 5545)
        val unfolded = icsContent.replace(Regex("\\r?\\n[ \\t]"), "")
        val lines = unfolded.lines()

        var count = 0
        var inEvent = false
        var uid = ""
        var summary = "未命名日程"
        var description = ""
        var startTime: LocalDateTime? = null
        var endTime: LocalDateTime? = null
        val nowIso = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.equals("BEGIN:VEVENT", ignoreCase = true)) {
                inEvent = true
                uid = UUID.randomUUID().toString()
                summary = "未命名日程"
                description = ""
                startTime = null
                endTime = null
                continue
            }

            if (line.equals("END:VEVENT", ignoreCase = true)) {
                if (inEvent && startTime != null) {
                    val actualEnd = endTime ?: startTime.plusHours(1)
                    val dur = java.time.Duration.between(startTime, actualEnd).toMinutes().toInt().coerceAtLeast(15)

                    val entity = ScheduleEntity(
                        id = if (uid.isNotBlank()) "ics_$uid" else UUID.randomUUID().toString(),
                        title = summary,
                        description = description,
                        category = "WORK",
                        priority = "MEDIUM",
                        status = "PENDING",
                        startTime = startTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        endTime = actualEnd.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        estimatedMinutes = dur,
                        actualMinutes = 0,
                        workType = "DEEP_WORK",
                        dod = "",
                        energyDelta = 0,
                        goalId = null,
                        studyTopicId = null,
                        colorHex = "#4285F4",
                        isDeleted = 0,
                        createdAt = nowIso,
                        updatedAt = nowIso
                    )
                    database.scheduleDao().insertOrUpdate(entity)
                    count++
                }
                inEvent = false
                continue
            }

            if (!inEvent) continue

            val colonIdx = line.indexOf(':')
            if (colonIdx <= 0) continue

            val keyPart = line.substring(0, colonIdx)
            val valuePart = line.substring(colonIdx + 1)
            val semiIdx = keyPart.indexOf(';')
            val propName = (if (semiIdx > 0) keyPart.substring(0, semiIdx) else keyPart).trim().uppercase(Locale.US)

            when (propName) {
                "UID" -> uid = valuePart.trim()
                "SUMMARY" -> summary = cleanIcsText(valuePart)
                "DESCRIPTION" -> description = cleanIcsText(valuePart)
                "DTSTART" -> startTime = parseIcsDateTime(valuePart)
                "DTEND" -> endTime = parseIcsDateTime(valuePart)
            }
        }
        count
    }

    /**
     * 将本地全部有效日程导出为 RFC 5545 .ics 标准日历文件
     */
    suspend fun exportSchedulesToIcsFile(includeCourses: Boolean = true): Result<File> = withContext(Dispatchers.IO) {
        try {
            val schedules = database.scheduleDao().getActiveSchedulesList()
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val icsFile = File(exportDir, "rmf_schedules_courses_${System.currentTimeMillis()}.ics")

            val sb = StringBuilder()
            sb.append("BEGIN:VCALENDAR\r\n")
            sb.append("VERSION:2.0\r\n")
            sb.append("PRODID:-//Renly Management Platform//RMF Android//CN\r\n")
            sb.append("CALSCALE:GREGORIAN\r\n")
            sb.append("METHOD:PUBLISH\r\n")
            sb.append("X-WR-CALNAME:RMF 日程与课表清单\r\n")
            sb.append("X-WR-TIMEZONE:${ZoneId.systemDefault().id}\r\n")

            val nowUtc = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.US))

            for (s in schedules) {
                val dtStart = formatToIcsUtcDateTime(s.startTime)
                val dtEnd = formatToIcsUtcDateTime(s.endTime)
                if (dtStart.isBlank() || dtEnd.isBlank()) continue

                sb.append("BEGIN:VEVENT\r\n")
                sb.append("UID:${s.id}@rmf.renly\r\n")
                sb.append("DTSTAMP:$nowUtc\r\n")
                sb.append("DTSTART:$dtStart\r\n")
                sb.append("DTEND:$dtEnd\r\n")
                sb.append("SUMMARY:${escapeIcsText(s.title)}\r\n")
                val desc = "分类: ${s.category} | 优先级: ${s.priority} | 状态: ${s.status}\n${s.description}"
                sb.append("DESCRIPTION:${escapeIcsText(desc)}\r\n")
                sb.append("STATUS:CONFIRMED\r\n")
                sb.append("END:VEVENT\r\n")
            }

            if (includeCourses) {
                try {
                    val semesterStart = TimetablePreferences.getSemesterStartDate(context)
                    val totalWeeks = TimetablePreferences.getTotalWeeks(context)
                    val projectionService = TimetableProjectionService(database.courseDao())

                    for (week in 1..totalWeeks) {
                        for (dayOffset in 0..6) {
                            val targetDate = semesterStart.plusWeeks((week - 1).toLong()).plusDays(dayOffset.toLong())
                            val projectedCourses = projectionService.getProjectedCoursesForDate(targetDate, semesterStart)
                            for (c in projectedCourses) {
                                val cStartIso = c.startTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                val cEndIso = c.endTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                val dtStart = formatToIcsUtcDateTime(cStartIso)
                                val dtEnd = formatToIcsUtcDateTime(cEndIso)
                                if (dtStart.isBlank() || dtEnd.isBlank()) continue

                                sb.append("BEGIN:VEVENT\r\n")
                                sb.append("UID:course_${c.courseId}_${targetDate}_${c.startTime.toLocalTime()}@rmf.renly\r\n")
                                sb.append("DTSTAMP:$nowUtc\r\n")
                                sb.append("DTSTART:$dtStart\r\n")
                                sb.append("DTEND:$dtEnd\r\n")
                                sb.append("SUMMARY:${escapeIcsText("🎓 " + c.name)}\r\n")
                                if (c.location.isNotBlank()) {
                                    sb.append("LOCATION:${escapeIcsText(c.location)}\r\n")
                                }
                                val cDesc = "任课教师: ${c.teacher}\n上课地点: ${c.location}\n学期第 ${week} 周"
                                sb.append("DESCRIPTION:${escapeIcsText(cDesc)}\r\n")
                                sb.append("STATUS:CONFIRMED\r\n")
                                sb.append("END:VEVENT\r\n")
                            }
                        }
                    }
                } catch (_: Exception) { }
            }

            sb.append("END:VCALENDAR\r\n")

            FileOutputStream(icsFile).use { fos ->
                fos.write(sb.toString().toByteArray(Charsets.UTF_8))
            }
            Result.success(icsFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 调用系统分享 / 日历打开 .ics 文件
     */
    fun shareIcsFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/calendar"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "导出日历至 Google Calendar 或系统日历")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (_: Exception) { }
    }

    private fun cleanIcsText(text: String): String {
        return text.replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\n", "\n")
            .replace("\\N", "\n")
            .replace("\\\\", "\\")
            .trim()
    }

    private fun escapeIcsText(text: String): String {
        return text.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\n", "\\n")
    }
}
