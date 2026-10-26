using System;

namespace RMF.Windows.Models;

public class TimeMarkerItem
{
    public string Id { get; set; } = Guid.NewGuid().ToString();
    public string Name { get; set; } = string.Empty; // 起床, 睡觉, 午休, 实际开工, 实际收工
    public string MarkerType { get; set; } = "POINT"; // POINT (时间点) or RANGE (时间段)
    public string Category { get; set; } = "LIFESTYLE"; // SLEEP, REST, MEAL, WORK, EXERCISE, CUSTOM
    public string Icon { get; set; } = "📍";
    public string Date { get; set; } = DateTime.Today.ToString("yyyy-MM-dd");
    public string StartTime { get; set; } = DateTime.Now.ToString("yyyy-MM-ddTHH:mm:ss");
    public string EndTime { get; set; } = string.Empty;
    public string ColorHex { get; set; } = "#38BDF8";
    public string Notes { get; set; } = string.Empty;
    public bool IsDeleted { get; set; } = false;
    public string CreatedAt { get; set; } = DateTime.Now.ToString("yyyy-MM-ddTHH:mm:ss");
    public string UpdatedAt { get; set; } = DateTime.Now.ToString("yyyy-MM-ddTHH:mm:ss");

    public string FormattedTimeSpan
    {
        get
        {
            if (DateTime.TryParse(StartTime, out var sDt))
            {
                if (MarkerType == "RANGE" && DateTime.TryParse(EndTime, out var eDt))
                {
                    int spanMin = (int)(eDt - sDt).TotalMinutes;
                    return $"{sDt:HH:mm} ~ {eDt:HH:mm} ({spanMin}分钟)";
                }
                return sDt.ToString("HH:mm");
            }
            return StartTime;
        }
    }
}
