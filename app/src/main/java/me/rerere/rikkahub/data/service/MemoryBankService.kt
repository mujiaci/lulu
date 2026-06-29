package me.rerere.rikkahub.data.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import me.rerere.ai.provider.EmbeddingGenerationParams
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.rikkahub.data.db.dao.MemoryBankDAO
import me.rerere.rikkahub.data.db.entity.MemoryBankEntity
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.utils.JsonInstant
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MEMORY_DUPLICATE_VECTOR_THRESHOLD = 0.85

class MemoryBankService(
    private val memoryBankDAO: MemoryBankDAO,
    private val okHttpClient: OkHttpClient?,
    private val context: Context?,
    private val settingsStore: SettingsStore? = null,
    private val providerManager: ProviderManager? = null,
) {
    data class MemoryStats(
        val total: Int = 0,
        val messageCount: Int = 0,
        val summaryCount: Int = 0,
        val manualCount: Int = 0,
        val vectorizedCount: Int = 0,
        val pendingCount: Int = 0,
        val failedCount: Int = 0,
    )

    val recallCount: Int = 5

    suspend fun getAssistantIds(): List<String> = withContext(Dispatchers.IO) {
        memoryBankDAO.getDistinctAssistantIds()
    }

    suspend fun getStats(assistantId: String? = null): MemoryStats = withContext(Dispatchers.IO) {
        val total = if (assistantId != null) {
            memoryBankDAO.getCountByAssistant(assistantId)
        } else {
            memoryBankDAO.getTotalCount()
        }
        val messageCount = if (assistantId != null) {
            memoryBankDAO.getCountByAssistantAndType(assistantId, "message")
        } else {
            memoryBankDAO.getCountByType("message")
        }
        val summaryCount = memoryBankDAO.getSummaryCount()
        val manualCount = if (assistantId != null) {
            memoryBankDAO.getCountByAssistantAndType(assistantId, "manual")
        } else {
            memoryBankDAO.getCountByType("manual")
        }
        val vectorizedCount = if (assistantId != null) {
            0
        } else {
            memoryBankDAO.getCountByVectorStatus("done")
        }
        val pendingCount = if (assistantId != null) {
            0
        } else {
            memoryBankDAO.getCountByVectorStatus("pending")
        }
        val failedCount = if (assistantId != null) {
            0
        } else {
            memoryBankDAO.getCountByVectorStatus("failed")
        }
        MemoryStats(
            total = total,
            messageCount = messageCount,
            summaryCount = summaryCount,
            manualCount = manualCount,
            vectorizedCount = vectorizedCount,
            pendingCount = pendingCount,
            failedCount = failedCount
        )
    }

    suspend fun getTodayPhaseSummaries(assistantId: String? = null): List<MemoryBankEntity> =
        withContext(Dispatchers.IO) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            if (assistantId != null) {
                memoryBankDAO.getMemoriesByAssistantTypeAndDateGroup(assistantId, "phase_summary", today)
            } else {
                memoryBankDAO.getMemoriesByTypeAndDateGroup("phase_summary", today)
            }
        }

    suspend fun getDailySummaries(assistantId: String? = null): List<MemoryBankEntity> =
        withContext(Dispatchers.IO) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            if (assistantId != null) {
                memoryBankDAO.getMemoriesByAssistantTypeAndDateGroup(assistantId, "daily_summary", today)
            } else {
                memoryBankDAO.getMemoriesByTypeAndDateGroup("daily_summary", today)
            }
        }

    suspend fun searchMemories(
        keyword: String = "",
        type: String = "",
        limit: Int = 100,
        assistantId: String? = null
    ): List<MemoryBankEntity> = withContext(Dispatchers.IO) {
        if (keyword.isNotBlank() && type.isNotBlank()) {
            memoryBankDAO.searchMemoriesByKeywordAndType(keyword, type, limit)
        } else if (keyword.isNotBlank()) {
            memoryBankDAO.searchMemoriesByKeyword(keyword, limit)
        } else if (type.isNotBlank() && assistantId != null) {
            memoryBankDAO.getMemoriesByAssistantAndTypeLimit(assistantId, type, limit)
        } else if (type.isNotBlank()) {
            memoryBankDAO.getMemoriesByTypeLimit(type, limit)
        } else {
            memoryBankDAO.getRecentMemories(limit)
        }
    }

    suspend fun deleteMemory(id: Int) = withContext(Dispatchers.IO) {
        memoryBankDAO.deleteMemoryById(id)
    }

    suspend fun rebuildIndex() {
        // No-op: vector index removed. Structured embedding fields are stored for the next memory phase.
    }

    suspend fun processPendingVectors() {
        val settingsStore = settingsStore ?: return
        val providerManager = providerManager ?: return
        withContext(Dispatchers.IO) {
            val settings = settingsStore.settingsFlow.value
            val config = settings.memoryEmbeddingConfig
            if (!config.enabled) return@withContext
            val modelId = config.modelId ?: return@withContext
            val model = settings.findModelById(modelId)?.takeIf { it.type == ModelType.EMBEDDING } ?: return@withContext
            val provider = model.findProvider(settings.providers) ?: return@withContext
            val providerImpl = providerManager.getProviderByType(provider)
            val pending = memoryBankDAO.getPendingVectorMemories(maxRetry = 3, limit = config.batchSize.coerceIn(1, 64))
            if (pending.isEmpty()) return@withContext

            val inputs = pending.map { memory -> memory.embeddingText?.takeIf { it.isNotBlank() } ?: memory.content }
            runCatching {
                providerImpl.generateEmbedding(
                    providerSetting = provider,
                    params = EmbeddingGenerationParams(
                        model = model,
                        input = inputs,
                        dimensions = config.dimensions,
                        customHeaders = model.customHeaders,
                        customBody = model.customBodies,
                    ),
                )
            }.onSuccess { result ->
                pending.zip(result.embeddings).forEach { (memory, vector) ->
                    memoryBankDAO.updateVectorResult(
                        id = memory.id,
                        status = "done",
                        retryCount = memory.vectorRetryCount,
                        vectorJson = encodeMemoryVector(vector),
                        modelId = result.model,
                        dimensions = vector.size,
                    )
                }
            }.onFailure {
                pending.forEach { memory ->
                    memoryBankDAO.updateVectorStatus(
                        id = memory.id,
                        status = if (memory.vectorRetryCount + 1 >= 3) "failed" else "pending",
                        retryCount = memory.vectorRetryCount + 1,
                    )
                }
            }
        }
    }

    suspend fun saveManualMemory(content: String): MemoryBankEntity = withContext(Dispatchers.IO) {
        val entity = MemoryBankEntity(
            content = content,
            type = "manual"
        )
        val id = memoryBankDAO.insertMemory(entity).toInt()
        entity.copy(id = id)
    }

    suspend fun saveExtractedMemories(
        candidates: List<AffectiveMemoryCandidate>,
        assistantId: String?,
        conversationId: String?,
        createdAt: Long = System.currentTimeMillis(),
    ): List<MemoryBankEntity> = withContext(Dispatchers.IO) {
        candidates
            .map { it.normalized() }
            .filter { it.content.isNotBlank() }
            .map { candidate ->
                val entity = candidate.toEntity(
                    assistantId = assistantId,
                    conversationId = conversationId,
                    createdAt = createdAt,
                )
                val id = memoryBankDAO.insertMemory(entity).toInt()
                entity.copy(id = id)
            }
    }

    suspend fun getProcessedSourceNodeIds(
        assistantId: String,
        conversationId: String,
    ): Set<String> = withContext(Dispatchers.IO) {
        memoryBankDAO.getMemoriesByAssistant(assistantId)
            .asSequence()
            .filter { it.conversationId == conversationId && !it.sourceMessageNodeIdsJson.isNullOrBlank() }
            .flatMap { memory ->
                runCatching {
                    JsonInstant.decodeFromString<List<String>>(memory.sourceMessageNodeIdsJson.orEmpty())
                }.getOrDefault(emptyList()).asSequence()
            }
            .toSet()
    }

    suspend fun recallMemories(query: String, count: Int): List<MemoryBankEntity> = withContext(Dispatchers.IO) {
        if (query.isNotBlank()) {
            memoryBankDAO.searchMemoriesByKeyword(query, count)
        } else {
            memoryBankDAO.getRecentMemories(count)
        }
    }

    suspend fun buildRecallContext(assistantId: String?, query: String = ""): String = withContext(Dispatchers.IO) {
        val memories = buildList {
            if (assistantId != null) {
                addAll(memoryBankDAO.getMemoriesByAssistant(assistantId).take(80))
                addAll(memoryBankDAO.getPinnedRecallMemoriesForAssistant(assistantId, 3))
                addAll(memoryBankDAO.getImportantRecallMemoriesForAssistant(assistantId, minImportance = 4, limit = 5))
            } else {
                addAll(memoryBankDAO.getPinnedRecallMemories(3))
                addAll(memoryBankDAO.getImportantRecallMemories(minImportance = 4, limit = 5))
            }
            addAll(getTodayPhaseSummaries(assistantId).take(2))
            addAll(getDailySummaries(assistantId).take(2))
            if (assistantId != null) {
                addAll(memoryBankDAO.getMemoriesByAssistantAndTypeLimit(assistantId, "manual", 3))
                addAll(memoryBankDAO.getMemoriesByAssistantAndTypeLimit(assistantId, "message", 3))
            }
            addAll(memoryBankDAO.getMemoriesByTypeLimit("manual", 3))
        }
            .distinctBy { it.id }

        val queryVector = buildQueryEmbedding(query)
        val selected = selectMemoryRecallItems(
            memories = memories,
            query = query,
            queryVector = queryVector,
        )
        val recalledIds = selected.map { it.id }.filter { it > 0 }
        if (recalledIds.isNotEmpty()) {
            memoryBankDAO.markMemoriesRecalled(
                ids = recalledIds,
                recalledAt = System.currentTimeMillis(),
            )
        }
        buildMemoryRecallContextFromSelected(selected)
    }

    private suspend fun buildQueryEmbedding(query: String): List<Float> {
        val settingsStore = settingsStore ?: return emptyList()
        val providerManager = providerManager ?: return emptyList()
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        val settings = settingsStore.settingsFlow.value
        val config = settings.memoryEmbeddingConfig
        if (!config.enabled) return emptyList()
        val modelId = config.modelId ?: return emptyList()
        val model = settings.findModelById(modelId)?.takeIf { it.type == ModelType.EMBEDDING } ?: return emptyList()
        val provider = model.findProvider(settings.providers) ?: return emptyList()
        val providerImpl = providerManager.getProviderByType(provider)

        return runCatching {
            providerImpl.generateEmbedding(
                providerSetting = provider,
                params = EmbeddingGenerationParams(
                    model = model,
                    input = listOf(trimmed),
                    dimensions = config.dimensions,
                    customHeaders = model.customHeaders,
                    customBody = model.customBodies,
                ),
            ).embeddings.firstOrNull().orEmpty()
        }.getOrDefault(emptyList())
    }
}

