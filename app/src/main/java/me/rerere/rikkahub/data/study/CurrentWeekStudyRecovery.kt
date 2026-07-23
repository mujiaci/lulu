package me.rerere.rikkahub.data.study

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Explicit recovery plan for 2026-07-22..2026-07-26 plus the capacity audit that
 * keeps monthly/weekly descriptions aligned with the actual minute budget.
 */
object CurrentWeekStudyRecovery {
    val usedRecoveryDate: LocalDate = LocalDate.of(2026, 7, 22)
    val replacementStudyDate: LocalDate = LocalDate.of(2026, 7, 26)

    private val plans: Map<LocalDate, DailyStudyPlan> = listOf(
        daily(
            LocalDate.of(2026, 7, 23),
            "病后补任务第1段：合并题续段 + 法理2 + 完形",
            law("刑法第3-7章合并题继续 50-60 分钟：先承接昨天未完成的题组，记录完成题数；每道错题只标一个主错因，不提前画正式连接图"),
            review("刑法错题账本 15-20 分钟：只登记隔日与7-14日重做日期，不把整理扩成抄写"),
            review("法理第2章闭卷回忆 20 分钟：复述目录树和关键词，未达到约70%就保留卡点，不惩罚式加时"),
            english(ExamStudyPlan.dailyVocabularyTaskTitle),
            english("英语一真题完形 20-25 分钟：补回7月22日未完成块；限时后只抓逻辑线索、词义辨析和3个主要错因"),
            health("今天仍按180分钟总预算执行；身体不适后的欠账分四天回收，23:30前停止，不熬夜清空"),
        ),
        daily(
            LocalDate.of(2026, 7, 24),
            "补任务第2段：合并题收口 + 法理2验收 + 翻译",
            law("刑法第3-7章合并题收口 50-60 分钟：完成后核对题量和主错因；未收口就继续做题，不能用画图替代"),
            review("刑法正式连接框架/思维导图 35-40 分钟：仅在合并题已经完成后开始；写主干、层级、易混点和题目锚点"),
            review("法理第2章闭卷验收 20 分钟：达到约70%后下一学习日进入第3章；未通过只补最卡的结构"),
            english(ExamStudyPlan.dailyVocabularyTaskTitle),
            english("英语一真题翻译 25-30 分钟：先限时，再复盘1个语法错点和规范表达；深复盘超时拆到缓冲，不熬夜"),
            health("固定必做控制在约153分钟，剩余约27分钟只吸收题组、翻译或身体状态的波动"),
        ),
        daily(
            LocalDate.of(2026, 7, 25),
            "补任务第3段：刑法闭环 + 法理滚动 + 阅读",
            review("刑法第3-7章闭环 45-50 分钟：收完正式连接图，并完成第一轮关键词口头复述；核对错题隔日/7-14日日期"),
            review("法理第3章第一轮入口 20分钟；如果第2章仍未达到约70%，则只补第2章最卡结构，不按日历硬跳章"),
            english(ExamStudyPlan.dailyVocabularyTaskTitle),
            english("英语一真题阅读 30-35 分钟：限时作答后写题干定位、原文证据、唯一主错因和正确选项理由"),
            english("完成后加码（不计必做）：英语一新题型 20-25 分钟；只有前四项按时完成且身体稳定才做"),
            health("周末仍按180分钟总预算；加码不是欠账，不完成不顺延"),
        ),
        daily(
            LocalDate.of(2026, 7, 26),
            "本周已用休息日后的轻补日：只收口，不开刑法第8章",
            review("本周刑法验收 35-40 分钟：核对第3-7章合并题、主错因、错题回炉日期、正式连接图和第一轮关键词痕迹，只补一个最大缺口"),
            review("法理当前章节闭卷回忆 20 分钟：处理本周卡点，留下下一章的明确入口"),
            english(ExamStudyPlan.dailyVocabularyTaskTitle),
            english(ExamStudyPlan.dailyEnglishReviewTaskTitle),
            review("周计划复盘 20-25 分钟：记录本周真实有效分钟和完成率；因7月22日身体不适，不以本周数据自动升级下周负荷"),
            health("7月22日已经使用本周休息/恢复日；今天安排约150分钟轻补，但不启动刑法第8章、不熬夜、不补全部历史欠账"),
        ),
    ).associateBy { it.date }

