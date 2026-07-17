package me.rerere.rikkahub.ui.pages.starwish

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.ApiUsageSource
import me.rerere.rikkahub.data.ai.ApiUsageStore
import me.rerere.rikkahub.data.ai.transformers.transformMessages
import me.rerere.rikkahub.data.companion.CompanionLifeEvent
import me.rerere.rikkahub.data.companion.CompanionLifeEventSource
import me.rerere.rikkahub.data.companion.CompanionLifeEventStatus
import me.rerere.rikkahub.data.companion.CompanionLifeEventType
import me.rerere.rikkahub.data.companion.CompanionPerceptionInput
import me.rerere.rikkahub.data.companion.CompanionRuntime
import me.rerere.rikkahub.data.companion.CompanionTurnMutation
import me.rerere.rikkahub.data.companion.toPromptContext
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.GenMediaRepository
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.starwish.StarWishGeneratedImage
import me.rerere.rikkahub.data.starwish.StarWishImageLaunch
import me.rerere.rikkahub.data.starwish.StarWishOutfitPrompts
import me.rerere.rikkahub.data.starwish.StarWishRules
import me.rerere.rikkahub.data.starwish.StarWishState
import me.rerere.rikkahub.data.starwish.StarWishStore
import me.rerere.rikkahub.data.starwish.StarWishTheaterChapter
import me.rerere.rikkahub.data.starwish.StarWishTheaterGuide
import me.rerere.rikkahub.data.starwish.StarWishTheaterSeed
import me.rerere.rikkahub.data.starwish.StarWishVideoItem
import me.rerere.rikkahub.data.study.StudyStore

