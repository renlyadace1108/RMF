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

    // === 5大核心模块扩展字段 ===

    /// <summary>
    /// 目标层级树父级关联 (Vision/Objective/KeyTask Id)
    /// </summary>
    [JsonPropertyName("goal_id")]
    public string? GoalId { get; set; }

    /// <summary>
    /// 三级认知负荷类型: DEEP_WORK (深度工作), SHALLOW_WORK (浅层事务), REST_BUFFER (强制休息/缓冲)
    /// </summary>
    [JsonPropertyName("work_type")]
    public string WorkType { get; set; } = "DEEP_WORK";

    /// <summary>
    /// 明确的 Definition of Done (验收标准质检)
    /// </summary>
    [JsonPropertyName("dod")]
    public string Dod { get; set; } = string.Empty;

    /// <summary>
    /// 实际耗时分钟数 (用于计算乐观偏差乘数 alpha 与 P&L 赤字)
    /// </summary>
    [JsonPropertyName("actual_minutes")]
    public int ActualMinutes { get; set; } = 0;

    /// <summary>
    /// 管理损耗与外部打断时长 (分钟)
    /// </summary>
    [JsonPropertyName("interruption_minutes")]
    public int InterruptionMinutes { get; set; } = 0;

    /// <summary>
    /// 是否剥离归入延期冻结池 (Deferred Queue)
    /// </summary>
    [JsonPropertyName("is_deferred")]
    public bool IsDeferred { get; set; } = false;

    /// <summary>
    /// 是否为待办敏捷池未排期任务 (Backlog)
    /// </summary>
    [JsonPropertyName("is_backlog")]
    public bool IsBacklog { get; set; } = false;

    /// <summary>
    /// 同步版本号
    /// </summary>
    [JsonPropertyName("sync_version")]
    public int SyncVersion { get; set; } = 1;

    /// <summary>
    /// 本地未推送变更标记 (用于增量同步遥测)
    /// </summary>
    [JsonPropertyName("is_dirty")]
    public bool IsDirty { get; set; } = false;
}
