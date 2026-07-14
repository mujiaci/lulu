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
    description = "Play a real app-local digital mini-game as the character. The engine executes a complete Signal Hunt session and returns its actual moves and score. Use it only when the character independently wants to play; never claim a game was played unless this tool succeeds.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
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
        val strategy = input.jsonObject["strategy"]?.jsonPrimitive?.contentOrNull
            ?.lowercase()
            ?.takeIf { value -> value in CompanionGameStrategy.entries.map { it.wireName } }
            ?: CompanionGameStrategy.CURIOUS.wireName
        val result = playCompanionSignalHunt(
            assistantId = assistantId,
            strategy = CompanionGameStrategy.entries.first { it.wireName == strategy },
            seed = (assistantId.hashCode() * 31) xor nowMillis.hashCode(),
        )
        val sessionId = UUID.nameUUIDFromBytes(
            "$assistantId|signal_hunt|$nowMillis".toByteArray(StandardCharsets.UTF_8),
        ).toString()
        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("success", true)
                    put("game", "signal_hunt")
                    put("session_id", sessionId)
                    put("strategy", result.strategy.wireName)
                    put("score", result.score)
                    put("max_score", result.maxScore)
                    put("signals_found", result.signalsFound)
                    put("played_at", nowMillis)
                    put("result", result.resultText)
                    put("moves", buildJsonArray {
                        result.moves.forEach { move ->
                            add(
                                buildJsonObject {
                                    put("cell", move.cell)
                                    put("found_signal", move.foundSignal)
                                    put("points", move.points)
                                },
                            )
                        }
                    })
                }.toString(),
            ),
        )
    },
)

internal enum class CompanionGameStrategy(val wireName: String) {
    CURIOUS("curious"),
    CAREFUL("careful"),
    BOLD("bold"),
}

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
