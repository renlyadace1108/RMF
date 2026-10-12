package com.renly.rmf.ui.screens

import android.app.PendingIntent
import android.content.Intent
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

                    // 切换模式: 日程 / 记账 (独立通栏双按钮，杜绝截断挤压)
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

                    // 移除智能解析预览卡片，直接极速捕获并入库

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
                                        // 需求：不要自动排期，直接入库进入待排期任务池，由用户在日/周视图手动拖拽排期
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
