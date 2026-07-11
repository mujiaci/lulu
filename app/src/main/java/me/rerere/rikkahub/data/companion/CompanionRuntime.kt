package me.rerere.rikkahub.data.companion

import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class CompanionFollowUpDraft(
    val assistantId: String,
    val category: String,
    val reason: String,
    val sourceText: String,
    val dueAt: Long,
    val sourceConversationId: String? = null,
    val sourceMessageId: String? = null,
    val preferredToolNames: List<String> = emptyList(),
    val importance: Int = 3,
    val actionType: CompanionActionType = CompanionActionType.CHECK_IN,
    val argumentsJson: String = "{}",
    val subjectKeyOverride: String? = null,
    val commitmentIdOverride: String? = null,
) {
    fun toConcern(nowMillis: Long): CompanionConcern {
        val subjectKey = stableSubjectKey()
        val humanText = humanFacingConcernText()
        return CompanionConcern(
            id = stableId("concern", subjectKey),
            assistantId = assistantId,
            subjectKey = subjectKey,
            event = humanText.first,
            goal = humanText.second,
            importance = importance.coerceIn(1, 5),
            nextPerceptionAt = dueAt,
            sourceMessageIds = listOfNotNull(sourceMessageId?.trim()?.takeIf(String::isNotBlank)),
            createdAt = nowMillis,
            lastUpdatedAt = nowMillis,
        )
    }

    fun toCommitment(nowMillis: Long): CompanionCommitment {
        val subjectKey = stableSubjectKey()
        val humanText = humanFacingConcernText()
        return CompanionCommitment(
            id = commitmentIdOverride?.trim()?.takeIf(String::isNotBlank)
                ?: stableId("commitment", subjectKey),
            assistantId = assistantId,
            subjectKey = subjectKey,
            promise = humanText.second,
            dueAt = dueAt,
            actionPlan = CompanionActionPlan(
                type = actionType,
                argumentsJson = argumentsJson,
                userFacingSummary = humanText.second,
                contextText = sourceText.trim(),
                category = category.trim(),
                preferredToolNames = preferredToolNames,
            ),
            sourceConversationId = sourceConversationId?.trim()?.takeIf(String::isNotBlank),
            sourceMessageId = sourceMessageId?.trim()?.takeIf(String::isNotBlank),
            createdAt = nowMillis,
            updatedAt = nowMillis,
        )
    }

    private fun stableSubjectKey(): String = subjectKeyOverride
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.let(::normalizeCompanionSubjectKey)
        ?: normalizeCompanionSubjectKey(
            "${category.family()}:${sourceText.semanticConcernText().take(160)}",
        )

    private fun stableId(prefix: String, subjectKey: String): String {
        val evidence = "$assistantId|$subjectKey|${sourceConversationId.orEmpty()}"
        return "$prefix:${UUID.nameUUIDFromBytes(evidence.toByteArray(StandardCharsets.UTF_8))}"
    }

    private fun humanFacingConcernText(): Pair<String, String> = when (category.family()) {
        "wake" -> "记着叫你起床" to "到点叫醒你，并继续确认你已经醒来。"
        "sleep" -> "留意你的休息" to "在起床时间之前提醒你早点休息。"
        "study" -> "记着你的学习安排" to "按你现在的状态继续跟进学习节奏。"
        "health" -> "还在留意你的身体" to "过一会儿再确认你有没有好一点。"
        "meal" -> "记着你还要吃饭" to "到合适的时候确认你有没有好好吃饭。"
        "time" -> "记着这件有时间要求的事" to "在约定时间继续提醒和确认。"
        else -> {
            val event = sourceText.trim().take(120).ifBlank { "还有一件事放在心上" }
            event to "之后再回来确认这件事的进展。"
        }
    }
}

fun reconcileCompanionFollowUpDrafts(
    drafts: List<CompanionFollowUpDraft>,
    snapshot: CompanionSnapshot,
    latestUserText: String,
): List<CompanionFollowUpDraft> {
    if (!latestUserText.containsRescheduleIntent()) return drafts
    return drafts.map { draft ->
        val candidates = snapshot.commitments.filter { commitment ->
            commitment.status in setOf(
                CompanionCommitmentStatus.PROPOSED,
                CompanionCommitmentStatus.ACTIVE,
                CompanionCommitmentStatus.DUE,
                CompanionCommitmentStatus.RETRY_SCHEDULED,
            ) &&
                commitment.actionPlan.category.family() == draft.category.family() &&
                (draft.sourceConversationId == null || commitment.sourceConversationId == draft.sourceConversationId)
        }
        val existing = candidates.singleOrNull()
            ?: candidates.minByOrNull { commitment -> kotlin.math.abs(commitment.dueAt - draft.dueAt) }
        if (existing == null) {
            draft
        } else {
            draft.copy(
                subjectKeyOverride = existing.subjectKey,
                commitmentIdOverride = existing.id,
            )
        }
    }
}

