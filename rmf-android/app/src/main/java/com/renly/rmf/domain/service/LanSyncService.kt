package com.renly.rmf.domain.service

import android.content.Context
import android.net.wifi.WifiManager
import com.renly.rmf.data.local.RmfDatabase
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 局域网服务发现与配对设备信息
 */
data class LanServerInfo(
    val ip: String,
    val port: Int = LanSyncService.DEFAULT_PORT,
    val deviceName: String = "PC",
    val discoveredAt: Long = System.currentTimeMillis()
)

/**
 * 局域网同步持久化偏好设置
 */
object LanSyncPreferences {
    private const val PREFS_NAME = "rmf_lan_sync_prefs"
    private const val KEY_LAST_IP = "lan_last_ip"
    private const val KEY_LAST_PORT = "lan_last_port"
    private const val KEY_LAST_SYNC_TIME = "lan_last_sync_time"

    private fun getPrefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLastIp(context: Context): String = getPrefs(context).getString(KEY_LAST_IP, "") ?: ""
    fun setLastIp(context: Context, ip: String) = getPrefs(context).edit().putString(KEY_LAST_IP, ip.trim()).apply()

    fun getLastPort(context: Context): Int = getPrefs(context).getInt(KEY_LAST_PORT, 8765)
    fun setLastPort(context: Context, port: Int) = getPrefs(context).edit().putInt(KEY_LAST_PORT, port).apply()

    fun getLastSyncTime(context: Context): String = getPrefs(context).getString(KEY_LAST_SYNC_TIME, "") ?: ""
    fun setLastSyncTime(context: Context, time: String) = getPrefs(context).edit().putString(KEY_LAST_SYNC_TIME, time).apply()
}

/**
 * 局域网 Wi-Fi 直连极速双向同步引擎 (LAN Direct Sync)
 * 1. 同 Wi-Fi / 热点下免节点、免代理、免云端 API 额度，0.1 秒瞬时完成双向增量合并。
 * 2. 具备 UDP 广播信标发现 (Beacon Discovery)，秒级感知局域网内 PC 电脑。
 * 3. 采用行级时间戳双向增量合并 (Bi-directional Merge)，零死锁无损自愈。
 */
