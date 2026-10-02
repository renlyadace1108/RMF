using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using Microsoft.Data.Sqlite;
using RMF.Windows.Models;

namespace RMF.Windows.Services;

public class DatabaseService
{
    private static readonly string DbDir = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        "RMF"
    );
    private static readonly string DbPath = Path.Combine(DbDir, "rmf.db");
    private static readonly string ConnectionString = $"Data Source={DbPath}";

    public static void Initialize()
    {
        if (!Directory.Exists(DbDir))
        {
            Directory.CreateDirectory(DbDir);
        }

        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

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
        ";

        using (var cmd = new SqliteCommand(initSql, conn))
        {
            cmd.ExecuteNonQuery();
        }

        // 动态迁移 schedules 表，追加 5 项核心扩展列
        MigrateSchedulesTable(conn);

        // 如果 goals 目标表为空，注入初始愿景与目标层级种子
        SeedInitialGoalsIfEmpty(conn);
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
            ["is_dirty"] = "INTEGER DEFAULT 0"
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

    private static void SeedInitialGoalsIfEmpty(SqliteConnection conn)
    {
        using var countCmd = new SqliteCommand("SELECT COUNT(*) FROM goals WHERE is_deleted = 0;", conn);
        long count = (long)(countCmd.ExecuteScalar() ?? 0);
        if (count > 0) return;

        string visionId = Guid.NewGuid().ToString();
        string objId = Guid.NewGuid().ToString();
        string keyTaskId1 = Guid.NewGuid().ToString();
        string keyTaskId2 = Guid.NewGuid().ToString();
        string now = DateTime.UtcNow.ToString("s");

        string seedSql = @"
            INSERT INTO goals (id, title, level, parent_id, quarter, progress, sort_order, is_deleted, created_at) VALUES
            (@vId, '核心产品研发与极简架构突破', 'VISION', NULL, '2026-全年', 45, 1, 0, @now),
            (@oId, 'RMF 个人工作台五大核心扩展深度落地', 'OBJECTIVE', @vId, '2026-Q4', 60, 2, 0, @now),
            (@kt1, '完成 AI 客观审计与认知负荷熔断面板', 'KEY_TASK', @oId, '2026-W40', 80, 3, 0, @now),
            (@kt2, '完成目标层级树穿透与时间损益对账表', 'KEY_TASK', @oId, '2026-W40', 70, 4, 0, @now);
        ";

        using var cmd = new SqliteCommand(seedSql, conn);
        cmd.Parameters.AddWithValue("@vId", visionId);
        cmd.Parameters.AddWithValue("@oId", objId);
        cmd.Parameters.AddWithValue("@kt1", keyTaskId1);
        cmd.Parameters.AddWithValue("@kt2", keyTaskId2);
        cmd.Parameters.AddWithValue("@now", now);
        cmd.ExecuteNonQuery();
    }

    // ==================== 目标层级树 (Goal Hierarchy) ====================

    public static List<GoalItem> GetAllGoals()
    {
        var list = new List<GoalItem>();
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        string sql = @"
            SELECT id, title, level, parent_id, quarter, progress, sort_order, is_deleted, created_at
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
                CreatedAt = DateTime.TryParse(reader.GetString(8), out var ca) ? ca : DateTime.UtcNow
            });
        }

        return list;
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
            INSERT INTO goals (id, title, level, parent_id, quarter, progress, sort_order, is_deleted, created_at)
            VALUES (@id, @title, @level, @parent_id, @quarter, @progress, @sort_order, 0, @now)
            ON CONFLICT(id) DO UPDATE SET
                title = excluded.title,
                level = excluded.level,
                parent_id = excluded.parent_id,
                quarter = excluded.quarter,
                progress = excluded.progress,
                sort_order = excluded.sort_order;
        ";

        using var cmd = new SqliteCommand(sql, conn);
        cmd.Parameters.AddWithValue("@id", item.Id);
        cmd.Parameters.AddWithValue("@title", item.Title);
        cmd.Parameters.AddWithValue("@level", item.Level);
        cmd.Parameters.AddWithValue("@parent_id", (object?)item.ParentId ?? DBNull.Value);
        cmd.Parameters.AddWithValue("@quarter", item.Quarter ?? "2026-Q4");
        cmd.Parameters.AddWithValue("@progress", item.Progress);
        cmd.Parameters.AddWithValue("@sort_order", item.SortOrder);
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
                   goal_id, work_type, dod, actual_minutes, interruption_minutes, is_deferred, is_backlog, sync_version, is_dirty
            FROM schedules
            WHERE is_deleted = 0 AND is_backlog = 0 AND is_deferred = 0 AND start_time < @end AND end_time >= @start
            ORDER BY start_time ASC;
        ";

        using var cmd = new SqliteCommand(sql, conn);
        cmd.Parameters.AddWithValue("@start", startIso);
        cmd.Parameters.AddWithValue("@end", endIso);

        using var reader = cmd.ExecuteReader();
        while (reader.Read())
        {
            list.Add(ReadScheduleFromReader(reader, start, end));
        }

        return list;
    }

    public static List<ScheduleItem> GetBacklogSchedules()
    {
        var list = new List<ScheduleItem>();
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        string sql = @"
            SELECT id, title, description, category, priority, status, start_time, end_time, estimated_minutes, is_deleted,
                   goal_id, work_type, dod, actual_minutes, interruption_minutes, is_deferred, is_backlog, sync_version, is_dirty
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
                   goal_id, work_type, dod, actual_minutes, interruption_minutes, is_deferred, is_backlog, sync_version, is_dirty
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
            IsDirty = !reader.IsDBNull(18) && reader.GetInt32(18) == 1
        };
    }

    public static List<string> GetDistinctCategories()
    {
        var list = new List<string>();
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        string sql = @"
            SELECT DISTINCT category 
            FROM schedules 
            WHERE is_deleted = 0 AND category IS NOT NULL AND TRIM(category) != ''
            ORDER BY category ASC;
        ";

        using var cmd = new SqliteCommand(sql, conn);
        using var reader = cmd.ExecuteReader();
        while (reader.Read())
        {
            string cat = reader.GetString(0).Trim();
            if (!string.IsNullOrEmpty(cat) && !list.Contains(cat, StringComparer.OrdinalIgnoreCase))
            {
                list.Add(cat);
            }
        }

        return list;
    }

    public static void UpsertSchedule(ScheduleItem item)
    {
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        string sql = @"
            INSERT INTO schedules (id, title, description, category, priority, status, start_time, end_time, estimated_minutes, is_deleted, created_at, updated_at,
                                  goal_id, work_type, dod, actual_minutes, interruption_minutes, is_deferred, is_backlog, sync_version, is_dirty)
            VALUES (@id, @title, @description, @category, @priority, @status, @start_time, @end_time, @estimated_minutes, 0, @now, @now,
                    @goal_id, @work_type, @dod, @actual_minutes, @interruption_minutes, @is_deferred, @is_backlog, @sync_version, 1)
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
                work_type = excluded.work_type,
                dod = excluded.dod,
                actual_minutes = excluded.actual_minutes,
                interruption_minutes = excluded.interruption_minutes,
                is_deferred = excluded.is_deferred,
                is_backlog = excluded.is_backlog,
                sync_version = sync_version + 1,
                is_dirty = 1,
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
        cmd.Parameters.AddWithValue("@work_type", item.WorkType ?? "DEEP_WORK");
        cmd.Parameters.AddWithValue("@dod", item.Dod ?? "");
        cmd.Parameters.AddWithValue("@actual_minutes", item.ActualMinutes);
        cmd.Parameters.AddWithValue("@interruption_minutes", item.InterruptionMinutes);
        cmd.Parameters.AddWithValue("@is_deferred", item.IsDeferred ? 1 : 0);
        cmd.Parameters.AddWithValue("@is_backlog", item.IsBacklog ? 1 : 0);
        cmd.Parameters.AddWithValue("@sync_version", item.SyncVersion);
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

    public static void ScheduleBacklogTask(string id, DateTime startTime, DateTime endTime)
    {
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        int estMins = (int)Math.Max(15, (endTime - startTime).TotalMinutes);

        string sql = @"
            UPDATE schedules 
            SET start_time = @start,
                end_time = @end,
                estimated_minutes = @est,
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
                   goal_id, work_type, dod, actual_minutes, interruption_minutes, is_deferred, is_backlog, sync_version, is_dirty
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
}
