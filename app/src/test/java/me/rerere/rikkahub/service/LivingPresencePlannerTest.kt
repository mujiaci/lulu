package me.rerere.rikkahub.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivingPresencePlannerTest {
    @Test
    fun `ordinary return does not create speakable rolling reminders`() {
        val now = 1_700_000_000_000L

        val plans = LivingPresencePlanner.planRollingJudgments(
            input = LivingPresenceInput(
                assistantName = "露露",
                userText = "我回来了",
                assistantText = "欢迎回来。",
                preferredToolNames = listOf("get_app_usage", "get_battery_info"),
            ),
            nowMillis = now,
        )

        assertEquals(emptyList<ProactiveReminderPlan>(), plans)
    }

    @Test
    fun `health concern creates dense checks with sensing tools and journal option`() {
        val now = 1_700_000_000_000L

        val plans = LivingPresencePlanner.planRollingJudgments(
            input = LivingPresenceInput(
                assistantName = "露露",
                userText = "我现在肚子好难受",
                assistantText = "先坐下，我有点担心你。",
                preferredToolNames = listOf("get_gadgetbridge_data", "get_battery_info", "get_app_usage"),
            ),
            nowMillis = now,
        )

        assertEquals(listOf(5L, 10L, 20L, 40L, 90L), plans.map { (it.triggerAtMillis - now) / 60_000L })
        assertEquals(ProactiveReminderKind.GENERAL, plans.first().kind)
        assertTrue(plans.first().preferredToolNames.contains("get_gadgetbridge_data"))
        assertTrue(plans.first().reason.contains("ReAct"))
        assertTrue(plans.first().actionHints.any { it.toolName == LivingPresenceAction.WRITE_JOURNAL.name })
        assertTrue(plans.first().actionHints.any { it.toolName == LivingPresenceAction.MEMORY_REFLECT.name })
        assertTrue(plans.first().actionHints.any { it.toolName == LivingPresenceAction.TOOL_CHECK.name })
    }

    @Test
    fun `study event creates low disturbance cadence`() {
        val now = 1_700_000_000_000L

        val plans = LivingPresencePlanner.planRollingJudgments(
            input = LivingPresenceInput(
                assistantName = "露露",
                userText = "我要开始学习专业课了",
                assistantText = "我帮你守住节奏。",
            ),
            nowMillis = now,
        )

        assertEquals(listOf(30L, 60L, 90L), plans.map { (it.triggerAtMillis - now) / 60_000L })
        assertEquals(ProactiveReminderKind.STUDY, plans.first().kind)
    }
}
