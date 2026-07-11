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
import me.rerere.ai.provider.RerankItem
import me.rerere.ai.provider.RerankParams
import me.rerere.rikkahub.data.db.dao.MemoryBankDAO
import me.rerere.rikkahub.data.db.entity.MemoryBankEntity
import me.rerere.rikkahub.data.db.entity.MemoryExtractionCheckpointEntity
import me.rerere.rikkahub.data.db.entity.MemoryGraphEdgeEntity
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.utils.JsonInstant
import okhttp3.OkHttpClient

private const val MEMORY_DUPLICATE_VECTOR_THRESHOLD = 0.85
private const val MEMORY_HEBBIAN_EDGE_DELTA = 0.18
private const val MEMORY_HEBBIAN_MAX_EDGE_WEIGHT = 3.0
private const val MEMORY_GRAPH_MIN_RECALL_WEIGHT = 0.12
private const val LIGHT_MAINTENANCE_INTERVAL_MS = 24L * 60 * 60 * 1000
private const val HEAVY_MAINTENANCE_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
private const val MAINTENANCE_PREFS_NAME = "memory_bank_maintenance"
private const val LAST_LIGHT_MAINTENANCE_KEY = "last_light_maintenance_at"
private const val LAST_HEAVY_MAINTENANCE_KEY = "last_heavy_maintenance_at"
private const val MAX_PROCESSED_MEMORY_NODE_IDS = 5_000

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
        val manualCount: Int = 0,
        val vectorizedCount: Int = 0,
        val pendingCount: Int = 0,
        val failedCount: Int = 0,
        val deprecatedCount: Int = 0,
    )

    data class MaintenanceResult(
        val deprecatedDuplicateCount: Int = 0,
    )

    private var lastLightMaintenanceAt: Long = 0L
    private var lastHeavyMaintenanceAt: Long = 0L

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
        val manualCount = if (assistantId != null) {
            memoryBankDAO.getCountByAssistantAndType(assistantId, "manual")
        } else {
            memoryBankDAO.getCountByType("manual")
        }
        val vectorizedCount = if (assistantId != null) {
            memoryBankDAO.getCountByAssistantAndVectorStatus(assistantId, "done")
        } else {
            memoryBankDAO.getCountByVectorStatus("done")
        }
        val pendingCount = if (assistantId != null) {
            memoryBankDAO.getCountByAssistantAndVectorStatus(assistantId, "pending")
        } else {
            memoryBankDAO.getCountByVectorStatus("pending")
        }
        val failedCount = if (assistantId != null) {
            memoryBankDAO.getCountByAssistantAndVectorStatus(assistantId, "failed")
        } else {
            memoryBankDAO.getCountByVectorStatus("failed")
        }
        val deprecatedCount = if (assistantId != null) {
            memoryBankDAO.getDeprecatedCountByAssistant(assistantId)
        } else {
            memoryBankDAO.getDeprecatedCount()
        }
        MemoryStats(
            total = total,
            messageCount = messageCount,
            manualCount = manualCount,
            vectorizedCount = vectorizedCount,
            pendingCount = pendingCount,
            failedCount = failedCount,
            deprecatedCount = deprecatedCount,
        )
    }

    suspend fun searchMemories(
        keyword: String = "",
        type: String = "",
        limit: Int = 100,
        assistantId: String? = null
    ): List<MemoryBankEntity> = withContext(Dispatchers.IO) {
        if (type == "deprecated" && keyword.isNotBlank() && assistantId != null) {
            memoryBankDAO.searchDeprecatedMemoriesByAssistantAndKeyword(assistantId, keyword, limit)
        } else if (type == "deprecated" && keyword.isNotBlank()) {
            memoryBankDAO.searchDeprecatedMemoriesByKeyword(keyword, limit)
        } else if (type == "deprecated" && assistantId != null) {
            memoryBankDAO.getDeprecatedMemoriesByAssistant(assistantId, limit)
        } else if (type == "deprecated") {
            memoryBankDAO.getDeprecatedMemories(limit)
        } else if (keyword.isNotBlank() && type.isNotBlank() && assistantId != null) {
            memoryBankDAO.searchMemoriesByAssistantKeywordAndType(assistantId, keyword, type, limit)
        } else if (keyword.isNotBlank() && type.isNotBlank()) {
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
        memoryBankDAO.deleteMemoryGraphEdgesForMemory(id)
        memoryBankDAO.deleteMemoryById(id)
    }

    suspend fun deleteMemoriesByAssistant(assistantId: String) = withContext(Dispatchers.IO) {
        memoryBankDAO.deleteMemoryGraphEdgesForAssistant(assistantId)
        memoryBankDAO.deleteExtractionCheckpointsByAssistant(assistantId)
        memoryBankDAO.deleteMemoriesByAssistant(assistantId)
    }

    suspend fun deleteAllMemories() = withContext(Dispatchers.IO) {
        memoryBankDAO.deleteAllMemoryGraphEdges()
        memoryBankDAO.deleteAllExtractionCheckpoints()
        memoryBankDAO.deleteAllMemories()
    }

    suspend fun updateMemory(memory: MemoryBankEntity) = withContext(Dispatchers.IO) {
        memoryBankDAO.updateMemory(memory)
    }

    suspend fun setPinned(memory: MemoryBankEntity, pinned: Boolean) = withContext(Dispatchers.IO) {
        memoryBankDAO.updateMemory(memory.copy(pinned = pinned))
    }

    suspend fun markMemoryDeprecated(
        memory: MemoryBankEntity,
        reason: String,
        supersededByMemoryId: String?,
    ) = withContext(Dispatchers.IO) {
        memoryBankDAO.markMemoryDeprecated(
            id = memory.id,
            deprecatedReason = reason.ifBlank { "manual_correction" },
            supersededByMemoryId = supersededByMemoryId?.takeIf { it.isNotBlank() },
            correctedAt = System.currentTimeMillis(),
        )
    }

    suspend fun rebuildIndex() {
        // No-op: vector index removed. Structured embedding fields are stored for the next memory phase.
    }

    suspend fun runLightMaintenance(limit: Int = 200): MaintenanceResult = withContext(Dispatchers.IO) {
        processPendingVectors()
        val memories = memoryBankDAO.getRecentMemories(limit)
            .filter { !it.deprecated && it.content.isNotBlank() }
        val deprecated = selectNearDuplicateMemoriesForDeprecation(memories)
        val now = System.currentTimeMillis()
        deprecated.forEach { duplicate ->
            memoryBankDAO.markMemoryDeprecated(
                id = duplicate.deprecated.id,
                deprecatedReason = "near_duplicate:${duplicate.keep.id}",
                supersededByMemoryId = duplicate.keep.id.toString(),
                correctedAt = now,
            )
        }
        MaintenanceResult(deprecatedDuplicateCount = deprecated.size)
    }

    suspend fun runHeavyMaintenance(limit: Int = 800): MaintenanceResult = withContext(Dispatchers.IO) {
        processPendingVectors()
        val memories = memoryBankDAO.getRecentMemories(limit)
            .filter { !it.deprecated && it.content.isNotBlank() }
        val deprecated = selectNearDuplicateMemoriesForDeprecation(memories)
        val now = System.currentTimeMillis()
        deprecated.forEach { duplicate ->
            memoryBankDAO.markMemoryDeprecated(
                id = duplicate.deprecated.id,
                deprecatedReason = "weekly_near_duplicate:${duplicate.keep.id}",
                supersededByMemoryId = duplicate.keep.id.toString(),
                correctedAt = now,
            )
        }
        MaintenanceResult(deprecatedDuplicateCount = deprecated.size)
    }

    suspend fun runAutoMaintenanceIfDue(now: Long = System.currentTimeMillis()) {
        val prefs = context?.getSharedPreferences(MAINTENANCE_PREFS_NAME, Context.MODE_PRIVATE)
        val lastLight = prefs?.getLong(LAST_LIGHT_MAINTENANCE_KEY, 0L) ?: lastLightMaintenanceAt
        val lastHeavy = prefs?.getLong(LAST_HEAVY_MAINTENANCE_KEY, 0L) ?: lastHeavyMaintenanceAt

        if (lastLight == 0L || now - lastLight >= LIGHT_MAINTENANCE_INTERVAL_MS) {
            runLightMaintenance()
            lastLightMaintenanceAt = now
            prefs?.edit()?.putLong(LAST_LIGHT_MAINTENANCE_KEY, now)?.apply()
        }

        if (lastHeavy == 0L) {
            lastHeavyMaintenanceAt = now
            prefs?.edit()?.putLong(LAST_HEAVY_MAINTENANCE_KEY, now)?.apply()
        } else if (now - lastHeavy >= HEAVY_MAINTENANCE_INTERVAL_MS) {
            runHeavyMaintenance()
            lastHeavyMaintenanceAt = now
            prefs?.edit()?.putLong(LAST_HEAVY_MAINTENANCE_KEY, now)?.apply()
        }
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
        val existingIdentities = assistantId
            ?.let { memoryBankDAO.getMemoriesByAssistant(it) }
            .orEmpty()
            .asSequence()
            .map { it.content.normalizedMemoryIdentity() }
            .filter { it.isNotBlank() }
            .toMutableSet()
        val saved = candidates
            .map { it.normalized() }
            .filter { it.content.isNotBlank() }
            .filterNot { it.shouldSkipMemoryBankWrite() }
            .filter { it.isDurableMemoryCandidate() }
            .filter { candidate -> existingIdentities.add(candidate.content.normalizedMemoryIdentity()) }
            .map { candidate ->
                val entity = candidate.toEntity(
                    assistantId = assistantId,
                    conversationId = conversationId,
                    createdAt = createdAt,
                )
                val id = memoryBankDAO.insertMemory(entity).toInt()
                entity.copy(id = id)
            }
        learnRecallCooccurrence(saved)
        saved
    }

    suspend fun getProcessedSourceNodeIds(
        assistantId: String,
        conversationId: String,
    ): Set<String> = withContext(Dispatchers.IO) {
        val checkpointIds = memoryBankDAO.getExtractionCheckpoint(assistantId, conversationId)
            ?.processedSourceNodeIdsJson
            .decodeMemoryNodeIds()
        val memoryIds = memoryBankDAO.getMemoriesByAssistant(assistantId)
            .asSequence()
            .filter { it.conversationId == conversationId && !it.sourceMessageNodeIdsJson.isNullOrBlank() }
            .flatMap { memory ->
                runCatching {
                    JsonInstant.decodeFromString<List<String>>(memory.sourceMessageNodeIdsJson.orEmpty())
                }.getOrDefault(emptyList()).asSequence()
            }
            .toSet()
        checkpointIds + memoryIds
    }

    suspend fun markExtractionProcessed(
        assistantId: String,
        conversationId: String,
        sourceNodeIds: Collection<String>,
        now: Long = System.currentTimeMillis(),
    ) = withContext(Dispatchers.IO) {
        val checkpoint = memoryBankDAO.getExtractionCheckpoint(assistantId, conversationId)
        val merged = mergeProcessedMemoryNodeIds(
            existing = checkpoint?.processedSourceNodeIdsJson.decodeMemoryNodeIds(),
            incoming = sourceNodeIds,
        )
        memoryBankDAO.upsertExtractionCheckpoint(
            MemoryExtractionCheckpointEntity(
                assistantId = assistantId,
                conversationId = conversationId,
                processedSourceNodeIdsJson = JsonInstant.encodeToString(
                    ListSerializer(String.serializer()),
                    merged.toList(),
                ),
                updatedAt = now,
            ),
        )
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
                addAll(memoryBankDAO.getMemoriesByAssistant(assistantId).take(120))
                addAll(memoryBankDAO.getPinnedRecallMemoriesForAssistant(assistantId, 3))
                addAll(memoryBankDAO.getImportantRecallMemoriesForAssistant(assistantId, minImportance = 4, limit = 8))
            } else {
                addAll(memoryBankDAO.getPinnedRecallMemories(3))
                addAll(memoryBankDAO.getImportantRecallMemories(minImportance = 4, limit = 5))
            }
            if (assistantId != null) {
                addAll(memoryBankDAO.getMemoriesByAssistantAndTypeLimit(assistantId, "manual", 8))
                addAll(memoryBankDAO.getMemoriesByAssistantAndTypeLimit(assistantId, "message", 12))
            }
            addAll(memoryBankDAO.getMemoriesByTypeLimit("manual", 5))
        }
            .distinctBy { it.id }

        val queryVector = buildQueryEmbedding(query)
        val rerankCandidateCount = memoryRerankCandidateCount()
        val rerankCandidates = rankMemoryRecallCandidates(
            memories = memories,
            query = query,
            queryVector = queryVector,
        ).take(rerankCandidateCount)
        val rerankResults = rerankRecallCandidates(query, rerankCandidates)
        val graphEdges = memories.map { it.id }
            .filter { it > 0 }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.let { sourceIds ->
                memoryBankDAO.getMemoryGraphEdgesFromSources(
                    sourceIds = sourceIds,
                    minWeight = MEMORY_GRAPH_MIN_RECALL_WEIGHT,
                    limit = 400,
                )
            }
            .orEmpty()
        val selected = selectMemoryRecallItems(
            memories = memories,
            query = query,
            queryVector = queryVector,
            rerankCandidateCount = rerankCandidateCount,
            graphEdges = graphEdges,
            reranker = { rerankResults },
        )
        val recalledIds = selected.map { it.id }.filter { it > 0 }
        if (recalledIds.isNotEmpty()) {
            memoryBankDAO.markMemoriesRecalled(
                ids = recalledIds,
                recalledAt = System.currentTimeMillis(),
            )
            learnRecallCooccurrence(selected)
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

    private fun memoryRerankCandidateCount(): Int =
        settingsStore?.settingsFlow?.value
            ?.memoryEmbeddingConfig
            ?.rerankCandidateCount
            ?.coerceIn(5, 60)
            ?: 60

    private suspend fun rerankRecallCandidates(
        query: String,
        candidates: List<MemoryBankEntity>,
    ): List<MemoryRerankResult> {
        val settingsStore = settingsStore ?: return emptyList()
        val providerManager = providerManager ?: return emptyList()
        val trimmed = query.trim()
        if (trimmed.isBlank() || candidates.size < 2) return emptyList()

        val settings = settingsStore.settingsFlow.value
        val config = settings.memoryEmbeddingConfig
        val modelId = config.rerankModelId ?: return emptyList()
        val model = settings.findModelById(modelId)?.takeIf { it.type == ModelType.RERANK } ?: return emptyList()
        val provider = model.findProvider(settings.providers) ?: return emptyList()
        val providerImpl = providerManager.getProviderByType(provider)
        val documents = candidates.map { it.rerankDocumentText() }

        return runCatching {
            providerImpl.rerank(
                providerSetting = provider,
                params = RerankParams(
                    model = model,
                    query = trimmed,
                    documents = documents,
                    topN = documents.size,
                    customHeaders = model.customHeaders,
                    customBody = model.customBodies,
                ),
            ).results.toMemoryRerankResults()
        }.getOrDefault(emptyList())
    }

    private suspend fun learnRecallCooccurrence(memories: List<MemoryBankEntity>) {
        val validMemories = memories.filter { it.id > 0 }
        if (validMemories.size < 2) return
        val selectedIds = validMemories.map { it.id.toString() }
        val now = System.currentTimeMillis()
        validMemories.forEach { memory ->
            val relatedIds = (memory.relatedMemoryIds() + selectedIds)
                .filter { it != memory.id.toString() }
                .distinct()
                .take(8)
            memoryBankDAO.updateRelatedMemoryIds(
                id = memory.id,
                relatedMemoryIdsJson = JsonInstant.encodeToString(
                    ListSerializer(String.serializer()),
                    relatedIds,
                ),
            )
            relatedIds.forEach { relatedId ->
                val targetId = relatedId.toIntOrNull() ?: return@forEach
                memoryBankDAO.insertMemoryGraphEdge(
                    MemoryGraphEdgeEntity(
                        sourceMemoryId = memory.id,
                        targetMemoryId = targetId,
                        weight = 0.0,
                        coOccurrenceCount = 0,
                        createdAt = now,
                        lastReinforcedAt = now,
                    )
                )
                memoryBankDAO.reinforceMemoryGraphEdge(
                    sourceId = memory.id,
                    targetId = targetId,
                    delta = hebbianDelta(memory, validMemories.firstOrNull { it.id == targetId }),
                    maxWeight = MEMORY_HEBBIAN_MAX_EDGE_WEIGHT,
                    reinforcedAt = now,
                )
            }
        }
    }
}

