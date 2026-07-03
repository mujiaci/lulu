package me.rerere.rikkahub.service

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@Serializable
data class LivingPresenceEvent(
    val kind: LivingPresenceEventKind,
    val assistantId: String = "",
    val assistantName: String,
    val userText: String,
    val assistantText: String,
    val rawSignals: List<String>,
    val apiPlan: LivingApiPlan,
    val createdAt: Long = System.currentTimeMillis(),
    val targetAtMillis: Long? = null,
    val deadlineAtMillis: Long? = null,
)

@Serializable
enum class LivingPresenceEventKind {
    ORDINARY_SILENCE,
    HEALTH_SAFETY,
    STUDY_FOCUS,
    DEADLINE,
    WAKE_UP,
}

@Serializable
data class LivingApiPlan(
    val mainApiTasks: List<LivingApiTask>,
    val secondaryApiTasks: List<LivingApiTask>,
    val ruleTasks: List<LivingApiTask>,
)

@Serializable
enum class LivingApiTask {
    BDI_JUDGEMENT,
    EMOTION_EVALUATION,
    HYPOTHESIS_REASONING,
    SPEECH_DECISION,
    INNER_MONOLOGUE,
    PERSONA_CONFLICT_CHECK,
    TOOL_ARBITRATION,
    TIME_EXTRACTION,
    EVENT_TAGGING,
    TOOL_RESULT_COMPRESSION,
    TODAY_PLAN_SUMMARY,
    LOW_RISK_SUMMARY,
    EXACT_SCHEDULING,
    COOLDOWN_LIMIT,
    PERMISSION_CHECK,
    LOCAL_STATE_READ,
}

object LivingPresenceEventExtractor {
    fun extract(
        assistantId: String = "",
        assistantName: String,
        userText: String,
        assistantText: String,
        nowMillis: Long = System.currentTimeMillis(),
        targetAtMillis: Long? = null,
        deadlineAtMillis: Long? = null,
    ): LivingPresenceEvent {
        val text = "$userText\n$assistantText".lowercase()
        val parsedTime = parseTimeSignal(text, nowMillis)
        val effectiveTargetAt = targetAtMillis ?: parsedTime.targetAtMillis
        val effectiveDeadlineAt = deadlineAtMillis ?: parsedTime.deadlineAtMillis
        val kind = when {
            text.containsAny(HEALTH_WORDS) -> LivingPresenceEventKind.HEALTH_SAFETY
            effectiveDeadlineAt != null || text.containsAny(DEADLINE_WORDS) -> LivingPresenceEventKind.DEADLINE
            effectiveTargetAt != null || text.containsAny(WAKE_WORDS) -> LivingPresenceEventKind.WAKE_UP
            text.containsAny(STUDY_WORDS) -> LivingPresenceEventKind.STUDY_FOCUS
            else -> LivingPresenceEventKind.ORDINARY_SILENCE
        }
        val hasExtractedTime = hasTimeSignal(text) || effectiveTargetAt != null || effectiveDeadlineAt != null
        return LivingPresenceEvent(
            kind = kind,
            assistantId = assistantId,
            assistantName = assistantName,
            userText = userText,
            assistantText = assistantText,
            rawSignals = rawSignals(kind, text, parsedTime.rawSignal),
            apiPlan = buildApiPlan(kind, hasTimeSignal = hasExtractedTime),
            createdAt = nowMillis,
            targetAtMillis = effectiveTargetAt,
            deadlineAtMillis = effectiveDeadlineAt,
        )
    }

    private fun buildApiPlan(kind: LivingPresenceEventKind, hasTimeSignal: Boolean): LivingApiPlan {
        val main = buildList {
            add(LivingApiTask.BDI_JUDGEMENT)
            add(LivingApiTask.EMOTION_EVALUATION)
            add(LivingApiTask.HYPOTHESIS_REASONING)
            add(LivingApiTask.SPEECH_DECISION)
            add(LivingApiTask.INNER_MONOLOGUE)
            add(LivingApiTask.PERSONA_CONFLICT_CHECK)
            if (kind == LivingPresenceEventKind.HEALTH_SAFETY || kind == LivingPresenceEventKind.DEADLINE) {
                add(LivingApiTask.TOOL_ARBITRATION)
            }
        }
        val secondary = buildList {
            add(LivingApiTask.EVENT_TAGGING)
            if (hasTimeSignal) add(LivingApiTask.TIME_EXTRACTION)
            add(LivingApiTask.TOOL_RESULT_COMPRESSION)
            if (kind == LivingPresenceEventKind.STUDY_FOCUS || kind == LivingPresenceEventKind.DEADLINE) {
                add(LivingApiTask.TODAY_PLAN_SUMMARY)
            }
            add(LivingApiTask.LOW_RISK_SUMMARY)
        }
        val rules = listOf(
            LivingApiTask.EXACT_SCHEDULING,
            LivingApiTask.COOLDOWN_LIMIT,
            LivingApiTask.PERMISSION_CHECK,
            LivingApiTask.LOCAL_STATE_READ,
        )
        return LivingApiPlan(mainApiTasks = main, secondaryApiTasks = secondary, ruleTasks = rules)
    }

