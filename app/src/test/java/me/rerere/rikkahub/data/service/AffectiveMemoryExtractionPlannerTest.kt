package me.rerere.rikkahub.data.service

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AffectiveMemoryExtractionPlannerTest {
    @Test
    fun `selected branch fingerprint is stable and changes with selected alternative`() {
        val original = nodes(3)
        val branchA = buildSelectedConversationBranchId(original)
        val changed = original.toMutableList().also { nodes ->
            val node = nodes[1]
            nodes[1] = node.copy(
                messages = node.messages + UIMessage(
                    role = MessageRole.ASSISTANT,
                    parts = listOf(UIMessagePart.Text("alternative")),
                ),
                selectIndex = 1,
            )
        }

        assertEquals(branchA, buildSelectedConversationBranchId(original))
        assertNotEquals(branchA, buildSelectedConversationBranchId(changed))
    }

    @Test
    fun `batch branch fingerprint stays stable when newer messages are appended`() {
        val firstThree = nodes(3)
        val appended = firstThree + nodes(2)

        assertEquals(
            buildSelectedConversationBranchId(firstThree, 3),
            buildSelectedConversationBranchId(appended, 3),
        )
        assertNotEquals(
            buildSelectedConversationBranchId(firstThree),
            buildSelectedConversationBranchId(appended),
        )
    }

    @Test
    fun `selected path mutation reports first changed sequence`() {
        val original = nodes(5)
        val changed = original.toMutableList().also { items ->
            val node = items[2]
            items[2] = node.copy(
                messages = node.messages + UIMessage(
                    role = node.role,
                    parts = listOf(UIMessagePart.Text("edited branch")),
                ),
                selectIndex = 1,
            )
        }

        assertEquals(3, firstSelectedBranchMutationSequence(original, changed))
        assertEquals(4, firstSelectedBranchMutationSequence(original, original.take(3)))
        assertEquals(null, firstSelectedBranchMutationSequence(original, original))
    }

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
    fun `planner rebuilds an entire configured window when a legacy checkpoint has a hole`() {
        val conversation = nodes(50)
        val partiallyProcessed = (1..30).map { idOf(it) }.toSet()

        val plan = buildAffectiveMemoryExtractionPlan(
            messageNodes = conversation,
            processedSourceNodeIds = partiallyProcessed,
            extractionInterval = 20,
        )

        requireNotNull(plan)
        assertEquals(20, plan.turns.size)
        assertEquals(idOf(21), plan.turns.first().nodeId)
        assertEquals(idOf(40), plan.turns.last().nodeId)
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
    fun `recent planner selects newest complete aligned forty message batch`() {
        val conversation = nodes(335)

        val plan = buildAffectiveMemoryExtractionPlan(
            messageNodes = conversation,
            processedSourceNodeIds = emptySet(),
            extractionInterval = 40,
            direction = MemoryExtractionDirection.RECENT_FIRST,
        )

        requireNotNull(plan)
        assertEquals("recent_interval", plan.reason)
        assertEquals(40, plan.turns.size)
        assertEquals(idOf(281), plan.turns.first().nodeId)
        assertEquals(idOf(320), plan.turns.last().nodeId)
        assertFalse(plan.turns.any { it.nodeId == idOf(321) })
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
        val customPlan = requireNotNull(
            buildAffectiveMemoryExtractionPlan(
                messageNodes = conversation,
                processedSourceNodeIds = emptySet(),
                extractionInterval = 12,
            ),
        )
        assertEquals("interval", customPlan.reason)
        assertEquals(12, customPlan.turns.size)
        assertEquals(idOf(1), customPlan.turns.first().nodeId)
        assertEquals(idOf(12), customPlan.turns.last().nodeId)
        assertEquals(
            null,
            buildAffectiveMemoryExtractionPlan(
                messageNodes = conversation,
                processedSourceNodeIds = emptySet(),
                extractionInterval = 0,
            ),
        )
    }

    @Test
    fun `planner honors role configured recent protection across boundaries`() {
        val conversation = nodes(27)

        assertEquals(
            null,
            buildAffectiveMemoryExtractionPlan(
                messageNodes = conversation,
                processedSourceNodeIds = emptySet(),
                extractionInterval = 12,
                protectedRecentCount = 16,
            ),
        )

        val plan = requireNotNull(
            buildAffectiveMemoryExtractionPlan(
                messageNodes = conversation,
                processedSourceNodeIds = emptySet(),
                extractionInterval = 12,
                protectedRecentCount = 15,
            ),
        )
        assertEquals(12, plan.turns.size)
        assertEquals(idOf(1), plan.turns.first().nodeId)
        assertEquals(idOf(12), plan.turns.last().nodeId)
        assertFalse(plan.turns.any { it.nodeId == idOf(13) })
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
