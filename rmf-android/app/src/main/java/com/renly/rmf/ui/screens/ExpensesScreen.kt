package com.renly.rmf.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.data.local.entity.ExpenseEntity
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.regex.Pattern

private val PRESET_EXPENSE_CATEGORIES = listOf("餐饮", "交通", "购物", "日常", "学习", "娱乐", "医疗", "居住", "人情", "其他")
private val PRESET_INCOME_CATEGORIES = listOf("工资", "奖金", "兼职", "理财", "红包", "退款", "其他")

private val CATEGORY_COLORS = listOf(
    Color(0xFFEF4444), // 赤红
    Color(0xFFF97316), // 橙色
    Color(0xFFF59E0B), // 琥珀
    Color(0xFF10B981), // 翠绿
    Color(0xFF06B6D4), // 青蓝
    Color(0xFF3B82F6), // 核心蓝
    Color(0xFF8B5CF6), // 紫色
    Color(0xFFEC4899), // 粉红
    Color(0xFF14B8A6), // 薄荷
    Color(0xFF64748B)  // 灰蓝
)

private fun getCategoryEmoji(category: String, type: String): String {
    return when {
        category.contains("餐") || category.contains("吃") || category.contains("饭") || category.contains("食") || category.contains("外卖") -> "🍜"
        category.contains("车") || category.contains("交通") || category.contains("行") || category.contains("油") || category.contains("地铁") -> "🚗"
        category.contains("购") || category.contains("买") || category.contains("服") -> "🛍️"
        category.contains("学") || category.contains("书") || category.contains("课") -> "📚"
        category.contains("玩") || category.contains("娱") || category.contains("游") || category.contains("影") -> "🎮"
        category.contains("医") || category.contains("药") || category.contains("病") -> "💊"
        category.contains("住") || category.contains("房") || category.contains("租") || category.contains("水") || category.contains("电") -> "🏠"
        category.contains("情") || category.contains("礼") -> "🎁"
        category.contains("资") || category.contains("薪") -> "💰"
        category.contains("奖") -> "🏆"
        category.contains("兼") -> "💼"
        category.contains("理财") || category.contains("投资") || category.contains("股") -> "📈"
        category.contains("包") -> "🧧"
        category.contains("退") -> "🔄"
        type == "INCOME" -> "💵"
        else -> "🛒"
    }
}

