using System;
using System.Linq;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Media;
using RMF.Windows.Models;
using RMF.Windows.Services;

namespace RMF.Windows;

public partial class DesktopWidgetWindow : Window
{
    private enum PinMode
    {
        Normal,
        Topmost,
        DesktopBottom
    }

    private PinMode _currentPinMode = PinMode.Normal;

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int X, int Y, int cx, int cy, uint uFlags);

    private static readonly IntPtr HWND_BOTTOM = new IntPtr(1);
    private static readonly IntPtr HWND_TOPMOST = new IntPtr(-1);
    private static readonly IntPtr HWND_NOTOPMOST = new IntPtr(-2);

    private const uint SWP_NOSIZE = 0x0001;
    private const uint SWP_NOMOVE = 0x0002;
    private const uint SWP_NOACTIVATE = 0x0010;

    public DesktopWidgetWindow()
    {
        InitializeComponent();

        Left = Math.Max(10, SystemParameters.PrimaryScreenWidth - Width - 24);
        Top = Math.Max(10, 64);

        Loaded += (s, e) =>
        {
            RefreshData();
            DatabaseService.OnDatabaseMutated += OnDatabaseMutated;
        };

        Closed += (s, e) =>
        {
            DatabaseService.OnDatabaseMutated -= OnDatabaseMutated;
        };

        Activated += (s, e) =>
        {
            if (_currentPinMode == PinMode.DesktopBottom)
            {
                ApplyBottomPin();
            }
        };
    }

    private void OnDatabaseMutated()
    {
        Dispatcher.InvokeAsync(RefreshData);
    }

    private void OnWindowMouseDown(object sender, MouseButtonEventArgs e)
    {
        if (e.LeftButton == MouseButtonState.Pressed)
        {
            try
            {
                DragMove();
            }
            catch { }
        }
    }

    private void ApplyBottomPin()
    {
        try
        {
            var helper = new WindowInteropHelper(this);
            if (helper.Handle != IntPtr.Zero)
            {
                SetWindowPos(helper.Handle, HWND_BOTTOM, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE);
            }
        }
        catch { }
    }

    public void RefreshData()
    {
        try
        {
            DateText.Text = DateTime.Today.ToString("MM-dd ddd");

            // 1. 北极星目标
            var northStars = DatabaseService.GetNorthStarGoals();
            if (northStars.Count > 0)
            {
                var first = northStars[0];
                NorthStarTitleText.Text = first.Title;
                NorthStarProgressText.Text = $"{first.Progress}%";
            }
            else
            {
                NorthStarTitleText.Text = "在目标管理中可将核心 OKR 设为北极星";
                NorthStarProgressText.Text = "-";
            }

            // 2. 今日课程
            var courses = TimetableProjectionService.GetEffectiveCoursesForDate(DateTime.Today);
            if (courses.Count > 0)
            {
                TodayCoursesText.Text = string.Join("\n", courses.Select(c => $"• {c.StartTime:HH:mm}-{c.EndTime:HH:mm} {c.CourseName}{(string.IsNullOrWhiteSpace(c.Location) ? "" : $" @ {c.Location}")}"));
            }
            else
            {
                TodayCoursesText.Text = "🌱 今日无课 / 自主攻坚自由日";
            }

            // 3. 今日日程与快捷打卡
            ScheduleItemsList.Children.Clear();
            var schedules = DatabaseService.GetSchedulesForDate(DateTime.Today)
                .Where(s => !s.IsAllDay)
                .OrderBy(s => s.StartTime)
                .ToList();

            int completed = schedules.Count(s => s.Status == "COMPLETED");
            ScheduleCountText.Text = $"{completed}/{schedules.Count} 完成";

            if (schedules.Count == 0)
            {
                ScheduleItemsList.Children.Add(new TextBlock
                {
                    Text = "今日暂无排期任务，可在下方快速录入",
                    FontSize = 11,
                    Foreground = new SolidColorBrush(Color.FromRgb(0x64, 0x74, 0x8B)),
                    Margin = new Thickness(0, 4, 0, 0)
                });
            }
            else
            {
                foreach (var item in schedules.Take(7))
                {
                    bool isDone = item.Status == "COMPLETED";

                    var row = new Grid { Margin = new Thickness(0, 3, 0, 3) };
                    row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
                    row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                    row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

                    // 交互打卡勾选框
                    var chkBorder = new Border
                    {
                        Width = 15,
                        Height = 15,
                        CornerRadius = new CornerRadius(3),
                        BorderThickness = new Thickness(1.2),
                        BorderBrush = isDone ? new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81)) : new SolidColorBrush(Color.FromRgb(0x47, 0x55, 0x69)),
                        Background = isDone ? new SolidColorBrush(Color.FromArgb(0x33, 0x10, 0xB9, 0x81)) : Brushes.Transparent,
                        Cursor = Cursors.Hand,
                        Margin = new Thickness(0, 0, 8, 0),
                        VerticalAlignment = VerticalAlignment.Center,
                        ToolTip = isDone ? "点击标记为未完成" : "点击快速打卡完成"
                    };
                    chkBorder.Child = new TextBlock
                    {
                        Text = isDone ? "✓" : "",
                        FontSize = 10,
                        FontWeight = FontWeights.Bold,
                        Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81)),
                        HorizontalAlignment = HorizontalAlignment.Center,
                        VerticalAlignment = VerticalAlignment.Center
                    };
                    chkBorder.MouseLeftButtonDown += (s, e) =>
                    {
                        e.Handled = true;
                        item.Status = isDone ? "PENDING" : "COMPLETED";
                        item.UpdatedAt = DateTime.UtcNow;
                        if (!isDone && item.ActualMinutes == 0)
                        {
                            item.ActualMinutes = Math.Max(10, (int)(DateTime.Now - item.StartTime).TotalMinutes);
                        }
                        DatabaseService.UpsertSchedule(item);
                        DatabaseService.NotifyDataChanged();
                    };
                    Grid.SetColumn(chkBorder, 0);
                    row.Children.Add(chkBorder);

                    // 标题与时间
                    var contentStack = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
                    var titleText = new TextBlock
                    {
                        Text = item.Title,
                        FontSize = 11,
                        FontWeight = isDone ? FontWeights.Normal : FontWeights.SemiBold,
                        Foreground = isDone ? new SolidColorBrush(Color.FromRgb(0x64, 0x74, 0x8B)) : new SolidColorBrush(Color.FromRgb(0xE2, 0xE8, 0xF0)),
                        TextTrimming = TextTrimming.CharacterEllipsis
                    };
                    if (isDone)
                    {
                        titleText.TextDecorations = TextDecorations.Strikethrough;
                    }
                    contentStack.Children.Add(titleText);

                    // 时间与计划 vs 实际耗时标签
                    var metaStack = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 1, 0, 0) };
                    metaStack.Children.Add(new TextBlock
                    {
                        Text = $"{item.StartTime:HH:mm}-{item.EndTime:HH:mm}",
                        FontSize = 9.5,
                        Foreground = new SolidColorBrush(Color.FromRgb(0x94, 0xA3, 0xB8))
                    });

                    if (item.ActualMinutes > 0)
                    {
                        int diff = item.ActualMinutes - item.EstimatedMinutes;
                        string diffText = diff > 0 ? $"+{diff}m超时" : (diff < 0 ? $"{diff}m提前" : "准时");
                        var badgeColor = diff > 0 ? Color.FromRgb(0xF5, 0x9E, 0x0B) : Color.FromRgb(0x10, 0xB9, 0x81);

                        var badge = new Border
                        {
                            CornerRadius = new CornerRadius(3),
                            Background = new SolidColorBrush(Color.FromArgb(0x25, badgeColor.R, badgeColor.G, badgeColor.B)),
                            Padding = new Thickness(4, 1, 4, 1),
                            Margin = new Thickness(6, 0, 0, 0),
                            VerticalAlignment = VerticalAlignment.Center
                        };
                        badge.Child = new TextBlock
                        {
                            Text = $"实{item.ActualMinutes}m / 估{item.EstimatedMinutes}m ({diffText})",
                            FontSize = 8.5,
                            FontWeight = FontWeights.Medium,
                            Foreground = new SolidColorBrush(badgeColor)
                        };
                        metaStack.Children.Add(badge);
                    }

                    contentStack.Children.Add(metaStack);
                    Grid.SetColumn(contentStack, 1);
                    row.Children.Add(contentStack);

                    // 右侧操作组 (工作区直达 + 专注 HUD)
                    var actionStack = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };

                    var asset = WorkspaceLauncherService.DetectAsset(item);
                    if (asset != null)
                    {
                        var wsBtn = new TextBlock
                        {
                            Text = "📂",
                            FontSize = 10.5,
                            Cursor = Cursors.Hand,
                            ToolTip = $"一键直达工作区: {asset.DisplayName}\n({asset.Target})",
                            Margin = new Thickness(4, 0, 2, 0),
                            VerticalAlignment = VerticalAlignment.Center
                        };
                        wsBtn.MouseLeftButtonDown += (s, e) =>
                        {
                            e.Handled = true;
                            WorkspaceLauncherService.LaunchAsset(asset.Target);
                        };
                        actionStack.Children.Add(wsBtn);
                    }

                    // 快捷专注 HUD 启动按钮
                    var focusBtn = new TextBlock
                    {
                        Text = "🎯",
                        FontSize = 11,
                        Cursor = Cursors.Hand,
                        ToolTip = "针对此任务启动专注 HUD",
                        Margin = new Thickness(4, 0, 0, 0),
                        VerticalAlignment = VerticalAlignment.Center
                    };
                    focusBtn.MouseLeftButtonDown += (s, e) =>
                    {
                        e.Handled = true;
                        try
                        {
                            var hud = new FocusHudWindow(item, () => RefreshData());
                            hud.Show();
                        }
                        catch { }
                    };
                    actionStack.Children.Add(focusBtn);

                    Grid.SetColumn(actionStack, 2);
                    row.Children.Add(actionStack);

                    ScheduleItemsList.Children.Add(row);
                }
            }
        }
        catch { }
    }

    private void OnRefreshClicked(object sender, RoutedEventArgs e)
    {
        RefreshData();
    }

    private void OnTogglePinModeClicked(object sender, RoutedEventArgs e)
    {
        var helper = new WindowInteropHelper(this);
        switch (_currentPinMode)
        {
            case PinMode.Normal:
                _currentPinMode = PinMode.Topmost;
                Topmost = true;
                PinIconText.Text = "📌";
                PinIconText.Foreground = new SolidColorBrush(Color.FromRgb(0x38, 0xBD, 0xF8));
                PinModeHintText.Text = "模式: 始终置顶";
                break;

            case PinMode.Topmost:
                _currentPinMode = PinMode.DesktopBottom;
                Topmost = false;
                ApplyBottomPin();
                PinIconText.Text = "🪟";
                PinIconText.Foreground = new SolidColorBrush(Color.FromRgb(0x10, 0xB9, 0x81));
                PinModeHintText.Text = "模式: 贴桌置底 (壁纸层)";
                break;

            case PinMode.DesktopBottom:
                _currentPinMode = PinMode.Normal;
                Topmost = false;
                if (helper.Handle != IntPtr.Zero)
                {
                    SetWindowPos(helper.Handle, HWND_NOTOPMOST, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE);
                }
                PinIconText.Text = "📄";
                PinIconText.Foreground = new SolidColorBrush(Color.FromRgb(0x94, 0xA3, 0xB8));
                PinModeHintText.Text = "模式: 普通浮窗";
                break;
        }
    }

    private void OnQuickTaskTextChanged(object sender, TextChangedEventArgs e)
    {
        QuickTaskPlaceholder.Visibility = string.IsNullOrEmpty(QuickTaskInput.Text) ? Visibility.Visible : Visibility.Collapsed;
    }

    private void OnQuickTaskKeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Enter)
        {
            string text = QuickTaskInput.Text.Trim();
            if (!string.IsNullOrEmpty(text))
            {
                var now = DateTime.Now;
                var newItem = new ScheduleItem
                {
                    Id = Guid.NewGuid().ToString(),
                    Title = text,
                    StartTime = now,
                    EndTime = now.AddMinutes(30),
                    EstimatedMinutes = 30,
                    ActualMinutes = 0,
                    Status = "PENDING",
                    Category = "WORK",
                    Priority = "MEDIUM",
                    CreatedAt = now,
                    UpdatedAt = now
                };
                DatabaseService.UpsertSchedule(newItem);
                DatabaseService.NotifyDataChanged();
                QuickTaskInput.Text = "";
                RefreshData();
            }
        }
    }

    private void OnCloseClicked(object sender, RoutedEventArgs e)
    {
        Close();
    }
}
