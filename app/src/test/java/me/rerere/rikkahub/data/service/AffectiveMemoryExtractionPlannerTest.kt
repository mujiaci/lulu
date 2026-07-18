package me.rerere.rikkahub.data.service

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AffectiveMemoryExtractionPlannerTest {
    @Test
    fun `planner waits until twenty stable logical messages remain after keeping newest ten`() {
        val shortConversation = nodes(29)

        val plan = buildAffectiveMemoryExtractionPlan(
            messageNodes = shortConversation,
            processedSourceNodeIds = emptySet(),
        )

        assertEquals(null, plan)
    }

    @Test
    fun `planner builds first extraction window from stable messages and excludes newest ten`() {
        val conversation = nodes(30)

        val plan = buildAffectiveMemoryExtractionPlan(
            messageNodes = conversation,
            processedSourceNodeIds = emptySet(),
        )

        requireNotNull(plan)
        assertEquals("interval", plan.reason)
        assertEquals(20, plan.turns.size)
        assertEquals(idOf(1), plan.turns.first().nodeId)
        assertEquals(idOf(20), plan.turns.last().nodeId)
        assertFalse(plan.turns.any { it.nodeId == idOf(21) })
    }

    @Test
    fun `planner selects the next unprocessed interval without overlap`() {
        val conversation = nodes(56)
        val processed = (1..20).map { idOf(it) }.toSet()

        val plan = buildAffectiveMemoryExtractionPlan(
            messageNodes = conversation,
            processedSourceNodeIds = processed,
        )

        requireNotNull(plan)
        assertEquals(20, plan.turns.size)
        assertEquals(idOf(21), plan.turns.first().nodeId)
        assertEquals(idOf(40), plan.turns.last().nodeId)
        assertFalse(plan.turns.any { it.nodeId in processed })
    }

    @Test
    fun `planner respects a forty message interval after keeping the newest ten`() {
        val conversation = nodes(90)
        val processed = (1..40).map { idOf(it) }.toSet()

        val plan = buildAffectiveMemoryExtractionPlan(
            messageNodes = conversation,
            processedSourceNodeIds = processed,
            extractionInterval = 40,
        )

        requireNotNull(plan)
        assertEquals(40, plan.turns.size)
        assertEquals(idOf(41), plan.turns.first().nodeId)
        assertEquals(idOf(80), plan.turns.last().nodeId)
    }

    @Test
    fun `planner uses role configured interval and zero disables automatic extraction`() {
        val conversation = nodes(22)

        assertEquals(
            null,
            buildAffectiveMemoryExtractionPlan(
                messageNodes = conversation,
                processedSourceNodeIds = emptySet(),
                extractionInterval = 20,
            ),
        )
        assertEquals(
            "interval",
            requireNotNull(
                buildAffectiveMemoryExtractionPlan(
                    messageNodes = conversation,
                    processedSourceNodeIds = emptySet(),
                    extractionInterval = 12,
                ),
            ).reason,
        )
        assertEquals(
            null,
            buildAffectiveMemoryExtractionPlan(
                messageNodes = conversation,
                processedSourceNodeIds = emptySet(),
                extractionInterval = 0,
            ),
        )
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
