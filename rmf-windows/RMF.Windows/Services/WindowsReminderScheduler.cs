using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using Microsoft.Toolkit.Uwp.Notifications;
using Microsoft.Win32;
using Windows.UI.Notifications;
using RMF.Windows.Models;

namespace RMF.Windows.Services;

/// <summary>
/// Windows 原生防漏提醒中枢：
/// 1. 原生接入 Windows Action Center 系统级调度 (ScheduledToastNotification)，即使 RMF 进程彻底关闭，Windows 操作系统内核依然准时响铃唤醒！
/// 2. 深度支持重复周期自动推进 (每天/工作日/每周/每月/每年)，周期性提醒永不丢失。
/// 3. 逾期智能补漏 (关机开机后即时追溯弹窗) 与稍后推迟/一键完成交互。
/// 4. 支持开机自启静默常驻系统托盘，实现全天候无缝守护。
/// </summary>
public static class WindowsReminderScheduler
{
    private const string ReminderToastGroup = "RMF_REMINDERS";
    private static readonly HashSet<string> _alertedReminderIds = new(StringComparer.OrdinalIgnoreCase);
    private static Action? _onRestoreWindow;
    private static Action<string>? _onNavigateToReminders;
    private static bool _isInitialized = false;

    public static void Initialize(Action onRestoreWindow, Action<string>? onNavigateToReminders = null)
    {
        _onRestoreWindow = onRestoreWindow;
        _onNavigateToReminders = onNavigateToReminders;

        if (_isInitialized) return;
        _isInitialized = true;

        try
        {
            ToastNotificationManagerCompat.OnActivated += toastArgs =>
            {
                try
                {
                    var args = ToastArguments.Parse(toastArgs.Argument);
                    if (args.TryGetValue("action", out string? action) && args.TryGetValue("reminderId", out string? reminderId))
                    {
                        HandleToastAction(action, reminderId);
                    }
                    else
                    {
                        System.Windows.Application.Current?.Dispatcher?.Invoke(() =>
                        {
                            _onRestoreWindow?.Invoke();
                        });
                    }
                }
                catch { }
            };
        }
        catch { }
    }

    /// <summary>
    /// 将单条提醒事项直接注册至 Windows OS 底层 Action Center 调度队列中
    /// </summary>
    public static void ScheduleReminder(ReminderItem reminder)
    {
        if (reminder == null || reminder.IsCompleted || reminder.IsDeleted)
        {
            if (reminder != null) CancelScheduledReminder(reminder.Id);
            return;
        }

        try
        {
            if (!DateTime.TryParse(reminder.RemindTime, out var remindDt))
            {
                return;
            }

            var triggerTime = remindDt.AddMinutes(-reminder.AdvanceMinutes);

            // 若触发时间在未来：直接排期至 Windows OS 核心调度器中 (进程关闭依然准时触发)
            if (triggerTime > DateTime.Now)
            {
                CancelScheduledReminder(reminder.Id);

                string baseDir = AppDomain.CurrentDomain.BaseDirectory;
                string logoPath = Path.Combine(baseDir, "app_logo_256.png");
                if (!File.Exists(logoPath)) logoPath = Path.Combine(baseDir, "app.ico");

                var builder = new ToastContentBuilder()
                    .AddArgument("action", "viewReminder")
                    .AddArgument("reminderId", reminder.Id)
                    .AddText($"🔔 {reminder.Title}")
                    .AddText(string.IsNullOrWhiteSpace(reminder.Content)
                        ? $"到期时间：{reminder.DisplayTime}"
                        : $"{reminder.Content} ({reminder.DisplayTime})")
                    .AddButton(new ToastButton()
                        .SetContent("✓ 标记完成")
                        .AddArgument("action", "complete")
                        .AddArgument("reminderId", reminder.Id)
                        .SetBackgroundActivation())
                    .AddButton(new ToastButton()
                        .SetContent("⏰ 稍后推迟 10分钟")
                        .AddArgument("action", "snooze")
                        .AddArgument("reminderId", reminder.Id)
                        .SetBackgroundActivation());

                if (File.Exists(logoPath))
                {
                    builder.AddAppLogoOverride(new Uri(logoPath), ToastGenericAppLogoCrop.Circle);
                }

                var scheduledToast = new ScheduledToastNotification(builder.GetXml(), new DateTimeOffset(triggerTime))
                {
                    Tag = reminder.Id,
                    Group = ReminderToastGroup
                };

                ToastNotificationManagerCompat.CreateToastNotifier().AddToSchedule(scheduledToast);
            }
            else
            {
                // 触发时间已过 (可能电脑休眠或关机错过)：触发逾期补漏弹窗
                if (!_alertedReminderIds.Contains(reminder.Id))
                {
                    _alertedReminderIds.Add(reminder.Id);
                    ShowOverdueNotification(reminder);
                }

                // 若此事件配置了周期性重复，自动推进到未来周期并排期
                if (!string.IsNullOrWhiteSpace(reminder.RepeatRule) && !reminder.RepeatRule.Equals("NONE", StringComparison.OrdinalIgnoreCase))
                {
                    var nextIso = ComputeNextOccurrence(reminder.RemindTime, reminder.RepeatRule);
                    if (nextIso != null)
                    {
                        DatabaseService.AdvanceReminderRepeat(reminder.Id, nextIso);
                        reminder.RemindTime = nextIso;
                        ScheduleReminder(reminder);
                    }
                }
            }
        }
        catch { }
    }

