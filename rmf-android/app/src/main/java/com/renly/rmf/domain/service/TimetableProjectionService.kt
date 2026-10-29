package com.renly.rmf.domain.service

import android.content.Context
import com.renly.rmf.data.local.dao.CourseDao
import com.renly.rmf.data.local.dao.ScheduleDao
import com.renly.rmf.data.local.entity.CourseAdjustmentEntity
import com.renly.rmf.data.local.entity.CourseEntity
import com.renly.rmf.data.local.entity.ScheduleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class ProjectedCourseSlot(
    val courseId: String,
    val name: String,
    val teacher: String,
    val location: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val colorHex: String,
    val isAdjusted: Boolean,
    val adjustmentReason: String = "",
    val reminderMinutes: Int = 15
)

class TimetableProjectionService(private val courseDao: CourseDao) {

    // 默认小节作息表 (支持在设置中自定义)
    private val defaultSectionTimes = mapOf(
        1 to Pair("08:00", "08:45"),
        2 to Pair("08:55", "09:40"),
        3 to Pair("10:00", "10:45"),
        4 to Pair("10:55", "11:40"),
        5 to Pair("14:00", "14:45"),
        6 to Pair("14:55", "15:40"),
        7 to Pair("16:00", "16:45"),
        8 to Pair("16:55", "17:40"),
        9 to Pair("19:00", "19:45"),
        10 to Pair("19:55", "20:40"),
        11 to Pair("20:50", "21:35"),
        12 to Pair("21:45", "22:30")
    )

    /**
     * 计算指定日期属于学期的第几周（统一以开学日期所在周的周一作为第1周起点）
     */
    fun calculateWeekNumber(date: LocalDate, semesterStartDate: LocalDate): Int {
        val alignedMonday = semesterStartDate.minusDays((semesterStartDate.dayOfWeek.value - 1).toLong())
        val days = ChronoUnit.DAYS.between(alignedMonday, date)
        if (days < 0) return 0
        return (days / 7).toInt() + 1
    }

    /**
     * 将指定日期的有效课程（结合单双周、小节起止时间、自由时间与临时调课微调）投影为时间流实体
     */
    suspend fun getProjectedCoursesForDate(
        targetDate: LocalDate,
        semesterStartDate: LocalDate
    ): List<ProjectedCourseSlot> {
        val weekNumber = calculateWeekNumber(targetDate, semesterStartDate)
        if (weekNumber <= 0) return emptyList()

        val allCourses = courseDao.getAllCoursesList()
        val adjustments = courseDao.getAdjustmentsForWeekList(weekNumber)
        return projectSlotsForDay(
            targetDate = targetDate,
            weekNumber = weekNumber,
            allCourses = allCourses,
            weekAdjustments = adjustments
        )
    }

