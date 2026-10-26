package com.renly.rmf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.renly.rmf.data.local.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders WHERE is_deleted = 0 ORDER BY is_completed ASC, remind_time ASC")
    fun getAllActiveReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE is_deleted = 0 AND is_completed = 0 ORDER BY remind_time ASC")
    fun getPendingReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE is_deleted = 0 AND is_completed = 0 ORDER BY remind_time ASC")
    suspend fun getPendingRemindersList(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id AND is_deleted = 0 LIMIT 1")
    suspend fun getReminderById(id: String): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(reminder: ReminderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reminders: List<ReminderEntity>)

    @Query("UPDATE reminders SET is_completed = :completed, updated_at = :updatedAt WHERE id = :id")
    suspend fun toggleComplete(id: String, completed: Int, updatedAt: String)

    @Query("UPDATE reminders SET remind_time = :newTime, advance_minutes = :advanceMins, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateRemindTime(id: String, newTime: String, advanceMins: Int, updatedAt: String)

    @Query("UPDATE reminders SET remind_time = :newTime, is_completed = :completed, updated_at = :updatedAt WHERE id = :id")
    suspend fun advanceRepeatCycle(id: String, newTime: String, completed: Int, updatedAt: String)

    @Query("UPDATE reminders SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: String)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun hardDelete(id: String)
}
