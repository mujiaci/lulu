package me.rerere.rikkahub.data.companion

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
    fun snapshot(assistantId: String): CompanionSnapshot = store.snapshot(assistantId)

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
        return reduction?.snapshot ?: snapshot(mutation.assistantId)
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
        return reduction?.affectedCommitment
    }

    fun nextCommitment(
        assistantId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): CompanionCommitment? = snapshot(assistantId).commitments
        .asSequence()
        .filter { commitment ->
            commitment.assistantId == assistantId && commitment.status in SCHEDULABLE_COMMITMENT_STATUSES
        }
        .sortedWith(
            compareBy<CompanionCommitment> { it.dueAt > nowMillis }
                .thenBy { it.dueAt }
                .thenBy { it.createdAt }
                .thenBy { it.id },
        )
        .firstOrNull()
}

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
    val updatedSnapshot = existing.copy(
        state = nextState,
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
    val updatedSnapshot = snapshot.copy(
        commitments = transitioned.map { if (it.id == commitmentId) updatedCommitment else it },
        updatedAt = maxOf(snapshot.updatedAt, cleanResult.completedAt),
    )
    return current.withUpdatedSnapshot(updatedSnapshot, affectedCommitmentId = commitmentId)
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
