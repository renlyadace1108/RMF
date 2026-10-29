using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using RMF.Windows.Models;

namespace RMF.Windows.Services;

/// <summary>
/// 局域网 Wi-Fi 直连极速双向同步引擎 (LAN Direct Sync)
/// 1. 采用零配置轻量原生 TcpListener HTTP 服务，完全免提权，免注册 URL，零网络节点/代理依赖。
/// 2. 支持 UDP 广播就绪信标 (Beacon)，移动端打开界面瞬间自动感知并配对。
/// 3. 无缝衔接行级时间戳双向增量合并 (Bi-directional Merge)，同 Wi-Fi / 热点下 0.1 秒极速收敛。
/// </summary>
public static class LanSyncService
{
    public const int DefaultPort = 8765;
    public const int BeaconPort = 8766;

    private static TcpListener? _listener;
    private static CancellationTokenSource? _serverCts;
    private static readonly object _lock = new();

    public static bool IsRunning { get; private set; }
    public static int Port { get; private set; } = DefaultPort;
    public static string LocalIp { get; private set; } = "127.0.0.1";
    public static volatile bool IsBeaconBroadcastingEnabled = true;
    private static volatile int _burstPacketsRemaining = 3;

    /// <summary>
    /// 触发一次快速发现突发广播 (当窗口还原或用户进入局域网配对时调用，以 1 秒间隔发送 3 次快速信标)
    /// </summary>
    public static void TriggerDiscoveryBurst()
    {
        _burstPacketsRemaining = 3;
    }

    public static event Action<string>? OnLog;
    public static event Action<bool>? OnServerStateChanged;

    /// <summary>
    /// 获取当前电脑在局域网 Wi-Fi / 有线连接中的最优本地 IPv4 地址
    /// </summary>
    public static string GetPreferredLocalIp()
    {
        try
        {
            var interfaces = NetworkInterface.GetAllNetworkInterfaces()
                .Where(n => n.OperationalStatus == OperationalStatus.Up &&
                            n.NetworkInterfaceType != NetworkInterfaceType.Loopback)
                .OrderByDescending(n => n.NetworkInterfaceType == NetworkInterfaceType.Wireless80211 ? 2 :
                                        n.NetworkInterfaceType == NetworkInterfaceType.Ethernet ? 1 : 0);

            foreach (var ni in interfaces)
            {
                string desc = ni.Description.ToLowerInvariant();
                string name = ni.Name.ToLowerInvariant();
                if (desc.Contains("virtual") || desc.Contains("hyper-v") || desc.Contains("vmware") ||
                    desc.Contains("tailscale") || desc.Contains("zerotier") || desc.Contains("docker") ||
                    name.Contains("vethernet") || desc.Contains("wsl"))
                {
                    continue;
                }

                var ipProps = ni.GetIPProperties();
                foreach (var addr in ipProps.UnicastAddresses)
                {
                    if (addr.Address.AddressFamily == AddressFamily.InterNetwork)
                    {
                        string ip = addr.Address.ToString();
                        if (ip.StartsWith("192.168.") || ip.StartsWith("10.") || ip.StartsWith("172."))
                        {
                            return ip;
                        }
                    }
                }
            }
        }
        catch { }
        return "127.0.0.1";
    }

    /// <summary>
    /// 启动局域网直连同步微服务与 UDP 探测信标
    /// </summary>
    public static void StartServer(int port = DefaultPort)
    {
        lock (_lock)
        {
            if (IsRunning) return;

            try
            {
                Port = port;
                LocalIp = GetPreferredLocalIp();
                _serverCts = new CancellationTokenSource();
                var token = _serverCts.Token;

                _listener = new TcpListener(IPAddress.Any, Port);
                _listener.Start();
                IsRunning = true;
                OnServerStateChanged?.Invoke(true);
                OnLog?.Invoke($"📡 局域网同步服务已在端口 {Port} 启动，本机 IP: {LocalIp}");

                // 启动 TCP 客户端监听循环
                Task.Run(async () =>
                {
                    while (!token.IsCancellationRequested)
                    {
                        try
                        {
                            var client = await _listener.AcceptTcpClientAsync(token);
                            _ = Task.Run(() => HandleClientAsync(client, token), token);
                        }
                        catch (OperationCanceledException) { break; }
                        catch (Exception ex)
                        {
                            if (token.IsCancellationRequested) break;
                            System.Diagnostics.Debug.WriteLine($"[LanSync] Accept error: {ex.Message}");
                        }
                    }
                }, token);

                // 启动 UDP Beacon 广播，让局域网内移动端无需输 IP 自动秒级发现
                StartBeaconBroadcaster(token);
            }
            catch (Exception ex)
            {
                IsRunning = false;
                OnServerStateChanged?.Invoke(false);
                OnLog?.Invoke($"启动局域网同步服务异常: {ex.Message}");
            }
        }
    }

    /// <summary>
    /// 停止局域网同步微服务
    /// </summary>
    public static void StopServer()
    {
        lock (_lock)
        {
            if (!IsRunning) return;

            try { _serverCts?.Cancel(); } catch { }
            try { _listener?.Stop(); } catch { }
            _listener = null;
            IsRunning = false;
            OnServerStateChanged?.Invoke(false);
            OnLog?.Invoke("局域网同步服务已停止。");
        }
    }

