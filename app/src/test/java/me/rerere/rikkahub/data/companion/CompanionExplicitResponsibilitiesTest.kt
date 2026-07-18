package me.rerere.rikkahub.data.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionExplicitResponsibilitiesTest {
    @Test
    fun `explicit recurring request persists every responsibility in one message`() {
        val anchors = detectExplicitRecurringResponsibilityAnchors(
            assistantId = "lulu",
            userText = "以后帮我看一下闹钟，监督我的学习、睡觉和起床。",
            sourceConversationId = "conversation",
            sourceMessageId = "message",
            nowMillis = 100L,
        )

        assertEquals(4, anchors.size)
        assertEquals(4, anchors.map { it.id }.distinct().size)
        assertTrue(anchors.all { it.kind == CompanionAlwaysOnAnchorKind.RESPONSIBILITY })
        assertTrue(anchors.all { it.sourceMessageId == "message" })
        assertTrue(anchors.any { "闹钟" in it.statement })
        assertTrue(anchors.any { "学习" in it.statement })
        assertTrue(anchors.any { "睡眠" in it.statement })
        assertTrue(anchors.any { "起床" in it.statement })
    }

    @Test
    fun `one off alarm request remains a scheduled task instead of a permanent duty`() {
        val anchors = detectExplicitRecurringResponsibilityAnchors(
            assistantId = "lulu",
            userText = "帮我看一下明早七点的闹钟。",
            sourceConversationId = null,
            sourceMessageId = null,
            nowMillis = 100L,
        )

        assertTrue(anchors.isEmpty())
    }

    @Test
    fun `cancellation removes the matching deterministic responsibility`() {
        val ids = detectExplicitRecurringResponsibilityCancellations(
            assistantId = "lulu",
            userText = "以后不用再监督我学习了。",
        )

        assertEquals(listOf("lulu:responsibility:study"), ids)
    }

    @Test
    fun `model detected responsibility wins over deterministic fallback without losing other areas`() {
        val modelAnchor = CompanionAlwaysOnAnchor(
            id = "model-study",
            assistantId = "lulu",
            kind = CompanionAlwaysOnAnchorKind.RESPONSIBILITY,
            statement = "陪她持续推进学习",
        )
        val fallback = detectExplicitRecurringResponsibilityAnchors(
            assistantId = "lulu",
            userText = "以后监督我学习和睡觉。",
            sourceConversationId = null,
            sourceMessageId = null,
            nowMillis = 100L,
        )

        val merged = mergeAlwaysOnResponsibilityAnchors(listOf(modelAnchor), fallback)

        assertTrue(merged.any { it.id == "model-study" })
        assertFalse(merged.any { it.id == "lulu:responsibility:study" })
        assertTrue(merged.any { it.id == "lulu:responsibility:sleep" })
    }
}
