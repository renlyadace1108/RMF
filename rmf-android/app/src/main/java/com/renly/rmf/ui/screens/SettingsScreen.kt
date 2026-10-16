package com.renly.rmf.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

import com.renly.rmf.domain.service.FluidCloudPreferences
import com.renly.rmf.domain.service.NavigationPreferences
import com.renly.rmf.domain.service.ReminderType

@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
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
    onTriggerAiReview: (String, (String) -> Unit) -> Unit = { _, _ -> },
    onExportData: () -> Unit = {},
    onExportLosslessDb: () -> Unit = {},
    onImportLosslessDb: () -> Unit = {},
    onClearAllBusinessData: () -> Unit = {},
    onTriggerUpload: () -> Unit = {},
    onTriggerDownload: () -> Unit = {},
    onShowGestureTutorial: () -> Unit = {}
) {
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
        // 顶部手势下拉指示条：向下拉动或点击可快速收回设置
        var topDragDelta by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 8.dp)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        topDragDelta += delta
                    },
                    onDragStopped = { velocity ->
                        val dist = topDragDelta
                        topDragDelta = 0f
                        if (dist > 20f || velocity > 100f) {
                            com.renly.rmf.util.HapticHelper.performQuadrantMoved(context)
                            onBack()
                        }
                    }
                )
                .clickable {
                    com.renly.rmf.util.HapticHelper.performLightClick(context)
                    onBack()
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.28f))
            )
        }

        // 顶部导航栏：系统设置标题与下拉收回指示器
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "系统设置",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DopamineBlue.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, DopamineBlue.copy(alpha = 0.35f)),
                modifier = Modifier.clickable {
                    com.renly.rmf.util.HapticHelper.performLightClick(context)
                    onBack()
                }
            ) {
                Text(
                    text = "底栏下拉收回 ↓",
                    fontSize = 11.5.sp,
                    color = DopamineBlue,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

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
                        text = "点击下方模块勾选或取消，随心定制底栏快捷入口",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )

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

        // 📳 操作触感反馈与物理震动设置卡片 (Tactile Haptic Feedback)
        var isHapticFeedbackEnabled by remember {
            mutableStateOf(com.renly.rmf.util.HapticHelper.isHapticEnabled(context))
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkCard,
            border = BorderStroke(1.dp, if (isHapticFeedbackEnabled) DopamineGreen.copy(alpha = 0.5f) else DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "📳 操作触感反馈 (震动)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isHapticFeedbackEnabled) DopamineGreen.copy(alpha = 0.15f) else TextMuted.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, if (isHapticFeedbackEnabled) DopamineGreen.copy(alpha = 0.4f) else DarkBorder)
                            ) {
                                Text(
                                    text = if (isHapticFeedbackEnabled) "已开启" else "已静音",
                                    fontSize = 10.5.sp,
                                    color = if (isHapticFeedbackEnabled) DopamineGreen else TextMuted,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "整个主界面左右滑屏翻页、切换模块、开关偏好与日常打卡时，提供清脆细腻的物理级马达触感回馈",
                            fontSize = 11.5.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 4.dp, end = 8.dp)
                        )
                    }

                    Switch(
                        checked = isHapticFeedbackEnabled,
                        onCheckedChange = { enabled ->
                            isHapticFeedbackEnabled = enabled
                            com.renly.rmf.util.HapticHelper.setHapticEnabled(context, enabled)
                            if (enabled) {
                                com.renly.rmf.util.HapticHelper.performSuccess(context)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = DopamineGreen,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurface
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 💡 底部栏手势教学指引卡片
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkCard,
            border = BorderStroke(1.dp, DopamineCyan.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("💡", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "底栏与切页手势教学",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "底部栏上拉呼出设置，下拉收回；主界面左右轻滑快速穿梭翻页",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    Button(
                        onClick = {
                            com.renly.rmf.util.HapticHelper.performLightClick(context)
                            onShowGestureTutorial()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DopamineCyan.copy(alpha = 0.18f)
                        ),
                        border = BorderStroke(1.dp, DopamineCyan.copy(alpha = 0.75f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "观看指引 🎬",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DopamineCyan
                        )
                    }
                }
            }
        }

        // ---------------------------------------------------------------------------------
        // ⚡ 智能互联与云端服务 (AI、Google、系统日历、通知)
        // ---------------------------------------------------------------------------------
        Spacer(modifier = Modifier.height(10.dp))

        SettingsSectionHeader(
            icon = "⚡",
            title = "数据分析与云端服务",
            subtitle = "数据分析引擎接入、云端同步、系统日历与胶囊通知"
        )

        // ⚡ 数据分析功能全局总设置 (Master Switch)
        val aiContext = androidx.compose.ui.platform.LocalContext.current
        var isAiEnabledState by remember {
            mutableStateOf(com.renly.rmf.domain.service.AiPreferences.isAiEnabled(aiContext))
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkCard,
            border = BorderStroke(1.dp, if (isAiEnabledState) DopaminePurple.copy(alpha = 0.5f) else DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // 总开关行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isAiEnabledState) DopaminePurple.copy(alpha = 0.2f) else DarkSurface,
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("⚡", fontSize = 22.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "启用智能数据分析",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isAiEnabledState) DopamineGreen.copy(alpha = 0.15f) else DarkSurface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isAiEnabledState) DopamineGreen.copy(alpha = 0.5f) else DarkBorder
                                    )
                                ) {
                                    Text(
                                        text = if (isAiEnabledState) "已启用" else "已停用",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isAiEnabledState) DopamineGreen else TextMuted,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (isAiEnabledState) "已开启每日复盘、效能洞察与日程分析支持" else "已停用外部分析接口，应用处于纯本地离线模式",
                                fontSize = 11.5.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }

                    Switch(
                        checked = isAiEnabledState,
                        onCheckedChange = { checked ->
                            isAiEnabledState = checked
                            com.renly.rmf.domain.service.AiPreferences.setAiEnabled(aiContext, checked)
                            android.widget.Toast.makeText(
                                aiContext,
                                if (checked) "✓ 已开启智能数据分析功能" else "✓ 已停用数据分析功能（纯本地离线模式）",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = DopaminePurple,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurface
                        )
                    )
                }

                // 当启用时，显示下属详细配置项
                if (isAiEnabledState) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    val isConfigured = com.renly.rmf.domain.service.AiPreferences.isConfigured(aiContext)
                    val configuredModel = com.renly.rmf.domain.service.AiPreferences.getModel(aiContext)

                    Surface(
                        onClick = { showAiConfigDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, DopaminePurple.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "🔧 自定义数据分析接口配置",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    if (isConfigured && configuredModel.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "($configuredModel)",
                                            fontSize = 11.sp,
                                            color = DopaminePurple,
                                            maxLines = 1
                                        )
                                    }
                                }
                                Text(
                                    text = if (isConfigured) "接口与模型已就绪 · 点击修改参数" else "⚠️ 尚未完成接口参数配置，点击填写",
                                    fontSize = 11.sp,
                                    color = if (isConfigured) TextSecondary else DopamineAmber,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "进入配置",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
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
        // 🔔 通知与胶囊通知实验室 (Notification & Capsule Lab)
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
                ScheduleReminderManager.sendStandardTestNotification(notifContext, ReminderType.ALL)
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
                            text = "通知与胶囊通知实验室",
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
                            text = if (isFluidRunning) "胶囊通知实时运行中" else "胶囊通知已待命",
                            color = if (isFluidRunning) DopamineGreen else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = "深度适配状态栏动态胶囊、锁屏实时活动、常驻展开卡片以及精准高优先级系统闹钟到期提醒。",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )

                // 权限与后台驻留诊断状态卡 (通知、电池白名单、精确闹钟)
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

                val isLiveSupported = com.renly.rmf.domain.service.FluidCloudHelper.isPromotedLiveUpdateSupported(notifContext)

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (hasNotifPerm) "✅" else "⚠️", fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (hasNotifPerm) "通知已开启" else "通知未开启",
                                    fontSize = 10.5.sp,
                                    color = if (hasNotifPerm) DopamineGreen else DopamineAmber,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (canExactAlarm) "⚡" else "⏳", fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (canExactAlarm) "精确闹钟就绪" else "精确闹钟受限",
                                    fontSize = 10.5.sp,
                                    color = if (canExactAlarm) DopamineCyan else DopamineAmber,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🌱", fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "0% CPU休眠",
                                    fontSize = 10.5.sp,
                                    color = DopamineGreen,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isLiveSupported) "🌊" else "📱", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when {
                                    isLiveSupported -> "原生胶囊通知 (Live Updates 借道) 已就绪"
                                    else -> "胶囊通知标准模式已就绪 (质感美化卡片模式)"
                                },
                                fontSize = 10.5.sp,
                                color = if (isLiveSupported) DopamineCyan else TextMuted,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // 系统原生超低功耗对齐唤醒架构卡片 (免电池优化、免自启动白名单)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DopamineGreen.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, DopamineGreen.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🌱", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "系统原生超低功耗唤醒架构 (0% CPU 后台冻结)",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = DopamineGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "RMF 深度兼容主流系统后台冻结机制，无需常驻后台或手动申请电池优化白名单。全部课程、日程与事件提醒均采用系统级硬件对齐闹钟 (AlarmClockInfo) 毫秒级按需唤起并借道实时活动展示，实现零持续CPU消耗与准时提醒的完美兼得。",
                            fontSize = 10.5.sp,
                            color = TextSecondary,
                            lineHeight = 14.5.sp
                        )
                    }
                }

                // 快捷直达系统通知设置 (引导用户手动开启默认关闭的横幅通知与锁屏通知)
                OutlinedButton(
                    onClick = {
                        com.renly.rmf.domain.service.BackgroundResidencyHelper.openAppNotificationSettings(notifContext)
                        notifFeedbackText = "💡 请在系统设置中勾选【允许横幅通知】与【锁屏通知】，确保强提醒弹窗展示"
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DopaminePurple),
                    border = BorderStroke(1.dp, DopaminePurple.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("🔔 跳转系统设置开启【横幅悬浮窗与声音震动】", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
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
                        ScheduleReminderManager.sendStandardTestNotification(notifContext, ReminderType.ALL)
                        notifFeedbackText = "✓ 已发送日程提醒测试通知！"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DopamineBlue),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("🔔 测试日程提醒通知", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 按钮 2: 胶囊通知实时服务启动/停止切换
                OutlinedButton(
                    onClick = {
                        if (isFluidRunning) {
                            FocusLiveService.stop(notifContext)
                            notifFeedbackText = "✓ 已停止胶囊通知前台实时服务"
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
                            FocusLiveService.start(notifContext, "胶囊通知实时测试", 25 * 60)
                            notifFeedbackText = "✓ 已启动胶囊通知！切至桌面或息屏锁屏，状态栏将呈现胶囊"
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
                        text = if (isFluidRunning) "⏹️ 停止胶囊通知实时服务 (剩余: ${fluidRemaining / 60}m${fluidRemaining % 60}s)" else "🌊 启动胶囊通知实时测试 (25分钟心流前台服务)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 按钮 3 & 4: 胶囊通知卡片效果测试
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
                            notifFeedbackText = "✓ 已触发课表专属胶囊通知卡片！"
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🎓 模拟上课胶囊通知", fontSize = 11.5.sp)
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
                            notifFeedbackText = "✓ 已触发日程专属胶囊通知卡片！"
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("📌 模拟日程胶囊通知", fontSize = 11.5.sp)
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
        // 📱 桌面小组件中心 (Desktop Widgets Management)
        // ---------------------------------------------------------------------------------
        val widgetContext = androidx.compose.ui.platform.LocalContext.current
        var widgetFeedbackText by remember { mutableStateOf<String?>(null) }

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
                            text = "桌面小组件中心",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Surface(
                        color = DopamineCyan.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, DopamineCyan)
                    ) {
                        Text(
                            text = "3 款规格已就绪",
                            color = DopamineCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = "在手机桌面长按空白处选择【添加微件 / 桌面小组件】，找到 RMF 即可添加到主屏幕。无需启动应用即可实时查看日程、课表、倒数日并一键直达。",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )

                // 3 款小组件卡片列表
                val widgetList = listOf(
                    Triple(
                        "⚡ 全景看板 (4x3 / 4x4)",
                        "聚合今日课程动态、倒数日里程碑、北极星战略目标及今日重点日程完成率，支持模块深度直达与快捷专注/新建。",
                        DopamineBlue
                    ),
                    Triple(
                        "📅 今日日程清单 (4x2)",
                        "专注时间块与待办任务列表，清晰呈现开始时间、完成状态与勾选进度，支持快捷添加新日程。",
                        DopamineGreen
                    ),
                    Triple(
                        "⏳ 专注与倒数胶囊 (2x2)",
                        "大字直观透视置顶考试/重要日子倒数天数，集成 25 分钟番茄专注一键开启与闪念速记快捷入口。",
                        DopamineAmber
                    )
                )

                widgetList.forEach { (name, desc, color) ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(3.dp),
                                    color = color,
                                    modifier = Modifier.size(6.dp)
                                ) {}
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            Text(
                                text = desc,
                                fontSize = 10.5.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 一键强制刷新全部小组件按钮
                OutlinedButton(
                    onClick = {
                        com.renly.rmf.ui.widget.RmfWidgetManager.refreshAllWidgets(widgetContext)
                        widgetFeedbackText = "✓ 桌面 3 款规格小组件数据已成功刷新！"
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DopamineCyan),
                    border = BorderStroke(1.dp, DopamineCyan),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("🔄 立即强制刷新全部桌面小组件数据", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                if (widgetFeedbackText != null) {
                    Text(
                        text = widgetFeedbackText!!,
                        fontSize = 11.5.sp,
                        color = DopamineGreen,
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
                            text = "主流手机系统日历",
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


