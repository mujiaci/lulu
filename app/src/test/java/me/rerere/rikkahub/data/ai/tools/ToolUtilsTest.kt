package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolUtilsTest {
    @Test
    fun `study app plan query keeps study tool and filters calendar fallback`() {
        val tools = listOf(
            fakeTool("today_study_plan", "Read app-local study plan todos and progress."),
            fakeTool("calendar_tool", "Read phone calendar events."),
            fakeTool("get_weather", "Weather"),
            fakeTool("get_location", "Location"),
            fakeTool("get_battery_info", "Battery"),
            fakeTool("get_app_usage", "App usage"),
            fakeTool("get_notifications", "Notifications"),
            fakeTool("set_lulu_expression_state", "Lulu state"),
            fakeTool("web_search", "Search web"),
        )

        val selected = tools.selectRelevantToolsForPrompt(
            listOf(UIMessage.user("今天我的考研计划是什么，待办还剩哪些？"))
        )

        assertTrue(selected.any { it.name == "today_study_plan" })
        assertFalse(selected.any { it.name == "calendar_tool" })
    }

    private fun fakeTool(name: String, description: String): Tool = Tool(
        name = name,
        description = description,
        execute = { _: JsonElement -> listOf(UIMessagePart.Text(JsonNull.toString())) },
    )
}
