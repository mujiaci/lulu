package me.rerere.rikkahub.ui.pages.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryDiagnosticsTest {
    @Test
    fun `twenty eligible messages form one ready batch`() {
        val diagnostic = buildMemoryTriggerDiagnostic(
            totalMessageCount = 30,
            protectedRecentCount = 10,
            batchSize = 20,
            successfulThrough = 0,
            batches = emptyList(),
        )

        assertEquals(MemoryTriggerState.READY, diagnostic.state)
        assertEquals(20, diagnostic.eligibleMessageCount)
        assertEquals(20, diagnostic.pendingMessageCount)
        assertEquals(0, diagnostic.messagesUntilNextBatch)
        assertTrue(diagnostic.explanation.contains("还有 20 条等待整理"))
    }

    @Test
    fun `nineteen eligible messages explain one missing message`() {
        val diagnostic = buildMemoryTriggerDiagnostic(
            totalMessageCount = 29,
            protectedRecentCount = 10,
            batchSize = 20,
            successfulThrough = 0,
            batches = emptyList(),
        )

        assertEquals(MemoryTriggerState.WAITING_FOR_MESSAGES, diagnostic.state)
        assertEquals(19, diagnostic.eligibleMessageCount)
        assertEquals(1, diagnostic.messagesUntilNextBatch)
        assertTrue(diagnostic.explanation.contains("还差 1 条"))
    }

    @Test
    fun `fully processed stable region reports next batch distance`() {
        val diagnostic = buildMemoryTriggerDiagnostic(
            totalMessageCount = 35,
            protectedRecentCount = 10,
            batchSize = 20,
            successfulThrough = 20,
            batches = emptyList(),
        )

        assertEquals(MemoryTriggerState.UP_TO_DATE, diagnostic.state)
        assertEquals(20, diagnostic.stableRegionEnd)
        assertEquals(0, diagnostic.pendingMessageCount)
        assertEquals(15, diagnostic.messagesUntilNextBatch)
        assertTrue(diagnostic.explanation.contains("再积累 15 条"))
    }

    @Test
    fun `invalid configuration is safely normalized`() {
        val diagnostic = buildMemoryTriggerDiagnostic(
            totalMessageCount = -5,
            protectedRecentCount = -2,
            batchSize = 0,
            successfulThrough = 999,
            batches = emptyList(),
        )

        assertEquals(0, diagnostic.totalMessageCount)
        assertEquals(0, diagnostic.protectedRecentCount)
        assertEquals(1, diagnostic.batchSize)
        assertEquals(0, diagnostic.successfulThrough)
    }
}
