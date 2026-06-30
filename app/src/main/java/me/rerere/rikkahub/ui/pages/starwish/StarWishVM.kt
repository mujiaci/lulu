package me.rerere.rikkahub.ui.pages.starwish

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.GenMediaRepository
import me.rerere.rikkahub.data.starwish.StarWishGeneratedImage
import me.rerere.rikkahub.data.starwish.StarWishImageLaunch
import me.rerere.rikkahub.data.starwish.StarWishOutfitPrompts
import me.rerere.rikkahub.data.starwish.StarWishRules
import me.rerere.rikkahub.data.starwish.StarWishState
import me.rerere.rikkahub.data.starwish.StarWishStore
import me.rerere.rikkahub.data.starwish.StarWishTheaterChapter
import me.rerere.rikkahub.data.study.StudyStore

class StarWishVM(
    private val store: StarWishStore,
    studyStore: StudyStore,
    private val genMediaRepository: GenMediaRepository,
    private val filesManager: FilesManager,
) : ViewModel() {
    val state: StateFlow<StarWishState> = store.state
    val studyState = studyStore.state
    private val _generatedImages = MutableStateFlow<List<StarWishGeneratedImage>>(emptyList())
    val generatedImages = _generatedImages.asStateFlow()

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

    fun createNextChapter(theater: String) {
        viewModelScope.launch {
            store.update { current ->
                val chapters = current.theaterChapters[theater].orEmpty()
                val nextChapter = chapters.size + 1
                val chapter = StarWishTheaterChapter(
                    id = "theater-${System.currentTimeMillis()}-${theater.hashCode()}",
                    theater = theater,
                    chapter = nextChapter,
                    title = "第 $nextChapter 章",
                    content = StarWishRules.defaultTheaterChapter(theater, nextChapter),
                    createdAt = System.currentTimeMillis(),
                )
                current.copy(theaterChapters = current.theaterChapters + (theater to (chapters + chapter)))
            }
        }
    }
}
