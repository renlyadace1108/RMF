package com.renly.rmf.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.data.local.entity.StudyTopicEntity
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    topicsFlow: Flow<List<StudyTopicEntity>>,
    onAddTopic: (StudyTopicEntity) -> Unit,
    onUpdateProgress: (StudyTopicEntity, Double) -> Unit
) {
    val topics by topicsFlow.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTopicForProgress by remember { mutableStateOf<StudyTopicEntity?>(null) }

    Scaffold(
        containerColor = DarkBg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = DopamineGreen,
                contentColor = DarkBg,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "新增学习专题", modifier = Modifier.size(24.dp))
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
                text = "学习与知识专题",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
            Text(
                text = "艾宾浩斯间隔复习 · 知识沉淀与技能树",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            val reviewService = remember { com.renly.rmf.domain.service.SpacedRepetitionService() }
            val reviewRecommendations = remember(topics) {
                reviewService.getReviewRecommendations(topics)
            }

            if (reviewRecommendations.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DarkCard,
                    border = BorderStroke(1.dp, DopamineAmber.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🧠", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "艾宾浩斯复盘建议 (${reviewRecommendations.size} 项到达记忆拐点)",
                                fontWeight = FontWeight.Bold,
                                color = DopamineAmber,
                                fontSize = 12.5.sp
                            )
                        }
                        Text(
                            text = "建议今日优先巩固: " + reviewRecommendations.take(3).joinToString("、") { it.title },
                            fontSize = 12.sp,
                            color = TextPrimary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            if (topics.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "暂无学习专题，点击右下角开启新知识技能树", color = TextMuted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(topics, key = { it.id }) { topic ->
                        StudyTopicCard(
                            topic = topic,
                            onClick = { selectedTopicForProgress = topic }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(60.dp))
                    }
                }
            }
        }

        if (showAddDialog) {
            AddStudyTopicDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { newTopic ->
                    onAddTopic(newTopic)
                    showAddDialog = false
                }
            )
        }

        selectedTopicForProgress?.let { topic ->
            UpdateStudyProgressDialog(
                topic = topic,
                onDismiss = { selectedTopicForProgress = null },
                onConfirm = { addedVal ->
                    onUpdateProgress(topic, addedVal)
                    selectedTopicForProgress = null
                }
            )
        }
    }
}

@Composable
fun StudyTopicCard(
    topic: StudyTopicEntity,
    onClick: () -> Unit
) {
    val progress = (topic.completedValue / topic.targetValue.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
    val unitLabel = when (topic.progressType) {
        "HOURS" -> "小时"
        "LESSONS" -> "节"
        "PAGES" -> "页"
        else -> topic.customUnit.ifBlank { "单位" }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = DarkCard,
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = topic.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    color = DopamineGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = topic.category,
                        color = DopamineGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 进度条
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = DopamineGreen,
                trackColor = DarkSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "已推进: ${"%.1f".format(topic.completedValue)} / ${"%.1f".format(topic.targetValue)} $unitLabel",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${topic.progressPercent}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = DopamineGreen
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStudyTopicDialog(
    onDismiss: () -> Unit,
    onConfirm: (StudyTopicEntity) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("技术栈") }
    var targetValue by remember { mutableStateOf("20") }
    var progressType by remember { mutableStateOf("HOURS") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("开启新攻坚专题", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("知识专题名称 (如: 深入理解计算机系统)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DopamineGreen,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("分类领域 (如: 计算机基础 / 外语)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DopamineGreen,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("HOURS" to "学时(h)", "LESSONS" to "课时(节)", "PAGES" to "页码(P)").forEach { (k, v) ->
                        FilterChip(
                            selected = progressType == k,
                            onClick = { progressType = k },
                            label = { Text(v, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = targetValue,
                    onValueChange = { targetValue = it },
                    label = { Text("总目标量") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val nowStr = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_DATE_TIME)
                        val entity = StudyTopicEntity(
                            title = title,
                            category = category,
                            targetValue = targetValue.toDoubleOrNull() ?: 10.0,
                            progressType = progressType,
                            createdAt = nowStr,
                            updatedAt = nowStr
                        )
                        onConfirm(entity)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DopamineGreen),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("立项启动", color = DarkBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        }
    )
}

@Composable
fun UpdateStudyProgressDialog(
    topic: StudyTopicEntity,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var addAmount by remember { mutableStateOf("1.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("打卡学习进度: ${topic.title}", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("本次攻坚增量:", fontSize = 13.sp, color = TextSecondary)
                OutlinedTextField(
                    value = addAmount,
                    onValueChange = { addAmount = it },
                    label = { Text("增量数值") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DopamineGreen,
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
                onClick = {
                    val addVal = addAmount.toDoubleOrNull() ?: 0.0
                    if (addVal > 0) {
                        onConfirm(addVal)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DopamineGreen),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("打卡打底", color = DarkBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        }
    )
}
