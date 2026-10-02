using System;
using System.Collections.Generic;
using System.Linq;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using RMF.Windows.Models;

namespace RMF.Windows.Services;

public class SchedulerAuditEngine
{
    public const int MaxDailyDeepWorkMinutes = 270; // 4.5 小时生理硬性上限
    public const int TransitionBufferMinutes = 15; // 强制休息缓冲时长

    private static readonly string[] VagueKeywords = new[]
    {
        "写代码", "看论文", "复习", "看书", "学习", "做项目", "搞一下", "写需求",
        "coding", "work", "study", "code", "dev", "research", "paper", "reading"
    };

    // =========================================================================
    // ================= 规则 A: 任务量化质检 (DoD 强制检查) =====================
    // =========================================================================

    public static (bool passed, string reason, string suggestedDod) InspectDoD(ScheduleItem item)
    {
        // 强制休息缓冲不需要 DoD
        if (item.WorkType == "REST_BUFFER")
        {
            return (true, "强制休息缓冲无需 DoD", string.Empty);
        }

        string title = item.Title.Trim();
        string dod = item.Dod?.Trim() ?? string.Empty;

        // 1. 如果有填写 DoD，进一步校验是否具备量化特征或成果描述
        if (!string.IsNullOrWhiteSpace(dod))
        {
            if (dod.Length < 3 || dod.Equals("ok", StringComparison.OrdinalIgnoreCase) || dod.Equals("done", StringComparison.OrdinalIgnoreCase))
            {
                return (false, "验收标准 (DoD) 过于简略，缺乏明确可检验产出标准", GenerateHeuristicDod(title));
            }
            return (true, "DoD 质检通过", dod);
        }

        // 2. 如果未填写 DoD，检查任务标题是否为模糊宽泛的动词词组
        bool isVague = VagueKeywords.Any(k => title.Contains(k, StringComparison.OrdinalIgnoreCase)) || title.Length <= 2;
        if (isVague)
        {
            return (false, "任务意图模糊且未量化，未填充明确 Definition of Done", GenerateHeuristicDod(title));
        }

        // 3. 其它默认情况：若无 DoD 且属于深度工作，建议补充明确可验证成果
        if (item.WorkType == "DEEP_WORK")
        {
            return (false, "深度工作 (DeepWork) 必须设定明确的验收标准 (DoD)", GenerateHeuristicDod(title));
        }

        return (true, "浅层事务免检放行", string.Empty);
    }

    public static string GenerateHeuristicDod(string title)
    {
        if (title.Contains("论文") || title.Contains("paper", StringComparison.OrdinalIgnoreCase))
            return "提炼 3 点核心贡献并撰写 200 字对比分析笔记";
        if (title.Contains("代码") || title.Contains("code", StringComparison.OrdinalIgnoreCase) || title.Contains("dev", StringComparison.OrdinalIgnoreCase))
            return "跑通相关单元测试并提交 Pull Request";
        if (title.Contains("复习") || title.Contains("学习") || title.Contains("study", StringComparison.OrdinalIgnoreCase))
            return "完成 1 套专项自测题目并归纳错题知识卡片";
        if (title.Contains("项目") || title.Contains("需求"))
            return "输出已定稿的交互流程图并通过技术评审";
        if (title.Contains("书") || title.Contains("reading", StringComparison.OrdinalIgnoreCase))
            return "梳理本章思维导图并提取 2 条可执行行动项";

        return "交付指定的可验证输出物并通过自测验收";
    }

    public async Task<string> SuggestDoDWithAiAsync(string title, GeminiService geminiService)
    {
        try
        {
            var config = ConfigService.Load();
            if (!string.IsNullOrWhiteSpace(config.GeminiApiKey))
            {
                string result = await geminiService.SuggestDoDAsync(title);
                if (!string.IsNullOrWhiteSpace(result))
                {
                    return result;
                }
            }
        }
        catch { }

        return GenerateHeuristicDod(title);
    }