    private fun rawSignals(kind: LivingPresenceEventKind, text: String, timeSignal: String?): List<String> = buildList {
        add(kind.name.lowercase())
        timeSignal?.let { add(it) }
        when (kind) {
            LivingPresenceEventKind.HEALTH_SAFETY -> addAll(HEALTH_WORDS.filter { text.contains(it) })
            LivingPresenceEventKind.STUDY_FOCUS -> addAll(STUDY_WORDS.filter { text.contains(it) })
            LivingPresenceEventKind.DEADLINE -> addAll(DEADLINE_WORDS.filter { text.contains(it) })
            LivingPresenceEventKind.WAKE_UP -> addAll(WAKE_WORDS.filter { text.contains(it) })
            LivingPresenceEventKind.ORDINARY_SILENCE -> add("no_explicit_risk")
        }
    }.distinct()

    private fun hasTimeSignal(text: String): Boolean =
        text.contains(Regex("[\\d一二两三四五六七八九十]+\\s*(点|:|：|min|分钟|分|个小时|小时|h)")) ||
            listOf("今晚", "明早", "明晚", "今天", "明天", "ddl", "截止", "到点", "提醒").any { text.contains(it) }

    private fun parseTimeSignal(text: String, nowMillis: Long): ParsedTimeSignal {
        parseRelativeTimeSignal(text, nowMillis).takeUnless { it.isEmpty() }?.let { return it }
        val absoluteTimeRegex = Regex(
            """(今天|明天|后天|今晚|明早|明晚)?\s*""" +
                """(早上|上午|中午|下午|晚上|夜里|凌晨)?\s*""" +
                """([0-9一二两三四五六七八九十]{1,3})\s*(?::|：|点)\s*""" +
                """([0-9一二两三四五六七八九十]{1,2}|半)?"""
        )
        val timeMatch = absoluteTimeRegex
            .find(text)
            ?: return ParsedTimeSignal()
        val dateWord = timeMatch.groupValues[1]
        val period = timeMatch.groupValues[2]
        val hourRaw = timeMatch.groupValues[3]
        val minuteRaw = timeMatch.groupValues[4]
        val hourBase = parseChineseNumber(hourRaw) ?: return ParsedTimeSignal()
        val minuteBase = when {
            minuteRaw == "半" -> 30
            minuteRaw.isNotBlank() -> parseChineseNumber(minuteRaw) ?: 0
            else -> 0
        }
        var hour = hourBase
        val isEvening = period == "下午" || period == "晚上" || period == "夜里" ||
            dateWord == "今晚" || dateWord == "明晚"
        if (isEvening && hour in 1..11) {
            hour += 12
        }
        if (period == "中午" && hour in 1..10) {
            hour += 12
        }
        hour = hour.coerceIn(0, 23)
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        val dayOffset = when (dateWord) {
            "明天", "明早", "明晚" -> 1L
            "后天" -> 2L
            else -> 0L
        }
        var targetDate = LocalDate.now(zone).plusDays(dayOffset)
        var target = LocalDateTime.of(targetDate, LocalTime.of(hour, minuteBase.coerceIn(0, 59)))
        if (dayOffset == 0L && target.isBefore(now.plusMinutes(1))) {
            targetDate = targetDate.plusDays(1)
            target = LocalDateTime.of(targetDate, LocalTime.of(hour, minuteBase.coerceIn(0, 59)))
        }
        val millis = target.atZone(zone).toInstant().toEpochMilli()
        val isDeadline = text.containsAny(DEADLINE_WORDS) || text.contains("前") || text.contains("之前")
        val isWake = text.containsAny(WAKE_WORDS) || text.containsAny(REMINDER_WORDS)
        val raw = "time_signal=${timeMatch.value.trim()}@${target}"
        return when {
            isDeadline -> ParsedTimeSignal(deadlineAtMillis = millis, rawSignal = raw)
            isWake -> ParsedTimeSignal(targetAtMillis = millis, rawSignal = raw)
            else -> ParsedTimeSignal(rawSignal = raw)
        }
    }

