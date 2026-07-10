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
    relationship = if (other.relationship.updatedAt >= relationship.updatedAt) {
        other.relationship
    } else {
        relationship
    },
    concerns = (concerns + other.concerns).distinctBy { it.id },
    commitments = (commitments + other.commitments).distinctBy { it.id },
    updatedAt = maxOf(updatedAt, other.updatedAt),
)

private fun CompanionSnapshot.normalized(): CompanionSnapshot = copy(
    state = state.copy(
        statusText = state.statusText.trim().take(MAX_STATE_TEXT_LENGTH),
        innerThought = state.innerThought.trim().take(MAX_INNER_THOUGHT_LENGTH),
        mood = state.mood.trim().take(MAX_STATE_TEXT_LENGTH),
        bodyState = state.bodyState.trim().take(MAX_STATE_TEXT_LENGTH),
        mindState = state.mindState.trim().take(MAX_STATE_TEXT_LENGTH),
        activityMode = state.activityMode.trim().take(MAX_STATE_TEXT_LENGTH),
        selfScene = state.selfScene.trim().take(MAX_SELF_SCENE_LENGTH),
    ),
    relationship = relationship.copy(
        roleLabel = relationship.roleLabel.trim().take(MAX_STATE_TEXT_LENGTH),
        trust = relationship.trust.normalizedDimension(),
        closeness = relationship.closeness.normalizedDimension(),
        reliability = relationship.reliability.normalizedDimension(),
        boundaryConfidence = relationship.boundaryConfidence.normalizedDimension(),
        unresolvedTension = relationship.unresolvedTension.normalizedDimension(),
    ),
    concerns = concerns
        .filter { it.assistantId == assistantId && it.id.isNotBlank() }
        .distinctBy { it.id }
        .sortedWith(
            compareBy<CompanionConcern> { it.status.isTerminal() }
                .thenByDescending { it.importance }
                .thenBy { it.nextPerceptionAt ?: Long.MAX_VALUE }
                .thenByDescending { it.lastUpdatedAt },
        )
        .take(MAX_CONCERNS_PER_ASSISTANT),
    commitments = commitments
        .filter { it.assistantId == assistantId && it.id.isNotBlank() }
        .distinctBy { it.id }
        .sortedWith(
            compareBy<CompanionCommitment> { it.status.isTerminal() }
                .thenBy { it.dueAt }
                .thenByDescending { it.updatedAt },
        )
        .take(MAX_COMMITMENTS_PER_ASSISTANT),
)

private fun CompanionConcernStatus.isTerminal(): Boolean =
    this == CompanionConcernStatus.COMPLETED || this == CompanionConcernStatus.CANCELLED

private fun CompanionCommitmentStatus.isTerminal(): Boolean = this in setOf(
    CompanionCommitmentStatus.FULFILLED,
    CompanionCommitmentStatus.CANCELLED,
    CompanionCommitmentStatus.SUPERSEDED,
)

private fun Float.normalizedDimension(): Float = coerceIn(0f, 1f)

private const val MAX_APPLIED_RELATIONSHIP_EVENTS = 2_000
private const val MAX_CONCERNS_PER_ASSISTANT = 300
private const val MAX_COMMITMENTS_PER_ASSISTANT = 300
private const val MAX_STATE_TEXT_LENGTH = 120
private const val MAX_INNER_THOUGHT_LENGTH = 600
private const val MAX_SELF_SCENE_LENGTH = 800
