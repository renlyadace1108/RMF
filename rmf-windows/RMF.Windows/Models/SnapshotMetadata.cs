using System;
using System.Text.Json.Serialization;

namespace RMF.Windows.Models;

public class SnapshotMetadata
{
    [JsonPropertyName("id")]
    public string Id { get; set; } = "current";

    [JsonPropertyName("device_id")]
    public string DeviceId { get; set; } = Environment.MachineName;

    [JsonPropertyName("version_counter")]
    public long VersionCounter { get; set; } = 1;

    [JsonPropertyName("file_hash")]
    public string FileHash { get; set; } = string.Empty;

    [JsonPropertyName("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
