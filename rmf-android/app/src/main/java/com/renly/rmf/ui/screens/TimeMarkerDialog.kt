package com.renly.rmf.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.data.local.entity.ScheduleEntity
import com.renly.rmf.data.local.entity.TimeMarkerEntity
import com.renly.rmf.domain.service.TimeMarkerVarianceAnalyzer
import com.renly.rmf.domain.service.VarianceInsight
import com.renly.rmf.ui.theme.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val MARKER_PALETTE = listOf(
    "#38BDF8" to "天空蓝",
    "#F59E0B" to "晨曦黄",
    "#10B981" to "薄荷绿",
    "#818CF8" to "薰衣紫",
    "#EC4899" to "蔷薇粉",
    "#64748B" to "石墨灰"
)

private val PRESET_ICONS = listOf(
    "🌅", "🌙", "💤", "🍱", "💻", "🏁", "🏃", "☕", "💊", "🚗", "🧘", "📍"
)

data class PresetMarkerOption(
    val name: String,
    val icon: String,
    val markerType: String, // POINT or RANGE
    val category: String,
    val colorHex: String,
    val defaultStartHour: Int,
    val defaultStartMinute: Int,
    val defaultDurationMinutes: Int = 0
)

private val PRESET_OPTIONS = listOf(
    PresetMarkerOption("起床", "🌅", "POINT", "SLEEP", "#F59E0B", 7, 30),
    PresetMarkerOption("午休", "💤", "RANGE", "REST", "#10B981", 12, 30, 60),
    PresetMarkerOption("睡觉", "🌙", "POINT", "SLEEP", "#818CF8", 23, 30),
    PresetMarkerOption("实际开工", "💻", "POINT", "WORK", "#38BDF8", 9, 0),
    PresetMarkerOption("午餐", "🍱", "RANGE", "MEAL", "#F59E0B", 12, 0, 40),
    PresetMarkerOption("实际收工", "🏁", "POINT", "WORK", "#38BDF8", 18, 30),
    PresetMarkerOption("运动锻炼", "🏃", "RANGE", "EXERCISE", "#EC4899", 19, 30, 45),
    PresetMarkerOption("夜间睡眠", "🌙", "RANGE", "SLEEP", "#818CF8", 23, 30, 450)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeMarkerDialog(
    selectedDate: LocalDate,
    schedules: List<ScheduleEntity>,
    markers: List<TimeMarkerEntity>,
    onDismiss: () -> Unit,
    onAddMarker: (TimeMarkerEntity) -> Unit,
    onUpdateMarker: (TimeMarkerEntity) -> Unit,
    onDeleteMarker: (String) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val dayMarkers = remember(markers, selectedDate) {
        markers.filter {
            it.isDeleted == 0 && (it.date == selectedDate.toString() || it.startTime.startsWith(selectedDate.toString()))
        }.sortedBy { it.startTime }
    }

    val varianceInsights = remember(selectedDate, schedules, dayMarkers) {
        TimeMarkerVarianceAnalyzer.analyzeDay(selectedDate, schedules, dayMarkers)
    }

    var selectedTab by remember { mutableIntStateOf(if (dayMarkers.isEmpty()) 1 else 0) }
    var editingMarker by remember { mutableStateOf<TimeMarkerEntity?>(null) }
    var showCustomEditor by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📍", fontSize = 20.sp, modifier = Modifier.padding(end = 6.dp))
                    Text(
                        text = "时间标记与实际偏差透视",
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
                Text(
                    text = "${selectedDate.format(DateTimeFormatter.ofPattern("M月d日 EEEE"))} · 记录实际真实作息与计划偏差",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 顶部切换分栏
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkBg,
                    contentColor = DopamineCyan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text("📊 标记与差异 (${dayMarkers.size})", fontSize = 11.5.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text("⚡ 快捷预设打卡", fontSize = 11.5.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = {
                            editingMarker = null
                            selectedTab = 2
                        },
                        text = {
                            Text("➕ 自定义标记", fontSize = 11.5.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal)
                        }
                    )
                }
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp, max = 460.dp)
            ) {
                when (selectedTab) {
                    0 -> MarkersAndVarianceListTab(
                        date = selectedDate,
                        markers = dayMarkers,
                        insights = varianceInsights,
                        onEditMarker = { marker ->
                            editingMarker = marker
                            selectedTab = 2
                        },
                        onDeleteMarker = { id ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDeleteMarker(id)
                            Toast.makeText(context, "已删除时间标记", Toast.LENGTH_SHORT).show()
                        },
                        onFastAddPreset = { selectedTab = 1 }
                    )

                    1 -> QuickPresetTab(
                        selectedDate = selectedDate,
                        onSelectPreset = { preset ->
                            val now = LocalTime.now()
                            val nowIso = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                            val startDt = selectedDate.atTime(now.hour, now.minute)
                            val startIso = startDt.format(DateTimeFormatter.ISO_DATE_TIME)
                            val endIso = if (preset.markerType == "RANGE") {
                                startDt.plusMinutes(preset.defaultDurationMinutes.toLong().coerceAtLeast(30)).format(DateTimeFormatter.ISO_DATE_TIME)
                            } else ""

                            val marker = TimeMarkerEntity(
                                name = preset.name,
                                markerType = preset.markerType,
                                category = preset.category,
                                icon = preset.icon,
                                date = selectedDate.toString(),
                                startTime = startIso,
                                endTime = endIso,
                                colorHex = preset.colorHex,
                                notes = "快捷预设标记",
                                createdAt = nowIso,
                                updatedAt = nowIso
                            )
                            onAddMarker(marker)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            Toast.makeText(context, "✓ 已标记【${preset.icon} ${preset.name}】", Toast.LENGTH_SHORT).show()
                            selectedTab = 0
                        },
                        onCustomClick = { selectedTab = 2 }
                    )

                    2 -> CustomMarkerEditorTab(
                        selectedDate = selectedDate,
                        initialMarker = editingMarker,
                        onSave = { marker ->
                            if (editingMarker != null) {
                                onUpdateMarker(marker)
                                Toast.makeText(context, "✓ 时间标记已更新", Toast.LENGTH_SHORT).show()
                            } else {
                                onAddMarker(marker)
                                Toast.makeText(context, "✓ 时间标记已创建", Toast.LENGTH_SHORT).show()
                            }
                            editingMarker = null
                            selectedTab = 0
                        },
                        onDelete = if (editingMarker != null) {
                            {
                                onDeleteMarker(editingMarker!!.id)
                                Toast.makeText(context, "已删除标记", Toast.LENGTH_SHORT).show()
                                editingMarker = null
                                selectedTab = 0
                            }
                        } else null
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成", color = DopamineCyan, fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Tab 1: 今日时间标记与计划偏差洞察列表
 */
@Composable
private fun MarkersAndVarianceListTab(
    date: LocalDate,
    markers: List<TimeMarkerEntity>,
    insights: List<VarianceInsight>,
    onEditMarker: (TimeMarkerEntity) -> Unit,
    onDeleteMarker: (String) -> Unit,
    onFastAddPreset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 1. 计划 vs 实际差异透视卡片 (Variance Insight Card)
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkBg),
            border = BorderStroke(1.dp, DopamineCyan.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🤖", fontSize = 14.sp, modifier = Modifier.padding(end = 4.dp))
                    Text(
                        text = "计划与实际执行偏差诊断",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = DopamineCyan
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "自动同步至AI复盘",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                insights.forEach { item ->
                    val badgeColor = when (item.level) {
                        "WARNING" -> DopamineAmber
                        "SUCCESS" -> DopamineGreen
                        else -> DopamineBlue
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(item.icon, fontSize = 12.5.sp, modifier = Modifier.padding(end = 6.dp, top = 1.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = badgeColor
                            )
                            Text(
                                text = item.detail,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. 标记列表
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📍", fontSize = 13.sp, modifier = Modifier.padding(end = 4.dp))
            Text(
                text = "今日已记录的真实时间点 / 时间段",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = onFastAddPreset,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("+ 快速标记", fontSize = 11.5.sp, color = DopamineAmber)
            }
        }

        if (markers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⏰", fontSize = 28.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("今日尚无真实作息与时间标记", fontSize = 12.5.sp, color = TextSecondary)
                    Text("切换到「快捷预设打卡」标记起床、就寝或午休吧！", fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.7f))
                }
            }
        } else {
            markers.forEach { marker ->
                val markerColor = try {
                    Color(android.graphics.Color.parseColor(marker.colorHex))
                } catch (_: Exception) {
                    DopamineCyan
                }

                val startTimeStr = formatTimeShort(marker.startTime)
                val timeRangeStr = if (marker.markerType == "RANGE" && marker.endTime.isNotBlank()) {
                    "$startTimeStr ~ ${formatTimeShort(marker.endTime)}"
                } else {
                    startTimeStr
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkBg,
                    border = BorderStroke(1.dp, markerColor.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onEditMarker(marker) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(marker.icon, fontSize = 18.sp, modifier = Modifier.padding(end = 8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = marker.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = markerColor.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = if (marker.markerType == "RANGE") "时间段" else "时间点",
                                        fontSize = 9.5.sp,
                                        color = markerColor,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "实际时间: $timeRangeStr",
                                    fontSize = 11.5.sp,
                                    color = DopamineAmber,
                                    fontWeight = FontWeight.Medium
                                )
                                if (marker.notes.isNotBlank()) {
                                    Text(
                                        text = " · ${marker.notes}",
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { onDeleteMarker(marker.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "删除", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab 2: 快捷预设打卡
 */
@Composable
private fun QuickPresetTab(
    selectedDate: LocalDate,
    onSelectPreset: (PresetMarkerOption) -> Unit,
    onCustomClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "点击预设图标即可秒级以当前时间打卡标记（可在详情内随时微调时间）：",
            fontSize = 11.5.sp,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        PRESET_OPTIONS.chunked(2).forEach { rowOptions ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowOptions.forEach { opt ->
                    val color = try {
                        Color(android.graphics.Color.parseColor(opt.colorHex))
                    } catch (_: Exception) {
                        DopamineCyan
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkBg),
                        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectPreset(opt) }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(opt.icon, fontSize = 22.sp, modifier = Modifier.padding(end = 8.dp))
                            Column {
                                Text(
                                    text = opt.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (opt.markerType == "RANGE") "时间段 (${opt.defaultDurationMinutes}m)" else "时间点标记",
                                    fontSize = 10.5.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onCustomClick,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, DopamineCyan),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = DopamineCyan, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("自定义特殊标记（自由设置时间与图标）", color = DopamineCyan, fontSize = 12.5.sp)
        }
    }
}

/**
 * Tab 3: 自定义时间标记编辑器
 */
@Composable
private fun CustomMarkerEditorTab(
    selectedDate: LocalDate,
    initialMarker: TimeMarkerEntity?,
    onSave: (TimeMarkerEntity) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val now = remember { LocalTime.now() }
    val initialStartHour = remember {
        initialMarker?.let { parseHour(it.startTime) } ?: now.hour
    }
    val initialStartMin = remember {
        initialMarker?.let { parseMinute(it.startTime) } ?: now.minute
    }
    val initialEndHour = remember {
        initialMarker?.let { parseHour(it.endTime) } ?: ((now.hour + 1) % 24)
    }
    val initialEndMin = remember {
        initialMarker?.let { parseMinute(it.endTime) } ?: now.minute
    }

    var name by remember { mutableStateOf(initialMarker?.name ?: "") }
    var markerType by remember { mutableStateOf(initialMarker?.markerType ?: "POINT") }
    var startHour by remember { mutableIntStateOf(initialStartHour) }
    var startMinute by remember { mutableIntStateOf(initialStartMin) }
    var endHour by remember { mutableIntStateOf(initialEndHour) }
    var endMinute by remember { mutableIntStateOf(initialEndMin) }
    var selectedIcon by remember { mutableStateOf(initialMarker?.icon ?: "📍") }
    var selectedColorHex by remember { mutableStateOf(initialMarker?.colorHex ?: "#38BDF8") }
    var notes by remember { mutableStateOf(initialMarker?.notes ?: "") }

    val defaultCyan = DopamineCyan
    val accentColor = remember(selectedColorHex, defaultCyan) {
        try {
            Color(android.graphics.Color.parseColor(selectedColorHex))
        } catch (_: Exception) {
            defaultCyan
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 1. 标记名称
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text("标记名称，如：起床、午休、专注、就寝...", color = TextSecondary, fontSize = 12.5.sp) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkBg,
                unfocusedContainerColor = DarkBg,
                focusedBorderColor = accentColor,
                unfocusedBorderColor = DarkBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 2. 类型选择：时间点 vs 时间段
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = { markerType = "POINT" },
                shape = RoundedCornerShape(8.dp),
                color = if (markerType == "POINT") accentColor.copy(alpha = 0.2f) else DarkBg,
                border = BorderStroke(1.dp, if (markerType == "POINT") accentColor else DarkBorder),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("⏰ 时间点标记", fontSize = 12.sp, fontWeight = if (markerType == "POINT") FontWeight.Bold else FontWeight.Normal, color = if (markerType == "POINT") accentColor else TextPrimary)
                }
            }

            Surface(
                onClick = { markerType = "RANGE" },
                shape = RoundedCornerShape(8.dp),
                color = if (markerType == "RANGE") accentColor.copy(alpha = 0.2f) else DarkBg,
                border = BorderStroke(1.dp, if (markerType == "RANGE") accentColor else DarkBorder),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("⏳ 时间段标记", fontSize = 12.sp, fontWeight = if (markerType == "RANGE") FontWeight.Bold else FontWeight.Normal, color = if (markerType == "RANGE") accentColor else TextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. 时间配置 (开始时间，若是 RANGE 则还有结束时间)
        Text("时间设置（自定义）", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(if (markerType == "RANGE") "开始时间" else "打卡时间", fontSize = 10.5.sp, color = TextSecondary)
                TimeInputRow(
                    hour = startHour,
                    minute = startMinute,
                    onHourChange = { startHour = it },
                    onMinuteChange = { startMinute = it }
                )
            }

            if (markerType == "RANGE") {
                Column(modifier = Modifier.weight(1f)) {
                    Text("结束时间", fontSize = 10.5.sp, color = TextSecondary)
                    TimeInputRow(
                        hour = endHour,
                        minute = endMinute,
                        onHourChange = { endHour = it },
                        onMinuteChange = { endMinute = it }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 4. 图标选择
        Text("图标表情", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PRESET_ICONS.forEach { icon ->
                val isSelected = selectedIcon == icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) accentColor.copy(alpha = 0.25f) else DarkBg)
                        .border(1.dp, if (isSelected) accentColor else DarkBorder, CircleShape)
                        .clickable { selectedIcon = icon },
                    contentAlignment = Alignment.Center
                ) {
                    Text(icon, fontSize = 16.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 5. 颜色调色盘
        Text("标记颜色", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MARKER_PALETTE.forEach { (hex, _) ->
                val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
                val color = Color(android.graphics.Color.parseColor(hex))
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (isSelected) 2.5.dp else 0.dp,
                            color = if (isSelected) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { selectedColorHex = hex },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 6. 备注输入
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            placeholder = { Text("备注说明（可选，如：今早熬夜推迟起床）", color = TextSecondary, fontSize = 11.5.sp) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkBg,
                unfocusedContainerColor = DarkBg,
                focusedBorderColor = accentColor,
                unfocusedBorderColor = DarkBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 保存与删除按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onDelete != null) {
                OutlinedButton(
                    onClick = onDelete,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, DopamineRed),
                    modifier = Modifier.weight(0.4f)
                ) {
                    Text("删除", color = DopamineRed, fontSize = 12.sp)
                }
            }

            Button(
                onClick = {
                    val finalName = name.ifBlank { "时间标记" }
                    val startDt = selectedDate.atTime(startHour, startMinute)
                    val startIso = startDt.format(DateTimeFormatter.ISO_DATE_TIME)
                    val endIso = if (markerType == "RANGE") {
                        selectedDate.atTime(endHour, endMinute).format(DateTimeFormatter.ISO_DATE_TIME)
                    } else ""

                    val nowIso = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                    val marker = initialMarker?.copy(
                        name = finalName,
                        markerType = markerType,
                        icon = selectedIcon,
                        startTime = startIso,
                        endTime = endIso,
                        colorHex = selectedColorHex,
                        notes = notes,
                        updatedAt = nowIso
                    ) ?: TimeMarkerEntity(
                        name = finalName,
                        markerType = markerType,
                        category = "CUSTOM",
                        icon = selectedIcon,
                        date = selectedDate.toString(),
                        startTime = startIso,
                        endTime = endIso,
                        colorHex = selectedColorHex,
                        notes = notes,
                        createdAt = nowIso,
                        updatedAt = nowIso
                    )
                    onSave(marker)
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                modifier = Modifier.weight(1f)
            ) {
                Text("保存标记", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun TimeInputRow(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = DarkBg,
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // 小时增减
            Text(
                text = "%02d".format(hour),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = DopamineCyan
            )
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = "▲",
                    fontSize = 9.sp,
                    color = TextSecondary,
                    modifier = Modifier.clickable { onHourChange((hour + 1) % 24) }
                )
                Text(
                    text = "▼",
                    fontSize = 9.sp,
                    color = TextSecondary,
                    modifier = Modifier.clickable { onHourChange((hour + 23) % 24) }
                )
            }

            Text(" : ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(horizontal = 4.dp))

            // 分钟增减
            Text(
                text = "%02d".format(minute),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = DopamineCyan
            )
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = "▲",
                    fontSize = 9.sp,
                    color = TextSecondary,
                    modifier = Modifier.clickable { onMinuteChange(((minute / 5 + 1) * 5) % 60) }
                )
                Text(
                    text = "▼",
                    fontSize = 9.sp,
                    color = TextSecondary,
                    modifier = Modifier.clickable { onMinuteChange(((minute / 5 - 1 + 12) * 5) % 60) }
                )
            }
        }
    }
}

private fun formatTimeShort(iso: String): String {
    return try {
        if (iso.isBlank()) ""
        else if (iso.contains("T")) {
            val dt = LocalDateTime.parse(iso)
            "%02d:%02d".format(dt.hour, dt.minute)
        } else iso
    } catch (_: Exception) {
        if (iso.length >= 16) iso.substring(11, 16) else iso
    }
}

private fun parseHour(iso: String): Int {
    return try {
        if (iso.contains("T")) LocalDateTime.parse(iso).hour else 0
    } catch (_: Exception) { 0 }
}

private fun parseMinute(iso: String): Int {
    return try {
        if (iso.contains("T")) LocalDateTime.parse(iso).minute else 0
    } catch (_: Exception) { 0 }
}