internal fun buildMemoryRecallContext(
    memories: List<MemoryBankEntity>,
    query: String = "",
    queryVector: List<Float> = emptyList(),
    maxItems: Int? = null,
    maxContentLength: Int = 120,
): String {
    val selected = selectMemoryRecallItems(
        memories = memories,
        query = query,
        queryVector = queryVector,
        maxItems = maxItems,
    )
    return buildMemoryRecallContextFromSelected(selected, maxContentLength)
}

private fun buildMemoryRecallContextFromSelected(
    selected: List<MemoryBankEntity>,
    maxContentLength: Int = 120,
): String {
    if (selected.isEmpty()) return ""

    val sections = listOf(
        "长期印象" to selected.filter { it.recallSectionTitle() == "长期印象" },
        "最近情感记忆" to selected.filter { it.recallSectionTitle() == "最近情感记忆" },
        "身体和五感" to selected.filter { it.bodySense.isNotBlankValue() || it.recallSectionTitle() == "身体和五感" },
        "当前相关回忆" to selected.filter { it.recallSectionTitle() == "当前相关回忆" },
        "关系变化" to selected.filter { it.recallSectionTitle() == "关系变化" },
        "未完成承诺" to selected.filter { it.recallSectionTitle() == "未完成承诺" },
    ).filter { (_, items) -> items.isNotEmpty() }

    return buildString {
        appendLine("<lulu_memory>")
        appendLine("这些是露露此刻自然想起的记忆，只作为联想参考。不要逐条复述，也不要说“我查到记忆”。")
        sections.forEach { (title, items) ->
            appendLine("$title：")
            items.forEach { memory ->
                appendLine("- ${memory.toRecallLine(maxContentLength)}")
            }
        }
        append("</lulu_memory>")
    }
}

