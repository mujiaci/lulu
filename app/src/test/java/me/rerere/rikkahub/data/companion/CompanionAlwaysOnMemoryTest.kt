package me.rerere.rikkahub.data.companion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompanionAlwaysOnMemoryTest {
    @Test
    fun `health statements become always on anchors`() {
        val anchors = detectAlwaysOnAnchors(
            assistantId = "assistant",
            userText = "我身体不好，之后安排学习要注意一点。",
            nowMillis = 100L,
        )

        assertEquals(1, anchors.size)
        assertEquals(CompanionAlwaysOnAnchorKind.HEALTH, anchors.single().kind)
        assertTrue(anchors.single().statement.contains("身体"))
    }

    @Test
    fun `delegated daily wake alarm becomes a responsibility anchor`() {
        val anchors = detectAlwaysOnAnchors(
            assistantId = "assistant",
            userText = "以后每天晚上定闹钟、叫我起床，这件事交给你了，记住。",
            nowMillis = 100L,
        )

        assertEquals(1, anchors.size)
        val anchor = anchors.single()
        assertEquals(CompanionAlwaysOnAnchorKind.RESPONSIBILITY, anchor.kind)
        assertTrue(anchor.responsibility.orEmpty().contains("睡眠"))
    }

    @Test
    fun `ordinary recall-like statements do not become permanent anchors`() {
        val anchors = detectAlwaysOnAnchors(
            assistantId = "assistant",
            userText = "你还记得我昨天睡得很晚吗？",
            nowMillis = 100L,
        )

        assertTrue(anchors.isEmpty())
    }

    @Test
    fun `explicit correction cancels the matching responsibility anchor`() {
        val ids = detectAlwaysOnAnchorCancellations(
            assistantId = "assistant",
            userText = "以后不用你负责每天的闹钟了。",
        )

        assertEquals(listOf("assistant:responsibility:wake-alarm"), ids)
    }

    @Test
    fun `model draft gets a stable id and cannot create impression-only memory`() {
        val draft = CompanionResponsibilityAnchorDraft(
            stableKey = "water",
            statement = "用户把规律喝水交给我照看",
            responsibility = "学习时间过长时提醒用户补水",
            actions = listOf("读取学习时长", "发送轻提醒"),
        )

        val first = draft.toAlwaysOnAnchorOrNull("assistant", nowMillis = 100L)
        val second = draft.toAlwaysOnAnchorOrNull("assistant", nowMillis = 200L)

        assertEquals("assistant:responsibility:water", first?.id)
        assertEquals(first?.id, second?.id)
        assertTrue(first?.actions?.isNotEmpty() == true)
    }
}
