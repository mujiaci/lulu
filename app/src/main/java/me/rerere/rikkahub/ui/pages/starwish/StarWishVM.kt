package me.rerere.rikkahub.ui.pages.starwish

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
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
            val current = state.value
            val launches = current.imageLaunches.filterNot { it.id in current.hiddenImageLaunchIds }
            val imagesDir = filesManager.getImagesDir()
            val media = genMediaRepository.listAllMedia()
            _generatedImages.value = media.mapNotNull { entity ->
                if (entity.id in current.hiddenGeneratedImageIds) return@mapNotNull null
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

    fun redeemVideo() {
        viewModelScope.launch {
            studyStore.update { current -> StudyRules.redeemVideo(current).state }
        }
    }

    fun clearVideoRecords() {
        viewModelScope.launch {
            studyStore.update { current ->
                current.copy(stats = current.stats.copy(videoRewardsRedeemed = 0))
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
                )
            }
        }
    }

    fun deleteSpecialStory(title: String) {
        viewModelScope.launch {
            store.update { current ->
                current.copy(
                    hiddenSpecialStoryTitles = current.hiddenSpecialStoryTitles + title,
                    customSpecialStories = current.customSpecialStories.filterNot { it.title == title },
                    specialStoryChapters = current.specialStoryChapters - title,
                )
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
        val selectedModel: Model? = settings.theaterModelId?.let { settings.findModelById(it) }
        val model = selectedModel
            ?.takeIf { selected -> selected.type == ModelType.CHAT }
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
        if (cleanTitle.isBlank()) return
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

private fun StarWishTheaterChapter.isPromptPlaceholder(seed: StarWishTheaterSeed): Boolean {
    val clean = content.trim()
    return clean == seed.prompt.trim() ||
        clean.startsWith("总设定：") ||
        clean.startsWith("你是一个擅长") ||
        clean.contains("硬性要求：") ||
        clean.contains("请根据下面设定生成")
}
