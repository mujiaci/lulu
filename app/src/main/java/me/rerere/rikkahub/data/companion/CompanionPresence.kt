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
    fallbackInnerThought: String? = null,
): CompanionState {
    val cleanPrevious = previous.sanitizedCompanionState()
    if (assistantText.isBlank()) return cleanPrevious

    // A model may omit the hidden <lulu_presence> block even though the visible
    // reply was generated successfully.  Do not leave yesterday's scene and
    // inner voice in the status dialog in that case.  The fallback is deliberately
    // short and grounded in the actual reply; it is only used when the model did
    // not provide a presence field, while fields that are actually supplied by
    // the model still take precedence.
    val fallbackReplySummary = assistantText
        .trim()
        .replace(Regex("\\s+"), " ")
        .take(72)
        .takeIf(String::isNotBlank)
    val fallbackScene = if (
        presence?.description.cleanModelPresenceField(MAX_SCENE_LENGTH) == null &&
        fallbackReplySummary != null
    ) {
        "刚刚和你聊到“$fallbackReplySummary”，注意力还停在这段对话上。"
    } else {
        null
    }
    val fallbackThought = fallbackThoughtForReply(assistantText)

    val statusText = presence?.statusText.cleanModelPresenceField(MAX_STATE_FIELD_LENGTH)
        ?: cleanPrevious.statusText.ifBlank { "刚刚回应你" }
    val innerThought = presence?.innerThought.cleanModelPresenceField(MAX_INNER_THOUGHT_LENGTH)
        ?: presence?.memoryThought.cleanModelPresenceField(MAX_INNER_THOUGHT_LENGTH)
        ?: fallbackInnerThought.cleanUsefulFallbackThought(MAX_INNER_THOUGHT_LENGTH)
        ?: fallbackThought.cleanModelPresenceField(MAX_INNER_THOUGHT_LENGTH)
        ?: cleanPrevious.innerThought
    val mood = presence?.mood.cleanModelPresenceField(MAX_STATE_FIELD_LENGTH) ?: cleanPrevious.mood
    val bodyState = presence?.bodyState.cleanModelPresenceField(MAX_STATE_FIELD_LENGTH) ?: cleanPrevious.bodyState
    val mindState = presence?.mindState.cleanModelPresenceField(MAX_STATE_FIELD_LENGTH) ?: cleanPrevious.mindState
    val activityMode = presence?.activityMode.cleanModelPresenceField(MAX_STATE_FIELD_LENGTH)
        ?: cleanPrevious.activityMode
        .ifBlank { "conversation" }
    val selfScene = presence?.description.cleanModelPresenceField(MAX_SCENE_LENGTH)
        ?: fallbackScene.cleanModelPresenceField(MAX_SCENE_LENGTH)
        ?: cleanPrevious.selfScene
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
        candidate.innerThought != cleanPrevious.innerThought ||
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

private fun fallbackThoughtForReply(reply: String): String {
    val summary = reply
        .trim()
        .replace(Regex("\\s+"), " ")
        .take(36)
    val topic = "刚才聊到“$summary”"
    return when {
        reply.contains("学习") || reply.contains("课程") || reply.contains("背") || reply.contains("题") ->
            "$topic，我还在想怎样把下一步变得更容易开始。"
        reply.contains("睡") || reply.contains("休息") || reply.contains("困") ->
            "$topic，我在留意你的状态，别让这段对话把该休息的时间也占掉。"
        reply.contains("？") || reply.contains("?") || reply.contains("吗") ->
            "$topic，我有点想知道你会怎么接这句话。"
        else -> "$topic，我刚刚把话说出来，也在等你接住下一句。"
    }
}

private fun String?.cleanPresenceField(maxLength: Int): String? =
    this
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.take(maxLength)
        ?.takeIf(String::isNotBlank)

private fun String?.cleanModelPresenceField(maxLength: Int): String? =
    cleanPresenceField(maxLength)
        ?.takeIf { !it.isTechnicalCompanionStateText() }

private fun String?.cleanUsefulFallbackThought(maxLength: Int): String? =
    cleanModelPresenceField(maxLength)
        ?.takeIf { thought ->
            "这一轮对话" !in thought &&
                "主动联系了你" !in thought &&
                "在电话里回应了你" !in thought
        }

private const val MAX_STATE_FIELD_LENGTH = 120
private const val MAX_INNER_THOUGHT_LENGTH = 600
private const val MAX_SCENE_LENGTH = 800
