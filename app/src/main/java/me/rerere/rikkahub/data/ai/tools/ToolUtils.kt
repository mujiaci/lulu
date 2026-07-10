package me.rerere.rikkahub.data.ai.tools

import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessage

fun List<Tool>.deduplicateByToolName(): List<Tool> = distinctBy { it.name }

fun List<Tool>.selectRelevantToolsForPrompt(messages: List<UIMessage>): List<Tool> {
    val latestUserText = messages.lastOrNull()?.toText().orEmpty()
    val asksForStudyPlan = STUDY_PLAN_MARKERS.any { latestUserText.contains(it, ignoreCase = true) }
    return if (asksForStudyPlan) filterNot { it.name == "calendar_tool" } else this
}

fun List<Tool>.passivePerceptionTools(): List<Tool> = filter { it.name in PASSIVE_PERCEPTION_TOOL_NAMES }

fun List<Tool>.activeModelTools(): List<Tool> = filterNot { it.name in PASSIVE_PERCEPTION_TOOL_NAMES }

fun List<Tool>.withConciseToolDescriptions(): List<Tool> = map { tool ->
    tool.copy(description = conciseToolDescription(tool).take(MAX_ACTIVE_TOOL_DESCRIPTION_LENGTH))
}

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
            "需要更新当前角色状态栏或心声时使用；可填内心独白，正文自然聊天，不提工具名。"

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
            "当角色想确认环境、桌面、身边状态或需要视觉感知时可以主动使用；把结果当感知，不提工具名。"

        name.contains("clipboard") || name.contains("sms") || name.contains("alarm") || name.contains("write") ->
            "当角色已经形成明确意图时可以主动使用；这是角色的本地行动能力，不要机械等待用户重复确认。"

        else -> ""
    }
}

private fun conciseToolDescription(tool: Tool): String = when (tool.name.removePrefix("mcp__")) {
    "ask_user" -> "Ask the user for missing information or confirmation."
    "calendar_tool" -> "Read, create, or delete device calendar events."
    "camera_capture" -> "Capture a camera image after the character decides visual confirmation is needed."
    "clipboard_tool" -> "Read or write plain text in the device clipboard."
    "control_music" -> "Control device music playback."
    "eval_javascript" -> "Run a small JavaScript calculation."
    "explore_nearby" -> "Search nearby places when local surroundings matter."
    "favorite_user_message" -> "Favorite the current user message when the character genuinely wants to keep it."
    "read_sms" -> "Read device SMS when message content is explicitly relevant."
    "scrape_web" -> "Read the content of a web page."
    "search_web" -> "Search the web for current external information."
    "set_alarm" -> "Set a device alarm for a specific hour and minute."
    "set_lulu_expression_state" -> "Update the character's visible state and private first-person thought for this turn."
    "text_to_speech" -> "Create replayable speech audio for supplied text."
    "today_study_plan" -> "Read or update the app-local study plan, tasks, focus sessions, and progress."
    "use_skill" -> "Use an enabled skill for a specialized task."
    "write_files" -> "Write requested content to local files."
    "write_lulu_journal" -> "Write a genuine first-person character diary entry when the character chooses to keep one."
    else -> tool.description.lineSequence().map(String::trim).filter(String::isNotBlank).joinToString(" ")
}

private val PASSIVE_PERCEPTION_TOOL_NAMES = setOf(
    "get_time_info",
    "get_battery_info",
    "get_app_usage",
    "get_gadgetbridge_data",
    "get_location",
    "get_weather",
    "get_notifications",
)

private val STUDY_PLAN_MARKERS = listOf(
    "考研",
    "学习计划",
    "今日计划",
    "今日待办",
    "学习待办",
    "番茄钟",
    "夸夸值",
)

private const val MAX_ACTIVE_TOOL_DESCRIPTION_LENGTH = 180
