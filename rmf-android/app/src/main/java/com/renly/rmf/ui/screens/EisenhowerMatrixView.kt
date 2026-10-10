package com.renly.rmf.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.data.local.entity.ScheduleEntity
import com.renly.rmf.data.local.entity.ScheduleTagEntity
import com.renly.rmf.domain.model.SubTaskItem
import com.renly.rmf.ui.theme.*

/**
 * 🎯 艾森豪威尔四象限看板视图 (Eisenhower Matrix)
 * 经典高效时间管理：
 * - Q1 (重要且紧急): 危机/截止日/突发 立即执行 🔥
 * - Q2 (重要不紧急): 规划/学习/深度专注 重点投入 ⭐
 * - Q3 (不重要但紧急): 琐事/打扰/代理委派 快速处理 ⚡
 * - Q4 (不重要不紧急): 休闲/整理/消除 适当舍弃 🍃
 */
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
    // 合并有效日程与待办池任务进行全局象限分析
    val allActive = remember(schedules, backlogItems) {
        val combined = schedules + backlogItems
        combined.distinctBy { it.id }
    }

    var selectedQuadrantTab by remember { mutableStateOf("ALL") }

    val quadrants = listOf(
        QuadrantInfo("Q1", "重要且紧急", "立即处理 · 绝不能拖延", DopamineRed, "🔥"),
        QuadrantInfo("Q2", "重要不紧急", "核心规划 · 打造高价值", DopamineBlue, "⭐"),
        QuadrantInfo("Q3", "不重要但紧急", "琐事代理 · 快速通关", DopamineAmber, "⚡"),
        QuadrantInfo("Q4", "不重要不紧急", "休闲消除 · 减负极简", DopamineGreen, "🍃")
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 顶部象限快速过滤标签栏
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = DarkSurface,
            border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    onClick = { selectedQuadrantTab = "ALL" },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedQuadrantTab == "ALL") DopaminePurple.copy(alpha = 0.2f) else Color.Transparent,
                    border = BorderStroke(1.dp, if (selectedQuadrantTab == "ALL") DopaminePurple else Color.Transparent),
                    modifier = Modifier.weight(1f).height(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "四象限全貌 (${allActive.size})",
                            fontSize = 11.5.sp,
                            fontWeight = if (selectedQuadrantTab == "ALL") FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedQuadrantTab == "ALL") DopaminePurple else TextSecondary
                        )
                    }
                }

                quadrants.forEach { q ->
                    val count = allActive.count { (it.eisenhowerQuadrant ?: "Q2") == q.code }
                    val isSel = selectedQuadrantTab == q.code
                    Surface(
                        onClick = { selectedQuadrantTab = q.code },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSel) q.color.copy(alpha = 0.2f) else Color.Transparent,
                        border = BorderStroke(1.dp, if (isSel) q.color else Color.Transparent),
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${q.emoji}${q.code} ($count)",
                                fontSize = 11.5.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) q.color else TextSecondary
                            )
                        }
                    }
                }
            }
        }

        if (selectedQuadrantTab == "ALL") {
            // 2x2 经典四象限布局
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuadrantBox(
                        info = quadrants[0], // Q1
                        items = allActive.filter { (it.eisenhowerQuadrant ?: "Q2") == "Q1" },
                        tagColorMap = tagColorMap,
                        onItemClick = onItemClick,
                        onToggleStatus = onToggleStatus,
                        modifier = Modifier.weight(1f)
                    )
                    QuadrantBox(
                        info = quadrants[1], // Q2
                        items = allActive.filter { (it.eisenhowerQuadrant ?: "Q2") == "Q2" },
                        tagColorMap = tagColorMap,
                        onItemClick = onItemClick,
                        onToggleStatus = onToggleStatus,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuadrantBox(
                        info = quadrants[2], // Q3
                        items = allActive.filter { (it.eisenhowerQuadrant ?: "Q2") == "Q3" },
                        tagColorMap = tagColorMap,
                        onItemClick = onItemClick,
                        onToggleStatus = onToggleStatus,
                        modifier = Modifier.weight(1f)
                    )
                    QuadrantBox(
                        info = quadrants[3], // Q4
                        items = allActive.filter { (it.eisenhowerQuadrant ?: "Q2") == "Q4" },
                        tagColorMap = tagColorMap,
                        onItemClick = onItemClick,
                        onToggleStatus = onToggleStatus,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            // 单一象限垂直大清单流
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
                        Text(currentQ.emoji, fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${currentQ.code} ${currentQ.name}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = currentQ.color
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = currentQ.subtitle,
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }

                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "该象限暂无任务，可在编辑中调整象限归属",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
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
                                    onToggleStatus = { onToggleStatus(item) },
                                    onChangeQuadrant = { targetQ -> onMoveQuadrant(item, targetQ) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class QuadrantInfo(
    val code: String,
    val name: String,
    val subtitle: String,
    val color: Color,
    val emoji: String
)

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
            // 象限头部
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
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${info.code} ${info.name}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = info.color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = info.color.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${items.size}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = info.color,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("无任务", fontSize = 11.sp, color = TextMuted)
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
                            shape = RoundedCornerShape(7.dp),
                            color = DarkSurface,
                            border = BorderStroke(0.5.dp, DarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(6.dp),
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

                                    // 子步骤进度指示
                                    val subtasks = remember(item.subtasksJson) { SubTaskItem.parseList(item.subtasksJson) }
                                    if (subtasks.isNotEmpty()) {
                                        val doneCount = subtasks.count { it.isDone }
                                        Text(
                                            text = "步骤 $doneCount/${subtasks.size}",
                                            fontSize = 9.sp,
                                            color = DopamineCyan
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
        border = BorderStroke(1.dp, if (isDone) DarkBorder else quadrantColor.copy(alpha = 0.3f)),
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
                        color = if (isDone) TextMuted else TextPrimary
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
                            val tagColor = tagColorMap[item.category] ?: DopamineBlue
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

                // 快速切换象限微型选钮
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf("Q1", "Q2", "Q3", "Q4").forEach { q ->
                        val isCurrent = (item.eisenhowerQuadrant ?: "Q2") == q
                        Surface(
                            onClick = { onChangeQuadrant(q) },
                            shape = RoundedCornerShape(4.dp),
                            color = if (isCurrent) quadrantColor.copy(alpha = 0.25f) else DarkBg,
                            border = BorderStroke(0.5.dp, if (isCurrent) quadrantColor else DarkBorder),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = q,
                                    fontSize = 9.5.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) quadrantColor else TextMuted
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
