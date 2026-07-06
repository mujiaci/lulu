package me.rerere.rikkahub.data.service

import org.junit.Assert.assertEquals
import org.junit.Test

class LivingPresenceScheduleTest {
    @Test
    fun `urgent living presence schedules a near future perception instead of dropping it`() {
        val now = 1_700_000_000_000L
        val nextPerceptionAt = now + 30_000L

        val triggerAt = resolveLivingPresenceTriggerAt(
            nextPerceptionAt = nextPerceptionAt,
            nowMillis = now,
        )

        assertEquals(nextPerceptionAt, triggerAt)
    }

    @Test
    fun `overdue living presence retries soon so the card does not stay stuck`() {
        val now = 1_700_000_000_000L
        val overdueAt = now - 60_000L

        val triggerAt = resolveLivingPresenceTriggerAt(
            nextPerceptionAt = overdueAt,
            nowMillis = now,
        )

        assertEquals(now + 10_000L, triggerAt)
    }
}
