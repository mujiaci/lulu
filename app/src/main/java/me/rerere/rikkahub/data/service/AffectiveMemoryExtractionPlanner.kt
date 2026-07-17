package me.rerere.rikkahub.data.service

import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.MessageNode

private const val EXTRACTION_INTERVAL = 20
private const val STABLE_TAIL_BUFFER = 6
private const val BURST_TAIL_BUFFER = 3
private const val EXTRACTION_WINDOW_SIZE = 30
private const val EXTRACTION_OVERLAP = 6

data class AffectiveMemoryExtractionPlan(
    val turns: List<MemoryExtractionTurn>,
    val reason: String,
)

fun buildAffectiveMemoryExtractionPlan(
    messageNodes: List<MessageNode>,
    processedSourceNodeIds: Set<String>,
): AffectiveMemoryExtractionPlan? {
    val logicalTurns = messageNodes.toMemoryExtractionTurns()
    val stableTurns = logicalTurns.dropLast(STABLE_TAIL_BUFFER)
    val processedStableCount = stableTurns.latestProcessedIndex(processedSourceNodeIds) + 1

    if (stableTurns.size - processedStableCount >= EXTRACTION_INTERVAL) {
        val start = (processedStableCount - EXTRACTION_OVERLAP).coerceAtLeast(0)
        val end = (start + EXTRACTION_WINDOW_SIZE).coerceAtMost(stableTurns.size)
        val turns = stableTurns.subList(start, end)
        if (turns.isNotEmpty()) {
            return AffectiveMemoryExtractionPlan(turns = turns, reason = "interval")
        }
    }

    val burstTurns = logicalTurns.dropLast(BURST_TAIL_BUFFER)
    val burstIndex = burstTurns.indexOfLast { turn ->
        turn.nodeId !in processedSourceNodeIds && turn.text.hasBurstMemorySignal()
    }
    if (burstIndex >= 0) {
        val start = (burstIndex - EXTRACTION_OVERLAP).coerceAtLeast(0)
        val end = (start + EXTRACTION_WINDOW_SIZE).coerceAtMost(burstTurns.size)
        val turns = burstTurns.subList(start, end)
        if (turns.isNotEmpty()) {
            return AffectiveMemoryExtractionPlan(turns = turns, reason = "burst")
        }
    }

    return null
}

internal fun List<MessageNode>.toMemoryExtractionTurns(): List<MemoryExtractionTurn> =
    mapNotNull { node ->
        val message = runCatching { node.currentMessage }.getOrNull() ?: return@mapNotNull null
        if (message.role != MessageRole.USER && message.role != MessageRole.ASSISTANT) return@mapNotNull null
        val text = message.toText().trim()
        if (text.isBlank()) return@mapNotNull null
        MemoryExtractionTurn(
            nodeId = node.id.toString(),
            role = message.role.name.lowercase(),
            text = text,
        )
    }

private fun List<MemoryExtractionTurn>.latestProcessedIndex(processedSourceNodeIds: Set<String>): Int =
    indexOfLast { it.nodeId in processedSourceNodeIds }

private fun String.hasBurstMemorySignal(): Boolean {
    val lowered = lowercase()
    return listOf(
        "崩溃",
        "难过",
        "伤心",
        "哭",
        "喜欢",
        "讨厌",
        "答应",
        "承诺",
        "别忘",
        "记住",
        "以后",
        "关系",
        "亲密",
        "冲突",
        "边界",
        "不要",
        "别再",
        "更喜欢",
        "希望你",
        "道歉",
        "原谅",
        "说开",
        "纠正",
        "sad",
        "promise",
        "remember",
        "love",
        "hate",
    ).any { signal -> signal in lowered }
}
