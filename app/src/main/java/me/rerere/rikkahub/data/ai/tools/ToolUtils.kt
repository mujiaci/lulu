package me.rerere.rikkahub.data.ai.tools

import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessage

fun List<Tool>.deduplicateByToolName(): List<Tool> = distinctBy { it.name }

@Suppress("UNUSED_PARAMETER")
fun List<Tool>.selectRelevantToolsForPrompt(messages: List<UIMessage>): List<Tool> = this

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
