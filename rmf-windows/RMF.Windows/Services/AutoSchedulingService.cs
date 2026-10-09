using System;
using System.Collections.Generic;
using System.Linq;
using RMF.Windows.Models;

namespace RMF.Windows.Services;

public class AutoSchedulingService
{
    public class TimeSlot
    {
        public DateTime StartTime { get; set; }
        public DateTime EndTime { get; set; }
        public double DurationMinutes => (EndTime - StartTime).TotalMinutes;
    }

    /// <summary>
    /// 智能自动排期: 在指定目标日期的偏好窗口内查找可用的空白时间段并见缝插针排期
    /// </summary>
    /// <param name="task">需要排期的任务</param>
    /// <param name="targetDate">目标日期</param>
    /// <param name="preferenceWindow">ALL_DAY (08:30~22:00), MORNING (09:00~12:00), AFTERNOON (14:00~18:00), EVENING (19:00~22:00)</param>
    /// <param name="appendBufferMinutes">任务后自动追加的工时缓冲垫 (默认 10 分钟)</param>
    /// <returns>(是否成功, 排期开始时间, 排期结束时间, 提示消息)</returns>
    public static (bool Success, DateTime StartTime, DateTime EndTime, string Message) AutoSchedule(
        ScheduleItem task,
        DateTime targetDate,
        string preferenceWindow = "ALL_DAY",
        int appendBufferMinutes = 10)
    {
        int requiredMins = task.EstimatedMinutes > 0 ? task.EstimatedMinutes : 45;
        DateTime windowStart;
        DateTime windowEnd;

        switch (preferenceWindow.ToUpperInvariant())
        {
            case "MORNING":
                windowStart = targetDate.Date.AddHours(9);
                windowEnd = targetDate.Date.AddHours(12);
                break;
            case "AFTERNOON":
                windowStart = targetDate.Date.AddHours(14);
                windowEnd = targetDate.Date.AddHours(18);
                break;
            case "EVENING":
                windowStart = targetDate.Date.AddHours(19);
                windowEnd = targetDate.Date.AddHours(22);
                break;
            default: // ALL_DAY
                windowStart = targetDate.Date.AddHours(8).AddMinutes(30);
                windowEnd = targetDate.Date.AddHours(22);
                break;
        }

        // 如果是今天且窗口起点已过，从当前时间起顺延 5 分钟规整为 5 分钟的倍数
        if (targetDate.Date == DateTime.Today && DateTime.Now > windowStart)
        {
            var now = DateTime.Now;
            int rem = now.Minute % 5;
            windowStart = now.AddMinutes(5 - rem).Date.AddHours(now.Hour).AddMinutes(now.Minute + (5 - rem));
        }

        if (windowStart >= windowEnd)
        {
            return (false, DateTime.MinValue, DateTime.MinValue, "偏好窗口今日可用时间已耗尽");
        }

        // 1. 获取当天的所有占用区间 (现有日程 + 课表投影)
        var busyIntervals = new List<(DateTime Start, DateTime End)>();

        var existingSchedules = DatabaseService.GetSchedulesForDate(targetDate)
            .Where(s => !s.IsAllDay && s.Id != task.Id)
            .ToList();

        foreach (var s in existingSchedules)
        {
            busyIntervals.Add((s.StartTime, s.EndTime));
        }

        var courses = TimetableProjectionService.GetEffectiveCoursesForDate(targetDate);
        foreach (var c in courses)
        {
            busyIntervals.Add((c.StartTime, c.EndTime));
        }

        // 合并与排序忙碌区间
        var sortedBusy = busyIntervals
            .Where(b => b.End > windowStart && b.Start < windowEnd)
            .OrderBy(b => b.Start)
            .ToList();

        // 2. 贪心搜索可用空闲时隙
        DateTime currentCursor = windowStart;
        DateTime? foundStart = null;
        DateTime? foundEnd = null;

        foreach (var busy in sortedBusy)
        {
            if (busy.Start > currentCursor)
            {
                double freeMins = (busy.Start - currentCursor).TotalMinutes;
                if (freeMins >= requiredMins)
                {
                    foundStart = currentCursor;
                    foundEnd = currentCursor.AddMinutes(requiredMins);
                    break;
                }
            }

            if (busy.End > currentCursor)
            {
                currentCursor = busy.End;
            }
        }

        // 检查最后一个占用区间与窗口终点之间的空档
        if (foundStart == null && (windowEnd - currentCursor).TotalMinutes >= requiredMins)
        {
            foundStart = currentCursor;
            foundEnd = currentCursor.AddMinutes(requiredMins);
        }

        if (foundStart == null || foundEnd == null)
        {
            return (false, DateTime.MinValue, DateTime.MinValue, $"未在 {targetDate:MM-dd} 的偏好窗口内找到连续 {requiredMins} 分钟的空白时段");
        }

        // 3. 执行排期
        task.StartTime = foundStart.Value;
        task.EndTime = foundEnd.Value;
        task.EstimatedMinutes = requiredMins;
        task.IsBacklog = false;
        task.IsDeferred = false;
        task.Status = "PENDING";
        DatabaseService.UpsertSchedule(task);

        // 4. 工时缓冲垫 (Buffer Time): 自动在紧随其后追加 10 分钟休息过渡块
        if (appendBufferMinutes > 0 && foundEnd.Value.AddMinutes(appendBufferMinutes) <= windowEnd)
        {
            var bufferItem = new ScheduleItem
            {
                Id = Guid.NewGuid().ToString(),
                Title = "☕ 过渡缓冲垫 (工时保护)",
                WorkType = "REST_BUFFER",
                Category = "REST",
                StartTime = foundEnd.Value,
                EndTime = foundEnd.Value.AddMinutes(appendBufferMinutes),
                EstimatedMinutes = appendBufferMinutes,
                Status = "PENDING",
                Source = "⚡ 智能排期缓冲",
                Dod = "身心切换、补水、站立活动，防止认知枯竭"
            };
            DatabaseService.UpsertSchedule(bufferItem);
        }

        return (true, foundStart.Value, foundEnd.Value, $"已智能安排在 {foundStart.Value:HH:mm} - {foundEnd.Value:HH:mm}" + (appendBufferMinutes > 0 ? $" (已追加 {appendBufferMinutes}m 缓冲垫)" : ""));
    }
}
