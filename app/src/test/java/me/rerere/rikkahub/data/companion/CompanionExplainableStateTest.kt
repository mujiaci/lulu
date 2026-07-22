package me.rerere.rikkahub.data.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionExplainableStateTest {
    @Test
    fun `new explicit fact replaces inferred life anchor`() {
        val inferred = CompanionLifeAnchor(
            activity = "可能在休息",
            startedAt = 10L,
            source = CompanionLifeAnchorSource.MODEL_INFERRED,
            updatedAt = 20L,
            expiresAt = 1_000L,
            confidence = 0.4f,
        )
        val explicit = CompanionLifeAnchor(
            activity = "正在图书馆复习",
            context = "考研自习",
            contactability = "可以简短联系",
            startedAt = 10L,
            source = CompanionLifeAnchorSource.USER_EXPLICIT,
            evidenceId = "message-1",
            updatedAt = 20L,
            expiresAt = 1_000L,
        )

        assertEquals(explicit, reduceCompanionLifeAnchor(inferred, explicit, 100L))
    }

    @Test
    fun `expired life anchor disappears without model work`() {
        val anchor = CompanionLifeAnchor(
            activity = "正在学习",
            startedAt = 10L,
            source = CompanionLifeAnchorSource.USER_EXPLICIT,
            expiresAt = 50L,
        )

        assertNull(reduceCompanionLifeAnchor(anchor, null, 100L))
    }

    @Test
    fun `state prompt keeps reason evidence expiry and confidence`() {
        val state = CompanionExplainableState(
            emotion = "紧张",
            emotionReason = "明天有考试",
            busy = CompanionStateSignal(
                value = 0.9f,
                reason = "正在做真题",
                evidenceIds = listOf("message-2"),
                updatedAt = 20L,
                expiresAt = 200L,
                confidence = 0.95f,
            ),
            updatedAt = 20L,
        )

        val prompt = state.toPromptLines(100L).joinToString("\n")

        assertTrue(prompt.contains("明天有考试"))
        assertTrue(prompt.contains("正在做真题"))
        assertTrue(prompt.contains("message-2"))
        assertTrue(prompt.contains("0.95"))
    }
}
