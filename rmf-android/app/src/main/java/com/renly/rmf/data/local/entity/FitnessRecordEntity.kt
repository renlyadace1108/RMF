package com.renly.rmf.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "fitness_records",
    indices = [
        Index(value = ["record_date"], name = "idx_fitness_records_date"),
        Index(value = ["plan_id"], name = "idx_fitness_records_plan")
    ]
)
data class FitnessRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "plan_id")
    val planId: String = "",

    @ColumnInfo(name = "record_date")
    val recordDate: String, // YYYY-MM-DD

    @ColumnInfo(name = "exercise_name")
    val exerciseName: String,

    @ColumnInfo(name = "category")
    val category: String = "CHEST", // CHEST, BACK, LEGS, SHOULDERS, ARMS, CORE, CARDIO, STRETCH, OTHER

    @ColumnInfo(name = "equipment")
    val equipment: String = "",

    @ColumnInfo(name = "sets_data")
    val setsData: String = "[]",

    @ColumnInfo(name = "sets_count")
    val setsCount: Int = 4,

    @ColumnInfo(name = "reps_per_set")
    val repsPerSet: Int = 10,

    @ColumnInfo(name = "weight_kg")
    val weightKg: Double = 0.0,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "distance_km")
    val distanceKm: Double = 0.0,

    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Int = 0,

    @ColumnInfo(name = "calories_burned")
    val caloriesBurned: Double = 0.0,

    @ColumnInfo(name = "is_completed")
    val isCompleted: Int = 0,

    @ColumnInfo(name = "note")
    val note: String = "",

    @ColumnInfo(name = "is_deleted")
    val isDeleted: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String
) {
    fun getWorkoutSets(): List<com.renly.rmf.domain.fitness.model.WorkoutSet> {
        return com.renly.rmf.domain.fitness.model.WorkoutSet.fromJsonArray(
            setsData,
            fallbackSetsCount = setsCount,
            fallbackReps = repsPerSet,
            fallbackWeight = weightKg
        )
    }
}
