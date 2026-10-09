package com.renly.rmf.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "course_adjustments",
    indices = [
        Index(value = ["target_week", "is_deleted"], name = "idx_course_adj_week")
    ]
)
data class CourseAdjustmentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "course_id")
    val courseId: String? = null,

    @ColumnInfo(name = "course_name")
    val courseName: String = "",

    @ColumnInfo(name = "adjustment_type")
    val adjustmentType: String, // CANCEL (停课), RESCHEDULE (调课), ADD (补课/加课)

    @ColumnInfo(name = "target_week")
    val targetWeek: Int, // 仅对特定周次生效

    @ColumnInfo(name = "orig_day_of_week")
    val origDayOfWeek: Int = 1,

    @ColumnInfo(name = "orig_start_section")
    val origStartSection: Int = 1,

    @ColumnInfo(name = "new_day_of_week")
    val newDayOfWeek: Int = 1,

    @ColumnInfo(name = "new_start_section")
    val newStartSection: Int = 1,

    @ColumnInfo(name = "section_span", defaultValue = "2")
    val sectionSpan: Int = 2,

    @ColumnInfo(name = "new_location")
    val newLocation: String = "",

    @ColumnInfo(name = "reason")
    val reason: String = "",

    @ColumnInfo(name = "is_custom_time", defaultValue = "0")
    val isCustomTime: Int = 0,

    @ColumnInfo(name = "custom_start_time")
    val customStartTime: String? = null,

    @ColumnInfo(name = "custom_end_time")
    val customEndTime: String? = null,

    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String
)
