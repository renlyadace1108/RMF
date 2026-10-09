using System;
using System.Collections.Generic;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace RMF.Windows.Models;

/// <summary>
/// 专业的单组训练记录模型 (对齐 Android 端 WorkoutSet 及 Hevy / Strong / 训记)
/// </summary>
public class WorkoutSetItem
{
    [JsonPropertyName("setNumber")]
    public int SetNumber { get; set; } = 1;

    [JsonPropertyName("type")]
    public string Type { get; set; } = "N"; // "W" = 热身组 (Warmup), "N" = 正式组 (Normal), "D" = 递减组 (Drop Set), "F" = 力竭组 (Failure)

    [JsonPropertyName("weightKg")]
    public double WeightKg { get; set; } = 0.0;

    [JsonPropertyName("reps")]
    public int Reps { get; set; } = 10;

    [JsonPropertyName("isCompleted")]
    public bool IsCompleted { get; set; } = false;

    [JsonPropertyName("rpe")]
    public double? Rpe { get; set; }

    [JsonIgnore]
    public string TypeLabel => (Type ?? "N").ToUpperInvariant() switch
    {
        "W" => "热身",
        "D" => "递减",
        "F" => "力竭",
        _ => "正式"
    };

    [JsonIgnore]
    public string TypeBadge => (Type ?? "N").ToUpperInvariant() switch
    {
        "W" => "W",
        "D" => "D",
        "F" => "F",
        _ => SetNumber.ToString()
    };

    /// <summary>
    /// 单组估算 1RM (Epley 公式: Weight * (1 + Reps / 30))
    /// </summary>
    [JsonIgnore]
    public double Estimated1RM
    {
        get
        {
            if (WeightKg <= 0.0 || Reps <= 0) return 0.0;
            if (Reps == 1) return WeightKg;
            return WeightKg * (1.0 + Reps / 30.0);
        }
    }

    /// <summary>
    /// 单组训练容量 (Weight * Reps)
    /// </summary>
    [JsonIgnore]
    public double VolumeKg => IsCompleted ? WeightKg * Reps : 0.0;

    public static string ToJsonArray(IEnumerable<WorkoutSetItem> sets)
    {
        try
        {
            return JsonSerializer.Serialize(sets);
        }
        catch
        {
            return "[]";
        }
    }

    public static List<WorkoutSetItem> FromJsonArray(string? json, int fallbackSetsCount = 4, int fallbackReps = 10, double fallbackWeight = 0.0)
    {
        if (string.IsNullOrWhiteSpace(json) || json.Trim() == "[]")
        {
            var fallbackList = new List<WorkoutSetItem>();
            int count = fallbackSetsCount <= 0 ? 4 : fallbackSetsCount;
            for (int i = 1; i <= count; i++)
            {
                fallbackList.Add(new WorkoutSetItem
                {
                    SetNumber = i,
                    Type = i == 1 && fallbackWeight > 20 ? "W" : "N",
                    WeightKg = i == 1 && fallbackWeight > 20 ? Math.Round(fallbackWeight * 0.6) : fallbackWeight,
                    Reps = fallbackReps,
                    IsCompleted = false
                });
            }
            return fallbackList;
        }

        try
        {
            var list = JsonSerializer.Deserialize<List<WorkoutSetItem>>(json);
            if (list == null || list.Count == 0)
            {
                return FromJsonArray(null, fallbackSetsCount, fallbackReps, fallbackWeight);
            }
            return list;
        }
        catch
        {
            return FromJsonArray(null, fallbackSetsCount, fallbackReps, fallbackWeight);
        }
    }
}
