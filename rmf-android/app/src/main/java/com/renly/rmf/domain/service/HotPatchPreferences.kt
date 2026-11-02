package com.renly.rmf.domain.service

import android.content.Context

/**
 * 动态热更新 (免安装补丁) 本地配置与状态持久化
 */
object HotPatchPreferences {

    private const val PREFS_NAME = "rmf_hotpatch_prefs"

    private const val KEY_ENABLED = "hotpatch_enabled"
    private const val KEY_PATCH_VERSION = "active_patch_version"
    private const val KEY_PATCH_NAME = "active_patch_name"
    private const val KEY_PATCH_PATH = "active_patch_path"
    private const val KEY_TARGET_HOST_VERSION = "target_host_version"
    private const val KEY_STABILITY_STAGE = "stability_stage"
    private const val KEY_LAST_ROLLBACK_REASON = "last_rollback_reason"
    private const val KEY_LAST_PATCH_CHECK_TIME = "last_patch_check_time"

    const val STAGE_NONE = 0
    const val STAGE_TESTING = 1
    const val STAGE_STABLE = 2
    const val STAGE_ROLLED_BACK = -1

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getActivePatchVersion(context: Context): Int =
        getPrefs(context).getInt(KEY_PATCH_VERSION, 0)

    fun getActivePatchName(context: Context): String =
        getPrefs(context).getString(KEY_PATCH_NAME, "") ?: ""

    fun getActivePatchPath(context: Context): String? =
        getPrefs(context).getString(KEY_PATCH_PATH, null)

    fun getTargetHostVersion(context: Context): Int =
        getPrefs(context).getInt(KEY_TARGET_HOST_VERSION, 0)

    fun setActivePatch(
        context: Context,
        version: Int,
        name: String,
        path: String,
        targetHostVersion: Int
    ) {
        getPrefs(context).edit()
            .putInt(KEY_PATCH_VERSION, version)
            .putString(KEY_PATCH_NAME, name)
            .putString(KEY_PATCH_PATH, path)
            .putInt(KEY_TARGET_HOST_VERSION, targetHostVersion)
            .putInt(KEY_STABILITY_STAGE, STAGE_TESTING)
            .apply()
    }

    fun clearActivePatch(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_PATCH_VERSION)
            .remove(KEY_PATCH_NAME)
            .remove(KEY_PATCH_PATH)
            .remove(KEY_TARGET_HOST_VERSION)
            .putInt(KEY_STABILITY_STAGE, STAGE_NONE)
            .apply()
    }

    fun getStabilityStage(context: Context): Int =
        getPrefs(context).getInt(KEY_STABILITY_STAGE, STAGE_NONE)

    fun setStabilityStage(context: Context, stage: Int) {
        getPrefs(context).edit().putInt(KEY_STABILITY_STAGE, stage).apply()
    }

    fun getLastRollbackReason(context: Context): String? =
        getPrefs(context).getString(KEY_LAST_ROLLBACK_REASON, null)

    fun setLastRollbackReason(context: Context, reason: String?) {
        if (reason == null) {
            getPrefs(context).edit().remove(KEY_LAST_ROLLBACK_REASON).apply()
        } else {
            getPrefs(context).edit().putString(KEY_LAST_ROLLBACK_REASON, reason).apply()
        }
    }

    fun getLastCheckTime(context: Context): String =
        getPrefs(context).getString(KEY_LAST_PATCH_CHECK_TIME, "从未检查") ?: "从未检查"

    fun setLastCheckTime(context: Context, time: String) {
        getPrefs(context).edit().putString(KEY_LAST_PATCH_CHECK_TIME, time).apply()
    }
}
