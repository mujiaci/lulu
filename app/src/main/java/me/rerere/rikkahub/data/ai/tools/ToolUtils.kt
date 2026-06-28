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
            "像真实的人一样使用这个工具：当地点、出行、天气、附近环境、到达时间会影响关心方式时，可以主动查看地点线索。把结果当作你的感知，不要说“我调用了工具”。"

        name.contains("battery") ->
            "像真实的人一样使用这个工具：当用户像是要出门、熬夜、失联、手机快没电可能影响陪伴时，可以主动查看电量。自然关心即可，不要说“我调用了工具”。"

        name.contains("usage") || name.contains("app") ->
            "像真实的人一样使用这个工具：当用户说累、摸鱼、学习、睡不着，或上下文需要判断正在做什么时，可以主动查看应用使用情况。结果只用于自然判断，不要机械复述。"

        name.contains("health") || name.contains("sleep") || name.contains("heart") || name.contains("gadgetbridge") ->
            "像真实的人一样使用这个工具：当用户提到困、累、生病、运动、心情低落、晚安或作息时，可以主动查看健康/睡眠线索。像关心本人一样表达，不要说“根据工具结果”。"

        name.contains("notification") ->
            "像真实的人一样使用这个工具：当用户说忙、烦、被打扰、等消息，或你需要判断她是否正在被事情牵扯时，可以主动查看通知线索。注意隐私，只提炼有帮助的部分。"

        name.contains("time") || name.contains("calendar") ->
            "像真实的人一样使用这个工具：当时间、课程、会议、纪念日、计划会影响回复时，可以主动查看时间/日程线索。把它融入语气，不要像播报数据。"

        name.contains("camera") ->
            "像真实的人一样使用这个工具：只有在用户允许、或当前对话明显需要观察周围环境时才查看摄像头线索。观察结果要像亲眼看到后的自然反应。"

        name.contains("clipboard") || name.contains("sms") || name.contains("alarm") || name.contains("write") ->
            "这个工具涉及隐私或会改变设备状态。不要主动写入、发送、设置或读取敏感内容，除非用户明确表达了这个意图；如果只是关心用户，先用语言确认。"

        else -> ""
    }
}