    private static void StartBeaconBroadcaster(CancellationToken token)
    {
        Task.Run(async () =>
        {
            using var udp = new UdpClient();
            udp.EnableBroadcast = true;
            var endpoint = new IPEndPoint(IPAddress.Broadcast, BeaconPort);
            string beaconMsg = $"RMF_BEACON|{Environment.MachineName}|{Port}";
            byte[] bytes = Encoding.UTF8.GetBytes(beaconMsg);

            while (!token.IsCancellationRequested)
            {
                bool isBurst = _burstPacketsRemaining > 0;
                if (isBurst || IsBeaconBroadcastingEnabled)
                {
                    try
                    {
                        await udp.SendAsync(bytes, bytes.Length, endpoint);
                    }
                    catch { }

                    if (isBurst)
                    {
                        _burstPacketsRemaining--;
                    }
                }

                try
                {
                    // 深度功耗优化：突发阶段 1 秒快速发包；前台常态 10 秒低功耗发包 (让 Wi-Fi 芯片进入 802.11 PSM 低功耗休眠)；后台/托盘状态 30 秒休眠
                    int delayMs = isBurst ? 1000 : (IsBeaconBroadcastingEnabled ? 10000 : 30000);
                    await Task.Delay(delayMs, token);
                }
                catch (OperationCanceledException) { break; }
            }
        }, token);
    }

    private static async Task HandleClientAsync(TcpClient client, CancellationToken token)
    {
        using (client)
        using (var stream = client.GetStream())
        {
            try
            {
                stream.ReadTimeout = 10000;
                stream.WriteTimeout = 10000;

                var reader = new StreamReader(stream, Encoding.UTF8, leaveOpen: true);
                string? requestLine = await reader.ReadLineAsync();
                if (string.IsNullOrEmpty(requestLine)) return;

                var parts = requestLine.Split(' ');
                if (parts.Length < 2) return;
                string method = parts[0].ToUpperInvariant();
                string path = parts[1];

                var headers = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
                string? headerLine;
                while (!string.IsNullOrEmpty(headerLine = await reader.ReadLineAsync()))
                {
                    int colonIdx = headerLine.IndexOf(':');
                    if (colonIdx > 0)
                    {
                        string hName = headerLine.Substring(0, colonIdx).Trim();
                        string hVal = headerLine.Substring(colonIdx + 1).Trim();
                        headers[hName] = hVal;
                    }
                }

                // 1. 心跳探活接口
                if (method == "GET" && path.StartsWith("/api/ping", StringComparison.OrdinalIgnoreCase))
                {
                    string json = $"{{\"status\":\"ok\",\"app\":\"RMF\",\"device\":\"{Environment.MachineName}\",\"time\":\"{DateTime.Now:yyyy-MM-dd HH:mm:ss}\"}}";
                    byte[] body = Encoding.UTF8.GetBytes(json);
                    byte[] head = Encoding.UTF8.GetBytes($"HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: {body.Length}\r\nConnection: close\r\n\r\n");
                    await stream.WriteAsync(head, 0, head.Length, token);
                    await stream.WriteAsync(body, 0, body.Length, token);
                    await stream.FlushAsync(token);
                    return;
                }

                // 2. 导出原子只读快照供对端拉取
                if (method == "GET" && path.StartsWith("/api/pull_snapshot", StringComparison.OrdinalIgnoreCase))
                {
                    string snapshotPath = DatabaseService.CreateSafeDatabaseSnapshot();
                    try
                    {
                        var fileInfo = new FileInfo(snapshotPath);
                        long length = fileInfo.Length;
                        byte[] head = Encoding.UTF8.GetBytes($"HTTP/1.1 200 OK\r\nContent-Type: application/x-sqlite3\r\nContent-Length: {length}\r\nConnection: close\r\n\r\n");
                        await stream.WriteAsync(head, 0, head.Length, token);
                        using (var fs = new FileStream(snapshotPath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete))
                        {
                            await fs.CopyToAsync(stream, token);
                        }
                        await stream.FlushAsync(token);
                    }
                    finally
                    {
                        Microsoft.Data.Sqlite.SqliteConnection.ClearAllPools();
                        if (File.Exists(snapshotPath))
                        {
                            try { File.Delete(snapshotPath); } catch { }
                        }
                    }
                    return;
                }

                // 3. 接收对端数据快照并执行行级双向增量合并
                if (method == "POST" && path.StartsWith("/api/push_and_merge", StringComparison.OrdinalIgnoreCase))
                {
                    long contentLength = 0;
                    if (headers.TryGetValue("Content-Length", out var lenStr))
                    {
                        long.TryParse(lenStr, out contentLength);
                    }

                    string tempFile = Path.Combine(DatabaseService.DbDir, $"rmf_lan_received_{DateTime.Now:yyyyMMddHHmmssfff}.db");
                    using (var fs = new FileStream(tempFile, FileMode.Create, FileAccess.Write))
                    {
                        if (contentLength > 0)
                        {
                            byte[] buffer = new byte[81920];
                            long remaining = contentLength;
                            while (remaining > 0)
                            {
                                int toRead = (int)Math.Min(buffer.Length, remaining);
                                int read = await stream.ReadAsync(buffer, 0, toRead, token);
                                if (read <= 0) break;
                                await fs.WriteAsync(buffer, 0, read, token);
                                remaining -= read;
                            }
                        }
                        else
                        {
                            await stream.CopyToAsync(fs, token);
                        }
                    }

                    try
                    {
                        bool hasLocalUnpushed = GoogleDriveSyncService.PerformBiDirectionalMerge(tempFile);
                        DatabaseService.CleanupLocalBackupFiles();
                        DatabaseService.Initialize();
                        WindowsReminderScheduler.RescheduleAll();
                        DatabaseService.NotifyDataChanged();

                        var config = ConfigService.Load();
                        config.LastSyncTime = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
                        ConfigService.Save(config);

                        GoogleDriveSyncService.NotifySyncState(CloudSyncState.Success, "局域网直连同步完成", config.LastSyncTime);

                        string json = $"{{\"status\":\"ok\",\"message\":\"局域网双向增量合并成功\",\"hasLocalUnpushed\":{(hasLocalUnpushed ? "true" : "false")}}}";
                        byte[] body = Encoding.UTF8.GetBytes(json);
                        byte[] head = Encoding.UTF8.GetBytes($"HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: {body.Length}\r\nConnection: close\r\n\r\n");
                        await stream.WriteAsync(head, 0, head.Length, token);
                        await stream.WriteAsync(body, 0, body.Length, token);
                        await stream.FlushAsync(token);
                    }
                    finally
                    {
                        if (File.Exists(tempFile))
                        {
                            try { File.Delete(tempFile); } catch { }
                        }
                    }
                    return;
                }

                // 默认 404
                byte[] notFound = Encoding.UTF8.GetBytes("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n");
                await stream.WriteAsync(notFound, 0, notFound.Length, token);
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[LanSync] Handle client error: {ex.Message}");
            }
        }
    }

