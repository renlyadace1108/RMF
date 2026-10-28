package com.renly.rmf.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.data.database.RmfDatabase
import com.renly.rmf.data.local.entity.DailyReportEntity
import com.renly.rmf.data.local.entity.ScheduleEntity
import com.renly.rmf.domain.service.EstimationVarianceService
import com.renly.rmf.domain.service.GapTimeBoxingService
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun DailyRitualDialog(
    initialMode: String = "MORNING", // "MORNING" or "SHUTDOWN"
    schedules: List<ScheduleEntity>,
    backlogSchedules: List<ScheduleEntity>,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf(initialMode) }
    var step by remember { mutableIntStateOf(1) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val todayDate = remember { LocalDate.now() }
    val todayStartIso = remember { todayDate.atStartOfDay().format(DateTimeFormatter.ISO_DATE_TIME) }
    val todayEndIso = remember { todayDate.plusDays(1).atStartOfDay().format(DateTimeFormatter.ISO_DATE_TIME) }

    val overdueTasks = remember(schedules, todayStartIso) {
        schedules.filter {
            it.isDeleted == 0 &&
            it.status != "COMPLETED" &&
            it.status != "ABANDONED" &&
            it.status != "BACKLOG" &&
            (it.isBacklog ?: 0) == 0 &&
            it.startTime < todayStartIso
        }
    }

    val todaySchedules = remember(schedules, todayStartIso, todayEndIso) {
        schedules.filter {
            it.isDeleted == 0 &&
            (it.isBacklog ?: 0) == 0 &&
            it.startTime >= todayStartIso &&
            it.startTime < todayEndIso
        }
    }

    val selectedBacklogIds = remember { mutableStateListOf<String>() }
    var reflectionText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (mode == "MORNING") "🌅 晨间规划仪式" else "🌇 晚间关机仪式",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (mode == "MORNING") DopamineAmber else DopaminePurple
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Text("✕", fontSize = 14.sp, color = TextMuted)
                    }
                }
                Text(
                    text = if (mode == "MORNING") "Sunsama 仪式流 · 掌控今日节奏，消除决策瘫痪" else "Sunsama 仪式流 · 结项卸下心智负担，心安理得休息",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )

                // 4-Step Pill Stepper
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val stepTitles = if (mode == "MORNING") {
                        listOf("① 遗留", "② 格局", "③ 战役", "④ 质检")
                    } else {
                        listOf("① 战果", "② 归档", "③ 复盘", "④ 关机")
                    }

                    stepTitles.forEachIndexed { index, label ->
                        val stepIndex = index + 1
                        val isActive = step == stepIndex
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isActive) (if (mode == "MORNING") DopamineAmber else DopaminePurple).copy(alpha = 0.2f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (isActive) (if (mode == "MORNING") DopamineAmber else DopaminePurple) else DarkBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.5.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                color = if (isActive) (if (mode == "MORNING") DopamineAmber else DopaminePurple) else TextMuted,
                                modifier = Modifier.padding(vertical = 4.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
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
                if (mode == "MORNING") {
                    when (step) {
                        1 -> { // 遗留清点
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkSurface,
                                border = BorderStroke(1.dp, DarkBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    if (overdueTasks.isNotEmpty()) {
                                        Text(
                                            text = "⚡ 发现昨日遗留 ${overdueTasks.size} 项任务",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DopamineAmber
                                        )
                                        Text(
                                            text = "选择清理方式，彻底卸下昨天的心理欠债：",
                                            fontSize = 11.5.sp,
                                            color = TextSecondary,
                                            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        val count = EstimationVarianceService.spilloverTasksToToday(
                                                            RmfDatabase.getInstance(context),
                                                            overdueTasks
                                                        )
                                                        Toast.makeText(context, "已顺延 $count 项任务至今日！", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = DopamineAmber),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("⏩ 顺延今日", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        val count = EstimationVarianceService.movePastTasksToBacklog(
                                                            RmfDatabase.getInstance(context),
                                                            overdueTasks
                                                        )
                                                        Toast.makeText(context, "已移入待办池 $count 项！", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("📦 移入待办", fontSize = 11.5.sp, color = TextPrimary)
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "🎉 历史排期已 100% 全部清空！",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DopamineGreen
                                        )
                                        Text(
                                            text = "没有任何拖延包袱，轻装上阵开启崭新的一天。",
                                            fontSize = 11.5.sp,
                                            color = TextSecondary,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                        2 -> { // 今日格局
                            val freeGaps = remember {
                                GapTimeBoxingService.findDailyFreeGaps(todayDate, todaySchedules, backlogSchedules)
                            }
                            val totalFreeMins = freeGaps.sumOf { it.durationMinutes }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkSurface,
                                border = BorderStroke(1.dp, DarkBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "📅 ${todayDate.format(DateTimeFormatter.ofPattern("M月d日 EEEE"))} · 今日时空格局",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("📌 已排日程", fontSize = 10.5.sp, color = TextMuted)
                                            Text("${todaySchedules.size} 项", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DopamineBlue)
                                        }
                                        Column {
                                            Text("⏳ 自由空当", fontSize = 10.5.sp, color = TextMuted)
                                            Text("%.1f 小时".format(totalFreeMins / 60.0), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DopamineGreen)
                                        }
                                        Column {
                                            Text("🎯 待办备选", fontSize = 10.5.sp, color = TextMuted)
                                            Text("${backlogSchedules.size} 项", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DopaminePurple)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "💡 Sunsama 黄金法则：一天留白 30% 是防止崩盘的关键。请审视今日空档，合理挑选要攻坚的任务。",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                        3 -> { // 战役挑选
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkSurface,
                                border = BorderStroke(1.dp, DarkBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "🎯 挑选今日 1~3 件核心战役 (Rule of 3)",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "勾选的任务将在确认后自动排入今日时间盒：",
                                        fontSize = 11.5.sp,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                                    )

                                    if (backlogSchedules.isEmpty()) {
                                        Text("待办敏捷池中暂无事项，你可以点击上方闪念胶囊快速录入！", fontSize = 11.5.sp, color = TextMuted)
                                    } else {
                                        backlogSchedules.take(5).forEach { b ->
                                            val checked = selectedBacklogIds.contains(b.id)
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(vertical = 2.dp)
                                            ) {
                                                Checkbox(
                                                    checked = checked,
                                                    onCheckedChange = {
                                                        if (it) selectedBacklogIds.add(b.id) else selectedBacklogIds.remove(b.id)
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "${b.title} (${b.estimatedMinutes}m)",
                                                    fontSize = 12.sp,
                                                    color = if (checked) DopamineCyan else TextPrimary,
                                                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        4 -> { // 负荷质检
                            val plannedDeepMins = todaySchedules.filter { it.workType == "DEEP_WORK" }.sumOf { if (it.estimatedMinutes > 0) it.estimatedMinutes else 30 }
                            val deepHours = plannedDeepMins / 60.0
                            val healthColor = if (deepHours <= 4.5) DopamineGreen else if (deepHours <= 5.5) DopamineAmber else DopamineRed
                            val healthText = if (deepHours <= 4.5) "🟢 负荷健康 · 节奏留有充裕弹性" else if (deepHours <= 5.5) "🟡 负荷饱和 · 需全力专注" else "🔴 负荷严重超载 · 极易疲劳延误，强烈建议删减"

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = healthColor.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, healthColor.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "⚖️ 认知负荷与红线质检 (Capacity Audit)",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = healthColor
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "今日深度工作排期: %.1f 小时 / 推荐上限 5.0 小时".format(deepHours),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = healthText,
                                        fontSize = 11.5.sp,
                                        color = healthColor,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // SHUTDOWN MODE
                    when (step) {
                        1 -> { // 今日战果
                            val done = todaySchedules.filter { it.status == "COMPLETED" }
                            val totalMins = done.sumOf { if (it.actualMinutes > 0) it.actualMinutes else it.estimatedMinutes }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DopamineGreen.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, DopamineGreen.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "🏆 今日达成 ${done.size} 项任务，专注 ${totalMins} 分钟！",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DopamineGreen
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    done.take(5).forEach { d ->
                                        Text("✓ ${d.title} (${d.actualMinutes}m)", fontSize = 11.5.sp, color = TextSecondary)
                                    }
                                }
                            }
                        }
                        2 -> { // 遗留归档
                            val unfinished = todaySchedules.filter { it.status != "COMPLETED" && it.status != "ABANDONED" }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkSurface,
                                border = BorderStroke(1.dp, DarkBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    if (unfinished.isNotEmpty()) {
                                        Text(
                                            text = "今日尚有 ${unfinished.size} 项未达成",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DopamineAmber
                                        )
                                        Text(
                                            text = "今天已经全力以赴了。选择归档方式，安心交接给明天：",
                                            fontSize = 11.5.sp,
                                            color = TextSecondary,
                                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        val db = RmfDatabase.getInstance(context)
                                                        unfinished.forEach { u ->
                                                            val startDt = LocalDateTime.parse(u.startTime).plusDays(1)
                                                            val endDt = LocalDateTime.parse(u.endTime).plusDays(1)
                                                            db.scheduleDao().insertOrUpdate(
                                                                u.copy(
                                                                    startTime = startDt.format(DateTimeFormatter.ISO_DATE_TIME),
                                                                    endTime = endDt.format(DateTimeFormatter.ISO_DATE_TIME),
                                                                    syncVersion = (u.syncVersion ?: 1) + 1
                                                                )
                                                            )
                                                        }
                                                        Toast.makeText(context, "已顺延至明天！", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("⏩ 顺延明天", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        EstimationVarianceService.movePastTasksToBacklog(
                                                            RmfDatabase.getInstance(context),
                                                            unfinished
                                                        )
                                                        Toast.makeText(context, "已放回待办池！", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("📦 入待办", fontSize = 11.sp, color = TextPrimary)
                                            }
                                        }
                                    } else {
                                        Text("✨ 今日排期已 100% 全部圆满收官！", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = DopamineGreen)
                                        Text("无需处理任何遗留琐事，尽情放松吧。", fontSize = 11.5.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
                                    }
                                }
                            }
                        }
                        3 -> { // 一言复盘
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkSurface,
                                border = BorderStroke(1.dp, DarkBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("📝 一言复盘与明日备忘", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text("记录今天最满意的一件事，或给明天第一件做的事留个提醒：", fontSize = 11.5.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
                                    OutlinedTextField(
                                        value = reflectionText,
                                        onValueChange = { reflectionText = it },
                                        placeholder = { Text("例如：今天搞定核心模块，明天早晨整理文档...", fontSize = 12.sp, color = TextMuted) },
                                        modifier = Modifier.fillMaxWidth().height(80.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }
                        4 -> { // 关机休眠
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DopaminePurple.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, DopaminePurple.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("🌙 晚间关机完成，心安理得休息！", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = DopaminePurple)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "今日工作已全部妥善归档。放下一切焦虑，充足的睡眠与休息是明日高效的最佳基石。",
                                        fontSize = 11.5.sp,
                                        color = TextSecondary,
                                        lineHeight = 16.sp
                                    )
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
                    if (step < 4) {
                        if (mode == "MORNING" && step == 3 && selectedBacklogIds.isNotEmpty()) {
                            coroutineScope.launch {
                                val db = RmfDatabase.getInstance(context)
                                val toSched = backlogSchedules.filter { selectedBacklogIds.contains(it.id) }
                                var schedStart = LocalDateTime.now().plusMinutes(15)
                                toSched.forEach { item ->
                                    val duration = if (item.estimatedMinutes > 0) item.estimatedMinutes else 45
                                    val newEnd = schedStart.plusMinutes(duration.toLong())
                                    db.scheduleDao().insertOrUpdate(
                                        item.copy(
                                            startTime = schedStart.format(DateTimeFormatter.ISO_DATE_TIME),
                                            endTime = newEnd.format(DateTimeFormatter.ISO_DATE_TIME),
                                            isBacklog = 0,
                                            status = "PENDING",
                                            syncVersion = (item.syncVersion ?: 1) + 1
                                        )
                                    )
                                    schedStart = newEnd.plusMinutes(15)
                                }
                            }
                        }
                        step++
                    } else {
                        // 完成仪式
                        if (mode == "SHUTDOWN" && reflectionText.isNotBlank()) {
                            coroutineScope.launch {
                                val db = RmfDatabase.getInstance(context)
                                db.dailyReportDao().insertOrUpdate(
                                    DailyReportEntity(
                                        date = todayDate.toString(),
                                        reflection = reflectionText,
                                        aiSummary = "",
                                        createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
                                        updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                                    )
                                )
                            }
                        }
                        Toast.makeText(
                            context,
                            if (mode == "MORNING") "🚀 今日排期已锁定，专注执行！" else "🌙 关机完成，安心享受休息！",
                            Toast.LENGTH_SHORT
                        ).show()
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (mode == "MORNING") DopamineAmber else DopaminePurple),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (step < 4) "下一步 ›" else (if (mode == "MORNING") "🚀 开启今日" else "🌙 完成关机"),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            if (step > 1) {
                TextButton(onClick = { step-- }) {
                    Text("‹ 上一步", color = TextSecondary)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("稍后再做", color = TextMuted)
                }
            }
        }
    )
}
