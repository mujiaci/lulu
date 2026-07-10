package me.rerere.rikkahub.data.companion

import me.rerere.rikkahub.data.model.LuluState
import me.rerere.rikkahub.service.LivingIntent
import me.rerere.rikkahub.service.LivingIntentKind
import me.rerere.rikkahub.service.LivingIntentStatus

fun LuluState.toCompanionState(): CompanionState = CompanionState(
    statusText = statusText.trim(),
    innerThought = innerVoice.trim(),
    mood = mood.label,
    bodyState = energy.label,
    mindState = mode.label,
    activityMode = mode.label,
    selfScene = selfScene.trim(),
    updatedAt = updatedAt,
    sinceAt = sinceAt,
)

fun LuluState.toCompanionRelationshipState(): CompanionRelationshipState = CompanionRelationshipState(
    roleLabel = relationship.label,
    familiarity = relationshipIntensity.coerceIn(0f, 1f),
    trust = 0.5f,
    closeness = relationshipIntensity.coerceIn(0f, 1f),
    reliability = 0.5f,
    boundaryConfidence = 0.5f,
    unresolvedTension = 0f,
    lastMeaningfulInteractionAt = updatedAt.takeIf { it > 0L },
    updatedAt = updatedAt,
)

fun LivingIntent.toCompanionConcern(): CompanionConcern = CompanionConcern(
    id = id,
    assistantId = assistantId,
    subjectKey = legacyCompanionSubjectKey(),
    event = concernEvent,
    goal = concernGoal,
    status = when (status) {
        LivingIntentStatus.ACTIVE,
        LivingIntentStatus.RESTRAINED -> CompanionConcernStatus.ACTIVE
        LivingIntentStatus.COMPLETED -> CompanionConcernStatus.COMPLETED
        LivingIntentStatus.CANCELLED -> CompanionConcernStatus.CANCELLED
    },
    importance = urgency.coerceIn(1, 5),
    nextPerceptionAt = nextPerceptionAt.takeIf { status == LivingIntentStatus.ACTIVE },
    sourceMessageIds = listOf(id),
    createdAt = createdAt,
    lastUpdatedAt = lastEvaluatedAt ?: createdAt,
    completedReason = completedReason,
)

fun importLegacyCompanionSnapshot(
    assistantId: String,
    current: CompanionSnapshot,
    legacyStates: List<LuluState>,
    legacyIntents: List<LivingIntent>,
): CompanionSnapshot {
    if (assistantId.isBlank()) return current
    val latestLegacyState = legacyStates
        .asSequence()
        .filter { it.assistantId.toString() == assistantId }
        .maxByOrNull { it.updatedAt }
    val importedState = latestLegacyState
        ?.takeIf { current.state.updatedAt <= 0L }
        ?.toCompanionState()
        ?: current.state
    val importedRelationship = latestLegacyState
        ?.takeIf { current.relationship.updatedAt <= 0L }
        ?.toCompanionRelationshipState()
        ?: current.relationship
    val importedConcerns = legacyIntents
        .filter { it.assistantId == assistantId }
        .map { CompanionConcernChange.Upsert(it.toCompanionConcern()) }
    val nowMillis = maxOf(
        current.updatedAt,
        importedState.updatedAt,
        importedRelationship.updatedAt,
        legacyIntents.maxOfOrNull { it.lastEvaluatedAt ?: it.createdAt } ?: 0L,
    )

    return current.copy(
        assistantId = assistantId,
        state = importedState,
        relationship = importedRelationship,
        concerns = CompanionConcernReducer.apply(
            current = current.concerns,
            changes = importedConcerns,
            nowMillis = nowMillis,
        ),
        updatedAt = nowMillis,
    )
}

private fun LivingIntent.legacyCompanionSubjectKey(): String = when {
    kind == LivingIntentKind.WAKE_UP && targetAtMillis != null -> "wake:$targetAtMillis"
    kind == LivingIntentKind.DEADLINE && deadlineAtMillis != null -> "deadline:$deadlineAtMillis"
    else -> "legacy:${kind.name.lowercase()}:$id"
}
