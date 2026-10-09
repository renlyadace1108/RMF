using System;
using System.Runtime.InteropServices;

namespace RMF.Windows.Services;

public class FocusAssistService
{
    public enum QueryUserNotificationStateResult
    {
        QUNS_NOT_PRESENT = 1,
        QUNS_BUSY = 2,
        QUNS_RUNNING_D3D_FULL_SCREEN = 3,
        QUNS_PRESENTATION_MODE = 4,
        QUNS_ACCEPTS_NOTIFICATIONS = 5,
        QUNS_QUIET_TIME = 6,
        QUNS_APP = 7
    }

    [DllImport("shell32.dll")]
    private static extern int SHQueryUserNotificationState(out QueryUserNotificationStateResult pquns);

    private static bool _isFocusModeActive = false;

    public static bool IsFocusModeActive => _isFocusModeActive;

    /// <summary>
    /// 获取当前系统 Windows 专注助手 / 通知接收状态
    /// </summary>
    public static QueryUserNotificationStateResult GetCurrentNotificationState()
    {
        try
        {
            if (SHQueryUserNotificationState(out var state) == 0)
            {
                return state;
            }
        }
        catch { }
        return QueryUserNotificationStateResult.QUNS_ACCEPTS_NOTIFICATIONS;
    }

    /// <summary>
    /// 开启深度专注工作模式 (抑制非关键干扰)
    /// </summary>
    public static void EnableFocusMode()
    {
        _isFocusModeActive = true;
    }

    /// <summary>
    /// 解除深度专注模式
    /// </summary>
    public static void DisableFocusMode()
    {
        _isFocusModeActive = false;
    }
}
