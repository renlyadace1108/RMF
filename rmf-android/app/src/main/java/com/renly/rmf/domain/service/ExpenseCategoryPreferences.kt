package com.renly.rmf.domain.service

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * 记账分类数据模型
 * @param id 唯一标识
 * @param name 分类名称 (如 餐饮、数码)
 * @param type 收支类型: "EXPENSE" (支出) 或 "INCOME" (收入)
 * @param emoji 图标 Emoji
 * @param isCustom 是否为用户自定义分类
 */
data class ExpenseCategoryItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String = "EXPENSE",
    val emoji: String = "🛒",
    val isCustom: Boolean = false
)

/**
 * 记账分类本地持久化管理器 (SharedPreferences + JSON)
 * 避免改动 Room 数据库结构导致覆盖升级时旧数据丢失或崩溃
 */
object ExpenseCategoryPreferences {
    private const val PREF_NAME = "rmf_expense_categories_pref"
    private const val KEY_CUSTOM_CATEGORIES = "custom_categories_json"

    val DEFAULT_EXPENSE_CATEGORIES = listOf(
        ExpenseCategoryItem(id = "def_exp_1", name = "餐饮", type = "EXPENSE", emoji = "🍜", isCustom = false),
        ExpenseCategoryItem(id = "def_exp_2", name = "交通", type = "EXPENSE", emoji = "🚗", isCustom = false),
        ExpenseCategoryItem(id = "def_exp_3", name = "购物", type = "EXPENSE", emoji = "🛍️", isCustom = false),
        ExpenseCategoryItem(id = "def_exp_4", name = "日常", type = "EXPENSE", emoji = "🛒", isCustom = false),
        ExpenseCategoryItem(id = "def_exp_5", name = "学习", type = "EXPENSE", emoji = "📚", isCustom = false),
        ExpenseCategoryItem(id = "def_exp_6", name = "娱乐", type = "EXPENSE", emoji = "🎮", isCustom = false),
        ExpenseCategoryItem(id = "def_exp_7", name = "医疗", type = "EXPENSE", emoji = "💊", isCustom = false),
        ExpenseCategoryItem(id = "def_exp_8", name = "居住", type = "EXPENSE", emoji = "🏠", isCustom = false),
        ExpenseCategoryItem(id = "def_exp_9", name = "人情", type = "EXPENSE", emoji = "🎁", isCustom = false),
        ExpenseCategoryItem(id = "def_exp_10", name = "其他", type = "EXPENSE", emoji = "📦", isCustom = false)
    )

    val DEFAULT_INCOME_CATEGORIES = listOf(
        ExpenseCategoryItem(id = "def_inc_1", name = "工资", type = "INCOME", emoji = "💰", isCustom = false),
        ExpenseCategoryItem(id = "def_inc_2", name = "奖金", type = "INCOME", emoji = "🏆", isCustom = false),
        ExpenseCategoryItem(id = "def_inc_3", name = "兼职", type = "INCOME", emoji = "💼", isCustom = false),
        ExpenseCategoryItem(id = "def_inc_4", name = "理财", type = "INCOME", emoji = "📈", isCustom = false),
        ExpenseCategoryItem(id = "def_inc_5", name = "红包", type = "INCOME", emoji = "🧧", isCustom = false),
        ExpenseCategoryItem(id = "def_inc_6", name = "退款", type = "INCOME", emoji = "🔄", isCustom = false),
        ExpenseCategoryItem(id = "def_inc_7", name = "其他", type = "INCOME", emoji = "💵", isCustom = false)
    )

    private val gson = Gson()
    private var cachedCustomCategories: List<ExpenseCategoryItem>? = null