    /// <summary>
    /// 从 Windows Action Center 取消指定提醒的系统底层调度
    /// </summary>
    public static void CancelScheduledReminder(string reminderId)
    {
        try
        {
            var notifier = ToastNotificationManagerCompat.CreateToastNotifier();
            var scheduledToasts = notifier.GetScheduledToastNotifications();
            foreach (var toast in scheduledToasts)
            {
                if (toast.Group == ReminderToastGroup && (string.IsNullOrEmpty(reminderId) || toast.Tag == reminderId))
                {
                    notifier.RemoveFromSchedule(toast);
                }
            }
        }
        catch { }
    }

    /// <summary>
    /// 全量核验并重新向 Windows Action Center 注册所有待办提醒 (用于开机、同步恢复或数据导入)
    /// </summary>
    public static void RescheduleAll()
    {
        try
        {
            CancelScheduledReminder(string.Empty); // 清空旧调度
            var pendingReminders = DatabaseService.GetPendingReminders();
            foreach (var reminder in pendingReminders)
            {
                ScheduleReminder(reminder);
            }
        }
        catch { }
    }

    /// <summary>
    /// 触发逾期未处理补漏通知
    /// </summary>
    public static void ShowOverdueNotification(ReminderItem reminder)
    {
        try
        {
            string baseDir = AppDomain.CurrentDomain.BaseDirectory;
            string logoPath = Path.Combine(baseDir, "app_logo_256.png");
            if (!File.Exists(logoPath)) logoPath = Path.Combine(baseDir, "app.ico");

            var builder = new ToastContentBuilder()
                .AddArgument("action", "viewReminder")
                .AddArgument("reminderId", reminder.Id)
                .AddText($"⚠️ [待处理提醒] {reminder.Title}")
                .AddText(string.IsNullOrWhiteSpace(reminder.Content)
                    ? $"原定时间: {reminder.DisplayTime}，请及时处理"
                    : $"{reminder.Content} (原定时间: {reminder.DisplayTime})")
                .AddButton(new ToastButton()
                    .SetContent("✓ 标记完成")
                    .AddArgument("action", "complete")
                    .AddArgument("reminderId", reminder.Id)
                    .SetBackgroundActivation())
                .AddButton(new ToastButton()
                    .SetContent("⏰ 稍后推迟 10分钟")
                    .AddArgument("action", "snooze")
                    .AddArgument("reminderId", reminder.Id)
                    .SetBackgroundActivation());

            if (File.Exists(logoPath))
            {
                builder.AddAppLogoOverride(new Uri(logoPath), ToastGenericAppLogoCrop.Circle);
            }

            builder.Show();
        }
        catch { }
    }

