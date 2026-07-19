package me.rerere.rikkahub.data.study

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamStudyPlanTest {
    @Test
    fun countdownUsesAConservativePlanningAnchorUntilTheOfficialDateIsPublished() {
        assertEquals(LocalDate.of(2026, 12, 19), ExamStudyPlan.examDate)
        assertEquals(DayOfWeek.SATURDAY, ExamStudyPlan.examDate.dayOfWeek)
        assertFalse(ExamStudyPlan.examDateIsOfficial)
        assertTrue(ExamStudyPlan.examDateNotice.contains("保守规划"))
        assertTrue(ExamStudyPlan.examDateNotice.contains("以教育部公告为准"))
        assertEquals(1, ExamStudyPlan.daysLeft(LocalDate.of(2026, 12, 18)))
    }

    @Test
    fun currentMilestoneMovesFromCourseDeadlinesToScoreGates() {
        assertTrue(ExamStudyPlan.currentMilestone(LocalDate.of(2026, 7, 12)).contains("7 月 31 日"))
        assertTrue(ExamStudyPlan.currentMilestone(LocalDate.of(2026, 8, 10)).contains("8 月 20 日"))
        assertTrue(ExamStudyPlan.currentMilestone(LocalDate.of(2026, 8, 25)).contains("8 月 31 日"))
        assertTrue(ExamStudyPlan.currentMilestone(LocalDate.of(2026, 9, 10)).contains("9 月 14 日"))
        assertTrue(ExamStudyPlan.currentMilestone(LocalDate.of(2026, 9, 20)).contains("100-105 分"))
        assertTrue(ExamStudyPlan.currentMilestone(LocalDate.of(2026, 10, 20)).contains("110-115 分"))
        assertTrue(ExamStudyPlan.currentMilestone(LocalDate.of(2026, 11, 20)).contains("118-122 分"))
        assertTrue(ExamStudyPlan.currentMilestone(LocalDate.of(2026, 12, 10)).contains("总分 385"))
    }

    @Test
    fun sichuanUniversityTargetUsesASafetyMarginInsteadOfTheHistoricalMinimum() {
        assertEquals(385, ExamStudyPlan.scuSafeTargetScore)
        assertEquals(70, ExamStudyPlan.politicsTargetScore)
        assertEquals(75, ExamStudyPlan.englishTargetScore)
        assertEquals(120, ExamStudyPlan.professionalFoundationTargetScore)
        assertEquals(120, ExamStudyPlan.professionalComprehensiveTargetScore)
        assertEquals(
            ExamStudyPlan.scuSafeTargetScore,
            ExamStudyPlan.politicsTargetScore +
                ExamStudyPlan.englishTargetScore +
                ExamStudyPlan.professionalFoundationTargetScore +
                ExamStudyPlan.professionalComprehensiveTargetScore,
        )
        assertTrue(ExamStudyPlan.studyHabitReference.contains("四川大学法律硕士（非法学）"))
        assertTrue(ExamStudyPlan.studyHabitReference.contains("2024 年法律（非法学）350"))
        assertTrue(ExamStudyPlan.studyHabitReference.contains("计划 68 人"))
        assertTrue(ExamStudyPlan.studyHabitReference.contains("复试科目为法理学"))
        assertTrue(ExamStudyPlan.studyHabitReference.contains("不是官方预测线"))
    }

    @Test
    fun scoreMilestonesApproachTheSichuanUniversitySafetyTargetGradually() {
        val september = ExamStudyPlan.monthlyPlans.single { it.month == "2026-09" }.tasks.joinToString("\n")
        val october = ExamStudyPlan.monthlyPlans.single { it.month == "2026-10" }.tasks.joinToString("\n")
        val november = ExamStudyPlan.monthlyPlans.single { it.month == "2026-11" }.tasks.joinToString("\n")
        val december = ExamStudyPlan.monthlyPlans.single { it.month == "2026-12" }.tasks.joinToString("\n")

        assertTrue(september.contains("100-105 分"))
        assertTrue(october.contains("110-115 分"))
        assertTrue(november.contains("118-122 分"))
        assertTrue(november.contains("375 分区间"))
        assertTrue(december.contains("385 分安全目标"))
        assertTrue(december.contains("各守住 120 分左右"))
    }

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
    fun julyEighteenUsesThreeHourCourseFirstPlan() {
        val plan = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 18))
        val titles = plan?.tasks.orEmpty().joinToString("\n") { it.title }

        assertTrue(plan?.title.orEmpty().contains("3 小时稳定日"))
        assertTrue(titles.contains("第 5 章课程已完成"))
        assertTrue(titles.contains("第 6-7 章课程 65-70 分钟"))
        assertTrue(titles.contains("达到约 70%"))
        assertTrue(titles.contains("机动复盘缓冲"))
        assertTrue(titles.contains("每日运动"))
    }

    @Test
    fun completedChaptersSixAndSevenNeverReturnToCourseTasks() {
        val afterCompletion = (19..25)
            .mapNotNull { day -> ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, day)) }
            .flatMap { it.tasks }
            .joinToString("\n") { it.title }
        val julyTwenty = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 20))
            ?.tasks.orEmpty().joinToString("\n") { it.title }
        val julyTwentyFour = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 24))
            ?.tasks.orEmpty().joinToString("\n") { it.title }
        val week = ExamStudyPlan.julyWeeks.single { it.id == "2026-07-w4" }
            .tasks.joinToString("\n")

        assertFalse(afterCompletion.contains("继续第 6-7 章课程"))
        assertFalse(afterCompletion.contains("第 6-7 章续课"))
        assertTrue(julyTwenty.contains("第 3-7 章合并题"))
        assertTrue(julyTwentyFour.contains("正式连接框架"))
        assertTrue(week.contains("第 6-7 章课程已全部完成"))
        assertFalse(week.contains("剩余 6.5 小时"))
    }

    @Test
    fun lawTheoryIsRecitedInsteadOfOnlySkimmed() {
        val july = ExamStudyPlan.monthlyPlans.single { it.month == "2026-07" }
        val text = july.tasks.joinToString("\n")

        assertTrue(text.contains("法理第 1 章从 7 月 13 日起"))
        assertTrue(text.contains("每天安排 30-40 分钟"))
        assertTrue(text.contains("规范表述"))
        assertTrue(ExamStudyPlan.studyHabitReference.contains("不能长期停留在“看错题、搞目录”"))
        assertTrue(ExamStudyPlan.studyHabitReference.contains("30-40 分钟正式背诵"))
        assertTrue(ExamStudyPlan.studyHabitReference.contains("4 小时是当前新基线"))
        assertTrue(ExamStudyPlan.studyHabitReference.contains("单纯看框架不算"))
        assertTrue(ExamStudyPlan.studyHabitReference.contains("标题写 3.5 小时不能只排 2 小时"))
        assertTrue(ExamStudyPlan.studyHabitReference.contains("播放器四倍速不等于四倍学习效率"))
        assertTrue(ExamStudyPlan.studyHabitReference.contains("设定每天学习时长前必须先算完整总账"))
        assertTrue(ExamStudyPlan.studyHabitReference.contains("没有分钟账本时只能给日期范围"))
        assertTrue(ExamStudyPlan.studyHabitReference.contains("月、周、日三层容量必须守恒"))
    }

    @Test
    fun singleNewBookMainLineUsesLawTheoryRecitationAsSideLine() {
        val habit = ExamStudyPlan.studyHabitReference
        val subject = ExamStudyPlan.subjectExecutionReference
        val july = ExamStudyPlan.monthlyPlans.single { it.month == "2026-07" }.tasks.joinToString("\n")
        val week = ExamStudyPlan.julyWeeks.single { it.id == "2026-07-w3" }.tasks.joinToString("\n")
        val julySix = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 6))
        val julyThirteen = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 13))
        val prompt = ExamStudyPlan.dynamicSchedulePrompt(
            date = LocalDate.of(2026, 7, 13),
            presetPlan = julyThirteen,
            defaultSchedule = ExamStudyPlan.todaySchedule(LocalDate.of(2026, 7, 13)),
            tasks = emptyList(),
        )
        val julyThirteenTasks = julyThirteen?.tasks.orEmpty().joinToString("\n") { it.title }

        assertTrue(habit.contains("一本学完再学下一本"))
        assertTrue(habit.contains("整本书顺序推进"))
        assertTrue(habit.contains("四倍速播放课程"))
        assertTrue(habit.contains("9 月 14 日只是冲刺假设"))
        assertTrue(habit.contains("不能压缩课后消化、做题、错题、正式框架图和背诵痕迹"))
        assertTrue(habit.contains("法理背诵复线"))
        assertTrue(habit.contains("复线只能是背诵、错题、框架回炉"))
        assertTrue(habit.contains("刑法主线期间不把民法作为新开副线"))
        assertTrue(habit.contains("4 小时是当前新基线"))
        assertTrue(habit.contains("不能再开另一门新书"))
        assertTrue(habit.contains("不要拆成 4-5 个碎片"))
        assertTrue(subject.contains("不单独安排预习目录"))
        assertFalse(subject.contains("课前 5 分钟看考试分析目录"))
        assertTrue(july.contains("7 月 31 日"))
        assertFalse(july.contains("民法第 1 章连续听完"))
        assertTrue(ExamStudyPlan.monthlyPlans.single { it.month == "2026-09" }.tasks.joinToString("\n").contains("9 月 14 日"))
        assertTrue(ExamStudyPlan.monthlyPlans.single { it.month == "2026-09" }.tasks.joinToString("\n").contains("9 月 15-30 日"))
        assertTrue(ExamStudyPlan.monthlyPlans.single { it.month == "2026-10" }.tasks.joinToString("\n").contains("不开常规新课"))
        assertTrue(week.contains("第 3-7 章合并题"))
        assertTrue(week.contains("第 5-7 章"))
        assertTrue(julySix?.title.orEmpty().contains("连续主块"))
        assertFalse(julySix?.tasks.orEmpty().any { it.title.contains("听众合法硕民法课程") })
        assertTrue(julyThirteen?.title.orEmpty().contains("3.5 小时起步"))
        assertTrue(julyThirteenTasks.contains("刑法第 2 章独立题组"))
        assertTrue(julyThirteenTasks.contains("法理第 1 章正式闭卷背诵"))
        assertTrue(julyThirteenTasks.contains("英语"))
        assertFalse(julyThirteenTasks.contains("民法第 1 章"))
        assertTrue(prompt.contains("一本新课主线学完再学下一本"))
        assertTrue(prompt.contains("做题、错题收集和正式框架图优先集中完成"))
        assertTrue(prompt.contains("法理背诵"))
        assertTrue(prompt.contains("英语主训练块"))
        assertTrue(prompt.contains("政治在 2026-09-15 起启动"))
    }

    @Test
    fun completedFirstChapterPracticeIsNotScheduledAgainAfterProgressCorrection() {
        val week = ExamStudyPlan.julyWeeks.single { it.id == "2026-07-w3" }
        val julyEight = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 8))
        val julyThirteen = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 13))
        val text = listOf(
            ExamStudyPlan.studyHabitReference,
            week.tasks.joinToString("\n"),
            julyEight?.tasks.orEmpty().joinToString("\n") { it.title },
            julyThirteen?.tasks.orEmpty().joinToString("\n") { it.title },
        ).joinToString("\n")

        assertEquals(40, ExamStudyPlan.chapterPracticeQuestionsPerSet)
        assertTrue(text.contains("配套题一套按约 40 道估算"))
        assertTrue(
            ExamStudyPlan.julyWeeks.single { it.id == "2026-07-w2" }
                .tasks.joinToString("\n").contains("第 1 章已正式闭环"),
        )
        assertFalse(julyThirteen?.tasks.orEmpty().joinToString("\n") { it.title }.contains("刑法第 1 章"))
    }

    @Test
    fun criminalLawCombinedPracticeWaitsUntilChaptersTwoThroughSixCoursesFinish() {
        val julyFourteen = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 14))!!
            .tasks.joinToString("\n") { it.title }
        val julySixteen = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 16))!!
            .tasks.joinToString("\n") { it.title }
        val week = ExamStudyPlan.julyWeeks.single { it.id == "2026-07-w3" }.tasks.joinToString("\n")

        assertFalse(julyFourteen.contains("第 3-7 章合并题"))
        assertTrue(julySixteen.contains("第 6-7 章课程"))
        assertFalse(julySixteen.contains("第 3-7 章合并题"))
        assertTrue(week.indexOf("第 5-7 章") < week.indexOf("第 3-7 章合并题"))
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
        val scheduleText = ExamStudyPlan.todaySchedule(LocalDate.of(2026, 7, 20))
            .joinToString("\n") { "${it.time} ${it.title} ${it.detail}" }

        assertTrue(habit.contains("畏难"))
        assertTrue(habit.contains("想逃避"))
        assertTrue(habit.contains("不背单词 20 个（1 组）"))
        assertTrue(habit.contains("学习一点反而会清醒"))
        assertTrue(scheduleText.contains("09:45-09:55 单词启动"))
        assertTrue(scheduleText.contains("畏难"))
        assertTrue(scheduleText.contains("20 个（1 组）"))
    }

    @Test
    fun dailyLoadUsesThreeHoursThisWeekAndGrowsOnlyByGate() {
        val restDay = LocalDate.of(2026, 7, 26)

        assertEquals(210, ExamStudyPlan.plannedStudyMinutes(LocalDate.of(2026, 7, 15)))
        assertEquals(240, ExamStudyPlan.plannedStudyMinutes(LocalDate.of(2026, 7, 17)))
        assertEquals(180, ExamStudyPlan.plannedStudyMinutes(LocalDate.of(2026, 7, 18)))
        assertEquals(180, ExamStudyPlan.plannedStudyMinutes(LocalDate.of(2026, 7, 19)))
        assertEquals(180, ExamStudyPlan.plannedStudyMinutes(LocalDate.of(2026, 7, 25)))
        assertEquals(210, ExamStudyPlan.plannedStudyMinutes(LocalDate.of(2026, 7, 27)))
        assertEquals(240, ExamStudyPlan.plannedStudyMinutes(LocalDate.of(2026, 8, 3)))
        assertEquals(270, ExamStudyPlan.plannedStudyMinutes(LocalDate.of(2026, 8, 10)))
        assertEquals(300, ExamStudyPlan.plannedStudyMinutes(LocalDate.of(2026, 8, 17)))
        assertEquals(390, ExamStudyPlan.plannedStudyMinutes(LocalDate.of(2026, 9, 16)))
        assertEquals(420, ExamStudyPlan.plannedStudyMinutes(LocalDate.of(2026, 10, 1)))
        assertEquals(480, ExamStudyPlan.plannedStudyMinutes(LocalDate.of(2026, 11, 1)))
        assertEquals(420, ExamStudyPlan.plannedStudyMinutes(LocalDate.of(2026, 12, 10)))
        assertEquals(0, ExamStudyPlan.plannedStudyMinutes(restDay))
        val schedule = ExamStudyPlan.todaySchedule(LocalDate.of(2026, 7, 20))
        assertTrue(schedule.first().title.contains("广播体操"))
        assertTrue(schedule.joinToString("\n") { it.detail }.contains("固定核心约"))
        assertTrue(ExamStudyPlan.studyHabitReference.contains("3 小时健康日"))
    }
    @Test
    fun `chapter ledger keeps criminal and civil chapter counts separate from question book size`() {
        assertEquals(25, ExamStudyPlan.criminalLawChapterCount)
        assertEquals(54, ExamStudyPlan.civilLawChapterCount)
        assertEquals(270, ExamStudyPlan.criminalLawChapterFiveCourseMinutes)
        assertTrue(ExamStudyPlan.studyHabitReference.contains("第 5 章原始课程约 270 分钟（4.5 小时）"))
    }

    @Test
    fun `criminal chapters three to seven wait for chapter seven before formal map`() {
        val july14 = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 14))!!.tasks.joinToString("\n") { it.title }
        val july16 = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 16))!!.tasks.joinToString("\n") { it.title }
        val july17 = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 17))!!.tasks.joinToString("\n") { it.title }

        assertFalse(july14.contains("第 3-4 章正式框架图"))
        assertTrue(july14.contains("等第 7 章听完后再画"))
        assertFalse(july16.contains("补齐刑法第 3-4 章正式框架图"))
        assertTrue(july16.contains("不画第 3-7 章正式连接框架"))
        assertTrue(july17.contains("英语翻译/语法深复盘"))
        assertTrue(july17.contains("课程 65-75 分钟"))
        assertTrue(july17.contains("机动复盘缓冲"))
    }

    @Test
    fun julyLegalTheoryReviewAdvancesInChapterOrder() {
        val expectedChapters = mapOf(
            13 to "法理第 1 章",
            14 to "法理第 1 章",
            15 to "法理第 1 章",
            16 to "法理第 1 章",
            17 to "法理第 1 章",
            18 to "法理第 1 章",
            20 to "法理第 2 章",
            21 to "法理第 2 章",
            22 to "法理第 2 章",
            23 to "法理第 2 章",
            24 to "法理第 2 章",
            25 to "法理第 2 章",
            27 to "法理第 2 章",
            29 to "法理第 2 章",
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
        val scheduleText = ExamStudyPlan.todaySchedule(LocalDate.of(2026, 7, 20))
            .joinToString("\n") { "${it.title} ${it.detail}" }

        assertTrue(habit.contains("音乐"))
        assertTrue(habit.contains("手势舞"))
        assertTrue(habit.contains("第八套广播体操"))
        assertTrue(habit.contains("85%"))
        assertTrue(habit.contains("15%"))
        assertTrue(habit.contains("一天最多可安排两次"))
        assertTrue(habit.contains("最多每 10-14 天"))
        assertTrue(habit.contains("天气热"))
        assertTrue(habit.contains("少量多次"))
        assertTrue(habit.contains("补充精力"))
        assertTrue(julyMovement.contains("手势舞"))
        assertTrue(julyMovement.contains("第八套广播体操"))
        assertTrue(julyMovement.contains("午后和晚间可各做半套广播体操"))
        assertTrue(scheduleText.contains("音乐"))
        assertTrue(scheduleText.contains("开窗站"))
        assertFalse(habit.contains("每天保留 15-30 分钟散步/拉伸"))
    }

    @Test
    fun downstreamPlanLeavesAFullRevisionRunwayBeforeFinalSprint() {
        val october = ExamStudyPlan.postJulyWeeks
            .filter { it.id.startsWith("2026-10") }
            .flatMap { it.tasks }
            .joinToString("\n")
        val november = ExamStudyPlan.postJulyWeeks
            .filter { it.id.startsWith("2026-11") }
            .flatMap { it.tasks }
            .joinToString("\n")

        assertTrue(ExamStudyPlan.monthlyPlans.single { it.month == "2026-10" }.tasks.joinToString("\n").contains("不开常规新课"))
        assertTrue(october.contains("第二轮"))
        assertFalse(october.contains("听课"))
        assertTrue(november.contains("套卷"))
        assertTrue(november.contains("答题纸"))
    }

    @Test
    fun hardCourseDeadlinesProtectThreeMonthsForRecitationAndOutput() {
        val july = ExamStudyPlan.monthlyPlans.single { it.month == "2026-07" }.tasks.joinToString("\n")
        val august = ExamStudyPlan.monthlyPlans.single { it.month == "2026-08" }.tasks.joinToString("\n")
        val september = ExamStudyPlan.monthlyPlans.single { it.month == "2026-09" }.tasks.joinToString("\n")
        val october = ExamStudyPlan.monthlyPlans.single { it.month == "2026-10" }.tasks.joinToString("\n")
        val december = ExamStudyPlan.monthlyPlans.single { it.month == "2026-12" }.tasks.joinToString("\n")

        assertTrue(july.contains("7 月 31 日"))
        assertTrue(august.contains("8 月 20 日"))
        assertTrue(august.contains("8 月 31 日"))
        assertTrue(september.contains("9 月 14 日"))
        assertTrue(september.contains("9 月 15-30 日"))
        assertTrue(october.contains("第二轮"))
        assertFalse(october.contains("常规新课主线"))
        assertTrue(december.contains("全真模拟"))
        assertTrue(december.contains("不新增资料或课程"))
    }

    @Test
    fun generatedDailyPlansFollowTheHardCourseCutoffs() {
        val augustCivil = ExamStudyPlan.todayPlan(LocalDate.of(2026, 8, 10))!!
            .tasks.joinToString("\n") { it.title }
        val augustConstitution = ExamStudyPlan.todayPlan(LocalDate.of(2026, 8, 25))!!
            .tasks.joinToString("\n") { it.title }
        val septemberHistory = ExamStudyPlan.todayPlan(LocalDate.of(2026, 9, 10))!!
            .tasks.joinToString("\n") { it.title }
        val septemberRevision = ExamStudyPlan.todayPlan(LocalDate.of(2026, 9, 16))!!
            .tasks.joinToString("\n") { it.title }

        assertTrue(augustCivil.contains("按刑法→民法→宪法→法制史顺序继续当前未完成科目的众合法硕课程"))
        assertTrue(augustConstitution.contains("按刑法→民法→宪法→法制史顺序继续当前未完成科目的众合法硕课程"))
        assertTrue(septemberHistory.contains("按刑法→民法→宪法→法制史顺序继续当前未完成科目的众合法硕课程"))
        assertTrue(septemberRevision.contains("新课主线"))
        assertTrue(septemberRevision.contains("按刑法→民法→宪法→法制史顺序继续当前未完成科目的众合法硕课程"))
    }

    @Test
    fun finalPreExamWeekStillGeneratesAPlan() {
        val plan = ExamStudyPlan.todayPlan(LocalDate.of(2026, 12, 18))
        val text = plan?.tasks.orEmpty().joinToString("\n") { it.title }
        val week = ExamStudyPlan.weekForDate(LocalDate.of(2026, 12, 18))

        assertTrue(plan != null)
        assertEquals("2026-12-w3", week?.id)
        assertEquals("2026-12-15 至考前", week?.dateRange)
        assertTrue(text.contains("不再开常规新课"))
        assertTrue(text.contains("保温"))
    }

    @Test
    fun formalStudyDaysIncludeFixedVocabularyReview() {
        val plan = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 2))
        val taskTitles = plan?.tasks.orEmpty().map { it.title }

        assertTrue(taskTitles.any { it.contains("英语一真题阅读") })
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
        assertTrue(prompt.contains("这一章没有结束前不要单独安排课后题"))
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
        assertTrue(prompt.contains("未完成待办按优先级选择"))
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
            19:30-20:10｜英语｜不背单词 120 个（6 组）+ 英语一真题阅读 1 篇
            """.trimIndent(),
        )

        assertEquals(3, blocks.size)
        assertEquals("09:30-10:20", blocks[1].time)
        assertEquals("专业课", blocks[1].title)
        assertEquals("听众合法硕刑法课程第 1 章", blocks[1].detail)
    }

    @Test
    fun highScorePlanKeepsEnglishCoreAndPoliticsSeparateFromVocabulary() {
        val julyThirteen = ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 13))!!
        val julyTasks = julyThirteen.tasks.map { it.title }
        val septemberFifteen = ExamStudyPlan.todayPlan(LocalDate.of(2026, 9, 15))!!
        val septemberTasks = septemberFifteen.tasks

        assertTrue(julyTasks.any { it.contains("不背单词 120 个") })
        assertTrue(julyTasks.any { it.contains("真题阅读") || it.contains("真题完形") })
        assertFalse(julyTasks.any { it.contains("政治") })
        assertTrue(septemberTasks.any { it.kind == StudyPlanTaskKind.English && !it.title.contains("不背单词") })
        assertTrue(septemberTasks.any { it.kind == StudyPlanTaskKind.Politics })
        assertFalse(ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 19))!!.tasks.isEmpty())
        assertTrue(ExamStudyPlan.todayPlan(LocalDate.of(2026, 7, 26))!!.tasks.isEmpty())
    }

    @Test
    fun professionalTrainingChainIncludesMultipleQuestionLayersAndThreeRecitationRounds() {
        val text = buildString {
            appendLine(ExamStudyPlan.subjectExecutionReference)
            ExamStudyPlan.monthlyPlans.forEach { appendLine(it.tasks.joinToString("\n")) }
            ExamStudyPlan.weeklyPlans.forEach { appendLine(it.tasks.joinToString("\n")) }
        }

        assertEquals(2, ExamStudyPlan.professionalMinimumPracticeSetsPerClosedUnit)
        assertEquals(2, ExamStudyPlan.professionalMistakeRedoRounds)
        assertTrue(text.contains("听课配套题"))
        assertTrue(text.contains("独立额外题源"))
        assertTrue(text.contains("错题二刷"))
        assertTrue(text.contains("三刷"))
        assertTrue(text.contains("分科历年真题"))
        assertTrue(text.contains("模拟"))
        assertTrue(text.contains("第一轮"))
        assertTrue(text.contains("第二轮"))
        assertTrue(text.contains("第三轮"))
    }

    @Test
    fun englishWeeklyRotationCoversReadingGrammarSmallThreeAndBothEssays() {
        val septemberEnglish = (14..19)
            .flatMap { day -> ExamStudyPlan.todayPlan(LocalDate.of(2026, 9, day))!!.tasks }
            .filter { it.kind == StudyPlanTaskKind.English && !it.title.contains("不背单词") }
            .map { it.title }

        assertEquals(3, ExamStudyPlan.englishReadingPassCount)
        assertTrue(septemberEnglish.count { it.contains("阅读") } >= 3)
        assertTrue(septemberEnglish.any { it.contains("真题阅读") || it.contains("语法") })
        assertTrue(septemberEnglish.any { it.contains("完形") })
        assertTrue(septemberEnglish.any { it.contains("新题型") })
        assertTrue(septemberEnglish.any { it.contains("翻译") })
        assertTrue(septemberEnglish.any { it.contains("小作文") && it.contains("大作文") })

        val october = ExamStudyPlan.monthlyPlans.single { it.month == "2026-10" }.tasks.joinToString("\n")
        assertTrue(october.contains("小作文"))
        assertTrue(october.contains("大作文"))
        assertTrue(october.contains("批改"))
        assertTrue(october.contains("重写"))
    }

    @Test
    fun politicsQuestionVolumeCanActuallyReachAFirstAndSecondPass() {
        val septemberPolitics = (15..19)
            .flatMap { day -> ExamStudyPlan.todayPlan(LocalDate.of(2026, 9, day))!!.tasks }
            .filter { it.kind == StudyPlanTaskKind.Politics }
        val octoberPolitics = (5..10)
            .flatMap { day -> ExamStudyPlan.todayPlan(LocalDate.of(2026, 10, day))!!.tasks }
            .filter { it.kind == StudyPlanTaskKind.Politics }
        val text = ExamStudyPlan.monthlyPlans
            .filter { it.month >= "2026-09" }
            .flatMap { it.tasks }
            .joinToString("\n")

        assertEquals(1000, ExamStudyPlan.politicsFirstPassQuestionTarget)
        assertEquals(4, septemberPolitics.size)
        assertTrue(septemberPolitics.all { it.title.contains("25-30 道") })
        assertEquals(6, octoberPolitics.size)
        assertTrue(octoberPolitics.all { it.title.contains("30-35 道") })
        assertTrue(text.contains("1000 题一刷"))
        assertTrue(text.contains("错题二刷"))
        assertTrue(text.contains("历年真题"))
        assertTrue(text.contains("肖八"))
        assertTrue(text.contains("肖四"))
        assertTrue(text.contains("整卷"))
    }

    @Test
    fun fullPaperDaysReceiveFullMinutesWithoutOverlappingBlocks() {
        val englishSchedule = ExamStudyPlan.todaySchedule(LocalDate.of(2026, 12, 2))
        val professionalSchedule = ExamStudyPlan.todaySchedule(LocalDate.of(2026, 12, 4))
        val politicsSchedule = ExamStudyPlan.todaySchedule(LocalDate.of(2026, 12, 5))

        assertEquals(180, durationMinutes(englishSchedule.single { it.title == "英语主训练" }.time))
        assertEquals(180, durationMinutes(professionalSchedule.single { it.title == "正式闭环" }.time))
        assertEquals(180, durationMinutes(politicsSchedule.single { it.title == "政治" }.time))
        assertNoScheduleOverlap(ExamStudyPlan.todaySchedule(LocalDate.of(2026, 10, 6)))
        assertNoScheduleOverlap(englishSchedule)
        assertNoScheduleOverlap(professionalSchedule)
        assertNoScheduleOverlap(politicsSchedule)
    }

    @Test
    fun practiceOnlyDaysDoNotDuplicateTheSameTaskAcrossMainAndClosureBlocks() {
        val schedule = ExamStudyPlan.todaySchedule(LocalDate.of(2026, 7, 13))
        val text = schedule.joinToString("\n") { it.detail }

        assertEquals(1, Regex("刑法第 2 章独立题组").findAll(text).count())
        assertEquals(1, Regex("刑法第 2 章正式框架图").findAll(text).count())
    }

    @Test
    fun weeklyReviewTitleDoesNotEraseTheDailyNewCourseMainLine() {
        val schedule = ExamStudyPlan.todaySchedule(LocalDate.of(2026, 9, 10))
        val main = schedule.first { it.title == "专业课主块" || it.title == "主线推进" }.detail

        assertTrue(main.contains("按刑法→民法→宪法→法制史顺序"))
        assertFalse(main.startsWith("只处理复盘和漏项"))
    }

    private fun durationMinutes(timeRange: String): Int {
        val (start, end) = timeRange.split("-").map { LocalTime.parse(it) }
        return java.time.Duration.between(start, end).toMinutes().toInt()
    }

    private fun assertNoScheduleOverlap(blocks: List<StudyScheduleBlock>) {
        blocks.zipWithNext().forEach { (current, next) ->
            val currentEnd = LocalTime.parse(current.time.substringAfter("-"))
            val nextStart = LocalTime.parse(next.time.substringBefore("-"))
            assertTrue("${current.time} overlaps ${next.time}", !currentEnd.isAfter(nextStart))
        }
    }

    @Test
    fun dailyScheduleFrontLoadsRecitationAndProtectsReviewBuffer() {
        val schedule = ExamStudyPlan.todaySchedule(LocalDate.of(2026, 7, 20))
        val titles = schedule.map { it.title }

        assertTrue(titles.contains("广播体操启动"))
        assertTrue(titles.contains("午后广播体操"))
        assertTrue(titles.contains("法理背诵前置"))
        assertTrue(titles.contains("深复盘/突发缓冲"))
        val secondCourseIndex = maxOf(titles.indexOf("专业课续段"), titles.indexOf("课程续段"))
        assertTrue(secondCourseIndex >= 0)
        assertTrue(titles.indexOf("法理背诵前置") < secondCourseIndex)
        assertTrue(titles.indexOf("深复盘/突发缓冲") < titles.indexOf("晚饭 + 离桌"))
        assertTrue(ExamStudyPlan.dailyMovementTaskTitle.contains("完整一套"))
        assertEquals(85, ExamStudyPlan.plannedCoreLoadPercent)
        assertEquals(15, ExamStudyPlan.reviewBufferPercent)
    }

}
