package com.renly.rmf.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.data.local.entity.ExpenseEntity
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

private val PRESET_EXPENSE_CATEGORIES = listOf("餐饮", "交通", "购物", "日常", "学习", "娱乐", "医疗", "居住", "人情", "其他")
private val PRESET_INCOME_CATEGORIES = listOf("工资", "奖金", "兼职", "理财", "红包", "退款", "其他")

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

    val totalExpense = expenses.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val totalIncome = expenses.filter { it.type == "INCOME" }.sumOf { it.amount }
    val balance = totalIncome - totalExpense

    Scaffold(
        containerColor = DarkBg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = DopamineAmber,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "记一笔")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // 页面标题与状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "极简日常记账",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "共计 ${expenses.size} 笔明细 · 支持点击编辑与删除",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 本月收支汇总大卡片 (点击各块可快捷切换筛选)
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp)
                    ) {
                        Text("累计支出", fontSize = 11.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "¥${"%.2f".format(totalExpense)}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = DopamineRed
                        )
                    }

                    Box(modifier = Modifier.width(1.dp).height(28.dp).background(DarkBorder))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp)
                    ) {
                        Text("累计收入", fontSize = 11.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "¥${"%.2f".format(totalIncome)}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = DopamineGreen
                        )
                    }

                    Box(modifier = Modifier.width(1.dp).height(28.dp).background(DarkBorder))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp)
                    ) {
                        Text("收支结余", fontSize = 11.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "¥${"%.2f".format(balance)}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (balance >= 0) TextPrimary else DopamineAmber
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 自然语言快捷智能记账条
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DopamineAmber.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
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
                        Icon(Icons.Default.AutoAwesome, contentDescription = "记账", tint = DopamineAmber)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 收支类型筛选切换 Chip 栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTypeFilter == "ALL",
                    onClick = { selectedTypeFilter = "ALL" },
                    label = { Text("全部 (${expenses.size})", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DopamineAmber.copy(alpha = 0.25f),
                        selectedLabelColor = DopamineAmber
                    )
                )
                FilterChip(
                    selected = selectedTypeFilter == "EXPENSE",
                    onClick = { selectedTypeFilter = "EXPENSE" },
                    label = { Text("支出 (${expenses.count { it.type == "EXPENSE" }})", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DopamineRed.copy(alpha = 0.25f),
                        selectedLabelColor = DopamineRed
                    )
                )
                FilterChip(
                    selected = selectedTypeFilter == "INCOME",
                    onClick = { selectedTypeFilter = "INCOME" },
                    label = { Text("收入 (${expenses.count { it.type == "INCOME" }})", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DopamineGreen.copy(alpha = 0.25f),
                        selectedLabelColor = DopamineGreen
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val filteredExpenses = expenses.filter {
                if (selectedTypeFilter == "ALL") true else it.type == selectedTypeFilter
            }

            if (filteredExpenses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🧾", fontSize = 36.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("暂无符合条件的账目明细", fontSize = 13.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("在上方输入或点击右下角「+」快速记一笔", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredExpenses, key = { it.id }) { item ->
                        ExpenseItemCard(
                            item = item,
                            onEdit = { editingExpense = item },
                            onDelete = { deletingExpense = item }
                        )
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
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpenseItemCard(
    item: ExpenseEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isIncome = item.type == "INCOME"
    val timeLabel = try {
        LocalDateTime.parse(item.transactionTime, DateTimeFormatter.ISO_DATE_TIME)
            .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
    } catch (_: Exception) {
        item.transactionTime
    }
    val emoji = getCategoryEmoji(item.category, item.type)

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.8.dp, DarkBorder.copy(alpha = 0.5f)),
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
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // 分类图标徽章
            Surface(
                shape = CircleShape,
                color = if (isIncome) DopamineGreen.copy(alpha = 0.15f) else DopamineAmber.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = emoji, fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 账目描述与分类标签
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.note.ifBlank { item.category },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(0.5.dp, DarkBorder)
                    ) {
                        Text(
                            text = item.category,
                            fontSize = 10.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        )
                    }
                    Text(text = timeLabel, fontSize = 10.sp, color = TextMuted)
                }
            }

            // 金额
            Text(
                text = (if (isIncome) "+¥" else "-¥") + "%.2f".format(item.amount),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isIncome) DopamineGreen else DopamineRed
            )

            Spacer(modifier = Modifier.width(4.dp))

            // 快捷编辑图标按钮
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "编辑",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }

            // 快捷删除图标按钮
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "删除",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// ✏️ 修改账目弹窗 (EditExpenseDialog)
// -----------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseDialog(
    expense: ExpenseEntity,
    onDismiss: () -> Unit,
    onSave: (ExpenseEntity) -> Unit,
    onDelete: () -> Unit
) {
    var isExpense by remember { mutableStateOf(expense.type == "EXPENSE") }
    var amountStr by remember { mutableStateOf(if (expense.amount == 0.0) "" else "%.2f".format(expense.amount)) }
    var note by remember { mutableStateOf(expense.note) }
    var category by remember { mutableStateOf(expense.category) }
    var transactionTime by remember { mutableStateOf(expense.transactionTime) }

    val presetList = if (isExpense) PRESET_EXPENSE_CATEGORIES else PRESET_INCOME_CATEGORIES

    val formattedTime = try {
        LocalDateTime.parse(transactionTime, DateTimeFormatter.ISO_DATE_TIME)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    } catch (_: Exception) {
        transactionTime
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
                Text("修改账目", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                // 顶部直接提供删除按钮
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "删除账目",
                        tint = DopamineRed
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 收支类型切换
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isExpense,
                        onClick = {
                            isExpense = true
                            if (!PRESET_EXPENSE_CATEGORIES.contains(category)) {
                                category = PRESET_EXPENSE_CATEGORIES.first()
                            }
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
                            if (!PRESET_INCOME_CATEGORIES.contains(category)) {
                                category = PRESET_INCOME_CATEGORIES.first()
                            }
                        },
                        label = { Text("收入") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DopamineGreen.copy(alpha = 0.25f),
                            selectedLabelColor = DopamineGreen
                        )
                    )
                }

                // 金额输入框
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

                // 常用分类快速点选
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
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color.White else TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // 备注输入框
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

                // 交易时间展示与重置
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
                Text("保存修改", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        }
    )
}

// -----------------------------------------------------------------------------------------
// ➕ 新增账目弹窗 (AddExpenseDialog)
// -----------------------------------------------------------------------------------------
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

                // 常用分类快速点选
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
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color.White else TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注详情 (如: 午餐烧鸭饭)") },
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
                            note = note.ifBlank { category },
                            transactionTime = nowStr,
                            createdAt = nowStr
                        )
                        onConfirm(entity)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DopamineAmber)
            ) {
                Text("确定", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        }
    )
}

// -----------------------------------------------------------------------------------------
// 🗑️ 删除账目二次确认弹窗 (DeleteExpenseConfirmDialog)
// -----------------------------------------------------------------------------------------
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

// -----------------------------------------------------------------------------------------
// 🧠 自然语言记账文本解析
// -----------------------------------------------------------------------------------------
private fun parseQuickExpense(text: String): ExpenseEntity {
    var raw = text.trim()
    var type = "EXPENSE"
    if (raw.contains("收入") || raw.contains("工资") || raw.contains("兼职") || raw.contains("奖金") || raw.contains("红包")) {
        type = "INCOME"
    }

    // 提取数字金额
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
