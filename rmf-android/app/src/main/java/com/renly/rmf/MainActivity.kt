package com.renly.rmf

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.renly.rmf.data.local.entity.ScheduleEntity
import com.renly.rmf.domain.service.DataExportService
import com.renly.rmf.domain.service.GeminiResult
import com.renly.rmf.domain.service.GeminiService
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
    object Settings : Screen("settings", "工作台", Icons.Default.Settings)
    object Goals : Screen("goals", "目标", Icons.Default.Flag)
    object Expenses : Screen("expenses", "记账", Icons.Default.AccountBalanceWallet)
    object DailyReport : Screen("daily_report", "日报", Icons.Default.AutoAwesome)
    object Summary : Screen("summary", "统计", Icons.Default.Insights)
    object Tutorial : Screen("tutorial", "教程", Icons.Default.MenuBook)
    object Fitness : Screen("fitness", "健身", Icons.Default.FitnessCenter)
}

class MainActivity : ComponentActivity() {

    private val db by lazy { (application as RmfApplication).database }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 异步后台自动检查并排期未来课程的 ColorOS 流体云上课提醒
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.renly.rmf.domain.service.CourseReminderScheduler.scheduleUpcomingCourseReminders(
                context = this@MainActivity,
                courseDao = db.courseDao(),
                daysAhead = 7
            )
        }

        setContent {
            var currentThemeMode by remember {
                mutableStateOf(com.renly.rmf.ui.theme.ThemePreferences.getThemeMode(this@MainActivity))
            }

            RMFTheme(themeMode = currentThemeMode) {
                var showSplashScreen by remember { mutableStateOf(true) }
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Schedule) }

                var showQuickCaptureDialog by remember { mutableStateOf(false) }
                var dailyReportDate by remember { mutableStateOf(java.time.LocalDate.now()) }

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
                        if (!isInPipMode) {
                            Surface(
                                color = DarkSurface,
                                border = BorderStroke(1.dp, DarkBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                            NavigationBar(
                                containerColor = DarkSurface,
                                tonalElevation = 0.dp
                            ) {
                                val navItems = listOf(
                                    Screen.Schedule,
                                    Screen.Timetable,
                                    Screen.Study,
                                    Screen.Focus,
                                    Screen.Settings
                                )
                                navItems.forEach { screen ->
                                    val isSelected = currentScreen == screen
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = { currentScreen = screen },
                                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                                        label = {
                                            Text(
                                                screen.title,
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = DopamineBlue,
                                            selectedTextColor = DopamineBlue,
                                            unselectedIconColor = TextMuted,
                                            unselectedTextColor = TextMuted,
                                            indicatorColor = DopamineBlue.copy(alpha = 0.15f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(if (isInPipMode) androidx.compose.foundation.layout.PaddingValues(0.dp) else innerPadding),
                        color = DarkBg
                    ) {
                        when (currentScreen) {
                            Screen.Schedule -> ScheduleScreen(
                                schedulesFlow = db.scheduleDao().getAllActiveSchedules(),
                                onToggleStatus = { schedule ->
                                    lifecycleScope.launch {
                                        val newStatus = if (schedule.status == "COMPLETED") "PENDING" else "COMPLETED"
                                        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                                        db.scheduleDao().updateStatus(schedule.id, newStatus, nowStr)
                                    }
                                },
                                onAddSchedule = { title, workType, category, mins, startStr, endStr ->
                                    lifecycleScope.launch {
                                        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                                        val entity = ScheduleEntity(
                                            title = title,
                                            workType = workType,
                                            category = category,
                                            estimatedMinutes = mins,
                                            startTime = startStr,
                                            endTime = endStr,
                                            createdAt = nowStr,
                                            updatedAt = nowStr
                                        )
                                        db.scheduleDao().insertOrUpdate(entity)
                                        if (startStr.isNotBlank()) {
                                            com.renly.rmf.domain.service.ScheduleReminderManager.scheduleReminder(
                                                context = this@MainActivity,
                                                scheduleId = entity.id,
                                                title = entity.title,
                                                startTimeStr = entity.startTime,
                                                advanceMinutes = 10
                                            )
                                        }
                                    }
                                },
                                onUpdateSchedule = { schedule ->
                                    lifecycleScope.launch {
                                        db.scheduleDao().insertOrUpdate(schedule)
                                    }
                                },
                                onDeleteSchedule = { id ->
                                    lifecycleScope.launch {
                                        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                                        db.scheduleDao().softDelete(id, nowStr)
                                    }
                                },
                                onOpenQuickCapture = { showQuickCaptureDialog = true },
                                onNavigateToSummary = { currentScreen = Screen.Summary },
                                onNavigateToFitness = { currentScreen = Screen.Fitness }
                            )

                            Screen.Timetable -> TimetableScreen(
                                coursesFlow = db.courseDao().getAllActiveCourses(),
                                adjustmentsFlow = db.courseDao().getAllActiveAdjustments(),
                                onAddCourse = { course ->
                                    lifecycleScope.launch {
                                        db.courseDao().insertOrUpdateCourse(course)
                                        com.renly.rmf.domain.service.CourseReminderScheduler.scheduleUpcomingCourseReminders(
                                            context = this@MainActivity,
                                            courseDao = db.courseDao(),
                                            daysAhead = 7
                                        )
                                        Toast.makeText(this@MainActivity, "课程已保存，流体云上课提醒已就绪！", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onDeleteCourse = { id ->
                                    lifecycleScope.launch {
                                        db.courseDao().softDeleteCourse(id, LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                                        com.renly.rmf.domain.service.CourseReminderScheduler.scheduleUpcomingCourseReminders(
                                            context = this@MainActivity,
                                            courseDao = db.courseDao(),
                                            daysAhead = 7
                                        )
                                    }
                                },
                                onAddAdjustment = { adj ->
                                    lifecycleScope.launch {
                                        db.courseDao().insertOrUpdateAdjustment(adj)
                                        com.renly.rmf.domain.service.CourseReminderScheduler.scheduleUpcomingCourseReminders(
                                            context = this@MainActivity,
                                            courseDao = db.courseDao(),
                                            daysAhead = 7
                                        )
                                        Toast.makeText(this@MainActivity, "已保存调停课，流体云提醒已同步更新！", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onDeleteAdjustment = { id ->
                                    lifecycleScope.launch {
                                        db.courseDao().softDeleteAdjustment(id, LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                                        com.renly.rmf.domain.service.CourseReminderScheduler.scheduleUpcomingCourseReminders(
                                            context = this@MainActivity,
                                            courseDao = db.courseDao(),
                                            daysAhead = 7
                                        )
                                        Toast.makeText(this@MainActivity, "已撤销调停课记录", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onProjectToToday = {
                                    lifecycleScope.launch {
                                        val projectionService = com.renly.rmf.domain.service.TimetableProjectionService(db.courseDao())
                                        val today = java.time.LocalDate.now()
                                        val semesterStart = com.renly.rmf.domain.service.TimetablePreferences.getSemesterStartDate(this@MainActivity)
                                        val projected = projectionService.getProjectedCoursesForDate(today, semesterStart)
                                        if (projected.isEmpty()) {
                                            Toast.makeText(this@MainActivity, "今日无排期课程可供投影", Toast.LENGTH_SHORT).show()
                                        } else {
                                            for (slot in projected) {
                                                val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                                                val startStr = slot.startTime.format(DateTimeFormatter.ISO_DATE_TIME)
                                                val endStr = slot.endTime.format(DateTimeFormatter.ISO_DATE_TIME)
                                                val mins = java.time.temporal.ChronoUnit.MINUTES.between(slot.startTime, slot.endTime).toInt()
                                                val entity = ScheduleEntity(
                                                    title = "🎓 ${slot.name}",
                                                    description = "地点: ${slot.location} | 教师: ${slot.teacher}",
                                                    workType = "DEEP_WORK",
                                                    category = "STUDY",
                                                    colorHex = slot.colorHex,
                                                    estimatedMinutes = mins,
                                                    startTime = startStr,
                                                    endTime = endStr,
                                                    createdAt = nowStr,
                                                    updatedAt = nowStr
                                                )
                                                db.scheduleDao().insertOrUpdate(entity)
                                                com.renly.rmf.domain.service.ScheduleReminderManager.scheduleReminder(
                                                    context = this@MainActivity,
                                                    scheduleId = entity.id,
                                                    title = entity.title,
                                                    startTimeStr = entity.startTime,
                                                    advanceMinutes = 10
                                                )
                                            }
                                            Toast.makeText(this@MainActivity, "已成功将今日 ${projected.size} 门课程投影到时间块！", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onImportCourses = { courses ->
                                    lifecycleScope.launch {
                                        courses.forEach { course ->
                                            db.courseDao().insertOrUpdateCourse(course)
                                        }
                                        com.renly.rmf.domain.service.CourseReminderScheduler.scheduleUpcomingCourseReminders(
                                            context = this@MainActivity,
                                            courseDao = db.courseDao(),
                                            daysAhead = 7
                                        )
                                        Toast.makeText(this@MainActivity, "🎉 成功导入 ${courses.size} 门课程！", Toast.LENGTH_SHORT).show()
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
                                                .setAspectRatio(android.util.Rational(16, 9))
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
                                    lifecycleScope.launch { db.expenseDao().insertOrUpdate(expense) }
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
                                onBack = { currentScreen = Screen.Schedule }
                            )

                            Screen.Tutorial -> TutorialScreen()

                            Screen.Fitness -> FitnessScreen(
                                fitnessDao = db.fitnessDao(),
                                scheduleDao = db.scheduleDao(),
                                onBack = { currentScreen = Screen.Schedule },
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

                            Screen.Settings -> SettingsScreen(
                                currentThemeMode = currentThemeMode,
                                onThemeModeChange = { newMode ->
                                    currentThemeMode = newMode
                                    com.renly.rmf.ui.theme.ThemePreferences.setThemeMode(this@MainActivity, newMode)
                                },
                                onNavigateToGoals = { currentScreen = Screen.Goals },
                                onNavigateToExpenses = { currentScreen = Screen.Expenses },
                                onNavigateToFitness = { currentScreen = Screen.Fitness },
                                onNavigateToDailyReport = { currentScreen = Screen.DailyReport },
                                onNavigateToSummary = { currentScreen = Screen.Summary },
                                onNavigateToTutorial = { currentScreen = Screen.Tutorial },
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
                                }
                            )
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
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(22.dp))
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
    }
}