    /// <summary>
    /// 处理用户在 Windows 原生横幅上的按钮动作 (完成 / 稍后提醒)
    /// </summary>
    public static void HandleToastAction(string action, string reminderId)
    {
        if (string.IsNullOrEmpty(reminderId)) return;

        try
        {
            var reminder = DatabaseService.GetReminderById(reminderId);
            if (reminder == null) return;

            if (action.Equals("complete", StringComparison.OrdinalIgnoreCase))
            {
                if (!string.IsNullOrWhiteSpace(reminder.RepeatRule) && !reminder.RepeatRule.Equals("NONE", StringComparison.OrdinalIgnoreCase))
                {
                    var nextIso = ComputeNextOccurrence(reminder.RemindTime, reminder.RepeatRule);
                    if (nextIso != null)
                    {
                        DatabaseService.AdvanceReminderRepeat(reminder.Id, nextIso);
                        reminder.RemindTime = nextIso;
                        ScheduleReminder(reminder);
                    }
                    else
                    {
                        DatabaseService.SetReminderCompleted(reminder.Id, true);
                        CancelScheduledReminder(reminder.Id);
                    }
                }
                else
                {
                    DatabaseService.SetReminderCompleted(reminder.Id, true);
                    CancelScheduledReminder(reminder.Id);
                }
            }
            else if (action.Equals("snooze", StringComparison.OrdinalIgnoreCase))
            {
                var newTime = DateTime.Now.AddMinutes(10).ToString("yyyy-MM-ddTHH:mm:ss");
                DatabaseService.AdvanceReminderRepeat(reminder.Id, newTime);
                reminder.RemindTime = newTime;
                ScheduleReminder(reminder);
            }
            else if (action.Equals("viewReminder", StringComparison.OrdinalIgnoreCase))
            {
                System.Windows.Application.Current?.Dispatcher?.Invoke(() =>
                {
                    _onRestoreWindow?.Invoke();
                    _onNavigateToReminders?.Invoke(reminderId);
                });
            }
        }
        catch { }
    }

    /// <summary>
    /// 计算周期性重复规则的下一个有效时间戳 (保证严格在当前时间之后)
    /// </summary>
    public static string? ComputeNextOccurrence(string currentIso, string repeatRule, DateTime? baseTime = null)
    {
        if (string.IsNullOrWhiteSpace(repeatRule) || repeatRule.Equals("NONE", StringComparison.OrdinalIgnoreCase))
        {
            return null;
        }

        DateTime now = baseTime ?? DateTime.Now;
        if (!DateTime.TryParse(currentIso, out var dt))
        {
            dt = now;
        }

        var next = dt;
        string rule = repeatRule.ToUpperInvariant();

        switch (rule)
        {
            case "DAILY":
                do
                {
                    next = next.AddDays(1);
                } while (next <= now);
                break;

            case "WEEKDAYS":
                do
                {
                    next = next.AddDays(1);
                } while (next <= now || next.DayOfWeek == DayOfWeek.Saturday || next.DayOfWeek == DayOfWeek.Sunday);
                break;

            case "WEEKLY":
                do
                {
                    next = next.AddDays(7);
                } while (next <= now);
                break;

            case "MONTHLY":
                do
                {
                    next = next.AddMonths(1);
                } while (next <= now);
                break;

            case "YEARLY":
                do
                {
                    next = next.AddYears(1);
                } while (next <= now);
                break;

            default:
                return null;
        }

        return next.ToString("yyyy-MM-ddTHH:mm:ss");
    }

    // =========================================================================
    // ======================= 开机自启动与后台静默守护 =========================
    // =========================================================================

    private const string AutoRunRegistrySubKey = @"Software\Microsoft\Windows\CurrentVersion\Run";
    private const string AutoRunAppName = "RMF";

    public static bool IsAutoStartOnBootEnabled()
    {
        try
        {
            using var key = Registry.CurrentUser.OpenSubKey(AutoRunRegistrySubKey, false);
            return key?.GetValue(AutoRunAppName) != null;
        }
        catch
        {
            return false;
        }
    }

    public static bool SetAutoStartOnBoot(bool enable)
    {
        try
        {
            using var key = Registry.CurrentUser.OpenSubKey(AutoRunRegistrySubKey, true);
            if (key == null) return false;

            if (enable)
            {
                string? exePath = Process.GetCurrentProcess().MainModule?.FileName;
                if (!string.IsNullOrEmpty(exePath) && File.Exists(exePath))
                {
                    key.SetValue(AutoRunAppName, $"\"{exePath}\" --minimized");
                    return true;
                }
                return false;
            }
            else
            {
                key.DeleteValue(AutoRunAppName, false);
                return true;
            }
        }
        catch
        {
            return false;
        }
    }
}
