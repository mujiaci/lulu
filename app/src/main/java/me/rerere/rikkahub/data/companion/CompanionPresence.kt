package me.rerere.rikkahub.data.companion

data class CompanionModelPresence(
    val statusText: String? = null,
    val description: String? = null,
    val innerThought: String? = null,
    val memoryThought: String? = null,
    val mood: String? = null,
    val bodyState: String? = null,
    val mindState: String? = null,
    val activityMode: String? = null,
    val userState: String? = null,
)

fun buildCompanionStateFromTurn(
    previous: CompanionState,
    assistantText: String,
    presence: CompanionModelPresence?,
    nowMillis: Long,
): CompanionState {
    val cleanPrevious = previous.sanitizedCompanionState()
    if (assistantText.isBlank()) return cleanPrevious

    val statusText = presence?.statusText.cleanPresenceField(MAX_STATE_FIELD_LENGTH)
        ?: cleanPrevious.statusText
        .ifBlank { "刚刚回应" }
    val innerThought = presence?.innerThought.cleanPresenceField(MAX_INNER_THOUGHT_LENGTH)
        ?: presence?.memoryThought.cleanPresenceField(MAX_INNER_THOUGHT_LENGTH)
        ?: cleanPrevious.innerThought
    val mood = presence?.mood.cleanPresenceField(MAX_STATE_FIELD_LENGTH) ?: cleanPrevious.mood
    val bodyState = presence?.bodyState.cleanPresenceField(MAX_STATE_FIELD_LENGTH) ?: cleanPrevious.bodyState
    val mindState = presence?.mindState.cleanPresenceField(MAX_STATE_FIELD_LENGTH) ?: cleanPrevious.mindState
    val activityMode = presence?.activityMode.cleanPresenceField(MAX_STATE_FIELD_LENGTH)
        ?: cleanPrevious.activityMode
        .ifBlank { "conversation" }
    val selfScene = presence?.description.cleanPresenceField(MAX_SCENE_LENGTH) ?: cleanPrevious.selfScene
    val candidate = cleanPrevious.copy(
        statusText = statusText,
        innerThought = innerThought,
        mood = mood,
        bodyState = bodyState,
        mindState = mindState,
        activityMode = activityMode,
        selfScene = selfScene,
    ).sanitizedCompanionState()
    val visibleStateChanged = candidate.statusText != cleanPrevious.statusText ||
        candidate.mood != cleanPrevious.mood ||
        candidate.bodyState != cleanPrevious.bodyState ||
        candidate.mindState != cleanPrevious.mindState ||
        candidate.activityMode != cleanPrevious.activityMode ||
        candidate.selfScene != cleanPrevious.selfScene

    return candidate.copy(
        updatedAt = nowMillis,
        sinceAt = if (visibleStateChanged || cleanPrevious.sinceAt <= 0L) nowMillis else cleanPrevious.sinceAt,
    )
}

private fun String?.cleanPresenceField(maxLength: Int): String? =
    this
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.take(maxLength)
        ?.takeIf(String::isNotBlank)

private const val MAX_STATE_FIELD_LENGTH = 120
private const val MAX_INNER_THOUGHT_LENGTH = 600
private const val MAX_SCENE_LENGTH = 800
