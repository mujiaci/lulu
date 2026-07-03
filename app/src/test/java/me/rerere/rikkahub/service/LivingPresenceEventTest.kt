package me.rerere.rikkahub.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivingPresenceEventTest {
    @Test
    fun `extractor routes personality judgement to main api and time extraction to secondary api`() {
        val event = LivingPresenceEventExtractor.extract(
            assistantName = "露露",
            userText = "我现在肚子好难受，晚上 6 点前还要交任务",
            assistantText = "先坐下，我有点担心你。",
            nowMillis = NOW,
        )

        assertEquals(LivingPresenceEventKind.HEALTH_SAFETY, event.kind)
        assertTrue(event.apiPlan.mainApiTasks.contains(LivingApiTask.BDI_JUDGEMENT))
        assertTrue(event.apiPlan.mainApiTasks.contains(LivingApiTask.EMOTION_EVALUATION))
        assertTrue(event.apiPlan.secondaryApiTasks.contains(LivingApiTask.TIME_EXTRACTION))
        assertTrue(event.apiPlan.ruleTasks.contains(LivingApiTask.EXACT_SCHEDULING))
        assertTrue(event.rawSignals.any { it.contains("肚子") })
    }

    @Test
    fun `extractor recognizes wake target as schedule event`() {
        val event = LivingPresenceEventExtractor.extract(
            assistantName = "露露",
            userText = "我 9 点要起床",
            assistantText = "那我提前一点惦记你。",
            nowMillis = NOW,
            targetAtMillis = NOW + 60 * MINUTE,
        )

        assertEquals(LivingPresenceEventKind.WAKE_UP, event.kind)
        assertEquals(NOW + 60 * MINUTE, event.targetAtMillis)
        assertTrue(event.apiPlan.ruleTasks.contains(LivingApiTask.PERMISSION_CHECK))
    }

    @Test
    fun `extractor parses chinese deadline time without explicit millis`() {
        val event = LivingPresenceEventExtractor.extract(
            assistantName = "露露",
            userText = "这个任务晚上 6 点前要提交",
            assistantText = "我会帮你盯一下。",
            nowMillis = NOW,
        )

        assertEquals(LivingPresenceEventKind.DEADLINE, event.kind)
        assertTrue(event.deadlineAtMillis != null)
        assertTrue(event.rawSignals.any { it.startsWith("time_signal=") })
        assertTrue(event.apiPlan.secondaryApiTasks.contains(LivingApiTask.TIME_EXTRACTION))
    }

    @Test
    fun `extractor parses wake time from chinese text`() {
        val event = LivingPresenceEventExtractor.extract(
            assistantName = "露露",
            userText = "明天九点叫我起床",
            assistantText = "我会记着。",
            nowMillis = NOW,
        )

        assertEquals(LivingPresenceEventKind.WAKE_UP, event.kind)
        assertTrue(event.targetAtMillis != null)
        assertTrue(event.rawSignals.any { it.contains("明天九点") })
    }

    @Test
    fun `extractor parses relative reminder time`() {
        val event = LivingPresenceEventExtractor.extract(
            assistantName = "露露",
            userText = "10分钟后提醒我继续背书",
            assistantText = "我会记着。",
            nowMillis = NOW,
        )

        assertEquals(LivingPresenceEventKind.WAKE_UP, event.kind)
        assertEquals(NOW + 10 * MINUTE, event.targetAtMillis)
        assertTrue(event.rawSignals.any { it.contains("relative_time_signal=10分钟后") })
    }

    @Test
    fun `extractor parses compact tomorrow morning wake phrase`() {
        val event = LivingPresenceEventExtractor.extract(
            assistantName = "露露",
            userText = "明早九点叫我起床",
            assistantText = "我会提前惦记。",
            nowMillis = NOW,
        )

        assertEquals(LivingPresenceEventKind.WAKE_UP, event.kind)
        assertTrue(event.targetAtMillis != null)
        assertTrue(event.rawSignals.any { it.contains("明早九点") })
    }

    @Test
    fun `extractor treats plain call me at clock time as wake target`() {
        val event = LivingPresenceEventExtractor.extract(
            assistantName = "露露",
            userText = "9点叫我一下",
            assistantText = "我记住。",
            nowMillis = NOW,
        )

        assertEquals(LivingPresenceEventKind.WAKE_UP, event.kind)
        assertTrue(event.targetAtMillis != null)
        assertTrue(event.rawSignals.any { it.startsWith("time_signal=") })
    }

    @Test
    fun `extractor treats remind me at clock time as wake target`() {
        val event = LivingPresenceEventExtractor.extract(
            assistantName = "露露",
            userText = "晚上六点提醒我继续背书",
            assistantText = "好，到点我会来。",
            nowMillis = NOW,
        )

        assertEquals(LivingPresenceEventKind.WAKE_UP, event.kind)
        assertTrue(event.targetAtMillis != null)
    }

    @Test
    fun `extractor keeps relative busy duration as time signal without turning it into wake event`() {
        val event = LivingPresenceEventExtractor.extract(
            assistantName = "露露",
            userText = "我要忙三个小时，回来再说",
            assistantText = "好，我会自己判断。",
            nowMillis = NOW,
        )

        assertEquals(LivingPresenceEventKind.ORDINARY_SILENCE, event.kind)
        assertEquals(null, event.targetAtMillis)
        assertTrue(event.rawSignals.any { it.contains("relative_time_signal=三个小时") })
        assertTrue(event.apiPlan.secondaryApiTasks.contains(LivingApiTask.TIME_EXTRACTION))
    }

    @Test
    fun `belief store merges similar open event instead of creating duplicate intent`() {
        val first = LivingPresenceEventExtractor.extract(
            assistantName = "露露",
            userText = "我先忙一下",
            assistantText = "好，我在这里等你。",
            nowMillis = NOW,
        )
        val existing = RollingJudgmentLoop.createIntent(
            assistantName = "露露",
            userText = first.userText,
            assistantText = first.assistantText,
            nowMillis = NOW,
        )
        val second = first.copy(
            userText = "我还没回来",
            createdAt = NOW + 25 * MINUTE,
        )

        val merged = LivingBeliefStore.mergeEvent(
            existingIntents = listOf(existing),
            event = second,
            nowMillis = NOW + 25 * MINUTE,
        )

        assertEquals(1, merged.size)
        assertEquals(existing.id, merged.single().id)
        assertTrue(merged.single().belief.contains("我还没回来"))
        assertTrue(merged.single().hypotheses.contains("用户可能在忙"))
        assertEquals(NOW + 25 * MINUTE, merged.single().lastEvaluatedAt)
    }

    @Test
    fun `belief store creates new intent when event kind changes`() {
        val ordinary = RollingJudgmentLoop.createIntent(
            assistantName = "露露",
            userText = "我先忙一下",
            assistantText = "好，我在这里等你。",
            nowMillis = NOW,
        )
        val health = LivingPresenceEventExtractor.extract(
            assistantName = "露露",
            userText = "我现在肚子好痛",
            assistantText = "先坐下。",
            nowMillis = NOW + 10 * MINUTE,
        )

        val merged = LivingBeliefStore.mergeEvent(
            existingIntents = listOf(ordinary),
            event = health,
            nowMillis = NOW + 10 * MINUTE,
        )

        assertEquals(2, merged.size)
        assertTrue(merged.any { it.kind == LivingIntentKind.HEALTH_SAFETY })
        assertTrue(merged.any { it.kind == LivingIntentKind.ORDINARY_SILENCE })
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val MINUTE = 60_000L
    }
}
