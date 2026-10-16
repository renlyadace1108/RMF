package com.renly.rmf.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.ui.input.pointer.pointerInput
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

    var isSemesterConfigured by remember { mutableStateOf(TimetablePreferences.isSemesterConfigured(context)) }
    var activeSemester by remember { mutableIntStateOf(TimetablePreferences.getActiveSemester(context)) }
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

    // 自动根据开学日期推算当前周次（以开学周周一对齐）
    val calculatedCurrentWeek = remember(semesterStartDate, totalWeeks) {
        val alignedMonday = semesterStartDate.minusDays((semesterStartDate.dayOfWeek.value - 1).toLong())
        val days = ChronoUnit.DAYS.between(alignedMonday, LocalDate.now())
        if (days >= 0) ((days / 7).toInt() + 1).coerceIn(1, totalWeeks) else 1
    }
    var currentWeek by remember(calculatedCurrentWeek) { mutableIntStateOf(calculatedCurrentWeek) }

    var showAddDialog by remember { mutableStateOf(false) }
    var showAdjustmentDialog by remember { mutableStateOf(false) }
    // 首次进入课程表若尚未配置开学日期，强制弹出学期配置弹窗
    var showSettingsDialog by remember { mutableStateOf(!isSemesterConfigured) }
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

    var timetableZoomFactor by remember { mutableFloatStateOf(1.0f) }

    Scaffold(
        containerColor = DarkBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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

            // 1. 顶部 Header 第一行：当前周次 + 学期/单双周 Badge + 网格缩放控制
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "第 $currentWeek 周",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            onClick = { showSettingsDialog = true },
                            color = DopamineBlue.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (activeSemester == 1) "第一学期" else "第二学期",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = DopamineBlue,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
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
                        text = "课表实时映射至日程 · 开学 ${semesterStartDate.monthValue}月${semesterStartDate.dayOfMonth}日 (${totalWeeks}周) · 双指捏合缩放",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 1.5 顶部快捷操作栏（独立单行，4个按钮均匀排布绝不挤压折行）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 📷 截图导入 (AI 视觉多模态智能提取)
                Surface(
                    onClick = {
                        if (!com.renly.rmf.domain.service.AiPreferences.isAiEnabled(context)) {
                            android.widget.Toast.makeText(
                                context,
                                "AI 智能功能已在系统设置中停用，请先前往设置开启！",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        } else if (!com.renly.rmf.domain.service.AiPreferences.isConfigured(context)) {
                            android.widget.Toast.makeText(
                                context,
                                "请先在「系统设置 -> 智能互联 -> AI 接口与模型配置」中填写 API 密钥！",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        } else {
                            imagePickerLauncher.launch("image/*")
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = DopaminePurple.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, DopaminePurple.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1.1f).height(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📷", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("截图导入", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DopaminePurple, maxLines = 1)
                        }
                    }
                }

                // 🔄 调停课与补课管理弹窗 (CourseAdjustmentModal)
                Surface(
                    onClick = { showAdjustmentDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.weight(1f).height(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔄", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            val weekAdjCount = adjustments.count { it.targetWeek == currentWeek }
                            Text(
                                text = if (weekAdjCount > 0) "调停课 $weekAdjCount" else "调停课",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }

                // ⚙️ 学期配置弹窗 (TimetableSettingsModal)
                Surface(
                    onClick = { showSettingsDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.weight(0.95f).height(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚙️", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("学期配置", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1)
                        }
                    }
                }

                // ⚡ 投影到今日/全学期同步
                Surface(
                    onClick = onProjectToToday,
                    shape = RoundedCornerShape(10.dp),
                    color = DopamineCyan.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, DopamineCyan.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(0.95f).height(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚡", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("同步日程", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DopamineCyan, maxLines = 1)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                            modifier = Modifier.height(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                                Text(
                                    text = "W$week",
                                    fontSize = 12.sp,
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
                    modifier = Modifier.height(32.dp)
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
                            modifier = Modifier.height(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text("🔲 网格", fontSize = 11.5.sp, fontWeight = if (isGridView) FontWeight.Bold else FontWeight.Normal, color = if (isGridView) DopamineCyan else TextSecondary)
                            }
                        }

                        Surface(
                            onClick = { isGridView = false },
                            shape = RoundedCornerShape(8.dp),
                            color = if (!isGridView) DopamineCyan.copy(alpha = 0.2f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (!isGridView) DopamineCyan else Color.Transparent),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text("📋 清单", fontSize = 11.5.sp, fontWeight = if (!isGridView) FontWeight.Bold else FontWeight.Normal, color = if (!isGridView) DopamineCyan else TextSecondary)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. 过滤并展示当前周次的课程
            val weekCourses = courses.filter { course ->
                currentWeek in course.startWeek..course.endWeek &&
                (course.weekType == "ALL" ||
                (course.weekType == "ODD" && currentWeek % 2 != 0) ||
                (course.weekType == "EVEN" && currentWeek % 2 == 0))
            }

            if (isGridView) {
                // 🔲 浅灰色网格视图 (7 列 x 12 节，包含真实浅灰色网格线与双指捏合无极缩放)
                WeekTimetableGrid(
                    mondayDate = mondayDate,
                    daysOfWeek = daysOfWeek,
                    weekCourses = weekCourses,
                    sectionTimes = sectionTimes,
                    zoomFactor = timetableZoomFactor,
                    onZoomChange = { newZoom -> timetableZoomFactor = newZoom },
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
                defaultEndWeek = totalWeeks,
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
                },
                onUpdateReminder = { newMins ->
                    val nowStr = java.time.LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                    val updated = course.copy(reminderMinutes = newMins, updatedAt = nowStr)
                    onAddCourse(updated)
                    selectedCourseDetail = updated
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

        // 学期课表参数设置弹窗 (TimetableSettingsModal - 首次使用强制配置第一/第二学期开学月日与周数)
        if (showSettingsDialog) {
            TimetableSettingsDialog(
                isMandatoryFirstSetup = !isSemesterConfigured,
                onDismiss = {
                    if (isSemesterConfigured) {
                        showSettingsDialog = false
                    }
                },
                onSave = { newActiveSemester, newStart, newWeeks ->
                    isSemesterConfigured = true
                    activeSemester = newActiveSemester
                    semesterStartDate = newStart
                    totalWeeks = newWeeks
                    showSettingsDialog = false
                    // 保存学期配置后立即触发课表到日程的全学期实时映射
                    onProjectToToday()
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
                    items(initialCourses, key = { it.id }) { course ->
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
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val defaultCourseColor = DopamineBlue
    val courseColor = remember(course.colorHex, defaultCourseColor) {
        try {
            Color(android.graphics.Color.parseColor(course.colorHex))
        } catch (_: Exception) {
            defaultCourseColor
        }
    }

    Surface(
        onClick = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = RoundedCornerShape(14.dp),
        color = DarkCard,
        border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.75f)),
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
    onDelete: () -> Unit,
    onUpdateReminder: (Int) -> Unit = {}
) {
    var reminderMins by remember(course.reminderMinutes) { mutableIntStateOf(course.reminderMinutes) }

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

                Spacer(modifier = Modifier.height(4.dp))
                Text("🔔 上课提醒时间 (实时活动胶囊倒计时):", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val reminderOptions = listOf(
                        15 to "提前15分",
                        10 to "提前10分",
                        5 to "提前5分",
                        0 to "准时",
                        -1 to "关闭"
                    )
                    reminderOptions.forEach { (mins, label) ->
                        FilterChip(
                            selected = reminderMins == mins,
                            onClick = {
                                reminderMins = mins
                                onUpdateReminder(mins)
                            },
                            label = { Text(label, fontSize = 10.5.sp) }
                        )
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
                        items(courses, key = { it.id }) { c ->
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
// ⚙️ 学期课表参数设置弹窗 (无年份输入，支持第一学期/第二学期开学月日与周数配置 & 首次强制配置)
// -----------------------------------------------------------------------------------------
@Composable
fun TimetableSettingsDialog(
    isMandatoryFirstSetup: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (activeSemester: Int, resolvedStartDate: LocalDate, resolvedTotalWeeks: Int) -> Unit
) {
    val ctx = LocalContext.current
    var activeSemester by remember { mutableIntStateOf(TimetablePreferences.getActiveSemester(ctx)) }

    // 第一学期 (月、日、周数) —— 已删除“年”
    var term1MonthText by remember { mutableStateOf(TimetablePreferences.getTerm1StartMonth(ctx).toString()) }
    var term1DayText by remember { mutableStateOf(TimetablePreferences.getTerm1StartDay(ctx).toString()) }
    var term1Weeks by remember { mutableIntStateOf(TimetablePreferences.getTerm1Weeks(ctx)) }

    // 第二学期 (月、日、周数) —— 已删除“年”
    var term2MonthText by remember { mutableStateOf(TimetablePreferences.getTerm2StartMonth(ctx).toString()) }
    var term2DayText by remember { mutableStateOf(TimetablePreferences.getTerm2StartDay(ctx).toString()) }
    var term2Weeks by remember { mutableIntStateOf(TimetablePreferences.getTerm2Weeks(ctx)) }

    var reminderEnabled by remember { mutableStateOf(TimetablePreferences.isCourseReminderEnabled(ctx)) }
    var advanceMins by remember { mutableIntStateOf(TimetablePreferences.getReminderAdvanceMinutes(ctx)) }

    AlertDialog(
        onDismissRequest = {
            if (!isMandatoryFirstSetup) {
                onDismiss()
            }
        },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = !isMandatoryFirstSetup,
            dismissOnClickOutside = !isMandatoryFirstSetup
        ),
        containerColor = DarkCard,
        title = {
            Column {
                Text(
                    text = if (isMandatoryFirstSetup) "🎓 首次使用课表 · 请配置学期参数" else "⚙️ 学期课表参数配置",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                if (isMandatoryFirstSetup) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "首次使用课程表需完成第一、第二学期开学日期与周数配置，录入的课程将实时映射至日程表",
                        color = DopamineCyan,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "选择当前启用的学期，并配置第一学期与第二学期的开学日期（月/日）及周数：",
                    fontSize = 11.5.sp,
                    color = TextSecondary
                )

                // 当前启用学期切换
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(1 to "🍂 第一学期 (当前)", 2 to "🌱 第二学期 (当前)").forEach { (termIdx, label) ->
                        val isSelected = activeSemester == termIdx
                        Surface(
                            onClick = { activeSemester = termIdx },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) DopamineCyan.copy(alpha = 0.2f) else DarkSurface,
                            border = BorderStroke(1.dp, if (isSelected) DopamineCyan else DarkBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (isSelected) label else if (termIdx == 1) "🍂 第一学期" else "🌱 第二学期",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) DopamineCyan else TextSecondary
                                )
                            }
                        }
                    }
                }

                // 1. 第一学期配置卡片 (仅月、日 + 学期周数)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurface,
                    border = BorderStroke(
                        1.dp,
                        if (activeSemester == 1) DopamineCyan.copy(alpha = 0.55f) else DarkBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🍂 第一学期开学日期",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeSemester == 1) DopamineCyan else TextPrimary
                            )
                            Text(
                                text = "总周数: $term1Weeks 周",
                                fontSize = 11.5.sp,
                                color = TextSecondary
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = term1MonthText,
                                onValueChange = { input ->
                                    val digits = input.filter { it.isDigit() }.take(2)
                                    term1MonthText = digits
                                },
                                label = { Text("开学月 (1-12)") },
                                singleLine = true,
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
                                value = term1DayText,
                                onValueChange = { input ->
                                    val digits = input.filter { it.isDigit() }.take(2)
                                    term1DayText = digits
                                },
                                label = { Text("开学日 (1-31)") },
                                singleLine = true,
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

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(16, 18, 20, 22).forEach { w ->
                                FilterChip(
                                    selected = term1Weeks == w,
                                    onClick = { term1Weeks = w },
                                    label = { Text("${w}周", fontSize = 11.5.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 2. 第二学期配置卡片 (仅月、日 + 学期周数)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurface,
                    border = BorderStroke(
                        1.dp,
                        if (activeSemester == 2) DopamineCyan.copy(alpha = 0.55f) else DarkBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🌱 第二学期开学日期",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeSemester == 2) DopamineCyan else TextPrimary
                            )
                            Text(
                                text = "总周数: $term2Weeks 周",
                                fontSize = 11.5.sp,
                                color = TextSecondary
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = term2MonthText,
                                onValueChange = { input ->
                                    val digits = input.filter { it.isDigit() }.take(2)
                                    term2MonthText = digits
                                },
                                label = { Text("开学月 (1-12)") },
                                singleLine = true,
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
                                value = term2DayText,
                                onValueChange = { input ->
                                    val digits = input.filter { it.isDigit() }.take(2)
                                    term2DayText = digits
                                },
                                label = { Text("开学日 (1-31)") },
                                singleLine = true,
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

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(16, 18, 20, 22).forEach { w ->
                                FilterChip(
                                    selected = term2Weeks == w,
                                    onClick = { term2Weeks = w },
                                    label = { Text("${w}周", fontSize = 11.5.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = DarkBorder, thickness = 1.dp)

                // 胶囊通知上课提醒配置
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("☁️ 胶囊通知上课提醒", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("上课前在状态栏胶囊、锁屏及横幅弹出胶囊通知实时倒计时提醒", fontSize = 11.sp, color = TextSecondary)
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
            Button(
                onClick = {
                    val t1M = (term1MonthText.toIntOrNull() ?: 9).coerceIn(1, 12)
                    val t1D = (term1DayText.toIntOrNull() ?: 1).coerceIn(1, 31)
                    val t2M = (term2MonthText.toIntOrNull() ?: 2).coerceIn(1, 12)
                    val t2D = (term2DayText.toIntOrNull() ?: 24).coerceIn(1, 31)

                    TimetablePreferences.setCourseReminderEnabled(ctx, reminderEnabled)
                    TimetablePreferences.setReminderAdvanceMinutes(ctx, advanceMins)
                    TimetablePreferences.saveDualSemesterConfig(
                        context = ctx,
                        activeSemester = activeSemester,
                        term1Month = t1M,
                        term1Day = t1D,
                        term1Weeks = term1Weeks,
                        term2Month = t2M,
                        term2Day = t2D,
                        term2Weeks = term2Weeks
                    )
                    val resolvedStart = TimetablePreferences.getSemesterStartDate(ctx)
                    val resolvedWeeks = TimetablePreferences.getTotalWeeks(ctx)
                    onSave(activeSemester, resolvedStart, resolvedWeeks)
                },
                colors = ButtonDefaults.buttonColors(containerColor = DopamineCyan),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isMandatoryFirstSetup) "完成配置并开启课表" else "保存设置",
                    color = DarkBg,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            if (!isMandatoryFirstSetup) {
                TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCourseDialog(
    initialDay: Int = 1,
    initialSection: Int = 1,
    defaultEndWeek: Int = 20,
    onDismiss: () -> Unit,
    onConfirm: (CourseEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var dayOfWeek by remember(initialDay) { mutableIntStateOf(initialDay) }
    var startSection by remember(initialSection) { mutableIntStateOf(initialSection) }
    var sectionSpan by remember { mutableIntStateOf(2) }
    var startWeek by remember { mutableIntStateOf(1) }
    var endWeek by remember(defaultEndWeek) { mutableIntStateOf(defaultEndWeek) }
    var weekType by remember { mutableStateOf("ALL") }
    var colorHex by remember { mutableStateOf("#38BDF8") }
    var reminderMinutes by remember { mutableIntStateOf(15) }

    val daysOfWeek = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("录入学期课程 (实时映射日程)", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("单次节数:", fontSize = 12.sp, color = TextSecondary)
                    listOf(1, 2, 3, 4).forEach { span ->
                        FilterChip(
                            selected = sectionSpan == span,
                            onClick = { sectionSpan = span },
                            label = { Text("${span}节", fontSize = 11.sp) }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("周次范围: 第 $startWeek - $endWeek 周", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                    Row {
                        IconButton(onClick = { if (endWeek > startWeek) endWeek-- }) { Text("-", color = DopamineCyan) }
                        IconButton(onClick = { if (endWeek < 30) endWeek++ }) { Text("+", color = DopamineCyan) }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL" to "每周", "ODD" to "单周", "EVEN" to "双周").forEach { (wType, label) ->
                        FilterChip(
                            selected = weekType == wType,
                            onClick = { weekType = wType },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text("🔔 上课提醒时间 (实时活动胶囊倒计时):", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val reminderOptions = listOf(
                        15 to "提前15分",
                        10 to "提前10分",
                        5 to "提前5分",
                        0 to "准时",
                        -1 to "关闭"
                    )
                    reminderOptions.forEach { (mins, label) ->
                        FilterChip(
                            selected = reminderMinutes == mins,
                            onClick = { reminderMinutes = mins },
                            label = { Text(label, fontSize = 10.5.sp) }
                        )
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
                            startWeek = startWeek,
                            endWeek = endWeek,
                            weekType = weekType,
                            colorHex = colorHex,
                            reminderMinutes = reminderMinutes,
                            createdAt = nowStr,
                            updatedAt = nowStr
                        )
                        onConfirm(entity)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DopamineCyan),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("录入并同步日程", color = DarkBg, fontWeight = FontWeight.Bold)
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
    zoomFactor: Float = 1.0f,
    onZoomChange: (Float) -> Unit = {},
    onCourseClick: (CourseEntity) -> Unit,
    onCellClick: (dayOfWeek: Int, startSection: Int) -> Unit
) {
    val currentZoom by rememberUpdatedState(zoomFactor)
    val currentOnZoomChange by rememberUpdatedState(onZoomChange)
    val sectionHeight = (60f * zoomFactor).dp
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
                    .width(40.dp)
                    .height(46.dp)
                    .border(BorderStroke(0.5.dp, gridLineColor)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${mondayDate.monthValue}\n月",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = DopamineCyan,
                    lineHeight = 13.5.sp,
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
                        .height(46.dp)
                        .border(BorderStroke(0.5.dp, gridLineColor))
                        .background(if (isToday) DopamineCyan.copy(alpha = 0.15f) else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = daysOfWeek[d],
                            fontSize = 12.sp,
                            fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isToday) DopamineCyan else TextPrimary
                        )
                        Text(
                            text = "${dayDate.monthValue}/${dayDate.dayOfMonth}",
                            fontSize = 10.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) DopamineCyan else TextMuted
                        )
                    }
                }
            }
        }

        // 2. 纵向滚动的节次与网格矩阵（支持双指捏合无极缩放）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) {
                                val zoomChange = event.calculateZoom()
                                if (zoomChange != 1f && zoomChange > 0f) {
                                    currentOnZoomChange((currentZoom * zoomChange).coerceIn(0.65f, 1.8f))
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .verticalScroll(rememberScrollState())
                .height(totalHeight)
        ) {
            // 左侧节次时间列 (1 ~ 12 节)
            Column(
                modifier = Modifier
                    .width(40.dp)
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
                                fontSize = (12.5f * zoomFactor).coerceIn(11f, 15f).sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = slot.first,
                                fontSize = (9f * zoomFactor).coerceIn(8f, 11f).sp,
                                color = TextSecondary,
                                lineHeight = (10.5f * zoomFactor).coerceIn(9.5f, 12.5f).sp
                            )
                            Text(
                                text = slot.second,
                                fontSize = (8.5f * zoomFactor).coerceIn(7.5f, 10.5f).sp,
                                color = TextMuted,
                                lineHeight = (9.5f * zoomFactor).coerceIn(8.5f, 11.5f).sp
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
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = course.name,
                                    fontSize = (11f * zoomFactor).coerceIn(9.5f, 14f).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = if (span >= 3) 4 else 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = (12.5f * zoomFactor).coerceIn(11f, 16f).sp
                                )
                                if (course.location.isNotBlank()) {
                                    Text(
                                        text = "@${course.location}",
                                        fontSize = (9.5f * zoomFactor).coerceIn(8.5f, 12f).sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White.copy(alpha = 0.92f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = (10.5f * zoomFactor).coerceIn(9.5f, 13.5f).sp
                                    )
                                }
                                if (course.teacher.isNotBlank() && span >= 2) {
                                    Text(
                                        text = course.teacher,
                                        fontSize = (9f * zoomFactor).coerceIn(8f, 11.5f).sp,
                                        color = Color.White.copy(alpha = 0.85f),
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

