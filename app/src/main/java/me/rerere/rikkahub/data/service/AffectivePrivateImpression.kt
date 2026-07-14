package me.rerere.rikkahub.data.service

import me.rerere.rikkahub.data.companion.CompanionPrivateImpression
import me.rerere.rikkahub.data.companion.mergeCompanionPrivateImpression

internal fun buildCompanionPrivateImpression(
    previous: CompanionPrivateImpression,
    candidates: List<AffectiveMemoryCandidate>,
    nowMillis: Long,
): CompanionPrivateImpression {
    val durable = candidates
        .map(AffectiveMemoryCandidate::normalized)
        .filter { it.confidence >= 0.6 && it.importance >= 2 }

    val summary = durable
        .asReversed()
        .firstNotNullOfOrNull { candidate ->
            candidate.relationshipEffect?.takeIf(String::isNotBlank)
                ?: candidate.content.takeIf { candidate.type == "relationship" && it.isNotBlank() }
        }
    val traits = durable
        .filter { it.type == "user_fact" }
        .map { it.userSignal ?: it.content }
    val preferences = durable
        .filter { it.type == "user_preference" }
        .map { it.userSignal ?: it.content }
    val boundaries = durable
        .filter { it.type == "user_boundary" }
        .map { it.userSignal ?: it.content }
    val recentChanges = durable
        .filter { it.type in setOf("relationship", "shared_event", "correction") }
        .map { it.relationshipEffect ?: it.content }

    if (
        summary.isNullOrBlank() &&
        traits.isEmpty() &&
        preferences.isEmpty() &&
        boundaries.isEmpty() &&
        recentChanges.isEmpty()
    ) return previous

    val merged = mergeCompanionPrivateImpression(
        previous = previous,
        summary = summary,
        observedTraits = traits,
        preferences = preferences,
        boundaries = boundaries,
        recentChanges = recentChanges,
        nowMillis = nowMillis,
    )
    return if (merged.sameImpressionContent(previous)) previous else merged
}

private fun CompanionPrivateImpression.sameImpressionContent(other: CompanionPrivateImpression): Boolean =
    summary == other.summary &&
        observedTraits == other.observedTraits &&
        preferences == other.preferences &&
        boundaries == other.boundaries &&
        recentChanges == other.recentChanges
