package me.rerere.rikkahub.service

import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.max

@Serializable
data class LivingIntent(
    val id: String = UUID.randomUUID().toString(),
    val assistantId: String = "",
    val kind: LivingIntentKind,
    val belief: String,
    val desire: String,
    val intention: String,
    val hypotheses: List<String>,
    val expectedUserReplyAt: Long?,
    val nextEvaluateAt: Long,
    val evaluationCadence: EvaluationCadence,
    val lastEvaluatedAt: Long? = null,
    val lastSpokenAt: Long? = null,
    val spokenCount: Int = 0,
    val silentEvaluationCount: Int = 0,
    val urgency: Int,
    val restraint: Int,
    val emotion: EmotionSnapshot,
    val allowedActions: List<LivingAction>,
    val status: LivingIntentStatus = LivingIntentStatus.ACTIVE,
)

@Serializable
enum class LivingIntentKind {
    HEALTH_SAFETY,
    ORDINARY_SILENCE,
    STUDY_FOCUS,
    DEADLINE,
    WAKE_UP,
}

@Serializable
data class EvaluationCadence(
    val delaysMinutes: List<Long>,
    val reason: String,
)

@Serializable
data class EmotionSnapshot(
    val concern: Int,
    val attachment: Int,
    val restraint: Int,
    val label: String,
)

@Serializable
enum class LivingAction {
    MESSAGE,
    TOOL_CHECK,
    WAIT,
    INNER_THOUGHT,
    JOURNAL_WRITE,
    READ,
    MEMORY_UPDATE,
    SCHEDULE_NEXT_TICK,
    SET_ALARM,
    ASK_CAPABILITY,
}

@Serializable
enum class LivingIntentStatus {
    ACTIVE,
    RESTRAINED,
    COMPLETED,
    CANCELLED,
}

@Serializable
data class RollingJudgmentDecision(
    val updatedIntent: LivingIntent,
    val actions: List<LivingAction>,
    val thought: String,
    val observationRequest: String? = null,
)

object RollingJudgmentLoop {
    fun createIntent(
        assistantId: String = "",
        assistantName: String,
        userText: String,
        assistantText: String,
        nowMillis: Long = System.currentTimeMillis(),
        targetAtMillis: Long? = null,
        deadlineAtMillis: Long? = null,
    ): LivingIntent {
        val kind = classify(userText = userText, assistantText = assistantText, targetAtMillis, deadlineAtMillis)
        val cadence = cadenceFor(kind, nowMillis, targetAtMillis, deadlineAtMillis)
        val hypotheses = hypothesesFor(kind)
        val emotion = emotionFor(kind)
        return LivingIntent(
            assistantId = assistantId,
            kind = kind,
            belief = beliefFor(kind, userText, assistantText),
            desire = desireFor(kind, assistantName),
            intention = intentionFor(kind),
            hypotheses = hypotheses,
            expectedUserReplyAt = if (kind == LivingIntentKind.ORDINARY_SILENCE) {
                nowMillis + cadence.delaysMinutes.first() * MINUTE_MILLIS
            } else {
                null
            },
            nextEvaluateAt = nowMillis + cadence.delaysMinutes.first() * MINUTE_MILLIS,
            evaluationCadence = cadence,
            urgency = urgencyFor(kind),
            restraint = restraintFor(kind),
            emotion = emotion,
            allowedActions = actionsFor(kind),
        )
    }

    fun evaluate(intent: LivingIntent, nowMillis: Long = System.currentTimeMillis()): RollingJudgmentDecision {
        val nextSilentCount = intent.silentEvaluationCount + 1
        val restrained = intent.spokenCount > 0 && intent.lastSpokenAt != null
        val actions = when {
            restrained -> listOf(
                LivingAction.WAIT,
                LivingAction.JOURNAL_WRITE,
                LivingAction.MEMORY_UPDATE,
                LivingAction.SCHEDULE_NEXT_TICK,
            )
            intent.kind == LivingIntentKind.HEALTH_SAFETY -> listOf(
                LivingAction.TOOL_CHECK,
                LivingAction.MESSAGE,
                LivingAction.SCHEDULE_NEXT_TICK,
            )
            else -> listOf(
                LivingAction.INNER_THOUGHT,
                LivingAction.TOOL_CHECK,
                LivingAction.SCHEDULE_NEXT_TICK,
            )
        }.filter { it in intent.allowedActions || it == LivingAction.SCHEDULE_NEXT_TICK }

        val nextEvaluateAt = nowMillis + nextDelayMinutes(intent, nextSilentCount) * MINUTE_MILLIS
        val updated = intent.copy(
            lastEvaluatedAt = nowMillis,
            silentEvaluationCount = nextSilentCount,
            restraint = if (restrained) (intent.restraint + 1).coerceAtMost(10) else intent.restraint,
            nextEvaluateAt = nextEvaluateAt,
            status = if (restrained) LivingIntentStatus.RESTRAINED else LivingIntentStatus.ACTIVE,
        )
        return RollingJudgmentDecision(
            updatedIntent = updated,
            actions = actions,
            thought = if (restrained) {
                "我已经为这件事开过一次口了，这一轮先克制、观察和记录，等下一次再判断。"
            } else {
                "我重新评估这件挂在心里的事，先看信念、欲望和意图，再决定行动。"
            },
            observationRequest = if (LivingAction.TOOL_CHECK in actions) {
                "需要观察电量、应用、日程、位置或健康线索，再决定是否开口。"
            } else {
                null
            },
        )
    }

