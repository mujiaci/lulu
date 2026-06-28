package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.LuluEnergy
import me.rerere.rikkahub.data.model.LuluMode
import me.rerere.rikkahub.data.model.LuluMood
import me.rerere.rikkahub.data.model.LuluRelationship
import me.rerere.rikkahub.data.model.LuluState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class LuluStateTransformerTest {
    @Test
    fun `injects current state before latest user message`() {
        val assistantId = Uuid.parse("77777777-7777-7777-7777-777777777777")
        val messages = listOf(
            UIMessage.system("base"),
            UIMessage.user("hello"),
            UIMessage.assistant("hi"),
            UIMessage.user("how are you"),
        )
        val state = LuluState(
            assistantId = assistantId,
            statusText = "有点困了",
            innerVoice = "夜里想安静陪着你。",
            mood = LuluMood.SOFT,
            energy = LuluEnergy.SLEEPY,
            relationship = LuluRelationship.CLOSE,
            mode = LuluMode.RESTING,
            updatedAt = 1234L,
        )

        val result = applyLuluStateContext(
            messages = messages,
            assistantId = assistantId,
            states = listOf(state),
        )

        assertEquals(messages.size + 1, result.size)
        assertEquals(MessageRole.SYSTEM, result[result.lastIndex - 1].role)
        assertEquals(MessageRole.USER, result.last().role)
        val injected = result[result.lastIndex - 1].toText()
        assertTrue(injected.contains("<lulu_status>"))
        assertTrue(injected.contains("有点困了"))
        assertTrue(injected.contains("夜里想安静陪着你。"))
        assertTrue(injected.contains("精力：有点困"))
    }

    @Test
    fun `does not change messages when assistant has no stored state`() {
        val assistantId = Uuid.parse("88888888-8888-8888-8888-888888888888")
        val messages = listOf(UIMessage.user("hello"))

        val result = applyLuluStateContext(
            messages = messages,
            assistantId = assistantId,
            states = emptyList(),
        )

        assertEquals(messages, result)
    }
}
