package com.renly.rmf.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.data.local.entity.DailyReportEntity
import com.renly.rmf.data.local.entity.ScheduleEntity
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun DailyReportScreen(
    currentDate: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    todaySchedulesFlow: Flow<List<ScheduleEntity>>,
    onSaveReport: (DailyReportEntity) -> Unit
) {
    val schedules by todaySchedulesFlow.collectAsState(initial = emptyList())
    val clipboardManager = LocalClipboardManager.current

    var summaryText by remember { mutableStateOf("") }
    var planText by remember { mutableStateOf("") }
    var reflectionText by remember { mutableStateOf("") }

    val completedTasks = schedules.filter { it.status == "COMPLETED" }
    val totalTasks = schedules.size
    val completionRate = if (totalTasks > 0) (completedTasks.size * 100 / totalTasks) else 0

    // 智能自动生成日报草稿
    fun generateDraft() {
        val completedSummary = if (completedTasks.isNotEmpty()) {
            completedTasks.joinToString("\n") { "• [已达成] ${it.title} (${it.category})" }
        } else {
            "今日无已完成事项。"
        }
        val pendingTasks = schedules.filter { it.status != "COMPLETED" }
        val pendingSummary = if (pendingTasks.isNotEmpty()) {
            pendingTasks.joinToString("\n") { "• [待推进] ${it.title}" }
        } else {
            "今日所有规划均已百分之百完成！"
        }

        summaryText = completedSummary
        planText = pendingSummary
        reflectionText = "今日完成率 ${completionRate}%。保持聚焦，减少打断，稳步推进北极星目标。"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(16.dp)
    ) {
        // 1. 顶部日期与操作栏
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "🏆 每日复盘日报",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = currentDate.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 EEE")),
                        fontSize = 12.sp,
                        color = DopamineBlue,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onDateChange(currentDate.minusDays(1)) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "前一天", tint = TextPrimary)
                    }
                    IconButton(onClick = { onDateChange(currentDate.plusDays(1)) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "后一天", tint = TextPrimary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 2. 核心指标卡片
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("总任务", fontSize = 11.sp, color = TextMuted)
                        Text("$totalTasks 项", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("已交付", fontSize = 11.sp, color = TextMuted)
                        Text("${completedTasks.size} 项", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DopamineGreen)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("达成率", fontSize = 11.sp, color = TextMuted)
                        Text("$completionRate%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DopamineBlue)
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 3. 智能草稿生成与复制按钮
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { generateDraft() },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = DopaminePurple, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("智能生成草稿", fontSize = 12.sp, color = DopaminePurple)
                }

                Button(
                    onClick = {
                        val fullReport = """
# RMF 每日对账复盘 (${currentDate})
## 1. 今日交付
$summaryText

## 2. 明日推进
$planText

## 3. 心流与复盘
$reflectionText
                        """.trimIndent()
                        clipboardManager.setText(AnnotatedString(fullReport))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = DopamineBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("复制 Markdown", fontSize = 12.sp, color = DopamineBlue)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 4. 今日工作总结
        item {
            Text("✅ 今日完成工作 (Done)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = summaryText,
                onValueChange = { summaryText = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DopamineBlue,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 5. 明日攻坚计划
        item {
            Text("🎯 明日攻坚计划 (Plan)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = planText,
                onValueChange = { planText = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DopamineBlue,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 6. 心流反思与卡点
        item {
            Text("💡 效率反思与心得 (Reflection)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = reflectionText,
                onValueChange = { reflectionText = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DopamineBlue,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(18.dp))
        }

        // 7. 保存按钮
        item {
            Button(
                onClick = {
                    val nowStr = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_DATE_TIME)
                    val report = DailyReportEntity(
                        date = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        reflection = "【今日交付】\n$summaryText\n\n【明日计划】\n$planText\n\n【心得反思】\n$reflectionText",
                        aiSummary = summaryText,
                        createdAt = nowStr,
                        updatedAt = nowStr
                    )
                    onSaveReport(report)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("保存复盘日报", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
