using System;
using System.Text.RegularExpressions;
using RMF.Windows.Models;

namespace RMF.Windows.Services;

public class NaturalLanguageScheduleParser
{
    public class ParseResult
    {
        public string Title { get; set; } = string.Empty;
        public DateTime StartTime { get; set; } = DateTime.Today.AddHours(9);
        public DateTime EndTime { get; set; } = DateTime.Today.AddHours(10);
        public int DurationMinutes { get; set; } = 60;
        public string Priority { get; set; } = "MEDIUM";
        public string Category { get; set; } = "WORK";
        public string? Tag { get; set; }
        public bool IsBacklog { get; set; } = false;
        public string PreviewSummary { get; set; } = string.Empty;
    }

    public static ParseResult Parse(string text)
    {
        var result = new ParseResult();
        if (string.IsNullOrWhiteSpace(text)) return result;

        string remaining = text.Trim();

        // 1. 优先级提取: p:高 / p:high / p:urgent / p:紧急 / p:中 / p:低
        var pMatch = Regex.Match(remaining, @"\bp:(高|high|urgent|紧急|中|medium|低|low)\b", RegexOptions.IgnoreCase);
        if (pMatch.Success)
        {
            string pVal = pMatch.Groups[1].Value.ToLower();
            if (pVal is "高" or "high" or "urgent" or "紧急") result.Priority = "HIGH";
            else if (pVal is "低" or "low") result.Priority = "LOW";
            else result.Priority = "MEDIUM";

            remaining = remaining.Replace(pMatch.Value, "").Trim();
        }

        // 2. 耗时提取: d:45m / d:1.5h / d:60 / 耗时45分钟
        var dMatch = Regex.Match(remaining, @"\bd:(\d+(?:\.\d+)?)(m|h)?\b", RegexOptions.IgnoreCase);
        if (dMatch.Success)
        {
            double val = double.Parse(dMatch.Groups[1].Value);
            string unit = dMatch.Groups[2].Value.ToLower();
            if (unit == "h") result.DurationMinutes = (int)(val * 60);
            else result.DurationMinutes = (int)val;

            remaining = remaining.Replace(dMatch.Value, "").Trim();
        }

        // 3. 标签/分类提取: #专业课 / #技术栈 / #学习 / #工作
        var tagMatch = Regex.Match(remaining, @"#([^\s#]+)");
        if (tagMatch.Success)
        {
            result.Tag = tagMatch.Groups[1].Value;
            if (result.Tag.Contains("学") || result.Tag.Contains("课") || result.Tag.Contains("考") || result.Tag.Contains("研"))
            {
                result.Category = "STUDY";
            }
            else if (result.Tag.Contains("生活") || result.Tag.Contains("买") || result.Tag.Contains("休"))
            {
                result.Category = "LIFE";
            }
            else
            {
                result.Category = "WORK";
            }
            remaining = remaining.Replace(tagMatch.Value, "").Trim();
        }

        // 4. 日期提取: 明天 / 明早 / 今天 / 今晚 / 后天 / 周一 ~ 周日
        DateTime targetDate = DateTime.Today;
        TimeSpan targetTime = new TimeSpan(9, 0, 0); // 默认早9点
        bool hasExplicitDate = false;
        bool hasExplicitTime = false;

        if (remaining.Contains("明早"))
        {
            targetDate = DateTime.Today.AddDays(1);
            targetTime = new TimeSpan(8, 30, 0);
            hasExplicitDate = true;
            hasExplicitTime = true;
            remaining = remaining.Replace("明早", "").Trim();
        }
        else if (remaining.Contains("明天"))
        {
            targetDate = DateTime.Today.AddDays(1);
            hasExplicitDate = true;
            remaining = remaining.Replace("明天", "").Trim();
        }
        else if (remaining.Contains("后天"))
        {
            targetDate = DateTime.Today.AddDays(2);
            hasExplicitDate = true;
            remaining = remaining.Replace("后天", "").Trim();
        }
        else if (remaining.Contains("今晚"))
        {
            targetDate = DateTime.Today;
            targetTime = new TimeSpan(19, 0, 0);
            hasExplicitDate = true;
            hasExplicitTime = true;
            remaining = remaining.Replace("今晚", "").Trim();
        }
        else if (remaining.Contains("今天"))
        {
            targetDate = DateTime.Today;
            hasExplicitDate = true;
            remaining = remaining.Replace("今天", "").Trim();
        }

        // 星期解析: 周X / 星期X
        var weekMatch = Regex.Match(remaining, @"(?:周|星期)([一二三四五六日天1-7])");
        if (weekMatch.Success)
        {
            int targetDow = weekMatch.Groups[1].Value switch
            {
                "一" or "1" => 1,
                "二" or "2" => 2,
                "三" or "3" => 3,
                "四" or "4" => 4,
                "五" or "5" => 5,
                "六" or "6" => 6,
                "日" or "天" or "7" => 7,
                _ => 1
            };

            int currentDow = ((int)DateTime.Today.DayOfWeek == 0) ? 7 : (int)DateTime.Today.DayOfWeek;
            int daysToAdd = (targetDow - currentDow + 7) % 7;
            if (daysToAdd == 0) daysToAdd = 7; // 指下周对应的星期
            targetDate = DateTime.Today.AddDays(daysToAdd);
            hasExplicitDate = true;
            remaining = remaining.Replace(weekMatch.Value, "").Trim();
        }

        // 5. 具体时间点提取: 9:00 / 14:30 / 9点半 / 9点
        var timeMatch = Regex.Match(remaining, @"(\d{1,2}):(\d{2})");
        if (timeMatch.Success)
        {
            int hour = int.Parse(timeMatch.Groups[1].Value);
            int min = int.Parse(timeMatch.Groups[2].Value);
            targetTime = new TimeSpan(hour, min, 0);
            hasExplicitTime = true;
            remaining = remaining.Replace(timeMatch.Value, "").Trim();
        }
        else
        {
            var zhTimeMatch = Regex.Match(remaining, @"(\d{1,2})点(?:半|(\d{1,2})分)?");
            if (zhTimeMatch.Success)
            {
                int hour = int.Parse(zhTimeMatch.Groups[1].Value);
                int min = zhTimeMatch.Value.Contains("半") ? 30 : (zhTimeMatch.Groups[2].Success ? int.Parse(zhTimeMatch.Groups[2].Value) : 0);
                if (hour < 7 && remaining.Contains("下午")) hour += 12;
                targetTime = new TimeSpan(hour, min, 0);
                hasExplicitTime = true;
                remaining = remaining.Replace(zhTimeMatch.Value, "").Trim();
            }
        }

        if (remaining.Contains("下午")) remaining = remaining.Replace("下午", "").Trim();
        if (remaining.Contains("上午")) remaining = remaining.Replace("上午", "").Trim();
        if (remaining.Contains("早上")) remaining = remaining.Replace("早上", "").Trim();

        // 标题清理
        result.Title = Regex.Replace(remaining, @"\s+", " ").Trim();
        if (string.IsNullOrEmpty(result.Title)) result.Title = "快速捕获事项";

        result.StartTime = targetDate.Date.Add(targetTime);
        result.EndTime = result.StartTime.AddMinutes(result.DurationMinutes);
        result.IsBacklog = !hasExplicitDate && !hasExplicitTime; // 如果既没有日期也没有时间，入库待办敏捷池

        result.PreviewSummary = $"{(result.IsBacklog ? "[待办池]" : $"[{result.StartTime:M/d HH:mm}-{result.EndTime:HH:mm}]")} · {result.Priority} · {result.DurationMinutes}m {(result.Tag != null ? $"· #{result.Tag}" : "")}";

        return result;
    }
}
