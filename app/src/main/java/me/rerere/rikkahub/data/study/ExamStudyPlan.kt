package me.rerere.rikkahub.data.study

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable

object ExamStudyPlan {
    val examDate: LocalDate = LocalDate.of(2026, 12, 21)
    const val vocabularyBacklog: Int = 1550
    const val dailyVocabularyTarget: Int = 120
    const val dailyVocabularyGroups: Int = 6
    const val weeklyRestDayCount: Int = 1
    val currentWeekRestDate: LocalDate = LocalDate.of(2026, 7, 4)
    val vocabularyDailyOptions: List<Int> = listOf(dailyVocabularyTarget)
    const val dailyVocabularyTaskTitle: String = "不背单词 120 个（6 组）"
    const val dailyLongSentenceTaskTitle: String =
        "长难句 1 句：从颉斌斌 66 句/田静《句句真研》/真题阅读中选 1 句，按唐迟方法论或田静语法拆主干、圈修饰/从句、复述译文"
    const val criminalLawChapterCount: Int = 25
    const val criminalLawTextbookPages: Int = 463
    const val criminalLawChapterOnePages: Int = 19
    const val criminalLawChapterOneCourseMinutes: Int = 360
    const val criminalLawCourseMinutes: Int = 5888
    const val civilLawCourseMinutes: Int = 5993
    const val constitutionalLawCourseMinutes: Int = 2553
    const val legalHistoryCourseMinutes: Int = 3402
    val professionalCourseHoursSummary: String =
        "刑法 $criminalLawCourseMinutes 分钟（约 98 小时 8 分钟）、民法 $civilLawCourseMinutes 分钟（约 99 小时 53 分钟）、宪法 $constitutionalLawCourseMinutes 分钟（约 42 小时 33 分钟）、法制史 $legalHistoryCourseMinutes 分钟（约 56 小时 42 分钟）"

    val dynamicScheduleSystemPrompt = """
        你是考研日计划编排助手。你只负责把用户今天的预制任务、手动新增待办、作息信息和学习习惯整理成可执行的日程表。
        输出必须严格使用中文，每行一个时间块，格式为：HH:mm-HH:mm｜标题｜具体安排。
        不要输出解释、鼓励语、Markdown 表格、编号、项目符号或多余前后缀。
        安排必须具体、严肃、可勾选；遇到听课任务时写清“听众合法硕某科课程”，不要只写“听课”。
    """.trimIndent()

    val studyHabitReference = """
        个人学习习惯与排计划规则：
        - 这是考研计划，优先追求可执行、稳定、可持续，不用漂亮但空泛的日程。
        - 专业课优先级最高。法理学已经听课和做题一遍，现在重点是背诵、框架、错题回看，不要重新从零听大量课程。
        - 刑法学、民法、宪法学、法制史需要从一轮听课和基础题开始。专业课听课默认使用众合法硕课程；安排时写成“听众合法硕刑法课程”“听众合法硕民法课程”等。
        - 专业课总课时校准：$professionalCourseHoursSummary。四科合计约 297 小时 16 分钟，不能按章节数或页数乐观估时。
        - 刑法学按 $criminalLawChapterCount 章、教材约 $criminalLawTextbookPages 页估算；第 1 章 $criminalLawChapterOnePages 页但课程约 6 小时。30-50 分钟只能算一个入口切片，不算学完一章。
        - 排刑法、民法新章时，默认先拆成 60-90 分钟听课切片 + 关键词/不懂点/小框架；整章听完后，再安排章节题 + 错题/框架 + 第一轮背诵。恢复日可缩成 30-60 分钟入口切片，但不要把一章压成一个短任务。
        - 这一章没有结束前不要单独安排题目，也不要机械写“做 5 道题”。老师课上讲的例题算在听课里；课后只留下关键词、不懂点或小框架。等整章课程闭环后，再集中做章节题、错题整理和第一轮背诵；未闭环的新章只能做已听关键词复述，不算第一轮。
        - 法硕专业课必须按倍数估算：听课一轮只是第一层成本，后面还有做题、错题总结、框架和三轮背诵。正常学习日不能只排“听一小段课”；除身体恢复日外，至少要有 60-90 分钟主块、30-45 分钟消化/框架/错题块、15-25 分钟背诵或框架封口。
        - 法理学不重听大课，但不是只看目录；闭卷背目录树和关键词算第一轮背诵，单纯看框架不算。7 月先做目录树、错题回看和第一轮关键词背诵，后面还要二轮规范表述、三轮限时输出。
        - 每章尽量拆成听课/看课、章节题、错题整理、第一轮背诵、框架图，不要只写“学习专业课”。
        - 不背单词总复习池约 $vocabularyBacklog 个，但每天固定安排 $dailyVocabularyTaskTitle，不把总池一次性塞进当天。
        - 长难句不能只写“长难句 1 句”。必须写清材料来源、老师/体系和当天动作。默认：先用唐迟长难句方法论或田静语法/长难句建立拆句动作；材料从颉斌斌 66 句、田静《句句真研》、已购买课程配套讲义或真题阅读句子中选 1 句；只做主干、修饰成分/从句、译文复述和 1 条不会的语法点，不做无边界抄笔记。
        - 长难句资料获取优先正版书、官方课程/公开试听、购买课程后的配套电子讲义和图书官方配套资源；不要安排小红书网盘倒卖、私下转账资料群或盗版 PDF。
        - 用户最近容易熬夜，日程要尊重她手动写下的起床和睡觉时间；如果没有写，默认上午慢启动、晚上 22:30 收尾。
        - 每天保留 15-30 分钟散步/拉伸。突发任务可以插入，但不要挤掉睡前收尾。
        - 用户吃饱后容易犯困，犯困后会明显不想学习。饭后不要立刻安排高压背诵、难题或长时间听课，优先安排 10-20 分钟散步、洗漱、整理桌面、轻量复盘或低阻力任务，等困劲过去后再进入深度学习。
        - 2026-07-02 用户因前一晚睡得很晚、当天早晚肠胃不适，只完成英语。后续 1-2 天要降量恢复：保留英语最小闭环，专业课只补最小入口，不要把漏掉的法理/刑法整包惩罚式滚到下一天。
        - 2026-07-03 用户昨天肚子不舒服、喝了药，又熬夜到 4 点，12 点才起来；但起床后感觉比较清醒，至少可以完成 1 小时学习，也希望被安排一点点任务。当天按“慢启动 + 可完成 1-2 小时”处理：下午先低阻力启动，再安排 30-40 分钟法理框架/错题、30-40 分钟众合法硕刑法课程入口、固定不背单词 120 个；仍然不要安排硬背诵、难题、长时间听课，也不要把昨天漏掉的任务整包追补。
        - 肠胃不适或睡眠不足后的恢复日，早晚不要安排硬背诵、难题或长时间听课；优先安排休息、清淡饮食、热水、散步/拉伸、错题回看、目录框架或 30-60 分钟低压听课入口。
        - 每周固定保留 $weeklyRestDayCount 天休息/缓冲日。2026-07-04 本周用户选择周六作为完整休息日，当天不安排任何学习任务，不安排单词、听课、背诵或题目；原本任务从 2026-07-05 起按 2-3 天轻量分摊，不惩罚式补课，同时仍要保证 12 月前完成看课、背诵和做题主线。
        - 用户可以手动新增学校任务，例如“写论文 50分钟”。这类任务必须被安排进今日计划。
        - 输出日程要先主线、再题目/回炉、再英语、再背诵收尾；中间要有饭点和休息。
    """.trimIndent()

