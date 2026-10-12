package com.renly.rmf.domain.service

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class FocusPreset(
    val id: String,
    val name: String,
    val minutes: Int,
    val icon: String = "⏱️"
)

object FocusPreferences {
    private const val PREF_NAME = "rmf_focus_prefs"
    private const val KEY_PRESETS = "focus_custom_presets"

    private val gson = Gson()

    val DEFAULT_PRESETS = listOf(
        FocusPreset("p_15", "番茄短冲", 15, "⚡"),
        FocusPreset("p_25", "标准番茄", 25, "🍅"),
        FocusPreset("p_45", "深度攻坚", 45, "🎯"),
        FocusPreset("p_60", "心流大块", 60, "🧠"),
        FocusPreset("p_90", "极限沉浸", 90, "🚀")
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getPresets(context: Context): List<FocusPreset> {
        val json = getPrefs(context).getString(KEY_PRESETS, null)
        if (json.isNullOrBlank()) {
            return DEFAULT_PRESETS
        }
        return try {
            val type = object : TypeToken<List<FocusPreset>>() {}.type
            val list: List<FocusPreset> = gson.fromJson(json, type)
            if (list.isEmpty()) DEFAULT_PRESETS else list
        } catch (_: Exception) {
            DEFAULT_PRESETS
        }
    }

    fun savePresets(context: Context, presets: List<FocusPreset>) {
        val json = gson.toJson(presets)
        getPrefs(context).edit().putString(KEY_PRESETS, json).apply()
    }

    fun addPreset(context: Context, name: String, minutes: Int, icon: String = "⏱️"): FocusPreset {
        val current = getPresets(context).toMutableList()
        val newPreset = FocusPreset(
            id = "preset_${System.currentTimeMillis()}",
            name = name,
            minutes = minutes.coerceIn(1, 360),
            icon = icon
        )
        current.add(newPreset)
        savePresets(context, current)
        return newPreset
    }

    fun deletePreset(context: Context, id: String) {
        val current = getPresets(context).toMutableList()
        current.removeAll { it.id == id }
        savePresets(context, current)
    }
}
