using System;
using System.IO;
using System.Windows;
using System.Windows.Threading;

namespace RMF.Windows;

public partial class App : Application
{
    private static readonly string LogFile = @"d:\RMF\crash.log";

    public App()
    {
        AppDomain.CurrentDomain.UnhandledException += (s, args) =>
        {
            File.AppendAllText(LogFile, $"[AppDomain] {DateTime.Now}: {args.ExceptionObject}\n\n");
        };

        DispatcherUnhandledException += (s, args) =>
        {
            File.AppendAllText(LogFile, $"[Dispatcher] {DateTime.Now}: {args.Exception}\n\n");
            args.Handled = false;
        };
    }

    protected override void OnStartup(StartupEventArgs e)
    {
        File.AppendAllText(LogFile, $"[Startup] {DateTime.Now}: Application Starting\n");
        base.OnStartup(e);
    }

    protected override void OnExit(ExitEventArgs e)
    {
        File.AppendAllText(LogFile, $"[Exit] {DateTime.Now}: Application Exited with code {e.ApplicationExitCode}\n");
        base.OnExit(e);
    }
}
