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
)

enum class ProactiveReminderKind {
    SLEEP,
    SCHEDULE,
    GENERAL,
}

object ProactiveReminderPlanner {
    private const val MIN_DELAY_MILLIS = 60_000L
    private const val DEFAULT_SLEEP_DELAY_MINUTES = 30L

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
            combined.containsAny(GENERAL_REMINDER_WORDS) -> ProactiveReminderKind.GENERAL
            else -> return null
        }

        val triggerAt = findExplicitRelativeTime(combined, nowMillis)
            ?: findExplicitClockTime(combined, nowMillis, zoneId)
            ?: when (kind) {
                ProactiveReminderKind.SLEEP -> nowMillis + DEFAULT_SLEEP_DELAY_MINUTES * 60_000L
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
                ProactiveReminderKind.GENERAL -> "刚才聊到了需要提醒的事情，露露决定到点主动找你。"
            },
            userText = userText.take(160),
        )
    }

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

    private val GENERAL_REMINDER_WORDS = setOf(
        "提醒我", "记得叫我", "到时候叫我", "一会儿叫我"
    )
}
