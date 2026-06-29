package me.rerere.rikkahub.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ProactiveReminderPlannerTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDateTime.of(2026, 6, 30, 22, 30)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    @Test
    fun `sleep mention schedules a near future care reminder`() {
        val plan = ProactiveReminderPlanner.plan(
            userText = "我等下准备睡觉了，先刷会手机",
            assistantText = "那我一会儿盯你睡觉。",
            nowMillis = now,
            zoneId = zone,
        )

        assertNotNull(plan)
        val delayMinutes = (plan!!.triggerAtMillis - now) / 60_000L
        assertTrue(delayMinutes in 20..40)
        assertEquals(ProactiveReminderKind.SLEEP, plan.kind)
    }

    @Test
    fun `explicit minute reminder uses requested delay`() {
        val plan = ProactiveReminderPlanner.plan(
            userText = "十分钟后提醒我睡觉",
            assistantText = "好，我十分钟后叫你。",
            nowMillis = now,
            zoneId = zone,
        )

        assertNotNull(plan)
        assertEquals(10L, (plan!!.triggerAtMillis - now) / 60_000L)
    }

    @Test
    fun `explicit hour reminder can schedule class follow up`() {
        val plan = ProactiveReminderPlanner.plan(
            userText = "我八点有课，到时候看看我起没起",
            assistantText = "我会记着。",
            nowMillis = LocalDateTime.of(2026, 6, 30, 7, 20)
                .atZone(zone)
                .toInstant()
                .toEpochMilli(),
            zoneId = zone,
        )

        assertNotNull(plan)
        assertEquals(ProactiveReminderKind.SCHEDULE, plan!!.kind)
        assertEquals(8, LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(plan.triggerAtMillis), zone).hour)
    }
}
