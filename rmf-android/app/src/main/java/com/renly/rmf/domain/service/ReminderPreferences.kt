package com.renly.rmf.domain.service

import android.content.Context
import android.content.SharedPreferences

/**
 * 提醒模式枚举与全局偏好管理
 * 支持：
 * - ALL: 闹钟与震动 (两者兼有，默认)
 * - ALARM: 仅闹钟 (仅响铃声音，无震动)
 * - VIBRATE: 仅震动 (仅震动，无响铃声音)
 * - NONE: 无提醒 (静音通知，不响铃不震动)
 */
enum class ReminderType(
    val code: String,
    val title: String,
    val emoji: String,
    val description: String
) {
    ALL("ALL", "闹钟+震动", "🔔", "铃声响铃并触发高强度震动提醒"),
    ALARM("ALARM", "仅闹钟", "⏰", "仅播放响铃声音，不触发手机震动"),
    VIBRATE("VIBRATE", "仅震动", "📳", "仅触发手机震动，保持环境安静"),
    NONE("NONE", "无提醒", "🔕", "静音通知呈现，不响铃也不震动");

    val isSoundEnabled: Boolean
        get() = this == ALL || this == ALARM

    val isVibrateEnabled: Boolean
        get() = this == ALL || this == VIBRATE

    companion object {
        fun fromCode(code: String?): ReminderType {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: ALL
        }
    }
}

object ReminderPreferences {
    private const val PREFS_NAME = "rmf_reminder_prefs"
    private const val KEY_GLOBAL_REMINDER_TYPE = "global_default_reminder_type"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 获取全局默认提醒方式 (默认: ALL - 闹钟+震动)
     */
    fun getGlobalReminderType(context: Context): ReminderType {
        val code = getPrefs(context).getString(KEY_GLOBAL_REMINDER_TYPE, ReminderType.ALL.code)
        return ReminderType.fromCode(code)
    }

    /**
     * 设置全局默认提醒方式
     */
    fun setGlobalReminderType(context: Context, type: ReminderType) {
        getPrefs(context).edit().putString(KEY_GLOBAL_REMINDER_TYPE, type.code).apply()
    }
}