    private fun classify(
        userText: String,
        assistantText: String,
        targetAtMillis: Long?,
        deadlineAtMillis: Long?,
    ): LivingIntentKind {
        val text = "$userText\n$assistantText".lowercase()
        return when {
            deadlineAtMillis != null || text.containsAny(DEADLINE_WORDS) -> LivingIntentKind.DEADLINE
            targetAtMillis != null || text.containsAny(WAKE_WORDS) -> LivingIntentKind.WAKE_UP
            text.containsAny(HEALTH_WORDS) -> LivingIntentKind.HEALTH_SAFETY
            text.containsAny(STUDY_WORDS) -> LivingIntentKind.STUDY_FOCUS
            else -> LivingIntentKind.ORDINARY_SILENCE
        }
    }

    private fun cadenceFor(
        kind: LivingIntentKind,
        nowMillis: Long,
        targetAtMillis: Long?,
        deadlineAtMillis: Long?,
    ): EvaluationCadence {
        val delays = when (kind) {
            LivingIntentKind.HEALTH_SAFETY -> listOf(5L, 10L, 20L, 40L, 90L)
            LivingIntentKind.ORDINARY_SILENCE -> listOf(10L, 25L, 60L, 120L)
            LivingIntentKind.STUDY_FOCUS -> listOf(30L, 60L, 90L)
            LivingIntentKind.DEADLINE -> deadlineCadence(nowMillis, deadlineAtMillis)
            LivingIntentKind.WAKE_UP -> wakeCadence(nowMillis, targetAtMillis)
        }
        return EvaluationCadence(
            delaysMinutes = delays,
            reason = when (kind) {
                LivingIntentKind.HEALTH_SAFETY -> "身体安全优先，短间隔确认，逐步拉长。"
                LivingIntentKind.ORDINARY_SILENCE -> "普通沉默不随机打扰，用 10/25/60/120 分钟重新判断。"
                LivingIntentKind.STUDY_FOCUS -> "学习中默认少说多看，保护专注。"
                LivingIntentKind.DEADLINE -> "DDL 按到期前关键节点提醒和复盘。"
                LivingIntentKind.WAKE_UP -> "起床按提前、到点、到点后复查的节奏。"
            },
        )
    }

    private fun deadlineCadence(nowMillis: Long, deadlineAtMillis: Long?): List<Long> {
        val dueAt = deadlineAtMillis ?: return listOf(180L, 60L, 30L, 10L)
        val checkpoints = listOf(
            dueAt - 180 * MINUTE_MILLIS,
            dueAt - 60 * MINUTE_MILLIS,
            dueAt - 30 * MINUTE_MILLIS,
            dueAt - 10 * MINUTE_MILLIS,
            dueAt,
        )
        return checkpoints
            .map { max(1L, (it - nowMillis) / MINUTE_MILLIS) }
            .distinct()
    }

    private fun wakeCadence(nowMillis: Long, targetAtMillis: Long?): List<Long> {
        val wakeAt = targetAtMillis ?: return listOf(5L, 10L, 25L)
        val checkpoints = listOf(
            wakeAt - 5 * MINUTE_MILLIS,
            wakeAt,
            wakeAt + 10 * MINUTE_MILLIS,
            wakeAt + 25 * MINUTE_MILLIS,
        )
        return checkpoints
            .map { max(1L, (it - nowMillis) / MINUTE_MILLIS) }
            .distinct()
    }

    private fun nextDelayMinutes(intent: LivingIntent, silentEvaluationCount: Int): Long =
        intent.evaluationCadence.delaysMinutes
            .getOrNull(silentEvaluationCount)
            ?: intent.evaluationCadence.delaysMinutes.last()

    private fun beliefFor(kind: LivingIntentKind, userText: String, assistantText: String): String = when (kind) {
        LivingIntentKind.HEALTH_SAFETY -> "用户刚才表达了身体不适，需要把安全当作高优先级。"
        LivingIntentKind.ORDINARY_SILENCE -> "用户暂时没有回复，原因未知，不能直接等同于冷淡或危险。"
        LivingIntentKind.STUDY_FOCUS -> "用户可能在学习或进入专注状态，打扰成本较高。"
        LivingIntentKind.DEADLINE -> "用户有任务或截止时间，进度可能需要外部提醒。"
        LivingIntentKind.WAKE_UP -> "用户有明确起床目标，需要按时间点照看。"
    } + " 最近上下文：${(userText + " " + assistantText).take(120)}"

