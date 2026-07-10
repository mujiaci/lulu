package me.rerere.rikkahub.data.service

import me.rerere.rikkahub.data.companion.CompanionRelationshipEventKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AffectiveRelationshipEventsTest {
    @Test
    fun `user boundary memory improves boundary understanding without forced closeness`() {
        val events = buildRelationshipEventsFromMemoryCandidates(
            candidates = listOf(
                candidate(
                    type = "user_boundary",
                    content = "我记得用户明确说过，不希望我用打卡式语气催促。",
                )
            ),
            assistantId = "assistant-a",
            conversationId = "conversation-a",
            createdAt = 100L,
        )

        val event = events.single()
        assertEquals(CompanionRelationshipEventKind.BOUNDARY_EXPRESSED, event.kind)
        assertTrue(event.boundaryDelta > 0f)
        assertEquals(0f, event.closenessDelta)
    }

    @Test
    fun `repair memory lowers tension and restores some trust`() {
        val events = buildRelationshipEventsFromMemoryCandidates(
            candidates = listOf(
                candidate(
                    type = "relationship",
                    content = "我记得我们把昨晚的误会说开了，她接受了我的道歉。",
                    relationshipEffect = "冲突得到修复，重新理解彼此",
                )
            ),
            assistantId = "assistant-a",
            conversationId = "conversation-a",
            createdAt = 100L,
        )

        val event = events.single()
        assertEquals(CompanionRelationshipEventKind.REPAIR, event.kind)
        assertTrue(event.tensionDelta < 0f)
        assertTrue(event.trustDelta > 0f)
    }

    private fun candidate(
        type: String,
        content: String,
        relationshipEffect: String? = null,
    ) = AffectiveMemoryCandidate(
        type = type,
        content = content,
        userSignal = "用户原话",
        sourceMessageNodeIds = listOf("message-1"),
        relationshipEffect = relationshipEffect,
    )
}
