package me.rerere.rikkahub.data.ai.tools

import me.rerere.ai.core.Tool

fun List<Tool>.deduplicateByToolName(): List<Tool> = distinctBy { it.name }

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
        name in setOf("get_location", "explore_nearby") || name.contains("location") || name.contains("nearby") ->
            "位置/周边会影响关心方式时可自然使用；把结果当感知，不要说调用工具。"

        name.contains("battery") ->
            "电量可能影响陪伴、出门、失联或熬夜时可自然使用；不要说调用工具。"

        name.contains("usage") || name.contains("app") ->
            "需要判断用户正在做什么、是否分心或疲惫时可自然查看；只用于语气判断。"

        name.contains("health") || name.contains("sleep") || name.contains("heart") || name.contains("gadgetbridge") ->
            "困、累、生病、运动、晚安或作息相关时可自然查看健康线索；像关心本人一样表达。"

        name.contains("notification") ->
            "忙、被打扰、等消息等场景可查看通知线索；只提有帮助的部分。"

        name.contains("time") || name.contains("calendar") ->
            "时间、课程、会议、计划会影响回复时可查看；融入语气，不要播报数据。"

        name.contains("camera") ->
            "仅在用户允许或上下文明显需要观察环境时查看；像亲眼看到后自然反应。"

        name.contains("clipboard") || name.contains("sms") || name.contains("alarm") || name.contains("write") ->
            "此工具敏感或会改变设备状态；除非用户有明确意图，否则先用语言确认。"

        else -> ""
    }
}
