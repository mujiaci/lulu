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

        assertTrue(plan.any { it.toolName == "get_gadgetbridge_data" })
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
}
