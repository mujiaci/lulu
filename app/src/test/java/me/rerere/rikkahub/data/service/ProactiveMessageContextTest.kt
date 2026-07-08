package me.rerere.rikkahub.data.service

import me.rerere.rikkahub.data.cihai.CihaiEntry
import me.rerere.rikkahub.data.cihai.CihaiEntryKind
import me.rerere.rikkahub.data.model.LuluMode
import me.rerere.rikkahub.data.model.LuluState
import me.rerere.rikkahub.service.LivingAction
import me.rerere.rikkahub.service.LivingIntentKind
import me.rerere.rikkahub.service.LivingJudgmentSource
import me.rerere.rikkahub.service.LivingJudgmentTrace
import me.rerere.rikkahub.service.LivingPresenceConsolidationHint
import me.rerere.rikkahub.service.LuluIntent
import me.rerere.rikkahub.service.LuluIntentPlan
import me.rerere.rikkahub.service.RollingJudgmentLoop
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

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

    @Test
    fun `silent living judgment updates unspoken status bar voice`() {
        val assistantId = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val intent = RollingJudgmentLoop.createIntent(
            assistantId = assistantId.toString(),
            assistantName = "露露",
            userText = "我先去学习，晚点回来",
            assistantText = "好，我不吵你。",
            nowMillis = NOW,
        )
        val trace = LivingJudgmentTrace(
            source = LivingJudgmentSource.MAIN_API_READY_CONTRACT,
            belief = "用户可能正在学习，不该被我打断。",
            desire = "安静陪着，但继续守住这件事。",
            intention = "不发消息，先记住这个判断。",
            thought = "我先不去吵你，但我会把这件事放在心里，等你回来时接住你。",
            action = "PASS, WRITE_DIARY, SCHEDULE_NEXT_PERCEPTION",
            observation = "用户说要学习。",
            decision = "静默记录并等待下一轮。",
            createdAt = NOW,
        )
        val decision = RollingJudgmentLoop.evaluate(
            intent = intent,
            nowMillis = NOW + 10 * MINUTE,
            externalJudgmentTrace = trace,
        )

        val state = buildSilentLivingPresenceState(
            assistantId = assistantId,
            previous = LuluState(assistantId = assistantId, innerVoice = "旧心声"),
            assistantName = "露露",
            decision = decision,
            nowMillis = NOW + 10 * MINUTE,
        )

        assertEquals("克制着没开口", state.statusText)
        assertEquals("我先不去吵你，但我会把这件事放在心里，等你回来时接住你。", state.innerVoice)
        assertEquals(LuluMode.THINKING, state.mode)
        assertTrue(state.selfScene.contains("没有发消息"))
        assertTrue(state.reason.contains("静默判断"))
        assertTrue(state.perceptionSummary.contains("用户说要学习"))
    }

    @Test
    fun `default silent presence hints do not request formal journal writing`() {
        val hints = defaultSilentPresenceActionHints()

        assertFalse(hints.contains(LivingPresenceConsolidationHint.WRITE_JOURNAL.name))
        assertTrue(hints.contains(LivingPresenceConsolidationHint.READ_BOOK.name))
        assertTrue(hints.contains(LivingPresenceConsolidationHint.MEMORY_REFLECT.name))
    }

    @Test
    fun `recent diary context keeps latest three formal diary or inner journal entries`() {
        val entries = listOf(
            cihaiEntry("old-diary", CihaiEntryKind.DIARY, createdAt = 1),
            cihaiEntry("reading", CihaiEntryKind.READING_NOTE, createdAt = 2),
            cihaiEntry("inner-1", CihaiEntryKind.INNER_JOURNAL, createdAt = 3),
            cihaiEntry("other-assistant", CihaiEntryKind.DIARY, assistantId = "other", createdAt = 4),
            cihaiEntry("inner-2", CihaiEntryKind.INNER_JOURNAL, createdAt = 5),
            cihaiEntry("new-diary", CihaiEntryKind.DIARY, createdAt = 6),
        )

        val recent = recentFormalDiaryOrInnerJournalEntries(entries, assistantId = "lulu")

        assertEquals(
            listOf("inner-1", "inner-2", "new-diary"),
            recent.map { it.title },
        )
        assertTrue(recent.all { it.kind == CihaiEntryKind.DIARY || it.kind == CihaiEntryKind.INNER_JOURNAL })
    }

    @Test
    fun `technical fallback trace is not shown as status bar inner voice`() {
        val assistantId = Uuid.parse("22222222-2222-2222-2222-222222222222")
        val intent = RollingJudgmentLoop.createIntent(
            assistantId = assistantId.toString(),
            assistantName = "露露",
            userText = "我先去学习，晚点回来",
            assistantText = "好，我不吵你。",
            nowMillis = NOW,
        )
        val decision = RollingJudgmentLoop.evaluate(
            intent = intent,
            nowMillis = NOW + 10 * MINUTE,
        )

        val state = buildSilentLivingPresenceState(
            assistantId = assistantId,
            previous = LuluState(assistantId = assistantId),
            assistantName = "露露",
            decision = decision,
            nowMillis = NOW + 10 * MINUTE,
        )

        assertTrue(state.innerVoice.startsWith("我"))
        assertFalse(state.innerVoice.contains("Seven-layer trace"))
        assertFalse(state.innerVoice.contains("Perception="))
        assertFalse(state.innerVoice.contains("requested_tools="))
    }

    @Test
    fun `autonomous api plan updates status bar with model inner thought when not speaking`() {
        val assistantId = Uuid.parse("33333333-3333-3333-3333-333333333333")
        val plan = LuluIntentPlan(
            intent = LuluIntent.DO_NOT_DISTURB,
            shouldMessageNow = false,
            delayMinutes = null,
            toolNames = emptyList(),
            reason = "副 API 判断现在不适合打扰用户。",
            tone = "安静",
            innerThought = "我先不把想靠近说出口，等你自己的节奏回来。",
            fromModel = true,
        )

        val state = buildAutonomousPlanPresenceState(
            assistantId = assistantId,
            previous = LuluState(assistantId = assistantId, innerVoice = "旧心声"),
            assistantName = "露露",
            plan = plan,
            nowMillis = NOW,
        )

        assertEquals("安静判断中", state.statusText)
        assertEquals("我先不把想靠近说出口，等你自己的节奏回来。", state.innerVoice)
        assertEquals(LuluMode.THINKING, state.mode)
        assertTrue(state.reason.contains("副 API"))
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val MINUTE = 60_000L

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
