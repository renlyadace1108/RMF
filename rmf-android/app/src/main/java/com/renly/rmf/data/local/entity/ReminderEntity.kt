package com.renly.rmf.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "reminders",
    indices = [
        Index(value = ["remind_time", "is_deleted", "is_completed"], name = "idx_reminders_time")
    ]
)
data class ReminderEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "content", defaultValue = "''")
    val content: String = "",

    @ColumnInfo(name = "remind_time")
    val remindTime: String, // ISO-8601: yyyy-MM-dd'T'HH:mm:ss

    @ColumnInfo(name = "advance_minutes", defaultValue = "0")
    val advanceMinutes: Int = 0, // 提前提醒分钟数 (0 = 准时)

    @ColumnInfo(name = "reminder_type", defaultValue = "'ALL'")
    val reminderType: String = "ALL", // ALL, ALARM, VIBRATE, NONE

    @ColumnInfo(name = "repeat_rule", defaultValue = "'NONE'")
    val repeatRule: String = "NONE", // NONE, DAILY, WEEKDAYS, WEEKLY, MONTHLY, YEARLY

    @ColumnInfo(name = "category", defaultValue = "'REMINDER'")
    val category: String = "REMINDER", // REMINDER, MEMO, LIFE, WORK, HEALTH

    @ColumnInfo(name = "priority", defaultValue = "'MEDIUM'")
    val priority: String = "MEDIUM", // HIGH, MEDIUM, LOW

    @ColumnInfo(name = "is_completed", defaultValue = "0")
    val isCompleted: Int = 0, // 0 = 待提醒, 1 = 已完成

    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String
)
