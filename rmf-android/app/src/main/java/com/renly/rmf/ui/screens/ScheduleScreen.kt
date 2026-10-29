package com.renly.rmf.ui.screens

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.renly.rmf.RmfApplication
import com.renly.rmf.data.local.entity.ScheduleEntity
import com.renly.rmf.data.local.entity.ScheduleTagEntity
import com.renly.rmf.data.local.entity.TimeMarkerEntity
import com.renly.rmf.domain.model.SubTaskItem
import com.renly.rmf.domain.service.CalendarSyncResult
import com.renly.rmf.domain.service.GoogleCalendarSyncService
import com.renly.rmf.domain.service.GoogleDrivePreferences
import com.renly.rmf.domain.service.NaturalLanguageScheduleParser
import com.renly.rmf.domain.service.ParsedScheduleResult
import com.renly.rmf.domain.service.ReminderType
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.UUID

private val FORMATTER_HH_MM = DateTimeFormatter.ofPattern("HH:mm")
private val FORMATTER_MONTH_DAY = DateTimeFormatter.ofPattern("M月d日")
private val FORMATTER_YEAR_MONTH_DAY = DateTimeFormatter.ofPattern("yyyy年M月d日")
private val FORMATTER_MONTH_DAY_WEEKDAY = DateTimeFormatter.ofPattern("M月d日 EEEE")

