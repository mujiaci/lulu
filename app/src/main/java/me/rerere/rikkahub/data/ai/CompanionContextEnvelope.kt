package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.limitContext
import me.rerere.rikkahub.data.model.Assistant

/**
 * The single bounded hand-off between companion context producers and model generation.
 *
 * Callers may prepend structured SYSTEM messages, but only this assembler decides how
 * much conversational history reaches a model. Mandatory persona and global-world text
 * is measured separately and is never silently truncated.
 */
internal data class CompanionContextEnvelope(
    val messages: List<UIMessage>,
    val droppedHistoryMessages: Int,
    val estimatedInputTokens: Int,
    val budget: CompanionContextBudget,
    val sections: List<CompanionContextSection>,
)

internal data class CompanionContextBudget(
    val maxHistoryMessages: Int,
    val maxEstimatedInputTokens: Int,
)

internal data class CompanionContextSection(
    val label: String,
    val estimatedTokens: Int,
    val messageCount: Int,
    val charCount: Int,
)

internal class CompanionContextOverflowException(
    val estimatedTokens: Int,
    val allowedTokens: Int,
) : IllegalStateException(
    "当前角色的固定人设、全局世界书和必要状态约束约需 $estimatedTokens tokens，" +
        "超过本场景安全预算 $allowedTokens；这些内容不会被静默裁剪。请缩短固定设定或改用更大上下文模型。",
)

internal fun buildCompanionContextEnvelope(
    assistant: Assistant,
    source: ApiUsageSource,
    messages: List<UIMessage>,
    characterCore: String,
    globalLorebook: String,
    roleLorebook: String,
    otherMandatoryPrompt: String,
): CompanionContextEnvelope {
    val budget = source.companionContextBudget()
    val configuredWindow = assistant.contextMessageSize
        .takeIf { it > 0 }
        ?.coerceAtMost(budget.maxHistoryMessages)
        ?: budget.maxHistoryMessages
    val rollingSummaryMessages = messages.filter { message ->
        message.role != MessageRole.SYSTEM &&
            message.toText().contains("<rolling_summary", ignoreCase = true)
    }
    val systemMessages = messages.filter { it.role == MessageRole.SYSTEM }
    val allHistory = messages.filter { message ->
        message.role != MessageRole.SYSTEM && message !in rollingSummaryMessages
    }
    var history = allHistory.limitContext(configuredWindow)

    val systemText = systemMessages.joinToString("\n\n") { it.toText() }
    val rollingSummaryText = rollingSummaryMessages.joinToString("\n\n") { it.toText() }
    val fixedText = listOf(
        characterCore,
        globalLorebook,
        roleLorebook,
        otherMandatoryPrompt,
        systemText,
        rollingSummaryText,
    )
        .filter(String::isNotBlank)
        .joinToString("\n\n")
    val fixedTokens = estimateCompanionPromptTokens(fixedText)
    if (fixedTokens > budget.maxEstimatedInputTokens) {
        throw CompanionContextOverflowException(
            estimatedTokens = fixedTokens,
            allowedTokens = budget.maxEstimatedInputTokens,
        )
    }

    while (
        history.size > MIN_HISTORY_MESSAGES &&
        fixedTokens + estimateCompanionPromptTokens(history.joinToString("\n\n") { it.toText() }) >
        budget.maxEstimatedInputTokens
    ) {
        history = history.drop(1)
        while (history.firstOrNull()?.role == MessageRole.TOOL) {
            history = history.drop(1)
        }
    }

    val recentText = history.joinToString("\n\n") { it.toText() }
    val classified = classifyStructuredSystemContext(systemText)
    val sections = listOf(
        section("角色核心", characterCore, if (characterCore.isBlank()) 0 else 1),
        section("全局世界书", globalLorebook, if (globalLorebook.isBlank()) 0 else 1),
        section("角色世界书", roleLorebook, if (roleLorebook.isBlank()) 0 else 1),
        section("最近消息", recentText, history.size),
        section(
            "滚动摘要",
            listOf(rollingSummaryText, classified.rollingSummary)
                .filter(String::isNotBlank)
                .joinToString("\n\n"),
            rollingSummaryMessages.size + classified.messageCount("rolling"),
        ),
        section("记忆", classified.memory, classified.messageCount("memory")),
        section("关系/状态", classified.relationshipState, classified.messageCount("relationship")),
        section("承诺/关注", classified.commitmentConcern, classified.messageCount("commitment")),
        section(
            "其他提示词",
            listOf(otherMandatoryPrompt, classified.other).filter(String::isNotBlank).joinToString("\n\n"),
            classified.messageCount("other"),
        ),
    )
    return CompanionContextEnvelope(
        messages = systemMessages +
            rollingSummaryMessages.map { UIMessage.system(it.toText()) } +
            history,
        droppedHistoryMessages = (allHistory.size - history.size).coerceAtLeast(0),
        estimatedInputTokens = fixedTokens + estimateCompanionPromptTokens(recentText),
        budget = budget,
        sections = sections,
    )
}

private data class ClassifiedSystemContext(
    val rollingSummary: String = "",
    val memory: String = "",
    val relationshipState: String = "",
    val commitmentConcern: String = "",
    val other: String = "",
    val counts: Map<String, Int> = emptyMap(),
) {
    fun messageCount(key: String): Int = counts[key] ?: 0
}

private fun classifyStructuredSystemContext(text: String): ClassifiedSystemContext {
    if (text.isBlank()) return ClassifiedSystemContext()
    val blocks = text.split(Regex("(?=<[^>]+>)"))
    val groups = blocks.groupBy { block ->
        when {
            block.contains("<rolling_summary", ignoreCase = true) -> "rolling"
            block.contains("<lulu_memory", ignoreCase = true) -> "memory"
            block.contains("commitment", ignoreCase = true) ||
                block.contains("concern", ignoreCase = true) ||
                block.contains("承诺") ||
                block.contains("关注") -> "commitment"
            block.contains("<companion_runtime", ignoreCase = true) ||
                block.contains("<companion_private_context", ignoreCase = true) -> "relationship"
            else -> "other"
        }
    }
    fun content(key: String) = groups[key].orEmpty().joinToString("\n").trim()
    return ClassifiedSystemContext(
        rollingSummary = content("rolling"),
        memory = content("memory"),
        relationshipState = content("relationship"),
        commitmentConcern = content("commitment"),
        other = content("other"),
        counts = groups.mapValues { it.value.size },
    )
}

private fun section(label: String, text: String, messageCount: Int) = CompanionContextSection(
    label = label,
    estimatedTokens = estimateCompanionPromptTokens(text),
    messageCount = messageCount,
    charCount = text.length,
)

private fun ApiUsageSource.companionContextBudget(): CompanionContextBudget = when (this) {
    ApiUsageSource.CHAT -> CompanionContextBudget(maxHistoryMessages = 60, maxEstimatedInputTokens = 32_000)
    ApiUsageSource.PHONE -> CompanionContextBudget(maxHistoryMessages = 30, maxEstimatedInputTokens = 16_000)
    ApiUsageSource.GAME -> CompanionContextBudget(maxHistoryMessages = 40, maxEstimatedInputTokens = 20_000)
    ApiUsageSource.OTHER -> CompanionContextBudget(maxHistoryMessages = 36, maxEstimatedInputTokens = 20_000)
}

private fun estimateCompanionPromptTokens(text: String): Int =
    if (text.isBlank()) 0 else ((text.length + 3) / 4).coerceAtLeast(1)

private const val MIN_HISTORY_MESSAGES = 2