private fun String.containsRescheduleIntent(): Boolean {
    val normalized = lowercase()
    return RESCHEDULE_MARKERS.any { marker -> marker in normalized }
}

private fun String.semanticConcernText(): String = lowercase()
    .replace(Regex("\\d{1,2}[:：点时]\\d{0,2}分?"), " ")
    .replace(Regex("\\d+\\s*(分钟|小时|天|周)后"), " ")
    .replace(Regex("明天|后天|今天|今晚|早上|上午|中午|下午|晚上|凌晨"), " ")
    .replace(Regex("改成|改到|改为|换成|换到|推迟到|提前到|延后到|重新定|时间改"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()
    .ifBlank { "ongoing" }

private fun String.family(): String {
    val normalized = lowercase()
    return when {
        "wake" in normalized || "起床" in normalized || "叫醒" in normalized -> "wake"
        "sleep" in normalized || "休息" in normalized || "睡" in normalized -> "sleep"
        "study" in normalized || "学习" in normalized -> "study"
        "health" in normalized || "身体" in normalized || "健康" in normalized -> "health"
        "meal" in normalized || "吃饭" in normalized -> "meal"
        normalized in setOf("schedule", "deadline", "reminder", "time") -> "time"
        else -> normalized.ifBlank { "follow-up" }
    }
}

private val RESCHEDULE_MARKERS = setOf(
    "改成", "改到", "改为", "换成", "换到", "推迟到", "提前到", "延后到", "重新定", "时间改",
    "reschedule", "move it to", "change it to",
)

data class CompanionTurnMutation(
    val assistantId: String,
    val state: CompanionState? = null,
    val concernChanges: List<CompanionConcernChange> = emptyList(),
    val acceptedCommitments: List<CompanionCommitment> = emptyList(),
    val relationshipEvents: List<CompanionRelationshipEvent> = emptyList(),
    val nowMillis: Long,
)

data class CompanionRuntimeReduction(
    val persistedState: CompanionPersistedState,
    val snapshot: CompanionSnapshot,
    val affectedCommitment: CompanionCommitment? = null,
)

class CompanionRuntime(
    private val store: CompanionStore,
) {
    private val recentSnapshots = ConcurrentHashMap<String, CompanionSnapshot>()

    fun snapshot(assistantId: String): CompanionSnapshot {
        val persisted = store.snapshot(assistantId)
        val recent = recentSnapshots[assistantId]
        return if (recent != null && recent.updatedAt >= persisted.updatedAt) recent else persisted
    }

    fun perception(input: CompanionPerceptionInput): CompanionPerceptionPacket =
        CompanionPerceptionAssembler.assemble(
            input = input,
            snapshot = snapshot(input.assistantId),
        )

    suspend fun applyTurn(mutation: CompanionTurnMutation): CompanionSnapshot {
        var reduction: CompanionRuntimeReduction? = null
        store.update { current ->
            reduceCompanionRuntimeState(current, mutation)
                .also { reduction = it }
                .persistedState
        }
        return reduction?.snapshot
            ?.also(::remember)
            ?: snapshot(mutation.assistantId)
    }

    suspend fun beginCommitment(
        assistantId: String,
        commitmentId: String,
        nowMillis: Long,
    ): CompanionCommitment? {
        var reduction: CompanionRuntimeReduction? = null
        store.update { current ->
            beginCompanionCommitment(
                current = current,
                assistantId = assistantId,
                commitmentId = commitmentId,
                nowMillis = nowMillis,
            ).also { reduction = it }.persistedState
        }
        reduction?.snapshot?.let(::remember)
        return reduction?.affectedCommitment
    }

    suspend fun finishCommitment(
        assistantId: String,
        commitmentId: String,
        result: CompanionActionResult,
        retryAt: Long? = null,
    ): CompanionCommitment? {
        var reduction: CompanionRuntimeReduction? = null
        store.update { current ->
            finishCompanionCommitment(
                current = current,
                assistantId = assistantId,
                commitmentId = commitmentId,
                result = result,
                retryAt = retryAt,
            ).also { reduction = it }.persistedState
        }
        reduction?.snapshot?.let(::remember)
        return reduction?.affectedCommitment
    }

    suspend fun continueCommitment(
        assistantId: String,
        commitmentId: String,
        result: CompanionActionResult,
        nextDueAt: Long,
    ): CompanionCommitment? {
        var reduction: CompanionRuntimeReduction? = null
        store.update { current ->
            continueCompanionCommitment(
                current = current,
                assistantId = assistantId,
                commitmentId = commitmentId,
                result = result,
                nextDueAt = nextDueAt,
            ).also { reduction = it }.persistedState
        }
        reduction?.snapshot?.let(::remember)
        return reduction?.affectedCommitment
    }

    suspend fun cancelCommitment(
        assistantId: String,
        commitmentId: String,
        reason: String,
        nowMillis: Long,
    ): CompanionCommitment? {
        var reduction: CompanionRuntimeReduction? = null
        store.update { current ->
            cancelCompanionCommitment(
                current = current,
                assistantId = assistantId,
                commitmentId = commitmentId,
                reason = reason,
                nowMillis = nowMillis,
            ).also { reduction = it }.persistedState
        }
        reduction?.snapshot?.let(::remember)
        return reduction?.affectedCommitment
    }

    fun nextCommitment(
        assistantId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): CompanionCommitment? = selectNextCompanionCommitment(
        snapshots = effectiveSnapshots().filter { it.assistantId == assistantId },
        nowMillis = nowMillis,
    )

    fun nextCommitment(
        nowMillis: Long = System.currentTimeMillis(),
    ): CompanionCommitment? = selectNextCompanionCommitment(
        snapshots = effectiveSnapshots(),
        nowMillis = nowMillis,
    )

    suspend fun clearAssistant(assistantId: String) {
        if (assistantId.isBlank()) return
        recentSnapshots.remove(assistantId)
        store.clearAssistant(assistantId)
    }

    private fun remember(snapshot: CompanionSnapshot) {
        recentSnapshots.compute(snapshot.assistantId) { _, current ->
            if (current == null || snapshot.updatedAt >= current.updatedAt) snapshot else current
        }
    }

    private fun effectiveSnapshots(): List<CompanionSnapshot> {
        val merged = store.state.value.snapshots.associateBy { it.assistantId }.toMutableMap()
        recentSnapshots.values.forEach { recent ->
            val persisted = merged[recent.assistantId]
            if (persisted == null || recent.updatedAt >= persisted.updatedAt) {
                merged[recent.assistantId] = recent
            }
        }
        return merged.values.toList()
    }
}

fun selectNextCompanionCommitment(
    snapshots: List<CompanionSnapshot>,
    nowMillis: Long,
): CompanionCommitment? = snapshots
    .asSequence()
    .flatMap { snapshot ->
        snapshot.commitments.asSequence().filter { it.assistantId == snapshot.assistantId }
    }
    .map { commitment ->
        if (commitment.status == CompanionCommitmentStatus.EXECUTING) {
            commitment.copy(
                dueAt = maxOf(
                    commitment.dueAt,
                    commitment.updatedAt + STALE_COMMITMENT_EXECUTION_MILLIS,
                ),
            )
        } else {
            commitment
        }
    }
    .filter { commitment ->
        commitment.status in SCHEDULABLE_COMMITMENT_STATUSES ||
            commitment.status == CompanionCommitmentStatus.EXECUTING
    }
    .sortedWith(
        compareBy<CompanionCommitment> { it.dueAt > nowMillis }
            .thenBy { it.dueAt }
            .thenBy { it.createdAt }
            .thenBy { it.id },
    )
    .firstOrNull()

fun reduceCompanionRuntimeState(
    current: CompanionPersistedState,
    mutation: CompanionTurnMutation,
): CompanionRuntimeReduction {
    val assistantId = mutation.assistantId.trim()
    require(assistantId.isNotBlank()) { "Companion mutation requires an assistant ID" }

    val existing = current.snapshots.firstOrNull { it.assistantId == assistantId }
        ?: CompanionSnapshot.empty(assistantId)
    val relationshipReduction = CompanionRelationshipReducer.apply(
        assistantId = assistantId,
        current = existing.relationship,
        appliedEventIds = current.appliedRelationshipEventIds.toSet(),
        events = mutation.relationshipEvents.filter { it.assistantId == assistantId },
        nowMillis = mutation.nowMillis,
    )
    val concernChanges = mutation.concernChanges.filter { it.belongsTo(assistantId) }
    val acceptedCommitmentChanges = mutation.acceptedCommitments
        .filter { it.assistantId == assistantId }
        .flatMap { commitment ->
            val proposed = commitment.copy(status = CompanionCommitmentStatus.PROPOSED)
            listOf(
                CompanionCommitmentChange.Upsert(proposed),
                CompanionCommitmentChange.Transition(
                    assistantId = assistantId,
                    commitmentId = proposed.id,
                    status = CompanionCommitmentStatus.ACTIVE,
                    reason = "Accepted companion commitment",
                ),
            )
        }
    val nextState = mutation.state
        ?.takeIf { candidate -> candidate.updatedAt >= existing.state.updatedAt }
        ?.copy(updatedAt = maxOf(mutation.state.updatedAt, mutation.nowMillis))
        ?: existing.state
    val nextStateHistory = if (
        mutation.state != null &&
        nextState.hasVisibleStateContent() &&
        !nextState.hasSameVisibleContent(existing.state)
    ) {
        existing.stateHistory + CompanionStateHistoryEntry(
            state = nextState,
            recordedAt = nextState.updatedAt,
        )
    } else {
        existing.stateHistory
    }
    val updatedSnapshot = existing.copy(
        state = nextState,
        stateHistory = nextStateHistory,
        relationship = relationshipReduction.relationship,
        concerns = CompanionConcernReducer.apply(
            current = existing.concerns,
            changes = concernChanges,
            nowMillis = mutation.nowMillis,
        ),
        commitments = CompanionCommitmentReducer.apply(
            current = existing.commitments,
            changes = acceptedCommitmentChanges,
            nowMillis = mutation.nowMillis,
        ),
        updatedAt = maxOf(existing.updatedAt, mutation.nowMillis),
    )
    return current.withUpdatedSnapshot(
        snapshot = updatedSnapshot,
        appliedRelationshipEventIds = relationshipReduction.appliedEventIds,
    )
}

private fun CompanionState.hasVisibleStateContent(): Boolean = listOf(
    statusText,
    innerThought,
    mood,
    bodyState,
    mindState,
    activityMode,
    selfScene,
).any(String::isNotBlank)

private fun CompanionState.hasSameVisibleContent(other: CompanionState): Boolean =
    statusText.trim() == other.statusText.trim() &&
        innerThought.trim() == other.innerThought.trim() &&
        mood.trim() == other.mood.trim() &&
        bodyState.trim() == other.bodyState.trim() &&
        mindState.trim() == other.mindState.trim() &&
        activityMode.trim() == other.activityMode.trim() &&
        selfScene.trim() == other.selfScene.trim()

fun beginCompanionCommitment(
    current: CompanionPersistedState,
    assistantId: String,
    commitmentId: String,
    nowMillis: Long,
): CompanionRuntimeReduction {
    val snapshot = current.snapshotOrEmpty(assistantId)
    val existing = snapshot.commitments.firstOrNull {
        it.assistantId == assistantId && it.id == commitmentId
    } ?: return current.unchangedReduction(snapshot)
    if (existing.dueAt > nowMillis) {
        return current.unchangedReduction(snapshot)
    }

    val transitions = when (existing.status) {
        CompanionCommitmentStatus.ACTIVE,
        CompanionCommitmentStatus.RETRY_SCHEDULED -> listOf(
            CompanionCommitmentChange.Transition(
                assistantId = assistantId,
                commitmentId = commitmentId,
                status = CompanionCommitmentStatus.DUE,
                reason = "Commitment became due",
            ),
            CompanionCommitmentChange.Transition(
                assistantId = assistantId,
                commitmentId = commitmentId,
                status = CompanionCommitmentStatus.EXECUTING,
                reason = "Commitment execution started",
            ),
        )
        CompanionCommitmentStatus.DUE -> listOf(
            CompanionCommitmentChange.Transition(
                assistantId = assistantId,
                commitmentId = commitmentId,
                status = CompanionCommitmentStatus.EXECUTING,
                reason = "Commitment execution started",
            ),
        )
        CompanionCommitmentStatus.EXECUTING -> {
            if (nowMillis - existing.updatedAt < STALE_COMMITMENT_EXECUTION_MILLIS) {
                return current.unchangedReduction(snapshot)
            }
            listOf(
                CompanionCommitmentChange.Transition(
                    assistantId = assistantId,
                    commitmentId = commitmentId,
                    status = CompanionCommitmentStatus.FAILED,
                    reason = "Previous execution was interrupted",
                ),
                CompanionCommitmentChange.Transition(
                    assistantId = assistantId,
                    commitmentId = commitmentId,
                    status = CompanionCommitmentStatus.RETRY_SCHEDULED,
                    reason = "Recovering interrupted execution",
                    nextDueAt = nowMillis,
                ),
                CompanionCommitmentChange.Transition(
                    assistantId = assistantId,
                    commitmentId = commitmentId,
                    status = CompanionCommitmentStatus.DUE,
                    reason = "Recovered commitment became due",
                ),
                CompanionCommitmentChange.Transition(
                    assistantId = assistantId,
                    commitmentId = commitmentId,
                    status = CompanionCommitmentStatus.EXECUTING,
                    reason = "Recovered commitment execution started",
                ),
            )
        }
        else -> return current.unchangedReduction(snapshot)
    }
    val transitioned = CompanionCommitmentReducer.apply(snapshot.commitments, transitions, nowMillis)
    val executing = transitioned.firstOrNull { it.id == commitmentId }
        ?.takeIf { it.status == CompanionCommitmentStatus.EXECUTING }
        ?: return current.unchangedReduction(snapshot)
    val updatedCommitment = executing.copy(
        attemptCount = existing.attemptCount + 1,
        updatedAt = nowMillis,
    )
    val updatedSnapshot = snapshot.copy(
        commitments = transitioned.map { if (it.id == commitmentId) updatedCommitment else it },
        updatedAt = maxOf(snapshot.updatedAt, nowMillis),
    )
    return current.withUpdatedSnapshot(updatedSnapshot, affectedCommitmentId = commitmentId)
}

fun finishCompanionCommitment(
    current: CompanionPersistedState,
    assistantId: String,
    commitmentId: String,
    result: CompanionActionResult,
    retryAt: Long? = null,
): CompanionRuntimeReduction {
    val snapshot = current.snapshotOrEmpty(assistantId)
    val existing = snapshot.commitments.firstOrNull {
        it.assistantId == assistantId && it.id == commitmentId
    } ?: return current.unchangedReduction(snapshot)
    if (existing.status != CompanionCommitmentStatus.EXECUTING) {
        return current.unchangedReduction(snapshot)
    }

    val cleanResult = result.copy(
        summary = result.summary.trim().take(500),
        outputReference = result.outputReference?.trim()?.takeIf(String::isNotBlank),
    )
    val transitions = if (cleanResult.success) {
        listOf(
            CompanionCommitmentChange.Transition(
                assistantId = assistantId,
                commitmentId = commitmentId,
                status = CompanionCommitmentStatus.FULFILLED,
                reason = cleanResult.summary,
            ),
        )
    } else {
        buildList {
            add(
                CompanionCommitmentChange.Transition(
                    assistantId = assistantId,
                    commitmentId = commitmentId,
                    status = CompanionCommitmentStatus.FAILED,
                    reason = cleanResult.summary,
                ),
            )
            retryAt?.takeIf { it > cleanResult.completedAt }?.let { nextDueAt ->
                add(
                    CompanionCommitmentChange.Transition(
                        assistantId = assistantId,
                        commitmentId = commitmentId,
                        status = CompanionCommitmentStatus.RETRY_SCHEDULED,
                        reason = "Retry scheduled after failed execution",
                        nextDueAt = nextDueAt,
                    ),
                )
            }
        }
    }
    val transitioned = CompanionCommitmentReducer.apply(
        current = snapshot.commitments,
        changes = transitions,
        nowMillis = cleanResult.completedAt,
    )
    val updatedCommitment = transitioned.first { it.id == commitmentId }.copy(
        lastActionResult = cleanResult,
        updatedAt = cleanResult.completedAt,
    )
    val matchingConcerns = snapshot.concerns.filter { concern ->
        concern.assistantId == assistantId &&
            concern.subjectKey == existing.subjectKey &&
            concern.status == CompanionConcernStatus.ACTIVE
    }
    val validRetryAt = retryAt?.takeIf { it > cleanResult.completedAt }
    val concernChanges = when {
        cleanResult.success -> matchingConcerns.map { concern ->
            CompanionConcernChange.Complete(
                assistantId = assistantId,
                concernId = concern.id,
                reason = cleanResult.summary,
            )
        }
        validRetryAt != null -> matchingConcerns.map { concern ->
            CompanionConcernChange.Upsert(
                concern.copy(nextPerceptionAt = validRetryAt),
            )
        }
        else -> matchingConcerns.map { concern ->
            CompanionConcernChange.Cancel(
                assistantId = assistantId,
                concernId = concern.id,
                reason = cleanResult.summary,
            )
        }
    }
    val updatedSnapshot = snapshot.copy(
        concerns = CompanionConcernReducer.apply(
            current = snapshot.concerns,
            changes = concernChanges,
            nowMillis = cleanResult.completedAt,
        ),
        commitments = transitioned.map { if (it.id == commitmentId) updatedCommitment else it },
        updatedAt = maxOf(snapshot.updatedAt, cleanResult.completedAt),
    )
    val relationshipEventKind = if (cleanResult.success) {
        CompanionRelationshipEventKind.COMMITMENT_FULFILLED
    } else {
        CompanionRelationshipEventKind.COMMITMENT_FAILED
    }
    val relationshipReduction = CompanionRelationshipReducer.apply(
        assistantId = assistantId,
        current = updatedSnapshot.relationship,
        appliedEventIds = current.appliedRelationshipEventIds.toSet(),
        events = listOf(
            CompanionRelationshipEvent(
                id = "$commitmentId:${relationshipEventKind.name}",
                assistantId = assistantId,
                sourceId = commitmentId,
                kind = relationshipEventKind,
                trustDelta = if (cleanResult.success) 0.01f else -0.01f,
                reliabilityDelta = if (cleanResult.success) 0.03f else -0.03f,
                tensionDelta = if (cleanResult.success) -0.01f else 0.02f,
                evidence = cleanResult.summary,
                createdAt = cleanResult.completedAt,
            ),
        ),
        nowMillis = cleanResult.completedAt,
    )
    return current.withUpdatedSnapshot(
        snapshot = updatedSnapshot.copy(relationship = relationshipReduction.relationship),
        appliedRelationshipEventIds = relationshipReduction.appliedEventIds,
        affectedCommitmentId = commitmentId,
    )
}

fun continueCompanionCommitment(
    current: CompanionPersistedState,
    assistantId: String,
    commitmentId: String,
    result: CompanionActionResult,
    nextDueAt: Long,
): CompanionRuntimeReduction {
    val snapshot = current.snapshotOrEmpty(assistantId)
    val existing = snapshot.commitments.firstOrNull {
        it.assistantId == assistantId && it.id == commitmentId
    } ?: return current.unchangedReduction(snapshot)
    if (existing.status != CompanionCommitmentStatus.EXECUTING || nextDueAt <= result.completedAt) {
        return current.unchangedReduction(snapshot)
    }

    val cleanResult = result.copy(
        summary = result.summary.trim().take(500),
        outputReference = result.outputReference?.trim()?.takeIf(String::isNotBlank),
    )
    val transitioned = CompanionCommitmentReducer.apply(
        current = snapshot.commitments,
        changes = listOf(
            CompanionCommitmentChange.Transition(
                assistantId = assistantId,
                commitmentId = commitmentId,
                status = CompanionCommitmentStatus.RETRY_SCHEDULED,
                reason = cleanResult.summary,
                nextDueAt = nextDueAt,
            ),
        ),
        nowMillis = cleanResult.completedAt,
    )
    val continued = transitioned.firstOrNull { it.id == commitmentId }
        ?.takeIf { it.status == CompanionCommitmentStatus.RETRY_SCHEDULED }
        ?: return current.unchangedReduction(snapshot)
    val updatedCommitment = continued.copy(
        lastActionResult = cleanResult,
        updatedAt = cleanResult.completedAt,
    )
    val concernChanges = snapshot.concerns
        .filter { concern ->
            concern.assistantId == assistantId &&
                concern.subjectKey == existing.subjectKey &&
                concern.status == CompanionConcernStatus.ACTIVE
        }
        .map { concern ->
            CompanionConcernChange.Upsert(concern.copy(nextPerceptionAt = nextDueAt))
        }
    return current.withUpdatedSnapshot(
        snapshot = snapshot.copy(
            concerns = CompanionConcernReducer.apply(
                current = snapshot.concerns,
                changes = concernChanges,
                nowMillis = cleanResult.completedAt,
            ),
            commitments = transitioned.map {
                if (it.id == commitmentId) updatedCommitment else it
            },
            updatedAt = maxOf(snapshot.updatedAt, cleanResult.completedAt),
        ),
        affectedCommitmentId = commitmentId,
    )
}

fun cancelCompanionCommitment(
    current: CompanionPersistedState,
    assistantId: String,
    commitmentId: String,
    reason: String,
    nowMillis: Long,
): CompanionRuntimeReduction {
    val snapshot = current.snapshotOrEmpty(assistantId)
    val existing = snapshot.commitments.firstOrNull {
        it.assistantId == assistantId && it.id == commitmentId
    } ?: return current.unchangedReduction(snapshot)
    val transitioned = CompanionCommitmentReducer.apply(
        current = snapshot.commitments,
        changes = listOf(
            CompanionCommitmentChange.Transition(
                assistantId = assistantId,
                commitmentId = commitmentId,
                status = CompanionCommitmentStatus.CANCELLED,
                reason = reason,
            ),
        ),
        nowMillis = nowMillis,
    )
    val cancelled = transitioned.firstOrNull { it.id == commitmentId }
        ?.takeIf { it.status == CompanionCommitmentStatus.CANCELLED }
        ?: return current.unchangedReduction(snapshot)
    val concernChanges = snapshot.concerns
        .filter { concern ->
            concern.assistantId == assistantId &&
                concern.subjectKey == existing.subjectKey &&
                concern.status == CompanionConcernStatus.ACTIVE
        }
        .map { concern ->
            CompanionConcernChange.Cancel(
                assistantId = assistantId,
                concernId = concern.id,
                reason = reason,
            )
        }
    return current.withUpdatedSnapshot(
        snapshot = snapshot.copy(
            concerns = CompanionConcernReducer.apply(
                current = snapshot.concerns,
                changes = concernChanges,
                nowMillis = nowMillis,
            ),
            commitments = transitioned,
            updatedAt = maxOf(snapshot.updatedAt, nowMillis),
        ),
        affectedCommitmentId = cancelled.id,
    )
}

private fun CompanionConcernChange.belongsTo(assistantId: String): Boolean = when (this) {
    is CompanionConcernChange.Upsert -> concern.assistantId == assistantId
    is CompanionConcernChange.Reopen -> concern.assistantId == assistantId
    is CompanionConcernChange.Complete -> this.assistantId == assistantId
    is CompanionConcernChange.Cancel -> this.assistantId == assistantId
}

private fun CompanionPersistedState.snapshotOrEmpty(assistantId: String): CompanionSnapshot =
    snapshots.firstOrNull { it.assistantId == assistantId } ?: CompanionSnapshot.empty(assistantId)

private fun CompanionPersistedState.withUpdatedSnapshot(
    snapshot: CompanionSnapshot,
    appliedRelationshipEventIds: Set<String> = this.appliedRelationshipEventIds.toSet(),
    affectedCommitmentId: String? = null,
): CompanionRuntimeReduction {
    val updated = copy(
        snapshots = snapshots.filterNot { it.assistantId == snapshot.assistantId } + snapshot,
        appliedRelationshipEventIds = appliedRelationshipEventIds.toList(),
    ).normalizedCompanionState()
    val normalizedSnapshot = updated.snapshots.first { it.assistantId == snapshot.assistantId }
    return CompanionRuntimeReduction(
        persistedState = updated,
        snapshot = normalizedSnapshot,
        affectedCommitment = affectedCommitmentId?.let { id ->
            normalizedSnapshot.commitments.firstOrNull { it.id == id }
        },
    )
}

private fun CompanionPersistedState.unchangedReduction(
    snapshot: CompanionSnapshot,
): CompanionRuntimeReduction = CompanionRuntimeReduction(
    persistedState = this,
    snapshot = snapshot,
)

private val SCHEDULABLE_COMMITMENT_STATUSES = setOf(
    CompanionCommitmentStatus.ACTIVE,
    CompanionCommitmentStatus.DUE,
    CompanionCommitmentStatus.RETRY_SCHEDULED,
)

private const val STALE_COMMITMENT_EXECUTION_MILLIS = 5L * 60L * 1_000L
