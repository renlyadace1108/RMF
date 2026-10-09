package com.renly.rmf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.renly.rmf.data.local.entity.ScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules WHERE is_deleted = 0 ORDER BY start_time ASC")
    fun getAllActiveSchedules(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE is_deleted = 0 ORDER BY start_time ASC")
    suspend fun getActiveSchedulesList(): List<ScheduleEntity>

    @Query("SELECT * FROM schedules WHERE is_deleted = 0 AND start_time >= :startTime AND start_time < :endTime ORDER BY start_time ASC")
    fun getSchedulesByDateRange(startTime: String, endTime: String): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE id = :id LIMIT 1")
    suspend fun getScheduleById(id: String): ScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(schedule: ScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(schedules: List<ScheduleEntity>)

    @Query("UPDATE schedules SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: String)

    @Query("UPDATE schedules SET status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: String)

    @Query("SELECT * FROM schedules")
    suspend fun getAllRawSchedules(): List<ScheduleEntity>
}
