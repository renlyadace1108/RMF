package com.renly.rmf.domain.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.renly.rmf.data.local.entity.CourseEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID

sealed class AiResult<out T> {
    data class Success<T>(val data: T, val rawResponse: String = "") : AiResult<T>()
    data class Error(val errorMessage: String) : AiResult<Nothing>()
}

object AiService {

    private val gson = Gson()

    // 默认高辨识度课程配色方案 (莫兰迪/Material Palette)
    private val COURSE_PALETTE = listOf(
        "#3B82F6", // 科技蓝
        "#10B981", // 翡翠绿
        "#F59E0B", // 琥珀黄
        "#EF4444", // 珊瑚红
        "#8B5CF6", // 罗兰紫
        "#06B6D4", // 湖水青
        "#EC4899", // 蔷薇粉
        "#F97316", // 暖阳橙
        "#6366F1", // 靛青蓝
        "#14B8A6"  // 薄荷绿
    )

    /**
     * 测试当前配置的 AI 模型连通性与响应速度
     */
    suspend fun testConnection(context: Context): AiResult<String> = withContext(Dispatchers.IO) {
        val apiKey = AiPreferences.getApiKey(context)
        if (apiKey.isBlank()) {
            return@withContext AiResult.Error("请先填写对应服务商的 API Key")
        }

        val provider = AiPreferences.getProvider(context)
        val baseUrl = AiPreferences.getBaseUrl(context)
        val model = AiPreferences.getModel(context)

        val startTime = System.currentTimeMillis()
        val testPrompt = "请仅回复五个字：AI连接正常。"

        val result = if (provider == AiProvider.GEMINI) {
            callGeminiApi(apiKey, baseUrl, model, testPrompt, null)
        } else {
            callOpenAiCompatibleApi(apiKey, baseUrl, model, testPrompt, null)
        }

        val elapsed = System.currentTimeMillis() - startTime

        return@withContext when (result) {
            is AiResult.Success -> {
                AiResult.Success("✅ 连接成功！耗时: ${elapsed}ms\n模型 [${model}] 回复: ${result.data.trim()}")
            }
            is AiResult.Error -> result
        }
    }

    /**
     * 生成每日复盘报告 (支持 Gemini, DeepSeek, Qwen 等所有配置的模型)
     */
    suspend fun generateDailyReview(
        context: Context,
        completedCount: Int,
        totalSchedules: Int,
        focusMinutes: Int,
        interruptionCount: Int,
        studySummary: String,
        customNote: String = ""
    ): AiResult<String> = withContext(Dispatchers.IO) {
        val apiKey = AiPreferences.getApiKey(context)
        if (apiKey.isBlank()) {
            return@withContext AiResult.Error("请先在设置中填写有效的 AI API Key。")
        }

        val provider = AiPreferences.getProvider(context)
        val baseUrl = AiPreferences.getBaseUrl(context)
        val model = AiPreferences.getModel(context)

        val prompt = """
            你是一名专业的精力管理与效率规划专家。请基于用户今天的实际执行数据，生成一份结构清晰、有洞察力的高质量复盘报告：
            - 今日日程完成率: $completedCount / $totalSchedules
            - 专注总时长: $focusMinutes 分钟
            - 打断发生次数: $interruptionCount 次
            - 学习与技能进展: $studySummary
            - 用户今日个人心得: $customNote

            请按以下规范输出排版精美的 Markdown 内容：
            ### 🌟 今日高光与成就盘点
            ### ⚡ 精力与专注状态深度诊断
            ### 🎯 明日重心与节奏优化建议
        """.trimIndent()

        return@withContext if (provider == AiProvider.GEMINI) {
            callGeminiApi(apiKey, baseUrl, model, prompt, null)
        } else {
            callOpenAiCompatibleApi(apiKey, baseUrl, model, prompt, null)
        }
    }

