package com.renly.rmf.domain.service

import android.content.Context
import android.content.Intent
import com.google.gson.GsonBuilder
import com.renly.rmf.data.local.RmfDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DataExportService {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun exportAndShareAllData(context: Context, database: RmfDatabase) = withContext(Dispatchers.IO) {
        val schedules = database.scheduleDao().getAllRawSchedules().filter { it.isDeleted == 0 }
        val courses = database.courseDao().getAllRawCourses().filter { it.isDeleted == 0 }
        val topics = database.studyTopicDao().getAllRawTopics().filter { it.isDeleted == 0 }
        val goals = database.goalDao().getAllRawGoals().filter { it.isDeleted == 0 }
        val expenses = database.expenseDao().getAllRawExpenses().filter { it.isDeleted == 0 }

        val exportPayload = mapOf(
            "app" to "RMF (Resonance Moment Flow)",
            "version" to com.renly.rmf.BuildConfig.VERSION_NAME,
            "exportTime" to LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
            "schedules" to schedules,
            "courses" to courses,
            "studyTopics" to topics,
            "goals" to goals,
            "expenses" to expenses
        )

        val jsonString = gson.toJson(exportPayload)

        // 保存到临时文件
        val file = File(context.cacheDir, "rmf_export_${System.currentTimeMillis()}.json")
        file.writeText(jsonString)

        withContext(Dispatchers.Main) {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, jsonString)
                putExtra(Intent.EXTRA_SUBJECT, "RMF 全量数据备份与导出")
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, "分享或导出 RMF 数据")
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(shareIntent)
        }
    }
}