    private val correctedCurrentWeek = WeeklyStudyPlan(
        id = "2026-07-w4",
        title = "病后恢复周：刑法3-7章闭环 + 法理滚动 + 英语全题型",
        dateRange = "2026-07-20 至 2026-07-26",
        tasks = listOf(
            "负荷：7月22日因身体不适已使用本周完整恢复日；7月23-25日各按180分钟执行，7月26日改为约150分钟轻补和周验收。本周不再设置第二个完整休息日，也不惩罚式加长其他三天",
            "刑法：优先完成第3-7章合并题、主错因账本、隔日/7-14日回炉日期、正式连接框架和第一轮关键词口头复述；这些未闭环前不启动第8章",
            "法理：第2章闭卷达到约70%后进入第3章；未达到就只补最卡结构，不按日期跳章，也不回到第1章重复制造进度",
            "英语：每天120个单词；本周保住完形、翻译、阅读、周复盘，新题型只作为状态稳定后的加码，不完成不继续顺延",
            "周验收：按真实有效分钟和完成率记账。因为本周存在生病恢复日，不据此自动升级7月27日后的负荷；下周先延续稳定档，再根据睡眠和白天完成情况调整",
        ),
    )

    private val monthlyAllocations: Map<YearMonth, StudyMonthAllocation> = mapOf(
        YearMonth.of(2026, 7) to StudyMonthAllocation(1_740, 1_080, 420, 0, 240),
        YearMonth.of(2026, 8) to StudyMonthAllocation(7_410, 5_040, 1_560, 0, 810),
        YearMonth.of(2026, 9) to StudyMonthAllocation(9_780, 6_000, 1_680, 1_320, 780),
        YearMonth.of(2026, 10) to StudyMonthAllocation(11_790, 6_720, 2_100, 2_100, 870),
        YearMonth.of(2026, 11) to StudyMonthAllocation(12_000, 6_000, 2_400, 2_700, 900),
        YearMonth.of(2026, 12) to StudyMonthAllocation(6_240, 3_360, 840, 1_380, 660),
    )

    init {
        installIntoExamStudyPlan()
    }

    /** Makes the existing Plan/Today/Tomorrow dashboard consume the corrections. */
    fun installIntoExamStudyPlan() {
        @Suppress("UNCHECKED_CAST")
        (ExamStudyPlan.dailyPlans as? MutableMap<LocalDate, DailyStudyPlan>)?.putAll(plans)

        @Suppress("UNCHECKED_CAST")
        val mutableWeeks = ExamStudyPlan.weeklyPlans as? MutableList<WeeklyStudyPlan>
        mutableWeeks?.indexOfFirst { it.id == correctedCurrentWeek.id }
            ?.takeIf { it >= 0 }
            ?.let { mutableWeeks[it] = correctedCurrentWeek }

        @Suppress("UNCHECKED_CAST")
        val mutableMonths = ExamStudyPlan.monthlyPlans as? MutableList<MonthlyStudyPlan>
        mutableMonths?.indices?.forEach { index ->
            val plan = mutableMonths[index]
            val month = runCatching { YearMonth.parse(plan.month) }.getOrNull() ?: return@forEach
            val allocation = monthlyAllocations[month] ?: return@forEach
            val correctedTasks = plan.tasks
                .filterNot { it.startsWith("容量校准：") || it.startsWith("总量倒推：") }
                .map { task ->
                    if (month == YearMonth.of(2026, 7)) {
                        task.replace(
                            "7 月 26 日完整休息",
                            "7月22日已使用本周恢复日，7月26日改为150分钟轻补",
                        )
                    } else {
                        task
                    }
                } + allocation.description(month)
            mutableMonths[index] = plan.copy(tasks = correctedTasks)
        }
    }

    fun planFor(date: LocalDate): DailyStudyPlan? = plans[date]

    fun plannedMinutes(date: LocalDate): Int = when (date) {
        LocalDate.of(2026, 7, 23),
        LocalDate.of(2026, 7, 24),
        LocalDate.of(2026, 7, 25) -> 180
        replacementStudyDate -> 150
        usedRecoveryDate -> 0
        else -> ExamStudyPlan.plannedStudyMinutes(date)
    }