    private fun parseRelativeTimeSignal(text: String, nowMillis: Long): ParsedTimeSignal {
        val timeMatch = Regex("""([0-9一二两三四五六七八九十]{1,3})\s*(个小时|小时|分钟|分|min|h)\s*(后|以后|之后)?""")
            .find(text)
            ?: return ParsedTimeSignal()
        val amount = parseChineseNumber(timeMatch.groupValues[1]) ?: return ParsedTimeSignal()
        val unit = timeMatch.groupValues[2]
        val delayMinutes = when (unit) {
            "个小时", "小时", "h" -> amount * 60L
            else -> amount.toLong()
        }.coerceAtLeast(1L)
        val millis = nowMillis + delayMinutes * 60_000L
        val isDeadline = text.containsAny(DEADLINE_WORDS) || text.contains("前") || text.contains("之前")
        val isWake = text.containsAny(WAKE_WORDS) || text.containsAny(REMINDER_WORDS)
        val raw = "relative_time_signal=${timeMatch.value.trim()}@+${delayMinutes}min"
        return when {
            isDeadline -> ParsedTimeSignal(deadlineAtMillis = millis, rawSignal = raw)
            isWake -> ParsedTimeSignal(targetAtMillis = millis, rawSignal = raw)
            else -> ParsedTimeSignal(rawSignal = raw)
        }
    }

    private fun parseChineseNumber(value: String): Int? {
        value.toIntOrNull()?.let { return it }
        if (value.isBlank()) return null
        val digits = mapOf(
            '零' to 0,
            '一' to 1,
            '二' to 2,
            '两' to 2,
            '三' to 3,
            '四' to 4,
            '五' to 5,
            '六' to 6,
            '七' to 7,
            '八' to 8,
            '九' to 9,
        )
        if ('十' !in value) {
            return value.mapNotNull { digits[it] }.joinToString("").toIntOrNull()
        }
        val parts = value.split('十')
        val tens = parts.getOrNull(0)?.takeIf { it.isNotBlank() }?.firstOrNull()?.let { digits[it] } ?: 1
        val ones = parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.firstOrNull()?.let { digits[it] } ?: 0
        return tens * 10 + ones
    }

    private fun String.containsAny(words: Set<String>): Boolean = words.any { contains(it) }

    private data class ParsedTimeSignal(
        val targetAtMillis: Long? = null,
        val deadlineAtMillis: Long? = null,
        val rawSignal: String? = null,
    ) {
        fun isEmpty(): Boolean = targetAtMillis == null && deadlineAtMillis == null && rawSignal == null
    }

    private val HEALTH_WORDS = setOf("肚子", "胃", "头", "肚子疼", "肚子痛", "胃疼", "胃痛", "难受", "不舒服", "头疼", "头痛", "疼", "痛")
    private val STUDY_WORDS = setOf("学习", "复习", "背书", "刷题", "写作业", "自习", "看书", "专业课", "考研")
    private val DEADLINE_WORDS = setOf("ddl", "截止", "交", "提交", "今晚", "今天之前", "点前", "之前完成")
    private val WAKE_WORDS = setOf("起床", "叫醒", "闹钟", "醒来")
    private val REMINDER_WORDS = setOf("叫我", "提醒我", "催我", "到点叫", "到时候叫")
}

object LivingBeliefStore {
    fun mergeEvent(
        existingIntents: List<LivingIntent>,
        event: LivingPresenceEvent,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<LivingIntent> {
        val eventKind = event.kind.toIntentKind()
        val match = existingIntents.firstOrNull { intent ->
            intent.kind == eventKind && intent.status != LivingIntentStatus.COMPLETED
        }
        if (match == null) {
            return existingIntents + RollingJudgmentLoop.createIntent(
                assistantName = event.assistantName,
                assistantId = event.assistantId,
                userText = event.userText,
                assistantText = event.assistantText,
                nowMillis = nowMillis,
                targetAtMillis = event.targetAtMillis,
                deadlineAtMillis = event.deadlineAtMillis,
            )
        }
        val updated = match.copy(
            belief = "${match.belief} 最新事件：${event.userText.take(120)}",
            hypotheses = (match.hypotheses + event.rawSignals.map { "事件信号：$it" })
                .distinct()
                .take(8),
            lastEvaluatedAt = nowMillis,
            targetAtMillis = event.targetAtMillis ?: match.targetAtMillis,
            deadlineAtMillis = event.deadlineAtMillis ?: match.deadlineAtMillis,
        )
        return existingIntents.map { if (it.id == match.id) updated else it }
    }

    private fun LivingPresenceEventKind.toIntentKind(): LivingIntentKind = when (this) {
        LivingPresenceEventKind.HEALTH_SAFETY -> LivingIntentKind.HEALTH_SAFETY
        LivingPresenceEventKind.ORDINARY_SILENCE -> LivingIntentKind.ORDINARY_SILENCE
        LivingPresenceEventKind.STUDY_FOCUS -> LivingIntentKind.STUDY_FOCUS
        LivingPresenceEventKind.DEADLINE -> LivingIntentKind.DEADLINE
        LivingPresenceEventKind.WAKE_UP -> LivingIntentKind.WAKE_UP
    }
}
