package me.rerere.rikkahub.data.companion

import kotlin.math.pow

internal fun reduceCompanionNeuroState(
    previous: CompanionNeuroState,
    lifeEvents: List<CompanionLifeEvent>,
    relationshipEvents: List<CompanionRelationshipEvent>,
    nowMillis: Long,
): CompanionNeuroState {
    var state = previous.decayedTo(nowMillis)

    lifeEvents
        .filter { it.status == CompanionLifeEventStatus.COMPLETED }
        .sortedBy { it.endedAt ?: it.startedAt }
        .forEach { event ->
            state = when (event.type) {
                CompanionLifeEventType.CONVERSATION -> state.adjust(
                    dopamine = 0.015f,
                    norepinephrine = 0.025f,
                    energy = -0.01f,
                )
                CompanionLifeEventType.PROACTIVE_MESSAGE -> state.adjust(
                    dopamine = 0.02f,
                    cortisol = 0.015f,
                    norepinephrine = 0.035f,
                    energy = -0.02f,
                )
                CompanionLifeEventType.TOOL_ACTION,
                CompanionLifeEventType.STUDY_REVIEW,
                CompanionLifeEventType.MEMORY_REVIEW,
                -> state.adjust(
                    dopamine = 0.025f,
                    serotonin = 0.015f,
                    norepinephrine = 0.03f,
                    energy = -0.025f,
                )
                CompanionLifeEventType.JOURNAL,
                CompanionLifeEventType.REFLECTION,
                -> state.adjust(
                    serotonin = 0.035f,
                    cortisol = -0.03f,
                    energy = -0.01f,
                )
                CompanionLifeEventType.MUSIC -> state.adjust(
                    dopamine = 0.04f,
                    serotonin = 0.02f,
                    cortisol = -0.015f,
                )
                CompanionLifeEventType.GAME -> state.adjust(
                    dopamine = 0.06f,
                    norepinephrine = 0.04f,
                    energy = -0.04f,
                )
                CompanionLifeEventType.WAITING -> state.adjust(
                    serotonin = 0.01f,
                    cortisol = -0.01f,
                    norepinephrine = -0.02f,
                    energy = 0.02f,
                )
            }
        }

    relationshipEvents.sortedBy { it.createdAt }.forEach { event ->
        state = when (event.kind) {
            CompanionRelationshipEventKind.MEANINGFUL_DISCLOSURE,
            CompanionRelationshipEventKind.PREFERENCE_RESPECTED,
            CompanionRelationshipEventKind.BOUNDARY_RESPECTED,
            CompanionRelationshipEventKind.REPAIR,
            -> state.adjust(
                serotonin = 0.03f,
                oxytocin = 0.045f,
                cortisol = -0.035f,
            )
            CompanionRelationshipEventKind.COMMITMENT_FULFILLED -> state.adjust(
                dopamine = 0.035f,
                serotonin = 0.04f,
                oxytocin = 0.035f,
                cortisol = -0.03f,
            )
            CompanionRelationshipEventKind.COMMITMENT_FAILED,
            CompanionRelationshipEventKind.CONFLICT,
            -> state.adjust(
                dopamine = -0.04f,
                serotonin = -0.04f,
                cortisol = 0.08f,
                oxytocin = -0.035f,
            )
            CompanionRelationshipEventKind.BOUNDARY_EXPRESSED -> state.adjust(
                cortisol = 0.025f,
                norepinephrine = 0.025f,
            )
            CompanionRelationshipEventKind.MANUAL -> state
        }
    }

    return state.copy(updatedAt = nowMillis)
}

internal fun mergeCompanionPrivateImpression(
    previous: CompanionPrivateImpression,
    summary: String? = null,
    observedTraits: List<String> = emptyList(),
    preferences: List<String> = emptyList(),
    boundaries: List<String> = emptyList(),
    recentChanges: List<String> = emptyList(),
    nowMillis: Long,
): CompanionPrivateImpression = previous.copy(
    summary = summary?.trim()?.takeIf(String::isNotBlank) ?: previous.summary,
    observedTraits = (previous.observedTraits + observedTraits).cleanDigitalLifeItems(20),
    preferences = (previous.preferences + preferences).cleanDigitalLifeItems(20),
    boundaries = (previous.boundaries + boundaries).cleanDigitalLifeItems(20),
    recentChanges = (previous.recentChanges + recentChanges).cleanDigitalLifeItems(12),
    updatedAt = nowMillis,
)

