package me.rerere.rikkahub.ui.pages.cihai

import me.rerere.rikkahub.service.LivingCapabilityRequest
import me.rerere.rikkahub.service.LivingIntentKind
import me.rerere.rikkahub.service.RollingJudgmentLoop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivingPresenceCardsTest {
    @Test
    fun `cards show selected assistant concerns sorted by next perception`() {
        val later = RollingJudgmentLoop.createIntent(
            assistantId = "lulu",
            assistantName = "露露",
            userText = "我先去学习",
            assistantText = "我守着你。",
            nowMillis = NOW,
        )
        val sooner = RollingJudgmentLoop.createIntent(
            assistantId = "lulu",
            assistantName = "露露",
            userText = "我肚子疼",
            assistantText = "我先确认你安全。",
            nowMillis = NOW,
        )
        val otherAssistant = RollingJudgmentLoop.createIntent(
            assistantId = "other",
            assistantName = "别的角色",
            userText = "我先忙",
            assistantText = "好。",
            nowMillis = NOW,
        )

        val cards = buildLivingIntentCards(
            intents = listOf(later, otherAssistant, sooner),
            selectedAssistantId = "lulu",
            nowMillis = NOW,
        )

        assertEquals(listOf(LivingIntentKind.HEALTH_SAFETY, LivingIntentKind.STUDY_FOCUS), cards.map { it.kind })
        assertTrue(cards.first().title.contains("身体"))
        assertTrue(cards.first().eventLine.contains("事件："))
        assertTrue(cards.first().goalLine.contains("目标："))
        assertTrue(cards.first().stateLine.contains("本轮判断"))
        assertTrue(cards.first().emotionLine.contains("冲动"))
        assertTrue(cards.first().perceptionLine.contains("重新从感知层开始"))
    }

    @Test
    fun `cards summarize overdue and repeated silent judgments`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantId = "lulu",
            assistantName = "露露",
            userText = "我先忙一下",
            assistantText = "好，我等你。",
            nowMillis = NOW,
        ).copy(
            nextEvaluateAt = NOW - MINUTE,
            spokenCount = 1,
            silentEvaluationCount = 3,
            restraint = 9,
        )

        val card = buildLivingIntentCards(
            intents = listOf(intent),
            selectedAssistantId = "lulu",
            nowMillis = NOW,
        ).single()

        assertEquals("现在该重新感知", card.nextPerceptionText)
        assertTrue(card.countLine.contains("默默判断 3 次"))
        assertTrue(card.countLine.contains("开口 1 次"))
        assertTrue(card.countLine.contains("克制 9/10"))
    }

    @Test
    fun `cards expose capability requests when role wants new observation ability`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantId = "lulu",
            assistantName = "露露",
            userText = "我肚子疼",
            assistantText = "我想确认你安全。",
            nowMillis = NOW,
        ).copy(
            capabilityRequests = listOf(
                LivingCapabilityRequest(
                    capability = "health_or_wearable_status",
                    reason = "需要更可靠的健康或穿戴设备线索，不能只靠猜。",
                    createdAt = NOW,
                )
            )
        )

        val card = buildLivingIntentCards(
            intents = listOf(intent),
            selectedAssistantId = "lulu",
            nowMillis = NOW,
        ).single()

        val capabilityLine = card.capabilityLine!!
        assertTrue(capabilityLine.contains("health_or_wearable_status"))
        assertTrue(capabilityLine.contains("健康或穿戴设备线索"))
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val MINUTE = 60_000L
    }
}
