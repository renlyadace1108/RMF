using System;
using System.Collections.Generic;

namespace RMF.Windows.Models;

public class TimePnLReport
{
    public DateTime PeriodStart { get; set; }
    public DateTime PeriodEnd { get; set; }

    /// <summary>
    /// 营业有效工时 (小时): 实际交付的高价值深度任务时长
    /// </summary>
    public double OperatingEffectiveHours { get; set; }

    /// <summary>
    /// 管理损耗/坏账工时 (小时): 外部打断、拖延、废弃时长
    /// </summary>
    public double BadDebtHours { get; set; }

    /// <summary>
    /// 预算赤字 (小时): 实际超出预估的差额
    /// </summary>
    public double BudgetDeficitHours { get; set; }

    /// <summary>
    /// 规划总预算工时 (小时)
    /// </summary>
    public double PlannedBudgetHours { get; set; }

    /// <summary>
    /// 实际总投入工时 (小时)
    /// </summary>
    public double TotalActualHours { get; set; }

    /// <summary>
    /// 有效利用率 = (有效工时 - 坏账工时) / 实际总工时
    /// </summary>
    public double ProfitMarginRatio => TotalActualHours > 0 
        ? Math.Max(0.0, (OperatingEffectiveHours - BadDebtHours) / TotalActualHours * 100.0) 
        : 100.0;

    /// <summary>
    /// 历史乐观偏差率 alpha
    /// </summary>
    public double OptimismAlpha { get; set; } = 1.35;

    /// <summary>
    /// 超时最显著的前几项任务归因
    /// </summary>
    public List<ScheduleItem> OverrunTasks { get; set; } = new();

    /// <summary>
    /// 外部打断最严重的任务
    /// </summary>
    public List<ScheduleItem> InterruptedTasks { get; set; } = new();
}
