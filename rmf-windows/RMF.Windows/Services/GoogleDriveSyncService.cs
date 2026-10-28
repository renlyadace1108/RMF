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

    private static CancellationTokenSource? _autoSyncCts;
    private static readonly object _autoSyncLock = new();
    private static readonly SemaphoreSlim _uploadSemaphore = new(1, 1);
    private static volatile bool _hasPendingAutoSync = false;
    public static bool IsAutoSyncing { get; private set; }

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
    /// 请求触发一次防抖自动云端同步（每操作一次自动在专属文件夹保存时间戳命名版本）
    /// </summary>
    public static void RequestAutoSync(Action<string>? statusCallback = null)
    {
        var config = ConfigService.Load();
        if (!IsAuthorized(config))
        {
            return;
        }

        lock (_autoSyncLock)
        {
            _hasPendingAutoSync = true;
            try { _autoSyncCts?.Cancel(); } catch { }
            _autoSyncCts = new CancellationTokenSource();
            var token = _autoSyncCts.Token;

            Task.Run(async () =>
            {
                try
                {
                    await Task.Delay(3000, token); // 3秒防抖窗口
                    if (token.IsCancellationRequested) return;

                    await _uploadSemaphore.WaitAsync(token);
                    try
                    {
                        if (!_hasPendingAutoSync || token.IsCancellationRequested) return;
                        _hasPendingAutoSync = false;

                        IsAutoSyncing = true;
                        statusCallback?.Invoke("⏳ 正在自动同步新版本至云端专属目录...");
                        var (success, fileId, message) = await UploadBackupToDriveAsync(
                            config.GoogleClientId,
                            config.GoogleClientSecret,
                            s => statusCallback?.Invoke(s),
                            token
                        );

                        if (success)
                        {
                            statusCallback?.Invoke($"✓ 云端已自动保存新版本 ({DateTime.Now:HH:mm:ss})");
                        }
                    }
                    finally
                    {
                        IsAutoSyncing = false;
                        _uploadSemaphore.Release();
                    }
                }
                catch (OperationCanceledException) { }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"[AutoSync] Background upload error: {ex.Message}");
                }
            }, token);
        }
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
                // 2. 生成带时间戳的云端数据包文件名（每操作一次自动保存一个云端版本）
                string timestamp = DateTime.Now.ToString("yyyyMMdd_HHmmss");
                string newBackupFileName = $"rmf_backup_{timestamp}.db";
                progressCallback?.Invoke($"正在上传新时间戳版本 ({newBackupFileName})...");

                using var stream = new FileStream(snapshotPath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);

                var fileMetadata = new Google.Apis.Drive.v3.Data.File
                {
                    Name = newBackupFileName,
                    Parents = new List<string> { folderId },
                    Description = $"RMF Cloud Database Snapshot ({Environment.MachineName} - {DateTime.Now:yyyy-MM-dd HH:mm:ss})"
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
                ConfigService.Save(config);

                // 3. 云端历史版本维护：保持专属文件夹最新 50 个时间戳版本，修剪超额陈旧快照
                try
                {
                    var pruneReq = service.Files.List();
                    pruneReq.Q = $"'{folderId}' in parents and trashed = false and name contains 'rmf_backup_'";
                    pruneReq.Spaces = "drive";
                    pruneReq.PageSize = 1000;
                    pruneReq.Fields = "files(id, name, createdTime)";
                    pruneReq.OrderBy = "name asc";
                    var pruneRes = await pruneReq.ExecuteAsync(cancellationToken);
                    if (pruneRes.Files != null && pruneRes.Files.Count > MaxCloudRetentionVersions)
                    {
                        var sortedToPrune = pruneRes.Files.OrderBy(f => f.Name).ToList();
                        int toDelete = sortedToPrune.Count - MaxCloudRetentionVersions;
                        for (int i = 0; i < toDelete; i++)
                        {
                            try
                            {
                                await service.Files.Delete(sortedToPrune[i].Id).ExecuteAsync(cancellationToken);
                            }
                            catch { }
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

    public record MergeTableDef(string TableName, string PkCol, string? TsCol);

    private static readonly List<MergeTableDef> TablesToMerge = new()
    {
        new("goals", "id", "created_at"),
        new("study_topics", "id", "updated_at"),
        new("schedules", "id", "updated_at"),
        new("courses", "id", "updated_at"),
        new("course_adjustments", "id", "updated_at"),
        new("interruptions", "id", "timestamp"),
        new("expenses", "id", "created_at"),
        new("daily_reports", "date", "updated_at"),
        new("schedule_tags", "id", "created_at"),
        new("fitness_plans", "id", "updated_at"),
        new("fitness_records", "id", "updated_at"),
        new("habits", "id", "updated_at"),
        new("countdowns", "id", "updated_at"),
        new("reminders", "id", "updated_at"),
        new("notes", "id", "updated_at"),
        new("time_markers", "id", "updated_at"),
        new("focus_logs", "id", "timestamp"),
        new("timetable_settings", "key", null)
    };

    /// <summary>
    /// 核心行级时间戳双向增量合并引擎 (Bi-directional Merge Engine):
    /// 1. 采用 SQLite ATTACH DATABASE 机制，零全量暴力替换，零死锁无损对齐。
    /// 2. 云端新增记录无损合入本地 (INSERT OR IGNORE)。
    /// 3. 冲突记录基于更新时间戳 LWW (Last-Write-Wins) 判定，保留更新的一方。
    /// 4. 若本地包含离线新记录或更新的修改，标记 hasLocalUnpushed = true 并反向同步，实现全数据链双向自愈收敛！
    /// </summary>
    public static bool PerformBiDirectionalMerge(string tempDownloadedPath)
    {
        bool hasLocalUnpushed = false;
        string escapedPath = tempDownloadedPath.Replace("'", "''");

        using var conn = new Microsoft.Data.Sqlite.SqliteConnection(DatabaseService.ConnectionString);
        conn.Open();

        try
        {
            using var detachOld = new Microsoft.Data.Sqlite.SqliteCommand("DETACH DATABASE remote_db;", conn);
            detachOld.ExecuteNonQuery();
        }
        catch { }

        using (var attachCmd = new Microsoft.Data.Sqlite.SqliteCommand($"ATTACH DATABASE '{escapedPath}' AS remote_db;", conn))
        {
            attachCmd.ExecuteNonQuery();
        }

        try
        {
            using var tx = conn.BeginTransaction();
            try
            {
                using (var fkOff = new Microsoft.Data.Sqlite.SqliteCommand("PRAGMA foreign_keys = OFF;", conn, tx))
                {
                    fkOff.ExecuteNonQuery();
                }

                foreach (var tableDef in TablesToMerge)
                {
                    string table = tableDef.TableName;
                    string pk = tableDef.PkCol;
                    string? ts = tableDef.TsCol;

                    bool mainExists = false;
                    using (var chkMain = new Microsoft.Data.Sqlite.SqliteCommand("SELECT COUNT(*) FROM main.sqlite_master WHERE type='table' AND name=@t;", conn, tx))
                    {
                        chkMain.Parameters.AddWithValue("@t", table);
                        mainExists = Convert.ToInt64(chkMain.ExecuteScalar()) > 0;
                    }

                    bool remoteExists = false;
                    using (var chkRemote = new Microsoft.Data.Sqlite.SqliteCommand("SELECT COUNT(*) FROM remote_db.sqlite_master WHERE type='table' AND name=@t;", conn, tx))
                    {
                        chkRemote.Parameters.AddWithValue("@t", table);
                        remoteExists = Convert.ToInt64(chkRemote.ExecuteScalar()) > 0;
                    }

                    if (!mainExists || !remoteExists) continue;

                    var mainCols = new List<string>();
                    using (var pragmaMain = new Microsoft.Data.Sqlite.SqliteCommand($"PRAGMA main.table_info('{table}');", conn, tx))
                    using (var rdr = pragmaMain.ExecuteReader())
                    {
                        while (rdr.Read()) mainCols.Add(rdr.GetString(1));
                    }

                    var remoteCols = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
                    using (var pragmaRemote = new Microsoft.Data.Sqlite.SqliteCommand($"PRAGMA remote_db.table_info('{table}');", conn, tx))
                    using (var rdr = pragmaRemote.ExecuteReader())
                    {
                        while (rdr.Read()) remoteCols.Add(rdr.GetString(1));
                    }

                    var commonCols = mainCols.Where(c => remoteCols.Contains(c)).ToList();
                    string? actualPk = commonCols.FirstOrDefault(c => string.Equals(c, pk, StringComparison.OrdinalIgnoreCase));
                    if (actualPk == null) continue;

                    string? actualTs = ts != null ? commonCols.FirstOrDefault(c => string.Equals(c, ts, StringComparison.OrdinalIgnoreCase)) : null;
                    string colListSql = string.Join(", ", commonCols.Select(c => $"`{c}`"));

                    // 检测本地是否有远程不存在的孤立行
                    using (var chkLocalOnly = new Microsoft.Data.Sqlite.SqliteCommand(
                        $"SELECT COUNT(*) FROM main.`{table}` m WHERE NOT EXISTS (SELECT 1 FROM remote_db.`{table}` r WHERE r.`{actualPk}` = m.`{actualPk}`);", conn, tx))
                    {
                        long count = Convert.ToInt64(chkLocalOnly.ExecuteScalar());
                        if (count > 0) hasLocalUnpushed = true;
                    }

                    // 检测本地是否包含更新的修改
                    if (actualTs != null)
                    {
                        using var chkLocalNewer = new Microsoft.Data.Sqlite.SqliteCommand(
                            $"SELECT COUNT(*) FROM main.`{table}` m JOIN remote_db.`{table}` r ON m.`{actualPk}` = r.`{actualPk}` WHERE (m.`{actualTs}` > r.`{actualTs}` OR ((r.`{actualTs}` IS NULL OR r.`{actualTs}` = '') AND (m.`{actualTs}` IS NOT NULL AND m.`{actualTs}` != '')));", conn, tx);
                        long count = Convert.ToInt64(chkLocalNewer.ExecuteScalar());
                        if (count > 0) hasLocalUnpushed = true;
                    }

                    // 1. 将远程新增行插入本地 (INSERT OR IGNORE)
                    using (var insertCmd = new Microsoft.Data.Sqlite.SqliteCommand($@"
                        INSERT OR IGNORE INTO main.`{table}` ({colListSql})
                        SELECT {colListSql} FROM remote_db.`{table}` r
                        WHERE NOT EXISTS (
                            SELECT 1 FROM main.`{table}` m WHERE m.`{actualPk}` = r.`{actualPk}`
                        );", conn, tx))
                    {
                        insertCmd.ExecuteNonQuery();
                    }

                    // 2. 将远程较新行更新替换本地 (INSERT OR REPLACE)
                    if (actualTs != null)
                    {
                        using var updateCmd = new Microsoft.Data.Sqlite.SqliteCommand($@"
                            INSERT OR REPLACE INTO main.`{table}` ({colListSql})
                            SELECT {colListSql} FROM remote_db.`{table}` r
                            WHERE r.`{actualPk}` IN (
                                SELECT r2.`{actualPk}`
                                FROM remote_db.`{table}` r2
                                JOIN main.`{table}` m2 ON r2.`{actualPk}` = m2.`{actualPk}`
                                WHERE (r2.`{actualTs}` > m2.`{actualTs}` OR ((m2.`{actualTs}` IS NULL OR m2.`{actualTs}` = '') AND (r2.`{actualTs}` IS NOT NULL AND r2.`{actualTs}` != '')))
                            );", conn, tx);
                        updateCmd.ExecuteNonQuery();
                    }
                    else if (table == "timetable_settings")
                    {
                        using var updateSettingsCmd = new Microsoft.Data.Sqlite.SqliteCommand($@"
                            INSERT OR REPLACE INTO main.`{table}` ({colListSql})
                            SELECT {colListSql} FROM remote_db.`{table}`;", conn, tx);
                        updateSettingsCmd.ExecuteNonQuery();
                    }
                }

                // 更新本地快照物理版本计数器
                using (var metaCmd = new Microsoft.Data.Sqlite.SqliteCommand(@"
                    INSERT OR REPLACE INTO main.snapshot_metadata (id, device_id, version_counter, file_hash, updated_at)
                    VALUES ('current', @dev, COALESCE((SELECT version_counter + 1 FROM main.snapshot_metadata WHERE id='current'), 1), '', @now);", conn, tx))
                {
                    metaCmd.Parameters.AddWithValue("@dev", Environment.MachineName);
                    metaCmd.Parameters.AddWithValue("@now", DateTime.UtcNow.ToString("o"));
                    metaCmd.ExecuteNonQuery();
                }

                using (var fkOn = new Microsoft.Data.Sqlite.SqliteCommand("PRAGMA foreign_keys = ON;", conn, tx))
                {
                    fkOn.ExecuteNonQuery();
                }

                tx.Commit();
            }
            catch
            {
                tx.Rollback();
                throw;
            }
        }
        finally
        {
            try
            {
                using var detachCmd = new Microsoft.Data.Sqlite.SqliteCommand("DETACH DATABASE remote_db;", conn);
                detachCmd.ExecuteNonQuery();
            }
            catch { }
        }

        return hasLocalUnpushed;
    }

    /// <summary>
    /// 从 Google Drive 专属同步文件夹检索并拉取最新时间戳备份数据库，执行行级双向增量合并 (LWW)
    /// </summary>
    public static async Task<(bool success, string message)> DownloadBackupFromDriveAsync(string clientId, string clientSecret, Action<string>? progressCallback = null, CancellationToken cancellationToken = default)
    {
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
            var remoteFile = searchRes.Files
                .OrderByDescending(f => f.Name)
                .First();
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

            // 执行核心行级时间戳双向增量合并 (Bi-directional Merge)
            progressCallback?.Invoke("正在执行核心数据表行级时间戳双向增量合并...");
            bool hasLocalUnpushed = PerformBiDirectionalMerge(tempDownloaded);

            // 合并成功后，立即清理下载的临时文件，坚决保证“本地就一个版本”
            try { File.Delete(tempDownloaded); } catch { }
            DatabaseService.CleanupLocalBackupFiles();

            // 重新刷新本地数据库缓存与连接
            DatabaseService.Initialize();

            // 重新刷新并排期 Windows Action Center 所有待办提醒
            WindowsReminderScheduler.RescheduleAll();

            // 触发全局数据变更通知以驱动日历画布即刻重绘
            DatabaseService.NotifyDataChanged();

            var config = ConfigService.Load();
            config.LastSyncTime = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
            ConfigService.Save(config);

            string fileTimeDisplay = remoteFile.CreatedTimeDateTimeOffset?.LocalDateTime.ToString("yyyy-MM-dd HH:mm:ss")
                ?? remoteFile.ModifiedTimeDateTimeOffset?.LocalDateTime.ToString("yyyy-MM-dd HH:mm:ss")
                ?? DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");

            // 若本地包含离线未推送的较新变更，自动反向推送合并后的完整快照至云端
            if (hasLocalUnpushed)
            {
                progressCallback?.Invoke("检测到本地包含离线新变更，正在反向增量合并推送至云端...");
                RequestAutoSync();
            }

            NotifySyncState(CloudSyncState.Success, hasLocalUnpushed ? "双向合并完成 (已同步本地新变更)" : "已与云端最新快照保持一致", config.LastSyncTime);

            return (true, $"已成功从 Google Drive 完成双向增量合并「{remoteFile.Name}」！快照时间: {fileTimeDisplay}。全量数据已实时对齐生效。");
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
        try
        {
            var config = ConfigService.Load();
            if (!IsAuthorized(config))
            {
                NotifySyncState(CloudSyncState.Idle, "未连接");
                return (false, "Google Drive 未关联授权。");
            }

            NotifySyncState(CloudSyncState.Syncing, "正在检查云端快照更新...");
            using var service = await GetDriveServiceAsync(clientId, clientSecret, cancellationToken);
            string folderId = await EnsureBackupFolderAsync(service, cancellationToken);

            var listReq = service.Files.List();
            listReq.Q = $"'{folderId}' in parents and trashed = false and (name contains 'rmf_backup_' or name = '{BackupFileName}')";
            listReq.Spaces = "drive";
            listReq.Fields = "files(id, name, createdTime, modifiedTime, size)";
            listReq.OrderBy = "createdTime desc";

            var searchRes = await listReq.ExecuteAsync(cancellationToken);
            if (searchRes.Files == null || searchRes.Files.Count == 0)
            {
                NotifySyncState(CloudSyncState.Error, "云端专属目录未发现快照备份");
                return (false, "在 Google Drive 对应目录中未发现备份文件。");
            }

            var remoteFile = searchRes.Files.OrderByDescending(f => f.Name).First();
            string? remoteTimestamp = ExtractTimestampFromBackupName(remoteFile.Name);
            string lastSync = config.LastSyncTime ?? string.Empty;

            bool shouldPull = false;
            if (force)
            {
                shouldPull = true;
            }
            else if (!string.IsNullOrEmpty(remoteTimestamp) && !string.IsNullOrEmpty(lastSync) && !lastSync.Contains("未同步"))
            {
                shouldPull = string.Compare(remoteTimestamp, lastSync, StringComparison.Ordinal) > 0;
            }
            else if (string.IsNullOrEmpty(lastSync) || lastSync.Contains("未同步"))
            {
                shouldPull = true;
            }
            else
            {
                long remoteEpoch = remoteFile.ModifiedTimeDateTimeOffset?.ToUnixTimeMilliseconds() ?? 0L;
                long localEpoch = File.Exists(DatabaseService.DbPath) ? new DateTimeOffset(File.GetLastWriteTimeUtc(DatabaseService.DbPath)).ToUnixTimeMilliseconds() : 0L;
                shouldPull = remoteEpoch > localEpoch;
            }

            if (shouldPull)
            {
                return await DownloadBackupFromDriveAsync(clientId, clientSecret, progressCallback, cancellationToken);
            }
            NotifySyncState(CloudSyncState.Success, "数据已是最新", lastSync);
            return (false, "本地数据已与云端最新快照保持一致。");
        }
        catch (Exception ex)
        {
            NotifySyncState(CloudSyncState.Error, $"云端检查异常: {ex.Message}");
            return (false, $"冷启动云端拉取失败: {ex.Message}");
        }
    }

    private static string? ExtractTimestampFromBackupName(string name)
    {
        var match = System.Text.RegularExpressions.Regex.Match(name, @"rmf_backup_(\d{4})(\d{2})(\d{2})_(\d{2})(\d{2})(\d{2})\.db");
        if (match.Success)
        {
            var g = match.Groups;
            return $"{g[1].Value}-{g[2].Value}-{g[3].Value} {g[4].Value}:{g[5].Value}:{g[6].Value}";
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