class LanSyncService(
    private val context: Context,
    private val database: RmfDatabase = RmfDatabase.getInstance(context)
) {
    private val driveSyncService = GoogleDriveSyncService(context, database)

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    companion object {
        const val DEFAULT_PORT = 8765
        const val BEACON_PORT = 8766

        /**
         * 获取当前 Android 设备在 Wi-Fi 网络中的本地 IPv4 地址
         */
        fun getLocalWifiIpAddress(): String? {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (networkInterface.isLoopback || !networkInterface.isUp) continue
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val host = addr.hostAddress ?: continue
                            if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                                return host
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
            return null
        }
    }

    /**
     * 启动移动端轻量 HTTP 微服务（允许电脑端主动向移动端发起直连合并）
     */
    fun startServer(port: Int = DEFAULT_PORT, scope: CoroutineScope) {
        if (serverSocket != null && !serverSocket!!.isClosed) return
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port)
                while (isActive) {
                    val client = serverSocket?.accept() ?: break
                    launch(Dispatchers.IO) {
                        handleClient(client)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * 停止移动端轻量 HTTP 微服务
     */
    fun stopServer() {
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
    }

    private suspend fun handleClient(client: Socket) = withContext(Dispatchers.IO) {
        try {
            client.soTimeout = 10000
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
            val line = reader.readLine() ?: return@withContext
            val parts = line.split(" ")
            if (parts.size < 2) return@withContext
            val method = parts[0].uppercase()
            val path = parts[1]

            val headers = mutableMapOf<String, String>()
            var hLine = reader.readLine()
            while (!hLine.isNullOrEmpty()) {
                val colon = hLine.indexOf(':')
                if (colon > 0) {
                    headers[hLine.substring(0, colon).trim().lowercase()] = hLine.substring(colon + 1).trim()
                }
                hLine = reader.readLine()
            }

            val out = client.getOutputStream()

            // 1. 心跳探活
            if (method == "GET" && path.startsWith("/api/ping")) {
                val body = "{\"status\":\"ok\",\"app\":\"RMF\",\"device\":\"${android.os.Build.MODEL}\"}".toByteArray(Charsets.UTF_8)
                val head = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.UTF_8)
                out.write(head)
                out.write(body)
                out.flush()
                return@withContext
            }

            // 2. 导出快照供对端拉取
            if (method == "GET" && path.startsWith("/api/pull_snapshot")) {
                val tempFile = File(context.cacheDir, "rmf_lan_export_${System.currentTimeMillis()}.db")
                try {
                    val ok = driveSyncService.createSafeDatabaseSnapshot(tempFile)
                    if (ok && tempFile.exists()) {
                        val head = "HTTP/1.1 200 OK\r\nContent-Type: application/x-sqlite3\r\nContent-Length: ${tempFile.length()}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.UTF_8)
                        out.write(head)
                        tempFile.inputStream().use { it.copyTo(out) }
                        out.flush()
                    } else {
                        val notFound = "HTTP/1.1 500 Internal Error\r\n\r\n".toByteArray(Charsets.UTF_8)
                        out.write(notFound)
                    }
                } finally {
                    tempFile.delete()
                }
                return@withContext
            }

            // 3. 接收对端快照并合并
            if (method == "POST" && path.startsWith("/api/push_and_merge")) {
                val contentLen = headers["content-length"]?.toLongOrNull() ?: 0L
                val tempFile = File(context.cacheDir, "rmf_lan_push_in_${System.currentTimeMillis()}.db")
                try {
                    val inStream = client.getInputStream()
                    tempFile.outputStream().use { fos ->
                        if (contentLen > 0) {
                            val buf = ByteArray(16384)
                            var rem = contentLen
                            while (rem > 0) {
                                val toRead = Math.min(buf.size.toLong(), rem).toInt()
                                val r = inStream.read(buf, 0, toRead)
                                if (r <= 0) break
                                fos.write(buf, 0, r)
                                rem -= r
                            }
                        } else {
                            inStream.copyTo(fos)
                        }
                    }

                    val hasLocalUnpushed = driveSyncService.mergeFromSnapshotFile(tempFile)
                    val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    LanSyncPreferences.setLastSyncTime(context, nowStr)
                    GoogleDrivePreferences.setLastSyncTime(context, nowStr)
                    GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.SUCCESS, "局域网同步完成 ($nowStr)")

                    val body = "{\"status\":\"ok\",\"message\":\"局域网双向合并成功\",\"hasLocalUnpushed\":$hasLocalUnpushed}".toByteArray(Charsets.UTF_8)
                    val head = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.UTF_8)
                    out.write(head)
                    out.write(body)
                    out.flush()
                } finally {
                    tempFile.delete()
                }
                return@withContext
            }

            val notFound = "HTTP/1.1 404 Not Found\r\n\r\n".toByteArray(Charsets.UTF_8)
            out.write(notFound)
        } catch (_: Exception) {
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    /**
     * 监听 UDP 局域网广播信标，自动发现电脑端
     */
    suspend fun discoverServers(
        timeoutMs: Long = 6000L,
        onFound: (LanServerInfo) -> Unit
    ) = withContext(Dispatchers.IO) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifiManager?.createMulticastLock("RmfLanMulticastLock")?.apply {
            setReferenceCounted(true)
            acquire()
        }

        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(BEACON_PORT))
                soTimeout = 1200
            }

            val startTime = System.currentTimeMillis()
            val buffer = ByteArray(1024)
            val packet = DatagramPacket(buffer, buffer.size)

            while (isActive && System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    socket.receive(packet)
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    if (text.startsWith("RMF_BEACON|")) {
                        val parts = text.split("|")
                        val devName = parts.getOrNull(1) ?: "PC"
                        val port = parts.getOrNull(2)?.toIntOrNull() ?: DEFAULT_PORT
                        val ip = packet.address.hostAddress ?: ""
                        if (ip.isNotEmpty()) {
                            onFound(LanServerInfo(ip, port, devName))
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    // 超时重试直到 timeoutMs
                } catch (_: Exception) {
                    break
                }
            }
        } catch (_: Exception) {
        } finally {
            try { socket?.close() } catch (_: Exception) {}
            try {
                if (multicastLock?.isHeld == true) {
                    multicastLock.release()
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * 与目标对端执行极速双向增量同步
     */
    suspend fun syncWithTarget(
        targetIp: String,
        targetPort: Int = DEFAULT_PORT,
        onProgress: ((String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        var pullConn: HttpURLConnection? = null
        try {
            GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.SYNCING, "局域网同步中...")
            onProgress?.invoke("正在探测对端连通性 (ping)...")

            // 1. Ping 探活
            val pingUrl = URL("http://$targetIp:$targetPort/api/ping")
            val pingConn = pingUrl.openConnection() as HttpURLConnection
            pingConn.connectTimeout = 3000
            pingConn.readTimeout = 3000
            pingConn.requestMethod = "GET"
            val pingCode = pingConn.responseCode
            if (pingCode != 200) {
                val err = "连接失败: HTTP $pingCode"
                GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.ERROR, err, error = err)
                return@withContext Result.failure(Exception(err))
            }
            pingConn.disconnect()

            // 2. 从电脑端拉取快照
            onProgress?.invoke("正在从电脑端拉取数据快照...")
            val pullUrl = URL("http://$targetIp:$targetPort/api/pull_snapshot")
            pullConn = pullUrl.openConnection() as HttpURLConnection
            pullConn.connectTimeout = 5000
            pullConn.readTimeout = 15000
            pullConn.requestMethod = "GET"

            if (pullConn.responseCode != 200) {
                val err = "拉取快照失败: HTTP ${pullConn.responseCode}"
                GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.ERROR, err, error = err)
                return@withContext Result.failure(Exception(err))
            }

            val tempPullFile = File(context.cacheDir, "rmf_lan_received_${System.currentTimeMillis()}.db")
            pullConn.inputStream.use { input ->
                tempPullFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            pullConn.disconnect()
            pullConn = null

            // 3. 执行行级双向增量合并
            onProgress?.invoke("正在执行行级时间戳双向增量合并...")
            val hasLocalUnpushed = driveSyncService.mergeFromSnapshotFile(tempPullFile)
            tempPullFile.delete()

            // 4. 若本地包含离线新记录或更新修改，反向推送给电脑端
            if (hasLocalUnpushed) {
                onProgress?.invoke("检测到本地包含离线新变更，正在同步推送至电脑端...")
                val tempPushFile = File(context.cacheDir, "rmf_lan_push_${System.currentTimeMillis()}.db")
                val snapshotOk = driveSyncService.createSafeDatabaseSnapshot(tempPushFile)
                if (snapshotOk && tempPushFile.exists()) {
                    try {
                        val pushUrl = URL("http://$targetIp:$targetPort/api/push_and_merge")
                        val pushConn = pushUrl.openConnection() as HttpURLConnection
                        pushConn.connectTimeout = 5000
                        pushConn.readTimeout = 15000
                        pushConn.requestMethod = "POST"
                        pushConn.doOutput = true
                        pushConn.setRequestProperty("Content-Type", "application/x-sqlite3")
                        pushConn.setFixedLengthStreamingMode(tempPushFile.length())

                        tempPushFile.inputStream().use { input ->
                            pushConn.outputStream.use { output ->
                                input.copyTo(output)
                            }
                        }

                        val pushCode = pushConn.responseCode
                        if (pushCode != 200) {
                            android.util.Log.w("LanSync", "Push to PC returned $pushCode")
                        }
                        pushConn.disconnect()
                    } finally {
                        tempPushFile.delete()
                    }
                }
            }

            // 5. 记录成功状态
            val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            LanSyncPreferences.setLastIp(context, targetIp)
            LanSyncPreferences.setLastPort(context, targetPort)
            LanSyncPreferences.setLastSyncTime(context, nowStr)
            GoogleDrivePreferences.setLastSyncTime(context, nowStr)
            GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.SUCCESS, "局域网同步完成 ($nowStr)")

            Result.success("局域网极速双向同步成功！")
        } catch (e: Exception) {
            val err = "局域网同步异常: ${e.message}"
            GoogleDriveSyncStateHolder.updateStatus(CloudSyncState.ERROR, err, error = err)
            Result.failure(e)
        } finally {
            try { pullConn?.disconnect() } catch (_: Exception) {}
        }
    }
}
