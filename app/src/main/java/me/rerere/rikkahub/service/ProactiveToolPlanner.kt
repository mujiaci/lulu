package me.rerere.rikkahub.service

data class ProactiveToolRequest(
    val toolName: String,
    val reason: String,
    val argumentsJson: String = "{}",
    val autoExecutable: Boolean = true,
)

object ProactiveToolPlanner {
    fun plan(
        userText: String,
        availableToolNames: Set<String>,
        recentlyUsedToolNames: Set<String> = emptySet(),
    ): List<ProactiveToolRequest> {
        val normalized = userText.lowercase()
        val requests = mutableListOf<ProactiveToolRequest>()

        fun addAuto(toolName: String, reason: String, argumentsJson: String = "{}") {
            if (toolName in availableToolNames && toolName !in recentlyUsedToolNames) {
                requests += ProactiveToolRequest(toolName, reason, argumentsJson)
            }
        }

        fun addCandidate(toolName: String, reason: String, argumentsJson: String = "{}") {
            if (toolName in availableToolNames && toolName !in recentlyUsedToolNames) {
                requests += ProactiveToolRequest(
                    toolName = toolName,
                    reason = reason,
                    argumentsJson = argumentsJson,
                    autoExecutable = false,
                )
            }
        }

        if (normalized.containsAny(TIRED_WORDS)) {
            addAuto("get_gadgetbridge_data", "用户表达疲惫或身体状态不好，需要主动查看睡眠、心率和健康数据。", """{"data_type":"all"}""")
            addAuto("get_app_usage", "用户表达疲惫时，屏幕使用情况可能帮助判断是否熬夜或用机过久。", """{"limit":5}""")
            addAuto("get_battery_info", "用户状态低落或疲惫时，顺手确认设备电量，避免陪伴中断。")
        }

        if (normalized.containsAny(LOCATION_WORDS)) {
            addAuto("get_location", "用户提到出门、附近或所在位置，需要主动查看位置。", """{"include_address":true}""")
            addAuto("explore_nearby", "用户提到附近或外出，需要主动探索周边环境。", """{"radius":1000}""")
        }

        if (normalized.containsAny(MESSAGE_WORDS)) {
            addAuto("get_notifications", "用户提到消息或有人找，需要先看低敏感度的通知摘要。", """{"limit":10}""")
            addCandidate("read_sms", "用户提到短信或消息；短信内容隐私较强，只有在用户明确同意时才读取。", """{"limit":5}""")
        }

        if (normalized.containsAny(ALARM_WORDS)) {
            addCandidate("set_alarm", "用户提到起床、提醒或闹钟；设置闹钟会改变设备状态，需要先确认具体时间和用户意图。")
        }

        if (normalized.containsAny(SCHEDULE_WORDS)) {
            addAuto("get_app_usage", "用户提到上课、学习或具体时间，需要判断现在是否可能正在忙。", """{"limit":5}""")
            addAuto("get_location", "用户提到上课或行程，位置线索可能帮助判断是否已经到达相关场景。", """{"include_address":true}""")
            addCandidate("calendar_tool", "用户提到课程、日程或安排；写入日历前需要确认标题和时间。", """{"action":"read","limit":5}""")
            addCandidate("set_alarm", "用户提到课程或具体时间，可以建议提前提醒；设置闹钟前需要用户确认。")
        }

        if (normalized.containsAny(MUSIC_WORDS)) {
            addAuto("control_music", "用户提到听歌或音乐状态，先只读取当前播放信息，不主动切歌或暂停。", """{"action":"get_now_playing"}""")
        }

        if (normalized.containsAny(CAMERA_WORDS)) {
            addCandidate("camera_capture", "用户提到看一下周围、桌面或拍照；摄像头必须在用户明确允许后再打开。")
        }

        if (normalized.containsAny(JOURNAL_WORDS)) {
            addCandidate("write_lulu_journal", "用户提到写入日志或记录下来；写入前需要确认记录内容。")
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
        "闹钟", "叫我", "提醒我", "起床", "几点叫", "记得叫"
    )

    private val SCHEDULE_WORDS = setOf(
        "上课", "有课", "课程", "下课", "自习", "补课", "会议", "开会", "日程", "安排",
        "八点", "七点", "六点", "九点", "十点", "7点", "8点", "9点", "10点"
    )

    private val MUSIC_WORDS = setOf(
        "音乐", "听歌", "歌单", "播放", "暂停", "切歌", "上一首", "下一首", "网易云", "qq音乐"
    )

    private val CAMERA_WORDS = setOf(
        "拍照", "摄像头", "看一下", "看看桌面", "看看周围", "桌面", "周围", "环境"
    )

    private val JOURNAL_WORDS = setOf(
        "日志", "日记", "记录下来", "记下来", "写下来", "存一下", "留档"
    )
}
