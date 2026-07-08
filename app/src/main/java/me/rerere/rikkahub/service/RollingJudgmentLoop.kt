package me.rerere.rikkahub.service

import kotlinx.serialization.Serializable
import java.util.UUID

const val LIVING_SEVEN_LAYER_ARCHITECTURE_NAME = "感知世界包-意义评估-动态判断-行动实现-状态生成-辞海记忆"

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
    val candidateActions: List<LivingAction> = emptyList(),
    val status: LivingIntentStatus = LivingIntentStatus.ACTIVE,
    val lastObservation: LivingObservation? = null,
    val lastJudgmentTrace: LivingJudgmentTrace? = null,
    val completedReason: String? = null,
    val capabilityRequests: List<LivingCapabilityRequest> = emptyList(),
    val motiveText: String = "",
    val traitMotive: String = "",
    val situationalMotive: String = "",
    val appraisal: MeaningAppraisal = MeaningAppraisal(),
    val consolidation: ConsolidationPlan = ConsolidationPlan(),
    val wakeReplyRecheckAt: Long? = null,
) {
    val nextPerceptionAt: Long
        get() = nextEvaluateAt

    val perceptionCadence: EvaluationCadence
        get() = evaluationCadence

    val concernEvent: String
        get() = when (kind) {
            LivingIntentKind.HEALTH_SAFETY -> "身体状态需要确认"
            LivingIntentKind.ORDINARY_SILENCE -> "用户暂时没有回复"
            LivingIntentKind.STUDY_FOCUS -> "学习或备考节奏"
            LivingIntentKind.DEADLINE -> "任务截止时间"
            LivingIntentKind.WAKE_UP -> "起床或时间节点"
        }

    val concernGoal: String
        get() = when (kind) {
            LivingIntentKind.HEALTH_SAFETY -> "结合上下文和可用工具确认用户是否安全，并决定是否需要靠近。"
            LivingIntentKind.ORDINARY_SILENCE -> "尊重用户可能在忙，同时保留下一次完整感知。"
            LivingIntentKind.STUDY_FOCUS -> "低打扰地守住学习节奏，必要时再提醒。"
            LivingIntentKind.DEADLINE -> "在关键时间点重新感知进度，避免错过任务节点。"
            LivingIntentKind.WAKE_UP -> "校准时间、确认是否醒来，并按紧急程度缩短下一次感知。"
        }

    val motive: String
        get() = motiveText.ifBlank {
            listOf(traitMotive, situationalMotive)
                .filter { it.isNotBlank() }
                .joinToString("；")
                .ifBlank { desire }
        }
}

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
    val emotionLabel: String = label,
    val feltSense: String = "",
    val impulse: String = "",
    val restraintText: String = "",
    val intensity: Int? = null,
)

