package me.rerere.rikkahub.ui.pages.imggen

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.rerere.ai.provider.ImageEditParams
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.ui.ImageAspectRatio
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.db.entity.GenMediaEntity
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.GenMediaRepository
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

@Serializable
data class GeneratedImage(
    val id: Int,
    val prompt: String,
    val filePath: String,
    val timestamp: Long,
    val model: String
)

private fun GenMediaEntity.toGeneratedImage(filesManager: FilesManager): GeneratedImage {
    val imagesDir = filesManager.getImagesDir()
    val fullPath = File(imagesDir, this.path.removePrefix("images/")).absolutePath

    return GeneratedImage(
        id = this.id,
        prompt = this.prompt,
        filePath = fullPath,
        timestamp = this.createAt,
        model = this.modelId
    )
}

internal fun mergeEffectiveReferenceImages(
    assistantFaceReference: String?,
    manualReferences: List<String>,
    maxImages: Int = 16,
): List<String> = buildList {
    assistantFaceReference?.takeIf { it.isNotBlank() }?.let(::add)
    manualReferences.filter { it.isNotBlank() }.forEach(::add)
}.distinct().take(maxImages)

private fun promptWithFaceReferenceFallback(prompt: String, hasFaceReference: Boolean): String {
    if (!hasFaceReference) return prompt
    return buildString {
        append("请尽量保持当前角色脸部参考图中的脸型、眼睛、发型和整体气质一致。")
        append("如果图片参考接口不可用，请仅按这条文字要求稳定角色脸，不要改变角色核心辨识度。\n")
        append(prompt)
    }
}

