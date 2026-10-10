package com.renly.rmf.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.renly.rmf.domain.service.AiProvider
import com.renly.rmf.domain.service.AiResult
import com.renly.rmf.domain.service.AiService
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.launch

/**
 * 独立的 AI 接口与大模型配置弹窗 (BYOK 自带密钥模式)
 * 符合《生成式人工智能服务管理暂行办法》与国内各大应用商店审核规范
 */
@Composable
fun AiSettingsDialog(
    onDismiss: () -> Unit,
    onTriggerAiReview: ((String) -> Unit) -> Unit = {},
    onAiReviewResult: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentProvider by remember { mutableStateOf(AiPreferences.getProvider(context)) }
    var currentApiKey by remember { mutableStateOf(AiPreferences.getApiKey(context)) }
    var currentBaseUrl by remember { mutableStateOf(AiPreferences.getBaseUrl(context)) }
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
                .fillMaxHeight(0.9f)
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
                                Text("🤖", fontSize = 18.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "AI 接口与大模型配置",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "第三方大模型对接 (BYOK 自带密钥)",
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
                                text = "• 本软件为本地个人效能工具，不提供、不出售亦不中转任何 AI 生成模型服务。\n" +
                                        "• AI 功能（课表截图识别与日程复盘）为扩展选项，由客户端直连您所配置的合法第三方 API 接口。\n" +
                                        "• 请严格遵守《生成式人工智能服务管理暂行办法》及相关法律法规，不得利用模型接口生成或传播违法不良信息。",
                                fontSize = 10.5.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. 服务商选择
                    Text(
                        text = "选择 AI 服务商 (Provider):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AiProvider.entries.forEach { provider ->
                            val isSelected = currentProvider == provider
                            Surface(
                                onClick = {
                                    currentProvider = provider
                                    AiPreferences.setProvider(context, provider)
                                    currentBaseUrl = provider.defaultBaseUrl
                                    AiPreferences.setBaseUrl(context, provider.defaultBaseUrl)
                                    currentModel = provider.defaultModel
                                    AiPreferences.setModel(context, provider.defaultModel)
                                    testStatus = null
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) DopaminePurple.copy(alpha = 0.2f) else DarkSurface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) DopaminePurple else DarkBorder
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp)
                                ) {
                                    Text(
                                        text = when (provider) {
                                            AiProvider.QWEN -> "通义千问"
                                            AiProvider.DEEPSEEK -> "DeepSeek"
                                            AiProvider.CUSTOM -> "自定义"
                                            AiProvider.GEMINI -> "Gemini"
                                        },
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) DopaminePurple else TextSecondary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 服务商特性提示
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentProvider.description,
                            fontSize = 11.sp,
                            color = DopamineCyan,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = if (currentProvider.isVisionSupported) DopamineGreen.copy(alpha = 0.15f) else DopamineOrange.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (currentProvider.isVisionSupported) "📷 支持课表多模态" else "📝 纯文本模型",
                                fontSize = 9.5.sp,
                                color = if (currentProvider.isVisionSupported) DopamineGreen else DopamineOrange,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. API Key 输入框
                    OutlinedTextField(
                        value = currentApiKey,
                        onValueChange = {
                            currentApiKey = it
                            AiPreferences.setApiKey(context, it)
                        },
                        label = { Text("${currentProvider.displayName} API Key") },
                        placeholder = { Text("在此粘贴您的 API Key...") },
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

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4. Base URL 输入框
                    OutlinedTextField(
                        value = currentBaseUrl,
                        onValueChange = {
                            currentBaseUrl = it
                            AiPreferences.setBaseUrl(context, it)
                        },
                        label = { Text("接口 Base URL (支持中转/反向代理)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 5. 模型标识
                    OutlinedTextField(
                        value = currentModel,
                        onValueChange = {
                            currentModel = it
                            AiPreferences.setModel(context, it)
                        },
                        label = { Text("模型标识 (Model ID)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 推荐模型快捷胶囊
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "推荐: ",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                        currentProvider.recommendedModels.take(3).forEach { modelId ->
                            val isCurrent = currentModel == modelId
                            Surface(
                                onClick = {
                                    currentModel = modelId
                                    AiPreferences.setModel(context, modelId)
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isCurrent) DopamineBlue.copy(alpha = 0.2f) else DarkSurface,
                                border = BorderStroke(1.dp, if (isCurrent) DopamineBlue else DarkBorder)
                            ) {
                                Text(
                                    text = modelId,
                                    fontSize = 10.5.sp,
                                    color = if (isCurrent) DopamineBlue else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 6. 测试与体验操作区
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                isTesting = true
                                testStatus = "正在向 ${currentModel} 发送握手信号..."
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
                            enabled = !isTesting && currentApiKey.isNotBlank(),
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
                            enabled = !isGeneratingReview && currentApiKey.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = DopaminePurple),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isGeneratingReview) "思考中..." else "✨ 生成今日复盘",
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
