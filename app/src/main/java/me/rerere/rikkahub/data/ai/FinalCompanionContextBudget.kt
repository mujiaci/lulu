package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage

/**
 * Final safety gate applied after every input transformer has run.
 *
 * The earlier envelope protects history before transformers. Companion runtime,
 * memory, commitment and reminder transformers may add large SYSTEM blocks later,
 * so the provider-bound message list must be bounded once more here.
 */
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
    val deduplicated = deduplicateSystemMessages(messages)
    var compactedCount = 0
    var bounded = deduplicated.mapIndexed { index, message ->
        if (message.role != MessageRole.SYSTEM) return@mapIndexed message
        val text = message.toText()
        val charLimit = systemCharLimit(text, isFirstSystem = index == deduplicated.indexOfFirst { it.role == MessageRole.SYSTEM })
        if (text.length <= charLimit) {
            message
        } else {
            compactedCount += 1
            UIMessage.system(compactText(text, charLimit))
        }
    }

    var dropped = messages.size - deduplicated.size
    while (estimateMessagesTokens(bounded) > maxTokens) {
        val removableIndex = bounded.indexOfFirst { it.role != MessageRole.SYSTEM }
        if (removableIndex < 0) break
        bounded = bounded.toMutableList().also { it.removeAt(removableIndex) }
        dropped += 1
    }

    // A very large user-authored persona/world prompt can still exceed the target.
    // Keep its beginning and ending rather than silently dropping the whole identity.
    if (estimateMessagesTokens(bounded) > maxTokens) {
        val firstSystemIndex = bounded.indexOfFirst { it.role == MessageRole.SYSTEM }
        if (firstSystemIndex >= 0) {
            val current = bounded[firstSystemIndex]
            val remainingChars = (maxTokens * 4 - bounded
                .filterIndexed { index, _ -> index != firstSystemIndex }
                .sumOf { it.toText().length })
                .coerceAtLeast(MIN_CORE_SYSTEM_CHARS)
            if (current.toText().length > remainingChars) {
                bounded = bounded.toMutableList().also {
                    it[firstSystemIndex] = UIMessage.system(compactText(current.toText(), remainingChars))
                }
                compactedCount += 1
            }
        }
    }

    return FinalCompanionContextResult(
        messages = bounded,
        estimatedTokens = estimateMessagesTokens(bounded),
        droppedMessages = dropped,
        compactedSystemMessages = compactedCount,
    )
}

private fun deduplicateSystemMessages(messages: List<UIMessage>): List<UIMessage> {
    val seen = hashSetOf<String>()
    return messages.filter { message ->
        if (message.role != MessageRole.SYSTEM) return@filter true
        val key = message.toText().trim().replace(Regex("\\s+"), " ")
        key.isBlank() || seen.add(key)
    }
}

private fun systemCharLimit(text: String, isFirstSystem: Boolean): Int = when {
    isFirstSystem -> CORE_SYSTEM_CHAR_LIMIT
    text.contains("commitment", ignoreCase = true) ||
        text.contains("concern", ignoreCase = true) ||
        text.contains("承诺") || text.contains("关注") -> COMMITMENT_CHAR_LIMIT
    text.contains("<lulu_memory", ignoreCase = true) ||
        text.contains("<companion_runtime", ignoreCase = true) ||
        text.contains("<companion_private_context", ignoreCase = true) ||
        text.contains("recent_digital_life", ignoreCase = true) -> MEMORY_RUNTIME_CHAR_LIMIT
    else -> OTHER_SYSTEM_CHAR_LIMIT
}

private fun compactText(text: String, maxChars: Int): String {
    if (text.length <= maxChars) return text
    val marker = "\n…[上下文已按本轮 token 预算压缩；完整内容仍保存在本地数据库]…\n"
    val usable = (maxChars - marker.length).coerceAtLeast(2)
    val head = (usable * 2) / 3
    val tail = usable - head
    return text.take(head) + marker + text.takeLast(tail)
}

private fun estimateMessagesTokens(messages: List<UIMessage>): Int =
    messages.sumOf { message ->
        val chars = message.toText().length
        if (chars == 0) 0 else ((chars + 3) / 4).coerceAtLeast(1)
    }

private fun ApiUsageSource.finalCompanionInputBudget(): Int = when (this) {
    ApiUsageSource.CHAT -> 15_000
    ApiUsageSource.PHONE -> 10_000
    ApiUsageSource.GAME -> 12_000
    ApiUsageSource.OTHER -> 12_000
}

private const val CORE_SYSTEM_CHAR_LIMIT = 24_000
private const val MIN_CORE_SYSTEM_CHARS = 8_000
private const val COMMITMENT_CHAR_LIMIT = 4_800
private const val MEMORY_RUNTIME_CHAR_LIMIT = 8_000
private const val OTHER_SYSTEM_CHAR_LIMIT = 4_800
