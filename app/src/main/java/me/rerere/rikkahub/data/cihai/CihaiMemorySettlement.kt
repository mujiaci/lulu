package me.rerere.rikkahub.data.cihai

import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.service.AffectiveMemoryCandidate
import me.rerere.rikkahub.data.service.AffectiveMemoryExtractor

@Serializable
data class CihaiMemorySettlementPolicy(
    val minimumBatchSize: Int = 8,
    val maximumWaitMillis: Long = 6 * 60 * 60 * 1_000L,
    val maximumBatchSize: Int = 20,
)

fun selectDueCihaiMemoryBatch(
    queue: List<CihaiMemoryQueueItem>,
    assistantId: String,
    nowMillis: Long,
    policy: CihaiMemorySettlementPolicy = CihaiMemorySettlementPolicy(),
): List<CihaiMemoryQueueItem> {
    if (assistantId.isBlank() || policy.maximumBatchSize <= 0) return emptyList()

    val dueItems = queue
        .asSequence()
        .filter { item ->
            item.entryId.isNotBlank() &&
                item.assistantId == assistantId &&
                item.nextAttemptAt <= nowMillis
        }
        .sortedWith(compareBy<CihaiMemoryQueueItem> { it.enqueuedAt }.thenBy { it.entryId })
        .distinctBy { it.entryId }
        .toList()
    if (dueItems.isEmpty()) return emptyList()

    val thresholdReached = dueItems.size >= policy.minimumBatchSize.coerceAtLeast(1)
    val oldestItem = dueItems.first()
    val oldestWaitExpired = nowMillis >= oldestItem.enqueuedAt &&
        nowMillis - oldestItem.enqueuedAt >= policy.maximumWaitMillis.coerceAtLeast(0L)
    if (!thresholdReached && !oldestWaitExpired) return emptyList()

    return dueItems.take(policy.maximumBatchSize)
}

object CihaiMemorySettlement {
    fun buildPrompt(
        entries: List<CihaiEntry>,
        assistantName: String = "当前角色",
        assistantPersona: String = "",
    ): String {
        require(entries.map { it.assistantId }.distinct().size <= 1) {
            "A Cihai memory settlement batch must belong to one assistant"
        }
        val name = assistantName.ifBlank { "当前角色" }
        return buildString {
            appendLine("你是$name 的辞海长期记忆沉淀器。只返回 JSON：{\"memories\":[...]}，不要解释。")
            if (assistantPersona.isNotBlank()) {
                appendLine("角色设定摘要：${assistantPersona.trim().take(1_200)}")
            }
            appendLine(
                "只允许提炼以下长期价值：稳定用户事实、用户偏好、用户边界、明确承诺及结果、" +
                    "有用户证据的关系变化、高情绪共同事件、用户纠正。"
            )
            appendLine(
                "type 只使用 user_fact, user_preference, user_boundary, promise, relationship, " +
                    "shared_event, correction。"
            )
            appendLine("content 必须用$name 的第一人称“我”写成具体压缩记忆，并包含以后召回仍有价值的新事实。")
            appendLine(
                "每条必须在 sourceMessageNodeIds 中引用当前批次证据，格式只能是 cihai:<entryId>；" +
                    "evidenceMessageNodeIds 如提供也只能使用同一批次 ID。"
            )
            appendLine(
                "直接拒绝：工具 JSON、工具状态或 observation 原文；感知层/评估层/判断层/七层架构字段描述；" +
                    "通用 fallback；无新事实的元反思；无用户证据的关系升级；与用户无关的泛化阅读感悟。"
            )
            appendLine(
                "无合格记忆时返回 {\"memories\":[]}。候选字段沿用 AffectiveMemoryCandidate，其中至少提供 " +
                    "type, content, userSignal, sourceMessageNodeIds, evidenceMessageNodeIds。"
            )
            appendLine("<cihai_batch>")
            entries.sortedBy { it.createdAt }.forEach { entry ->
                appendLine("[cihai:${entry.id}] kind=${entry.kind.name} title=${entry.title.trim().take(200)}")
                appendLine(entry.content.trim().take(1_200))
                entry.sourceExcerpt?.trim()?.takeIf { it.isNotBlank() }?.let { excerpt ->
                    appendLine("sourceExcerpt=${excerpt.take(500)}")
                }
            }
            append("</cihai_batch>")
        }
    }

    fun parseCandidates(rawText: String): List<AffectiveMemoryCandidate> =
        AffectiveMemoryExtractor.parseExtractionResult(rawText).memories

    fun parseAndValidateCandidates(
        rawText: String,
        assistantId: String,
        entries: List<CihaiEntry>,
    ): List<AffectiveMemoryCandidate> = validateCandidates(
        assistantId = assistantId,
        entries = entries,
        candidates = parseCandidates(rawText),
    )

