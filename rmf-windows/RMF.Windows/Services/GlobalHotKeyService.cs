using System;
using System.Runtime.InteropServices;
using System.Windows.Interop;

namespace RMF.Windows.Services;

public class GlobalHotKeyService : IDisposable
{
    private const int WM_HOTKEY = 0x0312;
    private const uint MOD_ALT = 0x0001;
    private const uint MOD_CONTROL = 0x0002;
    private const uint MOD_SHIFT = 0x0004;
    private const uint MOD_WIN = 0x0008;
    private const uint MOD_NOREPEAT = 0x4000;

    private const int VK_SPACE = 0x20;
    private const int VK_F = 0x46;
    private const int VK_L = 0x4C;

    private const int HOTKEY_ID_QUICK_CAPTURE_ALT = 9000;
    private const int HOTKEY_ID_QUICK_CAPTURE = 9001;
    private const int HOTKEY_ID_FOCUS_HUD = 9002;
    private const int HOTKEY_ID_PASSTHROUGH = 9003;

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool RegisterHotKey(IntPtr hWnd, int id, uint fsModifiers, uint vk);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool UnregisterHotKey(IntPtr hWnd, int id);

    private readonly IntPtr _hwnd;
    private HwndSource? _source;
    private readonly Action _onQuickCapture;
    private readonly Action _onFocusHud;
    private readonly Action? _onTogglePassThrough;

    public GlobalHotKeyService(IntPtr hWnd, Action onQuickCapture, Action onFocusHud, Action? onTogglePassThrough = null)
    {
        _hwnd = hWnd;
        _onQuickCapture = onQuickCapture;
        _onFocusHud = onFocusHud;
        _onTogglePassThrough = onTogglePassThrough;

        _source = HwndSource.FromHwnd(_hwnd);
        _source?.AddHook(HwndHook);

        RegisterHotKeys();
    }

    private void RegisterHotKeys()
    {
        try
        {
            // Alt + Space -> 类似 Raycast / Spotlight 极速呼出闪念胶囊 (若被占用则忽略)
            RegisterHotKey(_hwnd, HOTKEY_ID_QUICK_CAPTURE_ALT, MOD_ALT | MOD_NOREPEAT, VK_SPACE);

            // Ctrl + Shift + Space -> 全局极速闪念捕获
            RegisterHotKey(_hwnd, HOTKEY_ID_QUICK_CAPTURE, MOD_CONTROL | MOD_SHIFT | MOD_NOREPEAT, VK_SPACE);

            // Ctrl + Shift + F -> 立即开启专注胶囊
            RegisterHotKey(_hwnd, HOTKEY_ID_FOCUS_HUD, MOD_CONTROL | MOD_SHIFT | MOD_NOREPEAT, VK_F);

            // Ctrl + Shift + L -> 解锁/锁定专注胶囊鼠标穿透
            RegisterHotKey(_hwnd, HOTKEY_ID_PASSTHROUGH, MOD_CONTROL | MOD_SHIFT | MOD_NOREPEAT, VK_L);
        }
        catch { }
    }

    private IntPtr HwndHook(IntPtr hwnd, int msg, IntPtr wParam, IntPtr lParam, ref bool handled)
    {
        if (msg == WM_HOTKEY)
        {
            try
            {
                int id = wParam.ToInt32();
                if (id == HOTKEY_ID_QUICK_CAPTURE || id == HOTKEY_ID_QUICK_CAPTURE_ALT)
                {
                    _onQuickCapture?.Invoke();
                    handled = true;
                }
                else if (id == HOTKEY_ID_FOCUS_HUD)
                {
                    _onFocusHud?.Invoke();
                    handled = true;
                }
                else if (id == HOTKEY_ID_PASSTHROUGH)
                {
                    _onTogglePassThrough?.Invoke();
                    handled = true;
                }
            }
            catch { }
        }
        return IntPtr.Zero;
    }

    public void Dispose()
    {
        try
        {
            UnregisterHotKey(_hwnd, HOTKEY_ID_QUICK_CAPTURE_ALT);
            UnregisterHotKey(_hwnd, HOTKEY_ID_QUICK_CAPTURE);
            UnregisterHotKey(_hwnd, HOTKEY_ID_FOCUS_HUD);
            UnregisterHotKey(_hwnd, HOTKEY_ID_PASSTHROUGH);
            _source?.RemoveHook(HwndHook);
            _source = null;
        }
        catch { }
    }
}
