package me.rerere.rikkahub.ui.pages.study

import me.rerere.rikkahub.data.study.StudyDrawResult
import me.rerere.rikkahub.data.study.StudyRarity

enum class DrawRevealPhase {
    RainbowVideo,
    Card,
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
            phase = if (results.first().rarity == StudyRarity.Rainbow) DrawRevealPhase.RainbowVideo else DrawRevealPhase.Card,
            lastIndex = results.lastIndex,
        )
    }

    fun videoFinished(state: DrawRevealState, results: List<StudyDrawResult>): DrawRevealState {
        if (state.phase != DrawRevealPhase.RainbowVideo || state.index !in results.indices) return state
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
            phase = if (results[nextIndex].rarity == StudyRarity.Rainbow) DrawRevealPhase.RainbowVideo else DrawRevealPhase.Card,
        )
    }

    fun summary(state: DrawRevealState): DrawRevealState = state.copy(index = state.lastIndex, phase = DrawRevealPhase.Summary)

    fun skipToNextRainbowVideoOrSummary(
        state: DrawRevealState,
        results: List<StudyDrawResult>,
    ): DrawRevealState {
        if (results.isEmpty()) return DrawRevealState(index = -1, phase = DrawRevealPhase.Done, lastIndex = -1)
        val nextIndex = results
            .withIndex()
            .firstOrNull { (index, result) -> index > state.index && result.rarity == StudyRarity.Rainbow }
            ?.index
        return if (nextIndex == null) {
            summary(state)
        } else {
            state.copy(index = nextIndex, phase = DrawRevealPhase.RainbowVideo)
        }
    }

    fun skip(state: DrawRevealState, results: List<StudyDrawResult>): DrawRevealState =
        skipToNextRainbowVideoOrSummary(state, results)
}
