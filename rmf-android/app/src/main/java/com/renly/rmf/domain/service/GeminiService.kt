package com.renly.rmf.domain.service

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

sealed class GeminiResult {
    data class Success(val responseText: String) : GeminiResult()
    data class Error(val errorMessage: String) : GeminiResult()
}

object GeminiService {

    private const val DEFAULT_MODEL = "gemini-1.5-flash"
    private val gson = Gson()

    suspend fun generateDailyReview(
        apiKey: String,
        completedCount: Int,
        totalSchedules: Int,
        focusMinutes: Int,
        interruptionCount: Int,
        studySummary: String,
        customNote: String = "",
        model: String = DEFAULT_MODEL
    ): GeminiResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext GeminiResult.Error("请先在设置中填写有效的 Gemini API Key。")
        }

        val prompt = """
            你是一名专业的精力管理与效率规划专家。请基于用户今天的实际执行数据，生成一份结构清晰、有洞察力的复盘总结报告：
            - 今日日程完成率: $completedCount / $totalSchedules
            - 专注总时长: $focusMinutes 分钟
            - 打断发生次数: $interruptionCount 次
            - 学习与技能进展: $studySummary
            - 用户今日个人心得: $customNote

            请按以下结构输出 Markdown:
            ### 🌟 今日高光与成就盘点
            ### ⚡ 精力与专注状态诊断
            ### 🎯 明日重心与节奏优化建议
        """.trimIndent()

        return@withContext callGeminiApi(apiKey, prompt, model)
    }

    suspend fun callGeminiApi(
        apiKey: String,
        promptText: String,
        model: String = DEFAULT_MODEL
    ): GeminiResult = withContext(Dispatchers.IO) {
        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val url = URL(endpoint)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 15000
                readTimeout = 25000
            }

            // 构建符合 Gemini 官方 API 规范的 JSON
            val requestBody = mapOf(
                "contents" to listOf(
                    mapOf(
                        "parts" to listOf(
                            mapOf("text" to promptText)
                        )
                    )
                )
            )

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(gson.toJson(requestBody))
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseJson = connection.inputStream.bufferedReader().use { it.readText() }
                val root = gson.fromJson(responseJson, JsonObject::class.java)
                val candidates = root.getAsJsonArray("candidates")
                if (candidates != null && candidates.size() > 0) {
                    val firstCandidate = candidates[0].asJsonObject
                    val contentObj = firstCandidate.getAsJsonObject("content")
                    val parts = contentObj.getAsJsonArray("parts")
                    val text = parts[0].asJsonObject.get("text").asString
                    return@withContext GeminiResult.Success(text)
                }
                return@withContext GeminiResult.Error("API 返回内容为空")
            } else {
                val errorJson = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                return@withContext GeminiResult.Error("请求失败: $errorJson")
            }
        } catch (e: Exception) {
            return@withContext GeminiResult.Error("连接 Gemini 异常: ${e.localizedMessage}")
        }
    }
}
