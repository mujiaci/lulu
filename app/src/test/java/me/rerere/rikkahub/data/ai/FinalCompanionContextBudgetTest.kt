package me.rerere.rikkahub.data.ai

import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalCompanionContextBudgetTest {
    @Test
    fun `chat context is capped after transformer additions`() {
        val messages = buildList {
            add(UIMessage.system("persona\n" + "a".repeat(20_000)))
            repeat(12) {
                add(UIMessage.system("<companion_runtime>" + "记忆".repeat(8_000) + "</companion_runtime>"))
            }
            repeat(80) { index ->
                add(UIMessage.user("历史消息 $index " + "x".repeat(500)))
            }
        }

        val result = enforceFinalCompanionContextBudget(messages, ApiUsageSource.CHAT)

        assertTrue(result.estimatedTokens <= 15_000)
        assertTrue(result.compactedSystemMessages > 0)
        assertTrue(result.droppedMessages > 0)
    }

    @Test
    fun `duplicate system blocks are sent once`() {
        val duplicate = UIMessage.system("<lulu_memory>同一条记忆</lulu_memory>")
        val result = enforceFinalCompanionContextBudget(
            messages = listOf(duplicate, duplicate, UIMessage.user("你好")),
            source = ApiUsageSource.CHAT,
        )

        assertEquals(2, result.messages.size)
        assertEquals(1, result.droppedMessages)
    }

    @Test
    fun `phone uses smaller final budget`() {
        val messages = listOf(
            UIMessage.system("persona" + "a".repeat(30_000)),
            UIMessage.system("<companion_runtime>" + "b".repeat(30_000) + "</companion_runtime>"),
            UIMessage.user("c".repeat(30_000)),
        )

        val result = enforceFinalCompanionContextBudget(messages, ApiUsageSource.PHONE)

        assertTrue(result.estimatedTokens <= 10_000)
    }
}
