package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HumanLikeToolPromptTest {
    @Test
    fun `location tool gets compact natural proactive guidance after original prompt`() {
        val tool = fakeTool(
            name = "get_location",
            originalPrompt = "original location prompt",
        ).withHumanLikeToolPrompt()

        val prompt = tool.systemPrompt(Model(displayName = "test"), listOf(UIMessage.user("好累")))

        assertTrue(prompt.startsWith("original location prompt"))
        assertTrue(prompt.contains("位置"))
        assertTrue(prompt.contains("自然使用"))
        assertTrue(prompt.contains("不要说调用工具"))
        assertTrue(prompt.length < 120)
    }

    @Test
    fun `side effect tools keep compact explicit intent guidance`() {
        val tool = fakeTool(
            name = "clipboard_tool",
            originalPrompt = "",
        ).withHumanLikeToolPrompt()

        val prompt = tool.systemPrompt(Model(displayName = "test"), emptyList())

        assertTrue(prompt.contains("敏感或会改变设备状态"))
        assertTrue(prompt.contains("明确意图"))
        assertTrue(prompt.length < 120)
    }

    @Test
    fun `unknown tool keeps original prompt only`() {
        val tool = fakeTool(
            name = "unknown_tool",
            originalPrompt = "keep me",
        ).withHumanLikeToolPrompt()

        assertEquals("keep me", tool.systemPrompt(Model(displayName = "test"), emptyList()))
    }

    private fun fakeTool(name: String, originalPrompt: String): Tool = Tool(
        name = name,
        description = "",
        systemPrompt = { _, _ -> originalPrompt },
        execute = { _: JsonElement -> listOf(UIMessagePart.Text(JsonNull.toString())) },
    )
}