enum class ScheduleViewMode(val title: String, val icon: String) {
    DAY_GRID("日网格", "⏱️"),
    WEEK_GRID("周网格", "📅"),
    LIST("清单流", "📋"),
    EISENHOWER("四象限", "🎯")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    schedulesFlow: Flow<List<ScheduleEntity>>,
    tagsFlow: Flow<List<ScheduleTagEntity>> = kotlinx.coroutines.flow.flowOf(emptyList()),
    timeMarkersFlow: Flow<List<TimeMarkerEntity>> = kotlinx.coroutines.flow.flowOf(emptyList()),
    onToggleStatus: (ScheduleEntity) -> Unit,
    onAddSchedule: (String, String, String, Int, String, String, Int, String) -> Unit,
    onUpdateSchedule: (ScheduleEntity) -> Unit = {},
    onDeleteSchedule: (String) -> Unit = {},
    onAddTimeMarker: (TimeMarkerEntity) -> Unit = {},
    onUpdateTimeMarker: (TimeMarkerEntity) -> Unit = {},
    onDeleteTimeMarker: (String) -> Unit = {},
    onOpenQuickCapture: () -> Unit = {},
    onNavigateToNotes: () -> Unit = {},
    onNavigateToSummary: () -> Unit = {},
    onNavigateToFitness: () -> Unit = {},
    onAddTag: (ScheduleTagEntity) -> Unit = {},
    onDeleteTag: (ScheduleTagEntity) -> Unit = {}
) {
    val schedules by schedulesFlow.collectAsState(initial = emptyList())
    val dbTags by tagsFlow.collectAsState(initial = emptyList())
    val timeMarkers by timeMarkersFlow.collectAsState(initial = emptyList())
    val defaultTags = remember {
        listOf(
            ScheduleTagEntity(id = "1", name = "工作", colorHex = "#38BDF8", createdAt = ""),
            ScheduleTagEntity(id = "2", name = "学习", colorHex = "#818CF8", createdAt = ""),
            ScheduleTagEntity(id = "3", name = "日常", colorHex = "#34D399", createdAt = ""),
            ScheduleTagEntity(id = "4", name = "运动", colorHex = "#F59E0B", createdAt = ""),
            ScheduleTagEntity(id = "5", name = "会议", colorHex = "#EC4899", createdAt = ""),
            ScheduleTagEntity(id = "6", name = "娱乐", colorHex = "#10B981", createdAt = "")
        )
    }
    val availableTags = if (dbTags.isNotEmpty()) dbTags else defaultTags
    val defaultAccent = DopamineBlue
    val tagColorMap = remember(availableTags, defaultAccent) {
        availableTags.associate { it.name to parseHexColor(it.colorHex, defaultAccent) }
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    var isTableLocked by rememberSaveable { mutableStateOf(true) }

    var currentViewMode by remember { mutableStateOf(ScheduleViewMode.DAY_GRID) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var quickInputText by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showBacklogDialog by remember { mutableStateOf(false) }
    var showGoToDateDialog by remember { mutableStateOf(false) }
    var detailItem by remember { mutableStateOf<ScheduleEntity?>(null) }
    var editingSchedule by remember { mutableStateOf<ScheduleEntity?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showCalendarSyncDialog by remember { mutableStateOf(false) }
    var showDriveSyncDialog by remember { mutableStateOf(false) }
    var showLanSyncDialog by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var showManageTagsDialog by remember { mutableStateOf(false) }
    var showTimeMarkerDialog by remember { mutableStateOf(false) }
    var showRoutineTemplateDialog by remember { mutableStateOf(false) }
    var defaultQuadrantForNewSchedule by remember { mutableStateOf("Q2") }
    var showDailyRitualDialog by remember { mutableStateOf(false) }
    var ritualMode by remember { mutableStateOf("MORNING") }

    // 过滤掉待办箱中的项目用于日历排期统计
    val calendarSchedules = remember(schedules) { schedules.filter { it.status != "BACKLOG" && (it.isBacklog ?: 0) == 0 } }
    val backlogSchedules = remember(schedules) { schedules.filter { it.status == "BACKLOG" || (it.isBacklog ?: 0) == 1 } }

    val completedCount = remember(calendarSchedules) { calendarSchedules.count { it.status == "COMPLETED" } }
    val totalCount = calendarSchedules.size
    val completionPercent = remember(totalCount, completedCount) {
        if (totalCount > 0) (completedCount * 100 / totalCount) else 0
    }

    val todayStartIso = remember { LocalDate.now().atStartOfDay().format(DateTimeFormatter.ISO_DATE_TIME) }
    val pastUnfinishedTasks = remember(schedules, todayStartIso) {
        schedules.filter {
            it.isDeleted == 0 &&
            it.status != "COMPLETED" &&
            it.status != "ABANDONED" &&
            it.status != "BACKLOG" &&
            (it.isBacklog ?: 0) == 0 &&
            it.startTime < todayStartIso
        }
    }
    var isSpilloverBannerDismissed by remember { mutableStateOf(false) }
    var isGapFillBannerDismissed by remember { mutableStateOf(false) }
    val recommendedGapSlot = remember(schedules, backlogSchedules, selectedDate) {
        val gaps = com.renly.rmf.domain.service.GapTimeBoxingService.findDailyFreeGaps(
            date = selectedDate,
            daySchedules = schedules,
            backlogItems = backlogSchedules
        )
        gaps.firstOrNull { it.suggestedBacklog != null }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶部操作区 (使用精炼紧凑边距，彻底消除上方过宽留白)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
            ) {
                Spacer(modifier = Modifier.height(2.dp))

                // 1. 顶部 Header (日期 + 标题 + 核心小窗口入口)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 EEEE")),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = DopamineBlue
                        )
                        Text(
                            text = "今日时间流",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        // ☁️ Google Drive 极简云端同步状态指示灯 (极简小云朵 / 状态圆点)
                        val syncStatusInfo by com.renly.rmf.domain.service.GoogleDriveSyncStateHolder.status.collectAsState()
                        val isCloudSyncing = syncStatusInfo.state == com.renly.rmf.domain.service.CloudSyncState.SYNCING
                        val syncSpinTransition = rememberInfiniteTransition(label = "syncSpin")
                        val syncAngle by syncSpinTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "syncSpinAngle"
                        )
                        Surface(
                            onClick = { showDriveSyncDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            color = DarkSurface,
                            border = BorderStroke(
                                1.dp,
                                when (syncStatusInfo.state) {
                                    com.renly.rmf.domain.service.CloudSyncState.SYNCING -> DopamineBlue.copy(alpha = 0.8f)
                                    com.renly.rmf.domain.service.CloudSyncState.SUCCESS -> DopamineGreen.copy(alpha = 0.5f)
                                    com.renly.rmf.domain.service.CloudSyncState.ERROR -> DopamineAmber.copy(alpha = 0.8f)
                                    com.renly.rmf.domain.service.CloudSyncState.IDLE -> DarkBorder
                                }
                            ),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                if (isCloudSyncing) {
                                    Text(
                                        "🔄",
                                        fontSize = 10.5.sp,
                                        modifier = Modifier.graphicsLayer { rotationZ = syncAngle }
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("同步中", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = DopamineBlue)
                                } else {
                                    val dotColor = when (syncStatusInfo.state) {
                                        com.renly.rmf.domain.service.CloudSyncState.SUCCESS -> DopamineGreen
                                        com.renly.rmf.domain.service.CloudSyncState.ERROR -> DopamineAmber
                                        com.renly.rmf.domain.service.CloudSyncState.IDLE -> TextMuted
                                        else -> DopamineBlue
                                    }
                                    Text("☁️", fontSize = 10.5.sp)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = when (syncStatusInfo.state) {
                                            com.renly.rmf.domain.service.CloudSyncState.SUCCESS -> "已同步"
                                            com.renly.rmf.domain.service.CloudSyncState.ERROR -> "待同步"
                                            com.renly.rmf.domain.service.CloudSyncState.IDLE -> "云同步"
                                            else -> "同步中"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = dotColor
                                    )
                                }
                            }
                        }

                        // ⚡ 闪念胶囊速记入口
                        Surface(
                            onClick = onOpenQuickCapture,
                            shape = RoundedCornerShape(16.dp),
                            color = DarkSurface,
                            border = BorderStroke(1.dp, DopamineBlue.copy(alpha = 0.4f)),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text("⚡", fontSize = 10.5.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("闪念", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = DopamineBlue)
                            }
                        }

                        // 📍 真实时间标记与偏差分析入口 (TimeMarker)
                        val dayMarkerCount = remember(timeMarkers, selectedDate) {
                            timeMarkers.count { it.isDeleted == 0 && (it.date == selectedDate.toString() || it.startTime.startsWith(selectedDate.toString())) }
                        }
                        Surface(
                            onClick = { showTimeMarkerDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            color = DarkSurface,
                            border = BorderStroke(1.dp, DopamineCyan.copy(alpha = 0.5f)),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text("📍", fontSize = 10.5.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (dayMarkerCount > 0) "标记 $dayMarkerCount" else "标记",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DopamineCyan
                                )
                            }
                        }

                        // 🌅 晨间规划仪式入口 (Sunsama 风格)
                        Surface(
                            onClick = {
                                ritualMode = "MORNING"
                                showDailyRitualDialog = true
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = DopamineAmber.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, DopamineAmber.copy(alpha = 0.5f)),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text("🌅", fontSize = 10.5.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("晨间", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = DopamineAmber)
                            }
                        }

                        // 🌇 晚间关机仪式入口 (Sunsama 风格)
                        Surface(
                            onClick = {
                                ritualMode = "SHUTDOWN"
                                showDailyRitualDialog = true
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = DopaminePurple.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, DopaminePurple.copy(alpha = 0.5f)),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text("🌇", fontSize = 10.5.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("关机", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = DopaminePurple)
                            }
                        }

                        // 🪄 智能作息模板预设入口 (Routine Templates)
                        Surface(
                            onClick = { showRoutineTemplateDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            color = DarkSurface,
                            border = BorderStroke(1.dp, DopaminePurple.copy(alpha = 0.5f)),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text("🪄", fontSize = 10.5.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("模版", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = DopaminePurple)
                            }
                        }

                        // 📝 灵感便签与随记速记入口
                        Surface(
                            onClick = onNavigateToNotes,
                            shape = RoundedCornerShape(16.dp),
                            color = DarkSurface,
                            border = BorderStroke(1.dp, DopamineAmber.copy(alpha = 0.5f)),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text("📝", fontSize = 10.5.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("随记", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = DopamineAmber)
                            }
                        }

                        // 🗓️ 日期快速跳转弹窗入口 (GoToDateModal)
                        Surface(
                            onClick = { showGoToDateDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            color = DarkSurface,
                            border = BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text("🗓️", fontSize = 10.5.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("跳转", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            }
                        }

                        // 📅 Google 日历同步与导出入口
                        Surface(
                            onClick = { showCalendarSyncDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            color = DarkSurface,
                            border = BorderStroke(1.dp, DopamineGreen.copy(alpha = 0.5f)),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text("📅", fontSize = 10.5.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("日历", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = DopamineGreen)
                            }
                        }

                        // 📥 待办箱 (Backlog 任务池弹窗入口)
                        Surface(
                            onClick = { showBacklogDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            color = DarkSurface,
                            border = BorderStroke(1.dp, DopamineAmber.copy(alpha = 0.5f)),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text("📥", fontSize = 10.5.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (backlogSchedules.isNotEmpty()) "待办 ${backlogSchedules.size}" else "待办",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DopamineAmber
                                )
                            }
                        }

                        // ⏱️ 24小时作息时钟与效能分析入口
                        Surface(
                            onClick = onNavigateToSummary,
                            shape = RoundedCornerShape(16.dp),
                            color = DarkSurface,
                            border = BorderStroke(1.dp, DopaminePurple.copy(alpha = 0.5f)),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text("⏱️", fontSize = 10.5.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("时钟", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = DopaminePurple)
                            }
                        }

                        // 🏋️ 每日健身计划入口
                        Surface(
                            onClick = onNavigateToFitness,
                            shape = RoundedCornerShape(16.dp),
                            color = DarkSurface,
                            border = BorderStroke(1.dp, DopamineCyan.copy(alpha = 0.5f)),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text("🏋️", fontSize = 10.5.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("健身", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = DopamineCyan)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                // 2. 电脑版多维视图模式切换卡片 (周网格 / 日网格 / 清单流)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(2.5.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        ScheduleViewMode.values().forEach { mode ->
                            val isSelected = currentViewMode == mode
                            Surface(
                                onClick = { currentViewMode = mode },
                                shape = RoundedCornerShape(7.dp),
                                color = if (isSelected) DopamineBlue.copy(alpha = 0.2f) else Color.Transparent,
                                border = BorderStroke(1.dp, if (isSelected) DopamineBlue.copy(alpha = 0.7f) else Color.Transparent),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(31.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(mode.icon, fontSize = 11.5.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = mode.title,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) DopamineBlue else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // 过去未完成排期智能自愈顺延横幅 (平滑展开/折叠动效)
            AnimatedVisibility(
                visible = pastUnfinishedTasks.isNotEmpty() && !isSpilloverBannerDismissed && selectedDate == java.time.LocalDate.now(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DopamineAmber.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, DopamineAmber.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text("⚡", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "历史遗留 ${pastUnfinishedTasks.size} 项未完成排期",
                                fontSize = 11.5.sp,
                                color = DopamineAmber,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val count = com.renly.rmf.domain.service.EstimationVarianceService.spilloverTasksToToday(com.renly.rmf.data.local.RmfDatabase.getInstance(context), pastUnfinishedTasks)
                                        android.widget.Toast.makeText(context, "✓ 已自愈顺延 ${count} 项任务至今日排期！", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DopamineAmber),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text("顺延今日", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val count = com.renly.rmf.domain.service.EstimationVarianceService.movePastTasksToBacklog(com.renly.rmf.data.local.RmfDatabase.getInstance(context), pastUnfinishedTasks)
                                        android.widget.Toast.makeText(context, "✓ 已将 ${count} 项未完成任务移入待办箱！", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, TextMuted),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text("入待办", fontSize = 10.5.sp, color = TextSecondary)
                            }
                            IconButton(
                                onClick = { isSpilloverBannerDismissed = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Text("✕", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                    }
                }
            }

            // 碎片空隙时间盒智能填空推荐横幅 (平滑展开/折叠动效)
            AnimatedVisibility(
                visible = recommendedGapSlot != null && !isGapFillBannerDismissed,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DopamineGreen.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, DopamineGreen.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text("💡", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = recommendedGapSlot?.summaryText ?: "",
                                fontSize = 11.5.sp,
                                color = DopamineGreen,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val slot = recommendedGapSlot ?: return@launch
                                        val success = com.renly.rmf.domain.service.GapTimeBoxingService.fillGapWithBacklog(
                                            com.renly.rmf.data.local.RmfDatabase.getInstance(context),
                                            slot
                                        )
                                        if (success) {
                                            android.widget.Toast.makeText(context, "✓ 已将待办「${slot.suggestedBacklog?.title}」填入时间盒！", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DopamineGreen),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text("⚡ 填入时间盒", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { isGapFillBannerDismissed = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Text("✕", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                    }
                }
            }

            // 3. 核心视图区（全屏自适应，紧贴底部导航栏）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(
                        start = if (currentViewMode == ScheduleViewMode.LIST) 10.dp else 4.dp,
                        end = if (currentViewMode == ScheduleViewMode.LIST) 10.dp else 4.dp,
                        bottom = 0.dp
                    )
            ) {
                when (currentViewMode) {
                    ScheduleViewMode.WEEK_GRID -> {
                        ScheduleWeekGridView(
                            schedules = calendarSchedules,
                            backlogItems = backlogSchedules,
                            selectedDate = selectedDate,
                            availableTags = availableTags,
                            tagColorMap = tagColorMap,
                            isLocked = isTableLocked,
                            onSelectDate = { selectedDate = it },
                            onItemClick = { detailItem = it },
                            onScheduleBacklog = { item, dt ->
                                if (isTableLocked) {
                                    Toast.makeText(context, "🔒 表格已锁定防误触，请先点击右下角小锁解锁", Toast.LENGTH_SHORT).show()
                                } else {
                                    val startStr = dt.format(DateTimeFormatter.ISO_DATE_TIME)
                                    val endStr = dt.plusMinutes(item.estimatedMinutes.toLong()).format(DateTimeFormatter.ISO_DATE_TIME)
                                    val updated = item.copy(
                                        status = "PENDING",
                                        startTime = startStr,
                                        endTime = endStr,
                                        updatedAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString()
                                    )
                                    onUpdateSchedule(updated)
                                }
                            },
                            onAddScheduleWithRange = { title, workType, category, mins, startStr, endStr, reminderMinutes, reminderType ->
                                if (isTableLocked) {
                                    Toast.makeText(context, "🔒 表格已锁定防误触，请先点击右下角小锁解锁", Toast.LENGTH_SHORT).show()
                                } else {
                                    onAddSchedule(title, workType, category, mins, startStr, endStr, reminderMinutes, reminderType)
                                }
                            },
                            onUpdateSchedule = { updated ->
                                if (isTableLocked) {
                                    Toast.makeText(context, "🔒 表格已锁定防误触，请先点击右下角小锁解锁", Toast.LENGTH_SHORT).show()
                                } else {
                                    onUpdateSchedule(updated)
                                }
                            }
                        )
                    }

                    ScheduleViewMode.DAY_GRID -> {
                        ScheduleDayGridView(
                            schedules = calendarSchedules,
                            backlogItems = backlogSchedules,
                            timeMarkers = timeMarkers,
                            onMarkerClick = { showTimeMarkerDialog = true },
                            onUpdateTimeMarker = onUpdateTimeMarker,
                            selectedDate = selectedDate,
                            availableTags = availableTags,
                            tagColorMap = tagColorMap,
                            isLocked = isTableLocked,
                            onSelectDate = { selectedDate = it },
                            onItemClick = { detailItem = it },
                            onScheduleBacklog = { item, dt ->
                                if (isTableLocked) {
                                    Toast.makeText(context, "🔒 表格已锁定防误触，请先点击右下角小锁解锁", Toast.LENGTH_SHORT).show()
                                } else {
                                    val startStr = dt.format(DateTimeFormatter.ISO_DATE_TIME)
                                    val endStr = dt.plusMinutes(item.estimatedMinutes.toLong()).format(DateTimeFormatter.ISO_DATE_TIME)
                                    val updated = item.copy(
                                        status = "PENDING",
                                        startTime = startStr,
                                        endTime = endStr,
                                        updatedAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString()
                                    )
                                    onUpdateSchedule(updated)
                                }
                            },
                            onAddNewScheduleAtHour = { hour ->
                                if (isTableLocked) {
                                    Toast.makeText(context, "🔒 表格已锁定防误触，请先点击右下角小锁解锁", Toast.LENGTH_SHORT).show()
                                } else {
                                    val dt = selectedDate.atTime(hour, 0)
                                    val startStr = dt.format(DateTimeFormatter.ISO_DATE_TIME)
                                    val endStr = dt.plusMinutes(45).format(DateTimeFormatter.ISO_DATE_TIME)
                                    val defaultCat = availableTags.firstOrNull()?.name ?: "工作"
                                    onAddSchedule("新任务", "DEEP_WORK", defaultCat, 45, startStr, endStr, 10, ReminderType.ALL.code)
                                }
                            },
                            onAddScheduleWithRange = { title, workType, category, mins, startStr, endStr, reminderMinutes, reminderType ->
                                if (isTableLocked) {
                                    Toast.makeText(context, "🔒 表格已锁定防误触，请先点击右下角小锁解锁", Toast.LENGTH_SHORT).show()
                                } else {
                                    onAddSchedule(title, workType, category, mins, startStr, endStr, reminderMinutes, reminderType)
                                }
                            },
                            onUpdateSchedule = { updated ->
                                if (isTableLocked) {
                                    Toast.makeText(context, "🔒 表格已锁定防误触，请先点击右下角小锁解锁", Toast.LENGTH_SHORT).show()
                                } else {
                                    onUpdateSchedule(updated)
                                }
                            }
                        )
                    }

                    ScheduleViewMode.LIST -> {
                        Column(modifier = Modifier.fillMaxSize()) {

                    // 经典清单流视图
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = DarkCard,
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "交付进度",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "$completedCount / $totalCount 项 ($completionPercent%)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (completionPercent == 100) DopamineGreen else DopamineBlue
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else 0f },
                                color = DopamineBlue,
                                trackColor = DarkSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 明确清晰的创建清单任务输入卡片 (消除歧义输入框)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = DarkCard,
                        border = BorderStroke(1.dp, DopamineBlue.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddCircle,
                                    contentDescription = null,
                                    tint = DopamineBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "新建清单任务",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "⚡ 支持自然语言识别",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = quickInputText,
                                    onValueChange = { quickInputText = it },
                                    placeholder = {
                                        Text(
                                            "输入待办内容，如：下午3点攻坚架构 45m #技术",
                                            fontSize = 12.sp,
                                            color = TextMuted
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 46.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = DopamineBlue,
                                        unfocusedBorderColor = DarkBorder,
                                        focusedContainerColor = DarkSurface,
                                        unfocusedContainerColor = DarkSurface,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = {
                                        if (quickInputText.isNotBlank()) {
                                            val parsed = NaturalLanguageScheduleParser.parse(quickInputText)
                                            onAddSchedule(
                                                parsed.title,
                                                "DEEP_WORK",
                                                parsed.category,
                                                parsed.durationMinutes,
                                                parsed.startTime.format(DateTimeFormatter.ISO_DATE_TIME),
                                                parsed.endTime.format(DateTimeFormatter.ISO_DATE_TIME),
                                                10,
                                                ReminderType.ALL.code
                                            )
                                            quickInputText = ""
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DopamineBlue,
                                        disabledContainerColor = DopamineBlue.copy(alpha = 0.35f)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
                                    enabled = quickInputText.isNotBlank()
                                ) {
                                    Text(
                                        text = "添加",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 过滤 Chips
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val baseFilters = listOf(
                            "ALL" to "全部日程",
                            "PENDING" to "待完成",
                            "DEEP_WORK" to "深度工作",
                            "SHALLOW_WORK" to "浅层事务",
                            "REST_BUFFER" to "休息缓冲"
                        )
                        val tagFilters = availableTags.map { it.name to it.name }
                        val filters = baseFilters + tagFilters

                        items(filters) { (key, label) ->
                            val isSelected = selectedFilter == key
                            val customColor = tagColorMap[key] ?: DopamineBlue
                            Surface(
                                onClick = { selectedFilter = key },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) customColor.copy(alpha = 0.18f) else DarkSurface,
                                border = BorderStroke(1.dp, if (isSelected) customColor else DarkBorder),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    if (tagColorMap.containsKey(key)) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(customColor)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = label,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) customColor else TextSecondary
                                    )
                                }
                            }
                        }

                        item {
                            Surface(
                                onClick = { showAddTagDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                color = DarkSurface,
                                border = BorderStroke(1.dp, DopamineBlue.copy(alpha = 0.4f)),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text("➕ 标签", fontSize = 11.5.sp, color = DopamineBlue, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        item {
                            Surface(
                                onClick = { showManageTagsDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                color = DarkSurface,
                                border = BorderStroke(1.dp, DarkBorder),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text("⚙️ 管理", fontSize = 11.5.sp, color = TextSecondary)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 任务列表
                    val filtered = calendarSchedules.filter { item ->
                        when (selectedFilter) {
                            "DEEP_WORK" -> item.workType == "DEEP_WORK"
                            "SHALLOW_WORK" -> item.workType == "SHALLOW_WORK"
                            "REST_BUFFER" -> item.workType == "REST_BUFFER"
                            "PENDING" -> item.status != "COMPLETED"
                            "ALL" -> true
                            else -> item.category == selectedFilter
                        }
                    }

                    if (filtered.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("✨", fontSize = 28.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("当前视图暂无日程安排", fontSize = 13.sp, color = TextMuted)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 840.dp)
                            ) {
                                items(filtered, key = { it.id }) { item ->
                                    ScheduleItemCard(
                                        item = item,
                                        tagColorMap = tagColorMap,
                                        onToggleStatus = { onToggleStatus(item) },
                                        onClickCard = { detailItem = item }
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(60.dp)) }
                            }
                        }
                    }
                }
            }

            ScheduleViewMode.EISENHOWER -> {
                EisenhowerMatrixView(
                    schedules = calendarSchedules,
                    backlogItems = backlogSchedules,
                    availableTags = availableTags,
                    tagColorMap = tagColorMap,
                    onItemClick = { detailItem = it },
                    onToggleStatus = onToggleStatus,
                    onMoveQuadrant = { item, newQuadrant ->
                        val updated = item.copy(
                            eisenhowerQuadrant = newQuadrant,
                            updatedAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString()
                        )
                        onUpdateSchedule(updated)
                    },
                    onUpdateSchedule = onUpdateSchedule,
                    onAddNewInQuadrant = { qCode ->
                        defaultQuadrantForNewSchedule = qCode
                        showAddDialog = true
                    }
                )
            }
        }
    }
}

        // 悬浮防误触锁定/解锁与新建日程 FAB
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 12.dp, end = 12.dp)
        ) {
            // 当处于解锁状态时，上方提供精致轻巧的重新锁定按钮
            AnimatedVisibility(
                visible = !isTableLocked,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    onClick = {
                        isTableLocked = true
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        Toast.makeText(context, "🔒 日常表已重新锁定防误触", Toast.LENGTH_SHORT).show()
                    },
                    shape = CircleShape,
                    color = DarkCard,
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.85f)),
                    shadowElevation = 3.dp,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "重新锁定防误触",
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            if (!isTableLocked) {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 主悬浮按钮：锁定状态下绘制小锁，点击直接解锁；解锁后变为加号，点击新建日程
            FloatingActionButton(
                onClick = {
                    if (isTableLocked) {
                        isTableLocked = false
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        Toast.makeText(context, "🔓 日常表已解锁，可正常编辑与排期", Toast.LENGTH_SHORT).show()
                    } else {
                        defaultQuadrantForNewSchedule = "Q2"
                        showAddDialog = true
                    }
                },
                containerColor = if (isTableLocked) Color(0xFFF59E0B) else DopamineBlue,
                contentColor = if (isTableLocked) Color.White else (if (LocalAppColors.current.isDark) DarkBg else Color.White),
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                modifier = Modifier.size(46.dp)
            ) {
                Crossfade(targetState = isTableLocked, label = "LockAddFabCrossfade") { locked ->
                    if (locked) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "点击解锁日常表",
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新建日程",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // 新建日程弹窗 (AddScheduleDialog)
        if (showAddDialog) {
            AddScheduleDialog(
                availableTags = availableTags,
                onDismiss = { showAddDialog = false },
                onOpenAddTag = { showAddTagDialog = true },
                onOpenManageTags = { showManageTagsDialog = true },
                onConfirm = { title, workType, category, minutes, reminderMins, reminderType ->
                    val now = LocalDateTime.now()
                    val startStr = now.format(DateTimeFormatter.ISO_DATE_TIME)
                    val endStr = now.plusMinutes(minutes.toLong()).format(DateTimeFormatter.ISO_DATE_TIME)
                    val nowStr = now.format(DateTimeFormatter.ISO_DATE_TIME)
                    val entity = ScheduleEntity(
                        title = title,
                        workType = workType,
                        category = category,
                        estimatedMinutes = minutes,
                        startTime = startStr,
                        endTime = endStr,
                        eisenhowerQuadrant = defaultQuadrantForNewSchedule,
                        reminderMinutes = reminderMins,
                        reminderType = reminderType,
                        createdAt = nowStr,
                        updatedAt = nowStr
                    )
                    onUpdateSchedule(entity)
                    showAddDialog = false
                }
            )
        }

        // 待办任务箱弹窗 (BacklogModal)
        if (showBacklogDialog) {
            BacklogDialog(
                backlogItems = backlogSchedules,
                availableTags = availableTags,
                tagColorMap = tagColorMap,
                onDismiss = { showBacklogDialog = false },
                onOpenAddTag = { showAddTagDialog = true },
                onOpenManageTags = { showManageTagsDialog = true },
                onAddBacklog = { title, workType, category, minutes, dod, quadrant ->
                    val nowStr = com.renly.rmf.util.TimeUtils.currentUtcIsoString()
                    val entity = ScheduleEntity(
                        title = title,
                        workType = workType,
                        category = category,
                        estimatedMinutes = minutes,
                        dod = dod,
                        status = "BACKLOG",
                        startTime = "",
                        endTime = "",
                        eisenhowerQuadrant = quadrant,
                        createdAt = nowStr,
                        updatedAt = nowStr
                    )
                    onUpdateSchedule(entity)
                },
                onScheduleToToday = { backlogItem ->
                    val now = LocalDateTime.now()
                    val startStr = now.format(DateTimeFormatter.ISO_DATE_TIME)
                    val endStr = now.plusMinutes(backlogItem.estimatedMinutes.toLong()).format(DateTimeFormatter.ISO_DATE_TIME)
                    val updated = backlogItem.copy(
                        status = "PENDING",
                        startTime = startStr,
                        endTime = endStr,
                        updatedAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString()
                    )
                    onUpdateSchedule(updated)
                },
                onToggleStatus = { backlogItem ->
                    val nextStatus = if (backlogItem.status == "COMPLETED") "BACKLOG" else "COMPLETED"
                    onUpdateSchedule(backlogItem.copy(status = nextStatus, updatedAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString()))
                },
                onDelete = { id ->
                    onDeleteSchedule(id)
                }
            )
        }

        // 日期快速跳转弹窗 (GoToDateModal)
        if (showGoToDateDialog) {
            GoToDateDialog(
                currentDate = selectedDate,
                onDismiss = { showGoToDateDialog = false },
                onSelectDate = {
                    selectedDate = it
                    showGoToDateDialog = false
                }
            )
        }

        // 📅 Google Calendar 日历互通同步弹窗
        if (showCalendarSyncDialog) {
            GoogleCalendarSyncModal(
                onDismiss = { showCalendarSyncDialog = false }
            )
        }

        // ☁️ Google Drive 专属云端同步详情与状态弹窗
        if (showDriveSyncDialog) {
            GoogleDriveSyncStatusModal(
                onDismiss = { showDriveSyncDialog = false },
                onOpenLanSync = {
                    showDriveSyncDialog = false
                    showLanSyncDialog = true
                }
            )
        }

        // 📡 局域网 Wi-Fi 直连极速双向同步弹窗
        if (showLanSyncDialog) {
            LanSyncModal(
                onDismiss = { showLanSyncDialog = false }
            )
        }

        // 🌅 / 🌇 每日规划与关机仪式弹窗 (Sunsama 风格)
        if (showDailyRitualDialog) {
            DailyRitualDialog(
                initialMode = ritualMode,
                schedules = schedules,
                backlogSchedules = backlogSchedules,
                onDismiss = { showDailyRitualDialog = false }
            )
        }

        // 日程详情弹窗 (EventQuickDetailModal)
        detailItem?.let { item ->
            ScheduleDetailModal(
                item = item,
                tagColorMap = tagColorMap,
                isLocked = isTableLocked,
                onToggleStatus = {
                    if (isTableLocked) {
                        Toast.makeText(context, "🔒 表格已锁定防误触，请先点击右下角小锁解锁", Toast.LENGTH_SHORT).show()
                    } else {
                        onToggleStatus(item)
                        detailItem = null
                    }
                },
                onEdit = {
                    if (isTableLocked) {
                        Toast.makeText(context, "🔒 表格已锁定防误触，请先点击右下角小锁解锁", Toast.LENGTH_SHORT).show()
                    } else {
                        editingSchedule = item
                        detailItem = null
                        showEditDialog = true
                    }
                },
                onDelete = {
                    if (isTableLocked) {
                        Toast.makeText(context, "🔒 表格已锁定防误触，请先点击右下角小锁解锁", Toast.LENGTH_SHORT).show()
                    } else {
                        onDeleteSchedule(item.id)
                        detailItem = null
                    }
                },
                onNavigateToFitness = onNavigateToFitness,
                onDismiss = { detailItem = null }
            )
        }

        // 日程全量编辑弹窗 (EventModal)
        if (showEditDialog && editingSchedule != null) {
            EditScheduleDialog(
                item = editingSchedule!!,
                availableTags = availableTags,
                onDismiss = {
                    showEditDialog = false
                    editingSchedule = null
                },
                onOpenAddTag = { showAddTagDialog = true },
                onOpenManageTags = { showManageTagsDialog = true },
                onSave = { updated ->
                    onUpdateSchedule(updated)
                    showEditDialog = false
                    editingSchedule = null
                },
                onDelete = {
                    onDeleteSchedule(editingSchedule!!.id)
                    showEditDialog = false
                    editingSchedule = null
                }
            )
        }

        // ➕ 新增日程标签弹窗
        if (showAddTagDialog) {
            AddScheduleTagDialog(
                onDismiss = { showAddTagDialog = false },
                onConfirm = { tag ->
                    onAddTag(tag)
                    showAddTagDialog = false
                }
            )
        }

        // ⚙️ 管理日程标签弹窗
        if (showManageTagsDialog) {
            ManageScheduleTagsDialog(
                tags = availableTags,
                onDismiss = { showManageTagsDialog = false },
                onDeleteTag = { tag ->
                    onDeleteTag(tag)
                },
                onOpenAdd = {
                    showAddTagDialog = true
                }
            )
        }

        // 📍 真实时间标记与实际偏差分析弹窗 (TimeMarkerDialog)
        if (showTimeMarkerDialog) {
            TimeMarkerDialog(
                selectedDate = selectedDate,
                schedules = calendarSchedules,
                markers = timeMarkers,
                onDismiss = { showTimeMarkerDialog = false },
                onAddMarker = onAddTimeMarker,
                onUpdateMarker = onUpdateTimeMarker,
                onDeleteMarker = onDeleteTimeMarker
            )
        }

        // 🪄 智能作息模板与预设套装弹窗 (RoutineTemplateDialog)
        if (showRoutineTemplateDialog) {
            RoutineTemplateDialog(
                selectedDate = selectedDate,
                schedules = calendarSchedules,
                markers = timeMarkers,
                onDismiss = { showRoutineTemplateDialog = false },
                onApplyPresetSchedules = { presetList ->
                    presetList.forEach { p ->
                        onAddSchedule(p.title, p.workType, p.category, p.durationMinutes, p.startTime, p.endTime, p.reminderMinutes, p.reminderType)
                    }
                },
                onApplyPresetMarkers = { markerList ->
                    markerList.forEach { m ->
                        onAddTimeMarker(m)
                    }
                }
            )
        }
    }
}

// -----------------------------------------------------------------------------------------
// 拖动排期数据模型与排期创建/填入弹窗 (Drag Range Model & Dialog)
// -----------------------------------------------------------------------------------------
data class DragRangeData(
    val date: LocalDate,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val durationMinutes: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DragRangeScheduleDialog(
    range: DragRangeData,
    backlogItems: List<ScheduleEntity> = emptyList(),
    availableTags: List<ScheduleTagEntity> = emptyList(),
    existingSchedules: List<ScheduleEntity> = emptyList(),
    onDismiss: () -> Unit,
    onAddSchedule: (String, String, String, Int, String, String, Int, String) -> Unit,
    onAssignBacklog: (ScheduleEntity, LocalDateTime, LocalDateTime, Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var workType by remember { mutableStateOf("DEEP_WORK") }
    var category by remember { mutableStateOf(availableTags.firstOrNull()?.name ?: "工作") }
    var durationMinutes by remember { mutableIntStateOf(range.durationMinutes) }
    var reminderMinutes by remember { mutableIntStateOf(10) }
    var reminderType by remember { mutableStateOf(ReminderType.ALL.code) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: 新建日程, 1: 待办填入

    val startDt = range.date.atTime(range.startHour, range.startMinute)
    val endDt = startDt.plusMinutes(durationMinutes.toLong())
    val startStr = startDt.format(DateTimeFormatter.ISO_DATE_TIME)
    val endStr = endDt.format(DateTimeFormatter.ISO_DATE_TIME)

    val startMinuteOfDay = range.startHour * 60 + range.startMinute
    val endMinuteOfDay = startMinuteOfDay + durationMinutes
    val conflictSchedule = remember(startMinuteOfDay, durationMinutes, existingSchedules) {
        existingSchedules.firstOrNull { s ->
            if (s.status == "CANCELLED") return@firstOrNull false
            val sDt = parseDateTime(s.startTime) ?: return@firstOrNull false
            if (sDt.toLocalDate() != range.date) return@firstOrNull false
            val oStart = sDt.hour * 60 + sDt.minute
            val oEnd = oStart + s.estimatedMinutes
            maxOf(startMinuteOfDay, oStart) < minOf(endMinuteOfDay, oEnd)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 600.dp),
        containerColor = DarkCard,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⏱️ 拖动排期设置", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(modifier = Modifier.height(5.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = DopamineBlue.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, DopamineBlue.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "📅 ${range.date.format(DateTimeFormatter.ofPattern("M月d日 EEEE"))} · %02d:%02d - %02d:%02d (%d分钟)".format(
                            range.startHour, range.startMinute, endDt.hour, endDt.minute, durationMinutes
                        ),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = DopamineBlue,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (conflictSchedule != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = DopamineRed.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, DopamineRed.copy(alpha = 0.5f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "⚠️ 注意：此时间段与「${conflictSchedule.title}」重叠冲突",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DopamineRed
                            )
                        }
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                // Tab 切换：新建日程 / 从待办箱选择
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        onClick = { selectedTab = 0 },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedTab == 0) DopamineBlue else DarkSurface,
                        border = BorderStroke(1.dp, if (selectedTab == 0) DopamineBlue else DarkBorder),
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "➕ 新建日程",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 0) (if (LocalAppColors.current.isDark) DarkBg else Color.White) else TextSecondary
                            )
                        }
                    }

                    Surface(
                        onClick = { selectedTab = 1 },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedTab == 1) DopamineAmber else DarkSurface,
                        border = BorderStroke(1.dp, if (selectedTab == 1) DopamineAmber else DarkBorder),
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "📥 待办填入 (${backlogItems.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 1) (if (LocalAppColors.current.isDark) DarkBg else Color.White) else TextSecondary
                            )
                        }
                    }
                }

                if (selectedTab == 0) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("日程名称") },
                        placeholder = { Text("例: 架构设计评审 / 深度学习") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DopamineBlue,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("工作类型:", fontSize = 11.sp, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("DEEP_WORK" to "深度攻坚", "SHALLOW_WORK" to "日常浅度", "REST_BUFFER" to "休整缓冲").forEach { (k, v) ->
                            FilterChip(
                                selected = workType == k,
                                onClick = { workType = k },
                                label = { Text(v, fontSize = 11.sp) }
                            )
                        }
                    }

                    val startMinuteOfDay = range.startHour * 60 + range.startMinute
                    if (workType == "DEEP_WORK" && startMinuteOfDay in 810..900) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = DopamineAmber.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, DopamineAmber.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        ) {
                            Text(
                                text = "💡 昼夜节律提示：13:30~15:00 处于人体常规餐后认知低谷期，若有条件建议调配为浅层事务或留白小憩。",
                                fontSize = 10.5.sp,
                                color = DopamineAmber,
                                modifier = Modifier.padding(6.dp),
                                lineHeight = 14.sp
                            )
                        }
                    }

                    Text("分类标签:", fontSize = 11.sp, color = TextSecondary)
                    if (availableTags.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(availableTags) { tagItem ->
                                val tagColor = parseHexColor(tagItem.colorHex, DopamineBlue)
                                FilterChip(
                                    selected = category == tagItem.name,
                                    onClick = { category = tagItem.name },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(tagColor)
                                        )
                                    },
                                    label = { Text(tagItem.name, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // 预估时长自由调节
                    Text("⏱️ 预估耗时: $durationMinutes 分钟", fontSize = 11.sp, color = TextSecondary)
                    Slider(
                        value = durationMinutes.toFloat().coerceIn(1f, 720f),
                        onValueChange = { durationMinutes = it.roundToInt().coerceIn(1, 720) },
                        valueRange = 1f..720f,
                        colors = SliderDefaults.colors(thumbColor = DopamineBlue, activeTrackColor = DopamineBlue)
                    )
                    val quickDurations = listOf(15, 25, 30, 45, 60, 90, 120, 180)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(quickDurations) { d ->
                            FilterChip(
                                selected = durationMinutes == d,
                                onClick = { durationMinutes = d },
                                label = { Text("${d}分", fontSize = 10.5.sp) }
                            )
                        }
                    }

                    Text("⏰ 到期提前提醒:", fontSize = 11.sp, color = TextSecondary)
                    val reminderOptions = listOf(
                        -1 to "不提醒",
                        0 to "准时",
                        5 to "提前5分",
                        10 to "提前10分",
                        15 to "提前15分",
                        30 to "提前30分"
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(reminderOptions) { (mins, label) ->
                            val isSel = reminderMinutes == mins
                            FilterChip(
                                selected = isSel,
                                onClick = { reminderMinutes = mins },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }

                    if (reminderMinutes >= 0) {
                        Text("🔔 提醒形式与闹铃模式:", fontSize = 11.sp, color = TextSecondary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(ReminderType.entries) { typeOption ->
                                val isSel = reminderType == typeOption.code
                                FilterChip(
                                    selected = isSel,
                                    onClick = { reminderType = typeOption.code },
                                    label = { Text("${typeOption.emoji} ${typeOption.title}", fontSize = 11.sp) }
                                )
                            }
                        }
                        val selType = ReminderType.fromCode(reminderType)
                        Text(selType.description, fontSize = 10.5.sp, color = DopamineAmber)
                    }
                } else {
                    if (backlogItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("待办箱暂无任务，可切换到「新建日程」", fontSize = 12.sp, color = TextMuted)
                        }
                    } else {
                        Text("点击任意待办任务，即刻排入此时段：", fontSize = 11.5.sp, color = TextSecondary)
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
                        ) {
                            items(backlogItems, key = { it.id }) { item ->
                                Surface(
                                    onClick = {
                                        onAssignBacklog(item, startDt, endDt, durationMinutes)
                                        onDismiss()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = DarkSurface,
                                    border = BorderStroke(1.dp, DopamineAmber.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Text("📌", fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                item.title,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text("原预估: ${item.estimatedMinutes}m · 调整为 ${durationMinutes}m 并排期", fontSize = 9.5.sp, color = TextMuted)
                                        }
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DopamineAmber, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedTab == 0) {
                Button(
                    onClick = {
                        val finalTitle = title.ifBlank { "新日程" }
                        onAddSchedule(finalTitle, workType, category, durationMinutes, startStr, endStr, reminderMinutes, reminderType)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("确认排期", color = if (LocalAppColors.current.isDark) DarkBg else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        }
    )
}

// -----------------------------------------------------------------------------------------
// 🪄 智能作息模板与预设套装 (Routine Template Models & Dialog)
// -----------------------------------------------------------------------------------------
data class PresetScheduleItem(
    val title: String,
    val workType: String,
    val category: String,
    val durationMinutes: Int,
    val startTime: String,
    val endTime: String,
    val reminderMinutes: Int = 10,
    val reminderType: String = "POPUP"
)

data class RoutineTemplatePreset(
    val id: String,
    val title: String,
    val icon: String,
    val desc: String,
    val schedules: List<PresetScheduleItem>,
    val markers: List<TimeMarkerEntity>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineTemplateDialog(
    selectedDate: LocalDate,
    schedules: List<ScheduleEntity>,
    markers: List<TimeMarkerEntity>,
    onDismiss: () -> Unit,
    onApplyPresetSchedules: (List<PresetScheduleItem>) -> Unit,
    onApplyPresetMarkers: (List<TimeMarkerEntity>) -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val dateStr = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

    // 构建常用预设作息套装
    val presets = remember(selectedDate) {
        listOf(
            RoutineTemplatePreset(
                id = "workday_focus",
                title = "工作日高效作息",
                icon = "💼",
                desc = "早起晨间规划、深度工作黄金块、午间休整与晚间复盘",
                schedules = listOf(
                    PresetScheduleItem(
                        title = "晨间规划与收件箱整理",
                        workType = "SHALLOW_WORK",
                        category = "工作",
                        durationMinutes = 30,
                        startTime = selectedDate.atTime(8, 30).format(DateTimeFormatter.ISO_DATE_TIME),
                        endTime = selectedDate.atTime(9, 0).format(DateTimeFormatter.ISO_DATE_TIME)
                    ),
                    PresetScheduleItem(
                        title = "上午核心深度攻坚",
                        workType = "DEEP_WORK",
                        category = "工作",
                        durationMinutes = 120,
                        startTime = selectedDate.atTime(9, 30).format(DateTimeFormatter.ISO_DATE_TIME),
                        endTime = selectedDate.atTime(11, 30).format(DateTimeFormatter.ISO_DATE_TIME)
                    ),
                    PresetScheduleItem(
                        title = "下午高能推进与协同",
                        workType = "DEEP_WORK",
                        category = "工作",
                        durationMinutes = 90,
                        startTime = selectedDate.atTime(14, 0).format(DateTimeFormatter.ISO_DATE_TIME),
                        endTime = selectedDate.atTime(15, 30).format(DateTimeFormatter.ISO_DATE_TIME)
                    ),
                    PresetScheduleItem(
                        title = "日常事务与晚间复盘",
                        workType = "SHALLOW_WORK",
                        category = "日常",
                        durationMinutes = 45,
                        startTime = selectedDate.atTime(17, 30).format(DateTimeFormatter.ISO_DATE_TIME),
                        endTime = selectedDate.atTime(18, 15).format(DateTimeFormatter.ISO_DATE_TIME)
                    )
                ),
                markers = listOf(
                    TimeMarkerEntity(
                        id = UUID.randomUUID().toString(),
                        name = "晨起唤醒",
                        markerType = "POINT",
                        category = "ROUTINE",
                        icon = "🌅",
                        date = dateStr,
                        startTime = selectedDate.atTime(7, 30).format(DateTimeFormatter.ISO_DATE_TIME),
                        endTime = "",
                        colorHex = "#10B981",
                        notes = "模板作息打卡",
                        createdAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString(),
                        updatedAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString()
                    ),
                    TimeMarkerEntity(
                        id = UUID.randomUUID().toString(),
                        name = "午休充能",
                        markerType = "RANGE",
                        category = "REST",
                        icon = "🛌",
                        date = dateStr,
                        startTime = selectedDate.atTime(12, 45).format(DateTimeFormatter.ISO_DATE_TIME),
                        endTime = selectedDate.atTime(13, 30).format(DateTimeFormatter.ISO_DATE_TIME),
                        colorHex = "#3B82F6",
                        notes = "模板作息打卡",
                        createdAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString(),
                        updatedAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString()
                    ),
                    TimeMarkerEntity(
                        id = UUID.randomUUID().toString(),
                        name = "舒缓就寝",
                        markerType = "POINT",
                        category = "ROUTINE",
                        icon = "💤",
                        date = dateStr,
                        startTime = selectedDate.atTime(23, 15).format(DateTimeFormatter.ISO_DATE_TIME),
                        endTime = "",
                        colorHex = "#8B5CF6",
                        notes = "模板作息打卡",
                        createdAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString(),
                        updatedAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString()
                    )
                )
            ),
            RoutineTemplatePreset(
                id = "weekend_recharge",
                title = "周末蓄能休整",
                icon = "🏖️",
                desc = "充裕自然醒、户外轻运动、阅读充电与慢节奏生活",
                schedules = listOf(
                    PresetScheduleItem(
                        title = "户外散步 / 有氧运动",
                        workType = "REST_BUFFER",
                        category = "运动",
                        durationMinutes = 60,
                        startTime = selectedDate.atTime(10, 0).format(DateTimeFormatter.ISO_DATE_TIME),
                        endTime = selectedDate.atTime(11, 0).format(DateTimeFormatter.ISO_DATE_TIME)
                    ),
                    PresetScheduleItem(
                        title = "沉浸式深度阅读 / 自由充电",
                        workType = "DEEP_WORK",
                        category = "学习",
                        durationMinutes = 90,
                        startTime = selectedDate.atTime(15, 0).format(DateTimeFormatter.ISO_DATE_TIME),
                        endTime = selectedDate.atTime(16, 30).format(DateTimeFormatter.ISO_DATE_TIME)
                    ),
                    PresetScheduleItem(
                        title = "家庭时光 / 影音放松",
                        workType = "REST_BUFFER",
                        category = "娱乐",
                        durationMinutes = 120,
                        startTime = selectedDate.atTime(19, 30).format(DateTimeFormatter.ISO_DATE_TIME),
                        endTime = selectedDate.atTime(21, 30).format(DateTimeFormatter.ISO_DATE_TIME)
                    )
                ),
                markers = listOf(
                    TimeMarkerEntity(
                        id = UUID.randomUUID().toString(),
                        name = "周末自然醒",
                        markerType = "POINT",
                        category = "ROUTINE",
                        icon = "🌅",
                        date = dateStr,
                        startTime = selectedDate.atTime(9, 0).format(DateTimeFormatter.ISO_DATE_TIME),
                        endTime = "",
                        colorHex = "#10B981",
                        notes = "模板作息打卡",
                        createdAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString(),
                        updatedAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString()
                    ),
                    TimeMarkerEntity(
                        id = UUID.randomUUID().toString(),
                        name = "周末午后惬意小憩",
                        markerType = "RANGE",
                        category = "REST",
                        icon = "🛌",
                        date = dateStr,
                        startTime = selectedDate.atTime(13, 30).format(DateTimeFormatter.ISO_DATE_TIME),
                        endTime = selectedDate.atTime(14, 30).format(DateTimeFormatter.ISO_DATE_TIME),
                        colorHex = "#3B82F6",
                        notes = "模板作息打卡",
                        createdAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString(),
                        updatedAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString()
                    )
                )
            ),
            RoutineTemplatePreset(
                id = "exam_sprint",
                title = "备考 / 冲刺作息",
                icon = "🔥",
                desc = "番茄钟多轮攻坚、背诵记忆、规律作息杜绝熬夜",
                schedules = listOf(
                    PresetScheduleItem(
                        title = "晨间重点攻坚 & 记忆背诵",
                        workType = "DEEP_WORK",
                        category = "学习",
                        durationMinutes = 90,
                        startTime = selectedDate.atTime(8, 0).format(DateTimeFormatter.ISO_DATE_TIME),
                        endTime = selectedDate.atTime(9, 30).format(DateTimeFormatter.ISO_DATE_TIME)
                    ),
                    PresetScheduleItem(
                        title = "上午真题模拟与实战演练",
                        workType = "DEEP_WORK",
                        category = "学习",
                        durationMinutes = 120,
                        startTime = selectedDate.atTime(10, 0).format(DateTimeFormatter.ISO_DATE_TIME),
                        endTime = selectedDate.atTime(12, 0).format(DateTimeFormatter.ISO_DATE_TIME)
                    ),
                    PresetScheduleItem(
                        title = "下午专题攻坚与错题复盘",
                        workType = "DEEP_WORK",
                        category = "学习",
                        durationMinutes = 120,
                        startTime = selectedDate.atTime(14, 30).format(DateTimeFormatter.ISO_DATE_TIME),
                        endTime = selectedDate.atTime(16, 30).format(DateTimeFormatter.ISO_DATE_TIME)
                    ),
                    PresetScheduleItem(
                        title = "晚间查漏补缺与知识树梳理",
                        workType = "DEEP_WORK",
                        category = "学习",
                        durationMinutes = 90,
                        startTime = selectedDate.atTime(19, 0).format(DateTimeFormatter.ISO_DATE_TIME),
                        endTime = selectedDate.atTime(20, 30).format(DateTimeFormatter.ISO_DATE_TIME)
                    )
                ),
                markers = listOf(
                    TimeMarkerEntity(
                        id = UUID.randomUUID().toString(),
                        name = "准点起床",
                        markerType = "POINT",
                        category = "ROUTINE",
                        icon = "🌅",
                        date = dateStr,
                        startTime = selectedDate.atTime(7, 0).format(DateTimeFormatter.ISO_DATE_TIME),
                        endTime = "",
                        colorHex = "#10B981",
                        notes = "模板作息打卡",
                        createdAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString(),
                        updatedAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString()
                    ),
                    TimeMarkerEntity(
                        id = UUID.randomUUID().toString(),
                        name = "高效午休",
                        markerType = "RANGE",
                        category = "REST",
                        icon = "🛌",
                        date = dateStr,
                        startTime = selectedDate.atTime(13, 0).format(DateTimeFormatter.ISO_DATE_TIME),
                        endTime = selectedDate.atTime(13, 40).format(DateTimeFormatter.ISO_DATE_TIME),
                        colorHex = "#3B82F6",
                        notes = "模板作息打卡",
                        createdAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString(),
                        updatedAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString()
                    )
                )
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🪄", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "智能作息模板与预设套装",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "一键为「$selectedDate」铺设科学作息与日程排期",
                        fontSize = 11.5.sp,
                        color = colors.textSecondary
                    )
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(presets) { preset ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = colors.bg),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.border.copy(alpha = 0.8f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(preset.icon, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = preset.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.dopamineBlue.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "${preset.schedules.size}日程 · ${preset.markers.size}标记",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.dopamineBlue,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = preset.desc,
                                fontSize = 11.5.sp,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )

                            // 简要展示排期预览
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.card.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                preset.schedules.forEach { s ->
                                    val start = LocalDateTime.parse(s.startTime, DateTimeFormatter.ISO_DATE_TIME).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
                                    val end = LocalDateTime.parse(s.endTime, DateTimeFormatter.ISO_DATE_TIME).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("• ${s.title}", fontSize = 11.sp, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        Text("$start-$end", fontSize = 10.5.sp, color = colors.textSecondary)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    onApplyPresetSchedules(preset.schedules)
                                    onApplyPresetMarkers(preset.markers)
                                    Toast.makeText(context, "🎉 已成功应用「${preset.title}」至今日排期！", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.dopamineBlue),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Text("一键应用此模板", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = colors.textSecondary)
            }
        }
    )
}

// -----------------------------------------------------------------------------------------

// 电脑版 1:1 周历网格视图 (Week Grid Track: 7 列星期 x 24 小时刻度网格 - 支持待办拖拽与手势框选排期)
// -----------------------------------------------------------------------------------------
@Composable
fun ScheduleWeekGridView(
    schedules: List<ScheduleEntity>,
    backlogItems: List<ScheduleEntity> = emptyList(),
    selectedDate: LocalDate,
    availableTags: List<ScheduleTagEntity> = emptyList(),
    tagColorMap: Map<String, Color> = emptyMap(),
    isLocked: Boolean = false,
    onSelectDate: (LocalDate) -> Unit,
    onItemClick: (ScheduleEntity) -> Unit,
    onScheduleBacklog: (ScheduleEntity, LocalDateTime) -> Unit = { _, _ -> },
    onAddScheduleWithRange: (String, String, String, Int, String, String, Int, String) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onUpdateSchedule: (ScheduleEntity) -> Unit = {}
) {
    val currentWeekMonday = remember(selectedDate) {
        selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
    val weekDates = remember(currentWeekMonday) {
        (0..6).map { currentWeekMonday.plusDays(it.toLong()) }
    }
    val sunday = remember(weekDates) { weekDates.last() }

    // 日/周视图缩放支持 (Zoom Scale State: 0.7x ~ 2.5x)
    var weekZoomFactor by rememberSaveable { mutableFloatStateOf(1.0f) }
    val hourHeightDpValue = 46f * weekZoomFactor
    val hourHeight = hourHeightDpValue.dp
    val timeColWidth = 32.dp
    val verticalScrollState = rememberScrollState()
    val density = LocalDensity.current

    val currentHourHeightDpValue by rememberUpdatedState(hourHeightDpValue)
    val currentWeekDates by rememberUpdatedState(weekDates)
    val currentDensity by rememberUpdatedState(density)
    val currentIsLocked by rememberUpdatedState(isLocked)
    val currentOnUpdateSchedule by rememberUpdatedState(onUpdateSchedule)
    val currentOnScheduleBacklog by rememberUpdatedState(onScheduleBacklog)
    val currentOnItemClick by rememberUpdatedState(onItemClick)

    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    var weekViewRootOffset by remember { mutableStateOf(Offset.Zero) }
    var backlogDragTouchInRoot by remember { mutableStateOf(Offset.Zero) }

    // 待办拖拽与框选排期状态
    var draggingBacklogItem by remember { mutableStateOf<ScheduleEntity?>(null) }
    var backlogDragOffsetY by remember { mutableFloatStateOf(0f) }
    var backlogDragOffsetX by remember { mutableFloatStateOf(0f) }
    var backlogChipRootY by remember { mutableFloatStateOf(0f) }
    var backlogChipRootX by remember { mutableFloatStateOf(0f) }
    var weekGridRootY by remember { mutableFloatStateOf(0f) }
    var weekGridRootX by remember { mutableFloatStateOf(0f) }
    var weekDayColWidthPx by remember { mutableFloatStateOf(0f) }
    var isBacklogExpanded by remember { mutableStateOf(false) }

    val computeWeekDragMinutes: (Offset, Offset) -> Triple<Int, Int, LocalDate> = { start, curr ->
        val minY = minOf(start.y, curr.y).coerceAtLeast(0f)
        val maxY = maxOf(start.y, curr.y).coerceAtMost(24 * currentHourHeightDpValue * currentDensity.density)
        val colW = if (weekDayColWidthPx > 0f) (start.x / weekDayColWidthPx).toInt().coerceIn(0, 6) else 0
        val targetDate = currentWeekDates[colW]

        val startMinExact = (minY / currentDensity.density / currentHourHeightDpValue * 60f)
        val endMinExact = (maxY / currentDensity.density / currentHourHeightDpValue * 60f)
        val sSnapped = startMinExact.roundToInt().coerceIn(0, 1439)
        val rawEndSnapped = endMinExact.roundToInt().coerceIn(1, 1440)
        val eSnapped = maxOf(sSnapped + 1, rawEndSnapped)
        Triple(sSnapped, eSnapped, targetDate)
    }

    val executeDropWeek: (ScheduleEntity) -> Unit = { item ->
        if (currentIsLocked) {
            Toast.makeText(context, "🔒 表格已锁定防误触，请先点击右下角小锁解锁", Toast.LENGTH_SHORT).show()
        } else {
            val currentScroll = verticalScrollState.value
            val dropGlobalY = if (backlogDragTouchInRoot != Offset.Zero) backlogDragTouchInRoot.y else (backlogChipRootY + backlogDragOffsetY)
            val dropGlobalX = if (backlogDragTouchInRoot != Offset.Zero) backlogDragTouchInRoot.x else (backlogChipRootX + backlogDragOffsetX)
            if (dropGlobalY >= weekGridRootY) {
                val relativeY = (dropGlobalY - weekGridRootY + currentScroll).coerceAtLeast(0f)
                val relativeX = (dropGlobalX - weekGridRootX - 32f * currentDensity.density).coerceAtLeast(0f)
                val colW = if (weekDayColWidthPx > 0f) (relativeX / weekDayColWidthPx).toInt().coerceIn(0, 6) else 0
                val targetDay = currentWeekDates[colW]

                val relDp = relativeY / currentDensity.density
                val previewBlockH = (item.estimatedMinutes.toFloat() / 60f * currentHourHeightDpValue).coerceAtLeast(20f)
                val blockTopDp = (relDp - previewBlockH / 2f).coerceIn(0f, (24f * currentHourHeightDpValue - previewBlockH).coerceAtLeast(0f))
                val totalMinutes = (blockTopDp / currentHourHeightDpValue) * 60f
                val snappedMinutes = totalMinutes.roundToInt().coerceIn(0, (1440 - item.estimatedMinutes).coerceAtLeast(0))
                val dropHour = snappedMinutes / 60
                val dropMinute = snappedMinutes % 60
                val targetDt = targetDay.atTime(dropHour, dropMinute)

                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnScheduleBacklog(item, targetDt)
                val weekdayName = when (targetDay.dayOfWeek) {
                    DayOfWeek.MONDAY -> "周一"
                    DayOfWeek.TUESDAY -> "周二"
                    DayOfWeek.WEDNESDAY -> "周三"
                    DayOfWeek.THURSDAY -> "周四"
                    DayOfWeek.FRIDAY -> "周五"
                    DayOfWeek.SATURDAY -> "周六"
                    DayOfWeek.SUNDAY -> "周日"
                }
                Toast.makeText(context, "已排期: ${item.title} 至 $weekdayName %02d:%02d".format(dropHour, dropMinute), Toast.LENGTH_SHORT).show()
            }
        }
        draggingBacklogItem = null
        backlogDragTouchInRoot = Offset.Zero
        backlogDragOffsetY = 0f
        backlogDragOffsetX = 0f
    }

    // 空白网格拖拽框选排期状态
    var isDragSelecting by remember { mutableStateOf(false) }
    var dragSelectStartOffset by remember { mutableStateOf<Offset?>(null) }
    var dragSelectCurrentOffset by remember { mutableStateOf<Offset?>(null) }
    var dragSelectedRange by remember { mutableStateOf<DragRangeData?>(null) }

    // 已有卡片长按拖拽移时
    var movingScheduleItem by remember { mutableStateOf<ScheduleEntity?>(null) }
    var movingDragDeltaY by remember { mutableFloatStateOf(0f) }
    var movingOriginalTopDp by remember { mutableFloatStateOf(0f) }

    // 选中具体时间灰色标尺状态
    var selectedTimeIndicatorMinute by remember { mutableStateOf<Int?>(null) }
    var selectedTimeIndicatorCol by remember { mutableStateOf<Int?>(null) }

    var hasInitializedScroll by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!hasInitializedScroll) {
            hasInitializedScroll = true
            val currentHour = LocalDateTime.now().hour
            val targetHour = (currentHour - 2).coerceIn(0, 20)
            verticalScrollState.scrollTo(targetHour * 105)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { weekViewRootOffset = it.positionInRoot() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
        // 周次切换控制器
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onSelectDate(selectedDate.minusWeeks(1)) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "上一周", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }

                Text(
                    text = "${currentWeekMonday.format(DateTimeFormatter.ofPattern("M/d"))} - ${sunday.format(DateTimeFormatter.ofPattern("M/d"))}",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                IconButton(
                    onClick = { onSelectDate(selectedDate.plusWeeks(1)) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "下一周", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = { onSelectDate(LocalDate.now()) },
                    shape = RoundedCornerShape(6.dp),
                    color = DopamineBlue.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, DopamineBlue.copy(alpha = 0.35f)),
                    modifier = Modifier.height(26.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 7.dp)
                    ) {
                        Icon(Icons.Default.Today, contentDescription = "回到今天", tint = DopamineBlue, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("回到今日", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = DopamineBlue)
                    }
                }
            }
        }

        // 📥 待排期任务快捷拖拽托盘 (支持长按拖拽至任意天、任意小时)
        if (backlogItems.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = DarkCard,
                border = BorderStroke(1.dp, DopamineAmber.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp)
                    .onGloballyPositioned { coords ->
                        backlogChipRootY = coords.positionInRoot().y
                        backlogChipRootX = coords.positionInRoot().x
                    }
            ) {
                Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { isBacklogExpanded = !isBacklogExpanded }
                        ) {
                            Text("📥", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "待排期任务 (${backlogItems.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DopamineAmber
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isBacklogExpanded) "收起 ▾" else "展开拖入周历 ▸",
                                fontSize = 9.5.sp,
                                color = TextMuted
                            )
                        }

                        Text(
                            text = "长按拖至周网格排期",
                            fontSize = 9.sp,
                            color = TextSecondary
                        )
                    }

                    if (isBacklogExpanded) {
                        Spacer(modifier = Modifier.height(3.dp))
                        val weekBacklogScrollState = rememberScrollState()
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(weekBacklogScrollState, enabled = draggingBacklogItem == null)
                        ) {
                            backlogItems.forEach { item ->
                                val chipColor = tagColorMap[item.category] ?: when (item.workType) {
                                    "DEEP_WORK" -> DopamineBlue
                                    "SHALLOW_WORK" -> DopamineAmber
                                    "REST_BUFFER" -> DopamineGreen
                                    else -> DopaminePurple
                                }

                                var chipPosInRoot by remember { mutableStateOf(Offset.Zero) }
                                val isBeingDragged = draggingBacklogItem?.id == item.id

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = chipColor.copy(alpha = if (isBeingDragged) 0.35f else 0.16f),
                                    border = BorderStroke(1.dp, chipColor.copy(alpha = if (isBeingDragged) 0.35f else 0.7f)),
                                    modifier = Modifier
                                        .onGloballyPositioned { coords -> chipPosInRoot = coords.positionInRoot() }
                                        .pointerInput(item.id, hourHeightDpValue) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                var isDragging = false
                                                val touchSlop = viewConfiguration.touchSlop

                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                                    if (!change.pressed) {
                                                        if (isDragging) {
                                                            change.consume()
                                                            executeDropWeek(item)
                                                        } else {
                                                            onItemClick(item)
                                                        }
                                                        break
                                                    }

                                                    if (!isDragging) {
                                                        val dragDistance = (change.position - down.position).getDistance()
                                                        if (dragDistance > touchSlop) {
                                                            isDragging = true
                                                            change.consume()
                                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            draggingBacklogItem = item
                                                            backlogDragTouchInRoot = chipPosInRoot + change.position
                                                            backlogDragOffsetY = backlogDragTouchInRoot.y - backlogChipRootY
                                                            backlogDragOffsetX = backlogDragTouchInRoot.x - backlogChipRootX
                                                        }
                                                    } else {
                                                        change.consume()
                                                        backlogDragTouchInRoot = chipPosInRoot + change.position
                                                        backlogDragOffsetY = backlogDragTouchInRoot.y - backlogChipRootY
                                                        backlogDragOffsetX = backlogDragTouchInRoot.x - backlogChipRootX
                                                    }
                                                }

                                                if (isDragging && draggingBacklogItem != null) {
                                                    draggingBacklogItem = null
                                                    backlogDragTouchInRoot = Offset.Zero
                                                    backlogDragOffsetY = 0f
                                                    backlogDragOffsetX = 0f
                                                }
                                            }
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = item.title,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${item.estimatedMinutes}m",
                                            fontSize = 9.sp,
                                            color = chipColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // 快捷操作提示条
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isLocked) Color(0xFFF59E0B).copy(alpha = 0.12f) else DarkCard.copy(alpha = 0.6f),
            border = if (isLocked) BorderStroke(0.8.dp, Color(0xFFF59E0B).copy(alpha = 0.45f)) else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
            ) {
                Text(if (isLocked) "🔒" else "💡", fontSize = 9.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isLocked) "表格已锁定防误触 · 点击右下角小锁解锁即可编辑排期" else "长按框选 · 拖拽排期 · 双指捏合缩放",
                    fontSize = 8.5.sp,
                    color = if (isLocked) Color(0xFFF59E0B) else TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(1.dp))

        // 7 列自适应全屏网格容器 (BoxWithConstraints 动态自适应手机宽度)
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val totalWidth = maxWidth
            val dayColWidth = ((totalWidth - timeColWidth) / 7).coerceAtLeast(36.dp)
            val weekGridWidth = dayColWidth * 7
            val dayColWidthPx = with(density) { dayColWidth.toPx() }
            LaunchedEffect(dayColWidthPx) {
                weekDayColWidthPx = dayColWidthPx
            }

            Surface(
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
                color = DarkSurface,
                border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 顶部固定星期标题行 (自适应充满宽度，周一至周日全展示)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurface)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.width(timeColWidth), contentAlignment = Alignment.Center) {
                            Text("GMT+8", fontSize = 7.5.sp, color = TextMuted)
                        }

                        weekDates.forEach { date ->
                            val isToday = date == LocalDate.now()
                            val weekdayName = when (date.dayOfWeek) {
                                DayOfWeek.MONDAY -> "一"
                                DayOfWeek.TUESDAY -> "二"
                                DayOfWeek.WEDNESDAY -> "三"
                                DayOfWeek.THURSDAY -> "四"
                                DayOfWeek.FRIDAY -> "五"
                                DayOfWeek.SATURDAY -> "六"
                                DayOfWeek.SUNDAY -> "日"
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(dayColWidth)
                            ) {
                                Text(
                                    text = "周$weekdayName",
                                    fontSize = 10.sp,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isToday) DopamineBlue else TextSecondary
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = if (isToday) DopamineBlue else Color.Transparent,
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = date.dayOfMonth.toString(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isToday) (if (LocalAppColors.current.isDark) DarkBg else Color.White) else TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f), thickness = 0.8.dp)

                    // 24 小时纵向滚动网格（支持双指捏合无极缩放）
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coords ->
                                weekGridRootY = coords.positionInRoot().y
                                weekGridRootX = coords.positionInRoot().x
                            }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        if (event.changes.count { it.pressed } >= 2) {
                                            val zoomChange = event.calculateZoom()
                                            if (zoomChange != 1f && zoomChange > 0f) {
                                                weekZoomFactor = (weekZoomFactor * zoomChange).coerceIn(0.6f, 2.5f)
                                                event.changes.forEach { it.consume() }
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                            .verticalScroll(verticalScrollState)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            // 时间刻度列
                            Column(modifier = Modifier.width(timeColWidth)) {
                                (0..23).forEach { h ->
                                    Box(
                                        modifier = Modifier
                                            .height(hourHeight)
                                            .fillMaxWidth(),
                                        contentAlignment = Alignment.TopCenter
                                    ) {
                                        Text(
                                            text = "%02d:00".format(h),
                                            fontSize = 8.5.sp,
                                            color = TextMuted,
                                            modifier = Modifier.padding(top = 1.dp)
                                        )
                                    }
                                }
                            }

                            // 7 天网格内容 (支持长按手势拖动框选排期)
                            Box(
                                modifier = Modifier
                                    .width(weekGridWidth)
                                    .height(hourHeight * 24)
                                    .pointerInput(isLocked, hourHeightDpValue) {
                                        if (!isLocked) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { offset ->
                                                    isDragSelecting = true
                                                    dragSelectStartOffset = offset
                                                    dragSelectCurrentOffset = offset
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    dragSelectCurrentOffset = change.position
                                                },
                                                onDragEnd = {
                                                    if (isDragSelecting && dragSelectStartOffset != null && dragSelectCurrentOffset != null) {
                                                        val (sSnapped, eSnapped, targetDate) = computeWeekDragMinutes(dragSelectStartOffset!!, dragSelectCurrentOffset!!)

                                                        dragSelectedRange = DragRangeData(
                                                            date = targetDate,
                                                            startHour = sSnapped / 60,
                                                            startMinute = sSnapped % 60,
                                                            endHour = eSnapped / 60,
                                                            endMinute = eSnapped % 60,
                                                            durationMinutes = eSnapped - sSnapped
                                                        )
                                                    }
                                                    isDragSelecting = false
                                                    dragSelectStartOffset = null
                                                    dragSelectCurrentOffset = null
                                                },
                                                onDragCancel = {
                                                    isDragSelecting = false
                                                    dragSelectStartOffset = null
                                                    dragSelectCurrentOffset = null
                                                }
                                            )
                                        }
                                    }
                            ) {
                                // 今日高亮柔和背景列与单点具体时间交互
                                weekDates.forEachIndexed { idx, date ->
                                    val isToday = date == LocalDate.now()
                                    Box(
                                        modifier = Modifier
                                            .offset(x = dayColWidth * idx)
                                            .width(dayColWidth)
                                            .fillMaxHeight()
                                            .pointerInput(idx, isLocked, hourHeightDpValue) {
                                                if (!isLocked) {
                                                    awaitEachGesture {
                                                        val down = awaitFirstDown(requireUnconsumed = false)
                                                        val downTime = System.currentTimeMillis()
                                                        var upChange: PointerInputChange? = null
                                                        while (true) {
                                                            val event = awaitPointerEvent()
                                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                            if (change.isConsumed) return@awaitEachGesture
                                                            if (change.changedToUp()) {
                                                                upChange = change
                                                                break
                                                            }
                                                        }
                                                        if (upChange != null) {
                                                            val elapsed = System.currentTimeMillis() - downTime
                                                            val distance = (upChange.position - down.position).getDistance()
                                                            if (elapsed < 450 && distance < 20f) {
                                                                val rawMin = (upChange.position.y / currentDensity.density / currentHourHeightDpValue * 60f)
                                                                val tappedMin = rawMin.roundToInt().coerceIn(0, 1439)
                                                                if (selectedTimeIndicatorCol == idx && selectedTimeIndicatorMinute == tappedMin) {
                                                                    selectedTimeIndicatorMinute = null
                                                                    selectedTimeIndicatorCol = null
                                                                } else {
                                                                    selectedTimeIndicatorMinute = tappedMin
                                                                    selectedTimeIndicatorCol = idx
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            .background(if (isToday) DopamineBlue.copy(alpha = if (LocalAppColors.current.isDark) 0.08f else 0.05f) else Color.Transparent)
                                    )
                                }

                                // 横向小时刻度线
                                (0..23).forEach { h ->
                                    HorizontalDivider(
                                        modifier = Modifier.offset(y = hourHeight * h),
                                        color = DarkBorder.copy(alpha = 0.35f),
                                        thickness = 0.8.dp
                                    )
                                }

                                // 纵向星期分割线
                                (1..6).forEach { d ->
                                    VerticalDivider(
                                        modifier = Modifier.offset(x = dayColWidth * d),
                                        color = DarkBorder.copy(alpha = 0.25f),
                                        thickness = 0.8.dp
                                    )
                                }

                                // 动态时间指示红线 (Now-Line)
                                val now = LocalDateTime.now()
                                val todayIndex = weekDates.indexOf(now.toLocalDate())
                                if (todayIndex != -1) {
                                    val currentMinuteFromZero = now.hour * 60 + now.minute
                                    val nowY = (currentMinuteFromZero.toFloat() / 60f) * hourHeightDpValue
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .offset(x = dayColWidth * todayIndex, y = (nowY - 2.5).dp)
                                            .width(dayColWidth)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .background(DopamineRed, CircleShape)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(1.8.dp)
                                                .background(DopamineRed)
                                        )
                                    }
                                }

                                // 选中时间灰色标尺指示线 (单点具体时间，像红线那样显示灰色标尺与胶囊)
                                if (selectedTimeIndicatorMinute != null && selectedTimeIndicatorCol != null) {
                                    val selMin = selectedTimeIndicatorMinute!!
                                    val selCol = selectedTimeIndicatorCol!!
                                    val selY = (selMin.toFloat() / 60f) * hourHeightDpValue
                                    val isDark = LocalAppColors.current.isDark
                                    val greyLineColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                                    val badgeBgColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)

                                    // 跨越全周横向贯穿标尺参考线
                                    Box(
                                        modifier = Modifier
                                            .offset(y = (selY - 0.9).dp)
                                            .width(weekGridWidth)
                                            .height(1.5.dp)
                                            .background(greyLineColor.copy(alpha = 0.45f))
                                            .zIndex(14f)
                                    )

                                    // 选中星期列上的高亮灰色实线标尺与时间微胶囊
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .offset(x = dayColWidth * selCol, y = (selY - 10).dp)
                                            .width(dayColWidth)
                                            .zIndex(15f)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = badgeBgColor,
                                            border = BorderStroke(1.dp, greyLineColor),
                                            shadowElevation = 3.dp,
                                            modifier = Modifier.height(20.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .padding(horizontal = 4.dp)
                                                    .clickable {
                                                        selectedTimeIndicatorMinute = null
                                                        selectedTimeIndicatorCol = null
                                                    }
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.5.dp)
                                                        .background(greyLineColor, CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "%02d:%02d".format(selMin / 60, selMin % 60),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "清除标尺",
                                                    tint = greyLineColor,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(1.8.dp)
                                                .background(greyLineColor)
                                        )
                                    }
                                }

                                // 待办拖拽至周历的实时预览虚影 (直接绘制在主视图内随手拖动预览)
                                if (draggingBacklogItem != null) {
                                    val currentScroll = verticalScrollState.value
                                    val dropGlobalY = if (backlogDragTouchInRoot != Offset.Zero) backlogDragTouchInRoot.y else (backlogChipRootY + backlogDragOffsetY)
                                    val dropGlobalX = if (backlogDragTouchInRoot != Offset.Zero) backlogDragTouchInRoot.x else (backlogChipRootX + backlogDragOffsetX)
                                    if (dropGlobalY >= weekGridRootY) {
                                        val relativeY = (dropGlobalY - weekGridRootY + currentScroll).coerceAtLeast(0f)
                                        val relativeX = (dropGlobalX - weekGridRootX - 32f * density.density).coerceAtLeast(0f)

                                        val colW = (relativeX / dayColWidthPx).toInt().coerceIn(0, 6)
                                        val relDp = relativeY / density.density
                                        val previewBlockH = (draggingBacklogItem!!.estimatedMinutes.toFloat() / 60f * hourHeightDpValue).coerceAtLeast(20f)
                                        val blockTopDp = (relDp - previewBlockH / 2f).coerceIn(0f, (24f * hourHeightDpValue - previewBlockH).coerceAtLeast(0f))
                                        val totalMinutes = (blockTopDp / hourHeightDpValue) * 60f
                                        val snappedMinutes = totalMinutes.roundToInt().coerceIn(0, (1440 - draggingBacklogItem!!.estimatedMinutes).coerceAtLeast(0))
                                        val previewHour = snappedMinutes / 60
                                        val previewMinute = snappedMinutes % 60
                                        val draggedColor = tagColorMap[draggingBacklogItem!!.category] ?: when (draggingBacklogItem!!.workType) {
                                            "DEEP_WORK" -> DopamineBlue
                                            "SHALLOW_WORK" -> DopamineAmber
                                            "REST_BUFFER" -> DopamineGreen
                                            else -> DopaminePurple
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = draggedColor.copy(alpha = 0.55f),
                                            border = BorderStroke(1.5.dp, draggedColor),
                                            modifier = Modifier
                                                .offset(x = dayColWidth * colW + 1.dp, y = blockTopDp.dp)
                                                .width(dayColWidth - 2.dp)
                                                .height(previewBlockH.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp),
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = draggingBacklogItem!!.title,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "%02d:%02d".format(previewHour, previewMinute),
                                                    fontSize = 7.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White.copy(alpha = 0.9f)
                                                )
                                            }
                                        }
                                    }
                                }

                                // 手势框选时间段的实时高亮指示框 (Week Drag-Select Box)
                                if (isDragSelecting && dragSelectStartOffset != null && dragSelectCurrentOffset != null) {
                                    val (sSnapped, eSnapped, targetDate) = computeWeekDragMinutes(dragSelectStartOffset!!, dragSelectCurrentOffset!!)
                                    val durationM = eSnapped - sSnapped
                                    val colIdx = currentWeekDates.indexOf(targetDate).coerceIn(0, 6)

                                    val ghostTopDp = (sSnapped.toFloat() / 60f) * hourHeightDpValue
                                    val ghostHeightDp = maxOf(12f, (durationM.toFloat() / 60f) * hourHeightDpValue)

                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = DopamineBlue.copy(alpha = 0.38f),
                                        border = BorderStroke(1.5.dp, DopamineBlue),
                                        modifier = Modifier
                                            .offset(x = dayColWidth * colIdx + 1.dp, y = ghostTopDp.dp)
                                            .width(dayColWidth - 2.dp)
                                            .height(ghostHeightDp.dp)
                                    ) {
                                        if (ghostHeightDp >= 20f) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(1.dp)) {
                                                Text(
                                                    text = "%02d:%02d-%02d:%02d".format(sSnapped / 60, sSnapped % 60, eSnapped / 60, eSnapped % 60),
                                                    fontSize = 7.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }

                                    // 周视图短时段悬浮气泡
                                    if (ghostHeightDp < 24f) {
                                        val tooltipOffset = if (ghostTopDp >= 24f) (ghostTopDp - 22f) else (ghostTopDp + ghostHeightDp + 2f)
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = DopamineBlue,
                                            shadowElevation = 4.dp,
                                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.8f)),
                                            modifier = Modifier
                                                .offset(x = (dayColWidth * colIdx - 8.dp).coerceAtLeast(0.dp), y = tooltipOffset.dp)
                                                .padding(horizontal = 2.dp)
                                        ) {
                                            Text(
                                                text = "%02d:%02d-%02d:%02d".format(sSnapped / 60, sSnapped % 60, eSnapped / 60, eSnapped % 60),
                                                fontSize = 7.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                // 日程事件色块 (支持长按拖动移动时间)
                                schedules.forEach { item ->
                                    val startDt = parseDateTime(item.startTime)
                                    if (startDt != null) {
                                        val dayIndex = weekDates.indexOf(startDt.toLocalDate())
                                        if (dayIndex in 0..6) {
                                            val startMinute = startDt.hour * 60 + startDt.minute
                                            val duration = item.estimatedMinutes.coerceIn(20, 360)
                                            val topY = (startMinute.toFloat() / 60f) * hourHeightDpValue
                                            val blockH = (duration.toFloat() / 60f) * hourHeightDpValue

                                            val accentColor = tagColorMap[item.category] ?: when (item.workType) {
                                                "DEEP_WORK" -> DopamineBlue
                                                "SHALLOW_WORK" -> DopamineAmber
                                                "REST_BUFFER" -> DopamineGreen
                                                else -> DopaminePurple
                                            }

                                            val isDone = item.status == "COMPLETED"
                                            val isMovingThis = movingScheduleItem?.id == item.id

                                            Surface(
                                                onClick = { onItemClick(item) },
                                                shape = RoundedCornerShape(4.dp),
                                                color = (if (isDone) DarkCard else accentColor).copy(
                                                    alpha = if (isMovingThis) 0.15f else if (LocalAppColors.current.isDark) 0.32f else 0.18f
                                                ),
                                                border = BorderStroke(1.dp, if (isDone) DarkBorder else accentColor.copy(alpha = 0.75f)),
                                                modifier = Modifier
                                                    .offset(x = dayColWidth * dayIndex + 1.dp, y = topY.dp)
                                                    .width(dayColWidth - 2.dp)
                                                    .height(blockH.dp)
                                                    .pointerInput(item.id + "_week_move", isLocked, hourHeightDpValue) {
                                                        if (!isLocked) {
                                                            detectDragGesturesAfterLongPress(
                                                                onDragStart = {
                                                                    movingScheduleItem = item
                                                                    movingDragDeltaY = 0f
                                                                    movingOriginalTopDp = topY
                                                                },
                                                                onDrag = { change, dragAmount ->
                                                                    change.consume()
                                                                    movingDragDeltaY += dragAmount.y
                                                                },
                                                                onDragEnd = {
                                                                    if (movingScheduleItem != null) {
                                                                        val currentTopDp = (movingOriginalTopDp + movingDragDeltaY / currentDensity.density).coerceIn(0f, 23.5f * currentHourHeightDpValue)
                                                                        val targetMinRaw = (currentTopDp / currentHourHeightDpValue * 60f)
                                                                        val targetMinSnapped = targetMinRaw.roundToInt().coerceIn(0, 1440 - item.estimatedMinutes)
                                                                        val curDate = startDt.toLocalDate()
                                                                        val newStart = curDate.atTime(targetMinSnapped / 60, targetMinSnapped % 60)
                                                                        val newEnd = newStart.plusMinutes(item.estimatedMinutes.toLong())
                                                                        val updated = item.copy(
                                                                            startTime = newStart.format(DateTimeFormatter.ISO_DATE_TIME),
                                                                            endTime = newEnd.format(DateTimeFormatter.ISO_DATE_TIME),
                                                                            updatedAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString()
                                                                        )
                                                                        currentOnUpdateSchedule(updated)
                                                                        movingScheduleItem = null
                                                                        movingDragDeltaY = 0f
                                                                    }
                                                                },
                                                                onDragCancel = {
                                                                    movingScheduleItem = null
                                                                    movingDragDeltaY = 0f
                                                                }
                                                            )
                                                        }
                                                    }
                                            ) {
                                                Column(modifier = Modifier.padding(horizontal = 2.dp, vertical = if (blockH < 22f) 1.dp else 2.dp)) {
                                                    Text(
                                                        text = item.title,
                                                        fontSize = if (blockH < 22f) 7.5.sp else 8.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        lineHeight = if (blockH < 22f) 9.sp else 10.5.sp,
                                                        maxLines = if (blockH >= 32f) 2 else 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        textDecoration = if (isDone) TextDecoration.LineThrough else null,
                                                        color = if (isDone) TextMuted else TextPrimary
                                                    )
                                                    if (blockH >= 28f) {
                                                        val endDt = startDt.plusMinutes(item.estimatedMinutes.coerceIn(5, 1440).toLong())
                                                        Text(
                                                            text = "%02d:%02d-%02d:%02d".format(startDt.hour, startDt.minute, endDt.hour, endDt.minute),
                                                            fontSize = 7.sp,
                                                            color = if (isDone) TextMuted else TextSecondary,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 🎯 待排期任务拖动时：仅在进入主视图前显示过渡卡片，进入主视图后直接在时间轴内预览
    if (draggingBacklogItem != null && backlogDragTouchInRoot != Offset.Zero && backlogDragTouchInRoot.y < weekGridRootY) {
        val draggedColor = tagColorMap[draggingBacklogItem!!.category] ?: when (draggingBacklogItem!!.workType) {
            "DEEP_WORK" -> DopamineBlue
            "SHALLOW_WORK" -> DopamineAmber
            "REST_BUFFER" -> DopamineGreen
            else -> DopaminePurple
        }
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = draggedColor.copy(alpha = 0.95f),
            shadowElevation = 12.dp,
            border = BorderStroke(1.5.dp, Color.White),
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (backlogDragTouchInRoot.x - weekViewRootOffset.x - 70.dp.toPx()).roundToInt(),
                        y = (backlogDragTouchInRoot.y - weekViewRootOffset.y - 18.dp.toPx()).roundToInt()
                    )
                }
                .graphicsLayer {
                    scaleX = 1.02f
                    scaleY = 1.02f
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("✊", fontSize = 12.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = draggingBacklogItem!!.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.35f)
                ) {
                    Text(
                        text = "${draggingBacklogItem!!.estimatedMinutes}m",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "拖动中",
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

    // 周历框选排期弹窗 (DragRangeScheduleDialog)
    if (dragSelectedRange != null) {
        DragRangeScheduleDialog(
            range = dragSelectedRange!!,
            backlogItems = backlogItems,
            availableTags = availableTags,
            existingSchedules = schedules,
            onDismiss = { dragSelectedRange = null },
            onAddSchedule = { title, workType, category, mins, startStr, endStr, reminderMinutes, reminderType ->
                onAddScheduleWithRange(title, workType, category, mins, startStr, endStr, reminderMinutes, reminderType)
            },
            onAssignBacklog = { item, startDt, endDt, mins ->
                val updated = item.copy(
                    status = "PENDING",
                    startTime = startDt.format(DateTimeFormatter.ISO_DATE_TIME),
                    endTime = endDt.format(DateTimeFormatter.ISO_DATE_TIME),
                    estimatedMinutes = mins,
                    updatedAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString()
                )
                onUpdateSchedule(updated)
            }
        )
    }
}


// -----------------------------------------------------------------------------------------
// 电脑版单日 24 小时时间轴轨道网格 (Day Grid Track - 支持拖动框选、拖拽移动与时长调整)
// -----------------------------------------------------------------------------------------
@Composable
fun ScheduleDayGridView(
    schedules: List<ScheduleEntity>,
    backlogItems: List<ScheduleEntity> = emptyList(),
    timeMarkers: List<TimeMarkerEntity> = emptyList(),
    onMarkerClick: (TimeMarkerEntity) -> Unit = {},
    onUpdateTimeMarker: (TimeMarkerEntity) -> Unit = {},
    selectedDate: LocalDate,
    availableTags: List<ScheduleTagEntity> = emptyList(),
    tagColorMap: Map<String, Color> = emptyMap(),
    isLocked: Boolean = false,
    onSelectDate: (LocalDate) -> Unit,
    onItemClick: (ScheduleEntity) -> Unit,
    onScheduleBacklog: (ScheduleEntity, LocalDateTime) -> Unit = { _, _ -> },
    onAddNewScheduleAtHour: (Int) -> Unit = {},
    onAddScheduleWithRange: (String, String, String, Int, String, String, Int, String) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onUpdateSchedule: (ScheduleEntity) -> Unit = {}
) {
    val currentWeekMonday = remember(selectedDate) { selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    val weekDates = remember(currentWeekMonday) { (0..6).map { currentWeekMonday.plusDays(it.toLong()) } }

    // 日视图缩放支持 (Zoom Scale State: 0.7x ~ 2.5x)
    var dayZoomFactor by rememberSaveable { mutableFloatStateOf(1.0f) }
    val hourHeightDpValue = 50f * dayZoomFactor
    val hourHeight = hourHeightDpValue.dp
    val timeColWidth = 36.dp
    val verticalScrollState = rememberScrollState()
    val density = LocalDensity.current

    val currentHourHeightDpValue by rememberUpdatedState(hourHeightDpValue)
    val currentSelectedDate by rememberUpdatedState(selectedDate)
    val currentDensity by rememberUpdatedState(density)
    val currentIsLocked by rememberUpdatedState(isLocked)
    val currentOnUpdateSchedule by rememberUpdatedState(onUpdateSchedule)
    val currentOnMarkerClick by rememberUpdatedState(onMarkerClick)
    val currentOnUpdateTimeMarker by rememberUpdatedState(onUpdateTimeMarker)
    val currentOnScheduleBacklog by rememberUpdatedState(onScheduleBacklog)
    val currentOnItemClick by rememberUpdatedState(onItemClick)

    val daySchedules = remember(schedules, selectedDate) {
        schedules.filter { item ->
            val dt = parseDateTime(item.startTime)
            dt?.toLocalDate() == selectedDate
        }
    }

    val dayMarkers = remember(timeMarkers, selectedDate) {
        timeMarkers.filter {
            it.isDeleted == 0 && (it.date == selectedDate.toString() || it.startTime.startsWith(selectedDate.toString()))
        }
    }
    val rangeMarkers = remember(dayMarkers) { dayMarkers.filter { it.markerType == "RANGE" } }
    val pointMarkers = remember(dayMarkers) { dayMarkers.filter { it.markerType != "RANGE" } }

    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    var dayViewRootOffset by remember { mutableStateOf(Offset.Zero) }
    var dayDragTouchInRoot by remember { mutableStateOf(Offset.Zero) }

    // 待办拖拽与排期状态
    var draggingItem by remember { mutableStateOf<ScheduleEntity?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var chipYInRoot by remember { mutableFloatStateOf(0f) }
    var timelineYInRoot by remember { mutableFloatStateOf(0f) }
    var isBacklogBarExpanded by remember { mutableStateOf(true) }
    var quickScheduleTargetItem by remember { mutableStateOf<ScheduleEntity?>(null) }
    var clickedHourForBacklog by remember { mutableIntStateOf(-1) }
    var selectedTimeIndicatorMinute by remember { mutableStateOf<Int?>(null) }

    val checkScheduleOverlap: (Int, Int, String?) -> ScheduleEntity? = remember(daySchedules) {
        { startMin, endMin, excludeScheduleId ->
            daySchedules.firstOrNull { other ->
                if (other.id == excludeScheduleId || other.status == "CANCELLED") return@firstOrNull false
                val sDt = parseDateTime(other.startTime) ?: return@firstOrNull false
                val otherStartMin = sDt.hour * 60 + sDt.minute
                val otherEndMin = otherStartMin + other.estimatedMinutes
                maxOf(startMin, otherStartMin) < minOf(endMin, otherEndMin)
            }
        }
    }
    val currentCheckScheduleOverlap by rememberUpdatedState(checkScheduleOverlap)

    val computeDragMinutes: (Offset, Offset) -> Pair<Int, Int> = { start, curr ->
        val minY = minOf(start.y, curr.y).coerceAtLeast(0f)
        val maxY = maxOf(start.y, curr.y).coerceAtMost(24 * currentHourHeightDpValue * currentDensity.density)

        val startMinExact = (minY / currentDensity.density / currentHourHeightDpValue * 60f)
        val endMinExact = (maxY / currentDensity.density / currentHourHeightDpValue * 60f)
        val sSnapped = startMinExact.roundToInt().coerceIn(0, 1439)
        val rawEndSnapped = endMinExact.roundToInt().coerceIn(1, 1440)
        val eSnapped = maxOf(sSnapped + 1, rawEndSnapped)
        sSnapped to eSnapped
    }

    val autoSequenceSchedule: () -> Unit = {
        if (currentIsLocked) {
            Toast.makeText(context, "🔒 表格已锁定防误触，请先点击右下角小锁解锁", Toast.LENGTH_SHORT).show()
        } else if (backlogItems.isEmpty()) {
            Toast.makeText(context, "待办箱为空，暂无可排任务", Toast.LENGTH_SHORT).show()
        } else {
            var cursorMinute = if (selectedTimeIndicatorMinute != null) {
                selectedTimeIndicatorMinute!!
            } else if (currentSelectedDate == LocalDate.now()) {
                val now = LocalDateTime.now()
                val curM = now.hour * 60 + now.minute
                val rem = curM % 5
                if (rem == 0) curM else curM + (5 - rem)
            } else {
                9 * 60 // 09:00
            }

            val activeIntervals = daySchedules.mapNotNull { s ->
                if (s.status == "CANCELLED") return@mapNotNull null
                val dt = parseDateTime(s.startTime) ?: return@mapNotNull null
                val sMin = dt.hour * 60 + dt.minute
                val eMin = sMin + s.estimatedMinutes
                sMin to eMin
            }.sortedBy { it.first }

            var scheduledCount = 0
            backlogItems.forEach { item ->
                val duration = item.estimatedMinutes.coerceIn(5, 720)
                while (cursorMinute + duration <= 1440) {
                    val endMinute = cursorMinute + duration
                    val conflict = activeIntervals.firstOrNull { (start, end) ->
                        maxOf(cursorMinute, start) < minOf(endMinute, end)
                    }
                    if (conflict != null) {
                        cursorMinute = conflict.second
                    } else {
                        break
                    }
                }

                if (cursorMinute + duration <= 1440) {
                    val startDt = currentSelectedDate.atTime(cursorMinute / 60, cursorMinute % 60)
                    currentOnScheduleBacklog(item, startDt)
                    cursorMinute += duration
                    scheduledCount++
                }
            }

            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            Toast.makeText(context, "⚡ 智能顺序排期完成: 已紧凑排入 ${scheduledCount} 项待办", Toast.LENGTH_SHORT).show()
        }
    }

    val executeDropDay: (ScheduleEntity) -> Unit = { item ->
        if (currentIsLocked) {
            Toast.makeText(context, "🔒 表格已锁定防误触，请先点击右下角小锁解锁", Toast.LENGTH_SHORT).show()
        } else {
            val currentScroll = verticalScrollState.value
            val dropGlobalY = if (dayDragTouchInRoot != Offset.Zero) dayDragTouchInRoot.y else (chipYInRoot + dragOffsetY)
            if (dropGlobalY >= timelineYInRoot) {
                val relativeY = (dropGlobalY - timelineYInRoot + currentScroll).coerceAtLeast(0f)
                val relDp = relativeY / currentDensity.density
                val previewBlockH = (item.estimatedMinutes.toFloat() / 60f * currentHourHeightDpValue).coerceAtLeast(28f)
                val blockTopDp = (relDp - previewBlockH / 2f).coerceIn(0f, (24f * currentHourHeightDpValue - previewBlockH).coerceAtLeast(0f))
                val totalMinutes = (blockTopDp / currentHourHeightDpValue) * 60f
                val snappedMinutes = totalMinutes.roundToInt().coerceIn(0, (1440 - item.estimatedMinutes).coerceAtLeast(0))
                val dropHour = snappedMinutes / 60
                val dropMinute = snappedMinutes % 60
                val targetDt = currentSelectedDate.atTime(dropHour, dropMinute)
                val conflict = currentCheckScheduleOverlap(snappedMinutes, snappedMinutes + item.estimatedMinutes, null)

                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnScheduleBacklog(item, targetDt)
                if (conflict != null) {
                    Toast.makeText(context, "已排期: ${item.title} 至 %02d:%02d (注意与「%s」重叠)".format(dropHour, dropMinute, conflict.title), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "已排期: ${item.title} 至 %02d:%02d".format(dropHour, dropMinute), Toast.LENGTH_SHORT).show()
                }
            }
        }
        draggingItem = null
        dayDragTouchInRoot = Offset.Zero
        dragOffsetY = 0f
    }

    // 空白网格拖拽框选排期状态 (Drag-to-select range)
    var isDragSelecting by remember { mutableStateOf(false) }
    var dragSelectStartOffset by remember { mutableStateOf<Offset?>(null) }
    var dragSelectCurrentOffset by remember { mutableStateOf<Offset?>(null) }
    var dragSelectedRange by remember { mutableStateOf<DragRangeData?>(null) }

    // 已有日程卡片长按拖拽移时 (Drag-to-move schedule)
    var movingScheduleItem by remember { mutableStateOf<ScheduleEntity?>(null) }
    var movingDragDeltaY by remember { mutableFloatStateOf(0f) }
    var movingOriginalTopDp by remember { mutableFloatStateOf(0f) }

    // 真实时间标记长按拖拽移时 (Drag-to-move time marker)
    var movingMarkerItem by remember { mutableStateOf<TimeMarkerEntity?>(null) }
    var movingMarkerDragDeltaY by remember { mutableFloatStateOf(0f) }
    var movingMarkerOriginalTopDp by remember { mutableFloatStateOf(0f) }

    // 已有日程卡片底边拖拽调整时长 (Drag-to-resize duration)
    var resizingScheduleItem by remember { mutableStateOf<ScheduleEntity?>(null) }
    var resizeDeltaY by remember { mutableFloatStateOf(0f) }

    // 计划日程 vs 实际作息双轨对照模式开关 (Dual Track Mode) —— 默认开启
    var isDualTrackMode by rememberSaveable {
        mutableStateOf(com.renly.rmf.domain.service.SchedulePreferences.isDualTrackMode(context))
    }

    var hasInitializedDayScroll by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!hasInitializedDayScroll) {
            hasInitializedDayScroll = true
            val currentHour = LocalDateTime.now().hour
            verticalScrollState.scrollTo((currentHour - 2).coerceAtLeast(0) * 115)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { dayViewRootOffset = it.positionInRoot() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
        // 1. 周日期横向选择器
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            weekDates.forEach { date ->
                val isSelected = date == selectedDate
                val isToday = date == LocalDate.now()
                val weekdayName = when (date.dayOfWeek) {
                    DayOfWeek.MONDAY -> "一"
                    DayOfWeek.TUESDAY -> "二"
                    DayOfWeek.WEDNESDAY -> "三"
                    DayOfWeek.THURSDAY -> "四"
                    DayOfWeek.FRIDAY -> "五"
                    DayOfWeek.SATURDAY -> "六"
                    DayOfWeek.SUNDAY -> "日"
                }

                Surface(
                    onClick = { onSelectDate(date) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) DopamineBlue else if (isToday) DopamineBlue.copy(alpha = 0.15f) else DarkSurface,
                    border = BorderStroke(1.dp, if (isSelected) DopamineBlue else DarkBorder.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 1.5.dp)
                        .height(42.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = weekdayName,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) (if (LocalAppColors.current.isDark) DarkBg else Color.White) else TextSecondary
                        )
                        Text(
                            text = date.dayOfMonth.toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) (if (LocalAppColors.current.isDark) DarkBg else Color.White) else TextPrimary
                        )
                    }
                }
            }
        }

        // 2. 📥 待排期任务快捷拖拽/点选托盘 (若有待办任务则醒目呈现)
        if (backlogItems.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = DarkCard,
                border = BorderStroke(1.dp, DopamineAmber.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp)
                    .onGloballyPositioned { coords ->
                        chipYInRoot = coords.positionInRoot().y
                    }
            ) {
                Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { isBacklogBarExpanded = !isBacklogBarExpanded }
                        ) {
                            Text("📥", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "待排期任务 (${backlogItems.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DopamineAmber
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isBacklogBarExpanded) "收起 ▾" else "展开 ▸",
                                fontSize = 9.5.sp,
                                color = TextMuted
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                onClick = autoSequenceSchedule,
                                shape = RoundedCornerShape(12.dp),
                                color = DopamineBlue.copy(alpha = 0.15f),
                                border = BorderStroke(0.8.dp, DopamineBlue.copy(alpha = 0.5f)),
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("⚡ 智能顺序排期", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = DopamineBlue)
                                }
                            }

                            Text(
                                text = "长按拖入 / 点选",
                                fontSize = 8.5.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    if (isBacklogBarExpanded) {
                        Spacer(modifier = Modifier.height(3.dp))
                        val backlogScrollState = rememberScrollState()
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(backlogScrollState, enabled = draggingItem == null)
                        ) {
                            backlogItems.forEach { item ->
                                val chipColor = tagColorMap[item.category] ?: when (item.workType) {
                                    "DEEP_WORK" -> DopamineBlue
                                    "SHALLOW_WORK" -> DopamineAmber
                                    "REST_BUFFER" -> DopamineGreen
                                    else -> DopaminePurple
                                }

                                var dayChipPosInRoot by remember { mutableStateOf(Offset.Zero) }
                                val isBeingDragged = draggingItem?.id == item.id

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = chipColor.copy(alpha = if (isBeingDragged) 0.35f else 0.16f),
                                    border = BorderStroke(1.dp, chipColor.copy(alpha = if (isBeingDragged) 0.35f else 0.7f)),
                                    modifier = Modifier
                                        .onGloballyPositioned { coords -> dayChipPosInRoot = coords.positionInRoot() }
                                        .pointerInput(item.id, hourHeightDpValue) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                var isDragging = false
                                                val touchSlop = viewConfiguration.touchSlop

                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                                    if (!change.pressed) {
                                                        if (isDragging) {
                                                            change.consume()
                                                            executeDropDay(item)
                                                        } else {
                                                            if (isLocked) {
                                                                Toast.makeText(context, "🔒 表格已锁定防误触，请先点击右下角小锁解锁", Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                quickScheduleTargetItem = item
                                                            }
                                                        }
                                                        break
                                                    }

                                                    if (!isDragging) {
                                                        val dragDistance = (change.position - down.position).getDistance()
                                                        if (dragDistance > touchSlop) {
                                                            isDragging = true
                                                            change.consume()
                                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            draggingItem = item
                                                            dayDragTouchInRoot = dayChipPosInRoot + change.position
                                                            dragOffsetY = dayDragTouchInRoot.y - chipYInRoot
                                                        }
                                                    } else {
                                                        change.consume()
                                                        dayDragTouchInRoot = dayChipPosInRoot + change.position
                                                        dragOffsetY = dayDragTouchInRoot.y - chipYInRoot
                                                    }
                                                }

                                                if (isDragging && draggingItem != null) {
                                                    draggingItem = null
                                                    dayDragTouchInRoot = Offset.Zero
                                                    dragOffsetY = 0f
                                                }
                                            }
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = item.title,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${item.estimatedMinutes}m",
                                            fontSize = 9.sp,
                                            color = chipColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 快捷操作提示条 & 双轨对照切换开关
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isLocked) Color(0xFFF59E0B).copy(alpha = 0.12f) else DarkCard.copy(alpha = 0.6f),
            border = if (isLocked) BorderStroke(0.8.dp, Color(0xFFF59E0B).copy(alpha = 0.45f)) else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isLocked) "🔒" else "💡", fontSize = 9.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isLocked) "表格已锁定防误触 · 点击右下角小锁解锁" else "长按框选 · 拖拽日程/标记移时 · 拉伸时长",
                        fontSize = 8.5.sp,
                        color = if (isLocked) Color(0xFFF59E0B) else TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 双轨对照开关胶囊
                Surface(
                    onClick = {
                        val newMode = !isDualTrackMode
                        isDualTrackMode = newMode
                        com.renly.rmf.domain.service.SchedulePreferences.setDualTrackMode(context, newMode)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDualTrackMode) DopamineBlue else DarkSurface,
                    border = BorderStroke(0.8.dp, if (isDualTrackMode) DopamineBlue else DarkBorder),
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isDualTrackMode) "⚖️ 双轨对照 开" else "⚖️ 双轨对照 关",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDualTrackMode) Color.White else TextSecondary
                        )
                    }
                }
            }
        }

        // 双轨模式轨道列头 (预计排期 vs 实际作息)
        if (isDualTrackMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = timeColWidth, end = 4.dp, top = 1.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = DopamineBlue.copy(alpha = 0.12f),
                    modifier = Modifier.weight(0.58f).padding(end = 2.dp)
                ) {
                    Text(
                        text = "📋 预计排期日程",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = DopamineBlue,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = DopamineGreen.copy(alpha = 0.12f),
                    modifier = Modifier.weight(0.42f).padding(start = 2.dp)
                ) {
                    Text(
                        text = "📍 实际作息打卡",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = DopamineGreen,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(1.dp))

        // 3. 24 小时时间轴轨道（支持双指捏合无极缩放）
        Surface(
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
            color = DarkSurface,
            border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coords ->
                        timelineYInRoot = coords.positionInRoot().y
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } >= 2) {
                                    val zoomChange = event.calculateZoom()
                                    if (zoomChange != 1f && zoomChange > 0f) {
                                        dayZoomFactor = (dayZoomFactor * zoomChange).coerceIn(0.6f, 2.5f)
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .verticalScroll(
                        verticalScrollState,
                        enabled = draggingItem == null && movingScheduleItem == null && movingMarkerItem == null && resizingScheduleItem == null && !isDragSelecting
                    )
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.width(timeColWidth)) {
                        (0..23).forEach { h ->
                            Box(
                                modifier = Modifier
                                    .height(hourHeight)
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!isLocked) {
                                            dragSelectedRange = DragRangeData(
                                                date = selectedDate,
                                                startHour = h,
                                                startMinute = 0,
                                                endHour = (h + 1).coerceAtMost(24),
                                                endMinute = 0,
                                                durationMinutes = 60
                                            )
                                        }
                                    },
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Text(
                                    text = "%02d:00".format(h),
                                    fontSize = 9.sp,
                                    color = TextMuted,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                            }
                        }
                    }

                    // 24 小时网格内容容器 (支持长按手势框选排期)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(hourHeight * 24)
                            .pointerInput(isLocked, hourHeightDpValue, selectedDate) {
                                if (!isLocked) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { offset ->
                                            isDragSelecting = true
                                            dragSelectStartOffset = offset
                                            dragSelectCurrentOffset = offset
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            dragSelectCurrentOffset = change.position
                                        },
                                        onDragEnd = {
                                            if (isDragSelecting && dragSelectStartOffset != null && dragSelectCurrentOffset != null) {
                                                val (sSnapped, eSnapped) = computeDragMinutes(dragSelectStartOffset!!, dragSelectCurrentOffset!!)

                                                dragSelectedRange = DragRangeData(
                                                    date = currentSelectedDate,
                                                    startHour = sSnapped / 60,
                                                    startMinute = sSnapped % 60,
                                                    endHour = eSnapped / 60,
                                                    endMinute = eSnapped % 60,
                                                    durationMinutes = eSnapped - sSnapped
                                                )
                                            }
                                            isDragSelecting = false
                                            dragSelectStartOffset = null
                                            dragSelectCurrentOffset = null
                                        },
                                        onDragCancel = {
                                            isDragSelecting = false
                                            dragSelectStartOffset = null
                                            dragSelectCurrentOffset = null
                                        }
                                    )
                                }
                            }
                    ) {
                        // 单日网格背景点击交互 (捕获单点时间标尺，不阻断父级长按拖选)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(isLocked, hourHeightDpValue) {
                                    if (!isLocked) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            val downTime = System.currentTimeMillis()
                                            var upChange: PointerInputChange? = null
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                if (change.isConsumed) return@awaitEachGesture
                                                if (change.changedToUp()) {
                                                    upChange = change
                                                    break
                                                }
                                            }
                                            if (upChange != null) {
                                                val elapsed = System.currentTimeMillis() - downTime
                                                val distance = (upChange.position - down.position).getDistance()
                                                if (elapsed < 450 && distance < 20f) {
                                                    val rawMin = (upChange.position.y / currentDensity.density / currentHourHeightDpValue * 60f)
                                                    val tappedMin = rawMin.roundToInt().coerceIn(0, 1439)
                                                    if (selectedTimeIndicatorMinute == tappedMin) {
                                                        selectedTimeIndicatorMinute = null
                                                    } else {
                                                        selectedTimeIndicatorMinute = tappedMin
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                        )

                        // 每小时网格分割线
                        (0..23).forEach { h ->
                            HorizontalDivider(
                                modifier = Modifier.offset(y = hourHeight * h),
                                color = DarkBorder.copy(alpha = 0.35f),
                                thickness = 0.8.dp
                            )
                        }

                        // 双轨对照模式中轴垂直分割线 (58% 计划 | 42% 实际标记)
                        if (isDualTrackMode) {
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val splitX = maxWidth * 0.58f
                                Box(
                                    modifier = Modifier
                                        .offset(x = splitX)
                                        .width(1.2.dp)
                                        .height(hourHeight * 24)
                                        .background(DopamineBlue.copy(alpha = 0.35f))
                                )
                            }
                        }

                        // 今日当前时间红线
                        if (selectedDate == LocalDate.now()) {
                            val now = LocalDateTime.now()
                            val currentMinute = now.hour * 60 + now.minute
                            val nowY = (currentMinute.toFloat() / 60f) * hourHeightDpValue
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.offset(y = (nowY - 2).dp).fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(DopamineRed, CircleShape)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.8.dp)
                                        .background(DopamineRed)
                                )
                            }
                        }

                        // 选中时间灰色标尺指示线 (单点具体时间，像红线那样显示灰色标尺与胶囊)
                        if (selectedTimeIndicatorMinute != null) {
                            val selMin = selectedTimeIndicatorMinute!!
                            val selY = (selMin.toFloat() / 60f) * hourHeightDpValue
                            val isDark = LocalAppColors.current.isDark
                            val greyLineColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                            val badgeBgColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .offset(y = (selY - 11).dp)
                                    .fillMaxWidth()
                                    .zIndex(15f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = badgeBgColor,
                                    border = BorderStroke(1.dp, greyLineColor),
                                    shadowElevation = 4.dp,
                                    modifier = Modifier.height(22.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .background(greyLineColor, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "%02d:%02d".format(selMin / 60, selMin % 60),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "+排期",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DopamineBlue,
                                            modifier = Modifier.clickable {
                                                val startH = selMin / 60
                                                val startM = selMin % 60
                                                val dur = 30
                                                val endTotal = (selMin + dur).coerceAtMost(1440)
                                                dragSelectedRange = DragRangeData(
                                                    date = selectedDate,
                                                    startHour = startH,
                                                    startMinute = startM,
                                                    endHour = endTotal / 60,
                                                    endMinute = endTotal % 60,
                                                    durationMinutes = dur
                                                )
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "清除标尺",
                                            tint = greyLineColor,
                                            modifier = Modifier
                                                .size(11.dp)
                                                .clickable {
                                                    selectedTimeIndicatorMinute = null
                                                }
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(1.8.dp)
                                        .background(greyLineColor)
                                )
                            }
                        }

                        // 动态待办拖拽预览色块 (拖入主视图后直接绘制在时间轴内并随手拖动预览)
                        if (draggingItem != null) {
                            val currentScroll = verticalScrollState.value
                            val dropGlobalY = if (dayDragTouchInRoot != Offset.Zero) dayDragTouchInRoot.y else (chipYInRoot + dragOffsetY)
                            if (dropGlobalY >= timelineYInRoot) {
                                val relativeY = (dropGlobalY - timelineYInRoot + currentScroll).coerceAtLeast(0f)
                                val relDp = relativeY / density.density
                                val previewBlockH = (draggingItem!!.estimatedMinutes.toFloat() / 60f * hourHeightDpValue).coerceAtLeast(28f)
                                val blockTopDp = (relDp - previewBlockH / 2f).coerceIn(0f, (24f * hourHeightDpValue - previewBlockH).coerceAtLeast(0f))
                                val totalMinutes = (blockTopDp / hourHeightDpValue) * 60f
                                val snappedMinutes = totalMinutes.roundToInt().coerceIn(0, (1440 - draggingItem!!.estimatedMinutes).coerceAtLeast(0))
                                val previewHour = snappedMinutes / 60
                                val previewMinute = snappedMinutes % 60
                                val endMinutesTotal = (snappedMinutes + draggingItem!!.estimatedMinutes).coerceAtMost(1440)
                                val endHour = endMinutesTotal / 60
                                val endMinute = endMinutesTotal % 60
                                val snappedBlockTopDp = (snappedMinutes.toFloat() / 60f) * hourHeightDpValue

                                val conflict = checkScheduleOverlap(snappedMinutes, endMinutesTotal, null)
                                val isConflict = conflict != null
                                val baseColor = tagColorMap[draggingItem!!.category] ?: when (draggingItem!!.workType) {
                                    "DEEP_WORK" -> DopamineBlue
                                    "SHALLOW_WORK" -> DopamineAmber
                                    "REST_BUFFER" -> DopamineGreen
                                    else -> DopaminePurple
                                }
                                val draggedColor = if (isConflict) DopamineRed else baseColor

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = (if (isConflict) DopamineRed else draggedColor).copy(alpha = if (isConflict) 0.5f else 0.42f),
                                    border = BorderStroke(2.dp, draggedColor),
                                    modifier = Modifier
                                        .offset(y = snappedBlockTopDp.dp)
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp)
                                        .height(previewBlockH.dp)
                                        .zIndex(30f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.5.dp)
                                                .height(24.dp)
                                                .background(draggedColor, RoundedCornerShape(2.dp))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (isConflict) "⚠️ 与「${conflict?.title}」冲突" else draggingItem!!.title,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isConflict) Color.White else TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "📍 %02d:%02d - %02d:%02d · %d分钟".format(
                                                    previewHour,
                                                    previewMinute,
                                                    endHour,
                                                    endMinute,
                                                    draggingItem!!.estimatedMinutes
                                                ),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = draggedColor
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 手势框选时间段的实时高亮指示框 (Drag-to-Select Ghost Box)
                        if (isDragSelecting && dragSelectStartOffset != null && dragSelectCurrentOffset != null) {
                            val (sSnapped, eSnapped) = computeDragMinutes(dragSelectStartOffset!!, dragSelectCurrentOffset!!)
                            val durationM = eSnapped - sSnapped

                            val ghostTopDp = (sSnapped.toFloat() / 60f) * hourHeightDpValue
                            val ghostHeightDp = maxOf(14f, (durationM.toFloat() / 60f) * hourHeightDpValue)

                            val conflict = currentCheckScheduleOverlap(sSnapped, eSnapped, null)
                            val isConflict = conflict != null
                            val ghostColor = if (isConflict) DopamineAmber else DopamineBlue

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ghostColor.copy(alpha = if (isConflict) 0.45f else 0.35f),
                                border = BorderStroke(1.8.dp, if (isConflict) DopamineRed else ghostColor),
                                modifier = Modifier
                                    .offset(y = ghostTopDp.dp)
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp)
                                    .height(ghostHeightDp.dp)
                            ) {
                                if (ghostHeightDp >= 38f) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp)
                                    ) {
                                        Text(if (isConflict) "⚠️" else "⏱️", fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isConflict) {
                                                "与「${conflict?.title}」冲突 · %02d:%02d-%02d:%02d (%dm)".format(
                                                    sSnapped / 60, sSnapped % 60, eSnapped / 60, eSnapped % 60, durationM
                                                )
                                            } else {
                                                "框选排期: %02d:%02d - %02d:%02d (%d分钟) · 松开即可排期".format(
                                                    sSnapped / 60, sSnapped % 60, eSnapped / 60, eSnapped % 60, durationM
                                                )
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                } else if (ghostHeightDp >= 24f) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        Text(if (isConflict) "⚠️" else "⏱️", fontSize = 10.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isConflict) {
                                                "与「${conflict?.title}」重叠 (%d分)".format(durationM)
                                            } else {
                                                "%02d:%02d - %02d:%02d (%d分)".format(
                                                    sSnapped / 60, sSnapped % 60, eSnapped / 60, eSnapped % 60, durationM
                                                )
                                            },
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            // 悬浮时间提示胶囊：当框选时间较短时，浮于选区上方/下方，保证文字绝不挤压裁切
                            if (ghostHeightDp < 38f) {
                                val tooltipOffset = if (ghostTopDp >= 32f) (ghostTopDp - 28f) else (ghostTopDp + ghostHeightDp + 4f)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isConflict) DopamineRed else DopamineBlue,
                                    shadowElevation = 6.dp,
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                                    modifier = Modifier
                                        .offset(y = tooltipOffset.dp)
                                        .padding(horizontal = 12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(if (isConflict) "⚠️" else "⏱️", fontSize = 10.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "框选: %02d:%02d - %02d:%02d (%d分钟) · 松开排期".format(
                                                sSnapped / 60, sSnapped % 60, eSnapped / 60, eSnapped % 60, durationM
                                            ),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        // 真实时间段标记 (Range Markers: 如实际午休、睡眠、锻炼等底色区块)
                        rangeMarkers.forEach { marker ->
                            key(marker.id) {
                                val sDt = parseDateTime(marker.startTime)
                            val eDt = parseDateTime(marker.endTime)
                            if (sDt != null && eDt != null) {
                                val sMin = sDt.hour * 60 + sDt.minute
                                val eMin = eDt.hour * 60 + eDt.minute
                                val durationM = (eMin - sMin).coerceIn(5, 1440)
                                val topY = (sMin.toFloat() / 60f) * hourHeightDpValue
                                val blockH = (durationM.toFloat() / 60f) * hourHeightDpValue
                                val markerColor = try {
                                    Color(android.graphics.Color.parseColor(marker.colorHex))
                                } catch (_: Exception) {
                                    DopamineCyan
                                }

                                val isMovingThis = movingMarkerItem?.id == marker.id

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = markerColor.copy(alpha = if (isMovingThis) 0.05f else 0.12f),
                                    border = BorderStroke(
                                        if (isMovingThis) 2.dp else 1.2.dp,
                                        markerColor.copy(alpha = if (isMovingThis) 0.9f else 0.5f)
                                    ),
                                    modifier = (if (isDualTrackMode) {
                                        Modifier
                                            .fillMaxWidth(0.42f)
                                            .align(Alignment.TopEnd)
                                    } else {
                                        Modifier.fillMaxWidth()
                                    })
                                        .offset(y = topY.dp)
                                        .padding(horizontal = 2.dp)
                                        .height(blockH.dp)
                                        .zIndex(if (isMovingThis) 20f else 5f)
                                        .pointerInput(marker.id + "_move", isLocked, hourHeightDpValue, selectedDate) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                if (isLocked) {
                                                    while (true) {
                                                        val event = awaitPointerEvent()
                                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                        if (change.changedToUp()) {
                                                            change.consume()
                                                            currentOnMarkerClick(marker)
                                                            break
                                                        }
                                                    }
                                                } else {
                                                    val drag = awaitLongPressOrCancellation(down.id)
                                                    if (drag != null) {
                                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        movingMarkerItem = marker
                                                        movingMarkerDragDeltaY = 0f
                                                        movingMarkerOriginalTopDp = topY
                                                        drag.consume()

                                                        while (true) {
                                                            val event = awaitPointerEvent()
                                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                            if (change.changedToUp()) {
                                                                change.consume()
                                                                if (movingMarkerItem != null) {
                                                                    val currentTopDp = (movingMarkerOriginalTopDp + movingMarkerDragDeltaY / currentDensity.density).coerceIn(0f, 23.5f * currentHourHeightDpValue)
                                                                    val targetMinExact = (currentTopDp / currentHourHeightDpValue * 60f)
                                                                    val targetMinSnapped = targetMinExact.roundToInt().coerceIn(0, 1440 - durationM)
                                                                    val targetStartHour = targetMinSnapped / 60
                                                                    val targetStartMin = targetMinSnapped % 60
                                                                    val targetEndMinTotal = targetMinSnapped + durationM
                                                                    val targetEndHour = (targetEndMinTotal / 60).coerceIn(0, 24)
                                                                    val targetEndMin = targetEndMinTotal % 60

                                                                    val newStart = currentSelectedDate.atTime(targetStartHour, targetStartMin)
                                                                    val newEnd = currentSelectedDate.atTime(targetEndHour, targetEndMin)
                                                                    val dateStr = currentSelectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                                                    val updated = marker.copy(
                                                                        date = dateStr,
                                                                        startTime = newStart.format(DateTimeFormatter.ISO_DATE_TIME),
                                                                        endTime = newEnd.format(DateTimeFormatter.ISO_DATE_TIME),
                                                                        updatedAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString()
                                                                    )
                                                                    currentOnUpdateTimeMarker(updated)
                                                                    Toast.makeText(context, "📍 已将「${marker.name}」调整至 %02d:%02d ~ %02d:%02d".format(targetStartHour, targetStartMin, targetEndHour, targetEndMin), Toast.LENGTH_SHORT).show()
                                                                    movingMarkerItem = null
                                                                    movingMarkerDragDeltaY = 0f
                                                                }
                                                                break
                                                            }
                                                            val dragDelta = change.position - change.previousPosition
                                                            change.consume()
                                                            movingMarkerDragDeltaY += dragDelta.y
                                                        }
                                                    } else {
                                                        val up = currentEvent.changes.firstOrNull { it.id == down.id }
                                                        if (up != null && up.changedToUp()) {
                                                            up.consume()
                                                            onMarkerClick(marker)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(marker.icon, fontSize = 11.5.sp, modifier = Modifier.padding(top = 1.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = marker.name,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = markerColor
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "%02d:%02d~%02d:%02d (%dm)".format(sDt.hour, sDt.minute, eDt.hour, eDt.minute, durationM),
                                                    fontSize = 9.5.sp,
                                                    color = markerColor.copy(alpha = 0.85f)
                                                )
                                            }
                                            if (marker.notes.isNotBlank() && blockH >= 34f) {
                                                Text(
                                                    text = marker.notes,
                                                    fontSize = 9.sp,
                                                    color = TextSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = markerColor.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "实际时间段",
                                                fontSize = 8.5.sp,
                                                color = markerColor,
                                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                        // 已排期日程卡片 (支持长按拖拽移时、底部手柄拉伸缩放时长)
                        daySchedules.forEach { item ->
                            key(item.id) {
                                val startDt = parseDateTime(item.startTime)
                            if (startDt != null) {
                                val startMinute = startDt.hour * 60 + startDt.minute
                                val origDuration = item.estimatedMinutes.coerceIn(1, 1440)
                                val isResizingThis = resizingScheduleItem?.id == item.id
                                val maxAllowedDuration = 1440 - startMinute
                                // 拖拽手柄保持原卡片尺寸，避免手柄在手指下漂移移动；实时扩展高度通过浮层即时呈现
                                val displayDuration = origDuration

                                val topY = (startMinute.toFloat() / 60f) * hourHeightDpValue
                                val blockH = (displayDuration.toFloat() / 60f) * hourHeightDpValue

                                val accentColor = tagColorMap[item.category] ?: when (item.workType) {
                                    "DEEP_WORK" -> DopamineBlue
                                    "SHALLOW_WORK" -> DopamineAmber
                                    "REST_BUFFER" -> DopamineGreen
                                    else -> DopaminePurple
                                }

                                val isDone = item.status == "COMPLETED"
                                val isMovingThis = movingScheduleItem?.id == item.id

                                Surface(
                                    onClick = { onItemClick(item) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = (if (isDone) DarkSurface else accentColor).copy(
                                        alpha = if (isMovingThis) 0.15f else if (LocalAppColors.current.isDark) 0.25f else 0.15f
                                    ),
                                    border = BorderStroke(
                                        if (isResizingThis) 2.dp else 1.dp,
                                        if (isDone) DarkBorder else if (isResizingThis) DopamineAmber else accentColor.copy(alpha = 0.7f)
                                    ),
                                    modifier = (if (isDualTrackMode) {
                                        Modifier
                                            .fillMaxWidth(0.58f)
                                            .align(Alignment.TopStart)
                                    } else {
                                        Modifier.fillMaxWidth()
                                    })
                                        .offset(y = topY.dp)
                                        .padding(horizontal = 4.dp)
                                        .height(blockH.dp)
                                        .pointerInput(item.id + "_move", isLocked, hourHeightDpValue, selectedDate) {
                                            if (!isLocked) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        movingScheduleItem = item
                                                        movingDragDeltaY = 0f
                                                        movingOriginalTopDp = topY
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        movingDragDeltaY += dragAmount.y
                                                    },
                                                    onDragEnd = {
                                                        if (movingScheduleItem != null) {
                                                            val currentTopDp = (movingOriginalTopDp + movingDragDeltaY / currentDensity.density).coerceIn(0f, 23.5f * currentHourHeightDpValue)
                                                            val targetMinExact = (currentTopDp / currentHourHeightDpValue * 60f)
                                                            val targetMinSnapped = targetMinExact.roundToInt().coerceIn(0, 1440 - item.estimatedMinutes)
                                                            val targetEndMinTotal = targetMinSnapped + item.estimatedMinutes
                                                            val conflict = currentCheckScheduleOverlap(targetMinSnapped, targetEndMinTotal, item.id)
                                                            val newStart = currentSelectedDate.atTime(targetMinSnapped / 60, targetMinSnapped % 60)
                                                            val newEnd = newStart.plusMinutes(item.estimatedMinutes.toLong())
                                                            val updated = item.copy(
                                                                startTime = newStart.format(DateTimeFormatter.ISO_DATE_TIME),
                                                                endTime = newEnd.format(DateTimeFormatter.ISO_DATE_TIME),
                                                                updatedAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString()
                                                            )
                                                            currentOnUpdateSchedule(updated)
                                                            if (conflict != null) {
                                                                Toast.makeText(context, "已移至 %02d:%02d (注意与「%s」重叠)".format(targetMinSnapped / 60, targetMinSnapped % 60, conflict.title), Toast.LENGTH_SHORT).show()
                                                            }
                                                            movingScheduleItem = null
                                                            movingDragDeltaY = 0f
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        movingScheduleItem = null
                                                        movingDragDeltaY = 0f
                                                    }
                                                )
                                            }
                                        }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        val endDt = startDt.plusMinutes(displayDuration.toLong())
                                        if (blockH < 34f) {
                                            // 极简紧凑单行模式：当日程时间块较短（如 15~30 分钟）时，单行排版，彻底解决文字挤压与截断问题
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(3.dp)
                                                        .height((blockH - 6f).coerceIn(6f, 16f).dp)
                                                        .background(accentColor, RoundedCornerShape(1.5.dp))
                                                )
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = item.title,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textDecoration = if (isDone) TextDecoration.LineThrough else null,
                                                    color = if (isDone) TextMuted else TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "%02d:%02d-%02d:%02d (%dm)".format(
                                                        startDt.hour,
                                                        startDt.minute,
                                                        endDt.hour,
                                                        endDt.minute,
                                                        displayDuration
                                                    ),
                                                    fontSize = 9.sp,
                                                    color = if (isResizingThis) DopamineAmber else TextSecondary,
                                                    maxLines = 1
                                                )
                                            }
                                        } else {
                                            // 宽裕双行模式 (>= 34dp)
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(3.5.dp)
                                                            .height(if (blockH < 48f) 18.dp else 28.dp)
                                                            .background(accentColor, RoundedCornerShape(2.dp))
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = item.title,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            textDecoration = if (isDone) TextDecoration.LineThrough else null,
                                                            color = if (isDone) TextMuted else TextPrimary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        if (blockH >= 42f) {
                                                            Spacer(modifier = Modifier.height(1.dp))
                                                            Text(
                                                                text = "%02d:%02d - %02d:%02d · %d分钟 · %s".format(
                                                                    startDt.hour,
                                                                    startDt.minute,
                                                                    endDt.hour,
                                                                    endDt.minute,
                                                                    displayDuration,
                                                                    when (item.workType) {
                                                                        "DEEP_WORK" -> "深度攻坚"
                                                                        "SHALLOW_WORK" -> "日常浅度"
                                                                        "REST_BUFFER" -> "休整缓冲"
                                                                        else -> "协同沟通"
                                                                    }
                                                                ),
                                                                fontSize = 10.sp,
                                                                color = if (isResizingThis) DopamineAmber else TextSecondary
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (!isLocked && blockH >= 24f) {
                                            // 底边时长调节手柄 (Drag Handle to Resize Duration)
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .fillMaxWidth()
                                                    .height(if (blockH < 34f) 8.dp else 12.dp)
                                                    .pointerInput(item.id + "_resize", isLocked, hourHeightDpValue) {
                                                        detectDragGestures(
                                                            onDragStart = {
                                                                resizingScheduleItem = item
                                                                resizeDeltaY = 0f
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                resizeDeltaY += dragAmount.y
                                                            },
                                                            onDragEnd = {
                                                                if (resizingScheduleItem != null) {
                                                                    val rawMins = resizeDeltaY / currentDensity.density / currentHourHeightDpValue * 60f
                                                                    val extraMins = rawMins.roundToInt()
                                                                    val curStart = parseDateTime(item.startTime) ?: currentSelectedDate.atTime(9, 0)
                                                                    val startMin = curStart.hour * 60 + curStart.minute
                                                                    val maxAllowedMins = 1440 - startMin
                                                                    val newMins = (item.estimatedMinutes + extraMins).coerceIn(1, maxOf(1, maxAllowedMins))
                                                                    val newEnd = curStart.plusMinutes(newMins.toLong())
                                                                    val updated = item.copy(
                                                                        estimatedMinutes = newMins,
                                                                        endTime = newEnd.format(DateTimeFormatter.ISO_DATE_TIME),
                                                                        updatedAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString()
                                                                    )
                                                                    currentOnUpdateSchedule(updated)
                                                                    resizingScheduleItem = null
                                                                    resizeDeltaY = 0f
                                                                }
                                                            },
                                                            onDragCancel = {
                                                                resizingScheduleItem = null
                                                                resizeDeltaY = 0f
                                                            }
                                                        )
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(28.dp)
                                                        .height(2.5.dp)
                                                        .background(
                                                            if (isResizingThis) DopamineAmber else accentColor.copy(alpha = 0.7f),
                                                            RoundedCornerShape(1.5.dp)
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                        // 真实时间点标记 (Point Markers: 如实际起床、就寝、实际开工等横向标尺线与图钉标签)
                        pointMarkers.forEach { marker ->
                            key(marker.id) {
                                val sDt = parseDateTime(marker.startTime)
                            if (sDt != null) {
                                val sMin = sDt.hour * 60 + sDt.minute
                                val topY = (sMin.toFloat() / 60f) * hourHeightDpValue
                                val markerColor = try {
                                    Color(android.graphics.Color.parseColor(marker.colorHex))
                                } catch (_: Exception) {
                                    DopamineAmber
                                }

                                val isMovingThis = movingMarkerItem?.id == marker.id

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.Transparent,
                                    modifier = (if (isDualTrackMode) {
                                        Modifier
                                            .fillMaxWidth(0.42f)
                                            .align(Alignment.TopEnd)
                                    } else {
                                        Modifier.fillMaxWidth()
                                    })
                                        .offset(y = (topY - 11).dp)
                                        .height(24.dp)
                                        .zIndex(if (isMovingThis) 22f else 8f)
                                        .pointerInput(marker.id + "_move", isLocked, hourHeightDpValue, selectedDate) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                if (isLocked) {
                                                    while (true) {
                                                        val event = awaitPointerEvent()
                                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                        if (change.changedToUp()) {
                                                            change.consume()
                                                            currentOnMarkerClick(marker)
                                                            break
                                                        }
                                                    }
                                                } else {
                                                    val drag = awaitLongPressOrCancellation(down.id)
                                                    if (drag != null) {
                                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        movingMarkerItem = marker
                                                        movingMarkerDragDeltaY = 0f
                                                        movingMarkerOriginalTopDp = topY
                                                        drag.consume()

                                                        while (true) {
                                                            val event = awaitPointerEvent()
                                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                            if (change.changedToUp()) {
                                                                change.consume()
                                                                if (movingMarkerItem != null) {
                                                                    val currentTopDp = (movingMarkerOriginalTopDp + movingMarkerDragDeltaY / currentDensity.density).coerceIn(0f, 23.8f * currentHourHeightDpValue)
                                                                    val targetMinExact = (currentTopDp / currentHourHeightDpValue * 60f)
                                                                    val targetMinSnapped = targetMinExact.roundToInt().coerceIn(0, 1439)
                                                                    val targetHour = targetMinSnapped / 60
                                                                    val targetMinute = targetMinSnapped % 60
                                                                    val newStart = currentSelectedDate.atTime(targetHour, targetMinute)
                                                                    val dateStr = currentSelectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                                                    val updated = marker.copy(
                                                                        date = dateStr,
                                                                        startTime = newStart.format(DateTimeFormatter.ISO_DATE_TIME),
                                                                        updatedAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString()
                                                                    )
                                                                    currentOnUpdateTimeMarker(updated)
                                                                    Toast.makeText(context, "📍 已将「${marker.name}」调整至 %02d:%02d".format(targetHour, targetMinute), Toast.LENGTH_SHORT).show()
                                                                    movingMarkerItem = null
                                                                    movingMarkerDragDeltaY = 0f
                                                                }
                                                                break
                                                            }
                                                            val dragDelta = change.position - change.previousPosition
                                                            change.consume()
                                                            movingMarkerDragDeltaY += dragDelta.y
                                                        }
                                                    } else {
                                                        val up = currentEvent.changes.firstOrNull { it.id == down.id }
                                                        if (up != null && up.changedToUp()) {
                                                            up.consume()
                                                            onMarkerClick(marker)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .height(20.dp)
                                                .shadow(if (isMovingThis) 0.dp else 3.dp, RoundedCornerShape(10.dp))
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isMovingThis) markerColor.copy(alpha = 0.35f) else markerColor)
                                                .then(
                                                    if (isMovingThis) Modifier.border(1.dp, markerColor, RoundedCornerShape(10.dp))
                                                    else Modifier
                                                )
                                                .padding(horizontal = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(marker.icon, fontSize = 10.sp)
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "${marker.name} %02d:%02d".format(sDt.hour, sDt.minute),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.Black
                                                )
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(if (isMovingThis) 1.dp else 1.6.dp)
                                                .background(markerColor.copy(alpha = if (isMovingThis) 0.35f else 0.75f))
                                        )
                                    }
                                }
                            }
                        }
                    }

                        // 已有日程卡片长按拖拽移时实时预览指示虚影 (Moving Schedule Hover Preview)
                        if (movingScheduleItem != null) {
                            val item = movingScheduleItem!!
                            val currentTopDp = (movingOriginalTopDp + movingDragDeltaY / density.density).coerceIn(0f, 23.5f * hourHeightDpValue)
                            val targetMinExact = (currentTopDp / hourHeightDpValue * 60f)
                            val targetMinSnapped = targetMinExact.roundToInt().coerceIn(0, 1440 - item.estimatedMinutes)
                            val targetStartHour = targetMinSnapped / 60
                            val targetStartMin = targetMinSnapped % 60
                            val targetEndMinTotal = targetMinSnapped + item.estimatedMinutes
                            val targetEndHour = (targetEndMinTotal / 60).coerceIn(0, 24)
                            val targetEndMin = targetEndMinTotal % 60

                            val snappedTopDp = (targetMinSnapped.toFloat() / 60f) * hourHeightDpValue
                            val previewHeightDp = (item.estimatedMinutes.toFloat() / 60f * hourHeightDpValue).coerceAtLeast(24f)

                            val conflict = checkScheduleOverlap(targetMinSnapped, targetEndMinTotal, item.id)
                            val isConflict = conflict != null
                            val strokeColor = if (isConflict) DopamineRed else DopamineAmber

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = (if (isConflict) DopamineRed else DopamineAmber).copy(alpha = if (isConflict) 0.5f else 0.38f),
                                border = BorderStroke(2.dp, strokeColor),
                                modifier = Modifier
                                    .offset(y = snappedTopDp.dp)
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp)
                                    .height(previewHeightDp.dp)
                                    .zIndex(30f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text(if (isConflict) "⚠️" else "🎯", fontSize = 11.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isConflict) {
                                            "与「${conflict?.title}」重叠 · %02d:%02d - %02d:%02d".format(
                                                targetStartHour, targetStartMin, targetEndHour, targetEndMin
                                            )
                                        } else {
                                            "移动至 %02d:%02d - %02d:%02d · %s (%dm)".format(
                                                targetStartHour, targetStartMin, targetEndHour, targetEndMin, item.title, item.estimatedMinutes
                                            )
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // 已有日程卡片底边拖拽调整时长实时预览指示虚影 (Resizing Schedule Hover Preview)
                        if (resizingScheduleItem != null) {
                            val item = resizingScheduleItem!!
                            val curStart = parseDateTime(item.startTime) ?: selectedDate.atTime(9, 0)
                            val startMinute = curStart.hour * 60 + curStart.minute
                            val rawMins = resizeDeltaY / density.density / hourHeightDpValue * 60f
                            val extraMins = rawMins.roundToInt()
                            val maxAllowedMins = 1440 - startMinute
                            val newMins = (item.estimatedMinutes + extraMins).coerceIn(1, maxOf(1, maxAllowedMins))
                            val curEnd = curStart.plusMinutes(newMins.toLong())

                            val topY = (startMinute.toFloat() / 60f) * hourHeightDpValue
                            val previewHeightDp = (newMins.toFloat() / 60f * hourHeightDpValue).coerceAtLeast(18f)

                            val conflict = checkScheduleOverlap(startMinute, startMinute + newMins, item.id)
                            val isConflict = conflict != null
                            val strokeColor = if (isConflict) DopamineRed else DopamineAmber

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = (if (isConflict) DopamineRed else DopamineAmber).copy(alpha = if (isConflict) 0.5f else 0.42f),
                                border = BorderStroke(2.dp, strokeColor),
                                shadowElevation = 6.dp,
                                modifier = Modifier
                                    .offset(y = topY.dp)
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp)
                                    .height(previewHeightDp.dp)
                                    .zIndex(31f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text(if (isConflict) "⚠️" else "⏱️", fontSize = 11.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isConflict) {
                                            "碰撞冲突: 与「${conflict?.title}」重叠 (%d分钟)".format(newMins)
                                        } else {
                                            "调整至 %02d:%02d (%d分钟)".format(
                                                curEnd.hour, curEnd.minute, newMins
                                            )
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // 真实时间标记长按拖拽移时实时预览指示虚影 (Moving Marker Hover Preview)
                        if (movingMarkerItem != null) {
                            val marker = movingMarkerItem!!
                            val markerColor = try {
                                Color(android.graphics.Color.parseColor(marker.colorHex))
                            } catch (_: Exception) {
                                DopamineAmber
                            }
                            val currentTopDp = (movingMarkerOriginalTopDp + movingMarkerDragDeltaY / density.density).coerceIn(0f, 23.8f * hourHeightDpValue)
                            val targetMinExact = (currentTopDp / hourHeightDpValue * 60f)

                            if (marker.markerType == "RANGE") {
                                val sDt = parseDateTime(marker.startTime)
                                val eDt = parseDateTime(marker.endTime)
                                val origDurationM = if (sDt != null && eDt != null) {
                                    ((eDt.hour * 60 + eDt.minute) - (sDt.hour * 60 + sDt.minute)).coerceIn(1, 1440)
                                } else 60

                                val targetMinSnapped = targetMinExact.roundToInt().coerceIn(0, 1440 - origDurationM)
                                val targetStartHour = targetMinSnapped / 60
                                val targetStartMin = targetMinSnapped % 60
                                val targetEndMinTotal = targetMinSnapped + origDurationM
                                val targetEndHour = (targetEndMinTotal / 60).coerceIn(0, 24)
                                val targetEndMin = targetEndMinTotal % 60
                                val snappedTopDp = (targetMinSnapped.toFloat() / 60f) * hourHeightDpValue
                                val previewHeightDp = (origDurationM.toFloat() / 60f * hourHeightDpValue).coerceAtLeast(24f)

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = markerColor.copy(alpha = 0.45f),
                                    border = BorderStroke(2.dp, markerColor),
                                    shadowElevation = 8.dp,
                                    modifier = Modifier
                                        .offset(y = snappedTopDp.dp)
                                        .fillMaxWidth()
                                        .padding(horizontal = 2.dp)
                                        .height(previewHeightDp.dp)
                                        .zIndex(32f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        Text(marker.icon, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "移动至 %02d:%02d - %02d:%02d · %s (%d分)".format(
                                                targetStartHour, targetStartMin, targetEndHour, targetEndMin, marker.name, origDurationM
                                            ),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            } else {
                                val targetMinSnapped = targetMinExact.roundToInt().coerceIn(0, 1439)
                                val targetHour = targetMinSnapped / 60
                                val targetMinute = targetMinSnapped % 60
                                val snappedTopDp = (targetMinSnapped.toFloat() / 60f) * hourHeightDpValue

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .offset(y = (snappedTopDp - 11f).dp)
                                        .fillMaxWidth()
                                        .zIndex(32f)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = markerColor,
                                        shadowElevation = 8.dp,
                                        border = BorderStroke(1.5.dp, Color.White),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        ) {
                                            Text(marker.icon, fontSize = 11.sp)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${marker.name} %02d:%02d · 松开保存".format(targetHour, targetMinute),
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Black
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(2.2.dp)
                                            .background(markerColor)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. 点击待办 Chip 弹出的快捷时段排期弹窗
        if (quickScheduleTargetItem != null) {
            val item = quickScheduleTargetItem!!
            AlertDialog(
                onDismissRequest = { quickScheduleTargetItem = null },
                containerColor = DarkCard,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚡ 快捷排期: ${item.title}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("预估耗时: ${item.estimatedMinutes} 分钟，请选择排入时间点：", fontSize = 11.5.sp, color = TextSecondary)
                        val now = LocalDateTime.now()
                        val presets = listOf(
                            "⚡ 现在执行 (%02d:%02d)".format(now.hour, now.minute) to now,
                            "🌅 上午 09:00" to selectedDate.atTime(9, 0),
                            "☀️ 下午 14:00" to selectedDate.atTime(14, 0),
                            "☕ 下午 16:30" to selectedDate.atTime(16, 30),
                            "🌙 今晚 20:00" to selectedDate.atTime(20, 0)
                        )
                        presets.forEach { (label, dt) ->
                            Surface(
                                onClick = {
                                    onScheduleBacklog(item, dt)
                                    quickScheduleTargetItem = null
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = DarkSurface,
                                border = BorderStroke(1.dp, DopamineBlue.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                                ) {
                                    Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { quickScheduleTargetItem = null }) {
                        Text("取消", color = TextSecondary)
                    }
                }
            )
        }

        // 5. 点击时间轴空白小时弹出的快捷填入弹窗
        if (clickedHourForBacklog != -1) {
            val targetHour = clickedHourForBacklog
            AlertDialog(
                onDismissRequest = { clickedHourForBacklog = -1 },
                containerColor = DarkCard,
                title = {
                    Text("🕒 %02d:00 快速排期".format(targetHour), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (backlogItems.isNotEmpty()) {
                            Text("📥 从待办箱选择填入此时间段:", fontSize = 12.sp, color = DopamineAmber, fontWeight = FontWeight.SemiBold)
                            backlogItems.take(5).forEach { item ->
                                Surface(
                                    onClick = {
                                        val dt = selectedDate.atTime(targetHour, 0)
                                        onScheduleBacklog(item, dt)
                                        clickedHourForBacklog = -1
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = DarkSurface,
                                    border = BorderStroke(1.dp, DarkBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Text("⚡", fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            item.title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text("${item.estimatedMinutes}m", fontSize = 10.5.sp, color = DopamineBlue, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            HorizontalDivider(color = DarkBorder, thickness = 0.8.dp)
                        }
                        Button(
                            onClick = {
                                val h = clickedHourForBacklog
                                clickedHourForBacklog = -1
                                onAddNewScheduleAtHour(h)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("＋ 在此时间段新建日程", color = if (LocalAppColors.current.isDark) DarkBg else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { clickedHourForBacklog = -1 }) {
                        Text("取消", color = TextSecondary)
                    }
                }
            )
        }

        // 6. 日历空白拖动框选排期弹窗 (DragRangeScheduleDialog)
        if (dragSelectedRange != null) {
            DragRangeScheduleDialog(
                range = dragSelectedRange!!,
                backlogItems = backlogItems,
                availableTags = availableTags,
                existingSchedules = daySchedules,
                onDismiss = { dragSelectedRange = null },
                onAddSchedule = { title, workType, category, mins, startStr, endStr, reminderMinutes, reminderType ->
                    onAddScheduleWithRange(title, workType, category, mins, startStr, endStr, reminderMinutes, reminderType)
                },
                onAssignBacklog = { item, startDt, endDt, mins ->
                    val updated = item.copy(
                        status = "PENDING",
                        startTime = startDt.format(DateTimeFormatter.ISO_DATE_TIME),
                        endTime = endDt.format(DateTimeFormatter.ISO_DATE_TIME),
                        estimatedMinutes = mins,
                        updatedAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString()
                    )
                    onUpdateSchedule(updated)
                }
            )
        }
    }

    // 🎯 单日视图下待排期任务拖动时：仅在进入主视图前显示过渡卡片，进入主视图后直接在时间轴内预览
    if (draggingItem != null && dayDragTouchInRoot != Offset.Zero && dayDragTouchInRoot.y < timelineYInRoot) {
        val draggedColor = tagColorMap[draggingItem!!.category] ?: when (draggingItem!!.workType) {
            "DEEP_WORK" -> DopamineBlue
            "SHALLOW_WORK" -> DopamineAmber
            "REST_BUFFER" -> DopamineGreen
            else -> DopaminePurple
        }
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = draggedColor.copy(alpha = 0.95f),
            shadowElevation = 12.dp,
            border = BorderStroke(1.5.dp, Color.White),
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (dayDragTouchInRoot.x - dayViewRootOffset.x - 70.dp.toPx()).roundToInt(),
                        y = (dayDragTouchInRoot.y - dayViewRootOffset.y - 18.dp.toPx()).roundToInt()
                    )
                }
                .graphicsLayer {
                    scaleX = 1.02f
                    scaleY = 1.02f
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("✊", fontSize = 12.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = draggingItem!!.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.35f)
                ) {
                    Text(
                        text = "${draggingItem!!.estimatedMinutes}m",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "拖动中",
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}
}

// -----------------------------------------------------------------------------------------
// 📥 电脑版 1:1 待排期任务箱弹窗 (BacklogModal)
// -----------------------------------------------------------------------------------------
@Composable
fun BacklogDialog(
    backlogItems: List<ScheduleEntity>,
    availableTags: List<ScheduleTagEntity> = emptyList(),
    tagColorMap: Map<String, Color> = emptyMap(),
    onDismiss: () -> Unit,
    onAddBacklog: (String, String, String, Int, String, String) -> Unit,
    onScheduleToToday: (ScheduleEntity) -> Unit,
    onToggleStatus: (ScheduleEntity) -> Unit,
    onDelete: (String) -> Unit,
    onOpenAddTag: () -> Unit = {},
    onOpenManageTags: () -> Unit = {}
) {
    var newTitle by remember { mutableStateOf("") }
    var newMinutes by remember { mutableIntStateOf(30) }
    var newWorkType by remember { mutableStateOf("DEEP_WORK") }
    var newCategory by remember { mutableStateOf(availableTags.firstOrNull()?.name ?: "工作") }
    var newDod by remember { mutableStateOf("") }
    var newQuadrant by remember { mutableStateOf("Q2") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 600.dp),
        containerColor = DarkCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📥 待排期任务池", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = DopamineAmber.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${backlogItems.size}项",
                        fontSize = 11.sp,
                        color = DopamineAmber,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "存放尚未确定执行时段的零散待办，点击「排期」随时投射到日历中。",
                    fontSize = 11.5.sp,
                    color = TextSecondary
                )

                // 快速收集输入框
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        placeholder = { Text("录入新待办事项...", fontSize = 12.sp, color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DopamineAmber,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = {
                            if (newTitle.isNotBlank()) {
                                onAddBacklog(newTitle, newWorkType, newCategory, newMinutes, newDod, newQuadrant)
                                newTitle = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DopamineAmber),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text("存入", color = DarkBg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                // 象限归属选择
                Text("🎯 象限归属:", fontSize = 11.sp, color = TextSecondary)
                val backlogQuadrantOptions = listOf(
                    "Q1" to ("🔥 Q1 重要且紧急" to "危机与突发，截止日迫在眉睫，需立刻执行"),
                    "Q2" to ("⭐ Q2 重要不紧急" to "核心战略、学习成长、深度规划与长远沉淀"),
                    "Q3" to ("⚡ Q3 紧急不重要" to "临时插单、琐事催促、代理委派，快速通关"),
                    "Q4" to ("🍃 Q4 不重要不紧急" to "休闲琐碎、低价值杂务，适当削减与断舍离")
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(backlogQuadrantOptions) { (qCode, qData) ->
                        val (qLabel, _) = qData
                        val isSel = newQuadrant == qCode
                        FilterChip(
                            selected = isSel,
                            onClick = { newQuadrant = qCode },
                            label = { Text(qLabel, fontSize = 10.5.sp) }
                        )
                    }
                }
                // 当前选中象限的事件类型与行动指导说明
                val currentQuadrantDesc = backlogQuadrantOptions.firstOrNull { it.first == newQuadrant }?.second?.second
                if (currentQuadrantDesc != null) {
                    Surface(
                        color = when (newQuadrant) {
                            "Q1" -> QuadrantQ1Color.copy(alpha = 0.12f)
                            "Q2" -> QuadrantQ2Color.copy(alpha = 0.12f)
                            "Q3" -> QuadrantQ3Color.copy(alpha = 0.12f)
                            else -> QuadrantQ4Color.copy(alpha = 0.12f)
                        },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "💡 $currentQuadrantDesc",
                            fontSize = 11.sp,
                            color = when (newQuadrant) {
                                "Q1" -> QuadrantQ1Color
                                "Q2" -> QuadrantQ2Color
                                "Q3" -> QuadrantQ3Color
                                else -> QuadrantQ4Color
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            lineHeight = 15.sp
                        )
                    }
                }

                if (availableTags.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("分类标签:", fontSize = 11.sp, color = TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("➕ 新增", fontSize = 11.sp, color = DopamineAmber, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onOpenAddTag() })
                            Text("⚙️ 管理", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.clickable { onOpenManageTags() })
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(availableTags) { tagItem ->
                            val tagColor = tagColorMap[tagItem.name] ?: parseHexColor(tagItem.colorHex, DopamineAmber)
                            FilterChip(
                                selected = newCategory == tagItem.name,
                                onClick = { newCategory = tagItem.name },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(tagColor)
                                    )
                                },
                                label = { Text(tagItem.name, fontSize = 11.sp) }
                            )
                        }

                        item {
                            Surface(
                                onClick = onOpenAddTag,
                                shape = RoundedCornerShape(8.dp),
                                color = DopamineAmber.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, DopamineAmber.copy(alpha = 0.4f)),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text("➕ 新增", fontSize = 11.sp, color = DopamineAmber, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = DarkBorder, thickness = 1.dp)

                if (backlogItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                        Text("待办池空空如也，随时在上方录入任务！", fontSize = 12.sp, color = TextMuted)
                    }
                } else {
                    backlogItems.forEach { item ->
                        val isDone = item.status == "COMPLETED"
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DarkSurface,
                            border = BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onToggleStatus(item) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isDone) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                        contentDescription = "完成",
                                        tint = if (isDone) DopamineGreen else TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        textDecoration = if (isDone) TextDecoration.LineThrough else null,
                                        color = if (isDone) TextMuted else TextPrimary
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "预估 ${item.estimatedMinutes}m · ${item.workType}",
                                            fontSize = 10.5.sp,
                                            color = TextSecondary
                                        )
                                        if (item.category.isNotBlank()) {
                                            val tagColor = tagColorMap[item.category] ?: DopamineAmber
                                            Surface(
                                                color = tagColor.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp),
                                                border = BorderStroke(0.5.dp, tagColor.copy(alpha = 0.5f))
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(tagColor))
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = item.category,
                                                        fontSize = 9.5.sp,
                                                        color = tagColor,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                // 一键排期到今日
                                Surface(
                                    onClick = { onScheduleToToday(item) },
                                    shape = RoundedCornerShape(6.dp),
                                    color = DopamineBlue.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, DopamineBlue.copy(alpha = 0.5f)),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                                        Text("⚡排期今日", fontSize = 10.5.sp, color = DopamineBlue, fontWeight = FontWeight.Bold)
                                    }
                                }
                                IconButton(
                                    onClick = { onDelete(item.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "删除", tint = TextMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成", color = DopamineBlue, fontWeight = FontWeight.Bold) }
        }
    )
}

// -----------------------------------------------------------------------------------------
// 🗓️ 电脑版 1:1 日期快速跳转弹窗 (GoToDateModal)
// -----------------------------------------------------------------------------------------
@Composable
fun GoToDateDialog(
    currentDate: LocalDate,
    onDismiss: () -> Unit,
    onSelectDate: (LocalDate) -> Unit
) {
    var year by remember { mutableIntStateOf(currentDate.year) }
    var month by remember { mutableIntStateOf(currentDate.monthValue) }
    var day by remember { mutableIntStateOf(currentDate.dayOfMonth) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 600.dp),
        containerColor = DarkCard,
        title = { Text("🗓️ 跳转至指定日期", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("快捷预设:", fontSize = 12.sp, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        "今天" to LocalDate.now(),
                        "明天" to LocalDate.now().plusDays(1),
                        "本周一" to LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                        "下周一" to LocalDate.now().plusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    ).forEach { (lbl, date) ->
                        Surface(
                            onClick = { onSelectDate(date) },
                            shape = RoundedCornerShape(8.dp),
                            color = DarkSurface,
                            border = BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier.weight(1f).height(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(lbl, fontSize = 11.5.sp, color = DopamineBlue)
                            }
                        }
                    }
                }

                HorizontalDivider(color = DarkBorder, thickness = 1.dp)

                Text("精确选择目标日期:", fontSize = 12.sp, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = year.toString(),
                        onValueChange = { year = it.toIntOrNull() ?: year },
                        label = { Text("年") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DopamineBlue,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = month.toString(),
                        onValueChange = { month = (it.toIntOrNull() ?: month).coerceIn(1, 12) },
                        label = { Text("月") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DopamineBlue,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = day.toString(),
                        onValueChange = { day = (it.toIntOrNull() ?: day).coerceIn(1, 31) },
                        label = { Text("日") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DopamineBlue,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val target = LocalDate.of(year, month, day.coerceAtMost(LocalDate.of(year, month, 1).lengthOfMonth()))
                        onSelectDate(target)
                    } catch (_: Exception) {
                        onSelectDate(LocalDate.now())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("立即跳转", color = DarkBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        }
    )
}

// -----------------------------------------------------------------------------------------
// ✏️ 电脑版 1:1 日程时间块全量编辑弹窗 (EventModal)
// -----------------------------------------------------------------------------------------
@Composable
fun EditScheduleDialog(
    item: ScheduleEntity,
    availableTags: List<ScheduleTagEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (ScheduleEntity) -> Unit,
    onDelete: () -> Unit,
    onOpenAddTag: () -> Unit = {},
    onOpenManageTags: () -> Unit = {}
) {
    var title by remember { mutableStateOf(item.title) }
    var workType by remember { mutableStateOf(item.workType) }
    var category by remember { mutableStateOf(item.category) }
    var minutes by remember { mutableIntStateOf(item.estimatedMinutes) }
    var dod by remember { mutableStateOf(item.dod) }
    var reminderMinutes by remember { mutableIntStateOf(item.reminderMinutes) }
    var reminderType by remember { mutableStateOf(item.reminderType) }
    var recurrenceRule by remember { mutableStateOf(item.recurrenceRule) }
    var eisenhowerQuadrant by remember { mutableStateOf(item.eisenhowerQuadrant ?: "Q2") }
    var subtasks by remember { mutableStateOf(SubTaskItem.parseList(item.subtasksJson)) }
    var newSubtaskText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 600.dp),
        containerColor = DarkCard,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("✏️ 编辑日程时间块", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "删除", tint = DopamineRed)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("任务标题") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DopamineBlue,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("🎯 艾森豪威尔四象限:", fontSize = 12.sp, color = TextSecondary)
                val quadrantOptions = listOf(
                    "Q1" to ("🔥 Q1 重要且紧急" to "危机突发与临期事件，需立刻执行"),
                    "Q2" to ("⭐ Q2 重要不紧急" to "核心战略、技能学习与深度专注，高价值投入"),
                    "Q3" to ("⚡ Q3 紧急不重要" to "临时插单、琐事催促、代理委派，快速通关"),
                    "Q4" to ("🍃 Q4 不重要不紧急" to "休闲琐碎、低价值杂务，适当削减与断舍离")
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(quadrantOptions) { (qCode, qData) ->
                        val (qLabel, _) = qData
                        val isSel = eisenhowerQuadrant == qCode
                        FilterChip(
                            selected = isSel,
                            onClick = { eisenhowerQuadrant = qCode },
                            label = { Text(qLabel, fontSize = 11.5.sp) }
                        )
                    }
                }
                val currentEditQuadrantDesc = quadrantOptions.firstOrNull { it.first == eisenhowerQuadrant }?.second?.second
                if (currentEditQuadrantDesc != null) {
                    Surface(
                        color = when (eisenhowerQuadrant) {
                            "Q1" -> QuadrantQ1Color.copy(alpha = 0.12f)
                            "Q2" -> QuadrantQ2Color.copy(alpha = 0.12f)
                            "Q3" -> QuadrantQ3Color.copy(alpha = 0.12f)
                            else -> QuadrantQ4Color.copy(alpha = 0.12f)
                        },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "💡 $currentEditQuadrantDesc",
                            fontSize = 11.sp,
                            color = when (eisenhowerQuadrant) {
                                "Q1" -> QuadrantQ1Color
                                "Q2" -> QuadrantQ2Color
                                "Q3" -> QuadrantQ3Color
                                else -> QuadrantQ4Color
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            lineHeight = 15.sp
                        )
                    }
                }

                Text("🔁 重复周期规则:", fontSize = 12.sp, color = TextSecondary)
                val recurrenceOptions = listOf(
                    "" to "不重复",
                    "DAILY" to "每天",
                    "WEEKDAYS" to "仅工作日",
                    "WEEKLY" to "每周",
                    "MONTHLY" to "每月"
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(recurrenceOptions) { (rCode, rLabel) ->
                        val isSel = recurrenceRule == rCode
                        FilterChip(
                            selected = isSel,
                            onClick = { recurrenceRule = rCode },
                            label = { Text(rLabel, fontSize = 11.5.sp) }
                        )
                    }
                }

                Text("工作类型:", fontSize = 12.sp, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("DEEP_WORK" to "深度", "SHALLOW_WORK" to "浅层", "REST_BUFFER" to "缓冲").forEach { (k, v) ->
                        FilterChip(
                            selected = workType == k,
                            onClick = { workType = k },
                            label = { Text(v, fontSize = 12.sp) }
                        )
                    }
                }

                val itemStart = remember(item.startTime) { parseDateTime(item.startTime) }
                if (workType == "DEEP_WORK" && itemStart != null) {
                    val minuteOfDay = itemStart.hour * 60 + itemStart.minute
                    if (minuteOfDay in 810..900) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = DopamineAmber.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, DopamineAmber.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = "💡 昼夜节律提示：13:30~15:00 处于人体常规餐后认知低谷期，若有条件建议调配为浅层事务或留白小憩。",
                                fontSize = 11.sp,
                                color = DopamineAmber,
                                modifier = Modifier.padding(6.dp),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                if (availableTags.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("分类标签:", fontSize = 12.sp, color = TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("➕ 新增", fontSize = 11.5.sp, color = DopamineBlue, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onOpenAddTag() })
                            Text("⚙️ 管理", fontSize = 11.5.sp, color = TextSecondary, modifier = Modifier.clickable { onOpenManageTags() })
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(availableTags) { tagItem ->
                            val tagColor = parseHexColor(tagItem.colorHex, DopamineBlue)
                            FilterChip(
                                selected = category == tagItem.name,
                                onClick = { category = tagItem.name },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(tagColor)
                                    )
                                },
                                label = { Text(tagItem.name, fontSize = 11.5.sp) }
                            )
                        }

                        item {
                            Surface(
                                onClick = onOpenAddTag,
                                shape = RoundedCornerShape(8.dp),
                                color = DopamineBlue.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, DopamineBlue.copy(alpha = 0.4f)),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text("➕ 新增", fontSize = 11.sp, color = DopamineBlue, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = dod,
                    onValueChange = { dod = it },
                    label = { Text("验收标准 (DoD)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DopamineCyan,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // 📝 子任务/检查步骤清单 (Checklist)
                Text("📝 子任务拆解清单 (${subtasks.count { it.isDone }}/${subtasks.size}):", fontSize = 12.sp, color = TextSecondary)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    subtasks.forEachIndexed { idx, st ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = DarkSurface,
                            border = BorderStroke(0.5.dp, DarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        subtasks = subtasks.toMutableList().also {
                                            it[idx] = st.copy(isDone = !st.isDone)
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = if (st.isDone) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                        contentDescription = "完成步骤",
                                        tint = if (st.isDone) DopamineGreen else TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = st.title,
                                    fontSize = 12.sp,
                                    textDecoration = if (st.isDone) TextDecoration.LineThrough else null,
                                    color = if (st.isDone) TextMuted else TextPrimary,
                                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                )
                                IconButton(
                                    onClick = {
                                        subtasks = subtasks.toMutableList().also { it.removeAt(idx) }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "删除步骤", tint = TextMuted, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    // 添加子步骤输入框
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DarkSurface,
                            border = BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.CenterStart,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp)
                            ) {
                                if (newSubtaskText.isEmpty()) {
                                    Text(
                                        "+ 添加分解步骤...",
                                        fontSize = 12.sp,
                                        color = TextMuted,
                                        maxLines = 1
                                    )
                                }
                                BasicTextField(
                                    value = newSubtaskText,
                                    onValueChange = { newSubtaskText = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontSize = 12.5.sp,
                                        color = TextPrimary
                                    ),
                                    cursorBrush = SolidColor(DopamineCyan),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        Surface(
                            onClick = {
                                if (newSubtaskText.isNotBlank()) {
                                    subtasks = subtasks + SubTaskItem(title = newSubtaskText.trim(), isDone = false)
                                    newSubtaskText = ""
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = DopamineCyan.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, DopamineCyan),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "添加", tint = DopamineCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("添加", color = DopamineCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Text("预估耗时: $minutes 分钟", fontSize = 12.sp, color = TextSecondary)
                Slider(
                    value = minutes.toFloat().coerceIn(1f, 720f),
                    onValueChange = { minutes = it.roundToInt().coerceIn(1, 720) },
                    valueRange = 1f..720f,
                    colors = SliderDefaults.colors(thumbColor = DopamineBlue, activeTrackColor = DopamineBlue)
                )
                val editQuickDurations = listOf(15, 25, 30, 45, 60, 90, 120, 180, 240)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(editQuickDurations) { d ->
                        FilterChip(
                            selected = minutes == d,
                            onClick = { minutes = d },
                            label = { Text("${d}分", fontSize = 11.sp) }
                        )
                    }
                }

                Text("⏰ 闹钟与胶囊通知:", fontSize = 12.sp, color = TextSecondary)
                val reminderOptions = listOf(
                    -1 to "不提醒",
                    0 to "准时",
                    5 to "提前5分",
                    10 to "提前10分",
                    15 to "提前15分",
                    30 to "提前30分",
                    60 to "提前1小时"
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(reminderOptions) { (mins, label) ->
                        val isSel = reminderMinutes == mins
                        FilterChip(
                            selected = isSel,
                            onClick = { reminderMinutes = mins },
                            label = { Text(label, fontSize = 11.5.sp) }
                        )
                    }
                }

                if (reminderMinutes >= 0) {
                    Text("🔔 提醒形式与闹铃模式:", fontSize = 12.sp, color = TextSecondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ReminderType.entries) { typeOption ->
                            val isSel = reminderType == typeOption.code
                            FilterChip(
                                selected = isSel,
                                onClick = { reminderType = typeOption.code },
                                label = { Text("${typeOption.emoji} ${typeOption.title}", fontSize = 11.5.sp) }
                            )
                        }
                    }
                    val selType = ReminderType.fromCode(reminderType)
                    Text(selType.description, fontSize = 11.sp, color = DopamineAmber)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = item.copy(
                        title = title,
                        workType = workType,
                        category = category,
                        estimatedMinutes = minutes,
                        dod = dod,
                        reminderMinutes = reminderMinutes,
                        reminderType = reminderType,
                        eisenhowerQuadrant = eisenhowerQuadrant,
                        recurrenceRule = recurrenceRule,
                        subtasksJson = SubTaskItem.listToJson(subtasks),
                        updatedAt = com.renly.rmf.util.TimeUtils.currentUtcIsoString()
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("保存更改", color = DarkBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        }
    )
}

// -----------------------------------------------------------------------------------------
// 日程时间块微型详情与操作弹窗
// -----------------------------------------------------------------------------------------
@Composable
fun ScheduleDetailModal(
    item: ScheduleEntity,
    tagColorMap: Map<String, Color> = emptyMap(),
    isLocked: Boolean = false,
    onToggleStatus: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onNavigateToFitness: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val isDone = item.status == "COMPLETED"
    val accentColor = tagColorMap[item.category] ?: when (item.workType) {
        "DEEP_WORK" -> DopamineBlue
        "SHALLOW_WORK" -> DopamineAmber
        "REST_BUFFER" -> DopamineGreen
        else -> DopaminePurple
    }

    val startDt = parseDateTime(item.startTime)
    val timeStr = if (startDt != null) {
        val endDt = parseDateTime(item.endTime) ?: startDt.plusMinutes(item.estimatedMinutes.toLong())
        if (startDt.toLocalDate() == endDt.toLocalDate()) {
            "%s - %02d:%02d".format(startDt.format(DateTimeFormatter.ofPattern("M月d日 HH:mm")), endDt.hour, endDt.minute)
        } else {
            "%s - %s".format(startDt.format(DateTimeFormatter.ofPattern("M月d日 HH:mm")), endDt.format(DateTimeFormatter.ofPattern("M月d日 HH:mm")))
        }
    } else {
        item.startTime
    }

    val isFitnessItem = item.id.startsWith("fitness_") || item.category == "HEALTH" || item.title.contains("健身")
    val context = androidx.compose.ui.platform.LocalContext.current
    val modalScope = rememberCoroutineScope()
    val workspaceAsset = remember(item) { com.renly.rmf.domain.service.WorkspaceLauncher.detectAsset(item) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 600.dp),
        containerColor = DarkCard,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isLocked) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                            border = BorderStroke(0.8.dp, Color(0xFFF59E0B).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "🔒 锁定防误触",
                                fontSize = 10.sp,
                                color = Color(0xFFF59E0B),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑", tint = if (isLocked) DopamineBlue.copy(alpha = 0.5f) else DopamineBlue, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "删除", tint = if (isLocked) DopamineRed.copy(alpha = 0.5f) else DopamineRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "📅 预估起止: $timeStr · ${item.estimatedMinutes}分钟", fontSize = 12.5.sp, color = TextSecondary)
                if (item.actualMinutes > 0) {
                    val diff = item.actualMinutes - item.estimatedMinutes
                    val diffText = if (diff > 0) "超时 +${diff}分钟" else if (diff < 0) "提前 ${-diff}分钟完成" else "准点完成"
                    val diffColor = if (diff > 0) DopamineAmber else DopamineGreen
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = diffColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, diffColor.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("⏱️", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "实际耗时: ${item.actualMinutes}分钟 ($diffText)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = diffColor
                            )
                        }
                    }
                }
                val rType = ReminderType.fromCode(item.reminderType)
                val reminderText = when (item.reminderMinutes) {
                    -1 -> "🔕 未开启提前提醒"
                    0 -> "⏰ 准时提醒 · ${rType.emoji} ${rType.title}"
                    60 -> "⏰ 提前1小时提醒 · ${rType.emoji} ${rType.title}"
                    else -> "⏰ 提前${item.reminderMinutes}分钟提醒 · ${rType.emoji} ${rType.title}"
                }
                Text(
                    text = reminderText,
                    fontSize = 12.sp,
                    color = if (item.reminderMinutes >= 0) DopamineAmber else TextMuted,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "🏷️ 工作类型: ${
                        when (item.workType) {
                            "DEEP_WORK" -> "深度专注"
                            "SHALLOW_WORK" -> "浅层事务"
                            "REST_BUFFER" -> "恢复缓冲"
                            else -> item.workType
                        }
                    }",
                    fontSize = 12.5.sp,
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold
                )
                if (item.category.isNotBlank()) {
                    val tagColor = tagColorMap[item.category] ?: accentColor
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "📂 业务标签: ", fontSize = 12.5.sp, color = TextSecondary)
                        Surface(
                            color = tagColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, tagColor.copy(alpha = 0.5f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(tagColor))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = item.category, fontSize = 11.sp, color = tagColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (item.dod.isNotBlank()) {
                    Text(text = "🎯 验收标准: ${item.dod}", fontSize = 12.sp, color = DopamineCyan)
                }
                if (item.description.isNotBlank()) {
                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Text(
                            text = item.description,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                if (workspaceAsset != null) {
                    Button(
                        onClick = {
                            com.renly.rmf.domain.service.WorkspaceLauncher.launchAsset(context, workspaceAsset)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DopamineCyan.copy(alpha = 0.18f)),
                        border = BorderStroke(1.dp, DopamineCyan.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("📂 直达工作区: ${workspaceAsset.displayName}", color = DopamineCyan, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (isFitnessItem) {
                    Button(
                        onClick = {
                            onDismiss()
                            onNavigateToFitness()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DopaminePurple.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, DopaminePurple.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🏋️ 直达健身模块查看与打卡", color = DopaminePurple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Button(
                    onClick = {
                        val parsedStart = parseDateTime(item.startTime) ?: LocalDateTime.now()
                        val duration = if (item.estimatedMinutes > 0) item.estimatedMinutes.toLong() else 30L
                        val parsedEnd = parseDateTime(item.endTime) ?: parsedStart.plusMinutes(duration)
                        val bufferEnd = parsedEnd.plusMinutes(15)
                        val nowIso = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                        val bufferEntity = ScheduleEntity(
                            id = java.util.UUID.randomUUID().toString(),
                            title = "☕ 呼吸缓冲休息",
                            category = "日常",
                            workType = "REST_BUFFER",
                            startTime = parsedEnd.format(DateTimeFormatter.ISO_DATE_TIME),
                            endTime = bufferEnd.format(DateTimeFormatter.ISO_DATE_TIME),
                            estimatedMinutes = 15,
                            colorHex = "#10B981",
                            status = "PENDING",
                            createdAt = nowIso,
                            updatedAt = nowIso
                        )
                        modalScope.launch {
                            val db = com.renly.rmf.data.local.RmfDatabase.getInstance(context)
                            db.scheduleDao().insertOrUpdate(bufferEntity)
                            android.widget.Toast.makeText(context, "已在任务后插入 15 分钟弹性呼吸缓冲带！", android.widget.Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DopamineGreen.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, DopamineGreen.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("🧘 插入 15m 呼吸缓冲带", color = DopamineGreen, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onToggleStatus,
                colors = ButtonDefaults.buttonColors(containerColor = if (isDone) DarkSurface else DopamineGreen),
                border = BorderStroke(1.dp, if (isDone) DarkBorder else Color.Transparent),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isDone) "设为未完成" else "✓ 标记完成",
                    color = if (isDone) TextSecondary else DarkBg,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = TextSecondary) }
        }
    )
}

fun parseDateTime(str: String): LocalDateTime? {
    val clean = str.trim().replace(" ", "T")
    return try {
        LocalDateTime.parse(clean, DateTimeFormatter.ISO_DATE_TIME)
    } catch (_: Exception) {
        try {
            LocalDateTime.parse(clean)
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun ScheduleItemCard(
    item: ScheduleEntity,
    tagColorMap: Map<String, Color> = emptyMap(),
    onToggleStatus: () -> Unit,
    onClickCard: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val isCompleted = item.status == "COMPLETED"
    val accentColor = tagColorMap[item.category] ?: when (item.workType) {
        "DEEP_WORK" -> DopamineBlue
        "SHALLOW_WORK" -> DopamineAmber
        "REST_BUFFER" -> DopamineGreen
        else -> DopaminePurple
    }

    val timeRangeStr = remember(item.startTime, item.endTime) {
        try {
            val s = parseDateTime(item.startTime)
            val e = parseDateTime(item.endTime)
            if (s != null && e != null) {
                val dateFmt = if (s.year != LocalDate.now().year) FORMATTER_YEAR_MONTH_DAY else FORMATTER_MONTH_DAY
                val dateStr = s.format(dateFmt)
                if (s.toLocalDate() == e.toLocalDate()) {
                    "$dateStr ${s.format(FORMATTER_HH_MM)} - ${e.format(FORMATTER_HH_MM)}"
                } else {
                    val endFmt = if (e.year != s.year) FORMATTER_YEAR_MONTH_DAY else FORMATTER_MONTH_DAY
                    val endDateStr = e.format(endFmt)
                    "$dateStr ${s.format(FORMATTER_HH_MM)} - $endDateStr ${e.format(FORMATTER_HH_MM)}"
                }
            } else if (s != null) {
                val dateFmt = if (s.year != LocalDate.now().year) FORMATTER_YEAR_MONTH_DAY else FORMATTER_MONTH_DAY
                "${s.format(dateFmt)} ${s.format(FORMATTER_HH_MM)}"
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    val checkScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isCompleted) 1.12f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "itemCheckScale"
    )

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClickCard()
        },
        shape = RoundedCornerShape(14.dp),
        shadowElevation = if (isCompleted) 0.dp else 2.dp,
        color = if (isCompleted) DarkSurface.copy(alpha = 0.55f) else DarkCard,
        border = BorderStroke(1.dp, if (isCompleted) DarkBorder.copy(alpha = 0.35f) else DarkBorder.copy(alpha = 0.8f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 13.dp)
        ) {
            // 垂直分类指示条 (Left Category Indicator)
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isCompleted) accentColor.copy(alpha = 0.35f) else accentColor)
            )

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleStatus()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = "完成状态",
                    tint = if (isCompleted) DopamineGreen else TextMuted,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            scaleX = checkScale
                            scaleY = checkScale
                        }
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                    color = if (isCompleted) TextMuted else TextPrimary,
                    letterSpacing = (-0.2).sp
                )

                if (timeRangeStr.isNotBlank()) {
                    Text(
                        text = "⏱️ $timeRangeStr · ${item.estimatedMinutes}分钟",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                if (item.dod.isNotBlank()) {
                    Surface(
                        color = DopamineBlue.copy(alpha = 0.10f),
                        border = BorderStroke(0.5.dp, DopamineBlue.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "验收标准: ${item.dod}",
                            fontSize = 11.sp,
                            color = DopamineBlue,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // 子任务 Checklist 进度条
                val subtasks = remember(item.subtasksJson) { SubTaskItem.parseList(item.subtasksJson) }
                if (subtasks.isNotEmpty()) {
                    val doneCount = subtasks.count { it.isDone }
                    val progress = doneCount.toFloat() / subtasks.size
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = DopamineCyan,
                            trackColor = DarkBorder
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "步骤 $doneCount/${subtasks.size}",
                            fontSize = 10.sp,
                            color = DopamineCyan,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 象限徽标
                    val q = item.eisenhowerQuadrant ?: "Q2"
                    val (qColor, qEmoji) = when (q) {
                        "Q1" -> DopamineRed to "🔥"
                        "Q2" -> DopamineBlue to "⭐"
                        "Q3" -> DopamineAmber to "⚡"
                        else -> DopamineGreen to "🍃"
                    }
                    Surface(
                        color = qColor.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, qColor.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "$qEmoji $q",
                            color = qColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    // 重复规则徽标
                    if (item.recurrenceRule.isNotBlank()) {
                        val recLabel = when (item.recurrenceRule) {
                            "DAILY" -> "每天"
                            "WEEKDAYS" -> "工作日"
                            "WEEKLY" -> "每周"
                            "MONTHLY" -> "每月"
                            else -> item.recurrenceRule
                        }
                        Surface(
                            color = DopaminePurple.copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, DopaminePurple.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "🔁 $recLabel",
                                color = DopaminePurple,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Surface(
                        color = accentColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = when (item.workType) {
                                "DEEP_WORK" -> "深度工作"
                                "SHALLOW_WORK" -> "浅层事务"
                                "REST_BUFFER" -> "恢复缓冲"
                                else -> item.workType
                            },
                            color = accentColor,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (item.category.isNotBlank()) {
                        val tagColor = tagColorMap[item.category] ?: DopamineBlue
                        Surface(
                            color = tagColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, tagColor.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(tagColor)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = item.category,
                                    color = tagColor,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScheduleDialog(
    availableTags: List<ScheduleTagEntity> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Int, Int, String) -> Unit,
    onOpenAddTag: () -> Unit = {},
    onOpenManageTags: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var title by remember { mutableStateOf("") }
    var workType by remember { mutableStateOf("DEEP_WORK") }
    var category by remember { mutableStateOf(availableTags.firstOrNull()?.name ?: "工作") }
    var minutes by remember { mutableIntStateOf(45) }
    var reminderMinutes by remember { mutableIntStateOf(10) }
    var reminderType by remember { mutableStateOf(ReminderType.ALL.code) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 600.dp),
        containerColor = DarkCard,
        title = { Text("规划新时间块", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("任务标题") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DopamineBlue,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("工作类型:", fontSize = 12.sp, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("DEEP_WORK" to "深度", "SHALLOW_WORK" to "浅层", "REST_BUFFER" to "缓冲").forEach { (k, v) ->
                        FilterChip(
                            selected = workType == k,
                            onClick = { workType = k },
                            label = { Text(v, fontSize = 12.sp) }
                        )
                    }
                }

                if (availableTags.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("分类标签:", fontSize = 12.sp, color = TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("➕ 新增", fontSize = 11.5.sp, color = DopamineBlue, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onOpenAddTag() })
                            Text("⚙️ 管理", fontSize = 11.5.sp, color = TextSecondary, modifier = Modifier.clickable { onOpenManageTags() })
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(availableTags) { tagItem ->
                            val tagColor = parseHexColor(tagItem.colorHex, DopamineBlue)
                            FilterChip(
                                selected = category == tagItem.name,
                                onClick = { category = tagItem.name },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(tagColor)
                                    )
                                },
                                label = { Text(tagItem.name, fontSize = 11.5.sp) }
                            )
                        }

                        item {
                            Surface(
                                onClick = onOpenAddTag,
                                shape = RoundedCornerShape(8.dp),
                                color = DopamineBlue.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, DopamineBlue.copy(alpha = 0.4f)),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text("➕ 新增", fontSize = 11.sp, color = DopamineBlue, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                Text("预估耗时: $minutes 分钟", fontSize = 12.sp, color = TextSecondary)
                Slider(
                    value = minutes.toFloat().coerceIn(1f, 720f),
                    onValueChange = { minutes = it.roundToInt().coerceIn(1, 720) },
                    valueRange = 1f..720f,
                    colors = SliderDefaults.colors(thumbColor = DopamineBlue, activeTrackColor = DopamineBlue)
                )
                val addQuickDurations = listOf(15, 25, 30, 45, 60, 90, 120, 180, 240)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(addQuickDurations) { d ->
                        FilterChip(
                            selected = minutes == d,
                            onClick = { minutes = d },
                            label = { Text("${d}分", fontSize = 11.sp) }
                        )
                    }
                }

                Text("⏰ 闹钟与胶囊通知:", fontSize = 12.sp, color = TextSecondary)
                val reminderOptions = listOf(
                    -1 to "不提醒",
                    0 to "准时",
                    5 to "提前5分",
                    10 to "提前10分",
                    15 to "提前15分",
                    30 to "提前30分",
                    60 to "提前1小时"
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(reminderOptions) { (mins, label) ->
                        val isSel = reminderMinutes == mins
                        FilterChip(
                            selected = isSel,
                            onClick = { reminderMinutes = mins },
                            label = { Text(label, fontSize = 11.5.sp) }
                        )
                    }
                }

                if (reminderMinutes >= 0) {
                    Text("🔔 提醒形式与闹铃模式:", fontSize = 12.sp, color = TextSecondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ReminderType.entries) { typeOption ->
                            val isSel = reminderType == typeOption.code
                            FilterChip(
                                selected = isSel,
                                onClick = { reminderType = typeOption.code },
                                label = { Text("${typeOption.emoji} ${typeOption.title}", fontSize = 11.5.sp) }
                            )
                        }
                    }
                    val selType = ReminderType.fromCode(reminderType)
                    Text(selType.description, fontSize = 11.sp, color = DopamineAmber)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title, workType, category, minutes, reminderMinutes, reminderType)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("确定排期", color = DarkBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        }
    )
}

@Composable
fun GoogleCalendarSyncModal(
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var statusText by remember { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }
    var lastSyncTime by remember { mutableStateOf(GoogleDrivePreferences.getCalendarLastSyncTime(context)) }
    val isAuthorized = GoogleDrivePreferences.isAuthorized(context)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📅", fontSize = 18.sp, modifier = Modifier.padding(end = 6.dp))
                Text("Google Calendar 日历同步", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isAuthorized) "✓ 已连接 Google 账号。可将本地日程双向同步至 Google 日历，或导出标准 .ics 文件。"
                    else "提示: 尚未配置 Google OAuth 授权，您依然可以使用标准 .ics 格式导出并添加到手机日历或 Google 日历中。",
                    fontSize = 11.5.sp,
                    color = if (isAuthorized) DopamineGreen else DopamineAmber
                )

                Text(
                    text = "上次同步时间: $lastSyncTime",
                    fontSize = 11.sp,
                    color = TextMuted
                )

                HorizontalDivider(color = DarkBorder)

                // 1. 直连 API 推送
                Button(
                    onClick = {
                        if (!isAuthorized) {
                            statusText = "请先在【工作台 -> Google Drive / Calendar】完成授权"
                            return@Button
                        }
                        isWorking = true
                        statusText = "正在推送日程至 Google Calendar..."
                        coroutineScope.launch {
                            val db = (context.applicationContext as RmfApplication).database
                            val service = GoogleCalendarSyncService(context, db)
                            val res = service.pushSchedulesToGoogleCalendar()
                            isWorking = false
                            when (res) {
                                is CalendarSyncResult.Success -> {
                                    statusText = "✓ ${res.message}"
                                    lastSyncTime = GoogleDrivePreferences.getCalendarLastSyncTime(context)
                                }
                                is CalendarSyncResult.Error -> {
                                    statusText = "✗ ${res.error}"
                                }
                            }
                        }
                    },
                    enabled = !isWorking,
                    colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🚀 推送日程至 Google Calendar (API)", fontSize = 12.sp)
                }

                // 2. 直连 API 拉取
                OutlinedButton(
                    onClick = {
                        if (!isAuthorized) {
                            statusText = "请先在【工作台 -> Google Drive / Calendar】完成授权"
                            return@OutlinedButton
                        }
                        isWorking = true
                        statusText = "正在从 Google Calendar 拉取事件..."
                        coroutineScope.launch {
                            val db = (context.applicationContext as RmfApplication).database
                            val service = GoogleCalendarSyncService(context, db)
                            val res = service.pullEventsFromGoogleCalendar(30)
                            isWorking = false
                            when (res) {
                                is CalendarSyncResult.Success -> {
                                    statusText = "✓ ${res.message}"
                                    lastSyncTime = GoogleDrivePreferences.getCalendarLastSyncTime(context)
                                }
                                is CalendarSyncResult.Error -> {
                                    statusText = "✗ ${res.error}"
                                }
                            }
                        }
                    },
                    enabled = !isWorking,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DopamineGreen),
                    border = BorderStroke(1.dp, DopamineGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("⬇ 从 Google Calendar 拉取日程", fontSize = 12.sp)
                }

                // 3. 导出 .ics 并直接分享到日历 App
                OutlinedButton(
                    onClick = {
                        isWorking = true
                        statusText = "正在导出 .ics 日历文件..."
                        coroutineScope.launch {
                            val db = (context.applicationContext as RmfApplication).database
                            val service = GoogleCalendarSyncService(context, db)
                            val res = service.exportSchedulesToIcsFile(includeCourses = true)
                            isWorking = false
                            if (res.isSuccess) {
                                val file = res.getOrThrow()
                                statusText = "✓ 已生成 .ics，正在调起日历应用..."
                                service.shareIcsFile(file)
                            } else {
                                statusText = "✗ 导出失败: ${res.exceptionOrNull()?.message}"
                            }
                        }
                    },
                    enabled = !isWorking,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📁 导出 .ics 文件并调起日历导入", fontSize = 12.sp)
                }

                if (statusText.isNotBlank()) {
                    Text(
                        text = statusText,
                        fontSize = 11.5.sp,
                        color = if (statusText.startsWith("✓")) DopamineGreen else if (statusText.startsWith("✗")) DopamineRed else DopamineCyan,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = TextPrimary)
            }
        }
    )
}

@Composable
fun AddScheduleTagDialog(
    onDismiss: () -> Unit,
    onConfirm: (ScheduleTagEntity) -> Unit
) {
    var tagName by remember { mutableStateOf("") }
    val presetColors = listOf(
        "#38BDF8", "#818CF8", "#34D399", "#F59E0B", "#EC4899",
        "#10B981", "#F43F5E", "#A855F7", "#06B6D4", "#F97316"
    )
    var selectedColor by remember { mutableStateOf(presetColors[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text("新增日程/待办分类标签", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = tagName,
                    onValueChange = { tagName = it },
                    label = { Text("标签名称", fontSize = 12.sp) },
                    placeholder = { Text("例如: 项目A、健身、阅读", fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DopamineBlue,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("选择标签颜色", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(presetColors) { hex ->
                        val color = try {
                            Color(android.graphics.Color.parseColor(hex))
                        } catch (e: Exception) {
                            DopamineBlue
                        }
                        val isSelected = selectedColor == hex
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColor = hex }
                                .then(
                                    if (isSelected) Modifier.border(2.5.dp, Color.White, CircleShape)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (tagName.isNotBlank()) {
                        val newTag = ScheduleTagEntity(
                            id = java.util.UUID.randomUUID().toString(),
                            name = tagName.trim(),
                            colorHex = selectedColor,
                            sortOrder = 0
                        )
                        onConfirm(newTag)
                    }
                },
                enabled = tagName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue)
            ) {
                Text("创建", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        }
    )
}

@Composable
fun ManageScheduleTagsDialog(
    tags: List<ScheduleTagEntity>,
    onDismiss: () -> Unit,
    onDeleteTag: (ScheduleTagEntity) -> Unit,
    onOpenAdd: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("管理日程分类标签", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                TextButton(onClick = {
                    onDismiss()
                    onOpenAdd()
                }) {
                    Text("+ 新增", color = DopamineBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
            ) {
                if (tags.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无标签。\n点击右上角「+ 新增」即可创建专属日程分类！",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(tags, key = { it.id }) { tag ->
                            val tagColor = parseHexColor(tag.colorHex, DopamineBlue)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkCard,
                                border = BorderStroke(1.dp, DarkBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(tagColor)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = tag.name,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.5.sp
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDeleteTag(tag) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "删除",
                                            tint = DopamineRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue)
            ) {
                Text("完成", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun GoogleDriveSyncStatusModal(
    onDismiss: () -> Unit,
    onOpenLanSync: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val syncStatusInfo by com.renly.rmf.domain.service.GoogleDriveSyncStateHolder.status.collectAsState()
    var isOperating by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("☁️", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Google Drive 云端同步",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkBg,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val dotColor = when (syncStatusInfo.state) {
                                com.renly.rmf.domain.service.CloudSyncState.SUCCESS -> DopamineGreen
                                com.renly.rmf.domain.service.CloudSyncState.SYNCING -> DopamineBlue
                                com.renly.rmf.domain.service.CloudSyncState.ERROR -> DopamineAmber
                                com.renly.rmf.domain.service.CloudSyncState.IDLE -> TextMuted
                            }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (syncStatusInfo.state) {
                                    com.renly.rmf.domain.service.CloudSyncState.SUCCESS -> "🟢 状态正常：已与云端保持最新"
                                    com.renly.rmf.domain.service.CloudSyncState.SYNCING -> "🔵 正在执行双向增量同步..."
                                    com.renly.rmf.domain.service.CloudSyncState.ERROR -> "🟡 离线/同步异常"
                                    com.renly.rmf.domain.service.CloudSyncState.IDLE -> "⚪ 尚未绑定 Google 账号"
                                },
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = dotColor
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "上次同步: ${if (syncStatusInfo.lastSyncTime.isNotBlank()) syncStatusInfo.lastSyncTime else "未检测到同步记录"}",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "专属目录: ${com.renly.rmf.domain.service.GoogleDriveSyncService.BACKUP_FOLDER_NAME} (Google Drive 根目录)",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkBg.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("• 行级时间戳双向增量合并 (LWW 无损融合)", fontSize = 11.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text("• 云端滚动保留最近 50 个时间戳版本快照", fontSize = 11.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text("• 切回前台 60 秒轻检测，本地就一个版本 (rmf.db)", fontSize = 11.sp, color = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    onClick = onOpenLanSync,
                    shape = RoundedCornerShape(10.dp),
                    color = DopamineBlue.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, DopamineBlue.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📡", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("局域网 Wi-Fi 直连极速同步", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("同 Wi-Fi / 热点免节点 0.1 秒秒级双向合并", fontSize = 10.5.sp, color = TextSecondary)
                            }
                        }
                        Text("进入 ›", fontSize = 12.sp, color = DopamineBlue, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (syncStatusInfo.errorDetails != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "异常原因: ${syncStatusInfo.errorDetails}",
                        fontSize = 10.5.sp,
                        color = DopamineRed
                    )
                }

                if (isOperating) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = DopamineBlue
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("正在处理云端同步，请稍候...", fontSize = 11.sp, color = DopamineBlue)
                    }
                }
            }
        },
        confirmButton = {
            if (com.renly.rmf.domain.service.GoogleDrivePreferences.isAuthorized(context)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        enabled = !isOperating,
                        onClick = {
                            isOperating = true
                            coroutineScope.launch {
                                try {
                                    val syncService = com.renly.rmf.domain.service.GoogleDriveSyncService(context)
                                    val res = syncService.pullLatestBackupIfNewer(force = true)
                                    if (res is com.renly.rmf.domain.service.SyncResult.Success) {
                                        android.widget.Toast.makeText(context, "✓ ${res.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    } else if (res is com.renly.rmf.domain.service.SyncResult.Error) {
                                        android.widget.Toast.makeText(context, "✗ ${res.error}", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                } finally {
                                    isOperating = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("🔄 双向合并拉取", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        enabled = !isOperating,
                        onClick = {
                            isOperating = true
                            coroutineScope.launch {
                                try {
                                    val syncService = com.renly.rmf.domain.service.GoogleDriveSyncService(context)
                                    val res = syncService.uploadBackupToDrive()
                                    if (res is com.renly.rmf.domain.service.SyncResult.Success) {
                                        android.widget.Toast.makeText(context, "✓ ${res.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    } else if (res is com.renly.rmf.domain.service.SyncResult.Error) {
                                        android.widget.Toast.makeText(context, "✗ ${res.error}", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                } finally {
                                    isOperating = false
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("⬆️ 强制上传快照", fontSize = 11.5.sp)
                    }
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("知道了", color = TextPrimary)
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isOperating,
                onClick = onDismiss
            ) {
                Text("关闭", color = TextSecondary)
            }
        }
    )
}
