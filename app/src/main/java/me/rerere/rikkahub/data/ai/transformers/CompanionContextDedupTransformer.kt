package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage

/**
 * Keeps transient prompt context as replaceable snapshots instead of chat history.
 *
 * Several input transformers inject current runtime, presence, study and profile
 * blocks on every request. Those blocks are useful for the current request but
 * must never accumulate across turns. Running this transformer both before and
 * after the input pipeline means old persisted copies are removed first and the
 * newest freshly generated snapshot wins at the end.
 */
object CompanionContextDedupTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> = deduplicateTransientCompanionContext(messages)
}

internal fun deduplicateTransientCompanionContext(messages: List<UIMessage>): List<UIMessage> {
    if (messages.size < 2) return messages

    val seenKinds = mutableSetOf<TransientContextKind>()
    val seenExactSystemBlocks = mutableSetOf<String>()
    val keptReversed = ArrayList<UIMessage>(messages.size)

    messages.asReversed().forEach { message ->
        if (message.role != MessageRole.SYSTEM) {
            keptReversed += message
            return@forEach
        }

        val text = message.toText().trim()
        if (text.isBlank()) {
            keptReversed += message
            return@forEach
        }

        val kind = transientContextKind(text)
        if (kind != null) {
            if (seenKinds.add(kind)) keptReversed += message
            return@forEach
        }

        // Static system prompts and world-book injections are allowed, but an
        // identical block only needs to be sent once. Keep the latest instance.
        val exactKey = text.normalizedSystemBlockKey()
        if (seenExactSystemBlocks.add(exactKey)) keptReversed += message
    }

    return keptReversed.asReversed()
}

private enum class TransientContextKind {
    COMPANION_RUNTIME,
    COMPANION_PRIVATE_CONTEXT,
    COMPANION_PRESENCE_CONTRACT,
    PRIVATE_USER_PROFILE,
    STUDY_STATE,
    TIME_REMINDER,
    ROLLING_SUMMARY,
    PROMPT_INJECTION_PLANNER,
}

private fun transientContextKind(text: String): TransientContextKind? {
    val lower = text.lowercase()
    return when {
        "<companion_runtime" in lower -> TransientContextKind.COMPANION_RUNTIME
        "<companion_private_context" in lower -> TransientContextKind.COMPANION_PRIVATE_CONTEXT
        "<companion_presence_contract" in lower -> TransientContextKind.COMPANION_PRESENCE_CONTRACT
        "<private_user_profile" in lower -> TransientContextKind.PRIVATE_USER_PROFILE
        "<study_state" in lower || "<study_context" in lower -> TransientContextKind.STUDY_STATE
        "<time_reminder" in lower || lower.startsWith("current time:") || lower.startsWith("当前时间：") ->
            TransientContextKind.TIME_REMINDER
        "<rolling_summary" in lower || lower.startsWith("滚动摘要：") -> TransientContextKind.ROLLING_SUMMARY
        "<prompt_injection_planner" in lower || "本轮可用表达池" in text ->
            TransientContextKind.PROMPT_INJECTION_PLANNER
        else -> null
    }
}

private fun String.normalizedSystemBlockKey(): String =
    replace("\r\n", "\n")
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .joinToString("\n")
