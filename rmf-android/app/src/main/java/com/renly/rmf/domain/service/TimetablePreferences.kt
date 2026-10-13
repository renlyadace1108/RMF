package com.renly.rmf.domain.service

import android.content.Context
import java.time.LocalDate
import java.time.YearMonth

object TimetablePreferences {
    private const val PREFS_NAME = "rmf_timetable_prefs"

    // 首次进入课表是否已完成双学期强制配置
    private const val KEY_DUAL_SEMESTER_CONFIGURED = "dual_semester_configured_v1"

    // 当前启用学期: 1 = 第一学期, 2 = 第二学期
    private const val KEY_ACTIVE_SEMESTER = "active_semester_index"

    // 第一学期 (默认 9月1日, 20周)
    private const val KEY_TERM1_MONTH = "term1_start_month"
    private const val KEY_TERM1_DAY = "term1_start_day"
    private const val KEY_TERM1_WEEKS = "term1_total_weeks"

    // 第二学期 (默认 2月24日, 20周)
    private const val KEY_TERM2_MONTH = "term2_start_month"
    private const val KEY_TERM2_DAY = "term2_start_day"
    private const val KEY_TERM2_WEEKS = "term2_total_weeks"

    // 兼容旧版键值与提醒配置
    private const val KEY_SEMESTER_START = "semester_start_date"
    private const val KEY_TOTAL_WEEKS = "total_weeks"
    private const val KEY_COURSE_REMINDER_ENABLED = "course_reminder_enabled"
    private const val KEY_REMINDER_ADVANCE_MINUTES = "reminder_advance_minutes"

    fun isSemesterConfigured(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DUAL_SEMESTER_CONFIGURED, false)
    }

    fun setSemesterConfigured(context: Context, configured: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DUAL_SEMESTER_CONFIGURED, configured).apply()
    }

    fun getActiveSemester(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_ACTIVE_SEMESTER)) {
            return prefs.getInt(KEY_ACTIVE_SEMESTER, 1).coerceIn(1, 2)
        }
        // 未手动指定时，按当前月份自动推断：2~7月默认第二学期，其余月份默认第一学期
        val m = LocalDate.now().monthValue
        return if (m in 2..7) 2 else 1
    }

    fun setActiveSemester(context: Context, semester: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_ACTIVE_SEMESTER, semester.coerceIn(1, 2)).apply()
    }

    fun getTerm1StartMonth(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_TERM1_MONTH, 9).coerceIn(1, 12)
    }

    fun getTerm1StartDay(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_TERM1_DAY, 1).coerceIn(1, 31)
    }

    fun getTerm1Weeks(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_TERM1_WEEKS, 20).coerceIn(1, 30)
    }

    fun getTerm2StartMonth(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_TERM2_MONTH, 2).coerceIn(1, 12)
    }

    fun getTerm2StartDay(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_TERM2_DAY, 24).coerceIn(1, 31)
    }

    fun getTerm2Weeks(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_TERM2_WEEKS, 20).coerceIn(1, 30)
    }

    /**
     * 保存双学期（无年份）开学月日、学期周数与当前启用学期，并标记已完成首次强制配置
     */
    fun saveDualSemesterConfig(
        context: Context,
        activeSemester: Int,
        term1Month: Int,
        term1Day: Int,
        term1Weeks: Int,
        term2Month: Int,
        term2Day: Int,
        term2Weeks: Int
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val safeActive = activeSemester.coerceIn(1, 2)
        val t1M = term1Month.coerceIn(1, 12)
        val t1D = term1Day.coerceIn(1, 31)
        val t1W = term1Weeks.coerceIn(1, 30)
        val t2M = term2Month.coerceIn(1, 12)
        val t2D = term2Day.coerceIn(1, 31)
        val t2W = term2Weeks.coerceIn(1, 30)

        val resolvedDate = if (safeActive == 1) {
            resolveStartDateWithoutYear(t1M, t1D, t1W)
        } else {
            resolveStartDateWithoutYear(t2M, t2D, t2W)
        }
        val resolvedWeeks = if (safeActive == 1) t1W else t2W

        prefs.edit()
            .putBoolean(KEY_DUAL_SEMESTER_CONFIGURED, true)
            .putInt(KEY_ACTIVE_SEMESTER, safeActive)
            .putInt(KEY_TERM1_MONTH, t1M)
            .putInt(KEY_TERM1_DAY, t1D)
            .putInt(KEY_TERM1_WEEKS, t1W)
            .putInt(KEY_TERM2_MONTH, t2M)
            .putInt(KEY_TERM2_DAY, t2D)
            .putInt(KEY_TERM2_WEEKS, t2W)
            .putString(KEY_SEMESTER_START, resolvedDate.toString())
            .putInt(KEY_TOTAL_WEEKS, resolvedWeeks)
            .apply()
    }

    /**
     * 无需用户输入“年份”，根据开学（月、日）与学期周数自动推算对应的学年日期（支持跨年学期如9月~次年1月）
     */
    fun resolveStartDateWithoutYear(
        month: Int,
        day: Int,
        totalWeeks: Int,
        referenceDate: LocalDate = LocalDate.now()
    ): LocalDate {
        val safeMonth = month.coerceIn(1, 12)
        for (yearOffset in listOf(0, -1, 1)) {
            val candidateYear = referenceDate.year + yearOffset
            val maxDay = YearMonth.of(candidateYear, safeMonth).lengthOfMonth()
            val candidateDate = LocalDate.of(candidateYear, safeMonth, day.coerceIn(1, maxDay))
            val alignedMonday = candidateDate.minusDays((candidateDate.dayOfWeek.value - 1).toLong())
            val semesterEnd = alignedMonday.plusWeeks(totalWeeks.toLong()).plusDays(6)
            if (!referenceDate.isBefore(alignedMonday.minusWeeks(2)) && !referenceDate.isAfter(semesterEnd)) {
                return candidateDate
            }
        }
        val maxDay = YearMonth.of(referenceDate.year, safeMonth).lengthOfMonth()
        return LocalDate.of(referenceDate.year, safeMonth, day.coerceIn(1, maxDay))
    }

    fun getSemesterStartDate(context: Context): LocalDate {
        val active = getActiveSemester(context)
        return if (active == 1) {
            resolveStartDateWithoutYear(
                month = getTerm1StartMonth(context),
                day = getTerm1StartDay(context),
                totalWeeks = getTerm1Weeks(context)
            )
        } else {
            resolveStartDateWithoutYear(
                month = getTerm2StartMonth(context),
                day = getTerm2StartDay(context),
                totalWeeks = getTerm2Weeks(context)
            )
        }
    }

    fun setSemesterStartDate(context: Context, date: LocalDate) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SEMESTER_START, date.toString()).apply()
    }

    fun getTotalWeeks(context: Context): Int {
        val active = getActiveSemester(context)
        return if (active == 1) getTerm1Weeks(context) else getTerm2Weeks(context)
    }

    fun getSemesterTotalWeeks(context: Context): Int = getTotalWeeks(context)

    fun setTotalWeeks(context: Context, weeks: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val active = getActiveSemester(context)
        val editor = prefs.edit().putInt(KEY_TOTAL_WEEKS, weeks)
        if (active == 1) {
            editor.putInt(KEY_TERM1_WEEKS, weeks)
        } else {
            editor.putInt(KEY_TERM2_WEEKS, weeks)
        }
        editor.apply()
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
}
