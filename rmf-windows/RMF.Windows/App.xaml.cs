using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Threading;
using System.Windows;
using Microsoft.Win32;

namespace RMF.Windows;

public partial class App : Application
{
    public const string AppUserModelId = "Renly.RMF.Desktop";

    [DllImport("shell32.dll", SetLastError = true)]
    private static extern int SetCurrentProcessExplicitAppUserModelID([MarshalAs(UnmanagedType.LPWStr)] string AppID);

    private Mutex? _instanceMutex;

    public App()
    {
        EnsureAppUserModelId();

        DispatcherUnhandledException += (sender, e) =>
        {
            try
            {
                System.Diagnostics.Debug.WriteLine($"[WPF Dispatcher Exception] {e.Exception}");
                e.Handled = true; // 拦截未捕获 UI 异常，防止进程以 0xc000041d 崩溃
            }
            catch { }
        };

        AppDomain.CurrentDomain.UnhandledException += (sender, e) =>
        {
            System.Diagnostics.Debug.WriteLine($"[AppDomain Unhandled] {e.ExceptionObject}");
        };
    }

    private static void EnsureAppUserModelId()
    {
        try
        {
            SetCurrentProcessExplicitAppUserModelID(AppUserModelId);

            string baseDir = AppDomain.CurrentDomain.BaseDirectory;
            string iconPath = Path.Combine(baseDir, "app.ico");
            if (!File.Exists(iconPath))
            {
                iconPath = Path.Combine(baseDir, "app_logo_256.png");
            }

            using var key = Registry.CurrentUser.CreateSubKey($@"Software\Classes\AppUserModelId\{AppUserModelId}");
            if (key != null)
            {
                key.SetValue("DisplayName", "RMF", RegistryValueKind.String);
                if (File.Exists(iconPath))
                {
                    key.SetValue("IconUri", iconPath, RegistryValueKind.String);
                }
                key.SetValue("ShowInSettings", 1, RegistryValueKind.DWord);
            }
        }
        catch { }
    }

    protected override void OnStartup(StartupEventArgs e)
    {
        const string mutexName = "RMF_Desktop_SingleInstance_Mutex";
        _instanceMutex = new Mutex(true, mutexName, out bool createdNew);

        if (!createdNew)
        {
            // 已有实例在运行，尝试激活前台窗口或退出重复实例
            Shutdown();
            return;
        }

        base.OnStartup(e);
    }

    protected override void OnExit(ExitEventArgs e)
    {
        if (_instanceMutex != null)
        {
            try
            {
                _instanceMutex.ReleaseMutex();
            }
            catch { }
            _instanceMutex.Dispose();
            _instanceMutex = null;
        }

        base.OnExit(e);
    }
}
