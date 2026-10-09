using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Media.Imaging;
using System.Windows.Shapes;
using System.Windows.Threading;
using Microsoft.Data.Sqlite;
using RMF.Windows.Models;
using RMF.Windows.Services;

namespace RMF.Windows;

public partial class MainWindow : Window
{
    private readonly DispatcherTimer _perfTimer;
    private readonly TrayIconService _trayIconService;
    private GlobalHotKeyService? _hotKeyService;
    private FocusHudWindow? _activeFocusHud;
    private bool _isForceExit = false;

    // 核心日期与视图状态
    private DateTime _currentDate = DateTime.Today;
    private DateTime _miniCalMonth = DateTime.Today;
    private string _currentView = "Week"; // "Day", "Week", "Month", "Year", "Custom", "Agenda"
    private DateTime _customStartDate = DateTime.Today;
    private DateTime _customEndDate = DateTime.Today.AddDays(3);
    private int _customDays = 4;
    private bool _isCustomAgendaMode = false;
    private bool _isUpdatingCustomPickers = false;
    private const double HourHeight = 54.0;

    // 事件编辑状态
    private string? _editingEventId = null;
    private DateTime _clickedSlotDateTime = DateTime.Today.AddHours(9);
    private bool _isUpdatingRgbPicker = false;
    private string _currentRgbPickerHex = "#1A73E8";

    // 搜索过滤与分类过滤
    private string _searchKeyword = string.Empty;
    private readonly HashSet<string> _disabledCategoryFilters = new(StringComparer.OrdinalIgnoreCase);

    // 目标层级与待办敏捷池状态
    private string? _editingGoalId = null;

    // 画布框选建块状态 (Drag-to-Select)
    private bool _isDragSelecting = false;
    private Point _dragSelectStartPoint;
    private Border? _dragSelectBorder;
    private DateTime _dragSelectDate = DateTime.Today;


    // Google Calendar 快速详情卡片与拖拽调整时长状态
    private ScheduleItem? _quickDetailItem = null;
    private bool _isResizingEvent = false;
    private ScheduleItem? _resizingItem = null;
    private double _resizeStartY = 0;
    private DateTime _resizeInitialEndTime;
    private readonly HashSet<string> _notifiedUpcomingEventIds = new(StringComparer.OrdinalIgnoreCase);
    private ScheduleItem? _upcomingAlertItem = null;

    // 内存与窗口嗅探 Win32 API
    [DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int attrValue, int attrSize);
    private const int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
    private const int DWMWA_SYSTEMBACKDROP_TYPE = 38; // 2 = Mica

    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll", CharSet = CharSet.Auto, SetLastError = true)]
    private static extern int GetWindowText(IntPtr hWnd, StringBuilder lpString, int nMaxCount);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);

    [DllImport("psapi.dll")]
    private static extern int EmptyWorkingSet(IntPtr hwProc);

    public MainWindow()
    {
        InitializeComponent();

        Loaded += (s, e) =>
        {
            DatabaseService.Initialize();
            var appConfig = ConfigService.Load();
            ThemeService.ApplyTheme(this, appConfig);

            BuildWeekHourGrid();
            BuildDayHourGrid();

            WeekEventsCanvas.SizeChanged += (sender, args) => RenderWeekEvents();
            DayEventsCanvas.SizeChanged += (sender, args) => RenderDayEvents();

            RenderAllCalendarViews();
            RefreshGoalTree();
            RefreshBacklogList();
            RefreshDeferredQueue();
            UpdateCognitiveLoadQuota();
            UpdateOptimismMultiplier();
            RefreshTimePnlData();

            ScrollWeekToCurrentTime();
            UpdateTelemetry();
            LoadSettingsIntoUi();
            InitTimetableControls();

            // 启用全页面 60/120Hz 丝滑物理平滑滚动并解除嵌套滚轮死锁
            SmoothScrollHelper.EnableSmoothScroll(SummaryScrollViewer);
            SmoothScrollHelper.RegisterNestedChild(DailyClockScrollViewer, SummaryScrollViewer);
            SmoothScrollHelper.EnableSmoothScroll(FocusPageScrollViewer);
            SmoothScrollHelper.EnableSmoothScroll(DailyReportScrollViewer);
            SmoothScrollHelper.EnableSmoothScroll(StudyTopicsScrollViewer);
            SmoothScrollHelper.EnableSmoothScroll(SettingsScrollViewer);
            SmoothScrollHelper.EnableSmoothScroll(TutorialScrollViewer);

            // 注册全局极速快捷键 (Ctrl+Shift+Space 闪念捕获, Ctrl+Shift+F 专注胶囊)
            try
            {
                IntPtr hWnd = new WindowInteropHelper(this).Handle;
                _hotKeyService = new GlobalHotKeyService(hWnd,
                    onQuickCapture: () =>
                    {
                        Dispatcher.Invoke(() =>
                        {
                            var win = new QuickCaptureWindow(() =>
                            {
                                RenderAllCalendarViews();
                                RefreshBacklogList();
                                RefreshGoalTree();
                                UpdateCognitiveLoadQuota();
                            });
                            win.Show();
                        });
                    },
                    onFocusHud: () =>
                    {
                        Dispatcher.Invoke(() =>
                        {
                            var todayPending = DatabaseService.GetTodaySchedules()
                                .FirstOrDefault(s => s.Status != "COMPLETED" && !s.IsAllDay)
                                ?? new ScheduleItem { Title = "深度工作攻坚", EstimatedMinutes = 25 };
                            LaunchFocusHudForSchedule(todayPending);
                        });
                    },
                    onTogglePassThrough: () =>
                    {
                        Dispatcher.Invoke(() =>
                        {
                            FocusHudWindow.Current?.TogglePassThrough();
                        });
                    }
                );
            }
            catch { }

            // 首次启动时弹出新手教程引导向导
            if (!appConfig.HasCompletedOnboarding)
            {
                ShowOnboardingModal();
            }

            // 启动渲染完成后 1.5 秒静默回收 JIT 编译废料与冷启动临时页
            _ = Task.Run(async () =>
            {
                await Task.Delay(1500);
                Dispatcher.Invoke(PerformSilentMemoryTrim);
            });
        };

        // 窗口最小化转入后台时，自动让渡闲置物理内存
        StateChanged += (s, e) =>
        {
            if (WindowState == WindowState.Minimized)
            {
                PerformSilentMemoryTrim();
            }
        };

        _perfTimer = new DispatcherTimer
        {
            Interval = TimeSpan.FromSeconds(2)
        };
        int perfTicks = 0;
        _perfTimer.Tick += (s, e) =>
        {
            UpdateTelemetry();
            CheckUpcomingEvents();
            if (_activePrimaryPage == "Focus")
            {
                UpdateFocusLiveMonitor();
            }
            if (++perfTicks >= 150) // 每 5 分钟在后台自动静默整理一次，确保长期运行零堆积
            {
                perfTicks = 0;
                PerformSilentMemoryTrim();
            }
        };
        _perfTimer.Start();

        // 初始化系统托盘常驻中枢
        _trayIconService = new TrayIconService(
            onRestore: () => Dispatcher.Invoke(RestoreFromTray),
            onNewEvent: () => Dispatcher.Invoke(() => OpenEventCreateModalWithRange(DateTime.Today, DateTime.Today.AddHours(9), DateTime.Today.AddHours(10))),
            onExit: () => Dispatcher.Invoke(ExitApplicationDirectly)
        );
    }

    private void RestoreFromTray()
    {
        Show();
        if (WindowState == WindowState.Minimized)
        {
            WindowState = WindowState.Normal;
        }
        Activate();
        Focus();
    }

    private void ExitApplicationDirectly()
    {
        _isForceExit = true;
        _hotKeyService?.Dispose();
        _trayIconService?.Dispose();
        System.Windows.Application.Current.Shutdown();
    }

    protected override void OnClosing(CancelEventArgs e)
    {
        if (!_isForceExit)
        {
            e.Cancel = true;
            Hide();
            _trayIconService?.ShowNotification("RMF 已转入后台常驻", "双击右下角托盘图标或右键菜单即可随时呼出。");
            PerformSilentMemoryTrim();
        }
        else
        {
            _hotKeyService?.Dispose();
            _trayIconService?.Dispose();
            base.OnClosing(e);
        }
    }

    private void ApplyWindows11ImmersiveDarkTitlebar()
    {
        try
        {
            IntPtr hwnd = new WindowInteropHelper(this).Handle;
            if (hwnd != IntPtr.Zero)
            {
                int useDarkMode = 1;
                DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, ref useDarkMode, sizeof(int));
                int backdropType = 2; // Mica
                DwmSetWindowAttribute(hwnd, DWMWA_SYSTEMBACKDROP_TYPE, ref backdropType, sizeof(int));
            }
        }
        catch { }
    }

    private void PerformSilentMemoryTrim()
    {
        try
        {
            GC.Collect(2, GCCollectionMode.Aggressive, false, true);
            GC.WaitForPendingFinalizers();
            EmptyWorkingSet(Process.GetCurrentProcess().Handle);
        }
        catch { }
    }

    private void UpdateTelemetry()
    {
        try
        {
            IntPtr handle = GetForegroundWindow();
            if (handle != IntPtr.Zero)
            {
                var sb = new StringBuilder(256);
                GetWindowText(handle, sb, sb.Capacity);
                GetWindowThreadProcessId(handle, out uint pid);

                string procName = "Unknown";
                try
                {
                    using var proc = Process.GetProcessById((int)pid);
                    procName = proc.ProcessName;
                }
                catch { }

                string title = sb.ToString();
                if (string.IsNullOrWhiteSpace(title)) title = "桌面 / 无标题";

                if (procName != "Unknown" && procName != "RMF.Windows")
                {
                    DatabaseService.LogActivity(procName, title, 2);
                }
            }
        }
        catch { }
    }

    // =========================================================================
    // =================== GOOGLE CALENDAR MAIN RENDERING ======================
    // =========================================================================

    private void RenderAllCalendarViews()
    {
        // 1. 更新顶部日期大标题 (仅在概要视图下生效)
        if (_activePrimaryPage == "Overview")
        {
            var culture = new CultureInfo("zh-CN");
            if (_currentView == "Day")
            {
                TopDateHeaderTitle.Text = _currentDate.ToString("yyyy年M月d日 · dddd", culture);
            }
            else if (_currentView == "Week")
            {
                DateTime monday = GetMondayOfWeek(_currentDate);
                DateTime sunday = monday.AddDays(6);
                if (monday.Month == sunday.Month)
                {
                    TopDateHeaderTitle.Text = monday.ToString("yyyy年M月", culture);
                }
                else if (monday.Year == sunday.Year)
                {
                    TopDateHeaderTitle.Text = $"{monday:yyyy年M月} - {sunday:M月}";
                }
                else
                {
                    TopDateHeaderTitle.Text = $"{monday:yyyy年M月} - {sunday:yyyy年M月}";
                }
            }
            else if (_currentView == "Month")
            {
                TopDateHeaderTitle.Text = _currentDate.ToString("yyyy年M月", culture);
            }
            else if (_currentView == "Year")
            {
                TopDateHeaderTitle.Text = $"{_currentDate.Year}年";
            }
            else if (_currentView == "Custom")
            {
                if (_customStartDate.Year == _customEndDate.Year)
                {
                    TopDateHeaderTitle.Text = $"{_customStartDate:yyyy年M月d日} - {_customEndDate:M月d日} (共 {_customDays} 天)";
                }
                else
                {
                    TopDateHeaderTitle.Text = $"{_customStartDate:yyyy年M月d日} - {_customEndDate:yyyy年M月d日} (共 {_customDays} 天)";
                }
            }
            else
            {
                TopDateHeaderTitle.Text = $"{_currentDate:yyyy年M月} · 日程清单";
            }
        }

        // 2. 渲染左侧迷你月历选择器
        RenderMiniMonthCalendar();

        // 4. 切换显示容器
        DayViewContainer.Visibility = _currentView == "Day" ? Visibility.Visible : Visibility.Collapsed;
        WeekViewContainer.Visibility = _currentView == "Week" ? Visibility.Visible : Visibility.Collapsed;
        MonthViewContainer.Visibility = _currentView == "Month" ? Visibility.Visible : Visibility.Collapsed;
        YearViewContainer.Visibility = _currentView == "Year" ? Visibility.Visible : Visibility.Collapsed;
        CustomViewContainer.Visibility = _currentView == "Custom" ? Visibility.Visible : Visibility.Collapsed;
        AgendaViewContainer.Visibility = _currentView == "Agenda" ? Visibility.Visible : Visibility.Collapsed;

        // 5. 渲染对应的视图内容
        switch (_currentView)
        {
            case "Day":
                RenderDayHeader();
                RenderDayEvents();
                break;
            case "Week":
                RenderWeekHeader();
                RenderWeekEvents();
                break;
            case "Month":
                RenderMonthGrid();
                break;
            case "Year":
                RenderYearGrid();
                break;
            case "Custom":
                SyncCustomControlsUI();
                if (_isCustomAgendaMode)
                {
                    RenderCustomAgenda();
                }
                else
                {
                    RenderCustomHeader();
                    RenderCustomEvents();
                }
                break;
            case "Agenda":
                RenderAgendaList();
                break;
        }

        // 6. 更新顶部视图选择器高亮态
        UpdateViewButtonsStyle();

        // 7. 更新分类筛选标签
        RenderMyCalendarsList();

    }

    private void UpdateViewButtonsStyle()
    {
        var activeBg = new SolidColorBrush(Color.FromRgb(0x1A, 0x73, 0xE8));
        var normalBg = Brushes.Transparent;
        var activeFg = Brushes.White;
        var normalFg = ThemeService.CurrentTextBrush;

        ViewDayBtn.Background = _currentView == "Day" ? activeBg : normalBg;
        ViewDayBtn.Foreground = _currentView == "Day" ? activeFg : normalFg;
        ViewDayBtn.FontWeight = _currentView == "Day" ? FontWeights.SemiBold : FontWeights.Normal;

        ViewWeekBtn.Background = _currentView == "Week" ? activeBg : normalBg;
        ViewWeekBtn.Foreground = _currentView == "Week" ? activeFg : normalFg;
        ViewWeekBtn.FontWeight = _currentView == "Week" ? FontWeights.SemiBold : FontWeights.Normal;

        ViewMonthBtn.Background = _currentView == "Month" ? activeBg : normalBg;
        ViewMonthBtn.Foreground = _currentView == "Month" ? activeFg : normalFg;
        ViewMonthBtn.FontWeight = _currentView == "Month" ? FontWeights.SemiBold : FontWeights.Normal;

        ViewYearBtn.Background = _currentView == "Year" ? activeBg : normalBg;
        ViewYearBtn.Foreground = _currentView == "Year" ? activeFg : normalFg;
        ViewYearBtn.FontWeight = _currentView == "Year" ? FontWeights.SemiBold : FontWeights.Normal;

        ViewCustomBtn.Background = _currentView == "Custom" ? activeBg : normalBg;
        ViewCustomBtn.Foreground = _currentView == "Custom" ? activeFg : normalFg;
        ViewCustomBtn.FontWeight = _currentView == "Custom" ? FontWeights.SemiBold : FontWeights.Normal;
    }

    // =========================================================================
    // ===================== 1. MINI MONTH CALENDAR ============================
    // =========================================================================

    private void RenderMiniMonthCalendar()
    {
        MiniCalMonthTitle.Text = _miniCalMonth.ToString("yyyy年M月");
        MiniCalGrid.Children.Clear();

        DateTime firstOfMonth = new DateTime(_miniCalMonth.Year, _miniCalMonth.Month, 1);
        int offset = ((int)firstOfMonth.DayOfWeek == 0) ? 6 : ((int)firstOfMonth.DayOfWeek - 1);
        DateTime startDate = firstOfMonth.AddDays(-offset);

        for (int i = 0; i < 42; i++)
        {
            DateTime date = startDate.AddDays(i);
            bool isCurrentMonth = date.Month == _miniCalMonth.Month;
            bool isSelected = date.Date == _currentDate.Date;
            bool isToday = date.Date == DateTime.Today;

            var border = new Border
            {
                Width = 26,
                Height = 26,
                CornerRadius = new CornerRadius(13),
                Cursor = Cursors.Hand,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center,
                Background = isSelected
                    ? ThemeService.CurrentAccentBrush
                    : (isToday ? new SolidColorBrush(Color.FromArgb(50, 0x1A, 0x73, 0xE8)) : Brushes.Transparent)
            };

            var tb = new TextBlock
            {
                Text = date.Day.ToString(),
                FontSize = 11,
                FontWeight = (isSelected || isToday) ? FontWeights.Bold : FontWeights.Normal,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center,
                Foreground = isSelected
                    ? (ThemeService.IsCurrentDark ? new SolidColorBrush(Color.FromRgb(0x20, 0x21, 0x24)) : Brushes.White)
                    : (isToday
                        ? ThemeService.CurrentAccentBrush
                        : (isCurrentMonth ? ThemeService.CurrentTextBrush : ThemeService.CurrentMutedTextBrush))
            };

            border.Child = tb;
            DateTime capturedDate = date;
            border.MouseLeftButtonUp += (s, e) =>
            {
                _currentDate = capturedDate;
                _miniCalMonth = capturedDate;
                RenderAllCalendarViews();
            };

            MiniCalGrid.Children.Add(border);
        }
    }

    private void OnMiniCalPrevClicked(object sender, RoutedEventArgs e)
    {
        _miniCalMonth = _miniCalMonth.AddMonths(-1);
        RenderMiniMonthCalendar();
    }

    private void OnMiniCalNextClicked(object sender, RoutedEventArgs e)
    {
        _miniCalMonth = _miniCalMonth.AddMonths(1);
        RenderMiniMonthCalendar();
    }

    // =========================================================================
    // ===================== 2. WEEK VIEW (周视图 7 COLUMNS) ===================
    // =========================================================================

    private static DateTime GetMondayOfWeek(DateTime time)
    {
        int diff = (7 + (time.DayOfWeek - DayOfWeek.Monday)) % 7;
        return time.Date.AddDays(-1 * diff);
    }

    private void BuildWeekHourGrid()
    {
        WeekHourLabelsPanel.Children.Clear();
        WeekSlotsGrid.Children.Clear();

        for (int h = 0; h < 24; h++)
        {
            var labelBorder = new Border
            {
                Height = HourHeight,
                VerticalAlignment = VerticalAlignment.Top
            };
            labelBorder.Child = new TextBlock
            {
                Text = h == 0 ? "" : $"{h:D2}:00",
                FontSize = 10,
                Foreground = ThemeService.CurrentSecondaryTextBrush,
                HorizontalAlignment = HorizontalAlignment.Right,
                Margin = new Thickness(0, -6, 6, 0)
            };
            WeekHourLabelsPanel.Children.Add(labelBorder);
        }

        for (int col = 0; col < 7; col++)
        {
            var colPanel = new StackPanel();
            for (int h = 0; h < 24; h++)
            {
                int colCaptured = col;
                int hCaptured = h;

                var slot = new Border
                {
                    Height = HourHeight,
                    BorderBrush = ThemeService.CurrentGridLineBrush,
                    BorderThickness = new Thickness(0, 0, 1, 1),
                    Background = Brushes.Transparent,
                    Cursor = Cursors.Hand,
                    ToolTip = $"点击或拖选创建日程"
                };

                slot.MouseLeftButtonUp += (s, e) =>
                {
                    if (_isDragSelecting) return;
                    DateTime monday = GetMondayOfWeek(_currentDate);
                    DateTime clickedDate = monday.AddDays(colCaptured);
                    OpenEventCreateModal(clickedDate, hCaptured);
                };

                colPanel.Children.Add(slot);
            }
            WeekSlotsGrid.Children.Add(colPanel);
        }

        WeekEventsCanvas.Height = 24 * HourHeight;
    }

    private void RenderWeekHeader()
    {
        WeekHeaderGrid.Children.Clear();
        DateTime monday = GetMondayOfWeek(_currentDate);
        string[] weekNames = { "周一", "周二", "周三", "周四", "周五", "周六", "周日" };

        int todayDeepWorkMins = DatabaseService.GetTodayDeepWorkMinutes(DateTime.Today);
        bool isCircuitBreaker = todayDeepWorkMins > 270;

        for (int i = 0; i < 7; i++)
        {
            DateTime dayDate = monday.AddDays(i);
            bool isToday = dayDate.Date == DateTime.Today;

            var dayHeaderPanel = new StackPanel
            {
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };

            // 周几标签
            var weekNameText = new TextBlock
            {
                Text = weekNames[i],
                FontSize = 11,
                FontWeight = isToday ? FontWeights.SemiBold : FontWeights.Normal,
                Foreground = isToday ? ThemeService.CurrentAccentBrush : ThemeService.CurrentSecondaryTextBrush,
                HorizontalAlignment = HorizontalAlignment.Center,
                Margin = new Thickness(0, 0, 0, 4)
            };
            dayHeaderPanel.Children.Add(weekNameText);

            // 日期数字圆圈 (Today 为经典 Google 蓝实心圆，若超限熔断则显示醒目红边)
            var dateBadge = new Border
            {
                Width = 36,
                Height = 36,
                CornerRadius = new CornerRadius(18),
                Background = isToday ? new SolidColorBrush(Color.FromRgb(0x1A, 0x73, 0xE8)) : Brushes.Transparent,
                BorderBrush = (isToday && isCircuitBreaker) ? new SolidColorBrush(Color.FromRgb(0xEF, 0x44, 0x44)) : Brushes.Transparent,
                BorderThickness = (isToday && isCircuitBreaker) ? new Thickness(2) : new Thickness(0),
                HorizontalAlignment = HorizontalAlignment.Center,
                Cursor = Cursors.Hand,
                ToolTip = (isToday && isCircuitBreaker) ? "🚨 今日认知负荷已过载，已触发排期熔断！" : null
            };

            var dateNumText = new TextBlock
            {
                Text = dayDate.Day.ToString(),
                FontSize = 16,
                FontWeight = isToday ? FontWeights.Bold : FontWeights.Normal,
                Foreground = isToday ? Brushes.White : ThemeService.CurrentTextBrush,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            dateBadge.Child = dateNumText;

            DateTime captured = dayDate;
            dateBadge.MouseLeftButtonUp += (s, e) =>
            {
                _currentDate = captured;
                _currentView = "Day";
                RenderAllCalendarViews();
            };

            dayHeaderPanel.Children.Add(dateBadge);
            WeekHeaderGrid.Children.Add(dayHeaderPanel);
        }
    }

    private void RenderWeekEvents()
    {
        WeekEventsCanvas.Children.Clear();
        WeekEventsCanvas.Height = 24 * HourHeight;

        double totalWidth = WeekEventsCanvas.ActualWidth;
        if (totalWidth <= 0 && WeekScrollViewer.ActualWidth > 80)
        {
            totalWidth = WeekScrollViewer.ActualWidth - 60;
        }
        if (totalWidth <= 100) totalWidth = 700;

        double colWidth = totalWidth / 7.0;
        DateTime monday = GetMondayOfWeek(_currentDate);
        DateTime nextMonday = monday.AddDays(7);

        // 1. Google 经典红线指示器 (如果当前周包含今天)
        ScheduleItem? activeIntersectingTask = null;

        if (DateTime.Today >= monday && DateTime.Today < nextMonday)
        {
            int todayCol = (int)(DateTime.Today - monday).TotalDays;
            double nowH = DateTime.Now.Hour + DateTime.Now.Minute / 60.0;
            double lineTop = nowH * HourHeight;

            // 红点
            var dot = new Ellipse
            {
                Width = 10,
                Height = 10,
                Fill = new SolidColorBrush(Color.FromRgb(0xEA, 0x43, 0x35)),
                IsHitTestVisible = false
            };
            Canvas.SetLeft(dot, todayCol * colWidth - 5);
            Canvas.SetTop(dot, lineTop - 4.5);
            WeekEventsCanvas.Children.Add(dot);

            // 红线
            var line = new Border
            {
                Height = 2,
                Background = new SolidColorBrush(Color.FromRgb(0xEA, 0x43, 0x35)),
                Width = colWidth,
                IsHitTestVisible = false
            };
            Canvas.SetLeft(line, todayCol * colWidth);
            Canvas.SetTop(line, lineTop - 1);
            WeekEventsCanvas.Children.Add(line);
        }

        // 2. 查询周事件并过滤
        var rawEvents = DatabaseService.GetSchedulesForDateRange(monday, nextMonday);
        var filteredEvents = rawEvents.Where(MatchesFilter).ToList();

        // 检索切过当前时间红线的活跃任务
        activeIntersectingTask = filteredEvents.FirstOrDefault(e => !e.IsAllDay && e.StartTime <= DateTime.Now && e.EndTime >= DateTime.Now && e.Status != "COMPLETED");

        // 2.1 全天日程 (All-Day Events) 分离渲染
        var allDayEvents = filteredEvents.Where(e => e.IsAllDay).ToList();
        var timedEvents = filteredEvents.Where(e => !e.IsAllDay).ToList();

        WeekAllDayGrid.Children.Clear();
        if (allDayEvents.Count > 0)
        {
            WeekAllDayBorder.Visibility = Visibility.Visible;
            for (int d = 0; d < 7; d++)
            {
                DateTime dayDate = monday.AddDays(d).Date;
                var dayAllDay = allDayEvents.Where(e => e.StartTime.Date <= dayDate && e.EndTime.Date >= dayDate).ToList();
                var dayStack = new StackPanel { Margin = new Thickness(2, 2, 2, 2) };
                foreach (var ev in dayAllDay)
                {
                    dayStack.Children.Add(CreateAllDayEventChip(ev));
                }
                WeekAllDayGrid.Children.Add(dayStack);
            }
        }
        else
        {
            WeekAllDayBorder.Visibility = Visibility.Collapsed;
        }

        // 2.1.5 课表动态投影 (Timetable -> Overview 背景参考块)
        for (int d = 0; d < 7; d++)
        {
            DateTime dayDate = monday.AddDays(d).Date;
            var projectedCourses = TimetableProjectionService.GetEffectiveCoursesForDate(dayDate);
            double dayLeft = d * colWidth;
            double availableDayWidth = Math.Max(20.0, colWidth - 4.0);

            foreach (var course in projectedCourses)
            {
                double startH = course.StartTime.Hour + course.StartTime.Minute / 60.0;
                double endH = course.EndTime.Hour + course.EndTime.Minute / 60.0;
                if (endH <= startH) endH = startH + 0.75;

                double top = startH * HourHeight + 1;
                double height = Math.Max(22.0, (endH - startH) * HourHeight - 2);

                Color courseCol = Color.FromRgb(0x3B, 0x82, 0xF6);
                try
                {
                    if (!string.IsNullOrEmpty(course.ColorHex))
                        courseCol = (Color)ColorConverter.ConvertFromString(course.ColorHex);
                }
                catch { }

                var projCard = new Border
                {
                    Width = availableDayWidth,
                    Height = height,
                    CornerRadius = new CornerRadius(6),
                    BorderThickness = new Thickness(1.5),
                    BorderBrush = new SolidColorBrush(Color.FromArgb(0x99, courseCol.R, courseCol.G, courseCol.B)),
                    Background = new SolidColorBrush(Color.FromArgb(0x1C, courseCol.R, courseCol.G, courseCol.B)),
                    Padding = new Thickness(6, 4, 6, 4),
                    IsHitTestVisible = true,
                    Cursor = Cursors.Hand,
                    ToolTip = $"[课表投影] {course.Name} @ {course.Location}\n作息时间: {course.StartTime:HH:mm} - {course.EndTime:HH:mm} (教师: {course.Teacher})"
                };

                var st = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
                var titleTb = new TextBlock
                {
                    Text = $"📚 {course.Name}",
                    FontSize = 10.5,
                    FontWeight = FontWeights.SemiBold,
                    Foreground = new SolidColorBrush(Color.FromRgb(0x93, 0xC5, 0xFD)),
                    TextTrimming = TextTrimming.CharacterEllipsis
                };
                var locTb = new TextBlock
                {
                    Text = $"{course.StartTime:HH:mm}-{course.EndTime:HH:mm} {course.Location}",
                    FontSize = 9.5,
                    Foreground = new SolidColorBrush(Color.FromRgb(0x94, 0xA3, 0xB8)),
                    TextTrimming = TextTrimming.CharacterEllipsis
                };
                st.Children.Add(titleTb);
                if (height > 32) st.Children.Add(locTb);
                projCard.Child = st;

                Canvas.SetLeft(projCard, dayLeft + 2);
                Canvas.SetTop(projCard, top);
                Canvas.SetZIndex(projCard, 1);
                WeekEventsCanvas.Children.Add(projCard);
            }
        }

        // 2.2 定时时间块渲染 (按天分组 + 并发时间重叠自动分列算法)
        for (int d = 0; d < 7; d++)
        {
            DateTime dayDate = monday.AddDays(d).Date;
            var dayEvents = timedEvents.Where(e => e.StartTime.Date == dayDate).OrderBy(e => e.StartTime).ThenByDescending(e => e.EndTime).ToList();
            if (dayEvents.Count == 0) continue;

            var clusters = ClusterOverlappingEvents(dayEvents);
            double dayLeft = d * colWidth;
            double availableDayWidth = Math.Max(20.0, colWidth - 4.0);

            foreach (var cluster in clusters)
            {
                var colAssignments = AssignEventColumns(cluster);
                int totalCols = colAssignments.Values.Max() + 1;
                double subColWidth = availableDayWidth / totalCols;

                foreach (var item in cluster)
                {
                    int subCol = colAssignments[item];

                    double startH = item.StartTime.Hour + item.StartTime.Minute / 60.0;
                    double endH = item.EndTime.Hour + item.EndTime.Minute / 60.0;
                    if (item.EndTime.Date > item.StartTime.Date) endH = 24.0;
                    if (endH <= startH) endH = startH + 0.5;

                    double top = startH * HourHeight + 1;
                    double height = Math.Max(20.0, (endH - startH) * HourHeight - 2);
                    double left = dayLeft + 2 + subCol * subColWidth;
                    double width = Math.Max(18.0, subColWidth - 2);

                    var card = CreateWeekEventChip(item, width, height);
                    Canvas.SetLeft(card, left);
                    Canvas.SetTop(card, top);
                    WeekEventsCanvas.Children.Add(card);
                }
            }
        }

        // 3. Module 4: 时间线实时联动打卡悬浮条 (如果当前红线切过正在进行的任务)
        if (activeIntersectingTask != null && DateTime.Today >= monday && DateTime.Today < nextMonday)
        {
            int todayCol = (int)(DateTime.Today - monday).TotalDays;
            double nowH = DateTime.Now.Hour + DateTime.Now.Minute / 60.0;
            double lineTop = nowH * HourHeight;

            var punchInBanner = CreateRealtimePunchInBanner(activeIntersectingTask, colWidth);
            Canvas.SetLeft(punchInBanner, Math.Max(0, todayCol * colWidth));
            Canvas.SetTop(punchInBanner, Math.Max(2, lineTop - 36));
            Canvas.SetZIndex(punchInBanner, 100);
            WeekEventsCanvas.Children.Add(punchInBanner);
        }
    }

    /// <summary>
    /// 全天日程芯片 (All-Day Event Chip)
    /// </summary>
    private FrameworkElement CreateAllDayEventChip(ScheduleItem item)
    {
        Brush bgBrush = GetEventBrush(item, 220);
        string srcTag = !string.IsNullOrEmpty(item.Source) ? $" [{item.Source}]" : "";
        var border = new Border
        {
            Height = 22,
            Background = bgBrush,
            CornerRadius = new CornerRadius(3),
            Padding = new Thickness(6, 2, 6, 2),
            Margin = new Thickness(0, 1, 0, 1),
            Cursor = Cursors.Hand,
            ToolTip = $"全天: {item.Title}\n来源: {(string.IsNullOrEmpty(item.Source) ? "未注明" : item.Source)}\n点击查看详情",
            ClipToBounds = true
        };

        var tb = new TextBlock
        {
            Text = (item.Status == "COMPLETED" ? "✓ " : "") + item.Title + srcTag,
            FontSize = 10.5,
            FontWeight = FontWeights.SemiBold,
            Foreground = Brushes.White,
            TextTrimming = TextTrimming.CharacterEllipsis,
            VerticalAlignment = VerticalAlignment.Center
        };
        border.Child = tb;
        border.MouseLeftButtonUp += (s, e) =>
        {
            e.Handled = true;
            OpenEventQuickDetail(item);
        };
        return border;
    }

    /// <summary>
    /// 解析日程颜色画刷 (优先采用 ColorHex，其次按三级认知负荷分配)
    /// </summary>
    private static Brush GetEventBrush(ScheduleItem item, byte opacity = 230)
    {
        if (!string.IsNullOrEmpty(item.ColorHex))
        {
            try
            {
                var color = (Color)ColorConverter.ConvertFromString(item.ColorHex);
                color.A = opacity;
                return new SolidColorBrush(color);
            }
            catch { }
        }

        return item.WorkType switch
        {
            "REST_BUFFER" => new SolidColorBrush(Color.FromArgb(180, 0x06, 0x4E, 0x3B)),
            "SHALLOW_WORK" => new SolidColorBrush(Color.FromArgb(200, 0x1E, 0x29, 0x3B)),
            _ => new SolidColorBrush(Color.FromArgb(230, 0x2E, 0x10, 0x65))
        };
    }

    /// <summary>
    /// 重叠事件聚类算法 (Interval Partitioning Clustering)
    /// </summary>
    private static List<List<ScheduleItem>> ClusterOverlappingEvents(List<ScheduleItem> sortedEvents)
    {
        var clusters = new List<List<ScheduleItem>>();
        if (sortedEvents.Count == 0) return clusters;

        var currentCluster = new List<ScheduleItem> { sortedEvents[0] };
        DateTime currentClusterEnd = sortedEvents[0].EndTime;

        for (int i = 1; i < sortedEvents.Count; i++)
        {
            var ev = sortedEvents[i];
            if (ev.StartTime < currentClusterEnd)
            {
                currentCluster.Add(ev);
                if (ev.EndTime > currentClusterEnd)
                    currentClusterEnd = ev.EndTime;
            }
            else
            {
                clusters.Add(currentCluster);
                currentCluster = new List<ScheduleItem> { ev };
                currentClusterEnd = ev.EndTime;
            }
        }
        clusters.Add(currentCluster);
        return clusters;
    }

    /// <summary>
    /// 贪心列分配算法 (Greedy Slot Coloring)
    /// </summary>
    private static Dictionary<ScheduleItem, int> AssignEventColumns(List<ScheduleItem> cluster)
    {
        var assignments = new Dictionary<ScheduleItem, int>();
        var columnEndTimes = new List<DateTime>();

        foreach (var ev in cluster)
        {
            int placedCol = -1;
            for (int c = 0; c < columnEndTimes.Count; c++)
            {
                if (columnEndTimes[c] <= ev.StartTime)
                {
                    placedCol = c;
                    columnEndTimes[c] = ev.EndTime;
                    break;
                }
            }

            if (placedCol == -1)
            {
                placedCol = columnEndTimes.Count;
                columnEndTimes.Add(ev.EndTime);
            }

            assignments[ev] = placedCol;
        }

        return assignments;
    }

    /// <summary>
    /// Module 4: 实时时间线打卡交互悬浮条
    /// </summary>
    private FrameworkElement CreateRealtimePunchInBanner(ScheduleItem task, double colWidth)
    {
        var border = new Border
        {
            Background = new SolidColorBrush(Color.FromArgb(240, 0x1E, 0x1B, 0x4B)),
            BorderBrush = new SolidColorBrush(Color.FromRgb(0x8B, 0x5C, 0xF6)),
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(14),
            Padding = new Thickness(8, 3, 8, 3),
            Cursor = Cursors.Arrow,
            MaxWidth = Math.Max(180, colWidth),
            ClipToBounds = true,
            Effect = new System.Windows.Media.Effects.DropShadowEffect
            {
                BlurRadius = 10,
                ShadowDepth = 2,
                Opacity = 0.6,
                Color = Colors.Black
            }
        };

        var sp = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };

        // 🔴 呼吸指示点
        var dot = new Ellipse
        {
            Width = 8,
            Height = 8,
            Fill = new SolidColorBrush(Color.FromRgb(0xEA, 0x43, 0x35)),
            Margin = new Thickness(0, 0, 6, 0)
        };
        sp.Children.Add(dot);

        // 已专注时间
        int elapsedMinutes = Math.Max(1, (int)(DateTime.Now - task.StartTime).TotalMinutes);
        var titleText = new TextBlock
        {
            Text = $"进行中: {task.Title} ({elapsedMinutes}m)",
            FontSize = 10.5,
            FontWeight = FontWeights.Bold,
            Foreground = Brushes.White,
            TextTrimming = TextTrimming.CharacterEllipsis,
            MaxWidth = 100,
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(0, 0, 6, 0)
        };
        sp.Children.Add(titleText);

        // 1. [✓ 提前完成]
        var finishBtn = new Button
        {
            Content = "✓ 完成",
            Background = new SolidColorBrush(Color.FromRgb(0x05, 0x96, 0x69)),
            Foreground = Brushes.White,
            BorderThickness = new Thickness(0),
            Padding = new Thickness(5, 2, 5, 2),
            FontSize = 10,
            FontWeight = FontWeights.SemiBold,
            Cursor = Cursors.Hand,
            Margin = new Thickness(0, 0, 4, 0)
        };
        finishBtn.Click += (s, e) =>
        {
            e.Handled = true;
            task.ActualMinutes = elapsedMinutes;
            task.Status = "COMPLETED";
            task.EndTime = DateTime.Now;
            DatabaseService.UpsertSchedule(task);
            DataLineageService.OnScheduleCompleted(task);
            RenderAllCalendarViews();
            RefreshTimePnlData();
            UpdateCognitiveLoadQuota();
        };
        sp.Children.Add(finishBtn);

        // 2. [+15m 延误缓冲]
        var delayBtn = new Button
        {
            Content = "+15m",
            Background = new SolidColorBrush(Color.FromRgb(0xD9, 0x77, 0x06)),
            Foreground = Brushes.White,
            BorderThickness = new Thickness(0),
            Padding = new Thickness(5, 2, 5, 2),
            FontSize = 10,
            FontWeight = FontWeights.SemiBold,
            Cursor = Cursors.Hand,
            Margin = new Thickness(0, 0, 4, 0)
        };
        delayBtn.Click += (s, e) =>
        {
            e.Handled = true;
            task.EndTime = task.EndTime.AddMinutes(15);
            task.ActualMinutes += 15;
            DatabaseService.UpsertSchedule(task);
            RenderAllCalendarViews();
            RefreshTimePnlData();
        };
        sp.Children.Add(delayBtn);

        border.Child = sp;
        return border;
    }

    /// <summary>
    /// Module 4: 三级认知负荷视觉层级、防文字重叠与底部拖拽调整时长
    /// </summary>
    private UIElement CreateWeekEventChip(ScheduleItem item, double width, double height)
    {
        // 1. 颜色与画刷
        Brush bgBrush = GetEventBrush(item, 230);
        Brush borderBrush;
        string workTypeBadge = "";

        if (item.IsTentative)
        {
            borderBrush = new SolidColorBrush(Color.FromRgb(0x94, 0xA3, 0xB8));
            workTypeBadge = "👻 ";
        }
        else if (item.WorkType == "REST_BUFFER")
        {
            borderBrush = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
            workTypeBadge = "☕ ";
        }
        else if (item.WorkType == "SHALLOW_WORK")
        {
            borderBrush = new SolidColorBrush(Color.FromRgb(0x64, 0x74, 0x8B));
            workTypeBadge = "⚡ ";
        }
        else
        {
            borderBrush = new SolidColorBrush(Color.FromRgb(0x8B, 0x5C, 0xF6));
            workTypeBadge = "🎯 ";
        }

        if (item.IsLocked)
        {
            workTypeBadge = "🔒 " + workTypeBadge;
        }

        if (!string.IsNullOrEmpty(item.ColorHex))
        {
            try
            {
                var c = (Color)ColorConverter.ConvertFromString(item.ColorHex);
                borderBrush = new SolidColorBrush(c);
            }
            catch { }
        }

        // 2. 检查孤立任务
        bool isIsolated = string.IsNullOrEmpty(item.GoalId) && item.WorkType != "REST_BUFFER";

        double cardOpacity = item.Status == "COMPLETED" ? 0.65 : (item.IsTentative ? 0.70 : 1.0);
        Brush cardBg = item.IsTentative ? new SolidColorBrush(Color.FromArgb(160, 0x47, 0x55, 0x69)) : bgBrush;
        Brush cardBorder = item.IsTentative 
            ? new SolidColorBrush(Color.FromRgb(0x94, 0xA3, 0xB8)) 
            : (isIsolated ? new SolidColorBrush(Color.FromRgb(0xF5, 0x9E, 0x0B)) : (item.Status == "IN_PROGRESS" ? new SolidColorBrush(Color.FromRgb(0x38, 0xBD, 0xF8)) : borderBrush));

        var border = new Border
        {
            Width = width,
            Height = height,
            Background = cardBg,
            BorderBrush = cardBorder,
            BorderThickness = new Thickness(isIsolated || item.IsTentative ? 1.5 : 1),
            CornerRadius = new CornerRadius(4),
            Padding = new Thickness(4, 2, 4, 2),
            Cursor = Cursors.Hand,
            Opacity = cardOpacity,
            ClipToBounds = true,
            ToolTip = $"{(item.IsTentative ? "👻 [待确认占位] " : "")}{(item.IsLocked ? "🔒 [坐标已锁定] " : "")}{workTypeBadge}{item.Title}\n{item.StartTime:HH:mm} - {item.EndTime:HH:mm}\n来源: {(string.IsNullOrEmpty(item.Source) ? "未注明" : item.Source)}\n类型: {item.WorkType}\nDoD: {(string.IsNullOrEmpty(item.Dod) ? "未填写" : item.Dod)}\n{(isIsolated ? "⚠️ 未绑定目标\n" : "")}点击查看详情{(item.IsLocked ? " (已锁定)" : "，拖动底部边缘调整时长")}"
        };

        var rootGrid = new Grid { ClipToBounds = true };

        // 内容排版布局 (Grid 规范严格约束宽度，彻底杜绝 WPF 横向 StackPanel 文字溢出碰撞)
        string statusPrefix = item.Status == "COMPLETED" ? "✓ " : "";
        string lockPrefix = item.IsLocked ? "🔒 " : "";
        string tentativePrefix = item.IsTentative ? "👻 " : "";
        if (height < 34.0)
        {
            // 超短时间块 (<30分钟)：单行极简内联展示
            var singleLineTb = new TextBlock
            {
                Text = lockPrefix + tentativePrefix + statusPrefix + $"{item.StartTime:HH:mm} {item.Title}" + (!string.IsNullOrEmpty(item.Source) ? $" [{item.Source}]" : ""),
                FontSize = 10,
                FontWeight = FontWeights.SemiBold,
                Foreground = Brushes.White,
                TextTrimming = TextTrimming.CharacterEllipsis,
                VerticalAlignment = VerticalAlignment.Center,
                TextDecorations = item.Status == "COMPLETED" ? TextDecorations.Strikethrough : null
            };
            rootGrid.Children.Add(singleLineTb);
        }
        else
        {
            // 标准与较长时间块：多行清晰排版
            var contentPanel = new StackPanel { VerticalAlignment = VerticalAlignment.Top, ClipToBounds = true };

            var titleTb = new TextBlock
            {
                Text = lockPrefix + tentativePrefix + statusPrefix + item.Title,
                FontSize = 10.5,
                FontWeight = FontWeights.SemiBold,
                Foreground = Brushes.White,
                TextTrimming = TextTrimming.CharacterEllipsis,
                TextDecorations = item.Status == "COMPLETED" ? TextDecorations.Strikethrough : null,
                Margin = new Thickness(0, 0, 0, 1)
            };
            contentPanel.Children.Add(titleTb);

            var timeTb = new TextBlock
            {
                Text = $"{item.StartTime:HH:mm} - {item.EndTime:HH:mm}" + (!string.IsNullOrEmpty(item.Source) ? $" · {item.Source}" : ""),
                FontSize = 9.0,
                Foreground = new SolidColorBrush(Color.FromArgb(210, 0xFF, 0xFF, 0xFF)),
                TextTrimming = TextTrimming.CharacterEllipsis
            };
            contentPanel.Children.Add(timeTb);

            if (height >= 50.0 && (!string.IsNullOrEmpty(item.Dod) || !string.IsNullOrEmpty(item.GoalId)))
            {
                string extraText = !string.IsNullOrEmpty(item.Dod) ? $"✓ {item.Dod}" : $"🎯 OKR关联";
                var extraTb = new TextBlock
                {
                    Text = extraText,
                    FontSize = 8.5,
                    Foreground = new SolidColorBrush(Color.FromArgb(170, 0xDF, 0xE5, 0xEF)),
                    TextTrimming = TextTrimming.CharacterEllipsis,
                    Margin = new Thickness(0, 1, 0, 0)
                };
                contentPanel.Children.Add(extraTb);
            }

            rootGrid.Children.Add(contentPanel);
        }

        // 底部 6px 拖拽手柄：调整日程时长 (Google Calendar 经典交互)
        var resizeThumb = new Border
        {
            Height = 6,
            VerticalAlignment = VerticalAlignment.Bottom,
            Background = Brushes.Transparent,
            Cursor = Cursors.SizeNS
        };
        resizeThumb.MouseLeftButtonDown += (s, e) =>
        {
            e.Handled = true;
            if (item.IsLocked)
            {
                MessageBox.Show("该日程时间坐标已锁定，若需调整时长请先在详情卡片中解锁。", "日程已锁定", MessageBoxButton.OK, MessageBoxImage.Information);
                return;
            }
            _isResizingEvent = true;
            _resizingItem = item;
            _resizeStartY = e.GetPosition(WeekEventsCanvas).Y;
            _resizeInitialEndTime = item.EndTime;
            WeekEventsCanvas.CaptureMouse();
        };
        rootGrid.Children.Add(resizeThumb);

        border.Child = rootGrid;

        // 单击卡片弹出 Google Calendar 轻量快速详情卡片
        border.MouseLeftButtonUp += (s, e) =>
        {
            if (_isResizingEvent) return;
            e.Handled = true;
            OpenEventQuickDetail(item);
        };

        return border;
    }

    private void ScrollWeekToCurrentTime()
    {
        try
        {
            double nowH = DateTime.Now.Hour + DateTime.Now.Minute / 60.0;
            double targetOffset = Math.Max(0, (nowH - 3.0) * HourHeight);
            WeekScrollViewer.ScrollToVerticalOffset(targetOffset);
            DayScrollViewer.ScrollToVerticalOffset(targetOffset);
        }
        catch { }
    }

    // =========================================================================
    // ===================== 3. DAY VIEW (天视图 1 COLUMN) =====================
    // =========================================================================

    private void BuildDayHourGrid()
    {
        DayHourLabelsPanel.Children.Clear();
        DayHourSlotsPanel.Children.Clear();

        for (int h = 0; h < 24; h++)
        {
            var labelBorder = new Border { Height = HourHeight, VerticalAlignment = VerticalAlignment.Top };
            labelBorder.Child = new TextBlock
            {
                Text = h == 0 ? "" : $"{h:D2}:00",
                FontSize = 10,
                Foreground = ThemeService.CurrentSecondaryTextBrush,
                HorizontalAlignment = HorizontalAlignment.Right,
                Margin = new Thickness(0, -6, 6, 0)
            };
            DayHourLabelsPanel.Children.Add(labelBorder);

            int hCaptured = h;
            var slot = new Border
            {
                Height = HourHeight,
                BorderBrush = ThemeService.CurrentGridLineBrush,
                BorderThickness = new Thickness(0, 0, 0, 1),
                Background = Brushes.Transparent,
                Cursor = Cursors.Hand,
                ToolTip = "点击或拖选创建日程"
            };
            slot.MouseLeftButtonUp += (s, e) =>
            {
                if (_isDragSelecting) return;
                OpenEventCreateModal(_currentDate, hCaptured);
            };
            DayHourSlotsPanel.Children.Add(slot);
        }

        DayEventsCanvas.Height = 24 * HourHeight;
    }

    private void RenderDayHeader()
    {
        var culture = new CultureInfo("zh-CN");
        DayHeaderDateNumText.Text = _currentDate.Day.ToString();
        DayHeaderWeekdayText.Text = _currentDate.ToString("dddd", culture);
        DayHeaderFullDateText.Text = _currentDate.ToString("yyyy年M月d日", culture);

        bool isToday = _currentDate.Date == DateTime.Today;
        DayHeaderDateBadge.Background = isToday ? new SolidColorBrush(Color.FromRgb(0x1A, 0x73, 0xE8)) : Brushes.Transparent;
        DayHeaderDateNumText.Foreground = isToday ? Brushes.White : ThemeService.CurrentTextBrush;
    }

    private void RenderDayEvents()
    {
        DayEventsCanvas.Children.Clear();
        DayEventsCanvas.Height = 24 * HourHeight;

        double canvasWidth = DayEventsCanvas.ActualWidth;
        if (canvasWidth <= 0 && DayScrollViewer.ActualWidth > 80)
        {
            canvasWidth = DayScrollViewer.ActualWidth - 60;
        }
        if (canvasWidth <= 100) canvasWidth = 600;

        if (_currentDate.Date == DateTime.Today)
        {
            double nowH = DateTime.Now.Hour + DateTime.Now.Minute / 60.0;
            double lineTop = nowH * HourHeight;

            var dot = new Ellipse
            {
                Width = 10,
                Height = 10,
                Fill = new SolidColorBrush(Color.FromRgb(0xEA, 0x43, 0x35)),
                IsHitTestVisible = false
            };
            Canvas.SetLeft(dot, -5);
            Canvas.SetTop(dot, lineTop - 4.5);
            DayEventsCanvas.Children.Add(dot);

            var line = new Border
            {
                Height = 2,
                Background = new SolidColorBrush(Color.FromRgb(0xEA, 0x43, 0x35)),
                Width = canvasWidth,
                IsHitTestVisible = false
            };
            Canvas.SetLeft(line, 0);
            Canvas.SetTop(line, lineTop - 1);
            DayEventsCanvas.Children.Add(line);
        }

        var rawEvents = DatabaseService.GetSchedulesForDate(_currentDate);
        var filteredEvents = rawEvents.Where(MatchesFilter).ToList();

        var timedEvents = filteredEvents.Where(e => !e.IsAllDay).OrderBy(e => e.StartTime).ThenByDescending(e => e.EndTime).ToList();
        var allDayEvents = filteredEvents.Where(e => e.IsAllDay).ToList();

        // 渲染全天日程吸顶栏 (在日视图独立行，不占用 24 小时画布空间)
        DayAllDayStackPanel.Children.Clear();
        if (allDayEvents.Count > 0)
        {
            DayAllDayBorder.Visibility = Visibility.Visible;
            foreach (var allDay in allDayEvents)
            {
                var chip = CreateAllDayEventChip(allDay);
                chip.Margin = new Thickness(0, 2, 0, 2);
                DayAllDayStackPanel.Children.Add(chip);
            }
        }
        else
        {
            DayAllDayBorder.Visibility = Visibility.Collapsed;
        }

        // 2.0 课表动态投影 (Timetable -> Overview 日视图背景参考块)
        var projectedDayCourses = TimetableProjectionService.GetEffectiveCoursesForDate(_currentDate);
        foreach (var course in projectedDayCourses)
        {
            double startH = course.StartTime.Hour + course.StartTime.Minute / 60.0;
            double endH = course.EndTime.Hour + course.EndTime.Minute / 60.0;
            if (endH <= startH) endH = startH + 0.75;

            double top = startH * HourHeight + 1;
            double height = Math.Max(22.0, (endH - startH) * HourHeight - 2);

            var projCard = new Border
            {
                Width = Math.Max(60.0, canvasWidth - 16.0),
                Height = height,
                Background = new SolidColorBrush(Color.FromArgb(45, 0x06, 0xB6, 0xD4)),
                BorderBrush = new SolidColorBrush(Color.FromArgb(120, 0x06, 0xB6, 0xD4)),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(4),
                Padding = new Thickness(6, 3, 6, 3),
                Opacity = 0.85,
                ClipToBounds = true,
                ToolTip = $"📚 课表投影: {course.CourseName}\n时间: {course.StartTime:HH:mm}-{course.EndTime:HH:mm}\n地点: {course.Location}\n(只读背景参考，若与日程重叠将提示冲突)"
            };

            var st = new StackPanel { VerticalAlignment = VerticalAlignment.Top };
            var titleTb = new TextBlock
            {
                Text = $"📚 [课程] {course.CourseName}",
                FontSize = 11,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(Color.FromRgb(0x67, 0xE8, 0xF9)),
                TextTrimming = TextTrimming.CharacterEllipsis
            };
            var locTb = new TextBlock
            {
                Text = $"{course.StartTime:HH:mm}-{course.EndTime:HH:mm}  📍 {course.Location}",
                FontSize = 10,
                Foreground = new SolidColorBrush(Color.FromRgb(0x94, 0xA3, 0xB8)),
                TextTrimming = TextTrimming.CharacterEllipsis
            };
            st.Children.Add(titleTb);
            if (height > 32) st.Children.Add(locTb);
            projCard.Child = st;

            Canvas.SetLeft(projCard, 8);
            Canvas.SetTop(projCard, top);
            Canvas.SetZIndex(projCard, 1);
            DayEventsCanvas.Children.Add(projCard);
        }

        // 渲染定时日程 (支持多任务并发并列分列算法)
        if (timedEvents.Count > 0)
        {
            var clusters = ClusterOverlappingEvents(timedEvents);
            double availableWidth = Math.Max(200.0, canvasWidth - 16.0);

            foreach (var cluster in clusters)
            {
                var colAssignments = AssignEventColumns(cluster);
                int totalCols = colAssignments.Values.Max() + 1;
                double subColWidth = availableWidth / totalCols;

                foreach (var item in cluster)
                {
                    int subCol = colAssignments[item];

                    double startH = item.StartTime.Hour + item.StartTime.Minute / 60.0;
                    double endH = item.EndTime.Hour + item.EndTime.Minute / 60.0;
                    if (item.EndTime.Date > item.StartTime.Date) endH = 24.0;
                    if (endH <= startH) endH = startH + 0.5;

                    double top = startH * HourHeight + 1;
                    double height = Math.Max(24.0, (endH - startH) * HourHeight - 2);
                    double left = 8 + subCol * subColWidth;
                    double width = Math.Max(60.0, subColWidth - 4);

                    var card = CreateDayEventCard(item, width, height);
                    Canvas.SetLeft(card, left);
                    Canvas.SetTop(card, top);
                    DayEventsCanvas.Children.Add(card);
                }
            }
        }
    }

    private UIElement CreateDayEventCard(ScheduleItem item, double width, double height)
    {
        Brush bgBrush = GetEventBrush(item, 230);
        Brush borderBrush;
        string badge = item.WorkType switch
        {
            "REST_BUFFER" => "☕ 缓冲休息",
            "SHALLOW_WORK" => "⚡ 浅层事务",
            _ => "🎯 深度工作"
        };

        if (item.IsTentative)
        {
            borderBrush = new SolidColorBrush(Color.FromRgb(0x94, 0xA3, 0xB8));
            badge = "👻 待确认";
        }
        else if (item.WorkType == "REST_BUFFER")
        {
            borderBrush = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
        }
        else if (item.WorkType == "SHALLOW_WORK")
        {
            borderBrush = new SolidColorBrush(Color.FromRgb(0x64, 0x74, 0x8B));
        }
        else
        {
            borderBrush = new SolidColorBrush(Color.FromRgb(0x8B, 0x5C, 0xF6));
        }

        if (item.IsLocked)
        {
            badge = "🔒 " + badge;
        }

        if (!string.IsNullOrEmpty(item.ColorHex))
        {
            try
            {
                var c = (Color)ColorConverter.ConvertFromString(item.ColorHex);
                borderBrush = new SolidColorBrush(c);
            }
            catch { }
        }

        bool isIsolated = string.IsNullOrEmpty(item.GoalId) && item.WorkType != "REST_BUFFER";

        double cardOpacity = item.Status == "COMPLETED" ? 0.7 : (item.IsTentative ? 0.70 : 1.0);
        Brush cardBg = item.IsTentative ? new SolidColorBrush(Color.FromArgb(160, 0x47, 0x55, 0x69)) : bgBrush;
        Brush cardBorder = item.IsTentative 
            ? new SolidColorBrush(Color.FromRgb(0x94, 0xA3, 0xB8)) 
            : (isIsolated ? new SolidColorBrush(Color.FromRgb(0xF5, 0x9E, 0x0B)) : (item.Status == "IN_PROGRESS" ? new SolidColorBrush(Color.FromRgb(0x38, 0xBD, 0xF8)) : borderBrush));

        var border = new Border
        {
            Width = width,
            Height = height,
            Background = cardBg,
            BorderBrush = cardBorder,
            BorderThickness = new Thickness(isIsolated || item.IsTentative ? 1.5 : 1),
            CornerRadius = new CornerRadius(6),
            Padding = new Thickness(8, 3, 8, 3),
            Cursor = Cursors.Hand,
            Opacity = cardOpacity,
            ClipToBounds = true,
            ToolTip = $"{(item.IsTentative ? "👻 [待确认占位] " : "")}{(item.IsLocked ? "🔒 [坐标已锁定] " : "")}{badge} · {item.Title}\n{item.StartTime:HH:mm} - {item.EndTime:HH:mm}\n来源: {(string.IsNullOrEmpty(item.Source) ? "未注明" : item.Source)}\nDoD: {(string.IsNullOrEmpty(item.Dod) ? "未填写" : item.Dod)}\n点击查看详情{(item.IsLocked ? " (已锁定)" : "，拖动底部边缘调整时长")}"
        };

        var rootGrid = new Grid { ClipToBounds = true };
        rootGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        rootGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        // 信息排版区 (使用标准 Grid + StackPanel，限制内部宽度，防止任何文字重叠)
        var infoPanel = new StackPanel { VerticalAlignment = VerticalAlignment.Center, ClipToBounds = true };

        string statusPrefix = item.Status == "COMPLETED" ? "✓ " : "";
        string lockPrefix = item.IsLocked ? "🔒 " : "";
        string tentativePrefix = item.IsTentative ? "👻 " : "";
        var titleTb = new TextBlock
        {
            Text = lockPrefix + tentativePrefix + statusPrefix + item.Title,
            FontSize = 12.5,
            FontWeight = FontWeights.Bold,
            Foreground = Brushes.White,
            TextTrimming = TextTrimming.CharacterEllipsis,
            TextDecorations = item.Status == "COMPLETED" ? TextDecorations.Strikethrough : null
        };
        infoPanel.Children.Add(titleTb);

        if (height >= 34.0)
        {
            var metaTb = new TextBlock
            {
                Text = $"{item.StartTime:HH:mm} - {item.EndTime:HH:mm} · [{badge}]" + (!string.IsNullOrEmpty(item.Source) ? $" · {item.Source}" : ""),
                FontSize = 10.5,
                Foreground = new SolidColorBrush(Color.FromArgb(220, 0xFF, 0xFF, 0xFF)),
                TextTrimming = TextTrimming.CharacterEllipsis,
                Margin = new Thickness(0, 1, 0, 0)
            };
            infoPanel.Children.Add(metaTb);
        }

        if (height >= 50.0 && (!string.IsNullOrWhiteSpace(item.Dod) || !string.IsNullOrWhiteSpace(item.Description)))
        {
            string detailText = !string.IsNullOrWhiteSpace(item.Dod) ? $"DoD: {item.Dod}" : item.Description;
            var dodTb = new TextBlock
            {
                Text = detailText,
                FontSize = 10.0,
                Foreground = new SolidColorBrush(Color.FromArgb(190, 0xD1, 0xD5, 0xDB)),
                TextTrimming = TextTrimming.CharacterEllipsis,
                Margin = new Thickness(0, 2, 0, 0)
            };
            infoPanel.Children.Add(dodTb);
        }

        Grid.SetColumn(infoPanel, 0);
        rootGrid.Children.Add(infoPanel);

        // 状态切换按钮 (仅在宽度充裕且高度足够时显示)
        if (width >= 160 && height >= 32)
        {
            string statusText = item.Status switch
            {
                "COMPLETED" => "已完成",
                "IN_PROGRESS" => "进行中",
                _ => "待办"
            };
            var statusBtn = new Button
            {
                Content = statusText,
                Background = new SolidColorBrush(Color.FromArgb(50, 0x00, 0x00, 0x00)),
                Foreground = Brushes.White,
                BorderBrush = Brushes.White,
                BorderThickness = new Thickness(1),
                Padding = new Thickness(8, 2, 8, 2),
                FontSize = 10.5,
                Cursor = Cursors.Hand,
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(6, 0, 0, 0)
            };
            statusBtn.Click += (s, e) =>
            {
                e.Handled = true;
                string next = item.Status switch
                {
                    "PENDING" => "IN_PROGRESS",
                    "IN_PROGRESS" => "COMPLETED",
                    _ => "PENDING"
                };
                DatabaseService.UpdateScheduleStatus(item.Id, next);
                RenderAllCalendarViews();
                RefreshTimePnlData();
                UpdateCognitiveLoadQuota();
            };
            Grid.SetColumn(statusBtn, 1);
            rootGrid.Children.Add(statusBtn);
        }

        // 底部 6px 拖拽时长手柄
        var resizeThumb = new Border
        {
            Height = 6,
            VerticalAlignment = VerticalAlignment.Bottom,
            Background = Brushes.Transparent,
            Cursor = Cursors.SizeNS
        };
        resizeThumb.MouseLeftButtonDown += (s, e) =>
        {
            e.Handled = true;
            if (item.IsLocked)
            {
                MessageBox.Show("该日程时间坐标已锁定，若需调整时长请先在详情卡片中解锁。", "日程已锁定", MessageBoxButton.OK, MessageBoxImage.Information);
                return;
            }
            _isResizingEvent = true;
            _resizingItem = item;
            _resizeStartY = e.GetPosition(DayEventsCanvas).Y;
            _resizeInitialEndTime = item.EndTime;
            DayEventsCanvas.CaptureMouse();
        };
        Grid.SetColumnSpan(resizeThumb, 2);
        rootGrid.Children.Add(resizeThumb);

        border.Child = rootGrid;

        border.MouseLeftButtonUp += (s, e) =>
        {
            if (_isResizingEvent) return;
            e.Handled = true;
            OpenEventQuickDetail(item);
        };
        return border;
    }

    // =========================================================================
    // ===================== 4. MONTH VIEW (月视图) ============================
    // =========================================================================

    private void RenderMonthGrid()
    {
        MonthCellsGrid.Children.Clear();

        DateTime firstOfMonth = new DateTime(_currentDate.Year, _currentDate.Month, 1);
        int offset = ((int)firstOfMonth.DayOfWeek == 0) ? 6 : ((int)firstOfMonth.DayOfWeek - 1);
        DateTime startDate = firstOfMonth.AddDays(-offset);
        DateTime endDate = startDate.AddDays(42);

        var allEvents = DatabaseService.GetSchedulesForDateRange(startDate, endDate).Where(MatchesFilter).ToList();

        for (int i = 0; i < 42; i++)
        {
            DateTime cellDate = startDate.AddDays(i);
            bool isCurrentMonth = cellDate.Month == _currentDate.Month;
            bool isToday = cellDate.Date == DateTime.Today;

            var cellBorder = new Border
            {
                BorderBrush = ThemeService.CurrentBorderBrush,
                BorderThickness = new Thickness(0, 0, 1, 1),
                Background = isToday ? new SolidColorBrush(Color.FromArgb(20, 0x1A, 0x73, 0xE8)) : (isCurrentMonth ? Brushes.Transparent : new SolidColorBrush(Color.FromArgb(30, 0x00, 0x00, 0x00))),
                Padding = new Thickness(4)
            };

            var cellPanel = new Grid();
            cellPanel.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            cellPanel.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });

            var dateBadge = new Border
            {
                Width = 24,
                Height = 24,
                CornerRadius = new CornerRadius(12),
                Background = isToday ? new SolidColorBrush(Color.FromRgb(0x1A, 0x73, 0xE8)) : Brushes.Transparent,
                HorizontalAlignment = HorizontalAlignment.Center,
                Margin = new Thickness(0, 2, 0, 4)
            };
            dateBadge.Child = new TextBlock
            {
                Text = cellDate.Day == 1 ? $"{cellDate.Month}月1日" : cellDate.Day.ToString(),
                FontSize = 11,
                FontWeight = isToday ? FontWeights.Bold : FontWeights.Normal,
                Foreground = isToday ? Brushes.White : (isCurrentMonth ? ThemeService.CurrentTextBrush : ThemeService.CurrentMutedTextBrush),
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            Grid.SetRow(dateBadge, 0);
            cellPanel.Children.Add(dateBadge);

            var dayEvents = allEvents.Where(e => e.StartTime.Date == cellDate.Date).Take(3).ToList();
            var eventsStack = new StackPanel();
            foreach (var ev in dayEvents)
            {
                string evSourceTag = !string.IsNullOrEmpty(ev.Source) ? $"[{ev.Source}] " : "";
                var chip = new Border
                {
                    Background = ev.WorkType == "DEEP_WORK" ? new SolidColorBrush(Color.FromRgb(0x7C, 0x3A, 0xED)) : (ev.WorkType == "REST_BUFFER" ? new SolidColorBrush(Color.FromRgb(0x05, 0x96, 0x69)) : new SolidColorBrush(Color.FromRgb(0x47, 0x55, 0x69))),
                    CornerRadius = new CornerRadius(3),
                    Padding = new Thickness(4, 1, 4, 1),
                    Margin = new Thickness(0, 0, 0, 2),
                    Cursor = Cursors.Hand,
                    ToolTip = $"{ev.Title}\n来源: {(string.IsNullOrEmpty(ev.Source) ? "未注明" : ev.Source)}\n时间: {ev.StartTime:HH:mm} - {ev.EndTime:HH:mm}"
                };
                chip.Child = new TextBlock
                {
                    Text = evSourceTag + ev.Title,
                    FontSize = 10,
                    Foreground = Brushes.White,
                    TextTrimming = TextTrimming.CharacterEllipsis
                };
                ScheduleItem capturedEv = ev;
                chip.MouseLeftButtonUp += (s, e) =>
                {
                    e.Handled = true;
                    OpenEventEditModal(capturedEv);
                };
                eventsStack.Children.Add(chip);
            }
            Grid.SetRow(eventsStack, 1);
            cellPanel.Children.Add(eventsStack);

            cellBorder.Child = cellPanel;
            DateTime capturedDate = cellDate;
            cellBorder.MouseLeftButtonUp += (s, e) =>
            {
                _currentDate = capturedDate;
                OpenEventCreateModal(capturedDate, 9);
            };

            MonthCellsGrid.Children.Add(cellBorder);
        }
    }

    // =========================================================================
    // ===================== 5. AGENDA VIEW (日程清单) =========================
    // =========================================================================

    private void RenderAgendaList()
    {
        AgendaListPanel.Children.Clear();
        DateTime startRange = _currentDate.AddDays(-7);
        DateTime endRange = _currentDate.AddDays(30);

        var rawEvents = DatabaseService.GetSchedulesForDateRange(startRange, endRange);
        var filteredEvents = rawEvents.Where(MatchesFilter).OrderBy(e => e.StartTime).ToList();

        if (filteredEvents.Count == 0)
        {
            AgendaEmptyState.Visibility = Visibility.Visible;
            return;
        }
        AgendaEmptyState.Visibility = Visibility.Collapsed;

        var grouped = filteredEvents.GroupBy(e => e.StartTime.Date);
        var culture = new CultureInfo("zh-CN");

        foreach (var group in grouped)
        {
            var groupHeader = new TextBlock
            {
                Text = group.Key.ToString("M月d日 · dddd", culture),
                FontSize = 14,
                FontWeight = FontWeights.Bold,
                Foreground = group.Key.Date == DateTime.Today ? ThemeService.CurrentAccentBrush : ThemeService.CurrentSecondaryTextBrush,
                Margin = new Thickness(0, 14, 0, 8)
            };
            AgendaListPanel.Children.Add(groupHeader);

            foreach (var ev in group)
            {
                AgendaListPanel.Children.Add(CreateAgendaItemCard(ev));
            }
        }
    }

    private UIElement CreateAgendaItemCard(ScheduleItem item)
    {
        var border = new Border
        {
            Background = ThemeService.CurrentCardBrush,
            BorderBrush = ThemeService.CurrentBorderBrush,
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(8),
            Padding = new Thickness(14, 10, 14, 10),
            Margin = new Thickness(0, 0, 0, 8),
            Cursor = Cursors.Hand
        };

        var grid = new Grid();
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        string typeBadge = item.WorkType switch
        {
            "REST_BUFFER" => "☕ 缓冲",
            "SHALLOW_WORK" => "⚡ 浅层",
            _ => "🎯 深度"
        };
        var catPill = new Border
        {
            Background = item.WorkType == "DEEP_WORK" ? new SolidColorBrush(Color.FromRgb(0x7C, 0x3A, 0xED)) : (item.WorkType == "REST_BUFFER" ? new SolidColorBrush(Color.FromRgb(0x05, 0x96, 0x69)) : new SolidColorBrush(Color.FromRgb(0x47, 0x55, 0x69))),
            CornerRadius = new CornerRadius(4),
            Padding = new Thickness(6, 2, 6, 2),
            Margin = new Thickness(0, 0, 12, 0),
            VerticalAlignment = VerticalAlignment.Center
        };
        catPill.Child = new TextBlock { Text = typeBadge, FontSize = 11, Foreground = Brushes.White, FontWeight = FontWeights.SemiBold };
        Grid.SetColumn(catPill, 0);
        grid.Children.Add(catPill);

        var details = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(0, 0, 10, 0) };
        details.Children.Add(new TextBlock
        {
            Text = item.Title,
            FontSize = 13,
            FontWeight = FontWeights.Bold,
            Foreground = ThemeService.CurrentTextBrush,
            TextTrimming = TextTrimming.CharacterEllipsis,
            TextDecorations = item.Status == "COMPLETED" ? TextDecorations.Strikethrough : null
        });
        string sourcePart = !string.IsNullOrEmpty(item.Source) ? $" · [{item.Source}]" : "";
        details.Children.Add(new TextBlock
        {
            Text = $"{item.StartTime:HH:mm} - {item.EndTime:HH:mm}{sourcePart} · DoD: {(string.IsNullOrEmpty(item.Dod) ? "未填写" : item.Dod)}",
            FontSize = 11,
            Foreground = ThemeService.CurrentSecondaryTextBrush,
            TextTrimming = TextTrimming.CharacterEllipsis,
            Margin = new Thickness(0, 2, 0, 0)
        });
        Grid.SetColumn(details, 1);
        grid.Children.Add(details);

        string statusText = item.Status switch
        {
            "COMPLETED" => "已完成",
            "IN_PROGRESS" => "进行中",
            _ => "待办"
        };
        var statusBtn = new Button
        {
            Content = statusText,
            Background = ThemeService.CurrentControlBrush,
            Foreground = item.Status == "COMPLETED" ? new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81)) : new SolidColorBrush(Color.FromRgb(0x8A, 0xB4, 0xF8)),
            BorderThickness = new Thickness(0),
            Padding = new Thickness(10, 4, 10, 4),
            FontSize = 11,
            Cursor = Cursors.Hand,
            VerticalAlignment = VerticalAlignment.Center
        };
        statusBtn.Click += (s, e) =>
        {
            e.Handled = true;
            string next = item.Status switch
            {
                "PENDING" => "IN_PROGRESS",
                "IN_PROGRESS" => "COMPLETED",
                _ => "PENDING"
            };
            DatabaseService.UpdateScheduleStatus(item.Id, next);
            RenderAllCalendarViews();
            RefreshTimePnlData();
            UpdateCognitiveLoadQuota();
        };
        Grid.SetColumn(statusBtn, 2);
        grid.Children.Add(statusBtn);

        border.Child = grid;
        border.MouseLeftButtonUp += (s, e) => OpenEventEditModal(item);
        return border;
    }

    // =========================================================================
    // ===================== 6. YEAR VIEW (年视图 - 12个月概览) ==================
    // =========================================================================

    private void RenderYearGrid()
    {
        YearMonthsGrid.Children.Clear();
        int year = _currentDate.Year;
        YearViewTitleText.Text = $"{year} 年度日程概览";

        DateTime yearStart = new DateTime(year, 1, 1);
        DateTime yearEnd = new DateTime(year + 1, 1, 1);
        var allEvents = DatabaseService.GetSchedulesForDateRange(yearStart, yearEnd).Where(MatchesFilter).ToList();

        YearTotalEventsText.Text = $"全年共 {allEvents.Count} 个日程";
        double deepWorkMins = allEvents.Where(e => e.WorkType == "DEEP_WORK").Sum(e => e.ActualMinutes > 0 ? e.ActualMinutes : e.EstimatedMinutes);
        YearDeepWorkHoursText.Text = $"深度工作: {deepWorkMins / 60.0:F1} 小时";

        var eventDates = allEvents.Select(e => e.StartTime.Date).ToHashSet();
        string[] weekInitials = { "一", "二", "三", "四", "五", "六", "日" };

        for (int m = 1; m <= 12; m++)
        {
            var monthCard = new Border
            {
                Background = ThemeService.CurrentCardBrush,
                BorderBrush = ThemeService.CurrentBorderBrush,
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(8),
                Padding = new Thickness(10),
                Margin = new Thickness(6)
            };

            var monthStack = new StackPanel();

            // Month Title Header
            DateTime firstDay = new DateTime(year, m, 1);
            int daysInMonth = DateTime.DaysInMonth(year, m);
            int monthEventCount = allEvents.Count(e => e.StartTime.Year == year && e.StartTime.Month == m);

            var headerPanel = new Grid { Margin = new Thickness(0, 0, 0, 8), Cursor = Cursors.Hand };
            headerPanel.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            headerPanel.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            var titleText = new TextBlock
            {
                Text = $"{m}月",
                FontSize = 14,
                FontWeight = FontWeights.Bold,
                Foreground = (year == DateTime.Today.Year && m == DateTime.Today.Month) ? new SolidColorBrush(Color.FromRgb(0x8A, 0xB4, 0xF8)) : ThemeService.CurrentTextBrush
            };
            Grid.SetColumn(titleText, 0);
            headerPanel.Children.Add(titleText);

            if (monthEventCount > 0)
            {
                var countBadge = new Border
                {
                    Background = new SolidColorBrush(Color.FromRgb(0x1E, 0x29, 0x3B)),
                    CornerRadius = new CornerRadius(3),
                    Padding = new Thickness(4, 1, 4, 1),
                    Child = new TextBlock
                    {
                        Text = $"{monthEventCount} 项",
                        FontSize = 10,
                        Foreground = new SolidColorBrush(Color.FromRgb(0x38, 0xBD, 0xF8))
                    }
                };
                Grid.SetColumn(countBadge, 1);
                headerPanel.Children.Add(countBadge);
            }

            int capturedMonth = m;
            headerPanel.MouseLeftButtonUp += (s, e) =>
            {
                _currentDate = new DateTime(year, capturedMonth, 1);
                _currentView = "Month";
                RenderAllCalendarViews();
            };
            monthStack.Children.Add(headerPanel);

            // Weekday Initials Row
            var weekdayGrid = new UniformGrid { Columns = 7, Margin = new Thickness(0, 0, 0, 4) };
            foreach (var w in weekInitials)
            {
                weekdayGrid.Children.Add(new TextBlock
                {
                    Text = w,
                    FontSize = 10,
                    Foreground = new SolidColorBrush(Color.FromRgb(0x71, 0x71, 0x7A)),
                    HorizontalAlignment = HorizontalAlignment.Center
                });
            }
            monthStack.Children.Add(weekdayGrid);

            // Days Grid
            var daysGrid = new UniformGrid { Columns = 7 };
            int offset = ((int)firstDay.DayOfWeek == 0) ? 6 : ((int)firstDay.DayOfWeek - 1);
            for (int blank = 0; blank < offset; blank++)
            {
                daysGrid.Children.Add(new Border { Height = 22 });
            }

            for (int day = 1; day <= daysInMonth; day++)
            {
                DateTime dayDate = new DateTime(year, m, day);
                bool isToday = dayDate.Date == DateTime.Today;
                bool hasEvents = eventDates.Contains(dayDate.Date);

                var dayCell = new Border
                {
                    Height = 24,
                    CornerRadius = new CornerRadius(12),
                    Background = isToday ? new SolidColorBrush(Color.FromRgb(0x1A, 0x73, 0xE8)) : (hasEvents ? new SolidColorBrush(Color.FromArgb(40, 0x10, 0xB9, 0x81)) : Brushes.Transparent),
                    Cursor = Cursors.Hand,
                    ToolTip = hasEvents ? $"{dayDate:M月d日}: 有日程安排 (点击查看)" : $"{dayDate:M月d日} (点击进入日视图)"
                };

                var dayNum = new TextBlock
                {
                    Text = day.ToString(),
                    FontSize = 11,
                    FontWeight = isToday ? FontWeights.Bold : (hasEvents ? FontWeights.SemiBold : FontWeights.Normal),
                    Foreground = isToday ? Brushes.White : (hasEvents ? new SolidColorBrush(Color.FromRgb(0x34, 0xD3, 0x99)) : ThemeService.CurrentTextBrush),
                    HorizontalAlignment = HorizontalAlignment.Center,
                    VerticalAlignment = VerticalAlignment.Center
                };
                dayCell.Child = dayNum;

                DateTime capturedDate = dayDate;
                dayCell.MouseLeftButtonUp += (s, e) =>
                {
                    _currentDate = capturedDate;
                    _currentView = "Day";
                    RenderAllCalendarViews();
                };

                daysGrid.Children.Add(dayCell);
            }

            monthStack.Children.Add(daysGrid);
            monthCard.Child = monthStack;
            YearMonthsGrid.Children.Add(monthCard);
        }
    }

    private void OnYearJumpCurrentMonthClicked(object sender, RoutedEventArgs e)
    {
        _currentDate = DateTime.Today;
        _currentView = "Month";
        RenderAllCalendarViews();
    }

    // =========================================================================
    // ===================== 7. CUSTOM VIEW (真正意义上的自由自定义排期视图) =========
    // =========================================================================

    private double GetCustomColWidth()
    {
        double available = CustomScrollViewer.ActualWidth > 80 ? (CustomScrollViewer.ActualWidth - 60) : 700;
        double minCol = 140.0;
        return (_customDays <= 4) ? Math.Max(minCol, available / Math.Max(1, _customDays)) : minCol;
    }

    private void SyncCustomControlsUI()
    {
        _isUpdatingCustomPickers = true;
        try
        {
            CustomStartDatePicker.SelectedDate = _customStartDate;
            CustomEndDatePicker.SelectedDate = _customEndDate;
            CustomDaysInput.Text = _customDays.ToString();
            UpdateCustomNoticeText();
            UpdateCustomModeButtons();
        }
        finally
        {
            _isUpdatingCustomPickers = false;
        }
    }

    private void UpdateCustomNoticeText()
    {
        CustomRangeNoticeText.Text = _customDays > 4 ? "↔ 支持横向平滑滚动" : "";
    }

    private void OnCustomDateRangePickerChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_isUpdatingCustomPickers) return;
        if (CustomStartDatePicker.SelectedDate.HasValue && CustomEndDatePicker.SelectedDate.HasValue)
        {
            DateTime start = CustomStartDatePicker.SelectedDate.Value.Date;
            DateTime end = CustomEndDatePicker.SelectedDate.Value.Date;
            if (end < start)
            {
                end = start;
                _isUpdatingCustomPickers = true;
                CustomEndDatePicker.SelectedDate = end;
                _isUpdatingCustomPickers = false;
            }
            int days = Math.Clamp((int)(end - start).TotalDays + 1, 1, 90);
            CustomDaysInput.Text = days.ToString();
        }
    }

    private void OnApplyCustomDateRangeClicked(object sender, RoutedEventArgs e)
    {
        if (CustomStartDatePicker.SelectedDate.HasValue && CustomEndDatePicker.SelectedDate.HasValue)
        {
            DateTime start = CustomStartDatePicker.SelectedDate.Value.Date;
            DateTime end = CustomEndDatePicker.SelectedDate.Value.Date;
            if (end < start) end = start;

            _customStartDate = start;
            _customEndDate = end;
            _customDays = Math.Clamp((int)(end - start).TotalDays + 1, 1, 90);
            _currentDate = _customStartDate;
            RenderAllCalendarViews();
        }
    }

    private void SetCustomDaysAndApply(int days)
    {
        _customDays = Math.Clamp(days, 1, 90);
        _customEndDate = _customStartDate.AddDays(_customDays - 1);
        _currentDate = _customStartDate;
        RenderAllCalendarViews();
    }

    private void OnCustomDaysDecrementClicked(object sender, RoutedEventArgs e) => SetCustomDaysAndApply(_customDays - 1);
    private void OnCustomDaysIncrementClicked(object sender, RoutedEventArgs e) => SetCustomDaysAndApply(_customDays + 1);

    private void OnCustomDaysInputKeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Enter)
        {
            if (int.TryParse(CustomDaysInput.Text.Trim(), out int val))
            {
                SetCustomDaysAndApply(val);
            }
            e.Handled = true;
        }
    }

    private void OnCustomPreset3Days(object sender, RoutedEventArgs e) => SetCustomDaysAndApply(3);
    private void OnCustomPreset4Days(object sender, RoutedEventArgs e) => SetCustomDaysAndApply(4);
    private void OnCustomPreset5Days(object sender, RoutedEventArgs e) => SetCustomDaysAndApply(5);
    private void OnCustomPreset7Days(object sender, RoutedEventArgs e) => SetCustomDaysAndApply(7);
    private void OnCustomPreset10Days(object sender, RoutedEventArgs e) => SetCustomDaysAndApply(10);
    private void OnCustomPreset14Days(object sender, RoutedEventArgs e) => SetCustomDaysAndApply(14);
    private void OnCustomPreset30Days(object sender, RoutedEventArgs e) => SetCustomDaysAndApply(30);
    private void OnCustomPresetRestOfMonth(object sender, RoutedEventArgs e)
    {
        int totalDays = DateTime.DaysInMonth(_customStartDate.Year, _customStartDate.Month);
        int remaining = Math.Max(1, totalDays - _customStartDate.Day + 1);
        SetCustomDaysAndApply(remaining);
    }

    private void OnCustomModeTimelineClicked(object sender, RoutedEventArgs e)
    {
        _isCustomAgendaMode = false;
        CustomTimelineViewGrid.Visibility = Visibility.Visible;
        CustomAgendaViewGrid.Visibility = Visibility.Collapsed;
        UpdateCustomModeButtons();
        RenderCustomHeader();
        RenderCustomEvents();
    }

    private void OnCustomModeAgendaClicked(object sender, RoutedEventArgs e)
    {
        _isCustomAgendaMode = true;
        CustomTimelineViewGrid.Visibility = Visibility.Collapsed;
        CustomAgendaViewGrid.Visibility = Visibility.Visible;
        UpdateCustomModeButtons();
        RenderCustomAgenda();
    }

    private void UpdateCustomModeButtons()
    {
        var activeBg = new SolidColorBrush(Color.FromRgb(0x1A, 0x73, 0xE8));
        var normalBg = Brushes.Transparent;
        var activeFg = Brushes.White;
        var normalFg = ThemeService.CurrentTextBrush;

        CustomModeTimelineBtn.Background = !_isCustomAgendaMode ? activeBg : normalBg;
        CustomModeTimelineBtn.Foreground = !_isCustomAgendaMode ? activeFg : normalFg;
        CustomModeTimelineBtn.FontWeight = !_isCustomAgendaMode ? FontWeights.SemiBold : FontWeights.Normal;

        CustomModeAgendaBtn.Background = _isCustomAgendaMode ? activeBg : normalBg;
        CustomModeAgendaBtn.Foreground = _isCustomAgendaMode ? activeFg : normalFg;
        CustomModeAgendaBtn.FontWeight = _isCustomAgendaMode ? FontWeights.SemiBold : FontWeights.Normal;
    }

    private void OnCustomScrollViewerScrollChanged(object sender, ScrollChangedEventArgs e)
    {
        if (e.HorizontalChange != 0)
        {
            CustomHeaderScrollViewer.ScrollToHorizontalOffset(e.HorizontalOffset);
            CustomAllDayScrollViewer.ScrollToHorizontalOffset(e.HorizontalOffset);
        }
    }

    private void RenderCustomHeader()
    {
        CustomHeaderContainer.Children.Clear();
        string[] weekNames = { "周日", "周一", "周二", "周三", "周四", "周五", "周六" };
        double colWidth = GetCustomColWidth();

        for (int i = 0; i < _customDays; i++)
        {
            DateTime dayDate = _customStartDate.AddDays(i);
            bool isToday = dayDate.Date == DateTime.Today;

            var dayHeaderBorder = new Border
            {
                Width = colWidth,
                BorderBrush = ThemeService.CurrentBorderBrush,
                BorderThickness = new Thickness(0, 0, 1, 0),
                Padding = new Thickness(4, 6, 4, 6)
            };

            var dayHeaderPanel = new StackPanel
            {
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };

            var weekNameText = new TextBlock
            {
                Text = weekNames[(int)dayDate.DayOfWeek],
                FontSize = 11,
                FontWeight = isToday ? FontWeights.SemiBold : FontWeights.Normal,
                Foreground = isToday ? ThemeService.CurrentAccentBrush : ThemeService.CurrentSecondaryTextBrush,
                HorizontalAlignment = HorizontalAlignment.Center,
                Margin = new Thickness(0, 0, 0, 4)
            };
            dayHeaderPanel.Children.Add(weekNameText);

            var dateBadge = new Border
            {
                Width = 32,
                Height = 32,
                CornerRadius = new CornerRadius(16),
                Background = isToday ? new SolidColorBrush(Color.FromRgb(0x1A, 0x73, 0xE8)) : Brushes.Transparent,
                HorizontalAlignment = HorizontalAlignment.Center,
                Cursor = Cursors.Hand,
                ToolTip = $"{dayDate:yyyy年M月d日} (点击进入日视图)"
            };

            var dateNumText = new TextBlock
            {
                Text = $"{dayDate.Month}/{dayDate.Day}",
                FontSize = 12,
                FontWeight = isToday ? FontWeights.Bold : FontWeights.Normal,
                Foreground = isToday ? Brushes.White : ThemeService.CurrentTextBrush,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            dateBadge.Child = dateNumText;

            DateTime captured = dayDate;
            dateBadge.MouseLeftButtonUp += (s, e) =>
            {
                _currentDate = captured;
                _currentView = "Day";
                RenderAllCalendarViews();
            };

            dayHeaderPanel.Children.Add(dateBadge);
            dayHeaderBorder.Child = dayHeaderPanel;
            CustomHeaderContainer.Children.Add(dayHeaderBorder);
        }
    }

    private void RenderCustomSlots()
    {
        CustomHourLabelsPanel.Children.Clear();
        for (int h = 0; h < 24; h++)
        {
            var hourBlock = new TextBlock
            {
                Text = $"{h:D2}:00",
                FontSize = 10,
                Foreground = ThemeService.CurrentSecondaryTextBrush,
                Height = HourHeight,
                HorizontalAlignment = HorizontalAlignment.Right,
                Margin = new Thickness(0, -7, 8, 0)
            };
            CustomHourLabelsPanel.Children.Add(hourBlock);
        }

        CustomSlotsContainer.Children.Clear();
        double colWidth = GetCustomColWidth();

        for (int col = 0; col < _customDays; col++)
        {
            int colCaptured = col;
            var colPanel = new StackPanel { Width = colWidth };
            for (int h = 0; h < 24; h++)
            {
                int hCaptured = h;
                var slot = new Border
                {
                    Height = HourHeight,
                    BorderBrush = ThemeService.CurrentGridLineBrush,
                    BorderThickness = new Thickness(0, 0, 1, 1),
                    Background = Brushes.Transparent,
                    Cursor = Cursors.Hand,
                    ToolTip = "点击创建日程"
                };

                slot.MouseLeftButtonUp += (s, e) =>
                {
                    if (_isDragSelecting) return;
                    DateTime clickedDate = _customStartDate.AddDays(colCaptured);
                    OpenEventCreateModal(clickedDate, hCaptured);
                };

                colPanel.Children.Add(slot);
            }
            CustomSlotsContainer.Children.Add(colPanel);
        }

        double totalWidth = colWidth * _customDays;
        CustomEventsCanvas.Width = totalWidth;
        CustomEventsCanvas.Height = 24 * HourHeight;
    }

    private void RenderCustomEvents()
    {
        RenderCustomSlots();
        CustomEventsCanvas.Children.Clear();

        double colWidth = GetCustomColWidth();
        double totalWidth = colWidth * _customDays;
        CustomEventsCanvas.Width = totalWidth;
        CustomEventsCanvas.Height = 24 * HourHeight;

        DateTime startDate = _customStartDate;
        DateTime endDate = _customEndDate.AddDays(1);

        // 1. Google 经典红线指示器
        for (int c = 0; c < _customDays; c++)
        {
            DateTime dayDate = _customStartDate.AddDays(c);
            if (dayDate == DateTime.Today)
            {
                double nowH = DateTime.Now.Hour + DateTime.Now.Minute / 60.0;
                double lineTop = nowH * HourHeight;

                var dot = new Ellipse
                {
                    Width = 10,
                    Height = 10,
                    Fill = new SolidColorBrush(Color.FromRgb(0xEA, 0x43, 0x35)),
                    IsHitTestVisible = false
                };
                Canvas.SetLeft(dot, c * colWidth - 5);
                Canvas.SetTop(dot, lineTop - 4.5);
                CustomEventsCanvas.Children.Add(dot);

                var line = new Border
                {
                    Height = 2,
                    Background = new SolidColorBrush(Color.FromRgb(0xEA, 0x43, 0x35)),
                    Width = colWidth,
                    IsHitTestVisible = false
                };
                Canvas.SetLeft(line, c * colWidth);
                Canvas.SetTop(line, lineTop - 1);
                CustomEventsCanvas.Children.Add(line);
            }
        }

        var rawEvents = DatabaseService.GetSchedulesForDateRange(startDate, endDate);
        var filteredEvents = rawEvents.Where(MatchesFilter).ToList();

        // 2. 全天日程分离渲染
        var allDayEvents = filteredEvents.Where(e => e.IsAllDay).ToList();
        var timedEvents = filteredEvents.Where(e => !e.IsAllDay).ToList();

        CustomAllDayContainer.Children.Clear();
        if (allDayEvents.Count > 0)
        {
            CustomAllDayBorder.Visibility = Visibility.Visible;
            for (int d = 0; d < _customDays; d++)
            {
                DateTime dayDate = _customStartDate.AddDays(d).Date;
                var dayAllDay = allDayEvents.Where(e => e.StartTime.Date <= dayDate && e.EndTime.Date >= dayDate).ToList();
                var dayStack = new StackPanel { Width = colWidth, Margin = new Thickness(2, 2, 2, 2) };
                foreach (var ev in dayAllDay)
                {
                    dayStack.Children.Add(CreateAllDayEventChip(ev));
                }
                CustomAllDayContainer.Children.Add(dayStack);
            }
        }
        else
        {
            CustomAllDayBorder.Visibility = Visibility.Collapsed;
        }

        // 3. 定时时间块渲染 (按天分组 + 并发时间重叠自动分列)
        for (int d = 0; d < _customDays; d++)
        {
            DateTime dayDate = _customStartDate.AddDays(d).Date;
            var dayEvents = timedEvents.Where(e => e.StartTime.Date == dayDate).OrderBy(e => e.StartTime).ThenByDescending(e => e.EndTime).ToList();
            if (dayEvents.Count == 0) continue;

            var clusters = ClusterOverlappingEvents(dayEvents);
            double dayLeft = d * colWidth;
            double availableDayWidth = Math.Max(20.0, colWidth - 4.0);

            foreach (var cluster in clusters)
            {
                var colAssignments = AssignEventColumns(cluster);
                int totalCols = colAssignments.Values.Max() + 1;
                double subColWidth = availableDayWidth / totalCols;

                foreach (var item in cluster)
                {
                    int subCol = colAssignments[item];

                    double startH = item.StartTime.Hour + item.StartTime.Minute / 60.0;
                    double endH = item.EndTime.Hour + item.EndTime.Minute / 60.0;
                    if (item.EndTime.Date > item.StartTime.Date) endH = 24.0;
                    if (endH <= startH) endH = startH + 0.5;

                    double top = startH * HourHeight + 1;
                    double height = Math.Max(20.0, (endH - startH) * HourHeight - 2);
                    double left = dayLeft + 2 + subCol * subColWidth;
                    double width = Math.Max(18.0, subColWidth - 2);

                    var card = CreateWeekEventChip(item, width, height);
                    Canvas.SetLeft(card, left);
                    Canvas.SetTop(card, top);
                    CustomEventsCanvas.Children.Add(card);
                }
            }
        }
    }

    private void RenderCustomAgenda()
    {
        CustomAgendaListPanel.Children.Clear();
        DateTime startDate = _customStartDate;
        DateTime endDate = _customEndDate.AddDays(1);

        var rawEvents = DatabaseService.GetSchedulesForDateRange(startDate, endDate);
        var filteredEvents = rawEvents.Where(MatchesFilter).OrderBy(e => e.StartTime).ToList();

        if (filteredEvents.Count == 0)
        {
            var empty = new Border
            {
                Background = ThemeService.CurrentCardBrush,
                BorderBrush = ThemeService.CurrentBorderBrush,
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(10),
                Padding = new Thickness(36),
                Child = new StackPanel
                {
                    HorizontalAlignment = HorizontalAlignment.Center,
                    Children =
                    {
                        new TextBlock { Text = "📅", FontSize = 36, HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 0, 0, 10) },
                        new TextBlock { Text = $"在当前自定义周期内（{_customStartDate:M月d日} - {_customEndDate:M月d日}）暂无排期日程", FontSize = 14, FontWeight = FontWeights.SemiBold, Foreground = ThemeService.CurrentTextBrush, HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 0, 0, 6) }
                    }
                }
            };
            CustomAgendaListPanel.Children.Add(empty);
            return;
        }

        var culture = new CultureInfo("zh-CN");
        for (int i = 0; i < _customDays; i++)
        {
            DateTime dayDate = _customStartDate.AddDays(i);
            var dayEvents = filteredEvents.Where(e => e.StartTime.Date == dayDate).ToList();
            if (dayEvents.Count == 0) continue;

            double deepMins = dayEvents.Where(e => e.WorkType == "DEEP_WORK").Sum(e => e.ActualMinutes > 0 ? e.ActualMinutes : e.EstimatedMinutes);

            var headerGrid = new Grid { Margin = new Thickness(0, 16, 0, 8) };
            headerGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            headerGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            var dateText = new TextBlock
            {
                Text = dayDate.ToString("M月d日 · dddd", culture),
                FontSize = 14,
                FontWeight = FontWeights.Bold,
                Foreground = dayDate == DateTime.Today ? ThemeService.CurrentAccentBrush : ThemeService.CurrentSecondaryTextBrush,
                VerticalAlignment = VerticalAlignment.Center
            };
            Grid.SetColumn(dateText, 0);
            headerGrid.Children.Add(dateText);

            var badge = new TextBlock
            {
                Text = $"{dayEvents.Count} 项任务" + (deepMins > 0 ? $" · 深度工作 {deepMins / 60.0:F1}h" : ""),
                FontSize = 11,
                Foreground = new SolidColorBrush(Color.FromRgb(0x38, 0xBD, 0xF8)),
                VerticalAlignment = VerticalAlignment.Center
            };
            Grid.SetColumn(badge, 1);
            headerGrid.Children.Add(badge);

            CustomAgendaListPanel.Children.Add(headerGrid);

            foreach (var ev in dayEvents)
            {
                CustomAgendaListPanel.Children.Add(CreateAgendaItemCard(ev));
            }
        }
    }

    private void OnCustomCanvasDrop(object sender, DragEventArgs e)
    {
        if (!e.Data.GetDataPresent("ScheduleId")) return;
        string? scheduleId = e.Data.GetData("ScheduleId") as string;
        if (string.IsNullOrEmpty(scheduleId)) return;

        Point pt = e.GetPosition(CustomEventsCanvas);
        double colWidth = GetCustomColWidth();

        int col = Math.Clamp((int)(pt.X / colWidth), 0, _customDays - 1);
        DateTime targetDate = _customStartDate.AddDays(col);

        double hourFraction = pt.Y / HourHeight;
        int hour = Math.Clamp((int)hourFraction, 0, 23);
        int minute = ((int)((hourFraction - hour) * 60) / 15) * 15;
        DateTime newStart = targetDate.AddHours(hour).AddMinutes(minute);

        var task = DatabaseService.GetScheduleById(scheduleId);
        if (task != null)
        {
            int durationMins = task.EstimatedMinutes > 0 ? task.EstimatedMinutes : 60;
            task.StartTime = newStart;
            task.EndTime = newStart.AddMinutes(durationMins);
            task.IsAllDay = false;
            DatabaseService.UpsertSchedule(task);
            RenderAllCalendarViews();
        }
    }

    // =========================================================================
    // ================= MODULE 4: 空白区域鼠标框选建块 (CANVAS DRAG-SELECT) =======
    // =========================================================================

    private void OnCanvasMouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (sender is not Canvas canvas) return;

        // 仅在空白处开始框选
        Point pt = e.GetPosition(canvas);
        _dragSelectStartPoint = pt;
        _isDragSelecting = true;

        if (canvas == WeekEventsCanvas)
        {
            double totalWidth = canvas.ActualWidth;
            if (totalWidth <= 0) totalWidth = 700;
            double colWidth = totalWidth / 7.0;
            int col = (int)(pt.X / colWidth);
            DateTime monday = GetMondayOfWeek(_currentDate);
            _dragSelectDate = monday.AddDays(Math.Clamp(col, 0, 6));
        }
        else if (canvas == CustomEventsCanvas)
        {
            double colWidth = GetCustomColWidth();
            int col = (int)(pt.X / colWidth);
            _dragSelectDate = _customStartDate.AddDays(Math.Clamp(col, 0, _customDays - 1));
        }
        else
        {
            _dragSelectDate = _currentDate;
        }

        if (_dragSelectBorder == null)
        {
            _dragSelectBorder = new Border
            {
                Background = new SolidColorBrush(Color.FromArgb(60, 0x1A, 0x73, 0xE8)),
                BorderBrush = new SolidColorBrush(Color.FromRgb(0x8A, 0xB4, 0xF8)),
                BorderThickness = new Thickness(1.5),
                CornerRadius = new CornerRadius(4),
                IsHitTestVisible = false
            };
        }

        if (!canvas.Children.Contains(_dragSelectBorder))
        {
            canvas.Children.Add(_dragSelectBorder);
        }

        Canvas.SetLeft(_dragSelectBorder, pt.X);
        Canvas.SetTop(_dragSelectBorder, pt.Y);
        _dragSelectBorder.Width = 0;
        _dragSelectBorder.Height = 0;
        _dragSelectBorder.Visibility = Visibility.Visible;
    }

    private void OnCanvasMouseMove(object sender, MouseEventArgs e)
    {
        // 1. 拖拽调整日程时间块时长 (Duration Resize with 15m Snap)
        if (_isResizingEvent && _resizingItem != null && sender is Canvas resCanvas)
        {
            Point currentPt = e.GetPosition(resCanvas);
            double deltaY = currentPt.Y - _resizeStartY;
            int deltaMins = (int)Math.Round((deltaY / HourHeight) * 60.0);
            deltaMins = (deltaMins / 15) * 15;

            DateTime newEnd = _resizeInitialEndTime.AddMinutes(deltaMins);
            if (newEnd < _resizingItem.StartTime.AddMinutes(15))
                newEnd = _resizingItem.StartTime.AddMinutes(15);

            if (newEnd != _resizingItem.EndTime)
            {
                _resizingItem.EndTime = newEnd;
                _resizingItem.EstimatedMinutes = (int)(newEnd - _resizingItem.StartTime).TotalMinutes;
                if (resCanvas == WeekEventsCanvas) RenderWeekEvents();
                else if (resCanvas == CustomEventsCanvas) RenderCustomEvents();
                else RenderDayEvents();
            }
            return;
        }

        if (!_isDragSelecting || _dragSelectBorder == null || sender is not Canvas canvas) return;

        Point currentPt2 = e.GetPosition(canvas);
        double top = Math.Min(_dragSelectStartPoint.Y, currentPt2.Y);
        double height = Math.Abs(currentPt2.Y - _dragSelectStartPoint.Y);

        double totalWidth = canvas.ActualWidth;
        if (totalWidth <= 0) totalWidth = 700;

        if (canvas == WeekEventsCanvas)
        {
            double colWidth = totalWidth / 7.0;
            int col = (int)(_dragSelectStartPoint.X / colWidth);
            double left = col * colWidth + 2;
            double width = colWidth - 4;

            Canvas.SetLeft(_dragSelectBorder, left);
            Canvas.SetTop(_dragSelectBorder, top);
            _dragSelectBorder.Width = width;
            _dragSelectBorder.Height = height;
        }
        else if (canvas == CustomEventsCanvas)
        {
            double colWidth = GetCustomColWidth();
            int col = (int)(_dragSelectStartPoint.X / colWidth);
            double left = col * colWidth + 2;
            double width = colWidth - 4;

            Canvas.SetLeft(_dragSelectBorder, left);
            Canvas.SetTop(_dragSelectBorder, top);
            _dragSelectBorder.Width = width;
            _dragSelectBorder.Height = height;
        }
        else
        {
            Canvas.SetLeft(_dragSelectBorder, 8);
            Canvas.SetTop(_dragSelectBorder, top);
            _dragSelectBorder.Width = Math.Max(100, canvas.ActualWidth - 16);
            _dragSelectBorder.Height = height;
        }
    }

    private void OnCanvasMouseLeftButtonUp(object sender, MouseButtonEventArgs e)
    {
        // 1. 释放拖拽调整日程时长
        if (_isResizingEvent)
        {
            _isResizingEvent = false;
            if (_resizingItem != null)
            {
                DatabaseService.UpsertSchedule(_resizingItem);
                _resizingItem = null;
                RenderAllCalendarViews();
                RefreshTimePnlData();
                UpdateCognitiveLoadQuota();
            }
            if (sender is Canvas c) c.ReleaseMouseCapture();
            return;
        }

        if (!_isDragSelecting || sender is not Canvas canvas) return;
        _isDragSelecting = false;

        if (_dragSelectBorder != null)
        {
            _dragSelectBorder.Visibility = Visibility.Collapsed;
            canvas.Children.Remove(_dragSelectBorder);
        }

        Point endPt = e.GetPosition(canvas);
        double minY = Math.Min(_dragSelectStartPoint.Y, endPt.Y);
        double maxY = Math.Max(_dragSelectStartPoint.Y, endPt.Y);

        if ((maxY - minY) >= 12.0)
        {
            // 依据像素高度转换为具体时段并以 15 分钟为步进吸附
            double startHourDec = minY / HourHeight;
            double endHourDec = maxY / HourHeight;

            int startTotalMinutes = (int)(startHourDec * 60);
            int endTotalMinutes = (int)(endHourDec * 60);

            // 吸附 15 分钟
            startTotalMinutes = (startTotalMinutes / 15) * 15;
            endTotalMinutes = Math.Max(startTotalMinutes + 30, ((endTotalMinutes + 14) / 15) * 15);

            DateTime st = _dragSelectDate.Date.AddMinutes(startTotalMinutes);
            DateTime et = _dragSelectDate.Date.AddMinutes(endTotalMinutes);

            OpenEventCreateModalWithRange(_dragSelectDate, st, et);
        }
    }

    // =========================================================================
    // ================= MODULE 2: 待办敏捷池拖拽投放 (DRAG & DROP) =============
    // =========================================================================

    private void OnWeekCanvasDragOver(object sender, DragEventArgs e)
    {
        if (e.Data.GetDataPresent("ScheduleId"))
        {
            e.Effects = DragDropEffects.Move;
            e.Handled = true;
        }
    }

    private void OnWeekCanvasDrop(object sender, DragEventArgs e)
    {
        if (!e.Data.GetDataPresent("ScheduleId")) return;
        string? scheduleId = e.Data.GetData("ScheduleId") as string;
        if (string.IsNullOrEmpty(scheduleId)) return;

        Point pt = e.GetPosition(WeekEventsCanvas);
        double totalWidth = WeekEventsCanvas.ActualWidth;
        if (totalWidth <= 0) totalWidth = 700;
        double colWidth = totalWidth / 7.0;

        int col = (int)(pt.X / colWidth);
        col = Math.Clamp(col, 0, 6);

        DateTime monday = GetMondayOfWeek(_currentDate);
        DateTime targetDate = monday.AddDays(col);

        double hourDec = pt.Y / HourHeight;
        int startMinutes = ((int)(hourDec * 60) / 30) * 30; // 30分钟吸附
        DateTime start = targetDate.Date.AddMinutes(startMinutes);
        DateTime end = start.AddHours(1);

        PlaceBacklogOnCalendar(scheduleId, targetDate, start, end);
    }

    private void OnDayCanvasDragOver(object sender, DragEventArgs e)
    {
        if (e.Data.GetDataPresent("ScheduleId"))
        {
            e.Effects = DragDropEffects.Move;
            e.Handled = true;
        }
    }

    private void OnDayCanvasDrop(object sender, DragEventArgs e)
    {
        if (!e.Data.GetDataPresent("ScheduleId")) return;
        string? scheduleId = e.Data.GetData("ScheduleId") as string;
        if (string.IsNullOrEmpty(scheduleId)) return;

        Point pt = e.GetPosition(DayEventsCanvas);
        double hourDec = pt.Y / HourHeight;
        int startMinutes = ((int)(hourDec * 60) / 30) * 30; // 30分钟吸附
        DateTime start = _currentDate.Date.AddMinutes(startMinutes);
        DateTime end = start.AddHours(1);

        PlaceBacklogOnCalendar(scheduleId, _currentDate.Date, start, end);
    }

    private void PlaceBacklogOnCalendar(string scheduleId, DateTime targetDate, DateTime start, DateTime end)
    {
        // 规则 A & B: 加载任务并执行 DoD 质检门禁与认知负荷熔断审查
        var backlogItem = DatabaseService.GetScheduleById(scheduleId);
        if (backlogItem != null)
        {
            // 规则 A: DoD 强制质检拦截
            var (dodPassed, dodReason, suggestedDod) = SchedulerAuditEngine.InspectDoD(backlogItem);
            if (!dodPassed && backlogItem.WorkType != "REST_BUFFER")
            {
                var res = MessageBox.Show(
                    $"⛔ 待办投放 DoD 质检拦截！\n\n任务「{backlogItem.Title}」目标未量化。\n{dodReason}\n\n💡 建议验收标准 (DoD)：\n「{suggestedDod}」\n\n是否一键采纳推荐 DoD 并排入主时间轴？（选「否」则取消排期，留在待办池）",
                    "DoD 质检门禁",
                    MessageBoxButton.YesNo,
                    MessageBoxImage.Warning
                );
                if (res == MessageBoxResult.Yes)
                {
                    backlogItem.Dod = suggestedDod;
                    DatabaseService.UpsertSchedule(backlogItem);
                }
                else
                {
                    return;
                }
            }

            // 规则 B: 认知负荷 4.5h 生理上限硬性熔断
            if (backlogItem.WorkType == "DEEP_WORK")
            {
                int currentDeepMins = DatabaseService.GetTodayDeepWorkMinutes(targetDate);
                int durationMins = (int)(end - start).TotalMinutes;
                if (currentDeepMins + durationMins > SchedulerAuditEngine.MaxDailyDeepWorkMinutes)
                {
                    var res = MessageBox.Show(
                        $"🚨 认知负荷排期熔断！\n\n投放此任务后，今日深度工作将达 {(currentDeepMins + durationMins) / 60.0:F1} 小时，突破 4.5h 生理硬性上限！\n\n调度引擎已熔断拦截。是否将该任务剥离归入「次要任务延期冻结池」锁定今日不排入？",
                        "认知超载排期熔断",
                        MessageBoxButton.YesNo,
                        MessageBoxImage.Question
                    );
                    if (res == MessageBoxResult.Yes)
                    {
                        backlogItem.IsDeferred = true;
                        backlogItem.IsBacklog = false;
                        DatabaseService.UpsertSchedule(backlogItem);
                        RefreshBacklogList();
                        RefreshDeferredQueue();
                    }
                    return;
                }
            }
        }

        // 检查缓冲区域防重叠约束
        if (CheckRestBufferConflict(targetDate, start, end, scheduleId))
        {
            MessageBox.Show("⚠️ 缓冲保护排期熔断：该时段与已设置的【☕ 强制休息/缓冲】时间块发生重叠！科学研究表明，强制缓冲期禁止被侵占，请投放到其他空白时段。", "防重叠排期约束", MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }

        DatabaseService.ScheduleBacklogTask(scheduleId, start, end);

        // 规则 C: 深度工作任务投放后自动在末尾建立 15 分钟认知冷却缓冲（若空闲）
        if (backlogItem != null && backlogItem.WorkType == "DEEP_WORK")
        {
            DateTime bufferEnd = end.AddMinutes(SchedulerAuditEngine.TransitionBufferMinutes);
            if (!CheckRestBufferConflict(targetDate, end, bufferEnd, null))
            {
                var bufferBlock = new ScheduleItem
                {
                    Id = Guid.NewGuid().ToString(),
                    Title = "☕ 强制脑力恢复缓冲",
                    Description = $"紧随高强度任务「{backlogItem.Title}」的法定认知冷却期，禁止排期侵占",
                    Category = "健康",
                    Priority = "MEDIUM",
                    Status = "PENDING",
                    StartTime = end,
                    EndTime = bufferEnd,
                    EstimatedMinutes = SchedulerAuditEngine.TransitionBufferMinutes,
                    WorkType = "REST_BUFFER",
                    Dod = "离开屏幕、活动颈椎或补充水分",
                    IsBacklog = false,
                    IsDeferred = false,
                    IsDirty = true
                };
                DatabaseService.UpsertSchedule(bufferBlock);
            }
        }

        RenderAllCalendarViews();
        RefreshBacklogList();
        RefreshTimePnlData();
        UpdateCognitiveLoadQuota();
    }

    // =========================================================================
    // ================== EVENT CREATION & EDIT MODAL ==========================
    // =========================================================================

    private void OnGoogleCreateClicked(object sender, RoutedEventArgs e)
    {
        OpenEventCreateModal(_currentDate, DateTime.Now.Hour);
    }

    private void OpenEventCreateModal(DateTime date, int hour)
    {
        DateTime st = date.Date.AddHours(hour);
        DateTime et = st.AddHours(1);
        OpenEventCreateModalWithRange(date, st, et);
    }

    private void InitEventTimeCombos()
    {
        if (EventStartTimeCombo == null || EventEndTimeCombo == null) return;
        if (EventStartTimeCombo.Items.Count > 0) return;

        for (int h = 0; h < 24; h++)
        {
            EventStartTimeCombo.Items.Add($"{h:D2}:00");
            EventStartTimeCombo.Items.Add($"{h:D2}:30");
            EventEndTimeCombo.Items.Add($"{h:D2}:00");
            EventEndTimeCombo.Items.Add($"{h:D2}:30");
        }
    }

    public static bool TryParseUserTime(string input, out TimeSpan time)
    {
        time = TimeSpan.Zero;
        if (string.IsNullOrWhiteSpace(input)) return false;

        string s = input.Trim().Replace("：", ":").Replace("点半", ":30").Replace("点", ":").Replace("分", "").Replace(" ", "");
        if (s.EndsWith(":")) s = s.Substring(0, s.Length - 1);

        // 1. 标准 HH:mm 或 H:mm 或 HH:mm:ss
        if (DateTime.TryParseExact(s, new[] { "H:m", "HH:mm", "H:mm", "HH:m", "H:m:s", "HH:mm:ss" },
            System.Globalization.CultureInfo.InvariantCulture, System.Globalization.DateTimeStyles.None, out var dt))
        {
            time = dt.TimeOfDay;
            return true;
        }

        // 2. 通用 12 小时制或带 AM/PM 的解析
        if (DateTime.TryParse(input.Trim(), out var dtGeneral))
        {
            time = dtGeneral.TimeOfDay;
            return true;
        }

        // 3. 简写纯数字：例如 "9" -> 09:00, "14" -> 14:00 (避免误当成天数)
        if (int.TryParse(s, out int hour) && hour >= 0 && hour <= 24)
        {
            time = hour == 24 ? new TimeSpan(23, 59, 59) : TimeSpan.FromHours(hour);
            return true;
        }

        // 4. 四位数字：例如 "0900" -> 09:00, "1430" -> 14:30
        if (s.Length == 4 && int.TryParse(s.Substring(0, 2), out int h) && int.TryParse(s.Substring(2, 2), out int m))
        {
            if (h >= 0 && h < 24 && m >= 0 && m < 60)
            {
                time = new TimeSpan(h, m, 0);
                return true;
            }
        }

        // 5. 三位数字：例如 "930" -> 09:30
        if (s.Length == 3 && int.TryParse(s.Substring(0, 1), out int h3) && int.TryParse(s.Substring(1, 2), out int m3))
        {
            if (h3 >= 0 && h3 < 24 && m3 >= 0 && m3 < 60)
            {
                time = new TimeSpan(h3, m3, 0);
                return true;
            }
        }

        return false;
    }

    private void OnEventDatePickerChanged(object sender, SelectionChangedEventArgs e)
    {
        if (EventDatePicker != null && EventDatePicker.SelectedDate.HasValue)
        {
            _clickedSlotDateTime = EventDatePicker.SelectedDate.Value.Date;
        }
    }

    private void OnEventStartTimeComboChanged(object sender, SelectionChangedEventArgs e)
    {
        if (EventStartTimeCombo == null || EventEndTimeCombo == null) return;
        string startStr = EventStartTimeCombo.Text ?? "";
        if (EventStartTimeCombo.SelectedItem is string sel) startStr = sel;

        if (TryParseUserTime(startStr, out var startTs))
        {
            string endStr = EventEndTimeCombo.Text ?? "";
            if (EventEndTimeCombo.SelectedItem is string selEnd) endStr = selEnd;

            if (TryParseUserTime(endStr, out var endTs))
            {
                if (endTs <= startTs)
                {
                    TimeSpan newEnd = startTs.Add(TimeSpan.FromHours(1));
                    if (newEnd.TotalHours >= 24) newEnd = new TimeSpan(23, 59, 0);
                    EventEndTimeCombo.Text = $"{newEnd.Hours:D2}:{newEnd.Minutes:D2}";
                }
            }
            else
            {
                TimeSpan newEnd = startTs.Add(TimeSpan.FromHours(1));
                if (newEnd.TotalHours >= 24) newEnd = new TimeSpan(23, 59, 0);
                EventEndTimeCombo.Text = $"{newEnd.Hours:D2}:{newEnd.Minutes:D2}";
            }
        }
    }

    private void OnEventIsAllDayChecked(object sender, RoutedEventArgs e)
    {
        bool isAllDay = EventIsAllDayCheck.IsChecked == true;
        if (EventStartTimeCombo != null)
        {
            EventStartTimeCombo.IsEnabled = !isAllDay;
            EventStartTimeCombo.Opacity = isAllDay ? 0.35 : 1.0;
        }
        if (EventEndTimeCombo != null)
        {
            EventEndTimeCombo.IsEnabled = !isAllDay;
            EventEndTimeCombo.Opacity = isAllDay ? 0.35 : 1.0;
        }
    }

    private void OpenEventCreateModalWithRange(DateTime date, DateTime startTime, DateTime endTime)
    {
        _editingEventId = null;
        _clickedSlotDateTime = startTime;

        InitEventTimeCombos();

        EventModalHeaderTitle.Text = "添加日程时间块";
        EventTitleInput.Clear();
        EventActualMinutesInput.Text = "0";
        EventInterruptionMinutesInput.Text = "0";
        EventStatusCombo.SelectedIndex = 0;
        EventWorkTypeCombo.SelectedIndex = 0; // Default DEEP_WORK

        EventIsAllDayCheck.IsChecked = false;
        EventIsTentativeCheck.IsChecked = false;
        EventIsLockedCheck.IsChecked = false;
        EventRecurrenceCombo.SelectedIndex = 0;
        EventColorCombo.SelectedIndex = 0;
        OnEventIsAllDayChecked(null!, null!);

        if (EventDatePicker != null)
        {
            EventDatePicker.SelectedDate = date.Date;
        }
        if (EventStartTimeCombo != null)
        {
            EventStartTimeCombo.Text = startTime.ToString("HH:mm");
        }
        if (EventEndTimeCombo != null)
        {
            EventEndTimeCombo.Text = endTime.ToString("HH:mm");
        }

        PopulateEventGoalsCombo(null);
        RefreshEventCategoryCombo();
        UpdateEventColorPreview();

        EventDeleteBtn.Visibility = Visibility.Collapsed;
        EventSaveBtn.Content = "保存";

        EventModal.Visibility = Visibility.Visible;
        EventTitleInput.Focus();
    }

    private void OpenEventEditModal(ScheduleItem item)
    {
        _editingEventId = item.Id;
        _clickedSlotDateTime = item.StartTime;

        EventModalHeaderTitle.Text = "编辑日程时间块";
        EventTitleInput.Text = item.Title;
        EventActualMinutesInput.Text = item.ActualMinutes.ToString();
        EventInterruptionMinutesInput.Text = item.InterruptionMinutes.ToString();
        EventIsTentativeCheck.IsChecked = item.IsTentative;
        EventIsLockedCheck.IsChecked = item.IsLocked;

        // 状态选择
        for (int i = 0; i < EventStatusCombo.Items.Count; i++)
        {
            if (EventStatusCombo.Items[i] is ComboBoxItem cbi && (string)cbi.Tag == item.Status)
            {
                EventStatusCombo.SelectedIndex = i;
                break;
            }
        }

        // 负荷类型选择
        for (int i = 0; i < EventWorkTypeCombo.Items.Count; i++)
        {
            if (EventWorkTypeCombo.Items[i] is ComboBoxItem cbi && (string)cbi.Tag == item.WorkType)
            {
                EventWorkTypeCombo.SelectedIndex = i;
                break;
            }
        }

        // 全天与重复与主题色选择
        EventIsAllDayCheck.IsChecked = item.IsAllDay;
        OnEventIsAllDayChecked(null!, null!);

        int recIdx = 0;
        for (int i = 0; i < EventRecurrenceCombo.Items.Count; i++)
        {
            if (EventRecurrenceCombo.Items[i] is ComboBoxItem cbi && (string)cbi.Tag == item.Recurrence)
            {
                recIdx = i;
                break;
            }
        }
        EventRecurrenceCombo.SelectedIndex = recIdx;

        int colIdx = 0;
        if (!string.IsNullOrEmpty(item.ColorHex))
        {
            bool found = false;
            for (int i = 0; i < EventColorCombo.Items.Count; i++)
            {
                if (EventColorCombo.Items[i] is ComboBoxItem cbi && (string)cbi.Tag == item.ColorHex)
                {
                    colIdx = i;
                    found = true;
                    break;
                }
            }
            if (!found)
            {
                var customCbi = new ComboBoxItem { Content = $"🎨 自定义 ({item.ColorHex})", Tag = item.ColorHex };
                int insertPos = Math.Max(0, EventColorCombo.Items.Count - 1);
                EventColorCombo.Items.Insert(insertPos, customCbi);
                colIdx = insertPos;
            }
        }
        EventColorCombo.SelectedIndex = colIdx;
        InitEventTimeCombos();

        if (EventDatePicker != null)
        {
            EventDatePicker.SelectedDate = item.StartTime.Date;
        }
        if (EventStartTimeCombo != null)
        {
            EventStartTimeCombo.Text = item.StartTime.ToString("HH:mm");
        }
        if (EventEndTimeCombo != null)
        {
            EventEndTimeCombo.Text = item.EndTime.ToString("HH:mm");
        }

        PopulateEventGoalsCombo(item.GoalId);
        RefreshEventCategoryCombo(item.Category);
        UpdateEventColorPreview(item.ColorHex);

        EventDeleteBtn.Visibility = Visibility.Visible;
        EventSaveBtn.Content = "更新保存";
        EventModal.Visibility = Visibility.Visible;
        EventTitleInput.Focus();
    }

    private void PopulateEventGoalsCombo(string? selectedGoalId)
    {
        EventGoalCombo.Items.Clear();
        EventGoalCombo.Items.Add(new ComboBoxItem { Content = "⚠️ (无目标 - 孤立任务)", Tag = "" });

        var goals = DatabaseService.GetAllGoals();
        int selectedIndex = 0;

        for (int i = 0; i < goals.Count; i++)
        {
            var g = goals[i];
            string prefix = g.Level switch
            {
                "VISION" => "🌟 [愿景] ",
                "OBJECTIVE" => "🎯 [目标] ",
                _ => "📌 [任务] "
            };
            var item = new ComboBoxItem { Content = $"{prefix}{g.Title}", Tag = g.Id };
            EventGoalCombo.Items.Add(item);

            if (g.Id == selectedGoalId)
            {
                selectedIndex = i + 1;
            }
        }

        EventGoalCombo.SelectedIndex = selectedIndex;
    }

    private void OnCloseEventModalClicked(object sender, RoutedEventArgs e)
    {
        EventModal.Visibility = Visibility.Collapsed;
    }

    private void OnSaveEventModalClicked(object sender, RoutedEventArgs e)
    {
        string title = EventTitleInput.Text.Trim();
        if (string.IsNullOrEmpty(title))
        {
            MessageBox.Show("请输入日程任务标题！", "提示", MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }

        string workType = "DEEP_WORK";
        if (EventWorkTypeCombo.SelectedItem is ComboBoxItem workCbi && workCbi.Tag is string wt)
        {
            workType = wt;
        }

        var existingItem = !string.IsNullOrEmpty(_editingEventId) ? DatabaseService.GetScheduleById(_editingEventId) : null;
        string dod = existingItem?.Dod ?? "";
        string desc = existingItem?.Description ?? "";
        string itemSource = !string.IsNullOrEmpty(existingItem?.Source) ? existingItem.Source : ConfigService.Load().ClientSourceTag;

        string category = EventCategoryCombo.SelectedItem?.ToString()
            ?? (EventCategoryCombo.SelectedItem is ComboBoxItem cbiCat ? cbiCat.Content?.ToString() : null)
            ?? EventCategoryCombo.Text?.Trim()
            ?? "工作";
        if (string.IsNullOrWhiteSpace(category)) category = "工作";

        bool isAllDay = EventIsAllDayCheck.IsChecked == true;
        string recurrence = "NONE";
        if (EventRecurrenceCombo.SelectedItem is ComboBoxItem recCbi && recCbi.Tag is string recTag)
        {
            recurrence = recTag;
        }

        string? colorHex = null;
        if (EventColorCombo.SelectedItem is ComboBoxItem colCbi && colCbi.Tag is string colTag && !string.IsNullOrEmpty(colTag) && colTag != "CUSTOM_POPUP")
        {
            colorHex = colTag;
        }

        DateTime baseDate = EventDatePicker?.SelectedDate?.Date ?? _clickedSlotDateTime.Date;
        DateTime startTime = baseDate.AddHours(9);
        DateTime endTime = startTime.AddHours(1);

        if (isAllDay)
        {
            startTime = baseDate.Date;
            endTime = baseDate.Date.AddDays(1).AddSeconds(-1);
        }
        else
        {
            string startText = EventStartTimeCombo?.Text?.Trim() ?? "";
            string endText = EventEndTimeCombo?.Text?.Trim() ?? "";

            if (!TryParseUserTime(startText, out var tsStart))
            {
                MessageBox.Show("开始时间格式不正确！请输入如 09:00、9 或 14:30。", "时间格式提示", MessageBoxButton.OK, MessageBoxImage.Warning);
                EventStartTimeCombo?.Focus();
                return;
            }
            if (!TryParseUserTime(endText, out var tsEnd))
            {
                MessageBox.Show("结束时间格式不正确！请输入如 10:00、10 或 15:30。", "时间格式提示", MessageBoxButton.OK, MessageBoxImage.Warning);
                EventEndTimeCombo?.Focus();
                return;
            }

            startTime = baseDate.Add(tsStart);
            endTime = baseDate.Add(tsEnd);

            if (endTime <= startTime)
            {
                MessageBox.Show("日程结束时间必须晚于开始时间！", "时间逻辑提示", MessageBoxButton.OK, MessageBoxImage.Warning);
                EventEndTimeCombo?.Focus();
                return;
            }
        }

        // ================= MODULE 4: 缓冲区域禁止重叠排期规则 =================
        if (!isAllDay && CheckRestBufferConflict(baseDate, startTime, endTime, _editingEventId))
        {
            MessageBox.Show(
                "⚠️ 缓冲保护排期熔断：该时段与已设置的【☕ 强制休息/缓冲】时间块发生重叠！\n科学研究表明，强制缓冲期禁止被侵占，请调整起止时段或保留缓冲时间。",
                "防重叠排期约束",
                MessageBoxButton.OK,
                MessageBoxImage.Warning
            );
            return;
        }

        // ================= 闭环层: 课表动态投影与冲突检测 (Timetable -> Overview) =================
        if (!isAllDay)
        {
            var (hasConflict, conflictingCourse) = TimetableProjectionService.CheckScheduleCourseConflict(startTime, endTime);
            if (hasConflict && conflictingCourse != null)
            {
                var conflictRes = MessageBox.Show(
                    $"⚠️ 课表时间冲突拦截告警！\n\n您排期的时段 [{startTime:HH:mm} - {endTime:HH:mm}] 与当前课表中的课程发生重叠：\n📚 课程：{conflictingCourse.CourseName}\n⏰ 时间：{conflictingCourse.StartTime:HH:mm} - {conflictingCourse.EndTime:HH:mm}\n📍 地点：{conflictingCourse.Location}\n\n是否仍然强行排期？（建议点「否」调整时间以避开上课时段）",
                    "课表时间冲突告警",
                    MessageBoxButton.YesNo,
                    MessageBoxImage.Warning
                );
                if (conflictRes == MessageBoxResult.No)
                {
                    return;
                }
            }
        }

        // ================= MODULE 1: 认知负荷硬性上限与排期熔断预警 =================
        if (workType == "DEEP_WORK")
        {
            int currentDeepMins = DatabaseService.GetTodayDeepWorkMinutes(baseDate);
            int thisTaskMins = isAllDay ? 240 : (int)(endTime - startTime).TotalMinutes;
            if (currentDeepMins + thisTaskMins > 270) // 4.5 小时
            {
                var dialogResult = MessageBox.Show(
                    $"🚨 认知负荷过载预警：今日深度工作 (DeepWork) 已达 {currentDeepMins / 60.0:F1} 小时，若追加该任务将突破 4.5 小时硬性生理上限！\n\n是否强行排期？（建议选择「否」将此任务剥离至次要延期冻结池）",
                    "认知负荷排期熔断",
                    MessageBoxButton.YesNo,
                    MessageBoxImage.Question
                );
                if (dialogResult == MessageBoxResult.No)
                {
                    // 自动剥离至次要任务延期冻结池
                    var deferredItem = new ScheduleItem
                    {
                        Id = _editingEventId ?? Guid.NewGuid().ToString(),
                        Title = title,
                        Description = desc,
                        Dod = dod,
                        WorkType = workType,
                        Category = category,
                        Priority = "HIGH",
                        Status = "PENDING",
                        StartTime = startTime,
                        EndTime = endTime,
                        EstimatedMinutes = thisTaskMins,
                        IsAllDay = isAllDay,
                        Recurrence = recurrence,
                        ColorHex = colorHex,
                        IsDeferred = true,
                        IsBacklog = false,
                        Source = itemSource
                    };
                    DatabaseService.UpsertSchedule(deferredItem);
                    EventModal.Visibility = Visibility.Collapsed;
                    RefreshDeferredQueue();
                    RenderAllCalendarViews();
                    MessageBox.Show("✅ 已将该任务自动归入「次要任务延期冻结池」，今日锁定不排入主时间轴。", "已归入延期冻结池", MessageBoxButton.OK, MessageBoxImage.Information);
                    return;
                }
            }
        }

        string? goalId = null;
        if (EventGoalCombo.SelectedItem is ComboBoxItem goalCbi && goalCbi.Tag is string gid && !string.IsNullOrEmpty(gid))
        {
            goalId = gid;
        }

        // [防作弊校验] 实际耗时必须来源于专注时钟/HUD计时，防作弊锁定原有记录值
        int actualMinutes = 0;
        if (!string.IsNullOrEmpty(_editingEventId))
        {
            var orig = DatabaseService.GetScheduleById(_editingEventId);
            if (orig != null)
            {
                actualMinutes = orig.ActualMinutes;
            }
        }

        int interruptionMinutes = 0;
        int.TryParse(EventInterruptionMinutesInput.Text.Trim(), out interruptionMinutes);

        string status = "PENDING";
        if (EventStatusCombo.SelectedItem is ComboBoxItem statusCbi && statusCbi.Tag is string st)
        {
            status = st;
        }

        var item = new ScheduleItem
        {
            Id = _editingEventId ?? Guid.NewGuid().ToString(),
            Title = title,
            Description = desc,
            Category = category,
            Priority = "HIGH",
            Status = status,
            StartTime = startTime,
            EndTime = endTime,
            EstimatedMinutes = (int)(endTime - startTime).TotalMinutes,
            GoalId = goalId,
            WorkType = workType,
            Dod = dod,
            IsAllDay = isAllDay,
            Recurrence = recurrence,
            ColorHex = colorHex,
            ActualMinutes = actualMinutes,
            InterruptionMinutes = interruptionMinutes,
            IsDeferred = false,
            IsBacklog = false,
            Source = itemSource,
            IsTentative = EventIsTentativeCheck?.IsChecked == true,
            IsLocked = EventIsLockedCheck?.IsChecked == true
        };

        DatabaseService.UpsertSchedule(item);
        if (item.Status == "COMPLETED")
        {
            DataLineageService.OnScheduleCompleted(item);
        }
        EventModal.Visibility = Visibility.Collapsed;

        RenderAllCalendarViews();
        RefreshGoalTree();
        RefreshBacklogList();
        RefreshDeferredQueue();
        UpdateCognitiveLoadQuota();
        RefreshTimePnlData();
    }

    private bool CheckRestBufferConflict(DateTime date, DateTime start, DateTime end, string? excludeId)
    {
        var existing = DatabaseService.GetSchedulesForDate(date);
        foreach (var ev in existing)
        {
            if (ev.Id == excludeId) continue;
            // 只要其中一方是 REST_BUFFER，就绝对禁止时间段重叠
            bool isCurrentBuffer = (EventWorkTypeCombo?.SelectedItem is ComboBoxItem cbi && (string)cbi.Tag == "REST_BUFFER");
            if (ev.WorkType == "REST_BUFFER" || isCurrentBuffer)
            {
                if (start < ev.EndTime && end > ev.StartTime)
                {
                    return true;
                }
            }
        }
        return false;
    }

    private void OnDeleteEventModalClicked(object sender, RoutedEventArgs e)
    {
        if (!string.IsNullOrEmpty(_editingEventId))
        {
            DatabaseService.DeleteSchedule(_editingEventId);
        }
        EventModal.Visibility = Visibility.Collapsed;
        RenderAllCalendarViews();
        RefreshBacklogList();
        RefreshDeferredQueue();
        UpdateCognitiveLoadQuota();
        RefreshTimePnlData();
    }

    // =========================================================================
    // =========== GOOGLE CALENDAR QUICK DETAIL POPOVER (轻量卡片详情) ==========
    // =========================================================================

    private void OpenEventQuickDetail(ScheduleItem item)
    {
        _quickDetailItem = item;

        // 1. Color stripe
        Brush colorBrush = GetEventBrush(item, 255);
        QuickDetailColorStripe.Fill = colorBrush;

        // 2. WorkType badge
        string wtText = item.WorkType switch
        {
            "REST_BUFFER" => "☕ 缓冲休息",
            "SHALLOW_WORK" => "⚡ 浅层事务",
            _ => "🎯 深度工作"
        };
        QuickDetailWorkTypeText.Text = wtText;

        // 2.5 Source badge
        string srcTag = string.IsNullOrWhiteSpace(item.Source) ? ConfigService.Load().ClientSourceTag : item.Source;
        QuickDetailSourceText.Text = srcTag;

        // 3. Recurrence badge
        if (!string.IsNullOrEmpty(item.Recurrence) && item.Recurrence != "NONE")
        {
            QuickDetailRecurrenceBadge.Visibility = Visibility.Visible;
            QuickDetailRecurrenceText.Text = item.Recurrence switch
            {
                "DAILY" => "🔁 每天重复",
                "WEEKDAYS" => "🔁 工作日重复",
                "WEEKLY" => "🔁 每周重复",
                "MONTHLY" => "🔁 每月重复",
                _ => "🔁 循环"
            };
        }
        else
        {
            QuickDetailRecurrenceBadge.Visibility = Visibility.Collapsed;
        }

        // 4. Title
        QuickDetailTitleText.Text = item.Title;

        // 5. Time & Date
        if (item.IsAllDay)
        {
            QuickDetailTimeText.Text = $"{item.StartTime:yyyy年M月d日} · 全天日程";
        }
        else
        {
            int durationMins = Math.Max(1, (int)(item.EndTime - item.StartTime).TotalMinutes);
            QuickDetailTimeText.Text = $"{item.StartTime:yyyy年M月d日} · {item.StartTime:HH:mm} - {item.EndTime:HH:mm} ({durationMins}分钟)";
        }

        // 6. Linked Goal
        if (!string.IsNullOrEmpty(item.GoalId))
        {
            var goal = DatabaseService.GetGoalById(item.GoalId);
            QuickDetailGoalRow.Visibility = Visibility.Visible;
            QuickDetailGoalText.Text = goal != null ? $"关联目标: {goal.Title}" : "关联战略目标";
        }
        else
        {
            QuickDetailGoalRow.Visibility = Visibility.Collapsed;
        }

        // 7. DoD
        if (!string.IsNullOrWhiteSpace(item.Dod))
        {
            QuickDetailDodBorder.Visibility = Visibility.Visible;
            QuickDetailDodText.Text = item.Dod;
        }
        else
        {
            QuickDetailDodBorder.Visibility = Visibility.Collapsed;
        }

        // 8. Description
        if (!string.IsNullOrWhiteSpace(item.Description))
        {
            QuickDetailDescBorder.Visibility = Visibility.Visible;
            QuickDetailDescText.Text = item.Description;
        }
        else
        {
            QuickDetailDescBorder.Visibility = Visibility.Collapsed;
        }

        // 9. Metrics
        QuickDetailStatusText.Text = item.Status switch
        {
            "COMPLETED" => "已完成 ✓",
            "IN_PROGRESS" => "进行中 ⏳",
            _ => "待进行"
        };
        QuickDetailActualText.Text = $"{item.ActualMinutes} 分钟";
        QuickDetailInterruptText.Text = $"{item.InterruptionMinutes} 分钟";

        // Toggle complete button icon and tooltip
        if (QuickDetailCompleteIconPath != null)
        {
            if (item.Status == "COMPLETED")
            {
                // 撤销/重置图标 (↩)
                QuickDetailCompleteIconPath.Data = Geometry.Parse("M 9,14 L 4,9 L 9,4 M 4,9 L 14,9 C 17.5,9 20,11.5 20,15 C 20,18.5 17.5,21 14,21 L 10,21");
                QuickDetailCompleteIconPath.Stroke = new SolidColorBrush(Color.FromRgb(0x38, 0xBD, 0xF8));
            }
            else
            {
                // 完成对勾图标 (✓)
                QuickDetailCompleteIconPath.Data = Geometry.Parse("M 5,12 L 10,17 L 19,7");
                QuickDetailCompleteIconPath.Stroke = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
            }
        }
        QuickDetailCompleteBtn.ToolTip = item.Status == "COMPLETED" ? "重新标为未完成" : "标记为已完成";

        bool isFitness = item.Id.StartsWith("fitness_") || item.Category == "HEALTH" || item.Title.Contains("健身");
        if (QuickDetailGoToFitnessBtn != null)
        {
            QuickDetailGoToFitnessBtn.Visibility = isFitness ? Visibility.Visible : Visibility.Collapsed;
        }

        QuickDetailTentativeConfirmBtn.Visibility = item.IsTentative ? Visibility.Visible : Visibility.Collapsed;
        QuickDetailLockToggleBtn.Content = item.IsLocked ? "🔓 解锁日程" : "🔒 锁定日程";

        EventQuickDetailModal.Visibility = Visibility.Visible;
    }

    private void OnQuickDetailGoToFitnessClicked(object sender, RoutedEventArgs e)
    {
        EventQuickDetailModal.Visibility = Visibility.Collapsed;
        SwitchPrimaryPage("Fitness");
    }

    private void OnCloseQuickDetailClicked(object sender, RoutedEventArgs e)
    {
        EventQuickDetailModal.Visibility = Visibility.Collapsed;
        _quickDetailItem = null;
    }

    private void OnQuickDetailEditClicked(object sender, RoutedEventArgs e)
    {
        if (_quickDetailItem == null) return;
        var target = _quickDetailItem;
        EventQuickDetailModal.Visibility = Visibility.Collapsed;
        OpenEventEditModal(target);
    }

    private void OnQuickDetailToggleStatusClicked(object sender, RoutedEventArgs e)
    {
        if (_quickDetailItem == null) return;
        if (_quickDetailItem.Status == "COMPLETED")
        {
            _quickDetailItem.Status = "PENDING";
        }
        else
        {
            _quickDetailItem.Status = "COMPLETED";
            if (_quickDetailItem.ActualMinutes == 0)
            {
                _quickDetailItem.ActualMinutes = Math.Max(15, (int)(_quickDetailItem.EndTime - _quickDetailItem.StartTime).TotalMinutes);
            }
            DataLineageService.OnScheduleCompleted(_quickDetailItem);
        }
        DatabaseService.UpsertSchedule(_quickDetailItem);
        EventQuickDetailModal.Visibility = Visibility.Collapsed;
        RenderAllCalendarViews();
        RefreshTimePnlData();
        UpdateCognitiveLoadQuota();
    }

    private void OnQuickDetailDeleteClicked(object sender, RoutedEventArgs e)
    {
        if (_quickDetailItem == null) return;
        var res = MessageBox.Show($"确定删除日程「{_quickDetailItem.Title}」吗？", "删除确认", MessageBoxButton.YesNo, MessageBoxImage.Question);
        if (res == MessageBoxResult.Yes)
        {
            DatabaseService.DeleteSchedule(_quickDetailItem.Id);
            EventQuickDetailModal.Visibility = Visibility.Collapsed;
            _quickDetailItem = null;
            RenderAllCalendarViews();
            RefreshTimePnlData();
            UpdateCognitiveLoadQuota();
        }
    }

    // =========================================================================
    // ================= UPCOMING EVENT TOAST REMINDER (<15 MIN) ================
    // =========================================================================

    private void CheckUpcomingEvents()
    {
        try
        {
            DateTime now = DateTime.Now;
            DateTime lookahead = now.AddMinutes(15);

            var todayEvents = DatabaseService.GetSchedulesForDate(DateTime.Today);
            var upcoming = todayEvents.FirstOrDefault(e =>
                !e.IsAllDay &&
                e.Status != "COMPLETED" &&
                e.StartTime >= now &&
                e.StartTime <= lookahead &&
                !_notifiedUpcomingEventIds.Contains(e.Id));

            if (upcoming != null)
            {
                _notifiedUpcomingEventIds.Add(upcoming.Id);
                _upcomingAlertItem = upcoming;
                int mins = Math.Max(1, (int)(upcoming.StartTime - now).TotalMinutes);
                UpcomingEventTimeText.Text = $"🔔 即将开始 ({mins}分钟后)";
                UpcomingEventTitleText.Text = $"{upcoming.Title} ({upcoming.StartTime:HH:mm} - {upcoming.EndTime:HH:mm})";
                UpcomingEventToast.Visibility = Visibility.Visible;

                // 弹出 Windows 原生系统托盘横幅/气泡通知
                _trayIconService?.ShowNotification($"🔔 日程即将开始 ({mins}分钟后)", $"{upcoming.Title} ({upcoming.StartTime:HH:mm} - {upcoming.EndTime:HH:mm})");
            }
        }
        catch { }
    }

    private void OnUpcomingEventViewClicked(object sender, RoutedEventArgs e)
    {
        if (_upcomingAlertItem != null)
        {
            UpcomingEventToast.Visibility = Visibility.Collapsed;
            OpenEventQuickDetail(_upcomingAlertItem);
        }
    }

    private void OnUpcomingEventDismissClicked(object sender, RoutedEventArgs e)
    {
        if (_upcomingAlertItem != null)
        {
            _notifiedUpcomingEventIds.Add(_upcomingAlertItem.Id);
        }
        UpcomingEventToast.Visibility = Visibility.Collapsed;
    }

    // =========================================================================
    // ================= 极简专注胶囊 HUD 与极速捕获 / 战报交互 ===================
    // =========================================================================

    private void LaunchFocusHudForSchedule(ScheduleItem schedule, int? customDurationMinutes = null)
    {
        try
        {
            if (_activeFocusHud != null)
            {
                try { _activeFocusHud.Close(); } catch { }
                _activeFocusHud = null;
            }

            _activeFocusHud = new FocusHudWindow(schedule, () =>
            {
                RenderAllCalendarViews();
                RefreshTimePnlData();
                UpdateCognitiveLoadQuota();
                if (_activePrimaryPage == "Focus")
                {
                    RenderFocusPage();
                }
            }, customDurationMinutes);

            _activeFocusHud.Closed += (s, e) =>
            {
                _activeFocusHud = null;
                Dispatcher.Invoke(() =>
                {
                    if (WindowState == WindowState.Minimized)
                    {
                        WindowState = WindowState.Normal;
                    }
                    Show();
                    Activate();
                    Focus();
                    if (_activePrimaryPage == "Focus")
                    {
                        RenderFocusPage();
                    }
                });
            };

            _activeFocusHud.Show();
            _activeFocusHud.Activate();
            WindowState = WindowState.Minimized;
        }
        catch (Exception ex)
        {
            MessageBox.Show($"启动专注胶囊异常: {ex.Message}", "HUD 启动失败", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }

    private void OnTopQuickCaptureClicked(object sender, RoutedEventArgs e)
    {
        var win = new QuickCaptureWindow(() =>
        {
            RenderAllCalendarViews();
            RefreshBacklogList();
            RefreshGoalTree();
            UpdateCognitiveLoadQuota();
        });
        win.Show();
        win.Activate();
    }

    private void OnTopFocusHudClicked(object sender, RoutedEventArgs e)
    {
        DateTime now = DateTime.Now;
        var todaySchedules = DatabaseService.GetSchedulesForDate(DateTime.Today);
        var active = todaySchedules.FirstOrDefault(s => !s.IsAllDay && s.Status != "COMPLETED" && s.StartTime <= now && s.EndTime >= now)
                  ?? todaySchedules.FirstOrDefault(s => !s.IsAllDay && s.Status != "COMPLETED" && s.StartTime >= now)
                  ?? new ScheduleItem
                  {
                      Id = Guid.NewGuid().ToString(),
                      Title = "🚀 深度工作专注时段",
                      WorkType = "DEEP_WORK",
                      StartTime = now,
                      EndTime = now.AddMinutes(45),
                      EstimatedMinutes = 45,
                      Status = "IN_PROGRESS"
                  };

        LaunchFocusHudForSchedule(active);
    }

    private DesktopWidgetWindow? _activeDesktopWidget;

    private void OnTopDesktopWidgetClicked(object sender, RoutedEventArgs e)
    {
        try
        {
            if (_activeDesktopWidget != null && _activeDesktopWidget.IsLoaded)
            {
                _activeDesktopWidget.Activate();
                return;
            }

            _activeDesktopWidget = new DesktopWidgetWindow();
            _activeDesktopWidget.Closed += (s, ev) => _activeDesktopWidget = null;
            _activeDesktopWidget.Show();
        }
        catch (Exception ex)
        {
            MessageBox.Show($"启动桌面智能挂件异常: {ex.Message}", "桌面挂件错误", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }

    private void OnUpcomingEventStartFocusClicked(object sender, RoutedEventArgs e)
    {
        if (_upcomingAlertItem != null)
        {
            UpcomingEventToast.Visibility = Visibility.Collapsed;
            LaunchFocusHudForSchedule(_upcomingAlertItem);
        }
    }

    private void OnUpcomingEventPostponeClicked(object sender, RoutedEventArgs e)
    {
        if (_upcomingAlertItem != null)
        {
            _upcomingAlertItem.StartTime = _upcomingAlertItem.StartTime.AddMinutes(10);
            _upcomingAlertItem.EndTime = _upcomingAlertItem.EndTime.AddMinutes(10);
            DatabaseService.UpsertSchedule(_upcomingAlertItem);
            UpcomingEventToast.Visibility = Visibility.Collapsed;
            RenderAllCalendarViews();
            RefreshTimePnlData();
            MessageBox.Show($"已将「{_upcomingAlertItem.Title}」顺延 10 分钟。", "日程顺延", MessageBoxButton.OK, MessageBoxImage.Information);
        }
    }

    private void OnUpcomingEventCompleteClicked(object sender, RoutedEventArgs e)
    {
        if (_upcomingAlertItem != null)
        {
            _upcomingAlertItem.Status = "COMPLETED";
            if (_upcomingAlertItem.ActualMinutes == 0)
            {
                _upcomingAlertItem.ActualMinutes = Math.Max(15, (int)(_upcomingAlertItem.EndTime - _upcomingAlertItem.StartTime).TotalMinutes);
            }
            DatabaseService.UpsertSchedule(_upcomingAlertItem);
            DataLineageService.OnScheduleCompleted(_upcomingAlertItem);
            UpcomingEventToast.Visibility = Visibility.Collapsed;
            RenderAllCalendarViews();
            RefreshTimePnlData();
            UpdateCognitiveLoadQuota();
        }
    }

    private void OnQuickDetailStartFocusHudClicked(object sender, RoutedEventArgs e)
    {
        if (_quickDetailItem == null) return;
        EventQuickDetailModal.Visibility = Visibility.Collapsed;
        LaunchFocusHudForSchedule(_quickDetailItem);
    }

    private void OnQuickDetailConfirmTentativeClicked(object sender, RoutedEventArgs e)
    {
        if (_quickDetailItem == null) return;
        _quickDetailItem.IsTentative = false;
        DatabaseService.UpsertSchedule(_quickDetailItem);
        EventQuickDetailModal.Visibility = Visibility.Collapsed;
        RenderAllCalendarViews();
        RefreshTimePnlData();
        MessageBox.Show($"日程「{_quickDetailItem.Title}」已成功转为正式确认日程！", "日程确认", MessageBoxButton.OK, MessageBoxImage.Information);
    }

    private void OnQuickDetailToggleLockClicked(object sender, RoutedEventArgs e)
    {
        if (_quickDetailItem == null) return;
        _quickDetailItem.IsLocked = !_quickDetailItem.IsLocked;
        DatabaseService.UpsertSchedule(_quickDetailItem);
        EventQuickDetailModal.Visibility = Visibility.Collapsed;
        RenderAllCalendarViews();
        RefreshTimePnlData();
        string statusText = _quickDetailItem.IsLocked ? "已锁定坐标（禁止缩放拖动调整）" : "已解除锁定";
        MessageBox.Show($"日程「{_quickDetailItem.Title}」{statusText}。", "坐标锁定状态", MessageBoxButton.OK, MessageBoxImage.Information);
    }

    private void OnExportWeeklyAuditMarkdownClicked(object sender, RoutedEventArgs e)
    {
        try
        {
            DateTime startOfWeek = _currentDate.AddDays(-(int)(_currentDate.DayOfWeek == DayOfWeek.Sunday ? 6 : (int)_currentDate.DayOfWeek - 1)).Date;
            DateTime endOfWeek = startOfWeek.AddDays(7);
            string md = ReportingEngineService.GenerateWeeklyAuditMarkdown(startOfWeek, endOfWeek);

            Clipboard.SetText(md);
            MessageBox.Show("✅ 本周个人效能战报 Markdown 已成功复制到剪贴板！\n可直接粘贴至 Obsidian、Notion 或复盘文档中。", "导出周度战报", MessageBoxButton.OK, MessageBoxImage.Information);
        }
        catch (Exception ex)
        {
            MessageBox.Show($"导出战报失败: {ex.Message}", "错误", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }

    private void OnExportWeeklyAuditPosterClicked(object sender, RoutedEventArgs e)
    {
        try
        {
            DateTime startOfWeek = _currentDate.AddDays(-(int)(_currentDate.DayOfWeek == DayOfWeek.Sunday ? 6 : (int)_currentDate.DayOfWeek - 1)).Date;
            DateTime endOfWeek = startOfWeek.AddDays(7);

            var sfd = new Microsoft.Win32.SaveFileDialog
            {
                Title = "导出周度效能战报高清海报",
                Filter = "PNG 高清图片 (*.png)|*.png",
                FileName = $"RMF_Weekly_Audit_{startOfWeek:yyyyMMdd}.png"
            };

            if (sfd.ShowDialog() == true)
            {
                ReportingEngineService.ExportWeeklyAuditPoster(startOfWeek, endOfWeek, sfd.FileName);
                MessageBox.Show($"🎉 周度效能战报长图海报已成功生成并保存至：\n{sfd.FileName}", "海报导出成功", MessageBoxButton.OK, MessageBoxImage.Information);
            }
        }
        catch (Exception ex)
        {
            MessageBox.Show($"生成海报失败: {ex.Message}", "错误", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }

    private void OnExportFullJsonClicked(object sender, RoutedEventArgs e)
    {
        try
        {
            var sfd = new Microsoft.Win32.SaveFileDialog
            {
                Title = "导出全量通用数据包 (JSON)",
                Filter = "JSON 文件 (*.json)|*.json|所有文件 (*.*)|*.*",
                FileName = $"RMF_Full_Data_{DateTime.Today:yyyyMMdd}.json"
            };

            if (sfd.ShowDialog() == true)
            {
                DataExportService.ExportFullDataJson(sfd.FileName);
                MessageBox.Show($"✅ 全量系统数据（日程、战略目标、课表、攻坚档案）已成功导出至：\n{sfd.FileName}", "JSON 导出完成", MessageBoxButton.OK, MessageBoxImage.Information);
            }
        }
        catch (Exception ex)
        {
            MessageBox.Show($"导出 JSON 失败: {ex.Message}", "导出错误", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }

    private void OnExportFullMarkdownClicked(object sender, RoutedEventArgs e)
    {
        try
        {
            var sfd = new Microsoft.Win32.SaveFileDialog
            {
                Title = "导出全量归档文档 (Markdown)",
                Filter = "Markdown 文档 (*.md)|*.md|所有文件 (*.*)|*.*",
                FileName = $"RMF_Full_Archive_{DateTime.Today:yyyyMMdd}.md"
            };

            if (sfd.ShowDialog() == true)
            {
                DataExportService.ExportFullDataMarkdown(sfd.FileName);
                MessageBox.Show($"✅ 全量战略与日程归档 Markdown 已成功导出至：\n{sfd.FileName}", "Markdown 导出完成", MessageBoxButton.OK, MessageBoxImage.Information);
            }
        }
        catch (Exception ex)
        {
            MessageBox.Show($"导出 Markdown 失败: {ex.Message}", "导出错误", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }

    // =========================================================================
    // ============== OFFLINE STANDARD iCALENDAR (.ics) EXPORT & IMPORT =========
    // =========================================================================

    private void OnExportIcsFileClicked(object sender, RoutedEventArgs e)
    {
        try
        {
            var sfd = new Microsoft.Win32.SaveFileDialog
            {
                Title = "导出日历到 iCalendar (.ics) 文件",
                Filter = "iCalendar 文件 (*.ics)|*.ics|所有文件 (*.*)|*.*",
                FileName = $"RMF_Calendar_{DateTime.Today:yyyyMMdd}.ics"
            };

            if (sfd.ShowDialog() == true)
            {
                var allSchedules = DatabaseService.GetAllSchedules();
                var sb = new StringBuilder();
                sb.AppendLine("BEGIN:VCALENDAR");
                sb.AppendLine("VERSION:2.0");
                sb.AppendLine("PRODID:-//RMF Personal Workspace//CN");
                sb.AppendLine("CALSCALE:GREGORIAN");
                sb.AppendLine("METHOD:PUBLISH");

                foreach (var item in allSchedules)
                {
                    if (item.IsDeferred) continue;

                    sb.AppendLine("BEGIN:VEVENT");
                    sb.AppendLine($"UID:{item.Id}@rmf.local");
                    sb.AppendLine($"SUMMARY:{EscapeIcsText(item.Title)}");

                    string desc = item.Description ?? "";
                    if (!string.IsNullOrEmpty(item.Dod))
                    {
                        desc += (string.IsNullOrEmpty(desc) ? "" : "\n") + $"[DoD] {item.Dod}";
                    }
                    if (!string.IsNullOrEmpty(desc))
                    {
                        sb.AppendLine($"DESCRIPTION:{EscapeIcsText(desc)}");
                    }

                    if (item.IsAllDay)
                    {
                        sb.AppendLine($"DTSTART;VALUE=DATE:{item.StartTime:yyyyMMdd}");
                        sb.AppendLine($"DTEND;VALUE=DATE:{item.EndTime.AddDays(1):yyyyMMdd}");
                    }
                    else
                    {
                        sb.AppendLine($"DTSTART:{item.StartTime:yyyyMMdd\\THHmmss}");
                        sb.AppendLine($"DTEND:{item.EndTime:yyyyMMdd\\THHmmss}");
                    }

                    if (!string.IsNullOrEmpty(item.Recurrence) && item.Recurrence != "NONE")
                    {
                        string freq = item.Recurrence switch
                        {
                            "DAILY" => "FREQ=DAILY",
                            "WEEKDAYS" => "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR",
                            "WEEKLY" => "FREQ=WEEKLY",
                            "MONTHLY" => "FREQ=MONTHLY",
                            _ => ""
                        };
                        if (!string.IsNullOrEmpty(freq))
                        {
                            sb.AppendLine($"RRULE:{freq}");
                        }
                    }

                    if (!string.IsNullOrEmpty(item.Category))
                    {
                        sb.AppendLine($"CATEGORIES:{EscapeIcsText(item.Category)}");
                    }

                    sb.AppendLine(item.Status == "COMPLETED" ? "STATUS:COMPLETED" : "STATUS:CONFIRMED");
                    sb.AppendLine("END:VEVENT");
                }

                sb.AppendLine("END:VCALENDAR");

                File.WriteAllText(sfd.FileName, sb.ToString(), Encoding.UTF8);
                IcsTransferStatusText.Text = $"✅ 成功导出 {allSchedules.Count} 项日程至 {System.IO.Path.GetFileName(sfd.FileName)}！";
                MessageBox.Show($"成功导出 {allSchedules.Count} 项日程至标准 .ics 文件！可在 Apple Calendar、Outlook 或 Google Calendar 中直接导入打开。", "导出成功", MessageBoxButton.OK, MessageBoxImage.Information);
            }
        }
        catch (Exception ex)
        {
            MessageBox.Show($"导出 .ics 失败: {ex.Message}", "错误", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }

    private void OnImportIcsFileClicked(object sender, RoutedEventArgs e)
    {
        try
        {
            var ofd = new Microsoft.Win32.OpenFileDialog
            {
                Title = "从 iCalendar (.ics) 文件导入日程",
                Filter = "iCalendar 文件 (*.ics)|*.ics|所有文件 (*.*)|*.*"
            };

            if (ofd.ShowDialog() == true)
            {
                string content = File.ReadAllText(ofd.FileName, Encoding.UTF8);
                int importedCount = ParseAndImportIcs(content);

                RenderAllCalendarViews();
                RefreshTimePnlData();
                UpdateCognitiveLoadQuota();

                IcsTransferStatusText.Text = $"✅ 成功从 {System.IO.Path.GetFileName(ofd.FileName)} 导入 {importedCount} 项日程！";
                MessageBox.Show($"成功导入 {importedCount} 项日程！已实时更新到日历主网格中。", "导入完成", MessageBoxButton.OK, MessageBoxImage.Information);
            }
        }
        catch (Exception ex)
        {
            MessageBox.Show($"导入 .ics 失败: {ex.Message}", "错误", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }

    private static string EscapeIcsText(string text)
    {
        if (string.IsNullOrEmpty(text)) return "";
        return text.Replace("\\", "\\\\").Replace(";", "\\;").Replace(",", "\\,").Replace("\r\n", "\\n").Replace("\n", "\\n");
    }

    private static string UnescapeIcsText(string text)
    {
        if (string.IsNullOrEmpty(text)) return "";
        return text.Replace("\\n", "\n").Replace("\\,", ",").Replace("\\;", ";").Replace("\\\\", "\\");
    }

    private int ParseAndImportIcs(string icsContent)
    {
        var lines = icsContent.Split(new[] { "\r\n", "\r", "\n" }, StringSplitOptions.None);
        int imported = 0;
        bool inEvent = false;

        string? summary = null;
        string? description = null;
        DateTime? dtStart = null;
        DateTime? dtEnd = null;
        bool isAllDay = false;
        string recurrence = "NONE";
        string category = "外部导入";

        foreach (var rawLine in lines)
        {
            string line = rawLine.Trim();
            if (line == "BEGIN:VEVENT")
            {
                inEvent = true;
                summary = null;
                description = null;
                dtStart = null;
                dtEnd = null;
                isAllDay = false;
                recurrence = "NONE";
                category = "外部导入";
            }
            else if (line == "END:VEVENT" && inEvent)
            {
                inEvent = false;
                if (!string.IsNullOrWhiteSpace(summary) && dtStart.HasValue)
                {
                    DateTime start = dtStart.Value;
                    DateTime end = dtEnd.HasValue && dtEnd.Value > start ? dtEnd.Value : start.AddHours(1);

                    var item = new ScheduleItem
                    {
                        Id = Guid.NewGuid().ToString(),
                        Title = summary,
                        Description = description ?? "",
                        StartTime = start,
                        EndTime = end,
                        IsAllDay = isAllDay,
                        Recurrence = recurrence,
                        Category = category,
                        Priority = "MEDIUM",
                        Status = "PENDING",
                        WorkType = "SHALLOW_WORK",
                        EstimatedMinutes = (int)(end - start).TotalMinutes,
                        Source = "📁 .ics导入",
                        IsDirty = true
                    };
                    DatabaseService.UpsertSchedule(item);
                    imported++;
                }
            }
            else if (inEvent)
            {
                if (line.StartsWith("SUMMARY:", StringComparison.OrdinalIgnoreCase))
                {
                    summary = UnescapeIcsText(line.Substring(8).Trim());
                }
                else if (line.StartsWith("DESCRIPTION:", StringComparison.OrdinalIgnoreCase))
                {
                    description = UnescapeIcsText(line.Substring(12).Trim());
                }
                else if (line.StartsWith("DTSTART", StringComparison.OrdinalIgnoreCase))
                {
                    int colonIdx = line.IndexOf(':');
                    if (colonIdx > 0)
                    {
                        string dateStr = line.Substring(colonIdx + 1).Trim();
                        isAllDay = line.Contains("VALUE=DATE");
                        dtStart = ParseIcsDateTime(dateStr);
                    }
                }
                else if (line.StartsWith("DTEND", StringComparison.OrdinalIgnoreCase))
                {
                    int colonIdx = line.IndexOf(':');
                    if (colonIdx > 0)
                    {
                        string dateStr = line.Substring(colonIdx + 1).Trim();
                        dtEnd = ParseIcsDateTime(dateStr);
                    }
                }
                else if (line.StartsWith("RRULE:", StringComparison.OrdinalIgnoreCase))
                {
                    string rrule = line.Substring(6).ToUpperInvariant();
                    if (rrule.Contains("FREQ=DAILY")) recurrence = "DAILY";
                    else if (rrule.Contains("BYDAY=MO,TU,WE,TH,FR")) recurrence = "WEEKDAYS";
                    else if (rrule.Contains("FREQ=WEEKLY")) recurrence = "WEEKLY";
                    else if (rrule.Contains("FREQ=MONTHLY")) recurrence = "MONTHLY";
                }
                else if (line.StartsWith("CATEGORIES:", StringComparison.OrdinalIgnoreCase))
                {
                    category = UnescapeIcsText(line.Substring(11).Trim());
                }
            }
        }

        return imported;
    }

    private static DateTime? ParseIcsDateTime(string dateStr)
    {
        try
        {
            if (dateStr.Length == 8) // yyyyMMdd
            {
                if (DateTime.TryParseExact(dateStr, "yyyyMMdd", CultureInfo.InvariantCulture, DateTimeStyles.None, out var d))
                    return d;
            }
            else if (dateStr.EndsWith("Z", StringComparison.OrdinalIgnoreCase))
            {
                string clean = dateStr.TrimEnd('Z');
                if (DateTime.TryParseExact(clean, "yyyyMMddTHHmmss", CultureInfo.InvariantCulture, DateTimeStyles.AssumeUniversal, out var dtUtc))
                    return dtUtc.ToLocalTime();
            }
            else
            {
                if (DateTime.TryParseExact(dateStr, "yyyyMMddTHHmmss", CultureInfo.InvariantCulture, DateTimeStyles.None, out var dt))
                    return dt;
            }
        }
        catch { }
        return null;
    }

    // =========================================================================
    // ================= MODULE 2: 目标层级树 (GOAL TREE OKR) ===================
    // =========================================================================

    private void RefreshGoalTree()
    {
        GoalTreePanel.Children.Clear();
        var roots = DatabaseService.GetGoalsHierarchy();

        if (roots.Count == 0)
        {
            GoalTreePanel.Children.Add(new TextBlock
            {
                Text = "暂无战略目标，点击上方「+ 目标」建立层级",
                FontSize = 11,
                Foreground = new SolidColorBrush(Color.FromRgb(0x71, 0x71, 0x7A)),
                Margin = new Thickness(4, 4, 0, 4)
            });
            return;
        }

        foreach (var root in roots)
        {
            RenderGoalNode(root, 0);
        }
    }

    private void RenderGoalNode(GoalItem goal, int depth)
    {
        var nodeBorder = new Border
        {
            Background = ThemeService.IsCurrentDark 
                ? new SolidColorBrush(Color.FromArgb(depth == 0 ? (byte)30 : (byte)15, 0xFF, 0xFF, 0xFF))
                : new SolidColorBrush(Color.FromArgb(depth == 0 ? (byte)25 : (byte)12, 0x00, 0x00, 0x00)),
            CornerRadius = new CornerRadius(4),
            Padding = new Thickness(6, 4, 6, 4),
            Margin = new Thickness(depth * 14, 0, 0, 4),
            Cursor = Cursors.Hand
        };

        var sp = new StackPanel();

        var titleRow = new Grid();
        titleRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        titleRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        string icon = goal.Level switch
        {
            "VISION" => "🌟 ",
            "ANNUAL" => "🚩 ",
            "OBJECTIVE" => "🎯 ",
            "MONTHLY" => "🔷 ",
            "KEY_TASK" => "📌 ",
            _ => "📌 "
        };

        var titleText = new TextBlock
        {
            Text = icon + goal.Title,
            FontSize = depth == 0 ? 12 : 11,
            FontWeight = depth == 0 ? FontWeights.Bold : FontWeights.Normal,
            Foreground = depth == 0 ? new SolidColorBrush(Color.FromRgb(0xF5, 0x9E, 0x0B)) : (depth == 1 ? new SolidColorBrush(Color.FromRgb(0x8A, 0xB4, 0xF8)) : new SolidColorBrush(Color.FromRgb(0xC0, 0x84, 0xFC))),
            TextTrimming = TextTrimming.CharacterEllipsis
        };
        Grid.SetColumn(titleText, 0);
        titleRow.Children.Add(titleText);

        var pctText = new TextBlock
        {
            Text = $"{goal.Progress}%",
            FontSize = 10,
            Foreground = ThemeService.CurrentSecondaryTextBrush,
            Margin = new Thickness(4, 0, 0, 0)
        };
        Grid.SetColumn(pctText, 1);
        titleRow.Children.Add(pctText);

        sp.Children.Add(titleRow);

        nodeBorder.Child = sp;
        nodeBorder.MouseLeftButtonUp += (s, e) =>
        {
            e.Handled = true;
            OpenGoalEditModal(goal);
        };

        GoalTreePanel.Children.Add(nodeBorder);

        foreach (var child in goal.Children)
        {
            RenderGoalNode(child, depth + 1);
        }
    }

    private void OnAddGoalClicked(object sender, RoutedEventArgs e)
    {
        _editingGoalId = null;
        GoalModalHeaderTitle.Text = "🎯 创建战略层级目标";
        GoalTitleInput.Clear();
        GoalLevelCombo.SelectedIndex = 1; // Default OBJECTIVE
        GoalQuarterInput.Text = "2026-Q4";
        PopulateGoalParentCombo(null);
        GoalDeleteBtn.Visibility = Visibility.Collapsed;
        GoalModal.Visibility = Visibility.Visible;
        GoalTitleInput.Focus();
    }

    private void OpenGoalEditModal(GoalItem goal)
    {
        _editingGoalId = goal.Id;
        GoalModalHeaderTitle.Text = "🎯 编辑战略层级目标";
        GoalTitleInput.Text = goal.Title;
        GoalQuarterInput.Text = goal.Quarter;

        for (int i = 0; i < GoalLevelCombo.Items.Count; i++)
        {
            if (GoalLevelCombo.Items[i] is ComboBoxItem cbi && (string)cbi.Tag == goal.Level)
            {
                GoalLevelCombo.SelectedIndex = i;
                break;
            }
        }

        PopulateGoalParentCombo(goal.ParentId);
        GoalDeleteBtn.Visibility = Visibility.Visible;
        GoalModal.Visibility = Visibility.Visible;
    }

    private void PopulateGoalParentCombo(string? selectedParentId)
    {
        GoalParentCombo.Items.Clear();
        GoalParentCombo.Items.Add(new ComboBoxItem { Content = "(顶级 - 无父级)", Tag = "" });

        var allGoals = DatabaseService.GetAllGoals();
        int selectedIndex = 0;

        for (int i = 0; i < allGoals.Count; i++)
        {
            var g = allGoals[i];
            if (g.Id == _editingGoalId) continue; // 避免自环
            var item = new ComboBoxItem { Content = $"[{g.Level}] {g.Title}", Tag = g.Id };
            GoalParentCombo.Items.Add(item);

            if (g.Id == selectedParentId)
            {
                selectedIndex = GoalParentCombo.Items.Count - 1;
            }
        }

        GoalParentCombo.SelectedIndex = selectedIndex;
    }

    private void OnGoalLevelChanged(object sender, SelectionChangedEventArgs e)
    {
        if (GoalParentLabel == null || GoalParentCombo == null) return;
        if (GoalLevelCombo.SelectedItem is ComboBoxItem cbi && (string)cbi.Tag == "VISION")
        {
            GoalParentLabel.Visibility = Visibility.Collapsed;
            GoalParentCombo.Visibility = Visibility.Collapsed;
        }
        else
        {
            GoalParentLabel.Visibility = Visibility.Visible;
            GoalParentCombo.Visibility = Visibility.Visible;
        }
    }

    private void OnCloseGoalModalClicked(object sender, RoutedEventArgs e)
    {
        GoalModal.Visibility = Visibility.Collapsed;
    }

    private void OnSaveGoalClicked(object sender, RoutedEventArgs e)
    {
        string title = GoalTitleInput.Text.Trim();
        if (string.IsNullOrEmpty(title))
        {
            MessageBox.Show("请输入目标名称！", "提示", MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }

        string level = "OBJECTIVE";
        if (GoalLevelCombo.SelectedItem is ComboBoxItem levelCbi && levelCbi.Tag is string l)
        {
            level = l;
        }

        string? parentId = null;
        if (GoalParentCombo.SelectedItem is ComboBoxItem parentCbi && parentCbi.Tag is string pid && !string.IsNullOrEmpty(pid))
        {
            parentId = pid;
        }

        var goal = new GoalItem
        {
            Id = _editingGoalId ?? Guid.NewGuid().ToString(),
            Title = title,
            Level = level,
            ParentId = parentId,
            Quarter = GoalQuarterInput.Text.Trim(),
            Progress = 50
        };

        DatabaseService.UpsertGoal(goal);
        GoalModal.Visibility = Visibility.Collapsed;
        RefreshGoalTree();
        RenderAllCalendarViews();
    }

    private void OnDeleteGoalClicked(object sender, RoutedEventArgs e)
    {
        if (!string.IsNullOrEmpty(_editingGoalId))
        {
            DatabaseService.DeleteGoal(_editingGoalId);
        }
        GoalModal.Visibility = Visibility.Collapsed;
        RefreshGoalTree();
        RenderAllCalendarViews();
    }

    // =========================================================================
    // ================= MODULE 2: 待办任务敏捷池 (BACKLOG DRAWER) =============
    // =========================================================================

    private void RefreshBacklogList()
    {
        BacklogItemsPanel.Children.Clear();
        var list = DatabaseService.GetBacklogSchedules();
        BacklogCountText.Text = list.Count.ToString();

        if (list.Count == 0)
        {
            BacklogEmptyText.Visibility = Visibility.Visible;
            return;
        }
        BacklogEmptyText.Visibility = Visibility.Collapsed;

        foreach (var task in list)
        {
            var card = CreateBacklogCard(task);
            BacklogItemsPanel.Children.Add(card);
        }
    }

    private UIElement CreateBacklogCard(ScheduleItem item)
    {
        var border = new Border
        {
            Background = ThemeService.CurrentCardBrush,
            BorderBrush = ThemeService.CurrentBorderBrush,
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(6),
            Padding = new Thickness(8, 6, 8, 6),
            Margin = new Thickness(0, 0, 0, 6),
            Cursor = Cursors.SizeAll,
            ToolTip = "按住拖拽至右侧主时间轴空白槽，或点击右侧「排期」按钮"
        };

        // 拖拽源事件处理 (带有位移阈值检测，避免点击按钮误触拖拽)
        Point dragStartPoint = new Point();
        border.PreviewMouseLeftButtonDown += (s, e) =>
        {
            dragStartPoint = e.GetPosition(null);
        };
        border.PreviewMouseMove += (s, e) =>
        {
            if (e.LeftButton == MouseButtonState.Pressed)
            {
                Point currentPoint = e.GetPosition(null);
                Vector diff = dragStartPoint - currentPoint;
                if (Math.Abs(diff.X) > SystemParameters.MinimumHorizontalDragDistance ||
                    Math.Abs(diff.Y) > SystemParameters.MinimumVerticalDragDistance)
                {
                    var data = new DataObject("ScheduleId", item.Id);
                    DragDrop.DoDragDrop(border, data, DragDropEffects.Move);
                }
            }
        };

        var grid = new Grid();
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        var sp = new StackPanel { Margin = new Thickness(0, 0, 8, 0) };
        sp.Children.Add(new TextBlock
        {
            Text = item.Title,
            FontSize = 11,
            FontWeight = FontWeights.SemiBold,
            Foreground = ThemeService.CurrentTextBrush,
            TextTrimming = TextTrimming.CharacterEllipsis
        });

        string metaText = $"{item.EstimatedMinutes}m · {item.Priority}";
        if (!string.IsNullOrEmpty(item.Dod)) metaText += $" · DoD已设定";
        sp.Children.Add(new TextBlock
        {
            Text = metaText,
            FontSize = 10,
            Foreground = ThemeService.CurrentSecondaryTextBrush,
            Margin = new Thickness(0, 2, 0, 0)
        });

        // 延期惩罚与滚雪球告警 (Rolled-Over Warnings)
        if (item.PostponeCount >= 3)
        {
            sp.Children.Add(new TextBlock
            {
                Text = $"⚠️ 滚雪球警报: 连续延期 {item.PostponeCount} 次",
                FontSize = 9.5,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(Color.FromRgb(0xEF, 0x44, 0x44)),
                Margin = new Thickness(0, 2, 0, 0)
            });
        }

        Grid.SetColumn(sp, 0);
        grid.Children.Add(sp);

        var scheduleBtn = new Button
        {
            Content = "⚡ 智能排期",
            Background = new SolidColorBrush(Color.FromRgb(0x1A, 0x73, 0xE8)),
            Foreground = Brushes.White,
            BorderThickness = new Thickness(0),
            Padding = new Thickness(6, 2, 6, 2),
            FontSize = 10,
            Cursor = Cursors.Hand,
            ToolTip = "自动检索空白时隙，规避课程与已有日程冲突，并附带 10m 缓冲垫",
            VerticalAlignment = VerticalAlignment.Center
        };
        scheduleBtn.Click += (s, e) =>
        {
            e.Handled = true;
            var (success, st, et, msg) = AutoSchedulingService.AutoSchedule(item, _currentDate, "ALL_DAY", 10);
            if (success)
            {
                RenderAllCalendarViews();
                RefreshBacklogList();
                RefreshTimePnlData();
                UpdateCognitiveLoadQuota();
                MessageBox.Show(msg, "智能排期成功", MessageBoxButton.OK, MessageBoxImage.Information);
            }
            else
            {
                MessageBox.Show(msg, "排期提示", MessageBoxButton.OK, MessageBoxImage.Warning);
            }
        };
        Grid.SetColumn(scheduleBtn, 1);
        grid.Children.Add(scheduleBtn);

        border.Child = grid;
        return border;
    }

    private void OnAddBacklogClicked(object sender, RoutedEventArgs e)
    {
        BacklogTitleInput.Clear();
        BacklogDodInput.Clear();
        BacklogMinutesInput.Text = "45";
        BacklogPriorityCombo.SelectedIndex = 1;

        BacklogGoalCombo.Items.Clear();
        BacklogGoalCombo.Items.Add(new ComboBoxItem { Content = "(无目标)", Tag = "" });
        var goals = DatabaseService.GetAllGoals();
        foreach (var g in goals)
        {
            BacklogGoalCombo.Items.Add(new ComboBoxItem { Content = $"[{g.Level}] {g.Title}", Tag = g.Id });
        }
        BacklogGoalCombo.SelectedIndex = 0;

        BacklogModal.Visibility = Visibility.Visible;
        BacklogTitleInput.Focus();
    }

    private void OnCloseBacklogModalClicked(object sender, RoutedEventArgs e)
    {
        BacklogModal.Visibility = Visibility.Collapsed;
    }

    private void OnSaveBacklogClicked(object sender, RoutedEventArgs e)
    {
        string title = BacklogTitleInput.Text.Trim();
        if (string.IsNullOrEmpty(title))
        {
            MessageBox.Show("请输入任务名称！", "提示", MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }

        int mins = 45;
        int.TryParse(BacklogMinutesInput.Text.Trim(), out mins);

        string priority = "MEDIUM";
        if (BacklogPriorityCombo.SelectedItem is ComboBoxItem pCbi && pCbi.Tag is string p) priority = p;

        string? goalId = null;
        if (BacklogGoalCombo.SelectedItem is ComboBoxItem gCbi && gCbi.Tag is string g && !string.IsNullOrEmpty(g)) goalId = g;

        var task = new ScheduleItem
        {
            Id = Guid.NewGuid().ToString(),
            Title = title,
            Dod = BacklogDodInput.Text.Trim(),
            EstimatedMinutes = mins,
            Priority = priority,
            GoalId = goalId,
            WorkType = "DEEP_WORK",
            Category = "工作",
            IsBacklog = true,
            IsDeferred = false,
            Status = "PENDING",
            StartTime = DateTime.MinValue,
            EndTime = DateTime.MinValue,
            Source = ConfigService.Load().ClientSourceTag
        };

        DatabaseService.UpsertSchedule(task);
        BacklogModal.Visibility = Visibility.Collapsed;
        RefreshBacklogList();
        UpdateSyncStatusBadge();
    }

    // =========================================================================
    // ================= ENGINE TELEMETRY & QUOTA STUBS ========================
    // =========================================================================

    private void UpdateCognitiveLoadQuota() { }
    private void UpdateOptimismMultiplier() { }
    private void RefreshTimePnlData() { }
    private void RefreshExpenseRecords() { }
    private void RefreshDeferredQueue() { }
    private void UpdateSyncStatusBadge() { }

    // =========================================================================
    // ================= MODULE 5: 客户端来源标识与增量同步遥测 ==================
    // =========================================================================

    private void OnSaveClientSourceTagClicked(object sender, RoutedEventArgs e)
    {
        string newTag = ClientSourceTagInput.Text.Trim();
        if (string.IsNullOrWhiteSpace(newTag))
        {
            newTag = "💻 桌面端";
            ClientSourceTagInput.Text = newTag;
        }

        var config = ConfigService.Load();
        config.ClientSourceTag = newTag;
        ConfigService.Save(config);

        ClientSourceTagStatusText.Text = $"✅ 客户端来源标签已更新为【{newTag}】！";
        RenderAllCalendarViews();
    }

    // =========================================================================
    // ======================= COLOR MAPPINGS & FILTERS ========================
    // =========================================================================

    private static readonly (string bg, string border)[] GoogleColorPalette = new[]
    {
        ("#1A73E8", "#185ABC"), // Google Blue
        ("#0B8043", "#076033"), // Google Green
        ("#8E24AA", "#6A1B9A"), // Google Purple
        ("#F4511E", "#D84315"), // Google Amber
        ("#D50000", "#B71C1C"), // Google Red
        ("#00838F", "#006064"), // Google Cyan
        ("#00897B", "#004D40"), // Google Teal
        ("#D81B60", "#880E4F"), // Google Pink
        ("#3949AB", "#1A237E"), // Google Indigo
        ("#F6BF26", "#F4B400"), // Google Yellow
    };

    private static (Brush bg, Brush border, Brush text) GetGoogleEventColors(string category)
    {
        if (string.IsNullOrWhiteSpace(category))
        {
            return (new SolidColorBrush(Color.FromRgb(0x1A, 0x73, 0xE8)), new SolidColorBrush(Color.FromRgb(0x18, 0x5A, 0xBC)), Brushes.White);
        }

        int hash = Math.Abs(category.Trim().GetHashCode());
        var colorPair = GoogleColorPalette[hash % GoogleColorPalette.Length];
        var bg = (SolidColorBrush)new BrushConverter().ConvertFrom(colorPair.bg)!;
        var border = (SolidColorBrush)new BrushConverter().ConvertFrom(colorPair.border)!;
        return (bg, border, Brushes.White);
    }

    private bool MatchesFilter(ScheduleItem item)
    {
        string cat = string.IsNullOrWhiteSpace(item.Category) ? "未分类" : item.Category.Trim();
        if (_disabledCategoryFilters.Contains(cat)) return false;

        if (!string.IsNullOrWhiteSpace(_searchKeyword))
        {
            bool inTitle = item.Title.Contains(_searchKeyword, StringComparison.OrdinalIgnoreCase);
            bool inDesc = item.Description.Contains(_searchKeyword, StringComparison.OrdinalIgnoreCase);
            bool inDod = item.Dod.Contains(_searchKeyword, StringComparison.OrdinalIgnoreCase);
            return inTitle || inDesc || inDod;
        }

        return true;
    }

    private void RenderMyCalendarsList()
    {
        MyCalendarsCategoryListPanel.Children.Clear();

        var categories = DatabaseService.GetDistinctCategories();
        if (categories.Count == 0)
        {
            MyCalendarsEmptyText.Visibility = Visibility.Visible;
            return;
        }

        MyCalendarsEmptyText.Visibility = Visibility.Collapsed;

        foreach (var cat in categories)
        {
            var colors = GetGoogleEventColors(cat);
            var cb = new CheckBox
            {
                IsChecked = !_disabledCategoryFilters.Contains(cat),
                Foreground = ThemeService.CurrentTextBrush,
                FontSize = 12,
                Margin = new Thickness(0, 0, 0, 6),
                Cursor = Cursors.Hand
            };

            var sp = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };
            var colorSquare = new Border
            {
                Width = 10,
                Height = 10,
                CornerRadius = new CornerRadius(2),
                Background = colors.bg,
                Margin = new Thickness(0, 0, 8, 0),
                VerticalAlignment = VerticalAlignment.Center
            };
            var label = new TextBlock
            {
                Text = cat,
                FontSize = 12,
                Foreground = ThemeService.CurrentTextBrush,
                VerticalAlignment = VerticalAlignment.Center
            };

            sp.Children.Add(colorSquare);
            sp.Children.Add(label);
            cb.Content = sp;

            string capturedCat = cat;
            cb.Checked += (s, e) =>
            {
                _disabledCategoryFilters.Remove(capturedCat);
                RenderCalendarCanvasOnly();
            };
            cb.Unchecked += (s, e) =>
            {
                _disabledCategoryFilters.Add(capturedCat);
                RenderCalendarCanvasOnly();
            };

            MyCalendarsCategoryListPanel.Children.Add(cb);
        }
    }

    private void RenderCalendarCanvasOnly()
    {
        switch (_currentView)
        {
            case "Day":
                RenderDayEvents();
                break;
            case "Week":
                RenderWeekEvents();
                break;
            case "Month":
                RenderMonthGrid();
                break;
            case "Year":
                RenderYearGrid();
                break;
            case "Custom":
                RenderCustomEvents();
                break;
            case "Agenda":
                RenderAgendaList();
                break;
        }
    }

    private void OnSearchEventsTextChanged(object sender, TextChangedEventArgs e)
    {
        _searchKeyword = SearchEventsInput.Text.Trim();
        ClearSearchBtn.Visibility = string.IsNullOrEmpty(_searchKeyword) ? Visibility.Collapsed : Visibility.Visible;
        RenderCalendarCanvasOnly();
        UpdateSearchResultsPopup();
    }

    private void OnClearSearchClicked(object sender, RoutedEventArgs e)
    {
        SearchEventsInput.Clear();
        SearchResultsPopup.IsOpen = false;
        RenderCalendarCanvasOnly();
    }

    private void UpdateSearchResultsPopup()
    {
        if (string.IsNullOrWhiteSpace(_searchKeyword) || _searchKeyword.Length < 1)
        {
            SearchResultsPopup.IsOpen = false;
            return;
        }

        SearchResultsListPanel.Children.Clear();
        var allEvents = DatabaseService.GetAllSchedules();
        var matched = allEvents
            .Where(MatchesFilter)
            .OrderByDescending(x => x.StartTime)
            .Take(8)
            .ToList();

        if (matched.Count == 0)
        {
            SearchResultsEmptyText.Visibility = Visibility.Visible;
        }
        else
        {
            SearchResultsEmptyText.Visibility = Visibility.Collapsed;
            foreach (var item in matched)
            {
                var rowBorder = new Border
                {
                    Background = ThemeService.CurrentControlBrush,
                    BorderBrush = ThemeService.CurrentBorderBrush,
                    BorderThickness = new Thickness(1),
                    CornerRadius = new CornerRadius(6),
                    Padding = new Thickness(10, 6, 10, 6),
                    Margin = new Thickness(0, 0, 0, 4),
                    Cursor = Cursors.Hand
                };

                var rowGrid = new Grid();
                rowGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                rowGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

                var leftSp = new StackPanel { Margin = new Thickness(0, 0, 8, 0) };
                leftSp.Children.Add(new TextBlock
                {
                    Text = item.Title,
                    FontSize = 12,
                    FontWeight = FontWeights.SemiBold,
                    Foreground = ThemeService.CurrentTextBrush,
                    TextTrimming = TextTrimming.CharacterEllipsis
                });

                string meta = $"{item.StartTime:yyyy/MM/dd HH:mm}";
                if (!string.IsNullOrEmpty(item.Dod)) meta += $" · DoD: {item.Dod}";
                leftSp.Children.Add(new TextBlock
                {
                    Text = meta,
                    FontSize = 10,
                    Foreground = ThemeService.CurrentSecondaryTextBrush,
                    TextTrimming = TextTrimming.CharacterEllipsis,
                    Margin = new Thickness(0, 2, 0, 0)
                });
                Grid.SetColumn(leftSp, 0);
                rowGrid.Children.Add(leftSp);

                var badge = new Border
                {
                    Background = item.WorkType == "DEEP_WORK" ? new SolidColorBrush(Color.FromRgb(0x7C, 0x3A, 0xED)) : (item.WorkType == "REST_BUFFER" ? new SolidColorBrush(Color.FromRgb(0x05, 0x96, 0x69)) : new SolidColorBrush(Color.FromRgb(0x47, 0x55, 0x69))),
                    CornerRadius = new CornerRadius(4),
                    Padding = new Thickness(6, 2, 6, 2),
                    VerticalAlignment = VerticalAlignment.Center
                };
                badge.Child = new TextBlock
                {
                    Text = item.WorkType == "DEEP_WORK" ? "深度" : (item.WorkType == "REST_BUFFER" ? "缓冲" : "浅层"),
                    FontSize = 10,
                    Foreground = Brushes.White
                };
                Grid.SetColumn(badge, 1);
                rowGrid.Children.Add(badge);

                rowBorder.Child = rowGrid;
                ScheduleItem captured = item;
                rowBorder.MouseLeftButtonUp += (s, e) =>
                {
                    SearchResultsPopup.IsOpen = false;
                    _currentDate = captured.StartTime.Date;
                    _miniCalMonth = captured.StartTime.Date;
                    RenderAllCalendarViews();
                    OpenEventQuickDetail(captured);
                };

                SearchResultsListPanel.Children.Add(rowBorder);
            }
        }

        SearchResultsPopup.IsOpen = true;
    }

    private void OnTopDateHeaderClicked(object sender, MouseButtonEventArgs? e)
    {
        if (_activePrimaryPage != "Overview") return;
        GoToDatePicker.SelectedDate = _currentDate;
        GoToDateModal.Visibility = Visibility.Visible;
    }

    private void OnCloseGoToDateClicked(object sender, RoutedEventArgs e)
    {
        GoToDateModal.Visibility = Visibility.Collapsed;
    }

    private void OnConfirmGoToDateClicked(object sender, RoutedEventArgs e)
    {
        if (GoToDatePicker.SelectedDate.HasValue)
        {
            _currentDate = GoToDatePicker.SelectedDate.Value.Date;
            _miniCalMonth = _currentDate;
            RenderAllCalendarViews();
        }
        GoToDateModal.Visibility = Visibility.Collapsed;
    }

    private void OnQuickDetailDuplicateClicked(object sender, RoutedEventArgs e)
    {
        if (_quickDetailItem == null) return;
        try
        {
            var clone = new ScheduleItem
            {
                Id = Guid.NewGuid().ToString("N"),
                Title = _quickDetailItem.Title + " (副本)",
                StartTime = _quickDetailItem.StartTime,
                EndTime = _quickDetailItem.EndTime,
                WorkType = _quickDetailItem.WorkType,
                GoalId = _quickDetailItem.GoalId,
                Dod = _quickDetailItem.Dod,
                Category = _quickDetailItem.Category,
                Description = _quickDetailItem.Description,
                ColorHex = _quickDetailItem.ColorHex,
                IsAllDay = _quickDetailItem.IsAllDay,
                Recurrence = _quickDetailItem.Recurrence,
                EstimatedMinutes = _quickDetailItem.EstimatedMinutes,
                Status = "PENDING",
                Source = ConfigService.Load().ClientSourceTag
            };

            DatabaseService.AddSchedule(clone);
            EventQuickDetailModal.Visibility = Visibility.Collapsed;
            RenderAllCalendarViews();
            RefreshTimePnlData();
            UpdateCognitiveLoadQuota();
            MessageBox.Show($"已成功复制日程「{clone.Title}」！", "复制成功", MessageBoxButton.OK, MessageBoxImage.Information);
        }
        catch (Exception ex)
        {
            MessageBox.Show($"复制日程失败: {ex.Message}", "错误", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }

    private void OnWindowPreviewKeyDown(object sender, KeyEventArgs e)
    {
        // 如果焦点在输入框中，不抢占普通文字输入，只处理 Escape
        if (Keyboard.FocusedElement is TextBox or PasswordBox)
        {
            if (e.Key == Key.Escape)
            {
                Keyboard.ClearFocus();
                SearchResultsPopup.IsOpen = false;
                CloseAllModals();
                e.Handled = true;
            }
            return;
        }

        switch (e.Key)
        {
            case Key.Escape:
                SearchResultsPopup.IsOpen = false;
                CloseAllModals();
                e.Handled = true;
                break;
            case Key.T:
                OnCalTodayClicked(this, new RoutedEventArgs());
                e.Handled = true;
                break;
            case Key.D:
                OnViewDayClicked(this, new RoutedEventArgs());
                e.Handled = true;
                break;
            case Key.W:
                OnViewWeekClicked(this, new RoutedEventArgs());
                e.Handled = true;
                break;
            case Key.M:
                OnViewMonthClicked(this, new RoutedEventArgs());
                e.Handled = true;
                break;
            case Key.Y:
                OnViewYearClicked(this, new RoutedEventArgs());
                e.Handled = true;
                break;
            case Key.X:
                OnViewCustomClicked(this, new RoutedEventArgs());
                e.Handled = true;
                break;
            case Key.A:
                OnViewAgendaClicked(this, new RoutedEventArgs());
                e.Handled = true;
                break;
            case Key.C:
                OpenEventCreateModal(_currentDate, 9);
                e.Handled = true;
                break;
            case Key.J:
            case Key.P:
                OnCalPrevClicked(this, new RoutedEventArgs());
                e.Handled = true;
                break;
            case Key.K:
            case Key.N:
                OnCalNextClicked(this, new RoutedEventArgs());
                e.Handled = true;
                break;
            case Key.G:
                OnTopDateHeaderClicked(this, null);
                e.Handled = true;
                break;
            case Key.OemQuestion: // '/' 键聚焦搜索框
                SearchEventsInput.Focus();
                SearchEventsInput.SelectAll();
                e.Handled = true;
                break;
            case Key.Delete:
                if (EventQuickDetailModal.Visibility == Visibility.Visible && _quickDetailItem != null)
                {
                    OnQuickDetailDeleteClicked(this, new RoutedEventArgs());
                    e.Handled = true;
                }
                break;
        }
    }

    private void CloseAllModals()
    {
        if (RgbColorPickerModal != null && RgbColorPickerModal.Visibility == Visibility.Visible)
        {
            RgbColorPickerModal.Visibility = Visibility.Collapsed;
            return;
        }
        if (TagManagementModal != null && TagManagementModal.Visibility == Visibility.Visible)
        {
            TagManagementModal.Visibility = Visibility.Collapsed;
            return;
        }
        if (EventQuickDetailModal.Visibility == Visibility.Visible)
        {
            EventQuickDetailModal.Visibility = Visibility.Collapsed;
            return;
        }
        if (EventModal.Visibility == Visibility.Visible)
        {
            EventModal.Visibility = Visibility.Collapsed;
            return;
        }
        if (GoalModal.Visibility == Visibility.Visible)
        {
            GoalModal.Visibility = Visibility.Collapsed;
            return;
        }
        if (BacklogModal.Visibility == Visibility.Visible)
        {
            BacklogModal.Visibility = Visibility.Collapsed;
            return;
        }
        if (GoToDateModal.Visibility == Visibility.Visible)
        {
            GoToDateModal.Visibility = Visibility.Collapsed;
            return;
        }
        if (AnalyticsModal != null && AnalyticsModal.Visibility == Visibility.Visible)
        {
            AnalyticsModal.Visibility = Visibility.Collapsed;
            return;
        }
        if (SettingsModal != null && SettingsModal.Visibility == Visibility.Visible)
        {
            SettingsModal.Visibility = Visibility.Collapsed;
            return;
        }
        if (StudyTopicModal != null && StudyTopicModal.Visibility == Visibility.Visible)
        {
            StudyTopicModal.Visibility = Visibility.Collapsed;
            return;
        }
    }

    // =========================================================================
    // ================= WEEKLY TIME ANALYTICS MODAL LOGIC =====================
    // =========================================================================

    private void OnOpenAnalyticsClicked(object sender, RoutedEventArgs e)
    {
        DateTime monday = GetMondayOfWeek(_currentDate);
        DateTime sunday = monday.AddDays(6);
        AnalyticsDateRangeText.Text = $"{monday:yyyy年M月d日} - {sunday:M月d日} (本周统计)";

        var weekEvents = DatabaseService.GetSchedulesForDateRange(monday, sunday.AddDays(1));
        var validEvents = weekEvents.Where(ev => !ev.IsAllDay).ToList();

        double totalHours = validEvents.Sum(ev => (ev.EndTime - ev.StartTime).TotalHours);
        double deepHours = validEvents.Where(ev => ev.WorkType == "DEEP_WORK").Sum(ev => (ev.EndTime - ev.StartTime).TotalHours);
        double shallowHours = validEvents.Where(ev => ev.WorkType == "SHALLOW_WORK").Sum(ev => (ev.EndTime - ev.StartTime).TotalHours);
        double restHours = validEvents.Where(ev => ev.WorkType == "REST_BUFFER").Sum(ev => (ev.EndTime - ev.StartTime).TotalHours);

        AnalyticsTotalHoursText.Text = $"{totalHours:F1} h";
        AnalyticsDeepHoursText.Text = $"{deepHours:F1} h";
        AnalyticsShallowHoursText.Text = $"{shallowHours:F1} h";
        AnalyticsRestHoursText.Text = $"{restHours:F1} h";

        int totalCount = validEvents.Count;
        int completedCount = validEvents.Count(ev => ev.Status == "COMPLETED");
        int dodCount = validEvents.Count(ev => !string.IsNullOrWhiteSpace(ev.Dod));

        double completionRate = totalCount > 0 ? (completedCount * 100.0 / totalCount) : 0.0;
        double dodRate = totalCount > 0 ? (dodCount * 100.0 / totalCount) : 100.0;

        AnalyticsCompletionRateText.Text = $"{completionRate:F0}% ({completedCount}/{totalCount} 项已完成)";
        AnalyticsDodRateText.Text = $"{dodRate:F0}% ({dodCount}/{totalCount} 项设定了 DoD)";

        // 渲染分类工时条形图
        AnalyticsCategoryBarsPanel.Children.Clear();
        var catGroups = validEvents.GroupBy(ev => string.IsNullOrWhiteSpace(ev.Category) ? "未分类" : ev.Category)
                                   .Select(g => new { Category = g.Key, Hours = g.Sum(ev => (ev.EndTime - ev.StartTime).TotalHours) })
                                   .OrderByDescending(g => g.Hours)
                                   .ToList();

        if (catGroups.Count == 0)
        {
            AnalyticsCategoryBarsPanel.Children.Add(new TextBlock
            {
                Text = "本周暂无排期日程记录",
                FontSize = 11,
                Foreground = new SolidColorBrush(Color.FromRgb(0x71, 0x71, 0x7A)),
                Margin = new Thickness(0, 4, 0, 4)
            });
        }
        else
        {
            foreach (var cg in catGroups)
            {
                double pct = totalHours > 0 ? (cg.Hours / totalHours) * 100.0 : 0.0;
                var row = new Grid { Margin = new Thickness(0, 0, 0, 8) };
                row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(100) });
                row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(80) });

                var catName = new TextBlock
                {
                    Text = cg.Category,
                    FontSize = 11,
                    FontWeight = FontWeights.SemiBold,
                    Foreground = ThemeService.CurrentTextBrush,
                    VerticalAlignment = VerticalAlignment.Center,
                    TextTrimming = TextTrimming.CharacterEllipsis
                };
                Grid.SetColumn(catName, 0);

                var barContainer = new Border
                {
                    Background = ThemeService.CurrentControlBrush,
                    CornerRadius = new CornerRadius(4),
                    Height = 12,
                    VerticalAlignment = VerticalAlignment.Center,
                    Margin = new Thickness(8, 0, 8, 0)
                };
                var barFill = new Border
                {
                    Background = new SolidColorBrush(Color.FromRgb(0x1A, 0x73, 0xE8)),
                    CornerRadius = new CornerRadius(4),
                    HorizontalAlignment = HorizontalAlignment.Left,
                    Width = Math.Max(4, Math.Min(300, 300 * (pct / 100.0)))
                };
                barContainer.Child = barFill;
                Grid.SetColumn(barContainer, 1);

                var hoursText = new TextBlock
                {
                    Text = $"{cg.Hours:F1}h ({pct:F0}%)",
                    FontSize = 11,
                    Foreground = ThemeService.CurrentSecondaryTextBrush,
                    HorizontalAlignment = HorizontalAlignment.Right,
                    VerticalAlignment = VerticalAlignment.Center
                };
                Grid.SetColumn(hoursText, 2);

                row.Children.Add(catName);
                row.Children.Add(barContainer);
                row.Children.Add(hoursText);
                AnalyticsCategoryBarsPanel.Children.Add(row);
            }
        }

        AnalyticsModal.Visibility = Visibility.Visible;
    }

    private void OnCloseAnalyticsModalClicked(object sender, RoutedEventArgs e)
    {
        AnalyticsModal.Visibility = Visibility.Collapsed;
    }

    // =========================================================================
    // ===================== TOP NAVIGATION & VIEW SWITCHING ===================
    // =========================================================================

    private void OnCalTodayClicked(object sender, RoutedEventArgs e)
    {
        _currentDate = DateTime.Today;
        _miniCalMonth = DateTime.Today;
        if (_currentView == "Custom")
        {
            _customStartDate = DateTime.Today;
            _customEndDate = _customStartDate.AddDays(_customDays - 1);
        }
        RenderAllCalendarViews();
        ScrollWeekToCurrentTime();
    }

    private void OnCalPrevClicked(object sender, RoutedEventArgs e)
    {
        if (_currentView == "Day") _currentDate = _currentDate.AddDays(-1);
        else if (_currentView == "Week") _currentDate = _currentDate.AddDays(-7);
        else if (_currentView == "Month") _currentDate = _currentDate.AddMonths(-1);
        else if (_currentView == "Year") _currentDate = _currentDate.AddYears(-1);
        else if (_currentView == "Custom")
        {
            _customStartDate = _customStartDate.AddDays(-_customDays);
            _customEndDate = _customEndDate.AddDays(-_customDays);
            _currentDate = _customStartDate;
        }
        else _currentDate = _currentDate.AddDays(-7);

        _miniCalMonth = _currentDate;
        RenderAllCalendarViews();
    }

    private void OnCalNextClicked(object sender, RoutedEventArgs e)
    {
        if (_currentView == "Day") _currentDate = _currentDate.AddDays(1);
        else if (_currentView == "Week") _currentDate = _currentDate.AddDays(7);
        else if (_currentView == "Month") _currentDate = _currentDate.AddMonths(1);
        else if (_currentView == "Year") _currentDate = _currentDate.AddYears(1);
        else if (_currentView == "Custom")
        {
            _customStartDate = _customStartDate.AddDays(_customDays);
            _customEndDate = _customEndDate.AddDays(_customDays);
            _currentDate = _customStartDate;
        }
        else _currentDate = _currentDate.AddDays(7);

        _miniCalMonth = _currentDate;
        RenderAllCalendarViews();
    }

    private void OnViewDayClicked(object sender, RoutedEventArgs e)
    {
        _currentView = "Day";
        RenderAllCalendarViews();
    }

    private void OnViewWeekClicked(object sender, RoutedEventArgs e)
    {
        _currentView = "Week";
        RenderAllCalendarViews();
    }

    private void OnViewMonthClicked(object sender, RoutedEventArgs e)
    {
        _currentView = "Month";
        RenderAllCalendarViews();
    }

    private void OnViewYearClicked(object sender, RoutedEventArgs e)
    {
        _currentView = "Year";
        RenderAllCalendarViews();
    }

    private void OnViewCustomClicked(object sender, RoutedEventArgs e)
    {
        _currentView = "Custom";
        RenderAllCalendarViews();
    }

    private void OnViewAgendaClicked(object sender, RoutedEventArgs e)
    {
        _currentView = "Agenda";
        RenderAllCalendarViews();
    }

    private void OnToggleLeftSidebarClicked(object sender, RoutedEventArgs e)
    {
        TogglePrimaryNavRail();
    }

    // =========================================================================
    // ====================== SETTINGS MODAL LOGIC =============================
    // =========================================================================

    private void OnToggleSettingsDrawerClicked(object sender, RoutedEventArgs e)
    {
        SwitchPrimaryPage("Settings");
    }

    private void OnCloseSettingsModalClicked(object sender, RoutedEventArgs e)
    {
        SwitchPrimaryPage("Overview");
    }

    private void OnSettingsTabThemeClicked(object sender, RoutedEventArgs e) => SwitchSettingsTab(0);
    private void OnSettingsTabDriveClicked(object sender, RoutedEventArgs e) => SwitchSettingsTab(1);
    private void OnSettingsTabSyncClicked(object sender, RoutedEventArgs e) => SwitchSettingsTab(2);
    private void OnSettingsTabClientClicked(object sender, RoutedEventArgs e) => SwitchSettingsTab(3);
    private void OnSettingsTabAdvancedClicked(object sender, RoutedEventArgs e) => SwitchSettingsTab(4);

    private void SwitchSettingsTab(int tabIndex)
    {
        var activeBg = ThemeService.CurrentAccentBrush;
        var normalBg = Brushes.Transparent;
        var activeFg = Brushes.White;
        var normalFg = ThemeService.CurrentSecondaryTextBrush;

        if (SettingsTabThemeBtn != null)
        {
            SettingsTabThemeBtn.Background = tabIndex == 0 ? activeBg : normalBg;
            SettingsTabThemeBtn.Foreground = tabIndex == 0 ? activeFg : normalFg;
            SettingsTabThemeBtn.FontWeight = tabIndex == 0 ? FontWeights.SemiBold : FontWeights.Normal;
        }

        if (SettingsTabDriveBtn != null)
        {
            SettingsTabDriveBtn.Background = tabIndex == 1 ? activeBg : normalBg;
            SettingsTabDriveBtn.Foreground = tabIndex == 1 ? activeFg : normalFg;
            SettingsTabDriveBtn.FontWeight = tabIndex == 1 ? FontWeights.SemiBold : FontWeights.Normal;
        }

        if (SettingsTabSyncBtn != null)
        {
            SettingsTabSyncBtn.Background = tabIndex == 2 ? activeBg : normalBg;
            SettingsTabSyncBtn.Foreground = tabIndex == 2 ? activeFg : normalFg;
            SettingsTabSyncBtn.FontWeight = tabIndex == 2 ? FontWeights.SemiBold : FontWeights.Normal;
        }

        if (SettingsTabClientBtn != null)
        {
            SettingsTabClientBtn.Background = tabIndex == 3 ? activeBg : normalBg;
            SettingsTabClientBtn.Foreground = tabIndex == 3 ? activeFg : normalFg;
            SettingsTabClientBtn.FontWeight = tabIndex == 3 ? FontWeights.SemiBold : FontWeights.Normal;
        }

        if (SettingsTabAdvancedBtn != null)
        {
            SettingsTabAdvancedBtn.Background = tabIndex == 4 ? activeBg : normalBg;
            SettingsTabAdvancedBtn.Foreground = tabIndex == 4 ? activeFg : normalFg;
            SettingsTabAdvancedBtn.FontWeight = tabIndex == 4 ? FontWeights.SemiBold : FontWeights.Normal;
        }

        if (SettingsPanelTheme != null) SettingsPanelTheme.Visibility = tabIndex == 0 ? Visibility.Visible : Visibility.Collapsed;
        if (SettingsPanelDrive != null) SettingsPanelDrive.Visibility = tabIndex == 1 ? Visibility.Visible : Visibility.Collapsed;
        if (SettingsPanelSync != null) SettingsPanelSync.Visibility = tabIndex == 2 ? Visibility.Visible : Visibility.Collapsed;
        if (SettingsPanelClient != null) SettingsPanelClient.Visibility = tabIndex == 3 ? Visibility.Visible : Visibility.Collapsed;
        if (SettingsPanelAdvanced != null) SettingsPanelAdvanced.Visibility = tabIndex == 4 ? Visibility.Visible : Visibility.Collapsed;
    }

    private void LoadSettingsIntoUi()
    {
        var config = ConfigService.Load();
        if (ClientSourceTagInput != null)
        {
            ClientSourceTagInput.Text = string.IsNullOrWhiteSpace(config.ClientSourceTag) ? "💻 桌面端" : config.ClientSourceTag;
        }
        if (GoogleDriveClientIdInput != null)
        {
            GoogleDriveClientIdInput.Text = config.GoogleClientId ?? string.Empty;
        }
        if (GoogleDriveClientSecretInput != null)
        {
            GoogleDriveClientSecretInput.Text = config.GoogleClientSecret ?? string.Empty;
        }
        UpdateGoogleDriveUiState(config);
        UpdateThemeUiFromConfig(config);
    }

    private void UpdateThemeUiFromConfig(AppConfig? config = null)
    {
        config ??= ConfigService.Load();
        bool isDark = ThemeService.ResolveIsDark(config.ThemeMode);
        string mode = (config.ThemeMode ?? "DARK").ToUpperInvariant();

        var activeBorderBrush = ThemeService.CurrentAccentBrush;
        var inactiveBorderBrush = ThemeService.CurrentBorderBrush;
        var activeBgBrush = new SolidColorBrush(Color.FromArgb(0x28, activeBorderBrush.Color.R, activeBorderBrush.Color.G, activeBorderBrush.Color.B));
        var inactiveBgBrush = ThemeService.CurrentControlBrush;

        if (ThemeModeLightBtn != null)
        {
            bool isSelected = mode == "LIGHT";
            ThemeModeLightBtn.BorderBrush = isSelected ? activeBorderBrush : inactiveBorderBrush;
            ThemeModeLightBtn.BorderThickness = new Thickness(isSelected ? 2 : 1);
            ThemeModeLightBtn.Background = isSelected ? activeBgBrush : inactiveBgBrush;
            if (ThemeModeLightTitleText != null)
            {
                ThemeModeLightTitleText.Foreground = isSelected ? activeBorderBrush : ThemeService.CurrentTextBrush;
                ThemeModeLightTitleText.FontWeight = isSelected ? FontWeights.Bold : FontWeights.SemiBold;
            }
            if (ThemeModeLightSubText != null)
            {
                ThemeModeLightSubText.Foreground = ThemeService.CurrentSecondaryTextBrush;
            }
        }

        if (ThemeModeDarkBtn != null)
        {
            bool isSelected = mode == "DARK";
            ThemeModeDarkBtn.BorderBrush = isSelected ? activeBorderBrush : inactiveBorderBrush;
            ThemeModeDarkBtn.BorderThickness = new Thickness(isSelected ? 2 : 1);
            ThemeModeDarkBtn.Background = isSelected ? activeBgBrush : inactiveBgBrush;
            if (ThemeModeDarkTitleText != null)
            {
                ThemeModeDarkTitleText.Foreground = isSelected ? activeBorderBrush : ThemeService.CurrentTextBrush;
                ThemeModeDarkTitleText.FontWeight = isSelected ? FontWeights.Bold : FontWeights.SemiBold;
            }
            if (ThemeModeDarkSubText != null)
            {
                ThemeModeDarkSubText.Foreground = ThemeService.CurrentSecondaryTextBrush;
            }
        }

        if (ThemeModeSystemBtn != null)
        {
            bool isSelected = mode == "SYSTEM";
            ThemeModeSystemBtn.BorderBrush = isSelected ? activeBorderBrush : inactiveBorderBrush;
            ThemeModeSystemBtn.BorderThickness = new Thickness(isSelected ? 2 : 1);
            ThemeModeSystemBtn.Background = isSelected ? activeBgBrush : inactiveBgBrush;
            if (ThemeModeSystemTitleText != null)
            {
                ThemeModeSystemTitleText.Foreground = isSelected ? activeBorderBrush : ThemeService.CurrentTextBrush;
                ThemeModeSystemTitleText.FontWeight = isSelected ? FontWeights.Bold : FontWeights.SemiBold;
            }
            if (ThemeModeSystemSubText != null)
            {
                ThemeModeSystemSubText.Foreground = ThemeService.CurrentSecondaryTextBrush;
            }
        }

        if (ThemeCurrentModeLabel != null)
        {
            string modeDesc = mode switch
            {
                "LIGHT" => "☀️ 日间明亮模式",
                "SYSTEM" => $"💻 跟随系统 (当前{(isDark ? "深色" : "明亮")})",
                _ => "🌙 夜间深色模式"
            };
            ThemeCurrentModeLabel.Text = $"当前模式：{modeDesc}";
            ThemeCurrentModeLabel.Foreground = ThemeService.CurrentAccentBrush;
        }

        string accentHex = string.IsNullOrWhiteSpace(config.AccentColorHex) ? "#1A73E8" : config.AccentColorHex;
        if (ThemeCustomAccentHexInput != null && ThemeCustomAccentHexInput.Text != accentHex)
        {
            ThemeCustomAccentHexInput.Text = accentHex;
        }
        if (ThemeCustomAccentPreviewBadge != null)
        {
            var parsedAccent = ThemeService.ParseHexColor(accentHex, Color.FromRgb(0x1A, 0x73, 0xE8));
            var brush = new SolidColorBrush(parsedAccent);
            ThemeCustomAccentPreviewBadge.Background = brush;
            if (ThemeAccentLargePreviewSwatch != null) ThemeAccentLargePreviewSwatch.Background = brush;
            if (ThemeAccentRgbSummaryText != null) ThemeAccentRgbSummaryText.Text = $"RGB({parsedAccent.R},{parsedAccent.G},{parsedAccent.B})";
            _isUpdatingThemeAccentSliders = true;
            if (ThemeAccentSliderR != null) ThemeAccentSliderR.Value = parsedAccent.R;
            if (ThemeAccentSliderG != null) ThemeAccentSliderG.Value = parsedAccent.G;
            if (ThemeAccentSliderB != null) ThemeAccentSliderB.Value = parsedAccent.B;
            if (ThemeAccentRText != null) ThemeAccentRText.Text = parsedAccent.R.ToString();
            if (ThemeAccentGText != null) ThemeAccentGText.Text = parsedAccent.G.ToString();
            if (ThemeAccentBText != null) ThemeAccentBText.Text = parsedAccent.B.ToString();
            _isUpdatingThemeAccentSliders = false;
        }

        if (ThemeCustomDarkBgInput != null)
        {
            ThemeCustomDarkBgInput.Text = string.IsNullOrWhiteSpace(config.CustomDarkBgHex) ? "#202124" : config.CustomDarkBgHex;
        }
        if (ThemeCustomLightBgInput != null)
        {
            ThemeCustomLightBgInput.Text = string.IsNullOrWhiteSpace(config.CustomLightBgHex) ? "#F8F9FA" : config.CustomLightBgHex;
        }

        if (ThemePreviewBadge != null) ThemePreviewBadge.Background = ThemeService.CurrentAccentBrush;
        if (ThemePreviewSampleBtn != null) ThemePreviewSampleBtn.Background = ThemeService.CurrentAccentBrush;
        if (ThemePreviewCardBox != null)
        {
            ThemePreviewCardBox.Background = ThemeService.CurrentSurfaceBrush;
            ThemePreviewCardBox.BorderBrush = ThemeService.CurrentBorderBrush;
        }
        if (ThemePreviewTitle != null) ThemePreviewTitle.Foreground = ThemeService.CurrentTextBrush;
        if (ThemePreviewBody != null) ThemePreviewBody.Foreground = ThemeService.CurrentSecondaryTextBrush;
    }

    private void OnThemeModeLightClicked(object sender, RoutedEventArgs e)
    {
        var config = ConfigService.Load();
        config.ThemeMode = "LIGHT";
        ConfigService.Save(config);
        ThemeService.ApplyTheme(this, config);
        ThemeStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
        ThemeStatusText.Text = "✓ 已切换为日间明亮模式，配置已自动保存！";
    }

    private void OnThemeModeDarkClicked(object sender, RoutedEventArgs e)
    {
        var config = ConfigService.Load();
        config.ThemeMode = "DARK";
        ConfigService.Save(config);
        ThemeService.ApplyTheme(this, config);
        ThemeStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
        ThemeStatusText.Text = "✓ 已切换为夜间深色模式，配置已自动保存！";
    }

    private void OnThemeModeSystemClicked(object sender, RoutedEventArgs e)
    {
        var config = ConfigService.Load();
        config.ThemeMode = "SYSTEM";
        ConfigService.Save(config);
        ThemeService.ApplyTheme(this, config);
        ThemeStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
        ThemeStatusText.Text = "✓ 已设置为跟随系统深浅模式，配置已自动保存！";
    }

    private void OnPresetAccentClicked(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement fe && fe.Tag is string hex)
        {
            var config = ConfigService.Load();
            config.AccentColorHex = hex;
            ConfigService.Save(config);
            ThemeService.ApplyTheme(this, config);
            ThemeStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
            ThemeStatusText.Text = $"✓ 已应用主题强调色：{hex}，配置已保存";
            if (ThemeCustomAccentHexInput != null)
            {
                ThemeCustomAccentHexInput.Text = hex;
            }
        }
    }

    private bool _isUpdatingThemeAccentSliders = false;

    private void OnThemeAccentRgbSliderChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        if (_isUpdatingThemeAccentSliders) return;
        if (ThemeAccentSliderR == null || ThemeAccentSliderG == null || ThemeAccentSliderB == null) return;

        byte r = (byte)Math.Clamp((int)Math.Round(ThemeAccentSliderR.Value), 0, 255);
        byte g = (byte)Math.Clamp((int)Math.Round(ThemeAccentSliderG.Value), 0, 255);
        byte b = (byte)Math.Clamp((int)Math.Round(ThemeAccentSliderB.Value), 0, 255);

        if (ThemeAccentRText != null) ThemeAccentRText.Text = r.ToString();
        if (ThemeAccentGText != null) ThemeAccentGText.Text = g.ToString();
        if (ThemeAccentBText != null) ThemeAccentBText.Text = b.ToString();

        var color = Color.FromRgb(r, g, b);
        var brush = new SolidColorBrush(color);

        if (ThemeCustomAccentPreviewBadge != null) ThemeCustomAccentPreviewBadge.Background = brush;
        if (ThemeAccentLargePreviewSwatch != null) ThemeAccentLargePreviewSwatch.Background = brush;
        if (ThemeAccentRgbSummaryText != null) ThemeAccentRgbSummaryText.Text = $"RGB({r},{g},{b})";

        string hex = $"#{r:X2}{g:X2}{b:X2}";
        _isUpdatingThemeAccentSliders = true;
        if (ThemeCustomAccentHexInput != null)
        {
            ThemeCustomAccentHexInput.Text = hex;
        }
        _isUpdatingThemeAccentSliders = false;
    }

    private void OnThemeCustomAccentHexTextChanged(object sender, TextChangedEventArgs e)
    {
        if (ThemeCustomAccentHexInput == null || ThemeCustomAccentPreviewBadge == null) return;
        string text = ThemeCustomAccentHexInput.Text.Trim();
        if (text.Length >= 4)
        {
            var color = ThemeService.ParseHexColor(text, Colors.Transparent);
            if (color != Colors.Transparent)
            {
                var brush = new SolidColorBrush(color);
                ThemeCustomAccentPreviewBadge.Background = brush;
                if (ThemeAccentLargePreviewSwatch != null) ThemeAccentLargePreviewSwatch.Background = brush;
                if (ThemeAccentRgbSummaryText != null) ThemeAccentRgbSummaryText.Text = $"RGB({color.R},{color.G},{color.B})";

                if (!_isUpdatingThemeAccentSliders)
                {
                    _isUpdatingThemeAccentSliders = true;
                    if (ThemeAccentSliderR != null) ThemeAccentSliderR.Value = color.R;
                    if (ThemeAccentSliderG != null) ThemeAccentSliderG.Value = color.G;
                    if (ThemeAccentSliderB != null) ThemeAccentSliderB.Value = color.B;
                    if (ThemeAccentRText != null) ThemeAccentRText.Text = color.R.ToString();
                    if (ThemeAccentGText != null) ThemeAccentGText.Text = color.G.ToString();
                    if (ThemeAccentBText != null) ThemeAccentBText.Text = color.B.ToString();
                    _isUpdatingThemeAccentSliders = false;
                }
            }
        }
    }

    private void OnOpenThemeRgbPickerModalClicked(object sender, RoutedEventArgs e)
    {
        _rgbPickerTarget = "THEME";
        string startHex = ThemeCustomAccentHexInput?.Text?.Trim() ?? "#1A73E8";
        if (!startHex.StartsWith("#")) startHex = "#" + startHex;

        try
        {
            var col = ThemeService.ParseHexColor(startHex, Color.FromRgb(0x1A, 0x73, 0xE8));
            _isUpdatingRgbPicker = true;
            if (RgbSliderR != null) RgbSliderR.Value = col.R;
            if (RgbSliderG != null) RgbSliderG.Value = col.G;
            if (RgbSliderB != null) RgbSliderB.Value = col.B;
            if (RgbInputR != null) RgbInputR.Text = col.R.ToString();
            if (RgbInputG != null) RgbInputG.Text = col.G.ToString();
            if (RgbInputB != null) RgbInputB.Text = col.B.ToString();
            if (RgbInputHex != null) RgbInputHex.Text = $"#{col.R:X2}{col.G:X2}{col.B:X2}";
            _currentRgbPickerHex = $"#{col.R:X2}{col.G:X2}{col.B:X2}";
            _isUpdatingRgbPicker = false;

            UpdateRgbPickerVisuals(col.R, col.G, col.B);
        }
        catch { }

        if (RgbModalPreviewTitle != null)
        {
            RgbModalPreviewTitle.Text = "全局主题强调色预览";
        }
        if (RgbModalPreviewTagText != null)
        {
            RgbModalPreviewTagText.Text = "主题色";
        }
        if (RgbModalPreviewTime != null)
        {
            RgbModalPreviewTime.Text = "实时生效";
        }

        if (RgbColorPickerModal != null)
        {
            RgbColorPickerModal.Visibility = Visibility.Visible;
        }
    }

    private void OnApplyCustomAccentClicked(object sender, RoutedEventArgs e)
    {
        string hex = ThemeCustomAccentHexInput.Text.Trim();
        if (!hex.StartsWith("#")) hex = "#" + hex;
        try
        {
            var color = ThemeService.ParseHexColor(hex, Colors.Transparent);
            if (color == Colors.Transparent)
            {
                ThemeStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0xEF, 0x44, 0x44));
                ThemeStatusText.Text = "✗ 无效的十六进制颜色代码，请输入如 #1A73E8 或 #10B981";
                return;
            }
            var config = ConfigService.Load();
            config.AccentColorHex = hex;
            ConfigService.Save(config);
            ThemeService.ApplyTheme(this, config);
            ThemeStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
            ThemeStatusText.Text = $"✓ 已自定义主题强调色为 {hex}，配置已保存！";
        }
        catch (Exception ex)
        {
            ThemeStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0xEF, 0x44, 0x44));
            ThemeStatusText.Text = $"✗ 应用颜色失败: {ex.Message}";
        }
    }

    private void OnPresetDarkBgClicked(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement fe && fe.Tag is string hex)
        {
            var config = ConfigService.Load();
            config.CustomDarkBgHex = hex;
            ConfigService.Save(config);
            ThemeService.ApplyTheme(this, config);
            ThemeStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
            ThemeStatusText.Text = $"✓ 已应用深色模式底色：{hex}";
        }
    }

    private void OnPresetLightBgClicked(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement fe && fe.Tag is string hex)
        {
            var config = ConfigService.Load();
            config.CustomLightBgHex = hex;
            ConfigService.Save(config);
            ThemeService.ApplyTheme(this, config);
            ThemeStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
            ThemeStatusText.Text = $"✓ 已应用日间模式底色：{hex}";
        }
    }

    private void OnApplyCustomDarkBgClicked(object sender, RoutedEventArgs e)
    {
        string hex = ThemeCustomDarkBgInput.Text.Trim();
        if (!hex.StartsWith("#")) hex = "#" + hex;
        var color = ThemeService.ParseHexColor(hex, Colors.Transparent);
        if (color == Colors.Transparent)
        {
            ThemeStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0xEF, 0x44, 0x44));
            ThemeStatusText.Text = "✗ 请输入有效的十六进制暗色底色代码";
            return;
        }
        var config = ConfigService.Load();
        config.CustomDarkBgHex = hex;
        ConfigService.Save(config);
        ThemeService.ApplyTheme(this, config);
        ThemeStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
        ThemeStatusText.Text = $"✓ 已更新深色模式底色为 {hex}";
    }

    private void OnApplyCustomLightBgClicked(object sender, RoutedEventArgs e)
    {
        string hex = ThemeCustomLightBgInput.Text.Trim();
        if (!hex.StartsWith("#")) hex = "#" + hex;
        var color = ThemeService.ParseHexColor(hex, Colors.Transparent);
        if (color == Colors.Transparent)
        {
            ThemeStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0xEF, 0x44, 0x44));
            ThemeStatusText.Text = "✗ 请输入有效的十六进制日间底色代码";
            return;
        }
        var config = ConfigService.Load();
        config.CustomLightBgHex = hex;
        ConfigService.Save(config);
        ThemeService.ApplyTheme(this, config);
        ThemeStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
        ThemeStatusText.Text = $"✓ 已更新日间模式底色为 {hex}";
    }

    private void OnResetThemeDefaultsClicked(object sender, RoutedEventArgs e)
    {
        var config = ConfigService.Load();
        config.ThemeMode = "DARK";
        config.AccentColorHex = "#1A73E8";
        config.CustomDarkBgHex = "#202124";
        config.CustomLightBgHex = "#F8F9FA";
        ConfigService.Save(config);
        ThemeService.ApplyTheme(this, config);
        ThemeStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
        ThemeStatusText.Text = "✓ 已恢复默认 Google 经典深色沉浸主题与 Google 蓝！";
    }

    public void ApplyVisualTheme(bool isDark, SolidColorBrush surfaceBrush, SolidColorBrush cardBrush, SolidColorBrush borderBrush, SolidColorBrush textBrush, SolidColorBrush secondaryTextBrush, SolidColorBrush navRailBrush, SolidColorBrush accentBrush)
    {
        if (RootMainGrid != null) RootMainGrid.Background = surfaceBrush;
        if (TopAppBarBorder != null)
        {
            TopAppBarBorder.Background = isDark ? surfaceBrush : cardBrush;
            TopAppBarBorder.BorderBrush = borderBrush;
        }
        if (TopDateHeaderTitle != null) TopDateHeaderTitle.Foreground = textBrush;
        if (PrimaryNavRail != null)
        {
            PrimaryNavRail.Background = navRailBrush;
            PrimaryNavRail.BorderBrush = borderBrush;
        }

        // Re-apply navigation highlight
        SwitchPrimaryPage(_activePrimaryPage);

        // Update ViewWeekBtn if active
        if (ViewWeekBtn != null) ViewWeekBtn.Background = accentBrush;

        // Update Pages backgrounds
        if (SettingsPageContainer != null) SettingsPageContainer.Background = surfaceBrush;
        if (SummaryPageContainer != null) SummaryPageContainer.Background = surfaceBrush;
        if (FocusPageContainer != null) FocusPageContainer.Background = surfaceBrush;
        if (DailyReportPageContainer != null) DailyReportPageContainer.Background = surfaceBrush;
        if (TimetablePageContainer != null) TimetablePageContainer.Background = surfaceBrush;
        if (StudyPageContainer != null) StudyPageContainer.Background = surfaceBrush;

        // Update Settings theme UI controls
        UpdateThemeUiFromConfig();

        // Re-render visual components that use programmatic brushes
        try
        {
            BuildWeekHourGrid();
            BuildDayHourGrid();
            RenderAllCalendarViews();
            RenderMiniMonthCalendar();
            RefreshBacklogList();
            RefreshGoalTree();
            if (SummaryPageContainer?.Visibility == Visibility.Visible) RenderSummaryPage();
            if (DailyReportPageContainer?.Visibility == Visibility.Visible) RenderDailyReportPage();
            if (StudyPageContainer?.Visibility == Visibility.Visible) RenderStudyPage();
            if (TimetablePageContainer?.Visibility == Visibility.Visible) RenderTimetablePage();
        }
        catch { }
    }

    private void UpdateGoogleDriveUiState(AppConfig? config = null)
    {
        config ??= ConfigService.Load();
        bool isLinked = config.IsGoogleDriveLinked;
        if (isLinked)
        {
            GoogleDriveStatusBadge.Background = new SolidColorBrush(Color.FromRgb(0x06, 0x4E, 0x3B));
            GoogleDriveStatusBadge.BorderBrush = new SolidColorBrush(Color.FromRgb(0x05, 0x96, 0x69));
            GoogleDriveStatusBadgeText.Foreground = new SolidColorBrush(Color.FromRgb(0x6E, 0xE7, 0xB7));
            GoogleDriveStatusBadgeText.Text = "已授权连接";
        }
        else
        {
            GoogleDriveStatusBadge.Background = new SolidColorBrush(Color.FromRgb(0x1E, 0x29, 0x3B));
            GoogleDriveStatusBadge.BorderBrush = new SolidColorBrush(Color.FromRgb(0x3B, 0x82, 0xF6));
            GoogleDriveStatusBadgeText.Foreground = new SolidColorBrush(Color.FromRgb(0x93, 0xC5, 0xFD));
            GoogleDriveStatusBadgeText.Text = "未授权 / 待同步";
        }

        string lastSync = string.IsNullOrWhiteSpace(config.LastSyncTime) ? "从未同步" : config.LastSyncTime;
        GoogleDriveLastSyncText.Text = $"上次备份：{lastSync}";
    }

    private void OnSaveGoogleDriveCredsClicked(object sender, RoutedEventArgs e)
    {
        string cId = GoogleDriveClientIdInput.Text.Trim();
        string cSec = GoogleDriveClientSecretInput.Text.Trim();
        var config = ConfigService.Load();
        config.GoogleClientId = cId;
        config.GoogleClientSecret = cSec;
        ConfigService.Save(config);

        GoogleDriveActionStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
        GoogleDriveActionStatusText.Text = "✓ OAuth 凭据已保存到本地配置！";
    }

    private void OnDriveClearAuthClicked(object sender, RoutedEventArgs e)
    {
        var result = System.Windows.MessageBox.Show(
            "确定要清除本地 Google Drive 授权 Token 缓存吗？下次同步时需要重新授权。",
            "确认解除授权",
            MessageBoxButton.YesNo,
            MessageBoxImage.Question
        );

        if (result == MessageBoxResult.Yes)
        {
            GoogleDriveSyncService.ClearLocalCredentials();
            UpdateGoogleDriveUiState();
            GoogleDriveActionStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0xF5, 0x9E, 0x0B));
            GoogleDriveActionStatusText.Text = "已成功解除授权并清除本地凭证缓存。";
        }
    }

    private async void OnDriveUploadBackupClicked(object sender, RoutedEventArgs e)
    {
        string cId = GoogleDriveClientIdInput.Text.Trim();
        string cSec = GoogleDriveClientSecretInput.Text.Trim();

        if (string.IsNullOrWhiteSpace(cId) || string.IsNullOrWhiteSpace(cSec))
        {
            System.Windows.MessageBox.Show("请先在上方填入有效 Google Client ID 与 Client Secret，并点击「保存凭据」！", "提示", MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }

        GoogleDriveActionStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0x38, 0xBD, 0xF8));
        GoogleDriveActionStatusText.Text = "正在准备云端备份并进行安全授权...";

        try
        {
            var (success, fileId, message) = await GoogleDriveSyncService.UploadBackupToDriveAsync(
                cId, cSec,
                status => Dispatcher.Invoke(() => GoogleDriveActionStatusText.Text = status)
            );

            if (success)
            {
                UpdateGoogleDriveUiState();
                GoogleDriveActionStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
                GoogleDriveActionStatusText.Text = $"✓ {message}";
                System.Windows.MessageBox.Show(message, "云端备份成功", MessageBoxButton.OK, MessageBoxImage.Information);
            }
            else
            {
                GoogleDriveActionStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0xEF, 0x44, 0x44));
                GoogleDriveActionStatusText.Text = $"✗ {message}";
                System.Windows.MessageBox.Show(message, "云端备份未完成", MessageBoxButton.OK, MessageBoxImage.Warning);
            }
        }
        catch (Exception ex)
        {
            GoogleDriveActionStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0xEF, 0x44, 0x44));
            GoogleDriveActionStatusText.Text = $"✗ 同步出错: {ex.Message}";
            System.Windows.MessageBox.Show($"备份失败: {ex.Message}", "错误", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }

    private async void OnDriveDownloadBackupClicked(object sender, RoutedEventArgs e)
    {
        string cId = GoogleDriveClientIdInput.Text.Trim();
        string cSec = GoogleDriveClientSecretInput.Text.Trim();

        if (string.IsNullOrWhiteSpace(cId) || string.IsNullOrWhiteSpace(cSec))
        {
            System.Windows.MessageBox.Show("请先填入有效 Google Client ID 与 Client Secret！", "提示", MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }

        var confirm = System.Windows.MessageBox.Show(
            "【警告】从 Google Drive 下载恢复将使用云端数据完整覆盖当前本地数据库！\n\n系统将在本地数据库目录自动生成一份 .bak 安全快照。确定继续从云端恢复吗？",
            "确认从云端恢复",
            MessageBoxButton.YesNo,
            MessageBoxImage.Warning
        );

        if (confirm != MessageBoxResult.Yes) return;

        GoogleDriveActionStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0x38, 0xBD, 0xF8));
        GoogleDriveActionStatusText.Text = "正在从 Google Drive 检索并拉取最新云端备份...";

        try
        {
            var (success, message) = await GoogleDriveSyncService.DownloadBackupFromDriveAsync(
                cId, cSec,
                status => Dispatcher.Invoke(() => GoogleDriveActionStatusText.Text = status)
            );

            if (success)
            {
                UpdateGoogleDriveUiState();
                GoogleDriveActionStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
                GoogleDriveActionStatusText.Text = $"✓ {message}";

                // 重新刷新界面上所有数据视图
                RenderAllCalendarViews();
                RefreshTimePnlData();
                UpdateCognitiveLoadQuota();
                if (_activePrimaryPage == "Timetable") RenderTimetablePage();
                else if (_activePrimaryPage == "Summary") RenderSummaryPage();
                else if (_activePrimaryPage == "Study") RenderStudyPage();
                else if (_activePrimaryPage == "DailyReport") RenderDailyReportPage();

                System.Windows.MessageBox.Show(message, "云端恢复成功", MessageBoxButton.OK, MessageBoxImage.Information);
            }
            else
            {
                GoogleDriveActionStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0xEF, 0x44, 0x44));
                GoogleDriveActionStatusText.Text = $"✗ {message}";
                System.Windows.MessageBox.Show(message, "恢复失败", MessageBoxButton.OK, MessageBoxImage.Warning);
            }
        }
        catch (Exception ex)
        {
            GoogleDriveActionStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0xEF, 0x44, 0x44));
            GoogleDriveActionStatusText.Text = $"✗ 恢复出错: {ex.Message}";
            System.Windows.MessageBox.Show($"恢复异常: {ex.Message}", "错误", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }

    private void OnOptimizeLocalDbClicked(object sender, RoutedEventArgs e)
    {
        try
        {
            DatabaseService.OptimizeDatabase();
            GoogleDriveActionStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
            GoogleDriveActionStatusText.Text = "✓ SQLite 数据库已成功执行 PRAGMA optimize & VACUUM 碎片整理！";
            System.Windows.MessageBox.Show("数据库优化完成！已释放未使用空间并更新全量查询执行计划统计。", "优化成功", MessageBoxButton.OK, MessageBoxImage.Information);
        }
        catch (Exception ex)
        {
            GoogleDriveActionStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0xEF, 0x44, 0x44));
            GoogleDriveActionStatusText.Text = $"✗ 优化失败: {ex.Message}";
        }
    }

    private void OnClearAllDbDataClicked(object sender, RoutedEventArgs e)
    {
        var result = System.Windows.MessageBox.Show(
            "确定要清空所有业务数据（日程、目标、学习档案、课程、调停课、每日汇报、专注监控、支出）吗？\n\n【安全保障】操作前系统会自动创建带有时间戳的 .bak 安全备份文件。\n系统设置与课程时间表参数将被完整保留。",
            "确认清空所有测试业务数据",
            MessageBoxButton.YesNo,
            MessageBoxImage.Warning
        );

        if (result != MessageBoxResult.Yes) return;

        try
        {
            var (success, msg) = DatabaseService.ClearAllData();
            if (success)
            {
                GoogleDriveActionStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
                GoogleDriveActionStatusText.Text = $"✓ {msg}";

                // 全局重新刷新所有界面数据
                RenderAllCalendarViews();
                RefreshGoalTree();
                RefreshBacklogList();
                RefreshDeferredQueue();
                RefreshTimePnlData();
                RenderStudyPage();
                RenderTimetablePage();
                RenderDailyReportPage();
                RenderSummaryPage();

                System.Windows.MessageBox.Show(msg, "清空成功", MessageBoxButton.OK, MessageBoxImage.Information);
            }
            else
            {
                GoogleDriveActionStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0xEF, 0x44, 0x44));
                GoogleDriveActionStatusText.Text = $"✗ {msg}";
                System.Windows.MessageBox.Show(msg, "错误", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }
        catch (Exception ex)
        {
            GoogleDriveActionStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0xEF, 0x44, 0x44));
            GoogleDriveActionStatusText.Text = $"✗ 清空异常: {ex.Message}";
        }
    }

    // =========================================================================
    // ================= PRIMARY NAV RAIL & MULTI-PAGE SWITCHING ===============
    // =========================================================================

    private string _activePrimaryPage = "Overview";
    private bool _isNavRailPinned = false;
    private string? _editingStudyTopicId = null;

    private void AnimateNavRailWidth(double targetWidth)
    {
        var anim = new DoubleAnimation
        {
            To = targetWidth,
            Duration = TimeSpan.FromMilliseconds(180),
            EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseOut }
        };
        PrimaryNavRail.BeginAnimation(FrameworkElement.WidthProperty, anim);
    }

    private void OnNavRailMouseEnter(object sender, MouseEventArgs e)
    {
        if (!_isNavRailPinned)
        {
            AnimateNavRailWidth(180);
        }
    }

    private void OnNavRailMouseLeave(object sender, MouseEventArgs e)
    {
        if (!_isNavRailPinned)
        {
            AnimateNavRailWidth(58);
        }
    }

    private void TogglePrimaryNavRail()
    {
        _isNavRailPinned = !_isNavRailPinned;
        if (_isNavRailPinned)
        {
            NavPinIcon.Text = "📍";
            NavPinLabel.Text = "已固定 (点击取消)";
            AnimateNavRailWidth(180);
        }
        else
        {
            NavPinIcon.Text = "📌";
            NavPinLabel.Text = "固定侧边栏";
            AnimateNavRailWidth(58);
        }
    }

    private void OnNavPinClicked(object sender, RoutedEventArgs e)
    {
        TogglePrimaryNavRail();
    }

    private void OnNavOverviewClicked(object sender, RoutedEventArgs e) => SwitchPrimaryPage("Overview");
    private void OnNavFocusClicked(object sender, RoutedEventArgs e) => SwitchPrimaryPage("Focus");
    private void OnNavSummaryClicked(object sender, RoutedEventArgs e) => SwitchPrimaryPage("Summary");
    private void OnNavStudyClicked(object sender, RoutedEventArgs e) => SwitchPrimaryPage("Study");
    private void OnNavFitnessClicked(object sender, RoutedEventArgs e) => SwitchPrimaryPage("Fitness");
    private void OnNavTimetableClicked(object sender, RoutedEventArgs e) => SwitchPrimaryPage("Timetable");
    private void OnNavDailyReportClicked(object sender, RoutedEventArgs e) => SwitchPrimaryPage("DailyReport");
    private void OnNavTutorialClicked(object sender, RoutedEventArgs e) => SwitchPrimaryPage("Tutorial");
    private void OnNavSettingsClicked(object sender, RoutedEventArgs e) => SwitchPrimaryPage("Settings");
    private void OnOpenTutorialPageClicked(object sender, RoutedEventArgs e) => SwitchPrimaryPage("Tutorial");

    private void OnTutorialJumpCalendarClicked(object sender, RoutedEventArgs e) => SwitchPrimaryPage("Overview");
    private void OnTutorialJumpFocusClicked(object sender, RoutedEventArgs e) => SwitchPrimaryPage("Focus");
    private void OnTutorialJumpSummaryClicked(object sender, RoutedEventArgs e) => SwitchPrimaryPage("Summary");
    private void OnTutorialJumpStudyClicked(object sender, RoutedEventArgs e) => SwitchPrimaryPage("Study");
    private void OnTutorialJumpTimetableClicked(object sender, RoutedEventArgs e) => SwitchPrimaryPage("Timetable");
    private void OnTutorialJumpDailyReportClicked(object sender, RoutedEventArgs e) => SwitchPrimaryPage("DailyReport");
    private void OnTutorialJumpSettingsClicked(object sender, RoutedEventArgs e) => SwitchPrimaryPage("Settings");

    private void SwitchPrimaryPage(string page)
    {
        _activePrimaryPage = page;

        var activeBg = ThemeService.CurrentAccentBrush;
        var normalBg = Brushes.Transparent;
        var activeFg = Brushes.White;
        var normalFg = ThemeService.CurrentSecondaryTextBrush;

        NavOverviewBtn.Background = page == "Overview" ? activeBg : normalBg;
        NavOverviewBtn.Foreground = page == "Overview" ? activeFg : normalFg;

        if (NavFocusBtn != null)
        {
            NavFocusBtn.Background = page == "Focus" ? activeBg : normalBg;
            NavFocusBtn.Foreground = page == "Focus" ? activeFg : normalFg;
        }

        NavSummaryBtn.Background = page == "Summary" ? activeBg : normalBg;
        NavSummaryBtn.Foreground = page == "Summary" ? activeFg : normalFg;

        NavStudyBtn.Background = page == "Study" ? activeBg : normalBg;
        NavStudyBtn.Foreground = page == "Study" ? activeFg : normalFg;

        if (NavFitnessBtn != null)
        {
            NavFitnessBtn.Background = page == "Fitness" ? activeBg : normalBg;
            NavFitnessBtn.Foreground = page == "Fitness" ? activeFg : normalFg;
        }

        NavTimetableBtn.Background = page == "Timetable" ? activeBg : normalBg;
        NavTimetableBtn.Foreground = page == "Timetable" ? activeFg : normalFg;

        NavDailyReportBtn.Background = page == "DailyReport" ? activeBg : normalBg;
        NavDailyReportBtn.Foreground = page == "DailyReport" ? activeFg : normalFg;

        if (NavTutorialBtn != null)
        {
            NavTutorialBtn.Background = page == "Tutorial" ? activeBg : normalBg;
            NavTutorialBtn.Foreground = page == "Tutorial" ? activeFg : normalFg;
        }

        if (NavSettingsBtn != null)
        {
            NavSettingsBtn.Background = page == "Settings" ? activeBg : normalBg;
            NavSettingsBtn.Foreground = page == "Settings" ? activeFg : normalFg;
        }

        OverviewPageContainer.Visibility = page == "Overview" ? Visibility.Visible : Visibility.Collapsed;
        if (FocusPageContainer != null)
        {
            FocusPageContainer.Visibility = page == "Focus" ? Visibility.Visible : Visibility.Collapsed;
        }
        SummaryPageContainer.Visibility = page == "Summary" ? Visibility.Visible : Visibility.Collapsed;
        StudyPageContainer.Visibility = page == "Study" ? Visibility.Visible : Visibility.Collapsed;
        if (FitnessPageContainer != null)
        {
            FitnessPageContainer.Visibility = page == "Fitness" ? Visibility.Visible : Visibility.Collapsed;
        }
        TimetablePageContainer.Visibility = page == "Timetable" ? Visibility.Visible : Visibility.Collapsed;
        DailyReportPageContainer.Visibility = page == "DailyReport" ? Visibility.Visible : Visibility.Collapsed;
        if (TutorialPageContainer != null)
        {
            TutorialPageContainer.Visibility = page == "Tutorial" ? Visibility.Visible : Visibility.Collapsed;
        }
        if (SettingsPageContainer != null)
        {
            SettingsPageContainer.Visibility = page == "Settings" ? Visibility.Visible : Visibility.Collapsed;
        }

        bool isOverview = page == "Overview";
        if (ViewSelectorBorder != null) ViewSelectorBorder.Visibility = isOverview ? Visibility.Visible : Visibility.Collapsed;
        if (TopTodayBtn != null) TopTodayBtn.Visibility = isOverview ? Visibility.Visible : Visibility.Collapsed;
        if (TopPrevBtn != null) TopPrevBtn.Visibility = isOverview ? Visibility.Visible : Visibility.Collapsed;
        if (TopNextBtn != null) TopNextBtn.Visibility = isOverview ? Visibility.Visible : Visibility.Collapsed;
        if (TopDateHeaderArrow != null) TopDateHeaderArrow.Visibility = isOverview ? Visibility.Visible : Visibility.Collapsed;
        if (SearchBoxBorder != null) SearchBoxBorder.Visibility = isOverview ? Visibility.Visible : Visibility.Collapsed;

        if (isOverview)
        {
            RenderAllCalendarViews();
        }
        else
        {
            if (TopDateHeaderTitle != null)
            {
                TopDateHeaderTitle.Text = page switch
                {
                    "Focus" => "🎯 专注",
                    "Summary" => "📊 统计",
                    "Study" => "📚 学习",
                    "Fitness" => "🏋️ 健身",
                    "Timetable" => "🎓 课程表",
                    "DailyReport" => "🏆 日报",
                    "Tutorial" => "📖 使用指南",
                    "Settings" => "⚙️ 设置",
                    _ => "RMF"
                };
            }
        }

        if (page == "Focus")
        {
            RenderFocusPage();
        }
        else if (page == "Summary")
        {
            RenderSummaryPage();
        }
        else if (page == "Study")
        {
            RenderStudyPage();
        }
        else if (page == "Fitness")
        {
            RenderFitnessPage();
        }
        else if (page == "Timetable")
        {
            RenderTimetablePage();
        }
        else if (page == "DailyReport")
        {
            RenderDailyReportPage();
        }
        else if (page == "Settings")
        {
            LoadSettingsIntoUi();
        }
    }

    // =========================================================================
    // ================= BEGINNER ONBOARDING & TUTORIAL LOGIC ==================
    // =========================================================================

    private int _onboardingCurrentStep = 1;

    private void ShowOnboardingModal()
    {
        _onboardingCurrentStep = 1;
        UpdateOnboardingStepUi();
        if (OnboardingModal != null)
        {
            OnboardingModal.Visibility = Visibility.Visible;
        }
    }

    private void HideOnboardingModal()
    {
        if (OnboardingModal != null)
        {
            OnboardingModal.Visibility = Visibility.Collapsed;
        }
    }

    private void CompleteOnboarding()
    {
        try
        {
            var config = ConfigService.Load();
            config.HasCompletedOnboarding = true;
            ConfigService.Save(config);
        }
        catch { }
        HideOnboardingModal();
    }

    private void OnOnboardingSkipClicked(object sender, RoutedEventArgs e)
    {
        CompleteOnboarding();
    }

    private void OnOnboardingPrevClicked(object sender, RoutedEventArgs e)
    {
        if (_onboardingCurrentStep > 1)
        {
            _onboardingCurrentStep--;
            UpdateOnboardingStepUi();
        }
    }

    private void OnOnboardingNextClicked(object sender, RoutedEventArgs e)
    {
        if (_onboardingCurrentStep < 4)
        {
            _onboardingCurrentStep++;
            UpdateOnboardingStepUi();
        }
        else
        {
            CompleteOnboarding();
        }
    }

    private void OnReplayOnboardingClicked(object sender, RoutedEventArgs e)
    {
        ShowOnboardingModal();
    }

    private void OnOnboardingOpenTutorialPageClicked(object sender, RoutedEventArgs e)
    {
        CompleteOnboarding();
        SwitchPrimaryPage("Tutorial");
    }

    private void OnOnboardingThemeDarkClicked(object sender, RoutedEventArgs e)
    {
        var config = ConfigService.Load();
        config.ThemeMode = "DARK";
        ConfigService.Save(config);
        ThemeService.ApplyTheme(this, config);
        LoadSettingsIntoUi();
    }

    private void OnOnboardingThemeLightClicked(object sender, RoutedEventArgs e)
    {
        var config = ConfigService.Load();
        config.ThemeMode = "LIGHT";
        ConfigService.Save(config);
        ThemeService.ApplyTheme(this, config);
        LoadSettingsIntoUi();
    }

    private void UpdateOnboardingStepUi()
    {
        if (OnboardingStepPanel1 == null) return;

        OnboardingStepPanel1.Visibility = _onboardingCurrentStep == 1 ? Visibility.Visible : Visibility.Collapsed;
        OnboardingStepPanel2.Visibility = _onboardingCurrentStep == 2 ? Visibility.Visible : Visibility.Collapsed;
        OnboardingStepPanel3.Visibility = _onboardingCurrentStep == 3 ? Visibility.Visible : Visibility.Collapsed;
        OnboardingStepPanel4.Visibility = _onboardingCurrentStep == 4 ? Visibility.Visible : Visibility.Collapsed;

        if (OnboardingStepIndicatorText != null)
        {
            OnboardingStepIndicatorText.Text = _onboardingCurrentStep switch
            {
                1 => "步骤 1 / 4 · 欢迎启程与理念",
                2 => "步骤 2 / 4 · 四维排期与待办敏捷池",
                3 => "步骤 3 / 4 · 自学攻坚、艾宾浩斯与高校课表",
                4 => "步骤 4 / 4 · 个性化配置与起步",
                _ => $"步骤 {_onboardingCurrentStep} / 4"
            };
        }

        var activeDot = ThemeService.CurrentAccentBrush;
        var inactiveDot = (Brush)FindResource("CardBorderBrush");

        if (OnboardingDot1 != null) OnboardingDot1.Fill = _onboardingCurrentStep == 1 ? activeDot : inactiveDot;
        if (OnboardingDot2 != null) OnboardingDot2.Fill = _onboardingCurrentStep == 2 ? activeDot : inactiveDot;
        if (OnboardingDot3 != null) OnboardingDot3.Fill = _onboardingCurrentStep == 3 ? activeDot : inactiveDot;
        if (OnboardingDot4 != null) OnboardingDot4.Fill = _onboardingCurrentStep == 4 ? activeDot : inactiveDot;

        if (OnboardingPrevBtn != null)
        {
            OnboardingPrevBtn.Visibility = _onboardingCurrentStep > 1 ? Visibility.Visible : Visibility.Collapsed;
        }

        if (OnboardingNextBtn != null)
        {
            OnboardingNextBtn.Content = _onboardingCurrentStep == 4 ? "开始使用 RMF 🚀" : "下一步 ›";
        }
    }

    // =========================================================================
    // ================= SCHEDULE TAG MANAGEMENT LOGIC =========================
    // =========================================================================

    private void RefreshEventCategoryCombo(string? selectedTag = null)
    {
        if (EventCategoryCombo == null) return;
        string currentSelected = selectedTag ?? EventCategoryCombo.SelectedItem?.ToString() ?? EventCategoryCombo.Text;

        EventCategoryCombo.Items.Clear();
        var tags = DatabaseService.GetDistinctCategories();
        foreach (var tag in tags)
        {
            EventCategoryCombo.Items.Add(tag);
        }

        if (!string.IsNullOrEmpty(currentSelected) && EventCategoryCombo.Items.Contains(currentSelected))
        {
            EventCategoryCombo.SelectedItem = currentSelected;
        }
        else if (EventCategoryCombo.Items.Count > 0)
        {
            EventCategoryCombo.SelectedIndex = 0;
        }
    }

    private void OnOpenTagManagementClicked(object sender, RoutedEventArgs e)
    {
        if (NewTagNameInput != null) NewTagNameInput.Clear();
        RenderTagManagementList();
        if (TagManagementModal != null)
        {
            TagManagementModal.Visibility = Visibility.Visible;
        }
    }

    private void OnCloseTagManagementClicked(object sender, RoutedEventArgs e)
    {
        if (TagManagementModal != null)
        {
            TagManagementModal.Visibility = Visibility.Collapsed;
        }
        RefreshEventCategoryCombo();
        RenderMyCalendarsList();
        RenderAllCalendarViews();
    }

    private void OnAddNewTagClicked(object sender, RoutedEventArgs e)
    {
        string name = NewTagNameInput?.Text?.Trim() ?? "";
        if (string.IsNullOrEmpty(name))
        {
            MessageBox.Show("请输入标签名称！", "提示", MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }

        string colorHex = "#1A73E8";
        if (NewTagColorCombo?.SelectedItem is ComboBoxItem cbi && cbi.Tag is string cTag && !string.IsNullOrEmpty(cTag))
        {
            colorHex = cTag;
        }

        bool success = DatabaseService.AddScheduleTag(name, colorHex);
        if (!success)
        {
            MessageBox.Show($"标签「{name}」已存在或创建失败！", "提示", MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }

        if (NewTagNameInput != null) NewTagNameInput.Clear();
        RenderTagManagementList();
        RefreshEventCategoryCombo(name);
        RenderMyCalendarsList();
        RenderAllCalendarViews();
    }

    private void RenderTagManagementList()
    {
        if (TagItemsListPanel == null) return;
        TagItemsListPanel.Children.Clear();

        var tags = DatabaseService.GetAllScheduleTags();
        if (TagCountSummaryText != null)
        {
            TagCountSummaryText.Text = $"共 {tags.Count} 个分类标签";
        }

        if (tags.Count == 0)
        {
            TagItemsListPanel.Children.Add(new TextBlock
            {
                Text = "暂无任何标签，请在上方添加新标签。",
                FontSize = 11.5,
                Foreground = ThemeService.CurrentSecondaryTextBrush,
                Margin = new Thickness(4, 6, 0, 6)
            });
            return;
        }

        foreach (var tag in tags)
        {
            var rowBorder = new Border
            {
                Background = (Brush)FindResource("CardBackground"),
                BorderBrush = (Brush)FindResource("CardBorderBrush"),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(6),
                Padding = new Thickness(10, 7, 10, 7),
                Margin = new Thickness(0, 0, 0, 6)
            };

            var grid = new Grid();
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            // Left Display Panel
            var leftSp = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };
            
            Brush dotBrush = Brushes.DodgerBlue;
            try
            {
                if (!string.IsNullOrEmpty(tag.ColorHex))
                {
                    dotBrush = new BrushConverter().ConvertFromString(tag.ColorHex) as Brush ?? Brushes.DodgerBlue;
                }
            }
            catch { }

            var colorDot = new Border
            {
                Width = 12,
                Height = 12,
                CornerRadius = new CornerRadius(6),
                Background = dotBrush,
                Margin = new Thickness(0, 0, 8, 0),
                VerticalAlignment = VerticalAlignment.Center
            };
            leftSp.Children.Add(colorDot);

            var nameText = new TextBlock
            {
                Text = tag.Name,
                FontSize = 12.5,
                FontWeight = FontWeights.SemiBold,
                Foreground = ThemeService.CurrentTextBrush,
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0, 0, 10, 0)
            };
            leftSp.Children.Add(nameText);

            var usageBadge = new Border
            {
                Background = (Brush)FindResource("ControlBackground"),
                BorderBrush = (Brush)FindResource("CardBorderBrush"),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(4),
                Padding = new Thickness(6, 1, 6, 1),
                VerticalAlignment = VerticalAlignment.Center
            };
            usageBadge.Child = new TextBlock
            {
                Text = $"{tag.UsageCount} 项日程使用",
                FontSize = 10,
                Foreground = ThemeService.CurrentSecondaryTextBrush
            };
            leftSp.Children.Add(usageBadge);

            Grid.SetColumn(leftSp, 0);
            grid.Children.Add(leftSp);

            // Right Action Buttons
            var rightSp = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };

            var editBtn = new Button
            {
                Content = "✏️",
                Style = (Style)FindResource("GoogleTopBtnStyle"),
                Width = 28,
                Height = 28,
                FontSize = 11,
                ToolTip = "重命名标签",
                Margin = new Thickness(0, 0, 4, 0)
            };

            var deleteBtn = new Button
            {
                Content = "🗑️",
                Style = (Style)FindResource("GoogleTopBtnStyle"),
                Width = 28,
                Height = 28,
                FontSize = 11,
                Foreground = new BrushConverter().ConvertFromString("#EF4444") as Brush,
                ToolTip = "删除标签"
            };

            // Inline Rename Interaction
            editBtn.Click += (s, e) =>
            {
                grid.Children.Clear();
                grid.ColumnDefinitions.Clear();
                grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

                var editInput = new TextBox
                {
                    Text = tag.Name,
                    Background = (Brush)FindResource("InputBackground"),
                    Foreground = ThemeService.CurrentTextBrush,
                    BorderBrush = (Brush)FindResource("AccentBlue"),
                    BorderThickness = new Thickness(1),
                    Padding = new Thickness(6, 3, 6, 3),
                    FontSize = 12,
                    VerticalAlignment = VerticalAlignment.Center
                };
                Grid.SetColumn(editInput, 0);
                grid.Children.Add(editInput);

                var actionBox = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(6, 0, 0, 0) };
                var confirmBtn = new Button
                {
                    Content = "✓ 保存",
                    Style = (Style)FindResource("GoogleActionBtnStyle"),
                    Padding = new Thickness(10, 4, 10, 4),
                    FontSize = 11,
                    FontWeight = FontWeights.SemiBold,
                    Margin = new Thickness(0, 0, 4, 0)
                };
                var cancelBtn = new Button
                {
                    Content = "✕",
                    Style = (Style)FindResource("GoogleTopBtnStyle"),
                    Width = 26,
                    Height = 26,
                    FontSize = 11
                };

                confirmBtn.Click += (cs, ce) =>
                {
                    string newName = editInput.Text.Trim();
                    if (string.IsNullOrEmpty(newName))
                    {
                        MessageBox.Show("标签名称不能为空！", "提示", MessageBoxButton.OK, MessageBoxImage.Warning);
                        return;
                    }
                    if (newName != tag.Name)
                    {
                        DatabaseService.RenameScheduleTag(tag.Name, newName);
                    }
                    RenderTagManagementList();
                    RefreshEventCategoryCombo(newName);
                    RenderMyCalendarsList();
                    RenderAllCalendarViews();
                };

                cancelBtn.Click += (cs, ce) =>
                {
                    RenderTagManagementList();
                };

                actionBox.Children.Add(confirmBtn);
                actionBox.Children.Add(cancelBtn);
                Grid.SetColumn(actionBox, 1);
                grid.Children.Add(actionBox);

                editInput.Focus();
                editInput.SelectAll();
            };

            deleteBtn.Click += (s, e) =>
            {
                var res = MessageBox.Show(
                    $"确定要从标签库中删除标签「{tag.Name}」吗？\n（已有该分类的日程不会被删除，但标签库中将不再提供此项供新日程选择）",
                    "删除标签确认",
                    MessageBoxButton.YesNo,
                    MessageBoxImage.Question
                );
                if (res == MessageBoxResult.Yes)
                {
                    DatabaseService.DeleteScheduleTag(tag.Name);
                    RenderTagManagementList();
                    RefreshEventCategoryCombo();
                    RenderMyCalendarsList();
                    RenderAllCalendarViews();
                }
            };

            var colorBtn = new Button
            {
                Content = "🎨",
                Style = (Style)FindResource("GoogleTopBtnStyle"),
                Width = 28,
                Height = 28,
                FontSize = 11,
                ToolTip = "调整标签色彩 (RGB 实时调色)",
                Margin = new Thickness(0, 0, 4, 0)
            };
            colorBtn.Click += (s, e) =>
            {
                OpenTagColorPicker(tag.Name, tag.ColorHex);
            };

            rightSp.Children.Add(colorBtn);
            rightSp.Children.Add(editBtn);
            rightSp.Children.Add(deleteBtn);
            Grid.SetColumn(rightSp, 1);
            grid.Children.Add(rightSp);

            rowBorder.Child = grid;
            TagItemsListPanel.Children.Add(rowBorder);
        }
    }

    // =========================================================================
    // ================= RGB COLOR PICKER & EVENT THEME PREVIEW ================
    // =========================================================================

    private string _rgbPickerTarget = "EVENT";

    private void OnOpenCustomColorPickerClicked(object sender, RoutedEventArgs e)
    {
        _rgbPickerTarget = "EVENT";
        string startHex = "#1A73E8";
        if (EventColorCombo?.SelectedItem is ComboBoxItem cbi && cbi.Tag is string tag && !string.IsNullOrEmpty(tag) && tag != "CUSTOM_POPUP")
        {
            startHex = tag;
        }
        else if (!string.IsNullOrEmpty(_currentRgbPickerHex))
        {
            startHex = _currentRgbPickerHex;
        }

        try
        {
            var col = (Color)ColorConverter.ConvertFromString(startHex);
            _isUpdatingRgbPicker = true;
            if (RgbSliderR != null) RgbSliderR.Value = col.R;
            if (RgbSliderG != null) RgbSliderG.Value = col.G;
            if (RgbSliderB != null) RgbSliderB.Value = col.B;
            if (RgbInputR != null) RgbInputR.Text = col.R.ToString();
            if (RgbInputG != null) RgbInputG.Text = col.G.ToString();
            if (RgbInputB != null) RgbInputB.Text = col.B.ToString();
            if (RgbInputHex != null) RgbInputHex.Text = $"#{col.R:X2}{col.G:X2}{col.B:X2}";
            _currentRgbPickerHex = $"#{col.R:X2}{col.G:X2}{col.B:X2}";
            _isUpdatingRgbPicker = false;

            UpdateRgbPickerVisuals(col.R, col.G, col.B);
        }
        catch { }

        // 同步日程卡片文本信息
        if (RgbModalPreviewTitle != null)
        {
            string title = EventTitleInput?.Text?.Trim() ?? "";
            RgbModalPreviewTitle.Text = string.IsNullOrEmpty(title) ? "示例日程时间块" : title;
        }
        if (RgbModalPreviewTagText != null)
        {
            string cat = (EventCategoryCombo?.SelectedItem as ComboBoxItem)?.Content?.ToString() ?? "工作";
            RgbModalPreviewTagText.Text = string.IsNullOrWhiteSpace(cat) ? "工作" : cat;
        }
        if (RgbModalPreviewTime != null)
        {
            string st = EventStartTimeCombo?.Text?.Trim() ?? "17:15";
            string et = EventEndTimeCombo?.Text?.Trim() ?? "19:45";
            RgbModalPreviewTime.Text = $"{st} - {et}";
        }

        if (RgbColorPickerModal != null)
        {
            RgbColorPickerModal.Visibility = Visibility.Visible;
        }
    }

    private void OnOpenCourseCustomColorPickerClicked(object sender, RoutedEventArgs e)
    {
        _rgbPickerTarget = "COURSE";
        string startHex = string.IsNullOrEmpty(_selectedCourseColor) ? "#3B82F6" : _selectedCourseColor;

        try
        {
            var col = (Color)ColorConverter.ConvertFromString(startHex);
            _isUpdatingRgbPicker = true;
            if (RgbSliderR != null) RgbSliderR.Value = col.R;
            if (RgbSliderG != null) RgbSliderG.Value = col.G;
            if (RgbSliderB != null) RgbSliderB.Value = col.B;
            if (RgbInputR != null) RgbInputR.Text = col.R.ToString();
            if (RgbInputG != null) RgbInputG.Text = col.G.ToString();
            if (RgbInputB != null) RgbInputB.Text = col.B.ToString();
            if (RgbInputHex != null) RgbInputHex.Text = $"#{col.R:X2}{col.G:X2}{col.B:X2}";
            _currentRgbPickerHex = $"#{col.R:X2}{col.G:X2}{col.B:X2}";
            _isUpdatingRgbPicker = false;

            UpdateRgbPickerVisuals(col.R, col.G, col.B);
        }
        catch { }

        if (RgbModalPreviewTitle != null)
        {
            string cname = CourseNameInput?.Text?.Trim() ?? "";
            RgbModalPreviewTitle.Text = string.IsNullOrEmpty(cname) ? "课程卡片" : cname;
        }
        if (RgbModalPreviewTagText != null)
        {
            RgbModalPreviewTagText.Text = "课程";
        }
        if (RgbModalPreviewTime != null)
        {
            RgbModalPreviewTime.Text = "第1节 - 第2节";
        }

        if (RgbColorPickerModal != null)
        {
            RgbColorPickerModal.Visibility = Visibility.Visible;
        }
    }

    private void OnOpenNewTagRgbPickerClicked(object sender, RoutedEventArgs e)
    {
        _rgbPickerTarget = "TAG_NEW";
        string startHex = "#1A73E8";
        if (NewTagColorCombo?.SelectedItem is ComboBoxItem cbi && cbi.Tag is string tag && !string.IsNullOrEmpty(tag))
        {
            startHex = tag;
        }

        try
        {
            var col = (Color)ColorConverter.ConvertFromString(startHex);
            _isUpdatingRgbPicker = true;
            if (RgbSliderR != null) RgbSliderR.Value = col.R;
            if (RgbSliderG != null) RgbSliderG.Value = col.G;
            if (RgbSliderB != null) RgbSliderB.Value = col.B;
            if (RgbInputR != null) RgbInputR.Text = col.R.ToString();
            if (RgbInputG != null) RgbInputG.Text = col.G.ToString();
            if (RgbInputB != null) RgbInputB.Text = col.B.ToString();
            if (RgbInputHex != null) RgbInputHex.Text = $"#{col.R:X2}{col.G:X2}{col.B:X2}";
            _currentRgbPickerHex = $"#{col.R:X2}{col.G:X2}{col.B:X2}";
            _isUpdatingRgbPicker = false;

            UpdateRgbPickerVisuals(col.R, col.G, col.B);
        }
        catch { }

        if (RgbModalPreviewTitle != null)
        {
            string tName = NewTagNameInput?.Text?.Trim() ?? "";
            RgbModalPreviewTitle.Text = string.IsNullOrEmpty(tName) ? "新标签" : tName;
        }
        if (RgbModalPreviewTagText != null)
        {
            string tName = NewTagNameInput?.Text?.Trim() ?? "";
            RgbModalPreviewTagText.Text = string.IsNullOrEmpty(tName) ? "新标签" : tName;
        }
        if (RgbModalPreviewTime != null)
        {
            RgbModalPreviewTime.Text = "新标签色彩选色";
        }

        if (RgbColorPickerModal != null)
        {
            RgbColorPickerModal.Visibility = Visibility.Visible;
        }
    }

    private void OpenTagColorPicker(string tagName, string currentHex)
    {
        _rgbPickerTarget = $"TAG_EDIT:{tagName}";
        string startHex = string.IsNullOrEmpty(currentHex) ? "#1A73E8" : currentHex;

        try
        {
            var col = (Color)ColorConverter.ConvertFromString(startHex);
            _isUpdatingRgbPicker = true;
            if (RgbSliderR != null) RgbSliderR.Value = col.R;
            if (RgbSliderG != null) RgbSliderG.Value = col.G;
            if (RgbSliderB != null) RgbSliderB.Value = col.B;
            if (RgbInputR != null) RgbInputR.Text = col.R.ToString();
            if (RgbInputG != null) RgbInputG.Text = col.G.ToString();
            if (RgbInputB != null) RgbInputB.Text = col.B.ToString();
            if (RgbInputHex != null) RgbInputHex.Text = $"#{col.R:X2}{col.G:X2}{col.B:X2}";
            _currentRgbPickerHex = $"#{col.R:X2}{col.G:X2}{col.B:X2}";
            _isUpdatingRgbPicker = false;

            UpdateRgbPickerVisuals(col.R, col.G, col.B);
        }
        catch { }

        if (RgbModalPreviewTitle != null)
        {
            RgbModalPreviewTitle.Text = tagName;
        }
        if (RgbModalPreviewTagText != null)
        {
            RgbModalPreviewTagText.Text = tagName;
        }
        if (RgbModalPreviewTime != null)
        {
            RgbModalPreviewTime.Text = "标签色彩实时调色";
        }

        if (RgbColorPickerModal != null)
        {
            RgbColorPickerModal.Visibility = Visibility.Visible;
        }
    }

    private void OnCloseRgbColorPickerClicked(object sender, RoutedEventArgs e)
    {
        if (RgbColorPickerModal != null)
        {
            RgbColorPickerModal.Visibility = Visibility.Collapsed;
        }

        if (_rgbPickerTarget == "EVENT" && EventColorCombo?.SelectedItem is ComboBoxItem cbi && (string)cbi.Tag == "CUSTOM_POPUP")
        {
            EventColorCombo.SelectedIndex = 0;
            UpdateEventColorPreview();
        }
    }

    private void OnApplyRgbColorPickerClicked(object sender, RoutedEventArgs e)
    {
        if (RgbColorPickerModal != null)
        {
            RgbColorPickerModal.Visibility = Visibility.Collapsed;
        }

        string hex = _currentRgbPickerHex;
        if (string.IsNullOrEmpty(hex) || !hex.StartsWith("#") || hex.Length != 7)
        {
            hex = "#1A73E8";
        }

        if (_rgbPickerTarget == "THEME")
        {
            string cleanHex = hex.ToUpperInvariant();
            if (ThemeCustomAccentHexInput != null)
            {
                ThemeCustomAccentHexInput.Text = cleanHex;
            }
            var config = ConfigService.Load();
            config.AccentColorHex = cleanHex;
            ConfigService.Save(config);
            ThemeService.ApplyTheme(this, config);
            ThemeStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
            ThemeStatusText.Text = $"✓ 已自定义主题强调色为 {cleanHex}，配置已保存！";
            return;
        }

        if (_rgbPickerTarget == "TAG_NEW")
        {
            string cleanHex = hex.ToUpperInvariant();
            bool foundTag = false;
            if (NewTagColorCombo != null)
            {
                for (int i = 0; i < NewTagColorCombo.Items.Count; i++)
                {
                    if (NewTagColorCombo.Items[i] is ComboBoxItem cbi && (cbi.Tag as string) == cleanHex)
                    {
                        NewTagColorCombo.SelectedIndex = i;
                        foundTag = true;
                        break;
                    }
                }
                if (!foundTag)
                {
                    var customTagItem = new ComboBoxItem
                    {
                        Content = $"🎨 自选色 ({cleanHex})",
                        Tag = cleanHex
                    };
                    NewTagColorCombo.Items.Add(customTagItem);
                    NewTagColorCombo.SelectedItem = customTagItem;
                }
            }
            return;
        }

        if (_rgbPickerTarget.StartsWith("TAG_EDIT:"))
        {
            string tagName = _rgbPickerTarget.Substring("TAG_EDIT:".Length);
            string cleanHex = hex.ToUpperInvariant();
            DatabaseService.UpdateScheduleTagColor(tagName, cleanHex);
            RenderTagManagementList();
            RefreshEventCategoryCombo();
            RenderMyCalendarsList();
            RenderAllCalendarViews();
            return;
        }

        if (_rgbPickerTarget == "COURSE")
        {
            _selectedCourseColor = hex.ToUpperInvariant();
            UpdateCourseColorPickerSelection();
            return;
        }

        // 查找或添加自定义色彩至下拉列表
        bool found = false;
        int targetIdx = -1;
        ComboBoxItem? existingCustomItem = null;

        for (int i = 0; i < EventColorCombo.Items.Count; i++)
        {
            if (EventColorCombo.Items[i] is ComboBoxItem cbi)
            {
                string tag = cbi.Tag as string ?? "";
                if (tag.Equals(hex, StringComparison.OrdinalIgnoreCase))
                {
                    targetIdx = i;
                    found = true;
                    break;
                }
                if (tag != "CUSTOM_POPUP" && (cbi.Content?.ToString()?.StartsWith("🎨 自定义") ?? false))
                {
                    existingCustomItem = cbi;
                }
            }
        }

        if (found && targetIdx >= 0)
        {
            EventColorCombo.SelectedIndex = targetIdx;
        }
        else if (existingCustomItem != null)
        {
            existingCustomItem.Content = $"🎨 自定义 ({hex.ToUpperInvariant()})";
            existingCustomItem.Tag = hex.ToUpperInvariant();
            EventColorCombo.SelectedItem = existingCustomItem;
        }
        else
        {
            var customItem = new ComboBoxItem
            {
                Content = $"🎨 自定义 ({hex.ToUpperInvariant()})",
                Tag = hex.ToUpperInvariant()
            };
            int insertPos = Math.Max(0, EventColorCombo.Items.Count - 1);
            EventColorCombo.Items.Insert(insertPos, customItem);
            EventColorCombo.SelectedItem = customItem;
        }

        UpdateEventColorPreview(hex);
    }

    private void OnColorPreviewCardClicked(object sender, MouseButtonEventArgs e)
    {
        OnOpenCustomColorPickerClicked(sender, e);
    }

    private void OnEventColorComboSelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (EventColorCombo?.SelectedItem is ComboBoxItem cbi)
        {
            string? tag = cbi.Tag as string;
            if (tag == "CUSTOM_POPUP")
            {
                OnOpenCustomColorPickerClicked(sender, e);
                return;
            }
            UpdateEventColorPreview(tag);
        }
    }

    private void OnEventTitleInputTextChanged(object sender, TextChangedEventArgs e)
    {
        UpdateEventColorPreview();
    }

    private void OnEventCategoryComboSelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        UpdateEventColorPreview();
    }

    private void UpdateEventColorPreview(string? specificHex = null)
    {
        string hex = "#1A73E8";
        if (!string.IsNullOrEmpty(specificHex) && specificHex != "CUSTOM_POPUP")
        {
            hex = specificHex;
        }
        else if (EventColorCombo?.SelectedItem is ComboBoxItem cbi && cbi.Tag is string tag && !string.IsNullOrEmpty(tag) && tag != "CUSTOM_POPUP")
        {
            hex = tag;
        }

        try
        {
            var color = (Color)ColorConverter.ConvertFromString(hex);
            var solidBrush = new SolidColorBrush(color);
            var bgBrush = new SolidColorBrush(Color.FromArgb(40, color.R, color.G, color.B));

            if (EventColorPreviewCard != null)
            {
                EventColorPreviewCard.BorderBrush = solidBrush;
                EventColorPreviewCard.Background = bgBrush;
            }
            if (EventColorPreviewSwatch != null)
            {
                EventColorPreviewSwatch.Background = solidBrush;
            }
            if (EventColorPreviewTagBadge != null)
            {
                EventColorPreviewTagBadge.Background = new SolidColorBrush(Color.FromArgb(45, color.R, color.G, color.B));
            }
            if (EventColorPreviewTagText != null)
            {
                EventColorPreviewTagText.Foreground = solidBrush;
                string cat = (EventCategoryCombo?.SelectedItem as ComboBoxItem)?.Content?.ToString() ?? "工作";
                EventColorPreviewTagText.Text = string.IsNullOrWhiteSpace(cat) ? "工作" : cat;
            }
            if (EventColorPreviewHex != null)
            {
                EventColorPreviewHex.Text = hex.ToUpperInvariant();
            }
            if (EventColorPreviewTitle != null)
            {
                string title = EventTitleInput?.Text?.Trim() ?? "";
                EventColorPreviewTitle.Text = string.IsNullOrEmpty(title) ? "示例日程：深度专注" : title;
            }
            if (EventColorPreviewTime != null)
            {
                string st = EventStartTimeCombo?.Text?.Trim() ?? "17:15";
                string et = EventEndTimeCombo?.Text?.Trim() ?? "19:45";
                EventColorPreviewTime.Text = $"{st} - {et}";
            }
        }
        catch { }
    }

    private void OnRgbSliderValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        if (_isUpdatingRgbPicker || RgbSliderR == null || RgbSliderG == null || RgbSliderB == null) return;

        byte r = (byte)Math.Clamp(Math.Round(RgbSliderR.Value), 0, 255);
        byte g = (byte)Math.Clamp(Math.Round(RgbSliderG.Value), 0, 255);
        byte b = (byte)Math.Clamp(Math.Round(RgbSliderB.Value), 0, 255);

        _isUpdatingRgbPicker = true;
        if (RgbInputR != null) RgbInputR.Text = r.ToString();
        if (RgbInputG != null) RgbInputG.Text = g.ToString();
        if (RgbInputB != null) RgbInputB.Text = b.ToString();
        _currentRgbPickerHex = $"#{r:X2}{g:X2}{b:X2}";
        if (RgbInputHex != null) RgbInputHex.Text = _currentRgbPickerHex;
        _isUpdatingRgbPicker = false;

        UpdateRgbPickerVisuals(r, g, b);
    }

    private void OnRgbInputTextChanged(object sender, TextChangedEventArgs e)
    {
        if (_isUpdatingRgbPicker || RgbInputR == null || RgbInputG == null || RgbInputB == null) return;

        if (byte.TryParse(RgbInputR.Text.Trim(), out byte r) &&
            byte.TryParse(RgbInputG.Text.Trim(), out byte g) &&
            byte.TryParse(RgbInputB.Text.Trim(), out byte b))
        {
            _isUpdatingRgbPicker = true;
            if (RgbSliderR != null) RgbSliderR.Value = r;
            if (RgbSliderG != null) RgbSliderG.Value = g;
            if (RgbSliderB != null) RgbSliderB.Value = b;
            _currentRgbPickerHex = $"#{r:X2}{g:X2}{b:X2}";
            if (RgbInputHex != null) RgbInputHex.Text = _currentRgbPickerHex;
            _isUpdatingRgbPicker = false;

            UpdateRgbPickerVisuals(r, g, b);
        }
    }

    private void OnRgbHexInputTextChanged(object sender, TextChangedEventArgs e)
    {
        if (_isUpdatingRgbPicker || RgbInputHex == null) return;

        string hex = RgbInputHex.Text.Trim();
        if (!hex.StartsWith("#")) hex = "#" + hex;

        if (hex.Length == 7)
        {
            try
            {
                var col = (Color)ColorConverter.ConvertFromString(hex);
                _isUpdatingRgbPicker = true;
                if (RgbSliderR != null) RgbSliderR.Value = col.R;
                if (RgbSliderG != null) RgbSliderG.Value = col.G;
                if (RgbSliderB != null) RgbSliderB.Value = col.B;
                if (RgbInputR != null) RgbInputR.Text = col.R.ToString();
                if (RgbInputG != null) RgbInputG.Text = col.G.ToString();
                if (RgbInputB != null) RgbInputB.Text = col.B.ToString();
                _currentRgbPickerHex = hex.ToUpperInvariant();
                _isUpdatingRgbPicker = false;

                UpdateRgbPickerVisuals(col.R, col.G, col.B);
            }
            catch { }
        }
    }

    private void OnRgbPresetSwatchClicked(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is string hex && !string.IsNullOrEmpty(hex))
        {
            try
            {
                var col = (Color)ColorConverter.ConvertFromString(hex);
                _isUpdatingRgbPicker = true;
                if (RgbSliderR != null) RgbSliderR.Value = col.R;
                if (RgbSliderG != null) RgbSliderG.Value = col.G;
                if (RgbSliderB != null) RgbSliderB.Value = col.B;
                if (RgbInputR != null) RgbInputR.Text = col.R.ToString();
                if (RgbInputG != null) RgbInputG.Text = col.G.ToString();
                if (RgbInputB != null) RgbInputB.Text = col.B.ToString();
                _currentRgbPickerHex = hex.ToUpperInvariant();
                if (RgbInputHex != null) RgbInputHex.Text = _currentRgbPickerHex;
                _isUpdatingRgbPicker = false;

                UpdateRgbPickerVisuals(col.R, col.G, col.B);
            }
            catch { }
        }
    }

    private void UpdateRgbPickerVisuals(byte r, byte g, byte b)
    {
        var solid = new SolidColorBrush(Color.FromRgb(r, g, b));
        var bg = new SolidColorBrush(Color.FromArgb(40, r, g, b));

        if (RgbModalSwatchBox != null) RgbModalSwatchBox.Background = solid;
        if (RgbModalHexLabel != null) RgbModalHexLabel.Text = _currentRgbPickerHex;
        if (RgbModalScheduleCardPreview != null)
        {
            RgbModalScheduleCardPreview.BorderBrush = solid;
            RgbModalScheduleCardPreview.Background = bg;
        }
        if (RgbModalPreviewTagBadge != null)
        {
            RgbModalPreviewTagBadge.Background = new SolidColorBrush(Color.FromArgb(50, r, g, b));
        }
        if (RgbModalPreviewTagText != null)
        {
            RgbModalPreviewTagText.Foreground = solid;
        }
    }

    // =========================================================================
    // ================= SCHEDULE SUMMARY & RETROSPECTIVE LOGIC ================
    // =========================================================================

    private string _summaryPeriodMode = "WEEK"; // DAY, WEEK, MONTH, 30DAYS, ALL
    private DateTime _summaryPeriodAnchor = DateTime.Today;

    private void OnRefreshSummaryClicked(object sender, RoutedEventArgs e)
    {
        RenderSummaryPage();
    }

    private void OnSummaryFilterChanged(object sender, SelectionChangedEventArgs e)
    {
        if (SummaryPageContainer != null && SummaryPageContainer.Visibility == Visibility.Visible)
        {
            RenderSummaryPage();
        }
    }

    private void OnSummarySearchInputChanged(object sender, TextChangedEventArgs e)
    {
        if (SummaryPageContainer != null && SummaryPageContainer.Visibility == Visibility.Visible)
        {
            RenderSummaryPage();
        }
    }

    private void OnSummaryPeriodSelectDay(object sender, RoutedEventArgs e)
    {
        _summaryPeriodMode = "DAY";
        _summaryPeriodAnchor = DateTime.Today;
        UpdateSummaryPeriodButtonsStyle();
        RenderSummaryPage();
    }

    private void OnSummaryPeriodSelectWeek(object sender, RoutedEventArgs e)
    {
        _summaryPeriodMode = "WEEK";
        _summaryPeriodAnchor = DateTime.Today;
        UpdateSummaryPeriodButtonsStyle();
        RenderSummaryPage();
    }

    private void OnSummaryPeriodSelectMonth(object sender, RoutedEventArgs e)
    {
        _summaryPeriodMode = "MONTH";
        _summaryPeriodAnchor = DateTime.Today;
        UpdateSummaryPeriodButtonsStyle();
        RenderSummaryPage();
    }

    private void OnSummaryPeriodSelect30Days(object sender, RoutedEventArgs e)
    {
        _summaryPeriodMode = "30DAYS";
        _summaryPeriodAnchor = DateTime.Today;
        UpdateSummaryPeriodButtonsStyle();
        RenderSummaryPage();
    }

    private void OnSummaryPeriodSelectAll(object sender, RoutedEventArgs e)
    {
        _summaryPeriodMode = "ALL";
        _summaryPeriodAnchor = DateTime.Today;
        UpdateSummaryPeriodButtonsStyle();
        RenderSummaryPage();
    }

    private void OnSummaryPeriodPrevClicked(object sender, RoutedEventArgs e)
    {
        if (_summaryPeriodMode == "DAY") _summaryPeriodAnchor = _summaryPeriodAnchor.AddDays(-1);
        else if (_summaryPeriodMode == "WEEK") _summaryPeriodAnchor = _summaryPeriodAnchor.AddDays(-7);
        else if (_summaryPeriodMode == "MONTH") _summaryPeriodAnchor = _summaryPeriodAnchor.AddMonths(-1);
        else if (_summaryPeriodMode == "30DAYS") _summaryPeriodAnchor = _summaryPeriodAnchor.AddDays(-30);
        RenderSummaryPage();
    }

    private void OnSummaryPeriodNextClicked(object sender, RoutedEventArgs e)
    {
        if (_summaryPeriodMode == "DAY") _summaryPeriodAnchor = _summaryPeriodAnchor.AddDays(1);
        else if (_summaryPeriodMode == "WEEK") _summaryPeriodAnchor = _summaryPeriodAnchor.AddDays(7);
        else if (_summaryPeriodMode == "MONTH") _summaryPeriodAnchor = _summaryPeriodAnchor.AddMonths(1);
        else if (_summaryPeriodMode == "30DAYS") _summaryPeriodAnchor = _summaryPeriodAnchor.AddDays(30);
        RenderSummaryPage();
    }

    private void OnSummaryPeriodTodayClicked(object sender, RoutedEventArgs e)
    {
        _summaryPeriodAnchor = DateTime.Today;
        RenderSummaryPage();
    }

    private DateTime _dailyClockSelectedDate = DateTime.Today;

    private void OnDailyClockPrevDayClicked(object sender, RoutedEventArgs e)
    {
        _dailyClockSelectedDate = _dailyClockSelectedDate.AddDays(-1);
        RenderDailyClockSection();
    }

    private void OnDailyClockNextDayClicked(object sender, RoutedEventArgs e)
    {
        _dailyClockSelectedDate = _dailyClockSelectedDate.AddDays(1);
        RenderDailyClockSection();
    }

    private void OnDailyClockTodayClicked(object sender, RoutedEventArgs e)
    {
        _dailyClockSelectedDate = DateTime.Today;
        RenderDailyClockSection();
    }

    private void OnDailyClockDatePickerChanged(object sender, SelectionChangedEventArgs e)
    {
        if (DailyClockDatePicker != null && DailyClockDatePicker.SelectedDate.HasValue && DailyClockDatePicker.SelectedDate.Value.Date != _dailyClockSelectedDate.Date)
        {
            _dailyClockSelectedDate = DailyClockDatePicker.SelectedDate.Value.Date;
            RenderDailyClockSection();
        }
    }

    private void UpdateSummaryPeriodButtonsStyle()
    {
        if (SummaryPeriodBtnDay == null) return;
        var list = new (Button btn, string mode)[]
        {
            (SummaryPeriodBtnDay, "DAY"),
            (SummaryPeriodBtnWeek, "WEEK"),
            (SummaryPeriodBtnMonth, "MONTH"),
            (SummaryPeriodBtn30Days, "30DAYS"),
            (SummaryPeriodBtnAll, "ALL")
        };

        foreach (var item in list)
        {
            if (item.btn == null) continue;
            bool active = _summaryPeriodMode == item.mode;
            item.btn.Background = active ? new SolidColorBrush(Color.FromRgb(0x1A, 0x73, 0xE8)) : Brushes.Transparent;
            item.btn.Foreground = active ? Brushes.White : ThemeService.CurrentTextBrush;
            item.btn.FontWeight = active ? FontWeights.SemiBold : FontWeights.Normal;
        }
    }

    private void RenderSummaryPage()
    {
        if (SummaryItemsListPanel == null) return;
        SummaryItemsListPanel.Children.Clear();

        UpdateSummaryPeriodButtonsStyle();

        var allSchedules = DatabaseService.GetAllSchedules();
        var culture = new CultureInfo("zh-CN");

        // 1. 计算统计周期起止范围与标题
        DateTime startRange = DateTime.MinValue;
        DateTime endRange = DateTime.MaxValue;
        int totalDaysInPeriod = 7;

        if (_summaryPeriodMode == "DAY")
        {
            startRange = _summaryPeriodAnchor.Date;
            endRange = startRange.AddDays(1);
            totalDaysInPeriod = 1;
            SummaryPeriodRangeText.Text = _summaryPeriodAnchor.ToString("yyyy年M月d日 · dddd", culture);
            SummaryPeriodBriefText.Text = "单日效能盘点";
        }
        else if (_summaryPeriodMode == "WEEK")
        {
            int diff = (7 + (_summaryPeriodAnchor.DayOfWeek - DayOfWeek.Monday)) % 7;
            DateTime monday = _summaryPeriodAnchor.AddDays(-diff).Date;
            DateTime sunday = monday.AddDays(6);
            startRange = monday;
            endRange = monday.AddDays(7);
            totalDaysInPeriod = 7;
            int weekOfYear = culture.Calendar.GetWeekOfYear(monday, CalendarWeekRule.FirstFourDayWeek, DayOfWeek.Monday);
            SummaryPeriodRangeText.Text = $"{monday:yyyy年M月d日} - {sunday:M月d日} (第{weekOfYear}周)";
            SummaryPeriodBriefText.Text = "周维度专注与交付走势";
        }
        else if (_summaryPeriodMode == "MONTH")
        {
            startRange = new DateTime(_summaryPeriodAnchor.Year, _summaryPeriodAnchor.Month, 1);
            endRange = startRange.AddMonths(1);
            totalDaysInPeriod = DateTime.DaysInMonth(_summaryPeriodAnchor.Year, _summaryPeriodAnchor.Month);
            SummaryPeriodRangeText.Text = $"{_summaryPeriodAnchor:yyyy年M月} (月度汇总)";
            SummaryPeriodBriefText.Text = "月度效能大盘";
        }
        else if (_summaryPeriodMode == "30DAYS")
        {
            startRange = _summaryPeriodAnchor.Date.AddDays(-29);
            endRange = _summaryPeriodAnchor.Date.AddDays(1);
            totalDaysInPeriod = 30;
            SummaryPeriodRangeText.Text = $"{startRange:M月d日} - {_summaryPeriodAnchor:M月d日} (近30天)";
            SummaryPeriodBriefText.Text = "滚动30天复盘";
        }
        else
        {
            startRange = DateTime.MinValue;
            endRange = DateTime.MaxValue;
            totalDaysInPeriod = 0;
            SummaryPeriodRangeText.Text = "全部历史全量汇总";
            SummaryPeriodBriefText.Text = "全生命周期成果库";
        }

        // 2. 成就激励计算 (Streak 连续达成天数 + Karma 效能积分)
        var allDoneTasks = allSchedules.Where(s => s.Status.Equals("COMPLETED", StringComparison.OrdinalIgnoreCase)).ToList();
        var doneDates = allDoneTasks.Select(s => s.StartTime.Date).Distinct().ToHashSet();

        int streak = 0;
        DateTime cursorDate = DateTime.Today;
        if (!doneDates.Contains(cursorDate) && doneDates.Contains(cursorDate.AddDays(-1)))
        {
            cursorDate = cursorDate.AddDays(-1);
        }
        while (doneDates.Contains(cursorDate))
        {
            streak++;
            cursorDate = cursorDate.AddDays(-1);
        }
        SummaryStreakBadgeText.Text = streak > 0 ? $"🔥 连续专注 {streak} 天" : "🔥 今日起步·开启连胜";

        double allDeepHours = allDoneTasks.Where(s => s.WorkType == "DEEP_WORK").Sum(s => (s.ActualMinutes > 0 ? s.ActualMinutes : s.EstimatedMinutes) / 60.0);
        int karma = (allDoneTasks.Count * 15) + (int)(allDeepHours * 10);
        string karmaLevel = karma switch
        {
            >= 1200 => "宗师级 🏆",
            >= 700 => "王者 ⚡",
            >= 350 => "进阶达人 🚀",
            >= 100 => "专注先锋 🌟",
            _ => "新手起步 🌱"
        };
        SummaryKarmaBadgeText.Text = $"⚡ 效能积分: {karma} ({karmaLevel})";

        // 3. 筛选当前周期内事项
        IEnumerable<ScheduleItem> filtered = allSchedules;
        if (_summaryPeriodMode != "ALL")
        {
            filtered = filtered.Where(s => s.StartTime >= startRange && s.StartTime < endRange);
        }

        // 状态筛选
        string statusFilter = (SummaryStatusCombo?.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "ALL";
        if (statusFilter != "ALL")
        {
            filtered = filtered.Where(s => s.Status.Equals(statusFilter, StringComparison.OrdinalIgnoreCase));
        }

        // 分类筛选
        string catFilter = (SummaryCategoryCombo?.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "ALL";
        if (catFilter != "ALL")
        {
            filtered = filtered.Where(s => s.Category.Equals(catFilter, StringComparison.OrdinalIgnoreCase));
        }

        // 认知负荷筛选
        string wtFilter = (SummaryWorkTypeCombo?.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "ALL";
        if (wtFilter != "ALL")
        {
            filtered = filtered.Where(s => s.WorkType.Equals(wtFilter, StringComparison.OrdinalIgnoreCase));
        }

        // 关键字搜索
        string keyword = SummarySearchInput?.Text?.Trim() ?? string.Empty;
        if (!string.IsNullOrEmpty(keyword))
        {
            filtered = filtered.Where(s => s.Title.Contains(keyword, StringComparison.OrdinalIgnoreCase) ||
                                           (s.Description != null && s.Description.Contains(keyword, StringComparison.OrdinalIgnoreCase)) ||
                                           (s.Dod != null && s.Dod.Contains(keyword, StringComparison.OrdinalIgnoreCase)));
        }

        var list = filtered.OrderByDescending(s => s.StartTime).ToList();

        // 4. 统计 KPI 指标
        int total = list.Count;
        int completed = list.Count(s => s.Status.Equals("COMPLETED", StringComparison.OrdinalIgnoreCase));
        int inProgress = list.Count(s => s.Status.Equals("IN_PROGRESS", StringComparison.OrdinalIgnoreCase));
        int pending = list.Count(s => s.Status.Equals("PENDING", StringComparison.OrdinalIgnoreCase) || s.Status.Equals("POSTPONED", StringComparison.OrdinalIgnoreCase));

        double completionRate = total > 0 ? (completed * 100.0 / total) : 0.0;

        double totalHours = 0.0;
        double deepHours = 0.0;
        double shallowHours = 0.0;
        int dodTotal = 0;
        int dodCompleted = 0;

        foreach (var item in list)
        {
            double durationMinutes = item.ActualMinutes > 0 ? item.ActualMinutes :
                (item.EndTime > item.StartTime ? (item.EndTime - item.StartTime).TotalMinutes : item.EstimatedMinutes);

            totalHours += durationMinutes / 60.0;
            if (item.WorkType == "DEEP_WORK")
            {
                deepHours += durationMinutes / 60.0;
            }
            else if (item.WorkType == "SHALLOW_WORK")
            {
                shallowHours += durationMinutes / 60.0;
            }

            if (!string.IsNullOrWhiteSpace(item.Dod))
            {
                dodTotal++;
                if (item.Status.Equals("COMPLETED", StringComparison.OrdinalIgnoreCase))
                {
                    dodCompleted++;
                }
            }
        }

        double deepPurityRate = totalHours > 0 ? (deepHours * 100.0 / totalHours) : 0.0;
        double dodRate = dodTotal > 0 ? (dodCompleted * 100.0 / dodTotal) : 0.0;

        double dailyAvg = totalDaysInPeriod > 0 ? (totalHours / totalDaysInPeriod) : (list.Select(s => s.StartTime.Date).Distinct().Count() is int d && d > 0 ? totalHours / d : totalHours);

        // 更新 KPI 卡片
        SummaryCompletionRateText.Text = $"{completionRate:0.0}%";
        SummaryCompletionProgressBar.Value = completionRate;
        SummaryCompletedTasksText.Text = $"已交付 {completed} 项 / 总计 {total} 项 (待办 {pending} · 进行中 {inProgress})";

        SummaryTotalHoursText.Text = $"{totalHours:0.0} h";
        SummaryDeepWorkHoursText.Text = $"深度: {deepHours:0.0} h · 浅层: {shallowHours:0.0} h";
        SummaryDailyAvgHoursText.Text = $"日均专注: {dailyAvg:0.0} h/天";

        SummaryDeepPurityRateText.Text = $"{deepPurityRate:0.0}%";
        SummaryDeepPurityProgressBar.Value = deepPurityRate;
        SummaryDeepWorkRatioDescText.Text = deepPurityRate >= 70 ? "🔥 高纯度黄金心流投入" : (deepPurityRate >= 40 ? "⚡ 深度浅层均衡配比" : "☕ 浅层与日常事务为主");

        SummaryDodRateText.Text = $"{dodRate:0.0}%";
        SummaryDodProgressBar.Value = dodRate;
        SummaryDodCountText.Text = $"含验收标准: {dodTotal} 项 (已达标 {dodCompleted} 项)";

        // 5. 渲染 每日 24 小时全景作息时钟盘 (Daily Clock Diagram)
        RenderDailyClockSection();

        // 6. 渲染 周期交付走势柱状图 (7-Day / Period Trend)
        RenderWeeklyTrendBars(allSchedules);

        // 7. 渲染分类工时环形图 (Category Donut Chart)
        RenderCategoryDonutChart(list);

        // 8. 渲染 24小时专注活跃时段热力分布 (Hourly Activity Bar Chart)
        RenderHourlyActivityBars(list);

        // 7. 渲染 Todo 风格任务复盘列表
        if (list.Count == 0)
        {
            var emptyPanel = new StackPanel
            {
                Margin = new Thickness(0, 40, 0, 40),
                HorizontalAlignment = HorizontalAlignment.Center
            };
            emptyPanel.Children.Add(new TextBlock
            {
                Text = "🍃",
                FontSize = 36,
                HorizontalAlignment = HorizontalAlignment.Center
            });
            emptyPanel.Children.Add(new TextBlock
            {
                Text = "当前筛选周期内暂无日程事项",
                FontSize = 14,
                FontWeight = FontWeights.SemiBold,
                Foreground = ThemeService.CurrentTextBrush,
                Margin = new Thickness(0, 8, 0, 4),
                HorizontalAlignment = HorizontalAlignment.Center
            });
            emptyPanel.Children.Add(new TextBlock
            {
                Text = "可尝试切换上方统计周期或调整筛选条件",
                FontSize = 12,
                Foreground = ThemeService.CurrentSecondaryTextBrush,
                HorizontalAlignment = HorizontalAlignment.Center
            });
            SummaryItemsListPanel.Children.Add(emptyPanel);
            return;
        }

        foreach (var item in list)
        {
            var card = CreateScheduleSummaryCard(item);
            SummaryItemsListPanel.Children.Add(card);
        }
    }

    private void RenderWeeklyTrendBars(List<ScheduleItem> allSchedules)
    {
        if (SummaryTrendBarsGrid == null) return;
        SummaryTrendBarsGrid.Children.Clear();

        // 确定用于柱状图的基准周 (周一至周日)
        DateTime anchor = _summaryPeriodMode == "WEEK" ? _summaryPeriodAnchor : DateTime.Today;
        int diff = (7 + (anchor.DayOfWeek - DayOfWeek.Monday)) % 7;
        DateTime monday = anchor.AddDays(-diff).Date;

        var weekDays = new List<DateTime>();
        for (int i = 0; i < 7; i++)
        {
            weekDays.Add(monday.AddDays(i));
        }

        var dayStats = new List<(DateTime Date, int DoneCount, double Hours)>();
        foreach (var day in weekDays)
        {
            var itemsOnDay = allSchedules.Where(s => s.StartTime.Date == day.Date).ToList();
            int done = itemsOnDay.Count(s => s.Status.Equals("COMPLETED", StringComparison.OrdinalIgnoreCase));
            double hours = itemsOnDay.Where(s => s.Status.Equals("COMPLETED", StringComparison.OrdinalIgnoreCase))
                                     .Sum(s => (s.ActualMinutes > 0 ? s.ActualMinutes : s.EstimatedMinutes) / 60.0);
            dayStats.Add((day, done, hours));
        }

        int maxDone = dayStats.Max(x => x.DoneCount);
        int scaleMax = Math.Max(4, maxDone);

        int totalWeekDone = dayStats.Sum(x => x.DoneCount);
        double totalWeekHours = dayStats.Sum(x => x.Hours);
        SummaryTrendSubtitleText.Text = $"本周共交付 {totalWeekDone} 项任务 · 累计专注 {totalWeekHours:0.0} 小时";

        string[] weekNames = { "周一", "周二", "周三", "周四", "周五", "周六", "周日" };

        for (int i = 0; i < 7; i++)
        {
            var stat = dayStats[i];
            bool isToday = stat.Date.Date == DateTime.Today;

            var colStack = new StackPanel
            {
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Bottom,
                Margin = new Thickness(2, 0, 2, 0),
                ToolTip = $"{stat.Date:M月d日} {weekNames[i]}\n已交付: {stat.DoneCount} 项\n专注投入: {stat.Hours:0.0} 小时"
            };

            // 柱顶数字 (若完成0项则显示浅灰色横杠)
            var countLabel = new TextBlock
            {
                Text = stat.DoneCount > 0 ? stat.DoneCount.ToString() : "-",
                FontSize = 10,
                FontWeight = stat.DoneCount > 0 ? FontWeights.Bold : FontWeights.Normal,
                Foreground = stat.DoneCount > 0 ? ThemeService.CurrentTextBrush : ThemeService.CurrentMutedTextBrush,
                HorizontalAlignment = HorizontalAlignment.Center,
                Margin = new Thickness(0, 0, 0, 4)
            };
            colStack.Children.Add(countLabel);

            // 柱体
            double barHeight = stat.DoneCount > 0 ? Math.Max(12, (stat.DoneCount * 65.0) / scaleMax) : 4;
            var barBorder = new Border
            {
                Width = 20,
                Height = barHeight,
                CornerRadius = new CornerRadius(4, 4, 1, 1),
                Background = isToday
                    ? new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81)) // 今日高亮翡翠绿
                    : (stat.DoneCount > 0
                        ? new SolidColorBrush(Color.FromRgb(0x25, 0x63, 0xEB)) // 高质感亮蓝
                        : ThemeService.CurrentControlBrush), // 灰色基座
                Margin = new Thickness(0, 0, 0, 6)
            };
            colStack.Children.Add(barBorder);

            // 星期
            colStack.Children.Add(new TextBlock
            {
                Text = weekNames[i],
                FontSize = 10,
                FontWeight = isToday ? FontWeights.Bold : FontWeights.Normal,
                Foreground = isToday ? ThemeService.CurrentAccentBrush : ThemeService.CurrentSecondaryTextBrush,
                HorizontalAlignment = HorizontalAlignment.Center
            });

            // 日期 (例如 10/2)
            colStack.Children.Add(new TextBlock
            {
                Text = stat.Date.ToString("M/d"),
                FontSize = 9,
                Foreground = isToday ? ThemeService.CurrentAccentBrush : ThemeService.CurrentMutedTextBrush,
                HorizontalAlignment = HorizontalAlignment.Center,
                Margin = new Thickness(0, 2, 0, 0)
            });

            SummaryTrendBarsGrid.Children.Add(colStack);
        }

        // 走势峰值洞察
        var peakDay = dayStats.OrderByDescending(x => x.DoneCount).ThenByDescending(x => x.Hours).FirstOrDefault();
        if (peakDay.DoneCount > 0)
        {
            int peakIdx = weekDays.IndexOf(peakDay.Date);
            string peakWeekName = peakIdx >= 0 ? weekNames[peakIdx] : "某日";
            SummaryTrendInsightText.Text = $"💡 效率峰值：{peakWeekName} 为本周交付巅峰 (完成 {peakDay.DoneCount} 项 · 专注投入 {peakDay.Hours:0.0}h)！保持优秀专注节奏！";
        }
        else
        {
            SummaryTrendInsightText.Text = "💡 本周刚刚开始，从今日首个深度专注日程开始连胜吧！";
        }
    }

    // =========================================================================
    // ================= 🕒 每日 24 小时全景作息时钟盘 & 多维图表矩阵 ============
    // =========================================================================

    public class ClockTimeBlock
    {
        public string Id { get; set; } = "";
        public string Title { get; set; } = "";
        public DateTime StartTime { get; set; }
        public DateTime EndTime { get; set; }
        public string Category { get; set; } = "WORK";
        public string HexColor { get; set; } = "#3B82F6";
        public string WorkType { get; set; } = "DEEP_WORK";
        public string Status { get; set; } = "PENDING";
        public bool IsCourse { get; set; } = false;
        public string Location { get; set; } = "";
    }

    private List<ClockTimeBlock> GetDayScheduleAndCourses(DateTime date)
    {
        var list = new List<ClockTimeBlock>();

        // 1. 本地日程表
        var allSchedules = DatabaseService.GetAllSchedules();
        foreach (var s in allSchedules.Where(x => x.StartTime.Date == date.Date))
        {
            string color = s.WorkType == "DEEP_WORK" ? "#A78BFA" : (s.Category switch
            {
                "WORK" => "#3B82F6",
                "STUDY" => "#8B5CF6",
                "HEALTH" => "#10B981",
                "LIFE" => "#F59E0B",
                _ => "#38BDF8"
            });

            DateTime end = s.EndTime > s.StartTime ? s.EndTime : s.StartTime.AddMinutes(s.EstimatedMinutes > 0 ? s.EstimatedMinutes : 30);
            list.Add(new ClockTimeBlock
            {
                Id = s.Id,
                Title = s.Title,
                StartTime = s.StartTime,
                EndTime = end,
                Category = s.Category,
                HexColor = color,
                WorkType = s.WorkType,
                Status = s.Status,
                IsCourse = false
            });
        }

        // 2. 课程表课程 (如果配置并排期在今日)
        try
        {
            int dayOfWeek = date.DayOfWeek == DayOfWeek.Sunday ? 7 : (int)date.DayOfWeek;
            string semStartStr = DatabaseService.GetTimetableSetting("semester_start_date", "");
            if (DateTime.TryParse(semStartStr, out var semStart))
            {
                int diffDays = (date.Date - semStart.Date).Days;
                int weekNum = diffDays >= 0 ? (diffDays / 7) + 1 : 1;
                int totalWeeks = int.TryParse(DatabaseService.GetTimetableSetting("total_weeks", "20"), out int tw) ? tw : 20;

                if (weekNum >= 1 && weekNum <= totalWeeks)
                {
                    var allCourses = DatabaseService.GetAllCourses();
                    int totalSections = int.TryParse(DatabaseService.GetTimetableSetting("total_sections", "12"), out int ts) ? ts : 12;
                    var sectionTimes = GetConfiguredSectionTimes(totalSections);

                    foreach (var c in allCourses.Where(x => x.DayOfWeek == dayOfWeek && weekNum >= x.StartWeek && weekNum <= x.EndWeek))
                    {
                        bool weekMatches = c.WeekType == "ALL" || (c.WeekType == "ODD" && weekNum % 2 == 1) || (c.WeekType == "EVEN" && weekNum % 2 == 0);
                        if (!weekMatches) continue;

                        var startSlot = sectionTimes.FirstOrDefault(x => x.Section == c.StartSection);
                        var endSlot = sectionTimes.FirstOrDefault(x => x.Section == (c.StartSection + c.SectionSpan - 1)) ?? startSlot;

                        if (startSlot != null && endSlot != null)
                        {
                            if (TimeSpan.TryParse(startSlot.Start, out var st) && TimeSpan.TryParse(endSlot.End, out var et))
                            {
                                list.Add(new ClockTimeBlock
                                {
                                    Title = c.Name + (string.IsNullOrWhiteSpace(c.Location) ? "" : $" ({c.Location})"),
                                    StartTime = date.Date.Add(st),
                                    EndTime = date.Date.Add(et),
                                    Category = "COURSE",
                                    HexColor = string.IsNullOrWhiteSpace(c.ColorHex) ? "#06B6D4" : c.ColorHex,
                                    WorkType = "DEEP_WORK",
                                    Status = "COMPLETED",
                                    IsCourse = true,
                                    Location = c.Location ?? ""
                                });
                            }
                        }
                    }
                }
            }
        }
        catch { }

        return list.OrderBy(x => x.StartTime).ToList();
    }

    private static System.Windows.Shapes.Path CreateArcPath(Point center, double rOuter, double rInner, double startAngleDeg, double endAngleDeg, Brush fill, Brush stroke, double strokeThickness = 1.0)
    {
        if (endAngleDeg - startAngleDeg >= 360.0)
        {
            endAngleDeg = startAngleDeg + 359.999;
        }
        else if (endAngleDeg <= startAngleDeg)
        {
            endAngleDeg = startAngleDeg + 1.0;
        }

        double rad1 = startAngleDeg * Math.PI / 180.0;
        double rad2 = endAngleDeg * Math.PI / 180.0;

        Point p1 = new Point(center.X + rOuter * Math.Cos(rad1), center.Y + rOuter * Math.Sin(rad1));
        Point p2 = new Point(center.X + rOuter * Math.Cos(rad2), center.Y + rOuter * Math.Sin(rad2));
        Point p3 = new Point(center.X + rInner * Math.Cos(rad2), center.Y + rInner * Math.Sin(rad2));
        Point p4 = new Point(center.X + rInner * Math.Cos(rad1), center.Y + rInner * Math.Sin(rad1));

        bool isLargeArc = (endAngleDeg - startAngleDeg) > 180.0;

        var figure = new PathFigure
        {
            StartPoint = p1,
            IsClosed = true,
            IsFilled = true
        };
        figure.Segments.Add(new ArcSegment(p2, new Size(rOuter, rOuter), 0, isLargeArc, SweepDirection.Clockwise, true));
        figure.Segments.Add(new LineSegment(p3, true));
        figure.Segments.Add(new ArcSegment(p4, new Size(rInner, rInner), 0, isLargeArc, SweepDirection.Counterclockwise, true));

        var geometry = new PathGeometry();
        geometry.Figures.Add(figure);

        return new System.Windows.Shapes.Path
        {
            Data = geometry,
            Fill = fill,
            Stroke = stroke,
            StrokeThickness = strokeThickness
        };
    }

    private void RenderDailyClockSection()
    {
        if (DailyClockCanvas == null || DailyClockItemsListPanel == null) return;

        DailyClockCanvas.Children.Clear();
        DailyClockItemsListPanel.Children.Clear();

        if (DailyClockDatePicker != null)
        {
            DailyClockDatePicker.SelectedDate = _dailyClockSelectedDate;
        }

        var blocks = GetDayScheduleAndCourses(_dailyClockSelectedDate);

        double totalHours = blocks.Sum(b => (b.EndTime - b.StartTime).TotalHours);
        int totalItems = blocks.Count;
        int completedItems = blocks.Count(b => b.Status.Equals("COMPLETED", StringComparison.OrdinalIgnoreCase));

        DailyClockSummaryTag.Text = $"当日排期 {totalItems} 项 · 累计 {totalHours:0.0}h";
        DailyClockCenterTitleText.Text = _dailyClockSelectedDate.Date == DateTime.Today ? "今日作息" : _dailyClockSelectedDate.ToString("M/d 作息");
        DailyClockCenterHoursText.Text = $"{totalHours:0.0} h";
        DailyClockCenterTasksText.Text = $"{totalItems} 项日程";
        DailyClockCenterHoverText.Text = "";

        Point center = new Point(210, 210);

        // 1. 24小时外圆表盘背景 (大盘 400x400)
        var dialBg = new System.Windows.Shapes.Ellipse
        {
            Width = 400,
            Height = 400,
            Fill = ThemeService.IsCurrentDark ? new SolidColorBrush(Color.FromRgb(0x1A, 0x1B, 0x1E)) : new SolidColorBrush(Color.FromRgb(0xF8, 0xF9, 0xFA)),
            Stroke = ThemeService.CurrentBorderBrush,
            StrokeThickness = 1.5
        };
        Canvas.SetLeft(dialBg, 10);
        Canvas.SetTop(dialBg, 10);
        DailyClockCanvas.Children.Add(dialBg);

        // 2. 24 小时刻度线与数字
        for (int h = 0; h < 24; h++)
        {
            double deg = -90.0 + h * 15.0;
            double rad = deg * Math.PI / 180.0;
            bool isCardinal = (h == 0 || h == 6 || h == 12 || h == 18);
            bool isEven = (h % 2 == 0);

            double rStart = isCardinal ? 176.0 : (isEven ? 180.0 : 186.0);
            double rEnd = 196.0;

            var tick = new System.Windows.Shapes.Line
            {
                X1 = 210.0 + rStart * Math.Cos(rad),
                Y1 = 210.0 + rStart * Math.Sin(rad),
                X2 = 210.0 + rEnd * Math.Cos(rad),
                Y2 = 210.0 + rEnd * Math.Sin(rad),
                Stroke = isCardinal
                    ? ThemeService.CurrentAccentBrush
                    : (isEven ? ThemeService.CurrentSecondaryTextBrush : ThemeService.CurrentBorderBrush),
                StrokeThickness = isCardinal ? 2.5 : (isEven ? 1.5 : 1.0)
            };
            DailyClockCanvas.Children.Add(tick);

            if (isEven)
            {
                double rNum = 163.0;
                double tx = 210.0 + rNum * Math.Cos(rad) - 9.0;
                double ty = 210.0 + rNum * Math.Sin(rad) - 8.0;

                var numText = new TextBlock
                {
                    Text = $"{h:D2}",
                    FontSize = 10.5,
                    FontWeight = isCardinal ? FontWeights.Bold : FontWeights.Normal,
                    Foreground = isCardinal
                        ? ThemeService.CurrentAccentBrush
                        : ThemeService.CurrentSecondaryTextBrush
                };
                Canvas.SetLeft(numText, tx);
                Canvas.SetTop(numText, ty);
                DailyClockCanvas.Children.Add(numText);
            }
        }

        // 3. 基础环形轨道底槽 (内径 82，外径 148)
        var trackBg = CreateArcPath(center, 148, 82, -90, 270, 
            ThemeService.IsCurrentDark ? new SolidColorBrush(Color.FromArgb(0x35, 0x3C, 0x40, 0x43)) : new SolidColorBrush(Color.FromArgb(0x18, 0x00, 0x00, 0x00)), 
            ThemeService.CurrentBorderBrush, 1.0);
        DailyClockCanvas.Children.Add(trackBg);

        // 4. 绘制当日各事项的扇形弧段
        foreach (var b in blocks)
        {
            int sMin = b.StartTime.Hour * 60 + b.StartTime.Minute;
            int eMin = b.EndTime.Hour * 60 + b.EndTime.Minute;
            if (eMin <= sMin) eMin = Math.Min(1440, sMin + 30);
            if (eMin - sMin < 10) eMin = Math.Min(1440, sMin + 12);

            double aStart = -90.0 + sMin * 0.25;
            double aEnd = -90.0 + eMin * 0.25;

            Color col = Color.FromRgb(0x3B, 0x82, 0xF6);
            try
            {
                var converted = System.Windows.Media.ColorConverter.ConvertFromString(b.HexColor);
                if (converted is Color c) col = c;
            }
            catch { }

            var arcBrush = new SolidColorBrush(Color.FromArgb(0xE0, col.R, col.G, col.B));
            var borderBrush = ThemeService.CurrentCardBrush;

            var arcPath = CreateArcPath(center, 148, 82, aStart, aEnd, arcBrush, borderBrush, 1.5);
            arcPath.Cursor = Cursors.Hand;
            arcPath.ToolTip = $"{(b.IsCourse ? "🎓 课程: " : "📋 ")}{b.Title}\n" +
                              $"时间: {b.StartTime:HH:mm} - {b.EndTime:HH:mm} ({(int)(b.EndTime - b.StartTime).TotalMinutes}分钟)\n" +
                              $"类型: {(b.WorkType == "DEEP_WORK" ? "🎯 深度工作" : "⚡ 浅层事务")}";

            arcPath.MouseEnter += (s, e) =>
            {
                arcPath.Fill = new SolidColorBrush(Color.FromArgb(0xFF, col.R, col.G, col.B));
                DailyClockCenterTitleText.Text = $"{b.StartTime:HH:mm}-{b.EndTime:HH:mm}";
                DailyClockCenterHoursText.Text = $"{(b.EndTime - b.StartTime).TotalHours:0.0}h";
                DailyClockCenterTasksText.Text = b.Title;
                DailyClockCenterHoverText.Text = b.IsCourse ? "🎓 课程" : (b.WorkType == "DEEP_WORK" ? "🎯 深度专注" : "⚡ 事务日程");
            };
            arcPath.MouseLeave += (s, e) =>
            {
                arcPath.Fill = arcBrush;
                DailyClockCenterTitleText.Text = _dailyClockSelectedDate.Date == DateTime.Today ? "今日作息" : _dailyClockSelectedDate.ToString("M/d 作息");
                DailyClockCenterHoursText.Text = $"{totalHours:0.0} h";
                DailyClockCenterTasksText.Text = $"{totalItems} 项日程";
                DailyClockCenterHoverText.Text = "";
            };

            DailyClockCanvas.Children.Add(arcPath);
        }

        // 5. 若为今天，绘制实时当前时间红色指针
        if (_dailyClockSelectedDate.Date == DateTime.Today)
        {
            double nowMin = DateTime.Now.Hour * 60 + DateTime.Now.Minute + DateTime.Now.Second / 60.0;
            double aNow = -90.0 + nowMin * 0.25;
            double radNow = aNow * Math.PI / 180.0;

            var needleLine = new System.Windows.Shapes.Line
            {
                X1 = 210.0 + 78.0 * Math.Cos(radNow),
                Y1 = 210.0 + 78.0 * Math.Sin(radNow),
                X2 = 210.0 + 195.0 * Math.Cos(radNow),
                Y2 = 210.0 + 195.0 * Math.Sin(radNow),
                Stroke = new SolidColorBrush(Color.FromRgb(0xEF, 0x44, 0x44)),
                StrokeThickness = 2.5
            };
            DailyClockCanvas.Children.Add(needleLine);

            var tipDot = new System.Windows.Shapes.Ellipse
            {
                Width = 7,
                Height = 7,
                Fill = new SolidColorBrush(Color.FromRgb(0xEF, 0x44, 0x44)),
                Stroke = Brushes.White,
                StrokeThickness = 1.2,
                ToolTip = $"当前指针: {DateTime.Now:HH:mm:ss}"
            };
            Canvas.SetLeft(tipDot, 210.0 + 195.0 * Math.Cos(radNow) - 3.5);
            Canvas.SetTop(tipDot, 210.0 + 195.0 * Math.Sin(radNow) - 3.5);
            DailyClockCanvas.Children.Add(tipDot);
        }

        // 6. 渲染右侧时序事项卡片
        if (blocks.Count == 0)
        {
            var emptySp = new StackPanel
            {
                Margin = new Thickness(0, 36, 0, 36),
                HorizontalAlignment = HorizontalAlignment.Center
            };
            emptySp.Children.Add(new TextBlock
            {
                Text = "🍃",
                FontSize = 32,
                HorizontalAlignment = HorizontalAlignment.Center
            });
            emptySp.Children.Add(new TextBlock
            {
                Text = "当日全天暂无排期日程",
                FontSize = 13,
                FontWeight = FontWeights.SemiBold,
                Foreground = ThemeService.CurrentTextBrush,
                Margin = new Thickness(0, 6, 0, 4),
                HorizontalAlignment = HorizontalAlignment.Center
            });
            emptySp.Children.Add(new TextBlock
            {
                Text = "可通过右上角「➕ 新建日程」为今日规划安排",
                FontSize = 11,
                Foreground = ThemeService.CurrentSecondaryTextBrush,
                HorizontalAlignment = HorizontalAlignment.Center
            });
            DailyClockItemsListPanel.Children.Add(emptySp);
        }
        else
        {
            foreach (var b in blocks)
            {
                Color itemColor = Color.FromRgb(0x3B, 0x82, 0xF6);
                try
                {
                    var converted = System.Windows.Media.ColorConverter.ConvertFromString(b.HexColor);
                    if (converted is Color c) itemColor = c;
                }
                catch { }

                var itemCard = new Border
                {
                    Background = ThemeService.CurrentCardBrush,
                    BorderBrush = ThemeService.CurrentBorderBrush,
                    BorderThickness = new Thickness(1),
                    CornerRadius = new CornerRadius(6),
                    Padding = new Thickness(10, 8, 10, 8),
                    Margin = new Thickness(0, 0, 0, 6)
                };

                var cardGrid = new Grid();
                cardGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto }); // 0: Color Strip
                cardGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) }); // 1: Content
                cardGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto }); // 2: Time & Badges

                var strip = new Border
                {
                    Width = 4,
                    CornerRadius = new CornerRadius(2),
                    Background = new SolidColorBrush(itemColor),
                    Margin = new Thickness(0, 0, 10, 0)
                };
                cardGrid.Children.Add(strip);

                var contentSp = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
                Grid.SetColumn(contentSp, 1);

                contentSp.Children.Add(new TextBlock
                {
                    Text = b.Title,
                    FontSize = 12,
                    FontWeight = FontWeights.SemiBold,
                    Foreground = ThemeService.CurrentTextBrush,
                    TextTrimming = TextTrimming.CharacterEllipsis
                });

                var tagsWrap = new WrapPanel { Margin = new Thickness(0, 3, 0, 0) };
                string catIcon = b.Category switch
                {
                    "WORK" => "💼 工作",
                    "STUDY" => "📚 学习",
                    "HEALTH" => "🏃 健康",
                    "LIFE" => "☕ 生活",
                    "COURSE" => "🎓 课程",
                    _ => "📌 其它"
                };
                tagsWrap.Children.Add(new TextBlock
                {
                    Text = catIcon,
                    FontSize = 10,
                    Foreground = ThemeService.CurrentSecondaryTextBrush,
                    Margin = new Thickness(0, 0, 8, 0)
                });

                if (b.WorkType == "DEEP_WORK")
                {
                    tagsWrap.Children.Add(new TextBlock
                    {
                        Text = "🎯 深度专注",
                        FontSize = 10,
                        Foreground = new SolidColorBrush(Color.FromRgb(0xA7, 0x8B, 0xFA))
                    });
                }
                contentSp.Children.Add(tagsWrap);
                cardGrid.Children.Add(contentSp);

                var timeSp = new StackPanel { VerticalAlignment = VerticalAlignment.Center, HorizontalAlignment = HorizontalAlignment.Right };
                Grid.SetColumn(timeSp, 2);

                timeSp.Children.Add(new TextBlock
                {
                    Text = $"{b.StartTime:HH:mm} - {b.EndTime:HH:mm}",
                    FontSize = 11,
                    FontWeight = FontWeights.Bold,
                    Foreground = ThemeService.CurrentTextBrush,
                    HorizontalAlignment = HorizontalAlignment.Right
                });

                int durM = (int)(b.EndTime - b.StartTime).TotalMinutes;
                timeSp.Children.Add(new TextBlock
                {
                    Text = $"{durM} 分钟",
                    FontSize = 10,
                    Foreground = ThemeService.CurrentMutedTextBrush,
                    HorizontalAlignment = HorizontalAlignment.Right,
                    Margin = new Thickness(0, 2, 0, 0)
                });

                cardGrid.Children.Add(timeSp);
                itemCard.Child = cardGrid;

                if (b.Id.StartsWith("fitness_") || b.Category == "HEALTH" || b.Title.Contains("健身"))
                {
                    itemCard.Cursor = Cursors.Hand;
                    itemCard.ToolTip = "🏋️ 点击直达健身模块查看与记录训练";
                    itemCard.MouseLeftButtonUp += (s, e) =>
                    {
                        SwitchPrimaryPage("Fitness");
                    };
                }

                DailyClockItemsListPanel.Children.Add(itemCard);
            }
        }
    }

    private void RenderCategoryDonutChart(List<ScheduleItem> items)
    {
        if (CategoryDonutCanvas == null || SummaryCategoryLegendPanel == null) return;
        CategoryDonutCanvas.Children.Clear();
        SummaryCategoryLegendPanel.Children.Clear();

        double workHours = 0, studyHours = 0, healthHours = 0, lifeHours = 0, otherHours = 0;

        foreach (var item in items)
        {
            double durationH = (item.ActualMinutes > 0 ? item.ActualMinutes :
                (item.EndTime > item.StartTime ? (item.EndTime - item.StartTime).TotalMinutes : item.EstimatedMinutes)) / 60.0;

            switch (item.Category)
            {
                case "WORK": workHours += durationH; break;
                case "STUDY": studyHours += durationH; break;
                case "HEALTH": healthHours += durationH; break;
                case "LIFE": lifeHours += durationH; break;
                default: otherHours += durationH; break;
            }
        }

        double totalCat = workHours + studyHours + healthHours + lifeHours + otherHours;
        CategoryDonutTotalHoursText.Text = $"{totalCat:0.0}h";

        var catDefs = new (string Name, double Hours, Color Col, string Icon)[]
        {
            ("工作", workHours, Color.FromRgb(0x3B, 0x82, 0xF6), "💼"),
            ("学习", studyHours, Color.FromRgb(0x8B, 0x5C, 0xF6), "📚"),
            ("健康", healthHours, Color.FromRgb(0x10, 0xB9, 0x81), "🏃"),
            ("生活", lifeHours, Color.FromRgb(0xF5, 0x9E, 0x0B), "☕"),
            ("其它", otherHours, Color.FromRgb(0x6B, 0x72, 0x80), "📌")
        };

        Point center = new Point(55, 55);

        if (totalCat > 0)
        {
            double curAngle = -90.0;
            foreach (var cat in catDefs)
            {
                if (cat.Hours <= 0) continue;
                double sweep = (cat.Hours / totalCat) * 360.0;
                if (cat.Hours == totalCat) sweep = 359.999;

                var arc = CreateArcPath(center, 50, 32, curAngle, curAngle + sweep, new SolidColorBrush(cat.Col), ThemeService.CurrentCardBrush, 1.2);
                arc.ToolTip = $"{cat.Icon} {cat.Name}: {cat.Hours:0.0}h ({(cat.Hours * 100.0 / totalCat):0.0}%)";
                CategoryDonutCanvas.Children.Add(arc);

                curAngle += sweep;
            }

            if (CategoryDonutInsightText != null)
            {
                if ((workHours + studyHours) / totalCat >= 0.6)
                {
                    CategoryDonutInsightText.Text = "💡 工作与学习占比高，精力集中于主航道！";
                }
                else if ((healthHours + lifeHours) / totalCat >= 0.5)
                {
                    CategoryDonutInsightText.Text = "☕ 生活与身心投入充足，劳逸结合状态佳！";
                }
                else
                {
                    CategoryDonutInsightText.Text = "💡 各分类投入均衡，作息与成长节奏平稳！";
                }
            }
        }
        else
        {
            var emptyRing = CreateArcPath(center, 50, 32, -90, 269.999, new SolidColorBrush(Color.FromRgb(0x3C, 0x40, 0x43)), Brushes.Transparent, 0);
            CategoryDonutCanvas.Children.Add(emptyRing);

            if (CategoryDonutInsightText != null)
            {
                CategoryDonutInsightText.Text = "🌱 当前周期暂无排期分类数据";
            }
        }

        // 渲染图例
        foreach (var cat in catDefs)
        {
            if (totalCat > 0 && cat.Hours <= 0) continue;
            double pct = totalCat > 0 ? (cat.Hours * 100.0 / totalCat) : 0;
            var itemSp = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 1, 0, 2) };
            itemSp.Children.Add(new Border
            {
                Width = 8,
                Height = 8,
                CornerRadius = new CornerRadius(4),
                Background = new SolidColorBrush(cat.Col),
                Margin = new Thickness(0, 0, 6, 0),
                VerticalAlignment = VerticalAlignment.Center
            });
            itemSp.Children.Add(new TextBlock
            {
                Text = $"{cat.Icon} {cat.Name} {cat.Hours:0.0}h ({pct:0}%)",
                FontSize = 10,
                Foreground = ThemeService.CurrentSecondaryTextBrush,
                VerticalAlignment = VerticalAlignment.Center
            });
            SummaryCategoryLegendPanel.Children.Add(itemSp);
        }
    }

    private void RenderHourlyActivityBars(List<ScheduleItem> items)
    {
        if (HourlyActivityBarsGrid == null) return;
        HourlyActivityBarsGrid.Children.Clear();

        double[] hourlyMinutes = new double[24];
        double morningM = 0, afternoonM = 0, eveningM = 0;

        foreach (var item in items)
        {
            double durationM = item.ActualMinutes > 0 ? item.ActualMinutes :
                (item.EndTime > item.StartTime ? (item.EndTime - item.StartTime).TotalMinutes : item.EstimatedMinutes);

            if (durationM <= 0) continue;

            DateTime st = item.StartTime;
            DateTime et = item.EndTime > item.StartTime ? item.EndTime : item.StartTime.AddMinutes(durationM);

            for (int h = 0; h < 24; h++)
            {
                DateTime slotStart = st.Date.AddHours(h);
                DateTime slotEnd = slotStart.AddHours(1);

                DateTime overlapStart = st > slotStart ? st : slotStart;
                DateTime overlapEnd = et < slotEnd ? et : slotEnd;

                if (overlapEnd > overlapStart)
                {
                    double overlapMinutes = (overlapEnd - overlapStart).TotalMinutes;
                    hourlyMinutes[h] += overlapMinutes;

                    if (h >= 8 && h < 12) morningM += overlapMinutes;
                    else if (h >= 13 && h < 18) afternoonM += overlapMinutes;
                    else if (h >= 19 && h < 24) eveningM += overlapMinutes;
                }
            }
        }

        double maxM = hourlyMinutes.Max();
        double scaleMax = Math.Max(30.0, maxM);

        for (int h = 0; h < 24; h++)
        {
            double m = hourlyMinutes[h];
            double barHeight = m > 0 ? Math.Max(4.0, (m / scaleMax) * 75.0) : 3.0;

            var colStack = new StackPanel
            {
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Bottom,
                Margin = new Thickness(1, 0, 1, 0)
            };

            var barBorder = new Border
            {
                Width = 7,
                Height = barHeight,
                CornerRadius = new CornerRadius(2, 2, 0, 0),
                Background = m == 0
                    ? ThemeService.CurrentControlBrush
                    : (m == maxM && maxM > 0
                        ? new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81)) // 峰值翡翠绿
                        : (m >= 0.6 * maxM
                            ? new SolidColorBrush(Color.FromRgb(0x8B, 0x5C, 0xF6)) // 高能薰衣草紫
                            : new SolidColorBrush(Color.FromRgb(0x3B, 0x82, 0xF6)))), // 经典蓝
                ToolTip = $"{h:D2}:00 - {h + 1:D2}:00: {m / 60.0:0.0}h 专注排期"
            };

            colStack.Children.Add(barBorder);
            HourlyActivityBarsGrid.Children.Add(colStack);
        }

        // 黄金精力时段指示
        if (SummaryMorningHoursText != null) SummaryMorningHoursText.Text = $"早间 {morningM / 60.0:0.0}h";
        if (SummaryAfternoonHoursText != null) SummaryAfternoonHoursText.Text = $"午间 {afternoonM / 60.0:0.0}h";
        if (SummaryEveningHoursText != null) SummaryEveningHoursText.Text = $"晚间 {eveningM / 60.0:0.0}h";

        if (SummaryPeakHourInsightText != null)
        {
            if (morningM >= afternoonM && morningM >= eveningM && morningM > 0)
            {
                SummaryPeakHourInsightText.Text = "⚡ 晨间 (8-12点) 专注投入最高，黄金心流效率极佳！";
            }
            else if (afternoonM >= morningM && afternoonM >= eveningM && afternoonM > 0)
            {
                SummaryPeakHourInsightText.Text = "☀️ 下午 (13-18点) 为主要攻坚期，执行推进有力！";
            }
            else if (eveningM > 0)
            {
                SummaryPeakHourInsightText.Text = "🌙 晚间 (19-23点) 专注力充沛，适合复盘与自学！";
            }
            else
            {
                SummaryPeakHourInsightText.Text = "🌱 暂无足够排期样本，规划更多日程后自动生成曲线！";
            }
        }
    }

    private Border CreateScheduleSummaryCard(ScheduleItem item)
    {
        bool isDone = item.Status.Equals("COMPLETED", StringComparison.OrdinalIgnoreCase);
        bool isInProg = item.Status.Equals("IN_PROGRESS", StringComparison.OrdinalIgnoreCase);

        var card = new Border
        {
            Background = ThemeService.CurrentCardBrush,
            BorderBrush = new SolidColorBrush(isDone ? Color.FromRgb(0x05, 0x96, 0x69) : (isInProg ? Color.FromRgb(0x25, 0x63, 0xEB) : (ThemeService.IsCurrentDark ? Color.FromRgb(0x3C, 0x40, 0x43) : Color.FromRgb(0xDA, 0xDC, 0xE0)))),
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(8),
            Margin = new Thickness(0, 0, 0, 8),
            Padding = new Thickness(14, 12, 14, 12)
        };

        var mainGrid = new Grid();
        mainGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto }); // 0: Circular Checkbox
        mainGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) }); // 1: Content
        mainGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto }); // 2: Actions

        // 0. 滴答清单经典圆形 Checkbox
        var checkBorder = new Border
        {
            Width = 22,
            Height = 22,
            CornerRadius = new CornerRadius(11),
            Cursor = Cursors.Hand,
            VerticalAlignment = VerticalAlignment.Top,
            Margin = new Thickness(0, 2, 12, 0),
            BorderThickness = new Thickness(1.5),
            Background = isDone ? new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81)) : Brushes.Transparent,
            BorderBrush = isDone ? new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81)) : new SolidColorBrush(Color.FromRgb(0x6B, 0x72, 0x80)),
            ToolTip = isDone ? "点击撤销完成 (重置为待办)" : "点击标记交付完成"
        };
        if (isDone)
        {
            checkBorder.Child = new TextBlock
            {
                Text = "✓",
                FontSize = 12,
                FontWeight = FontWeights.Bold,
                Foreground = Brushes.White,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
        }

        checkBorder.MouseEnter += (s, e) =>
        {
            if (!isDone) checkBorder.BorderBrush = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
        };
        checkBorder.MouseLeave += (s, e) =>
        {
            if (!isDone) checkBorder.BorderBrush = new SolidColorBrush(Color.FromRgb(0x6B, 0x72, 0x80));
        };
        checkBorder.MouseLeftButtonUp += (s, e) =>
        {
            e.Handled = true;
            if (isDone)
            {
                item.Status = "PENDING";
            }
            else
            {
                item.Status = "COMPLETED";
                if (item.ActualMinutes == 0)
                {
                    item.ActualMinutes = item.EstimatedMinutes > 0 ? item.EstimatedMinutes : (int)(item.EndTime - item.StartTime).TotalMinutes;
                }
            }
            DatabaseService.UpsertSchedule(item);
            if (!isDone)
            {
                DataLineageService.OnScheduleCompleted(item);
            }
            RenderAllCalendarViews();
            RefreshTimePnlData();
            UpdateCognitiveLoadQuota();
            RenderSummaryPage();
        };

        Grid.SetColumn(checkBorder, 0);
        mainGrid.Children.Add(checkBorder);

        // 1. 中间主体内容
        var contentSp = new StackPanel();

        // 顶部标签行：优先级、分类、认知类型、状态
        var tagsRow = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 6) };

        // 优先级徽章
        var prioBadge = new Border
        {
            CornerRadius = new CornerRadius(4),
            Padding = new Thickness(6, 1, 6, 1),
            Margin = new Thickness(0, 0, 6, 0)
        };
        string prioStr = item.Priority switch
        {
            "URGENT" => "🔴 紧急 (P1)",
            "HIGH" => "🟡 高优先 (P2)",
            "LOW" => "⚪ 低优先 (P4)",
            _ => "🔵 中优先 (P3)"
        };
        Color prioColor = item.Priority switch
        {
            "URGENT" => Color.FromRgb(0xEF, 0x44, 0x44),
            "HIGH" => Color.FromRgb(0xF5, 0x9E, 0x0B),
            "LOW" => Color.FromRgb(0x9C, 0xA3, 0xAF),
            _ => Color.FromRgb(0x60, 0xA5, 0xFA)
        };
        prioBadge.Background = new SolidColorBrush(Color.FromArgb(30, prioColor.R, prioColor.G, prioColor.B));
        prioBadge.Child = new TextBlock { Text = prioStr, FontSize = 10, FontWeight = FontWeights.SemiBold, Foreground = new SolidColorBrush(prioColor) };
        tagsRow.Children.Add(prioBadge);

        // 分类徽章
        var catBadge = new Border
        {
            Background = ThemeService.CurrentControlBrush,
            CornerRadius = new CornerRadius(4),
            Padding = new Thickness(6, 1, 6, 1),
            Margin = new Thickness(0, 0, 6, 0)
        };
        string catIcon = item.Category switch { "WORK" => "💼 工作", "STUDY" => "📚 学习", "LIFE" => "☕ 生活", "HEALTH" => "🏃 健康", _ => item.Category };
        catBadge.Child = new TextBlock { Text = catIcon, FontSize = 10, Foreground = ThemeService.CurrentAccentBrush };
        tagsRow.Children.Add(catBadge);

        // 认知负荷徽章
        var workTypeBadge = new Border
        {
            Background = ThemeService.CurrentControlBrush,
            CornerRadius = new CornerRadius(4),
            Padding = new Thickness(6, 1, 6, 1),
            Margin = new Thickness(0, 0, 6, 0)
        };
        string wtName = item.WorkType switch { "DEEP_WORK" => "🎯 深度工作", "SHALLOW_WORK" => "⚡ 浅层事务", _ => "☕ 缓冲休息" };
        Color wtColor = item.WorkType switch { "DEEP_WORK" => Color.FromRgb(0xA7, 0x8B, 0xFA), "SHALLOW_WORK" => Color.FromRgb(0xFB, 0xBF, 0x24), _ => Color.FromRgb(0x34, 0xD3, 0x99) };
        workTypeBadge.Child = new TextBlock { Text = wtName, FontSize = 10, Foreground = new SolidColorBrush(wtColor) };
        tagsRow.Children.Add(workTypeBadge);

        contentSp.Children.Add(tagsRow);

        // 任务标题 (已完成时带删除线)
        var titleTb = new TextBlock
        {
            Text = item.Title,
            FontSize = 14,
            FontWeight = FontWeights.SemiBold,
            Foreground = isDone ? ThemeService.CurrentMutedTextBrush : ThemeService.CurrentTextBrush,
            Margin = new Thickness(0, 0, 0, 4)
        };
        if (isDone)
        {
            titleTb.TextDecorations = TextDecorations.Strikethrough;
        }
        contentSp.Children.Add(titleTb);

        // 时间与工时偏差比对
        int plannedMins = item.EstimatedMinutes > 0 ? item.EstimatedMinutes : (int)(item.EndTime - item.StartTime).TotalMinutes;
        int actualMins = item.ActualMinutes > 0 ? item.ActualMinutes : plannedMins;
        int diffMins = actualMins - plannedMins;
        string deviationStr = diffMins switch
        {
            > 0 => $" (+{diffMins}m 超出)",
            < 0 => $" ({diffMins}m 高效完成)",
            _ => " (精准达成)"
        };

        string timeInfo = $"📅 {item.StartTime:yyyy/MM/dd HH:mm} - {item.EndTime:HH:mm} · 预估: {plannedMins}m · 实际: {actualMins}m{deviationStr}";
        contentSp.Children.Add(new TextBlock
        {
            Text = timeInfo,
            FontSize = 11,
            Foreground = ThemeService.CurrentSecondaryTextBrush,
            Margin = new Thickness(0, 0, 0, 4)
        });

        // DoD 验收标准卡片
        if (!string.IsNullOrWhiteSpace(item.Dod))
        {
            var dodBorder = new Border
            {
                Background = ThemeService.IsCurrentDark ? new SolidColorBrush(Color.FromRgb(0x1E, 0x1B, 0x4B)) : new SolidColorBrush(Color.FromRgb(0xF5, 0xF3, 0xFF)),
                BorderBrush = ThemeService.IsCurrentDark ? new SolidColorBrush(Color.FromRgb(0x6D, 0x28, 0xD9)) : new SolidColorBrush(Color.FromRgb(0xC4, 0xB5, 0xFD)),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(4),
                Padding = new Thickness(8, 4, 8, 4),
                Margin = new Thickness(0, 2, 0, 2)
            };
            string checkMark = isDone ? " [已交付验收 ✓]" : " [待验收]";
            dodBorder.Child = new TextBlock
            {
                Text = $"📋 验收交付标准 (DoD): {item.Dod}{checkMark}",
                FontSize = 11,
                Foreground = ThemeService.IsCurrentDark ? new SolidColorBrush(Color.FromRgb(0xC0, 0x84, 0xFC)) : new SolidColorBrush(Color.FromRgb(0x7C, 0x3A, 0xED)),
                TextWrapping = TextWrapping.Wrap
            };
            contentSp.Children.Add(dodBorder);
        }

        Grid.SetColumn(contentSp, 1);
        mainGrid.Children.Add(contentSp);

        // 2. 右侧操作按钮组 (详情与删除)
        var actionsSp = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(12, 0, 0, 0)
        };

        var editBtn = new Button
        {
            Content = "✏️ 详情",
            Style = (Style)FindResource("GoogleTopBtnStyle"),
            FontSize = 11,
            Padding = new Thickness(8, 4, 8, 4),
            Margin = new Thickness(0, 0, 4, 0),
            ToolTip = "查看与编辑日程详情"
        };
        editBtn.Click += (s, e) => OpenEventEditModal(item);
        actionsSp.Children.Add(editBtn);

        var delBtn = new Button
        {
            Content = "🗑️",
            Style = (Style)FindResource("GoogleTopBtnStyle"),
            FontSize = 11,
            Padding = new Thickness(6, 4, 6, 4),
            ToolTip = "删除日程"
        };
        delBtn.Click += (s, e) =>
        {
            DatabaseService.DeleteSchedule(item.Id);
            RenderAllCalendarViews();
            RenderSummaryPage();
        };
        actionsSp.Children.Add(delBtn);

        Grid.SetColumn(actionsSp, 2);
        mainGrid.Children.Add(actionsSp);

        card.Child = mainGrid;
        return card;
    }

    // =========================================================================
    // ================= STUDY & KNOWLEDGE MANAGEMENT LOGIC ====================
    // =========================================================================

    private void OnRefreshStudyClicked(object sender, RoutedEventArgs e)
    {
        RenderStudyPage();
    }

    private void OnStudyFilterChanged(object sender, SelectionChangedEventArgs e)
    {
        if (StudyPageContainer != null && StudyPageContainer.Visibility == Visibility.Visible)
        {
            RenderStudyPage();
        }
    }

    private void RenderStudyPage()
    {
        if (StudyTopicsPanel == null) return;
        StudyTopicsPanel.Children.Clear();

        var allTopics = DatabaseService.GetAllStudyTopics();

        // 过滤
        string catFilter = (StudyCategoryFilterCombo?.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "ALL";
        string statusFilter = (StudyStatusFilterCombo?.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "ALL";

        IEnumerable<StudyTopicItem> filtered = allTopics;
        if (catFilter != "ALL")
        {
            filtered = filtered.Where(t => t.Category.Equals(catFilter, StringComparison.OrdinalIgnoreCase));
        }
        if (statusFilter != "ALL")
        {
            filtered = filtered.Where(t => t.Status.Equals(statusFilter, StringComparison.OrdinalIgnoreCase));
        }

        var list = filtered.OrderByDescending(t => t.UpdatedAt).ToList();

        // 统计指标
        int totalCount = allTopics.Count;
        int activeCount = allTopics.Count(t => t.Status.Equals("IN_PROGRESS", StringComparison.OrdinalIgnoreCase));
        int completedCount = allTopics.Count(t => t.Status.Equals("COMPLETED", StringComparison.OrdinalIgnoreCase));

        double totalCompletedHours = allTopics.Sum(t => t.CompletedHours);
        double totalTargetHours = allTopics.Sum(t => t.TargetHours);
        double avgProgress = allTopics.Count > 0 ? allTopics.Average(t => t.ProgressPercent) : 0.0;

        StudyActiveTopicsText.Text = $"{activeCount} 个";
        StudyTotalTopicsText.Text = $"全量档案: {totalCount} 个主题";

        StudyTotalHoursText.Text = $"{totalCompletedHours:0.0} 小时";
        StudyTargetHoursText.Text = $"总目标学时: {totalTargetHours:0.0} h";

        StudyAvgProgressText.Text = $"{avgProgress:0.0}%";
        StudyAvgProgressBar.Value = avgProgress;

        StudyCompletedTopicsText.Text = $"{completedCount} 个";

        if (list.Count == 0)
        {
            var emptyPanel = new StackPanel
            {
                Margin = new Thickness(0, 40, 0, 40),
                HorizontalAlignment = HorizontalAlignment.Center
            };
            emptyPanel.Children.Add(new TextBlock
            {
                Text = "📚",
                FontSize = 32,
                HorizontalAlignment = HorizontalAlignment.Center
            });
            emptyPanel.Children.Add(new TextBlock
            {
                Text = "暂无匹配的学习主题，点击右上角「+ 新建学习主题」开启打卡",
                FontSize = 13,
                Foreground = ThemeService.CurrentSecondaryTextBrush,
                Margin = new Thickness(0, 8, 0, 0),
                HorizontalAlignment = HorizontalAlignment.Center
            });
            StudyTopicsPanel.Children.Add(emptyPanel);
            return;
        }

        foreach (var item in list)
        {
            var card = CreateStudyTopicCard(item);
            StudyTopicsPanel.Children.Add(card);
        }
    }

    private Border CreateStudyTopicCard(StudyTopicItem item)
    {
        bool isDone = item.Status.Equals("COMPLETED", StringComparison.OrdinalIgnoreCase);

        var card = new Border
        {
            Background = ThemeService.CurrentCardBrush,
            BorderBrush = isDone ? new SolidColorBrush(Color.FromRgb(0x05, 0x96, 0x69)) : ThemeService.CurrentBorderBrush,
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(10),
            Margin = new Thickness(0, 0, 0, 12),
            Padding = new Thickness(16, 14, 16, 14)
        };

        var sp = new StackPanel();

        // 顶部行：分类徽章、状态徽章、操作按钮
        var topGrid = new Grid { Margin = new Thickness(0, 0, 0, 8) };
        topGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        topGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        var badgesSp = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };

        var catBadge = new Border
        {
            Background = ThemeService.CurrentControlBrush,
            CornerRadius = new CornerRadius(4),
            Padding = new Thickness(8, 2, 8, 2),
            Margin = new Thickness(0, 0, 8, 0)
        };
        catBadge.Child = new TextBlock
        {
            Text = $"🏷️ {item.Category}",
            FontSize = 11,
            FontWeight = FontWeights.Medium,
            Foreground = ThemeService.CurrentAccentBrush
        };
        badgesSp.Children.Add(catBadge);

        var statusBadge = new Border
        {
            CornerRadius = new CornerRadius(4),
            Padding = new Thickness(8, 2, 8, 2)
        };
        if (isDone)
        {
            statusBadge.Background = new SolidColorBrush(Color.FromRgb(0x06, 0x4E, 0x3B));
            statusBadge.Child = new TextBlock { Text = "🏆 已学成/结业", FontSize = 11, Foreground = new SolidColorBrush(Color.FromRgb(0x34, 0xD3, 0x99)) };
        }
        else if (item.Status == "NOT_STARTED")
        {
            statusBadge.Background = ThemeService.CurrentControlBrush;
            statusBadge.Child = new TextBlock { Text = "🕒 筹划中", FontSize = 11, Foreground = ThemeService.CurrentMutedTextBrush };
        }
        else
        {
            statusBadge.Background = new SolidColorBrush(Color.FromRgb(0x1E, 0x3A, 0x8A));
            statusBadge.Child = new TextBlock { Text = "⏳ 正在攻关", FontSize = 11, Foreground = new SolidColorBrush(Color.FromRgb(0x60, 0xA5, 0xFA)) };
        }
        badgesSp.Children.Add(statusBadge);
        Grid.SetColumn(badgesSp, 0);
        topGrid.Children.Add(badgesSp);

        var opSp = new StackPanel { Orientation = Orientation.Horizontal };
        var editBtn = new Button
        {
            Content = "✏️ 编辑",
            Style = (Style)FindResource("GoogleTopBtnStyle"),
            FontSize = 11,
            Padding = new Thickness(8, 3, 8, 3),
            Margin = new Thickness(0, 0, 4, 0)
        };
        editBtn.Click += (s, e) => OnEditStudyTopicClicked(item);
        opSp.Children.Add(editBtn);

        var delBtn = new Button
        {
            Content = "🗑️",
            Style = (Style)FindResource("GoogleTopBtnStyle"),
            FontSize = 11,
            Padding = new Thickness(6, 3, 6, 3)
        };
        delBtn.Click += (s, e) =>
        {
            DatabaseService.DeleteStudyTopic(item.Id);
            RenderStudyPage();
        };
        opSp.Children.Add(delBtn);

        Grid.SetColumn(opSp, 1);
        topGrid.Children.Add(opSp);
        sp.Children.Add(topGrid);

        // 主题标题
        sp.Children.Add(new TextBlock
        {
            Text = item.Title,
            FontSize = 16,
            FontWeight = FontWeights.Bold,
            Foreground = ThemeService.CurrentTextBrush,
            Margin = new Thickness(0, 0, 0, 6)
        });

        // 进度与多维度指标展示 (时长、节数、页数、自定义单位)
        var progGrid = new Grid { Margin = new Thickness(0, 0, 0, 6) };
        progGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        progGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        string unitName = item.ProgressType switch
        {
            "LESSONS" => "节",
            "PAGES" => "页",
            "CUSTOM" => string.IsNullOrWhiteSpace(item.CustomUnit) ? "项" : item.CustomUnit,
            _ => "h"
        };
        string typeIcon = item.ProgressType switch
        {
            "LESSONS" => "📑 节数",
            "PAGES" => "📖 页数",
            "CUSTOM" => "🎯 " + (string.IsNullOrWhiteSpace(item.CustomUnit) ? "进度" : item.CustomUnit),
            _ => "⏱️ 学时"
        };

        double currentVal = item.ProgressType == "HOURS" || string.IsNullOrEmpty(item.ProgressType) 
            ? (item.CompletedHours > 0 ? item.CompletedHours : item.CompletedValue)
            : item.CompletedValue;
        double targetVal = item.ProgressType == "HOURS" || string.IsNullOrEmpty(item.ProgressType)
            ? (item.TargetHours > 0 ? item.TargetHours : item.TargetValue)
            : item.TargetValue;

        string progressDesc = $"{typeIcon}: 已完成 {currentVal:0.#} {unitName} / 目标 {targetVal:0.#} {unitName}";
        if (item.ProgressType != "HOURS" && item.CompletedHours > 0)
        {
            progressDesc += $" (累计投入 {item.CompletedHours:0.0}h 专注)";
        }

        progGrid.Children.Add(new TextBlock
        {
            Text = progressDesc,
            FontSize = 12,
            Foreground = ThemeService.CurrentSecondaryTextBrush
        });

        var progTb = new TextBlock
        {
            Text = $"{item.ProgressPercent}%",
            FontSize = 12,
            FontWeight = FontWeights.Bold,
            Foreground = isDone ? new SolidColorBrush(Color.FromRgb(0x34, 0xD3, 0x99)) : new SolidColorBrush(Color.FromRgb(0x8A, 0xB4, 0xF8))
        };
        Grid.SetColumn(progTb, 1);
        progGrid.Children.Add(progTb);
        sp.Children.Add(progGrid);

        // 进度条
        sp.Children.Add(new ProgressBar
        {
            Height = 6,
            Maximum = 100,
            Value = item.ProgressPercent,
            Background = ThemeService.CurrentControlBrush,
            Foreground = isDone ? new SolidColorBrush(Color.FromRgb(0x34, 0xD3, 0x99)) : new SolidColorBrush(Color.FromRgb(0x1A, 0x73, 0xE8)),
            Margin = new Thickness(0, 0, 0, 10)
        });

        // 当前攻关里程碑 (Checkpoint)
        if (!string.IsNullOrWhiteSpace(item.CurrentCheckpoint))
        {
            var cpBorder = new Border
            {
                Background = ThemeService.CurrentControlBrush,
                BorderBrush = ThemeService.CurrentBorderBrush,
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(6),
                Padding = new Thickness(10, 6, 10, 6),
                Margin = new Thickness(0, 0, 0, 8)
            };
            cpBorder.Child = new TextBlock
            {
                Text = $"🎯 当前章节/里程碑: {item.CurrentCheckpoint}",
                FontSize = 12,
                Foreground = new SolidColorBrush(Color.FromRgb(0xFB, 0xBF, 0x24)),
                TextWrapping = TextWrapping.Wrap
            };
            sp.Children.Add(cpBorder);
        }

        // 笔记/资料
        if (!string.IsNullOrWhiteSpace(item.Notes))
        {
            sp.Children.Add(new TextBlock
            {
                Text = $"📝 笔记心得: {item.Notes}",
                FontSize = 11,
                Foreground = ThemeService.CurrentSecondaryTextBrush,
                TextWrapping = TextWrapping.Wrap,
                Margin = new Thickness(0, 0, 0, 10)
            });
        }

        // 结业配速预测 (Velocity & Forecasting)
        var forecast = SpacedRepetitionService.ForecastStudyCompletion(item);
        sp.Children.Add(new TextBlock
        {
            Text = $"📈 达成预测: {forecast.StatusSummary}",
            FontSize = 11,
            Foreground = ThemeService.IsCurrentDark ? new SolidColorBrush(Color.FromRgb(0x67, 0xE8, 0xF9)) : new SolidColorBrush(Color.FromRgb(0x08, 0x91, 0xB2)),
            Margin = new Thickness(0, 0, 0, 8)
        });

        // 底部快捷打卡操作条
        var actionRow = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 4, 0, 0) };

        // 非纯时长模式提供快捷 +1 推进按钮 (如 +1讲 / +10页 / +1题)
        if (item.ProgressType != "HOURS" && !isDone)
        {
            string stepLabel = item.ProgressType == "PAGES" ? "+10页" : $"+1{unitName}";
            double stepVal = item.ProgressType == "PAGES" ? 10.0 : 1.0;
            var stepBtn = new Button
            {
                Content = $"⚡ 推进 {stepLabel}",
                Style = (Style)FindResource("GoogleOutlineBtnStyle"),
                FontSize = 11,
                Padding = new Thickness(10, 4, 10, 4),
                Margin = new Thickness(0, 0, 8, 0),
                ToolTip = $"快捷记录进度 (+{stepVal}{unitName})"
            };
            stepBtn.Click += (s, e) =>
            {
                item.CompletedValue = Math.Round(item.CompletedValue + stepVal, 2);
                if (item.TargetValue > 0)
                {
                    item.ProgressPercent = Math.Min(100, (int)Math.Round((item.CompletedValue / item.TargetValue) * 100));
                }
                if (item.ProgressPercent >= 100)
                {
                    item.Status = "COMPLETED";
                }
                DatabaseService.UpsertStudyTopic(item);
                RenderStudyPage();
            };
            actionRow.Children.Add(stepBtn);
        }

        var ebbinghausBtn = new Button
        {
            Content = "🔁 艾宾浩斯复习链",
            Style = (Style)FindResource("GoogleTopBtnStyle"),
            Background = ThemeService.CurrentControlBrush,
            Foreground = ThemeService.CurrentAccentBrush,
            FontSize = 11,
            Padding = new Thickness(10, 4, 10, 4),
            Margin = new Thickness(0, 0, 8, 0),
            ToolTip = "自动生成 +1d, +3d, +7d, +15d, +30d 的科学艾宾浩斯复习任务并沉淀至待办敏捷池"
        };
        ebbinghausBtn.Click += (s, e) =>
        {
            var created = SpacedRepetitionService.GenerateEbbinghausTasks(item.Title, item.Id);
            RefreshBacklogList();
            MessageBox.Show($"🎉 已为「{item.Title}」成功生成 5 级艾宾浩斯渐进复习任务（+1d、+3d、+7d、+15d、+30d）并存入待办敏捷池！", "艾宾浩斯复习链已生成", MessageBoxButton.OK, MessageBoxImage.Information);
        };
        actionRow.Children.Add(ebbinghausBtn);

        var focusTimerBtn = new Button
        {
            Content = "⏱️ 开启专注计时",
            Style = (Style)FindResource("GoogleActionBtnStyle"),
            Background = ThemeService.CurrentAccentBrush,
            FontSize = 11,
            Padding = new Thickness(10, 4, 10, 4),
            Margin = new Thickness(0, 0, 8, 0),
            ToolTip = "启动悬浮胶囊专注时钟进行真实计时，完成时自动防作弊结算至本课题"
        };
        focusTimerBtn.Click += (s, e) =>
        {
            var now = DateTime.Now;
            var studySchedule = new ScheduleItem
            {
                Id = Guid.NewGuid().ToString(),
                Title = $"[攻坚学习] {item.Title}",
                Description = $"围绕学习档案「{item.Title}」展开深度专注研读。\n当前攻关重点：{item.CurrentCheckpoint}",
                Category = "STUDY",
                StudyTopicId = item.Id,
                WorkType = "DEEP_WORK",
                Priority = "HIGH",
                Status = "IN_PROGRESS",
                StartTime = now,
                EndTime = now.AddMinutes(45),
                EstimatedMinutes = 45,
                ActualMinutes = 0
            };

            DatabaseService.UpsertSchedule(studySchedule);
            LaunchFocusHudForSchedule(studySchedule);
        };
        actionRow.Children.Add(focusTimerBtn);

        if (isDone)
        {
            var reopenBtn = new Button
            {
                Content = "↩️ 继续精读",
                Style = (Style)FindResource("GoogleOutlineBtnStyle"),
                FontSize = 11,
                Padding = new Thickness(10, 4, 10, 4)
            };
            reopenBtn.Click += (s, e) =>
            {
                item.Status = "IN_PROGRESS";
                DatabaseService.UpsertStudyTopic(item);
                RenderStudyPage();
            };
            actionRow.Children.Add(reopenBtn);
        }
        else
        {
            var doneBtn = new Button
            {
                Content = "🏆 标为学成结业",
                Style = (Style)FindResource("GoogleActionBtnStyle"),
                Background = new SolidColorBrush(Color.FromRgb(0x05, 0x96, 0x69)),
                FontSize = 11,
                Padding = new Thickness(10, 4, 10, 4)
            };
            doneBtn.Click += (s, e) =>
            {
                item.Status = "COMPLETED";
                item.ProgressPercent = 100;
                DatabaseService.UpsertStudyTopic(item);
                RenderStudyPage();
            };
            actionRow.Children.Add(doneBtn);
        }

        sp.Children.Add(actionRow);
        card.Child = sp;
        return card;
    }

    private void OnStudyTopicProgressTypeChanged(object sender, SelectionChangedEventArgs e)
    {
        if (StudyTopicProgressTypeInput?.SelectedItem is ComboBoxItem cbi && cbi.Tag is string pType)
        {
            UpdateStudyTopicModalUnitLabels(pType);
        }
    }

    private void OnStudyTopicCustomUnitChanged(object sender, TextChangedEventArgs e)
    {
        if (StudyTopicProgressTypeInput?.SelectedItem is ComboBoxItem cbi && cbi.Tag?.ToString() == "CUSTOM")
        {
            UpdateStudyTopicModalUnitLabels("CUSTOM");
        }
    }

    private void UpdateStudyTopicModalUnitLabels(string pType)
    {
        if (StudyTopicTargetValueLabel == null || StudyTopicCompletedValueLabel == null || StudyTopicCustomUnitContainer == null) return;

        StudyTopicCustomUnitContainer.Visibility = pType == "CUSTOM" ? Visibility.Visible : Visibility.Collapsed;

        string customUnit = string.IsNullOrWhiteSpace(StudyTopicCustomUnitInput?.Text) ? "项" : StudyTopicCustomUnitInput.Text.Trim();
        string unitStr = pType switch
        {
            "LESSONS" => "节",
            "PAGES" => "页",
            "CUSTOM" => customUnit,
            _ => "h"
        };

        StudyTopicTargetValueLabel.Text = $"目标总量 ({unitStr})：";
        StudyTopicCompletedValueLabel.Text = $"已完成量 ({unitStr})：";

        if (pType == "HOURS")
        {
            StudyTopicCompletedHoursInput.IsReadOnly = true;
            StudyTopicCompletedHoursInput.Focusable = false;
            StudyTopicCompletedHoursInput.Background = (Brush)FindResource("ControlBackground");
            StudyTopicCompletedAntiCheatBadge.Text = "🔒 专注计时";
            StudyTopicCompletedAntiCheatBadge.ToolTip = "学习时长模式下由真实专注打卡防作弊累计";
        }
        else
        {
            StudyTopicCompletedHoursInput.IsReadOnly = false;
            StudyTopicCompletedHoursInput.Focusable = true;
            StudyTopicCompletedHoursInput.Background = (Brush)FindResource("InputBackground");
            StudyTopicCompletedAntiCheatBadge.Text = "✏️ 可编辑";
            StudyTopicCompletedAntiCheatBadge.ToolTip = "节数/页数/自定义数量支持手动调整或点击卡片快速推进";
        }

        RecalculateStudyTopicModalProgress();
    }

    private void OnStudyTopicTargetValueChanged(object sender, TextChangedEventArgs e)
    {
        RecalculateStudyTopicModalProgress();
    }

    private void OnStudyTopicCompletedValueChanged(object sender, TextChangedEventArgs e)
    {
        RecalculateStudyTopicModalProgress();
    }

    private void RecalculateStudyTopicModalProgress()
    {
        if (StudyTopicTargetHoursInput == null || StudyTopicCompletedHoursInput == null || StudyTopicProgressInput == null) return;
        double.TryParse(StudyTopicTargetHoursInput.Text.Trim(), out double target);
        double.TryParse(StudyTopicCompletedHoursInput.Text.Trim(), out double completed);
        int percent = target > 0 ? Math.Min(100, Math.Max(0, (int)Math.Round((completed / target) * 100))) : 0;
        StudyTopicProgressInput.Text = percent.ToString();
    }

    private void OnCreateStudyTopicClicked(object sender, RoutedEventArgs e)
    {
        _editingStudyTopicId = null;
        StudyTopicModalHeader.Text = "新建学习主题";
        StudyTopicTitleInput.Text = "";
        StudyTopicCategoryInput.SelectedIndex = 0;
        StudyTopicStatusInput.SelectedIndex = 0;
        StudyTopicProgressTypeInput.SelectedIndex = 0; // 默认学习时长 (小时)
        StudyTopicCustomUnitInput.Text = "题";
        StudyTopicTargetHoursInput.Text = "20";
        StudyTopicCompletedHoursInput.Text = "0";
        StudyTopicProgressInput.Text = "0";
        StudyTopicCheckpointInput.Text = "";
        StudyTopicNotesInput.Text = "";
        UpdateStudyTopicModalUnitLabels("HOURS");
        StudyTopicModal.Visibility = Visibility.Visible;
    }

    private void OnEditStudyTopicClicked(StudyTopicItem item)
    {
        _editingStudyTopicId = item.Id;
        StudyTopicModalHeader.Text = "编辑学习主题";
        StudyTopicTitleInput.Text = item.Title;

        // 设置分类
        foreach (ComboBoxItem cbItem in StudyTopicCategoryInput.Items)
        {
            if (cbItem.Content.ToString() == item.Category)
            {
                StudyTopicCategoryInput.SelectedItem = cbItem;
                break;
            }
        }

        // 设置状态
        foreach (ComboBoxItem cbItem in StudyTopicStatusInput.Items)
        {
            if (cbItem.Tag?.ToString() == item.Status)
            {
                StudyTopicStatusInput.SelectedItem = cbItem;
                break;
            }
        }

        // 设置计量类型
        string pType = string.IsNullOrEmpty(item.ProgressType) ? "HOURS" : item.ProgressType;
        foreach (ComboBoxItem cbItem in StudyTopicProgressTypeInput.Items)
        {
            if (cbItem.Tag?.ToString() == pType)
            {
                StudyTopicProgressTypeInput.SelectedItem = cbItem;
                break;
            }
        }

        StudyTopicCustomUnitInput.Text = string.IsNullOrEmpty(item.CustomUnit) ? "题" : item.CustomUnit;
        double targetVal = pType == "HOURS" ? (item.TargetHours > 0 ? item.TargetHours : item.TargetValue) : item.TargetValue;
        double completedVal = pType == "HOURS" ? (item.CompletedHours > 0 ? item.CompletedHours : item.CompletedValue) : item.CompletedValue;

        StudyTopicTargetHoursInput.Text = targetVal.ToString("0.#");
        StudyTopicCompletedHoursInput.Text = completedVal.ToString("0.#");
        StudyTopicProgressInput.Text = item.ProgressPercent.ToString();
        StudyTopicCheckpointInput.Text = item.CurrentCheckpoint;
        StudyTopicNotesInput.Text = item.Notes;

        UpdateStudyTopicModalUnitLabels(pType);
        StudyTopicModal.Visibility = Visibility.Visible;
    }

    private void OnCloseStudyTopicModalClicked(object sender, RoutedEventArgs e)
    {
        StudyTopicModal.Visibility = Visibility.Collapsed;
    }

    private void OnSaveStudyTopicClicked(object sender, RoutedEventArgs e)
    {
        string title = StudyTopicTitleInput.Text.Trim();
        if (string.IsNullOrWhiteSpace(title))
        {
            System.Windows.MessageBox.Show("请输入学习主题名称", "提示", MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }

        string category = (StudyTopicCategoryInput.SelectedItem as ComboBoxItem)?.Content.ToString() ?? "技术栈";
        string status = (StudyTopicStatusInput.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "IN_PROGRESS";
        string progressType = (StudyTopicProgressTypeInput.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "HOURS";
        string customUnit = StudyTopicCustomUnitInput.Text.Trim();

        double.TryParse(StudyTopicTargetHoursInput.Text.Trim(), out double targetVal);
        if (targetVal <= 0) targetVal = progressType == "HOURS" ? 10 : (progressType == "PAGES" ? 100 : 20);

        double.TryParse(StudyTopicCompletedHoursInput.Text.Trim(), out double completedVal);
        if (completedVal < 0) completedVal = 0;

        double completedHours = 0.0;
        double targetHours = progressType == "HOURS" ? targetVal : 10.0;

        if (!string.IsNullOrEmpty(_editingStudyTopicId))
        {
            var existingTopic = DatabaseService.GetAllStudyTopics().FirstOrDefault(t => t.Id == _editingStudyTopicId);
            if (existingTopic != null)
            {
                completedHours = existingTopic.CompletedHours;
                if (progressType == "HOURS")
                {
                    // 时长模式下保持防作弊真实学时
                    completedVal = existingTopic.CompletedHours;
                }
            }
        }
        else
        {
            if (progressType == "HOURS")
            {
                completedVal = 0.0;
                completedHours = 0.0;
            }
        }

        if (progressType == "HOURS")
        {
            completedHours = completedVal;
        }

        // 依据所选维度目标与完成量计算进度百分比
        int progress = targetVal > 0 ? Math.Min(100, Math.Max(0, (int)Math.Round((completedVal / targetVal) * 100))) : 0;
        if (progress >= 100 && status != "COMPLETED")
        {
            status = "COMPLETED";
        }

        string checkpoint = StudyTopicCheckpointInput.Text.Trim();
        string notes = StudyTopicNotesInput.Text.Trim();

        var topic = new StudyTopicItem
        {
            Id = _editingStudyTopicId ?? Guid.NewGuid().ToString(),
            Title = title,
            Category = category,
            Status = status,
            ProgressType = progressType,
            CustomUnit = customUnit,
            TargetValue = targetVal,
            CompletedValue = completedVal,
            TargetHours = targetHours,
            CompletedHours = completedHours,
            ProgressPercent = progress,
            CurrentCheckpoint = checkpoint,
            Notes = notes,
            UpdatedAt = DateTime.UtcNow
        };

        DatabaseService.UpsertStudyTopic(topic);
        StudyTopicModal.Visibility = Visibility.Collapsed;
        RenderStudyPage();
    }

    // =========================================================================
    // =========================================================================
    // ================== FITNESS & WORKOUT (每日专业健身规划与记录) LOGIC =================
    // =========================================================================

    private DateTime _fitnessSelectedDate = DateTime.Today;
    private string? _editingFitnessPlanId = null;
    private DispatcherTimer? _fitnessRestTimer = null;
    private int _restTimerTotalSeconds = 90;
    private int _restTimerRemainingSeconds = 0;
    private bool _isRestTimerPaused = false;
    private string _fitnessSelectedCategoryFilter = "ALL";

    private static string GetFitnessCategoryColorHex(string category)
    {
        return category?.ToUpperInvariant() switch
        {
            "CHEST" => "#EF4444",
            "BACK" => "#3B82F6",
            "LEGS" => "#10B981",
            "SHOULDERS" => "#F59E0B",
            "ARMS" => "#8B5CF6",
            "CORE" => "#EC4899",
            "CARDIO" => "#06B6D4",
            "STRETCH" => "#14B8A6",
            _ => "#6B7280"
        };
    }

    private static string GetFitnessCategoryDisplayName(string category)
    {
        return category?.ToUpperInvariant() switch
        {
            "CHEST" => "胸部",
            "BACK" => "背部",
            "LEGS" => "腿部",
            "SHOULDERS" => "肩部",
            "ARMS" => "手臂",
            "CORE" => "核心",
            "CARDIO" => "有氧",
            "STRETCH" => "拉伸",
            _ => "其他"
        };
    }

    // ----------------- 组间休息计时器 (Rest Timer) -----------------

    private void StartRestTimer(int seconds = 90)
    {
        _fitnessRestTimer?.Stop();
        _restTimerTotalSeconds = seconds;
        _restTimerRemainingSeconds = seconds;
        _isRestTimerPaused = false;

        FitnessRestTimerDock.Visibility = Visibility.Visible;
        FitnessRestTimerText.Text = $"{_restTimerRemainingSeconds / 60:D2}:{_restTimerRemainingSeconds % 60:D2}";
        FitnessRestTimerProgress.Maximum = _restTimerTotalSeconds;
        FitnessRestTimerProgress.Value = _restTimerRemainingSeconds;
        FitnessTimerPauseResumeBtn.Content = "暂停";

        _fitnessRestTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1) };
        _fitnessRestTimer.Tick += (s, e) =>
        {
            if (_isRestTimerPaused) return;
            _restTimerRemainingSeconds--;
            if (_restTimerRemainingSeconds <= 0)
            {
                _fitnessRestTimer?.Stop();
                _fitnessRestTimer = null;
                FitnessRestTimerDock.Visibility = Visibility.Collapsed;
                try { System.Media.SystemSounds.Exclamation.Play(); } catch { }
            }
            else
            {
                FitnessRestTimerText.Text = $"{_restTimerRemainingSeconds / 60:D2}:{_restTimerRemainingSeconds % 60:D2}";
                FitnessRestTimerProgress.Value = _restTimerRemainingSeconds;
            }
        };
        _fitnessRestTimer.Start();
    }

    private void OnFitnessTimerSub15Clicked(object sender, RoutedEventArgs e)
    {
        _restTimerRemainingSeconds = Math.Max(0, _restTimerRemainingSeconds - 15);
        if (_restTimerRemainingSeconds <= 0)
        {
            _fitnessRestTimer?.Stop();
            _fitnessRestTimer = null;
            FitnessRestTimerDock.Visibility = Visibility.Collapsed;
        }
        else
        {
            FitnessRestTimerText.Text = $"{_restTimerRemainingSeconds / 60:D2}:{_restTimerRemainingSeconds % 60:D2}";
            FitnessRestTimerProgress.Value = _restTimerRemainingSeconds;
        }
    }

    private void OnFitnessTimerAdd30Clicked(object sender, RoutedEventArgs e)
    {
        _restTimerRemainingSeconds += 30;
        if (_restTimerRemainingSeconds > _restTimerTotalSeconds)
        {
            _restTimerTotalSeconds = _restTimerRemainingSeconds;
            FitnessRestTimerProgress.Maximum = _restTimerTotalSeconds;
        }
        FitnessRestTimerText.Text = $"{_restTimerRemainingSeconds / 60:D2}:{_restTimerRemainingSeconds % 60:D2}";
        FitnessRestTimerProgress.Value = _restTimerRemainingSeconds;
    }

    private void OnFitnessTimerPauseResumeClicked(object sender, RoutedEventArgs e)
    {
        _isRestTimerPaused = !_isRestTimerPaused;
        FitnessTimerPauseResumeBtn.Content = _isRestTimerPaused ? "继续" : "暂停";
    }

    private void OnFitnessTimerSkipClicked(object sender, RoutedEventArgs e)
    {
        _fitnessRestTimer?.Stop();
        _fitnessRestTimer = null;
        FitnessRestTimerDock.Visibility = Visibility.Collapsed;
    }

    // ----------------- 专业动作库选择器 -----------------

    private void OnFitnessCategoryFilterClicked(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is string tag)
        {
            _fitnessSelectedCategoryFilter = tag;
            foreach (var child in FitnessCategoryFilterPanel.Children)
            {
                if (child is Button b)
                {
                    b.Style = (b.Tag?.ToString() == tag) ? (Style)FindResource("GoogleActionBtnStyle") : (Style)FindResource("GoogleOutlineBtnStyle");
                }
            }
            RenderExerciseLibraryPicker();
        }
    }

    private void OnFitnessExerciseSearchChanged(object sender, TextChangedEventArgs e)
    {
        RenderExerciseLibraryPicker();
    }

    private void RenderExerciseLibraryPicker()
    {
        FitnessExerciseLibraryListPanel.Children.Clear();
        string query = FitnessExerciseSearchInput.Text.Trim();
        var exercises = ExerciseLibrary.SearchExercises(query, _fitnessSelectedCategoryFilter);

        if (exercises.Count == 0)
        {
            FitnessExerciseLibraryListPanel.Children.Add(new TextBlock
            {
                Text = "未匹配到动作，可展开下方「➕ 自定义新动作录入」自由添加！",
                FontSize = 11,
                Foreground = (Brush)FindResource("TextSecondary"),
                Margin = new Thickness(8, 12, 8, 12),
                HorizontalAlignment = HorizontalAlignment.Center
            });
            return;
        }

        foreach (var ex in exercises)
        {
            var itemBorder = new Border
            {
                Background = (Brush)FindResource("ControlBackground"),
                BorderBrush = (Brush)FindResource("CardBorderBrush"),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(6),
                Padding = new Thickness(10, 8, 10, 8),
                Margin = new Thickness(0, 0, 0, 6)
            };

            var grid = new Grid();
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            var infoStack = new StackPanel();

            var titleRow = new StackPanel { Orientation = Orientation.Horizontal };
            string catColor = GetFitnessCategoryColorHex(ex.Category);
            var catChip = new Border
            {
                Background = new SolidColorBrush((Color)ColorConverter.ConvertFromString(catColor)) { Opacity = 0.2 },
                BorderBrush = new SolidColorBrush((Color)ColorConverter.ConvertFromString(catColor)),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(3),
                Padding = new Thickness(5, 1, 5, 1),
                Margin = new Thickness(0, 0, 6, 0),
                VerticalAlignment = VerticalAlignment.Center
            };
            catChip.Child = new TextBlock
            {
                Text = ex.CategoryName,
                FontSize = 9.5,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString(catColor))
            };
            titleRow.Children.Add(catChip);

            if (!string.IsNullOrWhiteSpace(ex.Equipment))
            {
                var eqChip = new Border
                {
                    Background = (Brush)FindResource("ControlBackground"),
                    BorderBrush = (Brush)FindResource("CardBorderBrush"),
                    BorderThickness = new Thickness(1),
                    CornerRadius = new CornerRadius(3),
                    Padding = new Thickness(4, 1, 4, 1),
                    Margin = new Thickness(0, 0, 6, 0),
                    VerticalAlignment = VerticalAlignment.Center
                };
                eqChip.Child = new TextBlock
                {
                    Text = ex.Equipment,
                    FontSize = 9,
                    Foreground = (Brush)FindResource("TextSecondary")
                };
                titleRow.Children.Add(eqChip);
            }

            titleRow.Children.Add(new TextBlock
            {
                Text = ex.Name,
                FontSize = 12.5,
                FontWeight = FontWeights.Bold,
                Foreground = (Brush)FindResource("TextPrimary"),
                VerticalAlignment = VerticalAlignment.Center
            });

            infoStack.Children.Add(titleRow);

            infoStack.Children.Add(new TextBlock
            {
                Text = $"{ex.NameEn} · 默认 {ex.DefaultSets}组 × {ex.DefaultReps}次" + (ex.DefaultWeightKg > 0 ? $" · {ex.DefaultWeightKg}kg" : "") + $" · {ex.PrimaryMuscles}",
                FontSize = 10,
                Foreground = (Brush)FindResource("TextSecondary"),
                Margin = new Thickness(0, 3, 0, 0)
            });

            Grid.SetColumn(infoStack, 0);
            grid.Children.Add(infoStack);

            var selectBtn = new Button
            {
                Content = "+ 选取",
                Style = (Style)FindResource("GoogleActionBtnStyle"),
                Padding = new Thickness(10, 4, 10, 4),
                FontSize = 11,
                FontWeight = FontWeights.SemiBold,
                VerticalAlignment = VerticalAlignment.Center,
                Cursor = Cursors.Hand
            };
            selectBtn.Click += (s, e) =>
            {
                AddExerciseToTodayWorkout(ex);
                FitnessExerciseModal.Visibility = Visibility.Collapsed;
            };
            Grid.SetColumn(selectBtn, 1);
            grid.Children.Add(selectBtn);

            itemBorder.Child = grid;
            FitnessExerciseLibraryListPanel.Children.Add(itemBorder);
        }
    }

    private void AddExerciseToTodayWorkout(ExerciseInfo exInfo)
    {
        string dateStr = _fitnessSelectedDate.ToString("yyyy-MM-dd");
        var plan = DatabaseService.GetFitnessPlanByDate(dateStr);
        if (plan == null)
        {
            plan = new FitnessPlanItem
            {
                Id = Guid.NewGuid().ToString(),
                PlanDate = dateStr,
                Title = $"{exInfo.CategoryName}专项力量训练",
                WorkoutType = exInfo.Category == "CARDIO" ? "CARDIO" : "STRENGTH",
                TargetDurationMinutes = 45
            };
            DatabaseService.UpsertFitnessPlan(plan);
        }

        var initialSets = new List<WorkoutSetItem>();
        for (int idx = 1; idx <= exInfo.DefaultSets; idx++)
        {
            bool isWarmup = (idx == 1 && exInfo.DefaultWeightKg > 20);
            double setWeight = isWarmup ? Math.Round(exInfo.DefaultWeightKg * 0.6 / 2.5) * 2.5 : exInfo.DefaultWeightKg;
            initialSets.Add(new WorkoutSetItem
            {
                SetNumber = idx,
                Type = isWarmup ? "W" : "N",
                WeightKg = setWeight,
                Reps = exInfo.DefaultReps,
                IsCompleted = false
            });
        }

        var existingRecords = DatabaseService.GetFitnessRecordsByDate(dateStr);
        var rec = new FitnessRecordItem
        {
            Id = Guid.NewGuid().ToString(),
            PlanId = plan.Id,
            RecordDate = dateStr,
            ExerciseName = exInfo.Name,
            Category = exInfo.Category,
            Equipment = exInfo.Equipment,
            SetsData = WorkoutSetItem.ToJsonArray(initialSets),
            TargetSets = exInfo.DefaultSets,
            TargetReps = exInfo.DefaultReps,
            TargetWeightKg = exInfo.DefaultWeightKg,
            CaloriesBurned = 70,
            Notes = exInfo.Tips,
            SortOrder = existingRecords.Count
        };

        DatabaseService.UpsertFitnessRecord(rec);
        RenderFitnessPage();
        SyncFitnessPlanToSchedule(dateStr);
    }

    private void SaveRecordSets(FitnessRecordItem rec, List<WorkoutSetItem> sets)
    {
        rec.SetsData = WorkoutSetItem.ToJsonArray(sets);
        rec.TargetSets = sets.Count;
        rec.TargetReps = sets.FirstOrDefault()?.Reps ?? 10;
        rec.TargetWeightKg = sets.Count > 0 ? sets.Max(s => s.WeightKg) : 0;
        rec.IsCompleted = sets.Count > 0 && sets.All(s => s.IsCompleted);
        rec.UpdatedAt = DateTime.UtcNow;
        DatabaseService.UpsertFitnessRecord(rec);
        RenderFitnessPage();
        SyncFitnessPlanToSchedule(rec.RecordDate);
    }

    private void RenderFitnessPage()
    {
        string dayOfWeekStr = _fitnessSelectedDate.DayOfWeek switch
        {
            DayOfWeek.Monday => "周一",
            DayOfWeek.Tuesday => "周二",
            DayOfWeek.Wednesday => "周三",
            DayOfWeek.Thursday => "周四",
            DayOfWeek.Friday => "周五",
            DayOfWeek.Saturday => "周六",
            DayOfWeek.Sunday => "周日",
            _ => ""
        };
        FitnessDateRangeText.Text = $"{_fitnessSelectedDate:yyyy年MM月dd日} · {dayOfWeekStr}";

        string dateStr = _fitnessSelectedDate.ToString("yyyy-MM-dd");
        var plan = DatabaseService.GetFitnessPlanByDate(dateStr);
        var records = DatabaseService.GetFitnessRecordsByDate(dateStr);
        int streakDays = DatabaseService.GetFitnessStreakDays();
        var (weekCount, weekMins, weekCalories) = DatabaseService.GetWeeklyFitnessStats();

        // Total volume and sets count calculation
        double totalVolumeKg = records.Sum(r => r.CompletedVolumeKg);
        int totalSetsCount = records.Sum(r => r.GetWorkoutSets().Count);
        int completedSetsCount = records.Sum(r => r.GetWorkoutSets().Count(s => s.IsCompleted));

        // 1. KPI 状态栏展示
        FitnessDoneRatioKpiText.Text = $"✓ {completedSetsCount}/{totalSetsCount} 组";
        FitnessVolumeKpiText.Text = $"⚡ {totalVolumeKg:N0} kg";
        FitnessStreakKpiText.Text = $"🔥 {streakDays} 天 · {weekMins}m";

        if (plan == null)
        {
            FitnessStatusKpiText.Text = "未设定计划";
            FitnessStatusKpiText.Foreground = (Brush)FindResource("TextSecondary");
            FitnessFinishWorkoutBtn.Content = "🎉 打卡结算";
        }
        else
        {
            if (plan.IsCompleted)
            {
                FitnessStatusKpiText.Text = $"✅ 已打卡 · {plan.Title}";
                FitnessStatusKpiText.Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#10B981"));
                FitnessFinishWorkoutBtn.Content = "✏️ 修改打卡记录";
            }
            else
            {
                FitnessStatusKpiText.Text = $"🎯 训练中 · {plan.Title}";
                FitnessStatusKpiText.Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#38BDF8"));
                FitnessFinishWorkoutBtn.Content = "🎉 打卡结算";
            }
        }

        // 2. 渲染今日动作清单
        FitnessExercisesListPanel.Children.Clear();

        if (records.Count == 0)
        {
            var emptyBorder = new Border
            {
                Background = (Brush)FindResource("ControlBackground"),
                BorderBrush = (Brush)FindResource("CardBorderBrush"),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(8),
                Padding = new Thickness(20),
                Margin = new Thickness(0, 4, 0, 0)
            };
            var emptyStack = new StackPanel { HorizontalAlignment = HorizontalAlignment.Center };
            emptyStack.Children.Add(new TextBlock
            {
                Text = "🏋️",
                FontSize = 28,
                HorizontalAlignment = HorizontalAlignment.Center,
                Margin = new Thickness(0, 0, 0, 8)
            });
            emptyStack.Children.Add(new TextBlock
            {
                Text = "今日尚未添加动作计划",
                FontSize = 13,
                FontWeight = FontWeights.SemiBold,
                Foreground = (Brush)FindResource("TextPrimary"),
                HorizontalAlignment = HorizontalAlignment.Center
            });
            emptyStack.Children.Add(new TextBlock
            {
                Text = "点击上方「⚡ 载入模版」导入经典推拉腿方案，或点击「➕ 添加动作」从 60+ 权威动作库选取！",
                FontSize = 11,
                Foreground = (Brush)FindResource("TextSecondary"),
                HorizontalAlignment = HorizontalAlignment.Center,
                Margin = new Thickness(0, 4, 0, 0)
            });
            emptyBorder.Child = emptyStack;
            FitnessExercisesListPanel.Children.Add(emptyBorder);
        }
        else
        {
            foreach (var rec in records)
            {
                var sets = rec.GetWorkoutSets();
                double best1RM = rec.BestEstimated1RM;
                double completedVol = rec.CompletedVolumeKg;
                bool isAllDone = sets.Count > 0 && sets.All(s => s.IsCompleted);

                var card = new Border
                {
                    Background = (Brush)FindResource("ControlBackground"),
                    BorderBrush = isAllDone ? new SolidColorBrush((Color)ColorConverter.ConvertFromString("#10B981")) : (Brush)FindResource("CardBorderBrush"),
                    BorderThickness = new Thickness(1),
                    CornerRadius = new CornerRadius(8),
                    Padding = new Thickness(12, 10, 12, 10),
                    Margin = new Thickness(0, 0, 0, 10)
                };

                var cardStack = new StackPanel();

                // Top Header Row
                var headerGrid = new Grid { Margin = new Thickness(0, 0, 0, 6) };
                headerGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                headerGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

                var leftHeader = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };

                string catColor = GetFitnessCategoryColorHex(rec.Category);
                var catChip = new Border
                {
                    Background = new SolidColorBrush((Color)ColorConverter.ConvertFromString(catColor)) { Opacity = 0.2 },
                    BorderBrush = new SolidColorBrush((Color)ColorConverter.ConvertFromString(catColor)),
                    BorderThickness = new Thickness(1),
                    CornerRadius = new CornerRadius(4),
                    Padding = new Thickness(6, 1, 6, 1),
                    Margin = new Thickness(0, 0, 6, 0),
                    VerticalAlignment = VerticalAlignment.Center
                };
                catChip.Child = new TextBlock
                {
                    Text = GetFitnessCategoryDisplayName(rec.Category),
                    FontSize = 10,
                    FontWeight = FontWeights.Bold,
                    Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString(catColor))
                };
                leftHeader.Children.Add(catChip);

                if (!string.IsNullOrWhiteSpace(rec.Equipment))
                {
                    var eqChip = new Border
                    {
                        Background = (Brush)FindResource("ControlBackground"),
                        BorderBrush = (Brush)FindResource("CardBorderBrush"),
                        BorderThickness = new Thickness(1),
                        CornerRadius = new CornerRadius(4),
                        Padding = new Thickness(5, 1, 5, 1),
                        Margin = new Thickness(0, 0, 6, 0),
                        VerticalAlignment = VerticalAlignment.Center
                    };
                    eqChip.Child = new TextBlock
                    {
                        Text = rec.Equipment,
                        FontSize = 9.5,
                        Foreground = (Brush)FindResource("TextSecondary")
                    };
                    leftHeader.Children.Add(eqChip);
                }

                var titleTb = new TextBlock
                {
                    Text = rec.ExerciseName,
                    FontSize = 13.5,
                    FontWeight = FontWeights.Bold,
                    Foreground = isAllDone ? (Brush)FindResource("TextSecondary") : (Brush)FindResource("TextPrimary"),
                    VerticalAlignment = VerticalAlignment.Center
                };
                if (isAllDone) titleTb.TextDecorations = TextDecorations.Strikethrough;
                leftHeader.Children.Add(titleTb);

                Grid.SetColumn(leftHeader, 0);
                headerGrid.Children.Add(leftHeader);

                // Right Delete Button
                var delBtn = new Button
                {
                    Content = "🗑️",
                    Style = (Style)FindResource("GoogleTopBtnStyle"),
                    Width = 26,
                    Height = 26,
                    Padding = new Thickness(0),
                    FontSize = 11,
                    ToolTip = "删除动作",
                    VerticalAlignment = VerticalAlignment.Center,
                    Cursor = Cursors.Hand
                };
                delBtn.Click += (s, e) =>
                {
                    DatabaseService.DeleteFitnessRecord(rec.Id);
                    RenderFitnessPage();
                    SyncFitnessPlanToSchedule(rec.RecordDate);
                };
                Grid.SetColumn(delBtn, 1);
                headerGrid.Children.Add(delBtn);

                cardStack.Children.Add(headerGrid);

                // Stats Subtitle (1RM & Volume)
                var statsStack = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 6) };
                if (best1RM > 0)
                {
                    statsStack.Children.Add(new TextBlock
                    {
                        Text = $"🔥 最佳1RM: {best1RM:0.#}kg",
                        FontSize = 10.5,
                        Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#38BDF8")),
                        Margin = new Thickness(0, 0, 10, 0)
                    });
                }
                if (completedVol > 0)
                {
                    statsStack.Children.Add(new TextBlock
                    {
                        Text = $"📊 完成容量: {completedVol:0.#}kg",
                        FontSize = 10.5,
                        Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString("#F59E0B")),
                        Margin = new Thickness(0, 0, 10, 0)
                    });
                }
                if (!string.IsNullOrWhiteSpace(rec.Notes))
                {
                    statsStack.Children.Add(new TextBlock
                    {
                        Text = $"💡 {rec.Notes}",
                        FontSize = 10,
                        Foreground = (Brush)FindResource("TextSecondary")
                    });
                }
                cardStack.Children.Add(statsStack);

                // Professional Sets Table Header
                var tableHeader = new Grid
                {
                    Background = (Brush)FindResource("CardBackground"),
                    Margin = new Thickness(0, 2, 0, 4)
                };
                tableHeader.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(36) }); // 组号
                tableHeader.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(42) }); // 类型
                tableHeader.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(110) }); // 重量
                tableHeader.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(110) }); // 次数
                tableHeader.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(42) }); // 完成
                tableHeader.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(32) }); // 删除

                TextBlock CreateTh(string txt, int col)
                {
                    var tb = new TextBlock
                    {
                        Text = txt,
                        FontSize = 9.5,
                        FontWeight = FontWeights.SemiBold,
                        Foreground = (Brush)FindResource("TextSecondary"),
                        HorizontalAlignment = HorizontalAlignment.Center,
                        VerticalAlignment = VerticalAlignment.Center,
                        Margin = new Thickness(0, 2, 0, 2)
                    };
                    Grid.SetColumn(tb, col);
                    return tb;
                }

                tableHeader.Children.Add(CreateTh("组号", 0));
                tableHeader.Children.Add(CreateTh("类型", 1));
                tableHeader.Children.Add(CreateTh("重量(kg)", 2));
                tableHeader.Children.Add(CreateTh("次数", 3));
                tableHeader.Children.Add(CreateTh("完成", 4));
                tableHeader.Children.Add(CreateTh("", 5));

                cardStack.Children.Add(tableHeader);

                // Sets Rows
                for (int i = 0; i < sets.Count; i++)
                {
                    int setIdx = i;
                    var setItem = sets[i];

                    var rowGrid = new Grid
                    {
                        Background = setItem.IsCompleted
                            ? new SolidColorBrush((Color)ColorConverter.ConvertFromString("#10B981")) { Opacity = 0.12 }
                            : Brushes.Transparent,
                        Margin = new Thickness(0, 1, 0, 1)
                    };
                    rowGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(36) });
                    rowGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(42) });
                    rowGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(110) });
                    rowGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(110) });
                    rowGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(42) });
                    rowGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(32) });

                    // 1. 组号
                    var idxTb = new TextBlock
                    {
                        Text = (setIdx + 1).ToString(),
                        FontSize = 11,
                        FontWeight = FontWeights.Bold,
                        Foreground = (Brush)FindResource("TextPrimary"),
                        HorizontalAlignment = HorizontalAlignment.Center,
                        VerticalAlignment = VerticalAlignment.Center
                    };
                    Grid.SetColumn(idxTb, 0);
                    rowGrid.Children.Add(idxTb);

                    // 2. 类型徽章 (W / N / D / F)
                    string typeColor = setItem.Type switch
                    {
                        "W" => "#F59E0B",
                        "D" => "#8B5CF6",
                        "F" => "#EF4444",
                        _ => "#38BDF8"
                    };
                    string typeLabel = setItem.Type switch
                    {
                        "W" => "W",
                        "D" => "D",
                        "F" => "F",
                        _ => "N"
                    };
                    var typeBtn = new Button
                    {
                        Content = typeLabel,
                        Width = 26,
                        Height = 20,
                        Padding = new Thickness(0),
                        FontSize = 10,
                        FontWeight = FontWeights.Bold,
                        Background = new SolidColorBrush((Color)ColorConverter.ConvertFromString(typeColor)) { Opacity = 0.25 },
                        Foreground = new SolidColorBrush((Color)ColorConverter.ConvertFromString(typeColor)),
                        BorderBrush = new SolidColorBrush((Color)ColorConverter.ConvertFromString(typeColor)),
                        BorderThickness = new Thickness(1),
                        Cursor = Cursors.Hand,
                        ToolTip = "点击切换组类型 (N 正式 / W 热身 / D 递减 / F 力竭)",
                        HorizontalAlignment = HorizontalAlignment.Center,
                        VerticalAlignment = VerticalAlignment.Center
                    };
                    typeBtn.Click += (s, e) =>
                    {
                        setItem.Type = setItem.Type switch
                        {
                            "N" => "W",
                            "W" => "D",
                            "D" => "F",
                            _ => "N"
                        };
                        SaveRecordSets(rec, sets);
                    };
                    Grid.SetColumn(typeBtn, 1);
                    rowGrid.Children.Add(typeBtn);

                    // 3. 重量 Stepper: [-] [weight] [+]
                    var weightStack = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
                    var weightMinusBtn = new Button
                    {
                        Content = "-",
                        Width = 20,
                        Height = 20,
                        Padding = new Thickness(0),
                        FontSize = 11,
                        Style = (Style)FindResource("GoogleOutlineBtnStyle"),
                        Cursor = Cursors.Hand
                    };
                    weightMinusBtn.Click += (s, e) =>
                    {
                        setItem.WeightKg = Math.Max(0, Math.Round((setItem.WeightKg - 2.5) * 10) / 10);
                        SaveRecordSets(rec, sets);
                    };
                    var weightTb = new TextBlock
                    {
                        Text = $"{setItem.WeightKg:0.##}",
                        Width = 46,
                        FontSize = 11,
                        FontWeight = FontWeights.SemiBold,
                        TextAlignment = TextAlignment.Center,
                        VerticalAlignment = VerticalAlignment.Center,
                        Foreground = (Brush)FindResource("TextPrimary")
                    };
                    var weightPlusBtn = new Button
                    {
                        Content = "+",
                        Width = 20,
                        Height = 20,
                        Padding = new Thickness(0),
                        FontSize = 11,
                        Style = (Style)FindResource("GoogleOutlineBtnStyle"),
                        Cursor = Cursors.Hand
                    };
                    weightPlusBtn.Click += (s, e) =>
                    {
                        setItem.WeightKg = Math.Round((setItem.WeightKg + 2.5) * 10) / 10;
                        SaveRecordSets(rec, sets);
                    };
                    weightStack.Children.Add(weightMinusBtn);
                    weightStack.Children.Add(weightTb);
                    weightStack.Children.Add(weightPlusBtn);
                    Grid.SetColumn(weightStack, 2);
                    rowGrid.Children.Add(weightStack);

                    // 4. 次数 Stepper: [-] [reps] [+]
                    var repsStack = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
                    var repsMinusBtn = new Button
                    {
                        Content = "-",
                        Width = 20,
                        Height = 20,
                        Padding = new Thickness(0),
                        FontSize = 11,
                        Style = (Style)FindResource("GoogleOutlineBtnStyle"),
                        Cursor = Cursors.Hand
                    };
                    repsMinusBtn.Click += (s, e) =>
                    {
                        setItem.Reps = Math.Max(1, setItem.Reps - 1);
                        SaveRecordSets(rec, sets);
                    };
                    var repsTb = new TextBlock
                    {
                        Text = setItem.Reps.ToString(),
                        Width = 42,
                        FontSize = 11,
                        FontWeight = FontWeights.SemiBold,
                        TextAlignment = TextAlignment.Center,
                        VerticalAlignment = VerticalAlignment.Center,
                        Foreground = (Brush)FindResource("TextPrimary")
                    };
                    var repsPlusBtn = new Button
                    {
                        Content = "+",
                        Width = 20,
                        Height = 20,
                        Padding = new Thickness(0),
                        FontSize = 11,
                        Style = (Style)FindResource("GoogleOutlineBtnStyle"),
                        Cursor = Cursors.Hand
                    };
                    repsPlusBtn.Click += (s, e) =>
                    {
                        setItem.Reps += 1;
                        SaveRecordSets(rec, sets);
                    };
                    repsStack.Children.Add(repsMinusBtn);
                    repsStack.Children.Add(repsTb);
                    repsStack.Children.Add(repsPlusBtn);
                    Grid.SetColumn(repsStack, 3);
                    rowGrid.Children.Add(repsStack);

                    // 5. 完成 CheckBox
                    var chk = new CheckBox
                    {
                        IsChecked = setItem.IsCompleted,
                        HorizontalAlignment = HorizontalAlignment.Center,
                        VerticalAlignment = VerticalAlignment.Center,
                        Cursor = Cursors.Hand
                    };
                    chk.Click += (s, e) =>
                    {
                        bool wasCompleted = setItem.IsCompleted;
                        setItem.IsCompleted = chk.IsChecked == true;
                        SaveRecordSets(rec, sets);

                        if (!wasCompleted && setItem.IsCompleted)
                        {
                            int restSeconds = setItem.Type == "W" ? 60 : (rec.Category is "CHEST" or "BACK" or "LEGS" ? 90 : 60);
                            StartRestTimer(restSeconds);
                        }
                    };
                    Grid.SetColumn(chk, 4);
                    rowGrid.Children.Add(chk);

                    // 6. 删除单组 (当总组数 > 1 时)
                    if (sets.Count > 1)
                    {
                        var delSetBtn = new Button
                        {
                            Content = "✕",
                            Width = 18,
                            Height = 18,
                            Padding = new Thickness(0),
                            FontSize = 9.5,
                            Style = (Style)FindResource("GoogleTopBtnStyle"),
                            ToolTip = "删除该组",
                            HorizontalAlignment = HorizontalAlignment.Center,
                            VerticalAlignment = VerticalAlignment.Center,
                            Cursor = Cursors.Hand
                        };
                        delSetBtn.Click += (s, e) =>
                        {
                            sets.RemoveAt(setIdx);
                            for (int k = 0; k < sets.Count; k++) sets[k].SetNumber = k + 1;
                            SaveRecordSets(rec, sets);
                        };
                        Grid.SetColumn(delSetBtn, 5);
                        rowGrid.Children.Add(delSetBtn);
                    }

                    cardStack.Children.Add(rowGrid);
                }

                // Bottom Row of Card: "+ 添加一组" & "⏱ 90s休息"
                var cardBottom = new Grid { Margin = new Thickness(0, 6, 0, 0) };
                cardBottom.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
                cardBottom.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                cardBottom.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

                var addSetBtn = new Button
                {
                    Content = "➕ 添加一组",
                    Style = (Style)FindResource("GoogleOutlineBtnStyle"),
                    Padding = new Thickness(8, 3, 8, 3),
                    FontSize = 10.5,
                    FontWeight = FontWeights.SemiBold,
                    Cursor = Cursors.Hand
                };
                addSetBtn.Click += (s, e) =>
                {
                    var lastSet = sets.LastOrDefault();
                    sets.Add(new WorkoutSetItem
                    {
                        SetNumber = sets.Count + 1,
                        Type = "N",
                        WeightKg = lastSet?.WeightKg ?? rec.TargetWeightKg,
                        Reps = lastSet?.Reps ?? rec.TargetReps,
                        IsCompleted = false
                    });
                    SaveRecordSets(rec, sets);
                };
                Grid.SetColumn(addSetBtn, 0);
                cardBottom.Children.Add(addSetBtn);

                var restQuickBtn = new Button
                {
                    Content = "⏱️ 休息90s",
                    Style = (Style)FindResource("GoogleTopBtnStyle"),
                    Padding = new Thickness(8, 3, 8, 3),
                    FontSize = 10.5,
                    Cursor = Cursors.Hand
                };
                restQuickBtn.Click += (s, e) => StartRestTimer(90);
                Grid.SetColumn(restQuickBtn, 2);
                cardBottom.Children.Add(restQuickBtn);

                cardStack.Children.Add(cardBottom);

                card.Child = cardStack;
                FitnessExercisesListPanel.Children.Add(card);
            }
        }

        // 3. 渲染历史打卡日志
        FitnessHistoryListPanel.Children.Clear();
        var allPlans = DatabaseService.GetAllFitnessPlans(20);

        if (allPlans.Count == 0)
        {
            FitnessHistoryListPanel.Children.Add(new TextBlock
            {
                Text = "暂无历史训练打卡记录。坚持锻炼，开启你的第一天！",
                FontSize = 11,
                Foreground = (Brush)FindResource("TextSecondary"),
                Margin = new Thickness(0, 4, 0, 0)
            });
        }
        else
        {
            foreach (var hPlan in allPlans)
            {
                var hCard = new Border
                {
                    Background = (Brush)FindResource("ControlBackground"),
                    BorderBrush = (Brush)FindResource("CardBorderBrush"),
                    BorderThickness = new Thickness(1),
                    CornerRadius = new CornerRadius(8),
                    Padding = new Thickness(12, 10, 12, 10),
                    Margin = new Thickness(0, 0, 0, 8),
                    Cursor = Cursors.Hand
                };

                hCard.MouseLeftButtonUp += (s, e) =>
                {
                    if (DateTime.TryParse(hPlan.PlanDate, out var pDate))
                    {
                        _fitnessSelectedDate = pDate;
                        RenderFitnessPage();
                    }
                };

                var hStack = new StackPanel();

                var hTop = new Grid();
                hTop.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                hTop.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

                var dateTb = new TextBlock
                {
                    Text = $"{hPlan.PlanDate} · {hPlan.Title}",
                    FontSize = 12,
                    FontWeight = FontWeights.SemiBold,
                    Foreground = (Brush)FindResource("TextPrimary")
                };
                Grid.SetColumn(dateTb, 0);
                hTop.Children.Add(dateTb);

                var statusBadge = new TextBlock
                {
                    Text = hPlan.IsCompleted ? "✅ 已打卡" : "⏳ 规划中",
                    FontSize = 11,
                    FontWeight = FontWeights.SemiBold,
                    Foreground = hPlan.IsCompleted ? new SolidColorBrush((Color)ColorConverter.ConvertFromString("#10B981")) : new SolidColorBrush((Color)ColorConverter.ConvertFromString("#F59E0B"))
                };
                Grid.SetColumn(statusBadge, 1);
                hTop.Children.Add(statusBadge);
                hStack.Children.Add(hTop);

                string feelingText = hPlan.FeelingRating switch
                {
                    "EASY" => "😊 轻松",
                    "MODERATE" => "👍 适中",
                    "HARD" => "🔥 爆裂",
                    _ => ""
                };

                int displayMins = hPlan.ActualDurationMinutes > 0 ? hPlan.ActualDurationMinutes : hPlan.TargetDurationMinutes;
                var hMeta = new TextBlock
                {
                    Text = $"⏱️ {displayMins}m · 🔥 {hPlan.CaloriesBurned:0.#} kcal" + (string.IsNullOrEmpty(feelingText) ? "" : $" · {feelingText}"),
                    FontSize = 10.5,
                    Foreground = (Brush)FindResource("TextSecondary"),
                    Margin = new Thickness(0, 3, 0, 0)
                };
                hStack.Children.Add(hMeta);

                if (!string.IsNullOrWhiteSpace(hPlan.Notes))
                {
                    hStack.Children.Add(new TextBlock
                    {
                        Text = hPlan.Notes,
                        FontSize = 10,
                        Foreground = (Brush)FindResource("TextSecondary"),
                        Margin = new Thickness(0, 2, 0, 0),
                        TextTrimming = TextTrimming.CharacterEllipsis
                    });
                }

                hCard.Child = hStack;
                FitnessHistoryListPanel.Children.Add(hCard);
            }
        }
    }

    // ----------------- 日期切换与刷新 -----------------

    private void OnFitnessPrevDayClicked(object sender, RoutedEventArgs e)
    {
        _fitnessSelectedDate = _fitnessSelectedDate.AddDays(-1);
        RenderFitnessPage();
    }

    private void OnFitnessTodayClicked(object sender, RoutedEventArgs e)
    {
        _fitnessSelectedDate = DateTime.Today;
        RenderFitnessPage();
    }

    private void OnFitnessNextDayClicked(object sender, RoutedEventArgs e)
    {
        _fitnessSelectedDate = _fitnessSelectedDate.AddDays(1);
        RenderFitnessPage();
    }

    private void OnRefreshFitnessClicked(object sender, RoutedEventArgs e)
    {
        RenderFitnessPage();
    }

    // ----------------- 计划定制弹窗 -----------------

    private void OnFitnessCreatePlanClicked(object sender, RoutedEventArgs e)
    {
        string dateStr = _fitnessSelectedDate.ToString("yyyy-MM-dd");
        var existing = DatabaseService.GetFitnessPlanByDate(dateStr);

        if (existing != null)
        {
            _editingFitnessPlanId = existing.Id;
            FitnessPlanModalHeader.Text = "编辑今日健身计划";
            FitnessPlanTitleInput.Text = existing.Title;
            FitnessPlanDurationInput.Text = existing.TargetDurationMinutes.ToString();
            FitnessPlanNotesInput.Text = existing.Notes;

            foreach (ComboBoxItem item in FitnessPlanTypeInput.Items)
            {
                if (item.Tag?.ToString() == existing.WorkoutType)
                {
                    FitnessPlanTypeInput.SelectedItem = item;
                    break;
                }
            }
        }
        else
        {
            _editingFitnessPlanId = null;
            FitnessPlanModalHeader.Text = "定制今日健身计划";
            FitnessPlanTitleInput.Text = "力量与体能塑形";
            FitnessPlanDurationInput.Text = "45";
            FitnessPlanNotesInput.Text = "";
            FitnessPlanTypeInput.SelectedIndex = 0;
        }

        FitnessPlanModal.Visibility = Visibility.Visible;
    }

    private void OnCloseFitnessPlanModalClicked(object sender, RoutedEventArgs e)
    {
        FitnessPlanModal.Visibility = Visibility.Collapsed;
    }

    private void OnSaveFitnessPlanClicked(object sender, RoutedEventArgs e)
    {
        string title = FitnessPlanTitleInput.Text.Trim();
        if (string.IsNullOrWhiteSpace(title))
        {
            title = "每日健身训练";
        }

        string typeTag = (FitnessPlanTypeInput.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "STRENGTH";
        if (!int.TryParse(FitnessPlanDurationInput.Text.Trim(), out int duration))
        {
            duration = 45;
        }
        string notes = FitnessPlanNotesInput.Text.Trim();

        string dateStr = _fitnessSelectedDate.ToString("yyyy-MM-dd");
        var existing = DatabaseService.GetFitnessPlanByDate(dateStr);

        var plan = existing ?? new FitnessPlanItem
        {
            Id = _editingFitnessPlanId ?? Guid.NewGuid().ToString(),
            PlanDate = dateStr
        };

        plan.Title = title;
        plan.WorkoutType = typeTag;
        plan.TargetDurationMinutes = duration;
        plan.Notes = notes;
        plan.UpdatedAt = DateTime.UtcNow;

        DatabaseService.UpsertFitnessPlan(plan);
        FitnessPlanModal.Visibility = Visibility.Collapsed;
        RenderFitnessPage();
        SyncFitnessPlanToSchedule(dateStr);
    }

    // ----------------- 动作录入弹窗 -----------------

    private void OnFitnessAddExerciseClicked(object sender, RoutedEventArgs e)
    {
        FitnessExerciseSearchInput.Text = "";
        _fitnessSelectedCategoryFilter = "ALL";
        foreach (var child in FitnessCategoryFilterPanel.Children)
        {
            if (child is Button b)
            {
                b.Style = (b.Tag?.ToString() == "ALL") ? (Style)FindResource("GoogleActionBtnStyle") : (Style)FindResource("GoogleOutlineBtnStyle");
            }
        }
        FitnessExerciseNameInput.Text = "";
        FitnessExerciseSetsInput.Text = "4";
        FitnessExerciseRepsInput.Text = "10";
        FitnessExerciseWeightInput.Text = "20";
        FitnessExerciseNoteInput.Text = "";
        FitnessCustomExerciseExpander.IsExpanded = false;

        RenderExerciseLibraryPicker();
        FitnessExerciseModal.Visibility = Visibility.Visible;
    }

    private void OnCloseFitnessExerciseModalClicked(object sender, RoutedEventArgs e)
    {
        FitnessExerciseModal.Visibility = Visibility.Collapsed;
    }

    private void OnSaveFitnessExerciseClicked(object sender, RoutedEventArgs e)
    {
        string name = FitnessExerciseNameInput.Text.Trim();
        if (string.IsNullOrWhiteSpace(name))
        {
            MessageBox.Show("请输入自定义动作名称", "提示", MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }

        string cat = (FitnessExerciseCategoryInput.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "CHEST";
        string eq = (FitnessExerciseEquipmentInput.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "哑铃";
        int.TryParse(FitnessExerciseSetsInput.Text.Trim(), out int sets);
        if (sets <= 0) sets = 4;
        int.TryParse(FitnessExerciseRepsInput.Text.Trim(), out int reps);
        if (reps <= 0) reps = 10;
        double.TryParse(FitnessExerciseWeightInput.Text.Trim(), out double weight);
        string note = FitnessExerciseNoteInput.Text.Trim();

        var customEx = new ExerciseInfo
        {
            Id = "custom_" + Guid.NewGuid().ToString("N")[..8],
            Name = name,
            NameEn = name,
            Category = cat,
            CategoryName = GetFitnessCategoryDisplayName(cat),
            Equipment = eq,
            DefaultSets = sets,
            DefaultReps = reps,
            DefaultWeightKg = weight,
            Tips = note
        };

        AddExerciseToTodayWorkout(customEx);
        FitnessExerciseModal.Visibility = Visibility.Collapsed;
    }

    // ----------------- 打卡结算弹窗 -----------------

    private void OnFitnessFinishWorkoutClicked(object sender, RoutedEventArgs e)
    {
        string dateStr = _fitnessSelectedDate.ToString("yyyy-MM-dd");
        var plan = DatabaseService.GetFitnessPlanByDate(dateStr);
        var records = DatabaseService.GetFitnessRecordsByDate(dateStr);

        int totalMins = plan?.TargetDurationMinutes ?? 45;
        double totalCalories = records.Sum(r => r.CaloriesBurned);
        if (totalCalories <= 0) totalCalories = 300;

        if (plan != null && plan.ActualDurationMinutes > 0)
        {
            totalMins = plan.ActualDurationMinutes;
        }
        if (plan != null && plan.CaloriesBurned > 0)
        {
            totalCalories = plan.CaloriesBurned;
        }

        FitnessFinishActualMinsInput.Text = totalMins.ToString();
        FitnessFinishCaloriesInput.Text = totalCalories.ToString("0.#");
        FitnessFinishNotesInput.Text = plan?.Notes ?? "";

        FitnessFinishModal.Visibility = Visibility.Visible;
    }

    private void OnCloseFitnessFinishModalClicked(object sender, RoutedEventArgs e)
    {
        FitnessFinishModal.Visibility = Visibility.Collapsed;
    }

    private void OnSubmitFitnessFinishClicked(object sender, RoutedEventArgs e)
    {
        string dateStr = _fitnessSelectedDate.ToString("yyyy-MM-dd");
        var plan = DatabaseService.GetFitnessPlanByDate(dateStr);

        if (plan == null)
        {
            plan = new FitnessPlanItem
            {
                Id = Guid.NewGuid().ToString(),
                PlanDate = dateStr,
                Title = "今日训练"
            };
        }

        int.TryParse(FitnessFinishActualMinsInput.Text.Trim(), out int actualMins);
        if (actualMins <= 0) actualMins = 45;

        double.TryParse(FitnessFinishCaloriesInput.Text.Trim(), out double calories);
        string feeling = (FitnessFinishFeelingInput.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "MODERATE";
        string notes = FitnessFinishNotesInput.Text.Trim();

        plan.IsCompleted = true;
        plan.ActualDurationMinutes = actualMins;
        plan.CaloriesBurned = calories;
        plan.FeelingRating = feeling;
        if (!string.IsNullOrWhiteSpace(notes))
        {
            plan.Notes = notes;
        }
        plan.UpdatedAt = DateTime.UtcNow;

        DatabaseService.UpsertFitnessPlan(plan);

        // 自动将当天未勾选动作的所有组置为已完成
        var records = DatabaseService.GetFitnessRecordsByDate(dateStr);
        foreach (var r in records)
        {
            var sets = r.GetWorkoutSets();
            if (sets.Any(s => !s.IsCompleted))
            {
                foreach (var s in sets) s.IsCompleted = true;
                r.SetsData = WorkoutSetItem.ToJsonArray(sets);
                r.IsCompleted = true;
                DatabaseService.UpsertFitnessRecord(r);
            }
        }

        FitnessFinishModal.Visibility = Visibility.Collapsed;
        RenderFitnessPage();
        SyncFitnessPlanToSchedule(dateStr);

        MessageBox.Show($"🎉 恭喜！已完成 {plan.Title} 打卡！\n连续运动打卡天数已更新，继续保持活力状态！", "打卡成功", MessageBoxButton.OK, MessageBoxImage.Information);
    }

    // ----------------- 经典训练模版 -----------------

    private void OnFitnessTemplateMenuClicked(object sender, RoutedEventArgs e)
    {
        FitnessTemplateModal.Visibility = Visibility.Visible;
    }

    private void OnCloseFitnessTemplateModalClicked(object sender, RoutedEventArgs e)
    {
        FitnessTemplateModal.Visibility = Visibility.Collapsed;
    }

    private void ApplyWorkoutTemplate(WorkoutTemplate tpl)
    {
        string dateStr = _fitnessSelectedDate.ToString("yyyy-MM-dd");
        var plan = DatabaseService.GetFitnessPlanByDate(dateStr) ?? new FitnessPlanItem
        {
            Id = Guid.NewGuid().ToString(),
            PlanDate = dateStr
        };

        plan.Title = tpl.Name;
        plan.WorkoutType = tpl.WorkoutType;
        plan.TargetDurationMinutes = tpl.EstimatedMinutes;
        plan.UpdatedAt = DateTime.UtcNow;
        DatabaseService.UpsertFitnessPlan(plan);

        int order = 0;
        foreach (var ex in tpl.Exercises)
        {
            var rec = new FitnessRecordItem
            {
                Id = Guid.NewGuid().ToString(),
                PlanId = plan.Id,
                RecordDate = dateStr,
                ExerciseName = ex.Name,
                Category = ex.Category,
                Equipment = ex.Equipment,
                SetsData = WorkoutSetItem.ToJsonArray(ex.Sets),
                TargetSets = ex.Sets.Count,
                TargetReps = ex.Sets.FirstOrDefault()?.Reps ?? 10,
                TargetWeightKg = ex.Sets.Count > 0 ? ex.Sets.Max(s => s.WeightKg) : 0,
                CaloriesBurned = 70,
                SortOrder = order++
            };
            DatabaseService.UpsertFitnessRecord(rec);
        }

        FitnessTemplateModal.Visibility = Visibility.Collapsed;
        RenderFitnessPage();
        SyncFitnessPlanToSchedule(dateStr);

        MessageBox.Show($"⚡ 已成功导入「{tpl.Name}」模版！包含 {tpl.Exercises.Count} 个动作及专业组数负重配置！", "模版导入成功", MessageBoxButton.OK, MessageBoxImage.Information);
    }

    private void OnApplyTemplatePushDayClicked(object sender, MouseButtonEventArgs e)
    {
        var tpl = ExerciseLibrary.Templates.FirstOrDefault(t => t.Id == "ppl_push") ?? ExerciseLibrary.Templates[0];
        ApplyWorkoutTemplate(tpl);
    }

    private void OnApplyTemplatePullDayClicked(object sender, MouseButtonEventArgs e)
    {
        var tpl = ExerciseLibrary.Templates.FirstOrDefault(t => t.Id == "ppl_pull") ?? ExerciseLibrary.Templates[1];
        ApplyWorkoutTemplate(tpl);
    }

    private void OnApplyTemplateLegsCoreClicked(object sender, MouseButtonEventArgs e)
    {
        var tpl = ExerciseLibrary.Templates.FirstOrDefault(t => t.Id == "ppl_legs") ?? ExerciseLibrary.Templates[2];
        ApplyWorkoutTemplate(tpl);
    }

    private void OnApplyTemplateUpperBodyClicked(object sender, MouseButtonEventArgs e)
    {
        var tpl = ExerciseLibrary.Templates.FirstOrDefault(t => t.Id == "upper_body") ?? ExerciseLibrary.Templates[3];
        ApplyWorkoutTemplate(tpl);
    }

    private void OnApplyTemplateCardioClicked(object sender, MouseButtonEventArgs e)
    {
        var tpl = ExerciseLibrary.Templates.FirstOrDefault(t => t.Id == "cardio_hiit") ?? ExerciseLibrary.Templates[4];
        ApplyWorkoutTemplate(tpl);
    }

    // ----------------- 健身与日程双向同步 -----------------

    private void SyncFitnessPlanToSchedule(string dateStr, bool showToast = false)
    {
        var plan = DatabaseService.GetFitnessPlanByDate(dateStr);
        var records = DatabaseService.GetFitnessRecordsByDate(dateStr);
        string scheduleId = $"fitness_schedule_{dateStr}";

        if (plan == null && (records == null || records.Count == 0))
        {
            DatabaseService.DeleteSchedule(scheduleId);
            RenderAllCalendarViews();
            return;
        }

        string planTitle = plan?.Title ?? "每日专业体能训练";
        int durationMins = plan?.TargetDurationMinutes ?? 45;
        double totalCalories = records?.Sum(r => r.CaloriesBurned) ?? 0;
        if (totalCalories <= 0 && plan != null) totalCalories = plan.CaloriesBurned;

        DateTime targetDate;
        if (!DateTime.TryParse(dateStr, out targetDate)) targetDate = DateTime.Today;

        var existing = DatabaseService.GetScheduleById(scheduleId);
        DateTime startTime;
        if (existing != null)
        {
            startTime = existing.StartTime;
        }
        else
        {
            var now = DateTime.Now;
            if (targetDate.Date == DateTime.Today && now.Hour >= 18)
            {
                startTime = now.AddMinutes(5);
            }
            else
            {
                startTime = targetDate.Date.AddHours(18).AddMinutes(30);
            }
        }
        DateTime endTime = startTime.AddMinutes(durationMins);

        var sbDesc = new System.Text.StringBuilder();
        sbDesc.AppendLine($"🏋️ 健身主题: {planTitle} · 预计用时: {durationMins}m");
        if (totalCalories > 0)
        {
            sbDesc.AppendLine($"🔥 预估消耗: ~{(int)totalCalories} kcal");
        }
        if (records != null && records.Count > 0)
        {
            sbDesc.AppendLine($"📋 训练动作清单 ({records.Count}项):");
            foreach (var r in records)
            {
                var sets = r.GetWorkoutSets();
                int completedSets = sets.Count(s => s.IsCompleted);
                string setInfo = sets.Count > 0
                    ? (completedSets > 0 ? $"{completedSets}/{sets.Count}组完成" : $"{sets.Count}组")
                    : $"{r.SetsCount}组";
                string doneTag = (r.IsCompleted || (sets.Count > 0 && completedSets == sets.Count)) ? "✓" : "·";
                sbDesc.AppendLine($" {doneTag} {r.ExerciseName} ({setInfo})");
            }
        }
        if (!string.IsNullOrWhiteSpace(plan?.Notes))
        {
            sbDesc.AppendLine($"💡 要点: {plan.Notes}");
        }

        bool isCompleted = (plan?.IsCompleted == true) || (records != null && records.Count > 0 && records.All(r => r.IsCompleted));
        int actualMins = (plan?.ActualDurationMinutes > 0) ? plan.ActualDurationMinutes : (isCompleted ? durationMins : 0);

        var sched = existing ?? new ScheduleItem
        {
            Id = scheduleId
        };

        sched.Title = $"🏋️ [健身] {planTitle}";
        sched.Description = sbDesc.ToString().TrimEnd();
        sched.Category = "HEALTH";
        sched.WorkType = "REST_BUFFER";
        sched.Priority = "MEDIUM";
        sched.Status = isCompleted ? "COMPLETED" : "PENDING";
        sched.StartTime = startTime;
        sched.EndTime = endTime;
        sched.EstimatedMinutes = durationMins;
        sched.ActualMinutes = actualMins;
        sched.IsDeleted = false;

        DatabaseService.UpsertSchedule(sched);
        RenderAllCalendarViews();

        if (showToast)
        {
            MessageBox.Show($"🎉 已成功将健身训练「{planTitle}」同步至主页日程！\n计划时段：{startTime:HH:mm} - {endTime:HH:mm}，将在 24 小时时钟图与日程清单中高亮呈现。", "同步成功", MessageBoxButton.OK, MessageBoxImage.Information);
        }
    }

    private void OnFitnessProjectToScheduleClicked(object sender, RoutedEventArgs e)
    {
        string dateStr = _fitnessSelectedDate.ToString("yyyy-MM-dd");
        SyncFitnessPlanToSchedule(dateStr, showToast: true);
    }

    // =========================================================================
    // =========================================================================
    // ================== TIMETABLE (WAKEUP 课程表) LOGIC ======================
    // =========================================================================

    public class SectionTimeSlot
    {
        public int Section { get; set; }
        public string Start { get; set; } = "08:00";
        public string End { get; set; } = "08:45";
        public string Label { get; set; } = "";
    }

    private int _selectedTimetableWeek = 1;
    private int _currentActualWeek = 1;
    private string? _editingCourseId = null;
    private string _selectedCourseColor = "#3B82F6";
    private bool _isTimetableControlsInitialized = false;

    private readonly (string Hex, string Name)[] _courseColorPalette = new[]
    {
        ("#3B82F6", "经典海蓝"),
        ("#10B981", "薄荷青草"),
        ("#EC4899", "甜桃柔粉"),
        ("#8B5CF6", "薰衣草紫"),
        ("#F59E0B", "蜜糖暖橙"),
        ("#06B6D4", "晴空湖蓝"),
        ("#E11D48", "覆盆子红"),
        ("#14B8A6", "碧潭青碧")
    };

    private List<SectionTimeSlot> GetConfiguredSectionTimes(int totalSections)
    {
        string json = DatabaseService.GetTimetableSetting("section_schedule_json", "");
        if (!string.IsNullOrWhiteSpace(json))
        {
            try
            {
                var list = JsonSerializer.Deserialize<List<SectionTimeSlot>>(json);
                if (list != null && list.Count > 0)
                {
                    while (list.Count < totalSections)
                    {
                        int sec = list.Count + 1;
                        list.Add(new SectionTimeSlot { Section = sec, Start = $"{8 + sec:D2}:00", End = $"{8 + sec:D2}:45", Label = sec <= 4 ? "上午" : (sec <= 8 ? "下午" : "晚上") });
                    }
                    return list.Take(totalSections).ToList();
                }
            }
            catch { }
        }

        int classDur = int.TryParse(DatabaseService.GetTimetableSetting("class_duration", "45"), out int cd) ? cd : 45;
        int breakDur = int.TryParse(DatabaseService.GetTimetableSetting("break_duration", "10"), out int bd) ? bd : 10;
        string mStart = DatabaseService.GetTimetableSetting("morning_start", "08:00");
        string aStart = DatabaseService.GetTimetableSetting("afternoon_start", "14:00");
        string eStart = DatabaseService.GetTimetableSetting("evening_start", "18:30");

        return GenerateDefaultSectionTimes(totalSections, classDur, breakDur, mStart, aStart, eStart);
    }

    private List<SectionTimeSlot> GenerateDefaultSectionTimes(int totalSections, int classDuration, int breakDuration, string morningStart, string afternoonStart, string eveningStart)
    {
        var list = new List<SectionTimeSlot>();

        TimeSpan parseTime(string t, TimeSpan def) => TimeSpan.TryParse(t, out var val) ? val : def;
        var mTime = parseTime(morningStart, new TimeSpan(8, 0, 0));
        var aTime = parseTime(afternoonStart, new TimeSpan(14, 0, 0));
        var eTime = parseTime(eveningStart, new TimeSpan(18, 30, 0));

        TimeSpan curTime = mTime;
        for (int sec = 1; sec <= totalSections; sec++)
        {
            if (sec == 5) curTime = aTime;
            else if (sec == 9) curTime = eTime;

            var start = curTime;
            var end = curTime.Add(TimeSpan.FromMinutes(classDuration));
            curTime = end.Add(TimeSpan.FromMinutes(breakDuration));

            string label = sec <= 4 ? "上午" : (sec <= 8 ? "下午" : "晚上");
            list.Add(new SectionTimeSlot
            {
                Section = sec,
                Start = $"{start.Hours:D2}:{start.Minutes:D2}",
                End = $"{end.Hours:D2}:{end.Minutes:D2}",
                Label = label
            });
        }
        return list;
    }

    private void InitTimetableControls()
    {
        if (_isTimetableControlsInitialized) return;
        _isTimetableControlsInitialized = true;

        int totalWeeks = int.TryParse(DatabaseService.GetTimetableSetting("total_weeks", "20"), out int tw) ? tw : 20;

        // 1. 初始化周数下拉框 (1 ~ 35 周)
        CourseStartWeekCombo.Items.Clear();
        CourseEndWeekCombo.Items.Clear();
        for (int w = 1; w <= 35; w++)
        {
            CourseStartWeekCombo.Items.Add(new ComboBoxItem { Content = $"第 {w} 周", Tag = w });
            CourseEndWeekCombo.Items.Add(new ComboBoxItem { Content = $"第 {w} 周", Tag = w });
        }
        CourseStartWeekCombo.SelectedIndex = 0; // 第 1 周
        CourseEndWeekCombo.SelectedIndex = Math.Clamp(totalWeeks - 1, 0, 34);

        // 2. 初始化色卡选择器
        CourseColorPickerPanel.Children.Clear();
        foreach (var (hex, name) in _courseColorPalette)
        {
            var colorBtn = new Border
            {
                Width = 26,
                Height = 26,
                CornerRadius = new CornerRadius(13),
                Margin = new Thickness(0, 0, 8, 4),
                Cursor = Cursors.Hand,
                ToolTip = name,
                Tag = hex
            };
            try
            {
                colorBtn.Background = (SolidColorBrush)new BrushConverter().ConvertFromString(hex)!;
            }
            catch
            {
                colorBtn.Background = Brushes.SteelBlue;
            }

            colorBtn.MouseLeftButtonDown += (s, e) =>
            {
                _selectedCourseColor = hex;
                UpdateCourseColorPickerSelection();
            };

            CourseColorPickerPanel.Children.Add(colorBtn);
        }

        // 自定义颜色按钮 (打开 RGB 调色盘)
        var customColorBtn = new Border
        {
            Height = 26,
            CornerRadius = new CornerRadius(13),
            Padding = new Thickness(10, 0, 10, 0),
            Margin = new Thickness(4, 0, 8, 4),
            Cursor = Cursors.Hand,
            Background = (Brush)FindResource("ControlBackground"),
            BorderBrush = (Brush)FindResource("CardBorderBrush"),
            BorderThickness = new Thickness(1),
            ToolTip = "自定义调色盘",
            Tag = "CUSTOM_COLOR_TRIGGER"
        };
        var customBtnStack = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };
        var customColorIndicator = new Border
        {
            Name = "CourseCustomColorIndicator",
            Width = 12,
            Height = 12,
            CornerRadius = new CornerRadius(6),
            Margin = new Thickness(0, 0, 6, 0),
            Background = Brushes.SteelBlue,
            VerticalAlignment = VerticalAlignment.Center
        };
        var customColorText = new TextBlock
        {
            Text = "🎨 自定义",
            FontSize = 11,
            Foreground = (Brush)FindResource("TextSecondary"),
            VerticalAlignment = VerticalAlignment.Center
        };
        customBtnStack.Children.Add(customColorIndicator);
        customBtnStack.Children.Add(customColorText);
        customColorBtn.Child = customBtnStack;

        customColorBtn.MouseLeftButtonDown += (s, e) =>
        {
            OnOpenCourseCustomColorPickerClicked(s, e);
        };

        CourseColorPickerPanel.Children.Add(customColorBtn);
        UpdateCourseColorPickerSelection();

        // 3. 计算并设置当前实际周
        CalculateCurrentActualWeek();
        _selectedTimetableWeek = _currentActualWeek;
    }

    private void UpdateCourseColorPickerSelection()
    {
        bool isPaletteMatched = false;
        Border? customTriggerBorder = null;

        foreach (var child in CourseColorPickerPanel.Children)
        {
            if (child is Border border && border.Tag is string hex)
            {
                if (hex == "CUSTOM_COLOR_TRIGGER")
                {
                    customTriggerBorder = border;
                    continue;
                }

                if (string.Equals(hex, _selectedCourseColor, StringComparison.OrdinalIgnoreCase))
                {
                    isPaletteMatched = true;
                    border.BorderBrush = Brushes.White;
                    border.BorderThickness = new Thickness(2.5);
                    border.Effect = new System.Windows.Media.Effects.DropShadowEffect
                    {
                        BlurRadius = 8,
                        ShadowDepth = 0,
                        Color = Colors.White,
                        Opacity = 0.8
                    };
                }
                else
                {
                    border.BorderBrush = Brushes.Transparent;
                    border.BorderThickness = new Thickness(1);
                    border.Effect = null;
                }
            }
        }

        if (customTriggerBorder != null)
        {
            if (customTriggerBorder.Child is StackPanel sp && sp.Children.Count > 0 && sp.Children[0] is Border ind)
            {
                try
                {
                    ind.Background = (SolidColorBrush)new BrushConverter().ConvertFromString(_selectedCourseColor)!;
                }
                catch
                {
                    ind.Background = Brushes.SteelBlue;
                }
            }

            if (!isPaletteMatched)
            {
                customTriggerBorder.BorderBrush = (Brush)FindResource("AccentBlue");
                customTriggerBorder.BorderThickness = new Thickness(2);
            }
            else
            {
                customTriggerBorder.BorderBrush = (Brush)FindResource("CardBorderBrush");
                customTriggerBorder.BorderThickness = new Thickness(1);
            }
        }
    }

    private void CalculateCurrentActualWeek()
    {
        string startDateStr = DatabaseService.GetTimetableSetting("semester_start_date", "2026-09-07");
        int totalWeeks = int.TryParse(DatabaseService.GetTimetableSetting("total_weeks", "20"), out int tw) ? tw : 20;

        if (DateTime.TryParse(startDateStr, out DateTime startDate))
        {
            int diffToMonday = ((int)startDate.DayOfWeek + 6) % 7;
            startDate = startDate.AddDays(-diffToMonday);

            double daysPassed = (DateTime.Today - startDate).TotalDays;
            int week = (int)Math.Floor(daysPassed / 7.0) + 1;
            _currentActualWeek = Math.Clamp(week, 1, totalWeeks);
        }
        else
        {
            _currentActualWeek = 1;
        }
    }

    public void RenderTimetablePage()
    {
        InitTimetableControls();
        CalculateCurrentActualWeek();

        string semesterName = DatabaseService.GetTimetableSetting("semester_name", "2026年秋季学期");
        string startDateStr = DatabaseService.GetTimetableSetting("semester_start_date", "2026-09-07");
        int totalWeeks = int.TryParse(DatabaseService.GetTimetableSetting("total_weeks", "20"), out int tw) ? tw : 20;
        int totalSections = int.TryParse(DatabaseService.GetTimetableSetting("total_sections", "12"), out int ts) ? ts : 12;
        bool showWeekend = DatabaseService.GetTimetableSetting("show_weekend", "true") != "false";

        if (_selectedTimetableWeek < 1) _selectedTimetableWeek = 1;
        if (_selectedTimetableWeek > totalWeeks) _selectedTimetableWeek = totalWeeks;

        // 获取用户自定义的各节作息起止时间
        var sectionTimes = GetConfiguredSectionTimes(totalSections);

        // 1. 顶部 Header 更新
        TimetableCurrentWeekTitle.Text = $"第 {_selectedTimetableWeek} 周";
        TimetableSemesterSubtitle.Text = $"{semesterName} · 全 {totalWeeks} 周";
        TimetableIsCurrentWeekBadge.Visibility = (_selectedTimetableWeek == _currentActualWeek) ? Visibility.Visible : Visibility.Collapsed;

        // 计算当前选中周的周一公历日期
        DateTime baseMonday = DateTime.Today;
        if (DateTime.TryParse(startDateStr, out DateTime sDate))
        {
            int diffToMonday = ((int)sDate.DayOfWeek + 6) % 7;
            sDate = sDate.AddDays(-diffToMonday);
            baseMonday = sDate.AddDays((_selectedTimetableWeek - 1) * 7);
        }
        DateTime baseSunday = baseMonday.AddDays(6);
        TimetableWeekDateRangeText.Text = $"{baseMonday:yyyy年M月d日} - {baseSunday:M月d日}";

        // 2. 渲染 Row 1: 横向 1~totalWeeks 周数胶囊条
        TimetableWeekCapsulesPanel.Children.Clear();
        for (int w = 1; w <= totalWeeks; w++)
        {
            int targetWeek = w;
            bool isSelected = (w == _selectedTimetableWeek);
            bool isCurrent = (w == _currentActualWeek);

            var capsule = new Button
            {
                Content = isCurrent && !isSelected ? $"第{w}周•" : $"第{w}周",
                Padding = new Thickness(10, 4, 10, 4),
                Margin = new Thickness(0, 0, 6, 0),
                FontSize = 11,
                Cursor = Cursors.Hand,
                FontWeight = (isSelected || isCurrent) ? FontWeights.SemiBold : FontWeights.Normal
            };

            var style = new Style(typeof(Button));
            var template = new ControlTemplate(typeof(Button));
            var borderFactory = new FrameworkElementFactory(typeof(Border));
            borderFactory.SetValue(Border.CornerRadiusProperty, new CornerRadius(12));
            borderFactory.SetValue(Border.PaddingProperty, new Thickness(10, 3, 10, 3));

            if (isSelected)
            {
                borderFactory.SetValue(Border.BackgroundProperty, new SolidColorBrush(Color.FromRgb(0x1A, 0x73, 0xE8)));
                borderFactory.SetValue(Border.BorderBrushProperty, new SolidColorBrush(Color.FromRgb(0x3B, 0x82, 0xF6)));
                borderFactory.SetValue(Border.BorderThicknessProperty, new Thickness(1));
                capsule.Foreground = Brushes.White;
            }
            else if (isCurrent)
            {
                borderFactory.SetValue(Border.BackgroundProperty, new SolidColorBrush(Color.FromRgb(0x06, 0x4E, 0x3B)));
                borderFactory.SetValue(Border.BorderBrushProperty, new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81)));
                borderFactory.SetValue(Border.BorderThicknessProperty, new Thickness(1));
                capsule.Foreground = new SolidColorBrush(Color.FromRgb(0x34, 0xD3, 0x99));
            }
            else
            {
                borderFactory.SetValue(Border.BackgroundProperty, ThemeService.CurrentControlBrush);
                borderFactory.SetValue(Border.BorderBrushProperty, ThemeService.CurrentBorderBrush);
                borderFactory.SetValue(Border.BorderThicknessProperty, new Thickness(1));
                capsule.Foreground = ThemeService.CurrentSecondaryTextBrush;
            }

            var presenterFactory = new FrameworkElementFactory(typeof(ContentPresenter));
            presenterFactory.SetValue(ContentPresenter.HorizontalAlignmentProperty, HorizontalAlignment.Center);
            presenterFactory.SetValue(ContentPresenter.VerticalAlignmentProperty, VerticalAlignment.Center);
            borderFactory.AppendChild(presenterFactory);
            template.VisualTree = borderFactory;
            style.Setters.Add(new Setter(Button.TemplateProperty, template));
            capsule.Style = style;

            capsule.Click += (s, e) =>
            {
                _selectedTimetableWeek = targetWeek;
                RenderTimetablePage();
            };

            TimetableWeekCapsulesPanel.Children.Add(capsule);
        }

        // 3. 渲染 Days of Week Header 列网格 (Column 0 = 月份, Columns 1~N = 星期与日期)
        int daysCount = showWeekend ? 7 : 5;
        string[] dayNames = new[] { "周一", "周二", "周三", "周四", "周五", "周六", "周日" };

        TimetableHeaderColumnsGrid.ColumnDefinitions.Clear();
        TimetableHeaderColumnsGrid.Children.Clear();

        // Column 0: 月份
        TimetableHeaderColumnsGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(48) });
        var monthHeader = new Border
        {
            BorderBrush = ThemeService.CurrentBorderBrush,
            BorderThickness = new Thickness(0, 0, 1, 0)
        };
        var monthText = new TextBlock
        {
            Text = $"{baseMonday.Month}\n月",
            FontSize = 11,
            FontWeight = FontWeights.Bold,
            Foreground = ThemeService.CurrentSecondaryTextBrush,
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center,
            TextAlignment = TextAlignment.Center,
            LineHeight = 14
        };
        monthHeader.Child = monthText;
        Grid.SetColumn(monthHeader, 0);
        TimetableHeaderColumnsGrid.Children.Add(monthHeader);

        // Columns 1 ~ daysCount
        for (int d = 0; d < daysCount; d++)
        {
            TimetableHeaderColumnsGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            DateTime dayDate = baseMonday.AddDays(d);
            bool isToday = (dayDate.Date == DateTime.Today);

            var dayHeader = new Border
            {
                BorderBrush = ThemeService.CurrentBorderBrush,
                BorderThickness = new Thickness(0, 0, d == daysCount - 1 ? 0 : 1, 0),
                Background = isToday ? new SolidColorBrush(Color.FromArgb(0x22, 0x1A, 0x73, 0xE8)) : Brushes.Transparent
            };

            var dayStack = new StackPanel
            {
                VerticalAlignment = VerticalAlignment.Center,
                HorizontalAlignment = HorizontalAlignment.Center
            };

            var dayTitle = new TextBlock
            {
                Text = dayNames[d],
                FontSize = 12,
                FontWeight = isToday ? FontWeights.Bold : FontWeights.Medium,
                Foreground = isToday ? ThemeService.CurrentAccentBrush : ThemeService.CurrentTextBrush,
                HorizontalAlignment = HorizontalAlignment.Center
            };

            var dateTitle = new TextBlock
            {
                Text = $"{dayDate.Month}/{dayDate.Day}",
                FontSize = 10.5,
                Foreground = isToday ? ThemeService.CurrentAccentBrush : ThemeService.CurrentSecondaryTextBrush,
                HorizontalAlignment = HorizontalAlignment.Center,
                Margin = new Thickness(0, 2, 0, 0)
            };

            dayStack.Children.Add(dayTitle);
            dayStack.Children.Add(dateTitle);
            dayHeader.Child = dayStack;

            Grid.SetColumn(dayHeader, d + 1);
            TimetableHeaderColumnsGrid.Children.Add(dayHeader);
        }

        // 4. 渲染 TimetableMatrixGrid (totalSections 节课 Matrix 网格与课程卡片)
        TimetableMatrixGrid.ColumnDefinitions.Clear();
        TimetableMatrixGrid.RowDefinitions.Clear();
        TimetableMatrixGrid.Children.Clear();

        // 建立列定义
        TimetableMatrixGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(48) });
        for (int d = 0; d < daysCount; d++)
        {
            TimetableMatrixGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        }

        // 建立行定义 (根据 totalSections 自适应，每节高度 68px)
        const double sectionRowHeight = 68.0;
        for (int r = 0; r < totalSections; r++)
        {
            TimetableMatrixGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(sectionRowHeight) });
        }

        // 渲染左侧节次时间列 (Column 0, Rows 0 ~ totalSections - 1)
        for (int r = 0; r < totalSections; r++)
        {
            var timeCell = new Border
            {
                BorderBrush = ThemeService.CurrentBorderBrush,
                BorderThickness = new Thickness(0, 0, 1, 1),
                Background = ThemeService.CurrentControlBrush
            };

            if (r == 3 || r == 7)
            {
                timeCell.BorderThickness = new Thickness(0, 0, 1, 2);
                timeCell.BorderBrush = ThemeService.CurrentBorderBrush;
            }

            var timeStack = new StackPanel
            {
                VerticalAlignment = VerticalAlignment.Center,
                HorizontalAlignment = HorizontalAlignment.Center
            };

            var secNum = new TextBlock
            {
                Text = (r + 1).ToString(),
                FontSize = 13,
                FontWeight = FontWeights.Bold,
                Foreground = ThemeService.CurrentTextBrush,
                HorizontalAlignment = HorizontalAlignment.Center
            };

            var slot = r < sectionTimes.Count ? sectionTimes[r] : new SectionTimeSlot { Start = $"{8 + r:D2}:00", End = $"{8 + r:D2}:45" };
            var timeSpanText = new TextBlock
            {
                Text = slot.Start,
                FontSize = 9.5,
                Foreground = ThemeService.CurrentSecondaryTextBrush,
                HorizontalAlignment = HorizontalAlignment.Center,
                Margin = new Thickness(0, 2, 0, 0)
            };

            var timeEndText = new TextBlock
            {
                Text = slot.End,
                FontSize = 9,
                Foreground = ThemeService.CurrentMutedTextBrush,
                HorizontalAlignment = HorizontalAlignment.Center
            };

            timeStack.Children.Add(secNum);
            timeStack.Children.Add(timeSpanText);
            timeStack.Children.Add(timeEndText);
            timeCell.Child = timeStack;

            Grid.SetColumn(timeCell, 0);
            Grid.SetRow(timeCell, r);
            TimetableMatrixGrid.Children.Add(timeCell);
        }

        // 渲染空白网格槽位 (点击可快速在对应星期与节次建课)
        for (int d = 0; d < daysCount; d++)
        {
            int colDay = d + 1; // 1 = 周一, ..., 7 = 周日
            for (int r = 0; r < totalSections; r++)
            {
                int rowSec = r + 1; // 1 ~ totalSections 节

                var slotCell = new Border
                {
                    Background = Brushes.Transparent,
                    BorderBrush = new SolidColorBrush(Color.FromArgb(0x28, 0x5F, 0x63, 0x68)),
                    BorderThickness = new Thickness(0, 0, 1, 1),
                    Cursor = Cursors.Hand,
                    ToolTip = $"点击在「{dayNames[d]} 第{rowSec}节」快速创建课程"
                };

                if (r == 3 || r == 7)
                {
                    slotCell.BorderThickness = new Thickness(0, 0, 1, 2);
                    slotCell.BorderBrush = new SolidColorBrush(Color.FromRgb(0x47, 0x4B, 0x50));
                }

                slotCell.MouseLeftButtonDown += (s, e) =>
                {
                    OpenCourseCreateModalForSlot(colDay, rowSec);
                };

                Grid.SetColumn(slotCell, colDay);
                Grid.SetRow(slotCell, r);
                TimetableMatrixGrid.Children.Add(slotCell);
            }
        }

        // 5. 加载本周的调停课规则
        var allAdjustments = DatabaseService.GetAllCourseAdjustments();
        var weekAdjustments = allAdjustments.Where(a => a.TargetWeek == _selectedTimetableWeek).ToList();

        // 6. 载入并绘制常规课程卡片
        var courses = DatabaseService.GetAllCourses();
        foreach (var course in courses)
        {
            if (course.DayOfWeek < 1 || course.DayOfWeek > daysCount) continue;

            int startRow = Math.Clamp(course.StartSection - 1, 0, totalSections - 1);
            int span = Math.Clamp(course.SectionSpan, 1, totalSections - startRow);

            bool isActiveInWeek = course.IsActiveInWeek(_selectedTimetableWeek);

            // 检查当前周针对此课是否存在调停课规则
            var suspendAdj = weekAdjustments.FirstOrDefault(a => a.CourseId == course.Id && a.AdjustmentType == "SUSPEND");
            var rescheduleAdj = weekAdjustments.FirstOrDefault(a => a.CourseId == course.Id && a.AdjustmentType == "RESCHEDULE");

            // 解析颜色
            SolidColorBrush bgBrush;
            try
            {
                bgBrush = (SolidColorBrush)new BrushConverter().ConvertFromString(course.ColorHex ?? "#3B82F6")!;
            }
            catch
            {
                bgBrush = new SolidColorBrush(Color.FromRgb(0x3B, 0x82, 0xF6));
            }

            var card = new Border
            {
                CornerRadius = new CornerRadius(8),
                Margin = new Thickness(2),
                Padding = new Thickness(7, 6, 7, 6),
                Cursor = Cursors.Hand
            };

            if (suspendAdj != null)
            {
                // 本周停课卡片样式
                card.Background = new SolidColorBrush(Color.FromArgb(0x33, 0xEF, 0x44, 0x44));
                card.BorderBrush = new SolidColorBrush(Color.FromRgb(0xEF, 0x44, 0x44));
                card.BorderThickness = new Thickness(1.5);
                card.Opacity = 0.8;
            }
            else if (rescheduleAdj != null)
            {
                // 本周已调课原槽位样式
                card.Background = new SolidColorBrush(Color.FromArgb(0x30, 0xF5, 0x9E, 0x0B));
                card.BorderBrush = new SolidColorBrush(Color.FromRgb(0xF5, 0x9E, 0x0B));
                card.BorderThickness = new Thickness(1);
                card.Opacity = 0.5;
            }
            else if (isActiveInWeek)
            {
                card.Background = bgBrush;
                card.Effect = new System.Windows.Media.Effects.DropShadowEffect
                {
                    BlurRadius = 8,
                    ShadowDepth = 2,
                    Color = Colors.Black,
                    Opacity = 0.35
                };
            }
            else
            {
                card.Background = new SolidColorBrush(Color.FromArgb(0x40, bgBrush.Color.R, bgBrush.Color.G, bgBrush.Color.B));
                card.BorderBrush = new SolidColorBrush(Color.FromArgb(0x60, bgBrush.Color.R, bgBrush.Color.G, bgBrush.Color.B));
                card.BorderThickness = new Thickness(1);
                card.Opacity = 0.65;
            }

            var cardStack = new StackPanel();

            string namePrefix = "";
            if (suspendAdj != null) namePrefix = "[⏸️ 本周停课] ";
            else if (rescheduleAdj != null) namePrefix = "[🔀 已调课] ";
            else if (!isActiveInWeek) namePrefix = "[非本周] ";

            var nameBlock = new TextBlock
            {
                Text = namePrefix + course.Name,
                FontWeight = FontWeights.Bold,
                FontSize = 11.5,
                Foreground = Brushes.White,
                TextWrapping = TextWrapping.Wrap,
                TextTrimming = TextTrimming.CharacterEllipsis,
                LineHeight = 15,
                Margin = new Thickness(0, 0, 0, 2)
            };
            cardStack.Children.Add(nameBlock);

            if (suspendAdj != null)
            {
                var reasonBlock = new TextBlock
                {
                    Text = $"⚠️ 停课: {(string.IsNullOrEmpty(suspendAdj.Reason) ? "正常调休" : suspendAdj.Reason)}",
                    FontSize = 9.5,
                    Foreground = new SolidColorBrush(Color.FromRgb(0xFC, 0xA5, 0xA5)),
                    TextTrimming = TextTrimming.CharacterEllipsis,
                    Margin = new Thickness(0, 0, 0, 2)
                };
                cardStack.Children.Add(reasonBlock);
            }
            else if (rescheduleAdj != null)
            {
                string targetTimeDesc = rescheduleAdj.IsCustomTime && !string.IsNullOrEmpty(rescheduleAdj.CustomStartTime)
                    ? $"周{rescheduleAdj.NewDayOfWeek} ⏰{rescheduleAdj.CustomStartTime}-{rescheduleAdj.CustomEndTime}"
                    : $"周{rescheduleAdj.NewDayOfWeek} 第{rescheduleAdj.NewStartSection}节";
                var movedBlock = new TextBlock
                {
                    Text = $"🔀 调至 {targetTimeDesc}",
                    FontSize = 9.5,
                    Foreground = new SolidColorBrush(Color.FromRgb(0xFD, 0xBA, 0x74)),
                    TextTrimming = TextTrimming.CharacterEllipsis,
                    Margin = new Thickness(0, 0, 0, 2)
                };
                cardStack.Children.Add(movedBlock);
            }
            else
            {
                if (course.IsCustomTime && !string.IsNullOrEmpty(course.CustomStartTime))
                {
                    var timeBadge = new Border
                    {
                        Background = new SolidColorBrush(Color.FromArgb(0x60, 0, 0, 0)),
                        BorderBrush = new SolidColorBrush(Color.FromRgb(0xFB, 0xBF, 0x24)),
                        BorderThickness = new Thickness(1),
                        CornerRadius = new CornerRadius(4),
                        Padding = new Thickness(4, 1.5, 4, 1.5),
                        Margin = new Thickness(0, 0, 0, 2),
                        HorizontalAlignment = HorizontalAlignment.Left
                    };
                    var timeBlock = new TextBlock
                    {
                        Text = $"⏰ {course.CustomStartTime} - {course.CustomEndTime}",
                        FontSize = 9.5,
                        FontWeight = FontWeights.Bold,
                        Foreground = new SolidColorBrush(Color.FromRgb(0xFE, 0xF0, 0x8A))
                    };
                    timeBadge.Child = timeBlock;
                    cardStack.Children.Add(timeBadge);
                }

                if (!string.IsNullOrWhiteSpace(course.Location))
                {
                    var locBlock = new TextBlock
                    {
                        Text = $"📍 {course.Location}",
                        FontSize = 10,
                        Foreground = new SolidColorBrush(Color.FromArgb(0xEE, 255, 255, 255)),
                        TextTrimming = TextTrimming.CharacterEllipsis,
                        Margin = new Thickness(0, 0, 0, 2)
                    };
                    cardStack.Children.Add(locBlock);
                }

                if (!string.IsNullOrWhiteSpace(course.Teacher))
                {
                    var teacherBlock = new TextBlock
                    {
                        Text = $"👤 {course.Teacher}",
                        FontSize = 9.5,
                        Foreground = new SolidColorBrush(Color.FromArgb(0xDD, 255, 255, 255)),
                        TextTrimming = TextTrimming.CharacterEllipsis,
                        Margin = new Thickness(0, 0, 0, 2)
                    };
                    cardStack.Children.Add(teacherBlock);
                }

                string weekTypeTag = course.WeekType switch
                {
                    "ODD" => " [单周]",
                    "EVEN" => " [双周]",
                    _ => ""
                };
                var weekRangeBlock = new TextBlock
                {
                    Text = $"⏱️ {course.StartWeek}-{course.EndWeek}周{weekTypeTag}",
                    FontSize = 9,
                    Foreground = new SolidColorBrush(Color.FromArgb(0xBB, 255, 255, 255)),
                    TextTrimming = TextTrimming.CharacterEllipsis
                };
                cardStack.Children.Add(weekRangeBlock);
            }

            card.Child = cardStack;

            string statusTip = suspendAdj != null ? $"⏸️ 本周停课 ({suspendAdj.Reason})"
                             : (rescheduleAdj != null ? $"🔀 本周已调课至周{rescheduleAdj.NewDayOfWeek} {(rescheduleAdj.IsCustomTime ? $"{rescheduleAdj.CustomStartTime}-{rescheduleAdj.CustomEndTime}" : $"第{rescheduleAdj.NewStartSection}节")}"
                             : (isActiveInWeek ? "✅ 本周有课" : "💤 本周无课"));

            string courseTimeTip = course.IsCustomTime && !string.IsNullOrEmpty(course.CustomStartTime)
                ? $"⏰ 时间: {course.CustomStartTime} - {course.CustomEndTime} (自定义时间)\n"
                : $"⏱️ 节次: 第 {course.StartSection}-{course.StartSection + course.SectionSpan - 1} 节\n";

            card.ToolTip = $"{course.Name} ({statusTip})\n" +
                           $"📍 教室: {(string.IsNullOrEmpty(course.Location) ? "未填写" : course.Location)}\n" +
                           $"👤 教师: {(string.IsNullOrEmpty(course.Teacher) ? "未填写" : course.Teacher)}\n" +
                           courseTimeTip +
                           $"📅 周数: {course.StartWeek}-{course.EndWeek} 周{(course.WeekType == "ODD" ? " (单周)" : course.WeekType == "EVEN" ? " (双周)" : " (每周)")}\n" +
                           (string.IsNullOrEmpty(course.Notes) ? "" : $"📝 备注: {course.Notes}\n") +
                           "💡 点击编辑课程详情或调停课";

            var currentCourse = course;
            card.MouseLeftButtonDown += (s, e) =>
            {
                e.Handled = true;
                OpenCourseEditModal(currentCourse);
            };

            Grid.SetColumn(card, course.DayOfWeek);
            Grid.SetRow(card, startRow);
            Grid.SetRowSpan(card, span);
            TimetableMatrixGrid.Children.Add(card);
        }

        // 7. 绘制本周调课产生的新卡片与临时补课卡片
        foreach (var adj in weekAdjustments)
        {
            if (adj.AdjustmentType == "SUSPEND") continue; // 停课无需在新槽位生成卡片

            if (adj.NewDayOfWeek < 1 || adj.NewDayOfWeek > daysCount) continue;
            int startRow;
            int span;
            if (adj.IsCustomTime && !string.IsNullOrEmpty(adj.CustomStartTime))
            {
                startRow = FindClosestSectionRow(adj.CustomStartTime, sectionTimes);
                span = CalculateTimeSpanRows(adj.CustomStartTime, adj.CustomEndTime, adj.SectionSpan, totalSections, startRow);
            }
            else
            {
                startRow = Math.Clamp(adj.NewStartSection - 1, 0, totalSections - 1);
                span = Math.Clamp(adj.SectionSpan, 1, totalSections - startRow);
            }

            var adjCard = new Border
            {
                CornerRadius = new CornerRadius(8),
                Margin = new Thickness(2),
                Padding = new Thickness(7, 6, 7, 6),
                Cursor = Cursors.Hand
            };

            bool isResched = (adj.AdjustmentType == "RESCHEDULE");
            adjCard.Background = isResched ? new SolidColorBrush(Color.FromRgb(0xC2, 0x41, 0x0C)) : new SolidColorBrush(Color.FromRgb(0x0F, 0x76, 0x6E));
            adjCard.BorderBrush = isResched ? new SolidColorBrush(Color.FromRgb(0xFB, 0x92, 0x3C)) : new SolidColorBrush(Color.FromRgb(0x2D, 0xD4, 0xBF));
            adjCard.BorderThickness = new Thickness(1.5);
            adjCard.Effect = new System.Windows.Media.Effects.DropShadowEffect
            {
                BlurRadius = 8,
                ShadowDepth = 2,
                Color = Colors.Black,
                Opacity = 0.4
            };

            var stack = new StackPanel();
            string tagText = isResched ? "🔀 [调课] " : "➕ [补课] ";
            var nameBlock = new TextBlock
            {
                Text = tagText + adj.CourseName,
                FontWeight = FontWeights.Bold,
                FontSize = 11.5,
                Foreground = Brushes.White,
                TextWrapping = TextWrapping.Wrap,
                TextTrimming = TextTrimming.CharacterEllipsis,
                LineHeight = 15,
                Margin = new Thickness(0, 0, 0, 2)
            };
            stack.Children.Add(nameBlock);

            if (adj.IsCustomTime && !string.IsNullOrEmpty(adj.CustomStartTime))
            {
                var timeBadge = new Border
                {
                    Background = new SolidColorBrush(Color.FromArgb(0x60, 0, 0, 0)),
                    BorderBrush = new SolidColorBrush(Color.FromRgb(0xFB, 0xBF, 0x24)),
                    BorderThickness = new Thickness(1),
                    CornerRadius = new CornerRadius(4),
                    Padding = new Thickness(4, 1.5, 4, 1.5),
                    Margin = new Thickness(0, 0, 0, 2),
                    HorizontalAlignment = HorizontalAlignment.Left
                };
                var timeBlock = new TextBlock
                {
                    Text = $"⏰ {adj.CustomStartTime} - {adj.CustomEndTime}",
                    FontSize = 9.5,
                    FontWeight = FontWeights.Bold,
                    Foreground = new SolidColorBrush(Color.FromRgb(0xFE, 0xF0, 0x8A))
                };
                timeBadge.Child = timeBlock;
                stack.Children.Add(timeBadge);
            }

            if (!string.IsNullOrWhiteSpace(adj.NewLocation))
            {
                var locBlock = new TextBlock
                {
                    Text = $"📍 {adj.NewLocation}",
                    FontSize = 10,
                    Foreground = new SolidColorBrush(Color.FromArgb(0xEE, 255, 255, 255)),
                    TextTrimming = TextTrimming.CharacterEllipsis,
                    Margin = new Thickness(0, 0, 0, 2)
                };
                stack.Children.Add(locBlock);
            }

            if (!string.IsNullOrWhiteSpace(adj.Reason))
            {
                var reasonBlock = new TextBlock
                {
                    Text = $"📝 {adj.Reason}",
                    FontSize = 9.5,
                    Foreground = new SolidColorBrush(Color.FromArgb(0xDD, 255, 255, 255)),
                    TextTrimming = TextTrimming.CharacterEllipsis
                };
                stack.Children.Add(reasonBlock);
            }

            adjCard.Child = stack;
            string timeDetailTip = adj.IsCustomTime && !string.IsNullOrEmpty(adj.CustomStartTime)
                ? $"⏰ 自定义时间: {adj.CustomStartTime} - {adj.CustomEndTime} (特定天变更)"
                : $"⏱️ 节次: 第 {adj.NewStartSection}-{adj.NewStartSection + adj.SectionSpan - 1} 节";

            adjCard.ToolTip = $"{adj.CourseName} ({(isResched ? "本周调课" : "本周临时加课/补课")})\n" +
                              $"📍 教室: {(string.IsNullOrEmpty(adj.NewLocation) ? "未填写" : adj.NewLocation)}\n" +
                              $"{timeDetailTip}\n" +
                              $"📝 原因: {adj.Reason}\n" +
                              "💡 点击查看并管理调停课记录";

            adjCard.MouseLeftButtonDown += (s, e) =>
            {
                e.Handled = true;
                OnOpenAdjustmentModalClicked(s, e);
            };

            Grid.SetColumn(adjCard, adj.NewDayOfWeek);
            Grid.SetRow(adjCard, startRow);
            Grid.SetRowSpan(adjCard, span);
            TimetableMatrixGrid.Children.Add(adjCard);
        }
    }

    // =========================================================================
    // ================== TIMETABLE CONTROLS & INTERACTIONS ====================
    // =========================================================================

    private void OnTimetablePrevWeekClicked(object sender, RoutedEventArgs e)
    {
        if (_selectedTimetableWeek > 1)
        {
            _selectedTimetableWeek--;
            RenderTimetablePage();
        }
    }

    private void OnTimetableNextWeekClicked(object sender, RoutedEventArgs e)
    {
        int totalWeeks = int.TryParse(DatabaseService.GetTimetableSetting("total_weeks", "20"), out int tw) ? tw : 20;
        if (_selectedTimetableWeek < totalWeeks)
        {
            _selectedTimetableWeek++;
            RenderTimetablePage();
        }
    }

    private void OnTimetableCurrentWeekClicked(object sender, RoutedEventArgs e)
    {
        CalculateCurrentActualWeek();
        _selectedTimetableWeek = _currentActualWeek;
        RenderTimetablePage();
    }

    private void OnRefreshTimetableClicked(object sender, RoutedEventArgs e)
    {
        RenderTimetablePage();
    }

    // =========================================================================
    // ======================== COURSE MODAL LOGIC =============================
    // =========================================================================

    private void OpenCourseCreateModalForSlot(int dayOfWeek, int startSection)
    {
        InitTimetableControls();
        _editingCourseId = null;
        CourseModalTitleText.Text = "➕ 添加课程";
        CourseNameInput.Text = string.Empty;
        CourseLocationInput.Text = string.Empty;
        CourseTeacherInput.Text = string.Empty;
        CourseNotesInput.Text = string.Empty;
        CourseDeleteBtn.Visibility = Visibility.Collapsed;
        CourseQuickAdjustBtn.Visibility = Visibility.Collapsed;

        if (CourseCustomTimeCheck != null) CourseCustomTimeCheck.IsChecked = false;
        if (CourseCustomTimePanel != null) CourseCustomTimePanel.Visibility = Visibility.Collapsed;
        if (CourseCustomStartTimeInput != null) CourseCustomStartTimeInput.Text = "15:30";
        if (CourseCustomEndTimeInput != null) CourseCustomEndTimeInput.Text = "17:15";

        if (dayOfWeek >= 1 && dayOfWeek <= 7)
        {
            CourseDayCombo.SelectedIndex = dayOfWeek - 1;
        }

        if (startSection >= 1 && startSection <= 12)
        {
            CourseStartSectionCombo.SelectedIndex = startSection - 1;
        }

        CourseSpanSectionCombo.SelectedIndex = 1;

        CourseStartWeekCombo.SelectedIndex = 0;
        int totalWeeks = int.TryParse(DatabaseService.GetTimetableSetting("total_weeks", "20"), out int tw) ? tw : 20;
        CourseEndWeekCombo.SelectedIndex = Math.Clamp(totalWeeks - 1, 0, CourseEndWeekCombo.Items.Count - 1);
        CourseWeekTypeCombo.SelectedIndex = 0;

        _selectedCourseColor = "#3B82F6";
        UpdateCourseColorPickerSelection();

        CourseModal.Visibility = Visibility.Visible;
        CourseNameInput.Focus();
    }

    private void OnCreateCourseClicked(object sender, RoutedEventArgs e)
    {
        OpenCourseCreateModalForSlot(1, 1);
    }

    private void OpenCourseEditModal(CourseItem course)
    {
        InitTimetableControls();
        _editingCourseId = course.Id;
        CourseModalTitleText.Text = "🎓 编辑课程详情";
        CourseNameInput.Text = course.Name;
        CourseLocationInput.Text = course.Location;
        CourseTeacherInput.Text = course.Teacher;
        CourseNotesInput.Text = course.Notes;
        CourseDeleteBtn.Visibility = Visibility.Visible;
        CourseQuickAdjustBtn.Visibility = Visibility.Visible;

        if (CourseCustomTimeCheck != null) CourseCustomTimeCheck.IsChecked = course.IsCustomTime;
        if (CourseCustomTimePanel != null) CourseCustomTimePanel.Visibility = course.IsCustomTime ? Visibility.Visible : Visibility.Collapsed;
        if (CourseCustomStartTimeInput != null) CourseCustomStartTimeInput.Text = string.IsNullOrEmpty(course.CustomStartTime) ? "15:30" : course.CustomStartTime;
        if (CourseCustomEndTimeInput != null) CourseCustomEndTimeInput.Text = string.IsNullOrEmpty(course.CustomEndTime) ? "17:15" : course.CustomEndTime;

        CourseDayCombo.SelectedIndex = Math.Clamp(course.DayOfWeek - 1, 0, CourseDayCombo.Items.Count - 1);
        CourseStartSectionCombo.SelectedIndex = Math.Clamp(course.StartSection - 1, 0, CourseStartSectionCombo.Items.Count - 1);
        CourseSpanSectionCombo.SelectedIndex = Math.Clamp(course.SectionSpan - 1, 0, CourseSpanSectionCombo.Items.Count - 1);
        CourseStartWeekCombo.SelectedIndex = Math.Clamp(course.StartWeek - 1, 0, CourseStartWeekCombo.Items.Count - 1);
        CourseEndWeekCombo.SelectedIndex = Math.Clamp(course.EndWeek - 1, 0, CourseEndWeekCombo.Items.Count - 1);

        int weekTypeIdx = course.WeekType switch
        {
            "ODD" => 1,
            "EVEN" => 2,
            _ => 0
        };
        CourseWeekTypeCombo.SelectedIndex = weekTypeIdx;

        _selectedCourseColor = string.IsNullOrEmpty(course.ColorHex) ? "#3B82F6" : course.ColorHex;
        UpdateCourseColorPickerSelection();

        CourseModal.Visibility = Visibility.Visible;
    }

    private void OnCourseCustomTimeCheckChanged(object sender, RoutedEventArgs e)
    {
        if (CourseCustomTimePanel == null || CourseCustomTimeCheck == null) return;
        CourseCustomTimePanel.Visibility = CourseCustomTimeCheck.IsChecked == true ? Visibility.Visible : Visibility.Collapsed;
    }

    private void OnCloseCourseModalClicked(object sender, RoutedEventArgs e)
    {
        CourseModal.Visibility = Visibility.Collapsed;
    }

    private void OnSaveCourseClicked(object sender, RoutedEventArgs e)
    {
        string name = CourseNameInput.Text.Trim();
        if (string.IsNullOrWhiteSpace(name))
        {
            System.Windows.MessageBox.Show("请输入课程名称！", "提示", MessageBoxButton.OK, MessageBoxImage.Information);
            CourseNameInput.Focus();
            return;
        }

        int dayOfWeek = CourseDayCombo.SelectedIndex + 1;
        int startSection = CourseStartSectionCombo.SelectedIndex + 1;
        int sectionSpan = CourseSpanSectionCombo.SelectedIndex + 1;
        int startWeek = CourseStartWeekCombo.SelectedIndex + 1;
        int endWeek = CourseEndWeekCombo.SelectedIndex + 1;

        if (endWeek < startWeek)
        {
            System.Windows.MessageBox.Show("结束周数不能小于起始周数！", "提示", MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }

        string weekType = (CourseWeekTypeCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "ALL";
        bool isCustomTime = CourseCustomTimeCheck?.IsChecked == true;
        string custStart = CourseCustomStartTimeInput?.Text.Trim() ?? "";
        string custEnd = CourseCustomEndTimeInput?.Text.Trim() ?? "";

        var course = new CourseItem
        {
            Id = _editingCourseId ?? Guid.NewGuid().ToString(),
            Name = name,
            Teacher = CourseTeacherInput.Text.Trim(),
            Location = CourseLocationInput.Text.Trim(),
            DayOfWeek = dayOfWeek,
            StartSection = startSection,
            SectionSpan = sectionSpan,
            StartWeek = startWeek,
            EndWeek = endWeek,
            WeekType = weekType,
            ColorHex = _selectedCourseColor,
            Notes = CourseNotesInput.Text.Trim(),
            IsCustomTime = isCustomTime,
            CustomStartTime = custStart,
            CustomEndTime = custEnd,
            UpdatedAt = DateTime.UtcNow
        };

        DatabaseService.UpsertCourse(course);
        CourseModal.Visibility = Visibility.Collapsed;
        RenderTimetablePage();
    }

    private void OnDeleteCourseClicked(object sender, RoutedEventArgs e)
    {
        if (string.IsNullOrEmpty(_editingCourseId)) return;

        var res = System.Windows.MessageBox.Show("确定要删除这门课程吗？", "删除课程确认", MessageBoxButton.YesNo, MessageBoxImage.Question);
        if (res == MessageBoxResult.Yes)
        {
            DatabaseService.DeleteCourse(_editingCourseId);
            CourseModal.Visibility = Visibility.Collapsed;
            RenderTimetablePage();
        }
    }

    private void OnCourseQuickAdjustClicked(object sender, RoutedEventArgs e)
    {
        string currentId = _editingCourseId ?? "";
        var currentCourse = DatabaseService.GetAllCourses().FirstOrDefault(c => c.Id == currentId);
        CourseModal.Visibility = Visibility.Collapsed;
        OnOpenAdjustmentModalClicked(sender, e);

        // 预设为调课模式 (RESCHEDULE) 并锁定当前周
        if (AdjTypeCombo != null)
        {
            AdjTypeCombo.SelectedIndex = 1;
            OnAdjTypeChanged(sender, null!);
        }

        if (AdjWeekCombo != null)
        {
            AdjWeekCombo.SelectedIndex = Math.Clamp(_selectedTimetableWeek - 1, 0, AdjWeekCombo.Items.Count - 1);
        }

        // 联动预选当前课程
        for (int i = 0; i < AdjCourseCombo.Items.Count; i++)
        {
            if (AdjCourseCombo.Items[i] is ComboBoxItem cItem && cItem.Tag?.ToString() == currentId)
            {
                AdjCourseCombo.SelectedIndex = i;
                break;
            }
        }

        if (currentCourse != null)
        {
            if (AdjNewDayCombo != null && currentCourse.DayOfWeek >= 1 && currentCourse.DayOfWeek <= 7)
            {
                AdjNewDayCombo.SelectedIndex = currentCourse.DayOfWeek - 1;
            }
            if (currentCourse.IsCustomTime && !string.IsNullOrEmpty(currentCourse.CustomStartTime))
            {
                if (AdjModeCustomTimeRadio != null) AdjModeCustomTimeRadio.IsChecked = true;
                if (AdjCustomStartTimeInput != null) AdjCustomStartTimeInput.Text = currentCourse.CustomStartTime;
                if (AdjCustomEndTimeInput != null) AdjCustomEndTimeInput.Text = currentCourse.CustomEndTime;
            }
        }

        UpdateAdjTargetDateText();
    }

    private int FindClosestSectionRow(string timeStr, List<SectionTimeSlot> slots)
    {
        if (slots == null || slots.Count == 0) return 0;
        if (!TimeSpan.TryParse(timeStr, out var targetTime)) return 0;

        int closestIdx = 0;
        double minDiff = double.MaxValue;

        for (int i = 0; i < slots.Count; i++)
        {
            if (TimeSpan.TryParse(slots[i].Start, out var slotStart))
            {
                double diff = Math.Abs((slotStart - targetTime).TotalMinutes);
                if (diff < minDiff)
                {
                    minDiff = diff;
                    closestIdx = i;
                }
            }
        }
        return closestIdx;
    }

    private int CalculateTimeSpanRows(string startStr, string endStr, int defaultSpan, int totalSections, int startRow)
    {
        if (TimeSpan.TryParse(startStr, out var s) && TimeSpan.TryParse(endStr, out var e) && e > s)
        {
            int classDur = int.TryParse(DatabaseService.GetTimetableSetting("class_duration", "45"), out int cd) ? cd : 45;
            int breakDur = int.TryParse(DatabaseService.GetTimetableSetting("break_duration", "10"), out int bd) ? bd : 10;
            double slotMins = Math.Max(30, classDur + breakDur);
            double totalMins = (e - s).TotalMinutes;
            int span = (int)Math.Round(totalMins / slotMins);
            return Math.Clamp(span, 1, Math.Max(1, totalSections - startRow));
        }
        return Math.Clamp(defaultSpan, 1, Math.Max(1, totalSections - startRow));
    }

    // =========================================================================
    // =================== TIMETABLE SETTINGS MODAL LOGIC ======================
    // =========================================================================

    private void OnTimetableTabSemesterClicked(object sender, RoutedEventArgs e)
    {
        TimetableTabSemesterBtn.Background = new SolidColorBrush(Color.FromRgb(0x1A, 0x73, 0xE8));
        TimetableTabSemesterBtn.Foreground = Brushes.White;
        TimetableTabTimingBtn.Background = Brushes.Transparent;
        TimetableTabTimingBtn.Foreground = ThemeService.CurrentSecondaryTextBrush;

        TimetableTabSemesterPanel.Visibility = Visibility.Visible;
        TimetableTabTimingPanel.Visibility = Visibility.Collapsed;
    }

    private void OnTimetableTabTimingClicked(object sender, RoutedEventArgs e)
    {
        TimetableTabTimingBtn.Background = new SolidColorBrush(Color.FromRgb(0x1A, 0x73, 0xE8));
        TimetableTabTimingBtn.Foreground = Brushes.White;
        TimetableTabSemesterBtn.Background = Brushes.Transparent;
        TimetableTabSemesterBtn.Foreground = ThemeService.CurrentSecondaryTextBrush;

        TimetableTabTimingPanel.Visibility = Visibility.Visible;
        TimetableTabSemesterPanel.Visibility = Visibility.Collapsed;

        int totalSections = int.TryParse(DatabaseService.GetTimetableSetting("total_sections", "12"), out int ts) ? ts : 12;
        RenderSectionTimesEditor(totalSections);
    }

    private void OnTimetableStartDatePickerChanged(object sender, SelectionChangedEventArgs? e)
    {
        if (TimetableStartDatePicker == null || TimetableStartDateInput == null) return;
        if (TimetableStartDatePicker.SelectedDate.HasValue)
        {
            var date = TimetableStartDatePicker.SelectedDate.Value;
            int diffToMonday = ((int)date.DayOfWeek + 6) % 7;
            var monday = date.AddDays(-diffToMonday);
            TimetableStartDateInput.Text = monday.ToString("yyyy-MM-dd");
        }
    }

    private void OnOpenTimetableSettingsClicked(object sender, RoutedEventArgs e)
    {
        TimetableSemesterNameInput.Text = DatabaseService.GetTimetableSetting("semester_name", "2026年秋季学期");
        string startStr = DatabaseService.GetTimetableSetting("semester_start_date", "2026-09-07");
        TimetableStartDateInput.Text = startStr;
        if (DateTime.TryParse(startStr, out var sdt))
        {
            TimetableStartDatePicker.SelectedDate = sdt;
        }

        TimetableTotalWeeksInput.Text = DatabaseService.GetTimetableSetting("total_weeks", "20");

        string totalSecsStr = DatabaseService.GetTimetableSetting("total_sections", "12");
        foreach (ComboBoxItem item in TimetableTotalSectionsCombo.Items)
        {
            if (item.Tag?.ToString() == totalSecsStr)
            {
                TimetableTotalSectionsCombo.SelectedItem = item;
                break;
            }
        }

        bool showWeekend = DatabaseService.GetTimetableSetting("show_weekend", "true") != "false";
        TimetableShowWeekendCheck.IsChecked = showWeekend;

        // 作息时间配置输入框
        TimetableClassDurationInput.Text = DatabaseService.GetTimetableSetting("class_duration", "45");
        TimetableBreakDurationInput.Text = DatabaseService.GetTimetableSetting("break_duration", "10");
        TimetableMorningStartTimeInput.Text = DatabaseService.GetTimetableSetting("morning_start", "08:00");
        TimetableAfternoonStartTimeInput.Text = DatabaseService.GetTimetableSetting("afternoon_start", "14:00");
        TimetableEveningStartTimeInput.Text = DatabaseService.GetTimetableSetting("evening_start", "18:30");

        OnTimetableTabSemesterClicked(sender, e);
        TimetableSettingsModal.Visibility = Visibility.Visible;
    }

    private void RenderSectionTimesEditor(int totalSections)
    {
        TimetableSectionsListPanel.Children.Clear();
        var currentSlots = GetConfiguredSectionTimes(totalSections);

        foreach (var slot in currentSlots)
        {
            var rowGrid = new Grid { Margin = new Thickness(0, 3, 0, 3) };
            rowGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(110) });
            rowGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(85) });
            rowGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(20) });
            rowGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(85) });
            rowGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

            var labelBlock = new TextBlock
            {
                Text = $"第 {slot.Section} 节 ({slot.Label})：",
                FontSize = 11,
                Foreground = ThemeService.CurrentSecondaryTextBrush,
                VerticalAlignment = VerticalAlignment.Center
            };
            Grid.SetColumn(labelBlock, 0);

            var startBox = new TextBox
            {
                Text = slot.Start,
                Background = ThemeService.CurrentInputBrush,
                Foreground = ThemeService.CurrentTextBrush,
                BorderBrush = ThemeService.CurrentBorderBrush,
                BorderThickness = new Thickness(1),
                Padding = new Thickness(6, 3, 6, 3),
                FontSize = 11,
                Tag = $"START_{slot.Section}"
            };
            Grid.SetColumn(startBox, 1);

            var sepBlock = new TextBlock
            {
                Text = "~",
                FontSize = 11,
                Foreground = ThemeService.CurrentSecondaryTextBrush,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            Grid.SetColumn(sepBlock, 2);

            var endBox = new TextBox
            {
                Text = slot.End,
                Background = ThemeService.CurrentInputBrush,
                Foreground = ThemeService.CurrentTextBrush,
                BorderBrush = ThemeService.CurrentBorderBrush,
                BorderThickness = new Thickness(1),
                Padding = new Thickness(6, 3, 6, 3),
                FontSize = 11,
                Tag = $"END_{slot.Section}"
            };
            Grid.SetColumn(endBox, 3);

            rowGrid.Children.Add(labelBlock);
            rowGrid.Children.Add(startBox);
            rowGrid.Children.Add(sepBlock);
            rowGrid.Children.Add(endBox);

            TimetableSectionsListPanel.Children.Add(rowGrid);
        }
    }

    private void OnAutoGenerateScheduleTimesClicked(object sender, RoutedEventArgs e)
    {
        int totalSections = (TimetableTotalSectionsCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString() switch
        {
            "8" => 8,
            "10" => 10,
            "14" => 14,
            _ => 12
        };

        int classDur = int.TryParse(TimetableClassDurationInput.Text.Trim(), out int cd) && cd > 0 ? cd : 45;
        int breakDur = int.TryParse(TimetableBreakDurationInput.Text.Trim(), out int bd) && bd >= 0 ? bd : 10;
        string mStart = TimetableMorningStartTimeInput.Text.Trim();
        string aStart = TimetableAfternoonStartTimeInput.Text.Trim();
        string eStart = TimetableEveningStartTimeInput.Text.Trim();

        var generated = GenerateDefaultSectionTimes(totalSections, classDur, breakDur, mStart, aStart, eStart);

        // 重新渲染编辑列表
        TimetableSectionsListPanel.Children.Clear();
        foreach (var slot in generated)
        {
            var rowGrid = new Grid { Margin = new Thickness(0, 3, 0, 3) };
            rowGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(110) });
            rowGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(85) });
            rowGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(20) });
            rowGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(85) });
            rowGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

            var labelBlock = new TextBlock
            {
                Text = $"第 {slot.Section} 节 ({slot.Label})：",
                FontSize = 11,
                Foreground = ThemeService.CurrentSecondaryTextBrush,
                VerticalAlignment = VerticalAlignment.Center
            };
            Grid.SetColumn(labelBlock, 0);

            var startBox = new TextBox
            {
                Text = slot.Start,
                Background = ThemeService.CurrentInputBrush,
                Foreground = ThemeService.CurrentTextBrush,
                BorderBrush = ThemeService.CurrentBorderBrush,
                BorderThickness = new Thickness(1),
                Padding = new Thickness(6, 3, 6, 3),
                FontSize = 11,
                Tag = $"START_{slot.Section}"
            };
            Grid.SetColumn(startBox, 1);

            var sepBlock = new TextBlock
            {
                Text = "~",
                FontSize = 11,
                Foreground = ThemeService.CurrentSecondaryTextBrush,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            Grid.SetColumn(sepBlock, 2);

            var endBox = new TextBox
            {
                Text = slot.End,
                Background = ThemeService.CurrentInputBrush,
                Foreground = ThemeService.CurrentTextBrush,
                BorderBrush = ThemeService.CurrentBorderBrush,
                BorderThickness = new Thickness(1),
                Padding = new Thickness(6, 3, 6, 3),
                FontSize = 11,
                Tag = $"END_{slot.Section}"
            };
            Grid.SetColumn(endBox, 3);

            rowGrid.Children.Add(labelBlock);
            rowGrid.Children.Add(startBox);
            rowGrid.Children.Add(sepBlock);
            rowGrid.Children.Add(endBox);

            TimetableSectionsListPanel.Children.Add(rowGrid);
        }

        System.Windows.MessageBox.Show($"已按单节 {classDur} 分钟、课间 {breakDur} 分钟生成全部节次时间！", "成功", MessageBoxButton.OK, MessageBoxImage.Information);
    }

    private void OnCloseTimetableSettingsClicked(object sender, RoutedEventArgs e)
    {
        TimetableSettingsModal.Visibility = Visibility.Collapsed;
    }

    private void OnSaveTimetableSettingsClicked(object sender, RoutedEventArgs e)
    {
        string semName = TimetableSemesterNameInput.Text.Trim();
        if (string.IsNullOrWhiteSpace(semName)) semName = "2026年秋季学期";

        string startDate = TimetableStartDateInput.Text.Trim();
        if (!DateTime.TryParse(startDate, out var parsedStart))
        {
            System.Windows.MessageBox.Show("开学日期格式不正确，请输入形如 2026-09-07 的有效日期！", "提示", MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }

        // 自动将开学日期对齐为当周周一
        int diffToMon = ((int)parsedStart.DayOfWeek + 6) % 7;
        parsedStart = parsedStart.AddDays(-diffToMon);
        startDate = parsedStart.ToString("yyyy-MM-dd");

        // 学期总周数 (自定义 1 ~ 35 周)
        if (!int.TryParse(TimetableTotalWeeksInput.Text.Trim(), out int totalWeeks) || totalWeeks < 1 || totalWeeks > 35)
        {
            totalWeeks = 20;
        }

        string totalSections = (TimetableTotalSectionsCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "12";
        string showWeekend = (TimetableShowWeekendCheck.IsChecked == true) ? "true" : "false";

        // 保存基础配置
        DatabaseService.SetTimetableSetting("semester_name", semName);
        DatabaseService.SetTimetableSetting("semester_start_date", startDate);
        DatabaseService.SetTimetableSetting("total_weeks", totalWeeks.ToString());
        DatabaseService.SetTimetableSetting("total_sections", totalSections);
        DatabaseService.SetTimetableSetting("show_weekend", showWeekend);

        DatabaseService.SetTimetableSetting("class_duration", TimetableClassDurationInput.Text.Trim());
        DatabaseService.SetTimetableSetting("break_duration", TimetableBreakDurationInput.Text.Trim());
        DatabaseService.SetTimetableSetting("morning_start", TimetableMorningStartTimeInput.Text.Trim());
        DatabaseService.SetTimetableSetting("afternoon_start", TimetableAfternoonStartTimeInput.Text.Trim());
        DatabaseService.SetTimetableSetting("evening_start", TimetableEveningStartTimeInput.Text.Trim());

        // 收集自定义的所有节次时间表
        int numSections = int.TryParse(totalSections, out int ns) ? ns : 12;
        var customSlots = new List<SectionTimeSlot>();

        for (int sec = 1; sec <= numSections; sec++)
        {
            string start = $"{8 + sec:D2}:00";
            string end = $"{8 + sec:D2}:45";

            foreach (var child in TimetableSectionsListPanel.Children)
            {
                if (child is Grid grid)
                {
                    TextBox? sBox = null;
                    TextBox? eBox = null;
                    foreach (var elem in grid.Children)
                    {
                        if (elem is TextBox tb)
                        {
                            if (tb.Tag?.ToString() == $"START_{sec}") sBox = tb;
                            else if (tb.Tag?.ToString() == $"END_{sec}") eBox = tb;
                        }
                    }
                    if (sBox != null && eBox != null)
                    {
                        start = sBox.Text.Trim();
                        end = eBox.Text.Trim();
                        break;
                    }
                }
            }

            customSlots.Add(new SectionTimeSlot
            {
                Section = sec,
                Start = start,
                End = end,
                Label = sec <= 4 ? "上午" : (sec <= 8 ? "下午" : "晚上")
            });
        }

        if (customSlots.Count > 0)
        {
            string json = JsonSerializer.Serialize(customSlots);
            DatabaseService.SetTimetableSetting("section_schedule_json", json);
        }

        // 重新同步起始周下拉框上限
        _isTimetableControlsInitialized = false;
        InitTimetableControls();

        TimetableSettingsModal.Visibility = Visibility.Collapsed;
        RenderTimetablePage();
    }

    private void OnLoadDemoCoursesClicked(object sender, RoutedEventArgs e)
    {
        var confirm = System.Windows.MessageBox.Show(
            "载入示范课表将为您注入《高等数学》、《数据结构与算法》、《计算机网络》、《操作系统》、《大学英语》、《人工智能导论》等示范课程。\n\n是否继续载入？",
            "载入 WakeUp 经典示范课表",
            MessageBoxButton.YesNo,
            MessageBoxImage.Question);

        if (confirm != MessageBoxResult.Yes) return;

        var demoList = new List<CourseItem>
        {
            new CourseItem
            {
                Name = "高等数学 (上)",
                Teacher = "李教授",
                Location = "教一 302",
                DayOfWeek = 1,
                StartSection = 1,
                SectionSpan = 2,
                StartWeek = 1,
                EndWeek = 16,
                WeekType = "ALL",
                ColorHex = "#3B82F6",
                Notes = "准备期中随堂测验，携带课本与草稿纸"
            },
            new CourseItem
            {
                Name = "大学英语 (三)",
                Teacher = "Sarah Brown",
                Location = "外语楼 204",
                DayOfWeek = 1,
                StartSection = 5,
                SectionSpan = 2,
                StartWeek = 1,
                EndWeek = 18,
                WeekType = "ALL",
                ColorHex = "#EC4899",
                Notes = "需提交 Unit 4 口语对话视频"
            },
            new CourseItem
            {
                Name = "数据结构与算法",
                Teacher = "王教授",
                Location = "计机楼 401",
                DayOfWeek = 2,
                StartSection = 3,
                SectionSpan = 2,
                StartWeek = 1,
                EndWeek = 16,
                WeekType = "ALL",
                ColorHex = "#10B981",
                Notes = "重点掌握平衡二叉树与图遍历算法"
            },
            new CourseItem
            {
                Name = "机器学习前沿",
                Teacher = "赵教授",
                Location = "计机楼 302",
                DayOfWeek = 2,
                StartSection = 7,
                SectionSpan = 2,
                StartWeek = 1,
                EndWeek = 16,
                WeekType = "ODD",
                ColorHex = "#E11D48",
                Notes = "隔周单周上课，关注 Transformer 论文研读"
            },
            new CourseItem
            {
                Name = "计算机网络",
                Teacher = "陈副教授",
                Location = "信科楼 105",
                DayOfWeek = 3,
                StartSection = 1,
                SectionSpan = 2,
                StartWeek = 1,
                EndWeek = 16,
                WeekType = "ALL",
                ColorHex = "#8B5CF6",
                Notes = "Wireshark 抓包实验作业本周截止"
            },
            new CourseItem
            {
                Name = "操作系统课设",
                Teacher = "刘讲师",
                Location = "软件实验室 A2",
                DayOfWeek = 4,
                StartSection = 5,
                SectionSpan = 3,
                StartWeek = 1,
                EndWeek = 16,
                WeekType = "ALL",
                ColorHex = "#06B6D4",
                Notes = "内核进程调度器模拟实验"
            },
            new CourseItem
            {
                Name = "人工智能导论",
                Teacher = "张特聘教授",
                Location = "前沿科学馆 101",
                DayOfWeek = 5,
                StartSection = 3,
                SectionSpan = 2,
                StartWeek = 1,
                EndWeek = 16,
                WeekType = "ALL",
                ColorHex = "#F59E0B",
                Notes = "大作业选题研讨"
            },
            new CourseItem
            {
                Name = "创新创业实践",
                Teacher = "吴老师",
                Location = "创客工坊 201",
                DayOfWeek = 5,
                StartSection = 7,
                SectionSpan = 2,
                StartWeek = 1,
                EndWeek = 16,
                WeekType = "EVEN",
                ColorHex = "#14B8A6",
                Notes = "双周上课，商业计划书答辩预演"
            }
        };

        foreach (var item in demoList)
        {
            DatabaseService.UpsertCourse(item);
        }

        TimetableSettingsModal.Visibility = Visibility.Collapsed;
        RenderTimetablePage();
        System.Windows.MessageBox.Show("示范课表已成功载入！", "成功", MessageBoxButton.OK, MessageBoxImage.Information);
    }

    // =========================================================================
    // ================= COURSE ADJUSTMENTS (调课/停课/临时补课) LOGIC ============
    // =========================================================================

    private void OnOpenAdjustmentModalClicked(object sender, RoutedEventArgs e)
    {
        int totalWeeks = int.TryParse(DatabaseService.GetTimetableSetting("total_weeks", "20"), out int tw) ? tw : 20;
        int totalSections = int.TryParse(DatabaseService.GetTimetableSetting("total_sections", "12"), out int ts) ? ts : 12;

        // 1. 初始化目标周下拉框
        AdjWeekCombo.Items.Clear();
        for (int w = 1; w <= totalWeeks; w++)
        {
            AdjWeekCombo.Items.Add(new ComboBoxItem { Content = $"第 {w} 周", Tag = w });
        }
        AdjWeekCombo.SelectedIndex = Math.Clamp(_selectedTimetableWeek - 1, 0, AdjWeekCombo.Items.Count - 1);

        // 2. 初始化关联课程下拉框
        AdjCourseCombo.Items.Clear();
        var courses = DatabaseService.GetAllCourses();
        foreach (var c in courses)
        {
            string dayStr = c.DayOfWeek switch { 1 => "周一", 2 => "周二", 3 => "周三", 4 => "周四", 5 => "周五", 6 => "周六", _ => "周日" };
            string timeStr = c.IsCustomTime && !string.IsNullOrEmpty(c.CustomStartTime) ? $"⏰{c.CustomStartTime}" : $"第{c.StartSection}节";
            AdjCourseCombo.Items.Add(new ComboBoxItem
            {
                Content = $"{c.Name} ({dayStr} {timeStr})",
                Tag = c.Id
            });
        }
        if (AdjCourseCombo.Items.Count > 0) AdjCourseCombo.SelectedIndex = 0;

        // 3. 初始化起始节次下拉框
        AdjNewStartSectionCombo.Items.Clear();
        for (int s = 1; s <= totalSections; s++)
        {
            AdjNewStartSectionCombo.Items.Add(new ComboBoxItem { Content = $"第 {s} 节", Tag = s });
        }
        AdjNewStartSectionCombo.SelectedIndex = 0;

        // 4. 重置表单状态与模式
        AdjTypeCombo.SelectedIndex = 0; // 默认停课
        OnAdjTypeChanged(sender, null!);

        if (AdjModeCustomTimeRadio != null) AdjModeCustomTimeRadio.IsChecked = true;
        OnAdjTimeModeChanged(sender, null!);

        UpdateAdjTargetDateText();
        OnAdjCustomTimeInputChanged(null!, null!);

        RefreshAdjustmentRecordsList();
        CourseAdjustmentModal.Visibility = Visibility.Visible;
    }

    private void OnCloseAdjustmentModalClicked(object sender, RoutedEventArgs e)
    {
        CourseAdjustmentModal.Visibility = Visibility.Collapsed;
        RenderTimetablePage();
    }

    private void OnAdjTypeChanged(object sender, SelectionChangedEventArgs? e)
    {
        if (AdjTypeCombo == null || AdjNewTimePanel == null || AdjCustomCourseNameInput == null || AdjCourseCombo == null || AdjCourseSelectLabel == null || AdjReasonInput == null) return;

        if (AdjTypeCombo.SelectedItem is ComboBoxItem item && item.Tag is string type)
        {
            if (type == "SUSPEND")
            {
                AdjNewTimePanel.Visibility = Visibility.Collapsed;
                AdjCustomCourseNameInput.Visibility = Visibility.Collapsed;
                AdjCourseCombo.Visibility = Visibility.Visible;
                AdjCourseSelectLabel.Text = "选择停课课程：";
                AdjReasonInput.Text = "国庆放假停课";
            }
            else if (type == "RESCHEDULE")
            {
                AdjNewTimePanel.Visibility = Visibility.Visible;
                AdjCustomCourseNameInput.Visibility = Visibility.Collapsed;
                AdjCourseCombo.Visibility = Visibility.Visible;
                AdjCourseSelectLabel.Text = "选择原课程：";
                AdjReasonInput.Text = "任课教师出差调课";
            }
            else if (type == "MAKEUP")
            {
                AdjNewTimePanel.Visibility = Visibility.Visible;
                AdjCustomCourseNameInput.Visibility = Visibility.Visible;
                AdjCourseCombo.Visibility = Visibility.Collapsed;
                AdjCourseSelectLabel.Text = "输入补课 / 临时加课名称：";
                AdjCustomCourseNameInput.Text = "考前集中串讲";
                AdjReasonInput.Text = "期末考前集中答疑辅导";
            }
        }
        UpdateAdjTargetDateText();
    }

    private void OnAdjCourseChanged(object sender, SelectionChangedEventArgs? e)
    {
        if (AdjCourseCombo == null || AdjNewLocationInput == null || AdjNewSpanCombo == null) return;

        if (AdjCourseCombo.SelectedItem is ComboBoxItem item && item.Tag is string courseId)
        {
            var course = DatabaseService.GetAllCourses().FirstOrDefault(c => c.Id == courseId);
            if (course != null)
            {
                AdjNewLocationInput.Text = course.Location;
                AdjNewSpanCombo.SelectedIndex = Math.Clamp(course.SectionSpan - 1, 0, AdjNewSpanCombo.Items.Count - 1);
                if (course.IsCustomTime && !string.IsNullOrEmpty(course.CustomStartTime))
                {
                    if (AdjCustomStartTimeInput != null) AdjCustomStartTimeInput.Text = course.CustomStartTime;
                    if (AdjCustomEndTimeInput != null) AdjCustomEndTimeInput.Text = course.CustomEndTime;
                }
            }
        }
    }

    private void OnAdjWeekOrDayChanged(object sender, SelectionChangedEventArgs? e)
    {
        UpdateAdjTargetDateText();
    }

    private void UpdateAdjTargetDateText()
    {
        if (AdjTargetDateText == null) return;
        try
        {
            string startStr = DatabaseService.GetTimetableSetting("semester_start_date", "2026-09-07");
            if (!DateTime.TryParse(startStr, out var semStart)) semStart = new DateTime(2026, 9, 7);
            int diffToMon = ((int)semStart.DayOfWeek + 6) % 7;
            semStart = semStart.AddDays(-diffToMon);

            int targetWeek = (AdjWeekCombo != null && AdjWeekCombo.SelectedIndex >= 0) ? AdjWeekCombo.SelectedIndex + 1 : _selectedTimetableWeek;
            int targetDay = (AdjNewDayCombo != null && AdjNewDayCombo.SelectedIndex >= 0) ? AdjNewDayCombo.SelectedIndex + 1 : 1;

            DateTime targetDate = semStart.AddDays((targetWeek - 1) * 7 + (targetDay - 1));
            string dayCn = targetDay switch { 1 => "周一", 2 => "周二", 3 => "周三", 4 => "周四", 5 => "周五", 6 => "周六", _ => "周日" };
            AdjTargetDateText.Text = $"{targetDate:yyyy年MM月dd日} (第{targetWeek}周 {dayCn})";
        }
        catch { }
    }

    private void OnAdjTimeModeChanged(object sender, RoutedEventArgs? e)
    {
        if (AdjCustomTimeInputsPanel == null || AdjStandardSectionInputsPanel == null) return;
        bool isCustom = AdjModeCustomTimeRadio?.IsChecked == true;
        AdjCustomTimeInputsPanel.Visibility = isCustom ? Visibility.Visible : Visibility.Collapsed;
        AdjStandardSectionInputsPanel.Visibility = isCustom ? Visibility.Collapsed : Visibility.Visible;
    }

    private void OnAdjCustomTimeInputChanged(object? sender, TextChangedEventArgs? e)
    {
        if (AdjCustomDurationTip == null || AdjCustomStartTimeInput == null || AdjCustomEndTimeInput == null) return;
        string s = AdjCustomStartTimeInput.Text.Trim();
        string end = AdjCustomEndTimeInput.Text.Trim();
        if (TimeSpan.TryParse(s, out var st) && TimeSpan.TryParse(end, out var et) && et > st)
        {
            int mins = (int)(et - st).TotalMinutes;
            int h = mins / 60;
            int m = mins % 60;
            string durStr = h > 0 ? (m > 0 ? $"{h}小时{m}分" : $"{h}小时") : $"{m}分钟";
            AdjCustomDurationTip.Text = $"时长: {mins}分钟 ({durStr})";
        }
        else
        {
            AdjCustomDurationTip.Text = "时长: 待输入有效时间";
        }
    }

    private void OnAdjQuickPresetTimeClicked(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is string tag)
        {
            var parts = tag.Split('|');
            if (parts.Length == 2)
            {
                if (AdjCustomStartTimeInput != null) AdjCustomStartTimeInput.Text = parts[0];
                if (AdjCustomEndTimeInput != null) AdjCustomEndTimeInput.Text = parts[1];
            }
        }
    }

    private void OnSaveAdjustmentClicked(object sender, RoutedEventArgs e)
    {
        string type = (AdjTypeCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "SUSPEND";
        int targetWeek = AdjWeekCombo.SelectedIndex + 1;

        string courseId = "";
        string courseName = "";
        int origDay = 1;
        int origSec = 1;

        if (type == "MAKEUP")
        {
            courseName = AdjCustomCourseNameInput.Text.Trim();
            if (string.IsNullOrWhiteSpace(courseName))
            {
                System.Windows.MessageBox.Show("请输入加课或补课的课程名称！", "提示", MessageBoxButton.OK, MessageBoxImage.Warning);
                AdjCustomCourseNameInput.Focus();
                return;
            }
        }
        else
        {
            courseId = (AdjCourseCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "";
            var origCourse = DatabaseService.GetAllCourses().FirstOrDefault(c => c.Id == courseId);
            if (origCourse == null)
            {
                System.Windows.MessageBox.Show("请选择一门有效的关联课程！", "提示", MessageBoxButton.OK, MessageBoxImage.Warning);
                return;
            }
            courseName = origCourse.Name;
            origDay = origCourse.DayOfWeek;
            origSec = origCourse.StartSection;
        }

        int newDay = AdjNewDayCombo.SelectedIndex + 1;
        bool isCustomTime = AdjModeCustomTimeRadio?.IsChecked == true;
        string customStart = AdjCustomStartTimeInput?.Text.Trim() ?? "";
        string customEnd = AdjCustomEndTimeInput?.Text.Trim() ?? "";

        int totalSections = int.TryParse(DatabaseService.GetTimetableSetting("total_sections", "12"), out int ts) ? ts : 12;
        var sectionTimes = GetConfiguredSectionTimes(totalSections);

        int newSec;
        int span;

        if (isCustomTime && (type == "RESCHEDULE" || type == "MAKEUP"))
        {
            if (!TimeSpan.TryParse(customStart, out var cs) || !TimeSpan.TryParse(customEnd, out var ce) || ce <= cs)
            {
                System.Windows.MessageBox.Show("自定义时间格式有误！请输入形如 15:30 的有效起止时间且结束时间须晚于开始时间。", "提示", MessageBoxButton.OK, MessageBoxImage.Warning);
                AdjCustomStartTimeInput?.Focus();
                return;
            }

            int closestRow = FindClosestSectionRow(customStart, sectionTimes);
            newSec = closestRow + 1;
            span = CalculateTimeSpanRows(customStart, customEnd, 2, totalSections, closestRow);
        }
        else
        {
            newSec = AdjNewStartSectionCombo.SelectedIndex + 1;
            span = AdjNewSpanCombo.SelectedIndex + 1;
            isCustomTime = false;
        }

        string newLoc = AdjNewLocationInput.Text.Trim();
        string reason = AdjReasonInput.Text.Trim();

        var adjItem = new CourseAdjustmentItem
        {
            Id = Guid.NewGuid().ToString(),
            CourseId = courseId,
            CourseName = courseName,
            AdjustmentType = type,
            TargetWeek = targetWeek,
            OrigDayOfWeek = origDay,
            OrigStartSection = origSec,
            NewDayOfWeek = newDay,
            NewStartSection = newSec,
            SectionSpan = span,
            NewLocation = newLoc,
            Reason = reason,
            IsCustomTime = isCustomTime,
            CustomStartTime = isCustomTime ? customStart : string.Empty,
            CustomEndTime = isCustomTime ? customEnd : string.Empty,
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };

        DatabaseService.UpsertCourseAdjustment(adjItem);
        RefreshAdjustmentRecordsList();
        RenderTimetablePage();
        System.Windows.MessageBox.Show("调停课规则已成功添加并生效！", "成功", MessageBoxButton.OK, MessageBoxImage.Information);
    }

    private void RefreshAdjustmentRecordsList()
    {
        AdjustmentRecordsListPanel.Children.Clear();
        var list = DatabaseService.GetAllCourseAdjustments();

        if (list.Count == 0)
        {
            AdjustmentRecordsEmptyText.Visibility = Visibility.Visible;
            return;
        }

        AdjustmentRecordsEmptyText.Visibility = Visibility.Collapsed;

        foreach (var item in list)
        {
            var border = new Border
            {
                Background = ThemeService.CurrentControlBrush,
                BorderBrush = ThemeService.CurrentBorderBrush,
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(6),
                Padding = new Thickness(10, 8, 10, 8),
                Margin = new Thickness(0, 0, 0, 8)
            };

            var grid = new Grid();
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            var stack = new StackPanel();

            string typeTag = item.AdjustmentType switch
            {
                "SUSPEND" => "⏸️ 停课",
                "RESCHEDULE" => "🔀 调课",
                "MAKEUP" => "➕ 补课",
                _ => "调停课"
            };

            var titleBlock = new TextBlock
            {
                Text = $"第 {item.TargetWeek} 周 · {typeTag} · 《{item.CourseName}》",
                FontSize = 12,
                FontWeight = FontWeights.SemiBold,
                Foreground = item.AdjustmentType == "SUSPEND" ? new SolidColorBrush(Color.FromRgb(0xEF, 0x44, 0x44))
                           : (item.AdjustmentType == "RESCHEDULE" ? new SolidColorBrush(Color.FromRgb(0xF5, 0x9E, 0x0B))
                           : new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81)))
            };
            stack.Children.Add(titleBlock);

            string targetTimeDesc = item.IsCustomTime && !string.IsNullOrEmpty(item.CustomStartTime)
                ? $"⏰ {item.CustomStartTime}-{item.CustomEndTime} (自由时间)"
                : $"第{item.NewStartSection}节 ({item.SectionSpan}节)";

            string detail = item.AdjustmentType switch
            {
                "SUSPEND" => $"原时间: 周{item.OrigDayOfWeek} 第{item.OrigStartSection}节 | 说明: {item.Reason}",
                "RESCHEDULE" => $"原: 周{item.OrigDayOfWeek}第{item.OrigStartSection}节 ➔ 调至: 周{item.NewDayOfWeek} {targetTimeDesc} | 地点: {(string.IsNullOrEmpty(item.NewLocation) ? "原教室" : item.NewLocation)} | 说明: {item.Reason}",
                "MAKEUP" => $"时间: 周{item.NewDayOfWeek} {targetTimeDesc} | 地点: {item.NewLocation} | 说明: {item.Reason}",
                _ => item.Reason
            };

            var detailBlock = new TextBlock
            {
                Text = detail,
                FontSize = 10.5,
                Foreground = ThemeService.CurrentSecondaryTextBrush,
                Margin = new Thickness(0, 3, 0, 0),
                TextWrapping = TextWrapping.Wrap
            };
            stack.Children.Add(detailBlock);

            Grid.SetColumn(stack, 0);
            grid.Children.Add(stack);

            var delBtn = new Button
            {
                Content = "✕ 撤销",
                Background = Brushes.Transparent,
                Foreground = new SolidColorBrush(Color.FromRgb(0xEF, 0x44, 0x44)),
                BorderThickness = new Thickness(0),
                Padding = new Thickness(6, 2, 6, 2),
                FontSize = 11,
                Cursor = Cursors.Hand,
                VerticalAlignment = VerticalAlignment.Center
            };
            string adjId = item.Id;
            delBtn.Click += (s, e) =>
            {
                DatabaseService.DeleteCourseAdjustment(adjId);
                RefreshAdjustmentRecordsList();
                RenderTimetablePage();
            };
            Grid.SetColumn(delBtn, 1);
            grid.Children.Add(delBtn);

            border.Child = grid;
            AdjustmentRecordsListPanel.Children.Add(border);
        }
    }

    // =========================================================================
    // ================= DAILY ACHIEVEMENT REPORT LOGIC (每日成果汇报) =========
    // =========================================================================

    private DateTime _dailyReportSelectedDate = DateTime.Today;

    private void OnRefreshDailyReportClicked(object sender, RoutedEventArgs e)
    {
        RenderDailyReportPage();
    }

    private void OnDailyReportPrevDayClicked(object sender, RoutedEventArgs e)
    {
        _dailyReportSelectedDate = _dailyReportSelectedDate.AddDays(-1);
        RenderDailyReportPage();
    }

    private void OnDailyReportTodayClicked(object sender, RoutedEventArgs e)
    {
        _dailyReportSelectedDate = DateTime.Today;
        RenderDailyReportPage();
    }

    private void OnDailyReportNextDayClicked(object sender, RoutedEventArgs e)
    {
        _dailyReportSelectedDate = _dailyReportSelectedDate.AddDays(1);
        RenderDailyReportPage();
    }

    private void OnDailyReportDatePickerChanged(object sender, SelectionChangedEventArgs e)
    {
        if (DailyReportDatePicker.SelectedDate.HasValue && DailyReportDatePicker.SelectedDate.Value.Date != _dailyReportSelectedDate.Date)
        {
            _dailyReportSelectedDate = DailyReportDatePicker.SelectedDate.Value.Date;
            RenderDailyReportPage();
        }
    }

    private void RenderDailyReportPage()
    {
        if (DailyReportPageContainer == null) return;

        var targetDate = _dailyReportSelectedDate.Date;
        var culture = new CultureInfo("zh-CN");

        // 1. 同步顶部日期标题与选择器
        DailyReportCurrentDateText.Text = $"{targetDate:yyyy年M月d日} {targetDate.ToString("dddd", culture)}";
        DailyReportDateTitleBadge.Text = $"{targetDate:yyyy年M月d日} · 成果对账";
        if (DailyReportDatePicker.SelectedDate != targetDate)
        {
            DailyReportDatePicker.SelectedDate = targetDate;
        }

        // 2. 查询当日日程安排 (包含重复事件投影)
        var daySchedules = DatabaseService.GetSchedulesForDateRange(targetDate, targetDate.AddDays(1))
            .Where(s => !s.IsBacklog && !s.IsDeferred)
            .OrderBy(s => s.StartTime)
            .ToList();

        // 3. 统计核心指标
        int totalTasks = daySchedules.Count;
        var completedList = daySchedules.Where(s => s.Status.Equals("COMPLETED", StringComparison.OrdinalIgnoreCase)).ToList();
        var pendingList = daySchedules.Where(s => !s.Status.Equals("COMPLETED", StringComparison.OrdinalIgnoreCase)).ToList();

        double completionRate = totalTasks > 0 ? (completedList.Count * 100.0 / totalTasks) : 0.0;

        double deepHours = 0.0;
        double shallowHours = 0.0;
        double restHours = 0.0;
        int interruptMinutes = 0;
        int dodTotal = 0;
        int dodCompleted = 0;

        foreach (var item in daySchedules)
        {
            double durationMins = item.ActualMinutes > 0 ? item.ActualMinutes :
                (item.EndTime > item.StartTime ? (item.EndTime - item.StartTime).TotalMinutes : item.EstimatedMinutes);

            if (item.WorkType == "DEEP_WORK") deepHours += durationMins / 60.0;
            else if (item.WorkType == "SHALLOW_WORK") shallowHours += durationMins / 60.0;
            else if (item.WorkType == "REST_BUFFER") restHours += durationMins / 60.0;
            else deepHours += durationMins / 60.0;

            interruptMinutes += Math.Max(0, item.InterruptionMinutes);

            if (!string.IsNullOrWhiteSpace(item.Dod))
            {
                dodTotal++;
                if (item.Status.Equals("COMPLETED", StringComparison.OrdinalIgnoreCase))
                {
                    dodCompleted++;
                }
            }
        }

        double totalHours = deepHours + shallowHours + restHours;
        double deepPurity = totalHours > 0 ? (deepHours * 100.0 / totalHours) : 0.0;
        double dodRate = dodTotal > 0 ? (dodCompleted * 100.0 / dodTotal) : 0.0;
        int focusScore = Math.Max(0, 100 - (interruptMinutes / 2));

        // 更新 KPI 卡片
        DailyReportCompletionRateText.Text = $"{completionRate:0.0}%";
        DailyReportCompletionProgressBar.Value = completionRate;
        DailyReportTaskCountText.Text = $"已交付 {completedList.Count} 项 / 今日总计 {totalTasks} 项";

        DailyReportDeepHoursText.Text = $"{deepHours:0.0} h";
        DailyReportDeepPurityProgressBar.Value = deepPurity;
        DailyReportDeepPurityText.Text = $"浅层 {shallowHours:0.0} h · 休息 {restHours:0.0} h";

        DailyReportDodRateText.Text = $"{dodRate:0.0}%";
        DailyReportDodProgressBar.Value = dodRate;
        DailyReportDodCountText.Text = $"达标 {dodCompleted} 项 / 设限 {dodTotal} 项";

        DailyReportFocusScoreText.Text = $"{focusScore} 分";
        DailyReportFocusProgressBar.Value = focusScore;
        DailyReportInterruptLossText.Text = $"打断损耗 {interruptMinutes} 分钟";

        DailyReportQuickSummaryTag.Text = $"已交付 {completedList.Count} 项 · 深度工时 {deepHours:0.0}h";

        // 更新模块 1 指标看板胶囊与描述
        if (DailyReportSec1DeepRun != null) DailyReportSec1DeepRun.Text = $"{deepHours:0.0} h";
        if (DailyReportSec1ShallowRun != null) DailyReportSec1ShallowRun.Text = $"{shallowHours:0.0} h";
        if (DailyReportSec1DeliverRun != null) DailyReportSec1DeliverRun.Text = $"{(totalTasks > 0 ? (completedList.Count * 100.0 / totalTasks).ToString("0.0") : "100.0")}% ({completedList.Count}/{totalTasks})";
        if (DailyReportSec1DodRun != null) DailyReportSec1DodRun.Text = $"{dodCompleted}/{dodTotal} 项";
        if (DailyReportSec1FocusRun != null) DailyReportSec1FocusRun.Text = $"{focusScore}分" + (interruptMinutes > 0 ? $" (打断{interruptMinutes}m)" : " · 零打断");

        var sbMetrics = new StringBuilder();
        sbMetrics.AppendLine($"• 深度工作投入：{deepHours:0.0} 小时 · 浅层事务：{shallowHours:0.0} 小时 · 休息缓冲：{restHours:0.0} 小时");
        sbMetrics.AppendLine($"• 任务交付完成率：{(totalTasks > 0 ? (completedList.Count * 100.0 / totalTasks).ToString("0.0") : "100.0")}% (交付 {completedList.Count} 项 / 总计 {totalTasks} 项)");
        sbMetrics.AppendLine($"• DoD 验收标准达成：{dodCompleted}/{dodTotal} 项");
        sbMetrics.Append(interruptMinutes > 0 ? $"• 外部打断与管理损耗：{interruptMinutes} 分钟" : "• 专注保持极高，今日零打断损耗");
        string metricsText = sbMetrics.ToString();
        if (DailyReportSec1MetricsTextBlock != null) DailyReportSec1MetricsTextBlock.Text = metricsText;
        if (DailyReportSecMetricsTextBox != null) DailyReportSecMetricsTextBox.Text = metricsText;

        // 4. 渲染左侧已完成卡片清单 (Key Wins)
        DailyReportCompletedItemsPanel.Children.Clear();
        DailyReportCompletedHeaderBadge.Text = $"共 {completedList.Count} 项交付";
        DailyReportCompletedEmptyText.Visibility = completedList.Count == 0 ? Visibility.Visible : Visibility.Collapsed;

        foreach (var item in completedList)
        {
            DailyReportCompletedItemsPanel.Children.Add(BuildDailyReportItemCard(item, isCompleted: true));
        }

        // 5. 渲染左侧未完成与明日规划 (Carryover & Next Steps)
        DailyReportPendingItemsPanel.Children.Clear();
        DailyReportPendingHeaderBadge.Text = $"待跟进 {pendingList.Count} 项";
        DailyReportPendingEmptyText.Visibility = pendingList.Count == 0 ? Visibility.Visible : Visibility.Collapsed;

        foreach (var item in pendingList)
        {
            DailyReportPendingItemsPanel.Children.Add(BuildDailyReportItemCard(item, isCompleted: false));
        }

        // 6. 加载已保存的汇报文本与个人心得
        string dateKey = targetDate.ToString("yyyy-MM-dd");
        var savedRecord = DatabaseService.GetDailyReport(dateKey);

        if (savedRecord != null && !string.IsNullOrWhiteSpace(savedRecord.AiSummary))
        {
            ParseAndLoadSections(savedRecord.AiSummary);
            DailyReportReflectionTextBox.Text = savedRecord.Reflection ?? string.Empty;
        }
        else
        {
            // 若无历史记录，保持纯净手账编辑界面（不填充丑陋的占位符假数据）
            GenerateAndPopulateModularSections(targetDate, completedList, pendingList, deepHours, shallowHours, restHours, interruptMinutes, dodCompleted, dodTotal);
            if (savedRecord != null && !string.IsNullOrWhiteSpace(savedRecord.Reflection))
            {
                DailyReportReflectionTextBox.Text = savedRecord.Reflection;
            }
        }
    }

    private void GenerateAndPopulateModularSections(DateTime date, List<ScheduleItem> completed, List<ScheduleItem> pending, double deepHours, double shallowHours, double restHours, int interruptMinutes, int dodCompleted, int dodTotal)
    {
        // 模块 2: 今日核心交付与产出 (Key Wins)
        var sb2 = new StringBuilder();
        if (completed.Count > 0)
        {
            foreach (var item in completed)
            {
                int mins = item.ActualMinutes > 0 ? item.ActualMinutes : (item.EndTime > item.StartTime ? (int)(item.EndTime - item.StartTime).TotalMinutes : item.EstimatedMinutes);
                string dodStr = !string.IsNullOrWhiteSpace(item.Dod) ? $" ➔ 【DoD达标】{item.Dod}" : "";
                sb2.AppendLine($"• [✓] {item.Title} ({mins}m, {item.StartTime:HH:mm}-{item.EndTime:HH:mm}){dodStr}");
            }
        }
        DailyReportSecWinsTextBox.Text = sb2.ToString().TrimEnd();

        // 模块 3: 递延事项与明日重点计划 (Next Steps)
        var sb3 = new StringBuilder();
        if (pending.Count > 0)
        {
            foreach (var item in pending)
            {
                sb3.AppendLine($"• [ ] {item.Title} (预估耗时: {item.EstimatedMinutes}m)");
            }
        }
        DailyReportSecNextStepsTextBox.Text = sb3.ToString().TrimEnd();

        // 模块 4: 今日复盘与认知反思 (Reflections) - 保持纯净，不硬塞机械文字
        // 留给用户自由发挥
    }

    private void ParseAndLoadSections(string serialized)
    {
        try
        {
            // 支持 JSON 格式存储或传统 Markdown 拆解
            if (serialized.TrimStart().StartsWith("{"))
            {
                using var doc = JsonDocument.Parse(serialized);
                var root = doc.RootElement;
                string loadedMetrics = root.TryGetProperty("metrics", out var m) ? m.GetString() ?? "" : "";
                if (!string.IsNullOrWhiteSpace(loadedMetrics))
                {
                    DailyReportSecMetricsTextBox.Text = loadedMetrics;
                    DailyReportSec1MetricsTextBlock.Text = loadedMetrics;
                }
                DailyReportSecWinsTextBox.Text = root.TryGetProperty("wins", out var w) ? w.GetString() ?? "" : "";
                DailyReportSecNextStepsTextBox.Text = root.TryGetProperty("next_steps", out var n) ? n.GetString() ?? "" : "";
                if (root.TryGetProperty("reflection", out var r) && !string.IsNullOrWhiteSpace(r.GetString()))
                {
                    DailyReportReflectionTextBox.Text = r.GetString()!;
                }
                return;
            }
        }
        catch { }

        // 回退兼容：若为整篇 Markdown 文本，则做智能语义拆分
        string[] parts = serialized.Split(new[] { "## ", "# " }, StringSplitOptions.RemoveEmptyEntries);
        foreach (var part in parts)
        {
            string trim = part.Trim();
            if (trim.Contains("认知工时") || trim.Contains("1."))
            {
                DailyReportSecMetricsTextBox.Text = trim;
                DailyReportSec1MetricsTextBlock.Text = trim;
            }
            else if (trim.Contains("核心交付") || trim.Contains("2."))
            {
                DailyReportSecWinsTextBox.Text = trim;
            }
            else if (trim.Contains("递延事项") || trim.Contains("3."))
            {
                DailyReportSecNextStepsTextBox.Text = trim;
            }
            else if (trim.Contains("复盘与认知反思") || trim.Contains("4."))
            {
                DailyReportReflectionTextBox.Text = trim;
            }
        }
    }

    private string ComposeFullReportMarkdown()
    {
        var sb = new StringBuilder();
        var culture = new CultureInfo("zh-CN");
        var date = _dailyReportSelectedDate.Date;
        sb.AppendLine($"# 📅 每日成果汇报 ({date:yyyy年M月d日} {date.ToString("dddd", culture)})");
        sb.AppendLine();
        sb.AppendLine("## 📊 1. 认知工时与专注指标");
        sb.AppendLine(DailyReportSecMetricsTextBox?.Text?.Trim() ?? "");
        sb.AppendLine();
        sb.AppendLine("## 🎯 2. 今日核心交付与产出 (Key Wins)");
        sb.AppendLine(DailyReportSecWinsTextBox?.Text?.Trim() ?? "");
        sb.AppendLine();
        sb.AppendLine("## ⏳ 3. 递延事项与明日重点计划 (Next Steps)");
        sb.AppendLine(DailyReportSecNextStepsTextBox?.Text?.Trim() ?? "");
        sb.AppendLine();
        sb.AppendLine("## 💡 4. 今日复盘与认知反思 (Reflections)");
        sb.AppendLine(DailyReportReflectionTextBox?.Text?.Trim() ?? "");
        return sb.ToString();
    }

    private void OnCopySection1Clicked(object sender, RoutedEventArgs e) => CopyTextToClipboard(DailyReportSecMetricsTextBox.Text, "工时与指标总结");
    private void OnCopySection2Clicked(object sender, RoutedEventArgs e) => CopyTextToClipboard(DailyReportSecWinsTextBox.Text, "核心交付产出");
    private void OnCopySection3Clicked(object sender, RoutedEventArgs e) => CopyTextToClipboard(DailyReportSecNextStepsTextBox.Text, "递延与明日计划");
    private void OnCopySection4Clicked(object sender, RoutedEventArgs e) => CopyTextToClipboard(DailyReportReflectionTextBox.Text, "今日反思心得");

    private void CopyTextToClipboard(string text, string title)
    {
        if (string.IsNullOrWhiteSpace(text))
        {
            System.Windows.MessageBox.Show("当前模块暂无内容可复制！", "提示", MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }
        try
        {
            System.Windows.Clipboard.SetText(text);
            System.Windows.MessageBox.Show($"【{title}】已成功复制到剪贴板！", "复制成功", MessageBoxButton.OK, MessageBoxImage.Information);
        }
        catch (Exception ex)
        {
            System.Windows.MessageBox.Show($"复制失败: {ex.Message}", "提示", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }

    private bool _isDailyReportPreviewMode = false;
    private void OnToggleDailyReportViewClicked(object sender, RoutedEventArgs e)
    {
        _isDailyReportPreviewMode = !_isDailyReportPreviewMode;
        if (_isDailyReportPreviewMode)
        {
            DailyReportEditorContainer.Visibility = Visibility.Collapsed;
            DailyReportPreviewContainer.Visibility = Visibility.Visible;
            DailyReportToggleViewBtn.Content = "📝 手账编辑";
            DailyReportPreviewFullTextBox.Text = ComposeFullReportMarkdown();
        }
        else
        {
            DailyReportEditorContainer.Visibility = Visibility.Visible;
            DailyReportPreviewContainer.Visibility = Visibility.Collapsed;
            DailyReportToggleViewBtn.Content = "👁️ 战报预览";
        }
    }

    private void OnExtractCompletedToWinsClicked(object sender, RoutedEventArgs e)
    {
        var targetDate = _dailyReportSelectedDate.Date;
        var completedList = DatabaseService.GetSchedulesForDateRange(targetDate, targetDate.AddDays(1))
            .Where(s => !s.IsBacklog && !s.IsDeferred && s.Status.Equals("COMPLETED", StringComparison.OrdinalIgnoreCase))
            .OrderBy(s => s.StartTime)
            .ToList();

        if (completedList.Count == 0)
        {
            System.Windows.MessageBox.Show("今日暂无标记为已完成的日程任务！可在左侧日程中勾选完成后导入。", "提示", MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }

        var sb = new StringBuilder();
        foreach (var item in completedList)
        {
            int mins = item.ActualMinutes > 0 ? item.ActualMinutes : (item.EndTime > item.StartTime ? (int)(item.EndTime - item.StartTime).TotalMinutes : item.EstimatedMinutes);
            string dodStr = !string.IsNullOrWhiteSpace(item.Dod) ? $" ➔ 【DoD达标】{item.Dod}" : "";
            sb.AppendLine($"• [✓] {item.Title} ({mins}m, {item.StartTime:HH:mm}-{item.EndTime:HH:mm}){dodStr}");
        }

        DailyReportSecWinsTextBox.Text = sb.ToString().TrimEnd();
        System.Windows.MessageBox.Show($"已将今日 {completedList.Count} 项已完成战果导入至核心产出模块！", "导入成功", MessageBoxButton.OK, MessageBoxImage.Information);
    }

    private void OnExtractPendingToNextStepsClicked(object sender, RoutedEventArgs e)
    {
        var targetDate = _dailyReportSelectedDate.Date;
        var pendingList = DatabaseService.GetSchedulesForDateRange(targetDate, targetDate.AddDays(1))
            .Where(s => !s.IsBacklog && !s.IsDeferred && !s.Status.Equals("COMPLETED", StringComparison.OrdinalIgnoreCase))
            .OrderBy(s => s.StartTime)
            .ToList();

        if (pendingList.Count == 0)
        {
            System.Windows.MessageBox.Show("今日已无未结日程任务！", "提示", MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }

        var sb = new StringBuilder();
        foreach (var item in pendingList)
        {
            sb.AppendLine($"• [ ] {item.Title} (预估耗时: {item.EstimatedMinutes}m)");
        }

        DailyReportSecNextStepsTextBox.Text = sb.ToString().TrimEnd();
        System.Windows.MessageBox.Show($"已将今日 {pendingList.Count} 项待办/递延任务导入至明日计划模块！", "导入成功", MessageBoxButton.OK, MessageBoxImage.Information);
    }

    private UIElement BuildDailyReportItemCard(ScheduleItem item, bool isCompleted)
    {
        var card = new Border
        {
            Background = ThemeService.CurrentCardBrush,
            BorderBrush = isCompleted ? new SolidColorBrush(Color.FromRgb(0x06, 0x5F, 0x46)) : ThemeService.CurrentBorderBrush,
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(6),
            Padding = new Thickness(12, 10, 12, 10),
            Margin = new Thickness(0, 0, 0, 8)
        };

        var mainGrid = new Grid();
        mainGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        mainGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        mainGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        // 图标徽章
        var iconBorder = new Border
        {
            Width = 26,
            Height = 26,
            CornerRadius = new CornerRadius(13),
            Background = isCompleted ? new SolidColorBrush(Color.FromRgb(0x06, 0x4E, 0x3B)) : ThemeService.CurrentControlBrush,
            VerticalAlignment = VerticalAlignment.Top,
            Margin = new Thickness(0, 2, 10, 0)
        };
        var iconText = new TextBlock
        {
            Text = isCompleted ? "✓" : "○",
            FontSize = 13,
            FontWeight = FontWeights.Bold,
            Foreground = isCompleted ? new SolidColorBrush(Color.FromRgb(0x34, 0xD3, 0x99)) : ThemeService.CurrentSecondaryTextBrush,
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center
        };
        iconBorder.Child = iconText;
        Grid.SetColumn(iconBorder, 0);
        mainGrid.Children.Add(iconBorder);

        // 主体信息
        var contentStack = new StackPanel();

        // 标题与耗时
        var titleRow = new WrapPanel { VerticalAlignment = VerticalAlignment.Center };
        var titleBlock = new TextBlock
        {
            Text = item.Title,
            FontSize = 13,
            FontWeight = FontWeights.SemiBold,
            Foreground = isCompleted ? ThemeService.CurrentTextBrush : new SolidColorBrush(Color.FromRgb(0xF5, 0x9E, 0x0B)),
            Margin = new Thickness(0, 0, 8, 0),
            VerticalAlignment = VerticalAlignment.Center
        };
        titleRow.Children.Add(titleBlock);

        // 认知类型徽章
        var workTypeBadge = new Border
        {
            CornerRadius = new CornerRadius(3),
            Padding = new Thickness(6, 1, 6, 1),
            Margin = new Thickness(0, 0, 6, 0),
            Background = item.WorkType == "DEEP_WORK" ? new SolidColorBrush(Color.FromRgb(0x2E, 0x10, 0x65))
                       : (item.WorkType == "SHALLOW_WORK" ? new SolidColorBrush(Color.FromRgb(0x1E, 0x29, 0x3B))
                       : new SolidColorBrush(Color.FromRgb(0x06, 0x4E, 0x3B)))
        };
        var workTypeText = new TextBlock
        {
            Text = item.WorkType == "DEEP_WORK" ? "🎯 深度" : (item.WorkType == "SHALLOW_WORK" ? "⚡ 浅层" : "☕ 休息"),
            FontSize = 10,
            Foreground = item.WorkType == "DEEP_WORK" ? new SolidColorBrush(Color.FromRgb(0xC0, 0x84, 0xFC))
                       : (item.WorkType == "SHALLOW_WORK" ? new SolidColorBrush(Color.FromRgb(0x38, 0xBD, 0xF8))
                       : new SolidColorBrush(Color.FromRgb(0x34, 0xD3, 0x99)))
        };
        workTypeBadge.Child = workTypeText;
        titleRow.Children.Add(workTypeBadge);

        contentStack.Children.Add(titleRow);

        // 时间范围与工时
        int mins = item.ActualMinutes > 0 ? item.ActualMinutes : (item.EndTime > item.StartTime ? (int)(item.EndTime - item.StartTime).TotalMinutes : item.EstimatedMinutes);
        var timeInfoBlock = new TextBlock
        {
            Text = $"⏰ {item.StartTime:HH:mm} - {item.EndTime:HH:mm} · 耗时: {mins} 分钟" + (item.InterruptionMinutes > 0 ? $" · 打断: {item.InterruptionMinutes}m" : ""),
            FontSize = 11,
            Foreground = ThemeService.CurrentSecondaryTextBrush,
            Margin = new Thickness(0, 3, 0, 0)
        };
        contentStack.Children.Add(timeInfoBlock);

        // Definition of Done (验收标准)
        if (!string.IsNullOrWhiteSpace(item.Dod))
        {
            var dodBorder = new Border
            {
                Background = ThemeService.CurrentControlBrush,
                BorderBrush = ThemeService.CurrentBorderBrush,
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(4),
                Padding = new Thickness(8, 4, 8, 4),
                Margin = new Thickness(0, 6, 0, 0)
            };
            var dodBlock = new TextBlock
            {
                Text = $"📋 验收成果 (DoD): {item.Dod}",
                FontSize = 11,
                Foreground = ThemeService.IsCurrentDark ? new SolidColorBrush(Color.FromRgb(0xBA, 0xE6, 0xFD)) : new SolidColorBrush(Color.FromRgb(0x03, 0x69, 0xA1)),
                TextWrapping = TextWrapping.Wrap
            };
            dodBorder.Child = dodBlock;
            contentStack.Children.Add(dodBorder);
        }

        // 描述或心得
        if (!string.IsNullOrWhiteSpace(item.Description))
        {
            var descBlock = new TextBlock
            {
                Text = item.Description,
                FontSize = 11,
                Foreground = ThemeService.CurrentMutedTextBrush,
                Margin = new Thickness(0, 4, 0, 0),
                TextWrapping = TextWrapping.Wrap
            };
            contentStack.Children.Add(descBlock);
        }

        Grid.SetColumn(contentStack, 1);
        mainGrid.Children.Add(contentStack);

        card.Child = mainGrid;
        return card;
    }

    private void OnGenerateDailyReportClicked(object sender, RoutedEventArgs e)
    {
        var targetDate = _dailyReportSelectedDate.Date;
        var daySchedules = DatabaseService.GetSchedulesForDateRange(targetDate, targetDate.AddDays(1))
            .Where(s => !s.IsBacklog && !s.IsDeferred)
            .OrderBy(s => s.StartTime)
            .ToList();

        var completedList = daySchedules.Where(s => s.Status.Equals("COMPLETED", StringComparison.OrdinalIgnoreCase)).ToList();
        var pendingList = daySchedules.Where(s => !s.Status.Equals("COMPLETED", StringComparison.OrdinalIgnoreCase)).ToList();

        double deepHours = 0.0;
        double shallowHours = 0.0;
        double restHours = 0.0;
        int interruptMinutes = 0;
        int dodTotal = 0;
        int dodCompleted = 0;

        foreach (var item in daySchedules)
        {
            double durationMins = item.ActualMinutes > 0 ? item.ActualMinutes :
                (item.EndTime > item.StartTime ? (item.EndTime - item.StartTime).TotalMinutes : item.EstimatedMinutes);

            if (item.WorkType == "DEEP_WORK") deepHours += durationMins / 60.0;
            else if (item.WorkType == "SHALLOW_WORK") shallowHours += durationMins / 60.0;
            else if (item.WorkType == "REST_BUFFER") restHours += durationMins / 60.0;
            else deepHours += durationMins / 60.0;

            interruptMinutes += Math.Max(0, item.InterruptionMinutes);

            if (!string.IsNullOrWhiteSpace(item.Dod))
            {
                dodTotal++;
                if (item.Status.Equals("COMPLETED", StringComparison.OrdinalIgnoreCase)) dodCompleted++;
            }
        }

        GenerateAndPopulateModularSections(targetDate, completedList, pendingList, deepHours, shallowHours, restHours, interruptMinutes, dodCompleted, dodTotal);

        // 自动存库
        SaveDailyReportRecord();

        System.Windows.MessageBox.Show("各模块成果汇报已自动提炼归类完毕并保存！", "成果汇报生成", MessageBoxButton.OK, MessageBoxImage.Information);
    }

    private void OnCopyDailyReportClicked(object sender, RoutedEventArgs e)
    {
        string fullMarkdown = ComposeFullReportMarkdown();
        try
        {
            System.Windows.Clipboard.SetText(fullMarkdown);
            System.Windows.MessageBox.Show("全量汇报内容已复制到剪贴板！可以直接粘贴到企业微信、钉钉或周报文档中。", "复制成功", MessageBoxButton.OK, MessageBoxImage.Information);
        }
        catch (Exception ex)
        {
            System.Windows.MessageBox.Show($"复制失败: {ex.Message}", "提示", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }

    private void OnSaveDailyReportContentClicked(object sender, RoutedEventArgs e)
    {
        SaveDailyReportRecord();
        System.Windows.MessageBox.Show("所有模块内容与心得笔记已成功保存至本地数据库！", "保存成功", MessageBoxButton.OK, MessageBoxImage.Information);
    }

    private void SaveDailyReportRecord()
    {
        string dateKey = _dailyReportSelectedDate.Date.ToString("yyyy-MM-dd");
        var existing = DatabaseService.GetDailyReport(dateKey);

        // 以模块化 JSON 结构保存，同时保持格式干净
        var payload = new
        {
            metrics = DailyReportSecMetricsTextBox?.Text?.Trim() ?? "",
            wins = DailyReportSecWinsTextBox?.Text?.Trim() ?? "",
            next_steps = DailyReportSecNextStepsTextBox?.Text?.Trim() ?? "",
            reflection = DailyReportReflectionTextBox?.Text?.Trim() ?? ""
        };
        string json = JsonSerializer.Serialize(payload);

        var report = new DailyReportItem
        {
            Date = dateKey,
            AiSummary = json,
            Reflection = DailyReportReflectionTextBox?.Text ?? string.Empty,
            CreatedAt = existing?.CreatedAt ?? DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };

        DatabaseService.UpsertDailyReport(report);
    }

    // =========================================================================
    // ================== FOCUS MANAGEMENT & CAPSULE WORKSPACE ==================
    // =========================================================================

    private int _focusSelectedIdleMinutes = 5;

    private void OnRefreshFocusPageClicked(object sender, RoutedEventArgs e)
    {
        RenderFocusPage();
    }

    private void OnFocusPageLaunchHudClicked(object sender, RoutedEventArgs e)
    {
        if (FocusHudWindow.Current != null && FocusHudWindow.Current.IsLoaded)
        {
            FocusHudWindow.Current.WindowState = WindowState.Normal;
            FocusHudWindow.Current.Activate();
            FocusHudWindow.Current.Topmost = true;
            return;
        }

        var todaySchedules = DatabaseService.GetTodaySchedules();
        var target = todaySchedules.FirstOrDefault(s => s.Status == "IN_PROGRESS")
                     ?? todaySchedules.FirstOrDefault(s => s.Status != "COMPLETED");

        if (target != null)
        {
            LaunchFocusHudForSchedule(target);
        }
        else
        {
            int mins = ConfigService.Current.FocusDefaultDurationMinutes;
            var schedule = new ScheduleItem
            {
                Id = Guid.NewGuid().ToString(),
                Title = "深度专注时段",
                Category = "攻坚",
                StartTime = DateTime.Now,
                EndTime = DateTime.Now.AddMinutes(mins),
                EstimatedMinutes = mins,
                ActualMinutes = 0,
                Status = "IN_PROGRESS"
            };
            DatabaseService.UpsertSchedule(schedule);
            LaunchFocusHudForSchedule(schedule, mins);
        }
        RenderFocusPage();
    }

    private void OnFocusPageBringHudToFrontClicked(object sender, RoutedEventArgs e)
    {
        if (FocusHudWindow.Current != null && FocusHudWindow.Current.IsLoaded)
        {
            FocusHudWindow.Current.WindowState = WindowState.Normal;
            FocusHudWindow.Current.Activate();
            FocusHudWindow.Current.Topmost = true;
        }
        else
        {
            OnFocusPageLaunchHudClicked(sender, e);
        }
    }

    private void OnFocusPageTogglePauseClicked(object sender, RoutedEventArgs e)
    {
        if (FocusHudWindow.Current != null && FocusHudWindow.Current.IsLoaded)
        {
            FocusHudWindow.Current.TogglePlayPause();
            UpdateFocusLiveMonitor();
            UpdateFocusKpis();
        }
        else
        {
            MessageBox.Show("桌面专注胶囊尚未启动，请先点击启动或绑定日程。", "提示", MessageBoxButton.OK, MessageBoxImage.Information);
        }
    }

    private void OnFocusPageFinishSessionClicked(object sender, RoutedEventArgs e)
    {
        if (FocusHudWindow.Current != null && FocusHudWindow.Current.IsLoaded)
        {
            FocusHudWindow.Current.FinishTask();
            RenderFocusPage();
        }
        else
        {
            MessageBox.Show("桌面专注胶囊尚未启动。", "提示", MessageBoxButton.OK, MessageBoxImage.Information);
        }
    }

    private void OnFocusBindingTabChanged(object sender, RoutedEventArgs e)
    {
        RenderFocusBindableList();
    }

    private void OnFocusQuickCustomTitleChanged(object sender, TextChangedEventArgs e)
    {
        if (FocusQuickCustomTitlePlaceholder != null)
        {
            FocusQuickCustomTitlePlaceholder.Visibility = string.IsNullOrEmpty(FocusQuickCustomTitleInput.Text)
                ? Visibility.Visible
                : Visibility.Collapsed;
        }
    }

    private void OnFocusQuickCustomLaunchClicked(object sender, RoutedEventArgs e)
    {
        string title = FocusQuickCustomTitleInput.Text?.Trim() ?? "";
        if (string.IsNullOrEmpty(title))
        {
            title = "自主深度攻坚专注";
        }

        if (!int.TryParse(FocusQuickCustomMinsInput.Text?.Trim() ?? "", out int mins) || mins <= 0)
        {
            mins = ConfigService.Current.FocusDefaultDurationMinutes;
        }

        var schedule = new ScheduleItem
        {
            Id = Guid.NewGuid().ToString(),
            Title = title,
            Category = "攻坚",
            StartTime = DateTime.Now,
            EndTime = DateTime.Now.AddMinutes(mins),
            EstimatedMinutes = mins,
            ActualMinutes = 0,
            Status = "IN_PROGRESS"
        };

        DatabaseService.UpsertSchedule(schedule);
        LaunchFocusHudForSchedule(schedule, mins);
        FocusQuickCustomTitleInput.Clear();
        RenderFocusPage();
    }

    private void OnFocusPresetDurationClicked(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is string tag)
        {
            FocusSettingCustomMinsInput.Text = tag;
        }
    }

    private void OnFocusPresetBreakClicked(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is string tag)
        {
            FocusSettingBreakMinsInput.Text = tag;
        }
    }

    private void OnFocusPresetIdleClicked(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is string tag && int.TryParse(tag, out int idleMins))
        {
            _focusSelectedIdleMinutes = idleMins;
            MessageBox.Show($"已选择无操作闲置挂起阈值: {idleMins} 分钟。请点击下方“保存专注偏好配置”生效。", "偏好调整", MessageBoxButton.OK, MessageBoxImage.Information);
        }
    }

    private void OnFocusSaveConfigClicked(object sender, RoutedEventArgs e)
    {
        try
        {
            var cfg = ConfigService.Current;
            if (int.TryParse(FocusSettingCustomMinsInput.Text?.Trim() ?? "", out int durMins) && durMins > 0)
            {
                cfg.FocusDefaultDurationMinutes = durMins;
            }
            if (int.TryParse(FocusSettingBreakMinsInput.Text?.Trim() ?? "", out int breakMins) && breakMins > 0)
            {
                cfg.FocusBreakDurationMinutes = breakMins;
            }

            cfg.FocusDefaultMode = FocusModeStopwatchRadio.IsChecked == true ? "STOPWATCH" : "POMODORO";
            cfg.FocusIdleThresholdMinutes = _focusSelectedIdleMinutes;

            ConfigService.Save(cfg);
            MessageBox.Show("专注配置与偏好设置已成功保存！新发起的专注胶囊将按此配置执行。", "配置已保存", MessageBoxButton.OK, MessageBoxImage.Information);
            RenderFocusPage();
        }
        catch (Exception ex)
        {
            MessageBox.Show($"保存专注配置失败: {ex.Message}", "保存失败", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }

    private void RenderFocusPage()
    {
        if (FocusPageContainer == null) return;

        var cfg = ConfigService.Current;
        _focusSelectedIdleMinutes = cfg.FocusIdleThresholdMinutes;

        if (FocusSettingCustomMinsInput != null) FocusSettingCustomMinsInput.Text = cfg.FocusDefaultDurationMinutes.ToString();
        if (FocusSettingBreakMinsInput != null) FocusSettingBreakMinsInput.Text = cfg.FocusBreakDurationMinutes.ToString();
        if (FocusModePomodoroRadio != null) FocusModePomodoroRadio.IsChecked = cfg.FocusDefaultMode != "STOPWATCH";
        if (FocusModeStopwatchRadio != null) FocusModeStopwatchRadio.IsChecked = cfg.FocusDefaultMode == "STOPWATCH";

        UpdateFocusKpis();
        UpdateFocusLiveMonitor();
        RenderFocusBindableList();
        RenderFocusInterruptions();
    }

    private void UpdateFocusKpis()
    {
        try
        {
            var todaySchedules = DatabaseService.GetTodaySchedules();
            int totalMinutes = todaySchedules.Where(s => s.ActualMinutes > 0).Sum(s => s.ActualMinutes);
            if (FocusHudWindow.Current != null && FocusHudWindow.Current.IsLoaded)
            {
                totalMinutes += FocusHudWindow.Current.ElapsedSeconds / 60;
            }

            if (FocusKpiTodayDurationText != null)
            {
                FocusKpiTodayDurationText.Text = totalMinutes >= 60
                    ? $"{totalMinutes} 分钟 ({(totalMinutes / 60.0):0.1}小时)"
                    : $"{totalMinutes} 分钟";
            }

            int completedRounds = todaySchedules.Count(s => s.Status == "COMPLETED" && s.ActualMinutes > 0);
            if (FocusKpiCompletedRoundsText != null)
            {
                FocusKpiCompletedRoundsText.Text = $"{completedRounds} 轮";
            }

            if (FocusKpiActiveTaskText != null && FocusKpiActiveStatusText != null)
            {
                if (FocusHudWindow.Current != null && FocusHudWindow.Current.IsLoaded && FocusHudWindow.Current.Schedule != null)
                {
                    FocusKpiActiveTaskText.Text = FocusHudWindow.Current.Schedule.Title;
                    FocusKpiActiveStatusText.Text = FocusHudWindow.Current.IsPaused ? "⏸ 暂停中" : "🟢 深度专注中";
                    FocusKpiActiveStatusText.Foreground = FocusHudWindow.Current.IsPaused
                        ? new SolidColorBrush(Color.FromRgb(0xF5, 0x9E, 0x0B))
                        : new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
                }
                else
                {
                    FocusKpiActiveTaskText.Text = "未绑定 / 就绪";
                    FocusKpiActiveStatusText.Text = "⚪ 待命";
                    FocusKpiActiveStatusText.Foreground = (Brush)FindResource("TextSecondary");
                }
            }

            var interruptions = DatabaseService.GetInterruptionsForDateRange(DateTime.Today, DateTime.Today.AddDays(1));
            int lossMins = (int)Math.Ceiling(interruptions.Sum(i => i.DurationSeconds) / 60.0);
            if (FocusKpiInterruptionText != null)
            {
                FocusKpiInterruptionText.Text = $"{interruptions.Count} 次 / {lossMins} 分钟";
            }
            if (FocusInterruptionCountBadge != null)
            {
                FocusInterruptionCountBadge.Text = $"{interruptions.Count} 次打断 ({lossMins} 分钟损耗)";
            }
        }
        catch { }
    }

    private void UpdateFocusLiveMonitor()
    {
        if (FocusLiveDot == null) return;

        if (FocusHudWindow.Current != null && FocusHudWindow.Current.IsLoaded)
        {
            var hud = FocusHudWindow.Current;
            bool isPaused = hud.IsPaused;

            FocusLiveDot.Fill = isPaused
                ? new SolidColorBrush(Color.FromRgb(0xF5, 0x9E, 0x0B))
                : new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));

            FocusLiveBadge.Background = isPaused
                ? new SolidColorBrush(Color.FromRgb(0x45, 0x2A, 0x05))
                : new SolidColorBrush(Color.FromRgb(0x06, 0x4E, 0x3B));

            FocusLiveBadgeText.Text = isPaused ? "⏸ 已暂停" : "🟢 正在专注";
            FocusLiveBadgeText.Foreground = isPaused
                ? new SolidColorBrush(Color.FromRgb(0xFB, 0xBF, 0x24))
                : new SolidColorBrush(Color.FromRgb(0x34, 0xD3, 0x99));

            string taskTitle = hud.Schedule?.Title ?? "未命名攻坚任务";
            FocusLiveBoundTitle.Text = taskTitle;

            var dodList = DataLineageService.ParseDodItems(hud.Schedule?.Dod ?? "");
            int doneDod = dodList.Count(d => d.Done);
            string dodText = $"验收标准 DoD: {doneDod}/{dodList.Count} 已完成";
            if (!string.IsNullOrEmpty(hud.Schedule?.GoalId))
            {
                dodText += " · 🎯 目标OKR联动中";
            }
            FocusLiveBoundDodInfo.Text = dodText;

            int displaySecs = hud.IsPomodoro ? hud.RemainingSeconds : hud.ElapsedSeconds;
            if (displaySecs < 0) displaySecs = 0;
            FocusLiveTimerLarge.Text = $"{displaySecs / 60:D2}:{displaySecs % 60:D2}";
            FocusLiveTimerLarge.Foreground = isPaused
                ? new SolidColorBrush(Color.FromRgb(0xF5, 0x9E, 0x0B))
                : new SolidColorBrush(Color.FromRgb(0x38, 0xBD, 0xF8));

            FocusLiveModeIndicator.Text = hud.IsPomodoro ? "番茄倒计时模式" : "正向秒表计时模式";

            FocusLiveBringHudBtn.IsEnabled = true;
            FocusLivePauseBtn.IsEnabled = true;
            FocusLiveFinishBtn.IsEnabled = true;
            FocusLivePauseBtn.Content = isPaused ? "▶ 继续专注" : "⏸ 暂停计时";
        }
        else
        {
            FocusLiveDot.Fill = new SolidColorBrush(Color.FromRgb(0x94, 0xA3, 0xB8));
            FocusLiveBadge.Background = new SolidColorBrush(Color.FromRgb(0x26, 0x33, 0x4D));
            FocusLiveBadgeText.Text = "⚪ 胶囊未启动";
            FocusLiveBadgeText.Foreground = new SolidColorBrush(Color.FromRgb(0x94, 0xA3, 0xB8));

            FocusLiveBoundTitle.Text = "暂无运行中的专注任务，请在下方选择日程绑定启动";
            FocusLiveBoundDodInfo.Text = "验收标准 DoD: 0/0 已完成";
            FocusLiveTimerLarge.Text = $"{ConfigService.Current.FocusDefaultDurationMinutes:D2}:00";
            FocusLiveTimerLarge.Foreground = (Brush)FindResource("TextSecondary");
            FocusLiveModeIndicator.Text = ConfigService.Current.FocusDefaultMode == "STOPWATCH" ? "正向秒表计时模式" : "番茄倒计时模式";

            FocusLiveBringHudBtn.IsEnabled = false;
            FocusLivePauseBtn.IsEnabled = false;
            FocusLiveFinishBtn.IsEnabled = false;
            FocusLivePauseBtn.Content = "⏸ 暂停/继续";
        }
    }

    private void RenderFocusBindableList()
    {
        if (FocusBindableItemsPanel == null) return;
        FocusBindableItemsPanel.Children.Clear();

        if (FocusTabTodaySchedulesRadio.IsChecked == true)
        {
            var schedules = DatabaseService.GetTodaySchedules().OrderBy(s => s.StartTime).ToList();
            if (schedules.Count == 0)
            {
                var emptyBlock = new TextBlock
                {
                    Text = "今日暂无已安排日程，可在上方自定义快速发起攻坚，或前往日程视图添加。",
                    Foreground = (Brush)FindResource("TextSecondary"),
                    FontSize = 12,
                    Padding = new Thickness(12, 16, 12, 16),
                    HorizontalAlignment = HorizontalAlignment.Center
                };
                FocusBindableItemsPanel.Children.Add(emptyBlock);
                return;
            }

            foreach (var item in schedules)
            {
                var card = new Border
                {
                    Background = (Brush)FindResource("InputBackground"),
                    BorderBrush = (Brush)FindResource("CardBorderBrush"),
                    BorderThickness = new Thickness(1),
                    CornerRadius = new CornerRadius(6),
                    Padding = new Thickness(12, 10, 12, 10),
                    Margin = new Thickness(0, 0, 0, 8)
                };

                var grid = new Grid();
                grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

                var leftStack = new StackPanel { VerticalAlignment = VerticalAlignment.Center };

                var titleRow = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 4) };
                var titleBlock = new TextBlock
                {
                    Text = item.Title,
                    FontSize = 13,
                    FontWeight = FontWeights.SemiBold,
                    Foreground = (Brush)FindResource("TextPrimary"),
                    TextTrimming = TextTrimming.CharacterEllipsis,
                    MaxWidth = 420
                };
                titleRow.Children.Add(titleBlock);

                if (!string.IsNullOrEmpty(item.Category))
                {
                    var catBorder = new Border
                    {
                        Background = new SolidColorBrush(Color.FromArgb(40, 56, 189, 248)),
                        CornerRadius = new CornerRadius(4),
                        Padding = new Thickness(6, 1, 6, 1),
                        Margin = new Thickness(8, 0, 0, 0),
                        VerticalAlignment = VerticalAlignment.Center
                    };
                    catBorder.Child = new TextBlock
                    {
                        Text = item.Category,
                        FontSize = 10.5,
                        Foreground = new SolidColorBrush(Color.FromRgb(0x38, 0xBD, 0xF8))
                    };
                    titleRow.Children.Add(catBorder);
                }

                leftStack.Children.Add(titleRow);

                var infoRow = new StackPanel { Orientation = Orientation.Horizontal };
                string timeStr = $"{item.StartTime:HH:mm} - {item.EndTime:HH:mm}";
                infoRow.Children.Add(new TextBlock
                {
                    Text = $"🕒 {timeStr}  |  预估: {item.EstimatedMinutes}m  |  实录: {item.ActualMinutes}m",
                    FontSize = 11,
                    Foreground = (Brush)FindResource("TextSecondary"),
                    Margin = new Thickness(0, 0, 8, 0)
                });

                var dodList = DataLineageService.ParseDodItems(item.Dod ?? "");
                if (dodList.Count > 0)
                {
                    int doneDod = dodList.Count(d => d.Done);
                    infoRow.Children.Add(new TextBlock
                    {
                        Text = $"|  DoD: {doneDod}/{dodList.Count}",
                        FontSize = 11,
                        Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81))
                    });
                }
                leftStack.Children.Add(infoRow);

                Grid.SetColumn(leftStack, 0);
                grid.Children.Add(leftStack);

                bool isCurrentActive = FocusHudWindow.Current != null &&
                                      FocusHudWindow.Current.IsLoaded &&
                                      FocusHudWindow.Current.Schedule?.Id == item.Id;

                var actionBtn = new Button
                {
                    Content = isCurrentActive ? "🟢 正在运行中" : "🎯 绑定并启动",
                    Style = (Style)FindResource(isCurrentActive ? "GoogleOutlineBtnStyle" : "GoogleActionBtnStyle"),
                    Padding = new Thickness(12, 5, 12, 5),
                    FontSize = 11.5,
                    FontWeight = FontWeights.SemiBold,
                    VerticalAlignment = VerticalAlignment.Center
                };

                var schedRef = item;
                actionBtn.Click += (s, e) =>
                {
                    if (isCurrentActive)
                    {
                        OnFocusPageBringHudToFrontClicked(s, e);
                    }
                    else
                    {
                        LaunchFocusHudForSchedule(schedRef);
                        RenderFocusPage();
                    }
                };

                Grid.SetColumn(actionBtn, 1);
                grid.Children.Add(actionBtn);

                card.Child = grid;
                FocusBindableItemsPanel.Children.Add(card);
            }
        }
        else if (FocusTabBacklogRadio.IsChecked == true)
        {
            var backlog = DatabaseService.GetBacklogSchedules().Where(b => b.Status != "COMPLETED").ToList();
            if (backlog.Count == 0)
            {
                var emptyBlock = new TextBlock
                {
                    Text = "待办任务池暂无待处理事项，全清成就达成！",
                    Foreground = (Brush)FindResource("TextSecondary"),
                    FontSize = 12,
                    Padding = new Thickness(12, 16, 12, 16),
                    HorizontalAlignment = HorizontalAlignment.Center
                };
                FocusBindableItemsPanel.Children.Add(emptyBlock);
                return;
            }

            foreach (var item in backlog)
            {
                var card = new Border
                {
                    Background = (Brush)FindResource("InputBackground"),
                    BorderBrush = (Brush)FindResource("CardBorderBrush"),
                    BorderThickness = new Thickness(1),
                    CornerRadius = new CornerRadius(6),
                    Padding = new Thickness(12, 10, 12, 10),
                    Margin = new Thickness(0, 0, 0, 8)
                };

                var grid = new Grid();
                grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

                var leftStack = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
                var titleRow = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 4) };
                titleRow.Children.Add(new TextBlock
                {
                    Text = item.Title,
                    FontSize = 13,
                    FontWeight = FontWeights.SemiBold,
                    Foreground = (Brush)FindResource("TextPrimary"),
                    TextTrimming = TextTrimming.CharacterEllipsis,
                    MaxWidth = 420
                });

                if (!string.IsNullOrEmpty(item.Category))
                {
                    var catBorder = new Border
                    {
                        Background = new SolidColorBrush(Color.FromArgb(40, 245, 158, 11)),
                        CornerRadius = new CornerRadius(4),
                        Padding = new Thickness(6, 1, 6, 1),
                        Margin = new Thickness(8, 0, 0, 0),
                        VerticalAlignment = VerticalAlignment.Center
                    };
                    catBorder.Child = new TextBlock
                    {
                        Text = item.Category,
                        FontSize = 10.5,
                        Foreground = new SolidColorBrush(Color.FromRgb(0xF5, 0x9E, 0x0B))
                    };
                    titleRow.Children.Add(catBorder);
                }
                leftStack.Children.Add(titleRow);

                int estMins = item.EstimatedMinutes > 0 ? item.EstimatedMinutes : 25;
                leftStack.Children.Add(new TextBlock
                {
                    Text = $"📥 待办任务  |  预估工时: {estMins} 分钟  |  优先级: {item.Priority}",
                    FontSize = 11,
                    Foreground = (Brush)FindResource("TextSecondary")
                });

                Grid.SetColumn(leftStack, 0);
                grid.Children.Add(leftStack);

                var actionBtn = new Button
                {
                    Content = "🎯 绑定攻坚",
                    Style = (Style)FindResource("GoogleActionBtnStyle"),
                    Padding = new Thickness(12, 5, 12, 5),
                    FontSize = 11.5,
                    FontWeight = FontWeights.SemiBold,
                    VerticalAlignment = VerticalAlignment.Center
                };

                var backlogRef = item;
                actionBtn.Click += (s, e) =>
                {
                    backlogRef.Status = "IN_PROGRESS";
                    backlogRef.StartTime = DateTime.Now;
                    backlogRef.EndTime = DateTime.Now.AddMinutes(estMins);
                    DatabaseService.UpsertSchedule(backlogRef);
                    LaunchFocusHudForSchedule(backlogRef, estMins);
                    RenderFocusPage();
                };

                Grid.SetColumn(actionBtn, 1);
                grid.Children.Add(actionBtn);

                card.Child = grid;
                FocusBindableItemsPanel.Children.Add(card);
            }
        }
        else if (FocusTabStudyRadio.IsChecked == true)
        {
            var studyTopics = DatabaseService.GetAllStudyTopics().Where(t => t.Status != "COMPLETED").ToList();
            if (studyTopics.Count == 0)
            {
                var emptyBlock = new TextBlock
                {
                    Text = "当前无在研学习主题，可前往知识中心开启新知识航道。",
                    Foreground = (Brush)FindResource("TextSecondary"),
                    FontSize = 12,
                    Padding = new Thickness(12, 16, 12, 16),
                    HorizontalAlignment = HorizontalAlignment.Center
                };
                FocusBindableItemsPanel.Children.Add(emptyBlock);
                return;
            }

            foreach (var item in studyTopics)
            {
                var card = new Border
                {
                    Background = (Brush)FindResource("InputBackground"),
                    BorderBrush = (Brush)FindResource("CardBorderBrush"),
                    BorderThickness = new Thickness(1),
                    CornerRadius = new CornerRadius(6),
                    Padding = new Thickness(12, 10, 12, 10),
                    Margin = new Thickness(0, 0, 0, 8)
                };

                var grid = new Grid();
                grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

                var leftStack = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
                var titleRow = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 4) };
                titleRow.Children.Add(new TextBlock
                {
                    Text = item.Title,
                    FontSize = 13,
                    FontWeight = FontWeights.SemiBold,
                    Foreground = (Brush)FindResource("TextPrimary"),
                    TextTrimming = TextTrimming.CharacterEllipsis,
                    MaxWidth = 420
                });

                if (!string.IsNullOrEmpty(item.Category))
                {
                    var catBorder = new Border
                    {
                        Background = new SolidColorBrush(Color.FromArgb(40, 16, 185, 129)),
                        CornerRadius = new CornerRadius(4),
                        Padding = new Thickness(6, 1, 6, 1),
                        Margin = new Thickness(8, 0, 0, 0),
                        VerticalAlignment = VerticalAlignment.Center
                    };
                    catBorder.Child = new TextBlock
                    {
                        Text = item.Category,
                        FontSize = 10.5,
                        Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81))
                    };
                    titleRow.Children.Add(catBorder);
                }
                leftStack.Children.Add(titleRow);

                leftStack.Children.Add(new TextBlock
                {
                    Text = $"📚 学习主题  |  已投入: {item.CompletedHours:0.1}h / 目标: {item.TargetHours:0.1}h  |  进度: {item.ProgressPercent}%",
                    FontSize = 11,
                    Foreground = (Brush)FindResource("TextSecondary")
                });

                Grid.SetColumn(leftStack, 0);
                grid.Children.Add(leftStack);

                var actionBtn = new Button
                {
                    Content = "🎯 专注研读",
                    Style = (Style)FindResource("GoogleActionBtnStyle"),
                    Padding = new Thickness(12, 5, 12, 5),
                    FontSize = 11.5,
                    FontWeight = FontWeights.SemiBold,
                    VerticalAlignment = VerticalAlignment.Center
                };

                var topicRef = item;
                actionBtn.Click += (s, e) =>
                {
                    int defMins = ConfigService.Current.FocusDefaultDurationMinutes;
                    var newSched = new ScheduleItem
                    {
                        Id = Guid.NewGuid().ToString(),
                        Title = $"学习: {topicRef.Title}",
                        Category = topicRef.Category ?? "学习",
                        StartTime = DateTime.Now,
                        EndTime = DateTime.Now.AddMinutes(defMins),
                        EstimatedMinutes = defMins,
                        ActualMinutes = 0,
                        Status = "IN_PROGRESS"
                    };
                    DatabaseService.UpsertSchedule(newSched);
                    LaunchFocusHudForSchedule(newSched, defMins);
                    RenderFocusPage();
                };

                Grid.SetColumn(actionBtn, 1);
                grid.Children.Add(actionBtn);

                card.Child = grid;
                FocusBindableItemsPanel.Children.Add(card);
            }
        }
    }

    private void RenderFocusInterruptions()
    {
        if (FocusInterruptionLogsPanel == null) return;
        FocusInterruptionLogsPanel.Children.Clear();

        var interruptions = DatabaseService.GetInterruptionsForDateRange(DateTime.Today, DateTime.Today.AddDays(1))
                                           .OrderByDescending(i => i.Timestamp)
                                           .ToList();

        if (interruptions.Count == 0)
        {
            FocusInterruptionLogsPanel.Children.Add(new TextBlock
            {
                Text = "今日暂无打断记录，保持深度心流！",
                Foreground = (Brush)FindResource("TextSecondary"),
                FontSize = 11.5,
                Padding = new Thickness(8, 12, 8, 12),
                HorizontalAlignment = HorizontalAlignment.Center
            });
            return;
        }

        foreach (var item in interruptions)
        {
            var row = new Border
            {
                Background = (Brush)FindResource("InputBackground"),
                BorderBrush = (Brush)FindResource("CardBorderBrush"),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(4),
                Padding = new Thickness(8, 6, 8, 6),
                Margin = new Thickness(0, 0, 0, 6)
            };

            var grid = new Grid();
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            var left = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };
            left.Children.Add(new TextBlock
            {
                Text = item.Timestamp.ToString("HH:mm"),
                FontFamily = new FontFamily("Consolas"),
                FontSize = 11,
                Foreground = (Brush)FindResource("TextSecondary"),
                Margin = new Thickness(0, 0, 8, 0)
            });

            string tagText = item.Type switch
            {
                "EXTERNAL" => "[协作/会议]",
                "URGENT" => "[应急插单]",
                "DISTRACTION" => "[走神疲劳]",
                _ => "[其他打断]"
            };

            var tagColor = item.Type switch
            {
                "EXTERNAL" => Color.FromRgb(0x38, 0xBD, 0xF8),
                "URGENT" => Color.FromRgb(0xF5, 0x9E, 0x0B),
                "DISTRACTION" => Color.FromRgb(0xEC, 0x48, 0x99),
                _ => Color.FromRgb(0x94, 0xA3, 0xB8)
            };

            left.Children.Add(new TextBlock
            {
                Text = tagText,
                FontSize = 11,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(tagColor),
                Margin = new Thickness(0, 0, 8, 0)
            });

            left.Children.Add(new TextBlock
            {
                Text = string.IsNullOrEmpty(item.Note) ? "未填写备注" : item.Note,
                FontSize = 11,
                Foreground = (Brush)FindResource("TextPrimary"),
                TextTrimming = TextTrimming.CharacterEllipsis,
                MaxWidth = 180
            });

            Grid.SetColumn(left, 0);
            grid.Children.Add(left);

            var right = new TextBlock
            {
                Text = $"{item.DurationSeconds}s",
                FontSize = 11,
                Foreground = (Brush)FindResource("TextSecondary"),
                VerticalAlignment = VerticalAlignment.Center
            };
            Grid.SetColumn(right, 1);
            grid.Children.Add(right);

            row.Child = grid;
            FocusInterruptionLogsPanel.Children.Add(row);
        }
    }
}
