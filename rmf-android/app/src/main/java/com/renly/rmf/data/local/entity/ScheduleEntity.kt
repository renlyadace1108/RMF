package com.renly.rmf.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "schedules",
    indices = [
        Index(value = ["start_time", "end_time", "is_deleted"], name = "idx_schedules_time"),
        Index(value = ["status", "is_deleted"], name = "idx_schedules_status")
    ]
)
data class ScheduleEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String = "",

    @ColumnInfo(name = "category")
    val category: String = "WORK", // WORK, STUDY, LIFE, HEALTH, FINANCE, OTHER

    @ColumnInfo(name = "priority")
    val priority: String = "MEDIUM", // LOW, MEDIUM, HIGH, URGENT

    @ColumnInfo(name = "status")
    val status: String = "PENDING", // PENDING, IN_PROGRESS, COMPLETED, POSTPONED, ABANDONED

    @ColumnInfo(name = "start_time")
    val startTime: String, // ISO-8601 YYYY-MM-DDTHH:mm:ss

    @ColumnInfo(name = "end_time")
    val endTime: String,

    @ColumnInfo(name = "estimated_minutes")
    val estimatedMinutes: Int = 30,

    @ColumnInfo(name = "actual_minutes", defaultValue = "0")
    val actualMinutes: Int = 0,

    @ColumnInfo(name = "work_type", defaultValue = "'DEEP_WORK'")
    val workType: String = "DEEP_WORK", // DEEP_WORK, SHALLOW_WORK, REST_BUFFER

    @ColumnInfo(name = "dod", defaultValue = "''")
    val dod: String = "",

    @ColumnInfo(name = "energy_delta", defaultValue = "0")
    val energyDelta: Int = 0, // -2 ~ +2

    @ColumnInfo(name = "goal_id")
    val goalId: String? = null,

    @ColumnInfo(name = "study_topic_id")
    val studyTopicId: String? = null,

    @ColumnInfo(name = "color_hex")
    val colorHex: String? = null,

    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Int = 0, // 0 = false, 1 = true (软删除标记)

    @ColumnInfo(name = "interruption_minutes", defaultValue = "0")
    val interruptionMinutes: Int? = 0,

    @ColumnInfo(name = "is_deferred", defaultValue = "0")
    val isDeferred: Int? = 0,

    @ColumnInfo(name = "is_backlog", defaultValue = "0")
    val isBacklog: Int? = 0,

    @ColumnInfo(name = "sync_version", defaultValue = "1")
    val syncVersion: Int? = 1,

    @ColumnInfo(name = "is_dirty", defaultValue = "0")
    val isDirty: Int? = 0,

    @ColumnInfo(name = "is_all_day", defaultValue = "0")
    val isAllDay: Int? = 0,

    @ColumnInfo(name = "recurrence", defaultValue = "'NONE'")
    val recurrence: String? = "NONE",

    @ColumnInfo(name = "source", defaultValue = "''")
    val source: String? = "",

    @ColumnInfo(name = "is_tentative", defaultValue = "0")
    val isTentative: Int? = 0,

    @ColumnInfo(name = "is_locked", defaultValue = "0")
    val isLocked: Int? = 0,

    @ColumnInfo(name = "depends_on_task_id")
    val dependsOnTaskId: String? = null,

    @ColumnInfo(name = "postpone_count", defaultValue = "0")
    val postponeCount: Int? = 0,

    @ColumnInfo(name = "eisenhower_quadrant", defaultValue = "'Q2'")
    val eisenhowerQuadrant: String? = "Q2",

    @ColumnInfo(name = "created_at")
    val createdAt: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String
)
