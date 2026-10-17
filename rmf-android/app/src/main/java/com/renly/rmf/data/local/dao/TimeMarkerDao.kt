package com.renly.rmf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.renly.rmf.data.local.entity.TimeMarkerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeMarkerDao {
    @Query("SELECT * FROM time_markers WHERE is_deleted = 0 ORDER BY start_time ASC")
    fun getAllActiveMarkers(): Flow<List<TimeMarkerEntity>>

    @Query("SELECT * FROM time_markers WHERE date = :date AND is_deleted = 0 ORDER BY start_time ASC")
    fun getMarkersByDate(date: String): Flow<List<TimeMarkerEntity>>

    @Query("SELECT * FROM time_markers WHERE date = :date AND is_deleted = 0 ORDER BY start_time ASC")
    suspend fun getMarkersListByDate(date: String): List<TimeMarkerEntity>

    @Query("SELECT * FROM time_markers WHERE id = :id LIMIT 1")
    suspend fun getMarkerById(id: String): TimeMarkerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(marker: TimeMarkerEntity)

    @Query("UPDATE time_markers SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: String)

    @Query("SELECT * FROM time_markers")
    suspend fun getAllRawMarkers(): List<TimeMarkerEntity>
}
