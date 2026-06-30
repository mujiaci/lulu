package me.rerere.rikkahub.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ProactiveReminderPlannerTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDateTime.of(2026, 6, 30, 22, 30)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    @Test
    fun `sleep mention schedules a near future care reminder with sensing tools`() {
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
        assertTrue(plan.preferredToolNames.contains("get_gadgetbridge_data"))
        assertTrue(plan.preferredToolNames.contains("get_app_usage"))
        assertTrue(plan.actionHints.any { it.toolName == "write_lulu_journal" && it.autoExecutable })
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
    fun `explicit hour reminder can schedule class follow up with location context`() {
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
        assertEquals(8, LocalDateTime.ofInstant(Instant.ofEpochMilli(plan.triggerAtMillis), zone).hour)
        assertTrue(plan.preferredToolNames.contains("get_location"))
        assertTrue(plan.preferredToolNames.contains("get_app_usage"))
        assertTrue(plan.actionHints.any { it.toolName == "calendar_tool" })
        assertTrue(plan.actionHints.any { it.toolName == "set_alarm" })
    }

    @Test
    fun `general reminder carries journal hint without unsafe tools`() {
        val plan = ProactiveReminderPlanner.plan(
            userText = "十五分钟后提醒我把今天这件事记到日志里",
            assistantText = "嗯，我等会儿提醒你。",
            nowMillis = now,
            zoneId = zone,
        )

        assertNotNull(plan)
        assertEquals(ProactiveReminderKind.GENERAL, plan!!.kind)
        assertTrue(plan.actionHints.any { it.toolName == "write_lulu_journal" && it.autoExecutable })
        assertFalse(plan.actionHints.any { it.toolName == "camera_capture" && it.autoExecutable })
        assertFalse(plan.actionHints.any { it.toolName == "read_sms" && it.autoExecutable })
    }

    @Test
    fun `meal mention schedules a short care follow up`() {
        val plan = ProactiveReminderPlanner.plan(
            userText = "我还没吃饭，等会儿再弄点东西吃",
            assistantText = "那我一会儿来盯你吃饭。",
            nowMillis = now,
            zoneId = zone,
        )

        assertNotNull(plan)
        assertEquals(ProactiveReminderKind.MEAL, plan!!.kind)
        assertEquals(20L, (plan.triggerAtMillis - now) / 60_000L)
        assertTrue(plan.preferredToolNames.contains("get_app_usage"))
        assertTrue(plan.preferredToolNames.contains("get_battery_info"))
    }

    @Test
    fun `study mention schedules a later focus check`() {
        val plan = ProactiveReminderPlanner.plan(
            userText = "我去写作业了，先不聊啦",
            assistantText = "好，我晚点轻轻看你还在不在状态里。",
            nowMillis = now,
            zoneId = zone,
        )

        assertNotNull(plan)
        assertEquals(ProactiveReminderKind.STUDY, plan!!.kind)
        assertEquals(45L, (plan.triggerAtMillis - now) / 60_000L)
        assertTrue(plan.preferredToolNames.contains("get_app_usage"))
    }
}
