package com.renly.rmf.ui.screens

import android.widget.Toast
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
import com.renly.rmf.BuildConfig
import com.renly.rmf.domain.service.HotPatchCheckResult
import com.renly.rmf.domain.service.HotPatchManager
import com.renly.rmf.domain.service.HotPatchPreferences
import com.renly.rmf.domain.service.PatchInfo
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 商业级免安装动态热更新 (Hot Patching) 管理控制弹窗
 */
@Composable
fun HotPatchDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isEnabled by remember { mutableStateOf(HotPatchPreferences.isEnabled(context)) }
    var activePatchVersion by remember { mutableIntStateOf(HotPatchPreferences.getActivePatchVersion(context)) }
    var activePatchName by remember { mutableStateOf(HotPatchPreferences.getActivePatchName(context)) }
    var stabilityStage by remember { mutableIntStateOf(HotPatchPreferences.getStabilityStage(context)) }
    var rollbackReason by remember { mutableStateOf(HotPatchPreferences.getLastRollbackReason(context)) }
    var lastCheckTime by remember { mutableStateOf(HotPatchPreferences.getLastCheckTime(context)) }

    var isChecking by remember { mutableStateOf(false) }
    var availablePatch by remember { mutableStateOf<PatchInfo?>(null) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var isReadyToReload by remember { mutableStateOf(false) }

    fun refreshState() {
        activePatchVersion = HotPatchPreferences.getActivePatchVersion(context)
        activePatchName = HotPatchPreferences.getActivePatchName(context)
        stabilityStage = HotPatchPreferences.getStabilityStage(context)
        rollbackReason = HotPatchPreferences.getLastRollbackReason(context)
        lastCheckTime = HotPatchPreferences.getLastCheckTime(context)
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
            shape = RoundedCornerShape(26.dp),
            color = DarkCard,
            border = BorderStroke(0.6.dp, DarkBorder),
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(0.92f)
                .padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 顶部标题栏与关闭按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DopamineCyan.copy(alpha = 0.16f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("⚡", fontSize = 20.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "动态热更新 (免安装补丁)",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (activePatchVersion > 0) DopamineGreen.copy(alpha = 0.2f) else DarkSurface,
                                    border = BorderStroke(0.6.dp, if (activePatchVersion > 0) DopamineGreen else DarkBorder)
                                ) {
                                    Text(
                                        text = if (activePatchVersion > 0) "补丁已生效" else "运行宿主底包",
                                        color = if (activePatchVersion > 0) DopamineGreen else TextMuted,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "商业级动态代码载入技术，无需系统重新安装",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 1.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isDownloading,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 核心状态卡片
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(0.6.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("宿主底包版本", fontSize = 12.sp, color = TextSecondary)
                            Text("v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("当前活跃热补丁", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                text = if (activePatchVersion > 0) "v$activePatchVersion ($activePatchName)" else "无 (未加载外部代码)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activePatchVersion > 0) DopamineCyan else TextMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("看门狗检验状态", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                text = when (stabilityStage) {
                                    HotPatchPreferences.STAGE_STABLE -> "🟢 稳定运行中 (安全合格)"
                                    HotPatchPreferences.STAGE_TESTING -> "🟡 启动观察期 (10s 守护)"
                                    HotPatchPreferences.STAGE_ROLLED_BACK -> "🔴 已自动熔断回滚"
                                    else -> "⚪ 宿主基线"
                                },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = when (stabilityStage) {
                                    HotPatchPreferences.STAGE_STABLE -> DopamineGreen
                                    HotPatchPreferences.STAGE_TESTING -> DopamineOrange
                                    HotPatchPreferences.STAGE_ROLLED_BACK -> DopamineRed
                                    else -> TextMuted
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("上次检查时间", fontSize = 11.sp, color = TextMuted)
                            Text(lastCheckTime, fontSize = 11.sp, color = TextMuted)
                        }

                        if (rollbackReason != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = DopamineRed.copy(alpha = 0.12f),
                                border = BorderStroke(0.6.dp, DopamineRed.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "⚠️ $rollbackReason",
                                    fontSize = 11.sp,
                                    color = DopamineRed,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 开关选项
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("允许加载动态热补丁", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(
                            text = "开启后启动时自动挂载已下载的补丁代码，关闭将只运行原装底包",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = {
                            isEnabled = it
                            HotPatchPreferences.setEnabled(context, it)
                            refreshState()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = DopamineCyan
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 检查到可用补丁详情展示卡片
                if (availablePatch != null) {
                    val patch = availablePatch!!
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DopamineCyan.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(0.6.dp, DopamineCyan.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("发现云端免安装补丁: v${patch.patchVersion}", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = DopamineCyan)
                                Text(
                                    text = String.format(Locale.CHINA, "%.1f KB", patch.fileSize / 1024f),
                                    fontSize = 11.5.sp,
                                    color = TextSecondary
                                )
                            }

                            if (patch.changelog.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(patch.changelog, fontSize = 12.sp, color = TextPrimary)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (isDownloading) {
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = DopamineCyan,
                                    trackColor = DarkBorder
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "正在下载补丁: ${(downloadProgress * 100).toInt()}%...",
                                    fontSize = 11.5.sp,
                                    color = TextSecondary
                                )
                            } else if (isReadyToReload) {
                                Button(
                                    onClick = {
                                        HotPatchManager.restartApp(context)
                                    },
                                    shape = RoundedCornerShape(999.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DopamineGreen),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("🔄 补丁就绪，点击立即重载生效 (免安装)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        isDownloading = true
                                        feedbackMessage = null
                                        coroutineScope.launch {
                                            val res = HotPatchManager.downloadAndApplyPatch(
                                                context = context,
                                                patchInfo = patch,
                                                onProgress = { p -> downloadProgress = p }
                                            )
                                            isDownloading = false
                                            if (res.isSuccess) {
                                                isReadyToReload = true
                                                feedbackMessage = "✓ 补丁下载并装配完成！无需安装，点击下方按钮立即重载。"
                                                refreshState()
                                            } else {
                                                feedbackMessage = "✗ 下载补丁失败: ${res.exceptionOrNull()?.message}"
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(999.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DopamineCyan),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("⚡ 一键下载并热装载", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (feedbackMessage != null) {
                    Text(
                        text = feedbackMessage!!,
                        fontSize = 12.sp,
                        color = if (feedbackMessage!!.startsWith("✓")) DopamineGreen else if (feedbackMessage!!.startsWith("✗")) DopamineRed else DopamineCyan,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                // 底部操作按键行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 检查补丁按键
                    Button(
                        onClick = {
                            if (isChecking) return@Button
                            isChecking = true
                            availablePatch = null
                            feedbackMessage = null
                            coroutineScope.launch {
                                val res = HotPatchManager.checkForPatch(context)
                                isChecking = false
                                refreshState()
                                when (res) {
                                    is HotPatchCheckResult.PatchAvailable -> {
                                        availablePatch = res.patchInfo
                                    }
                                    is HotPatchCheckResult.AlreadyLatest -> {
                                        feedbackMessage = "🎉 当前已是最新补丁 (v${res.currentPatchVersion})，无需更新。"
                                    }
                                    is HotPatchCheckResult.NotAuthorized -> {
                                        feedbackMessage = res.message
                                    }
                                    is HotPatchCheckResult.NoPatchFound -> {
                                        feedbackMessage = res.message
                                    }
                                    is HotPatchCheckResult.Error -> {
                                        feedbackMessage = "✗ ${res.error}"
                                    }
                                }
                            }
                        },
                        enabled = !isChecking && !isDownloading,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("正在检索...", fontSize = 12.sp)
                        } else {
                            Text("🔍 检查云端热补丁", fontSize = 12.sp)
                        }
                    }

                    // 卸载/回滚补丁按键 (若当前有补丁)
                    if (activePatchVersion > 0) {
                        OutlinedButton(
                            onClick = {
                                HotPatchManager.rollbackPatch(context)
                                refreshState()
                                Toast.makeText(context, "已清除热补丁，重启应用后彻底恢复原版底包。", Toast.LENGTH_LONG).show()
                            },
                            enabled = !isDownloading,
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(0.6.dp, DopamineRed),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DopamineRed),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("↩️ 卸载并恢复底包", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
