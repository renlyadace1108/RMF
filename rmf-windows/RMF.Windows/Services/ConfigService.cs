using System;
using System.IO;
using System.Text.Json;

namespace RMF.Windows.Services;

public class AppConfig
{
    public string GeminiApiKey { get; set; } = string.Empty;
    public string SelectedModel { get; set; } = "gemini-2.5-flash";
    public string CustomModelName { get; set; } = string.Empty;
    public string CustomBaseUrl { get; set; } = string.Empty; // 可选自定义代理反向代理地址
    public double Temperature { get; set; } = 0.2; // 默认采用低采样温度，确保输出绝对客观、严谨、一致且可靠

    // Google Drive 云端同步配置
    public string GoogleClientId { get; set; } = string.Empty;
    public string GoogleClientSecret { get; set; } = string.Empty;
    public bool IsGoogleDriveLinked { get; set; } = false;
    public string LastSyncTime { get; set; } = string.Empty;

    /// <summary>
    /// 获取当前生效的最终模型名称
    /// </summary>
    public string GetEffectiveModel()
    {
        if (SelectedModel == "custom" && !string.IsNullOrWhiteSpace(CustomModelName))
        {
            return CustomModelName.Trim();
        }
        return string.IsNullOrWhiteSpace(SelectedModel) ? "gemini-2.5-flash" : SelectedModel;
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
        return "https://generativelanguage.googleapis.com";
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

        // Fallback to environment variable if empty
        if (string.IsNullOrWhiteSpace(_current.GeminiApiKey))
        {
            string? envKey = Environment.GetEnvironmentVariable("GEMINI_API_KEY");
            if (!string.IsNullOrWhiteSpace(envKey))
            {
                _current.GeminiApiKey = envKey.Trim();
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
