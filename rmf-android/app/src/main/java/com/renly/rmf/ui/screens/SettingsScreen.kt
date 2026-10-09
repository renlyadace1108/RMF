package com.renly.rmf.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.domain.service.AuditReport
import com.renly.rmf.domain.service.CalendarSyncResult
import com.renly.rmf.domain.service.FocusLiveService
import com.renly.rmf.domain.service.GoogleDrivePreferences
import com.renly.rmf.domain.service.GoogleDriveSyncService
import com.renly.rmf.domain.service.ScheduleReminderManager
import com.renly.rmf.domain.service.SystemCalendarSyncService
import com.renly.rmf.ui.components.ExpandableRgbColorPicker
import com.renly.rmf.ui.components.RgbColorPicker
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import com.renly.rmf.domain.service.NavigationPreferences

@Composable
fun SettingsScreen(
    currentThemeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    currentAccentColorHex: String = com.renly.rmf.ui.theme.ThemePreferences.DEFAULT_ACCENT_COLOR,
    onAccentColorChange: (String) -> Unit = {},
    currentCustomDarkBgHex: String = com.renly.rmf.ui.theme.ThemePreferences.DEFAULT_DARK_BG_COLOR,
    onDarkBgColorChange: (String) -> Unit = {},
    currentCustomLightBgHex: String = com.renly.rmf.ui.theme.ThemePreferences.DEFAULT_LIGHT_BG_COLOR,
    onLightBgColorChange: (String) -> Unit = {},
    isBottomBarVisible: Boolean = true,
    onBottomBarVisibleChange: (Boolean) -> Unit = {},
    enabledNavRoutes: List<String> = NavigationPreferences.DEFAULT_ENABLED_ROUTES,
    onEnabledNavRoutesChange: (List<String>) -> Unit = {},
    defaultHomeRoute: String = NavigationPreferences.DEFAULT_HOME_ROUTE,
    onDefaultHomeRouteChange: (String) -> Unit = {},
    syncDao: com.renly.rmf.data.local.dao.SyncDao? = null,
    onNavigateToGoals: () -> Unit,
    onNavigateToExpenses: () -> Unit,
    onNavigateToFitness: () -> Unit = {},
    onNavigateToDailyReport: () -> Unit,
    onNavigateToSummary: () -> Unit,
    onNavigateToTutorial: () -> Unit,
    onTriggerAudit: () -> AuditReport,
    onTriggerAiReview: (String, (String) -> Unit) -> Unit,
    onExportData: () -> Unit,
    onExportLosslessDb: () -> Unit = {},
    onImportLosslessDb: () -> Unit = {},
    onClearAllBusinessData: () -> Unit = {},
    onTriggerUpload: () -> Unit,
    onTriggerDownload: () -> Unit
) {
    var geminiApiKey by remember { mutableStateOf("") }
    var auditReport by remember { mutableStateOf<AuditReport?>(null) }
    var aiReviewText by remember { mutableStateOf<String?>(null) }
    var isGeneratingAi by remember { mutableStateOf(false) }
    var showTagModal by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "工作台与高级功能",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 主题外观切换卡片
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkCard,
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "🎨 主题外观模式",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "自由切换日间清爽、夜间深邃或自动跟随系统",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.values().forEach { mode ->
                        val isSelected = currentThemeMode == mode
                        Surface(
                            onClick = { onThemeModeChange(mode) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) DopamineBlue.copy(alpha = 0.18f) else DarkSurface,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) DopamineBlue else DarkBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(mode.icon, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = mode.title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) DopamineBlue else TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "🎨 主题强调色 (Accent Color)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "选择预设调色盘或输入自定义十六进制色号，全局光效与按钮高亮即时生效",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )

                // 预设强调色圆点
                val presetAccentColors = listOf(
                    "#1A73E8" to "Google蓝",
                    "#38BDF8" to "天青蓝",
                    "#8B5CF6" to "罗兰紫",
                    "#10B981" to "翡翠绿",
                    "#F59E0B" to "琥珀橙",
                    "#EF4444" to "珊瑚红",
                    "#EC4899" to "蔷薇粉",
                    "#06B6D4" to "极光青"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    presetAccentColors.forEach { (hex, title) ->
                        val isSelected = currentAccentColorHex.equals(hex, ignoreCase = true)
                        val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Cyan }
                        Surface(
                            onClick = { onAccentColorChange(hex) },
                            shape = CircleShape,
                            color = color,
                            border = BorderStroke(if (isSelected) 2.5.dp else 0.dp, if (isSelected) TextPrimary else Color.Transparent),
                            modifier = Modifier.size(32.dp)
                        ) {}
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 自定义 HEX 输入框
                var customHexInput by remember(currentAccentColorHex) { mutableStateOf(currentAccentColorHex) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = customHexInput,
                        onValueChange = { input ->
                            customHexInput = input
                            if (input.matches("^#[0-9a-fA-F]{6}$".toRegex())) {
                                onAccentColorChange(input)
                            }
                        },
                        label = { Text("自定义色号 (如 #1A73E8)", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        leadingIcon = {
                            val previewColor = try {
                                Color(android.graphics.Color.parseColor(customHexInput))
                            } catch (_: Exception) {
                                Color.Transparent
                            }
                            Surface(
                                shape = CircleShape,
                                color = previewColor,
                                border = BorderStroke(1.dp, DarkBorder),
                                modifier = Modifier.size(20.dp)
                            ) {}
                        },
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (customHexInput.matches("^#[0-9a-fA-F]{6}$".toRegex())) {
                                onAccentColorChange(customHexInput)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DopamineAccent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("应用", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // RGB 实时预览调色微调器
                ExpandableRgbColorPicker(
                    colorHex = currentAccentColorHex,
                    onColorChange = { newHex ->
                        customHexInput = newHex
                        onAccentColorChange(newHex)
                    },
                    title = "🎨 RGB 实时微调选色",
                    defaultExpanded = false
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // ============================================
                // 自定义背景底色风格 (Background Tone Customization)
                // ============================================
                Text(
                    text = "🌌 自定义背景底色风格 (Background Tone)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "自由定制深色或日间模式的主背景基调，支持预设色盘与 RGB 实时调色预览",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )

                // 选项卡：切换设置深色背景 或 日间背景
                var selectedBgTab by remember { mutableStateOf(if (currentThemeMode == ThemeMode.LIGHT) 1 else 0) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = { selectedBgTab = 0 },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedBgTab == 0) DopamineBlue.copy(alpha = 0.15f) else DarkSurface,
                        border = BorderStroke(1.dp, if (selectedBgTab == 0) DopamineBlue else DarkBorder),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🌙 深色底色", fontSize = 12.sp, fontWeight = if (selectedBgTab == 0) FontWeight.Bold else FontWeight.Normal, color = if (selectedBgTab == 0) DopamineBlue else TextSecondary)
                        }
                    }

                    Surface(
                        onClick = { selectedBgTab = 1 },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedBgTab == 1) DopamineBlue.copy(alpha = 0.15f) else DarkSurface,
                        border = BorderStroke(1.dp, if (selectedBgTab == 1) DopamineBlue else DarkBorder),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("☀️ 日间底色", fontSize = 12.sp, fontWeight = if (selectedBgTab == 1) FontWeight.Bold else FontWeight.Normal, color = if (selectedBgTab == 1) DopamineBlue else TextSecondary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedBgTab == 0) {
                    // 深色模式预设
                    val presetDarkBgs = listOf(
                        "#090B0E" to "极光黑",
                        "#000000" to "AMOLED纯黑",
                        "#18191B" to "炭墨黑",
                        "#111827" to "幽蓝深夜",
                        "#0F172A" to "深海沉夜",
                        "#171717" to "中性黑"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetDarkBgs.forEach { (hex, title) ->
                            val isSelected = currentCustomDarkBgHex.equals(hex, ignoreCase = true)
                            val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Black }
                            Surface(
                                onClick = { onDarkBgColorChange(hex) },
                                shape = CircleShape,
                                color = color,
                                border = BorderStroke(if (isSelected) 2.5.dp else 1.dp, if (isSelected) DopamineBlue else DarkBorder),
                                modifier = Modifier.size(32.dp)
                            ) {}
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    var customDarkBgInput by remember(currentCustomDarkBgHex) { mutableStateOf(currentCustomDarkBgHex) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = customDarkBgInput,
                            onValueChange = { input ->
                                customDarkBgInput = input
                                if (input.matches("^#[0-9a-fA-F]{6}$".toRegex())) {
                                    onDarkBgColorChange(input)
                                }
                            },
                            label = { Text("深色背景 HEX (如 #090B0E)", fontSize = 11.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            leadingIcon = {
                                val previewColor = try {
                                    Color(android.graphics.Color.parseColor(customDarkBgInput))
                                } catch (_: Exception) {
                                    Color.Black
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = previewColor,
                                    border = BorderStroke(1.dp, DarkBorder),
                                    modifier = Modifier.size(20.dp)
                                ) {}
                            },
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (customDarkBgInput.matches("^#[0-9a-fA-F]{6}$".toRegex())) {
                                    onDarkBgColorChange(customDarkBgInput)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DopamineAccent),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("应用", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // RGB 实时预览调色微调器
                    ExpandableRgbColorPicker(
                        colorHex = currentCustomDarkBgHex,
                        onColorChange = { newHex ->
                            customDarkBgInput = newHex
                            onDarkBgColorChange(newHex)
                        },
                        title = "🌙 深色背景 RGB 实时调色",
                        defaultExpanded = false
                    )
                } else {
                    // 日间模式预设
                    val presetLightBgs = listOf(
                        "#F8FAFC" to "象牙暖玉白",
                        "#FFFFFF" to "极简纯白",
                        "#F1F5F9" to "冰川冷灰",
                        "#F0FDF4" to "护眼薄荷",
                        "#FFFBEB" to "暖阳淡米",
                        "#F5F5F7" to "原色金属灰"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetLightBgs.forEach { (hex, title) ->
                            val isSelected = currentCustomLightBgHex.equals(hex, ignoreCase = true)
                            val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.White }
                            Surface(
                                onClick = { onLightBgColorChange(hex) },
                                shape = CircleShape,
                                color = color,
                                border = BorderStroke(if (isSelected) 2.5.dp else 1.dp, if (isSelected) DopamineBlue else DarkBorder),
                                modifier = Modifier.size(32.dp)
                            ) {}
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    var customLightBgInput by remember(currentCustomLightBgHex) { mutableStateOf(currentCustomLightBgHex) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = customLightBgInput,
                            onValueChange = { input ->
                                customLightBgInput = input
                                if (input.matches("^#[0-9a-fA-F]{6}$".toRegex())) {
                                    onLightBgColorChange(input)
                                }
                            },
                            label = { Text("日间背景 HEX (如 #F8FAFC)", fontSize = 11.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            leadingIcon = {
                                val previewColor = try {
                                    Color(android.graphics.Color.parseColor(customLightBgInput))
                                } catch (_: Exception) {
                                    Color.White
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = previewColor,
                                    border = BorderStroke(1.dp, DarkBorder),
                                    modifier = Modifier.size(20.dp)
                                ) {}
                            },
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (customLightBgInput.matches("^#[0-9a-fA-F]{6}$".toRegex())) {
                                    onLightBgColorChange(customLightBgInput)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DopamineAccent),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("应用", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // RGB 实时预览调色微调器
                    ExpandableRgbColorPicker(
                        colorHex = currentCustomLightBgHex,
                        onColorChange = { newHex ->
                            customLightBgInput = newHex
                            onLightBgColorChange(newHex)
                        },
                        title = "☀️ 日间背景 RGB 实时调色",
                        defaultExpanded = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 📱 导航与主页展示自定义卡片 (Navigation & Home Customization)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkCard,
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "📱 主页与底部快捷栏自定义",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "自选是否在底部栏展示健身、目标等模块，或彻底隐藏底部栏开启全屏沉浸",
                            fontSize = 11.5.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Switch(
                        checked = isBottomBarVisible,
                        onCheckedChange = { onBottomBarVisibleChange(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = DopamineBlue,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurface
                        )
                    )
                }

                if (!isBottomBarVisible) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = DopaminePurple.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, DopaminePurple.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("✨", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "底部快捷栏已隐藏（全屏沉浸模式）。页面右下角已开启轻量悬浮导航胶囊，可随时点击切换任意模块或返回工作台重新开启底部栏。",
                                fontSize = 11.5.sp,
                                color = DopaminePurple,
                                lineHeight = 16.sp
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "📌 底部快捷栏选项 (已选 ${enabledNavRoutes.size} 项，建议 3~6 项最佳)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "可自选是否把「健身」加入底部快捷栏，随时增减个性化选项",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )

                    // 快捷预设按钮组
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            onClick = {
                                onEnabledNavRoutesChange(listOf("schedule", "timetable", "study", "focus", "settings"))
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = DarkSurface,
                            border = BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("经典 5 项", fontSize = 11.sp, color = TextSecondary)
                            }
                        }

                        Surface(
                            onClick = {
                                onEnabledNavRoutesChange(listOf("schedule", "timetable", "focus", "fitness", "settings"))
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = DopamineCyan.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, DopamineCyan.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🏋️ 健身专属 5 项", fontSize = 11.sp, color = DopamineCyan, fontWeight = FontWeight.Bold)
                            }
                        }

                        Surface(
                            onClick = {
                                onEnabledNavRoutesChange(listOf("schedule", "timetable", "study", "focus", "fitness", "settings"))
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = DarkSurface,
                            border = BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("全能 6 项", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 所有可选模块列表（两列排列）
                    val allItems = NavigationPreferences.ALL_AVAILABLE_NAV_ITEMS
                    allItems.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { item ->
                                val isChecked = enabledNavRoutes.contains(item.route)
                                Surface(
                                    onClick = {
                                        val newRoutes = if (isChecked) {
                                            if (enabledNavRoutes.size > 1) {
                                                enabledNavRoutes.filter { it != item.route }
                                            } else {
                                                enabledNavRoutes
                                            }
                                        } else {
                                            enabledNavRoutes + item.route
                                        }
                                        onEnabledNavRoutesChange(newRoutes)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isChecked) DopamineBlue.copy(alpha = 0.16f) else DarkSurface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isChecked) DopamineBlue else DarkBorder
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(item.iconEmoji, fontSize = 15.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = item.title,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isChecked) TextPrimary else TextSecondary
                                                )
                                                if (item.route == "fitness") {
                                                    Text(
                                                        text = "⭐ 健身模块",
                                                        fontSize = 9.sp,
                                                        color = DopamineCyan
                                                    )
                                                }
                                            }
                                        }
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = null,
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = DopamineBlue,
                                                checkmarkColor = Color.White,
                                                uncheckedColor = DarkBorder
                                            ),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 实时底栏布局预览
                    Text(
                        text = "👀 底部栏实时模拟预览 (即时生效):",
                        fontSize = 11.5.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            enabledNavRoutes.forEach { route ->
                                val item = allItems.find { it.route == route }
                                if (item != null) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(item.iconEmoji, fontSize = 13.sp)
                                        Text(
                                            text = item.title,
                                            fontSize = 9.5.sp,
                                            color = if (route == "fitness") DopamineCyan else DopamineBlue
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // 默认启动主页单选
                Text(
                    text = "🏠 默认启动主页 (Default Launch Screen)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "每次打开应用冷启动时优先展示的页面",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )

                val homeCandidates = listOf(
                    "schedule" to "📅 日程",
                    "timetable" to "🎓 课表",
                    "study" to "📖 学习",
                    "focus" to "⏱️ 专注",
                    "fitness" to "🏋️ 健身",
                    "goals" to "🎯 目标",
                    "expenses" to "💰 记账",
                    "settings" to "⚙️ 工作台"
                )

                homeCandidates.chunked(4).forEach { rowList ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowList.forEach { (route, label) ->
                            val isSelected = defaultHomeRoute == route
                            Surface(
                                onClick = { onDefaultHomeRouteChange(route) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) DopamineBlue.copy(alpha = 0.2f) else DarkSurface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) DopamineBlue else DarkBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) DopamineBlue else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 核心子系统快捷入口
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onNavigateToGoals,
                colors = ButtonDefaults.buttonColors(containerColor = DopaminePurple),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text("🎯 目标金字塔", fontSize = 11.5.sp)
            }

            Button(
                onClick = onNavigateToExpenses,
                colors = ButtonDefaults.buttonColors(containerColor = DopamineAmber),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text("💰 日常记账", fontSize = 11.5.sp)
            }

            Button(
                onClick = onNavigateToFitness,
                colors = ButtonDefaults.buttonColors(containerColor = DopamineCyan),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text("🏋️ 每日健身", fontSize = 11.5.sp, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onNavigateToDailyReport,
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text("🏆 每日复盘", fontSize = 11.5.sp, color = DopamineGreen)
            }

            Button(
                onClick = onNavigateToSummary,
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text("📊 效能统计", fontSize = 11.5.sp, color = DopamineBlue)
            }

            Button(
                onClick = onNavigateToTutorial,
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text("📖 使用教程", fontSize = 11.5.sp, color = TextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 电脑版 1:1 标签分类库管理入口
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { showTagModal = true },
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, DopaminePurple.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text("🏷️ 标签分类库与色彩管理 (Tag Management)", fontSize = 12.sp, color = DopaminePurple, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 节律健康审计卡片
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🩺 日程节律健康审计",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "深度诊断时间硬冲突排期、连续4小时超载疲劳断崖与认知负荷均衡度。",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                Button(
                    onClick = { auditReport = onTriggerAudit() },
                    colors = ButtonDefaults.buttonColors(containerColor = DopamineCyan),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("立即执行节律诊断", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 🤖 多模型 AI 服务中枢卡片 (Gemini / 通义千问 / DeepSeek / 自定义)
        val aiContext = androidx.compose.ui.platform.LocalContext.current
        var currentAiProvider by remember { mutableStateOf(com.renly.rmf.domain.service.AiPreferences.getProvider(aiContext)) }
        var currentAiApiKey by remember { mutableStateOf(com.renly.rmf.domain.service.AiPreferences.getApiKey(aiContext)) }
        var currentAiBaseUrl by remember { mutableStateOf(com.renly.rmf.domain.service.AiPreferences.getBaseUrl(aiContext)) }
        var currentAiModel by remember { mutableStateOf(com.renly.rmf.domain.service.AiPreferences.getModel(aiContext)) }
        var isApiKeyVisible by remember { mutableStateOf(false) }
        var testConnectionStatus by remember { mutableStateOf<String?>(null) }
        var isTestingConnection by remember { mutableStateOf(false) }

        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🤖 AI 模型服务中枢",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (currentAiProvider.isVisionSupported) {
                        Surface(
                            color = DopamineGreen.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "📷 支持课表截图识别",
                                fontSize = 10.sp,
                                color = DopamineGreen,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = DopamineOrange.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "📝 纯文本规划",
                                fontSize = 10.sp,
                                color = DopamineOrange,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "支持 Google Gemini、通义千问 (Qwen)、DeepSeek 及自建中转接口。一键用于课表截图识别与每日精力智能复盘。",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                // 1. 服务商切换胶囊网格
                Text(
                    text = "选择 AI 服务商 (Provider):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    com.renly.rmf.domain.service.AiProvider.entries.forEach { provider ->
                        val isSelected = currentAiProvider == provider
                        Surface(
                            onClick = {
                                currentAiProvider = provider
                                com.renly.rmf.domain.service.AiPreferences.setProvider(aiContext, provider)
                                currentAiBaseUrl = provider.defaultBaseUrl
                                com.renly.rmf.domain.service.AiPreferences.setBaseUrl(aiContext, provider.defaultBaseUrl)
                                currentAiModel = provider.defaultModel
                                com.renly.rmf.domain.service.AiPreferences.setModel(aiContext, provider.defaultModel)
                                testConnectionStatus = null
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) DopaminePurple.copy(alpha = 0.2f) else DarkSurface,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) DopaminePurple else DarkBorder
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp)
                            ) {
                                Text(
                                    text = when (provider) {
                                        com.renly.rmf.domain.service.AiProvider.GEMINI -> "Gemini"
                                        com.renly.rmf.domain.service.AiProvider.QWEN -> "千问 Qwen"
                                        com.renly.rmf.domain.service.AiProvider.DEEPSEEK -> "DeepSeek"
                                        com.renly.rmf.domain.service.AiProvider.CUSTOM -> "自定义"
                                    },
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) DopaminePurple else TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Text(
                    text = currentAiProvider.description,
                    fontSize = 11.sp,
                    color = DopamineCyan.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
                )

                // 2. API Key 输入框
                OutlinedTextField(
                    value = currentAiApiKey,
                    onValueChange = {
                        currentAiApiKey = it
                        com.renly.rmf.domain.service.AiPreferences.setApiKey(aiContext, it)
                    },
                    label = { Text("${currentAiProvider.name} API Key") },
                    placeholder = { Text("在此粘贴您的 API 密钥...") },
                    singleLine = true,
                    visualTransformation = if (isApiKeyVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                            Text(if (isApiKeyVisible) "🙈" else "👁️", fontSize = 14.sp)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Base URL 输入框 (可选/支持反代)
                OutlinedTextField(
                    value = currentAiBaseUrl,
                    onValueChange = {
                        currentAiBaseUrl = it
                        com.renly.rmf.domain.service.AiPreferences.setBaseUrl(aiContext, it)
                    },
                    label = { Text("接口 Base URL (支持自定义中转)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 4. 模型名称输入与推荐快捷标签
                OutlinedTextField(
                    value = currentAiModel,
                    onValueChange = {
                        currentAiModel = it
                        com.renly.rmf.domain.service.AiPreferences.setModel(aiContext, it)
                    },
                    label = { Text("模型标识 (Model ID)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // 推荐模型快捷胶囊
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("推荐: ", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.align(Alignment.CenterVertically))
                    currentAiProvider.recommendedModels.take(3).forEach { modelId ->
                        val isCurrent = currentAiModel == modelId
                        Surface(
                            onClick = {
                                currentAiModel = modelId
                                com.renly.rmf.domain.service.AiPreferences.setModel(aiContext, modelId)
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isCurrent) DopamineBlue.copy(alpha = 0.2f) else DarkSurface,
                            border = BorderStroke(1.dp, if (isCurrent) DopamineBlue else DarkBorder)
                        ) {
                            Text(
                                text = modelId,
                                fontSize = 10.5.sp,
                                color = if (isCurrent) DopamineBlue else TextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5. 操作按钮：测试连接 & 生成复盘
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            isTestingConnection = true
                            testConnectionStatus = "正在向 ${currentAiModel} 发送握手信号..."
                            coroutineScope.launch {
                                when (val testRes = com.renly.rmf.domain.service.AiService.testConnection(aiContext)) {
                                    is com.renly.rmf.domain.service.AiResult.Success -> {
                                        testConnectionStatus = testRes.data
                                    }
                                    is com.renly.rmf.domain.service.AiResult.Error -> {
                                        testConnectionStatus = "❌ 连接失败: ${testRes.errorMessage}"
                                    }
                                }
                                isTestingConnection = false
                            }
                        },
                        enabled = !isTestingConnection && currentAiApiKey.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, DopamineCyan.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (isTestingConnection) "握手中..." else "🔌 测试连接",
                            color = DopamineCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            isGeneratingAi = true
                            onTriggerAiReview(currentAiApiKey) { result ->
                                aiReviewText = result
                                isGeneratingAi = false
                            }
                        },
                        enabled = !isGeneratingAi && currentAiApiKey.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (isGeneratingAi) "思考中..." else "✨ 生成今日复盘",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // 握手测试结果反馈横幅
                if (testConnectionStatus != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (testConnectionStatus?.startsWith("✅") == true)
                            DopamineGreen.copy(alpha = 0.12f)
                        else
                            DopamineOrange.copy(alpha = 0.12f),
                        border = BorderStroke(
                            1.dp,
                            if (testConnectionStatus?.startsWith("✅") == true)
                                DopamineGreen.copy(alpha = 0.4f)
                            else
                                DopamineOrange.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = testConnectionStatus ?: "",
                            fontSize = 11.5.sp,
                            color = if (testConnectionStatus?.startsWith("✅") == true) DopamineGreen else DopamineOrange,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Google Drive 官方 API 跨端同步卡片 (1:1 像素级对齐 PC 端)
        val currentContext = androidx.compose.ui.platform.LocalContext.current
        var googleClientId by remember { mutableStateOf(GoogleDrivePreferences.getClientId(currentContext)) }
        var googleClientSecret by remember { mutableStateOf(GoogleDrivePreferences.getClientSecret(currentContext)) }
        var driveStatusText by remember { mutableStateOf("") }
        var lastSyncDisplay by remember { mutableStateOf(GoogleDrivePreferences.getLastSyncTime(currentContext)) }
        var isDriveWorking by remember { mutableStateOf(false) }
        var showAuthCodeDialog by remember { mutableStateOf(false) }
        var authCodeInput by remember { mutableStateOf("") }

        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("☁️", fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
                        Text(
                            text = "Google Drive 云端安全备份与同步",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    val isLinked = GoogleDrivePreferences.isAuthorized(currentContext)
                    Surface(
                        color = if (isLinked) Color(0xFF064E3B) else Color(0xFF1E293B),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, if (isLinked) Color(0xFF059669) else DopamineBlue)
                    ) {
                        Text(
                            text = if (isLinked) "已授权连接" else "未连接",
                            color = if (isLinked) Color(0xFF6EE7B7) else DopamineBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = "基于 Google Drive 官方 API，将整个 SQLite 数据库（含所有日程、课程表、每日成果、学习档案）完整安全备份至云端。与 Windows 电脑端 100% 互通，确保访问并读写同一个云端数据库文件 (rmf_cloud_backup.db)。",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                Text(
                    text = "Google Cloud OAuth 2.0 凭据配置：",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DopamineBlue,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                OutlinedTextField(
                    value = googleClientId,
                    onValueChange = { googleClientId = it },
                    label = { Text("Client ID (.apps.googleusercontent.com)", fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = DopamineBlue,
                        unfocusedBorderColor = DarkBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = googleClientSecret,
                    onValueChange = { googleClientSecret = it },
                    label = { Text("Client Secret", fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = DopamineBlue,
                        unfocusedBorderColor = DarkBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "上次备份：$lastSyncDisplay",
                        fontSize = 11.5.sp,
                        color = TextMuted,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            GoogleDrivePreferences.setClientId(currentContext, googleClientId)
                            GoogleDrivePreferences.setClientSecret(currentContext, googleClientSecret)
                            driveStatusText = "✓ OAuth 凭据已保存到本地配置！"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("保存凭据", fontSize = 12.sp, color = Color.White)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    OutlinedButton(
                        onClick = {
                            GoogleDrivePreferences.clearAuth(currentContext)
                            driveStatusText = "已解除授权并清除本地 Token。"
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DopamineRed),
                        border = BorderStroke(1.dp, DopamineRed),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("解除授权", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 云端操作按钮 (API 互通)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val cId = googleClientId.trim()
                            val cSec = googleClientSecret.trim()
                            if (cId.isBlank()) {
                                driveStatusText = "✗ 请先填写 Client ID"
                                return@Button
                            }
                            GoogleDrivePreferences.setClientId(currentContext, cId)
                            GoogleDrivePreferences.setClientSecret(currentContext, cSec)

                            if (!GoogleDrivePreferences.isAuthorized(currentContext)) {
                                // 启动授权弹窗
                                showAuthCodeDialog = true
                            } else {
                                isDriveWorking = true
                                driveStatusText = "正在调用 Google Drive API 上传..."
                                onTriggerUpload()
                            }
                        },
                        enabled = !isDriveWorking,
                        colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("⬆ 上传完整备份至云端", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            val cId = googleClientId.trim()
                            val cSec = googleClientSecret.trim()
                            if (cId.isBlank()) {
                                driveStatusText = "✗ 请先填写 Client ID"
                                return@OutlinedButton
                            }
                            GoogleDrivePreferences.setClientId(currentContext, cId)
                            GoogleDrivePreferences.setClientSecret(currentContext, cSec)

                            if (!GoogleDrivePreferences.isAuthorized(currentContext)) {
                                showAuthCodeDialog = true
                            } else {
                                isDriveWorking = true
                                driveStatusText = "正在从 Google Drive API 拉取..."
                                onTriggerDownload()
                            }
                        },
                        enabled = !isDriveWorking,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DopamineGreen),
                        border = BorderStroke(1.dp, DopamineGreen),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("⬇ 从云端拉取恢复本地", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (driveStatusText.isNotBlank()) {
                    Text(
                        text = driveStatusText,
                        fontSize = 11.5.sp,
                        color = if (driveStatusText.startsWith("✓")) DopamineGreen else if (driveStatusText.startsWith("✗")) DopamineRed else DopamineCyan,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // OAuth 2.0 快捷授权引导弹窗
        if (showAuthCodeDialog) {
            AlertDialog(
                onDismissRequest = { showAuthCodeDialog = false },
                containerColor = DarkSurface,
                title = { Text("🔑 授权 Google Drive API", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "为了让安卓端与电脑端访问同一个云盘文件，需要完成一次 OAuth 2.0 授权：",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )

                        Button(
                            onClick = {
                                val cId = GoogleDrivePreferences.getClientId(currentContext)
                                val authUrl = GoogleDriveSyncService.buildAuthorizationUrl(cId)
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(authUrl))
                                currentContext.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("1. 打开浏览器登录并获取授权码", fontSize = 12.sp)
                        }

                        Text("2. 在下方粘贴授权码 (Authorization Code) 或直接输入 Access Token：", fontSize = 12.sp, color = TextSecondary)

                        OutlinedTextField(
                            value = authCodeInput,
                            onValueChange = { authCodeInput = it },
                            placeholder = { Text("粘贴 4/0A... 授权码或 ya29... Token", fontSize = 11.sp, color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = DopamineBlue,
                                unfocusedBorderColor = DarkBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val code = authCodeInput.trim()
                            if (code.isNotBlank()) {
                                if (code.startsWith("ya29.")) {
                                    // 直接作为 Access Token 存入
                                    GoogleDrivePreferences.setAccessToken(currentContext, code)
                                    driveStatusText = "✓ Access Token 已保存生效！"
                                    showAuthCodeDialog = false
                                } else {
                                    // 调用换取 Token
                                    driveStatusText = "正在兑换 Token..."
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                        val res = GoogleDriveSyncService.exchangeAuthCode(
                                            clientId = GoogleDrivePreferences.getClientId(currentContext),
                                            clientSecret = GoogleDrivePreferences.getClientSecret(currentContext),
                                            authCode = code
                                        )
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            if (res.isSuccess) {
                                                val (acc, ref) = res.getOrThrow()
                                                GoogleDrivePreferences.setAccessToken(currentContext, acc)
                                                if (ref != null) GoogleDrivePreferences.setRefreshToken(currentContext, ref)
                                                driveStatusText = "✓ Google Drive 授权成功！"
                                                showAuthCodeDialog = false
                                            } else {
                                                driveStatusText = "✗ 授权失败: ${res.exceptionOrNull()?.message}"
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DopamineGreen)
                    ) {
                        Text("完成授权", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAuthCodeDialog = false }) {
                        Text("取消", color = TextMuted)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---------------------------------------------------------------------------------
        // 🔔 通知与 ColorOS 流体云实验室 (Notification & Fluid Cloud Lab)
        // ---------------------------------------------------------------------------------
        val notifContext = androidx.compose.ui.platform.LocalContext.current
        val isFluidRunning by FocusLiveService.isRunning.collectAsState()
        val fluidRemaining by FocusLiveService.remainingSeconds.collectAsState()
        val fluidTaskTitle by FocusLiveService.currentTaskTitle.collectAsState()
        var notifFeedbackText by remember { mutableStateOf<String?>(null) }

        val notifPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                ScheduleReminderManager.sendStandardTestNotification(notifContext)
                notifFeedbackText = "✓ 通知权限已就绪，已向通知栏发送测试横幅！"
            } else {
                notifFeedbackText = "⚠️ 未授予通知权限，请在系统设置中允许本应用通知"
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔔", fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
                        Text(
                            text = "通知与流体云实验室",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Surface(
                        color = if (isFluidRunning) Color(0xFF064E3B) else DarkSurface,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, if (isFluidRunning) DopamineGreen else DarkBorder)
                    ) {
                        Text(
                            text = if (isFluidRunning) "流体云实时运行中" else "胶囊已待命",
                            color = if (isFluidRunning) DopamineGreen else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = "深度适配 ColorOS / HyperOS / OriginOS 状态栏动态胶囊、锁屏实时活动、常驻展开卡片以及精准高优先级系统闹钟到期提醒。",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                // 按钮 1: 测试标准日程到期通知
                Button(
                    onClick = {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                notifContext,
                                android.Manifest.permission.POST_NOTIFICATIONS
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (!hasPermission) {
                                notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                return@Button
                            }
                        }
                        ScheduleReminderManager.sendStandardTestNotification(notifContext)
                        notifFeedbackText = "✓ 已发送高优先级标准日程提醒通知！请下拉通知中心查看"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("🔔 测试标准日程提醒通知 (高优先级横幅+声音震动)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 按钮 2: 流体云实时胶囊前台服务启动/停止切换
                OutlinedButton(
                    onClick = {
                        if (isFluidRunning) {
                            FocusLiveService.stop(notifContext)
                            notifFeedbackText = "✓ 已停止流体云前台实时胶囊服务"
                        } else {
                            FocusLiveService.start(notifContext, "流体云实时胶囊测试", 25 * 60)
                            notifFeedbackText = "✓ 已启动流体云实时胶囊！切至桌面或息屏锁屏，挖孔旁将呈现胶囊"
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isFluidRunning) DopamineAmber else DopamineCyan
                    ),
                    border = BorderStroke(1.dp, if (isFluidRunning) DopamineAmber else DopamineCyan),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isFluidRunning) "⏹️ 停止流体云实时胶囊服务 (剩余: ${fluidRemaining / 60}m${fluidRemaining % 60}s)" else "🌊 启动流体云实时胶囊测试 (25分钟心流前台服务)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 按钮 3 & 4: 流体云通知卡片效果测试
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            ScheduleReminderManager.sendFluidCloudTestReminder(notifContext, isCourse = true)
                            notifFeedbackText = "✓ 已触发课表专属流体云胶囊通知卡片！"
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🎓 模拟上课流体云通知", fontSize = 11.5.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            ScheduleReminderManager.sendFluidCloudTestReminder(notifContext, isCourse = false)
                            notifFeedbackText = "✓ 已触发日程专属流体云胶囊通知卡片！"
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("📌 模拟日程流体云通知", fontSize = 11.5.sp)
                    }
                }

                if (notifFeedbackText != null) {
                    Text(
                        text = notifFeedbackText!!,
                        fontSize = 11.5.sp,
                        color = if (notifFeedbackText!!.startsWith("✓")) DopamineGreen else DopamineOrange,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---------------------------------------------------------------------------------
        // 📱 手机系统原生日历同步卡片 (System Calendar Sync Engine)
        // ---------------------------------------------------------------------------------
        val sysCalContext = androidx.compose.ui.platform.LocalContext.current
        var sysCalStatusText by remember { mutableStateOf("") }
        var isSysCalWorking by remember { mutableStateOf(false) }
        var lastSysCalSyncTime by remember { mutableStateOf(SystemCalendarSyncService.getLastSyncTime(sysCalContext)) }
        var isAutoSyncSysCal by remember { mutableStateOf(SystemCalendarSyncService.isAutoSyncEnabled(sysCalContext)) }

        val sysCalPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
        ) { perms ->
            val readGranted = perms[android.Manifest.permission.READ_CALENDAR] ?: false
            val writeGranted = perms[android.Manifest.permission.WRITE_CALENDAR] ?: false
            if (readGranted && writeGranted) {
                isSysCalWorking = true
                sysCalStatusText = "权限已授予，正在同步全部日程到系统日历..."
                coroutineScope.launch {
                    val db = (sysCalContext.applicationContext as com.renly.rmf.RmfApplication).database
                    val activeSchedules = db.scheduleDao().getAllActiveSchedules().first()
                    val result = SystemCalendarSyncService.syncAllSchedules(sysCalContext, activeSchedules)
                    isSysCalWorking = false
                    when (result) {
                        is CalendarSyncResult.Success -> {
                            sysCalStatusText = "✓ ${result.message}"
                            lastSysCalSyncTime = SystemCalendarSyncService.getLastSyncTime(sysCalContext)
                        }
                        is CalendarSyncResult.Error -> {
                            sysCalStatusText = "✗ ${result.error}"
                        }
                    }
                }
            } else {
                sysCalStatusText = "✗ 需要日历读写权限方可将时间块同步至手机系统日历"
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📱", fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
                        Text(
                            text = "手机系统日历互通同步",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Surface(
                        color = Color(0xFF064E3B),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFF059669))
                    ) {
                        Text(
                            text = "小米/OPPO/vivo/华为等",
                            color = Color(0xFF6EE7B7),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = "一键将本地全部日程时间块、验收标准(DoD)和提前提醒无缝同步至手机系统自带日历。无需启动 App 即可在锁屏小组件与负一屏日历卡片中实时查阅。",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )

                Text(
                    text = "上次同步时间: $lastSysCalSyncTime",
                    fontSize = 11.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 一键全量同步按钮
                Button(
                    onClick = {
                        if (!SystemCalendarSyncService.hasCalendarPermissions(sysCalContext)) {
                            sysCalPermissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.READ_CALENDAR,
                                    android.Manifest.permission.WRITE_CALENDAR
                                )
                            )
                            return@Button
                        }
                        isSysCalWorking = true
                        sysCalStatusText = "正在同步全部日程到手机系统日历..."
                        coroutineScope.launch {
                            val db = (sysCalContext.applicationContext as com.renly.rmf.RmfApplication).database
                            val activeSchedules = db.scheduleDao().getAllActiveSchedules().first()
                            val result = SystemCalendarSyncService.syncAllSchedules(sysCalContext, activeSchedules)
                            isSysCalWorking = false
                            when (result) {
                                is CalendarSyncResult.Success -> {
                                    sysCalStatusText = "✓ ${result.message}"
                                    lastSysCalSyncTime = SystemCalendarSyncService.getLastSyncTime(sysCalContext)
                                }
                                is CalendarSyncResult.Error -> {
                                    sysCalStatusText = "✗ ${result.error}"
                                }
                            }
                        }
                    },
                    enabled = !isSysCalWorking,
                    colors = ButtonDefaults.buttonColors(containerColor = DopamineGreen),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isSysCalWorking) "正在同步至系统日历..." else "🔄 立即全量同步日程至手机系统日历",
                        color = Color.Black,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 自动同步开关
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("⚡ 自动增量同步", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("新增或修改时间块时，自动即时写入手机自带日历", fontSize = 10.5.sp, color = TextMuted)
                        }
                        Switch(
                            checked = isAutoSyncSysCal,
                            onCheckedChange = { checked ->
                                if (checked && !SystemCalendarSyncService.hasCalendarPermissions(sysCalContext)) {
                                    sysCalPermissionLauncher.launch(
                                        arrayOf(
                                            android.Manifest.permission.READ_CALENDAR,
                                            android.Manifest.permission.WRITE_CALENDAR
                                        )
                                    )
                                } else {
                                    isAutoSyncSysCal = checked
                                    SystemCalendarSyncService.setAutoSyncEnabled(sysCalContext, checked)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = DopamineGreen
                            )
                        )
                    }
                }

                if (sysCalStatusText.isNotBlank()) {
                    Text(
                        text = sysCalStatusText,
                        fontSize = 11.5.sp,
                        color = if (sysCalStatusText.startsWith("✓")) DopamineGreen else if (sysCalStatusText.startsWith("✗")) DopamineRed else DopamineCyan,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 📅 Google Calendar & .ics 日历互通同步卡片 (对齐 Windows GoogleCalendarService)
        var calSyncStatusText by remember { mutableStateOf("") }
        var isCalWorking by remember { mutableStateOf(false) }
        var icsUrlInput by remember { mutableStateOf(GoogleDrivePreferences.getCalendarIcsUrl(currentContext)) }
        var lastCalSyncTime by remember { mutableStateOf(GoogleDrivePreferences.getCalendarLastSyncTime(currentContext)) }

        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📅", fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
                        Text(
                            text = "Google Calendar 日历互通同步",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    val isLinked = GoogleDrivePreferences.isAuthorized(currentContext)
                    Surface(
                        color = if (isLinked) Color(0xFF064E3B) else Color(0xFF1E293B),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, if (isLinked) Color(0xFF059669) else DopamineBlue)
                    ) {
                        Text(
                            text = if (isLinked) "API 已就绪" else "未授权 OAuth",
                            color = if (isLinked) Color(0xFF6EE7B7) else DopamineBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = "支持通过 Google Calendar API 直连推送日程与上课计划；同时支持标准 RFC 5545 .ics 文件导出或 URL 私密订阅，在手机系统日历/Google日历中实时查看。",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
                )

                Text(
                    text = "上次同步时间: $lastCalSyncTime",
                    fontSize = 11.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 按钮组 1: Google Calendar REST API 直连推送与拉取
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (!GoogleDrivePreferences.isAuthorized(currentContext)) {
                                showAuthCodeDialog = true
                                return@Button
                            }
                            isCalWorking = true
                            calSyncStatusText = "正在推送日程至 Google Calendar..."
                            coroutineScope.launch {
                                val db = (currentContext.applicationContext as com.renly.rmf.RmfApplication).database
                                val service = com.renly.rmf.domain.service.GoogleCalendarSyncService(currentContext, db)
                                val res = service.pushSchedulesToGoogleCalendar()
                                isCalWorking = false
                                when (res) {
                                    is com.renly.rmf.domain.service.CalendarSyncResult.Success -> {
                                        calSyncStatusText = "✓ ${res.message}"
                                        lastCalSyncTime = GoogleDrivePreferences.getCalendarLastSyncTime(currentContext)
                                    }
                                    is com.renly.rmf.domain.service.CalendarSyncResult.Error -> {
                                        calSyncStatusText = "✗ ${res.error}"
                                    }
                                }
                            }
                        },
                        enabled = !isCalWorking,
                        colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🚀 推送日程至 Google", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            if (!GoogleDrivePreferences.isAuthorized(currentContext)) {
                                showAuthCodeDialog = true
                                return@OutlinedButton
                            }
                            isCalWorking = true
                            calSyncStatusText = "正在从 Google Calendar 拉取事件..."
                            coroutineScope.launch {
                                val db = (currentContext.applicationContext as com.renly.rmf.RmfApplication).database
                                val service = com.renly.rmf.domain.service.GoogleCalendarSyncService(currentContext, db)
                                val res = service.pullEventsFromGoogleCalendar(30)
                                isCalWorking = false
                                when (res) {
                                    is com.renly.rmf.domain.service.CalendarSyncResult.Success -> {
                                        calSyncStatusText = "✓ ${res.message}"
                                        lastCalSyncTime = GoogleDrivePreferences.getCalendarLastSyncTime(currentContext)
                                    }
                                    is com.renly.rmf.domain.service.CalendarSyncResult.Error -> {
                                        calSyncStatusText = "✗ ${res.error}"
                                    }
                                }
                            }
                        },
                        enabled = !isCalWorking,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DopamineGreen),
                        border = BorderStroke(1.dp, DopamineGreen),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("⬇ 从 Google 拉取日程", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 按钮组 2: 导出 .ics 通用日历文件 (含课表与日程)
                OutlinedButton(
                    onClick = {
                        isCalWorking = true
                        calSyncStatusText = "正在生成 .ics 日历文件..."
                        coroutineScope.launch {
                            val db = (currentContext.applicationContext as com.renly.rmf.RmfApplication).database
                            val service = com.renly.rmf.domain.service.GoogleCalendarSyncService(currentContext, db)
                            val res = service.exportSchedulesToIcsFile(includeCourses = true)
                            isCalWorking = false
                            if (res.isSuccess) {
                                val file = res.getOrThrow()
                                calSyncStatusText = "✓ 已生成 .ics，调起日历分享..."
                                service.shareIcsFile(file)
                            } else {
                                calSyncStatusText = "✗ 导出失败: ${res.exceptionOrNull()?.message}"
                            }
                        }
                    },
                    enabled = !isCalWorking,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("📁 导出 .ics 通用日历文件 (课表+日程，支持所有日历导入)", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 输入框与按钮组 3: Google Calendar iCal 订阅 URL (对齐 Windows 端)
                Text(
                    text = "或者输入 Google Calendar 私密 iCal 订阅地址 (.ics)：",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                OutlinedTextField(
                    value = icsUrlInput,
                    onValueChange = { icsUrlInput = it },
                    placeholder = { Text("https://calendar.google.com/calendar/ical/.../basic.ics", fontSize = 11.sp, color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = DopamineBlue,
                        unfocusedBorderColor = DarkBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = {
                        val url = icsUrlInput.trim()
                        if (url.isBlank()) {
                            calSyncStatusText = "✗ 请输入有效的 .ics 订阅 URL"
                            return@OutlinedButton
                        }
                        isCalWorking = true
                        calSyncStatusText = "正在从日历订阅地址同步..."
                        coroutineScope.launch {
                            val db = (currentContext.applicationContext as com.renly.rmf.RmfApplication).database
                            val service = com.renly.rmf.domain.service.GoogleCalendarSyncService(currentContext, db)
                            val res = service.syncFromIcsUrl(url)
                            isCalWorking = false
                            when (res) {
                                is com.renly.rmf.domain.service.CalendarSyncResult.Success -> {
                                    calSyncStatusText = "✓ ${res.message}"
                                    lastCalSyncTime = GoogleDrivePreferences.getCalendarLastSyncTime(currentContext)
                                }
                                is com.renly.rmf.domain.service.CalendarSyncResult.Error -> {
                                    calSyncStatusText = "✗ ${res.error}"
                                }
                            }
                        }
                    },
                    enabled = !isCalWorking,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DopamineCyan),
                    border = BorderStroke(1.dp, DopamineCyan),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("🔄 从 iCal 订阅地址同步日程", fontSize = 12.sp)
                }

                if (calSyncStatusText.isNotBlank()) {
                    Text(
                        text = calSyncStatusText,
                        fontSize = 11.5.sp,
                        color = if (calSyncStatusText.startsWith("✓")) DopamineGreen else if (calSyncStatusText.startsWith("✗")) DopamineRed else DopamineCyan,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🛡️", fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
                        Text(
                            text = "无损升级与数据安全防护",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Surface(
                        color = Color(0xFF064E3B),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFF059669))
                    ) {
                        Text(
                            text = "无损保护中",
                            color = Color(0xFF6EE7B7),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = "手机端与电脑端遵循相同升级保障：所有新版本直接覆盖安装，底层数据库结构自动增量对齐，历史数据 100% 完整保留。每次升级或冷启动均自动归档安全快照。",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
                )

                Text(
                    text = com.renly.rmf.domain.service.LosslessUpgradeManager.getLastBackupTime(currentContext),
                    fontSize = 11.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onExportLosslessDb,
                        colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("💾 导出无损数据包", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onImportLosslessDb,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DopamineGreen),
                        border = BorderStroke(1.dp, DopamineGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📥 从数据包还原", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onExportData,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = BorderStroke(1.dp, DarkBorder),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📤 导出开放 JSON 并分享", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                var showClearConfirmDialog by remember { mutableStateOf(false) }

                OutlinedButton(
                    onClick = { showClearConfirmDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DopamineRed),
                    border = BorderStroke(1.dp, DopamineRed.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🗑️ 清空所有业务测试数据 (全新空白开始)", fontSize = 12.sp, color = DopamineRed, fontWeight = FontWeight.SemiBold)
                }

                if (showClearConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearConfirmDialog = false },
                        containerColor = DarkSurface,
                        title = {
                            Text("⚠️ 确认清空所有测试业务数据？", color = DopamineRed, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        },
                        text = {
                            Text(
                                "此操作将清空所有日程、课程、目标、学习记录、健身计划与打卡记录，为您提供一个全新的空白工作台（系统配置与主题模式将保留）。\n\n清空前系统将自动生成一份带时间戳的安全数据快照备份。\n\n确认清空吗？",
                                fontSize = 12.5.sp,
                                color = TextSecondary,
                                lineHeight = 18.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showClearConfirmDialog = false
                                    onClearAllBusinessData()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DopamineRed)
                            ) {
                                Text("确认清空", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearConfirmDialog = false }) {
                                Text("取消", color = TextMuted)
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // © 软件品牌、版权与版本声明 (对齐 Windows 客户端)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.size(24.dp)
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.renly.rmf.R.drawable.ic_logo),
                        contentDescription = "RMF",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "RMF",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Text(
                        text = "v${com.renly.rmf.BuildConfig.VERSION_NAME}",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Made By Renly 2026",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
            Text(
                text = "renly20061108@gmail.com",
                fontSize = 10.5.sp,
                color = TextMuted,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // 节律健康诊断弹窗
    auditReport?.let { report ->
        AlertDialog(
            onDismissRequest = { auditReport = null },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "节律健康诊断评分: ${report.score} 分",
                    color = if (report.score >= 80) DopamineGreen else DopamineAmber,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("深度工作: ${report.totalDeepWorkMinutes}分钟 | 缓冲休息: ${report.totalRestMinutes}分钟", fontSize = 12.sp, color = TextSecondary)
                    HorizontalDivider(color = DarkBorder)
                    report.findings.forEach { finding ->
                        val color = when (finding.level) {
                            "DANGER" -> DopamineRed
                            "WARNING" -> DopamineAmber
                            else -> DopamineGreen
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(finding.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
                                Text(finding.description, fontSize = 11.sp, color = TextPrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { auditReport = null }) { Text("已知晓") }
            }
        )
    }

    // AI 复盘展示弹窗
    aiReviewText?.let { review ->
        AlertDialog(
            onDismissRequest = { aiReviewText = null },
            containerColor = DarkSurface,
            title = { Text("今日 Gemini AI 智能复盘", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(review, color = TextPrimary, fontSize = 13.sp, lineHeight = 20.sp)
                }
            },
            confirmButton = {
                Button(onClick = { aiReviewText = null }) { Text("完成") }
            }
        )
    }

    // 标签分类库与色彩管理弹窗 (TagManagementModal)
    if (showTagModal) {
        TagManagementDialog(
            syncDao = syncDao,
            onDismiss = { showTagModal = false }
        )
    }
}

// -----------------------------------------------------------------------------------------
// 🏷️ 电脑版 1:1 标签分类库与多巴胺色彩管理弹窗 (TagManagementModal + RgbColorPickerModal)
// -----------------------------------------------------------------------------------------
@Composable
fun TagManagementDialog(
    syncDao: com.renly.rmf.data.local.dao.SyncDao?,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val dbTags by (syncDao?.getAllTags() ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())

    var newTagName by remember { mutableStateOf("") }
    var selectedColorIdx by remember { mutableIntStateOf(0) }

    val colorPalette = listOf(
        "#1A73E8",
        "#38BDF8",
        "#8B5CF6",
        "#10B981",
        "#F59E0B",
        "#EF4444",
        "#EC4899",
        "#06B6D4",
        "#7C3AED",
        "#D97706",
        "#059669",
        "#6366F1"
    )

    var customHexInput by remember { mutableStateOf(colorPalette[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🏷️ 日程分类标签库与色彩", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    color = DopaminePurple.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "共${dbTags.size}个标签",
                        fontSize = 11.sp,
                        color = DopaminePurple,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("管理系统日程业务标签体系与专属高质感色彩（与 Windows 端 100% 双端互通）：", fontSize = 11.5.sp, color = TextSecondary)

                // 添加新标签输入区
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newTagName,
                        onValueChange = { newTagName = it },
                        placeholder = { Text("输入新标签名称 (如: 竞赛)...", fontSize = 12.sp, color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DopaminePurple,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = {
                            if (newTagName.isNotBlank() && syncDao != null) {
                                val hex = customHexInput.ifBlank { colorPalette[selectedColorIdx] }
                                coroutineScope.launch {
                                    syncDao.insertOrUpdateTag(
                                        com.renly.rmf.data.local.entity.ScheduleTagEntity(
                                            id = java.util.UUID.randomUUID().toString(),
                                            name = newTagName.trim(),
                                            colorHex = hex,
                                            sortOrder = dbTags.size + 1,
                                            createdAt = java.time.Instant.now().toString()
                                        )
                                    )
                                    newTagName = ""
                                }
                            }
                        },
                        enabled = newTagName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = DopaminePurple),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Text("添加", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                // 调色盘选择器 (12色专属标签色盘)
                Text("选择专属标签色彩:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    colorPalette.take(6).forEachIndexed { idx, hex ->
                        val isSelected = selectedColorIdx == idx
                        val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Cyan }
                        Surface(
                            onClick = {
                                selectedColorIdx = idx
                                customHexInput = hex
                            },
                            shape = CircleShape,
                            color = color,
                            border = BorderStroke(if (isSelected) 2.5.dp else 0.dp, if (isSelected) TextPrimary else Color.Transparent),
                            modifier = Modifier.size(28.dp)
                        ) {}
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    colorPalette.drop(6).forEachIndexed { idx, hex ->
                        val actualIdx = idx + 6
                        val isSelected = selectedColorIdx == actualIdx
                        val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Cyan }
                        Surface(
                            onClick = {
                                selectedColorIdx = actualIdx
                                customHexInput = hex
                            },
                            shape = CircleShape,
                            color = color,
                            border = BorderStroke(if (isSelected) 2.5.dp else 0.dp, if (isSelected) TextPrimary else Color.Transparent),
                            modifier = Modifier.size(28.dp)
                        ) {}
                    }
                }

                // RGB 实时选色微调器 (新建标签)
                ExpandableRgbColorPicker(
                    colorHex = customHexInput.ifBlank { colorPalette[selectedColorIdx] },
                    onColorChange = { newHex ->
                        customHexInput = newHex
                    },
                    title = "🎨 RGB 实时微调新标签色",
                    defaultExpanded = false
                )

                HorizontalDivider(color = DarkBorder, thickness = 1.dp)

                Text("已有标签库列表 (${dbTags.size}项):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                if (dbTags.isEmpty()) {
                    Text("暂无自定义标签，可输入名称创建新标签。", fontSize = 11.5.sp, color = TextMuted)
                }

                var editingTagId by remember { mutableStateOf<String?>(null) }

                dbTags.forEach { tag ->
                    val color = try { Color(android.graphics.Color.parseColor(tag.colorHex)) } catch (_: Exception) { Color.Cyan }
                    val isEditingThis = editingTagId == tag.id
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, if (isEditingThis) color.copy(alpha = 0.8f) else DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = color.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                        Text(tag.name, fontSize = 11.5.sp, color = color, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(tag.colorHex, fontSize = 11.sp, color = TextMuted)
                                Spacer(modifier = Modifier.weight(1f))

                                // 快捷换色调色小圆点
                                colorPalette.take(4).forEach { palHex ->
                                    val pColor = try { Color(android.graphics.Color.parseColor(palHex)) } catch (_: Exception) { Color.Cyan }
                                    Surface(
                                        onClick = {
                                            coroutineScope.launch {
                                                syncDao?.insertOrUpdateTag(tag.copy(colorHex = palHex))
                                            }
                                        },
                                        shape = CircleShape,
                                        color = pColor,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .padding(horizontal = 1.dp)
                                    ) {}
                                    Spacer(modifier = Modifier.width(3.dp))
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                IconButton(
                                    onClick = { editingTagId = if (isEditingThis) null else tag.id },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Palette,
                                        contentDescription = "RGB调色",
                                        tint = if (isEditingThis) color else TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(2.dp))

                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            syncDao?.deleteTag(tag.id)
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "删除",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // 展开为该标签专属 RGB 选色器
                            if (isEditingThis) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("调整「${tag.name}」的 RGB 专属色彩:", fontSize = 11.sp, color = TextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                RgbColorPicker(
                                    colorHex = tag.colorHex,
                                    onColorChange = { newHex ->
                                        coroutineScope.launch {
                                            syncDao?.insertOrUpdateTag(tag.copy(colorHex = newHex))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成", color = DopaminePurple, fontWeight = FontWeight.Bold) }
        }
    )
}

