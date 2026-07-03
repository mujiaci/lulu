package me.rerere.rikkahub.service

import kotlinx.serialization.Serializable

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
        val kind = when {
            deadlineAtMillis != null || text.containsAny(DEADLINE_WORDS) -> LivingPresenceEventKind.DEADLINE
            targetAtMillis != null || text.containsAny(WAKE_WORDS) -> LivingPresenceEventKind.WAKE_UP
            text.containsAny(HEALTH_WORDS) -> LivingPresenceEventKind.HEALTH_SAFETY
            text.containsAny(STUDY_WORDS) -> LivingPresenceEventKind.STUDY_FOCUS
            else -> LivingPresenceEventKind.ORDINARY_SILENCE
        }
        return LivingPresenceEvent(
            kind = kind,
            assistantId = assistantId,
            assistantName = assistantName,
            userText = userText,
            assistantText = assistantText,
            rawSignals = rawSignals(kind, text),
            apiPlan = buildApiPlan(kind, hasTimeSignal = hasTimeSignal(text) || targetAtMillis != null || deadlineAtMillis != null),
            createdAt = nowMillis,
            targetAtMillis = targetAtMillis,
            deadlineAtMillis = deadlineAtMillis,
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

    private fun rawSignals(kind: LivingPresenceEventKind, text: String): List<String> = buildList {
        add(kind.name.lowercase())
        when (kind) {
            LivingPresenceEventKind.HEALTH_SAFETY -> addAll(HEALTH_WORDS.filter { text.contains(it) })
            LivingPresenceEventKind.STUDY_FOCUS -> addAll(STUDY_WORDS.filter { text.contains(it) })
            LivingPresenceEventKind.DEADLINE -> addAll(DEADLINE_WORDS.filter { text.contains(it) })
            LivingPresenceEventKind.WAKE_UP -> addAll(WAKE_WORDS.filter { text.contains(it) })
            LivingPresenceEventKind.ORDINARY_SILENCE -> add("no_explicit_risk")
        }
    }.distinct()

    private fun hasTimeSignal(text: String): Boolean =
        text.contains(Regex("\\d+\\s*(点|:|：|min|分钟|小时|h)")) ||
            listOf("今晚", "今天", "明天", "ddl", "截止", "到点").any { text.contains(it) }

    private fun String.containsAny(words: Set<String>): Boolean = words.any { contains(it) }

    private val HEALTH_WORDS = setOf("肚子", "胃", "头", "肚子疼", "肚子痛", "胃疼", "胃痛", "难受", "不舒服", "头疼", "头痛", "疼", "痛")
    private val STUDY_WORDS = setOf("学习", "复习", "背书", "刷题", "写作业", "自习", "看书", "专业课", "考研")
    private val DEADLINE_WORDS = setOf("ddl", "截止", "交", "提交", "今晚", "今天之前", "点前", "之前完成")
    private val WAKE_WORDS = setOf("起床", "叫醒", "闹钟", "醒来")
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
