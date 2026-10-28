using System;
using System.Collections.Generic;
using System.Linq;
using RMF.Windows.Models;

namespace RMF.Windows.Services;

public class TimeGapSlot
{
    public DateTime StartTime { get; set; }
    public DateTime EndTime { get; set; }
    public int DurationMinutes => Math.Max(0, (int)(EndTime - StartTime).TotalMinutes);
    public ScheduleItem? SuggestedBacklog { get; set; }

    public DateTime SuggestedTimeBoxStart => StartTime;

    public DateTime SuggestedTimeBoxEnd
    {
        get
        {
            if (SuggestedBacklog != null && SuggestedBacklog.EstimatedMinutes > 0)
            {
                int fitMinutes = Math.Min(DurationMinutes, SuggestedBacklog.EstimatedMinutes);
                return StartTime.AddMinutes(fitMinutes);
            }
            return EndTime;
        }
    }

    public string SummaryText
    {
        get
        {
            if (SuggestedBacklog != null)
            {
                int taskMins = SuggestedBacklog.EstimatedMinutes > 0 ? SuggestedBacklog.EstimatedMinutes : DurationMinutes;
                return $"{StartTime:HH:mm}~{EndTime:HH:mm} ({DurationMinutes}m空闲) · 推荐填入待办「{SuggestedBacklog.Title}」(需{taskMins}m)";
            }
            return $"{StartTime:HH:mm}~{EndTime:HH:mm} ({DurationMinutes}m空闲碎片)";
        }
    }
}

/// <summary>
/// 碎片空隙时间盒智能填空服务 (Gap-Filling Time-Boxing Engine)
/// 自动扫描课表与日程间的 20~60+ 分钟空当，智能匹配敏捷待办池并一键卡槽装填
/// </summary>
public static class GapTimeBoxingService
{
    public static List<TimeGapSlot> FindDailyFreeGaps(
        DateTime date,
        List<ScheduleItem> daySchedules,
        List<EffectiveCourseSlot> dayCourses,
        List<ScheduleItem> backlogItems,
        int minGapMinutes = 20,
        int dayStartHour = 8,
        int dayEndHour = 22)
    {
        var targetDate = date.Date;
        var startOfDay = targetDate.AddHours(dayStartHour);
        var endOfDay = targetDate.AddHours(dayEndHour);

        // 若计算当天，且当前时间晚于起始时间，从当前时刻（向后5分钟规整）开始寻找空闲
        if (targetDate == DateTime.Today)
        {
            var now = DateTime.Now;
            int rem = now.Minute % 5;
            var roundedNow = now.AddMinutes(rem == 0 ? 0 : 5 - rem);
            if (roundedNow.Date == targetDate && roundedNow > startOfDay)
            {
                startOfDay = roundedNow;
            }
        }

        if (startOfDay >= endOfDay) return new List<TimeGapSlot>();

        // 1. 搜集当日所有占用时段 (日程 + 课表)
        var busyList = new List<(DateTime Start, DateTime End)>();

        foreach (var s in daySchedules)
        {
            if (s.IsAllDay || s.IsBacklog || s.Status == "CANCELLED" || s.IsDeleted) continue;
            var sStart = s.StartTime < startOfDay ? startOfDay : s.StartTime;
            var sEnd = s.EndTime > endOfDay ? endOfDay : s.EndTime;
            if (sEnd > sStart)
            {
                busyList.Add((sStart, sEnd));
            }
        }

        foreach (var c in dayCourses)
        {
            var cStart = c.StartTime < startOfDay ? startOfDay : c.StartTime;
            var cEnd = c.EndTime > endOfDay ? endOfDay : c.EndTime;
            if (cEnd > cStart)
            {
                busyList.Add((cStart, cEnd));
            }
        }

        // 2. 合并重叠或相邻的忙碌区间
        busyList = busyList.OrderBy(b => b.Start).ThenBy(b => b.End).ToList();
        var mergedBusy = new List<(DateTime Start, DateTime End)>();
        foreach (var b in busyList)
        {
            if (mergedBusy.Count == 0)
            {
                mergedBusy.Add(b);
            }
            else
            {
                var last = mergedBusy[^1];
                if (b.Start <= last.End)
                {
                    mergedBusy[^1] = (last.Start, b.End > last.End ? b.End : last.End);
                }
                else
                {
                    mergedBusy.Add(b);
                }
            }
        }

        // 3. 扫描空闲时隙
        var gaps = new List<TimeGapSlot>();
        var cur = startOfDay;

        foreach (var busy in mergedBusy)
        {
            if (busy.Start > cur)
            {
                int duration = (int)(busy.Start - cur).TotalMinutes;
                if (duration >= minGapMinutes)
                {
                    gaps.Add(new TimeGapSlot
                    {
                        StartTime = cur,
                        EndTime = busy.Start
                    });
                }
            }
            if (busy.End > cur)
            {
                cur = busy.End;
            }
        }

        if (endOfDay > cur)
        {
            int duration = (int)(endOfDay - cur).TotalMinutes;
            if (duration >= minGapMinutes)
            {
                gaps.Add(new TimeGapSlot
                {
                    StartTime = cur,
                    EndTime = endOfDay
                });
            }
        }

        // 4. 智能匹配待办池任务
        var activeBacklogs = backlogItems
            .Where(b => b.Status != "COMPLETED" && !b.IsDeleted)
            .OrderByDescending(b => GetPriorityWeight(b.Priority))
            .ToList();

        var allocatedIds = new HashSet<string>();

        foreach (var gap in gaps)
        {
            // 优先匹配预估耗时 <= 空闲时长的任务
            var match = activeBacklogs
                .Where(b => !allocatedIds.Contains(b.Id) &&
                            b.EstimatedMinutes > 0 &&
                            b.EstimatedMinutes <= gap.DurationMinutes)
                .OrderByDescending(b => GetPriorityWeight(b.Priority))
                .ThenBy(b => gap.DurationMinutes - b.EstimatedMinutes)
                .FirstOrDefault();

            if (match == null)
            {
                // 兜底匹配任意未指定预估耗时的待办
                match = activeBacklogs
                    .FirstOrDefault(b => !allocatedIds.Contains(b.Id) && b.EstimatedMinutes <= 0);
            }

            if (match != null)
            {
                gap.SuggestedBacklog = match;
                allocatedIds.Add(match.Id);
            }
        }

        return gaps;
    }

    private static int GetPriorityWeight(string priority) => priority switch
    {
        "URGENT" => 4,
        "HIGH" => 3,
        "MEDIUM" => 2,
        _ => 1
    };

    /// <summary>
    /// 一键将待办任务卡槽装填进空闲时隙
    /// </summary>
    public static bool FillGapWithBacklog(TimeGapSlot gapSlot)
    {
        if (gapSlot?.SuggestedBacklog == null) return false;

        var item = gapSlot.SuggestedBacklog;
        item.StartTime = gapSlot.SuggestedTimeBoxStart;
        item.EndTime = gapSlot.SuggestedTimeBoxEnd;
        item.IsBacklog = false;
        item.Status = "PENDING";
        item.IsDirty = true;
        item.SyncVersion++;
        item.UpdatedAt = DateTime.UtcNow;

        DatabaseService.UpsertSchedule(item);
        return true;
    }
}
