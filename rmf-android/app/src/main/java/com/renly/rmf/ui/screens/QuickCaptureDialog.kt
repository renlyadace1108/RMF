package com.renly.rmf.ui.screens

import android.app.PendingIntent
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.data.local.entity.ExpenseEntity
import com.renly.rmf.data.local.entity.ScheduleEntity
import com.renly.rmf.domain.service.AiPreferences
import com.renly.rmf.domain.service.AiScheduleParseResult
import com.renly.rmf.domain.service.AiService
import com.renly.rmf.domain.service.NaturalLanguageScheduleParser
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCaptureDialog(
    onDismissRequest: () -> Unit,
    onSaveSchedule: (ScheduleEntity) -> Unit,
    onSaveExpense: (ExpenseEntity) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var rawText by remember { mutableStateOf("") }
    var captureMode by remember { mutableStateOf("SCHEDULE") } // SCHEDULE or EXPENSE

    val isAiActive = remember {
        AiPreferences.isAiEnabled(context) && AiPreferences.isConfigured(context)
    }

    var isAnalyzingWithAi by remember { mutableStateOf(false) }
    var pendingScheduleCandidate by remember { mutableStateOf<AiScheduleParseResult?>(null) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        content = {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = DarkCard,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    // 头部标题
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = "闪念捕获",
                            tint = DopamineBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "全局闪念极速捕获",
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 切换模式: 日程 / 记账
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = { captureMode = "SCHEDULE" },
                            shape = RoundedCornerShape(8.dp),
                            color = if (captureMode == "SCHEDULE") DopamineBlue.copy(alpha = 0.18f) else DarkSurface,
                            border = BorderStroke(1.dp, if (captureMode == "SCHEDULE") DopamineBlue else DarkBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "📅 日程安排",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (captureMode == "SCHEDULE") DopamineBlue else TextSecondary
                                )
                            }
                        }

                        Surface(
                            onClick = { captureMode = "EXPENSE" },
                            shape = RoundedCornerShape(8.dp),
                            color = if (captureMode == "EXPENSE") DopamineGreen.copy(alpha = 0.18f) else DarkSurface,
                            border = BorderStroke(1.dp, if (captureMode == "EXPENSE") DopamineGreen else DarkBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "💰 随手记账",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (captureMode == "EXPENSE") DopamineGreen else TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 输入框
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        placeholder = {
                            Text(
                                if (captureMode == "SCHEDULE") "如：明早 9:00 电路实验 p:高 d:45m 或 下午3点开会"
                                else "如：午餐 28.5 或 咖啡 18.0",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DopamineBlue,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 状态说明提示
                    if (captureMode == "SCHEDULE") {
                        if (isAiActive) {
                            Text(
                                text = "✨ 已开启智能排期识别，输入具体时间将弹窗询问自动排期",
                                fontSize = 11.sp,
                                color = DopamineCyan
                            )
                        } else {
                            Text(
                                text = "📌 未开启数据分析服务，闪念将直接存入待排期任务池供手动排期",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 提交按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismissRequest,
                            enabled = !isAnalyzingWithAi
                        ) {
                            Text("取消", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (rawText.isNotBlank()) {
                                    if (captureMode == "SCHEDULE") {
                                        if (isAiActive) {
                                            isAnalyzingWithAi = true
                                            coroutineScope.launch {
                                                val parseResult = AiService.parseScheduleWithAi(context, rawText)
                                                isAnalyzingWithAi = false
                                                if (parseResult.hasTime && parseResult.startDateTime != null && parseResult.endDateTime != null) {
                                                    // 识别到具体时间：弹框询问用户是否要排期
                                                    pendingScheduleCandidate = parseResult
                                                } else {
                                                    // AI开启但未识别到时间（纯待办）：直接入库手动排期
                                                    val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                                    val backlogSchedule = ScheduleEntity(
                                                        id = UUID.randomUUID().toString(),
                                                        title = parseResult.title,
                                                        startTime = "",
                                                        endTime = "",
                                                        estimatedMinutes = parseResult.durationMinutes,
                                                        priority = parseResult.priority,
                                                        category = parseResult.category,
                                                        status = "BACKLOG",
                                                        createdAt = now,
                                                        updatedAt = now
                                                    )
                                                    onSaveSchedule(backlogSchedule)
                                                    Toast.makeText(context, "✓ 未包含具体时间，已存入待排期任务池", Toast.LENGTH_SHORT).show()
                                                    onDismissRequest()
                                                }
                                            }
                                        } else {
                                            // 未开AI：直接入库手动排期
                                            val parsed = NaturalLanguageScheduleParser.parse(rawText)
                                            val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                            val newSchedule = ScheduleEntity(
                                                id = UUID.randomUUID().toString(),
                                                title = parsed.title,
                                                startTime = "",
                                                endTime = "",
                                                estimatedMinutes = parsed.durationMinutes,
                                                priority = parsed.priority,
                                                category = parsed.category,
                                                status = "BACKLOG",
                                                createdAt = now,
                                                updatedAt = now
                                            )
                                            onSaveSchedule(newSchedule)
                                            Toast.makeText(context, "✓ 已存入待排期任务池，请在日历中手动排期", Toast.LENGTH_SHORT).show()
                                            onDismissRequest()
                                        }
                                    } else {
                                        // 记账模式
                                        val parts = rawText.trim().split("\\s+".toRegex())
                                        val amount = parts.lastOrNull()?.toDoubleOrNull() ?: 0.0
                                        val desc = parts.dropLast(if (amount > 0) 1 else 0).joinToString(" ").ifBlank { "日常支出" }
                                        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                        val newExpense = ExpenseEntity(
                                            id = UUID.randomUUID().toString(),
                                            category = "餐饮",
                                            amount = amount,
                                            note = desc,
                                            rawInputText = rawText,
                                            transactionTime = now,
                                            createdAt = now
                                        )
                                        onSaveExpense(newExpense)
                                        Toast.makeText(context, "✓ 支出记录已保存", Toast.LENGTH_SHORT).show()
                                        onDismissRequest()
                                    }
                                }
                            },
                            enabled = rawText.isNotBlank() && !isAnalyzingWithAi,
                            colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isAnalyzingWithAi) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("智能识别中...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            } else {
                                Text(if (isAiActive) "⚡ 智能捕获" else "⚡ 立即入库", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    )

    // 自动排期询问确认弹框
    pendingScheduleCandidate?.let { candidate ->
        val sTime = candidate.startDateTime?.format(DateTimeFormatter.ofPattern("M月d日 HH:mm")) ?: ""
        val eTime = candidate.endDateTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: ""

        AlertDialog(
            onDismissRequest = {
                // 点击外部或后退：默认存入待排期任务池手动排期
                val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                val backlogSchedule = ScheduleEntity(
                    id = UUID.randomUUID().toString(),
                    title = candidate.title,
                    startTime = "",
                    endTime = "",
                    estimatedMinutes = candidate.durationMinutes,
                    priority = candidate.priority,
                    category = candidate.category,
                    status = "BACKLOG",
                    createdAt = now,
                    updatedAt = now
                )
                onSaveSchedule(backlogSchedule)
                pendingScheduleCandidate = null
                Toast.makeText(context, "✓ 已存入待排期任务池，可在日历中手动排期", Toast.LENGTH_SHORT).show()
                onDismissRequest()
            },
            containerColor = DarkCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📅 识别到排期时间", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("任务名称：${candidate.title}", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                    Text("建议时段：$sTime ~ $eTime (${candidate.durationMinutes}分钟)", color = DopamineBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("是否确认按此时间自动排期到日程表？若选择手动排期，将存入待排期任务池供您随时拖拽排程。", color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        val startIso = candidate.startDateTime?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) ?: ""
                        val endIso = candidate.endDateTime?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) ?: ""
                        val autoSchedule = ScheduleEntity(
                            id = UUID.randomUUID().toString(),
                            title = candidate.title,
                            startTime = startIso,
                            endTime = endIso,
                            estimatedMinutes = candidate.durationMinutes,
                            priority = candidate.priority,
                            category = candidate.category,
                            status = "PENDING",
                            createdAt = now,
                            updatedAt = now
                        )
                        onSaveSchedule(autoSchedule)
                        pendingScheduleCandidate = null
                        Toast.makeText(context, "✓ 已按识别时间自动排期", Toast.LENGTH_SHORT).show()
                        onDismissRequest()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue)
                ) {
                    Text("确认自动排期", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        val backlogSchedule = ScheduleEntity(
                            id = UUID.randomUUID().toString(),
                            title = candidate.title,
                            startTime = "",
                            endTime = "",
                            estimatedMinutes = candidate.durationMinutes,
                            priority = candidate.priority,
                            category = candidate.category,
                            status = "BACKLOG",
                            createdAt = now,
                            updatedAt = now
                        )
                        onSaveSchedule(backlogSchedule)
                        pendingScheduleCandidate = null
                        Toast.makeText(context, "✓ 已存入待排期任务池，可在日历中手动排期", Toast.LENGTH_SHORT).show()
                        onDismissRequest()
                    }
                ) {
                    Text("仅存入库 (手动排期)", color = TextSecondary)
                }
            }
        )
    }
}
