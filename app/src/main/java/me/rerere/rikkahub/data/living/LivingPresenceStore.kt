package me.rerere.rikkahub.data.living

import android.content.Context
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.service.LivingIntent
import me.rerere.rikkahub.service.LivingIntentStatus
import me.rerere.rikkahub.service.LivingIntentReturnClassifier
import me.rerere.rikkahub.service.LivingJudgmentTrace
import me.rerere.rikkahub.service.LivingObservation
import me.rerere.rikkahub.service.RollingJudgmentDecision
import me.rerere.rikkahub.service.RollingJudgmentLoop
import me.rerere.rikkahub.utils.JsonInstant

private val Context.livingPresenceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "living_presence",
)

@Serializable
data class LivingPresenceState(
    val activeIntents: List<LivingIntent> = emptyList(),
    val archivedIntents: List<LivingIntent> = emptyList(),
)

class LivingPresenceStore(
    private val context: Context,
    scope: AppScope,
    private val json: Json = JsonInstant,
) {
    private val stateKey = stringPreferencesKey("state")

    val state: StateFlow<LivingPresenceState> = context.livingPresenceDataStore.data
        .map { prefs ->
            prefs[stateKey]?.let { raw ->
                runCatching { json.decodeFromString<LivingPresenceState>(raw) }
                    .getOrDefault(LivingPresenceState())
            } ?: LivingPresenceState()
        }
        .catch { emit(LivingPresenceState()) }
        .stateIn(scope, SharingStarted.Eagerly, LivingPresenceState())

    suspend fun update(transform: (LivingPresenceState) -> LivingPresenceState) {
        context.livingPresenceDataStore.edit { prefs ->
            val current = prefs[stateKey]?.let { raw ->
                runCatching { json.decodeFromString<LivingPresenceState>(raw) }
                    .getOrDefault(LivingPresenceState())
            } ?: LivingPresenceState()
            prefs[stateKey] = json.encodeToString(transform(current).normalized())
        }
    }

    suspend fun clearAssistant(assistantId: String) {
        update { state -> state.withoutAssistant(assistantId) }
    }

    suspend fun updateIntent(intent: LivingIntent) {
        update { current ->
            current.copy(
                activeIntents = current.activeIntents.map { existing ->
                    if (existing.id == intent.id) intent else existing
                }
            )
        }
    }

    suspend fun completeReturnedIntents(
        assistantId: String,
        userText: String,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (userText.isBlank()) return
        update { current ->
            val updated = current.activeIntents.map { intent ->
                if (intent.matchesAssistant(assistantId)) {
                    when {
                        intent.shouldScheduleWakeReplyRecheck(nowMillis) -> intent.copy(
                            status = LivingIntentStatus.ACTIVE,
                            lastEvaluatedAt = nowMillis,
                            completedReason = null,
                            wakeReplyRecheckAt = nowMillis,
                            nextEvaluateAt = nowMillis + WAKE_REPLY_RECHECK_DELAY_MILLIS,
                        )
                        LivingIntentReturnClassifier.shouldCompleteOnUserReturn(intent, userText, nowMillis) -> intent.copy(
                            status = LivingIntentStatus.COMPLETED,
                            lastEvaluatedAt = nowMillis,
                            completedReason = LivingIntentReturnClassifier.completeReason(intent, userText, nowMillis),
                            nextEvaluateAt = nowMillis,
                        )
                        else -> intent
                    }
                } else {
                    intent
                }
            }
            val completed = updated.filter { it.status == LivingIntentStatus.COMPLETED }
            val completedIds = completed.map { it.id }.toSet()
            current.copy(
                activeIntents = updated.filterNot { it.id in completedIds },
                archivedIntents = (completed + current.archivedIntents).take(ARCHIVED_LIMIT),
            )
        }
    }

    fun nextDueIntent(assistantId: String, nowMillis: Long = System.currentTimeMillis()): LivingIntent? =
        state.value.activeIntents.firstOrNull { intent ->
            intent.status != LivingIntentStatus.COMPLETED &&
                intent.status != LivingIntentStatus.CANCELLED &&
                intent.nextPerceptionAt <= nowMillis &&
                (intent.assistantId.isBlank() || intent.assistantId == assistantId)
        }

    suspend fun evaluateDueIntent(
        assistantId: String,
        nowMillis: Long = System.currentTimeMillis(),
        externalObservation: LivingObservation? = null,
        externalJudgmentTrace: LivingJudgmentTrace? = null,
    ): RollingJudgmentDecision? {
        val intent = nextDueIntent(assistantId = assistantId, nowMillis = nowMillis) ?: return null
        val decision = RollingJudgmentLoop.evaluate(
            intent = intent,
            nowMillis = nowMillis,
            externalObservation = externalObservation,
            externalJudgmentTrace = externalJudgmentTrace,
        )
        val updatedIntent = if (
            intent.kind == me.rerere.rikkahub.service.LivingIntentKind.WAKE_UP &&
            intent.wakeReplyRecheckAt != null &&
            nowMillis >= intent.wakeReplyRecheckAt + WAKE_REPLY_RECHECK_DELAY_MILLIS
        ) {
            decision.updatedIntent.copy(
                status = LivingIntentStatus.COMPLETED,
                lastEvaluatedAt = nowMillis,
                completedReason = "用户醒来后已经回复过，完成一次防睡回笼复查后归档。",
                nextEvaluateAt = nowMillis,
            )
        } else {
            decision.updatedIntent
        }
        updateIntent(updatedIntent)
        archiveCompleted()
        return decision.copy(updatedIntent = updatedIntent)
    }

    suspend fun markIntentSpoken(intentId: String, nowMillis: Long = System.currentTimeMillis()) {
        update { current ->
            current.copy(
                activeIntents = current.activeIntents.map { intent ->
                    if (intent.id == intentId) {
                        intent.copy(
                            lastSpokenAt = nowMillis,
                            spokenCount = intent.spokenCount + 1,
                            status = LivingIntentStatus.ACTIVE,
                        )
                    } else {
                        intent
                    }
                }
            )
        }
    }

    suspend fun archiveCompleted() {
        update { current ->
            val completed = current.activeIntents.filter {
                it.status == LivingIntentStatus.COMPLETED || it.status == LivingIntentStatus.CANCELLED
            }
            val completedIds = completed.map { it.id }.toSet()
            current.copy(
                activeIntents = current.activeIntents.filterNot { it.id in completedIds },
                archivedIntents = (completed + current.archivedIntents).take(120),
            )
        }
    }

    private fun LivingPresenceState.normalized(): LivingPresenceState =
        copy(
            activeIntents = activeIntents
                .distinctBy { it.id }
                .sortedBy { it.nextPerceptionAt }
                .take(ACTIVE_LIMIT),
            archivedIntents = archivedIntents
                .distinctBy { it.id }
                .take(ARCHIVED_LIMIT),
        )

    private fun LivingIntent.matchesAssistant(assistantId: String): Boolean =
        this.assistantId.isBlank() || this.assistantId == assistantId

    private fun LivingIntent.shouldScheduleWakeReplyRecheck(nowMillis: Long): Boolean {
        if (kind != me.rerere.rikkahub.service.LivingIntentKind.WAKE_UP) return false
        if (wakeReplyRecheckAt != null) return false
        val hasBeenActivelyHeld = spokenCount > 0 || silentEvaluationCount > 0 || lastEvaluatedAt != null
        val targetReached = targetAtMillis?.let { nowMillis >= it } ?: hasBeenActivelyHeld
        return hasBeenActivelyHeld && targetReached
    }

    private companion object {
        const val ACTIVE_LIMIT = 60
        const val ARCHIVED_LIMIT = 120
        const val WAKE_REPLY_RECHECK_DELAY_MILLIS = 20 * 60_000L
    }
}

internal fun LivingPresenceState.withoutAssistant(assistantId: String): LivingPresenceState {
    if (assistantId.isBlank()) return this
    return copy(
        activeIntents = activeIntents.filterNot { it.assistantId == assistantId },
        archivedIntents = archivedIntents.filterNot { it.assistantId == assistantId },
    )
}
