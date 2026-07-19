package me.rerere.rikkahub.ui.pages.game

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickCompanionGameTest {
    @Test
    fun `rock paper scissors compares all outcomes`() {
        assertEquals("平局", compareRockPaperScissors("石头", "石头"))
        assertEquals("用户胜", compareRockPaperScissors("石头", "剪刀"))
        assertEquals("角色胜", compareRockPaperScissors("石头", "布"))
    }

    @Test
    fun `tic tac toe role takes a winning move`() {
        val board = listOf("O", "O", null, "X", "X", null, null, null, null)

        assertEquals(2, chooseTicTacToeMove(board, "O"))
    }

    @Test
    fun `tic tac toe role blocks user before choosing center`() {
        val board = listOf("X", "X", null, null, null, null, "O", null, null)

        assertEquals(2, chooseTicTacToeMove(board, "O"))
    }

    @Test
    fun `tic tac toe detects diagonal winner`() {
        assertEquals(
            "X",
            quickTicTacToeWinner(listOf("X", null, "O", null, "X", null, "O", null, "X")),
        )
    }
}
