using System;
using System.Diagnostics;
using System.IO;
using System.Text.RegularExpressions;
using System.Windows;
using RMF.Windows.Models;

namespace RMF.Windows.Services;

public enum WorkspaceAssetType
{
    None,
    Url,
    Folder,
    File
}

public class WorkspaceAsset
{
    public WorkspaceAssetType Type { get; set; } = WorkspaceAssetType.None;
    public string Target { get; set; } = string.Empty;
    public string DisplayName { get; set; } = string.Empty;
    public string Icon => Type switch
    {
        WorkspaceAssetType.Folder => "📁",
        WorkspaceAssetType.File => "📄",
        WorkspaceAssetType.Url => "🌐",
        _ => "🔗"
    };
}

/// <summary>
/// 任务资产与工作区直达服务 (Task Context & Workspace Launcher)
/// 自动智能提取日程/待办关联的本地代码库、文件夹、文档或在线URL，一键直达上下文
/// </summary>
public static class WorkspaceLauncherService
{
    private static readonly Regex ExplicitTagRegex = new(
        @"(?:工作区|资产|workspace|asset|repo|path)\s*[:：]\s*(?<target>[^\r\n]+)",
        RegexOptions.IgnoreCase | RegexOptions.Compiled);

    private static readonly Regex MarkdownLinkRegex = new(
        @"\[(?<text>[^\]]+)\]\((?<url>https?://[^\s\)]+|[a-zA-Z]:\\[^\s\)]+)\)",
        RegexOptions.IgnoreCase | RegexOptions.Compiled);

    private static readonly Regex HttpUrlRegex = new(
        @"(https?://[^\s<>'""{}\|\^`]+)",
        RegexOptions.IgnoreCase | RegexOptions.Compiled);

    private static readonly Regex WindowsPathRegex = new(
        @"([a-zA-Z]:\\[^\s<>'""{}\|\^`\r\n]+|\\\\[^\s<>'""{}\|\^`\r\n]+)",
        RegexOptions.IgnoreCase | RegexOptions.Compiled);

    /// <summary>
    /// 从日程实体、描述或绑定的学习主题中自动侦测关联工作区与资产
    /// </summary>
    public static WorkspaceAsset? DetectAsset(ScheduleItem? schedule)
    {
        if (schedule == null) return null;

        // 1. 优先从任务描述中检索
        var asset = ExtractAssetFromText(schedule.Description);
        if (asset != null) return asset;

        // 2. 从 DoD 验收标准中检索
        asset = ExtractAssetFromText(schedule.Dod);
        if (asset != null) return asset;

        // 3. 从关联的学习主题中检索
        if (!string.IsNullOrWhiteSpace(schedule.StudyTopicId))
        {
            try
            {
                var topic = DatabaseService.GetStudyTopicById(schedule.StudyTopicId);
                if (topic != null)
                {
                    asset = ExtractAssetFromText(topic.Notes) ?? ExtractAssetFromText(topic.CurrentCheckpoint);
                    if (asset != null)
                    {
                        if (string.IsNullOrWhiteSpace(asset.DisplayName))
                        {
                            asset.DisplayName = topic.Title;
                        }
                        return asset;
                    }
                }
            }
            catch { }
        }

        return null;
    }

    /// <summary>
    /// 从文本中提取第一个有效的工作区资产
    /// </summary>
    public static WorkspaceAsset? ExtractAssetFromText(string? text)
    {
        if (string.IsNullOrWhiteSpace(text)) return null;

        // A. 显式前缀标识: workspace: / 工作区: / asset:
        var tagMatch = ExplicitTagRegex.Match(text);
        if (tagMatch.Success)
        {
            var raw = tagMatch.Groups["target"].Value.Trim().Trim('"', '\'', '`');
            var parsed = ResolveAsset(raw);
            if (parsed != null) return parsed;
        }

        // B. Markdown 链接: [文档名](https://... 或 C:\...)
        var mdMatch = MarkdownLinkRegex.Match(text);
        if (mdMatch.Success)
        {
            var label = mdMatch.Groups["text"].Value.Trim();
            var target = mdMatch.Groups["url"].Value.Trim();
            var parsed = ResolveAsset(target);
            if (parsed != null)
            {
                if (!string.IsNullOrWhiteSpace(label))
                {
                    parsed.DisplayName = label;
                }
                return parsed;
            }
        }

        // C. Windows 本地路径: C:\Projects\...
        var pathMatch = WindowsPathRegex.Match(text);
        if (pathMatch.Success)
        {
            var target = pathMatch.Value.Trim().TrimEnd('.', ';', ',', ')', ']');
            var parsed = ResolveAsset(target);
            if (parsed != null) return parsed;
        }

        // D. HTTP/HTTPS 在线超链接
        var urlMatch = HttpUrlRegex.Match(text);
        if (urlMatch.Success)
        {
            var target = urlMatch.Value.Trim().TrimEnd('.', ';', ',', ')', ']');
            var parsed = ResolveAsset(target);
            if (parsed != null) return parsed;
        }

        return null;
    }

