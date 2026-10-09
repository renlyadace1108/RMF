package com.renly.rmf.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.data.local.entity.InterruptionEntity
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun FocusScreen(
    isInPip: Boolean = false,
    onRecordInterruption: (InterruptionEntity) -> Unit,
    onEnterPiP: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val liveRunning by com.renly.rmf.domain.service.FocusLiveService.isRunning.collectAsState()
    val liveSeconds by com.renly.rmf.domain.service.FocusLiveService.remainingSeconds.collectAsState()

    var showInterruptionDialog by remember { mutableStateOf(false) }

    val isRunning = liveRunning
    val totalSeconds = liveSeconds

    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val timeFormatted = "%02d:%02d".format(minutes, seconds)
    val progress = (25 * 60 - totalSeconds).toFloat() / (25 * 60).toFloat()

    // 针对画中画 (PiP) 模式专属的超清微型专注胶囊视图
    if (isInPip) {
        FocusPipCapsule(
            timeFormatted = timeFormatted,
            isRunning = isRunning,
            onTogglePlay = {
                if (isRunning) {
                    com.renly.rmf.domain.service.FocusLiveService.pause(context)
                } else {
                    com.renly.rmf.domain.service.FocusLiveService.start(context, "深度心流专注", totalSeconds)
                }
            },
            onReset = {
                com.renly.rmf.domain.service.FocusLiveService.stop(context)
            }
        )
        return
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // 顶部操作栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "心流专注空间",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Text(
                    text = "屏蔽外界纷扰 · 沉浸深度创造",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // PiP 小窗按钮 (画中画)
            Surface(
                onClick = onEnterPiP,
                shape = RoundedCornerShape(12.dp),
                color = DarkCard,
                border = BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🪟", fontSize = 16.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 巨幅拟物环形双层发光倒计时表盘
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(240.dp)
        ) {
            // 背景底环
            CircularProgressIndicator(
                progress = { 1f },
                color = DarkSurface,
                strokeWidth = 10.dp,
                modifier = Modifier.fillMaxSize()
            )

            // 动态进度环
            CircularProgressIndicator(
                progress = { progress },
                color = if (isRunning) DopamineBlue else DopamineAmber,
                strokeWidth = 10.dp,
                modifier = Modifier.fillMaxSize()
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = timeFormatted,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    letterSpacing = (-1).sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = (if (isRunning) DopamineGreen else TextMuted).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isRunning) "● 深度心流中" else "○ 准备就绪",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isRunning) DopamineGreen else TextMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 核心控制按钮
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = {
                    com.renly.rmf.domain.service.FocusLiveService.stop(context)
                },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = DarkCard),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "重置", tint = TextSecondary)
            }

            FilledIconButton(
                onClick = {
                    if (isRunning) {
                        com.renly.rmf.domain.service.FocusLiveService.pause(context)
                    } else {
                        com.renly.rmf.domain.service.FocusLiveService.start(context, "深度心流专注", totalSeconds)
                    }
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isRunning) DopamineAmber else DopamineBlue
                ),
                modifier = Modifier.size(76.dp)
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "开始/暂停",
                    tint = DarkBg,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ColorOS 流体云指示胶囊
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isRunning) DopamineCyan.copy(alpha = 0.12f) else DarkCard,
            border = BorderStroke(1.dp, if (isRunning) DopamineCyan.copy(alpha = 0.4f) else DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isRunning) "🫧 ColorOS 流体云已激活" else "🫧 支持 ColorOS 流体云",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isRunning) DopamineCyan else TextSecondary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (isRunning) "切到桌面/锁屏可在挖孔旁常驻倒计时" else "开启后状态栏挖孔旁将呈现胶囊",
                    fontSize = 10.5.sp,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 记录打断轻质按钮
        OutlinedButton(
            onClick = { showInterruptionDialog = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DopamineRed),
            border = BorderStroke(1.dp, DopamineRed.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Text("⚡ 记录一次心流打断", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(18.dp))
    }

    if (showInterruptionDialog) {
        RecordInterruptionDialog(
            onDismiss = { showInterruptionDialog = false },
            onConfirm = { type, note ->
                val entity = InterruptionEntity(
                    type = type,
                    durationSeconds = 60,
                    timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_DATE_TIME),
                    note = note
                )
                onRecordInterruption(entity)
                showInterruptionDialog = false
            }
        )
    }
}

@Composable
fun RecordInterruptionDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var selectedType by remember { mutableStateOf("EXTERNAL") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("记录专注打断", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "打断来源:", color = TextSecondary, fontSize = 12.sp)
                val types = listOf(
                    "EXTERNAL" to "外部干扰",
                    "INTERNAL" to "走神自扰",
                    "EMERGENCY" to "突发事务",
                    "TECH" to "技术故障"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    types.forEach { (typeKey, label) ->
                        FilterChip(
                            selected = selectedType == typeKey,
                            onClick = { selectedType = typeKey },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("打断详情与备注") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DopamineRed,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedType, note) },
                colors = ButtonDefaults.buttonColors(containerColor = DopamineRed),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("记录", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        }
    )
}

@Composable
fun FocusPipCapsule(
    timeFormatted: String,
    isRunning: Boolean,
    onTogglePlay: () -> Unit,
    onReset: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkCard,
            border = BorderStroke(1.dp, if (isRunning) DopamineBlue.copy(alpha = 0.6f) else DarkBorder),
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧状态指示与倒计时
                Column(
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(if (isRunning) DopamineGreen else DopamineAmber, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRunning) "心流专注中" else "准备就绪",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isRunning) DopamineGreen else TextMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = timeFormatted,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        letterSpacing = (-1).sp
                    )
                }

                // 右侧操作按钮
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledIconButton(
                        onClick = onReset,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = DarkSurface),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "重置",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    FilledIconButton(
                        onClick = onTogglePlay,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isRunning) DopamineAmber else DopamineBlue
                        ),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isRunning) "暂停" else "开始",
                            tint = DarkBg,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

