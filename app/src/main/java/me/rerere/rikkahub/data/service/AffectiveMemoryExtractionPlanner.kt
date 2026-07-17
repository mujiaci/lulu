package me.rerere.rikkahub.data.service

import me.rerere.rikkahub.data.model.MessageNode
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

const val DEFAULT_MEMORY_EXTRACTION_INTERVAL = 20
const val MEMORY_EXTRACTION_TAIL_BUFFER = 10
private const val EXTRACTION_WINDOW_SIZE = 30
private const val EXTRACTION_OVERLAP = 6

data class AffectiveMemoryExtractionPlan(
    val turns: List<MemoryExtractionTurn>,
    val reason: String,
)

fun buildAffectiveMemoryExtractionPlan(
    messageNodes: List<MessageNode>,
    processedSourceNodeIds: Set<String>,
    extractionInterval: Int = DEFAULT_MEMORY_EXTRACTION_INTERVAL,
): AffectiveMemoryExtractionPlan? {
    if (extractionInterval <= 0) return null
    val logicalTurns = messageNodes.toMemoryExtractionTurns()
    val stableTurns = logicalTurns.dropLast(MEMORY_EXTRACTION_TAIL_BUFFER)
    val processedStableCount = stableTurns.latestProcessedIndex(processedSourceNodeIds) + 1

    if (stableTurns.size - processedStableCount >= extractionInterval) {
        val start = (processedStableCount - EXTRACTION_OVERLAP).coerceAtLeast(0)
        val end = (start + EXTRACTION_WINDOW_SIZE).coerceAtMost(stableTurns.size)
        val turns = stableTurns.subList(start, end)
        if (turns.isNotEmpty()) {
            return AffectiveMemoryExtractionPlan(turns = turns, reason = "interval")
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
            createdAtMillis = runCatching {
                message.createdAt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            }.getOrDefault(0L),
        )
    }

private fun List<MemoryExtractionTurn>.latestProcessedIndex(processedSourceNodeIds: Set<String>): Int =
    indexOfLast { it.nodeId in processedSourceNodeIds }

