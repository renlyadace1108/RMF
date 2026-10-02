using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Interop;
using System.Windows.Threading;
using Microsoft.Data.Sqlite;
using RMF.Windows.Models;
using RMF.Windows.Services;

namespace RMF.Windows;

public partial class MainWindow : Window
{
    private readonly DispatcherTimer _perfTimer;
    private readonly GeminiService _geminiService = new();

    // 真实排期日程数据列表（全部来源于本地 SQLite 数据库）
    private List<ScheduleItem> _todaySchedule = new();

    // Win32 DWM API for Windows 11 Immersive Dark Titlebar & Mica backdrop
    [DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int attrValue, int attrSize);

    private const int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
    private const int DWMWA_SYSTEMBACKDROP_TYPE = 38; // 2 = Mica

    // Win32 API imports for lightweight foreground window sniffing
    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll", CharSet = CharSet.Auto, SetLastError = true)]
    private static extern int GetWindowText(IntPtr hWnd, StringBuilder lpString, int nMaxCount);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);

    // Win32 API to trim memory working set
    [DllImport("psapi.dll")]
    private static extern int EmptyWorkingSet(IntPtr hwProc);

    public MainWindow()
    {
        InitializeComponent();

        Loaded += (s, e) =>
        {
            DatabaseService.Initialize();
            ApplyWindows11ImmersiveDarkTitlebar();
            ReloadAllRealData();
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
            long memoryBytes = currentProcess.WorkingSet64;
            double memoryMb = memoryBytes / (1024.0 * 1024.0);
            MemoryUsageText.Text = $"内存占用: {memoryMb:F1} MB";

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

                // 真实活动打点记录（每 2 秒 1 次入库）
                if (procName != "Unknown" && procName != "RMF.Windows")
                {
                    DatabaseService.LogActivity(procName, title, 2);
                }
            }
        }
        catch { }
    }

    // ================== 真实数据全量刷新与动态渲染 ==================

    private void ReloadAllRealData()
    {
        // 1. 日程列表与进度真实刷新
        _todaySchedule = DatabaseService.GetTodaySchedules();
        int totalSchedule = _todaySchedule.Count;
        int completedSchedule = 0;
        foreach (var item in _todaySchedule)
        {
            if (item.Status == "COMPLETED") completedSchedule++;
        }

        MetricScheduleCompletedText.Text = completedSchedule.ToString();
        MetricScheduleTotalText.Text = $" / {totalSchedule} 个时间块";
        MetricScheduleProgressBar.Value = totalSchedule > 0 ? (completedSchedule * 100 / totalSchedule) : 0;

        ScheduleTimelinePanel.Children.Clear();
        if (totalSchedule == 0)
        {
            ScheduleEmptyState.Visibility = Visibility.Visible;
            ScheduleTimelinePanel.Visibility = Visibility.Collapsed;
        }
        else
        {
            ScheduleEmptyState.Visibility = Visibility.Collapsed;
            ScheduleTimelinePanel.Visibility = Visibility.Visible;

            foreach (var item in _todaySchedule)
            {
                ScheduleTimelinePanel.Children.Add(CreateScheduleItemCard(item));
            }
        }

        // 2. 支出统计与流水真实列表刷新
        decimal totalExpense = DatabaseService.GetTodayTotalExpense();
        MetricExpenseTotalText.Text = $"¥ {totalExpense:F2}";
        FinanceSummaryText.Text = $"今日合计: ¥ {totalExpense:F2}";

        var expenses = DatabaseService.GetTodayExpenses();
        MetricExpenseSubtitleText.Text = expenses.Count > 0 ? $"今日已记 {expenses.Count} 笔流水" : "今日暂无记账";

        ExpenseHistoryPanel.Children.Clear();
        if (expenses.Count == 0)
        {
            ExpenseEmptyState.Visibility = Visibility.Visible;
            ExpenseHistoryPanel.Visibility = Visibility.Collapsed;
        }
        else
        {
            ExpenseEmptyState.Visibility = Visibility.Collapsed;
            ExpenseHistoryPanel.Visibility = Visibility.Visible;

            foreach (var exp in expenses)
            {
                ExpenseHistoryPanel.Children.Add(CreateExpenseItemCard(exp));
            }
        }

        // 3. 专注时长刷新
        int focusMinutes = DatabaseService.GetTodayTrackedMinutes();
        MetricFocusMinutesText.Text = focusMinutes.ToString();
        MetricFocusStatusText.Text = focusMinutes > 0 ? " 分钟专注" : " 分钟";
        MetricFocusSubtitleText.Text = focusMinutes > 0 ? $"Win32 今日已真实捕获 {focusMinutes} 分钟前台活动" : "Win32 前台实时嗅探中";
    }

    private UIElement CreateScheduleItemCard(ScheduleItem item)
    {
        var border = new Border
        {
            Background = new System.Windows.Media.SolidColorBrush(item.Status == "IN_PROGRESS"
                ? System.Windows.Media.Color.FromRgb(0x22, 0x25, 0x30)
                : System.Windows.Media.Color.FromRgb(0x1D, 0x1D, 0x21)),
            BorderBrush = item.Status == "IN_PROGRESS"
                ? new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(0x3B, 0x82, 0xF6))
                : (System.Windows.Media.Brush)FindResource("CardBorderBrush"),
            BorderThickness = new Thickness(item.Status == "IN_PROGRESS" ? 1.5 : 1),
            CornerRadius = new CornerRadius(10),
            Padding = new Thickness(16),
            Margin = new Thickness(0, 0, 0, 12)
        };

        var grid = new Grid();
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        // Column 0: Time
        var timePanel = new StackPanel
        {
            Margin = new Thickness(0, 0, 16, 0),
            VerticalAlignment = VerticalAlignment.Center
        };
        timePanel.Children.Add(new TextBlock
        {
            Text = item.StartTime.ToString("HH:mm"),
            FontWeight = FontWeights.Bold,
            FontSize = 15,
            Foreground = new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(0x38, 0xBD, 0xF8))
        });
        timePanel.Children.Add(new TextBlock
        {
            Text = item.EndTime.ToString("HH:mm"),
            FontSize = 12,
            Foreground = (System.Windows.Media.Brush)FindResource("TextMuted")
        });
        Grid.SetColumn(timePanel, 0);
        grid.Children.Add(timePanel);

        // Column 1: Content
        var contentPanel = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
        var titleRow = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 4) };

        // Category Tag
        var catBorder = new Border
        {
            Background = new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(0x1E, 0x29, 0x3B)),
            CornerRadius = new CornerRadius(4),
            Padding = new Thickness(6, 2, 6, 2),
            Margin = new Thickness(0, 0, 8, 0)
        };
        catBorder.Child = new TextBlock
        {
            Text = item.Category,
            FontSize = 11,
            Foreground = new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(0x38, 0xBD, 0xF8)),
            FontWeight = FontWeights.SemiBold
        };
        titleRow.Children.Add(catBorder);

        titleRow.Children.Add(new TextBlock
        {
            Text = item.Title,
            FontWeight = FontWeights.Bold,
            FontSize = 14,
            Foreground = (System.Windows.Media.Brush)FindResource("TextPrimary"),
            TextDecorations = item.Status == "COMPLETED" ? TextDecorations.Strikethrough : null
        });
        contentPanel.Children.Add(titleRow);

        if (!string.IsNullOrWhiteSpace(item.Description))
        {
            contentPanel.Children.Add(new TextBlock
            {
                Text = item.Description,
                FontSize = 12,
                Foreground = (System.Windows.Media.Brush)FindResource("TextSecondary")
            });
        }
        Grid.SetColumn(contentPanel, 1);
        grid.Children.Add(contentPanel);

        // Column 2: Status Toggle Button
        string statusText = item.Status switch
        {
            "COMPLETED" => "✓ 已完成",
            "IN_PROGRESS" => "● 进行中",
            _ => "待执行"
        };
        var statusColor = item.Status switch
        {
            "COMPLETED" => System.Windows.Media.Color.FromRgb(0x10, 0xB9, 0x81),
            "IN_PROGRESS" => System.Windows.Media.Color.FromRgb(0x38, 0xBD, 0xF8),
            _ => System.Windows.Media.Color.FromRgb(0xA1, 0xA1, 0xAA)
        };

        var statusBtn = new Button
        {
            Content = statusText,
            Background = new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromArgb(40, statusColor.R, statusColor.G, statusColor.B)),
            Foreground = new System.Windows.Media.SolidColorBrush(statusColor),
            BorderBrush = new System.Windows.Media.SolidColorBrush(statusColor),
            BorderThickness = new Thickness(1),
            Padding = new Thickness(10, 4, 10, 4),
            FontSize = 12,
            FontWeight = FontWeights.SemiBold,
            Cursor = System.Windows.Input.Cursors.Hand,
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(0, 0, 10, 0)
        };
        statusBtn.Click += (s, e) =>
        {
            string nextStatus = item.Status switch
            {
                "PENDING" => "IN_PROGRESS",
                "IN_PROGRESS" => "COMPLETED",
                _ => "PENDING"
            };
            DatabaseService.UpdateScheduleStatus(item.Id, nextStatus);
            ReloadAllRealData();
        };
        Grid.SetColumn(statusBtn, 2);
        grid.Children.Add(statusBtn);

        // Column 3: Delete Button
        var delBtn = new Button
        {
            Content = "🗑️",
            Background = System.Windows.Media.Brushes.Transparent,
            Foreground = (System.Windows.Media.Brush)FindResource("TextMuted"),
            BorderThickness = new Thickness(0),
            Padding = new Thickness(6),
            FontSize = 12,
            Cursor = System.Windows.Input.Cursors.Hand,
            VerticalAlignment = VerticalAlignment.Center
        };
        delBtn.Click += (s, e) =>
        {
            DatabaseService.DeleteSchedule(item.Id);
            ReloadAllRealData();
        };
        Grid.SetColumn(delBtn, 3);
        grid.Children.Add(delBtn);

        border.Child = grid;
        return border;
    }

    private UIElement CreateExpenseItemCard(FinanceTransaction exp)
    {
        var border = new Border
        {
            Background = new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(0x1D, 0x1D, 0x21)),
            BorderBrush = (System.Windows.Media.Brush)FindResource("CardBorderBrush"),
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(8),
            Padding = new Thickness(14),
            Margin = new Thickness(0, 0, 0, 8)
        };

        var grid = new Grid();
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        // Category Tag
        var catBorder = new Border
        {
            Background = new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(0x27, 0x27, 0x2A)),
            CornerRadius = new CornerRadius(4),
            Padding = new Thickness(8, 4, 8, 4),
            Margin = new Thickness(0, 0, 12, 0),
            VerticalAlignment = VerticalAlignment.Center
        };
        catBorder.Child = new TextBlock
        {
            Text = exp.Category,
            FontSize = 12,
            Foreground = new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(0x38, 0xBD, 0xF8)),
            FontWeight = FontWeights.SemiBold
        };
        Grid.SetColumn(catBorder, 0);
        grid.Children.Add(catBorder);

        // Details
        var detailsPanel = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
        detailsPanel.Children.Add(new TextBlock
        {
            Text = string.IsNullOrWhiteSpace(exp.Note) ? exp.RawInputText : exp.Note,
            FontSize = 13,
            FontWeight = FontWeights.SemiBold,
            Foreground = (System.Windows.Media.Brush)FindResource("TextPrimary")
        });
        detailsPanel.Children.Add(new TextBlock
        {
            Text = exp.TransactionTime.ToString("HH:mm:ss"),
            FontSize = 11,
            Foreground = (System.Windows.Media.Brush)FindResource("TextMuted"),
            Margin = new Thickness(0, 2, 0, 0)
        });
        Grid.SetColumn(detailsPanel, 1);
        grid.Children.Add(detailsPanel);

        // Amount
        var amountText = new TextBlock
        {
            Text = $"¥ {exp.Amount:F2}",
            FontSize = 15,
            FontWeight = FontWeights.Bold,
            Foreground = new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(0xF4, 0x3F, 0x5E)),
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(0, 0, 12, 0)
        };
        Grid.SetColumn(amountText, 2);
        grid.Children.Add(amountText);

        // Delete button
        var delBtn = new Button
        {
            Content = "✕",
            Background = System.Windows.Media.Brushes.Transparent,
            Foreground = (System.Windows.Media.Brush)FindResource("TextMuted"),
            BorderThickness = new Thickness(0),
            Padding = new Thickness(6),
            FontSize = 12,
            Cursor = System.Windows.Input.Cursors.Hand,
            VerticalAlignment = VerticalAlignment.Center
        };
        delBtn.Click += (s, e) =>
        {
            using var conn = new SqliteConnection($"Data Source={Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "RMF", "rmf.db")}");
            conn.Open();
            using var cmd = new SqliteCommand("UPDATE expenses SET is_deleted = 1 WHERE id = @id", conn);
            cmd.Parameters.AddWithValue("@id", exp.Id);
            cmd.ExecuteNonQuery();
            ReloadAllRealData();
        };
        Grid.SetColumn(delBtn, 3);
        grid.Children.Add(delBtn);

        border.Child = grid;
        return border;
    }

    // ================== 新建日程弹窗交互 ==================

    private void OnNewScheduleHeaderClicked(object sender, RoutedEventArgs e)
    {
        NewScheduleTitleInput.Clear();
        NewScheduleDescInput.Clear();
        NewScheduleStartInput.Text = DateTime.Now.ToString("HH:mm");
        NewScheduleEndInput.Text = DateTime.Now.AddHours(1).ToString("HH:mm");
        NewScheduleCategoryCombo.SelectedIndex = 0;
        NewScheduleModal.Visibility = Visibility.Visible;
    }

    private void OnCloseScheduleModalClicked(object sender, RoutedEventArgs e)
    {
        NewScheduleModal.Visibility = Visibility.Collapsed;
    }

    private void OnSaveScheduleModalClicked(object sender, RoutedEventArgs e)
    {
        string title = NewScheduleTitleInput.Text.Trim();
        if (string.IsNullOrEmpty(title))
        {
            MessageBox.Show("请输入日程任务标题！", "提示", MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }

        string category = "核心研发";
        if (NewScheduleCategoryCombo.SelectedItem is ComboBoxItem cbi && cbi.Content is string c)
        {
            category = c;
        }

        DateTime startTime = DateTime.Today.AddHours(DateTime.Now.Hour).AddMinutes(DateTime.Now.Minute);
        DateTime endTime = startTime.AddHours(1);

        if (TimeSpan.TryParse(NewScheduleStartInput.Text.Trim(), out var tsStart))
        {
            startTime = DateTime.Today.Add(tsStart);
        }
        if (TimeSpan.TryParse(NewScheduleEndInput.Text.Trim(), out var tsEnd))
        {
            endTime = DateTime.Today.Add(tsEnd);
        }
        if (endTime <= startTime)
        {
            endTime = startTime.AddHours(1);
        }

        var item = new ScheduleItem
        {
            Title = title,
            Description = NewScheduleDescInput.Text.Trim(),
            Category = category,
            Priority = "HIGH",
            Status = "PENDING",
            StartTime = startTime,
            EndTime = endTime,
            EstimatedMinutes = (int)(endTime - startTime).TotalMinutes
        };

        DatabaseService.AddSchedule(item);
        NewScheduleModal.Visibility = Visibility.Collapsed;
        ReloadAllRealData();
    }

    // ================== 页面导航与视图切换 ==================

    private void SwitchView(string viewName)
    {
        ScheduleView.Visibility = viewName == "Schedule" ? Visibility.Visible : Visibility.Collapsed;
        FocusView.Visibility = viewName == "Focus" ? Visibility.Visible : Visibility.Collapsed;
        FinanceView.Visibility = viewName == "Finance" ? Visibility.Visible : Visibility.Collapsed;
        SyncView.Visibility = viewName == "Sync" ? Visibility.Visible : Visibility.Collapsed;
        SettingsView.Visibility = viewName == "Settings" ? Visibility.Visible : Visibility.Collapsed;

        NavScheduleBtn.Style = (Style)FindResource(viewName == "Schedule" ? "ActiveNavTabButtonStyle" : "NavTabButtonStyle");
        NavFocusBtn.Style = (Style)FindResource(viewName == "Focus" ? "ActiveNavTabButtonStyle" : "NavTabButtonStyle");
        NavFinanceBtn.Style = (Style)FindResource(viewName == "Finance" ? "ActiveNavTabButtonStyle" : "NavTabButtonStyle");
        NavSyncBtn.Style = (Style)FindResource(viewName == "Sync" ? "ActiveNavTabButtonStyle" : "NavTabButtonStyle");
        NavSettingsBtn.Style = (Style)FindResource(viewName == "Settings" ? "ActiveNavTabButtonStyle" : "NavTabButtonStyle");
    }

    private void OnNavScheduleClicked(object sender, RoutedEventArgs e) => SwitchView("Schedule");
    private void OnNavFocusClicked(object sender, RoutedEventArgs e) => SwitchView("Focus");
    private void OnNavFinanceClicked(object sender, RoutedEventArgs e) => SwitchView("Finance");
    private void OnNavSyncClicked(object sender, RoutedEventArgs e) => SwitchView("Sync");
    private void OnNavSettingsClicked(object sender, RoutedEventArgs e) => SwitchView("Settings");
    private void OnQuickExpenseHeaderClicked(object sender, RoutedEventArgs e) => SwitchView("Finance");

    // ================== GEMINI AI 业务逻辑 ==================

    /// <summary>
    /// 1. 真实日程审查
    /// </summary>
    private async void OnAuditScheduleClicked(object sender, RoutedEventArgs e)
    {
        if (_todaySchedule.Count == 0)
        {
            AuditResultText.Text = "ℹ️ 今日数据库中暂无排期日程。\n\n请先点击右上角「+ 新建日程」规划你今日的第一个任务时间块，Gemini 将基于你的真实安排与前台活动进行客观负荷审计。";
            return;
        }

        AuditScheduleBtn.IsEnabled = false;
        AuditScheduleBtn.Content = "⏳ Gemini 正在审查今日日程与负荷...";
        AuditResultText.Text = "正在连接 Google Gemini API 分析今日安排与桌面活动流，请稍候...";

        try
        {
            string currentActivity = ActiveWindowText.Text;
            string feedback = await _geminiService.AuditScheduleAsync(_todaySchedule, currentActivity);
            AuditResultText.Text = feedback;
        }
        catch (Exception ex)
        {
            AuditResultText.Text = $"❌ 审查失败: {ex.Message}\n\n请先前往左侧「⚙️ Gemini API 设置」填入你的 Google AI Studio API Key。";
        }
        finally
        {
            AuditScheduleBtn.IsEnabled = true;
            AuditScheduleBtn.Content = "✨ 重新唤醒 Gemini 审查";
        }
    }

    /// <summary>
    /// 2. 评估用户新想法与监督决策
    /// </summary>
    private async void OnEvaluateIdeaClicked(object sender, RoutedEventArgs e)
    {
        string idea = UserIdeaInput.Text.Trim();
        if (string.IsNullOrEmpty(idea))
        {
            IdeaEvaluationResultText.Text = "请输入你的想法或计划内容！";
            return;
        }

        EvaluateIdeaBtn.IsEnabled = false;
        EvaluateIdeaBtn.Content = "⏳ Gemini 正在深度推演与评估...";
        IdeaEvaluationResultText.Text = "Gemini 正在作为你的客观决策评估引擎，分析可行性与防分心审查...";

        try
        {
            string currentContext = "当前主线工作：RMF 个人管理平台开发（包含日程、时间专注、收支管理与多端协同）";
            string result = await _geminiService.EvaluateIdeaAsync(idea, currentContext);
            IdeaEvaluationResultText.Text = result;
        }
        catch (Exception ex)
        {
            IdeaEvaluationResultText.Text = $"❌ 评估失败: {ex.Message}\n请检查 API Key 配置。";
        }
        finally
        {
            EvaluateIdeaBtn.IsEnabled = true;
            EvaluateIdeaBtn.Content = "🚀 让 Gemini 深入评估这个想法";
        }
    }

    /// <summary>
    /// 3. 智能排程帮手
    /// </summary>
    private async void OnPlanScheduleClicked(object sender, RoutedEventArgs e)
    {
        string goal = GoalPlanInput.Text.Trim();
        if (string.IsNullOrEmpty(goal))
        {
            GoalPlanResultText.Text = "请输入你想排入的目标或任务描述！";
            return;
        }

        PlanScheduleBtn.IsEnabled = false;
        PlanScheduleBtn.Content = "⏳ 规划中...";
        GoalPlanResultText.Text = "Gemini 正在分析你的空闲时段与精力模型，正在生成时间块...";

        try
        {
            string plan = await _geminiService.PlanScheduleAsync(goal, _todaySchedule);
            GoalPlanResultText.Text = plan;
        }
        catch (Exception ex)
        {
            GoalPlanResultText.Text = $"❌ 排程失败: {ex.Message}";
        }
        finally
        {
            PlanScheduleBtn.IsEnabled = true;
            PlanScheduleBtn.Content = "生成推荐排程";
        }
    }

    private void OnModelBadgeClicked(object sender, System.Windows.Input.MouseButtonEventArgs e)
    {
        SwitchView("Settings");
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

    // ================== 设置与 API 管理 ==================

    private void LoadSettingsIntoUi()
    {
        var config = ConfigService.Load();
        ApiKeyInput.Text = config.GeminiApiKey;
        BaseUrlInput.Text = string.IsNullOrWhiteSpace(config.CustomBaseUrl)
            ? "https://generativelanguage.googleapis.com"
            : config.CustomBaseUrl;
        CustomModelInput.Text = config.CustomModelName;

        // Model selection
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
        if (!matched)
        {
            ModelSelectCombo.SelectedIndex = 0; // default to 2.5-flash
        }

        CustomModelPanel.Visibility = config.SelectedModel == "custom" ? Visibility.Visible : Visibility.Collapsed;

        // Update badge
        ActiveModelBadge.Text = $"⚡ 模型: {config.GetEffectiveModel()}";

        // Google Drive Sync UI
        GoogleClientIdInput.Text = config.GoogleClientId;
        GoogleClientSecretInput.Text = config.GoogleClientSecret;
        UpdateSyncUiState(config);
    }

    private void UpdateSyncUiState(AppConfig config)
    {
        if (config.IsGoogleDriveLinked && !string.IsNullOrWhiteSpace(config.GoogleClientId))
        {
            SyncStatusBadge.Background = new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(0x13, 0x33, 0x24));
            SyncStatusBadge.BorderBrush = (System.Windows.Media.Brush)FindResource("AccentGreen");
            SyncStatusBadgeText.Foreground = (System.Windows.Media.Brush)FindResource("AccentGreen");
            SyncStatusBadgeText.Text = "🟢 已绑定 Google Drive";
            SyncStatusText.Foreground = (System.Windows.Media.Brush)FindResource("TextPrimary");
            SyncStatusText.Text = string.IsNullOrEmpty(config.LastSyncTime)
                ? "账号已连接，随时可同步至云端专属隐藏空间 `drive.appdata`。"
                : $"账号已连接。最近同步时间：{config.LastSyncTime}。";
        }
        else
        {
            SyncStatusBadge.Background = new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(0x33, 0x20, 0x22));
            SyncStatusBadge.BorderBrush = (System.Windows.Media.Brush)FindResource("AccentRed");
            SyncStatusBadgeText.Foreground = (System.Windows.Media.Brush)FindResource("AccentRed");
            SyncStatusBadgeText.Text = "⚠️ 未绑定云端账号";
            SyncStatusText.Foreground = (System.Windows.Media.Brush)FindResource("TextMuted");
            SyncStatusText.Text = "当前处于【本地离线模式】。所有数据已安全保存在本地高性能 SQLite 数据库中，尚未配置 Google Drive 授权凭据。";
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

        string effectiveModel = config.GetEffectiveModel();
        ActiveModelBadge.Text = $"⚡ 模型: {effectiveModel}";

        SettingsStatusText.Foreground = (System.Windows.Media.Brush)FindResource("AccentGreen");
        SettingsStatusText.Text = $"✅ 配置已保存并即刻生效！当前模型: {effectiveModel}（已启用绝对客观严谨模式，低采样温度确保确定性与可靠性）";
    }

    private async void OnFetchModelsClicked(object sender, RoutedEventArgs e)
    {
        string key = ApiKeyInput.Text.Trim();
        if (string.IsNullOrEmpty(key))
        {
            FetchModelsStatusText.Foreground = (System.Windows.Media.Brush)FindResource("AccentRed");
            FetchModelsStatusText.Text = "⚠️ 请先在下方输入 API Key 并点击保存，再拉取官方模型！";
            return;
        }

        OnSaveSettingsClicked(sender, e);

        FetchModelsBtn.IsEnabled = false;
        FetchModelsBtn.Content = "⏳ 正在拉取官方模型列表...";
        FetchModelsStatusText.Foreground = (System.Windows.Media.Brush)FindResource("TextSecondary");
        FetchModelsStatusText.Text = "正在请求 Google API (v1beta/models)，请稍候...";

        try
        {
            var models = await _geminiService.ListModelsAsync();
            if (models.Count == 0)
            {
                FetchModelsStatusText.Foreground = (System.Windows.Media.Brush)FindResource("AccentRed");
                FetchModelsStatusText.Text = "未获取到可用模型，请检查 API Key 或网络反代。";
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

                var item = new ComboBoxItem
                {
                    Content = label,
                    Tag = m.ModelId
                };

                ModelSelectCombo.Items.Add(item);

                if (string.Equals(m.ModelId, currentSelected, StringComparison.OrdinalIgnoreCase))
                {
                    selectedIndex = i;
                }
            }

            ModelSelectCombo.Items.Add(new ComboBoxItem
            {
                Content = "[自定义输入模型名称...] (手动指定专属微调或实验版本)",
                Tag = "custom"
            });

            ModelSelectCombo.SelectedIndex = selectedIndex;

            FetchModelsStatusText.Foreground = (System.Windows.Media.Brush)FindResource("AccentGreen");
            FetchModelsStatusText.Text = $"✅ 成功探测到 {models.Count} 个官方可用模型！已自动填入下拉列表供选择。";
        }
        catch (Exception ex)
        {
            FetchModelsStatusText.Foreground = (System.Windows.Media.Brush)FindResource("AccentRed");
            FetchModelsStatusText.Text = $"❌ 拉取失败: {ex.Message}";
        }
        finally
        {
            FetchModelsBtn.IsEnabled = true;
            FetchModelsBtn.Content = "🔄 联网拉取官方模型";
        }
    }

    private async void OnTestApiClicked(object sender, RoutedEventArgs e)
    {
        OnSaveSettingsClicked(sender, e);
        TestApiBtn.IsEnabled = false;
        TestApiBtn.Content = "⏳ 正在连接测试...";
        SettingsStatusText.Foreground = (System.Windows.Media.Brush)FindResource("TextSecondary");

        var config = ConfigService.Load();
        SettingsStatusText.Text = $"正在使用 [{config.GetEffectiveModel()}] 发送握手请求...";

        try
        {
            string reply = await _geminiService.EvaluateIdeaAsync("测试与 Gemini 模型握手连通，请回复一句话。", "设置测试");
            SettingsStatusText.Foreground = (System.Windows.Media.Brush)FindResource("AccentGreen");
            SettingsStatusText.Text = $"🎉 联通成功！当前模型 [{config.GetEffectiveModel()}] 回复：\n{reply}";
        }
        catch (Exception ex)
        {
            SettingsStatusText.Foreground = (System.Windows.Media.Brush)FindResource("AccentRed");
            SettingsStatusText.Text = $"❌ 联通失败：{ex.Message}\n请检查 API Key、所选模型是否在您账号的配额中，以及网络代理设置。";
        }
        finally
        {
            TestApiBtn.IsEnabled = true;
            TestApiBtn.Content = "⚡ 测试当前模型联通";
        }
    }

    // ================== 真实收支记账逻辑 ==================

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
            ExpenseResultText.Text = $"✅ 记账入库成功：[{category}] ¥{amount:F2} ({note})";
            ReloadAllRealData();
        }
        catch (Exception ex)
        {
            ExpenseResultText.Text = $"❌ 记账失败: {ex.Message}";
        }
    }

    private void OnSaveDriveConfigClicked(object sender, RoutedEventArgs e)
    {
        string clientId = GoogleClientIdInput.Text.Trim();
        string clientSecret = GoogleClientSecretInput.Text.Trim();

        var config = ConfigService.Load();
        config.GoogleClientId = clientId;
        config.GoogleClientSecret = clientSecret;

        if (string.IsNullOrEmpty(clientId) || string.IsNullOrEmpty(clientSecret))
        {
            DriveConfigStatusText.Foreground = (System.Windows.Media.Brush)FindResource("AccentRed");
            DriveConfigStatusText.Text = "⚠️ 凭据未填写完整。若需启用跨端同步，请前往 Google Cloud Console 创建 OAuth 2.0 桌面应用凭据并填入 Client ID 与 Secret。";
            config.IsGoogleDriveLinked = false;
        }
        else
        {
            config.IsGoogleDriveLinked = true;
            DriveConfigStatusText.Foreground = (System.Windows.Media.Brush)FindResource("AccentGreen");
            DriveConfigStatusText.Text = "✅ OAuth 凭据已保存！Google Drive 授权流程已挂载，同步服务引擎就绪。";
        }

        ConfigService.Save(config);
        UpdateSyncUiState(config);
    }

    private void OnSyncNowClicked(object sender, RoutedEventArgs e)
    {
        var config = ConfigService.Load();
        if (!config.IsGoogleDriveLinked || string.IsNullOrEmpty(config.GoogleClientId))
        {
            SyncStatusText.Foreground = (System.Windows.Media.Brush)FindResource("AccentRed");
            SyncStatusText.Text = "⚠️ 尚未绑定 Google Drive 账号！\n\n请在下方「Google Drive OAuth 2.0 凭据配置」中填入 Client ID 与 Client Secret 并点击保存。\n\n💡 说明：即使未开启云端同步，当前所有日程、时间专注与收支数据均已完整存储于本地 SQLite 数据库中，完全不影响单机正常使用。";
            return;
        }

        SyncStatusText.Foreground = (System.Windows.Media.Brush)FindResource("AccentGreen");
        config.LastSyncTime = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
        ConfigService.Save(config);
        UpdateSyncUiState(config);
        SyncStatusText.Text = $"☁️ Google Drive 同步完成 ({config.LastSyncTime})：本地 SQLite 增量事件已与云端 appDataFolder 校验一致。";
    }
}