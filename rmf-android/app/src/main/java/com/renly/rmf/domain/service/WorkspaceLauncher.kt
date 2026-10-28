package com.renly.rmf.domain.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.renly.rmf.data.local.entity.ScheduleEntity
import java.util.regex.Pattern

data class WorkspaceAsset(
    val type: String, // "URL", "FILE", "FOLDER"
    val target: String,
    val displayName: String
)

object WorkspaceLauncher {
    private val EXPLICIT_TAG_PATTERN = Pattern.compile(
        "(?:工作区|资产|workspace|asset|repo|path)\\s*[:：]\\s*([^\\r\\n]+)",
        Pattern.CASE_INSENSITIVE
    )

    private val MARKDOWN_LINK_PATTERN = Pattern.compile(
        "\\[([^\\]]+)\\]\\((https?://[^\\s\\)]+)\\)",
        Pattern.CASE_INSENSITIVE
    )

    private val HTTP_URL_PATTERN = Pattern.compile(
        "(https?://[^\\s<>'\"{}|\\\\^`]+)",
        Pattern.CASE_INSENSITIVE
    )

    fun detectAsset(schedule: ScheduleEntity?): WorkspaceAsset? {
        if (schedule == null) return null

        // 1. 尝试从描述解析
        extractFromText(schedule.description)?.let { return it }

        // 2. 尝试从 DoD 验收标准解析
        extractFromText(schedule.dod)?.let { return it }

        return null
    }

    fun extractFromText(text: String?): WorkspaceAsset? {
        if (text.isNullOrBlank()) return null

        // A. 显式前缀: 工作区: / workspace:
        val tagMatcher = EXPLICIT_TAG_PATTERN.matcher(text)
        if (tagMatcher.find()) {
            val raw = tagMatcher.group(1)?.trim()?.trim('"', '\'', '`') ?: ""
            resolveAsset(raw)?.let { return it }
        }

        // B. Markdown 链接: [文档名](url)
        val mdMatcher = MARKDOWN_LINK_PATTERN.matcher(text)
        if (mdMatcher.find()) {
            val label = mdMatcher.group(1)?.trim() ?: ""
            val url = mdMatcher.group(2)?.trim() ?: ""
            resolveAsset(url)?.let { asset ->
                return if (label.isNotBlank()) asset.copy(displayName = label) else asset
            }
        }

        // C. HTTP/HTTPS 超链接
        val urlMatcher = HTTP_URL_PATTERN.matcher(text)
        if (urlMatcher.find()) {
            val raw = urlMatcher.group(1)?.trim()?.trimEnd('.', ';', ',', ')', ']') ?: ""
            resolveAsset(raw)?.let { return it }
        }

        return null
    }

    private fun resolveAsset(target: String): WorkspaceAsset? {
        if (target.isBlank()) return null
        val clean = target.trim()

        if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
            val uri = runCatching { Uri.parse(clean) }.getOrNull()
            val host = uri?.host ?: clean
            val path = uri?.path?.trim('/') ?: ""
            val display = if (path.isNotBlank() && path.length <= 20) "$host/$path" else host
            return WorkspaceAsset(
                type = "URL",
                target = clean,
                displayName = display
            )
        }

        if (clean.startsWith("content://") || clean.startsWith("file://")) {
            return WorkspaceAsset(
                type = "FILE",
                target = clean,
                displayName = clean.substringAfterLast('/')
            )
        }

        return null
    }

    fun launchAsset(context: Context, asset: WorkspaceAsset) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(asset.target)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法直达资产「${asset.displayName}」: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