internal fun selectMemoryRecallItems(
    memories: List<MemoryBankEntity>,
    query: String = "",
    queryVector: List<Float> = emptyList(),
    maxItems: Int? = null,
): List<MemoryBankEntity> {
    val queryTerms = query.recallQueryTerms()
    val sorted = memories
        .filter { it.content.isNotBlank() && !it.deprecated }
        .sortedByDescending { memory -> memory.recallScore(queryTerms, queryVector) }
        .deduplicateNearVectors()
    val limit = maxItems ?: sorted.dynamicRecallLimit(queryVector)
    val direct = sorted.take(limit)
    return direct.expandRelatedMemories(sorted, maxRelatedItems = 1)
}

private fun List<MemoryBankEntity>.expandRelatedMemories(
    candidates: List<MemoryBankEntity>,
    maxRelatedItems: Int,
): List<MemoryBankEntity> {
    if (maxRelatedItems <= 0 || isEmpty()) return this
    val selectedIds = mapTo(mutableSetOf()) { it.id }
    val candidateById = candidates.associateBy { it.id.toString() }
    val related = asSequence()
        .flatMap { memory -> memory.relatedMemoryIds().asSequence() }
        .mapNotNull { relatedId -> candidateById[relatedId] }
        .filter { it.id !in selectedIds }
        .distinctBy { it.id }
        .take(maxRelatedItems)
        .toList()
    return this + related
}

