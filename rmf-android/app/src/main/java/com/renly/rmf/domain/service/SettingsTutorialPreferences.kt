package com.renly.rmf.domain.service

import android.content.Context
import android.content.SharedPreferences

/**
 * 首次操作教学与手势指引状态管理
 * 记录用户是否已体验或查看过「底部栏左右滑动进入/退出设置」教学
 */
object SettingsTutorialPreferences {
    private const val PREFS_NAME = "rmf_tutorial_preferences"
    private const val KEY_SETTINGS_GESTURE_TUTORIAL_COMPLETED = "settings_gesture_tutorial_completed_v1"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 用户是否已完成「进入设置手势教学」
     */
    fun isSettingsGestureTutorialCompleted(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SETTINGS_GESTURE_TUTORIAL_COMPLETED, false)
    }

    /**
     * 设置手势教学完成状态
     */
    fun setSettingsGestureTutorialCompleted(context: Context, completed: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SETTINGS_GESTURE_TUTORIAL_COMPLETED, completed).apply()
    }

    /**
     * 重置教学状态（便于用户在设置或教程中重新观看）
     */
    fun resetTutorial(context: Context) {
        setSettingsGestureTutorialCompleted(context, false)
    }
}
