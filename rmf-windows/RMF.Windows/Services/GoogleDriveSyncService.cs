using System;
using System.Collections.Generic;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using Google.Apis.Auth.OAuth2;
using Google.Apis.Drive.v3;
using Google.Apis.Services;
using Google.Apis.Util.Store;
using RMF.Windows.Models;

namespace RMF.Windows.Services;

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
    private const string BackupFileName = "rmf_cloud_backup.db";
    private const string BackupFolderName = "RMF_CloudSync";
    private const int MaxCloudRetentionVersions = 50;

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
    /// 上传本地数据库至 Google Drive 进行云端同步
    /// </summary>
    public static async Task<(bool success, string fileId, string message)> UploadBackupToDriveAsync(string clientId, string clientSecret, Action<string>? progressCallback = null, CancellationToken cancellationToken = default)
    {
        try
        {
            progressCallback?.Invoke("正在连接 Google Drive API 并校验授权...");
            using var service = await GetDriveServiceAsync(clientId, clientSecret, cancellationToken);

            progressCallback?.Invoke("正在定位专属云端同步目录...");
            string folderId = await EnsureBackupFolderAsync(service, cancellationToken);

            // 0. 更新本地快照物理版本计数器
            var localMeta = DatabaseService.GetSnapshotMetadata();
            localMeta.VersionCounter++;
            localMeta.DeviceId = Environment.MachineName;
            localMeta.UpdatedAt = DateTime.UtcNow;
            DatabaseService.SaveSnapshotMetadata(localMeta);

            // 1. 生成一份原子只读数据库快照
            progressCallback?.Invoke("正在生成本地数据库原子只读快照...");
            string snapshotPath = DatabaseService.CreateSafeDatabaseSnapshot();

            try
            {
                // 2. 生成带 UTC+8 标准编程时间戳规范的专属快照文件名
                var beijingTime = DateTimeOffset.UtcNow.ToOffset(TimeSpan.FromHours(8));
                string timestamp = beijingTime.ToString("yyyyMMdd_HHmmss");
                string newBackupFileName = $"rmf_backup_{timestamp}_UTC+8.db";
                progressCallback?.Invoke($"正在上传 UTC+8 标准时间戳版本 ({newBackupFileName})...");

                using var stream = new FileStream(snapshotPath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);

                var fileMetadata = new Google.Apis.Drive.v3.Data.File
                {
                    Name = newBackupFileName,
                    Parents = new List<string> { folderId },
                    Description = $"RMF Cloud Database Snapshot ({Environment.MachineName} - {beijingTime:yyyy-MM-dd HH:mm:ss} UTC+8)"
                };

                var createReq = service.Files.Create(fileMetadata, stream, "application/x-sqlite3");
                createReq.Fields = "id, name, createdTime";
                var uploadProgress = await createReq.UploadAsync(cancellationToken);

                if (uploadProgress.Status != Google.Apis.Upload.UploadStatus.Completed)
                {
                    return (false, string.Empty, $"上传失败: {uploadProgress.Exception?.Message ?? uploadProgress.Status.ToString()}");
                }

                string newFileId = createReq.ResponseBody.Id;
                var config = ConfigService.Load();
                config.GoogleClientId = clientId.Trim();
                config.GoogleClientSecret = clientSecret.Trim();
                config.GoogleDriveFolderId = folderId;
                config.GoogleDriveFileId = newFileId;
                config.IsGoogleDriveLinked = true;
                config.LastSyncTime = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
                config.LastSyncedTimestamp = timestamp;
                ConfigService.Save(config);

                // 3. 云端历史版本维护：保持专属文件夹最新 50 个时间戳版本，修剪超额陈旧快照，并清理旧版 rmf_cloud_backup.db
                try
                {
                    var pruneReq = service.Files.List();
                    pruneReq.Q = $"'{folderId}' in parents and trashed = false and (name contains 'rmf_backup_' or name = '{BackupFileName}')";
                    pruneReq.Spaces = "drive";
                    pruneReq.PageSize = 1000;
                    pruneReq.Fields = "files(id, name, createdTime)";
                    pruneReq.OrderBy = "name asc";
                    var pruneRes = await pruneReq.ExecuteAsync(cancellationToken);
                    if (pruneRes.Files != null)
                    {
                        // 彻底清理历史遗留的旧版单一命名备份文件，避免任何设备排序出现冲突
                        var legacyFiles = pruneRes.Files.Where(f => f.Name == BackupFileName).ToList();
                        foreach (var leg in legacyFiles)
                        {
                            try { await service.Files.Delete(leg.Id).ExecuteAsync(cancellationToken); } catch { }
                        }

                        var tsFiles = pruneRes.Files
                            .Where(f => !string.IsNullOrEmpty(f.Name) && ExtractRawTimestamp(f.Name) != null)
                            .OrderBy(f => ExtractRawTimestamp(f.Name)!)
                            .ToList();

                        if (tsFiles.Count > MaxCloudRetentionVersions)
                        {
                            int toDelete = tsFiles.Count - MaxCloudRetentionVersions;
                            for (int i = 0; i < toDelete; i++)
                            {
                                try
                                {
                                    await service.Files.Delete(tsFiles[i].Id).ExecuteAsync(cancellationToken);
                                }
                                catch { }
                            }
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

                NotifySyncState(CloudSyncState.Success, "云端已保存新快照", config.LastSyncTime);
                return (true, newFileId, $"已成功保存云端时间戳版本「{newBackupFileName}」！时间: {config.LastSyncTime}");
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
    /// 从 Google Drive 专属同步文件夹检索并拉取最新时间戳备份数据库，安全替换本地数据库
    /// </summary>
    public static async Task<(bool success, string message)> DownloadBackupFromDriveAsync(string clientId, string clientSecret, Action<string>? progressCallback = null, CancellationToken cancellationToken = default)
    {
        IsRestoring = true;
        try
        {
            NotifySyncState(CloudSyncState.Syncing, "正在拉取云端快照...");
            progressCallback?.Invoke("正在连接 Google Drive API...");
            using var service = await GetDriveServiceAsync(clientId, clientSecret, cancellationToken);

            string folderId = await EnsureBackupFolderAsync(service, cancellationToken);

            progressCallback?.Invoke("正在检索云端专属目录最新镜像...");
            var listReq = service.Files.List();
            listReq.Q = $"'{folderId}' in parents and trashed = false and (name contains 'rmf_backup_' or name = '{BackupFileName}')";
            listReq.Spaces = "drive";
            listReq.Fields = "files(id, name, createdTime, modifiedTime, size)";
            listReq.OrderBy = "createdTime desc";

            var searchRes = await listReq.ExecuteAsync(cancellationToken);
            if (searchRes.Files == null || searchRes.Files.Count == 0)
            {
                NotifySyncState(CloudSyncState.Error, "未发现云端快照文件");
                return (false, "在 Google Drive 对应目录中未发现可还原的备份文件。");
            }

            // 选择最新的一份云端时间戳快照（名称按时间戳降序排序，确保准确度）
            var remoteFile = PickLatestBackupFile(searchRes.Files);
            if (remoteFile == null)
            {
                NotifySyncState(CloudSyncState.Error, "未发现云端有效快照文件");
                return (false, "在 Google Drive 对应目录中未发现可还原的备份文件。");
            }
            string tempDownloaded = Path.Combine(DatabaseService.DbDir, "rmf_downloaded_temp.db");

            progressCallback?.Invoke($"正在拉取最新云端快照 ({remoteFile.Name})...");
            var getReq = service.Files.Get(remoteFile.Id);

            using (var fileStream = new FileStream(tempDownloaded, FileMode.Create, FileAccess.Write))
            {
                await getReq.DownloadAsync(fileStream, cancellationToken);
            }

            // 验证拉取的数据库是否完整有效
            progressCallback?.Invoke("正在校验云端数据完整性...");
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
                        NotifySyncState(CloudSyncState.Error, "云端快照完整性校验未通过");
                        return (false, "云端下载的数据库完整性检验未通过，已取消合并。");
                    }
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

            // 核心：直接使用云端最新数据快照安全覆盖本地数据库（零并发冲突，谁新就用谁）
            progressCallback?.Invoke("云端快照时间戳更新，正在安全载入最新数据文件...");
            bool replaced = DatabaseService.ReplaceDatabaseFile(tempDownloaded);

            // 清理下载的临时文件，坚决保证“本地就一个版本”
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

            string? rawTimestamp = ExtractRawTimestamp(remoteFile.Name);
            var config = ConfigService.Load();
            config.LastSyncTime = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
            config.LastSyncedTimestamp = rawTimestamp ?? remoteFile.Name;
            ConfigService.Save(config);

            string fileTimeDisplay = remoteFile.CreatedTimeDateTimeOffset?.LocalDateTime.ToString("yyyy-MM-dd HH:mm:ss")
                ?? remoteFile.ModifiedTimeDateTimeOffset?.LocalDateTime.ToString("yyyy-MM-dd HH:mm:ss")
                ?? DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");

            NotifySyncState(CloudSyncState.Success, "已与云端最新快照保持一致", config.LastSyncTime);

            return (true, $"已成功从 Google Drive 同步最新版本「{remoteFile.Name}」！快照时间: {fileTimeDisplay}。全量数据已完全对齐生效。");
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
            NotifySyncState(CloudSyncState.Error, $"云端下载合并异常: {ex.Message}");
            return (false, $"云端下载合并异常: {ex.Message}");
        }
        finally
        {
            _ = Task.Delay(5000).ContinueWith(_ => IsRestoring = false);
        }
    }

    /// <summary>
    /// 静默/前台智能轻检测与拉取合并：
    /// 比较云端专属目录最新镜像时间戳与本地 LastSyncTime，若云端版本更新则自动静默拉取并执行行级双向增量合并。
    /// 支持 force 参数强制触发一次完整云端轻检测与合并。
    /// </summary>
    public static async Task<(bool success, string message)> PullLatestBackupIfNewerAsync(
        string clientId,
        string clientSecret,
        Action<string>? progressCallback = null,
        CancellationToken cancellationToken = default,
        bool force = false)
    {
        // 用户明确要求：“就只保留上传和下载两个，不要比对了”
        // 直接从云端下载最新时间戳快照并安全覆盖还原
        return await DownloadBackupFromDriveAsync(clientId, clientSecret, progressCallback, cancellationToken);
    }

    /// <summary>
    /// 从云端文件列表中精准甄选最新快照：
    /// 1. 严格以 rmf_backup_yyyyMMdd_HHmmss_UTC+8.db 格式提取实际时间戳降序对比。
    /// 2. 避免 ASCII 字母排序导致旧版 rmf_cloud_backup.db 误排在时间戳版本之前。
    /// 3. 若无时间戳快照，才向下兼容旧单文件。
    /// </summary>
    public static Google.Apis.Drive.v3.Data.File? PickLatestBackupFile(IList<Google.Apis.Drive.v3.Data.File>? files)
    {
        if (files == null || files.Count == 0) return null;

        var timestamped = files
            .Where(f => !string.IsNullOrEmpty(f.Name) && ExtractRawTimestamp(f.Name) != null)
            .OrderByDescending(f => ExtractRawTimestamp(f.Name)!)
            .ToList();

        if (timestamped.Count > 0)
        {
            return timestamped[0];
        }

        return files.FirstOrDefault(f => f.Name == BackupFileName);
    }

    private static string? ExtractRawTimestamp(string name)
    {
        var match = System.Text.RegularExpressions.Regex.Match(name, @"rmf_backup_(\d{8}_\d{6})(?:_UTC\+?8)?\.db");
        return match.Success ? match.Groups[1].Value : null;
    }

    private static string? ExtractTimestampFromBackupName(string name)
    {
        var match = System.Text.RegularExpressions.Regex.Match(name, @"rmf_backup_(\d{4})(\d{2})(\d{2})_(\d{2})(\d{2})(\d{2})(?:_UTC\+?8)?\.db");
        if (match.Success)
        {
            var g = match.Groups;
            return $"{g[1].Value}-{g[2].Value}-{g[3].Value} {g[4].Value}:{g[5].Value}:{g[6].Value} (UTC+8)";
        }
        return null;
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
}