    private static WorkspaceAsset? ResolveAsset(string target)
    {
        if (string.IsNullOrWhiteSpace(target)) return null;

        target = target.Trim();

        // 1. Web URL
        if (target.StartsWith("http://", StringComparison.OrdinalIgnoreCase) ||
            target.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
        {
            if (Uri.TryCreate(target, UriKind.Absolute, out var uri))
            {
                string host = uri.Host;
                string seg = uri.AbsolutePath.Trim('/');
                string display = host;
                if (!string.IsNullOrEmpty(seg) && seg.Length <= 24)
                {
                    display = $"{host}/{seg}";
                }
                return new WorkspaceAsset
                {
                    Type = WorkspaceAssetType.Url,
                    Target = target,
                    DisplayName = display
                };
            }
            return new WorkspaceAsset
            {
                Type = WorkspaceAssetType.Url,
                Target = target,
                DisplayName = target.Length > 28 ? target[..28] + "..." : target
            };
        }

        // 2. 本地文件夹
        if (Directory.Exists(target))
        {
            string folderName = Path.GetFileName(target.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar));
            if (string.IsNullOrWhiteSpace(folderName)) folderName = target;

            return new WorkspaceAsset
            {
                Type = WorkspaceAssetType.Folder,
                Target = target,
                DisplayName = folderName
            };
        }

        // 3. 本地文件
        if (File.Exists(target))
        {
            string fileName = Path.GetFileName(target);
            return new WorkspaceAsset
            {
                Type = WorkspaceAssetType.File,
                Target = target,
                DisplayName = fileName
            };
        }

        // 4. 路径格式符合本地路径规则 (即使目前未挂载或驱动器断开)
        if (WindowsPathRegex.IsMatch(target))
        {
            bool hasExt = Path.HasExtension(target);
            string name = Path.GetFileName(target);
            if (string.IsNullOrWhiteSpace(name)) name = target;

            return new WorkspaceAsset
            {
                Type = hasExt ? WorkspaceAssetType.File : WorkspaceAssetType.Folder,
                Target = target,
                DisplayName = name
            };
        }

        return null;
    }

    /// <summary>
    /// 一键直达打开资产或工作区
    /// </summary>
    public static bool LaunchAsset(string target)
    {
        if (string.IsNullOrWhiteSpace(target)) return false;

        try
        {
            target = target.Trim();

            // Web URL
            if (target.StartsWith("http://", StringComparison.OrdinalIgnoreCase) ||
                target.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
            {
                Process.Start(new ProcessStartInfo(target) { UseShellExecute = true });
                return true;
            }

            // 本地文件夹 -> Explorer
            if (Directory.Exists(target))
            {
                Process.Start(new ProcessStartInfo("explorer.exe", $"\"{target}\"") { UseShellExecute = true });
                return true;
            }

            // 本地文件 -> 默认关联软件
            if (File.Exists(target))
            {
                Process.Start(new ProcessStartInfo(target) { UseShellExecute = true });
                return true;
            }

            // 兜底 ShellExecute 打开
            Process.Start(new ProcessStartInfo(target) { UseShellExecute = true });
            return true;
        }
        catch (Exception ex)
        {
            MessageBox.Show(
                $"无法直达工作区资产：\n目标：{target}\n原因：{ex.Message}",
                "工作区直达提示",
                MessageBoxButton.OK,
                MessageBoxImage.Warning);
            return false;
        }
    }
}
