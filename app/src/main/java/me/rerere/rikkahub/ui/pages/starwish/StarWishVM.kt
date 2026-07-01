package me.rerere.rikkahub.ui.pages.starwish

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.GenMediaRepository
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.starwish.StarWishGeneratedImage
import me.rerere.rikkahub.data.starwish.StarWishImageLaunch
import me.rerere.rikkahub.data.starwish.StarWishOutfitPrompts
import me.rerere.rikkahub.data.starwish.StarWishRules
import me.rerere.rikkahub.data.starwish.StarWishState
import me.rerere.rikkahub.data.starwish.StarWishStore
import me.rerere.rikkahub.data.starwish.StarWishTheaterChapter
import me.rerere.rikkahub.data.starwish.StarWishTheaterSeed
import me.rerere.rikkahub.data.study.StudyRules
import me.rerere.rikkahub.data.study.StudyStore
import me.rerere.rikkahub.data.ai.mcp.McpCommonOptions
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig

private const val MCDONALDS_MCP_NAME = "麦当劳 MCP"
private const val MCDONALDS_MCP_URL = "https://mcp.mcd.cn"

private fun normalizeMcdonaldsMcpAuthValue(token: String): String {
    val value = token.trim()
    return if (value.startsWith("Bearer ", ignoreCase = true)) {
        value
    } else {
        "Bearer $value"
    }
}