    val subjectExecutionReference = """
        三科具体执行入口：
        - 专业课材料主线：考试分析/大纲作定义和背诵锚点，众合法硕课程作听课主线，文运/众合章节题作章节闭环，历年真题作后期题感和主观题表达校准，自己的错题清单作回炉入口。
        - 专业课每章流程：课前 5 分钟看目录；听众合法硕课程 60-90 分钟切片；切片后写 3-5 个关键词、1 个不懂点或 1 张小框架；整章听完后再做章节题；错题只标 1 个主错因；最后做框架图和第一轮关键词背诵。
        - 英语材料主线：不背单词固定 120 个；长难句用田静《句句真研》/颉斌斌 66 句/真题阅读句子三选一；阅读用历年真题解析书或正版真题册；作文用石雷鹏或王江涛择一；翻译可用唐静或已购课程；小三门优先真题解析和短方法课，不买大量新资料。
        - 英语题型流程：7 月阅读预备；8 月阅读精读每周 2-3 次；9 月加完形/新题型/翻译小块；10 月作文模板和分题型计时；11 月整套 180 分钟模拟；12 月每周至少 1 次完整限时。
        - 英语一整卷计时参考：作文 45-50 分钟，阅读 70-75 分钟，翻译 20 分钟，新题型 15-20 分钟，完形 10-15 分钟，预留涂卡和检查；英语二按实际题型分值微调。
        - 政治拆成听课理解、选择题刷题、真题/模拟题限时、主观题背诵四类。听课可用徐涛强化课/核心考案，选择题用肖秀荣 1000 题或同等题册，背诵用腿姐背诵手册/肖秀荣知识点提要择一，模拟卷用肖八、肖四，腿四/徐六只作补充。
        - 政治整卷 180 分钟训练：单选 15-20 分钟，多选 35-45 分钟，分析题 90-100 分钟，最后留 10 分钟检查和补答题卡。
    """.trimIndent()

    val monthlyPlans = listOf(
        MonthlyStudyPlan(
            "2026-07",
            "重建学习系统：专业课按章节闭环开局，英语做阅读预备，政治只做材料确认",
            listOf(
                "专业课总原则：每天只开可闭环的小块，材料入口固定为考试分析/大纲、众合法硕课程、文运/众合章节题和自己的错题清单，不临时搜网盘资料",
                "法理学：7 月不重听大课，目标是 13 章目录树、错题回看、第一轮关键词背诵和一张总框架；每天验收为能口头复述目录和 3-5 个关键词",
                "刑法学：第 1 章 6 小时课程必须先听完并做章节题/错题/框架；第 2 章完成 2-3 个听课切片；第 3 章只开入口；第 4 章不硬追，月底按实际续接",
                "民法：第 1 章听课切片和入口框架要收口，第 2 章完成 1-2 个切片，第 3 章只做入口；民法不和刑法抢同一黄金时段",
                "宪法学、法制史：7 月只做第 1 章入口框架和关键词，最多摸第 2 章入口；章节没闭环前不做整章题",
                "英语：非完整休息日固定 $dailyVocabularyTaskTitle；每周 3-4 次长难句，来源固定为田静《句句真研》/颉斌斌 66 句/真题阅读句子三选一；7 月只做阅读预备，不做完整限时阅读",
                "英语 7 月底验收：能稳定拆句，至少完成 10-12 句长难句和 2-3 个真题阅读小段精读；作文和小三门只确认材料入口，不正式启动训练",
                "政治：7 月不正式启动听课刷题，只在月底确认材料入口：徐涛强化课/核心考案、肖秀荣 1000 题、腿姐背诵手册或肖秀荣知识点提要、肖八肖四购买/获取方式",
                "每周至少 1 天完整休息/缓冲；用户明确选择休息日时当天任务清零，其他周默认用周复盘日做缓冲，不惩罚式补课",
            ),
        ),
        MonthlyStudyPlan(
            "2026-08",
            "一轮主体推进：刑民按章闭环，综合课入门，第一轮背诵不断线",
            listOf(
                "续接 7 月实际进度，刑法、民法按章推进：整章听完后才安排章节题、错题和框架，不做未闭环硬刷题",
                "刑法目标按真实课时推进到中段；民法推进到前中段。若落后，先保刑民核心章节闭环，不用硬追数字",
                "宪法学、法制史进入稳定入门：每周 2-3 次小块听课/框架，章节闭环后再做题，避免 9 月突然开荒",
                "第一轮背诵成为日常：每天至少 20-30 分钟目录树和关键词背诵，法理完整走完一轮",
                "英语正式加入阅读训练：每天固定 $dailyVocabularyTaskTitle，每周 2-3 次真题阅读精读；每篇按题干定位、原文证据、错因归类、摘 1 句长难句闭环",
                "英语小三门先铺入口：完形看固定搭配和逻辑连接，新题型看段落结构，翻译用长难句拆分；每周 1 次小块，不抢阅读主线",
                "8 月底前确认作文主线老师/材料：石雷鹏或王江涛择一，只收集模板框架，不开始大规模背作文",
            ),
        ),
        MonthlyStudyPlan(
            "2026-09",
            "二轮背诵启动：一轮收口、错题回炉、政治轻启动",
            listOf(
                "9 月前半继续补一轮尾巴，9 月中后必须把二轮背诵拉成主线：每天至少一次闭卷复述",
                "刑民开始从章节题转向错题回炉和题型意识，错题按概念、法条、题干、选项陷阱归因",
                "综合课法理/宪法/法制史进入框架默写和关键词背诵，不再只靠听课输入",
                "政治轻启动：每天 20-30 分钟基础课或选择题，不抢专业课黄金时段",
                "政治材料入口固定：徐涛强化课/核心考案作理解入口，肖秀荣 1000 题作选择题入口；听完一小节就配 10-20 道选择题并写错因",
                "英语阅读稳定训练，每周复盘错题来源；小三门每周 1-2 次，翻译优先唐静/已购课程方法，完形和新题型优先真题解析",
            ),
        ),
        MonthlyStudyPlan(
            "2026-10",
            "二轮强化到真题化：主观题表达、法言法语和题型训练",
            listOf(
                "专业课从会看转向会写：主观题表达、法言法语、踩点意识优先，每周固定闭卷输出",
                "历年真题开始分科训练，做完当天复盘，把错题回到具体章节和背诵漏洞",
                "第二轮背诵必须覆盖五科主体，背不出来的章节进入隔日回炉表",
                "英语作文模板搭建并开始小作文/大作文轮换输出：小作文固定 8-10 类表达，大作文固定描述、分析、建议三段式；分题型开始计时",
                "英语开始扣时间：阅读单篇 18-20 分钟，完形 10-15 分钟，新题型 15-20 分钟，翻译 20 分钟，作文 45-50 分钟",
                "政治选择题和错题稳定推进：1000 题章节刷 + 错因表；10 月末开始看近年真题选择题考法，不把真题当押题背",
            ),
        ),
        MonthlyStudyPlan(
            "2026-11",
            "三轮背诵与套卷节奏：高频点反复输出，错题压缩",
            listOf(
                "三轮背诵进入主线：限时回忆、默写框架、口头输出，高频点反复压实",
                "专业课套卷/真题节奏稳定，每周至少一次综合复盘，复盘比多刷一套更重要",
                "错题本开始压缩，只保留高频错因、反复错点和主观题表达问题",
                "英语作文定稿，阅读错题只保留高频错因，不再做无边界扩张；每 1-2 周做 1 次英语整套 180 分钟模拟",
                "政治高频题和错题本收束，选择题保持手感，主观题逐步背诵；肖八优先选择题和错因复盘，主观题先看结构和材料结合",
            ),
        ),
        MonthlyStudyPlan(
            "2026-12",
            "保温、模拟与作息校准：不再开新坑，只做可提分动作",
            listOf(
                "不再开新坑，只保温错题、高频点、作文模板、政治易混点和专业课主观题表达",
                "模拟以校准节奏为主：时间分配、答题顺序、卡壳处理和卷面表达比临时扩资料更重要",
                "每天保留轻量英语和政治手感，专业课背诵短频快回炉，避免大块崩盘；英语每周至少 1 次完整 180 分钟限时，政治每周至少 1 次选择题限时或整卷模拟",
                "政治肖四主观题进入背诵主线；整卷 180 分钟按单选 15-20 分钟、多选 35-45 分钟、分析题 90-100 分钟、检查 10 分钟训练",
                "考前一周不追求新进度，重点稳定睡眠、饮食、证件材料、路线和可执行心态",
            ),
        ),
    )

