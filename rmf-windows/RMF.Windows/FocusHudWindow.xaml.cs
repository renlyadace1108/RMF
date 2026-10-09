using System;
using System.Collections.Generic;
using System.Linq;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Threading;
using RMF.Windows.Models;
using RMF.Windows.Services;

namespace RMF.Windows;

public partial class FocusHudWindow : Window
{
    public static FocusHudWindow? Current { get; private set; }

    private ScheduleItem _schedule;
    private readonly DispatcherTimer _timer;
    private int _remainingSeconds;
    private int _elapsedSeconds;
    private bool _isPaused = false;
    private bool _isPassThrough = false;
    private string _selectedInterruptionType = "EXTERNAL";
    private readonly Action? _onRefreshMain;

    // 双轨计时与闲置侦测
    private bool _isPomodoro = true;
    private bool _isBreak = false;
    private readonly PassiveTrackingService _tracker;

    // Win32 API 鼠标穿透
    private const int GWL_EXSTYLE = -20;
    private const int WS_EX_TRANSPARENT = 0x00000020;
    private const int WS_EX_LAYERED = 0x00080000;

    [DllImport("user32.dll", SetLastError = true)]
    private static extern int GetWindowLong(IntPtr hWnd, int nIndex);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern int SetWindowLong(IntPtr hWnd, int nIndex, int dwNewLong);

    public ScheduleItem Schedule => _schedule;
    public int ElapsedSeconds => _elapsedSeconds;
    public int RemainingSeconds => _remainingSeconds;
    public bool IsPaused => _isPaused;
    public bool IsPomodoro => _isPomodoro;

    public void SwitchSchedule(ScheduleItem newSchedule)
    {
        _schedule = newSchedule;
        TaskTitleText.Text = string.IsNullOrEmpty(_schedule.Title) ? "专注时段" : _schedule.Title;
        RenderDodList();
        _onRefreshMain?.Invoke();
    }

    public void TogglePlayPause()
    {
        OnPlayPauseClicked(this, new RoutedEventArgs());
    }

    public void FinishTask()
    {
        OnFinishTaskClicked(this, new RoutedEventArgs());
    }