    fun getCustomCategories(context: Context): List<ExpenseCategoryItem> {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = sp.getString(KEY_CUSTOM_CATEGORIES, null)
        val list = if (json.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                val type = object : TypeToken<List<ExpenseCategoryItem>>() {}.type
                gson.fromJson<List<ExpenseCategoryItem>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
        cachedCustomCategories = list
        return list
    }

    private fun saveCustomCategories(context: Context, list: List<ExpenseCategoryItem>) {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(list)
        sp.edit().putString(KEY_CUSTOM_CATEGORIES, json).apply()
        cachedCustomCategories = list
    }

    fun getExpenseCategories(context: Context): List<ExpenseCategoryItem> {
        val custom = getCustomCategories(context).filter { it.type == "EXPENSE" }
        return DEFAULT_EXPENSE_CATEGORIES + custom
    }

    fun getIncomeCategories(context: Context): List<ExpenseCategoryItem> {
        val custom = getCustomCategories(context).filter { it.type == "INCOME" }
        return DEFAULT_INCOME_CATEGORIES + custom
    }

    fun getAllCategories(context: Context): List<ExpenseCategoryItem> {
        val custom = getCustomCategories(context)
        return DEFAULT_EXPENSE_CATEGORIES + DEFAULT_INCOME_CATEGORIES + custom
    }

    fun addCustomCategory(context: Context, name: String, type: String, emoji: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        val current = getCustomCategories(context).toMutableList()
        if (current.any { it.name.equals(trimmed, ignoreCase = true) && it.type == type }) {
            return false // 已存在同名分类
        }
        val defaults = if (type == "EXPENSE") DEFAULT_EXPENSE_CATEGORIES else DEFAULT_INCOME_CATEGORIES
        if (defaults.any { it.name.equals(trimmed, ignoreCase = true) }) {
            return false // 已存在默认分类
        }

        current.add(
            ExpenseCategoryItem(
                id = UUID.randomUUID().toString(),
                name = trimmed,
                type = type,
                emoji = emoji.ifBlank { if (type == "INCOME") "💵" else "🛒" },
                isCustom = true
            )
        )
        saveCustomCategories(context, current)
        return true
    }

    fun deleteCustomCategory(context: Context, id: String): Boolean {
        val current = getCustomCategories(context).toMutableList()
        val removed = current.removeAll { it.id == id }
        if (removed) {
            saveCustomCategories(context, current)
        }
        return removed
    }

    fun getCategoryEmoji(name: String, type: String = "EXPENSE"): String {
        val cached = cachedCustomCategories
        val matched = cached?.firstOrNull { it.name == name && it.type == type }
            ?: cached?.firstOrNull { it.name == name }
            ?: (DEFAULT_EXPENSE_CATEGORIES + DEFAULT_INCOME_CATEGORIES).firstOrNull { it.name == name }
        if (matched != null) return matched.emoji

        return resolveEmojiByKeyword(name, type)
    }

    fun getCategoryEmoji(context: Context, name: String, type: String): String {
        val all = getAllCategories(context)
        val matched = all.firstOrNull { it.name == name && it.type == type }
            ?: all.firstOrNull { it.name == name }
        if (matched != null) return matched.emoji

        return resolveEmojiByKeyword(name, type)
    }

    private fun resolveEmojiByKeyword(name: String, type: String): String {
        return when {
            name.contains("餐") || name.contains("吃") || name.contains("饭") || name.contains("食") || name.contains("外卖") -> "🍜"
            name.contains("车") || name.contains("交通") || name.contains("行") || name.contains("油") || name.contains("地铁") -> "🚗"
            name.contains("购") || name.contains("买") || name.contains("服") -> "🛍️"
            name.contains("学") || name.contains("书") || name.contains("课") -> "📚"
            name.contains("玩") || name.contains("娱") || name.contains("游") || name.contains("影") -> "🎮"
            name.contains("医") || name.contains("药") || name.contains("病") -> "💊"
            name.contains("住") || name.contains("房") || name.contains("租") || name.contains("水") || name.contains("电") -> "🏠"
            name.contains("情") || name.contains("礼") -> "🎁"
            name.contains("资") || name.contains("薪") -> "💰"
            name.contains("奖") -> "🏆"
            name.contains("兼") -> "💼"
            name.contains("理财") || name.contains("投资") || name.contains("股") -> "📈"
            name.contains("包") -> "🧧"
            name.contains("退") -> "🔄"
            type == "INCOME" -> "💵"
            else -> "🛒"
        }
    }
}
