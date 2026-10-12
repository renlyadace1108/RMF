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
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
    var showEventPickerModal by remember { mutableStateOf(false) }
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
                    val title = boundSchedule?.title ?: "深度专注"
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
                    text = "深度专注",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Text(
                    text = "沉浸心流 · 实时活动",
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

        // 4. 时间滑动滚轮选择器 (Scroll Wheel Duration Picker)
        if (!isRunning) {
            val minutesList = remember { (5..180 step 5).toList() }
            val itemHeight = 40.dp
            val initialIndex = remember { (minutesList.indexOf(customMinutes)).coerceAtLeast(0) }
            val wheelListState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
            val flingBehavior = rememberSnapFlingBehavior(lazyListState = wheelListState)

            // 监听滚轮滚动并同步 customMinutes
            LaunchedEffect(wheelListState.firstVisibleItemIndex) {
                val selectedIdx = wheelListState.firstVisibleItemIndex.coerceIn(0, minutesList.lastIndex)
                if (minutesList[selectedIdx] != customMinutes) {
                    customMinutes = minutesList[selectedIdx]
                }
            }

            // 当从外部改变 customMinutes 时同步滚轮位置
            LaunchedEffect(customMinutes) {
                val targetIdx = minutesList.indexOf(customMinutes)
                if (targetIdx >= 0 && targetIdx != wheelListState.firstVisibleItemIndex) {
                    wheelListState.animateScrollToItem(targetIdx)
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "设定专注时长",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 拟物滚轮容器 (带上下羽化渐变遮罩与中间高亮选框)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(itemHeight * 3)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkCard)
                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // 中间高亮标尺线 / 背景槽
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(itemHeight)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DopamineCyan.copy(alpha = 0.12f))
                            .border(1.dp, DopamineCyan.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    )

                    // 纵向滚轮滚动列表
                    LazyColumn(
                        state = wheelListState,
                        flingBehavior = flingBehavior,
                        contentPadding = PaddingValues(vertical = itemHeight),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(minutesList) { mins ->
                            val isCurrentSelected = mins == customMinutes
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(itemHeight)
                                    .clickable {
                                        customMinutes = mins
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "$mins",
                                        fontSize = if (isCurrentSelected) 22.sp else 16.sp,
                                        fontWeight = if (isCurrentSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrentSelected) DopamineCyan else TextMuted
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "分钟",
                                        fontSize = if (isCurrentSelected) 12.sp else 10.sp,
                                        color = if (isCurrentSelected) TextPrimary else TextMuted
                                    )
                                }
                            }
                        }
                    }

                    // 上下羽化渐变遮罩 (打造 iOS / ColorOS 真实滚轮深度感)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(DarkCard, Color.Transparent)
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, DarkCard)
                                )
                            )
                    )
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
                    } else {
                        val title = boundSchedule?.title ?: "深度专注"
                        val secs = if (liveSeconds > 0 && liveSeconds < customMinutes * 60) liveSeconds else customMinutes * 60
                        FocusLiveService.start(context, title, secs)
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

        Spacer(modifier = Modifier.height(20.dp))

        // 6. 记录心流打断按钮
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

    // 弹窗 2: 记录打断弹窗
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