class StarWishVM(
    private val store: StarWishStore,
    private val studyStore: StudyStore,
    private val genMediaRepository: GenMediaRepository,
    private val filesManager: FilesManager,
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
    private val apiUsageStore: ApiUsageStore,
    private val companionRuntime: CompanionRuntime,
) : ViewModel() {
    val state: StateFlow<StarWishState> = store.state
    val studyState = studyStore.state
    private val _videoPlayback = MutableSharedFlow<StarWishVideoItem>(extraBufferCapacity = 8)
    val videoPlayback = _videoPlayback
    private val _videoMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val videoMessage = _videoMessage
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
            val current = state.value
            val launches = current.imageLaunches.filterNot { it.id in current.hiddenImageLaunchIds }
            val imagesDir = filesManager.getImagesDir()
            val media = genMediaRepository.listAllMedia()
            val hiddenMedia = media.filter { it.id in current.hiddenGeneratedImageIds }
            if (hiddenMedia.isNotEmpty()) {
                hiddenMedia.forEach { entity ->
                    runCatching {
                        File(imagesDir, entity.path.removePrefix("images/")).delete()
                    }
                }
                genMediaRepository.deleteMediaByIds(hiddenMedia.map { it.id })
                store.update { state ->
                    state.copy(hiddenGeneratedImageIds = state.hiddenGeneratedImageIds - hiddenMedia.map { it.id }.toSet())
                }
            }
            val hiddenIds = hiddenMedia.map { it.id }.toSet()
            _generatedImages.value = media.mapNotNull { entity ->
                if (entity.id in current.hiddenGeneratedImageIds || entity.id in hiddenIds) return@mapNotNull null
                val launch = launches.firstOrNull { it.prompt == entity.prompt }
                StarWishGeneratedImage(
                    id = entity.id,
                    outfit = launch?.outfit ?: "生成图库",
                    filePath = File(imagesDir, entity.path.removePrefix("images/")).absolutePath,
                    prompt = entity.prompt,
                    createdAt = entity.createAt,
                    fromStarWish = launch != null,
                )
            }
        }
    }

    fun importVideo(uri: Uri) {
        viewModelScope.launch {
            val localUri = filesManager.createChatFilesByContents(listOf(uri)).firstOrNull()
            if (localUri == null) {
                _videoMessage.tryEmit("视频导入失败")
                return@launch
            }
            val sourceName = filesManager.getFileNameFromUri(uri) ?: "星愿视频"
            val item = StarWishVideoItem(
                id = "custom-video-${System.currentTimeMillis()}-${sourceName.hashCode()}",
                title = sourceName.substringBeforeLast('.').ifBlank { "星愿视频" },
                uri = localUri.toString(),
                builtIn = false,
                createdAt = System.currentTimeMillis(),
            )
            store.update { current ->
                current.copy(customVideos = current.customVideos + item)
            }
            _videoMessage.tryEmit("已加入视频柜，使用视频碎片后可解锁")
        }
    }

    fun unlockNextVideoOrPlayRandom() {
        viewModelScope.launch {
            val currentStarWish = state.value
            val visibleVideos = StarWishRules.allVideos(currentStarWish.customVideos)
                .filterNot { it.id in currentStarWish.hiddenVideoIds }
            val hasLockedVideo = visibleVideos.any { it.id !in currentStarWish.unlockedVideoIds }
            var result = StarWishRules.unlockNextVideo(currentStarWish, studyState.value, Random.Default)
            val video = result.video
            if (video == null) {
                _videoMessage.tryEmit(if (visibleVideos.isEmpty()) "先上传或内置视频后再解锁" else "还需要 1 枚视频碎片")
                return@launch
            }
            if (result.consumedFragment) {
                studyStore.update { currentStudy ->
                    result = StarWishRules.unlockNextVideo(currentStarWish, currentStudy, Random.Default)
                    result.studyState
                }
                if (!result.consumedFragment || result.video == null) {
                    _videoMessage.tryEmit("还需要 1 枚视频碎片")
                    return@launch
                }
                store.update { result.starWishState }
                _videoMessage.tryEmit("已解锁：${result.video!!.title}")
            } else if (hasLockedVideo) {
                _videoMessage.tryEmit("还需要 1 枚视频碎片")
                return@launch
            }
            _videoPlayback.emit(result.video ?: video)
        }
    }

    fun playVideo(video: StarWishVideoItem) {
        viewModelScope.launch {
            if (video.id in state.value.unlockedVideoIds) {
                _videoPlayback.emit(video)
            } else {
                _videoMessage.tryEmit("这个视频还没有解锁")
            }
        }
    }

    fun deleteVideo(video: StarWishVideoItem) {
        viewModelScope.launch {
            if (!video.builtIn) {
                runCatching { filesManager.deleteChatFiles(listOf(video.uri.toUri())) }
            }
            store.update { current ->
                current.copy(
                    customVideos = current.customVideos.filterNot { it.id == video.id },
                    hiddenVideoIds = current.hiddenVideoIds + video.id,
                    unlockedVideoIds = current.unlockedVideoIds - video.id,
                )
            }
        }
    }

    fun createNextChapter(theater: String, influence: String = "") {
        viewModelScope.launch {
            try {
                _isGeneratingChapter.value = true
                _chapterError.value = null
                val seed = StarWishRules.allTheaters(state.value.customTheaters).firstOrNull { it.title == theater } ?: return@launch
                val study = studyState.value
                if (study.inventory.theaterFragments < StarWishRules.RARE_FRAGMENTS_PER_CHAPTER) return@launch
                val chapters = state.value.theaterChapters[theater].orEmpty().filterNot { it.isPromptPlaceholder(seed) }
                val guide = state.value.theaterGuides[theater] ?: StarWishRules.defaultTheaterGuide(seed)
                val nextChapter = chapters.size + 1
                val generated = generateTheaterChapterContent(seed, chapters, nextChapter, influence.trim(), guide)
                studyStore.update { current ->
                    current.copy(
                        inventory = current.inventory.copy(
                            theaterFragments = (current.inventory.theaterFragments - StarWishRules.RARE_FRAGMENTS_PER_CHAPTER).coerceAtLeast(0),
                        ),
                    )
                }
                var createdChapter: StarWishTheaterChapter? = null
                store.update { current ->
                    val latestChapters = current.theaterChapters[theater].orEmpty().filterNot { it.isPromptPlaceholder(seed) }
                    val chapter = StarWishTheaterChapter(
                        id = "theater-${System.currentTimeMillis()}-${theater.hashCode()}",
                        theater = theater,
                        chapter = nextChapter,
                        title = "第 $nextChapter 章",
                        content = generated.content,
                        userInfluence = influence.trim(),
                        createdAt = System.currentTimeMillis(),
                    )
                    createdChapter = chapter
                    current.copy(theaterChapters = current.theaterChapters + (theater to (latestChapters + chapter)))
                }
                createdChapter?.let { chapter ->
                    val nowMillis = System.currentTimeMillis()
                    companionRuntime.applyTurn(
                        CompanionTurnMutation(
                            assistantId = generated.assistantId,
                            lifeEvents = listOf(
                                CompanionLifeEvent(
                                    id = "starwish:${chapter.id}",
                                    assistantId = generated.assistantId,
                                    type = CompanionLifeEventType.TOOL_ACTION,
                                    status = CompanionLifeEventStatus.COMPLETED,
                                    title = "写入了小剧场新章节",
                                    summary = "参与完成《${chapter.theater}》第 ${chapter.chapter} 章《${chapter.title}》。",
                                    source = CompanionLifeEventSource.AGENT,
                                    evidenceReference = chapter.id,
                                    importance = 3,
                                    startedAt = nowMillis,
                                    endedAt = nowMillis,
                                    createdAt = nowMillis,
                                ),
                            ),
                            nowMillis = nowMillis,
                        ),
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _chapterError.value = e.message ?: "小剧场生成失败"
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

    fun saveTheaterGuide(title: String, guide: StarWishTheaterGuide) {
        viewModelScope.launch {
            store.update { current ->
                current.copy(theaterGuides = current.theaterGuides + (title to guide.normalized()))
            }
        }
    }

    fun rememberSection(section: String) {
        viewModelScope.launch {
            store.update { it.copy(lastSection = section) }
        }
    }

    fun deleteScroll(title: String, legacyOutfit: String? = null) {
        viewModelScope.launch {
            store.update { current ->
                val keys = setOfNotNull(title, legacyOutfit)
                current.copy(
                    hiddenScrollTitles = current.hiddenScrollTitles + title,
                    customOutfitPrompts = current.customOutfitPrompts - keys,
                    imageLaunches = current.imageLaunches.filterNot { it.outfit in keys },
                )
            }
        }
    }

    fun deleteImageLaunch(id: String) {
        viewModelScope.launch {
            store.update { current ->
                current.copy(hiddenImageLaunchIds = current.hiddenImageLaunchIds + id)
            }
        }
    }

    fun deleteGeneratedImage(id: Int) {
        viewModelScope.launch {
            store.update { current ->
                current.copy(hiddenGeneratedImageIds = current.hiddenGeneratedImageIds + id)
            }
        }
    }

    fun deleteTheater(title: String) {
        viewModelScope.launch {
            store.update { current ->
                current.copy(
                    hiddenTheaterTitles = current.hiddenTheaterTitles + title,
                    customTheaters = current.customTheaters.filterNot { it.title == title },
                    theaterChapters = current.theaterChapters - title,
                    theaterGuides = current.theaterGuides - title,
                )
            }
        }
    }

    private suspend fun generateTheaterChapterContent(
        seed: StarWishTheaterSeed,
        chapters: List<StarWishTheaterChapter>,
        nextChapter: Int,
        influence: String,
        guide: StarWishTheaterGuide,
    ): GeneratedTheaterChapterContent {
        val settings = settingsStore.settingsFlow.first()
        val selectedModel: Model? = settings.theaterModelId?.let { settings.findModelById(it) }
        val model = selectedModel
            ?.takeIf { selected -> selected.type == ModelType.CHAT }
            ?: error("请先在默认模型里设置“小剧场模型”，用来生成小剧场正文。")
        val providerSetting = model.findProvider(settings.providers)
            ?: error("小剧场模型没有找到对应提供商。")
        val provider = providerManager.getProviderByType(providerSetting)
        val assistant = settings.getCurrentAssistant()
        val companionContext = companionRuntime.perception(
            CompanionPerceptionInput(
                assistantId = assistant.id.toString(),
                assistantName = assistant.name,
                persona = assistant.systemPrompt,
                nowMillis = System.currentTimeMillis(),
            ),
        ).toPromptContext()
        val prompt = StarWishRules.theaterChapterPrompt(seed, chapters, nextChapter, influence, guide)
        val messages = transformMessages(
            messages = listOf(
                UIMessage.system(buildString {
                    appendLine(
                        "小剧场中的核心陪伴角色是 ${assistant.name.ifBlank { "当前角色" }}，" +
                            "必须遵守其人设、关系边界与语言习惯。",
                    )
                    if (assistant.systemPrompt.isNotBlank()) {
                        appendLine("角色人设：")
                        appendLine(assistant.systemPrompt)
                    }
                    if (assistant.appearancePrompt.isNotBlank()) {
                        appendLine("角色外貌：")
                        appendLine(assistant.appearancePrompt)
                    }
                }.trim()),
                companionContext.takeIf(String::isNotBlank)?.let(UIMessage::system),
                UIMessage.user(prompt),
            ).filterNotNull(),
            assistant = assistant,
            modeInjections = settings.modeInjections,
            lorebooks = settings.lorebooks,
        )
        val chunk = provider.generateText(
            providerSetting = providerSetting,
            messages = messages,
            params = TextGenerationParams(
                model = model,
                temperature = 0.9f,
                topP = 0.95f,
                maxTokens = 3200,
                reasoningLevel = ReasoningLevel.OFF,
            ),
        )
        chunk.usage?.let { usage ->
            apiUsageStore.record(
                source = ApiUsageSource.OTHER,
                title = "星愿馆：小剧场",
                model = model.displayName.ifBlank { model.modelId },
                provider = providerSetting.name.ifBlank { providerSetting.id.toString() },
                usage = usage,
            )
        }
        val content = chunk.choices.firstOrNull()?.message?.toText()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: error("小剧场 API 没有返回正文。")
        return GeneratedTheaterChapterContent(
            content = content,
            assistantId = assistant.id.toString(),
        )
    }

    private data class GeneratedTheaterChapterContent(
        val content: String,
        val assistantId: String,
    )

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

}

private fun StarWishTheaterChapter.isPromptPlaceholder(seed: StarWishTheaterSeed): Boolean {
    val clean = content.trim()
    return clean == seed.prompt.trim() ||
        clean.startsWith("总设定：") ||
        clean.startsWith("你是一个擅长") ||
        clean.contains("硬性要求：") ||
        clean.contains("请根据下面设定生成")
}
