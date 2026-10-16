package com.renly.rmf

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import kotlinx.coroutines.Job
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.lifecycle.lifecycleScope
import com.renly.rmf.data.local.entity.ScheduleEntity
import com.renly.rmf.domain.service.DataExportService
import com.renly.rmf.domain.service.SchedulerAuditEngine
import com.renly.rmf.ui.screens.*
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Schedule : Screen("schedule", "日程", Icons.Default.Schedule)
    object Timetable : Screen("timetable", "课表", Icons.Default.CalendarMonth)
    object Study : Screen("study", "学习", Icons.Default.MenuBook)
    object Focus : Screen("focus", "专注", Icons.Default.Timer)
    object Workbench : Screen("workbench", "工作台", Icons.Default.Dashboard)
    object Settings : Screen("settings", "设置", Icons.Default.Settings)
    object Goals : Screen("goals", "目标", Icons.Default.Flag)
    object Expenses : Screen("expenses", "记账", Icons.Default.AccountBalanceWallet)
    object DailyReport : Screen("daily_report", "日报", Icons.Default.AutoAwesome)
    object Summary : Screen("summary", "统计", Icons.Default.Insights)
    object Tutorial : Screen("tutorial", "教程", Icons.Default.MenuBook)
    object Fitness : Screen("fitness", "健身", Icons.Default.FitnessCenter)
    object Habits : Screen("habits", "习惯", Icons.Default.SelfImprovement)
    object Countdowns : Screen("countdowns", "倒数", Icons.Default.HourglassBottom)

    companion object {
        fun fromRoute(route: String): Screen {
            return when (route) {
                "schedule" -> Schedule
                "timetable" -> Timetable
                "study" -> Study
                "focus" -> Focus
                "workbench" -> Workbench
                "fitness" -> Fitness
                "habits" -> Habits
                "countdowns" -> Countdowns
                "goals" -> Goals
                "expenses" -> Expenses
                "daily_report" -> DailyReport
                "summary" -> Summary
                "tutorial" -> Tutorial
                "settings" -> Settings
                else -> Schedule
            }
        }
    }
}

class MainActivity : ComponentActivity() {

