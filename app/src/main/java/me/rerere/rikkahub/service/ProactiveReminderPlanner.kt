package me.rerere.rikkahub.service

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class ProactiveReminderPlan(
    val triggerAtMillis: Long,
    val kind: ProactiveReminderKind,
    val reason: String,
    val userText: String,
    val preferredToolNames: List<String> = emptyList(),
    val actionHints: List<ProactiveActionHint> = emptyList(),
)

fun LuluIntentPlan.toProactiveReminderPlan(
    userText: String,
    nowMillis: Long = System.currentTimeMillis(),
): ProactiveReminderPlan? {
    val delay = delayMinutes ?: if (shouldMessageNow) 1 else return null
    if (delay <= 0) return null
    val kind = when (intent) {
        LuluIntent.CARE_REMINDER -> when {
            reason.contains("吃") -> ProactiveReminderKind.MEAL
            reason.contains("学习") || reason.contains("写作业") -> ProactiveReminderKind.STUDY
            reason.contains("睡") -> ProactiveReminderKind.SLEEP
            else -> ProactiveReminderKind.GENERAL
        }
        LuluIntent.STAY_NEAR -> ProactiveReminderKind.STUDY
        LuluIntent.CHECK_CONTEXT -> ProactiveReminderKind.GENERAL
        LuluIntent.REACH_OUT -> ProactiveReminderKind.GENERAL
        LuluIntent.DO_NOT_DISTURB -> return null
    }
    return ProactiveReminderPlan(
        triggerAtMillis = nowMillis + delay * 60_000L,
        kind = kind,
        reason = "露露自主计划：$reason 语气：$tone",
        userText = userText.take(160),
        preferredToolNames = toolNames,
    )
}

data class ProactiveActionHint(
    val toolName: String,
    val reason: String,
    val argumentsJson: String = "{}",
    val autoExecutable: Boolean = true,
)

enum class ProactiveReminderKind {
    SLEEP,
    SCHEDULE,
    MEAL,
    STUDY,
    GENERAL,
}

object ProactiveReminderPlanner {
    private const val MIN_DELAY_MILLIS = 60_000L
    private const val DEFAULT_SLEEP_DELAY_MINUTES = 30L
    private const val DEFAULT_MEAL_DELAY_MINUTES = 20L
    private const val DEFAULT_STUDY_DELAY_MINUTES = 45L