enum class ExpensePeriodMode(val title: String) {
    DAY("日"),
    WEEK("周"),
    MONTH("月"),
    YEAR("年"),
    CUSTOM("自定义")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    expensesFlow: Flow<List<ExpenseEntity>>,
    onAddExpense: (ExpenseEntity) -> Unit,
    onUpdateExpense: (ExpenseEntity) -> Unit = {},
    onDeleteExpense: (String) -> Unit = {}
) {
    val expenses by expensesFlow.collectAsState(initial = emptyList())
    var quickInputText by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf("ALL") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<ExpenseEntity?>(null) }
    var deletingExpense by remember { mutableStateOf<ExpenseEntity?>(null) }

    // 周期视图模式：日、周、月、年、自定义
    var currentPeriodMode by remember { mutableStateOf(ExpensePeriodMode.MONTH) }

    // 各周期选中的基准时间状态
    var selectedDay by remember { mutableStateOf(LocalDate.now()) }
    var selectedWeekMonday by remember {
        mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
    }
    var selectedYearMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedYear by remember { mutableIntStateOf(LocalDate.now().year) }
    var customStartDate by remember { mutableStateOf(LocalDate.now().minusDays(29)) }
    var customEndDate by remember { mutableStateOf(LocalDate.now()) }

    // 子展示模式：图表分析 vs 账单明细
    var showChartDashboard by remember { mutableStateOf(true) }

    // 日期选择弹窗状态
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var isSelectingCustomStart by remember { mutableStateOf(true) }

    // 辅助解析交易日期的 LocalDate
    fun parseTxDate(txTime: String): LocalDate? {
        return try {
            LocalDateTime.parse(txTime).toLocalDate()
        } catch (e: Exception) {
            try {
                LocalDate.parse(txTime.take(10))
            } catch (e2: Exception) {
                null
            }
        }
    }

    // 根据选定的周期模式过滤出的账目
    val periodFilteredExpenses = remember(
        expenses, currentPeriodMode, selectedDay, selectedWeekMonday,
        selectedYearMonth, selectedYear, customStartDate, customEndDate
    ) {
        expenses.filter { item ->
            val date = parseTxDate(item.transactionTime) ?: return@filter false
            when (currentPeriodMode) {
                ExpensePeriodMode.DAY -> date == selectedDay
                ExpensePeriodMode.WEEK -> {
                    val endSunday = selectedWeekMonday.plusDays(6)
                    !date.isBefore(selectedWeekMonday) && !date.isAfter(endSunday)
                }
                ExpensePeriodMode.MONTH -> {
                    YearMonth.from(date) == selectedYearMonth
                }
                ExpensePeriodMode.YEAR -> {
                    date.year == selectedYear
                }
                ExpensePeriodMode.CUSTOM -> {
                    !date.isBefore(customStartDate) && !date.isAfter(customEndDate)
                }
            }
        }
    }

    // 按收支类型（全部、支出、收入）进一步过滤
    val finalDisplayedExpenses = remember(periodFilteredExpenses, selectedTypeFilter) {
        if (selectedTypeFilter == "ALL") periodFilteredExpenses
        else periodFilteredExpenses.filter { it.type == selectedTypeFilter }
    }

    // 统计当前周期的收支指标
    val periodTotalExpense = periodFilteredExpenses.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val periodTotalIncome = periodFilteredExpenses.filter { it.type == "INCOME" }.sumOf { it.amount }
    val periodBalance = periodTotalIncome - periodTotalExpense

    // 计算当前周期的天数用于日均支出统计
    val periodDays = remember(currentPeriodMode, selectedYearMonth, selectedYear, customStartDate, customEndDate) {
        when (currentPeriodMode) {
            ExpensePeriodMode.DAY -> 1
            ExpensePeriodMode.WEEK -> 7
            ExpensePeriodMode.MONTH -> selectedYearMonth.lengthOfMonth()
            ExpensePeriodMode.YEAR -> if (LocalDate.of(selectedYear, 1, 1).isLeapYear) 366 else 365
            ExpensePeriodMode.CUSTOM -> (ChronoUnit.DAYS.between(customStartDate, customEndDate) + 1).coerceAtLeast(1).toInt()
        }
    }
    val averageDailyExpense = if (periodDays > 0) periodTotalExpense / periodDays else 0.0

    Scaffold(
        containerColor = DarkBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = DopamineAmber,
                contentColor = DarkBg,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "记一笔", modifier = Modifier.size(24.dp))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // 1. 顶部标题栏 + 周期模式切换药丸胶囊
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "财务记账与分析",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "多维周期视图 · 智能图表分析 · 极简记账",
                        fontSize = 11.5.sp,
                        color = TextSecondary
                    )
                }

                // 日 / 周 / 月 / 年 / 自定义 切换药丸
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.height(34.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExpensePeriodMode.entries.forEach { mode ->
                            val isSel = currentPeriodMode == mode
                            Surface(
                                onClick = { currentPeriodMode = mode },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSel) DopamineAmber.copy(alpha = 0.22f) else Color.Transparent,
                                border = BorderStroke(1.dp, if (isSel) DopamineAmber else Color.Transparent),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = mode.title,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) DopamineAmber else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. 周期时间导航切换条 (支持前一周期 / 后一周期 / 回当前 / 点选日期)
            PeriodNavigatorCard(
                mode = currentPeriodMode,
                selectedDay = selectedDay,
                selectedWeekMonday = selectedWeekMonday,
                selectedYearMonth = selectedYearMonth,
                selectedYear = selectedYear,
                customStartDate = customStartDate,
                customEndDate = customEndDate,
                onPrev = {
                    when (currentPeriodMode) {
                        ExpensePeriodMode.DAY -> selectedDay = selectedDay.minusDays(1)
                        ExpensePeriodMode.WEEK -> selectedWeekMonday = selectedWeekMonday.minusWeeks(1)
                        ExpensePeriodMode.MONTH -> selectedYearMonth = selectedYearMonth.minusMonths(1)
                        ExpensePeriodMode.YEAR -> selectedYear--
                        ExpensePeriodMode.CUSTOM -> {
                            val range = ChronoUnit.DAYS.between(customStartDate, customEndDate)
                            customEndDate = customStartDate.minusDays(1)
                            customStartDate = customEndDate.minusDays(range)
                        }
                    }
                },
                onNext = {
                    when (currentPeriodMode) {
                        ExpensePeriodMode.DAY -> selectedDay = selectedDay.plusDays(1)
                        ExpensePeriodMode.WEEK -> selectedWeekMonday = selectedWeekMonday.plusWeeks(1)
                        ExpensePeriodMode.MONTH -> selectedYearMonth = selectedYearMonth.plusMonths(1)
                        ExpensePeriodMode.YEAR -> selectedYear++
                        ExpensePeriodMode.CUSTOM -> {
                            val range = ChronoUnit.DAYS.between(customStartDate, customEndDate)
                            customStartDate = customEndDate.plusDays(1)
                            customEndDate = customStartDate.plusDays(range)
                        }
                    }
                },
                onResetToday = {
                    selectedDay = LocalDate.now()
                    selectedWeekMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    selectedYearMonth = YearMonth.now()
                    selectedYear = LocalDate.now().year
                    customStartDate = LocalDate.now().minusDays(29)
                    customEndDate = LocalDate.now()
                },
                onSelectCustomPreset = { start, end ->
                    customStartDate = start
                    customEndDate = end
                },
                onPickDate = { showDatePickerDialog = true }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. 当前周期收支汇总仪表盘卡片
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("支出", fontSize = 11.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "¥${"%.2f".format(periodTotalExpense)}",
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = DopamineRed
                            )
                        }

                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(DarkBorder))

                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("收入", fontSize = 11.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "¥${"%.2f".format(periodTotalIncome)}",
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = DopamineGreen
                            )
                        }

                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(DarkBorder))

                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("结余", fontSize = 11.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "¥${"%.2f".format(periodBalance)}",
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (periodBalance >= 0) DopamineCyan else DopamineAmber
                            )
                        }

                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(DarkBorder))

                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(if (currentPeriodMode == ExpensePeriodMode.YEAR) "月均支出" else "日均支出", fontSize = 11.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "¥${"%.2f".format(if (currentPeriodMode == ExpensePeriodMode.YEAR) periodTotalExpense / 12 else averageDailyExpense)}",
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. 自然语言快捷智能记账条
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, DopamineAmber.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 1.dp)
                ) {
                    TextField(
                        value = quickInputText,
                        onValueChange = { quickInputText = it },
                        placeholder = { Text("智能记账: 如 午餐 28.5 / 打车 35 / 兼职 500", fontSize = 12.sp, color = TextMuted) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            if (quickInputText.isNotBlank()) {
                                val parsed = parseQuickExpense(quickInputText)
                                onAddExpense(parsed)
                                quickInputText = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "智能记账", tint = DopamineAmber, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 5. 视图切换与收支类型筛选工具栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 收支筛选 chips (全部 / 支出 / 收入)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL" to "全部 (${periodFilteredExpenses.size})", "EXPENSE" to "支出 (${periodFilteredExpenses.count { it.type == "EXPENSE" }})", "INCOME" to "收入 (${periodFilteredExpenses.count { it.type == "INCOME" }})").forEach { (code, label) ->
                        val isSel = selectedTypeFilter == code
                        Surface(
                            onClick = { selectedTypeFilter = code },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) (when(code) { "EXPENSE" -> DopamineRed.copy(alpha = 0.2f); "INCOME" -> DopamineGreen.copy(alpha = 0.2f); else -> DopamineAmber.copy(alpha = 0.2f) }) else DarkSurface,
                            border = BorderStroke(1.dp, if (isSel) (when(code) { "EXPENSE" -> DopamineRed; "INCOME" -> DopamineGreen; else -> DopamineAmber }) else DarkBorder),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 7.dp)) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) (when(code) { "EXPENSE" -> DopamineRed; "INCOME" -> DopamineGreen; else -> DopamineAmber }) else TextSecondary
                                )
                            }
                        }
                    }
                }

                // 图表分析 / 明细列表 切换按钮
                Surface(
                    onClick = { showChartDashboard = !showChartDashboard },
                    shape = RoundedCornerShape(8.dp),
                    color = if (showChartDashboard) DopamineCyan.copy(alpha = 0.18f) else DarkSurface,
                    border = BorderStroke(1.dp, if (showChartDashboard) DopamineCyan else DarkBorder),
                    modifier = Modifier.height(28.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (showChartDashboard) Icons.Default.ViewList else Icons.Default.BarChart,
                            contentDescription = "切换视图",
                            tint = if (showChartDashboard) DopamineCyan else TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (showChartDashboard) "看明细" else "看图表",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (showChartDashboard) DopamineCyan else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 6. 主内容区：图表分析面板 或 账单明细列表
            if (showChartDashboard) {
                // 📊 专业图表分析面板
                ExpenseChartAnalysisView(
                    mode = currentPeriodMode,
                    expenses = periodFilteredExpenses,
                    totalExpense = periodTotalExpense,
                    totalIncome = periodTotalIncome,
                    selectedWeekMonday = selectedWeekMonday,
                    selectedYearMonth = selectedYearMonth,
                    selectedYear = selectedYear,
                    modifier = Modifier.weight(1f)
                )
            } else {
                // 📋 账目明细列表
                if (finalDisplayedExpenses.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🧾", fontSize = 36.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("当前周期暂无明细记录", fontSize = 13.sp, color = TextMuted)
                            Text("可点击右上角「看图表」或点击右下角「+」记账", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(finalDisplayedExpenses, key = { it.id }) { item ->
                            ExpenseItemCard(
                                item = item,
                                onEdit = { editingExpense = item },
                                onDelete = { deletingExpense = item }
                            )
                        }
                    }
                }
            }
        }

        // 新增账目弹窗
        if (showAddDialog) {
            AddExpenseDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { newExpense ->
                    onAddExpense(newExpense)
                    showAddDialog = false
                }
            )
        }

        // 修改账目弹窗
        if (editingExpense != null) {
            EditExpenseDialog(
                expense = editingExpense!!,
                onDismiss = { editingExpense = null },
                onSave = { updated ->
                    onUpdateExpense(updated)
                    editingExpense = null
                },
                onDelete = {
                    deletingExpense = editingExpense
                    editingExpense = null
                }
            )
        }

        // 删除二次确认弹窗
        if (deletingExpense != null) {
            DeleteExpenseConfirmDialog(
                expense = deletingExpense!!,
                onDismiss = { deletingExpense = null },
                onConfirm = {
                    onDeleteExpense(deletingExpense!!.id)
                    deletingExpense = null
                }
            )
        }

        // 简易日期选择对话框 (DatePickerDialog)
        if (showDatePickerDialog) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = selectedDay.atStartOfDay(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { showDatePickerDialog = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                val picked = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                                when (currentPeriodMode) {
                                    ExpensePeriodMode.DAY -> selectedDay = picked
                                    ExpensePeriodMode.WEEK -> selectedWeekMonday = picked.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                                    ExpensePeriodMode.MONTH -> selectedYearMonth = YearMonth.from(picked)
                                    ExpensePeriodMode.YEAR -> selectedYear = picked.year
                                    ExpensePeriodMode.CUSTOM -> {
                                        if (isSelectingCustomStart) customStartDate = picked else customEndDate = picked
                                    }
                                }
                            }
                            showDatePickerDialog = false
                        }
                    ) { Text("确定", color = DopamineAmber, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePickerDialog = false }) { Text("取消", color = TextSecondary) }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

