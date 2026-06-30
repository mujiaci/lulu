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
            addAuto("get_location", "用户提到出门、附近或所在位置，需要主动查看位置。", """{"include_address":true,"force_refresh":true}""")
            addAuto("explore_nearby", "用户提到附近或外出，需要主动探索周边环境。", """{"radius":1000,"force_refresh":true}""")
        }

        if (normalized.containsAny(MESSAGE_WORDS)) {
            addAuto("get_notifications", "用户提到消息或有人找，需要先看低敏感度的通知摘要。", """{"limit":10}""")
            addCandidate("read_sms", "用户提到短信或消息；短信内容隐私较强，只有在用户明确同意时才读取。", """{"limit":5}""")
        }

        if (normalized.containsAny(ALARM_WORDS)) {
            val alarmArgs = normalized.buildAlarmArgumentsJson()
            if (normalized.hasExplicitActionConsent() && alarmArgs != null) {
                addAuto("set_alarm", "用户明确提到叫他、提醒他或设置闹钟，并且给出了具体时间，可以主动完成提醒动作。", alarmArgs)
            } else {
                addCandidate("set_alarm", "用户提到起床、提醒或闹钟；时间或意图不够明确时先作为候选动作。")
            }
        }

        if (normalized.containsAny(SCHEDULE_WORDS)) {
            addAuto("get_app_usage", "用户提到上课、学习或具体时间，需要判断现在是否可能正在忙。", """{"limit":5}""")
            addAuto("get_location", "用户提到上课或行程，位置线索可能帮助判断是否已经到达相关场景。", """{"include_address":true,"force_refresh":true}""")
            if (normalized.hasExplicitActionConsent()) {
                addAuto("calendar_tool", "用户明确提到课程、日程或具体时间，可以主动读取或写入日历来延续安排。", """{"action":"read","limit":5}""")
                normalized.buildAlarmArgumentsJson()?.let { alarmArgs ->
                    addAuto("set_alarm", "用户明确提到课程提醒或叫他，并且给出了具体时间，可以主动设置提醒闹钟。", alarmArgs)
                } ?: addCandidate("set_alarm", "用户提到课程提醒或叫他，但没有可直接设置的具体闹钟时间。")
            } else {
                addCandidate("calendar_tool", "用户提到课程、日程或安排；标题或时间不够明确时先作为候选动作。", """{"action":"read","limit":5}""")
                addCandidate("set_alarm", "用户提到课程或具体时间，可以建议提前提醒；不够明确时先作为候选动作。")
            }
        }

        if (normalized.containsAny(MEAL_WORDS)) {
            addAuto("get_app_usage", "用户提到吃饭，先看是否还在刷手机或忙别的。", """{"limit":5}""")
            addAuto("get_battery_info", "用户要吃饭或可能出门时，顺手确认设备电量。")
            addAuto("get_location", "用户提到吃饭，位置线索能帮助判断是否在外面或适合推荐附近。", """{"include_address":true,"force_refresh":true}""")
            addAuto("explore_nearby", "用户提到吃饭，可以主动看看附近有没有合适的吃饭地点。", """{"radius":1000,"keyword":"餐厅 美食","force_refresh":true}""")
        }

        if (normalized.containsAny(STUDY_WORDS)) {
            addAuto("get_app_usage", "用户提到学习或写作业，需要判断是否进入专注状态或被手机带跑。", """{"limit":5}""")
            addAuto("control_music", "用户提到学习时，当前音乐状态可能影响专注。", """{"action":"get_now_playing"}""")
            addAuto("get_battery_info", "学习陪伴前顺手确认电量，避免中途断开。")
        }

        if (normalized.containsAny(MUSIC_WORDS)) {
            addAuto("control_music", "用户提到听歌或音乐状态，先只读取当前播放信息，不主动切歌或暂停。", """{"action":"get_now_playing"}""")
        }

        if (normalized.containsAny(CAMERA_WORDS)) {
            addAuto("camera_capture", "用户提到看一下周围、桌面或拍照；按露露人设可以主动打开摄像头理解用户环境。")
        }

        if (normalized.containsAny(JOURNAL_WORDS)) {
            if (normalized.hasExplicitActionConsent()) {
                addAuto("write_lulu_journal", "用户明确提到写入日志或记录下来，可以主动把这件事记进露露日志。")
            } else {
                addCandidate("write_lulu_journal", "用户提到写入日志或记录下来；内容不够明确时先作为候选动作。")
            }
        }

        return requests.distinctBy { it.toolName }.take(5)
    }

    private fun String.containsAny(words: Set<String>): Boolean = words.any { contains(it) }

    private fun String.hasExplicitActionConsent(): Boolean =
        containsAny(EXPLICIT_ACTION_WORDS) || Regex("""\d{1,2}\s*[点:：]""").containsMatchIn(this)

    private fun String.buildAlarmArgumentsJson(): String? {
        val match = Regex("""([零〇一二两三四五六七八九十\d]{1,3})\s*[点:：]\s*([零〇一二两三四五六七八九十\d]{1,3})?""")
            .findAll(this)
            .lastOrNull()
            ?: return null
        val hour = parseChineseNumber(match.groupValues[1]) ?: return null
        val inlineMinute = match.groupValues.getOrNull(2)
            ?.takeIf { it.isNotBlank() }
            ?.let { parseChineseNumber(it) }
        val trailingMinute = if (inlineMinute == null) {
            Regex("""[点:：]\s*([零〇一二两三四五六七八九十\d]{1,3})\s*(?:分|叫|提醒|闹钟)""")
                .findAll(this)
                .lastOrNull()
                ?.groupValues
                ?.getOrNull(1)
                ?.let { parseChineseNumber(it) }
        } else {
            null
        }
        val minute = inlineMinute ?: trailingMinute ?: 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return """{"hour":$hour,"minute":$minute,"label":"露露提醒"}"""
    }

    private fun parseChineseNumber(raw: String): Int? {
        raw.toIntOrNull()?.let { return it }
        if (raw.isBlank()) return null
        val normalized = raw.replace("两", "二").replace("〇", "零")
        val digitMap = mapOf(
            '零' to 0,
            '一' to 1,
            '二' to 2,
            '三' to 3,
            '四' to 4,
            '五' to 5,
            '六' to 6,
            '七' to 7,
            '八' to 8,
            '九' to 9,
        )
        if ('十' in normalized) {
            val parts = normalized.split("十", limit = 2)
            val tens = parts.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.singleOrNull()
                ?.let { digitMap[it] }
                ?: 1
            val ones = parts.getOrNull(1)
                ?.takeIf { it.isNotBlank() }
                ?.singleOrNull()
                ?.let { digitMap[it] }
                ?: 0
            return tens * 10 + ones
        }
        return normalized.mapNotNull { digitMap[it] }
            .takeIf { it.isNotEmpty() }
            ?.joinToString("")
            ?.toIntOrNull()
    }

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

    private val MEAL_WORDS = setOf(
        "吃饭", "没吃饭", "还没吃", "晚饭", "午饭", "早饭", "早餐", "午餐", "晚餐", "点外卖", "弄点吃的"
    )

    private val STUDY_WORDS = setOf(
        "学习", "写作业", "作业", "复习", "背书", "刷题", "自习", "看书", "去学", "先不聊"
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

    private val EXPLICIT_ACTION_WORDS = setOf(
        "帮我", "给我", "帮我记", "记得", "提醒我", "叫我", "到时候", "可以", "麻烦", "顺手"
    )
}