    public FocusHudWindow(ScheduleItem schedule, Action? onRefreshMain = null, int? customDurationMinutes = null)
    {
        Current = this;
        InitializeComponent();
        _schedule = schedule;
        _onRefreshMain = onRefreshMain;

        // 默认定位到屏幕顶部正中
        Left = (SystemParameters.PrimaryScreenWidth - Width) / 2;
        Top = 20;

        TaskTitleText.Text = string.IsNullOrEmpty(_schedule.Title) ? "专注时段" : _schedule.Title;

        var cfg = ConfigService.Current;
        _isPomodoro = cfg.FocusDefaultMode != "STOPWATCH";

        int durationMins = customDurationMinutes.HasValue && customDurationMinutes.Value > 0
            ? customDurationMinutes.Value
            : (_schedule.EstimatedMinutes > 0 ? _schedule.EstimatedMinutes : Math.Max(5, cfg.FocusDefaultDurationMinutes));

        _remainingSeconds = durationMins * 60;
        _elapsedSeconds = 0;

        _timer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1) };
        _timer.Tick += OnTimerTick;
        _timer.Start();

        // 挂载被动闲置侦测 (按配置闲置阈值自动提醒并挂起)
        _tracker = new PassiveTrackingService
        {
            IdleThresholdSeconds = Math.Max(60, cfg.FocusIdleThresholdMinutes * 60)
        };
        _tracker.IdleStateChanged += isIdle =>
        {
            if (isIdle && !_isPaused)
            {
                _isPaused = true;
                PlayPauseIcon.Text = "▶";
                StatusDot.Fill = new SolidColorBrush(Color.FromRgb(0x94, 0xA3, 0xB8)); // 灰色闲置
                ModeBadgeText.Text = "离席挂起";
            }
        };
        _tracker.Start();

        UpdateTimerDisplay();
        RenderDodList();
        FocusAssistService.EnableFocusMode();
    }

    private void OnTimerTick(object? sender, EventArgs e)
    {
        if (_isPaused) return;

        _elapsedSeconds++;
        if (_isPomodoro)
        {
            if (_remainingSeconds > 0)
            {
                _remainingSeconds--;
            }
            else
            {
                // 番茄钟倒计时结束: 25m 专注 <=> 5m 休息循环
                _isBreak = !_isBreak;
                _remainingSeconds = _isBreak ? 5 * 60 : 25 * 60;
                if (_isBreak)
                {
                    ModeBadgeText.Text = "☕ 番茄休息";
                    StatusDot.Fill = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
                }
                else
                {
                    ModeBadgeText.Text = "🧠 番茄专注";
                    StatusDot.Fill = new SolidColorBrush(Color.FromRgb(0x38, 0xBD, 0xF8));
                }
            }
        }

        UpdateTimerDisplay();
    }

    private void UpdateTimerDisplay()
    {
        if (!_isPomodoro)
        {
            // 正向秒表模式
            int m = _elapsedSeconds / 60;
            int s = _elapsedSeconds % 60;
            TimerText.Text = $"{m:D2}:{s:D2}";
            TimerText.Foreground = new SolidColorBrush(Color.FromRgb(0xA7, 0x8B, 0xFA));
            ModeBadgeText.Text = "正向秒表";
            return;
        }

        if (_remainingSeconds > 0)
        {
            int m = _remainingSeconds / 60;
            int s = _remainingSeconds % 60;
            TimerText.Text = $"{m:D2}:{s:D2}";
            TimerText.Foreground = _isBreak ? new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81)) : new SolidColorBrush(Color.FromRgb(0x38, 0xBD, 0xF8));
            if (!_isBreak) ModeBadgeText.Text = "深度专注";
        }
        else
        {
            int m = _elapsedSeconds / 60;
            int s = _elapsedSeconds % 60;
            TimerText.Text = $"+{m:D2}:{s:D2}";
            TimerText.Foreground = new SolidColorBrush(Color.FromRgb(0xF5, 0x9E, 0x0B));
            ModeBadgeText.Text = "超时加时";
        }
    }

    private void OnToggleTimerModeClicked(object sender, RoutedEventArgs e)
    {
        _isPomodoro = !_isPomodoro;
        if (_isPomodoro)
        {
            TimerModeIcon.Foreground = new SolidColorBrush(Color.FromRgb(0x38, 0xBD, 0xF8));
            _remainingSeconds = 25 * 60;
            _isBreak = false;
        }
        else
        {
            TimerModeIcon.Foreground = new SolidColorBrush(Color.FromRgb(0xA7, 0x8B, 0xFA));
        }
        UpdateTimerDisplay();
    }

    private void OnCapsuleBarMouseDown(object sender, MouseButtonEventArgs e)
    {
        if (e.LeftButton == MouseButtonState.Pressed && !_isPassThrough)
        {
            try
            {
                DragMove();
            }
            catch { }
        }
    }

    private void OnPlayPauseClicked(object sender, RoutedEventArgs e)
    {
        _isPaused = !_isPaused;
        if (_isPaused)
        {
            PlayPauseIcon.Text = "▶";
            StatusDot.Fill = new SolidColorBrush(Color.FromRgb(0xF5, 0x9E, 0x0B)); // 黄色暂停
            ModeBadgeText.Text = "已暂停";
        }
        else
        {
            PlayPauseIcon.Text = "⏸";
            StatusDot.Fill = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81)); // 绿色继续
            ModeBadgeText.Text = "深度专注";
        }
    }

    private void OnToggleDodPanelClicked(object sender, RoutedEventArgs e)
    {
        InterruptionPanel.Visibility = Visibility.Collapsed;
        DodPanel.Visibility = (DodPanel.Visibility == Visibility.Visible) ? Visibility.Collapsed : Visibility.Visible;
        if (DodPanel.Visibility == Visibility.Visible)
        {
            RenderDodList();
        }
    }

    private void OnToggleInterruptionPanelClicked(object sender, RoutedEventArgs e)
    {
        DodPanel.Visibility = Visibility.Collapsed;
        InterruptionPanel.Visibility = (InterruptionPanel.Visibility == Visibility.Visible) ? Visibility.Collapsed : Visibility.Visible;
    }

    private void OnLogInterruptionTypeClicked(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is string type)
        {
            _selectedInterruptionType = type;
            InterruptionNoteInput.Text = $"发生 {btn.Content}";
        }
    }

    private void OnConfirmInterruptionClicked(object sender, RoutedEventArgs e)
    {
        var item = new InterruptionItem
        {
            ScheduleId = _schedule.Id,
            Type = _selectedInterruptionType,
            DurationSeconds = 60, // 默认打断 1 分钟损耗
            Timestamp = DateTime.Now,
            Note = InterruptionNoteInput.Text.Trim()
        };

        DatabaseService.AddInterruption(item);
        InterruptionPanel.Visibility = Visibility.Collapsed;

        // 打断时自动暂停 3 分钟给用户缓冲
        _isPaused = true;
        PlayPauseIcon.Text = "▶";
        StatusDot.Fill = new SolidColorBrush(Color.FromRgb(0xEF, 0x44, 0x44));
        ModeBadgeText.Text = "打断中";
    }

    private void RenderDodList()
    {
        DodItemsList.Children.Clear();
        var items = DataLineageService.ParseDodItems(_schedule.Dod);

        int total = items.Count;
        int done = items.Count(i => i.Done);
        int percent = total > 0 ? (int)Math.Round((double)done / total * 100) : 0;
        DodProgressText.Text = $"{done}/{total} ({percent}%)";

        foreach (var dod in items)
        {
            var row = new Grid { Margin = new Thickness(0, 3, 0, 3) };
            row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(24) });
            row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

            var cb = new CheckBox
            {
                IsChecked = dod.Done,
                VerticalAlignment = VerticalAlignment.Center
            };

            var tb = new TextBlock
            {
                Text = dod.Text,
                FontSize = 11.5,
                Foreground = dod.Done ? new SolidColorBrush(Color.FromRgb(0x94, 0xA3, 0xB8)) : new SolidColorBrush(Color.FromRgb(0xF1, 0xF5, 0xF9)),
                TextDecorations = dod.Done ? TextDecorations.Strikethrough : null,
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(4, 0, 0, 0)
            };

            cb.Click += (s, e) =>
            {
                dod.Done = (cb.IsChecked == true);
                tb.Foreground = dod.Done ? new SolidColorBrush(Color.FromRgb(0x94, 0xA3, 0xB8)) : new SolidColorBrush(Color.FromRgb(0xF1, 0xF5, 0xF9));
                tb.TextDecorations = dod.Done ? TextDecorations.Strikethrough : null;

                _schedule.Dod = DataLineageService.SerializeDodItems(items);
                DataLineageService.OnScheduleDodToggled(_schedule);
                Dispatcher.BeginInvoke(new Action(() =>
                {
                    RenderDodList();
                    _onRefreshMain?.Invoke();
                }));
            };

            Grid.SetColumn(cb, 0);
            Grid.SetColumn(tb, 1);
            row.Children.Add(cb);
            row.Children.Add(tb);
            DodItemsList.Children.Add(row);
        }
    }

    private void OnAddDodItemClicked(object sender, RoutedEventArgs e)
    {
        string text = NewDodInput.Text.Trim();
        if (string.IsNullOrEmpty(text)) return;

        var items = DataLineageService.ParseDodItems(_schedule.Dod);
        items.Add(new DataLineageService.DodItem
        {
            Id = Guid.NewGuid().ToString("N"),
            Text = text,
            Done = false
        });

        _schedule.Dod = DataLineageService.SerializeDodItems(items);
        DatabaseService.UpsertSchedule(_schedule);
        NewDodInput.Clear();
        Dispatcher.BeginInvoke(new Action(() =>
        {
            RenderDodList();
            _onRefreshMain?.Invoke();
        }));
    }

    private void OnNewDodInputKeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Enter)
        {
            OnAddDodItemClicked(sender, e);
        }
    }

    public void TogglePassThrough()
    {
        try
        {
            IntPtr hwnd = new WindowInteropHelper(this).Handle;
            if (hwnd == IntPtr.Zero) return;

            int extendedStyle = GetWindowLong(hwnd, GWL_EXSTYLE);

            _isPassThrough = !_isPassThrough;
            if (_isPassThrough)
            {
                SetWindowLong(hwnd, GWL_EXSTYLE, extendedStyle | WS_EX_TRANSPARENT | WS_EX_LAYERED);
                LockIcon.Text = "🔒";
                LockPassThroughButton.ToolTip = "鼠标穿透已启用 (快捷键 Ctrl+Shift+L 解除穿透)";
                Opacity = 0.75;
            }
            else
            {
                SetWindowLong(hwnd, GWL_EXSTYLE, extendedStyle & ~WS_EX_TRANSPARENT);
                LockIcon.Text = "🔓";
                LockPassThroughButton.ToolTip = "鼠标穿透锁定 (Ctrl+Shift+L 解锁)";
                Opacity = 1.0;
            }
        }
        catch { }
    }

    private void OnTogglePassThroughClicked(object sender, RoutedEventArgs e)
    {
        TogglePassThrough();
    }

    private void OnFinishTaskClicked(object sender, RoutedEventArgs e)
    {
        _timer.Stop();
        _tracker?.Stop();
        _schedule.Status = "COMPLETED";
        _schedule.ActualMinutes = Math.Max(1, _elapsedSeconds / 60);

        DatabaseService.UpsertSchedule(_schedule);
        DataLineageService.OnScheduleCompleted(_schedule);
        FocusAssistService.DisableFocusMode();

        _onRefreshMain?.Invoke();
        Close();
    }

    private void OnCloseHudClicked(object sender, RoutedEventArgs e)
    {
        _timer.Stop();
        _tracker?.Stop();
        FocusAssistService.DisableFocusMode();
        Close();
    }

    protected override void OnClosed(EventArgs e)
    {
        if (Current == this) Current = null;
        try
        {
            _timer?.Stop();
            _tracker?.Stop();
            FocusAssistService.DisableFocusMode();
        }
        catch { }
        base.OnClosed(e);
    }
}