/**
 * 周期时间导航卡片 (日/周/月/年/自定义)
 */
@Composable
private fun PeriodNavigatorCard(
    mode: ExpensePeriodMode,
    selectedDay: LocalDate,
    selectedWeekMonday: LocalDate,
    selectedYearMonth: YearMonth,
    selectedYear: Int,
    customStartDate: LocalDate,
    customEndDate: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onResetToday: () -> Unit,
    onSelectCustomPreset: (LocalDate, LocalDate) -> Unit,
    onPickDate: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, DarkBorder),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrev, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "上一个周期", tint = TextPrimary, modifier = Modifier.size(18.dp))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val label = when (mode) {
                        ExpensePeriodMode.DAY -> {
                            val isToday = selectedDay == LocalDate.now()
                            "${selectedDay.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))} · ${getDayOfWeekChinese(selectedDay.dayOfWeek)}" + if (isToday) " (今天)" else ""
                        }
                        ExpensePeriodMode.WEEK -> {
                            val sunday = selectedWeekMonday.plusDays(6)
                            "${selectedWeekMonday.year}年 ${selectedWeekMonday.monthValue}/${selectedWeekMonday.dayOfMonth} - ${sunday.monthValue}/${sunday.dayOfMonth}"
                        }
                        ExpensePeriodMode.MONTH -> {
                            "${selectedYearMonth.year}年 ${selectedYearMonth.monthValue}月"
                        }
                        ExpensePeriodMode.YEAR -> {
                            "${selectedYear}年"
                        }
                        ExpensePeriodMode.CUSTOM -> {
                            "${customStartDate.monthValue}/${customStartDate.dayOfMonth} ~ ${customEndDate.monthValue}/${customEndDate.dayOfMonth}"
                        }
                    }

                    Text(
                        text = label,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = DopamineAmber,
                        modifier = Modifier.clickable { onPickDate() }
                    )

                    // 回当前按钮
                    val isCurrent = when (mode) {
                        ExpensePeriodMode.DAY -> selectedDay == LocalDate.now()
                        ExpensePeriodMode.WEEK -> selectedWeekMonday == LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        ExpensePeriodMode.MONTH -> selectedYearMonth == YearMonth.now()
                        ExpensePeriodMode.YEAR -> selectedYear == LocalDate.now().year
                        ExpensePeriodMode.CUSTOM -> false
                    }
                    if (!isCurrent && mode != ExpensePeriodMode.CUSTOM) {
                        Surface(
                            onClick = onResetToday,
                            shape = RoundedCornerShape(4.dp),
                            color = DopamineAmber.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "回今日",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = DopamineAmber,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                IconButton(onClick = onNext, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "下一个周期", tint = TextPrimary, modifier = Modifier.size(18.dp))
                }
            }

            // 自定义视图快捷时间段药丸 (近7天, 近30天, 本月至今, 近90天)
            if (mode == ExpensePeriodMode.CUSTOM) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val today = LocalDate.now()
                    val presets = listOf(
                        "近7天" to Pair(today.minusDays(6), today),
                        "近30天" to Pair(today.minusDays(29), today),
                        "本月" to Pair(today.withDayOfMonth(1), today),
                        "近90天" to Pair(today.minusDays(89), today)
                    )
                    presets.forEach { (name, range) ->
                        val isSel = customStartDate == range.first && customEndDate == range.second
                        Surface(
                            onClick = { onSelectCustomPreset(range.first, range.second) },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSel) DopamineAmber.copy(alpha = 0.22f) else DarkSurface,
                            border = BorderStroke(0.5.dp, if (isSel) DopamineAmber else DarkBorder),
                            modifier = Modifier.weight(1f).height(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = name,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) DopamineAmber else TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getDayOfWeekChinese(dow: DayOfWeek): String {
    return when (dow) {
        DayOfWeek.MONDAY -> "周一"
        DayOfWeek.TUESDAY -> "周二"
        DayOfWeek.WEDNESDAY -> "周三"
        DayOfWeek.THURSDAY -> "周四"
        DayOfWeek.FRIDAY -> "周五"
        DayOfWeek.SATURDAY -> "周六"
        DayOfWeek.SUNDAY -> "周日"
    }
}

/**
 * 📊 智能图表分析视图
 * 包含：
 * 1. 周期收支走势柱状图 (Week: 7天对比, Month: 走势柱状, Year: 12个月收支对比)
 * 2. 支出分类占比环形饼图与排行榜
 * 3. 收支健康度比例条
 */
@Composable
private fun ExpenseChartAnalysisView(
    mode: ExpensePeriodMode,
    expenses: List<ExpenseEntity>,
    totalExpense: Double,
    totalIncome: Double,
    selectedWeekMonday: LocalDate,
    selectedYearMonth: YearMonth,
    selectedYear: Int,
    modifier: Modifier = Modifier
) {
    val expenseList = remember(expenses) { expenses.filter { it.type == "EXPENSE" } }

    // 分类统计聚合与排序
    val categoryTotals = remember(expenseList) {
        expenseList.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. 趋势柱状图卡片
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            border = BorderStroke(1.dp, DarkBorder),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (mode) {
                            ExpensePeriodMode.DAY -> "📅 当天小时分布"
                            ExpensePeriodMode.WEEK -> "📈 本周每日支出走势"
                            ExpensePeriodMode.MONTH -> "📈 当月每日支出走势"
                            ExpensePeriodMode.YEAR -> "📊 全年各月收支对比"
                            ExpensePeriodMode.CUSTOM -> "📈 自定义区间走势"
                        },
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "单位: 元",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (mode) {
                    ExpensePeriodMode.WEEK -> {
                        WeekBarTrendChart(
                            weekMonday = selectedWeekMonday,
                            expenses = expenseList
                        )
                    }
                    ExpensePeriodMode.MONTH -> {
                        MonthBarTrendChart(
                            yearMonth = selectedYearMonth,
                            expenses = expenseList
                        )
                    }
                    ExpensePeriodMode.YEAR -> {
                        YearDualBarTrendChart(
                            year = selectedYear,
                            expenses = expenses
                        )
                    }
                    else -> {
                        GeneralBarTrendChart(expenses = expenseList)
                    }
                }
            }
        }

        // 2. 分类占比环形饼图与排行榜卡片
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            border = BorderStroke(1.dp, DarkBorder),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "🍩 支出分类占比分析",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (categoryTotals.isEmpty() || totalExpense <= 0.0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("当前周期暂无支出记录，轻松无账单 🍃", fontSize = 12.sp, color = TextMuted)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 环形图 Canvas
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            DonutChartCanvas(
                                categoryTotals = categoryTotals,
                                totalExpense = totalExpense,
                                modifier = Modifier.fillMaxSize()
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("总支出", fontSize = 9.sp, color = TextMuted)
                                Text(
                                    text = "¥${totalExpense.toInt()}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = DopamineAmber
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // 分类图例与进度排行 (前 5 名)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            categoryTotals.take(5).forEachIndexed { idx, (cat, amt) ->
                                val pct = if (totalExpense > 0) (amt / totalExpense * 100).toInt() else 0
                                val col = CATEGORY_COLORS[idx % CATEGORY_COLORS.size]
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(col)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${getCategoryEmoji(cat, "EXPENSE")} $cat",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "¥${"%.1f".format(amt)} ($pct%)",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondary
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (pct / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = col,
                                    trackColor = DarkBorder
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. 收支比率与节余健康度仪表
        if (totalIncome > 0 || totalExpense > 0) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                border = BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "⚖️ 收支平衡与储蓄健康度",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val totalFlow = (totalIncome + totalExpense).coerceAtLeast(1.0)
                    val expenseRatio = (totalExpense / totalFlow).toFloat().coerceIn(0f, 1f)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "支出占比: ${(expenseRatio * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DopamineRed
                        )
                        Text(
                            text = "收入结余: ${((1f - expenseRatio) * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DopamineGreen
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(expenseRatio.coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .background(DopamineRed)
                        )
                        Box(
                            modifier = Modifier
                                .weight((1f - expenseRatio).coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .background(DopamineGreen)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

/**
 * 周视图 7 天柱状图 (周一至周日)
 */
@Composable
private fun WeekBarTrendChart(
    weekMonday: LocalDate,
    expenses: List<ExpenseEntity>
) {
    val daysOfWeek = listOf("一", "二", "三", "四", "五", "六", "日")
    val dailyTotals = (0..6).map { dayOffset ->
        val date = weekMonday.plusDays(dayOffset.toLong())
        val total = expenses.filter {
            try {
                LocalDateTime.parse(it.transactionTime).toLocalDate() == date
            } catch (e: Exception) {
                false
            }
        }.sumOf { it.amount }
        Pair(date, total)
    }

    val maxAmount = dailyTotals.maxOfOrNull { it.second }?.coerceAtLeast(10.0) ?: 100.0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        dailyTotals.forEachIndexed { idx, (date, total) ->
            val isToday = date == LocalDate.now()
            val heightFraction = (total / maxAmount).toFloat().coerceIn(0.04f, 1f)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                if (total > 0) {
                    Text(
                        text = "¥${total.toInt()}",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isToday) DopamineCyan else TextSecondary,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxHeight(heightFraction * 0.78f)
                        .width(16.dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (isToday) DopamineCyan else if (total > 0) DopamineAmber else DarkBorder.copy(alpha = 0.5f))
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "周${daysOfWeek[idx]}",
                    fontSize = 10.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) DopamineCyan else TextMuted
                )
                Text(
                    text = "${date.dayOfMonth}日",
                    fontSize = 8.sp,
                    color = if (isToday) DopamineCyan else TextMuted
                )
            }
        }
    }
}

/**
 * 月视图 每日支出走势柱状图
 */
@Composable
private fun MonthBarTrendChart(
    yearMonth: YearMonth,
    expenses: List<ExpenseEntity>
) {
    val daysCount = yearMonth.lengthOfMonth()
    val dailyTotals = (1..daysCount).map { day ->
        val total = expenses.filter {
            try {
                val dt = LocalDateTime.parse(it.transactionTime).toLocalDate()
                dt.year == yearMonth.year && dt.monthValue == yearMonth.monthValue && dt.dayOfMonth == day
            } catch (e: Exception) {
                false
            }
        }.sumOf { it.amount }
        Pair(day, total)
    }

    val maxAmount = dailyTotals.maxOfOrNull { it.second }?.coerceAtLeast(10.0) ?: 100.0

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            dailyTotals.forEach { (day, total) ->
                val isToday = (yearMonth == YearMonth.now() && day == LocalDate.now().dayOfMonth)
                val heightFraction = (total / maxAmount).toFloat().coerceIn(0.03f, 1f)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(heightFraction * 0.85f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(if (isToday) DopamineCyan else if (total > 0) DopamineAmber else DarkBorder.copy(alpha = 0.4f))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("1日", fontSize = 9.sp, color = TextMuted)
            Text("10日", fontSize = 9.sp, color = TextMuted)
            Text("20日", fontSize = 9.sp, color = TextMuted)
            Text("${daysCount}日", fontSize = 9.sp, color = TextMuted)
        }
    }
}

/**
 * 年视图 12 个月双柱状图 (支出与收入对比)
 */
@Composable
private fun YearDualBarTrendChart(
    year: Int,
    expenses: List<ExpenseEntity>
) {
    val monthlyData = (1..12).map { month ->
        val expenseTotal = expenses.filter {
            try {
                val dt = LocalDateTime.parse(it.transactionTime).toLocalDate()
                dt.year == year && dt.monthValue == month && it.type == "EXPENSE"
            } catch (e: Exception) {
                false
            }
        }.sumOf { it.amount }

        val incomeTotal = expenses.filter {
            try {
                val dt = LocalDateTime.parse(it.transactionTime).toLocalDate()
                dt.year == year && dt.monthValue == month && it.type == "INCOME"
            } catch (e: Exception) {
                false
            }
        }.sumOf { it.amount }

        Triple(month, expenseTotal, incomeTotal)
    }

    val maxAmount = monthlyData.maxOfOrNull { maxOf(it.second, it.third) }?.coerceAtLeast(10.0) ?: 100.0

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            monthlyData.forEach { (month, exp, inc) ->
                val expFraction = (exp / maxAmount).toFloat().coerceIn(0.04f, 1f)
                val incFraction = (inc / maxAmount).toFloat().coerceIn(0.04f, 1f)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(1.5.dp)
                    ) {
                        // 支出柱 (赤红)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight(expFraction * 0.85f)
                                .width(7.dp)
                                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                .background(if (exp > 0) DopamineRed else DarkBorder.copy(alpha = 0.4f))
                        )
                        // 收入柱 (翠绿)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight(incFraction * 0.85f)
                                .width(7.dp)
                                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                .background(if (inc > 0) DopamineGreen else DarkBorder.copy(alpha = 0.4f))
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${month}月",
                        fontSize = 9.sp,
                        color = TextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(DopamineRed))
            Spacer(modifier = Modifier.width(3.dp))
            Text("支出", fontSize = 10.sp, color = TextSecondary)
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(DopamineGreen))
            Spacer(modifier = Modifier.width(3.dp))
            Text("收入", fontSize = 10.sp, color = TextSecondary)
        }
    }
}