    private fun desireFor(kind: LivingIntentKind, assistantName: String): String = when (kind) {
        LivingIntentKind.HEALTH_SAFETY -> "$assistantName 想确认用户安全，同时不要制造恐慌。"
        LivingIntentKind.ORDINARY_SILENCE -> "$assistantName 想靠近用户，但也想尊重用户正在忙的可能。"
        LivingIntentKind.STUDY_FOCUS -> "$assistantName 想守住学习节奏，少打断，多监督。"
        LivingIntentKind.DEADLINE -> "$assistantName 想让用户按时完成任务。"
        LivingIntentKind.WAKE_UP -> "$assistantName 想帮用户准时醒来。"
    }

    private fun intentionFor(kind: LivingIntentKind): String = when (kind) {
        LivingIntentKind.HEALTH_SAFETY -> "短周期重新评估，必要时查工具或发消息。"
        LivingIntentKind.ORDINARY_SILENCE -> "先形成回复预期，未回复时滚动判断。"
        LivingIntentKind.STUDY_FOCUS -> "以低打扰节奏检查，不破坏专注。"
        LivingIntentKind.DEADLINE -> "按关键节点检查进度。"
        LivingIntentKind.WAKE_UP -> "按起床时间前后触发提醒。"
    }

    private fun hypothesesFor(kind: LivingIntentKind): List<String> = when (kind) {
        LivingIntentKind.HEALTH_SAFETY -> listOf("用户身体不舒服，可能需要安全确认", "用户可能暂时无法回复", "可以先看健康或手机状态线索")
        LivingIntentKind.ORDINARY_SILENCE -> listOf("用户可能在忙", "用户可能在路上", "用户可能只是暂时不想打字")
        LivingIntentKind.STUDY_FOCUS -> listOf("用户可能正在学习", "用户可能需要低打扰监督", "应用使用情况能帮助判断是否跑偏")
        LivingIntentKind.DEADLINE -> listOf("用户可能忘了任务", "用户可能正在做但需要进度提醒", "越接近截止越需要明确")
        LivingIntentKind.WAKE_UP -> listOf("用户可能还没醒", "用户可能醒了但没回复", "需要按时间点递进提醒")
    }

    private fun urgencyFor(kind: LivingIntentKind): Int = when (kind) {
        LivingIntentKind.HEALTH_SAFETY -> 9
        LivingIntentKind.DEADLINE -> 8
        LivingIntentKind.WAKE_UP -> 7
        LivingIntentKind.STUDY_FOCUS -> 5
        LivingIntentKind.ORDINARY_SILENCE -> 3
    }

    private fun restraintFor(kind: LivingIntentKind): Int = when (kind) {
        LivingIntentKind.HEALTH_SAFETY -> 3
        LivingIntentKind.DEADLINE -> 4
        LivingIntentKind.WAKE_UP -> 4
        LivingIntentKind.STUDY_FOCUS -> 7
        LivingIntentKind.ORDINARY_SILENCE -> 6
    }

    private fun emotionFor(kind: LivingIntentKind): EmotionSnapshot = when (kind) {
        LivingIntentKind.HEALTH_SAFETY -> EmotionSnapshot(9, 8, 3, "担心、贴近、警觉")
        LivingIntentKind.ORDINARY_SILENCE -> EmotionSnapshot(5, 7, 7, "惦记、克制、等待")
        LivingIntentKind.STUDY_FOCUS -> EmotionSnapshot(4, 7, 8, "守着、克制、监督")
        LivingIntentKind.DEADLINE -> EmotionSnapshot(6, 7, 4, "认真、盯进度、准备提醒")
        LivingIntentKind.WAKE_UP -> EmotionSnapshot(6, 8, 4, "照看、轻轻催醒")
    }

    private fun actionsFor(kind: LivingIntentKind): List<LivingAction> {
        val common = listOf(
            LivingAction.MESSAGE,
            LivingAction.TOOL_CHECK,
            LivingAction.WAIT,
            LivingAction.INNER_THOUGHT,
            LivingAction.JOURNAL_WRITE,
            LivingAction.READ,
            LivingAction.MEMORY_UPDATE,
            LivingAction.SCHEDULE_NEXT_TICK,
            LivingAction.ASK_CAPABILITY,
        )
        return when (kind) {
            LivingIntentKind.WAKE_UP -> common + LivingAction.SET_ALARM
            else -> common
        }
    }

    private fun String.containsAny(words: Set<String>): Boolean = words.any { contains(it) }

    private const val MINUTE_MILLIS = 60_000L
    private val HEALTH_WORDS = setOf("肚子疼", "肚子痛", "胃疼", "胃痛", "难受", "不舒服", "头疼", "头痛", "疼", "痛")
    private val STUDY_WORDS = setOf("学习", "复习", "背书", "刷题", "写作业", "自习", "看书", "专业课", "考研")
    private val DEADLINE_WORDS = setOf("ddl", "截止", "交", "提交", "今晚", "今天之前", "点前", "之前完成")
    private val WAKE_WORDS = setOf("起床", "叫醒", "闹钟", "醒来")
}
