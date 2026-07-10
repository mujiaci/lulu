package me.rerere.rikkahub.data.cihai

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.ModelType
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.ApiUsageSource
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.service.AffectiveMemoryCandidate
import me.rerere.rikkahub.data.service.MemoryBankService
import kotlin.uuid.Uuid

class CihaiService(
    private val store: CihaiStore,
    private val memoryBankService: MemoryBankService,
    private val settingsStore: SettingsStore,
    private val generationHandler: GenerationHandler,
    private val scope: AppScope,
) {
    private val settlementMutex = Mutex()

    suspend fun addEntry(entry: CihaiEntry) {
        store.addEntry(entry)
        scope.launch {
            runCatching {
                settleDueMemories(assistantId = entry.assistantId)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Cihai settlement launch failed for assistant=${entry.assistantId}", error)
            }
        }
    }

    suspend fun addBook(book: CihaiBook) {
        store.addBook(book)
    }

    suspend fun recordSilentJudgment(
        assistantId: String,
        assistantName: String,
        reason: String,
        userText: String,
        createdAt: Long = System.currentTimeMillis(),
    ) {
        addEntry(
            CihaiEntry.fromSilentJudgment(
                assistantId = assistantId,
                assistantName = assistantName,
                reason = reason,
                userText = userText,
                createdAt = createdAt,
            )
        )
    }

    suspend fun recordSilentPresenceAction(
        assistantId: String,
        assistantName: String,
        reason: String,
        userText: String,
        actionHintNames: List<String> = emptyList(),
        createdAt: Long = System.currentTimeMillis(),
    ) {
        val result = planCihaiSilentPresence(
            CihaiSilentPresenceInput(
                assistantId = assistantId,
                assistantName = assistantName,
                reason = reason,
                userText = userText,
                actionHintNames = actionHintNames,
                books = store.state.value.books,
                createdAt = createdAt,
            )
        )
        result.updatedBook?.let { store.updateBook(it) }
        result.entries.forEach { entry -> addEntry(entry) }
    }

    suspend fun readBook(book: CihaiBook) {
        val result = book.readNextReflection()
        store.updateBook(result.updatedBook)
        addEntry(result.entry)
    }

    suspend fun settleDueMemories(
        assistantId: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        settlementMutex.withLock {
            val state = store.snapshot()
            val assistantIds = assistantId
                ?.takeIf { it.isNotBlank() }
                ?.let(::listOf)
                ?: state.memoryQueue
                    .map { it.assistantId }
                    .filter { it.isNotBlank() }
                    .distinct()
            assistantIds.forEach { targetAssistantId ->
                settleAssistantBatch(targetAssistantId, nowMillis)
            }
        }
    }

    private suspend fun settleAssistantBatch(assistantId: String, nowMillis: Long) {
        val state = store.snapshot()
        val batch = selectDueCihaiMemoryBatch(
            queue = state.memoryQueue,
            assistantId = assistantId,
            nowMillis = nowMillis,
        )
        if (batch.isEmpty()) return

        val batchEntryIds = batch.mapTo(linkedSetOf()) { it.entryId }
        val entries = state.entries
            .filter { it.assistantId == assistantId && it.id in batchEntryIds }
            .sortedBy { it.createdAt }
        if (entries.isEmpty()) {
            store.completeMemorySettlement(batchEntryIds, emptySet())
            return
        }

        runCatching {
            val alreadyProcessed = memoryBankService.getProcessedCihaiEntryIds(assistantId)
            val alreadySavedIds = entries.mapNotNullTo(linkedSetOf()) { entry ->
                entry.id.takeIf { it in alreadyProcessed }
            }
            if (alreadySavedIds.isNotEmpty()) {
                store.completeMemorySettlement(alreadySavedIds, alreadySavedIds)
            }

            val pendingEntries = entries.filterNot { it.id in alreadySavedIds }
            if (pendingEntries.isEmpty()) return@runCatching
            val pendingEntryIds = pendingEntries.mapTo(linkedSetOf()) { it.id }
            val candidates = generateSettlementCandidates(assistantId, pendingEntries)
            val savedEvidenceIds = if (candidates.isEmpty()) {
                emptySet()
            } else {
                memoryBankService.saveCihaiSettledMemories(
                    candidates = candidates,
                    assistantId = assistantId,
                    createdAt = nowMillis,
                )
            }
            store.completeMemorySettlement(
                reviewedEntryIds = pendingEntryIds,
                savedEvidenceEntryIds = savedEvidenceIds,
            )
            Log.i(
                TAG,
                "Settled ${pendingEntries.size} Cihai entries into ${candidates.size} candidates " +
                    "for assistant=$assistantId",
            )
        }.onFailure { error ->
            if (error is CancellationException) throw error
            store.retryMemorySettlement(
                entryIds = batchEntryIds,
                failedAt = nowMillis,
                error = error.message ?: error::class.simpleName.orEmpty(),
            )
            Log.w(TAG, "Cihai memory settlement failed for assistant=$assistantId", error)
        }
    }

    private suspend fun generateSettlementCandidates(
        assistantId: String,
        entries: List<CihaiEntry>,
    ): List<AffectiveMemoryCandidate> {
        val settings = settingsStore.settingsFlow.value
        val assistantUuid = runCatching { Uuid.parse(assistantId) }.getOrNull()
        val assistant = assistantUuid?.let { settings.getAssistantById(it) }
            ?: return emptyList()
        val modelId = settings.memoryEmbeddingConfig.extractionModelId
            ?: assistant.chatModelId
            ?: settings.chatModelId
        val model = settings.findModelById(modelId)
            ?.takeIf { it.type == ModelType.CHAT }
            ?: error("No chat model is available for Cihai memory settlement")
        val prompt = CihaiMemorySettlement.buildPrompt(
            entries = entries,
            assistantName = assistant.name,
            assistantPersona = assistant.systemPrompt,
        )
        val settlementAssistant = assistant.copy(
            systemPrompt = "你是角色长期记忆整理器，只执行用户消息中的结构化辞海沉淀任务。",
            temperature = 0.2f,
            topP = 0.8f,
            maxTokens = 1_400,
            streamOutput = false,
            enableMemory = false,
            localTools = emptyList(),
            reasoningLevel = ReasoningLevel.OFF,
            allowSkipReply = false,
        )
        var generatedMessages: List<UIMessage> = emptyList()
        generationHandler.generateText(
            settings = settings,
            model = model,
            messages = listOf(UIMessage.user(prompt)),
            assistant = settlementAssistant,
            maxSteps = 1,
            apiUsageSource = ApiUsageSource.OTHER,
            apiUsageTitle = "辞海记忆沉淀：${assistant.name.ifBlank { "当前角色" }}",
        ).collect { chunk ->
            when (chunk) {
                is GenerationChunk.Messages -> generatedMessages = chunk.messages
            }
        }
        val rawText = generatedMessages.lastOrNull()?.toText().orEmpty()
        if (rawText.isBlank()) error("Cihai memory settlement returned no content")
        return CihaiMemorySettlement.parseAndValidateCandidates(
            rawText = rawText,
            assistantId = assistantId,
            entries = entries,
        ).take(MAX_SETTLEMENT_CANDIDATES)
    }

    private companion object {
        const val TAG = "CihaiService"
        const val MAX_SETTLEMENT_CANDIDATES = 8
    }
}
