using System;
using System.Collections.Generic;
using System.Linq;
using RMF.Windows.Models;

namespace RMF.Windows.Services;

public class SpacedRepetitionService
{
    // 经典艾宾浩斯记忆遗忘曲线复习间隔（天数）
    private static readonly int[] EbbinghausIntervalDays = { 1, 3, 7, 15, 30 };

    /// <summary>
    /// 为指定知识点/学习主题生成 5 级艾宾浩斯渐进复习任务并存入待办敏捷池
    /// </summary>
    public static List<ScheduleItem> GenerateEbbinghausTasks(string topicTitle, string? studyTopicId = null, DateTime? baseDate = null)
    {
        var start = baseDate ?? DateTime.Today;
        var created = new List<ScheduleItem>();

        for (int i = 0; i < EbbinghausIntervalDays.Length; i++)
        {
            int days = EbbinghausIntervalDays[i];
            DateTime targetDate = start.AddDays(days);

            var item = new ScheduleItem
            {
                Id = Guid.NewGuid().ToString(),
                Title = $"🔁 艾宾浩斯复习第 {i + 1} 轮 (+{days}天): {topicTitle}",
                Description = $"针对「{topicTitle}」的科学间隔复习，巩固长期记忆突触。\n基准日期: {start:yyyy-MM-dd}，阶段: 第 {i + 1}/5 周期",
                Category = "复习",
                Priority = (i <= 1) ? "HIGH" : "MEDIUM",
                WorkType = "DEEP_WORK",
                Dod = $"闭卷复述「{topicTitle}」核心要点与脉络，独立默写/答辩无误",
                StartTime = targetDate.AddHours(9),
                EndTime = targetDate.AddHours(9).AddMinutes(25),
                EstimatedMinutes = 25,
                IsBacklog = true, // 初始置于待办敏捷池
                StudyTopicId = studyTopicId,
                Source = "🧠 艾宾浩斯引擎"
            };

            DatabaseService.UpsertSchedule(item);
            created.Add(item);
        }

        return created;
    }

    public class VelocityForecastResult
    {
        public double RemainingHours { get; set; }
        public double DailyVelocityHours { get; set; }
        public int EstimatedDaysRemaining { get; set; }
        public DateTime EstimatedCompletionDate { get; set; }
        public string StatusSummary { get; set; } = string.Empty;
    }

    /// <summary>
    /// <summary>
    /// 结业配速预测 (Velocity & Forecasting): 根据近 7 天该学习主题的实际工时与完成速率，动态预测预期结业日期
    /// </summary>
    public static VelocityForecastResult ForecastStudyCompletion(StudyTopicItem topic)
    {
        string unit = topic.ProgressType switch
        {
            "LESSONS" => "节",
            "PAGES" => "页",
            "CUSTOM" => string.IsNullOrWhiteSpace(topic.CustomUnit) ? "项" : topic.CustomUnit,
            _ => "h"
        };

        double targetVal = topic.TargetValue > 0 ? topic.TargetValue : (topic.TargetHours > 0 ? topic.TargetHours : 10.0);
        double completedVal = topic.CompletedValue > 0 ? topic.CompletedValue : topic.CompletedHours;
        double remaining = Math.Max(0, targetVal - completedVal);

        if (remaining <= 0 || topic.ProgressPercent >= 100)
        {
            return new VelocityForecastResult
            {
                RemainingHours = 0,
                DailyVelocityHours = 0,
                EstimatedDaysRemaining = 0,
                EstimatedCompletionDate = DateTime.Today,
                StatusSummary = "🎉 已圆满达成该学习主题目标！"
            };
        }

        // 计算近 7 天内完成的该主题日程工时与记录
        DateTime sevenDaysAgo = DateTime.Today.AddDays(-7);
        var recentSchedules = DatabaseService.GetSchedulesForDateRange(sevenDaysAgo, DateTime.Today.AddDays(1))
            .Where(s => s.Status == "COMPLETED" && (s.StudyTopicId == topic.Id || s.Category == topic.Category || s.Title.Contains(topic.Title)))
            .ToList();

        double totalRecentMinutes = recentSchedules.Sum(s => s.ActualMinutes > 0 ? s.ActualMinutes : s.EstimatedMinutes);
        double recentHours = totalRecentMinutes / 60.0;

        if (topic.ProgressType == "HOURS" || string.IsNullOrEmpty(topic.ProgressType))
        {
            // 时长模式：直接以小时速率推算
            double dailyVelocity = recentHours > 0 ? (recentHours / 7.0) : 0.5;
            int daysNeeded = (int)Math.Ceiling(remaining / dailyVelocity);
            DateTime expectedDate = DateTime.Today.AddDays(daysNeeded);

            string paceDesc = recentHours > 0 
                ? $"近 7 天投入 {recentHours:F1}h (平均 {dailyVelocity:F1}h/天)" 
                : "按基准 0.5h/天估算";

            return new VelocityForecastResult
            {
                RemainingHours = Math.Round(remaining, 1),
                DailyVelocityHours = Math.Round(dailyVelocity, 2),
                EstimatedDaysRemaining = daysNeeded,
                EstimatedCompletionDate = expectedDate,
                StatusSummary = $"剩余 {remaining:F1}h · {paceDesc} · 预计于 {expectedDate:yyyy-MM-dd} ({daysNeeded}天后) 结业"
            };
        }
        else
        {
            // 节数/页数/自定义模式：依据已完成量推算平均推进步长或默认每日节奏
            double dailyVelocity = completedVal > 0 ? Math.Max(0.2, completedVal / Math.Max(1, (DateTime.Today - topic.CreatedAt.Date).Days + 1)) : 1.0;
            int daysNeeded = (int)Math.Ceiling(remaining / dailyVelocity);
            DateTime expectedDate = DateTime.Today.AddDays(daysNeeded);

            return new VelocityForecastResult
            {
                RemainingHours = Math.Round(remaining, 1),
                DailyVelocityHours = Math.Round(dailyVelocity, 2),
                EstimatedDaysRemaining = daysNeeded,
                EstimatedCompletionDate = expectedDate,
                StatusSummary = $"剩余 {remaining:0.#} {unit} · 配速约 {dailyVelocity:0.#} {unit}/天 · 预计于 {expectedDate:yyyy-MM-dd} ({daysNeeded}天后) 达成"
            };
        }
    }
}
