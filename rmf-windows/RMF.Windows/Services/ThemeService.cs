using System;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;
using System.Windows.Media;

namespace RMF.Windows.Services;

public static class ThemeService
{
    [DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int attrValue, int attrSize);
    private const int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
    private const int DWMWA_SYSTEMBACKDROP_TYPE = 38;

    public static event Action? ThemeChanged;

    public static bool IsCurrentDark { get; private set; } = true;
    public static SolidColorBrush CurrentAccentBrush { get; private set; } = new(Color.FromRgb(0x1A, 0x73, 0xE8));
    public static SolidColorBrush CurrentAccentHoverBrush { get; private set; } = new(Color.FromRgb(0x18, 0x5A, 0xBC));
    public static SolidColorBrush CurrentSurfaceBrush { get; private set; } = new(Color.FromRgb(0x20, 0x21, 0x24));
    public static SolidColorBrush CurrentCardBrush { get; private set; } = new(Color.FromRgb(0x28, 0x29, 0x2C));
    public static SolidColorBrush CurrentBorderBrush { get; private set; } = new(Color.FromRgb(0x3C, 0x40, 0x43));
    public static SolidColorBrush CurrentGridLineBrush { get; private set; } = new(Color.FromRgb(0x2A, 0x2B, 0x2D));
    public static SolidColorBrush CurrentTextBrush { get; private set; } = new(Color.FromRgb(0xE8, 0xEA, 0xED));
    public static SolidColorBrush CurrentSecondaryTextBrush { get; private set; } = new(Color.FromRgb(0x9A, 0xA0, 0xA6));
    public static SolidColorBrush CurrentNavRailBrush { get; private set; } = new(Color.FromRgb(0x18, 0x19, 0x1B));

    public static SolidColorBrush CurrentControlBrush { get; private set; } = new(Color.FromRgb(0x30, 0x31, 0x34));
    public static SolidColorBrush CurrentInputBrush { get; private set; } = new(Color.FromRgb(0x18, 0x19, 0x1B));
    public static SolidColorBrush CurrentCardHoverBrush { get; private set; } = new(Color.FromRgb(0x35, 0x37, 0x3B));
    public static SolidColorBrush CurrentMutedTextBrush { get; private set; } = new(Color.FromRgb(0x71, 0x71, 0x7A));

    /// <summary>
    /// 判断当前配置是否解析为暗黑模式
    /// </summary>
    public static bool ResolveIsDark(string themeMode)
    {
        if (string.Equals(themeMode, "LIGHT", StringComparison.OrdinalIgnoreCase))
        {
            return false;
        }
        if (string.Equals(themeMode, "SYSTEM", StringComparison.OrdinalIgnoreCase))
        {
            try
            {
                using var key = Microsoft.Win32.Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize");
                var val = key?.GetValue("AppsUseLightTheme");
                if (val is int intVal)
                {
                    return intVal == 0;
                }
            }
            catch { }
        }
        return true;
    }

    /// <summary>
    /// 解析 Hex 颜色，失败则返回默认值
    /// </summary>
    public static Color ParseHexColor(string? hex, Color fallback)
    {
        if (string.IsNullOrWhiteSpace(hex)) return fallback;
        string clean = hex.Trim().TrimStart('#');
        try
        {
            if (clean.Length == 6)
            {
                byte r = Convert.ToByte(clean.Substring(0, 2), 16);
                byte g = Convert.ToByte(clean.Substring(2, 2), 16);
                byte b = Convert.ToByte(clean.Substring(4, 2), 16);
                return Color.FromRgb(r, g, b);
            }
            if (clean.Length == 8)
            {
                byte a = Convert.ToByte(clean.Substring(0, 2), 16);
                byte r = Convert.ToByte(clean.Substring(2, 2), 16);
                byte g = Convert.ToByte(clean.Substring(4, 2), 16);
                byte b = Convert.ToByte(clean.Substring(6, 2), 16);
                return Color.FromArgb(a, r, g, b);
            }
        }
        catch { }
        return fallback;
    }

    /// <summary>
    /// 调整颜色明度
    /// </summary>
    public static Color AdjustBrightness(Color color, double factor)
    {
        double r = Math.Clamp(color.R * factor, 0, 255);
        double g = Math.Clamp(color.G * factor, 0, 255);
        double b = Math.Clamp(color.B * factor, 0, 255);
        return Color.FromRgb((byte)r, (byte)g, (byte)b);
    }

