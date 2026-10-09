using System;
using System.Collections.Generic;
using System.Linq;
using System.Text.Json;
using RMF.Windows.Models;

namespace RMF.Windows.Services;

public class DataLineageService
{
    public class DodItem
    {
        public string Id { get; set; } = Guid.NewGuid().ToString("N");
        public string Text { get; set; } = string.Empty;
        public bool Done { get; set; } = false;
        public double Weight { get; set; } = 1.0;
    }

    /// <summary>
    /// 解析 DoD 文本（支持 JSON 数组或标准多行 [ ] / [x] 列表格式）
    /// </summary>
    public static List<DodItem> ParseDodItems(string? dodText)
    {
        var list = new List<DodItem>();
        if (string.IsNullOrWhiteSpace(dodText)) return list;

        string trimmed = dodText.Trim();
        if (trimmed.StartsWith("[") && trimmed.EndsWith("]"))
        {
            try
            {
                var parsed = JsonSerializer.Deserialize<List<DodItem>>(trimmed);
                if (parsed != null && parsed.Count > 0) return parsed;
            }
            catch { }
        }

        // 兼容多行文本清单: "[x] 完成模块测试" 或 "- [ ] 编写文档" 或直接单行文本
        var lines = trimmed.Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries);
        int idx = 1;
        foreach (var rawLine in lines)
        {
            string line = rawLine.Trim();
            if (string.IsNullOrEmpty(line)) continue;

            bool isDone = false;
            string cleanText = line;

            if (line.StartsWith("- [x]", StringComparison.OrdinalIgnoreCase) || line.StartsWith("[x]", StringComparison.OrdinalIgnoreCase))
            {
                isDone = true;
                cleanText = line.Substring(line.IndexOf(']') + 1).Trim();
            }
            else if (line.StartsWith("- [ ]", StringComparison.OrdinalIgnoreCase) || line.StartsWith("[ ]", StringComparison.OrdinalIgnoreCase))
            {
                isDone = false;
                cleanText = line.Substring(line.IndexOf(']') + 1).Trim();
            }
            else if (line.StartsWith("- "))
            {
                cleanText = line.Substring(2).Trim();
            }

            list.Add(new DodItem
            {
                Id = $"dod_{idx++}",
                Text = cleanText,
                Done = isDone,
                Weight = 1.0
            });
        }

