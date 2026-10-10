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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.data.local.entity.CountdownEntity
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownsScreen(
    countdownsFlow: Flow<List<CountdownEntity>>,
    onAddCountdown: (String, String, String, String) -> Unit,
    onTogglePin: (CountdownEntity) -> Unit,
    onDeleteCountdown: (String) -> Unit
) {
    val countdowns by countdownsFlow.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }

    Scaffold(
        containerColor = DarkBg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = DopamineAmber,
                contentColor = DarkBg,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "新增倒数日", modifier = Modifier.size(24.dp))
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

            // 头部标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "⏳ 倒数日与里程碑",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "考研、期末、重要发版与纪念日倒数胶囊",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Surface(
                    color = DopamineCyan.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, DopamineCyan.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "共 ${countdowns.size} 个里程碑",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = DopamineCyan,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (countdowns.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⏳", fontSize = 36.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("还没有记录任何倒数日", fontSize = 14.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("点击右下角「＋」添加考试、生日或目标截止日期", fontSize = 11.5.sp, color = TextMuted)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(countdowns, key = { it.id }) { item ->
                        CountdownCard(
                            item = item,
                            today = today,
                            onTogglePin = { onTogglePin(item) },
                            onDelete = { onDeleteCountdown(item.id) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showAddDialog) {
        AddCountdownDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, targetDate, category, colorHex ->
                onAddCountdown(title, targetDate, category, colorHex)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun CountdownCard(
    item: CountdownEntity,
    today: LocalDate,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val target = remember(item.targetDate) {
        try {
            LocalDate.parse(item.targetDate.take(10))
        } catch (_: Exception) {
            today
        }
    }

    val daysDiff = remember(target, today) {
        ChronoUnit.DAYS.between(today, target)
    }

    val isPassed = daysDiff < 0
    val cardColor = try {
        Color(android.graphics.Color.parseColor(item.colorHex))
    } catch (_: Exception) {
        DopamineAmber
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DarkCard,
        border = BorderStroke(1.dp, if (item.isPinned == 1) cardColor else DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isPinned == 1) {
                        Surface(
                            color = cardColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = "📌 置顶",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = cardColor,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = item.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "目标: ${item.targetDate.take(10)}",
                        fontSize = 11.5.sp,
                        color = TextSecondary
                    )
                    Text("·", fontSize = 11.5.sp, color = TextMuted)
                    Text(
                        text = when (item.category) {
                            "EXAM" -> "📝 考试冲刺"
                            "MILESTONE" -> "🚀 战略里程碑"
                            "ANNIVERSARY" -> "💖 纪念日"
                            "HOLIDAY" -> "🌴 节假日"
                            else -> "🎯 目标截止"
                        },
                        fontSize = 11.5.sp,
                        color = cardColor
                    )
                }
            }

            // 倒数胶囊大字
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = cardColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, cardColor.copy(alpha = 0.5f))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isPassed) "已过去" else "还剩",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "${Math.abs(daysDiff)}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = cardColor
                    )
                    Text(
                        text = "DAYS",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column {
                IconButton(onClick = onTogglePin, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (item.isPinned == 1) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = "置顶",
                        tint = if (item.isPinned == 1) cardColor else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "删除", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCountdownDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var year by remember { mutableIntStateOf(LocalDate.now().year) }
    var month by remember { mutableIntStateOf(LocalDate.now().monthValue) }
    var day by remember { mutableIntStateOf(LocalDate.now().dayOfMonth) }
    var selectedCategory by remember { mutableStateOf("EXAM") }
    var selectedColor by remember { mutableStateOf("#F59E0B") }

    val categories = listOf("EXAM" to "📝 考试", "MILESTONE" to "🚀 里程碑", "ANNIVERSARY" to "💖 纪念日", "HOLIDAY" to "🌴 节日")
    val presetColors = listOf("#F59E0B", "#EF4444", "#38BDF8", "#10B981", "#8B5CF6", "#EC4899")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("⏳ 新建倒数日", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("事件标题 (如: 考研初试、期末考试)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DopamineAmber,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("目标日期:", fontSize = 12.sp, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = year.toString(),
                        onValueChange = { year = it.toIntOrNull() ?: year },
                        label = { Text("年") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = month.toString(),
                        onValueChange = { month = (it.toIntOrNull() ?: month).coerceIn(1, 12) },
                        label = { Text("月") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = day.toString(),
                        onValueChange = { day = (it.toIntOrNull() ?: day).coerceIn(1, 31) },
                        label = { Text("日") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("分类属性:", fontSize = 12.sp, color = TextSecondary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories) { (code, label) ->
                        FilterChip(
                            selected = selectedCategory == code,
                            onClick = { selectedCategory = code },
                            label = { Text(label, fontSize = 11.5.sp) }
                        )
                    }
                }

                Text("胶囊色彩:", fontSize = 12.sp, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presetColors.forEach { hex ->
                        val isSel = selectedColor == hex
                        val c = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { DopamineAmber }
                        Surface(
                            onClick = { selectedColor = hex },
                            shape = CircleShape,
                            color = c,
                            border = BorderStroke(if (isSel) 2.5.dp else 0.dp, if (isSel) Color.White else Color.Transparent),
                            modifier = Modifier.size(28.dp)
                        ) {}
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val dateStr = "%04d-%02d-%02d".format(year, month, day)
                        onConfirm(title, dateStr, selectedCategory, selectedColor)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DopamineAmber),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("确定添加", color = DarkBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        }
    )
}
