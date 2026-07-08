package me.rerere.rikkahub.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        assertEquals(listOf(5L), plans.map { (it.triggerAtMillis - now) / 60_000L })
        assertEquals(ProactiveReminderKind.GENERAL, plans.first().kind)
        assertTrue(plans.first().preferredToolNames.contains("get_gadgetbridge_data"))
        assertTrue(plans.first().reason.contains("活人感动态感知"))
        assertTrue(plans.first().reason.contains("Appraisal="))
        assertTrue(plans.first().reason.contains("感知层必须先装入角色人设"))
        assertTrue(plans.first().reason.contains("下次什么时候感知"))
        assertFalse(plans.first().actionHints.any { it.toolName == LivingPresenceConsolidationHint.WRITE_JOURNAL.name })
        assertTrue(plans.first().actionHints.any { it.toolName == LivingPresenceConsolidationHint.MEMORY_REFLECT.name })
        assertTrue(plans.first().actionHints.any { it.toolName == LivingPresenceAction.TOOL_USE.name })
    }

    @Test
    fun `action pool stays separate from expression affordance pool`() {
        val actionNames = LivingPresenceAction.entries.map { it.name }.toSet()
        val expressionNames = LivingPresenceExpressionAffordance.entries.map { it.name }.toSet()

        assertEquals(
            setOf(
                "MESSAGE",
                "WAIT",
                "TOOL_USE",
                "SET_ALARM",
                "WRITE_DIARY",
                "SCHEDULE_NEXT_PERCEPTION",
                "READ",
                "ASK_USER",
                "PASS",
            ),
            actionNames,
        )
        assertEquals(
            setOf(
                "TEXT",
                "KAOMOJI",
                "STICKER",
                "VOICE",
                "STATUS_BAR",
                "LIGHT_REMINDER",
                "LONG_EXPLANATION",
                "SILENT_RECORD",
            ),
            expressionNames,
        )
        assertFalse(actionNames.any { it in expressionNames })
    }

    @Test
    fun `study event creates bootstrap judgment instead of fixed focus cadence`() {
        val now = 1_700_000_000_000L

        val plans = LivingPresencePlanner.planRollingJudgments(
            input = LivingPresenceInput(
                assistantName = "露露",
                userText = "我要开始学习专业课了",
                assistantText = "我帮你守住节奏。",
            ),
            nowMillis = now,
        )

        assertEquals(listOf(1L), plans.map { (it.triggerAtMillis - now) / 60_000L })
        assertEquals(ProactiveReminderKind.STUDY, plans.first().kind)
        assertTrue(plans.first().reason.contains("下一次什么时候感知，都必须根据本轮感知和人设重新决定"))
    }
}
