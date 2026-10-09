package com.renly.rmf.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "study_topics",
    indices = [
        Index(value = ["status", "is_deleted"], name = "idx_study_status")
    ]
)
data class StudyTopicEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "category", defaultValue = "'技术栈'")
    val category: String = "技术栈", // 技术栈, 计算机基础, 外语, 读书精读, 资格考证, 兴趣

    @ColumnInfo(name = "status", defaultValue = "'IN_PROGRESS'")
    val status: String = "IN_PROGRESS", // NOT_STARTED, IN_PROGRESS, COMPLETED

    @ColumnInfo(name = "progress_percent", defaultValue = "0")
    val progressPercent: Int = 0,

    @ColumnInfo(name = "progress_type", defaultValue = "'HOURS'")
    val progressType: String = "HOURS", // HOURS (时长), LESSONS (节数), PAGES (页数), CUSTOM (自定义单位)

    @ColumnInfo(name = "custom_unit", defaultValue = "''")
    val customUnit: String = "", // 如 "题", "词", "讲", "章"

    @ColumnInfo(name = "target_value", defaultValue = "10.0")
    val targetValue: Double = 10.0,

    @ColumnInfo(name = "completed_value", defaultValue = "0.0")
    val completedValue: Double = 0.0,

    @ColumnInfo(name = "target_hours", defaultValue = "10.0")
    val targetHours: Double = 10.0,

    @ColumnInfo(name = "completed_hours", defaultValue = "0.0")
    val completedHours: Double = 0.0,

    @ColumnInfo(name = "current_checkpoint")
    val currentCheckpoint: String = "",

    @ColumnInfo(name = "notes")
    val notes: String = "",

    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String
)
