package com.renly.rmf.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
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
    var auditReport by remember { mutableStateOf<AuditReport?>(null) }
    var aiReviewText by remember { mutableStateOf<String?>(null) }
    var isGeneratingAi by remember { mutableStateOf(false) }
    var showTagModal by remember { mutableStateOf(false) }
    var showAiConfigDialog by remember { mutableStateOf(false) }
    var showGoogleSettingsDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showUserAgreementDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

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

        Spacer(modifier = Modifier.height(8.dp))

        SettingsSectionHeader(
            icon = "🎨",
            title = "个性化与色彩外观",
            subtitle = "主题模式、强调色、自定义背景底色与标签色彩管理"
        )

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

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // 🏷️ 日程分类标签库管理入口
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🏷️ 日程分类标签库与色彩",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "自定义时间块的业务标签与专属色彩，双端实时同步",
                                fontSize = 11.5.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Button(
                            onClick = { showTagModal = true },
                            colors = ButtonDefaults.buttonColors(containerColor = DopaminePurple),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("管理标签库", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        SettingsSectionHeader(
            icon = "🧭",
            title = "界面布局与导航偏好",
            subtitle = "底部导航栏常驻开关、默认首选主页与快捷功能模块定制"
        )

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

        Spacer(modifier = Modifier.height(10.dp))

        SettingsSectionHeader(
            icon = "🚀",
            title = "工作台功能直达",
            subtitle = "目标金字塔、日常记账、每日健身、复盘统计与使用教程"
        )

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

        // ---------------------------------------------------------------------------------
        // ⚡ 智能互联与云端服务 (AI、Google、系统日历、通知)
        // ---------------------------------------------------------------------------------
        Spacer(modifier = Modifier.height(10.dp))

        SettingsSectionHeader(
            icon = "⚡",
            title = "智能互联与云端服务",
            subtitle = "AI 智能助理接入、Google 账号与云端同步、系统日历与流体云通知"
        )

        // 🤖 AI 接口与大模型配置 (独立入口项，符合应用商店审核合规规范)
        val aiContext = androidx.compose.ui.platform.LocalContext.current

        Surface(
            onClick = { showAiConfigDialog = true },
            shape = RoundedCornerShape(16.dp),
            color = DarkCard,
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DopaminePurple.copy(alpha = 0.16f),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🤖", fontSize = 22.sp)
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "AI 接口与智能助理配置",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val isConfigured = com.renly.rmf.domain.service.AiPreferences.isConfigured(aiContext)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isConfigured) DopamineGreen.copy(alpha = 0.15f) else DarkSurface,
                            border = BorderStroke(
                                1.dp,
                                if (isConfigured) DopamineGreen.copy(alpha = 0.5f) else DarkBorder
                            )
                        ) {
                            Text(
                                text = if (isConfigured) "已接入" else "未配置",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isConfigured) DopamineGreen else TextMuted,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "自定义第三方大模型 (通义千问/DeepSeek/Gemini/自建中转) 与连通测试",
                        fontSize = 11.5.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "进入配置",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 🌐 Google 服务与多端互通 (独立入口项，整合 Google Drive 云备份与 Google Calendar 日历互通)
        val googleContext = androidx.compose.ui.platform.LocalContext.current
        val isGoogleAuthorized = remember(showGoogleSettingsDialog) {
            GoogleDrivePreferences.isAuthorized(googleContext)
        }

        Surface(
            onClick = { showGoogleSettingsDialog = true },
            shape = RoundedCornerShape(16.dp),
            color = DarkCard,
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DopamineBlue.copy(alpha = 0.16f),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🌐", fontSize = 22.sp)
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Google 服务与多端互通",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isGoogleAuthorized) DopamineGreen.copy(alpha = 0.15f) else DarkSurface,
                            border = BorderStroke(
                                1.dp,
                                if (isGoogleAuthorized) DopamineGreen.copy(alpha = 0.5f) else DarkBorder
                            )
                        ) {
                            Text(
                                text = if (isGoogleAuthorized) "已授权" else "未连接",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isGoogleAuthorized) DopamineGreen else TextMuted,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Google Drive 数据库跨端云备份与 Google Calendar 日历双向互通",
                        fontSize = 11.5.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "进入配置",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )

                // 权限诊断状态卡
                val hasNotifPerm = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        notifContext,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else true

                val alarmManager = notifContext.getSystemService(android.content.Context.ALARM_SERVICE) as? android.app.AlarmManager
                val canExactAlarm = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    alarmManager?.canScheduleExactAlarms() ?: true
                } else true

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (hasNotifPerm) "✅" else "⚠️", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (hasNotifPerm) "通知权限已开启" else "通知权限未开启",
                                fontSize = 11.sp,
                                color = if (hasNotifPerm) DopamineGreen else DopamineAmber,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (canExactAlarm) "⚡" else "⏳", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (canExactAlarm) "精确闹钟已就绪" else "精确闹钟受限",
                                fontSize = 11.sp,
                                color = if (canExactAlarm) DopamineCyan else DopamineAmber,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // 快捷直达系统通知设置 (引导用户手动开启国产系统默认关闭的横幅通知与锁屏通知)
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, notifContext.packageName)
                                }
                            } else {
                                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.fromParts("package", notifContext.packageName, null)
                                }
                            }
                            notifContext.startActivity(intent)
                            notifFeedbackText = "💡 请在系统设置中勾选【允许横幅通知】与【锁屏通知】，确保强提醒弹窗展示"
                        } catch (_: Exception) {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.fromParts("package", notifContext.packageName, null)
                            }
                            notifContext.startActivity(intent)
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DopaminePurple),
                    border = BorderStroke(1.dp, DopaminePurple.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("⚙️ 跳转系统设置开启【横幅悬浮窗与声音震动】", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

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
                        notifFeedbackText = "✓ 已发送高优先级标准日程提醒通知！横幅与声音震动已就绪"
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
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                    notifContext,
                                    android.Manifest.permission.POST_NOTIFICATIONS
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (!hasPermission) {
                                    notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    return@OutlinedButton
                                }
                            }
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
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                    notifContext,
                                    android.Manifest.permission.POST_NOTIFICATIONS
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (!hasPermission) {
                                    notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    return@OutlinedButton
                                }
                            }
                            ScheduleReminderManager.sendFluidCloudTestReminder(notifContext, isCourse = true)
                            notifFeedbackText = "✓ 已触发课表专属流体云胶囊通知卡片！"
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🎓 模拟上课流体云", fontSize = 11.5.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                    notifContext,
                                    android.Manifest.permission.POST_NOTIFICATIONS
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (!hasPermission) {
                                    notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    return@OutlinedButton
                                }
                            }
                            ScheduleReminderManager.sendFluidCloudTestReminder(notifContext, isCourse = false)
                            notifFeedbackText = "✓ 已触发日程专属流体云胶囊通知卡片！"
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("📌 模拟日程流体云", fontSize = 11.5.sp)
                    }
                }

                if (notifFeedbackText != null) {
                    Text(
                        text = notifFeedbackText!!,
                        fontSize = 11.5.sp,
                        color = if (notifFeedbackText!!.startsWith("✓")) DopamineGreen else if (notifFeedbackText!!.startsWith("💡")) DopaminePurple else DopamineOrange,
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

        Spacer(modifier = Modifier.height(10.dp))

        // ---------------------------------------------------------------------------------
        // 🩺 节律健康与数据审计 (Hard conflict, overload, rhythm audit)
        // ---------------------------------------------------------------------------------
        SettingsSectionHeader(
            icon = "🩺",
            title = "节律健康与数据审计",
            subtitle = "深度诊断时间排期硬冲突、连续4小时超载疲劳断崖与认知负荷均衡度"
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🩺 日程节律健康审计",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "深度诊断时间硬冲突排期、连续4小时超载疲劳断崖与认知负荷均衡度。",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                Button(
                    onClick = { auditReport = onTriggerAudit() },
                    colors = ButtonDefaults.buttonColors(containerColor = DopamineCyan),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("立即执行节律诊断", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ---------------------------------------------------------------------------------
        // 🛡️ 数据安全与备份防护 (Lossless database protection, export, import)
        // ---------------------------------------------------------------------------------
        SettingsSectionHeader(
            icon = "🛡️",
            title = "数据安全与备份防护",
            subtitle = "底层无损数据库架构保护、全量数据导出与数据还原"
        )

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
                    text = com.renly.rmf.domain.service.LosslessUpgradeManager.getLastBackupTime(context),
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

        Spacer(modifier = Modifier.height(10.dp))

        // ---------------------------------------------------------------------------------
        // ℹ️ 软件品牌与法律合规 (Brand, License, User Agreement, Privacy Policy)
        // ---------------------------------------------------------------------------------
        SettingsSectionHeader(
            icon = "ℹ️",
            title = "软件品牌与法律合规",
            subtitle = "系统版本信息、开源版权声明、用户服务协议与隐私政策"
        )

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
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.renly.rmf.R.drawable.ic_logo),
                    contentDescription = "RMF",
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "RMF (Renly Management Platform)",
                    fontSize = 12.5.sp,
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
                text = "©Renly 2026 保留所有权利",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = "All Rights Reserved • renly20061108@gmail.com",
                fontSize = 10.5.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 《用户服务协议》与《隐私政策》常驻入口 (工信部与各大应用商店合规规范)
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { showUserAgreementDialog = true },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("《用户服务协议》", fontSize = 11.5.sp, color = DopamineBlue)
                }
                Text("•", fontSize = 11.sp, color = TextMuted)
                TextButton(
                    onClick = { showPrivacyPolicyDialog = true },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("《隐私政策》", fontSize = 11.5.sp, color = DopamineBlue)
                }
            }
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

    // 🤖 独立的 AI 接口与模型服务配置弹窗 (BYOK 模式)
    if (showAiConfigDialog) {
        AiSettingsDialog(
            onDismiss = { showAiConfigDialog = false },
            onTriggerAiReview = { callback ->
                onTriggerAiReview("") { result ->
                    callback(result)
                }
            },
            onAiReviewResult = { result ->
                aiReviewText = result
            }
        )
    }

    // 🌐 独立的 Google 服务与多端互通设置弹窗 (Google Drive & Google Calendar)
    if (showGoogleSettingsDialog) {
        GoogleSettingsDialog(
            onDismiss = { showGoogleSettingsDialog = false },
            onTriggerUpload = onTriggerUpload,
            onTriggerDownload = onTriggerDownload
        )
    }

    // AI 复盘展示弹窗
    aiReviewText?.let { review ->
        AlertDialog(
            onDismissRequest = { aiReviewText = null },
            containerColor = DarkSurface,
            title = { Text("今日 AI 智能复盘", color = TextPrimary, fontWeight = FontWeight.Bold) },
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

    // 🛡️ 隐私政策正文浏览弹窗
    if (showPrivacyPolicyDialog) {
        PrivacyDocumentViewerDialog(
            title = "隐私政策",
            content = com.renly.rmf.domain.service.PrivacyPreferences.PRIVACY_POLICY_FULL,
            onDismiss = { showPrivacyPolicyDialog = false }
        )
    }

    // 📜 用户服务协议正文浏览弹窗
    if (showUserAgreementDialog) {
        PrivacyDocumentViewerDialog(
            title = "用户服务协议",
            content = com.renly.rmf.domain.service.PrivacyPreferences.USER_AGREEMENT_FULL,
            onDismiss = { showUserAgreementDialog = false }
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

@Composable
private fun SettingsSectionHeader(
    icon: String,
    title: String,
    subtitle: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}


