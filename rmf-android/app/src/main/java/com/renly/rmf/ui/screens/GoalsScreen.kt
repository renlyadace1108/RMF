package com.renly.rmf.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.data.local.entity.GoalEntity
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    goalsFlow: Flow<List<GoalEntity>>,
    onAddGoal: (GoalEntity) -> Unit,
    onUpdateProgress: (GoalEntity, Int) -> Unit,
    onToggleNorthStar: (GoalEntity) -> Unit
) {
    val goals by goalsFlow.collectAsState(initial = emptyList())
    var selectedLevel by remember { mutableStateOf("ALL") }
    var showAddDialog by remember { mutableStateOf(false) }

    val northStarGoal = goals.find { it.isNorthStar == 1 }

    Scaffold(
        containerColor = DarkBg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = DopaminePurple,
                contentColor = DarkBg,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "新增目标", modifier = Modifier.size(24.dp))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp)
        ) {
            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "OKR 目标金字塔",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
            Text(
                text = "北极星引领 · 四级金字塔层层拆解落地",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 1. 北极星战略目标高质感卡片
            if (northStarGoal != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DarkCard,
                    border = BorderStroke(1.5.dp, DopamineAmber.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = DopamineAmber.copy(alpha = 0.2f),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = DopamineAmber, modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "北极星战略愿景",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = DopamineAmber
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "${northStarGoal.progress}%",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = DopamineAmber
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = northStarGoal.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { northStarGoal.progress / 100f },
                            color = DopamineAmber,
                            trackColor = DarkSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // 2. 层级过滤胶囊条
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val levels = listOf(
                    "ALL" to "全部目标",
                    "ANNUAL" to "年度战略",
                    "QUARTERLY" to "季度目标",
                    "MONTHLY" to "月度攻坚",
                    "WEEKLY" to "周度执行"
                )
                items(levels) { (key, label) ->
                    val isSelected = selectedLevel == key
                    Surface(
                        onClick = { selectedLevel = key },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) DopaminePurple.copy(alpha = 0.2f) else DarkSurface,
                        border = BorderStroke(1.dp, if (isSelected) DopaminePurple else DarkBorder),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                            Text(
                                text = label,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) DopaminePurple else TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val filteredGoals = goals.filter {
                if (selectedLevel == "ALL") true else it.level == selectedLevel
            }

            if (filteredGoals.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "暂无匹配目标，点击右下角设定新目标", color = TextMuted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredGoals, key = { it.id }) { goal ->
                        GoalItemCard(
                            goal = goal,
                            onProgressChange = { newP -> onUpdateProgress(goal, newP) },
                            onToggleNorthStar = { onToggleNorthStar(goal) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(60.dp))
                    }
                }
            }
        }

        if (showAddDialog) {
            AddGoalDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { newGoal ->
                    onAddGoal(newGoal)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun GoalItemCard(
    goal: GoalEntity,
    onProgressChange: (Int) -> Unit,
    onToggleNorthStar: () -> Unit
) {
    val levelLabel = when (goal.level) {
        "ANNUAL" -> "年度"
        "QUARTERLY" -> "季度"
        "MONTHLY" -> "月度"
        "WEEKLY" -> "周度"
        else -> goal.level
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DarkCard,
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = DopaminePurple.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = levelLabel,
                        color = DopaminePurple,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "置信度 ${(goal.confidence * 100).toInt()}%",
                    fontSize = 11.5.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.weight(1f))

                IconButton(onClick = onToggleNorthStar, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (goal.isNorthStar == 1) Icons.Default.Star else Icons.Outlined.StarOutline,
                        contentDescription = "北极星目标",
                        tint = if (goal.isNorthStar == 1) DopamineAmber else TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = goal.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 进度控制与进度条
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress = { goal.progress / 100f },
                    color = DopaminePurple,
                    trackColor = DarkSurface,
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "${goal.progress}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = DopaminePurple
                )

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    onClick = { onProgressChange((goal.progress + 10).coerceAtMost(100)) },
                    shape = RoundedCornerShape(6.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Text(
                        text = "+10%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGoalDialog(
    onDismiss: () -> Unit,
    onConfirm: (GoalEntity) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("QUARTERLY") }
    var isNorthStar by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("设定新目标", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("愿景描述") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DopaminePurple,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("层级分类:", fontSize = 12.sp, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ANNUAL" to "年度", "QUARTERLY" to "季度", "MONTHLY" to "月度", "WEEKLY" to "周度").forEach { (k, v) ->
                        FilterChip(
                            selected = level == k,
                            onClick = { level = k },
                            label = { Text(v, fontSize = 11.sp) }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isNorthStar, onCheckedChange = { isNorthStar = it })
                    Text("设为北极星战略目标", color = TextPrimary, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val nowStr = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_DATE_TIME)
                        val entity = GoalEntity(
                            title = title,
                            level = level,
                            progress = 0,
                            isNorthStar = if (isNorthStar) 1 else 0,
                            confidence = 0.9,
                            createdAt = nowStr
                        )
                        onConfirm(entity)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DopaminePurple),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("确定", color = DarkBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        }
    )
}
