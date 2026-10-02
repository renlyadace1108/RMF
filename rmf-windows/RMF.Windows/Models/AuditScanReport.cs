using System;
using System.Collections.Generic;

namespace RMF.Windows.Models;

public class AuditIssue
{
    /// <summary>
    /// DOD_MISSING, OVERLOAD_CIRCUIT_BREAK, MISSING_BUFFER, CHRONOTYPE_MISMATCH
    /// </summary>
    public string IssueType { get; set; } = string.Empty;

    /// <summary>
    /// CRITICAL (熔断/违规), WARNING (风险), INFO (提示)
    /// </summary>
    public string Severity { get; set; } = "WARNING";

    public string Title { get; set; } = string.Empty;
    public string Description { get; set; } = string.Empty;
    public string? RelatedScheduleId { get; set; }
}

public class AuditScanReport
{
    public DateTime Date { get; set; } = DateTime.Today;

    /// <summary>
    /// 排期健康体检评分 0 ~ 100
    /// </summary>
    public int OverallHealthScore { get; set; } = 100;

    /// <summary>
    /// 今日排期总工时 (小时)
    /// </summary>
    public double TotalScheduledHours { get; set; }

    /// <summary>
    /// 今日深度工作时长 (小时)
    /// </summary>
    public double DeepWorkHours { get; set; }

    /// <summary>
    /// 认知负荷硬顶上限 (默认 4.5 小时)
    /// </summary>
    public double DeepWorkCapHours { get; set; } = 4.5;

    public bool IsCognitiveOverloaded => DeepWorkHours > DeepWorkCapHours;

    /// <summary>
    /// DoD 验收标准完备度 (百分比 0 ~ 100)
    /// </summary>
    public double DoDComplianceRatio { get; set; } = 100.0;

    /// <summary>
    /// 缺少转场缓冲的违规次数 (无缝背靠背排期)
    /// </summary>
    public int MissingBufferCount { get; set; }

    /// <summary>
    /// 精力曲线错位次数 (低谷排高难任务，或上午峰值排琐碎杂务)
    /// </summary>
    public int ChronotypeMismatchCount { get; set; }

    public List<AuditIssue> Issues { get; set; } = new();
    public List<string> Recommendations { get; set; } = new();
}
