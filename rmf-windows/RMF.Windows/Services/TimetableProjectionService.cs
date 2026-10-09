using System;
using System.Collections.Generic;
using System.Linq;
using RMF.Windows.Models;

namespace RMF.Windows.Services;

public class TimetableProjectionService
{
    public class SectionTimeSlot
    {
        public string Start { get; set; } = "08:00";
        public string End { get; set; } = "08:45";
    }

    /// <summary>
    /// 计算指定公历日期对应的教学周数 (1 ~ totalWeeks)，如未开学或已结束返回相应边界
    /// </summary>
    public static int GetSemesterWeekForDate(DateTime date)
    {
        string startDateStr = DatabaseService.GetTimetableSetting("semester_start_date", "2026-09-07");
        int totalWeeks = int.TryParse(DatabaseService.GetTimetableSetting("total_weeks", "20"), out int tw) ? tw : 20;

        if (DateTime.TryParse(startDateStr, out DateTime startDate))
        {
            int diffToMonday = ((int)startDate.DayOfWeek + 6) % 7;
            startDate = startDate.AddDays(-diffToMonday);

            double daysPassed = (date.Date - startDate.Date).TotalDays;
            int week = (int)Math.Floor(daysPassed / 7.0) + 1;
            return Math.Clamp(week, 1, totalWeeks);
        }

        return 1;
    }

    /// <summary>
    /// 获取根据作息设置生成的各节起止时间
    /// </summary>
    public static List<SectionTimeSlot> GetConfiguredSectionTimes(int totalSections)
    {
        var slots = new List<SectionTimeSlot>();
        int classDur = int.TryParse(DatabaseService.GetTimetableSetting("class_duration", "45"), out int cd) ? cd : 45;
        int breakDur = int.TryParse(DatabaseService.GetTimetableSetting("break_duration", "10"), out int bd) ? bd : 10;
        string morningStart = DatabaseService.GetTimetableSetting("morning_start", "08:00");
        string afternoonStart = DatabaseService.GetTimetableSetting("afternoon_start", "14:00");
        string eveningStart = DatabaseService.GetTimetableSetting("evening_start", "18:30");

        TimeSpan current = TimeSpan.TryParse(morningStart, out var ms) ? ms : new TimeSpan(8, 0, 0);

        for (int sec = 1; sec <= totalSections; sec++)
        {
            string customKey = $"section_{sec}_time";
            string savedTime = DatabaseService.GetTimetableSetting(customKey, string.Empty);
            if (!string.IsNullOrEmpty(savedTime) && savedTime.Contains('-'))
            {
                var parts = savedTime.Split('-');
                if (parts.Length == 2 && TimeSpan.TryParse(parts[0].Trim(), out var customS) && TimeSpan.TryParse(parts[1].Trim(), out var customE))
                {
                    slots.Add(new SectionTimeSlot
                    {
                        Start = customS.ToString(@"hh\:mm"),
                        End = customE.ToString(@"hh\:mm")
                    });
                    current = customE.Add(TimeSpan.FromMinutes(breakDur));
                    continue;
                }
            }

            if (sec == 5 && TimeSpan.TryParse(afternoonStart, out var afs)) current = afs;
            else if (sec == 9 && TimeSpan.TryParse(eveningStart, out var evs)) current = evs;

            TimeSpan end = current.Add(TimeSpan.FromMinutes(classDur));
            slots.Add(new SectionTimeSlot
            {
                Start = current.ToString(@"hh\:mm"),
                End = end.ToString(@"hh\:mm")
            });
            current = end.Add(TimeSpan.FromMinutes(breakDur));
        }

        return slots;
    }

