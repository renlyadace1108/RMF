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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.RmfApplication
import com.renly.rmf.data.local.entity.ScheduleEntity
import com.renly.rmf.domain.service.CalendarSyncResult
import com.renly.rmf.domain.service.GoogleCalendarSyncService
import com.renly.rmf.domain.service.GoogleDrivePreferences
import com.renly.rmf.domain.service.NaturalLanguageScheduleParser
import com.renly.rmf.domain.service.ParsedScheduleResult
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

enum class ScheduleViewMode(val title: String, val icon: String) {
    WEEK_GRID("周网格", "📅"),
    DAY_GRID("日网格", "⏱️"),
    LIST("清单流", "📋")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    schedulesFlow: Flow<List<ScheduleEntity>>,
    onToggleStatus: (ScheduleEntity) -> Unit,
    onAddSchedule: (String, String, String, Int, String, String) -> Unit,
    onUpdateSchedule: (ScheduleEntity) -> Unit = {},
    onDeleteSchedule: (String) -> Unit = {},
    onOpenQuickCapture: () -> Unit = {},
    onNavigateToSummary: () -> Unit = {},
    onNavigateToFitness: () -> Unit = {}
) {
    val schedules by schedulesFlow.collectAsState(initial = emptyList())
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

    // 过滤掉待办箱中的项目用于日历排期统计
    val calendarSchedules = schedules.filter { it.status != "BACKLOG" }
    val backlogSchedules = schedules.filter { it.status == "BACKLOG" }

    val completedCount = calendarSchedules.count { it.status == "COMPLETED" }
    val totalCount = calendarSchedules.size
    val completionPercent = if (totalCount > 0) (completedCount * 100 / totalCount) else 0

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

                    Spacer(modifier = Modifier.weight(1f))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
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
                            onSelectDate = { selectedDate = it },
                            onItemClick = { detailItem = it },
                            onScheduleBacklog = { item, dt ->
                                val startStr = dt.format(DateTimeFormatter.ISO_DATE_TIME)
                                val endStr = dt.plusMinutes(item.estimatedMinutes.toLong()).format(DateTimeFormatter.ISO_DATE_TIME)
                                val updated = item.copy(
                                    status = "PENDING",
                                    startTime = startStr,
                                    endTime = endStr,
                                    updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                                )
                                onUpdateSchedule(updated)
                            },
                            onAddScheduleWithRange = { title, workType, category, mins, startStr, endStr ->
                                onAddSchedule(title, workType, category, mins, startStr, endStr)
                            },
                            onUpdateSchedule = onUpdateSchedule
                        )
                    }

                    ScheduleViewMode.DAY_GRID -> {
                        ScheduleDayGridView(
                            schedules = calendarSchedules,
                            backlogItems = backlogSchedules,
                            selectedDate = selectedDate,
                            onSelectDate = { selectedDate = it },
                            onItemClick = { detailItem = it },
                            onScheduleBacklog = { item, dt ->
                                val startStr = dt.format(DateTimeFormatter.ISO_DATE_TIME)
                                val endStr = dt.plusMinutes(item.estimatedMinutes.toLong()).format(DateTimeFormatter.ISO_DATE_TIME)
                                val updated = item.copy(
                                    status = "PENDING",
                                    startTime = startStr,
                                    endTime = endStr,
                                    updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                                )
                                onUpdateSchedule(updated)
                            },
                            onAddNewScheduleAtHour = { hour ->
                                val dt = selectedDate.atTime(hour, 0)
                                val startStr = dt.format(DateTimeFormatter.ISO_DATE_TIME)
                                val endStr = dt.plusMinutes(45).format(DateTimeFormatter.ISO_DATE_TIME)
                                onAddSchedule("新任务", "DEEP_WORK", "WORK", 45, startStr, endStr)
                            },
                            onAddScheduleWithRange = { title, workType, category, mins, startStr, endStr ->
                                onAddSchedule(title, workType, category, mins, startStr, endStr)
                            },
                            onUpdateSchedule = onUpdateSchedule
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

                    // 自然语言输入条
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkCard,
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                        ) {
                            TextField(
                                value = quickInputText,
                                onValueChange = { quickInputText = it },
                                placeholder = { Text("例: 下午3点攻坚架构 45m #技术", fontSize = 12.5.sp, color = TextMuted) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = {
                                    if (quickInputText.isNotBlank()) {
                                        val parsed = NaturalLanguageScheduleParser.parse(quickInputText)
                                        onAddSchedule(
                                            parsed.title,
                                            "DEEP_WORK",
                                            parsed.category,
                                            parsed.durationMinutes,
                                            parsed.startTime.format(DateTimeFormatter.ISO_DATE_TIME),
                                            parsed.endTime.format(DateTimeFormatter.ISO_DATE_TIME)
                                        )
                                        quickInputText = ""
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "解析添加",
                                    tint = DopamineBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 过滤 Chips
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val filters = listOf(
                            "ALL" to "全部日程",
                            "PENDING" to "待完成",
                            "DEEP_WORK" to "深度工作",
                            "SHALLOW_WORK" to "浅层事务",
                            "REST_BUFFER" to "休息缓冲"
                        )
                        items(filters) { (key, label) ->
                            val isSelected = selectedFilter == key
                            Surface(
                                onClick = { selectedFilter = key },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) DopamineBlue.copy(alpha = 0.18f) else DarkSurface,
                                border = BorderStroke(1.dp, if (isSelected) DopamineBlue else DarkBorder),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                                    Text(
                                        text = label,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) DopamineBlue else TextSecondary
                                    )
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
                            else -> true
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
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filtered, key = { it.id }) { item ->
                                ScheduleItemCard(
                                    item = item,
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
    }
}

        // 悬浮新建日程 FAB (贴合右下角，不挤占日程网格纵向高度)
        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = DopamineBlue,
            contentColor = if (LocalAppColors.current.isDark) DarkBg else Color.White,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 12.dp, end = 12.dp)
                .size(46.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "新建日程", modifier = Modifier.size(22.dp))
        }

        // 新建日程弹窗 (AddScheduleDialog)
        if (showAddDialog) {
            AddScheduleDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { title, workType, category, minutes ->
                    val now = LocalDateTime.now()
                    val startStr = now.format(DateTimeFormatter.ISO_DATE_TIME)
                    val endStr = now.plusMinutes(minutes.toLong()).format(DateTimeFormatter.ISO_DATE_TIME)
                    onAddSchedule(title, workType, category, minutes, startStr, endStr)
                    showAddDialog = false
                }
            )
        }

        // 待办任务箱弹窗 (BacklogModal)
        if (showBacklogDialog) {
            BacklogDialog(
                backlogItems = backlogSchedules,
                onDismiss = { showBacklogDialog = false },
                onAddBacklog = { title, workType, category, minutes, dod ->
                    val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                    val entity = ScheduleEntity(
                        title = title,
                        workType = workType,
                        category = category,
                        estimatedMinutes = minutes,
                        dod = dod,
                        status = "BACKLOG",
                        startTime = "",
                        endTime = "",
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
                        updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                    )
                    onUpdateSchedule(updated)
                },
                onToggleStatus = { backlogItem ->
                    val nextStatus = if (backlogItem.status == "COMPLETED") "BACKLOG" else "COMPLETED"
                    onUpdateSchedule(backlogItem.copy(status = nextStatus, updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)))
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

        // 日程详情弹窗 (EventQuickDetailModal)
        detailItem?.let { item ->
            ScheduleDetailModal(
                item = item,
                onToggleStatus = {
                    onToggleStatus(item)
                    detailItem = null
                },
                onEdit = {
                    editingSchedule = item
                    detailItem = null
                    showEditDialog = true
                },
                onDelete = {
                    onDeleteSchedule(item.id)
                    detailItem = null
                },
                onNavigateToFitness = onNavigateToFitness,
                onDismiss = { detailItem = null }
            )
        }

        // 日程全量编辑弹窗 (EventModal)
        if (showEditDialog && editingSchedule != null) {
            EditScheduleDialog(
                item = editingSchedule!!,
                onDismiss = {
                    showEditDialog = false
                    editingSchedule = null
                },
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
    onDismiss: () -> Unit,
    onAddSchedule: (String, String, String, Int, String, String) -> Unit,
    onAssignBacklog: (ScheduleEntity, LocalDateTime, LocalDateTime, Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var workType by remember { mutableStateOf("DEEP_WORK") }
    var category by remember { mutableStateOf("WORK") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: 新建日程, 1: 待办填入

    val startDt = range.date.atTime(range.startHour, range.startMinute)
    val endDt = range.date.atTime(range.endHour, range.endMinute)
    val startStr = startDt.format(DateTimeFormatter.ISO_DATE_TIME)
    val endStr = endDt.format(DateTimeFormatter.ISO_DATE_TIME)

    AlertDialog(
        onDismissRequest = onDismiss,
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
                            range.startHour, range.startMinute, range.endHour, range.endMinute, range.durationMinutes
                        ),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = DopamineBlue,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
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

                    Text("分类标签:", fontSize = 11.sp, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("WORK" to "工作", "STUDY" to "学习", "HEALTH" to "健康", "LIFE" to "生活").forEach { (k, v) ->
                            FilterChip(
                                selected = category == k,
                                onClick = { category = k },
                                label = { Text(v, fontSize = 11.sp) }
                            )
                        }
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
                                        onAssignBacklog(item, startDt, endDt, range.durationMinutes)
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
                                            Text("原预估: ${item.estimatedMinutes}m · 调整为 ${range.durationMinutes}m 并排期", fontSize = 9.5.sp, color = TextMuted)
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
                        onAddSchedule(finalTitle, workType, category, range.durationMinutes, startStr, endStr)
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
// 电脑版 1:1 周历网格视图 (Week Grid Track: 7 列星期 x 24 小时刻度网格 - 支持待办拖拽与手势框选排期)
// -----------------------------------------------------------------------------------------
@Composable
fun ScheduleWeekGridView(
    schedules: List<ScheduleEntity>,
    backlogItems: List<ScheduleEntity> = emptyList(),
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onItemClick: (ScheduleEntity) -> Unit,
    onScheduleBacklog: (ScheduleEntity, LocalDateTime) -> Unit = { _, _ -> },
    onAddScheduleWithRange: (String, String, String, Int, String, String) -> Unit = { _, _, _, _, _, _ -> },
    onUpdateSchedule: (ScheduleEntity) -> Unit = {}
) {
    val currentWeekMonday = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekDates = (0..6).map { currentWeekMonday.plusDays(it.toLong()) }
    val sunday = weekDates.last()

    val hourHeight = 46.dp
    val timeColWidth = 32.dp
    val verticalScrollState = rememberScrollState()
    val density = LocalDensity.current

    // 待办拖拽与框选排期状态
    var draggingBacklogItem by remember { mutableStateOf<ScheduleEntity?>(null) }
    var backlogDragOffsetY by remember { mutableFloatStateOf(0f) }
    var backlogDragOffsetX by remember { mutableFloatStateOf(0f) }
    var backlogChipRootY by remember { mutableFloatStateOf(0f) }
    var backlogChipRootX by remember { mutableFloatStateOf(0f) }
    var weekGridRootY by remember { mutableFloatStateOf(0f) }
    var weekGridRootX by remember { mutableFloatStateOf(0f) }
    var isBacklogExpanded by remember { mutableStateOf(false) }

    // 空白网格拖拽框选排期状态
    var isDragSelecting by remember { mutableStateOf(false) }
    var dragSelectStartOffset by remember { mutableStateOf<Offset?>(null) }
    var dragSelectCurrentOffset by remember { mutableStateOf<Offset?>(null) }
    var dragSelectedRange by remember { mutableStateOf<DragRangeData?>(null) }

    // 已有卡片长按拖拽移时
    var movingScheduleItem by remember { mutableStateOf<ScheduleEntity?>(null) }
    var movingDragDeltaY by remember { mutableFloatStateOf(0f) }
    var movingOriginalTopDp by remember { mutableFloatStateOf(0f) }
    var weekDayColWidthPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val currentHour = LocalDateTime.now().hour
        val targetHour = (currentHour - 2).coerceIn(0, 20)
        verticalScrollState.scrollTo(targetHour * 105)
    }

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
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(backlogItems, key = { it.id }) { item ->
                                val chipColor = when (item.workType) {
                                    "DEEP_WORK" -> DopamineBlue
                                    "SHALLOW_WORK" -> DopamineAmber
                                    "REST_BUFFER" -> DopamineGreen
                                    else -> DopaminePurple
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = chipColor.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, chipColor.copy(alpha = 0.7f)),
                                    modifier = Modifier
                                        .pointerInput(item.id) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { offset ->
                                                    draggingBacklogItem = item
                                                    backlogDragOffsetY = offset.y
                                                    backlogDragOffsetX = offset.x
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    backlogDragOffsetY += dragAmount.y
                                                    backlogDragOffsetX += dragAmount.x
                                                },
                                                onDragEnd = {
                                                    if (draggingBacklogItem != null) {
                                                        val currentScroll = verticalScrollState.value
                                                        val dropGlobalY = backlogChipRootY + backlogDragOffsetY
                                                        val dropGlobalX = backlogChipRootX + backlogDragOffsetX
                                                        val relativeY = (dropGlobalY - weekGridRootY + currentScroll).coerceAtLeast(0f)
                                                        val relativeX = (dropGlobalX - weekGridRootX - 32f * density.density).coerceAtLeast(0f)

                                                        val colW = if (weekDayColWidthPx > 0f) (relativeX / weekDayColWidthPx).toInt().coerceIn(0, 6) else 0
                                                        val targetDay = weekDates[colW]

                                                        val relDp = relativeY / density.density
                                                        val dropHour = (relDp / 46f).toInt().coerceIn(0, 23)
                                                        val dropMinute = if ((relDp % 46f) >= 23f) 30 else 0
                                                        val targetDt = targetDay.atTime(dropHour, dropMinute)

                                                        onScheduleBacklog(draggingBacklogItem!!, targetDt)
                                                        draggingBacklogItem = null
                                                        backlogDragOffsetY = 0f
                                                        backlogDragOffsetX = 0f
                                                    }
                                                },
                                                onDragCancel = {
                                                    draggingBacklogItem = null
                                                    backlogDragOffsetY = 0f
                                                    backlogDragOffsetX = 0f
                                                }
                                            )
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp)
                                    ) {
                                        Text("⋮⋮", fontSize = 9.5.sp, color = chipColor, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(3.dp))
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
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coords ->
                        weekGridRootY = coords.positionInRoot().y
                        weekGridRootX = coords.positionInRoot().x
                    }
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

                    // 24 小时纵向滚动网格
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
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
                                    .pointerInput(Unit) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { offset ->
                                                isDragSelecting = true
                                                dragSelectStartOffset = offset
                                                dragSelectCurrentOffset = offset
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragSelectCurrentOffset = (dragSelectCurrentOffset ?: Offset.Zero) + dragAmount
                                            },
                                            onDragEnd = {
                                                if (isDragSelecting && dragSelectStartOffset != null && dragSelectCurrentOffset != null) {
                                                    val start = dragSelectStartOffset!!
                                                    val curr = dragSelectCurrentOffset!!
                                                    val minY = minOf(start.y, curr.y).coerceAtLeast(0f)
                                                    val maxY = maxOf(start.y, curr.y).coerceAtMost(24 * 46f * density.density)
                                                    val heightPx = maxY - minY
                                                    if (heightPx >= (15f / 60f * 46f * density.density)) {
                                                        val colIdx = (start.x / dayColWidthPx).toInt().coerceIn(0, 6)
                                                        val targetDate = weekDates[colIdx]

                                                        val startMinRaw = ((minY / density.density) / 46f * 60f).toInt()
                                                        val endMinRaw = ((maxY / density.density) / 46f * 60f).toInt()
                                                        val sSnapped = ((startMinRaw / 15) * 15).coerceIn(0, 1425)
                                                        val eSnapped = (((endMinRaw + 14) / 15) * 15).coerceIn(sSnapped + 15, 1440)

                                                        dragSelectedRange = DragRangeData(
                                                            date = targetDate,
                                                            startHour = sSnapped / 60,
                                                            startMinute = sSnapped % 60,
                                                            endHour = eSnapped / 60,
                                                            endMinute = eSnapped % 60,
                                                            durationMinutes = eSnapped - sSnapped
                                                        )
                                                    }
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
                            ) {
                                // 今日高亮柔和背景列
                                weekDates.forEachIndexed { idx, date ->
                                    if (date == LocalDate.now()) {
                                        Box(
                                            modifier = Modifier
                                                .offset(x = dayColWidth * idx)
                                                .width(dayColWidth)
                                                .fillMaxHeight()
                                                .background(DopamineBlue.copy(alpha = if (LocalAppColors.current.isDark) 0.08f else 0.05f))
                                        )
                                    }
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
                                    val nowY = (currentMinuteFromZero.toFloat() / 60f) * 46f
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

                                // 待办拖拽至周历的实时预览虚影 (Hover Preview Ghost on Week Column)
                                if (draggingBacklogItem != null) {
                                    val currentScroll = verticalScrollState.value
                                    val dropGlobalY = backlogChipRootY + backlogDragOffsetY
                                    val dropGlobalX = backlogChipRootX + backlogDragOffsetX
                                    val relativeY = (dropGlobalY - weekGridRootY + currentScroll).coerceAtLeast(0f)
                                    val relativeX = (dropGlobalX - weekGridRootX - 32f * density.density).coerceAtLeast(0f)

                                    val colW = (relativeX / dayColWidthPx).toInt().coerceIn(0, 6)
                                    val relDp = relativeY / density.density
                                    val previewHour = (relDp / 46f).toInt().coerceIn(0, 23)
                                    val previewMinute = if ((relDp % 46f) >= 23f) 30 else 0
                                    val previewTopY = previewHour * 46f + previewMinute * 46f / 60f
                                    val previewBlockH = (draggingBacklogItem!!.estimatedMinutes.toFloat() / 60f * 46f).coerceAtLeast(20f)

                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = DopamineBlue.copy(alpha = 0.45f),
                                        border = BorderStroke(1.5.dp, DopamineBlue),
                                        modifier = Modifier
                                            .offset(x = dayColWidth * colW + 1.dp, y = previewTopY.dp)
                                            .width(dayColWidth - 2.dp)
                                            .height(previewBlockH.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(2.dp)) {
                                            Text(
                                                text = "🎯 %02d:%02d".format(previewHour, previewMinute),
                                                fontSize = 7.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                // 手势框选时间段的实时高亮指示框 (Week Drag-Select Box)
                                if (isDragSelecting && dragSelectStartOffset != null && dragSelectCurrentOffset != null) {
                                    val start = dragSelectStartOffset!!
                                    val curr = dragSelectCurrentOffset!!
                                    val minY = minOf(start.y, curr.y).coerceAtLeast(0f)
                                    val maxY = maxOf(start.y, curr.y)
                                    val topDp = minY / density.density
                                    val heightDp = maxOf(10f, (maxY - minY) / density.density)
                                    val colIdx = (start.x / dayColWidthPx).toInt().coerceIn(0, 6)

                                    val startMinRaw = ((minY / density.density) / 46f * 60f).toInt()
                                    val endMinRaw = ((maxY / density.density) / 46f * 60f).toInt()
                                    val sSnapped = ((startMinRaw / 15) * 15).coerceIn(0, 1425)
                                    val eSnapped = maxOf(sSnapped + 15, ((endMinRaw + 14) / 15) * 15).coerceIn(15, 1440)

                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = DopamineBlue.copy(alpha = 0.35f),
                                        border = BorderStroke(1.5.dp, DopamineBlue),
                                        modifier = Modifier
                                            .offset(x = dayColWidth * colIdx + 1.dp, y = topDp.dp)
                                            .width(dayColWidth - 2.dp)
                                            .height(heightDp.dp)
                                    ) {
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

                                // 日程事件色块 (支持长按拖动移动时间)
                                schedules.forEach { item ->
                                    val startDt = parseDateTime(item.startTime)
                                    if (startDt != null) {
                                        val dayIndex = weekDates.indexOf(startDt.toLocalDate())
                                        if (dayIndex in 0..6) {
                                            val startMinute = startDt.hour * 60 + startDt.minute
                                            val duration = item.estimatedMinutes.coerceIn(20, 360)
                                            val topY = (startMinute.toFloat() / 60f) * 46f
                                            val blockH = (duration.toFloat() / 60f) * 46f

                                            val accentColor = when (item.workType) {
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
                                                    .pointerInput(item.id + "_week_move") {
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
                                                                    val currentTopDp = (movingOriginalTopDp + movingDragDeltaY / density.density).coerceIn(0f, 23.5f * 46f)
                                                                    val targetMinRaw = (currentTopDp / 46f * 60f).toInt()
                                                                    val targetMinSnapped = ((targetMinRaw / 15) * 15).coerceIn(0, 1440 - item.estimatedMinutes)
                                                                    val curDate = startDt.toLocalDate()
                                                                    val newStart = curDate.atTime(targetMinSnapped / 60, targetMinSnapped % 60)
                                                                    val newEnd = newStart.plusMinutes(item.estimatedMinutes.toLong())
                                                                    val updated = item.copy(
                                                                        startTime = newStart.format(DateTimeFormatter.ISO_DATE_TIME),
                                                                        endTime = newEnd.format(DateTimeFormatter.ISO_DATE_TIME),
                                                                        updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                                                                    )
                                                                    onUpdateSchedule(updated)
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
                                            ) {
                                                Column(modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)) {
                                                    Text(
                                                        text = item.title,
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        lineHeight = 10.5.sp,
                                                        maxLines = if (blockH >= 32) 2 else 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        textDecoration = if (isDone) TextDecoration.LineThrough else null,
                                                        color = if (isDone) TextMuted else TextPrimary
                                                    )
                                                    if (blockH >= 28) {
                                                        Text(
                                                            text = "%02d:%02d".format(startDt.hour, startDt.minute),
                                                            fontSize = 7.5.sp,
                                                            color = if (isDone) TextMuted else TextSecondary
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

    // 周历框选排期弹窗 (DragRangeScheduleDialog)
    if (dragSelectedRange != null) {
        DragRangeScheduleDialog(
            range = dragSelectedRange!!,
            backlogItems = backlogItems,
            onDismiss = { dragSelectedRange = null },
            onAddSchedule = { title, workType, category, mins, startStr, endStr ->
                onAddScheduleWithRange(title, workType, category, mins, startStr, endStr)
            },
            onAssignBacklog = { item, startDt, endDt, mins ->
                val updated = item.copy(
                    status = "PENDING",
                    startTime = startDt.format(DateTimeFormatter.ISO_DATE_TIME),
                    endTime = endDt.format(DateTimeFormatter.ISO_DATE_TIME),
                    estimatedMinutes = mins,
                    updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
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
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onItemClick: (ScheduleEntity) -> Unit,
    onScheduleBacklog: (ScheduleEntity, LocalDateTime) -> Unit = { _, _ -> },
    onAddNewScheduleAtHour: (Int) -> Unit = {},
    onAddScheduleWithRange: (String, String, String, Int, String, String) -> Unit = { _, _, _, _, _, _ -> },
    onUpdateSchedule: (ScheduleEntity) -> Unit = {}
) {
    val currentWeekMonday = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekDates = (0..6).map { currentWeekMonday.plusDays(it.toLong()) }

    val hourHeight = 50.dp
    val timeColWidth = 36.dp
    val verticalScrollState = rememberScrollState()
    val density = LocalDensity.current

    val daySchedules = schedules.filter { item ->
        val dt = parseDateTime(item.startTime)
        dt?.toLocalDate() == selectedDate
    }

    // 待办拖拽与排期状态
    var draggingItem by remember { mutableStateOf<ScheduleEntity?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var chipYInRoot by remember { mutableFloatStateOf(0f) }
    var timelineYInRoot by remember { mutableFloatStateOf(0f) }
    var isBacklogBarExpanded by remember { mutableStateOf(true) }
    var quickScheduleTargetItem by remember { mutableStateOf<ScheduleEntity?>(null) }
    var clickedHourForBacklog by remember { mutableIntStateOf(-1) }

    // 空白网格拖拽框选排期状态 (Drag-to-select range)
    var isDragSelecting by remember { mutableStateOf(false) }
    var dragSelectStartOffset by remember { mutableStateOf<Offset?>(null) }
    var dragSelectCurrentOffset by remember { mutableStateOf<Offset?>(null) }
    var dragSelectedRange by remember { mutableStateOf<DragRangeData?>(null) }

    // 已有日程卡片长按拖拽移时 (Drag-to-move schedule)
    var movingScheduleItem by remember { mutableStateOf<ScheduleEntity?>(null) }
    var movingDragDeltaY by remember { mutableFloatStateOf(0f) }
    var movingOriginalTopDp by remember { mutableFloatStateOf(0f) }

    // 已有日程卡片底边拖拽调整时长 (Drag-to-resize duration)
    var resizingScheduleItem by remember { mutableStateOf<ScheduleEntity?>(null) }
    var resizeDeltaY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val currentHour = LocalDateTime.now().hour
        verticalScrollState.scrollTo((currentHour - 2).coerceAtLeast(0) * 115)
    }

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

                        Text(
                            text = "长按拖入时间轴 / 点击快捷排期",
                            fontSize = 9.sp,
                            color = TextSecondary
                        )
                    }

                    if (isBacklogBarExpanded) {
                        Spacer(modifier = Modifier.height(3.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(backlogItems, key = { it.id }) { item ->
                                val chipColor = when (item.workType) {
                                    "DEEP_WORK" -> DopamineBlue
                                    "SHALLOW_WORK" -> DopamineAmber
                                    "REST_BUFFER" -> DopamineGreen
                                    else -> DopaminePurple
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = chipColor.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, chipColor.copy(alpha = 0.7f)),
                                    modifier = Modifier
                                        .pointerInput(item.id) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { offset ->
                                                    draggingItem = item
                                                    dragOffsetY = offset.y
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffsetY += dragAmount.y
                                                },
                                                onDragEnd = {
                                                    if (draggingItem != null) {
                                                        val currentScroll = verticalScrollState.value
                                                        val dropGlobalY = chipYInRoot + dragOffsetY
                                                        val relativeY = (dropGlobalY - timelineYInRoot + currentScroll).coerceAtLeast(0f)
                                                        val relDp = relativeY / density.density
                                                        val dropHour = (relDp / 50f).toInt().coerceIn(0, 23)
                                                        val dropMinute = if ((relDp % 50f) >= 25f) 30 else 0
                                                        val targetDt = selectedDate.atTime(dropHour, dropMinute)
                                                        onScheduleBacklog(draggingItem!!, targetDt)
                                                        draggingItem = null
                                                        dragOffsetY = 0f
                                                    }
                                                },
                                                onDragCancel = {
                                                    draggingItem = null
                                                    dragOffsetY = 0f
                                                }
                                            )
                                        }
                                        .clickable {
                                            quickScheduleTargetItem = item
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp)
                                    ) {
                                        Text("⋮⋮", fontSize = 9.5.sp, color = chipColor, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(3.dp))
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

        // 快捷操作提示条 (电脑版同款体验)
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = DarkCard.copy(alpha = 0.6f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
            ) {
                Text("💡", fontSize = 9.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "长按空白处向下拉动可框选排期 · 长按卡片可移动时间 · 拖动底边手柄可调整时长",
                    fontSize = 8.5.sp,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(1.dp))

        // 3. 24 小时时间轴轨道
        Surface(
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
            color = DarkSurface,
            border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScrollState)
                    .onGloballyPositioned { coords ->
                        timelineYInRoot = coords.positionInRoot().y
                    }
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
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
                            .pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        isDragSelecting = true
                                        dragSelectStartOffset = offset
                                        dragSelectCurrentOffset = offset
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragSelectCurrentOffset = (dragSelectCurrentOffset ?: Offset.Zero) + dragAmount
                                    },
                                    onDragEnd = {
                                        if (isDragSelecting && dragSelectStartOffset != null && dragSelectCurrentOffset != null) {
                                            val start = dragSelectStartOffset!!
                                            val curr = dragSelectCurrentOffset!!
                                            val minY = minOf(start.y, curr.y).coerceAtLeast(0f)
                                            val maxY = maxOf(start.y, curr.y).coerceAtMost(24 * 50f * density.density)
                                            val heightPx = maxY - minY
                                            if (heightPx >= (15f / 60f * 50f * density.density)) {
                                                val startMinRaw = ((minY / density.density) / 50f * 60f).toInt()
                                                val endMinRaw = ((maxY / density.density) / 50f * 60f).toInt()
                                                val sSnapped = ((startMinRaw / 15) * 15).coerceIn(0, 1425)
                                                val eSnapped = (((endMinRaw + 14) / 15) * 15).coerceIn(sSnapped + 15, 1440)

                                                dragSelectedRange = DragRangeData(
                                                    date = selectedDate,
                                                    startHour = sSnapped / 60,
                                                    startMinute = sSnapped % 60,
                                                    endHour = eSnapped / 60,
                                                    endMinute = eSnapped % 60,
                                                    durationMinutes = eSnapped - sSnapped
                                                )
                                            }
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
                    ) {
                        // 每小时网格分割线与点击交互
                        (0..23).forEach { h ->
                            Box(
                                modifier = Modifier
                                    .offset(y = hourHeight * h)
                                    .fillMaxWidth()
                                    .height(hourHeight)
                                    .clickable {
                                        clickedHourForBacklog = h
                                    }
                            )
                            HorizontalDivider(
                                modifier = Modifier.offset(y = hourHeight * h),
                                color = DarkBorder.copy(alpha = 0.35f),
                                thickness = 0.8.dp
                            )
                        }

                        // 今日当前时间红线
                        if (selectedDate == LocalDate.now()) {
                            val now = LocalDateTime.now()
                            val currentMinute = now.hour * 60 + now.minute
                            val nowY = (currentMinute.toFloat() / 60f) * 50f
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

                        // 动态待办拖拽预览色块 (Hover Preview Ghost)
                        if (draggingItem != null) {
                            val currentScroll = verticalScrollState.value
                            val dropGlobalY = chipYInRoot + dragOffsetY
                            val relativeY = (dropGlobalY - timelineYInRoot + currentScroll).coerceAtLeast(0f)
                            val relDp = relativeY / density.density
                            val previewHour = (relDp / 50f).toInt().coerceIn(0, 23)
                            val previewMinute = if ((relDp % 50f) >= 25f) 30 else 0
                            val previewTopY = previewHour * 50f + previewMinute * 50f / 60f
                            val previewBlockH = (draggingItem!!.estimatedMinutes.toFloat() / 60f * 50f).coerceAtLeast(24f)

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = DopamineBlue.copy(alpha = 0.35f),
                                border = BorderStroke(1.5.dp, DopamineBlue),
                                modifier = Modifier
                                    .offset(y = previewTopY.dp)
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp)
                                    .height(previewBlockH.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text("🎯", fontSize = 11.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "放置到 %02d:%02d · %s (%dm)".format(previewHour, previewMinute, draggingItem!!.title, draggingItem!!.estimatedMinutes),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // 手势框选时间段的实时高亮指示框 (Drag-to-Select Ghost Box)
                        if (isDragSelecting && dragSelectStartOffset != null && dragSelectCurrentOffset != null) {
                            val start = dragSelectStartOffset!!
                            val curr = dragSelectCurrentOffset!!
                            val minY = minOf(start.y, curr.y).coerceAtLeast(0f)
                            val maxY = maxOf(start.y, curr.y)
                            val topDp = minY / density.density
                            val heightDp = maxOf(12f, (maxY - minY) / density.density)

                            val startMinRaw = ((minY / density.density) / 50f * 60f).toInt()
                            val endMinRaw = ((maxY / density.density) / 50f * 60f).toInt()
                            val sSnapped = ((startMinRaw / 15) * 15).coerceIn(0, 1425)
                            val eSnapped = maxOf(sSnapped + 15, ((endMinRaw + 14) / 15) * 15).coerceIn(15, 1440)
                            val durationM = eSnapped - sSnapped

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DopamineBlue.copy(alpha = 0.32f),
                                border = BorderStroke(1.8.dp, DopamineBlue),
                                modifier = Modifier
                                    .offset(y = topDp.dp)
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp)
                                    .height(heightDp.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    Text("⏱️", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "框选排期: %02d:%02d - %02d:%02d (%d分钟) · 松开即可排期".format(
                                            sSnapped / 60, sSnapped % 60, eSnapped / 60, eSnapped % 60, durationM
                                        ),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // 已排期日程卡片 (支持长按拖拽移时、底部手柄拉伸缩放时长)
                        daySchedules.forEach { item ->
                            val startDt = parseDateTime(item.startTime)
                            if (startDt != null) {
                                val startMinute = startDt.hour * 60 + startDt.minute
                                val origDuration = item.estimatedMinutes.coerceIn(15, 360)
                                val isResizingThis = resizingScheduleItem?.id == item.id
                                val extraResizeMinutes = if (isResizingThis) {
                                    ((resizeDeltaY / density.density / 50f * 60f) / 15 * 15).toInt()
                                } else 0
                                val displayDuration = (origDuration + extraResizeMinutes).coerceIn(15, 360)

                                val topY = (startMinute.toFloat() / 60f) * 50f
                                val blockH = (displayDuration.toFloat() / 60f) * 50f

                                val accentColor = when (item.workType) {
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
                                    modifier = Modifier
                                        .offset(y = topY.dp)
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp)
                                        .height(blockH.dp)
                                        .pointerInput(item.id + "_move") {
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
                                                        val currentTopDp = (movingOriginalTopDp + movingDragDeltaY / density.density).coerceIn(0f, 23.5f * 50f)
                                                        val targetMinRaw = (currentTopDp / 50f * 60f).toInt()
                                                        val targetMinSnapped = ((targetMinRaw / 15) * 15).coerceIn(0, 1440 - item.estimatedMinutes)
                                                        val newStart = selectedDate.atTime(targetMinSnapped / 60, targetMinSnapped % 60)
                                                        val newEnd = newStart.plusMinutes(item.estimatedMinutes.toLong())
                                                        val updated = item.copy(
                                                            startTime = newStart.format(DateTimeFormatter.ISO_DATE_TIME),
                                                            endTime = newEnd.format(DateTimeFormatter.ISO_DATE_TIME),
                                                            updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                                                        )
                                                        onUpdateSchedule(updated)
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
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                .weight(1f, fill = false)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(3.5.dp)
                                                    .height(28.dp)
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
                                                Spacer(modifier = Modifier.height(1.dp))
                                                Text(
                                                    text = "%02d:%02d · %d分钟 · %s".format(
                                                        startDt.hour,
                                                        startDt.minute,
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

                                        // 底边时长调节手柄 (Drag Handle to Resize Duration)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(14.dp)
                                                .pointerInput(item.id + "_resize") {
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
                                                                val extraMins = ((resizeDeltaY / density.density / 50f * 60f) / 15 * 15).toInt()
                                                                val newMins = (item.estimatedMinutes + extraMins).coerceIn(15, 360)
                                                                val curStart = parseDateTime(item.startTime) ?: selectedDate.atTime(9, 0)
                                                                val newEnd = curStart.plusMinutes(newMins.toLong())
                                                                val updated = item.copy(
                                                                    estimatedMinutes = newMins,
                                                                    endTime = newEnd.format(DateTimeFormatter.ISO_DATE_TIME),
                                                                    updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                                                                )
                                                                onUpdateSchedule(updated)
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
                                                    .width(32.dp)
                                                    .height(3.5.dp)
                                                    .background(
                                                        if (isResizingThis) DopamineAmber else accentColor.copy(alpha = 0.7f),
                                                        RoundedCornerShape(2.dp)
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 已有日程卡片长按拖拽移时实时预览指示虚影 (Moving Schedule Hover Preview)
                        if (movingScheduleItem != null) {
                            val item = movingScheduleItem!!
                            val currentTopDp = (movingOriginalTopDp + movingDragDeltaY / density.density).coerceIn(0f, 23.5f * 50f)
                            val targetMinRaw = (currentTopDp / 50f * 60f).toInt()
                            val targetMinSnapped = ((targetMinRaw / 15) * 15).coerceIn(0, 1440 - item.estimatedMinutes)
                            val targetStartHour = targetMinSnapped / 60
                            val targetStartMin = targetMinSnapped % 60
                            val targetEndMinTotal = targetMinSnapped + item.estimatedMinutes
                            val targetEndHour = (targetEndMinTotal / 60).coerceIn(0, 24)
                            val targetEndMin = targetEndMinTotal % 60

                            val previewTopDp = targetMinSnapped / 60f * 50f
                            val previewHeightDp = (item.estimatedMinutes.toFloat() / 60f * 50f).coerceAtLeast(24f)

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DopamineAmber.copy(alpha = 0.38f),
                                border = BorderStroke(2.dp, DopamineAmber),
                                modifier = Modifier
                                    .offset(y = previewTopDp.dp)
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp)
                                    .height(previewHeightDp.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text("🎯", fontSize = 11.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "移动至 %02d:%02d - %02d:%02d · %s (%dm)".format(
                                            targetStartHour, targetStartMin, targetEndHour, targetEndMin, item.title, item.estimatedMinutes
                                        ),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
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
                onDismiss = { dragSelectedRange = null },
                onAddSchedule = { title, workType, category, mins, startStr, endStr ->
                    onAddScheduleWithRange(title, workType, category, mins, startStr, endStr)
                },
                onAssignBacklog = { item, startDt, endDt, mins ->
                    val updated = item.copy(
                        status = "PENDING",
                        startTime = startDt.format(DateTimeFormatter.ISO_DATE_TIME),
                        endTime = endDt.format(DateTimeFormatter.ISO_DATE_TIME),
                        estimatedMinutes = mins,
                        updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                    )
                    onUpdateSchedule(updated)
                }
            )
        }
    }
}

// -----------------------------------------------------------------------------------------
// 📥 电脑版 1:1 待排期任务箱弹窗 (BacklogModal)
// -----------------------------------------------------------------------------------------
@Composable
fun BacklogDialog(
    backlogItems: List<ScheduleEntity>,
    onDismiss: () -> Unit,
    onAddBacklog: (String, String, String, Int, String) -> Unit,
    onScheduleToToday: (ScheduleEntity) -> Unit,
    onToggleStatus: (ScheduleEntity) -> Unit,
    onDelete: (String) -> Unit
) {
    var newTitle by remember { mutableStateOf("") }
    var newMinutes by remember { mutableIntStateOf(30) }
    var newWorkType by remember { mutableStateOf("DEEP_WORK") }
    var newCategory by remember { mutableStateOf("WORK") }
    var newDod by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                                onAddBacklog(newTitle, newWorkType, newCategory, newMinutes, newDod)
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
                                    Text(
                                        text = "预估 ${item.estimatedMinutes}m · ${item.workType}",
                                        fontSize = 10.5.sp,
                                        color = TextSecondary
                                    )
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
    onDismiss: () -> Unit,
    onSave: (ScheduleEntity) -> Unit,
    onDelete: () -> Unit
) {
    var title by remember { mutableStateOf(item.title) }
    var workType by remember { mutableStateOf(item.workType) }
    var category by remember { mutableStateOf(item.category) }
    var minutes by remember { mutableIntStateOf(item.estimatedMinutes) }
    var dod by remember { mutableStateOf(item.dod) }

    AlertDialog(
        onDismissRequest = onDismiss,
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

                Text("预估耗时: $minutes 分钟", fontSize = 12.sp, color = TextSecondary)
                Slider(
                    value = minutes.toFloat(),
                    onValueChange = { minutes = it.toInt() },
                    valueRange = 15f..240f,
                    steps = 14,
                    colors = SliderDefaults.colors(thumbColor = DopamineBlue, activeTrackColor = DopamineBlue)
                )
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
                        updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
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
    onToggleStatus: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onNavigateToFitness: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val isDone = item.status == "COMPLETED"
    val accentColor = when (item.workType) {
        "DEEP_WORK" -> DopamineBlue
        "SHALLOW_WORK" -> DopamineAmber
        "REST_BUFFER" -> DopamineGreen
        else -> DopaminePurple
    }

    val startDt = parseDateTime(item.startTime)
    val timeStr = if (startDt != null) {
        startDt.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
    } else {
        item.startTime
    }

    val isFitnessItem = item.id.startsWith("fitness_") || item.category == "HEALTH" || item.title.contains("健身")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑", tint = DopamineBlue, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "删除", tint = DopamineRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "📅 预估起止: $timeStr · ${item.estimatedMinutes}分钟", fontSize = 12.5.sp, color = TextSecondary)
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
                    Text(text = "📂 业务分类: ${item.category}", fontSize = 12.5.sp, color = TextSecondary)
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
    return try {
        LocalDateTime.parse(str, DateTimeFormatter.ISO_DATE_TIME)
    } catch (_: Exception) {
        try {
            LocalDateTime.parse(str)
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun ScheduleItemCard(
    item: ScheduleEntity,
    onToggleStatus: () -> Unit,
    onClickCard: () -> Unit = {}
) {
    val isCompleted = item.status == "COMPLETED"
    val accentColor = when (item.workType) {
        "DEEP_WORK" -> DopamineBlue
        "SHALLOW_WORK" -> DopamineAmber
        "REST_BUFFER" -> DopamineGreen
        else -> DopaminePurple
    }

    val timeRangeStr = try {
        val s = LocalDateTime.parse(item.startTime, DateTimeFormatter.ISO_DATE_TIME)
        val e = LocalDateTime.parse(item.endTime, DateTimeFormatter.ISO_DATE_TIME)
        "${s.format(DateTimeFormatter.ofPattern("HH:mm"))} - ${e.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    } catch (_: Exception) {
        ""
    }

    Surface(
        onClick = onClickCard,
        shape = RoundedCornerShape(14.dp),
        color = if (isCompleted) DarkSurface.copy(alpha = 0.6f) else DarkCard,
        border = BorderStroke(1.dp, if (isCompleted) Color.Transparent else DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            IconButton(
                onClick = onToggleStatus,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = "完成状态",
                    tint = if (isCompleted) DopamineGreen else TextMuted,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                    color = if (isCompleted) TextMuted else TextPrimary
                )

                if (timeRangeStr.isNotBlank()) {
                    Text(
                        text = "⏱️ $timeRangeStr · ${item.estimatedMinutes}分钟",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                if (item.dod.isNotBlank()) {
                    Text(
                        text = "验收标准: ${item.dod}",
                        fontSize = 11.5.sp,
                        color = DopamineBlue.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        Surface(
                            color = DarkSurface,
                            border = BorderStroke(1.dp, DarkBorder),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = item.category,
                                color = TextMuted,
                                fontSize = 10.5.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
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
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var workType by remember { mutableStateOf("DEEP_WORK") }
    var category by remember { mutableStateOf("STUDY") }
    var minutes by remember { mutableIntStateOf(45) }

    AlertDialog(
        onDismissRequest = onDismiss,
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

                Text("预估耗时: $minutes 分钟", fontSize = 12.sp, color = TextSecondary)
                Slider(
                    value = minutes.toFloat(),
                    onValueChange = { minutes = it.toInt() },
                    valueRange = 15f..180f,
                    steps = 10,
                    colors = SliderDefaults.colors(thumbColor = DopamineBlue, activeTrackColor = DopamineBlue)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title, workType, category, minutes)
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

