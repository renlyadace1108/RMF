using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using RMF.Windows.Models;

namespace RMF.Windows.Services;

public class ReportingEngineService
{
    public class WeeklyAuditResult
    {
        public DateTime StartDate { get; set; }
        public DateTime EndDate { get; set; }
        public double PlannedHours { get; set; }
        public double ActualHours { get; set; }
        public double InterruptionHours { get; set; }
        public double NetFocusHours { get; set; }
        public double FocusScore { get; set; } // 0 ~ 100
        public int TotalTasks { get; set; }
        public int CompletedTasks { get; set; }
        public int DodDeliveryRate { get; set; } // 0 ~ 100
        public Dictionary<string, int> InterruptionCounts { get; set; } = new();
        public double DeepWorkRatio { get; set; } // 0 ~ 100
    }

    /// <summary>
    /// 生成本周效能对账审计数据
    /// </summary>
    public static WeeklyAuditResult GenerateWeeklyAudit(DateTime referenceDate)
    {
        int diff = ((int)referenceDate.DayOfWeek + 6) % 7;
        DateTime monday = referenceDate.Date.AddDays(-diff);
        DateTime nextMonday = monday.AddDays(7);

        var schedules = DatabaseService.GetSchedulesForDateRange(monday, nextMonday);
        var interruptions = DatabaseService.GetInterruptionsForDateRange(monday, nextMonday);

        var result = new WeeklyAuditResult
        {
            StartDate = monday,
            EndDate = nextMonday.AddDays(-1),
            TotalTasks = schedules.Count,
            CompletedTasks = schedules.Count(s => s.Status == "COMPLETED")
        };

        double plannedMins = schedules.Sum(s => s.EstimatedMinutes > 0 ? s.EstimatedMinutes : (s.EndTime - s.StartTime).TotalMinutes);
        double actualMins = schedules.Sum(s => s.ActualMinutes > 0 ? s.ActualMinutes : (s.Status == "COMPLETED" ? (s.EndTime - s.StartTime).TotalMinutes : 0));
        double interruptMins = schedules.Sum(s => s.InterruptionMinutes) + (interruptions.Sum(i => i.DurationSeconds) / 60.0);

        result.PlannedHours = Math.Round(plannedMins / 60.0, 1);
        result.ActualHours = Math.Round(actualMins / 60.0, 1);
        result.InterruptionHours = Math.Round(interruptMins / 60.0, 1);
        result.NetFocusHours = Math.Round(Math.Max(0, actualMins - interruptMins) / 60.0, 1);

        result.FocusScore = (result.ActualHours > 0)
            ? Math.Clamp(Math.Round((result.NetFocusHours / result.ActualHours) * 100.0, 1), 0, 100)
            : 100.0;

        int totalDodPercent = 0;
        int dodCount = 0;
        foreach (var s in schedules)
        {
            if (!string.IsNullOrEmpty(s.Dod))
            {
                totalDodPercent += DataLineageService.CalculateDodCompletion(s.Dod);
                dodCount++;
            }
            else if (s.Status == "COMPLETED")
            {
                totalDodPercent += 100;
                dodCount++;
            }
        }
        result.DodDeliveryRate = (dodCount > 0) ? (int)Math.Round((double)totalDodPercent / dodCount) : 100;

        // 打断诱因统计
        result.InterruptionCounts["外部协作"] = interruptions.Count(i => i.Type == "EXTERNAL");
        result.InterruptionCounts["紧急插单"] = interruptions.Count(i => i.Type == "URGENT_INSERT");
        result.InterruptionCounts["内部走神"] = interruptions.Count(i => i.Type == "DISTRACTION");

        // 深度工作占比
        double deepMins = schedules.Where(s => s.WorkType == "DEEP_WORK").Sum(s => s.ActualMinutes > 0 ? s.ActualMinutes : s.EstimatedMinutes);
        result.DeepWorkRatio = (actualMins > 0) ? Math.Clamp(Math.Round((deepMins / actualMins) * 100.0), 0, 100) : 75;

        return result;
    }

