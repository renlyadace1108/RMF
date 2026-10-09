using System;
using System.Text.Json.Serialization;

namespace RMF.Windows.Models;

public class FitnessPlanItem
{
    [JsonPropertyName("id")]
    public string Id { get; set; } = Guid.NewGuid().ToString("N");

    [JsonPropertyName("plan_date")]
    public string PlanDate { get; set; } = DateTime.Today.ToString("yyyy-MM-dd");

    [JsonPropertyName("title")]
    public string Title { get; set; } = "今日体能强化";

    [JsonPropertyName("workout_type")]
    public string WorkoutType { get; set; } = "STRENGTH"; // STRENGTH, CARDIO, HIIT, STRETCH, OTHER

    [JsonPropertyName("target_duration_minutes")]
    public int TargetDurationMinutes { get; set; } = 45;

    [JsonPropertyName("actual_duration_minutes")]
    public int ActualDurationMinutes { get; set; } = 0;

    [JsonPropertyName("calories_burned")]
    public double CaloriesBurned { get; set; } = 0.0;

    [JsonPropertyName("feeling")]
    public string Feeling { get; set; } = "MODERATE"; // EASY, MODERATE, HARD

    [JsonPropertyName("status")]
    public string Status { get; set; } = "PLANNED"; // PLANNED, COMPLETED, SKIPPED

    [JsonPropertyName("notes")]
    public string Notes { get; set; } = string.Empty;

    [JsonPropertyName("is_deleted")]
    public bool IsDeleted { get; set; } = false;

    [JsonPropertyName("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [JsonPropertyName("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

    [JsonIgnore]
    public bool IsCompleted
    {
        get => Status == "COMPLETED";
        set => Status = value ? "COMPLETED" : "PLANNED";
    }

    [JsonIgnore]
    public string FeelingRating
    {
        get => Feeling;
        set => Feeling = value;
    }
}
