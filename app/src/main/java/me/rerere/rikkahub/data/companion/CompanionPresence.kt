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
)

fun buildCompanionStateFromTurn(
    previous: CompanionState,
    assistantText: String,
    presence: CompanionModelPresence?,
    nowMillis: Long,
): CompanionState {
    if (assistantText.isBlank()) return previous

    val statusText = presence?.statusText.cleanPresenceField(MAX_STATE_FIELD_LENGTH)
        ?: previous.statusText
        .ifBlank { "刚刚回应" }
    val innerThought = presence?.innerThought.cleanPresenceField(MAX_INNER_THOUGHT_LENGTH)
        ?: presence?.memoryThought.cleanPresenceField(MAX_INNER_THOUGHT_LENGTH)
        ?: previous.innerThought
    val mood = presence?.mood.cleanPresenceField(MAX_STATE_FIELD_LENGTH) ?: previous.mood
    val bodyState = presence?.bodyState.cleanPresenceField(MAX_STATE_FIELD_LENGTH) ?: previous.bodyState
    val mindState = presence?.mindState.cleanPresenceField(MAX_STATE_FIELD_LENGTH) ?: previous.mindState
    val activityMode = presence?.activityMode.cleanPresenceField(MAX_STATE_FIELD_LENGTH)
        ?: previous.activityMode
        .ifBlank { "conversation" }
    val selfScene = presence?.description.cleanPresenceField(MAX_SCENE_LENGTH) ?: previous.selfScene
    val visibleStateChanged = statusText != previous.statusText ||
        mood != previous.mood ||
        bodyState != previous.bodyState ||
        mindState != previous.mindState ||
        activityMode != previous.activityMode ||
        selfScene != previous.selfScene

    return previous.copy(
        statusText = statusText,
        innerThought = innerThought,
        mood = mood,
        bodyState = bodyState,
        mindState = mindState,
        activityMode = activityMode,
        selfScene = selfScene,
        updatedAt = nowMillis,
        sinceAt = if (visibleStateChanged || previous.sinceAt <= 0L) nowMillis else previous.sinceAt,
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
