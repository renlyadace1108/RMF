package com.renly.rmf.domain.service

import android.content.Context
import android.content.SharedPreferences

object FluidCloudPreferences {
    private const val PREFS_NAME = "rmf_fluid_cloud_prefs"

    // 借道模式常量
    const val MODE_HYBRID = 0             // 双借道融合模式 (MediaSession + 原生实时活动芯片，推荐)
    const val MODE_MEDIA_SESSION = 1      // 媒体会话借道 (适配 ColorOS 14/15 挖孔胶囊与锁屏控制台)
    const val MODE_NATIVE_LIVE_UPDATE = 2 // 安卓原生实时活动芯片 (Android 15/16 纯净 Chronometer 芯片)
    const val MODE_CUSTOM_DRAWER = 3      // 经典通知抽屉卡片 (带有自定义 RemoteViews 霓虹卡片)

    private const val KEY_BRIDGE_MODE = "fluid_cloud_bridge_mode"
    private const val KEY_SILENT_AUDIO_KEEP_ALIVE = "fluid_cloud_silent_audio_keep_alive"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getBridgeMode(context: Context): Int {
        return getPrefs(context).getInt(KEY_BRIDGE_MODE, MODE_HYBRID)
    }

    fun setBridgeMode(context: Context, mode: Int) {
        getPrefs(context).edit().putInt(KEY_BRIDGE_MODE, mode).apply()
    }

    fun isSilentAudioEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SILENT_AUDIO_KEEP_ALIVE, true)
    }

    fun setSilentAudioEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SILENT_AUDIO_KEEP_ALIVE, enabled).apply()
    }
}
