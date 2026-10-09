package com.renly.rmf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.renly.rmf.data.local.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE is_deleted = 0 ORDER BY is_north_star DESC, sort_order ASC, created_at ASC")
    fun getAllActiveGoals(): Flow<List<GoalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(goal: GoalEntity)

    @Query("UPDATE goals SET progress = :progress WHERE id = :id")
    suspend fun updateProgress(id: String, progress: Int)

    @Query("UPDATE goals SET is_deleted = 1 WHERE id = :id")
    suspend fun softDelete(id: String)

    @Query("SELECT * FROM goals")
    suspend fun getAllRawGoals(): List<GoalEntity>
}
