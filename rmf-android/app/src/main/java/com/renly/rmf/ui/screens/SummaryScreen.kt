package com.renly.rmf.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.data.local.entity.CourseEntity
import com.renly.rmf.data.local.entity.ExpenseEntity
import com.renly.rmf.data.local.entity.InterruptionEntity
import com.renly.rmf.data.local.entity.ScheduleEntity
import com.renly.rmf.domain.service.TimetablePreferences
import com.renly.rmf.ui.theme.LocalAppColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// =========================================================================
// 🕒 统一作息时间块数据结构 (兼容日程表排期 + 课表周期课程)
// =========================================================================
data class ClockTimeBlock(
    val id: String,
    val title: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val category: String = "WORK",
    val colorHex: String = "#3B82F6",
    val workType: String = "DEEP_WORK",
    val status: String = "PENDING",
    val isCourse: Boolean = false,
    val location: String = ""
)

private val defaultSectionTimes = mapOf(
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

private fun parseSummaryDateTime(dateStr: String): LocalDateTime? {
    if (dateStr.isBlank()) return null
    return try {
        LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME)
    } catch (_: Exception) {
        try {
            LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        } catch (_: Exception) {
            try {
                LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay()
            } catch (_: Exception) {
                null
            }
        }
    }
}

private fun parseHexColor(hex: String, defaultColor: Color): Color {
    return try {
        if (hex.isBlank()) return defaultColor
        val cleanHex = if (hex.startsWith("#")) hex else "#$hex"
        Color(android.graphics.Color.parseColor(cleanHex))
    } catch (_: Exception) {
        defaultColor
    }
}

/**
 * 聚合某一日的日程排期与课表课程，生成 24 小时时间块序列
 */
private fun getCombinedDailyBlocks(
    selectedDate: LocalDate,
    schedules: List<ScheduleEntity>,
    courses: List<CourseEntity>,
    context: Context
): List<ClockTimeBlock> {
    val list = mutableListOf<ClockTimeBlock>()

    // 1. 筛选当天日程
    for (s in schedules) {
        val st = parseSummaryDateTime(s.startTime) ?: continue
        if (st.toLocalDate() != selectedDate) continue

        var et = parseSummaryDateTime(s.endTime)
        if (et == null || !et.isAfter(st)) {
            val mins = if (s.estimatedMinutes > 0) s.estimatedMinutes.toLong() else 30L
            et = st.plusMinutes(mins)
        }

        val color = if (s.workType == "DEEP_WORK") "#A78BFA" else when (s.category) {
            "WORK" -> "#3B82F6"
            "STUDY" -> "#8B5CF6"
            "HEALTH" -> "#10B981"
            "LIFE" -> "#F59E0B"
            else -> "#38BDF8"
        }

        list.add(
            ClockTimeBlock(
                id = s.id,
                title = s.title,
                startTime = st,
                endTime = et,
                category = s.category.orEmpty().ifBlank { "WORK" },
                colorHex = if (!s.colorHex.isNullOrBlank()) s.colorHex else color,
                workType = s.workType,
                status = s.status,
                isCourse = false
            )
        )
    }

    // 2. 筛选当天课表课程 (结合学期周次与单双周)
    try {
        val dayOfWeek = selectedDate.dayOfWeek.value // 1..7 (周一为1)
        val semesterStart = TimetablePreferences.getSemesterStartDate(context)
        val diffDays = ChronoUnit.DAYS.between(semesterStart, selectedDate)
        val weekNum = if (diffDays >= 0) (diffDays / 7).toInt() + 1 else 1
        val totalWeeks = TimetablePreferences.getTotalWeeks(context)

        if (weekNum in 1..totalWeeks) {
            for (c in courses) {
                if (c.dayOfWeek != dayOfWeek) continue
                if (weekNum < c.startWeek || weekNum > c.endWeek) continue

                val isOdd = weekNum % 2 != 0
                val weekMatches = c.weekType == "ALL" || (c.weekType == "ODD" && isOdd) || (c.weekType == "EVEN" && !isOdd)
                if (!weekMatches) continue

                // 计算起止时间
                val startSlot = defaultSectionTimes[c.startSection]
                val endSlot = defaultSectionTimes[c.startSection + c.sectionSpan - 1] ?: startSlot

                if (startSlot != null && endSlot != null) {
                    val st = selectedDate.atTime(LocalTime.parse(startSlot.first))
                    val et = selectedDate.atTime(LocalTime.parse(endSlot.second))

                    // 避免如果日程中已经投影过该课程产生完全重复
                    val alreadyProjected = list.any {
                        it.isCourse.not() && it.title.contains(c.name) && it.startTime == st
                    }
                    if (!alreadyProjected) {
                        list.add(
                            ClockTimeBlock(
                                id = "course_${c.id}_$selectedDate",
                                title = c.name + if (c.location.isNotBlank()) " (${c.location})" else "",
                                startTime = st,
                                endTime = et,
                                category = "COURSE",
                                colorHex = c.colorHex.ifBlank { "#06B6D4" },
                                workType = "DEEP_WORK",
                                status = "COMPLETED",
                                isCourse = true,
                                location = c.location
                            )
                        )
                    }
                }
            }
        }
    } catch (_: Exception) {}

    return list.sortedBy { it.startTime }
}

