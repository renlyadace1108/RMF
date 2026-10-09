package com.renly.rmf.data.local

import android.content.Context
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
        FitnessRecordEntity::class
    ],
    version = 3,
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
            db.execSQL("DELETE FROM snapshot_metadata;")
            db.execSQL("DELETE FROM timetable_settings WHERE key LIKE 'test_%';")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        const val DATABASE_NAME = "rmf.db"

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createFitnessTables(db)
                ensureLosslessSchema(db)
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE fitness_records ADD COLUMN equipment TEXT NOT NULL DEFAULT '';")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE fitness_records ADD COLUMN sets_data TEXT NOT NULL DEFAULT '[]';")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE fitness_records ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;")
                } catch (_: Exception) {}
                ensureLosslessSchema(db)
            }
        }

        private fun createFitnessTables(db: SupportSQLiteDatabase) {
            try {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS fitness_plans (
                        id TEXT PRIMARY KEY NOT NULL,
                        plan_date TEXT NOT NULL,
                        title TEXT NOT NULL,
                        workout_type TEXT NOT NULL,
                        target_duration_minutes INTEGER NOT NULL,
                        actual_duration_minutes INTEGER NOT NULL,
                        calories_burned REAL NOT NULL,
                        feeling TEXT NOT NULL,
                        status TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        is_deleted INTEGER NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    );
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_fitness_plans_date ON fitness_plans(plan_date);")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS fitness_records (
                        id TEXT PRIMARY KEY NOT NULL,
                        plan_id TEXT NOT NULL,
                        record_date TEXT NOT NULL,
                        exercise_name TEXT NOT NULL,
                        category TEXT NOT NULL,
                        equipment TEXT NOT NULL DEFAULT '',
                        sets_data TEXT NOT NULL DEFAULT '[]',
                        sets_count INTEGER NOT NULL,
                        reps_per_set INTEGER NOT NULL,
                        weight_kg REAL NOT NULL,
                        sort_order INTEGER NOT NULL DEFAULT 0,
                        distance_km REAL NOT NULL,
                        duration_minutes INTEGER NOT NULL,
                        calories_burned REAL NOT NULL,
                        is_completed INTEGER NOT NULL,
                        note TEXT NOT NULL,
                        is_deleted INTEGER NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    );
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_fitness_records_date ON fitness_records(record_date);")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_fitness_records_plan ON fitness_records(plan_id);")
            } catch (_: Exception) {}
        }

        @Volatile
        private var INSTANCE: RmfDatabase? = null

        fun getInstance(context: Context): RmfDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RmfDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .addCallback(object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            // 启用与 Windows 端完全对齐的高性能 WAL 模式与外键约束
                            try {
                                db.query("PRAGMA journal_mode = WAL;").close()
                                db.query("PRAGMA synchronous = NORMAL;").close()
                                db.query("PRAGMA foreign_keys = ON;").close()
                                db.query("PRAGMA busy_timeout = 5000;").close()

                                // 无损动态列检查与补齐（对齐 Windows 端 SQLite 数据库热升级防护）
                                ensureLosslessSchema(db)
                            } catch (_: Exception) {}
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private fun ensureLosslessSchema(db: SupportSQLiteDatabase) {
            try {
                // 确保健身计划与记录表结构存在
                createFitnessTables(db)
                // 1. 检查 schedules 表字段完整性
                ensureTableColumns(
                    db = db,
                    tableName = "schedules",
                    expectedColumns = mapOf(
                        "goal_id" to "TEXT",
                        "work_type" to "TEXT DEFAULT 'DEEP_WORK'",
                        "dod" to "TEXT",
                        "actual_minutes" to "INTEGER DEFAULT 0",
                        "interruption_minutes" to "INTEGER DEFAULT 0",
                        "is_deferred" to "INTEGER DEFAULT 0",
                        "is_backlog" to "INTEGER DEFAULT 0",
                        "sync_version" to "INTEGER DEFAULT 1",
                        "is_dirty" to "INTEGER DEFAULT 0",
                        "is_all_day" to "INTEGER DEFAULT 0",
                        "recurrence" to "TEXT DEFAULT 'NONE'",
                        "color_hex" to "TEXT DEFAULT ''",
                        "source" to "TEXT DEFAULT ''",
                        "study_topic_id" to "TEXT",
                        "is_tentative" to "INTEGER DEFAULT 0",
                        "is_locked" to "INTEGER DEFAULT 0",
                        "depends_on_task_id" to "TEXT",
                        "postpone_count" to "INTEGER DEFAULT 0",
                        "eisenhower_quadrant" to "TEXT DEFAULT 'Q2'"
                    )
                )

                // 2. 检查 courses 表字段完整性
                ensureTableColumns(
                    db = db,
                    tableName = "courses",
                    expectedColumns = mapOf(
                        "color_hex" to "TEXT DEFAULT '#0284C7'",
                        "is_custom_time" to "INTEGER DEFAULT 0",
                        "custom_start_time" to "TEXT",
                        "custom_end_time" to "TEXT",
                        "teacher" to "TEXT DEFAULT ''",
                        "location" to "TEXT DEFAULT ''"
                    )
                )

                // 3. 检查 fitness_records 表字段完整性 (包含 sets_data, equipment, sort_order)
                ensureTableColumns(
                    db = db,
                    tableName = "fitness_records",
                    expectedColumns = mapOf(
                        "equipment" to "TEXT DEFAULT ''",
                        "sets_data" to "TEXT DEFAULT '[]'",
                        "sort_order" to "INTEGER DEFAULT 0"
                    )
                )
            } catch (_: Exception) {}
        }

        private fun ensureTableColumns(
            db: SupportSQLiteDatabase,
            tableName: String,
            expectedColumns: Map<String, String>
        ) {
            val existingCols = mutableSetOf<String>()
            val cursor = db.query("PRAGMA table_info($tableName);")
            try {
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex != -1) {
                        existingCols.add(cursor.getString(nameIndex).lowercase())
                    }
                }
            } finally {
                cursor.close()
            }

            for ((colName, colDef) in expectedColumns) {
                if (!existingCols.contains(colName.lowercase())) {
                    try {
                        db.execSQL("ALTER TABLE $tableName ADD COLUMN $colName $colDef;")
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
