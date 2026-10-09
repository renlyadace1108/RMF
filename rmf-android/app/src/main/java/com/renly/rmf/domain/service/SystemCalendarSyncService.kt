package com.renly.rmf.domain.service

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.renly.rmf.data.local.entity.ScheduleEntity
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.TimeZone

/**
 * 手机系统原生日历同步引擎 (System Calendar Sync Engine)
 *
 * 支持将 RMF 中的全部日程与时间块双向/单向导出同步到 Android 系统级日历
 * （深度适配小米 HyperOS 日历、OPPO ColorOS 日历、vivo OriginOS 日历、华为 HarmonyOS 日历与三星 OneUI 日历）。
 * 支持写入日程标题、开始结束时间、验收标准(DoD)、以及专属闹钟提醒 (CalendarContract.Reminders)。
 */
object SystemCalendarSyncService {

    private const val PREF_NAME = "rmf_system_calendar_pref"
    private const val KEY_AUTO_SYNC = "auto_sync_system_calendar"
    private const val KEY_LAST_SYNC_TIME = "last_system_calendar_sync_time"

    fun hasCalendarPermissions(context: Context): Boolean {
        val readGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        return readGranted && writeGranted
    }

    fun isAutoSyncEnabled(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_AUTO_SYNC, false)
    }

    fun setAutoSyncEnabled(context: Context, enabled: Boolean) {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sp.edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
    }

    fun getLastSyncTime(context: Context): String {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sp.getString(KEY_LAST_SYNC_TIME, "从未同步") ?: "从未同步"
    }

    private fun updateLastSyncTime(context: Context) {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        sp.edit().putString(KEY_LAST_SYNC_TIME, nowStr).apply()
    }

    /**
     * 获取系统可用的主日历或第一个可见日历 ID
     */
    fun getAvailableCalendarId(context: Context): Long? {
        if (!hasCalendarPermissions(context)) return null
        val cr = context.contentResolver
        val uri = CalendarContract.Calendars.CONTENT_URI
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.VISIBLE
        )
        var primaryId: Long? = null
        var firstId: Long? = null

        try {
            cr.query(uri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                val primaryCol = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
                val visibleCol = cursor.getColumnIndex(CalendarContract.Calendars.VISIBLE)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    if (firstId == null) firstId = id
                    val isPrimary = if (primaryCol != -1) cursor.getInt(primaryCol) == 1 else false
                    val isVisible = if (visibleCol != -1) cursor.getInt(visibleCol) == 1 else true
                    if (isPrimary) {
                        return id
                    }
                    if (isVisible && primaryId == null) {
                        primaryId = id
                    }
                }
            }
        } catch (_: Exception) {}

        if (primaryId != null) return primaryId
        if (firstId != null) return firstId

        // 若当前手机尚无默认日历账户，尝试自建本地日历账户
        return tryCreateLocalCalendar(context)
    }

    private fun tryCreateLocalCalendar(context: Context): Long? {
        return try {
            val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "RMF_ACCOUNT")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                .build()
            val values = ContentValues().apply {
                put(CalendarContract.Calendars.ACCOUNT_NAME, "RMF_ACCOUNT")
                put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                put(CalendarContract.Calendars.NAME, "RMF_SCHEDULE_CALENDAR")
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "RMF 智能日程")
                put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF00F0FF.toInt())
                put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
                put(CalendarContract.Calendars.OWNER_ACCOUNT, "rmf@local")
                put(CalendarContract.Calendars.VISIBLE, 1)
                put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
            }
            val resultUri = context.contentResolver.insert(uri, values)
            resultUri?.lastPathSegment?.toLongOrNull()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 查找系统中属于指定 scheduleId 的日历事件 ID
     */
    fun findEventIdForSchedule(context: Context, calendarId: Long, scheduleId: String): Long? {
        val cr = context.contentResolver
        val uri = CalendarContract.Events.CONTENT_URI
        val projection = arrayOf(CalendarContract.Events._ID)
        val selection = "(${CalendarContract.Events.CALENDAR_ID} = ?) AND (${CalendarContract.Events.SYNC_DATA1} = ? OR ${CalendarContract.Events.DESCRIPTION} LIKE ?)"
        val selectionArgs = arrayOf(calendarId.toString(), scheduleId, "%[RMF_ID:$scheduleId]%")
        try {
            cr.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events._ID))
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * 将单条日程同步/更新至系统日历
     */
    fun syncSingleSchedule(context: Context, schedule: ScheduleEntity): Boolean {
        if (!hasCalendarPermissions(context)) return false
        if (schedule.startTime.isBlank() || schedule.endTime.isBlank()) return false
        if (schedule.isDeleted == 1 || schedule.status == "ABANDONED" || schedule.status == "BACKLOG") {
            deleteScheduleEvent(context, schedule.id)
            return true
        }

        val calendarId = getAvailableCalendarId(context) ?: return false

        return try {
            val startEpoch = parseIsoToEpochMillis(schedule.startTime)
            val endEpoch = parseIsoToEpochMillis(schedule.endTime)
            if (startEpoch <= 0 || endEpoch <= 0) return false

            val finalEndEpoch = if (endEpoch <= startEpoch) startEpoch + (schedule.estimatedMinutes * 60 * 1000L) else endEpoch

            val cr = context.contentResolver
            val existingEventId = findEventIdForSchedule(context, calendarId, schedule.id)

            val cleanDesc = buildString {
                if (schedule.category.isNotBlank()) append("🏷️ 分类: ").append(schedule.category).append("\n")
                if (schedule.description.isNotBlank()) append("📝 描述: ").append(schedule.description).append("\n")
                if (schedule.dod.isNotBlank()) append("✅ 验收标准(DoD): ").append(schedule.dod).append("\n")
                append("\n[RMF_ID:${schedule.id}]")
            }

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, schedule.title)
                put(CalendarContract.Events.DESCRIPTION, cleanDesc)
                put(CalendarContract.Events.DTSTART, startEpoch)
                put(CalendarContract.Events.DTEND, finalEndEpoch)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.SYNC_DATA1, schedule.id)
                put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
                put(CalendarContract.Events.HAS_ALARM, if (schedule.reminderMinutes >= 0) 1 else 0)
            }

            val eventId: Long = if (existingEventId != null) {
                val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingEventId)
                cr.update(eventUri, values, null, null)
                existingEventId
            } else {
                val insertUri = cr.insert(CalendarContract.Events.CONTENT_URI, values) ?: return false
                insertUri.lastPathSegment?.toLongOrNull() ?: return false
            }

            // 更新系统闹钟提醒 (CalendarContract.Reminders)
            val remUri = CalendarContract.Reminders.CONTENT_URI
            cr.delete(remUri, "${CalendarContract.Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()))
            if (schedule.reminderMinutes >= 0) {
                val remValues = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, schedule.reminderMinutes)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                cr.insert(remUri, remValues)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 从系统日历中删除指定日程事件
     */
    fun deleteScheduleEvent(context: Context, scheduleId: String): Boolean {
        if (!hasCalendarPermissions(context)) return false
        val calendarId = getAvailableCalendarId(context) ?: return false
        return try {
            val existingEventId = findEventIdForSchedule(context, calendarId, scheduleId)
            if (existingEventId != null) {
                val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingEventId)
                context.contentResolver.delete(eventUri, null, null) > 0
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 全量同步所有未删除的有效日程到系统日历
     */
    fun syncAllSchedules(context: Context, schedules: List<ScheduleEntity>): CalendarSyncResult {
        if (!hasCalendarPermissions(context)) {
            return CalendarSyncResult.Error("未获取系统日历读写权限，请在权限申请弹窗中允许")
        }
        val calendarId = getAvailableCalendarId(context)
            ?: return CalendarSyncResult.Error("未检测到手机系统可用日历，请确认手机自带日历已开启")

        val validSchedules = schedules.filter {
            it.isDeleted == 0 &&
            it.status != "BACKLOG" &&
            it.status != "ABANDONED" &&
            it.startTime.isNotBlank() &&
            it.endTime.isNotBlank()
        }

        if (validSchedules.isEmpty()) {
            return CalendarSyncResult.Success("暂无可同步的有效排期日程", 0)
        }

        var synced = 0
        for (item in validSchedules) {
            if (syncSingleSchedule(context, item)) {
                synced++
            }
        }

        updateLastSyncTime(context)
        return CalendarSyncResult.Success("已成功同步 $synced 项日程至手机系统日历！", synced)
    }

    private fun parseIsoToEpochMillis(isoStr: String): Long {
        return try {
            val ldt = try {
                LocalDateTime.parse(isoStr, DateTimeFormatter.ISO_DATE_TIME)
            } catch (_: Exception) {
                LocalDateTime.parse(isoStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            }
            ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }
}