    /// <summary>
    /// 获取指定日期生效的所有课程投影槽位（自适应单双周及调停课规则）
    /// </summary>
    public static List<EffectiveCourseSlot> GetEffectiveCoursesForDate(DateTime date)
    {
        var result = new List<EffectiveCourseSlot>();
        int week = GetSemesterWeekForDate(date);
        int dayOfWeek = ((int)date.DayOfWeek == 0) ? 7 : (int)date.DayOfWeek; // 1=Mon .. 7=Sun

        int totalSections = int.TryParse(DatabaseService.GetTimetableSetting("total_sections", "12"), out int ts) ? ts : 12;
        var sectionTimes = GetConfiguredSectionTimes(totalSections);

        var allAdjustments = DatabaseService.GetAllCourseAdjustments();
        var weekAdjustments = allAdjustments.Where(a => a.TargetWeek == week).ToList();
        var courses = DatabaseService.GetAllCourses();

        foreach (var course in courses)
        {
            // 检查停课规则
            var suspendAdj = weekAdjustments.FirstOrDefault(a => a.CourseId == course.Id && a.AdjustmentType == "SUSPEND");
            if (suspendAdj != null) continue;

            // 检查调课规则
            var rescheduleAdj = weekAdjustments.FirstOrDefault(a => a.CourseId == course.Id && a.AdjustmentType == "RESCHEDULE");

            int activeDay = course.DayOfWeek;
            int activeStartSec = course.StartSection;
            int activeSpan = course.SectionSpan;
            string activeLoc = course.Location;

            if (rescheduleAdj != null)
            {
                // 如果原本在今天但被调到其他时间，跳过
                if (course.DayOfWeek == dayOfWeek) continue;

                // 如果被调至今天
                if (rescheduleAdj.NewDayOfWeek == dayOfWeek)
                {
                    activeDay = rescheduleAdj.NewDayOfWeek;
                    activeStartSec = rescheduleAdj.NewStartSection;
                    activeSpan = rescheduleAdj.SectionSpan;
                    if (!string.IsNullOrEmpty(rescheduleAdj.NewLocation)) activeLoc = rescheduleAdj.NewLocation;
                }
                else
                {
                    continue;
                }
            }
            else
            {
                if (!course.IsActiveInWeek(week)) continue;
                if (course.DayOfWeek != dayOfWeek) continue;
            }

            // 计算具体起始与结束时间
            DateTime startTime;
            DateTime endTime;

            if (course.IsCustomTime && !string.IsNullOrEmpty(course.CustomStartTime) && !string.IsNullOrEmpty(course.CustomEndTime))
            {
                if (TimeSpan.TryParse(course.CustomStartTime, out var cs) && TimeSpan.TryParse(course.CustomEndTime, out var ce))
                {
                    startTime = date.Date.Add(cs);
                    endTime = date.Date.Add(ce);
                }
                else
                {
                    startTime = date.Date.AddHours(8);
                    endTime = date.Date.AddHours(9).AddMinutes(30);
                }
            }
            else
            {
                int sIdx = Math.Clamp(activeStartSec - 1, 0, sectionTimes.Count - 1);
                int eIdx = Math.Clamp(activeStartSec - 1 + activeSpan - 1, 0, sectionTimes.Count - 1);

                TimeSpan.TryParse(sectionTimes[sIdx].Start, out var st);
                TimeSpan.TryParse(sectionTimes[eIdx].End, out var et);

                startTime = date.Date.Add(st);
                endTime = date.Date.Add(et);
            }

            result.Add(new EffectiveCourseSlot
            {
                CourseId = course.Id,
                Name = course.Name,
                Location = activeLoc,
                Teacher = course.Teacher,
                DayOfWeek = dayOfWeek,
                StartTime = startTime,
                EndTime = endTime,
                ColorHex = course.ColorHex,
                StartSection = activeStartSec,
                SectionSpan = activeSpan,
                IsAdjustment = (rescheduleAdj != null),
                AdjustmentType = rescheduleAdj != null ? "RESCHEDULE" : null
            });
        }

        return result.OrderBy(c => c.StartTime).ToList();
    }

    /// <summary>
    /// 检测某日程时间范围是否与当天的课程投影存在重叠冲突
    /// </summary>
    public static (bool hasConflict, EffectiveCourseSlot? conflictingCourse) CheckScheduleCourseConflict(DateTime startTime, DateTime endTime)
    {
        var courses = GetEffectiveCoursesForDate(startTime.Date);
        foreach (var c in courses)
        {
            // 时间区间碰撞检测: StartA < EndB && EndA > StartB
            if (startTime < c.EndTime && endTime > c.StartTime)
            {
                return (true, c);
            }
        }
        return (false, null);
    }
}
