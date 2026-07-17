package me.rerere.rikkahub.data.companion

fun buildCompanionCurrentLifeThread(
    snapshot: CompanionSnapshot,
    nowMillis: Long,
): String = buildString {
    appendLine("<current_life_thread>")
    snapshot.alwaysOnAnchors
        .filter { it.status == CompanionAlwaysOnAnchorStatus.ACTIVE && (it.expiresAt == null || it.expiresAt > nowMillis) }
        .sortedByDescending { it.importance }
        .take(5)
        .forEach { anchor -> appendLine("- responsibility=${anchor.statement.take(220)}") }
    snapshot.concerns
        .filter { it.status == CompanionConcernStatus.ACTIVE }
        .sortedWith(compareByDescending<CompanionConcern> { it.importance }.thenBy { it.nextPerceptionAt ?: Long.MAX_VALUE })
        .take(4)
        .forEach { concern -> appendLine("- concern=${concern.goal.take(220)} next=${concern.nextPerceptionAt ?: "none"}") }
    snapshot.commitments
        .filter { it.status in ACTIVE_LIFE_THREAD_COMMITMENT_STATUSES }
        .sortedBy { it.dueAt }
        .take(4)
        .forEach { commitment -> appendLine("- promised=${commitment.promise.take(220)} due=${commitment.dueAt}") }
    snapshot.lifeEvents
        .filter { it.isMeaningfulDigitalLifeEvidence() }
        .sortedByDescending { it.endedAt ?: it.startedAt }
        .take(3)
        .forEach { event -> appendLine("- recent_real_event=${event.title.take(160)} result=${event.summary.take(220)}") }
    if (snapshot.state.statusText.isNotBlank() || snapshot.state.mood.isNotBlank()) {
        appendLine("- current_role_state=${snapshot.state.statusText.take(140)} mood=${snapshot.state.mood.take(80)}")
    }
    append("</current_life_thread>")
}.takeIf { it.lineSequence().count() > 2 }.orEmpty()

private val ACTIVE_LIFE_THREAD_COMMITMENT_STATUSES = setOf(
    CompanionCommitmentStatus.ACTIVE,
    CompanionCommitmentStatus.DUE,
    CompanionCommitmentStatus.EXECUTING,
    CompanionCommitmentStatus.RETRY_SCHEDULED,
)
