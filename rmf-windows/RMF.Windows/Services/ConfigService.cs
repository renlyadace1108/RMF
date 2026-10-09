using System;
using System.IO;
using System.Text.Json;

namespace RMF.Windows.Services;

public class AppConfig
{
    // 监管系统多模型核心配置
    public string ApiProvider { get; set; } = "Gemini"; // "Gemini" 或 "OpenAI"
    public string GeminiApiKey { get; set; } = string.Empty;
    public string OpenAiApiKey { get; set; } = string.Empty;
    public string SelectedModel { get; set; } = "gemini-2.5-flash";
    public string CustomModelName { get; set; } = string.Empty;
    public string CustomBaseUrl { get; set; } = string.Empty; // 可选自定义代理/反代地址
    public double Temperature { get; set; } = 0.2; // 默认采用低采样温度，确保输出绝对客观、严谨、一致

    // Google Drive 云端同步配置
    public string GoogleClientId { get; set; } = string.Empty;
    public string GoogleClientSecret { get; set; } = string.Empty;
    public string GoogleDriveFolderId { get; set; } = string.Empty;
    public string GoogleDriveFileId { get; set; } = string.Empty;
    public bool IsGoogleDriveLinked { get; set; } = false;
    public string LastSyncTime { get; set; } = string.Empty;

    // Google Calendar 日历同步配置
    public string GoogleCalendarIcsUrl { get; set; } = string.Empty;
    public string GoogleCalendarLastSyncTime { get; set; } = string.Empty;
    public bool IsGoogleCalendarLinked { get; set; } = false;

    /// <summary>
    /// 当前客户端自定义来源标识标签 (例如: "💻 桌面端", "💻 Windows办公机", "💻 笔记本")
    /// </summary>
    public string ClientSourceTag { get; set; } = "💻 桌面端";

    // 主题与色彩系统配置
    public string ThemeMode { get; set; } = "DARK"; // "DARK", "LIGHT", "SYSTEM"
    public string AccentColorHex { get; set; } = "#1A73E8"; // 默认 Google 蓝
    public string CustomDarkBgHex { get; set; } = "#202124"; // 默认深色背景
    public string CustomLightBgHex { get; set; } = "#F8F9FA"; // 默认日间背景

    // 新手引导配置
    public bool HasCompletedOnboarding { get; set; } = false;

    // 专注模式与悬浮胶囊管理配置
    public int FocusDefaultDurationMinutes { get; set; } = 25; // 默认番茄钟单次专注时长 (分钟)
    public int FocusBreakDurationMinutes { get; set; } = 5; // 默认休息时长 (分钟)
    public string FocusDefaultMode { get; set; } = "POMODORO"; // "POMODORO" (番茄钟) 或 "STOPWATCH" (正向秒表)
    public int FocusIdleThresholdMinutes { get; set; } = 5; // 闲置防作弊阈值 (分钟)

    /// <summary>
    /// 获取当前生效的最终模型名称
    /// </summary>
    public string GetEffectiveModel()
    {
        if (SelectedModel == "custom" && !string.IsNullOrWhiteSpace(CustomModelName))
        {
            return CustomModelName.Trim();
        }
        if (!string.IsNullOrWhiteSpace(SelectedModel) && SelectedModel != "custom")
        {
            return SelectedModel.Trim();
        }
        return "gemini-2.5-flash";
    }

    /// <summary>
    /// 获取当前生效的 API 基础地址
    /// </summary>
    public string GetEffectiveBaseUrl()
    {
        if (!string.IsNullOrWhiteSpace(CustomBaseUrl))
        {
            return CustomBaseUrl.Trim().TrimEnd('/');
        }
        return ApiProvider == "OpenAI"
            ? "https://api.deepseek.com"
            : "https://generativelanguage.googleapis.com";
    }

    /// <summary>
    /// 获取当前生效的有效 API Key
    /// </summary>
    public string GetEffectiveApiKey()
    {
        if (ApiProvider == "OpenAI")
        {
            if (!string.IsNullOrWhiteSpace(OpenAiApiKey)) return OpenAiApiKey.Trim();
            string? env = Environment.GetEnvironmentVariable("OPENAI_API_KEY") 
                          ?? Environment.GetEnvironmentVariable("DEEPSEEK_API_KEY");
            if (!string.IsNullOrWhiteSpace(env)) return env.Trim();
            return GeminiApiKey.Trim();
        }
        else
        {
            if (!string.IsNullOrWhiteSpace(GeminiApiKey)) return GeminiApiKey.Trim();
            string? env = Environment.GetEnvironmentVariable("GEMINI_API_KEY");
            if (!string.IsNullOrWhiteSpace(env)) return env.Trim();
            return OpenAiApiKey.Trim();
        }
    }
}

public static class ConfigService
{
    private static readonly string ConfigDir = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        "RMF"
    );
    private static readonly string ConfigPath = Path.Combine(ConfigDir, "config.json");

    private static AppConfig? _current;
    public static AppConfig Current => Load();

    public static AppConfig Load()
    {
        if (_current != null) return _current;

        try
        {
            if (File.Exists(ConfigPath))
            {
                string json = File.ReadAllText(ConfigPath);
                _current = JsonSerializer.Deserialize<AppConfig>(json) ?? new AppConfig();
            }
            else
            {
                _current = new AppConfig();
            }
        }
        catch
        {
            _current = new AppConfig();
        }

        // Fallback to environment variables if empty
        if (string.IsNullOrWhiteSpace(_current.GeminiApiKey))
        {
            string? envKey = Environment.GetEnvironmentVariable("GEMINI_API_KEY");
            if (!string.IsNullOrWhiteSpace(envKey))
            {
                _current.GeminiApiKey = envKey.Trim();
            }
        }
        if (string.IsNullOrWhiteSpace(_current.OpenAiApiKey))
        {
            string? envKey = Environment.GetEnvironmentVariable("OPENAI_API_KEY") 
                             ?? Environment.GetEnvironmentVariable("DEEPSEEK_API_KEY");
            if (!string.IsNullOrWhiteSpace(envKey))
            {
                _current.OpenAiApiKey = envKey.Trim();
            }
        }

        return _current;
    }

    public static void Save(AppConfig config)
    {
        _current = config;

        try
        {
            Directory.CreateDirectory(ConfigDir);
            string json = JsonSerializer.Serialize(_current, new JsonSerializerOptions { WriteIndented = true });
            File.WriteAllText(ConfigPath, json);
        }
        catch { }
    }
}