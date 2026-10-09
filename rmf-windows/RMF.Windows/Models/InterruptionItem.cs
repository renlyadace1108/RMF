using System;
using System.Text.Json.Serialization;

namespace RMF.Windows.Models;

public class InterruptionItem
{
    [JsonPropertyName("id")]
    public string Id { get; set; } = Guid.NewGuid().ToString();

    [JsonPropertyName("schedule_id")]
    public string ScheduleId { get; set; } = string.Empty;

    /// <summary>
    /// 打断类型: EXTERNAL (外部协作), URGENT_INSERT (紧急插单), DISTRACTION (内部走神)
    /// </summary>
    [JsonPropertyName("type")]
    public string Type { get; set; } = "EXTERNAL";

    [JsonPropertyName("duration_seconds")]
    public int DurationSeconds { get; set; } = 0;

    [JsonPropertyName("timestamp")]
    public DateTime Timestamp { get; set; } = DateTime.Now;

    [JsonPropertyName("note")]
    public string Note { get; set; } = string.Empty;
}
