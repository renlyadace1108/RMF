package com.renly.rmf.domain.service

import android.content.Context
import android.content.SharedPreferences

object SchedulePreferences {
    private const val PREFS_NAME = "rmf_schedule_prefs"
    private const val KEY_DUAL_TRACK_MODE = "is_dual_track_mode"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 计划日程 vs 实际作息双轨对照模式
     * 默认开启 (true)
     */
    fun isDualTrackMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DUAL_TRACK_MODE, true)
    }

    fun setDualTrackMode(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DUAL_TRACK_MODE, enabled).apply()
    }
}
