package me.rerere.rikkahub.service

import org.junit.Assert.assertTrue
import org.junit.Test

class ProactiveToolPlannerTest {
    @Test
    fun `tired message should request health context`() {
        val plan = ProactiveToolPlanner.plan(
            userText = "我今天真的好累，感觉没精神",
            availableToolNames = setOf("get_gadgetbridge_data", "get_battery_info", "get_app_usage"),
        )

        assertTrue(plan.any { it.toolName == "get_gadgetbridge_data" && it.argumentsJson.contains("data_type") })
    }

    @Test
    fun `going outside should request location context`() {
        val plan = ProactiveToolPlanner.plan(
            userText = "我想出去走走，附近有什么地方吗",
            availableToolNames = setOf("get_location", "explore_nearby", "get_battery_info"),
        )

        assertTrue(plan.any { it.toolName == "get_location" })
        assertTrue(plan.any { it.toolName == "explore_nearby" })
    }

    @Test
    fun `class time should request state context and follow up candidates`() {
        val plan = ProactiveToolPlanner.plan(
            userText = "我八点有课，七点五十五记得叫我一下",
            availableToolNames = setOf("get_location", "get_app_usage", "set_alarm", "calendar_tool"),
        )

        assertTrue(plan.any { it.toolName == "get_app_usage" })
        assertTrue(plan.any { it.toolName == "get_location" })
        assertTrue(plan.any { it.toolName == "set_alarm" && !it.autoExecutable })
    }

    @Test
    fun `music mention should inspect current playback without changing it`() {
        val plan = ProactiveToolPlanner.plan(
            userText = "我现在听歌写作业，有点静不下来",
            availableToolNames = setOf("control_music", "get_app_usage"),
        )

        assertTrue(plan.any { it.toolName == "control_music" && it.argumentsJson.contains("get_now_playing") })
    }

    @Test
    fun `sms mention should prefer notifications before sms`() {
        val plan = ProactiveToolPlanner.plan(
            userText = "好像有人给我发短信了，你帮我看看",
            availableToolNames = setOf("get_notifications", "read_sms"),
        )

        assertTrue(plan.any { it.toolName == "get_notifications" })
        assertTrue(plan.any { it.toolName == "read_sms" && !it.autoExecutable })
    }

    @Test
    fun `camera and log intents should become explicit action candidates`() {
        val plan = ProactiveToolPlanner.plan(
            userText = "等下可以帮我看一下桌面，然后把今天这件事记进日志吗",
            availableToolNames = setOf("camera_capture", "write_lulu_journal"),
        )

        assertTrue(plan.any { it.toolName == "camera_capture" && !it.autoExecutable })
        assertTrue(plan.any { it.toolName == "write_lulu_journal" && !it.autoExecutable })
    }
}
