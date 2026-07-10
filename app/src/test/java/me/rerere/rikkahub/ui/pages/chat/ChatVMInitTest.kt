package me.rerere.rikkahub.ui.pages.chat

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.toMessageNode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ChatVMInitTest {
    @Test
    fun `manual reply can be requested only when latest current message is from user`() {
        val empty = conversationWith()
        val userLast = conversationWith(UIMessage.user("第一句"), UIMessage.user("第二句"))
        val assistantLast = conversationWith(UIMessage.user("第一句"), UIMessage.assistant("回复"))

        assertFalse(canRequestManualReply(empty))
        assertTrue(canRequestManualReply(userLast))
        assertFalse(canRequestManualReply(assistantLast))
    }

    private fun conversationWith(vararg messages: UIMessage): Conversation =
        Conversation.ofId(
            id = Uuid.random(),
            messages = messages.map { it.toMessageNode() },
        )
}
