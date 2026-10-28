package com.renly.rmf.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.renly.rmf.domain.service.LanServerInfo
import com.renly.rmf.domain.service.LanSyncPreferences
import com.renly.rmf.domain.service.LanSyncService
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 局域网 Wi-Fi 直连极速双向同步弹窗
 * 同 Wi-Fi / 热点下免节点、免代理、零云端 API 依赖，0.1 秒秒级双向增量同步
 */
@Composable
fun LanSyncModal(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val lanService = remember { LanSyncService(context) }
    var localIp by remember { mutableStateOf(LanSyncService.getLocalWifiIpAddress() ?: "未连接 Wi-Fi") }
    val discoveredServers = remember { mutableStateListOf<LanServerInfo>() }
    var isSearching by remember { mutableStateOf(true) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncProgress by remember { mutableStateOf("") }
    var targetIpInput by remember { mutableStateOf(LanSyncPreferences.getLastIp(context)) }
    var lastSyncTime by remember { mutableStateOf(LanSyncPreferences.getLastSyncTime(context)) }

    // 启动本地接收服务与 UDP 信标发现
    DisposableEffect(Unit) {
        lanService.startServer(scope = coroutineScope)
        val discoveryJob = coroutineScope.launch {
            lanService.discoverServers(timeoutMs = 15000L) { server ->
                if (discoveredServers.none { it.ip == server.ip }) {
                    discoveredServers.add(server)
                    if (targetIpInput.isBlank()) {
                        targetIpInput = server.ip
                    }
                }
            }
            isSearching = false
        }

        onDispose {
            discoveryJob.cancel()
            lanService.stopServer()
        }
    }

    fun triggerSync(ip: String, port: Int = LanSyncService.DEFAULT_PORT) {
        if (ip.isBlank()) {
            Toast.makeText(context, "请输入目标设备 IP 地址", Toast.LENGTH_SHORT).show()
            return
        }
        isSyncing = true
        syncProgress = "准备连接 $ip:$port..."
        coroutineScope.launch {
            val result = lanService.syncWithTarget(ip, port) { progress ->
                syncProgress = progress
            }
            isSyncing = false
            if (result.isSuccess) {
                Toast.makeText(context, "✓ ${result.getOrNull()}", Toast.LENGTH_SHORT).show()
                lastSyncTime = LanSyncPreferences.getLastSyncTime(context)
            } else {
                val err = result.exceptionOrNull()?.message ?: "同步失败"
                Toast.makeText(context, "✗ $err", Toast.LENGTH_LONG).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSyncing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.92f),
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Wifi, contentDescription = null, tint = DopamineBlue, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "局域网 Wi-Fi 直连极速同步",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 本机 Wi-Fi 信息卡片
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkBg,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (localIp.startsWith("192.") || localIp.startsWith("10.") || localIp.startsWith("172.")) DopamineGreen else TextMuted)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "本机 IP: $localIp",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(localIp))
                                    Toast.makeText(context, "已复制本机 IP", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "复制", tint = TextSecondary, modifier = Modifier.size(14.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "端口: ${LanSyncService.DEFAULT_PORT} • 接收监听已就绪 (电脑也可填入此 IP 触发)",
                            fontSize = 10.5.sp,
                            color = TextMuted
                        )
                        if (lastSyncTime.isNotBlank()) {
                            Text(
                                text = "上次直连同步: $lastSyncTime",
                                fontSize = 10.5.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 自动发现已发现电脑列表
                Text("同局域网发现的电脑", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))

                if (discoveredServers.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = DarkBg.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            if (isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = DopamineBlue)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("正在监听局域网电脑就绪信标...", fontSize = 11.5.sp, color = TextSecondary)
                            } else {
                                Text("未探测到主动广播的电脑，可在下方直接手动输入电脑 IP", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (server in discoveredServers) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = DarkBg,
                                border = BorderStroke(1.dp, DopamineBlue.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Computer, contentDescription = null, tint = DopamineBlue, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(server.deviceName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                            Text("${server.ip}:${server.port}", fontSize = 11.sp, color = TextSecondary)
                                        }
                                    }

                                    Button(
                                        enabled = !isSyncing,
                                        onClick = { triggerSync(server.ip, server.port) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("直连同步", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 手动 IP 直连区域
                Text("手动指定目标电脑 IP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = targetIpInput,
                        onValueChange = { targetIpInput = it.trim() },
                        placeholder = { Text("例: 192.168.1.100", fontSize = 12.sp, color = TextMuted) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.5.sp, color = TextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DopamineBlue,
                            unfocusedBorderColor = DarkBorder,
                            focusedContainerColor = DarkBg,
                            unfocusedContainerColor = DarkBg
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        enabled = !isSyncing && targetIpInput.isNotBlank(),
                        onClick = { triggerSync(targetIpInput) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DopamineGreen),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Text("连接", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // 同步中实时进度提示
                if (isSyncing) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = DopamineBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = syncProgress.ifBlank { "正在执行局域网极速双向同步..." },
                            fontSize = 11.5.sp,
                            color = DopamineBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DarkBg.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("💡 提示：电脑与手机连接至同一 Wi-Fi 或热点即可 0.1 秒秒级双向合并。完全不经过任何云端服务器与代理节点，100% 数据本地互传。", fontSize = 10.5.sp, color = TextMuted)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                enabled = !isSyncing,
                onClick = onDismiss
            ) {
                Text("关闭", color = TextSecondary)
            }
        }
    )
}
