using System;
using System.Collections.Generic;
using System.Linq;
using RMF.Windows.Models;

namespace RMF.Windows.Services;

public static class TimeMarkerVarianceAnalyzer
{
    public static List<VarianceInsight> AnalyzeDay(
        DateTime targetDate,
        List<ScheduleItem> schedules,
        List<TimeMarkerItem> markers)
    {
        var insights = new List<VarianceInsight>();

        var dateStr = targetDate.ToString("yyyy-MM-dd");
        var daySchedules = schedules
            .Where(s => !s.IsDeleted && s.Status != "BACKLOG" && !string.IsNullOrWhiteSpace(s.StartTime))
            .Where(s =>
            {
                if (DateTime.TryParse(s.StartTime, out var dt))
                {
                    return dt.Date == targetDate.Date;
                }
                return false;
            })
            .OrderBy(s => s.StartTime)
            .ToList();

        var dayMarkers = markers
            .Where(m => !m.IsDeleted && (m.Date == dateStr || (DateTime.TryParse(m.StartTime, out var dt) && dt.Date == targetDate.Date)))
            .OrderBy(m => m.StartTime)
            .ToList();

        if (dayMarkers.Count == 0)
        {
            insights.Add(new VarianceInsight
            {
                Icon = "💡",
                Title = "暂未记录时间标记",
                Detail = "点击下方预设或快速打卡记录起床、就寝或午休，开启实际执行与预计日程的精准偏差分析。",
                Level = "INFO"
            });
            return insights;
        }

        // 1. 起床时间分析 (Wake up vs First Schedule)
        var wakeMarker = dayMarkers.FirstOrDefault(m =>
            m.Name.Contains("起床") || (m.Category == "SLEEP" && m.Icon == "🌅"));
        if (wakeMarker != null && DateTime.TryParse(wakeMarker.StartTime, out var wakeTime))
        {
            if (daySchedules.Count > 0)
            {
                var firstSchedule = daySchedules.First();
                if (DateTime.TryParse(firstSchedule.StartTime, out var firstSchedTime))
                {
                    int diffMinutes = (int)(wakeTime - firstSchedTime).TotalMinutes;
                    if (diffMinutes > 10)
                    {
                        insights.Add(new VarianceInsight
                        {
                            Icon = "⚠️",
                            Title = "晨间起床晚于首个计划日程",
                            Detail = $"首个日程「{firstSchedule.Title}」定于 {firstSchedTime:HH:mm}，实际于 {wakeTime:HH:mm} 起床（推迟 {diffMinutes} 分钟），计划开局产生延迟，建议适当调整晨起闹钟或合理延后首个任务。",
                            Level = "WARNING"
                        });
                    }
                    else if (diffMinutes < -15)
                    {
                        int advance = Math.Abs(diffMinutes);
                        insights.Add(new VarianceInsight
                        {
                            Icon = "🌅",
                            Title = "从容早起，预留充沛缓冲",
                            Detail = $"实际于 {wakeTime:HH:mm} 起床，比首个日程「{firstSchedule.Title}」提前 {advance} 分钟，晨间准备时间充裕，执行心态平稳！",
                            Level = "SUCCESS"
                        });
                    }
                    else
                    {
                        insights.Add(new VarianceInsight
                        {
                            Icon = "🎯",
                            Title = "晨起与首个日程准点对齐",
                            Detail = $"实际于 {wakeTime:HH:mm} 起床，与首个计划日程无缝顺承，节奏紧凑精准。",
                            Level = "SUCCESS"
                        });
                    }
                }
            }
            else
            {
                insights.Add(new VarianceInsight
                {
                    Icon = "🌅",
                    Title = $"记录起床时间: {wakeTime:HH:mm}",
                    Detail = "今日暂未安排具体开始时间的定时日程，处于自适应自由作息流。",
                    Level = "INFO"
                });
            }
        }

        // 2. 午休 / 休息时间段分析 (Rest/Nap Range vs Schedule Overlaps)
        var restMarkers = dayMarkers.Where(m =>
            (m.Name.Contains("午休") || m.Name.Contains("午睡") || m.Category == "REST") && m.MarkerType == "RANGE").ToList();

        foreach (var rest in restMarkers)
        {
            if (DateTime.TryParse(rest.StartTime, out var rStart) && DateTime.TryParse(rest.EndTime, out var rEnd))
            {
                int napMinutes = (int)(rEnd - rStart).TotalMinutes;
                var overlappingSchedules = daySchedules.Where(sched =>
                {
                    if (DateTime.TryParse(sched.StartTime, out var sStart))
                    {
                        var sEnd = DateTime.TryParse(sched.EndTime, out var se) ? se : sStart.AddMinutes(sched.EstimatedMinutes > 0 ? sched.EstimatedMinutes : 30);
                        return sStart < rEnd && sEnd > rStart;
                    }
                    return false;
                }).ToList();

                if (overlappingSchedules.Count > 0)
                {
                    var titles = string.Join("、", overlappingSchedules.Select(s => $"「{s.Title}」"));
                    insights.Add(new VarianceInsight
                    {
                        Icon = "💤",
                        Title = $"实际{rest.Name}挤占预计日程",
                        Detail = $"实际{rest.Name}（{rStart:HH:mm} ~ {rEnd:HH:mm}，共 {napMinutes} 分钟）与计划日程 {titles} 发生时间重叠，原计划执行被作息挤占，需调整未完成任务至后段。",
                        Level = "WARNING"
                    });
                }
                else
                {
                    insights.Add(new VarianceInsight
                    {
                        Icon = "💤",
                        Title = $"规律{rest.Name}: {napMinutes} 分钟",
                        Detail = $"实际于 {rStart:HH:mm} ~ {rEnd:HH:mm} 深度充能，未与工作日程发生冲突，作息安排健康高效。",
                        Level = "SUCCESS"
                    });
                }
            }
        }

        // 3. 睡觉 / 收工时间分析 (Bedtime / Off-work vs Last Schedule)
        var sleepMarker = dayMarkers.FirstOrDefault(m =>
            m.Name.Contains("睡觉") || m.Name.Contains("就寝") || m.Name.Contains("收工"));
        if (sleepMarker != null && DateTime.TryParse(sleepMarker.StartTime, out var sleepTime))
        {
            if (daySchedules.Count > 0)
            {
                var lastSchedule = daySchedules.Last();
                var lastEndTime = DateTime.TryParse(lastSchedule.EndTime, out var le)
                    ? le
                    : (DateTime.TryParse(lastSchedule.StartTime, out var ls) ? ls.AddMinutes(lastSchedule.EstimatedMinutes > 0 ? lastSchedule.EstimatedMinutes : 30) : DateTime.MinValue);

                if (lastEndTime != DateTime.MinValue)
                {
                    int diff = (int)(sleepTime - lastEndTime).TotalMinutes;
                    if (diff > 60)
                    {
                        insights.Add(new VarianceInsight
                        {
                            Icon = "🌙",
                            Title = "日程结束至就寝有较长空白",
                            Detail = $"最后一个日程「{lastSchedule.Title}」于 {lastEndTime:HH:mm} 结束，实际于 {sleepTime:HH:mm} 就寝，存在约 {diff / 60} 小时余量时间，可用于复盘或个人娱乐。",
                            Level = "INFO"
                        });
                    }
                    else if (diff < 0)
                    {
                        insights.Add(new VarianceInsight
                        {
                            Icon = "⚠️",
                            Title = "就寝时间早于预计日程结束",
                            Detail = $"实际于 {sleepTime:HH:mm} 就寝，但日程「{lastSchedule.Title}」预计至 {lastEndTime:HH:mm}，可能存在未完结排期推迟到明日。",
                            Level = "WARNING"
                        });
                    }
                    else
                    {
                        insights.Add(new VarianceInsight
                        {
                            Icon = "🌙",
                            Title = "准时收工与健康就寝",
                            Detail = $"日程结束于 {lastEndTime:HH:mm}，随后于 {sleepTime:HH:mm} 顺利就寝，作息节奏张弛有度。",
                            Level = "SUCCESS"
                        });
                    }
                }
            }
        }

        // 4. 统计今日所有打卡锚点
        var markerNames = string.Join(" · ", dayMarkers.Select(m => $"{m.Icon} {m.Name} ({m.FormattedTimeSpan})"));
        insights.Add(new VarianceInsight
        {
            Icon = "📊",
            Title = $"今日共记录 {dayMarkers.Count} 个实际作息锚点",
            Detail = markerNames,
            Level = "INFO"
        });

        return insights;
    }
}
