package me.rerere.rikkahub.service

import me.rerere.rikkahub.data.model.LuluEnergy
import me.rerere.rikkahub.data.model.LuluMood
import me.rerere.rikkahub.data.model.LuluState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class LuluIntentPlannerTest {
    private val assistantId = Uuid.parse("11111111-2222-3333-4444-555555555555")

    @Test
    fun `study goodbye becomes delayed autonomous check instead of immediate message`() {
        val plan = LuluIntentPlanner.plan(
            input = LuluIntentInput(
                assistantName = "露露",
                state = LuluState(assistantId = assistantId, mood = LuluMood.CALM),
                userText = "我去写作业了，先不聊啦",
                assistantText = "好，我晚点轻轻看你还在不在状态里。",
                minutesSinceLastChat = 0,
                availableToolNames = setOf("get_app_usage", "control_music", "get_battery_info"),
            )
        )

        assertEquals(LuluIntent.STAY_NEAR, plan.intent)
        assertFalse(plan.shouldMessageNow)
        assertEquals(45, plan.delayMinutes)
        assertTrue(plan.toolNames.contains("get_app_usage"))
        assertTrue(plan.toolNames.contains("control_music"))
        assertTrue(plan.reason.contains("写作业"))
    }

    @Test
    fun `long silence with warm state can become immediate miss-you message`() {
        val plan = LuluIntentPlanner.plan(
            input = LuluIntentInput(
                assistantName = "露露",
                state = LuluState(assistantId = assistantId, mood = LuluMood.HAPPY, energy = LuluEnergy.HIGH),
                userText = "",
                assistantText = "刚才聊得很开心。",
                minutesSinceLastChat = 180,
                availableToolNames = setOf("get_battery_info", "get_app_usage", "camera_capture"),
            )
        )

        assertEquals(LuluIntent.REACH_OUT, plan.intent)
        assertTrue(plan.shouldMessageNow)
        assertEquals(null, plan.delayMinutes)
        assertTrue(plan.toolNames.contains("get_battery_info"))
        assertTrue(plan.reason.contains("很久"))
    }

    @Test
    fun `explicit sleep promise keeps a delayed care plan`() {
        val plan = LuluIntentPlanner.plan(
            input = LuluIntentInput(
                assistantName = "露露",
                state = LuluState(assistantId = assistantId, mood = LuluMood.SOFT, energy = LuluEnergy.SLEEPY),
                userText = "我再刷十分钟就睡",
                assistantText = "那我十分钟后叫你。",
                minutesSinceLastChat = 0,
                availableToolNames = setOf("get_gadgetbridge_data", "get_app_usage", "get_battery_info"),
            )
        )

        assertEquals(LuluIntent.CARE_REMINDER, plan.intent)
        assertFalse(plan.shouldMessageNow)
        assertEquals(10, plan.delayMinutes)
        assertTrue(plan.toolNames.contains("get_gadgetbridge_data"))
    }

    @Test
    fun `immediate reach out intent becomes near future proactive plan`() {
        val now = 1_700_000_000_000L
        val intentPlan = LuluIntentPlan(
            intent = LuluIntent.REACH_OUT,
            shouldMessageNow = true,
            delayMinutes = null,
            toolNames = listOf("get_battery_info", "get_app_usage"),
            reason = "露露想自然地找用户说句话。",
            tone = "自然、想念、不要像提醒",
        )

        val plan = intentPlan.toProactiveReminderPlan(
            userText = "",
            nowMillis = now,
        )

        assertEquals(ProactiveReminderKind.GENERAL, plan!!.kind)
        assertEquals(now + 60_000L, plan.triggerAtMillis)
        assertTrue(plan.preferredToolNames.contains("get_app_usage"))
        assertTrue(plan.reason.contains("自然"))
    }

    @Test
    fun `model planner parses and clamps autonomous json plan`() {
        val plan = LuluIntentModelPlanner.parsePlan(
            rawText = """
                ```json
                {
                  "intent": "check_context",
                  "shouldMessageNow": false,
                  "delayMinutes": 99999,
                  "toolNames": ["camera_capture", "unknown_tool", "get_app_usage", "camera_capture"],
                  "reason": "露露想先看一下用户现在的状态，再决定怎么开口。",
                  "tone": "轻一点"
                }
                ```
            """.trimIndent(),
            availableToolNames = setOf("camera_capture", "get_app_usage"),
        )

        assertEquals(LuluIntent.CHECK_CONTEXT, plan!!.intent)
        assertFalse(plan.shouldMessageNow)
        assertEquals(24 * 60, plan.delayMinutes)
        assertEquals(listOf("camera_capture", "get_app_usage"), plan.toolNames)
        assertTrue(plan.reason.contains("状态"))
    }

    @Test
    fun `model planner ignores invalid json plan`() {
        val plan = LuluIntentModelPlanner.parsePlan(
            rawText = "我觉得可以稍后看看，但这里不是 JSON",
            availableToolNames = setOf("get_app_usage"),
        )

        assertEquals(null, plan)
    }
}
