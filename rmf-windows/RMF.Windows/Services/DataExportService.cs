using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.Json;
using RMF.Windows.Models;

namespace RMF.Windows.Services;

public class DataExportService
{
    public class RmfFullDataBundle
    {
        public string Version { get; set; } = "2.0";
        public DateTime ExportedAt { get; set; } = DateTime.UtcNow;
        public object Schedules { get; set; } = new();
        public object Goals { get; set; } = new();
        public object StudyTopics { get; set; } = new();
        public object Courses { get; set; } = new();
        public object Notes { get; set; } = new();
        public object Reminders { get; set; } = new();
        public object TimeMarkers { get; set; } = new();
        public object Habits { get; set; } = new();
        public object Countdowns { get; set; } = new();
    }

    /// <summary>
    /// 全量数据一键导出为通用 JSON 物理包
    /// </summary>
    public static string ExportFullDataJson(string targetFilePath)
    {
        var schedules = DatabaseService.GetAllSchedules();
        var goals = DatabaseService.GetAllGoals();
        var studyTopics = DatabaseService.GetAllStudyTopics();
        var courses = DatabaseService.GetAllCourses();
        var notes = DatabaseService.GetAllNotes();
        var reminders = DatabaseService.GetAllReminders();
        var timeMarkers = DatabaseService.GetAllTimeMarkers();
        var habits = DatabaseService.GetAllHabits();
        var countdowns = DatabaseService.GetAllCountdowns();

        var bundle = new RmfFullDataBundle
        {
            Version = "2.0",
            ExportedAt = DateTime.UtcNow,
            Schedules = schedules,
            Goals = goals,
            StudyTopics = studyTopics,
            Courses = courses,
            Notes = notes,
            Reminders = reminders,
            TimeMarkers = timeMarkers,
            Habits = habits,
            Countdowns = countdowns
        };

        var options = new JsonSerializerOptions
        {
            WriteIndented = true,
            Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping
        };

        string json = JsonSerializer.Serialize(bundle, options);
        File.WriteAllText(targetFilePath, json, Encoding.UTF8);
        return targetFilePath;
    }

    /// <summary>
    /// 从外部备份 JSON 文件导入并智能合并全量数据
    /// </summary>
    public static (bool success, string message) ImportFullDataJson(string jsonFilePath)
    {
        try
        {
            if (!File.Exists(jsonFilePath)) return (false, "文件不存在");

            string json = File.ReadAllText(jsonFilePath, Encoding.UTF8);
            using var doc = JsonDocument.Parse(json);
            var root = doc.RootElement;

            int noteCount = 0, remCount = 0, markerCount = 0, habitCount = 0, cdCount = 0;

            if (root.TryGetProperty("Notes", out var notesElem) || root.TryGetProperty("notes", out notesElem))
            {
                var notes = JsonSerializer.Deserialize<List<NoteItem>>(notesElem.GetRawText());
                if (notes != null)
                {
                    foreach (var n in notes) { DatabaseService.UpsertNote(n); noteCount++; }
                }
            }

            if (root.TryGetProperty("Reminders", out var remElem) || root.TryGetProperty("reminders", out remElem))
            {
                var reminders = JsonSerializer.Deserialize<List<ReminderItem>>(remElem.GetRawText());
                if (reminders != null)
                {
                    foreach (var r in reminders) { DatabaseService.UpsertReminder(r); remCount++; }
                }
            }

            if (root.TryGetProperty("TimeMarkers", out var markElem) || root.TryGetProperty("timeMarkers", out markElem))
            {
                var markers = JsonSerializer.Deserialize<List<TimeMarkerItem>>(markElem.GetRawText());
                if (markers != null)
                {
                    foreach (var m in markers) { DatabaseService.UpsertTimeMarker(m); markerCount++; }
                }
            }

            if (root.TryGetProperty("Habits", out var habitElem) || root.TryGetProperty("habits", out habitElem))
            {
                var habits = JsonSerializer.Deserialize<List<HabitItem>>(habitElem.GetRawText());
                if (habits != null)
                {
                    foreach (var h in habits) { DatabaseService.UpsertHabit(h); habitCount++; }
                }
            }

            if (root.TryGetProperty("Countdowns", out var cdElem) || root.TryGetProperty("countdowns", out cdElem))
            {
                var countdowns = JsonSerializer.Deserialize<List<CountdownItem>>(cdElem.GetRawText());
                if (countdowns != null)
                {
                    foreach (var cd in countdowns) { DatabaseService.UpsertCountdown(cd); cdCount++; }
                }
            }

            // 导入后重新核验并排期所有待办事项
            WindowsReminderScheduler.RescheduleAll();

            return (true, $"已成功导入并合并：便签 {noteCount} 条，提醒事项 {remCount} 条，时间标记 {markerCount} 条，习惯 {habitCount} 条，倒数日 {cdCount} 条！");
        }
        catch (Exception ex)
        {
            return (false, $"导入失败: {ex.Message}");
        }
    }

