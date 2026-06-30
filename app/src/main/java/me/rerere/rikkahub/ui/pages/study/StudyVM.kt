package me.rerere.rikkahub.ui.pages.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.study.StudyDrawResult
import me.rerere.rikkahub.data.study.StudyRules
import me.rerere.rikkahub.data.study.StudyShopItem
import me.rerere.rikkahub.data.study.StudyState
import me.rerere.rikkahub.data.study.StudyStore
import me.rerere.rikkahub.data.study.SuperMomentChoice
import java.time.LocalDate
import kotlin.random.Random

class StudyVM(
    private val store: StudyStore,
) : ViewModel() {
    val state: StateFlow<StudyState> = store.state

    private val _effects = MutableSharedFlow<StudyEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<StudyEffect> = _effects

    fun signIn() = reduce {
        val result = StudyRules.signIn(it, LocalDate.now())
        emitReward(result.reward.title)
        result.state
    }

    fun addTask(title: String) = reduce { StudyRules.addTask(it, title) }

    fun deleteTask(id: String) = reduce { StudyRules.deleteTask(it, id) }

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
        _effects.tryEmit(StudyEffect.MysteryBox(result.reward.mysteryBoxKudos))
        result.state
    }

    fun draw(count: Int) = reduce {
        val result = StudyRules.draw(it, count, Random.Default)
        if (result.results.isNotEmpty()) {
            _effects.tryEmit(StudyEffect.DrawResults(result.results))
        } else {
            _effects.tryEmit(StudyEffect.Message("夸夸值或抽卡券不够"))
        }
        result.state
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

    fun redeemMcDonalds() = reduce {
        val result = StudyRules.redeemMcDonalds(it)
        if (result.reward.title.isBlank()) {
            _effects.tryEmit(StudyEffect.Message("还需要 2 个麦当劳碎片"))
        } else {
            _effects.tryEmit(StudyEffect.McDonaldsRedeemed)
        }
        result.state
    }

    fun applyUniversalNormal(key: String) = reduce {
        val result = StudyRules.useUniversalNormalFragment(it, key)
        emitReward(result.reward.title)
        result.state
    }

    fun applyUniversalRare(key: String) = reduce {
        val result = StudyRules.useUniversalRareFragment(it, key)
        emitReward(result.reward.title)
        result.state
    }

    fun applyUniversalEpic() = reduce {
        val result = StudyRules.useUniversalEpicFragment(it)
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

    fun applyBestUniversalRare() = reduce {
        val key = StudyRules.bestRareFragmentTarget(it)
        val result = key?.let { target -> StudyRules.useUniversalRareFragment(it, target) }
        if (result == null) {
            _effects.tryEmit(StudyEffect.Message("小剧场已经全部补满"))
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
            store.set(transform(state.value))
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
    data class MysteryBox(val kudos: Int) : StudyEffect
    data class DrawResults(val results: List<StudyDrawResult>) : StudyEffect
    data object McDonaldsRedeemed : StudyEffect
    data object SuperMomentReady : StudyEffect
}
