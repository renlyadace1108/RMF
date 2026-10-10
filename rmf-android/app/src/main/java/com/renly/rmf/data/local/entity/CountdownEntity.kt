package com.renly.rmf.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "countdowns")
data class CountdownEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "target_date")
    val targetDate: String, // YYYY-MM-DD or ISO datetime

    @ColumnInfo(name = "category", defaultValue = "'EXAM'")
    val category: String = "EXAM", // EXAM, MILESTONE, ANNIVERSARY, HOLIDAY, GOAL, OTHER

    @ColumnInfo(name = "color_hex", defaultValue = "'#F59E0B'")
    val colorHex: String = "#F59E0B",

    @ColumnInfo(name = "is_pinned", defaultValue = "0")
    val isPinned: Int = 0, // 1 = pinned to home/capsule top

    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String
)
