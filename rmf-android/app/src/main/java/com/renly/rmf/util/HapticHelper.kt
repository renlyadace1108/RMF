package com.renly.rmf.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * ⚡ 多巴胺触感反馈助手 (Dopamine Haptic Feedback)
 * 赋予日程打卡、象限切换、翻页穿梭、系统设置开关与按键清脆细腻的物理级触感。
 */
object HapticHelper {
    private const val PREFS_NAME = "rmf_haptic_preferences"
    private const val KEY_HAPTIC_ENABLED = "haptic_feedback_enabled"

    @Volatile
    private var cachedHapticEnabled: Boolean? = null

    @Volatile
    private var cachedVibrator: Vibrator? = null
    @Volatile
    private var vibratorInitialized = false

    /**
     * 查询是否启用了全局操作触感反馈（震动）
     */
    fun isHapticEnabled(context: Context): Boolean {
        cachedHapticEnabled?.let { return it }
        val enabled = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_HAPTIC_ENABLED, true)
        cachedHapticEnabled = enabled
        return enabled
    }

    /**
     * 设置是否启用全局操作触感反馈（震动）
     */
    fun setHapticEnabled(context: Context, enabled: Boolean) {
        cachedHapticEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HAPTIC_ENABLED, enabled)
            .apply()
    }

    private fun getVibrator(context: Context): Vibrator? {
        if (vibratorInitialized) return cachedVibrator
        synchronized(this) {
            if (vibratorInitialized) return cachedVibrator
            cachedVibrator = try {
                val appCtx = context.applicationContext
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val manager = appCtx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    manager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    appCtx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
            } catch (_: Exception) {
                null
            }
            vibratorInitialized = true
            return cachedVibrator
        }
    }

    /**
     * 任务完成/打卡成功：多巴胺愉悦轻微双振
     */
    fun performSuccess(context: Context) {
        if (!isHapticEnabled(context)) return
        val vibrator = getVibrator(context) ?: return
        try {
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 优先使用标准系统预设双击效果
                val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                vibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 30, 60, 45)
                val amplitudes = intArrayOf(0, 180, 0, 255)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 30, 60, 45), -1)
            }
        } catch (_: Exception) { }
    }

    /**
     * 轻触点击：极微小清脆反馈（微小顿感，适用于按键、选项、开关）
     */
    fun performLightClick(context: Context) {
        if (!isHapticEnabled(context)) return
        val vibrator = getVibrator(context) ?: return
        try {
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                vibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(18, 120))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(18)
            }
        } catch (_: Exception) { }
    }

    /**
     * 页面滑屏翻页：极清脆短促的微触感（物理刻度感）
     */
    fun performPageTurn(context: Context) {
        if (!isHapticEnabled(context)) return
        val vibrator = getVibrator(context) ?: return
        try {
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                vibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(12, 100))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(12)
            }
        } catch (_: Exception) { }
    }

    /**
     * 象限移位 / 手势拉出收回设置：稍具质感的利落回弹
     */
    fun performQuadrantMoved(context: Context) {
        if (!isHapticEnabled(context)) return
        val vibrator = getVibrator(context) ?: return
        try {
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                vibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(30, 180))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(30)
            }
        } catch (_: Exception) { }
    }
}
