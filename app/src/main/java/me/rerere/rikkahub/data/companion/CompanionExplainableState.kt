package me.rerere.rikkahub.data.companion

import kotlinx.serialization.Serializable

@Serializable
data class CompanionExplainableState(
    val emotion: String = "",
    val emotionReason: String = "",
    val energy: CompanionStateSignal = CompanionStateSignal(),
    val calmness: CompanionStateSignal = CompanionStateSignal(),
    val safety: CompanionStateSignal = CompanionStateSignal(),
    val defensiveness: CompanionStateSignal = CompanionStateSignal(),
    val openness: CompanionStateSignal = CompanionStateSignal(),
    val socialWillingness: CompanionStateSignal = CompanionStateSignal(),
    val busy: CompanionStateSignal = CompanionStateSignal(),
    val attentionFocus: String = "",
    val unresolvedConcerns: List<String> = emptyList(),
    val unfinishedThoughts: List<String> = emptyList(),
    val updatedAt: Long = 0L,
)

@Serializable
data class CompanionStateSignal(
    val value: Float = 0.5f,
    val reason: String = "",
    val evidenceIds: List<String> = emptyList(),
    val updatedAt: Long = 0L,
    val expiresAt: Long? = null,
    val confidence: Float = 0f,
)

@Serializable
data class CompanionLifeAnchor(
    val activity: String,
    val context: String = "",
    val contactability: String = "",
    val startedAt: Long,
    val expectedEndAt: Long? = null,
    val source: CompanionLifeAnchorSource,
    val evidenceId: String? = null,
    val updatedAt: Long = startedAt,
    val expiresAt: Long? = expectedEndAt,
    val confidence: Float = 1f,
)

@Serializable
enum class CompanionLifeAnchorSource {
    USER_EXPLICIT,
    TOOL_RESULT,
    SYSTEM_EVENT,
    MODEL_INFERRED,
}

internal fun reduceCompanionLifeAnchor(
    current: CompanionLifeAnchor?,
    incoming: CompanionLifeAnchor?,
    nowMillis: Long,
): CompanionLifeAnchor? {
    val activeCurrent = current?.normalizedLifeAnchor()?.takeIf { it.isActiveAt(nowMillis) }
    val cleanIncoming = incoming?.normalizedLifeAnchor()?.takeIf { it.isActiveAt(nowMillis) }
        ?: return activeCurrent
    if (activeCurrent == null) return cleanIncoming
    return when {
        cleanIncoming.updatedAt > activeCurrent.updatedAt -> cleanIncoming
        cleanIncoming.updatedAt < activeCurrent.updatedAt -> activeCurrent
        cleanIncoming.source.priority() >= activeCurrent.source.priority() -> cleanIncoming
        else -> activeCurrent
    }
}

internal fun newerCompanionLifeAnchor(
    first: CompanionLifeAnchor?,
    second: CompanionLifeAnchor?,
): CompanionLifeAnchor? = when {
    first == null -> second?.normalizedLifeAnchor()
    second == null -> first.normalizedLifeAnchor()
    second.updatedAt > first.updatedAt -> second.normalizedLifeAnchor()
    second.updatedAt < first.updatedAt -> first.normalizedLifeAnchor()
    second.source.priority() >= first.source.priority() -> second.normalizedLifeAnchor()
    else -> first.normalizedLifeAnchor()
}

internal fun CompanionLifeAnchor.normalizedLifeAnchor(): CompanionLifeAnchor = copy(
    activity = activity.trim().take(120),
    context = context.trim().take(160),
    contactability = contactability.trim().take(80),
    expectedEndAt = expectedEndAt?.coerceAtLeast(startedAt),
    expiresAt = expiresAt?.coerceAtLeast(startedAt),
    evidenceId = evidenceId?.trim()?.takeIf(String::isNotBlank),
    confidence = confidence.coerceIn(0f, 1f),
)

internal fun CompanionLifeAnchor.isActiveAt(nowMillis: Long): Boolean =
    activity.isNotBlank() && (expiresAt == null || expiresAt > nowMillis)

internal fun CompanionExplainableState.normalizedExplainableState(): CompanionExplainableState = copy(
    emotion = emotion.trim().take(80),
    emotionReason = emotionReason.trim().take(240),
    energy = energy.normalizedSignal(),
    calmness = calmness.normalizedSignal(),
    safety = safety.normalizedSignal(),
    defensiveness = defensiveness.normalizedSignal(),
    openness = openness.normalizedSignal(),
    socialWillingness = socialWillingness.normalizedSignal(),
    busy = busy.normalizedSignal(),
    attentionFocus = attentionFocus.trim().take(160),
    unresolvedConcerns = unresolvedConcerns.cleanItems(),
    unfinishedThoughts = unfinishedThoughts.cleanItems(),
)

internal fun CompanionExplainableState.toPromptLines(nowMillis: Long): List<String> = buildList {
    if (updatedAt <= 0L) return@buildList
    if (emotion.isNotBlank()) add("explainable_state emotion=${emotion.take(80)} reason=${emotionReason.take(240)}")
    listOf(
        "energy" to energy,
        "calmness" to calmness,
        "safety" to safety,
        "defensiveness" to defensiveness,
        "openness" to openness,
        "social_willingness" to socialWillingness,
        "busy" to busy,
    ).forEach { (name, signal) ->
        if (signal.updatedAt > 0L && (signal.expiresAt == null || signal.expiresAt > nowMillis)) {
            add(
                "state_signal name=$name value=${signal.value} reason=${signal.reason.take(180)} " +
                    "confidence=${signal.confidence} evidence=${signal.evidenceIds.takeLast(4).joinToString(",")}",
            )
        }
    }
    if (attentionFocus.isNotBlank()) add("attention_focus=${attentionFocus.take(160)}")
    unresolvedConcerns.takeLast(4).forEach { add("unresolved_state_concern=${it.take(180)}") }
    unfinishedThoughts.takeLast(4).forEach { add("unfinished_thought=${it.take(180)}") }
}

private fun CompanionStateSignal.normalizedSignal(): CompanionStateSignal = copy(
    value = value.coerceIn(0f, 1f),
    reason = reason.trim().take(240),
    evidenceIds = evidenceIds.map { it.trim() }.filter(String::isNotBlank).distinct().takeLast(20),
    confidence = confidence.coerceIn(0f, 1f),
)

private fun List<String>.cleanItems(): List<String> = map { it.trim() }
    .filter(String::isNotBlank)
    .distinct()
    .takeLast(12)
    .map { it.take(240) }

private fun CompanionLifeAnchorSource.priority(): Int = when (this) {
    CompanionLifeAnchorSource.USER_EXPLICIT -> 4
    CompanionLifeAnchorSource.TOOL_RESULT -> 3
    CompanionLifeAnchorSource.SYSTEM_EVENT -> 2
    CompanionLifeAnchorSource.MODEL_INFERRED -> 1
}
