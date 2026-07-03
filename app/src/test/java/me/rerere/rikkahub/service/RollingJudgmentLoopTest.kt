package me.rerere.rikkahub.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RollingJudgmentLoopTest {
    @Test
    fun `health event uses dense semantic cadence`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "露露",
            userText = "我现在肚子好难受",
            assistantText = "先坐下，我有点担心你。",
            nowMillis = NOW,
        )

        assertEquals(LivingIntentKind.HEALTH_SAFETY, intent.kind)
        assertEquals(listOf(5L, 10L, 20L, 40L, 90L), intent.evaluationCadence.delaysMinutes)
        assertEquals(NOW + 5 * MINUTE, intent.nextEvaluateAt)
        assertTrue(intent.hypotheses.contains("用户身体不舒服，可能需要安全确认"))
        assertTrue(intent.allowedActions.contains(LivingAction.TOOL_CHECK))
    }

    @Test
    fun `ordinary silence uses non random rolling cadence`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "露露",
            userText = "我先忙一下",
            assistantText = "好，我在这里。",
            nowMillis = NOW,
        )

        assertEquals(LivingIntentKind.ORDINARY_SILENCE, intent.kind)
        assertEquals(listOf(10L, 25L, 60L, 120L), intent.evaluationCadence.delaysMinutes)
        assertEquals("用户可能在忙", intent.hypotheses.first())
    }

    @Test
    fun `study event protects focus with slower cadence`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "露露",
            userText = "我要去学习专业课",
            assistantText = "嗯，我帮你守着节奏。",
            nowMillis = NOW,
        )

        assertEquals(LivingIntentKind.STUDY_FOCUS, intent.kind)
        assertEquals(listOf(30L, 60L, 90L), intent.evaluationCadence.delaysMinutes)
        assertTrue(intent.allowedActions.contains(LivingAction.MEMORY_UPDATE))
    }

    @Test
    fun `deadline event schedules checks before due time`() {
        val dueAt = NOW + 6 * 60 * MINUTE
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "露露",
            userText = "这个任务晚上 6 点前要完成",
            assistantText = "我会帮你盯进度。",
            nowMillis = NOW,
            deadlineAtMillis = dueAt,
        )

        assertEquals(LivingIntentKind.DEADLINE, intent.kind)
        assertEquals(listOf(180L, 300L, 330L, 350L, 360L), intent.evaluationCadence.delaysMinutes)
        assertEquals(NOW + 180 * MINUTE, intent.nextEvaluateAt)
    }

    @Test
    fun `wake up event schedules before and after target`() {
        val wakeAt = NOW + 60 * MINUTE
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "露露",
            userText = "我 9 点要起床",
            assistantText = "那我提前一点惦记你。",
            nowMillis = NOW,
            targetAtMillis = wakeAt,
        )

        assertEquals(LivingIntentKind.WAKE_UP, intent.kind)
        assertEquals(listOf(55L, 60L, 70L, 85L), intent.evaluationCadence.delaysMinutes)
        assertTrue(intent.allowedActions.contains(LivingAction.SET_ALARM))
    }

    @Test
    fun `spoken intent becomes restrained on later silent evaluation`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "露露",
            userText = "我先忙一下",
            assistantText = "好，我在这里。",
            nowMillis = NOW,
        ).copy(
            spokenCount = 1,
            lastSpokenAt = NOW + 10 * MINUTE,
            nextEvaluateAt = NOW + 25 * MINUTE,
        )

        val decision = RollingJudgmentLoop.evaluate(intent, nowMillis = NOW + 25 * MINUTE)

        assertEquals(LivingIntentStatus.RESTRAINED, decision.updatedIntent.status)
        assertTrue(decision.actions.contains(LivingAction.WAIT))
        assertTrue(decision.actions.contains(LivingAction.JOURNAL_WRITE))
        assertTrue(decision.actions.contains(LivingAction.SCHEDULE_NEXT_TICK))
        assertTrue(decision.updatedIntent.restraint > intent.restraint)
        assertEquals(1, decision.updatedIntent.silentEvaluationCount)
        assertTrue(decision.observation?.requestedTools?.contains("get_app_usage") == true)
        assertTrue(decision.judgmentTrace?.thought?.contains("BDI") == true)
        assertEquals(LivingJudgmentSource.MAIN_API_READY_CONTRACT, decision.judgmentTrace?.source)
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val MINUTE = 60_000L
    }
}
