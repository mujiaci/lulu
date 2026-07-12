package me.rerere.rikkahub.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class CompanionTurnReminderPlannerTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `deterministic clock fallback remains active when model plan exists`() {
        val now = LocalDateTime.of(2026, 7, 11, 2, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val plans = buildCompanionTurnReminderPlans(
            plan = CompanionChatTurnPlan(
                innerThought = "我得把她明早叫起来。",
            ),
            userText = "我明天早上七点半一定要起床，叫醒我",
            assistantText = "我会叫你。",
            nowMillis = now,
            zoneId = zone,
        )

        assertTrue(plans.isNotEmpty())
        val wakePlan = plans.single { it.kind == ProactiveReminderKind.WAKE }
        assertEquals(
            LocalDateTime.of(2026, 7, 12, 7, 30),
            LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(wakePlan.triggerAtMillis),
                zone,
            ),
        )
    }

    @Test
    fun `model follow up and deterministic wake fallback are merged without duplicates`() {
        val now = LocalDateTime.of(2026, 7, 11, 2, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val plans = buildCompanionTurnReminderPlans(
            plan = CompanionChatTurnPlan(
                followUps = listOf(
                    CompanionChatFollowUpPlan(
                        delayMinutes = 15,
                        reason = "确认用户有没有停止刷手机并准备睡觉",
                        kind = "sleep",
                    ),
                ),
            ),
            userText = "我明天早上七点半一定要起床，叫醒我",
            assistantText = "现在先睡，我会继续盯着。",
            nowMillis = now,
            zoneId = zone,
        )

        assertTrue(plans.any { it.kind == ProactiveReminderKind.SLEEP })
        assertEquals(1, plans.count { it.kind == ProactiveReminderKind.WAKE })
    }

    @Test
    fun `explicit wake clock wins over rounded model delay`() {
        val now = LocalDateTime.of(2026, 7, 11, 22, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val plans = buildCompanionTurnReminderPlans(
            plan = CompanionChatTurnPlan(
                followUps = listOf(
                    CompanionChatFollowUpPlan(
                        // 08:30 tomorrow is 630 minutes away; this simulates
                        // a model rounding it down to 08:00 (600 minutes).
                        delayMinutes = 600,
                        reason = "明早确认你有没有起床",
                        kind = "wake",
                    ),
                ),
            ),
            userText = "明天早上八点半叫醒我",
            assistantText = "好，我记住了。",
            nowMillis = now,
            zoneId = zone,
        )

        val wakePlans = plans.filter { it.kind == ProactiveReminderKind.WAKE }
        assertEquals(1, wakePlans.size)
        assertEquals(
            LocalDateTime.of(2026, 7, 12, 8, 30),
            LocalDateTime.ofInstant(Instant.ofEpochMilli(wakePlans.single().triggerAtMillis), zone),
        )
    }

    @Test
    fun `late night wake request also creates bedtime supervision`() {
        val now = LocalDateTime.of(2026, 7, 11, 2, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val plans = buildCompanionTurnReminderPlans(
            plan = CompanionChatTurnPlan(),
            userText = "我明天早上七点要起床，你一定叫醒我",
            assistantText = "现在先睡。",
            nowMillis = now,
            zoneId = zone,
        )

        val sleepPlan = plans.single { it.kind == ProactiveReminderKind.SLEEP }
        assertEquals(15L, (sleepPlan.triggerAtMillis - now) / 60_000L)
        assertEquals(1, plans.count { it.kind == ProactiveReminderKind.WAKE })
    }
}
