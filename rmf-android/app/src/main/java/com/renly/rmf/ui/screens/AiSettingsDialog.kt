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
import com.renly.rmf.domain.service.AiProvider
import com.renly.rmf.domain.service.AiResult
import com.renly.rmf.domain.service.AiService
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.launch

/**
 * 独立的数据分析接口与引擎配置弹窗 (BYOK 自带密钥模式)
 * 符合《生成式人工智能服务管理暂行办法》及各大应用商店合规审查要求
 * 严格保持中立，零品牌名称曝光，零引导行为
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
                                Text("⚡", fontSize = 18.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "数据分析引擎配置",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "扩展接口接入 (BYOK 自带密钥)",
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
                                text = "• 本软件为纯本地客户端效能工具，不提供、不出售亦不中转任何生成模型服务。\n" +
                                        "• 数据分析与复盘功能为可选扩展，由客户端直连您自主配置的标准化接口 (BYOK)。\n" +
                                        "• 请严格遵守相关法律法规，用户对输入内容与调用结果独立承担合规责任，开发者不保存您的密钥及交互数据。",
                                fontSize = 10.5.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. 引擎方案选择
                    Text(
                        text = "选择引擎方案:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                color = if (isSelected) DopaminePurple.copy(alpha = 0.16f) else DarkSurface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) DopaminePurple else DarkBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            currentProvider = provider
                                            AiPreferences.setProvider(context, provider)
                                            currentBaseUrl = provider.defaultBaseUrl
                                            AiPreferences.setBaseUrl(context, provider.defaultBaseUrl)
                                            currentModel = provider.defaultModel
                                            AiPreferences.setModel(context, provider.defaultModel)
                                            testStatus = null
                                        },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = DopaminePurple,
                                            unselectedColor = TextMuted
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = provider.displayName,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) DopaminePurple else TextPrimary
                                        )
                                        Text(
                                            text = provider.description,
                                            fontSize = 11.sp,
                                            color = TextSecondary,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. API Key 输入框 (所有方案通用)
                    OutlinedTextField(
                        value = currentApiKey,
                        onValueChange = {
                            currentApiKey = it
                            AiPreferences.setApiKey(context, it)
                        },
                        label = { Text("接口密钥 (API Key)") },
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

                    if (currentProvider != AiProvider.CUSTOM) {
                        Text(
                            text = "💡 当前方案已预设接口服务，填入密钥即可直接使用",
                            fontSize = 11.sp,
                            color = DopamineCyan,
                            modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                        )
                    } else {
                        // 4. 自定义接口专属输入框
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = currentBaseUrl,
                            onValueChange = {
                                currentBaseUrl = it
                                AiPreferences.setBaseUrl(context, it)
                            },
                            label = { Text("接口 Base URL (兼容接口地址)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))
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
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 5. 测试与体验操作区
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
