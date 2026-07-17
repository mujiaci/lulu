package me.rerere.rikkahub.ui.pages.study

import me.rerere.rikkahub.data.study.StudyDrawResult
import me.rerere.rikkahub.data.study.StudyRarity
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawRevealFlowTest {
    @Test
    fun drawWithAnyRainbowStartsWithRainbowOpeningVideoBeforeFirstCard() {
        val results = listOf(draw(StudyRarity.Normal), draw(StudyRarity.Rainbow))

        val start = DrawRevealFlow.start(results)

        assertEquals(DrawRevealPhase.RainbowOpeningVideo, start.phase)
        assertEquals(0, start.index)

        val revealed = DrawRevealFlow.videoFinished(start, results)
        assertEquals(DrawRevealPhase.Card, revealed.phase)
        assertEquals(0, revealed.index)
    }

    @Test
    fun drawWithAnyEpicStartsWithOneOpeningVideoBeforeFirstCard() {
        val results = listOf(draw(StudyRarity.Normal), draw(StudyRarity.Epic))

        val start = DrawRevealFlow.start(results)

        assertEquals(DrawRevealPhase.EpicOpeningVideo, start.phase)
        assertEquals(0, start.index)

        val revealed = DrawRevealFlow.videoFinished(start, results)
        assertEquals(DrawRevealPhase.Card, revealed.phase)
        assertEquals(0, revealed.index)
    }

    @Test
    fun rainbowOpeningTakesPriorityWhenDrawContainsRainbowAndEpic() {
        val results = listOf(draw(StudyRarity.Epic), draw(StudyRarity.Rainbow))

        val start = DrawRevealFlow.start(results)

        assertEquals(DrawRevealPhase.RainbowOpeningVideo, start.phase)
        assertEquals(0, start.index)
    }

    @Test
    fun nextMovesHorizontallyThroughCardsWithoutAnotherOpeningVideoGate() {
        val results = listOf(draw(StudyRarity.Normal), draw(StudyRarity.Epic), draw(StudyRarity.Rare))

        val start = DrawRevealFlow.start(results)
        val firstCard = DrawRevealFlow.videoFinished(start, results)
        assertEquals(DrawRevealPhase.Card, firstCard.phase)
        assertEquals(0, firstCard.index)

        val epicCard = DrawRevealFlow.next(firstCard, results)
        assertEquals(DrawRevealPhase.Card, epicCard.phase)
        assertEquals(1, epicCard.index)

        val rareCard = DrawRevealFlow.next(epicCard, results)
        assertEquals(DrawRevealPhase.Card, rareCard.phase)
        assertEquals(2, rareCard.index)
    }

    @Test
    fun drawWithRareStartsWithPurpleOpeningVideo() {
        val results = listOf(draw(StudyRarity.Normal), draw(StudyRarity.Rare))

        val start = DrawRevealFlow.start(results)

        assertEquals(DrawRevealPhase.RareOpeningVideo, start.phase)
        assertEquals(0, start.index)
    }

    @Test
    fun skipMovesToSummaryAfterTheBatchOpeningVideo() {
        val results = listOf(draw(StudyRarity.Normal), draw(StudyRarity.Epic), draw(StudyRarity.Rare))
        val firstCard = DrawRevealFlow.videoFinished(DrawRevealFlow.start(results), results)

        val skipped = DrawRevealFlow.skip(firstCard, results)

        assertEquals(DrawRevealPhase.Summary, skipped.phase)
        assertEquals(results.lastIndex, skipped.index)
    }

    @Test
    fun skipDuringTheBatchOpeningVideoRevealsTheFirstCard() {
        val results = listOf(draw(StudyRarity.Rainbow), draw(StudyRarity.Epic))

        val skipped = DrawRevealFlow.skip(DrawRevealFlow.start(results), results)

        assertEquals(DrawRevealPhase.Card, skipped.phase)
        assertEquals(0, skipped.index)
    }

    @Test
    fun skipShowsSummaryWhenNoMoreOpeningVideosRemain() {
        val results = listOf(draw(StudyRarity.Epic), draw(StudyRarity.Rainbow))
        val firstCard = DrawRevealFlow.videoFinished(DrawRevealFlow.start(results), results)

        val skipped = DrawRevealFlow.skip(firstCard, results)

        assertEquals(DrawRevealPhase.Summary, skipped.phase)
        assertEquals(results.lastIndex, skipped.index)
    }

    private fun draw(rarity: StudyRarity) = StudyDrawResult(
        rarity = rarity,
        fragmentKey = rarity.name,
        title = "${rarity.label}碎片",
    )
}