/**
 * 通用柱状图 (用于日视图/自定义视图)
 */
@Composable
private fun GeneralBarTrendChart(expenses: List<ExpenseEntity>) {
    if (expenses.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("暂无数据", fontSize = 12.sp, color = TextMuted)
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            val maxAmt = expenses.maxOfOrNull { it.amount }?.coerceAtLeast(1.0) ?: 10.0
            expenses.takeLast(10).forEach { item ->
                val frac = (item.amount / maxAmt).toFloat().coerceIn(0.08f, 1f)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(frac * 0.82f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(DopamineAmber)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.category.take(2),
                        fontSize = 9.sp,
                        color = TextMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * 环形饼图 Canvas 绘制
 */
@Composable
private fun DonutChartCanvas(
    categoryTotals: List<Pair<String, Double>>,
    totalExpense: Double,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (totalExpense <= 0.0) return@Canvas
        var startAngle = -90f
        val strokeWidth = size.width * 0.18f

        categoryTotals.forEachIndexed { idx, (_, amt) ->
            val sweepAngle = (amt / totalExpense * 360f).toFloat()
            val color = CATEGORY_COLORS[idx % CATEGORY_COLORS.size]
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            startAngle += sweepAngle
        }
    }
}

/**
 * 单笔账目明细卡片
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExpenseItemCard(
    item: ExpenseEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isExpense = item.type == "EXPENSE"
    val haptic = LocalHapticFeedback.current

    val formattedTime = remember(item.transactionTime) {
        try {
            val ldt = LocalDateTime.parse(item.transactionTime)
            ldt.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
        } catch (e: Exception) {
            item.transactionTime.take(16).replace("T", " ")
        }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = DarkCard,
        border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onEdit,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (isExpense) DopamineRed.copy(alpha = 0.15f) else DopamineGreen.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(getCategoryEmoji(item.category, item.type), fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.note.ifBlank { item.category },
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = DarkSurface,
                        border = BorderStroke(0.5.dp, DarkBorder)
                    ) {
                        Text(
                            text = item.category,
                            fontSize = 9.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formattedTime,
                    fontSize = 10.5.sp,
                    color = TextMuted
                )
            }

            Text(
                text = (if (isExpense) "-¥" else "+¥") + "%.2f".format(item.amount),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (isExpense) DopamineRed else DopamineGreen
            )

            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp).padding(start = 4.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "删除", tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * 新增账目弹窗 (AddExpenseDialog)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(
    onDismiss: () -> Unit,
    onConfirm: (ExpenseEntity) -> Unit
) {
    var amountStr by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("餐饮") }
    var isExpense by remember { mutableStateOf(true) }

    val presetList = if (isExpense) PRESET_EXPENSE_CATEGORIES else PRESET_INCOME_CATEGORIES

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("记一笔账", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isExpense,
                        onClick = {
                            isExpense = true
                            category = "餐饮"
                        },
                        label = { Text("支出") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DopamineRed.copy(alpha = 0.25f),
                            selectedLabelColor = DopamineRed
                        )
                    )
                    FilterChip(
                        selected = !isExpense,
                        onClick = {
                            isExpense = false
                            category = "工资"
                        },
                        label = { Text("收入") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DopamineGreen.copy(alpha = 0.25f),
                            selectedLabelColor = DopamineGreen
                        )
                    )
                }

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) {
                            amountStr = input
                        }
                    },
                    label = { Text("金额") },
                    prefix = { Text("¥ ", color = DopamineAmber, fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DopamineAmber,
                        focusedLabelColor = DopamineAmber
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("分类选择", fontSize = 12.sp, color = TextSecondary)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(presetList) { cat ->
                        val isSelected = category == cat
                        Surface(
                            onClick = { category = cat },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) DopamineAmber else DarkCard,
                            border = BorderStroke(1.dp, if (isSelected) DopamineAmber else DarkBorder),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text(getCategoryEmoji(cat, if (isExpense) "EXPENSE" else "INCOME"), fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = cat,
                                    fontSize = 11.sp,
                                    color = if (isSelected) DarkBg else TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注详情 (如: 晚餐牛肉火锅)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DopamineAmber,
                        focusedLabelColor = DopamineAmber
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    if (amt > 0) {
                        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                        val entity = ExpenseEntity(
                            type = if (isExpense) "EXPENSE" else "INCOME",
                            amount = amt,
                            category = category,
                            note = note,
                            rawInputText = "",
                            transactionTime = nowStr,
                            createdAt = nowStr
                        )
                        onConfirm(entity)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DopamineAmber)
            ) {
                Text("确认入账", color = DarkBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        }
    )
}

/**
 * 修改账目弹窗 (EditExpenseDialog)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseDialog(
    expense: ExpenseEntity,
    onDismiss: () -> Unit,
    onSave: (ExpenseEntity) -> Unit,
    onDelete: () -> Unit
) {
    var amountStr by remember { mutableStateOf(expense.amount.toString()) }
    var note by remember { mutableStateOf(expense.note) }
    var category by remember { mutableStateOf(expense.category) }
    var isExpense by remember { mutableStateOf(expense.type == "EXPENSE") }
    var transactionTime by remember { mutableStateOf(expense.transactionTime) }

    val presetList = if (isExpense) PRESET_EXPENSE_CATEGORIES else PRESET_INCOME_CATEGORIES

    val formattedTime = remember(transactionTime) {
        try {
            val ldt = LocalDateTime.parse(transactionTime)
            ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        } catch (e: Exception) {
            transactionTime
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("修改账目明细", color = TextPrimary, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "删除", tint = DopamineRed, modifier = Modifier.size(20.dp))
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isExpense,
                        onClick = { isExpense = true },
                        label = { Text("支出") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DopamineRed.copy(alpha = 0.25f),
                            selectedLabelColor = DopamineRed
                        )
                    )
                    FilterChip(
                        selected = !isExpense,
                        onClick = { isExpense = false },
                        label = { Text("收入") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DopamineGreen.copy(alpha = 0.25f),
                            selectedLabelColor = DopamineGreen
                        )
                    )
                }

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) {
                            amountStr = input
                        }
                    },
                    label = { Text("金额") },
                    prefix = { Text("¥ ", color = DopamineAmber, fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DopamineAmber,
                        focusedLabelColor = DopamineAmber
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("分类选择", fontSize = 12.sp, color = TextSecondary)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(presetList) { cat ->
                        val isSelected = category == cat
                        Surface(
                            onClick = { category = cat },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) DopamineAmber else DarkCard,
                            border = BorderStroke(1.dp, if (isSelected) DopamineAmber else DarkBorder),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text(getCategoryEmoji(cat, if (isExpense) "EXPENSE" else "INCOME"), fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = cat,
                                    fontSize = 11.sp,
                                    color = if (isSelected) DarkBg else TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注详情 (如: 午餐牛肉面)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DopamineAmber,
                        focusedLabelColor = DopamineAmber
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "时间: $formattedTime",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    TextButton(
                        onClick = {
                            transactionTime = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("设为当前时间", fontSize = 10.sp, color = DopamineAmber)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    if (amt > 0) {
                        val updated = expense.copy(
                            type = if (isExpense) "EXPENSE" else "INCOME",
                            amount = amt,
                            category = category,
                            note = note,
                            transactionTime = transactionTime
                        )
                        onSave(updated)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DopamineAmber)
            ) {
                Text("保存修改", color = DarkBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        }
    )
}

/**
 * 删除账目二次确认弹窗 (DeleteExpenseConfirmDialog)
 */
@Composable
fun DeleteExpenseConfirmDialog(
    expense: ExpenseEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("确认删除账目？", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "确定要删除该笔明细吗？",
                    fontSize = 13.sp,
                    color = TextPrimary
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DarkCard,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${expense.note.ifBlank { expense.category }} (${expense.category})",
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = (if (expense.type == "INCOME") "+¥" else "-¥") + "%.2f".format(expense.amount),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (expense.type == "INCOME") DopamineGreen else DopamineRed
                        )
                    }
                }
                Text(
                    text = "删除后该记录将移出统计，不可恢复。",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = DopamineRed)
            ) {
                Text("确认删除", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        }
    )
}

