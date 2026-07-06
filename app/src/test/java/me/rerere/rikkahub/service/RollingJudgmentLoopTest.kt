package me.rerere.rikkahub.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RollingJudgmentLoopTest {
    @Test
    fun `health event uses dense semantic cadence`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "闇查湶",
            userText = "鎴戠幇鍦ㄨ倸瀛愬ソ闅惧彈",
            assistantText = "鍏堝潗涓嬶紝鎴戞湁鐐规媴蹇冧綘銆?,
            nowMillis = NOW,
        )

        assertEquals(LivingIntentKind.HEALTH_SAFETY, intent.kind)
        assertEquals(listOf(5L, 10L, 20L, 40L, 90L), intent.evaluationCadence.delaysMinutes)
        assertEquals(NOW + 5 * MINUTE, intent.nextEvaluateAt)
        assertTrue(intent.hypotheses.contains("鐢ㄦ埛韬綋涓嶈垝鏈嶏紝鍙兘闇€瑕佸畨鍏ㄧ‘璁?))
        assertTrue(intent.candidateActions.contains(LivingAction.TOOL_USE))
        assertTrue(intent.candidateActions.contains(LivingAction.PASS))
        assertTrue(intent.candidateActions.contains(LivingAction.ASK_USER))
    }

    @Test
    fun `ordinary silence uses non random rolling cadence`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "闇查湶",
            userText = "鎴戝厛蹇欎竴涓?,
            assistantText = "濂斤紝鎴戝湪杩欓噷銆?,
            nowMillis = NOW,
        )

        assertEquals(LivingIntentKind.ORDINARY_SILENCE, intent.kind)
        assertEquals(listOf(10L, 25L, 60L, 120L), intent.evaluationCadence.delaysMinutes)
        assertEquals("鐢ㄦ埛鍙兘鍦ㄥ繖", intent.hypotheses.first())
    }

    @Test
    fun `study event protects focus with slower cadence`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "闇查湶",
            userText = "鎴戣鍘诲涔犱笓涓氳",
            assistantText = "鍡紝鎴戝府浣犲畧鐫€鑺傚銆?,
            nowMillis = NOW,
        )

        assertEquals(LivingIntentKind.STUDY_FOCUS, intent.kind)
        assertEquals(listOf(30L, 60L, 90L), intent.evaluationCadence.delaysMinutes)
        assertTrue(intent.candidateActions.contains(LivingAction.WRITE_DIARY))
        assertTrue(LivingAction.MEMORY_UPDATE !in intent.candidateActions)
    }

    @Test
    fun `deadline event schedules checks before due time`() {
        val dueAt = NOW + 6 * 60 * MINUTE
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "闇查湶",
            userText = "杩欎釜浠诲姟鏅氫笂 6 鐐瑰墠瑕佸畬鎴?,
            assistantText = "鎴戜細甯綘鐩繘搴︺€?,
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
            assistantName = "闇查湶",
            userText = "鎴?9 鐐硅璧峰簥",
            assistantText = "閭ｆ垜鎻愬墠涓€鐐规儲璁颁綘銆?,
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
            assistantName = "闇查湶",
            userText = "鎴?9 鐐硅璧峰簥",
            assistantText = "閭ｆ垜鍏堟牎鍑嗘椂闂达紝鍐嶅府浣犵洴鐫€銆?,
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
            action = "SET_ALARM, WRITE_DIARY, SCHEDULE_NEXT_PERCEPTION",
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
        assertTrue(decision.actions.contains(LivingAction.WRITE_DIARY))
    }

    @Test
    fun `spoken intent becomes restrained on later silent evaluation`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "闇查湶",
            userText = "鎴戝厛蹇欎竴涓?,
            assistantText = "濂斤紝鎴戝湪杩欓噷銆?,
            nowMillis = NOW,
        ).copy(
            spokenCount = 1,
            lastSpokenAt = NOW + 10 * MINUTE,
            nextEvaluateAt = NOW + 25 * MINUTE,
        )

        val decision = RollingJudgmentLoop.evaluate(intent, nowMillis = NOW + 25 * MINUTE)

        assertEquals(LivingIntentStatus.RESTRAINED, decision.updatedIntent.status)
        assertTrue(decision.actions.contains(LivingAction.WAIT))
        assertTrue(decision.actions.contains(LivingAction.WRITE_DIARY))
        assertTrue(decision.actions.contains(LivingAction.SCHEDULE_NEXT_PERCEPTION))
        assertTrue(decision.updatedIntent.restraint > intent.restraint)
        assertEquals(1, decision.updatedIntent.silentEvaluationCount)
        assertTrue(decision.observation?.requestedTools?.contains("get_app_usage") == true)
        assertTrue(decision.judgmentTrace?.thought?.contains("鎯呭鎰熺煡-鎰忎箟璇勪及-鐘舵€佷繚鎸?瀹¤鍐崇瓥-琛屼负瀹炵幇-浜烘牸琛ㄨ揪-缁忛獙娌夋穩") == true)
        assertEquals(LivingJudgmentSource.MAIN_API_READY_CONTRACT, decision.judgmentTrace?.source)
    }

    @Test
    fun `external observation is used before structured judgment`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "闇查湶",
            userText = "鎴戣鍘诲涔犱笓涓氳",
            assistantText = "鍡紝鎴戝府浣犲畧鐫€鑺傚銆?,
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
            assistantName = "闇查湶",
            userText = "鎴戣鍘诲涔犱笓涓氳",
            assistantText = "鍡紝鎴戝府浣犲畧鐫€鑺傚銆?,
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
        assertEquals("感知世界包-意义评估-动态判断-行动实现-状态生成-辞海记忆", trace.architectureName)
        assertTrue(trace.perception.contains("Runtime observation"))
        assertTrue(trace.appraisal.contains(intent.appraisal.meaning))
        assertTrue(trace.state.contains("mood="))
        assertTrue(trace.state.contains("relationship="))
        assertTrue(trace.state.contains("innerThought="))
        assertTrue(trace.deliberation.contains("Living presence trace"))
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
            assistantName = "闇查湶",
            userText = "鎴戝厛蹇欎笁涓皬鏃?,
            assistantText = "濂斤紝鎴戜細鑷繁鍒ゆ柇浠€涔堟椂鍊欓潬杩戙€?,
            nowMillis = NOW,
        )
        val trace = LivingJudgmentTrace(
            source = LivingJudgmentSource.MAIN_API_READY_CONTRACT,
            belief = "She may be busy, not ignoring me.",
            desire = "Stay near without interrupting.",
            motiveText = "Stay near without interrupting.",
            intention = "Wait and write journal first.",
            thought = "I checked the observation and chose restraint.",
            action = "WAIT, WRITE_DIARY, SCHEDULE_NEXT_PERCEPTION",
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
            assistantName = "闇查湶",
            userText = "鎴戠幇鍦ㄨ倸瀛愬ソ鐥?,
            assistantText = "鍏堝潗涓嬶紝鎴戜細纭浣犲畨鍏ㄣ€?,
            nowMillis = NOW,
        )
        val trace = LivingJudgmentTrace(
            source = LivingJudgmentSource.MAIN_API_READY_CONTRACT,
            belief = "She may be resting after saying her stomach hurts.",
            desire = "Care without making panic.",
            motiveText = "Care without making panic.",
            intention = "Check quietly first and write down the concern.",
            thought = "A message is not the only caring action.",
            action = "TOOL_USE, WRITE_DIARY, SCHEDULE_NEXT_PERCEPTION",
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
            listOf(LivingAction.TOOL_USE, LivingAction.WRITE_DIARY, LivingAction.SCHEDULE_NEXT_PERCEPTION),
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
            action = "TOOL_CHECK, ASK_CAPABILITY, SCHEDULE_NEXT_PERCEPTION",
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
            action = "PASS, WRITE_DIARY, SCHEDULE_NEXT_PERCEPTION",
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
        assertTrue(decision.actions.contains(LivingAction.WRITE_DIARY))
        assertTrue(LivingAction.MESSAGE !in decision.actions)
    }

    @Test
    fun `main api structured judgment controls next evaluation time`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "闇查湶",
            userText = "鎴戝繖涓変釜灏忔椂锛屽彲鑳戒笉鍥炰綘",
            assistantText = "濂斤紝鎴戜細鑷繁鍒ゆ柇浠€涔堟椂鍊欏啀鎯宠繖浠朵簨銆?,
            nowMillis = NOW,
        )
        val trace = LivingJudgmentTrace(
            source = LivingJudgmentSource.MAIN_API_READY_CONTRACT,
            belief = "She is probably busy for a long block.",
            desire = "Stay nearby without checking too soon.",
            motiveText = "Stay nearby without checking too soon.",
            intention = "Wait longer, then reassess.",
            thought = "Five minutes would be too clingy here.",
            action = "WAIT, WRITE_DIARY, SCHEDULE_NEXT_PERCEPTION",
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
            action = "TOOL_CHECK, SCHEDULE_NEXT_PERCEPTION",
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
            assistantName = "闇查湶",
            userText = "鎴戝厛蹇欎竴浼氬効",
            assistantText = "濂斤紝鎴戝湪杩欓噷绛変綘銆?,
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
        assertTrue(decision.updatedIntent.emotion.label.contains("蹇嶄綇"))
    }

    @Test
    fun `emotion state decays across long quiet gaps before new deltas`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "闇查湶",
            userText = "鎴戝厛蹇欎竴涓?,
            assistantText = "濂斤紝鎴戝湪杩欓噷绛変綘銆?,
            nowMillis = NOW,
        ).copy(
            emotion = EmotionSnapshot(
                concern = 8,
                attachment = 8,
                restraint = 8,
                label = "寰堢揣寮?,
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
