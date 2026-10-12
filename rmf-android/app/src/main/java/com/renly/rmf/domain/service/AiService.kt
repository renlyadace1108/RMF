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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

sealed class AiResult<out T> {
    data class Success<T>(val data: T, val rawResponse: String = "") : AiResult<T>()
    data class Error(val errorMessage: String) : AiResult<Nothing>()
}

data class AiScheduleParseResult(
    val hasTime: Boolean,
    val title: String,
    val startDateTime: LocalDateTime? = null,
    val endDateTime: LocalDateTime? = null,
    val durationMinutes: Int = 60,
    val priority: String = "MEDIUM",
    val category: String = "WORK"
)

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
     * 测试当前配置的自定义接口连通性与响应速度
     */
    suspend fun testConnection(context: Context): AiResult<String> = withContext(Dispatchers.IO) {
        if (!AiPreferences.isAiEnabled(context)) {
            return@withContext AiResult.Error("数据分析功能已在设置中停用，请先开启总开关。")
        }
        val baseUrl = AiPreferences.getBaseUrl(context)
        if (baseUrl.isBlank()) {
            return@withContext AiResult.Error("请先填写接口服务地址 (Base URL)")
        }
        val apiKey = AiPreferences.getApiKey(context)
        if (apiKey.isBlank()) {
            return@withContext AiResult.Error("请先填写接口密钥 (API Key)")
        }
        val model = AiPreferences.getModel(context)
        if (model.isBlank()) {
            return@withContext AiResult.Error("请先填写模型名称 (Model)")
        }

        val startTime = System.currentTimeMillis()
        val testPrompt = "请仅回复五个字：服务已就绪。"

        val result = callOpenAiCompatibleApi(apiKey, baseUrl, model, testPrompt, null)

        val elapsed = System.currentTimeMillis() - startTime

        return@withContext when (result) {
            is AiResult.Success -> {
                AiResult.Success("✅ 连接成功！响应耗时: ${elapsed}ms\n模型 [${model}] 已就绪。")
            }
            is AiResult.Error -> result
        }
    }

    /**
     * 生成每日复盘报告
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
        if (!AiPreferences.isAiEnabled(context)) {
            return@withContext AiResult.Error("数据分析功能已在系统设置中停用。")
        }
        val baseUrl = AiPreferences.getBaseUrl(context)
        val apiKey = AiPreferences.getApiKey(context)
        val model = AiPreferences.getModel(context)
        if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
            return@withContext AiResult.Error("请先在设置中完整配置接口地址、密钥与模型。")
        }

        val prompt = """
            你是一名专业的精力管理与效能规划助手。请基于用户今天的实际执行数据，生成一份结构清晰、有洞察力的高质量复盘报告：
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

        return@withContext callOpenAiCompatibleApi(apiKey, baseUrl, model, prompt, null)
    }

    data class AiDailyReportDraft(
        val summary: String,
        val plan: String,
        val reflection: String
    )

    /**
     * 为每日复盘日报生成结构化的草稿内容 (今日交付、明日推进、心得反思)
     */
    suspend fun generateDailyReportDraft(
        context: Context,
        dateStr: String,
        completedTasks: List<String>,
        pendingTasks: List<String>,
        completionRate: Int
    ): AiResult<AiDailyReportDraft> = withContext(Dispatchers.IO) {
        if (!AiPreferences.isAiEnabled(context)) {
            return@withContext AiResult.Error("数据分析功能已在系统设置中停用。")
        }
        val baseUrl = AiPreferences.getBaseUrl(context)
        val apiKey = AiPreferences.getApiKey(context)
        val model = AiPreferences.getModel(context)
        if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
            return@withContext AiResult.Error("请先在设置中完整配置接口地址、密钥与模型。")
        }

        val completedText = if (completedTasks.isNotEmpty()) {
            completedTasks.joinToString("\n") { "- $it" }
        } else {
            "今日无已完成事项"
        }

        val pendingText = if (pendingTasks.isNotEmpty()) {
            pendingTasks.joinToString("\n") { "- $it" }
        } else {
            "今日无遗留待办事项"
        }

        val prompt = """
            你是一名专业的高绩效个人效能与精力管理专家。请根据用户在 $dateStr 的执行清单数据，生成今日的复盘总结草稿。
            
            【今日执行数据】
            - 任务履约达成率: $completionRate%
            - 已交付完成事项:
            $completedText
            - 待推进/未完成事项:
            $pendingText

            【任务要求】
            请分析并提炼出以下三部分内容，输出格式必须为纯 JSON 对象，严禁带有 markdown 标记或额外废话：
            1. summary: 今日完成工作 (Done) 的条理化总结，突出重点成果。
            2. plan: 明日攻坚计划 (Plan) 的建议安排，合理承接未完事项并设立重心。
            3. reflection: 效率反思与心得 (Reflection)，结合达成率给出客观中肯的精力节奏与心流建议。

            【JSON 格式规范】
            {
              "summary": "• 成果1\n• 成果2",
              "plan": "• 明日重心1\n• 推进事项2",
              "reflection": "今日完成率 XX%，..."
            }
        """.trimIndent()

        val apiRes = callOpenAiCompatibleApi(apiKey, baseUrl, model, prompt, null)
        if (apiRes is AiResult.Success) {
            try {
                var jsonStr = apiRes.data.trim()
                if (jsonStr.contains("```json")) {
                    jsonStr = jsonStr.substringAfter("```json").substringBefore("```").trim()
                } else if (jsonStr.contains("```")) {
                    jsonStr = jsonStr.substringAfter("```").substringBefore("```").trim()
                }
                val startIdx = jsonStr.indexOf('{')
                val endIdx = jsonStr.lastIndexOf('}')
                if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
                    jsonStr = jsonStr.substring(startIdx, endIdx + 1)
                }

                val obj = gson.fromJson(jsonStr, JsonObject::class.java)
                val summary = obj.get("summary")?.asString?.trim() ?: ""
                val plan = obj.get("plan")?.asString?.trim() ?: ""
                val reflection = obj.get("reflection")?.asString?.trim() ?: ""

                if (summary.isNotBlank() || plan.isNotBlank() || reflection.isNotBlank()) {
                    return@withContext AiResult.Success(
                        AiDailyReportDraft(
                            summary = summary,
                            plan = plan,
                            reflection = reflection
                        ),
                        rawResponse = apiRes.data
                    )
                }
            } catch (e: Exception) {
                // 降级使用 raw 回复
            }
            // 若非标准 JSON 但有内容，作为 reflection 返回
            return@withContext AiResult.Success(
                AiDailyReportDraft(
                    summary = completedText,
                    plan = pendingText,
                    reflection = apiRes.data.trim()
                ),
                rawResponse = apiRes.data
            )
        } else if (apiRes is AiResult.Error) {
            return@withContext AiResult.Error(apiRes.errorMessage)
        }

        return@withContext AiResult.Error("未知异常")
    }

    /**
     * 智能识别用户输入的闪念文本，提取明确的时间安排与任务信息
     * 如果包含明确时间，返回 hasTime = true 及开始结束时间，用于询问用户是否自动排期；
     * 如果不包含明确时间，返回 hasTime = false，用于直接存入待排期池由用户手动排期。
     */
    suspend fun parseScheduleWithAi(
        context: Context,
        rawText: String
    ): AiScheduleParseResult = withContext(Dispatchers.IO) {
        val cleanInput = rawText.trim()
        if (cleanInput.isBlank()) {
            return@withContext AiScheduleParseResult(
                hasTime = false,
                title = "新时间块"
            )
        }

        if (!AiPreferences.isAiEnabled(context) || !AiPreferences.isConfigured(context)) {
            // 未开启 AI 或未配置时，走本地规则兜底
            val local = NaturalLanguageScheduleParser.parse(cleanInput)
            return@withContext AiScheduleParseResult(
                hasTime = local.hasExplicitTime,
                title = local.title,
                startDateTime = if (local.hasExplicitTime) local.startTime else null,
                endDateTime = if (local.hasExplicitTime) local.endTime else null,
                durationMinutes = local.durationMinutes,
                priority = local.priority,
                category = local.category
            )
        }

        val baseUrl = AiPreferences.getBaseUrl(context)
        val apiKey = AiPreferences.getApiKey(context)
        val model = AiPreferences.getModel(context)

        val now = LocalDateTime.now()
        val baseTimeStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm EEEE"))

        val prompt = """
            当前系统基准时间为: $baseTimeStr
            请分析用户的日程待办文本: "$cleanInput"

            任务要求：
            1. 判断文本中是否包含具体明确的计划时间（如 "明天下午3点"、"周五19:00"、"后天上午9点半"、"15:00-16:30" 等）：
               - 若包含具体时间：hasTime 必须为 true，并推算出对应的开始时间与结束时间。
               - 若仅为纯待办事项（如 "买牛奶"、"背单词"、"做数学作业"）或时间模糊未指明具体几点几分：hasTime 必须为 false。
            2. 字段规范：
               - hasTime: true 或 false
               - title: 清洗后的纯任务标题（去掉时间修饰词）
               - startTime: 格式严格为 "yyyy-MM-dd'T'HH:mm:ss"（若 hasTime 为 false 则为空字符串 ""）
               - endTime: 格式严格为 "yyyy-MM-dd'T'HH:mm:ss"（若未指定持续时长默认60分钟，若 hasTime 为 false 则为空字符串 ""）
               - durationMinutes: 持续时长整数(分钟，默认 60)
               - priority: "HIGH" / "MEDIUM" / "LOW" (根据紧急程度推断，默认 "MEDIUM")
               - category: "WORK" / "STUDY" / "LIFE" / "HEALTH" (根据分类推断，默认 "WORK")

            【输出要求】仅回复纯 JSON 格式对象，严禁包含任何前缀、闲聊或 markdown 格式：
            {
              "hasTime": true,
              "title": "项目评审会议",
              "startTime": "2026-10-13T15:00:00",
              "endTime": "2026-10-13T16:00:00",
              "durationMinutes": 60,
              "priority": "HIGH",
              "category": "WORK"
            }
        """.trimIndent()

        val apiRes = callOpenAiCompatibleApi(apiKey, baseUrl, model, prompt, null)
        if (apiRes is AiResult.Success) {
            try {
                var jsonStr = apiRes.data.trim()
                if (jsonStr.contains("```json")) {
                    jsonStr = jsonStr.substringAfter("```json").substringBefore("```").trim()
                } else if (jsonStr.contains("```")) {
                    jsonStr = jsonStr.substringAfter("```").substringBefore("```").trim()
                }
                val startIdx = jsonStr.indexOf('{')
                val endIdx = jsonStr.lastIndexOf('}')
                if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
                    jsonStr = jsonStr.substring(startIdx, endIdx + 1)
                }

                val obj = gson.fromJson(jsonStr, JsonObject::class.java)
                val hasTime = obj.get("hasTime")?.asBoolean ?: false
                val title = obj.get("title")?.asString?.trim()?.ifBlank { cleanInput } ?: cleanInput
                val durationMinutes = obj.get("durationMinutes")?.asInt ?: 60
                val priority = obj.get("priority")?.asString?.uppercase() ?: "MEDIUM"
                val category = obj.get("category")?.asString?.uppercase() ?: "WORK"

                var startLdt: LocalDateTime? = null
                var endLdt: LocalDateTime? = null

                if (hasTime) {
                    val sStr = obj.get("startTime")?.asString ?: ""
                    val eStr = obj.get("endTime")?.asString ?: ""
                    if (sStr.isNotBlank()) {
                        startLdt = try {
                            LocalDateTime.parse(sStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        } catch (_: Exception) {
                            try { LocalDateTime.parse(sStr, DateTimeFormatter.ISO_DATE_TIME) } catch (_: Exception) { null }
                        }
                    }
                    if (eStr.isNotBlank()) {
                        endLdt = try {
                            LocalDateTime.parse(eStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        } catch (_: Exception) {
                            try { LocalDateTime.parse(eStr, DateTimeFormatter.ISO_DATE_TIME) } catch (_: Exception) { null }
                        }
                    }
                    if (startLdt != null && endLdt == null) {
                        endLdt = startLdt.plusMinutes(durationMinutes.toLong())
                    }
                }

                return@withContext AiScheduleParseResult(
                    hasTime = hasTime && startLdt != null,
                    title = title,
                    startDateTime = startLdt,
                    endDateTime = endLdt,
                    durationMinutes = durationMinutes,
                    priority = priority,
                    category = category
                )
            } catch (e: Exception) {
                // JSON 解析异常，走本地降级
            }
        }

        // 异常或网络失败时走本地规则降级
        val local = NaturalLanguageScheduleParser.parse(cleanInput)
        return@withContext AiScheduleParseResult(
            hasTime = local.hasExplicitTime,
            title = local.title,
            startDateTime = if (local.hasExplicitTime) local.startTime else null,
            endDateTime = if (local.hasExplicitTime) local.endTime else null,
            durationMinutes = local.durationMinutes,
            priority = local.priority,
            category = local.category
        )
    }

    /**
     * 从课程表截图图片中自动识别提取结构化课程列表
     */
    suspend fun extractTimetableFromImage(
        context: Context,
        imageBytes: ByteArray
    ): AiResult<List<CourseEntity>> = withContext(Dispatchers.IO) {
        if (!AiPreferences.isAiEnabled(context)) {
            return@withContext AiResult.Error("数据分析功能已在设置中停用。")
        }
        val baseUrl = AiPreferences.getBaseUrl(context)
        val apiKey = AiPreferences.getApiKey(context)
        val model = AiPreferences.getModel(context)
        if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
            return@withContext AiResult.Error("未完整配置接口！请前往「系统设置」中配置自定义接口地址、密钥与视觉模型。")
        }

        val prompt = """
            你是一名专业的教务课表识别提取助手。请仔细观察这张课程表截图，识别横轴（星期几）和纵轴（节次时间）以及所有的课程单元格。
            
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

        val textResult = callOpenAiCompatibleApi(apiKey, baseUrl, model, prompt, imageBytes)

        when (textResult) {
            is AiResult.Error -> return@withContext AiResult.Error(textResult.errorMessage)
            is AiResult.Success -> {
                try {
                    val rawText = textResult.data
                    val courses = parseCoursesFromJson(rawText)
                    if (courses.isEmpty()) {
                        return@withContext AiResult.Error("未能从截图中解析出有效课程数据，请确保课表截图清晰完整。接口返回: $rawText")
                    }
                    return@withContext AiResult.Success(courses, rawText)
                } catch (e: Exception) {
                    return@withContext AiResult.Error("解析课程 JSON 失败: ${e.localizedMessage}\n接口原内容: ${textResult.data}")
                }
            }
        }
    }

    /**
     * 将用户选择的图片压缩为适合视觉模型上传的 JPEG 字节数组 (控制在 1920px 以内，保证又快又省)
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
    // 内部实现：标准兼容协议调用
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
            val endpoint = when {
                cleanBase.endsWith("/chat/completions") -> cleanBase
                cleanBase.contains("generativelanguage.googleapis.com") -> {
                    if (cleanBase.endsWith("/openai")) "$cleanBase/chat/completions"
                    else if (cleanBase.endsWith("/v1beta")) "$cleanBase/openai/chat/completions"
                    else "$cleanBase/v1beta/openai/chat/completions"
                }
                cleanBase.endsWith("/v1") || cleanBase.endsWith("/v4") -> "$cleanBase/chat/completions"
                else -> "$cleanBase/v1/chat/completions"
            }

            val url = URL(endpoint)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                instanceFollowRedirects = true
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

            OutputStreamWriter(connection.outputStream, java.nio.charset.StandardCharsets.UTF_8).use { writer ->
                writer.write(gson.toJson(requestBody))
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseJson = connection.inputStream.bufferedReader(java.nio.charset.StandardCharsets.UTF_8).use { it.readText() }
                val root = gson.fromJson(responseJson, JsonObject::class.java)
                val choices = root.getAsJsonArray("choices")
                if (choices != null && choices.size() > 0) {
                    val firstChoice = choices[0].asJsonObject
                    val message = firstChoice.getAsJsonObject("message")
                    if (message != null) {
                        val contentElem = message.get("content")
                        val content = if (contentElem != null && !contentElem.isJsonNull) contentElem.asString else ""
                        val reasoningElem = message.get("reasoning_content")
                        val reasoning = if (reasoningElem != null && !reasoningElem.isJsonNull) reasoningElem.asString else ""
                        val finalText = if (content.isNotBlank()) content else reasoning
                        if (finalText.isNotBlank()) {
                            return AiResult.Success(finalText)
                        }
                    }
                }
                return AiResult.Error("接口返回 choices 为空或内容未就绪")
            } else {
                val errorJson = connection.errorStream?.bufferedReader(java.nio.charset.StandardCharsets.UTF_8)?.use { it.readText() } ?: "HTTP $responseCode"
                return AiResult.Error("请求失败 [HTTP $responseCode]: $errorJson")
            }
        } catch (e: Exception) {
            val hint = if (baseUrl.startsWith("http://")) " (建议优先使用 https:// 安全协议)" else ""
            AiResult.Error("连接接口异常: ${e.localizedMessage ?: e.message}$hint")
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
