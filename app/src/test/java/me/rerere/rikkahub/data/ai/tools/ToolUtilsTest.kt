package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.buildJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolUtilsTest {
    @Test
    fun `generation tool selection keeps planner requests and trims unrelated schemas`() {
        val tools = (1..20).map { index -> tool("unrelated_$index") } + listOf(
            tool("set_alarm"),
            tool("favorite_user_message"),
            tool("write_lulu_journal"),
        )

        val selected = tools.selectCompanionToolsForGeneration(
            messages = listOf(UIMessage.user("明天七点叫我起床")),
            preferredToolNames = listOf("set_alarm"),
        )

        assertTrue(selected.any { it.name == "set_alarm" })
        assertTrue(selected.any { it.name == "favorite_user_message" })
        assertTrue(selected.size <= 12)
    }

    @Test
    fun `generation tool selection keeps all tools when the set is already small`() {
        val tools = listOf(tool("a"), tool("b"))

        assertEquals(tools, tools.selectCompanionToolsForGeneration(emptyList()))
    }

    private fun tool(name: String): Tool = Tool(
        name = name,
        description = "Tool named $name",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject { },
                required = emptyList(),
            )
        },
        execute = { emptyList() },
    )
}