    /// <summary>
    /// 导出标准 Markdown 周度对账报告
    /// </summary>
    public static string GenerateWeeklyMarkdownReport(WeeklyAuditResult audit)
    {
        var sb = new StringBuilder();
        sb.AppendLine($"# 📊 RMF 周度效能对账审计报告");
        sb.AppendLine($"**审计周期**: {audit.StartDate:yyyy-MM-dd} ~ {audit.EndDate:yyyy-MM-dd}");
        sb.AppendLine($"**生成时间**: {DateTime.Now:yyyy-MM-dd HH:mm:ss}\n");

        sb.AppendLine("## 一、 工时与心流对账");
        sb.AppendLine($"* **计划总预算**: `{audit.PlannedHours:F1} 小时`");
        sb.AppendLine($"* **实际总投入**: `{audit.ActualHours:F1} 小时`");
        sb.AppendLine($"* **打断损耗工时**: `{audit.InterruptionHours:F1} 小时`");
        sb.AppendLine($"* **净深度工时**: `{audit.NetFocusHours:F1} 小时`");
        sb.AppendLine($"* **专注度评分**: `{audit.FocusScore:F1} / 100`\n");

        sb.AppendLine("## 二、 交付与质量指标");
        sb.AppendLine($"* **任务完成率**: `{audit.CompletedTasks} / {audit.TotalTasks}`");
        sb.AppendLine($"* **DoD 验收标准交付率**: `{audit.DodDeliveryRate}%`");
        sb.AppendLine($"* **深度工作负荷比例**: `{audit.DeepWorkRatio}%`\n");

        sb.AppendLine("## 三、 核心打断损耗归因");
        sb.AppendLine($"* 👥 外部协作打断: `{audit.InterruptionCounts.GetValueOrDefault("外部协作", 0)} 次`");
        sb.AppendLine($"* 🚨 紧急插单打扰: `{audit.InterruptionCounts.GetValueOrDefault("紧急插单", 0)} 次`");
        sb.AppendLine($"* 💭 内部走神中断: `{audit.InterruptionCounts.GetValueOrDefault("内部走神", 0)} 次`\n");

        sb.AppendLine("---");
        sb.AppendLine("*由 RMF (Renly Management Platform) 战略战报引擎自动生成*");
        return sb.ToString();
    }

    public static string GenerateWeeklyAuditMarkdown(DateTime startOfWeek, DateTime endOfWeek)
    {
        var audit = GenerateWeeklyAudit(startOfWeek);
        return GenerateWeeklyMarkdownReport(audit);
    }

    public static string ExportWeeklyAuditPoster(DateTime startOfWeek, DateTime endOfWeek, string targetPath)
    {
        var audit = GenerateWeeklyAudit(startOfWeek);
        return ExportPosterImage(audit, targetPath);
    }

