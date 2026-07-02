package me.rerere.rikkahub.data.study

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamStudyPlanTest {
    @Test
    fun tomorrowPlanReadsTheNextCalendarDay() {
        val tomorrow = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 2).plusDays(1))

        assertEquals("法理3 + 民法1", tomorrow?.title)
    }

    @Test
    fun vocabularyPlanTreatsBacklogAsTwentyWordGroups() {
        assertEquals(1550, ExamStudyPlan.vocabularyBacklog)
        assertEquals(listOf(40, 60, 80, 100), ExamStudyPlan.vocabularyDailyOptions)
        assertTrue(ExamStudyPlan.vocabularyDailyOptions.all { it % 20 == 0 })
    }

    @Test
    fun julySecondDoesNotForceVocabularyReview() {
        val plan = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 2))
        val taskTitles = plan?.tasks.orEmpty().map { it.title }

        assertTrue(taskTitles.any { it.contains("英语长难句 1 句") })
        assertFalse(taskTitles.any { it.contains("单词") || it.contains("不背单词") })
    }
}