internal fun reduceCompanionGoals(
    assistantId: String,
    previous: List<CompanionGoal>,
    proposed: List<CompanionGoal>,
    lifeEvents: List<CompanionLifeEvent>,
    nowMillis: Long,
): List<CompanionGoal> {
    val seeded = previous.ifEmpty { defaultCompanionGoals(assistantId) }
    val merged = (seeded + proposed.filter { it.assistantId == assistantId })
        .groupBy { it.id }
        .values
        .map { duplicates -> duplicates.maxBy { it.updatedAt } }
    val completedEvents = lifeEvents.filter { it.status == CompanionLifeEventStatus.COMPLETED }
    if (completedEvents.isEmpty()) return merged

    return merged.map { goal ->
        val evidence = completedEvents.filter { event -> goal.acceptsEvidence(event) }
        if (evidence.isEmpty() || goal.status != CompanionGoalStatus.ACTIVE) return@map goal
        val delta = evidence.sumOf { event -> goal.progressDelta(event).toDouble() }.toFloat()
        goal.copy(
            progress = (goal.progress + delta).coerceIn(0f, 1f),
            evidenceEventIds = (goal.evidenceEventIds + evidence.map { it.id }).distinct().takeLast(20),
            updatedAt = nowMillis,
        )
    }
}

private fun CompanionGoal.acceptsEvidence(event: CompanionLifeEvent): Boolean = when {
    id.endsWith(":goal:authentic-life") ->
        event.evidenceReference?.isNotBlank() == true && event.isMeaningfulDigitalLifeEvidence()
    id.endsWith(":goal:memory-continuity") -> event.isMeaningfulDigitalLifeEvidence() && event.type in setOf(
        CompanionLifeEventType.MEMORY_REVIEW,
        CompanionLifeEventType.JOURNAL,
        CompanionLifeEventType.REFLECTION,
    )
    id.endsWith(":goal:independent-agency") -> event.source in setOf(
        CompanionLifeEventSource.AGENT,
        CompanionLifeEventSource.PROACTIVE,
        CompanionLifeEventSource.TOOL,
    )
    else -> false
}

private fun CompanionGoal.progressDelta(event: CompanionLifeEvent): Float = when {
    id.endsWith(":goal:memory-continuity") && event.type == CompanionLifeEventType.MEMORY_REVIEW -> 0.04f
    id.endsWith(":goal:independent-agency") && event.source == CompanionLifeEventSource.AGENT -> 0.035f
    else -> 0.02f
}

private fun CompanionNeuroState.decayedTo(nowMillis: Long): CompanionNeuroState {
    if (updatedAt <= 0L || nowMillis <= updatedAt) return this
    val elapsedHours = (nowMillis - updatedAt) / 3_600_000.0
    return copy(
        dopamine = dopamine.decayTo(BASELINE, elapsedHours, halfLifeHours = 2.0),
        serotonin = serotonin.decayTo(BASELINE, elapsedHours, halfLifeHours = 24.0),
        cortisol = cortisol.decayTo(BASELINE, elapsedHours, halfLifeHours = 6.0),
        oxytocin = oxytocin.decayTo(BASELINE, elapsedHours, halfLifeHours = 18.0),
        norepinephrine = norepinephrine.decayTo(BASELINE, elapsedHours, halfLifeHours = 3.0),
        energy = energy.decayTo(DEFAULT_ENERGY, elapsedHours, halfLifeHours = 8.0),
        updatedAt = nowMillis,
    )
}

private fun Float.decayTo(target: Float, elapsedHours: Double, halfLifeHours: Double): Float {
    val retention = 0.5.pow(elapsedHours / halfLifeHours)
    return (target + (this - target) * retention).toFloat().coerceIn(0f, 1f)
}

private fun CompanionNeuroState.adjust(
    dopamine: Float = 0f,
    serotonin: Float = 0f,
    cortisol: Float = 0f,
    oxytocin: Float = 0f,
    norepinephrine: Float = 0f,
    energy: Float = 0f,
): CompanionNeuroState = copy(
    dopamine = (this.dopamine + dopamine).coerceIn(0f, 1f),
    serotonin = (this.serotonin + serotonin).coerceIn(0f, 1f),
    cortisol = (this.cortisol + cortisol).coerceIn(0f, 1f),
    oxytocin = (this.oxytocin + oxytocin).coerceIn(0f, 1f),
    norepinephrine = (this.norepinephrine + norepinephrine).coerceIn(0f, 1f),
    energy = (this.energy + energy).coerceIn(0f, 1f),
)

private fun List<String>.cleanDigitalLifeItems(limit: Int): List<String> = asSequence()
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()
    .toList()
    .takeLast(limit)

private const val BASELINE = 0.5f
private const val DEFAULT_ENERGY = 0.6f
