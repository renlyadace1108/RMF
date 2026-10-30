using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading;
using System.Threading.Tasks;
using Google.Apis.Auth.OAuth2;
using Google.Apis.Drive.v3;
using Google.Apis.Services;
using Google.Apis.Util.Store;
using RMF.Windows.Models;

namespace RMF.Windows.Services;

/// <summary>
/// Google Drive 远端版本描述清单实体 (rmf_release_info.json)
/// </summary>
public class ReleaseInfo
{
    [JsonPropertyName("versionCode")]
    public int VersionCode { get; set; }

    [JsonPropertyName("versionName")]
    public string VersionName { get; set; } = string.Empty;

    [JsonPropertyName("releaseDate")]
    public string ReleaseDate { get; set; } = string.Empty;

    [JsonPropertyName("changelog")]
    public string Changelog { get; set; } = string.Empty;

    [JsonPropertyName("minSupportedVersion")]
    public int MinSupportedVersion { get; set; }

    [JsonPropertyName("phoneApkFileId")]
    public string PhoneApkFileId { get; set; } = string.Empty;

    [JsonPropertyName("phoneApkName")]
    public string PhoneApkName { get; set; } = string.Empty;

    [JsonPropertyName("phoneApkSize")]
    public long PhoneApkSize { get; set; }

    [JsonPropertyName("padApkFileId")]
    public string PadApkFileId { get; set; } = string.Empty;

    [JsonPropertyName("padApkName")]
    public string PadApkName { get; set; } = string.Empty;

    [JsonPropertyName("padApkSize")]
    public long PadApkSize { get; set; }

    [JsonPropertyName("windowsInstallerFileId")]
    public string WindowsInstallerFileId { get; set; } = string.Empty;

    [JsonPropertyName("windowsInstallerName")]
    public string WindowsInstallerName { get; set; } = string.Empty;

    [JsonPropertyName("windowsInstallerSize")]
    public long WindowsInstallerSize { get; set; }

    [JsonPropertyName("windowsZipFileId")]
    public string WindowsZipFileId { get; set; } = string.Empty;

    [JsonPropertyName("windowsZipName")]
    public string WindowsZipName { get; set; } = string.Empty;

    [JsonPropertyName("windowsZipSize")]
    public long WindowsZipSize { get; set; }
}

public enum CloudSyncState
{
    Idle,       // 未连接/空闲
    Syncing,    // 正在双向同步
    Success,    // 同步完成/已与云端保持最新 (🟢)
    Error       // 离线/连接超时/异常 (🟡)
}

public class GoogleDriveSyncService
{
    public static event Action<CloudSyncState, string, string?>? OnSyncStateChanged;

    public static void NotifySyncState(CloudSyncState state, string message, string? lastSync = null)
    {
        try
        {
            OnSyncStateChanged?.Invoke(state, message, lastSync);
        }
        catch { }
    }
    private static readonly string[] Scopes = { DriveService.Scope.DriveFile };
    private const string ApplicationName = "Renly Management Platform";
    private const string BackupFileName = "data.db";
    private const string BackupFolderName = "RMF_CloudSync";

    public static bool IsAutoSyncing { get; private set; }
    public static volatile bool IsRestoring = false;

    /// <summary>
    /// 判断本地 Google Drive 是否已处于可用且已授权状态
    /// </summary>
    public static bool IsAuthorized(AppConfig? config = null)
    {
        config ??= ConfigService.Load();
        if (!config.IsGoogleDriveLinked) return false;
        if (string.IsNullOrWhiteSpace(config.GoogleClientId) || string.IsNullOrWhiteSpace(config.GoogleClientSecret)) return false;
        if (!string.IsNullOrEmpty(config.GoogleDriveRefreshToken)) return true;
        return Directory.Exists(TokenFolder) && Directory.GetFiles(TokenFolder).Length > 0;
    }

    /// <summary>
    /// 请求触发一次防抖自动云端同步（用户已配置为仅手动上传/下载，此处直接返回）
    /// </summary>
    public static void RequestAutoSync(Action<string>? statusCallback = null)
    {
        // 用户明确要求：“就只保留上传和下载两个，不要比对了”
        // 彻底关闭后台自动竞态上传，由用户完全自主通过「上传到云端」与「从云端下载」确定性操作
        return;
    }

