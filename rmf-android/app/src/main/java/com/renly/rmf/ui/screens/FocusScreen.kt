package com.renly.rmf.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalView
import com.renly.rmf.util.HapticHelper
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.data.local.entity.FocusRecordEntity
import com.renly.rmf.data.local.entity.InterruptionEntity
import com.renly.rmf.data.local.entity.ScheduleEntity
import com.renly.rmf.domain.service.AiPreferences
import com.renly.rmf.domain.service.AiResult
import com.renly.rmf.domain.service.AiService
import com.renly.rmf.domain.service.FocusLiveService
import com.renly.rmf.domain.service.FocusPreferences
import com.renly.rmf.domain.service.FocusPreset
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FocusScreen(
    isInPip: Boolean = false,
    schedulesFlow: Flow<List<ScheduleEntity>> = kotlinx.coroutines.flow.flowOf(emptyList()),
    focusRecordsFlow: Flow<List<FocusRecordEntity>> = kotlinx.coroutines.flow.flowOf(emptyList()),
    onUpdateSchedule: (ScheduleEntity) -> Unit = {},
    onRecordInterruption: (InterruptionEntity) -> Unit,
    onEnterPiP: () -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    val liveRunning by FocusLiveService.isRunning.collectAsState()
    val liveSeconds by FocusLiveService.remainingSeconds.collectAsState()
    val initialSeconds by FocusLiveService.initialTotalSeconds.collectAsState()
    val liveTitle by FocusLiveService.currentTaskTitle.collectAsState()
    val sessionInterruptions by FocusLiveService.interruptionCount.collectAsState()

    val allSchedules by schedulesFlow.collectAsState(initial = emptyList())
    val activeSchedules = remember(allSchedules) {
        allSchedules.filter { it.isDeleted == 0 && it.status != "COMPLETED" && it.status != "ABANDONED" }
    }

    val focusRecords by focusRecordsFlow.collectAsState(initial = emptyList())

    var boundScheduleId by remember { mutableStateOf<String?>(null) }
    val boundSchedule = remember(boundScheduleId, allSchedules) {
        allSchedules.find { it.id == boundScheduleId }
    }

    var selectedHours by remember { mutableIntStateOf(0) }
    var selectedMinutes by remember { mutableIntStateOf(25) }
    var selectedSeconds by remember { mutableIntStateOf(0) }
    var presets by remember { mutableStateOf(FocusPreferences.getPresets(context)) }

    val isRunning = liveRunning
    val isPausedMidSession = !isRunning && liveSeconds > 0 && liveSeconds < initialSeconds

    fun applyPresetMinutes(totalMins: Int) {
        if (isPausedMidSession) {
            FocusLiveService.stop(context)
        }
        selectedHours = (totalMins / 60).coerceIn(0, 12)
        selectedMinutes = (totalMins % 60).coerceIn(0, 59)
        selectedSeconds = 0
    }

    fun adjustMinutes(deltaMins: Int) {
        if (isPausedMidSession) {
            FocusLiveService.stop(context)
        }
        val currentTotal = selectedHours * 60 + selectedMinutes
        val newTotal = (currentTotal + deltaMins).coerceIn(1, 12 * 60)
        selectedHours = (newTotal / 60).coerceIn(0, 12)
        selectedMinutes = (newTotal % 60).coerceIn(0, 59)
        selectedSeconds = 0
    }

    var showAddPresetDialog by remember { mutableStateOf(false) }
    var showDirectTimeDialog by remember { mutableStateOf(false) }
    var presetToDelete by remember { mutableStateOf<FocusPreset?>(null) }
    var showEventPickerModal by remember { mutableStateOf(false) }
    var showInterruptionDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    val configuredTotalSeconds = (selectedHours * 3600 + selectedMinutes * 60 + selectedSeconds).coerceAtLeast(1)
    val currentRemainingSeconds = if (isRunning || isPausedMidSession) liveSeconds else configuredTotalSeconds
    val maxSeconds = if (isRunning || isPausedMidSession) maxOf(1, initialSeconds) else maxOf(1, configuredTotalSeconds)

    val displayHours = currentRemainingSeconds / 3600
    val displayMinutes = (currentRemainingSeconds % 3600) / 60
    val displaySeconds = currentRemainingSeconds % 60
    val timeFormatted = "%02d:%02d:%02d".format(displayHours, displayMinutes, displaySeconds)
    val pipTimeFormatted = if (displayHours > 0) {
        "%02d:%02d:%02d".format(displayHours, displayMinutes, displaySeconds)
    } else {
        "%02d:%02d".format(displayMinutes, displaySeconds)
    }
    val progress = ((maxSeconds - currentRemainingSeconds).toFloat() / maxSeconds.toFloat()).coerceIn(0f, 1f)

    // ==========================================
    // 🪟 超紧凑超精简画中画 (PiP) 模式视图
    // ==========================================
    if (isInPip) {
        FocusPipCapsule(
            timeFormatted = pipTimeFormatted,
            taskTitle = if (isRunning) liveTitle else (boundSchedule?.title ?: "深度专注"),
            isRunning = isRunning,
            onTogglePlay = {
                if (isRunning) {
                    FocusLiveService.pause(context)
                    Toast.makeText(context, "⏸️ 专注已暂停 · 已自动记录 1 次心流打断", Toast.LENGTH_SHORT).show()
                } else {
                    if (isPausedMidSession) {
                        FocusLiveService.resume(context)
                    } else {
                        val title = boundSchedule?.title ?: "深度专注"
                        FocusLiveService.start(
                            context = context,
                            title = title,
                            seconds = currentRemainingSeconds,
                            scheduleId = boundSchedule?.id,
                            category = boundSchedule?.category ?: "WORK"
                        )
                    }
                }
            },
            onReset = {
                FocusLiveService.stop(context)
            }
        )
        return
    }

    // ==========================================
    // 主界面视图
    // ==========================================
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
                // 专注历史记录与分析
                Surface(
                    onClick = { showHistoryDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    color = DarkCard,
                    border = BorderStroke(1.dp, DopaminePurple.copy(alpha = 0.5f)),
                    modifier = Modifier.height(38.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "历史分析",
                            tint = DopaminePurple,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "历史分析",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DopaminePurple
                        )
                    }
                }

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
                            text = if (boundSchedule != null) "已绑定" else "绑定日程",
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

        // 时·分·秒 三列独立滑动滚轮配置
        val hoursList = remember { (0..12).toList() }
        val minsSecsList = remember { (0..59).toList() }
        val itemHeight = 40.dp

        // 3. 拟物环形双层发光倒计时表盘（时 : 分 : 秒 三列滚轮合为一体）
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(260.dp)
        ) {
            val trackColor = DarkSurface
            val activeColor1 = if (isRunning) DopamineCyan else DopamineAmber
            val activeColor2 = if (isRunning) DopamineBlue else DopamineOrange
            val activeColor3 = if (isRunning) DopaminePurple else DopamineRed

            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeW = 11.dp.toPx()
                val diameter = size.minDimension - strokeW
                val radius = diameter / 2f
                val centerOffset = Offset(size.width / 2f, size.height / 2f)

                // 1. 底层钛金轨道 (Titanium Base Ring)
                drawCircle(
                    color = trackColor,
                    radius = radius,
                    center = centerOffset,
                    style = Stroke(width = strokeW)
                )

                // 2. 轨道内侧微光边缘 (Subtle inner specular edge)
                drawCircle(
                    color = Color.White.copy(alpha = 0.04f),
                    radius = radius - strokeW / 2f,
                    center = centerOffset,
                    style = Stroke(width = 1.dp.toPx())
                )

                // 3. 动态高质感多色霓虹渐变弧 (Luminous Gradient Progress Arc)
                if (progress > 0.001f) {
                    val sweepBrush = Brush.sweepGradient(
                        0.0f to activeColor1,
                        0.5f to activeColor2,
                        1.0f to activeColor3,
                        center = centerOffset
                    )
                    drawArc(
                        brush = sweepBrush,
                        startAngle = -90f,
                        sweepAngle = (progress.coerceIn(0f, 1f) * 360f),
                        useCenter = false,
                        style = Stroke(
                            width = strokeW,
                            cap = StrokeCap.Round
                        )
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isRunning || isPausedMidSession) {
                    Text(
                        text = timeFormatted,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        letterSpacing = (-0.5).sp,
                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                        modifier = Modifier.clickable(enabled = isPausedMidSession) {
                            FocusLiveService.stop(context)
                            showDirectTimeDialog = true
                        }
                    )
                } else {
                    // 顶部 时 / 分 / 秒 清晰标签 (点击可直接呼出精准调时弹窗)
                    Row(
                        modifier = Modifier
                            .width(188.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showDirectTimeDialog = true },
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("时", fontSize = 11.5.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("分", fontSize = 11.5.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("秒", fontSize = 11.5.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // 表盘中央：时 : 分 : 秒 三个独立滚轮并排组合
                    Box(
                        modifier = Modifier
                            .width(190.dp)
                            .height(itemHeight * 3),
                        contentAlignment = Alignment.Center
                    ) {
                        // 中央选中行高亮胶囊框
                        Box(
                            modifier = Modifier
                                .width(186.dp)
                                .height(itemHeight + 2.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DopamineCyan.copy(alpha = 0.10f))
                                .border(
                                    width = 1.dp,
                                    color = DopamineCyan.copy(alpha = 0.34f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // 时 (00..12)
                            DialTimeUnitWheel(
                                values = hoursList,
                                selectedValue = selectedHours,
                                onValueChange = { h ->
                                    selectedHours = h
                                    if (h == 0 && selectedMinutes == 0 && selectedSeconds == 0) {
                                        selectedMinutes = 1
                                    }
                                },
                                itemHeight = itemHeight,
                                modifier = Modifier.width(54.dp)
                            )

                            Text(
                                text = ":",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )

                            // 分 (00..59)
                            DialTimeUnitWheel(
                                values = minsSecsList,
                                selectedValue = selectedMinutes,
                                onValueChange = { m ->
                                    selectedMinutes = m
                                    if (selectedHours == 0 && m == 0 && selectedSeconds == 0) {
                                        selectedSeconds = 30
                                    }
                                },
                                itemHeight = itemHeight,
                                modifier = Modifier.width(54.dp)
                            )

                            Text(
                                text = ":",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )

                            // 秒 (00..59)
                            DialTimeUnitWheel(
                                values = minsSecsList,
                                selectedValue = selectedSeconds,
                                onValueChange = { s ->
                                    if (selectedHours == 0 && selectedMinutes == 0 && s == 0) {
                                        selectedMinutes = 1
                                        selectedSeconds = 0
                                    } else {
                                        selectedSeconds = s
                                    }
                                },
                                itemHeight = itemHeight,
                                modifier = Modifier.width(54.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = (if (isRunning) DopamineGreen else if (isPausedMidSession) DopamineAmber else TextMuted).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = when {
                            isRunning -> "● 深度心流中"
                            isPausedMidSession -> "⏸ 已暂停 (点击重置并调时)"
                            else -> "↕ 滑动滚轮 · 点击精准调时"
                        },
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            isRunning -> DopamineGreen
                            isPausedMidSession -> DopamineAmber
                            else -> TextSecondary
                        },
                        modifier = Modifier
                            .clickable(enabled = !isRunning) {
                                if (isPausedMidSession) {
                                    FocusLiveService.stop(context)
                                }
                                showDirectTimeDialog = true
                            }
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 快捷微调时长工具条 ([-15m] [-5m] [-1m] [+1m] [+5m] [+15m] [✏️精准调时])
        if (!isRunning) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(-15, -5, -1, 1, 5, 15).forEach { delta ->
                    Surface(
                        onClick = {
                            adjustMinutes(delta)
                            HapticHelper.performLightClick(context)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = DarkCard,
                        border = BorderStroke(1.dp, if (delta > 0) DopamineCyan.copy(alpha = 0.35f) else DarkBorder),
                        modifier = Modifier
                            .height(32.dp)
                            .padding(horizontal = 3.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = if (delta > 0) "+${delta}m" else "${delta}m",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (delta > 0) DopamineCyan else TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                Surface(
                    onClick = {
                        if (isPausedMidSession) {
                            FocusLiveService.stop(context)
                        }
                        showDirectTimeDialog = true
                        HapticHelper.performLightClick(context)
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = DopamineBlue.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, DopamineBlue.copy(alpha = 0.4f)),
                    modifier = Modifier.height(32.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = DopamineBlue, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("精准输入", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DopamineBlue)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. 🎛️ 专注预设选择与管理区 (预设 + 自定义预设)
        if (!isRunning) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⏱️ 专注时长预设",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    TextButton(
                        onClick = { showAddPresetDialog = true },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = DopamineCyan, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("自定义预设", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DopamineCyan)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(presets, key = { it.id }) { preset ->
                        val isSelected = (selectedHours * 60 + selectedMinutes == preset.minutes && selectedSeconds == 0)
                        val isCustom = preset.id.startsWith("preset_")
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) DopamineCyan.copy(alpha = 0.2f) else DarkCard,
                            border = BorderStroke(1.dp, if (isSelected) DopamineCyan else DarkBorder),
                            modifier = Modifier
                                .height(38.dp)
                                .combinedClickable(
                                    onClick = {
                                        applyPresetMinutes(preset.minutes)
                                        HapticHelper.performLightClick(context)
                                        Toast.makeText(context, "已切换为：${preset.name} (${preset.minutes}分钟)", Toast.LENGTH_SHORT).show()
                                    },
                                    onLongClick = {
                                        if (isCustom) {
                                            presetToDelete = preset
                                            HapticHelper.performLightClick(context)
                                        }
                                    }
                                )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Text(preset.icon, fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${preset.name} (${preset.minutes}m)",
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) DopamineCyan else TextPrimary
                                )
                                if (isCustom) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { presetToDelete = preset },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "删除预设",
                                            tint = TextMuted,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
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
                    if ((isRunning || isPausedMidSession) && boundSchedule != null) {
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
                        Toast.makeText(context, "⏸️ 专注已暂停 · 已自动记录 1 次心流打断", Toast.LENGTH_SHORT).show()
                    } else {
                        if (isPausedMidSession) {
                            FocusLiveService.resume(context)
                        } else {
                            val title = boundSchedule?.title ?: "深度专注"
                            FocusLiveService.start(
                                context = context,
                                title = title,
                                seconds = configuredTotalSeconds,
                                scheduleId = boundSchedule?.id,
                                category = boundSchedule?.category ?: "WORK"
                            )
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

        Spacer(modifier = Modifier.height(20.dp))

        // 6. 记录心流打断按钮
        OutlinedButton(
            onClick = { showInterruptionDialog = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DopamineRed),
            border = BorderStroke(1.dp, DopamineRed.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(42.dp)
        ) {
            Text(
                text = if (sessionInterruptions > 0) "⚡ 记录一次心流打断 (本轮已打断 ${sessionInterruptions} 次)" else "⚡ 记录一次心流打断",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // ==========================================
    // 弹窗 1: 选择绑定日程事件 (EventPickerModal)
    // ==========================================
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
                        items(activeSchedules, key = { it.id }) { schedule ->
                            val isSelected = schedule.id == boundScheduleId
                            Surface(
                                onClick = {
                                    boundScheduleId = schedule.id
                                    if (!isRunning) {
                                        applyPresetMinutes(schedule.estimatedMinutes.coerceIn(5, 360))
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

    // ==========================================
    // 弹窗 2: 自定义专注预设弹窗
    // ==========================================
    if (showAddPresetDialog) {
        var presetName by remember { mutableStateOf("") }
        var presetMinsText by remember { mutableStateOf("30") }
        var presetIcon by remember { mutableStateOf("⚡") }
        val icons = listOf("⚡", "🍅", "🎯", "🧠", "🚀", "📖", "💻", "☕")

        AlertDialog(
            onDismissRequest = { showAddPresetDialog = false },
            containerColor = DarkCard,
            title = { Text("新增自定义专注预设", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text("预设名称 (如: 英语速读、代码攻坚)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = presetMinsText,
                        onValueChange = { presetMinsText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("专注时长 (分钟: 1-360)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("预设图标:", fontSize = 12.sp, color = TextSecondary)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        icons.forEach { icon ->
                            Surface(
                                onClick = { presetIcon = icon },
                                shape = CircleShape,
                                color = if (presetIcon == icon) DopamineCyan.copy(alpha = 0.25f) else DarkSurface,
                                border = BorderStroke(1.dp, if (presetIcon == icon) DopamineCyan else DarkBorder),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(icon, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val m = presetMinsText.toIntOrNull() ?: 25
                        val n = presetName.trim().ifBlank { "${m}分钟专注" }
                        FocusPreferences.addPreset(context, n, m, presetIcon)
                        presets = FocusPreferences.getPresets(context)
                        applyPresetMinutes(m)
                        showAddPresetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DopamineCyan)
                ) {
                    Text("保存预设", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPresetDialog = false }) { Text("取消", color = TextSecondary) }
            }
        )
    }

    // 弹窗 2.1: 精准设定专注时长弹窗
    if (showDirectTimeDialog) {
        var inputHours by remember { mutableIntStateOf(selectedHours) }
        var inputMinutes by remember { mutableIntStateOf(selectedMinutes) }
        var inputSeconds by remember { mutableIntStateOf(selectedSeconds) }

        AlertDialog(
            onDismissRequest = { showDirectTimeDialog = false },
            containerColor = DarkCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⏱️ 精准设定专注时长", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // 当前选定预览
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Text("设定时长预览", fontSize = 11.sp, color = TextSecondary)
                            Text(
                                text = "%02d:%02d:%02d".format(inputHours, inputMinutes, inputSeconds),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = DopamineCyan,
                                letterSpacing = (-0.5).sp,
                                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                            )
                            val totalM = inputHours * 60 + inputMinutes
                            Text("合计共 $totalM 分钟", fontSize = 11.5.sp, color = TextMuted)
                        }
                    }

                    // 常用时长一键填充
                    Text("常用推荐时长:", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(10, 15, 20, 25, 30, 45, 60, 90, 120).forEach { mins ->
                            val isChipSelected = (inputHours * 60 + inputMinutes == mins && inputSeconds == 0)
                            Surface(
                                onClick = {
                                    inputHours = mins / 60
                                    inputMinutes = mins % 60
                                    inputSeconds = 0
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isChipSelected) DopamineCyan.copy(alpha = 0.2f) else DarkSurface,
                                border = BorderStroke(1.dp, if (isChipSelected) DopamineCyan else DarkBorder),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text("${mins}m", fontSize = 11.sp, color = if (isChipSelected) DopamineCyan else TextPrimary)
                                }
                            }
                        }
                    }

                    // 小时与分钟微调滑块与加减按钮
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("小时: $inputHours 小时", fontSize = 12.5.sp, color = TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = { if (inputHours > 0) inputHours-- }, modifier = Modifier.size(28.dp)) {
                                    Text("－", color = TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                IconButton(onClick = { if (inputHours < 12) inputHours++ }, modifier = Modifier.size(28.dp)) {
                                    Text("＋", color = DopamineCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Slider(
                            value = inputHours.toFloat(),
                            onValueChange = { inputHours = it.toInt() },
                            valueRange = 0f..12f,
                            steps = 11,
                            colors = SliderDefaults.colors(
                                thumbColor = DopamineCyan,
                                activeTrackColor = DopamineCyan,
                                inactiveTrackColor = DarkSurface
                            )
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("分钟: $inputMinutes 分钟", fontSize = 12.5.sp, color = TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = { if (inputMinutes > 0) inputMinutes-- }, modifier = Modifier.size(28.dp)) {
                                    Text("－", color = TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                IconButton(onClick = { if (inputMinutes < 59) inputMinutes++ }, modifier = Modifier.size(28.dp)) {
                                    Text("＋", color = DopamineCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Slider(
                            value = inputMinutes.toFloat(),
                            onValueChange = { inputMinutes = it.toInt() },
                            valueRange = 0f..59f,
                            steps = 58,
                            colors = SliderDefaults.colors(
                                thumbColor = DopamineCyan,
                                activeTrackColor = DopamineCyan,
                                inactiveTrackColor = DarkSurface
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val total = inputHours * 3600 + inputMinutes * 60 + inputSeconds
                        if (total <= 0) {
                            inputMinutes = 1
                        }
                        selectedHours = inputHours
                        selectedMinutes = if (inputHours == 0 && inputMinutes == 0 && inputSeconds == 0) 1 else inputMinutes
                        selectedSeconds = inputSeconds
                        showDirectTimeDialog = false
                        Toast.makeText(context, "✓ 专注时长已设定为: %02d:%02d:%02d".format(selectedHours, selectedMinutes, selectedSeconds), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DopamineCyan)
                ) {
                    Text("应用设定", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDirectTimeDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            }
        )
    }

    // 弹窗 2.2: 删除自定义预设确认弹窗
    if (presetToDelete != null) {
        val targetPreset = presetToDelete
        AlertDialog(
            onDismissRequest = { presetToDelete = null },
            containerColor = DarkCard,
            title = { Text("删除自定义预设", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text("是否确定删除自定义预设「${targetPreset?.name} (${targetPreset?.minutes}m)」？", color = TextSecondary, fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        targetPreset?.let { p ->
                            FocusPreferences.deletePreset(context, p.id)
                            presets = FocusPreferences.getPresets(context)
                            Toast.makeText(context, "✓ 已删除预设: ${p.name}", Toast.LENGTH_SHORT).show()
                        }
                        presetToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DopamineRed)
                ) {
                    Text("删除", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { presetToDelete = null }) {
                    Text("取消", color = TextSecondary)
                }
            }
        )
    }

    // ==========================================
    // 弹窗 3: 记录打断弹窗
    // ==========================================
    if (showInterruptionDialog) {
        RecordInterruptionDialog(
            onDismiss = { showInterruptionDialog = false },
            onConfirm = { type, note ->
                val entity = InterruptionEntity(
                    scheduleId = boundSchedule?.id,
                    type = type,
                    durationSeconds = 60,
                    timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
                    note = note
                )
                onRecordInterruption(entity)
                if (isRunning || isPausedMidSession) {
                    FocusLiveService.recordInterruption(context)
                }
                showInterruptionDialog = false
            }
        )
    }

    // ==========================================
    // 弹窗 4: 专注历史记录与日/周/月/年分析弹窗
    // ==========================================
    if (showHistoryDialog) {
        FocusHistoryAndAnalysisDialog(
            records = focusRecords,
            onDismiss = { showHistoryDialog = false }
        )
    }
}

// ==========================================
// 🪟 极致小巧简洁的画中画胶囊 (PiP View)
// ==========================================
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
            shape = RoundedCornerShape(12.dp),
            color = DarkCard,
            border = BorderStroke(1.dp, if (isRunning) DopamineBlue.copy(alpha = 0.5f) else DarkBorder),
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧：状态圆点 + 紧凑倒计时
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(if (isRunning) DopamineGreen else DopamineAmber, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = timeFormatted,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        letterSpacing = (-0.5).sp
                    )
                }

                // 右侧：超精炼重置与播放按钮
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onReset,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "重置",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    FilledIconButton(
                        onClick = onTogglePlay,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isRunning) DopamineAmber else DopamineBlue
                        ),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isRunning) "暂停" else "开始",
                            tint = DarkBg,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 📊 专注历史记录与日/周/月/年分析弹窗
// ==========================================
@Composable
fun FocusHistoryAndAnalysisDialog(
    records: List<FocusRecordEntity>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: 历史流水, 1: 周期智能分析
    var selectedTimeframe by remember { mutableStateOf("日") } // "日", "周", "月", "年"
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisResultText by remember { mutableStateOf<String?>(null) }

    val isAiEnabled = remember { AiPreferences.isAiEnabled(context) }
    val isAiConfigured = remember { AiPreferences.isConfigured(context) }

    val now = LocalDateTime.now()

    // 根据选定的周期筛选记录
    val filteredRecords = remember(records, selectedTimeframe) {
        val today = LocalDate.now()
        when (selectedTimeframe) {
            "日" -> {
                records.filter {
                    try {
                        val dt = LocalDateTime.parse(it.startTime, DateTimeFormatter.ISO_DATE_TIME)
                        dt.toLocalDate() == today
                    } catch (_: Exception) { false }
                }
            }
            "周" -> {
                val weekFields = WeekFields.of(Locale.getDefault())
                val currentWeek = today.get(weekFields.weekOfYear())
                val currentYear = today.year
                records.filter {
                    try {
                        val dt = LocalDateTime.parse(it.startTime, DateTimeFormatter.ISO_DATE_TIME)
                        dt.year == currentYear && dt.toLocalDate().get(weekFields.weekOfYear()) == currentWeek
                    } catch (_: Exception) { false }
                }
            }
            "月" -> {
                val currentMonth = today.monthValue
                val currentYear = today.year
                records.filter {
                    try {
                        val dt = LocalDateTime.parse(it.startTime, DateTimeFormatter.ISO_DATE_TIME)
                        dt.year == currentYear && dt.monthValue == currentMonth
                    } catch (_: Exception) { false }
                }
            }
            "年" -> {
                val currentYear = today.year
                records.filter {
                    try {
                        val dt = LocalDateTime.parse(it.startTime, DateTimeFormatter.ISO_DATE_TIME)
                        dt.year == currentYear
                    } catch (_: Exception) { false }
                }
            }
            else -> records
        }
    }

    val totalMinutes = filteredRecords.sumOf { it.durationMinutes }
    val totalSessions = filteredRecords.size
    val avgMinutes = if (totalSessions > 0) totalMinutes / totalSessions else 0
    val totalInterruptions = filteredRecords.sumOf { it.interruptionCount }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        modifier = Modifier.fillMaxWidth(0.96f).heightIn(max = 680.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📈 专注历史与多维分析", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = TextMuted)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Tab 切换：历史记录 VS 周期分析
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkSurface,
                    contentColor = DopamineCyan,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("专注记录 (${records.size})", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("周期智能分析", fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == 0) {
                    // 1. 历史记录明细流
                    if (records.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(260.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📭 暂无专注记录，点击开始专注沉浸心流吧！", fontSize = 12.sp, color = TextMuted)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().height(360.dp)
                        ) {
                            items(records, key = { it.id }) { item ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = DarkSurface,
                                    border = BorderStroke(1.dp, DarkBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = item.taskTitle,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = if (item.status == "COMPLETED") DopamineGreen.copy(alpha = 0.2f) else DopamineAmber.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = if (item.status == "COMPLETED") "达成" else "中断",
                                                        fontSize = 9.sp,
                                                        color = if (item.status == "COMPLETED") DopamineGreen else DopamineAmber,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = item.startTime.take(16).replace("T", " "),
                                                fontSize = 10.5.sp,
                                                color = TextMuted,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "${item.durationMinutes} 分钟",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = DopamineCyan
                                            )
                                            Text(
                                                text = item.category,
                                                fontSize = 10.sp,
                                                color = TextSecondary,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 2. 周期智能分析 (日/周/月/年)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // 周期选择器
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("日", "周", "月", "年").forEach { tf ->
                                val isSelected = selectedTimeframe == tf
                                Surface(
                                    onClick = {
                                        selectedTimeframe = tf
                                        analysisResultText = null
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) DopamineCyan.copy(alpha = 0.2f) else DarkSurface,
                                    border = BorderStroke(1.dp, if (isSelected) DopamineCyan else DarkBorder),
                                    modifier = Modifier.weight(1f).height(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${tf}分析",
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) DopamineCyan else TextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 核心数据指标矩阵
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkSurface,
                                modifier = Modifier.weight(1f).padding(vertical = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("总时长", fontSize = 10.sp, color = TextMuted)
                                    Text("${totalMinutes}m", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DopamineCyan)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkSurface,
                                modifier = Modifier.weight(1f).padding(vertical = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("专注次数", fontSize = 10.sp, color = TextMuted)
                                    Text("${totalSessions}次", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkSurface,
                                modifier = Modifier.weight(1f).padding(vertical = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("平均单次", fontSize = 10.sp, color = TextMuted)
                                    Text("${avgMinutes}m", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DopamineGreen)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 触发 AI 分析按钮
                        Button(
                            onClick = {
                                if (!isAiEnabled || !isAiConfigured) {
                                    Toast.makeText(context, "请先在「系统设置」中开启并配置自定义数据分析接口", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                coroutineScope.launch {
                                    isAnalyzing = true
                                    analysisResultText = null
                                    val catSummary = filteredRecords.groupBy { it.category }
                                        .map { "${it.key}: ${it.value.sumOf { r -> r.durationMinutes }}m (${it.value.size}次)" }
                                        .joinToString()
                                    val topTasks = filteredRecords.map { it.taskTitle }.distinct().take(5)

                                    val res = AiService.generateFocusAnalysis(
                                        context = context,
                                        timeframe = selectedTimeframe,
                                        periodLabel = "${now.format(DateTimeFormatter.ISO_LOCAL_DATE)} 当前${selectedTimeframe}周期",
                                        totalSessions = totalSessions,
                                        totalMinutes = totalMinutes,
                                        avgMinutesPerSession = avgMinutes,
                                        interruptionCount = totalInterruptions,
                                        categoryBreakdown = catSummary.ifBlank { "单项深度工作" },
                                        topTasks = topTasks
                                    )
                                    isAnalyzing = false
                                    when (res) {
                                        is AiResult.Success -> analysisResultText = res.data
                                        is AiResult.Error -> analysisResultText = "分析生成失败: ${res.errorMessage}"
                                    }
                                }
                            },
                            enabled = !isAnalyzing,
                            colors = ButtonDefaults.buttonColors(containerColor = DopaminePurple),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(38.dp)
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("正在生成${selectedTimeframe}心流报告...", fontSize = 11.sp, color = Color.White)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("智能${selectedTimeframe}分析与节律诊断", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (analysisResultText != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkSurface,
                                border = BorderStroke(1.dp, DopaminePurple.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = analysisResultText!!,
                                    fontSize = 11.5.sp,
                                    color = TextPrimary,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = TextSecondary)
            }
        }
    )
}

// ==========================================
// 打断弹窗
// ==========================================
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
private fun DialTimeUnitWheel(
    values: List<Int>,
    selectedValue: Int,
    onValueChange: (Int) -> Unit,
    itemHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val initialIdx = remember { values.indexOf(selectedValue).coerceAtLeast(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIdx)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val view = LocalView.current

    val centerIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.visibleItemsInfo.isEmpty()) {
                listState.firstVisibleItemIndex
            } else {
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                layoutInfo.visibleItemsInfo.minByOrNull { item ->
                    kotlin.math.abs((item.offset + item.size / 2) - viewportCenter)
                }?.index ?: listState.firstVisibleItemIndex
            }
        }
    }

    var isProgrammaticScroll by remember { mutableStateOf(false) }

    // 当外部 selectedValue 改变时（例如点击预设、快捷微调按钮、弹窗精准输入）
    // 立即通过 isProgrammaticScroll 标记驱动滚轮平滑滚动或瞬间跳转到目标项，严禁触发 onValueChange 反向截断！
    LaunchedEffect(selectedValue) {
        val targetIdx = values.indexOf(selectedValue)
        if (targetIdx >= 0 && targetIdx != centerIndex) {
            isProgrammaticScroll = true
            try {
                val diff = kotlin.math.abs(targetIdx - centerIndex)
                if (diff > 8) {
                    listState.scrollToItem(targetIdx)
                } else {
                    listState.animateScrollToItem(targetIdx)
                }
            } finally {
                isProgrammaticScroll = false
            }
        }
    }

    // 只有在用户主动拖拽/甩动停止后，才派发最终落点数值
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (!isScrolling && !isProgrammaticScroll) {
                    val currentVal = values[centerIndex.coerceIn(0, values.lastIndex)]
                    if (currentVal != selectedValue) {
                        onValueChange(currentVal)
                    }
                }
            }
    }

    // 用户手动滚动过程中提供微触觉反馈
    LaunchedEffect(centerIndex) {
        if (listState.isScrollInProgress && !isProgrammaticScroll) {
            try {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            } catch (_: Exception) {}
        }
    }

    LazyColumn(
        state = listState,
        flingBehavior = flingBehavior,
        contentPadding = PaddingValues(vertical = itemHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.height(itemHeight * 3)
    ) {
        items(values) { v ->
            val isSelected = v == selectedValue
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .clickable {
                        if (v != selectedValue) {
                            onValueChange(v)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "%02d".format(v),
                    fontSize = if (isSelected) 30.sp else 16.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (isSelected) TextPrimary else TextMuted.copy(alpha = 0.42f),
                    letterSpacing = (-0.5).sp
                )
            }
        }
    }
}

