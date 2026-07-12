package me.rerere.rikkahub.data.study

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable

object ExamStudyPlan {
    // 2027 研考正式日期尚未公布，先按更早的周末做保守冲刺锚点；公告发布后必须更新。
    val examDate: LocalDate = LocalDate.of(2026, 12, 19)
    const val examDateIsOfficial: Boolean = false
    const val examDateNotice: String = "按 2026-12-19 保守规划，正式考试日期以教育部公告为准"
    const val vocabularyBacklog: Int = 1550
    const val dailyVocabularyTarget: Int = 120
    const val dailyVocabularyGroups: Int = 6
    const val chapterPracticeQuestionsPerSet: Int = 40
    const val professionalExamMinutes: Int = 180
    const val professionalPaperScore: Int = 150
    const val professionalSubjectiveScore: Int = 90
    const val scuSafeTargetScore: Int = 385
    const val politicsTargetScore: Int = 70
    const val englishTargetScore: Int = 75
    const val professionalFoundationTargetScore: Int = 120
    const val professionalComprehensiveTargetScore: Int = 120
    const val weeklyRestDayCount: Int = 1
    val currentWeekRestDate: LocalDate = LocalDate.of(2026, 7, 19)
    val vocabularyDailyOptions: List<Int> = listOf(dailyVocabularyTarget)
    const val dailyVocabularyTaskTitle: String = "不背单词 120 个（6 组）"
    const val dailyEnglishCoreMinutes: Int = 30
    const val dailyEnglishTotalMinutes: Int = 55
    const val recoveryStudyMinutes: Int = 90
    const val baselineStudyMinutes: Int = 180
    const val growthStudyMinutes: Int = 210
    const val expansionStudyMinutes: Int = 240
    const val dailyLongSentenceTaskTitle: String =
        "长难句 1 句：从颉斌斌 66 句/田静《句句真研》/真题阅读中选 1 句，按唐迟方法论或田静语法拆主干、圈修饰/从句、复述译文"
    const val dailyEnglishReadingTaskTitle: String =
        "英语一真题阅读训练 30-40 分钟：用正版真题完成 1 篇（首次不强制限时），逐题标出题干定位、原文证据和错因，并摘 1 句长难句"
    const val dailyEnglishReviewTaskTitle: String =
        "英语周复盘 30 分钟：回看本周长难句/阅读错因，闭卷复述 3 个语法或定位问题，补 1 句译文"
    const val politicsStartDateText: String = "2026-09-15"
    const val legalTheoryChapterCount: Int = 13
    const val criminalLawChapterCount: Int = 25
    const val civilLawChapterCount: Int = 21
    const val constitutionalLawChapterCount: Int = 7
    const val legalHistoryChapterCount: Int = 7
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
        - 用户容易畏难；当她想逃避，或身体不舒服导致“不想学习”时，不要立刻判断为完全不能学。非明显生病或疼痛加重时，先安排不背单词 20 个（1 组）作为低阻力启动，因为她有时学习一点反而会清醒。
        - 专业课优先级最高。法理学已经听课和做题一遍，现在重点是背诵、框架、错题回看，不要重新从零听大量课程。
        - 唯一目标院校是四川大学法律硕士（非法学）。近三年学校基本线存在波动：2023 年法律 345、2024 年法律（非法学）350、2025 年法律 335，且学校明确学院可二次提高。不能按刚过线设计；备考安全目标定为 $scuSafeTargetScore 分：政治 $politicsTargetScore、英语一 $englishTargetScore、专业基础 $professionalFoundationTargetScore、专业综合 $professionalComprehensiveTargetScore。该分数是计划目标，不是官方预测线。
        - 2026 官方招生目录显示法律（非法学）计划 68 人，其中非全日制 20 人、拟推免 24 人，复试科目为法理学。目录人数会随推免完成和生源调整，因此不能把计划人数直接等同于统考全日制最终录取人数；初试必须尽量建立 30 分以上安全垫。
        - 刑法学、民法、宪法学、法制史需要从一轮听课和基础题开始。专业课听课默认使用众合法硕课程；安排时写成“听众合法硕刑法课程”“听众合法硕民法课程”等。
        - 专业课总课时校准：$professionalCourseHoursSummary。四科合计约 297 小时 16 分钟，不能按章节数或页数乐观估时。
        - 章节账本必须写清：法理学 $legalTheoryChapterCount 章、刑法学 $criminalLawChapterCount 章、民法 $civilLawChapterCount 章、宪法学 $constitutionalLawChapterCount 章、法制史 $legalHistoryChapterCount 章。月计划、周计划、日计划必须能看出章节范围、听课估时、背诵轮次和题目/真题入口。
        - 2026-07-08 考试结构校准：考试分析已到手，后续不再把“确认考试分析”当学习任务；考试分析是定义、构成要件、简答/分析/案例/论述规范表述的主锚点。
        - 法硕非法学两张专业课卷每张卷 180 分钟、150 分。专业基础课：民法 75 分、刑法 75 分；题型为单选 40 个 40 分、多选 10 个 20 分、简答 4 题 40 分、分析题 2 个 20 分、案例分析 2 个 30 分，主观题 90 分。综合课：法理 60 分、宪法 50 分、法制史 40 分；题型为单选 40 个 40 分、多选 10 个 20 分、简答 3 题 30 分、分析 3 题 30 分、论述 2 题 30 分，主观题 90 分。
        - 题型权重决定计划节奏：选择题 60 分需要每章标选择题陷阱、易混点和错因；主观题 90 分不能等到 10 月才开始，7-8 月每个闭环章节都要从考试分析抽出定义、构成要件、答题骨架和 1-2 句规范表述，但暂不提前做完整 3 小时套卷。
        - 用户会严格按计划执行，计划不能留下“到时候再说”的空档。背诵是难点，必须尽早开始：7 月起每天有最小背诵输出，8 月背诵成为日常主线，9 月中后即使一轮有尾巴也要启动二轮，11 月进入三轮限时输出。
        - 最近七天用户平均每天约 2 小时，但昨天已完成 3 小时；3 小时现在是健康状态的最低基线，不是上限。不能因为过去的恢复日记录继续把正常日压在 2 小时以内。
        - 负荷参考：真正不适时恢复约 $recoveryStudyMinutes 分钟；本周健康日约 210 分钟；7 月 20 日周约 270 分钟；7 月 27 日周约 330 分钟；8 月 3 日周约 360 分钟；8 月 10 日周约 390 分钟；8 月 17 日起约 420 分钟，9 月后再视完成率升到 7-8 小时。每周日休息，任何一周完成率或睡眠崩掉就停在原档，不硬升。
        - 这些是有效学习分钟，不含吃饭、广播体操和休息；播放器四倍速但实际按约二倍速计账，必须把暂停、笔记、理解、题目和背诵算进去。
        - 日总量是所有科目合计，不是专业课之外再叠加英语和背诵。英语阅读必须占预算；政治启动后也必须从当天预算中切分，不能无限叠加。
        - 刑法学按 $criminalLawChapterCount 章、教材约 $criminalLawTextbookPages 页估算；第 1 章 $criminalLawChapterOnePages 页但课程约 6 小时。30-50 分钟只能算一个入口切片，不算学完一章。
        - 用户可以四倍速播放课程，但按暂停、笔记和理解后的约二倍速计账。倍速只压缩“听课输入时间”，不能压缩课后消化、做题、错题、正式框架图和背诵痕迹。9 月 14 日只是冲刺假设，9 月 30 日是更可靠的新课压力线；不能靠跳章、挤掉复线或熬夜维持旧日期。
        - 排刑法、民法新章时，默认先拆成 60-90 分钟听课切片；关键词/不懂点/小框架是课程后的自动随手整理，不单独占任务。整章听完后，再安排章节题、错题和第一轮背诵；需要正式框架图时另列明确任务。恢复日可缩成 30-60 分钟入口切片，但不要把一章压成一个短任务。
        - 用户明确要求“一本学完再学下一本”。这里的“学完一本”指这本书的新课一轮完成：听课账本完整、每章有关键词/小框架、闭环章有章节题和错题主错因、第一轮背诵账本启动；不是等三轮背诵全部结束。新课按整本书顺序推进：刑法主线期间不把民法作为新开副线；刑法没有完成新课一轮前不启动民法，民法没有完成新课一轮前不启动宪法，宪法没有完成新课一轮前不启动法制史；法理背诵、刑法/民法错题和背诵可以作为复线回炉。
        - 用户不喜欢把一章学完后的做题、错题收集和正式框架图拆成很多天零散塞一点。课程结束后的随手整理默认自动完成，不单列；同一组题目和正式框架图优先一次性或半天内集中完成；确实要拆时，只拆成 2 个大块，不要拆成 4-5 个碎片散落到每天。
        - 2026-07-09 继续校准：主线和复线可以同时存在，但复线只能是背诵、错题、框架回炉或英语保底，不能再开另一门新书。7 月组合应是“刑法新学/闭环主线 + 法理背诵复线”，不要再把民法、宪法、法制史塞成新学副线。
        - 这一章没有结束前不要单独安排题目，也不要机械写“做 5 道题”。老师课上讲的例题算在听课里；课后只留下关键词、不懂点或小框架。等整章课程闭环后，再集中做章节题、错题整理和第一轮背诵；未闭环的新章只能做已听关键词复述，不算第一轮。
        - 专业课章节题一张按约 $chapterPracticeQuestionsPerSet 道估算。10-15 道只能算启动、回看或恢复日小块，不能写成完整章节题闭环；正常闭环要在 2-3 天内补完整张并整理错因。
        - 2026-07-11 最新进度：刑法第 1 章课程、题目、复习和框架图已经全部完成，第 1 章正式闭环；刑法第 2 章课程已经听完。第 2 章题目单独成组，第 3-7 章共用一组合并题，必须先按顺序听完第 3-7 章，再集中做题、整理错因和正式连接框架；第 7 章课程结束前不得提前刷这组题。
        - 2026-07-12 最新进度校准：刑法第 3、4 章课程已听完，今天有效学习约 3 小时；课程后的关键词/卡点整理已随手完成，但正式框架图尚未画。课程结束后的随手整理默认视为自动动作，不再单独占一个计划任务；只有“正式框架图/思维导图”才单独列任务并明确标注。因前一晚熬夜，法理正式背诵从今天起顺延，先恢复睡眠，不用今晚补回。
        - 2026-07-11 法理欠账：法理第 1 章正式闭卷背诵仍未完成，从 7 月 12 日先补第 1 章，再依次顺延后续章节，不能按旧日历跳章。
        - 2026-07-13 重新校准：用户健康状态已恢复，刑法推进到第 5 章，但第 2 章题目和正式框架图未完成，法理正式背诵尚未开始；本周先用 3 小时基线集中补第 2 章闭环、法理第 1 章背诵和英语阅读，再继续第 5-7 章课程。
        - 法理背诵必须按第 1-13 章连续推进。前置章节未完成或未验收时，不得为了追原计划数字跳到后续章节；先把欠账拆到 2-3 天完成，再从下一章继续。缓冲日只能回炉已经接触过的章节，不能跨章开新入口。
        - 当用户明确说某章已经看完，日计划必须把该章切换到正式闭环阶段：做章节题、整理错题；需要画图时明确写“正式框架图/思维导图”，再做第一轮关键词背诵。不要把课后随手整理伪装成任务，也不要把旧的听课任务残留在后续日期里。
        - 2026-07-07 进度压力校准：用户觉得 7 月任务偏少，担心后面每周休息、生病或突发情况导致压力堆积。7 月要在保留每周 1 天休息/缓冲的前提下，正常学习周额外前移 2-3 个专业课主块，优先加在刑法、民法连续听课和闭环上；不要用取消休息日、压缩睡眠或饭后硬扛来换进度。加量必须逐日、逐周递进：先小幅加，再中等加，再稳定加，不允许突然把某一天塞满。
        - 法硕专业课必须按倍数估算：听课一轮只是第一层成本，后面还有做题、错题总结、正式框架图和三轮背诵。健康基线日要同时推进主课和一个闭环，不能只听课；真正恢复日才只保一个入口。
        - 法理学不重听大课，但不是只看目录；闭卷背目录树和关键词算第一轮背诵，单纯看框架不算。7 月不能长期停留在“看错题、搞目录”的前置动作，必须安排 30-40 分钟正式背诵：合上书口头复述目录树和关键词，卡住只看 10 秒提示，再继续用自己的话说出来。后面还要二轮规范表述、三轮限时输出。
        - 专业课高分验收不能只看“听完几章”：每个闭环章节必须留下课程分钟、章节题完成量、主错因、正式框架图、第一轮关键词背诵和 1-2 句规范表述；9 月开始每周至少 1 次主观题闭卷输出，10 月每周至少 2 次，11 月进入答题纸和套卷。
        - 政治不提前挤占 7-8 月专业课，但从 $politicsStartDateText 起正式启动：第一阶段每周 3 次，每次 30-40 分钟，按“徐涛强化课/核心考案理解 20-25 分钟 + 肖秀荣 1000 题或同等选择题 10-15 道 + 错因 1 条”执行；10 月增加到每周 4-5 次并开始章节回顾，11 月进入肖八选择题和主观题结构，12 月以肖四主观题和整卷节奏为主。
        - 每章尽量拆成听课/看课、章节题、错题整理、第一轮背诵、正式框架图，不要只写“学习专业课”；课后关键词/卡点/小框架包含在听课里，不占一个拆分项。
        - 不背单词总复习池约 $vocabularyBacklog 个，但每天固定安排 $dailyVocabularyTaskTitle，不把总池一次性塞进当天。
        - 2026-07-13 起正常学习日英语固定为“$dailyVocabularyTaskTitle + 1 个约 $dailyEnglishCoreMinutes 分钟的主训练块”，合计约 $dailyEnglishTotalMinutes 分钟。主训练必须明确写成长难句、真题阅读、翻译、完形/新题型或作文之一；月计划、周计划写了英语阶段目标，日计划就必须有对应动作，不能连续只剩单词。
        - 非完整休息日可以把单词往前放：上午或饭后困劲后先背 20 个（1 组）启动，再进入专业课主块；这不是降低任务，而是给进入状态一个台阶。
        - 长难句不能只写“长难句 1 句”。必须写清材料来源、老师/体系和当天动作。默认：先用唐迟长难句方法论或田静语法/长难句建立拆句动作；材料从颉斌斌 66 句、田静《句句真研》、已购买课程配套讲义或真题阅读句子中选 1 句；只做主干、修饰成分/从句、译文复述和 1 条不会的语法点，不做无边界抄笔记。
        - 长难句资料获取优先正版书、官方课程/公开试听、购买课程后的配套电子讲义和图书官方配套资源；不要安排小红书网盘倒卖、私下转账资料群或盗版 PDF。
        - 当前通常 02:00 睡、10:00 起，这个作息需要纠正而不是纵容。本周先要求 23:30 前结束学习、01:30 前入睡、09:30 起床；稳定 3-4 天后调整到 01:00 前睡、09:00 起，再向 00:30-08:30 靠拢。日计划保留广播体操和睡前降噪，不能用熬夜补任务。
        - 具体学习时段由用户自己控制，计划的硬指标是任务、有效分钟和验收痕迹，不因为某个钟点没开始就判定失败；但 23:30 学习收尾和逐步提前睡觉属于硬约束。
        - 当前没有固定工作或学校事务占用，娱乐由学习抽卡奖励触发；不能用“可能有别的事”预先压缩健康日学习量，也不预留无条件的长时间娱乐块。
        - 每天保留轻量活动，但不强制出门。目标是补充精力、缓解疲劳，并给僵硬的身体“上机油”；优先少量多次，天气热时用寝室内活动替代散步。
        - 用户更喜欢配音乐运动：需要安排轻运动时，第八套广播体操按约 70% 的优先权重选择；手势舞、简单舞蹈、原地踏步、开窗站立、转腰、转肩膀、脚踝/膝盖活动等共享其余约 30%。散步和羽毛球只作为天气、朋友和身体状态合适时的加餐，身体不适时可覆盖上述权重。
        - 用户吃饱后容易犯困，犯困后会明显不想学习。饭后不要立刻安排高压背诵、难题或长时间听课，优先安排 10-20 分钟音乐轻活动、散步、洗漱、整理桌面、轻量复盘或低阻力任务，等困劲过去后再进入深度学习。
        - 点击生成今日计划时，计划必须从当前时间开始重新编排；如果当前已经是下午且上午待办未完成，不要从早上补排，也不要输出早于当前时间的时间块。所有未完成待办必须从现在起压缩、改序或拆成小块排入剩余时间；已完成待办不要重复安排。
        - 7 月初的肠胃不适、头晕和病后恢复属于历史记录，当前已经恢复，不能继续据此自动降量。只有用户当天明确说生病、头晕、疼痛或几乎没睡，才启用恢复档。
        - 普通畏难、想玩或不想开始不等于身体恢复日；先用单词 20 个或广播体操启动，然后回到至少 3 小时健康基线。
        - 每周固定保留 $weeklyRestDayCount 天休息/缓冲日。2026-07-04 本周用户选择周六作为完整休息日，当天不安排任何学习任务，不安排单词、听课、背诵或题目；原本任务从 2026-07-05 起按 2-3 天轻量分摊，不惩罚式补课，同时仍要保证 12 月前完成看课、背诵和做题主线。
        - 2026-07-07 用户来例假身体不舒服，要求把本周休息日调整到今天。当天按完整休息日处理，不安排单词、长难句、听课、背诵、做题、错题、框架或复盘；原本刑法第 1 章闭环从 2026-07-08 起轻量顺延，不惩罚式堆叠；民法不作为刑法阶段的新开副线。
        - 用户可以手动新增学校任务，例如“写论文 50分钟”。这类任务必须被安排进今日计划。
        - 输出日程要先主线、再题目/回炉、再英语、再背诵收尾；中间要有饭点和休息。
    """.trimIndent()

    val subjectExecutionReference = """
        三科具体执行入口：
        - 专业课材料主线：考试分析已到手，考试分析/大纲作定义、构成要件、背诵和主观题规范表述锚点，众合法硕课程作听课主线，文运/众合章节题作章节闭环，历年真题作后期题感和主观题表达校准，自己的错题清单作回炉入口。
        - 专业课每章流程：不单独安排预习目录；老师讲课时会带框架，听众合法硕课程 60-90 分钟切片即可。课程结束后的关键词/卡点整理是听课后的自动动作，不单独拆成一个任务；整章听完后再做章节题（一张约 $chapterPracticeQuestionsPerSet 道，可拆成 2-3 次）；错题只标 1 个主错因；需要正式画框架图/思维导图时，必须另列并明确写“正式框架图”，最后回到考试分析标出选择题陷阱、简答/分析/案例/论述答题骨架、第一轮关键词背诵和 1-2 句规范表述。
        - 英语材料主线：不背单词固定 120 个；长难句用田静《句句真研》/颉斌斌 66 句/真题阅读句子三选一；阅读用历年真题解析书或正版真题册；作文用石雷鹏或王江涛择一；翻译可用唐静或已购课程；小三门优先真题解析和短方法课，不买大量新资料。
        - 英语题型流程：7 月每周至少 3 次真题阅读小段/精读、2 次长难句、1 次周复盘，不能因为之前漏排阅读而继续只背单词；8 月每周 3 篇阅读精读 + 2 次长难句/翻译；9 月阅读每周 3-4 篇并加入完形/新题型/翻译小块；10 月作文模板和分题型计时；11 月每周至少 1 套整卷；12 月每周至少 1 次完整限时并压缩错题。
        - 英语一整卷计时参考：作文 45-50 分钟，阅读 70-75 分钟，翻译 20 分钟，新题型 15-20 分钟，完形 10-15 分钟，预留涂卡和检查；英语二按实际题型分值微调。
        - 政治拆成听课理解、选择题刷题、错题回炉、真题/模拟题限时和主观题背诵五类。9 月 15 日起每周 3 次“强化课/核心考案 20-25 分钟 + 1000 题 10-15 道”；10 月每周 4-5 次并完成章节错因表；11 月肖八选择题与主观题结构并行；12 月肖四主观题背诵和整卷节奏定型。材料只选徐涛/核心考案、肖秀荣 1000 题、腿姐背诵手册或知识点提要中的一套主线，不反复换老师。
        - 政治整卷 180 分钟训练：单选 15-20 分钟，多选 35-45 分钟，分析题 90-100 分钟，最后留 10 分钟检查和补答题卡。
    """.trimIndent()

    val monthlyPlans = listOf(
        MonthlyStudyPlan("2026-07", "刑法主线推进、法理背诵重启、英语基础提分", listOf(
            "专业课主线：先集中完成刑法第 2 章独立题组和正式框架图，再继续第 5 章并听完第 6-7 章；第 3-7 章合并题等课程全部结束后集中完成，之后按实际课程分钟顺序推进",
            "专业课背诵：法理第 1 章从 7 月 13 日起每天安排 30-40 分钟正式闭卷背诵，完成目录树、关键词和 1-2 句规范表述后再进入第 2 章；不把看过目录当作背会",
            "英语：每天 120 个单词复习 + 30-40 分钟主训练；本周至少 3 次真题阅读小段/精读、2 次长难句、1 次周复盘，补上之前漏排的阅读",
            "政治：7 月不正式启动，不挤占刑法闭环和英语；只确认已有材料，不把找课、买资料列成学习任务",
            "日负荷：健康日以 180 分钟为最低基线，本周增长到 210 分钟的日子安排在课程收口日；真正不适才启用恢复档，每周保留 1 天完整休息",
        )),
        MonthlyStudyPlan("2026-08", "刑法收口后启动民法", listOf(
            "专业课新课：8 月 1 日承接刑法阶段复盘，刑法未完成就继续唯一主线；完成后次日才启动民法。健康日按 330-420 分钟总量爬坡，其中专业课主块和续段合计至少 180 分钟，并轮换正式闭环，8 月 20 日和 31 日按真实课时重算",
            "专业课背诵：法理第 1-6 章完成第一轮目录树、关键词和规范表述；刑法已闭环章节每周至少 2 次抽背，背不出就回到考试分析，不用熟悉感冒充掌握",
            "英语：120 个单词不断线；每周 3 篇真题阅读精读、2 次长难句/翻译、1 次错因复盘。阅读必须留下题干定位、原文证据、错因和摘句",
            "政治：仍不进入每日主线；月底只确认 9 月启动用的一套强化课/讲义和一本选择题册，不重复购课",
        )),
        MonthlyStudyPlan("2026-09", "新课收口与背诵输出并行", listOf(
            "9 月 1-14 日：按刑法→民法→宪法→法制史顺序继续当前唯一新课主线；9 月 14 日做冲刺假设复盘，不靠跳章伪造完成",
            "9 月 15-30 日：力争收口常规新课；法理第 7-13 章完成第一轮并启动较早章节二轮，刑民题目和错题回炉，综合课框架串联；每周至少 2 次主观题闭卷输出并订正",
            "英语：每周 3-4 篇真题阅读，其中至少 2 篇单篇限时；完形/新题型/翻译各按周轮换 1 个小块，单词继续滚动，周末汇总阅读错因",
            "政治：9 月 15 日起每周 3 次，每次 30-40 分钟；完成强化课/核心考案小节后立刻做 10-15 道选择题并写 1 条错因，不只听课",
            "分数验收：9 月末做两张专业课分科诊断卷，先达到每张 100-105 分；低于 100 分必须按选择题知识漏洞、主观题表述遗漏分别回炉",
        )),
        MonthlyStudyPlan("2026-10", "第二轮规范表述与真题化", listOf(
            "五科第二轮规范表述全覆盖，按遗忘率滚动复习，不开常规新课",
            "刑法、民法历年真题分科训练；综合课主观题训练；每周至少 2 次闭卷输出并订正",
            "英语：每周 3 篇单篇限时阅读并精复盘；完形、新题型、翻译各轮换 1 次；作文每周 1 次小作文 + 1 次大作文素材/段落输出，月底形成稳定模板而非照抄范文",
            "政治：每周 4-5 次强化课/知识点回顾 + 1000 题，建立章节错因表；月底选择题正确率和薄弱章节必须可统计",
            "分数验收：10 月末两张专业课限时卷稳定到每张 110-115 分，主观题必须按采点和规范表述逐项复盘",
        )),
        MonthlyStudyPlan("2026-11", "第三轮、高频点与套卷", listOf(
            "五科第三轮：高频点、易混点、反复错点和规范表述限时回忆",
            "专业课套卷与答题纸训练，复盘时间不得少于做题时间；不新增课程和大而散的资料",
            "英语：每周至少 1 套 180 分钟真题/模拟整卷，次日按阅读证据、翻译表达、作文结构和时间分配复盘；每周输出 1 小作文 + 1 大作文",
            "政治：肖八选择题逐套限时并复盘，主观题先练材料对应和答题结构；选择题错因回到章节，不只背答案",
            "分数验收：11 月末专业基础、专业综合分别冲到 118-122 分；四科整套总分至少进入 375 分区间，差额优先从专业课错题和英语阅读找回",
        )),
        MonthlyStudyPlan("2026-12", "模拟保温与考场校准", listOf(
            "只做全真模拟、最易丢分点、错题压缩版和高频表达保温，不新增资料或课程",
            "校准答题顺序、卷面、时间和作息；考前一周降量但保持每日抽背手感",
            "英语：每周至少 1 套完整限时，阅读错因和作文模板压缩到最后清单；考前一周只做熟题保温、作文默写和时间校准",
            "政治：肖四主观题按题目—材料—采点背诵，选择题保持手感；至少做 1 次 180 分钟整卷节奏校准，睡眠饮食稳定",
            "分数验收：全真模拟围绕 385 分安全目标校准，专业基础和专业综合各守住 120 分左右；单次波动只修具体漏洞，不临时更换资料体系",
        )),
    )

    val julyWeeks = listOf(
        WeeklyStudyPlan("2026-07-w1", "历史进度确认", "2026-07-02 至 2026-07-07", listOf("保留已完成记录，不重复安排")),
        WeeklyStudyPlan("2026-07-w2", "历史进度：刑法 1-4 章与身体恢复", "2026-07-08 至 2026-07-12", listOf(
            "刑法第 1 章已正式闭环；第 2 章课程完成；第 3-4 章课程完成，正式框架图待补；保留真实完成记录，不重复听课",
            "法理第 1 章正式闭卷背诵仍欠账，因熬夜没有当晚硬补，从 7 月 13 日起拆块回收",
        )),
        WeeklyStudyPlan("2026-07-w3", "刑法 2-7 章闭环 + 法理 1 背诵 + 英语双线", "2026-07-13 至 2026-07-19", listOf(
            "负荷：6 个学习日，日均约 3.5 小时，周目标约 21 小时；7 月 19 日完整休息，不把休息日补成欠账",
            "刑法：7 月 13 日先集中完成第 2 章独立题组和正式框架图；随后按顺序完成第 5-7 章课程（当前到第 5 章）；第 3-7 章合并题等课程全部结束后集中完成，条件不满足就先补课程",
            "法理：第 1 章每天 30-40 分钟正式闭卷背诵，至少完成 3 次口头复述和 1 次闭卷验收；不跳到第 2 章，不把看目录当背会",
            "英语：6 个学习日每天 120 个单词；至少 3 次真题阅读小段/精读、2 次长难句、1 次周复盘；每篇阅读留下题干定位、原文证据和错因痕迹",
            "政治：本周不启动；7 月 19 日为完整休息日，单词、专业课和复盘全部清零",
            "周验收：第 2 章题组和框架完成；第 5-7 章课程实际分钟可核对；法理第 1 章至少留下 3 次正式背诵痕迹；英语至少留下 6 个主训练痕迹",
        )),
        WeeklyStudyPlan("2026-07-w4", "刑法第 8 章起顺序推进 + 法理 2 + 英语阅读起步", "2026-07-20 至 2026-07-26", listOf(
            "负荷：6 个学习日，日均约 4.5 小时，周目标约 27 小时；若完成率低于 80%，下一周不加量",
            "刑法：先验收第 2 章和第 3-7 章组合闭环，再从第 8 章起按课程账本每天推进 90-110 分钟；本周至少留下 3 个 30-40 分钟章节闭环块，不跨章跳跃",
            "法理：第 1 章验收通过后启动第 2 章第一轮，安排 3 个 25-30 分钟闭卷块；未通过就继续第 1 章，不按日期跳章",
            "英语：每天 120 个单词；3 次真题阅读精读、2 次长难句/翻译，周六完成本月第一篇不计时完整阅读并写题干定位、证据和错因",
            "政治：仍不正式启动；7 月 26 日为完整休息日",
        )),
        WeeklyStudyPlan("2026-07-w5", "刑法阶段验收 + 法理连续背诵 + 英语月复盘", "2026-07-27 至 2026-07-31", listOf(
            "负荷：按 5-5.5 小时/日进入增长档；7 月 31 日用实际听课分钟和闭环痕迹重算，不用章节数自我欺骗",
            "刑法：继续当前未完成章节课程主块；月底按课程分钟、章节题、主错因、正式框架图和第一轮关键词逐项验收并重算完成日",
            "法理：完成当前连续章节的第一轮闭卷验收，明确 8 月从哪一章继续；不把未背章节写成已完成",
            "英语：每天 120 个单词，完成 2 次长难句、1 次阅读小段、1 篇不计时完整阅读复盘；统计词汇剩余池和阅读/语法错因",
            "政治：只锁定 9 月使用的一套课程/讲义和一本题册，不安排听课或刷题",
        )),
    )

    val postJulyWeeks = listOf(
        WeeklyStudyPlan("2026-08-w1", "刑法收口与民法条件启动", "2026-08-01 至 2026-08-07", listOf(
            "负荷：约 5.5-6 小时/日，周日休息；课程输入、闭环、英语和法理都从总预算中分配",
            "专业课：承接 7 月复盘，刑法未完成就继续课程/题组/框架；完成后次日才启动民法。法理安排 3 次 25-30 分钟闭卷背诵",
            "英语：120 个单词每日不断线，3 篇真题阅读精读、2 次长难句/翻译、1 次错因复盘",
            "政治：不启动；周日完整休息",
        )),
        WeeklyStudyPlan("2026-08-w2", "唯一新课主线连续推进", "2026-08-08 至 2026-08-14", listOf(
            "负荷：约 6-6.5 小时/日，周目标约 36-39 小时；达到 80% 完成率才进入下一档",
            "专业课：继续当前唯一科目的众合法硕课程，至少 4 个 90-120 分钟主块和 2 个正式闭环块；已结束章节留下题目、主错因、正式框架图和关键词",
            "背诵：法理连续章节 3 次闭卷，刑法已学章节 2 次抽背；英语完成 3 篇阅读 + 2 次长难句/翻译",
            "政治：不启动；周日完整休息",
        )),
        WeeklyStudyPlan("2026-08-w3", "8 月 20 日阶段验收", "2026-08-15 至 2026-08-20", listOf(
            "负荷：约 6.5 小时/日；8 月 20 日必须按真实课程分钟、题组和框架重算，不达标就顺延压力线",
            "专业课：按已听分钟、题组、主错因、正式框架图和背诵痕迹验收当前科目，重算完成日；至少做 1 次主观题答题骨架输出",
            "英语：2-3 篇阅读精读、2 次长难句/翻译，统计定位/语法错因；法理和已学刑法抽背不断线",
            "政治：不启动，正常日总量连续稳定后仅增加 20-30 分钟，不取消休息日",
        )),
        WeeklyStudyPlan("2026-08-w4", "8 月收口与 9 月重排", "2026-08-21 至 2026-08-31", listOf(
            "负荷：约 7 小时/日，周日休息；9 月 14 日只作冲刺假设，9 月 30 日作为更可靠压力线",
            "专业课：继续唯一新课科目，完成后按刑法→民法→宪法→法制史切换；8 月 31 日写清真实章节和剩余课时",
            "背诵：法理第 1-6 章第一轮验收，刑法已闭环章抽背 2 次；英语每周 3 篇阅读、2 次长难句/翻译和 1 次周复盘",
            "政治：只确认 9 月 15 日使用的一套主课和一本选择题册，不把找资料算学习",
        )),
        WeeklyStudyPlan("2026-09-w1", "顺序新课推进与已学回炉", "2026-09-01 至 2026-09-07", listOf(
            "专业课：继续唯一新课主线，至少 4 个课程主块、2 个闭环块、1 次主观题闭卷输出；法理第一轮继续顺序推进",
            "英语：3 篇阅读，其中 1 篇单篇限时；长难句/翻译 2 次，小三门 1 次入门；政治暂不启动",
        )),
        WeeklyStudyPlan("2026-09-w2", "9 月 14 日冲刺假设复盘", "2026-09-08 至 2026-09-14", listOf(
            "专业课：按真实课时核对全部新课完成度，完成 2 次主观题闭卷输出；未完成部分按分值和课时重排，不跳章",
            "英语：3-4 篇阅读、1 次翻译或新题型、1 次错因复盘；政治材料就位但不提前刷题",
        )),
        WeeklyStudyPlan("2026-09-w3", "新课尾巴、背诵与政治启动", "2026-09-15 至 2026-09-21", listOf(
            "专业课：收口新课尾巴，法理第 7-13 章第一轮与较早章节回炉并行，刑民错题回炉，完成 2 次主观题闭卷输出",
            "英语：3 篇阅读（至少 1 篇限时）+ 完形/新题型/翻译 2 个轮换块；120 个单词继续滚动",
            "政治：完成 3 次 30-40 分钟启动块，每次强化课/核心考案 20-25 分钟 + 选择题 10-15 道 + 错因 1 条",
        )),
        WeeklyStudyPlan("2026-09-w4", "常规新课收口与真题锚定", "2026-09-22 至 2026-09-30", listOf(
            "专业课：力争收口常规新课，分科真题定位高频点，完成 2 次主观题闭卷输出；月底两张诊断卷目标 100-105",
            "英语：3-4 篇阅读、2 个小三门/翻译块和 1 次周复盘；政治完成 3 次课题绑定块并汇总第一张错因表",
        )),
        WeeklyStudyPlan("2026-10-w1", "第二轮：刑法民法", "2026-10-01 至 2026-10-07", listOf(
            "专业课：刑民规范表述与分科真题，至少 2 次闭卷输出；英语 3 篇限时阅读 + 1 小作文 + 1 次翻译/新题型",
            "政治：4 次章节复习 + 1000 题，错题回到知识点；周日完整休息",
        )),
        WeeklyStudyPlan("2026-10-w2", "第二轮：法理宪法", "2026-10-08 至 2026-10-14", listOf(
            "专业课：法理宪法规范表述、主观题和刑民错题滚动；英语 3 篇限时阅读 + 1 大作文段落 + 1 次完形/翻译",
            "政治：4-5 次课题绑定块，完成对应章节错因表",
        )),
        WeeklyStudyPlan("2026-10-w3", "第二轮：法制史与跨科串联", "2026-10-15 至 2026-10-21", listOf(
            "专业课：法制史时间线、制度比较、五科易混点串联，至少 2 次闭卷输出；英语继续阅读 3 篇并完成小作文/大作文各一块",
            "政治：4-5 次选择题和错因回炉，统计薄弱章节",
        )),
        WeeklyStudyPlan("2026-10-w4", "第二轮总验收", "2026-10-22 至 2026-10-31", listOf(
            "专业课：五科第二轮验收、真题复盘和薄弱点清单，限时卷目标 110-115；英语完成半套/分题型计时和作文模板验收",
            "政治：1000 题薄弱章二刷，形成 10 月选择题正确率和错因清单",
        )),
        WeeklyStudyPlan("2026-11-w1", "第三轮高频压缩", "2026-11-01 至 2026-11-07", listOf(
            "专业课：限时回忆高频点、易混点和反复错点；英语 1 套整卷 + 次日复盘 + 1 小 1 大作文；政治开始肖八选择题",
        )),
        WeeklyStudyPlan("2026-11-w2", "专业课套卷与答题纸", "2026-11-08 至 2026-11-14", listOf(
            "专业课：使用答题纸完成限时套卷并逐题复盘；英语 1 套整卷和作文输出；政治肖八选择题限时 + 主观题结构",
        )),
        WeeklyStudyPlan("2026-11-w3", "第三轮薄弱项回炉", "2026-11-15 至 2026-11-21", listOf(
            "专业课：按套卷暴露问题回到章节和规范表述；英语 1 套整卷、阅读错因压缩和作文默写；政治肖八错题回炉",
        )),
        WeeklyStudyPlan("2026-11-w4", "考场节奏定型", "2026-11-22 至 2026-11-30", listOf(
            "专业课：套卷、答题纸、卷面和时间定型；英语 1-2 套整卷并固定各题型时间；政治完成肖八复盘和主观题结构清单",
        )),
        WeeklyStudyPlan("2026-12-w1", "全真模拟与保温", "2026-12-01 至 2026-12-07", listOf(
            "专业课与英语各至少 1 次全真模拟，复盘后只补最易丢分点；政治肖四主观题第一轮和选择题保温",
        )),
        WeeklyStudyPlan("2026-12-w2", "错题压缩与作息校准", "2026-12-08 至 2026-12-14", listOf(
            "专业课压缩错题和高频规范表述；英语 1 套限时 + 作文默写；政治肖四主观题第二轮并做 1 次整卷节奏校准",
        )),
        WeeklyStudyPlan("2026-12-w3", "考前保温", "2026-12-15 至考前", listOf(
            "专业课轻量抽背和框架默写；英语熟题、作文和时间保温；政治肖四高频采点复述；不新增资料或课程",
        )),
    )

    val weeklyPlans: List<WeeklyStudyPlan> = julyWeeks + postJulyWeeks

    val dailyPlans: Map<LocalDate, DailyStudyPlan> = listOf(
        daily("2026-06-30", "启动准备日", "确认 7 月备考资料：文运/众合题册、考试分析、网课目录", dailyVocabularyTaskTitle, "散步 15 分钟，给大脑换气"),
        daily("2026-07-01", "启动调研日：不计正式进度", "登录和调研小红书法硕非法经验", "确认五科章节账本：法理 13、刑法 25、民法 21、宪法 7、法制史 7", "整理资料命名规则：科目-章节-题型-错因", "把 7 月计划校准到 7 月 2 日开始执行", "晚上只做收尾，不再开新章节"),
        daily("2026-07-02", "法理2 + 刑法1入口", "法理学第 2 章：错题回看、框架整理、第一轮背诵", "刑法学第 1 章：听众合法硕刑法课程 40-60 分钟，作为 6 小时课程的第 1 个切片，只抓犯罪论总入口", "刑法学第 1 章：听完写 3 个关键词和 1 个不懂点，不急着做大题", dailyLongSentenceTaskTitle, "睡前 10 分钟整理明日最小任务"),
        daily("2026-07-03", "病后慢启动：保底 + 可加码", "起床后先吃清淡、喝热水、洗漱，整理桌面 10 分钟；今天不追上午时长", "法理学第 2 章：看错题和框架 30-40 分钟，今天只恢复熟悉度，不算背诵轮次", "刑法学第 1 章：听众合法硕刑法课程 30-40 分钟，作为 6 小时课程的恢复日入口切片，听完写 3 个关键词和 1 个不懂点", "加码任务：如果晚上身体还可以，只补刑法第 1 章小框架，不单独安排题目；课上例题算在听课里", "不背单词 120 个（6 组）：拆成 3 次完成，每次 2 组，中间必须离桌休息", "饭后散步或慢走 15-20 分钟，等困劲过去再回桌面", "睡前 10 分钟写明天第一步，23:30 前结束学习入口"),
        restDaily("2026-07-04", "周六完整休息日：今天无学习任务"),
        daily("2026-07-05", "休息后重启：刑法1主块 + 法理小封口", "上午先慢启动，热水、清淡早饭、整理桌面 10 分钟后再开始", "刑法学第 1 章：听众合法硕刑法课程 75-90 分钟，作为休息后第 2 个切片，抓犯罪论总入口和构成要件", "刑法学第 1 章：听完只写 3-5 个关键词和 1 个不懂点，不单独安排题目；课上例题算在听课里", "法理学第 2 章：用考试分析目录树第一轮背诵 20 分钟，卡住只看关键词提示", dailyLongSentenceTaskTitle, "饭后开窗站 5 分钟 + 转肩转腰 5 分钟；晚上只做框架封口和提前睡"),
        daily("2026-07-06", "刑法1连续主块：先攒够章节手感", "刑法学第 1 章：听众合法硕刑法课程 75-90 分钟，作为第 3 个切片，听完补构成要件小框架", "刑法学第 1 章：回看前两个切片关键词 20 分钟，列出还没听完的部分；今天不新开民法大块，避免章节手感被打断", "法理学第 2 章：目录树第一轮背诵 15 分钟，只做封口", "英语：不背单词按 20 个一组做 6 组，错词只标记不抄写", "饭后放一首歌做手势舞或原地踏步 5-8 分钟，等困劲过去再回桌面"),
        restDaily("2026-07-07", "例假不适完整休息日：今天无学习任务"),
        daily("2026-07-08", "休息后承接：刑法1闭环启动 + 法理背诵复线", "顺延自 7 月 7 日：刑法第 1 章：做文运/众合章节题 15-20 道，只算启动量，不算完整闭环；每道错题只标 1 个主错因，不再补听课", "刑法第 1 章：回到考试分析标出犯罪论的选择题陷阱、构成要件、案例分析入口和简短答题骨架，再画一张方便背诵的犯罪论入口框架/思维导图", "不单独安排预习目录：老师讲课会带框架，民法暂不启动；今天只保留刑法主线和法理背诵复线", "法理第 1-3 章：正式背诵 30-40 分钟，先看错题卡点 5 分钟，再合上书口头复述目录树和关键词，卡住只看 10 秒提示继续说", dailyVocabularyTaskTitle, "饭后只做手势舞一首歌或整理桌面，不开新课；今天有效学习量至少凑够 2 小时"),
        daily("2026-07-09", "刑法1补闭环 + 法理未完成背诵", "刑法第 1 章未完成框架图：回到考试分析，把犯罪论入口、构成要件、选择题陷阱和案例分析入口画成一张方便背诵的思维导图，30-45 分钟内收口", "法理第 1-3 章未完成背诵：正式背诵 30-40 分钟，合上书口头复述目录树和关键词，卡住只看 10 秒提示继续说", "刑法第 1 章：回看 7 月 8 日做过的 15-20 道章节题，每道错题只补 1 个主错因，不新增民法任务", "刑法第 2 章：如果前三项完成且状态还可以，只听众合法硕刑法课程 45-60 分钟作为下一章入口，听完写 3 个关键词和 1 个不懂点", dailyLongSentenceTaskTitle, "饭后开窗站一会儿，配音乐转肩、转腰、脚踝绕圈 8-10 分钟；今天从 2-3 小时有效学习起步"),
        restDaily("2026-07-10", "头晕停学记录：当天任务全部未完成，欠账重新分配"),
        daily("2026-07-11", "头晕后恢复：欠账第1段", "7 月 10 日法理欠账：只背法理第 1 章 20-25 分钟，闭卷复述目录树和最卡关键词，不跨到后续章节", "7 月 10 日刑法第 1 章框架图欠账：先画犯罪论入口与构成要件主干 20-25 分钟，只完成上半张", "刑法学第 2 章状态允许再选做：不头晕时听众合法硕刑法课程 45-60 分钟，只留 3-5 个关键词和 1 个不懂点；仍不舒服就取消", "法理连续性检查：今天不安排第 2 章及以后内容，第 1 章完成后再继续", dailyLongSentenceTaskTitle, "饭后洗漱 + 手势舞一首歌，晚上不因欠账继续熬夜"),
        daily("2026-07-12", "今日完成：刑法3-4课程", "已完成：听众合法硕刑法第 3、4 章课程，今天有效学习约 3 小时", dailyVocabularyTaskTitle, "今晚按时收尾，饭后轻活动并优先修复睡眠；法理背诵和正式框架图都不补夜"),
        daily("2026-07-13", "3.5 小时起步：刑法2章闭环 + 法理1背诵 + 英语阅读", "刑法第 2 章独立题组：完成 75 分钟，给错题标一个主错因；今天至少留下题量记录", "刑法第 2 章正式框架图：主干、层级、构成要件、易混点和题目锚点，40 分钟", "法理第 1 章正式闭卷背诵 35 分钟：合上考试分析复述目录树、关键词和 1-2 句规范表述", dailyVocabularyTaskTitle, dailyEnglishReadingTaskTitle, "饭后广播体操 10-15 分钟；23:30 前收尾，今晚不熬夜"),
        daily("2026-07-14", "3.5 小时起步：刑法5课程 + 3-4框架 + 英语阅读", "听众合法硕刑法第 5 章课程 90 分钟；按实际分钟推进，第 5 章未结束不跳第 6 章", "刑法第 3-4 章正式框架图 35 分钟：补主干、易混点和题目锚点", "法理第 1 章正式闭卷背诵 30 分钟，卡住只看 10 秒提示再继续", dailyVocabularyTaskTitle, dailyEnglishReadingTaskTitle, "第八套广播体操半套，23:30 前收尾"),
        daily("2026-07-15", "3.5 小时起步：刑法5课程 + 2章框架收口 + 英语长难句", "听众合法硕刑法第 5 章课程 90 分钟，记录实际听课分钟和剩余小节", "刑法第 2 章正式框架图收口 35 分钟；若昨日未完成，今天优先补完，不另开新闭环", "法理第 1 章闭卷背诵 30 分钟，复述目录树并补 2 句规范表述", dailyVocabularyTaskTitle, dailyLongSentenceTaskTitle, "广播体操或轻走 15 分钟；不因未清题组熬夜"),
    ).associateBy { it.date } + listOf(
        daily("2026-07-16", "3.5 小时起步：刑法6-7课程 + 法理1背诵 + 英语阅读", "继续听众合法硕刑法第 6-7 章课程 90 分钟；未完成第 7 章时不做第 3-7 章合并题", "补齐刑法第 3-4 章正式框架图 30 分钟", "法理第 1 章闭卷回背 30 分钟，只处理上次卡点并写下 1 条规范表述", dailyVocabularyTaskTitle, dailyEnglishReadingTaskTitle, "广播体操或散步后按时休息"),
        daily("2026-07-17", "4 小时增长日：刑法3-7题组 + 连接框架 + 英语", "确认第 3-7 章课程全部结束后，完成合并题第一大块 80 分钟并标主错因；未结束就补第 7 章课程", "正式连接框架图完成主干、章节连接和易混点 40 分钟", "法理第 1 章闭卷回忆 30 分钟", dailyVocabularyTaskTitle, dailyLongSentenceTaskTitle, "不熬夜硬清，剩余题目顺延"),
        daily("2026-07-18", "3.5 小时起步：题组收口 + 法理验收 + 英语周复盘", "第 3-7 章合并题继续 75 分钟并整理主错因；若课程未收口则继续第 7 章，不提前刷合并题", "补齐第 3-7 章正式连接框架图 35 分钟", "法理第 1 章闭卷验收 30 分钟：口头复述后写下 3 个最不稳点", dailyVocabularyTaskTitle, dailyEnglishReviewTaskTitle, "广播体操 10 分钟，23:00 后不再学习"),
        restDaily("2026-07-19", "周日完整休息/缓冲日：不安排学习任务"),
        daily("2026-07-20", "刑法8章课程 + 法理2入口 + 英语长难句", "确认第 2 章和第 3-7 章闭环已完成后，听众合法硕刑法第 8 章课程 90-100 分钟；否则补最近闭环", "法理第 2 章第一轮闭卷背诵 25 分钟", dailyVocabularyTaskTitle, dailyLongSentenceTaskTitle, "晚上按时结束"),
        daily("2026-07-21", "刑法课程 + 最近闭环 + 英语阅读", "继续当前听众合法硕刑法课程 90 分钟，不跨章跳跃；课后随手整理包含在课程内", "从最近结束章节完成一项正式闭环：题目、主错因或框架图，30 分钟", "法理第 2 章闭卷回忆 25 分钟", dailyVocabularyTaskTitle, dailyEnglishReadingTaskTitle, "整理本周卡点清单 10 分钟即可", "天气和身体合适时轻打羽毛球 20-30 分钟，不作为硬任务"),
        daily("2026-07-22", "刑法课程 + 法理2背诵 + 英语长难句", "按当前课程账本继续听众合法硕刑法课程 90 分钟，记录实际听课分钟", "法理第 2 章正式背诵 25 分钟，完成目录树和关键词复述", "补一个最近闭环缺口：章节题、主错因或正式框架图，30 分钟", dailyVocabularyTaskTitle, dailyLongSentenceTaskTitle, "广播体操 10 分钟"),
        daily("2026-07-23", "刑法课程 + 闭环 + 英语阅读", "继续当前听众合法硕刑法课程 90 分钟，上一章未结束时不切换", "补一个最近正式闭环缺口：章节题、主错因或正式框架图三选一，30 分钟", "法理第 2 章闭卷回背 25 分钟", dailyVocabularyTaskTitle, dailyEnglishReadingTaskTitle, "饭后轻活动"),
        daily("2026-07-24", "刑法课程 + 法理2背诵 + 英语长难句", "继续当前刑法课程主块 90 分钟，按实际分钟记录", "完成一个最近章节闭环缺口，30 分钟", "法理第 2 章正式背诵 25 分钟，不熬夜", dailyVocabularyTaskTitle, dailyLongSentenceTaskTitle, "按时结束"),
        daily("2026-07-25", "刑法阶段复盘 + 英语完整阅读", "按课程账本补最近未完成的听课切片 90 分钟", "完成一个正式闭环验收：题组、主错因或框架图，30 分钟", "法理第 2 章闭卷验收 25 分钟", dailyVocabularyTaskTitle, "英语真题阅读完整篇（不计时）：题干定位、原文证据、错因分类和摘 1 句长难句，30-35 分钟", "广播体操或散步"),
        restDaily("2026-07-26", "周日完整休息/缓冲日：不安排学习任务"),
        daily("2026-07-27", "刑法课程 + 法理连续背诵 + 英语长难句", "补最近未完成刑法课程块 90 分钟，不跨章跳跃", "核对一个最近闭环缺口并补齐，30 分钟", "法理第 2 章回背 25 分钟", dailyVocabularyTaskTitle, dailyLongSentenceTaskTitle, "半天低负荷恢复"),
        daily("2026-07-28", "刑法课程 + 闭环 + 英语阅读", "按实际课程分钟推进后续章节 90 分钟，当前章未结束不跳章", "完成一个最近章节闭环缺口，30 分钟", "法理第 2 章正式背诵 25 分钟", dailyVocabularyTaskTitle, dailyEnglishReadingTaskTitle, "广播体操 10 分钟"),
        daily("2026-07-29", "刑法课程 + 法理背诵 + 英语长难句", "完成最近刑法课程缺口 90 分钟，记录剩余小节", "把一个最近缺口闭环到题目、主错因或正式框架图，30 分钟", "法理第 2 章回背 25 分钟", dailyVocabularyTaskTitle, dailyLongSentenceTaskTitle, "按时结束"),
        daily("2026-07-30", "刑法阶段验收 + 英语阅读", "按课程账本核对已听分钟和剩余课程，不用章节数制造虚假完成", "验收第 2 章独立题组、第 3-7 章合并题、错因和正式框架图，只补一个最小缺口，30 分钟", "法理第 2 章闭卷背诵 25 分钟", dailyVocabularyTaskTitle, dailyEnglishReadingTaskTitle, "准备 8 月阶段清单"),
        daily("2026-07-31", "刑法阶段复盘与8月续接", "按已听课程分钟核对刑法账本，记录当前章节、剩余课时和下一段 90-120 分钟课程入口", "核对题组、主错因、正式框架图和关键词痕迹，只补一个最小缺口，30 分钟", "法理第 2 章回背 25 分钟；根据真实结果决定 8 月继续第 2 章还是进入第 3 章", dailyVocabularyTaskTitle, dailyEnglishReviewTaskTitle, "不以熬夜破坏 8 月主线"),
    ).associateBy { it.date }

    fun todayPlan(date: LocalDate = LocalDate.now()): DailyStudyPlan? =
        dailyPlans[date] ?: generatedFallbackPlan(date)

    private fun generatedFallbackPlan(date: LocalDate): DailyStudyPlan? {
        if (date < LocalDate.of(2026, 8, 1) || date > examDate) return null
        val week = weekForDate(date) ?: return null
        val activeBook = when {
            date <= LocalDate.of(2026, 8, 7) -> "刑法收口"
            date <= LocalDate.of(2026, 9, 30) -> "顺序新课"
            else -> "全科回炉"
        }
        val activeCourse = when (activeBook) {
            "刑法收口" -> "刑法未完成时继续听众合法硕刑法课程；完成后次日再启动民法"
            "顺序新课" -> "按刑法→民法→宪法→法制史顺序继续当前未完成科目的众合法硕课程"
            else -> "按高频点和错题本回炉"
        }
        val title = when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> "周一主线：$activeBook"
            DayOfWeek.TUESDAY -> "周二主线：$activeBook"
            DayOfWeek.WEDNESDAY -> "周三主线：$activeBook + 背诵复线"
            DayOfWeek.THURSDAY -> "周四主线：$activeBook 闭环题和错题"
            DayOfWeek.FRIDAY -> "周五主线：$activeBook 真题锚点和主观题"
            DayOfWeek.SATURDAY -> "周六主线：$activeBook 补漏、框架和轻模拟"
            DayOfWeek.SUNDAY -> "周复盘/缓冲日：不新增硬章节"
        }
        val recitation = when {
            date < LocalDate.of(2026, 9, 15) ->
                "背诵：已学科目第一轮结构和关键词 20-30 分钟；本周闭环章必须留下闭卷输出痕迹"
            date < LocalDate.of(2026, 10, 1) ->
                "背诵：补齐五科第一轮 90-120 分钟；按闭卷结果回炉，不用翻书熟悉感代替会背"
            date < LocalDate.of(2026, 11, 1) ->
                "背诵：第二轮规范表述 90-120 分钟；背不出的章节写进隔日回炉表"
            else ->
                "背诵：第三轮限时输出 90-120 分钟拆成多次；默写框架、口头答题、抽背高频点"
        }
        val english = when (date.monthValue) {
            8 -> "英语：$dailyVocabularyTaskTitle；再完成 1 个 30 分钟主训练（真题阅读精读、长难句或翻译），每周 3 篇阅读、2 次长难句/翻译和 1 次错因复盘"
            9 -> "英语：$dailyVocabularyTaskTitle；再完成 1 个主训练块，阅读每周 3-4 篇并开始单篇限时，小三门和翻译按周轮换"
            10 -> "英语：$dailyVocabularyTaskTitle；分题型计时，阅读单篇 18-20 分钟，作文每周小作文/大作文各 1 块"
            11 -> "英语：$dailyVocabularyTaskTitle；每周至少 1 套 180 分钟整卷，次日复盘阅读证据、翻译表达、作文结构和时间"
            else -> "英语：$dailyVocabularyTaskTitle；每周至少 1 套完整限时，作文模板、阅读错题和时间分配保温，不扩新资料"
        }
        val politics = if (date >= LocalDate.of(2026, 9, 15)) {
            when {
                date.monthValue == 9 -> "政治：启动块 30-40 分钟，强化课/核心考案 20-25 分钟 + 1000 题 10-15 道 + 1 条错因，每周 3 次"
                date.monthValue == 10 -> "政治：每周 4-5 次章节课题绑定，1000 题和错因表同步，月底统计薄弱章节"
                date.monthValue == 11 -> "政治：肖八选择题限时与错因复盘，主观题练材料对应和答题结构"
                else -> "政治：肖四主观题背诵、选择题保温和 180 分钟整卷节奏校准"
            }
        } else {
            null
        }
        if (date.dayOfWeek == DayOfWeek.SUNDAY) {
            return restDaily(date.toString(), "周日完整休息/缓冲日：不安排学习任务")
        }
        val mainTask = if (activeBook == "全科回炉") {
            "主线块：按本周主题完成背诵、错题、真题或主观题闭卷输出，不再开常规新课；做完必须订正到具体章节和规范表述"
        } else {
            "新课主线：$activeCourse，按当周有效学习预算安排主块和续段，课程输入目标至少 120-180 分钟；上一章结束再进入下一章，课后随手整理包含在本任务内，不另生成任务"
        }
        val tasks = buildList {
            add("本周计划：${week.title}（${week.dateRange}）")
            add(mainTask)
            add("正式闭环块：30-45 分钟只完成一项章节题、错题主错因或明确标注的正式框架图；整章未结束时不刷整章题，也不把课后随手整理单独列卡片")
            add(recitation)
            add(english)
            if (politics != null) add(politics)
            add("饭后低阻力：音乐轻活动、手势舞、开窗站、散步、洗漱、整理桌面或轻复盘 10-20 分钟，等困劲过去后再进入深度学习")
        }
        return daily(date.toString(), title, *tasks.toTypedArray())
    }

    fun weekForDate(date: LocalDate): WeeklyStudyPlan? =
        weeklyPlans.firstOrNull { week ->
            val parts = week.dateRange.split(" 至 ")
            if (parts.size != 2) return@firstOrNull false
            val start = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return@firstOrNull false
            val end = if (parts[1] == "考前") {
                examDate
            } else {
                runCatching { LocalDate.parse(parts[1]) }.getOrNull() ?: return@firstOrNull false
            }
            date >= start && date <= end
        }

    fun plannedStudyMinutes(date: LocalDate = LocalDate.now()): Int {
        if (isRestDay(date)) return 0
        return when {
            date <= LocalDate.of(2026, 7, 12) -> baselineStudyMinutes
            date <= LocalDate.of(2026, 7, 19) -> 210
            date <= LocalDate.of(2026, 7, 26) -> 270
            date <= LocalDate.of(2026, 8, 2) -> 330
            date <= LocalDate.of(2026, 8, 9) -> 360
            date <= LocalDate.of(2026, 8, 16) -> 390
            date <= LocalDate.of(2026, 9, 30) -> 420
            date <= LocalDate.of(2026, 11, 30) -> 450
            date <= LocalDate.of(2026, 12, 14) -> 420
            else -> 300
        }
    }

    private fun timeRange(start: LocalTime, minutes: Int): String {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val end = start.plusMinutes(minutes.toLong())
        return "${start.format(formatter)}-${end.format(formatter)}"
    }

    fun todaySchedule(date: LocalDate = LocalDate.now()): List<StudyScheduleBlock> {
        val plan = todayPlan(date)
        val tasks = plan?.tasks.orEmpty()
        if (isRestDay(date)) {
            return listOf(
                StudyScheduleBlock("09:30-10:00", "自然醒 + 早餐", "今天是完整休息日，不安排单词、听课、背诵或题目。"),
                StudyScheduleBlock("11:30-12:00", "轻活动", "天气舒服就轻松散步或晒太阳；天气热就在寝室放音乐做手势舞、开窗站或转肩转腰。"),
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
                StudyScheduleBlock("18:20-19:00", "饭后低阻力", "饭后不要硬学；先听音乐慢走、手势舞一首歌、洗漱或整理桌面，等困劲过去。"),
                StudyScheduleBlock("19:10-19:35", "刑法加码", "如果身体还可以，只补刑法第 1 章小框架；不单独安排题目，课上例题算在听课里。"),
                StudyScheduleBlock("19:45-20:25", "单词 3", "不背单词 40 个（2 组），完成今天英语最小闭环。"),
                StudyScheduleBlock("21:20-21:35", "睡前收尾", "写明天第一步；23:30 前结束学习入口，不用补课证明自己。"),
            )
        }
        if (tasks.isEmpty()) {
            return listOf(
                StudyScheduleBlock("09:30-09:45", "启动", "写下今天最小任务：1 个专业课小块 + 1 个英语小块。"),
                StudyScheduleBlock("09:45-10:05", "单词启动", "先背不背单词 20 个（1 组）。如果有畏难或身体不舒服，这一组先帮大脑醒过来。"),
                StudyScheduleBlock("10:10-11:40", "主线学习", "优先做最怕但最重要的一项，不先整理资料。"),
                StudyScheduleBlock("14:30-15:05", "正式闭环", "从章节题、主错因或正式框架图中选一个最近缺口完成；课后随手整理不单独占时段。"),
                StudyScheduleBlock("22:30-22:45", "收尾", "只写明天第一步，不再开新章节。"),
            )
        }

        val hasLesson = tasks.any { it.title.hasAny("看课", "听课", "课程", "听众合法硕") }
        val hasRecite = tasks.any { it.title.hasAny("背诵", "回背", "口头", "闭卷", "抽背", "规范表述") }
        val hasReviewDay = tasks.any {
            it.kind != StudyPlanTaskKind.Health &&
                it.kind != StudyPlanTaskKind.English &&
                it.title.hasAny("复盘", "缓冲", "整理 7 月")
        }
        val lawTasks = tasks.filter {
            it.kind == StudyPlanTaskKind.Law && !it.title.startsWith("本周计划")
        }.map { it.title }
        val politicsTasks = tasks.filter { it.kind == StudyPlanTaskKind.Politics }.map { it.title }
        val reviewTasks = tasks.filter { it.kind == StudyPlanTaskKind.Review }.map { it.title }
        val englishTasks = tasks.filter { it.kind == StudyPlanTaskKind.English }.map { it.title }
        val vocabularyTasks = englishTasks.filter { it.contains("不背单词") }
        val englishCoreTasks = englishTasks.filterNot { it.contains("不背单词") }
        val closureTasks = reviewTasks.filter {
            it.hasAny(
                "章节题",
                "题组",
                "合并题",
                "错题",
                "主错因",
                "正式框架图",
                "正式连接框架",
                "思维导图",
                "知识图谱",
                "时间线",
            )
        }

        val mainInput = when {
            hasReviewDay -> "只处理复盘和漏项，不开大新章节。"
            lawTasks.isNotEmpty() -> lawTasks.take(2).joinToString("；")
            else -> tasks.firstOrNull { !it.title.startsWith("本周计划") }?.title ?: "完成今天的最小学习入口"
        }
        val eveningOutput = when {
            hasRecite -> reviewTasks.firstOrNull { it.hasAny("背诵", "回背", "口头", "关键词", "目录") }
                ?: "闭卷回忆 20 分钟，先复述结构和关键词，不追求一字不差。"
            else -> null
        }
        val studyBudget = plannedStudyMinutes(date)
        val includeClosure = closureTasks.isNotEmpty() || hasLesson
        val includePolitics = politicsTasks.isNotEmpty() && studyBudget >= 360
        val includeRecitation = eveningOutput != null
        val startupMinutes = 10
        val vocabularyTotalMinutes = when {
            studyBudget >= 420 -> 40
            studyBudget >= 270 -> 35
            else -> 30
        }
        val englishMinutes = when {
            studyBudget >= 420 -> 60
            studyBudget >= 330 -> 50
            studyBudget >= 270 -> 45
            else -> 30
        }
        val politicsMinutes = if (includePolitics) 40 else 0
        val recitationMinutes = if (includeRecitation) {
            when {
                studyBudget >= 420 -> 75
                studyBudget >= 330 -> 60
                studyBudget >= 270 -> 45
                else -> 30
            }
        } else 0
        val closureMinutes = if (includeClosure) {
            when {
                studyBudget >= 420 -> 60
                studyBudget >= 330 -> 45
                studyBudget >= 270 -> 40
                else -> 30
            }
        } else 0
        val professionalMinutes = (studyBudget - startupMinutes - vocabularyTotalMinutes - englishMinutes -
            politicsMinutes - recitationMinutes - closureMinutes).coerceAtLeast(60)
        val firstProfessionalMinutes = professionalMinutes.coerceAtMost(if (studyBudget >= 360) 120 else 90)
        val secondProfessionalMinutes = (professionalMinutes - firstProfessionalMinutes).coerceAtLeast(0)
        val vocabularyLaterMinutes = (vocabularyTotalMinutes - startupMinutes).coerceAtLeast(10)
        val firstProfessionalStart = LocalTime.of(10, 0)
        val secondProfessionalStart = LocalTime.of(14, 0)
        val secondProfessionalEnd = secondProfessionalStart.plusMinutes(secondProfessionalMinutes.toLong())
        val closureStart = if (secondProfessionalMinutes >= 20) {
            secondProfessionalEnd.plusMinutes(10)
        } else {
            LocalTime.of(14, 30)
        }
        val vocabularyStart = LocalTime.of(18, 45)
        val englishStart = vocabularyStart.plusMinutes(vocabularyLaterMinutes.toLong() + 10)
        val politicsStart = englishStart.plusMinutes(englishMinutes.toLong() + 10)
        val recitationStart = politicsStart.plusMinutes(if (includePolitics) politicsMinutes.toLong() + 10 else 0)

        return buildList {
            add(StudyScheduleBlock("09:30-09:40", "今日负荷", "今天按约 $studyBudget 分钟有效学习安排；这是逐周爬坡目标，不是把所有任务无限叠加。具体开始时间由你自己控制。"))
            add(StudyScheduleBlock("09:45-09:55", "单词启动", "先做不背单词 20 个（1 组）。如果有畏难、想逃避或身体发沉，先用这一组把状态点亮，再进专业课主块。"))
            add(StudyScheduleBlock(timeRange(firstProfessionalStart, firstProfessionalMinutes), if (hasLesson) "专业课主块" else "主线推进", "$mainInput；完成本块规定分钟，记录实际听课/题目分钟，不把课后闭环偷算成看课。"))
            add(StudyScheduleBlock("12:00-14:00", "午饭 + 午休", "吃饭和休息不计入学习时长；午休 20-40 分钟，避免下午断电。"))
            if (secondProfessionalMinutes >= 20) {
                add(
                    StudyScheduleBlock(
                        timeRange(secondProfessionalStart, secondProfessionalMinutes),
                        "专业课续段",
                        "继续同一科目或同一题组，不跨章；如果上午课程未收口，优先补课程，不另开新书。",
                    ),
                )
            }
            if (includeClosure && closureTasks.isNotEmpty()) {
                add(
                    StudyScheduleBlock(
                        timeRange(closureStart, closureMinutes),
                        "正式闭环",
                        closureTasks.first(),
                    ),
                )
            } else if (includeClosure && hasLesson) {
                add(
                    StudyScheduleBlock(
                        timeRange(closureStart, closureMinutes),
                        "课程续段",
                        "继续上午同一课程切片，不切换新章节；课后随手记录仍包含在课程内。",
                    ),
                )
            }
            if (vocabularyTasks.isNotEmpty()) {
                add(
                    StudyScheduleBlock(
                        timeRange(vocabularyStart, vocabularyLaterMinutes),
                        "英语单词",
                        "完成 ${vocabularyTasks.joinToString("；")}; 早上的 20 个已经算入 6 组中的 1 组，晚间完成剩余 5 组并只标记错词。",
                    ),
                )
            }
            if (englishCoreTasks.isNotEmpty()) {
                add(
                    StudyScheduleBlock(
                        timeRange(englishStart, englishMinutes),
                        "英语主训练",
                        englishCoreTasks.joinToString("；"),
                    ),
                )
            }
            if (includePolitics) {
                add(
                    StudyScheduleBlock(
                        timeRange(politicsStart, politicsMinutes),
                        "政治",
                        politicsTasks.joinToString("；"),
                    ),
                )
            }
            if (includeRecitation) {
                add(StudyScheduleBlock(timeRange(recitationStart, recitationMinutes), "背诵输出", eveningOutput!!))
            }
            add(StudyScheduleBlock("20:30-20:50", "音乐轻活动", "离开桌面 10-20 分钟；开窗站一会儿，配音乐做手势舞、转肩转腰、原地踏步或天气舒服时散步。"))
            add(StudyScheduleBlock("23:15-23:30", "强制收尾", "勾选待办，写明天第一步后离开学习桌；不再补课、不刷经验帖，目标 01:30 前入睡，不能继续拖到 02:00。"))
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
                    "只需要晚上确认明天第一步：刑法第 1 章做题/框架闭环，或法理背诵复线。不要今天偷补，也不要启动民法。",
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
        if (tasks.any { it.kind == StudyPlanTaskKind.Politics }) {
            tips += StudyTip(
                "政治必须课题绑定",
                "政治今天不是只听课：先完成强化课/核心考案小节，再做 10-15 道选择题，最后写 1 条错因；错题回到知识点，不背答案。",
            )
        }
        if (tasks.any { it.title.hasAny("看课", "听课", "课程", "听众合法硕") }) {
            tips += StudyTip(
                "看课别做抄写员",
                "不单独安排预习目录，老师讲课会带框架；关键词、卡点和小框架随听课自然留下，不再生成额外任务。章节没结束前不单独安排题目，整章闭环后再做章节题。",
            )
        }
        if (tasks.any { it.title.hasAny("做题", "题", "错题") }) {
            tips += StudyTip(
                "题目跟着整章走",
                "这一章没有结束前不要单独做题。整章听完后再集中做章节题；每道错题只标一个主错因，第二天先看错因。",
            )
        }
        if (tasks.any { it.title.hasAny("背诵", "回背", "口头", "闭卷", "抽背", "目录") }) {
            tips += StudyTip(
                "背书三步走",
                "先背目录树，再背关键词，最后用自己的话复述。卡住时看 10 秒提示就合上书继续说，不要从头重读。",
            )
        }
        if (tasks.any { it.title.hasAny("正式框架图", "正式连接框架", "思维导图", "知识图谱", "时间线") }) {
            tips += StudyTip(
                "正式框架图只画主干",
                "一张正式图只放章标题、层级、构成要件、易混关系和题目锚点。不要把教材搬进图里，图是为了回忆，不是为了收藏。",
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
                "复盘日只补最小漏项：错题 10 题、目录背 15 分钟、音乐轻活动或散步 20 分钟。保住连续性，比硬凑时长更值钱。",
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
        currentTime: LocalTime = LocalTime.now(),
    ): String {
        if (isRestDay(date)) {
            return """
                你是考研日计划编排助手。今天日期：$date。
                今天是用户明确选择的完整休息日，不安排任何学习任务。
                不要安排单词、长难句、听课、背诵、做题、错题、框架、复盘或资料整理。
                如果必须输出日程，只输出休息、吃饭、音乐轻活动/散步、自由时间和睡前确认明天第一步。
                只输出时间表，每行：HH:mm-HH:mm｜标题｜具体安排。
            """.trimIndent()
        }
        val currentMinute = currentTime.truncatedTo(ChronoUnit.MINUTES)
        val currentTimeText = currentMinute.format(DateTimeFormatter.ofPattern("HH:mm"))
        val unfinishedTasks = tasks.filterNot { it.done }
        val unfinishedPlanTasks = unfinishedTasks.filter { it.source == StudyTaskSource.Plan }
        val unfinishedManualTasks = unfinishedTasks.filter { it.source == StudyTaskSource.Manual }
        val remainingDefaultSchedule = scheduleBlocksFromTime(defaultSchedule, currentMinute)
        return buildString {
            appendLine(studyHabitReference)
            appendLine()
            appendLine(subjectExecutionReference)
            appendLine()
            appendLine("日期：$date")
            appendLine("当前时间：$currentTimeText")
            appendLine("今日默认学习预算：约 ${plannedStudyMinutes(date)} 分钟；这不是每天固定时长，可根据睡眠、身体和近期完成率降档。")
            appendLine("今日预制主题：${presetPlan?.title ?: "无预制主题，按最小闭环安排"}")
            appendLine("今日预制任务：")
            presetPlan?.tasks.orEmpty().forEachIndexed { index, task ->
                appendLine("${index + 1}. ${task.kind.label}｜${task.title}")
            }
            if (presetPlan == null) appendLine("1. 专业课小块 + 英语小块 + 音乐轻活动/收尾")
            appendLine()
            appendLine("当前未完成待办（包含用户手动新增和系统计划任务，先按今日预算取舍）：")
            if (unfinishedTasks.isEmpty()) {
                appendLine("- 暂无未完成待办，请按预制主题安排剩余时间。")
            } else {
                unfinishedPlanTasks.forEach { task -> appendLine("- 计划：${task.title}") }
                unfinishedManualTasks.forEach { task -> appendLine("- 手动：${task.title}") }
            }
            appendLine()
            appendLine("剩余日程骨架，可调整但不要丢掉饭点、休息、音乐轻活动/运动和睡前收尾；早于当前时间的默认块已剔除，不可原样补排：")
            if (remainingDefaultSchedule.isEmpty()) {
                appendLine("- 默认骨架没有剩余块，请从 $currentTimeText 起按待办和作息重新压缩安排。")
            } else {
                remainingDefaultSchedule.forEach { block ->
                    appendLine("${block.time}｜${block.title}｜${block.detail}")
                }
            }
            appendLine()
            appendLine("请重新生成今天的计划表。要求：")
            appendLine("- 生成时间是 $currentTimeText；如果日期是今天，第一段时间必须从 $currentTimeText 或之后开始，不允许输出早于当前时间的时间块。")
            appendLine("- 不要从早上补排；已经过去但未完成的待办，要从现在开始压缩、改序或拆成可执行小块。")
            appendLine("- 已完成待办不要重复安排。未完成待办按优先级选择；超出今日预算的任务明确顺延，不得为了清空列表压缩休息或延长到熬夜。")
            appendLine("- 如果用户手动待办里写了起床/睡觉时间，按它调整时间段。")
            appendLine("- 如果用户说睡太晚、肚子不舒服或身体难受，优先降量恢复；不要把昨天漏项整包顺延，只补最小入口。")
            appendLine("- 用户手动新增的临时任务优先插入；如果因此超出预算，从系统计划中删减或顺延，不额外加总量。")
            appendLine("- 非完整休息日固定保留 $dailyVocabularyTaskTitle，并且从 2026-07-13 起必须再安排一个英语主训练块（约 $dailyEnglishCoreMinutes 分钟）；主训练从长难句、真题阅读、翻译、完形/新题型或作文中选一项，不能连续只排单词。如果日期是 $currentWeekRestDate 或用户明确选择当天完整休息，当天不安排任何学习任务，英语也不补。")
            appendLine("- 英语主训练必须写清来源和步骤，例如：$dailyLongSentenceTaskTitle；阅读必须写题干定位、原文证据、错因归类和摘句，不要写成泛泛‘英语学习’。")
            appendLine("- 听课任务必须写“听众合法硕某科课程”，例如“听众合法硕刑法课程第 1 章”。")
            appendLine("- 专业课按课时估算，不按页数乐观估算：$professionalCourseHoursSummary。刑法第 1 章 19 页但约 6 小时，30-50 分钟只能算听课切片。")
            appendLine("- 一本新课主线学完再学下一本：刑法主线期间不把民法作为新开副线；复线只能是法理背诵、错题、框架回炉、英语保底或身体恢复。")
            appendLine("- 同一章已经听完或学完后，做题、错题收集和正式框架图优先集中完成；确实要切分时，只拆成 2 个大块，不要每天零散塞一点。")
            appendLine("- 7 月专业课组合是“刑法新学/闭环主线 + 法理背诵复线”；不要同时塞刑法、民法、宪法、法制史四本。")
            appendLine("- 不单独安排预习目录；老师讲课时会带框架。关键词、不懂点或小框架是听课中的自然记录，包含在听课任务内，禁止再生成独立时间块或待办。")
            appendLine("- 这一章没有结束前不要单独安排题目，也不要机械安排“5 道题”；老师课上例题算在听课里。整章课程闭环后，再安排章节题、错题整理和第一轮背诵；未闭环新章只能复述已听关键词，不算第一轮。")
            appendLine("- 专业课章节题一张按约 $chapterPracticeQuestionsPerSet 道估算。10-15 道只能算启动、回看或恢复日小块，不能写成完整章节题闭环；如果当天时间不够，要写清剩余题量在后续 1-2 天补完。")
            appendLine("- 法理学不用重听大课，但必须背：闭卷背目录树和关键词算第一轮，单纯看框架不算；后面再进入规范表述和限时输出。")
            appendLine("- 法理学不能长期停留在看错题和整理目录；需要正式背诵动作，例如合上书口头复述目录树和关键词、默写关键词、卡住只看 10 秒提示再继续说。当前先给 20-30 分钟，睡眠恢复且总量稳定后再加。")
            appendLine("- 专业课高分验收：每个闭环章节必须留下课程分钟、题目完成量、主错因、正式框架图、第一轮关键词和规范表述；9 月起每周至少 1 次主观题闭卷，10 月至少 2 次，11 月使用答题纸和套卷。")
            appendLine("- 政治在 $politicsStartDateText 起启动：每周 3 次 30-40 分钟，强化课/核心考案 20-25 分钟后立即做 1000 题或同等选择题 10-15 道并写 1 条错因；10 月每周 4-5 次，11 月肖八，12 月肖四。7-8 月不要把政治新课叠加到专业课上。")
            appendLine("- 法理章节必须按顺序连续推进。当前较早章节尚未完成或验收时，先拆分欠账并回收，不得跳到更后章节；缓冲日只复盘已经接触过的章节。")
            appendLine("- 最近七天平均约 2 小时、昨天已完成 3 小时；本周健康日按约 210 分钟，之后逐周爬到 270、330、360、390、420 分钟，不能把 4 小时当 8 月上限。真正不适时才降到恢复档 $recoveryStudyMinutes 分钟；每周日休息，完成率或睡眠崩掉就停档或降档。")
            appendLine("- 课程播放器虽然可四倍速，但按约二倍速计入有效分钟：暂停、记笔记、理解、题目、框架和背诵都要算时间。9 月 14 日只是冲刺假设，9 月 30 日才是更可靠的新课压力线；7 月 31 日和 8 月 20 日必须按真实课程分钟重算。")
            appendLine("- 任务超出今日预算时，保留最重要的专业课闭环和英语阅读，其余明确顺延；不要为了清空列表压缩休息或延长到熬夜。")
            appendLine("- 当前作息通常 02:00-10:00，不能纵容继续推迟：默认学习收尾不晚于 23:30，先把入睡提前到 01:30，再逐步提前；用户明确身体不适时才临时降档。")
            appendLine("- 专业课要按倍数考虑：听课后还要做题、错题总结、框架和三轮背诵；不要把听完课当作学完。")
            appendLine("- 只输出时间表，每行：HH:mm-HH:mm｜标题｜具体安排。")
        }
    }

    fun scheduleBlocksFromTime(
        blocks: List<StudyScheduleBlock>,
        currentTime: LocalTime,
    ): List<StudyScheduleBlock> {
        val currentMinute = currentTime.truncatedTo(ChronoUnit.MINUTES)
        return blocks.filter { block ->
            blockStartTime(block)?.let { !it.isBefore(currentMinute) } ?: true
        }
    }

    private fun blockStartTime(block: StudyScheduleBlock): LocalTime? {
        val rawStart = block.time
            .substringBefore('-')
            .substringBefore('–')
            .substringBefore('—')
            .trim()
        return runCatching { LocalTime.parse(rawStart, DateTimeFormatter.ofPattern("H:mm")) }.getOrNull()
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

    fun currentMilestone(date: LocalDate = LocalDate.now()): String = when {
        date <= LocalDate.of(2026, 7, 31) -> "当前阶段节点：7 月 31 日核对刑法课时与闭环痕迹，重新估算完成日"
        date <= LocalDate.of(2026, 8, 20) -> "当前阶段节点：8 月 20 日核对唯一新课主线，并按真实课时重算完成日"
        date <= LocalDate.of(2026, 8, 31) -> "当前阶段节点：8 月 31 日按真实进度重排 9 月章节范围"
        date <= LocalDate.of(2026, 9, 14) -> "当前冲刺假设：9 月 14 日核对新课完成度，背诵和输出复线不得停"
        date <= LocalDate.of(2026, 9, 30) -> "当前收口与分数关：力争结束常规新课，两张专业课诊断卷各 100-105 分"
        date <= LocalDate.of(2026, 10, 31) -> "当前分数关：10 月末两张专业课限时卷各 110-115 分"
        date <= LocalDate.of(2026, 11, 30) -> "当前分数关：11 月末专业课各 118-122 分，总分进入 375 区间"
        else -> "最终目标：总分 385，专业基础和专业综合各稳定在 120 分左右"
    }

    private fun daily(date: String, title: String, vararg tasks: String): DailyStudyPlan {
        val planDate = LocalDate.parse(date)
        val baseTasks = tasks
            .map(::normalizeTaskTitle)
        val withVocabulary = if (baseTasks.any { it.contains("不背单词") }) {
            baseTasks
        } else {
            baseTasks + dailyVocabularyTaskTitle
        }
        val normalizedTasks = if (
            planDate >= LocalDate.of(2026, 7, 13) &&
            withVocabulary.none { it.isEnglishCoreTask() }
        ) {
            withVocabulary + defaultEnglishCoreTask(planDate)
        } else {
            withVocabulary
        }
        return DailyStudyPlan(
            date = planDate,
            title = title,
            tasks = normalizedTasks.map { task ->
                planTask(
                    title = task,
                    kind = when {
                        task.contains("英语") || task.contains("单词") || task.contains("长难句") -> StudyPlanTaskKind.English
                        task.contains("政治") -> StudyPlanTaskKind.Politics
                        task.contains("散步") || task.contains("手势舞") || task.contains("广播体操") ||
                            task.contains("羽毛球") || task.contains("开窗站") || task.contains("原地踏步") ||
                            task.contains("转肩") || task.contains("转腰") || task.contains("音乐轻活动") ||
                            task.contains("吃") || task.contains("拉伸") -> StudyPlanTaskKind.Health
                        task.contains("听众合法硕") || task.contains("众合法硕课程") || task.contains("听课") ||
                            task.contains("看课") || task.contains("课程主块") || task.contains("课程队列") ||
                            task.contains("当前课程") -> StudyPlanTaskKind.Law
                        task.contains("回看") || task.contains("复述") || task.contains("背诵") ||
                        task.contains("框架") || task.contains("思维导图") || task.contains("整理") -> StudyPlanTaskKind.Review
                        task.contains("确认") || task.contains("补一个") -> StudyPlanTaskKind.Foundation
                        else -> StudyPlanTaskKind.Law
                    }
                )
            },
        )
    }

    private fun String.isEnglishCoreTask(): Boolean =
        contains("长难句") || contains("真题阅读") || contains("阅读精读") || contains("阅读预备") ||
            contains("英语周复盘") || contains("翻译") || contains("完形") || contains("新题型") ||
            contains("作文") || contains("英语整卷") || contains("英语：")

    private fun defaultEnglishCoreTask(date: LocalDate): String = when (date.dayOfWeek) {
        DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY -> dailyLongSentenceTaskTitle
        DayOfWeek.TUESDAY, DayOfWeek.THURSDAY -> dailyEnglishReadingTaskTitle
        DayOfWeek.SATURDAY -> dailyEnglishReviewTaskTitle
        DayOfWeek.SUNDAY -> dailyEnglishReviewTaskTitle
    }

    private fun restDaily(date: String, title: String): DailyStudyPlan =
        DailyStudyPlan(
            date = LocalDate.parse(date),
            title = title,
            tasks = emptyList(),
        )

    private fun isRestDay(date: LocalDate): Boolean =
        date == currentWeekRestDate ||
            (date >= LocalDate.of(2026, 7, 19) && date.dayOfWeek == DayOfWeek.SUNDAY) ||
            dailyPlans[date]?.tasks?.isEmpty() == true

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
    Politics("政治"),
    Law("专业课"),
    Review("复盘"),
    Health("身体"),
    Foundation("准备"),
}
