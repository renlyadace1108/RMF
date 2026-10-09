package com.renly.rmf.data.local.dao

import androidx.room.*
import com.renly.rmf.data.local.entity.FitnessPlanEntity
import com.renly.rmf.data.local.entity.FitnessRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FitnessDao {

    @Query("SELECT * FROM fitness_plans WHERE plan_date = :date AND is_deleted = 0 LIMIT 1")
    suspend fun getPlanByDate(date: String): FitnessPlanEntity?

    @Query("SELECT * FROM fitness_plans WHERE plan_date = :date AND is_deleted = 0 LIMIT 1")
    fun observePlanByDate(date: String): Flow<FitnessPlanEntity?>

    @Query("SELECT * FROM fitness_plans WHERE is_deleted = 0 ORDER BY plan_date DESC LIMIT :limit")
    fun observeRecentPlans(limit: Int = 30): Flow<List<FitnessPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlan(plan: FitnessPlanEntity)

    @Query("UPDATE fitness_plans SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun deletePlan(id: String, now: String)

    @Query("SELECT * FROM fitness_records WHERE record_date = :date AND is_deleted = 0 ORDER BY sort_order ASC, created_at ASC")
    fun observeRecordsByDate(date: String): Flow<List<FitnessRecordEntity>>

    @Query("SELECT * FROM fitness_records WHERE record_date = :date AND is_deleted = 0 ORDER BY sort_order ASC, created_at ASC")
    suspend fun getRecordsByDate(date: String): List<FitnessRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecord(record: FitnessRecordEntity)

    @Query("UPDATE fitness_records SET is_completed = CASE WHEN is_completed = 1 THEN 0 ELSE 1 END, updated_at = :now WHERE id = :id")
    suspend fun toggleRecordCompleted(id: String, now: String)

    @Query("UPDATE fitness_records SET is_completed = :completed, updated_at = :now WHERE record_date = :date AND is_deleted = 0")
    suspend fun setAllRecordsCompletedForDate(date: String, completed: Int, now: String)

    @Query("UPDATE fitness_records SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun deleteRecord(id: String, now: String)

    @Query("SELECT DISTINCT plan_date FROM fitness_plans WHERE is_deleted = 0 AND status = 'COMPLETED' ORDER BY plan_date DESC")
    suspend fun getCompletedDates(): List<String>

    @Query("SELECT DISTINCT plan_date FROM fitness_plans WHERE is_deleted = 0 AND status = 'COMPLETED' ORDER BY plan_date DESC")
    fun observeCompletedDates(): Flow<List<String>>

    @Query("SELECT * FROM fitness_plans WHERE is_deleted = 0 AND status = 'COMPLETED' AND plan_date >= :startDate")
    fun observeCompletedPlansSince(startDate: String): Flow<List<FitnessPlanEntity>>
}
