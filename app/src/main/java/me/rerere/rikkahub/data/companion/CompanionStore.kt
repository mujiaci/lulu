package me.rerere.rikkahub.data.companion

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.utils.JsonInstant

private val Context.companionRuntimeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "companion_runtime",
)

class CompanionStore(
    private val context: Context,
    scope: AppScope,
    private val json: Json = JsonInstant,
) {
    private val stateKey = stringPreferencesKey("state")

    init {
        scope.launch {
            context.companionRuntimeDataStore.edit { preferences ->
                val raw = preferences[stateKey] ?: return@edit
                val normalized = decodeState(raw)
                val encoded = json.encodeToString(normalized)
                if (encoded != raw) preferences[stateKey] = encoded
            }
        }
    }

    val state: StateFlow<CompanionPersistedState> = context.companionRuntimeDataStore.data
        .map { preferences -> decodeState(preferences[stateKey]) }
        .catch { error ->
            Log.e(TAG, "Failed to read companion runtime state", error)
            emit(CompanionPersistedState())
        }
        .stateIn(scope, SharingStarted.Eagerly, CompanionPersistedState())

    suspend fun update(transform: (CompanionPersistedState) -> CompanionPersistedState) {
        context.companionRuntimeDataStore.edit { preferences ->
            val current = decodeState(preferences[stateKey])
            preferences[stateKey] = json.encodeToString(transform(current).normalizedCompanionState())
        }
    }

    suspend fun updateSnapshot(
        assistantId: String,
        transform: (CompanionSnapshot) -> CompanionSnapshot,
    ) {
        if (assistantId.isBlank()) return
        update { current ->
            val existing = current.snapshots.firstOrNull { it.assistantId == assistantId }
                ?: CompanionSnapshot.empty(assistantId)
            val updated = transform(existing).copy(assistantId = assistantId)
            current.copy(
                snapshots = current.snapshots.filterNot { it.assistantId == assistantId } + updated,
            )
        }
    }

    fun snapshot(assistantId: String): CompanionSnapshot =
        state.value.snapshots.firstOrNull { it.assistantId == assistantId }
            ?: CompanionSnapshot.empty(assistantId)

    suspend fun clearAssistant(assistantId: String) {
        update { state -> state.withoutAssistant(assistantId) }
    }

    suspend fun deleteConcernSubjects(assistantId: String, subjectKeys: Set<String>) {
        val normalizedKeys = subjectKeys.map(::normalizeCompanionSubjectKey).filter(String::isNotBlank).toSet()
        if (assistantId.isBlank() || normalizedKeys.isEmpty()) return
        updateSnapshot(assistantId) { snapshot ->
            snapshot.copy(
                concerns = snapshot.concerns.filterNot { concern ->
                    normalizeCompanionSubjectKey(concern.subjectKey) in normalizedKeys
                },
                commitments = snapshot.commitments.filterNot { commitment ->
                    normalizeCompanionSubjectKey(commitment.subjectKey) in normalizedKeys
                },
            )
        }
    }

    suspend fun deleteAlwaysOnAnchor(assistantId: String, anchorId: String) {
        if (assistantId.isBlank() || anchorId.isBlank()) return
        updateSnapshot(assistantId) { snapshot ->
            snapshot.copy(alwaysOnAnchors = snapshot.alwaysOnAnchors.filterNot { it.id == anchorId })
        }
    }

    suspend fun deleteLifeEvent(assistantId: String, eventId: String) {
        if (assistantId.isBlank() || eventId.isBlank()) return
        updateSnapshot(assistantId) { snapshot ->
            snapshot.copy(lifeEvents = snapshot.lifeEvents.filterNot { it.id == eventId })
        }
    }

    suspend fun deleteCommitment(assistantId: String, commitmentId: String) {
        if (assistantId.isBlank() || commitmentId.isBlank()) return
        updateSnapshot(assistantId) { snapshot ->
            snapshot.copy(commitments = snapshot.commitments.filterNot { it.id == commitmentId })
        }
    }

    suspend fun clearRelationshipNarrative(assistantId: String) {
        if (assistantId.isBlank()) return
        updateSnapshot(assistantId) { snapshot ->
            snapshot.copy(
                privateImpression = snapshot.privateImpression.dismissCurrentProfileEvidence().copy(
                    relationshipTitle = "",
                    relationshipNarrative = "",
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun clearUserPortrait(assistantId: String) {
        if (assistantId.isBlank()) return
        updateSnapshot(assistantId) { snapshot ->
            snapshot.copy(
                privateImpression = snapshot.privateImpression.dismissCurrentProfileEvidence().copy(
                    summary = "",
                    userPortrait = "",
                    observedTraits = emptyList(),
                    preferences = emptyList(),
                    boundaries = emptyList(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun clearInteractionUnderstanding(assistantId: String) {
        if (assistantId.isBlank()) return
        updateSnapshot(assistantId) { snapshot ->
            snapshot.copy(
                privateImpression = snapshot.privateImpression.dismissCurrentProfileEvidence().copy(
                    interactionUnderstanding = "",
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun clearUnresolvedRelationshipMatters(assistantId: String) {
        if (assistantId.isBlank()) return
        updateSnapshot(assistantId) { snapshot ->
            snapshot.copy(
                privateImpression = snapshot.privateImpression.dismissCurrentProfileEvidence().copy(
                    unresolvedMatters = emptyList(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun deleteRelationshipEvent(assistantId: String, eventId: String) {
        if (assistantId.isBlank() || eventId.isBlank()) return
        updateSnapshot(assistantId) { snapshot ->
            val remaining = snapshot.relationshipHistory.filterNot { it.id == eventId }
            val rebuilt = CompanionRelationshipReducer.apply(
                assistantId = assistantId,
                current = CompanionRelationshipState(),
                appliedEventIds = emptySet(),
                events = remaining,
                nowMillis = System.currentTimeMillis(),
            )
            snapshot.copy(
                relationship = rebuilt.relationship,
                relationshipHistory = remaining,
            )
        }
    }

    private fun CompanionPrivateImpression.dismissCurrentProfileEvidence(): CompanionPrivateImpression = copy(
        dismissedProfileEvidenceMessageNodeIds = (
            dismissedProfileEvidenceMessageNodeIds + evidenceMessageNodeIds
        ).filter(String::isNotBlank).distinct().takeLast(200),
    )

    private fun decodeState(raw: String?): CompanionPersistedState {
        if (raw.isNullOrBlank()) return CompanionPersistedState()
        return runCatching { json.decodeFromString<CompanionPersistedState>(raw) }
            .onFailure { error -> Log.e(TAG, "Malformed companion runtime state; preserving stored value", error) }
            .getOrDefault(CompanionPersistedState())
            .normalizedCompanionState()
    }

    private companion object {
        const val TAG = "CompanionStore"
    }
}

internal fun CompanionPersistedState.withoutAssistant(assistantId: String): CompanionPersistedState {
    if (assistantId.isBlank()) return this
    return copy(snapshots = snapshots.filterNot { it.assistantId == assistantId })
}

internal fun CompanionPersistedState.normalizedCompanionState(): CompanionPersistedState {
    val normalizedSnapshots = snapshots
        .filter { it.assistantId.isNotBlank() }
        .groupBy { it.assistantId }
        .map { (assistantId, duplicates) ->
            duplicates
                .sortedBy { it.updatedAt }
                .fold(CompanionSnapshot.empty(assistantId)) { accumulated, snapshot ->
                    accumulated.merge(snapshot)
                }
                .normalized()
        }
        .sortedBy { it.assistantId }

    return copy(
        version = CURRENT_COMPANION_SCHEMA_VERSION,
        snapshots = normalizedSnapshots,
        appliedRelationshipEventIds = appliedRelationshipEventIds
            .filter(String::isNotBlank)
            .distinct()
            .takeLast(MAX_APPLIED_RELATIONSHIP_EVENTS),
    )
}

private fun CompanionSnapshot.merge(other: CompanionSnapshot): CompanionSnapshot = copy(
    state = if (other.state.updatedAt >= state.updatedAt) other.state else state,
    stateHistory = (stateHistory + other.stateHistory).distinctBy { it.id },
    neuroState = if (other.neuroState.updatedAt >= neuroState.updatedAt) other.neuroState else neuroState,
    privateImpression = if (other.privateImpression.updatedAt >= privateImpression.updatedAt) {
        other.privateImpression
    } else {
        privateImpression
    },
    alwaysOnAnchors = (alwaysOnAnchors + other.alwaysOnAnchors)
        .groupBy { it.id }
        .values
        .map { duplicates -> duplicates.maxBy { it.updatedAt } },
    goals = (goals + other.goals)
        .groupBy { it.id }
        .values
        .map { duplicates -> duplicates.maxBy { it.updatedAt } },
    lifeEvents = (lifeEvents + other.lifeEvents).distinctBy { it.id },
    relationship = if (other.relationship.updatedAt >= relationship.updatedAt) {
        other.relationship
    } else {
        relationship
    },
    relationshipHistory = (relationshipHistory + other.relationshipHistory)
        .groupBy { it.timelineKey() }
        .values
        .map { duplicates -> duplicates.maxBy { it.extractedAt ?: 0L } },
    concerns = (concerns + other.concerns).distinctBy { it.id },
    commitments = (commitments + other.commitments).distinctBy { it.id },
    continuity = if (other.continuity.updatedAt >= continuity.updatedAt) {
        other.continuity
    } else {
        continuity
    },
    updatedAt = maxOf(updatedAt, other.updatedAt),
)

private fun CompanionSnapshot.normalized(): CompanionSnapshot = copy(
    continuity = continuity.copy(
        conversationId = continuity.conversationId?.trim()?.takeIf(String::isNotBlank),
        lastUserText = continuity.lastUserText.trim().take(800),
        lastAssistantText = continuity.lastAssistantText.trim().take(800),
    ),
    state = state.normalizedForStorage(),
    stateHistory = stateHistory
        .filter { it.recordedAt > 0L && it.state.hasVisibleStateContent() }
        .distinctBy { it.id }
        .sortedBy { it.recordedAt }
        .takeLast(MAX_STATE_HISTORY_PER_ASSISTANT)
        .map { entry -> entry.copy(state = entry.state.normalizedForStorage()) }
        .filter { it.state.hasVisibleStateContent() },
    neuroState = neuroState.normalizedForStorage(),
    privateImpression = privateImpression.normalizedForStorage(),
    alwaysOnAnchors = alwaysOnAnchors
        .filter { anchor -> anchor.assistantId == assistantId && anchor.id.isNotBlank() && anchor.statement.isNotBlank() }
        .groupBy { it.id }
        .values
        .map { duplicates -> duplicates.maxBy { it.updatedAt } }
        .map { anchor ->
            anchor.copy(
                statement = anchor.statement.cleanCompanionHumanText("").take(MAX_STATE_TEXT_LENGTH * 2),
                responsibility = anchor.responsibility?.cleanCompanionHumanText("")?.take(MAX_STATE_TEXT_LENGTH * 3),
                triggers = anchor.triggers.cleanImpressionItems(8),
                actions = anchor.actions.cleanImpressionItems(8),
                avoid = anchor.avoid.cleanImpressionItems(8),
                importance = anchor.importance.coerceIn(1, 5),
            )
        }
        .sortedWith(compareBy<CompanionAlwaysOnAnchor> { it.status != CompanionAlwaysOnAnchorStatus.ACTIVE }.thenByDescending { it.importance }.thenByDescending { it.updatedAt })
        .take(MAX_ALWAYS_ON_ANCHORS_PER_ASSISTANT),
    goals = goals.ifEmpty { defaultCompanionGoals(assistantId) }
        .filter { goal -> goal.assistantId == assistantId && goal.id.isNotBlank() && goal.title.isNotBlank() }
        .groupBy { it.id }
        .values
        .map { duplicates -> duplicates.maxBy { it.updatedAt } }
        .map { goal ->
            goal.copy(
                title = goal.title.cleanCompanionHumanText("").take(MAX_GOAL_TEXT_LENGTH),
                category = goal.category.trim().take(MAX_STATE_TEXT_LENGTH),
                progress = goal.progress.coerceIn(0f, 1f),
                evidenceEventIds = goal.evidenceEventIds.filter(String::isNotBlank).distinct().takeLast(20),
            )
        }
        .sortedWith(compareBy<CompanionGoal> { it.status != CompanionGoalStatus.ACTIVE }.thenByDescending { it.updatedAt })
        .take(MAX_GOALS_PER_ASSISTANT),
    lifeEvents = lifeEvents
        .filter { event -> event.assistantId == assistantId && event.id.isNotBlank() && event.title.isNotBlank() }
        .distinctBy { it.id }
        .sortedWith(compareBy<CompanionLifeEvent> { it.startedAt }.thenBy { it.id })
        .takeLast(MAX_LIFE_EVENTS_PER_ASSISTANT)
        .map { it.normalizedForStorage() },
    relationship = relationship.copy(
        roleLabel = relationship.roleLabel.trim().take(MAX_STATE_TEXT_LENGTH),
        trust = relationship.trust.normalizedDimension(),
        closeness = relationship.closeness.normalizedDimension(),
        reliability = relationship.reliability.normalizedDimension(),
        boundaryConfidence = relationship.boundaryConfidence.normalizedDimension(),
        unresolvedTension = relationship.unresolvedTension.normalizedDimension(),
    ),
    relationshipHistory = relationshipHistory
        .filter { event ->
            event.assistantId == assistantId &&
                event.id.isNotBlank() &&
                event.sourceId.isNotBlank()
        }
        .groupBy { it.timelineKey() }
        .values
        .map { duplicates -> duplicates.maxBy { it.extractedAt ?: 0L } }
        .sortedWith(compareBy<CompanionRelationshipEvent> { it.createdAt }.thenBy { it.id })
        .takeLast(MAX_RELATIONSHIP_HISTORY_PER_ASSISTANT),
    concerns = concerns
        .filter { it.assistantId == assistantId && it.id.isNotBlank() }
        .mergeSemanticConcernDuplicates()
        .sortedWith(
            compareBy<CompanionConcern> { it.status.isTerminal() }
                .thenByDescending { it.importance }
                .thenBy { it.nextPerceptionAt ?: Long.MAX_VALUE }
                .thenByDescending { it.lastUpdatedAt },
        )
        .take(MAX_CONCERNS_PER_ASSISTANT),
    commitments = commitments
        .filter { it.assistantId == assistantId && it.id.isNotBlank() }
        .mergeSemanticCommitmentDuplicates()
        .sortedWith(
            compareBy<CompanionCommitment> { it.status.isTerminal() }
                .thenBy { it.dueAt }
                .thenByDescending { it.updatedAt },
        )
        .take(MAX_COMMITMENTS_PER_ASSISTANT),
)

private fun CompanionNeuroState.normalizedForStorage(): CompanionNeuroState = copy(
    dopamine = dopamine.normalizedDimension(),
    serotonin = serotonin.normalizedDimension(),
    cortisol = cortisol.normalizedDimension(),
    oxytocin = oxytocin.normalizedDimension(),
    norepinephrine = norepinephrine.normalizedDimension(),
    energy = energy.normalizedDimension(),
)

private fun CompanionPrivateImpression.normalizedForStorage(): CompanionPrivateImpression = copy(
    summary = summary.cleanCompanionHumanText("").take(MAX_PRIVATE_IMPRESSION_SUMMARY_LENGTH),
    relationshipTitle = relationshipTitle.cleanCompanionHumanText("").take(MAX_STATE_TEXT_LENGTH),
    relationshipNarrative = relationshipNarrative.cleanCompanionHumanText("").take(MAX_PRIVATE_IMPRESSION_SUMMARY_LENGTH * 2),
    userPortrait = userPortrait.cleanCompanionHumanText("").take(MAX_PRIVATE_IMPRESSION_SUMMARY_LENGTH * 2),
    interactionUnderstanding = interactionUnderstanding.cleanCompanionHumanText("").take(MAX_PRIVATE_IMPRESSION_SUMMARY_LENGTH * 2),
    uncertainties = uncertainties.cleanImpressionItems(8),
    unresolvedMatters = unresolvedMatters.cleanImpressionItems(8),
    evidenceMessageNodeIds = evidenceMessageNodeIds.filter(String::isNotBlank).distinct().takeLast(80),
    dismissedProfileEvidenceMessageNodeIds = dismissedProfileEvidenceMessageNodeIds.filter(String::isNotBlank).distinct().takeLast(200),
    observedTraits = observedTraits.cleanImpressionItems(),
    preferences = preferences.cleanImpressionItems(),
    boundaries = boundaries.cleanImpressionItems(),
    recentChanges = recentChanges.cleanImpressionItems(),
)

private fun CompanionLifeEvent.normalizedForStorage(): CompanionLifeEvent = copy(
    title = title.cleanCompanionHumanText("").take(MAX_LIFE_EVENT_TITLE_LENGTH),
    summary = summary.cleanCompanionHumanText("").take(MAX_LIFE_EVENT_SUMMARY_LENGTH),
    evidenceReference = evidenceReference?.trim()?.take(MAX_EVIDENCE_REFERENCE_LENGTH)?.takeIf(String::isNotBlank),
    relatedMemoryIds = relatedMemoryIds.filter(String::isNotBlank).distinct().takeLast(20),
    importance = importance.coerceIn(1, 5),
    endedAt = endedAt?.coerceAtLeast(startedAt),
    createdAt = createdAt.coerceAtLeast(0L),
)

private fun List<String>.cleanImpressionItems(limit: Int = MAX_PRIVATE_IMPRESSION_ITEMS): List<String> = asSequence()
    .map { it.cleanCompanionHumanText("").take(MAX_PRIVATE_IMPRESSION_ITEM_LENGTH) }
    .filter(String::isNotBlank)
    .distinct()
    .toList()
    .takeLast(limit)

private fun CompanionState.normalizedForStorage(): CompanionState = copy(
    statusText = statusText.trim().take(MAX_STATE_TEXT_LENGTH),
    innerThought = innerThought.trim().take(MAX_INNER_THOUGHT_LENGTH),
    mood = mood.trim().take(MAX_STATE_TEXT_LENGTH),
    bodyState = bodyState.trim().take(MAX_STATE_TEXT_LENGTH),
    mindState = mindState.trim().take(MAX_STATE_TEXT_LENGTH),
    activityMode = activityMode.trim().take(MAX_STATE_TEXT_LENGTH),
    selfScene = selfScene.trim().take(MAX_SELF_SCENE_LENGTH),
).sanitizedCompanionState()

private fun CompanionConcernStatus.isTerminal(): Boolean =
    this == CompanionConcernStatus.COMPLETED || this == CompanionConcernStatus.CANCELLED

private fun CompanionCommitmentStatus.isTerminal(): Boolean = this in setOf(
    CompanionCommitmentStatus.FULFILLED,
    CompanionCommitmentStatus.CANCELLED,
    CompanionCommitmentStatus.SUPERSEDED,
)

private fun List<CompanionConcern>.mergeSemanticConcernDuplicates(): List<CompanionConcern> =
    map { concern ->
        concern.copy(
            subjectKey = normalizeCompanionSubjectKey(concern.subjectKey),
            event = concern.event.cleanCompanionHumanText("正在继续留意这件事。"),
            goal = concern.goal.cleanCompanionHumanText(""),
            completedReason = concern.completedReason
                ?.cleanCompanionHumanText("")
                ?.takeIf(String::isNotBlank),
        )
    }
        .groupBy { concern -> concern.assistantId to concern.subjectKey }
        .values
        .map { duplicates ->
            val selected = duplicates.maxWith(
                compareBy<CompanionConcern> { !it.status.isTerminal() }
                    .thenBy { it.lastUpdatedAt }
                    .thenBy { it.createdAt },
            )
            selected.copy(
                status = when {
                    duplicates.any { it.status == CompanionConcernStatus.ACTIVE } -> CompanionConcernStatus.ACTIVE
                    duplicates.any { it.status == CompanionConcernStatus.PAUSED } -> CompanionConcernStatus.PAUSED
                    else -> selected.status
                },
                importance = duplicates.maxOf { it.importance },
                nextPerceptionAt = duplicates.mapNotNull { it.nextPerceptionAt }.minOrNull(),
                sourceMessageIds = duplicates.flatMap { it.sourceMessageIds }.filter(String::isNotBlank).distinct(),
                createdAt = duplicates.minOf { it.createdAt },
                lastUpdatedAt = duplicates.maxOf { it.lastUpdatedAt },
            )
        }

private fun List<CompanionCommitment>.mergeSemanticCommitmentDuplicates(): List<CompanionCommitment> =
    map { commitment ->
        commitment
            .copy(subjectKey = normalizeCompanionSubjectKey(commitment.subjectKey))
            .sanitizedHumanFacingText()
    }
        .groupBy { commitment -> commitment.assistantId to commitment.subjectKey }
        .values
        .map { duplicates ->
            duplicates.maxWith(
                compareBy<CompanionCommitment> { !it.status.isTerminal() }
                    .thenBy { it.updatedAt }
                    .thenBy { it.createdAt },
            )
        }

private fun Float.normalizedDimension(): Float = coerceIn(0f, 1f)

private fun CompanionRelationshipEvent.timelineKey(): String = "$assistantId:$sourceId:${kind.name}"

private fun CompanionState.hasVisibleStateContent(): Boolean = listOf(
    statusText,
    innerThought,
    mood,
    bodyState,
    mindState,
    activityMode,
    selfScene,
).any(String::isNotBlank)

private const val MAX_APPLIED_RELATIONSHIP_EVENTS = 2_000
private const val MAX_CONCERNS_PER_ASSISTANT = 300
private const val MAX_COMMITMENTS_PER_ASSISTANT = 300
private const val MAX_ALWAYS_ON_ANCHORS_PER_ASSISTANT = 48
private const val MAX_STATE_HISTORY_PER_ASSISTANT = 160
private const val MAX_RELATIONSHIP_HISTORY_PER_ASSISTANT = 160
private const val MAX_LIFE_EVENTS_PER_ASSISTANT = 300
private const val MAX_GOALS_PER_ASSISTANT = 24
private const val MAX_STATE_TEXT_LENGTH = 120
private const val MAX_INNER_THOUGHT_LENGTH = 600
private const val MAX_SELF_SCENE_LENGTH = 800
private const val MAX_GOAL_TEXT_LENGTH = 180
private const val MAX_LIFE_EVENT_TITLE_LENGTH = 120
private const val MAX_LIFE_EVENT_SUMMARY_LENGTH = 500
private const val MAX_EVIDENCE_REFERENCE_LENGTH = 240
private const val MAX_PRIVATE_IMPRESSION_SUMMARY_LENGTH = 600
private const val MAX_PRIVATE_IMPRESSION_ITEM_LENGTH = 240
private const val MAX_PRIVATE_IMPRESSION_ITEMS = 20
