using System;
using System.Collections.Generic;
using System.Text.Json.Serialization;

namespace RMF.Windows.Models;

public class GoalItem
{
    [JsonPropertyName("id")]
    public string Id { get; set; } = Guid.NewGuid().ToString();

    [JsonPropertyName("title")]
    public string Title { get; set; } = string.Empty;

    /// <summary>
    /// VISION (长期愿景), OBJECTIVE (季度/月度目标), KEY_TASK (周关键任务)
    /// </summary>
    [JsonPropertyName("level")]
    public string Level { get; set; } = "OBJECTIVE";

    [JsonPropertyName("parent_id")]
    public string? ParentId { get; set; }

    [JsonPropertyName("quarter")]
    public string Quarter { get; set; } = "2026-Q4";

    [JsonPropertyName("progress")]
    public int Progress { get; set; } = 0;

    [JsonPropertyName("sort_order")]
    public int SortOrder { get; set; } = 0;

    [JsonPropertyName("is_deleted")]
    public bool IsDeleted { get; set; } = false;

    [JsonPropertyName("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    /// <summary>
    /// 是否为常驻置顶北极星目标 (North Star Pin)
    /// </summary>
    [JsonPropertyName("is_north_star")]
    public bool IsNorthStar { get; set; } = false;

    /// <summary>
    /// 周期中途置信度自评 (0.1 ~ 1.0)
    /// </summary>
    [JsonPropertyName("confidence")]
    public double Confidence { get; set; } = 1.0;

    // 非数据库序列化辅助属性
    [JsonIgnore]
    public List<GoalItem> Children { get; set; } = new();

    [JsonIgnore]
    public bool IsExpanded { get; set; } = true;
}
