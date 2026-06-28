package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HumanLikeToolPromptTest {
    @Test
    fun `location tool gets natural proactive guidance after original prompt`() {
        val tool = fakeTool(
            name = "get_location",
            originalPrompt = "original location prompt",
        ).withHumanLikeToolPrompt()

        val prompt = tool.systemPrompt(Model(displayName = "test"), listOf(UIMessage.user("好累")))

        assertTrue(prompt.startsWith("original location prompt"))
        assertTrue(prompt.contains("像真实的人一样"))
        assertTrue(prompt.contains("地点"))
        assertTrue(prompt.contains("不要说“我调用了工具”"))
    }

    @Test
    fun `side effect tools are told to wait for explicit intent`() {
        val tool = fakeTool(
            name = "clipboard_tool",
            originalPrompt = "",
        ).withHumanLikeToolPrompt()

        val prompt = tool.systemPrompt(Model(displayName = "test"), emptyList())

        assertTrue(prompt.contains("不要主动写入"))
        assertTrue(prompt.contains("明确表达"))
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
        execute = { _: JsonElement -> listOf(me.rerere.ai.ui.UIMessagePart.Text(JsonNull.toString())) },
    )
}