    /// <summary>
    /// 主动向指定的局域网对端 (如移动端 IP) 发起双向合并同步
    /// </summary>
    public static async Task<(bool success, string message)> SyncWithTargetAsync(string targetIp, int targetPort = DefaultPort, Action<string>? progressCallback = null)
    {
        try
        {
            using var client = new System.Net.Http.HttpClient { Timeout = TimeSpan.FromSeconds(6) };
            progressCallback?.Invoke("正在测试局域网握手探活...");
            var pingRes = await client.GetAsync($"http://{targetIp}:{targetPort}/api/ping");
            if (!pingRes.IsSuccessStatusCode)
            {
                return (false, $"连接对端失败: HTTP {(int)pingRes.StatusCode}");
            }

            progressCallback?.Invoke("正在从对端拉取数据快照...");
            string tempDownloaded = Path.Combine(DatabaseService.DbDir, $"rmf_lan_received_{DateTime.Now:yyyyMMddHHmmssfff}.db");
            using (var getStream = await client.GetStreamAsync($"http://{targetIp}:{targetPort}/api/pull_snapshot"))
            using (var fs = new FileStream(tempDownloaded, FileMode.Create, FileAccess.Write))
            {
                await getStream.CopyToAsync(fs);
            }

            progressCallback?.Invoke("正在执行行级时间戳双向增量合并...");
            bool hasLocalUnpushed = GoogleDriveSyncService.PerformBiDirectionalMerge(tempDownloaded);
            try { File.Delete(tempDownloaded); } catch { }
            DatabaseService.CleanupLocalBackupFiles();
            DatabaseService.Initialize();
            WindowsReminderScheduler.RescheduleAll();
            DatabaseService.NotifyDataChanged();

            if (hasLocalUnpushed)
            {
                progressCallback?.Invoke("检测到本地包含较新记录，正在反向推送至对端...");
                string localSnap = DatabaseService.CreateSafeDatabaseSnapshot();
                try
                {
                    using var fileStream = new FileStream(localSnap, FileMode.Open, FileAccess.Read);
                    using var content = new System.Net.Http.StreamContent(fileStream);
                    content.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("application/x-sqlite3");
                    var postRes = await client.PostAsync($"http://{targetIp}:{targetPort}/api/push_and_merge", content);
                    if (!postRes.IsSuccessStatusCode)
                    {
                        return (false, "反向推送合并快照失败。");
                    }
                }
                finally
                {
                    Microsoft.Data.Sqlite.SqliteConnection.ClearAllPools();
                    if (File.Exists(localSnap)) { try { File.Delete(localSnap); } catch { } }
                }
            }

            var config = ConfigService.Load();
            config.LastSyncTime = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
            ConfigService.Save(config);
            GoogleDriveSyncService.NotifySyncState(CloudSyncState.Success, "局域网直连同步完成", config.LastSyncTime);

            return (true, "已成功完成局域网极速双向合并同步！");
        }
        catch (Exception ex)
        {
            return (false, $"局域网同步异常: {ex.Message}");
        }
    }
}
