using System.Collections.Generic;
using System.Text.Json.Serialization;

namespace RMF.Windows.Models;

public class GeminiModelInfo
{
    [JsonPropertyName("name")]
    public string Name { get; set; } = string.Empty; // e.g. "models/gemini-2.5-flash"

    [JsonPropertyName("displayName")]
    public string DisplayName { get; set; } = string.Empty;

    [JsonPropertyName("description")]
    public string Description { get; set; } = string.Empty;

    [JsonPropertyName("supportedGenerationMethods")]
    public List<string> SupportedGenerationMethods { get; set; } = new();

    /// <summary>
    /// 去除 "models/" 前缀后的纯模型标识符 (如 "gemini-2.5-flash")
    /// </summary>
    public string ModelId => Name.StartsWith("models/") ? Name.Substring("models/".Length) : Name;

    public override string ToString()
    {
        return $"{DisplayName} ({ModelId})";
    }
}

public class GeminiModelListResponse
{
    [JsonPropertyName("models")]
    public List<GeminiModelInfo>? Models { get; set; }
}
