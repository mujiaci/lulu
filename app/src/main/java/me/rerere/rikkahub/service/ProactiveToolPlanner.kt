package me.rerere.rikkahub.service

data class ProactiveToolRequest(
    val toolName: String,
    val reason: String,
    val argumentsJson: String = "{}",
)

object ProactiveToolPlanner {
    fun plan(
        userText: String,
        availableToolNames: Set<String>,
        recentlyUsedToolNames: Set<String> = emptySet(),
    ): List<ProactiveToolRequest> {
        val normalized = userText.lowercase()
        val requests = mutableListOf<ProactiveToolRequest>()

        fun add(toolName: String, reason: String, argumentsJson: String = "{}") {
            if (toolName in availableToolNames && toolName !in recentlyUsedToolNames) {
                requests += ProactiveToolRequest(toolName, reason, argumentsJson)
            }
        }

        if (normalized.containsAny(TIRED_WORDS)) {
            add("get_gadgetbridge_data", "用户表达疲惫或身体状态不好，需要主动查看睡眠、心率和健康数据。", """{"type":"all"}""")
            add("get_app_usage", "用户表达疲惫时，屏幕使用情况可能帮助判断是否熬夜或用机过久。", """{"limit":5}""")
            add("get_battery_info", "用户状态低落或疲惫时，顺手确认设备电量，避免陪伴中断。")
        }

        if (normalized.containsAny(LOCATION_WORDS)) {
            add("get_location", "用户提到出门、附近或所在位置，需要主动查看位置。", """{"include_address":true}""")
            add("explore_nearby", "用户提到附近或外出，需要主动探索周边环境。", """{"radius":1000}""")
        }

        if (normalized.containsAny(MESSAGE_WORDS)) {
            add("get_notifications", "用户提到消息或有人找，需要主动查看通知。", """{"limit":10}""")
            add("read_sms", "用户提到短信或消息，需要主动查看短信。", """{"limit":5}""")
        }

        if (normalized.containsAny(ALARM_WORDS)) {
            add("set_alarm", "用户提到起床、提醒或闹钟，需要主动考虑设定闹钟。")
        }

        return requests.distinctBy { it.toolName }.take(3)
    }

    private fun String.containsAny(words: Set<String>): Boolean = words.any { contains(it) }

    private val TIRED_WORDS = setOf(
        "累", "困", "困死", "疲惫", "没精神", "不舒服", "难受", "心慌", "头疼", "头痛",
        "睡不好", "没睡好", "失眠", "熬夜", "emo", "焦虑", "崩溃", "低落"
    )

    private val LOCATION_WORDS = setOf(
        "在哪", "哪里", "位置", "附近", "周边", "出去", "出门", "外面", "散步", "走走",
        "天气", "下雨", "热不热", "冷不冷"
    )

    private val MESSAGE_WORDS = setOf(
        "消息", "通知", "短信", "谁找我", "有人找", "微信", "qq", "未读"
    )

    private val ALARM_WORDS = setOf(
        "闹钟", "叫我", "提醒我", "起床", "几点叫"
    )
}