    // =========================================================================
    // ================= 规则 B~D: 一键客观重排 (AUTO-SCHEDULE) =================
    // =========================================================================

    public static SchedulingPlanResult GenerateAutoSchedulePlan(
        DateTime targetDate,
        List<ScheduleItem> backlogTasks,
        List<ScheduleItem> existingGridTasks,
        double alphaMultiplier = 1.35)
    {
        var plan = new SchedulingPlanResult
        {
            TargetDate = targetDate,
            AppliedAlpha = Math.Clamp(alphaMultiplier, 1.2, 1.5)
        };

        if (backlogTasks.Count == 0)
        {
            plan.DecisionLogs.Add("【待办池扫描】待办敏捷池当前为空，无需排期。");
            return plan;
        }

        // 1. 任务量化质检 (Rule A)
        var validTasks = new List<ScheduleItem>();
        foreach (var task in backlogTasks)
        {
            var (passed, reason, suggestedDod) = InspectDoD(task);
            if (!passed)
            {
                plan.RejectedTasks.Add(new RejectedTaskInfo
                {
                    Task = task,
                    Reason = reason,
                    SuggestedDod = suggestedDod
                });
                plan.DecisionLogs.Add($"【DoD质检拦截】待办「{task.Title}」因未明确量化验收标准被驳回，建议设定: {suggestedDod}");
            }
            else
            {
                validTasks.Add(task);
            }
        }

        // 2. 按优先级与认知负荷降序排序 (URGENT > HIGH > MEDIUM > LOW; DEEP_WORK 优先)
        validTasks.Sort((a, b) =>
        {
            int pA = GetPriorityWeight(a.Priority);
            int pB = GetPriorityWeight(b.Priority);
            if (pA != pB) return pB.CompareTo(pA); // 高优先级在前
            bool aIsDeep = a.WorkType == "DEEP_WORK";
            bool bIsDeep = b.WorkType == "DEEP_WORK";
            if (aIsDeep && !bIsDeep) return -1;
            if (!aIsDeep && bIsDeep) return 1;
            return 0;
        });

        // 3. 计算今日时间占用与可用时段槽
        // 精力曲线映射 (Chronotype Windows):
        // Morning Peak: 09:00 - 12:00 (深度工作峰值)
        // Lunch Break:  12:00 - 14:00 (强制午休与就餐)
        // Afternoon:    14:00 - 16:30 (次要专注时段)
        // Low Valley:   16:30 - 18:30 (浅层事务、沟通与整理)
        // Evening:      19:30 - 21:00 (复盘与自选轻负荷)

        DateTime dayStart = targetDate.Date;
        var occupiedRanges = existingGridTasks
            .Where(e => !e.IsDeleted && !e.IsBacklog && !e.IsDeferred && e.StartTime.Date == dayStart)
            .OrderBy(e => e.StartTime)
            .Select(e => (start: e.StartTime, end: e.EndTime, workType: e.WorkType))
            .ToList();

        // 统计已有深度工作耗时
        int currentDeepMins = occupiedRanges
            .Where(r => r.workType == "DEEP_WORK")
            .Sum(r => (int)Math.Max(0, (r.end - r.start).TotalMinutes));

        // 定义每日主要排期窗口
        var candidateWindows = new List<(DateTime winStart, DateTime winEnd, string allowedType, string name)>
        {
            (dayStart.AddHours(9), dayStart.AddHours(12), "DEEP_WORK", "早晨认知精力峰值区 (09:00-12:00)"),
            (dayStart.AddHours(14), dayStart.AddHours(16).AddMinutes(30), "DEEP_WORK", "下午主线专注区 (14:00-16:30)"),
            (dayStart.AddHours(16).AddMinutes(30), dayStart.AddHours(18).AddMinutes(30), "SHALLOW_WORK", "傍晚浅层事务与沟通区 (16:30-18:30)"),
            (dayStart.AddHours(19).AddMinutes(30), dayStart.AddHours(21).AddMinutes(30), "SHALLOW_WORK", "晚间收尾与轻量复盘区 (19:30-21:30)")
        };

        foreach (var task in validTasks)
        {
            // 悲观时间修正 (Rule C: 预估耗时 * alpha 乘数)
            int rawEst = task.EstimatedMinutes > 0 ? task.EstimatedMinutes : 45;
            int dilatedMinutes = (int)Math.Ceiling(rawEst * plan.AppliedAlpha);
            dilatedMinutes = ((dilatedMinutes + 14) / 15) * 15; // 15分钟对齐

            // 认知负荷配额硬顶熔断检查 (Rule B)
            if (task.WorkType == "DEEP_WORK")
            {
                if (currentDeepMins + dilatedMinutes > MaxDailyDeepWorkMinutes)
                {
                    plan.IsCircuitBroken = true;
                    plan.DeferredTasks.Add(task);
                    plan.DecisionLogs.Add($"【负荷熔断】今日深度工作已达 {currentDeepMins / 60.0:F1}h，任务「{task.Title}」(需{dilatedMinutes}m) 超过 4.5h 硬顶。已强行剥离至延期冻结池。");
                    continue;
                }
            }

            // 寻找空闲时间槽 (Rule D: 精力曲线拟合)
            bool scheduled = false;
            foreach (var win in candidateWindows)
            {
                // 如果是浅层事务，不强行占用早晨黄金专注时段
                if (task.WorkType == "SHALLOW_WORK" && win.allowedType == "DEEP_WORK" && candidateWindows.Any(w => w.allowedType == "SHALLOW_WORK"))
                {
                    continue;
                }

                DateTime cursor = win.winStart;
                while (cursor.AddMinutes(dilatedMinutes) <= win.winEnd)
                {
                    DateTime proposedEnd = cursor.AddMinutes(dilatedMinutes);

                    // 检查是否与已有时间块冲突
                    bool hasConflict = occupiedRanges.Any(r => cursor < r.end && proposedEnd > r.start);
                    if (!hasConflict)
                    {
                        // 安排此任务
                        var scheduledTask = new ScheduleItem
                        {
                            Id = task.Id,
                            Title = task.Title,
                            Description = task.Description,
                            Category = task.Category,
                            Priority = task.Priority,
                            Status = "PENDING",
                            StartTime = cursor,
                            EndTime = proposedEnd,
                            EstimatedMinutes = dilatedMinutes,
                            GoalId = task.GoalId,
                            WorkType = task.WorkType,
                            Dod = task.Dod,
                            IsBacklog = false,
                            IsDeferred = false,
                            IsDirty = true
                        };

                        plan.ScheduledBlocks.Add(scheduledTask);
                        occupiedRanges.Add((cursor, proposedEnd, task.WorkType));

                        if (task.WorkType == "DEEP_WORK")
                        {
                            currentDeepMins += dilatedMinutes;
                            plan.ScheduledDeepWorkHours += (dilatedMinutes / 60.0);
                            plan.DecisionLogs.Add($"【精力拟合】高优任务「{task.Title}」排入 {win.name} [{cursor:HH:mm} - {proposedEnd:HH:mm}] (按α={plan.AppliedAlpha:F2}x 膨胀至{dilatedMinutes}m)");

                            // 强制插入 15 分钟认知冷却缓冲 (Rule C)
                            DateTime bufferStart = proposedEnd;
                            DateTime bufferEnd = bufferStart.AddMinutes(TransitionBufferMinutes);
                            if (bufferEnd <= win.winEnd)
                            {
                                var bufferBlock = new ScheduleItem
                                {
                                    Id = Guid.NewGuid().ToString(),
                                    Title = "☕ 强制脑力恢复缓冲",
                                    Description = $"紧随高强度任务「{task.Title}」的法定认知冷却期，禁止排期侵占",
                                    Category = "健康",
                                    Priority = "MEDIUM",
                                    Status = "PENDING",
                                    StartTime = bufferStart,
                                    EndTime = bufferEnd,
                                    EstimatedMinutes = TransitionBufferMinutes,
                                    WorkType = "REST_BUFFER",
                                    Dod = "离开屏幕、活动颈椎或补充水分",
                                    IsBacklog = false,
                                    IsDeferred = false,
                                    IsDirty = true
                                };

                                plan.ScheduledBlocks.Add(bufferBlock);
                                occupiedRanges.Add((bufferStart, bufferEnd, "REST_BUFFER"));
                                plan.DecisionLogs.Add($"【转场缓冲】在「{task.Title}」后强制插入 15 分钟 Rest/Buffer 脑力冷却槽 [{bufferStart:HH:mm} - {bufferEnd:HH:mm}]");
                            }
                        }
                        else
                        {
                            plan.DecisionLogs.Add($"【低谷压入】浅层事务「{task.Title}」排入 {win.name} [{cursor:HH:mm} - {proposedEnd:HH:mm}]");
                        }

                        scheduled = true;
                        break;
                    }

                    // 步进 15 分钟重试
                    cursor = cursor.AddMinutes(15);
                }

                if (scheduled) break;
            }

            if (!scheduled)
            {
                // 无合适时间槽，自动延期
                plan.DeferredTasks.Add(task);
                plan.DecisionLogs.Add($"【时段饱和】今日各精力窗口已满，无法容纳「{task.Title}」，已转入次要延期池。");
            }
        }

        plan.ScheduledDeepWorkHours = Math.Round(plan.ScheduledDeepWorkHours, 1);
        return plan;
    }

