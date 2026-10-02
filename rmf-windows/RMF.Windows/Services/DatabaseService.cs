using System;
using System.Collections.Generic;
using System.IO;
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

        using var cmd = new SqliteCommand(initSql, conn);
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
            SELECT id, title, description, category, priority, status, start_time, end_time, estimated_minutes, is_deleted
            FROM schedules
            WHERE is_deleted = 0 AND start_time < @end AND end_time >= @start
            ORDER BY start_time ASC;
        ";

        using var cmd = new SqliteCommand(sql, conn);
        cmd.Parameters.AddWithValue("@start", startIso);
        cmd.Parameters.AddWithValue("@end", endIso);

        using var reader = cmd.ExecuteReader();
        while (reader.Read())
        {
            list.Add(new ScheduleItem
            {
                Id = reader.GetString(0),
                Title = reader.GetString(1),
                Description = reader.IsDBNull(2) ? "" : reader.GetString(2),
                Category = reader.IsDBNull(3) ? "WORK" : reader.GetString(3),
                Priority = reader.IsDBNull(4) ? "MEDIUM" : reader.GetString(4),
                Status = reader.IsDBNull(5) ? "PENDING" : reader.GetString(5),
                StartTime = DateTime.TryParse(reader.GetString(6), out var st) ? st : start,
                EndTime = DateTime.TryParse(reader.GetString(7), out var et) ? et : end,
                EstimatedMinutes = reader.IsDBNull(8) ? 0 : reader.GetInt32(8),
                IsDeleted = reader.GetInt32(9) == 1
            });
        }

        return list;
    }

    public static void UpsertSchedule(ScheduleItem item)
    {
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        string sql = @"
            INSERT INTO schedules (id, title, description, category, priority, status, start_time, end_time, estimated_minutes, is_deleted, created_at, updated_at)
            VALUES (@id, @title, @description, @category, @priority, @status, @start_time, @end_time, @estimated_minutes, 0, @now, @now)
            ON CONFLICT(id) DO UPDATE SET
                title = excluded.title,
                description = excluded.description,
                category = excluded.category,
                start_time = excluded.start_time,
                end_time = excluded.end_time,
                estimated_minutes = excluded.estimated_minutes,
                updated_at = excluded.updated_at;
        ";

        using var cmd = new SqliteCommand(sql, conn);
        cmd.Parameters.AddWithValue("@id", item.Id);
        cmd.Parameters.AddWithValue("@title", item.Title);
        cmd.Parameters.AddWithValue("@description", item.Description);
        cmd.Parameters.AddWithValue("@category", item.Category);
        cmd.Parameters.AddWithValue("@priority", item.Priority);
        cmd.Parameters.AddWithValue("@status", item.Status);
        cmd.Parameters.AddWithValue("@start_time", item.StartTime.ToString("s"));
        cmd.Parameters.AddWithValue("@end_time", item.EndTime.ToString("s"));
        cmd.Parameters.AddWithValue("@estimated_minutes", item.EstimatedMinutes);
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

        string sql = "UPDATE schedules SET status = @status, updated_at = @now WHERE id = @id;";
        using var cmd = new SqliteCommand(sql, conn);
        cmd.Parameters.AddWithValue("@status", status);
        cmd.Parameters.AddWithValue("@now", DateTime.UtcNow.ToString("s"));
        cmd.Parameters.AddWithValue("@id", id);
        cmd.ExecuteNonQuery();
    }

    public static void DeleteSchedule(string id)
    {
        using var conn = new SqliteConnection(ConnectionString);
        conn.Open();

        string sql = "UPDATE schedules SET is_deleted = 1, updated_at = @now WHERE id = @id;";
        using var cmd = new SqliteCommand(sql, conn);
        cmd.Parameters.AddWithValue("@now", DateTime.UtcNow.ToString("s"));
        cmd.Parameters.AddWithValue("@id", id);
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
            // Background telemetry should never crash the app
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
