using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Interop;
using System.Windows.Media;
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
    private readonly HashSet<string> _activeCategoryFilters = new(StringComparer.OrdinalIgnoreCase)
    {
        "核心研发", "WORK", "工作",
        "深度学习", "STUDY", "学习",
        "运动健康", "HEALTH", "健康",
        "日常事务", "LIFE", "生活",
        "财务管理", "FINANCE", "财务",
        "Google 订阅", "GOOGLE", "未分类"
    };

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
            ScrollWeekToCurrentTime();
            UpdateTelemetry();
            LoadSettingsIntoUi();
        };

        _perfTimer = new DispatcherTimer
        {
            Interval = TimeSpan.FromSeconds(2)
        };
        _perfTimer.Tick += (s, e) => UpdateTelemetry();
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
        LogoDateNumText.Text = DateTime.Today.Day.ToString();

        // 1. 更新顶部日期大标题 (依照 Google Calendar 规则)
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

        // 3. 更新同步状态徽章
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
    }

    private void UpdateSyncStatusBadge()
    {
        var config = ConfigService.Load();
        if (config.IsGoogleCalendarLinked && !string.IsNullOrWhiteSpace(config.GoogleCalendarIcsUrl))
        {
            CalSyncStatusDot.Fill = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
            string timeStr = string.IsNullOrWhiteSpace(config.GoogleCalendarLastSyncTime) ? "就绪" : config.GoogleCalendarLastSyncTime;
            CalSyncStatusText.Text = $"🟢 Google 日历已连接 ({timeStr})";
            CalSyncBadge.BorderBrush = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
        }
        else
        {
            CalSyncStatusDot.Fill = new SolidColorBrush(Color.FromRgb(0x94, 0xA3, 0xB8));
            CalSyncStatusText.Text = "💾 本地数据模式 (离线就绪)";
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
                Cursor = System.Windows.Input.Cursors.Hand,
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

        // 24 个小时刻度
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

        // 7 列，每列 24 小时单元格
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
                    Cursor = System.Windows.Input.Cursors.Hand,
                    ToolTip = $"点击创建日程"
                };

                slot.MouseLeftButtonUp += (s, e) =>
                {
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

            // 日期数字圆圈 (Today 为经典 Google 蓝实心圆)
            var dateBadge = new Border
            {
                Width = 36,
                Height = 36,
                CornerRadius = new CornerRadius(18),
                Background = isToday ? new SolidColorBrush(Color.FromRgb(0x1A, 0x73, 0xE8)) : Brushes.Transparent,
                HorizontalAlignment = HorizontalAlignment.Center,
                Cursor = System.Windows.Input.Cursors.Hand
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

        foreach (var item in filteredEvents)
        {
            int col = (int)(item.StartTime.Date - monday.Date).TotalDays;
            if (col < 0 || col >= 7) continue;

            double startH = item.StartTime.Hour + item.StartTime.Minute / 60.0;
            double endH = item.EndTime.Hour + item.EndTime.Minute / 60.0;
            if (item.EndTime.Date > item.StartTime.Date) endH = 24.0;
            if (endH <= startH) endH = startH + 0.75;

            double top = startH * HourHeight + 1;
            double height = Math.Max(22.0, (endH - startH) * HourHeight - 2);
            double left = col * colWidth + 2;
            double width = Math.Max(20.0, colWidth - 4);

            var card = CreateWeekEventChip(item, width, height);
            Canvas.SetLeft(card, left);
            Canvas.SetTop(card, top);
            WeekEventsCanvas.Children.Add(card);
        }
    }

    private UIElement CreateWeekEventChip(ScheduleItem item, double width, double height)
    {
        var (bgBrush, borderBrush, textBrush) = GetGoogleEventColors(item.Category);

        var border = new Border
        {
            Width = width,
            Height = height,
            Background = bgBrush,
            BorderBrush = item.Status == "IN_PROGRESS" ? new SolidColorBrush(Color.FromRgb(0x38, 0xBD, 0xF8)) : borderBrush,
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(4),
            Padding = new Thickness(6, 2, 6, 2),
            Cursor = System.Windows.Input.Cursors.Hand,
            Opacity = item.Status == "COMPLETED" ? 0.65 : 1.0,
            ToolTip = $"{item.Title}\n{item.StartTime:HH:mm} - {item.EndTime:HH:mm}\n分类: {item.Category}\n点击查看或修改"
        };

        var panel = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
        var titleText = new TextBlock
        {
            Text = (item.Status == "COMPLETED" ? "✓ " : "") + item.Title,
            FontSize = 11,
            FontWeight = FontWeights.SemiBold,
            Foreground = textBrush,
            TextTrimming = TextTrimming.CharacterEllipsis,
            TextDecorations = item.Status == "COMPLETED" ? TextDecorations.Strikethrough : null
        };
        panel.Children.Add(titleText);

        if (height >= 34)
        {
            var timeText = new TextBlock
            {
                Text = $"{item.StartTime:HH:mm} - {item.EndTime:HH:mm}",
                FontSize = 10,
                Foreground = new SolidColorBrush(Color.FromArgb(200, 0xFF, 0xFF, 0xFF)),
                TextTrimming = TextTrimming.CharacterEllipsis
            };
            panel.Children.Add(timeText);
        }

        border.Child = panel;
        border.MouseLeftButtonUp += (s, e) =>
        {
            e.Handled = true;
            OpenEventEditModal(item);
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
                Cursor = System.Windows.Input.Cursors.Hand,
                ToolTip = "点击创建日程"
            };
            slot.MouseLeftButtonUp += (s, e) => OpenEventCreateModal(_currentDate, hCaptured);
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

        // 1. 红线指示器
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

        // 2. 渲染当天事件
        var rawEvents = DatabaseService.GetSchedulesForDate(_currentDate);
        var filteredEvents = rawEvents.Where(MatchesFilter).ToList();

        foreach (var item in filteredEvents)
        {
            double startH = item.StartTime.Hour + item.StartTime.Minute / 60.0;
            double endH = item.EndTime.Hour + item.EndTime.Minute / 60.0;
            if (item.EndTime.Date > item.StartTime.Date) endH = 24.0;
            if (endH <= startH) endH = startH + 0.75;

            double top = startH * HourHeight + 1;
            double height = Math.Max(26.0, (endH - startH) * HourHeight - 2);
            double width = Math.Max(200.0, canvasWidth - 16);

            var card = CreateDayEventCard(item, width, height);
            Canvas.SetLeft(card, 8);
            Canvas.SetTop(card, top);
            DayEventsCanvas.Children.Add(card);
        }
    }

    private UIElement CreateDayEventCard(ScheduleItem item, double width, double height)
    {
        var (bgBrush, borderBrush, textBrush) = GetGoogleEventColors(item.Category);

        var border = new Border
        {
            Width = width,
            Height = height,
            Background = bgBrush,
            BorderBrush = item.Status == "IN_PROGRESS" ? new SolidColorBrush(Color.FromRgb(0x38, 0xBD, 0xF8)) : borderBrush,
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(6),
            Padding = new Thickness(10, 4, 10, 4),
            Cursor = System.Windows.Input.Cursors.Hand,
            Opacity = item.Status == "COMPLETED" ? 0.7 : 1.0,
            ToolTip = "点击查看或编辑"
        };

        var grid = new Grid();
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        var infoPanel = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
        var titleRow = new StackPanel { Orientation = Orientation.Horizontal };
        titleRow.Children.Add(new TextBlock
        {
            Text = (item.Status == "COMPLETED" ? "✓ " : "") + item.Title,
            FontSize = 13,
            FontWeight = FontWeights.Bold,
            Foreground = textBrush,
            TextTrimming = TextTrimming.CharacterEllipsis,
            TextDecorations = item.Status == "COMPLETED" ? TextDecorations.Strikethrough : null
        });
        titleRow.Children.Add(new TextBlock
        {
            Text = $" · {item.StartTime:HH:mm} - {item.EndTime:HH:mm}",
            FontSize = 12,
            Foreground = new SolidColorBrush(Color.FromArgb(220, 0xFF, 0xFF, 0xFF)),
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(6, 0, 0, 0)
        });
        infoPanel.Children.Add(titleRow);

        if (height >= 46 && !string.IsNullOrWhiteSpace(item.Description))
        {
            infoPanel.Children.Add(new TextBlock
            {
                Text = item.Description,
                FontSize = 11,
                Foreground = new SolidColorBrush(Color.FromArgb(200, 0xFF, 0xFF, 0xFF)),
                TextTrimming = TextTrimming.CharacterEllipsis,
                Margin = new Thickness(0, 2, 0, 0)
            });
        }
        Grid.SetColumn(infoPanel, 0);
        grid.Children.Add(infoPanel);

        // Status badge button
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
            FontSize = 11,
            Cursor = System.Windows.Input.Cursors.Hand,
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
        };
        Grid.SetColumn(statusBtn, 1);
        grid.Children.Add(statusBtn);

        border.Child = grid;
        border.MouseLeftButtonUp += (s, e) => OpenEventEditModal(item);
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
        DateTime startCalendarDate = firstOfMonth.AddDays(-offset);
        DateTime endCalendarDate = startCalendarDate.AddDays(42);

        var monthEvents = DatabaseService.GetSchedulesForDateRange(startCalendarDate, endCalendarDate);

        for (int i = 0; i < 42; i++)
        {
            DateTime cellDate = startCalendarDate.AddDays(i);
            bool isCurrentMonth = cellDate.Month == _currentDate.Month;
            bool isToday = cellDate.Date == DateTime.Today;

            var cellBorder = new Border
            {
                BorderBrush = new SolidColorBrush(Color.FromRgb(0x3C, 0x40, 0x43)),
                BorderThickness = new Thickness(0, 0, 1, 1),
                Background = isCurrentMonth ? Brushes.Transparent : new SolidColorBrush(Color.FromArgb(30, 0x00, 0x00, 0x00)),
                Padding = new Thickness(4)
            };

            var cellPanel = new Grid();
            cellPanel.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            cellPanel.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });

            // Date Number Badge
            var numBadge = new Border
            {
                Width = 24,
                Height = 24,
                CornerRadius = new CornerRadius(12),
                Background = isToday ? new SolidColorBrush(Color.FromRgb(0x1A, 0x73, 0xE8)) : Brushes.Transparent,
                HorizontalAlignment = HorizontalAlignment.Right,
                Margin = new Thickness(0, 0, 2, 2)
            };
            numBadge.Child = new TextBlock
            {
                Text = cellDate.Day.ToString(),
                FontSize = 11,
                FontWeight = (isToday || isCurrentMonth) ? FontWeights.SemiBold : FontWeights.Normal,
                Foreground = isToday ? Brushes.White : (isCurrentMonth ? new SolidColorBrush(Color.FromRgb(0xE8, 0xEA, 0xED)) : new SolidColorBrush(Color.FromRgb(0x71, 0x71, 0x7A))),
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            Grid.SetRow(numBadge, 0);
            cellPanel.Children.Add(numBadge);

            // Events List in this day cell
            var dayEvents = monthEvents.Where(e => e.StartTime.Date == cellDate.Date && MatchesFilter(e)).Take(3).ToList();
            var eventsStack = new StackPanel();
            foreach (var ev in dayEvents)
            {
                var (bgBrush, _, textBrush) = GetGoogleEventColors(ev.Category);
                var chip = new Border
                {
                    Background = bgBrush,
                    CornerRadius = new CornerRadius(3),
                    Padding = new Thickness(4, 1, 4, 1),
                    Margin = new Thickness(0, 1, 0, 1),
                    Cursor = System.Windows.Input.Cursors.Hand,
                    ToolTip = $"{ev.Title}\n{ev.StartTime:HH:mm} - {ev.EndTime:HH:mm}"
                };
                chip.Child = new TextBlock
                {
                    Text = $"{ev.StartTime:HH:mm} {ev.Title}",
                    FontSize = 10,
                    Foreground = textBrush,
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
        var (bgBrush, borderBrush, textBrush) = GetGoogleEventColors(item.Category);

        var border = new Border
        {
            Background = new SolidColorBrush(Color.FromRgb(0x28, 0x29, 0x2C)),
            BorderBrush = new SolidColorBrush(Color.FromRgb(0x3C, 0x40, 0x43)),
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(8),
            Padding = new Thickness(14, 10, 14, 10),
            Margin = new Thickness(0, 0, 0, 8),
            Cursor = System.Windows.Input.Cursors.Hand
        };

        var grid = new Grid();
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        // Category Tag indicator
        var catPill = new Border
        {
            Background = bgBrush,
            CornerRadius = new CornerRadius(4),
            Padding = new Thickness(6, 2, 6, 2),
            Margin = new Thickness(0, 0, 12, 0),
            VerticalAlignment = VerticalAlignment.Center
        };
        catPill.Child = new TextBlock { Text = item.Category, FontSize = 11, Foreground = textBrush, FontWeight = FontWeights.SemiBold };
        Grid.SetColumn(catPill, 0);
        grid.Children.Add(catPill);

        var details = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
        details.Children.Add(new TextBlock
        {
            Text = item.Title,
            FontSize = 13,
            FontWeight = FontWeights.Bold,
            Foreground = new SolidColorBrush(Color.FromRgb(0xE8, 0xEA, 0xED)),
            TextDecorations = item.Status == "COMPLETED" ? TextDecorations.Strikethrough : null
        });
        details.Children.Add(new TextBlock
        {
            Text = $"{item.StartTime:HH:mm} - {item.EndTime:HH:mm}",
            FontSize = 11,
            Foreground = new SolidColorBrush(Color.FromRgb(0x9A, 0xA0, 0xA6)),
            Margin = new Thickness(0, 2, 0, 0)
        });
        Grid.SetColumn(details, 1);
        grid.Children.Add(details);

        // Status Button
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
            Cursor = System.Windows.Input.Cursors.Hand,
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
        };
        Grid.SetColumn(statusBtn, 2);
        grid.Children.Add(statusBtn);

        border.Child = grid;
        border.MouseLeftButtonUp += (s, e) => OpenEventEditModal(item);
        return border;
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
        _editingEventId = null;
        _clickedSlotDateTime = date.Date.AddHours(hour);

        EventModalHeaderTitle.Text = "添加日程时间块";
        EventTitleInput.Clear();
        EventDescInput.Clear();
        EventDateDisplayText.Text = date.ToString("yyyy年M月d日");
        EventStartTimeInput.Text = $"{hour:D2}:00";
        EventEndTimeInput.Text = $"{(hour + 1):D2}:00";
        EventCategoryCombo.SelectedIndex = 0;
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
        EventDateDisplayText.Text = item.StartTime.ToString("yyyy年M月d日");
        EventStartTimeInput.Text = item.StartTime.ToString("HH:mm");
        EventEndTimeInput.Text = item.EndTime.ToString("HH:mm");

        for (int i = 0; i < EventCategoryCombo.Items.Count; i++)
        {
            if (EventCategoryCombo.Items[i] is ComboBoxItem cbi && (cbi.Tag as string == item.Category || cbi.Content.ToString()!.Contains(item.Category)))
            {
                EventCategoryCombo.SelectedIndex = i;
                break;
            }
        }

        EventDeleteBtn.Visibility = Visibility.Visible;
        EventSaveBtn.Content = "更新保存";
        EventModal.Visibility = Visibility.Visible;
        EventTitleInput.Focus();
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

        string category = "核心研发";
        if (EventCategoryCombo.SelectedItem is ComboBoxItem cbi && cbi.Tag is string tag)
        {
            category = tag switch
            {
                "WORK" => "核心研发",
                "STUDY" => "深度学习",
                "HEALTH" => "运动健康",
                "LIFE" => "日常事务",
                "FINANCE" => "财务管理",
                _ => "核心研发"
            };
        }

        DateTime baseDate = _clickedSlotDateTime.Date;
        DateTime startTime = baseDate.AddHours(9);
        DateTime endTime = startTime.AddHours(1);

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

        var item = new ScheduleItem
        {
            Id = _editingEventId ?? Guid.NewGuid().ToString(),
            Title = title,
            Description = EventDescInput.Text.Trim(),
            Category = category,
            Priority = "HIGH",
            Status = "PENDING",
            StartTime = startTime,
            EndTime = endTime,
            EstimatedMinutes = (int)(endTime - startTime).TotalMinutes
        };

        DatabaseService.UpsertSchedule(item);
        EventModal.Visibility = Visibility.Collapsed;
        RenderAllCalendarViews();
    }

    private void OnDeleteEventModalClicked(object sender, RoutedEventArgs e)
    {
        if (!string.IsNullOrEmpty(_editingEventId))
        {
            DatabaseService.DeleteSchedule(_editingEventId);
        }
        EventModal.Visibility = Visibility.Collapsed;
        RenderAllCalendarViews();
    }

    // =========================================================================
    // ======================= COLOR MAPPINGS & FILTERS ========================
    // =========================================================================

    private static (Brush bg, Brush border, Brush text) GetGoogleEventColors(string category)
    {
        return category switch
        {
            "核心研发" or "WORK" or "工作" => (
                new SolidColorBrush(Color.FromRgb(0x1A, 0x73, 0xE8)), // Google Blue
                new SolidColorBrush(Color.FromRgb(0x18, 0x5A, 0xBC)),
                Brushes.White
            ),
            "深度学习" or "STUDY" or "学习" => (
                new SolidColorBrush(Color.FromRgb(0x8E, 0x24, 0xAA)), // Google Purple
                new SolidColorBrush(Color.FromRgb(0x6A, 0x1B, 0x9A)),
                Brushes.White
            ),
            "运动健康" or "HEALTH" or "健康" => (
                new SolidColorBrush(Color.FromRgb(0x0B, 0x80, 0x43)), // Google Green
                new SolidColorBrush(Color.FromRgb(0x07, 0x60, 0x33)),
                Brushes.White
            ),
            "财务管理" or "FINANCE" or "财务" => (
                new SolidColorBrush(Color.FromRgb(0xD5, 0x00, 0x00)), // Google Red
                new SolidColorBrush(Color.FromRgb(0xB7, 0x1C, 0x1C)),
                Brushes.White
            ),
            "日常事务" or "LIFE" or "生活" => (
                new SolidColorBrush(Color.FromRgb(0xF4, 0x51, 0x1E)), // Google Amber
                new SolidColorBrush(Color.FromRgb(0xD8, 0x43, 0x15)),
                Brushes.White
            ),
            _ => (
                new SolidColorBrush(Color.FromRgb(0x00, 0x83, 0x8F)), // Google Cyan
                new SolidColorBrush(Color.FromRgb(0x00, 0x60, 0x64)),
                Brushes.White
            )
        };
    }

    private bool MatchesFilter(ScheduleItem item)
    {
        // 1. 分类过滤
        if (!_activeCategoryFilters.Contains(item.Category)) return false;

        // 2. 搜索关键字过滤
        if (!string.IsNullOrWhiteSpace(_searchKeyword))
        {
            bool inTitle = item.Title.Contains(_searchKeyword, StringComparison.OrdinalIgnoreCase);
            bool inDesc = item.Description.Contains(_searchKeyword, StringComparison.OrdinalIgnoreCase);
            return inTitle || inDesc;
        }

        return true;
    }

    private void OnCategoryFilterChanged(object sender, RoutedEventArgs e)
    {
        _activeCategoryFilters.Clear();
        if (CatCheckWork.IsChecked == true) { _activeCategoryFilters.Add("核心研发"); _activeCategoryFilters.Add("WORK"); _activeCategoryFilters.Add("工作"); }
        if (CatCheckStudy.IsChecked == true) { _activeCategoryFilters.Add("深度学习"); _activeCategoryFilters.Add("STUDY"); _activeCategoryFilters.Add("学习"); }
        if (CatCheckHealth.IsChecked == true) { _activeCategoryFilters.Add("运动健康"); _activeCategoryFilters.Add("HEALTH"); _activeCategoryFilters.Add("健康"); }
        if (CatCheckLife.IsChecked == true) { _activeCategoryFilters.Add("日常事务"); _activeCategoryFilters.Add("LIFE"); _activeCategoryFilters.Add("生活"); }
        if (CatCheckFinance.IsChecked == true) { _activeCategoryFilters.Add("财务管理"); _activeCategoryFilters.Add("FINANCE"); _activeCategoryFilters.Add("财务"); }
        if (CatCheckGoogle.IsChecked == true) { _activeCategoryFilters.Add("Google 订阅"); _activeCategoryFilters.Add("GOOGLE"); _activeCategoryFilters.Add("未分类"); }

        RenderAllCalendarViews();
    }

    private void OnSearchEventsTextChanged(object sender, TextChangedEventArgs e)
    {
        _searchKeyword = SearchEventsInput.Text.Trim();
        RenderAllCalendarViews();
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
            LeftSidebarColumn.Width = new GridLength(256);
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

        RightDrawerColumn.Width = new GridLength(340);
        DrawerGeminiPanel.Visibility = drawerType == "Gemini" ? Visibility.Visible : Visibility.Collapsed;
        DrawerExpensePanel.Visibility = drawerType == "Expense" ? Visibility.Visible : Visibility.Collapsed;
        DrawerSettingsPanel.Visibility = drawerType == "Settings" ? Visibility.Visible : Visibility.Collapsed;

        if (drawerType == "Expense")
        {
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
        try
        {
            var (added, updated) = await _googleCalendarService.SyncFromIcsAsync(config.GoogleCalendarIcsUrl);
            RenderAllCalendarViews();
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
        SaveGoogleCalSyncBtn.Content = "⏳ 正在连接测试与同步...";
        GoogleCalendarSyncModalStatus.Foreground = new SolidColorBrush(Color.FromRgb(0x9A, 0xA0, 0xA6));
        GoogleCalendarSyncModalStatus.Text = "正在从 Google Calendar 拉取 iCal 数据流...";

        try
        {
            var (added, updated) = await _googleCalendarService.SyncFromIcsAsync(url);
            GoogleCalendarSyncModalStatus.Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
            GoogleCalendarSyncModalStatus.Text = $"🎉 成功同步！已从 Google 日历解析并导入 {added} 项日程存入本地 SQLite。";

            RenderAllCalendarViews();
            await Task.Delay(1000);
            GoogleCalendarSyncModal.Visibility = Visibility.Collapsed;
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

    private async void OnAuditScheduleClicked(object sender, RoutedEventArgs e)
    {
        DateTime monday = GetMondayOfWeek(_currentDate);
        var weekEvents = DatabaseService.GetSchedulesForDateRange(monday, monday.AddDays(7));

        if (weekEvents.Count == 0)
        {
            AuditResultText.Text = "ℹ️ 当前周期数据库中暂无排期日程。\n\n请先点击左上角「+ 创建」或同步 Google 日历规划该日期的任务时间块，Gemini 将基于你的真实安排与前台活动进行客观负荷审计。";
            return;
        }

        AuditScheduleBtn.IsEnabled = false;
        AuditScheduleBtn.Content = "⏳ Gemini 正在审查当前排期与负荷...";
        AuditResultText.Text = "正在连接 Google Gemini API 分析安排与桌面活动流，请稍候...";

        try
        {
            string currentActivity = ActiveWindowText.Text;
            string feedback = await _geminiService.AuditScheduleAsync(weekEvents, currentActivity);
            AuditResultText.Text = feedback;
        }
        catch (Exception ex)
        {
            AuditResultText.Text = $"❌ 审查失败: {ex.Message}\n\n请先在侧边栏「⚙️ 设置」填入你的 Google AI Studio API Key。";
        }
        finally
        {
            AuditScheduleBtn.IsEnabled = true;
            AuditScheduleBtn.Content = "✨ 重新审查当前日程与负荷";
        }
    }

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
            EvaluateIdeaBtn.Content = "🚀 让 Gemini 深入评估";
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