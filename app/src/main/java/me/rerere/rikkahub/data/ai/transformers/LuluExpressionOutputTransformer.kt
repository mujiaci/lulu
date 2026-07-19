package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.companion.CompanionModelPresence
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.uuid.Uuid

const val LULU_PRESENCE_METADATA_TYPE = "lulu_presence"
const val LULU_BUBBLE_SEGMENT_METADATA_TYPE = "lulu_bubble_segment"

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
    val segments = splitCompanionExpressionBubbles(textPart.text)
    if (segments.size <= 1) return withPresence

    val splitMessages = segments.mapIndexed { index, segment ->
        last.copy(
            id = if (index == 0) last.id else Uuid.random(),
            parts = listOf(textPart.copy(text = segment)),
            annotations = last.annotations + UIMessageAnnotation.Metadata(
                type = LULU_BUBBLE_SEGMENT_METADATA_TYPE,
                data = buildJsonObject {
                    put("index", index)
                    put("count", segments.size)
                },
            ),
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
    "mood", "emotion", "心情", "情绪" -> "mood"
    "body_state", "body state", "body", "身体状态", "具身状态" -> "body_state"
    "mind_state", "mind state", "mind", "精神状态", "注意状态" -> "mind_state"
    "activity_mode", "activity mode", "mode", "行动状态", "活动状态" -> "activity_mode"
    "user_state", "user state", "用户状态", "醒睡状态" -> "user_state"
    "emoji", "表情", "表情符号" -> "emoji"
    "sticker", "贴纸", "表情意图" -> "sticker"
    "bubble_pacing", "bubble pacing", "pacing", "气泡节奏" -> "bubble_pacing"
    else -> null
}

fun List<UIMessage>.companionModelPresence(): CompanionModelPresence? =
    drop(indexOfLast { message -> message.role == MessageRole.USER } + 1)
        .asReversed()
        .asSequence()
        .mapNotNull { message -> message.luluPresenceMetadata() }
        .firstOrNull()
        ?.data
        ?.toCompanionModelPresence()
        ?: drop(indexOfLast { message -> message.role == MessageRole.USER } + 1)
            .asReversed()
            .asSequence()
            .flatMap { message -> message.parts.asReversed().asSequence() }
            .filterIsInstance<UIMessagePart.Tool>()
            .firstOrNull { it.toolName == "set_lulu_expression_state" }
            ?.input
            ?.let { input ->
                runCatching {
                    JsonInstant.parseToJsonElement(input).jsonObject.toCompanionModelPresence()
                }.getOrNull()
            }

private fun kotlinx.serialization.json.JsonObject.toCompanionModelPresence(): CompanionModelPresence? =
    CompanionModelPresence(
        statusText = this["status"]?.jsonPrimitive?.contentOrNull
            ?: this["status_text"]?.jsonPrimitive?.contentOrNull,
        description = this["description"]?.jsonPrimitive?.contentOrNull,
        innerThought = this["inner_voice"]?.jsonPrimitive?.contentOrNull,
        memoryThought = this["thought"]?.jsonPrimitive?.contentOrNull,
        mood = this["mood"]?.jsonPrimitive?.contentOrNull,
        bodyState = this["body_state"]?.jsonPrimitive?.contentOrNull,
        mindState = this["mind_state"]?.jsonPrimitive?.contentOrNull,
        activityMode = this["activity_mode"]?.jsonPrimitive?.contentOrNull,
        userState = this["user_state"]?.jsonPrimitive?.contentOrNull,
        emoji = this["emoji"]?.jsonPrimitive?.contentOrNull,
        sticker = this["sticker"]?.jsonPrimitive?.contentOrNull,
        bubblePacing = this["bubble_pacing"]?.jsonPrimitive?.contentOrNull,
    ).takeIf { presence ->
        presence.statusText.orEmpty().isNotBlank() ||
            presence.description.orEmpty().isNotBlank() ||
            presence.innerThought.orEmpty().isNotBlank() ||
            presence.memoryThought.orEmpty().isNotBlank() ||
            presence.mood.orEmpty().isNotBlank() ||
            presence.bodyState.orEmpty().isNotBlank() ||
            presence.mindState.orEmpty().isNotBlank() ||
            presence.activityMode.orEmpty().isNotBlank() ||
            presence.userState.orEmpty().isNotBlank() ||
            presence.emoji.orEmpty().isNotBlank() ||
            presence.sticker.orEmpty().isNotBlank() ||
            presence.bubblePacing.orEmpty().isNotBlank()
    }

internal const val COMPANION_INCOMPLETE_REPLY_MARKER =
    "（本轮回复生成不完整，请重试）"

private val COMPANION_PRIVATE_BLOCK_REGEX =
    Regex(
        "(?is)<\\s*(runtime_context|companion_runtime|private_user_profile|companion_private_context)\\b[^>]*>.*?</\\s*\\1\\s*>",
    )

private val COMPANION_INTERNAL_LEAK_MARKERS = listOf(
    "这只是后台表达方向",
    "这只是后台第一人称心声",
    "本轮可用表达池",
    "表达池只是表达层",
    "用户资料（只作为理解用户",
    "用户资料（只用于稳定理解用户",
    "不要逐字复述这些标签",
    "本轮回复前已经完成的角色行动",
    "成功完成的动作不要重复调用",
)

private val COMPANION_INTERNAL_LINE_PREFIXES = listOf(
    "表达建议：",
    "动作描写建议：",
    "可参考素材：",
    "表情建议：",
    "贴纸/动作建议：",
    "身体表现：",
    "头像氛围：",
    "使用方式：",
    "本轮可用表达池：",
    "表达池只是表达层",
    "用户资料（只",
    "昵称：",
    "个人资料：",
    "我的外貌：",
    "聊天、称呼、关系感",
    "这只是后台表达方向",
    "这只是后台第一人称心声",
)

internal fun sanitizeLuluVisibleExpression(text: String): String {
    val hadPrivateContext = COMPANION_PRIVATE_BLOCK_REGEX.containsMatchIn(text)
    val withoutPrivateBlocks = COMPANION_PRIVATE_BLOCK_REGEX.replace(
        LULU_PRESENCE_BLOCK_REGEX.replace(text, ""),
        "",
    )
    val normalized = withoutPrivateBlocks
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()
    val lines = normalized.lines()
    val containsInternalLeak = COMPANION_INTERNAL_LEAK_MARKERS.any { marker ->
        normalized.contains(marker, ignoreCase = true)
    }
    val candidate = if (containsInternalLeak) {
        val lastInternalLine = lines.indexOfLast { line ->
            COMPANION_INTERNAL_LEAK_MARKERS.any { marker ->
                line.contains(marker, ignoreCase = true)
            } || COMPANION_INTERNAL_LINE_PREFIXES.any { prefix ->
                line.trim().startsWith(prefix)
            }
        }
        lines
            .drop(lastInternalLine + 1)
            .joinToString("\n")
            .trim()
            .ifBlank { COMPANION_INCOMPLETE_REPLY_MARKER }
    } else {
        normalized
    }

    val visibleCandidate = if (containsInternalLeak || hadPrivateContext) {
        candidate
            .lineSequence()
            .map { it.trim() }
            .filterNot { line ->
                line.isNotBlank() &&
                    COMPANION_INTERNAL_LINE_PREFIXES.any { prefix -> line.startsWith(prefix) }
            }
            .joinToString("\n")
    } else {
        candidate
    }
    return visibleCandidate
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
        .ifBlank {
            if (containsInternalLeak || hadPrivateContext) COMPANION_INCOMPLETE_REPLY_MARKER else ""
        }
}

internal fun splitCompanionExpressionBubbles(text: String): List<String> {
    val clean = text.trim()
    if (clean.isBlank()) return listOf(clean)
    if (clean.contains("```") || clean.contains("\n- ") || clean.contains("\n1. ")) return listOf(clean)

    val normalized = clean.replace("\r\n", "\n").replace('\r', '\n')
    val paragraphSegments = normalized.split(COMPANION_PARAGRAPH_BOUNDARY_REGEX)
        .map { it.trim() }
        .filter { it.isNotBlank() }
    val usesParagraphRhythm = paragraphSegments.size > 1 || clean.contains('\n')
    val roughSegments = if (usesParagraphRhythm) {
        segmentParagraphBubbles(paragraphSegments)
    } else {
        clean.split(COMPANION_SENTENCE_BOUNDARY_REGEX)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty {
                clean.split(Regex("(?<=[,\\uFF0C\\u3001\\uFF1B;])\\s*"))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            }
    }
    if (roughSegments.size <= 1) return listOf(clean)
    val maxBubbles = if (usesParagraphRhythm) 4 else 3

    return roughSegments
        .fold(mutableListOf<String>()) { acc, segment ->
            val last = acc.lastOrNull()
            when {
                last == null -> acc += segment
                segment.length < 5 && last.length + segment.length <= 22 -> acc[acc.lastIndex] = "$last$segment"
                acc.size >= maxBubbles -> acc[acc.lastIndex] = if (usesParagraphRhythm) {
                    "$last\n$segment"
                } else {
                    "$last$segment"
                }
                else -> acc += segment
            }
            acc
        }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .takeIf { it.size > 1 }
        ?: listOf(clean)
}

private fun segmentParagraphBubbles(paragraphs: List<String>): List<String> {
    val result = mutableListOf<String>()
    paragraphs.forEach { paragraph ->
        val paragraphBubbles = paragraph.splitParagraphBubbleUnits()
        paragraphBubbles.forEachIndexed { index, bubble ->
            val previous = result.lastOrNull()
            if (
                index == 0 &&
                previous != null &&
                bubble.startsWithDependentConnector() &&
                previous.visualBubbleLength() + bubble.visualBubbleLength() <= 48
            ) {
                result[result.lastIndex] = "$previous$bubble"
            } else {
                result += bubble
            }
        }
    }
    return result
}

private fun String.splitParagraphBubbleUnits(): List<String> {
    val units = split(COMPANION_SENTENCE_OR_LINE_BOUNDARY_REGEX)
        .map(String::trim)
        .filter(String::isNotBlank)
    if (units.size <= 1) return listOf(trim())

    return units.fold(mutableListOf<String>()) { bubbles, unit ->
        val previous = bubbles.lastOrNull()
        when {
            previous == null -> bubbles += unit
            (!previous.endsWithTerminalPunctuation() || unit.startsWithAnaphoricContinuation()) &&
                previous.visualBubbleLength() + unit.visualBubbleLength() <= 44 -> {
                bubbles[bubbles.lastIndex] = "$previous$unit"
            }
            else -> bubbles += unit
        }
        bubbles
    }
}

private fun String.startsWithDependentConnector(): Boolean = DEPENDENT_BUBBLE_CONNECTORS.any { startsWith(it) }

private fun String.startsWithAnaphoricContinuation(): Boolean =
    startsWith("这是") || startsWith("那是") || startsWith("这就是") || startsWith("那就是")

private fun String.endsWithTerminalPunctuation(): Boolean {
    val last = lastOrNull() ?: return false
    return last in setOf('.', '!', '?', '~', '～', '。', '！', '？', '…')
}

private fun String.visualBubbleLength(): Int = count { !it.isWhitespace() }

private val DEPENDENT_BUBBLE_CONNECTORS = listOf(
    "但", "不过", "可是", "只是", "而且", "所以", "因为",
)

private val COMPANION_PARAGRAPH_BOUNDARY_REGEX =
    Regex("(?:\\r?\\n)[ \\t]*(?:\\r?\\n)+")

private val COMPANION_SENTENCE_BOUNDARY_REGEX =
    Regex("(?<=[.!?~～。！？])\\s*|(?<=…)(?=\\s|$)\\s*")

private val COMPANION_SENTENCE_OR_LINE_BOUNDARY_REGEX =
    Regex("(?<=[.!?~～。！？])\\s*|(?<=…)(?=\\s|$)\\s*|\\n+")
