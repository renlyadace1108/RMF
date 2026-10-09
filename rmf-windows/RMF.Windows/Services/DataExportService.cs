using System;
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

        var bundle = new RmfFullDataBundle
        {
            Version = "2.0",
            ExportedAt = DateTime.UtcNow,
            Schedules = schedules,
            Goals = goals,
            StudyTopics = studyTopics,
            Courses = courses
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
    /// 导出为结构化 Markdown 归档文件
    /// </summary>
    public static string ExportFullDataMarkdown(string targetFilePath)
    {
        var schedules = DatabaseService.GetAllSchedules();
        var goals = DatabaseService.GetAllGoals();
        var studyTopics = DatabaseService.GetAllStudyTopics();
        var courses = DatabaseService.GetAllCourses();

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

        File.WriteAllText(targetFilePath, sb.ToString(), Encoding.UTF8);
        return targetFilePath;
    }
}
