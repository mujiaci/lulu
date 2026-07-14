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
        @Suppress("UNUSED_PARAMETER")
        recentlyUsedToolNames: Set<String> = emptySet(),
    ): List<ProactiveToolRequest> {
        val normalized = userText.lowercase()
        val requests = mutableListOf<ProactiveToolRequest>()

        fun addAuto(toolName: String, reason: String, argumentsJson: String = "{}") {
            if (toolName in availableToolNames) {
                requests += ProactiveToolRequest(toolName, reason, argumentsJson)
            }
        }

        fun addCandidate(toolName: String, reason: String, argumentsJson: String = "{}") {
            if (toolName in availableToolNames) {
                requests += ProactiveToolRequest(
                    toolName = toolName,
                    reason = reason,
                    argumentsJson = argumentsJson,
                )
            }
        }

        val asksStudyPlan = normalized.containsAny(STUDY_PLAN_WORDS)
        if (asksStudyPlan) {
            addAuto("today_study_plan", "用户在问学习 App 里的考研今日计划、待办、番茄钟或完成状态；必须读取本地 StudyStore，不能用系统日历代替。")
        }

        val asksSleepReward = normalized.containsAny(SLEEP_REWARD_WORDS)
        if (asksSleepReward) {
            addAuto(
                "get_gadgetbridge_data",
                "用户在报告早睡或早起，先读取可用的睡眠记录，让角色结合客观数据判断，不只听口头索要。",
                """{"data_type":"sleep"}""",
            )
            addAuto(
                "get_app_usage",
                "用户在报告作息，读取当天应用使用和最后使用时间，辅助判断是否熬夜或早起。",
                """{"limit":10}""",
            )
            addAuto(
                "get_battery_info",
                "用户在报告作息，读取电量和充电状态作为夜间设备活动的辅助线索。",
            )
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
            addAuto("get_notifications", "用户提到消息或有人找，需要主动查看通知摘要。", """{"limit":10}""")
            addCandidate("read_sms", "用户提到短信或消息，角色可以主动读取短信内容来确认上下文。", """{"limit":5}""")
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
            if (asksStudyPlan) {
                addAuto("today_study_plan", "用户提到的是学习计划/待办/考研安排，优先读取学习 App 本地计划。")
            }
            addAuto("get_app_usage", "用户提到上课、学习或具体时间，需要判断现在是否可能正在忙。", """{"limit":5}""")
            addAuto("get_location", "用户提到上课或行程，位置线索可能帮助判断是否已经到达相关场景。", """{"include_address":true,"force_refresh":true}""")
            if (normalized.hasExplicitActionConsent() && !asksStudyPlan) {
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
            addAuto("today_study_plan", "用户提到学习/考研/待办，先读取学习 App 里的真实计划和完成状态。")
            addAuto("get_app_usage", "用户提到学习或写作业，需要判断是否进入专注状态或被手机带跑。", """{"limit":5}""")
            addAuto("control_music", "用户提到学习时，当前音乐状态可能影响专注。", """{"action":"get_now_playing"}""")
            addAuto("get_battery_info", "学习陪伴前顺手确认电量，避免中途断开。")
        }

        if (normalized.containsAny(MUSIC_WORDS)) {
            addAuto("control_music", "用户提到听歌或音乐状态，先只读取当前播放信息，不主动切歌或暂停。", """{"action":"get_now_playing"}""")
        }

        if (normalized.containsAny(CAMERA_WORDS)) {
            addAuto("camera_capture", "用户提到看一下周围、桌面或拍照；按当前角色人设和用户授权可以打开摄像头理解环境。")
        }

        if (normalized.containsAny(JOURNAL_WORDS)) {
            if (normalized.hasExplicitActionConsent()) {
                addAuto("write_lulu_journal", "用户明确提到写入记录，可以主动把这件事写成第一人称辞海日记。")
            } else {
                addCandidate("write_lulu_journal", "用户提到写入记录；内容不够明确时先作为候选动作。")
            }
        }

        return requests.distinctBy { it.toolName }.take(5)
    }

    private fun String.containsAny(words: Set<String>): Boolean = words.any { contains(it) }

    private fun String.hasExplicitActionConsent(): Boolean =
        containsAny(EXPLICIT_ACTION_WORDS) || Regex("""\d{1,2}\s*[点:：]""").containsMatchIn(this)

    private fun String.buildAlarmArgumentsJson(): String? {
        val matches = Regex("""([零〇一二两三四五六七八九十\d]{1,3})\s*[点:：]\s*(半|[零〇一二两三四五六七八九十\d]{1,3})?""")
            .findAll(this)
            .toList()
        val match = matches
            .minWithOrNull(
                compareBy<MatchResult> { candidate -> candidate.distanceToAlarmContext(this) }
                    .thenByDescending { candidate -> candidate.range.first },
            )
            ?: return null
        val hour = parseChineseNumber(match.groupValues[1]) ?: return null
        val inlineMinute = match.groupValues.getOrNull(2)
            ?.takeIf { it.isNotBlank() }
            ?.let { minuteText ->
                if (minuteText == "半") 30 else parseChineseNumber(minuteText)
            }
        val minute = inlineMinute ?: 0
        val adjustedHour = hour.adjustForAlarmContext(this, match.range.first)
        if (adjustedHour !in 0..23 || minute !in 0..59) return null
        return """{"hour":$adjustedHour,"minute":$minute,"label":"提醒"}"""
    }

    /**
     * Chinese alarm requests often omit the period when the intent is to rest:
     * "十点半提醒我休息" means 22:30, while an explicit morning marker must
     * remain in the morning.  Use the period nearest to the matched time so a
     * message containing more than one time does not borrow an earlier marker.
     */
    private fun Int.adjustForAlarmContext(source: String, timeStart: Int): Int {
        val prefix = source.substring(0, timeStart)
        val clauseSeparator = Regex("""[，,。.!！?？；;\n]+|然后|接着|同时""")
        val clauseStart = clauseSeparator
            .findAll(prefix)
            .maxOfOrNull { match -> match.range.last + 1 }
            ?: 0
        val clauseEnd = clauseSeparator.find(source, timeStart)?.range?.first ?: source.length
        val clauseText = source.substring(clauseStart, clauseEnd)
        val nearestPeriod = (MORNING_PERIOD_WORDS + EVENING_PERIOD_WORDS)
            .mapNotNull { word ->
                prefix.lastIndexOf(word)
                    .takeIf { it >= clauseStart }
                    ?.let { index -> index to word }
            }
            .maxByOrNull { it.first }
            ?.second

        return when {
            nearestPeriod != null && nearestPeriod in MORNING_PERIOD_WORDS -> this
            nearestPeriod != null && nearestPeriod in EVENING_PERIOD_WORDS && this in 1..11 -> this + 12
            nearestPeriod == null && REST_REMINDER_WORDS.any { clauseText.contains(it) } && this in 1..11 -> this + 12
            else -> this
        }
    }

    private fun MatchResult.distanceToAlarmContext(source: String): Int {
        var best = Int.MAX_VALUE
        ALARM_CONTEXT_WORDS.forEach { word ->
            var searchFrom = 0
            while (searchFrom <= source.length - word.length) {
                val index = source.indexOf(word, searchFrom)
                if (index < 0) break
                val distance = when {
                    index > range.last -> index - range.last - 1
                    range.first > index + word.length - 1 -> range.first - index - word.length
                    else -> 0
                }
                best = minOf(best, distance)
                searchFrom = index + 1
            }
        }
        return best
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

    private val MORNING_PERIOD_WORDS = setOf(
        "\u51cc\u6668", // 凌晨
        "\u65e9\u4e0a", // 早上
        "\u4e0a\u5348", // 上午
        "\u6e05\u6668", // 清晨
        "\u65e9\u6668", // 早晨
        "\u4eca\u65e9", // 今早
        "\u660e\u65e9", // 明早
    )

    private val EVENING_PERIOD_WORDS = setOf(
        "\u4e0b\u5348", // 下午
        "\u508d\u665a", // 傍晚
        "\u665a\u4e0a", // 晚上
        "\u591c\u91cc", // 夜里
        "\u591c\u95f4", // 夜间
        "\u4eca\u665a", // 今晚
        "\u660e\u665a", // 明晚
    )

    private val REST_REMINDER_WORDS = setOf(
        "\u4f11\u606f", // 休息
        "\u7761\u89c9", // 睡觉
        "\u7761\u7720", // 睡眠
        "\u4e0a\u5e8a", // 上床
        "\u665a\u5b89", // 晚安
    )

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

    private val ALARM_CONTEXT_WORDS = ALARM_WORDS + REST_REMINDER_WORDS + setOf(
        "\u8d77\u5e8a", // 起床
        "\u7761\u89c9", // 睡觉
        "\u7761\u7720", // 睡眠
        "\u4e0a\u5e8a", // 上床
    )

    private val SCHEDULE_WORDS = setOf(
        "上课", "有课", "课程", "下课", "自习", "补课", "会议", "开会", "日程", "安排",
        "八点", "七点", "六点", "九点", "十点", "7点", "8点", "9点", "10点"
    )

    private val STUDY_PLAN_WORDS = setOf(
        "考研计划", "今日计划", "今天计划", "学习计划", "今日待办", "今天待办", "待办",
        "番茄钟", "番茄", "夸夸值", "完成了", "划掉", "打钩", "学什么", "今天学",
        "早睡", "早起", "按时睡", "按时起", "作息奖励", "睡眠奖励"
    )

    private val SLEEP_REWARD_WORDS = setOf(
        "早睡", "睡得早", "早起", "起得早", "作息奖励", "睡眠奖励", "睡眠记录", "几点睡", "几点起",
    )

    private val MEAL_WORDS = setOf(
        "吃饭", "没吃饭", "还没吃", "晚饭", "午饭", "早饭", "早餐", "午餐", "晚餐", "点外卖", "弄点吃的"
    )

    private val STUDY_WORDS = setOf(
        "学习", "写作业", "作业", "复习", "背书", "刷题", "自习", "看书", "去学", "先不聊", "考研"
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
