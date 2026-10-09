package com.renly.rmf.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "schedule_tags",
    indices = [
        Index(value = ["name"], unique = true)
    ]
)
data class ScheduleTagEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "color_hex", defaultValue = "'#1A73E8'")
    val colorHex: String = "#1A73E8",

    @ColumnInfo(name = "sort_order", defaultValue = "0")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: String = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_DATE_TIME)
)