    /**
     * 从课程表截图图片中自动识别提取结构化课程列表
     */
    suspend fun extractTimetableFromImage(
        context: Context,
        imageBytes: ByteArray
    ): AiResult<List<CourseEntity>> = withContext(Dispatchers.IO) {
        val apiKey = AiPreferences.getApiKey(context)
        if (apiKey.isBlank()) {
            return@withContext AiResult.Error("未配置 API Key！请前往「工作台」->「AI 模型服务配置」中填写。")
        }

        val provider = AiPreferences.getProvider(context)
        if (provider == AiProvider.DEEPSEEK) {
            return@withContext AiResult.Error("DeepSeek 官方当前仅提供纯文本模型，不支持图像视觉识别！请在工作台设置中切换为 Google Gemini (gemini-1.5-flash) 或 通义千问 (qwen-vl-plus) 视觉模型。")
        }

        val baseUrl = AiPreferences.getBaseUrl(context)
        val model = AiPreferences.getModel(context)

        val prompt = """
            你是一名专业的高校教务课表识别提取助手。请仔细观察这张课程表截图，识别横轴（星期几）和纵轴（节次时间）以及所有的课程单元格。
            
            提取规则：
            1. name: 课程名称 (例如: "操作系统", "大学物理")。
            2. dayOfWeek: 星期几，必须为整数 1..7 (1=周一, 2=周二, ..., 7=周日)。
            3. startSection: 开始节次，从第1节开始的整数 (例如上午第1节就是 1, 第3节就是 3, 下午第5节就是 5)。
            4. sectionSpan: 连续上几节课 (通常为 2 或 3 或 4)。
            5. location: 教室地点 (例如: "教3-201", "综B402")，无则留空字符串 ""。
            6. teacher: 任课教师姓名，无则留空字符串 ""。
            7. startWeek: 起始周数，整数 (如第1周开始则为 1，默认 1)。
            8. endWeek: 结束周数，整数 (如到第16周则为 16，默认 16)。
            9. weekType: 单双周类型，字符串必须为 "ALL"(每周), "ODD"(单周), "EVEN"(双周) 之一。
            10. 如果同一个格子内有两门不同的课（如前八周与后八周不同，或单双周不同），请分别拆分为两条独立记录。
            11. 忽略空白格、表头、无关备注。

            【绝对重要】你的输出必须仅为一个纯粹的 JSON 数组，严禁包含任何前言说明，格式如下：
            [
              {
                "name": "高等数学",
                "dayOfWeek": 1,
                "startSection": 1,
                "sectionSpan": 2,
                "location": "一教302",
                "teacher": "张教授",
                "startWeek": 1,
                "endWeek": 16,
                "weekType": "ALL"
              }
            ]
        """.trimIndent()

        val textResult = if (provider == AiProvider.GEMINI) {
            callGeminiApi(apiKey, baseUrl, model, prompt, imageBytes)
        } else {
            callOpenAiCompatibleApi(apiKey, baseUrl, model, prompt, imageBytes)
        }

        when (textResult) {
            is AiResult.Error -> return@withContext AiResult.Error(textResult.errorMessage)
            is AiResult.Success -> {
                try {
                    val rawText = textResult.data
                    val courses = parseCoursesFromJson(rawText)
                    if (courses.isEmpty()) {
                        return@withContext AiResult.Error("未能从截图中解析出有效课程数据，请确保课表截图清晰完整。AI返回: $rawText")
                    }
                    return@withContext AiResult.Success(courses, rawText)
                } catch (e: Exception) {
                    return@withContext AiResult.Error("解析课程 JSON 失败: ${e.localizedMessage}\nAI回复原内容: ${textResult.data}")
                }
            }
        }
    }

