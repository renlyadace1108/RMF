package com.renly.rmf.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "interruptions",
    indices = [
        Index(value = ["schedule_id"], name = "idx_interruptions_sched"),
        Index(value = ["timestamp"], name = "idx_interruptions_time")
    ]
)
data class InterruptionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "schedule_id")
    val scheduleId: String? = null,

    @ColumnInfo(name = "type")
    val type: String, // EXTERNAL, INTERNAL, EMERGENCY, TECH

    @ColumnInfo(name = "duration_seconds", defaultValue = "0")
    val durationSeconds: Int = 0,

    @ColumnInfo(name = "timestamp")
    val timestamp: String,

    @ColumnInfo(name = "note")
    val note: String = ""
)
