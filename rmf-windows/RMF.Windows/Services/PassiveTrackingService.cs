using System;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;
using System.Windows.Threading;

namespace RMF.Windows.Services;

public class PassiveTrackingService
{
    [StructLayout(LayoutKind.Sequential)]
    private struct LASTINPUTINFO
    {
        public uint cbSize;
        public uint dwTime;
    }

    [DllImport("user32.dll")]
    private static extern bool GetLastInputInfo(ref LASTINPUTINFO plii);

    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);

    [DllImport("user32.dll", CharSet = CharSet.Auto, SetLastError = true)]
    private static extern int GetWindowText(IntPtr hWnd, StringBuilder lpString, int nMaxCount);

    private readonly DispatcherTimer _trackingTimer;
    private bool _isIdle = false;

    public event Action<bool>? IdleStateChanged; // true: 刚进入闲置, false: 刚恢复活动
    public event Action<string, string, bool>? ForegroundAppChanged; // processName, title, isProductive

    public int IdleThresholdSeconds { get; set; } = 300; // 默认 5 分钟闲置阈值

    public PassiveTrackingService()
    {
        _trackingTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(3) };
        _trackingTimer.Tick += OnTick;
    }

    public void Start()
    {
        _trackingTimer.Start();
    }

    public void Stop()
    {
        _trackingTimer.Stop();
    }

    /// <summary>
    /// 获取当前键盘鼠标闲置秒数
    /// </summary>
    public static int GetIdleSeconds()
    {
        var lii = new LASTINPUTINFO();
        lii.cbSize = (uint)Marshal.SizeOf(lii);
        if (GetLastInputInfo(ref lii))
        {
            uint systemUptime = (uint)Environment.TickCount;
            uint idleTicks = systemUptime - lii.dwTime;
            return (int)(idleTicks / 1000);
        }
        return 0;
    }

    /// <summary>
    /// 获取当前 Windows 处于前台的活动窗口信息
    /// </summary>
    public static (string ProcessName, string WindowTitle, bool IsProductive) GetActiveWindowInfo()
    {
        try
        {
            IntPtr hwnd = GetForegroundWindow();
            if (hwnd == IntPtr.Zero) return ("Idle", "", true);

            GetWindowThreadProcessId(hwnd, out uint pid);
            var proc = Process.GetProcessById((int)pid);
            string procName = proc.ProcessName.ToLowerInvariant();

            var sb = new StringBuilder(256);
            GetWindowText(hwnd, sb, sb.Capacity);
            string title = sb.ToString();

            // 智能识别生产力工具 vs 娱乐消遣
            bool isProductive = true;
            string[] entertainmentProcesses = { "steam", "epicgameslauncher", "bilibili", "douyu", "wechat", "qq", "game", "music", "spotify" };
            foreach (var ent in entertainmentProcesses)
            {
                if (procName.Contains(ent))
                {
                    isProductive = false;
                    break;
                }
            }

            return (proc.ProcessName, title, isProductive);
        }
        catch
        {
            return ("Unknown", "", true);
        }
    }

    private void OnTick(object? sender, EventArgs e)
    {
        int idleSec = GetIdleSeconds();
        if (!_isIdle && idleSec >= IdleThresholdSeconds)
        {
            _isIdle = true;
            IdleStateChanged?.Invoke(true);
        }
        else if (_isIdle && idleSec < 5)
        {
            _isIdle = false;
            IdleStateChanged?.Invoke(false);
        }

        var (proc, title, isProd) = GetActiveWindowInfo();
        ForegroundAppChanged?.Invoke(proc, title, isProd);
    }
}
