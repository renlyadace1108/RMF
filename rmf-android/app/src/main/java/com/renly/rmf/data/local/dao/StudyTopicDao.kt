package com.renly.rmf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.renly.rmf.data.local.entity.StudyTopicEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyTopicDao {
    @Query("SELECT * FROM study_topics WHERE is_deleted = 0 ORDER BY created_at DESC")
    fun getAllActiveTopics(): Flow<List<StudyTopicEntity>>

    @Query("SELECT * FROM study_topics WHERE id = :id LIMIT 1")
    suspend fun getTopicById(id: String): StudyTopicEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(topic: StudyTopicEntity)

    @Query("UPDATE study_topics SET completed_value = :completedValue, progress_percent = :percent, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateProgress(id: String, completedValue: Double, percent: Int, updatedAt: String)

    @Query("UPDATE study_topics SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: String)

    @Query("SELECT * FROM study_topics")
    suspend fun getAllRawTopics(): List<StudyTopicEntity>
}
