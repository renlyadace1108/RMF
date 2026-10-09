using System;
using System.Text.Json.Serialization;

namespace RMF.Windows.Models;

public class CourseAdjustmentItem
{
    [JsonPropertyName("id")]
    public string Id { get; set; } = Guid.NewGuid().ToString();

    /// <summary>
    /// 关联的原课程 ID (如果是停课或调课，关联原课；临时加课可为空)
    /// </summary>
    [JsonPropertyName("course_id")]
    public string CourseId { get; set; } = string.Empty;

    /// <summary>
    /// 课程名称 (冗余记录或临时加课的名称)
    /// </summary>
    [JsonPropertyName("course_name")]
    public string CourseName { get; set; } = string.Empty;

    /// <summary>
    /// 调整类型:
    /// SUSPEND - 停课 (在特定周暂停原课程)
    /// RESCHEDULE - 调课 (在特定周调整上课日期、节次或教室)
    /// MAKEUP - 补课/临时加课 (在特定周临时新增一堂课)
    /// </summary>
    [JsonPropertyName("adjustment_type")]
    public string AdjustmentType { get; set; } = "SUSPEND";

    /// <summary>
    /// 生效的目标周数 (如第 5 周)
    /// </summary>
    [JsonPropertyName("target_week")]
    public int TargetWeek { get; set; } = 1;

    /// <summary>
    /// 原上课星期 (1-7)
    /// </summary>
    [JsonPropertyName("orig_day_of_week")]
    public int OrigDayOfWeek { get; set; } = 1;

    /// <summary>
    /// 原起始节次 (1-12)
    /// </summary>
    [JsonPropertyName("orig_start_section")]
    public int OrigStartSection { get; set; } = 1;

    /// <summary>
    /// 新上课星期 (调课/补课时使用, 1-7)
    /// </summary>
    [JsonPropertyName("new_day_of_week")]
    public int NewDayOfWeek { get; set; } = 1;

    /// <summary>
    /// 新起始节次 (调课/补课时使用, 1-12)
    /// </summary>
    [JsonPropertyName("new_start_section")]
    public int NewStartSection { get; set; } = 1;

    /// <summary>
    /// 连堂节数 (如 2 节)
    /// </summary>
    [JsonPropertyName("section_span")]
    public int SectionSpan { get; set; } = 2;

    /// <summary>
    /// 新上课教室 (可留空沿用原教室)
    /// </summary>
    [JsonPropertyName("new_location")]
    public string NewLocation { get; set; } = string.Empty;

    /// <summary>
    /// 是否使用自由自定义起止时间 (针对特定天更改具体时间，而不按照固定节次)
    /// </summary>
    [JsonPropertyName("is_custom_time")]
    public bool IsCustomTime { get; set; } = false;

    /// <summary>
    /// 自定义开始时间 (格式如 "15:30")
    /// </summary>
    [JsonPropertyName("custom_start_time")]
    public string CustomStartTime { get; set; } = string.Empty;

    /// <summary>
    /// 自定义结束时间 (格式如 "17:15")
    /// </summary>
    [JsonPropertyName("custom_end_time")]
    public string CustomEndTime { get; set; } = string.Empty;

    /// <summary>
    /// 调停课原因或说明 (如 "国庆调休"、"任课教师出差停课"、"考前集中答疑")
    /// </summary>
    [JsonPropertyName("reason")]
    public string Reason { get; set; } = string.Empty;

    [JsonPropertyName("is_deleted")]
    public bool IsDeleted { get; set; } = false;

    [JsonPropertyName("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [JsonPropertyName("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
