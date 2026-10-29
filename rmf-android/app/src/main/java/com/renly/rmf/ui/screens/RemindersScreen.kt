package com.renly.rmf.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.data.local.dao.ReminderDao
import com.renly.rmf.data.local.entity.ReminderEntity
import com.renly.rmf.domain.service.ReminderRepeatHelper
import com.renly.rmf.domain.service.ReminderType
import com.renly.rmf.domain.service.ScheduleReminderManager
import com.renly.rmf.domain.service.SystemCalendarSyncService
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    reminderDao: ReminderDao,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val remindersFlow = remember { reminderDao.getAllActiveReminders() }
    val reminders by remindersFlow.collectAsState(initial = emptyList())

    var selectedTab by remember { mutableIntStateOf(0) } // 0: 待提醒, 1: 已完成
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<ReminderEntity?>(null) }
    var syncFeedbackText by remember { mutableStateOf<String?>(null) }
    var showKeepAliveDialog by remember { mutableStateOf(false) }

    // 系统保活与精准权限状态诊断
    val canExactAlarm = remember { ScheduleReminderManager.canScheduleExactAlarms(context) }
    val isBatteryIgnored = remember { ScheduleReminderManager.isIgnoringBatteryOptimizations(context) }
    val isHealthFullyGuarded = canExactAlarm && isBatteryIgnored

    val pendingReminders = remember(reminders) {
        reminders.filter { it.isCompleted == 0 && it.isDeleted == 0 }
    }
    val completedReminders = remember(reminders) {
        reminders.filter { it.isCompleted == 1 && it.isDeleted == 0 }
    }

    val todayStr = remember { LocalDate.now().toString() }
    val dueTodayCount = remember(pendingReminders, todayStr) {
        pendingReminders.count { it.remindTime.startsWith(todayStr) }
    }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkCard)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onNavigateBack != {}) {
                            IconButton(onClick = onNavigateBack, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = TextPrimary)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text("🔔", fontSize = 22.sp, modifier = Modifier.padding(end = 6.dp))
                        Text(
                            text = "事件提醒",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // 提醒防漏守护与系统保活健康诊断按钮
                        FilledTonalButton(
                            onClick = { showKeepAliveDialog = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isHealthFullyGuarded) DopamineGreen.copy(alpha = 0.16f) else DopamineAmber.copy(alpha = 0.2f),
                                contentColor = if (isHealthFullyGuarded) DopamineGreen else DopamineAmber
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(if (isHealthFullyGuarded) "🛡️ 守护极佳" else "⚠️ 防漏守护", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // 同步到手机系统原生日历与系统通知按钮
                        FilledTonalButton(
                            onClick = {
                                coroutineScope.launch {
                                    val res = SystemCalendarSyncService.syncAllReminders(context, reminders)
                                    when (res) {
                                        is com.renly.rmf.domain.service.CalendarSyncResult.Success -> {
                                            syncFeedbackText = res.message
                                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                        }
                                        is com.renly.rmf.domain.service.CalendarSyncResult.Error -> {
                                            syncFeedbackText = res.error
                                            Toast.makeText(context, res.error, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = DopamineCyan.copy(alpha = 0.16f),
                                contentColor = DopamineCyan
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = "同步系统", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("同步日历", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text(
                    text = "系统时钟硬件 RTC 唤醒 · 零后台常驻与零闲置耗电 · 重复周期自动推进 · 逾期强力兜底",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 统计卡片排
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatisticMiniCard(
                        title = "待提醒事项",
                        value = "${pendingReminders.size}",
                        accentColor = DopamineCyan,
                        modifier = Modifier.weight(1f)
                    )
                    StatisticMiniCard(
                        title = "今日到期",
                        value = "$dueTodayCount",
                        accentColor = DopamineAmber,
                        modifier = Modifier.weight(1f)
                    )
                    StatisticMiniCard(
                        title = "已完成",
                        value = "${completedReminders.size}",
                        accentColor = DopamineGreen,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tab 切换：待提醒 / 已完成
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkCard,
                    contentColor = DopamineCyan,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "待提醒 (${pendingReminders.size})",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 0) DopamineCyan else TextSecondary
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "已完成 (${completedReminders.size})",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 1) DopamineGreen else TextSecondary
                            )
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingReminder = null
                    showAddEditDialog = true
                },
                containerColor = DopamineCyan,
                contentColor = DarkBg,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "新增提醒", modifier = Modifier.size(24.dp))
            }
        }
    ) { paddingValues ->
        val currentList = if (selectedTab == 0) pendingReminders else completedReminders

        if (currentList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔔", fontSize = 42.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (selectedTab == 0) "暂无待提醒事件" else "暂无已完成的提醒记录",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (selectedTab == 0) "点击右下角「+」即可添加准时提醒事项" else "完成提醒后将归档在此处",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(currentList, key = { it.id }) { reminder ->
                    ReminderItemCard(
                        reminder = reminder,
                        onToggleComplete = { completed ->
                            coroutineScope.launch {
                                val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                if (completed) {
                                    if (!reminder.repeatRule.equals("NONE", ignoreCase = true)) {
                                        val nextIso = ReminderRepeatHelper.computeNextOccurrence(reminder.remindTime, reminder.repeatRule)
                                        if (nextIso != null) {
                                            reminderDao.advanceRepeatCycle(reminder.id, nextIso, 0, nowStr)
                                            val updated = reminder.copy(remindTime = nextIso, isCompleted = 0, updatedAt = nowStr)
                                            ScheduleReminderManager.scheduleDirectReminder(context, updated)
                                            if (SystemCalendarSyncService.isAutoSyncEnabled(context)) {
                                                SystemCalendarSyncService.syncSingleReminder(context, updated)
                                            }
                                            val ruleName = ReminderRepeatHelper.formatRepeatRule(reminder.repeatRule)
                                            val nextLdt = try { LocalDateTime.parse(nextIso, DateTimeFormatter.ISO_LOCAL_DATE_TIME) } catch (_: Exception) { null }
                                            val nextFmt = nextLdt?.format(DateTimeFormatter.ofPattern("M月d日 HH:mm")) ?: nextIso
                                            Toast.makeText(context, "✓ 今日已完成！下次提醒：$nextFmt ($ruleName)", Toast.LENGTH_LONG).show()
                                        } else {
                                            reminderDao.toggleComplete(reminder.id, 1, nowStr)
                                            ScheduleReminderManager.cancelDirectReminder(context, reminder.id)
                                            SystemCalendarSyncService.deleteReminderEvent(context, reminder.id)
                                            Toast.makeText(context, "✓ 已完成提醒", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        reminderDao.toggleComplete(reminder.id, 1, nowStr)
                                        ScheduleReminderManager.cancelDirectReminder(context, reminder.id)
                                        SystemCalendarSyncService.deleteReminderEvent(context, reminder.id)
                                        Toast.makeText(context, "✓ 已完成提醒", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    reminderDao.toggleComplete(reminder.id, 0, nowStr)
                                    val updated = reminder.copy(isCompleted = 0, updatedAt = nowStr)
                                    ScheduleReminderManager.scheduleDirectReminder(context, updated)
                                    if (SystemCalendarSyncService.isAutoSyncEnabled(context)) {
                                        SystemCalendarSyncService.syncSingleReminder(context, updated)
                                    }
                                    Toast.makeText(context, "✓ 已恢复待提醒", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onQuickSnooze = { minutes ->
                            coroutineScope.launch {
                                val ldt = try {
                                    LocalDateTime.parse(reminder.remindTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                } catch (_: Exception) {
                                    LocalDateTime.now()
                                }
                                val snoozed = ldt.plusMinutes(minutes.toLong())
                                val snoozedIso = snoozed.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                reminderDao.updateRemindTime(reminder.id, snoozedIso, 0, nowStr)

                                val updated = reminder.copy(
                                    remindTime = snoozedIso,
                                    advanceMinutes = 0,
                                    updatedAt = nowStr
                                )
                                ScheduleReminderManager.scheduleDirectReminder(context, updated)
                                if (SystemCalendarSyncService.isAutoSyncEnabled(context)) {
                                    SystemCalendarSyncService.syncSingleReminder(context, updated)
                                }
                                Toast.makeText(context, "✓ 已推迟 $minutes 分钟", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onEdit = {
                            editingReminder = reminder
                            showAddEditDialog = true
                        },
                        onDelete = {
                            coroutineScope.launch {
                                val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                reminderDao.softDelete(reminder.id, nowStr)
                                ScheduleReminderManager.cancelDirectReminder(context, reminder.id)
                                SystemCalendarSyncService.deleteReminderEvent(context, reminder.id)
                                Toast.makeText(context, "✓ 已删除提醒", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }

    // 提醒保活与防漏健康诊断对话框
    if (showKeepAliveDialog) {
        AlertDialog(
            onDismissRequest = { showKeepAliveDialog = false },
            containerColor = DarkCard,
            title = {
                Text("🛡️ 提醒防漏与系统保活健康中心", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "为确保在关闭后台、锁屏待机或国内厂商(ColorOS/MIUI/OriginOS等)深度杀后台时不漏提醒，请确保以下防漏策略生效：",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    // 1. 电池无限制白名单
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, if (isBatteryIgnored) DopamineGreen.copy(alpha = 0.4f) else DopamineAmber.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("🔋 忽略电池优化 (防锁屏休眠)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(
                                    if (isBatteryIgnored) "已加入电池白名单，免遭系统冻结" else "当前受系统省电策略限制，可能导致提醒推迟",
                                    fontSize = 11.sp,
                                    color = if (isBatteryIgnored) DopamineGreen else DopamineAmber
                                )
                            }
                            if (!isBatteryIgnored) {
                                TextButton(onClick = { ScheduleReminderManager.requestIgnoreBatteryOptimizations(context) }) {
                                    Text("去开启", color = DopamineCyan, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Text("✓ 已生效", fontSize = 12.sp, color = DopamineGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 2. 精准闹钟权限
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, if (canExactAlarm) DopamineGreen.copy(alpha = 0.4f) else DopamineAmber.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("⏰ 精准硬件闹钟权限 (Android 12+)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(
                                    if (canExactAlarm) "已获最高级时钟硬件唤醒权限" else "未授权，闹钟唤醒可能会被系统合并延迟",
                                    fontSize = 11.sp,
                                    color = if (canExactAlarm) DopamineGreen else DopamineAmber
                                )
                            }
                            if (!canExactAlarm) {
                                TextButton(onClick = { ScheduleReminderManager.requestExactAlarmPermission(context) }) {
                                    Text("去授权", color = DopamineCyan, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Text("✓ 已生效", fontSize = 12.sp, color = DopamineGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 3. 国产 ROM 自启动与后台白名单管理
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("⚙️ 厂商系统自启动管理", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("ColorOS/MIUI/OriginOS/鸿蒙专属后台自启与应用锁设置", fontSize = 11.sp, color = TextSecondary)
                            }
                            TextButton(onClick = { ScheduleReminderManager.openAutostartSettings(context) }) {
                                Text("前往设置", color = DopamineCyan, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 4. 立即发送测试提醒
                    OutlinedButton(
                        onClick = {
                            ScheduleReminderManager.sendStandardTestNotification(context)
                            Toast.makeText(context, "已触发即时测试通知，请查看状态栏", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🔔 发送即时测试通知验证响铃与震动", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showKeepAliveDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = DopamineCyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("完成", color = DarkBg, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // 新增 / 编辑提醒对话框
    if (showAddEditDialog) {
        AddEditReminderDialog(
            reminder = editingReminder,
            onDismiss = { showAddEditDialog = false },
            onConfirm = { savedReminder ->
                coroutineScope.launch {
                    reminderDao.insertOrUpdate(savedReminder)
                    ScheduleReminderManager.scheduleDirectReminder(context, savedReminder)
                    if (SystemCalendarSyncService.isAutoSyncEnabled(context)) {
                        SystemCalendarSyncService.syncSingleReminder(context, savedReminder)
                    }
                    Toast.makeText(context, "✓ 提醒已设定并注册系统级精准闹钟", Toast.LENGTH_SHORT).show()
                    showAddEditDialog = false
                }
            }
        )
    }
}

@Composable
private fun StatisticMiniCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, DarkBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(title, fontSize = 10.5.sp, color = TextSecondary)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = accentColor)
        }
    }
}

@Composable
fun ReminderItemCard(
    reminder: ReminderEntity,
    onToggleComplete: (Boolean) -> Unit,
    onQuickSnooze: (Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isDone = reminder.isCompleted == 1
    val priorityColor = when (reminder.priority.uppercase()) {
        "HIGH", "URGENT" -> DopamineRed
        "LOW" -> DopamineGreen
        else -> DopamineCyan
    }

    val displayTimeStr = remember(reminder.remindTime) {
        try {
            val ldt = LocalDateTime.parse(reminder.remindTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val today = LocalDate.now()
            when {
                ldt.toLocalDate() == today -> "今天 ${ldt.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                ldt.toLocalDate() == today.plusDays(1) -> "明天 ${ldt.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                ldt.year == today.year -> ldt.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
                else -> ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            }
        } catch (_: Exception) {
            reminder.remindTime
        }
    }

    val repeatText = remember(reminder.repeatRule) {
        ReminderRepeatHelper.formatRepeatRule(reminder.repeatRule)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DarkCard,
        border = BorderStroke(1.dp, if (isDone) DarkBorder else priorityColor.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isDone,
                    onCheckedChange = onToggleComplete,
                    colors = CheckboxDefaults.colors(
                        checkedColor = DopamineGreen,
                        uncheckedColor = TextSecondary,
                        checkmarkColor = DarkBg
                    ),
                    modifier = Modifier.size(28.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = reminder.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDone) TextMuted else TextPrimary,
                            textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                        )
                        if (!reminder.repeatRule.equals("NONE", ignoreCase = true)) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = DopaminePurple.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "🔄 $repeatText",
                                    fontSize = 10.sp,
                                    color = DopaminePurple,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    if (reminder.content.isNotBlank()) {
                        Text(
                            text = reminder.content,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            maxLines = 2,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // 快捷编辑/删除操作
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "删除", tint = DopamineRed.copy(alpha = 0.7f), modifier = Modifier.size(17.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 底部元数据信息与快捷微调标签
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⏰", fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = displayTimeStr,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDone) TextMuted else DopamineCyan
                    )

                    if (reminder.advanceMinutes > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = DopamineAmber.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "提前${reminder.advanceMinutes}分",
                                fontSize = 10.sp,
                                color = DopamineAmber,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = DopamineCyan.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "🛡️ 精准闹钟对齐",
                            fontSize = 10.sp,
                            color = DopamineCyan,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                // 未完成时展示快捷推迟按钮
                if (!isDone) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SuggestionChip(
                            onClick = { onQuickSnooze(10) },
                            label = { Text("+10分", fontSize = 10.sp) },
                            modifier = Modifier.height(26.dp)
                        )
                        SuggestionChip(
                            onClick = { onQuickSnooze(60) },
                            label = { Text("+1小时", fontSize = 10.sp) },
                            modifier = Modifier.height(26.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddEditReminderDialog(
    reminder: ReminderEntity?,
    onDismiss: () -> Unit,
    onConfirm: (ReminderEntity) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(reminder?.title ?: "") }
    var content by remember { mutableStateOf(reminder?.content ?: "") }

    val initialDateTime = remember(reminder) {
        if (reminder != null) {
            try {
                LocalDateTime.parse(reminder.remindTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            } catch (_: Exception) {
                LocalDateTime.now().plusMinutes(15)
            }
        } else {
            LocalDateTime.now().plusMinutes(15)
        }
    }

    var selectedDate by remember { mutableStateOf(initialDateTime.toLocalDate()) }
    var selectedTime by remember { mutableStateOf(initialDateTime.toLocalTime()) }
    var advanceMinutes by remember { mutableIntStateOf(reminder?.advanceMinutes ?: 0) }
    var repeatRule by remember { mutableStateOf(reminder?.repeatRule ?: "NONE") }
    var reminderType by remember { mutableStateOf(reminder?.reminderType ?: "ALL") }
    var category by remember { mutableStateOf(reminder?.category ?: "REMINDER") }
    var priority by remember { mutableStateOf(reminder?.priority ?: "MEDIUM") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (reminder == null) "🔔 新增事件提醒" else "✏️ 编辑事件提醒",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("提醒事项 (如: 吃药/喝水/带钥匙/开会)") },
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DopamineCyan,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("补充说明 / 备注 (可选)") },
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DopamineCyan,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                // 日期与时间选择器按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, y, m, d -> selectedDate = LocalDate.of(y, m + 1, d) },
                                selectedDate.year,
                                selectedDate.monthValue - 1,
                                selectedDate.dayOfMonth
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("📅 ${selectedDate.format(DateTimeFormatter.ofPattern("M月d日"))}", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, min -> selectedTime = LocalTime.of(h, min) },
                                selectedTime.hour,
                                selectedTime.minute,
                                true
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("⏰ ${selectedTime.format(DateTimeFormatter.ofPattern("HH:mm"))}", fontSize = 12.sp)
                    }
                }

                // 重复周期规则
                Text("重复周期 (自动推进下一次排期):", fontSize = 11.5.sp, color = TextSecondary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val repeatOptions = listOf(
                        "NONE" to "不重复",
                        "DAILY" to "每天",
                        "WEEKDAYS" to "工作日",
                        "WEEKLY" to "每周",
                        "MONTHLY" to "每月",
                        "YEARLY" to "每年"
                    )
                    repeatOptions.forEach { (rule, label) ->
                        FilterChip(
                            selected = repeatRule == rule,
                            onClick = { repeatRule = rule },
                            label = { Text(label, fontSize = 10.5.sp) }
                        )
                    }
                }

                // 提前提醒分钟数选择
                Text("提前多久提醒 (精准硬件时钟唤醒):", fontSize = 11.5.sp, color = TextSecondary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val advanceOptions = listOf(
                        0 to "准时",
                        5 to "提前5分",
                        10 to "提前10分",
                        15 to "提前15分",
                        30 to "提前30分"
                    )
                    advanceOptions.forEach { (mins, label) ->
                        FilterChip(
                            selected = advanceMinutes == mins,
                            onClick = { advanceMinutes = mins },
                            label = { Text(label, fontSize = 10.5.sp) }
                        )
                    }
                }

                // 响铃与震动模式
                Text("提醒模式:", fontSize = 11.5.sp, color = TextSecondary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("ALL" to "响铃+震动", "ALARM" to "仅响铃", "VIBRATE" to "仅震动", "NONE" to "静音").forEach { (type, label) ->
                        FilterChip(
                            selected = reminderType == type,
                            onClick = { reminderType = type },
                            label = { Text(label, fontSize = 10.5.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val remindDateTime = LocalDateTime.of(selectedDate, selectedTime)
                        val remindIso = remindDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        val nowIso = com.renly.rmf.util.TimeUtils.currentUtcIsoString()

                        val newEntity = ReminderEntity(
                            id = reminder?.id ?: UUID.randomUUID().toString(),
                            title = title.trim(),
                            content = content.trim(),
                            remindTime = remindIso,
                            advanceMinutes = advanceMinutes,
                            repeatRule = repeatRule,
                            reminderType = reminderType,
                            priority = priority,
                            category = category,
                            isCompleted = reminder?.isCompleted ?: 0,
                            createdAt = reminder?.createdAt ?: nowIso,
                            updatedAt = nowIso
                        )
                        onConfirm(newEntity)
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = DopamineCyan),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (reminder == null) "设定提醒" else "保存修改", color = DarkBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        }
    )
}
