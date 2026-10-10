package com.renly.rmf.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.data.local.entity.InterruptionEntity
import com.renly.rmf.data.local.entity.ScheduleEntity
import com.renly.rmf.domain.service.AmbientSoundType
import com.renly.rmf.domain.service.FocusAmbientSoundPlayer
import com.renly.rmf.domain.service.FocusLiveService
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    isInPip: Boolean = false,
    schedulesFlow: Flow<List<ScheduleEntity>> = kotlinx.coroutines.flow.flowOf(emptyList()),
    onUpdateSchedule: (ScheduleEntity) -> Unit = {},
    onRecordInterruption: (InterruptionEntity) -> Unit,
    onEnterPiP: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val liveRunning by FocusLiveService.isRunning.collectAsState()
    val liveSeconds by FocusLiveService.remainingSeconds.collectAsState()
    val initialSeconds by FocusLiveService.initialTotalSeconds.collectAsState()
    val liveTitle by FocusLiveService.currentTaskTitle.collectAsState()

    val allSchedules by schedulesFlow.collectAsState(initial = emptyList())
    val activeSchedules = remember(allSchedules) {
        allSchedules.filter { it.isDeleted == 0 && it.status != "COMPLETED" && it.status != "ABANDONED" }
    }

    var boundScheduleId by remember { mutableStateOf<String?>(null) }
    val boundSchedule = remember(boundScheduleId, allSchedules) {
        allSchedules.find { it.id == boundScheduleId }
    }

    var customMinutes by remember { mutableIntStateOf(25) }
    var selectedAmbientSound by remember { mutableStateOf(AmbientSoundType.NONE) }
    var showEventPickerModal by remember { mutableStateOf(false) }
    var showCustomDurationDialog by remember { mutableStateOf(false) }
    var showInterruptionDialog by remember { mutableStateOf(false) }

    val isRunning = liveRunning
    val currentRemainingSeconds = if (isRunning) liveSeconds else (customMinutes * 60)
    val maxSeconds = if (isRunning) maxOf(1, initialSeconds) else maxOf(1, customMinutes * 60)

    val minutes = currentRemainingSeconds / 60
    val seconds = currentRemainingSeconds % 60
    val timeFormatted = "%02d:%02d".format(minutes, seconds)
    val progress = ((maxSeconds - currentRemainingSeconds).toFloat() / maxSeconds.toFloat()).coerceIn(0f, 1f)

    // 画中画 (PiP) 模式视图
    if (isInPip) {
        FocusPipCapsule(
            timeFormatted = timeFormatted,
            taskTitle = if (isRunning) liveTitle else (boundSchedule?.title ?: "深度专注"),
            isRunning = isRunning,
            onTogglePlay = {
                if (isRunning) {
                    FocusLiveService.pause(context)
                } else {
                    val title = boundSchedule?.title ?: "深度心流专注"
                    FocusLiveService.start(context, title, currentRemainingSeconds)
                }
            },
            onReset = {
                FocusLiveService.stop(context)
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
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 1. 顶部操作栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "心流专注空间",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Text(
                    text = "流体云实时胶囊 · 自定义时长 · 日程绑定",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 绑定日程快捷入口
                Surface(
                    onClick = { showEventPickerModal = true },
                    shape = RoundedCornerShape(12.dp),
                    color = if (boundSchedule != null) DopamineBlue.copy(alpha = 0.15f) else DarkCard,
                    border = BorderStroke(1.dp, if (boundSchedule != null) DopamineBlue else DarkBorder),
                    modifier = Modifier.height(38.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "绑定日程",
                            tint = if (boundSchedule != null) DopamineBlue else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (boundSchedule != null) "已绑定日程" else "绑定日程",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (boundSchedule != null) DopamineBlue else TextSecondary
                        )
                    }
                }

                // PiP 小窗按钮 (画中画)
                Surface(
                    onClick = onEnterPiP,
                    shape = RoundedCornerShape(12.dp),
                    color = DarkCard,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🪟", fontSize = 15.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. 绑定事件卡片 (支持随时切换与解绑)
        if (boundSchedule != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = DopamineBlue.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, DopamineBlue.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎯", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = boundSchedule.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (boundSchedule.category.isNotBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = DopamineBlue.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = boundSchedule.category,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DopamineBlue,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        if (boundSchedule.dod.isNotBlank()) {
                            Text(
                                text = "验收标准: ${boundSchedule.dod}",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { showEventPickerModal = true },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("更换", fontSize = 12.sp, color = DopamineBlue)
                        }
                        IconButton(
                            onClick = { boundScheduleId = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "解绑", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        } else {
            Surface(
                onClick = { showEventPickerModal = true },
                shape = RoundedCornerShape(14.dp),
                color = DarkCard,
                border = BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📌", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "未绑定具体日程 (自由专注模式)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                        Text(
                            text = "点击可关联今日日程，实际专注时长将自动计入 DoD 与时间块",
                            fontSize = 10.5.sp,
                            color = TextMuted
                        )
                    }
                    Text("绑定 >", fontSize = 11.5.sp, color = DopamineCyan, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 3. 巨幅拟物环形双层发光倒计时表盘
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(220.dp)
        ) {
            CircularProgressIndicator(
                progress = { 1f },
                color = DarkSurface,
                strokeWidth = 10.dp,
                modifier = Modifier.fillMaxSize()
            )

            CircularProgressIndicator(
                progress = { progress },
                color = if (isRunning) DopamineBlue else DopamineAmber,
                strokeWidth = 10.dp,
                modifier = Modifier.fillMaxSize()
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = timeFormatted,
                    fontSize = 48.sp,
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
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isRunning) DopamineGreen else TextMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 4. 自定义专注时长控制区 (未开始时完全开放调节)
        if (!isRunning) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "⚙️ 设定专注时长: $customMinutes 分钟",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 常用时长预设胶囊
                val presets = listOf(15, 25, 35, 45, 60, 90)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(presets) { mins ->
                        val isSelected = customMinutes == mins
                        FilterChip(
                            selected = isSelected,
                            onClick = { customMinutes = mins },
                            label = { Text("${mins}m", fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 微调增减与自定义直接输入按钮组
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = { customMinutes = maxOf(5, customMinutes - 5) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "-5m", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("5m", fontSize = 11.5.sp)
                    }

                    OutlinedButton(
                        onClick = { showCustomDurationDialog = true },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, DopamineCyan.copy(alpha = 0.6f)),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "自定义", tint = DopamineCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("精确输入时长", fontSize = 11.5.sp, color = DopamineCyan)
                    }

                    FilledTonalButton(
                        onClick = { customMinutes = minOf(180, customMinutes + 5) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "+5m", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("5m", fontSize = 11.5.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 5. 核心控制按钮 (开始 / 暂停 / 结束重置)
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = {
                    if (isRunning && boundSchedule != null) {
                        val elapsedSeconds = maxOf(0, initialSeconds - liveSeconds)
                        if (elapsedSeconds >= 60) {
                            val addedMins = elapsedSeconds / 60
                            val updated = boundSchedule.copy(
                                actualMinutes = boundSchedule.actualMinutes + addedMins,
                                updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                            )
                            onUpdateSchedule(updated)
                        }
                    }
                    FocusLiveService.stop(context)
                    FocusAmbientSoundPlayer.stopSound()
                },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = DarkCard),
                modifier = Modifier.size(54.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "重置/结束", tint = TextSecondary)
            }

            FilledIconButton(
                onClick = {
                    if (isRunning) {
                        FocusLiveService.pause(context)
                        FocusAmbientSoundPlayer.stopSound()
                    } else {
                        val title = boundSchedule?.title ?: "深度心流专注"
                        val secs = if (liveSeconds > 0 && liveSeconds < customMinutes * 60) liveSeconds else customMinutes * 60
                        FocusLiveService.start(context, title, secs)
                        if (selectedAmbientSound != AmbientSoundType.NONE) {
                            FocusAmbientSoundPlayer.startSound(selectedAmbientSound)
                        }
                    }
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isRunning) DopamineAmber else DopamineBlue
                ),
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "开始/暂停",
                    tint = DarkBg,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 🎧 专注白噪音与沉浸声景卡片 (Ambient Soundscape)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = DarkCard,
            border = BorderStroke(1.dp, if (selectedAmbientSound != AmbientSoundType.NONE) DopaminePurple.copy(alpha = 0.5f) else DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🎧 专注白噪音与沉浸声景", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.weight(1f))
                    if (selectedAmbientSound != AmbientSoundType.NONE) {
                        Surface(
                            color = DopaminePurple.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (FocusAmbientSoundPlayer.isPlaying()) "正在播放" else "就绪",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = DopaminePurple,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(AmbientSoundType.values()) { sound ->
                        val isSel = selectedAmbientSound == sound
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                selectedAmbientSound = sound
                                if (isRunning) {
                                    FocusAmbientSoundPlayer.startSound(sound)
                                }
                            },
                            leadingIcon = {
                                Text(sound.icon, fontSize = 13.sp)
                            },
                            label = {
                                Text(sound.title, fontSize = 12.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                            }
                        )
                    }
                }

                if (selectedAmbientSound != AmbientSoundType.NONE) {
                    Text(
                        text = selectedAmbientSound.desc,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 6. ColorOS 流体云实时状态指示卡片 (动态呈现流体云胶囊状态)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isRunning) DopamineCyan.copy(alpha = 0.12f) else DarkCard,
            border = BorderStroke(1.dp, if (isRunning) DopamineCyan.copy(alpha = 0.45f) else DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) DopamineGreen else DopamineCyan)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRunning) "🫧 ColorOS 流体云实时胶囊运行中" else "🫧 ColorOS 流体云待命就绪",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isRunning) DopamineCyan else TextPrimary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Surface(
                        color = (if (isRunning) DopamineCyan else TextMuted).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (isRunning) "实时投递中" else "待机",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isRunning) DopamineCyan else TextMuted,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isRunning) {
                        "当前状态栏左上角挖孔旁已浮现实时动态胶囊！切换至手机桌面或息屏锁屏，胶囊与大卡片将常驻展示【${if (boundSchedule != null) boundSchedule.title else liveTitle}】倒计时与快捷暂停按钮。"
                    } else {
                        "点击上方开始专注后，系统将自动通过高优先级前台服务在状态栏与锁屏激活流体云实时胶囊（深度适配 OPPO ColorOS / 小米 HyperOS / vivo OriginOS）。"
                    },
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 7. 记录心流打断按钮
        OutlinedButton(
            onClick = { showInterruptionDialog = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DopamineRed),
            border = BorderStroke(1.dp, DopamineRed.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(42.dp)
        ) {
            Text("⚡ 记录一次心流打断", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // 弹窗 1: 选择绑定日程事件 (EventPickerModal)
    if (showEventPickerModal) {
        AlertDialog(
            onDismissRequest = { showEventPickerModal = false },
            containerColor = DarkCard,
            title = {
                Text("🎯 选择要绑定的日程时间块", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                if (activeSchedules.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📭 暂无可绑定的待办或进行中日程", fontSize = 13.sp, color = TextMuted)
                        Text("可在「日程」页面新增时间块后再来绑定", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)
                    ) {
                        items(activeSchedules) { schedule ->
                            val isSelected = schedule.id == boundScheduleId
                            Surface(
                                onClick = {
                                    boundScheduleId = schedule.id
                                    if (!isRunning) {
                                        customMinutes = schedule.estimatedMinutes.coerceIn(5, 180)
                                    }
                                    showEventPickerModal = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) DopamineBlue.copy(alpha = 0.15f) else DarkSurface,
                                border = BorderStroke(1.dp, if (isSelected) DopamineBlue else DarkBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = schedule.title,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 3.dp)
                                        ) {
                                            Text(
                                                text = "⏱️ ${schedule.estimatedMinutes}分钟",
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                            if (schedule.category.isNotBlank()) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "· ${schedule.category}",
                                                    fontSize = 11.sp,
                                                    color = DopamineCyan
                                                )
                                            }
                                        }
                                        if (schedule.dod.isNotBlank()) {
                                            Text(
                                                text = "DoD: ${schedule.dod}",
                                                fontSize = 10.5.sp,
                                                color = TextMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = "已选", tint = DopamineBlue, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEventPickerModal = false }) {
                    Text("完成", color = DopamineBlue)
                }
            },
            dismissButton = {
                if (boundScheduleId != null) {
                    TextButton(onClick = {
                        boundScheduleId = null
                        showEventPickerModal = false
                    }) {
                        Text("清除绑定", color = DopamineRed)
                    }
                }
            }
        )
    }

    // 弹窗 2: 精确自定义时长输入弹窗
    if (showCustomDurationDialog) {
        var inputMinutesText by remember { mutableStateOf(customMinutes.toString()) }
        AlertDialog(
            onDismissRequest = { showCustomDurationDialog = false },
            containerColor = DarkCard,
            title = { Text("⏱️ 自定义专注时长 (分钟)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = inputMinutesText,
                        onValueChange = { inputMinutesText = it.filter { char -> char.isDigit() } },
                        label = { Text("输入时长 (1 ~ 240 分钟)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DopamineCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("提示: 建议单次深度专注时长在 25 ~ 90 分钟以内，保持最佳神经认知专注力。", fontSize = 11.sp, color = TextSecondary)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = inputMinutesText.toIntOrNull()
                        if (parsed != null && parsed in 1..240) {
                            customMinutes = parsed
                        }
                        showCustomDurationDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DopamineCyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("确定", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDurationDialog = false }) { Text("取消", color = TextSecondary) }
            }
        )
    }

    // 弹窗 3: 记录打断弹窗
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
fun FocusPipCapsule(
    timeFormatted: String,
    taskTitle: String = "心流专注",
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
                Column(verticalArrangement = Arrangement.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(if (isRunning) DopamineGreen else DopamineAmber, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRunning) taskTitle else "准备就绪",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isRunning) DopamineGreen else TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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

