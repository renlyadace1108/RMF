using System;
using System.IO;
using System.Text.Json;

namespace RMF.Windows.Services;

public class AppConfig
{
    public string GeminiApiKey { get; set; } = string.Empty;
    public string GeminiModel { get; set; } = "gemini-2.5-flash";
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

    public static void Save(string apiKey, string model = "gemini-2.5-flash")
    {
        _current ??= new AppConfig();
        _current.GeminiApiKey = apiKey.Trim();
        _current.GeminiModel = model.Trim();

        try
        {
            Directory.CreateDirectory(ConfigDir);
            string json = JsonSerializer.Serialize(_current, new JsonSerializerOptions { WriteIndented = true });
            File.WriteAllText(ConfigPath, json);
        }
        catch { }
    }
}
