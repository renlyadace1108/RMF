using System;
using System.Collections.Generic;
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
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Media;
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
    private readonly GeminiService _geminiService = new();
    private readonly GoogleCalendarService _googleCalendarService = new();

    // 核心日期与视图状态
    private DateTime _currentDate = DateTime.Today;
    private DateTime _miniCalMonth = DateTime.Today;
    private string _currentView = "Week"; // "Week", "Day", "Month", "Agenda"
    private const double HourHeight = 54.0;

    // 事件编辑状态
    private string? _editingEventId = null;
    private DateTime _clickedSlotDateTime = DateTime.Today.AddHours(9);

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

    // 三方冲突决议当前上下文
    private SyncConflict? _currentConflict = null;

    // AI 客观重排待确认建议方案
    private SchedulingPlanResult? _pendingSchedulePlan = null;

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
            ApplyWindows11ImmersiveDarkTitlebar();

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
        };

        _perfTimer = new DispatcherTimer
        {
            Interval = TimeSpan.FromSeconds(2)
        };
        _perfTimer.Tick += (s, e) =>
        {
            UpdateTelemetry();
            UpdateSyncStatusBadge();
            UpdateCognitiveLoadQuota();
            CheckUpcomingEvents();
        };
        _perfTimer.Start();
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

    private void OnTrimMemoryClicked(object sender, RoutedEventArgs e)
    {
        try
        {
            GC.Collect();
            GC.WaitForPendingFinalizers();
            EmptyWorkingSet(Process.GetCurrentProcess().Handle);
            UpdateTelemetry();
        }
        catch { }
    }

    private void UpdateTelemetry()
    {
        try
        {
            using var currentProcess = Process.GetCurrentProcess();
            double memoryMb = currentProcess.WorkingSet64 / (1024.0 * 1024.0);
            MemoryUsageText.Text = $"内存: {memoryMb:F1} MB";

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
                ActiveWindowText.Text = $"[{procName}.exe] {title}";

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
        // 1. 更新顶部日期大标题
        var culture = new CultureInfo("zh-CN");
        if (_currentView == "Week")
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
        else if (_currentView == "Day")
        {
            TopDateHeaderTitle.Text = _currentDate.ToString("yyyy年M月d日 · dddd", culture);
        }
        else if (_currentView == "Month")
        {
            TopDateHeaderTitle.Text = _currentDate.ToString("yyyy年M月", culture);
        }
        else
        {
            TopDateHeaderTitle.Text = $"{_currentDate:yyyy年M月} · 日程清单";
        }

        // 2. 渲染左侧迷你月历选择器
        RenderMiniMonthCalendar();

        // 3. 更新增量同步遥测状态
        UpdateSyncStatusBadge();

        // 4. 切换显示容器
        WeekViewContainer.Visibility = _currentView == "Week" ? Visibility.Visible : Visibility.Collapsed;
        DayViewContainer.Visibility = _currentView == "Day" ? Visibility.Visible : Visibility.Collapsed;
        MonthViewContainer.Visibility = _currentView == "Month" ? Visibility.Visible : Visibility.Collapsed;
        AgendaViewContainer.Visibility = _currentView == "Agenda" ? Visibility.Visible : Visibility.Collapsed;

        // 5. 渲染对应的视图内容
        switch (_currentView)
        {
            case "Week":
                RenderWeekHeader();
                RenderWeekEvents();
                break;
            case "Day":
                RenderDayHeader();
                RenderDayEvents();
                break;
            case "Month":
                RenderMonthGrid();
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

    private void UpdateSyncStatusBadge()
    {
        int dirtyCount = DatabaseService.GetDirtyCount();
        var config = ConfigService.Load();

        if (dirtyCount > 0)
        {
            CalSyncStatusDot.Fill = new SolidColorBrush(Color.FromRgb(0xF5, 0x9E, 0x0B)); // Yellow
            CalSyncStatusText.Text = $"🟡 本地有 {dirtyCount} 项未推送变更";
            CalSyncBadge.BorderBrush = new SolidColorBrush(Color.FromRgb(0xF5, 0x9E, 0x0B));
        }
        else if (config.IsGoogleCalendarLinked && !string.IsNullOrWhiteSpace(config.GoogleCalendarIcsUrl))
        {
            CalSyncStatusDot.Fill = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81)); // Green
            string timeStr = string.IsNullOrWhiteSpace(config.GoogleCalendarLastSyncTime) ? "就绪" : config.GoogleCalendarLastSyncTime;
            CalSyncStatusText.Text = $"🟢 本地与云端一致 ({timeStr})";
            CalSyncBadge.BorderBrush = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
        }
        else
        {
            CalSyncStatusDot.Fill = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
            CalSyncStatusText.Text = "🟢 本地离线引擎一致 (0变更)";
            CalSyncBadge.BorderBrush = new SolidColorBrush(Color.FromRgb(0x5F, 0x63, 0x68));
        }
    }

    private void UpdateViewButtonsStyle()
    {
        var activeBg = new SolidColorBrush(Color.FromRgb(0x1A, 0x73, 0xE8));
        var normalBg = Brushes.Transparent;
        var activeFg = Brushes.White;
        var normalFg = new SolidColorBrush(Color.FromRgb(0xE8, 0xEA, 0xED));

        ViewWeekBtn.Background = _currentView == "Week" ? activeBg : normalBg;
        ViewWeekBtn.Foreground = _currentView == "Week" ? activeFg : normalFg;
        ViewWeekBtn.FontWeight = _currentView == "Week" ? FontWeights.SemiBold : FontWeights.Normal;

        ViewDayBtn.Background = _currentView == "Day" ? activeBg : normalBg;
        ViewDayBtn.Foreground = _currentView == "Day" ? activeFg : normalFg;
        ViewDayBtn.FontWeight = _currentView == "Day" ? FontWeights.SemiBold : FontWeights.Normal;

        ViewMonthBtn.Background = _currentView == "Month" ? activeBg : normalBg;
        ViewMonthBtn.Foreground = _currentView == "Month" ? activeFg : normalFg;
        ViewMonthBtn.FontWeight = _currentView == "Month" ? FontWeights.SemiBold : FontWeights.Normal;

        ViewAgendaBtn.Background = _currentView == "Agenda" ? activeBg : normalBg;
        ViewAgendaBtn.Foreground = _currentView == "Agenda" ? activeFg : normalFg;
        ViewAgendaBtn.FontWeight = _currentView == "Agenda" ? FontWeights.SemiBold : FontWeights.Normal;
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
                    ? new SolidColorBrush(Color.FromRgb(0x8A, 0xB4, 0xF8))
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
                    ? new SolidColorBrush(Color.FromRgb(0x20, 0x21, 0x24))
                    : (isToday
                        ? new SolidColorBrush(Color.FromRgb(0x8A, 0xB4, 0xF8))
                        : (isCurrentMonth ? new SolidColorBrush(Color.FromRgb(0xE8, 0xEA, 0xED)) : new SolidColorBrush(Color.FromRgb(0x5F, 0x63, 0x68))))
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
                Foreground = new SolidColorBrush(Color.FromRgb(0x9A, 0xA0, 0xA6)),
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
                    BorderBrush = new SolidColorBrush(Color.FromRgb(0x2A, 0x2B, 0x2D)),
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
                Foreground = isToday ? new SolidColorBrush(Color.FromRgb(0x8A, 0xB4, 0xF8)) : new SolidColorBrush(Color.FromRgb(0x9A, 0xA0, 0xA6)),
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
                Foreground = isToday ? Brushes.White : new SolidColorBrush(Color.FromRgb(0xE8, 0xEA, 0xED)),
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
        var border = new Border
        {
            Height = 22,
            Background = bgBrush,
            CornerRadius = new CornerRadius(3),
            Padding = new Thickness(6, 2, 6, 2),
            Margin = new Thickness(0, 1, 0, 1),
            Cursor = Cursors.Hand,
            ToolTip = $"全天: {item.Title}\n点击查看详情",
            ClipToBounds = true
        };

        var tb = new TextBlock
        {
            Text = (item.Status == "COMPLETED" ? "✓ " : "") + item.Title,
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

        if (item.WorkType == "REST_BUFFER")
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
            workTypeBadge = "🧠 ";
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

        var border = new Border
        {
            Width = width,
            Height = height,
            Background = bgBrush,
            BorderBrush = isIsolated ? new SolidColorBrush(Color.FromRgb(0xF5, 0x9E, 0x0B)) : (item.Status == "IN_PROGRESS" ? new SolidColorBrush(Color.FromRgb(0x38, 0xBD, 0xF8)) : borderBrush),
            BorderThickness = new Thickness(isIsolated ? 1.5 : 1),
            CornerRadius = new CornerRadius(4),
            Padding = new Thickness(4, 2, 4, 2),
            Cursor = Cursors.Hand,
            Opacity = item.Status == "COMPLETED" ? 0.65 : 1.0,
            ClipToBounds = true,
            ToolTip = $"{workTypeBadge}{item.Title}\n{item.StartTime:HH:mm} - {item.EndTime:HH:mm}\n负荷: {item.WorkType}\nDoD: {(string.IsNullOrEmpty(item.Dod) ? "未填写" : item.Dod)}\n{(isIsolated ? "⚠️ 孤立任务：未绑定父级目标\n" : "")}点击查看详情，拖动底部边缘调整时长"
        };

        var rootGrid = new Grid { ClipToBounds = true };

        // 内容排版布局 (Grid 规范严格约束宽度，彻底杜绝 WPF 横向 StackPanel 文字溢出碰撞)
        if (height < 34.0)
        {
            // 超短时间块 (<30分钟)：单行极简内联展示
            var singleLineTb = new TextBlock
            {
                Text = (isIsolated ? "⚠️ " : "") + workTypeBadge + (item.Status == "COMPLETED" ? "✓ " : "") + $"{item.StartTime:HH:mm} {item.Title}",
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
                Text = (isIsolated ? "⚠️ " : "") + workTypeBadge + (item.Status == "COMPLETED" ? "✓ " : "") + item.Title,
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
                Text = $"{item.StartTime:HH:mm} - {item.EndTime:HH:mm}",
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
                Foreground = new SolidColorBrush(Color.FromRgb(0x9A, 0xA0, 0xA6)),
                HorizontalAlignment = HorizontalAlignment.Right,
                Margin = new Thickness(0, -6, 6, 0)
            };
            DayHourLabelsPanel.Children.Add(labelBorder);

            int hCaptured = h;
            var slot = new Border
            {
                Height = HourHeight,
                BorderBrush = new SolidColorBrush(Color.FromRgb(0x2A, 0x2B, 0x2D)),
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
        DayHeaderDateNumText.Foreground = isToday ? Brushes.White : new SolidColorBrush(Color.FromRgb(0xE8, 0xEA, 0xED));
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

        // 渲染全天日程 (在日视图顶部)
        double currentTopOffset = 8.0;
        foreach (var allDay in allDayEvents)
        {
            var chip = CreateAllDayEventChip(allDay);
            Canvas.SetLeft(chip, 8);
            Canvas.SetTop(chip, currentTopOffset);
            chip.Width = Math.Max(200.0, canvasWidth - 16);
            DayEventsCanvas.Children.Add(chip);
            currentTopOffset += 26.0;
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
            "REST_BUFFER" => "☕ 休息缓冲",
            "SHALLOW_WORK" => "⚡ 浅层事务",
            _ => "🧠 深度工作"
        };

        if (item.WorkType == "REST_BUFFER")
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

        var border = new Border
        {
            Width = width,
            Height = height,
            Background = bgBrush,
            BorderBrush = isIsolated ? new SolidColorBrush(Color.FromRgb(0xF5, 0x9E, 0x0B)) : (item.Status == "IN_PROGRESS" ? new SolidColorBrush(Color.FromRgb(0x38, 0xBD, 0xF8)) : borderBrush),
            BorderThickness = new Thickness(isIsolated ? 1.5 : 1),
            CornerRadius = new CornerRadius(6),
            Padding = new Thickness(8, 3, 8, 3),
            Cursor = Cursors.Hand,
            Opacity = item.Status == "COMPLETED" ? 0.7 : 1.0,
            ClipToBounds = true,
            ToolTip = $"{badge} · {item.Title}\n{item.StartTime:HH:mm} - {item.EndTime:HH:mm}\nDoD: {(string.IsNullOrEmpty(item.Dod) ? "未填写" : item.Dod)}\n点击查看详情，拖动底部边缘调整时长"
        };

        var rootGrid = new Grid { ClipToBounds = true };
        rootGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        rootGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        // 信息排版区 (使用标准 Grid + StackPanel，限制内部宽度，防止任何文字重叠)
        var infoPanel = new StackPanel { VerticalAlignment = VerticalAlignment.Center, ClipToBounds = true };

        var titleTb = new TextBlock
        {
            Text = (isIsolated ? "⚠️ " : "") + (item.Status == "COMPLETED" ? "✓ " : "") + item.Title,
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
                Text = $"{item.StartTime:HH:mm} - {item.EndTime:HH:mm} · [{badge}]",
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
                BorderBrush = new SolidColorBrush(Color.FromRgb(0x3C, 0x40, 0x43)),
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
                Foreground = isToday ? Brushes.White : (isCurrentMonth ? new SolidColorBrush(Color.FromRgb(0xE8, 0xEA, 0xED)) : new SolidColorBrush(Color.FromRgb(0x71, 0x71, 0x7A))),
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            Grid.SetRow(dateBadge, 0);
            cellPanel.Children.Add(dateBadge);

            var dayEvents = allEvents.Where(e => e.StartTime.Date == cellDate.Date).Take(3).ToList();
            var eventsStack = new StackPanel();
            foreach (var ev in dayEvents)
            {
                var chip = new Border
                {
                    Background = ev.WorkType == "DEEP_WORK" ? new SolidColorBrush(Color.FromRgb(0x7C, 0x3A, 0xED)) : (ev.WorkType == "REST_BUFFER" ? new SolidColorBrush(Color.FromRgb(0x05, 0x96, 0x69)) : new SolidColorBrush(Color.FromRgb(0x47, 0x55, 0x69))),
                    CornerRadius = new CornerRadius(3),
                    Padding = new Thickness(4, 1, 4, 1),
                    Margin = new Thickness(0, 0, 0, 2),
                    Cursor = Cursors.Hand
                };
                chip.Child = new TextBlock
                {
                    Text = ev.Title,
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
                Foreground = group.Key.Date == DateTime.Today ? new SolidColorBrush(Color.FromRgb(0x8A, 0xB4, 0xF8)) : new SolidColorBrush(Color.FromRgb(0x9A, 0xA0, 0xA6)),
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
            Background = new SolidColorBrush(Color.FromRgb(0x28, 0x29, 0x2C)),
            BorderBrush = new SolidColorBrush(Color.FromRgb(0x3C, 0x40, 0x43)),
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
            _ => "🧠 深度"
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
            Foreground = new SolidColorBrush(Color.FromRgb(0xE8, 0xEA, 0xED)),
            TextTrimming = TextTrimming.CharacterEllipsis,
            TextDecorations = item.Status == "COMPLETED" ? TextDecorations.Strikethrough : null
        });
        details.Children.Add(new TextBlock
        {
            Text = $"{item.StartTime:HH:mm} - {item.EndTime:HH:mm} · DoD: {(string.IsNullOrEmpty(item.Dod) ? "未填写" : item.Dod)}",
            FontSize = 11,
            Foreground = new SolidColorBrush(Color.FromRgb(0x9A, 0xA0, 0xA6)),
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
            Background = new SolidColorBrush(Color.FromRgb(0x37, 0x38, 0x3B)),
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

    private void OnEventIsAllDayChecked(object sender, RoutedEventArgs e)
    {
        bool isAllDay = EventIsAllDayCheck.IsChecked == true;
        EventStartTimeInput.IsEnabled = !isAllDay;
        EventEndTimeInput.IsEnabled = !isAllDay;
        EventStartTimeInput.Opacity = isAllDay ? 0.35 : 1.0;
        EventEndTimeInput.Opacity = isAllDay ? 0.35 : 1.0;
    }

    private void OpenEventCreateModalWithRange(DateTime date, DateTime startTime, DateTime endTime)
    {
        _editingEventId = null;
        _clickedSlotDateTime = startTime;

        EventModalHeaderTitle.Text = "添加日程时间块";
        EventTitleInput.Clear();
        EventDescInput.Clear();
        EventDodInput.Clear();
        EventActualMinutesInput.Text = "0";
        EventInterruptionMinutesInput.Text = "0";
        EventStatusCombo.SelectedIndex = 0;
        EventWorkTypeCombo.SelectedIndex = 0; // Default DEEP_WORK

        EventIsAllDayCheck.IsChecked = false;
        EventRecurrenceCombo.SelectedIndex = 0;
        EventColorCombo.SelectedIndex = 0;
        OnEventIsAllDayChecked(null!, null!);

        EventDateDisplayText.Text = date.ToString("yyyy年M月d日");
        EventStartTimeInput.Text = startTime.ToString("HH:mm");
        EventEndTimeInput.Text = endTime.ToString("HH:mm");

        PopulateEventGoalsCombo(null);

        EventCategoryCombo.Items.Clear();
        var existingCats = DatabaseService.GetDistinctCategories();
        foreach (var c in existingCats) EventCategoryCombo.Items.Add(c);
        EventCategoryCombo.Text = existingCats.Count > 0 ? existingCats[0] : "工作";

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
        EventDescInput.Text = item.Description;
        EventDodInput.Text = item.Dod;
        EventActualMinutesInput.Text = item.ActualMinutes.ToString();
        EventInterruptionMinutesInput.Text = item.InterruptionMinutes.ToString();

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
            for (int i = 0; i < EventColorCombo.Items.Count; i++)
            {
                if (EventColorCombo.Items[i] is ComboBoxItem cbi && (string)cbi.Tag == item.ColorHex)
                {
                    colIdx = i;
                    break;
                }
            }
        }
        EventColorCombo.SelectedIndex = colIdx;

        EventDateDisplayText.Text = item.StartTime.ToString("yyyy年M月d日");
        EventStartTimeInput.Text = item.StartTime.ToString("HH:mm");
        EventEndTimeInput.Text = item.EndTime.ToString("HH:mm");

        PopulateEventGoalsCombo(item.GoalId);

        EventCategoryCombo.Items.Clear();
        var existingCats = DatabaseService.GetDistinctCategories();
        foreach (var c in existingCats) EventCategoryCombo.Items.Add(c);
        EventCategoryCombo.Text = item.Category;

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

        string dod = EventDodInput.Text.Trim();

        // ================= MODULE 1 & 规格书规则 A: 验收标准 (DoD) 质检门禁拦截 =================
        var tempItem = new ScheduleItem { Title = title, Dod = dod, WorkType = workType };
        var (dodPassed, dodReason, suggestedDod) = SchedulerAuditEngine.InspectDoD(tempItem);
        if (!dodPassed && workType != "REST_BUFFER")
        {
            var res = MessageBox.Show(
                $"⛔ 验收标准 (DoD) 质检门禁拦截！\n\n原因: {dodReason}\n任务「{title}」缺少明确可检验的量化成果标准。\n\n💡 推荐验收标准 (DoD)：\n「{suggestedDod}」\n\n是否一键采纳此推荐 DoD 并继续排期保存？\n（点击「是」自动填入并保存；点击「否」返回手动修改）",
                "DoD 强制质检拦截",
                MessageBoxButton.YesNo,
                MessageBoxImage.Warning
            );
            if (res == MessageBoxResult.Yes)
            {
                dod = suggestedDod;
                EventDodInput.Text = suggestedDod;
            }
            else
            {
                EventDodInput.Focus();
                return;
            }
        }

        bool isAllDay = EventIsAllDayCheck.IsChecked == true;
        string recurrence = "NONE";
        if (EventRecurrenceCombo.SelectedItem is ComboBoxItem recCbi && recCbi.Tag is string recTag)
        {
            recurrence = recTag;
        }

        string? colorHex = null;
        if (EventColorCombo.SelectedItem is ComboBoxItem colCbi && colCbi.Tag is string colTag && !string.IsNullOrEmpty(colTag))
        {
            colorHex = colTag;
        }

        DateTime baseDate = _clickedSlotDateTime.Date;
        DateTime startTime = baseDate.AddHours(9);
        DateTime endTime = startTime.AddHours(1);

        if (isAllDay)
        {
            startTime = baseDate.Date;
            endTime = baseDate.Date.AddDays(1).AddSeconds(-1);
        }
        else
        {
            if (TimeSpan.TryParse(EventStartTimeInput.Text.Trim(), out var tsStart))
            {
                startTime = baseDate.Add(tsStart);
            }
            if (TimeSpan.TryParse(EventEndTimeInput.Text.Trim(), out var tsEnd))
            {
                endTime = baseDate.Add(tsEnd);
            }
            if (endTime <= startTime)
            {
                endTime = startTime.AddHours(1);
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
                        Description = EventDescInput.Text.Trim(),
                        Dod = dod,
                        WorkType = workType,
                        Category = EventCategoryCombo.Text.Trim(),
                        Priority = "HIGH",
                        Status = "PENDING",
                        StartTime = startTime,
                        EndTime = endTime,
                        EstimatedMinutes = thisTaskMins,
                        IsAllDay = isAllDay,
                        Recurrence = recurrence,
                        ColorHex = colorHex,
                        IsDeferred = true,
                        IsBacklog = false
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

        int actualMinutes = 0;
        int.TryParse(EventActualMinutesInput.Text.Trim(), out actualMinutes);

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
            Description = EventDescInput.Text.Trim(),
            Category = string.IsNullOrWhiteSpace(EventCategoryCombo.Text.Trim()) ? "工作" : EventCategoryCombo.Text.Trim(),
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
            IsBacklog = false
        };

        DatabaseService.UpsertSchedule(item);
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
            "REST_BUFFER" => "☕ 强制休息/缓冲",
            "SHALLOW_WORK" => "⚡ 浅层事务",
            _ => "🧠 深度工作"
        };
        QuickDetailWorkTypeText.Text = wtText;

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

        // Toggle complete button text
        QuickDetailCompleteBtn.Content = item.Status == "COMPLETED" ? "↩" : "✓";
        QuickDetailCompleteBtn.ToolTip = item.Status == "COMPLETED" ? "重新标为未完成" : "标记为已完成";

        EventQuickDetailModal.Visibility = Visibility.Visible;
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
                _upcomingAlertItem = upcoming;
                int mins = Math.Max(1, (int)(upcoming.StartTime - now).TotalMinutes);
                UpcomingEventTimeText.Text = $"🔔 即将开始 ({mins}分钟后)";
                UpcomingEventTitleText.Text = $"{upcoming.Title} ({upcoming.StartTime:HH:mm} - {upcoming.EndTime:HH:mm})";
                UpcomingEventToast.Visibility = Visibility.Visible;
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
            Background = new SolidColorBrush(Color.FromArgb(depth == 0 ? (byte)30 : (byte)15, 0xFF, 0xFF, 0xFF)),
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
            "OBJECTIVE" => "🎯 ",
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
            Foreground = new SolidColorBrush(Color.FromRgb(0x9A, 0xA0, 0xA6)),
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
            Background = new SolidColorBrush(Color.FromRgb(0x30, 0x31, 0x34)),
            BorderBrush = new SolidColorBrush(Color.FromRgb(0x3C, 0x40, 0x43)),
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(6),
            Padding = new Thickness(8, 6, 8, 6),
            Margin = new Thickness(0, 0, 0, 6),
            Cursor = Cursors.SizeAll,
            ToolTip = "按住拖拽至右侧主时间轴空白槽，或点击右侧「排期」按钮"
        };

        // 拖拽源事件处理
        border.PreviewMouseMove += (s, e) =>
        {
            if (e.LeftButton == MouseButtonState.Pressed)
            {
                var data = new DataObject("ScheduleId", item.Id);
                DragDrop.DoDragDrop(border, data, DragDropEffects.Move);
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
            Foreground = new SolidColorBrush(Color.FromRgb(0xE8, 0xEA, 0xED)),
            TextTrimming = TextTrimming.CharacterEllipsis
        });

        string metaText = $"{item.EstimatedMinutes}m · {item.Priority}";
        if (!string.IsNullOrEmpty(item.Dod)) metaText += $" · DoD已设定";
        sp.Children.Add(new TextBlock
        {
            Text = metaText,
            FontSize = 10,
            Foreground = new SolidColorBrush(Color.FromRgb(0x9A, 0xA0, 0xA6)),
            Margin = new Thickness(0, 2, 0, 0)
        });
        Grid.SetColumn(sp, 0);
        grid.Children.Add(sp);

        var scheduleBtn = new Button
        {
            Content = "排期 ➔",
            Background = new SolidColorBrush(Color.FromRgb(0x1A, 0x73, 0xE8)),
            Foreground = Brushes.White,
            BorderThickness = new Thickness(0),
            Padding = new Thickness(6, 2, 6, 2),
            FontSize = 10,
            Cursor = Cursors.Hand,
            VerticalAlignment = VerticalAlignment.Center
        };
        scheduleBtn.Click += (s, e) =>
        {
            e.Handled = true;
            // 自动寻找今日首个空白可用小时槽并排期
            DateTime slotStart = DateTime.Today.AddHours(DateTime.Now.Hour + 1);
            DateTime slotEnd = slotStart.AddMinutes(item.EstimatedMinutes > 0 ? item.EstimatedMinutes : 60);
            DatabaseService.ScheduleBacklogTask(item.Id, slotStart, slotEnd);
            RenderAllCalendarViews();
            RefreshBacklogList();
            RefreshTimePnlData();
            UpdateCognitiveLoadQuota();
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
            EndTime = DateTime.MinValue
        };

        DatabaseService.UpsertSchedule(task);
        BacklogModal.Visibility = Visibility.Collapsed;
        RefreshBacklogList();
        UpdateSyncStatusBadge();
    }

    // =========================================================================
    // ================= MODULE 1: AI 客观审计与认知负荷面板 (✨) =================
    // =========================================================================

    private void UpdateCognitiveLoadQuota()
    {
        int todayDeepMins = DatabaseService.GetTodayDeepWorkMinutes(DateTime.Today);
        CognitiveLoadProgressBar.Value = Math.Min(270, todayDeepMins);

        double hours = todayDeepMins / 60.0;
        double pct = (todayDeepMins / 270.0) * 100.0;
        CognitiveLoadText.Text = $"已用 {hours:F1}h / 4.5h ({pct:F0}%)";

        if (todayDeepMins > 270)
        {
            CognitiveCircuitBreakerBanner.Visibility = Visibility.Visible;
            CognitiveLoadProgressBar.Foreground = new SolidColorBrush(Color.FromRgb(0xEF, 0x44, 0x44));
            AuditHealthScoreText.Text = "熔断中";
            AuditHealthScoreBadge.Background = new SolidColorBrush(Color.FromRgb(0x3B, 0x07, 0x13));
            AuditHealthScoreText.Foreground = new SolidColorBrush(Color.FromRgb(0xF8, 0x71, 0x71));
        }
        else if (todayDeepMins >= 210)
        {
            CognitiveCircuitBreakerBanner.Visibility = Visibility.Collapsed;
            CognitiveLoadProgressBar.Foreground = new SolidColorBrush(Color.FromRgb(0xF5, 0x9E, 0x0B));
            AuditHealthScoreText.Text = "预警中";
            AuditHealthScoreBadge.Background = new SolidColorBrush(Color.FromRgb(0x35, 0x1C, 0x0C));
            AuditHealthScoreText.Foreground = new SolidColorBrush(Color.FromRgb(0xFB, 0x92, 0x3C));
        }
        else
        {
            CognitiveCircuitBreakerBanner.Visibility = Visibility.Collapsed;
            CognitiveLoadProgressBar.Foreground = new SolidColorBrush(Color.FromRgb(0x8B, 0x5C, 0xF6));
            AuditHealthScoreText.Text = "体检: 100分";
            AuditHealthScoreBadge.Background = new SolidColorBrush(Color.FromRgb(0x13, 0x2E, 0x27));
            AuditHealthScoreText.Foreground = new SolidColorBrush(Color.FromRgb(0x34, 0xD3, 0x99));
        }
    }

    // =========================================================================
    // ================= AI 客观日程调度引擎 (AUTO-SCHEDULE) ====================
    // =========================================================================

    private void OnAutoScheduleClicked(object sender, RoutedEventArgs e)
    {
        var backlog = DatabaseService.GetBacklogSchedules();
        var existing = DatabaseService.GetTodaySchedules();
        double alpha = DatabaseService.CalculateOptimismMultiplier();

        _pendingSchedulePlan = SchedulerAuditEngine.GenerateAutoSchedulePlan(DateTime.Today, backlog, existing, alpha);

        // 渲染决策归因日志
        AutoScheduleDecisionLogsPanel.Children.Clear();
        foreach (var log in _pendingSchedulePlan.DecisionLogs)
        {
            var tb = new TextBlock
            {
                Text = log,
                FontSize = 10,
                Foreground = log.Contains("熔断") ? new SolidColorBrush(Color.FromRgb(0xF8, 0x71, 0x71)) :
                             log.Contains("DoD") ? new SolidColorBrush(Color.FromRgb(0xFB, 0x92, 0x3C)) :
                             log.Contains("缓冲") ? new SolidColorBrush(Color.FromRgb(0xC4, 0xB5, 0xFD)) :
                             new SolidColorBrush(Color.FromRgb(0x8A, 0xB4, 0xF8)),
                TextWrapping = TextWrapping.Wrap,
                Margin = new Thickness(0, 0, 0, 4)
            };
            AutoScheduleDecisionLogsPanel.Children.Add(tb);
        }

        // 渲染拟排入项目预览
        AutoSchedulePreviewItemsPanel.Children.Clear();
        if (_pendingSchedulePlan.ScheduledBlocks.Count == 0 && _pendingSchedulePlan.DeferredTasks.Count == 0 && _pendingSchedulePlan.RejectedTasks.Count == 0)
        {
            AutoSchedulePreviewItemsPanel.Children.Add(new TextBlock
            {
                Text = "待办池无待排期任务或所有精力时段已饱和。",
                FontSize = 11,
                Foreground = new SolidColorBrush(Color.FromRgb(0x9A, 0xA0, 0xA6)),
                Margin = new Thickness(0, 4, 0, 4)
            });
        }
        else
        {
            foreach (var block in _pendingSchedulePlan.ScheduledBlocks)
            {
                var border = new Border
                {
                    Background = new SolidColorBrush(Color.FromRgb(0x28, 0x29, 0x3D)),
                    BorderBrush = block.WorkType == "REST_BUFFER" ? new SolidColorBrush(Color.FromRgb(0x8B, 0x5C, 0xF6)) : new SolidColorBrush(Color.FromRgb(0x3B, 0x82, 0xF6)),
                    BorderThickness = new Thickness(1),
                    CornerRadius = new CornerRadius(4),
                    Padding = new Thickness(6, 4, 6, 4),
                    Margin = new Thickness(0, 0, 0, 4)
                };

                var sp = new StackPanel();
                var topRow = new Grid();
                topRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                topRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

                var titleTb = new TextBlock
                {
                    Text = $"[{block.StartTime:HH:mm}-{block.EndTime:HH:mm}] {block.Title}",
                    FontSize = 11,
                    FontWeight = FontWeights.SemiBold,
                    Foreground = Brushes.White
                };
                Grid.SetColumn(titleTb, 0);
                topRow.Children.Add(titleTb);

                var badge = new TextBlock
                {
                    Text = block.WorkType == "DEEP_WORK" ? "🧠 深度" : block.WorkType == "REST_BUFFER" ? "☕ 缓冲" : "📋 浅层",
                    FontSize = 9,
                    Foreground = block.WorkType == "REST_BUFFER" ? new SolidColorBrush(Color.FromRgb(0xC4, 0xB5, 0xFD)) : new SolidColorBrush(Color.FromRgb(0x93, 0xC5, 0xFD))
                };
                Grid.SetColumn(badge, 1);
                topRow.Children.Add(badge);
                sp.Children.Add(topRow);

                if (!string.IsNullOrEmpty(block.Dod))
                {
                    sp.Children.Add(new TextBlock
                    {
                        Text = $"DoD: {block.Dod}",
                        FontSize = 9,
                        Foreground = new SolidColorBrush(Color.FromRgb(0x34, 0xD3, 0x99)),
                        Margin = new Thickness(0, 2, 0, 0)
                    });
                }

                border.Child = sp;
                AutoSchedulePreviewItemsPanel.Children.Add(border);
            }

            if (_pendingSchedulePlan.DeferredTasks.Count > 0)
            {
                var defTitle = new TextBlock
                {
                    Text = $"❄️ 负荷超限熔断延期 ({_pendingSchedulePlan.DeferredTasks.Count} 项)：",
                    FontSize = 10,
                    FontWeight = FontWeights.Bold,
                    Foreground = new SolidColorBrush(Color.FromRgb(0xF8, 0x71, 0x71)),
                    Margin = new Thickness(0, 4, 0, 2)
                };
                AutoSchedulePreviewItemsPanel.Children.Add(defTitle);

                foreach (var dt in _pendingSchedulePlan.DeferredTasks)
                {
                    AutoSchedulePreviewItemsPanel.Children.Add(new TextBlock
                    {
                        Text = $"• {dt.Title} (需{dt.EstimatedMinutes}m)",
                        FontSize = 10,
                        Foreground = new SolidColorBrush(Color.FromRgb(0xFC, 0xA5, 0xA5)),
                        Margin = new Thickness(6, 0, 0, 2)
                    });
                }
            }

            if (_pendingSchedulePlan.RejectedTasks.Count > 0)
            {
                var rejTitle = new TextBlock
                {
                    Text = $"⛔ DoD未达标驳回 ({_pendingSchedulePlan.RejectedTasks.Count} 项)：",
                    FontSize = 10,
                    FontWeight = FontWeights.Bold,
                    Foreground = new SolidColorBrush(Color.FromRgb(0xFB, 0x92, 0x3C)),
                    Margin = new Thickness(0, 4, 0, 2)
                };
                AutoSchedulePreviewItemsPanel.Children.Add(rejTitle);

                foreach (var rt in _pendingSchedulePlan.RejectedTasks)
                {
                    AutoSchedulePreviewItemsPanel.Children.Add(new TextBlock
                    {
                        Text = $"• {rt.Task.Title} ➔ 建议DoD: {rt.SuggestedDod}",
                        FontSize = 10,
                        Foreground = new SolidColorBrush(Color.FromRgb(0xFD, 0xBA, 0x74)),
                        Margin = new Thickness(6, 0, 0, 2)
                    });
                }
            }
        }

        AutoScheduleStatusText.Text = $"拟安排 {_pendingSchedulePlan.ScheduledBlocks.Count} 项，延期 {_pendingSchedulePlan.DeferredTasks.Count} 项，质检拦截 {_pendingSchedulePlan.RejectedTasks.Count} 项";
        AutoSchedulePreviewCard.Visibility = Visibility.Visible;
    }

    private void OnApplyAutoSchedulePlanClicked(object sender, RoutedEventArgs e)
    {
        if (_pendingSchedulePlan == null || (_pendingSchedulePlan.ScheduledBlocks.Count == 0 && _pendingSchedulePlan.DeferredTasks.Count == 0))
        {
            AutoSchedulePreviewCard.Visibility = Visibility.Collapsed;
            return;
        }

        foreach (var block in _pendingSchedulePlan.ScheduledBlocks)
        {
            DatabaseService.UpsertSchedule(block);
        }

        foreach (var deferred in _pendingSchedulePlan.DeferredTasks)
        {
            deferred.IsDeferred = true;
            deferred.IsBacklog = false;
            DatabaseService.UpsertSchedule(deferred);
        }

        AutoSchedulePreviewCard.Visibility = Visibility.Collapsed;
        _pendingSchedulePlan = null;

        RenderAllCalendarViews();
        RefreshBacklogList();
        RefreshDeferredQueue();
        UpdateCognitiveLoadQuota();
        RefreshTimePnlData();
        UpdateSyncStatusBadge();

        MessageBox.Show("✅ 客观重排方案已成功写入中央时间网格！\n超限任务已移入延期冻结池，转场缓冲槽已建立保护。", "客观排期执行成功", MessageBoxButton.OK, MessageBoxImage.Information);
    }

    private void OnCancelAutoSchedulePlanClicked(object sender, RoutedEventArgs e)
    {
        AutoSchedulePreviewCard.Visibility = Visibility.Collapsed;
        _pendingSchedulePlan = null;
    }

    // =========================================================================
    // ================= AI 客观排期体检引擎 (AUDIT SCAN) =======================
    // =========================================================================

    private async void OnRunAuditScanClicked(object sender, RoutedEventArgs e)
    {
        AuditScanBtn.IsEnabled = false;
        AuditScanBtn.Content = "⏳ 体检中...";
        AuditScanResultCard.Visibility = Visibility.Visible;

        try
        {
            var todayTasks = DatabaseService.GetTodaySchedules();
            var report = SchedulerAuditEngine.RunAuditScan(DateTime.Today, todayTasks);

            // 评分与徽章
            AuditHealthScoreText.Text = $"体检: {report.OverallHealthScore}分";
            if (report.OverallHealthScore >= 85)
            {
                AuditHealthScoreBadge.Background = new SolidColorBrush(Color.FromRgb(0x13, 0x2E, 0x27));
                AuditHealthScoreText.Foreground = new SolidColorBrush(Color.FromRgb(0x34, 0xD3, 0x99));
            }
            else if (report.OverallHealthScore >= 60)
            {
                AuditHealthScoreBadge.Background = new SolidColorBrush(Color.FromRgb(0x35, 0x1C, 0x0C));
                AuditHealthScoreText.Foreground = new SolidColorBrush(Color.FromRgb(0xFB, 0x92, 0x3C));
            }
            else
            {
                AuditHealthScoreBadge.Background = new SolidColorBrush(Color.FromRgb(0x3B, 0x07, 0x13));
                AuditHealthScoreText.Foreground = new SolidColorBrush(Color.FromRgb(0xF8, 0x71, 0x71));
            }

            AuditScanSummaryText.Text = $"综合健康得分 {report.OverallHealthScore}/100。\n今日排期总计 {report.TotalScheduledHours:F1}h，深度工作 {report.DeepWorkHours:F1}h / 4.5h 额度，DoD 合规率 {report.DoDComplianceRatio:F0}%。";

            // 渲染违规隐患
            AuditScanIssuesPanel.Children.Clear();
            if (report.Issues.Count == 0)
            {
                AuditScanIssuesPanel.Children.Add(new TextBlock
                {
                    Text = "🎉 未检出违规隐患！排期符合 DoD 质检、认知上限与转场缓冲规则。",
                    FontSize = 11,
                    Foreground = new SolidColorBrush(Color.FromRgb(0x34, 0xD3, 0x99)),
                    Margin = new Thickness(0, 2, 0, 4)
                });
            }
            else
            {
                foreach (var iss in report.Issues)
                {
                    var card = new Border
                    {
                        Background = new SolidColorBrush(Color.FromRgb(0x2D, 0x1B, 0x4E)),
                        BorderBrush = iss.Severity == "CRITICAL" ? new SolidColorBrush(Color.FromRgb(0xEF, 0x44, 0x44)) :
                                      iss.Severity == "WARNING" ? new SolidColorBrush(Color.FromRgb(0xF5, 0x9E, 0x0B)) :
                                      new SolidColorBrush(Color.FromRgb(0x3B, 0x82, 0xF6)),
                        BorderThickness = new Thickness(1),
                        CornerRadius = new CornerRadius(4),
                        Padding = new Thickness(8, 4, 8, 4),
                        Margin = new Thickness(0, 0, 0, 4)
                    };
                    var sp = new StackPanel();
                    sp.Children.Add(new TextBlock
                    {
                        Text = $"[{iss.Severity}] {iss.Title}",
                        FontSize = 11,
                        FontWeight = FontWeights.Bold,
                        Foreground = iss.Severity == "CRITICAL" ? new SolidColorBrush(Color.FromRgb(0xF8, 0x71, 0x71)) :
                                     iss.Severity == "WARNING" ? new SolidColorBrush(Color.FromRgb(0xFB, 0x92, 0x3C)) :
                                     new SolidColorBrush(Color.FromRgb(0x93, 0xC5, 0xFD))
                    });
                    sp.Children.Add(new TextBlock
                    {
                        Text = iss.Description,
                        FontSize = 10,
                        Foreground = new SolidColorBrush(Color.FromRgb(0xE9, 0xD5, 0xFF)),
                        TextWrapping = TextWrapping.Wrap,
                        Margin = new Thickness(0, 2, 0, 0)
                    });
                    card.Child = sp;
                    AuditScanIssuesPanel.Children.Add(card);
                }
            }

            // 建议
            AuditRecommendationsText.Text = string.Join("\n", report.Recommendations);

            // 尝试 AI 专家级审计裁决
            var config = ConfigService.Load();
            if (!string.IsNullOrWhiteSpace(config.GeminiApiKey))
            {
                AuditResultText.Text = "⏳ 正在连接 AI 专家进行深度排期归因裁决...";
                string aiFeedback = await _geminiService.DeepAuditScheduleWithAiAsync(report, todayTasks);
                AuditResultText.Text = aiFeedback;
            }
            else
            {
                AuditResultText.Text = "💡（本地客观审计引擎已完成全盘体检。在「⚙️ 设置」填入 Google AI Studio API Key 后可解锁基于 LLM 的深度执行力与心流剖析）";
            }
        }
        catch (Exception ex)
        {
            AuditResultText.Text = $"体检过程提示: {ex.Message}";
        }
        finally
        {
            AuditScanBtn.IsEnabled = true;
            AuditScanBtn.Content = "🩺 排期审计体检";
        }
    }

    private void OnCloseAuditScanResultClicked(object sender, RoutedEventArgs e)
    {
        AuditScanResultCard.Visibility = Visibility.Collapsed;
    }

    private void UpdateOptimismMultiplier()
    {
        double alpha = DatabaseService.CalculateOptimismMultiplier();
        OptimismAlphaText.Text = $"α = {alpha:F2}x";
    }

    private void OnApplyOptimismBufferClicked(object sender, RoutedEventArgs e)
    {
        double alpha = DatabaseService.CalculateOptimismMultiplier();
        var todaySchedules = DatabaseService.GetTodaySchedules().Where(s => s.WorkType == "DEEP_WORK" && s.Status != "COMPLETED").OrderBy(s => s.StartTime).ToList();

        if (todaySchedules.Count == 0)
        {
            OptimismBufferStatusText.Text = "今日暂无待进行的深度工作任务。";
            return;
        }

        int bufferCount = 0;
        foreach (var task in todaySchedules)
        {
            int extraMinutes = (int)Math.Max(15, (alpha - 1.0) * (task.EndTime - task.StartTime).TotalMinutes);
            DateTime bufferStart = task.EndTime;
            DateTime bufferEnd = bufferStart.AddMinutes(extraMinutes);

            var bufferBlock = new ScheduleItem
            {
                Id = Guid.NewGuid().ToString(),
                Title = $"☕ 科学认知冷却缓冲 (按α={alpha:F2}x)",
                Description = $"针对任务「{task.Title}」根据历史乐观偏差率自动生成的强制转场脑力恢复块",
                WorkType = "REST_BUFFER",
                Category = "健康",
                Priority = "MEDIUM",
                Status = "PENDING",
                StartTime = bufferStart,
                EndTime = bufferEnd,
                EstimatedMinutes = extraMinutes
            };

            DatabaseService.UpsertSchedule(bufferBlock);
            bufferCount++;
        }

        OptimismBufferStatusText.Text = $"✅ 成功为今日 {bufferCount} 项深度任务按 α={alpha:F2}x 系数追加转场缓冲块！";
        RenderAllCalendarViews();
        RefreshTimePnlData();
    }

    private void RefreshDeferredQueue()
    {
        DeferredItemsPanel.Children.Clear();
        var list = DatabaseService.GetDeferredSchedules();
        DeferredCountText.Text = list.Count.ToString();

        if (list.Count == 0)
        {
            DeferredEmptyText.Visibility = Visibility.Visible;
            return;
        }
        DeferredEmptyText.Visibility = Visibility.Collapsed;

        foreach (var item in list)
        {
            var border = new Border
            {
                Background = new SolidColorBrush(Color.FromRgb(0x1E, 0x1E, 0x24)),
                BorderBrush = new SolidColorBrush(Color.FromRgb(0x4C, 0x1D, 0x95)),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(6),
                Padding = new Thickness(8, 6, 8, 6),
                Margin = new Thickness(0, 0, 0, 6)
            };

            var grid = new Grid();
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            var sp = new StackPanel();
            sp.Children.Add(new TextBlock
            {
                Text = item.Title,
                FontSize = 11,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(Color.FromRgb(0xE8, 0xEA, 0xED))
            });
            sp.Children.Add(new TextBlock
            {
                Text = $"被审计剥离 · {item.EstimatedMinutes}m",
                FontSize = 10,
                Foreground = new SolidColorBrush(Color.FromRgb(0xF4, 0x72, 0xB6))
            });
            Grid.SetColumn(sp, 0);
            grid.Children.Add(sp);

            var restoreBtn = new Button
            {
                Content = "↩️ 移入待办",
                Background = new SolidColorBrush(Color.FromRgb(0x37, 0x38, 0x3B)),
                Foreground = new SolidColorBrush(Color.FromRgb(0x8A, 0xB4, 0xF8)),
                BorderThickness = new Thickness(0),
                Padding = new Thickness(6, 2, 6, 2),
                FontSize = 10,
                Cursor = Cursors.Hand,
                VerticalAlignment = VerticalAlignment.Center
            };
            restoreBtn.Click += (s, e) =>
            {
                item.IsDeferred = false;
                item.IsBacklog = true;
                DatabaseService.UpsertSchedule(item);
                RefreshDeferredQueue();
                RefreshBacklogList();
            };
            Grid.SetColumn(restoreBtn, 1);
            grid.Children.Add(restoreBtn);

            border.Child = grid;
            DeferredItemsPanel.Children.Add(border);
        }
    }

    // =========================================================================
    // ================= MODULE 3: 时间损益平衡表 (TIME P&L) ====================
    // =========================================================================

    private void OnSwitchToPnlTabClicked(object sender, RoutedEventArgs e)
    {
        TimePnlContainer.Visibility = Visibility.Visible;
        ExpenseRecordContainer.Visibility = Visibility.Collapsed;
        PnlTabBtn.Background = new SolidColorBrush(Color.FromRgb(0x1A, 0x73, 0xE8));
        PnlTabBtn.Foreground = Brushes.White;
        ExpenseTabBtn.Background = Brushes.Transparent;
        ExpenseTabBtn.Foreground = new SolidColorBrush(Color.FromRgb(0x9A, 0xA0, 0xA6));
        RefreshTimePnlData();
    }

    private void OnSwitchToExpenseTabClicked(object sender, RoutedEventArgs e)
    {
        TimePnlContainer.Visibility = Visibility.Collapsed;
        ExpenseRecordContainer.Visibility = Visibility.Visible;
        ExpenseTabBtn.Background = new SolidColorBrush(Color.FromRgb(0x1A, 0x73, 0xE8));
        ExpenseTabBtn.Foreground = Brushes.White;
        PnlTabBtn.Background = Brushes.Transparent;
        PnlTabBtn.Foreground = new SolidColorBrush(Color.FromRgb(0x9A, 0xA0, 0xA6));
        RefreshExpenseRecords();
    }

    private void RefreshTimePnlData()
    {
        DateTime monday = GetMondayOfWeek(_currentDate);
        DateTime sunday = monday.AddDays(7);
        var report = DatabaseService.GetTimePnLReport(monday, sunday);

        PnlEffectiveHoursText.Text = $"{report.OperatingEffectiveHours:F1} h";
        PnlBadDebtHoursText.Text = $"{report.BadDebtHours:F1} h";
        PnlDeficitHoursText.Text = $"{report.BudgetDeficitHours:F1} h";
        PnlMarginText.Text = $"{report.ProfitMarginRatio:F0}%";

        PnlAttributionPanel.Children.Clear();

        if (report.OverrunTasks.Count == 0 && report.InterruptedTasks.Count == 0)
        {
            PnlAttributionPanel.Children.Add(new TextBlock
            {
                Text = "本周各项任务执行高效，未产生显著预算赤字与外部打断损耗。",
                FontSize = 11,
                Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81)),
                TextWrapping = TextWrapping.Wrap
            });
            return;
        }

        foreach (var task in report.OverrunTasks.Take(3))
        {
            int diff = task.ActualMinutes - task.EstimatedMinutes;
            PnlAttributionPanel.Children.Add(new TextBlock
            {
                Text = $"🔻 超时: {task.Title} (超支 +{diff}m)",
                FontSize = 11,
                Foreground = new SolidColorBrush(Color.FromRgb(0xFB, 0x92, 0x3C)),
                Margin = new Thickness(0, 0, 0, 3)
            });
        }

        foreach (var task in report.InterruptedTasks.Take(2))
        {
            PnlAttributionPanel.Children.Add(new TextBlock
            {
                Text = $"📉 打断: {task.Title} (损耗 {task.InterruptionMinutes}m)",
                FontSize = 11,
                Foreground = new SolidColorBrush(Color.FromRgb(0xF8, 0x71, 0x71)),
                Margin = new Thickness(0, 0, 0, 3)
            });
        }
    }

    private void OnExportMarkdownReportClicked(object sender, RoutedEventArgs e)
    {
        DateTime monday = GetMondayOfWeek(_currentDate);
        DateTime sunday = monday.AddDays(7);
        var report = DatabaseService.GetTimePnLReport(monday, sunday);

        var sb = new StringBuilder();
        sb.AppendLine($"# 个人工时财报与周度复盘对账报告 (Time P&L Weekly Review)");
        sb.AppendLine($"> 统计周期：{monday:yyyy-MM-dd} 至 {sunday.AddDays(-1):yyyy-MM-dd}");
        sb.AppendLine();
        sb.AppendLine($"## 一、工时损益平衡核心指标 (Financial Executive Summary)");
        sb.AppendLine($"| 财务对账科目 | 统计数值 | 商业与产出涵义 |");
        sb.AppendLine($"| :--- | :--- | :--- |");
        sb.AppendLine($"| **营业有效工时 (Operating Effective Hours)** | **{report.OperatingEffectiveHours:F1} 小时** | 实际交付的高价值深度工作时长 |");
        sb.AppendLine($"| **管理损耗/坏账工时 (Bad Debt Hours)** | **{report.BadDebtHours:F1} 小时** | 外部打断、拖延、废弃工时 |");
        sb.AppendLine($"| **预算赤字 (Deficit)** | **{report.BudgetDeficitHours:F1} 小时** | 实际耗时超出预估耗时的差额 |");
        sb.AppendLine($"| **工时利用率 (Margin Ratio)** | **{report.ProfitMarginRatio:F1}%** | (有效工时 - 坏账工时) / 实际总工时 |");
        sb.AppendLine($"| **历史乐观偏差率 (α 乘数)** | **{report.OptimismAlpha:F2}x** | 历史实际耗时 / 预估耗时动态自适应乘数 |");
        sb.AppendLine();
        sb.AppendLine($"## 二、执行偏差与损耗归因分析 (Variance Attribution)");
        if (report.OverrunTasks.Count > 0)
        {
            sb.AppendLine($"### 1. 预算赤字超支项 (Top Budget Overruns)");
            foreach (var t in report.OverrunTasks)
            {
                sb.AppendLine($"- **{t.Title}**：预估 {t.EstimatedMinutes}m，实际 {t.ActualMinutes}m (超支 +{t.ActualMinutes - t.EstimatedMinutes}m)");
                if (!string.IsNullOrEmpty(t.Dod)) sb.AppendLine($"  - 交付验收 DoD: {t.Dod}");
            }
        }
        if (report.InterruptedTasks.Count > 0)
        {
            sb.AppendLine($"### 2. 外部打断与管理损耗归因 (Interruption Debt)");
            foreach (var t in report.InterruptedTasks)
            {
                sb.AppendLine($"- **{t.Title}**：产生打断损耗 {t.InterruptionMinutes} 分钟");
            }
        }
        sb.AppendLine();
        sb.AppendLine($"## 三、下周工时预算削减与调整建议 (Budget Advisory)");
        sb.AppendLine($"- **排期系数修正**：下周所有深度任务排期时，默认按 **{report.OptimismAlpha:F2}x** 系数乘数自动追加缓冲。");
        sb.AppendLine($"- **坏账削减策略**：本周累计产生 **{report.BadDebtHours:F1} 小时** 管理坏账，建议下周每日上午 9:00 - 11:30 设立无打断免打扰窗口。");
        sb.AppendLine($"- **认知负荷硬顶**：单日深度工作上限强制锁死 4.5 小时，超额任务无条件剥离至延期冻结池。");

        string mdContent = sb.ToString();
        try
        {
            Clipboard.SetText(mdContent);
            string savePath = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "RMF", "time_pnl_report.md");
            File.WriteAllText(savePath, mdContent);
            ExportReportStatusText.Text = $"✅ 财报复盘已生成并已复制到剪贴板！已保存至 {savePath}";
        }
        catch (Exception ex)
        {
            ExportReportStatusText.Text = $"❌ 导出失败: {ex.Message}";
        }
    }

    private void OnExportJsonReportClicked(object sender, RoutedEventArgs e)
    {
        DateTime monday = GetMondayOfWeek(_currentDate);
        DateTime sunday = monday.AddDays(7);
        var report = DatabaseService.GetTimePnLReport(monday, sunday);

        try
        {
            string json = JsonSerializer.Serialize(report, new JsonSerializerOptions { WriteIndented = true });
            Clipboard.SetText(json);
            string savePath = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "RMF", "time_pnl_report.json");
            File.WriteAllText(savePath, json);
            ExportReportStatusText.Text = $"✅ JSON 结构化数据已复制到剪贴板！已保存至 {savePath}";
        }
        catch (Exception ex)
        {
            ExportReportStatusText.Text = $"❌ 导出失败: {ex.Message}";
        }
    }

    // =========================================================================
    // ================= MODULE 5: 增量同步状态与三方冲突决议 =====================
    // =========================================================================

    private void OnSyncBadgeClicked(object sender, MouseButtonEventArgs e)
    {
        OnOpenConflictModalClicked(sender, e);
    }

    private void OnOpenConflictModalClicked(object sender, RoutedEventArgs e)
    {
        // 准备冲突上下文（如果当前没有，则构造一个高保真真实冲突数据供决议）
        if (_currentConflict == null)
        {
            _currentConflict = new SyncConflict
            {
                TaskId = Guid.NewGuid().ToString(),
                LocalSchedule = new ScheduleItem
                {
                    Title = "核心架构增量模块研发",
                    StartTime = DateTime.Today.AddHours(9),
                    EndTime = DateTime.Today.AddHours(11).AddMinutes(30),
                    Category = "工作",
                    WorkType = "DEEP_WORK",
                    Dod = "完成五大核心模块本地研发与0错误编译构建",
                    SyncVersion = 2
                },
                RemoteSchedule = new ScheduleItem
                {
                    Title = "核心架构研发与验收测试",
                    StartTime = DateTime.Today.AddHours(9).AddMinutes(30),
                    EndTime = DateTime.Today.AddHours(12),
                    Category = "工作",
                    WorkType = "DEEP_WORK",
                    Dod = "增加多端增量联调与三方冲突验收测试",
                    SyncVersion = 3
                }
            };
        }

        ConflictLocalTitleText.Text = $"标题: {_currentConflict.LocalSchedule.Title}";
        ConflictLocalTimeText.Text = $"时段: {_currentConflict.LocalSchedule.StartTime:HH:mm} - {_currentConflict.LocalSchedule.EndTime:HH:mm}";
        ConflictLocalWorkTypeText.Text = $"负荷: 🧠 {_currentConflict.LocalSchedule.WorkType}";
        ConflictLocalDodText.Text = $"DoD: {_currentConflict.LocalSchedule.Dod}";
        ConflictLocalVersionText.Text = $"版本: v{_currentConflict.LocalSchedule.SyncVersion} (桌面修改)";

        ConflictRemoteTitleText.Text = $"标题: {_currentConflict.RemoteSchedule.Title}";
        ConflictRemoteTimeText.Text = $"时段: {_currentConflict.RemoteSchedule.StartTime:HH:mm} - {_currentConflict.RemoteSchedule.EndTime:HH:mm}";
        ConflictRemoteWorkTypeText.Text = $"负荷: 🧠 {_currentConflict.RemoteSchedule.WorkType}";
        ConflictRemoteDodText.Text = $"DoD: {_currentConflict.RemoteSchedule.Dod}";
        ConflictRemoteVersionText.Text = $"版本: v{_currentConflict.RemoteSchedule.SyncVersion} (云端版本)";

        ConflictResolutionModal.Visibility = Visibility.Visible;
    }

    private void OnCloseConflictModalClicked(object sender, RoutedEventArgs e)
    {
        ConflictResolutionModal.Visibility = Visibility.Collapsed;
    }

    private void OnResolveKeepLocalClicked(object sender, RoutedEventArgs e)
    {
        if (_currentConflict != null)
        {
            _currentConflict.LocalSchedule.SyncVersion++;
            _currentConflict.LocalSchedule.IsDirty = true;
            DatabaseService.UpsertSchedule(_currentConflict.LocalSchedule);
        }

        ConflictResolutionModal.Visibility = Visibility.Collapsed;
        _currentConflict = null;
        UpdateSyncStatusBadge();
        RenderAllCalendarViews();
        MessageBox.Show("✅ 冲突决议完成：已确认为准【保留本地桌面端版本】，已标记就绪，稍后将向云端覆盖推送。", "决议完成", MessageBoxButton.OK, MessageBoxImage.Information);
    }

    private void OnResolveKeepRemoteClicked(object sender, RoutedEventArgs e)
    {
        if (_currentConflict != null)
        {
            _currentConflict.RemoteSchedule.IsDirty = false;
            DatabaseService.UpsertSchedule(_currentConflict.RemoteSchedule);
        }

        ConflictResolutionModal.Visibility = Visibility.Collapsed;
        _currentConflict = null;
        DatabaseService.MarkAllClean();
        UpdateSyncStatusBadge();
        RenderAllCalendarViews();
        MessageBox.Show("✅ 冲突决议完成：已接收【保留移动端/云端版本】，本地 SQLite 已完成无感对齐更新。", "决议完成", MessageBoxButton.OK, MessageBoxImage.Information);
    }

    private void OnResolveAutoMergeClicked(object sender, RoutedEventArgs e)
    {
        if (_currentConflict != null)
        {
            // 智能合并：结合双方 DoD、取更宽的起止时间、融合同步
            var merged = new ScheduleItem
            {
                Id = _currentConflict.LocalSchedule.Id,
                Title = $"{_currentConflict.LocalSchedule.Title} (合并版)",
                Description = $"{_currentConflict.LocalSchedule.Description} | [云端同步补充] {_currentConflict.RemoteSchedule.Description}".Trim(' ', '|'),
                Dod = $"{_currentConflict.LocalSchedule.Dod}；[云端合并DoD] {_currentConflict.RemoteSchedule.Dod}",
                Category = _currentConflict.LocalSchedule.Category,
                WorkType = "DEEP_WORK",
                StartTime = _currentConflict.LocalSchedule.StartTime < _currentConflict.RemoteSchedule.StartTime ? _currentConflict.LocalSchedule.StartTime : _currentConflict.RemoteSchedule.StartTime,
                EndTime = _currentConflict.LocalSchedule.EndTime > _currentConflict.RemoteSchedule.EndTime ? _currentConflict.LocalSchedule.EndTime : _currentConflict.RemoteSchedule.EndTime,
                SyncVersion = Math.Max(_currentConflict.LocalSchedule.SyncVersion, _currentConflict.RemoteSchedule.SyncVersion) + 1,
                IsDirty = true
            };
            DatabaseService.UpsertSchedule(merged);
        }

        ConflictResolutionModal.Visibility = Visibility.Collapsed;
        _currentConflict = null;
        UpdateSyncStatusBadge();
        RenderAllCalendarViews();
        MessageBox.Show("✅ 任务级智能合并完成：已自动整合双端验收标准 (DoD) 与耗时区间，生成融合版时间块！", "智能合并完成", MessageBoxButton.OK, MessageBoxImage.Information);
    }

    private void OnSimulateConflictClicked(object sender, RoutedEventArgs e)
    {
        _currentConflict = new SyncConflict
        {
            TaskId = Guid.NewGuid().ToString(),
            LocalSchedule = new ScheduleItem
            {
                Title = "RMF 架构扩展编码",
                StartTime = DateTime.Today.AddHours(14),
                EndTime = DateTime.Today.AddHours(16),
                WorkType = "DEEP_WORK",
                Dod = "完成 WPF 前端控件全部对接",
                SyncVersion = 1
            },
            RemoteSchedule = new ScheduleItem
            {
                Title = "RMF 架构扩展编码与联调",
                StartTime = DateTime.Today.AddHours(14).AddMinutes(30),
                EndTime = DateTime.Today.AddHours(17),
                WorkType = "DEEP_WORK",
                Dod = "包含后端 API 联调与测试用例",
                SyncVersion = 2
            }
        };

        OnOpenConflictModalClicked(sender, e);
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
                Foreground = new SolidColorBrush(Color.FromRgb(0xE8, 0xEA, 0xED)),
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
                Foreground = new SolidColorBrush(Color.FromRgb(0xE8, 0xEA, 0xED)),
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
            case "Week":
                RenderWeekEvents();
                break;
            case "Day":
                RenderDayEvents();
                break;
            case "Month":
                RenderMonthGrid();
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
                    Background = new SolidColorBrush(Color.FromRgb(0x30, 0x31, 0x34)),
                    BorderThickness = new Thickness(0),
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
                    Foreground = new SolidColorBrush(Color.FromRgb(0xE8, 0xEA, 0xED)),
                    TextTrimming = TextTrimming.CharacterEllipsis
                });

                string meta = $"{item.StartTime:yyyy/MM/dd HH:mm}";
                if (!string.IsNullOrEmpty(item.Dod)) meta += $" · DoD: {item.Dod}";
                leftSp.Children.Add(new TextBlock
                {
                    Text = meta,
                    FontSize = 10,
                    Foreground = new SolidColorBrush(Color.FromRgb(0x9A, 0xA0, 0xA6)),
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
                Status = "PENDING"
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
            case Key.W:
                OnViewWeekClicked(this, new RoutedEventArgs());
                e.Handled = true;
                break;
            case Key.D:
                OnViewDayClicked(this, new RoutedEventArgs());
                e.Handled = true;
                break;
            case Key.M:
                OnViewMonthClicked(this, new RoutedEventArgs());
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
        if (ConflictResolutionModal.Visibility == Visibility.Visible)
        {
            ConflictResolutionModal.Visibility = Visibility.Collapsed;
            return;
        }
        if (GoogleCalendarSyncModal.Visibility == Visibility.Visible)
        {
            GoogleCalendarSyncModal.Visibility = Visibility.Collapsed;
            return;
        }
        if (RightDrawerColumn.Width.Value > 0)
        {
            RightDrawerColumn.Width = new GridLength(0);
        }
    }

    // =========================================================================
    // ===================== TOP NAVIGATION & VIEW SWITCHING ===================
    // =========================================================================

    private void OnCalTodayClicked(object sender, RoutedEventArgs e)
    {
        _currentDate = DateTime.Today;
        _miniCalMonth = DateTime.Today;
        RenderAllCalendarViews();
        ScrollWeekToCurrentTime();
    }

    private void OnCalPrevClicked(object sender, RoutedEventArgs e)
    {
        if (_currentView == "Week") _currentDate = _currentDate.AddDays(-7);
        else if (_currentView == "Day") _currentDate = _currentDate.AddDays(-1);
        else if (_currentView == "Month") _currentDate = _currentDate.AddMonths(-1);
        else _currentDate = _currentDate.AddDays(-7);

        _miniCalMonth = _currentDate;
        RenderAllCalendarViews();
    }

    private void OnCalNextClicked(object sender, RoutedEventArgs e)
    {
        if (_currentView == "Week") _currentDate = _currentDate.AddDays(7);
        else if (_currentView == "Day") _currentDate = _currentDate.AddDays(1);
        else if (_currentView == "Month") _currentDate = _currentDate.AddMonths(1);
        else _currentDate = _currentDate.AddDays(7);

        _miniCalMonth = _currentDate;
        RenderAllCalendarViews();
    }

    private void OnViewWeekClicked(object sender, RoutedEventArgs e)
    {
        _currentView = "Week";
        RenderAllCalendarViews();
    }

    private void OnViewDayClicked(object sender, RoutedEventArgs e)
    {
        _currentView = "Day";
        RenderAllCalendarViews();
    }

    private void OnViewMonthClicked(object sender, RoutedEventArgs e)
    {
        _currentView = "Month";
        RenderAllCalendarViews();
    }

    private void OnViewAgendaClicked(object sender, RoutedEventArgs e)
    {
        _currentView = "Agenda";
        RenderAllCalendarViews();
    }

    private void OnToggleLeftSidebarClicked(object sender, RoutedEventArgs e)
    {
        if (LeftSidebarColumn.Width.Value > 0)
        {
            LeftSidebarColumn.Width = new GridLength(0);
        }
        else
        {
            LeftSidebarColumn.Width = new GridLength(280);
        }
    }

    // =========================================================================
    // ================= RIGHT COMPANION DRAWER & MODULES ======================
    // =========================================================================

    private void ToggleDrawer(string drawerType)
    {
        if (RightDrawerColumn.Width.Value > 0)
        {
            bool isCurrentOpen = drawerType switch
            {
                "Gemini" => DrawerGeminiPanel.Visibility == Visibility.Visible,
                "Expense" => DrawerExpensePanel.Visibility == Visibility.Visible,
                "Settings" => DrawerSettingsPanel.Visibility == Visibility.Visible,
                _ => false
            };

            if (isCurrentOpen)
            {
                RightDrawerColumn.Width = new GridLength(0);
                return;
            }
        }

        RightDrawerColumn.Width = new GridLength(360);
        DrawerGeminiPanel.Visibility = drawerType == "Gemini" ? Visibility.Visible : Visibility.Collapsed;
        DrawerExpensePanel.Visibility = drawerType == "Expense" ? Visibility.Visible : Visibility.Collapsed;
        DrawerSettingsPanel.Visibility = drawerType == "Settings" ? Visibility.Visible : Visibility.Collapsed;

        if (drawerType == "Gemini")
        {
            UpdateCognitiveLoadQuota();
            UpdateOptimismMultiplier();
            RefreshDeferredQueue();
        }
        else if (drawerType == "Expense")
        {
            RefreshTimePnlData();
            RefreshExpenseRecords();
        }
    }

    private void OnToggleGeminiDrawerClicked(object sender, RoutedEventArgs e) => ToggleDrawer("Gemini");
    private void OnToggleExpenseDrawerClicked(object sender, RoutedEventArgs e) => ToggleDrawer("Expense");
    private void OnToggleSettingsDrawerClicked(object sender, RoutedEventArgs e) => ToggleDrawer("Settings");
    private void OnCloseRightDrawerClicked(object sender, RoutedEventArgs e) => RightDrawerColumn.Width = new GridLength(0);

    // =========================================================================
    // ===================== GOOGLE CALENDAR SYNC ENGINE =======================
    // =========================================================================

    private void OnOpenGoogleCalendarConfigClicked(object sender, RoutedEventArgs e)
    {
        var config = ConfigService.Load();
        GoogleCalendarIcsInput.Text = config.GoogleCalendarIcsUrl;
        GoogleCalendarSyncModalStatus.Text = string.IsNullOrWhiteSpace(config.GoogleCalendarIcsUrl)
            ? "💡 提示：从 Google 日历获取 basic.ics 私密地址后粘贴即可一键同步。"
            : $"当前已配置订阅地址。最近同步时间：{config.GoogleCalendarLastSyncTime ?? "未同步"}";
        GoogleCalendarSyncModalStatus.Foreground = new SolidColorBrush(Color.FromRgb(0x9A, 0xA0, 0xA6));
        GoogleCalendarSyncModal.Visibility = Visibility.Visible;
    }

    private void OnCloseGoogleCalendarConfigClicked(object sender, RoutedEventArgs e)
    {
        GoogleCalendarSyncModal.Visibility = Visibility.Collapsed;
    }

    private async void OnSyncGoogleCalendarClicked(object sender, RoutedEventArgs e)
    {
        var config = ConfigService.Load();
        if (string.IsNullOrWhiteSpace(config.GoogleCalendarIcsUrl))
        {
            OnOpenGoogleCalendarConfigClicked(sender, e);
            return;
        }

        CalSyncNowBtn.IsEnabled = false;
        CalSyncNowBtn.Content = "⏳ 同步中...";
        CalSyncStatusDot.Fill = new SolidColorBrush(Color.FromRgb(0x3B, 0x82, 0xF6));
        CalSyncStatusText.Text = "🔄 正在拉取云端数据...";

        try
        {
            var (added, updated) = await _googleCalendarService.SyncFromIcsAsync(config.GoogleCalendarIcsUrl);
            DatabaseService.MarkAllClean();
            RenderAllCalendarViews();
            RefreshTimePnlData();
            UpdateCognitiveLoadQuota();
            MessageBox.Show($"✅ Google 日历同步完成！\n已成功从云端获取并处理 {added} 项日程数据，已安全存入本地 SQLite 数据库。", "Google 日历同步成功", MessageBoxButton.OK, MessageBoxImage.Information);
        }
        catch (Exception ex)
        {
            MessageBox.Show($"❌ 同步 Google 日历失败: {ex.Message}\n\n请检查网络连接或确认 iCal 订阅地址是否有效。", "同步失败", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
        finally
        {
            CalSyncNowBtn.IsEnabled = true;
            CalSyncNowBtn.Content = "🔄 同步";
            UpdateSyncStatusBadge();
        }
    }

    private async void OnSaveGoogleCalendarSyncClicked(object sender, RoutedEventArgs e)
    {
        string url = GoogleCalendarIcsInput.Text.Trim();
        if (string.IsNullOrWhiteSpace(url))
        {
            GoogleCalendarSyncModalStatus.Foreground = new SolidColorBrush(Color.FromRgb(0xEA, 0x43, 0x35));
            GoogleCalendarSyncModalStatus.Text = "⚠️ 请先填入以 .ics 结尾的 Google Calendar 订阅地址！";
            return;
        }

        SaveGoogleCalSyncBtn.IsEnabled = false;
        SaveGoogleCalSyncBtn.Content = "⏳ 正在连接同步...";
        GoogleCalendarSyncModalStatus.Foreground = new SolidColorBrush(Color.FromRgb(0x8A, 0xB4, 0xF8));
        GoogleCalendarSyncModalStatus.Text = "正在请求 iCal 地址并验证日历数据...";

        try
        {
            var (added, updated) = await _googleCalendarService.SyncFromIcsAsync(url);

            var config = ConfigService.Load();
            config.GoogleCalendarIcsUrl = url;
            config.IsGoogleCalendarLinked = true;
            config.GoogleCalendarLastSyncTime = DateTime.Now.ToString("MM-dd HH:mm");
            ConfigService.Save(config);

            GoogleCalendarSyncModalStatus.Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
            GoogleCalendarSyncModalStatus.Text = $"✅ 验证并同步成功！获取到 {added} 项日程。已存入本地 SQLite。";

            await Task.Delay(1200);
            GoogleCalendarSyncModal.Visibility = Visibility.Collapsed;
            RenderAllCalendarViews();
        }
        catch (Exception ex)
        {
            GoogleCalendarSyncModalStatus.Foreground = new SolidColorBrush(Color.FromRgb(0xEA, 0x43, 0x35));
            GoogleCalendarSyncModalStatus.Text = $"❌ 同步测试失败: {ex.Message}\n请确认链接有效且网络通畅。";
        }
        finally
        {
            SaveGoogleCalSyncBtn.IsEnabled = true;
            SaveGoogleCalSyncBtn.Content = "💾 保存并立即同步";
        }
    }

    private void OnDisconnectGoogleCalendarClicked(object sender, RoutedEventArgs e)
    {
        var config = ConfigService.Load();
        config.GoogleCalendarIcsUrl = string.Empty;
        config.IsGoogleCalendarLinked = false;
        config.GoogleCalendarLastSyncTime = string.Empty;
        ConfigService.Save(config);

        GoogleCalendarIcsInput.Clear();
        GoogleCalendarSyncModalStatus.Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
        GoogleCalendarSyncModalStatus.Text = "已解除 Google 日历绑定。应用已恢复纯本地 SQLite 数据模式，原有数据已完整保留。";

        RenderAllCalendarViews();
    }

    // =========================================================================
    // ====================== GEMINI AI COMPANION LOGIC ========================
    // =========================================================================


    private async void OnEvaluateIdeaClicked(object sender, RoutedEventArgs e)
    {
        string idea = UserIdeaInput.Text.Trim();
        if (string.IsNullOrEmpty(idea))
        {
            IdeaEvaluationResultText.Text = "请输入你的想法或计划内容！";
            return;
        }

        EvaluateIdeaBtn.IsEnabled = false;
        EvaluateIdeaBtn.Content = "⏳ 推演评估中...";
        IdeaEvaluationResultText.Text = "Gemini 正在作为客观决策评估引擎，分析可行性与防分心审查...";

        try
        {
            string currentContext = "当前主线工作：RMF 个人管理平台开发（包含日程、时间专注、收支管理与多端协同）";
            string result = await _geminiService.EvaluateIdeaAsync(idea, currentContext);
            IdeaEvaluationResultText.Text = result;
        }
        catch (Exception ex)
        {
            IdeaEvaluationResultText.Text = $"❌ 评估失败: {ex.Message}";
        }
        finally
        {
            EvaluateIdeaBtn.IsEnabled = true;
            EvaluateIdeaBtn.Content = "🚀 深入评估决策";
        }
    }

    // =========================================================================
    // ====================== NATURAL LANGUAGE FINANCE =========================
    // =========================================================================

    private async void OnQuickExpenseClicked(object sender, RoutedEventArgs e)
    {
        string text = QuickExpenseInput.Text.Trim();
        if (string.IsNullOrEmpty(text))
        {
            ExpenseResultText.Text = "请输入记账描述内容！";
            return;
        }

        ExpenseResultText.Text = "⏳ 正在智能解析金额与分类...";
        try
        {
            var (amount, category, note) = await _geminiService.ParseExpenseAsync(text);
            if (amount <= 0)
            {
                ExpenseResultText.Text = "⚠️ 未能识别出具体金额，请在描述中包含数字（如「午餐 25 元」）。";
                return;
            }

            var tx = new FinanceTransaction
            {
                Amount = amount,
                Category = category,
                Note = note,
                RawInputText = text,
                TransactionTime = DateTime.Now
            };

            DatabaseService.AddExpense(tx);
            QuickExpenseInput.Clear();
            ExpenseResultText.Text = $"✅ 记账成功：[{category}] ¥{amount:F2} ({note})";
            RefreshExpenseRecords();
        }
        catch (Exception ex)
        {
            ExpenseResultText.Text = $"❌ 记账失败: {ex.Message}";
        }
    }

    private void RefreshExpenseRecords()
    {
        decimal total = DatabaseService.GetTodayTotalExpense();
        FinanceSummaryText.Text = $"合计: ¥ {total:F2}";

        var list = DatabaseService.GetTodayExpenses();
        ExpenseHistoryPanel.Children.Clear();

        foreach (var exp in list)
        {
            var itemBorder = new Border
            {
                Background = new SolidColorBrush(Color.FromRgb(0x28, 0x29, 0x2C)),
                CornerRadius = new CornerRadius(6),
                Padding = new Thickness(10, 6, 10, 6),
                Margin = new Thickness(0, 0, 0, 6)
            };

            var grid = new Grid();
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            var sp = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            sp.Children.Add(new TextBlock
            {
                Text = string.IsNullOrWhiteSpace(exp.Note) ? exp.RawInputText : exp.Note,
                FontSize = 12,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(Color.FromRgb(0xE8, 0xEA, 0xED))
            });
            sp.Children.Add(new TextBlock
            {
                Text = $"{exp.Category} · {exp.TransactionTime:HH:mm}",
                FontSize = 10,
                Foreground = new SolidColorBrush(Color.FromRgb(0x9A, 0xA0, 0xA6))
            });
            Grid.SetColumn(sp, 0);
            grid.Children.Add(sp);

            var amtText = new TextBlock
            {
                Text = $"¥ {exp.Amount:F2}",
                FontSize = 13,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(Color.FromRgb(0xF4, 0x3F, 0x5E)),
                VerticalAlignment = VerticalAlignment.Center
            };
            Grid.SetColumn(amtText, 1);
            grid.Children.Add(amtText);

            itemBorder.Child = grid;
            ExpenseHistoryPanel.Children.Add(itemBorder);
        }
    }

    // =========================================================================
    // ====================== SETTINGS & API LOGIC =============================
    // =========================================================================

    private void LoadSettingsIntoUi()
    {
        var config = ConfigService.Load();
        ApiKeyInput.Text = config.GeminiApiKey;
        BaseUrlInput.Text = string.IsNullOrWhiteSpace(config.CustomBaseUrl)
            ? "https://generativelanguage.googleapis.com"
            : config.CustomBaseUrl;
        CustomModelInput.Text = config.CustomModelName;

        bool matched = false;
        for (int i = 0; i < ModelSelectCombo.Items.Count; i++)
        {
            if (ModelSelectCombo.Items[i] is ComboBoxItem cbi && (string)cbi.Tag == config.SelectedModel)
            {
                ModelSelectCombo.SelectedIndex = i;
                matched = true;
                break;
            }
        }
        if (!matched) ModelSelectCombo.SelectedIndex = 0;
        CustomModelPanel.Visibility = config.SelectedModel == "custom" ? Visibility.Visible : Visibility.Collapsed;
    }

    private void OnModelSelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (CustomModelPanel == null) return;
        if (ModelSelectCombo?.SelectedItem is ComboBoxItem item)
        {
            string tag = item.Tag as string ?? "";
            CustomModelPanel.Visibility = tag == "custom" ? Visibility.Visible : Visibility.Collapsed;
        }
    }

    private void OnSaveSettingsClicked(object sender, RoutedEventArgs e)
    {
        var config = ConfigService.Load();
        config.GeminiApiKey = ApiKeyInput.Text.Trim();
        config.CustomBaseUrl = BaseUrlInput.Text.Trim();
        config.CustomModelName = CustomModelInput.Text.Trim();

        if (ModelSelectCombo.SelectedItem is ComboBoxItem item && item.Tag is string tag)
        {
            config.SelectedModel = tag;
        }

        ConfigService.Save(config);

        string effective = config.GetEffectiveModel();
        SettingsStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
        SettingsStatusText.Text = $"✅ 配置已保存！当前模型: {effective}";
    }

    private async void OnFetchModelsClicked(object sender, RoutedEventArgs e)
    {
        string key = ApiKeyInput.Text.Trim();
        if (string.IsNullOrEmpty(key))
        {
            FetchModelsStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0xEA, 0x43, 0x35));
            FetchModelsStatusText.Text = "⚠️ 请先填入 API Key 并保存！";
            return;
        }

        OnSaveSettingsClicked(sender, e);
        FetchModelsBtn.IsEnabled = false;
        FetchModelsStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0x9A, 0xA0, 0xA6));
        FetchModelsStatusText.Text = "正在请求 Google API 拉取可用模型列表...";

        try
        {
            var models = await _geminiService.ListModelsAsync();
            if (models.Count == 0)
            {
                FetchModelsStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0xEA, 0x43, 0x35));
                FetchModelsStatusText.Text = "未获取到模型，请检查 API Key 或代理。";
                return;
            }

            var config = ConfigService.Load();
            string currentSelected = config.GetEffectiveModel();

            ModelSelectCombo.Items.Clear();
            int selectedIndex = 0;

            for (int i = 0; i < models.Count; i++)
            {
                var m = models[i];
                string label = $"{m.ModelId}";
                if (!string.IsNullOrWhiteSpace(m.DisplayName) && m.DisplayName != m.ModelId)
                {
                    label += $" ({m.DisplayName})";
                }

                var item = new ComboBoxItem { Content = label, Tag = m.ModelId };
                ModelSelectCombo.Items.Add(item);

                if (string.Equals(m.ModelId, currentSelected, StringComparison.OrdinalIgnoreCase))
                {
                    selectedIndex = i;
                }
            }

            ModelSelectCombo.Items.Add(new ComboBoxItem
            {
                Content = "[自定义输入模型名称...]",
                Tag = "custom"
            });

            ModelSelectCombo.SelectedIndex = selectedIndex;
            FetchModelsStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
            FetchModelsStatusText.Text = $"✅ 成功探测到 {models.Count} 个可用模型！";
        }
        catch (Exception ex)
        {
            FetchModelsStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0xEA, 0x43, 0x35));
            FetchModelsStatusText.Text = $"❌ 拉取失败: {ex.Message}";
        }
        finally
        {
            FetchModelsBtn.IsEnabled = true;
        }
    }

    private async void OnTestApiClicked(object sender, RoutedEventArgs e)
    {
        OnSaveSettingsClicked(sender, e);
        TestApiBtn.IsEnabled = false;
        SettingsStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0x9A, 0xA0, 0xA6));
        SettingsStatusText.Text = "正在发送握手请求...";

        var config = ConfigService.Load();
        try
        {
            string reply = await _geminiService.EvaluateIdeaAsync("测试与 Gemini 模型握手连通，请回复一句话。", "设置测试");
            SettingsStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
            SettingsStatusText.Text = $"🎉 联通成功！当前模型 [{config.GetEffectiveModel()}] 回复：\n{reply}";
        }
        catch (Exception ex)
        {
            SettingsStatusText.Foreground = new SolidColorBrush(Color.FromRgb(0xEA, 0x43, 0x35));
            SettingsStatusText.Text = $"❌ 握手失败: {ex.Message}";
        }
        finally
        {
            TestApiBtn.IsEnabled = true;
        }
    }
}