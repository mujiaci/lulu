package me.rerere.rikkahub.data.service

import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ProactiveMessageAssemblyTest {
    @Test
    fun `proactive generation assembles system history and current context before transforms`() {
        val history = listOf(
            UIMessage.user("昨晚记得叫我起床"),
            UIMessage.assistant("我记着。"),
        )

        val messages = composeProactiveGenerationMessages(
            systemPrompt = "角色人设",
            historyMessages = history,
            currentContext = UIMessage.user("当前感知和长期记忆"),
        )

        assertEquals(4, messages.size)
        assertEquals("角色人设", messages.first().toText())
        assertEquals("当前感知和长期记忆", messages.last().toText())
    }
}
