package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.random.Random

fun createCompanionGameTool(
    assistantId: String,
    clockMillis: () -> Long = System::currentTimeMillis,
): Tool = Tool(
    name = "play_companion_game",
    description = "Play one real app-local mini-game as the configured character. Available games are signal_hunt, rock_paper_scissors, dice_duel, and tic_tac_toe. The engine returns actual moves and results. Use only when the character independently chooses to play; never claim a game happened unless this tool succeeds.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putJsonObject("game") {
                    put("type", JsonPrimitive("string"))
                    put("enum", buildJsonArray {
                        CompanionGameKind.entries.forEach { add(JsonPrimitive(it.wireName)) }
                    })
                    put("description", JsonPrimitive("The mini-game the character chooses to play."))
                }
                putJsonObject("strategy") {
                    put("type", JsonPrimitive("string"))
                    put("enum", buildJsonArray {
                        add(JsonPrimitive("curious"))
                        add(JsonPrimitive("careful"))
                        add(JsonPrimitive("bold"))
                    })
                    put("description", JsonPrimitive("The character's chosen play style."))
                }
            },
        )
    },
    execute = { input ->
        val nowMillis = clockMillis()
        val game = input.jsonObject["game"]?.jsonPrimitive?.contentOrNull
            ?.lowercase()
            ?.let { wireName -> CompanionGameKind.entries.firstOrNull { it.wireName == wireName } }
            ?: CompanionGameKind.SIGNAL_HUNT
        val strategy = input.jsonObject["strategy"]?.jsonPrimitive?.contentOrNull
            ?.lowercase()
            ?.takeIf { value -> value in CompanionGameStrategy.entries.map { it.wireName } }
            ?: CompanionGameStrategy.CURIOUS.wireName
        val selectedStrategy = CompanionGameStrategy.entries.first { it.wireName == strategy }
        val seed = (assistantId.hashCode() * 31) xor nowMillis.hashCode()
        val sessionId = UUID.nameUUIDFromBytes(
            "$assistantId|${game.wireName}|$nowMillis".toByteArray(StandardCharsets.UTF_8),
        ).toString()
        val details = when (game) {
            CompanionGameKind.SIGNAL_HUNT -> {
                val result = playCompanionSignalHunt(
                    assistantId = assistantId,
                    strategy = selectedStrategy,
                    seed = seed,
                )
                buildJsonObject {
                    put("strategy", result.strategy.wireName)
                    put("score", result.score)
                    put("max_score", result.maxScore)
                    put("signals_found", result.signalsFound)
                    put("result", result.resultText)
                    put("moves", buildJsonArray {
                        result.moves.forEach { move ->
                            add(buildJsonObject {
                                put("cell", move.cell)
                                put("found_signal", move.foundSignal)
                                put("points", move.points)
                            })
                        }
                    })
                }
            }
            CompanionGameKind.ROCK_PAPER_SCISSORS -> {
                val result = playCompanionRockPaperScissors(selectedStrategy, seed)
                buildJsonObject {
                    put("role_move", result.roleMove)
                    put("opponent_move", result.opponentMove)
                    put("outcome", result.outcome)
                    put("result", result.resultText)
                }
            }
            CompanionGameKind.DICE_DUEL -> {
                val result = playCompanionDiceDuel(seed)
                buildJsonObject {
                    put("role_roll", result.roleRoll)
                    put("opponent_roll", result.opponentRoll)
                    put("outcome", result.outcome)
                    put("result", result.resultText)
                }
            }
            CompanionGameKind.TIC_TAC_TOE -> {
                val result = playCompanionTicTacToe(selectedStrategy, seed)
                buildJsonObject {
                    put("outcome", result.outcome)
                    put("result", result.resultText)
                    put("moves", buildJsonArray {
                        result.moves.forEach { move ->
                            add(buildJsonObject {
                                put("player", move.player)
                                put("cell", move.cell)
                            })
                        }
                    })
                }
            }
        }
        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("success", true)
                    put("game", game.wireName)
                    put("session_id", sessionId)
                    put("played_at", nowMillis)
                    details.forEach { (key, value) -> put(key, value) }
                }.toString(),
            ),
        )
    },
)

internal enum class CompanionGameKind(val wireName: String) {
    SIGNAL_HUNT("signal_hunt"),
    ROCK_PAPER_SCISSORS("rock_paper_scissors"),
    DICE_DUEL("dice_duel"),
    TIC_TAC_TOE("tic_tac_toe"),
}

internal enum class CompanionGameStrategy(val wireName: String) {
    CURIOUS("curious"),
    CAREFUL("careful"),
    BOLD("bold"),
}

internal data class CompanionSimpleGameResult(
    val roleMove: String,
    val opponentMove: String,
    val outcome: String,
    val resultText: String,
)

internal data class CompanionTicTacToeMove(
    val player: String,
    val cell: Int,
)

internal data class CompanionTicTacToeResult(
    val outcome: String,
    val moves: List<CompanionTicTacToeMove>,
    val resultText: String,
)

