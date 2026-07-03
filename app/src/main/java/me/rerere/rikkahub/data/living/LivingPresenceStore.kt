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
import me.rerere.rikkahub.service.LivingBeliefStore
import me.rerere.rikkahub.service.LivingIntent
import me.rerere.rikkahub.service.LivingIntentKind
import me.rerere.rikkahub.service.LivingIntentStatus
import me.rerere.rikkahub.service.LivingPresenceEvent
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

    suspend fun mergeEvent(event: LivingPresenceEvent, nowMillis: Long = System.currentTimeMillis()) {
        update { current ->
            current.copy(
                activeIntents = LivingBeliefStore.mergeEvent(
                    existingIntents = current.activeIntents,
                    event = event,
                    nowMillis = nowMillis,
                )
            )
        }
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
                if (intent.matchesAssistant(assistantId) && intent.shouldCompleteOnUserReturn(userText)) {
                    intent.copy(
                        status = LivingIntentStatus.COMPLETED,
                        lastEvaluatedAt = nowMillis,
                        completedReason = completeReason(intent, userText),
                        nextEvaluateAt = nowMillis,
                    )
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
                intent.nextEvaluateAt <= nowMillis &&
                (intent.assistantId.isBlank() || intent.assistantId == assistantId)
        }

    suspend fun evaluateDueIntent(
        assistantId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): RollingJudgmentDecision? {
        val intent = nextDueIntent(assistantId = assistantId, nowMillis = nowMillis) ?: return null
        val decision = RollingJudgmentLoop.evaluate(intent, nowMillis)
        updateIntent(decision.updatedIntent)
        return decision
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
                .sortedBy { it.nextEvaluateAt }
                .take(ACTIVE_LIMIT),
            archivedIntents = archivedIntents
                .distinctBy { it.id }
                .take(ARCHIVED_LIMIT),
        )

    private fun LivingIntent.matchesAssistant(assistantId: String): Boolean =
        this.assistantId.isBlank() || this.assistantId == assistantId

    private fun LivingIntent.shouldCompleteOnUserReturn(userText: String): Boolean {
        val text = userText.lowercase()
        val hasBeenActivelyHeld = spokenCount > 0 || silentEvaluationCount > 0 || lastEvaluatedAt != null
        return when (kind) {
            LivingIntentKind.ORDINARY_SILENCE -> hasBeenActivelyHeld
            LivingIntentKind.HEALTH_SAFETY -> hasBeenActivelyHeld && text.containsAny(
                "没事",
                "好多了",
                "不疼",
                "不痛",
                "吃药",
                "回来了",
                "还好",
                "好了",
            )
            LivingIntentKind.STUDY_FOCUS -> hasBeenActivelyHeld && text.containsAny(
                "学完",
                "完成",
                "做完",
                "休息",
                "回来了",
                "结束",
            )
            LivingIntentKind.DEADLINE -> text.containsAny(
                "完成",
                "做完",
                "交了",
                "提交",
                "搞定",
                "弄完",
            )
            LivingIntentKind.WAKE_UP -> text.containsAny(
                "醒了",
                "起了",
                "起来",
                "起床了",
            )
        }
    }

    private fun completeReason(intent: LivingIntent, userText: String): String =
        when (intent.kind) {
            LivingIntentKind.ORDINARY_SILENCE -> "用户重新回来发消息，沉默回复预期结束。"
            LivingIntentKind.HEALTH_SAFETY -> "用户反馈了身体状态：${userText.take(80)}"
            LivingIntentKind.STUDY_FOCUS -> "用户反馈了学习/休息状态：${userText.take(80)}"
            LivingIntentKind.DEADLINE -> "用户反馈任务完成或提交：${userText.take(80)}"
            LivingIntentKind.WAKE_UP -> "用户反馈已经醒来或起床：${userText.take(80)}"
        }

    private fun String.containsAny(vararg words: String): Boolean =
        words.any { contains(it) }

    private companion object {
        const val ACTIVE_LIMIT = 60
        const val ARCHIVED_LIMIT = 120
    }
}
