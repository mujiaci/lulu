package me.rerere.rikkahub.data.companion

sealed interface CompanionConcernChange {
    data class Upsert(val concern: CompanionConcern) : CompanionConcernChange

    data class Complete(
        val assistantId: String,
        val concernId: String,
        val reason: String,
    ) : CompanionConcernChange

    data class Cancel(
        val assistantId: String,
        val concernId: String,
        val reason: String,
    ) : CompanionConcernChange

    data class Reopen(val concern: CompanionConcern) : CompanionConcernChange
}

object CompanionConcernReducer {
    fun apply(
        current: List<CompanionConcern>,
        changes: List<CompanionConcernChange>,
        nowMillis: Long,
    ): List<CompanionConcern> {
        val concerns = current
            .map { concern -> concern.normalized() }
            .distinctBy { it.id }
            .toMutableList()

        changes.forEach { change ->
            when (change) {
                is CompanionConcernChange.Upsert -> concerns.upsert(change.concern, nowMillis, reopen = false)
                is CompanionConcernChange.Reopen -> concerns.upsert(change.concern, nowMillis, reopen = true)
                is CompanionConcernChange.Complete -> concerns.transition(
                    assistantId = change.assistantId,
                    concernId = change.concernId,
                    status = CompanionConcernStatus.COMPLETED,
                    reason = change.reason,
                    nowMillis = nowMillis,
                )
                is CompanionConcernChange.Cancel -> concerns.transition(
                    assistantId = change.assistantId,
                    concernId = change.concernId,
                    status = CompanionConcernStatus.CANCELLED,
                    reason = change.reason,
                    nowMillis = nowMillis,
                )
            }
        }

        return concerns
            .distinctBy { it.id }
            .sortedWith(
                compareBy<CompanionConcern> { it.status.sortRank() }
                    .thenByDescending { it.importance }
                    .thenBy { it.nextPerceptionAt ?: Long.MAX_VALUE }
                    .thenByDescending { it.lastUpdatedAt }
                    .thenBy { it.id },
            )
    }

    private fun MutableList<CompanionConcern>.upsert(
        incoming: CompanionConcern,
        nowMillis: Long,
        reopen: Boolean,
    ) {
        val normalized = incoming.normalized()
        if (normalized.id.isBlank() || normalized.assistantId.isBlank() || normalized.subjectKey.isBlank()) return

        val matchingIndex = indexOfFirst { existing ->
            existing.assistantId == normalized.assistantId &&
                existing.subjectKey == normalized.subjectKey
        }
        if (matchingIndex < 0) {
            add(
                normalized.copy(
                    status = CompanionConcernStatus.ACTIVE,
                    lastUpdatedAt = maxOf(normalized.lastUpdatedAt, nowMillis),
                ),
            )
            return
        }

        val existing = this[matchingIndex]
        if (existing.status.isTerminal() && !reopen) return
        this[matchingIndex] = normalized.copy(
            id = existing.id,
            status = if (reopen) CompanionConcernStatus.ACTIVE else normalized.status,
            sourceMessageIds = (existing.sourceMessageIds + normalized.sourceMessageIds)
                .filter(String::isNotBlank)
                .distinct(),
            createdAt = minOf(existing.createdAt, normalized.createdAt),
            lastUpdatedAt = maxOf(existing.lastUpdatedAt, normalized.lastUpdatedAt, nowMillis),
            completedReason = if (reopen) null else normalized.completedReason,
        )
    }

    private fun MutableList<CompanionConcern>.transition(
        assistantId: String,
        concernId: String,
        status: CompanionConcernStatus,
        reason: String,
        nowMillis: Long,
    ) {
        val index = indexOfFirst { it.assistantId == assistantId && it.id == concernId }
        if (index < 0 || this[index].status.isTerminal()) return
        this[index] = this[index].copy(
            status = status,
            nextPerceptionAt = null,
            lastUpdatedAt = nowMillis,
            completedReason = reason.trim().takeIf(String::isNotBlank),
        )
    }

    private fun CompanionConcern.normalized(): CompanionConcern = copy(
        subjectKey = normalizeCompanionSubjectKey(subjectKey),
        event = event.trim(),
        goal = goal.trim(),
        importance = importance.coerceIn(1, 5),
        sourceMessageIds = sourceMessageIds.filter(String::isNotBlank).distinct(),
    )

    private fun CompanionConcernStatus.isTerminal(): Boolean =
        this == CompanionConcernStatus.COMPLETED || this == CompanionConcernStatus.CANCELLED

    private fun CompanionConcernStatus.sortRank(): Int = when (this) {
        CompanionConcernStatus.ACTIVE -> 0
        CompanionConcernStatus.PAUSED -> 1
        CompanionConcernStatus.COMPLETED -> 2
        CompanionConcernStatus.CANCELLED -> 3
    }
}

fun normalizeCompanionSubjectKey(value: String): String {
    val normalized = value
        .trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\s*([:/|])\\s*")) { match -> match.groupValues[1] }
    val semanticText = normalized.replace(':', ' ')
    return when {
        listOf("wake", "起床", "叫醒", "闹钟").any { it in semanticText } -> "wake"
        listOf("sleep", "睡觉", "入睡", "休息提醒").any { it in semanticText } -> "sleep"
        normalized.startsWith("care:") || normalized.startsWith("reminder:") ->
            "schedule:${normalized.substringAfter(':')}"
        else -> normalized
    }.take(MAX_COMPANION_SUBJECT_KEY_LENGTH)
}

private const val MAX_COMPANION_SUBJECT_KEY_LENGTH = 240
