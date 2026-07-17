package me.rerere.rikkahub.data.companion

sealed interface CompanionCommitmentChange {
    data class Upsert(val commitment: CompanionCommitment) : CompanionCommitmentChange

    data class Transition(
        val assistantId: String,
        val commitmentId: String,
        val status: CompanionCommitmentStatus,
        val reason: String,
        val nextDueAt: Long? = null,
    ) : CompanionCommitmentChange
}

object CompanionCommitmentReducer {
    fun apply(
        current: List<CompanionCommitment>,
        changes: List<CompanionCommitmentChange>,
        nowMillis: Long,
    ): List<CompanionCommitment> {
        val commitments = current
            .map { commitment -> commitment.normalized() }
            .distinctBy { it.id }
            .toMutableList()

        changes.forEach { change ->
            when (change) {
                is CompanionCommitmentChange.Upsert -> commitments.upsert(change.commitment, nowMillis)
                is CompanionCommitmentChange.Transition -> commitments.transition(change, nowMillis)
            }
        }

        return commitments
            .distinctBy { it.id }
            .sortedWith(
                compareBy<CompanionCommitment> { it.status.sortRank() }
                    .thenBy { it.dueAt }
                    .thenByDescending { it.updatedAt }
                    .thenBy { it.id },
            )
    }

    private fun MutableList<CompanionCommitment>.upsert(
        incoming: CompanionCommitment,
        nowMillis: Long,
    ) {
        val normalized = incoming.normalized()
        if (normalized.id.isBlank() || normalized.assistantId.isBlank() || normalized.subjectKey.isBlank()) return

        val sameIdIndex = indexOfFirst { it.id == normalized.id && it.assistantId == normalized.assistantId }
        if (sameIdIndex >= 0) {
            val existing = this[sameIdIndex]
            if (existing.status.isTerminal()) return
            this[sameIdIndex] = normalized.copy(
                createdAt = minOf(existing.createdAt, normalized.createdAt),
                updatedAt = maxOf(existing.updatedAt, normalized.updatedAt, nowMillis),
            )
            return
        }

        val previousIndex = indexOfFirst { existing ->
            existing.assistantId == normalized.assistantId &&
                existing.subjectKey == normalized.subjectKey &&
                !existing.status.isTerminal()
        }
        if (previousIndex >= 0) {
            val previous = this[previousIndex]
            this[previousIndex] = previous.copy(
                status = CompanionCommitmentStatus.SUPERSEDED,
                updatedAt = nowMillis,
                resolvedAt = nowMillis,
                statusReason = "Replaced by commitment ${normalized.id}",
            )
        }
        add(normalized.copy(updatedAt = maxOf(normalized.updatedAt, nowMillis)))
    }

    private fun MutableList<CompanionCommitment>.transition(
        change: CompanionCommitmentChange.Transition,
        nowMillis: Long,
    ) {
        val index = indexOfFirst {
            it.assistantId == change.assistantId && it.id == change.commitmentId
        }
        if (index < 0) return

        val existing = this[index]
        if (change.status !in existing.status.allowedNextStatuses()) return
        val resolvedAt = if (change.status.isTerminal()) nowMillis else null
        this[index] = existing.copy(
            dueAt = change.nextDueAt ?: existing.dueAt,
            status = change.status,
            updatedAt = nowMillis,
            resolvedAt = resolvedAt,
            statusReason = change.reason.trim().takeIf(String::isNotBlank),
        )
    }

    private fun CompanionCommitment.normalized(): CompanionCommitment = copy(
        subjectKey = normalizeCompanionSubjectKey(subjectKey),
        promise = promise.trim(),
        actionPlan = actionPlan.copy(
            toolName = actionPlan.toolName?.trim()?.takeIf(String::isNotBlank),
            argumentsJson = actionPlan.argumentsJson.trim().ifBlank { "{}" },
            userFacingSummary = actionPlan.userFacingSummary.trim(),
            contextText = actionPlan.contextText.trim(),
            category = actionPlan.category.trim(),
            preferredToolNames = actionPlan.preferredToolNames
                .map { it.trim() }
                .filter(String::isNotBlank)
                .distinct()
                .take(10),
        ),
        lastActionResult = lastActionResult?.copy(
            summary = lastActionResult.summary.trim().take(500),
            outputReference = lastActionResult.outputReference?.trim()?.takeIf(String::isNotBlank),
        ),
        attemptCount = attemptCount.coerceAtLeast(0),
    ).sanitizedHumanFacingText()

    private fun CompanionCommitmentStatus.allowedNextStatuses(): Set<CompanionCommitmentStatus> = when (this) {
        CompanionCommitmentStatus.PROPOSED -> setOf(
            CompanionCommitmentStatus.ACTIVE,
            CompanionCommitmentStatus.CANCELLED,
            CompanionCommitmentStatus.SUPERSEDED,
        )
        CompanionCommitmentStatus.ACTIVE -> setOf(
            CompanionCommitmentStatus.DUE,
            CompanionCommitmentStatus.CANCELLED,
            CompanionCommitmentStatus.SUPERSEDED,
        )
        CompanionCommitmentStatus.DUE -> setOf(
            CompanionCommitmentStatus.EXECUTING,
            CompanionCommitmentStatus.CANCELLED,
            CompanionCommitmentStatus.SUPERSEDED,
        )
        CompanionCommitmentStatus.EXECUTING -> setOf(
            CompanionCommitmentStatus.FULFILLED,
            CompanionCommitmentStatus.FAILED,
            CompanionCommitmentStatus.RETRY_SCHEDULED,
            CompanionCommitmentStatus.CANCELLED,
        )
        CompanionCommitmentStatus.FAILED -> setOf(
            CompanionCommitmentStatus.RETRY_SCHEDULED,
            CompanionCommitmentStatus.CANCELLED,
        )
        CompanionCommitmentStatus.RETRY_SCHEDULED -> setOf(
            CompanionCommitmentStatus.DUE,
            CompanionCommitmentStatus.CANCELLED,
            CompanionCommitmentStatus.SUPERSEDED,
        )
        CompanionCommitmentStatus.FULFILLED,
        CompanionCommitmentStatus.CANCELLED,
        CompanionCommitmentStatus.SUPERSEDED -> emptySet()
    }

    private fun CompanionCommitmentStatus.isTerminal(): Boolean = this in setOf(
        CompanionCommitmentStatus.FULFILLED,
        CompanionCommitmentStatus.CANCELLED,
        CompanionCommitmentStatus.SUPERSEDED,
    )

    private fun CompanionCommitmentStatus.sortRank(): Int = when (this) {
        CompanionCommitmentStatus.DUE -> 0
        CompanionCommitmentStatus.EXECUTING -> 1
        CompanionCommitmentStatus.ACTIVE -> 2
        CompanionCommitmentStatus.RETRY_SCHEDULED -> 3
        CompanionCommitmentStatus.PROPOSED -> 4
        CompanionCommitmentStatus.FAILED -> 5
        CompanionCommitmentStatus.FULFILLED -> 6
        CompanionCommitmentStatus.CANCELLED -> 7
        CompanionCommitmentStatus.SUPERSEDED -> 8
    }
}
