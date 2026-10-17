package com.renly.rmf.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.data.local.entity.CourseEntity
import com.renly.rmf.data.local.entity.StudyTaskPoint
import com.renly.rmf.data.local.entity.StudyTopicEntity
import com.renly.rmf.data.local.entity.getTaskPoints
import com.renly.rmf.data.local.entity.toJson
import com.renly.rmf.ui.theme.*
import com.renly.rmf.util.HapticHelper
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    topicsFlow: Flow<List<StudyTopicEntity>>,
    coursesFlow: Flow<List<CourseEntity>> = kotlinx.coroutines.flow.flowOf(emptyList()),
    onAddTopic: (StudyTopicEntity) -> Unit,
    onUpdateTopic: (StudyTopicEntity) -> Unit = {},
    onDeleteTopic: (String) -> Unit = {},
    onUpdateProgress: (StudyTopicEntity, Double) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val topics by topicsFlow.collectAsState(initial = emptyList())
    val courses by coursesFlow.collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTopicForProgress by remember { mutableStateOf<StudyTopicEntity?>(null) }
    var selectedTopicForDetail by remember { mutableStateOf<StudyTopicEntity?>(null) }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }

    val categories = remember(topics) {
        listOf("ALL" to "全部") + topics.map { it.category }.distinct().filter { it.isNotBlank() }.map { it to it }
    }

    val filteredTopics = remember(topics, selectedCategoryFilter) {
        if (selectedCategoryFilter == "ALL") topics else topics.filter { it.category == selectedCategoryFilter }
    }

    Scaffold(
        containerColor = DarkBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    HapticHelper.performLightClick(context)
                    showAddDialog = true
                },
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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // 顶部标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "学习与知识专题",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "任务点精准推进 · 知识沉淀与技能树",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // 快捷导入课表图标按钮
                if (courses.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = {
                            HapticHelper.performLightClick(context)
                            showAddDialog = true
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = DopamineCyan.copy(alpha = 0.15f),
                            contentColor = DopamineCyan
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("导入课程", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 艾宾浩斯复盘建议条
            val reviewService = remember { com.renly.rmf.domain.service.SpacedRepetitionService() }
            val reviewRecommendations = remember(topics) {
                reviewService.getReviewRecommendations(topics)
            }

            if (reviewRecommendations.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = DarkCard,
                    border = BorderStroke(1.dp, DopamineAmber.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🧠", fontSize = 15.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "艾宾浩斯复习提醒 (${reviewRecommendations.size} 项到达拐点)",
                                fontWeight = FontWeight.Bold,
                                color = DopamineAmber,
                                fontSize = 12.5.sp
                            )
                        }
                        Text(
                            text = "建议今日巩固: " + reviewRecommendations.take(3).joinToString("、") { it.title },
                            fontSize = 12.sp,
                            color = TextPrimary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // 分类过滤器标签
            if (categories.size > 2) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    items(categories, key = { it.first }) { (key, label) ->
                        val isSelected = selectedCategoryFilter == key
                        Surface(
                            onClick = {
                                selectedCategoryFilter = key
                                HapticHelper.performLightClick(context)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) DopamineGreen.copy(alpha = 0.2f) else DarkSurface,
                            border = BorderStroke(1.dp, if (isSelected) DopamineGreen else DarkBorder),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) DopamineGreen else TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            if (filteredTopics.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "📚", fontSize = 38.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "暂无学习攻坚专题", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "可点击右下角开启新专题，或直接一键导入课表课程",
                            color = TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filteredTopics, key = { it.id }) { topic ->
                        StudyTopicCard(
                            topic = topic,
                            onCheckInClick = { selectedTopicForProgress = topic },
                            onDetailClick = { selectedTopicForDetail = topic }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }

        // 弹窗 1: 开启/导入新学习专题弹窗 (支持从课表导入 + 预设所有任务点名称)
        if (showAddDialog) {
            AddStudyTopicDialog(
                courses = courses,
                onDismiss = { showAddDialog = false },
                onConfirm = { newTopic ->
                    onAddTopic(newTopic)
                    Toast.makeText(context, "✓ 学习专题「${newTopic.title}」已建立", Toast.LENGTH_SHORT).show()
                    showAddDialog = false
                }
            )
        }

        // 弹窗 2: 打卡推进弹窗 (已导入显示任务点清单，未导入显示任务点名称/章节备注输入)
        selectedTopicForProgress?.let { topic ->
            UpdateStudyProgressDialog(
                topic = topic,
                onDismiss = { selectedTopicForProgress = null },
                onConfirm = { updatedTopic ->
                    onUpdateTopic(updatedTopic)
                    selectedTopicForProgress = null
                    HapticHelper.performSuccess(context)
                    Toast.makeText(context, "🎉 打卡成功！已记录任务点进展", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // 弹窗 3: 专题全量任务点清单与管理弹窗
        selectedTopicForDetail?.let { topic ->
            StudyTopicDetailDialog(
                topic = topic,
                onDismiss = { selectedTopicForDetail = null },
                onUpdateTopic = { updated ->
                    onUpdateTopic(updated)
                    selectedTopicForDetail = updated
                },
                onDelete = {
                    onDeleteTopic(topic.id)
                    selectedTopicForDetail = null
                    Toast.makeText(context, "已删除专题", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

/**
 * 学习专题卡片
 */
@Composable
fun StudyTopicCard(
    topic: StudyTopicEntity,
    onCheckInClick: () -> Unit,
    onDetailClick: () -> Unit
) {
    val progress = (topic.completedValue / topic.targetValue.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
    val unitLabel = when (topic.progressType) {
        "HOURS" -> "小时"
        "LESSONS" -> topic.customUnit.ifBlank { "讲" }
        "PAGES" -> "页"
        else -> topic.customUnit.ifBlank { "讲" }
    }

    val taskPoints = remember(topic.checkpointsJson) { topic.getTaskPoints() }
    val completedPointsCount = remember(taskPoints) { taskPoints.count { it.isCompleted } }
    val nextPendingPoint = remember(taskPoints) { taskPoints.firstOrNull { !it.isCompleted } }

    Surface(
        onClick = onCheckInClick,
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
            // 顶部标题与分类
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = topic.title,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
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

            // 任务点透视与最近打卡状态
            if (taskPoints.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DarkSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎯", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (nextPendingPoint != null) {
                                "任务点 ($completedPointsCount/${taskPoints.size}) · 下一站: ${nextPendingPoint.name}"
                            } else {
                                "全量 ${taskPoints.size} 个任务点已全部攻坚完毕！🎉"
                            },
                            fontSize = 11.5.sp,
                            color = if (nextPendingPoint != null) DopamineCyan else DopamineGreen,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            } else if (topic.currentCheckpoint.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DarkSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📌", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "最近打卡: ${topic.currentCheckpoint}",
                            fontSize = 11.5.sp,
                            color = DopamineCyan,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

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

            // 底部推进量与操作按钮
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "已推进: ${"%.1f".format(topic.completedValue)} / ${"%.1f".format(topic.targetValue)} $unitLabel",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${topic.progressPercent}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = DopamineGreen
                )

                Spacer(modifier = Modifier.weight(1f))

                // 查看详情/管理清单
                TextButton(
                    onClick = onDetailClick,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("清单/管理", fontSize = 11.5.sp, color = TextSecondary)
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 打卡按钮
                Button(
                    onClick = onCheckInClick,
                    colors = ButtonDefaults.buttonColors(containerColor = DopamineGreen),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("打卡", color = DarkBg, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * 开启/导入新学习专题弹窗 (支持从课表导入 + 预设所有任务点名称)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStudyTopicDialog(
    courses: List<CourseEntity>,
    onDismiss: () -> Unit,
    onConfirm: (StudyTopicEntity) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(if (courses.isNotEmpty()) 0 else 1) }

    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("技术栈") }
    var targetValueText by remember { mutableStateOf("16") }
    var progressType by remember { mutableStateOf("LESSONS") }
    var customUnit by remember { mutableStateOf("讲") }

    // 预设任务点列表 (每个任务点的单独名称)
    var taskPoints by remember { mutableStateOf<List<StudyTaskPoint>>(emptyList()) }

    var newTaskPointName by remember { mutableStateOf("") }
    var showBatchPasteDialog by remember { mutableStateOf(false) }
    var batchPasteText by remember { mutableStateOf("") }
    var generateCountText by remember { mutableStateOf("16") }

    // 监听任务点数量变动，自动同步总目标数
    LaunchedEffect(taskPoints.size) {
        if (taskPoints.isNotEmpty()) {
            targetValueText = taskPoints.size.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Column {
                Text("开启新学习专题", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("支持从课表快速导入并预设所有任务点/章节名称", color = TextSecondary, fontSize = 11.5.sp, modifier = Modifier.padding(top = 2.dp))
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 顶部来源切换 (从课表导入 / 自定义立项)
                if (courses.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurface)
                            .padding(2.dp)
                    ) {
                        Surface(
                            onClick = { selectedTab = 0 },
                            shape = RoundedCornerShape(6.dp),
                            color = if (selectedTab == 0) DopamineGreen else Color.Transparent,
                            modifier = Modifier.weight(1f).height(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "🎓 从课表导入",
                                    color = if (selectedTab == 0) DarkBg else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Surface(
                            onClick = { selectedTab = 1 },
                            shape = RoundedCornerShape(6.dp),
                            color = if (selectedTab == 1) DopamineGreen else Color.Transparent,
                            modifier = Modifier.weight(1f).height(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "✏️ 自定义立项",
                                    color = if (selectedTab == 1) DarkBg else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 模式 1: 从课表导入课程
                if (selectedTab == 0 && courses.isNotEmpty()) {
                    Text("选择目标学期课程:", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(courses, key = { it.id }) { course ->
                            val isSelected = title == course.name
                            Surface(
                                onClick = {
                                    title = course.name
                                    category = "学科课程"
                                    customUnit = "讲"
                                    progressType = "LESSONS"
                                    val estimatedWeeks = (course.endWeek - course.startWeek + 1).coerceAtLeast(1)
                                    // 预填充默认讲数
                                    if (taskPoints.isEmpty()) {
                                        taskPoints = (1..estimatedWeeks).map { idx ->
                                            StudyTaskPoint(name = "第${idx}讲 ${course.name}核心知识点")
                                        }
                                        targetValueText = estimatedWeeks.toString()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) DopamineCyan.copy(alpha = 0.2f) else DarkSurface,
                                border = BorderStroke(1.dp, if (isSelected) DopamineCyan else DarkBorder),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    Text(course.name, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) DopamineCyan else TextPrimary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("${course.startWeek}-${course.endWeek}周", fontSize = 10.sp, color = TextMuted)
                                }
                            }
                        }
                    }
                }

                // 专题名称
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("知识专题名称 (如: 深入理解计算机系统)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // 领域分类
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("领域分类 (如: 计算机基础 / 学科课程 / 外语)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // 进度度量类型
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("LESSONS" to "课时/讲", "HOURS" to "学时(h)", "PAGES" to "页码(P)").forEach { (k, v) ->
                        FilterChip(
                            selected = progressType == k,
                            onClick = { progressType = k },
                            label = { Text(v, fontSize = 11.sp) }
                        )
                    }
                }

                // 核心区域：预先指定所有任务点的单独名称
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🎯 预设各任务点/章节名称 (${taskPoints.size})",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = { showBatchPasteDialog = !showBatchPasteDialog },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(12.dp), tint = DopamineCyan)
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("批量粘贴", fontSize = 11.sp, color = DopamineCyan)
                                }
                            }
                        }

                        // 批量粘贴大纲展开区
                        AnimatedVisibility(visible = showBatchPasteDialog) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Text(
                                    "每行粘贴一个章节或任务点名称：",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                                OutlinedTextField(
                                    value = batchPasteText,
                                    onValueChange = { batchPasteText = it },
                                    placeholder = { Text("第1章 计算机系统概述\n第2章 数据的机器级表示\n第3章 程序的机器级表示...", fontSize = 11.5.sp) },
                                    maxLines = 5,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .padding(vertical = 4.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        onClick = {
                                            val lines = batchPasteText.split("\n")
                                                .map { it.trim() }
                                                .filter { it.isNotBlank() }
                                            if (lines.isNotEmpty()) {
                                                val added = lines.map { StudyTaskPoint(name = it) }
                                                taskPoints = taskPoints + added
                                                batchPasteText = ""
                                                showBatchPasteDialog = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = DopamineCyan),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("解析并填入任务点", color = DarkBg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // 快速生成 1~N 讲模板行
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("快速生成:", fontSize = 11.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.width(6.dp))
                            listOf(8, 12, 16, 24).forEach { count ->
                                Surface(
                                    onClick = {
                                        taskPoints = (1..count).map { idx ->
                                            val prefix = if (title.isNotBlank()) title else "课程"
                                            StudyTaskPoint(name = "第${idx}讲 $prefix 重点知识")
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = DarkCard,
                                    border = BorderStroke(1.dp, DarkBorder),
                                    modifier = Modifier
                                        .height(24.dp)
                                        .padding(horizontal = 2.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                                        Text("${count}讲", fontSize = 10.5.sp, color = TextPrimary)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 手动添加单个任务点
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
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
                                    if (newTaskPointName.isEmpty()) {
                                        Text(
                                            "输入单独任务点名称 (如: 第1章 绪论)",
                                            fontSize = 11.5.sp,
                                            color = TextMuted,
                                            maxLines = 1
                                        )
                                    }
                                    BasicTextField(
                                        value = newTaskPointName,
                                        onValueChange = { newTaskPointName = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            fontSize = 12.sp,
                                            color = TextPrimary
                                        ),
                                        cursorBrush = SolidColor(DopamineGreen),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                onClick = {
                                    if (newTaskPointName.isNotBlank()) {
                                        taskPoints = taskPoints + StudyTaskPoint(name = newTaskPointName.trim())
                                        newTaskPointName = ""
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = DopamineGreen,
                                modifier = Modifier.height(40.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "添加", tint = DarkBg, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("添加", color = DarkBg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // 任务点条目列表 (带序号与删除按钮)
                        if (taskPoints.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                taskPoints.forEachIndexed { index, point ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = DarkCard,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "%02d".format(index + 1),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = DopamineGreen
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = point.name,
                                                fontSize = 12.sp,
                                                color = TextPrimary,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            IconButton(
                                                onClick = {
                                                    taskPoints = taskPoints.toMutableList().also { it.removeAt(index) }
                                                },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "删除", tint = TextMuted, modifier = Modifier.size(12.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 总目标量
                OutlinedTextField(
                    value = targetValueText,
                    onValueChange = { targetValueText = it },
                    label = { Text("总攻坚目标量 (${if (taskPoints.isNotEmpty()) "已自动同步为任务点数量" else "可手动输入"})") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                        val entity = StudyTopicEntity(
                            title = title.trim(),
                            category = category.trim().ifBlank { "技术栈" },
                            targetValue = targetValueText.toDoubleOrNull() ?: if (taskPoints.isNotEmpty()) taskPoints.size.toDouble() else 10.0,
                            progressType = progressType,
                            customUnit = customUnit,
                            currentCheckpoint = if (taskPoints.isNotEmpty()) "待开启: ${taskPoints.first().name}" else "",
                            checkpointsJson = taskPoints.toJson(),
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

/**
 * 打卡推进弹窗
 * - 每次打卡均包含明确的章节/任务点备注输入框（无论是预设课表、自定义专题还是持续多次打卡）；
 * - 若存在未打卡的预设章节大纲，支持一键点击填入章节名称并绑定完成；
 * - 若无预设章节或已全部打卡，支持持续输入本次攻坚的章节名（如 unit1(2)），打卡后自动沉淀到任务点履历；
 * - 数字框采用 BasicTextField + 自定义居中容器，杜绝截断，整型默认格式化为整数。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateStudyProgressDialog(
    topic: StudyTopicEntity,
    onDismiss: () -> Unit,
    onConfirm: (StudyTopicEntity) -> Unit
) {
    val existingPoints = remember(topic.checkpointsJson) { topic.getTaskPoints().toMutableList() }
    val pendingPoints = remember(existingPoints) { existingPoints.filter { !it.isCompleted } }
    val completedPoints = remember(existingPoints) { existingPoints.filter { it.isCompleted } }

    val isHourType = topic.progressType == "HOURS"
    val unitLabel = when (topic.progressType) {
        "HOURS" -> "小时"
        "LESSONS" -> topic.customUnit.ifBlank { "讲" }
        "PAGES" -> "页"
        else -> topic.customUnit.ifBlank { "讲" }
    }

    // 默认选中的待打卡预设章节 ID (若有)
    var selectedPointId by remember {
        mutableStateOf(pendingPoints.firstOrNull()?.id)
    }

    // 本次打卡章节备注：若有待完成的预设章节，默认填入该章节名；否则为空让用户自由填写 (或从快速词条填入)
    var customCheckpointNote by remember {
        mutableStateOf(pendingPoints.firstOrNull()?.name ?: "")
    }

    var showCompletedHistory by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Column {
                Text("打卡学习进度", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(topic.title, color = DopamineGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. 最近完成状态标识
                if (topic.currentCheckpoint.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DopamineCyan.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, DopamineCyan.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📌", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "上次打卡: ${topic.currentCheckpoint}",
                                fontSize = 11.5.sp,
                                color = DopamineCyan,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // 2. 待完成预设章节列表（如果有）
                if (pendingPoints.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "待打卡章节大纲 (点击快速填入):",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(pendingPoints, key = { it.id }) { point ->
                                val isSelected = selectedPointId == point.id
                                Surface(
                                    onClick = {
                                        selectedPointId = point.id
                                        customCheckpointNote = point.name
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) DopamineGreen.copy(alpha = 0.2f) else DarkSurface,
                                    border = BorderStroke(1.dp, if (isSelected) DopamineGreen else DarkBorder),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 10.dp)
                                    ) {
                                        Text(
                                            text = point.name,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) DopamineGreen else TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. 核心：每次打卡都包含的章节备注输入框
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = customCheckpointNote,
                        onValueChange = {
                            customCheckpointNote = it
                            // 若用户修改了内容且不等于选中的点，则取消预设点绑定作为自定义点提交
                            if (selectedPointId != null && pendingPoints.none { p -> p.id == selectedPointId && p.name == it }) {
                                selectedPointId = null
                            }
                        },
                        label = { Text("本次打卡章节 / 任务点备注 *") },
                        placeholder = { Text("如: unit1 (2) 或 完成了第3章课后习题", fontSize = 12.sp) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 常用快速词条
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("第1章", "第2章", "第3章", "第4章", "第5章", "期中复习", "课后习题", "实验报告").forEach { tag ->
                            Surface(
                                onClick = {
                                    customCheckpointNote = if (customCheckpointNote.isBlank()) tag else "$customCheckpointNote $tag"
                                    selectedPointId = null
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = DarkSurface,
                                border = BorderStroke(1.dp, DarkBorder),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                    Text("+ $tag", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                }

                // 4. 一次打卡只允许推进一个任务点
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "本次推进:",
                        fontSize = 12.5.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DopamineGreen.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, DopamineGreen.copy(alpha = 0.35f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("🎯", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "+1 $unitLabel (推进1个任务点)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = DopamineGreen
                            )
                        }
                    }
                }

                // 5. 已完成章节历史 (折叠展示，清晰不冗余)
                if (completedPoints.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DarkSurface.copy(alpha = 0.5f),
                        onClick = { showCompletedHistory = !showCompletedHistory },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "已完成章节 (${completedPoints.size} 个任务点)",
                                fontSize = 11.5.sp,
                                color = TextMuted
                            )
                            Icon(
                                imageVector = if (showCompletedHistory) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (showCompletedHistory) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 130.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            completedPoints.forEachIndexed { idx, pt ->
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("✓", fontSize = 11.sp, color = DopamineGreen, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "%02d. %s".format(idx + 1, pt.name),
                                        fontSize = 11.5.sp,
                                        color = TextMuted,
                                        textDecoration = TextDecoration.LineThrough,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (pt.completedAt != null) {
                                        Text(
                                            text = pt.completedAt.take(16).replace("T", " "),
                                            fontSize = 10.sp,
                                            color = TextMuted
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
                onClick = {
                    val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                    val noteTrimmed = customCheckpointNote.trim()
                    val finalPointName = if (noteTrimmed.isNotBlank()) noteTrimmed else "完成阶段任务点"

                    val targetPending = existingPoints.firstOrNull { it.id == selectedPointId && !it.isCompleted }
                        ?: existingPoints.firstOrNull { it.name == noteTrimmed && !it.isCompleted }
                        ?: pendingPoints.firstOrNull()

                    val updatedPoints = if (targetPending != null) {
                        // 勾选完成了预设章节
                        existingPoints.map { p ->
                            if (p.id == targetPending.id) {
                                p.copy(name = finalPointName, isCompleted = true, completedAt = nowStr)
                            } else p
                        }
                    } else {
                        // 记录新完成的章节并沉淀入任务点履历
                        existingPoints + StudyTaskPoint(
                            name = finalPointName,
                            isCompleted = true,
                            completedAt = nowStr
                        )
                    }

                    val newCompleted = topic.completedValue + 1.0
                    val newTarget = maxOf(topic.targetValue, updatedPoints.size.toDouble(), newCompleted)
                    val newPercent = ((newCompleted / newTarget.coerceAtLeast(1.0)) * 100).toInt().coerceIn(0, 100)

                    val updatedTopic = topic.copy(
                        completedValue = newCompleted,
                        targetValue = newTarget,
                        progressPercent = newPercent,
                        currentCheckpoint = finalPointName,
                        checkpointsJson = updatedPoints.toJson(),
                        updatedAt = nowStr
                    )
                    onConfirm(updatedTopic)
                },
                colors = ButtonDefaults.buttonColors(containerColor = DopamineGreen),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("打卡确认", color = DarkBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        }
    )
}

/**
 * 专题全量任务点清单与管理弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyTopicDetailDialog(
    topic: StudyTopicEntity,
    onDismiss: () -> Unit,
    onUpdateTopic: (StudyTopicEntity) -> Unit,
    onDelete: () -> Unit
) {
    var taskPoints by remember { mutableStateOf(topic.getTaskPoints()) }
    var newPointName by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(topic.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("攻坚清单与任务点全景", color = TextSecondary, fontSize = 11.5.sp)
                }
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "删除专题", tint = DopamineRed, modifier = Modifier.size(18.dp))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 进度概览条
                val completedCount = taskPoints.count { it.isCompleted }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("任务点完成度: $completedCount / ${taskPoints.size}", fontSize = 12.sp, color = TextSecondary)
                            Text("${topic.progressPercent}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DopamineGreen)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (topic.completedValue / topic.targetValue.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = DopamineGreen,
                            trackColor = DarkCard
                        )
                    }
                }

                Text("任务点章节清单:", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)

                if (taskPoints.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无任务点，可在下方直接添加章节", color = TextMuted, fontSize = 12.sp)
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        taskPoints.forEachIndexed { idx, point ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (point.isCompleted) DarkSurface.copy(alpha = 0.5f) else DarkSurface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = point.isCompleted,
                                        onCheckedChange = { isChecked ->
                                            val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                                            val updatedList = taskPoints.map { p ->
                                                if (p.id == point.id) p.copy(isCompleted = isChecked, completedAt = if (isChecked) nowStr else null) else p
                                            }
                                            taskPoints = updatedList
                                            val newCount = updatedList.count { it.isCompleted }
                                            val newPercent = ((newCount.toDouble() / topic.targetValue.coerceAtLeast(1.0)) * 100).toInt().coerceIn(0, 100)
                                            val updatedTopic = topic.copy(
                                                completedValue = newCount.toDouble(),
                                                progressPercent = newPercent,
                                                currentCheckpoint = if (isChecked) point.name else topic.currentCheckpoint,
                                                checkpointsJson = updatedList.toJson(),
                                                updatedAt = nowStr
                                            )
                                            onUpdateTopic(updatedTopic)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "%02d. %s".format(idx + 1, point.name),
                                        fontSize = 12.sp,
                                        color = if (point.isCompleted) TextMuted else TextPrimary,
                                        textDecoration = if (point.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            val updatedList = taskPoints.toMutableList().also { it.removeAt(idx) }
                                            taskPoints = updatedList
                                            val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                                            val updatedTopic = topic.copy(
                                                checkpointsJson = updatedList.toJson(),
                                                updatedAt = nowStr
                                            )
                                            onUpdateTopic(updatedTopic)
                                        },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "删除", tint = TextMuted, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // 追加新任务点
                Row(
                    verticalAlignment = Alignment.CenterVertically,
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
                            if (newPointName.isEmpty()) {
                                Text(
                                    "追加新任务点/章节名称",
                                    fontSize = 11.5.sp,
                                    color = TextMuted,
                                    maxLines = 1
                                )
                            }
                            BasicTextField(
                                value = newPointName,
                                onValueChange = { newPointName = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                ),
                                cursorBrush = SolidColor(DopamineGreen),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        onClick = {
                            if (newPointName.isNotBlank()) {
                                val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                                val updatedList = taskPoints + StudyTaskPoint(name = newPointName.trim())
                                taskPoints = updatedList
                                val updatedTopic = topic.copy(
                                    checkpointsJson = updatedList.toJson(),
                                    targetValue = maxOf(topic.targetValue, updatedList.size.toDouble()),
                                    updatedAt = nowStr
                                )
                                onUpdateTopic(updatedTopic)
                                newPointName = ""
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = DopamineGreen,
                        modifier = Modifier.height(40.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "追加", tint = DarkBg, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("追加", color = DarkBg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成", color = DopamineGreen, fontWeight = FontWeight.Bold) }
        }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = DarkCard,
            title = { Text("确认删除专题", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("确定要删除「${topic.title}」及其全部任务点吗？此操作不可逆。", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DopamineRed)
                ) {
                    Text("确认删除", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消", color = TextSecondary) }
            }
        )
    }
}
