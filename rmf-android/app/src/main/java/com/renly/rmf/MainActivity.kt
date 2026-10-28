package com.renly.rmf

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EditNote
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.lifecycleScope
import com.renly.rmf.BuildConfig
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
    object Reminders : Screen("reminders", "提醒", Icons.Default.NotificationsActive)
    object Notes : Screen("notes", "随记", Icons.Default.EditNote)

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
                "reminders" -> Reminders
                "notes" -> Notes
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

    private var lastResumeCheckMillis: Long = 0L

    override fun onResume() {
        super.onResume()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            isInPipMode = isInPictureInPictureMode
        }

        // 切回前台智能轻检测与拉取 (防抖 60 秒冷却)，零感后台静默检测与行级增量合并
        if (com.renly.rmf.domain.service.PrivacyPreferences.isPrivacyAccepted(this)) {
            val now = System.currentTimeMillis()
            if (now - lastResumeCheckMillis >= 60_000L) {
                lastResumeCheckMillis = now
                lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    checkAndPullCloudSyncSilent()
                }
            }
        }
    }

    private suspend fun checkAndPullCloudSyncSilent(force: Boolean = false) {
        if (!com.renly.rmf.domain.service.GoogleDrivePreferences.isAuthorized(this)) {
            com.renly.rmf.domain.service.GoogleDriveSyncStateHolder.updateStatus(
                com.renly.rmf.domain.service.CloudSyncState.IDLE,
                "未连接"
            )
            return
        }

        try {
            val pullResult = googleDriveSyncService.pullLatestBackupIfNewer(force = force)
            if (pullResult is com.renly.rmf.domain.service.SyncResult.Success && !pullResult.message.contains("无需拉取")) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(this@MainActivity, "✓ 已自动完成云端数据双向增量同步", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("RMF_Sync", "Cloud sync pull error (silent): ${e.message}")
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

        // 离开前台时异步截断 WAL 预写日志并收缩 SQLite 内存池，将后台常驻内存占用降至最低
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(PASSIVE);").close()
                db.openHelper.writableDatabase.query("PRAGMA shrink_memory;").close()
            } catch (_: Exception) {}
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE);").close()
                    db.openHelper.writableDatabase.query("PRAGMA shrink_memory;").close()
                } catch (_: Exception) {}
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleWidgetIntent(intent)

        // 启动时清理本地所有历史备份与临时快照，坚决贯彻“本地就一个版本 (rmf.db)”
        com.renly.rmf.domain.service.GoogleDriveSyncService.cleanupLocalBackupFiles(this)

        // 注册 Room 表级变更监听：每操作一次数据，防抖3秒自动向云端专属文件夹上传时间戳版本
        db.invalidationTracker.addObserver(object : androidx.room.InvalidationTracker.Observer(
            arrayOf(
                "schedules", "courses", "course_adjustments", "timetable_settings",
                "reminders", "notes", "habits", "goals", "expenses", "time_markers", "countdowns", "fitness_plans", "study_topics"
            )
        ) {
            override fun onInvalidated(tables: Set<String>) {
                com.renly.rmf.domain.service.GoogleDriveAutoSyncManager.requestAutoSync(this@MainActivity)
            }
        })

        // 异步后台自动检查并排期未来课程的胶囊通知上课提醒与全学期课表日程实时映射 (遵循工信部合规规范，仅在用户同意隐私政策后启动)
        if (com.renly.rmf.domain.service.PrivacyPreferences.isPrivacyAccepted(this)) {
            checkAndRequestNotificationPermission()
            com.renly.rmf.domain.service.QuickActionNotificationManager.updateQuickActionNotification(this)
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

            // 初始化云端同步状态指示器
            if (com.renly.rmf.domain.service.GoogleDrivePreferences.isAuthorized(this@MainActivity)) {
                val lastSync = com.renly.rmf.domain.service.GoogleDrivePreferences.getLastSyncTime(this@MainActivity)
                com.renly.rmf.domain.service.GoogleDriveSyncStateHolder.updateStatus(
                    com.renly.rmf.domain.service.CloudSyncState.SUCCESS,
                    if (lastSync.isNotBlank()) "已同步 ($lastSync)" else "已连接云端",
                    lastSync
                )
            } else {
                com.renly.rmf.domain.service.GoogleDriveSyncStateHolder.updateStatus(
                    com.renly.rmf.domain.service.CloudSyncState.IDLE,
                    "未连接"
                )
            }

            // 软件冷启动无后台静默检查并拉取云端最新数据双向同步
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                checkAndPullCloudSyncSilent()
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

                val density = LocalDensity.current
                val haptic = LocalHapticFeedback.current
                val coroutineScope = rememberCoroutineScope()
                val saveableStateHolder = rememberSaveableStateHolder()

                val safeNavScreens = remember(enabledNavRoutes) {
                    val nav = enabledNavRoutes.map { Screen.fromRoute(it) }
                    if (nav.isEmpty()) listOf(Screen.Schedule, Screen.Workbench) else nav
                }

                val initialNavIndex = remember(safeNavScreens, defaultHomeRoute) {
                    val idx = safeNavScreens.indexOfFirst { it.route == defaultHomeRoute }
                    if (idx >= 0) idx else 0
                }

                val pagerState = rememberPagerState(
                    initialPage = initialNavIndex,
                    pageCount = { safeNavScreens.size }
                )

                var currentScreen by remember {
                    mutableStateOf<Screen>(safeNavScreens.getOrNull(initialNavIndex) ?: Screen.Schedule)
                }
                var lastScreenBeforeSettings by remember { mutableStateOf<Screen>(currentScreen) }
                var showFloatingNavMenu by remember { mutableStateOf(false) }

                // 页面滑动手势完成后，自动同步当前选中的屏幕状态与底栏高亮，并触发触感反馈
                LaunchedEffect(pagerState) {
                    androidx.compose.runtime.snapshotFlow { pagerState.currentPage }
                        .collect { page ->
                            val pageScreen = safeNavScreens.getOrNull(page)
                            if (pageScreen != null && currentScreen != pageScreen && safeNavScreens.contains(currentScreen)) {
                                com.renly.rmf.util.HapticHelper.performPageTurn(this@MainActivity)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentScreen = pageScreen
                            }
                        }
                }

                // 底栏配置变动时，安全同步当前 pager 页码，严防索引越界闪退并保持当前页面状态无缝对接 (滑动手势进行中绝不强行打断)
                LaunchedEffect(safeNavScreens) {
                    if (safeNavScreens.isNotEmpty() && !pagerState.isScrollInProgress) {
                        val targetIdx = safeNavScreens.indexOf(currentScreen)
                        if (targetIdx != -1) {
                            if (pagerState.currentPage != targetIdx) {
                                pagerState.scrollToPage(targetIdx)
                            }
                        } else {
                            val clamped = pagerState.currentPage.coerceIn(0, safeNavScreens.size - 1)
                            if (pagerState.currentPage != clamped) {
                                pagerState.scrollToPage(clamped)
                            }
                            if (currentScreen != Screen.Settings) {
                                currentScreen = safeNavScreens.getOrNull(clamped) ?: Screen.Schedule
                            }
                        }
                    }
                }

                // 核心统一导航调度函数
                val navigateToScreen: (Screen, Int?) -> Unit = { targetScreen, _ ->
                    val targetIdx = safeNavScreens.indexOf(targetScreen)
                    if (targetIdx != -1) {
                        currentScreen = targetScreen
                        coroutineScope.launch {
                            if (kotlin.math.abs(pagerState.currentPage - targetIdx) > 1) {
                                pagerState.scrollToPage(targetIdx)
                            } else {
                                pagerState.animateScrollToPage(targetIdx)
                            }
                        }
                    } else {
                        if (currentScreen != Screen.Settings && safeNavScreens.contains(currentScreen)) {
                            lastScreenBeforeSettings = currentScreen
                        }
                        currentScreen = targetScreen
                    }
                }

                LaunchedEffect(currentScreen) {
                    if (currentScreen != Screen.Settings && safeNavScreens.contains(currentScreen)) {
                        lastScreenBeforeSettings = currentScreen
                    }
                }

                var showQuickCaptureDialog by remember { mutableStateOf(false) }
                var dailyReportDate by remember { mutableStateOf(java.time.LocalDate.now()) }

                // 预先缓存并持久化所有 Room DAO 的响应式 Flow 实例，杜绝页面滑动或重组频繁重建流导致界面卡顿闪烁
                val schedulesFlow = remember { db.scheduleDao().getAllActiveSchedules() }
                val tagsFlow = remember { db.syncDao().getAllTags() }
                val timeMarkersFlow = remember { db.timeMarkerDao().getAllActiveMarkers() }
                val coursesFlow = remember { db.courseDao().getAllActiveCourses() }
                val adjustmentsFlow = remember { db.courseDao().getAllActiveAdjustments() }
                val studyTopicsFlow = remember { db.studyTopicDao().getAllActiveTopics() }
                val focusRecordsFlow = remember { db.focusDao().getAllFocusRecords() }
                val goalsFlow = remember { db.goalDao().getAllActiveGoals() }
                val expensesFlow = remember { db.expenseDao().getAllActiveExpenses() }
                val interruptionsFlow = remember { db.interruptionDao().getAllInterruptions() }
                val habitsFlow = remember { db.habitDao().getAllActiveHabits() }
                val countdownsFlow = remember { db.countdownDao().getAllActiveCountdowns() }
                val notesFlow = remember { db.noteDao().getAllActiveNotes() }
                val todaySchedulesFlow = remember(dailyReportDate) {
                    db.scheduleDao().getSchedulesByDateRange(
                        dailyReportDate.atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        dailyReportDate.plusDays(1).atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    )
                }
                val todayMarkersFlow = remember(dailyReportDate) {
                    db.timeMarkerDao().getMarkersByDate(dailyReportDate.toString())
                }

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

                val configuration = LocalConfiguration.current
                val useNavigationRail = remember(configuration) {
                    BuildConfig.IS_PAD_EDITION || configuration.screenWidthDp >= 600
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
                        if (!isInPipMode && isBottomBarVisible && !useNavigationRail) {
                            var bottomBarDragDelta by remember { mutableFloatStateOf(0f) }

                            Surface(
                                color = DarkSurface,
                                border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.75f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .draggable(
                                        orientation = Orientation.Vertical,
                                        state = rememberDraggableState { delta ->
                                            bottomBarDragDelta += delta
                                        },
                                        onDragStopped = { velocity ->
                                            val dist = bottomBarDragDelta
                                            bottomBarDragDelta = 0f

                                            // 底部栏向上轻拉 (dist < 0, velocity < 0)：拉出设置界面
                                            if (dist < -20f || velocity < -100f) {
                                                if (currentScreen != Screen.Settings) {
                                                    com.renly.rmf.domain.service.SettingsTutorialPreferences.setSettingsGestureTutorialCompleted(this@MainActivity, true)
                                                    showSettingsGestureTutorial = false
                                                    com.renly.rmf.util.HapticHelper.performQuadrantMoved(this@MainActivity)
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    navigateToScreen(Screen.Settings, 1)
                                                }
                                            }
                                            // 底部栏向下轻拉 (dist > 0, velocity > 0)：收回设置界面
                                            else if (dist > 20f || velocity > 100f) {
                                                if (currentScreen == Screen.Settings) {
                                                    com.renly.rmf.util.HapticHelper.performQuadrantMoved(this@MainActivity)
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    val fallback = if (safeNavScreens.contains(lastScreenBeforeSettings)) {
                                                        lastScreenBeforeSettings
                                                    } else {
                                                        safeNavScreens.firstOrNull() ?: Screen.Schedule
                                                    }
                                                    navigateToScreen(fallback, -1)
                                                }
                                            }
                                        }
                                    )
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // 顶部微光上拉/下拉手势指示条 (Subtle vertical pull handle)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp, bottom = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(36.dp)
                                                .height(3.5.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(
                                                    if (currentScreen == Screen.Settings)
                                                        DopamineBlue.copy(alpha = 0.7f)
                                                    else
                                                        Color.White.copy(alpha = 0.22f)
                                                )
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
                                                    com.renly.rmf.util.HapticHelper.performLightClick(this@MainActivity)
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
                    val isInMainPager = safeNavScreens.contains(currentScreen)

                    BackHandler(enabled = !isInMainPager || currentScreen == Screen.Settings) {
                        val fallback = if (safeNavScreens.contains(lastScreenBeforeSettings)) {
                            lastScreenBeforeSettings
                        } else {
                            safeNavScreens.firstOrNull() ?: Screen.Schedule
                        }
                        navigateToScreen(fallback, -1)
                    }

                    val renderScreenContent: @Composable (Screen, Boolean) -> Unit = { screen, isActive ->
                        when (screen) {
                            Screen.Schedule -> ScheduleScreen(
                                schedulesFlow = schedulesFlow,
                                tagsFlow = tagsFlow,
                                timeMarkersFlow = timeMarkersFlow,
                                onAddTimeMarker = { marker ->
                                    lifecycleScope.launch {
                                        db.timeMarkerDao().insertOrUpdate(marker)
                                    }
                                },
                                onUpdateTimeMarker = { marker ->
                                    lifecycleScope.launch {
                                        db.timeMarkerDao().insertOrUpdate(marker)
                                    }
                                },
                                onDeleteTimeMarker = { id ->
                                    lifecycleScope.launch {
                                        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                                        db.timeMarkerDao().softDelete(id, nowStr)
                                    }
                                },
                                onNavigateToNotes = { navigateToScreen(Screen.Notes, 1) },
                                onToggleStatus = { schedule ->
                                    lifecycleScope.launch {
                                        val newStatus = if (schedule.status == "COMPLETED") "PENDING" else "COMPLETED"
                                        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                                        if (newStatus == "COMPLETED" && schedule.actualMinutes == 0) {
                                            val actMins = try {
                                                val sDt = LocalDateTime.parse(schedule.startTime, DateTimeFormatter.ISO_DATE_TIME)
                                                val dur = java.time.Duration.between(sDt, LocalDateTime.now()).toMinutes().toInt()
                                                if (dur in 5..360) dur else schedule.estimatedMinutes
                                            } catch (_: Exception) {
                                                schedule.estimatedMinutes
                                            }
                                            db.scheduleDao().insertOrUpdate(schedule.copy(status = newStatus, actualMinutes = actMins, updatedAt = nowStr))
                                        } else {
                                            db.scheduleDao().updateStatus(schedule.id, newStatus, nowStr)
                                        }
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
                                isActive = isActive,
                                coursesFlow = coursesFlow,
                                adjustmentsFlow = adjustmentsFlow,
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
                                topicsFlow = studyTopicsFlow,
                                coursesFlow = coursesFlow,
                                onAddTopic = { topic ->
                                    lifecycleScope.launch {
                                        db.studyTopicDao().insertOrUpdate(topic)
                                    }
                                },
                                onUpdateTopic = { topic ->
                                    lifecycleScope.launch {
                                        db.studyTopicDao().insertOrUpdate(topic)
                                    }
                                },
                                onDeleteTopic = { topicId ->
                                    lifecycleScope.launch {
                                        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                                        db.studyTopicDao().softDelete(topicId, nowStr)
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
                                schedulesFlow = schedulesFlow,
                                focusRecordsFlow = focusRecordsFlow,
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
                                goalsFlow = goalsFlow,
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
                                expensesFlow = expensesFlow,
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
                                todaySchedulesFlow = todaySchedulesFlow,
                                todayMarkersFlow = todayMarkersFlow,
                                onSaveReport = { report ->
                                    lifecycleScope.launch {
                                        db.dailyReportDao().insertOrUpdate(report)
                                        Toast.makeText(this@MainActivity, "今日复盘已妥善保存！", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            Screen.Summary -> SummaryScreen(
                                schedulesFlow = schedulesFlow,
                                coursesFlow = coursesFlow,
                                interruptionsFlow = interruptionsFlow,
                                expensesFlow = expensesFlow,
                                timeMarkersFlow = timeMarkersFlow,
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
                                habitsFlow = habitsFlow,
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
                                countdownsFlow = countdownsFlow,
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

                            Screen.Reminders -> RemindersScreen(
                                reminderDao = db.reminderDao(),
                                onNavigateBack = { navigateToScreen(Screen.Workbench, -1) }
                            )

                            Screen.Notes -> NotesScreen(
                                notesFlow = notesFlow,
                                onAddNote = { title, content, category, colorHex, isPinned ->
                                    lifecycleScope.launch {
                                        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                                        val entity = com.renly.rmf.data.local.entity.NoteEntity(
                                            title = title,
                                            content = content,
                                            category = category,
                                            colorHex = colorHex,
                                            isPinned = if (isPinned) 1 else 0,
                                            createdAt = nowStr,
                                            updatedAt = nowStr
                                        )
                                        db.noteDao().insertOrUpdate(entity)
                                    }
                                },
                                onUpdateNote = { note ->
                                    lifecycleScope.launch {
                                        db.noteDao().insertOrUpdate(note)
                                    }
                                },
                                onDeleteNote = { id ->
                                    lifecycleScope.launch {
                                        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                                        db.noteDao().softDelete(id, nowStr)
                                    }
                                },
                                onTogglePin = { note ->
                                    lifecycleScope.launch {
                                        val newPin = if (note.isPinned == 1) 0 else 1
                                        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                                        db.noteDao().togglePin(note.id, newPin, nowStr)
                                    }
                                },
                                onConvertToSchedule = { title, content ->
                                    lifecycleScope.launch {
                                        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                                        val entity = ScheduleEntity(
                                            title = title,
                                            description = content,
                                            workType = "DEEP_WORK",
                                            category = "工作",
                                            status = "BACKLOG",
                                            estimatedMinutes = 30,
                                            startTime = "",
                                            endTime = "",
                                            eisenhowerQuadrant = "Q2",
                                            createdAt = nowStr,
                                            updatedAt = nowStr
                                        )
                                        db.scheduleDao().insertOrUpdate(entity)
                                        Toast.makeText(this@MainActivity, "✓ 已添加至待办任务箱", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onBack = {
                                    val fallback = if (safeNavScreens.contains(lastScreenBeforeSettings)) {
                                        lastScreenBeforeSettings
                                    } else {
                                        safeNavScreens.firstOrNull() ?: Screen.Schedule
                                    }
                                    navigateToScreen(fallback, -1)
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
                                onNavigateToReminders = { navigateToScreen(Screen.Reminders, 1) },
                                onNavigateToNotes = { navigateToScreen(Screen.Notes, 1) },
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
                                        val todayStr = java.time.LocalDate.now().toString()
                                        val timeMarkers = db.timeMarkerDao().getMarkersListByDate(todayStr)
                                        val markersSummary = com.renly.rmf.domain.service.TimeMarkerVarianceAnalyzer.buildSummaryTextForAi(
                                            java.time.LocalDate.now(),
                                            schedules,
                                            timeMarkers
                                        )

                                        when (val res = com.renly.rmf.domain.service.AiService.generateDailyReview(
                                            context = this@MainActivity,
                                            completedCount = completed,
                                            totalSchedules = schedules.size,
                                            focusMinutes = completed * 30,
                                            interruptionCount = interruptions.size,
                                            studySummary = studySum.ifBlank { "无专项更新" },
                                            timeMarkersSummary = markersSummary
                                        )) {
                                            is com.renly.rmf.domain.service.AiResult.Success -> callback(res.data)
                                            is com.renly.rmf.domain.service.AiResult.Error -> callback("生成失败: ${res.errorMessage}")
                                        }
                                    }
                                }
                            )

                            Screen.Settings -> SettingsScreen(
                                onBack = {
                                    val fallback = if (safeNavScreens.contains(lastScreenBeforeSettings)) {
                                        lastScreenBeforeSettings
                                    } else {
                                        safeNavScreens.firstOrNull() ?: Screen.Schedule
                                    }
                                    navigateToScreen(fallback, -1)
                                },
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
                                        val todayStr = java.time.LocalDate.now().toString()
                                        val timeMarkers = db.timeMarkerDao().getMarkersListByDate(todayStr)
                                        val markersSummary = com.renly.rmf.domain.service.TimeMarkerVarianceAnalyzer.buildSummaryTextForAi(
                                            java.time.LocalDate.now(),
                                            schedules,
                                            timeMarkers
                                        )

                                        when (val res = com.renly.rmf.domain.service.AiService.generateDailyReview(
                                            context = this@MainActivity,
                                            completedCount = completed,
                                            totalSchedules = schedules.size,
                                            focusMinutes = completed * 30,
                                            interruptionCount = interruptions.size,
                                            studySummary = studySum.ifBlank { "无专项更新" },
                                            timeMarkersSummary = markersSummary
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

                    Box(modifier = Modifier.fillMaxSize()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(if (isInPipMode) androidx.compose.foundation.layout.PaddingValues(0.dp) else innerPadding),
                            color = DarkBg
                        ) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                if (useNavigationRail && !isInPipMode) {
                                    Surface(
                                        color = DarkSurface,
                                        border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.75f)),
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(84.dp)
                                    ) {
                                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                            val needsScroll = maxHeight < 520.dp
                                            val railScrollState = rememberScrollState()
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .then(if (needsScroll) Modifier.verticalScroll(railScrollState) else Modifier),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                                                ) {
                                                    Image(
                                                        painter = painterResource(id = R.drawable.ic_logo),
                                                        contentDescription = "RMF Pad Logo",
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = if (BuildConfig.IS_PAD_EDITION) "RMF Pad" else "RMF",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = DopamineBlue
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Surface(
                                                        onClick = { showQuickCaptureDialog = true },
                                                        shape = androidx.compose.foundation.shape.CircleShape,
                                                        color = DopaminePurple.copy(alpha = 0.18f),
                                                        border = BorderStroke(1.dp, DopaminePurple.copy(alpha = 0.45f)),
                                                        modifier = Modifier.size(34.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text("⚡", fontSize = 15.sp)
                                                        }
                                                    }
                                                }

                                                if (!needsScroll) Spacer(modifier = Modifier.weight(1f))

                                                safeNavScreens.forEach { screen ->
                                                    val isSelected = currentScreen == screen
                                                    val iconScale by animateFloatAsState(
                                                        targetValue = if (isSelected) 1.15f else 1.0f,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessLow
                                                        ),
                                                        label = "railIconScale_${screen.route}"
                                                    )
                                                    NavigationRailItem(
                                                        selected = isSelected,
                                                        onClick = {
                                                            com.renly.rmf.util.HapticHelper.performLightClick(this@MainActivity)
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
                                                                fontSize = 11.sp,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                            )
                                                        },
                                                        colors = NavigationRailItemDefaults.colors(
                                                            selectedIconColor = DopamineBlue,
                                                            selectedTextColor = DopamineBlue,
                                                            unselectedIconColor = TextMuted,
                                                            unselectedTextColor = TextMuted,
                                                            indicatorColor = DopamineBlue.copy(alpha = 0.16f)
                                                        ),
                                                        modifier = Modifier.padding(vertical = 2.dp)
                                                    )
                                                }

                                                if (!needsScroll) Spacer(modifier = Modifier.weight(1f))

                                                val isSettingsSelected = currentScreen == Screen.Settings
                                                NavigationRailItem(
                                                    selected = isSettingsSelected,
                                                    onClick = {
                                                        com.renly.rmf.util.HapticHelper.performLightClick(this@MainActivity)
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        if (currentScreen == Screen.Settings) {
                                                            val fallback = if (safeNavScreens.contains(lastScreenBeforeSettings)) {
                                                                lastScreenBeforeSettings
                                                            } else {
                                                                safeNavScreens.firstOrNull() ?: Screen.Schedule
                                                            }
                                                            navigateToScreen(fallback, -1)
                                                        } else {
                                                            navigateToScreen(Screen.Settings, 1)
                                                        }
                                                    },
                                                    icon = {
                                                        Icon(
                                                            Icons.Default.Settings,
                                                            contentDescription = "系统设置",
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    },
                                                    label = {
                                                        Text(
                                                            "设置",
                                                            fontSize = 11.sp,
                                                            fontWeight = if (isSettingsSelected) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    },
                                                    colors = NavigationRailItemDefaults.colors(
                                                        selectedIconColor = DopamineBlue,
                                                        selectedTextColor = DopamineBlue,
                                                        unselectedIconColor = TextMuted,
                                                        unselectedTextColor = TextMuted,
                                                        indicatorColor = DopamineBlue.copy(alpha = 0.16f)
                                                    ),
                                                    modifier = Modifier.padding(bottom = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                // 1. 主功能模块原生 120Hz 极速硬件加速横向滑页视口 (HorizontalPager)
                                // beyondViewportPageCount = 1 预加载前后相邻页，消除滑动手势掉帧与图片闪跳
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize(),
                                    beyondViewportPageCount = 1,
                                    key = { page -> safeNavScreens.getOrNull(page)?.route ?: page }
                                ) { page ->
                                    val screen = safeNavScreens.getOrNull(page) ?: safeNavScreens.firstOrNull() ?: Screen.Schedule
                                    val isPageActive = (pagerState.currentPage == page)
                                    saveableStateHolder.SaveableStateProvider(screen.route) {
                                        renderScreenContent(screen, isPageActive)
                                    }
                                }

                                // 2. 二级子页面或系统设置平滑覆盖层 (从下往上优雅展开、向下拉回收起)
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = !isInMainPager,
                                    enter = slideInVertically(
                                        initialOffsetY = { fullHeight -> fullHeight },
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    ) + expandVertically(
                                        expandFrom = Alignment.Bottom,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    ) + fadeIn(animationSpec = tween(220)),
                                    exit = slideOutVertically(
                                        targetOffsetY = { fullHeight -> fullHeight },
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    ) + shrinkVertically(
                                        shrinkTowards = Alignment.Bottom,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    ) + fadeOut(animationSpec = tween(180))
                                ) {
                                    Surface(
                                        modifier = Modifier.fillMaxSize(),
                                        color = DarkBg
                                    ) {
                                        if (!isInMainPager) {
                                            saveableStateHolder.SaveableStateProvider(currentScreen.route) {
                                                renderScreenContent(currentScreen, true)
                                            }
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
                                },
                                onSaveReminder = { reminder ->
                                    lifecycleScope.launch {
                                        db.reminderDao().insertOrUpdate(reminder)
                                        com.renly.rmf.domain.service.ScheduleReminderManager.scheduleDirectReminder(this@MainActivity, reminder)
                                        if (com.renly.rmf.domain.service.SystemCalendarSyncService.isAutoSyncEnabled(this@MainActivity)) {
                                            com.renly.rmf.domain.service.SystemCalendarSyncService.syncSingleReminder(this@MainActivity, reminder)
                                        }
                                        Toast.makeText(this@MainActivity, "✓ 事件提醒已设定", Toast.LENGTH_SHORT).show()
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

                    if (showSettingsGestureTutorial && !useNavigationRail) {
                        com.renly.rmf.ui.screens.SettingsGestureTutorialDialog(
                            onDismiss = {
                                com.renly.rmf.domain.service.SettingsTutorialPreferences.setSettingsGestureTutorialCompleted(this@MainActivity, true)
                                showSettingsGestureTutorial = false
                            },
                            onTryNow = {
                                com.renly.rmf.domain.service.SettingsTutorialPreferences.setSettingsGestureTutorialCompleted(this@MainActivity, true)
                                showSettingsGestureTutorial = false
                                navigateToScreen(Screen.Settings, 1)
                                Toast.makeText(this@MainActivity, "✨ 已进入系统设置！在底部栏向下轻拉即可退回", Toast.LENGTH_SHORT).show()
                            }
                        )
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


