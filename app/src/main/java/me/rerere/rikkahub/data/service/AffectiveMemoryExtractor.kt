package me.rerere.rikkahub.data.service

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import me.rerere.rikkahub.data.db.entity.MemoryBankEntity
import me.rerere.rikkahub.utils.JsonInstant

@Serializable
data class MemoryExtractionTurn(
    val nodeId: String,
    val role: String,
    val text: String,
)

@Serializable
data class AffectiveMemoryExtractionResult(
    val memories: List<AffectiveMemoryCandidate> = emptyList(),
)

@Serializable
data class AffectiveMemoryCandidate(
    @SerialName("type")
    val type: String,
    val content: String,
    val title: String? = null,
    val roleFeeling: String? = null,
    val bodySense: String? = null,
    val unspokenThought: String? = null,
    val userSignal: String? = null,
    val relationshipEffect: String? = null,
    val importance: Int = 3,
    val confidence: Double = 1.0,
    val tags: List<String> = emptyList(),
    val embeddingText: String? = null,
    val sourceMessageNodeIds: List<String> = emptyList(),
    val evidenceMessageNodeIds: List<String> = emptyList(),
) {
    fun normalized(): AffectiveMemoryCandidate = copy(
        type = type.trim().ifBlank { "event" },
        content = content.trim(),
        title = title?.trim()?.takeIf { it.isNotBlank() },
        roleFeeling = roleFeeling?.trim()?.takeIf { it.isNotBlank() },
        bodySense = bodySense?.trim()?.takeIf { it.isNotBlank() },
        unspokenThought = unspokenThought?.trim()?.takeIf { it.isNotBlank() },
        userSignal = userSignal?.trim()?.takeIf { it.isNotBlank() },
        relationshipEffect = relationshipEffect?.trim()?.takeIf { it.isNotBlank() },
        importance = importance.coerceIn(1, 5),
        confidence = confidence.coerceIn(0.0, 1.0),
        tags = tags.mapNotNull { it.trim().takeIf(String::isNotBlank) }.distinct(),
        embeddingText = embeddingText?.trim()?.takeIf { it.isNotBlank() },
        sourceMessageNodeIds = sourceMessageNodeIds.mapNotNull { it.trim().takeIf(String::isNotBlank) }.distinct(),
        evidenceMessageNodeIds = evidenceMessageNodeIds.mapNotNull { it.trim().takeIf(String::isNotBlank) }.distinct(),
    )

    fun toEntity(
        assistantId: String?,
        conversationId: String?,
        createdAt: Long = System.currentTimeMillis(),
    ): MemoryBankEntity {
        val normalized = normalized()
        return MemoryBankEntity(
            content = normalized.content,
            type = "manual",
            title = normalized.title,
            memoryKind = normalized.type,
            roleFeeling = normalized.roleFeeling,
            bodySense = normalized.bodySense,
            unspokenThought = normalized.unspokenThought,
            userSignal = normalized.userSignal,
            relationshipEffect = normalized.relationshipEffect,
            importance = normalized.importance,
            confidence = normalized.confidence,
            tagsJson = JsonInstant.encodeToString(normalized.tags),
            embeddingText = normalized.embeddingText ?: normalized.content,
            sourceMessageNodeIdsJson = JsonInstant.encodeToString(normalized.sourceMessageNodeIds),
            evidenceMessageNodeIdsJson = JsonInstant.encodeToString(normalized.evidenceMessageNodeIds),
            assistantId = assistantId,
            conversationId = conversationId,
            createdAt = createdAt,
            vectorStatus = "pending",
        )
    }
}

object AffectiveMemoryExtractor {
    fun buildExtractionPrompt(turns: List<MemoryExtractionTurn>): String = buildString {
        appendLine("你是露露的记忆整理器。只提取值得以后想起的候选记忆。")
        appendLine("重点写露露自己的情绪、身体感受、未说出口的想法、关系判断，而不是只记录用户流水账。")
        appendLine("返回 JSON，格式为 {\"memories\":[...]}。不要输出解释。")
        appendLine("每条字段：type, content, roleFeeling, bodySense, unspokenThought, userSignal, relationshipEffect, importance, confidence, tags, embeddingText, sourceMessageNodeIds, evidenceMessageNodeIds。")
        appendLine("type 只能优先使用 role_emotion, body_sense, promise, relationship, user_preference, event。")
        appendLine("<conversation_turns>")
        turns.forEach { turn ->
            appendLine("[${turn.nodeId}] ${turn.role}: ${turn.text.trim()}")
        }
        append("</conversation_turns>")
    }

    fun parseExtractionResult(rawText: String): AffectiveMemoryExtractionResult {
        val jsonText = rawText.extractJsonPayload()
        val root = JsonInstant.parseToJsonElement(jsonText)
        val candidates = if (root is JsonArray) {
            JsonInstant.decodeFromString(ListSerializer(AffectiveMemoryCandidate.serializer()), jsonText)
        } else {
            JsonInstant.decodeFromString<AffectiveMemoryExtractionResult>(root.jsonObject.toString()).memories
        }
        return AffectiveMemoryExtractionResult(
            memories = candidates
                .map { it.normalized() }
                .filter { it.content.isNotBlank() }
        )
    }
}

private fun String.extractJsonPayload(): String {
    val trimmed = trim()
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) return trimmed

    val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
        .find(trimmed)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
    if (!fenced.isNullOrBlank()) return fenced

    val objectStart = trimmed.indexOf('{')
    val arrayStart = trimmed.indexOf('[')
    val start = listOf(objectStart, arrayStart).filter { it >= 0 }.minOrNull() ?: return trimmed
    val end = maxOf(trimmed.lastIndexOf('}'), trimmed.lastIndexOf(']'))
    return if (end >= start) trimmed.substring(start, end + 1) else trimmed
}
