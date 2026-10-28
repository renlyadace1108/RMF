using System;
using System.Collections.Generic;
using System.Linq;
using RMF.Windows.Models;

namespace RMF.Windows.Services;

public class EstimationVarianceReport
{
    public int TotalTasksEvaluated { get; set; }
    public int TotalEstimatedMinutes { get; set; }
    public int TotalActualMinutes { get; set; }
    public int VarianceMinutes => TotalActualMinutes - TotalEstimatedMinutes;
    public double VariancePercentage => TotalEstimatedMinutes > 0 ? ((double)VarianceMinutes / TotalEstimatedMinutes) * 100.0 : 0.0;
    public double AccuracyScore { get; set; } // 0 - 100%
    public List<CategoryVarianceMetric> CategoryMetrics { get; set; } = new();
    public string Recommendation { get; set; } = "";
}

public class CategoryVarianceMetric
{
    public string Category { get; set; } = "";
    public int TaskCount { get; set; }
    public int EstimatedMinutes { get; set; }
    public int ActualMinutes { get; set; }
    public double Factor => EstimatedMinutes > 0 ? (double)ActualMinutes / EstimatedMinutes : 1.0;
    public string StatusText => Factor > 1.15 ? $"超时 +{(int)((Factor - 1) * 100)}%" : (Factor < 0.85 ? $"提前 {(int)((1 - Factor) * 100)}%" : "准时对齐");
}

public static class EstimationVarianceService
{
    /// <summary>
    /// 分析已完成日程的计划 vs 实际耗时偏差，输出预估准确率与分类偏差因子
    /// </summary>
    public static EstimationVarianceReport Analyze(List<ScheduleItem> schedules)
    {
        var evaluated = schedules
            .Where(s => s.Status == "COMPLETED" && s.EstimatedMinutes > 0 && s.ActualMinutes > 0)
            .ToList();

        var report = new EstimationVarianceReport
        {
            TotalTasksEvaluated = evaluated.Count,
            TotalEstimatedMinutes = evaluated.Sum(s => s.EstimatedMinutes),
            TotalActualMinutes = evaluated.Sum(s => s.ActualMinutes)
        };

        if (evaluated.Count == 0)
        {
            report.Recommendation = "暂无带有实际耗时记录的已完成任务。在任务打卡或专注结束时记录耗时，即可自动生成个人专属偏差校准模型。";
            return report;
        }

        int accurateCount = evaluated.Count(s => Math.Abs(s.ActualMinutes - s.EstimatedMinutes) <= 10);
        report.AccuracyScore = Math.Round((double)accurateCount / evaluated.Count * 100.0, 1);

        var groups = evaluated.GroupBy(s => string.IsNullOrWhiteSpace(s.Category) ? "WORK" : s.Category);
        foreach (var g in groups)
        {
            report.CategoryMetrics.Add(new CategoryVarianceMetric
            {
                Category = g.Key,
                TaskCount = g.Count(),
                EstimatedMinutes = g.Sum(s => s.EstimatedMinutes),
                ActualMinutes = g.Sum(s => s.ActualMinutes)
            });
        }

        var overrunningCat = report.CategoryMetrics.OrderByDescending(c => c.Factor).FirstOrDefault(c => c.Factor > 1.2);
        if (overrunningCat != null)
        {
            report.Recommendation = $"💡 偏差洞察：你在【{overrunningCat.Category}】类任务上实际耗时平均为预估的 {overrunningCat.Factor:F2} 倍（平均超期 {(int)((overrunningCat.Factor - 1) * 100)}%），排期时建议主动预留 15~20 分钟缓冲，或将大任务拆分为小颗粒度子行动。";
        }
        else
        {
            report.Recommendation = $"🌟 节奏卓越：你的任务预估准确率达 {report.AccuracyScore:F0}%，计划与实际执行高度拟合，精力感知能力非常精准！";
        }

        return report;
    }

    /// <summary>
    /// 获取历史未完成的逾期排期任务
    /// </summary>
    public static List<ScheduleItem> GetPastUnfinishedTasks()
    {
        var today = DateTime.Today;
        return DatabaseService.GetAllSchedules()
            .Where(s => !s.IsDeleted && s.Status != "COMPLETED" && s.Status != "ABANDONED" && !s.IsBacklog && s.StartTime.Date < today)
            .OrderBy(s => s.StartTime)
            .ToList();
    }

    /// <summary>
    /// 一键将过去未完成任务智能顺延至今日排期
    /// </summary>
    public static int SpilloverTasksToToday(List<ScheduleItem> tasks)
    {
        if (tasks == null || tasks.Count == 0) return 0;
        var now = DateTime.Now;

        DateTime nextStart = now.AddMinutes(15);
        int remMin = nextStart.Minute % 15;
        if (remMin > 0) nextStart = nextStart.AddMinutes(15 - remMin);

        int count = 0;
        foreach (var task in tasks)
        {
            int duration = task.EstimatedMinutes > 0 ? task.EstimatedMinutes : (int)(task.EndTime - task.StartTime).TotalMinutes;
            if (duration <= 0) duration = 30;

            task.StartTime = nextStart;
            task.EndTime = nextStart.AddMinutes(duration);
            task.EstimatedMinutes = duration;
            task.IsDeferred = true;
            task.IsDirty = true;
            task.SyncVersion++;
            task.UpdatedAt = DateTime.UtcNow;
            DatabaseService.UpsertSchedule(task);

            nextStart = task.EndTime.AddMinutes(5);
            count++;
        }

        DatabaseService.NotifyDataChanged();
        return count;
    }

    /// <summary>
    /// 将过去未完成任务安全移入待办箱备选
    /// </summary>
    public static int MovePastTasksToBacklog(List<ScheduleItem> tasks)
    {
        if (tasks == null || tasks.Count == 0) return 0;
        int count = 0;
        foreach (var task in tasks)
        {
            task.IsBacklog = true;
            task.IsDirty = true;
            task.SyncVersion++;
            task.UpdatedAt = DateTime.UtcNow;
            DatabaseService.UpsertSchedule(task);
            count++;
        }
        DatabaseService.NotifyDataChanged();
        return count;
    }
}
