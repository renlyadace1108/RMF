using System;
using System.Text.Json.Serialization;

namespace RMF.Windows.Models;

public class DailyReportItem
{
    [JsonPropertyName("date")]
    public string Date { get; set; } = DateTime.Today.ToString("yyyy-MM-dd");

    [JsonPropertyName("reflection")]
    public string Reflection { get; set; } = string.Empty;

    [JsonPropertyName("ai_summary")]
    public string AiSummary { get; set; } = string.Empty;

    [JsonPropertyName("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [JsonPropertyName("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