        return list;
    }

    /// <summary>
    /// 将 DoD 项列表序列化为 JSON 字符串
    /// </summary>
    public static string SerializeDodItems(List<DodItem> items)
    {
        if (items == null || items.Count == 0) return string.Empty;
        return JsonSerializer.Serialize(items);
    }

    /// <summary>
    /// 计算 DoD 完成百分比 (0 ~ 100)
    /// </summary>
    public static int CalculateDodCompletion(string? dodText)
    {
        var items = ParseDodItems(dodText);
        if (items.Count == 0) return 0;

        double totalWeight = items.Sum(i => Math.Max(0.1, i.Weight));
        double doneWeight = items.Where(i => i.Done).Sum(i => Math.Max(0.1, i.Weight));

        if (totalWeight <= 0) return 0;
        return (int)Math.Clamp(Math.Round((doneWeight / totalWeight) * 100.0), 0, 100);
    }

    /// <summary>
    /// 日程完成打卡时的血缘后置处理：学时反哺 + 驱动 OKR 进度
    /// </summary>
    public static void OnScheduleCompleted(ScheduleItem schedule)
    {
        if (schedule == null) return;

        // 1. 学时自动反哺 (Overview -> Study)
        CascadeStudyHours(schedule);

        // 2. 目标进度步进 (DoD -> OKR)
        CascadeGoalProgress(schedule);
    }

    /// <summary>
    /// 当日程中的 DoD 被勾选切换时触发
    /// </summary>
    public static void OnScheduleDodToggled(ScheduleItem schedule)
    {
        if (schedule == null) return;
        DatabaseService.UpsertSchedule(schedule);
        CascadeGoalProgress(schedule);
    }

    private static void CascadeStudyHours(ScheduleItem schedule)
    {
        try
        {
            // 判定是否属于学习类日程
            bool isStudy = string.Equals(schedule.Category, "STUDY", StringComparison.OrdinalIgnoreCase)
                           || schedule.Title.Contains("学习")
                           || schedule.Title.Contains("复习")
                           || schedule.Title.Contains("课程")
                           || schedule.Title.Contains("阅读")
                           || schedule.Description.Contains("#Study")
                           || schedule.Description.Contains("#学习")
                           || !string.IsNullOrEmpty(schedule.StudyTopicId);

            if (!isStudy) return;

            // [防作弊校验] 学习学时必须经由实际计时（如 HUD 悬浮胶囊/专注时钟）真实记录，禁止未计时的预估或默认推算计入
            if (schedule.ActualMinutes <= 0) return;

            double actualMins = schedule.ActualMinutes;
            double hoursToAdd = actualMins / 60.0;
            if (hoursToAdd <= 0) return;

            string? targetTopicId = schedule.StudyTopicId;

            // 若未直接绑定，尝试按日程标题/分类智能匹配在研的主题档案
            if (string.IsNullOrEmpty(targetTopicId))
            {
                var allTopics = DatabaseService.GetAllStudyTopics().Where(t => t.Status != "COMPLETED").ToList();
                var matched = allTopics.FirstOrDefault(t => schedule.Title.Contains(t.Title, StringComparison.OrdinalIgnoreCase))
                           ?? allTopics.FirstOrDefault(t => schedule.Description.Contains(t.Title, StringComparison.OrdinalIgnoreCase))
                           ?? allTopics.FirstOrDefault();

                if (matched != null)
                {
                    targetTopicId = matched.Id;
                    schedule.StudyTopicId = targetTopicId;
                    DatabaseService.UpsertSchedule(schedule);
                }
            }

            if (!string.IsNullOrEmpty(targetTopicId))
            {
                DatabaseService.AddStudyHours(targetTopicId, hoursToAdd);
            }
        }
        catch { }
    }

    private static void CascadeGoalProgress(ScheduleItem schedule)
    {
        if (string.IsNullOrEmpty(schedule.GoalId)) return;

        try
        {
            var allGoals = DatabaseService.GetAllGoals();
            var targetGoal = allGoals.FirstOrDefault(g => g.Id == schedule.GoalId);
            if (targetGoal == null) return;

            // 获取绑定到该目标的所有日程
            var relatedSchedules = DatabaseService.GetAllSchedules()
                .Where(s => s.GoalId == targetGoal.Id)
                .ToList();

            if (relatedSchedules.Count > 0)
            {
                int totalSchedules = relatedSchedules.Count;
                double totalScore = 0.0;

                foreach (var s in relatedSchedules)
                {
                    if (s.Status == "COMPLETED")
                    {
                        totalScore += 100.0;
                    }
                    else
                    {
                        int dodPercent = CalculateDodCompletion(s.Dod);
                        totalScore += dodPercent;
                    }
                }

                int newProgress = (int)Math.Clamp(Math.Round(totalScore / totalSchedules), 0, 100);
                targetGoal.Progress = newProgress;
                DatabaseService.UpsertGoal(targetGoal);

                // 自下而上递归向上级目标传递 (如 周任务 -> 季度目标 -> 年度目标)
                PropagateGoalHierarchy(targetGoal.ParentId, allGoals);
            }
        }
        catch { }
    }

    private static void PropagateGoalHierarchy(string? parentId, List<GoalItem> allGoals)
    {
        if (string.IsNullOrEmpty(parentId)) return;

        var parent = allGoals.FirstOrDefault(g => g.Id == parentId);
        if (parent == null) return;

        var siblings = allGoals.Where(g => g.ParentId == parentId).ToList();
        if (siblings.Count > 0)
        {
            parent.Progress = (int)Math.Clamp(Math.Round(siblings.Average(s => s.Progress)), 0, 100);
            DatabaseService.UpsertGoal(parent);

            // 递归至根节点
            PropagateGoalHierarchy(parent.ParentId, allGoals);
        }
    }
}
