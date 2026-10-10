package com.renly.rmf.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.data.local.entity.HabitEntity
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    habitsFlow: Flow<List<HabitEntity>>,
    onAddHabit: (String, String, Int, String) -> Unit,
    onToggleCheckIn: (HabitEntity, LocalDate) -> Unit,
    onDeleteHabit: (String) -> Unit
) {
    val habits by habitsFlow.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    val todayStr = remember(today) { today.toString() }

    val totalHabitsCount = habits.size
    val todayCompletedCount = habits.count { isHabitChecked(it, todayStr) }
    val bestOverallStreak = habits.maxOfOrNull { it.bestStreak } ?: 0

    Scaffold(
        containerColor = DarkBg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = DopamineBlue,
                contentColor = DarkBg,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "新增习惯", modifier = Modifier.size(24.dp))
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

            // 头部标题与统计卡片
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🌱 自律习惯打卡",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "坚持微小习惯，365天方块点亮自律热力图",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Surface(
                    color = DopamineAmber.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, DopamineAmber.copy(alpha = 0.5f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Whatshot, contentDescription = "最佳连续", tint = DopamineAmber, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "最佳 $bestOverallStreak 天",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DopamineAmber
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 今日打卡进度概览
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = DarkCard,
                border = BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("今日习惯达成率", fontSize = 12.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        val percent = if (totalHabitsCount > 0) (todayCompletedCount * 100 / totalHabitsCount) else 0
                        Text("$todayCompletedCount / $totalHabitsCount 习惯 ($percent%)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DopamineBlue)
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { if (totalHabitsCount > 0) todayCompletedCount.toFloat() / totalHabitsCount else 0f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = DopamineBlue,
                            trackColor = DarkSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (habits.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🌱", fontSize = 36.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("还没有创建任何习惯", fontSize = 14.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("点击右下角「＋」开始培养喝水、早读或健身习惯", fontSize = 11.5.sp, color = TextMuted)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(habits, key = { it.id }) { habit ->
                        HabitCard(
                            habit = habit,
                            todayStr = todayStr,
                            onToggleToday = { onToggleCheckIn(habit, today) },
                            onDelete = { onDeleteHabit(habit.id) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showAddDialog) {
        AddHabitDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, icon, targetDays, colorHex ->
                onAddHabit(title, icon, targetDays, colorHex)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun HabitCard(
    habit: HabitEntity,
    todayStr: String,
    onToggleToday: () -> Unit,
    onDelete: () -> Unit
) {
    val isCheckedToday = isHabitChecked(habit, todayStr)
    val habitColor = try {
        Color(android.graphics.Color.parseColor(habit.colorHex))
    } catch (_: Exception) {
        DopamineBlue
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DarkCard,
        border = BorderStroke(1.dp, if (isCheckedToday) habitColor.copy(alpha = 0.6f) else DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 顶部信息行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = habitColor.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, habitColor.copy(alpha = 0.5f)),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(habit.icon, fontSize = 18.sp)
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = habit.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = "🔥 连续 ${habit.currentStreak} 天",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DopamineAmber
                        )
                        Text("·", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = "目标: 每周${habit.targetDaysPerWeek}天",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                // 今日打卡按钮
                Surface(
                    onClick = onToggleToday,
                    shape = RoundedCornerShape(10.dp),
                    color = if (isCheckedToday) DopamineGreen else habitColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (isCheckedToday) DopamineGreen else habitColor.copy(alpha = 0.6f)),
                    modifier = Modifier.height(34.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        if (isCheckedToday) {
                            Icon(Icons.Default.Check, contentDescription = "已打卡", tint = DarkBg, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("已打卡", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = DarkBg)
                        } else {
                            Text("⚡ 打卡", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = habitColor)
                        }
                    }
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "删除", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 365天 GitHub 风格打卡微型热力图 (最近 12 周 / 84 天展示)
            Text("📊 近期打卡热力图 (近 12 周):", fontSize = 10.5.sp, color = TextMuted)
            Spacer(modifier = Modifier.height(6.dp))

            HabitHeatmapGrid(
                historyDates = parseDatesList(habit.historyDatesJson),
                activeColor = habitColor
            )
        }
    }
}

@Composable
fun HabitHeatmapGrid(
    historyDates: Set<String>,
    activeColor: Color
) {
    val weeks = 12
    val today = remember { LocalDate.now() }
    val daysList = remember(today) {
        val list = mutableListOf<LocalDate>()
        val startDate = today.minusDays((weeks * 7 - 1).toLong())
        for (i in 0 until (weeks * 7)) {
            list.add(startDate.plusDays(i.toLong()))
        }
        list
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(3.5.dp)
    ) {
        for (w in 0 until weeks) {
            Column(verticalArrangement = Arrangement.spacedBy(3.5.dp)) {
                for (d in 0 until 7) {
                    val date = daysList[w * 7 + d]
                    val isChecked = historyDates.contains(date.toString())
                    val isFuture = date.isAfter(today)

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                when {
                                    isFuture -> Color.Transparent
                                    isChecked -> activeColor
                                    else -> DarkSurface
                                }
                            )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHabitDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("✨") }
    var targetDays by remember { mutableIntStateOf(7) }
    var selectedColor by remember { mutableStateOf("#38BDF8") }

    val presetIcons = listOf("✨", "💧", "📖", "🏃", "🧘", "🥗", "💤", "💻", "🎸", "💊")
    val presetColors = listOf("#38BDF8", "#34D399", "#F59E0B", "#EC4899", "#818CF8", "#F43F5E")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("🌱 建立新习惯", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("习惯名称 (如: 每天早起喝水500ml)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DopamineBlue,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("选择图标:", fontSize = 12.sp, color = TextSecondary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presetIcons) { icon ->
                        val isSel = selectedIcon == icon
                        Surface(
                            onClick = { selectedIcon = icon },
                            shape = CircleShape,
                            color = if (isSel) DopamineBlue.copy(alpha = 0.2f) else DarkSurface,
                            border = BorderStroke(1.dp, if (isSel) DopamineBlue else DarkBorder),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(icon, fontSize = 16.sp)
                            }
                        }
                    }
                }

                Text("专属色系:", fontSize = 12.sp, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presetColors.forEach { hex ->
                        val isSel = selectedColor == hex
                        val c = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { DopamineBlue }
                        Surface(
                            onClick = { selectedColor = hex },
                            shape = CircleShape,
                            color = c,
                            border = BorderStroke(if (isSel) 2.5.dp else 0.dp, if (isSel) Color.White else Color.Transparent),
                            modifier = Modifier.size(28.dp)
                        ) {}
                    }
                }

                Text("每周目标天数: 每周 $targetDays 天", fontSize = 12.sp, color = TextSecondary)
                Slider(
                    value = targetDays.toFloat(),
                    onValueChange = { targetDays = it.toInt() },
                    valueRange = 1f..7f,
                    steps = 5,
                    colors = SliderDefaults.colors(thumbColor = DopamineBlue, activeTrackColor = DopamineBlue)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title, selectedIcon, targetDays, selectedColor)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("创建习惯", color = DarkBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        }
    )
}

fun isHabitChecked(habit: HabitEntity, dateStr: String): Boolean {
    return parseDatesList(habit.historyDatesJson).contains(dateStr)
}

fun parseDatesList(json: String?): Set<String> {
    if (json.isNullOrBlank()) return emptySet()
    val set = mutableSetOf<String>()
    try {
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            set.add(array.getString(i))
        }
    } catch (_: Exception) {}
    return set
}