private data class DuplicateMemoryDeprecation(
    val deprecated: MemoryBankEntity,
    val keep: MemoryBankEntity,
)

internal data class MemoryRerankResult(
    val index: Int,
    val relevanceScore: Double,
)

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
        appendLine("这些是当前角色此刻自然想起的记忆，只作为联想参考。不要逐条复述，也不要说“我查到记忆”。")
        appendLine("载入这些记忆时必须代入当前角色，把它们当成“我记得/我当时/我没说出口”的内在回忆来理解；后续总结、沉淀和回应都要保持第一人称，不要改写成旁白或第三人称。")
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
    rerankCandidateCount: Int = 60,
    graphEdges: List<MemoryGraphEdgeEntity> = emptyList(),
    reranker: (List<MemoryBankEntity>) -> List<MemoryRerankResult> = { emptyList() },
): List<MemoryBankEntity> {
    val sorted = rankMemoryRecallCandidates(memories, query, queryVector)
    val limit = maxItems ?: sorted.dynamicRecallLimit(queryVector)
    val candidateLimit = if (maxItems == null) sorted.size.coerceAtMost(rerankCandidateCount.coerceIn(5, 60)) else limit
    val candidates = sorted.take(candidateLimit)
    val direct = applyMemoryRerankResults(
        memories = candidates,
        results = runCatching { reranker(candidates) }.getOrDefault(emptyList()),
    ).take(limit)
    return direct
        .expandGraphMemories(sorted, graphEdges, maxRelatedItems = 2)
        .expandRelatedMemories(sorted, maxRelatedItems = 1)
        .distinctBy { it.id }
}

