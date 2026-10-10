package com.renly.rmf.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.renly.rmf.domain.service.PrivacyPreferences
import com.renly.rmf.ui.theme.*

/**
 * 首次冷启动隐私合规确认弹窗 (符合工信部双弹窗与各大应用市场审核规范)
 */
@Composable
fun PrivacyComplianceDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    var showSecondaryConfirm by remember { mutableStateOf(false) }
    var currentDetailDoc by remember { mutableStateOf<String?>(null) } // "PRIVACY" or "AGREEMENT" or null

    if (currentDetailDoc != null) {
        PrivacyDocumentViewerDialog(
            title = if (currentDetailDoc == "PRIVACY") "隐私政策" else "用户服务协议",
            content = if (currentDetailDoc == "PRIVACY") PrivacyPreferences.PRIVACY_POLICY_FULL else PrivacyPreferences.USER_AGREEMENT_FULL,
            onDismiss = { currentDetailDoc = null }
        )
    }

    if (showSecondaryConfirm) {
        AlertDialog(
            onDismissRequest = { /* 禁止点击外部关闭 */ },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            containerColor = DarkCard,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "隐私保护温馨提示",
                    color = DopamineAmber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = "若不同意《用户服务协议》与《隐私政策》，RMF 将无法为您提供个人日程管理、本地时间块与专注计时服务。\n\n本软件坚持数据本地离线存储，绝无任何违规收集个人信息的行为。请您再次考虑。",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSecondaryConfirm = false
                        onAccept()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("同意并继续", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = onDecline,
                    border = BorderStroke(1.dp, DopamineRed.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("退出应用", color = DopamineRed)
                }
            }
        )
    } else {
        Dialog(
            onDismissRequest = { /* 禁止点击外部关闭，必须做出合规选择 */ },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = DarkCard,
                border = BorderStroke(1.dp, DarkBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DopamineBlue.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🛡️", fontSize = 18.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "用户协议与隐私保护提示",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "欢迎使用 RMF (Renly Management Platform)",
                                fontSize = 11.5.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    HorizontalDivider(color = DarkBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))

                    // 滚动正文区域
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .heightIn(max = 340.dp)
                            .background(DarkSurface, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = PrivacyPreferences.PRIVACY_SUMMARY,
                            color = TextPrimary,
                            fontSize = 12.5.sp,
                            lineHeight = 19.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 协议链接快捷跳转
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "点击查阅完整：", fontSize = 11.sp, color = TextMuted)
                        TextButton(
                            onClick = { currentDetailDoc = "AGREEMENT" },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text("《用户协议》", fontSize = 11.5.sp, color = DopamineBlue, fontWeight = FontWeight.SemiBold)
                        }
                        Text(text = "与", fontSize = 11.sp, color = TextMuted)
                        TextButton(
                            onClick = { currentDetailDoc = "PRIVACY" },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text("《隐私政策》", fontSize = 11.5.sp, color = DopamineBlue, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 操作按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showSecondaryConfirm = true },
                            border = BorderStroke(1.dp, DarkBorder),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Text("暂不同意", color = TextSecondary, fontSize = 13.sp)
                        }

                        Button(
                            onClick = onAccept,
                            colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(44.dp)
                        ) {
                            Text("同意并继续", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 完整协议正文浏览弹窗
 */
@Composable
fun PrivacyDocumentViewerDialog(
    title: String,
    content: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = title,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .background(DarkSurface, RoundedCornerShape(8.dp))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = content,
                    color = TextPrimary,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("我已了解", fontWeight = FontWeight.Bold)
            }
        }
    )
}
