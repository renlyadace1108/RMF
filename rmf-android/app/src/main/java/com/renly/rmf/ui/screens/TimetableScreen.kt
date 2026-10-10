package com.renly.rmf.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.data.local.entity.CourseAdjustmentEntity
import com.renly.rmf.data.local.entity.CourseEntity
import com.renly.rmf.domain.service.TimetablePreferences
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    coursesFlow: Flow<List<CourseEntity>>,
    adjustmentsFlow: Flow<List<CourseAdjustmentEntity>> = kotlinx.coroutines.flow.flowOf(emptyList()),
    onAddCourse: (CourseEntity) -> Unit,
    onDeleteCourse: (String) -> Unit = {},
    onAddAdjustment: (CourseAdjustmentEntity) -> Unit = {},
    onDeleteAdjustment: (String) -> Unit = {},
    onProjectToToday: () -> Unit = {},
    onImportCourses: (List<CourseEntity>) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val courses by coursesFlow.collectAsState(initial = emptyList())
    val adjustments by adjustmentsFlow.collectAsState(initial = emptyList())

    var semesterStartDate by remember { mutableStateOf(TimetablePreferences.getSemesterStartDate(context)) }
    var totalWeeks by remember { mutableIntStateOf(TimetablePreferences.getTotalWeeks(context)) }

    // AI 课表截图自动提取状态
    var isAnalyzingImage by remember { mutableStateOf(false) }
    var analysisStatusText by remember { mutableStateOf("") }
    var recognizedCourses by remember { mutableStateOf<List<CourseEntity>>(emptyList()) }
    var showImportPreviewDialog by remember { mutableStateOf(false) }
    var importErrorText by remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isAnalyzingImage = true
                analysisStatusText = "正在预处理课表图片..."
                val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.renly.rmf.domain.service.AiService.compressImageFromUri(context, uri)
                }
                if (bytes == null) {
                    isAnalyzingImage = false
                    importErrorText = "未能成功读取图片文件，请重试"
                    return@launch
                }
                analysisStatusText = "AI 视觉大模型正在解析排课网格与课程信息..."
                when (val res = com.renly.rmf.domain.service.AiService.extractTimetableFromImage(context, bytes)) {
                    is com.renly.rmf.domain.service.AiResult.Success -> {
                        isAnalyzingImage = false
                        recognizedCourses = res.data
                        showImportPreviewDialog = true
                    }
                    is com.renly.rmf.domain.service.AiResult.Error -> {
                        isAnalyzingImage = false
                        importErrorText = res.errorMessage
                    }
                }
            }
        }
    }

    // 自动根据开学日期推算当前周次
    val calculatedCurrentWeek = remember(semesterStartDate) {
        val days = ChronoUnit.DAYS.between(semesterStartDate, LocalDate.now())
        if (days >= 0) ((days / 7).toInt() + 1).coerceIn(1, totalWeeks) else 1
    }
    var currentWeek by remember { mutableIntStateOf(calculatedCurrentWeek) }

    var showAddDialog by remember { mutableStateOf(false) }
    var showAdjustmentDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var selectedCourseDetail by remember { mutableStateOf<CourseEntity?>(null) }
    var isGridView by remember { mutableStateOf(true) } // 默认网格视图，画上浅灰色格子
    var addCourseInitialDay by remember { mutableIntStateOf(1) }
    var addCourseInitialSection by remember { mutableIntStateOf(1) }

    val daysOfWeek = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    val mondayDate = remember(semesterStartDate, currentWeek) {
        val diffToMonday = (semesterStartDate.dayOfWeek.value - 1)
        val alignedMonday = semesterStartDate.minusDays(diffToMonday.toLong())
        alignedMonday.plusWeeks((currentWeek - 1).toLong())
    }

    val sectionTimes = remember {
        mapOf(
            1 to Pair("08:00", "08:45"),
            2 to Pair("08:55", "09:40"),
            3 to Pair("10:00", "10:45"),
            4 to Pair("10:55", "11:40"),
            5 to Pair("14:00", "14:45"),
            6 to Pair("14:55", "15:40"),
            7 to Pair("16:00", "16:45"),
            8 to Pair("16:55", "17:40"),
            9 to Pair("19:00", "19:45"),
            10 to Pair("19:55", "20:40"),
            11 to Pair("20:50", "21:35"),
            12 to Pair("21:45", "22:30")
        )
    }

    Scaffold(
        containerColor = DarkBg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = DopamineCyan,
                contentColor = DarkBg,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加课程", modifier = Modifier.size(24.dp))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. 顶部 Header (当前周次 + 单双周 Badge + 电脑端核心弹窗: ⚡投影 / 🔄调停课 / ⚙️学期)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "第 $currentWeek 周",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = DopamineCyan.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (currentWeek % 2 != 0) "单周" else "双周",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = DopamineCyan,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "学期课表动态映射与时间块对齐",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 📷 截图导入 (AI 视觉多模态智能提取)
                    Surface(
                        onClick = {
                            if (!com.renly.rmf.domain.service.AiPreferences.isConfigured(context)) {
                                android.widget.Toast.makeText(
                                    context,
                                    "请先在「工作台 -> AI 接口与大模型配置」中填写 API 密钥！",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            } else {
                                imagePickerLauncher.launch("image/*")
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = DopaminePurple.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, DopaminePurple.copy(alpha = 0.5f)),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 9.dp)
                        ) {
                            Text("📷", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("截图导入", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = DopaminePurple)
                        }
                    }

                    // 🔄 调停课与补课管理弹窗 (CourseAdjustmentModal)
                    Surface(
                        onClick = { showAdjustmentDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 9.dp)
                        ) {
                            Text("🔄", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            val weekAdjCount = adjustments.count { it.targetWeek == currentWeek }
                            Text(
                                text = if (weekAdjCount > 0) "调停课 $weekAdjCount" else "调停课",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                    }

                    // ⚙️ 学期配置弹窗 (TimetableSettingsModal)
                    Surface(
                        onClick = { showSettingsDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 9.dp)
                        ) {
                            Text("⚙️", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("学期", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                    }

                    // ⚡ 投影到今日
                    Surface(
                        onClick = onProjectToToday,
                        shape = RoundedCornerShape(20.dp),
                        color = DopamineCyan.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, DopamineCyan.copy(alpha = 0.5f)),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Text("⚡", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("投影", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = DopamineCyan)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. 灵动周次滚轮切换条 1..totalWeeks + 🔲网格 / 📋清单 视图切换
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items((1..totalWeeks).toList()) { week ->
                        val isSelected = week == currentWeek
                        Surface(
                            onClick = { currentWeek = week },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) DopamineCyan.copy(alpha = 0.2f) else DarkSurface,
                            border = BorderStroke(1.dp, if (isSelected) DopamineCyan else DarkBorder),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 9.dp)) {
                                Text(
                                    text = "W$week",
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) DopamineCyan else TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 视图模式切换胶囊
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.height(30.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Surface(
                            onClick = { isGridView = true },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isGridView) DopamineCyan.copy(alpha = 0.2f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (isGridView) DopamineCyan else Color.Transparent),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 7.dp)) {
                                Text("🔲 网格", fontSize = 11.sp, fontWeight = if (isGridView) FontWeight.Bold else FontWeight.Normal, color = if (isGridView) DopamineCyan else TextSecondary)
                            }
                        }

                        Surface(
                            onClick = { isGridView = false },
                            shape = RoundedCornerShape(8.dp),
                            color = if (!isGridView) DopamineCyan.copy(alpha = 0.2f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (!isGridView) DopamineCyan else Color.Transparent),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 7.dp)) {
                                Text("📋 清单", fontSize = 11.sp, fontWeight = if (!isGridView) FontWeight.Bold else FontWeight.Normal, color = if (!isGridView) DopamineCyan else TextSecondary)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. 过滤并展示当前周次的课程
            val weekCourses = courses.filter { course ->
                currentWeek in course.startWeek..course.endWeek &&
                (course.weekType == "ALL" ||
                (course.weekType == "ODD" && currentWeek % 2 != 0) ||
                (course.weekType == "EVEN" && currentWeek % 2 == 0))
            }

            if (isGridView) {
                // 🔲 浅灰色网格视图 (7 列 x 12 节，包含真实浅灰色网格线)
                WeekTimetableGrid(
                    mondayDate = mondayDate,
                    daysOfWeek = daysOfWeek,
                    weekCourses = weekCourses,
                    sectionTimes = sectionTimes,
                    onCourseClick = { course -> selectedCourseDetail = course },
                    onCellClick = { day, sec ->
                        addCourseInitialDay = day
                        addCourseInitialSection = sec
                        showAddDialog = true
                    }
                )
            } else {
                // 📋 清单流视图
                if (weekCourses.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🏖️", fontSize = 32.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "第 $currentWeek 周无排期课程，尽情自主攻坚！", fontSize = 13.sp, color = TextMuted)
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        for (dayIdx in 1..7) {
                            val dayCourses = weekCourses.filter { it.dayOfWeek == dayIdx }
                            if (dayCourses.isNotEmpty()) {
                                item {
                                    Column {
                                        Text(
                                            text = daysOfWeek[dayIdx - 1],
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DopamineCyan,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                        dayCourses.forEach { course ->
                                            CourseCard(
                                                course = course,
                                                onClick = { selectedCourseDetail = course }
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Spacer(modifier = Modifier.height(60.dp))
                        }
                    }
                }
            }
        }

        // 添加课程弹窗 (CourseModal - 创建)
        if (showAddDialog) {
            AddCourseDialog(
                initialDay = addCourseInitialDay,
                initialSection = addCourseInitialSection,
                onDismiss = { showAddDialog = false },
                onConfirm = { newCourse ->
                    onAddCourse(newCourse)
                    showAddDialog = false
                }
            )
        }

        // 课程详情与删除弹窗 (CourseModal - 详情)
        selectedCourseDetail?.let { course ->
            CourseDetailDialog(
                course = course,
                onDismiss = { selectedCourseDetail = null },
                onDelete = {
                    onDeleteCourse(course.id)
                    selectedCourseDetail = null
                }
            )
        }

        // 调停课与补课管理弹窗 (CourseAdjustmentModal)
        if (showAdjustmentDialog) {
            CourseAdjustmentDialog(
                courses = courses,
                adjustments = adjustments,
                currentWeek = currentWeek,
                onDismiss = { showAdjustmentDialog = false },
                onAddAdjustment = { adj ->
                    onAddAdjustment(adj)
                },
                onDeleteAdjustment = { id ->
                    onDeleteAdjustment(id)
                }
            )
        }

        // 学期课表参数设置弹窗 (TimetableSettingsModal)
        if (showSettingsDialog) {
            TimetableSettingsDialog(
                currentStartDate = semesterStartDate,
                currentTotalWeeks = totalWeeks,
                onDismiss = { showSettingsDialog = false },
                onSave = { newStart, newWeeks ->
                    semesterStartDate = newStart
                    totalWeeks = newWeeks
                    TimetablePreferences.setSemesterStartDate(context, newStart)
                    TimetablePreferences.setTotalWeeks(context, newWeeks)
                    showSettingsDialog = false
                }
            )
        }

        // 🤖 AI 课表截图分析中加载弹窗
        if (isAnalyzingImage) {
            AlertDialog(
                onDismissRequest = {},
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = DopaminePurple,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("AI 正在提取课表...", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                },
                text = {
                    Column {
                        Text(analysisStatusText, fontSize = 13.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "提示：提取耗时约 3~10 秒，模型将自动解析周几、节次、教室与单双周。",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                },
                confirmButton = {},
                containerColor = DarkCard,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // ⚠️ 课表识别异常提示弹窗
        if (importErrorText != null) {
            AlertDialog(
                onDismissRequest = { importErrorText = null },
                title = { Text("⚠️ 课表识别提示", fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = {
                    Text(
                        text = importErrorText ?: "",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = { importErrorText = null }) {
                        Text("我知道了", color = DopamineBlue, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = DarkCard,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // 📋 AI 课表识别结果核对与导入确认弹窗
        if (showImportPreviewDialog) {
            AiTimetableImportPreviewDialog(
                initialCourses = recognizedCourses,
                onDismiss = { showImportPreviewDialog = false },
                onConfirmImport = { selectedCourses ->
                    onImportCourses(selectedCourses)
                    showImportPreviewDialog = false
                }
            )
        }
    }
}

/**
 * 📋 AI 识别课表清单二次确认弹窗
 */
@Composable
fun AiTimetableImportPreviewDialog(
    initialCourses: List<CourseEntity>,
    onDismiss: () -> Unit,
    onConfirmImport: (List<CourseEntity>) -> Unit
) {
    var selectedIds by remember { mutableStateOf(initialCourses.map { it.id }.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "📋 识别出 ${initialCourses.size} 门课程",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        selectedIds = if (selectedIds.size == initialCourses.size) emptySet() else initialCourses.map { it.id }.toSet()
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (selectedIds.size == initialCourses.size) "取消全选" else "全选",
                        fontSize = 12.sp,
                        color = DopaminePurple,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "请核对识别出的排课信息，点击条目可取消导入不需要的课程：",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(initialCourses) { course ->
                        val isChecked = selectedIds.contains(course.id)
                        val color = try {
                            Color(android.graphics.Color.parseColor(course.colorHex))
                        } catch (_: Exception) {
                            DopamineBlue
                        }
                        val dayStr = when (course.dayOfWeek) {
                            1 -> "周一"
                            2 -> "周二"
                            3 -> "周三"
                            4 -> "周四"
                            5 -> "周五"
                            6 -> "周六"
                            7 -> "周日"
                            else -> "周${course.dayOfWeek}"
                        }
                        val weekTypeStr = when (course.weekType) {
                            "ODD" -> "(单周)"
                            "EVEN" -> "(双周)"
                            else -> ""
                        }

                        Surface(
                            onClick = {
                                selectedIds = if (isChecked) selectedIds - course.id else selectedIds + course.id
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isChecked) DarkSurface else DarkCard.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, if (isChecked) color.copy(alpha = 0.6f) else DarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedIds = if (checked) selectedIds + course.id else selectedIds - course.id
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = DopaminePurple,
                                        uncheckedColor = TextSecondary
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = CircleShape,
                                            color = color,
                                            modifier = Modifier.size(8.dp)
                                        ) {}
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = course.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$dayStr 第${course.startSection}-${course.startSection + course.sectionSpan - 1}节 · ${course.startWeek}-${course.endWeek}周 $weekTypeStr",
                                        fontSize = 11.5.sp,
                                        color = TextSecondary
                                    )
                                    if (course.location.isNotBlank() || course.teacher.isNotBlank()) {
                                        Text(
                                            text = listOf(course.location, course.teacher).filter { it.isNotBlank() }.joinToString(" · "),
                                            fontSize = 11.sp,
                                            color = DopamineCyan.copy(alpha = 0.85f)
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
                    val toImport = initialCourses.filter { selectedIds.contains(it.id) }
                    onConfirmImport(toImport)
                },
                enabled = selectedIds.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = DopaminePurple),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("一键导入勾选课程 (${selectedIds.size})", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        },
        containerColor = DarkCard,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun CourseCard(
    course: CourseEntity,
    onClick: () -> Unit = {}
) {
    val courseColor = try {
        Color(android.graphics.Color.parseColor(course.colorHex))
    } catch (_: Exception) {
        DopamineBlue
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = DarkCard,
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(3.dp),
                color = courseColor,
                modifier = Modifier
                    .width(4.dp)
                    .height(42.dp)
            ) {}

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                val timeInfo = if (course.isCustomTime == 1 && !course.customStartTime.isNullOrBlank()) {
                    "⏰ ${course.customStartTime} - ${course.customEndTime ?: ""}"
                } else {
                    "📖 第 ${course.startSection} - ${course.startSection + course.sectionSpan - 1} 节"
                }

                Text(
                    text = timeInfo,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )

                val metaInfo = buildString {
                    if (course.location.isNotBlank()) append("📍 ${course.location}  ")
                    if (course.teacher.isNotBlank()) append("👨‍🏫 ${course.teacher}")
                }
                if (metaInfo.isNotBlank()) {
                    Text(
                        text = metaInfo,
                        fontSize = 11.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// 课程详情与管理弹窗 (CourseModal)
// -----------------------------------------------------------------------------------------
@Composable
fun CourseDetailDialog(
    course: CourseEntity,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🎓 ${course.name}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "删除课程", tint = DopamineRed)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("教师: ${course.teacher.ifBlank { "未指定" }}", fontSize = 13.sp, color = TextSecondary)
                Text("地点: ${course.location.ifBlank { "未指定" }}", fontSize = 13.sp, color = TextSecondary)
                Text("排期周次: 第 ${course.startWeek} - ${course.endWeek} 周 (${when (course.weekType) {
                    "ODD" -> "单周"
                    "EVEN" -> "双周"
                    else -> "每周"
                }})", fontSize = 13.sp, color = TextSecondary)
                Text("节次跨度: 第 ${course.startSection} 节起，共 ${course.sectionSpan} 节", fontSize = 13.sp, color = TextSecondary)
                if (course.isCustomTime == 1) {
                    Text("自定义时间: ${course.customStartTime} - ${course.customEndTime}", fontSize = 13.sp, color = DopamineCyan)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = DopamineCyan, fontWeight = FontWeight.Bold) }
        }
    )
}

// -----------------------------------------------------------------------------------------
// 🔄 电脑版 1:1 调停课与补课管理弹窗 (CourseAdjustmentModal)
// -----------------------------------------------------------------------------------------
@Composable
fun CourseAdjustmentDialog(
    courses: List<CourseEntity>,
    adjustments: List<CourseAdjustmentEntity>,
    currentWeek: Int,
    onDismiss: () -> Unit,
    onAddAdjustment: (CourseAdjustmentEntity) -> Unit,
    onDeleteAdjustment: (String) -> Unit
) {
    var selectedCourseId by remember { mutableStateOf(courses.firstOrNull()?.id ?: "") }
    var adjustmentType by remember { mutableStateOf("CANCEL") } // CANCEL, RESCHEDULE, ADD
    var targetWeek by remember { mutableIntStateOf(currentWeek) }
    var newDayOfWeek by remember { mutableIntStateOf(1) }
    var newStartSection by remember { mutableIntStateOf(1) }
    var reason by remember { mutableStateOf("") }
    var newLocation by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔄 调停课与补课管理", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("登记指定周次的临时停课、调换时段或加课：", fontSize = 11.5.sp, color = TextSecondary)

                // 调整类型选择
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("CANCEL" to "停课", "RESCHEDULE" to "调课", "ADD" to "加课").forEach { (typeKey, label) ->
                        FilterChip(
                            selected = adjustmentType == typeKey,
                            onClick = { adjustmentType = typeKey },
                            label = { Text(label, fontSize = 11.5.sp) }
                        )
                    }
                }

                // 课程选择 (如果有课程)
                if (courses.isNotEmpty()) {
                    Text("选择目标课程:", fontSize = 12.sp, color = TextSecondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(courses) { c ->
                            FilterChip(
                                selected = selectedCourseId == c.id,
                                onClick = { selectedCourseId = c.id },
                                label = { Text(c.name, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // 生效周次
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("生效周次: 第 $targetWeek 周", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                    Row {
                        IconButton(onClick = { if (targetWeek > 1) targetWeek-- }, modifier = Modifier.size(28.dp)) {
                            Text("◀", fontSize = 12.sp, color = DopamineCyan)
                        }
                        IconButton(onClick = { targetWeek++ }, modifier = Modifier.size(28.dp)) {
                            Text("▶", fontSize = 12.sp, color = DopamineCyan)
                        }
                    }
                }

                if (adjustmentType != "CANCEL") {
                    Text("调整后星期 (周$newDayOfWeek):", fontSize = 12.sp, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..7).forEach { d ->
                            Surface(
                                onClick = { newDayOfWeek = d },
                                shape = RoundedCornerShape(6.dp),
                                color = if (newDayOfWeek == d) DopamineCyan else DarkSurface,
                                border = BorderStroke(1.dp, if (newDayOfWeek == d) DopamineCyan else DarkBorder),
                                modifier = Modifier.weight(1f).height(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("周$d", fontSize = 10.sp, color = if (newDayOfWeek == d) DarkBg else TextSecondary)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = newLocation,
                        onValueChange = { newLocation = it },
                        label = { Text("新上课教室/地点") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DopamineCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("调课事由 (如: 教师出差)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DopamineCyan,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val cName = courses.find { it.id == selectedCourseId }?.name ?: "自定义课程"
                        val nowStr = java.time.LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                        val adj = CourseAdjustmentEntity(
                            courseId = selectedCourseId,
                            courseName = cName,
                            adjustmentType = adjustmentType,
                            targetWeek = targetWeek,
                            newDayOfWeek = newDayOfWeek,
                            newStartSection = newStartSection,
                            newLocation = newLocation,
                            reason = reason,
                            createdAt = nowStr,
                            updatedAt = nowStr
                        )
                        onAddAdjustment(adj)
                        reason = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DopamineCyan),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存本条调停课记录", color = DarkBg, fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(color = DarkBorder, thickness = 1.dp)

                // 已有调停课记录清单
                Text("已生效的调停课历史:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                if (adjustments.isEmpty()) {
                    Text("暂无任何调停课记录", fontSize = 11.sp, color = TextMuted)
                } else {
                    adjustments.forEach { adj ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DarkSurface,
                            border = BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "第${adj.targetWeek}周 · ${adj.courseName} · [${when (adj.adjustmentType) {
                                            "CANCEL" -> "停课"
                                            "RESCHEDULE" -> "调至周${adj.newDayOfWeek}"
                                            else -> "加课"
                                        }}]",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (adj.adjustmentType == "CANCEL") DopamineRed else DopamineCyan
                                    )
                                    if (adj.reason.isNotBlank()) {
                                        Text("事由: ${adj.reason}", fontSize = 10.5.sp, color = TextSecondary)
                                    }
                                }
                                IconButton(
                                    onClick = { onDeleteAdjustment(adj.id) },
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
            TextButton(onClick = onDismiss) { Text("完成", color = DopamineCyan, fontWeight = FontWeight.Bold) }
        }
    )
}

// -----------------------------------------------------------------------------------------
// ⚙️ 电脑版 1:1 学期课表参数设置弹窗 (TimetableSettingsModal)
// -----------------------------------------------------------------------------------------
@Composable
fun TimetableSettingsDialog(
    currentStartDate: LocalDate,
    currentTotalWeeks: Int,
    onDismiss: () -> Unit,
    onSave: (LocalDate, Int) -> Unit
) {
    val ctx = LocalContext.current
    var year by remember { mutableIntStateOf(currentStartDate.year) }
    var month by remember { mutableIntStateOf(currentStartDate.monthValue) }
    var day by remember { mutableIntStateOf(currentStartDate.dayOfMonth) }
    var weeks by remember { mutableIntStateOf(currentTotalWeeks) }
    var reminderEnabled by remember { mutableStateOf(TimetablePreferences.isCourseReminderEnabled(ctx)) }
    var advanceMins by remember { mutableIntStateOf(TimetablePreferences.getReminderAdvanceMinutes(ctx)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("⚙️ 学期课表参数配置", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("配置开学第一周周一的基准日期，系统将自动精确映射教学周：", fontSize = 11.5.sp, color = TextSecondary)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = year.toString(),
                        onValueChange = { year = it.toIntOrNull() ?: year },
                        label = { Text("开学年") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DopamineCyan,
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
                            focusedBorderColor = DopamineCyan,
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
                            focusedBorderColor = DopamineCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("学期总周数: $weeks 周", fontSize = 12.sp, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(16, 18, 20, 22).forEach { w ->
                        FilterChip(
                            selected = weeks == w,
                            onClick = { weeks = w },
                            label = { Text("${w}周", fontSize = 12.sp) }
                        )
                    }
                }

                HorizontalDivider(color = DarkBorder, thickness = 1.dp)

                // ColorOS 流体云上课提醒配置

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("☁️ ColorOS 流体云上课提醒", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("上课前在状态栏胶囊、锁屏及横幅弹出流体云实时倒计时提醒", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = { reminderEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DopamineCyan,
                            checkedTrackColor = DopamineCyan.copy(alpha = 0.3f)
                        )
                    )
                }

                if (reminderEnabled) {
                    Text("提前提醒时间:", fontSize = 12.sp, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15, 20, 30).forEach { m ->
                            FilterChip(
                                selected = advanceMins == m,
                                onClick = { advanceMins = m },
                                label = { Text("提前${m}分钟", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val ctx = LocalContext.current
            Button(
                    onClick = {
                        TimetablePreferences.setCourseReminderEnabled(ctx, reminderEnabled)
                        TimetablePreferences.setReminderAdvanceMinutes(ctx, advanceMins)
                        try {
                            val newDate = LocalDate.of(year, month, day.coerceAtMost(LocalDate.of(year, month, 1).lengthOfMonth()))
                            onSave(newDate, weeks)
                        } catch (_: Exception) {
                            onSave(currentStartDate, weeks)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DopamineCyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("保存设置", color = DarkBg, fontWeight = FontWeight.Bold)
                }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCourseDialog(
    initialDay: Int = 1,
    initialSection: Int = 1,
    onDismiss: () -> Unit,
    onConfirm: (CourseEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var dayOfWeek by remember(initialDay) { mutableIntStateOf(initialDay) }
    var startSection by remember(initialSection) { mutableIntStateOf(initialSection) }
    var sectionSpan by remember { mutableIntStateOf(2) }
    var colorHex by remember { mutableStateOf("#38BDF8") }

    val daysOfWeek = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("录入学期课程", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("课程名称 (如: 操作系统)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DopamineCyan,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = teacher,
                        onValueChange = { teacher = it },
                        label = { Text("任课教师") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DopamineCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("教室地点") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DopamineCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("星期:", fontSize = 12.sp, color = TextSecondary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items((1..7).toList()) { d ->
                        FilterChip(
                            selected = dayOfWeek == d,
                            onClick = { dayOfWeek = d },
                            label = { Text(daysOfWeek[d - 1], fontSize = 11.sp) }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("第 $startSection 节起，连上 $sectionSpan 节", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                    Row {
                        IconButton(onClick = { if (startSection > 1) startSection-- }) { Text("◀", color = DopamineCyan) }
                        IconButton(onClick = { if (startSection < 12) startSection++ }) { Text("▶", color = DopamineCyan) }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val nowStr = java.time.LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                        val entity = CourseEntity(
                            name = name,
                            teacher = teacher,
                            location = location,
                            dayOfWeek = dayOfWeek,
                            startSection = startSection,
                            sectionSpan = sectionSpan,
                            startWeek = 1,
                            endWeek = 16,
                            weekType = "ALL",
                            colorHex = colorHex,
                            createdAt = nowStr,
                            updatedAt = nowStr
                        )
                        onConfirm(entity)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DopamineCyan),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("录入课程", color = DarkBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        }
    )
}

@Composable
fun WeekTimetableGrid(
    mondayDate: LocalDate,
    daysOfWeek: List<String>,
    weekCourses: List<CourseEntity>,
    sectionTimes: Map<Int, Pair<String, String>>,
    onCourseClick: (CourseEntity) -> Unit,
    onCellClick: (dayOfWeek: Int, startSection: Int) -> Unit
) {
    val sectionHeight = 58.dp
    val totalHeight = sectionHeight * 12
    val isDark = LocalAppColors.current.isDark
    val gridLineColor = if (isDark) Color(0x3894A3B8) else Color(0xFFE2E8F0) // 浅灰色网格线 (夜间微光 / 日间柔和 Slate-200)
    val sessionDividerColor = if (isDark) Color(0x66CBD5E1) else Color(0xFFCBD5E1) // 上午/下午/晚上分割线

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .border(BorderStroke(1.dp, gridLineColor), RoundedCornerShape(12.dp))
    ) {
        // 1. 顶部表头行 (月份 + 7天星期与公历日期)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkCard)
        ) {
            // 左上角月份
            Box(
                modifier = Modifier
                    .width(38.dp)
                    .height(44.dp)
                    .border(BorderStroke(0.5.dp, gridLineColor)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${mondayDate.monthValue}\n月",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = DopamineCyan,
                    lineHeight = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            // 7天星期与日期
            for (d in 0..6) {
                val dayDate = mondayDate.plusDays(d.toLong())
                val isToday = (dayDate == LocalDate.now())
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .border(BorderStroke(0.5.dp, gridLineColor))
                        .background(if (isToday) DopamineCyan.copy(alpha = 0.15f) else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = daysOfWeek[d],
                            fontSize = 11.5.sp,
                            fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isToday) DopamineCyan else TextPrimary
                        )
                        Text(
                            text = "${dayDate.monthValue}/${dayDate.dayOfMonth}",
                            fontSize = 9.5.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) DopamineCyan else TextMuted
                        )
                    }
                }
            }
        }

        // 2. 纵向滚动的节次与网格矩阵
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .height(totalHeight)
        ) {
            // 左侧节次时间列 (1 ~ 12 节)
            Column(
                modifier = Modifier
                    .width(38.dp)
                    .height(totalHeight)
                    .background(DarkCard.copy(alpha = 0.7f))
            ) {
                for (sec in 1..12) {
                    val slot = sectionTimes[sec] ?: Pair("00:00", "00:00")
                    val isMajor = (sec == 4 || sec == 8)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(sectionHeight)
                            .border(
                                BorderStroke(
                                    if (isMajor) 1.dp else 0.5.dp,
                                    if (isMajor) sessionDividerColor else gridLineColor
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$sec",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = slot.first,
                                fontSize = 8.5.sp,
                                color = TextSecondary,
                                lineHeight = 10.sp
                            )
                            Text(
                                text = slot.second,
                                fontSize = 8.sp,
                                color = TextMuted,
                                lineHeight = 9.sp
                            )
                        }
                    }
                }
            }

            // 7天课程列
            for (dayIdx in 1..7) {
                val dayCourses = weekCourses.filter { it.dayOfWeek == dayIdx }
                val isToday = (mondayDate.plusDays((dayIdx - 1).toLong()) == LocalDate.now())

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(totalHeight)
                        .background(if (isToday) DopamineCyan.copy(alpha = 0.04f) else Color.Transparent)
                ) {
                    // 底层: 12个带有浅灰色边框的格子 (点击可直接快速加课)
                    Column(modifier = Modifier.fillMaxSize()) {
                        for (sec in 1..12) {
                            val isMajor = (sec == 4 || sec == 8)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(sectionHeight)
                                    .border(
                                        BorderStroke(
                                            if (isMajor) 1.dp else 0.5.dp,
                                            if (isMajor) sessionDividerColor else gridLineColor
                                        )
                                    )
                                    .clickable {
                                        onCellClick(dayIdx, sec)
                                    }
                            )
                        }
                    }

                    // 上层: 当前天的课程卡片
                    for (course in dayCourses) {
                        val startSec = course.startSection.coerceIn(1, 12)
                        val span = course.sectionSpan.coerceAtLeast(1).coerceAtMost(13 - startSec)
                        val topOffset = sectionHeight * (startSec - 1)
                        val cardHeight = sectionHeight * span - 4.dp

                        val parsedColor = try {
                            Color(android.graphics.Color.parseColor(course.colorHex))
                        } catch (_: Exception) {
                            DopamineBlue
                        }

                        Surface(
                            onClick = { onCourseClick(course) },
                            shape = RoundedCornerShape(6.dp),
                            color = parsedColor.copy(alpha = 0.88f),
                            border = BorderStroke(1.dp, parsedColor),
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .offset(x = 0.dp, y = topOffset + 2.dp)
                                .fillMaxWidth()
                                .height(cardHeight)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 3.dp, vertical = 3.dp),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text(
                                    text = course.name,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = if (span >= 3) 4 else 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 12.sp
                                )
                                if (course.location.isNotBlank()) {
                                    Text(
                                        text = "@${course.location}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White.copy(alpha = 0.9f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 10.sp
                                    )
                                }
                                if (course.teacher.isNotBlank() && span >= 2) {
                                    Text(
                                        text = course.teacher,
                                        fontSize = 8.5.sp,
                                        color = Color.White.copy(alpha = 0.8f),
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

