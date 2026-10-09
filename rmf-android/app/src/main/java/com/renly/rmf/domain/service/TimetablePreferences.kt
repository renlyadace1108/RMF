package com.renly.rmf.domain.service

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object TimetablePreferences {
    private const val PREFS_NAME = "rmf_timetable_prefs"
    private const val KEY_SEMESTER_START = "semester_start_date"
    private const val KEY_TOTAL_WEEKS = "total_weeks"
    private const val KEY_COURSE_REMINDER_ENABLED = "course_reminder_enabled"
    private const val KEY_REMINDER_ADVANCE_MINUTES = "reminder_advance_minutes"

    fun getSemesterStartDate(context: Context): LocalDate {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dateStr = prefs.getString(KEY_SEMESTER_START, null)
        return if (!dateStr.isNullOrBlank()) {
            try {
                LocalDate.parse(dateStr)
            } catch (e: Exception) {
                defaultSemesterStart()
            }
        } else {
            defaultSemesterStart()
        }
    }

    fun setSemesterStartDate(context: Context, date: LocalDate) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SEMESTER_START, date.toString()).apply()
    }

    fun getTotalWeeks(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_TOTAL_WEEKS, 16)
    }

    fun setTotalWeeks(context: Context, weeks: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_TOTAL_WEEKS, weeks).apply()
    }

    fun isCourseReminderEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_COURSE_REMINDER_ENABLED, true) // 默认开启
    }

    fun setCourseReminderEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_COURSE_REMINDER_ENABLED, enabled).apply()
    }

    fun getReminderAdvanceMinutes(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_REMINDER_ADVANCE_MINUTES, 15) // 默认提前15分钟
    }

    fun setReminderAdvanceMinutes(context: Context, minutes: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_REMINDER_ADVANCE_MINUTES, minutes).apply()
    }

    private fun defaultSemesterStart(): LocalDate {
        val today = LocalDate.now()
        return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
}
