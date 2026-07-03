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

        assertEquals("病后慢启动：保底 + 可加码", tomorrow?.title)
    }

    @Test
    fun recoveryDayStillGivesAtLeastOneHourOfConcreteStudy() {
        val plan = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 3))
        val schedule = ExamStudyPlan.todaySchedule(LocalDate.of(2026, 7, 3))
        val text = schedule.joinToString("\n") { "${it.time} ${it.title} ${it.detail}" }

        assertEquals("病后慢启动：保底 + 可加码", plan?.title)
        assertTrue(text.contains("法理学第 2 章"))
        assertTrue(text.contains("听众合法硕刑法课程"))
        assertTrue(text.contains("不背单词 120 个"))
    }

    @Test
    fun partialChapterLessonDoesNotScheduleSeparatePractice() {
        val plan = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 4))
        val titles = plan?.tasks.orEmpty().joinToString("\n") { it.title }

        assertTrue(titles.contains("不单独安排题目"))
        assertTrue(titles.contains("课上例题算在听课里"))
        assertFalse(titles.contains("已学内容小测"))
        assertFalse(titles.contains("例题回忆"))
        assertFalse(titles.contains("做 8-10 题"))
        assertFalse(titles.contains("章节题 5 道"))
    }

    @Test
    fun closedChapterDaySchedulesChapterPracticeAndCorrection() {
        val plan = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 13))
        val titles = plan?.tasks.orEmpty().joinToString("\n") { it.title }

        assertTrue(titles.contains("刑法第 1 章：整章章节题"))
        assertTrue(titles.contains("错题"))
        assertTrue(titles.contains("第一轮关键词背诵"))
    }

    @Test
    fun lawTheoryIsRecitedInsteadOfOnlySkimmed() {
        val july = ExamStudyPlan.monthlyPlans.single { it.month == "2026-07" }
        val text = july.tasks.joinToString("\n")

        assertTrue(text.contains("法理学不重听"))
        assertTrue(text.contains("第一轮背诵"))
        assertTrue(text.contains("不是只看目录"))
        assertTrue(ExamStudyPlan.studyHabitReference.contains("单纯看框架不算"))
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

        assertTrue(taskTitles.any { it.contains("长难句 1 句") })
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
        assertTrue(prompt.contains("这一章没有结束前不要单独安排题目"))
        assertTrue(prompt.contains("单纯看框架不算"))
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
