package com.renly.rmf.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "goals",
    indices = [
        Index(value = ["parent_id", "is_deleted"], name = "idx_goals_parent")
    ]
)
data class GoalEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "level")
    val level: String = "ANNUAL", // ANNUAL, QUARTERLY, MONTHLY, WEEKLY

    @ColumnInfo(name = "parent_id")
    val parentId: String? = null,

    @ColumnInfo(name = "quarter")
    val quarter: String? = null,

    @ColumnInfo(name = "progress", defaultValue = "0")
    val progress: Int = 0, // 0 - 100

    @ColumnInfo(name = "sort_order", defaultValue = "0")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "is_north_star", defaultValue = "0")
    val isNorthStar: Int = 0, // 1 = 北极星战略目标

    @ColumnInfo(name = "confidence", defaultValue = "1.0")
    val confidence: Double = 1.0, // 0.0 ~ 1.0 置信度

    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: String
)
