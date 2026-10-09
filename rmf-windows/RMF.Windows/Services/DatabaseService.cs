using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using Microsoft.Data.Sqlite;
using RMF.Windows.Models;

namespace RMF.Windows.Services;

public class DatabaseService
{
    public static readonly string DbDir = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        "RMF"
    );
    public static readonly string DbPath = Path.Combine(DbDir, "rmf.db");
    public static readonly string ConnectionString = $"Data Source={DbPath}";

    public static void Initialize()
    {
        if (!Directory.Exists(DbDir))
        {
            Directory.CreateDirectory(DbDir);
        }

        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        // 启用 WAL 预写日志与高性能 PRAGMA 配置
        using (var pragmaCmd = new SqliteCommand(@"
            PRAGMA journal_mode = WAL;
            PRAGMA synchronous = NORMAL;
            PRAGMA foreign_keys = ON;
            PRAGMA busy_timeout = 5000;
        ", conn))
        {
            pragmaCmd.ExecuteNonQuery();
        }

        string initSql = @"
            CREATE TABLE IF NOT EXISTS schedules (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                description TEXT,
                category TEXT,
                priority TEXT,
                status TEXT,
                start_time TEXT,
                end_time TEXT,
                estimated_minutes INTEGER,
                is_deleted INTEGER DEFAULT 0,
                created_at TEXT,
                updated_at TEXT
            );

            CREATE TABLE IF NOT EXISTS goals (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                level TEXT NOT NULL,
                parent_id TEXT,
                quarter TEXT,
                progress INTEGER DEFAULT 0,
                sort_order INTEGER DEFAULT 0,
                is_deleted INTEGER DEFAULT 0,
                created_at TEXT
            );

            CREATE TABLE IF NOT EXISTS expenses (
                id TEXT PRIMARY KEY,
                type TEXT,
                amount REAL,
                category TEXT,
                note TEXT,
                raw_input_text TEXT,
                transaction_time TEXT,
                is_deleted INTEGER DEFAULT 0,
                created_at TEXT
            );

            CREATE TABLE IF NOT EXISTS focus_logs (
                id TEXT PRIMARY KEY,
                process_name TEXT,
                window_title TEXT,
                duration_seconds INTEGER,
                timestamp TEXT
            );

            CREATE TABLE IF NOT EXISTS study_topics (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                category TEXT DEFAULT '技术栈',
                status TEXT DEFAULT 'IN_PROGRESS',
                progress_percent INTEGER DEFAULT 0,
                target_hours REAL DEFAULT 10.0,
                completed_hours REAL DEFAULT 0.0,
                current_checkpoint TEXT,
                notes TEXT,
                is_deleted INTEGER DEFAULT 0,
                created_at TEXT,
                updated_at TEXT
            );

            CREATE TABLE IF NOT EXISTS courses (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                teacher TEXT,
                location TEXT,
                day_of_week INTEGER NOT NULL,
                start_section INTEGER NOT NULL,
                section_span INTEGER DEFAULT 2,
                start_week INTEGER DEFAULT 1,
                end_week INTEGER DEFAULT 16,
                week_type TEXT DEFAULT 'ALL',
                color_hex TEXT DEFAULT '#3B82F6',
                notes TEXT,
                is_custom_time INTEGER DEFAULT 0,
                custom_start_time TEXT,
                custom_end_time TEXT,
                is_deleted INTEGER DEFAULT 0,
                created_at TEXT,
                updated_at TEXT
            );

            CREATE TABLE IF NOT EXISTS timetable_settings (
                key TEXT PRIMARY KEY,
                value TEXT
            );

            CREATE TABLE IF NOT EXISTS course_adjustments (
                id TEXT PRIMARY KEY,
                course_id TEXT,
                course_name TEXT,
                adjustment_type TEXT NOT NULL,
                target_week INTEGER NOT NULL,
                orig_day_of_week INTEGER NOT NULL,
                orig_start_section INTEGER NOT NULL,
                new_day_of_week INTEGER NOT NULL,
                new_start_section INTEGER NOT NULL,
                section_span INTEGER DEFAULT 2,
                new_location TEXT,
                reason TEXT,
                is_custom_time INTEGER DEFAULT 0,
                custom_start_time TEXT,
                custom_end_time TEXT,
                is_deleted INTEGER DEFAULT 0,
                created_at TEXT,
                updated_at TEXT
            );

            CREATE TABLE IF NOT EXISTS daily_reports (
                date TEXT PRIMARY KEY,
                reflection TEXT,
                ai_summary TEXT,
                created_at TEXT,
                updated_at TEXT
            );

            CREATE TABLE IF NOT EXISTS interruptions (
                id TEXT PRIMARY KEY,
                schedule_id TEXT,
                type TEXT NOT NULL,
                duration_seconds INTEGER DEFAULT 0,
                timestamp TEXT,
                note TEXT
            );

            CREATE TABLE IF NOT EXISTS snapshot_metadata (
                id TEXT PRIMARY KEY,
                device_id TEXT,
                version_counter INTEGER DEFAULT 1,
                file_hash TEXT,
                updated_at TEXT
            );

            CREATE TABLE IF NOT EXISTS schedule_tags (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL UNIQUE COLLATE NOCASE,
                color_hex TEXT DEFAULT '#1A73E8',
                sort_order INTEGER DEFAULT 0,
                created_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS fitness_plans (
                id TEXT PRIMARY KEY,
                plan_date TEXT NOT NULL,
                title TEXT NOT NULL,
                workout_type TEXT NOT NULL DEFAULT 'STRENGTH',
                target_duration_minutes INTEGER DEFAULT 45,
                actual_duration_minutes INTEGER DEFAULT 0,
                calories_burned REAL DEFAULT 0.0,
                feeling TEXT DEFAULT 'MODERATE',
                status TEXT DEFAULT 'PLANNED',
                notes TEXT DEFAULT '',
                is_deleted INTEGER DEFAULT 0,
                created_at TEXT,
                updated_at TEXT
            );

            CREATE TABLE IF NOT EXISTS fitness_records (
                id TEXT PRIMARY KEY,
                plan_id TEXT DEFAULT '',
                record_date TEXT NOT NULL,
                exercise_name TEXT NOT NULL,
                category TEXT DEFAULT 'CHEST',
                equipment TEXT DEFAULT '',
                sets_data TEXT DEFAULT '[]',
                sets_count INTEGER DEFAULT 4,
                reps_per_set INTEGER DEFAULT 10,
                weight_kg REAL DEFAULT 0.0,
                sort_order INTEGER DEFAULT 0,
                distance_km REAL DEFAULT 0.0,
                duration_minutes INTEGER DEFAULT 0,
                calories_burned REAL DEFAULT 0.0,
                is_completed INTEGER DEFAULT 0,
                note TEXT DEFAULT '',
                is_deleted INTEGER DEFAULT 0,
                created_at TEXT,
                updated_at TEXT
            );

            -- 高性能查询索引优化 (优化高频日期检索与未删除过滤)
            CREATE INDEX IF NOT EXISTS idx_schedules_time ON schedules (start_time, end_time, is_deleted);
            CREATE INDEX IF NOT EXISTS idx_schedules_status ON schedules (status, is_deleted);
            CREATE INDEX IF NOT EXISTS idx_courses_day_sec ON courses (day_of_week, start_section, is_deleted);
            CREATE INDEX IF NOT EXISTS idx_course_adj_week ON course_adjustments (target_week, is_deleted);
            CREATE INDEX IF NOT EXISTS idx_goals_parent ON goals (parent_id, is_deleted);
            CREATE INDEX IF NOT EXISTS idx_study_status ON study_topics (status, is_deleted);
            CREATE INDEX IF NOT EXISTS idx_interruptions_sched ON interruptions (schedule_id);
            CREATE INDEX IF NOT EXISTS idx_interruptions_time ON interruptions (timestamp);
            CREATE INDEX IF NOT EXISTS idx_fitness_plans_date ON fitness_plans (plan_date, is_deleted);
            CREATE INDEX IF NOT EXISTS idx_fitness_records_date ON fitness_records (record_date, is_deleted);
            CREATE INDEX IF NOT EXISTS idx_fitness_records_plan ON fitness_records (plan_id, is_deleted);
        ";

        using (var cmd = new SqliteCommand(initSql, conn))
        {
            cmd.ExecuteNonQuery();
        }

        // 动态迁移 schedules 表，追加核心扩展列
        MigrateSchedulesTable(conn);
        // 动态迁移 courses 和 course_adjustments 表，追加自定义具体时间与考期列
        MigrateCourseTables(conn);
        // 动态迁移 goals 表，追加北极星与置信度列
        MigrateGoalsTable(conn);
        // 动态迁移 study_topics 表，追加多维度进度支持 (学习时长/节数/页数/自定义单位)
        MigrateStudyTopicsTable(conn);
        // 动态迁移 fitness_records 表，追加 sets_data, equipment, sort_order 列
        MigrateFitnessTables(conn);
        // 初始化日程分类标签库并同步现有分类
        InitializeScheduleTags(conn);
    }

    private static void MigrateCourseTables(SqliteConnection conn)
    {
        try
        {
            var courseCols = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            using (var cmd = new SqliteCommand("PRAGMA table_info(courses);", conn))
            using (var reader = cmd.ExecuteReader())
            {
                while (reader.Read()) courseCols.Add(reader.GetString(1));
            }

            if (!courseCols.Contains("is_custom_time"))
            {
                using var c1 = new SqliteCommand("ALTER TABLE courses ADD COLUMN is_custom_time INTEGER DEFAULT 0;", conn);
                c1.ExecuteNonQuery();
            }
            if (!courseCols.Contains("custom_start_time"))
            {
                using var c2 = new SqliteCommand("ALTER TABLE courses ADD COLUMN custom_start_time TEXT;", conn);
                c2.ExecuteNonQuery();
            }
            if (!courseCols.Contains("custom_end_time"))
            {
                using var c3 = new SqliteCommand("ALTER TABLE courses ADD COLUMN custom_end_time TEXT;", conn);
                c3.ExecuteNonQuery();
            }
            if (!courseCols.Contains("exam_date"))
            {
                using var c4 = new SqliteCommand("ALTER TABLE courses ADD COLUMN exam_date TEXT;", conn);
                c4.ExecuteNonQuery();
            }
            if (!courseCols.Contains("course_url"))
            {
                using var c5 = new SqliteCommand("ALTER TABLE courses ADD COLUMN course_url TEXT;", conn);
                c5.ExecuteNonQuery();
            }
            if (!courseCols.Contains("room_code"))
            {
                using var c6 = new SqliteCommand("ALTER TABLE courses ADD COLUMN room_code TEXT;", conn);
                c6.ExecuteNonQuery();
            }

            var adjCols = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            using (var cmd = new SqliteCommand("PRAGMA table_info(course_adjustments);", conn))
            using (var reader = cmd.ExecuteReader())
            {
                while (reader.Read()) adjCols.Add(reader.GetString(1));
            }

            if (!adjCols.Contains("is_custom_time"))
            {
                using var c1 = new SqliteCommand("ALTER TABLE course_adjustments ADD COLUMN is_custom_time INTEGER DEFAULT 0;", conn);
                c1.ExecuteNonQuery();
            }
            if (!adjCols.Contains("custom_start_time"))
            {
                using var c2 = new SqliteCommand("ALTER TABLE course_adjustments ADD COLUMN custom_start_time TEXT;", conn);
                c2.ExecuteNonQuery();
            }
            if (!adjCols.Contains("custom_end_time"))
            {
                using var c3 = new SqliteCommand("ALTER TABLE course_adjustments ADD COLUMN custom_end_time TEXT;", conn);
                c3.ExecuteNonQuery();
            }
        }
        catch { }
    }

    private static void MigrateGoalsTable(SqliteConnection conn)
    {
        try
        {
            var goalCols = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            using (var cmd = new SqliteCommand("PRAGMA table_info(goals);", conn))
            using (var reader = cmd.ExecuteReader())
            {
                while (reader.Read()) goalCols.Add(reader.GetString(1));
            }

            if (!goalCols.Contains("is_north_star"))
            {
                using var c1 = new SqliteCommand("ALTER TABLE goals ADD COLUMN is_north_star INTEGER DEFAULT 0;", conn);
                c1.ExecuteNonQuery();
            }
            if (!goalCols.Contains("confidence"))
            {
                using var c2 = new SqliteCommand("ALTER TABLE goals ADD COLUMN confidence REAL DEFAULT 1.0;", conn);
                c2.ExecuteNonQuery();
            }
        }
        catch { }
    }

    private static void MigrateStudyTopicsTable(SqliteConnection conn)
    {
        try
        {
            var topicCols = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            using (var cmd = new SqliteCommand("PRAGMA table_info(study_topics);", conn))
            using (var reader = cmd.ExecuteReader())
            {
                while (reader.Read()) topicCols.Add(reader.GetString(1));
            }

            if (!topicCols.Contains("progress_type"))
            {
                using var c1 = new SqliteCommand("ALTER TABLE study_topics ADD COLUMN progress_type TEXT DEFAULT 'HOURS';", conn);
                c1.ExecuteNonQuery();
            }
            if (!topicCols.Contains("custom_unit"))
            {
                using var c2 = new SqliteCommand("ALTER TABLE study_topics ADD COLUMN custom_unit TEXT DEFAULT '';", conn);
                c2.ExecuteNonQuery();
            }
            if (!topicCols.Contains("target_value"))
            {
                using var c3 = new SqliteCommand("ALTER TABLE study_topics ADD COLUMN target_value REAL DEFAULT 10.0;", conn);
                c3.ExecuteNonQuery();
                // 兼容已有数据
                using var cSync = new SqliteCommand("UPDATE study_topics SET target_value = target_hours WHERE target_value IS NULL OR target_value = 0;", conn);
                cSync.ExecuteNonQuery();
            }
            if (!topicCols.Contains("completed_value"))
            {
                using var c4 = new SqliteCommand("ALTER TABLE study_topics ADD COLUMN completed_value REAL DEFAULT 0.0;", conn);
                c4.ExecuteNonQuery();
                // 兼容已有数据
                using var cSync2 = new SqliteCommand("UPDATE study_topics SET completed_value = completed_hours WHERE completed_value IS NULL OR completed_value = 0;", conn);
                cSync2.ExecuteNonQuery();
            }
        }
        catch { }
    }

    private static void MigrateSchedulesTable(SqliteConnection conn)
    {
        var existingCols = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        using (var cmd = new SqliteCommand("PRAGMA table_info(schedules);", conn))
        using (var reader = cmd.ExecuteReader())
        {
            while (reader.Read())
            {
                existingCols.Add(reader.GetString(1));
            }
        }

        var missingCols = new Dictionary<string, string>
        {
            ["goal_id"] = "TEXT",
            ["work_type"] = "TEXT DEFAULT 'DEEP_WORK'",
            ["dod"] = "TEXT",
            ["actual_minutes"] = "INTEGER DEFAULT 0",
            ["interruption_minutes"] = "INTEGER DEFAULT 0",
            ["is_deferred"] = "INTEGER DEFAULT 0",
            ["is_backlog"] = "INTEGER DEFAULT 0",
            ["sync_version"] = "INTEGER DEFAULT 1",
            ["is_dirty"] = "INTEGER DEFAULT 0",
            ["is_all_day"] = "INTEGER DEFAULT 0",
            ["recurrence"] = "TEXT DEFAULT 'NONE'",
            ["color_hex"] = "TEXT DEFAULT ''",
            ["source"] = "TEXT DEFAULT ''",
            ["study_topic_id"] = "TEXT",
            ["is_tentative"] = "INTEGER DEFAULT 0",
            ["is_locked"] = "INTEGER DEFAULT 0",
            ["depends_on_task_id"] = "TEXT",
            ["postpone_count"] = "INTEGER DEFAULT 0",
            ["eisenhower_quadrant"] = "TEXT DEFAULT 'Q2'"
        };

        foreach (var (col, def) in missingCols)
        {
            if (!existingCols.Contains(col))
            {
                try
                {
                    using var alterCmd = new SqliteCommand($"ALTER TABLE schedules ADD COLUMN {col} {def};", conn);
                    alterCmd.ExecuteNonQuery();
                }
                catch { }
            }
        }
    }

    private static void MigrateFitnessTables(SqliteConnection conn)
    {
        try
        {
            var cols = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            using (var cmd = new SqliteCommand("PRAGMA table_info(fitness_records);", conn))
            using (var reader = cmd.ExecuteReader())
            {
                while (reader.Read()) cols.Add(reader.GetString(1));
            }

            if (!cols.Contains("equipment"))
            {
                using var c1 = new SqliteCommand("ALTER TABLE fitness_records ADD COLUMN equipment TEXT DEFAULT '';", conn);
                c1.ExecuteNonQuery();
            }
            if (!cols.Contains("sets_data"))
            {
                using var c2 = new SqliteCommand("ALTER TABLE fitness_records ADD COLUMN sets_data TEXT DEFAULT '[]';", conn);
                c2.ExecuteNonQuery();
            }
            if (!cols.Contains("sort_order"))
            {
                using var c3 = new SqliteCommand("ALTER TABLE fitness_records ADD COLUMN sort_order INTEGER DEFAULT 0;", conn);
                c3.ExecuteNonQuery();
            }
        }
        catch { }
    }

    private static void InitializeScheduleTags(SqliteConnection conn)
    {
        try
        {
            // 确保默认日程标签已存在
            string[] defaultTags = { "工作", "学习", "日常", "运动", "会议", "娱乐" };
            string[] defaultColors = { "#1A73E8", "#7C3AED", "#059669", "#D97706", "#0891B2", "#EC4899" };
            for (int i = 0; i < defaultTags.Length; i++)
            {
                using var cmd = new SqliteCommand(@"
                    INSERT OR IGNORE INTO schedule_tags (id, name, color_hex, sort_order, created_at)
                    VALUES (@id, @name, @color, @order, @now);
                ", conn);
                cmd.Parameters.AddWithValue("@id", Guid.NewGuid().ToString("N"));
                cmd.Parameters.AddWithValue("@name", defaultTags[i]);
                cmd.Parameters.AddWithValue("@color", defaultColors[i]);
                cmd.Parameters.AddWithValue("@order", i);
                cmd.Parameters.AddWithValue("@now", DateTime.UtcNow.ToString("o"));
                cmd.ExecuteNonQuery();
            }

            // 自动同步 schedules 表中既往已有分类至 schedule_tags 标签库
            using var syncCmd = new SqliteCommand(@"
                INSERT OR IGNORE INTO schedule_tags (id, name, color_hex, sort_order, created_at)
                SELECT lower(hex(randomblob(16))), TRIM(category), '#1A73E8', 100, datetime('now')
                FROM schedules
                WHERE is_deleted = 0 AND category IS NOT NULL AND TRIM(category) != ''
                GROUP BY TRIM(category);
            ", conn);
            syncCmd.ExecuteNonQuery();
        }
        catch { }
    }

    /// <summary>
    /// 清空所有业务数据（日程、目标、学习档案、课程、调停课、每日汇报、专注监控、支出等），保留数据表结构与设置
    /// </summary>
    public static (bool success, string message) ClearAllData()
    {
        try
        {
            // 自动在本地数据库目录创建带有时间戳的 .bak 安全副本
            string backupPath = Path.Combine(DbDir, $"rmf_pre_clear_{DateTime.Now:yyyyMMddHHmmss}.bak");
            if (File.Exists(DbPath))
            {
                File.Copy(DbPath, backupPath, true);
            }

            Initialize(); // 确保所有可能的新增数据表已就绪
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();

            using var trans = conn.BeginTransaction();
            using var cmd = conn.CreateCommand();
            cmd.Transaction = trans;
            cmd.CommandText = @"
                DELETE FROM schedules;
                DELETE FROM goals;
                DELETE FROM study_topics;
                DELETE FROM courses;
                DELETE FROM course_adjustments;
                DELETE FROM daily_reports;
                DELETE FROM focus_logs;
                DELETE FROM expenses;
                DELETE FROM interruptions;
                DELETE FROM fitness_plans;
                DELETE FROM fitness_records;
                DELETE FROM snapshot_metadata;
                DELETE FROM timetable_settings WHERE key LIKE 'test_%';
            ";
            cmd.ExecuteNonQuery();
            trans.Commit();

            using var vacCmd = new SqliteCommand("VACUUM;", conn);
            vacCmd.ExecuteNonQuery();

            return (true, $"已清空所有测试业务数据，并已自动生成数据备份副本：{Path.GetFileName(backupPath)}");
        }
        catch (Exception ex)
        {
            return (false, $"清空数据失败: {ex.Message}");
        }
    }

    // ==================== 目标层级树 (Goal Hierarchy) ====================

    public static List<GoalItem> GetAllGoals()
    {
        var list = new List<GoalItem>();
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        string sql = @"
            SELECT id, title, level, parent_id, quarter, progress, sort_order, is_deleted, created_at,
                   is_north_star, confidence
            FROM goals
            WHERE is_deleted = 0
            ORDER BY sort_order ASC, created_at ASC;
        ";

        using var cmd = new SqliteCommand(sql, conn);
        using var reader = cmd.ExecuteReader();
        while (reader.Read())
        {
            list.Add(new GoalItem
            {
                Id = reader.GetString(0),
                Title = reader.GetString(1),
                Level = reader.GetString(2),
                ParentId = reader.IsDBNull(3) ? null : reader.GetString(3),
                Quarter = reader.IsDBNull(4) ? "2026-Q4" : reader.GetString(4),
                Progress = reader.IsDBNull(5) ? 0 : reader.GetInt32(5),
                SortOrder = reader.IsDBNull(6) ? 0 : reader.GetInt32(6),
                IsDeleted = reader.GetInt32(7) == 1,
                CreatedAt = DateTime.TryParse(reader.GetString(8), out var ca) ? ca : DateTime.UtcNow,
                IsNorthStar = reader.FieldCount > 9 && !reader.IsDBNull(9) && reader.GetInt32(9) == 1,
                Confidence = reader.FieldCount > 10 && !reader.IsDBNull(10) ? reader.GetDouble(10) : 1.0
            });
        }

        return list;
    }

    public static List<GoalItem> GetNorthStarGoals()
    {
        return GetAllGoals().Where(g => g.IsNorthStar).ToList();
    }

    public static GoalItem? GetGoalById(string id)
    {
        return GetAllGoals().FirstOrDefault(g => g.Id == id);
    }

    public static List<GoalItem> GetGoalsHierarchy()
    {
        var all = GetAllGoals();
        var map = all.ToDictionary(g => g.Id);
        var roots = new List<GoalItem>();

        foreach (var item in all)
        {
            if (!string.IsNullOrEmpty(item.ParentId) && map.TryGetValue(item.ParentId, out var parent))
            {
                parent.Children.Add(item);
            }
            else
            {
                roots.Add(item);
            }
        }

        return roots;
    }

    public static void UpsertGoal(GoalItem item)
    {
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        string sql = @"
            INSERT INTO goals (id, title, level, parent_id, quarter, progress, sort_order, is_deleted, created_at,
                               is_north_star, confidence)
            VALUES (@id, @title, @level, @parent_id, @quarter, @progress, @sort_order, 0, @now,
                    @is_north_star, @confidence)
            ON CONFLICT(id) DO UPDATE SET
                title = excluded.title,
                level = excluded.level,
                parent_id = excluded.parent_id,
                quarter = excluded.quarter,
                progress = excluded.progress,
                sort_order = excluded.sort_order,
                is_north_star = excluded.is_north_star,
                confidence = excluded.confidence;
        ";

        using var cmd = new SqliteCommand(sql, conn);
        cmd.Parameters.AddWithValue("@id", item.Id);
        cmd.Parameters.AddWithValue("@title", item.Title);
        cmd.Parameters.AddWithValue("@level", item.Level);
        cmd.Parameters.AddWithValue("@parent_id", (object?)item.ParentId ?? DBNull.Value);
        cmd.Parameters.AddWithValue("@quarter", item.Quarter ?? "2026-Q4");
        cmd.Parameters.AddWithValue("@progress", item.Progress);
        cmd.Parameters.AddWithValue("@sort_order", item.SortOrder);
        cmd.Parameters.AddWithValue("@is_north_star", item.IsNorthStar ? 1 : 0);
        cmd.Parameters.AddWithValue("@confidence", item.Confidence);
        cmd.Parameters.AddWithValue("@now", DateTime.UtcNow.ToString("s"));
        cmd.ExecuteNonQuery();
    }

    public static void DeleteGoal(string id)
    {
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        string sql = "UPDATE goals SET is_deleted = 1 WHERE id = @id OR parent_id = @id;";
        using var cmd = new SqliteCommand(sql, conn);
        cmd.Parameters.AddWithValue("@id", id);
        cmd.ExecuteNonQuery();
    }

    // ==================== 日程管理 (Schedules) ====================

    public static List<ScheduleItem> GetAllSchedules()
    {
        var list = new List<ScheduleItem>();
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        string sql = @"
            SELECT id, title, description, category, priority, status, start_time, end_time, estimated_minutes, is_deleted,
                   goal_id, work_type, dod, actual_minutes, interruption_minutes, is_deferred, is_backlog, sync_version, is_dirty,
                   is_all_day, recurrence, color_hex, source, study_topic_id
            FROM schedules
            WHERE is_deleted = 0
            ORDER BY start_time ASC;
        ";

        using var cmd = new SqliteCommand(sql, conn);
        using var reader = cmd.ExecuteReader();
        while (reader.Read())
        {
            list.Add(ReadScheduleFromReader(reader, DateTime.MinValue, DateTime.MaxValue));
        }

        return list;
    }

    public static List<ScheduleItem> GetTodaySchedules()
    {
        return GetSchedulesForDate(DateTime.Today);
    }

    public static List<ScheduleItem> GetSchedulesForDate(DateTime date)
    {
        return GetSchedulesForDateRange(date.Date, date.Date.AddDays(1));
    }

    public static List<ScheduleItem> GetSchedulesForDateRange(DateTime start, DateTime end)
    {
        var list = new List<ScheduleItem>();
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        string startIso = start.ToString("s");
        string endIso = end.ToString("s");

        string sql = @"
            SELECT id, title, description, category, priority, status, start_time, end_time, estimated_minutes, is_deleted,
                   goal_id, work_type, dod, actual_minutes, interruption_minutes, is_deferred, is_backlog, sync_version, is_dirty,
                   is_all_day, recurrence, color_hex, source, study_topic_id
            FROM schedules
            WHERE is_deleted = 0 AND is_backlog = 0 AND is_deferred = 0 
              AND (
                  (start_time < @end AND end_time >= @start)
                  OR (recurrence IS NOT NULL AND recurrence != 'NONE' AND recurrence != '' AND start_time < @end)
              )
            ORDER BY start_time ASC;
        ";

        using var cmd = new SqliteCommand(sql, conn);
        cmd.Parameters.AddWithValue("@start", startIso);
        cmd.Parameters.AddWithValue("@end", endIso);

        var directEvents = new List<ScheduleItem>();
        using var reader = cmd.ExecuteReader();
        while (reader.Read())
        {
            directEvents.Add(ReadScheduleFromReader(reader, start, end));
        }

        // 展开重复事件并按起止时间投影
        var projected = new List<ScheduleItem>();
        foreach (var item in directEvents)
        {
            if (string.IsNullOrEmpty(item.Recurrence) || item.Recurrence == "NONE")
            {
                if (item.StartTime < end && item.EndTime >= start)
                {
                    projected.Add(item);
                }
            }
            else
            {
                // 投影重复事件
                TimeSpan duration = item.EndTime - item.StartTime;
                for (DateTime d = start.Date; d < end.Date; d = d.AddDays(1))
                {
                    if (d < item.StartTime.Date) continue;

                    bool matches = item.Recurrence switch
                    {
                        "DAILY" => true,
                        "WEEKDAYS" => d.DayOfWeek >= DayOfWeek.Monday && d.DayOfWeek <= DayOfWeek.Friday,
                        "WEEKLY" => d.DayOfWeek == item.StartTime.DayOfWeek,
                        "MONTHLY" => d.Day == item.StartTime.Day,
                        _ => false
                    };

                    if (matches)
                    {
                        if (d == item.StartTime.Date)
                        {
                            projected.Add(item);
                        }
                        else
                        {
                            var clone = new ScheduleItem
                            {
                                Id = item.Id,
                                Title = item.Title,
                                Description = item.Description,
                                Category = item.Category,
                                Priority = item.Priority,
                                Status = item.Status,
                                StartTime = d.Add(item.StartTime.TimeOfDay),
                                EndTime = d.Add(item.StartTime.TimeOfDay).Add(duration),
                                EstimatedMinutes = item.EstimatedMinutes,
                                GoalId = item.GoalId,
                                WorkType = item.WorkType,
                                Dod = item.Dod,
                                ActualMinutes = item.ActualMinutes,
                                InterruptionMinutes = item.InterruptionMinutes,
                                IsDeferred = item.IsDeferred,
                                IsBacklog = item.IsBacklog,
                                SyncVersion = item.SyncVersion,
                                IsDirty = item.IsDirty,
                                IsAllDay = item.IsAllDay,
                                Recurrence = item.Recurrence,
                                ColorHex = item.ColorHex,
                                Source = item.Source,
                                StudyTopicId = item.StudyTopicId
                            };
                            projected.Add(clone);
                        }
                    }
                }
            }
        }

        return projected.OrderBy(e => e.StartTime).ToList();
    }

    public static List<ScheduleItem> GetBacklogSchedules()
    {
        var list = new List<ScheduleItem>();
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        string sql = @"
            SELECT id, title, description, category, priority, status, start_time, end_time, estimated_minutes, is_deleted,
                   goal_id, work_type, dod, actual_minutes, interruption_minutes, is_deferred, is_backlog, sync_version, is_dirty,
                   is_all_day, recurrence, color_hex, source, study_topic_id
            FROM schedules
            WHERE is_deleted = 0 AND is_backlog = 1 AND is_deferred = 0
            ORDER BY priority DESC, created_at DESC;
        ";

        using var cmd = new SqliteCommand(sql, conn);
        using var reader = cmd.ExecuteReader();
        while (reader.Read())
        {
            list.Add(ReadScheduleFromReader(reader, DateTime.Today, DateTime.Today.AddHours(1)));
        }

        return list;
    }

    public static List<ScheduleItem> GetDeferredSchedules()
    {
        var list = new List<ScheduleItem>();
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        string sql = @"
            SELECT id, title, description, category, priority, status, start_time, end_time, estimated_minutes, is_deleted,
                   goal_id, work_type, dod, actual_minutes, interruption_minutes, is_deferred, is_backlog, sync_version, is_dirty,
                   is_all_day, recurrence, color_hex, source, study_topic_id
            FROM schedules
            WHERE is_deleted = 0 AND is_deferred = 1
            ORDER BY updated_at DESC;
        ";

        using var cmd = new SqliteCommand(sql, conn);
        using var reader = cmd.ExecuteReader();
        while (reader.Read())
        {
            list.Add(ReadScheduleFromReader(reader, DateTime.Today, DateTime.Today.AddHours(1)));
        }

        return list;
    }

    public static ScheduleItem? GetScheduleById(string id)
    {
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        string sql = @"
            SELECT id, title, description, category, priority, status, start_time, end_time, estimated_minutes, is_deleted,
                   goal_id, work_type, dod, actual_minutes, interruption_minutes, is_deferred, is_backlog, sync_version, is_dirty,
                   is_all_day, recurrence, color_hex, source, study_topic_id
            FROM schedules
            WHERE id = @id AND is_deleted = 0
            LIMIT 1;
        ";

        using var cmd = new SqliteCommand(sql, conn);
        cmd.Parameters.AddWithValue("@id", id);
        using var reader = cmd.ExecuteReader();
        if (reader.Read())
        {
            return ReadScheduleFromReader(reader, DateTime.Today, DateTime.Today.AddHours(1));
        }

        return null;
    }

    private static ScheduleItem ReadScheduleFromReader(SqliteDataReader reader, DateTime defaultStart, DateTime defaultEnd)
    {
        return new ScheduleItem
        {
            Id = reader.GetString(0),
            Title = reader.GetString(1),
            Description = reader.IsDBNull(2) ? "" : reader.GetString(2),
            Category = reader.IsDBNull(3) ? "WORK" : reader.GetString(3),
            Priority = reader.IsDBNull(4) ? "MEDIUM" : reader.GetString(4),
            Status = reader.IsDBNull(5) ? "PENDING" : reader.GetString(5),
            StartTime = DateTime.TryParse(reader.GetString(6), out var st) ? st : defaultStart,
            EndTime = DateTime.TryParse(reader.GetString(7), out var et) ? et : defaultEnd,
            EstimatedMinutes = reader.IsDBNull(8) ? 0 : reader.GetInt32(8),
            IsDeleted = reader.GetInt32(9) == 1,
            GoalId = reader.IsDBNull(10) ? null : reader.GetString(10),
            WorkType = reader.IsDBNull(11) ? "DEEP_WORK" : reader.GetString(11),
            Dod = reader.IsDBNull(12) ? "" : reader.GetString(12),
            ActualMinutes = reader.IsDBNull(13) ? 0 : reader.GetInt32(13),
            InterruptionMinutes = reader.IsDBNull(14) ? 0 : reader.GetInt32(14),
            IsDeferred = !reader.IsDBNull(15) && reader.GetInt32(15) == 1,
            IsBacklog = !reader.IsDBNull(16) && reader.GetInt32(16) == 1,
            SyncVersion = reader.IsDBNull(17) ? 1 : reader.GetInt32(17),
            IsDirty = !reader.IsDBNull(18) && reader.GetInt32(18) == 1,
            IsAllDay = reader.FieldCount > 19 && !reader.IsDBNull(19) && reader.GetInt32(19) == 1,
            Recurrence = reader.FieldCount > 20 && !reader.IsDBNull(20) ? reader.GetString(20) : "NONE",
            ColorHex = reader.FieldCount > 21 && !reader.IsDBNull(21) ? reader.GetString(21) : null,
            Source = reader.FieldCount > 22 && !reader.IsDBNull(22) ? reader.GetString(22) : "",
            StudyTopicId = reader.FieldCount > 23 && !reader.IsDBNull(23) ? reader.GetString(23) : null,
            IsTentative = reader.FieldCount > 24 && !reader.IsDBNull(24) && reader.GetInt32(24) == 1,
            IsLocked = reader.FieldCount > 25 && !reader.IsDBNull(25) && reader.GetInt32(25) == 1,
            DependsOnTaskId = reader.FieldCount > 26 && !reader.IsDBNull(26) ? reader.GetString(26) : null,
            PostponeCount = reader.FieldCount > 27 && !reader.IsDBNull(27) ? reader.GetInt32(27) : 0,
            EisenhowerQuadrant = reader.FieldCount > 28 && !reader.IsDBNull(28) ? reader.GetString(28) : "Q2"
        };
    }

    public static List<string> GetDistinctCategories()
    {
        var list = new List<string>();
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        // 优先从 schedule_tags 标签库中加载所有规范标签
        string sql = @"
            SELECT name FROM schedule_tags ORDER BY sort_order ASC, name ASC;
        ";

        using (var cmd = new SqliteCommand(sql, conn))
        using (var reader = cmd.ExecuteReader())
        {
            while (reader.Read())
            {
                string cat = reader.GetString(0).Trim();
                if (!string.IsNullOrEmpty(cat) && !list.Contains(cat, StringComparer.OrdinalIgnoreCase))
                {
                    list.Add(cat);
                }
            }
        }

        // 补充读取既有未登记在 tags 表中的日程历史分类
        try
        {
            string legacySql = @"
                SELECT DISTINCT category FROM schedules 
                WHERE is_deleted = 0 AND category IS NOT NULL AND TRIM(category) != '';
            ";
            using var legacyCmd = new SqliteCommand(legacySql, conn);
            using var reader = legacyCmd.ExecuteReader();
            while (reader.Read())
            {
                string cat = reader.GetString(0).Trim();
                if (!string.IsNullOrEmpty(cat) && !list.Contains(cat, StringComparer.OrdinalIgnoreCase))
                {
                    list.Add(cat);
                }
            }
        }
        catch { }

        if (list.Count == 0)
        {
            list.AddRange(new[] { "工作", "学习", "日常", "运动", "会议", "娱乐" });
        }

        return list;
    }

    public static List<ScheduleTagItem> GetAllScheduleTags()
    {
        var list = new List<ScheduleTagItem>();
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        string sql = @"
            SELECT t.id, t.name, t.color_hex, 
                   (SELECT COUNT(1) FROM schedules s WHERE s.is_deleted = 0 AND s.category = t.name) as usage_cnt
            FROM schedule_tags t
            ORDER BY t.sort_order ASC, t.name ASC;
        ";
        using var cmd = new SqliteCommand(sql, conn);
        using var reader = cmd.ExecuteReader();
        while (reader.Read())
        {
            list.Add(new ScheduleTagItem
            {
                Id = reader.GetString(0),
                Name = reader.GetString(1),
                ColorHex = reader.IsDBNull(2) ? "#1A73E8" : reader.GetString(2),
                UsageCount = reader.GetInt32(3)
            });
        }
        return list;
    }

    public static bool AddScheduleTag(string name, string colorHex = "#1A73E8")
    {
        name = name.Trim();
        if (string.IsNullOrEmpty(name)) return false;

        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        string sql = @"
            INSERT OR IGNORE INTO schedule_tags (id, name, color_hex, sort_order, created_at)
            VALUES (@id, @name, @color, 50, @now);
        ";
        using var cmd = new SqliteCommand(sql, conn);
        cmd.Parameters.AddWithValue("@id", Guid.NewGuid().ToString("N"));
        cmd.Parameters.AddWithValue("@name", name);
        cmd.Parameters.AddWithValue("@color", string.IsNullOrWhiteSpace(colorHex) ? "#1A73E8" : colorHex);
        cmd.Parameters.AddWithValue("@now", DateTime.UtcNow.ToString("o"));
        return cmd.ExecuteNonQuery() > 0;
    }

    public static bool RenameScheduleTag(string oldName, string newName, string colorHex = "")
    {
        oldName = oldName.Trim();
        newName = newName.Trim();
        if (string.IsNullOrEmpty(oldName) || string.IsNullOrEmpty(newName)) return false;

        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();
        using var tx = conn.BeginTransaction();

        try
        {
            using var cmd = new SqliteCommand(@"
                UPDATE schedule_tags 
                SET name = @newName, 
                    color_hex = CASE WHEN @color != '' THEN @color ELSE color_hex END
                WHERE name = @oldName;
            ", conn, tx);
            cmd.Parameters.AddWithValue("@newName", newName);
            cmd.Parameters.AddWithValue("@oldName", oldName);
            cmd.Parameters.AddWithValue("@color", colorHex ?? "");
            cmd.ExecuteNonQuery();

            using var updateSchedCmd = new SqliteCommand(@"
                UPDATE schedules SET category = @newName WHERE category = @oldName;
            ", conn, tx);
            updateSchedCmd.Parameters.AddWithValue("@newName", newName);
            updateSchedCmd.Parameters.AddWithValue("@oldName", oldName);
            updateSchedCmd.ExecuteNonQuery();

            tx.Commit();
            return true;
        }
        catch
        {
            tx.Rollback();
            return false;
        }
    }

    public static bool UpdateScheduleTagColor(string tagName, string colorHex)
    {
        tagName = tagName.Trim();
        colorHex = colorHex.Trim();
        if (string.IsNullOrEmpty(tagName) || string.IsNullOrEmpty(colorHex)) return false;

        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();
        try
        {
            using var cmd = new SqliteCommand(@"
                UPDATE schedule_tags 
                SET color_hex = @color
                WHERE name = @name;
            ", conn);
            cmd.Parameters.AddWithValue("@name", tagName);
            cmd.Parameters.AddWithValue("@color", colorHex);
            return cmd.ExecuteNonQuery() > 0;
        }
        catch
        {
            return false;
        }
    }

    public static bool DeleteScheduleTag(string tagName)
    {
        tagName = tagName.Trim();
        if (string.IsNullOrEmpty(tagName)) return false;

        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        using var cmd = new SqliteCommand("DELETE FROM schedule_tags WHERE name = @name;", conn);
        cmd.Parameters.AddWithValue("@name", tagName);
        return cmd.ExecuteNonQuery() > 0;
    }

    public static void UpsertSchedule(ScheduleItem item)
    {
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        string sql = @"
            INSERT INTO schedules (id, title, description, category, priority, status, start_time, end_time, estimated_minutes, is_deleted, created_at, updated_at,
                                  goal_id, work_type, dod, actual_minutes, interruption_minutes, is_deferred, is_backlog, sync_version, is_dirty,
                                  is_all_day, recurrence, color_hex, source, study_topic_id,
                                  is_tentative, is_locked, depends_on_task_id, postpone_count, eisenhower_quadrant)
            VALUES (@id, @title, @description, @category, @priority, @status, @start_time, @end_time, @estimated_minutes, 0, @now, @now,
                    @goal_id, @work_type, @dod, @actual_minutes, @interruption_minutes, @is_deferred, @is_backlog, @sync_version, 1,
                    @is_all_day, @recurrence, @color_hex, @source, @study_topic_id,
                    @is_tentative, @is_locked, @depends_on_task_id, @postpone_count, @eisenhower_quadrant)
            ON CONFLICT(id) DO UPDATE SET
                title = excluded.title,
                description = excluded.description,
                category = excluded.category,
                priority = excluded.priority,
                status = excluded.status,
                start_time = excluded.start_time,
                end_time = excluded.end_time,
                estimated_minutes = excluded.estimated_minutes,
                goal_id = excluded.goal_id,
                study_topic_id = excluded.study_topic_id,
                work_type = excluded.work_type,
                dod = excluded.dod,
                actual_minutes = excluded.actual_minutes,
                interruption_minutes = excluded.interruption_minutes,
                is_deferred = excluded.is_deferred,
                is_backlog = excluded.is_backlog,
                sync_version = sync_version + 1,
                is_dirty = 1,
                is_all_day = excluded.is_all_day,
                recurrence = excluded.recurrence,
                color_hex = excluded.color_hex,
                source = COALESCE(NULLIF(excluded.source, ''), schedules.source),
                is_tentative = excluded.is_tentative,
                is_locked = excluded.is_locked,
                depends_on_task_id = excluded.depends_on_task_id,
                postpone_count = excluded.postpone_count,
                eisenhower_quadrant = excluded.eisenhower_quadrant,
                updated_at = excluded.updated_at;
        ";

        using var cmd = new SqliteCommand(sql, conn);
        cmd.Parameters.AddWithValue("@id", item.Id);
        cmd.Parameters.AddWithValue("@title", item.Title);
        cmd.Parameters.AddWithValue("@description", item.Description ?? "");
        cmd.Parameters.AddWithValue("@category", item.Category ?? "WORK");
        cmd.Parameters.AddWithValue("@priority", item.Priority ?? "MEDIUM");
        cmd.Parameters.AddWithValue("@status", item.Status ?? "PENDING");
        cmd.Parameters.AddWithValue("@start_time", item.StartTime.ToString("s"));
        cmd.Parameters.AddWithValue("@end_time", item.EndTime.ToString("s"));
        cmd.Parameters.AddWithValue("@estimated_minutes", item.EstimatedMinutes);
        cmd.Parameters.AddWithValue("@goal_id", (object?)item.GoalId ?? DBNull.Value);
        cmd.Parameters.AddWithValue("@study_topic_id", (object?)item.StudyTopicId ?? DBNull.Value);
        cmd.Parameters.AddWithValue("@work_type", item.WorkType ?? "DEEP_WORK");
        cmd.Parameters.AddWithValue("@dod", item.Dod ?? "");
        cmd.Parameters.AddWithValue("@actual_minutes", item.ActualMinutes);
        cmd.Parameters.AddWithValue("@interruption_minutes", item.InterruptionMinutes);
        cmd.Parameters.AddWithValue("@is_deferred", item.IsDeferred ? 1 : 0);
        cmd.Parameters.AddWithValue("@is_backlog", item.IsBacklog ? 1 : 0);
        cmd.Parameters.AddWithValue("@sync_version", item.SyncVersion);
        cmd.Parameters.AddWithValue("@is_all_day", item.IsAllDay ? 1 : 0);
        cmd.Parameters.AddWithValue("@recurrence", item.Recurrence ?? "NONE");
        cmd.Parameters.AddWithValue("@color_hex", (object?)item.ColorHex ?? DBNull.Value);
        cmd.Parameters.AddWithValue("@source", item.Source ?? "");
        cmd.Parameters.AddWithValue("@is_tentative", item.IsTentative ? 1 : 0);
        cmd.Parameters.AddWithValue("@is_locked", item.IsLocked ? 1 : 0);
        cmd.Parameters.AddWithValue("@depends_on_task_id", (object?)item.DependsOnTaskId ?? DBNull.Value);
        cmd.Parameters.AddWithValue("@postpone_count", item.PostponeCount);
        cmd.Parameters.AddWithValue("@eisenhower_quadrant", item.EisenhowerQuadrant ?? "Q2");
        cmd.Parameters.AddWithValue("@now", DateTime.UtcNow.ToString("s"));

        cmd.ExecuteNonQuery();
    }

    public static void AddSchedule(ScheduleItem item)
    {
        UpsertSchedule(item);
    }

    public static void UpdateScheduleStatus(string id, string status)
    {
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        string sql = "UPDATE schedules SET status = @status, is_dirty = 1, updated_at = @now WHERE id = @id;";
        using var cmd = new SqliteCommand(sql, conn);
        cmd.Parameters.AddWithValue("@status", status);
        cmd.Parameters.AddWithValue("@now", DateTime.UtcNow.ToString("s"));
        cmd.Parameters.AddWithValue("@id", id);
        cmd.ExecuteNonQuery();
    }

    public static void UpdateScheduleProgress(string id, int actualMinutes, int interruptionMinutes, string? newStatus = null)
    {
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        string sql = @"
            UPDATE schedules 
            SET actual_minutes = @actual, 
                interruption_minutes = @interruption,
                status = COALESCE(@status, status),
                is_dirty = 1,
                updated_at = @now
            WHERE id = @id;
        ";

        using var cmd = new SqliteCommand(sql, conn);
        cmd.Parameters.AddWithValue("@actual", actualMinutes);
        cmd.Parameters.AddWithValue("@interruption", interruptionMinutes);
        cmd.Parameters.AddWithValue("@status", (object?)newStatus ?? DBNull.Value);
        cmd.Parameters.AddWithValue("@now", DateTime.UtcNow.ToString("s"));
        cmd.Parameters.AddWithValue("@id", id);
        cmd.ExecuteNonQuery();
    }

    public static void MarkScheduleDeferred(string id, bool isDeferred)
    {
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        string sql = @"
            UPDATE schedules 
            SET is_deferred = @isDeferred,
                is_backlog = CASE WHEN @isDeferred = 1 THEN 0 ELSE is_backlog END,
                is_dirty = 1,
                updated_at = @now 
            WHERE id = @id;
        ";

        using var cmd = new SqliteCommand(sql, conn);
        cmd.Parameters.AddWithValue("@isDeferred", isDeferred ? 1 : 0);
        cmd.Parameters.AddWithValue("@now", DateTime.UtcNow.ToString("s"));
        cmd.Parameters.AddWithValue("@id", id);
        cmd.ExecuteNonQuery();
    }

    public static void ScheduleBacklogTask(string id, DateTime startTime, DateTime endTime, string? defaultSource = null)
    {
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        int estMins = (int)Math.Max(15, (endTime - startTime).TotalMinutes);

        string sql = @"
            UPDATE schedules 
            SET start_time = @start,
                end_time = @end,
                estimated_minutes = @est,
                source = CASE WHEN source IS NULL OR source = '' THEN @source ELSE source END,
                is_backlog = 0,
                is_deferred = 0,
                is_dirty = 1,
                updated_at = @now 
            WHERE id = @id;
        ";

        using var cmd = new SqliteCommand(sql, conn);
        cmd.Parameters.AddWithValue("@start", startTime.ToString("s"));
        cmd.Parameters.AddWithValue("@end", endTime.ToString("s"));
        cmd.Parameters.AddWithValue("@est", estMins);
        cmd.Parameters.AddWithValue("@source", defaultSource ?? ConfigService.Load().ClientSourceTag);
        cmd.Parameters.AddWithValue("@now", DateTime.UtcNow.ToString("s"));
        cmd.Parameters.AddWithValue("@id", id);
        cmd.ExecuteNonQuery();
    }

    public static void DeleteSchedule(string id)
    {
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        string sql = "UPDATE schedules SET is_deleted = 1, is_dirty = 1, updated_at = @now WHERE id = @id;";
        using var cmd = new SqliteCommand(sql, conn);
        cmd.Parameters.AddWithValue("@now", DateTime.UtcNow.ToString("s"));
        cmd.Parameters.AddWithValue("@id", id);
        cmd.ExecuteNonQuery();
    }

    // ==================== 认知负荷与 P&L 计算 (Analytics & Metrics) ====================

    public static int GetTodayDeepWorkMinutes(DateTime date)
    {
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        string dayStart = date.Date.ToString("s");
        string dayEnd = date.Date.AddDays(1).ToString("s");

        string sql = @"
            SELECT start_time, end_time, actual_minutes, estimated_minutes
            FROM schedules
            WHERE is_deleted = 0 AND is_backlog = 0 AND is_deferred = 0
              AND work_type = 'DEEP_WORK'
              AND start_time >= @start AND start_time < @end;
        ";

        using var cmd = new SqliteCommand(sql, conn);
        cmd.Parameters.AddWithValue("@start", dayStart);
        cmd.Parameters.AddWithValue("@end", dayEnd);

        int totalMins = 0;
        using var reader = cmd.ExecuteReader();
        while (reader.Read())
        {
            int actual = reader.IsDBNull(2) ? 0 : reader.GetInt32(2);
            if (actual > 0)
            {
                totalMins += actual;
            }
            else
            {
                int est = reader.IsDBNull(3) ? 0 : reader.GetInt32(3);
                if (est > 0)
                {
                    totalMins += est;
                }
                else
                {
                    if (DateTime.TryParse(reader.GetString(0), out var st) &&
                        DateTime.TryParse(reader.GetString(1), out var et))
                    {
                        totalMins += (int)Math.Max(0, (et - st).TotalMinutes);
                    }
                }
            }
        }

        return totalMins;
    }

    public static double CalculateOptimismMultiplier()
    {
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        string sql = @"
            SELECT actual_minutes, estimated_minutes
            FROM schedules
            WHERE is_deleted = 0 AND status = 'COMPLETED'
              AND actual_minutes > 0 AND estimated_minutes > 0;
        ";

        using var cmd = new SqliteCommand(sql, conn);
        using var reader = cmd.ExecuteReader();

        double sumActual = 0;
        double sumEstimated = 0;
        int count = 0;

        while (reader.Read())
        {
            sumActual += reader.GetInt32(0);
            sumEstimated += reader.GetInt32(1);
            count++;
        }

        if (count < 3 || sumEstimated <= 0)
        {
            return 1.35; // 经验黄金基准
        }

        double alpha = sumActual / sumEstimated;
        return Math.Round(Math.Clamp(alpha, 1.0, 3.0), 2);
    }

    public static TimePnLReport GetTimePnLReport(DateTime start, DateTime end)
    {
        var report = new TimePnLReport
        {
            PeriodStart = start,
            PeriodEnd = end,
            OptimismAlpha = CalculateOptimismMultiplier()
        };

        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        string sql = @"
            SELECT id, title, description, category, priority, status, start_time, end_time, estimated_minutes, is_deleted,
                   goal_id, work_type, dod, actual_minutes, interruption_minutes, is_deferred, is_backlog, sync_version, is_dirty,
                   is_all_day, recurrence, color_hex, source
            FROM schedules
            WHERE is_deleted = 0 AND is_backlog = 0 AND start_time >= @start AND start_time < @end;
        ";

        using var cmd = new SqliteCommand(sql, conn);
        cmd.Parameters.AddWithValue("@start", start.ToString("s"));
        cmd.Parameters.AddWithValue("@end", end.ToString("s"));

        var list = new List<ScheduleItem>();
        using (var reader = cmd.ExecuteReader())
        {
            while (reader.Read())
            {
                list.Add(ReadScheduleFromReader(reader, start, end));
            }
        }

        double effectiveMins = 0;
        double badDebtMins = 0;
        double deficitMins = 0;
        double plannedMins = 0;
        double actualMins = 0;

        foreach (var item in list)
        {
            int planned = item.EstimatedMinutes > 0 
                ? item.EstimatedMinutes 
                : (int)Math.Max(15, (item.EndTime - item.StartTime).TotalMinutes);
            plannedMins += planned;

            int actual = item.ActualMinutes > 0 ? item.ActualMinutes : (item.Status == "COMPLETED" ? planned : 0);
            actualMins += actual;

            if (item.WorkType == "DEEP_WORK" && item.Status == "COMPLETED")
            {
                effectiveMins += actual;
            }

            // 坏账损耗 = 外部打断时长 + (若是被废弃或延期但耗费的工时)
            badDebtMins += item.InterruptionMinutes;
            if (item.IsDeferred && actual > 0)
            {
                badDebtMins += actual;
            }

            // 赤字 = 实际超出预估的部分
            if (actual > planned)
            {
                deficitMins += (actual - planned);
                report.OverrunTasks.Add(item);
            }

            if (item.InterruptionMinutes > 0)
            {
                report.InterruptedTasks.Add(item);
            }
        }

        report.OperatingEffectiveHours = Math.Round(effectiveMins / 60.0, 1);
        report.BadDebtHours = Math.Round(badDebtMins / 60.0, 1);
        report.BudgetDeficitHours = Math.Round(deficitMins / 60.0, 1);
        report.PlannedBudgetHours = Math.Round(plannedMins / 60.0, 1);
        report.TotalActualHours = Math.Round(actualMins / 60.0, 1);

        return report;
    }

    // ==================== 增量同步遥测 (Sync Telemetry) ====================

    public static int GetDirtyCount()
    {
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        using var cmd = new SqliteCommand("SELECT COUNT(*) FROM schedules WHERE is_deleted = 0 AND is_dirty = 1;", conn);
        var res = cmd.ExecuteScalar();
        return res != null && int.TryParse(res.ToString(), out var count) ? count : 0;
    }

    public static void MarkAllClean()
    {
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        using var cmd = new SqliteCommand("UPDATE schedules SET is_dirty = 0 WHERE is_dirty = 1;", conn);
        cmd.ExecuteNonQuery();
    }

    // ==================== 记账管理 (Expenses) ====================

    public static List<FinanceTransaction> GetTodayExpenses()
    {
        var list = new List<FinanceTransaction>();
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        string todayStart = DateTime.Today.ToString("s");
        string todayEnd = DateTime.Today.AddDays(1).ToString("s");

        string sql = @"
            SELECT id, type, amount, category, note, raw_input_text, transaction_time, is_deleted
            FROM expenses
            WHERE is_deleted = 0 AND transaction_time >= @start AND transaction_time < @end
            ORDER BY transaction_time DESC;
        ";

        using var cmd = new SqliteCommand(sql, conn);
        cmd.Parameters.AddWithValue("@start", todayStart);
        cmd.Parameters.AddWithValue("@end", todayEnd);

        using var reader = cmd.ExecuteReader();
        while (reader.Read())
        {
            list.Add(new FinanceTransaction
            {
                Id = reader.GetString(0),
                Type = reader.IsDBNull(1) ? "EXPENSE" : reader.GetString(1),
                Amount = reader.IsDBNull(2) ? 0 : Convert.ToDecimal(reader.GetDouble(2)),
                Category = reader.IsDBNull(3) ? "餐饮美食" : reader.GetString(3),
                Note = reader.IsDBNull(4) ? "" : reader.GetString(4),
                RawInputText = reader.IsDBNull(5) ? "" : reader.GetString(5),
                TransactionTime = DateTime.TryParse(reader.GetString(6), out var tt) ? tt : DateTime.Now,
                IsDeleted = reader.GetInt32(7) == 1
            });
        }

        return list;
    }

    public static void AddExpense(FinanceTransaction item)
    {
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        string sql = @"
            INSERT INTO expenses (id, type, amount, category, note, raw_input_text, transaction_time, is_deleted, created_at)
            VALUES (@id, @type, @amount, @category, @note, @raw_input_text, @transaction_time, 0, @now);
        ";

        using var cmd = new SqliteCommand(sql, conn);
        cmd.Parameters.AddWithValue("@id", item.Id);
        cmd.Parameters.AddWithValue("@type", item.Type);
        cmd.Parameters.AddWithValue("@amount", (double)item.Amount);
        cmd.Parameters.AddWithValue("@category", item.Category);
        cmd.Parameters.AddWithValue("@note", item.Note);
        cmd.Parameters.AddWithValue("@raw_input_text", item.RawInputText);
        cmd.Parameters.AddWithValue("@transaction_time", item.TransactionTime.ToString("s"));
        cmd.Parameters.AddWithValue("@now", DateTime.UtcNow.ToString("s"));

        cmd.ExecuteNonQuery();
    }

    public static decimal GetTodayTotalExpense()
    {
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        string todayStart = DateTime.Today.ToString("s");
        string todayEnd = DateTime.Today.AddDays(1).ToString("s");

        string sql = "SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE is_deleted = 0 AND type = 'EXPENSE' AND transaction_time >= @start AND transaction_time < @end;";
        using var cmd = new SqliteCommand(sql, conn);
        cmd.Parameters.AddWithValue("@start", todayStart);
        cmd.Parameters.AddWithValue("@end", todayEnd);

        var result = cmd.ExecuteScalar();
        if (result != null && double.TryParse(result.ToString(), out var val))
        {
            return Convert.ToDecimal(val);
        }
        return 0m;
    }

    // ==================== 行为活动采集打点 (Activity Log) ====================

    public static void LogActivity(string processName, string windowTitle, int durationSeconds)
    {
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();

            string sql = @"
                INSERT INTO focus_logs (id, process_name, window_title, duration_seconds, timestamp)
                VALUES (@id, @process_name, @window_title, @duration_seconds, @now);
            ";

            using var cmd = new SqliteCommand(sql, conn);
            cmd.Parameters.AddWithValue("@id", Guid.NewGuid().ToString());
            cmd.Parameters.AddWithValue("@process_name", processName ?? "");
            cmd.Parameters.AddWithValue("@window_title", windowTitle ?? "");
            cmd.Parameters.AddWithValue("@duration_seconds", durationSeconds);
            cmd.Parameters.AddWithValue("@now", DateTime.UtcNow.ToString("s"));

            cmd.ExecuteNonQuery();
        }
        catch
        {
        }
    }

    public static int GetTodayTrackedMinutes()
    {
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();

            string todayStart = DateTime.Today.ToString("s");
            string todayEnd = DateTime.Today.AddDays(1).ToString("s");

            string sql = "SELECT COALESCE(SUM(duration_seconds), 0) FROM focus_logs WHERE timestamp >= @start AND timestamp < @end;";
            using var cmd = new SqliteCommand(sql, conn);
            cmd.Parameters.AddWithValue("@start", todayStart);
            cmd.Parameters.AddWithValue("@end", todayEnd);

            var result = cmd.ExecuteScalar();
            if (result != null && double.TryParse(result.ToString(), out var val))
            {
                return (int)(val / 60);
            }
        }
        catch
        {
        }
        return 0;
    }

    // ==================== 学习内容与知识管理 (Study Topics) ====================

    public static List<StudyTopicItem> GetAllStudyTopics()
    {
        var list = new List<StudyTopicItem>();
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();

            string sql = @"
                SELECT id, title, category, status, progress_percent, target_hours, completed_hours, current_checkpoint, notes, is_deleted, created_at, updated_at,
                       progress_type, custom_unit, target_value, completed_value
                FROM study_topics
                WHERE is_deleted = 0
                ORDER BY updated_at DESC, created_at DESC;
            ";

            using var cmd = new SqliteCommand(sql, conn);
            using var reader = cmd.ExecuteReader();
            while (reader.Read())
            {
                list.Add(new StudyTopicItem
                {
                    Id = reader.GetString(0),
                    Title = reader.GetString(1),
                    Category = reader.IsDBNull(2) ? "技术栈" : reader.GetString(2),
                    Status = reader.IsDBNull(3) ? "IN_PROGRESS" : reader.GetString(3),
                    ProgressPercent = reader.IsDBNull(4) ? 0 : reader.GetInt32(4),
                    TargetHours = reader.IsDBNull(5) ? 10.0 : reader.GetDouble(5),
                    CompletedHours = reader.IsDBNull(6) ? 0.0 : reader.GetDouble(6),
                    CurrentCheckpoint = reader.IsDBNull(7) ? "" : reader.GetString(7),
                    Notes = reader.IsDBNull(8) ? "" : reader.GetString(8),
                    IsDeleted = reader.GetInt32(9) == 1,
                    CreatedAt = DateTime.TryParse(reader.GetString(10), out var ca) ? ca : DateTime.UtcNow,
                    UpdatedAt = DateTime.TryParse(reader.GetString(11), out var ua) ? ua : DateTime.UtcNow,
                    ProgressType = reader.FieldCount > 12 && !reader.IsDBNull(12) ? reader.GetString(12) : "HOURS",
                    CustomUnit = reader.FieldCount > 13 && !reader.IsDBNull(13) ? reader.GetString(13) : "",
                    TargetValue = reader.FieldCount > 14 && !reader.IsDBNull(14) ? reader.GetDouble(14) : (reader.IsDBNull(5) ? 10.0 : reader.GetDouble(5)),
                    CompletedValue = reader.FieldCount > 15 && !reader.IsDBNull(15) ? reader.GetDouble(15) : (reader.IsDBNull(6) ? 0.0 : reader.GetDouble(6))
                });
            }
        }
        catch { }
        return list;
    }

    public static void UpsertStudyTopic(StudyTopicItem item)
    {
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();

            string sql = @"
                INSERT INTO study_topics (id, title, category, status, progress_percent, target_hours, completed_hours, current_checkpoint, notes, is_deleted, created_at, updated_at,
                                         progress_type, custom_unit, target_value, completed_value)
                VALUES (@id, @title, @category, @status, @progress_percent, @target_hours, @completed_hours, @current_checkpoint, @notes, 0, @created_at, @updated_at,
                        @progress_type, @custom_unit, @target_value, @completed_value)
                ON CONFLICT(id) DO UPDATE SET
                    title = excluded.title,
                    category = excluded.category,
                    status = excluded.status,
                    progress_percent = excluded.progress_percent,
                    target_hours = excluded.target_hours,
                    completed_hours = excluded.completed_hours,
                    current_checkpoint = excluded.current_checkpoint,
                    notes = excluded.notes,
                    progress_type = excluded.progress_type,
                    custom_unit = excluded.custom_unit,
                    target_value = excluded.target_value,
                    completed_value = excluded.completed_value,
                    updated_at = excluded.updated_at;
            ";

            using var cmd = new SqliteCommand(sql, conn);
            cmd.Parameters.AddWithValue("@id", item.Id);
            cmd.Parameters.AddWithValue("@title", item.Title);
            cmd.Parameters.AddWithValue("@category", item.Category);
            cmd.Parameters.AddWithValue("@status", item.Status);
            cmd.Parameters.AddWithValue("@progress_percent", item.ProgressPercent);
            cmd.Parameters.AddWithValue("@target_hours", item.TargetHours);
            cmd.Parameters.AddWithValue("@completed_hours", item.CompletedHours);
            cmd.Parameters.AddWithValue("@current_checkpoint", item.CurrentCheckpoint ?? "");
            cmd.Parameters.AddWithValue("@notes", item.Notes ?? "");
            cmd.Parameters.AddWithValue("@progress_type", item.ProgressType ?? "HOURS");
            cmd.Parameters.AddWithValue("@custom_unit", item.CustomUnit ?? "");
            cmd.Parameters.AddWithValue("@target_value", item.TargetValue);
            cmd.Parameters.AddWithValue("@completed_value", item.CompletedValue);
            cmd.Parameters.AddWithValue("@created_at", item.CreatedAt.ToString("s"));
            cmd.Parameters.AddWithValue("@updated_at", DateTime.UtcNow.ToString("s"));
            cmd.ExecuteNonQuery();
        }
        catch { }
    }

    public static void DeleteStudyTopic(string id)
    {
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();
            using var cmd = new SqliteCommand("UPDATE study_topics SET is_deleted = 1, updated_at = @now WHERE id = @id;", conn);
            cmd.Parameters.AddWithValue("@id", id);
            cmd.Parameters.AddWithValue("@now", DateTime.UtcNow.ToString("s"));
            cmd.ExecuteNonQuery();
        }
        catch { }
    }

    // ==================== 课程表管理 (WakeUp Timetable) ====================

    public static List<CourseItem> GetAllCourses()
    {
        var list = new List<CourseItem>();
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();

            string sql = @"
                SELECT id, name, teacher, location, day_of_week, start_section, section_span,
                       start_week, end_week, week_type, color_hex, notes, is_custom_time, custom_start_time, custom_end_time,
                       is_deleted, created_at, updated_at,
                       exam_date, course_url, room_code
                FROM courses
                WHERE is_deleted = 0
                ORDER BY day_of_week ASC, start_section ASC;
            ";

            using var cmd = new SqliteCommand(sql, conn);
            using var reader = cmd.ExecuteReader();
            while (reader.Read())
            {
                list.Add(new CourseItem
                {
                    Id = reader.GetString(0),
                    Name = reader.GetString(1),
                    Teacher = reader.IsDBNull(2) ? string.Empty : reader.GetString(2),
                    Location = reader.IsDBNull(3) ? string.Empty : reader.GetString(3),
                    DayOfWeek = reader.GetInt32(4),
                    StartSection = reader.GetInt32(5),
                    SectionSpan = reader.GetInt32(6),
                    StartWeek = reader.GetInt32(7),
                    EndWeek = reader.GetInt32(8),
                    WeekType = reader.IsDBNull(9) ? "ALL" : reader.GetString(9),
                    ColorHex = reader.IsDBNull(10) ? "#3B82F6" : reader.GetString(10),
                    Notes = reader.IsDBNull(11) ? string.Empty : reader.GetString(11),
                    IsCustomTime = !reader.IsDBNull(12) && reader.GetInt32(12) == 1,
                    CustomStartTime = reader.IsDBNull(13) ? string.Empty : reader.GetString(13),
                    CustomEndTime = reader.IsDBNull(14) ? string.Empty : reader.GetString(14),
                    IsDeleted = reader.GetInt32(15) == 1,
                    CreatedAt = DateTime.TryParse(reader.GetString(16), out var ca) ? ca : DateTime.UtcNow,
                    UpdatedAt = DateTime.TryParse(reader.GetString(17), out var ua) ? ua : DateTime.UtcNow,
                    ExamDate = (reader.FieldCount > 18 && !reader.IsDBNull(18) && DateTime.TryParse(reader.GetString(18), out var ed)) ? ed : null,
                    CourseUrl = reader.FieldCount > 19 && !reader.IsDBNull(19) ? reader.GetString(19) : "",
                    RoomCode = reader.FieldCount > 20 && !reader.IsDBNull(20) ? reader.GetString(20) : ""
                });
            }
        }
        catch { }
        return list;
    }

    public static void UpsertCourse(CourseItem course)
    {
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();

            string sql = @"
                INSERT INTO courses (id, name, teacher, location, day_of_week, start_section, section_span,
                                    start_week, end_week, week_type, color_hex, notes,
                                    is_custom_time, custom_start_time, custom_end_time, is_deleted, created_at, updated_at,
                                    exam_date, course_url, room_code)
                VALUES (@id, @name, @teacher, @location, @day_of_week, @start_section, @section_span,
                        @start_week, @end_week, @week_type, @color_hex, @notes,
                        @is_custom_time, @custom_start_time, @custom_end_time, 0, @created_at, @updated_at,
                        @exam_date, @course_url, @room_code)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    teacher = excluded.teacher,
                    location = excluded.location,
                    day_of_week = excluded.day_of_week,
                    start_section = excluded.start_section,
                    section_span = excluded.section_span,
                    start_week = excluded.start_week,
                    end_week = excluded.end_week,
                    week_type = excluded.week_type,
                    color_hex = excluded.color_hex,
                    notes = excluded.notes,
                    is_custom_time = excluded.is_custom_time,
                    custom_start_time = excluded.custom_start_time,
                    custom_end_time = excluded.custom_end_time,
                    exam_date = excluded.exam_date,
                    course_url = excluded.course_url,
                    room_code = excluded.room_code,
                    is_deleted = 0,
                    updated_at = excluded.updated_at;
            ";

            using var cmd = new SqliteCommand(sql, conn);
            cmd.Parameters.AddWithValue("@id", course.Id);
            cmd.Parameters.AddWithValue("@name", course.Name);
            cmd.Parameters.AddWithValue("@teacher", course.Teacher ?? string.Empty);
            cmd.Parameters.AddWithValue("@location", course.Location ?? string.Empty);
            cmd.Parameters.AddWithValue("@day_of_week", course.DayOfWeek);
            cmd.Parameters.AddWithValue("@start_section", course.StartSection);
            cmd.Parameters.AddWithValue("@section_span", course.SectionSpan);
            cmd.Parameters.AddWithValue("@start_week", course.StartWeek);
            cmd.Parameters.AddWithValue("@end_week", course.EndWeek);
            cmd.Parameters.AddWithValue("@week_type", course.WeekType ?? "ALL");
            cmd.Parameters.AddWithValue("@color_hex", course.ColorHex ?? "#3B82F6");
            cmd.Parameters.AddWithValue("@notes", course.Notes ?? string.Empty);
            cmd.Parameters.AddWithValue("@is_custom_time", course.IsCustomTime ? 1 : 0);
            cmd.Parameters.AddWithValue("@custom_start_time", course.CustomStartTime ?? string.Empty);
            cmd.Parameters.AddWithValue("@custom_end_time", course.CustomEndTime ?? string.Empty);
            cmd.Parameters.AddWithValue("@exam_date", (object?)course.ExamDate?.ToString("s") ?? DBNull.Value);
            cmd.Parameters.AddWithValue("@course_url", course.CourseUrl ?? string.Empty);
            cmd.Parameters.AddWithValue("@room_code", course.RoomCode ?? string.Empty);
            cmd.Parameters.AddWithValue("@created_at", course.CreatedAt.ToString("s"));
            cmd.Parameters.AddWithValue("@updated_at", DateTime.UtcNow.ToString("s"));
            cmd.ExecuteNonQuery();
        }
        catch { }
    }

    public static void DeleteCourse(string id)
    {
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();
            using var cmd = new SqliteCommand("UPDATE courses SET is_deleted = 1, updated_at = @now WHERE id = @id;", conn);
            cmd.Parameters.AddWithValue("@id", id);
            cmd.Parameters.AddWithValue("@now", DateTime.UtcNow.ToString("s"));
            cmd.ExecuteNonQuery();
        }
        catch { }
    }

    public static string GetTimetableSetting(string key, string defaultValue)
    {
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();
            using var cmd = new SqliteCommand("SELECT value FROM timetable_settings WHERE key = @key;", conn);
            cmd.Parameters.AddWithValue("@key", key);
            var res = cmd.ExecuteScalar();
            if (res != null && res != DBNull.Value) return res.ToString()!;
        }
        catch { }
        return defaultValue;
    }

    public static void SetTimetableSetting(string key, string value)
    {
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();
            string sql = @"
                INSERT INTO timetable_settings (key, value) VALUES (@key, @value)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value;
            ";
            using var cmd = new SqliteCommand(sql, conn);
            cmd.Parameters.AddWithValue("@key", key);
            cmd.Parameters.AddWithValue("@value", value);
            cmd.ExecuteNonQuery();
        }
        catch { }
    }

    public static List<CourseAdjustmentItem> GetAllCourseAdjustments()
    {
        var list = new List<CourseAdjustmentItem>();
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();
            string sql = @"
                SELECT id, course_id, course_name, adjustment_type, target_week,
                       orig_day_of_week, orig_start_section, new_day_of_week, new_start_section,
                       section_span, new_location, reason, is_custom_time, custom_start_time, custom_end_time,
                       created_at, updated_at
                FROM course_adjustments
                WHERE is_deleted = 0
                ORDER BY target_week ASC, orig_day_of_week ASC, orig_start_section ASC;
            ";
            using var cmd = new SqliteCommand(sql, conn);
            using var reader = cmd.ExecuteReader();
            while (reader.Read())
            {
                list.Add(new CourseAdjustmentItem
                {
                    Id = reader.GetString(0),
                    CourseId = reader.IsDBNull(1) ? string.Empty : reader.GetString(1),
                    CourseName = reader.IsDBNull(2) ? string.Empty : reader.GetString(2),
                    AdjustmentType = reader.IsDBNull(3) ? "SUSPEND" : reader.GetString(3),
                    TargetWeek = reader.GetInt32(4),
                    OrigDayOfWeek = reader.GetInt32(5),
                    OrigStartSection = reader.GetInt32(6),
                    NewDayOfWeek = reader.GetInt32(7),
                    NewStartSection = reader.GetInt32(8),
                    SectionSpan = reader.GetInt32(9),
                    NewLocation = reader.IsDBNull(10) ? string.Empty : reader.GetString(10),
                    Reason = reader.IsDBNull(11) ? string.Empty : reader.GetString(11),
                    IsCustomTime = !reader.IsDBNull(12) && reader.GetInt32(12) == 1,
                    CustomStartTime = reader.IsDBNull(13) ? string.Empty : reader.GetString(13),
                    CustomEndTime = reader.IsDBNull(14) ? string.Empty : reader.GetString(14),
                    CreatedAt = DateTime.TryParse(reader.GetString(15), out var ca) ? ca : DateTime.UtcNow,
                    UpdatedAt = DateTime.TryParse(reader.GetString(16), out var ua) ? ua : DateTime.UtcNow
                });
            }
        }
        catch { }
        return list;
    }

    public static void UpsertCourseAdjustment(CourseAdjustmentItem item)
    {
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();
            string sql = @"
                INSERT INTO course_adjustments (id, course_id, course_name, adjustment_type, target_week,
                                                orig_day_of_week, orig_start_section, new_day_of_week, new_start_section,
                                                section_span, new_location, reason,
                                                is_custom_time, custom_start_time, custom_end_time,
                                                is_deleted, created_at, updated_at)
                VALUES (@id, @course_id, @course_name, @adjustment_type, @target_week,
                        @orig_day_of_week, @orig_start_section, @new_day_of_week, @new_start_section,
                        @section_span, @new_location, @reason,
                        @is_custom_time, @custom_start_time, @custom_end_time,
                        0, @created_at, @updated_at)
                ON CONFLICT(id) DO UPDATE SET
                    course_id = excluded.course_id,
                    course_name = excluded.course_name,
                    adjustment_type = excluded.adjustment_type,
                    target_week = excluded.target_week,
                    orig_day_of_week = excluded.orig_day_of_week,
                    orig_start_section = excluded.orig_start_section,
                    new_day_of_week = excluded.new_day_of_week,
                    new_start_section = excluded.new_start_section,
                    section_span = excluded.section_span,
                    new_location = excluded.new_location,
                    reason = excluded.reason,
                    is_custom_time = excluded.is_custom_time,
                    custom_start_time = excluded.custom_start_time,
                    custom_end_time = excluded.custom_end_time,
                    is_deleted = 0,
                    updated_at = excluded.updated_at;
            ";
            using var cmd = new SqliteCommand(sql, conn);
            cmd.Parameters.AddWithValue("@id", item.Id);
            cmd.Parameters.AddWithValue("@course_id", item.CourseId ?? string.Empty);
            cmd.Parameters.AddWithValue("@course_name", item.CourseName ?? string.Empty);
            cmd.Parameters.AddWithValue("@adjustment_type", item.AdjustmentType ?? "SUSPEND");
            cmd.Parameters.AddWithValue("@target_week", item.TargetWeek);
            cmd.Parameters.AddWithValue("@orig_day_of_week", item.OrigDayOfWeek);
            cmd.Parameters.AddWithValue("@orig_start_section", item.OrigStartSection);
            cmd.Parameters.AddWithValue("@new_day_of_week", item.NewDayOfWeek);
            cmd.Parameters.AddWithValue("@new_start_section", item.NewStartSection);
            cmd.Parameters.AddWithValue("@section_span", item.SectionSpan);
            cmd.Parameters.AddWithValue("@new_location", item.NewLocation ?? string.Empty);
            cmd.Parameters.AddWithValue("@reason", item.Reason ?? string.Empty);
            cmd.Parameters.AddWithValue("@is_custom_time", item.IsCustomTime ? 1 : 0);
            cmd.Parameters.AddWithValue("@custom_start_time", item.CustomStartTime ?? string.Empty);
            cmd.Parameters.AddWithValue("@custom_end_time", item.CustomEndTime ?? string.Empty);
            cmd.Parameters.AddWithValue("@created_at", item.CreatedAt.ToString("s"));
            cmd.Parameters.AddWithValue("@updated_at", DateTime.UtcNow.ToString("s"));
            cmd.ExecuteNonQuery();
        }
        catch { }
    }

    public static void DeleteCourseAdjustment(string id)
    {
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();
            using var cmd = new SqliteCommand("UPDATE course_adjustments SET is_deleted = 1, updated_at = @now WHERE id = @id;", conn);
            cmd.Parameters.AddWithValue("@id", id);
            cmd.Parameters.AddWithValue("@now", DateTime.UtcNow.ToString("s"));
            cmd.ExecuteNonQuery();
        }
        catch { }
    }

    // ==================== 每日成果汇报 (Daily Reports) ====================

    public static DailyReportItem? GetDailyReport(string date)
    {
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();
            using var cmd = new SqliteCommand("SELECT date, reflection, ai_summary, created_at, updated_at FROM daily_reports WHERE date = @date LIMIT 1;", conn);
            cmd.Parameters.AddWithValue("@date", date);
            using var reader = cmd.ExecuteReader();
            if (reader.Read())
            {
                return new DailyReportItem
                {
                    Date = reader.GetString(0),
                    Reflection = reader.IsDBNull(1) ? string.Empty : reader.GetString(1),
                    AiSummary = reader.IsDBNull(2) ? string.Empty : reader.GetString(2),
                    CreatedAt = DateTime.TryParse(reader.GetString(3), out var ca) ? ca : DateTime.UtcNow,
                    UpdatedAt = DateTime.TryParse(reader.GetString(4), out var ua) ? ua : DateTime.UtcNow
                };
            }
        }
        catch { }
        return null;
    }

    public static void UpsertDailyReport(DailyReportItem report)
    {
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();
            string sql = @"
                INSERT INTO daily_reports (date, reflection, ai_summary, created_at, updated_at)
                VALUES (@date, @reflection, @ai_summary, @created_at, @updated_at)
                ON CONFLICT(date) DO UPDATE SET
                    reflection = excluded.reflection,
                    ai_summary = excluded.ai_summary,
                    updated_at = excluded.updated_at;
            ";
            using var cmd = new SqliteCommand(sql, conn);
            cmd.Parameters.AddWithValue("@date", report.Date);
            cmd.Parameters.AddWithValue("@reflection", report.Reflection ?? string.Empty);
            cmd.Parameters.AddWithValue("@ai_summary", report.AiSummary ?? string.Empty);
            cmd.Parameters.AddWithValue("@created_at", report.CreatedAt.ToString("s"));
            cmd.Parameters.AddWithValue("@updated_at", DateTime.UtcNow.ToString("s"));
            cmd.ExecuteNonQuery();
        }
        catch { }
    }

    /// <summary>
    /// 对数据库进行碎片整理与健康压缩 (VACUUM & OPTIMIZE)
    /// </summary>
    public static void OptimizeDatabase()
    {
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();
            using var cmd = new SqliteCommand("PRAGMA optimize; VACUUM;", conn);
            cmd.ExecuteNonQuery();
        }
        catch { }
    }

    /// <summary>
    /// 创建带时间戳的安全只读副本用于云端备份同步
    /// </summary>
    public static string CreateSafeDatabaseSnapshot()
    {
        if (!File.Exists(DbPath))
        {
            throw new FileNotFoundException("本地数据库文件尚未初始化。");
        }

        string tempBackup = Path.Combine(DbDir, $"rmf_backup_temp_{Guid.NewGuid():N}.db");
        // 使用 SQLite 在线备份机制，必须显式禁用连接池并完全释放文件句柄
        using (var sourceConn = new SqliteConnection(ConnectionString))
        {
            sourceConn.Open();
            using (var destConn = new SqliteConnection($"Data Source={tempBackup};Pooling=False"))
            {
                destConn.Open();
                sourceConn.BackupDatabase(destConn);
                destConn.Close();
            }
            sourceConn.Close();
        }
        SqliteConnection.ClearAllPools();
        return tempBackup;
    }

    // ==================== 打断记录 (Interruptions) ====================
    public static void AddInterruption(InterruptionItem item)
    {
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();
            string sql = @"
                INSERT INTO interruptions (id, schedule_id, type, duration_seconds, timestamp, note)
                VALUES (@id, @schedule_id, @type, @duration_seconds, @timestamp, @note);
            ";
            using var cmd = new SqliteCommand(sql, conn);
            cmd.Parameters.AddWithValue("@id", item.Id);
            cmd.Parameters.AddWithValue("@schedule_id", item.ScheduleId ?? "");
            cmd.Parameters.AddWithValue("@type", item.Type ?? "EXTERNAL");
            cmd.Parameters.AddWithValue("@duration_seconds", item.DurationSeconds);
            cmd.Parameters.AddWithValue("@timestamp", item.Timestamp.ToString("s"));
            cmd.Parameters.AddWithValue("@note", item.Note ?? "");
            cmd.ExecuteNonQuery();

            // 同步累加对应 schedule 的 interruption_minutes
            if (!string.IsNullOrEmpty(item.ScheduleId) && item.DurationSeconds > 0)
            {
                int addMins = (int)Math.Ceiling(item.DurationSeconds / 60.0);
                string updateSql = "UPDATE schedules SET interruption_minutes = interruption_minutes + @mins WHERE id = @id;";
                using var updateCmd = new SqliteCommand(updateSql, conn);
                updateCmd.Parameters.AddWithValue("@mins", addMins);
                updateCmd.Parameters.AddWithValue("@id", item.ScheduleId);
                updateCmd.ExecuteNonQuery();
            }
        }
        catch { }
    }

    public static List<InterruptionItem> GetInterruptionsForDateRange(DateTime start, DateTime end)
    {
        var list = new List<InterruptionItem>();
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();
            string sql = @"
                SELECT id, schedule_id, type, duration_seconds, timestamp, note
                FROM interruptions
                WHERE timestamp >= @start AND timestamp < @end
                ORDER BY timestamp ASC;
            ";
            using var cmd = new SqliteCommand(sql, conn);
            cmd.Parameters.AddWithValue("@start", start.ToString("s"));
            cmd.Parameters.AddWithValue("@end", end.ToString("s"));
            using var reader = cmd.ExecuteReader();
            while (reader.Read())
            {
                list.Add(new InterruptionItem
                {
                    Id = reader.GetString(0),
                    ScheduleId = reader.IsDBNull(1) ? "" : reader.GetString(1),
                    Type = reader.GetString(2),
                    DurationSeconds = reader.GetInt32(3),
                    Timestamp = DateTime.TryParse(reader.GetString(4), out var dt) ? dt : DateTime.Now,
                    Note = reader.IsDBNull(5) ? "" : reader.GetString(5)
                });
            }
        }
        catch { }
        return list;
    }

    public static List<InterruptionItem> GetInterruptionsForSchedule(string scheduleId)
    {
        var list = new List<InterruptionItem>();
        if (string.IsNullOrEmpty(scheduleId)) return list;
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();
            string sql = @"
                SELECT id, schedule_id, type, duration_seconds, timestamp, note
                FROM interruptions
                WHERE schedule_id = @sid
                ORDER BY timestamp ASC;
            ";
            using var cmd = new SqliteCommand(sql, conn);
            cmd.Parameters.AddWithValue("@sid", scheduleId);
            using var reader = cmd.ExecuteReader();
            while (reader.Read())
            {
                list.Add(new InterruptionItem
                {
                    Id = reader.GetString(0),
                    ScheduleId = reader.GetString(1),
                    Type = reader.GetString(2),
                    DurationSeconds = reader.GetInt32(3),
                    Timestamp = DateTime.TryParse(reader.GetString(4), out var dt) ? dt : DateTime.Now,
                    Note = reader.IsDBNull(5) ? "" : reader.GetString(5)
                });
            }
        }
        catch { }
        return list;
    }

    // ==================== 学习学时反哺沉淀 (Study Hours Feed) ====================
    public static void AddStudyHours(string topicId, double hours)
    {
        if (string.IsNullOrEmpty(topicId) || hours <= 0) return;
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();
            string sql = @"
                UPDATE study_topics
                SET completed_hours = completed_hours + @hours,
                    completed_value = CASE 
                        WHEN progress_type = 'HOURS' THEN completed_value + @hours
                        ELSE completed_value
                    END,
                    progress_percent = CASE 
                        WHEN progress_type = 'HOURS' AND target_value > 0 THEN MIN(100, CAST(ROUND(((completed_value + @hours) / target_value) * 100) AS INTEGER))
                        WHEN target_hours > 0 THEN MIN(100, CAST(ROUND(((completed_hours + @hours) / target_hours) * 100) AS INTEGER))
                        ELSE progress_percent
                    END,
                    updated_at = @now
                WHERE id = @id;
            ";
            using var cmd = new SqliteCommand(sql, conn);
            cmd.Parameters.AddWithValue("@hours", Math.Round(hours, 2));
            cmd.Parameters.AddWithValue("@now", DateTime.UtcNow.ToString("s"));
            cmd.Parameters.AddWithValue("@id", topicId);
            cmd.ExecuteNonQuery();
        }
        catch { }
    }

    // ==================== 快照版本管理 (Snapshot Metadata) ====================
    public static SnapshotMetadata GetSnapshotMetadata()
    {
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();
            string sql = "SELECT id, device_id, version_counter, file_hash, updated_at FROM snapshot_metadata WHERE id = 'current';";
            using var cmd = new SqliteCommand(sql, conn);
            using var reader = cmd.ExecuteReader();
            if (reader.Read())
            {
                return new SnapshotMetadata
                {
                    Id = reader.GetString(0),
                    DeviceId = reader.IsDBNull(1) ? Environment.MachineName : reader.GetString(1),
                    VersionCounter = reader.GetInt64(2),
                    FileHash = reader.IsDBNull(3) ? "" : reader.GetString(3),
                    UpdatedAt = DateTime.TryParse(reader.GetString(4), out var dt) ? dt : DateTime.UtcNow
                };
            }
        }
        catch { }
        return new SnapshotMetadata();
    }

    public static void SaveSnapshotMetadata(SnapshotMetadata meta)
    {
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();
            string sql = @"
                INSERT INTO snapshot_metadata (id, device_id, version_counter, file_hash, updated_at)
                VALUES ('current', @device_id, @version_counter, @file_hash, @updated_at)
                ON CONFLICT(id) DO UPDATE SET
                    device_id = excluded.device_id,
                    version_counter = excluded.version_counter,
                    file_hash = excluded.file_hash,
                    updated_at = excluded.updated_at;
            ";
            using var cmd = new SqliteCommand(sql, conn);
            cmd.Parameters.AddWithValue("@device_id", meta.DeviceId ?? Environment.MachineName);
            cmd.Parameters.AddWithValue("@version_counter", meta.VersionCounter);
            cmd.Parameters.AddWithValue("@file_hash", meta.FileHash ?? "");
            cmd.Parameters.AddWithValue("@updated_at", meta.UpdatedAt.ToString("s"));
            cmd.ExecuteNonQuery();
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[DatabaseService] SaveSnapshotMetadata error: {ex.Message}");
        }
    }

    // =========================================================================
    // 🏋️ 每日健身计划与训练追踪 (Fitness Module CRUD & Stats)
    // =========================================================================

    public static FitnessPlanItem? GetFitnessPlanByDate(string date)
    {
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();
            string sql = @"
                SELECT id, plan_date, title, workout_type, target_duration_minutes,
                       actual_duration_minutes, calories_burned, feeling, status, notes,
                       created_at, updated_at
                FROM fitness_plans
                WHERE plan_date = @date AND is_deleted = 0
                LIMIT 1;
            ";
            using var cmd = new SqliteCommand(sql, conn);
            cmd.Parameters.AddWithValue("@date", date);
            using var reader = cmd.ExecuteReader();
            if (reader.Read())
            {
                return new FitnessPlanItem
                {
                    Id = reader.GetString(0),
                    PlanDate = reader.GetString(1),
                    Title = reader.GetString(2),
                    WorkoutType = reader.GetString(3),
                    TargetDurationMinutes = reader.GetInt32(4),
                    ActualDurationMinutes = reader.GetInt32(5),
                    CaloriesBurned = reader.GetDouble(6),
                    Feeling = reader.GetString(7),
                    Status = reader.GetString(8),
                    Notes = reader.IsDBNull(9) ? "" : reader.GetString(9),
                    CreatedAt = DateTime.TryParse(reader.GetString(10), out var cAt) ? cAt : DateTime.UtcNow,
                    UpdatedAt = DateTime.TryParse(reader.GetString(11), out var uAt) ? uAt : DateTime.UtcNow
                };
            }
        }
        catch { }
        return null;
    }

    public static List<FitnessPlanItem> GetAllFitnessPlans(int limit = 50)
    {
        var list = new List<FitnessPlanItem>();
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();
            string sql = @"
                SELECT id, plan_date, title, workout_type, target_duration_minutes,
                       actual_duration_minutes, calories_burned, feeling, status, notes,
                       created_at, updated_at
                FROM fitness_plans
                WHERE is_deleted = 0
                ORDER BY plan_date DESC
                LIMIT @limit;
            ";
            using var cmd = new SqliteCommand(sql, conn);
            cmd.Parameters.AddWithValue("@limit", limit);
            using var reader = cmd.ExecuteReader();
            while (reader.Read())
            {
                list.Add(new FitnessPlanItem
                {
                    Id = reader.GetString(0),
                    PlanDate = reader.GetString(1),
                    Title = reader.GetString(2),
                    WorkoutType = reader.GetString(3),
                    TargetDurationMinutes = reader.GetInt32(4),
                    ActualDurationMinutes = reader.GetInt32(5),
                    CaloriesBurned = reader.GetDouble(6),
                    Feeling = reader.GetString(7),
                    Status = reader.GetString(8),
                    Notes = reader.IsDBNull(9) ? "" : reader.GetString(9),
                    CreatedAt = DateTime.TryParse(reader.GetString(10), out var cAt) ? cAt : DateTime.UtcNow,
                    UpdatedAt = DateTime.TryParse(reader.GetString(11), out var uAt) ? uAt : DateTime.UtcNow
                });
            }
        }
        catch { }
        return list;
    }

    public static void UpsertFitnessPlan(FitnessPlanItem plan)
    {
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();
            string sql = @"
                INSERT INTO fitness_plans (id, plan_date, title, workout_type, target_duration_minutes,
                                          actual_duration_minutes, calories_burned, feeling, status, notes,
                                          is_deleted, created_at, updated_at)
                VALUES (@id, @plan_date, @title, @workout_type, @target_duration_minutes,
                        @actual_duration_minutes, @calories_burned, @feeling, @status, @notes,
                        0, @created_at, @updated_at)
                ON CONFLICT(id) DO UPDATE SET
                    plan_date = excluded.plan_date,
                    title = excluded.title,
                    workout_type = excluded.workout_type,
                    target_duration_minutes = excluded.target_duration_minutes,
                    actual_duration_minutes = excluded.actual_duration_minutes,
                    calories_burned = excluded.calories_burned,
                    feeling = excluded.feeling,
                    status = excluded.status,
                    notes = excluded.notes,
                    updated_at = excluded.updated_at;
            ";
            using var cmd = new SqliteCommand(sql, conn);
            cmd.Parameters.AddWithValue("@id", plan.Id);
            cmd.Parameters.AddWithValue("@plan_date", plan.PlanDate);
            cmd.Parameters.AddWithValue("@title", plan.Title);
            cmd.Parameters.AddWithValue("@workout_type", plan.WorkoutType);
            cmd.Parameters.AddWithValue("@target_duration_minutes", plan.TargetDurationMinutes);
            cmd.Parameters.AddWithValue("@actual_duration_minutes", plan.ActualDurationMinutes);
            cmd.Parameters.AddWithValue("@calories_burned", plan.CaloriesBurned);
            cmd.Parameters.AddWithValue("@feeling", plan.Feeling);
            cmd.Parameters.AddWithValue("@status", plan.Status);
            cmd.Parameters.AddWithValue("@notes", plan.Notes ?? "");
            cmd.Parameters.AddWithValue("@created_at", plan.CreatedAt.ToString("s"));
            cmd.Parameters.AddWithValue("@updated_at", DateTime.UtcNow.ToString("s"));
            cmd.ExecuteNonQuery();
        }
        catch { }
    }

    public static void DeleteFitnessPlan(string id)
    {
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();
            using var cmd = new SqliteCommand("UPDATE fitness_plans SET is_deleted = 1, updated_at = @now WHERE id = @id;", conn);
            cmd.Parameters.AddWithValue("@id", id);
            cmd.Parameters.AddWithValue("@now", DateTime.UtcNow.ToString("s"));
            cmd.ExecuteNonQuery();
        }
        catch { }
    }

    public static List<FitnessRecordItem> GetFitnessRecordsByDate(string date)
    {
        var list = new List<FitnessRecordItem>();
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();
            string sql = @"
                SELECT id, plan_id, record_date, exercise_name, category, equipment, sets_data, sets_count, reps_per_set,
                       weight_kg, sort_order, distance_km, duration_minutes, calories_burned, is_completed, note,
                       created_at, updated_at
                FROM fitness_records
                WHERE record_date = @date AND is_deleted = 0
                ORDER BY sort_order ASC, created_at ASC;
            ";
            using var cmd = new SqliteCommand(sql, conn);
            cmd.Parameters.AddWithValue("@date", date);
            using var reader = cmd.ExecuteReader();
            while (reader.Read())
            {
                list.Add(new FitnessRecordItem
                {
                    Id = reader.GetString(0),
                    PlanId = reader.IsDBNull(1) ? "" : reader.GetString(1),
                    RecordDate = reader.GetString(2),
                    ExerciseName = reader.GetString(3),
                    Category = reader.GetString(4),
                    Equipment = reader.IsDBNull(5) ? "" : reader.GetString(5),
                    SetsData = reader.IsDBNull(6) ? "[]" : reader.GetString(6),
                    SetsCount = reader.GetInt32(7),
                    RepsPerSet = reader.GetInt32(8),
                    WeightKg = reader.GetDouble(9),
                    SortOrder = reader.GetInt32(10),
                    DistanceKm = reader.GetDouble(11),
                    DurationMinutes = reader.GetInt32(12),
                    CaloriesBurned = reader.GetDouble(13),
                    IsCompleted = reader.GetInt32(14) == 1,
                    Note = reader.IsDBNull(15) ? "" : reader.GetString(15),
                    CreatedAt = DateTime.TryParse(reader.GetString(16), out var cAt) ? cAt : DateTime.UtcNow,
                    UpdatedAt = DateTime.TryParse(reader.GetString(17), out var uAt) ? uAt : DateTime.UtcNow
                });
            }
        }
        catch { }
        return list;
    }

    public static void UpsertFitnessRecord(FitnessRecordItem rec)
    {
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();
            string sql = @"
                INSERT INTO fitness_records (id, plan_id, record_date, exercise_name, category, equipment, sets_data,
                                            sets_count, reps_per_set, weight_kg, sort_order, distance_km,
                                            duration_minutes, calories_burned, is_completed, note,
                                            is_deleted, created_at, updated_at)
                VALUES (@id, @plan_id, @record_date, @exercise_name, @category, @equipment, @sets_data,
                        @sets_count, @reps_per_set, @weight_kg, @sort_order, @distance_km,
                        @duration_minutes, @calories_burned, @is_completed, @note,
                        0, @created_at, @updated_at)
                ON CONFLICT(id) DO UPDATE SET
                    plan_id = excluded.plan_id,
                    record_date = excluded.record_date,
                    exercise_name = excluded.exercise_name,
                    category = excluded.category,
                    equipment = excluded.equipment,
                    sets_data = excluded.sets_data,
                    sets_count = excluded.sets_count,
                    reps_per_set = excluded.reps_per_set,
                    weight_kg = excluded.weight_kg,
                    sort_order = excluded.sort_order,
                    distance_km = excluded.distance_km,
                    duration_minutes = excluded.duration_minutes,
                    calories_burned = excluded.calories_burned,
                    is_completed = excluded.is_completed,
                    note = excluded.note,
                    updated_at = excluded.updated_at;
            ";
            using var cmd = new SqliteCommand(sql, conn);
            cmd.Parameters.AddWithValue("@id", rec.Id);
            cmd.Parameters.AddWithValue("@plan_id", rec.PlanId ?? "");
            cmd.Parameters.AddWithValue("@record_date", rec.RecordDate);
            cmd.Parameters.AddWithValue("@exercise_name", rec.ExerciseName);
            cmd.Parameters.AddWithValue("@category", rec.Category);
            cmd.Parameters.AddWithValue("@equipment", rec.Equipment ?? "");
            cmd.Parameters.AddWithValue("@sets_data", rec.SetsData ?? "[]");
            cmd.Parameters.AddWithValue("@sets_count", rec.SetsCount);
            cmd.Parameters.AddWithValue("@reps_per_set", rec.RepsPerSet);
            cmd.Parameters.AddWithValue("@weight_kg", rec.WeightKg);
            cmd.Parameters.AddWithValue("@sort_order", rec.SortOrder);
            cmd.Parameters.AddWithValue("@distance_km", rec.DistanceKm);
            cmd.Parameters.AddWithValue("@duration_minutes", rec.DurationMinutes);
            cmd.Parameters.AddWithValue("@calories_burned", rec.CaloriesBurned);
            cmd.Parameters.AddWithValue("@is_completed", rec.IsCompleted ? 1 : 0);
            cmd.Parameters.AddWithValue("@note", rec.Note ?? "");
            cmd.Parameters.AddWithValue("@created_at", rec.CreatedAt.ToString("s"));
            cmd.Parameters.AddWithValue("@updated_at", DateTime.UtcNow.ToString("s"));
            cmd.ExecuteNonQuery();
        }
        catch { }
    }

    public static void ToggleFitnessRecordCompleted(string id)
    {
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();
            using var cmd = new SqliteCommand("UPDATE fitness_records SET is_completed = CASE WHEN is_completed = 1 THEN 0 ELSE 1 END, updated_at = @now WHERE id = @id;", conn);
            cmd.Parameters.AddWithValue("@now", DateTime.UtcNow.ToString("s"));
            cmd.Parameters.AddWithValue("@id", id);
            cmd.ExecuteNonQuery();
        }
        catch { }
    }

    public static void ToggleFitnessRecordCompleted(string id, bool isCompleted)
    {
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();
            using var cmd = new SqliteCommand("UPDATE fitness_records SET is_completed = @val, updated_at = @now WHERE id = @id;", conn);
            cmd.Parameters.AddWithValue("@val", isCompleted ? 1 : 0);
            cmd.Parameters.AddWithValue("@now", DateTime.UtcNow.ToString("s"));
            cmd.Parameters.AddWithValue("@id", id);
            cmd.ExecuteNonQuery();
        }
        catch { }
    }

    public static void DeleteFitnessRecord(string id)
    {
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();
            using var cmd = new SqliteCommand("UPDATE fitness_records SET is_deleted = 1, updated_at = @now WHERE id = @id;", conn);
            cmd.Parameters.AddWithValue("@id", id);
            cmd.Parameters.AddWithValue("@now", DateTime.UtcNow.ToString("s"));
            cmd.ExecuteNonQuery();
        }
        catch { }
    }

    public static int GetFitnessStreakDays()
    {
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();
            // 查询所有已完成健身打卡的日期
            string sql = @"
                SELECT DISTINCT plan_date
                FROM fitness_plans
                WHERE is_deleted = 0 AND status = 'COMPLETED'
                ORDER BY plan_date DESC;
            ";
            using var cmd = new SqliteCommand(sql, conn);
            using var reader = cmd.ExecuteReader();
            var dates = new HashSet<string>();
            while (reader.Read())
            {
                dates.Add(reader.GetString(0));
            }

            var checkDate = DateTime.Today;
            // 如果今天还没打卡，检查昨天是否打卡作为起始
            if (!dates.Contains(checkDate.ToString("yyyy-MM-dd")))
            {
                checkDate = checkDate.AddDays(-1);
            }

            int streak = 0;
            while (dates.Contains(checkDate.ToString("yyyy-MM-dd")))
            {
                streak++;
                checkDate = checkDate.AddDays(-1);
            }
            return streak;
        }
        catch { return 0; }
    }

    public static (int totalDays, int totalMinutes, double totalCalories) GetWeeklyFitnessStats()
    {
        try
        {
            using var conn = new SqliteConnection(ConnectionString);
            conn.Open();
            string start = DateTime.Today.AddDays(-6).ToString("yyyy-MM-dd");
            string sql = @"
                SELECT COUNT(DISTINCT plan_date), SUM(actual_duration_minutes), SUM(calories_burned)
                FROM fitness_plans
                WHERE is_deleted = 0 AND status = 'COMPLETED' AND plan_date >= @start;
            ";
            using var cmd = new SqliteCommand(sql, conn);
            cmd.Parameters.AddWithValue("@start", start);
            using var reader = cmd.ExecuteReader();
            if (reader.Read())
            {
                int days = reader.IsDBNull(0) ? 0 : reader.GetInt32(0);
                int mins = reader.IsDBNull(1) ? 0 : reader.GetInt32(1);
                double cals = reader.IsDBNull(2) ? 0 : reader.GetDouble(2);
                return (days, mins, cals);
            }
        }
        catch { }
        return (0, 0, 0.0);
    }
}

