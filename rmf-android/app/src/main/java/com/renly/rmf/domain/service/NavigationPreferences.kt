package com.renly.rmf.domain.service

import android.content.Context
import android.content.SharedPreferences

data class NavItemConfig(
    val route: String,
    val title: String,
    val iconEmoji: String,
    val defaultEnabled: Boolean,
    val description: String
)

object NavigationPreferences {
    private const val PREFS_NAME = "rmf_navigation_preferences"
    private const val KEY_BOTTOM_BAR_VISIBLE = "bottom_bar_visible"
    private const val KEY_ENABLED_ROUTES = "enabled_routes"
    private const val KEY_DEFAULT_HOME_ROUTE = "default_home_route"

    val ALL_AVAILABLE_NAV_ITEMS = listOf(
        NavItemConfig("schedule", "日程", "📅", true, "核心时间块日程管理、待办与打卡"),
        NavItemConfig("timetable", "课表", "🎓", true, "周课表透视、调停课与一键投影"),
        NavItemConfig("study", "学习", "📖", true, "知识库专项主题与记忆间隔复习"),
        NavItemConfig("focus", "专注", "⏱️", true, "番茄时钟、流体云联动与画中画"),
        NavItemConfig("fitness", "健身", "🏋️", false, "日常训练记录、部位动作与计划投影"),
        NavItemConfig("habits", "习惯", "🌱", false, "习惯连续打卡、Streak统计与365天热力图"),
        NavItemConfig("countdowns", "倒数", "⏳", false, "重要考试、里程碑与纪念日倒数胶囊"),
        NavItemConfig("goals", "目标", "🎯", false, "北极星目标金字塔与进度追踪"),
        NavItemConfig("expenses", "记账", "💰", false, "日常收支账目、分类与月度分析"),
        NavItemConfig("daily_report", "复盘", "🏆", false, "每日小结、成就复盘与心得反思"),
        NavItemConfig("summary", "统计", "📊", false, "全维度效能数据、图表与被打断分析"),
        NavItemConfig("settings", "工作台", "⚙️", true, "全局设置、云同步、AI 引擎与备份")
    )

    val DEFAULT_ENABLED_ROUTES = listOf("schedule", "timetable", "study", "focus", "settings")
    const val DEFAULT_HOME_ROUTE = "schedule"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isBottomBarVisible(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BOTTOM_BAR_VISIBLE, true)
    }

    fun setBottomBarVisible(context: Context, visible: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BOTTOM_BAR_VISIBLE, visible).apply()
    }

    fun getEnabledRoutes(context: Context): List<String> {
        val raw = getPrefs(context).getString(KEY_ENABLED_ROUTES, null)
        if (raw.isNullOrBlank()) {
            return DEFAULT_ENABLED_ROUTES
        }
        val list = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        return if (list.isEmpty()) DEFAULT_ENABLED_ROUTES else list
    }

    fun setEnabledRoutes(context: Context, routes: List<String>) {
        val safeRoutes = if (routes.isEmpty()) DEFAULT_ENABLED_ROUTES else routes.distinct()
        val raw = safeRoutes.joinToString(",")
        getPrefs(context).edit().putString(KEY_ENABLED_ROUTES, raw).apply()
    }

    fun getDefaultHomeRoute(context: Context): String {
        val route = getPrefs(context).getString(KEY_DEFAULT_HOME_ROUTE, DEFAULT_HOME_ROUTE) ?: DEFAULT_HOME_ROUTE
        return if (ALL_AVAILABLE_NAV_ITEMS.any { it.route == route }) route else DEFAULT_HOME_ROUTE
    }

    fun setDefaultHomeRoute(context: Context, route: String) {
        val safeRoute = if (ALL_AVAILABLE_NAV_ITEMS.any { it.route == route }) route else DEFAULT_HOME_ROUTE
        getPrefs(context).edit().putString(KEY_DEFAULT_HOME_ROUTE, safeRoute).apply()
    }

    fun resetToDefault(context: Context) {
        getPrefs(context).edit()
            .putBoolean(KEY_BOTTOM_BAR_VISIBLE, true)
            .putString(KEY_ENABLED_ROUTES, DEFAULT_ENABLED_ROUTES.joinToString(","))
            .putString(KEY_DEFAULT_HOME_ROUTE, DEFAULT_HOME_ROUTE)
            .apply()
    }
}
