package com.renly.rmf.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.domain.service.AuditReport
import com.renly.rmf.domain.service.GoogleDrivePreferences
import com.renly.rmf.domain.service.GoogleDriveSyncService
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    currentThemeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onNavigateToGoals: () -> Unit,
    onNavigateToExpenses: () -> Unit,
    onNavigateToFitness: () -> Unit = {},
    onNavigateToDailyReport: () -> Unit,
    onNavigateToSummary: () -> Unit,
    onNavigateToTutorial: () -> Unit,
    onTriggerAudit: () -> AuditReport,
    onTriggerAiReview: (String, (String) -> Unit) -> Unit,
    onExportData: () -> Unit,
    onExportLosslessDb: () -> Unit = {},
    onImportLosslessDb: () -> Unit = {},
    onClearAllBusinessData: () -> Unit = {},
    onTriggerUpload: () -> Unit,
    onTriggerDownload: () -> Unit
) {
    var geminiApiKey by remember { mutableStateOf("") }
    var auditReport by remember { mutableStateOf<AuditReport?>(null) }
    var aiReviewText by remember { mutableStateOf<String?>(null) }
    var isGeneratingAi by remember { mutableStateOf(false) }
    var showTagModal by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "工作台与高级功能",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 主题外观切换卡片
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkCard,
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "🎨 主题外观模式",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "自由切换日间清爽、夜间深邃或自动跟随系统",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.values().forEach { mode ->
                        val isSelected = currentThemeMode == mode
                        Surface(
                            onClick = { onThemeModeChange(mode) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) DopamineBlue.copy(alpha = 0.18f) else DarkSurface,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) DopamineBlue else DarkBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(mode.icon, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = mode.title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) DopamineBlue else TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 核心子系统快捷入口
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onNavigateToGoals,
                colors = ButtonDefaults.buttonColors(containerColor = DopaminePurple),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text("🎯 目标金字塔", fontSize = 11.5.sp)
            }

            Button(
                onClick = onNavigateToExpenses,
                colors = ButtonDefaults.buttonColors(containerColor = DopamineAmber),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text("💰 日常记账", fontSize = 11.5.sp)
            }

            Button(
                onClick = onNavigateToFitness,
                colors = ButtonDefaults.buttonColors(containerColor = DopamineCyan),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text("🏋️ 每日健身", fontSize = 11.5.sp, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onNavigateToDailyReport,
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text("🏆 每日复盘", fontSize = 11.5.sp, color = DopamineGreen)
            }

            Button(
                onClick = onNavigateToSummary,
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text("📊 效能统计", fontSize = 11.5.sp, color = DopamineBlue)
            }

            Button(
                onClick = onNavigateToTutorial,
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text("📖 使用教程", fontSize = 11.5.sp, color = TextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 电脑版 1:1 标签分类库管理入口
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { showTagModal = true },
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, DopaminePurple.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text("🏷️ 标签分类库与色彩管理 (Tag Management)", fontSize = 12.sp, color = DopaminePurple, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 节律健康审计卡片
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🩺 日程节律健康审计",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "深度诊断时间硬冲突排期、连续4小时超载疲劳断崖与认知负荷均衡度。",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                Button(
                    onClick = { auditReport = onTriggerAudit() },
                    colors = ButtonDefaults.buttonColors(containerColor = DopamineCyan),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("立即执行节律诊断", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Gemini AI 每日智能复盘卡片
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🤖 Gemini AI 每日智能复盘",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "基于今日实际完成日程、打断损耗与学习学时，一键生成结构化复盘日志。",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                OutlinedTextField(
                    value = geminiApiKey,
                    onValueChange = { geminiApiKey = it },
                    label = { Text("Gemini API Key") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        isGeneratingAi = true
                        onTriggerAiReview(geminiApiKey) { result ->
                            aiReviewText = result
                            isGeneratingAi = false
                        }
                    },
                    enabled = !isGeneratingAi,
                    colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isGeneratingAi) "AI 正在深度思考分析中..." else "生成今日智能复盘")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Google Drive 官方 API 跨端同步卡片 (1:1 像素级对齐 PC 端)
        val currentContext = androidx.compose.ui.platform.LocalContext.current
        var googleClientId by remember { mutableStateOf(GoogleDrivePreferences.getClientId(currentContext)) }
        var googleClientSecret by remember { mutableStateOf(GoogleDrivePreferences.getClientSecret(currentContext)) }
        var driveStatusText by remember { mutableStateOf("") }
        var lastSyncDisplay by remember { mutableStateOf(GoogleDrivePreferences.getLastSyncTime(currentContext)) }
        var isDriveWorking by remember { mutableStateOf(false) }
        var showAuthCodeDialog by remember { mutableStateOf(false) }
        var authCodeInput by remember { mutableStateOf("") }

        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("☁️", fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
                        Text(
                            text = "Google Drive 云端安全备份与同步",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    val isLinked = GoogleDrivePreferences.isAuthorized(currentContext)
                    Surface(
                        color = if (isLinked) Color(0xFF064E3B) else Color(0xFF1E293B),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, if (isLinked) Color(0xFF059669) else DopamineBlue)
                    ) {
                        Text(
                            text = if (isLinked) "已授权连接" else "未连接",
                            color = if (isLinked) Color(0xFF6EE7B7) else DopamineBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = "基于 Google Drive 官方 API，将整个 SQLite 数据库（含所有日程、课程表、每日成果、学习档案）完整安全备份至云端。与 Windows 电脑端 100% 互通，确保访问并读写同一个云端数据库文件 (rmf_cloud_backup.db)。",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                Text(
                    text = "Google Cloud OAuth 2.0 凭据配置：",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DopamineBlue,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                OutlinedTextField(
                    value = googleClientId,
                    onValueChange = { googleClientId = it },
                    label = { Text("Client ID (.apps.googleusercontent.com)", fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = DopamineBlue,
                        unfocusedBorderColor = DarkBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = googleClientSecret,
                    onValueChange = { googleClientSecret = it },
                    label = { Text("Client Secret", fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = DopamineBlue,
                        unfocusedBorderColor = DarkBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "上次备份：$lastSyncDisplay",
                        fontSize = 11.5.sp,
                        color = TextMuted,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            GoogleDrivePreferences.setClientId(currentContext, googleClientId)
                            GoogleDrivePreferences.setClientSecret(currentContext, googleClientSecret)
                            driveStatusText = "✓ OAuth 凭据已保存到本地配置！"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("保存凭据", fontSize = 12.sp, color = Color.White)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    OutlinedButton(
                        onClick = {
                            GoogleDrivePreferences.clearAuth(currentContext)
                            driveStatusText = "已解除授权并清除本地 Token。"
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DopamineRed),
                        border = BorderStroke(1.dp, DopamineRed),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("解除授权", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 云端操作按钮 (API 互通)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val cId = googleClientId.trim()
                            val cSec = googleClientSecret.trim()
                            if (cId.isBlank()) {
                                driveStatusText = "✗ 请先填写 Client ID"
                                return@Button
                            }
                            GoogleDrivePreferences.setClientId(currentContext, cId)
                            GoogleDrivePreferences.setClientSecret(currentContext, cSec)

                            if (!GoogleDrivePreferences.isAuthorized(currentContext)) {
                                // 启动授权弹窗
                                showAuthCodeDialog = true
                            } else {
                                isDriveWorking = true
                                driveStatusText = "正在调用 Google Drive API 上传..."
                                onTriggerUpload()
                            }
                        },
                        enabled = !isDriveWorking,
                        colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("⬆ 上传完整备份至云端", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            val cId = googleClientId.trim()
                            val cSec = googleClientSecret.trim()
                            if (cId.isBlank()) {
                                driveStatusText = "✗ 请先填写 Client ID"
                                return@OutlinedButton
                            }
                            GoogleDrivePreferences.setClientId(currentContext, cId)
                            GoogleDrivePreferences.setClientSecret(currentContext, cSec)

                            if (!GoogleDrivePreferences.isAuthorized(currentContext)) {
                                showAuthCodeDialog = true
                            } else {
                                isDriveWorking = true
                                driveStatusText = "正在从 Google Drive API 拉取..."
                                onTriggerDownload()
                            }
                        },
                        enabled = !isDriveWorking,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DopamineGreen),
                        border = BorderStroke(1.dp, DopamineGreen),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("⬇ 从云端拉取恢复本地", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (driveStatusText.isNotBlank()) {
                    Text(
                        text = driveStatusText,
                        fontSize = 11.5.sp,
                        color = if (driveStatusText.startsWith("✓")) DopamineGreen else if (driveStatusText.startsWith("✗")) DopamineRed else DopamineCyan,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // OAuth 2.0 快捷授权引导弹窗
        if (showAuthCodeDialog) {
            AlertDialog(
                onDismissRequest = { showAuthCodeDialog = false },
                containerColor = DarkSurface,
                title = { Text("🔑 授权 Google Drive API", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "为了让安卓端与电脑端访问同一个云盘文件，需要完成一次 OAuth 2.0 授权：",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )

                        Button(
                            onClick = {
                                val cId = GoogleDrivePreferences.getClientId(currentContext)
                                val authUrl = GoogleDriveSyncService.buildAuthorizationUrl(cId)
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(authUrl))
                                currentContext.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("1. 打开浏览器登录并获取授权码", fontSize = 12.sp)
                        }

                        Text("2. 在下方粘贴授权码 (Authorization Code) 或直接输入 Access Token：", fontSize = 12.sp, color = TextSecondary)

                        OutlinedTextField(
                            value = authCodeInput,
                            onValueChange = { authCodeInput = it },
                            placeholder = { Text("粘贴 4/0A... 授权码或 ya29... Token", fontSize = 11.sp, color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = DopamineBlue,
                                unfocusedBorderColor = DarkBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val code = authCodeInput.trim()
                            if (code.isNotBlank()) {
                                if (code.startsWith("ya29.")) {
                                    // 直接作为 Access Token 存入
                                    GoogleDrivePreferences.setAccessToken(currentContext, code)
                                    driveStatusText = "✓ Access Token 已保存生效！"
                                    showAuthCodeDialog = false
                                } else {
                                    // 调用换取 Token
                                    driveStatusText = "正在兑换 Token..."
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                        val res = GoogleDriveSyncService.exchangeAuthCode(
                                            clientId = GoogleDrivePreferences.getClientId(currentContext),
                                            clientSecret = GoogleDrivePreferences.getClientSecret(currentContext),
                                            authCode = code
                                        )
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            if (res.isSuccess) {
                                                val (acc, ref) = res.getOrThrow()
                                                GoogleDrivePreferences.setAccessToken(currentContext, acc)
                                                if (ref != null) GoogleDrivePreferences.setRefreshToken(currentContext, ref)
                                                driveStatusText = "✓ Google Drive 授权成功！"
                                                showAuthCodeDialog = false
                                            } else {
                                                driveStatusText = "✗ 授权失败: ${res.exceptionOrNull()?.message}"
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DopamineGreen)
                    ) {
                        Text("完成授权", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAuthCodeDialog = false }) {
                        Text("取消", color = TextMuted)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 📅 Google Calendar & .ics 日历互通同步卡片 (对齐 Windows GoogleCalendarService)
        var calSyncStatusText by remember { mutableStateOf("") }
        var isCalWorking by remember { mutableStateOf(false) }
        var icsUrlInput by remember { mutableStateOf(GoogleDrivePreferences.getCalendarIcsUrl(currentContext)) }
        var lastCalSyncTime by remember { mutableStateOf(GoogleDrivePreferences.getCalendarLastSyncTime(currentContext)) }
        val coroutineScope = rememberCoroutineScope()

        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📅", fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
                        Text(
                            text = "Google Calendar 日历互通同步",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    val isLinked = GoogleDrivePreferences.isAuthorized(currentContext)
                    Surface(
                        color = if (isLinked) Color(0xFF064E3B) else Color(0xFF1E293B),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, if (isLinked) Color(0xFF059669) else DopamineBlue)
                    ) {
                        Text(
                            text = if (isLinked) "API 已就绪" else "未授权 OAuth",
                            color = if (isLinked) Color(0xFF6EE7B7) else DopamineBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = "支持通过 Google Calendar API 直连推送日程与上课计划；同时支持标准 RFC 5545 .ics 文件导出或 URL 私密订阅，在手机系统日历/Google日历中实时查看。",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
                )

                Text(
                    text = "上次同步时间: $lastCalSyncTime",
                    fontSize = 11.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 按钮组 1: Google Calendar REST API 直连推送与拉取
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (!GoogleDrivePreferences.isAuthorized(currentContext)) {
                                showAuthCodeDialog = true
                                return@Button
                            }
                            isCalWorking = true
                            calSyncStatusText = "正在推送日程至 Google Calendar..."
                            coroutineScope.launch {
                                val db = (currentContext.applicationContext as com.renly.rmf.RmfApplication).database
                                val service = com.renly.rmf.domain.service.GoogleCalendarSyncService(currentContext, db)
                                val res = service.pushSchedulesToGoogleCalendar()
                                isCalWorking = false
                                when (res) {
                                    is com.renly.rmf.domain.service.CalendarSyncResult.Success -> {
                                        calSyncStatusText = "✓ ${res.message}"
                                        lastCalSyncTime = GoogleDrivePreferences.getCalendarLastSyncTime(currentContext)
                                    }
                                    is com.renly.rmf.domain.service.CalendarSyncResult.Error -> {
                                        calSyncStatusText = "✗ ${res.error}"
                                    }
                                }
                            }
                        },
                        enabled = !isCalWorking,
                        colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🚀 推送日程至 Google", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            if (!GoogleDrivePreferences.isAuthorized(currentContext)) {
                                showAuthCodeDialog = true
                                return@OutlinedButton
                            }
                            isCalWorking = true
                            calSyncStatusText = "正在从 Google Calendar 拉取事件..."
                            coroutineScope.launch {
                                val db = (currentContext.applicationContext as com.renly.rmf.RmfApplication).database
                                val service = com.renly.rmf.domain.service.GoogleCalendarSyncService(currentContext, db)
                                val res = service.pullEventsFromGoogleCalendar(30)
                                isCalWorking = false
                                when (res) {
                                    is com.renly.rmf.domain.service.CalendarSyncResult.Success -> {
                                        calSyncStatusText = "✓ ${res.message}"
                                        lastCalSyncTime = GoogleDrivePreferences.getCalendarLastSyncTime(currentContext)
                                    }
                                    is com.renly.rmf.domain.service.CalendarSyncResult.Error -> {
                                        calSyncStatusText = "✗ ${res.error}"
                                    }
                                }
                            }
                        },
                        enabled = !isCalWorking,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DopamineGreen),
                        border = BorderStroke(1.dp, DopamineGreen),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("⬇ 从 Google 拉取日程", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 按钮组 2: 导出 .ics 通用日历文件 (含课表与日程)
                OutlinedButton(
                    onClick = {
                        isCalWorking = true
                        calSyncStatusText = "正在生成 .ics 日历文件..."
                        coroutineScope.launch {
                            val db = (currentContext.applicationContext as com.renly.rmf.RmfApplication).database
                            val service = com.renly.rmf.domain.service.GoogleCalendarSyncService(currentContext, db)
                            val res = service.exportSchedulesToIcsFile(includeCourses = true)
                            isCalWorking = false
                            if (res.isSuccess) {
                                val file = res.getOrThrow()
                                calSyncStatusText = "✓ 已生成 .ics，调起日历分享..."
                                service.shareIcsFile(file)
                            } else {
                                calSyncStatusText = "✗ 导出失败: ${res.exceptionOrNull()?.message}"
                            }
                        }
                    },
                    enabled = !isCalWorking,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("📁 导出 .ics 通用日历文件 (课表+日程，支持所有日历导入)", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 输入框与按钮组 3: Google Calendar iCal 订阅 URL (对齐 Windows 端)
                Text(
                    text = "或者输入 Google Calendar 私密 iCal 订阅地址 (.ics)：",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                OutlinedTextField(
                    value = icsUrlInput,
                    onValueChange = { icsUrlInput = it },
                    placeholder = { Text("https://calendar.google.com/calendar/ical/.../basic.ics", fontSize = 11.sp, color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = DopamineBlue,
                        unfocusedBorderColor = DarkBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = {
                        val url = icsUrlInput.trim()
                        if (url.isBlank()) {
                            calSyncStatusText = "✗ 请输入有效的 .ics 订阅 URL"
                            return@OutlinedButton
                        }
                        isCalWorking = true
                        calSyncStatusText = "正在从日历订阅地址同步..."
                        coroutineScope.launch {
                            val db = (currentContext.applicationContext as com.renly.rmf.RmfApplication).database
                            val service = com.renly.rmf.domain.service.GoogleCalendarSyncService(currentContext, db)
                            val res = service.syncFromIcsUrl(url)
                            isCalWorking = false
                            when (res) {
                                is com.renly.rmf.domain.service.CalendarSyncResult.Success -> {
                                    calSyncStatusText = "✓ ${res.message}"
                                    lastCalSyncTime = GoogleDrivePreferences.getCalendarLastSyncTime(currentContext)
                                }
                                is com.renly.rmf.domain.service.CalendarSyncResult.Error -> {
                                    calSyncStatusText = "✗ ${res.error}"
                                }
                            }
                        }
                    },
                    enabled = !isCalWorking,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DopamineCyan),
                    border = BorderStroke(1.dp, DopamineCyan),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("🔄 从 iCal 订阅地址同步日程", fontSize = 12.sp)
                }

                if (calSyncStatusText.isNotBlank()) {
                    Text(
                        text = calSyncStatusText,
                        fontSize = 11.5.sp,
                        color = if (calSyncStatusText.startsWith("✓")) DopamineGreen else if (calSyncStatusText.startsWith("✗")) DopamineRed else DopamineCyan,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🛡️", fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
                        Text(
                            text = "无损升级与数据安全防护",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Surface(
                        color = Color(0xFF064E3B),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFF059669))
                    ) {
                        Text(
                            text = "无损保护中",
                            color = Color(0xFF6EE7B7),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = "手机端与电脑端遵循相同升级保障：所有新版本直接覆盖安装，底层数据库结构自动增量对齐，历史数据 100% 完整保留。每次升级或冷启动均自动归档安全快照。",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
                )

                Text(
                    text = com.renly.rmf.domain.service.LosslessUpgradeManager.getLastBackupTime(currentContext),
                    fontSize = 11.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onExportLosslessDb,
                        colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("💾 导出无损数据包", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onImportLosslessDb,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DopamineGreen),
                        border = BorderStroke(1.dp, DopamineGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📥 从数据包还原", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onExportData,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = BorderStroke(1.dp, DarkBorder),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📤 导出开放 JSON 并分享", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                var showClearConfirmDialog by remember { mutableStateOf(false) }

                OutlinedButton(
                    onClick = { showClearConfirmDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DopamineRed),
                    border = BorderStroke(1.dp, DopamineRed.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🗑️ 清空所有业务测试数据 (全新空白开始)", fontSize = 12.sp, color = DopamineRed, fontWeight = FontWeight.SemiBold)
                }

                if (showClearConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearConfirmDialog = false },
                        containerColor = DarkSurface,
                        title = {
                            Text("⚠️ 确认清空所有测试业务数据？", color = DopamineRed, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        },
                        text = {
                            Text(
                                "此操作将清空所有日程、课程、目标、学习记录、健身计划与打卡记录，为您提供一个全新的空白工作台（系统配置与主题模式将保留）。\n\n清空前系统将自动生成一份带时间戳的安全数据快照备份。\n\n确认清空吗？",
                                fontSize = 12.5.sp,
                                color = TextSecondary,
                                lineHeight = 18.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showClearConfirmDialog = false
                                    onClearAllBusinessData()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DopamineRed)
                            ) {
                                Text("确认清空", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearConfirmDialog = false }) {
                                Text("取消", color = TextMuted)
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // © 软件品牌、版权与版本声明 (对齐 Windows 客户端)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.size(24.dp)
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.renly.rmf.R.drawable.ic_logo),
                        contentDescription = "RMF",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "RMF",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Text(
                        text = "v${com.renly.rmf.BuildConfig.VERSION_NAME}",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Made By Renly 2026",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
            Text(
                text = "renly20061108@gmail.com",
                fontSize = 10.5.sp,
                color = TextMuted,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // 节律健康诊断弹窗
    auditReport?.let { report ->
        AlertDialog(
            onDismissRequest = { auditReport = null },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "节律健康诊断评分: ${report.score} 分",
                    color = if (report.score >= 80) DopamineGreen else DopamineAmber,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("深度工作: ${report.totalDeepWorkMinutes}分钟 | 缓冲休息: ${report.totalRestMinutes}分钟", fontSize = 12.sp, color = TextSecondary)
                    HorizontalDivider(color = DarkBorder)
                    report.findings.forEach { finding ->
                        val color = when (finding.level) {
                            "DANGER" -> DopamineRed
                            "WARNING" -> DopamineAmber
                            else -> DopamineGreen
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(finding.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
                                Text(finding.description, fontSize = 11.sp, color = TextPrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { auditReport = null }) { Text("已知晓") }
            }
        )
    }

    // AI 复盘展示弹窗
    aiReviewText?.let { review ->
        AlertDialog(
            onDismissRequest = { aiReviewText = null },
            containerColor = DarkSurface,
            title = { Text("今日 Gemini AI 智能复盘", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(review, color = TextPrimary, fontSize = 13.sp, lineHeight = 20.sp)
                }
            },
            confirmButton = {
                Button(onClick = { aiReviewText = null }) { Text("完成") }
            }
        )
    }

    // 标签分类库与色彩管理弹窗 (TagManagementModal)
    if (showTagModal) {
        TagManagementDialog(
            onDismiss = { showTagModal = false }
        )
    }
}

// -----------------------------------------------------------------------------------------
// 🏷️ 电脑版 1:1 标签分类库与多巴胺色彩管理弹窗 (TagManagementModal + RgbColorPickerModal)
// -----------------------------------------------------------------------------------------
@Composable
fun TagManagementDialog(
    onDismiss: () -> Unit
) {
    val defaultTags = remember {
        mutableStateListOf(
            Pair("工作", "#38BDF8"),
            Pair("学习", "#34D399"),
            Pair("生活", "#FBBF24"),
            Pair("健康", "#F472B6"),
            Pair("财务", "#FB923C"),
            Pair("技术", "#A78BFA"),
            Pair("攻坚", "#22D3EE"),
            Pair("紧急", "#F87171")
        )
    }

    var newTagName by remember { mutableStateOf("") }
    var selectedColorIdx by remember { mutableIntStateOf(0) }

    val colorPalette = listOf(
        "#38BDF8",
        "#34D399",
        "#FBBF24",
        "#FB923C",
        "#F472B6",
        "#A78BFA",
        "#F87171",
        "#22D3EE"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("🏷️ 日程分类标签库与色彩", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("管理系统日程业务标签体系与专属高质感色彩：", fontSize = 11.5.sp, color = TextSecondary)

                // 添加新标签
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newTagName,
                        onValueChange = { newTagName = it },
                        placeholder = { Text("输入新标签名称...", fontSize = 12.sp, color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DopaminePurple,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = {
                            if (newTagName.isNotBlank()) {
                                val hex = colorPalette[selectedColorIdx]
                                defaultTags.add(Pair(newTagName, hex))
                                newTagName = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DopaminePurple),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text("添加", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                // 多巴胺调色盘选择器 (RgbColorPicker)
                Text("选择专属标签色彩:", fontSize = 12.sp, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    colorPalette.forEachIndexed { idx, hex ->
                        val isSelected = selectedColorIdx == idx
                        val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Cyan }
                        Surface(
                            onClick = { selectedColorIdx = idx },
                            shape = CircleShape,
                            color = color,
                            border = BorderStroke(if (isSelected) 2.5.dp else 0.dp, if (isSelected) TextPrimary else Color.Transparent),
                            modifier = Modifier.size(28.dp)
                        ) {}
                    }
                }

                HorizontalDivider(color = DarkBorder, thickness = 1.dp)

                Text("已有标签库列表 (${defaultTags.size}项):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                defaultTags.forEach { (name, hex) ->
                    val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Cyan }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = color.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                    Text(name, fontSize = 11.5.sp, color = color, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(hex, fontSize = 11.sp, color = TextMuted)
                            Spacer(modifier = Modifier.weight(1f))
                            if (defaultTags.size > 1) {
                                IconButton(
                                    onClick = { defaultTags.remove(Pair(name, hex)) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "删除",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成", color = DopaminePurple, fontWeight = FontWeight.Bold) }
        }
    )
}

