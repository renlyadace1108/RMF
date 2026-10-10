package com.renly.rmf.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.renly.rmf.RmfApplication
import com.renly.rmf.domain.service.CalendarSyncResult
import com.renly.rmf.domain.service.GoogleCalendarSyncService
import com.renly.rmf.domain.service.GoogleDrivePreferences
import com.renly.rmf.domain.service.GoogleDriveSyncService
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 独立的 Google 服务与多端互通设置弹窗
 * 整合 Google Drive 跨端云备份/恢复与 Google Calendar 日历双向互通/订阅
 */
@Composable
fun GoogleSettingsDialog(
    onDismiss: () -> Unit,
    onTriggerUpload: () -> Unit,
    onTriggerDownload: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var googleClientId by remember { mutableStateOf(GoogleDrivePreferences.getClientId(context)) }
    var googleClientSecret by remember { mutableStateOf(GoogleDrivePreferences.getClientSecret(context)) }
    var driveStatusText by remember { mutableStateOf("") }
    var lastSyncDisplay by remember { mutableStateOf(GoogleDrivePreferences.getLastSyncTime(context)) }
    var isDriveWorking by remember { mutableStateOf(false) }
    var showAuthCodeDialog by remember { mutableStateOf(false) }
    var authCodeInput by remember { mutableStateOf("") }

    var calSyncStatusText by remember { mutableStateOf("") }
    var isCalWorking by remember { mutableStateOf(false) }
    var icsUrlInput by remember { mutableStateOf(GoogleDrivePreferences.getCalendarIcsUrl(context)) }
    var lastCalSyncTime by remember { mutableStateOf(GoogleDrivePreferences.getCalendarLastSyncTime(context)) }

    val isAuthorized = GoogleDrivePreferences.isAuthorized(context)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkCard,
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // 顶部标题栏与关闭按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DopamineBlue.copy(alpha = 0.18f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🌐", fontSize = 18.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Google 服务与多端互通",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isAuthorized) Color(0xFF064E3B) else DarkSurface,
                                    border = BorderStroke(1.dp, if (isAuthorized) Color(0xFF059669) else DarkBorder)
                                ) {
                                    Text(
                                        text = if (isAuthorized) "已授权" else "未连接",
                                        color = if (isAuthorized) Color(0xFF6EE7B7) else TextMuted,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Google Drive 跨端同步 + Google Calendar 日历互通",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 1.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = DarkBorder, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(14.dp))

                // 可滚动内容主体
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ==========================================
                    // 1. Google OAuth 2.0 凭据与全局授权管理
                    // ==========================================
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🔑 OAuth 2.0 客户端凭据",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DopamineBlue
                                )
                                if (isAuthorized) {
                                    OutlinedButton(
                                        onClick = {
                                            GoogleDrivePreferences.clearAuth(context)
                                            driveStatusText = "已解除授权并清除本地凭据 Token"
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DopamineRed),
                                        border = BorderStroke(1.dp, DopamineRed),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("解除授权", fontSize = 11.sp)
                                    }
                                }
                            }

                            Text(
                                text = "用于直连 Google Drive 与 Google Calendar API，安卓端与 Windows 电脑端使用相同凭据即可访问同一云端档案：",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        GoogleDrivePreferences.setClientId(context, googleClientId.trim())
                                        GoogleDrivePreferences.setClientSecret(context, googleClientSecret.trim())
                                        driveStatusText = "✓ OAuth 凭据已成功保存！"
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("保存配置", fontSize = 12.sp, color = Color.White)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val cId = googleClientId.trim()
                                        if (cId.isBlank()) {
                                            driveStatusText = "✗ 请先填写 Client ID"
                                            return@OutlinedButton
                                        }
                                        GoogleDrivePreferences.setClientId(context, cId)
                                        GoogleDrivePreferences.setClientSecret(context, googleClientSecret.trim())
                                        showAuthCodeDialog = true
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DopamineCyan),
                                    border = BorderStroke(1.dp, DopamineCyan),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (isAuthorized) "重新授权" else "立即登录授权", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // ==========================================
                    // 2. Google Drive 云端安全备份与同步
                    // ==========================================
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "☁️ Google Drive 云端数据库备份",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "上次: $lastSyncDisplay",
                                    fontSize = 10.5.sp,
                                    color = TextMuted
                                )
                            }

                            Text(
                                text = "通过 Google Drive API，将包含全部日程、课程、习惯、学习与健身数据的 SQLite 数据库无损上传/拉取，与电脑版 100% 互通 (rmf_cloud_backup.db)。",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        val cId = googleClientId.trim()
                                        if (cId.isBlank()) {
                                            driveStatusText = "✗ 请先填写 Client ID"
                                            return@Button
                                        }
                                        GoogleDrivePreferences.setClientId(context, cId)
                                        GoogleDrivePreferences.setClientSecret(context, googleClientSecret.trim())

                                        if (!GoogleDrivePreferences.isAuthorized(context)) {
                                            showAuthCodeDialog = true
                                        } else {
                                            isDriveWorking = true
                                            driveStatusText = "正在调用 Google Drive API 上传备份..."
                                            onTriggerUpload()
                                        }
                                    },
                                    enabled = !isDriveWorking,
                                    colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("⬆ 上传完整备份", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val cId = googleClientId.trim()
                                        if (cId.isBlank()) {
                                            driveStatusText = "✗ 请先填写 Client ID"
                                            return@OutlinedButton
                                        }
                                        GoogleDrivePreferences.setClientId(context, cId)
                                        GoogleDrivePreferences.setClientSecret(context, googleClientSecret.trim())

                                        if (!GoogleDrivePreferences.isAuthorized(context)) {
                                            showAuthCodeDialog = true
                                        } else {
                                            isDriveWorking = true
                                            driveStatusText = "正在从 Google Drive API 拉取备份..."
                                            onTriggerDownload()
                                        }
                                    },
                                    enabled = !isDriveWorking,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DopamineGreen),
                                    border = BorderStroke(1.dp, DopamineGreen),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("⬇ 从云端拉取恢复", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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

                    // ==========================================
                    // 3. Google Calendar 日历互通与同步
                    // ==========================================
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📅 Google Calendar 日历互通同步",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "上次: $lastCalSyncTime",
                                    fontSize = 10.5.sp,
                                    color = TextMuted
                                )
                            }

                            Text(
                                text = "支持 REST API 直连推送日程与上课计划至 Google 日历；同时支持标准 .ics 文件导出或 URL 私密订阅：",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                            )

                            // REST API 直连推送与拉取
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (!GoogleDrivePreferences.isAuthorized(context)) {
                                            showAuthCodeDialog = true
                                            return@Button
                                        }
                                        isCalWorking = true
                                        calSyncStatusText = "正在推送日程至 Google Calendar..."
                                        coroutineScope.launch {
                                            val db = (context.applicationContext as RmfApplication).database
                                            val service = GoogleCalendarSyncService(context, db)
                                            val res = service.pushSchedulesToGoogleCalendar()
                                            isCalWorking = false
                                            when (res) {
                                                is CalendarSyncResult.Success -> {
                                                    calSyncStatusText = "✓ ${res.message}"
                                                    lastCalSyncTime = GoogleDrivePreferences.getCalendarLastSyncTime(context)
                                                }
                                                is CalendarSyncResult.Error -> {
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
                                    Text("🚀 推送至 Google", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (!GoogleDrivePreferences.isAuthorized(context)) {
                                            showAuthCodeDialog = true
                                            return@OutlinedButton
                                        }
                                        isCalWorking = true
                                        calSyncStatusText = "正在从 Google Calendar 拉取事件..."
                                        coroutineScope.launch {
                                            val db = (context.applicationContext as RmfApplication).database
                                            val service = GoogleCalendarSyncService(context, db)
                                            val res = service.pullEventsFromGoogleCalendar(30)
                                            isCalWorking = false
                                            when (res) {
                                                is CalendarSyncResult.Success -> {
                                                    calSyncStatusText = "✓ ${res.message}"
                                                    lastCalSyncTime = GoogleDrivePreferences.getCalendarLastSyncTime(context)
                                                }
                                                is CalendarSyncResult.Error -> {
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
                                    Text("⬇ 从 Google 拉取", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // 导出 .ics 通用文件
                            OutlinedButton(
                                onClick = {
                                    isCalWorking = true
                                    calSyncStatusText = "正在生成 .ics 日历文件..."
                                    coroutineScope.launch {
                                        val db = (context.applicationContext as RmfApplication).database
                                        val service = GoogleCalendarSyncService(context, db)
                                        val res = service.exportSchedulesToIcsFile(includeCourses = true)
                                        isCalWorking = false
                                        if (res.isSuccess) {
                                            val file = res.getOrThrow()
                                            calSyncStatusText = "✓ 已生成 .ics，调起系统分享..."
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
                                Text("📁 导出 .ics 日历文件 (课表+日程)", fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 私密 iCal 订阅 URL
                            Text(
                                text = "输入 Google Calendar 私密 iCal 订阅地址 (.ics)：",
                                fontSize = 11.sp,
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
                                    GoogleDrivePreferences.setCalendarIcsUrl(context, url)
                                    isCalWorking = true
                                    calSyncStatusText = "正在从日历订阅地址同步..."
                                    coroutineScope.launch {
                                        val db = (context.applicationContext as RmfApplication).database
                                        val service = GoogleCalendarSyncService(context, db)
                                        val res = service.syncFromIcsUrl(url)
                                        isCalWorking = false
                                        when (res) {
                                            is CalendarSyncResult.Success -> {
                                                calSyncStatusText = "✓ ${res.message}"
                                                lastCalSyncTime = GoogleDrivePreferences.getCalendarLastSyncTime(context)
                                            }
                                            is CalendarSyncResult.Error -> {
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
                }
            }
        }
    }

    // OAuth 2.0 快捷授权引导弹窗
    if (showAuthCodeDialog) {
        AlertDialog(
            onDismissRequest = { showAuthCodeDialog = false },
            containerColor = DarkSurface,
            title = { Text("🔑 授权 Google 服务 API", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "为了让安卓端与电脑端访问同一个云盘文件并互通日历，需要完成一次 OAuth 2.0 授权：",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Button(
                        onClick = {
                            val cId = GoogleDrivePreferences.getClientId(context)
                            val authUrl = GoogleDriveSyncService.buildAuthorizationUrl(cId)
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                            context.startActivity(intent)
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
                                GoogleDrivePreferences.setAccessToken(context, code)
                                driveStatusText = "✓ Access Token 已保存生效！"
                                showAuthCodeDialog = false
                            } else {
                                driveStatusText = "正在兑换 Token..."
                                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                    val res = GoogleDriveSyncService.exchangeAuthCode(
                                        clientId = GoogleDrivePreferences.getClientId(context),
                                        clientSecret = GoogleDrivePreferences.getClientSecret(context),
                                        authCode = code
                                    )
                                    withContext(Dispatchers.Main) {
                                        if (res.isSuccess) {
                                            val (acc, ref) = res.getOrThrow()
                                            GoogleDrivePreferences.setAccessToken(context, acc)
                                            if (ref != null) GoogleDrivePreferences.setRefreshToken(context, ref)
                                            driveStatusText = "✓ Google 授权成功！"
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
}