    fun validateCandidates(
        assistantId: String,
        entries: List<CihaiEntry>,
        candidates: List<AffectiveMemoryCandidate>,
    ): List<AffectiveMemoryCandidate> {
        if (assistantId.isBlank()) return emptyList()

        val batchEntriesByEvidenceId = entries
            .asSequence()
            .filter { it.assistantId == assistantId && it.id.isNotBlank() }
            .associateBy { it.evidenceId }
        if (batchEntriesByEvidenceId.isEmpty()) return emptyList()

        val acceptedByText = linkedMapOf<String, AffectiveMemoryCandidate>()
        candidates.forEach { candidate ->
            val normalized = candidate.normalized()
            val sourceIds = normalized.sourceMessageNodeIds
            val evidenceIds = normalized.evidenceMessageNodeIds
            val allEvidenceIds = (sourceIds + evidenceIds).distinct()
            if (sourceIds.isEmpty() || allEvidenceIds.any { it !in batchEntriesByEvidenceId }) {
                return@forEach
            }

            val referencedEntries = allEvidenceIds.mapNotNull(batchEntriesByEvidenceId::get)
            if (!normalized.isDurableCihaiCandidate(referencedEntries)) return@forEach

            val candidateWithEvidence = normalized.copy(
                sourceMessageNodeIds = sourceIds.distinct(),
                evidenceMessageNodeIds = if (evidenceIds.isEmpty()) sourceIds.distinct() else evidenceIds.distinct(),
            )
            val normalizedText = candidateWithEvidence.content.normalizedMemoryText()
            if (normalizedText.isBlank()) return@forEach

            val previous = acceptedByText[normalizedText]
            acceptedByText[normalizedText] = if (previous == null) {
                candidateWithEvidence
            } else {
                previous.copy(
                    sourceMessageNodeIds = (previous.sourceMessageNodeIds +
                        candidateWithEvidence.sourceMessageNodeIds).distinct(),
                    evidenceMessageNodeIds = (previous.evidenceMessageNodeIds +
                        candidateWithEvidence.evidenceMessageNodeIds).distinct(),
                )
            }
        }
        return acceptedByText.values.toList()
    }
}

private val CihaiEntry.evidenceId: String
    get() = "cihai:$id"

private fun AffectiveMemoryCandidate.isDurableCihaiCandidate(
    referencedEntries: List<CihaiEntry>,
): Boolean {
    if (type.lowercase() !in ALLOWED_MEMORY_TYPES) return false
    if (!content.trim().startsWith("我")) return false

    val inspectedText = listOfNotNull(
        content,
        title,
        roleFeeling,
        bodySense,
        unspokenThought,
        userSignal,
        relationshipEffect,
        embeddingText,
    ).joinToString("\n")
    if (inspectedText.looksLikeToolPayload() || inspectedText.containsTraceDescription()) return false
    if (content.isGenericFallbackOrMetaReflection()) return false

    val normalizedType = type.lowercase()
    val needsUserSignal = normalizedType in USER_EVIDENCE_MEMORY_TYPES ||
        inspectedText.containsRelationshipUpgrade()
    if (needsUserSignal && userSignal.isNullOrBlank()) return false
    if (normalizedType == "shared_event" &&
        importance < 4 &&
        roleFeeling.isNullOrBlank() &&
        unspokenThought.isNullOrBlank()
    ) {
        return false
    }

    val readingOnlyEvidence = referencedEntries.isNotEmpty() &&
        referencedEntries.all { it.kind == CihaiEntryKind.READING_NOTE }
    if (readingOnlyEvidence && userSignal.isNullOrBlank()) return false

    return true
}

private fun String.looksLikeToolPayload(): Boolean {
    val compact = trim()
    if ((compact.startsWith("{") || compact.startsWith("[")) &&
        Regex("\"[^\"]+\"\\s*:").containsMatchIn(compact)
    ) {
        return true
    }
    return TOOL_PAYLOAD_MARKERS.any { marker -> contains(marker, ignoreCase = true) }
}

private fun String.containsTraceDescription(): Boolean =
    TRACE_MARKERS.any { marker -> contains(marker, ignoreCase = true) }

private fun String.isGenericFallbackOrMetaReflection(): Boolean {
    val compact = trim().replace(Regex("\\s+"), " ")
    return GENERIC_META_MARKERS.any { marker -> compact.contains(marker, ignoreCase = true) }
}

private fun String.containsRelationshipUpgrade(): Boolean =
    RELATIONSHIP_UPGRADE_MARKERS.any { marker -> contains(marker, ignoreCase = true) }

private fun String.normalizedMemoryText(): String = lowercase()
    .replace(Regex("[\\p{P}\\p{S}\\s]+"), "")

private val ALLOWED_MEMORY_TYPES = setOf(
    "user_fact",
    "user_preference",
    "user_boundary",
    "promise",
    "relationship",
    "shared_event",
    "correction",
)

private val USER_EVIDENCE_MEMORY_TYPES = setOf(
    "user_fact",
    "user_preference",
    "user_boundary",
    "promise",
    "relationship",
    "shared_event",
    "correction",
)

private val TOOL_PAYLOAD_MARKERS = listOf(
    "tool_result",
    "function_call",
    "requested_tools=",
    "available_requested_tools=",
    "missing_requested_tools=",
    "observation=",
    "原始 observation",
    "工具 JSON",
    "工具状态",
    "请求参数",
    "\"success\"",
    "\"arguments\"",
)

private val TRACE_MARKERS = listOf(
    "感知层",
    "意义评估层",
    "评估层",
    "判断层",
    "动态判断层",
    "行动实现层",
    "状态生成层",
    "七层架构",
    "seven-layer trace",
    "perception=",
    "evaluation=",
    "judgment=",
)

private val GENERIC_META_MARKERS = listOf(
    "我整理了记忆",
    "我记录了这次",
    "我做了一次反思",
    "我完成了沉淀",
    "这是一条记忆",
    "之后可以参考",
    "以后可以参考",
    "继续观察",
    "等待下一次",
    "下一轮判断",
    "等下次再判断",
    "我先保持克制",
    "我先不打扰",
    "我会继续等待",
    "我应该参考这次判断",
)

private val RELATIONSHIP_UPGRADE_MARKERS = listOf(
    "更亲密",
    "更信任",
    "关系升级",
    "亲密度提升",
    "信任加深",
    "closer relationship",
    "trust deepened",
)
