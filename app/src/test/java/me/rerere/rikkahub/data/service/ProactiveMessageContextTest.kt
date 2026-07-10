package me.rerere.rikkahub.data.service

import me.rerere.rikkahub.data.cihai.CihaiEntry
import me.rerere.rikkahub.data.cihai.CihaiEntryKind
import me.rerere.rikkahub.data.companion.CompanionState
import me.rerere.rikkahub.service.CompanionIntent
import me.rerere.rikkahub.service.CompanionIntentDecision
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProactiveMessageContextTest {
    @Test
    fun `wake targeted context keeps sensing until the user is awake`() {
        val context = buildTargetedProactiveSensingInstruction(
            targetedKind = "wake",
            targetedReason = "用户要求被叫醒。",
        )

        assertTrue(context.contains("位置"))
        assertTrue(context.contains("天气"))
        assertTrue(context.contains("应用使用"))
        assertTrue(context.contains("继续叫醒"))
    }

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

    @Test
    fun `recent diary context keeps only formal diary entries`() {
        val entries = listOf(
            cihaiEntry("old-diary", CihaiEntryKind.DIARY, createdAt = 1),
            cihaiEntry("reading", CihaiEntryKind.READING_NOTE, createdAt = 2),
            cihaiEntry("inner-1", CihaiEntryKind.INNER_JOURNAL, createdAt = 3),
            cihaiEntry("other-assistant", CihaiEntryKind.DIARY, assistantId = "other", createdAt = 4),
            cihaiEntry("inner-2", CihaiEntryKind.INNER_JOURNAL, createdAt = 5),
            cihaiEntry("new-diary", CihaiEntryKind.DIARY, createdAt = 6),
        )

        val recent = recentFormalDiaryEntries(entries, assistantId = "lulu")

        assertEquals(
            listOf("old-diary", "new-diary"),
            recent.map { it.title },
        )
        assertTrue(recent.all { it.kind == CihaiEntryKind.DIARY })
    }

    @Test
    fun `autonomous api plan updates status bar with model inner thought when not speaking`() {
        val plan = CompanionIntentDecision(
            intent = CompanionIntent.WAIT,
            shouldMessageNow = false,
            delayMinutes = null,
            toolNames = emptyList(),
            reason = "副 API 判断现在不适合打扰用户。",
            tone = "安静",
            innerThought = "我先不把想靠近说出口，等你自己的节奏回来。",
            fromModel = true,
        )

        val state = buildAutonomousPlanPresenceState(
            previous = CompanionState(innerThought = "旧心声"),
            assistantName = "露露",
            plan = plan,
            nowMillis = NOW,
        )

        assertEquals("安静判断中", state.statusText)
        assertEquals("我先不把想靠近说出口，等你自己的节奏回来。", state.innerThought)
        assertEquals("waiting", state.activityMode)
        assertEquals("安静留意着现在的变化", state.mindState)
    }

    private companion object {
        const val NOW = 1_700_000_000_000L

        fun cihaiEntry(
            title: String,
            kind: CihaiEntryKind,
            assistantId: String = "lulu",
            createdAt: Long,
        ) = CihaiEntry(
            assistantId = assistantId,
            kind = kind,
            title = title,
            content = "content-$title",
            createdAt = createdAt,
        )
    }
}