    val julyWeeks = listOf(
        WeeklyStudyPlan(
            id = "2026-07-w1",
            title = "第1周：病后慢启动，但建立真实闭环",
            dateRange = "2026-07-02 至 2026-07-07",
            tasks = listOf(
                "从 7 月 2 日正式进入章节制，7 月 1 日作为登录、调研、校准和资料整理日",
                "7 月 2-3 日按身体恢复处理，不惩罚式补课；7 月 4 日为本周完整休息日，不安排任何学习任务",
                "刑法第 1 章：本周完成 2-3 个众合法硕听课切片；每个切片后写 3-5 个关键词、1 个不懂点或 1 张小框架，不做整章章节题",
                "法理第 1-3 章：只用考试分析/错题本回看目录树和关键词，不重听大课；每天只验收 1 个小块",
                "民法第 1 章：只开 30-45 分钟众合法硕课程入口，写主体和民事法律关系关键词，不急着做题",
                "英语：非完整休息日固定 $dailyVocabularyTaskTitle；做 2-3 句长难句，来源用田静《句句真研》或真题阅读句子，不做完整阅读",
                "第 7 天只复盘已听分钟数、关键词卡点和下周第一步；饭后只散步/整理桌面，不补硬任务",
            ),
        ),
        WeeklyStudyPlan(
            id = "2026-07-w2",
            title = "第2周：刑法1收口，刑法2/民法1打底",
            dateRange = "2026-07-08 至 2026-07-14",
            tasks = listOf(
                "法理学第 5-8 章：用考试分析和错题本做第一轮关键词背诵，每章留下目录树和 3 个最不稳关键词",
                "刑法第 1 章收口：补完约 6 小时众合法硕课程，整章闭环后再做文运/众合章节题 10-15 题并标主错因",
                "刑法第 2 章只启动 1-2 个听课切片，不做未闭环题；每个切片留 3-5 个关键词和 1 个不懂点",
                "民法第 1 章推进到入口收口：累计 2-3 个众合法硕切片，画主体/法律关系入口框架；整章未闭环前不刷题",
                "英语：固定 $dailyVocabularyTaskTitle；长难句至少 3 句，其中 1 句从真题阅读里摘，按主干、修饰、译文复述闭环",
                "第 14 天轻量缓冲：只统计刑法/民法已听分钟数、错题清单和下周第一步，不新增硬章节",
            ),
        ),
        WeeklyStudyPlan(
            id = "2026-07-w3",
            title = "第3周：刑法2-3、民法2-3推进，综合课入场",
            dateRange = "2026-07-15 至 2026-07-21",
            tasks = listOf(
                "法理学第 9-11 章：完成目录树和关键词背诵，并用 15 分钟回炉第 1-8 章最容易忘的目录",
                "刑法第 2 章：按众合法硕课程推进 2 个切片；如果第 1 章已经闭环，再做第 1 章章节题错题回看，不碰第 2 章整章题",
                "民法第 2 章：只做 1-2 个听课切片，抓民事法律行为、代理、物权入口，不追求一周吃完整章",
                "宪法学第 1 章、法制史第 1 章：各 1 个 30-45 分钟入口切片，输出一张入口框架，不刷题",
                "英语：固定 $dailyVocabularyTaskTitle；长难句 3 句，阅读预备 1 小段，只做定位和译文复述，不计时",
                "第 21 天周复盘/缓冲：只整理已闭环范围最常错 10 个点，未闭环章节只统计已听分钟数和卡点",
            ),
        ),
        WeeklyStudyPlan(
            id = "2026-07-w4",
            title = "第4周：刑民小循环成型，法理一轮找回收口",
            dateRange = "2026-07-22 至 2026-07-28",
            tasks = listOf(
                "法理学第 12-13 章收尾，并二次回看第 1-11 章；月底形成一张 13 章总目录图",
                "刑法第 3 章、民法第 3 章只按课时切片推进；能闭环的旧章节补章节题和框架，没闭环的新章只留关键词",
                "宪法第 1 章、法制史第 1 章入口框架收口；第 2 章只允许 30-45 分钟摸入口，不抢刑民时间",
                "刑民各做一次错题回炉：只回看已闭环章节，把错因和对应知识点写清，不重新刷一堆题",
                "英语：固定 $dailyVocabularyTaskTitle；长难句 3-4 句，阅读预备 1-2 个小段，仍不做完整限时阅读",
                "第 28 天做 7 月阶段复盘，不新增硬章节；饭后只做散步、洗漱、整理桌面或轻复盘",
            ),
        ),
        WeeklyStudyPlan(
            id = "2026-07-w5",
            title = "第5周：7月收口，按真实进度给8月续接",
            dateRange = "2026-07-29 至 2026-07-31",
            tasks = listOf(
                "法理学 13 章框架闭环，列出最不会背的 20 个点，并标注 8 月第一轮回炉顺序",
                "刑法、民法按实际听课分钟数收口：优先补未闭环切片，不为追数字强开新章",
                "宪法第 1-2 章、法制史第 1-2 章只补入口和框架，不做整章题",
                "英语只按当天 $dailyVocabularyTaskTitle 和 1 句长难句走；整理 8 月阅读材料入口：真题解析书/正版真题册 + 一个阅读方法主线",
                "政治只确认材料入口和购买/课程位置，不正式听课刷题：徐涛强化课/核心考案、肖秀荣 1000 题、腿姐/肖秀荣背诵手册、肖八肖四",
                "整理 7 月背诵清单、错题清单、框架图清单和各科已听分钟数，给 8 月安排续接入口；8 月按真实进度排",
            ),
        ),
    )