    private static int GetPriorityWeight(string? priority) => priority switch
    {
        "URGENT" => 4,
        "HIGH" => 3,
        "MEDIUM" => 2,
        _ => 1
    };

    // =========================================================================
    // ================= 规则 A~D: 排期审计体检 (AUDIT SCAN) =====================
    // =========================================================================

    public static AuditScanReport RunAuditScan(DateTime date, List<ScheduleItem> tasks)
    {
        var report = new AuditScanReport { Date = date };
        var activeTasks = tasks.Where(t => !t.IsDeleted && !t.IsBacklog && !t.IsDeferred && t.StartTime.Date == date.Date).OrderBy(t => t.StartTime).ToList();

        if (activeTasks.Count == 0)
        {
            report.OverallHealthScore = 100;
            report.Recommendations.Add("今日暂无排期。可使用「🤖 一键客观重排」自动拟合待办任务。");
            return report;
        }

        double totalHours = 0;
        double deepHours = 0;
        int dodPassedCount = 0;
        int eligibleDodTaskCount = 0;

        for (int i = 0; i < activeTasks.Count; i++)
        {
            var task = activeTasks[i];
            double durHours = Math.Max(0.25, (task.EndTime - task.StartTime).TotalHours);
            totalHours += durHours;

            // 1. 认知负荷统计
            if (task.WorkType == "DEEP_WORK")
            {
                deepHours += durHours;
            }

            // 2. DoD 质检合规性
            if (task.WorkType != "REST_BUFFER")
            {
                eligibleDodTaskCount++;
                var (passed, reason, suggestedDod) = InspectDoD(task);
                if (passed)
                {
                    dodPassedCount++;
                }
                else
                {
                    report.Issues.Add(new AuditIssue
                    {
                        IssueType = "DOD_MISSING",
                        Severity = "WARNING",
                        Title = $"任务缺少量化 DoD: {task.Title}",
                        Description = $"未定义验收标准易导致拖延与范围蔓延。建议补充: {suggestedDod}",
                        RelatedScheduleId = task.Id
                    });
                }
            }

            // 3. 转场缓冲违规检查 (无缝背靠背排期)
            if (i < activeTasks.Count - 1)
            {
                var nextTask = activeTasks[i + 1];
                double gapMinutes = (nextTask.StartTime - task.EndTime).TotalMinutes;
                if (gapMinutes < 10 && task.WorkType == "DEEP_WORK")
                {
                    report.MissingBufferCount++;
                    report.Issues.Add(new AuditIssue
                    {
                        IssueType = "MISSING_BUFFER",
                        Severity = "WARNING",
                        Title = $"违规无缝连续排期: 「{task.Title}」➔「{nextTask.Title}」",
                        Description = $"两项深度工作之间仅间隔 {gapMinutes:F0} 分钟，违反 15 分钟大脑前额叶皮层认知冷却规则。",
                        RelatedScheduleId = task.Id
                    });
                }
            }

            // 4. 精力曲线错位检查 (Chronotype Mismatch)
            if (task.WorkType == "SHALLOW_WORK" && task.StartTime.Hour >= 9 && task.StartTime.Hour < 12)
            {
                report.ChronotypeMismatchCount++;
                report.Issues.Add(new AuditIssue
                {
                    IssueType = "CHRONOTYPE_MISMATCH",
                    Severity = "INFO",
                    Title = $"精力曲线错位: 上午黄金时段排入琐碎事务「{task.Title}」",
                    Description = "09:00 - 12:00 为生理精力巅峰，应严格用于高价值深度工作，建议将琐碎事务后移至傍晚低谷时段。",
                    RelatedScheduleId = task.Id
                });
            }
            else if (task.WorkType == "DEEP_WORK" && task.StartTime.Hour >= 21)
            {
                report.ChronotypeMismatchCount++;
                report.Issues.Add(new AuditIssue
                {
                    IssueType = "CHRONOTYPE_MISMATCH",
                    Severity = "WARNING",
                    Title = $"深宵过度专注风险: 「{task.Title}」",
                    Description = "夜间 21:00 后安排高强度深度工作易导致皮质醇升高引发失眠，建议前移至下午或明日上午。",
                    RelatedScheduleId = task.Id
                });
            }
        }

        report.TotalScheduledHours = Math.Round(totalHours, 1);
        report.DeepWorkHours = Math.Round(deepHours, 1);
        report.DoDComplianceRatio = eligibleDodTaskCount > 0 ? Math.Round((double)dodPassedCount / eligibleDodTaskCount * 100.0, 0) : 100.0;

        // 5. 认知负荷硬顶超限判定
        if (report.IsCognitiveOverloaded)
        {
            report.Issues.Insert(0, new AuditIssue
            {
                IssueType = "OVERLOAD_CIRCUIT_BREAK",
                Severity = "CRITICAL",
                Title = $"🚨 认知负荷严重超载 ({report.DeepWorkHours:F1}h / 4.5h)",
                Description = $"今日深度工作已超出 4.5 小时生理硬性极限，必然引发决策疲劳与质量崩塌！已强行触发排期熔断。"
            });
        }

        // 6. 计算综合健康评分 (0 ~ 100)
        int score = 100;
        if (report.IsCognitiveOverloaded) score -= 30;
        score -= Math.Min(30, report.MissingBufferCount * 10);
        score -= (int)((100.0 - report.DoDComplianceRatio) * 0.25);
        score -= Math.Min(15, report.ChronotypeMismatchCount * 5);
        report.OverallHealthScore = Math.Clamp(score, 25, 100);

        // 7. 综合建议
        if (report.IsCognitiveOverloaded)
        {
            report.Recommendations.Add($"🚨 立即剥离至少 {report.DeepWorkHours - report.DeepWorkCapHours:F1} 小时次要深度工作至延期冻结池。");
        }
        if (report.MissingBufferCount > 0)
        {
            report.Recommendations.Add($"🪄 点击「一键按 α 追加转场缓冲」，自动在深度任务之间补齐 15 分钟脑力冷却槽。");
        }
        if (report.DoDComplianceRatio < 80)
        {
            report.Recommendations.Add($"✍️ 补全模糊任务的 Definition of Done，明确交付物验收标准以防范围蔓延。");
        }
        if (report.Recommendations.Count == 0)
        {
            report.Recommendations.Add("✅ 今日排期节奏严谨，负荷在安全阈值内，转场缓冲充足，保持专注执行！");
        }

        return report;
    }
}