/**
 * 自然语言记账文本解析
 */
private fun parseQuickExpense(text: String): ExpenseEntity {
    var raw = text.trim()
    var type = "EXPENSE"
    if (raw.contains("收入") || raw.contains("工资") || raw.contains("兼职") || raw.contains("奖金") || raw.contains("红包")) {
        type = "INCOME"
    }

    val numRegex = Pattern.compile("(\\d+(?:\\.\\d+)?)")
    val matcher = numRegex.matcher(raw)
    var amount = 0.0
    if (matcher.find()) {
        amount = matcher.group(1)?.toDoubleOrNull() ?: 0.0
        raw = raw.replace(matcher.group(0) ?: "", "").trim()
    }

    val category = when {
        raw.contains("吃") || raw.contains("饭") || raw.contains("外卖") || raw.contains("餐") -> "餐饮"
        raw.contains("车") || raw.contains("打车") || raw.contains("地铁") || raw.contains("油") -> "交通"
        raw.contains("买") || raw.contains("物") || raw.contains("服") -> "购物"
        raw.contains("学") || raw.contains("书") || raw.contains("课") -> "学习"
        raw.contains("玩") || raw.contains("游") || raw.contains("影") -> "娱乐"
        raw.contains("药") || raw.contains("医") -> "医疗"
        raw.contains("房") || raw.contains("租") || raw.contains("水电") -> "居住"
        raw.contains("工") || raw.contains("薪") -> "工资"
        raw.contains("兼") -> "兼职"
        raw.contains("包") -> "红包"
        else -> if (type == "INCOME") "工资" else "日常"
    }

    val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
    return ExpenseEntity(
        type = type,
        amount = amount,
        category = category,
        note = raw.ifBlank { category },
        rawInputText = text,
        transactionTime = nowStr,
        createdAt = nowStr
    )
}
