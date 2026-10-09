using System;

namespace RMF.Windows.Models;

public class EffectiveCourseSlot
{
    public string CourseId { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public string CourseName
    {
        get => Name;
        set => Name = value;
    }
    public string Location { get; set; } = string.Empty;
    public string Teacher { get; set; } = string.Empty;
    public int DayOfWeek { get; set; }
    public DateTime StartTime { get; set; }
    public DateTime EndTime { get; set; }
    public string ColorHex { get; set; } = "#3B82F6";
    public int StartSection { get; set; }
    public int SectionSpan { get; set; }
    public bool IsAdjustment { get; set; }
    public string? AdjustmentType { get; set; }
}
