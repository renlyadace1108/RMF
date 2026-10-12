package com.renly.rmf.domain.service

import android.content.Context

/**
 * 胶囊通知与实时活动配置规范
 * 架构红线：全局强制使用安卓原生实时活动 (Android Live Updates / Promoted Ongoing) 借道上岛，
 * 严禁使用媒体播放/MediaSession 借道方式！
 */
object FluidCloudPreferences {
    // 强制固定为原生实时活动规范 (Promoted Ongoing)
    const val MODE_NATIVE_LIVE_UPDATE = 2

    fun getBridgeMode(context: Context): Int = MODE_NATIVE_LIVE_UPDATE

    fun isSilentAudioEnabled(context: Context): Boolean = false
}