    private static readonly string TokenFolder = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        "RMF",
        "GoogleDriveTokens"
    );

    /// <summary>
    /// 初始化并执行 OAuth 2.0 授权，获取 DriveService 客户端
    /// </summary>
    public static async Task<DriveService> GetDriveServiceAsync(string clientId, string clientSecret, CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(clientId) || string.IsNullOrWhiteSpace(clientSecret))
        {
            throw new ArgumentException("请提供有效的 Google Client ID 和 Client Secret。");
        }

        if (!Directory.Exists(TokenFolder))
        {
            Directory.CreateDirectory(TokenFolder);
        }

        var clientSecrets = new ClientSecrets
        {
            ClientId = clientId.Trim(),
            ClientSecret = clientSecret.Trim()
        };

        UserCredential credential;
        try
        {
            credential = await GoogleWebAuthorizationBroker.AuthorizeAsync(
                clientSecrets,
                Scopes,
                "user",
                cancellationToken,
                new FileDataStore(TokenFolder, true)
            );

            // 预先探测 Token 是否已陈旧/过期；若已过期 (如超过 7 天测试期 invalid_grant)，主动探测刷新
            if (credential.Token.IsStale)
            {
                bool refreshed = await credential.RefreshTokenAsync(cancellationToken);
                if (!refreshed)
                {
                    throw new InvalidOperationException("Token refresh returned false (invalid_grant)");
                }
            }
        }
        catch (Exception ex) when (ex.Message.Contains("invalid_grant", StringComparison.OrdinalIgnoreCase) ||
                                  ex.InnerException?.Message.Contains("invalid_grant", StringComparison.OrdinalIgnoreCase) == true)
        {
            // 授权已过期：彻底清理过期缓存文件，触发浏览器重新登录授权
            ClearLocalCredentials();
            Directory.CreateDirectory(TokenFolder);

            credential = await GoogleWebAuthorizationBroker.AuthorizeAsync(
                clientSecrets,
                Scopes,
                "user",
                cancellationToken,
                new FileDataStore(TokenFolder, true)
            );
        }

        try
        {
            var config = ConfigService.Load();
            config.IsGoogleDriveLinked = true;
            if (!string.IsNullOrEmpty(credential.Token.RefreshToken))
            {
                config.GoogleDriveRefreshToken = credential.Token.RefreshToken;
            }
            ConfigService.Save(config);
        }
        catch { }

        return new DriveService(new BaseClientService.Initializer
        {
            HttpClientInitializer = credential,
            ApplicationName = ApplicationName
        });
    }

    /// <summary>
    /// 确保在 Google Drive 根目录创建或找到专属云存储文件夹
    /// </summary>
    public static async Task<string> EnsureBackupFolderAsync(DriveService service, CancellationToken cancellationToken = default)
    {
        var listReq = service.Files.List();
        listReq.Q = $"mimeType = 'application/vnd.google-apps.folder' and name = '{BackupFolderName}' and trashed = false";
        listReq.Spaces = "drive";
        listReq.OrderBy = "modifiedTime desc";
        listReq.Fields = "files(id, name)";

        var res = await listReq.ExecuteAsync(cancellationToken);
        if (res.Files != null && res.Files.Count > 0)
        {
            return res.Files[0].Id;
        }

        // 创建文件夹
        var folderMetadata = new Google.Apis.Drive.v3.Data.File
        {
            Name = BackupFolderName,
            MimeType = "application/vnd.google-apps.folder"
        };

        var createReq = service.Files.Create(folderMetadata);
        createReq.Fields = "id";
        var folder = await createReq.ExecuteAsync(cancellationToken);
        return folder.Id;
    }

    /// <summary>
    /// 上传本地数据库至 Google Drive 进行云端同步（唯一文件 data.db）
    /// </summary>
    public static async Task<(bool success, string fileId, string message)> UploadBackupToDriveAsync(string clientId, string clientSecret, Action<string>? progressCallback = null, CancellationToken cancellationToken = default)
    {
        try
        {
            progressCallback?.Invoke("正在连接 Google Drive API 并校验授权...");
            using var service = await GetDriveServiceAsync(clientId, clientSecret, cancellationToken);

            // 0. 更新本地快照物理版本计数器
            var localMeta = DatabaseService.GetSnapshotMetadata();
            localMeta.VersionCounter++;
            localMeta.DeviceId = Environment.MachineName;
            localMeta.UpdatedAt = DateTime.UtcNow;
            DatabaseService.SaveSnapshotMetadata(localMeta);

            // 统计本地数据指纹（日程数、课表数）
            int scheduleCount = 0;
            int courseCount = 0;
            try
            {
                using var cntConn = new Microsoft.Data.Sqlite.SqliteConnection(DatabaseService.ConnectionString);
                cntConn.Open();
                using var scCmd = new Microsoft.Data.Sqlite.SqliteCommand("SELECT count(*) FROM schedules WHERE is_deleted = 0;", cntConn);
                scheduleCount = Convert.ToInt32(scCmd.ExecuteScalar());
                using var ccCmd = new Microsoft.Data.Sqlite.SqliteCommand("SELECT count(*) FROM courses;", cntConn);
                courseCount = Convert.ToInt32(ccCmd.ExecuteScalar());
            }
            catch { }

            // 1. 生成一份原子只读数据库快照
            progressCallback?.Invoke("正在生成本地数据库原子只读快照...");
            string snapshotPath = DatabaseService.CreateSafeDatabaseSnapshot();

            try
            {
                // 2. 全局穿透检索云端最新数据文件 data.db，打破文件夹隔离
                progressCallback?.Invoke($"正在全局检索云端唯一数据文件 ({BackupFileName})...");

                using var stream = new FileStream(snapshotPath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);

                var listReq = service.Files.List();
                listReq.Q = $"(name = '{BackupFileName}' or name = 'data' or name = 'rmf_cloud_backup.db') and trashed = false";
                listReq.Spaces = "drive";
                listReq.OrderBy = "modifiedTime desc";
                listReq.PageSize = 20;
                listReq.Fields = "files(id, name, modifiedTime, parents)";
                var searchRes = await listReq.ExecuteAsync(cancellationToken);

                var existingFile = searchRes.Files?.FirstOrDefault();
                string newFileId;

                if (existingFile != null)
                {
                    progressCallback?.Invoke($"正在更新覆盖云端数据文件 ({existingFile.Name})...");
                    var updateMeta = new Google.Apis.Drive.v3.Data.File
                    {
                        Name = BackupFileName,
                        Description = $"RMF Cloud Database Snapshot ({Environment.MachineName} - {DateTime.Now:yyyy-MM-dd HH:mm:ss})"
                    };
                    var updateReq = service.Files.Update(updateMeta, existingFile.Id, stream, "application/x-sqlite3");
                    updateReq.Fields = "id, name";
                    var updateProgress = await updateReq.UploadAsync(cancellationToken);
                    if (updateProgress.Status != Google.Apis.Upload.UploadStatus.Completed)
                    {
                        return (false, string.Empty, $"上传失败: {updateProgress.Exception?.Message ?? updateProgress.Status.ToString()}");
                    }
                    newFileId = existingFile.Id;
                }
                else
                {
                    progressCallback?.Invoke("正在定位专属云端同步目录...");
                    string folderId = await EnsureBackupFolderAsync(service, cancellationToken);

                    progressCallback?.Invoke($"正在创建并上传云端数据文件 ({BackupFileName})...");
                    var createMeta = new Google.Apis.Drive.v3.Data.File
                    {
                        Name = BackupFileName,
                        Parents = new List<string> { folderId },
                        Description = $"RMF Cloud Database Backup ({Environment.MachineName} - {DateTime.Now:yyyy-MM-dd HH:mm:ss})"
                    };
                    var createReq = service.Files.Create(createMeta, stream, "application/x-sqlite3");
                    createReq.Fields = "id, name";
                    var uploadProgress = await createReq.UploadAsync(cancellationToken);
                    if (uploadProgress.Status != Google.Apis.Upload.UploadStatus.Completed)
                    {
                        return (false, string.Empty, $"上传失败: {uploadProgress.Exception?.Message ?? uploadProgress.Status.ToString()}");
                    }
                    newFileId = createReq.ResponseBody.Id;
                }

                var config = ConfigService.Load();
                config.GoogleClientId = clientId.Trim();
                config.GoogleClientSecret = clientSecret.Trim();
                config.GoogleDriveFileId = newFileId;
                config.IsGoogleDriveLinked = true;
                config.LastSyncTime = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
                config.LastSyncedTimestamp = config.LastSyncTime;
                ConfigService.Save(config);

                // 3. 清理云端同目录下所有旧的历史多余文件，坚决保证云端仅保留唯一的 data.db
                try
                {
                    if (searchRes.Files != null)
                    {
                        var obsoleteFiles = searchRes.Files.Where(f => f.Id != newFileId).ToList();
                        foreach (var obs in obsoleteFiles)
                        {
                            try { await service.Files.Delete(obs.Id).ExecuteAsync(cancellationToken); } catch { }
                        }
                    }
                }
                catch { }

                // 清理 schedules 中的本地 dirty 标记
                try
                {
                    using var cleanConn = new Microsoft.Data.Sqlite.SqliteConnection(DatabaseService.ConnectionString);
                    cleanConn.Open();
                    using var clrCmd = new Microsoft.Data.Sqlite.SqliteCommand("UPDATE schedules SET is_dirty = 0 WHERE is_dirty = 1;", cleanConn);
                    clrCmd.ExecuteNonQuery();
                }
                catch { }

                long sizeKb = new FileInfo(snapshotPath).Length / 1024;
                string successMsg = $"已成功上传云端数据文件「{BackupFileName}」！\n📦 大小: {sizeKb} KB\n📊 包含: {scheduleCount} 条日程，{courseCount} 门课程\n🕒 时间: {config.LastSyncTime}";

                NotifySyncState(CloudSyncState.Success, "云端已保存最新数据", config.LastSyncTime);
                return (true, newFileId, successMsg);
            }
            finally
            {
                // 确保释放连接池并清理临时本地只读副本，保证本地严格只有一个 active 数据库
                Microsoft.Data.Sqlite.SqliteConnection.ClearAllPools();
                if (File.Exists(snapshotPath))
                {
                    try { File.Delete(snapshotPath); } catch { }
                }
                DatabaseService.CleanupLocalBackupFiles();
            }
        }
        catch (Exception ex)
        {
            bool isInvalidGrant = ex.Message.Contains("invalid_grant", StringComparison.OrdinalIgnoreCase) ||
                                  ex.InnerException?.Message.Contains("invalid_grant", StringComparison.OrdinalIgnoreCase) == true;
            if (isInvalidGrant)
            {
                ClearLocalCredentials();
                string errMsg = "Google 授权凭据已过期或失效 (invalid_grant)。\n\n已自动重置本地凭据缓存。请重新在浏览器中登录授权。\n\n💡 提示：若 Google Cloud 处于测试 (Testing) 模式，授权每 7 天会失效。在 Google Cloud 控制台将应用发布状态设为「生产 (In Production)」，即可永久有效。";
                NotifySyncState(CloudSyncState.Error, "Google 授权已过期，请重新登录");
                return (false, string.Empty, errMsg);
            }
            NotifySyncState(CloudSyncState.Error, $"同步上传异常: {ex.Message}");
            return (false, string.Empty, $"同步上传异常: {ex.Message}");
        }
    }

    /// <summary>
    /// 从 Google Drive 专属同步文件夹检索并拉取唯一数据文件 data.db，安全替换本地数据库
    /// </summary>
    public static async Task<(bool success, string message)> DownloadBackupFromDriveAsync(string clientId, string clientSecret, Action<string>? progressCallback = null, CancellationToken cancellationToken = default)
    {
        IsRestoring = true;
        try
        {
            NotifySyncState(CloudSyncState.Syncing, "正在拉取云端数据文件...");
            progressCallback?.Invoke("正在连接 Google Drive API...");
            using var service = await GetDriveServiceAsync(clientId, clientSecret, cancellationToken);

            progressCallback?.Invoke("正在全局检索云端最新数据文件...");
            var listReq = service.Files.List();
            listReq.Q = $"(name = '{BackupFileName}' or name = 'data' or name = 'rmf_cloud_backup.db' or name contains 'rmf_backup_') and trashed = false";
            listReq.Spaces = "drive";
            listReq.OrderBy = "modifiedTime desc";
            listReq.PageSize = 20;
            listReq.Fields = "files(id, name, createdTime, modifiedTime, size, parents)";

            var searchRes = await listReq.ExecuteAsync(cancellationToken);
            Google.Apis.Drive.v3.Data.File? remoteFile = searchRes.Files?.FirstOrDefault(f => f.Name == BackupFileName || f.Name == "data")
                ?? searchRes.Files?.FirstOrDefault(f => f.Name == "rmf_cloud_backup.db")
                ?? searchRes.Files?.FirstOrDefault();

            if (remoteFile == null)
            {
                // 回退到特定同步目录检索
                string folderId = await EnsureBackupFolderAsync(service, cancellationToken);
                var fallbackReq = service.Files.List();
                fallbackReq.Q = $"'{folderId}' in parents and trashed = false";
                fallbackReq.Spaces = "drive";
                fallbackReq.OrderBy = "modifiedTime desc";
                fallbackReq.PageSize = 20;
                fallbackReq.Fields = "files(id, name, createdTime, modifiedTime, size)";
                var fallbackRes = await fallbackReq.ExecuteAsync(cancellationToken);
                remoteFile = fallbackRes.Files?.FirstOrDefault();
            }

            if (remoteFile == null)
            {
                NotifySyncState(CloudSyncState.Error, "未发现云端有效数据文件");
                return (false, $"在 Google Drive 中未发现有效数据文件 ({BackupFileName})。");
            }
            string tempDownloaded = Path.Combine(DatabaseService.DbDir, "rmf_downloaded_temp.db");

            progressCallback?.Invoke($"正在拉取最新云端快照 ({remoteFile.Name})...");
            var getReq = service.Files.Get(remoteFile.Id);

            using (var fileStream = new FileStream(tempDownloaded, FileMode.Create, FileAccess.Write))
            {
                await getReq.DownloadAsync(fileStream, cancellationToken);
            }

            // 验证拉取的数据库是否完整有效并统计数据指纹
            progressCallback?.Invoke("正在校验云端数据完整性...");
            int restoredScheduleCount = 0;
            int restoredCourseCount = 0;
            try
            {
                using (var verifyConn = new Microsoft.Data.Sqlite.SqliteConnection($"Data Source={tempDownloaded};Pooling=False"))
                {
                    verifyConn.Open();
                    using var cmd = new Microsoft.Data.Sqlite.SqliteCommand("PRAGMA integrity_check;", verifyConn);
                    string? checkResult = cmd.ExecuteScalar()?.ToString();
                    if (checkResult != "ok")
                    {
                        try { File.Delete(tempDownloaded); } catch { }
                        NotifySyncState(CloudSyncState.Error, "云端数据完整性校验未通过");
                        return (false, "云端下载的数据库完整性检验未通过，已取消载入。");
                    }

                    try
                    {
                        using var scCmd = new Microsoft.Data.Sqlite.SqliteCommand("SELECT count(*) FROM schedules WHERE is_deleted = 0;", verifyConn);
                        restoredScheduleCount = Convert.ToInt32(scCmd.ExecuteScalar());
                        using var ccCmd = new Microsoft.Data.Sqlite.SqliteCommand("SELECT count(*) FROM courses;", verifyConn);
                        restoredCourseCount = Convert.ToInt32(ccCmd.ExecuteScalar());
                    }
                    catch { }

                    verifyConn.Close();
                }
                Microsoft.Data.Sqlite.SqliteConnection.ClearAllPools();
            }
            catch (Exception ex)
            {
                try { File.Delete(tempDownloaded); } catch { }
                NotifySyncState(CloudSyncState.Error, $"云端数据库校验失败: {ex.Message}");
                return (false, $"云端数据库校验失败: {ex.Message}");
            }

            // 核心：直接使用云端唯一数据文件安全覆盖本地数据库
            progressCallback?.Invoke("正在安全载入云端最新数据文件...");
            bool replaced = DatabaseService.ReplaceDatabaseFile(tempDownloaded);

            long sizeKb = new FileInfo(tempDownloaded).Length / 1024;
            try { File.Delete(tempDownloaded); } catch { }
            DatabaseService.CleanupLocalBackupFiles();

            if (!replaced)
            {
                NotifySyncState(CloudSyncState.Error, "载入云端最新数据文件失败");
                return (false, "载入云端最新数据库失败。");
            }

            // 重新刷新本地数据库连接并排期所有提醒
            WindowsReminderScheduler.RescheduleAll();

            // 触发全局数据变更通知以驱动日历画布与各界面即刻重绘
            DatabaseService.NotifyDataChanged();

            var config = ConfigService.Load();
            config.LastSyncTime = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
            config.LastSyncedTimestamp = config.LastSyncTime;
            ConfigService.Save(config);

            string remoteModTime = remoteFile.ModifiedTimeDateTimeOffset?.ToOffset(TimeSpan.FromHours(8)).ToString("yyyy-MM-dd HH:mm:ss")
                ?? remoteFile.ModifiedTimeRaw
                ?? config.LastSyncTime;

            string successMsg = $"已成功从 Google Drive 恢复数据文件「{remoteFile.Name}」！\n🕒 云端时间: {remoteModTime}\n📦 大小: {sizeKb} KB\n📊 恢复数据: {restoredScheduleCount} 条日程，{restoredCourseCount} 门课程\n全量数据已完全对齐生效。";

            NotifySyncState(CloudSyncState.Success, "已与云端数据保持一致", config.LastSyncTime);

            return (true, successMsg);
        }
        catch (Exception ex)
        {
            bool isInvalidGrant = ex.Message.Contains("invalid_grant", StringComparison.OrdinalIgnoreCase) ||
                                  ex.InnerException?.Message.Contains("invalid_grant", StringComparison.OrdinalIgnoreCase) == true;
            if (isInvalidGrant)
            {
                ClearLocalCredentials();
                string errMsg = "Google 授权已过期或失效 (invalid_grant)。\n\n已自动清理过期授权缓存。请再次点击「从云端拉取恢复」或「上传完整备份」，系统将自动在浏览器中打开 Google 授权页面，登录后即可恢复正常同步。\n\n💡 永久免过期技巧：若你在 Google Cloud 控制台创建的凭据处于「测试 (Testing)」状态，Google 会限制 Refresh Token 有效期为 7 天。在 Google Cloud 控制台「OAuth 同意屏幕」中将发布状态改为「发布应用 (In Production)」，授权将永久有效无需每周重登。";
                NotifySyncState(CloudSyncState.Error, "Google 授权已过期，请重新登录");
                return (false, errMsg);
            }
            NotifySyncState(CloudSyncState.Error, $"云端下载异常: {ex.Message}");
            return (false, $"云端下载异常: {ex.Message}");
        }
        finally
        {
            _ = Task.Delay(5000).ContinueWith(_ => IsRestoring = false);
        }
    }

    /// <summary>
    /// 极简云端下载同步：直接调用从云端下载恢复唯一数据文件
    /// </summary>
    public static async Task<(bool success, string message)> PullLatestBackupIfNewerAsync(
        string clientId,
        string clientSecret,
        Action<string>? progressCallback = null,
        CancellationToken cancellationToken = default,
        bool force = false)
    {
        return await DownloadBackupFromDriveAsync(clientId, clientSecret, progressCallback, cancellationToken);
    }

    /// <summary>
    /// 解绑 Google Drive 本地缓存授权凭证
    /// </summary>
    public static void ClearLocalCredentials()
    {
        try
        {
            if (Directory.Exists(TokenFolder))
            {
                foreach (var file in Directory.GetFiles(TokenFolder))
                {
                    try { File.Delete(file); } catch { }
                }
                try { Directory.Delete(TokenFolder, true); } catch { }
            }
            var config = ConfigService.Load();
            config.IsGoogleDriveLinked = false;
            config.GoogleDriveRefreshToken = string.Empty;
            config.GoogleDriveFileId = string.Empty;
            ConfigService.Save(config);
        }
        catch { }
    }

    public const string ReleaseManifestName = "rmf_release_info.json";

    /// <summary>
    /// 从 Google Drive 检索版本清单并检查是否有可用更新
    /// </summary>
    public static async Task<(bool hasUpdate, ReleaseInfo? info, string message)> CheckForSoftwareUpdateAsync(
        string clientId,
        string clientSecret,
        CancellationToken cancellationToken = default)
    {
        try
        {
            using var service = await GetDriveServiceAsync(clientId, clientSecret, cancellationToken);

            var listReq = service.Files.List();
            listReq.Q = $"name = '{ReleaseManifestName}' and trashed = false";
            listReq.Spaces = "drive";
            listReq.OrderBy = "modifiedTime desc";
            listReq.PageSize = 5;
            listReq.Fields = "files(id, name, modifiedTime, size)";
            var searchRes = await listReq.ExecuteAsync(cancellationToken);

            var releaseFile = searchRes.Files?.FirstOrDefault();
            if (releaseFile == null)
            {
                var folderId = await EnsureBackupFolderAsync(service, cancellationToken);
                var fallbackReq = service.Files.List();
                fallbackReq.Q = $"'{folderId}' in parents and name = '{ReleaseManifestName}' and trashed = false";
                fallbackReq.Spaces = "drive";
                fallbackReq.OrderBy = "modifiedTime desc";
                fallbackReq.PageSize = 5;
                fallbackReq.Fields = "files(id, name, modifiedTime, size)";
                var fallbackRes = await fallbackReq.ExecuteAsync(cancellationToken);
                releaseFile = fallbackRes.Files?.FirstOrDefault();
            }

            if (releaseFile == null)
            {
                return (false, null, $"Google Drive 中暂无已发布的版本描述清单 ({ReleaseManifestName})。");
            }

            using var memoryStream = new MemoryStream();
            await service.Files.Get(releaseFile.Id).DownloadAsync(memoryStream, cancellationToken);
            memoryStream.Position = 0;

            var releaseInfo = await JsonSerializer.DeserializeAsync<ReleaseInfo>(
                memoryStream,
                new JsonSerializerOptions { PropertyNameCaseInsensitive = true },
                cancellationToken
            );

            if (releaseInfo == null)
            {
                return (false, null, "版本清单解析失败：内容为空。");
            }

            int localVersionCode = 1050806;
            string localVersionName = "1.5.8.6";

            bool hasUpdate = releaseInfo.VersionCode > localVersionCode ||
                (releaseInfo.VersionCode == localVersionCode && releaseInfo.VersionName != localVersionName);

            if (!hasUpdate)
            {
                return (false, releaseInfo, $"当前已是最新版本 (v{localVersionName})，无需更新。");
            }

            return (true, releaseInfo, $"发现新版本 v{releaseInfo.VersionName}！");
        }
        catch (Exception ex)
        {
            return (false, null, $"检查更新失败: {ex.Message}");
        }
    }

    /// <summary>
    /// 从 Google Drive 下载指定的发布资产文件 (例如 Windows 安装包或压缩包)
    /// </summary>
    public static async Task<(bool success, string message)> DownloadReleaseFileAsync(
        string clientId,
        string clientSecret,
        string fileId,
        string targetFilePath,
        Action<long, long>? progressCallback = null,
        CancellationToken cancellationToken = default)
    {
        try
        {
            using var service = await GetDriveServiceAsync(clientId, clientSecret, cancellationToken);

            var metaReq = service.Files.Get(fileId);
            metaReq.Fields = "id, name, size";
            var meta = await metaReq.ExecuteAsync(cancellationToken);
            long totalBytes = meta.Size ?? 0L;

            string tempFile = targetFilePath + ".download.tmp";
            if (File.Exists(tempFile)) File.Delete(tempFile);

            var dir = Path.GetDirectoryName(targetFilePath);
            if (!string.IsNullOrEmpty(dir) && !Directory.Exists(dir))
            {
                Directory.CreateDirectory(dir);
            }

            using (var fileStream = new FileStream(tempFile, FileMode.Create, FileAccess.Write, FileShare.None))
            {
                var getReq = service.Files.Get(fileId);
                getReq.MediaDownloader.ProgressChanged += progress =>
                {
                    progressCallback?.Invoke(progress.BytesDownloaded, totalBytes);
                };
                await getReq.DownloadAsync(fileStream, cancellationToken);
            }

            if (File.Exists(targetFilePath)) File.Delete(targetFilePath);
            File.Move(tempFile, targetFilePath);

            return (true, $"文件已成功下载至: {targetFilePath}");
        }
        catch (Exception ex)
        {
            return (false, $"下载失败: {ex.Message}");
        }
    }

    /// <summary>
    /// 将新版本各平台构建及清单一键发布至 Google Drive (专属 RMF_CloudSync 目录)
    /// </summary>
    public static async Task<(bool success, string message)> PublishReleaseToDriveAsync(
        string clientId,
        string clientSecret,
        string versionName,
        int versionCode,
        string changelog,
        string? phoneApkPath = null,
        string? padApkPath = null,
        string? windowsInstallerPath = null,
        string? windowsZipPath = null,
        Action<string>? progressCallback = null,
        CancellationToken cancellationToken = default)
    {
        try
        {
            progressCallback?.Invoke("正在连接 Google Drive API 并确保同步目录就绪...");
            using var service = await GetDriveServiceAsync(clientId, clientSecret, cancellationToken);
            var folderId = await EnsureBackupFolderAsync(service, cancellationToken);

            var releaseInfo = new ReleaseInfo
            {
                VersionCode = versionCode,
                VersionName = versionName,
                ReleaseDate = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss"),
                Changelog = changelog
            };

            // 1. 上传 Android 手机版 APK
            if (!string.IsNullOrWhiteSpace(phoneApkPath) && File.Exists(phoneApkPath))
            {
                string fileName = Path.GetFileName(phoneApkPath);
                progressCallback?.Invoke($"正在上传 Android 手机版安装包 ({fileName})...");
                var fileId = await UploadFileToFolderAsync(service, folderId, phoneApkPath, "application/vnd.android.package-archive", cancellationToken);
                releaseInfo.PhoneApkFileId = fileId;
                releaseInfo.PhoneApkName = fileName;
                releaseInfo.PhoneApkSize = new FileInfo(phoneApkPath).Length;
            }

            // 2. 上传 Android 平板版 APK
            if (!string.IsNullOrWhiteSpace(padApkPath) && File.Exists(padApkPath))
            {
                string fileName = Path.GetFileName(padApkPath);
                progressCallback?.Invoke($"正在上传 Android 平板版安装包 ({fileName})...");
                var fileId = await UploadFileToFolderAsync(service, folderId, padApkPath, "application/vnd.android.package-archive", cancellationToken);
                releaseInfo.PadApkFileId = fileId;
                releaseInfo.PadApkName = fileName;
                releaseInfo.PadApkSize = new FileInfo(padApkPath).Length;
            }

            // 3. 上传 Windows 安装包
            if (!string.IsNullOrWhiteSpace(windowsInstallerPath) && File.Exists(windowsInstallerPath))
            {
                string fileName = Path.GetFileName(windowsInstallerPath);
                progressCallback?.Invoke($"正在上传 Windows 安装程序 ({fileName})...");
                var fileId = await UploadFileToFolderAsync(service, folderId, windowsInstallerPath, "application/octet-stream", cancellationToken);
                releaseInfo.WindowsInstallerFileId = fileId;
                releaseInfo.WindowsInstallerName = fileName;
                releaseInfo.WindowsInstallerSize = new FileInfo(windowsInstallerPath).Length;
            }

            // 4. 上传 Windows 便携压缩包
            if (!string.IsNullOrWhiteSpace(windowsZipPath) && File.Exists(windowsZipPath))
            {
                string fileName = Path.GetFileName(windowsZipPath);
                progressCallback?.Invoke($"正在上传 Windows 绿色压缩包 ({fileName})...");
                var fileId = await UploadFileToFolderAsync(service, folderId, windowsZipPath, "application/zip", cancellationToken);
                releaseInfo.WindowsZipFileId = fileId;
                releaseInfo.WindowsZipName = fileName;
                releaseInfo.WindowsZipSize = new FileInfo(windowsZipPath).Length;
            }

            // 5. 生成并上传 rmf_release_info.json
            progressCallback?.Invoke("正在更新云端版本清单 (rmf_release_info.json)...");
            var jsonString = JsonSerializer.Serialize(releaseInfo, new JsonSerializerOptions { WriteIndented = true });
            byte[] jsonBytes = System.Text.Encoding.UTF8.GetBytes(jsonString);

            // 检查云端是否已存在旧的清单
            var checkReq = service.Files.List();
            checkReq.Q = $"name = '{ReleaseManifestName}' and trashed = false";
            checkReq.Spaces = "drive";
            checkReq.Fields = "files(id, name)";
            var checkRes = await checkReq.ExecuteAsync(cancellationToken);
            var existingManifest = checkRes.Files?.FirstOrDefault();

            using var jsonStream = new MemoryStream(jsonBytes);
            if (existingManifest != null)
            {
                var updateMeta = new Google.Apis.Drive.v3.Data.File
                {
                    Name = ReleaseManifestName,
                    Description = $"RMF Release Info v{versionName}"
                };
                var updateReq = service.Files.Update(updateMeta, existingManifest.Id, jsonStream, "application/json");
                await updateReq.UploadAsync(cancellationToken);
            }
            else
            {
                var createMeta = new Google.Apis.Drive.v3.Data.File
                {
                    Name = ReleaseManifestName,
                    Parents = new List<string> { folderId },
                    Description = $"RMF Release Info v{versionName}"
                };
                var createReq = service.Files.Create(createMeta, jsonStream, "application/json");
                await createReq.UploadAsync(cancellationToken);
            }

            string successSummary = $"版本 v{versionName} 已成功发布至 Google Drive 云端更新通道！\n" +
                $"- 手机版 APK: {(string.IsNullOrEmpty(releaseInfo.PhoneApkFileId) ? "未包含" : releaseInfo.PhoneApkName)}\n" +
                $"- 平板版 APK: {(string.IsNullOrEmpty(releaseInfo.PadApkFileId) ? "未包含" : releaseInfo.PadApkName)}\n" +
                $"- Windows 安装包: {(string.IsNullOrEmpty(releaseInfo.WindowsInstallerFileId) ? "未包含" : releaseInfo.WindowsInstallerName)}\n" +
                $"- Windows 压缩包: {(string.IsNullOrEmpty(releaseInfo.WindowsZipFileId) ? "未包含" : releaseInfo.WindowsZipName)}";

            return (true, successSummary);
        }
        catch (Exception ex)
        {
            return (false, $"发布至 Google Drive 失败: {ex.Message}");
        }
    }

    private static async Task<string> UploadFileToFolderAsync(
        DriveService service,
        string folderId,
        string filePath,
        string mimeType,
        CancellationToken cancellationToken)
    {
        string fileName = Path.GetFileName(filePath);

        // 检查该文件夹下是否已存在同名文件，若存在则直接覆盖更新
        var listReq = service.Files.List();
        listReq.Q = $"'{folderId}' in parents and name = '{fileName}' and trashed = false";
        listReq.Spaces = "drive";
        listReq.Fields = "files(id, name)";
        var listRes = await listReq.ExecuteAsync(cancellationToken);
        var existing = listRes.Files?.FirstOrDefault();

        using var stream = new FileStream(filePath, FileMode.Open, FileAccess.Read, FileShare.Read);

        if (existing != null)
        {
            var updateMeta = new Google.Apis.Drive.v3.Data.File
            {
                Name = fileName
            };
            var updateReq = service.Files.Update(updateMeta, existing.Id, stream, mimeType);
            updateReq.Fields = "id";
            var result = await updateReq.UploadAsync(cancellationToken);
            if (result.Status == Google.Apis.Upload.UploadStatus.Failed)
            {
                throw result.Exception ?? new Exception($"覆盖上传文件 {fileName} 失败");
            }
            return existing.Id;
        }
        else
        {
            var createMeta = new Google.Apis.Drive.v3.Data.File
            {
                Name = fileName,
                Parents = new List<string> { folderId }
            };
            var createReq = service.Files.Create(createMeta, stream, mimeType);
            createReq.Fields = "id";
            var result = await createReq.UploadAsync(cancellationToken);
            if (result.Status == Google.Apis.Upload.UploadStatus.Failed)
            {
                throw result.Exception ?? new Exception($"创建上传文件 {fileName} 失败");
            }
            return createReq.ResponseBody?.Id ?? string.Empty;
        }
    }
}
