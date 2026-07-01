package me.rerere.rikkahub.data.study

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object ExamStudyPlan {
    val examDate: LocalDate = LocalDate.of(2026, 12, 21)

    val monthlyPlans = listOf(
        MonthlyStudyPlan(
            "2026-07",
            "专业课稳启动：法理二轮找回，刑民开局，英语恢复手感",
            listOf(
                "确认五科章节账本：法理学 13 章、刑法学 25 章、民法 21 章、宪法学 7 章、法制史 7 章",
                "法理学已经听课和做题一遍，本月不重新从零听课，重点做 13 章框架、错题回看、第一轮背诵找回",
                "刑法学、民法从零启动：听课后立刻做章节题，先建立题感，再进入背诵",
                "宪法学、法制史只轻量入门，不和刑民抢主力时间",
                "英语围绕剩余 1579 个待复习词滚动，每天加一点长难句或阅读题型入口",
                "每天安排散步/拉伸，晚上留收尾时间；每周至少 1 天休息或缓冲，防止畏难和熬夜反扑",
            ),
        ),
        MonthlyStudyPlan(
            "2026-08",
            "专业课一轮收尾，刑民题感加固，二轮背诵启动",
            listOf(
                "完成刑法学第 14-25 章、民法第 13-21 章一轮",
                "完成宪法学第 4-7 章、法制史第 4-7 章一轮",
                "五科开始第二轮背诵：先背框架，再背关键词，再背完整表述，不卷遍数，只追求能输出",
                "刑法、民法每周固定章节题和错题回炉，综合课每周固定框架默写",
            ),
        ),
        MonthlyStudyPlan("2026-09", "专业课二轮主体与政治轻启动", listOf("五科二轮背诵", "刑民与综合课分题型训练", "政治每天轻量启动", "英语阅读稳定训练")),
        MonthlyStudyPlan("2026-10", "三轮背诵与真题训练", listOf("专业课主观题表达", "历年真题分科训练", "英语作文模板搭建", "政治选择题和错题")),
        MonthlyStudyPlan("2026-11", "冲刺背诵与套卷节奏", listOf("专业课高频点反复输出", "英语作文定稿", "政治高频题", "每周模拟并复盘错因")),
        MonthlyStudyPlan("2026-12", "保温、模拟与作息校准", listOf("不再开新坑", "错题和高频点保温", "考前模拟", "睡眠节律稳定")),
    )

    val julyWeeks = listOf(
        WeeklyStudyPlan(
            id = "2026-07-w1",
            title = "第1周：轻启动，先把系统跑起来",
            dateRange = "2026-07-02 至 2026-07-07",
            tasks = listOf(
                "从 7 月 2 日正式进入章节制，7 月 1 日作为登录、调研、校准和资料整理日",
                "法理学第 1-4 章：错题回看、目录式背诵、框架图，不重复从零听课",
                "刑法学第 1-2 章、民法第 1 章：看课后立刻做 10-15 题，建立题感",
                "每天复习单词 60-90 个，状态好再加 1 句长难句",
                "建立 Everything 命名和检索规则，每天只整理一个小资料块",
                "第 7 天做休息/缓冲/复盘，只补最小漏项",
            ),
        ),
        WeeklyStudyPlan(
            id = "2026-07-w2",
            title = "第2周：法理起势，刑民打底",
            dateRange = "2026-07-08 至 2026-07-14",
            tasks = listOf(
                "法理学第 5-8 章：第一轮背诵找回、错题回看、框架图",
                "刑法学第 3-5 章：犯罪论总框架继续推进，章节题当天完成",
                "民法第 2-4 章：民事法律行为、代理等基础推进",
                "每天英语单词 80-100 个，长难句 1 句",
                "第 14 天做轻量缓冲，把法理 1-8 章画成一张总图",
            ),
        ),
        WeeklyStudyPlan(
            id = "2026-07-w3",
            title = "第3周：刑民继续推进，宪法法制史入场",
            dateRange = "2026-07-15 至 2026-07-21",
            tasks = listOf(
                "法理学第 9-11 章并回背第 1-8 章",
                "刑法学第 6-8 章：犯罪形态、共同犯罪等，做题后整理错因",
                "民法第 5-7 章：诉讼时效、物权入口等",
                "宪法学第 1 章、法制史第 1 章入门",
                "整理本周最常错 10 个点，第 21 天复盘和休息",
            ),
        ),
        WeeklyStudyPlan(
            id = "2026-07-w4",
            title = "第4周：五科同时成网",
            dateRange = "2026-07-22 至 2026-07-28",
            tasks = listOf(
                "法理学第 12-13 章收尾，并二次回看第 1-11 章",
                "刑法学第 9-12 章、民法第 8-11 章",
                "宪法学第 2 章、法制史第 2 章",
                "每科至少补一张知识框架图，刑民各做一次错题回炉",
                "第 28 天做 7 月阶段复盘，不新增硬章节",
            ),
        ),
        WeeklyStudyPlan(
            id = "2026-07-w5",
            title = "第5周：7月收尾，给8月铺路",
            dateRange = "2026-07-29 至 2026-07-31",
            tasks = listOf(
                "法理学 13 章框架闭环，列出最不会背的 20 个点",
                "刑法学推进到第 13 章，民法推进到第 12 章",
                "宪法学第 3 章、法制史第 3 章",
                "整理 7 月背诵清单、错题清单、框架图清单，给 8 月安排续接入口",
            ),
        ),
    )

    val dailyPlans: Map<LocalDate, DailyStudyPlan> = listOf(
        daily("2026-06-30", "启动准备日", "确认 7 月备考资料：文运/众合题册、考试分析、网课目录", "不背单词复习 50 个，只做热身", "散步 15 分钟，给大脑换气"),
        daily("2026-07-01", "启动调研日：不计正式进度", "登录和调研小红书法硕非法经验", "确认五科章节账本：法理 13、刑法 25、民法 21、宪法 7、法制史 7", "整理资料命名规则：科目-章节-题型-错因", "把 7 月计划校准到 7 月 2 日开始执行", "晚上只做收尾，不再开新章节"),
        daily("2026-07-02", "法理2 + 刑法1", "法理学第 2 章：错题回看、框架整理、第一轮背诵", "刑法学第 1 章：听课抓犯罪论总入口", "刑法学第 1 章：做 10-15 题并标错因", "英语长难句 1 句", "睡前 10 分钟整理明日最小任务"),
        daily("2026-07-03", "法理3 + 民法1", "法理学第 3 章：目录式回忆并补框架", "民法第 1 章：看课并做 10 题", "背诵：法理第 1-2 章第一轮滚动", "画民法第 1 章主体框架", "散步 15 分钟"),
        daily("2026-07-04", "刑法2 + 英语恢复", "刑法学第 2 章：看课并做题", "刑法第 1-2 章：整理犯罪论入口图", "法理第 1-3 章：口头背目录 15 分钟", "英语单词 80 个", "晚上不要加新课，只收错题"),
        daily("2026-07-05", "法理4 + 民法2", "法理学第 4 章：错题回看并背关键词", "民法第 2 章：看课并做 10 题", "背诵：民法第 1-2 章关键词", "画民法第 1-2 章总框架", "散步 15 分钟"),
        daily("2026-07-06", "刑法2补强 + 法理回背", "刑法学第 2 章：补 10 题并整理错因", "法理第 1-4 章：目录式回背", "背诵：法理第 3-4 章第一轮", "用一页纸写本周专业课小结", "散步或拉伸 20 分钟"),
        daily("2026-07-07", "第一周复盘与容错", "保底：英语单词 50-80 个", "回看法理第 1-4 章错题", "回看刑法/民法第 1-2 章错题", "补一个本周漏掉的小任务，只补最小一块", "好好吃一顿饭，散步 20 分钟"),
        daily("2026-07-08", "法理5 + 刑法3", "法理学第 5 章：错题回看、框架整理、第一轮背诵", "刑法学第 3 章：看课并做 10-15 题", "背诵：法理第 1-4 章滚动 15 分钟", "画法理第 1-5 章小总图", "英语单词 80 个"),
        daily("2026-07-09", "法理6 + 民法3", "法理学第 6 章：目录式回忆并补框架", "民法第 3 章：看课并做 10-15 题", "背诵：刑法第 1-3 章关键词", "画民法第 1-3 章框架", "英语长难句 1 句"),
        daily("2026-07-10", "法理7 + 刑法4", "法理学第 7 章：错题回看并背关键词", "刑法学第 4 章：看课并做题", "背诵：法理第 5-6 章第一轮", "整理刑法第 1-4 章犯罪论框架", "散步 15 分钟"),
        daily("2026-07-11", "民法4 + 刑法5", "民法第 4 章：看课并做题", "刑法学第 5 章：听课抓重点", "背诵：民法第 1-3 章关键词", "画民法法律行为框架", "英语单词 80-100 个"),
        daily("2026-07-12", "法理8 + 本周闭环", "法理学第 8 章：错题回看、框架整理、第一轮背诵", "回看法理第 1-7 章错题", "口头背诵法理第 1-8 章标题和关键词", "画法理第 1-8 章总框架", "整理一页本周不会背清单"),
        daily("2026-07-13", "刑民补题日", "刑法第 1-5 章：错题回看 10 题", "民法第 1-4 章：错题回看 10 题", "背诵：刑法/民法各 15 分钟", "补本周漏掉的框架图"),
        daily("2026-07-14", "轻量缓冲日", "英语单词 60-80 个", "法理第 1-7 章二次回背 20 分钟", "刑法第 1-5 章错题回看", "散步 20 分钟"),
        daily("2026-07-15", "刑法6 + 民法5", "刑法学第 6 章：看课并做题", "民法第 5 章：听课抓关键词", "背诵：刑法第 4-5 章第一轮", "画刑法第 4-6 章框架图"),
        daily("2026-07-16", "民法6 + 宪法1", "民法第 6 章：看课并做题", "宪法学第 1 章：看课并做题", "背诵：民法第 4-5 章第一轮", "画宪法第 1 章框架", "英语单词 80 个"),
        daily("2026-07-17", "法理9 + 法制史1", "法理学第 9 章：错题回看、框架整理、第一轮背诵", "法制史第 1 章：看课并做题", "背诵：法理第 1-8 章第二次滚动", "整理法制史时间线入口图"),
        daily("2026-07-18", "刑法7 + 民法7", "刑法学第 7 章：看课并做题", "民法第 7 章：听课抓重点", "背诵：刑法第 6 章第一轮", "错题：刑法本周错题回看"),
        daily("2026-07-19", "刑法8 + 民法8", "刑法学第 8 章：看课并做题", "民法第 8 章：看课并做题", "背诵：民法第 6-7 章第一轮", "画民法第 5-8 章小框架"),
        daily("2026-07-20", "法理11 + 宪法法制史回背", "法理学第 11 章：错题回看、框架整理、第一轮背诵", "宪法第 1 章：关键词回背", "法制史第 1 章：时间线回背", "整理宪法/法制史入门框架"),
        daily("2026-07-21", "周复盘：五科第一次同屏", "回看刑法第 6-8 章错题", "回看民法第 5-8 章错题", "法理第 1-11 章口头背目录", "整理本周最常错 10 个点", "休息或散步 30 分钟"),
        daily("2026-07-22", "法理12 + 刑法9", "法理学第 12 章：错题回看、框架整理、第一轮背诵", "刑法学第 9 章：看课并做题", "背诵：法理第 9-11 章第一轮", "画法理第 9-12 章思维导图"),
        daily("2026-07-23", "刑法10 + 民法9", "刑法学第 10 章：看课并做题", "民法第 9 章：听课抓重点", "背诵：刑法第 7-8 章第一轮", "画刑法第 9-10 章框架"),
        daily("2026-07-24", "民法10 + 法理13", "民法第 10 章：看课并做题", "法理学第 13 章：错题回看、框架整理、第一轮背诵", "背诵：民法第 8-9 章第一轮", "整理民法第 1-10 章目录图"),
        daily("2026-07-25", "宪法2 + 法制史2", "宪法学第 2 章：看课并做题", "法制史第 2 章：看课并做题", "背诵：宪法/法制史第 1 章回背", "画两科第 1-2 章框架"),
        daily("2026-07-26", "刑法11 + 民法11", "刑法学第 11 章：看课并做题", "民法第 11 章：听课抓重点", "背诵：刑法第 9-10 章第一轮", "错题：刑法第 1-11 章挑 10 题"),
        daily("2026-07-27", "刑法12 + 民法12", "刑法学第 12 章：看课并做题", "民法第 12 章：听课抓重点", "背诵：民法第 10-11 章第一轮", "画民法第 9-12 章框架"),
        daily("2026-07-28", "阶段复盘日", "法理第 1-13 章目录背诵", "刑法第 1-12 章框架回看", "民法第 1-12 章框架回看", "整理 7 月错题清单和背诵清单"),
        daily("2026-07-29", "法理闭环 + 刑法13", "法理学第 1-13 章：列出最不会背的 20 个点", "刑法学第 13 章：看课并做题", "法理学第 11-13 章：第一轮背诵关键词", "背诵：法理第 8-10 章滚动", "画法理 13 章总框架"),
        daily("2026-07-30", "刑法13 + 宪法3", "刑法学第 13 章：看课并做题", "宪法学第 3 章：看课并做题", "背诵：刑法第 11-12 章第一轮", "画宪法第 1-3 章框架"),
        daily("2026-07-31", "法制史3 + 7月收口", "法制史第 3 章：看课并做题", "背诵：法理第 11-13 章第一轮", "检查 7 月框架图是否补齐", "整理 8 月续接清单：刑法 14-25、民法 13-21、宪法 4-7、法制史 4-7"),
    ).associateBy { it.date }

    fun todayPlan(date: LocalDate = LocalDate.now()): DailyStudyPlan? = dailyPlans[date]

    fun todaySchedule(date: LocalDate = LocalDate.now()): List<StudyScheduleBlock> {
        val plan = todayPlan(date)
        val tasks = plan?.tasks.orEmpty()
        if (tasks.isEmpty()) {
            return listOf(
                StudyScheduleBlock("09:30-09:45", "启动", "写下今天最小任务：1 个专业课小块 + 1 个英语小块。"),
                StudyScheduleBlock("10:00-11:30", "主线学习", "优先做最怕但最重要的一项，不先整理资料。"),
                StudyScheduleBlock("14:30-15:30", "回炉", "回看错题或补框架，把今天的输入变成可复习痕迹。"),
                StudyScheduleBlock("22:30-22:45", "收尾", "只写明天第一步，不再开新章节。"),
            )
        }

        val hasLesson = tasks.any { it.title.hasAny("看课", "听课") }
        val hasPractice = tasks.any { it.title.hasAny("做题", "10-15", "错题", "题") }
        val hasRecite = tasks.any { it.title.hasAny("背诵", "回背", "口头", "关键词", "目录") }
        val hasFramework = tasks.any { it.title.hasAny("框架", "思维导图", "目录图", "时间线") }
        val hasEnglish = tasks.any { it.kind == StudyPlanTaskKind.English }
        val hasReviewDay = tasks.any { it.title.hasAny("复盘", "缓冲", "收口", "整理 7 月") }
        val lawTasks = tasks.filter { it.kind == StudyPlanTaskKind.Law }.map { it.title }
        val reviewTasks = tasks.filter { it.kind == StudyPlanTaskKind.Review }.map { it.title }
        val englishTask = tasks.firstOrNull { it.kind == StudyPlanTaskKind.English }?.title

        val mainInput = when {
            hasReviewDay -> "只处理复盘和漏项，不开大新章节。"
            lawTasks.isNotEmpty() -> lawTasks.take(2).joinToString("；")
            else -> tasks.first().title
        }
        val practice = when {
            hasPractice -> "做题后立刻标错因：概念混淆、法条不熟、题干没读懂、选项陷阱。"
            reviewTasks.isNotEmpty() -> reviewTasks.take(2).joinToString("；")
            else -> "把上午内容写成 3-5 个可回看的关键词。"
        }
        val eveningOutput = when {
            hasRecite -> reviewTasks.firstOrNull { it.hasAny("背诵", "回背", "口头", "关键词", "目录") }
                ?: "目录式背诵 15-25 分钟，先复述结构，不追求一字不差。"
            hasFramework -> reviewTasks.firstOrNull { it.hasAny("框架", "思维导图", "目录图", "时间线") }
                ?: "补一张小框架图，只画主干和易混点。"
            else -> "回看今天最不稳的 3 个点，用自己的话说出来。"
        }

        return buildList {
            add(StudyScheduleBlock("09:30-09:45", "启动", "看一眼待办，只选今天最关键的专业课入口；手机和资料搜索先放后面。"))
            add(StudyScheduleBlock("10:00-11:40", if (hasLesson) "看课/新内容" else "主线推进", mainInput))
            add(StudyScheduleBlock("11:40-12:00", "即时封口", "写下本块 3 个关键词和 1 个不懂点，午饭前别继续开新内容。"))
            add(StudyScheduleBlock("12:00-14:00", "午饭 + 午休", "吃饭和休息不计入学习时长；午休 20-40 分钟，避免下午断电。"))
            add(StudyScheduleBlock("14:20-16:00", if (hasPractice) "章节题/错题" else "回炉复习", practice))
            add(StudyScheduleBlock("16:20-17:20", if (hasFramework) "框架整理" else "二次消化", "把今天专业课压成目录、表格或错题清单，资料整理最多占 20 分钟。"))
            if (hasEnglish || englishTask != null) {
                add(StudyScheduleBlock("19:30-20:10", "英语", englishTask ?: "单词滚动复习，状态好再加 1 句长难句。"))
            }
            add(StudyScheduleBlock("20:20-21:10", "背诵输出", eveningOutput))
            add(StudyScheduleBlock("21:10-21:40", "散步/拉伸", "离开桌面 15-30 分钟，让明天还能继续，而不是今晚硬熬。"))
            add(StudyScheduleBlock("22:30-22:45", "睡前收尾", "勾选待办，写明天第一步；不再补课、不刷经验帖、不临时买资料。"))
        }
    }

    fun todayTips(date: LocalDate = LocalDate.now()): List<StudyTip> {
        val plan = todayPlan(date)
        val tasks = plan?.tasks.orEmpty()
        val tips = linkedSetOf<StudyTip>()

        tips += StudyTip(
            "今天的原则",
            "先完成专业课主线，再整理资料。经验帖只提供方法，不能替你完成看课、做题、背诵和错题回炉。",
        )
        if (tasks.any { it.title.hasAny("看课", "听课") }) {
            tips += StudyTip(
                "看课别做抄写员",
                "课前看目录，课中只记定义、构成要件、易混点和例子；课后 10 分钟内做 10-15 题，题感比漂亮笔记更重要。",
            )
        }
        if (tasks.any { it.title.hasAny("做题", "题", "错题") }) {
            tips += StudyTip(
                "错题要写错因",
                "每道错题只标一个主错因：概念混淆、法条不熟、题干没读懂、选项陷阱。第二天先看错因，不先看答案。",
            )
        }
        if (tasks.any { it.title.hasAny("背诵", "回背", "口头", "关键词", "目录") }) {
            tips += StudyTip(
                "背书三步走",
                "先背目录树，再背关键词，最后用自己的话复述。卡住时看 10 秒提示就合上书继续说，不要从头重读。",
            )
        }
        if (tasks.any { it.title.hasAny("框架", "思维导图", "目录图", "时间线") }) {
            tips += StudyTip(
                "框架只画主干",
                "一张图只放章标题、核心概念、易混关系和真题高频点。不要把教材搬进图里，图是为了回忆，不是为了收藏。",
            )
        }
        if (tasks.any { it.title.hasAny("整理", "资料", "清单", "命名") }) {
            tips += StudyTip(
                "Everything 规则",
                "文件名按“科目-章节-任务-日期”写，例如“刑法-第1章-错题-0702.md”。每天资料整理最多一个小块，必须转成题、背诵或框架。",
            )
        }
        if (tasks.any { it.kind == StudyPlanTaskKind.English }) {
            tips += StudyTip(
                "英语不断线",
                "单词用滚动复习，不要一天塞爆。长难句只拆主干、修饰和翻译，保持手感比追求一次吃透更重要。",
            )
        }
        if (tasks.any { it.kind == StudyPlanTaskKind.Health || it.title.hasAny("复盘", "缓冲", "休息") }) {
            tips += StudyTip(
                "缓冲不是偷懒",
                "复盘日只补最小漏项：错题 10 题、目录背 15 分钟、散步 20 分钟。保住连续性，比硬凑时长更值钱。",
            )
        }
        tips += StudyTip(
            "资料优先级",
            "考试分析/大纲、历年真题、章节题和错题清单优先；内部资料、保过押题、私下转账网盘群直接跳过。",
        )
        return tips.take(5)
    }

    fun daysLeft(date: LocalDate = LocalDate.now()): Long =
        ChronoUnit.DAYS.between(date, examDate).coerceAtLeast(0)

    private fun daily(date: String, title: String, vararg tasks: String): DailyStudyPlan =
        DailyStudyPlan(
            date = LocalDate.parse(date),
            title = title,
            tasks = tasks.map { task ->
                planTask(
                    title = task,
                    kind = when {
                        task.contains("英语") || task.contains("单词") || task.contains("长难句") -> StudyPlanTaskKind.English
                        task.contains("散步") || task.contains("吃") || task.contains("拉伸") -> StudyPlanTaskKind.Health
                        task.contains("回看") || task.contains("复述") || task.contains("背诵") ||
                            task.contains("框架") || task.contains("思维导图") || task.contains("整理") -> StudyPlanTaskKind.Review
                        task.contains("确认") || task.contains("补一个") -> StudyPlanTaskKind.Foundation
                        else -> StudyPlanTaskKind.Law
                    }
                )
            },
        )

    private fun planTask(title: String, kind: StudyPlanTaskKind): StudyPlanTask =
        StudyPlanTask(title = title, kind = kind)

    private fun String.hasAny(vararg keywords: String): Boolean =
        keywords.any { contains(it) }
}

data class MonthlyStudyPlan(
    val month: String,
    val focus: String,
    val tasks: List<String>,
)

data class WeeklyStudyPlan(
    val id: String,
    val title: String,
    val dateRange: String,
    val tasks: List<String>,
)

data class DailyStudyPlan(
    val date: LocalDate,
    val title: String,
    val tasks: List<StudyPlanTask>,
)

data class StudyScheduleBlock(
    val time: String,
    val title: String,
    val detail: String,
)

data class StudyTip(
    val title: String,
    val detail: String,
)

data class StudyPlanTask(
    val title: String,
    val kind: StudyPlanTaskKind,
)

enum class StudyPlanTaskKind(val label: String) {
    English("英语"),
    Law("专业课"),
    Review("复盘"),
    Health("身体"),
    Foundation("准备"),
}
