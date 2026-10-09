using System;
using System.Collections.Generic;
using System.Linq;
using System.Text.Json.Serialization;

namespace RMF.Windows.Models;

public class FitnessRecordItem
{
    [JsonPropertyName("id")]
    public string Id { get; set; } = Guid.NewGuid().ToString("N");

    [JsonPropertyName("plan_id")]
    public string PlanId { get; set; } = string.Empty;

    [JsonPropertyName("record_date")]
    public string RecordDate { get; set; } = DateTime.Today.ToString("yyyy-MM-dd");

    [JsonPropertyName("exercise_name")]
    public string ExerciseName { get; set; } = string.Empty;

    [JsonPropertyName("category")]
    public string Category { get; set; } = "CHEST"; // CHEST, BACK, LEGS, SHOULDERS, ARMS, CORE, CARDIO, STRETCH

    [JsonPropertyName("equipment")]
    public string Equipment { get; set; } = string.Empty;

    [JsonPropertyName("sets_data")]
    public string SetsData { get; set; } = "[]";

    [JsonPropertyName("sets_count")]
    public int SetsCount { get; set; } = 4;

    [JsonPropertyName("reps_per_set")]
    public int RepsPerSet { get; set; } = 10;

    [JsonPropertyName("weight_kg")]
    public double WeightKg { get; set; } = 0.0;

    [JsonPropertyName("sort_order")]
    public int SortOrder { get; set; } = 0;

    [JsonPropertyName("distance_km")]
    public double DistanceKm { get; set; } = 0.0;

    [JsonPropertyName("duration_minutes")]
    public int DurationMinutes { get; set; } = 0;

    [JsonPropertyName("calories_burned")]
    public double CaloriesBurned { get; set; } = 0.0;

    [JsonPropertyName("is_completed")]
    public bool IsCompleted { get; set; } = false;

    [JsonPropertyName("note")]
    public string Note { get; set; } = string.Empty;

    [JsonPropertyName("is_deleted")]
    public bool IsDeleted { get; set; } = false;

    [JsonPropertyName("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [JsonPropertyName("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

    public List<WorkoutSetItem> GetWorkoutSets()
    {
        return WorkoutSetItem.FromJsonArray(SetsData, SetsCount, RepsPerSet, WeightKg);
    }

    [JsonIgnore]
    public double BestEstimated1RM
    {
        get
        {
            var sets = GetWorkoutSets();
            return sets.Count > 0 ? sets.Max(s => s.Estimated1RM) : 0.0;
        }
    }

    [JsonIgnore]
    public double CompletedVolumeKg
    {
        get
        {
            var sets = GetWorkoutSets();
            return sets.Where(s => s.IsCompleted).Sum(s => s.VolumeKg);
        }
    }

    [JsonIgnore]
    public int CompletedSetsCount
    {
        get
        {
            var sets = GetWorkoutSets();
            return sets.Count(s => s.IsCompleted);
        }
    }

    [JsonIgnore]
    public int TargetSets
    {
        get => SetsCount;
        set => SetsCount = value;
    }

    [JsonIgnore]
    public int TargetReps
    {
        get => RepsPerSet;
        set => RepsPerSet = value;
    }

    [JsonIgnore]
    public double TargetWeightKg
    {
        get => WeightKg;
        set => WeightKg = value;
    }

    [JsonIgnore]
    public string Notes
    {
        get => Note;
        set => Note = value;
    }
}