    val dailyPlans: Map<LocalDate, DailyStudyPlan> = listOf(
        daily("2026-06-30", "启动准备日", "确认 7 月备考资料：文运/众合题册、考试分析、网课目录", dailyVocabularyTaskTitle, "散步 15 分钟，给大脑换气"),
        daily("2026-07-01", "启动调研日：不计正式进度", "登录和调研小红书法硕非法经验", "确认五科章节账本：法理 13、刑法 25、民法 21、宪法 7、法制史 7", "整理资料命名规则：科目-章节-题型-错因", "把 7 月计划校准到 7 月 2 日开始执行", "晚上只做收尾，不再开新章节"),
        daily("2026-07-02", "法理2 + 刑法1入口", "法理学第 2 章：错题回看、框架整理、第一轮背诵", "刑法学第 1 章：听众合法硕刑法课程 40-60 分钟，作为 6 小时课程的第 1 个切片，只抓犯罪论总入口", "刑法学第 1 章：听完写 3 个关键词和 1 个不懂点，不急着做大题", dailyLongSentenceTaskTitle, "睡前 10 分钟整理明日最小任务"),
        daily("2026-07-03", "病后慢启动：保底 + 可加码", "起床后先吃清淡、喝热水、洗漱，整理桌面 10 分钟；今天不追上午时长", "法理学第 2 章：看错题和框架 30-40 分钟，今天只恢复熟悉度，不算背诵轮次", "刑法学第 1 章：听众合法硕刑法课程 30-40 分钟，作为 6 小时课程的恢复日入口切片，听完写 3 个关键词和 1 个不懂点", "加码任务：如果晚上身体还可以，只补刑法第 1 章小框架，不单独安排题目；课上例题算在听课里", "不背单词 120 个（6 组）：拆成 3 次完成，每次 2 组，中间必须离桌休息", "饭后散步或慢走 15-20 分钟，等困劲过去再回桌面", "睡前 10 分钟写明天第一步，23:30 前结束学习入口"),
        restDaily("2026-07-04", "周六完整休息日：今天无学习任务"),
        daily("2026-07-05", "休息后重启：刑法1主块 + 法理小封口", "上午先慢启动，热水、清淡早饭、整理桌面 10 分钟后再开始", "刑法学第 1 章：听众合法硕刑法课程 75-90 分钟，作为休息后第 2 个切片，抓犯罪论总入口和构成要件", "刑法学第 1 章：听完只写 3-5 个关键词和 1 个不懂点，不单独安排题目；课上例题算在听课里", "法理学第 2 章：用考试分析目录树第一轮背诵 20 分钟，卡住只看关键词提示", dailyLongSentenceTaskTitle, "饭后散步 15-20 分钟；晚上只做框架封口和提前睡"),
        daily("2026-07-06", "刑法1继续 + 民法1入口", "刑法学第 1 章：听众合法硕刑法课程 75-90 分钟，作为第 3 个切片，听完补构成要件小框架", "民法第 1 章：听众合法硕民法课程 30-45 分钟，抓主体和民事法律关系入口", "民法第 1 章：只写 3-5 个入口关键词和 1 个不懂点，不急着做整章题", "英语：不背单词按 20 个一组做 6 组，错词只标记不抄写", "饭后散步 15-20 分钟，等困劲过去再回桌面"),
        daily("2026-07-07", "第一周复盘 + 刑法轻补", "刑法学第 1 章：听众合法硕刑法课程 45-60 分钟，只补一个轻切片或回看已听切片，不单独安排题目", "统计刑法第 1 章本周已听分钟数、已学范围和未闭环切片", "回看法理第 1-3 章错题和目录背诵卡点", "回看民法第 1 章入口关键词，补 5 个关键词", "英语阅读预备：从真题阅读里摘 1 句长难句，只做主干、修饰和译文复述", "好好吃一顿饭，散步 20 分钟"),
        daily("2026-07-08", "法理5 + 刑法1第4切片", "法理学第 5 章：用考试分析和错题本回看目录、框架、关键词，完成第一轮背诵痕迹", "刑法学第 1 章：听众合法硕刑法课程 60-90 分钟，作为第 4 个切片，听完写构成要件小框架", "背诵：法理第 1-4 章滚动 15 分钟，只背目录和关键词", "画法理第 1-5 章小总图，资料整理不超过 20 分钟", dailyVocabularyTaskTitle, "饭后只散步或整理桌面，不开新课"),
        daily("2026-07-09", "法理6 + 民法1第2切片", "法理学第 6 章：用考试分析做目录树第一轮背诵，并补 3 个不稳关键词", "民法第 1 章：听众合法硕民法课程 60-75 分钟，作为第 2 个切片，课后只留关键词和不懂点", "背诵：刑法第 1 章已听关键词 10 分钟，不背完整表述", "画民法第 1 章主体/法律关系入口框架", dailyLongSentenceTaskTitle, "饭后散步 15 分钟"),
        daily("2026-07-10", "法理7 + 刑法1第5切片", "法理学第 7 章：错题回看并背关键词，验收为能口头说出本章目录", "刑法学第 1 章：听众合法硕刑法课程 60-90 分钟，作为第 5 个切片，补齐犯罪论入口笔记", "背诵：法理第 5-6 章第一轮目录和关键词", "整理刑法第 1 章犯罪论总框架，暂不做整章题", "英语：不背单词 120 个，状态好再摘 1 个真题阅读小段做定位", "散步 15 分钟"),
        daily("2026-07-11", "民法1继续 + 刑法1收口准备", "民法第 1 章：听众合法硕民法课程 60-75 分钟，作为第 3 个切片，课后只留关键词和小框架", "刑法学第 1 章：回看已听切片，列出整章章节题前还缺哪些知识点", "背诵：民法第 1 章关键词 15 分钟，只背入口概念", "画民法法律关系框架", dailyLongSentenceTaskTitle, "饭后洗漱/散步后再回桌面"),
        daily("2026-07-12", "法理8 + 本周闭环", "法理学第 8 章：用考试分析和错题本回看、框架整理、第一轮关键词背诵", "回看法理第 1-7 章错题，只标最常错的 5 个点", "法理第 1-8 章：第一轮背诵标题和关键词，不追求完整表述", "画法理第 1-8 章总框架", "整理一页本周不会背清单", "散步 20 分钟"),
        daily("2026-07-13", "刑法1收口 + 民法1框架", "刑法第 1 章：如果 6 小时课程已闭环，做文运/众合章节题 10-15 题，把错因分成概念不清、法条不熟、题干没读懂；未闭环就改为补听课", "民法第 1 章：先补主体和法律关系框架，未确认整章听完前不做整章章节题", "背诵：刑法第 1 章第一轮关键词 15 分钟，民法第 1 章只背入口关键词", "补本周漏掉的框架图", "英语阅读预备：真题阅读 1 小段，只做题干定位和译文复述", "饭后低阻力整理桌面 10 分钟"),
        daily("2026-07-14", "轻量缓冲日", "今天不新增硬章节；只做缓冲、复盘和身体恢复", "法理第 1-7 章二次背诵 20 分钟，只背目录树", "刑法第 1 章错题/切片清单回看，确认 6 小时课程还缺哪些切片", "英语：单词 120 个拆成 6 组，状态差可拆到早中晚", "散步 20 分钟，饭后不补课"),
        daily("2026-07-15", "刑法2第1切片 + 民法2入口", "刑法学第 2 章：听众合法硕刑法课程 60-90 分钟，作为第 1 个切片，听完写 3-5 个关键词和 1 个不懂点", "民法第 2 章：听众合法硕民法课程 30-45 分钟，只抓入口概念", "背诵：刑法第 1 章第一轮关键词，能复述结构即可", "画刑法第 1-2 章连接框架", dailyLongSentenceTaskTitle, "饭后散步 15 分钟"),
        daily("2026-07-16", "民法2第1切片 + 宪法1入口", "民法第 2 章：听众合法硕民法课程 60-90 分钟，作为第 1 个切片，课后只留关键词和不懂点", "宪法学第 1 章：听众合法硕宪法课程 30-45 分钟，只抓总入口和核心概念", "背诵：民法第 1 章第一轮关键词", "画宪法第 1 章入口框架", dailyVocabularyTaskTitle, "饭后洗漱/散步，等困劲过去再整理框架"),
        daily("2026-07-17", "法理9 + 法制史1入口", "法理学第 9 章：用考试分析和错题本回看、框架整理、第一轮关键词背诵", "法制史第 1 章：听众合法硕法制史课程 30-45 分钟，抓朝代线索入口", "背诵：法理第 1-8 章第二次滚动，只背目录和关键词", "整理法制史时间线入口图", "英语阅读预备：从真题阅读里摘 1 句长难句拆解", "散步 15-20 分钟"),
        daily("2026-07-18", "刑法2第2切片 + 本周回炉", "刑法学第 2 章：听众合法硕刑法课程 60-90 分钟，作为第 2 个切片，课后只留关键词和不懂点", "民法第 2 章：回看第 1 切片关键词 15 分钟，不做题", "背诵：刑法第 1 章第一轮关键词", "错题：只回看刑法第 1 章已闭环错题 5-10 题，未闭环不刷", dailyLongSentenceTaskTitle, "饭后低阻力整理错题清单"),
        daily("2026-07-19", "民法2第2切片 + 刑法2框架", "民法第 2 章：听众合法硕民法课程 60-90 分钟，作为第 2 个切片，课后只留关键词和不懂点", "刑法第 2 章：整理听课小框架和不懂点，不做整章题", "背诵：民法第 1 章第一轮关键词", "画民法第 1-2 章小框架", "英语：单词 120 个；状态好做真题阅读 1 小段定位", "散步 20 分钟"),
        daily("2026-07-20", "法理11 + 宪法法制史背诵", "法理学第 11 章：用考试分析和错题本回看、框架整理、第一轮关键词背诵", "宪法第 1 章：关键词背诵 15 分钟，只背入口概念", "法制史第 1 章：时间线背诵 15 分钟", "整理宪法/法制史入门框架", "英语：田静《句句真研》或真题阅读句子 1 句长难句", "饭后不做硬背诵，先散步"),
        daily("2026-07-21", "周复盘：五科第一次同屏", "今天作为周复盘/缓冲，不新增大章节", "回看刑法第 1 章错题；刑法第 2 章只统计已听分钟数和未闭环切片", "回看民法第 1 章错题；民法第 2 章只统计已听分钟数和未闭环切片", "法理第 1-11 章口头背目录，列出最卡的 5 章", "整理已闭环范围里最常错 10 个点", "休息或散步 30 分钟"),
        daily("2026-07-22", "法理12 + 刑法3第1切片", "法理学第 12 章：用考试分析和错题本回看、框架整理、第一轮关键词背诵", "刑法学第 3 章：听众合法硕刑法课程 60-90 分钟，作为第 1 个切片，听完写 3-5 个关键词和 1 个不懂点", "背诵：法理第 9-11 章第一轮目录和关键词", "画法理第 9-12 章思维导图", dailyLongSentenceTaskTitle, "饭后散步 15 分钟"),
        daily("2026-07-23", "刑法3第2切片 + 民法3入口", "刑法学第 3 章：听众合法硕刑法课程 60-90 分钟，作为第 2 个切片，课后只留关键词和不懂点", "民法第 3 章：听众合法硕民法课程 30-45 分钟，只抓入口，不做题", "刑法第 2 章：只复述已听关键词，未确认整章闭环前不算第一轮背诵", "画刑法第 2-3 章框架", "英语阅读预备：真题阅读 1 小段，标定位词和转折词", "饭后洗漱/整理桌面"),
        daily("2026-07-24", "民法3第1切片 + 法理13", "民法第 3 章：听众合法硕民法课程 60-90 分钟，作为第 1 个切片，听完写 3-5 个关键词", "法理学第 13 章：用考试分析和错题本回看、框架整理、第一轮关键词背诵", "民法第 2 章：只复述已听关键词，未确认整章闭环前不算第一轮背诵", "整理民法第 1-3 章目录图", dailyLongSentenceTaskTitle, "散步 15-20 分钟"),
        daily("2026-07-25", "宪法1 + 法制史1入口闭环", "宪法学第 1 章：听众合法硕宪法课程 45-60 分钟，补入口框架；本章结束前不单独安排题目", "法制史第 1 章：听众合法硕法制史课程 45-60 分钟，补时间线；本章结束前不单独安排题目", "背诵：宪法/法制史第 1 章关键词，各 10 分钟", "画两科第 1 章框架", "英语：单词 120 个，状态好摘 1 句真题长难句", "饭后不刷题"),
        daily("2026-07-26", "刑法3回炉 + 刑法4可选入口", "刑法第 3 章：回看听课框架，标出混淆点和未闭环切片", "刑法学第 4 章：只有状态好才听众合法硕刑法课程 45-60 分钟作为入口；状态一般就不新开", "刑法第 2-3 章：只复述已听关键词，未闭环章节不算第一轮", "错题：只从确认整章闭环的刑法章节里挑 5-10 题；没闭环就不挑题", dailyLongSentenceTaskTitle, "散步 20 分钟"),
        daily("2026-07-27", "民法3回炉 + 民法4可选入口", "民法第 3 章：回看听课框架，标出混淆点和未闭环切片", "民法第 4 章：只有状态好才听众合法硕民法课程 45-60 分钟作为入口；状态一般就不新开", "民法第 2-3 章：只复述已听关键词，未闭环章节不算第一轮", "画民法第 3-4 章框架草图，资料整理不超过 20 分钟", "英语阅读预备：真题阅读 1 小段，口头复述译文", "饭后散步"),
        daily("2026-07-28", "阶段复盘日", "今天不新增硬章节，只做 7 月阶段复盘和身体缓冲", "法理第 1-13 章目录背诵，列出最不会背的 20 个点", "刑法第 1-4 章：统计已听分钟数、未闭环切片和已闭环范围错题", "民法第 1-4 章：统计已听分钟数、未闭环切片和已闭环范围错题", "整理 7 月错题清单、背诵清单、框架图清单", "散步 30 分钟"),
        daily("2026-07-29", "法理闭环 + 刑法未闭环补切片", "法理学第 1-13 章：列出最不会背的 20 个点，并标注 8 月回炉顺序", "刑法学：按实际进度补一个未闭环切片 60-90 分钟；如果第 4 章已开，就作为第 2 个切片，否则补第 2-3 章", "法理学第 11-13 章：第一轮关键词背诵 20 分钟", "画法理 13 章总框架", dailyLongSentenceTaskTitle, "饭后低阻力整理框架"),
        daily("2026-07-30", "民法未闭环补切片 + 宪法2入口", "民法：按实际进度补一个未闭环切片 60-90 分钟；如果第 4 章已开，就作为第 2 个切片，否则补第 2-3 章", "宪法学第 2 章：听众合法硕宪法课程 30-45 分钟，只抓入口，不做题", "刑法第 3-4 章：只复述已听关键词，未闭环章节不算第一轮", "画宪法第 1-2 章框架", "英语：单词 120 个；整理 8 月阅读真题材料入口", "饭后散步 15-20 分钟"),
        daily("2026-07-31", "法制史2入口 + 7月收口", "法制史第 2 章：听众合法硕法制史课程 30-45 分钟，只抓入口和时间线", "背诵：法理第 11-13 章第一轮目录和关键词", "检查 7 月框架图是否补齐：法理总图、刑法第 1 章、民法第 1 章、宪法/法制史第 1 章", "整理 8 月续接清单：刑法、民法、宪法、法制史按实际听课分钟数续接", "英语：确认 8 月阅读材料入口，真题解析书/正版真题册 + 一个阅读方法主线", "政治：只确认材料入口和购买/课程位置，徐涛强化课/核心考案、肖秀荣 1000 题、腿姐/肖秀荣背诵手册、肖八肖四"),
    ).associateBy { it.date }

