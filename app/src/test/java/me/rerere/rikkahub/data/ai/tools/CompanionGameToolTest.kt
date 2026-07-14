package me.rerere.rikkahub.data.ai.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionGameToolTest {
    @Test
    fun `signal hunt is deterministic and executes five real moves`() {
        val first = playCompanionSignalHunt(
            assistantId = "assistant-a",
            strategy = CompanionGameStrategy.CAREFUL,
            seed = 42,
        )
        val second = playCompanionSignalHunt(
            assistantId = "assistant-a",
            strategy = CompanionGameStrategy.CAREFUL,
            seed = 42,
        )

        assertEquals(first, second)
        assertEquals(5, first.moves.size)
        assertEquals(first.moves.sumOf { it.points }, first.score)
        assertTrue(first.signalsFound in 0..3)
        assertTrue(first.moves.map { it.cell }.distinct().size == 5)
    }
}
