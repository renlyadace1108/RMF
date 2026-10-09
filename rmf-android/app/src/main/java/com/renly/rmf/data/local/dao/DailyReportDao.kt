package com.renly.rmf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.renly.rmf.data.local.entity.DailyReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyReportDao {
    @Query("SELECT * FROM daily_reports WHERE date = :date LIMIT 1")
    suspend fun getReportByDate(date: String): DailyReportEntity?

    @Query("SELECT * FROM daily_reports ORDER BY date DESC")
    fun getAllReports(): Flow<List<DailyReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(report: DailyReportEntity)

    @Query("SELECT * FROM daily_reports")
    suspend fun getAllRawReports(): List<DailyReportEntity>
}