    fun applyToState(state: StudyState, date: LocalDate): StudyState {
        installIntoExamStudyPlan()
        val plan = planFor(date) ?: return state
        val dateText = date.toString()
        val manualTasks = state.tasks.filter { it.source != StudyTaskSource.Plan }
        val previousByTitle = state.tasks
            .filter { it.source == StudyTaskSource.Plan }
            .associateBy { it.title }
        val planTasks = plan.tasks.mapIndexed { index, task ->
            val title = "${task.kind.label}｜${task.title}"
            val previous = previousByTitle[title]
            StudyTask(
                id = "recovery-plan-$dateText-$index",
                title = title,
                done = previous?.done ?: false,
                createdAt = previous?.createdAt ?: date.toEpochDay(),
                completedAt = previous?.completedAt,
                completionRewardClaimed = previous?.completionRewardClaimed ?: previous?.done ?: false,
                source = StudyTaskSource.Plan,
            )
        }
        val expectedSchedule = scheduleFor(date).orEmpty()
        val currentPlanTitles = state.tasks
            .filter { it.source == StudyTaskSource.Plan }
            .map { it.title }
        val nextPlanTitles = planTasks.map { it.title }
        if (
            state.activePlanDate == dateText &&
            currentPlanTitles == nextPlanTitles &&
            state.generatedSchedules[dateText] == expectedSchedule
        ) return state
        return state.copy(
            today = dateText,
            tasks = planTasks + manualTasks,
            activePlanDate = dateText,
            superMomentAvailable = false,
            generatedSchedules = state.generatedSchedules + (dateText to expectedSchedule),
        )
    }

    fun scheduleFor(date: LocalDate): List<StudyScheduleBlock>? {
        val plan = planFor(date) ?: return null
        val budget = plannedMinutes(date)
        val coreTasks = plan.tasks.filter { it.kind != StudyPlanTaskKind.Health }
        val law = coreTasks.filter { it.kind == StudyPlanTaskKind.Law }.joinToString("；") { it.title }
        val review = coreTasks.filter { it.kind == StudyPlanTaskKind.Review }.joinToString("；") { it.title }
        val english = coreTasks.filter { it.kind == StudyPlanTaskKind.English }.joinToString("；") { it.title }
        return buildList {
            add(StudyScheduleBlock("09:30-09:40", "低阻力启动", "先喝水、简单活动；如果仍不舒服，先做20个单词再判断状态。"))
            if (law.isNotBlank()) add(StudyScheduleBlock("10:00-11:00", "刑法主块", law))
            if (review.isNotBlank()) add(StudyScheduleBlock("14:00-15:00", "闭环与背诵", review))
            if (english.isNotBlank()) add(StudyScheduleBlock("16:00-16:55", "英语", english))
            add(StudyScheduleBlock("17:05-17:20", "缓冲与收尾", "今天总预算约${budget}分钟；只吸收本日超时，写明天第一步后停止。"))
        }
    }

    fun dynamicSchedulePrompt(
        date: LocalDate,
        presetPlan: DailyStudyPlan,
        tasks: List<StudyTask>,
        currentTime: LocalTime,
    ): String? {
        if (planFor(date) == null) return null
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val unfinished = tasks.filterNot { it.done }
        return buildString {
            appendLine("日期：$date；当前时间：${currentTime.format(formatter)}。")
            appendLine("今天属于2026-07-22身体不适后的非惩罚式补任务周。7月22日已作为本周恢复/休息日，7月26日不是第二个休息日。")
            appendLine("今日有效学习总预算约${plannedMinutes(date)}分钟，包含专业课、背诵和英语，不允许叠加成更长总量。")
            appendLine("今日主题：${presetPlan.title}")
            appendLine("今日预制任务：")
            presetPlan.tasks.forEachIndexed { index, task ->
                appendLine("${index + 1}. ${task.kind.label}｜${task.title}")
            }
            appendLine("当前未完成待办：")
            if (unfinished.isEmpty()) appendLine("- 无") else unfinished.forEach { appendLine("- ${it.title}") }
            appendLine("请从当前时间或之后开始重新排时间块；已过去的时段不得补写。")
            appendLine("优先级：刑法合并题/闭环 > 法理闭卷背诵 > 英语单词与真题主训练 > 其他。")
            appendLine("不得把7月22日全部欠账塞进今天；超出预算的内容顺延到本恢复计划的下一天。")
            appendLine("7月26日只做轻补和周验收，不启动刑法第8章；任何一天都不得用熬夜清空。")
            appendLine("只输出时间表，每行严格使用：HH:mm-HH:mm｜标题｜具体安排。")
        }
    }

