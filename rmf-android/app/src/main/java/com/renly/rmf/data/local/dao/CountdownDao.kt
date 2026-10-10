package com.renly.rmf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.renly.rmf.data.local.entity.CountdownEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CountdownDao {
    @Query("SELECT * FROM countdowns WHERE is_deleted = 0 ORDER BY is_pinned DESC, target_date ASC")
    fun getAllActiveCountdowns(): Flow<List<CountdownEntity>>

    @Query("SELECT * FROM countdowns WHERE is_deleted = 0 ORDER BY is_pinned DESC, target_date ASC")
    suspend fun getActiveCountdownsList(): List<CountdownEntity>

    @Query("SELECT * FROM countdowns WHERE id = :id LIMIT 1")
    suspend fun getCountdownById(id: String): CountdownEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(countdown: CountdownEntity)

    @Query("UPDATE countdowns SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: String)

    @Query("UPDATE countdowns SET is_pinned = :isPinned, updated_at = :updatedAt WHERE id = :id")
    suspend fun togglePin(id: String, isPinned: Int, updatedAt: String)

    @Query("SELECT * FROM countdowns")
    suspend fun getAllRawCountdowns(): List<CountdownEntity>
}
