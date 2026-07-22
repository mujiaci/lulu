package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage

/** Final safety gate after all input transformers. Source-level filtering should do most work. */
internal data class FinalCompanionContextResult(
    val messages: List<UIMessage>,
    val estimatedTokens: Int,
    val droppedMessages: Int,
    val compactedSystemMessages: Int,
)

internal fun enforceFinalCompanionContextBudget(
    messages: List<UIMessage>,
    source: ApiUsageSource,
): FinalCompanionContextResult {
    val maxTokens = source.finalCompanionInputBudget()
    var bounded = deduplicateFinalSystemSnapshots(messages)
    val compactedSystemMessages = (messages.size - bounded.size).coerceAtLeast(0)
    var dropped = compactedSystemMessages

    // Preserve current system truth and recent conversation. Remove oldest conversational
    // messages first only when source-level selection still exceeds the emergency ceiling.
    while (estimateMessagesTokens(bounded) > maxTokens) {
        val latestUser = bounded.indexOfLast { it.role == MessageRole.USER }
        val nonSystemCount = bounded.count { it.role != MessageRole.SYSTEM }
        val removableIndex = bounded.indices.firstOrNull { index ->
            val message = bounded[index]
            message.role != MessageRole.SYSTEM &&
                index < latestUser &&
                nonSystemCount > MIN_NON_SYSTEM_MESSAGES
        } ?: -1
        if (removableIndex < 0) break
        bounded = bounded.toMutableList().also { it.removeAt(removableIndex) }
        dropped += 1
    }

    return FinalCompanionContextResult(
        messages = bounded,
        estimatedTokens = estimateMessagesTokens(bounded),
        droppedMessages = dropped,
        compactedSystemMessages = compactedSystemMessages,
    )
}

private fun deduplicateFinalSystemSnapshots(messages: List<UIMessage>): List<UIMessage> {
    val seenKinds = mutableSetOf<String>()
    val seenExact = mutableSetOf<String>()
    val kept = mutableListOf<UIMessage>()
    messages.asReversed().forEach { message ->
        if (message.role != MessageRole.SYSTEM) {
            kept += message
            return@forEach
        }
        val text = message.toText().trim()
        if (text.isBlank()) {
            kept += message
            return@forEach
        }
        val kind = finalSnapshotKind(text)
        val keep = if (kind != null) seenKinds.add(kind) else seenExact.add(normalizeSystemText(text))
        if (keep) kept += message
    }
    return kept.asReversed()
}

private fun finalSnapshotKind(text: String): String? = when {
    text.contains("<companion_runtime", ignoreCase = true) -> "runtime"
    text.contains("<companion_private_context", ignoreCase = true) -> "private_context"
    text.contains("<companion_presence_contract", ignoreCase = true) -> "presence_contract"
    text.contains("<private_user_profile", ignoreCase = true) -> "user_profile"
    text.contains("<rolling_summary", ignoreCase = true) -> "rolling_summary"
    text.contains("<lulu_memory", ignoreCase = true) -> "memory"
    text.contains("<study_state", ignoreCase = true) || text.contains("<study_context", ignoreCase = true) -> "study"
    text.contains("<time_reminder", ignoreCase = true) -> "time"
    text.contains("<prompt_injection_planner", ignoreCase = true) || text.contains("本轮可用表达池") -> "planner"
    else -> null
}

private fun normalizeSystemText(text: String): String = text
    .lineSequence().map(String::trim).filter(String::isNotBlank).joinToString("\n")

private fun estimateMessagesTokens(messages: List<UIMessage>): Int = messages.sumOf { message ->
    val chars = message.toText().length
    if (chars == 0) 0 else ((chars + 3) / 4).coerceAtLeast(1)
}

private fun ApiUsageSource.finalCompanionInputBudget(): Int = when (this) {
    ApiUsageSource.CHAT -> 15_000
    ApiUsageSource.PHONE -> 10_000
    ApiUsageSource.GAME -> 12_000
    ApiUsageSource.OTHER -> 12_000
}

private const val MIN_NON_SYSTEM_MESSAGES = 8
