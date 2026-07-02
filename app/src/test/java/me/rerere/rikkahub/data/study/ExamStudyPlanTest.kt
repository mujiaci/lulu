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
        assertEquals(120, ExamStudyPlan.dailyVocabularyTarget)
        assertEquals(6, ExamStudyPlan.dailyVocabularyGroups)
        assertEquals(listOf(120), ExamStudyPlan.vocabularyDailyOptions)
        assertTrue(ExamStudyPlan.dailyVocabularyTarget % 20 == 0)
    }

    @Test
    fun formalStudyDaysIncludeFixedVocabularyReview() {
        val plan = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 2))
        val taskTitles = plan?.tasks.orEmpty().map { it.title }

        assertTrue(taskTitles.any { it.contains("英语长难句 1 句") })
        assertTrue(taskTitles.any { it.contains("不背单词 120 个（6 组）") })
        assertTrue(taskTitles.any { it.contains("听众合法硕刑法课程") })
        assertFalse(taskTitles.any { it.contains("不背单词 60") || it.contains("不背单词 80") || it.contains("不背单词 100") })
    }

    @Test
    fun schedulePromptIncludesHabitsAndManualTasks() {
        val prompt = ExamStudyPlan.dynamicSchedulePrompt(
            date = LocalDate.of(2026, 7, 2),
            presetPlan = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 2)),
            defaultSchedule = ExamStudyPlan.todaySchedule(LocalDate.of(2026, 7, 2)),
            tasks = listOf(
                StudyTask(id = "manual-1", title = "9点起床，23点睡觉"),
                StudyTask(id = "manual-2", title = "写论文 50分钟"),
            ),
        )

        assertTrue(prompt.contains("众合法硕"))
        assertTrue(prompt.contains("不背单词 120 个"))
        assertTrue(prompt.contains("写论文 50分钟"))
        assertTrue(prompt.contains("9点起床，23点睡觉"))
    }

    @Test
    fun parseScheduleBlocksReadsPipeSeparatedLines() {
        val blocks = ExamStudyPlan.parseScheduleBlocks(
            """
            09:00-09:20｜启动｜整理桌面，确认今天三件事
            09:30-10:20 | 专业课 | 听众合法硕刑法课程第 1 章
            19:30-20:10｜英语｜不背单词 120 个（6 组）+ 长难句 1 句
            """.trimIndent(),
        )

        assertEquals(3, blocks.size)
        assertEquals("09:30-10:20", blocks[1].time)
        assertEquals("专业课", blocks[1].title)
        assertEquals("听众合法硕刑法课程第 1 章", blocks[1].detail)
    }
}
