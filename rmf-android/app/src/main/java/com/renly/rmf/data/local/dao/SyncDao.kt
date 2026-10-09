package com.renly.rmf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.renly.rmf.data.local.entity.ScheduleTagEntity
import com.renly.rmf.data.local.entity.SnapshotMetadataEntity
import com.renly.rmf.data.local.entity.TimetableSettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    // Snapshot Metadata
    @Query("SELECT * FROM snapshot_metadata WHERE id = 'current' LIMIT 1")
    suspend fun getMetadata(): SnapshotMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMetadata(metadata: SnapshotMetadataEntity)

    // Timetable Settings
    @Query("SELECT value FROM timetable_settings WHERE key = :key LIMIT 1")
    suspend fun getSetting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: TimetableSettingEntity)

    // Schedule Tags
    @Query("SELECT * FROM schedule_tags ORDER BY sort_order ASC")
    fun getAllTags(): Flow<List<ScheduleTagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTag(tag: ScheduleTagEntity)

    @Query("SELECT * FROM schedule_tags")
    suspend fun getAllRawTags(): List<ScheduleTagEntity>

    @Query("SELECT * FROM timetable_settings")
    suspend fun getAllRawSettings(): List<TimetableSettingEntity>
}
