package com.renly.rmf.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "courses",
    indices = [
        Index(value = ["day_of_week", "start_section", "is_deleted"], name = "idx_courses_day_sec")
    ]
)
data class CourseEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "teacher")
    val teacher: String = "",

    @ColumnInfo(name = "location")
    val location: String = "",

    @ColumnInfo(name = "room_code")
    val roomCode: String = "",

    @ColumnInfo(name = "day_of_week")
    val dayOfWeek: Int = 1, // 1 = 周一, ..., 7 = 周日

    @ColumnInfo(name = "start_section")
    val startSection: Int = 1, // 1 到 12 节

    @ColumnInfo(name = "section_span", defaultValue = "2")
    val sectionSpan: Int = 2,

    @ColumnInfo(name = "start_week", defaultValue = "1")
    val startWeek: Int = 1,

    @ColumnInfo(name = "end_week", defaultValue = "16")
    val endWeek: Int = 16,

    @ColumnInfo(name = "week_type", defaultValue = "'ALL'")
    val weekType: String = "ALL", // ALL, ODD (单周), EVEN (双周)

    @ColumnInfo(name = "color_hex", defaultValue = "'#3B82F6'")
    val colorHex: String = "#3B82F6",

    @ColumnInfo(name = "notes")
    val notes: String = "",

    @ColumnInfo(name = "is_custom_time", defaultValue = "0")
    val isCustomTime: Int = 0, // 0 = 节次模式, 1 = 自定义精确起止时间模式

    @ColumnInfo(name = "custom_start_time")
    val customStartTime: String? = null, // 如 "14:15"

    @ColumnInfo(name = "custom_end_time")
    val customEndTime: String? = null, // 如 "15:45"

    @ColumnInfo(name = "exam_date")
    val examDate: String? = null,

    @ColumnInfo(name = "course_url")
    val courseUrl: String? = null,

    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String
)
