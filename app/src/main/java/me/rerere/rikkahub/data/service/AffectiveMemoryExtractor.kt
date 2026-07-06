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
    val relatedMemoryIds: List<String> = emptyList(),
    val people: List<String> = emptyList(),
    val topics: List<String> = emptyList(),
    val supersededByMemoryId: String? = null,
    val correctedAt: Long? = null,
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
        relatedMemoryIds = relatedMemoryIds.mapNotNull { it.trim().takeIf(String::isNotBlank) }.distinct(),
        people = people.mapNotNull { it.trim().takeIf(String::isNotBlank) }.distinct(),
        topics = topics.mapNotNull { it.trim().takeIf(String::isNotBlank) }.distinct(),
        supersededByMemoryId = supersededByMemoryId?.trim()?.takeIf { it.isNotBlank() },
    )

    fun toEntity(
        assistantId: String?,
        conversationId: String?,
        createdAt: Long = System.currentTimeMillis(),
    ): MemoryBankEntity {
        val normalized = normalized()
        val displayContent = normalized.toDisplayMemoryContent()
        val embeddingText = normalized.embeddingText
            ?.takeUnless { it.looksLikeRawToolOrTraceDump() }
            ?: normalized.toEmbeddingMemoryText(displayContent)
        return MemoryBankEntity(
            content = displayContent,
            type = "message",
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
            embeddingText = embeddingText,
            sourceMessageNodeIdsJson = JsonInstant.encodeToString(normalized.sourceMessageNodeIds),
            evidenceMessageNodeIdsJson = JsonInstant.encodeToString(normalized.evidenceMessageNodeIds),
            relatedMemoryIdsJson = JsonInstant.encodeToString(normalized.relatedMemoryIds),
            peopleJson = JsonInstant.encodeToString(normalized.people),
            topicsJson = JsonInstant.encodeToString(normalized.topics),
            supersededByMemoryId = normalized.supersededByMemoryId,
            correctedAt = normalized.correctedAt,
            assistantId = assistantId,
            conversationId = conversationId,
            createdAt = createdAt,
            vectorStatus = "pending",
        )
    }
}

internal fun AffectiveMemoryCandidate.shouldSkipMemoryBankWrite(): Boolean {
    val normalized = normalized()
    if (normalized.content.isBlank()) return true
    if (normalized.content.looksLikeVocabularyDrill() && !normalized.hasAffectiveSummary()) return true
    return normalized.content.looksLikeRawToolOrTraceDump() && !normalized.hasAffectiveSummary()
}

private fun AffectiveMemoryCandidate.hasAffectiveSummary(): Boolean =
    !roleFeeling.isNullOrBlank() ||
        !bodySense.isNullOrBlank() ||
        !unspokenThought.isNullOrBlank() ||
        !userSignal.isNullOrBlank() ||
        !relationshipEffect.isNullOrBlank()

private fun AffectiveMemoryCandidate.toDisplayMemoryContent(): String {
    val normalized = normalized()
    val lines = buildList {
        normalized.unspokenThought?.let { add("没说出口：$it") }
        normalized.roleFeeling?.let { add("当时感觉：$it") }
        normalized.userSignal?.let { add("用户信号：$it") }
        normalized.relationshipEffect?.let { add("关系影响：$it") }
    }
    if (lines.isNotEmpty()) {
        return ("我记得这件事。" + lines.joinToString("；")).take(260)
    }
    if (normalized.content.looksLikeRawToolOrTraceDump()) {
        return "我记得当时做过一次工具观察和内部判断，但原始结果只适合作为证据回查，不直接当作记忆内容。"
    }
    return firstPersonSummary(normalized.content).take(260)
}

private fun AffectiveMemoryCandidate.toEmbeddingMemoryText(displayContent: String): String =
    listOfNotNull(
        displayContent,
        roleFeeling?.let { "roleFeeling=$it" },
        bodySense?.let { "bodySense=$it" },
        unspokenThought?.let { "unspokenThought=$it" },
        userSignal?.let { "userSignal=$it" },
        relationshipEffect?.let { "relationshipEffect=$it" },
        tags.takeIf { it.isNotEmpty() }?.joinToString(prefix = "tags=", separator = ","),
    ).joinToString("\n")

private fun firstPersonSummary(content: String): String {
    val compact = content.trim()
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
    if (compact.startsWith("我")) return compact
    return "我记得：$compact"
}

private fun String.looksLikeRawToolOrTraceDump(): Boolean {
    val markers = listOf(
        "tool_result[",
        "requested_tools=",
        "available_requested_tools=",
        "missing_requested_tools=",
        "Seven-layer trace",
        "Perception=",
        "study_app_local_store",
        "\"success\"",
        "\"undone_tasks\"",
    )
    return markers.any { contains(it, ignoreCase = true) }
}

private fun String.looksLikeVocabularyDrill(): Boolean {
    val words = Regex("[A-Za-z][A-Za-z'-]{2,}").findAll(this).map { it.value }.toList()
    if (words.size < 12) return false
    val uniqueRatio = words.distinctBy { it.lowercase() }.size.toDouble() / words.size
    val hasSentencePunctuation = contains("。") || contains("，") || contains(". ") || contains("?")
    return uniqueRatio > 0.75 && !hasSentencePunctuation
}

object AffectiveMemoryExtractor {
    fun buildExtractionPrompt(
        turns: List<MemoryExtractionTurn>,
        assistantName: String = "露露",
        assistantPersona: String = "",
    ): String = buildString {
        val name = assistantName.ifBlank { "当前角色" }
        appendLine("你是$name 的记忆整理器。只提取值得以后想起的候选记忆。")
        if (assistantPersona.isNotBlank()) {
            appendLine("角色设定摘要：${assistantPersona.take(1200)}")
        }
        appendLine("重点写$name 自己的情绪、身体感受、未说出口的想法、关系判断，而不是只记录用户流水账。")
        appendLine("content 必须是$name 第一人称的压缩记忆摘要，不要粘贴工具 JSON、学习计划原文、单词表、长列表或完整 observation。")
        appendLine("如果原文只是英文单词、工具结果、日程 JSON 或流水账，除非能提炼出用户偏好/承诺/关系变化/角色心声，否则不要写入 memories。")
        appendLine("unspokenThought 必须贴合$name 的人设、语言习惯和关系位置；不要写成旁白腔、客服腔或通用模板。")
        appendLine("unspokenThought 要尽量具体：写$name 当时的猜测、顾虑、想靠近但没有说出口的话、想做但暂时压住的动作、对用户真实状态的判断。不要只写“很担心”这类空泛短句。")
        appendLine("返回 JSON，格式为 {\"memories\":[...]}。不要输出解释。")
        appendLine("每条字段：type, content, roleFeeling, bodySense, unspokenThought, userSignal, relationshipEffect, importance, confidence, tags, embeddingText, sourceMessageNodeIds, evidenceMessageNodeIds, relatedMemoryIds, people, topics, supersededByMemoryId, correctedAt。")
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
