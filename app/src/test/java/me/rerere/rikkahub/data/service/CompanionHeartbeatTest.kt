package me.rerere.rikkahub.data.service

import me.rerere.rikkahub.data.companion.CompanionInteractionTimeline
import me.rerere.rikkahub.data.companion.CompanionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionHeartbeatTest {
    @Test
    fun `recent user activity stays local and does not request a model`() {
        val decision = CompanionHeartbeatEvaluator.evaluate(
            snapshot = CompanionSnapshot.empty("assistant-a").copy(
                interactionTimeline = CompanionInteractionTimeline(lastUserActivityAt = 9_700_000L),
            ),
            nowMillis = 10_000_000L,
        )

        assertFalse(decision.shouldRunDeepPerception)
        assertEquals("local_heartbeat_only", decision.reason)
    }

    @Test
    fun `outbound contact cannot hide a long user absence`() {
        val decision = CompanionHeartbeatEvaluator.evaluate(
            snapshot = CompanionSnapshot.empty("assistant-a").copy(
                interactionTimeline = CompanionInteractionTimeline(
                    lastUserActivityAt = 1_000_000L,
                    lastOutboundAt = 2_000_000L,
                ),
            ),
            nowMillis = 10_000_000L,
        )

        assertTrue(decision.minutesSinceUserActivity > 120L)
        assertTrue(decision.shouldRunDeepPerception)
        assertEquals("meaningful_silence", decision.reason)
    }
}
