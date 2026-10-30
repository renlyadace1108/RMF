package com.renly.rmf.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.renly.rmf.domain.service.GoogleDriveUpdateService
import com.renly.rmf.domain.service.UpdateCheckResult
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * 应用内自动更新升级弹窗 (Google Drive 专属更新通道)
 */
@Composable
fun AppUpdateDialog(
    updateData: UpdateCheckResult.UpdateAvailable,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isDownloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var downloadedBytes by remember { mutableStateOf(0L) }
    var totalBytes by remember { mutableStateOf(updateData.targetFileSize) }
    var statusText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val cachedFile = remember {
        GoogleDriveUpdateService.getCachedApk(context, updateData.targetFileSize)
    }
    var downloadedApkFile by remember { mutableStateOf<File?>(cachedFile) }
    val isReadyToInstall = downloadedApkFile != null && downloadedApkFile!!.exists()

    fun triggerInstall(file: File) {
        val installRes = GoogleDriveUpdateService.installApk(context, file)
        if (installRes.isFailure) {
            errorText = installRes.exceptionOrNull()?.message ?: "调起安装程序失败"
        } else {
            errorText = null
        }
    }

    fun startDownloadAndInstall() {
        if (isReadyToInstall && downloadedApkFile != null) {
            triggerInstall(downloadedApkFile!!)
            return
        }

        isDownloading = true
        errorText = null
        statusText = "正在连接 Google Drive 云端并下载安装包..."

        coroutineScope.launch {
            val res = GoogleDriveUpdateService.downloadApk(
                context = context,
                fileId = updateData.targetFileId,
                expectedSize = updateData.targetFileSize,
                onProgress = { p, current, total ->
                    progress = p
                    downloadedBytes = current
                    if (total > 0) totalBytes = total
                    statusText = String.format(
                        Locale.CHINA,
                        "正在下载: %.1f MB / %.1f MB (%.0f%%)",
                        current / (1024f * 1024f),
                        total / (1024f * 1024f),
                        p * 100f
                    )
                }
            )

            isDownloading = false
            if (res.isSuccess) {
                val apk = res.getOrThrow()
                downloadedApkFile = apk
                statusText = "下载完成，正在调起系统安装器..."
                triggerInstall(apk)
            } else {
                errorText = res.exceptionOrNull()?.message ?: "下载过程中出现异常"
                statusText = ""
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isDownloading) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !isDownloading,
            dismissOnClickOutside = !isDownloading,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkCard,
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DopamineBlue.copy(alpha = 0.16f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🚀", fontSize = 22.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "发现新版本",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = DopamineBlue.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, DopamineBlue.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "v${updateData.releaseInfo.versionName}",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DopamineBlue,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (updateData.isPad) "平板专属优化版 (Pad Edition)" else "手机正式版 (Phone Edition)",
                                fontSize = 11.5.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    if (!isDownloading) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭",
                                tint = TextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Meta Info Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val sizeMb = if (totalBytes > 0) totalBytes / (1024f * 1024f) else 0f
                    if (sizeMb > 0f) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = DarkSurface,
                            border = BorderStroke(1.dp, DarkBorder)
                        ) {
                            Text(
                                text = String.format(Locale.CHINA, "📦 大小: %.1f MB", sizeMb),
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (updateData.releaseInfo.releaseDate.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = DarkSurface,
                            border = BorderStroke(1.dp, DarkBorder)
                        ) {
                            Text(
                                text = "🕒 ${updateData.releaseInfo.releaseDate.split(" ").firstOrNull() ?: updateData.releaseInfo.releaseDate}",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = DopamineGreen.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, DopamineGreen.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "☁️ Google Drive 专属通道",
                            fontSize = 11.sp,
                            color = DopamineGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Changelog Card
                Text(
                    text = "更新内容与改进说明:",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp, max = 220.dp)
                ) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(scrollState)
                    ) {
                        val changelog = updateData.releaseInfo.changelog.ifBlank { "• 常规性能优化与体验细节打磨" }
                        Text(
                            text = changelog,
                            fontSize = 12.5.sp,
                            color = TextPrimary.copy(alpha = 0.9f),
                            lineHeight = 18.sp
                        )
                    }
                }

                // Downloading Progress
                if (isDownloading || statusText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = DopamineBlue,
                            trackColor = DarkSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = statusText,
                            fontSize = 11.5.sp,
                            color = DopamineBlue
                        )
                    }
                }

                // Error / Permission Notice
                if (errorText != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "⚠️ $errorText",
                                fontSize = 11.5.sp,
                                color = Color(0xFFFCA5A5),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isDownloading) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("稍后提醒", color = TextSecondary, fontSize = 13.sp)
                        }
                    }

                    Button(
                        onClick = { startDownloadAndInstall() },
                        enabled = !isDownloading,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DopamineBlue,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = when {
                                isDownloading -> "正在下载..."
                                isReadyToInstall -> "安装包已就绪，立即安装"
                                else -> "立即下载并更新"
                            },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
