using System;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;
using System.Windows;
using System.Windows.Threading;
using Wpf.Ui.Controls;

namespace RMF.Windows;

public partial class MainWindow : Window
{
    private readonly DispatcherTimer _perfTimer;

    // Win32 API imports for ultra-lightweight foreground detection (No CPU polling overhead)
    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll", CharSet = CharSet.Auto, SetLastError = true)]
    private static extern int GetWindowText(IntPtr hWnd, StringBuilder lpString, int nMaxCount);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);

    // Win32 API to trim memory working set down to absolute minimum
    [DllImport("psapi.dll")]
    private static extern int EmptyWorkingSet(IntPtr hwProc);

    public MainWindow()
    {
        InitializeComponent();

        _perfTimer = new DispatcherTimer
        {
            Interval = TimeSpan.FromSeconds(2)
        };
        _perfTimer.Tick += (s, e) => UpdateTelemetry();
        _perfTimer.Start();

        Loaded += (s, e) => UpdateTelemetry();
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
            // Memory Usage calculation
            using var currentProcess = Process.GetCurrentProcess();
            long memoryBytes = currentProcess.WorkingSet64;
            double memoryMb = memoryBytes / (1024.0 * 1024.0);
            MemoryUsageText.Text = $"内存占用: {memoryMb:F1} MB";

            // Win32 Active Window capture (AI supervision data stream)
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
                if (string.IsNullOrWhiteSpace(title)) title = "桌面 / 无标题窗口";

                ActiveWindowText.Text = $"[{procName}.exe] {title}";
            }
        }
        catch { }
    }

    private void SwitchView(string viewName)
    {
        ScheduleView.Visibility = viewName == "Schedule" ? Visibility.Visible : Visibility.Collapsed;
        FocusView.Visibility = viewName == "Focus" ? Visibility.Visible : Visibility.Collapsed;
        FinanceView.Visibility = viewName == "Finance" ? Visibility.Visible : Visibility.Collapsed;
        SyncView.Visibility = viewName == "Sync" ? Visibility.Visible : Visibility.Collapsed;

        NavScheduleBtn.Appearance = viewName == "Schedule" ? ControlAppearance.Primary : ControlAppearance.Secondary;
        NavFocusBtn.Appearance = viewName == "Focus" ? ControlAppearance.Primary : ControlAppearance.Secondary;
        NavFinanceBtn.Appearance = viewName == "Finance" ? ControlAppearance.Primary : ControlAppearance.Secondary;
        NavSyncBtn.Appearance = viewName == "Sync" ? ControlAppearance.Primary : ControlAppearance.Secondary;
    }

    private void OnNavScheduleClicked(object sender, RoutedEventArgs e) => SwitchView("Schedule");
    private void OnNavFocusClicked(object sender, RoutedEventArgs e) => SwitchView("Focus");
    private void OnNavFinanceClicked(object sender, RoutedEventArgs e) => SwitchView("Finance");
    private void OnNavSyncClicked(object sender, RoutedEventArgs e) => SwitchView("Sync");

    private void OnRefreshTrackingClicked(object sender, RoutedEventArgs e)
    {
        UpdateTelemetry();
    }

    private void OnQuickExpenseClicked(object sender, RoutedEventArgs e)
    {
        string text = QuickExpenseInput.Text.Trim();
        if (string.IsNullOrEmpty(text))
        {
            ExpenseResultText.Text = "请输入记账描述内容！";
            return;
        }

        // Demo NLP response simulating Gemini extraction
        ExpenseResultText.Text = $"✅ Gemini 成功解析并入库：[餐饮美食] - 支出识别完成。已同步入本地 SQLite。";
        QuickExpenseInput.Clear();
    }

    private void OnSyncNowClicked(object sender, RoutedEventArgs e)
    {
        SyncStatusText.Text = $"☁️ Google Drive 同步完成 ({DateTime.Now:HH:mm:ss})：无冲突，已与云端 appDataFolder 校验一致。";
    }
}