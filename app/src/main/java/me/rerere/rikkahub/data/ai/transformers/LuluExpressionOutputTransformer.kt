package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.uuid.Uuid

const val LULU_PRESENCE_METADATA_TYPE = "lulu_presence"

private val LULU_PRESENCE_BLOCK_REGEX =
    Regex("(?is)<\\s*lulu[_\\s-]*presence\\s*>\\s*([\\s\\S]*?)\\s*</\\s*lulu[_\\s-]*presence\\s*>")

object LuluExpressionOutputTransformer : OutputMessageTransformer {
    override suspend fun visualTransform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> = sanitizeLuluAssistantExpressionMessages(messages)

    override suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> = splitLuluAssistantExpressionMessages(messages)
}

private fun sanitizeLuluAssistantExpressionMessages(messages: List<UIMessage>): List<UIMessage> {
    return messages.map { message ->
        if (message.role != MessageRole.ASSISTANT) return@map message
        message.sanitizeLuluTextParts(dropBlankPresenceText = false).message
    }
}

internal fun splitLuluAssistantExpressionMessages(messages: List<UIMessage>): List<UIMessage> {
    val trailingPresence = mutableListOf<UIMessageAnnotation.Metadata>()
    val visibleMessages = messages.mapNotNull { message ->
        if (message.role != MessageRole.ASSISTANT) return@mapNotNull message
        val sanitized = message.sanitizeLuluTextParts(dropBlankPresenceText = true)
        if (sanitized.message.parts.isEmpty() && sanitized.presenceAnnotations.isNotEmpty()) {
            trailingPresence += sanitized.presenceAnnotations
            null
        } else {
            sanitized.message
        }
    }
    val withPresence = visibleMessages.attachTrailingLuluPresence(trailingPresence)
    val last = withPresence.lastOrNull() ?: return withPresence
    if (last.role != MessageRole.ASSISTANT) return withPresence
    val textPart = last.parts.singleOrNull() as? UIMessagePart.Text ?: return withPresence
    val segments = splitLuluExpressionBubbles(textPart.text)
    if (segments.size <= 1) return withPresence

    val splitMessages = segments.mapIndexed { index, segment ->
        last.copy(
            id = if (index == 0) last.id else Uuid.random(),
            parts = listOf(textPart.copy(text = segment)),
            usage = if (index == 0) last.usage else null,
            translation = null,
        )
    }
    return withPresence.dropLast(1) + splitMessages
}

private data class SanitizedLuluMessage(
    val message: UIMessage,
    val presenceAnnotations: List<UIMessageAnnotation.Metadata>,
)

private fun UIMessage.sanitizeLuluTextParts(dropBlankPresenceText: Boolean): SanitizedLuluMessage {
    val presenceAnnotations = parts
        .filterIsInstance<UIMessagePart.Text>()
        .mapNotNull { part -> extractLuluPresenceMetadata(part.text) }
    val newParts = parts.mapNotNull { part ->
        if (part !is UIMessagePart.Text) return@mapNotNull part
        val visibleText = sanitizeLuluVisibleExpression(part.text)
        when {
            visibleText.isNotBlank() -> part.copy(text = visibleText)
            dropBlankPresenceText && extractLuluPresenceMetadata(part.text) != null -> null
            else -> part.copy(text = visibleText)
        }
    }
    val nextAnnotations = if (presenceAnnotations.isEmpty()) {
        annotations
    } else {
        annotations + presenceAnnotations
    }
    return SanitizedLuluMessage(
        message = copy(parts = newParts, annotations = nextAnnotations),
        presenceAnnotations = presenceAnnotations,
    )
}

private fun List<UIMessage>.attachTrailingLuluPresence(
    annotations: List<UIMessageAnnotation.Metadata>,
): List<UIMessage> {
    if (annotations.isEmpty()) return this
    val targetIndex = indexOfLast { message ->
        message.role == MessageRole.ASSISTANT &&
            message.parts.singleOrNull() is UIMessagePart.Text &&
            message.toText().isNotBlank()
    }
    if (targetIndex < 0) return this
    return mapIndexed { index, message ->
        if (index == targetIndex) {
            message.copy(annotations = message.annotations + annotations)
        } else {
            message
        }
    }
}

