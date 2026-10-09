package com.renly.rmf.domain.service

import android.content.Context
import android.content.SharedPreferences

enum class AiProvider(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val recommendedModels: List<String>,
    val isVisionSupported: Boolean,
    val description: String
) {
    GEMINI(
        id = "GEMINI",
        displayName = "Google Gemini (官方原生)",
        defaultBaseUrl = "https://generativelanguage.googleapis.com",
        defaultModel = "gemini-1.5-flash",
        recommendedModels = listOf("gemini-1.5-flash", "gemini-2.0-flash", "gemini-1.5-pro"),
        isVisionSupported = true,
        description = "多模态视觉理解极强，提供充裕免费额度，课表截图识别首选"
    ),
    QWEN(
        id = "QWEN",
        displayName = "通义千问 (Qwen / 阿里百炼)",
        defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        defaultModel = "qwen-vl-plus",
        recommendedModels = listOf("qwen-vl-plus", "qwen-vl-max", "qwen2.5-vl-72b-instruct", "qwen-plus", "qwen-turbo"),
        isVisionSupported = true,
        description = "阿里百炼兼容模式，qwen-vl 系列视觉模型对中文表格与课表识别效果极佳"
    ),
    DEEPSEEK(
        id = "DEEPSEEK",
        displayName = "DeepSeek (深度求索 / 文本)",
        defaultBaseUrl = "https://api.deepseek.com/v1",
        defaultModel = "deepseek-chat",
        recommendedModels = listOf("deepseek-chat", "deepseek-reasoner"),
        isVisionSupported = false,
        description = "高性价比推理与总结，适合每日复盘与精力规划 (注: 官方接口为纯文本模型)"
    ),
    CUSTOM(
        id = "CUSTOM",
        displayName = "自定义 (OpenAI 兼容接口)",
        defaultBaseUrl = "",
        defaultModel = "gpt-4o-mini",
        recommendedModels = listOf("gpt-4o-mini", "gpt-4o", "claude-3-5-sonnet"),
        isVisionSupported = true,
        description = "支持自建中转、OneAPI、反代或任意遵循 OpenAI 规范的 API 接口"
    );

    companion object {
        fun fromId(id: String): AiProvider {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: GEMINI
        }
    }
}

object AiPreferences {
    private const val PREFS_NAME = "rmf_ai_preferences"
    private const val KEY_PROVIDER = "ai_provider"
    private const val KEY_API_KEY = "ai_api_key"
    private const val KEY_BASE_URL = "ai_base_url"
    private const val KEY_MODEL = "ai_model"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getProvider(context: Context): AiProvider {
        val id = getPrefs(context).getString(KEY_PROVIDER, AiProvider.GEMINI.id) ?: AiProvider.GEMINI.id
        return AiProvider.fromId(id)
    }

    fun setProvider(context: Context, provider: AiProvider) {
        getPrefs(context).edit().putString(KEY_PROVIDER, provider.id).apply()
    }

    fun getApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_API_KEY, "") ?: ""
    }

    fun setApiKey(context: Context, apiKey: String) {
        getPrefs(context).edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    }

    fun getBaseUrl(context: Context): String {
        val saved = getPrefs(context).getString(KEY_BASE_URL, "") ?: ""
        if (saved.isNotBlank()) return saved
        return getProvider(context).defaultBaseUrl
    }

    fun setBaseUrl(context: Context, baseUrl: String) {
        getPrefs(context).edit().putString(KEY_BASE_URL, baseUrl.trim()).apply()
    }

    fun getModel(context: Context): String {
        val saved = getPrefs(context).getString(KEY_MODEL, "") ?: ""
        if (saved.isNotBlank()) return saved
        return getProvider(context).defaultModel
    }

    fun setModel(context: Context, model: String) {
        getPrefs(context).edit().putString(KEY_MODEL, model.trim()).apply()
    }

    fun isConfigured(context: Context): Boolean {
        return getApiKey(context).isNotBlank()
    }
}
