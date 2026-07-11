package me.rerere.rikkahub.data.study

import java.time.LocalDate
import java.time.LocalTime
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
        val plan = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 5))
        val titles = plan?.tasks.orEmpty().joinToString("\n") { it.title }

        assertTrue(titles.contains("不单独安排题目"))
        assertTrue(titles.contains("课上例题算在听课里"))
        assertFalse(titles.contains("已学内容小测"))
        assertFalse(titles.contains("例题回忆"))
        assertFalse(titles.contains("做 8-10 题"))
        assertFalse(titles.contains("章节题 5 道"))
    }

    @Test
    fun menstrualDiscomfortMovesCurrentWeekRestDayToJulySeven() {
        val restPlan = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 7))
        val restSchedule = ExamStudyPlan.todaySchedule(LocalDate.of(2026, 7, 7))
        val restPrompt = ExamStudyPlan.dynamicSchedulePrompt(
            date = LocalDate.of(2026, 7, 7),
            presetPlan = restPlan,
            defaultSchedule = restSchedule,
            tasks = emptyList(),
        )
        val julyEightTasks = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 8))
            ?.tasks
            .orEmpty()
            .joinToString("\n") { it.title }

        assertEquals("例假不适完整休息日：今天无学习任务", restPlan?.title)
        assertTrue(restPlan?.tasks.orEmpty().isEmpty())
        assertTrue(restSchedule.joinToString("\n") { it.detail }.contains("不安排单词、听课、背诵或题目"))
        assertTrue(restPrompt.contains("今天是用户明确选择的完整休息日"))
        assertTrue(julyEightTasks.contains("顺延自 7 月 7 日"))
        assertTrue(julyEightTasks.contains("刑法第 1 章：做文运/众合章节题"))
        assertTrue(julyEightTasks.contains("15-20 道"))
        assertTrue(julyEightTasks.contains("不算完整闭环"))
        assertTrue(julyEightTasks.contains("不单独安排预习目录"))
        assertTrue(julyEightTasks.contains("法理第 1-3 章：正式背诵 30-40 分钟"))
        assertTrue(julyEightTasks.contains("有效学习量至少凑够 2 小时"))
    }

    @Test
    fun closedChapterDaySchedulesChapterPracticeAndCorrection() {
        val plan = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 13))
        val titles = plan?.tasks.orEmpty().joinToString("\n") { it.title }

        assertTrue(titles.contains("刑法第 1 章：整章章节题"))
        assertTrue(titles.contains("约 40 道"))
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
        assertTrue(ExamStudyPlan.studyHabitReference.contains("不能长期停留在“看错题、搞目录”"))
        assertTrue(ExamStudyPlan.studyHabitReference.contains("正式背诵 30-40 分钟"))
        assertTrue(ExamStudyPlan.studyHabitReference.contains("至少安排约 2 小时有效学习"))
        assertTrue(ExamStudyPlan.studyHabitReference.contains("单纯看框架不算"))
    }

    @Test
    fun singleNewBookMainLineUsesLawTheoryRecitationAsSideLine() {
        val habit = ExamStudyPlan.studyHabitReference
        val subject = ExamStudyPlan.subjectExecutionReference
        val july = ExamStudyPlan.monthlyPlans.single { it.month == "2026-07" }.tasks.joinToString("\n")
        val week = ExamStudyPlan.julyWeeks.single { it.id == "2026-07-w2" }.tasks.joinToString("\n")
        val julySix = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 6))
        val julyNine = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 9))
        val prompt = ExamStudyPlan.dynamicSchedulePrompt(
            date = LocalDate.of(2026, 7, 9),
            presetPlan = julyNine,
            defaultSchedule = ExamStudyPlan.todaySchedule(LocalDate.of(2026, 7, 9)),
            tasks = emptyList(),
        )
        val julyNineTasks = julyNine?.tasks.orEmpty().joinToString("\n") { it.title }

        assertTrue(habit.contains("一本学完再学下一本"))
        assertTrue(habit.contains("整本书顺序推进"))
        assertTrue(habit.contains("倍速听课"))
        assertTrue(habit.contains("9 月底前基本收完"))
        assertTrue(habit.contains("不能压缩课后消化、做题、错题、框架和背诵痕迹"))
        assertTrue(habit.contains("法理学背诵可以作为复线"))
        assertTrue(habit.contains("复线只能是背诵、错题、框架回炉"))
        assertTrue(habit.contains("刑法主线期间不把民法作为新开副线"))
        assertTrue(habit.contains("从每天 2-3 小时有效学习起步"))
        assertTrue(habit.contains("不能再开另一门新书"))
        assertTrue(habit.contains("不要拆成 4-5 个碎片"))
        assertTrue(subject.contains("不单独安排预习目录"))
        assertFalse(subject.contains("课前 5 分钟看考试分析目录"))
        assertTrue(july.contains("7 月新学主线只放刑法"))
        assertFalse(july.contains("民法第 1 章连续听完"))
        assertTrue(ExamStudyPlan.monthlyPlans.single { it.month == "2026-09" }.tasks.joinToString("\n").contains("9 月 15 日前完成民法"))
        assertTrue(ExamStudyPlan.monthlyPlans.single { it.month == "2026-09" }.tasks.joinToString("\n").contains("法制史第 1-7 章"))
        assertTrue(ExamStudyPlan.monthlyPlans.single { it.month == "2026-10" }.tasks.joinToString("\n").contains("不再把新课当主线"))
        assertTrue(week.contains("刑法主线 + 法理背诵复线"))
        assertTrue(week.contains("民法第 1 章不在刑法主线期间启动"))
        assertTrue(julySix?.title.orEmpty().contains("连续主块"))
        assertFalse(julySix?.tasks.orEmpty().any { it.title.contains("听众合法硕民法课程") })
        assertTrue(julyNine?.title.orEmpty().contains("刑法1补闭环"))
        assertTrue(julyNineTasks.contains("刑法第 1 章未完成框架图"))
        assertTrue(julyNineTasks.contains("法理第 1-3 章未完成背诵"))
        assertFalse(julyNineTasks.contains("民法第 1 章"))
        assertFalse(julyNineTasks.contains("预览"))
        assertTrue(prompt.contains("一本新课主线学完再学下一本"))
        assertTrue(prompt.contains("做题、错题收集和框架闭环优先集中完成"))
        assertTrue(prompt.contains("法理学背诵可以作为复线"))
        assertFalse(prompt.contains("另一科在连续主块期间最多安排 15-30 分钟复述或框架保温"))
    }

    @Test
    fun chapterPracticeUsesFortyQuestionSetsInsteadOfTinyChunks() {
        val week = ExamStudyPlan.julyWeeks.single { it.id == "2026-07-w2" }
        val julyEight = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 8))
        val julyThirteen = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 13))
        val text = listOf(
            ExamStudyPlan.studyHabitReference,
            week.tasks.joinToString("\n"),
            julyEight?.tasks.orEmpty().joinToString("\n") { it.title },
            julyThirteen?.tasks.orEmpty().joinToString("\n") { it.title },
        ).joinToString("\n")

        assertEquals(40, ExamStudyPlan.chapterPracticeQuestionsPerSet)
        assertTrue(text.contains("一张按约 40 道估算"))
        assertTrue(text.contains("本周完成文运/众合章节题 1 张约 40 道"))
        assertTrue(text.contains("7 月 8 日先做 15-20 道启动"))
        assertTrue(text.contains("7 月 13 日补完剩余 20-25 道"))
        assertFalse(week.tasks.joinToString("\n").contains("本周集中做文运/众合章节题 10-15 题"))
    }

    @Test
    fun examAnalysisAndExamStructureDriveStudyPlanCadence() {
        val habit = ExamStudyPlan.studyHabitReference
        val subject = ExamStudyPlan.subjectExecutionReference
        val july = ExamStudyPlan.monthlyPlans.single { it.month == "2026-07" }.tasks.joinToString("\n")
        val august = ExamStudyPlan.monthlyPlans.single { it.month == "2026-08" }.tasks.joinToString("\n")
        val september = ExamStudyPlan.monthlyPlans.single { it.month == "2026-09" }.tasks.joinToString("\n")
        val october = ExamStudyPlan.monthlyPlans.single { it.month == "2026-10" }.tasks.joinToString("\n")
        val november = ExamStudyPlan.monthlyPlans.single { it.month == "2026-11" }.tasks.joinToString("\n")
        val week = ExamStudyPlan.julyWeeks.single { it.id == "2026-07-w2" }.tasks.joinToString("\n")
        val julyEight = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 8))
            ?.tasks
            .orEmpty()
            .joinToString("\n") { it.title }
        val julyNine = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 9))
            ?.tasks
            .orEmpty()
            .joinToString("\n") { it.title }
        val all = listOf(habit, subject, july, august, september, october, november, week, julyEight, julyNine)
            .joinToString("\n")

        assertTrue(habit.contains("考试分析已到手"))
        assertTrue(habit.contains("每张卷 180 分钟、150 分"))
        assertTrue(habit.contains("专业基础课"))
        assertTrue(habit.contains("民法 75 分"))
        assertTrue(habit.contains("刑法 75 分"))
        assertTrue(habit.contains("单选 40 个 40 分"))
        assertTrue(habit.contains("多选 10 个 20 分"))
        assertTrue(habit.contains("简答 4 题 40 分"))
        assertTrue(habit.contains("分析题 2 个 20 分"))
        assertTrue(habit.contains("案例分析 2 个 30 分"))
        assertTrue(habit.contains("综合课"))
        assertTrue(habit.contains("法理 60 分"))
        assertTrue(habit.contains("宪法 50 分"))
        assertTrue(habit.contains("法制史 40 分"))
        assertTrue(habit.contains("简答 3 题 30 分"))
        assertTrue(habit.contains("分析 3 题 30 分"))
        assertTrue(habit.contains("论述 2 题 30 分"))
        assertTrue(habit.contains("主观题 90 分"))
        assertTrue(all.contains("选择题陷阱"))
        assertTrue(all.contains("答题骨架"))
        assertTrue(all.contains("规范表述"))
        assertTrue(all.contains("3 小时"))
        assertTrue(julyEight.contains("考试分析"))
        assertTrue(julyNine.contains("考试分析"))
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
    fun vocabularyCanBeMovedEarlierAsAvoidanceStartup() {
        val habit = ExamStudyPlan.studyHabitReference
        val scheduleText = ExamStudyPlan.todaySchedule(LocalDate.of(2026, 7, 9))
            .joinToString("\n") { "${it.time} ${it.title} ${it.detail}" }

        assertTrue(habit.contains("畏难"))
        assertTrue(habit.contains("想逃避"))
        assertTrue(habit.contains("不背单词 20 个（1 组）"))
        assertTrue(habit.contains("学习一点反而会清醒"))
        assertTrue(scheduleText.contains("09:45-10:05 单词启动"))
        assertTrue(scheduleText.contains("畏难"))
        assertTrue(scheduleText.contains("20 个（1 组）"))
    }

    @Test
    fun julyTenHeadacheBacklogIsRebalancedWithoutSkippingLegalTheoryChapters() {
        val july10 = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 10))!!
        val july11 = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 11))!!.tasks.joinToString("\n") { it.title }
        val july12 = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 12))!!.tasks.joinToString("\n") { it.title }
        val july13 = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 13))!!.tasks.joinToString("\n") { it.title }
        val july15 = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 15))!!.tasks.joinToString("\n") { it.title }

        assertTrue(july10.title.contains("头晕"))
        assertTrue(july10.tasks.isEmpty())
        assertTrue(july11.contains("法理第 1 章"))
        assertTrue(july11.contains("刑法第 1 章框架图欠账"))
        assertTrue(july11.contains("状态允许再选做"))
        assertTrue(july12.contains("法理第 2 章"))
        assertTrue(july12.contains("合成完整一张"))
        assertTrue(july13.contains("法理第 3 章"))
        assertTrue(july13.contains("第 1-3 章连续验收"))
        assertFalse(july13.contains("法理第 4 章"))
        assertFalse(july13.contains("法理第 8 章"))
        assertTrue(july15.contains("法理学第 4 章"))
    }

    @Test
    fun julyLegalTheoryReviewAdvancesInChapterOrder() {
        val expectedChapters = mapOf(
            15 to "法理学第 4 章",
            16 to "法理学第 5 章",
            17 to "法理学第 6 章",
            19 to "法理第 7 章",
            20 to "法理第 8 章",
            22 to "法理学第 9 章",
            23 to "法理第 10 章",
            24 to "法理学第 11 章",
            26 to "法理学第 12 章",
            27 to "法理学第 13 章",
        )

        expectedChapters.forEach { (day, chapterText) ->
            val tasks = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, day))!!.tasks
                .joinToString("\n") { it.title }
            assertTrue("July $day should contain $chapterText", tasks.contains(chapterText))
        }
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
        assertTrue(prompt.contains("法理章节必须按顺序连续推进"))
    }

    @Test
    fun schedulePromptRegeneratesFromCurrentTimeWithAllUnfinishedTasks() {
        val prompt = ExamStudyPlan.dynamicSchedulePrompt(
            date = LocalDate.of(2026, 7, 8),
            presetPlan = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 8)),
            defaultSchedule = ExamStudyPlan.todaySchedule(LocalDate.of(2026, 7, 8)),
            tasks = listOf(
                StudyTask(
                    id = "plan-review-criminal",
                    title = "复盘 | 刑法第 1 章：画一张方便背诵的犯罪论入口框架/思维导图",
                    source = StudyTaskSource.Plan,
                ),
                StudyTask(
                    id = "plan-law-theory",
                    title = "背诵 | 法理第 1-3 章：正式背诵 30-40 分钟",
                    source = StudyTaskSource.Plan,
                ),
                StudyTask(
                    id = "plan-vocab",
                    title = "英语 | 不背单词 120 个（6组）",
                    source = StudyTaskSource.Plan,
                ),
                StudyTask(
                    id = "manual-paper",
                    title = "修改并且打印六分开题报告",
                    source = StudyTaskSource.Manual,
                ),
                StudyTask(
                    id = "manual-badminton",
                    title = "打羽毛球在晚上",
                    source = StudyTaskSource.Manual,
                ),
                StudyTask(
                    id = "done-task",
                    title = "已经完成的任务不要再排",
                    done = true,
                    source = StudyTaskSource.Manual,
                ),
            ),
            currentTime = LocalTime.of(16, 16),
        )

        assertTrue(prompt.contains("当前时间：16:16"))
        assertTrue(prompt.contains("不允许输出早于当前时间的时间块"))
        assertTrue(prompt.contains("不要从早上补排"))
        assertTrue(prompt.contains("所有未完成待办必须在剩余时间里出现"))
        assertTrue(prompt.contains("复盘 | 刑法第 1 章"))
        assertTrue(prompt.contains("背诵 | 法理第 1-3 章"))
        assertTrue(prompt.contains("英语 | 不背单词 120 个"))
        assertTrue(prompt.contains("修改并且打印六分开题报告"))
        assertTrue(prompt.contains("打羽毛球在晚上"))
        assertFalse(prompt.substringAfter("当前未完成待办").contains("已经完成的任务不要再排"))
        assertFalse(prompt.substringAfter("剩余日程骨架").substringBefore("请重新生成").contains("09:"))
    }

    @Test
    fun scheduleBlocksFromTimeRemovesPastBlocksBeforeSaving() {
        val blocks = listOf(
            StudyScheduleBlock("09:45-10:05", "单词启动", "不背单词 20 个"),
            StudyScheduleBlock("16:20-17:00", "专业课", "从当前时间后重新排"),
            StudyScheduleBlock("19:30-20:10", "英语", "不背单词 120 个"),
        )

        val remaining = ExamStudyPlan.scheduleBlocksFromTime(blocks, LocalTime.of(16, 16))

        assertEquals(listOf("16:20-17:00", "19:30-20:10"), remaining.map { it.time })
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
