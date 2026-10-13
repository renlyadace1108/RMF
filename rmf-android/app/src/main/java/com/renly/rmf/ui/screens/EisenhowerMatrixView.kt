package com.renly.rmf.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.data.local.entity.ScheduleEntity
import com.renly.rmf.data.local.entity.ScheduleTagEntity
import com.renly.rmf.domain.model.SubTaskItem
import com.renly.rmf.ui.theme.*
import com.renly.rmf.util.HapticHelper
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 🎯 艾森豪威尔四象限法则专有色彩规范 (Eisenhower Standard Color Palette)
 * 绝对解耦于用户全局强调色 (Accent Color)，确保时间管理四象限色彩认知稳定统一：
 * - Q1 (重要且紧急): 危机/截止日/突发 立即执行 🔥 警示赤红
 * - Q2 (重要不紧急): 规划/学习/深度专注 重点投入 ⭐ 核心蔚蓝
 * - Q3 (不重要但紧急): 琐事/打扰/代理委派 快速处理 ⚡ 活力琥珀
 * - Q4 (不重要不紧急): 休闲/整理/消除 适当舍弃 🍃 极简薄荷
 */
val QuadrantQ1Color = Color(0xFFEF4444) // 鲜明赤红
val QuadrantQ2Color = Color(0xFF0284C7) // 经典科技蔚蓝
val QuadrantQ3Color = Color(0xFFF59E0B) // 活力金琥珀
val QuadrantQ4Color = Color(0xFF10B981) // 翡翠薄荷绿

data class QuadrantInfo(
    val code: String,
    val name: String,
    val subtitle: String,
    val color: Color,
    val emoji: String,
    val actionAdvice: String
)

/**
 * 智能获取任务的生效象限（若未设置则根据优先级 URGENT->Q1, HIGH->Q2, MEDIUM->Q3, LOW->Q4 自动映射）
 */
fun ScheduleEntity.getEffectiveQuadrant(): String {
    val q = eisenhowerQuadrant
    if (!q.isNullOrBlank() && q in listOf("Q1", "Q2", "Q3", "Q4")) return q
    return when (priority.uppercase()) {
        "URGENT" -> "Q1"
        "HIGH" -> "Q2"
        "MEDIUM" -> "Q3"
        "LOW" -> "Q4"
        else -> "Q2"
    }
}

/**
 * 同一类别的事件按时间远近来排列，越近的越靠前
 */
fun sortQuadrantItems(items: List<ScheduleEntity>): List<ScheduleEntity> {
    return items.sortedWith { a, b ->
        // 1. 完成状态优先：未完成在前，已完成沉底
        val aDone = if (a.status == "COMPLETED") 1 else 0
        val bDone = if (b.status == "COMPLETED") 1 else 0
        if (aDone != bDone) return@sortedWith aDone.compareTo(bDone)

        // 2. 同一类别归集聚合
        val catCompare = a.category.compareTo(b.category)
        if (catCompare != 0) return@sortedWith catCompare

        // 3. 同一类别内：按时间远近排列，越近的越靠前
        val aTime = parseDateTimeOrNull(a.startTime)
        val bTime = parseDateTimeOrNull(b.startTime)
        when {
            aTime != null && bTime != null -> aTime.compareTo(bTime) // 时间更早/更近的排在前面
            aTime != null && bTime == null -> -1 // 标注了具体时间的排在未标注时间的前面
            aTime == null && bTime != null -> 1
            else -> b.createdAt.compareTo(a.createdAt) // 无具体时间的按创建时间倒序
        }
    }
}

private fun parseDateTimeOrNull(timeStr: String): LocalDateTime? {
    if (timeStr.isBlank()) return null
    return try {
        LocalDateTime.parse(timeStr)
    } catch (e: Exception) {
        null
    }
}

