package com.renly.rmf.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "icon", defaultValue = "'✨'")
    val icon: String = "✨",

    @ColumnInfo(name = "target_days_per_week", defaultValue = "7")
    val targetDaysPerWeek: Int = 7,

    @ColumnInfo(name = "color_hex", defaultValue = "'#38BDF8'")
    val colorHex: String = "#38BDF8",

    @ColumnInfo(name = "current_streak", defaultValue = "0")
    val currentStreak: Int = 0,

    @ColumnInfo(name = "best_streak", defaultValue = "0")
    val bestStreak: Int = 0,

    @ColumnInfo(name = "history_dates_json", defaultValue = "'[]'")
    val historyDatesJson: String = "[]", // ["2026-10-08", "2026-10-09"]

    @ColumnInfo(name = "reminder_time", defaultValue = "''")
    val reminderTime: String = "", // e.g. "08:00"

    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String
)
