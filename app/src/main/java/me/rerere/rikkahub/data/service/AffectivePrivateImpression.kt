package me.rerere.rikkahub.data.service

import me.rerere.rikkahub.data.companion.CompanionPrivateImpression
import me.rerere.rikkahub.data.companion.mergeCompanionPrivateImpression

internal fun buildCompanionPrivateImpression(
    previous: CompanionPrivateImpression,
    candidates: List<AffectiveMemoryCandidate>,
    nowMillis: Long,
): CompanionPrivateImpression {
    val dismissedEvidence = previous.dismissedProfileEvidenceMessageNodeIds.toSet()
    val durable = candidates
        .map(AffectiveMemoryCandidate::normalized)
        .filter { it.confidence >= 0.6 && it.importance >= 2 }
        .filter { candidate ->
            (candidate.evidenceMessageNodeIds + candidate.sourceMessageNodeIds)
                .none { it in dismissedEvidence }
        }

    val relationshipCandidates = durable.filter {
        it.type in setOf("relationship", "shared_event", "correction")
    }
    val latestRelationship = relationshipCandidates.lastOrNull()
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
    val recentChanges = relationshipCandidates.map { it.relationshipEffect ?: it.content }

    val relationshipTitle = latestRelationship?.title
    val relationshipNarrative = latestRelationship
        ?.roleFeeling
        ?.takeIf { it.length >= 12 }
        ?: latestRelationship?.content
    val explicitPortrait = latestRelationship
        ?.unspokenThought
        ?.takeIf { it.length >= 12 }
    val userPortrait = explicitPortrait ?: buildUserPortrait(traits, preferences, boundaries)
    val interactionUnderstanding = durable
        .asReversed()
        .firstNotNullOfOrNull { candidate ->
            candidate.relationshipEffect?.takeIf {
                candidate.type in setOf("relationship", "user_preference", "user_boundary", "correction")
            }
        }
    val uncertainties = durable
        .filter { candidate -> candidate.tags.any { it.contains("不确定") || it.contains("uncertain", ignoreCase = true) } }
        .map { it.content }
    val unresolvedMatters = relationshipCandidates
        .filter { candidate ->
            val inspected = listOfNotNull(candidate.content, candidate.relationshipEffect, candidate.tags.joinToString(" "))
                .joinToString(" ")
            listOf("冲突", "误会", "没说开", "不舒服", "unresolved", "conflict")
                .any { inspected.contains(it, ignoreCase = true) } &&
                listOf("和好", "修复", "说开", "解决", "repair")
                    .none { inspected.contains(it, ignoreCase = true) }
        }
        .map { it.content }
    val evidenceIds = durable
        .flatMap { it.evidenceMessageNodeIds + it.sourceMessageNodeIds }
        .filter(String::isNotBlank)
        .distinct()

    if (
        summary.isNullOrBlank() &&
        relationshipTitle.isNullOrBlank() &&
        relationshipNarrative.isNullOrBlank() &&
        userPortrait.isNullOrBlank() &&
        interactionUnderstanding.isNullOrBlank() &&
        uncertainties.isEmpty() &&
        unresolvedMatters.isEmpty() &&
        traits.isEmpty() &&
        preferences.isEmpty() &&
        boundaries.isEmpty() &&
        recentChanges.isEmpty()
    ) return previous

    val merged = mergeCompanionPrivateImpression(
        previous = previous,
        summary = summary,
        relationshipTitle = relationshipTitle,
        relationshipNarrative = relationshipNarrative,
        userPortrait = userPortrait,
        interactionUnderstanding = interactionUnderstanding,
        uncertainties = uncertainties,
        unresolvedMatters = unresolvedMatters,
        evidenceMessageNodeIds = evidenceIds,
        observedTraits = traits,
        preferences = preferences,
        boundaries = boundaries,
        recentChanges = recentChanges,
        nowMillis = nowMillis,
    )
    return if (merged.sameImpressionContent(previous)) previous else merged
}

private fun buildUserPortrait(
    traits: List<String>,
    preferences: List<String>,
    boundaries: List<String>,
): String? {
    val evidence = (traits.takeLast(2) + preferences.takeLast(2) + boundaries.takeLast(1))
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
    if (evidence.isEmpty()) return null
    return "在我眼里，" + evidence.joinToString("；")
}

private fun CompanionPrivateImpression.sameImpressionContent(other: CompanionPrivateImpression): Boolean =
    summary == other.summary &&
        relationshipTitle == other.relationshipTitle &&
        relationshipNarrative == other.relationshipNarrative &&
        userPortrait == other.userPortrait &&
        interactionUnderstanding == other.interactionUnderstanding &&
        uncertainties == other.uncertainties &&
        unresolvedMatters == other.unresolvedMatters &&
        evidenceMessageNodeIds == other.evidenceMessageNodeIds &&
        dismissedProfileEvidenceMessageNodeIds == other.dismissedProfileEvidenceMessageNodeIds &&
        observedTraits == other.observedTraits &&
        preferences == other.preferences &&
        boundaries == other.boundaries &&
        recentChanges == other.recentChanges
