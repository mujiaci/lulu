package me.rerere.rikkahub.data.voicecall

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCallRepositorySummaryTest {
    @Test
    fun `summarizes persisted voice call sessions`() {
        val sessions = listOf(
            VoiceCallSession(
                id = "call-1",
                conversationId = "conversation-1",
                assistantId = "assistant-1",
                assistantName = "露露",
                startedAt = 1L,
                transcript = listOf(
                    VoiceCallLine(role = VoiceCallRole.Assistant, text = "喂"),
                    VoiceCallLine(role = VoiceCallRole.User, text = "在吗"),
                ),
            ),
            VoiceCallSession(
                id = "call-2",
                conversationId = "conversation-1",
                assistantId = "assistant-1",
                assistantName = "露露",
                startedAt = 2L,
                transcript = listOf(
                    VoiceCallLine(role = VoiceCallRole.System, text = "系统记录"),
                ),
            ),
        )

        val summary = summarizeVoiceCallSessions(sessions)

        assertEquals(2, summary.sessionCount)
        assertEquals(2, summary.visibleLineCount)
    }
    @Test
    fun `selects distinct recent openings for the same role and conversation`() {
        val sessions = listOf(
            session("new", 30L, "新的开场"),
            session("duplicate", 20L, "新的开场"),
            session("old", 10L, "旧的开场"),
            session("other", 40L, "别的会话", conversationId = "conversation-2"),
        )

        assertEquals(
            listOf("新的开场", "旧的开场"),
            selectRecentAssistantOpenings(
                sessions = sessions,
                conversationId = "conversation-1",
                assistantId = "assistant-1",
            ),
        )
    }

    private fun session(
        id: String,
        startedAt: Long,
        opening: String,
        conversationId: String = "conversation-1",
    ) = VoiceCallSession(
        id = id,
        conversationId = conversationId,
        assistantId = "assistant-1",
        assistantName = "角色",
        startedAt = startedAt,
        transcript = listOf(VoiceCallLine(role = VoiceCallRole.Assistant, text = opening)),
    )
}