@Composable
fun SummaryScreen(
    schedulesFlow: Flow<List<ScheduleEntity>>,
    coursesFlow: Flow<List<CourseEntity>> = flowOf(emptyList()),
    interruptionsFlow: Flow<List<InterruptionEntity>>,
    expensesFlow: Flow<List<ExpenseEntity>>,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current

    val schedules by schedulesFlow.collectAsState(initial = emptyList())
    val courses by coursesFlow.collectAsState(initial = emptyList())
    val interruptions by interruptionsFlow.collectAsState(initial = emptyList())
    val expenses by expensesFlow.collectAsState(initial = emptyList())

    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedBlock by remember { mutableStateOf<ClockTimeBlock?>(null) }

    val dailyBlocks = remember(selectedDate, schedules, courses) {
        getCombinedDailyBlocks(selectedDate, schedules, courses, context)
    }

    val totalHours = dailyBlocks.sumOf {
        Duration.between(it.startTime, it.endTime).toMinutes().toDouble() / 60.0
    }

    val totalSchedules = schedules.size
    val completedSchedules = schedules.count { it.status == "COMPLETED" }
    val completionRate = if (totalSchedules > 0) (completedSchedules * 100 / totalSchedules) else 0

    val totalInterruptionSeconds = interruptions.sumOf { it.durationSeconds }
    val interruptionMinutes = totalInterruptionSeconds / 60
    val totalExpenseAmount = expenses.sumOf { it.amount }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. 顶部 Header 与导航栏
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = colors.textPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "📊 效能时钟与多维分析",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "24小时全息时钟盘 · 交付走势 · 分类占比 · 精力热力",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
            }
        }

        // 2. 🕒 每日 24 小时全息时钟盘卡片
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colors.card),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    // 日期切换操作栏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🕒 全息作息时钟",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }

                        // 日期切换器
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                onClick = {
                                    selectedDate = selectedDate.minusDays(1)
                                    selectedBlock = null
                                },
                                shape = CircleShape,
                                color = colors.surface,
                                border = BorderStroke(1.dp, colors.border),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronLeft,
                                        contentDescription = "前一天",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Surface(
                                onClick = {
                                    selectedDate = LocalDate.now()
                                    selectedBlock = null
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedDate == LocalDate.now()) colors.dopamineBlue.copy(alpha = 0.15f) else colors.surface,
                                border = BorderStroke(1.dp, if (selectedDate == LocalDate.now()) colors.dopamineBlue else colors.border),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Today,
                                        contentDescription = "今天",
                                        tint = if (selectedDate == LocalDate.now()) colors.dopamineBlue else colors.textSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "今天",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selectedDate == LocalDate.now()) colors.dopamineBlue else colors.textSecondary
                                    )
                                }
                            }

                            Surface(
                                onClick = {
                                    selectedDate = selectedDate.plusDays(1)
                                    selectedBlock = null
                                },
                                shape = CircleShape,
                                color = colors.surface,
                                border = BorderStroke(1.dp, colors.border),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "后一天",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 日期信息标签行
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedDate.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE")),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.dopamineBlue.copy(alpha = 0.12f),
                            border = BorderStroke(0.5.dp, colors.dopamineBlue.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "排期 ${dailyBlocks.size} 项 · %.1fh".format(totalHours),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.dopamineBlue,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 24小时矢量圆形表盘绘制
                    DailyClockDial(
                        selectedDate = selectedDate,
                        blocks = dailyBlocks,
                        selectedBlock = selectedBlock,
                        onSelectBlock = { selectedBlock = it }
                    )
                }
            }
        }

        // 3. 全天排期事项时序明细
        item {
            Text(
                text = "📋 当日事项时序清单 (${dailyBlocks.size} 项)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        }

        if (dailyBlocks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = colors.card),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🍃", fontSize = 28.sp)
                        Text(
                            text = "当日全天暂无排期日程或课程",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Text(
                            text = "可在日程表中为该日添加安排，时钟盘将自动同步映射",
                            fontSize = 11.sp,
                            color = colors.textMuted,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        } else {
            items(dailyBlocks, key = { it.id }) { block ->
                val isSelected = selectedBlock?.id == block.id
                val durMinutes = Duration.between(block.startTime, block.endTime).toMinutes()
                val blockColor = parseHexColor(block.colorHex, colors.dopamineBlue)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedBlock = if (isSelected) null else block
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) colors.cardHover else colors.card
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        if (isSelected) 1.5.dp else 1.dp,
                        if (isSelected) blockColor else colors.border
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 颜色指示条
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(38.dp)
                                .background(blockColor, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))

                        // 标题与分类标签
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = block.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                modifier = Modifier.padding(top = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val catTag = when (block.category) {
                                    "WORK" -> "💼 工作"
                                    "STUDY" -> "📚 学习"
                                    "HEALTH" -> "🏃 健康"
                                    "LIFE" -> "☕ 生活"
                                    "COURSE" -> "🎓 课程"
                                    else -> "📌 其它"
                                }
                                Text(catTag, fontSize = 10.sp, color = colors.textSecondary)

                                if (block.workType == "DEEP_WORK") {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = colors.dopaminePurple.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "🎯 深度专注",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.dopaminePurple,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 起止时间与时长
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${block.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))} - ${block.endTime.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "$durMinutes 分钟",
                                fontSize = 10.sp,
                                color = colors.textMuted,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. 📈 多维分析图表矩阵 (交付走势 + 分类环形 + 24h精力时段)
        item {
            Text(
                text = "📊 多维效能分析图表矩阵",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // 图表 1: 7天完成走势柱状图
        item {
            WeeklyCompletionTrendChart(schedules = schedules)
        }

        // 图表 2: 分类工时占比环形图
        item {
            CategoryDonutChart(blocks = dailyBlocks)
        }

        // 图表 3: 24小时时段精力热力柱状图
        item {
            HourlyEnergyHeatmap(blocks = dailyBlocks)
        }

        // 5. 核心看板指标
        item {
            Text(
                text = "🎯 核心履约看板",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = colors.card),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("全周期履约率", fontSize = 11.sp, color = colors.textMuted)
                        Text("$completionRate%", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.dopamineBlue, modifier = Modifier.padding(vertical = 3.dp))
                        Text("$completedSchedules / $totalSchedules 项已完成", fontSize = 10.sp, color = colors.textSecondary)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = colors.card),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("心流打断总耗时", fontSize = 11.sp, color = colors.textMuted)
                        Text("${interruptionMinutes}m", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.dopamineOrange, modifier = Modifier.padding(vertical = 3.dp))
                        Text("${interruptions.size} 次被打断记录", fontSize = 10.sp, color = colors.textSecondary)
                    }
                }
            }
        }

        // 6. 财务支出总览
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colors.card),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("累计支出记账", fontSize = 11.sp, color = colors.textMuted)
                        Text("￥%.2f".format(totalExpenseAmount), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.dopamineGreen, modifier = Modifier.padding(top = 2.dp))
                    }
                    Text("共计 ${expenses.size} 笔明细", fontSize = 11.sp, color = colors.textSecondary)
                }
            }
        }

        // 7. 心流打断原因透视
        item {
            Text("⚡ 心流打断原因透视", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colors.card),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val reasonGroups = interruptions.groupBy { it.note.ifBlank { it.type }.ifBlank { "未归类打断" } }
                    if (reasonGroups.isEmpty()) {
                        Text("暂无打断记录，专注状态良好！👏", fontSize = 12.sp, color = colors.dopamineGreen)
                    } else {
                        reasonGroups.forEach { (reason, list) ->
                            val reasonMins = list.sumOf { it.durationSeconds } / 60
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(reason, fontSize = 12.sp, color = colors.textPrimary)
                                Text("${list.size} 次 (${reasonMins}m)", fontSize = 12.sp, color = colors.dopamineOrange, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// =========================================================================
// 🕒 每日 24 小时全景作息矢量时钟盘 (Jetpack Compose Canvas 实现)
// =========================================================================
@Composable
fun DailyClockDial(
    selectedDate: LocalDate,
    blocks: List<ClockTimeBlock>,
    selectedBlock: ClockTimeBlock?,
    onSelectBlock: (ClockTimeBlock) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val totalHours = blocks.sumOf {
        Duration.between(it.startTime, it.endTime).toMinutes().toDouble() / 60.0
    }
    val totalCount = blocks.size

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = (size.height / 2f) - 20.dp.toPx()
            val trackWidth = 28.dp.toPx()
            val innerRadius = outerRadius - trackWidth
            val midRadius = (outerRadius + innerRadius) / 2f

            // 1. 表盘外轮廓背景圆盘
            drawCircle(
                color = if (colors.isDark) Color(0xFF13171F) else Color(0xFFF8FAFC),
                radius = outerRadius + 18.dp.toPx(),
                center = center
            )
            drawCircle(
                color = colors.border.copy(alpha = 0.8f),
                radius = outerRadius + 18.dp.toPx(),
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // 2. 24 小时刻度线与数字刻度
            val textPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = 9.sp.toPx()
            }

            for (h in 0 until 24) {
                val deg = -90f + h * 15f
                val rad = Math.toRadians(deg.toDouble())
                val cos = Math.cos(rad).toFloat()
                val sin = Math.sin(rad).toFloat()

                val isCardinal = (h == 0 || h == 6 || h == 12 || h == 18)
                val isEven = (h % 2 == 0)

                val rStart = if (isCardinal) outerRadius + 4.dp.toPx() else (if (isEven) outerRadius + 8.dp.toPx() else outerRadius + 11.dp.toPx())
                val rEnd = outerRadius + 16.dp.toPx()

                drawLine(
                    color = if (isCardinal) colors.dopamineBlue else (if (isEven) colors.textSecondary else colors.border.copy(alpha = 0.5f)),
                    start = Offset(center.x + rStart * cos, center.y + rStart * sin),
                    end = Offset(center.x + rEnd * cos, center.y + rEnd * sin),
                    strokeWidth = if (isCardinal) 2.dp.toPx() else (if (isEven) 1.2.dp.toPx() else 0.8.dp.toPx())
                )

                // 每隔 3 小时绘制时刻文字 (00, 03, 06, 09, 12, 15, 18, 21)
                if (h % 3 == 0) {
                    val nx = center.x + (rStart - 7.dp.toPx()) * cos
                    val ny = center.y + (rStart - 7.dp.toPx()) * sin + 3.dp.toPx()
                    textPaint.color = if (isCardinal) colors.dopamineBlue.toArgb() else colors.textMuted.toArgb()
                    textPaint.isFakeBoldText = isCardinal
                    drawContext.canvas.nativeCanvas.drawText("%02d".format(h), nx, ny, textPaint)
                }
            }

            // 3. 基础环形轨道底槽
            drawArc(
                color = if (colors.isDark) Color(0x353C4043) else Color(0x18000000),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(center.x - midRadius, center.y - midRadius),
                size = Size(midRadius * 2f, midRadius * 2f),
                style = Stroke(width = trackWidth)
            )

            // 4. 绘制当日各事项的弧形扇区
            blocks.forEach { b ->
                val sMin = b.startTime.hour * 60 + b.startTime.minute
                var eMin = b.endTime.hour * 60 + b.endTime.minute
                if (eMin <= sMin) eMin = minOf(1440, sMin + 30)
                if (eMin - sMin < 10) eMin = minOf(1440, sMin + 12)

                val aStart = -90f + sMin * 0.25f
                val aEnd = -90f + eMin * 0.25f
                val sweep = aEnd - aStart

                val blockColor = parseHexColor(b.colorHex, colors.dopamineBlue)
                val isSelected = b.id == selectedBlock?.id

                drawArc(
                    color = if (isSelected) blockColor else blockColor.copy(alpha = 0.88f),
                    startAngle = aStart,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(center.x - midRadius, center.y - midRadius),
                    size = Size(midRadius * 2f, midRadius * 2f),
                    style = Stroke(
                        width = if (isSelected) trackWidth + 5.dp.toPx() else trackWidth,
                        cap = StrokeCap.Round
                    )
                )
            }

            // 5. 若为今天，绘制实时当前时间红色指针
            if (selectedDate == LocalDate.now()) {
                val now = LocalTime.now()
                val nowMin = now.hour * 60 + now.minute + now.second / 60f
                val aNow = -90f + nowMin * 0.25f
                val radNow = Math.toRadians(aNow.toDouble())
                val cosNow = Math.cos(radNow).toFloat()
                val sinNow = Math.sin(radNow).toFloat()

                // 指针红线
                drawLine(
                    color = Color(0xFFEF4444),
                    start = Offset(center.x + (innerRadius - 2.dp.toPx()) * cosNow, center.y + (innerRadius - 2.dp.toPx()) * sinNow),
                    end = Offset(center.x + (outerRadius + 15.dp.toPx()) * cosNow, center.y + (outerRadius + 15.dp.toPx()) * sinNow),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // 尖端指示圆点
                drawCircle(
                    color = Color(0xFFEF4444),
                    radius = 3.5.dp.toPx(),
                    center = Offset(center.x + (outerRadius + 15.dp.toPx()) * cosNow, center.y + (outerRadius + 15.dp.toPx()) * sinNow)
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.5.dp.toPx(),
                    center = Offset(center.x + (outerRadius + 15.dp.toPx()) * cosNow, center.y + (outerRadius + 15.dp.toPx()) * sinNow),
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
        }

        // 表盘中心信息圆盘 (Center Disc)
        Surface(
            modifier = Modifier.size(116.dp),
            shape = CircleShape,
            color = colors.card,
            border = BorderStroke(1.5.dp, colors.border),
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (selectedBlock != null) {
                    Text(
                        text = "${selectedBlock.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))}-${selectedBlock.endTime.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.dopamineBlue
                    )
                    Text(
                        text = selectedBlock.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    val dur = Duration.between(selectedBlock.startTime, selectedBlock.endTime).toMinutes()
                    Text(
                        text = "${dur}m · ${if (selectedBlock.isCourse) "🎓 课程" else if (selectedBlock.workType == "DEEP_WORK") "🎯 深度" else "⚡ 事务"}",
                        fontSize = 9.5.sp,
                        color = colors.dopamineGreen
                    )
                } else {
                    Text(
                        text = if (selectedDate == LocalDate.now()) "今日作息" else "全天作息",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary
                    )
                    Text(
                        text = "%.1fh".format(totalHours),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.dopamineBlue,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                    Text(
                        text = "$totalCount 项日程",
                        fontSize = 10.5.sp,
                        color = colors.textMuted
                    )
                }
            }
        }
    }
}

// =========================================================================
// 📈 周期交付走势柱状图 (7-Day Completion Trend)
// =========================================================================
@Composable
fun WeeklyCompletionTrendChart(
    schedules: List<ScheduleEntity>,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val today = LocalDate.now()
    val past7Days = remember { (6 downTo 0).map { today.minusDays(it.toLong()) } }

    val dailyStats = remember(schedules) {
        past7Days.map { date ->
            val daySchedules = schedules.filter { s ->
                val st = parseSummaryDateTime(s.startTime)
                st?.toLocalDate() == date
            }
            val total = daySchedules.size
            val completed = daySchedules.count { it.status == "COMPLETED" }
            Triple(date, total, completed)
        }
    }

    val maxTotal = dailyStats.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    val totalDoneWeek = dailyStats.sumOf { it.third }
    val totalWeek = dailyStats.sumOf { it.second }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("📈 7日交付走势", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text("近 7 天累计完成 $totalDoneWeek / $totalWeek 项", fontSize = 11.sp, color = colors.textSecondary)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.dopamineGreen.copy(alpha = 0.15f)
                ) {
                    val rate = if (totalWeek > 0) (totalDoneWeek * 100 / totalWeek) else 0
                    Text(
                        text = "周达标率 $rate%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.dopamineGreen,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 7 根柱状图
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                dailyStats.forEach { (date, total, completed) ->
                    val isToday = date == today
                    val barHeightFraction = (total.toFloat() / maxTotal.toFloat()).coerceIn(0.08f, 1f)
                    val doneFraction = if (total > 0) (completed.toFloat() / total.toFloat()) else 0f

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            text = "$completed/$total",
                            fontSize = 9.sp,
                            color = if (isToday) colors.dopamineBlue else colors.textMuted,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        // 柱体
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height((70 * barHeightFraction).dp)
                                .background(colors.border.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            if (total > 0 && completed > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(doneFraction)
                                        .background(
                                            if (isToday) colors.dopamineBlue else colors.dopamineGreen,
                                            RoundedCornerShape(4.dp)
                                        )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        val weekdayName = when (date.dayOfWeek.value) {
                            1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"; 6 -> "六"; else -> "日"
                        }
                        Text(
                            text = weekdayName,
                            fontSize = 11.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) colors.dopamineBlue else colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// 🍩 分类工时环形图 & 洞察 (Category Donut Chart)
// =========================================================================
@Composable
fun CategoryDonutChart(
    blocks: List<ClockTimeBlock>,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current

    val categoryHours = remember(blocks) {
        var workH = 0.0
        var studyH = 0.0
        var healthH = 0.0
        var lifeH = 0.0
        var courseH = 0.0
        var otherH = 0.0

        for (b in blocks) {
            val dur = Duration.between(b.startTime, b.endTime).toMinutes().toDouble() / 60.0
            when (b.category) {
                "WORK" -> workH += dur
                "STUDY" -> studyH += dur
                "HEALTH" -> healthH += dur
                "LIFE" -> lifeH += dur
                "COURSE" -> courseH += dur
                else -> otherH += dur
            }
        }
        listOf(
            Triple("工作", workH, Color(0xFF3B82F6)),
            Triple("学习", studyH, Color(0xFF8B5CF6)),
            Triple("课程", courseH, Color(0xFF06B6D4)),
            Triple("健康", healthH, Color(0xFF10B981)),
            Triple("生活", lifeH, Color(0xFFF59E0B)),
            Triple("其它", otherH, Color(0xFF6B7280))
        )
    }

    val totalCatHours = categoryHours.sumOf { it.second }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("🍩 分类工时占比", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧环形 Canvas
                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeW = 16.dp.toPx()
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = (size.minDimension - strokeW) / 2f

                        if (totalCatHours <= 0) {
                            drawCircle(
                                color = colors.border.copy(alpha = 0.5f),
                                radius = radius,
                                center = center,
                                style = Stroke(width = strokeW)
                            )
                        } else {
                            var startAngle = -90f
                            for ((_, h, c) in categoryHours) {
                                if (h <= 0) continue
                                val sweep = (h / totalCatHours * 360.0).toFloat()
                                drawArc(
                                    color = c,
                                    startAngle = startAngle,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    topLeft = Offset(center.x - radius, center.y - radius),
                                    size = Size(radius * 2, radius * 2),
                                    style = Stroke(width = strokeW)
                                )
                                startAngle += sweep
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("总工时", fontSize = 9.sp, color = colors.textMuted)
                        Text("%.1fh".format(totalCatHours), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // 右侧图例列表
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    categoryHours.filter { it.second > 0 }.forEach { (name, h, col) ->
                        val pct = if (totalCatHours > 0) (h * 100 / totalCatHours).toInt() else 0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(col, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(name, fontSize = 11.sp, color = colors.textPrimary)
                            }
                            Text("%.1fh ($pct%)".format(h), fontSize = 11.sp, color = colors.textSecondary)
                        }
                    }

                    if (categoryHours.none { it.second > 0 }) {
                        Text("当日暂无分类排期工时", fontSize = 11.sp, color = colors.textMuted)
                    }
                }
            }

            // 智能洞察说明
            Spacer(modifier = Modifier.height(10.dp))
            val insight = when {
                totalCatHours <= 0 -> "🌱 当前日期暂无排期，添加日程后自动洞察精力分布。"
                (categoryHours[0].second + categoryHours[1].second + categoryHours[2].second) / totalCatHours >= 0.6 ->
                    "💡 工作与学业占比超 60%，专注精力高度聚焦于主航道！"
                (categoryHours[3].second + categoryHours[4].second) / totalCatHours >= 0.5 ->
                    "☕ 生活与身心投入充足，劳逸结合状态佳！"
                else -> "💡 各分类投入均衡，作息与成长节奏平稳！"
            }
            Text(
                text = insight,
                fontSize = 11.sp,
                color = colors.dopamineBlue,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// =========================================================================
// ⚡ 24 小时时段精力与排期分布 (Hourly Energy Heatmap)
// =========================================================================
@Composable
fun HourlyEnergyHeatmap(
    blocks: List<ClockTimeBlock>,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current

    val (hourlyMins, morningM, afternoonM, eveningM) = remember(blocks) {
        val arr = DoubleArray(24)
        var morning = 0.0
        var afternoon = 0.0
        var evening = 0.0

        for (b in blocks) {
            val sMin = b.startTime.hour * 60 + b.startTime.minute
            var eMin = b.endTime.hour * 60 + b.endTime.minute
            if (eMin <= sMin) eMin = minOf(1440, sMin + 30)

            for (h in 0 until 24) {
                val slotS = h * 60
                val slotE = (h + 1) * 60
                val oS = maxOf(sMin, slotS)
                val oE = minOf(eMin, slotE)
                if (oE > oS) {
                    val dur = (oE - oS).toDouble()
                    arr[h] += dur
                    if (h in 8..11) morning += dur
                    else if (h in 13..17) afternoon += dur
                    else if (h in 19..23) evening += dur
                }
            }
        }
        listOf(arr, doubleArrayOf(morning), doubleArrayOf(afternoon), doubleArrayOf(evening))
    }

    val maxM = (hourlyMins as DoubleArray).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("⚡ 24小时时段排期热力", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Spacer(modifier = Modifier.height(10.dp))

            // 黄金精力时段指示指标
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val mH = (morningM as DoubleArray)[0] / 60.0
                val aH = (afternoonM as DoubleArray)[0] / 60.0
                val eH = (eveningM as DoubleArray)[0] / 60.0

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.surface,
                    border = BorderStroke(0.5.dp, colors.border),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("早间 (8-12)", fontSize = 10.sp, color = colors.textMuted)
                        Text("%.1fh".format(mH), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.dopamineBlue)
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.surface,
                    border = BorderStroke(0.5.dp, colors.border),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("午间 (13-18)", fontSize = 10.sp, color = colors.textMuted)
                        Text("%.1fh".format(aH), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.dopamineOrange)
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.surface,
                    border = BorderStroke(0.5.dp, colors.border),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("晚间 (19-23)", fontSize = 10.sp, color = colors.textMuted)
                        Text("%.1fh".format(eH), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.dopaminePurple)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 24根细柱
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                for (h in 0 until 24) {
                    val m = hourlyMins[h]
                    val fraction = if (m > 0) (m / maxM).toFloat().coerceIn(0.1f, 1f) else 0.05f

                    val barColor = when {
                        m == 0.0 -> colors.border.copy(alpha = 0.3f)
                        m == maxM && maxM > 0 -> colors.dopamineGreen // 峰值翡翠绿
                        m >= 0.6 * maxM -> colors.dopaminePurple     // 高能薰衣草紫
                        else -> colors.dopamineBlue                  // 经典蓝
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 0.8.dp)
                            .height((50 * fraction).dp)
                            .background(barColor, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    )
                }
            }

            // 底部 0, 6, 12, 18, 23 刻度标签
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("00", fontSize = 9.sp, color = colors.textMuted)
                Text("06", fontSize = 9.sp, color = colors.textMuted)
                Text("12", fontSize = 9.sp, color = colors.textMuted)
                Text("18", fontSize = 9.sp, color = colors.textMuted)
                Text("24", fontSize = 9.sp, color = colors.textMuted)
            }
        }
    }
}