@Composable
fun EisenhowerMatrixView(
    schedules: List<ScheduleEntity>,
    backlogItems: List<ScheduleEntity>,
    availableTags: List<ScheduleTagEntity>,
    tagColorMap: Map<String, Color>,
    onItemClick: (ScheduleEntity) -> Unit,
    onToggleStatus: (ScheduleEntity) -> Unit,
    onMoveQuadrant: (ScheduleEntity, String) -> Unit,
    onUpdateSchedule: (ScheduleEntity) -> Unit = {},
    onAddNewInQuadrant: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 合并所有有效日程与待办池任务，确保所有待办任务全面接入四象限
    val allActive = remember(schedules, backlogItems) {
        val combined = (schedules + backlogItems).filter { it.isDeleted == 0 && it.status != "ABANDONED" }
        combined.distinctBy { it.id }
    }

    var selectedQuadrantTab by remember { mutableStateOf("ALL") }
    // 视图模式：GRID (2x2 经典宫格) 或 LIST (纵向全览清单)
    var isGridView by remember { mutableStateOf(true) }

    // 标注开始时间弹窗状态
    var annotatingItem by remember { mutableStateOf<ScheduleEntity?>(null) }

    // 长按拖拽跨象限移动状态
    var draggedItem by remember { mutableStateOf<ScheduleEntity?>(null) }
    var dragTouchPosition by remember { mutableStateOf(Offset.Zero) }
    var hoveredQuadrant by remember { mutableStateOf<String?>(null) }
    val quadrantBounds = remember { mutableStateMapOf<String, Rect>() }

    val quadrants = remember {
        listOf(
            QuadrantInfo("Q1", "重要且紧急", "立即处理 · 绝不拖延", QuadrantQ1Color, "🔥", "危机应对"),
            QuadrantInfo("Q2", "重要不紧急", "核心规划 · 打造高价值", QuadrantQ2Color, "⭐", "深度专注"),
            QuadrantInfo("Q3", "不重要但紧急", "琐事代理 · 快速通关", QuadrantQ3Color, "⚡", "授权委派"),
            QuadrantInfo("Q4", "不重要不紧急", "休闲消除 · 减负极简", QuadrantQ4Color, "🍃", "断舍离")
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 76.dp), // 留出空间避免 FAB 遮挡
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // -------------------------------------------------------------
            // 1. 顶部专业工具栏：流式胶囊筛选 + 宫格/列表切换
            // -------------------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧：水平可滑动的象限切换药丸胶囊
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 全貌药丸
                    val isAllSel = selectedQuadrantTab == "ALL"
                    Surface(
                        onClick = {
                            HapticHelper.performLightClick(context)
                            selectedQuadrantTab = "ALL"
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isAllSel) DopaminePurple.copy(alpha = 0.2f) else DarkSurface,
                        border = BorderStroke(1.dp, if (isAllSel) DopaminePurple else DarkBorder.copy(alpha = 0.6f)),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "四象限全貌",
                                fontSize = 11.5.sp,
                                fontWeight = if (isAllSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isAllSel) DopaminePurple else TextSecondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                color = if (isAllSel) DopaminePurple.copy(alpha = 0.3f) else DarkBg,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = "${allActive.size}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAllSel) DopaminePurple else TextMuted,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    // 各象限药丸 (Q1 ~ Q4)
                    quadrants.forEach { q ->
                        val count = allActive.count { it.getEffectiveQuadrant() == q.code }
                        val isSel = selectedQuadrantTab == q.code
                        Surface(
                            onClick = {
                                HapticHelper.performLightClick(context)
                                selectedQuadrantTab = q.code
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSel) q.color.copy(alpha = 0.2f) else DarkSurface,
                            border = BorderStroke(1.dp, if (isSel) q.color else DarkBorder.copy(alpha = 0.6f)),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(q.emoji, fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = q.code,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) q.color else TextSecondary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    color = if (isSel) q.color.copy(alpha = 0.3f) else DarkBg,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = "$count",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) q.color else TextMuted,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 右侧：2x2 宫格 / 清单流 视图切换
                Surface(
                    onClick = {
                        HapticHelper.performLightClick(context)
                        isGridView = !isGridView
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.height(32.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "切换视图",
                            tint = DopamineCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isGridView) "清单" else "宫格",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = DopamineCyan
                        )
                    }
                }
            }

            // 拖拽提示条
            if (draggedItem != null) {
                Surface(
                    color = DopamineAmber.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, DopamineAmber),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("🖐️", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "正在拖动「${draggedItem?.title}」${if (hoveredQuadrant != null) " → 松手置入 $hoveredQuadrant" else " (移动到目标象限上方)"}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = DopamineAmber
                        )
                    }
                }
            }

            // -------------------------------------------------------------
            // 2. 主体内容呈现区
            // -------------------------------------------------------------
            if (selectedQuadrantTab == "ALL") {
                if (isGridView) {
                    // 2x2 经典四象限宫格（上下两行，每行两个卡片，支持长按拖动与同一类别按时间排序）
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 上半部：Q1 紧急重要 + Q2 核心重要
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QuadrantBox(
                                info = quadrants[0], // Q1
                                items = sortQuadrantItems(allActive.filter { it.getEffectiveQuadrant() == "Q1" }),
                                tagColorMap = tagColorMap,
                                isHovered = (hoveredQuadrant == "Q1"),
                                onBoundsCalculated = { rect -> quadrantBounds["Q1"] = rect },
                                onItemClick = onItemClick,
                                onToggleStatus = {
                                    HapticHelper.performSuccess(context)
                                    onToggleStatus(it)
                                },
                                onAnnotateStartTime = { annotatingItem = it },
                                onAddNewTask = { onAddNewInQuadrant("Q1") },
                                onDragStart = { item, pos ->
                                    draggedItem = item
                                    dragTouchPosition = pos
                                },
                                onDragMove = { pos ->
                                    dragTouchPosition = pos
                                    hoveredQuadrant = quadrantBounds.entries.firstOrNull { it.value.contains(pos) }?.key
                                },
                                onDragEnd = {
                                    val targetQ = hoveredQuadrant
                                    if (targetQ != null && draggedItem != null && targetQ != draggedItem?.getEffectiveQuadrant()) {
                                        HapticHelper.performQuadrantMoved(context)
                                        onMoveQuadrant(draggedItem!!, targetQ)
                                    }
                                    draggedItem = null
                                    hoveredQuadrant = null
                                },
                                modifier = Modifier.weight(1f)
                            )
                            QuadrantBox(
                                info = quadrants[1], // Q2
                                items = sortQuadrantItems(allActive.filter { it.getEffectiveQuadrant() == "Q2" }),
                                tagColorMap = tagColorMap,
                                isHovered = (hoveredQuadrant == "Q2"),
                                onBoundsCalculated = { rect -> quadrantBounds["Q2"] = rect },
                                onItemClick = onItemClick,
                                onToggleStatus = {
                                    HapticHelper.performSuccess(context)
                                    onToggleStatus(it)
                                },
                                onAnnotateStartTime = { annotatingItem = it },
                                onAddNewTask = { onAddNewInQuadrant("Q2") },
                                onDragStart = { item, pos ->
                                    draggedItem = item
                                    dragTouchPosition = pos
                                },
                                onDragMove = { pos ->
                                    dragTouchPosition = pos
                                    hoveredQuadrant = quadrantBounds.entries.firstOrNull { it.value.contains(pos) }?.key
                                },
                                onDragEnd = {
                                    val targetQ = hoveredQuadrant
                                    if (targetQ != null && draggedItem != null && targetQ != draggedItem?.getEffectiveQuadrant()) {
                                        HapticHelper.performQuadrantMoved(context)
                                        onMoveQuadrant(draggedItem!!, targetQ)
                                    }
                                    draggedItem = null
                                    hoveredQuadrant = null
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // 下半部：Q3 紧急不重要 + Q4 不重要不紧急
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QuadrantBox(
                                info = quadrants[2], // Q3
                                items = sortQuadrantItems(allActive.filter { it.getEffectiveQuadrant() == "Q3" }),
                                tagColorMap = tagColorMap,
                                isHovered = (hoveredQuadrant == "Q3"),
                                onBoundsCalculated = { rect -> quadrantBounds["Q3"] = rect },
                                onItemClick = onItemClick,
                                onToggleStatus = {
                                    HapticHelper.performSuccess(context)
                                    onToggleStatus(it)
                                },
                                onAnnotateStartTime = { annotatingItem = it },
                                onAddNewTask = { onAddNewInQuadrant("Q3") },
                                onDragStart = { item, pos ->
                                    draggedItem = item
                                    dragTouchPosition = pos
                                },
                                onDragMove = { pos ->
                                    dragTouchPosition = pos
                                    hoveredQuadrant = quadrantBounds.entries.firstOrNull { it.value.contains(pos) }?.key
                                },
                                onDragEnd = {
                                    val targetQ = hoveredQuadrant
                                    if (targetQ != null && draggedItem != null && targetQ != draggedItem?.getEffectiveQuadrant()) {
                                        HapticHelper.performQuadrantMoved(context)
                                        onMoveQuadrant(draggedItem!!, targetQ)
                                    }
                                    draggedItem = null
                                    hoveredQuadrant = null
                                },
                                modifier = Modifier.weight(1f)
                            )
                            QuadrantBox(
                                info = quadrants[3], // Q4
                                items = sortQuadrantItems(allActive.filter { it.getEffectiveQuadrant() == "Q4" }),
                                tagColorMap = tagColorMap,
                                isHovered = (hoveredQuadrant == "Q4"),
                                onBoundsCalculated = { rect -> quadrantBounds["Q4"] = rect },
                                onItemClick = onItemClick,
                                onToggleStatus = {
                                    HapticHelper.performSuccess(context)
                                    onToggleStatus(it)
                                },
                                onAnnotateStartTime = { annotatingItem = it },
                                onAddNewTask = { onAddNewInQuadrant("Q4") },
                                onDragStart = { item, pos ->
                                    draggedItem = item
                                    dragTouchPosition = pos
                                },
                                onDragMove = { pos ->
                                    dragTouchPosition = pos
                                    hoveredQuadrant = quadrantBounds.entries.firstOrNull { it.value.contains(pos) }?.key
                                },
                                onDragEnd = {
                                    val targetQ = hoveredQuadrant
                                    if (targetQ != null && draggedItem != null && targetQ != draggedItem?.getEffectiveQuadrant()) {
                                        HapticHelper.performQuadrantMoved(context)
                                        onMoveQuadrant(draggedItem!!, targetQ)
                                    }
                                    draggedItem = null
                                    hoveredQuadrant = null
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    // 清单流水模式：Q1 ~ Q4 纵向折叠展开流（同一类别按时间先后排列）
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        quadrants.forEach { q ->
                            val itemsInQ = sortQuadrantItems(allActive.filter { it.getEffectiveQuadrant() == q.code })
                            item(key = q.code) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = DarkCard,
                                    border = BorderStroke(1.dp, q.color.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        // 象限分段标题
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                        ) {
                                            Text(q.emoji, fontSize = 16.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "${q.code} ${q.name}",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = q.color
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "· ${q.subtitle}",
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                            Spacer(modifier = Modifier.weight(1f))
                                            Surface(
                                                color = q.color.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "${itemsInQ.size} 项",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = q.color,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                onClick = { onAddNewInQuadrant(q.code) },
                                                shape = CircleShape,
                                                color = q.color.copy(alpha = 0.15f),
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.Add, contentDescription = "新增任务", tint = q.color, modifier = Modifier.size(15.dp))
                                                }
                                            }
                                        }

                                        if (itemsInQ.isEmpty()) {
                                            Text(
                                                text = "当前象限暂无任务，轻松无负担 🍃",
                                                fontSize = 11.5.sp,
                                                color = TextMuted,
                                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                                            )
                                        } else {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                itemsInQ.forEach { item ->
                                                    QuadrantDetailedCard(
                                                        item = item,
                                                        quadrantColor = q.color,
                                                        tagColorMap = tagColorMap,
                                                        onItemClick = { onItemClick(item) },
                                                        onToggleStatus = {
                                                            HapticHelper.performSuccess(context)
                                                            onToggleStatus(item)
                                                        },
                                                        onAnnotateStartTime = { annotatingItem = item },
                                                        onChangeQuadrant = { targetQ ->
                                                            HapticHelper.performQuadrantMoved(context)
                                                            onMoveQuadrant(item, targetQ)
                                                        }
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
            } else {
                // 单一聚焦象限视图 (Q1 / Q2 / Q3 / Q4 独立详情流)
                val currentQ = quadrants.firstOrNull { it.code == selectedQuadrantTab } ?: quadrants[0]
                val filtered = sortQuadrantItems(allActive.filter { it.getEffectiveQuadrant() == currentQ.code })

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkCard,
                    border = BorderStroke(1.dp, currentQ.color.copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Text(currentQ.emoji, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${currentQ.code} ${currentQ.name}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = currentQ.color
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = currentQ.color.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = currentQ.actionAdvice,
                                            fontSize = 10.sp,
                                            color = currentQ.color,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = currentQ.subtitle,
                                    fontSize = 11.5.sp,
                                    color = TextSecondary
                                )
                            }

                            Surface(
                                color = currentQ.color.copy(alpha = 0.15f),
                                shape = CircleShape
                            ) {
                                Text(
                                    text = "${filtered.size} 项",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = currentQ.color,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                onClick = { onAddNewInQuadrant(currentQ.code) },
                                shape = CircleShape,
                                color = currentQ.color.copy(alpha = 0.18f),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, contentDescription = "新增", tint = currentQ.color, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        if (filtered.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(currentQ.emoji, fontSize = 32.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "该象限暂无任务",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextSecondary
                                    )
                                    Text(
                                        "可在编辑任务时调整象限归属",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filtered, key = { it.id }) { item ->
                                    QuadrantDetailedCard(
                                        item = item,
                                        quadrantColor = currentQ.color,
                                        tagColorMap = tagColorMap,
                                        onItemClick = { onItemClick(item) },
                                        onToggleStatus = {
                                            HapticHelper.performSuccess(context)
                                            onToggleStatus(item)
                                        },
                                        onAnnotateStartTime = { annotatingItem = item },
                                        onChangeQuadrant = { targetQ ->
                                            HapticHelper.performQuadrantMoved(context)
                                            onMoveQuadrant(item, targetQ)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 标注开始时间弹窗 (AnnotateStartTimeDialog)
        if (annotatingItem != null) {
            AnnotateStartTimeDialog(
                schedule = annotatingItem!!,
                onDismiss = { annotatingItem = null },
                onSave = { updated ->
                    onUpdateSchedule(updated)
                    annotatingItem = null
                }
            )
        }
    }
}

/**
 * 2x2 宫格单个象限卡片
 * - 支持标注开始时间并显式展示
 * - 支持长按拖拽卡片在象限间移动
 * - 拖拽移动到该象限上方时高亮光晕
 */
@Composable
private fun QuadrantBox(
    info: QuadrantInfo,
    items: List<ScheduleEntity>,
    tagColorMap: Map<String, Color>,
    isHovered: Boolean,
    onBoundsCalculated: (Rect) -> Unit,
    onItemClick: (ScheduleEntity) -> Unit,
    onToggleStatus: (ScheduleEntity) -> Unit,
    onAnnotateStartTime: (ScheduleEntity) -> Unit,
    onAddNewTask: () -> Unit,
    onDragStart: (ScheduleEntity, Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isHovered) info.color.copy(alpha = 0.15f) else DarkCard,
        border = BorderStroke(
            width = if (isHovered) 2.dp else 1.dp,
            color = if (isHovered) info.color else info.color.copy(alpha = 0.35f)
        ),
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                onBoundsCalculated(coords.boundsInRoot())
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(7.dp)
        ) {
            // 象限头部：微彩光渐变底衬 + 计数指示 + 快速加号
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(info.color)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${info.code} ${info.name}",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = info.color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = info.color.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${items.size}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = info.color,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
                Spacer(modifier = Modifier.width(3.dp))
                Surface(
                    onClick = onAddNewTask,
                    shape = CircleShape,
                    color = info.color.copy(alpha = 0.15f),
                    modifier = Modifier.size(20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, contentDescription = "加任务", tint = info.color, modifier = Modifier.size(13.dp))
                    }
                }
            }

            if (isHovered) {
                Surface(
                    color = info.color.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                ) {
                    Text(
                        text = "松手移动至此象限",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = info.color,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(info.emoji, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "暂无任务",
                            fontSize = 10.5.sp,
                            color = TextMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(items, key = { it.id }) { item ->
                        val isDone = item.status == "COMPLETED"
                        var cardRootOffset by remember { mutableStateOf(Offset.Zero) }

                        Surface(
                            onClick = { onItemClick(item) },
                            shape = RoundedCornerShape(8.dp),
                            color = DarkSurface,
                            border = BorderStroke(0.5.dp, if (isDone) DarkBorder.copy(alpha = 0.4f) else DarkBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coords ->
                                    cardRootOffset = coords.boundsInRoot().topLeft
                                }
                                .pointerInput(item.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { localOffset ->
                                            HapticHelper.performLightClick(context)
                                            onDragStart(item, cardRootOffset + localOffset)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            cardRootOffset += dragAmount
                                            onDragMove(cardRootOffset)
                                        },
                                        onDragEnd = { onDragEnd() },
                                        onDragCancel = { onDragEnd() }
                                    )
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp, horizontal = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { onToggleStatus(item) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isDone) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                            contentDescription = "完成",
                                            tint = if (isDone) DopamineGreen else TextMuted,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            textDecoration = if (isDone) TextDecoration.LineThrough else null,
                                            color = if (isDone) TextMuted else TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        // 子步骤或标签指示
                                        val subtasks = remember(item.subtasksJson) { SubTaskItem.parseList(item.subtasksJson) }
                                        if (subtasks.isNotEmpty()) {
                                            val doneCount = subtasks.count { it.isDone }
                                            Text(
                                                text = "步骤 $doneCount/${subtasks.size}",
                                                fontSize = 9.sp,
                                                color = DopamineCyan
                                            )
                                        } else if (item.category.isNotBlank()) {
                                            val tagColor = tagColorMap[item.category] ?: info.color
                                            Text(
                                                text = item.category,
                                                fontSize = 9.sp,
                                                color = tagColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                // 标注开始时间指示与快捷编辑胶囊
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp)
                                ) {
                                    val formattedTime = remember(item.startTime) {
                                        val dt = parseDateTimeOrNull(item.startTime)
                                        if (dt != null) {
                                            val today = LocalDate.now()
                                            if (dt.toLocalDate() == today) {
                                                dt.format(DateTimeFormatter.ofPattern("HH:mm"))
                                            } else {
                                                dt.format(DateTimeFormatter.ofPattern("M/d HH:mm"))
                                            }
                                        } else null
                                    }

                                    Surface(
                                        onClick = { onAnnotateStartTime(item) },
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (formattedTime != null) DopamineCyan.copy(alpha = 0.12f) else DarkBg,
                                        border = BorderStroke(0.5.dp, if (formattedTime != null) DopamineCyan.copy(alpha = 0.45f) else DarkBorder)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text("⏰", fontSize = 8.5.sp)
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                text = formattedTime ?: "+标注时间",
                                                fontSize = 8.5.sp,
                                                fontWeight = if (formattedTime != null) FontWeight.Bold else FontWeight.Normal,
                                                color = if (formattedTime != null) DopamineCyan else TextMuted
                                            )
                                        }
                                    }

                                    Text(
                                        text = "${item.estimatedMinutes}m",
                                        fontSize = 8.5.sp,
                                        color = TextSecondary
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

/**
 * 纵向列表模式下的单项详细卡片
 */
@Composable
private fun QuadrantDetailedCard(
    item: ScheduleEntity,
    quadrantColor: Color,
    tagColorMap: Map<String, Color>,
    onItemClick: () -> Unit,
    onToggleStatus: () -> Unit,
    onAnnotateStartTime: () -> Unit,
    onChangeQuadrant: (String) -> Unit
) {
    val isDone = item.status == "COMPLETED"
    val subtasks = remember(item.subtasksJson) { SubTaskItem.parseList(item.subtasksJson) }

    Surface(
        onClick = onItemClick,
        shape = RoundedCornerShape(10.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, if (isDone) DarkBorder.copy(alpha = 0.5f) else quadrantColor.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onToggleStatus,
                    modifier = Modifier.size(24.dp)
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
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (isDone) TextDecoration.LineThrough else null,
                        color = if (isDone) TextMuted else TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = "${item.estimatedMinutes}m · ${item.workType}",
                            fontSize = 10.5.sp,
                            color = TextSecondary
                        )
                        if (item.category.isNotBlank()) {
                            val tagColor = tagColorMap[item.category] ?: quadrantColor
                            Surface(
                                color = tagColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = item.category,
                                    fontSize = 9.5.sp,
                                    color = tagColor,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }

                        // 标注开始时间
                        val formattedTime = remember(item.startTime) {
                            val dt = parseDateTimeOrNull(item.startTime)
                            if (dt != null) {
                                val today = LocalDate.now()
                                if (dt.toLocalDate() == today) {
                                    dt.format(DateTimeFormatter.ofPattern("HH:mm"))
                                } else {
                                    dt.format(DateTimeFormatter.ofPattern("M/d HH:mm"))
                                }
                            } else null
                        }

                        Surface(
                            onClick = onAnnotateStartTime,
                            shape = RoundedCornerShape(4.dp),
                            color = if (formattedTime != null) DopamineCyan.copy(alpha = 0.15f) else DarkBg,
                            border = BorderStroke(0.5.dp, if (formattedTime != null) DopamineCyan.copy(alpha = 0.4f) else DarkBorder)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text("⏰", fontSize = 9.sp)
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = formattedTime ?: "+标注时间",
                                    fontSize = 9.sp,
                                    fontWeight = if (formattedTime != null) FontWeight.Bold else FontWeight.Normal,
                                    color = if (formattedTime != null) DopamineCyan else TextMuted
                                )
                            }
                        }
                    }
                }

                // 快速切换象限微型选钮 (Q1 / Q2 / Q3 / Q4)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    val quadrantOptions = listOf(
                        "Q1" to QuadrantQ1Color,
                        "Q2" to QuadrantQ2Color,
                        "Q3" to QuadrantQ3Color,
                        "Q4" to QuadrantQ4Color
                    )
                    quadrantOptions.forEach { (q, qCol) ->
                        val isCurrent = item.getEffectiveQuadrant() == q
                        Surface(
                            onClick = { onChangeQuadrant(q) },
                            shape = RoundedCornerShape(4.dp),
                            color = if (isCurrent) qCol.copy(alpha = 0.25f) else DarkBg,
                            border = BorderStroke(0.5.dp, if (isCurrent) qCol else DarkBorder),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = q,
                                    fontSize = 9.5.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) qCol else TextMuted
                                )
                            }
                        }
                    }
                }
            }

            // Checklist 子任务展开进度
            if (subtasks.isNotEmpty()) {
                val doneCount = subtasks.count { it.isDone }
                val progress = doneCount.toFloat() / subtasks.size
                Spacer(modifier = Modifier.height(6.dp))
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
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "子步骤 $doneCount/${subtasks.size}",
                        fontSize = 10.sp,
                        color = DopamineCyan
                    )
                }
            }
        }
    }
}

/**
 * ⏰ 标注开始时间快捷弹窗 (AnnotateStartTimeDialog)
 * 支持快捷标注/修改任务在四象限中的执行开始日期与时间，并自动推导结束时间
 */
@Composable
fun AnnotateStartTimeDialog(
    schedule: ScheduleEntity,
    onDismiss: () -> Unit,
    onSave: (ScheduleEntity) -> Unit
) {
    val initialDt = remember(schedule.startTime) {
        parseDateTimeOrNull(schedule.startTime) ?: LocalDateTime.now().withMinute(0).withSecond(0).plusHours(1)
    }

    var selectedDate by remember { mutableStateOf(initialDt.toLocalDate()) }
    var selectedHour by remember { mutableIntStateOf(initialDt.hour) }
    var selectedMinute by remember { mutableIntStateOf((initialDt.minute / 5) * 5) }
    var durationMinutes by remember { mutableIntStateOf(if (schedule.estimatedMinutes > 0) schedule.estimatedMinutes else 30) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⏰ 标注开始时间", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "为「${schedule.title}」排定具体的开始时间与时长",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. 日期快捷选择 (今天 / 明天 / 后天 / 自定义)
                Text("📅 执行日期:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                val today = LocalDate.now()
                val dateOptions = listOf(
                    today to "今天 (${today.monthValue}/${today.dayOfMonth})",
                    today.plusDays(1) to "明天 (${today.plusDays(1).monthValue}/${today.plusDays(1).dayOfMonth})",
                    today.plusDays(2) to "后天 (${today.plusDays(2).monthValue}/${today.plusDays(2).dayOfMonth})"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dateOptions.forEach { (d, label) ->
                        val isSel = selectedDate == d
                        Surface(
                            onClick = { selectedDate = d },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) DopamineCyan.copy(alpha = 0.2f) else DarkSurface,
                            border = BorderStroke(1.dp, if (isSel) DopamineCyan else DarkBorder),
                            modifier = Modifier.weight(1f).height(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) DopamineCyan else TextSecondary
                                )
                            }
                        }
                    }
                }

                // 2. 开始时间 (常用时段快捷胶囊)
                Text("⏱️ 常用开始时段:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                val presetTimes = listOf(
                    Pair(9, 0) to "上午 09:00",
                    Pair(10, 0) to "上午 10:00",
                    Pair(14, 0) to "下午 14:00",
                    Pair(16, 0) to "下午 16:00",
                    Pair(19, 0) to "晚上 19:00",
                    Pair(21, 0) to "夜间 21:00"
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presetTimes) { (t, label) ->
                        val isSel = selectedHour == t.first && selectedMinute == t.second
                        Surface(
                            onClick = {
                                selectedHour = t.first
                                selectedMinute = t.second
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) DopamineBlue.copy(alpha = 0.22f) else DarkSurface,
                            border = BorderStroke(1.dp, if (isSel) DopamineBlue else DarkBorder),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) DopamineBlue else TextSecondary
                                )
                            }
                        }
                    }
                }

                // 精确时间调整条 (时 : 分)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceAround,
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("时", fontSize = 10.sp, color = TextMuted)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { selectedHour = (selectedHour - 1 + 24) % 24 }) {
                                    Text("◀", fontSize = 12.sp, color = TextSecondary)
                                }
                                Text("%02d".format(selectedHour), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = DopamineCyan)
                                TextButton(onClick = { selectedHour = (selectedHour + 1) % 24 }) {
                                    Text("▶", fontSize = 12.sp, color = TextSecondary)
                                }
                            }
                        }

                        Text(":", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextMuted)

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("分", fontSize = 10.sp, color = TextMuted)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { selectedMinute = (selectedMinute - 5 + 60) % 60 }) {
                                    Text("◀", fontSize = 12.sp, color = TextSecondary)
                                }
                                Text("%02d".format(selectedMinute), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = DopamineCyan)
                                TextButton(onClick = { selectedMinute = (selectedMinute + 5) % 60 }) {
                                    Text("▶", fontSize = 12.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                }

                // 3. 预估时长 (分钟)
                Text("⏳ 持续时长:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                val durationOptions = listOf(15, 30, 45, 60, 90, 120)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    durationOptions.forEach { mins ->
                        val isSel = durationMinutes == mins
                        Surface(
                            onClick = { durationMinutes = mins },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) DopamineAmber.copy(alpha = 0.2f) else DarkSurface,
                            border = BorderStroke(1.dp, if (isSel) DopamineAmber else DarkBorder),
                            modifier = Modifier.weight(1f).height(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${mins}m",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) DopamineAmber else TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val startDt = selectedDate.atTime(selectedHour, selectedMinute)
                    val endDt = startDt.plusMinutes(durationMinutes.toLong())
                    val updated = schedule.copy(
                        startTime = startDt.format(DateTimeFormatter.ISO_DATE_TIME),
                        endTime = endDt.format(DateTimeFormatter.ISO_DATE_TIME),
                        estimatedMinutes = durationMinutes,
                        status = if (schedule.status == "BACKLOG") "PENDING" else schedule.status,
                        updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = DopamineCyan)
            ) {
                Text("确认标注", color = DarkBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (schedule.startTime.isNotBlank()) {
                    TextButton(
                        onClick = {
                            val updated = schedule.copy(
                                startTime = "",
                                endTime = "",
                                updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                            )
                            onSave(updated)
                        }
                    ) {
                        Text("清除时间", color = DopamineRed)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("取消", color = TextSecondary)
                }
            }
        }
    )
}