    private val db: com.renly.rmf.data.local.RmfDatabase
        get() = (application as RmfApplication).database
    private var isInPipMode by mutableStateOf(false)

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
    }

    private val exportBackupLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/x-sqlite3")
    ) { uri ->
        if (uri != null) {
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE);").close()
                    val dbFile = getDatabasePath(com.renly.rmf.data.local.RmfDatabase.DATABASE_NAME)
                    if (dbFile.exists()) {
                        contentResolver.openOutputStream(uri)?.use { output ->
                            dbFile.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "✓ 无损升级快照已成功导出！可用于跨设备同步或覆盖安装前留底", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "导出失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private val importBackupLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val result = com.renly.rmf.domain.service.LosslessUpgradeManager.restoreLosslessBackupFromUri(
                        context = this@MainActivity,
                        database = db,
                        uri = uri
                    )
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (result.isSuccess) {
                            Toast.makeText(this@MainActivity, "✓ 无损快照已完整恢复！正在重载工作台...", Toast.LENGTH_LONG).show()
                            recreate()
                        } else {
                            Toast.makeText(this@MainActivity, "✗ 还原失败: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "数据还原失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private val googleDriveSyncService by lazy {
        com.renly.rmf.domain.service.GoogleDriveSyncService(this, db)
    }

    private fun triggerDriveApiUpload() {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "正在连接 Google Drive API 并上传快照...", Toast.LENGTH_SHORT).show()
            val result = googleDriveSyncService.uploadBackupToDrive(
                onProgress = { msg ->
                    lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            )
            when (result) {
                is com.renly.rmf.domain.service.SyncResult.Success -> {
                    Toast.makeText(this@MainActivity, "✓ ${result.message}", Toast.LENGTH_LONG).show()
                }
                is com.renly.rmf.domain.service.SyncResult.Error -> {
                    Toast.makeText(this@MainActivity, "✗ ${result.error}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun triggerDriveApiDownload() {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "正在连接 Google Drive API 并拉取云端快照...", Toast.LENGTH_SHORT).show()
            val result = googleDriveSyncService.downloadBackupFromDrive(
                onProgress = { msg ->
                    lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            )
            when (result) {
                is com.renly.rmf.domain.service.SyncResult.Success -> {
                    Toast.makeText(this@MainActivity, "✓ ${result.message}", Toast.LENGTH_LONG).show()
                    recreate()
                }
                is com.renly.rmf.domain.service.SyncResult.Error -> {
                    Toast.makeText(this@MainActivity, "✗ ${result.error}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun triggerCloudBackup() {
        if (com.renly.rmf.domain.service.GoogleDrivePreferences.isAuthorized(this)) {
            triggerDriveApiUpload()
            return
        }
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE);").close()
                val dbFile = getDatabasePath(com.renly.rmf.data.local.RmfDatabase.DATABASE_NAME)
                val exportFile = java.io.File(cacheDir, "rmf_cloud_backup.db")
                dbFile.copyTo(exportFile, overwrite = true)

                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                    this@MainActivity,
                    "${packageName}.fileprovider",
                    exportFile
                )
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/x-sqlite3"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(sendIntent, "保存至 Google Drive 或云端网盘")
                startActivity(chooser)
            } catch (e: Exception) {
                exportBackupLauncher.launch("rmf_cloud_backup.db")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            isInPipMode = isInPictureInPictureMode
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "✓ 日程与胶囊通知提醒权限已成功开启", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private var pendingTargetRoute by mutableStateOf<String?>(null)
    private var pendingTargetAction by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        val route = intent?.getStringExtra(com.renly.rmf.ui.widget.RmfWidgetManager.EXTRA_TARGET_ROUTE)
        val action = intent?.getStringExtra(com.renly.rmf.ui.widget.RmfWidgetManager.EXTRA_TARGET_ACTION)
        if (!route.isNullOrBlank()) {
            pendingTargetRoute = route
        }
        if (!action.isNullOrBlank()) {
            pendingTargetAction = action
        }
    }

    override fun onStop() {
        super.onStop()
        // 用户离开 App 切到桌面或锁屏时，自动刷新所有桌面小组件，确保桌面数据永远实时更新
        com.renly.rmf.ui.widget.RmfWidgetManager.refreshAllWidgets(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleWidgetIntent(intent)

        // 异步后台自动检查并排期未来课程的胶囊通知上课提醒与全学期课表日程实时映射 (遵循工信部合规规范，仅在用户同意隐私政策后启动)
        if (com.renly.rmf.domain.service.PrivacyPreferences.isPrivacyAccepted(this)) {
            checkAndRequestNotificationPermission()
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                if (com.renly.rmf.domain.service.TimetablePreferences.isSemesterConfigured(this@MainActivity)) {
                    com.renly.rmf.domain.service.TimetableProjectionService.syncAllToSchedule(
                        context = this@MainActivity,
                        courseDao = db.courseDao(),
                        scheduleDao = db.scheduleDao()
                    )
                }
                com.renly.rmf.domain.service.CourseReminderScheduler.scheduleUpcomingCourseReminders(
                    context = this@MainActivity,
                    courseDao = db.courseDao(),
                    daysAhead = 7
                )
            }
        }

        setContent {
            var currentThemeMode by remember {
                mutableStateOf(com.renly.rmf.ui.theme.ThemePreferences.getThemeMode(this@MainActivity))
            }
            var currentAccentColorHex by remember {
                mutableStateOf(com.renly.rmf.ui.theme.ThemePreferences.getAccentColorHex(this@MainActivity))
            }
            var currentCustomDarkBgHex by remember {
                mutableStateOf(com.renly.rmf.ui.theme.ThemePreferences.getDarkBgColorHex(this@MainActivity))
            }
            var currentCustomLightBgHex by remember {
                mutableStateOf(com.renly.rmf.ui.theme.ThemePreferences.getLightBgColorHex(this@MainActivity))
            }

            RMFTheme(
                themeMode = currentThemeMode,
                accentColorHex = currentAccentColorHex,
                darkBgColorHex = currentCustomDarkBgHex,
                lightBgColorHex = currentCustomLightBgHex
            ) {
                var showSplashScreen by remember { mutableStateOf(true) }
                var showSettingsGestureTutorial by remember { mutableStateOf(false) }
                val isUpgradeInstall = remember {
                    com.renly.rmf.domain.service.PrivacyPreferences.isOverwriteOrUpgradeInstall(this@MainActivity)
                }
                var showPrivacyDialog by remember {
                    mutableStateOf(!com.renly.rmf.domain.service.PrivacyPreferences.isPrivacyAccepted(this@MainActivity))
                }

                if (showPrivacyDialog) {
                    com.renly.rmf.ui.screens.PrivacyComplianceDialog(
                        isUpgradeInstall = isUpgradeInstall,
                        onAccept = {
                            com.renly.rmf.domain.service.PrivacyPreferences.setPrivacyAccepted(this@MainActivity, true)
                            showPrivacyDialog = false
                            // 若首次安装未完成过手势教学则展示教学；若为覆盖升级且已完成过则不再重复打扰
                            showSettingsGestureTutorial = !com.renly.rmf.domain.service.SettingsTutorialPreferences.isSettingsGestureTutorialCompleted(this@MainActivity)
                            showSplashScreen = false
                            if (isUpgradeInstall) {
                                Toast.makeText(this@MainActivity, "✓ 隐私协议已确认，旧版本数据已完整无损载入", Toast.LENGTH_SHORT).show()
                            }
                            checkAndRequestNotificationPermission()
                            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                if (com.renly.rmf.domain.service.TimetablePreferences.isSemesterConfigured(this@MainActivity)) {
                                    com.renly.rmf.domain.service.TimetableProjectionService.syncAllToSchedule(
                                        context = this@MainActivity,
                                        courseDao = db.courseDao(),
                                        scheduleDao = db.scheduleDao()
                                    )
                                }
                                com.renly.rmf.domain.service.CourseReminderScheduler.scheduleUpcomingCourseReminders(
                                    context = this@MainActivity,
                                    courseDao = db.courseDao(),
                                    daysAhead = 7
                                )
                            }
                        },
                        onDecline = {
                            finishAffinity()
                        }
                    )
                }

                var isBottomBarVisible by remember {
                    mutableStateOf(com.renly.rmf.domain.service.NavigationPreferences.isBottomBarVisible(this@MainActivity))
                }
                var enabledNavRoutes by remember {
                    mutableStateOf(com.renly.rmf.domain.service.NavigationPreferences.getEnabledRoutes(this@MainActivity))
                }
                var defaultHomeRoute by remember {
                    mutableStateOf(com.renly.rmf.domain.service.NavigationPreferences.getDefaultHomeRoute(this@MainActivity))
                }
                var currentScreen by remember {
                    mutableStateOf<Screen>(Screen.fromRoute(com.renly.rmf.domain.service.NavigationPreferences.getDefaultHomeRoute(this@MainActivity)))
                }
                var previousScreen by remember { mutableStateOf<Screen>(currentScreen) }
                LaunchedEffect(currentScreen) {
                    if (previousScreen != currentScreen) {
                        kotlinx.coroutines.delay(300)
                        previousScreen = currentScreen
                    }
                }
                var slideDirection by remember { mutableIntStateOf(1) } // 1: forward (right to left), -1: backward (left to right)
                var lastScreenBeforeSettings by remember { mutableStateOf<Screen>(currentScreen) }
                var showFloatingNavMenu by remember { mutableStateOf(false) }

                val density = LocalDensity.current
                val haptic = LocalHapticFeedback.current
                val coroutineScope = rememberCoroutineScope()

                val navScreens = enabledNavRoutes.map { Screen.fromRoute(it) }
                val safeNavScreens = if (navScreens.isEmpty()) listOf(Screen.Schedule, Screen.Workbench) else navScreens

                val navigateToScreen: (Screen, Int?) -> Unit = { targetScreen, explicitDir ->
                    if (targetScreen != currentScreen) {
                        val prevIdx = safeNavScreens.indexOf(currentScreen)
                        val nextIdx = safeNavScreens.indexOf(targetScreen)
                        slideDirection = explicitDir ?: if (nextIdx != -1 && prevIdx != -1) {
                            if (nextIdx >= prevIdx) 1 else -1
                        } else if (targetScreen == Screen.Settings) {
                            1
                        } else if (currentScreen == Screen.Settings) {
                            -1
                        } else 1
                        previousScreen = currentScreen
                        if (currentScreen != Screen.Settings) {
                            lastScreenBeforeSettings = currentScreen
                        }
                        currentScreen = targetScreen
                    }
                }

                var visitedScreens by remember { mutableStateOf(setOf<Screen>(currentScreen)) }
                val saveableStateHolder = rememberSaveableStateHolder()

                var dragOffsetX by remember { mutableFloatStateOf(0f) }
                var isHandDragging by remember { mutableStateOf(false) }
                var isDragSettling by remember { mutableStateOf(false) }
                var activeAdjacentScreen by remember { mutableStateOf<Screen?>(null) }
                var skipTransitionAnim by remember { mutableStateOf(false) }
                var settleJob by remember { mutableStateOf<Job?>(null) }

                val curNavIdx = safeNavScreens.indexOf(currentScreen)
                val getAdjacentScreen: (Int) -> Screen? = { dir ->
                    when (dir) {
                        -1 -> if (curNavIdx != -1 && curNavIdx < safeNavScreens.size - 1) {
                            safeNavScreens[curNavIdx + 1]
                        } else {
                            null
                        }
                        1 -> if (currentScreen == Screen.Settings) {
                            lastScreenBeforeSettings
                        } else if (curNavIdx > 0) {
                            safeNavScreens[curNavIdx - 1]
                        } else {
                            null
                        }
                        else -> null
                    }
                }

                LaunchedEffect(activeAdjacentScreen) {
                    val adj = activeAdjacentScreen
                    if (adj != null && !visitedScreens.contains(adj)) {
                        visitedScreens = visitedScreens + adj
                    }
                }

                val renderScreens = remember(visitedScreens, activeAdjacentScreen) {
                    if (activeAdjacentScreen != null) (visitedScreens + activeAdjacentScreen!!).toList() else visitedScreens.toList()
                }

                LaunchedEffect(currentScreen) {
                    if (currentScreen != Screen.Settings) {
                        lastScreenBeforeSettings = currentScreen
                    }
                    if (!visitedScreens.contains(currentScreen)) {
                        visitedScreens = visitedScreens + currentScreen
                    }
                }

                var showQuickCaptureDialog by remember { mutableStateOf(false) }
                var dailyReportDate by remember { mutableStateOf(java.time.LocalDate.now()) }

                LaunchedEffect(pendingTargetRoute) {
                    pendingTargetRoute?.let { route ->
                        navigateToScreen(Screen.fromRoute(route), null)
                        showSplashScreen = false
                        pendingTargetRoute = null
                    }
                }

                LaunchedEffect(pendingTargetAction) {
                    pendingTargetAction?.let { act ->
                        if (act == "quick_capture") {
                            showQuickCaptureDialog = true
                            showSplashScreen = false
                        }
                        pendingTargetAction = null
                    }
                }

                Crossfade(
                    targetState = showSplashScreen,
                    animationSpec = tween(durationMillis = 350),
                    label = "AppSplashCrossfade"
                ) { isSplash ->
                    if (isSplash) {
                        AppSplashScreen(
                            onFinished = { showSplashScreen = false }
                        )
                    } else {
                        Scaffold(
                    containerColor = DarkBg,
                    bottomBar = {
                        if (!isInPipMode && isBottomBarVisible) {
                            var bottomBarDragDelta by remember { mutableFloatStateOf(0f) }

                            Surface(
                                color = DarkSurface,
                                border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.75f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .draggable(
                                        orientation = Orientation.Horizontal,
                                        state = rememberDraggableState { delta ->
                                            bottomBarDragDelta += delta
                                        },
                                        onDragStopped = { velocity ->
                                            val dist = bottomBarDragDelta
                                            bottomBarDragDelta = 0f

                                            if (kotlin.math.abs(dist) > 28f || kotlin.math.abs(velocity) > 120f) {
                                                com.renly.rmf.domain.service.SettingsTutorialPreferences.setSettingsGestureTutorialCompleted(this@MainActivity, true)
                                                showSettingsGestureTutorial = false
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                                if (currentScreen == Screen.Settings) {
                                                    val dir = if (dist > 0 || velocity > 0) -1 else 1
                                                    navigateToScreen(lastScreenBeforeSettings, dir)
                                                } else {
                                                    val dir = if (dist < 0 || velocity < 0) 1 else -1
                                                    navigateToScreen(Screen.Settings, dir)
                                                }
                                            }
                                        }
                                    )
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // 顶部微光手势质感指示条 (Subtle gesture pill indicator)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(28.dp)
                                                .height(2.5.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(Color.White.copy(alpha = 0.18f))
                                        )
                                    }

                                    NavigationBar(
                                        containerColor = Color.Transparent,
                                        tonalElevation = 0.dp
                                    ) {
                                        safeNavScreens.forEach { screen ->
                                            val isSelected = currentScreen == screen
                                            val iconScale by animateFloatAsState(
                                                targetValue = if (isSelected) 1.10f else 1.0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessLow
                                                ),
                                                label = "navIconScale_${screen.route}"
                                            )
                                            NavigationBarItem(
                                                selected = isSelected,
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    navigateToScreen(screen, null)
                                                },
                                                icon = {
                                                    Icon(
                                                        screen.icon,
                                                        contentDescription = screen.title,
                                                        modifier = Modifier.graphicsLayer {
                                                            scaleX = iconScale
                                                            scaleY = iconScale
                                                        }
                                                    )
                                                },
                                                label = {
                                                    Text(
                                                        screen.title,
                                                        fontSize = if (safeNavScreens.size > 5) 10.sp else 11.5.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                colors = NavigationBarItemDefaults.colors(
                                                    selectedIconColor = DopamineBlue,
                                                    selectedTextColor = DopamineBlue,
                                                    unselectedIconColor = TextMuted,
                                                    unselectedTextColor = TextMuted,
                                                    indicatorColor = DopamineBlue.copy(alpha = 0.16f)
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(if (isInPipMode) androidx.compose.foundation.layout.PaddingValues(0.dp) else innerPadding),
                            color = DarkBg
                        ) {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(safeNavScreens, currentScreen) {
                                        val touchSlop = viewConfiguration.touchSlop
                                        val swipeThreshold = with(density) { 46.dp.toPx() }
                                        val screenPxWidth = size.width.toFloat().coerceAtLeast(400f)

                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            settleJob?.cancel()
                                            var totalX = 0f
                                            var totalY = 0f
                                            var isHorizontalSwipe: Boolean? = null
                                            val velocityTracker = VelocityTracker()
                                            velocityTracker.addPosition(down.uptimeMillis, down.position)

                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                                if (event.changes.count { it.pressed } >= 2) {
                                                    isHorizontalSwipe = false
                                                    if (isHandDragging) {
                                                        isHandDragging = false
                                                        coroutineScope.launch {
                                                            androidx.compose.animation.core.animate(
                                                                initialValue = dragOffsetX,
                                                                targetValue = 0f,
                                                                animationSpec = androidx.compose.animation.core.tween(200)
                                                            ) { v, _ -> dragOffsetX = v }
                                                            activeAdjacentScreen = null
                                                        }
                                                    }
                                                    break
                                                }

                                                if (!change.pressed) {
                                                    if (isHorizontalSwipe == true) {
                                                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                                                        val velocity = velocityTracker.calculateVelocity().x
                                                        val curOffset = dragOffsetX
                                                        val absOffset = kotlin.math.abs(curOffset)
                                                        val passedDistance = absOffset > (screenPxWidth * 0.16f) || absOffset > swipeThreshold
                                                        val passedVelocity = kotlin.math.abs(velocity) > 360f
                                                        val directionAgrees = (curOffset < 0 && velocity <= 100f) || (curOffset > 0 && velocity >= -100f)
                                                        val targetDir = if (curOffset < 0) -1 else 1
                                                        val commitTarget = getAdjacentScreen(targetDir)
                                                        val shouldCommit = commitTarget != null && (passedDistance || passedVelocity) && directionAgrees

                                                        if (shouldCommit && commitTarget != null) {
                                                            val targetX = if (curOffset < 0) -screenPxWidth else screenPxWidth
                                                            val dir = if (curOffset < 0) 1 else -1
                                                            isDragSettling = true
                                                            isHandDragging = false
                                                            settleJob?.cancel()
                                                            settleJob = coroutineScope.launch {
                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                androidx.compose.animation.core.animate(
                                                                    initialValue = curOffset,
                                                                    targetValue = targetX,
                                                                    animationSpec = androidx.compose.animation.core.tween(
                                                                        durationMillis = 240,
                                                                        easing = FastOutSlowInEasing
                                                                    )
                                                                ) { v, _ -> dragOffsetX = v }
                                                                skipTransitionAnim = true
                                                                navigateToScreen(commitTarget, dir)
                                                                dragOffsetX = 0f
                                                                activeAdjacentScreen = null
                                                                isDragSettling = false
                                                                kotlinx.coroutines.delay(60)
                                                                skipTransitionAnim = false
                                                            }
                                                        } else {
                                                            isDragSettling = true
                                                            isHandDragging = false
                                                            settleJob?.cancel()
                                                            settleJob = coroutineScope.launch {
                                                                androidx.compose.animation.core.animate(
                                                                    initialValue = curOffset,
                                                                    targetValue = 0f,
                                                                    animationSpec = androidx.compose.animation.core.tween(
                                                                        durationMillis = 200,
                                                                        easing = FastOutSlowInEasing
                                                                    )
                                                                ) { v, _ -> dragOffsetX = v }
                                                                activeAdjacentScreen = null
                                                                isDragSettling = false
                                                            }
                                                        }
                                                    } else {
                                                        isHandDragging = false
                                                        activeAdjacentScreen = null
                                                    }
                                                    break
                                                }

                                                val pos = change.position
                                                val prevPos = change.previousPosition
                                                val dx = pos.x - prevPos.x
                                                val dy = pos.y - prevPos.y
                                                totalX += dx
                                                totalY += dy
                                                velocityTracker.addPosition(change.uptimeMillis, pos)

                                                if (isHorizontalSwipe == null) {
                                                    val absX = kotlin.math.abs(totalX)
                                                    val absY = kotlin.math.abs(totalY)
                                                    if (absX > touchSlop || absY > touchSlop) {
                                                        if (absX > absY * 1.35f && absX > touchSlop) {
                                                            isHorizontalSwipe = true
                                                        } else {
                                                            isHorizontalSwipe = false
                                                        }
                                                    }
                                                }

                                                if (isHorizontalSwipe == true) {
                                                    change.consume()
                                                    isHandDragging = true
                                                    settleJob?.cancel()
                                                    val newDir = if (totalX < 0f) -1 else if (totalX > 0f) 1 else 0
                                                    val adjacent = getAdjacentScreen(newDir)
                                                    if (activeAdjacentScreen != adjacent) {
                                                        activeAdjacentScreen = adjacent
                                                    }
                                                    val isOverBoundary = (totalX > 0 && getAdjacentScreen(1) == null) ||
                                                                         (totalX < 0 && getAdjacentScreen(-1) == null)
                                                    val effectiveX = if (isOverBoundary) totalX * 0.22f else totalX
                                                    dragOffsetX = effectiveX
                                                }
                                            }
                                        }
                                    }
                            ) {
                                val screenWidth = constraints.maxWidth.toFloat().coerceAtLeast(400f)

                                renderScreens.forEach { screen ->
                                    val isActive = (currentScreen == screen)
                                    val isAdjacent = (activeAdjacentScreen == screen && (isHandDragging || isDragSettling))
                                    val isPrev = (previousScreen == screen && previousScreen != currentScreen)

                                    key(screen.route) {
                                        saveableStateHolder.SaveableStateProvider(screen.route) {
                                            ScreenLayerSlot(
                                                screen = screen,
                                                isActive = isActive,
                                                isAdjacent = isAdjacent,
                                                isPrev = isPrev,
                                                isHandDragging = isHandDragging,
                                                isDragSettling = isDragSettling,
                                                slideDirection = slideDirection,
                                                screenWidth = screenWidth,
                                                dragOffsetProvider = { dragOffsetX },
                                                skipTransitionAnim = skipTransitionAnim
                                            ) {
                                                when (screen) {
                            Screen.Schedule -> ScheduleScreen(
                                schedulesFlow = db.scheduleDao().getAllActiveSchedules(),
                                tagsFlow = db.syncDao().getAllTags(),
                                onToggleStatus = { schedule ->
                                    lifecycleScope.launch {
                                        val newStatus = if (schedule.status == "COMPLETED") "PENDING" else "COMPLETED"
                                        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                                        db.scheduleDao().updateStatus(schedule.id, newStatus, nowStr)
                                        if (newStatus == "COMPLETED") {
                                            com.renly.rmf.domain.service.ScheduleReminderManager.cancelReminder(this@MainActivity, schedule.id)
                                        } else if (schedule.reminderMinutes >= 0 && schedule.startTime.isNotBlank()) {
                                            val rType = com.renly.rmf.domain.service.ReminderType.fromCode(schedule.reminderType)
                                            com.renly.rmf.domain.service.ScheduleReminderManager.scheduleReminder(
                                                context = this@MainActivity,
                                                scheduleId = schedule.id,
                                                title = schedule.title,
                                                startTimeStr = schedule.startTime,
                                                advanceMinutes = schedule.reminderMinutes,
                                                reminderType = rType
                                            )
                                        }
                                    }
                                },
                                onAddSchedule = { title, workType, category, mins, startStr, endStr, reminderMinutes, reminderType ->
                                    lifecycleScope.launch {
                                        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                                        val actualType = com.renly.rmf.domain.service.ReminderType.fromCode(reminderType)
                                        val entity = ScheduleEntity(
                                            title = title,
                                            workType = workType,
                                            category = category,
                                            estimatedMinutes = mins,
                                            startTime = startStr,
                                            endTime = endStr,
                                            reminderMinutes = reminderMinutes,
                                            reminderType = actualType.code,
                                            createdAt = nowStr,
                                            updatedAt = nowStr
                                        )
                                        db.scheduleDao().insertOrUpdate(entity)
                                        if (startStr.isNotBlank() && reminderMinutes >= 0) {
                                            com.renly.rmf.domain.service.ScheduleReminderManager.scheduleReminder(
                                                context = this@MainActivity,
                                                scheduleId = entity.id,
                                                title = entity.title,
                                                startTimeStr = entity.startTime,
                                                advanceMinutes = reminderMinutes,
                                                reminderType = actualType
                                            )
                                        }
                                        if (com.renly.rmf.domain.service.SystemCalendarSyncService.isAutoSyncEnabled(this@MainActivity)) {
                                            com.renly.rmf.domain.service.SystemCalendarSyncService.syncSingleSchedule(this@MainActivity, entity)
                                        }
                                    }
                                },
                                onUpdateSchedule = { schedule ->
                                    lifecycleScope.launch {
                                        db.scheduleDao().insertOrUpdate(schedule)
                                        if (schedule.startTime.isNotBlank()) {
                                            if (schedule.reminderMinutes >= 0 && schedule.status != "COMPLETED" && schedule.status != "ABANDONED") {
                                                val rType = com.renly.rmf.domain.service.ReminderType.fromCode(schedule.reminderType)
                                                com.renly.rmf.domain.service.ScheduleReminderManager.scheduleReminder(
                                                    context = this@MainActivity,
                                                    scheduleId = schedule.id,
                                                    title = schedule.title,
                                                    startTimeStr = schedule.startTime,
                                                    advanceMinutes = schedule.reminderMinutes,
                                                    reminderType = rType
                                                )
                                            } else {
                                                com.renly.rmf.domain.service.ScheduleReminderManager.cancelReminder(
                                                    context = this@MainActivity,
                                                    scheduleId = schedule.id
                                                )
                                            }
                                        }
                                        if (com.renly.rmf.domain.service.SystemCalendarSyncService.isAutoSyncEnabled(this@MainActivity)) {
                                            com.renly.rmf.domain.service.SystemCalendarSyncService.syncSingleSchedule(this@MainActivity, schedule)
                                        }
                                    }
                                },
                                onDeleteSchedule = { id ->
                                    lifecycleScope.launch {
                                        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                                        db.scheduleDao().softDelete(id, nowStr)
                                        com.renly.rmf.domain.service.ScheduleReminderManager.cancelReminder(this@MainActivity, id)
                                        if (com.renly.rmf.domain.service.SystemCalendarSyncService.isAutoSyncEnabled(this@MainActivity)) {
                                            com.renly.rmf.domain.service.SystemCalendarSyncService.deleteScheduleEvent(this@MainActivity, id)
                                        }
                                    }
                                },
                                onOpenQuickCapture = { showQuickCaptureDialog = true },
                                onNavigateToSummary = { navigateToScreen(Screen.Summary, 1) },
                                onNavigateToFitness = { navigateToScreen(Screen.Fitness, 1) },
                                onAddTag = { tag ->
                                    lifecycleScope.launch {
                                        db.syncDao().insertOrUpdateTag(tag)
                                    }
                                },
                                onDeleteTag = { tag ->
                                    lifecycleScope.launch {
                                        db.syncDao().deleteTag(tag.id)
                                    }
                                }
                            )

                            Screen.Timetable -> TimetableScreen(
                                coursesFlow = db.courseDao().getAllActiveCourses(),
                                adjustmentsFlow = db.courseDao().getAllActiveAdjustments(),
                                onAddCourse = { course ->
                                    lifecycleScope.launch {
                                        db.courseDao().insertOrUpdateCourse(course)
                                        val syncedCount = com.renly.rmf.domain.service.TimetableProjectionService.syncAllToSchedule(
                                            context = this@MainActivity,
                                            courseDao = db.courseDao(),
                                            scheduleDao = db.scheduleDao()
                                        )
                                        com.renly.rmf.domain.service.CourseReminderScheduler.scheduleUpcomingCourseReminders(
                                            context = this@MainActivity,
                                            courseDao = db.courseDao(),
                                            daysAhead = 7
                                        )
                                        Toast.makeText(this@MainActivity, "✓ 课程已保存，全学期（共 $syncedCount 节课）已实时映射到日程！", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onDeleteCourse = { id ->
                                    lifecycleScope.launch {
                                        db.courseDao().softDeleteCourse(id, LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                                        com.renly.rmf.domain.service.TimetableProjectionService.syncAllToSchedule(
                                            context = this@MainActivity,
                                            courseDao = db.courseDao(),
                                            scheduleDao = db.scheduleDao()
                                        )
                                        com.renly.rmf.domain.service.CourseReminderScheduler.scheduleUpcomingCourseReminders(
                                            context = this@MainActivity,
                                            courseDao = db.courseDao(),
                                            daysAhead = 7
                                        )
                                        Toast.makeText(this@MainActivity, "课程已删除，日程映射已实时更新", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onAddAdjustment = { adj ->
                                    lifecycleScope.launch {
                                        db.courseDao().insertOrUpdateAdjustment(adj)
                                        com.renly.rmf.domain.service.TimetableProjectionService.syncAllToSchedule(
                                            context = this@MainActivity,
                                            courseDao = db.courseDao(),
                                            scheduleDao = db.scheduleDao()
                                        )
                                        com.renly.rmf.domain.service.CourseReminderScheduler.scheduleUpcomingCourseReminders(
                                            context = this@MainActivity,
                                            courseDao = db.courseDao(),
                                            daysAhead = 7
                                        )
                                        Toast.makeText(this@MainActivity, "✓ 已保存调停课，日程与胶囊通知提醒已实时同步更新！", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onDeleteAdjustment = { id ->
                                    lifecycleScope.launch {
                                        db.courseDao().softDeleteAdjustment(id, LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                                        com.renly.rmf.domain.service.TimetableProjectionService.syncAllToSchedule(
                                            context = this@MainActivity,
                                            courseDao = db.courseDao(),
                                            scheduleDao = db.scheduleDao()
                                        )
                                        com.renly.rmf.domain.service.CourseReminderScheduler.scheduleUpcomingCourseReminders(
                                            context = this@MainActivity,
                                            courseDao = db.courseDao(),
                                            daysAhead = 7
                                        )
                                        Toast.makeText(this@MainActivity, "已撤销调停课记录，日程映射已实时恢复", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onProjectToToday = {
                                    lifecycleScope.launch {
                                        val syncedCount = com.renly.rmf.domain.service.TimetableProjectionService.syncAllToSchedule(
                                            context = this@MainActivity,
                                            courseDao = db.courseDao(),
                                            scheduleDao = db.scheduleDao()
                                        )
                                        com.renly.rmf.domain.service.CourseReminderScheduler.scheduleUpcomingCourseReminders(
                                            context = this@MainActivity,
                                            courseDao = db.courseDao(),
                                            daysAhead = 7
                                        )
                                        val activeTerm = com.renly.rmf.domain.service.TimetablePreferences.getActiveSemester(this@MainActivity)
                                        val totalWeeks = com.renly.rmf.domain.service.TimetablePreferences.getSemesterTotalWeeks(this@MainActivity)
                                        val termLabel = if (activeTerm == 1) "第一学期" else "第二学期"
                                        Toast.makeText(
                                            this@MainActivity,
                                            "✓ $termLabel（共 $totalWeeks 周，$syncedCount 节课）已实时映射到日程！",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                onImportCourses = { courses ->
                                    lifecycleScope.launch {
                                        courses.forEach { course ->
                                            db.courseDao().insertOrUpdateCourse(course)
                                        }
                                        val syncedCount = com.renly.rmf.domain.service.TimetableProjectionService.syncAllToSchedule(
                                            context = this@MainActivity,
                                            courseDao = db.courseDao(),
                                            scheduleDao = db.scheduleDao()
                                        )
                                        com.renly.rmf.domain.service.CourseReminderScheduler.scheduleUpcomingCourseReminders(
                                            context = this@MainActivity,
                                            courseDao = db.courseDao(),
                                            daysAhead = 7
                                        )
                                        Toast.makeText(
                                            this@MainActivity,
                                            "🎉 成功导入 ${courses.size} 门课程，全学期 $syncedCount 节课已实时映射到日程！",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            )

                            Screen.Study -> StudyScreen(
                                topicsFlow = db.studyTopicDao().getAllActiveTopics(),
                                onAddTopic = { topic ->
                                    lifecycleScope.launch {
                                        db.studyTopicDao().insertOrUpdate(topic)
                                    }
                                },
                                onUpdateProgress = { topic, addedVal ->
                                    lifecycleScope.launch {
                                        val newVal = topic.completedValue + addedVal
                                        val percent = ((newVal / topic.targetValue.coerceAtLeast(1.0)) * 100).toInt().coerceIn(0, 100)
                                        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                                        db.studyTopicDao().updateProgress(topic.id, newVal, percent, nowStr)
                                    }
                                }
                            )

                            Screen.Focus -> FocusScreen(
                                isInPip = isInPipMode,
                                schedulesFlow = db.scheduleDao().getAllActiveSchedules(),
                                focusRecordsFlow = db.focusDao().getAllFocusRecords(),
                                onUpdateSchedule = { schedule ->
                                    lifecycleScope.launch {
                                        db.scheduleDao().insertOrUpdate(schedule)
                                        if (com.renly.rmf.domain.service.SystemCalendarSyncService.isAutoSyncEnabled(this@MainActivity)) {
                                            com.renly.rmf.domain.service.SystemCalendarSyncService.syncSingleSchedule(this@MainActivity, schedule)
                                        }
                                    }
                                },
                                onRecordInterruption = { interruption ->
                                    lifecycleScope.launch {
                                        db.interruptionDao().insert(interruption)
                                        Toast.makeText(this@MainActivity, "已记录本次打断", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onEnterPiP = {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        enterPictureInPictureMode(
                                            android.app.PictureInPictureParams.Builder()
                                                // 极致小巧胶囊比例 (2.39:1 接近 Android PiP 极限最大宽高比 2.39)
                                                .setAspectRatio(android.util.Rational(239, 100))
                                                .build()
                                        )
                                    } else {
                                        Toast.makeText(this@MainActivity, "设备系统版本暂不支持画中画", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            Screen.Goals -> GoalsScreen(
                                goalsFlow = db.goalDao().getAllActiveGoals(),
                                onAddGoal = { goal ->
                                    lifecycleScope.launch { db.goalDao().insertOrUpdate(goal) }
                                },
                                onUpdateProgress = { goal, newP ->
                                    lifecycleScope.launch { db.goalDao().updateProgress(goal.id, newP) }
                                },
                                onToggleNorthStar = { goal ->
                                    lifecycleScope.launch {
                                        val flipped = if (goal.isNorthStar == 1) 0 else 1
                                        db.goalDao().insertOrUpdate(goal.copy(isNorthStar = flipped))
                                    }
                                }
                            )

                            Screen.Expenses -> ExpensesScreen(
                                expensesFlow = db.expenseDao().getAllActiveExpenses(),
                                onAddExpense = { expense ->
                                    lifecycleScope.launch {
                                        db.expenseDao().insertOrUpdate(expense)
                                        Toast.makeText(this@MainActivity, "记账成功！", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onUpdateExpense = { expense ->
                                    lifecycleScope.launch {
                                        db.expenseDao().insertOrUpdate(expense)
                                        Toast.makeText(this@MainActivity, "账目已更新！", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onDeleteExpense = { id ->
                                    lifecycleScope.launch {
                                        db.expenseDao().softDelete(id)
                                        Toast.makeText(this@MainActivity, "账目已删除！", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            Screen.DailyReport -> DailyReportScreen(
                                currentDate = dailyReportDate,
                                onDateChange = { dailyReportDate = it },
                                todaySchedulesFlow = db.scheduleDao().getSchedulesByDateRange(
                                    dailyReportDate.atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                                    dailyReportDate.plusDays(1).atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                ),
                                onSaveReport = { report ->
                                    lifecycleScope.launch {
                                        db.dailyReportDao().insertOrUpdate(report)
                                        Toast.makeText(this@MainActivity, "今日复盘已妥善保存！", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            Screen.Summary -> SummaryScreen(
                                schedulesFlow = db.scheduleDao().getAllActiveSchedules(),
                                coursesFlow = db.courseDao().getAllActiveCourses(),
                                interruptionsFlow = db.interruptionDao().getAllInterruptions(),
                                expensesFlow = db.expenseDao().getAllActiveExpenses(),
                                onBack = null
                            )

                            Screen.Tutorial -> TutorialScreen(
                                onShowGestureTutorial = { showSettingsGestureTutorial = true }
                            )

                            Screen.Fitness -> FitnessScreen(
                                fitnessDao = db.fitnessDao(),
                                scheduleDao = db.scheduleDao(),
                                onBack = { navigateToScreen(Screen.Schedule, -1) },
                                onProjectToSchedule = { plan, _ ->
                                    lifecycleScope.launch {
                                        db.fitnessDao().upsertPlan(plan)
                                        com.renly.rmf.domain.fitness.FitnessScheduleSyncManager.syncFitnessPlanToSchedule(
                                            fitnessDao = db.fitnessDao(),
                                            scheduleDao = db.scheduleDao(),
                                            dateStr = plan.planDate
                                        )
                                        Toast.makeText(this@MainActivity, "🎉 已将训练「${plan.title}」同步至主页日程！", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            Screen.Habits -> HabitsScreen(
                                habitsFlow = db.habitDao().getAllActiveHabits(),
                                onAddHabit = { title, icon, targetDays, colorHex ->
                                    lifecycleScope.launch {
                                        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                                        val entity = com.renly.rmf.data.local.entity.HabitEntity(
                                            title = title,
                                            icon = icon,
                                            targetDaysPerWeek = targetDays,
                                            colorHex = colorHex,
                                            createdAt = nowStr,
                                            updatedAt = nowStr
                                        )
                                        db.habitDao().insertOrUpdate(entity)
                                        Toast.makeText(this@MainActivity, "✓ 习惯创建成功！", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onToggleCheckIn = { habit, date ->
                                    lifecycleScope.launch {
                                        val set = parseDatesList(habit.historyDatesJson).toMutableSet()
                                        val dateStr = date.toString()
                                        val wasChecked = set.contains(dateStr)
                                        if (wasChecked) {
                                            set.remove(dateStr)
                                        } else {
                                            set.add(dateStr)
                                        }
                                        val jsonStr = org.json.JSONArray(set.toList()).toString()
                                        val newStreak = if (wasChecked) maxOf(0, habit.currentStreak - 1) else (habit.currentStreak + 1)
                                        val newBest = maxOf(habit.bestStreak, newStreak)
                                        val updated = habit.copy(
                                            historyDatesJson = jsonStr,
                                            currentStreak = newStreak,
                                            bestStreak = newBest,
                                            updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                                        )
                                        db.habitDao().insertOrUpdate(updated)
                                        Toast.makeText(this@MainActivity, if (wasChecked) "已取消今日打卡" else "🔥 连续打卡 $newStreak 天达成！", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onDeleteHabit = { id ->
                                    lifecycleScope.launch {
                                        db.habitDao().softDelete(id, LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                                    }
                                }
                            )

                            Screen.Countdowns -> CountdownsScreen(
                                countdownsFlow = db.countdownDao().getAllActiveCountdowns(),
                                onAddCountdown = { title, targetDate, category, colorHex ->
                                    lifecycleScope.launch {
                                        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                                        val entity = com.renly.rmf.data.local.entity.CountdownEntity(
                                            title = title,
                                            targetDate = targetDate,
                                            category = category,
                                            colorHex = colorHex,
                                            createdAt = nowStr,
                                            updatedAt = nowStr
                                        )
                                        db.countdownDao().insertOrUpdate(entity)
                                        Toast.makeText(this@MainActivity, "✓ 倒数日已创建！", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onTogglePin = { item ->
                                    lifecycleScope.launch {
                                        val newPin = if (item.isPinned == 1) 0 else 1
                                        db.countdownDao().togglePin(item.id, newPin, LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                                    }
                                },
                                onDeleteCountdown = { id ->
                                    lifecycleScope.launch {
                                        db.countdownDao().softDelete(id, LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                                    }
                                }
                            )

                            Screen.Workbench -> WorkbenchScreen(
                                onNavigateToSettings = { navigateToScreen(Screen.Settings, 1) },
                                onNavigateToGoals = { navigateToScreen(Screen.Goals, 1) },
                                onNavigateToExpenses = { navigateToScreen(Screen.Expenses, 1) },
                                onNavigateToFitness = { navigateToScreen(Screen.Fitness, 1) },
                                onNavigateToDailyReport = { navigateToScreen(Screen.DailyReport, 1) },
                                onNavigateToSummary = { navigateToScreen(Screen.Summary, 1) },
                                onNavigateToHabits = { navigateToScreen(Screen.Habits, 1) },
                                onNavigateToCountdowns = { navigateToScreen(Screen.Countdowns, 1) },
                                onNavigateToTutorial = { navigateToScreen(Screen.Tutorial, 1) },
                                onTriggerAudit = {
                                    val currentList = runBlockingOrCachedSchedules()
                                    SchedulerAuditEngine.audit(currentList)
                                },
                                onTriggerAiReview = { _, callback ->
                                    lifecycleScope.launch {
                                        val schedules = db.scheduleDao().getAllRawSchedules().filter { it.isDeleted == 0 }
                                        val completed = schedules.count { it.status == "COMPLETED" }
                                        val interruptions = db.interruptionDao().getAllRawInterruptions()
                                        val topics = db.studyTopicDao().getAllRawTopics().filter { it.isDeleted == 0 }
                                        val studySum = topics.joinToString { "${it.title}: ${it.progressPercent}%" }

                                        when (val res = com.renly.rmf.domain.service.AiService.generateDailyReview(
                                            context = this@MainActivity,
                                            completedCount = completed,
                                            totalSchedules = schedules.size,
                                            focusMinutes = completed * 30,
                                            interruptionCount = interruptions.size,
                                            studySummary = studySum.ifBlank { "无专项更新" }
                                        )) {
                                            is com.renly.rmf.domain.service.AiResult.Success -> callback(res.data)
                                            is com.renly.rmf.domain.service.AiResult.Error -> callback("生成失败: ${res.errorMessage}")
                                        }
                                    }
                                }
                            )

                            Screen.Settings -> SettingsScreen(
                                onBack = { navigateToScreen(lastScreenBeforeSettings, -1) },
                                currentThemeMode = currentThemeMode,
                                onThemeModeChange = { newMode ->
                                    currentThemeMode = newMode
                                    com.renly.rmf.ui.theme.ThemePreferences.setThemeMode(this@MainActivity, newMode)
                                },
                                currentAccentColorHex = currentAccentColorHex,
                                onAccentColorChange = { newHex ->
                                    currentAccentColorHex = newHex
                                    com.renly.rmf.ui.theme.ThemePreferences.setAccentColorHex(this@MainActivity, newHex)
                                },
                                currentCustomDarkBgHex = currentCustomDarkBgHex,
                                onDarkBgColorChange = { newHex ->
                                    currentCustomDarkBgHex = newHex
                                    com.renly.rmf.ui.theme.ThemePreferences.setDarkBgColorHex(this@MainActivity, newHex)
                                },
                                currentCustomLightBgHex = currentCustomLightBgHex,
                                onLightBgColorChange = { newHex ->
                                    currentCustomLightBgHex = newHex
                                    com.renly.rmf.ui.theme.ThemePreferences.setLightBgColorHex(this@MainActivity, newHex)
                                },
                                isBottomBarVisible = isBottomBarVisible,
                                onBottomBarVisibleChange = { visible ->
                                    isBottomBarVisible = visible
                                    com.renly.rmf.domain.service.NavigationPreferences.setBottomBarVisible(this@MainActivity, visible)
                                },
                                enabledNavRoutes = enabledNavRoutes,
                                onEnabledNavRoutesChange = { routes ->
                                    enabledNavRoutes = routes
                                    com.renly.rmf.domain.service.NavigationPreferences.setEnabledRoutes(this@MainActivity, routes)
                                },
                                defaultHomeRoute = defaultHomeRoute,
                                onDefaultHomeRouteChange = { route ->
                                    defaultHomeRoute = route
                                    com.renly.rmf.domain.service.NavigationPreferences.setDefaultHomeRoute(this@MainActivity, route)
                                    Toast.makeText(this@MainActivity, "已设为冷启动默认主页", Toast.LENGTH_SHORT).show()
                                },
                                syncDao = db.syncDao(),
                                onTriggerAiReview = { _, callback ->
                                    lifecycleScope.launch {
                                        val schedules = db.scheduleDao().getAllRawSchedules().filter { it.isDeleted == 0 }
                                        val completed = schedules.count { it.status == "COMPLETED" }
                                        val interruptions = db.interruptionDao().getAllRawInterruptions()
                                        val topics = db.studyTopicDao().getAllRawTopics().filter { it.isDeleted == 0 }
                                        val studySum = topics.joinToString { "${it.title}: ${it.progressPercent}%" }

                                        when (val res = com.renly.rmf.domain.service.AiService.generateDailyReview(
                                            context = this@MainActivity,
                                            completedCount = completed,
                                            totalSchedules = schedules.size,
                                            focusMinutes = completed * 30,
                                            interruptionCount = interruptions.size,
                                            studySummary = studySum.ifBlank { "无专项更新" }
                                        )) {
                                            is com.renly.rmf.domain.service.AiResult.Success -> callback(res.data)
                                            is com.renly.rmf.domain.service.AiResult.Error -> callback("生成失败: ${res.errorMessage}")
                                        }
                                    }
                                },
                                onExportData = {
                                    lifecycleScope.launch {
                                        DataExportService.exportAndShareAllData(this@MainActivity, db)
                                    }
                                },
                                onExportLosslessDb = {
                                    exportBackupLauncher.launch("rmf_lossless_backup_v1.4.3.3.db")
                                },
                                onImportLosslessDb = {
                                    importBackupLauncher.launch(arrayOf("*/*"))
                                },
                                onClearAllBusinessData = {
                                    lifecycleScope.launch {
                                        com.renly.rmf.domain.service.LosslessUpgradeManager.createManualSnapshotBackup(this@MainActivity)
                                        db.clearAllBusinessData()
                                        Toast.makeText(this@MainActivity, "已清空所有测试业务数据，并已自动生成安全备份！", Toast.LENGTH_LONG).show()
                                    }
                                },
                                onTriggerUpload = {
                                    if (com.renly.rmf.domain.service.GoogleDrivePreferences.isAuthorized(this@MainActivity)) {
                                        triggerDriveApiUpload()
                                    } else {
                                        triggerCloudBackup()
                                    }
                                },
                                onTriggerDownload = {
                                    if (com.renly.rmf.domain.service.GoogleDrivePreferences.isAuthorized(this@MainActivity)) {
                                        triggerDriveApiDownload()
                                    } else {
                                        importBackupLauncher.launch(arrayOf("*/*"))
                                    }
                                },
                                onShowGestureTutorial = {
                                    showSettingsGestureTutorial = true
                                }
                            )
                                }
                            }
                        }
                    }
                }
            }

                        if (showQuickCaptureDialog) {
                            QuickCaptureDialog(
                                onDismissRequest = { showQuickCaptureDialog = false },
                                onSaveSchedule = { schedule ->
                                    lifecycleScope.launch {
                                        db.scheduleDao().insertOrUpdate(schedule)
                                        com.renly.rmf.domain.service.ScheduleReminderManager.scheduleReminder(
                                            context = this@MainActivity,
                                            scheduleId = schedule.id,
                                            title = schedule.title,
                                            startTimeStr = schedule.startTime,
                                            advanceMinutes = 10
                                        )
                                        Toast.makeText(this@MainActivity, "闪念日程已排期入库！", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onSaveExpense = { expense ->
                                    lifecycleScope.launch {
                                        db.expenseDao().insertOrUpdate(expense)
                                        Toast.makeText(this@MainActivity, "已记录支出 ￥${expense.amount}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }

                    // 当用户选择取消不显示底部快捷栏时，提供右下角悬浮快捷导航入口，防止迷航
                    if (!isBottomBarVisible && !isInPipMode) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 24.dp, end = 18.dp),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Surface(
                                onClick = { showFloatingNavMenu = true },
                                shape = RoundedCornerShape(24.dp),
                                color = DopamineBlue,
                                shadowElevation = 8.dp,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🧭", fontSize = 15.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "快捷导航",
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (showFloatingNavMenu) {
                        AlertDialog(
                            onDismissRequest = { showFloatingNavMenu = false },
                            containerColor = DarkCard,
                            shape = RoundedCornerShape(20.dp),
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🧭", fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "全屏快捷导航",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                            },
                            text = {
                                Column {
                                    Text(
                                        text = "当前处于沉浸全屏模式（底栏已隐藏）。可直接点击切换任意模块：",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    val allScreens = listOf(
                                        Screen.Schedule,
                                        Screen.Timetable,
                                        Screen.Study,
                                        Screen.Focus,
                                        Screen.Workbench,
                                        Screen.Fitness,
                                        Screen.Goals,
                                        Screen.Expenses,
                                        Screen.DailyReport,
                                        Screen.Summary,
                                        Screen.Settings
                                    )
                                    allScreens.chunked(2).forEach { rowScreens ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            rowScreens.forEach { scr ->
                                                val isCurrent = currentScreen == scr
                                                Surface(
                                                    onClick = {
                                                        navigateToScreen(scr, null)
                                                        showFloatingNavMenu = false
                                                    },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (isCurrent) DopamineBlue.copy(alpha = 0.2f) else DarkSurface,
                                                    border = BorderStroke(1.dp, if (isCurrent) DopamineBlue else DarkBorder),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(42.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .padding(horizontal = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            scr.icon,
                                                            contentDescription = scr.title,
                                                            tint = if (isCurrent) DopamineBlue else TextSecondary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = scr.title,
                                                            fontSize = 12.5.sp,
                                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                            color = if (isCurrent) DopamineBlue else TextPrimary
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Surface(
                                        onClick = {
                                            isBottomBarVisible = true
                                            com.renly.rmf.domain.service.NavigationPreferences.setBottomBarVisible(this@MainActivity, true)
                                            showFloatingNavMenu = false
                                            Toast.makeText(this@MainActivity, "已恢复显示底部快捷导航栏", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = DopaminePurple.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, DopaminePurple.copy(alpha = 0.4f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "🔄 恢复显示底部快捷栏",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = DopaminePurple
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showFloatingNavMenu = false }) {
                                    Text("关闭", color = TextSecondary)
                                }
                            }
                        )
                    }

                    if (showSettingsGestureTutorial) {
                        com.renly.rmf.ui.screens.SettingsGestureTutorialDialog(
                            onDismiss = {
                                com.renly.rmf.domain.service.SettingsTutorialPreferences.setSettingsGestureTutorialCompleted(this@MainActivity, true)
                                showSettingsGestureTutorial = false
                            },
                            onTryNow = {
                                com.renly.rmf.domain.service.SettingsTutorialPreferences.setSettingsGestureTutorialCompleted(this@MainActivity, true)
                                showSettingsGestureTutorial = false
                                navigateToScreen(Screen.Settings, 1)
                                Toast.makeText(this@MainActivity, "✨ 已进入系统设置！在底部栏左右轻滑即可退回", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}
}
}

    private var cachedSchedules: List<ScheduleEntity> = emptyList()

    private fun runBlockingOrCachedSchedules(): List<ScheduleEntity> {
        lifecycleScope.launch {
            cachedSchedules = db.scheduleDao().getAllActiveSchedules().first()
        }
        return cachedSchedules
    }
}

@Composable
fun AppSplashScreen(
    onFinished: () -> Unit
) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(900)
        onFinished()
    }

    val isDark = LocalAppColors.current.isDark
    val splashBg = if (isDark) DarkPalette.bg else LightPalette.bg
    val rmfTextColor = if (isDark) Color.White else Color.Black
    val subtitleColor = if (isDark) DopamineBlue else Color(0xFF0284C7)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(splashBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_logo),
                contentDescription = "RMF Logo",
                modifier = Modifier.size(92.dp)
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "RMF",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = rmfTextColor,
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Renly Management Platform",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = subtitleColor,
                letterSpacing = 1.sp
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 28.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = "©Renly 2026 保留所有权利",
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}

/**
 * 极速硬件加速屏幕容器 (ScreenLayerSlot)
 * 将位移动画与拖拽偏移量完全隔离在 RenderNode 的 graphicsLayer 绘制阶段，
 * 彻底消除切换动画与手势拖拽过程中的 Composable 树冗余重组，实现纯正原生 120Hz 满帧流体级体验。
 */
@Composable
private fun ScreenLayerSlot(
    screen: Screen,
    isActive: Boolean,
    isAdjacent: Boolean,
    isPrev: Boolean,
    isHandDragging: Boolean,
    isDragSettling: Boolean,
    slideDirection: Int,
    screenWidth: Float,
    dragOffsetProvider: () -> Float,
    skipTransitionAnim: Boolean,
    content: @Composable () -> Unit
) {
    val animProgress = remember { androidx.compose.animation.core.Animatable(if (isActive) 1f else 0f) }

    LaunchedEffect(isActive, skipTransitionAnim) {
        if (skipTransitionAnim) {
            animProgress.snapTo(if (isActive) 1f else 0f)
        } else {
            animProgress.animateTo(
                targetValue = if (isActive) 1f else 0f,
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 280,
                    easing = androidx.compose.animation.core.CubicBezierEasing(0.08f, 0.82f, 0.17f, 1.0f)
                )
            )
        }
    }

    val shouldRender = isActive || (isAdjacent && (isHandDragging || isDragSettling)) || isPrev

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (shouldRender) {
                    Modifier.graphicsLayer {
                        if (isHandDragging || isDragSettling) {
                            val currentOffset = dragOffsetProvider()
                            val dragFraction = (kotlin.math.abs(currentOffset) / screenWidth).coerceIn(0f, 1f)
                            if (isActive) {
                                translationX = currentOffset
                                alpha = 1f - dragFraction * 0.22f
                                scaleX = 1f - dragFraction * 0.03f
                                scaleY = 1f - dragFraction * 0.03f
                            } else if (isAdjacent) {
                                val base = if (currentOffset < 0f) screenWidth else -screenWidth
                                translationX = base + currentOffset
                                alpha = 0.80f + dragFraction * 0.20f
                                scaleX = 0.97f + dragFraction * 0.03f
                                scaleY = 0.97f + dragFraction * 0.03f
                            } else {
                                translationX = 100000f
                                alpha = 0f
                            }
                        } else if (isActive) {
                            val progress = animProgress.value
                            translationX = (1f - progress) * (slideDirection * screenWidth)
                            alpha = progress.coerceIn(0f, 1f)
                            val s = 0.96f + 0.04f * progress.coerceIn(0f, 1f)
                            scaleX = s
                            scaleY = s
                        } else if (isPrev) {
                            val progress = animProgress.value
                            translationX = (progress - 1f) * (slideDirection * (screenWidth * 0.28f))
                            alpha = progress.coerceIn(0f, 1f)
                            val s = 0.96f + 0.04f * progress.coerceIn(0f, 1f)
                            scaleX = s
                            scaleY = s
                        } else {
                            translationX = 100000f
                            alpha = 0f
                        }
                    }
                } else {
                    Modifier
                        .graphicsLayer {
                            alpha = 0f
                            translationX = 100000f
                        }
                        .clearAndSetSemantics { }
                }
            )
    ) {
        content()
    }
}

