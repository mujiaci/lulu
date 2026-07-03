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

    @Test
    fun `external observation is used before structured judgment`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "露露",
            userText = "我要去学习专业课",
            assistantText = "嗯，我帮你守着节奏。",
            nowMillis = NOW,
        )
        val observation = LivingObservation(
            summary = "Runtime observation: available_tools=get_app_usage,today_study_plan; study_tasks_open=3",
            requestedTools = listOf("get_app_usage", "today_study_plan"),
            signals = listOf("available_tool=get_app_usage", "available_tool=today_study_plan", "study_tasks_open=3"),
            createdAt = NOW + 30 * MINUTE,
        )

        val decision = RollingJudgmentLoop.evaluate(
            intent = intent,
            nowMillis = NOW + 30 * MINUTE,
            externalObservation = observation,
        )

        assertEquals(observation.summary, decision.observation?.summary)
        assertTrue(decision.updatedIntent.lastObservation?.signals?.contains("study_tasks_open=3") == true)
        assertTrue(decision.judgmentTrace?.observation?.contains("available_tools=get_app_usage") == true)
        assertTrue(decision.thought.contains("Runtime observation"))
    }

    @Test
    fun `main api structured trace overrides rule fallback trace`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "露露",
            userText = "我先忙三个小时",
            assistantText = "好，我会自己判断什么时候靠近。",
            nowMillis = NOW,
        )
        val trace = LivingJudgmentTrace(
            source = LivingJudgmentSource.MAIN_API_READY_CONTRACT,
            belief = "She may be busy, not ignoring me.",
            desire = "Stay near without interrupting.",
            intention = "Wait and write journal first.",
            thought = "I checked the observation and chose restraint.",
            action = "WAIT, JOURNAL_WRITE, SCHEDULE_NEXT_TICK",
            observation = "Runtime observation is available.",
            decision = "Do not message now.",
            createdAt = NOW,
        )

        val decision = RollingJudgmentLoop.evaluate(
            intent = intent,
            nowMillis = NOW + 10 * MINUTE,
            externalJudgmentTrace = trace,
        )

        assertEquals(LivingJudgmentSource.MAIN_API_STRUCTURED_JUDGMENT, decision.judgmentTrace?.source)
        assertEquals("I checked the observation and chose restraint.", decision.judgmentTrace?.thought)
        assertEquals("Do not message now.", decision.updatedIntent.lastJudgmentTrace?.decision)
    }

    @Test
    fun `main api structured action can choose restraint from allowed action pool`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "露露",
            userText = "我现在肚子好痛",
            assistantText = "先坐下，我会确认你安全。",
            nowMillis = NOW,
        )
        val trace = LivingJudgmentTrace(
            source = LivingJudgmentSource.MAIN_API_READY_CONTRACT,
            belief = "She may be resting after saying her stomach hurts.",
            desire = "Care without making panic.",
            intention = "Check quietly first and write down the concern.",
            thought = "A message is not the only caring action.",
            action = "TOOL_CHECK, JOURNAL_WRITE, SCHEDULE_NEXT_TICK",
            observation = "Battery and app usage are available; health data is missing.",
            decision = "Do not send another message yet.",
            createdAt = NOW,
        )

        val decision = RollingJudgmentLoop.evaluate(
            intent = intent,
            nowMillis = NOW + 5 * MINUTE,
            externalJudgmentTrace = trace,
        )

        assertEquals(
            listOf(LivingAction.TOOL_CHECK, LivingAction.JOURNAL_WRITE, LivingAction.SCHEDULE_NEXT_TICK),
            decision.actions,
        )
        assertTrue(LivingAction.MESSAGE !in decision.actions)
        assertEquals("Do not send another message yet.", decision.judgmentTrace?.decision)
    }

    @Test
    fun `main api structured judgment controls next evaluation time`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "露露",
            userText = "我忙三个小时，可能不回你",
            assistantText = "好，我会自己判断什么时候再想这件事。",
            nowMillis = NOW,
        )
        val trace = LivingJudgmentTrace(
            source = LivingJudgmentSource.MAIN_API_READY_CONTRACT,
            belief = "She is probably busy for a long block.",
            desire = "Stay nearby without checking too soon.",
            intention = "Wait longer, then reassess.",
            thought = "Five minutes would be too clingy here.",
            action = "WAIT, JOURNAL_WRITE, SCHEDULE_NEXT_TICK",
            observation = "No risk signals.",
            decision = "Re-evaluate after a character-chosen interval.",
            nextEvaluateDelayMinutes = 37,
            createdAt = NOW,
        )

        val decision = RollingJudgmentLoop.evaluate(
            intent = intent,
            nowMillis = NOW + 10 * MINUTE,
            externalJudgmentTrace = trace,
        )

        assertEquals(NOW + 47 * MINUTE, decision.updatedIntent.nextEvaluateAt)
        assertEquals(37, decision.judgmentTrace?.nextEvaluateDelayMinutes)
    }

    @Test
    fun `emotion state accumulates across restrained silent evaluations`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "露露",
            userText = "我先忙一会儿",
            assistantText = "好，我在这里等你。",
            nowMillis = NOW,
        ).copy(
            spokenCount = 1,
            lastSpokenAt = NOW + 10 * MINUTE,
            silentEvaluationCount = 2,
            nextEvaluateAt = NOW + 60 * MINUTE,
        )

        val decision = RollingJudgmentLoop.evaluate(intent, nowMillis = NOW + 60 * MINUTE)

        assertTrue(decision.updatedIntent.emotion.disappointment > intent.emotion.disappointment)
        assertTrue(decision.updatedIntent.emotion.tension >= intent.emotion.tension)
        assertEquals(NOW + 60 * MINUTE, decision.updatedIntent.emotion.lastChangedAt)
        assertTrue(decision.updatedIntent.emotion.label.contains("忍住"))
    }

    @Test
    fun `emotion state decays across long quiet gaps before new deltas`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "露露",
            userText = "我先忙一下",
            assistantText = "好，我在这里等你。",
            nowMillis = NOW,
        ).copy(
            emotion = EmotionSnapshot(
                concern = 8,
                attachment = 8,
                restraint = 8,
                label = "很紧张",
                tension = 8,
                disappointment = 6,
                relief = 4,
                lastChangedAt = NOW,
            ),
            nextEvaluateAt = NOW + 180 * MINUTE,
        )

        val decision = RollingJudgmentLoop.evaluate(intent, nowMillis = NOW + 180 * MINUTE)

        assertTrue(decision.updatedIntent.emotion.tension < intent.emotion.tension)
        assertTrue(decision.updatedIntent.emotion.disappointment < intent.emotion.disappointment)
        assertTrue(decision.updatedIntent.emotion.relief < intent.emotion.relief)
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val MINUTE = 60_000L
    }
}
