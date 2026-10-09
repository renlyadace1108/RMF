package com.renly.rmf.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "fitness_plans",
    indices = [
        Index(value = ["plan_date"], name = "idx_fitness_plans_date")
    ]
)
data class FitnessPlanEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "plan_date")
    val planDate: String, // YYYY-MM-DD

    @ColumnInfo(name = "title")
    val title: String = "今日体能强化",

    @ColumnInfo(name = "workout_type")
    val workoutType: String = "STRENGTH", // STRENGTH, CARDIO, HIIT, STRETCH, OTHER

    @ColumnInfo(name = "target_duration_minutes")
    val targetDurationMinutes: Int = 45,

    @ColumnInfo(name = "actual_duration_minutes")
    val actualDurationMinutes: Int = 0,

    @ColumnInfo(name = "calories_burned")
    val caloriesBurned: Double = 0.0,

    @ColumnInfo(name = "feeling")
    val feeling: String = "MODERATE", // EASY, MODERATE, HARD

    @ColumnInfo(name = "status")
    val status: String = "PLANNED", // PLANNED, COMPLETED, SKIPPED

    @ColumnInfo(name = "notes")
    val notes: String = "",

    @ColumnInfo(name = "is_deleted")
    val isDeleted: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String
)
