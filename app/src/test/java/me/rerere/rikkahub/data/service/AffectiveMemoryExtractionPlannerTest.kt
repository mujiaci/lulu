package me.rerere.rikkahub.data.service

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AffectiveMemoryExtractionPlannerTest {
    @Test
    fun `planner waits until sixty stable logical messages remain after tail buffer`() {
        val shortConversation = nodes(69)

        val plan = buildAffectiveMemoryExtractionPlan(
            messageNodes = shortConversation,
            processedSourceNodeIds = emptySet(),
        )

        assertEquals(null, plan)
    }

    @Test
    fun `planner builds first extraction window from stable messages and excludes tail buffer`() {
        val conversation = nodes(70)

        val plan = buildAffectiveMemoryExtractionPlan(
            messageNodes = conversation,
            processedSourceNodeIds = emptySet(),
        )

        requireNotNull(plan)
        assertEquals("interval", plan.reason)
        assertEquals(60, plan.turns.size)
        assertEquals(idOf(1), plan.turns.first().nodeId)
        assertEquals(idOf(60), plan.turns.last().nodeId)
        assertFalse(plan.turns.any { it.nodeId == idOf(61) })
    }

    @Test
    fun `planner overlaps ten messages after the latest processed source node`() {
        val conversation = nodes(130)
        val processed = (1..60).map { idOf(it) }.toSet()

        val plan = buildAffectiveMemoryExtractionPlan(
            messageNodes = conversation,
            processedSourceNodeIds = processed,
        )

        requireNotNull(plan)
        assertEquals(idOf(51), plan.turns.first().nodeId)
        assertEquals(idOf(110), plan.turns.last().nodeId)
    }

    @Test
    fun `planner can trigger early for high affect turns while avoiding the newest three messages`() {
        val conversation = nodes(15) { index ->
            if (index == 10) "我现在真的很崩溃，答应我之后别丢下这件事" else "message $index"
        }

        val plan = buildAffectiveMemoryExtractionPlan(
            messageNodes = conversation,
            processedSourceNodeIds = emptySet(),
        )

        requireNotNull(plan)
        assertEquals("burst", plan.reason)
        assertTrue(plan.turns.any { it.text.contains("崩溃") })
        assertFalse(plan.turns.any { it.nodeId in setOf(idOf(13), idOf(14), idOf(15)) })
    }

    @Test
    fun `planner triggers early for an explicit user boundary`() {
        val conversation = nodes(15) { index ->
            if (index == 9) "我明确说一下，以后不要用打卡式语气催我" else "message $index"
        }

        val plan = buildAffectiveMemoryExtractionPlan(
            messageNodes = conversation,
            processedSourceNodeIds = emptySet(),
        )

        requireNotNull(plan)
        assertEquals("burst", plan.reason)
        assertTrue(plan.turns.any { it.text.contains("不要用打卡式语气") })
    }

    private fun nodes(
        count: Int,
        text: (Int) -> String = { index -> "message $index" },
    ): List<MessageNode> = (1..count).map { index ->
        MessageNode(
            id = kotlin.uuid.Uuid.parse(idOf(index)),
            messages = listOf(
                UIMessage(
                    role = if (index % 2 == 0) MessageRole.ASSISTANT else MessageRole.USER,
                    parts = listOf(UIMessagePart.Text(text(index))),
                )
            ),
        )
    }

    private fun idOf(index: Int): String = "00000000-0000-0000-0000-${index.toString().padStart(12, '0')}"
}
