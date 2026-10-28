package com.renly.rmf.domain.service

import android.content.Context
import android.content.SharedPreferences

/**
 * 提醒模式枚举与全局偏好管理
 * 支持：
 * - ALL: 系统通知+响铃 (默认系统通知与横幅)
 * - ALARM: 强提醒闹钟 (系统闹钟响铃，持续唤醒与防漏)
 * - CAPSULE: 流体胶囊 (实时状态栏/通知流体胶囊倒计时)
 * - VIBRATE: 仅震动 (静音震动，安静环境适用)
 * - NONE: 静默通知 (不响铃不震动)
 */
enum class ReminderType(
    val code: String,
    val title: String,
    val emoji: String,
    val description: String
) {
    ALL("ALL", "通知+响铃", "🔔", "系统横幅通知并播放提示音与震动"),
    ALARM("ALARM", "强提醒闹钟", "⏰", "系统闹钟级别强提醒，高优先级持续响铃与防漏唤醒"),
    CAPSULE("CAPSULE", "流体胶囊", "💊", "状态栏/通知实时倒计时流体胶囊"),
    VIBRATE("VIBRATE", "仅震动", "📳", "仅触发手机震动，保持环境安静"),
    NONE("NONE", "静默通知", "🔕", "静音通知呈现，不响铃也不震动");

    val isSoundEnabled: Boolean
        get() = this == ALL || this == ALARM

    val isVibrateEnabled: Boolean
        get() = this == ALL || this == ALARM || this == VIBRATE

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
