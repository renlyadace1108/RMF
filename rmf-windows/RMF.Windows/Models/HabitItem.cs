using System;

namespace RMF.Windows.Models;

public class HabitItem
{
    public string Id { get; set; } = Guid.NewGuid().ToString();
    public string Title { get; set; } = string.Empty;
    public string Icon { get; set; } = "✨";
    public int TargetDaysPerWeek { get; set; } = 7;
    public string ColorHex { get; set; } = "#38BDF8";
    public int CurrentStreak { get; set; } = 0;
    public int BestStreak { get; set; } = 0;
    public string HistoryDatesJson { get; set; } = "[]";
    public string ReminderTime { get; set; } = string.Empty;
    public bool IsDeleted { get; set; } = false;
    public string CreatedAt { get; set; } = DateTime.Now.ToString("yyyy-MM-ddTHH:mm:ss");
    public string UpdatedAt { get; set; } = DateTime.Now.ToString("yyyy-MM-ddTHH:mm:ss");
}