class ImgGenVM(
    context: Application,
    val settingsStore: SettingsStore,
    val providerManager: ProviderManager,
    val genMediaRepository: GenMediaRepository,
    private val filesManager: FilesManager,
) : AndroidViewModel(context) {
    private val _prompt = MutableStateFlow("")
    val prompt: StateFlow<String> = _prompt

    private val _numberOfImages = MutableStateFlow(1)
    val numberOfImages: StateFlow<Int> = _numberOfImages

    private val _aspectRatio = MutableStateFlow(ImageAspectRatio.PORTRAIT)
    val aspectRatio: StateFlow<ImageAspectRatio> = _aspectRatio

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating
    private var cancelJob: Job? = null
    private var generationRunId: Long = 0L

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _currentGeneratedImages = MutableStateFlow<List<GeneratedImage>>(emptyList())
    val currentGeneratedImages: StateFlow<List<GeneratedImage>> = _currentGeneratedImages

    private val _referenceImages = MutableStateFlow<List<String>>(emptyList())
    val referenceImages: StateFlow<List<String>> = _referenceImages
    private var appliedInitialRequestKey: String? = null

    val pager = Pager(
        config = PagingConfig(pageSize = 20, enablePlaceholders = false),
        pagingSourceFactory = { genMediaRepository.getAllMedia() }
    )
    val generatedImages: Flow<PagingData<GeneratedImage>> = pager.flow
        .map { pagingData ->
            pagingData.map { entity -> entity.toGeneratedImage(filesManager) }
        }
        .cachedIn(viewModelScope)

    fun updatePrompt(prompt: String) {
        _prompt.value = prompt
    }

    fun updateNumberOfImages(count: Int) {
        _numberOfImages.value = count.coerceIn(1, 4)
    }

    fun updateAspectRatio(aspectRatio: ImageAspectRatio) {
        _aspectRatio.value = aspectRatio
    }

    fun applyInitialRequest(prompt: String, count: Int, autoGenerate: Boolean) {
        if (prompt.isBlank()) return
        val key = "$prompt|$count|$autoGenerate"
        if (appliedInitialRequestKey == key) return
        appliedInitialRequestKey = key
        _prompt.value = prompt
        _numberOfImages.value = count.coerceIn(1, 4)
        if (autoGenerate) {
            generateImage()
        }
    }

    fun addReferenceImages(paths: List<String>) {
        _referenceImages.value = (_referenceImages.value + paths).distinct().take(MAX_REFERENCE_IMAGES)
    }

    fun removeReferenceImage(path: String) {
        _referenceImages.value = _referenceImages.value.filterNot { it == path }
        deleteReferenceFiles(listOf(path))
    }

    fun clearReferenceImages() {
        deleteReferenceFiles(_referenceImages.value)
        _referenceImages.value = emptyList()
    }

    fun clearError() {
        _error.value = null
    }

    fun startNewSession() {
        generationRunId++
        cancelJob?.cancel()
        cancelJob = null
        clearReferenceImages()
        _prompt.value = ""
        _currentGeneratedImages.value = emptyList()
        _error.value = null
        _isGenerating.value = false
    }

    fun generateImage() {
        if(prompt.value.isBlank()) return
        cancelJob?.cancel()
        val runId = ++generationRunId
        cancelJob = viewModelScope.launch {
            try {
                _isGenerating.value = true
                _error.value = null
                _currentGeneratedImages.value = emptyList()

                val settings = settingsStore.settingsFlow.first()
                val model = settings.findModelById(settings.imageGenerationModelId)
                    ?: throw IllegalStateException(IMAGE_MODEL_NOT_SELECTED)
                if (model.type != ModelType.IMAGE) {
                    throw IllegalStateException(IMAGE_MODEL_NOT_SELECTED)
                }

                val provider = model.findProvider(settings.providers)
                    ?: throw IllegalStateException("Provider not found")

                val providerSetting = settings.providers.find { it.id == provider.id }
                    ?: throw IllegalStateException("Provider setting not found")

                val params = ImageGenerationParams(
                    model = model,
                    prompt = _prompt.value,
                    numOfImages = _numberOfImages.value,
                    aspectRatio = _aspectRatio.value,
                    customHeaders = model.customHeaders,
                    customBody = model.customBodies
                )

                val result = providerManager.getProviderByType(provider)
                    .generateImage(providerSetting, params)

                val newImages = mutableListOf<GeneratedImage>()

                result.items.forEachIndexed { index, item ->
                    coroutineContext.ensureActive()
                    val imageFile = saveImageToStorage(
                        item = item,
                        prompt = _prompt.value,
                        modelName = model.displayName,
                        index = index
                    )
                    val generatedImage = GeneratedImage(
                        id = 0, // Will be updated after database insertion
                        prompt = _prompt.value,
                        filePath = imageFile.absolutePath,
                        timestamp = System.currentTimeMillis(),
                        model = model.displayName
                    )
                    newImages.add(generatedImage)
                }

                if (runId == generationRunId) {
                    _currentGeneratedImages.value = newImages
                }
            } catch (e: Exception) {
                if(e is CancellationException) return@launch
                Log.e(TAG, "Failed to generate image", e)
                if (runId == generationRunId) {
                    _error.value = e.toFriendlyImageError()
                }
            } finally {
                if (runId == generationRunId) {
                    _isGenerating.value = false
                }
            }
        }
    }

    fun editImage() {
        if (prompt.value.isBlank()) return
        cancelJob?.cancel()
        val runId = ++generationRunId
        cancelJob = viewModelScope.launch {
            try {
                _isGenerating.value = true
                _error.value = null
                _currentGeneratedImages.value = emptyList()

                val settings = settingsStore.settingsFlow.first()
                val model = settings.findModelById(settings.imageGenerationModelId)
                    ?: throw IllegalStateException(IMAGE_MODEL_NOT_SELECTED)
                if (model.type != ModelType.IMAGE) {
                    throw IllegalStateException(IMAGE_MODEL_NOT_SELECTED)
                }

                val provider = model.findProvider(settings.providers)
                    ?: throw IllegalStateException("Provider not found")

                val providerSetting = settings.providers.find { it.id == provider.id }
                    ?: throw IllegalStateException("Provider setting not found")

                val assistantFaceReference = settings.getCurrentAssistant().faceReferenceImage
                val sourceImages = mergeEffectiveReferenceImages(
                    assistantFaceReference = assistantFaceReference,
                    manualReferences = _referenceImages.value,
                    maxImages = MAX_REFERENCE_IMAGES,
                )
                if (sourceImages.isEmpty()) {
                    val params = ImageGenerationParams(
                        model = model,
                        prompt = _prompt.value,
                        numOfImages = _numberOfImages.value,
                        aspectRatio = _aspectRatio.value,
                        customHeaders = model.customHeaders,
                        customBody = model.customBodies
                    )
                    val result = providerManager.getProviderByType(provider)
                        .generateImage(providerSetting, params)
                    val newImages = mutableListOf<GeneratedImage>()

                    result.items.forEachIndexed { index, item ->
                        coroutineContext.ensureActive()
                        val imageFile = saveImageToStorage(
                            item = item,
                            prompt = _prompt.value,
                            modelName = model.displayName,
                            index = index,
                            type = GenMediaEntity.TYPE_IMAGE_GENERATION,
                        )
                        val generatedImage = GeneratedImage(
                            id = 0,
                            prompt = _prompt.value,
                            filePath = imageFile.absolutePath,
                            timestamp = System.currentTimeMillis(),
                            model = model.displayName
                        )
                        newImages.add(generatedImage)
                    }

                    if (runId == generationRunId) {
                        _currentGeneratedImages.value = newImages
                    }
                    return@launch
                }

                var resultType = GenMediaEntity.TYPE_IMAGE_EDIT
                val result = try {
                    val params = ImageEditParams(
                        model = model,
                        prompt = _prompt.value,
                        images = sourceImages,
                        numOfImages = _numberOfImages.value,
                        aspectRatio = _aspectRatio.value,
                        customHeaders = model.customHeaders,
                        customBody = model.customBodies
                    )

                    providerManager.getProviderByType(provider)
                        .editImage(providerSetting, params)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    resultType = GenMediaEntity.TYPE_IMAGE_GENERATION
                    val fallbackParams = ImageGenerationParams(
                        model = model,
                        prompt = promptWithFaceReferenceFallback(
                            prompt = _prompt.value,
                            hasFaceReference = assistantFaceReference != null,
                        ),
                        numOfImages = _numberOfImages.value,
                        aspectRatio = _aspectRatio.value,
                        customHeaders = model.customHeaders,
                        customBody = model.customBodies
                    )
                    providerManager.getProviderByType(provider)
                        .generateImage(providerSetting, fallbackParams)
                }

                val newImages = mutableListOf<GeneratedImage>()

                result.items.forEachIndexed { index, item ->
                    coroutineContext.ensureActive()
                    val imageFile = saveImageToStorage(
                        item = item,
                        prompt = _prompt.value,
                        modelName = model.displayName,
                        index = index,
                        type = resultType,
                        sourcePaths = sourceImages.joinToString("|"),
                    )
                    val generatedImage = GeneratedImage(
                        id = 0, // Will be updated after database insertion
                        prompt = _prompt.value,
                        filePath = imageFile.absolutePath,
                        timestamp = System.currentTimeMillis(),
                        model = model.displayName
                    )
                    newImages.add(generatedImage)
                }

                if (runId == generationRunId) {
                    _currentGeneratedImages.value = newImages
                }
            } catch (e: Exception) {
                if (e is CancellationException) return@launch
                Log.e(TAG, "Failed to edit image", e)
                if (runId == generationRunId) {
                    _error.value = e.toFriendlyImageError()
                }
            } finally {
                if (runId == generationRunId) {
                    _isGenerating.value = false
                }
            }
        }
    }

    fun cancelGeneration() {
        generationRunId++
        cancelJob?.cancel()
        cancelJob = null
        _isGenerating.value = false
    }

    private suspend fun saveImageToStorage(
        item: ImageGenerationItem,
        prompt: String,
        modelName: String,
        index: Int,
        type: String = GenMediaEntity.TYPE_IMAGE_GENERATION,
        sourcePaths: String? = null,
    ): File {
        val imagesDir = filesManager.getImagesDir()

        val timestamp = System.currentTimeMillis()
        val filename = "${timestamp}_${modelName.toSafeFileNamePart()}_$index.png"
        val imageFile = File(imagesDir, filename)
        val sourceUrl = item.sourceUrl
        val data = item.data

        val createdFile = when {
            sourceUrl != null -> downloadGeneratedImageToFile(sourceUrl, imageFile)
            data != null -> filesManager.createImageFileFromBase64(data, imageFile.absolutePath)
            else -> error("Generated image has neither data nor source URL")
        }

        // Save to database with relative path
        val relativePath = "images/${imageFile.name}"
        val entity = GenMediaEntity(
            path = relativePath,
            modelId = modelName,
            prompt = prompt,
            createAt = timestamp,
            type = type,
            sourcePaths = sourcePaths,
        )
        genMediaRepository.insertMedia(entity)

        return createdFile
    }

    private suspend fun downloadGeneratedImageToFile(url: String, imageFile: File): File = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        imageFile.parentFile?.mkdirs()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("Failed to download generated image: ${connection.responseCode}")
            }
            connection.inputStream.use { input ->
                imageFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            coroutineContext.ensureActive()
            imageFile
        } finally {
            connection.disconnect()
        }
    }

    fun deleteImage(image: GeneratedImage) {
        viewModelScope.launch {
            try {
                // Delete from database first
                genMediaRepository.deleteMedia(image.id)

                // Then delete the file
                val file = File(image.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete image", e)
                _error.value = "Failed to delete image"
            }
        }
    }

    private fun deleteReferenceFiles(paths: List<String>) {
        viewModelScope.launch {
            paths.forEach { path ->
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    companion object {
        private const val TAG = "ImgGenVM"
        private const val MAX_REFERENCE_IMAGES = 16
        private const val IMAGE_MODEL_NOT_SELECTED = "当前默认图像模型不是图片生成模型。请点输入框旁边的模型按钮，选择类型为 IMAGE 的生图模型后再生成。"
    }
}

internal fun String.toSafeFileNamePart(): String =
    replace(Regex("""[\\/:*?"<>|]"""), "_")
        .replace(Regex("""\s+"""), "_")
        .trim('_')
        .ifBlank { "image_model" }
        .take(80)

private fun Throwable.toFriendlyImageError(): String {
    val raw = message.orEmpty()
    return when {
        raw.contains("not supported model for image generation", ignoreCase = true) ||
            raw.contains("only imagen models are supported", ignoreCase = true) ||
            raw.contains("bad_response_status_code", ignoreCase = true) ||
            raw.contains("404", ignoreCase = true) ->
            "生图模型不匹配或接口不支持当前模型。请在生图页选择 IMAGE 类型模型，比如 Imagen / gpt-image 系列，再重新生成。原始错误：$raw"
        raw.isBlank() -> "生图失败，但没有返回具体错误。请先确认默认图像模型和 API Key。"
        else -> raw
    }
}
