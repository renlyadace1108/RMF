using System;
using System.Collections.Generic;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;

namespace RMF.Windows.Services;

/// <summary>
/// 高性能平滑惯性滚动中枢：
/// 1. 消除原生 WPF ScrollViewer 48px 生硬锯齿跳跃，引入 60/120Hz 指数衰减缓动
/// 2. 深度穿透嵌套 ScrollViewer 滚轮死锁区（Scroll Trap），解决悬停卡死
/// 3. 与 BitmapCache 硬件纹理加速协同，实现丝滑流畅的仪表盘浏览体验
/// </summary>
public static class SmoothScrollHelper
{
    private sealed class ScrollState
    {
        public readonly ScrollViewer ScrollViewer;
        public double TargetOffset;
        public bool IsAnimating;
        public EventHandler? RenderHandler;
        public double DistanceMultiplier = 1.0;

        public ScrollState(ScrollViewer sv, double multiplier)
        {
            ScrollViewer = sv;
            DistanceMultiplier = multiplier;
        }
    }

    private static readonly Dictionary<ScrollViewer, ScrollState> _states = new();

    /// <summary>
    /// 为指定 ScrollViewer 挂载丝滑物理滚动加速
    /// </summary>
    public static void EnableSmoothScroll(ScrollViewer? sv, double distanceMultiplier = 1.0)
    {
        if (sv == null) return;
        if (_states.ContainsKey(sv)) return;

        var state = new ScrollState(sv, distanceMultiplier);
        _states[sv] = state;

        sv.PreviewMouseWheel += (sender, e) =>
        {
            if (sv.ScrollableHeight <= 0) return;

            e.Handled = true;

            if (!state.IsAnimating)
            {
                state.TargetOffset = sv.VerticalOffset;
            }

            // Windows 标准滚轮 1 刻度为 120，平滑滚行动量映射为 ~110px
            double step = -(e.Delta / 120.0) * (110.0 * state.DistanceMultiplier);
            state.TargetOffset = Math.Clamp(state.TargetOffset + step, 0, sv.ScrollableHeight);

            if (!state.IsAnimating)
            {
                state.IsAnimating = true;
                state.RenderHandler = (s, args) =>
                {
                    if (!state.IsAnimating || !sv.IsVisible || sv.ScrollableHeight <= 0)
                    {
                        state.IsAnimating = false;
                        if (state.RenderHandler != null)
                        {
                            CompositionTarget.Rendering -= state.RenderHandler;
                        }
                        return;
                    }

                    double current = sv.VerticalOffset;
                    double diff = state.TargetOffset - current;

                    if (Math.Abs(diff) < 0.6)
                    {
                        sv.ScrollToVerticalOffset(state.TargetOffset);
                        state.IsAnimating = false;
                        if (state.RenderHandler != null)
                        {
                            CompositionTarget.Rendering -= state.RenderHandler;
                        }
                    }
                    else
                    {
                        // 0.25 阻尼比：每帧逼近剩余距离的 25%，呈现极致自然的平滑减速
                        sv.ScrollToVerticalOffset(current + diff * 0.25);
                    }
                };

                CompositionTarget.Rendering += state.RenderHandler;
            }
        };

        // 当用户直接拖拽滚动条或触控条时，同步修正目标 Offset，防止动量错位
        sv.ScrollChanged += (sender, e) =>
        {
            if (!state.IsAnimating)
            {
                state.TargetOffset = sv.VerticalOffset;
            }
        };
    }

    /// <summary>
    /// 解决嵌套 ScrollViewer（如卡片内部列表）截断父级滚轮的经典 WPF 缺陷：
    /// 当子容器无内容、内容未溢出、或滚动到顶/底边界时，滚轮动量无缝穿透转发给父级
    /// </summary>
    public static void RegisterNestedChild(ScrollViewer? child, ScrollViewer? parent)
    {
        if (child == null || parent == null) return;

        child.PreviewMouseWheel += (sender, e) =>
        {
            bool atTop = child.VerticalOffset <= 0.001 && e.Delta > 0;
            bool atBottom = child.VerticalOffset >= (child.ScrollableHeight - 0.001) && e.Delta < 0;

            if (child.ScrollableHeight <= 0 || atTop || atBottom)
            {
                e.Handled = true;

                // 穿透触发父容器平滑滚动
                var parentArgs = new MouseWheelEventArgs(e.MouseDevice, e.Timestamp, e.Delta)
                {
                    RoutedEvent = UIElement.PreviewMouseWheelEvent,
                    Source = parent
                };
                parent.RaiseEvent(parentArgs);
            }
        };
    }
}
