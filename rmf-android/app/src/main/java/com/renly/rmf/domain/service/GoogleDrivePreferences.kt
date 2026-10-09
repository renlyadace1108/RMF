package com.renly.rmf.domain.service

import android.content.Context
import android.content.SharedPreferences

object GoogleDrivePreferences {
    private const val PREFS_NAME = "rmf_google_drive_prefs"
    private const val KEY_CLIENT_ID = "google_drive_client_id"
    private const val KEY_CLIENT_SECRET = "google_drive_client_secret"
    private const val KEY_ACCESS_TOKEN = "google_drive_access_token"
    private const val KEY_REFRESH_TOKEN = "google_drive_refresh_token"
    private const val KEY_EXPIRES_AT = "google_drive_expires_at"
    private const val KEY_LAST_SYNC_TIME = "google_drive_last_sync_time"
    private const val KEY_CALENDAR_LAST_SYNC_TIME = "google_calendar_last_sync_time"
    private const val KEY_CALENDAR_ICS_URL = "google_calendar_ics_url"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getClientId(context: Context): String {
        return getPrefs(context).getString(KEY_CLIENT_ID, "") ?: ""
    }

    fun setClientId(context: Context, clientId: String) {
        getPrefs(context).edit().putString(KEY_CLIENT_ID, clientId.trim()).apply()
    }

    fun getClientSecret(context: Context): String {
        return getPrefs(context).getString(KEY_CLIENT_SECRET, "") ?: ""
    }

    fun setClientSecret(context: Context, clientSecret: String) {
        getPrefs(context).edit().putString(KEY_CLIENT_SECRET, clientSecret.trim()).apply()
    }

    fun getAccessToken(context: Context): String {
        return getPrefs(context).getString(KEY_ACCESS_TOKEN, "") ?: ""
    }

    fun setAccessToken(context: Context, token: String, expiresInSeconds: Long = 3600) {
        val expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000)
        getPrefs(context).edit()
            .putString(KEY_ACCESS_TOKEN, token.trim())
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .apply()
    }

    fun getRefreshToken(context: Context): String {
        return getPrefs(context).getString(KEY_REFRESH_TOKEN, "") ?: ""
    }

    fun setRefreshToken(context: Context, refreshToken: String) {
        getPrefs(context).edit().putString(KEY_REFRESH_TOKEN, refreshToken.trim()).apply()
    }

    fun getExpiresAt(context: Context): Long {
        return getPrefs(context).getLong(KEY_EXPIRES_AT, 0L)
    }

    fun isTokenExpired(context: Context): Boolean {
        val expiresAt = getExpiresAt(context)
        // 提前 60 秒视作过期
        return System.currentTimeMillis() >= (expiresAt - 60_000)
    }

    fun getLastSyncTime(context: Context): String {
        return getPrefs(context).getString(KEY_LAST_SYNC_TIME, "从未同步") ?: "从未同步"
    }

    fun setLastSyncTime(context: Context, time: String) {
        getPrefs(context).edit().putString(KEY_LAST_SYNC_TIME, time).apply()
    }

    fun getCalendarLastSyncTime(context: Context): String {
        return getPrefs(context).getString(KEY_CALENDAR_LAST_SYNC_TIME, "从未同步") ?: "从未同步"
    }

    fun setCalendarLastSyncTime(context: Context, time: String) {
        getPrefs(context).edit().putString(KEY_CALENDAR_LAST_SYNC_TIME, time).apply()
    }

    fun getCalendarIcsUrl(context: Context): String {
        return getPrefs(context).getString(KEY_CALENDAR_ICS_URL, "") ?: ""
    }

    fun setCalendarIcsUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_CALENDAR_ICS_URL, url.trim()).apply()
    }

    fun isConfigured(context: Context): Boolean {
        return getClientId(context).isNotBlank()
    }

    fun isAuthorized(context: Context): Boolean {
        return getAccessToken(context).isNotBlank() || getRefreshToken(context).isNotBlank()
    }

    fun clearAuth(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .apply()
    }
}