    private fun projectSlotsForDay(
        targetDate: LocalDate,
        weekNumber: Int,
        allCourses: List<CourseEntity>,
        weekAdjustments: List<CourseAdjustmentEntity>
    ): List<ProjectedCourseSlot> {
        val dayOfWeekInt = targetDate.dayOfWeek.value // 1 = Monday, 7 = Sunday
        val isOddWeek = (weekNumber % 2 != 0)
        val result = mutableListOf<ProjectedCourseSlot>()

        // 1. 遍历基准课程
        for (course in allCourses) {
            if (weekNumber < course.startWeek || weekNumber > course.endWeek) continue
            if (course.weekType == "ODD" && !isOddWeek) continue
            if (course.weekType == "EVEN" && isOddWeek) continue

            val adj = weekAdjustments.find { it.courseId == course.id }
            if (adj != null) {
                if (adj.adjustmentType == "CANCEL") {
                    continue
                } else if (adj.adjustmentType == "RESCHEDULE") {
                    if (adj.newDayOfWeek == dayOfWeekInt) {
                        val times = calculateTimes(
                            targetDate,
                            adj.isCustomTime == 1,
                            adj.customStartTime,
                            adj.customEndTime,
                            adj.newStartSection,
                            adj.sectionSpan
                        )
                        result.add(
                            ProjectedCourseSlot(
                                courseId = course.id,
                                name = course.name,
                                teacher = course.teacher,
                                location = if (adj.newLocation.isNotBlank()) adj.newLocation else course.location,
                                startTime = times.first,
                                endTime = times.second,
                                colorHex = course.colorHex,
                                isAdjusted = true,
                                adjustmentReason = if (adj.reason.isNotBlank()) "已调课: ${adj.reason}" else "已调课",
                                reminderMinutes = course.reminderMinutes
                            )
                        )
                    }
                    continue
                }
            }

            if (course.dayOfWeek == dayOfWeekInt) {
                val times = calculateTimes(
                    targetDate,
                    course.isCustomTime == 1,
                    course.customStartTime,
                    course.customEndTime,
                    course.startSection,
                    course.sectionSpan
                )
                result.add(
                    ProjectedCourseSlot(
                        courseId = course.id,
                        name = course.name,
                        teacher = course.teacher,
                        location = course.location,
                        startTime = times.first,
                        endTime = times.second,
                        colorHex = course.colorHex,
                        isAdjusted = false,
                        reminderMinutes = course.reminderMinutes
                    )
                )
            }
        }

        // 2. 检查是否有针对当天的独立加课 (ADD)
        val addedCourses = weekAdjustments.filter { it.adjustmentType == "ADD" && it.newDayOfWeek == dayOfWeekInt }
        for (addAdj in addedCourses) {
            val times = calculateTimes(
                targetDate,
                addAdj.isCustomTime == 1,
                addAdj.customStartTime,
                addAdj.customEndTime,
                addAdj.newStartSection,
                addAdj.sectionSpan
            )
            result.add(
                ProjectedCourseSlot(
                    courseId = addAdj.id,
                    name = addAdj.courseName,
                    teacher = "",
                    location = addAdj.newLocation,
                    startTime = times.first,
                    endTime = times.second,
                    colorHex = "#10B981",
                    isAdjusted = true,
                    adjustmentReason = if (addAdj.reason.isNotBlank()) "临时加课: ${addAdj.reason}" else "临时加课"
                )
            )
        }

        return result.sortedBy { it.startTime }
    }