class StarWishVM(
    private val store: StarWishStore,
    private val studyStore: StudyStore,
    private val genMediaRepository: GenMediaRepository,
    private val filesManager: FilesManager,
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
) : ViewModel() {
    val state: StateFlow<StarWishState> = store.state
    val studyState = studyStore.state
    val mcpDiagnostic = settingsStore.settingsFlow
        .map { settings -> buildMcdonaldsMcpDiagnostic(settings.mcpServers) }
        .stateIn(viewModelScope, SharingStarted.Lazily, "正在读取 MCP 配置...")
    val videoModelStatus = settingsStore.settingsFlow
        .map { settings ->
            settings.findModelById(settings.videoGenerationModelId)
                ?.takeIf { it.type == ModelType.VIDEO }
                ?.let { "已选择视频模型：${it.displayName.ifBlank { it.modelId }}" }
                ?: "还没有选择视频模型；去设置-默认模型里选择一个视频模型。"
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, "正在读取视频模型...")
    private val _generatedImages = MutableStateFlow<List<StarWishGeneratedImage>>(emptyList())
    val generatedImages = _generatedImages.asStateFlow()
    private val _isGeneratingChapter = MutableStateFlow(false)
    val isGeneratingChapter = _isGeneratingChapter.asStateFlow()
    private val _chapterError = MutableStateFlow<String?>(null)
    val chapterError = _chapterError.asStateFlow()

    init {
        viewModelScope.launch {
            state.collectLatest {
                refreshGeneratedImages()
            }
        }
    }

    fun savePrompts(outfit: String, prompts: StarWishOutfitPrompts) {
        viewModelScope.launch {
            store.update {
                it.copy(customOutfitPrompts = it.customOutfitPrompts + (outfit to prompts))
            }
        }
    }

    fun recordImageLaunch(outfit: String, prompt: String) {
        viewModelScope.launch {
            val launch = StarWishImageLaunch(
                id = "image-${System.currentTimeMillis()}-${outfit.hashCode()}",
                outfit = outfit,
                prompt = prompt,
                createdAt = System.currentTimeMillis(),
            )
            store.update {
                it.copy(imageLaunches = (listOf(launch) + it.imageLaunches).take(80))
            }
        }
    }

    fun refreshGeneratedImages() {
        viewModelScope.launch {
            val launches = state.value.imageLaunches
            val imagesDir = filesManager.getImagesDir()
            val media = genMediaRepository.listAllMedia()
            _generatedImages.value = media.mapNotNull { entity ->
                val launch = launches.firstOrNull { it.prompt == entity.prompt } ?: return@mapNotNull null
                StarWishGeneratedImage(
                    id = entity.id,
                    outfit = launch.outfit,
                    filePath = File(imagesDir, entity.path.removePrefix("images/")).absolutePath,
                    prompt = entity.prompt,
                    createdAt = entity.createAt,
                )
            }
        }
    }

    fun saveMcdonaldsMcpCode(code: String) {
        viewModelScope.launch {
            store.update { it.copy(mcdonaldsMcpCode = code) }
        }
    }

    fun installMcdonaldsMcp(code: String) {
        viewModelScope.launch {
            val token = code.trim()
            store.update { it.copy(mcdonaldsMcpCode = token) }
            settingsStore.update { settings ->
                val existing = settings.mcpServers.firstOrNull { server ->
                    server.commonOptions.name == MCDONALDS_MCP_NAME ||
                        (server is McpServerConfig.StreamableHTTPServer && server.url == MCDONALDS_MCP_URL)
                }
                val headers = listOf("Authorization" to normalizeMcdonaldsMcpAuthValue(token))
                val commonOptions = (existing?.commonOptions ?: McpCommonOptions())
                    .copy(
                        enable = true,
                        name = MCDONALDS_MCP_NAME,
                        headers = headers,
                    )
                val config = when (existing) {
                    is McpServerConfig.StreamableHTTPServer -> existing.copy(
                        commonOptions = commonOptions,
                        url = MCDONALDS_MCP_URL,
                    )
                    null -> McpServerConfig.StreamableHTTPServer(
                        commonOptions = commonOptions,
                        url = MCDONALDS_MCP_URL,
                    )
                    else -> McpServerConfig.StreamableHTTPServer(
                        id = existing.id,
                        commonOptions = commonOptions,
                        url = MCDONALDS_MCP_URL,
                    )
                }
                val nextServers = if (existing == null) {
                    settings.mcpServers + config
                } else {
                    settings.mcpServers.map { server ->
                        if (server.id == existing.id) config else server
                    }
                }
                settings.copy(
                    mcpServers = nextServers,
                    assistants = settings.assistants.map { assistant ->
                        if (assistant.id == settings.assistantId) {
                            assistant.copy(mcpServers = assistant.mcpServers + config.id)
                        } else {
                            assistant
                        }
                    },
                )
            }
        }
    }

    fun redeemVideo() {
        viewModelScope.launch {
            studyStore.update { current -> StudyRules.redeemVideo(current).state }
        }
    }

    fun createNextChapter(theater: String, influence: String = "") {
        viewModelScope.launch {
            try {
                _isGeneratingChapter.value = true
                _chapterError.value = null
                val seed = StarWishRules.allTheaters(state.value.customTheaters).firstOrNull { it.title == theater } ?: return@launch
                val study = studyState.value
                if (study.inventory.universalRareFragments < StarWishRules.RARE_FRAGMENTS_PER_CHAPTER) return@launch
                val chapters = state.value.theaterChapters[theater].orEmpty().filterNot { it.isPromptPlaceholder(seed) }
                val nextChapter = chapters.size + 1
                val content = generateTheaterChapterContent(seed, chapters, nextChapter, influence.trim())
                studyStore.update { current ->
                    current.copy(
                        inventory = current.inventory.copy(
                            universalRareFragments = (current.inventory.universalRareFragments - StarWishRules.RARE_FRAGMENTS_PER_CHAPTER).coerceAtLeast(0),
                        ),
                    )
                }
                store.update { current ->
                    val latestChapters = current.theaterChapters[theater].orEmpty().filterNot { it.isPromptPlaceholder(seed) }
                    val chapter = StarWishTheaterChapter(
                        id = "theater-${System.currentTimeMillis()}-${theater.hashCode()}",
                        theater = theater,
                        chapter = nextChapter,
                        title = "第 $nextChapter 章",
                        content = content,
                        userInfluence = influence.trim(),
                        createdAt = System.currentTimeMillis(),
                    )
                    current.copy(theaterChapters = current.theaterChapters + (theater to (latestChapters + chapter)))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _chapterError.value = e.message ?: "小剧场生成失败"
            } finally {
                _isGeneratingChapter.value = false
            }
        }
    }

    fun createNextSpecialStoryChapter(story: String, influence: String = "") {
        viewModelScope.launch {
            try {
                _isGeneratingChapter.value = true
                _chapterError.value = null
                val seed = StarWishRules.allSpecialStories(state.value.customSpecialStories).firstOrNull { it.title == story } ?: return@launch
                val study = studyState.value
                if (study.inventory.specialStoryFragments < StarWishRules.SPECIAL_FRAGMENTS_PER_CHAPTER) return@launch
                val chapters = state.value.specialStoryChapters[story].orEmpty().filterNot { it.isPromptPlaceholder(seed) }
                val nextChapter = chapters.size + 1
                val content = generateTheaterChapterContent(seed, chapters, nextChapter, influence.trim())
                studyStore.update { current ->
                    StudyRules.redeemSpecialStory(current).state
                }
                store.update { current ->
                    val latestChapters = current.specialStoryChapters[story].orEmpty().filterNot { it.isPromptPlaceholder(seed) }
                    val chapter = StarWishTheaterChapter(
                        id = "special-${System.currentTimeMillis()}-${story.hashCode()}",
                        theater = story,
                        chapter = nextChapter,
                        title = "第 $nextChapter 章",
                        content = content,
                        userInfluence = influence.trim(),
                        createdAt = System.currentTimeMillis(),
                    )
                    current.copy(specialStoryChapters = current.specialStoryChapters + (story to (latestChapters + chapter)))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _chapterError.value = e.message ?: "特殊剧情生成失败"
            } finally {
                _isGeneratingChapter.value = false
            }
        }
    }

    fun deleteChapter(theater: String, chapterId: String) {
        viewModelScope.launch {
            store.update { current ->
                val updated = current.theaterChapters[theater].orEmpty()
                    .filterNot { it.id == chapterId }
                    .mapIndexed { index, chapter ->
                        chapter.copy(
                            chapter = index + 1,
                            title = "第 ${index + 1} 章",
                        )
                    }
                current.copy(theaterChapters = current.theaterChapters + (theater to updated))
            }
        }
    }

    fun deleteSpecialStoryChapter(story: String, chapterId: String) {
        viewModelScope.launch {
            store.update { current ->
                val updated = current.specialStoryChapters[story].orEmpty()
                    .filterNot { it.id == chapterId }
                    .mapIndexed { index, chapter ->
                        chapter.copy(
                            chapter = index + 1,
                            title = "第 ${index + 1} 章",
                        )
                    }
                current.copy(specialStoryChapters = current.specialStoryChapters + (story to updated))
            }
        }
    }

    private suspend fun generateTheaterChapterContent(
        seed: StarWishTheaterSeed,
        chapters: List<StarWishTheaterChapter>,
        nextChapter: Int,
        influence: String,
    ): String {
        val settings = settingsStore.settingsFlow.first()
        val model = settings.theaterModelId
            ?.let { settings.findModelById(it) }
            ?.takeIf { it.type == ModelType.CHAT }
            ?: error("请先在默认模型里设置“小剧场模型”，用来生成小剧场正文。")
        val providerSetting = model.findProvider(settings.providers)
            ?: error("小剧场模型没有找到对应提供商。")
        val provider = providerManager.getProviderByType(providerSetting)
        val prompt = StarWishRules.theaterChapterPrompt(seed, chapters, nextChapter, influence)
        val chunk = provider.generateText(
            providerSetting = providerSetting,
            messages = listOf(UIMessage.user(prompt)),
            params = TextGenerationParams(
                model = model,
                temperature = 0.9f,
                topP = 0.95f,
                maxTokens = 3200,
                reasoningLevel = ReasoningLevel.OFF,
                customHeaders = model.customHeaders,
                customBody = model.customBodies,
            ),
        )
        return chunk.choices.firstOrNull()?.message?.toText()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: error("小剧场 API 没有返回正文。")
    }

    fun addCustomTheater(title: String, prompt: String) {
        val cleanTitle = title.trim()
        val cleanPrompt = prompt.trim()
        if (cleanTitle.isBlank() || cleanPrompt.isBlank()) return
        viewModelScope.launch {
            val seed = StarWishTheaterSeed(
                id = "custom-${System.currentTimeMillis()}-${cleanTitle.hashCode()}",
                title = cleanTitle,
                prompt = cleanPrompt,
                createdAt = System.currentTimeMillis(),
            )
            store.update { current ->
                current.copy(customTheaters = current.customTheaters + seed)
            }
        }
    }

    fun addCustomSpecialStory(title: String, prompt: String) {
        val cleanTitle = title.trim()
        val cleanPrompt = prompt.trim()
        if (cleanTitle.isBlank() || cleanPrompt.isBlank()) return
        viewModelScope.launch {
            val seed = StarWishTheaterSeed(
                id = "custom-special-${System.currentTimeMillis()}-${cleanTitle.hashCode()}",
                title = cleanTitle,
                prompt = cleanPrompt,
                createdAt = System.currentTimeMillis(),
            )
            store.update { current ->
                current.copy(customSpecialStories = current.customSpecialStories + seed)
            }
        }
    }
}

private fun buildMcdonaldsMcpDiagnostic(servers: List<McpServerConfig>): String {
    val mcdServer = servers.firstOrNull { server ->
        server.commonOptions.name == MCDONALDS_MCP_NAME ||
            (server is McpServerConfig.StreamableHTTPServer && server.url == MCDONALDS_MCP_URL)
    }
    if (mcdServer == null) {
        return "还没有安装麦当劳 MCP。粘贴 MCP 码后点“保存并接通”，我会自动填好服务地址和请求头。"
    }
    val tokenReady = mcdServer.commonOptions.headers.any { (key, value) ->
        key.equals("Authorization", ignoreCase = true) &&
            value.removePrefix("Bearer").trim().isNotBlank()
    }
    val enabled = servers.filter { it.commonOptions.enable }
    val allTools = enabled.flatMap { server ->
        server.commonOptions.tools.map { tool -> server.commonOptions.name to tool.name }
    }
    val orderTools = allTools.filter { (_, tool) ->
        val name = tool.lowercase()
        listOf("mcdonald", "麦当劳", "order", "cart", "menu", "点单", "下单").any { key -> name.contains(key) }
    }
    return buildString {
        append("麦当劳 MCP 已安装，")
        append(if (tokenReady) "MCP 码已填写。" else "还差 MCP 码。")
        append("已启用 ${if (mcdServer.commonOptions.enable) "是" else "否"}。")
        append("已同步工具 ${allTools.size} 个。")
        if (orderTools.isEmpty()) {
            append("如果刚保存，稍等同步工具；若一直为 0，多半是 MCP 码没填或填错。")
        } else {
            append("疑似点单工具：")
            append(orderTools.take(5).joinToString("、") { "${it.first}/${it.second}" })
            append("。真实下单仍需要工具参数确认和支付确认。")
        }
    }
}

private fun StarWishTheaterChapter.isPromptPlaceholder(seed: StarWishTheaterSeed): Boolean {
    val clean = content.trim()
    return clean == seed.prompt.trim() ||
        clean.startsWith("总设定：") ||
        clean.startsWith("你是一个擅长") ||
        clean.contains("硬性要求：") ||
        clean.contains("请根据下面设定生成")
}
