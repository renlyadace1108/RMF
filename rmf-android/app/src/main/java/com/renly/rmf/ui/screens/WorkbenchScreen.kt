package com.renly.rmf.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.domain.service.AuditReport
import com.renly.rmf.domain.service.AuditFinding
import com.renly.rmf.ui.theme.*

/**
 * WorkbenchScreen (工作台)
 * 聚合核心业务子系统（目标、记账、健身、复盘、统计、习惯、倒数）、节律健康诊断与 AI 洞察助理。
 * 与系统设置（外观个性化、云同步、胶囊通知实验室、数据备份）彻底解耦隔离。
 * 支持页面从左往右滑动直接进入系统设置。
 */
@Composable
fun WorkbenchScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToGoals: () -> Unit,
    onNavigateToExpenses: () -> Unit,
    onNavigateToFitness: () -> Unit = {},
    onNavigateToDailyReport: () -> Unit,
    onNavigateToSummary: () -> Unit,
    onNavigateToTutorial: () -> Unit,
    onNavigateToHabits: () -> Unit = {},
    onNavigateToCountdowns: () -> Unit = {},
    onNavigateToReminders: () -> Unit = {},
    onNavigateToNotes: () -> Unit = {},
    onTriggerAudit: () -> AuditReport,
    onTriggerAiReview: (String, (String) -> Unit) -> Unit
) {
    var auditReport by remember { mutableStateOf<AuditReport?>(null) }
    var aiReviewText by remember { mutableStateOf<String?>(null) }
    var isGeneratingAi by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // ---------------------------------------------------------------------
        // 顶部工作台标题栏与系统设置快速跳转入口
        // ---------------------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🚀", fontSize = 22.sp, modifier = Modifier.padding(end = 8.dp))
                    Text(
                        text = "工作台",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Text(
                    text = "多维效率与全域管理中枢 · 目标、财务、习惯、统计与健康审计",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---------------------------------------------------------------------
        // 模块 1: 核心业务功能矩阵 (Functional Matrix)
        // ---------------------------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚡", fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
            Text(
                text = "核心功能矩阵",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
        Text(
            text = "点击卡片快速直达各大子系统模块，高效调度日常生活与多维效能",
            fontSize = 11.5.sp,
            color = TextSecondary,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
        )

        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        val appColors = LocalAppColors.current
        val functionalCards = remember(
            onNavigateToGoals, onNavigateToExpenses, onNavigateToFitness,
            onNavigateToDailyReport, onNavigateToSummary, onNavigateToHabits,
            onNavigateToCountdowns, onNavigateToReminders, onNavigateToNotes, onNavigateToTutorial, appColors
        ) {
            listOf(
                FunctionalItem(
                    title = "随记便签",
                    subtitle = "灵感火花与便签备忘",
                    icon = Icons.Default.EditNote,
                    color = appColors.dopamineAmber,
                    onClick = onNavigateToNotes
                ),
                FunctionalItem(
                    title = "事件提醒",
                    subtitle = "单点提醒与实时活动胶囊",
                    icon = Icons.Default.NotificationsActive,
                    color = appColors.dopamineCyan,
                    onClick = onNavigateToReminders
                ),
                FunctionalItem(
                    title = "目标金字塔",
                    subtitle = "OKR与北极星目标追踪",
                    icon = Icons.Default.Flag,
                    color = appColors.dopaminePurple,
                    onClick = onNavigateToGoals
                ),
                FunctionalItem(
                    title = "日常记账",
                    subtitle = "多维收支账本与月度报表",
                    icon = Icons.Default.AccountBalanceWallet,
                    color = appColors.dopamineAmber,
                    onClick = onNavigateToExpenses
                ),
                FunctionalItem(
                    title = "每日健身",
                    subtitle = "动作库、肌群计划与训练",
                    icon = Icons.Default.FitnessCenter,
                    color = appColors.dopamineGreen,
                    onClick = onNavigateToFitness
                ),
                FunctionalItem(
                    title = "每日复盘",
                    subtitle = "反思成就与智能复盘",
                    icon = Icons.Default.AutoAwesome,
                    color = appColors.dopamineOrange,
                    onClick = onNavigateToDailyReport
                ),
                FunctionalItem(
                    title = "效能统计",
                    subtitle = "全周期时间与打断透视",
                    icon = Icons.Default.Insights,
                    color = appColors.dopamineCyan,
                    onClick = onNavigateToSummary
                ),
                FunctionalItem(
                    title = "习惯打卡",
                    subtitle = "习惯连续 Streak 追踪",
                    icon = Icons.Default.SelfImprovement,
                    color = appColors.dopamineGreen,
                    onClick = onNavigateToHabits
                ),
                FunctionalItem(
                    title = "倒数纪念日",
                    subtitle = "重要里程碑与考试倒数",
                    icon = Icons.Default.HourglassBottom,
                    color = appColors.dopaminePurple,
                    onClick = onNavigateToCountdowns
                ),
                FunctionalItem(
                    title = "使用教程",
                    subtitle = "全功能操作指南与快捷键",
                    icon = Icons.Default.MenuBook,
                    color = appColors.dopamineBlue,
                    onClick = onNavigateToTutorial
                ),
                FunctionalItem(
                    title = "桌面小组件",
                    subtitle = "微件管理与一键桌面放置",
                    icon = Icons.Default.Widgets,
                    color = appColors.dopamineCyan,
                    onClick = onNavigateToSettings
                )
            )
        }

        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isPad = com.renly.rmf.BuildConfig.IS_PAD_EDITION || configuration.screenWidthDp >= 600
        val columns = if (configuration.screenWidthDp >= 840) 4 else if (isPad) 3 else 2

        functionalCards.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, item.color.copy(alpha = 0.28f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                item.onClick()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 11.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = item.color.copy(alpha = 0.14f),
                                border = BorderStroke(0.5.dp, item.color.copy(alpha = 0.35f)),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title,
                                        tint = item.color,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(9.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = item.subtitle,
                                    fontSize = 10.5.sp,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ---------------------------------------------------------------------
        // 模块 2: 🩺 节律健康与数据审计 (Rhythm & Conflict Health Audit)
        // ---------------------------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🩺", fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
            Text(
                text = "节律健康与排期审计",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
        Text(
            text = "深度诊断时间排期硬冲突、连续4小时超载疲劳断崖与认知负荷均衡度",
            fontSize = 11.5.sp,
            color = TextSecondary,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "日程排期节律诊断",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "检测并规避认知疲劳峰值与重叠排期",
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }

                    Button(
                        onClick = { auditReport = onTriggerAudit() },
                        colors = ButtonDefaults.buttonColors(containerColor = DopamineCyan),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("立即诊断", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                if (auditReport != null) {
                    val report = auditReport!!
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (report.findings.isEmpty()) "🎉 完美节律！未检测到排期冲突或超载" else "⚠️ 发现 ${report.findings.size} 项节律调整建议",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (report.findings.isEmpty()) DopamineGreen else DopamineAmber
                        )
                        Surface(
                            color = if (report.score >= 80) DopamineGreen.copy(alpha = 0.2f) else DopamineAmber.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "健康评分: ${report.score}分",
                                color = if (report.score >= 80) DopamineGreen else DopamineAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    report.findings.take(3).forEach { finding ->
                        val isDanger = finding.level == "DANGER"
                        val findingColor = if (isDanger) DopamineRed else DopamineAmber
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DarkSurface,
                            border = BorderStroke(
                                1.dp,
                                findingColor.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = finding.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = findingColor
                                )
                                Text(
                                    text = finding.description,
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ---------------------------------------------------------------------
        // 模块 3: 🤖 AI 智能复盘与效率洞察 (AI Insights Assistant)
        // ---------------------------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🤖", fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
            Text(
                text = "AI 智能复盘与效率洞察",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
        Text(
            text = "结合大模型算力，深度总结日程交付完成度，生成具有指导意义的心得洞察",
            fontSize = 11.5.sp,
            color = TextSecondary,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val isAiEnabled = remember { com.renly.rmf.domain.service.AiPreferences.isAiEnabled(context) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "AI 智能每日复盘总结",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            if (!isAiEnabled) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = DarkBorder,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "已停用",
                                        fontSize = 10.sp,
                                        color = TextMuted,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (isAiEnabled) "聚合任务、被打断、专注时长与知识积累" else "AI 功能已在系统设置中关闭",
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }

                    Button(
                        onClick = {
                            if (!isAiEnabled) {
                                android.widget.Toast.makeText(context, "AI 智能功能已在系统设置中停用，请前往设置开启", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isGeneratingAi = true
                            aiReviewText = null
                            onTriggerAiReview("请根据我今日的完成情况进行高效复盘并提出优化建议") { result ->
                                isGeneratingAi = false
                                aiReviewText = result
                            }
                        },
                        enabled = !isGeneratingAi && isAiEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAiEnabled) DopaminePurple else DarkBorder,
                            disabledContainerColor = DarkBorder
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            if (!isAiEnabled) "已停用" else if (isGeneratingAi) "正在生成..." else "生成 AI 复盘",
                            color = if (isAiEnabled) Color.White else TextMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                if (!isAiEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "💡 提示：当前已在系统设置中关闭 AI 功能总开关。开启后可在此生成具有指导意义的心得洞察。",
                        fontSize = 11.5.sp,
                        color = TextMuted,
                        lineHeight = 16.sp
                    )
                }

                if (aiReviewText != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, DopaminePurple.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "🤖 AI 导师复盘意见：",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = DopaminePurple
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = aiReviewText!!,
                                fontSize = 11.5.sp,
                                color = TextPrimary,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

private data class FunctionalItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit
)