private fun MemoryBankEntity.relatedMemoryIds(): List<String> =
    runCatching {
        JsonInstant.decodeFromString(ListSerializer(String.serializer()), relatedMemoryIdsJson.orEmpty())
    }.getOrDefault(emptyList())

private fun List<MemoryBankEntity>.dynamicRecallLimit(queryVector: List<Float>): Int {
    if (queryVector.isEmpty()) return 6
    val bestSimilarity = maxOfOrNull { memory ->
        cosineSimilarity(queryVector, decodeMemoryVector(memory.embeddingVectorJson))
    } ?: 0.0
    return when {
        bestSimilarity >= 0.70 -> 3
        bestSimilarity >= 0.50 -> 5
        else -> 7
    }
}

private fun MemoryBankEntity.recallSectionTitle(): String = when (memoryKind ?: type) {
    "role_emotion" -> "最近情感记忆"
    "body_sense" -> "身体和五感"
    "promise" -> "未完成承诺"
    "relationship" -> "关系变化"
    "user_preference", "manual" -> "长期印象"
    "phase_summary", "daily_summary" -> "当前相关回忆"
    else -> "当前相关回忆"
}

private fun MemoryBankEntity.recallScore(queryTerms: List<String>, queryVector: List<Float>): Double {
    val text = listOfNotNull(
        title,
        content,
        memoryKind,
        roleFeeling,
        bodySense,
        unspokenThought,
        userSignal,
        relationshipEffect,
        tagsJson,
        embeddingText,
        peopleJson,
        topicsJson,
    ).joinToString("\n").lowercase()
    val queryScore = queryTerms.count { term -> term in text } * 120.0
    val promiseScore = if ((memoryKind ?: type) == "promise") 160.0 else 0.0
    val pinnedScore = if (pinned) 220.0 else 0.0
    val importanceScore = importance.coerceIn(1, 5) * 24.0
    val confidenceScore = confidence.coerceIn(0.0, 1.0) * 20.0
    val freshnessScore = createdAt.coerceAtLeast(0L) / 1_000_000_000_000.0
    val vectorScore = cosineSimilarity(queryVector, decodeMemoryVector(embeddingVectorJson)) * 240.0
    return vectorScore + queryScore + promiseScore + pinnedScore + importanceScore + confidenceScore + freshnessScore
}

private fun List<MemoryBankEntity>.deduplicateNearVectors(): List<MemoryBankEntity> =
    fold(emptyList()) { selected, candidate ->
        val candidateVector = decodeMemoryVector(candidate.embeddingVectorJson)
        if (candidateVector.isEmpty()) {
            selected + candidate
        } else {
            val isDuplicate = selected.any { existing ->
                cosineSimilarity(candidateVector, decodeMemoryVector(existing.embeddingVectorJson)) >
                    MEMORY_DUPLICATE_VECTOR_THRESHOLD
            }
            if (isDuplicate) selected else selected + candidate
        }
    }

private fun MemoryBankEntity.toRecallLine(maxContentLength: Int): String {
    val parts = buildList {
        add(content.trim())
        roleFeeling?.takeIf { it.isNotBlank() }?.let { add("露露当时的感觉：$it") }
        bodySense?.takeIf { it.isNotBlank() }?.let { add("身体感：$it") }
        unspokenThought?.takeIf { it.isNotBlank() }?.let { add("没说出口的想法：$it") }
        relationshipEffect?.takeIf { it.isNotBlank() }?.let { add("关系判断：$it") }
    }
    val prefix = if (confidence < 0.7) "可能：" else ""
    return (prefix + parts.joinToString("；")).ellipsize(maxContentLength)
}

private fun String?.isNotBlankValue(): Boolean = this != null && isNotBlank()

private fun String.ellipsize(maxLength: Int): String {
    if (length <= maxLength) return this
    return take(maxLength).trimEnd() + "..."
}

private fun String.recallQueryTerms(): List<String> {
    val compact = trim().lowercase().filterNot(Char::isWhitespace)
    if (compact.isBlank()) return emptyList()
    val wordTerms = Regex("[\\p{L}\\p{N}_]{2,}")
        .findAll(lowercase())
        .map { it.value }
    val cjkBigrams = compact.windowed(size = 2, step = 1, partialWindows = false)
        .asSequence()
        .filter { term -> term.any { it.code > 127 } }
    return (wordTerms + cjkBigrams)
        .filter { it.length >= 2 }
        .distinct()
        .take(24)
        .toList()
}
