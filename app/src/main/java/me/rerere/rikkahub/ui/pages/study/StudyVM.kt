package me.rerere.rikkahub.ui.pages.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.ApiUsageSource
import me.rerere.rikkahub.data.ai.ApiUsageStore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.study.ExamStudyPlan
import me.rerere.rikkahub.data.study.StudyDrawResult
import me.rerere.rikkahub.data.study.StudyMysteryBoxReward
import me.rerere.rikkahub.data.study.StudyRules
import me.rerere.rikkahub.data.study.StudyShopItem
import me.rerere.rikkahub.data.study.StudyState
import me.rerere.rikkahub.data.study.StudyStore
import me.rerere.rikkahub.data.study.SuperMomentChoice
import me.rerere.rikkahub.data.starwish.StarWishVideoItem
import me.rerere.rikkahub.data.starwish.StarWishRules
import me.rerere.rikkahub.data.starwish.StarWishStore
import me.rerere.rikkahub.data.study.StudyFragmentType
import me.rerere.rikkahub.data.study.StudyEntertainmentReward
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

class StudyVM(
    private val store: StudyStore,
    private val starWishStore: StarWishStore,
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
    private val apiUsageStore: ApiUsageStore,
) : ViewModel() {
    val state: StateFlow<StudyState> = store.state

    private val _effects = MutableSharedFlow<StudyEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<StudyEffect> = _effects
    private val _isGeneratingSchedule = MutableStateFlow(false)
    val isGeneratingSchedule = _isGeneratingSchedule.asStateFlow()

    fun syncToday() = reduce { StudyRules.rolloverToDate(it, LocalDate.now()) }

    fun selectCompanion(assistantId: String) = reduce { StudyRules.selectCompanion(it, assistantId) }

    fun signIn() = reduce {
        val result = StudyRules.signIn(it, LocalDate.now())
        emitReward(result.reward.title)
        result.state
    }

    fun addTask(title: String) = reduce {
        if (title.isBlank()) return@reduce it
        StudyRules.clearGeneratedSchedule(StudyRules.addTask(it, title), LocalDate.now())
    }

    fun deleteTask(id: String) = reduce {
        StudyRules.clearGeneratedSchedule(StudyRules.deleteTask(it, id), LocalDate.now())
    }

    fun generateTodaySchedule() {
        viewModelScope.launch {
            if (_isGeneratingSchedule.value) return@launch
            _isGeneratingSchedule.value = true
            try {
                val date = LocalDate.now()
                val currentTime = LocalTime.now()
                val currentState = state.value
                val settings = settingsStore.settingsFlow.first()
                val assistant = settings.getCurrentAssistant()
                val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId)
                    ?.takeIf { it.type == ModelType.CHAT }
                    ?: error("请先设置主聊天模型，再生成今日计划。")
                val providerSetting = model.findProvider(settings.providers)
                    ?: error("当前主聊天模型没有找到对应提供商。")
                val provider = providerManager.getProviderByType(providerSetting)
                val prompt = ExamStudyPlan.dynamicSchedulePrompt(
                    date = date,
                    presetPlan = ExamStudyPlan.todayPlan(date),
                    defaultSchedule = ExamStudyPlan.todaySchedule(date),
                    tasks = currentState.tasks,
                    currentTime = currentTime,
                )
                val chunk = provider.generateText(
                    providerSetting = providerSetting,
                    messages = listOf(
                        UIMessage.system(ExamStudyPlan.dynamicScheduleSystemPrompt),
                        UIMessage.user(prompt),
                    ),
                    params = TextGenerationParams(
                        model = model,
                        temperature = 0.35f,
                        topP = 0.9f,
                        maxTokens = 1800,
                        reasoningLevel = ReasoningLevel.OFF,
                    ),
                )
                chunk.usage?.let { usage ->
                    apiUsageStore.record(
                        source = ApiUsageSource.OTHER,
                        title = "学习：今日计划",
                        model = model.displayName.ifBlank { model.modelId },
                        provider = providerSetting.name.ifBlank { providerSetting.id.toString() },
                        usage = usage,
                    )
                }
                val text = chunk.choices.firstOrNull()?.message?.toText().orEmpty()
                val schedule = ExamStudyPlan.scheduleBlocksFromTime(
                    blocks = ExamStudyPlan.parseScheduleBlocks(text),
                    currentTime = currentTime,
                )
                if (schedule.isEmpty()) {
                    error("主 API 没有返回可读取的时间表，请再点一次生成。")
                }
                store.update { current ->
                    StudyRules.saveGeneratedSchedule(current, date, schedule)
                }
                _effects.tryEmit(StudyEffect.Message("今日计划表已生成"))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _effects.tryEmit(StudyEffect.Message(e.message ?: "今日计划表生成失败"))
            } finally {
                _isGeneratingSchedule.value = false
            }
        }
    }

    fun toggleTask(id: String, done: Boolean) = reduce {
        val result = StudyRules.toggleTask(it, id, done)
        emitReward(result.reward.title)
        if (result.state.superMomentAvailable && !it.superMomentAvailable) {
            _effects.tryEmit(StudyEffect.SuperMomentReady)
        }
        result.state
    }

    fun completePomodoro(minutes: Int) = reduce {
        val result = StudyRules.completePomodoro(it, minutes, Random.Default)
        emitReward(result.reward.title)
        result.state
    }

    fun openMysteryBox(index: Int = 0) = reduce {
        val result = StudyRules.openMysteryBox(it, index)
        if (result.reward.title.isNotBlank()) {
            _effects.tryEmit(
                StudyEffect.MysteryBox(
                    StudyMysteryBoxReward(
                        kudos = result.reward.kudos,
                        universalNormalFragments = result.reward.universalNormalFragments,
                    ),
                ),
            )
        }
        result.state
    }

    fun draw(count: Int) {
        viewModelScope.launch {
            var revealItems: List<StudyDrawReveal> = emptyList()
            var message: String? = null
            var updatedStarWish = starWishStore.state.value
            store.update { current ->
                val result = StudyRules.draw(current, count, Random.Default)
                if (result.results.isEmpty()) {
                    message = "夸夸值或抽卡券不够"
                    return@update result.state
                }
                var updatedStudy = result.state
                revealItems = result.results.map { drawResult ->
                    if (drawResult.fragmentType != StudyFragmentType.Video) {
                        StudyDrawReveal(drawResult)
                    } else {
                        val unlock = StarWishRules.unlockNextVideo(updatedStarWish, updatedStudy, Random.Default)
                        updatedStarWish = unlock.starWishState
                        updatedStudy = unlock.studyState
                        StudyDrawReveal(drawResult, unlock.video)
                    }
                }
                updatedStudy
            }
            message?.let {
                _effects.tryEmit(StudyEffect.Message(it))
                return@launch
            }
            starWishStore.update { updatedStarWish }
            _effects.tryEmit(StudyEffect.DrawResults(revealItems))
        }
    }

    fun drawPurpleTicket() {
        viewModelScope.launch {
            var revealItems: List<StudyDrawReveal> = emptyList()
            store.update { current ->
                val result = StudyRules.drawPurpleTicket(current, Random.Default)
                revealItems = result.results.map { drawResult -> StudyDrawReveal(drawResult) }
                result.state
            }
            if (revealItems.isEmpty()) {
                _effects.tryEmit(StudyEffect.Message("没有可用的今日零紫安全抽"))
            } else {
                _effects.tryEmit(StudyEffect.DrawResults(revealItems))
            }
        }
    }

    fun claimSuperMoment(choice: SuperMomentChoice) = reduce {
        val result = StudyRules.claimSuperMoment(it, choice)
        emitReward(result.reward.title)
        result.state
    }

    fun claimAchievement(id: String) = reduce {
        val result = StudyRules.claimAchievement(it, id)
        emitReward(result.reward.title)
        result.state
    }

    fun claimLevel(level: Int) = reduce {
        val result = StudyRules.claimLevelReward(it, level)
        emitReward(result.reward.title)
        result.state
    }

    fun refreshShop() = reduce { StudyRules.manualRefreshShop(it, LocalDate.now(), Random.Default) }

    fun buyShopItem(item: StudyShopItem) = reduce {
        val result = StudyRules.buyShopItem(it, item.id)
        emitReward(result.reward.title.ifBlank { "购买失败，夸夸值不够或商品已售罄" })
        result.state
    }

    fun redeemEntertainment(rewardType: StudyEntertainmentReward) = reduce {
        val result = StudyRules.redeemEntertainment(it, rewardType)
        emitReward(result.reward.title.ifBlank { "还需要 1 个对应碎片" })
        result.state
    }

    fun applyUniversalNormal(key: String) = reduce {
        val result = StudyRules.useUniversalNormalFragment(it, key)
        emitReward(result.reward.title)
        result.state
    }

    fun applyBestUniversalNormal() = reduce {
        val key = StudyRules.bestNormalFragmentTarget(it)
        val result = key?.let { target -> StudyRules.useUniversalNormalFragment(it, target) }
        if (result == null) {
            _effects.tryEmit(StudyEffect.Message("普通套装已经全部补满"))
            it
        } else {
            emitReward(result.reward.title)
            result.state
        }
    }

    fun applyPenalty() = reduce {
        val result = StudyRules.applyInactivityPenalty(it)
        emitReward(result.reward.title)
        result.state
    }

    private fun reduce(transform: (StudyState) -> StudyState) {
        viewModelScope.launch {
            store.update(transform)
        }
    }

    private fun emitReward(title: String) {
        if (title.isNotBlank()) {
            _effects.tryEmit(StudyEffect.Message(title))
        }
    }
}

sealed interface StudyEffect {
    data class Message(val text: String) : StudyEffect
    data object MysteryBoxReady : StudyEffect
    data class MysteryBox(val reward: StudyMysteryBoxReward) : StudyEffect
    data class DrawResults(val results: List<StudyDrawReveal>) : StudyEffect
    data object SuperMomentReady : StudyEffect
}

data class StudyDrawReveal(
    val result: StudyDrawResult,
    val video: StarWishVideoItem? = null,
)
