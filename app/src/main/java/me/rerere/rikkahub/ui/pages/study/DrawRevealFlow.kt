package me.rerere.rikkahub.ui.pages.study

import me.rerere.rikkahub.data.study.StudyDrawResult
import me.rerere.rikkahub.data.study.StudyRarity

enum class DrawRevealPhase {
    RainbowOpeningVideo,
    EpicOpeningVideo,
    RareOpeningVideo,
    Card,
    RewardVideo,
    Summary,
    Done,
}

data class DrawRevealState(
    val index: Int,
    val phase: DrawRevealPhase,
    val lastIndex: Int,
)

object DrawRevealFlow {
    fun start(results: List<StudyDrawResult>): DrawRevealState {
        if (results.isEmpty()) return DrawRevealState(index = -1, phase = DrawRevealPhase.Done, lastIndex = -1)
        return DrawRevealState(
            index = 0,
            phase = when {
                results.any { it.rarity == StudyRarity.Rainbow } -> DrawRevealPhase.RainbowOpeningVideo
                results.any { it.rarity == StudyRarity.Epic } -> DrawRevealPhase.EpicOpeningVideo
                results.any { it.rarity == StudyRarity.Rare } -> DrawRevealPhase.RareOpeningVideo
                else -> DrawRevealPhase.Card
            },
            lastIndex = results.lastIndex,
        )
    }

    fun videoFinished(state: DrawRevealState, results: List<StudyDrawResult>): DrawRevealState {
        if (state.phase !in openingVideoPhases || state.index !in results.indices) return state
        return state.copy(phase = DrawRevealPhase.Card)
    }

    fun next(state: DrawRevealState, results: List<StudyDrawResult>): DrawRevealState {
        if (results.isEmpty() || state.phase == DrawRevealPhase.Done) {
            return DrawRevealState(index = -1, phase = DrawRevealPhase.Done, lastIndex = -1)
        }
        if (state.index >= results.lastIndex) return state.copy(index = results.lastIndex, phase = DrawRevealPhase.Done)
        val nextIndex = state.index + 1
        return state.copy(
            index = nextIndex,
            phase = DrawRevealPhase.Card,
        )
    }

    fun summary(state: DrawRevealState): DrawRevealState = state.copy(index = state.lastIndex, phase = DrawRevealPhase.Summary)

    fun skip(state: DrawRevealState, results: List<StudyDrawResult>): DrawRevealState {
        if (results.isEmpty()) return DrawRevealState(index = -1, phase = DrawRevealPhase.Done, lastIndex = -1)
        if (state.phase in openingVideoPhases) return videoFinished(state, results)
        return summary(state)
    }

    fun pendingRewardVideoIndexes(videoIndexes: Set<Int>, playedIndexes: Set<Int>): List<Int> =
        (videoIndexes - playedIndexes).sorted()

    private val openingVideoPhases = setOf(
        DrawRevealPhase.RainbowOpeningVideo,
        DrawRevealPhase.EpicOpeningVideo,
        DrawRevealPhase.RareOpeningVideo,
    )
}
