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

fun CompanionIntentDecision.toProactiveReminderPlan(
    userText: String,
    nowMillis: Long = System.currentTimeMillis(),
): ProactiveReminderPlan? {
    val delay = delayMinutes ?: if (shouldMessageNow) 1 else return null
    if (delay <= 0) return null
    if (
        intent == CompanionIntent.OBSERVE &&
        !shouldScheduleFollowUpForUserTurn(userText = userText, reason = reason, delayMinutes = delay)
    ) {
        return null
    }
    val kind = when (intent) {
        CompanionIntent.FOLLOW_UP -> category.toProactiveReminderKind(reason)
        CompanionIntent.STAY_AVAILABLE,
        CompanionIntent.OBSERVE,
        CompanionIntent.REACH_OUT -> ProactiveReminderKind.GENERAL
        CompanionIntent.WAIT -> return null
    }
    return ProactiveReminderPlan(
        triggerAtMillis = nowMillis + delay * 60_000L,
        kind = kind,
        reason = "$reason 语气：$tone",
        userText = userText.take(160),
        preferredToolNames = toolNames,
    )
}

private fun String?.toProactiveReminderKind(reason: String): ProactiveReminderKind = when (this?.lowercase()) {
    "sleep" -> ProactiveReminderKind.SLEEP
    "schedule", "deadline", "wake" -> ProactiveReminderKind.SCHEDULE
    "meal" -> ProactiveReminderKind.MEAL
    "study" -> ProactiveReminderKind.STUDY
    else -> when {
        reason.contains("吃") -> ProactiveReminderKind.MEAL
        reason.contains("学习") || reason.contains("写作业") -> ProactiveReminderKind.STUDY
        reason.contains("睡") -> ProactiveReminderKind.SLEEP
        else -> ProactiveReminderKind.GENERAL
    }
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
            combined.containsAny(WAKE_WORDS) -> ProactiveReminderKind.SCHEDULE
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
                ProactiveReminderKind.SLEEP -> "刚才聊到了睡觉或休息，当前角色决定到点重新确认。"
                ProactiveReminderKind.SCHEDULE -> if (combined.containsAny(WAKE_WORDS)) {
                    "刚才明确提到了起床或叫醒目标，当前角色会把这个时间点放在心上。"
                } else {
                    "刚才聊到了课程或日程，当前角色决定到点重新确认。"
                }
                ProactiveReminderKind.MEAL -> "刚才聊到了吃饭，当前角色决定稍后重新确认。"
                ProactiveReminderKind.STUDY -> "刚才聊到了学习或任务，当前角色决定稍后重新确认。"
                ProactiveReminderKind.GENERAL -> "刚才明确提到了后续提醒，当前角色决定到点重新确认。"
            },
            userText = userText.take(160),
            preferredToolNames = buildPreferredToolNames(kind, combined),
            actionHints = buildActionHints(kind, combined, userText.lowercase()),
        )
    }

    private fun buildPreferredToolNames(kind: ProactiveReminderKind, text: String): List<String> = when {
        kind == ProactiveReminderKind.SCHEDULE && text.containsAny(WAKE_WORDS) ->
            listOf("get_gadgetbridge_data", "get_app_usage", "get_battery_info")
        kind == ProactiveReminderKind.SLEEP -> listOf("get_gadgetbridge_data", "get_app_usage", "get_battery_info")
        kind == ProactiveReminderKind.SCHEDULE -> listOf("get_location", "get_app_usage", "calendar_tool")
        kind == ProactiveReminderKind.MEAL -> listOf("get_app_usage", "get_battery_info", "get_location")
        kind == ProactiveReminderKind.STUDY -> listOf("get_app_usage", "control_music", "get_battery_info")
        else -> listOf("get_app_usage", "get_battery_info")
    }

    private fun buildActionHints(
        kind: ProactiveReminderKind,
        text: String,
        userText: String,
    ): List<ProactiveActionHint> = buildList {
        when (kind) {
            ProactiveReminderKind.SLEEP -> Unit
            ProactiveReminderKind.SCHEDULE -> {
                if (!text.containsAny(WAKE_WORDS)) {
                    add(
                        ProactiveActionHint(
                            toolName = "calendar_tool",
                            reason = "用户提到了课程/日程，到点前可以读取或写入日历来确认安排。",
                            argumentsJson = """{"action":"read","limit":5}""",
                        )
                    )
                }
                add(
                    ProactiveActionHint(
                        toolName = "set_alarm",
                        reason = if (text.containsAny(WAKE_WORDS)) {
                            "用户明确提到了起床或叫醒目标，可以根据具体时间主动设置闹钟。"
                        } else {
                            "用户提到了上课/日程提醒，意图明确时可以主动设置闹钟。"
                        },
                    )
                )
            }
            ProactiveReminderKind.MEAL -> Unit
            ProactiveReminderKind.STUDY -> Unit
            ProactiveReminderKind.GENERAL -> Unit
        }

        if (userText.containsAny(JOURNAL_WORDS)) {
            add(
                ProactiveActionHint(
                    toolName = "write_lulu_journal",
                    reason = "用户明确提到记录，可以把这件事写成第一人称辞海日记。",
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
        val match = Regex(
            """(?:(今天|今日|今晚|今早|明天|明日|明早|明晚|后天)\s*)?""" +
                """(?:(凌晨|早上|早晨|上午|中午|下午|傍晚|晚上)\s*)?""" +
                """([零〇一二两三四五六七八九十\d]{1,3})\s*[点时:：]\s*""" +
                """(半|[零〇一二两三四五六七八九十\d]{1,3})?\s*分?""",
        )
            .find(text)
            ?: return null
        val dateWord = match.groupValues[1]
        val periodWord = match.groupValues[2]
        val rawHour = parseChineseNumber(match.groupValues[3]) ?: return null
        val minuteWord = match.groupValues[4]
        val minute = when (minuteWord) {
            "半" -> 30
            "" -> 0
            else -> parseChineseNumber(minuteWord) ?: return null
        }
        val effectivePeriod = periodWord.ifBlank {
            when (dateWord) {
                "今晚", "明晚" -> "晚上"
                "今早", "明早" -> "早上"
                else -> ""
            }
        }
        val hour = when {
            effectivePeriod in EVENING_PERIODS && rawHour in 1..11 -> rawHour + 12
            effectivePeriod == "中午" && rawHour in 1..10 -> rawHour + 12
            effectivePeriod == "凌晨" && rawHour == 12 -> 0
            else -> rawHour
        }
        if (hour !in 0..23 || minute !in 0..59) return null

        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        val targetTime = LocalTime.of(hour, minute)
        val explicitDayOffset = when (dateWord) {
            "明天", "明日", "明早", "明晚" -> 1L
            "后天" -> 2L
            "今天", "今日", "今晚", "今早" -> 0L
            else -> null
        }
        val targetDate = explicitDayOffset?.let { now.toLocalDate().plusDays(it) }
            ?: if (targetTime.isAfter(now.toLocalTime())) now.toLocalDate() else now.toLocalDate().plusDays(1)
        val target = LocalDateTime.of(targetDate, targetTime)
        if (explicitDayOffset == 0L && !target.isAfter(now)) return null
        return target
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
        "上课", "有课", "课程", "下课", "会议", "开会", "自习", "补课", "起床", "叫醒", "闹钟"
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

    private val WAKE_WORDS = setOf(
        "起床", "叫醒", "闹钟", "几点叫", "记得叫"
    )

    private val EVENING_PERIODS = setOf("下午", "傍晚", "晚上")

    private val JOURNAL_WORDS = setOf(
        "日志", "日记", "记录下来", "记下来", "写下来", "记进日志", "存一下", "留档"
    )
}
