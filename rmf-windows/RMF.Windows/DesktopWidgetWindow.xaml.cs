using System;
using System.Linq;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using RMF.Windows.Models;
using RMF.Windows.Services;

namespace RMF.Windows;

public partial class DesktopWidgetWindow : Window
{
    public DesktopWidgetWindow()
    {
        InitializeComponent();

        // 默认靠屏幕右上角停靠
        Left = SystemParameters.PrimaryScreenWidth - Width - 20;
        Top = 60;

        Loaded += (s, e) => RefreshData();
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

    public void RefreshData()
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
            TodayCoursesText.Text = string.Join("\n", courses.Select(c => $"• {c.StartTime:HH:mm}-{c.EndTime:HH:mm} {c.CourseName} @ {c.Location}"));
        }
        else
        {
            TodayCoursesText.Text = "🌱 今日无课 / 自主攻坚自由日";
        }

        // 3. 今日日程
        ScheduleItemsList.Children.Clear();
        var schedules = DatabaseService.GetSchedulesForDate(DateTime.Today)
            .Where(s => !s.IsAllDay)
            .OrderBy(s => s.StartTime)
            .Take(5)
            .ToList();

        if (schedules.Count == 0)
        {
            ScheduleItemsList.Children.Add(new TextBlock
            {
                Text = "今日暂无排期任务",
                FontSize = 11,
                Foreground = new SolidColorBrush(Color.FromRgb(0x64, 0x74, 0x8B)),
                Margin = new Thickness(0, 4, 0, 0)
            });
        }
        else
        {
            foreach (var item in schedules)
            {
                var row = new Grid { Margin = new Thickness(0, 3, 0, 3) };
                row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

                var title = new TextBlock
                {
                    Text = (item.Status == "COMPLETED" ? "✓ " : "• ") + item.Title,
                    FontSize = 11,
                    Foreground = item.Status == "COMPLETED" ? new SolidColorBrush(Color.FromRgb(0x64, 0x74, 0x8B)) : new SolidColorBrush(Color.FromRgb(0xE2, 0xE8, 0xF0)),
                    TextTrimming = TextTrimming.CharacterEllipsis
                };

                var time = new TextBlock
                {
                    Text = $"{item.StartTime:HH:mm}",
                    FontSize = 10,
                    Foreground = new SolidColorBrush(Color.FromRgb(0x94, 0xA3, 0xB8)),
                    Margin = new Thickness(6, 0, 0, 0)
                };

                Grid.SetColumn(title, 0);
                Grid.SetColumn(time, 1);
                row.Children.Add(title);
                row.Children.Add(time);
                ScheduleItemsList.Children.Add(row);
            }
        }
    }

    private void OnRefreshClicked(object sender, RoutedEventArgs e)
    {
        RefreshData();
    }

    private void OnToggleTopmostClicked(object sender, RoutedEventArgs e)
    {
        Topmost = !Topmost;
        PinIconText.Foreground = Topmost ? new SolidColorBrush(Color.FromRgb(0x38, 0xBD, 0xF8)) : new SolidColorBrush(Color.FromRgb(0x94, 0xA3, 0xB8));
    }

    private void OnCloseClicked(object sender, RoutedEventArgs e)
    {
        Close();
    }
}
