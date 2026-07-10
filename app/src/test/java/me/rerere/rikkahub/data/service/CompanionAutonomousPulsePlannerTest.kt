package me.rerere.rikkahub.data.service

import me.rerere.rikkahub.data.companion.CompanionConcern
import me.rerere.rikkahub.data.companion.CompanionRelationshipState
import me.rerere.rikkahub.data.companion.CompanionSnapshot
import me.rerere.rikkahub.data.datastore.ProactiveMessageSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionAutonomousPulsePlannerTest {
    private val setting = ProactiveMessageSetting(
        enabled = true,
        minIntervalMinutes = 30,
        maxIntervalMinutes = 90,
    )

    @Test
    fun `active companion work makes the next perception sooner`() {
        val plan = CompanionAutonomousPulsePlanner.planNext(
            input = CompanionAutonomousPulseInput(
                setting = setting,
                snapshot = CompanionSnapshot.empty("assistant-a").copy(
                    concerns = listOf(
                        CompanionConcern(
                            assistantId = "assistant-a",
                            subjectKey = "health:headache",
                            event = "用户说头疼",
                            goal = "稍后重新确认状态",
                            nextPerceptionAt = 1_699_999_900_000L,
                        ),
                    ),
                ),
                minutesSinceLastChat = 50,
            ),
        )

        assertEquals(18, plan.delayMinutes)
        assertTrue(plan.reason.contains("active_work"))
    }

    @Test
    fun `concern without a perception time does not cause repeated polling`() {
        val plan = CompanionAutonomousPulsePlanner.planNext(
            input = CompanionAutonomousPulseInput(
                setting = setting,
                snapshot = CompanionSnapshot.empty("assistant-a").copy(
                    concerns = listOf(
                        CompanionConcern(
                            assistantId = "assistant-a",
                            subjectKey = "relationship:general",
                            event = "记得用户最近压力很大",
                            goal = "保持关心但不机械轮询",
                            nextPerceptionAt = null,
                        ),
                    ),
                ),
                minutesSinceLastChat = 30,
                nowMillis = 1_700_000_000_000L,
            ),
        )

        assertEquals(60, plan.delayMinutes)
        assertTrue(plan.reason.contains("active=0"))
    }

    @Test
    fun `long silence schedules an earlier perception without assuming attachment`() {
        val plan = CompanionAutonomousPulsePlanner.planNext(
            input = CompanionAutonomousPulseInput(
                setting = setting,
                snapshot = CompanionSnapshot.empty("assistant-a"),
                minutesSinceLastChat = 160,
            ),
        )

        assertEquals(12, plan.delayMinutes)
        assertTrue(plan.reason.contains("long_silence"))
        assertTrue(plan.reason.contains("active=0"))
    }

    @Test
    fun `future concern does not force repeated early polling`() {
        val plan = CompanionAutonomousPulsePlanner.planNext(
            input = CompanionAutonomousPulseInput(
                setting = setting,
                snapshot = CompanionSnapshot.empty("assistant-a").copy(
                    concerns = listOf(
                        CompanionConcern(
                            assistantId = "assistant-a",
                            subjectKey = "exam:result",
                            event = "等待结果",
                            goal = "结果公布后再确认",
                            nextPerceptionAt = 1_700_003_600_000L,
                        ),
                    ),
                ),
                minutesSinceLastChat = 30,
                nowMillis = 1_700_000_000_000L,
            ),
        )

        assertEquals(60, plan.delayMinutes)
        assertTrue(plan.reason.contains("active=0"))
    }

    @Test
    fun `unresolved relationship tension slows the background pulse`() {
        val plan = CompanionAutonomousPulsePlanner.planNext(
            input = CompanionAutonomousPulseInput(
                setting = setting,
                snapshot = CompanionSnapshot.empty("assistant-a").copy(
                    relationship = CompanionRelationshipState(unresolvedTension = 0.8f),
                ),
                minutesSinceLastChat = 160,
            ),
        )

        assertEquals(90, plan.delayMinutes)
        assertTrue(plan.reason.contains("relationship_tension"))
    }

    @Test
    fun `targeted trigger keeps normal background pulse away`() {
        val plan = CompanionAutonomousPulsePlanner.planNext(
            input = CompanionAutonomousPulseInput(
                setting = setting,
                snapshot = CompanionSnapshot.empty("assistant-a"),
                minutesSinceLastChat = 40,
                activeTargetedTriggerMillis = 1_700_000_000_000L,
                nowMillis = 1_699_996_760_000L,
            ),
        )

        assertEquals(84, plan.delayMinutes)
        assertTrue(plan.reason.contains("targeted_active"))
    }

    @Test
    fun `far targeted trigger is not truncated by normal maximum interval`() {
        val nowMillis = 1_700_000_000_000L
        val plan = CompanionAutonomousPulsePlanner.planNext(
            input = CompanionAutonomousPulseInput(
                setting = setting,
                snapshot = CompanionSnapshot.empty("assistant-a"),
                minutesSinceLastChat = 40,
                activeTargetedTriggerMillis = nowMillis + 6 * 60 * 60_000L,
                nowMillis = nowMillis,
            ),
        )

        assertEquals(390, plan.delayMinutes)
        assertTrue(plan.reason.contains("targeted_active"))
    }
}