    fun todayPlan(date: LocalDate = LocalDate.now()): DailyStudyPlan? = dailyPlans[date]

    fun todaySchedule(date: LocalDate = LocalDate.now()): List<StudyScheduleBlock> {
        val plan = todayPlan(date)
        val tasks = plan?.tasks.orEmpty()
        if (isRestDay(date)) {
            return listOf(
                StudyScheduleBlock("09:30-10:00", "自然醒 + 早餐", "今天是完整休息日，不安排单词、听课、背诵或题目。"),
                StudyScheduleBlock("11:30-12:00", "出门走走", "轻松散步或晒太阳，不计入学习任务。"),
                StudyScheduleBlock("14:00-16:00", "自由时间", "休息、娱乐、补觉或处理生活事，不打开考研任务。"),
                StudyScheduleBlock("20:30-20:45", "轻收尾", "只确认明天第一步，不复盘、不补课、不因为休息产生负罪感。"),
            )
        }
        if (date == LocalDate.of(2026, 7, 3)) {
            return listOf(
                StudyScheduleBlock("12:00-12:40", "起床恢复", "吃清淡、喝热水、洗漱，整理桌面 10 分钟；今天不补上午缺口。"),
                StudyScheduleBlock("13:20-14:00", "单词 1", "不背单词 40 个（2 组），只做识别和复习。"),
                StudyScheduleBlock("14:20-15:00", "法理入口", "法理学第 2 章只回看错题和目录框架，不背完整表述、不补难题。"),
                StudyScheduleBlock("15:15-15:55", "刑法入口", "听众合法硕刑法课程第 1 章，作为约 6 小时课程的入口切片，只抓犯罪论总入口，听完写 3 个关键词和 1 个不懂点。"),
                StudyScheduleBlock("16:00-16:40", "单词 2", "不背单词 40 个（2 组），错词只标记，不展开抄写。"),
                StudyScheduleBlock("18:20-19:00", "饭后低阻力", "饭后不要硬学；先慢走、洗漱或整理桌面，等困劲过去。"),
                StudyScheduleBlock("19:10-19:35", "刑法加码", "如果身体还可以，只补刑法第 1 章小框架；不单独安排题目，课上例题算在听课里。"),
                StudyScheduleBlock("19:45-20:25", "单词 3", "不背单词 40 个（2 组），完成今天英语最小闭环。"),
                StudyScheduleBlock("21:20-21:35", "睡前收尾", "写明天第一步；23:30 前结束学习入口，不用补课证明自己。"),
            )
        }
        if (tasks.isEmpty()) {
            return listOf(
                StudyScheduleBlock("09:30-09:45", "启动", "写下今天最小任务：1 个专业课小块 + 1 个英语小块。"),
                StudyScheduleBlock("10:00-11:30", "主线学习", "优先做最怕但最重要的一项，不先整理资料。"),
                StudyScheduleBlock("14:30-15:30", "回炉", "回看错题或补框架，把今天的输入变成可复习痕迹。"),
                StudyScheduleBlock("22:30-22:45", "收尾", "只写明天第一步，不再开新章节。"),
            )
        }

        val hasLesson = tasks.any { it.title.hasAny("看课", "听课") }
        val hasClosedChapterPractice = tasks.any { it.title.hasAny("整章章节题", "已闭环错题") }
        val hasPractice = hasClosedChapterPractice || tasks.any { task ->
            task.title.hasAny("错题") && !task.title.hasAny("未确认整章听完前不做", "不做整章章节题")
        }
        val hasRecite = tasks.any { it.title.hasAny("背诵", "回背", "口头", "关键词", "目录") }
        val hasFramework = tasks.any { it.title.hasAny("框架", "思维导图", "目录图", "时间线") }
        val hasEnglish = tasks.any { it.kind == StudyPlanTaskKind.English }
        val hasReviewDay = tasks.any { it.title.hasAny("复盘", "缓冲", "收口", "整理 7 月") }
        val lawTasks = tasks.filter { it.kind == StudyPlanTaskKind.Law }.map { it.title }
        val reviewTasks = tasks.filter { it.kind == StudyPlanTaskKind.Review }.map { it.title }
        val englishTasks = tasks.filter { it.kind == StudyPlanTaskKind.English }.map { it.title }

        val mainInput = when {
            hasReviewDay -> "只处理复盘和漏项，不开大新章节。"
            lawTasks.isNotEmpty() -> lawTasks.take(2).joinToString("；")
            else -> tasks.first().title
        }
        val practice = when {
            hasClosedChapterPractice -> "整章课程闭环后再做章节题；做题后立刻标错因：概念混淆、法条不熟、题干没读懂、选项陷阱。"
            hasPractice -> "只回看已闭环章节错题，标出错因和对应知识点；没学完的章节不硬刷整章题。"
            reviewTasks.isNotEmpty() -> reviewTasks.take(2).joinToString("；")
            else -> "把上午内容写成 3-5 个关键词、1 个不懂点或一张小框架；不单独安排题目。"
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
            val practiceTitle = when {
                hasClosedChapterPractice -> "整章章节题/错题"
                hasPractice -> "错题回炉"
                else -> "消化/框架"
            }
            add(StudyScheduleBlock("14:20-16:00", practiceTitle, practice))
            add(StudyScheduleBlock("16:20-17:20", if (hasFramework) "框架整理" else "二次消化", "把今天专业课压成目录、表格或错题清单，资料整理最多占 20 分钟。"))
            if (hasEnglish || englishTasks.isNotEmpty()) {
                add(StudyScheduleBlock("19:30-20:10", "英语", englishTasks.joinToString("；").ifBlank { "$dailyVocabularyTaskTitle；状态好再加：$dailyLongSentenceTaskTitle。" }))
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

        if (isRestDay(date)) {
            return listOf(
                StudyTip(
                    "完整休息日",
                    "今天不安排任何学习任务，也不补单词。休息是本周系统恢复，原任务从明天起分摊。",
                ),
                StudyTip(
                    "明天再重启",
                    "只需要晚上确认明天第一步：刑法第 1 章听课切片 + 法理小封口。不要今天偷补。",
                ),
            )
        }

        tips += StudyTip(
            "今天的原则",
            "先完成专业课主线，再整理资料。经验帖只提供方法，不能替你完成看课、做题、背诵和错题回炉。",
        )
        if (tasks.any { it.kind == StudyPlanTaskKind.English }) {
            tips += StudyTip(
                "长难句三步拆",
                "$vocabularyBacklog 左右是总复习池，不是当天任务。今天固定 $dailyVocabularyTaskTitle；$dailyLongSentenceTaskTitle。资料先用正版书/官方课/配套讲义，不为找资料消耗当天学习块。",
            )
        }
        if (tasks.any { it.title.hasAny("看课", "听课") }) {
            tips += StudyTip(
                "看课别做抄写员",
                "课前看目录，课中只记定义、构成要件、易混点和老师例题；章节没结束前不单独安排题目，整章闭环后再做章节题。",
            )
        }
        if (tasks.any { it.title.hasAny("做题", "题", "错题") }) {
            tips += StudyTip(
                "题目跟着整章走",
                "这一章没有结束前不要单独做题。整章听完后再集中做章节题；每道错题只标一个主错因，第二天先看错因。",
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

    fun dynamicSchedulePrompt(
        date: LocalDate,
        presetPlan: DailyStudyPlan?,
        defaultSchedule: List<StudyScheduleBlock>,
        tasks: List<StudyTask>,
    ): String {
        if (isRestDay(date)) {
            return """
                你是考研日计划编排助手。今天日期：$date。
                今天是用户明确选择的完整休息日，不安排任何学习任务。
                不要安排单词、长难句、听课、背诵、做题、错题、框架、复盘或资料整理。
                如果必须输出日程，只输出休息、吃饭、散步、自由时间和睡前确认明天第一步。
                只输出时间表，每行：HH:mm-HH:mm｜标题｜具体安排。
            """.trimIndent()
        }
        val planTasks = tasks.filter { it.source == StudyTaskSource.Plan }
        val manualTasks = tasks.filter { it.source == StudyTaskSource.Manual }
        return buildString {
            appendLine(studyHabitReference)
            appendLine()
            appendLine(subjectExecutionReference)
            appendLine()
            appendLine("日期：$date")
            appendLine("今日预制主题：${presetPlan?.title ?: "无预制主题，按最小闭环安排"}")
            appendLine("今日预制任务：")
            presetPlan?.tasks.orEmpty().forEachIndexed { index, task ->
                appendLine("${index + 1}. ${task.kind.label}｜${task.title}")
            }
            if (presetPlan == null) appendLine("1. 专业课小块 + 英语小块 + 散步/收尾")
            appendLine()
            appendLine("当前待办（包含用户手动新增和系统计划任务）：")
            if (tasks.isEmpty()) {
                appendLine("- 暂无待办，请按预制主题安排。")
            } else {
                planTasks.forEach { task -> appendLine("- 计划：${task.title}") }
                manualTasks.forEach { task -> appendLine("- 手动：${task.title}") }
            }
            appendLine()
            appendLine("默认日程骨架，可调整但不要丢掉饭点、休息、运动和睡前收尾：")
            defaultSchedule.forEach { block ->
                appendLine("${block.time}｜${block.title}｜${block.detail}")
            }
            appendLine()
            appendLine("请重新生成今天的计划表。要求：")
            appendLine("- 如果用户手动待办里写了起床/睡觉时间，按它调整时间段。")
            appendLine("- 如果用户说睡太晚、肚子不舒服或身体难受，优先降量恢复；不要把昨天漏项整包顺延，只补最小入口。")
            appendLine("- 如果用户手动待办里写了临时任务和分钟数，必须插入计划表。")
            appendLine("- 非完整休息日固定保留 $dailyVocabularyTaskTitle；如果日期是 2026-07-04 或用户明确选择当天完整休息，当天不安排任何学习任务，单词也不补。")
            appendLine("- 长难句必须写清来源和步骤，例如：$dailyLongSentenceTaskTitle。不要写成泛泛学习。")
            appendLine("- 听课任务必须写“听众合法硕某科课程”，例如“听众合法硕刑法课程第 1 章”。")
            appendLine("- 专业课按课时估算，不按页数乐观估算：$professionalCourseHoursSummary。刑法第 1 章 19 页但约 6 小时，30-50 分钟只能算听课切片。")
            appendLine("- 刑法、民法新章优先排 60-90 分钟听课切片 + 3-5 个关键词/不懂点/小框架；恢复日才缩成 30-60 分钟入口切片。")
            appendLine("- 这一章没有结束前不要单独安排题目，也不要机械安排“5 道题”；老师课上例题算在听课里。整章课程闭环后，再安排章节题、错题整理和第一轮背诵；未闭环新章只能复述已听关键词，不算第一轮。")
            appendLine("- 法理学不用重听大课，但必须背：闭卷背目录树和关键词算第一轮，单纯看框架不算；后面再进入规范表述和限时输出。")
            appendLine("- 正常学习日不能只排一个短听课入口：至少安排 60-90 分钟专业课主块、30-45 分钟消化/框架/错题块、15-25 分钟背诵或框架封口。")
            appendLine("- 专业课要按倍数考虑：听课后还要做题、错题总结、框架和三轮背诵；不要把听完课当作学完。")
            appendLine("- 只输出时间表，每行：HH:mm-HH:mm｜标题｜具体安排。")
        }
    }

    fun parseScheduleBlocks(text: String): List<StudyScheduleBlock> {
        val linePattern = Regex("""^\s*(\d{1,2}:\d{2}\s*[-–—]\s*\d{1,2}:\d{2})\s*[｜|]\s*([^｜|]+)\s*[｜|]\s*(.+?)\s*$""")
        return text.lineSequence()
            .mapNotNull { rawLine ->
                val line = rawLine.trim().trimStart('-', '•', '*').trim()
                val match = linePattern.matchEntire(line) ?: return@mapNotNull null
                StudyScheduleBlock(
                    time = match.groupValues[1].replace(Regex("\\s+"), "").replace('–', '-').replace('—', '-'),
                    title = match.groupValues[2].trim().trim('：', ':'),
                    detail = match.groupValues[3].trim(),
                )
            }
            .filter { it.time.length in 10..13 && it.title.isNotBlank() && it.detail.isNotBlank() }
            .take(12)
            .toList()
    }

    fun daysLeft(date: LocalDate = LocalDate.now()): Long =
        ChronoUnit.DAYS.between(date, examDate).coerceAtLeast(0)

    private fun daily(date: String, title: String, vararg tasks: String): DailyStudyPlan {
        val normalizedTasks = tasks
            .map(::normalizeTaskTitle)
            .let { normalized ->
                if (normalized.any { it.contains("不背单词") }) normalized else normalized + dailyVocabularyTaskTitle
            }
        return DailyStudyPlan(
            date = LocalDate.parse(date),
            title = title,
            tasks = normalizedTasks.map { task ->
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
    }

    private fun restDaily(date: String, title: String): DailyStudyPlan =
        DailyStudyPlan(
            date = LocalDate.parse(date),
            title = title,
            tasks = emptyList(),
        )

    private fun isRestDay(date: LocalDate): Boolean = date == currentWeekRestDate

    private fun planTask(title: String, kind: StudyPlanTaskKind): StudyPlanTask =
        StudyPlanTask(title = title, kind = kind)

    private fun normalizeTaskTitle(task: String): String =
        task.replace(
            Regex("""不背单词(?:复习)?\s*\d+\s*个（\d+\s*组）"""),
            dailyVocabularyTaskTitle,
        ).withPreferredCourseProvider()

    private fun String.withPreferredCourseProvider(): String {
        if (!hasAny("听课", "看课") || contains("众合法硕")) return this
        val subject = when {
            contains("刑法") -> "刑法"
            contains("民法") -> "民法"
            contains("宪法") -> "宪法"
            contains("法制史") -> "法制史"
            contains("法理") -> "法理"
            else -> return this
        }
        return replace("听课", "听众合法硕${subject}课程")
            .replace("看课", "听众合法硕${subject}课程")
    }

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

@Serializable
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
