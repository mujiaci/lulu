package me.rerere.rikkahub.ui.pages.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.entity.MemoryBankEntity
import me.rerere.rikkahub.data.service.MemoryBankService
import kotlin.uuid.Uuid

class MemoryBankVM(
    private val memoryBankService: MemoryBankService,
    private val settingsStore: SettingsStore,
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

    init {
        loadMemories()
    }

    fun loadMemories() {
        viewModelScope.launch {
            _loading.value = true
            try {
                // Load assistant IDs for filter
                _assistantIds.value = memoryBankService.getAssistantIds()

                // Update stats using COUNT queries (fast, no full load)
                _stats.value = memoryBankService.getStats(_selectedAssistantId.value)

                // Load display data based on type filter
                val selectedType = _selectedType.value
                val assistantId = _selectedAssistantId.value

                _memories.value = memoryBankService.searchMemories(
                    keyword = _searchQuery.value,
                    type = selectedType,
                    limit = 100,
                    assistantId = assistantId,
                )
            } finally {
                _loading.value = false
            }
        }
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