    /**
     * 将用户选择的图片压缩为适合视觉大模型上传的 JPEG 字节数组 (控制在 1920px 以内，保证又快又省)
     */
    fun compressImageFromUri(context: Context, uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val originalBitmap = BitmapFactory.decodeStream(input) ?: return null
                
                val maxDim = 1920
                val width = originalBitmap.width
                val height = originalBitmap.height
                val scaledBitmap = if (width > maxDim || height > maxDim) {
                    val ratio = maxDim.toFloat() / maxOf(width, height)
                    Bitmap.createScaledBitmap(originalBitmap, (width * ratio).toInt(), (height * ratio).toInt(), true)
                } else {
                    originalBitmap
                }

                val stream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                stream.toByteArray()
            }
        } catch (e: Exception) {
            null
        }
    }

    // ==========================================
    // 内部实现：Gemini 官方原生协议调用
    // ==========================================
    private fun callGeminiApi(
        apiKey: String,
        baseUrl: String,
        model: String,
        prompt: String,
        imageBytes: ByteArray?
    ): AiResult<String> {
        return try {
            val cleanBaseUrl = baseUrl.trimEnd('/')
            val endpoint = if (cleanBaseUrl.contains("generativelanguage.googleapis.com")) {
                "$cleanBaseUrl/v1beta/models/$model:generateContent?key=$apiKey"
            } else {
                "$cleanBaseUrl/v1beta/models/$model:generateContent?key=$apiKey"
            }

            val url = URL(endpoint)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 30000
                readTimeout = 60000
            }

            val partsList = mutableListOf<Map<String, Any>>()
            partsList.add(mapOf("text" to prompt))

            if (imageBytes != null && imageBytes.isNotEmpty()) {
                val base64Data = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
                partsList.add(
                    mapOf(
                        "inlineData" to mapOf(
                            "mimeType" to "image/jpeg",
                            "data" to base64Data
                        )
                    )
                )
            }

            val requestBody = mapOf(
                "contents" to listOf(
                    mapOf("parts" to partsList)
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
                    return AiResult.Success(text)
                }
                return AiResult.Error("Gemini API 返回内容为空")
            } else {
                val errorJson = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                return AiResult.Error("Gemini 请求失败 [HTTP $responseCode]: $errorJson")
            }
        } catch (e: Exception) {
            AiResult.Error("连接 Gemini 异常: ${e.localizedMessage}")
        }
    }

    // ==========================================
    // 内部实现：OpenAI 兼容协议调用 (通义千问 / DeepSeek / 自定义代理)
    // ==========================================
    private fun callOpenAiCompatibleApi(
        apiKey: String,
        baseUrl: String,
        model: String,
        prompt: String,
        imageBytes: ByteArray?
    ): AiResult<String> {
        return try {
            val cleanBase = baseUrl.trimEnd('/')
            val endpoint = if (cleanBase.endsWith("/chat/completions")) {
                cleanBase
            } else if (cleanBase.endsWith("/v1")) {
                "$cleanBase/chat/completions"
            } else {
                "$cleanBase/chat/completions"
            }

            val url = URL(endpoint)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                doOutput = true
                connectTimeout = 30000
                readTimeout = 60000
            }

            val messages = mutableListOf<Map<String, Any>>()
            if (imageBytes != null && imageBytes.isNotEmpty()) {
                val base64Data = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
                val contentList = listOf(
                    mapOf("type" to "text", "text" to prompt),
                    mapOf(
                        "type" to "image_url",
                        "image_url" to mapOf("url" to "data:image/jpeg;base64,$base64Data")
                    )
                )
                messages.add(mapOf("role" to "user", "content" to contentList))
            } else {
                messages.add(mapOf("role" to "user", "content" to prompt))
            }

            val requestBody = mutableMapOf<String, Any>(
                "model" to model,
                "messages" to messages
            )

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(gson.toJson(requestBody))
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseJson = connection.inputStream.bufferedReader().use { it.readText() }
                val root = gson.fromJson(responseJson, JsonObject::class.java)
                val choices = root.getAsJsonArray("choices")
                if (choices != null && choices.size() > 0) {
                    val firstChoice = choices[0].asJsonObject
                    val message = firstChoice.getAsJsonObject("message")
                    val content = message.get("content").asString
                    return AiResult.Success(content)
                }
                return AiResult.Error("API 返回 choices 为空")
            } else {
                val errorJson = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                return AiResult.Error("请求失败 [HTTP $responseCode]: $errorJson")
            }
        } catch (e: Exception) {
            AiResult.Error("连接 AI 接口异常: ${e.localizedMessage}")
        }
    }

    /**
     * 清洗和反序列化模型返回的 JSON 字符串为标准 CourseEntity 列表
     */
    private fun parseCoursesFromJson(rawResponse: String): List<CourseEntity> {
        var cleanJson = rawResponse.trim()
        
        // 过滤 markdown 代码块 ```json ... ```
        if (cleanJson.contains("```json")) {
            cleanJson = cleanJson.substringAfter("```json").substringBefore("```").trim()
        } else if (cleanJson.contains("```")) {
            cleanJson = cleanJson.substringAfter("```").substringBefore("```").trim()
        }

        val startIndex = cleanJson.indexOf('[')
        val endIndex = cleanJson.lastIndexOf(']')
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            cleanJson = cleanJson.substring(startIndex, endIndex + 1)
        }

        val jsonArray = gson.fromJson(cleanJson, JsonArray::class.java) ?: return emptyList()
        val nowIso = Instant.now().toString()
        val courses = mutableListOf<CourseEntity>()
        val colorMap = mutableMapOf<String, String>()
        var colorIdx = 0

        for (i in 0 until jsonArray.size()) {
            val obj = jsonArray[i].asJsonObject
            val name = obj.get("name")?.asString?.trim() ?: continue
            if (name.isBlank()) continue

            val dayOfWeek = obj.get("dayOfWeek")?.asInt ?: 1
            val startSection = obj.get("startSection")?.asInt ?: 1
            val sectionSpan = obj.get("sectionSpan")?.asInt ?: 2
            val location = obj.get("location")?.asString?.trim() ?: ""
            val teacher = obj.get("teacher")?.asString?.trim() ?: ""
            val startWeek = obj.get("startWeek")?.asInt ?: 1
            val endWeek = obj.get("endWeek")?.asInt ?: 16
            val rawWeekType = obj.get("weekType")?.asString?.uppercase() ?: "ALL"
            val weekType = when {
                rawWeekType.contains("ODD") || rawWeekType.contains("单") -> "ODD"
                rawWeekType.contains("EVEN") || rawWeekType.contains("双") -> "EVEN"
                else -> "ALL"
            }

            // 为相同课程名分配统一的亮丽专属颜色
            val assignedColor = colorMap.getOrPut(name) {
                val color = COURSE_PALETTE[colorIdx % COURSE_PALETTE.size]
                colorIdx++
                color
            }

            courses.add(
                CourseEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    dayOfWeek = dayOfWeek.coerceIn(1, 7),
                    startSection = startSection.coerceIn(1, 14),
                    sectionSpan = sectionSpan.coerceIn(1, 6),
                    location = location,
                    teacher = teacher,
                    startWeek = startWeek.coerceAtLeast(1),
                    endWeek = endWeek.coerceAtLeast(startWeek),
                    weekType = weekType,
                    colorHex = assignedColor,
                    createdAt = nowIso,
                    updatedAt = nowIso
                )
            )
        }

        return courses
    }
}
