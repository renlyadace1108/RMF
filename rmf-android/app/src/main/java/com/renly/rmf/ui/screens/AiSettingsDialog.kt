package com.renly.rmf.ui.screens

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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.renly.rmf.domain.service.AiPreferences
import com.renly.rmf.domain.service.AiResult
import com.renly.rmf.domain.service.AiService
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.launch

/**
 * 纯自定义数据分析接口配置弹窗 (BYOK 自带密钥模式)
 * 无任何预置服务商与模型推荐，完全由用户自主配置标准兼容接口
 * 严格保持技术中立，零品牌名称曝光，完全符合各大应用商店审核规范
 */
@Composable
fun AiSettingsDialog(
    onDismiss: () -> Unit,
    onTriggerAiReview: ((String) -> Unit) -> Unit = {},
    onAiReviewResult: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentBaseUrl by remember { mutableStateOf(AiPreferences.getBaseUrl(context)) }
    var currentApiKey by remember { mutableStateOf(AiPreferences.getApiKey(context)) }
    var currentModel by remember { mutableStateOf(AiPreferences.getModel(context)) }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var isGeneratingReview by remember { mutableStateOf(false) }

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
                .fillMaxHeight(0.88f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // 顶部标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DopaminePurple.copy(alpha = 0.18f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("⚡", fontSize = 18.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "自定义数据分析接口",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "兼容接口接入 (BYOK 自带密钥)",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = TextSecondary
                        )
                    }
                }

                HorizontalDivider(
                    color = DarkBorder,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // 可滚动内容区域
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 1. 合规与免责声明卡片
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DopamineAmber.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, DopamineAmber.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⚖️", fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "合规使用须知与免责声明",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DopamineAmber
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• 本软件为纯本地客户端效能工具，不内置、不出售亦不中转任何网络模型服务。\n" +
                                        "• 数据分析与复盘功能由客户端直连您自主配置的标准化接口 (BYOK)。\n" +
                                        "• 请严格遵守相关法律法规，合规使用第三方接口，开发者不存储您的密钥及交互数据。",
                                fontSize = 10.5.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. 接口地址输入框
                    Text(
                        text = "接口服务地址 (Base URL)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = currentBaseUrl,
                        onValueChange = {
                            currentBaseUrl = it
                            AiPreferences.setBaseUrl(context, it)
                        },
                        placeholder = { Text("例如: https://api.example.com/v1", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. 接口密钥输入框
                    Text(
                        text = "接口密钥 (API Key)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = currentApiKey,
                        onValueChange = {
                            currentApiKey = it
                            AiPreferences.setApiKey(context, it)
                        },
                        placeholder = { Text("在此粘贴您的 API Key...", fontSize = 12.sp) },
                        singleLine = true,
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Text(if (isApiKeyVisible) "🙈" else "👁️", fontSize = 14.sp)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4. 模型标识输入框
                    Text(
                        text = "模型名称 (Model ID)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = currentModel,
                        onValueChange = {
                            currentModel = it
                            AiPreferences.setModel(context, it)
                        },
                        placeholder = { Text("例如: model-name-instruct", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // 5. 操作区：测试连接与复盘体验
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                isTesting = true
                                testStatus = "正在发送握手信号..."
                                coroutineScope.launch {
                                    when (val testRes = AiService.testConnection(context)) {
                                        is AiResult.Success -> {
                                            testStatus = testRes.data
                                        }
                                        is AiResult.Error -> {
                                            testStatus = "❌ 连接失败: ${testRes.errorMessage}"
                                        }
                                    }
                                    isTesting = false
                                }
                            },
                            enabled = !isTesting && currentApiKey.isNotBlank() && currentBaseUrl.isNotBlank() && currentModel.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                            border = BorderStroke(1.dp, DopamineCyan.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isTesting) "握手中..." else "🔌 测试连接",
                                color = DopamineCyan,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = {
                                isGeneratingReview = true
                                onTriggerAiReview { result ->
                                    onAiReviewResult(result)
                                    isGeneratingReview = false
                                }
                            },
                            enabled = !isGeneratingReview && currentApiKey.isNotBlank() && currentBaseUrl.isNotBlank() && currentModel.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = DopaminePurple),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isGeneratingReview) "分析中..." else "✨ 生成今日复盘",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // 握手测试结果反馈横幅
                    if (testStatus != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (testStatus?.startsWith("✅") == true)
                                DopamineGreen.copy(alpha = 0.12f)
                            else
                                DopamineOrange.copy(alpha = 0.12f),
                            border = BorderStroke(
                                1.dp,
                                if (testStatus?.startsWith("✅") == true)
                                    DopamineGreen.copy(alpha = 0.4f)
                                else
                                    DopamineOrange.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = testStatus ?: "",
                                fontSize = 11.5.sp,
                                color = if (testStatus?.startsWith("✅") == true) DopamineGreen else DopamineOrange,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                HorizontalDivider(
                    color = DarkBorder,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                // 底部完成按钮
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text("保存并完成", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
