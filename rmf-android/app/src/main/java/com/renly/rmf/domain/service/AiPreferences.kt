package com.renly.rmf.domain.service

import android.content.Context
import android.content.SharedPreferences

object AiPreferences {
    private const val PREFS_NAME = "rmf_ai_preferences"
    private const val KEY_AI_ENABLED = "ai_feature_enabled"
    private const val KEY_BASE_URL = "ai_base_url"
    private const val KEY_API_KEY = "ai_api_key"
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

    fun getBaseUrl(context: Context): String {
        return getPrefs(context).getString(KEY_BASE_URL, "") ?: ""
    }

    fun setBaseUrl(context: Context, baseUrl: String) {
        getPrefs(context).edit().putString(KEY_BASE_URL, baseUrl.trim()).apply()
    }

    fun getApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_API_KEY, "") ?: ""
    }

    fun setApiKey(context: Context, apiKey: String) {
        getPrefs(context).edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    }

    fun getModel(context: Context): String {
        return getPrefs(context).getString(KEY_MODEL, "") ?: ""
    }

    fun setModel(context: Context, model: String) {
        getPrefs(context).edit().putString(KEY_MODEL, model.trim()).apply()
    }

    fun isConfigured(context: Context): Boolean {
        return getApiKey(context).isNotBlank() && getBaseUrl(context).isNotBlank() && getModel(context).isNotBlank()
    }
}
