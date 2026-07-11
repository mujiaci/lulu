package me.rerere.rikkahub.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProactiveMessageWorkerTest {
    @Test
    fun `targeted work keeps commitment identity and exact remaining delay`() {
        val spec = buildTargetedProactiveWorkSpec(
            triggerAtMillis = 130_000L,
            nowMillis = 10_000L,
            assistantId = "assistant-1",
            commitmentId = "commitment-1",
        )

        assertEquals("targeted_proactive_message_work", spec.uniqueWorkName)
        assertEquals(120_000L, spec.delayMillis)
        assertEquals("assistant-1", spec.assistantId)
        assertEquals("commitment-1", spec.commitmentId)
        assertTrue(spec.isTargeted)
    }

    @Test
    fun `targeted work never produces a negative delay`() {
        val spec = buildTargetedProactiveWorkSpec(
            triggerAtMillis = 9_000L,
            nowMillis = 10_000L,
            assistantId = "assistant-1",
            commitmentId = "commitment-1",
        )

        assertEquals(0L, spec.delayMillis)
    }
}
