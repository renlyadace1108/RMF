package com.renly.rmf.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ----------------------------------------------------
// 现代全套应用主题调色板数据模型
// ----------------------------------------------------
data class AppColors(
    val bg: Color,
    val surface: Color,
    val card: Color,
    val cardHover: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val dopamineBlue: Color,
    val dopamineGreen: Color,
    val dopamineAmber: Color,
    val dopamineOrange: Color,
    val dopaminePink: Color,
    val dopaminePurple: Color,
    val dopamineRed: Color,
    val dopamineCyan: Color,
    val isDark: Boolean
)

// ----------------------------------------------------
// 夜间模式 (Dark Mode Palette)
// ----------------------------------------------------
val DarkPalette = AppColors(
    bg = Color(0xFF090B0E),               // 深邃极光黑底
    surface = Color(0xFF11141A),          // 微抬升表面底色
    card = Color(0xFF171B22),             // 悬浮磨砂质感卡片底色
    cardHover = Color(0xFF1F242D),        // 交互高亮状态
    border = Color(0xFF262C36),           // 柔和微光勾边
    textPrimary = Color(0xFFF1F5F9),      // 纯净主标题与核心文字
    textSecondary = Color(0xFF94A3B8),    // 柔和副标题与提示文字
    textMuted = Color(0xFF64748B),        // 弱化辅助信息与时间标签
    dopamineBlue = Color(0xFF38BDF8),
    dopamineGreen = Color(0xFF34D399),
    dopamineAmber = Color(0xFFFBBF24),
    dopamineOrange = Color(0xFFFB923C),
    dopaminePink = Color(0xFFF472B6),
    dopaminePurple = Color(0xFFA78BFA),
    dopamineRed = Color(0xFFF87171),
    dopamineCyan = Color(0xFF22D3EE),
    isDark = true
)

// ----------------------------------------------------
// 日间模式 (Light Mode Palette - 象牙暖玉白，低疲劳温润高质感)
// ----------------------------------------------------
val LightPalette = AppColors(
    bg = Color(0xFFF8FAFC),               // 清透温润象牙白背景 (Slate-50)
    surface = Color(0xFFFFFFFF),          // 纯白导航栏与顶栏
    card = Color(0xFFFFFFFF),             // 纯净卡片底色
    cardHover = Color(0xFFF1F5F9),        // 轻微按压高亮 (Slate-100)
    border = Color(0xFFE2E8F0),           // 极细高质感边框 (Slate-200)
    textPrimary = Color(0xFF0F172A),      // 深邃墨黑主标题 (Slate-900)
    textSecondary = Color(0xFF334155),    // 柔和正文副文本 (Slate-700)
    textMuted = Color(0xFF64748B),        // 辅助时间与微弱标签 (Slate-500)
    dopamineBlue = Color(0xFF0284C7),     // 澄澈天青蓝
    dopamineGreen = Color(0xFF059669),    // 翡翠薄荷绿
    dopamineAmber = Color(0xFFD97706),    // 阳光琥珀橙
    dopamineOrange = Color(0xFFEA580C),   // 晨曦活力橙
    dopaminePink = Color(0xFFDB2777),     // 霓虹樱花粉
    dopaminePurple = Color(0xFF7C3AED),   // 质感紫罗兰
    dopamineRed = Color(0xFFDC2626),      // 警示珊瑚红
    dopamineCyan = Color(0xFF0891B2),     // 冰川天青色
    isDark = false
)

val LocalAppColors = staticCompositionLocalOf { DarkPalette }

// ----------------------------------------------------
// 动态响应主题调色令牌 (兼容既有代码调用，同时自动随主题切换)
// ----------------------------------------------------
val DarkBg: Color
    @Composable
    get() = LocalAppColors.current.bg

val DarkSurface: Color
    @Composable
    get() = LocalAppColors.current.surface

val DarkCard: Color
    @Composable
    get() = LocalAppColors.current.card

val DarkCardHover: Color
    @Composable
    get() = LocalAppColors.current.cardHover

val DarkBorder: Color
    @Composable
    get() = LocalAppColors.current.border

val TextPrimary: Color
    @Composable
    get() = LocalAppColors.current.textPrimary

val TextSecondary: Color
    @Composable
    get() = LocalAppColors.current.textSecondary

val TextMuted: Color
    @Composable
    get() = LocalAppColors.current.textMuted

val DopamineBlue: Color
    @Composable
    get() = LocalAppColors.current.dopamineBlue

val DopamineGreen: Color
    @Composable
    get() = LocalAppColors.current.dopamineGreen

val DopamineAmber: Color
    @Composable
    get() = LocalAppColors.current.dopamineAmber

val DopamineOrange: Color
    @Composable
    get() = LocalAppColors.current.dopamineOrange

val DopaminePink: Color
    @Composable
    get() = LocalAppColors.current.dopaminePink

val DopaminePurple: Color
    @Composable
    get() = LocalAppColors.current.dopaminePurple

val DopamineRed: Color
    @Composable
    get() = LocalAppColors.current.dopamineRed

val DopamineCyan: Color
    @Composable
    get() = LocalAppColors.current.dopamineCyan

// ----------------------------------------------------
// 灵动光影渐变系统 (Gradients)
// ----------------------------------------------------
val PrimaryGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFF38BDF8), Color(0xFF818CF8))
)

val NorthStarGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFF59E0B), Color(0xFFD97706))
)

val CardGlowBorder = Brush.verticalGradient(
    colors = listOf(Color(0x3338BDF8), Color(0x0538BDF8))
)

val FocusPulseGradient = Brush.radialGradient(
    colors = listOf(Color(0x4038BDF8), Color(0x00090B0E))
)
