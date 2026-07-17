package me.rerere.rikkahub.data.service

import me.rerere.rikkahub.data.companion.CompanionActionPlan
import me.rerere.rikkahub.data.companion.CompanionActionType
import me.rerere.rikkahub.data.companion.CompanionCommitment
import me.rerere.rikkahub.data.companion.CompanionToolExecution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ProactiveMessageWorkerTest {
    @Test
    fun `tool commitments require a confirmed successful tool result`() {
        val commitment = CompanionCommitment(
            assistantId = "assistant-1",
            subjectKey = "alarm",
            promise = "Set the alarm",
            dueAt = 1L,
            actionPlan = CompanionActionPlan(type = CompanionActionType.ALARM),
        )

        val missing = validateProactiveCommitmentCompletion(commitment, emptyList(), "Done")
        val failed = validateProactiveCommitmentCompletion(
            commitment,
            listOf(CompanionToolExecution("call-1", "set_alarm", "{}", "{\"success\":false}")),
            "Done",
        )
        val succeeded = validateProactiveCommitmentCompletion(
            commitment,
            listOf(CompanionToolExecution("call-2", "set_alarm", "{}", "{\"success\":true}")),
            "[PASS]",
        )

        assertTrue(!missing.success)
        assertTrue(!failed.success)
        assertTrue(succeeded.success)
    }

    @Test
    fun `responsibility review uses the next local 0045 window`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val before = ZonedDateTime.of(2026, 7, 15, 0, 30, 0, 0, zone).toInstant().toEpochMilli()
        val after = ZonedDateTime.of(2026, 7, 15, 1, 0, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals(
            ZonedDateTime.of(2026, 7, 15, 0, 45, 0, 0, zone).toInstant().toEpochMilli(),
            nextAlwaysOnAnchorReviewAt(before, zone),
        )
        assertEquals(
            ZonedDateTime.of(2026, 7, 16, 0, 45, 0, 0, zone).toInstant().toEpochMilli(),
            nextAlwaysOnAnchorReviewAt(after, zone),
        )
    }
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

    @Test
    fun `overdue commitment is requeued immediately instead of left waiting`() {
        assertEquals(
            11_000L,
            recoveredCommitmentTriggerAt(dueAt = 5_000L, nowMillis = 10_000L),
        )
        assertEquals(
            30_000L,
            recoveredCommitmentTriggerAt(dueAt = 30_000L, nowMillis = 10_000L),
        )
    }
}