internal fun playCompanionRockPaperScissors(
    strategy: CompanionGameStrategy,
    seed: Int,
): CompanionSimpleGameResult {
    val random = Random(seed)
    val moves = listOf("rock", "paper", "scissors")
    val roleMove = when (strategy) {
        CompanionGameStrategy.CAREFUL -> moves[(seed and Int.MAX_VALUE) % moves.size]
        CompanionGameStrategy.CURIOUS -> moves.random(random)
        CompanionGameStrategy.BOLD -> if (random.nextBoolean()) "rock" else "scissors"
    }
    val opponentMove = moves.random(random)
    val outcome = when {
        roleMove == opponentMove -> "draw"
        (roleMove == "rock" && opponentMove == "scissors") ||
            (roleMove == "paper" && opponentMove == "rock") ||
            (roleMove == "scissors" && opponentMove == "paper") -> "win"
        else -> "lose"
    }
    return CompanionSimpleGameResult(
        roleMove = roleMove,
        opponentMove = opponentMove,
        outcome = outcome,
        resultText = "角色选择了 $roleMove，对手选择了 $opponentMove，本局结果为 $outcome。",
    )
}

internal fun playCompanionDiceDuel(seed: Int): CompanionSimpleGameResult {
    val random = Random(seed)
    val roleRoll = random.nextInt(1, 7)
    val opponentRoll = random.nextInt(1, 7)
    val outcome = when {
        roleRoll > opponentRoll -> "win"
        roleRoll < opponentRoll -> "lose"
        else -> "draw"
    }
    return CompanionSimpleGameResult(
        roleMove = roleRoll.toString(),
        opponentMove = opponentRoll.toString(),
        outcome = outcome,
        resultText = "角色掷出 $roleRoll，对手掷出 $opponentRoll，本局结果为 $outcome。",
    )
}

internal fun playCompanionTicTacToe(
    strategy: CompanionGameStrategy,
    seed: Int,
): CompanionTicTacToeResult {
    val random = Random(seed)
    val board = MutableList<String?>(9) { null }
    val moves = mutableListOf<CompanionTicTacToeMove>()
    var current = "role"
    while (moves.size < 9 && ticTacToeWinner(board) == null) {
        val open = board.indices.filter { board[it] == null }
        val mark = if (current == "role") "X" else "O"
        val opponentMark = if (mark == "X") "O" else "X"
        val winning = open.firstOrNull { cell ->
            board.toMutableList().also { it[cell] = mark }.let(::ticTacToeWinner) == mark
        }
        val blocking = open.firstOrNull { cell ->
            board.toMutableList().also { it[cell] = opponentMark }.let(::ticTacToeWinner) == opponentMark
        }
        val cell = when {
            winning != null -> winning
            strategy == CompanionGameStrategy.CAREFUL && blocking != null -> blocking
            strategy == CompanionGameStrategy.BOLD -> open.filter { it in setOf(0, 2, 6, 8) }.randomOrNull(random)
                ?: open.random(random)
            4 in open -> 4
            blocking != null -> blocking
            else -> open.random(random)
        }
        board[cell] = mark
        moves += CompanionTicTacToeMove(current, cell)
        current = if (current == "role") "opponent" else "role"
    }
    val winner = ticTacToeWinner(board)
    val outcome = when (winner) {
        "X" -> "win"
        "O" -> "lose"
        else -> "draw"
    }
    return CompanionTicTacToeResult(
        outcome = outcome,
        moves = moves,
        resultText = "井字棋完整结束，本局结果为 $outcome。",
    )
}

internal fun ticTacToeWinner(board: List<String?>): String? =
    TIC_TAC_TOE_LINES.firstNotNullOfOrNull { line ->
        board[line[0]]?.takeIf { mark -> line.all { board[it] == mark } }
    }

private val TIC_TAC_TOE_LINES = listOf(
    listOf(0, 1, 2),
    listOf(3, 4, 5),
    listOf(6, 7, 8),
    listOf(0, 3, 6),
    listOf(1, 4, 7),
    listOf(2, 5, 8),
    listOf(0, 4, 8),
    listOf(2, 4, 6),
)

internal data class CompanionGameMove(
    val cell: Int,
    val foundSignal: Boolean,
    val points: Int,
)

internal data class CompanionGameResult(
    val strategy: CompanionGameStrategy,
    val score: Int,
    val maxScore: Int,
    val signalsFound: Int,
    val moves: List<CompanionGameMove>,
    val resultText: String,
)

internal fun playCompanionSignalHunt(
    assistantId: String,
    strategy: CompanionGameStrategy,
    seed: Int,
): CompanionGameResult {
    val random = Random(seed xor assistantId.hashCode())
    val signalCells = (0..8).shuffled(random).take(3).toSet()
    val preferredCells = when (strategy) {
        CompanionGameStrategy.CAREFUL -> listOf(4, 0, 2, 6, 8, 1, 3, 5, 7)
        CompanionGameStrategy.CURIOUS -> (0..8).shuffled(random)
        CompanionGameStrategy.BOLD -> listOf(0, 8, 2, 6, 4, 7, 1, 5, 3).shuffled(random)
    }
    var streak = 0
    val moves = preferredCells.take(5).map { cell ->
        val found = cell in signalCells
        streak = if (found) streak + 1 else 0
        CompanionGameMove(
            cell = cell,
            foundSignal = found,
            points = if (found) 20 + (streak - 1) * 5 else 0,
        )
    }
    val score = moves.sumOf(CompanionGameMove::points)
    val found = moves.count(CompanionGameMove::foundSignal)
    val resultText = when (found) {
        3 -> "找到了全部三个信号，完成了一局漂亮的全收集。"
        2 -> "找到了两个信号，差一点完成全收集。"
        1 -> "找到一个信号，留下了下一局想再试的路线。"
        else -> "这局没有找到信号，但完整走完了五次选择。"
    }
    return CompanionGameResult(
        strategy = strategy,
        score = score,
        maxScore = 75,
        signalsFound = found,
        moves = moves,
        resultText = resultText,
    )
}
