using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Windows.Forms;
using Microsoft.Toolkit.Uwp.Notifications;

namespace RMF.Windows.Services;

public class TrayIconService : IDisposable
{
    private NotifyIcon? _notifyIcon;
    private readonly Action _onRestore;
    private readonly Action _onNewEvent;
    private readonly Action _onExit;

    public TrayIconService(Action onRestore, Action onNewEvent, Action onExit)
    {
        _onRestore = onRestore;
        _onNewEvent = onNewEvent;
        _onExit = onExit;
        InitializeTray();
    }

    private void InitializeTray()
    {
        try
        {
            _notifyIcon = new NotifyIcon
            {
                Text = "RMF 时间与精力节律管理系统",
                Visible = true
            };

            // 获取应用专属 Logo 图标
            Icon? appIcon = null;
            try
            {
                string icoPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "app.ico");
                if (File.Exists(icoPath))
                {
                    appIcon = new Icon(icoPath);
                }
                else
                {
                    string? exePath = Process.GetCurrentProcess().MainModule?.FileName;
                    if (!string.IsNullOrEmpty(exePath) && File.Exists(exePath))
                    {
                        appIcon = Icon.ExtractAssociatedIcon(exePath);
                    }
                }
            }
            catch { }

            if (appIcon == null)
            {
                // 备用：在内存中绘制一个 16x16 现代蓝色日历图标
                using var bmp = new Bitmap(16, 16);
                using (var g = Graphics.FromImage(bmp))
                {
                    g.Clear(Color.Transparent);
                    using var brush = new SolidBrush(Color.FromArgb(26, 115, 232));
                    g.FillRectangle(brush, 1, 1, 14, 14);
                    using var whiteBrush = new SolidBrush(Color.White);
                    g.FillRectangle(whiteBrush, 3, 5, 10, 8);
                    g.FillRectangle(brush, 5, 7, 2, 2);
                    g.FillRectangle(brush, 9, 7, 2, 2);
                    g.FillRectangle(brush, 5, 10, 2, 2);
                    g.FillRectangle(brush, 9, 10, 2, 2);
                }
                IntPtr hIcon = bmp.GetHicon();
                appIcon = Icon.FromHandle(hIcon);
            }

            _notifyIcon.Icon = appIcon;

            // 托盘右键菜单
            var contextMenu = new ContextMenuStrip();
            var itemShow = contextMenu.Items.Add("📅 显示主界面");
            itemShow.Font = new Font(itemShow.Font, System.Drawing.FontStyle.Bold);
            itemShow.Click += (s, e) => _onRestore();

            var itemNew = contextMenu.Items.Add("➕ 新建日程时间块");
            itemNew.Click += (s, e) => _onNewEvent();

            contextMenu.Items.Add(new ToolStripSeparator());

            var itemExit = contextMenu.Items.Add("🚪 彻底退出程序");
            itemExit.Click += (s, e) => _onExit();

            _notifyIcon.ContextMenuStrip = contextMenu;

            _notifyIcon.DoubleClick += (s, e) => _onRestore();
            _notifyIcon.Click += (s, e) =>
            {
                if (e is MouseEventArgs me && me.Button == MouseButtons.Left)
                {
                    _onRestore();
                }
            };

            _notifyIcon.BalloonTipClicked += (s, e) => _onRestore();

            try
            {
                ToastNotificationManagerCompat.OnActivated += toastArgs =>
                {
                    try
                    {
                        System.Windows.Application.Current?.Dispatcher?.Invoke(() =>
                        {
                            _onRestore();
                        });
                    }
                    catch { }
                };
            }
            catch { }
        }
        catch { }
    }

    /// <summary>
    /// 弹出 Windows 原生 Toast 通知（使用应用专属 Logo 图标）
    /// </summary>
    public void ShowNotification(string title, string message)
    {
        try
        {
            string baseDir = AppDomain.CurrentDomain.BaseDirectory;
            string logoPath = Path.Combine(baseDir, "app_logo_256.png");
            if (!File.Exists(logoPath))
            {
                logoPath = Path.Combine(baseDir, "app.ico");
            }

            var builder = new ToastContentBuilder()
                .AddText(title)
                .AddText(message);

            if (File.Exists(logoPath))
            {
                builder.AddAppLogoOverride(new Uri(logoPath), ToastGenericAppLogoCrop.Circle);
            }

            builder.Show();
            return;
        }
        catch
        {
            // 降级回退：使用 NotifyIcon 气泡（去除通用感叹号，避免显示系统默认蓝圈(i)）
            try
            {
                if (_notifyIcon != null && _notifyIcon.Visible)
                {
                    _notifyIcon.ShowBalloonTip(4000, title, message, ToolTipIcon.None);
                }
            }
            catch { }
        }
    }

    public void Dispose()
    {
        try
        {
            if (_notifyIcon != null)
            {
                _notifyIcon.Visible = false;
                _notifyIcon.Dispose();
                _notifyIcon = null;
            }
        }
        catch { }
    }
}
