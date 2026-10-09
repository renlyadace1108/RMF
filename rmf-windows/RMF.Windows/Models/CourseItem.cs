using System;
using System.Text.Json.Serialization;

namespace RMF.Windows.Models;

public class CourseItem
{
    [JsonPropertyName("id")]
    public string Id { get; set; } = Guid.NewGuid().ToString();

    [JsonPropertyName("name")]
    public string Name { get; set; } = string.Empty;

    [JsonPropertyName("teacher")]
    public string Teacher { get; set; } = string.Empty;

    [JsonPropertyName("location")]
    public string Location { get; set; } = string.Empty;

    /// <summary>
    /// 1 = 周一, 2 = 周二, ..., 7 = 周日
    /// </summary>
    [JsonPropertyName("day_of_week")]
    public int DayOfWeek { get; set; } = 1;

    /// <summary>
    /// 起始节次 (1 到 12)
    /// </summary>
    [JsonPropertyName("start_section")]
    public int StartSection { get; set; } = 1;

    /// <summary>
    /// 连堂节数 (如 2 节)
    /// </summary>
    [JsonPropertyName("section_span")]
    public int SectionSpan { get; set; } = 2;

    [JsonPropertyName("start_week")]
    public int StartWeek { get; set; } = 1;

    [JsonPropertyName("end_week")]
    public int EndWeek { get; set; } = 16;

    /// <summary>
    /// ALL = 每周, ODD = 单周, EVEN = 双周
    /// </summary>
    [JsonPropertyName("week_type")]
    public string WeekType { get; set; } = "ALL";

    /// <summary>
    /// 马卡龙专属颜色 Hex 码，如 #3B82F6
    /// </summary>
    [JsonPropertyName("color_hex")]
    public string ColorHex { get; set; } = "#3B82F6";

    [JsonPropertyName("notes")]
    public string Notes { get; set; } = string.Empty;

    /// <summary>
    /// 是否使用自由自定义起止时间 (不拘泥于固定节次)
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
    /// 结课考试/考核日期 (生成考期倒计时)
    /// </summary>
    [JsonPropertyName("exam_date")]
    public DateTime? ExamDate { get; set; }

    /// <summary>
    /// 课件资料超链接
    /// </summary>
    [JsonPropertyName("course_url")]
    public string CourseUrl { get; set; } = string.Empty;

    /// <summary>
    /// 教室编码
    /// </summary>
    [JsonPropertyName("room_code")]
    public string RoomCode { get; set; } = string.Empty;

    [JsonPropertyName("is_deleted")]
    public bool IsDeleted { get; set; } = false;

    [JsonPropertyName("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [JsonPropertyName("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

    /// <summary>
    /// 判断在指定周数是否开课
    /// </summary>
    public bool IsActiveInWeek(int week)
    {
        if (week < StartWeek || week > EndWeek) return false;
        if (WeekType == "ODD" && week % 2 == 0) return false;
        if (WeekType == "EVEN" && week % 2 != 0) return false;
        return true;
    }
}