    /**
     * 将整个学期的课表（含单双周、起止周、调停课）实时全量映射同步至日程表 (schedules)
     * 任何新增课程、删除课程、AI 截图导入、调停课或修改开学日期/周数都会实时触发此同步
     */
    suspend fun syncSemesterTimetableToSchedules(
        context: Context,
        scheduleDao: ScheduleDao
    ): Int = withContext(Dispatchers.IO) {
        val semesterStart = TimetablePreferences.getSemesterStartDate(context)
        val totalWeeks = TimetablePreferences.getTotalWeeks(context).coerceIn(1, 30)
        val activeTerm = TimetablePreferences.getActiveSemester(context)
        val alignedMonday = semesterStart.minusDays((semesterStart.dayOfWeek.value - 1).toLong())

        val allCourses = courseDao.getAllCoursesList()
        val allAdjustments = courseDao.getAllRawAdjustments().filter { it.isDeleted == 0 }
        val adjustmentsByWeek = allAdjustments.groupBy { it.targetWeek }

        // 保留用户已手动勾选完成的课程日程状态
        val existingSynced = scheduleDao.getTimetableSyncedSchedules()
        val completedIds = existingSynced.filter { it.status == "COMPLETED" }.map { it.id }.toSet()

        val nowStr = com.renly.rmf.util.TimeUtils.currentUtcIsoString()
        val mappedSchedules = mutableListOf<ScheduleEntity>()

        if (allCourses.isNotEmpty() || allAdjustments.isNotEmpty()) {
            for (week in 1..totalWeeks) {
                val weekMonday = alignedMonday.plusWeeks((week - 1).toLong())
                val weekAdjs = adjustmentsByWeek[week] ?: emptyList()

                for (dayOffset in 0..6) {
                    val targetDate = weekMonday.plusDays(dayOffset.toLong())
                    val slots = projectSlotsForDay(
                        targetDate = targetDate,
                        weekNumber = week,
                        allCourses = allCourses,
                        weekAdjustments = weekAdjs
                    )
                    for (slot in slots) {
                        val timeKey = slot.startTime.format(DateTimeFormatter.ofPattern("HHmm"))
                        val deterministicId = "tt_t${activeTerm}_w${week}_${targetDate}_${slot.courseId}_$timeKey"
                        val mins = ChronoUnit.MINUTES.between(slot.startTime, slot.endTime).toInt().coerceAtLeast(15)
                        val metaParts = mutableListOf("第${week}周")
                        if (slot.location.isNotBlank()) metaParts.add("地点: ${slot.location}")
                        if (slot.teacher.isNotBlank()) metaParts.add("教师: ${slot.teacher}")
                        if (slot.isAdjusted && slot.adjustmentReason.isNotBlank()) metaParts.add(slot.adjustmentReason)

                        mappedSchedules.add(
                            ScheduleEntity(
                                id = deterministicId,
                                title = "🎓 ${slot.name}",
                                description = metaParts.joinToString(" | "),
                                workType = "DEEP_WORK",
                                category = "学习",
                                colorHex = slot.colorHex,
                                estimatedMinutes = mins,
                                startTime = slot.startTime.format(DateTimeFormatter.ISO_DATE_TIME),
                                endTime = slot.endTime.format(DateTimeFormatter.ISO_DATE_TIME),
                                status = if (completedIds.contains(deterministicId)) "COMPLETED" else "PENDING",
                                reminderMinutes = if (TimetablePreferences.isCourseReminderEnabled(context)) {
                                    TimetablePreferences.getReminderAdvanceMinutes(context)
                                } else {
                                    -1
                                },
                                source = "TIMETABLE_SYNC",
                                isDeleted = 0,
                                createdAt = nowStr,
                                updatedAt = nowStr
                            )
                        )
                    }
                }
            }
        }

        scheduleDao.deleteAllTimetableSyncedSchedules()
        if (mappedSchedules.isNotEmpty()) {
            scheduleDao.insertAll(mappedSchedules)
        }
        mappedSchedules.size
    }

    private fun calculateTimes(
        date: LocalDate,
        isCustomTime: Boolean,
        customStart: String?,
        customEnd: String?,
        startSection: Int,
        span: Int
    ): Pair<LocalDateTime, LocalDateTime> {
        if (isCustomTime && !customStart.isNullOrBlank() && !customEnd.isNullOrBlank()) {
            val sTime = try {
                LocalTime.parse(customStart, DateTimeFormatter.ofPattern("HH:mm"))
            } catch (_: Exception) {
                LocalTime.of(8, 0)
            }
            val eTime = try {
                LocalTime.parse(customEnd, DateTimeFormatter.ofPattern("HH:mm"))
            } catch (_: Exception) {
                LocalTime.of(9, 40)
            }
            return Pair(LocalDateTime.of(date, sTime), LocalDateTime.of(date, eTime))
        }

        val startStr = defaultSectionTimes[startSection]?.first ?: "08:00"
        val endSection = (startSection + span - 1).coerceAtMost(12)
        val endStr = defaultSectionTimes[endSection]?.second ?: "09:40"

        val sTime = LocalTime.parse(startStr, DateTimeFormatter.ofPattern("HH:mm"))
        val eTime = LocalTime.parse(endStr, DateTimeFormatter.ofPattern("HH:mm"))
        return Pair(LocalDateTime.of(date, sTime), LocalDateTime.of(date, eTime))
    }

    companion object {
        suspend fun syncAllToSchedule(
            context: Context,
            courseDao: CourseDao,
            scheduleDao: ScheduleDao
        ): Int {
            val service = TimetableProjectionService(courseDao)
            val count = service.syncSemesterTimetableToSchedules(context, scheduleDao)
            if (SystemCalendarSyncService.isAutoSyncEnabled(context)) {
                SystemCalendarSyncService.syncAllIfAutoSyncEnabled(context)
            }
            return count
        }
    }
}
