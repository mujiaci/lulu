package me.rerere.rikkahub.data.service

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import me.rerere.rikkahub.data.db.entity.MemoryBankEntity
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.data.companion.CompanionStateHistoryEntry

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

internal fun AffectiveMemoryCandidate.isDurableMemoryCandidate(): Boolean {
    val normalized = normalized()
    if (normalized.type.lowercase() !in DURABLE_MEMORY_TYPES) return false
    if (normalized.sourceMessageNodeIds.isEmpty() && normalized.evidenceMessageNodeIds.isEmpty()) return false
    if (!normalized.content.hasFirstPersonVoice()) return false

    val inspectedText = listOfNotNull(
        normalized.content,
        normalized.title,
        normalized.roleFeeling,
        normalized.bodySense,
        normalized.unspokenThought,
        normalized.userSignal,
        normalized.relationshipEffect,
        normalized.embeddingText,
    ).joinToString("\n")
    if (inspectedText.looksLikeRawToolOrTraceDump()) return false
    if (GENERIC_META_MEMORY_MARKERS.any { inspectedText.contains(it, ignoreCase = true) }) return false
    if (normalized.userSignal.isNullOrBlank()) return false
    return true
}

internal fun String.normalizedMemoryIdentity(): String = lowercase()
    .replace(Regex("[\\p{P}\\p{S}\\s]+"), "")

private fun AffectiveMemoryCandidate.hasAffectiveSummary(): Boolean =
    !roleFeeling.isNullOrBlank() ||
        !bodySense.isNullOrBlank() ||
        !unspokenThought.isNullOrBlank() ||
        !userSignal.isNullOrBlank() ||
        !relationshipEffect.isNullOrBlank()

private fun AffectiveMemoryCandidate.toDisplayMemoryContent(): String {
    val normalized = normalized()
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
    if (compact.hasFirstPersonVoice()) return compact
    return "我记得：$compact"
}

private fun String.hasFirstPersonVoice(): Boolean {
    val compact = trimStart()
    return FIRST_PERSON_PREFIXES.any { prefix -> compact.startsWith(prefix, ignoreCase = true) }
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
        assistantName: String = "当前角色",
        assistantPersona: String = "",
        stateHistory: List<CompanionStateHistoryEntry> = emptyList(),
    ): String = buildString {
        val name = assistantName.ifBlank { "当前角色" }
        appendLine("你是$name 的记忆整理器。只提取值得以后想起的候选记忆。")
        if (assistantPersona.isNotBlank()) {
            appendLine("角色设定摘要：${assistantPersona.take(1200)}")
        }
        appendLine("只保存以后召回仍有价值的新事实：稳定用户事实、用户偏好、用户边界、明确承诺及结果、有用户证据的关系变化、高情绪共同事件、用户纠正。")
        appendLine("content 必须是$name 第一人称的自然完整记忆，不要拼接字段标签，也不要粘贴工具 JSON、学习计划原文、单词表、长列表或完整 observation。")
        appendLine("所有 content、roleFeeling、bodySense、unspokenThought、relationshipEffect、embeddingText 都必须代入$name，用第一人称“我”来总结；不要写成“${name}觉得”“角色认为”“助手记录”这类旁白或第三人称。")
        appendLine("如果原文只是英文单词、工具结果、日程 JSON 或流水账，除非能提炼出用户偏好/承诺/关系变化/角色心声，否则不要写入 memories。")
        appendLine("unspokenThought 必须贴合$name 的人设、语言习惯和关系位置；不要写成旁白腔、客服腔或通用模板。")
        appendLine("unspokenThought 要尽量具体：写$name 当时的猜测、顾虑、符合人设但没有说出口的意图、想做但暂时压住的动作、对用户真实状态的判断。不要默认亲密动作，也不要只写“很担心”这类空泛短句。")
        appendLine("返回 JSON，格式为 {\"memories\":[...]}。不要输出解释。")
        appendLine("每条字段：type, content, roleFeeling, bodySense, unspokenThought, userSignal, relationshipEffect, importance, confidence, tags, embeddingText, sourceMessageNodeIds, evidenceMessageNodeIds, relatedMemoryIds, people, topics, supersededByMemoryId, correctedAt。")
        appendLine("type 只能使用 user_fact, user_preference, user_boundary, promise, relationship, shared_event, correction。")
        appendLine("每条必须提供 sourceMessageNodeIds 或 evidenceMessageNodeIds，并用 userSignal 简述用户原话或真实工具结果证据；无新事实时返回空 memories。")
        val visibleStateHistory = stateHistory.filter { entry -> entry.toExtractionContext().isNotBlank() }
        if (visibleStateHistory.isNotEmpty()) {
            appendLine("<character_state_history>")
            appendLine("以下是角色当时真实保存的状态栏与没说出口内容，只用于理解语境和补充第一人称感受；仍然必须用 conversation_turns 里的消息节点作为事实证据。")
            visibleStateHistory.takeLast(60).forEach { entry ->
                appendLine("[${entry.recordedAt}] ${entry.toExtractionContext()}")
            }
            appendLine("</character_state_history>")
        }
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

private fun CompanionStateHistoryEntry.toExtractionContext(): String = buildList {
    state.statusText.takeIf(String::isNotBlank)?.let { add("状态=$it") }
    state.mood.takeIf(String::isNotBlank)?.let { add("心情=$it") }
    state.bodyState.takeIf(String::isNotBlank)?.let { add("身体=$it") }
    state.mindState.takeIf(String::isNotBlank)?.let { add("精神=$it") }
    state.activityMode.takeIf(String::isNotBlank)?.let { add("活动=$it") }
    state.selfScene.takeIf(String::isNotBlank)?.let { add("此刻=$it") }
    state.innerThought.takeIf(String::isNotBlank)?.let { add("没说出口=$it") }
}.joinToString("；").take(1_200)

private val DURABLE_MEMORY_TYPES = setOf(
    "user_fact",
    "user_preference",
    "user_boundary",
    "promise",
    "relationship",
    "shared_event",
    "correction",
)

private val FIRST_PERSON_PREFIXES = listOf(
    "我",
    "咱",
    "本人",
    "本小姐",
    "本少爷",
    "本官",
    "本王",
    "本宫",
    "在下",
    "余",
    "吾",
    "I ",
    "I'm ",
    "I’m ",
)

private val GENERIC_META_MEMORY_MARKERS = listOf(
    "cihai_reflection",
    "我记得这件事。当时感觉",
    "复盘、收束、准备下一轮",
    "后续可复用的长期记忆",
    "感知世界包",
    "意义评估",
    "动态判断",
    "状态生成",
    "辞海记忆架构",
    "七层架构",
    "下一轮判断",
    "我完成了沉淀",
    "我整理了记忆",
    "以后可以参考",
    "等待下一次",
)

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
