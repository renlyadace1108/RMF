using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using RMF.Windows.Models;
using RMF.Windows.Services;

namespace RMF.Windows;

public partial class QuickCaptureWindow : Window
{
    private readonly Action? _onSaved;

    public QuickCaptureWindow(Action? onSaved = null)
    {
        InitializeComponent();
        _onSaved = onSaved;

        Loaded += (s, e) =>
        {
            QuickInputBox.Focus();
            UpdatePills();
        };

        // 点击窗体空白区域支持自由拖动
        MouseLeftButtonDown += (s, e) =>
        {
            if (e.ButtonState == MouseButtonState.Pressed)
            {
                try { DragMove(); } catch { }
            }
        };

        // 失去焦点时自动隐藏关闭，提供丝滑体验
        Deactivated += (s, e) =>
        {
            Close();
        };
    }

    private void OnQuickInputTextChanged(object sender, TextChangedEventArgs e)
    {
        string text = QuickInputBox.Text;
        PlaceholderText.Visibility = string.IsNullOrEmpty(text) ? Visibility.Visible : Visibility.Collapsed;
        UpdatePills();
    }

    private void UpdatePills()
    {
        string text = QuickInputBox.Text;
        var parsed = NaturalLanguageScheduleParser.Parse(text);

        if (parsed.IsBacklog)
        {
            TimePillText.Text = "📥 待办敏捷池 (Backlog)";
        }
        else
        {
            TimePillText.Text = $"📅 {parsed.StartTime:M/d HH:mm} - {parsed.EndTime:HH:mm}";
        }

        string prioStr = parsed.Priority switch
        {
            "HIGH" => "⚡ 高优先级",
            "LOW" => "☕ 低优先级",
            _ => "⚡ 中优先级"
        };
        PriorityPillText.Text = prioStr;

        DurationPillText.Text = $"⏱️ {parsed.DurationMinutes} 分钟";

        if (!string.IsNullOrEmpty(parsed.Tag))
        {
            TagPill.Visibility = Visibility.Visible;
            TagPillText.Text = $"#{parsed.Tag}";
        }
        else
        {
            TagPill.Visibility = Visibility.Collapsed;
        }
    }

    private void OnQuickInputKeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Escape)
        {
            Close();
            return;
        }

        if (e.Key == Key.Enter)
        {
            string raw = QuickInputBox.Text.Trim();
            if (string.IsNullOrEmpty(raw))
            {
                Close();
                return;
            }

            var parsed = NaturalLanguageScheduleParser.Parse(raw);
            var item = new ScheduleItem
            {
                Title = parsed.Title,
                Category = parsed.Category,
                Priority = parsed.Priority,
                StartTime = parsed.StartTime,
                EndTime = parsed.EndTime,
                EstimatedMinutes = parsed.DurationMinutes,
                IsBacklog = parsed.IsBacklog,
                Source = "⚡ 全局闪念捕获",
                Dod = $"[ ] 完成【{parsed.Title}】",
                Description = parsed.Tag != null ? $"#{parsed.Tag}" : ""
            };

            DatabaseService.AddSchedule(item);

            _onSaved?.Invoke();
            Close();
        }
    }

    private void OnCloseClicked(object sender, RoutedEventArgs e)
    {
        Close();
    }
}