internal fun extractLuluPresenceMetadata(text: String): UIMessageAnnotation.Metadata? {
    val block = LULU_PRESENCE_BLOCK_REGEX
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?: return null
    val values = mutableMapOf<String, String>()
    block.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .forEach { line ->
            val separator = listOf(":", "：").map { line.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: return@forEach
            val key = line.take(separator).trim().normalizeLuluPresenceKey() ?: return@forEach
            val value = line.drop(separator + 1).trim().trim('"', '\'', '“', '”', '‘', '’')
            if (value.isNotBlank()) values[key] = value
        }
    if (values.isEmpty()) return null
    return UIMessageAnnotation.Metadata(
        type = LULU_PRESENCE_METADATA_TYPE,
        data = buildJsonObject {
            values.forEach { (key, value) -> put(key, value) }
        },
    )
}

fun UIMessage.luluPresenceMetadata(): UIMessageAnnotation.Metadata? =
    annotations
        .asReversed()
        .asSequence()
        .filterIsInstance<UIMessageAnnotation.Metadata>()
        .firstOrNull { it.type == LULU_PRESENCE_METADATA_TYPE }
        ?: parts
            .asReversed()
            .asSequence()
            .filterIsInstance<UIMessagePart.Text>()
            .mapNotNull { part -> extractLuluPresenceMetadata(part.text) }
            .firstOrNull()

private fun String.normalizeLuluPresenceKey(): String? = when (trim().lowercase()) {
    "status", "status_text", "状态", "状态栏", "当前状态" -> "status"
    "description", "scene", "self_scene", "状态描写", "状态描述", "场景", "动作", "姿态" -> "description"
    "inner", "inner_voice", "inner voice", "心声", "内心", "没说出口", "未说出口", "未说出口的想法" -> "inner_voice"
    "thought", "memory_thought", "想法", "记住", "记忆", "短期想法" -> "thought"
    else -> null
}

internal fun sanitizeLuluVisibleExpression(text: String): String {
    val withoutPresenceBlocks = text
        .replace(LULU_PRESENCE_BLOCK_REGEX, "")
        .trim()
    val internalPrefixes = listOf(
        "表达建议：",
        "动作描写建议：",
        "可参考素材：",
        "表情建议：",
        "贴纸/动作建议：",
        "身体表现：",
        "头像氛围：",
        "使用方式：",
    )
    return withoutPresenceBlocks
        .lineSequence()
        .map { it.trim() }
        .filter { line -> line.isNotBlank() && internalPrefixes.none { prefix -> line.startsWith(prefix) } }
        .joinToString("\n")
        .trim()
}

private fun splitLuluExpressionBubbles(text: String): List<String> {
    val clean = text.trim()
    if (clean.isBlank()) return listOf(clean)
    if (clean.contains("```") || clean.contains("\n- ") || clean.contains("\n1. ")) return listOf(clean)

    val paragraphSegments = clean.split(Regex("\\n\\s*\\n+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
    val roughSegments = if (paragraphSegments.size > 1) {
        paragraphSegments
    } else {
        clean.split(Regex("(?<=[.!?~\\u3002\\uFF01\\uFF1F\\u2026])\\s*"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty {
                clean.split(Regex("(?<=[,\\uFF0C\\u3001\\uFF1B;])\\s*"))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            }
    }
    if (roughSegments.size <= 1) return listOf(clean)

    return roughSegments
        .fold(mutableListOf<String>()) { acc, segment ->
            val last = acc.lastOrNull()
            when {
                last == null -> acc += segment
                segment.length < 5 && last.length + segment.length <= 22 -> acc[acc.lastIndex] = "$last$segment"
                acc.size >= 3 -> acc[acc.lastIndex] = "$last$segment"
                else -> acc += segment
            }
            acc
        }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .takeIf { it.size > 1 }
        ?: listOf(clean)
}
