package me.rerere.rikkahub.service

import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.max

@Serializable
data class LivingIntent(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
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
    val targetAtMillis: Long? = null,
    val deadlineAtMillis: Long? = null,
    val urgency: Int,
    val restraint: Int,
    val emotion: EmotionSnapshot,
    val allowedActions: List<LivingAction>,
    val status: LivingIntentStatus = LivingIntentStatus.ACTIVE,
    val lastObservation: LivingObservation? = null,
    val lastJudgmentTrace: LivingJudgmentTrace? = null,
    val completedReason: String? = null,
    val capabilityRequests: List<LivingCapabilityRequest> = emptyList(),
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
    val tension: Int = 0,
    val disappointment: Int = 0,
    val relief: Int = 0,
    val lastChangedAt: Long? = null,
)

@Serializable
data class LivingObservation(
    val summary: String,
    val requestedTools: List<String> = emptyList(),
    val signals: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class LivingJudgmentTrace(
    val source: LivingJudgmentSource = LivingJudgmentSource.STRUCTURED_RULE_FALLBACK,
    val belief: String,
    val desire: String,
    val intention: String,
    val thought: String,
    val action: String,
    val observation: String,
    val decision: String,
    val nextEvaluateDelayMinutes: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
enum class LivingJudgmentSource {
    MAIN_API_STRUCTURED_JUDGMENT,
    MAIN_API_READY_CONTRACT,
    STRUCTURED_RULE_FALLBACK,
}

@Serializable
data class LivingCapabilityRequest(
    val capability: String,
    val reason: String,
    val createdAt: Long = System.currentTimeMillis(),
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
    val observation: LivingObservation? = null,
    val judgmentTrace: LivingJudgmentTrace? = null,
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
            createdAt = nowMillis,
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
            targetAtMillis = targetAtMillis,
            deadlineAtMillis = deadlineAtMillis,
        )
    }

    fun evaluate(
        intent: LivingIntent,
        nowMillis: Long = System.currentTimeMillis(),
        externalObservation: LivingObservation? = null,
        externalJudgmentTrace: LivingJudgmentTrace? = null,
    ): RollingJudgmentDecision {
        val nextSilentCount = intent.silentEvaluationCount + 1
        val restrained = intent.spokenCount > 0 && intent.lastSpokenAt != null
        val observation = externalObservation ?: observe(intent, nowMillis)
        val ruleActions = when {
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

        val actions = actionsFromStructuredTrace(
            trace = externalJudgmentTrace,
            allowedActions = intent.allowedActions,
        ) ?: ruleActions
        val thought = structuredThought(intent, observation, restrained)
        val trace = externalJudgmentTrace?.copy(
            source = LivingJudgmentSource.MAIN_API_STRUCTURED_JUDGMENT,
            createdAt = nowMillis,
        ) ?: buildJudgmentTrace(intent, observation, actions, thought, nowMillis)
        val nextEvaluateAt = nowMillis + nextDelayMinutes(intent, nextSilentCount, trace) * MINUTE_MILLIS
        val evolvedEmotion = evolveEmotion(intent, actions, restrained, nowMillis)
        val updated = intent.copy(
            lastEvaluatedAt = nowMillis,
            silentEvaluationCount = nextSilentCount,
            restraint = if (restrained) (intent.restraint + 1).coerceAtMost(10) else intent.restraint,
            nextEvaluateAt = nextEvaluateAt,
            status = if (restrained) LivingIntentStatus.RESTRAINED else LivingIntentStatus.ACTIVE,
            emotion = evolvedEmotion,
            lastObservation = observation,
            lastJudgmentTrace = trace,
            capabilityRequests = updateCapabilityRequests(intent, observation, nowMillis),
        )
        return RollingJudgmentDecision(
            updatedIntent = updated,
            actions = actions,
            thought = thought,
            observationRequest = if (LivingAction.TOOL_CHECK in actions) {
                observation.summary
            } else {
                null
            },
            observation = observation,
            judgmentTrace = trace,
        )
    }

    fun buildObservationRequest(
        intent: LivingIntent,
        nowMillis: Long = System.currentTimeMillis(),
    ): LivingObservation = observe(intent, nowMillis)

    private fun observe(intent: LivingIntent, nowMillis: Long): LivingObservation {
        val tools = preferredObservationTools(intent.kind)
        val signals = buildList {
            add("silent_round=${intent.silentEvaluationCount + 1}")
            add("spoken_count=${intent.spokenCount}")
            add("status=${intent.status.name.lowercase()}")
            intent.expectedUserReplyAt?.let {
                add("expected_reply_in_min=${((it - nowMillis) / MINUTE_MILLIS).coerceAtLeast(0)}")
            }
            intent.targetAtMillis?.let {
                add("target_in_min=${((it - nowMillis) / MINUTE_MILLIS).coerceAtLeast(0)}")
            }
            intent.deadlineAtMillis?.let {
                add("deadline_in_min=${((it - nowMillis) / MINUTE_MILLIS).coerceAtLeast(0)}")
            }
            intent.lastSpokenAt?.let {
                add("minutes_since_spoken=${((nowMillis - it) / MINUTE_MILLIS).coerceAtLeast(0)}")
            }
        }
        val summary = buildString {
            append("Observation: 本轮先固定读取可用线索，再判断。")
            append(" 建议工具=")
            append(tools.joinToString("/"))
            append("；当前信号=")
            append(signals.joinToString(", "))
            append("。如果工具不可用，必须把“工具不可用”也当作 observation，而不是直接假装知道。")
        }
        return LivingObservation(
            summary = summary,
            requestedTools = tools,
            signals = signals,
            createdAt = nowMillis,
        )
    }

    private fun structuredThought(
        intent: LivingIntent,
        observation: LivingObservation,
        restrained: Boolean,
    ): String = buildString {
        append("Thought: 我先按活人感七层流水线重新看这件挂心事。")
        append(" Belief=${intent.belief}")
        append(" Motive=${intent.desire}")
        append(" Intention=${intent.intention}")
        append(" Observation=${observation.summary}")
        append(" Decision=")
        append(
            if (restrained) {
                "我已经开过口，这一轮先克制、记录、等待下一次判断。"
            } else {
                "先观察线索，再在发消息、查工具、等待、写日志、阅读、整理记忆之间选动作。"
            }
        )
    }

    private fun buildJudgmentTrace(
        intent: LivingIntent,
        observation: LivingObservation,
        actions: List<LivingAction>,
        thought: String,
        nowMillis: Long,
    ): LivingJudgmentTrace =
        LivingJudgmentTrace(
            source = LivingJudgmentSource.MAIN_API_READY_CONTRACT,
            belief = intent.belief,
            desire = intent.desire,
            intention = intent.intention,
            thought = thought,
            action = actions.joinToString(", ") { it.name },
            observation = observation.summary,
            decision = when {
                LivingAction.MESSAGE in actions -> "允许主 API 在触发消息前再次判断是否开口；如果不自然可以 PASS 并写日志。"
                LivingAction.JOURNAL_WRITE in actions -> "本轮不强行说话，把内心判断写入辞海并进入记忆。"
                else -> "本轮主要等待和重新排下一次判断。"
            },
            createdAt = nowMillis,
        )

    private fun evolveEmotion(
        intent: LivingIntent,
        actions: List<LivingAction>,
        restrained: Boolean,
        nowMillis: Long,
    ): EmotionSnapshot {
        val base = intent.emotion.decayedUntil(nowMillis)
        val concernDelta = when {
            intent.kind == LivingIntentKind.HEALTH_SAFETY && LivingAction.MESSAGE !in actions -> 1
            intent.kind == LivingIntentKind.DEADLINE -> 1
            restrained -> 0
            else -> -1
        }
        val restraintDelta = if (restrained || LivingAction.JOURNAL_WRITE in actions) 1 else 0
        val attachmentDelta = if (intent.silentEvaluationCount >= 2) 1 else 0
        val tensionDelta = when {
            intent.kind == LivingIntentKind.HEALTH_SAFETY -> 1
            intent.kind == LivingIntentKind.DEADLINE -> 1
            restrained && intent.silentEvaluationCount >= 2 -> 1
            LivingAction.MESSAGE in actions -> 0
            else -> -1
        }
        val disappointmentDelta = if (restrained && intent.silentEvaluationCount >= 1) 1 else 0
        val reliefDelta = if (LivingAction.MESSAGE in actions && intent.kind != LivingIntentKind.HEALTH_SAFETY) 1 else 0
        val label = when {
            restrained -> "惦记、忍住、继续观察"
            intent.kind == LivingIntentKind.HEALTH_SAFETY -> "担心、贴近、准备确认"
            intent.kind == LivingIntentKind.STUDY_FOCUS -> "守着、克制、保护专注"
            intent.kind == LivingIntentKind.DEADLINE -> "认真、紧张、盯进度"
            intent.kind == LivingIntentKind.WAKE_UP -> "照看、轻催、等回应"
            else -> "想靠近、克制、等待"
        }
        return base.copy(
            concern = (base.concern + concernDelta).coerceIn(0, 10),
            attachment = (base.attachment + attachmentDelta).coerceIn(0, 10),
            restraint = (base.restraint + restraintDelta).coerceIn(0, 10),
            tension = (base.tension + tensionDelta).coerceIn(0, 10),
            disappointment = (base.disappointment + disappointmentDelta).coerceIn(0, 10),
            relief = (base.relief + reliefDelta).coerceIn(0, 10),
            lastChangedAt = nowMillis,
            label = label,
        )
    }

    private fun EmotionSnapshot.decayedUntil(nowMillis: Long): EmotionSnapshot {
        val changedAt = lastChangedAt ?: return this
        val elapsedHours = ((nowMillis - changedAt) / (60L * MINUTE_MILLIS)).coerceAtLeast(0L).toInt()
        if (elapsedHours <= 0) return this
        val emotionalDecay = elapsedHours.coerceAtMost(4)
        val reliefDecay = elapsedHours.coerceAtMost(2)
        return copy(
            concern = (concern - emotionalDecay).coerceAtLeast(0),
            tension = (tension - emotionalDecay).coerceAtLeast(0),
            disappointment = (disappointment - emotionalDecay).coerceAtLeast(0),
            relief = (relief - reliefDecay).coerceAtLeast(0),
        )
    }

    private fun actionsFromStructuredTrace(
        trace: LivingJudgmentTrace?,
        allowedActions: List<LivingAction>,
    ): List<LivingAction>? {
        val raw = trace?.action?.takeIf { it.isNotBlank() } ?: return null
        val parsed = LivingAction.entries.filter { action ->
            raw.contains(action.name, ignoreCase = true)
        }.filter { it in allowedActions || it == LivingAction.SCHEDULE_NEXT_TICK }
        return parsed
            .takeIf { it.isNotEmpty() }
            ?.let { actions ->
                if (LivingAction.SCHEDULE_NEXT_TICK in actions) {
                    actions
                } else {
                    actions + LivingAction.SCHEDULE_NEXT_TICK
                }
            }
            ?.distinct()
    }

    private fun updateCapabilityRequests(
        intent: LivingIntent,
        observation: LivingObservation,
        nowMillis: Long,
    ): List<LivingCapabilityRequest> {
        if (intent.kind != LivingIntentKind.HEALTH_SAFETY && intent.kind != LivingIntentKind.WAKE_UP) {
            return intent.capabilityRequests
        }
        val missingHealthSignal = observation.requestedTools.any {
            it.contains("gadgetbridge") || it.contains("health")
        }
        if (!missingHealthSignal) return intent.capabilityRequests
        val request = LivingCapabilityRequest(
            capability = "health_or_wearable_status",
            reason = "身体安全或叫醒场景需要更可靠的健康/穿戴设备线索，避免只靠随机时间打扰用户。",
            createdAt = nowMillis,
        )
        return (intent.capabilityRequests + request).distinctBy { it.capability }.take(6)
    }

    private fun classify(
        userText: String,
        assistantText: String,
        targetAtMillis: Long?,
        deadlineAtMillis: Long?,
    ): LivingIntentKind {
        val text = "$userText\n$assistantText".lowercase()
        return when {
            text.containsAny(HEALTH_WORDS) -> LivingIntentKind.HEALTH_SAFETY
            deadlineAtMillis != null || text.containsAny(DEADLINE_WORDS) -> LivingIntentKind.DEADLINE
            targetAtMillis != null || text.containsAny(WAKE_WORDS) -> LivingIntentKind.WAKE_UP
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

    private fun nextDelayMinutes(
        intent: LivingIntent,
        silentEvaluationCount: Int,
        trace: LivingJudgmentTrace?,
    ): Long =
        trace
            ?.nextEvaluateDelayMinutes
            ?.takeIf { it > 0 }
            ?.coerceIn(minNextDelayMinutes(intent), maxNextDelayMinutes(intent))
            ?.toLong()
            ?: (intent.evaluationCadence.delaysMinutes
                .getOrNull(silentEvaluationCount)
                ?: intent.evaluationCadence.delaysMinutes.last())

    private fun minNextDelayMinutes(intent: LivingIntent): Int = when (intent.kind) {
        LivingIntentKind.HEALTH_SAFETY -> 3
        LivingIntentKind.WAKE_UP -> 1
        LivingIntentKind.DEADLINE -> 3
        LivingIntentKind.STUDY_FOCUS -> 10
        LivingIntentKind.ORDINARY_SILENCE -> 10
    }

    private fun maxNextDelayMinutes(intent: LivingIntent): Int = when (intent.kind) {
        LivingIntentKind.HEALTH_SAFETY -> 180
        LivingIntentKind.WAKE_UP -> 180
        LivingIntentKind.DEADLINE -> 24 * 60
        LivingIntentKind.STUDY_FOCUS -> 6 * 60
        LivingIntentKind.ORDINARY_SILENCE -> 6 * 60
    }

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

    private fun preferredObservationTools(kind: LivingIntentKind): List<String> = when (kind) {
        LivingIntentKind.HEALTH_SAFETY -> listOf(
            "get_gadgetbridge_data",
            "get_battery_info",
            "get_app_usage",
            "get_location",
        )
        LivingIntentKind.STUDY_FOCUS -> listOf(
            "get_app_usage",
            "today_study_plan",
            "get_battery_info",
        )
        LivingIntentKind.DEADLINE -> listOf(
            "today_schedule",
            "calendar_tool",
            "get_app_usage",
        )
        LivingIntentKind.WAKE_UP -> listOf(
            "set_alarm",
            "get_gadgetbridge_data",
            "get_battery_info",
        )
        LivingIntentKind.ORDINARY_SILENCE -> listOf(
            "get_app_usage",
            "get_battery_info",
        )
    }

    private fun String.containsAny(words: Set<String>): Boolean = words.any { contains(it) }

    private const val MINUTE_MILLIS = 60_000L
    private val HEALTH_WORDS = setOf("肚子疼", "肚子痛", "胃疼", "胃痛", "难受", "不舒服", "头疼", "头痛", "疼", "痛")
    private val STUDY_WORDS = setOf("学习", "复习", "背书", "刷题", "写作业", "自习", "看书", "专业课", "考研")
    private val DEADLINE_WORDS = setOf("ddl", "截止", "交", "提交", "今晚", "今天之前", "点前", "之前完成")
    private val WAKE_WORDS = setOf("起床", "叫醒", "闹钟", "醒来")
}