    /// <summary>
    /// 导出为结构化 Markdown 归档文件
    /// </summary>
    public static string ExportFullDataMarkdown(string targetFilePath)
    {
        var schedules = DatabaseService.GetAllSchedules();
        var goals = DatabaseService.GetAllGoals();
        var studyTopics = DatabaseService.GetAllStudyTopics();
        var courses = DatabaseService.GetAllCourses();
        var notes = DatabaseService.GetAllNotes();
        var reminders = DatabaseService.GetAllReminders();
        var timeMarkers = DatabaseService.GetAllTimeMarkers();
        var habits = DatabaseService.GetAllHabits();
        var countdowns = DatabaseService.GetAllCountdowns();

        var sb = new StringBuilder();
        sb.AppendLine("# 📦 RMF 全量数据战略归档备份");
        sb.AppendLine($"*导出时间: {DateTime.Now:yyyy-MM-dd HH:mm:ss}*\n");

        sb.AppendLine("## 一、 战略目标体系 (OKR)");
        foreach (var g in goals)
        {
            sb.AppendLine($"- [{g.Level}] **{g.Title}** ({g.Progress}%)" + (g.IsNorthStar ? " ⭐[北极星]" : ""));
        }
        sb.AppendLine();

        sb.AppendLine("## 二、 学习攻坚档案 (Study & Mastery)");
        foreach (var st in studyTopics)
        {
            sb.AppendLine($"- **{st.Title}** [{st.Category}] 已学: {st.CompletedHours}h / 目标: {st.TargetHours}h ({st.ProgressPercent}%)");
        }
        sb.AppendLine();

        sb.AppendLine("## 三、 课程清单 (Courses)");
        foreach (var c in courses)
        {
            sb.AppendLine($"- 周{c.DayOfWeek} 第{c.StartSection}节 **{c.Name}** @ {c.Location} ({c.Teacher})");
        }
        sb.AppendLine();

        sb.AppendLine($"## 四、 日程与待办事项 (共 {schedules.Count} 条)");
        foreach (var s in schedules.OrderByDescending(x => x.StartTime).Take(200))
        {
            string statusIcon = s.Status == "COMPLETED" ? "✅" : "⏳";
            sb.AppendLine($"- {statusIcon} `{s.StartTime:yyyy-MM-dd HH:mm}` **{s.Title}** [{s.WorkType}]" + (!string.IsNullOrEmpty(s.Dod) ? $" DoD: {s.Dod}" : ""));
        }
        sb.AppendLine();

        sb.AppendLine($"## 五、 便签中心 (共 {notes.Count} 条)");
        foreach (var n in notes)
        {
            sb.AppendLine($"- [{(n.IsPinned ? "📌" : "📄")}] **{n.Title}** [{n.Category}]: {n.Content}");
        }
        sb.AppendLine();

        sb.AppendLine($"## 六、 提醒事项 (共 {reminders.Count} 条)");
        foreach (var r in reminders)
        {
            string statusIcon = r.IsCompleted ? "✅" : "🔔";
            sb.AppendLine($"- {statusIcon} `{r.RemindTime}` [{r.Priority}] **{r.Title}** ({r.RepeatRuleText})");
        }
        sb.AppendLine();

        sb.AppendLine($"## 七、 习惯打卡与倒数日");
        sb.AppendLine("### 习惯项:");
        foreach (var h in habits)
        {
            sb.AppendLine($"- {h.Icon} **{h.Title}** (连续: {h.CurrentStreak}天 / 最佳: {h.BestStreak}天)");
        }
        sb.AppendLine("### 倒数日:");
        foreach (var cd in countdowns)
        {
            sb.AppendLine($"- [{(cd.IsPinned ? "📌" : "⏳")}] **{cd.Title}** 目标: {cd.TargetDate} (剩余 {cd.RemainingDays} 天)");
        }

        File.WriteAllText(targetFilePath, sb.ToString(), Encoding.UTF8);
        return targetFilePath;
    }
}
