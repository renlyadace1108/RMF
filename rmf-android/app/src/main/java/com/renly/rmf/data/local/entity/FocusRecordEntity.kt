package com.renly.rmf.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "focus_records",
    indices = [
        Index(value = ["schedule_id"], name = "idx_focus_records_sched"),
        Index(value = ["start_time"], name = "idx_focus_records_start")
    ]
)
data class FocusRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "schedule_id")
    val scheduleId: String? = null,

    @ColumnInfo(name = "task_title")
    val taskTitle: String = "深度专注",

    @ColumnInfo(name = "category")
    val category: String = "WORK",

    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Int,

    @ColumnInfo(name = "start_time")
    val startTime: String, // ISO-8601 LocalDateTime

    @ColumnInfo(name = "end_time")
    val endTime: String, // ISO-8601 LocalDateTime

    @ColumnInfo(name = "status")
    val status: String = "COMPLETED", // COMPLETED, INTERRUPTED

    @ColumnInfo(name = "interruption_count", defaultValue = "0")
    val interruptionCount: Int = 0,

    @ColumnInfo(name = "note")
    val note: String = ""
)
