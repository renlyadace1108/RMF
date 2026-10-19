package com.renly.rmf.domain.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.renly.rmf.RmfApplication
import com.renly.rmf.data.local.entity.TimeMarkerEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class QuickActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? RmfApplication ?: return
        val now = LocalDateTime.now()
        val today = LocalDate.now()
        val dateStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val nowIso = now.format(DateTimeFormatter.ISO_DATE_TIME)
        val timeLabel = now.format(DateTimeFormatter.ofPattern("HH:mm"))

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = app.database
                when (intent.action) {
                    ACTION_MARK_WAKE_UP -> {
                        val marker = TimeMarkerEntity(
                            id = UUID.randomUUID().toString(),
                            name = "起床打卡",
                            markerType = "POINT",
                            category = "ROUTINE",
                            icon = "🌅",
                            date = dateStr,
                            startTime = nowIso,
                            endTime = "",
                            colorHex = "#10B981", // 活力翡翠绿
                            notes = "通知栏快捷打卡 ($timeLabel)",
                            createdAt = nowIso,
                            updatedAt = nowIso
                        )
                        db.timeMarkerDao().insertOrUpdate(marker)
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, "🌅 已打卡「起床」: $timeLabel", Toast.LENGTH_SHORT).show()
                        }
                    }

                    ACTION_MARK_NAP -> {
                        // 预设计算 45 分钟午休区间
                        val endNap = now.plusMinutes(45)
                        val endIso = endNap.format(DateTimeFormatter.ISO_DATE_TIME)
                        val endLabel = endNap.format(DateTimeFormatter.ofPattern("HH:mm"))
                        val marker = TimeMarkerEntity(
                            id = UUID.randomUUID().toString(),
                            name = "午休充能",
                            markerType = "RANGE",
                            category = "REST",
                            icon = "🛌",
                            date = dateStr,
                            startTime = nowIso,
                            endTime = endIso,
                            colorHex = "#3B82F6", // 经典蓝
                            notes = "通知栏快捷打卡 ($timeLabel - $endLabel)",
                            createdAt = nowIso,
                            updatedAt = nowIso
                        )
                        db.timeMarkerDao().insertOrUpdate(marker)
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, "🛌 已打卡「午休」: $timeLabel ~ $endLabel", Toast.LENGTH_SHORT).show()
                        }
                    }

                    ACTION_MARK_SLEEP -> {
                        val marker = TimeMarkerEntity(
                            id = UUID.randomUUID().toString(),
                            name = "就寝打卡",
                            markerType = "POINT",
                            category = "ROUTINE",
                            icon = "💤",
                            date = dateStr,
                            startTime = nowIso,
                            endTime = "",
                            colorHex = "#8B5CF6", // 薰衣草紫
                            notes = "通知栏快捷打卡 ($timeLabel)",
                            createdAt = nowIso,
                            updatedAt = nowIso
                        )
                        db.timeMarkerDao().insertOrUpdate(marker)
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, "💤 已打卡「就寝」: $timeLabel", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_MARK_WAKE_UP = "com.renly.rmf.ACTION_MARK_WAKE_UP"
        const val ACTION_MARK_NAP = "com.renly.rmf.ACTION_MARK_NAP"
        const val ACTION_MARK_SLEEP = "com.renly.rmf.ACTION_MARK_SLEEP"
    }
}
