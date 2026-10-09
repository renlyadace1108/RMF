package com.renly.rmf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.renly.rmf.data.local.entity.CourseAdjustmentEntity
import com.renly.rmf.data.local.entity.CourseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses WHERE is_deleted = 0 ORDER BY day_of_week ASC, start_section ASC")
    fun getAllActiveCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE is_deleted = 0")
    suspend fun getAllCoursesList(): List<CourseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCourse(course: CourseEntity)

    @Query("UPDATE courses SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDeleteCourse(id: String, updatedAt: String)

    @Query("SELECT * FROM course_adjustments WHERE is_deleted = 0 AND target_week = :targetWeek")
    fun getAdjustmentsForWeek(targetWeek: Int): Flow<List<CourseAdjustmentEntity>>

    @Query("SELECT * FROM course_adjustments WHERE is_deleted = 0 ORDER BY target_week ASC")
    fun getAllActiveAdjustments(): Flow<List<CourseAdjustmentEntity>>

    @Query("SELECT * FROM course_adjustments WHERE is_deleted = 0 AND target_week = :targetWeek")
    suspend fun getAdjustmentsForWeekList(targetWeek: Int): List<CourseAdjustmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAdjustment(adjustment: CourseAdjustmentEntity)

    @Query("UPDATE course_adjustments SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDeleteAdjustment(id: String, updatedAt: String)

    @Query("SELECT * FROM courses")
    suspend fun getAllRawCourses(): List<CourseEntity>

    @Query("SELECT * FROM course_adjustments")
    suspend fun getAllRawAdjustments(): List<CourseAdjustmentEntity>
}
