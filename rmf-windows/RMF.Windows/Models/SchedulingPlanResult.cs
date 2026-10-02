using System;
using System.Collections.Generic;

namespace RMF.Windows.Models;

public class RejectedTaskInfo
{
    public ScheduleItem Task { get; set; } = new();
    public string Reason { get; set; } = string.Empty;
    public string SuggestedDod { get; set; } = string.Empty;
}

public class SchedulingPlanResult
{
    public DateTime TargetDate { get; set; } = DateTime.Today;

    /// <summary>
    /// 拟合排期生成的时间块（包含深度工作、浅层事务与强制插入的缓冲块）
    /// </summary>
    public List<ScheduleItem> ScheduledBlocks { get; set; } = new();

    /// <summary>
    /// 因超出当日 4.5h 认知负荷硬顶被强行熔断并剥离延期的任务
    /// </summary>
    public List<ScheduleItem> DeferredTasks { get; set; } = new();

    /// <summary>
    /// 因未填充明确 Definition of Done 被质检驳回、禁止直接排期的任务
    /// </summary>
    public List<RejectedTaskInfo> RejectedTasks { get; set; } = new();

    /// <summary>
    /// 客观调度决策日志与归因说明
    /// </summary>
    public List<string> DecisionLogs { get; set; } = new();

    /// <summary>
    /// 拟排入的总深度工作小时数
    /// </summary>
    public double ScheduledDeepWorkHours { get; set; }

    /// <summary>
    /// 是否触发了排期熔断机制
    /// </summary>
    public bool IsCircuitBroken { get; set; }

    /// <summary>
    /// 应用的历史乐观偏差乘数 alpha
    /// </summary>
    public double AppliedAlpha { get; set; } = 1.35;
}
