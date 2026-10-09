package com.renly.rmf.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.data.local.entity.ExpenseEntity
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    expensesFlow: Flow<List<ExpenseEntity>>,
    onAddExpense: (ExpenseEntity) -> Unit
) {
    val expenses by expensesFlow.collectAsState(initial = emptyList())
    var quickInputText by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf("ALL") }
    var showAddDialog by remember { mutableStateOf(false) }

    val totalExpense = expenses.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val totalIncome = expenses.filter { it.type == "INCOME" }.sumOf { it.amount }

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
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "极简日常记账",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 本月收支汇总大卡片
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("累计支出", fontSize = 12.sp, color = TextSecondary)
                        Text(
                            text = "¥${"%.2f".format(totalExpense)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = DopamineRed
                        )
                    }

                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(DarkBorder))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("累计收入", fontSize = 12.sp, color = TextSecondary)
                        Text(
                            text = "¥${"%.2f".format(totalIncome)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = DopamineGreen
                        )
                    }

                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(DarkBorder))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("收支结余", fontSize = 12.sp, color = TextSecondary)
                        val balance = totalIncome - totalExpense
                        Text(
                            text = "¥${"%.2f".format(balance)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (balance >= 0) TextPrimary else DopamineAmber
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 自然语言快捷记账条
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    TextField(
                        value = quickInputText,
                        onValueChange = { quickInputText = it },
                        placeholder = { Text("智能记账: 如 午餐 28.5 / 打车 35", fontSize = 13.sp, color = TextMuted) },
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

            Spacer(modifier = Modifier.height(12.dp))

            val filteredExpenses = expenses.filter {
                if (selectedTypeFilter == "ALL") true else it.type == selectedTypeFilter
            }

            if (filteredExpenses.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无账目明细，快速输入随心记一笔", color = TextMuted)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredExpenses, key = { it.id }) { item ->
                        ExpenseItemCard(item = item)
                    }
                }
            }
        }

        if (showAddDialog) {
            AddExpenseDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { newExpense ->
                    onAddExpense(newExpense)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun ExpenseItemCard(item: ExpenseEntity) {
    val isIncome = item.type == "INCOME"
    val timeLabel = try {
        LocalDateTime.parse(item.transactionTime, DateTimeFormatter.ISO_DATE_TIME).format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
    } catch (_: Exception) {
        item.transactionTime
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(14.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.note.ifBlank { item.category },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = item.category,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(text = timeLabel, fontSize = 11.sp, color = TextMuted)
                }
            }

            Text(
                text = (if (isIncome) "+¥" else "-¥") + "%.2f".format(item.amount),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isIncome) DopamineGreen else DopamineRed
            )
        }
    }
}

private fun parseQuickExpense(text: String): ExpenseEntity {
    var raw = text.trim()
    var type = "EXPENSE"
    if (raw.contains("收入") || raw.contains("工资") || raw.contains("兼职")) {
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
        else -> "日常"
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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("记一笔账", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = isExpense, onClick = { isExpense = true }, label = { Text("支出") })
                    FilterChip(selected = !isExpense, onClick = { isExpense = false }, label = { Text("收入") })
                }
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("金额 (¥)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注详情 (如: 午餐烧鸭饭)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                    val entity = ExpenseEntity(
                        type = if (isExpense) "EXPENSE" else "INCOME",
                        amount = amt,
                        category = category,
                        note = note,
                        transactionTime = nowStr,
                        createdAt = nowStr
                    )
                    onConfirm(entity)
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
