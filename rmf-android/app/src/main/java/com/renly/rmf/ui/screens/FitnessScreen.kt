package com.renly.rmf.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renly.rmf.data.local.dao.FitnessDao
import com.renly.rmf.data.local.dao.ScheduleDao
import com.renly.rmf.data.local.entity.FitnessPlanEntity
import com.renly.rmf.data.local.entity.FitnessRecordEntity
import com.renly.rmf.domain.fitness.ExerciseInfo
import com.renly.rmf.domain.fitness.ExerciseLibrary
import com.renly.rmf.domain.fitness.FitnessScheduleSyncManager
import com.renly.rmf.domain.fitness.WorkoutTemplate
import com.renly.rmf.domain.fitness.model.WorkoutSet
import com.renly.rmf.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitnessScreen(
    fitnessDao: FitnessDao,
    scheduleDao: ScheduleDao? = null,
    onBack: () -> Unit,
    onProjectToSchedule: (FitnessPlanEntity, List<FitnessRecordEntity>) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val dateStr = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

    val currentPlan by fitnessDao.observePlanByDate(dateStr).collectAsState(initial = null)
    val records by fitnessDao.observeRecordsByDate(dateStr).collectAsState(initial = emptyList())
    val completedDates by fitnessDao.observeCompletedDates().collectAsState(initial = emptyList())
    val recentPlans by fitnessDao.observeRecentPlans(20).collectAsState(initial = emptyList())

    // 震动服务
    val vibrator = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun triggerVibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(350)
            }
        } catch (_: Exception) {}
    }

    // 组间休息计时器状态
    var restTimerSeconds by remember { mutableIntStateOf(0) }
    var restTimerTotal by remember { mutableIntStateOf(90) }
    var isRestTimerRunning by remember { mutableStateOf(false) }

    LaunchedEffect(isRestTimerRunning, restTimerSeconds) {
        if (isRestTimerRunning && restTimerSeconds > 0) {
            delay(1000L)
            restTimerSeconds -= 1
            if (restTimerSeconds == 0) {
                isRestTimerRunning = false
                triggerVibrate()
            }
        }
    }

    fun startRestTimer(seconds: Int = 90) {
        restTimerTotal = seconds
        restTimerSeconds = seconds
        isRestTimerRunning = true
    }

    // 统计计算
    val allWorkoutSets = remember(records) {
        records.flatMap { it.getWorkoutSets() }
    }
    val totalVolumeKg = remember(records) {
        allWorkoutSets.filter { it.isCompleted }.sumOf { it.volumeKg }
    }
    val completedSetsCount = remember(allWorkoutSets) {
        allWorkoutSets.count { it.isCompleted }
    }
    val totalSetsCount = remember(allWorkoutSets) {
        allWorkoutSets.size
    }

    // 连续打卡天数计算
    val streakDays = remember(completedDates) {
        val dateSet = completedDates.toSet()
        var check = LocalDate.now()
        if (!dateSet.contains(check.format(DateTimeFormatter.ISO_LOCAL_DATE))) {
            check = check.minusDays(1)
        }
        var count = 0
        while (dateSet.contains(check.format(DateTimeFormatter.ISO_LOCAL_DATE))) {
            count++
            check = check.minusDays(1)
        }
        count
    }

    // 近7天运动统计
    val past7Days = remember { LocalDate.now().minusDays(6).format(DateTimeFormatter.ISO_LOCAL_DATE) }
    val pastWeekPlans by fitnessDao.observeCompletedPlansSince(past7Days).collectAsState(initial = emptyList())
    val weekWorkoutsCount = pastWeekPlans.size
    val weekTotalMins = pastWeekPlans.sumOf { if (it.actualDurationMinutes > 0) it.actualDurationMinutes else it.targetDurationMinutes }
    val weekTotalCalories = pastWeekPlans.sumOf { it.caloriesBurned }

    // 弹窗状态
    var showPlanDialog by remember { mutableStateOf(false) }
    var showExercisePicker by remember { mutableStateOf(false) }
    var showCustomExerciseDialog by remember { mutableStateOf(false) }
    var showFinishDialog by remember { mutableStateOf(false) }
    var showTemplatesDialog by remember { mutableStateOf(false) }

    val dayOfWeekName = when (selectedDate.dayOfWeek) {
        DayOfWeek.MONDAY -> "周一"
        DayOfWeek.TUESDAY -> "周二"
        DayOfWeek.WEDNESDAY -> "周三"
        DayOfWeek.THURSDAY -> "周四"
        DayOfWeek.FRIDAY -> "周五"
        DayOfWeek.SATURDAY -> "周六"
        DayOfWeek.SUNDAY -> "周日"
        else -> ""
    }

    // 自动同步至主页日程流
    fun triggerAutoSync(targetDateStr: String = dateStr) {
        if (scheduleDao != null) {
            coroutineScope.launch {
                FitnessScheduleSyncManager.syncFitnessPlanToSchedule(
                    fitnessDao = fitnessDao,
                    scheduleDao = scheduleDao,
                    dateStr = targetDateStr
                )
            }
        }
    }

    // 更新某动作的组列表与数据库持久化
    fun updateRecordSets(record: FitnessRecordEntity, newSets: List<WorkoutSet>) {
        val completedCount = newSets.count { it.isCompleted }
        val isAllCompleted = completedCount == newSets.size && newSets.isNotEmpty()
        val maxWeight = newSets.maxOfOrNull { it.weightKg } ?: 0.0
        val repsFirst = newSets.firstOrNull()?.reps ?: 10
        val updatedRecord = record.copy(
            setsData = WorkoutSet.toJsonArray(newSets),
            setsCount = newSets.size,
            repsPerSet = repsFirst,
            weightKg = maxWeight,
            isCompleted = if (isAllCompleted) 1 else 0,
            updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
        )
        coroutineScope.launch {
            fitnessDao.upsertRecord(updatedRecord)
            triggerAutoSync(record.recordDate)
        }
    }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🏋️ 每日健身规划", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showTemplatesDialog = true }) {
                        Icon(Icons.Default.Bolt, contentDescription = "分化模版", tint = DopamineAmber)
                    }
                    IconButton(onClick = {
                        val planToProject = currentPlan ?: FitnessPlanEntity(
                            id = UUID.randomUUID().toString(),
                            planDate = dateStr,
                            title = "每日专业体能训练",
                            workoutType = "STRENGTH",
                            targetDurationMinutes = 45,
                            createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
                            updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                        )
                        coroutineScope.launch {
                            fitnessDao.upsertPlan(planToProject)
                            triggerAutoSync(dateStr)
                            onProjectToSchedule(planToProject, records)
                        }
                    }) {
                        Icon(Icons.Default.CalendarToday, contentDescription = "投影日程", tint = DopamineCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        bottomBar = {
            // 组间休息浮动计时器条 (类似 Hevy 悬浮计时器)
            AnimatedVisibility(
                visible = restTimerSeconds > 0,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Surface(
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DopamineGreen.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = DopamineGreen.copy(alpha = 0.2f),
                                    shape = CircleShape,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("⏱", fontSize = 16.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("组间休息中", fontSize = 11.sp, color = TextSecondary)
                                    val minutes = restTimerSeconds / 60
                                    val seconds = restTimerSeconds % 60
                                    Text(
                                        text = "%02d:%02d".format(minutes, seconds),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DopamineGreen
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = DarkCard,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, DarkBorder),
                                    modifier = Modifier.clickable {
                                        restTimerSeconds = (restTimerSeconds - 15).coerceAtLeast(0)
                                    }
                                ) {
                                    Text("-15s", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                                Surface(
                                    color = DarkCard,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, DarkBorder),
                                    modifier = Modifier.clickable {
                                        restTimerSeconds += 30
                                        restTimerTotal += 30
                                    }
                                ) {
                                    Text("+30s", fontSize = 11.sp, color = DopamineCyan, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                                Surface(
                                    color = DopamineGreen.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, DopamineGreen),
                                    modifier = Modifier.clickable {
                                        isRestTimerRunning = !isRestTimerRunning
                                    }
                                ) {
                                    Text(
                                        text = if (isRestTimerRunning) "暂停" else "继续",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DopamineGreen,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Surface(
                                    color = DarkCard,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, DarkBorder),
                                    modifier = Modifier.clickable {
                                        restTimerSeconds = 0
                                        isRestTimerRunning = false
                                    }
                                ) {
                                    Text("跳过", fontSize = 11.sp, color = TextMuted, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        val progress = if (restTimerTotal > 0) restTimerSeconds.toFloat() / restTimerTotal.toFloat() else 0f
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = DopamineGreen,
                            trackColor = DarkBorder
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. 日期切换条
            item {
                Spacer(modifier = Modifier.height(2.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    border = BorderStroke(1.dp, DarkBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedDate = selectedDate.minusDays(1) }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "前一天", tint = TextPrimary)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${selectedDate.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))} · $dayOfWeekName",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = DopamineCyan
                            )
                            if (selectedDate != LocalDate.now()) {
                                Text(
                                    text = "点击回到今天",
                                    fontSize = 11.sp,
                                    color = DopamineAmber,
                                    modifier = Modifier.clickable { selectedDate = LocalDate.now() }
                                )
                            }
                        }

                        IconButton(onClick = { selectedDate = selectedDate.plusDays(1) }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "后一天", tint = TextPrimary)
                        }
                    }
                }
            }

            // 2. 专业 KPI 指标仪表盘 (总训练容量, 完成组数, 连续打卡)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 今日总容量
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        border = BorderStroke(1.dp, DarkBorder),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("总训练容量", fontSize = 11.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(2.dp))
                            val volStr = if (totalVolumeKg >= 1000) "%.1fk".format(totalVolumeKg / 1000.0) else "%.0f".format(totalVolumeKg)
                            Text("⚡ $volStr kg", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DopamineCyan)
                        }
                    }

                    // 组数进度
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        border = BorderStroke(1.dp, DarkBorder),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("完成组数", fontSize = 11.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(2.dp))
                            val setProgressColor = if (totalSetsCount > 0 && completedSetsCount == totalSetsCount) DopamineGreen else DopaminePurple
                            Text("✓ $completedSetsCount/$totalSetsCount 组", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = setProgressColor)
                        }
                    }

                    // 连续打卡
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        border = BorderStroke(1.dp, DarkBorder),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(0.9f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("连续运动", fontSize = 11.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("🔥 $streakDays 天", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DopamineAmber)
                        }
                    }
                }
            }

            // 3. 今日训练主题卡片与核心操作
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    border = BorderStroke(1.dp, DarkBorder),
                    shape = RoundedCornerShape(12.dp),
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
                                    text = currentPlan?.title ?: "今日未定训练分化",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val typeTag = when (currentPlan?.workoutType) {
                                        "CARDIO" -> "有氧耐力"
                                        "HIIT" -> "HIIT燃脂"
                                        "STRETCH" -> "拉伸放松"
                                        else -> "力量抗阻"
                                    }
                                    Surface(
                                        color = DopamineCyan.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(typeTag, fontSize = 10.sp, color = DopamineCyan, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                    }
                                    Text(
                                        text = "目标 ${currentPlan?.targetDurationMinutes ?: 45} 分钟",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            // 状态标签
                            val isCompleted = currentPlan?.status == "COMPLETED"
                            Surface(
                                color = if (isCompleted) DopamineGreen.copy(alpha = 0.2f) else DopamineAmber.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, if (isCompleted) DopamineGreen else DopamineAmber),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (isCompleted) "已打卡完成" else "训练规划中",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCompleted) DopamineGreen else DopamineAmber,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (!currentPlan?.notes.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "📝 ${currentPlan?.notes}",
                                fontSize = 11.5.sp,
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 操作按钮组
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showExercisePicker = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = DopamineCyan)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("添加动作", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }

                            OutlinedButton(
                                onClick = { showPlanDialog = true },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, DarkBorder)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextPrimary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("修改计划", fontSize = 12.5.sp, color = TextPrimary)
                            }

                            Button(
                                onClick = { showFinishDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = DopamineGreen)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("打卡结算", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }

            // 4. 动作列表 Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📋 训练动作与按组记录", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            color = DarkCard,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier.clickable { startRestTimer(60) }
                        ) {
                            Text("⏱ 60s", fontSize = 10.5.sp, color = DopamineGreen, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                        Surface(
                            color = DarkCard,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier.clickable { startRestTimer(90) }
                        ) {
                            Text("⏱ 90s", fontSize = 10.5.sp, color = DopamineGreen, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                        Surface(
                            color = DarkCard,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier.clickable { startRestTimer(120) }
                        ) {
                            Text("⏱ 120s", fontSize = 10.5.sp, color = DopamineGreen, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }

            // 5. 专业按组记录动作卡片 (参考 Hevy / Strong / 训记)
            if (records.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        border = BorderStroke(1.dp, DarkBorder),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🏋️", fontSize = 34.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("今日暂无动作安排", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("点击右上角「⚡」导入经典推拉腿模版，或点击「添加动作」从权威动作库选取！", fontSize = 11.5.sp, color = TextSecondary, textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                items(records, key = { it.id }) { rec ->
                    WorkoutExerciseCard(
                        record = rec,
                        onUpdateSets = { newSets -> updateRecordSets(rec, newSets) },
                        onDeleteRecord = {
                            coroutineScope.launch {
                                fitnessDao.deleteRecord(rec.id, LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                                triggerAutoSync(rec.recordDate)
                            }
                        },
                        onTriggerRestTimer = { seconds -> startRestTimer(seconds) }
                    )
                }
            }

            // 6. 历史训练日志与周度概览
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📜 近期训练日志", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("本周: $weekWorkoutsCount 次 · ${weekTotalMins}m · ${weekTotalCalories.toInt()}kcal", fontSize = 11.sp, color = DopaminePurple)
                }
            }

            if (recentPlans.isEmpty()) {
                item {
                    Text("暂无历史记录，开启今日健身吧！", fontSize = 12.sp, color = TextSecondary)
                }
            } else {
                items(recentPlans) { plan ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        border = BorderStroke(1.dp, DarkBorder),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    selectedDate = LocalDate.parse(plan.planDate)
                                } catch (_: Exception) {}
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = plan.planDate,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DopamineCyan
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = plan.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                val usedMins = if (plan.actualDurationMinutes > 0) plan.actualDurationMinutes else plan.targetDurationMinutes
                                Text(
                                    text = "耗时 $usedMins 分钟 · 消耗 ~${plan.caloriesBurned.toInt()} kcal · 感受: ${getFeelingLabel(plan.feeling)}",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }

                            val isDone = plan.status == "COMPLETED"
                            Surface(
                                color = if (isDone) DopamineGreen.copy(alpha = 0.15f) else DarkBorder,
                                shape = CircleShape,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isDone) {
                                        Text("✓", fontSize = 12.sp, color = DopamineGreen, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text("·", fontSize = 14.sp, color = TextMuted)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // =========================================================================
    // 弹窗 1: 定制今日计划
    // =========================================================================
    if (showPlanDialog) {
        var planTitle by remember { mutableStateOf(currentPlan?.title ?: "全身活力塑形") }
        var targetMins by remember { mutableStateOf((currentPlan?.targetDurationMinutes ?: 45).toString()) }
        var planType by remember { mutableStateOf(currentPlan?.workoutType ?: "STRENGTH") }
        var planNotes by remember { mutableStateOf(currentPlan?.notes ?: "") }

        AlertDialog(
            onDismissRequest = { showPlanDialog = false },
            title = { Text("定制健身计划", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = planTitle,
                        onValueChange = { planTitle = it },
                        label = { Text("计划主题 / 分化名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = targetMins,
                        onValueChange = { targetMins = it },
                        label = { Text("目标时长 (分钟)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = planNotes,
                        onValueChange = { planNotes = it },
                        label = { Text("要点提示 / 目标") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val mins = targetMins.toIntOrNull() ?: 45
                    val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                    val updated = (currentPlan ?: FitnessPlanEntity(
                        id = UUID.randomUUID().toString(),
                        planDate = dateStr,
                        createdAt = nowStr,
                        updatedAt = nowStr
                    )).copy(
                        title = planTitle.ifBlank { "每日健身训练" },
                        workoutType = planType,
                        targetDurationMinutes = mins,
                        notes = planNotes,
                        updatedAt = nowStr
                    )
                    coroutineScope.launch {
                        fitnessDao.upsertPlan(updated)
                        triggerAutoSync(dateStr)
                    }
                    showPlanDialog = false
                }) {
                    Text("保存计划")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPlanDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // =========================================================================
    // 弹窗 2: 专业动作库选择器 (Hevy 级别 60+ 动作与分类筛选)
    // =========================================================================
    if (showExercisePicker) {
        ExercisePickerModal(
            onDismiss = { showExercisePicker = false },
            onSelectExercise = { exInfo ->
                val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                coroutineScope.launch {
                    var plan = currentPlan
                    if (plan == null) {
                        plan = FitnessPlanEntity(
                            id = UUID.randomUUID().toString(),
                            planDate = dateStr,
                            title = "${exInfo.categoryName}专项目标训练",
                            workoutType = if (exInfo.category == "CARDIO") "CARDIO" else "STRENGTH",
                            createdAt = nowStr,
                            updatedAt = nowStr
                        )
                        fitnessDao.upsertPlan(plan)
                    }

                    // 创建 Hevy 风格的多组初始结构 (第 1 组为热身组 W，其余为正式组 N)
                    val initialSets = (1..exInfo.defaultSets).map { idx ->
                        WorkoutSet(
                            setNumber = idx,
                            type = if (idx == 1 && exInfo.defaultWeightKg > 20) "W" else "N",
                            weightKg = if (idx == 1 && exInfo.defaultWeightKg > 20) (exInfo.defaultWeightKg * 0.6).toInt().toDouble() else exInfo.defaultWeightKg,
                            reps = exInfo.defaultReps,
                            isCompleted = false
                        )
                    }

                    val newRecord = FitnessRecordEntity(
                        id = UUID.randomUUID().toString(),
                        planId = plan.id,
                        recordDate = dateStr,
                        exerciseName = exInfo.name,
                        category = exInfo.category,
                        equipment = exInfo.equipment,
                        setsData = WorkoutSet.toJsonArray(initialSets),
                        setsCount = exInfo.defaultSets,
                        repsPerSet = exInfo.defaultReps,
                        weightKg = exInfo.defaultWeightKg,
                        sortOrder = records.size,
                        caloriesBurned = 70.0,
                        note = exInfo.tips,
                        createdAt = nowStr,
                        updatedAt = nowStr
                    )
                    fitnessDao.upsertRecord(newRecord)
                    triggerAutoSync(dateStr)
                }
                showExercisePicker = false
            },
            onAddCustom = {
                showExercisePicker = false
                showCustomExerciseDialog = true
            }
        )
    }

    // =========================================================================
    // 弹窗 3: 自定义动作录入
    // =========================================================================
    if (showCustomExerciseDialog) {
        var customName by remember { mutableStateOf("") }
        var customCat by remember { mutableStateOf("CHEST") }
        var customEquip by remember { mutableStateOf("哑铃") }
        var customSets by remember { mutableStateOf("4") }
        var customReps by remember { mutableStateOf("10") }
        var customWeight by remember { mutableStateOf("20") }
        var customNote by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCustomExerciseDialog = false },
            title = { Text("➕ 添加自定义动作", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("动作名称 (如: 哈克深蹲)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 类别选择
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("CHEST" to "胸", "BACK" to "背", "LEGS" to "腿", "SHOULDERS" to "肩", "ARMS" to "臂", "CORE" to "核心", "CARDIO" to "有氧").forEach { (cat, label) ->
                            val isSel = customCat == cat
                            Surface(
                                color = if (isSel) DopamineCyan.copy(alpha = 0.2f) else DarkCard,
                                border = BorderStroke(1.dp, if (isSel) DopamineCyan else DarkBorder),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { customCat = cat }
                            ) {
                                Box(modifier = Modifier.padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                                    Text(label, fontSize = 10.5.sp, color = if (isSel) DopamineCyan else TextSecondary)
                                }
                            }
                        }
                    }

                    // 器械选择
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("杠铃", "哑铃", "绳索", "器械", "自重").forEach { eq ->
                            val isSel = customEquip == eq
                            Surface(
                                color = if (isSel) DopamineAmber.copy(alpha = 0.2f) else DarkCard,
                                border = BorderStroke(1.dp, if (isSel) DopamineAmber else DarkBorder),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { customEquip = eq }
                            ) {
                                Box(modifier = Modifier.padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                                    Text(eq, fontSize = 10.5.sp, color = if (isSel) DopamineAmber else TextSecondary)
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customSets,
                            onValueChange = { customSets = it },
                            label = { Text("组数") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = customReps,
                            onValueChange = { customReps = it },
                            label = { Text("每组次数") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = customWeight,
                            onValueChange = { customWeight = it },
                            label = { Text("重量 (kg)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = customNote,
                        onValueChange = { customNote = it },
                        label = { Text("动作要点提示") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (customName.isNotBlank()) {
                        val sets = customSets.toIntOrNull() ?: 4
                        val reps = customReps.toIntOrNull() ?: 10
                        val weight = customWeight.toDoubleOrNull() ?: 0.0
                        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)

                        coroutineScope.launch {
                            var plan = currentPlan
                            if (plan == null) {
                                plan = FitnessPlanEntity(
                                    id = UUID.randomUUID().toString(),
                                    planDate = dateStr,
                                    title = "定制训练规划",
                                    workoutType = if (customCat == "CARDIO") "CARDIO" else "STRENGTH",
                                    createdAt = nowStr,
                                    updatedAt = nowStr
                                )
                                fitnessDao.upsertPlan(plan)
                            }

                            val initialSets = (1..sets).map { idx ->
                                WorkoutSet(
                                    setNumber = idx,
                                    type = if (idx == 1 && weight > 20) "W" else "N",
                                    weightKg = weight,
                                    reps = reps,
                                    isCompleted = false
                                )
                            }

                            val newRecord = FitnessRecordEntity(
                                id = UUID.randomUUID().toString(),
                                planId = plan.id,
                                recordDate = dateStr,
                                exerciseName = customName.trim(),
                                category = customCat,
                                equipment = customEquip,
                                setsData = WorkoutSet.toJsonArray(initialSets),
                                setsCount = sets,
                                repsPerSet = reps,
                                weightKg = weight,
                                sortOrder = records.size,
                                caloriesBurned = 60.0,
                                note = customNote.trim(),
                                createdAt = nowStr,
                                updatedAt = nowStr
                            )
                            fitnessDao.upsertRecord(newRecord)
                            triggerAutoSync(dateStr)
                        }
                        showCustomExerciseDialog = false
                    }
                }) {
                    Text("添加到今日")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomExerciseDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // =========================================================================
    // 弹窗 4: 完成打卡结算
    // =========================================================================
    if (showFinishDialog) {
        var actualMins by remember { mutableStateOf((currentPlan?.actualDurationMinutes.takeIf { (it ?: 0) > 0 } ?: currentPlan?.targetDurationMinutes ?: 45).toString()) }
        val sumCals = records.sumOf { it.caloriesBurned }.takeIf { it > 0 } ?: (currentPlan?.caloriesBurned.takeIf { (it ?: 0.0) > 0.0 } ?: 320.0)
        var actualCalories by remember { mutableStateOf(sumCals.toInt().toString()) }
        var feeling by remember { mutableStateOf(currentPlan?.feeling ?: "MODERATE") }
        var finishNotes by remember { mutableStateOf(currentPlan?.notes ?: "") }

        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text("🎉 今日训练打卡结算", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 数据概览展示
                    Surface(
                        color = DarkCard,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("总容量", fontSize = 11.sp, color = TextSecondary)
                                Text("%.0f kg".format(totalVolumeKg), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DopamineCyan)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("完成组数", fontSize = 11.sp, color = TextSecondary)
                                Text("$completedSetsCount/$totalSetsCount 组", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DopamineGreen)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("动作数", fontSize = 11.sp, color = TextSecondary)
                                Text("${records.size} 项", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DopaminePurple)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = actualMins,
                            onValueChange = { actualMins = it },
                            label = { Text("用时 (分钟)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = actualCalories,
                            onValueChange = { actualCalories = it },
                            label = { Text("消耗 (kcal)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text("训练感受评价：", fontSize = 12.sp, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val feelings = listOf("EASY" to "😊 轻松", "MODERATE" to "👍 适中", "HARD" to "🔥 爆裂")
                        feelings.forEach { (tag, label) ->
                            val isSel = feeling == tag
                            Surface(
                                color = if (isSel) DopamineAmber.copy(alpha = 0.25f) else DarkCard,
                                border = BorderStroke(1.dp, if (isSel) DopamineAmber else DarkBorder),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { feeling = tag }
                            ) {
                                Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(label, fontSize = 11.5.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, color = if (isSel) DopamineAmber else TextPrimary)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = finishNotes,
                        onValueChange = { finishNotes = it },
                        label = { Text("训练心得与身体反馈") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mins = actualMins.toIntOrNull() ?: 45
                        val cal = actualCalories.toDoubleOrNull() ?: 300.0
                        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)

                        coroutineScope.launch {
                            val plan = (currentPlan ?: FitnessPlanEntity(
                                id = UUID.randomUUID().toString(),
                                planDate = dateStr,
                                title = "今日训练",
                                createdAt = nowStr,
                                updatedAt = nowStr
                            )).copy(
                                status = "COMPLETED",
                                actualDurationMinutes = mins,
                                caloriesBurned = cal,
                                feeling = feeling,
                                notes = finishNotes,
                                updatedAt = nowStr
                            )
                            fitnessDao.upsertPlan(plan)
                            // 标记所有动作为已完成
                            records.forEach { r ->
                                val sets = r.getWorkoutSets().map { it.copy(isCompleted = true) }
                                updateRecordSets(r, sets)
                            }
                            triggerAutoSync(dateStr)
                        }
                        showFinishDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DopamineGreen)
                ) {
                    Text("确认打卡", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // =========================================================================
    // 弹窗 5: 经典分化训练模版选择 (PPL 推拉腿、上下肢二分化、减脂等)
    // =========================================================================
    if (showTemplatesDialog) {
        AlertDialog(
            onDismissRequest = { showTemplatesDialog = false },
            title = { Text("⚡ 经典分化训练模版", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
                ) {
                    item {
                        Text("一键载入权威训练分化，按组结构自动展开：", fontSize = 11.5.sp, color = TextSecondary)
                    }

                    items(ExerciseLibrary.TEMPLATES) { tpl ->
                        WorkoutTemplateItem(
                            template = tpl,
                            onClick = {
                                applyWorkoutTemplate(
                                    template = tpl,
                                    dateStr = dateStr,
                                    currentPlan = currentPlan,
                                    fitnessDao = fitnessDao,
                                    scheduleDao = scheduleDao,
                                    coroutineScope = coroutineScope
                                )
                                showTemplatesDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTemplatesDialog = false }) {
                    Text("关闭", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}

/**
 * 单个动作卡片：含按组记录表格、重量/次数调整、完成打勾、自动算容量与 1RM
 */
@Composable
private fun WorkoutExerciseCard(
    record: FitnessRecordEntity,
    onUpdateSets: (List<WorkoutSet>) -> Unit,
    onDeleteRecord: () -> Unit,
    onTriggerRestTimer: (Int) -> Unit
) {
    val sets = remember(record.setsData, record.setsCount, record.repsPerSet, record.weightKg) {
        record.getWorkoutSets()
    }
    val catColor = getCategoryColor(record.category)

    // 单动作最佳估算 1RM
    val best1RM = remember(sets) {
        sets.maxOfOrNull { it.estimated1RM } ?: 0.0
    }
    // 单动作完成容量
    val exerciseVolume = remember(sets) {
        sets.filter { it.isCompleted }.sumOf { it.volumeKg }
    }
    val isCompleted = sets.isNotEmpty() && sets.all { it.isCompleted }

    Card(
        colors = CardDefaults.cardColors(containerColor = if (isCompleted) DarkCard.copy(alpha = 0.75f) else DarkCard),
        border = BorderStroke(1.dp, if (isCompleted) DopamineGreen.copy(alpha = 0.5f) else DarkBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    // 肌肉部位标签
                    Surface(
                        color = catColor.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, catColor),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = getCategoryName(record.category),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = catColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    // 器械标签
                    if (record.equipment.isNotBlank()) {
                        Surface(
                            color = DarkSurface,
                            border = BorderStroke(1.dp, DarkBorder),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = record.equipment,
                                fontSize = 9.5.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = record.exerciseName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCompleted) TextSecondary else TextPrimary,
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDeleteRecord,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "删除动作", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }

            // 统计指标胶囊 (1RM & Volume)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (best1RM > 0) {
                    Text(
                        text = "估算 1RM: %.1fkg".format(best1RM),
                        fontSize = 10.5.sp,
                        color = DopamineCyan
                    )
                }
                if (exerciseVolume > 0) {
                    Text(
                        text = "容量: %.0fkg".format(exerciseVolume),
                        fontSize = 10.5.sp,
                        color = DopamineAmber
                    )
                }
            }

            if (record.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "💡 ${record.note}",
                    fontSize = 10.5.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 表格头部
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("组号", fontSize = 10.sp, color = TextMuted, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                Text("类型", fontSize = 10.sp, color = TextMuted, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                Text("重量(KG)", fontSize = 10.sp, color = TextMuted, modifier = Modifier.weight(1.3f), textAlign = TextAlign.Center)
                Text("次数", fontSize = 10.sp, color = TextMuted, modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center)
                Text("完成", fontSize = 10.sp, color = TextMuted, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 每组数据行
            sets.forEachIndexed { index, s ->
                WorkoutSetRow(
                    set = s,
                    isLast = index == sets.size - 1,
                    onUpdate = { updatedSet ->
                        val newSets = sets.toMutableList()
                        newSets[index] = updatedSet
                        onUpdateSets(newSets)
                        // 若勾选完成，自动触发组间休息计时器
                        if (!s.isCompleted && updatedSet.isCompleted) {
                            val restTime = if (s.type == "W") 60 else if (record.category in listOf("CHEST", "BACK", "LEGS")) 90 else 60
                            onTriggerRestTimer(restTime)
                        }
                    },
                    onDelete = if (sets.size > 1) {
                        {
                            val newSets = sets.toMutableList()
                            newSets.removeAt(index)
                            // 重新编号
                            val reindexed = newSets.mapIndexed { idx, item -> item.copy(setNumber = idx + 1) }
                            onUpdateSets(reindexed)
                        }
                    } else null
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 添加组操作按键
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = DarkCardHover,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.clickable {
                        val lastSet = sets.lastOrNull()
                        val newSet = WorkoutSet(
                            setNumber = sets.size + 1,
                            type = "N",
                            weightKg = lastSet?.weightKg ?: record.weightKg,
                            reps = lastSet?.reps ?: record.repsPerSet,
                            isCompleted = false
                        )
                        onUpdateSets(sets + newSet)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = DopamineCyan, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加一组", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = DopamineCyan)
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { onTriggerRestTimer(90) }
                    ) {
                        Text("⏱ 休息90s", fontSize = 10.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }
}

/**
 * 单组输入行：组号、类型切换、重量步进编辑、次数步进编辑、完成打勾
 */
@Composable
private fun WorkoutSetRow(
    set: WorkoutSet,
    isLast: Boolean,
    onUpdate: (WorkoutSet) -> Unit,
    onDelete: (() -> Unit)?
) {
    val isDone = set.isCompleted
    val rowBg = if (isDone) DopamineGreen.copy(alpha = 0.12f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(rowBg)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. 组号
        Text(
            text = "${set.setNumber}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDone) DopamineGreen else TextPrimary,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.Center
        )

        // 2. 组类型切换 (W / N / D / F)
        val (badgeBg, badgeTextColor) = when (set.type) {
            "W" -> DopamineAmber.copy(alpha = 0.2f) to DopamineAmber
            "D" -> DopaminePurple.copy(alpha = 0.2f) to DopaminePurple
            "F" -> DopamineRed.copy(alpha = 0.2f) to DopamineRed
            else -> DarkSurface to TextSecondary
        }
        Surface(
            color = badgeBg,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .width(36.dp)
                .clickable {
                    // 点击循环切换组类型
                    val nextType = when (set.type) {
                        "N" -> "W"
                        "W" -> "D"
                        "D" -> "F"
                        "F" -> "N"
                        else -> "N"
                    }
                    onUpdate(set.copy(type = nextType))
                }
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 2.dp)) {
                Text(set.typeLabel, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = badgeTextColor)
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // 3. 重量调节 (kg)
        Row(
            modifier = Modifier.weight(1.3f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "-",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .clickable {
                        val newWeight = (set.weightKg - 2.5).coerceAtLeast(0.0)
                        onUpdate(set.copy(weightKg = newWeight))
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            Surface(
                color = DarkSurface,
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                Text(
                    text = if (set.weightKg % 1.0 == 0.0) "%.0f".format(set.weightKg) else "%.1f".format(set.weightKg),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    textAlign = TextAlign.Center
                )
            }
            Text(
                text = "+",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .clickable {
                        onUpdate(set.copy(weightKg = set.weightKg + 2.5))
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        // 4. 次数调节 (reps)
        Row(
            modifier = Modifier.weight(1.2f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "-",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .clickable {
                        val newReps = (set.reps - 1).coerceAtLeast(1)
                        onUpdate(set.copy(reps = newReps))
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            Surface(
                color = DarkSurface,
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                Text(
                    text = "${set.reps}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    textAlign = TextAlign.Center
                )
            }
            Text(
                text = "+",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .clickable {
                        onUpdate(set.copy(reps = set.reps + 1))
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        // 5. 完成打勾按键
        Box(
            modifier = Modifier.width(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = if (isDone) DopamineGreen else DarkSurface,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, if (isDone) DopamineGreen else DarkBorder),
                modifier = Modifier
                    .size(22.dp)
                    .clickable {
                        onUpdate(set.copy(isCompleted = !isDone))
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isDone) {
                        Text("✓", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * 专业动作库选择模态弹窗 (搜索 + 分类 Chips + 器械标签 + 快捷选取)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePickerModal(
    onDismiss: () -> Unit,
    onSelectExercise: (ExerciseInfo) -> Unit,
    onAddCustom: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }

    val filteredList = remember(searchQuery, selectedCategory) {
        ExerciseLibrary.search(searchQuery, selectedCategory)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📚 权威训练动作库", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                Surface(
                    color = DopamineCyan.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.clickable(onClick = onAddCustom)
                ) {
                    Text("+ 自定义", fontSize = 11.sp, color = DopamineCyan, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
            ) {
                // 搜索框
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索 60+ 动作 (如: 卧推、硬拉、深蹲)", fontSize = 12.sp) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 分类水平滑动栏
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(ExerciseLibrary.CATEGORIES) { (catKey, catName) ->
                        val isSel = selectedCategory == catKey
                        Surface(
                            color = if (isSel) DopamineCyan.copy(alpha = 0.2f) else DarkCard,
                            border = BorderStroke(1.dp, if (isSel) DopamineCyan else DarkBorder),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable { selectedCategory = catKey }
                        ) {
                            Text(
                                text = catName,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) DopamineCyan else TextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 动作列表
                if (filteredList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("未找到匹配动作", fontSize = 13.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(onClick = onAddCustom, colors = ButtonDefaults.buttonColors(containerColor = DopamineCyan)) {
                                Text("立即创建自定义动作", fontSize = 12.sp, color = Color.Black)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredList, key = { it.id }) { ex ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkCard),
                                border = BorderStroke(1.dp, DarkBorder),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectExercise(ex) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(ex.name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = DarkSurface,
                                                shape = RoundedCornerShape(3.dp),
                                                border = BorderStroke(1.dp, DarkBorder)
                                            ) {
                                                Text(ex.equipment, fontSize = 9.sp, color = DopamineAmber, modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(ex.englishName, fontSize = 10.5.sp, color = TextMuted)
                                        if (ex.tips.isNotBlank()) {
                                            Text("💡 ${ex.tips}", fontSize = 10.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }

                                    Surface(
                                        color = DopamineCyan.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.clickable { onSelectExercise(ex) }
                                    ) {
                                        Text("+ 选取", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = DopamineCyan, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        },
        containerColor = DarkSurface
    )
}

/**
 * 训练分化模版卡片项
 */
@Composable
private fun WorkoutTemplateItem(
    template: WorkoutTemplate,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, DopamineAmber.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(template.title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("${template.targetDurationMinutes}m · ${template.exercises.size}动作", fontSize = 11.sp, color = DopamineAmber, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(template.subtitle, fontSize = 11.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "包含: " + template.exercises.joinToString(" · ") { it.name },
                fontSize = 10.sp,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 将分化模版直接展开并应用到指定日期
 */
private fun applyWorkoutTemplate(
    template: WorkoutTemplate,
    dateStr: String,
    currentPlan: FitnessPlanEntity?,
    fitnessDao: FitnessDao,
    scheduleDao: ScheduleDao?,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    val nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
    coroutineScope.launch {
        val plan = (currentPlan ?: FitnessPlanEntity(
            id = UUID.randomUUID().toString(),
            planDate = dateStr,
            createdAt = nowStr,
            updatedAt = nowStr
        )).copy(
            title = template.title,
            workoutType = template.workoutType,
            targetDurationMinutes = template.targetDurationMinutes,
            notes = template.subtitle,
            updatedAt = nowStr
        )
        fitnessDao.upsertPlan(plan)

        template.exercises.forEachIndexed { idx, tplEx ->
            val record = FitnessRecordEntity(
                id = UUID.randomUUID().toString(),
                planId = plan.id,
                recordDate = dateStr,
                exerciseName = tplEx.name,
                category = tplEx.category,
                equipment = tplEx.equipment,
                setsData = WorkoutSet.toJsonArray(tplEx.sets),
                setsCount = tplEx.sets.size,
                repsPerSet = tplEx.sets.firstOrNull()?.reps ?: 10,
                weightKg = tplEx.sets.maxOfOrNull { it.weightKg } ?: 0.0,
                sortOrder = idx,
                caloriesBurned = tplEx.calories,
                createdAt = nowStr,
                updatedAt = nowStr
            )
            fitnessDao.upsertRecord(record)
        }

        if (scheduleDao != null) {
            FitnessScheduleSyncManager.syncFitnessPlanToSchedule(
                fitnessDao = fitnessDao,
                scheduleDao = scheduleDao,
                dateStr = dateStr
            )
        }
    }
}

@Composable
private fun getCategoryColor(category: String): Color {
    return when (category.uppercase()) {
        "CHEST" -> DopamineRed
        "BACK" -> DopamineBlue
        "LEGS" -> DopamineGreen
        "SHOULDERS" -> DopamineAmber
        "ARMS" -> DopaminePurple
        "CORE" -> DopaminePink
        "CARDIO" -> DopamineCyan
        else -> DopamineGreen
    }
}

private fun getCategoryName(category: String): String {
    return when (category.uppercase()) {
        "CHEST" -> "胸部"
        "BACK" -> "背部"
        "LEGS" -> "腿臀"
        "SHOULDERS" -> "肩部"
        "ARMS" -> "手臂"
        "CORE" -> "核心"
        "CARDIO" -> "有氧"
        "STRETCH" -> "拉伸"
        else -> "其他"
    }
}

private fun getFeelingLabel(feeling: String): String {
    return when (feeling) {
        "EASY" -> "😊 轻松"
        "HARD" -> "🔥 爆裂"
        else -> "👍 适中"
    }
}
