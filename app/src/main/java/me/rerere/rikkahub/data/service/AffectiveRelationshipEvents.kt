package me.rerere.rikkahub.data.service

import me.rerere.rikkahub.data.companion.CompanionRelationshipEvent
import me.rerere.rikkahub.data.companion.CompanionRelationshipEventKind

internal fun buildRelationshipEventsFromMemoryCandidates(
    candidates: List<AffectiveMemoryCandidate>,
    assistantId: String,
    conversationId: String,
    createdAt: Long,
): List<CompanionRelationshipEvent> = candidates
    .mapNotNull { candidate ->
        candidate.toRelationshipEventOrNull(
            assistantId = assistantId,
            conversationId = conversationId,
            createdAt = createdAt,
        )
    }
    .distinctBy { event -> event.sourceId to event.kind }

private fun AffectiveMemoryCandidate.toRelationshipEventOrNull(
    assistantId: String,
    conversationId: String,
    createdAt: Long,
): CompanionRelationshipEvent? {
    val normalized = normalized()
    val sourceId = (normalized.evidenceMessageNodeIds + normalized.sourceMessageNodeIds)
        .firstOrNull()
        ?: return null
    val inspected = listOfNotNull(
        normalized.content,
        normalized.roleFeeling,
        normalized.userSignal,
        normalized.relationshipEffect,
    ).joinToString("\n")
    val effect = when (normalized.type.lowercase()) {
        "user_boundary" -> RelationshipEffect(
            kind = CompanionRelationshipEventKind.BOUNDARY_EXPRESSED,
            boundary = 0.04f,
        )
        "correction" -> RelationshipEffect(
            kind = CompanionRelationshipEventKind.BOUNDARY_EXPRESSED,
            boundary = 0.02f,
        )
        "user_preference" -> if (inspected.hasAny(RESPECT_MARKERS)) {
            RelationshipEffect(
                kind = CompanionRelationshipEventKind.PREFERENCE_RESPECTED,
                trust = 0.01f,
                boundary = 0.01f,
            )
        } else {
            RelationshipEffect(
                kind = CompanionRelationshipEventKind.MEANINGFUL_DISCLOSURE,
                closeness = 0.005f,
            )
        }
        "relationship" -> when {
            inspected.hasAny(REPAIR_MARKERS) -> RelationshipEffect(
                kind = CompanionRelationshipEventKind.REPAIR,
                trust = 0.01f,
                tension = -0.05f,
            )
            inspected.hasAny(CONFLICT_MARKERS) -> RelationshipEffect(
                kind = CompanionRelationshipEventKind.CONFLICT,
                trust = -0.01f,
                tension = 0.05f,
            )
            else -> RelationshipEffect(
                kind = CompanionRelationshipEventKind.MEANINGFUL_DISCLOSURE,
                trust = 0.005f,
                closeness = 0.01f,
            )
        }
        "shared_event" -> RelationshipEffect(
            kind = CompanionRelationshipEventKind.MEANINGFUL_DISCLOSURE,
            closeness = if (normalized.importance >= 4) 0.015f else 0.005f,
        )
        "user_fact" -> if (normalized.importance >= 4) {
            RelationshipEffect(
                kind = CompanionRelationshipEventKind.MEANINGFUL_DISCLOSURE,
                trust = 0.005f,
                closeness = 0.005f,
            )
        } else {
            return null
        }
        else -> return null
    }
    return CompanionRelationshipEvent(
        id = "$assistantId:$conversationId:$sourceId:${effect.kind.name}",
        assistantId = assistantId,
        sourceId = sourceId,
        kind = effect.kind,
        trustDelta = effect.trust,
        closenessDelta = effect.closeness,
        boundaryDelta = effect.boundary,
        tensionDelta = effect.tension,
        evidence = inspected.take(500),
        createdAt = normalized.occurredAtMillis ?: normalized.sourceMessageAtMillis ?: createdAt,
        sourceMessageAt = normalized.sourceMessageAtMillis,
        occurredAt = normalized.occurredAtMillis ?: normalized.sourceMessageAtMillis,
        extractedAt = createdAt,
    )
}

private data class RelationshipEffect(
    val kind: CompanionRelationshipEventKind,
    val trust: Float = 0f,
    val closeness: Float = 0f,
    val boundary: Float = 0f,
    val tension: Float = 0f,
)

private fun String.hasAny(markers: Set<String>): Boolean = markers.any { marker ->
    contains(marker, ignoreCase = true)
}

private val RESPECT_MARKERS = setOf("尊重", "照顾", "按用户希望", "没有强迫", "没有催促")
private val REPAIR_MARKERS = setOf("修复", "和好", "说开", "道歉", "原谅", "重新理解")
private val CONFLICT_MARKERS = setOf("冲突", "争吵", "生气", "失望", "伤害", "误会", "不信任")
