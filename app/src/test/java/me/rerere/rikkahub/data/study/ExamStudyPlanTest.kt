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
    fun unfinishedNewChaptersOnlyReviewHeardKeywords() {
        val text = listOf(
            ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 23)),
            ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 26)),
            ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 30)),
        ).flatMap { it?.tasks.orEmpty() }.joinToString("\n") { it.title }

        assertTrue(text.contains("未确认整章闭环前不算第一轮背诵"))
        assertTrue(text.contains("未闭环章节不算第一轮"))
        assertTrue(text.contains("没闭环就不挑题"))
        assertFalse(text.contains("刑法第 2 章第一轮关键词"))
        assertFalse(text.contains("刑法第 2-3 章第一轮"))
        assertFalse(text.contains("刑法第 3-4 章第一轮关键词"))
        assertFalse(text.contains("刑法第 1-2 章已闭环范围"))
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
    fun criminalAndCivilLawUseStaggeredBlocksInsteadOfDailyFragmentation() {
        val habit = ExamStudyPlan.studyHabitReference
        val julySix = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 6))
        val julyNine = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 9))
        val prompt = ExamStudyPlan.dynamicSchedulePrompt(
            date = LocalDate.of(2026, 7, 9),
            presetPlan = julyNine,
            defaultSchedule = ExamStudyPlan.todaySchedule(LocalDate.of(2026, 7, 9)),
            tasks = emptyList(),
        )

        assertTrue(habit.contains("错峰并行"))
        assertTrue(habit.contains("累计约 3 小时"))
        assertTrue(habit.contains("不新开大块"))
        assertTrue(julySix?.title.orEmpty().contains("连续主块"))
        assertFalse(julySix?.tasks.orEmpty().any { it.title.contains("听众合法硕民法课程") })
        assertTrue(julyNine?.title.orEmpty().contains("民法1连续主块"))
        assertTrue(prompt.contains("2-3 天一个学科小单元"))
        assertTrue(prompt.contains("另一科在连续主块期间最多安排 15-30 分钟复述或框架保温"))
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
    fun movementPlanUsesMusicBasedIndoorOptionsForEnergyRecovery() {
        val habit = ExamStudyPlan.studyHabitReference
        val julyMovement = listOf(
            ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 8)),
            ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 10)),
            ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 12)),
            ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 14)),
            ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 21)),
            ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 28)),
        ).flatMap { it?.tasks.orEmpty() }.joinToString("\n") { it.title }
        val scheduleText = ExamStudyPlan.todaySchedule(LocalDate.of(2026, 7, 9))
            .joinToString("\n") { "${it.title} ${it.detail}" }

        assertTrue(habit.contains("音乐"))
        assertTrue(habit.contains("手势舞"))
        assertTrue(habit.contains("第八套广播体操"))
        assertTrue(habit.contains("天气热"))
        assertTrue(habit.contains("少量多次"))
        assertTrue(habit.contains("补充精力"))
        assertTrue(julyMovement.contains("手势舞"))
        assertTrue(julyMovement.contains("第八套广播体操"))
        assertTrue(julyMovement.contains("羽毛球"))
        assertTrue(scheduleText.contains("音乐"))
        assertTrue(scheduleText.contains("开窗站"))
        assertFalse(habit.contains("每天保留 15-30 分钟散步/拉伸"))
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
