package me.rerere.rikkahub.data.service

import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.MessageNode
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

const val DEFAULT_MEMORY_EXTRACTION_INTERVAL = 20
const val MEMORY_EXTRACTION_TAIL_BUFFER = 10

data class AffectiveMemoryExtractionPlan(
    val turns: List<MemoryExtractionTurn>,
    val reason: String,
)

enum class MemoryExtractionDirection {
    OLDEST_FIRST,
    RECENT_FIRST,
}

fun buildAffectiveMemoryExtractionPlan(
    messageNodes: List<MessageNode>,
    processedSourceNodeIds: Set<String>,
    extractionInterval: Int = DEFAULT_MEMORY_EXTRACTION_INTERVAL,
    direction: MemoryExtractionDirection = MemoryExtractionDirection.OLDEST_FIRST,
): AffectiveMemoryExtractionPlan? {
    if (extractionInterval <= 0) return null
    val logicalTurns = messageNodes.toMemoryExtractionTurns()
    val stableTurns = logicalTurns.dropLast(MEMORY_EXTRACTION_TAIL_BUFFER)
    // Only complete, interval-aligned windows are eligible. For example, with 335
    // logical turns and an interval of 40, turns 321..325 remain pending after the
    // newest ten are protected, while 281..320 is the newest complete window.
    val alignedStableTurns = stableTurns.take(
        (stableTurns.size / extractionInterval) * extractionInterval,
    )
    // The interval is the user's exact batch size, not merely a trigger threshold.
    // Work from the earliest source nodes that have never been checkpointed, so a
    // retry can repair old partial batches without re-summarising already handled turns.
    val pendingStableTurns = alignedStableTurns.filterNot { it.nodeId in processedSourceNodeIds }
    if (pendingStableTurns.size < extractionInterval) return null

    val selectedTurns = when (direction) {
        MemoryExtractionDirection.OLDEST_FIRST -> pendingStableTurns.take(extractionInterval)
        MemoryExtractionDirection.RECENT_FIRST -> pendingStableTurns.takeLast(extractionInterval)
    }

    return AffectiveMemoryExtractionPlan(
        turns = selectedTurns,
        reason = if (direction == MemoryExtractionDirection.RECENT_FIRST) "recent_interval" else "interval",
    )
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
