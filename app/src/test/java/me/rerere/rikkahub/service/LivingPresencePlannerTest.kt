package me.rerere.rikkahub.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivingPresencePlannerTest {
    @Test
    fun `ordinary silence creates multiple rolling judgement checkpoints`() {
        val now = 1_700_000_000_000L

        val plans = LivingPresencePlanner.planRollingJudgments(
            input = LivingPresenceInput(
                assistantName = "露露",
                userText = "我先去处理点事",
                assistantText = "好，我在这里等你。",
                preferredToolNames = listOf("get_app_usage", "get_battery_info"),
            ),
            nowMillis = now,
        )

        assertEquals(listOf(10L, 25L, 60L, 120L), plans.map { (it.triggerAtMillis - now) / 60_000L })
        assertTrue(plans.all { it.reason.contains("滚动判断") })
        assertTrue(plans.any { it.reason.contains("写日志") })
        assertTrue(plans.any { it.reason.contains("阅读") })
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

        assertEquals(listOf(8L, 20L, 45L, 90L, 150L), plans.map { (it.triggerAtMillis - now) / 60_000L })
        assertEquals(ProactiveReminderKind.GENERAL, plans.first().kind)
        assertTrue(plans.first().preferredToolNames.contains("get_gadgetbridge_data"))
        assertTrue(plans.first().reason.contains("ReAct"))
        assertTrue(plans.first().actionHints.any { it.toolName == LivingPresenceAction.WRITE_JOURNAL.name })
    }
    @Test
    fun `rolling judgement reason names full loop and memory reflection option`() {
        val plans = LivingPresencePlanner.planRollingJudgments(
            input = LivingPresenceInput(
                assistantName = "Lulu",
                userText = "I need to be busy for a while",
                assistantText = "I will stay here and check later.",
            ),
            nowMillis = 1_700_000_000_000L,
        )

        assertTrue(plans.first().reason.contains("RollingJudgmentLoop"))
        assertTrue(plans.first().reason.contains("graph memory"))
        assertTrue(plans.first().actionHints.any { it.toolName == LivingPresenceAction.MEMORY_REFLECT.name })
    }
}
