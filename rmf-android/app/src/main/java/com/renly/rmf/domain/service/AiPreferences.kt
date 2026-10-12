package com.renly.rmf.domain.service

import android.content.Context
import android.content.SharedPreferences

enum class AiProvider(
    val id: String,
    val displayName: String,
    val shortName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val description: String,
    val isVisionSupported: Boolean = false
) {
    BALANCED(
        id = "BALANCED",
        displayName = "引擎方案 A",
        shortName = "方案 A",
        defaultBaseUrl = "https://api.siliconflow.cn/v1",
        defaultModel = "Qwen/Qwen2.5-7B-Instruct",
        description = "标准计算模式，响应迅速，适合日常日程分析与排期规划",
        isVisionSupported = false
    ),
    REASONING(
        id = "REASONING",
        displayName = "引擎方案 B",
        shortName = "方案 B",
        defaultBaseUrl = "https://api.siliconflow.cn/v1",
        defaultModel = "deepseek-ai/DeepSeek-R1-Distill-Qwen-7B",
        description = "深度推演模式，具备逻辑链运算能力，适合复杂效能诊断",
        isVisionSupported = false
    ),
    SUMMARY(
        id = "SUMMARY",
        displayName = "引擎方案 C",
        shortName = "方案 C",
        defaultBaseUrl = "https://api.siliconflow.cn/v1",
        defaultModel = "THUDM/glm-4-9b-chat",
        description = "语言优化模式，表达自然丰富，适合复盘报告与心得提炼",
        isVisionSupported = false
    ),
    FLAGSHIP(
        id = "FLAGSHIP",
        displayName = "引擎方案 D",
        shortName = "方案 D",
        defaultBaseUrl = "https://api.siliconflow.cn/v1",
        defaultModel = "deepseek-ai/DeepSeek-V3",
        description = "高阶全能模式，超大规模参数综合运算，提供高精度评估",
        isVisionSupported = false
    ),
    CUSTOM(
        id = "CUSTOM",
        displayName = "自定义方案 (E)",
        shortName = "自定义",
        defaultBaseUrl = "",
        defaultModel = "",
        description = "支持自主配置标准兼容接口地址与模型标识",
        isVisionSupported = true
    );

    companion object {
        fun fromId(id: String): AiProvider {
            return when (id.uppercase()) {
                "BALANCED", "ENGINE_A", "QWEN" -> BALANCED
                "REASONING", "ENGINE_B", "DEEPSEEK" -> REASONING
                "SUMMARY", "ENGINE_C" -> SUMMARY
                "FLAGSHIP", "ENGINE_D" -> FLAGSHIP
                "CUSTOM", "GEMINI" -> CUSTOM
                else -> entries.find { it.id.equals(id, ignoreCase = true) } ?: BALANCED
            }
        }
    }
}

object AiPreferences {
    private const val PREFS_NAME = "rmf_ai_preferences"
    private const val KEY_AI_ENABLED = "ai_feature_enabled"
    private const val KEY_PROVIDER = "ai_provider"
    private const val KEY_API_KEY = "ai_api_key"
    private const val KEY_BASE_URL = "ai_base_url"
    private const val KEY_MODEL = "ai_model"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 数据分析功能全局总开关状态
     * 默认开启；关闭后停用所有网络分析与复盘计算
     */
    fun isAiEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AI_ENABLED, true)
    }

    fun setAiEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AI_ENABLED, enabled).apply()
    }

    fun getProvider(context: Context): AiProvider {
        val id = getPrefs(context).getString(KEY_PROVIDER, AiProvider.BALANCED.id) ?: AiProvider.BALANCED.id
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
        val provider = getProvider(context)
        if (provider != AiProvider.CUSTOM) {
            return provider.defaultBaseUrl
        }
        val saved = getPrefs(context).getString(KEY_BASE_URL, "") ?: ""
        if (saved.isNotBlank()) return saved
        return provider.defaultBaseUrl
    }

    fun setBaseUrl(context: Context, baseUrl: String) {
        getPrefs(context).edit().putString(KEY_BASE_URL, baseUrl.trim()).apply()
    }

    fun getModel(context: Context): String {
        val provider = getProvider(context)
        if (provider != AiProvider.CUSTOM) {
            return provider.defaultModel
        }
        val saved = getPrefs(context).getString(KEY_MODEL, "") ?: ""
        if (saved.isNotBlank()) return saved
        return AiProvider.BALANCED.defaultModel
    }

    fun setModel(context: Context, model: String) {
        getPrefs(context).edit().putString(KEY_MODEL, model.trim()).apply()
    }

    fun isConfigured(context: Context): Boolean {
        return getApiKey(context).isNotBlank()
    }
}
