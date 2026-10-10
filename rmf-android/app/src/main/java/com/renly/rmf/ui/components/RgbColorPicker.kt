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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
 * 支持红/绿/蓝三原色实时滑块、HEX/RGB代码双向同步、色彩高对比度实时预览、
 * 以及全量 526 种「中国传统色 (zhongguose.com)」分类预置调色板与全景浏览搜索。
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

    // 匹配中国传统色
    val matchedChineseColor = remember(liveHex) {
        ChineseColors.allColors.find { it.hex.equals(liveHex, ignoreCase = true) }
    }

    var showChineseColorBrowser by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("全部") }

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
                        if (matchedChineseColor != null) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = liveColor.copy(alpha = 0.25f),
                                border = BorderStroke(1.dp, liveColor.copy(alpha = 0.7f))
                            ) {
                                Text(
                                    text = "🇨🇳 ${matchedChineseColor.name} (${matchedChineseColor.pinyin})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = liveColor.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, liveColor.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = "实时微调中",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = liveColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
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

            HorizontalDivider(color = DarkBorder, thickness = 0.8.dp)

            // ==============================================================
            // 🇨🇳 中国传统色 (zhongguose.com) 526 种全量预置颜色快速选色栏
            // ==============================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🇨🇳 中国传统色预置 (526色)",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Surface(
                    onClick = { showChineseColorBrowser = true },
                    shape = RoundedCornerShape(6.dp),
                    color = DopamineBlue.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, DopamineBlue.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "📖 搜索与全景浏览",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DopamineBlue,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // 色系分类筛选标签 (水平滚动)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ChineseColors.CATEGORIES.forEach { category ->
                    val isSelected = selectedCategory == category
                    Surface(
                        onClick = { selectedCategory = category },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) DopamineAccent.copy(alpha = 0.2f) else DarkCard,
                        border = BorderStroke(1.dp, if (isSelected) DopamineAccent else DarkBorder)
                    ) {
                        Text(
                            text = category,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) DopamineAccent else TextSecondary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // 预设颜色快捷选择微缩条 (按所选分类展示精选色块，水平滚动，1秒直达)
            val filteredPresetColors = remember(selectedCategory) {
                if (selectedCategory == "全部") {
                    ChineseColors.allColors.take(40)
                } else {
                    ChineseColors.allColors.filter { it.category == selectedCategory }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filteredPresetColors.forEach { cColor ->
                    val c = Color(cColor.r, cColor.g, cColor.b)
                    val isCurrent = liveHex.equals(cColor.hex, ignoreCase = true)

                    Surface(
                        onClick = {
                            red = cColor.r.toFloat()
                            green = cColor.g.toFloat()
                            blue = cColor.b.toFloat()
                            onColorChange(cColor.hex)
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isCurrent) c.copy(alpha = 0.3f) else DarkCard,
                        border = BorderStroke(if (isCurrent) 1.5.dp else 1.dp, if (isCurrent) c else DarkBorder),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = cColor.name,
                                fontSize = 10.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCurrent) TextPrimary else TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }

    // 中国传统色 526 色全量图谱浏览器弹窗
    if (showChineseColorBrowser) {
        ChineseColorBrowserDialog(
            currentHex = liveHex,
            onSelectColor = { selected ->
                red = selected.r.toFloat()
                green = selected.g.toFloat()
                blue = selected.b.toFloat()
                onColorChange(selected.hex)
                showChineseColorBrowser = false
            },
            onDismiss = { showChineseColorBrowser = false }
        )
    }
}

/**
 * 中国传统色（zhongguose.com）全景大图谱浏览器弹窗
 * 支持 526 种色彩搜索、色系筛选、拼音检索与一键选用
 */
@Composable
fun ChineseColorBrowserDialog(
    currentHex: String,
    onSelectColor: (ChineseColor) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf("全部") }

    val filteredList = remember(searchQuery, selectedCat) {
        ChineseColors.allColors.filter { item ->
            val matchCat = selectedCat == "全部" || item.category == selectedCat
            val matchSearch = searchQuery.isBlank() ||
                    item.name.contains(searchQuery.trim(), ignoreCase = true) ||
                    item.pinyin.contains(searchQuery.trim(), ignoreCase = true) ||
                    item.hex.contains(searchQuery.trim(), ignoreCase = true)
            matchCat && matchSearch
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkCard,
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 顶部标题
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DopaminePurple.copy(alpha = 0.2f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🇨🇳", fontSize = 18.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "中国传统色全量色谱",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = DarkSurface,
                                    border = BorderStroke(1.dp, DarkBorder)
                                ) {
                                    Text(
                                        text = "共${ChineseColors.allColors.size}色",
                                        fontSize = 10.sp,
                                        color = DopamineCyan,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "源自中国色 (zhongguose.com) 官方色名、色谱与标准 RGB",
                                fontSize = 10.5.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 搜索栏
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索颜色中文名 (如: 胭脂、天青) 或拼音...", fontSize = 12.sp, color = TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DopaminePurple,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 色系选择标签
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ChineseColors.CATEGORIES.forEach { category ->
                        val isSelected = selectedCat == category
                        Surface(
                            onClick = { selectedCat = category },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) DopaminePurple.copy(alpha = 0.2f) else DarkSurface,
                            border = BorderStroke(1.dp, if (isSelected) DopaminePurple else DarkBorder)
                        ) {
                            Text(
                                text = category,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) DopaminePurple else TextSecondary,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = DarkBorder, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // 颜色网格瀑布展示
                Text(
                    text = "符合条件色彩 (${filteredList.size} 项，点击直接应用)：",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 每行 3 个色彩卡片
                    filteredList.chunked(3).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowItems.forEach { item ->
                                val color = Color(item.r, item.g, item.b)
                                val isSelected = currentHex.equals(item.hex, ignoreCase = true)

                                Surface(
                                    onClick = { onSelectColor(item) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) color.copy(alpha = 0.25f) else DarkSurface,
                                    border = BorderStroke(
                                        if (isSelected) 1.5.dp else 0.8.dp,
                                        if (isSelected) color else DarkBorder
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = item.name,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = item.hex,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }
                            // 补全空位
                            if (rowItems.size < 3) {
                                repeat(3 - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
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

    // 匹配中国色名称
    val matched = remember(colorHex) {
        ChineseColors.allColors.find { it.hex.equals(colorHex, ignoreCase = true) }
    }

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
                if (matched != null) {
                    Text(
                        text = "🇨🇳 ${matched.name}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = DopamineAccent,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
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