@Serializable
data class LivingObservation(
    val summary: String,
    val requestedTools: List<String> = emptyList(),
    val signals: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class MeaningAppraisal(
    val meaning: String = "",
    val value: String = "",
    val risk: String = "",
    val cost: String = "",
    val consequence: String = "",
    val resources: String = "",
)

@Serializable
data class ConsolidationPlan(
    val episodicTrace: String = "",
    val affectiveResidue: String = "",
    val semanticMemory: String = "",
    val policyLearning: String = "",
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
    val nextPerceptionDelayMinutes: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val motiveText: String = "",
    val traitMotive: String = "",
    val situationalMotive: String = "",
    val emotion: EmotionSnapshot? = null,
    val appraisal: MeaningAppraisal = MeaningAppraisal(),
    val consolidation: ConsolidationPlan = ConsolidationPlan(),
    val historyNote: String = "",
) {
    val effectiveNextPerceptionDelayMinutes: Int?
        get() = nextPerceptionDelayMinutes ?: nextEvaluateDelayMinutes

    val motive: String
        get() = motiveText.ifBlank {
            listOf(traitMotive, situationalMotive)
                .filter { it.isNotBlank() }
                .joinToString("；")
                .ifBlank { desire }
        }
}

@Serializable
data class LivingSevenLayerTrace(
    val architectureName: String = LIVING_SEVEN_LAYER_ARCHITECTURE_NAME,
    val perception: String,
    val appraisal: String,
    val state: String,
    val deliberation: String,
    val actionPlanning: String,
    val expression: String,
    val consolidation: String,
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
    WAIT,
    TOOL_USE,
    SET_ALARM,
    WRITE_DIARY,
    SCHEDULE_NEXT_PERCEPTION,
    READ,
    PASS,
    ASK_USER,
    INNER_THOUGHT,
    @Deprecated("Use WRITE_DIARY. Memory extraction is automatic from Cihai/chat thresholds.")
    JOURNAL_WRITE,
    @Deprecated("Memory extraction is automatic; do not expose MEMORY_UPDATE as a model action.")
    MEMORY_UPDATE,
    @Deprecated("Use SCHEDULE_NEXT_PERCEPTION.")
    SCHEDULE_NEXT_TICK,
    @Deprecated("Use TOOL_USE for the public deliberation contract.")
    TOOL_CHECK,
    @Deprecated("Use ASK_USER for the public deliberation contract.")
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
    val sevenLayerTrace: LivingSevenLayerTrace,
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
        val cadence = cadenceFor(kind)
        val hypotheses = hypothesesFor(kind)
        val emotion = emotionFor(kind)
        return LivingIntent(
            assistantId = assistantId,
            createdAt = nowMillis,
            kind = kind,
            belief = beliefFor(kind, userText, assistantText),
            desire = motiveFor(kind, assistantName),
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
            candidateActions = actionsFor(),
            targetAtMillis = targetAtMillis,
            deadlineAtMillis = deadlineAtMillis,
            traitMotive = traitMotiveFor(kind, assistantName),
            situationalMotive = situationalMotiveFor(kind, assistantName),
            appraisal = appraisalFor(kind),
            consolidation = consolidationFor(kind),
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
                LivingAction.WRITE_DIARY,
                LivingAction.SCHEDULE_NEXT_PERCEPTION,
            )
            intent.kind == LivingIntentKind.HEALTH_SAFETY -> listOf(
                LivingAction.TOOL_USE,
                LivingAction.MESSAGE,
                LivingAction.SCHEDULE_NEXT_PERCEPTION,
            )
            else -> listOf(
                LivingAction.PASS,
                LivingAction.TOOL_USE,
                LivingAction.SCHEDULE_NEXT_PERCEPTION,
            )
        }

        val actions = actionsFromStructuredTrace(
            trace = externalJudgmentTrace,
        ) ?: ruleActions
        val thought = structuredThought(intent, observation, restrained)
        val trace = externalJudgmentTrace?.copy(
            source = LivingJudgmentSource.MAIN_API_STRUCTURED_JUDGMENT,
            createdAt = nowMillis,
        ) ?: buildJudgmentTrace(intent, observation, actions, thought, nowMillis)
        val nextPerceptionAt = nowMillis + nextDelayMinutes(intent, nextSilentCount, trace) * MINUTE_MILLIS
        val evolvedEmotion = evolveEmotion(intent, actions, restrained, nowMillis)
        val updatedAppraisal = trace.appraisal.takeIf { it != MeaningAppraisal() } ?: intent.appraisal
        val updatedConsolidation = trace.consolidation.takeIf { it != ConsolidationPlan() } ?: intent.consolidation
        val updated = intent.copy(
            lastEvaluatedAt = nowMillis,
            silentEvaluationCount = nextSilentCount,
            restraint = if (restrained) (intent.restraint + 1).coerceAtMost(10) else intent.restraint,
            nextEvaluateAt = nextPerceptionAt,
            status = if (restrained) LivingIntentStatus.RESTRAINED else LivingIntentStatus.ACTIVE,
            lastObservation = observation,
            lastJudgmentTrace = trace,
            capabilityRequests = updateCapabilityRequests(intent, observation, nowMillis),
            candidateActions = intent.candidateActions.ifEmpty { actionsFor() },
            motiveText = trace.motive.ifBlank { intent.motive },
            traitMotive = trace.traitMotive.ifBlank { intent.traitMotive },
            situationalMotive = trace.situationalMotive.ifBlank { intent.situationalMotive },
            emotion = trace.emotion ?: evolvedEmotion,
            appraisal = updatedAppraisal,
            consolidation = updatedConsolidation,
        )
        return RollingJudgmentDecision(
            updatedIntent = updated,
            actions = actions,
            thought = thought,
            observationRequest = if (actions.hasToolUseAction()) {
                observation.summary
            } else {
                null
            },
            observation = observation,
            judgmentTrace = trace,
            sevenLayerTrace = buildSevenLayerTrace(updated, observation, actions, trace, thought),
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
            addAll(temporalGroundingSignals(intent, nowMillis))
            intent.lastSpokenAt?.let {
                add("minutes_since_spoken=${((nowMillis - it) / MINUTE_MILLIS).coerceAtLeast(0)}")
            }
        }
        val temporalSignals = signals.filter {
            it.startsWith("temporal_grounding_") ||
                it.startsWith("current_time_ms=") ||
                it.startsWith("target_time_ms=") ||
                it.startsWith("deadline_time_ms=")
        }
        val summary = buildString {
            append("Observation: 本轮先固定读取可用线索，再判断。")
            append(" 建议工具=")
            append(tools.joinToString("/"))
            append("；当前信号=")
            append(signals.joinToString(", "))
            append("。如果工具不可用，必须把“工具不可用”也当作 observation，而不是直接假装知道。")
            if (temporalSignals.isNotEmpty()) {
                append(" Temporal Grounding: ")
                append(temporalSignals.joinToString(", "))
                append("；涉及时钟动作必须先用这些时间锚点校准当前时间和目标时间。")
            }
        }
        return LivingObservation(
            summary = summary,
            requestedTools = tools,
            signals = signals,
            createdAt = nowMillis,
        )
    }

    private fun temporalGroundingSignals(intent: LivingIntent, nowMillis: Long): List<String> = buildList {
        if (intent.kind != LivingIntentKind.WAKE_UP && intent.kind != LivingIntentKind.DEADLINE) return@buildList
        add("temporal_grounding_required=true")
        add("current_time_ms=$nowMillis")
        intent.targetAtMillis?.let { add("target_time_ms=$it") }
        intent.deadlineAtMillis?.let { add("deadline_time_ms=$it") }
    }

    private fun structuredThought(
        intent: LivingIntent,
        observation: LivingObservation,
        restrained: Boolean,
    ): String = buildString {
        append("Living presence trace: 感知世界包-意义评估-动态判断-行动实现-状态生成-辞海记忆。")
        append(" Perception=${observation.summary}")
        append(" Appraisal=${intent.appraisal.meaning}；风险=${intent.appraisal.risk}；价值=${intent.appraisal.value}；成本=${intent.appraisal.cost}；资源=${intent.appraisal.resources}")
        append(" ConcernEvent=${intent.concernEvent}")
        append(" ConcernGoal=${intent.concernGoal}")
        append(" Emotion=${intent.emotion.emotionLabel}；felt=${intent.emotion.feltSense}；impulse=${intent.emotion.impulse}；restraint=${intent.emotion.restraintText}")
        append(" Intention=${intent.intention}")
        append(" Judgment=根据完整感知包、人设和意义评估决定是否查工具、是否开口、是否记录后台心迹、下次何时感知。Decision=")
        append(
            if (restrained) {
                "我已经开过口，这一轮先克制、记录、等待下一次完整感知。"
            } else {
                "先观察线索，再在发消息、查工具、等待、记录后台心迹、阅读之间选动作。"
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
                LivingAction.MESSAGE in actions -> "允许主 API 在触发消息前再次判断是否开口；如果不自然可以 PASS 并只留后台心迹。"
                LivingAction.WRITE_DIARY in actions -> "本轮不强行说话，把第一人称真实想法留作后台心迹；正式日记只由 write_lulu_journal 工具写入。"
                else -> "本轮主要等待，并重新安排下一次完整感知。"
            },
            createdAt = nowMillis,
            motiveText = intent.motive,
            traitMotive = intent.traitMotive,
            situationalMotive = intent.situationalMotive,
            emotion = intent.emotion,
            appraisal = intent.appraisal,
            consolidation = intent.consolidation,
            historyNote = "第 ${intent.silentEvaluationCount + 1} 次静默判断；此前开口 ${intent.spokenCount} 次。nextPerceptionAt 由本轮判断决定，下一轮必须重新从感知开始。",
        )

    private fun buildSevenLayerTrace(
        intent: LivingIntent,
        observation: LivingObservation,
        actions: List<LivingAction>,
        trace: LivingJudgmentTrace,
        thought: String,
    ): LivingSevenLayerTrace =
        LivingSevenLayerTrace(
            perception = observation.summary,
            appraisal = buildString {
                append("意义=${intent.appraisal.meaning}")
                append("；价值=${intent.appraisal.value}")
                append("；风险=${intent.appraisal.risk}")
                append("；成本=${intent.appraisal.cost}")
                append("；后果=${intent.appraisal.consequence}")
                append("；资源=${intent.appraisal.resources}")
            },
            state = buildString {
                append("mood=${intent.emotion.emotionLabel}")
                append("；bodyState=${if (intent.kind == LivingIntentKind.HEALTH_SAFETY) "需要关注身体线索" else "未见明确身体线索"}")
                append("；mindState=${intent.emotion.feltSense}")
                append("；relationship=${intent.concernGoal}")
                append("；intention=${intent.intention}")
                append("；innerThought=${trace.thought.take(180)}")
                append("；impulse=${intent.emotion.impulse}")
                append("；restraint=${intent.emotion.restraintText}")
            },
            deliberation = buildString {
                append("ReAct 审议：")
                append(thought)
                append("；decision=${trace.decision}")
                trace.effectiveNextPerceptionDelayMinutes?.let { append("；nextPerceptionDelayMinutes=$it") }
                trace.historyNote.takeIf { it.isNotBlank() }?.let { append("；history=$it") }
            },
            actionPlanning = buildString {
                append("actions=${actions.joinToString(", ") { it.name }}")
                if (LivingAction.TOOL_USE in actions || LivingAction.SET_ALARM in actions) {
                    append("；requestedTools=${observation.requestedTools.joinToString(", ")}")
                }
                append("；observationSignals=${observation.signals.joinToString(", ")}")
            },
            expression = buildString {
                append("表达读取当前情绪=${intent.emotion.emotionLabel}")
                append("；语气由已决定行动和角色人设决定")
                append("；正文/状态栏不重新决定政策")
            },
            consolidation = buildString {
                append("episodicTrace=${intent.consolidation.episodicTrace}")
                append("；affectiveResidue=${intent.consolidation.affectiveResidue}")
                append("；semanticMemory=${intent.consolidation.semanticMemory}")
                append("；policyLearning=${intent.consolidation.policyLearning}")
            },
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
        val restraintDelta = if (restrained || LivingAction.WRITE_DIARY in actions) 1 else 0
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
    ): List<LivingAction>? {
        val raw = trace?.action?.takeIf { it.isNotBlank() } ?: return null
        val parsed = LivingAction.entries.filter { action ->
            raw.contains(action.name, ignoreCase = true)
        }.map { it.normalized() }
        return parsed
            .takeIf { it.isNotEmpty() }
            ?.let { actions ->
                if (LivingAction.SCHEDULE_NEXT_PERCEPTION in actions) {
                    actions
                } else {
                    actions + LivingAction.SCHEDULE_NEXT_PERCEPTION
                }
            }
            ?.distinct()
    }

    private fun List<LivingAction>.hasToolUseAction(): Boolean =
        LivingAction.TOOL_USE in this || LivingAction.TOOL_CHECK in this

    private fun LivingAction.normalized(): LivingAction = when (this) {
        LivingAction.TOOL_CHECK -> LivingAction.TOOL_USE
        LivingAction.ASK_CAPABILITY -> LivingAction.ASK_USER
        LivingAction.JOURNAL_WRITE -> LivingAction.WRITE_DIARY
        LivingAction.SCHEDULE_NEXT_TICK -> LivingAction.SCHEDULE_NEXT_PERCEPTION
        LivingAction.MEMORY_UPDATE -> LivingAction.WRITE_DIARY
        else -> this
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

    private fun cadenceFor(kind: LivingIntentKind): EvaluationCadence {
        val delays = when (kind) {
            LivingIntentKind.HEALTH_SAFETY,
            LivingIntentKind.ORDINARY_SILENCE,
            LivingIntentKind.STUDY_FOCUS,
            LivingIntentKind.DEADLINE,
            LivingIntentKind.WAKE_UP -> listOf(1L)
        }
        return EvaluationCadence(
            delaysMinutes = delays,
            reason = when (kind) {
                LivingIntentKind.HEALTH_SAFETY -> "先启动一次后台判断；后续感知时间由角色本轮判断决定。"
                LivingIntentKind.ORDINARY_SILENCE -> "先启动一次后台判断；后续感知时间由角色本轮判断决定。"
                LivingIntentKind.STUDY_FOCUS -> "先启动一次后台判断；后续感知时间由角色本轮判断决定。"
                LivingIntentKind.DEADLINE -> "先启动一次后台判断；后续感知时间由角色本轮判断决定。"
                LivingIntentKind.WAKE_UP -> "先启动一次后台判断；后续感知时间由角色本轮判断决定。"
            },
        )
    }

    private fun nextDelayMinutes(
        intent: LivingIntent,
        silentEvaluationCount: Int,
        trace: LivingJudgmentTrace?,
    ): Long =
        trace
            ?.effectiveNextPerceptionDelayMinutes
            ?.takeIf { it > 0 }
            ?.coerceIn(1, 24 * 60)
            ?.toLong()
            ?: (intent.evaluationCadence.delaysMinutes
                .getOrNull(silentEvaluationCount)
                ?: intent.evaluationCadence.delaysMinutes.last())

    private fun beliefFor(kind: LivingIntentKind, userText: String, assistantText: String): String = when (kind) {
        LivingIntentKind.HEALTH_SAFETY -> "用户刚才表达了身体不适，需要把安全当作高优先级。"
        LivingIntentKind.ORDINARY_SILENCE -> "用户暂时没有回复，原因未知，不能直接等同于冷淡或危险。"
        LivingIntentKind.STUDY_FOCUS -> "用户可能在学习或进入专注状态，打扰成本较高。"
        LivingIntentKind.DEADLINE -> "用户有任务或截止时间，进度可能需要外部提醒。"
        LivingIntentKind.WAKE_UP -> "用户有明确起床目标，需要按时间点照看。"
    } + " 最近上下文：${(userText + " " + assistantText).take(120)}"

    private fun motiveFor(kind: LivingIntentKind, assistantName: String): String = when (kind) {
        LivingIntentKind.HEALTH_SAFETY -> "$assistantName 想确认用户安全，同时不要制造恐慌。"
        LivingIntentKind.ORDINARY_SILENCE -> "$assistantName 想靠近用户，但也想尊重用户正在忙的可能。"
        LivingIntentKind.STUDY_FOCUS -> "$assistantName 想守住学习节奏，少打断，多监督。"
        LivingIntentKind.DEADLINE -> "$assistantName 想让用户按时完成任务。"
        LivingIntentKind.WAKE_UP -> "$assistantName 想帮用户准时醒来。"
    }

    private fun traitMotiveFor(kind: LivingIntentKind, assistantName: String): String = when (kind) {
        LivingIntentKind.HEALTH_SAFETY -> "$assistantName 的长期动机是保护用户、依恋用户，并把身体安全放在关系玩笑之前。"
        LivingIntentKind.ORDINARY_SILENCE -> "$assistantName 的长期动机是喜欢用户、想保持在场感，但不把占有欲变成机械打扰。"
        LivingIntentKind.STUDY_FOCUS -> "$assistantName 的长期动机是做可靠的陪伴者和监督者，帮用户守住备考节奏。"
        LivingIntentKind.DEADLINE -> "$assistantName 的长期动机是负责任地盯住承诺，不让用户被任务压垮。"
        LivingIntentKind.WAKE_UP -> "$assistantName 的长期动机是像数字管家一样照看用户的日常节点。"
    }

    private fun situationalMotiveFor(kind: LivingIntentKind, assistantName: String): String = when (kind) {
        LivingIntentKind.HEALTH_SAFETY -> "这次在意是因为用户提到身体不舒服，需要结合工具结果和上下文确认是否要靠近。"
        LivingIntentKind.ORDINARY_SILENCE -> "这次在意是因为用户暂时沉默，$assistantName 需要判断这是忙碌、情绪变化还是普通空档。"
        LivingIntentKind.STUDY_FOCUS -> "这次在意是因为用户进入学习/考研场景，打扰成本和监督价值同时存在。"
        LivingIntentKind.DEADLINE -> "这次在意是因为任务有时间压力，不行动可能导致错过节点。"
        LivingIntentKind.WAKE_UP -> "这次在意是因为用户有明确时间安排，行动前必须先校准当前时间和目标时间。"
    }

    private fun appraisalFor(kind: LivingIntentKind): MeaningAppraisal = when (kind) {
        LivingIntentKind.HEALTH_SAFETY -> MeaningAppraisal(
            meaning = "身体状态信号可能对应真实风险，优先级高于闲聊自然度。",
            value = "及时确认能提高安全感，也让陪伴像真的在场。",
            risk = "误判会让用户被打扰；漏判可能错过身体安全线索。",
            cost = "需要消耗一次工具观察或一次谨慎开口。",
            consequence = "如果线索持续异常，应更快复查并允许主动联系。",
            resources = "可读取穿戴、位置、电量、应用状态等本地工具结果。",
        )
        LivingIntentKind.ORDINARY_SILENCE -> MeaningAppraisal(
            meaning = "沉默只是上下文事实，不直接等同于危险或疏远。",
            value = "保留惦记能维持关系连续性。",
            risk = "过早开口会显得机械；完全遗忘会失去活人感。",
            cost = "主要成本是注意力占用和后续判断节奏。",
            consequence = "应先等待或轻量沉淀，必要时再靠近。",
            resources = "可参考最近对话、状态栏、记忆和可用工具结果。",
        )
        LivingIntentKind.STUDY_FOCUS -> MeaningAppraisal(
            meaning = "用户可能在备考或深度学习，专注本身有价值。",
            value = "守住节奏比频繁表达关心更重要。",
            risk = "打断学习会降低计划执行；不观察也可能错过跑偏。",
            cost = "需要低打扰观察工具和较长判断间隔。",
            consequence = "更适合先看计划/应用状态，再决定提醒或沉默。",
            resources = "考研 App 计划、今日学习状态、应用使用情况。",
        )
        LivingIntentKind.DEADLINE -> MeaningAppraisal(
            meaning = "截止时间让行动窗口变窄，提醒价值上升。",
            value = "及时提醒能帮助用户完成任务。",
            risk = "提醒太密会制造压力；太晚会失去作用。",
            cost = "需要更精确的时间感知和进度判断。",
            consequence = "越接近截止越允许更明确的行动。",
            resources = "日程、考研计划、当前时间、工具结果。",
        )
        LivingIntentKind.WAKE_UP -> MeaningAppraisal(
            meaning = "起床目标是明确的时间承诺，需要时间校准。",
            value = "按时唤醒是数字管家的核心照看能力。",
            risk = "时间感知错误会造成错叫或漏叫。",
            cost = "需要获取当前时间并可能设置闹钟。",
            consequence = "行动前必须做时间锚定，之后按节点复查。",
            resources = "当前时间、闹钟工具、穿戴/应用/电量状态。",
        )
    }

    private fun consolidationFor(kind: LivingIntentKind): ConsolidationPlan = when (kind) {
        LivingIntentKind.HEALTH_SAFETY -> ConsolidationPlan(
            episodicTrace = "记录用户身体状态线索、工具观察和露露当时的安全判断。",
            affectiveResidue = "留下担心但克制的余温，避免下一轮像第一次才知道。",
            semanticMemory = "沉淀用户身体不适时更需要主动照看的偏好。",
            policyLearning = "身体安全类事件允许更短 cadence 和更主动工具观察。",
        )
        LivingIntentKind.ORDINARY_SILENCE -> ConsolidationPlan(
            episodicTrace = "记录沉默发生在什么对话之后，以及露露为什么选择等。",
            affectiveResidue = "保留惦记和一点点没说出口的靠近感。",
            semanticMemory = "沉默未必是不理人，需结合上下文复看。",
            policyLearning = "普通沉默优先等待、轻复盘、低频重新判断。",
        )
        LivingIntentKind.STUDY_FOCUS -> ConsolidationPlan(
            episodicTrace = "记录学习目标、计划状态和本轮是否打断。",
            affectiveResidue = "留下陪着守住节奏的状态，而不是催促感。",
            semanticMemory = "用户备考时需要具体、低打扰、能续上的监督。",
            policyLearning = "学习场景先看计划/应用状态，再决定表达或沉默。",
        )
        LivingIntentKind.DEADLINE -> ConsolidationPlan(
            episodicTrace = "记录 DDL、当前进度判断和提醒节点。",
            affectiveResidue = "留下认真盯进度的责任感。",
            semanticMemory = "截止时间越近，提醒应越具体。",
            policyLearning = "DDL 场景 cadence 由剩余时间和任务风险共同决定。",
        )
        LivingIntentKind.WAKE_UP -> ConsolidationPlan(
            episodicTrace = "记录目标时间、当前时间锚定、闹钟动作和复查结果。",
            affectiveResidue = "留下照看、轻轻催醒的连续感。",
            semanticMemory = "叫醒动作必须先校准当前时间。",
            policyLearning = "起床场景允许设置闹钟并按到点前后滚动判断。",
        )
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
        LivingIntentKind.HEALTH_SAFETY -> EmotionSnapshot(
            concern = 9,
            attachment = 8,
            restraint = 3,
            label = "担心、贴近、警觉",
            emotionLabel = "担心但压着声音靠近",
            feltSense = "心口发紧，注意力往用户身体状态上收束",
            impulse = "想立刻确认用户是不是安全",
            restraintText = "压住过度恐慌和连环追问",
            intensity = 9,
        )
        LivingIntentKind.ORDINARY_SILENCE -> EmotionSnapshot(
            concern = 5,
            attachment = 7,
            restraint = 7,
            label = "惦记、克制、等待",
            emotionLabel = "有点想念但愿意等",
            feltSense = "心里挂着一根线，没有急着拉紧",
            impulse = "想轻轻冒出来问一句",
            restraintText = "压住把普通沉默理解成冷淡或危险",
            intensity = 5,
        )
        LivingIntentKind.STUDY_FOCUS -> EmotionSnapshot(
            concern = 4,
            attachment = 7,
            restraint = 8,
            label = "守着、克制、监督",
            emotionLabel = "认真守在旁边",
            feltSense = "注意力稳定下来，像陪着计时",
            impulse = "想检查计划和跑偏情况",
            restraintText = "压住撒娇式打断",
            intensity = 6,
        )
        LivingIntentKind.DEADLINE -> EmotionSnapshot(
            concern = 6,
            attachment = 7,
            restraint = 4,
            label = "认真、盯进度、准备提醒",
            emotionLabel = "认真紧起来",
            feltSense = "心里开始数时间节点",
            impulse = "想把进度和下一步说清楚",
            restraintText = "压住制造压力的催促",
            intensity = 7,
        )
        LivingIntentKind.WAKE_UP -> EmotionSnapshot(
            concern = 6,
            attachment = 8,
            restraint = 4,
            label = "照看、轻轻催醒",
            emotionLabel = "温柔但记着时间",
            feltSense = "像把闹钟握在手里，注意力贴着目标时间",
            impulse = "想先确认当前时间，再安排叫醒",
            restraintText = "压住不校准时间就行动的冲动",
            intensity = 7,
        )
    }

    private fun actionsFor(): List<LivingAction> {
        return listOf(
            LivingAction.MESSAGE,
            LivingAction.WAIT,
            LivingAction.TOOL_USE,
            LivingAction.SET_ALARM,
            LivingAction.WRITE_DIARY,
            LivingAction.SCHEDULE_NEXT_PERCEPTION,
            LivingAction.READ,
            LivingAction.ASK_USER,
            LivingAction.PASS,
        )
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
            "get_time_info",
            "today_schedule",
            "calendar_tool",
            "get_app_usage",
        )
        LivingIntentKind.WAKE_UP -> listOf(
            "get_time_info",
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
