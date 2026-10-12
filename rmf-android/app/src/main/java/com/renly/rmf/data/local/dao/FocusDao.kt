package com.renly.rmf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.renly.rmf.data.local.entity.FocusRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_records ORDER BY start_time DESC")
    fun getAllFocusRecords(): Flow<List<FocusRecordEntity>>

    @Query("SELECT * FROM focus_records WHERE start_time >= :startTime ORDER BY start_time DESC")
    fun getFocusRecordsSince(startTime: String): Flow<List<FocusRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: FocusRecordEntity)

    @Query("DELETE FROM focus_records WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM focus_records")
    suspend fun getAllRawRecords(): List<FocusRecordEntity>
}
