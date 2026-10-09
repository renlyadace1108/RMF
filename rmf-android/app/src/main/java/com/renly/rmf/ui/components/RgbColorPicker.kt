package com.renly.rmf.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.ui.theme.*

fun hexToRgb(hex: String, defaultColor: Color = Color(0xFF38BDF8)): Triple<Int, Int, Int> {
    return try {
        val clean = hex.trim().removePrefix("#")
        val colorInt = when (clean.length) {
            6 -> clean.toLong(16).toInt()
            8 -> clean.toLong(16).toInt()
            else -> return Triple(
                (defaultColor.red * 255).toInt(),
                (defaultColor.green * 255).toInt(),
                (defaultColor.blue * 255).toInt()
            )
        }
        val r = (colorInt shr 16) and 0xFF
        val g = (colorInt shr 8) and 0xFF
        val b = colorInt and 0xFF
        Triple(r, g, b)
    } catch (_: Exception) {
        Triple(
            (defaultColor.red * 255).toInt(),
            (defaultColor.green * 255).toInt(),
            (defaultColor.blue * 255).toInt()
        )
    }
}

fun rgbToHex(r: Int, g: Int, b: Int): String {
    val cr = r.coerceIn(0, 255)
    val cg = g.coerceIn(0, 255)
    val cb = b.coerceIn(0, 255)
    return String.format("#%02X%02X%02X", cr, cg, cb)
}

/**
 * 现代 RGB 实时预览选色器组件
 * 支持红/绿/蓝三原色实时滑块、HEX/RGB代码双向同步、色彩高对比度实时预览
 */
@Composable
fun RgbColorPicker(
    colorHex: String,
    onColorChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val (initR, initG, initB) = remember(colorHex) { hexToRgb(colorHex) }
    var red by remember(colorHex) { mutableFloatStateOf(initR.toFloat()) }
    var green by remember(colorHex) { mutableFloatStateOf(initG.toFloat()) }
    var blue by remember(colorHex) { mutableFloatStateOf(initB.toFloat()) }

    val liveColor = Color(red.toInt(), green.toInt(), blue.toInt())
    val liveHex = rgbToHex(red.toInt(), green.toInt(), blue.toInt())

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, DarkBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 实时预览 Banner
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 颜色预览圆角大方块
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = liveColor,
                    border = BorderStroke(1.5.dp, DarkBorder),
                    modifier = Modifier.size(46.dp)
                ) {}

                Spacer(modifier = Modifier.width(10.dp))

                // 色号与 RGB 参数展示
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = liveHex,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = liveColor.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, liveColor.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = "实时高光效果",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = liveColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "RGB(${red.toInt()}, ${green.toInt()}, ${blue.toInt()})",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            HorizontalDivider(color = DarkBorder, thickness = 0.8.dp)

            // R 滑块 (红)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                    border = BorderStroke(0.8.dp, Color(0xFFEF4444)),
                    modifier = Modifier.width(42.dp)
                ) {
                    Text(
                        text = "R ${red.toInt()}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 3.dp, horizontal = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = red,
                    onValueChange = {
                        red = it
                        val hex = rgbToHex(red.toInt(), green.toInt(), blue.toInt())
                        onColorChange(hex)
                    },
                    valueRange = 0f..255f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFEF4444),
                        activeTrackColor = Color(0xFFEF4444),
                        inactiveTrackColor = DarkBorder
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            // G 滑块 (绿)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    border = BorderStroke(0.8.dp, Color(0xFF10B981)),
                    modifier = Modifier.width(42.dp)
                ) {
                    Text(
                        text = "G ${green.toInt()}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 3.dp, horizontal = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = green,
                    onValueChange = {
                        green = it
                        val hex = rgbToHex(red.toInt(), green.toInt(), blue.toInt())
                        onColorChange(hex)
                    },
                    valueRange = 0f..255f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF10B981),
                        activeTrackColor = Color(0xFF10B981),
                        inactiveTrackColor = DarkBorder
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            // B 滑块 (蓝)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                    border = BorderStroke(0.8.dp, Color(0xFF38BDF8)),
                    modifier = Modifier.width(42.dp)
                ) {
                    Text(
                        text = "B ${blue.toInt()}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 3.dp, horizontal = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = blue,
                    onValueChange = {
                        blue = it
                        val hex = rgbToHex(red.toInt(), green.toInt(), blue.toInt())
                        onColorChange(hex)
                    },
                    valueRange = 0f..255f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF38BDF8),
                        activeTrackColor = Color(0xFF38BDF8),
                        inactiveTrackColor = DarkBorder
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 可折叠 RGB 实时选色卡片扩展栏
 */
@Composable
fun ExpandableRgbColorPicker(
    colorHex: String,
    onColorChange: (String) -> Unit,
    title: String = "RGB 实时滑块微调",
    defaultExpanded: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(defaultExpanded) }
    val (r, g, b) = remember(colorHex) { hexToRgb(colorHex) }
    val liveColor = Color(r, g, b)

    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            onClick = { isExpanded = !isExpanded },
            shape = RoundedCornerShape(8.dp),
            color = if (isExpanded) DarkSurface else DarkCard,
            border = BorderStroke(1.dp, if (isExpanded) liveColor.copy(alpha = 0.6f) else DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(liveColor)
                        .border(1.dp, DarkBorder, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "RGB($r, $g, $b)",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(top = 6.dp)) {
                RgbColorPicker(
                    colorHex = colorHex,
                    onColorChange = onColorChange
                )
            }
        }
    }
}