    /// <summary>
    /// 应用主题到指定的 MainWindow
    /// </summary>
    public static void ApplyTheme(MainWindow window, AppConfig config)
    {
        bool isDark = ResolveIsDark(config.ThemeMode);
        IsCurrentDark = isDark;

        Color accentColor = ParseHexColor(config.AccentColorHex, Color.FromRgb(0x1A, 0x73, 0xE8));
        Color accentHoverColor = AdjustBrightness(accentColor, isDark ? 1.15 : 0.88);
        Color accentLightColor = AdjustBrightness(accentColor, 1.35);

        CurrentAccentBrush = new SolidColorBrush(accentColor);
        CurrentAccentHoverBrush = new SolidColorBrush(accentHoverColor);

        Color surfaceColor;
        Color cardColor;
        Color controlColor;
        Color inputColor;
        Color cardHoverColor;
        Color borderColor;
        Color borderSubtleColor;
        Color textColor;
        Color secondaryTextColor;
        Color mutedTextColor;
        Color navRailColor;

        if (isDark)
        {
            surfaceColor = ParseHexColor(config.CustomDarkBgHex, Color.FromRgb(0x20, 0x21, 0x24));
            cardColor = Color.FromRgb(0x28, 0x29, 0x2C);
            controlColor = Color.FromRgb(0x30, 0x31, 0x34);
            inputColor = Color.FromRgb(0x18, 0x19, 0x1B);
            cardHoverColor = Color.FromRgb(0x35, 0x37, 0x3B);
            borderColor = Color.FromRgb(0x3C, 0x40, 0x43);
            borderSubtleColor = Color.FromRgb(0x2A, 0x2B, 0x2D);
            textColor = Color.FromRgb(0xE8, 0xEA, 0xED);
            secondaryTextColor = Color.FromRgb(0x9A, 0xA0, 0xA6);
            mutedTextColor = Color.FromRgb(0x71, 0x71, 0x7A);
            navRailColor = Color.FromRgb(0x18, 0x19, 0x1B);
            CurrentGridLineBrush = new SolidColorBrush(Color.FromRgb(0x2A, 0x2B, 0x2D));
        }
        else
        {
            surfaceColor = ParseHexColor(config.CustomLightBgHex, Color.FromRgb(0xF8, 0xF9, 0xFA));
            cardColor = Color.FromRgb(0xFF, 0xFF, 0xFF);
            controlColor = Color.FromRgb(0xF1, 0xF3, 0xF4);
            inputColor = Color.FromRgb(0xFF, 0xFF, 0xFF);
            cardHoverColor = Color.FromRgb(0xE8, 0xEA, 0xED);
            borderColor = Color.FromRgb(0xDA, 0xDC, 0xE0);
            borderSubtleColor = Color.FromRgb(0xEE, 0xEE, 0xEE);
            textColor = Color.FromRgb(0x20, 0x21, 0x24);
            secondaryTextColor = Color.FromRgb(0x5F, 0x63, 0x68);
            mutedTextColor = Color.FromRgb(0x80, 0x86, 0x8B);
            navRailColor = Color.FromRgb(0xFF, 0xFF, 0xFF);
            CurrentGridLineBrush = new SolidColorBrush(Color.FromRgb(0xE5, 0xE7, 0xEB));
        }

        CurrentSurfaceBrush = new SolidColorBrush(surfaceColor);
        CurrentCardBrush = new SolidColorBrush(cardColor);
        CurrentControlBrush = new SolidColorBrush(controlColor);
        CurrentInputBrush = new SolidColorBrush(inputColor);
        CurrentCardHoverBrush = new SolidColorBrush(cardHoverColor);
        CurrentBorderBrush = new SolidColorBrush(borderColor);
        CurrentTextBrush = new SolidColorBrush(textColor);
        CurrentSecondaryTextBrush = new SolidColorBrush(secondaryTextColor);
        CurrentMutedTextBrush = new SolidColorBrush(mutedTextColor);
        CurrentNavRailBrush = new SolidColorBrush(navRailColor);

        // 1. 设置 Window 资源字典中的画刷，供动态及后续渲染使用
        window.Resources["GoogleSurface"] = CurrentSurfaceBrush;
        window.Resources["GoogleSurfaceVariant"] = CurrentCardBrush;
        window.Resources["GoogleBorder"] = CurrentBorderBrush;
        window.Resources["GoogleBorderSubtle"] = new SolidColorBrush(borderSubtleColor);
        window.Resources["GoogleGridLine"] = CurrentGridLineBrush;
        window.Resources["GoogleText"] = CurrentTextBrush;
        window.Resources["GoogleTextSecondary"] = CurrentSecondaryTextBrush;
        window.Resources["GoogleBlue"] = CurrentAccentBrush;
        window.Resources["GoogleBlueHover"] = CurrentAccentHoverBrush;
        window.Resources["GoogleBlueLight"] = new SolidColorBrush(accentLightColor);

        window.Resources["SidebarBackground"] = CurrentNavRailBrush;
        window.Resources["CardBackground"] = CurrentCardBrush;
        window.Resources["CardHoverBackground"] = CurrentCardHoverBrush;
        window.Resources["CardBorderBrush"] = CurrentBorderBrush;
        window.Resources["ControlBackground"] = CurrentControlBrush;
        window.Resources["InputBackground"] = CurrentInputBrush;
        window.Resources["TextPrimary"] = CurrentTextBrush;
        window.Resources["TextSecondary"] = CurrentSecondaryTextBrush;
        window.Resources["TextMuted"] = CurrentMutedTextBrush;
        window.Resources["AccentBlue"] = CurrentAccentBrush;

        // 2. 更新 Window 根属性
        window.Background = CurrentSurfaceBrush;
        window.Foreground = CurrentTextBrush;

        // 3. 更新 Windows 11 原生沉浸式暗黑/日间标题栏
        try
        {
            IntPtr hwnd = new WindowInteropHelper(window).Handle;
            if (hwnd != IntPtr.Zero)
            {
                int useDarkMode = isDark ? 1 : 0;
                DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, ref useDarkMode, sizeof(int));
                int backdropType = 2; // Mica
                DwmSetWindowAttribute(hwnd, DWMWA_SYSTEMBACKDROP_TYPE, ref backdropType, sizeof(int));
            }
        }
        catch { }

        // 4. 调用窗口的个性化更新钩子
        window.ApplyVisualTheme(isDark, CurrentSurfaceBrush, CurrentCardBrush, CurrentBorderBrush, CurrentTextBrush, CurrentSecondaryTextBrush, CurrentNavRailBrush, CurrentAccentBrush);

        ThemeChanged?.Invoke();
    }
}
