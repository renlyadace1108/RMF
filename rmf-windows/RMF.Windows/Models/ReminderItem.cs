using System;

namespace RMF.Windows.Models;

public class ReminderItem
{
    public string Id { get; set; } = Guid.NewGuid().ToString();
    public string Title { get; set; } = string.Empty;
    public string Content { get; set; } = string.Empty;
    public string RemindTime { get; set; } = DateTime.Now.ToString("yyyy-MM-ddTHH:mm:ss");
    public int AdvanceMinutes { get; set; } = 0; // 0 = 准时
    public string ReminderType { get; set; } = "ALL"; // ALL, ALARM, VIBRATE, NONE
    public string RepeatRule { get; set; } = "NONE"; // NONE, DAILY, WEEKDAYS, WEEKLY, MONTHLY, YEARLY
    public string Category { get; set; } = "REMINDER"; // REMINDER, MEMO, LIFE, WORK, HEALTH
    public string Priority { get; set; } = "MEDIUM"; // HIGH, MEDIUM, LOW
    public bool IsCompleted { get; set; } = false;
    public bool IsDeleted { get; set; } = false;
    public string CreatedAt { get; set; } = DateTime.Now.ToString("yyyy-MM-ddTHH:mm:ss");
    public string UpdatedAt { get; set; } = DateTime.Now.ToString("yyyy-MM-ddTHH:mm:ss");

    public string DisplayTime
    {
        get
        {
            if (DateTime.TryParse(RemindTime, out var dt))
            {
                return dt.ToString("yyyy-MM-dd HH:mm");
            }
            return RemindTime;
        }
    }

    public string PriorityText => Priority switch
    {
        "HIGH" => "🔴 高",
        "LOW" => "🟢 低",
        _ => "🟡 中"
    };

    public string PriorityBadgeColor => Priority switch
    {
        "HIGH" => "#EF4444",
        "LOW" => "#10B981",
        _ => "#F59E0B"
    };

    public string RepeatRuleText => RepeatRule switch
    {
        "DAILY" => "每天",
        "WEEKDAYS" => "工作日",
        "WEEKLY" => "每周",
        "MONTHLY" => "每月",
        "YEARLY" => "每年",
        _ => "单次"
    };

    public string CategoryText => Category switch
    {
        "MEMO" => "📝 备忘",
        "LIFE" => "🌱 生活",
        "WORK" => "💼 工作",
        "HEALTH" => "💊 健康",
        _ => "🔔 提醒"
    };

    public bool IsOverdue
    {
        get
        {
            if (IsCompleted) return false;
            if (DateTime.TryParse(RemindTime, out var dt))
            {
                return dt < DateTime.Now;
            }
            return false;
        }
    }
}
