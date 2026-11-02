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
// 夜间模式 (Apple iOS 18 × ColorOS 17 水生极光暗夜美学)
// ----------------------------------------------------
val DarkPalette = AppColors(
    bg = Color(0xFF0A0D14),               // 深邃钛金黑曜背景 (Titanium Obsidian)
    surface = Color(0xFF121722),          // 悬浮层表面底色 (Ambient Glass)
    card = Color(0xFF171E2E),             // 通透磨砂质感卡片底色 (Aquamorphic Slate)
    cardHover = Color(0xFF222C42),        // 交互微光按压态
    border = Color(0x1EFFFFFF),           // 0.5dp 晶莹晶体反光微边框 (Jewel Rim Highlight)
    textPrimary = Color(0xFFF8FAFC),      // 纯净雪白主标题
    textSecondary = Color(0xFF94A3B8),    // 柔和微光副标题
    textMuted = Color(0xFF64748B),        // 弱化次级说明与时间
    dopamineBlue = Color(0xFF0A84FF),     // 苹果 iOS 18 标志性天青蓝
    dopamineGreen = Color(0xFF30D158),    // 苹果翡翠薄荷绿
    dopamineAmber = Color(0xFFFF9F0A),    // 温暖晨曦琥珀
    dopamineOrange = Color(0xFFFF6434),   // 活力流体暖橙
    dopaminePink = Color(0xFFFF375F),     // 霓虹樱花玫粉
    dopaminePurple = Color(0xFFBF5AF2),   // 极光梦境罗兰紫
    dopamineRed = Color(0xFFFF453A),      // 苹果警示珊瑚红
    dopamineCyan = Color(0xFF00C2CB),     // ColorOS 17 水生标志天青湖蓝
    isDark = true
)

// ----------------------------------------------------
// 日间模式 (Apple 瓷感雪白 × ColorOS 温润流体美学)
// ----------------------------------------------------
val LightPalette = AppColors(
    bg = Color(0xFFF2F5F9),               // 纯净雪瓷温润背景 (Porcelain Snow)
    surface = Color(0xFFFFFFFF),          // 纯白通透悬浮层
    card = Color(0xFFFFFFFF),             // 洁净卡片底色
    cardHover = Color(0xFFF1F5F9),        // 轻柔按压高亮
    border = Color(0x0F000000),           // 极细高质感发丝级边框 (Hairline)
    textPrimary = Color(0xFF0F172A),      // 深邃玄黑标题
    textSecondary = Color(0xFF334155),    // 柔和石墨副文本
    textMuted = Color(0xFF64748B),        // 辅助时间与微弱标签
    dopamineBlue = Color(0xFF007AFF),     // iOS 原生纯净蓝
    dopamineGreen = Color(0xFF34C759),    // 翡翠青翠绿
    dopamineAmber = Color(0xFFF59E0B),    // 阳光琥珀橙
    dopamineOrange = Color(0xFFFF6934),   // 晨曦活力橙
    dopaminePink = Color(0xFFFF2D55),     // 优雅山茶粉
    dopaminePurple = Color(0xFF7C3AED),   // 质感紫罗兰
    dopamineRed = Color(0xFFFF3B30),      // 晶莹珊瑚红
    dopamineCyan = Color(0xFF00B4D8),     // 水生天青色
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

val DopamineAccent: Color
    @Composable
    get() = LocalAppColors.current.dopamineBlue

val DopamineGreen: Color
    @Composable
    get() = LocalAppColors.current.dopamineGreen

val DopamineEmerald: Color
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

val DarkCardGradient: Brush
    @Composable
    get() {
        val colors = LocalAppColors.current
        return if (colors.isDark) {
            Brush.linearGradient(
                listOf(
                    Color(0xFF191E28),
                    Color(0xFF13171F)
                )
            )
        } else {
            Brush.linearGradient(
                listOf(
                    Color(0xFFFFFFFF),
                    Color(0xFFF8FAFC)
                )
            )
        }
    }

val DarkSurfaceGradient: Brush
    @Composable
    get() {
        val colors = LocalAppColors.current
        return if (colors.isDark) {
            Brush.verticalGradient(
                listOf(
                    Color(0xFF141822),
                    Color(0xFF0F1218)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color(0xFFFFFFFF),
                    Color(0xFFF1F5F9)
                )
            )
        }
    }

val SpecularEdgeHighlight: Brush = Brush.verticalGradient(
    listOf(
        Color(0x22FFFFFF),
        Color(0x05FFFFFF),
        Color(0x00FFFFFF)
    )
)

// ----------------------------------------------------
// Apple × ColorOS 水生光影与流体微光系统 (Aquamorphic Brushes)
// ----------------------------------------------------
val AquamorphicAurora = Brush.linearGradient(
    colors = listOf(Color(0xFF00C2CB), Color(0xFF0A84FF), Color(0xFFBF5AF2))
)

val IosGlassBorderLight = Brush.linearGradient(
    colors = listOf(
        Color(0x35FFFFFF),
        Color(0x10FFFFFF),
        Color(0x05FFFFFF),
        Color(0x1AFFFFFF)
    )
)

val IosSpringBlue = Brush.linearGradient(
    colors = listOf(Color(0xFF0A84FF), Color(0xFF00C2CB))
)

val IosSpringGreen = Brush.linearGradient(
    colors = listOf(Color(0xFF30D158), Color(0xFF059669))
)

val IosSpringAmber = Brush.linearGradient(
    colors = listOf(Color(0xFFFF9F0A), Color(0xFFFF6434))
)

val IosSpringPurple = Brush.linearGradient(
    colors = listOf(Color(0xFFBF5AF2), Color(0xFF5856D6))
)
