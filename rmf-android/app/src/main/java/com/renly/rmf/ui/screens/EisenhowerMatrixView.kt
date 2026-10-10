package com.renly.rmf.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

/**
 * 🎯 艾森豪威尔四象限法则专有色彩规范 (Eisenhower Standard Color Palette)
 * 绝对解耦于用户全局强调色 (Accent Color)，确保时间管理四象限色彩认知稳定统一：
 * - Q1 (重要且紧急): 危机/截止日/突发 立即执行 🔥 警示赤红
 * - Q2 (重要不紧急): 规划/学习/深度专注 重点投入 ⭐ 核心蔚蓝 (绝不被棕/橙主题色污染)
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

@Composable
fun EisenhowerMatrixView(
    schedules: List<ScheduleEntity>,
    backlogItems: List<ScheduleEntity>,
    availableTags: List<ScheduleTagEntity>,
    tagColorMap: Map<String, Color>,
    onItemClick: (ScheduleEntity) -> Unit,
    onToggleStatus: (ScheduleEntity) -> Unit,
    onMoveQuadrant: (ScheduleEntity, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 合并有效日程与待办池任务进行全局象限分析
    val allActive = remember(schedules, backlogItems) {
        val combined = schedules + backlogItems
        combined.distinctBy { it.id }
    }

    var selectedQuadrantTab by remember { mutableStateOf("ALL") }
    // 视图模式：GRID (2x2 经典宫格) 或 LIST (纵向全览清单)
    var isGridView by remember { mutableStateOf(true) }

    val quadrants = remember {
        listOf(
            QuadrantInfo("Q1", "重要且紧急", "立即处理 · 绝不拖延", QuadrantQ1Color, "🔥", "危机应对"),
            QuadrantInfo("Q2", "重要不紧急", "核心规划 · 打造高价值", QuadrantQ2Color, "⭐", "深度专注"),
            QuadrantInfo("Q3", "不重要但紧急", "琐事代理 · 快速通关", QuadrantQ3Color, "⚡", "授权委派"),
            QuadrantInfo("Q4", "不重要不紧急", "休闲消除 · 减负极简", QuadrantQ4Color, "🍃", "断舍离")
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 76.dp), // 留出 76dp 彻底避免底部 FAB 遮挡 Q4
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
                    val count = allActive.count { (it.eisenhowerQuadrant ?: "Q2") == q.code }
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

        // -------------------------------------------------------------
        // 2. 主体内容呈现区
        // -------------------------------------------------------------
        if (selectedQuadrantTab == "ALL") {
            if (isGridView) {
                // 2x2 经典四象限宫格（上下两行，每行两个卡片）
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
                            items = allActive.filter { (it.eisenhowerQuadrant ?: "Q2") == "Q1" },
                            tagColorMap = tagColorMap,
                            onItemClick = onItemClick,
                            onToggleStatus = {
                                HapticHelper.performSuccess(context)
                                onToggleStatus(it)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        QuadrantBox(
                            info = quadrants[1], // Q2
                            items = allActive.filter { (it.eisenhowerQuadrant ?: "Q2") == "Q2" },
                            tagColorMap = tagColorMap,
                            onItemClick = onItemClick,
                            onToggleStatus = {
                                HapticHelper.performSuccess(context)
                                onToggleStatus(it)
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
                            items = allActive.filter { (it.eisenhowerQuadrant ?: "Q2") == "Q3" },
                            tagColorMap = tagColorMap,
                            onItemClick = onItemClick,
                            onToggleStatus = {
                                HapticHelper.performSuccess(context)
                                onToggleStatus(it)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        QuadrantBox(
                            info = quadrants[3], // Q4
                            items = allActive.filter { (it.eisenhowerQuadrant ?: "Q2") == "Q4" },
                            tagColorMap = tagColorMap,
                            onItemClick = onItemClick,
                            onToggleStatus = {
                                HapticHelper.performSuccess(context)
                                onToggleStatus(it)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                // 清单流水模式：Q1 ~ Q4 纵向折叠展开流
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    quadrants.forEach { q ->
                        val itemsInQ = allActive.filter { (it.eisenhowerQuadrant ?: "Q2") == q.code }
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
            val filtered = allActive.filter { (it.eisenhowerQuadrant ?: "Q2") == currentQ.code }

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

/**
 * 2x2 宫格单个象限卡片
 */
@Composable
private fun QuadrantBox(
    info: QuadrantInfo,
    items: List<ScheduleEntity>,
    tagColorMap: Map<String, Color>,
    onItemClick: (ScheduleEntity) -> Unit,
    onToggleStatus: (ScheduleEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DarkCard,
        border = BorderStroke(1.dp, info.color.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // 象限头部：微彩光渐变底衬与计数指示
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(info.color)
                )
                Spacer(modifier = Modifier.width(5.dp))
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
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
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
                        Surface(
                            onClick = { onItemClick(item) },
                            shape = RoundedCornerShape(8.dp),
                            color = DarkSurface,
                            border = BorderStroke(0.5.dp, if (isDone) DarkBorder.copy(alpha = 0.4f) else DarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp, horizontal = 6.dp),
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
                        val isCurrent = (item.eisenhowerQuadrant ?: "Q2") == q
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