private fun rankMemoryRecallCandidates(
    memories: List<MemoryBankEntity>,
    query: String,
    queryVector: List<Float>,
): List<MemoryBankEntity> {
    val queryTerms = query.recallQueryTerms()
    return memories
        .filter { it.content.isNotBlank() && !it.deprecated }
        .sortedByDescending { memory -> memory.recallScore(queryTerms, queryVector) }
        .deduplicateNearVectors()
}

internal fun applyMemoryRerankResults(
    memories: List<MemoryBankEntity>,
    results: List<MemoryRerankResult>,
): List<MemoryBankEntity> {
    if (memories.size < 2 || results.isEmpty()) return memories
    val rankedIndexes = results
        .filter { it.index in memories.indices }
        .sortedByDescending { it.relevanceScore }
        .map { it.index }
        .distinct()
    val ranked = rankedIndexes.map { memories[it] }
    val remaining = memories.filterIndexed { index, _ -> index !in rankedIndexes }
    return ranked + remaining
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

private fun List<MemoryBankEntity>.expandGraphMemories(
    candidates: List<MemoryBankEntity>,
    graphEdges: List<MemoryGraphEdgeEntity>,
    maxRelatedItems: Int,
): List<MemoryBankEntity> {
    if (maxRelatedItems <= 0 || isEmpty() || graphEdges.isEmpty()) return this
    val selectedIds = mapTo(mutableSetOf()) { it.id }
    val candidateById = candidates.associateBy { it.id }
    val graphRelated = graphEdges.asSequence()
        .filter { edge -> edge.sourceMemoryId in selectedIds && edge.targetMemoryId !in selectedIds }
        .sortedWith(
            compareByDescending<MemoryGraphEdgeEntity> { it.weight }
                .thenByDescending { it.lastReinforcedAt }
        )
        .mapNotNull { edge -> candidateById[edge.targetMemoryId] }
        .distinctBy { it.id }
        .take(maxRelatedItems)
        .toList()
    return this + graphRelated
}

private fun MemoryBankEntity.relatedMemoryIds(): List<String> =
    runCatching {
        JsonInstant.decodeFromString(ListSerializer(String.serializer()), relatedMemoryIdsJson.orEmpty())
    }.getOrDefault(emptyList())

private fun hebbianDelta(source: MemoryBankEntity, target: MemoryBankEntity?): Double {
    val sourceStrength = source.importance.coerceIn(1, 5) / 5.0 * source.confidence.coerceIn(0.0, 1.0)
    val targetStrength = (target?.importance ?: source.importance).coerceIn(1, 5) / 5.0 *
        (target?.confidence ?: source.confidence).coerceIn(0.0, 1.0)
    return MEMORY_HEBBIAN_EDGE_DELTA + (sourceStrength + targetStrength) * 0.05
}

private fun List<RerankItem>.toMemoryRerankResults(): List<MemoryRerankResult> =
    map { item ->
        MemoryRerankResult(
            index = item.index,
            relevanceScore = item.relevanceScore,
        )
    }

private fun MemoryBankEntity.rerankDocumentText(): String =
    listOfNotNull(
        title,
        content,
        roleFeeling,
        bodySense,
        unspokenThought,
        userSignal,
        relationshipEffect,
        embeddingText,
        tagsJson,
        peopleJson,
        topicsJson,
    ).joinToString("\n").take(1600)

private fun List<MemoryBankEntity>.dynamicRecallLimit(queryVector: List<Float>): Int {
    if (queryVector.isEmpty()) return 8
    val bestSimilarity = maxOfOrNull { memory ->
        cosineSimilarity(queryVector, decodeMemoryVector(memory.embeddingVectorJson))
    } ?: 0.0
    return when {
        bestSimilarity >= 0.70 -> 6
        bestSimilarity >= 0.50 -> 8
        else -> 10
    }
}

private fun selectNearDuplicateMemoriesForDeprecation(
    memories: List<MemoryBankEntity>,
): List<DuplicateMemoryDeprecation> {
    val active = memories
        .filter { decodeMemoryVector(it.embeddingVectorJson).isNotEmpty() }
        .sortedByDescending { it.maintenanceKeepScore() }
    val keepers = mutableListOf<MemoryBankEntity>()
    val deprecated = mutableListOf<DuplicateMemoryDeprecation>()
    active.forEach { candidate ->
        val duplicateOf = keepers.firstOrNull { keeper ->
            cosineSimilarity(
                decodeMemoryVector(candidate.embeddingVectorJson),
                decodeMemoryVector(keeper.embeddingVectorJson),
            ) > MEMORY_DUPLICATE_VECTOR_THRESHOLD
        }
        if (duplicateOf != null) {
            deprecated += DuplicateMemoryDeprecation(
                deprecated = candidate,
                keep = duplicateOf,
            )
        } else {
            keepers += candidate
        }
    }
    return deprecated
}

private fun MemoryBankEntity.maintenanceKeepScore(): Double =
    importance.coerceIn(1, 5) * 100.0 +
        confidence.coerceIn(0.0, 1.0) * 50.0 +
        recallCount * 10.0 +
        createdAt.coerceAtLeast(0L) / 1_000_000_000_000.0

private fun MemoryBankEntity.recallSectionTitle(): String = when (memoryKind ?: type) {
    "role_emotion" -> "最近情感记忆"
    "body_sense" -> "身体和五感"
    "promise" -> "未完成承诺"
    "relationship" -> "关系变化"
    "user_preference", "manual" -> "长期印象"
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
        roleFeeling?.takeIf { it.isNotBlank() }?.let { add("我当时的感觉：$it") }
        bodySense?.takeIf { it.isNotBlank() }?.let { add("我的身体感：$it") }
        unspokenThought?.takeIf { it.isNotBlank() }?.let { add("我没说出口的想法：$it") }
        relationshipEffect?.takeIf { it.isNotBlank() }?.let { add("我的关系判断：$it") }
    }
    val prefix = if (confidence < 0.7) "可能：" else ""
    return (prefix + parts.joinToString("；")).ellipsize(maxContentLength)
}

private fun String?.isNotBlankValue(): Boolean = this != null && isNotBlank()

private fun String.ellipsize(maxLength: Int): String {
    if (length <= maxLength) return this
    return take(maxLength).trimEnd() + "..."
}

private fun String?.decodeMemoryNodeIds(): Set<String> = runCatching {
    JsonInstant.decodeFromString(ListSerializer(String.serializer()), orEmpty())
        .asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .toCollection(linkedSetOf())
}.getOrDefault(emptySet())

internal fun mergeProcessedMemoryNodeIds(
    existing: Collection<String>,
    incoming: Collection<String>,
    limit: Int = MAX_PROCESSED_MEMORY_NODE_IDS,
): Set<String> = (existing.asSequence() + incoming.asSequence())
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()
    .toList()
    .takeLast(limit.coerceAtLeast(1))
    .toCollection(linkedSetOf())

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
