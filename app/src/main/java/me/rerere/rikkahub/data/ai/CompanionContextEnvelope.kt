package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.limitContext
import me.rerere.rikkahub.data.model.Assistant

/** Single bounded hand-off between context producers and model generation. */
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

    // A rolling summary can arrive as either a persisted chat message or an injected
    // SYSTEM snapshot. Only the newest copy is useful; sending both wastes tokens and
    // can make the model treat the same old event as two independent facts.
    val latestRollingSummary = messages
        .asReversed()
        .firstOrNull { it.toText().isRollingSummaryText() }
        ?.toText()
        ?.trim()
        .orEmpty()

    val deduplicatedSystemMessages = deduplicateEnvelopeSystemMessages(
        messages.filter { message ->
            message.role == MessageRole.SYSTEM && !message.toText().isRollingSummaryText()
        },
    )
    val allHistoryBeforeDedup = messages.filter { message ->
        message.role != MessageRole.SYSTEM && !message.toText().isRollingSummaryText()
    }
    val allHistory = collapseAdjacentDuplicateHistory(allHistoryBeforeDedup)
    var history = allHistory.limitContext(configuredWindow)

    val systemText = deduplicatedSystemMessages.joinToString("\n\n") { it.toText() }
    val fixedText = listOf(
        characterCore,
        globalLorebook,
        roleLorebook,
        otherMandatoryPrompt,
        systemText,
        latestRollingSummary,
    ).filter(String::isNotBlank).joinToString("\n\n")
    val fixedTokens = estimateCompanionPromptTokens(fixedText)
    if (fixedTokens > budget.maxEstimatedInputTokens) {
        throw CompanionContextOverflowException(fixedTokens, budget.maxEstimatedInputTokens)
    }

    while (
        history.size > MIN_HISTORY_MESSAGES &&
        fixedTokens + estimateCompanionPromptTokens(history.joinToString("\n\n") { it.toText() }) >
        budget.maxEstimatedInputTokens
    ) {
        history = history.drop(1)
        while (history.firstOrNull()?.role == MessageRole.TOOL) history = history.drop(1)
    }

    val recentText = history.joinToString("\n\n") { it.toText() }
    val classified = classifyStructuredSystemContext(deduplicatedSystemMessages)
    val sections = listOf(
        section("角色核心", characterCore, if (characterCore.isBlank()) 0 else 1),
        section("全局世界书", globalLorebook, if (globalLorebook.isBlank()) 0 else 1),
        section("角色世界书", roleLorebook, if (roleLorebook.isBlank()) 0 else 1),
        section("最近消息", recentText, history.size),
        section("滚动摘要", latestRollingSummary, if (latestRollingSummary.isBlank()) 0 else 1),
        section("记忆", classified.memory, classified.messageCount("memory")),
        section("关系/状态", classified.relationshipState, classified.messageCount("relationship")),
        section("承诺/关注", classified.commitmentConcern, classified.messageCount("commitment")),
        section(
            "其他提示词",
            listOf(otherMandatoryPrompt, classified.other).filter(String::isNotBlank).joinToString("\n\n"),
            classified.messageCount("other"),
        ),
    )
    val summaryMessage = latestRollingSummary
        .takeIf { it.isNotBlank() }
        ?.let { summary -> UIMessage.system(summary) }
    return CompanionContextEnvelope(
        messages = buildList {
            addAll(deduplicatedSystemMessages)
            if (summaryMessage != null) add(summaryMessage)
            addAll(history)
        },
        droppedHistoryMessages =
            (allHistoryBeforeDedup.size - history.size).coerceAtLeast(0),
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

private fun classifyStructuredSystemContext(messages: List<UIMessage>): ClassifiedSystemContext {
    val groups = mutableMapOf<String, MutableList<String>>()
    messages.forEach { message ->
        val text = message.toText().trim()
        if (text.isBlank()) return@forEach
        if (text.contains("<companion_runtime", ignoreCase = true)) {
            val commitmentLines = extractRuntimeCommitmentLines(text)
            val relationshipText = stripRuntimeCommitmentLines(text)
            if (relationshipText.isNotBlank()) groups.getOrPut("relationship", ::mutableListOf) += relationshipText
            if (commitmentLines.isNotBlank()) groups.getOrPut("commitment", ::mutableListOf) += commitmentLines
            return@forEach
        }
        val key = when {
            text.contains("<rolling_summary", ignoreCase = true) -> "rolling"
            text.contains("<lulu_memory", ignoreCase = true) -> "memory"
            text.contains("<companion_private_context", ignoreCase = true) ||
                text.contains("<private_user_profile", ignoreCase = true) -> "relationship"
            text.contains("<commitment_context", ignoreCase = true) ||
                text.contains("<concern_context", ignoreCase = true) -> "commitment"
            else -> "other"
        }
        groups.getOrPut(key, ::mutableListOf) += text
    }
    fun content(key: String) = groups[key].orEmpty().joinToString("\n\n").trim()
    return ClassifiedSystemContext(
        rollingSummary = content("rolling"),
        memory = content("memory"),
        relationshipState = content("relationship"),
        commitmentConcern = content("commitment"),
        other = content("other"),
        counts = groups.mapValues { it.value.size },
    )
}

private fun extractRuntimeCommitmentLines(text: String): String {
    val lines = text.lineSequence().toList()
    val selected = mutableListOf<String>()
    var capture = false
    for (line in lines) {
        val trimmed = line.trim()
        when {
            trimmed == "active_concerns:" || trimmed == "active_commitments:" -> {
                capture = true
                selected += trimmed
            }
            capture && trimmed.startsWith("-") -> selected += trimmed
            capture -> capture = false
        }
    }
    return selected.joinToString("\n")
}

private fun stripRuntimeCommitmentLines(text: String): String {
    val kept = mutableListOf<String>()
    var skip = false
    for (line in text.lineSequence()) {
        val trimmed = line.trim()
        when {
            trimmed == "active_concerns:" || trimmed == "active_commitments:" -> skip = true
            skip && trimmed.startsWith("-") -> Unit
            else -> {
                skip = false
                kept += line
            }
        }
    }
    return kept.joinToString("\n").trim()
}

private fun deduplicateEnvelopeSystemMessages(messages: List<UIMessage>): List<UIMessage> {
    val seenKinds = mutableSetOf<String>()
    val seenExact = mutableSetOf<String>()
    val result = mutableListOf<UIMessage>()
    messages.asReversed().forEach { message ->
        val text = message.toText().trim()
        val kind = structuredKind(text)
        val keep = if (kind != null) seenKinds.add(kind) else seenExact.add(normalizeSystemText(text))
        if (keep) result += message
    }
    return result.asReversed()
}

/**
 * Collapses only adjacent, byte-equivalent conversational duplicates.
 *
 * We deliberately do not remove repeated ideas from distant turns: a user repeating a
 * request can be meaningful. Adjacent identical copies, however, are transport/UI retry
 * artefacts and should never consume model context twice. TOOL messages are left intact
 * because their ordering and multiplicity can be required by provider protocols.
 */
private fun collapseAdjacentDuplicateHistory(messages: List<UIMessage>): List<UIMessage> {
    if (messages.size < 2) return messages
    val result = ArrayList<UIMessage>(messages.size)
    messages.forEach { message ->
        val previous = result.lastOrNull()
        val duplicate = message.role != MessageRole.TOOL &&
            previous?.role == message.role &&
            normalizeConversationText(previous.toText()) == normalizeConversationText(message.toText())
        if (!duplicate) result += message
    }
    return result
}

private fun String.isRollingSummaryText(): Boolean {
    val normalized = trim()
    return normalized.contains("<rolling_summary", ignoreCase = true) ||
        normalized.startsWith("滚动摘要：") ||
        normalized.startsWith("rolling summary:", ignoreCase = true)
}

private fun structuredKind(text: String): String? = when {
    text.contains("<companion_runtime", ignoreCase = true) -> "runtime"
    text.contains("<companion_private_context", ignoreCase = true) -> "private_context"
    text.contains("<companion_presence_contract", ignoreCase = true) -> "presence_contract"
    text.contains("<private_user_profile", ignoreCase = true) -> "user_profile"
    text.contains("<rolling_summary", ignoreCase = true) -> "rolling"
    text.contains("<lulu_memory", ignoreCase = true) -> "memory"
    text.contains("<study_state", ignoreCase = true) || text.contains("<study_context", ignoreCase = true) -> "study"
    text.contains("<time_reminder", ignoreCase = true) -> "time"
    else -> null
}

private fun normalizeSystemText(text: String): String = text
    .lineSequence().map(String::trim).filter(String::isNotBlank).joinToString("\n")

private fun normalizeConversationText(text: String): String = text
    .replace("\r\n", "\n")
    .trim()

private fun section(label: String, text: String, messageCount: Int) = CompanionContextSection(
    label = label,
    estimatedTokens = estimateCompanionPromptTokens(text),
    messageCount = messageCount,
    charCount = text.length,
)

private fun ApiUsageSource.companionContextBudget(): CompanionContextBudget = when (this) {
    ApiUsageSource.CHAT -> CompanionContextBudget(maxHistoryMessages = 48, maxEstimatedInputTokens = 18_000)
    ApiUsageSource.PHONE -> CompanionContextBudget(maxHistoryMessages = 24, maxEstimatedInputTokens = 12_000)
    ApiUsageSource.GAME -> CompanionContextBudget(maxHistoryMessages = 32, maxEstimatedInputTokens = 15_000)
    ApiUsageSource.OTHER -> CompanionContextBudget(maxHistoryMessages = 30, maxEstimatedInputTokens = 15_000)
}

private fun estimateCompanionPromptTokens(text: String): Int =
    if (text.isBlank()) 0 else ((text.length + 3) / 4).coerceAtLeast(1)

private const val MIN_HISTORY_MESSAGES = 2