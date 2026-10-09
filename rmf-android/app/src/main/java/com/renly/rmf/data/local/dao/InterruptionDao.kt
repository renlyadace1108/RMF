package com.renly.rmf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.renly.rmf.data.local.entity.InterruptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InterruptionDao {
    @Query("SELECT * FROM interruptions ORDER BY timestamp DESC")
    fun getAllInterruptions(): Flow<List<InterruptionEntity>>

    @Query("SELECT * FROM interruptions WHERE schedule_id = :scheduleId")
    suspend fun getInterruptionsByScheduleId(scheduleId: String): List<InterruptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(interruption: InterruptionEntity)

    @Query("SELECT * FROM interruptions")
    suspend fun getAllRawInterruptions(): List<InterruptionEntity>
}