    fun capacityAudit(
        start: LocalDate = LocalDate.of(2026, 7, 23),
        endInclusive: LocalDate = LocalDate.of(2026, 12, 18),
    ): StudyCapacityAudit {
        require(!endInclusive.isBefore(start))
        val minutesByMonth = linkedMapOf<YearMonth, Int>()
        var cursor = start
        while (!cursor.isAfter(endInclusive)) {
            val month = YearMonth.from(cursor)
            minutesByMonth[month] = minutesByMonth.getOrDefault(month, 0) + plannedMinutes(cursor)
            cursor = cursor.plusDays(1)
        }
        val rawCourseMinutes = ExamStudyPlan.criminalLawCourseMinutes +
            ExamStudyPlan.civilLawCourseMinutes +
            ExamStudyPlan.constitutionalLawCourseMinutes +
            ExamStudyPlan.legalHistoryCourseMinutes
        return StudyCapacityAudit(
            start = start,
            endInclusive = endInclusive,
            minutesByMonth = minutesByMonth,
            totalMinutes = minutesByMonth.values.sum(),
            professionalRawCourseMinutes = rawCourseMinutes,
            professionalEffectiveInputFloorMinutes = rawCourseMinutes / 2,
            subjectMinutes = mapOf(
                StudyPlanSubject.Professional to monthlyAllocations.values.sumOf { it.professionalMinutes },
                StudyPlanSubject.English to monthlyAllocations.values.sumOf { it.englishMinutes },
                StudyPlanSubject.Politics to monthlyAllocations.values.sumOf { it.politicsMinutes },
                StudyPlanSubject.Buffer to monthlyAllocations.values.sumOf { it.bufferMinutes },
            ),
        )
    }

    private fun daily(
        date: LocalDate,
        title: String,
        vararg tasks: StudyPlanTask,
    ): DailyStudyPlan = DailyStudyPlan(date = date, title = title, tasks = tasks.toList())

    private fun law(title: String) = StudyPlanTask(title, StudyPlanTaskKind.Law)
    private fun review(title: String) = StudyPlanTask(title, StudyPlanTaskKind.Review)
    private fun english(title: String) = StudyPlanTask(title, StudyPlanTaskKind.English)
    private fun health(title: String) = StudyPlanTask(title, StudyPlanTaskKind.Health)
}

private data class StudyMonthAllocation(
    val totalMinutes: Int,
    val professionalMinutes: Int,
    val englishMinutes: Int,
    val politicsMinutes: Int,
    val bufferMinutes: Int,
) {
    init {
        require(totalMinutes == professionalMinutes + englishMinutes + politicsMinutes + bufferMinutes)
    }

    fun description(month: YearMonth): String =
        "容量校准：${month.monthValue}月总预算约${minutesText(totalMinutes)}；专业课（含听课、题源、错题、背诵和专业课套卷）${minutesText(professionalMinutes)}，英语${minutesText(englishMinutes)}，政治${minutesText(politicsMinutes)}，机动/深复盘${minutesText(bufferMinutes)}。各科必须从总预算内切分，不再额外叠加。"

    private fun minutesText(minutes: Int): String {
        val hours = minutes / 60
        val remainder = minutes % 60
        return if (remainder == 0) "${hours}小时" else "${hours}小时${remainder}分钟"
    }
}

enum class StudyPlanSubject {
    Professional,
    English,
    Politics,
    Buffer,
}

data class StudyCapacityAudit(
    val start: LocalDate,
    val endInclusive: LocalDate,
    val minutesByMonth: Map<YearMonth, Int>,
    val totalMinutes: Int,
    val professionalRawCourseMinutes: Int,
    val professionalEffectiveInputFloorMinutes: Int,
    val subjectMinutes: Map<StudyPlanSubject, Int>,
) {
    val totalHours: Double get() = totalMinutes / 60.0
    val professionalRawCourseHours: Double get() = professionalRawCourseMinutes / 60.0
    val professionalEffectiveInputFloorHours: Double get() = professionalEffectiveInputFloorMinutes / 60.0
}
