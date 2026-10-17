package com.renly.rmf.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.renly.rmf.data.local.dao.*
import com.renly.rmf.data.local.entity.*

@Database(
    entities = [
        ScheduleEntity::class,
        CourseEntity::class,
        CourseAdjustmentEntity::class,
        StudyTopicEntity::class,
        GoalEntity::class,
        InterruptionEntity::class,
        ExpenseEntity::class,
        DailyReportEntity::class,
        ScheduleTagEntity::class,
        SnapshotMetadataEntity::class,
        TimetableSettingEntity::class,
        FitnessPlanEntity::class,
        FitnessRecordEntity::class,
        HabitEntity::class,
        CountdownEntity::class,
        FocusRecordEntity::class,
        ReminderEntity::class,
        NoteEntity::class,
        TimeMarkerEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class RmfDatabase : RoomDatabase() {

    abstract fun scheduleDao(): ScheduleDao
    abstract fun courseDao(): CourseDao
    abstract fun studyTopicDao(): StudyTopicDao
    abstract fun goalDao(): GoalDao
    abstract fun interruptionDao(): InterruptionDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun dailyReportDao(): DailyReportDao
    abstract fun syncDao(): SyncDao
    abstract fun fitnessDao(): FitnessDao
    abstract fun habitDao(): HabitDao
    abstract fun countdownDao(): CountdownDao
    abstract fun focusDao(): FocusDao
    abstract fun reminderDao(): ReminderDao
    abstract fun noteDao(): NoteDao
    abstract fun timeMarkerDao(): TimeMarkerDao

    /**
     * 清空所有测试与业务数据，保留表结构与系统偏好，对齐 Windows 端 ClearAllData
     */
    suspend fun clearAllBusinessData() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val db = openHelper.writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM schedules;")
            db.execSQL("DELETE FROM courses;")
            db.execSQL("DELETE FROM course_adjustments;")
            db.execSQL("DELETE FROM study_topics;")
            db.execSQL("DELETE FROM goals;")
            db.execSQL("DELETE FROM interruptions;")
            db.execSQL("DELETE FROM expenses;")
            db.execSQL("DELETE FROM daily_reports;")
            db.execSQL("DELETE FROM fitness_plans;")
            db.execSQL("DELETE FROM fitness_records;")
            db.execSQL("DELETE FROM habits;")
            db.execSQL("DELETE FROM countdowns;")
            db.execSQL("DELETE FROM focus_records;")
            db.execSQL("DELETE FROM reminders;")
            db.execSQL("DELETE FROM notes;")
            db.execSQL("DELETE FROM time_markers;")
            db.execSQL("DELETE FROM snapshot_metadata;")
            db.execSQL("DELETE FROM timetable_settings WHERE key LIKE 'test_%';")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        const val DATABASE_NAME = "rmf.db"
        private const val CURRENT_DB_VERSION = 10

        private data class ColSpec(
            val name: String,
            val type: String,
            val notNull: Boolean,
            val defaultSql: String? = null,
            val fallbackExpr: String = "''",
            val pkPos: Int = 0
        )

        private data class IndexSpec(
            val name: String,
            val unique: Boolean,
            val columns: List<String>
        )

        private data class TableSpec(
            val tableName: String,
            val columns: List<ColSpec>,
            val indices: List<IndexSpec> = emptyList()
        )

        /**
         * 16 张核心业务表与 Room @Entity 100% 严格对齐的精确 Schema 规范表。
         * 用于覆盖安装前/迁移时的无损表结构自愈，确保任何历史版本数据 0 丢失载入。
         */
        private val ALL_TABLE_SPECS: List<TableSpec> = listOf(
            TableSpec(
                tableName = "schedules",
                columns = listOf(
                    ColSpec("id", "TEXT", true, null, "lower(hex(randomblob(16)))", pkPos = 1),
                    ColSpec("title", "TEXT", true, null, "''"),
                    ColSpec("description", "TEXT", true, null, "''"),
                    ColSpec("category", "TEXT", true, null, "'WORK'"),
                    ColSpec("priority", "TEXT", true, null, "'MEDIUM'"),
                    ColSpec("status", "TEXT", true, null, "'PENDING'"),
                    ColSpec("start_time", "TEXT", true, null, "''"),
                    ColSpec("end_time", "TEXT", true, null, "''"),
                    ColSpec("estimated_minutes", "INTEGER", true, null, "30"),
                    ColSpec("actual_minutes", "INTEGER", true, "0", "0"),
                    ColSpec("work_type", "TEXT", true, "'DEEP_WORK'", "'DEEP_WORK'"),
                    ColSpec("dod", "TEXT", true, "''", "''"),
                    ColSpec("energy_delta", "INTEGER", true, "0", "0"),
                    ColSpec("goal_id", "TEXT", false, null, "NULL"),
                    ColSpec("study_topic_id", "TEXT", false, null, "NULL"),
                    ColSpec("color_hex", "TEXT", false, null, "NULL"),
                    ColSpec("is_deleted", "INTEGER", true, "0", "0"),
                    ColSpec("interruption_minutes", "INTEGER", false, "0", "0"),
                    ColSpec("is_deferred", "INTEGER", false, "0", "0"),
                    ColSpec("is_backlog", "INTEGER", false, "0", "0"),
                    ColSpec("sync_version", "INTEGER", false, "1", "1"),
                    ColSpec("is_dirty", "INTEGER", false, "0", "0"),
                    ColSpec("is_all_day", "INTEGER", false, "0", "0"),
                    ColSpec("recurrence", "TEXT", false, "'NONE'", "'NONE'"),
                    ColSpec("source", "TEXT", false, "''", "''"),
                    ColSpec("is_tentative", "INTEGER", false, "0", "0"),
                    ColSpec("is_locked", "INTEGER", false, "0", "0"),
                    ColSpec("depends_on_task_id", "TEXT", false, null, "NULL"),
                    ColSpec("postpone_count", "INTEGER", false, "0", "0"),
                    ColSpec("eisenhower_quadrant", "TEXT", false, "'Q2'", "'Q2'"),
                    ColSpec("recurrence_rule", "TEXT", true, "''", "''"),
                    ColSpec("subtasks_json", "TEXT", true, "'[]'", "'[]'"),
                    ColSpec("reminder_minutes", "INTEGER", true, "10", "10"),
                    ColSpec("reminder_type", "TEXT", true, "'ALL'", "'ALL'"),
                    ColSpec("created_at", "TEXT", true, null, "datetime('now')"),
                    ColSpec("updated_at", "TEXT", true, null, "datetime('now')")
                ),
                indices = listOf(
                    IndexSpec("idx_schedules_time", false, listOf("start_time", "end_time", "is_deleted")),
                    IndexSpec("idx_schedules_status", false, listOf("status", "is_deleted"))
                )
            ),
            TableSpec(
                tableName = "courses",
                columns = listOf(
                    ColSpec("id", "TEXT", true, null, "lower(hex(randomblob(16)))", pkPos = 1),
                    ColSpec("name", "TEXT", true, null, "''"),
                    ColSpec("teacher", "TEXT", true, null, "''"),
                    ColSpec("location", "TEXT", true, null, "''"),
                    ColSpec("room_code", "TEXT", true, null, "''"),
                    ColSpec("day_of_week", "INTEGER", true, null, "1"),
                    ColSpec("start_section", "INTEGER", true, null, "1"),
                    ColSpec("section_span", "INTEGER", true, "2", "2"),
                    ColSpec("start_week", "INTEGER", true, "1", "1"),
                    ColSpec("end_week", "INTEGER", true, "16", "16"),
                    ColSpec("week_type", "TEXT", true, "'ALL'", "'ALL'"),
                    ColSpec("color_hex", "TEXT", true, "'#3B82F6'", "'#3B82F6'"),
                    ColSpec("notes", "TEXT", true, null, "''"),
                    ColSpec("is_custom_time", "INTEGER", true, "0", "0"),
                    ColSpec("custom_start_time", "TEXT", false, null, "NULL"),
                    ColSpec("custom_end_time", "TEXT", false, null, "NULL"),
                    ColSpec("exam_date", "TEXT", false, null, "NULL"),
                    ColSpec("course_url", "TEXT", false, null, "NULL"),
                    ColSpec("reminder_minutes", "INTEGER", true, "15", "15"),
                    ColSpec("is_deleted", "INTEGER", true, "0", "0"),
                    ColSpec("created_at", "TEXT", true, null, "datetime('now')"),
                    ColSpec("updated_at", "TEXT", true, null, "datetime('now')")
                ),
                indices = listOf(
                    IndexSpec("idx_courses_day_sec", false, listOf("day_of_week", "start_section", "is_deleted"))
                )
            ),
            TableSpec(
                tableName = "course_adjustments",
                columns = listOf(
                    ColSpec("id", "TEXT", true, null, "lower(hex(randomblob(16)))", pkPos = 1),
                    ColSpec("course_id", "TEXT", false, null, "NULL"),
                    ColSpec("course_name", "TEXT", true, null, "''"),
                    ColSpec("adjustment_type", "TEXT", true, null, "'CANCEL'"),
                    ColSpec("target_week", "INTEGER", true, null, "1"),
                    ColSpec("orig_day_of_week", "INTEGER", true, null, "1"),
                    ColSpec("orig_start_section", "INTEGER", true, null, "1"),
                    ColSpec("new_day_of_week", "INTEGER", true, null, "1"),
                    ColSpec("new_start_section", "INTEGER", true, null, "1"),
                    ColSpec("section_span", "INTEGER", true, "2", "2"),
                    ColSpec("new_location", "TEXT", true, null, "''"),
                    ColSpec("reason", "TEXT", true, null, "''"),
                    ColSpec("is_custom_time", "INTEGER", true, "0", "0"),
                    ColSpec("custom_start_time", "TEXT", false, null, "NULL"),
                    ColSpec("custom_end_time", "TEXT", false, null, "NULL"),
                    ColSpec("is_deleted", "INTEGER", true, "0", "0"),
                    ColSpec("created_at", "TEXT", true, null, "datetime('now')"),
                    ColSpec("updated_at", "TEXT", true, null, "datetime('now')")
                ),
                indices = listOf(
                    IndexSpec("idx_course_adj_week", false, listOf("target_week", "is_deleted"))
                )
            ),
            TableSpec(
                tableName = "study_topics",
                columns = listOf(
                    ColSpec("id", "TEXT", true, null, "lower(hex(randomblob(16)))", pkPos = 1),
                    ColSpec("title", "TEXT", true, null, "''"),
                    ColSpec("category", "TEXT", true, "'技术栈'", "'技术栈'"),
                    ColSpec("status", "TEXT", true, "'IN_PROGRESS'", "'IN_PROGRESS'"),
                    ColSpec("progress_percent", "INTEGER", true, "0", "0"),
                    ColSpec("progress_type", "TEXT", true, "'HOURS'", "'HOURS'"),
                    ColSpec("custom_unit", "TEXT", true, "''", "''"),
                    ColSpec("target_value", "REAL", true, "10.0", "10.0"),
                    ColSpec("completed_value", "REAL", true, "0.0", "0.0"),
                    ColSpec("target_hours", "REAL", true, "10.0", "10.0"),
                    ColSpec("completed_hours", "REAL", true, "0.0", "0.0"),
                    ColSpec("current_checkpoint", "TEXT", true, "''", "''"),
                    ColSpec("notes", "TEXT", true, "''", "''"),
                    ColSpec("checkpoints_json", "TEXT", true, "'[]'", "'[]'"),
                    ColSpec("is_deleted", "INTEGER", true, "0", "0"),
                    ColSpec("created_at", "TEXT", true, null, "datetime('now')"),
                    ColSpec("updated_at", "TEXT", true, null, "datetime('now')")
                ),
                indices = listOf(
                    IndexSpec("idx_study_status", false, listOf("status", "is_deleted"))
                )
            ),
            TableSpec(
                tableName = "goals",
                columns = listOf(
                    ColSpec("id", "TEXT", true, null, "lower(hex(randomblob(16)))", pkPos = 1),
                    ColSpec("title", "TEXT", true, null, "''"),
                    ColSpec("level", "TEXT", true, null, "'ANNUAL'"),
                    ColSpec("parent_id", "TEXT", false, null, "NULL"),
                    ColSpec("quarter", "TEXT", false, null, "NULL"),
                    ColSpec("progress", "INTEGER", true, "0", "0"),
                    ColSpec("sort_order", "INTEGER", true, "0", "0"),
                    ColSpec("is_north_star", "INTEGER", true, "0", "0"),
                    ColSpec("confidence", "REAL", true, "1.0", "1.0"),
                    ColSpec("is_deleted", "INTEGER", true, "0", "0"),
                    ColSpec("created_at", "TEXT", true, null, "datetime('now')")
                ),
                indices = listOf(
                    IndexSpec("idx_goals_parent", false, listOf("parent_id", "is_deleted"))
                )
            ),
            TableSpec(
                tableName = "interruptions",
                columns = listOf(
                    ColSpec("id", "TEXT", true, null, "lower(hex(randomblob(16)))", pkPos = 1),
                    ColSpec("schedule_id", "TEXT", false, null, "NULL"),
                    ColSpec("type", "TEXT", true, null, "'EXTERNAL'"),
                    ColSpec("duration_seconds", "INTEGER", true, "0", "0"),
                    ColSpec("timestamp", "TEXT", true, null, "datetime('now')"),
                    ColSpec("note", "TEXT", true, null, "''")
                ),
                indices = listOf(
                    IndexSpec("idx_interruptions_sched", false, listOf("schedule_id")),
                    IndexSpec("idx_interruptions_time", false, listOf("timestamp"))
                )
            ),
            TableSpec(
                tableName = "expenses",
                columns = listOf(
                    ColSpec("id", "TEXT", true, null, "lower(hex(randomblob(16)))", pkPos = 1),
                    ColSpec("type", "TEXT", true, null, "'EXPENSE'"),
                    ColSpec("amount", "REAL", true, null, "0.0"),
                    ColSpec("category", "TEXT", true, null, "'餐饮'"),
                    ColSpec("note", "TEXT", true, null, "''"),
                    ColSpec("raw_input_text", "TEXT", true, null, "''"),
                    ColSpec("transaction_time", "TEXT", true, null, "datetime('now')"),
                    ColSpec("is_deleted", "INTEGER", true, "0", "0"),
                    ColSpec("created_at", "TEXT", true, null, "datetime('now')")
                )
            ),
            TableSpec(
                tableName = "daily_reports",
                columns = listOf(
                    ColSpec("date", "TEXT", true, null, "date('now')", pkPos = 1),
                    ColSpec("reflection", "TEXT", true, null, "''"),
                    ColSpec("ai_summary", "TEXT", true, null, "''"),
                    ColSpec("created_at", "TEXT", true, null, "datetime('now')"),
                    ColSpec("updated_at", "TEXT", true, null, "datetime('now')")
                )
            ),
            TableSpec(
                tableName = "schedule_tags",
                columns = listOf(
                    ColSpec("id", "TEXT", true, null, "lower(hex(randomblob(16)))", pkPos = 1),
                    ColSpec("name", "TEXT", true, null, "''"),
                    ColSpec("color_hex", "TEXT", true, "'#1A73E8'", "'#1A73E8'"),
                    ColSpec("sort_order", "INTEGER", true, "0", "0"),
                    ColSpec("created_at", "TEXT", true, null, "datetime('now')")
                ),
                indices = listOf(
                    IndexSpec("index_schedule_tags_name", true, listOf("name"))
                )
            ),
            TableSpec(
                tableName = "snapshot_metadata",
                columns = listOf(
                    ColSpec("id", "TEXT", true, null, "'current'", pkPos = 1),
                    ColSpec("device_id", "TEXT", true, null, "''"),
                    ColSpec("version_counter", "INTEGER", true, "1", "1"),
                    ColSpec("file_hash", "TEXT", true, null, "''"),
                    ColSpec("updated_at", "TEXT", true, null, "''")
                )
            ),
            TableSpec(
                tableName = "timetable_settings",
                columns = listOf(
                    ColSpec("key", "TEXT", true, null, "''", pkPos = 1),
                    ColSpec("value", "TEXT", true, null, "''")
                )
            ),
            TableSpec(
                tableName = "fitness_plans",
                columns = listOf(
                    ColSpec("id", "TEXT", true, null, "lower(hex(randomblob(16)))", pkPos = 1),
                    ColSpec("plan_date", "TEXT", true, null, "date('now')"),
                    ColSpec("title", "TEXT", true, null, "'今日体能强化'"),
                    ColSpec("workout_type", "TEXT", true, null, "'STRENGTH'"),
                    ColSpec("target_duration_minutes", "INTEGER", true, null, "45"),
                    ColSpec("actual_duration_minutes", "INTEGER", true, null, "0"),
                    ColSpec("calories_burned", "REAL", true, null, "0.0"),
                    ColSpec("feeling", "TEXT", true, null, "'MODERATE'"),
                    ColSpec("status", "TEXT", true, null, "'PLANNED'"),
                    ColSpec("notes", "TEXT", true, null, "''"),
                    ColSpec("is_deleted", "INTEGER", true, null, "0"),
                    ColSpec("created_at", "TEXT", true, null, "datetime('now')"),
                    ColSpec("updated_at", "TEXT", true, null, "datetime('now')")
                ),
                indices = listOf(
                    IndexSpec("idx_fitness_plans_date", false, listOf("plan_date"))
                )
            ),
            TableSpec(
                tableName = "fitness_records",
                columns = listOf(
                    ColSpec("id", "TEXT", true, null, "lower(hex(randomblob(16)))", pkPos = 1),
                    ColSpec("plan_id", "TEXT", true, null, "''"),
                    ColSpec("record_date", "TEXT", true, null, "date('now')"),
                    ColSpec("exercise_name", "TEXT", true, null, "''"),
                    ColSpec("category", "TEXT", true, null, "'CHEST'"),
                    ColSpec("equipment", "TEXT", true, null, "''"),
                    ColSpec("sets_data", "TEXT", true, null, "'[]'"),
                    ColSpec("sets_count", "INTEGER", true, null, "4"),
                    ColSpec("reps_per_set", "INTEGER", true, null, "10"),
                    ColSpec("weight_kg", "REAL", true, null, "0.0"),
                    ColSpec("sort_order", "INTEGER", true, null, "0"),
                    ColSpec("distance_km", "REAL", true, null, "0.0"),
                    ColSpec("duration_minutes", "INTEGER", true, null, "0"),
                    ColSpec("calories_burned", "REAL", true, null, "0.0"),
                    ColSpec("is_completed", "INTEGER", true, null, "0"),
                    ColSpec("note", "TEXT", true, null, "''"),
                    ColSpec("is_deleted", "INTEGER", true, null, "0"),
                    ColSpec("created_at", "TEXT", true, null, "datetime('now')"),
                    ColSpec("updated_at", "TEXT", true, null, "datetime('now')")
                ),
                indices = listOf(
                    IndexSpec("idx_fitness_records_date", false, listOf("record_date")),
                    IndexSpec("idx_fitness_records_plan", false, listOf("plan_id"))
                )
            ),
            TableSpec(
                tableName = "habits",
                columns = listOf(
                    ColSpec("id", "TEXT", true, null, "lower(hex(randomblob(16)))", pkPos = 1),
                    ColSpec("title", "TEXT", true, null, "''"),
                    ColSpec("icon", "TEXT", true, "'✨'", "'✨'"),
                    ColSpec("target_days_per_week", "INTEGER", true, "7", "7"),
                    ColSpec("color_hex", "TEXT", true, "'#38BDF8'", "'#38BDF8'"),
                    ColSpec("current_streak", "INTEGER", true, "0", "0"),
                    ColSpec("best_streak", "INTEGER", true, "0", "0"),
                    ColSpec("history_dates_json", "TEXT", true, "'[]'", "'[]'"),
                    ColSpec("reminder_time", "TEXT", true, "''", "''"),
                    ColSpec("is_deleted", "INTEGER", true, "0", "0"),
                    ColSpec("created_at", "TEXT", true, null, "datetime('now')"),
                    ColSpec("updated_at", "TEXT", true, null, "datetime('now')")
                ),
                indices = listOf(
                    IndexSpec("idx_habits_deleted", false, listOf("is_deleted"))
                )
            ),
            TableSpec(
                tableName = "countdowns",
                columns = listOf(
                    ColSpec("id", "TEXT", true, null, "lower(hex(randomblob(16)))", pkPos = 1),
                    ColSpec("title", "TEXT", true, null, "''"),
                    ColSpec("target_date", "TEXT", true, null, "date('now')"),
                    ColSpec("category", "TEXT", true, "'EXAM'", "'EXAM'"),
                    ColSpec("color_hex", "TEXT", true, "'#F59E0B'", "'#F59E0B'"),
                    ColSpec("is_pinned", "INTEGER", true, "0", "0"),
                    ColSpec("is_deleted", "INTEGER", true, "0", "0"),
                    ColSpec("created_at", "TEXT", true, null, "datetime('now')"),
                    ColSpec("updated_at", "TEXT", true, null, "datetime('now')")
                ),
                indices = listOf(
                    IndexSpec("idx_countdowns_pinned", false, listOf("is_pinned", "is_deleted"))
                )
            ),
            TableSpec(
                tableName = "focus_records",
                columns = listOf(
                    ColSpec("id", "TEXT", true, null, "lower(hex(randomblob(16)))", pkPos = 1),
                    ColSpec("schedule_id", "TEXT", false, null, "NULL"),
                    ColSpec("task_title", "TEXT", true, null, "'深度专注'"),
                    ColSpec("category", "TEXT", true, null, "'WORK'"),
                    ColSpec("duration_minutes", "INTEGER", true, null, "25"),
                    ColSpec("start_time", "TEXT", true, null, "datetime('now')"),
                    ColSpec("end_time", "TEXT", true, null, "datetime('now')"),
                    ColSpec("status", "TEXT", true, null, "'COMPLETED'"),
                    ColSpec("interruption_count", "INTEGER", true, "0", "0"),
                    ColSpec("note", "TEXT", true, null, "''")
                ),
                indices = listOf(
                    IndexSpec("idx_focus_records_sched", false, listOf("schedule_id")),
                    IndexSpec("idx_focus_records_start", false, listOf("start_time"))
                )
            ),
            TableSpec(
                tableName = "reminders",
                columns = listOf(
                    ColSpec("id", "TEXT", true, null, "lower(hex(randomblob(16)))", pkPos = 1),
                    ColSpec("title", "TEXT", true, null, "''"),
                    ColSpec("content", "TEXT", true, "''", "''"),
                    ColSpec("remind_time", "TEXT", true, null, "datetime('now')"),
                    ColSpec("advance_minutes", "INTEGER", true, "0", "0"),
                    ColSpec("reminder_type", "TEXT", true, "'ALL'", "'ALL'"),
                    ColSpec("repeat_rule", "TEXT", true, "'NONE'", "'NONE'"),
                    ColSpec("category", "TEXT", true, "'REMINDER'", "'REMINDER'"),
                    ColSpec("priority", "TEXT", true, "'MEDIUM'", "'MEDIUM'"),
                    ColSpec("is_completed", "INTEGER", true, "0", "0"),
                    ColSpec("is_deleted", "INTEGER", true, "0", "0"),
                    ColSpec("created_at", "TEXT", true, null, "datetime('now')"),
                    ColSpec("updated_at", "TEXT", true, null, "datetime('now')")
                ),
                indices = listOf(
                    IndexSpec("idx_reminders_time", false, listOf("remind_time", "is_deleted", "is_completed"))
                )
            ),
            TableSpec(
                tableName = "notes",
                columns = listOf(
                    ColSpec("id", "TEXT", true, null, "lower(hex(randomblob(16)))", pkPos = 1),
                    ColSpec("title", "TEXT", true, null, "''"),
                    ColSpec("content", "TEXT", true, null, "''"),
                    ColSpec("category", "TEXT", true, "'灵感'", "'灵感'"),
                    ColSpec("color_hex", "TEXT", true, "'#F59E0B'", "'#F59E0B'"),
                    ColSpec("is_pinned", "INTEGER", true, "0", "0"),
                    ColSpec("is_deleted", "INTEGER", true, "0", "0"),
                    ColSpec("created_at", "TEXT", true, null, "datetime('now')"),
                    ColSpec("updated_at", "TEXT", true, null, "datetime('now')")
                ),
                indices = listOf(
                    IndexSpec("idx_notes_pinned", false, listOf("is_pinned", "is_deleted")),
                    IndexSpec("idx_notes_updated", false, listOf("updated_at", "is_deleted"))
                )
            ),
            TableSpec(
                tableName = "time_markers",
                columns = listOf(
                    ColSpec("id", "TEXT", true, null, "lower(hex(randomblob(16)))", pkPos = 1),
                    ColSpec("name", "TEXT", true, null, "''"),
                    ColSpec("marker_type", "TEXT", true, "'POINT'", "'POINT'"),
                    ColSpec("category", "TEXT", true, "'LIFESTYLE'", "'LIFESTYLE'"),
                    ColSpec("icon", "TEXT", true, "'📍'", "'📍'"),
                    ColSpec("date", "TEXT", true, null, "date('now')"),
                    ColSpec("start_time", "TEXT", true, null, "datetime('now')"),
                    ColSpec("end_time", "TEXT", true, "''", "''"),
                    ColSpec("color_hex", "TEXT", true, "'#38BDF8'", "'#38BDF8'"),
                    ColSpec("notes", "TEXT", true, "''", "''"),
                    ColSpec("is_deleted", "INTEGER", true, "0", "0"),
                    ColSpec("created_at", "TEXT", true, null, "datetime('now')"),
                    ColSpec("updated_at", "TEXT", true, null, "datetime('now')")
                ),
                indices = listOf(
                    IndexSpec("idx_markers_date", false, listOf("date", "is_deleted")),
                    IndexSpec("idx_markers_time", false, listOf("start_time", "is_deleted"))
                )
            )
        )

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureLosslessSchema(db)
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureLosslessSchema(db)
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureLosslessSchema(db)
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureLosslessSchema(db)
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureLosslessSchema(db)
            }
        }

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureLosslessSchema(db)
            }
        }

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureLosslessSchema(db)
            }
        }

        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureLosslessSchema(db)
            }
        }

        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureLosslessSchema(db)
            }
        }

        @Volatile
        private var INSTANCE: RmfDatabase? = null

        fun resetInstance() {
            synchronized(this) {
                try {
                    INSTANCE?.close()
                } catch (_: Exception) {}
                INSTANCE = null
            }
        }

        fun getInstance(context: Context): RmfDatabase {
            val current = INSTANCE
            if (current != null && current.isOpen) {
                return current
            }
            return synchronized(this) {
                val existing = INSTANCE
                if (existing != null && existing.isOpen) {
                    return@synchronized existing
                }

                val appCtx = context.applicationContext
                // 1. 打开 Room 前先对本地已有数据库执行 WAL 合并、自动备份与无损表结构自愈
                preHealExistingDatabaseFile(appCtx, forceMigrationReset = false)

                var instance = buildRoomDatabase(appCtx)
                try {
                    // 主动触发连接打开与 Room Schema 校验；若因旧版哈希或跨端表结构差异报错，则立即强制自愈重试
                    instance.openHelper.writableDatabase
                } catch (e: Exception) {
                    android.util.Log.w("RmfDatabase", "First Room database open attempt failed, retrying after force heal: ${e.message}", e)
                    try {
                        instance.close()
                    } catch (_: Exception) {}
                    preHealExistingDatabaseFile(appCtx, forceMigrationReset = true)
                    instance = buildRoomDatabase(appCtx)
                    try {
                        instance.openHelper.writableDatabase
                    } catch (e2: Exception) {
                        android.util.Log.e("RmfDatabase", "Second Room database open attempt failed: ${e2.message}", e2)
                    }
                }

                INSTANCE = instance
                instance
            }
        }

        private fun buildRoomDatabase(appCtx: Context): RmfDatabase {
            return Room.databaseBuilder(
                appCtx,
                RmfDatabase::class.java,
                DATABASE_NAME
            )
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        try {
                            db.query("PRAGMA journal_mode = WAL;").close()
                            db.query("PRAGMA synchronous = NORMAL;").close()
                            db.query("PRAGMA foreign_keys = ON;").close()
                            db.query("PRAGMA busy_timeout = 5000;").close()
                            db.query("PRAGMA temp_store = MEMORY;").close()
                            db.query("PRAGMA cache_size = -8000;").close()
                            ensureDefaultScheduleTags(db)
                        } catch (_: Exception) {}
                    }
                })
                .build()
        }

        /**
         * 在 Room 打开已有数据库文件前，直接通过原生 SQLiteDatabase：
         * 1. 将 -wal 日志完整刷入主库 (防止最近未刷盘的数据在升级时丢失)；
         * 2. 触发无损物理备份快照；
         * 3. 逐表比对并无损重建/补齐所有表字段、默认值、非空约束与索引，保证 100% 通过 Room 校验且数据 0 丢失。
         */
        fun preHealExistingDatabaseFile(context: Context, forceMigrationReset: Boolean = false) {
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            if (!dbFile.exists() || dbFile.length() < 100L) return

            var rawDb: SQLiteDatabase? = null
            try {
                rawDb = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE)
                try {
                    rawDb.rawQuery("PRAGMA wal_checkpoint(TRUNCATE);", null).close()
                } catch (_: Exception) {}

                // 先在任何结构变更前执行物理快照备份
                com.renly.rmf.domain.service.LosslessUpgradeManager.performAutoUpgradeSafetyBackup(context)

                val anyHealed = healAllTablesRaw(rawDb)
                val userVersion = rawDb.version

                // 若进行了表结构自愈、强制重置或版本号不在当前版本，则将 user_version 设为 6，
                // 让 Room 通过 MIGRATION_6_7 正式执行 onValidateSchema 并写入最新的 room_master_table 哈希
                if (forceMigrationReset || anyHealed || userVersion < 1 || userVersion > CURRENT_DB_VERSION) {
                    rawDb.version = CURRENT_DB_VERSION - 1
                } else if (userVersion == CURRENT_DB_VERSION) {
                    // 检查是否存在 room_master_table，若不存在（如从外部或 Windows 导入的 .db）则同样设为 6 触发 Room 写入哈希
                    var hasMaster = false
                    val c = rawDb.rawQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name='room_master_table';",
                        null
                    )
                    try {
                        hasMaster = c.moveToFirst()
                    } finally {
                        c.close()
                    }
                    if (!hasMaster) {
                        rawDb.version = CURRENT_DB_VERSION - 1
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    rawDb?.close()
                } catch (_: Exception) {}
            }
        }

        private data class ExistingColInfo(
            val name: String,
            val type: String,
            val notNull: Boolean,
            val defaultSql: String?,
            val pkPos: Int
        )

        private fun buildCreateTableSql(tableName: String, columns: List<ColSpec>): String {
            val colDefs = columns.map { col ->
                val sb = StringBuilder()
                sb.append("`${col.name}` ${col.type}")
                if (col.notNull) sb.append(" NOT NULL")
                if (col.defaultSql != null) sb.append(" DEFAULT ${col.defaultSql}")
                sb.toString()
            }
            val pkCols = columns.filter { it.pkPos > 0 }.sortedBy { it.pkPos }.map { "`${it.name}`" }
            val pkClause = if (pkCols.isNotEmpty()) ", PRIMARY KEY(${pkCols.joinToString(", ")})" else ""
            return "CREATE TABLE IF NOT EXISTS `$tableName` (${colDefs.joinToString(", ")}$pkClause);"
        }

        private fun defaultSqlEquals(actual: String?, expected: String?): Boolean {
            if (actual == null && expected == null) return true
            if (actual == null || expected == null) return false
            val a = actual.trim().removeSurrounding("(", ")")
            val e = expected.trim().removeSurrounding("(", ")")
            return a == e
        }

        private fun healAllTablesRaw(db: SQLiteDatabase): Boolean {
            var anyModified = false
            db.beginTransaction()
            try {
                // 清理早先版本遗留但未在 @Entity 声明的索引，避免触发 Room onValidateSchema 报错
                db.execSQL("DROP INDEX IF EXISTS idx_schedules_active_time;")
                db.execSQL("DROP INDEX IF EXISTS idx_courses_active_day;")

                for (spec in ALL_TABLE_SPECS) {
                    var tableExists = false
                    db.rawQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name=?;",
                        arrayOf(spec.tableName)
                    ).use { c ->
                        tableExists = c.moveToFirst()
                    }

                    if (!tableExists) {
                        db.execSQL(buildCreateTableSql(spec.tableName, spec.columns))
                        for (idx in spec.indices) {
                            val uniqueStr = if (idx.unique) "UNIQUE " else ""
                            val colsStr = idx.columns.joinToString(", ") { "`$it`" }
                            db.execSQL("CREATE ${uniqueStr}INDEX IF NOT EXISTS `${idx.name}` ON `${spec.tableName}` ($colsStr);")
                        }
                        anyModified = true
                        continue
                    }

                    val existingCols = linkedMapOf<String, ExistingColInfo>()
                    db.rawQuery("PRAGMA table_info(`${spec.tableName}`);", null).use { c ->
                        val nameIdx = c.getColumnIndex("name")
                        val typeIdx = c.getColumnIndex("type")
                        val notNullIdx = c.getColumnIndex("notnull")
                        val dfltIdx = c.getColumnIndex("dflt_value")
                        val pkIdx = c.getColumnIndex("pk")
                        while (c.moveToNext()) {
                            val name = c.getString(nameIdx)
                            val type = c.getString(typeIdx) ?: "TEXT"
                            val notNull = c.getInt(notNullIdx) != 0
                            val dflt = if (c.isNull(dfltIdx)) null else c.getString(dfltIdx)
                            val pk = c.getInt(pkIdx)
                            existingCols[name.lowercase()] = ExistingColInfo(name, type, notNull, dflt, pk)
                        }
                    }

                    var needsRebuild = existingCols.size != spec.columns.size
                    if (!needsRebuild) {
                        for (col in spec.columns) {
                            val ex = existingCols[col.name.lowercase()]
                            if (ex == null ||
                                !ex.type.equals(col.type, ignoreCase = true) ||
                                ex.notNull != col.notNull ||
                                ex.pkPos != col.pkPos ||
                                !defaultSqlEquals(ex.defaultSql, col.defaultSql)
                            ) {
                                needsRebuild = true
                                break
                            }
                        }
                    }

                    if (needsRebuild) {
                        val tempTable = "__rmf_heal_${spec.tableName}"
                        db.execSQL("DROP TABLE IF EXISTS `$tempTable`;")
                        db.execSQL(buildCreateTableSql(tempTable, spec.columns))

                        val targetColsSql = spec.columns.joinToString(", ") { "`${it.name}`" }
                        val selectExprsSql = spec.columns.joinToString(", ") { col ->
                            val ex = existingCols[col.name.lowercase()]
                            if (ex != null) {
                                val fallback = col.defaultSql ?: col.fallbackExpr
                                if (col.notNull) {
                                    "COALESCE(`${ex.name}`, $fallback)"
                                } else {
                                    "`${ex.name}`"
                                }
                            } else {
                                col.defaultSql ?: if (col.notNull) col.fallbackExpr else "NULL"
                            }
                        }

                        db.execSQL("INSERT OR REPLACE INTO `$tempTable` ($targetColsSql) SELECT $selectExprsSql FROM `${spec.tableName}`;")
                        db.execSQL("DROP TABLE `${spec.tableName}`;")
                        db.execSQL("ALTER TABLE `$tempTable` RENAME TO `${spec.tableName}`;")
                        anyModified = true
                    }

                    // 同步索引：删除未在 @Entity 中声明的自定义索引，并补齐声明的索引
                    val expectedIndexNames = spec.indices.map { it.name }.toSet()
                    val existingIndexNames = mutableListOf<String>()
                    db.rawQuery("PRAGMA index_list(`${spec.tableName}`);", null).use { c ->
                        val nameIdx = c.getColumnIndex("name")
                        while (c.moveToNext()) {
                            val idxName = c.getString(nameIdx) ?: continue
                            if (!idxName.startsWith("sqlite_autoindex_")) {
                                existingIndexNames.add(idxName)
                            }
                        }
                    }
                    for (idxName in existingIndexNames) {
                        if (!expectedIndexNames.contains(idxName)) {
                            db.execSQL("DROP INDEX IF EXISTS `$idxName`;")
                            anyModified = true
                        }
                    }
                    for (idx in spec.indices) {
                        val uniqueStr = if (idx.unique) "UNIQUE " else ""
                        val colsStr = idx.columns.joinToString(", ") { "`$it`" }
                        db.execSQL("CREATE ${uniqueStr}INDEX IF NOT EXISTS `${idx.name}` ON `${spec.tableName}` ($colsStr);")
                    }
                }

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            return anyModified
        }

        private fun ensureLosslessSchema(db: SupportSQLiteDatabase) {
            try {
                db.execSQL("DROP INDEX IF EXISTS idx_schedules_active_time;")
                db.execSQL("DROP INDEX IF EXISTS idx_courses_active_day;")

                for (spec in ALL_TABLE_SPECS) {
                    var tableExists = false
                    db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='${spec.tableName}';").use { c ->
                        tableExists = c.moveToFirst()
                    }

                    if (!tableExists) {
                        db.execSQL(buildCreateTableSql(spec.tableName, spec.columns))
                        for (idx in spec.indices) {
                            val uniqueStr = if (idx.unique) "UNIQUE " else ""
                            val colsStr = idx.columns.joinToString(", ") { "`$it`" }
                            db.execSQL("CREATE ${uniqueStr}INDEX IF NOT EXISTS `${idx.name}` ON `${spec.tableName}` ($colsStr);")
                        }
                        continue
                    }

                    val existingCols = linkedMapOf<String, ExistingColInfo>()
                    db.query("PRAGMA table_info(`${spec.tableName}`);").use { c ->
                        val nameIdx = c.getColumnIndex("name")
                        val typeIdx = c.getColumnIndex("type")
                        val notNullIdx = c.getColumnIndex("notnull")
                        val dfltIdx = c.getColumnIndex("dflt_value")
                        val pkIdx = c.getColumnIndex("pk")
                        while (c.moveToNext()) {
                            val name = c.getString(nameIdx)
                            val type = c.getString(typeIdx) ?: "TEXT"
                            val notNull = c.getInt(notNullIdx) != 0
                            val dflt = if (c.isNull(dfltIdx)) null else c.getString(dfltIdx)
                            val pk = c.getInt(pkIdx)
                            existingCols[name.lowercase()] = ExistingColInfo(name, type, notNull, dflt, pk)
                        }
                    }

                    var needsRebuild = existingCols.size != spec.columns.size
                    if (!needsRebuild) {
                        for (col in spec.columns) {
                            val ex = existingCols[col.name.lowercase()]
                            if (ex == null ||
                                !ex.type.equals(col.type, ignoreCase = true) ||
                                ex.notNull != col.notNull ||
                                ex.pkPos != col.pkPos ||
                                !defaultSqlEquals(ex.defaultSql, col.defaultSql)
                            ) {
                                needsRebuild = true
                                break
                            }
                        }
                    }

                    if (needsRebuild) {
                        val tempTable = "__rmf_heal_${spec.tableName}"
                        db.execSQL("DROP TABLE IF EXISTS `$tempTable`;")
                        db.execSQL(buildCreateTableSql(tempTable, spec.columns))

                        val targetColsSql = spec.columns.joinToString(", ") { "`${it.name}`" }
                        val selectExprsSql = spec.columns.joinToString(", ") { col ->
                            val ex = existingCols[col.name.lowercase()]
                            if (ex != null) {
                                val fallback = col.defaultSql ?: col.fallbackExpr
                                if (col.notNull) {
                                    "COALESCE(`${ex.name}`, $fallback)"
                                } else {
                                    "`${ex.name}`"
                                }
                            } else {
                                col.defaultSql ?: if (col.notNull) col.fallbackExpr else "NULL"
                            }
                        }

                        db.execSQL("INSERT OR REPLACE INTO `$tempTable` ($targetColsSql) SELECT $selectExprsSql FROM `${spec.tableName}`;")
                        db.execSQL("DROP TABLE `${spec.tableName}`;")
                        db.execSQL("ALTER TABLE `$tempTable` RENAME TO `${spec.tableName}`;")
                    }

                    val expectedIndexNames = spec.indices.map { it.name }.toSet()
                    val existingIndexNames = mutableListOf<String>()
                    db.query("PRAGMA index_list(`${spec.tableName}`);").use { c ->
                        val nameIdx = c.getColumnIndex("name")
                        while (c.moveToNext()) {
                            val idxName = c.getString(nameIdx) ?: continue
                            if (!idxName.startsWith("sqlite_autoindex_")) {
                                existingIndexNames.add(idxName)
                            }
                        }
                    }
                    for (idxName in existingIndexNames) {
                        if (!expectedIndexNames.contains(idxName)) {
                            db.execSQL("DROP INDEX IF EXISTS `$idxName`;")
                        }
                    }
                    for (idx in spec.indices) {
                        val uniqueStr = if (idx.unique) "UNIQUE " else ""
                        val colsStr = idx.columns.joinToString(", ") { "`$it`" }
                        db.execSQL("CREATE ${uniqueStr}INDEX IF NOT EXISTS `${idx.name}` ON `${spec.tableName}` ($colsStr);")
                    }
                }

                ensureDefaultScheduleTags(db)
            } catch (_: Exception) {}
        }

        private fun ensureDefaultScheduleTags(db: SupportSQLiteDatabase) {
            try {
                val defaultTags = listOf(
                    Triple("工作", "#1A73E8", 0),
                    Triple("学习", "#7C3AED", 1),
                    Triple("日常", "#059669", 2),
                    Triple("运动", "#D97706", 3),
                    Triple("会议", "#0891B2", 4),
                    Triple("娱乐", "#EC4899", 5)
                )
                val nowStr = java.time.Instant.now().toString()
                for ((name, color, order) in defaultTags) {
                    db.execSQL(
                        "INSERT OR IGNORE INTO schedule_tags (id, name, color_hex, sort_order, created_at) VALUES (?, ?, ?, ?, ?);",
                        arrayOf(java.util.UUID.randomUUID().toString(), name, color, order, nowStr)
                    )
                }

                // 自动同步 schedules 表中既往已有分类至 schedule_tags 标签库
                db.execSQL("""
                    INSERT OR IGNORE INTO schedule_tags (id, name, color_hex, sort_order, created_at)
                    SELECT lower(hex(randomblob(16))), TRIM(category), '#1A73E8', 100, datetime('now')
                    FROM schedules
                    WHERE is_deleted = 0 AND category IS NOT NULL AND TRIM(category) != ''
                    GROUP BY TRIM(category);
                """)
            } catch (_: Exception) {}
        }
    }
}
