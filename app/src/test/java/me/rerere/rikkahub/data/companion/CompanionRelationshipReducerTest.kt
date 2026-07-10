package me.rerere.rikkahub.data.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionRelationshipReducerTest {
    @Test
    fun `same source relationship event is applied once`() {
        val event = event(
            id = "event-1",
            sourceId = "message-1",
            closenessDelta = 0.2f,
            trustDelta = 0.1f,
        )

        val first = CompanionRelationshipReducer.apply(
            assistantId = ASSISTANT_ID,
            current = CompanionRelationshipState(),
            appliedEventIds = emptySet(),
            events = listOf(event),
            nowMillis = 100L,
        )
        val second = CompanionRelationshipReducer.apply(
            assistantId = ASSISTANT_ID,
            current = first.relationship,
            appliedEventIds = first.appliedEventIds,
            events = listOf(event.copy(id = "event-retry")),
            nowMillis = 200L,
        )

        assertEquals(0.2f, second.relationship.closeness)
        assertEquals(0.6f, second.relationship.trust)
        assertEquals(first.appliedEventIds, second.appliedEventIds)
    }

    @Test
    fun `relationship deltas are clamped to zero and one`() {
        val result = CompanionRelationshipReducer.apply(
            assistantId = ASSISTANT_ID,
            current = CompanionRelationshipState(
                trust = 0.9f,
                closeness = 0.1f,
                unresolvedTension = 0.2f,
            ),
            appliedEventIds = emptySet(),
            events = listOf(
                event(
                    trustDelta = 0.8f,
                    closenessDelta = -0.8f,
                    tensionDelta = 2f,
                ),
            ),
            nowMillis = 100L,
        )

        assertEquals(1f, result.relationship.trust)
        assertEquals(0f, result.relationship.closeness)
        assertEquals(1f, result.relationship.unresolvedTension)
    }

    @Test
    fun `events cannot update another assistant`() {
        val result = CompanionRelationshipReducer.apply(
            assistantId = ASSISTANT_ID,
            current = CompanionRelationshipState(),
            appliedEventIds = emptySet(),
            events = listOf(event(assistantId = "assistant-b", trustDelta = 0.4f)),
            nowMillis = 100L,
        )

        assertEquals(CompanionRelationshipState(), result.relationship)
        assertEquals(emptySet<String>(), result.appliedEventIds)
    }

    @Test
    fun `repair lowers tension without forcing closeness`() {
        val result = CompanionRelationshipReducer.apply(
            assistantId = ASSISTANT_ID,
            current = CompanionRelationshipState(
                closeness = 0.35f,
                unresolvedTension = 0.7f,
            ),
            appliedEventIds = emptySet(),
            events = listOf(
                event(
                    kind = CompanionRelationshipEventKind.REPAIR,
                    tensionDelta = -0.4f,
                ),
            ),
            nowMillis = 100L,
        )

        assertEquals(0.3f, result.relationship.unresolvedTension, 0.0001f)
        assertEquals(0.35f, result.relationship.closeness)
    }

    private fun event(
        id: String = "event-1",
        assistantId: String = ASSISTANT_ID,
        sourceId: String = "message-1",
        kind: CompanionRelationshipEventKind = CompanionRelationshipEventKind.MEANINGFUL_DISCLOSURE,
        trustDelta: Float = 0f,
        closenessDelta: Float = 0f,
        tensionDelta: Float = 0f,
    ): CompanionRelationshipEvent = CompanionRelationshipEvent(
        id = id,
        assistantId = assistantId,
        sourceId = sourceId,
        kind = kind,
        trustDelta = trustDelta,
        closenessDelta = closenessDelta,
        tensionDelta = tensionDelta,
        evidence = "test evidence",
        createdAt = 50L,
    )

    private companion object {
        const val ASSISTANT_ID = "assistant-a"
    }
}
