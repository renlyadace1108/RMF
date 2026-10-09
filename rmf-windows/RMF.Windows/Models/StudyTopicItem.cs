using System;
using System.Text.Json.Serialization;

namespace RMF.Windows.Models;

public class StudyTopicItem
{
    [JsonPropertyName("id")]
    public string Id { get; set; } = Guid.NewGuid().ToString();

    [JsonPropertyName("title")]
    public string Title { get; set; } = string.Empty;

    [JsonPropertyName("category")]
    public string Category { get; set; } = "技术栈"; // 技术栈, 计算机基础, 外语, 读书精读, 资格考证, 兴趣

    [JsonPropertyName("status")]
    public string Status { get; set; } = "IN_PROGRESS"; // NOT_STARTED, IN_PROGRESS, COMPLETED

    [JsonPropertyName("progress_percent")]
    public int ProgressPercent { get; set; } = 0; // 0 - 100

    [JsonPropertyName("progress_type")]
    public string ProgressType { get; set; } = "HOURS"; // HOURS (学时), LESSONS (节数), PAGES (页数), CUSTOM (自定义)

    [JsonPropertyName("custom_unit")]
    public string CustomUnit { get; set; } = string.Empty; // 自定义单位名称，例如 "题", "词", "讲", "章"

    [JsonPropertyName("target_value")]
    public double TargetValue { get; set; } = 10.0; // 目标总量 (时长/节数/页数/自定义数量)

    [JsonPropertyName("completed_value")]
    public double CompletedValue { get; set; } = 0.0; // 当前已完成量

    [JsonPropertyName("target_hours")]
    public double TargetHours { get; set; } = 10.0;

    [JsonPropertyName("completed_hours")]
    public double CompletedHours { get; set; } = 0.0;

    [JsonPropertyName("current_checkpoint")]
    public string CurrentCheckpoint { get; set; } = string.Empty; // 当前正在读的章节/核心实验

    [JsonPropertyName("notes")]
    public string Notes { get; set; } = string.Empty; // 学习心得或资料链接

    [JsonPropertyName("is_deleted")]
    public bool IsDeleted { get; set; } = false;

    [JsonPropertyName("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [JsonPropertyName("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
