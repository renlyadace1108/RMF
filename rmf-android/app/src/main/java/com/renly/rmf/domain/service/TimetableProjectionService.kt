package com.renly.rmf.domain.service

import com.renly.rmf.data.local.dao.CourseDao
import com.renly.rmf.data.local.entity.CourseAdjustmentEntity
import com.renly.rmf.data.local.entity.CourseEntity
import com.renly.rmf.data.local.entity.ScheduleEntity
import java.time.DayOfWeek
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
    val adjustmentReason: String = ""
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
     * 计算指定日期属于学期的第几周
     */
    fun calculateWeekNumber(date: LocalDate, semesterStartDate: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(semesterStartDate, date)
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

        val dayOfWeekInt = targetDate.dayOfWeek.value // 1 = Monday, 7 = Sunday
        val isOddWeek = (weekNumber % 2 != 0)

        val result = mutableListOf<ProjectedCourseSlot>()

        // 1. 遍历基准课程
        for (course in allCourses) {
            // 校验周次范围
            if (weekNumber < course.startWeek || weekNumber > course.endWeek) continue

            // 校验单双周
            if (course.weekType == "ODD" && !isOddWeek) continue
            if (course.weekType == "EVEN" && isOddWeek) continue

            // 检查当前课程在当周是否有微调
            val adj = adjustments.find { it.courseId == course.id }
            if (adj != null) {
                if (adj.adjustmentType == "CANCEL") {
                    // 本周该课程停课，跳过
                    continue
                } else if (adj.adjustmentType == "RESCHEDULE") {
                    // 调课：检查新安排是否在今天
                    if (adj.newDayOfWeek == dayOfWeekInt) {
                        val times = calculateTimes(targetDate, adj.isCustomTime == 1, adj.customStartTime, adj.customEndTime, adj.newStartSection, adj.sectionSpan)
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
                                adjustmentReason = "已调课: ${adj.reason}"
                            )
                        )
                    }
                    continue
                }
            }

            // 正常基准课程在今天
            if (course.dayOfWeek == dayOfWeekInt) {
                val times = calculateTimes(targetDate, course.isCustomTime == 1, course.customStartTime, course.customEndTime, course.startSection, course.sectionSpan)
                result.add(
                    ProjectedCourseSlot(
                        courseId = course.id,
                        name = course.name,
                        teacher = course.teacher,
                        location = course.location,
                        startTime = times.first,
                        endTime = times.second,
                        colorHex = course.colorHex,
                        isAdjusted = false
                    )
                )
            }
        }

        // 2. 检查是否有针对当天的独立加课 (ADD)
        val addedCourses = adjustments.filter { it.adjustmentType == "ADD" && it.newDayOfWeek == dayOfWeekInt }
        for (addAdj in addedCourses) {
            val times = calculateTimes(targetDate, addAdj.isCustomTime == 1, addAdj.customStartTime, addAdj.customEndTime, addAdj.newStartSection, addAdj.sectionSpan)
            result.add(
                ProjectedCourseSlot(
                    courseId = addAdj.id,
                    name = addAdj.courseName,
                    teacher = "",
                    location = addAdj.newLocation,
                    startTime = times.first,
                    endTime = times.second,
                    colorHex = "#10B981", // 绿色标注临时加课
                    isAdjusted = true,
                    adjustmentReason = "临时加课: ${addAdj.reason}"
                )
            )
        }

        return result.sortedBy { it.startTime }
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
            val sTime = LocalTime.parse(customStart, DateTimeFormatter.ofPattern("HH:mm"))
            val eTime = LocalTime.parse(customEnd, DateTimeFormatter.ofPattern("HH:mm"))
            return Pair(LocalDateTime.of(date, sTime), LocalDateTime.of(date, eTime))
        }

        val startStr = defaultSectionTimes[startSection]?.first ?: "08:00"
        val endSection = (startSection + span - 1).coerceAtMost(12)
        val endStr = defaultSectionTimes[endSection]?.second ?: "09:40"

        val sTime = LocalTime.parse(startStr, DateTimeFormatter.ofPattern("HH:mm"))
        val eTime = LocalTime.parse(endStr, DateTimeFormatter.ofPattern("HH:mm"))
        return Pair(LocalDateTime.of(date, sTime), LocalDateTime.of(date, eTime))
    }
}
