package com.renly.rmf.ui.screens

import android.app.PendingIntent
import android.content.Intent
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
import com.renly.rmf.domain.service.NaturalLanguageScheduleParser
import com.renly.rmf.ui.theme.*
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
    var rawText by remember { mutableStateOf("") }
    var captureMode by remember { mutableStateOf("SCHEDULE") } // SCHEDULE or EXPENSE

    val parsedPreview = remember(rawText, captureMode) {
        if (rawText.isBlank()) null
        else if (captureMode == "SCHEDULE") {
            NaturalLanguageScheduleParser.parse(rawText)
        } else {
            // 解析记账: 如 "午餐 28.5"
            val parts = rawText.trim().split("\\s+".toRegex())
            val amount = parts.lastOrNull()?.toDoubleOrNull() ?: 0.0
            val desc = parts.dropLast(if (amount > 0) 1 else 0).joinToString(" ")
            Pair(desc.ifBlank { "支出项目" }, amount)
        }
    }

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
                    // 头部
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = "闪念捕获",
                            tint = DopamineBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "全局闪念极速捕获",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        // 切换模式: 日程 / 记账
                        FilterChip(
                            selected = captureMode == "SCHEDULE",
                            onClick = { captureMode = "SCHEDULE" },
                            label = { Text("日程", fontSize = 11.sp) }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        FilterChip(
                            selected = captureMode == "EXPENSE",
                            onClick = { captureMode = "EXPENSE" },
                            label = { Text("记账", fontSize = 11.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 输入框
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        placeholder = {
                            Text(
                                if (captureMode == "SCHEDULE") "如：明早 9:00 电路实验 p:高 d:45m #实验"
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

                    // 实时智能解析预览
                    if (rawText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = DarkBg,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "⚡ 智能解析命中预览",
                                    fontSize = 11.sp,
                                    color = DopamineBlue,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (captureMode == "SCHEDULE" && parsedPreview is com.renly.rmf.domain.service.ParsedScheduleResult) {
                                    Text(
                                        text = "任务: ${parsedPreview.title}",
                                        fontSize = 12.sp,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "时间: ${parsedPreview.startTime.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))} (${parsedPreview.durationMinutes}分钟)",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                    if (parsedPreview.tag != null) {
                                        Text(
                                            text = "标签: #${parsedPreview.tag}",
                                            fontSize = 11.sp,
                                            color = DopamineOrange
                                        )
                                    }
                                } else if (parsedPreview is Pair<*, *>) {
                                    Text(
                                        text = "项目: ${parsedPreview.first}   金额: ￥${parsedPreview.second}",
                                        fontSize = 12.sp,
                                        color = DopamineGreen
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 提交按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismissRequest) {
                            Text("取消", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (rawText.isNotBlank()) {
                                    if (captureMode == "SCHEDULE") {
                                        val parsed = NaturalLanguageScheduleParser.parse(rawText)
                                        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                        val newSchedule = ScheduleEntity(
                                            id = UUID.randomUUID().toString(),
                                            title = parsed.title,
                                            startTime = parsed.startTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                                            endTime = parsed.endTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                                            priority = parsed.priority,
                                            category = parsed.category,
                                            status = "PENDING",
                                            createdAt = now,
                                            updatedAt = now
                                        )
                                        onSaveSchedule(newSchedule)
                                    } else {
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
                                    }
                                    onDismissRequest()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("⚡ 立即入库", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    )
}
