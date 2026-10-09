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

public class GoogleDriveSyncService
{
    private static readonly string[] Scopes = { DriveService.Scope.DriveFile };
    private const string ApplicationName = "Renly Management Platform";
    private const string BackupFileName = "rmf_cloud_backup.db";
    private const string BackupFolderName = "RMF_CloudSync";

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

        var credential = await GoogleWebAuthorizationBroker.AuthorizeAsync(
            clientSecrets,
            Scopes,
            "user",
            cancellationToken,
            new FileDataStore(TokenFolder, true)
        );

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
                // 2. 检查云端是否已存在历史同名备份文件
                progressCallback?.Invoke("正在检查云端现有版本...");
                var listReq = service.Files.List();
                listReq.Q = $"name = '{BackupFileName}' and '{folderId}' in parents and trashed = false";
                listReq.Spaces = "drive";
                listReq.Fields = "files(id, name, modifiedTime)";

                var searchRes = await listReq.ExecuteAsync(cancellationToken);
                string? existingFileId = (searchRes.Files != null && searchRes.Files.Count > 0) ? searchRes.Files[0].Id : null;

                using var stream = new FileStream(snapshotPath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);

                if (!string.IsNullOrEmpty(existingFileId))
                {
                    progressCallback?.Invoke("正在将增量数据流更新至云端现存文件...");
                    var updateMeta = new Google.Apis.Drive.v3.Data.File
                    {
                        Name = BackupFileName,
                        Description = $"RMF Local Database Snapshot ({DateTime.Now:yyyy-MM-dd HH:mm:ss})"
                    };

                    var updateReq = service.Files.Update(updateMeta, existingFileId, stream, "application/x-sqlite3");
                    updateReq.Fields = "id, modifiedTime";
                    var updatedFile = await updateReq.UploadAsync(cancellationToken);

                    if (updatedFile.Status == Google.Apis.Upload.UploadStatus.Completed)
                    {
                        var config = ConfigService.Load();
                        config.GoogleClientId = clientId.Trim();
                        config.GoogleClientSecret = clientSecret.Trim();
                        config.GoogleDriveFolderId = folderId;
                        config.GoogleDriveFileId = existingFileId;
                        config.IsGoogleDriveLinked = true;
                        config.LastSyncTime = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
                        ConfigService.Save(config);

                        return (true, existingFileId, $"已成功覆盖更新至 Google Drive！时间: {config.LastSyncTime}");
                    }
                    else
                    {
                        return (false, string.Empty, $"上传未完全完成: {updatedFile.Exception?.Message ?? updatedFile.Status.ToString()}");
                    }
                }
                else
                {
                    progressCallback?.Invoke("正在创建并上传云端数据库文件...");
                    var fileMetadata = new Google.Apis.Drive.v3.Data.File
                    {
                        Name = BackupFileName,
                        Parents = new List<string> { folderId },
                        Description = $"RMF Local Database Backup ({DateTime.Now:yyyy-MM-dd HH:mm:ss})"
                    };

                    var createReq = service.Files.Create(fileMetadata, stream, "application/x-sqlite3");
                    createReq.Fields = "id, modifiedTime";
                    var uploadProgress = await createReq.UploadAsync(cancellationToken);

                    if (uploadProgress.Status == Google.Apis.Upload.UploadStatus.Completed)
                    {
                        string newFileId = createReq.ResponseBody.Id;
                        var config = ConfigService.Load();
                        config.GoogleClientId = clientId.Trim();
                        config.GoogleClientSecret = clientSecret.Trim();
                        config.GoogleDriveFolderId = folderId;
                        config.GoogleDriveFileId = newFileId;
                        config.IsGoogleDriveLinked = true;
                        config.LastSyncTime = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
                        ConfigService.Save(config);

                        return (true, newFileId, $"已成功新建上传至 Google Drive 文件夹！时间: {config.LastSyncTime}");
                    }
                    else
                    {
                        return (false, string.Empty, $"上传失败: {uploadProgress.Exception?.Message ?? uploadProgress.Status.ToString()}");
                    }
                }
            }
            finally
            {
                // 确保释放连接池并清理临时本地只读副本
                Microsoft.Data.Sqlite.SqliteConnection.ClearAllPools();
                if (File.Exists(snapshotPath))
                {
                    try { File.Delete(snapshotPath); } catch { }
                }
            }
        }
        catch (Exception ex)
        {
            return (false, string.Empty, $"同步上传异常: {ex.Message}");
        }
    }

    /// <summary>
    /// 从 Google Drive 下载并恢复本地数据库
    /// </summary>
    public static async Task<(bool success, string message)> DownloadBackupFromDriveAsync(string clientId, string clientSecret, Action<string>? progressCallback = null, CancellationToken cancellationToken = default)
    {
        try
        {
            progressCallback?.Invoke("正在连接 Google Drive API...");
            using var service = await GetDriveServiceAsync(clientId, clientSecret, cancellationToken);

            string folderId = await EnsureBackupFolderAsync(service, cancellationToken);

            progressCallback?.Invoke("正在检索云端备份镜像...");
            var listReq = service.Files.List();
            listReq.Q = $"name = '{BackupFileName}' and '{folderId}' in parents and trashed = false";
            listReq.Spaces = "drive";
            listReq.Fields = "files(id, name, modifiedTime, size)";

            var searchRes = await listReq.ExecuteAsync(cancellationToken);
            if (searchRes.Files == null || searchRes.Files.Count == 0)
            {
                return (false, "在 Google Drive 对应目录中未发现可还原的备份文件。");
            }

            var remoteFile = searchRes.Files[0];
            string tempDownloaded = Path.Combine(DatabaseService.DbDir, "rmf_downloaded_temp.db");

            progressCallback?.Invoke($"正在拉取云端数据 ({remoteFile.Name})...");
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
                        return (false, "云端下载的数据库完整性检验未通过，已取消覆盖。");
                    }
                    verifyConn.Close();
                }
                Microsoft.Data.Sqlite.SqliteConnection.ClearAllPools();
            }
            catch (Exception ex)
            {
                return (false, $"云端数据库校验失败: {ex.Message}");
            }

            // 校验通过，提取远程快照元数据进行多端分叉仲裁检测
            SnapshotMetadata? remoteMeta = null;
            try
            {
                using var tempConn = new Microsoft.Data.Sqlite.SqliteConnection($"Data Source={tempDownloaded};Pooling=False");
                tempConn.Open();
                using var metaCmd = new Microsoft.Data.Sqlite.SqliteCommand("SELECT id, device_id, version_counter, file_hash, updated_at FROM snapshot_metadata WHERE id = 'current';", tempConn);
                using var metaReader = metaCmd.ExecuteReader();
                if (metaReader.Read())
                {
                    remoteMeta = new SnapshotMetadata
                    {
                        DeviceId = metaReader.IsDBNull(1) ? "" : metaReader.GetString(1),
                        VersionCounter = metaReader.GetInt64(2),
                        FileHash = metaReader.IsDBNull(3) ? "" : metaReader.GetString(3),
                        UpdatedAt = DateTime.TryParse(metaReader.GetString(4), out var dt) ? dt : DateTime.UtcNow
                    };
                }
                tempConn.Close();
                Microsoft.Data.Sqlite.SqliteConnection.ClearAllPools();
            }
            catch { }

            var localMeta = DatabaseService.GetSnapshotMetadata();

            // 检查是否存在非线性分叉冲突: 远程设备与本地设备不同，且本地有更新的未同步修改 (dirty)
            bool hasLocalDirty = false;
            try
            {
                using var chkConn = new Microsoft.Data.Sqlite.SqliteConnection(DatabaseService.ConnectionString);
                chkConn.Open();
                using var chkCmd = new Microsoft.Data.Sqlite.SqliteCommand("SELECT COUNT(*) FROM schedules WHERE is_dirty = 1;", chkConn);
                hasLocalDirty = Convert.ToInt32(chkCmd.ExecuteScalar()) > 0;
            }
            catch { }

            if (remoteMeta != null && !string.IsNullOrEmpty(remoteMeta.DeviceId) && remoteMeta.DeviceId != localMeta.DeviceId && hasLocalDirty)
            {
                // 触发冲突保护：禁止覆盖，保留冲突副本
                string conflictFileName = $"RMF_Conflict_{DateTime.Now:yyyyMMdd_HHmmss}.db";
                string conflictPath = Path.Combine(DatabaseService.DbDir, conflictFileName);
                File.Copy(tempDownloaded, conflictPath, true);
                try { File.Delete(tempDownloaded); } catch { }

                return (false, $"⚠️ 检测到多设备快照分叉冲突！远程修改来自设备 [{remoteMeta.DeviceId}]，而本地存在尚未同步的新变更。为保障本地数据安全，已自动留存冲突快照副本「{conflictFileName}」，未覆盖本地数据库！");
            }

            // 安全覆盖本地主数据库：先备份旧库为 .bak
            progressCallback?.Invoke("正在将云端镜像热应用到本地工作区...");
            string currentDb = DatabaseService.DbPath;
            string backupOld = Path.Combine(DatabaseService.DbDir, $"rmf_pre_restore_{DateTime.Now:yyyyMMddHHmmss}.bak");
            if (File.Exists(currentDb))
            {
                try { File.Copy(currentDb, backupOld, true); } catch { }
            }

            // 使用 SQLite Online Backup 机制安全热载入目标库，避免文件锁冲突
            using (var downloadConn = new Microsoft.Data.Sqlite.SqliteConnection($"Data Source={tempDownloaded};Pooling=False"))
            {
                downloadConn.Open();
                using (var targetConn = new Microsoft.Data.Sqlite.SqliteConnection(DatabaseService.ConnectionString))
                {
                    targetConn.Open();
                    downloadConn.BackupDatabase(targetConn);
                    targetConn.Close();
                }
                downloadConn.Close();
            }
            Microsoft.Data.Sqlite.SqliteConnection.ClearAllPools();

            try { File.Delete(tempDownloaded); } catch { }

            // 重新刷新本地数据库缓存与连接
            DatabaseService.Initialize();

            var config = ConfigService.Load();
            config.LastSyncTime = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
            ConfigService.Save(config);

            return (true, $"已成功从 Google Drive 云端恢复数据！更新时间: {remoteFile.ModifiedTimeDateTimeOffset?.LocalDateTime:yyyy-MM-dd HH:mm:ss}");
        }
        catch (Exception ex)
        {
            return (false, $"云端下载恢复异常: {ex.Message}");
        }
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
                Directory.Delete(TokenFolder, true);
            }
            var config = ConfigService.Load();
            config.IsGoogleDriveLinked = false;
            config.GoogleDriveFileId = string.Empty;
            ConfigService.Save(config);
        }
        catch { }
    }
}
