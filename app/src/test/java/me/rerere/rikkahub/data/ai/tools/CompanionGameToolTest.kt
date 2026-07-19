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

    @Test
    fun `additional companion games are deterministic`() {
        assertEquals(
            playCompanionRockPaperScissors(CompanionGameStrategy.CURIOUS, 9),
            playCompanionRockPaperScissors(CompanionGameStrategy.CURIOUS, 9),
        )
        assertEquals(playCompanionDiceDuel(12), playCompanionDiceDuel(12))
        assertEquals(
            playCompanionTicTacToe(CompanionGameStrategy.CAREFUL, 21),
            playCompanionTicTacToe(CompanionGameStrategy.CAREFUL, 21),
        )
    }

    @Test
    fun `autonomous tic tac toe always completes a legal board`() {
        val result = playCompanionTicTacToe(CompanionGameStrategy.BOLD, 4)

        assertTrue(result.moves.size in 5..9)
        assertEquals(result.moves.size, result.moves.map { it.cell }.distinct().size)
        assertTrue(result.outcome in setOf("win", "lose", "draw"))
    }
}
