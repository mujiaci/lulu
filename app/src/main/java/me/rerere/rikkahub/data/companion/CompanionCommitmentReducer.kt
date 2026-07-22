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
                is CompanionCommitmentChange.Upsert -> change.commitment
                    .splitCompoundCommitments()
                    .forEach { commitments.upsert(it, nowMillis) }
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
                history = (existing.history + normalized.history)
                    .distinctBy { "${it.fromStatus}:${it.toStatus}:${it.occurredAt}:${it.reason}" }
                    .sortedBy { it.occurredAt }
                    .takeLast(MAX_COMMITMENT_HISTORY),
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
                history = previous.history.appendTransition(
                    from = previous.status,
                    to = CompanionCommitmentStatus.SUPERSEDED,
                    occurredAt = nowMillis,
                    reason = "Replaced by commitment ${normalized.id}",
                ),
            )
        }
        val initialHistory = normalized.history.ifEmpty {
            listOf(
                CompanionCommitmentHistoryEntry(
                    toStatus = normalized.status,
                    occurredAt = maxOf(normalized.createdAt, 0L),
                    reason = "Commitment recorded",
                ),
            )
        }
        add(
            normalized.copy(
                updatedAt = maxOf(normalized.updatedAt, nowMillis),
                history = initialHistory.takeLast(MAX_COMMITMENT_HISTORY),
            ),
        )
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
        val reason = change.reason.trim().take(500)
        this[index] = existing.copy(
            dueAt = change.nextDueAt ?: existing.dueAt,
            status = change.status,
            updatedAt = nowMillis,
            resolvedAt = resolvedAt,
            statusReason = reason.takeIf(String::isNotBlank),
            history = existing.history.appendTransition(
                from = existing.status,
                to = change.status,
                occurredAt = nowMillis,
                reason = reason,
            ),
        )
    }

    private fun CompanionCommitment.splitCompoundCommitments(): List<CompanionCommitment> {
        val source = responsibility.ifBlank { promise }.trim()
        val clauses = splitCommitmentClauses(source)
        if (clauses.size <= 1) return listOf(this)
        return clauses.mapIndexed { index, clause ->
            val stableSuffix = clause.lowercase().hashCode().toUInt().toString(16)
            copy(
                id = "$id:part:${index + 1}:$stableSuffix",
                subjectKey = "$subjectKey:part:$stableSuffix",
                promise = clause,
                responsibility = clause,
                history = emptyList(),
            )
        }
    }

    private fun splitCommitmentClauses(text: String): List<String> {
        val normalized = text
            .replace(Regex("(?m)^\\s*[-*•]+\\s*"), "")
            .replace(Regex("(?m)^\\s*\\d+[.、)]\\s*"), "")
            .trim()
        val strongParts = normalized
            .split(Regex("[\\n；;]+"))
            .map(String::trim)
            .filter { it.length >= 2 }
        if (strongParts.size > 1) return strongParts.distinct()

        val enumeration = normalized
            .split('、')
            .map(String::trim)
            .filter { it.length >= 2 }
        if (enumeration.size <= 1) return listOf(normalized)
        val explicitActionPrefixes = listOf(
            "监督", "提醒", "叫醒", "催", "检查", "确认", "陪", "帮助", "记录", "安排", "联系", "跟进",
        )
        val everyPartIsAction = enumeration.all { part ->
            explicitActionPrefixes.any(part::startsWith)
        }
        return if (everyPartIsAction) enumeration.distinct() else listOf(normalized)
    }

    private fun CompanionCommitment.normalized(): CompanionCommitment = copy(
        subjectKey = normalizeCompanionSubjectKey(subjectKey),
        promise = promise.trim(),
        promisorId = promisorId.trim().ifBlank { assistantId },
        beneficiary = beneficiary.trim().ifBlank { "user" },
        responsibility = responsibility.trim().ifBlank { promise.trim() }.take(500),
        schedule = schedule.copy(
            timeDescription = schedule.timeDescription.trim().take(160),
            frequency = schedule.frequency.trim().take(120),
            condition = schedule.condition.trim().take(240),
        ),
        executionMethod = executionMethod.trim().take(160),
        history = history.sortedBy { it.occurredAt }.takeLast(MAX_COMMITMENT_HISTORY),

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

    private fun List<CompanionCommitmentHistoryEntry>.appendTransition(
        from: CompanionCommitmentStatus,
        to: CompanionCommitmentStatus,
        occurredAt: Long,
        reason: String,
    ): List<CompanionCommitmentHistoryEntry> = (
        this + CompanionCommitmentHistoryEntry(
            fromStatus = from,
            toStatus = to,
            occurredAt = occurredAt,
            reason = reason,
        )
    ).takeLast(MAX_COMMITMENT_HISTORY)

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

    private const val MAX_COMMITMENT_HISTORY = 80
}
