package me.rerere.rikkahub.data.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProactiveMessageContextTest {
    @Test
    fun `sleep targeted context asks for sleep sensing first`() {
        val context = buildTargetedProactiveSensingInstruction(
            targetedKind = "sleep",
            targetedReason = "刚才聊到了睡觉/休息，露露决定到点来催你睡觉。",
        )

        assertTrue(context.contains("睡眠"))
        assertTrue(context.contains("应用使用"))
        assertTrue(context.contains("电量"))
        assertFalse(context.contains("短信正文"))
        assertTrue(context.contains("摄像头"))
        assertFalse(context.contains("不要打开摄像头"))
    }

    @Test
    fun `schedule targeted context asks for location and calendar first`() {
        val context = buildTargetedProactiveSensingInstruction(
            targetedKind = "schedule",
            targetedReason = "刚才聊到了课程/日程，露露决定到点来确认你的状态。",
        )

        assertTrue(context.contains("位置"))
        assertTrue(context.contains("日历"))
        assertTrue(context.contains("应用使用"))
    }

    @Test
    fun `meal targeted context asks for eating and app usage cues`() {
        val context = buildTargetedProactiveSensingInstruction(
            targetedKind = "meal",
            targetedReason = "刚才聊到了吃饭，露露决定稍后来确认用户有没有吃。",
        )

        assertTrue(context.contains("吃饭"))
        assertTrue(context.contains("应用使用"))
        assertTrue(context.contains("电量"))
    }

    @Test
    fun `study targeted context asks for focus cues`() {
        val context = buildTargetedProactiveSensingInstruction(
            targetedKind = "study",
            targetedReason = "刚才聊到了学习，露露决定晚点来轻轻确认状态。",
        )

        assertTrue(context.contains("学习"))
        assertTrue(context.contains("应用使用"))
        assertTrue(context.contains("音乐"))
    }
}
