package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

/**
 * Compacts the freshly generated runtime snapshot by semantic fields, not by cutting
 * the whole prompt. Full data remains in the companion database; only the current
 * request view is reduced.
 */
object CompanionRuntimeCompactTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> = compactCompanionRuntimeMessages(messages)
}

internal fun compactCompanionRuntimeMessages(messages: List<UIMessage>): List<UIMessage> {
    val recentConversation = messages
        .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
        .takeLast(8)
        .joinToString("\n") { it.toText() }

    return messages.map { message ->
        if (message.role != MessageRole.SYSTEM) return@map message
        val original = message.toText()
        if (!original.contains("<companion_runtime", ignoreCase = true)) return@map message
        UIMessage(
            id = message.id,
            role = message.role,
            parts = listOf(UIMessagePart.Text(compactRuntime(original, recentConversation))),
            annotations = message.annotations,
            createdAt = message.createdAt,
            finishedAt = message.finishedAt,
            modelId = message.modelId,
            usage = message.usage,
            translation = message.translation,
        )
    }
}

private fun compactRuntime(text: String, recentConversation: String): String {
    val output = mutableListOf<String>()
    var section: String? = null
    var sectionItems = 0
    var skipContinuity = false

    text.lineSequence().forEach { raw ->
        val line = raw.trimEnd()
        val trimmed = line.trim()
        when {
            trimmed.startsWith("cross_modal_continuity") -> {
                section = "continuity"
                sectionItems = 0
                skipContinuity = false
                output += trimmed
            }
            section == "continuity" && trimmed.startsWith("- previous_") -> {
                val value = trimmed.substringAfter('=', "").trim()
                if (value.isNotBlank() && recentConversation.contains(value.take(120), ignoreCase = true)) {
                    skipContinuity = true
                } else if (!skipContinuity) {
                    output += trimmed.take(MAX_CONTINUITY_LINE)
                }
            }
            section == "continuity" && trimmed.startsWith("- instruction=") -> {
                if (!skipContinuity) output += "- continue naturally; do not recite this record"
                section = null
            }
            trimmed in LIST_SECTIONS -> {
                section = trimmed
                sectionItems = 0
                output += trimmed
            }
            section in LIST_SECTIONS && trimmed.startsWith("-") -> {
                if (sectionItems < maxItems(section)) {
                    output += compactListItem(section, trimmed)
                    sectionItems += 1
                }
            }
            section in LIST_SECTIONS && !trimmed.startsWith("-") -> {
                section = null
                output += compactOrdinaryLine(trimmed)
            }
            trimmed.startsWith("- relationship_narrative=") -> output += trimmed.take(360)
            trimmed.startsWith("- user_portrait=") -> output += trimmed.take(360)
            trimmed.startsWith("- interaction_understanding=") -> output += trimmed.take(320)
            trimmed.startsWith("- legacy_summary=") -> output += trimmed.take(220)
            trimmed.startsWith("- observed_trait=") ||
                trimmed.startsWith("- preference=") ||
                trimmed.startsWith("- boundary=") ||
                trimmed.startsWith("- recent_change=") ||
                trimmed.startsWith("- unresolved_matter=") -> {
                val key = trimmed.substringBefore('=')
                val already = output.count { it.trim().startsWith("$key=") }
                if (already < 2) output += trimmed.take(220)
            }
            else -> output += compactOrdinaryLine(trimmed)
        }
    }
    return output.filter(String::isNotBlank).joinToString("\n")
}

private fun compactListItem(section: String?, line: String): String = when (section) {
    "active_commitments:" -> line
        .replace(Regex("\\bid=[^ ]+\\s*"), "")
        .replace(Regex("\\slast_result=.*$"), "")
        .take(240)
    "active_concerns:" -> line.take(220)
    "recent_digital_life:" -> line
        .replace(Regex("\\sevidence=.*$"), "")
        .take(260)
    "perception_facts:" -> line.take(260)
    else -> line.take(220)
}

private fun compactOrdinaryLine(line: String): String = when {
    line.startsWith("persona_priority=") ->
        "persona_priority=user persona, relationship, worldview, style and boundaries remain highest priority"
    line.startsWith("Treat this runtime snapshot") ->
        "Use this as current runtime truth; never recite it or invent intimacy."
    else -> line.take(MAX_ORDINARY_LINE)
}

private fun maxItems(section: String?): Int = when (section) {
    "active_concerns:", "active_commitments:" -> 4
    "active_self_goals:" -> 3
    "recent_digital_life:" -> 3
    "perception_facts:" -> 6
    else -> 4
}

private val LIST_SECTIONS = setOf(
    "active_concerns:",
    "active_commitments:",
    "active_self_goals:",
    "recent_digital_life:",
    "perception_facts:",
)

private const val MAX_CONTINUITY_LINE = 320
private const val MAX_ORDINARY_LINE = 520
