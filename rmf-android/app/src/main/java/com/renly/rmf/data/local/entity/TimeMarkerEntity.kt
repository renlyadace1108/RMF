package com.renly.rmf.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "time_markers",
    indices = [
        Index(value = ["date", "is_deleted"], name = "idx_markers_date"),
        Index(value = ["start_time", "is_deleted"], name = "idx_markers_time")
    ]
)
data class TimeMarkerEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "name")
    val name: String, // "起床", "睡觉", "午休", "实际开工", "实际收工", or custom

    @ColumnInfo(name = "marker_type", defaultValue = "'POINT'")
    val markerType: String = "POINT", // "POINT" (时间点) or "RANGE" (时间段)

    @ColumnInfo(name = "category", defaultValue = "'LIFESTYLE'")
    val category: String = "LIFESTYLE", // SLEEP, REST, MEAL, WORK, EXERCISE, CUSTOM

    @ColumnInfo(name = "icon", defaultValue = "'📍'")
    val icon: String = "📍", // 🌅, 🌙, 💤, 🍱, 💻, 🏃, 📍

    @ColumnInfo(name = "date")
    val date: String, // YYYY-MM-DD

    @ColumnInfo(name = "start_time")
    val startTime: String, // ISO-8601 YYYY-MM-DDTHH:mm:ss

    @ColumnInfo(name = "end_time", defaultValue = "''")
    val endTime: String = "", // ISO-8601 for RANGE, empty for POINT

    @ColumnInfo(name = "color_hex", defaultValue = "'#38BDF8'")
    val colorHex: String = "#38BDF8",

    @ColumnInfo(name = "notes", defaultValue = "''")
    val notes: String = "",

    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String
)
