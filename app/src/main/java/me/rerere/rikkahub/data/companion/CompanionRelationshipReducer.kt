package me.rerere.rikkahub.data.companion

data class CompanionRelationshipReduction(
    val relationship: CompanionRelationshipState,
    val appliedEventIds: Set<String>,
)

object CompanionRelationshipReducer {
    fun apply(
        assistantId: String,
        current: CompanionRelationshipState,
        appliedEventIds: Set<String>,
        events: List<CompanionRelationshipEvent>,
        nowMillis: Long,
    ): CompanionRelationshipReduction {
        val appliedKeys = LinkedHashSet<String>(
            appliedEventIds.toList().takeLast(MAX_APPLIED_EVENT_IDS),
        )
        var relationship = current

        events.asSequence()
            .filter { event ->
                event.assistantId == assistantId &&
                    event.id.isNotBlank() &&
                    event.sourceId.isNotBlank()
            }
            .sortedWith(compareBy<CompanionRelationshipEvent> { it.createdAt }.thenBy { it.id })
            .forEach { event ->
                val key = event.deduplicationKey()
                if (key in appliedKeys) return@forEach

                relationship = relationship.copy(
                    trust = (relationship.trust + event.trustDelta).normalizedDimension(),
                    closeness = (relationship.closeness + event.closenessDelta).normalizedDimension(),
                    reliability = (relationship.reliability + event.reliabilityDelta).normalizedDimension(),
                    boundaryConfidence = (relationship.boundaryConfidence + event.boundaryDelta).normalizedDimension(),
                    unresolvedTension = (relationship.unresolvedTension + event.tensionDelta).normalizedDimension(),
                    lastMeaningfulInteractionAt = maxOf(
                        relationship.lastMeaningfulInteractionAt ?: Long.MIN_VALUE,
                        event.createdAt,
                    ).takeUnless { it == Long.MIN_VALUE },
                    updatedAt = maxOf(relationship.updatedAt, event.createdAt, nowMillis),
                )
                appliedKeys += key
                while (appliedKeys.size > MAX_APPLIED_EVENT_IDS) {
                    appliedKeys.remove(appliedKeys.first())
                }
            }

        return CompanionRelationshipReduction(
            relationship = relationship,
            appliedEventIds = appliedKeys,
        )
    }

    private fun CompanionRelationshipEvent.deduplicationKey(): String =
        "$assistantId:$sourceId:${kind.name}"

    private fun Float.normalizedDimension(): Float = coerceIn(0f, 1f)

    private const val MAX_APPLIED_EVENT_IDS = 2_000
}
