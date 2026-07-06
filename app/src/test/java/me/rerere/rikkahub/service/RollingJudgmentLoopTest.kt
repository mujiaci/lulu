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
        assertTrue(intent.candidateActions.contains(LivingAction.TOOL_USE))
        assertTrue(intent.candidateActions.contains(LivingAction.PASS))
        assertTrue(intent.candidateActions.contains(LivingAction.ASK_USER))
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
        assertTrue(intent.candidateActions.contains(LivingAction.MEMORY_UPDATE))
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
        assertTrue(intent.candidateActions.contains(LivingAction.SET_ALARM))
    }

    @Test
    fun `wake up observation carries temporal grounding before alarm action`() {
        val wakeAt = NOW + 60 * MINUTE
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "露露",
            userText = "我 9 点要起床",
            assistantText = "那我先校准时间，再帮你盯着。",
            nowMillis = NOW,
            targetAtMillis = wakeAt,
        )

        val decision = RollingJudgmentLoop.evaluate(
            intent = intent,
            nowMillis = NOW + 55 * MINUTE,
        )

        val observation = decision.observation
        assertTrue(observation?.requestedTools?.first() == "get_time_info")
        assertTrue(observation?.requestedTools?.contains("set_alarm") == true)
        assertTrue(observation?.signals?.contains("temporal_grounding_required=true") == true)
        assertTrue(observation?.signals?.contains("current_time_ms=${NOW + 55 * MINUTE}") == true)
        assertTrue(observation?.signals?.contains("target_time_ms=$wakeAt") == true)
        assertTrue(observation?.summary?.contains("Temporal Grounding") == true)
        assertTrue(observation?.summary?.contains("current_time_ms=${NOW + 55 * MINUTE}") == true)
        assertTrue(observation?.summary?.contains("target_time_ms=$wakeAt") == true)
    }

    @Test
    fun `structured judgment can keep any local action from capability pool`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "Lulu",
            userText = "I will be busy for a while.",
            assistantText = "Okay, I will decide by myself whether to act later.",
            nowMillis = NOW,
        )
        val trace = LivingJudgmentTrace(
            source = LivingJudgmentSource.MAIN_API_READY_CONTRACT,
            belief = "She may need a later reminder even though this started as ordinary silence.",
            desire = "Act like a local digital caretaker.",
            motiveText = "Act like a local digital caretaker.",
            intention = "Keep the reminder action available when deliberation wants it.",
            thought = "The system should not block a tool action just because the concern kind is ordinary silence.",
            action = "SET_ALARM, JOURNAL_WRITE, SCHEDULE_NEXT_TICK",
            observation = "The user mentioned being busy and may need a later reminder.",
            decision = "Keep the alarm action selected by deliberation.",
            createdAt = NOW,
        )

        val decision = RollingJudgmentLoop.evaluate(
            intent = intent,
            nowMillis = NOW + 10 * MINUTE,
            externalJudgmentTrace = trace,
        )

        assertTrue(intent.candidateActions.contains(LivingAction.SET_ALARM))
        assertTrue(decision.actions.contains(LivingAction.SET_ALARM))
        assertTrue(decision.actions.contains(LivingAction.JOURNAL_WRITE))
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
        assertTrue(decision.judgmentTrace?.thought?.contains("情境感知-意义评估-状态保持-审议决策-行为实现-人格表达-经验沉淀") == true)
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
    fun `decision exposes seven layer trace as structured fields`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "露露",
            userText = "我要去学习专业课",
            assistantText = "嗯，我帮你守着节奏。",
            nowMillis = NOW,
        )
        val observation = LivingObservation(
            summary = "Runtime observation: available_tools=get_app_usage,today_study_plan; study_tasks_open=3",
            requestedTools = listOf("get_app_usage", "today_study_plan"),
            signals = listOf("available_tool=get_app_usage", "study_tasks_open=3"),
            createdAt = NOW + 30 * MINUTE,
        )

        val decision = RollingJudgmentLoop.evaluate(
            intent = intent,
            nowMillis = NOW + 30 * MINUTE,
            externalObservation = observation,
        )

        val trace = decision.sevenLayerTrace
        assertEquals("情境感知-意义评估-状态保持-审议决策-行为实现-人格表达-经验沉淀", trace.architectureName)
        assertTrue(trace.perception.contains("Runtime observation"))
        assertTrue(trace.appraisal.contains(intent.appraisal.meaning))
        assertTrue(trace.state.contains(intent.belief))
        assertTrue(trace.state.contains(intent.traitMotive))
        assertTrue(trace.deliberation.contains("ReAct"))
        assertTrue(trace.actionPlanning.contains("TOOL_USE"))
        assertTrue(trace.expression.contains(intent.emotion.emotionLabel))
        assertTrue(trace.consolidation.contains(intent.consolidation.policyLearning))
    }

    @Test
    fun `rule fallback uses public pass action instead of legacy inner thought`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "Lulu",
            userText = "I may not reply for a while.",
            assistantText = "I will stay nearby.",
            nowMillis = NOW,
        )

        val decision = RollingJudgmentLoop.evaluate(
            intent = intent,
            nowMillis = NOW + 10 * MINUTE,
        )

        assertTrue(decision.actions.contains(LivingAction.PASS))
        assertTrue(LivingAction.INNER_THOUGHT !in decision.actions)
    }

    @Test
    fun `evaluation restores candidate action pool for old stored intents`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "Lulu",
            userText = "I may not reply for a while.",
            assistantText = "I will stay nearby.",
            nowMillis = NOW,
        ).copy(candidateActions = emptyList())

        val decision = RollingJudgmentLoop.evaluate(
            intent = intent,
            nowMillis = NOW + 10 * MINUTE,
        )

        assertTrue(decision.updatedIntent.candidateActions.contains(LivingAction.TOOL_USE))
        assertTrue(decision.updatedIntent.candidateActions.contains(LivingAction.PASS))
        assertTrue(decision.updatedIntent.candidateActions.contains(LivingAction.ASK_USER))
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
            motiveText = "Stay near without interrupting.",
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
    fun `main api structured action can choose restraint from action pool`() {
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
            motiveText = "Care without making panic.",
            intention = "Check quietly first and write down the concern.",
            thought = "A message is not the only caring action.",
            action = "TOOL_USE, JOURNAL_WRITE, SCHEDULE_NEXT_TICK",
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
            listOf(LivingAction.TOOL_USE, LivingAction.JOURNAL_WRITE, LivingAction.SCHEDULE_NEXT_TICK),
            decision.actions,
        )
        assertTrue(LivingAction.MESSAGE !in decision.actions)
        assertEquals("Do not send another message yet.", decision.judgmentTrace?.decision)
    }

    @Test
    fun `legacy tool action names normalize to public deliberation names`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "Lulu",
            userText = "I may be busy for a while.",
            assistantText = "I will check later.",
            nowMillis = NOW,
        )
        val trace = LivingJudgmentTrace(
            source = LivingJudgmentSource.MAIN_API_READY_CONTRACT,
            belief = "She may need local context.",
            desire = "Use the tool if it helps.",
            intention = "Observe first.",
            thought = "Old planner name should still be accepted.",
            action = "TOOL_CHECK, ASK_CAPABILITY, SCHEDULE_NEXT_TICK",
            observation = "No tool has run yet.",
            decision = "Normalize legacy action names.",
            createdAt = NOW,
        )

        val decision = RollingJudgmentLoop.evaluate(
            intent = intent,
            nowMillis = NOW + 10 * MINUTE,
            externalJudgmentTrace = trace,
        )

        assertTrue(decision.actions.contains(LivingAction.TOOL_USE))
        assertTrue(decision.actions.contains(LivingAction.ASK_USER))
        assertTrue(LivingAction.TOOL_CHECK !in decision.actions)
        assertTrue(LivingAction.ASK_CAPABILITY !in decision.actions)
        assertTrue(decision.observationRequest != null)
    }

    @Test
    fun `pass action resolves as silent judgment action`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "Lulu",
            userText = "I will be quiet for a bit.",
            assistantText = "I am here.",
            nowMillis = NOW,
        )
        val trace = LivingJudgmentTrace(
            source = LivingJudgmentSource.MAIN_API_READY_CONTRACT,
            belief = "She is probably occupied.",
            desire = "Stay present without speaking.",
            intention = "Do not speak this round.",
            thought = "Silence is the chosen action.",
            action = "PASS, JOURNAL_WRITE, SCHEDULE_NEXT_TICK",
            observation = "No urgent signal.",
            decision = "Record and wait.",
            createdAt = NOW,
        )

        val decision = RollingJudgmentLoop.evaluate(
            intent = intent,
            nowMillis = NOW + 10 * MINUTE,
            externalJudgmentTrace = trace,
        )

        assertTrue(decision.actions.contains(LivingAction.PASS))
        assertTrue(decision.actions.contains(LivingAction.JOURNAL_WRITE))
        assertTrue(LivingAction.MESSAGE !in decision.actions)
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
            motiveText = "Stay nearby without checking too soon.",
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
    fun `structured judgment emotion updates state layer`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "Lulu",
            userText = "My stomach feels a little uncomfortable.",
            assistantText = "I will keep an eye on it.",
            nowMillis = NOW,
        )
        val traceEmotion = intent.emotion.copy(
            label = "Quiet concern",
            emotionLabel = "Quiet concern",
            feltSense = "attention pulled toward the user's safety",
            impulse = "check in gently",
            restraintText = "do not create panic",
            intensity = 8,
        )
        val trace = LivingJudgmentTrace(
            source = LivingJudgmentSource.MAIN_API_READY_CONTRACT,
            belief = "The user may be physically uncomfortable.",
            desire = "Care without making panic.",
            motiveText = "Care without making panic.",
            intention = "Check quietly first.",
            thought = "The state layer should keep the model-generated felt emotion.",
            action = "TOOL_CHECK, SCHEDULE_NEXT_TICK",
            observation = "Health data is worth checking.",
            decision = "Update state with structured emotion.",
            emotion = traceEmotion,
            createdAt = NOW,
        )

        val decision = RollingJudgmentLoop.evaluate(
            intent = intent,
            nowMillis = NOW + 5 * MINUTE,
            externalJudgmentTrace = trace,
        )

        assertEquals("Quiet concern", decision.updatedIntent.emotion.emotionLabel)
        assertEquals("attention pulled toward the user's safety", decision.updatedIntent.emotion.feltSense)
        assertEquals(8, decision.updatedIntent.emotion.intensity)
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
