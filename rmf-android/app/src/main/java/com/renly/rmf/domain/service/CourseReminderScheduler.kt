package com.renly.rmf.domain.service

import android.content.Context
import com.renly.rmf.data.local.dao.CourseDao
import java.time.LocalDate

object CourseReminderScheduler {

    /**
     * 为今日或接下来几天（默认未来 7 天）的所有有效课程自动排期注册 ColorOS 流体云系统闹钟
     */
    suspend fun scheduleUpcomingCourseReminders(
        context: Context,
        courseDao: CourseDao,
        daysAhead: Int = 3
    ) {
        if (!TimetablePreferences.isCourseReminderEnabled(context)) {
            return
        }

        val semesterStart = TimetablePreferences.getSemesterStartDate(context)
        val advanceMinutes = TimetablePreferences.getReminderAdvanceMinutes(context)
        val projectionService = TimetableProjectionService(courseDao)
        val today = LocalDate.now()

        for (offset in 0 until daysAhead) {
            val targetDate = today.plusDays(offset.toLong())
            val projectedCourses = projectionService.getProjectedCoursesForDate(targetDate, semesterStart)

            for (course in projectedCourses) {
                // 生成唯一 key: courseId_YYYY-MM-DD_start
                val reminderKey = "course_${course.courseId}_${targetDate}_${course.startTime.toLocalTime()}"
                val durationMins = java.time.temporal.ChronoUnit.MINUTES.between(course.startTime, course.endTime).toInt()

                ScheduleReminderManager.scheduleCourseReminder(
                    context = context,
                    reminderKey = reminderKey,
                    courseName = course.name,
                    location = course.location,
                    teacher = course.teacher,
                    startTime = course.startTime,
                    durationMinutes = durationMins,
                    advanceMinutes = advanceMinutes
                )
            }
        }
    }
}
