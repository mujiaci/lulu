package me.rerere.rikkahub.service

import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class VoiceCallChatSyncTest {
    @Test
    fun `voice call visible turn stores only real conversation text`() {
        val conversationId = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val assistantId = Uuid.parse("22222222-2222-2222-2222-222222222222")
        val conversation = Conversation.ofId(conversationId, assistantId)

        val updated = appendVoiceCallVisibleTurn(
            conversation = conversation,
            userText = "我刚刚下课了",
            assistantText = "那先喝口水，慢慢走。",
        )

        val messages = updated.currentMessages
        assertEquals(2, messages.size)
        assertEquals(MessageRole.USER, messages[0].role)
        assertEquals("我刚刚下课了", messages[0].toText())
        assertEquals(MessageRole.ASSISTANT, messages[1].role)
        assertEquals("那先喝口水，慢慢走。", messages[1].toText())
        assertFalse(updated.currentMessages.any { it.toText().contains("请只输出") })
    }

    @Test
    fun `voice call opening stores assistant only`() {
        val conversationId = Uuid.parse("33333333-3333-3333-3333-333333333333")
        val assistantId = Uuid.parse("44444444-4444-4444-4444-444444444444")
        val conversation = Conversation.ofId(conversationId, assistantId)

        val updated = appendVoiceCallVisibleTurn(
            conversation = conversation,
            userText = null,
            assistantText = "喂，我在呢。",
        )

        assertEquals(1, updated.currentMessages.size)
        assertTrue(updated.currentMessages.all { it.role == MessageRole.ASSISTANT })
        assertEquals("喂，我在呢。", updated.currentMessages.single().toText())
    }
}
