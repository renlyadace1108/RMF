package com.renly.rmf.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode(val title: String, val icon: String) {
    SYSTEM("跟随系统", "⚙️"),
    LIGHT("日间模式", "☀️"),
    DARK("夜间模式", "🌙")
}

object ThemePreferences {
    private const val PREFS_NAME = "rmf_theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_ACCENT_COLOR = "theme_accent_color"
    private const val KEY_DARK_BG_COLOR = "theme_dark_bg_color"
    private const val KEY_LIGHT_BG_COLOR = "theme_light_bg_color"
    const val DEFAULT_ACCENT_COLOR = "#38BDF8"
    const val DEFAULT_DARK_BG_COLOR = "#090B0E"
    const val DEFAULT_LIGHT_BG_COLOR = "#F8FAFC"

    fun getThemeMode(context: Context): ThemeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val modeStr = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return try {
            ThemeMode.valueOf(modeStr ?: ThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun getAccentColorHex(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACCENT_COLOR, DEFAULT_ACCENT_COLOR) ?: DEFAULT_ACCENT_COLOR
    }

    fun setAccentColorHex(context: Context, hex: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACCENT_COLOR, hex.trim()).apply()
    }

    fun getDarkBgColorHex(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DARK_BG_COLOR, DEFAULT_DARK_BG_COLOR) ?: DEFAULT_DARK_BG_COLOR
    }

    fun setDarkBgColorHex(context: Context, hex: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DARK_BG_COLOR, hex.trim()).apply()
    }

    fun getLightBgColorHex(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LIGHT_BG_COLOR, DEFAULT_LIGHT_BG_COLOR) ?: DEFAULT_LIGHT_BG_COLOR
    }

    fun setLightBgColorHex(context: Context, hex: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LIGHT_BG_COLOR, hex.trim()).apply()
    }
}

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    secondary = Color(0xFFA78BFA),
    tertiary = Color(0xFF34D399),
    background = Color(0xFF090B0E),
    surface = Color(0xFF11141A),
    onPrimary = Color(0xFF090B0E),
    onSecondary = Color(0xFF090B0E),
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    secondary = Color(0xFF7C3AED),
    tertiary = Color(0xFF059669),
    background = Color(0xFFF1F5F9),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A)
)

@Composable
fun RMFTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentColorHex: String = ThemePreferences.DEFAULT_ACCENT_COLOR,
    darkBgColorHex: String = ThemePreferences.DEFAULT_DARK_BG_COLOR,
    lightBgColorHex: String = ThemePreferences.DEFAULT_LIGHT_BG_COLOR,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val parsedAccent = try {
        Color(android.graphics.Color.parseColor(accentColorHex))
    } catch (_: Exception) {
        if (isDark) DarkPalette.dopamineBlue else LightPalette.dopamineBlue
    }

    val targetBgHex = if (isDark) darkBgColorHex else lightBgColorHex
    val parsedBg = try {
        Color(android.graphics.Color.parseColor(targetBgHex))
    } catch (_: Exception) {
        if (isDark) DarkPalette.bg else LightPalette.bg
    }

    val basePalette = if (isDark) DarkPalette else LightPalette
    val currentColors = if (isDark) {
        basePalette.copy(
            dopamineBlue = parsedAccent,
            bg = parsedBg,
            surface = if (parsedBg == Color(0xFF000000)) Color(0xFF111111) else basePalette.surface
        )
    } else {
        basePalette.copy(
            dopamineBlue = parsedAccent,
            bg = parsedBg
        )
    }

    val baseScheme = if (isDark) DarkColorScheme else LightColorScheme
    val colorScheme = baseScheme.copy(
        primary = parsedAccent,
        background = currentColors.bg,
        surface = currentColors.surface
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = currentColors.bg.toArgb()
            window.navigationBarColor = currentColors.bg.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    CompositionLocalProvider(
        LocalAppColors provides currentColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

fun parseHexColor(hex: String?, defaultColor: Color = Color(0xFF38BDF8)): Color {
    if (hex.isNullOrBlank()) return defaultColor
    return try {
        Color(android.graphics.Color.parseColor(hex.trim()))
    } catch (_: Exception) {
        defaultColor
    }
}
