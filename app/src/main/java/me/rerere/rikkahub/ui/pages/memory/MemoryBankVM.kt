package me.rerere.rikkahub.ui.pages.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.companion.CompanionRuntime
import me.rerere.rikkahub.data.companion.CompanionTurnMutation
import me.rerere.rikkahub.data.db.entity.MemoryBankEntity
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.service.MemoryBankService
import me.rerere.rikkahub.data.service.buildCompanionPrivateImpression
import me.rerere.rikkahub.data.service.buildDeterministicMemoryCandidatesFromNodes
import me.rerere.rikkahub.data.service.buildRelationshipEventsFromMemoryCandidates
import kotlin.uuid.Uuid

class MemoryBankVM(
    private val memoryBankService: MemoryBankService,
    private val settingsStore: SettingsStore,
    private val conversationRepository: ConversationRepository,
    private val companionRuntime: CompanionRuntime,
) : ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings.dummy())

    private val _memories = MutableStateFlow<List<MemoryBankEntity>>(emptyList())
    val memories: StateFlow<List<MemoryBankEntity>> = _memories.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedType = MutableStateFlow("")
    val selectedType: StateFlow<String> = _selectedType.asStateFlow()

    private val _selectedAssistantId = MutableStateFlow<String?>(null)
    val selectedAssistantId: StateFlow<String?> = _selectedAssistantId.asStateFlow()

    private val _assistantIds = MutableStateFlow<List<String>>(emptyList())
    val assistantIds: StateFlow<List<String>> = _assistantIds.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _stats = MutableStateFlow(MemoryBankService.MemoryStats())
    val stats: StateFlow<MemoryBankService.MemoryStats> = _stats.asStateFlow()

    private val _maintenanceMessage = MutableStateFlow<String?>(null)
    val maintenanceMessage: StateFlow<String?> = _maintenanceMessage.asStateFlow()
    private var attemptedAutomaticHistoryRepair = false

    init {
        loadMemories()
    }

    fun loadMemories() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val currentSettings = settingsStore.settingsFlow.first()
                refreshMemoryData(currentSettings)
                if (
                    _memories.value.isEmpty() &&
                    _searchQuery.value.isBlank() &&
                    _selectedType.value.isBlank() &&
                    !attemptedAutomaticHistoryRepair
                ) {
                    attemptedAutomaticHistoryRepair = true
                    val recovered = recoverMemoriesFromHistory(currentSettings, _selectedAssistantId.value)
                    if (recovered > 0) {
                        _maintenanceMessage.value = "已从已有聊天补整理 $recovered 条长期记忆"
                        refreshMemoryData(currentSettings)
                    }
                }
            } finally {
                _loading.value = false
            }
        }
    }

    private suspend fun refreshMemoryData(currentSettings: Settings) {
        val configuredAssistantIds = currentSettings.assistants.map { it.id.toString() }
        val storedAssistantIds = memoryBankService.getAssistantIds()
        _assistantIds.value = (configuredAssistantIds + storedAssistantIds).distinct()
        val selectedAssistantId = _selectedAssistantId.value
        if (selectedAssistantId != null && selectedAssistantId !in _assistantIds.value) {
            _selectedAssistantId.value = null
        }
        val assistantId = _selectedAssistantId.value
        _stats.value = memoryBankService.getStats(assistantId)
        _memories.value = memoryBankService.searchMemories(
            keyword = _searchQuery.value,
            type = _selectedType.value,
            limit = 100,
            assistantId = assistantId,
        )
    }

    fun repairMemoriesFromHistory() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val currentSettings = settingsStore.settingsFlow.first()
                val recovered = recoverMemoriesFromHistory(currentSettings, _selectedAssistantId.value)
                _maintenanceMessage.value = if (recovered > 0) {
                    "已从已有聊天补整理 $recovered 条长期记忆"
                } else {
                    "没有发现新的明确偏好、边界或纠正；普通寒暄不会写进长期记忆"
                }
                attemptedAutomaticHistoryRepair = true
                refreshMemoryData(currentSettings)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _maintenanceMessage.value = error.message?.let { "整理失败：$it" } ?: "整理失败，请重试"
            } finally {
                _loading.value = false
            }
        }
    }

    private suspend fun recoverMemoriesFromHistory(
        currentSettings: Settings,
        assistantId: String?,
    ): Int {
        val assistants = currentSettings.assistants.filter { assistant ->
            assistantId == null || assistant.id.toString() == assistantId
        }
        var recoveredCount = 0
        for (assistant in assistants) {
            for (conversation in conversationRepository.getRecentConversations(assistant.id, limit = 8)) {
                val candidates = buildDeterministicMemoryCandidatesFromNodes(conversation.messageNodes, limit = 6)
                if (candidates.isEmpty()) continue
                val nowMillis = System.currentTimeMillis()
                val saved = memoryBankService.saveExtractedMemories(
                    candidates = candidates,
                    assistantId = assistant.id.toString(),
                    conversationId = conversation.id.toString(),
                    createdAt = nowMillis,
                )
                recoveredCount += saved.size
                if (saved.isNotEmpty()) {
                    val snapshot = companionRuntime.snapshot(assistant.id.toString())
                    companionRuntime.applyTurn(
                        CompanionTurnMutation(
                            assistantId = assistant.id.toString(),
                            privateImpression = buildCompanionPrivateImpression(
                                previous = snapshot.privateImpression,
                                candidates = candidates,
                                nowMillis = nowMillis,
                            ),
                            relationshipEvents = buildRelationshipEventsFromMemoryCandidates(
                                candidates = candidates,
                                assistantId = assistant.id.toString(),
                                conversationId = conversation.id.toString(),
                                createdAt = nowMillis,
                            ),
                            nowMillis = nowMillis,
                        ),
                    )
                }
            }
        }
        return recoveredCount
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        loadMemories()
    }

    fun setSelectedType(type: String) {
        _selectedType.value = type
        loadMemories()
    }

    fun setSelectedAssistantId(assistantId: String?) {
        _selectedAssistantId.value = assistantId
        loadMemories()
    }

    fun deleteMemory(id: Int) {
        viewModelScope.launch {
            memoryBankService.deleteMemory(id)
            loadMemories()
        }
    }

    fun clearLongTermMemories() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val assistantId = _selectedAssistantId.value
                if (assistantId == null) {
                    memoryBankService.deleteAllMemories()
                    _maintenanceMessage.value = "已清除全部长期记忆"
                } else {
                    memoryBankService.deleteMemoriesByAssistant(assistantId)
                    _maintenanceMessage.value = "已清除当前角色的长期记忆"
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _maintenanceMessage.value = error.message?.let { "清除失败：$it" } ?: "清除失败，请重试"
            } finally {
                _loading.value = false
                loadMemories()
            }
        }
    }

    fun updateMemory(memory: MemoryBankEntity) {
        viewModelScope.launch {
            memoryBankService.updateMemory(memory)
            loadMemories()
        }
    }

    fun setPinned(memory: MemoryBankEntity, pinned: Boolean) {
        viewModelScope.launch {
            memoryBankService.setPinned(memory, pinned)
            loadMemories()
        }
    }

    fun markDeprecated(memory: MemoryBankEntity, reason: String, supersededByMemoryId: String?) {
        viewModelScope.launch {
            memoryBankService.markMemoryDeprecated(memory, reason, supersededByMemoryId)
            loadMemories()
        }
    }

    fun rebuildIndex() {
        viewModelScope.launch {
            _loading.value = true
            try {
                memoryBankService.rebuildIndex()
                loadMemories()
            } finally {
                _loading.value = false
            }
        }
    }

    fun processPendingVectors() {
        viewModelScope.launch {
            _loading.value = true
            try {
                memoryBankService.processPendingVectors()
                loadMemories()
            } finally {
                _loading.value = false
            }
        }
    }

    fun runLightMaintenance() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val result = memoryBankService.runLightMaintenance()
                _maintenanceMessage.value = "轻量维护完成：合并 ${result.deprecatedDuplicateCount} 条重复记忆"
                loadMemories()
            } finally {
                _loading.value = false
            }
        }
    }

    fun setMemoryEmbeddingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(memoryEmbeddingConfig = settings.memoryEmbeddingConfig.copy(enabled = enabled))
            }
        }
    }

    fun setMemoryEmbeddingModel(modelId: Uuid?) {
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(memoryEmbeddingConfig = settings.memoryEmbeddingConfig.copy(modelId = modelId))
            }
        }
    }

    fun setMemoryEmbeddingDimensions(value: String) {
        val dimensions = value.trim().toIntOrNull()?.takeIf { it > 0 }
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(memoryEmbeddingConfig = settings.memoryEmbeddingConfig.copy(dimensions = dimensions))
            }
        }
    }

    fun embeddingModels(settings: Settings): List<Model> =
        settings.providers.flatMap { provider -> provider.models }
            .filter { model -> model.type == ModelType.EMBEDDING }
}
