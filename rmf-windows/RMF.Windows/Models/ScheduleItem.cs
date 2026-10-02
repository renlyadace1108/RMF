using System;
using System.Text.Json.Serialization;

namespace RMF.Windows.Models;

public class ScheduleItem
{
    [JsonPropertyName("id")]
    public string Id { get; set; } = Guid.NewGuid().ToString();

    [JsonPropertyName("title")]
    public string Title { get; set; } = string.Empty;

    [JsonPropertyName("description")]
    public string Description { get; set; } = string.Empty;

    [JsonPropertyName("category")]
    public string Category { get; set; } = "WORK"; // WORK, STUDY, LIFE, HEALTH, FINANCE, OTHER

    [JsonPropertyName("priority")]
    public string Priority { get; set; } = "MEDIUM"; // LOW, MEDIUM, HIGH, URGENT

    [JsonPropertyName("status")]
    public string Status { get; set; } = "PENDING"; // PENDING, IN_PROGRESS, COMPLETED, POSTPONED

    [JsonPropertyName("start_time")]
    public DateTime StartTime { get; set; }

    [JsonPropertyName("end_time")]
    public DateTime EndTime { get; set; }

    [JsonPropertyName("estimated_minutes")]
    public int EstimatedMinutes { get; set; }

    [JsonPropertyName("is_deleted")]
    public bool IsDeleted { get; set; } = false;
}
