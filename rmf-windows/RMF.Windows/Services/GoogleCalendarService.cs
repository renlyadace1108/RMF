using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Net.Http;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using RMF.Windows.Models;

namespace RMF.Windows.Services;

public class GoogleCalendarService
{
    private static readonly HttpClient HttpClient = new()
    {
        Timeout = TimeSpan.FromSeconds(15)
    };

    /// <summary>
    /// 从 Google Calendar 的 iCal/ICS 私密或公开订阅地址同步日程数据入本地 SQLite
    /// </summary>
    public async Task<(int addedCount, int updatedCount)> SyncFromIcsAsync(string icsUrl)
    {
        if (string.IsNullOrWhiteSpace(icsUrl))
        {
            throw new ArgumentException("Google Calendar iCal 订阅地址不能为空。");
        }

        string rawIcs = await HttpClient.GetStringAsync(icsUrl.Trim());
        var events = ParseIcsEvents(rawIcs);

        int added = 0;
        int updated = 0;

        foreach (var item in events)
        {
            // 存入或更新本地 SQLite
            DatabaseService.UpsertSchedule(item);
            added++;
        }

        // 记录同步时间与状态
        var config = ConfigService.Load();
        config.GoogleCalendarIcsUrl = icsUrl.Trim();
        config.GoogleCalendarLastSyncTime = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
        config.IsGoogleCalendarLinked = true;
        ConfigService.Save(config);

        return (added, updated);
    }

    /// <summary>
    /// 解析标准 RFC 5545 iCalendar (ICS) 文本
    /// </summary>
    public static List<ScheduleItem> ParseIcsEvents(string icsContent)
    {
        var items = new List<ScheduleItem>();
        if (string.IsNullOrWhiteSpace(icsContent)) return items;

        // 1. 展开折叠行 (Unfolding: 依据 RFC 5545，行首为空格或Tab表示上一行的延续)
        string unfolded = Regex.Replace(icsContent, @"\r?\n[ \t]", "");

        // 2. 逐行读取
        using var reader = new StringReader(unfolded);
        string? line;
        bool inEvent = false;

        string uid = string.Empty;
        string summary = string.Empty;
        string description = string.Empty;
        string category = "WORK";
        DateTime startTime = DateTime.MinValue;
        DateTime endTime = DateTime.MinValue;
        string status = "PENDING";

        while ((line = reader.ReadLine()) != null)
        {
            line = line.Trim();
            if (line.Equals("BEGIN:VEVENT", StringComparison.OrdinalIgnoreCase))
            {
                inEvent = true;
                uid = Guid.NewGuid().ToString();
                summary = "未命名日程";
                description = string.Empty;
                category = "工作";
                startTime = DateTime.MinValue;
                endTime = DateTime.MinValue;
                status = "PENDING";
                continue;
            }

            if (line.Equals("END:VEVENT", StringComparison.OrdinalIgnoreCase))
            {
                if (inEvent && startTime != DateTime.MinValue)
                {
                    if (endTime <= startTime)
                    {
                        endTime = startTime.AddHours(1);
                    }

                    items.Add(new ScheduleItem
                    {
                        Id = string.IsNullOrWhiteSpace(uid) ? Guid.NewGuid().ToString() : uid,
                        Title = summary,
                        Description = description,
                        Category = category,
                        Priority = "HIGH",
                        Status = status,
                        StartTime = startTime,
                        EndTime = endTime,
                        EstimatedMinutes = (int)(endTime - startTime).TotalMinutes,
                        IsDeleted = false,
                        Source = "☁️ Google日历"
                    });
                }
                inEvent = false;
                continue;
            }

            if (!inEvent) continue;

            // 属性键值分离
            int colonIdx = line.IndexOf(':');
            if (colonIdx <= 0) continue;

            string keyPart = line[..colonIdx];
            string valuePart = line[(colonIdx + 1)..];

            // 提取主属性名 (忽略参数如 DTSTART;VALUE=DATE)
            int semiIdx = keyPart.IndexOf(';');
            string propName = (semiIdx > 0 ? keyPart[..semiIdx] : keyPart).Trim().ToUpperInvariant();

            switch (propName)
            {
                case "UID":
                    uid = valuePart.Trim();
                    break;
                case "SUMMARY":
                    summary = CleanIcsText(valuePart);
                    break;
                case "DESCRIPTION":
                    description = CleanIcsText(valuePart);
                    break;
                case "CATEGORIES":
                    category = CleanIcsText(valuePart);
                    break;
                case "STATUS":
                    string st = valuePart.Trim().ToUpperInvariant();
                    status = st == "CONFIRMED" ? "PENDING" : (st == "COMPLETED" ? "COMPLETED" : "PENDING");
                    break;
                case "DTSTART":
                    startTime = ParseIcsDateTime(valuePart, keyPart);
                    break;
                case "DTEND":
                    endTime = ParseIcsDateTime(valuePart, keyPart);
                    break;
            }
        }

        return items;
    }

    private static string CleanIcsText(string text)
    {
        if (string.IsNullOrEmpty(text)) return string.Empty;
        return text
            .Replace(@"\,", ",")
            .Replace(@"\;", ";")
            .Replace(@"\n", "\n")
            .Replace(@"\N", "\n")
            .Replace(@"\\", @"\")
            .Trim();
    }

    private static DateTime ParseIcsDateTime(string value, string keyPart)
    {
        value = value.Trim();
        try
        {
            // 格式 1: 20261002T093000Z (UTC)
            if (value.EndsWith('Z') && DateTime.TryParseExact(value, "yyyyMMdd'T'HHmmss'Z'", CultureInfo.InvariantCulture, DateTimeStyles.AdjustToUniversal, out var dtUtc))
            {
                return dtUtc.ToLocalTime();
            }

            // 格式 2: 20261002T093000 (Local floating)
            if (DateTime.TryParseExact(value, "yyyyMMdd'T'HHmmss", CultureInfo.InvariantCulture, DateTimeStyles.None, out var dtLocal))
            {
                return dtLocal;
            }

            // 格式 3: 20261002 (All-day date)
            if (DateTime.TryParseExact(value, "yyyyMMdd", CultureInfo.InvariantCulture, DateTimeStyles.None, out var dtDate))
            {
                return dtDate;
            }

            // 容错解析通用日期
            if (DateTime.TryParse(value, out var fallback))
            {
                return fallback;
            }
        }
        catch
        {
        }

        return DateTime.Today.AddHours(9);
    }
}