    /// <summary>
    /// 将周度对账报告离线渲染为高清长图海报并保存到本地
    /// </summary>
    public static string ExportPosterImage(WeeklyAuditResult audit, string? customFilePath = null)
    {
        string filePath = customFilePath ?? "";
        if (string.IsNullOrEmpty(filePath))
        {
            string exportDir = Path.Combine(DatabaseService.DbDir, "Exports");
            if (!Directory.Exists(exportDir)) Directory.CreateDirectory(exportDir);
            filePath = Path.Combine(exportDir, $"RMF_WeeklyAudit_{audit.StartDate:yyyyMMdd}.png");
        }

        // 构造一个 600px 宽度的离线 WPF 卡片
        var card = new Border
        {
            Width = 600,
            Background = new SolidColorBrush(Color.FromRgb(0x18, 0x19, 0x1C)),
            BorderBrush = new SolidColorBrush(Color.FromRgb(0x3B, 0x82, 0xF6)),
            BorderThickness = new Thickness(2),
            CornerRadius = new CornerRadius(16),
            Padding = new Thickness(32)
        };

        var sp = new StackPanel();

        // 标题
        sp.Children.Add(new TextBlock
        {
            Text = "📊 RMF 个人战略效能对账战报",
            FontSize = 22,
            FontWeight = FontWeights.Bold,
            Foreground = new SolidColorBrush(Color.FromRgb(0x38, 0xBD, 0xF8)),
            Margin = new Thickness(0, 0, 0, 6)
        });

        sp.Children.Add(new TextBlock
        {
            Text = $"周期: {audit.StartDate:yyyy-MM-dd} ~ {audit.EndDate:yyyy-MM-dd} · 审计归档",
            FontSize = 13,
            Foreground = new SolidColorBrush(Color.FromRgb(0x94, 0xA3, 0xB8)),
            Margin = new Thickness(0, 0, 0, 20)
        });

        // 核心指标网格
        var grid = new UniformGrid { Columns = 3, Margin = new Thickness(0, 0, 0, 24) };

        void AddMetric(string label, string val, Color c)
        {
            var b = new Border
            {
                Background = new SolidColorBrush(Color.FromRgb(0x22, 0x24, 0x2A)),
                CornerRadius = new CornerRadius(10),
                Padding = new Thickness(14, 12, 14, 12),
                Margin = new Thickness(4)
            };
            var st = new StackPanel();
            st.Children.Add(new TextBlock { Text = label, FontSize = 11, Foreground = new SolidColorBrush(Color.FromRgb(0x94, 0xA3, 0xB8)) });
            st.Children.Add(new TextBlock { Text = val, FontSize = 20, FontWeight = FontWeights.Bold, Foreground = new SolidColorBrush(c), Margin = new Thickness(0, 4, 0, 0) });
            b.Child = st;
            grid.Children.Add(b);
        }

        AddMetric("净深度工时", $"{audit.NetFocusHours:F1}h", Color.FromRgb(0x38, 0xBD, 0xF8));
        AddMetric("专注度评分", $"{audit.FocusScore:F0}%", Color.FromRgb(0x10, 0xB9, 0x81));
        AddMetric("DoD 交付率", $"{audit.DodDeliveryRate}%", Color.FromRgb(0xF5, 0x9E, 0x0B));
        AddMetric("计划投入", $"{audit.PlannedHours:F1}h", Color.FromRgb(0xE2, 0xE8, 0xF0));
        AddMetric("实际投入", $"{audit.ActualHours:F1}h", Color.FromRgb(0xA8, 0x55, 0xF7));
        AddMetric("打断损耗", $"{audit.InterruptionHours:F1}h", Color.FromRgb(0xEF, 0x44, 0x44));

        sp.Children.Add(grid);

        // 打断分析
        var interruptBox = new Border
        {
            Background = new SolidColorBrush(Color.FromRgb(0x20, 0x22, 0x27)),
            CornerRadius = new CornerRadius(10),
            Padding = new Thickness(16),
            Margin = new Thickness(0, 0, 0, 20)
        };
        var iStack = new StackPanel();
        iStack.Children.Add(new TextBlock { Text = "⚡ 打断诱因审计统计", FontSize = 13, FontWeight = FontWeights.SemiBold, Foreground = new SolidColorBrush(Color.FromRgb(0xF5, 0x9E, 0x0B)), Margin = new Thickness(0, 0, 0, 8) });
        iStack.Children.Add(new TextBlock { Text = $"• 外部协作打断: {audit.InterruptionCounts.GetValueOrDefault("外部协作", 0)} 次   • 紧急插单: {audit.InterruptionCounts.GetValueOrDefault("紧急插单", 0)} 次   • 内部走神: {audit.InterruptionCounts.GetValueOrDefault("内部走神", 0)} 次", FontSize = 12, Foreground = new SolidColorBrush(Color.FromRgb(0xCB, 0xD5, 0xE1)) });
        interruptBox.Child = iStack;
        sp.Children.Add(interruptBox);

        // 底部水印
        sp.Children.Add(new TextBlock
        {
            Text = "Renly Management Platform · 全流程感知与多周期战略复盘中枢",
            FontSize = 10.5,
            Foreground = new SolidColorBrush(Color.FromRgb(0x64, 0x74, 0x8B)),
            HorizontalAlignment = HorizontalAlignment.Center
        });

        card.Child = sp;

        // 测量并布局
        card.Measure(new Size(600, double.PositiveInfinity));
        card.Arrange(new Rect(0, 0, 600, card.DesiredSize.Height));
        card.UpdateLayout();

        int width = (int)card.ActualWidth;
        int height = (int)card.ActualHeight;

        var rtb = new RenderTargetBitmap(width, height, 96, 96, PixelFormats.Pbgra32);
        rtb.Render(card);

        var encoder = new PngBitmapEncoder();
        encoder.Frames.Add(BitmapFrame.Create(rtb));
        using var stream = new FileStream(filePath, FileMode.Create);
        encoder.Save(stream);

        return filePath;
    }
}
