package me.rerere.rikkahub.data.ai.tools

import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessage

fun List<Tool>.deduplicateByToolName(): List<Tool> = distinctBy { it.name }

fun List<Tool>.selectRelevantToolsForPrompt(messages: List<UIMessage>): List<Tool> {
    if (size <= 8) return this

    val recentText = messages
        .asReversed()
        .filter { it.role == MessageRole.USER }
        .take(3)
        .joinToString("\n") { it.toText() }
        .lowercase()
    if (recentText.isBlank()) return keepDefaultPromptTools()

    val selected = filter { tool ->
        val name = tool.name.removePrefix("mcp__").lowercase()
        val description = tool.description.lowercase()
        if (isStudyAppPlanQuery(recentText) && name.contains("calendar") && !isExplicitCalendarQuery(recentText)) {
            return@filter false
        }
        name in defaultPromptToolNames ||
            recentText.contains(name) ||
            recentText.contains(name.replace('_', ' ')) ||
            description.split(' ', ',', '.', ';', ':', '/', '-', '_')
                .filter { it.length >= 4 }
                .take(16)
                .any { recentText.contains(it) } ||
            matchesToolIntent(name, recentText)
    }

    return selected.ifEmpty { keepDefaultPromptTools() }
}

private val defaultPromptToolNames = setOf(
    "get_time_info",
    "set_lulu_expression_state",
)

private fun List<Tool>.keepDefaultPromptTools(): List<Tool> {
    val defaults = filter { it.name.removePrefix("mcp__").lowercase() in defaultPromptToolNames }
    return defaults.ifEmpty { take(4) }
}

private fun matchesToolIntent(name: String, text: String): Boolean = when {
    name == "today_study_plan" ->
        isStudyAppPlanQuery(text)
    name.contains("search") || name.contains("scrape") || name.contains("web") ->
        text.hasAny("搜", "查", "最新", "新闻", "网页", "网址", "链接", "浏览", "资料", "小红书", "google", "http")
    name.contains("weather") ->
        text.hasAny("天气", "温度", "下雨", "下雪", "冷", "热", "带伞", "风", "湿度")
    name.contains("location") || name.contains("nearby") ->
        text.hasAny("位置", "在哪", "哪里", "附近", "周边", "路线", "导航", "地址", "到这", "离我")
    name.contains("calendar") || name.contains("time") ->
        text.hasAny("时间", "几点", "今天", "明天", "昨天", "日期", "日程", "安排", "会议", "课表", "提醒")
    name.contains("alarm") ->
        text.hasAny("闹钟", "叫我", "提醒我", "定个", "起床")
    name.contains("battery") ->
        text.hasAny("电量", "充电", "没电", "耗电", "电池")
    name.contains("usage") || name.contains("app") ->
        text.hasAny("分心", "刷", "用了多久", "使用情况", "应用", "app", "学习状态")
    name.contains("notification") ->
        text.hasAny("通知", "消息", "谁找我", "未读")
    name.contains("camera") ->
        text.hasAny("拍", "看一下", "看看", "摄像头", "相机", "眼前", "桌面")
    name.contains("clipboard") ->
        text.hasAny("剪贴板", "复制", "粘贴", "刚复制")
    name.contains("sms") ->
        text.hasAny("短信", "验证码", "发消息", "发给")
    name.contains("music") ->
        text.hasAny("音乐", "播放", "暂停", "歌曲", "歌单")
    name.contains("tts") || name.contains("speech") ->
        text.hasAny("朗读", "念给我", "语音", "说出来")
    name.contains("journal") || name.contains("memory") ->
        text.hasAny("记住", "记录", "日记", "回忆", "记忆")
    name.contains("skill") ->
        text.hasAny("技能", "skill", "专项", "按照", "规则", "文档")
    name.contains("javascript") ->
        text.hasAny("计算", "运行", "脚本", "javascript", "js", "代码")
    name.contains("write") || name.contains("zip") || name.contains("file") ->
        text.hasAny("写文件", "生成文件", "压缩包", "zip", "下载", "导出")
    else -> false
}

private fun String.hasAny(vararg needles: String): Boolean = needles.any { contains(it) }

private fun isStudyAppPlanQuery(text: String): Boolean =
    text.hasAny(
        "考研",
        "学习计划",
        "今日计划",
        "今天计划",
        "明日计划",
        "明天计划",
        "今日任务",
        "今天任务",
        "明日任务",
        "明天任务",
        "今日待办",
        "今天待办",
        "待办",
        "划掉",
        "打钩",
        "完成",
        "番茄钟",
        "番茄",
        "夸夸值",
        "碎片",
        "抽卡",
        "学习 app",
        "学习app",
    )

private fun isExplicitCalendarQuery(text: String): Boolean =
    text.hasAny("系统日历", "手机日历", "日历", "会议", "课表")

fun Tool.withHumanLikeToolPrompt(): Tool {
    val guidance = humanLikeToolGuidance(name)
    if (guidance.isBlank()) return this

    return copy(
        systemPrompt = { model, messages ->
            listOf(
                systemPrompt(model, messages),
                guidance,
            ).filter { it.isNotBlank() }.joinToString("\n")
        }
    )
}

fun List<Tool>.withHumanLikeToolPrompts(): List<Tool> = map { it.withHumanLikeToolPrompt() }

internal fun humanLikeToolGuidance(toolName: String): String {
    val name = toolName.removePrefix("mcp__").lowercase()
    return when {
        name == "set_lulu_expression_state" ->
            "需要更新露露状态栏/心声时使用；可填内心独白，正文自然聊天，不提工具名。"

        name == "get_weather" || name.contains("weather") ->
            "问天气/温度/雨雪/带伞时使用；自然回答，不提工具名。"
        name in setOf("get_location", "explore_nearby") || name.contains("location") || name.contains("nearby") ->
            "问位置/路线/附近时使用；把结果当感知，不提工具名。"

        name.contains("battery") ->
            "问电量/充电/耗电时使用；不提工具名。"

        name.contains("usage") || name.contains("app") ->
            "判断分心、疲惫或应用使用情况时使用。"

        name.contains("health") || name.contains("sleep") || name.contains("heart") || name.contains("gadgetbridge") ->
            "健康、睡眠、心率、运动、作息相关时使用。"

        name.contains("notification") ->
            "问通知、未读、消息打扰时使用。"

        name.contains("time") || name.contains("calendar") ->
            "问手机系统时间、日期、日程或日历时使用；学习 App 的今日计划不要用它。"

        name == "today_study_plan" ->
            "问考研计划、学习待办、完成/划掉任务、番茄钟、夸夸值时使用；这是学习 App 本地记录。"

        name.contains("camera") ->
            "仅在用户允许或明确要看环境时使用。"

        name.contains("clipboard") || name.contains("sms") || name.contains("alarm") || name.contains("write") ->
            "敏感或会改设备状态；除非用户明确要求，否则先确认。"

        else -> ""
    }
}