    fun plan(
        userText: String,
        assistantText: String,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ProactiveReminderPlan? {
        val combined = "$userText\n$assistantText".lowercase()
        val kind = when {
            combined.containsAny(SLEEP_WORDS) -> ProactiveReminderKind.SLEEP
            combined.containsAny(SCHEDULE_WORDS) -> ProactiveReminderKind.SCHEDULE
            combined.containsAny(MEAL_WORDS) -> ProactiveReminderKind.MEAL
            combined.containsAny(STUDY_WORDS) -> ProactiveReminderKind.STUDY
            combined.containsAny(GENERAL_REMINDER_WORDS) -> ProactiveReminderKind.GENERAL
            else -> return null
        }

        val triggerAt = findExplicitRelativeTime(combined, nowMillis)
            ?: findExplicitClockTime(combined, nowMillis, zoneId)
            ?: when (kind) {
                ProactiveReminderKind.SLEEP -> nowMillis + DEFAULT_SLEEP_DELAY_MINUTES * 60_000L
                ProactiveReminderKind.MEAL -> nowMillis + DEFAULT_MEAL_DELAY_MINUTES * 60_000L
                ProactiveReminderKind.STUDY -> nowMillis + DEFAULT_STUDY_DELAY_MINUTES * 60_000L
                ProactiveReminderKind.SCHEDULE -> null
                ProactiveReminderKind.GENERAL -> null
            }
            ?: return null

        if (triggerAt - nowMillis < MIN_DELAY_MILLIS) return null
        return ProactiveReminderPlan(
            triggerAtMillis = triggerAt,
            kind = kind,
            reason = when (kind) {
                ProactiveReminderKind.SLEEP -> "刚才聊到了睡觉/休息，露露决定到点来催你睡觉。"
                ProactiveReminderKind.SCHEDULE -> "刚才聊到了课程/日程，露露决定到点来确认你的状态。"
                ProactiveReminderKind.MEAL -> "刚才聊到了吃饭，露露决定稍后来确认你有没有好好吃。"
                ProactiveReminderKind.STUDY -> "刚才聊到了学习/写作业，露露决定晚点来轻轻确认你的状态。"
                ProactiveReminderKind.GENERAL -> "刚才聊到了需要提醒的事情，露露决定到点主动找你。"
            },
            userText = userText.take(160),
            preferredToolNames = buildPreferredToolNames(kind),
            actionHints = buildActionHints(kind, combined),
        )
    }

    private fun buildPreferredToolNames(kind: ProactiveReminderKind): List<String> = when (kind) {
        ProactiveReminderKind.SLEEP -> listOf("get_gadgetbridge_data", "get_app_usage", "get_battery_info")
        ProactiveReminderKind.SCHEDULE -> listOf("get_location", "get_app_usage", "calendar_tool")
        ProactiveReminderKind.MEAL -> listOf("get_app_usage", "get_battery_info", "get_location")
        ProactiveReminderKind.STUDY -> listOf("get_app_usage", "control_music", "get_battery_info")
        ProactiveReminderKind.GENERAL -> listOf("get_app_usage", "get_battery_info")
    }

    private fun buildActionHints(kind: ProactiveReminderKind, text: String): List<ProactiveActionHint> = buildList {
        when (kind) {
            ProactiveReminderKind.SLEEP -> {
                add(
                    ProactiveActionHint(
                        toolName = "write_lulu_journal",
                        reason = "把这次催睡/休息约定记进露露日志，后续能延续关心。",
                        argumentsJson = """{"entry_type":"care","content":"用户刚才提到睡觉/休息，露露约好到点提醒。"}""",
                    )
                )
            }
            ProactiveReminderKind.SCHEDULE -> {
                add(
                    ProactiveActionHint(
                        toolName = "calendar_tool",
                        reason = "用户提到了课程/日程，到点前可以读取或写入日历来确认安排。",
                        argumentsJson = """{"action":"read","limit":5}""",
                    )
                )
                add(
                    ProactiveActionHint(
                        toolName = "set_alarm",
                        reason = "用户提到了上课/日程提醒，意图明确时可以主动设置闹钟。",
                    )
                )
            }
            ProactiveReminderKind.MEAL -> {
                add(
                    ProactiveActionHint(
                        toolName = "write_lulu_journal",
                        reason = "把这次吃饭关心记进露露日志，后续能延续照看。",
                        argumentsJson = """{"entry_type":"care","content":"用户刚才提到还没吃饭，露露约好稍后确认。"}""",
                    )
                )
            }
            ProactiveReminderKind.STUDY -> {
                add(
                    ProactiveActionHint(
                        toolName = "write_lulu_journal",
                        reason = "把这次学习/写作业约定记进露露日志，后续能延续陪伴。",
                        argumentsJson = """{"entry_type":"care","content":"用户刚才去学习/写作业，露露约好晚点轻轻确认。"}""",
                    )
                )
            }
            ProactiveReminderKind.GENERAL -> Unit
        }

        if (text.containsAny(JOURNAL_WORDS)) {
            add(
                ProactiveActionHint(
                    toolName = "write_lulu_journal",
                    reason = "用户明确提到记录/日志，可以把这件事写进露露日志。",
                    argumentsJson = """{"entry_type":"user_request","content":"用户希望稍后把这件事记录下来。"}""",
                )
            )
        }
        if (text.containsAny(ALARM_WORDS)) {
            add(
                ProactiveActionHint(
                    toolName = "set_alarm",
                    reason = "用户明确说要叫他/提醒他，可以根据具体时间设置闹钟。",
                )
            )
        }
    }.distinctBy { it.toolName }

    private fun findExplicitRelativeTime(text: String, nowMillis: Long): Long? {
        val match = Regex("""([一二两三四五六七八九十\d]+)\s*(分钟|分|小时|个小时)\s*后""").find(text)
            ?: return null
        val amount = parseChineseNumber(match.groupValues[1]) ?: return null
        val unit = match.groupValues[2]
        val minutes = if (unit.contains("小时")) amount * 60L else amount.toLong()
        return nowMillis + minutes * 60_000L
    }

    private fun findExplicitClockTime(text: String, nowMillis: Long, zoneId: ZoneId): Long? {
        val match = Regex("""([零〇一二两三四五六七八九十\d]{1,3})\s*[点:：]\s*([零〇一二两三四五六七八九十\d]{1,3})?""")
            .find(text)
            ?: return null
        val hour = parseChineseNumber(match.groupValues[1]) ?: return null
        val minute = match.groupValues.getOrNull(2)
            ?.takeIf { it.isNotBlank() }
            ?.let { parseChineseNumber(it) }
            ?: 0
        if (hour !in 0..23 || minute !in 0..59) return null

        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        val targetTime = LocalTime.of(hour, minute)
        val targetDate = if (targetTime.isAfter(now.toLocalTime())) {
            now.toLocalDate()
        } else {
            now.toLocalDate().plusDays(1)
        }
        return LocalDateTime.of(targetDate, targetTime)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
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

    private fun String.containsAny(words: Set<String>): Boolean =
        words.any { it in this }

    private val SLEEP_WORDS = setOf(
        "睡觉", "睡了", "睡啦", "晚安", "早点睡", "去睡", "想睡", "困了", "催我睡", "提醒我睡"
    )

    private val SCHEDULE_WORDS = setOf(
        "上课", "有课", "课程", "下课", "会议", "开会", "自习", "补课"
    )

    private val MEAL_WORDS = setOf(
        "吃饭", "没吃饭", "还没吃", "晚饭", "午饭", "早饭", "早餐", "午餐", "晚餐", "点外卖", "弄点吃的"
    )

    private val STUDY_WORDS = setOf(
        "学习", "写作业", "作业", "复习", "背书", "刷题", "自习", "看书", "去学", "先不聊"
    )

    private val GENERAL_REMINDER_WORDS = setOf(
        "提醒我", "记得叫我", "到时候叫我", "一会儿叫我"
    )

    private val ALARM_WORDS = setOf(
        "闹钟", "叫我", "提醒我", "起床", "几点叫", "记得叫"
    )

    private val JOURNAL_WORDS = setOf(
        "日志", "日记", "记录下来", "记下来", "写下来", "记进日志", "存一下", "留档"
    )
}
