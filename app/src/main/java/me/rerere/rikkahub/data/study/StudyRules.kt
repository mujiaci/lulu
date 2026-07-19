package me.rerere.rikkahub.data.study

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.random.Random

object StudyRules {
    const val SINGLE_DRAW_COST = 100
    const val DISCOUNT_SINGLE_DRAW_COST = 100
    const val TEN_DRAW_COST = 800
    const val TASK_COMPLETE_KUDOS = 50
    const val EARLY_SLEEP_KUDOS = 500
    const val EARLY_RISE_TEN_DRAW_TICKETS = 1
    const val EARLY_SLEEP_EVENING_START_HOUR = 20
    const val EARLY_SLEEP_CUTOFF_HOUR = 1
    const val EARLY_SLEEP_CUTOFF_MINUTE = 30
    const val EARLY_RISE_CUTOFF_HOUR = 9
    const val EARLY_RISE_CUTOFF_MINUTE = 30
    const val NIGHT_CONFLICT_END_HOUR = 5
    const val STUDY_REWARD_INTERVAL_MINUTES = 5
    const val STUDY_REWARD_KUDOS = 100
    const val OFFICIAL_ECONOMY_RESET_VERSION = 2
    const val DATA_LOSS_COMPENSATION_VERSION = 4
    const val POMODORO_INTERRUPTION_COMPENSATION_VERSION = 1
    const val POMODORO_INTERRUPTION_COMPENSATION_KUDOS = 400
    const val GACHA_BAD_LUCK_COMPENSATION_VERSION = 1
    const val GACHA_BAD_LUCK_COMPENSATION_TEN_DRAW_TICKETS = 4
    const val NON_NORMAL_PITY_DRAW_COUNT = 30
    const val NORMAL_FRAGMENTS_PER_OUTFIT = 10
    private const val UNIVERSAL_NORMAL_OVERFLOW_KUDOS = 100
    private const val INTERNAL_TEST_GRANT_VERSION = 1

    val outfitNames = listOf(
        "星穹图书馆",
        "樱吹雪剑道场",
        "深海回廊",
        "永夜花庭",
        "云上列车",
        "琉璃沙漠",
        "机械蝴蝶",
        "月光浴场",
        "废墟花园",
        "倒悬都市",
        "雨后天台",
        "星砂邮局",
        "薄荷钟楼",
        "雾港旧船",
        "玻璃温室",
        "极光书房",
        "柠檬海岸",
        "雪夜便利店",
        "琥珀剧院",
        "云雀庭院",
    )

    val outfitParts = listOf("专属碎片")

    val theaterNames = listOf(
        "少卿今天不早朝",
        "星舰AI说他爱上我了",
        "废土便利店的草莓糖",
        "把魔尊契约当话本",
        "被献祭给龙之后",
        "捡到S级机甲",
        "我把修真界改成5A景区",
        "午夜出租车",
        "会整理书桌的幽灵",
        "欢迎来到心动游戏",
        "女王陛下的打脸法庭",
        "末世便利店女王",
        "女尊朝的首席狼臣",
        "前男友重生但我是反派",
        "原始部落的露字祭司",
        "性转恋综大逃杀",
    )

    val levels = listOf(
        StudyLevel(1, 80, "启程", StudyReward(kudos = 100, title = "夸夸值 100")),
        StudyLevel(2, 200, "稳定", StudyReward(kudos = 200, title = "夸夸值 200")),
        StudyLevel(3, 400, "微光", StudyReward(tenDrawTickets = 1, title = "十连抽券 x1")),
        StudyLevel(4, 800, "靠近", StudyReward(kudos = 300, title = "夸夸值 300")),
        StudyLevel(5, 1_500, "热望", StudyReward(kudos = 500, tenDrawTickets = 1, title = "夸夸值 500 + 十连抽券 x1")),
        StudyLevel(6, 2_500, "长跑", StudyReward(tenDrawTickets = 1, title = "十连抽券 x1")),
        StudyLevel(7, 4_000, "星火", StudyReward(kudos = 600, tenDrawTickets = 1, title = "夸夸值 600 + 十连抽券 x1")),
        StudyLevel(8, 6_500, "破晓", StudyReward(kudos = 800, title = "夸夸值 800")),
        StudyLevel(9, 10_000, "执灯", StudyReward(tenDrawTickets = 2, title = "十连抽券 x2")),
        StudyLevel(10, 15_000, "织梦者", StudyReward(kudos = 1_000, tenDrawTickets = 1, title = "夸夸值 1000 + 十连抽券 x1")),
        StudyLevel(11, 22_000, "星桥", StudyReward(kudos = 1_200, tenDrawTickets = 1, title = "夸夸值 1200 + 十连抽券 x1")),
        StudyLevel(12, 32_000, "远航", StudyReward(kudos = 1_500, tenDrawTickets = 2, title = "夸夸值 1500 + 十连抽券 x2")),
        StudyLevel(13, 45_000, "回响", StudyReward(kudos = 1_800, tenDrawTickets = 2, title = "夸夸值 1800 + 十连抽券 x2")),
        StudyLevel(14, 60_000, "满愿", StudyReward(kudos = 2_000, tenDrawTickets = 3, title = "夸夸值 2000 + 十连抽券 x3")),
        StudyLevel(15, 80_000, "星穹彼岸", StudyReward(kudos = 3_000, tenDrawTickets = 5, title = "夸夸值 3000 + 十连抽券 x5")),
    )

    val achievements = listOf(
        StudyAchievement("warm_start", "热身完成", "累计完成3个番茄钟", StudyReward(kudos = 50, title = "夸夸值 50")),
        StudyAchievement("first_companion", "初识陪伴", "累计完成10个番茄钟", StudyReward(tenDrawTickets = 1, title = "十连抽券 x1")),
        StudyAchievement("pomodoro_20", "渐入佳境", "累计完成20个番茄钟", StudyReward(kudos = 200, title = "夸夸值 200")),
        StudyAchievement("pomodoro_50", "专注成林", "累计完成50个番茄钟", StudyReward(tenDrawTickets = 2, title = "十连抽券 x2")),
        StudyAchievement("pomodoro_100", "百次同行", "累计完成100个番茄钟", StudyReward(kudos = 1_000, tenDrawTickets = 3, title = "夸夸值 1000 + 十连抽券 x3")),
        StudyAchievement("task_spark", "清单起势", "累计完成10项待办", StudyReward(kudos = 100, title = "夸夸值 100")),
        StudyAchievement("todo_slayer", "清单杀手", "累计完成30项待办", StudyReward(tenDrawTickets = 1, title = "十连抽券 x1")),
        StudyAchievement("tasks_50", "步步兑现", "累计完成50项待办", StudyReward(kudos = 500, title = "夸夸值 500")),
        StudyAchievement("tasks_100", "百事皆成", "累计完成100项待办", StudyReward(tenDrawTickets = 3, title = "十连抽券 x3")),
        StudyAchievement("perfect_3", "连续全清3天", "连续3天待办全清", StudyReward(kudos = 100, title = "夸夸值 100")),
        StudyAchievement("perfect_7", "连续全清7天", "连续7天待办全清", StudyReward(tenDrawTickets = 1, title = "十连抽券 x1")),
        StudyAchievement("perfect_14", "两周不负", "连续14天待办全清", StudyReward(kudos = 800, tenDrawTickets = 2, title = "夸夸值 800 + 十连抽券 x2")),
        StudyAchievement("perfect_30", "一月成章", "连续30天待办全清", StudyReward(tenDrawTickets = 5, title = "十连抽券 x5")),
        StudyAchievement("deep_work_10h", "坐稳书桌", "累计学习时长10小时", StudyReward(kudos = 100, title = "夸夸值 100")),
        StudyAchievement("time_traveler", "时光旅人", "累计学习时长50小时", StudyReward(kudos = 300, title = "夸夸值 300")),
        StudyAchievement("study_100h", "百小时灯火", "累计学习时长100小时", StudyReward(kudos = 1_000, tenDrawTickets = 2, title = "夸夸值 1000 + 十连抽券 x2")),
        StudyAchievement("study_200h", "长夜有光", "累计学习时长200小时", StudyReward(tenDrawTickets = 5, title = "十连抽券 x5")),
        StudyAchievement("first_outfit", "第一画卷", "解锁第一套普通画卷", StudyReward(tenDrawTickets = 1, title = "十连抽券 x1")),
        StudyAchievement("outfit_collector", "画卷收藏家", "解锁任意3套普通画卷", StudyReward(kudos = 500, title = "夸夸值 500")),
        StudyAchievement("outfits_5", "衣香成册", "解锁任意5套普通画卷", StudyReward(tenDrawTickets = 2, title = "十连抽券 x2")),
        StudyAchievement("outfits_10", "十卷珍藏", "解锁任意10套普通画卷", StudyReward(kudos = 1_500, tenDrawTickets = 3, title = "夸夸值 1500 + 十连抽券 x3")),
        StudyAchievement("theater_open", "剧场开幕", "攒够一次小剧场章节兑换", StudyReward(tenDrawTickets = 1, title = "十连抽券 x1")),
        StudyAchievement("lucky_drawer", "好运初现", "累计获得20个抽卡碎片", StudyReward(kudos = 100, title = "夸夸值 100")),
        StudyAchievement("epic_touch", "金光亮起", "获得第一枚金色碎片", StudyReward(tenDrawTickets = 1, kudos = 100, title = "十连抽券 x1 + 夸夸值 100")),
        StudyAchievement("mcdonalds_arrival", "第一支视频", "首次解锁视频奖励", StudyReward(kudos = 300, title = "夸夸值 300")),
        StudyAchievement("pomodoro_150", "一百五十次相见", "累计完成150个番茄钟", StudyReward(kudos = 1_500, tenDrawTickets = 3, title = "夸夸值 1500 + 十连抽券 x3")),
        StudyAchievement("pomodoro_200", "专注两百回", "累计完成200个番茄钟", StudyReward(tenDrawTickets = 5, title = "十连抽券 x5")),
        StudyAchievement("pomodoro_365", "日日有回声", "累计完成365个番茄钟", StudyReward(kudos = 3_650, tenDrawTickets = 8, title = "夸夸值 3650 + 十连抽券 x8")),
        StudyAchievement("tasks_150", "清单远征", "累计完成150项待办", StudyReward(kudos = 1_500, tenDrawTickets = 2, title = "夸夸值 1500 + 十连抽券 x2")),
        StudyAchievement("tasks_200", "两百次兑现", "累计完成200项待办", StudyReward(tenDrawTickets = 5, title = "十连抽券 x5")),
        StudyAchievement("tasks_365", "一年份完成感", "累计完成365项待办", StudyReward(kudos = 3_650, tenDrawTickets = 8, title = "夸夸值 3650 + 十连抽券 x8")),
        StudyAchievement("perfect_60", "两月成习", "连续60天待办全清", StudyReward(kudos = 2_000, tenDrawTickets = 5, title = "夸夸值 2000 + 十连抽券 x5")),
        StudyAchievement("perfect_100", "百日不辍", "连续100天待办全清", StudyReward(tenDrawTickets = 10, title = "十连抽券 x10")),
        StudyAchievement("study_300h", "三百小时航程", "累计学习时长300小时", StudyReward(kudos = 3_000, tenDrawTickets = 5, title = "夸夸值 3000 + 十连抽券 x5")),
        StudyAchievement("study_500h", "五百小时星河", "累计学习时长500小时", StudyReward(tenDrawTickets = 10, title = "十连抽券 x10")),
        StudyAchievement("study_1000h", "千小时之证", "累计学习时长1000小时", StudyReward(kudos = 10_000, tenDrawTickets = 20, title = "夸夸值 10000 + 十连抽券 x20")),
        StudyAchievement("outfits_15", "十五卷风景", "解锁任意15套普通画卷", StudyReward(kudos = 2_000, tenDrawTickets = 5, title = "夸夸值 2000 + 十连抽券 x5")),
        StudyAchievement("outfits_20", "画卷全收集", "解锁全部20套普通画卷", StudyReward(tenDrawTickets = 10, title = "十连抽券 x10")),
        StudyAchievement("theaters_3", "三幕连映", "解锁3个小剧场章节", StudyReward(kudos = 800, tenDrawTickets = 2, title = "夸夸值 800 + 十连抽券 x2")),
        StudyAchievement("videos_3", "三支珍藏", "累计解锁3次视频奖励", StudyReward(kudos = 800, tenDrawTickets = 2, title = "夸夸值 800 + 十连抽券 x2")),
    )

    fun rolloverToDate(state: StudyState, date: LocalDate = LocalDate.now()): StudyState {
        val dateText = date.toString()
        if (state.today == dateText) return syncPlanTasks(state, date)
        val previousDate = state.today.takeIf { it.isNotBlank() }
        val hadStudyOnPreviousDay = previousDate != null && state.lastStudyDate == previousDate
        val nextInactive = if (hadStudyOnPreviousDay) 0 else state.inactiveStudyDays + 1
        val rolled = state.copy(
            today = dateText,
            tasks = emptyList(),
            inactiveStudyDays = nextInactive,
            superMomentAvailable = false,
            purchasedShopItemIds = emptySet(),
            manualShopRefreshDate = null,
            activePlanDate = null,
        )
        return applyInactivityPenalty(syncPlanTasks(rolled, date)).state
    }

    fun grantInternalTestResources(state: StudyState): StudyState {
        if (state.internalTestGrantVersion >= OFFICIAL_ECONOMY_RESET_VERSION) return state
        if (state.internalTestGrantVersion >= INTERNAL_TEST_GRANT_VERSION) return state
        val reward = StudyReward(kudos = 100_000, title = "内部测试资源 +100000")
        return state.copy(
            wallet = state.wallet.add(reward),
            inventory = state.inventory.copy(videoFragments = state.inventory.videoFragments + 2),
            internalTestGrantVersion = INTERNAL_TEST_GRANT_VERSION,
            recentEvents = state.recentEvents.addEvent(StudyEventType.Fragment, "内部测试资源", "夸夸值 100000 · 金色碎片 x2"),
        )
    }

    fun resetEconomyForOfficialStart(state: StudyState): StudyState {
        if (state.internalTestGrantVersion >= OFFICIAL_ECONOMY_RESET_VERSION) return state
        return state.copy(
            internalTestGrantVersion = OFFICIAL_ECONOMY_RESET_VERSION,
            recentEvents = state.recentEvents.addEvent(
                StudyEventType.Fragment,
                "正式开始攒资源",
                "已清空测试夸夸值、抽卡券、盲盒和全部碎片",
            ),
        )
    }

    fun grantDataLossCompensation(state: StudyState): StudyState {
        if (state.internalTestGrantVersion >= DATA_LOSS_COMPENSATION_VERSION) return state
        val reward = StudyReward(tenDrawTickets = 6, title = "版本碎片补偿：十连抽券 x6（60 抽）")
        return state.copy(
            wallet = state.wallet.add(reward),
            inventory = state.inventory.copy(
                normalFragments = emptyMap(),
                rareFragments = emptyMap(),
                universalNormalFragments = 0,
                unopenedMysteryBoxes = state.inventory.unopenedMysteryBoxes.map { box ->
                    box.copy(universalNormalFragments = 0)
                },
            ),
            internalTestGrantVersion = DATA_LOSS_COMPENSATION_VERSION,
            recentEvents = state.recentEvents.addEvent(
                StudyEventType.Fragment,
                "版本碎片补偿",
                "已清空图片碎片（含通用普通碎片）并补发十连抽券 x6（60 抽）；已解锁图片和娱乐碎片保留",
            ),
        )
    }

    fun syncPlanTasks(state: StudyState, date: LocalDate = LocalDate.now()): StudyState {
        val dateText = date.toString()
        val plan = ExamStudyPlan.todayPlan(date)
        val manualTasks = state.tasks.filter { it.source != StudyTaskSource.Plan }
        val existingPlanTasksByTitle = state.tasks
            .filter { it.source == StudyTaskSource.Plan }
            .associateBy { it.title }
        val planTasks = plan?.tasks?.mapIndexed { index, task ->
            val id = "plan-$dateText-$index"
            val title = "${task.kind.label}｜${task.title}"
            val existing = existingPlanTasksByTitle[title]
            StudyTask(
                id = id,
                title = title,
                done = existing?.done ?: false,
                createdAt = existing?.createdAt ?: date.toEpochDay(),
                completedAt = existing?.completedAt,
                completionRewardClaimed = existing?.completionRewardClaimed ?: existing?.done ?: false,
                source = StudyTaskSource.Plan,
            )
        }.orEmpty()
        val existingPlanTitles = state.tasks.filter { it.source == StudyTaskSource.Plan }.map { it.title }
        val nextPlanTitles = planTasks.map { it.title }
        if (state.activePlanDate == dateText && existingPlanTitles == nextPlanTitles) return state
        return state.copy(
            today = dateText,
            tasks = planTasks + manualTasks,
            activePlanDate = dateText,
            superMomentAvailable = false,
            generatedSchedules = state.generatedSchedules - dateText,
        )
    }

    fun signIn(state: StudyState, date: LocalDate = LocalDate.now()): StudyActionResult {
        val dateText = date.toString()
        if (state.lastSignInDate == dateText) {
            return StudyActionResult(state)
        }
        val previous = state.lastSignInDate?.let(LocalDate::parse)
        val streak = if (previous == date.minusDays(1)) state.signInStreak + 1 else 1
        val kudos = 50
        val reward = StudyReward(kudos = kudos, title = "签到 +$kudos")
        return StudyActionResult(
            state = state.copy(
                today = dateText,
                signInStreak = streak,
                lastSignInDate = dateText,
                wallet = state.wallet.add(reward),
                recentEvents = state.recentEvents.addEvent(StudyEventType.SignIn, "签到成功", "连续 $streak 天，获得 $kudos 夸夸值"),
            ),
            reward = reward,
        )
    }

    fun hasClaimedSleepHabitReward(
        state: StudyState,
        habit: StudySleepHabit,
        date: LocalDate = state.today.toLocalDateOrToday(),
    ): Boolean = sleepHabitClaimKey(date, habit) in state.sleepHabitRewardClaims

    fun claimSleepHabitReward(
        state: StudyState,
        habit: StudySleepHabit,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        assistantName: String = "学习陪伴角色",
        decisionReason: String = "",
        reportedTime: LocalTime? = null,
    ): StudySleepRewardResult {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val date = now.toLocalDate()
        val current = if (state.today == date.toString()) state else rolloverToDate(state, date)
        val claimKey = sleepHabitClaimKey(date, habit)
        if (claimKey in current.sleepHabitRewardClaims) {
            return StudySleepRewardResult(
                state = current,
                granted = false,
                alreadyClaimed = true,
                reason = "今天这项作息奖励已经发过了。",
            )
        }
        if (habit == StudySleepHabit.EarlySleep && now.toLocalTime().isInObviousNightConflictWindow()) {
            return StudySleepRewardResult(
                state = current,
                granted = false,
                reason = "现在仍是凌晨且你正在聊天，不能把这一晚直接判定为早睡。",
            )
        }
        if (reportedTime == null) {
            return StudySleepRewardResult(
                state = current,
                granted = false,
                reason = "还不知道具体几点睡或几点起；角色需要先问清时间，再按你的作息基线判断。",
            )
        }
        val matchesPersonalBaseline = when (habit) {
            StudySleepHabit.EarlySleep -> reportedTime.isEarlySleepForUser()
            StudySleepHabit.EarlyRise -> reportedTime <= LocalTime.of(
                EARLY_RISE_CUTOFF_HOUR,
                EARLY_RISE_CUTOFF_MINUTE,
            )
        }
        if (!matchesPersonalBaseline) {
            val baseline = when (habit) {
                StudySleepHabit.EarlySleep -> "最晚约01:30入睡"
                StudySleepHabit.EarlyRise -> "最晚约09:30起床"
            }
            return StudySleepRewardResult(
                state = current,
                granted = false,
                reason = "这次时间没有达到你的个人标准（$baseline），所以不发奖励。",
            )
        }
        val cleanDecisionReason = decisionReason.trim()
        if (cleanDecisionReason.isBlank()) {
            return StudySleepRewardResult(
                state = current,
                granted = false,
                reason = "角色需要先根据时间和对话作出判断，并写明认可理由，不能机械发奖。",
            )
        }

        val reward = when (habit) {
            StudySleepHabit.EarlySleep -> StudyReward(
                kudos = EARLY_SLEEP_KUDOS,
                title = "早睡奖励 +$EARLY_SLEEP_KUDOS 夸夸值",
            )
            StudySleepHabit.EarlyRise -> StudyReward(
                tenDrawTickets = EARLY_RISE_TEN_DRAW_TICKETS,
                title = "早起奖励 十连抽券 x$EARLY_RISE_TEN_DRAW_TICKETS",
            )
        }
        val roleName = assistantName.trim().ifBlank { "学习陪伴角色" }
        val eventTitle = when (habit) {
            StudySleepHabit.EarlySleep -> "早睡被夸啦"
            StudySleepHabit.EarlyRise -> "早起被夸啦"
        }
        val eventDetail = when (habit) {
            StudySleepHabit.EarlySleep -> "$roleName 确认你昨晚按计划休息，+$EARLY_SLEEP_KUDOS 夸夸值"
            StudySleepHabit.EarlyRise -> "$roleName 确认你今天按计划起床，十连抽券 x$EARLY_RISE_TEN_DRAW_TICKETS"
        } + " · 判断：$cleanDecisionReason"
        return StudySleepRewardResult(
            state = current.copy(
                wallet = current.wallet.add(reward),
                sleepHabitRewardClaims = current.sleepHabitRewardClaims + claimKey,
                recentEvents = current.recentEvents.addEvent(StudyEventType.Habit, eventTitle, eventDetail),
            ),
            reward = reward,
            granted = true,
            reason = reward.title,
        )
    }

    fun toggleTask(
        state: StudyState,
        taskId: String,
        done: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): StudyActionResult {
        val index = state.tasks.indexOfFirst { it.id == taskId }
        if (index < 0 || state.tasks[index].done == done) return StudyActionResult(state)
        val nextTasks = state.tasks.toMutableList()
        val currentTask = nextTasks[index]
        val rewardEligible = done && !currentTask.completionRewardClaimed
        nextTasks[index] = currentTask.copy(
            done = done,
            completedAt = if (done) nowMillis else null,
            completionRewardClaimed = currentTask.completionRewardClaimed || currentTask.done || done,
        )
        val reward = if (rewardEligible) {
            StudyReward(kudos = TASK_COMPLETE_KUDOS, title = "完成待办 +$TASK_COMPLETE_KUDOS")
        } else {
            StudyReward()
        }
        val completedAll = nextTasks.isNotEmpty() && nextTasks.all { it.done }
        val canRecordPerfect = completedAll && state.lastPerfectDate != state.today
        val nextPerfectStreak = if (canRecordPerfect) state.perfectStreak + 1 else state.perfectStreak
        return StudyActionResult(
            state = state.copy(
                tasks = nextTasks,
                wallet = state.wallet.add(reward),
                stats = if (rewardEligible) {
                    state.stats.copy(totalTasksCompleted = state.stats.totalTasksCompleted + 1)
                } else {
                    state.stats
                },
                inactiveStudyDays = if (done) 0 else state.inactiveStudyDays,
                lastStudyDate = if (done) state.today else state.lastStudyDate,
                superMomentAvailable = state.superMomentAvailable ||
                    (completedAll && state.superMomentClaimedDate != state.today),
                perfectStreak = nextPerfectStreak,
                longestPerfectStreak = max(state.longestPerfectStreak, nextPerfectStreak),
                lastPerfectDate = if (canRecordPerfect) state.today else state.lastPerfectDate,
                recentEvents = state.recentEvents.addEvent(
                    StudyEventType.Task,
                    if (done) "待办完成" else "待办取消",
                    nextTasks[index].title,
                ),
            ),
            reward = reward,
        )
    }

    private fun sleepHabitClaimKey(date: LocalDate, habit: StudySleepHabit): String =
        "${date}:${habit.name}"

    private fun LocalTime.isInObviousNightConflictWindow(): Boolean =
        this > LocalTime.of(EARLY_SLEEP_CUTOFF_HOUR, EARLY_SLEEP_CUTOFF_MINUTE) &&
            this < LocalTime.of(NIGHT_CONFLICT_END_HOUR, 0)

    private fun LocalTime.isEarlySleepForUser(): Boolean =
        this >= LocalTime.of(EARLY_SLEEP_EVENING_START_HOUR, 0) ||
            this <= LocalTime.of(EARLY_SLEEP_CUTOFF_HOUR, EARLY_SLEEP_CUTOFF_MINUTE)

    fun completePomodoro(
        state: StudyState,
        minutes: Int = 25,
        random: Random = Random.Default,
    ): StudyActionResult {
        val studiedMinutes = minutes.coerceAtLeast(0)
        val date = state.today.ifBlank { LocalDate.now().toString() }
        val todayRecord = state.dailyStudyRecords[date] ?: StudyDailyRecord()
        val rewardMinutes = state.pendingRewardMinutes + studiedMinutes
        val rewardCount = rewardMinutes / STUDY_REWARD_INTERVAL_MINUTES
        val remainingRewardMinutes = rewardMinutes % STUDY_REWARD_INTERVAL_MINUTES
        val rewardKudos = rewardCount * STUDY_REWARD_KUDOS
        val purpleDrawsToday = if (state.dailyPurpleDrawDate == date) state.dailyPurpleDrawCount else 0
        val drawsToday = if (state.dailyPurpleDrawDate == date) state.dailyDrawCount else 0
        val grantPurpleSafety = todayRecord.studyMinutes + studiedMinutes >= 120 &&
            drawsToday >= 30 &&
            purpleDrawsToday == 0 &&
            state.purpleSafetyGrantedDate != date
        val reward = StudyReward(
            kudos = rewardKudos,
            purpleDrawTickets = if (grantPurpleSafety) 1 else 0,
            title = buildList {
                add(
                    if (rewardCount > 0) {
                        "学习 ${studiedMinutes} 分钟 +${rewardKudos} 夸夸值（${rewardCount} 抽卡进度）"
                    } else {
                        "学习 ${studiedMinutes} 分钟，抽卡进度 ${remainingRewardMinutes}/${STUDY_REWARD_INTERVAL_MINUTES} 分钟"
                    },
                )
                if (grantPurpleSafety) add("今日零紫安全抽 x1")
            }.joinToString(" · "),
        )
        return StudyActionResult(
            state = state.copy(
                wallet = state.wallet.add(reward),
                pendingRewardMinutes = remainingRewardMinutes,
                purpleSafetyGrantedDate = if (grantPurpleSafety) date else state.purpleSafetyGrantedDate,
                inactiveStudyDays = 0,
                lastStudyDate = date,
                stats = state.stats.copy(
                    totalPomodoros = state.stats.totalPomodoros + 1,
                    totalStudyMinutes = state.stats.totalStudyMinutes + studiedMinutes,
                ),
                dailyStudyRecords = state.dailyStudyRecords + (
                    date to todayRecord.copy(
                        pomodoros = todayRecord.pomodoros + 1,
                        studyMinutes = todayRecord.studyMinutes + studiedMinutes,
                    )
                    ),
                recentEvents = state.recentEvents.addEvent(
                    StudyEventType.Pomodoro,
                    "番茄钟完成",
                    if (rewardCount > 0) "获得 $rewardKudos 夸夸值 · 累计 $rewardCount 抽" else "累计抽卡进度 ${remainingRewardMinutes}/${STUDY_REWARD_INTERVAL_MINUTES} 分钟",
                ),
            ),
            reward = reward,
        )
    }

    fun studyTimeOverview(
        state: StudyState,
        today: LocalDate = state.today.toLocalDateOrToday(),
    ): StudyTimeOverview {
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val weekEnd = weekStart.plusDays(6)
        val todayRecord = state.dailyStudyRecords[today.toString()] ?: StudyDailyRecord()
        val weekRecords = state.dailyStudyRecords
            .mapNotNull { (rawDate, record) ->
                rawDate.toLocalDateOrNull()?.let { date -> date to record }
            }
            .filter { (date, _) -> !date.isBefore(weekStart) && !date.isAfter(weekEnd) }
            .map { (_, record) -> record }

        return StudyTimeOverview(
            todayMinutes = todayRecord.studyMinutes,
            todayPomodoros = todayRecord.pomodoros,
            weekMinutes = weekRecords.sumOf { it.studyMinutes },
            weekPomodoros = weekRecords.sumOf { it.pomodoros },
        )
    }

    fun openMysteryBox(state: StudyState, index: Int = 0): StudyActionResult {
        val box = state.inventory.unopenedMysteryBoxes.getOrNull(index) ?: return StudyActionResult(state)
        val reward = StudyReward(
            kudos = box.kudos,
            title = "盲盒 +${box.kudos} 夸夸值",
        )
        val remainingBoxes = state.inventory.unopenedMysteryBoxes.toMutableList().also { it.removeAt(index) }
        return StudyActionResult(
            state = state.copy(
                wallet = state.wallet.add(reward),
                inventory = state.inventory
                    .addReward(reward)
                    .copy(unopenedMysteryBoxes = remainingBoxes),
                recentEvents = state.recentEvents.addEvent(
                    StudyEventType.MysteryBox,
                    "盲盒开启",
                    reward.title,
                ),
            ),
            reward = reward,
        )
    }

    fun draw(state: StudyState, count: Int, random: Random = Random.Default): StudyDrawActionResult {
        val drawCount = if (count >= 10) 10 else 1
        val singleCost = if (hasSingleDrawDiscount(state)) DISCOUNT_SINGLE_DRAW_COST else SINGLE_DRAW_COST
        val nextWallet = when {
            drawCount == 1 && state.wallet.singleDrawTickets > 0 ->
                state.wallet.copy(singleDrawTickets = state.wallet.singleDrawTickets - 1)
            drawCount == 10 && state.wallet.tenDrawTickets > 0 ->
                state.wallet.copy(tenDrawTickets = state.wallet.tenDrawTickets - 1)
            state.wallet.kudos >= if (drawCount == 10) TEN_DRAW_COST else singleCost ->
                state.wallet.copy(kudos = state.wallet.kudos - if (drawCount == 10) TEN_DRAW_COST else singleCost)
            else -> return StudyDrawActionResult(state, emptyList())
        }
        var inventory = state.inventory
        var drawsSinceNonNormal = state.drawsSinceNonNormal.coerceIn(0, NON_NORMAL_PITY_DRAW_COUNT - 1)
        val generatedResults = mutableListOf<StudyDrawResult>()
        val results = buildList {
            repeat(drawCount) {
                val drawnResult = if (drawsSinceNonNormal >= NON_NORMAL_PITY_DRAW_COUNT - 1) {
                    drawRare(random)
                } else {
                    drawOne(random)
                }
                val alreadyFull = drawnResult.rarity == StudyRarity.Normal &&
                    inventory.isNormalFragmentFull(drawnResult.fragmentKey)
                val result = drawnResult.copy(alreadyFull = alreadyFull)
                generatedResults += result
                drawsSinceNonNormal = if (result.rarity == StudyRarity.Normal) {
                    drawsSinceNonNormal + 1
                } else {
                    0
                }
                if (!alreadyFull) {
                    inventory = inventory.addDrawResult(result)
                }
                // A pull remains visible even if its blue fragment was already full.
                add(result)
            }
        }
        val refreshed = inventory.refreshUnlockStats()
        val drawDate = state.today.ifBlank { LocalDate.now().toString() }
        val previousPurpleCount = if (state.dailyPurpleDrawDate == drawDate) state.dailyPurpleDrawCount else 0
        val previousDrawCount = if (state.dailyPurpleDrawDate == drawDate) state.dailyDrawCount else 0
        val purpleCount = generatedResults.count { it.rarity == StudyRarity.Rare }
        val nextPurpleCount = previousPurpleCount + purpleCount
        val nextDrawCount = previousDrawCount + generatedResults.size
        val studiedToday = state.dailyStudyRecords[drawDate]?.studyMinutes ?: 0
        val grantPurpleSafety = studiedToday >= 120 &&
            nextDrawCount >= 30 &&
            nextPurpleCount == 0 &&
            state.purpleSafetyGrantedDate != drawDate
        return StudyDrawActionResult(
            state = state.copy(
                wallet = nextWallet.copy(
                    purpleDrawTickets = nextWallet.purpleDrawTickets + if (grantPurpleSafety) 1 else 0,
                ),
                inventory = refreshed.first,
                dailyPurpleDrawDate = drawDate,
                dailyPurpleDrawCount = nextPurpleCount,
                dailyDrawCount = nextDrawCount,
                drawsSinceNonNormal = drawsSinceNonNormal,
                purpleSafetyGrantedDate = if (grantPurpleSafety) drawDate else state.purpleSafetyGrantedDate,
                stats = state.stats.copy(
                    unlockedOutfitSets = refreshed.second.first,
                    unlockedTheaters = state.stats.unlockedTheaters,
                ),
                recentEvents = state.recentEvents.addEvent(
                    StudyEventType.Draw,
                    if (drawCount == 10) "十连抽" else "单抽",
                    "完成 $drawCount 抽；获得 ${results.count { !it.alreadyFull }} 项，${results.count { it.alreadyFull }} 枚已满蓝色碎片未重复计入",
                ),
            ),
            results = results,
        )
    }

    /**
     * One-time private-build compensation for the Pomodoro that was interrupted by the update.
     * Keep this idempotent because StudyStore applies migrations from several read/write paths.
     */
    fun grantPomodoroInterruptionCompensation(state: StudyState): StudyState {
        if (state.pomodoroInterruptionCompensationVersion >= POMODORO_INTERRUPTION_COMPENSATION_VERSION) {
            return state
        }
        val reward = StudyReward(
            kudos = POMODORO_INTERRUPTION_COMPENSATION_KUDOS,
            title = "番茄钟中断补偿 +${POMODORO_INTERRUPTION_COMPENSATION_KUDOS} 夸夸值",
        )
        return state.copy(
            wallet = state.wallet.add(reward),
            pomodoroInterruptionCompensationVersion = POMODORO_INTERRUPTION_COMPENSATION_VERSION,
            recentEvents = state.recentEvents.addEvent(
                StudyEventType.Pomodoro,
                "番茄钟中断补偿",
                reward.title,
            ),
        )
    }

    fun grantGachaBadLuckCompensation(state: StudyState): StudyState {
        if (state.gachaBadLuckCompensationVersion >= GACHA_BAD_LUCK_COMPENSATION_VERSION) return state
        val reward = StudyReward(
            tenDrawTickets = GACHA_BAD_LUCK_COMPENSATION_TEN_DRAW_TICKETS,
            title = "40抽全蓝体验补偿：十连抽券 x$GACHA_BAD_LUCK_COMPENSATION_TEN_DRAW_TICKETS",
        )
        return state.copy(
            wallet = state.wallet.add(reward),
            gachaBadLuckCompensationVersion = GACHA_BAD_LUCK_COMPENSATION_VERSION,
            recentEvents = state.recentEvents.addEvent(
                StudyEventType.Draw,
                "40抽全蓝体验补偿",
                "已补发十连抽券 x$GACHA_BAD_LUCK_COMPENSATION_TEN_DRAW_TICKETS（40抽）",
            ),
        )
    }

    fun drawPurpleTicket(state: StudyState, random: Random = Random.Default): StudyDrawActionResult {
        if (state.wallet.purpleDrawTickets <= 0) return StudyDrawActionResult(state, emptyList())
        val result = drawRare(random)
        val refreshed = state.inventory.addDrawResult(result).refreshUnlockStats()
        val drawDate = state.today.ifBlank { LocalDate.now().toString() }
        val previousPurpleCount = if (state.dailyPurpleDrawDate == drawDate) state.dailyPurpleDrawCount else 0
        val previousDrawCount = if (state.dailyPurpleDrawDate == drawDate) state.dailyDrawCount else 0
        return StudyDrawActionResult(
            state = state.copy(
                wallet = state.wallet.copy(purpleDrawTickets = state.wallet.purpleDrawTickets - 1),
                inventory = refreshed.first,
                dailyPurpleDrawDate = drawDate,
                dailyPurpleDrawCount = previousPurpleCount + 1,
                dailyDrawCount = previousDrawCount,
                stats = state.stats.copy(unlockedOutfitSets = refreshed.second.first),
                recentEvents = state.recentEvents.addEvent(
                    StudyEventType.Draw,
                    "今日零紫安全抽",
                    result.title,
                ),
            ),
            results = listOf(result),
        )
    }

    fun currentLevel(state: StudyState): StudyLevel {
        return levels.lastOrNull { state.wallet.totalKudosEarned >= it.threshold } ?: levels.first()
    }

    fun claimableLevels(state: StudyState): List<StudyLevel> {
        return levels.filter { state.wallet.totalKudosEarned >= it.threshold && it.level !in state.claimedLevelRewards }
    }

    fun hasSingleDrawDiscount(state: StudyState): Boolean {
        return state.wallet.totalKudosEarned >= 80_000 || 15 in state.claimedLevelRewards
    }

    fun claimLevelReward(state: StudyState, level: Int): StudyActionResult {
        val item = levels.firstOrNull { it.level == level } ?: return StudyActionResult(state)
        if (state.wallet.totalKudosEarned < item.threshold || level in state.claimedLevelRewards) return StudyActionResult(state)
        return StudyActionResult(
            state = state.copy(
                wallet = state.wallet.add(item.reward),
                inventory = state.inventory.addReward(item.reward),
                claimedLevelRewards = state.claimedLevelRewards + level,
                recentEvents = state.recentEvents.addEvent(StudyEventType.Level, "等级奖励", "Lv$level ${item.reward.title}"),
            ),
            reward = item.reward,
        )
    }

    fun claimSuperMoment(state: StudyState, choice: SuperMomentChoice): StudyActionResult {
        if (!state.superMomentAvailable || state.superMomentClaimedDate == state.today) return StudyActionResult(state)
        val selected = when (choice) {
            SuperMomentChoice.NormalFragments,
            SuperMomentChoice.RareFragment -> StudyReward()
        }
        val fixed = StudyReward(tenDrawTickets = 1, title = "十连抽券 x1")
        val reward = selected + fixed
        return StudyActionResult(
            state = state.copy(
                wallet = state.wallet.add(reward),
                inventory = state.inventory.addReward(reward),
                superMomentAvailable = false,
                superMomentClaimedDate = state.today,
                recentEvents = state.recentEvents.addEvent(StudyEventType.SuperMoment, "超神时刻", reward.title),
            ),
            reward = reward,
        )
    }

    fun claimableAchievements(state: StudyState): List<StudyAchievement> {
        return achievements.filter { achievement ->
            achievement.id !in state.claimedAchievementIds && when (achievement.id) {
                "first_companion" -> state.stats.totalPomodoros >= 10
                "pomodoro_20" -> state.stats.totalPomodoros >= 20
                "pomodoro_50" -> state.stats.totalPomodoros >= 50
                "pomodoro_100" -> state.stats.totalPomodoros >= 100
                "pomodoro_150" -> state.stats.totalPomodoros >= 150
                "pomodoro_200" -> state.stats.totalPomodoros >= 200
                "pomodoro_365" -> state.stats.totalPomodoros >= 365
                "warm_start" -> state.stats.totalPomodoros >= 3
                "todo_slayer" -> state.stats.totalTasksCompleted >= 30
                "task_spark" -> state.stats.totalTasksCompleted >= 10
                "tasks_50" -> state.stats.totalTasksCompleted >= 50
                "tasks_100" -> state.stats.totalTasksCompleted >= 100
                "tasks_150" -> state.stats.totalTasksCompleted >= 150
                "tasks_200" -> state.stats.totalTasksCompleted >= 200
                "tasks_365" -> state.stats.totalTasksCompleted >= 365
                "perfect_3" -> state.longestPerfectStreak >= 3
                "perfect_7" -> state.longestPerfectStreak >= 7
                "perfect_14" -> state.longestPerfectStreak >= 14
                "perfect_30" -> state.longestPerfectStreak >= 30
                "perfect_60" -> state.longestPerfectStreak >= 60
                "perfect_100" -> state.longestPerfectStreak >= 100
                "deep_work_10h" -> state.stats.totalStudyMinutes >= 600
                "time_traveler" -> state.stats.totalStudyMinutes >= 3_000
                "study_100h" -> state.stats.totalStudyMinutes >= 6_000
                "study_200h" -> state.stats.totalStudyMinutes >= 12_000
                "study_300h" -> state.stats.totalStudyMinutes >= 18_000
                "study_500h" -> state.stats.totalStudyMinutes >= 30_000
                "study_1000h" -> state.stats.totalStudyMinutes >= 60_000
                "first_outfit" -> state.stats.unlockedOutfitSets >= 1
                "outfit_collector" -> state.stats.unlockedOutfitSets >= 3
                "outfits_5" -> state.stats.unlockedOutfitSets >= 5
                "outfits_10" -> state.stats.unlockedOutfitSets >= 10
                "outfits_15" -> state.stats.unlockedOutfitSets >= 15
                "outfits_20" -> state.stats.unlockedOutfitSets >= 20
                "theater_open" -> state.inventory.theaterFragments >= 1 || state.stats.unlockedTheaters >= 1
                "lucky_drawer" -> state.inventory.normalFragments.values.sum() +
                    state.inventory.universalNormalFragments +
                    state.inventory.douyinFragments + state.inventory.theaterFragments +
                    state.inventory.gameFragments + state.inventory.videoFragments +
                    state.inventory.animeFragments >= 20
                "epic_touch" -> state.inventory.gameFragments + state.inventory.videoFragments >= 1
                "mcdonalds_arrival" -> state.stats.videoRewardsRedeemed >= 1
                "theaters_3" -> state.stats.unlockedTheaters >= 3
                "videos_3" -> state.stats.videoRewardsRedeemed >= 3
                else -> false
            }
        }
    }

    fun claimAchievement(state: StudyState, id: String): StudyActionResult {
        val achievement = claimableAchievements(state).firstOrNull { it.id == id } ?: return StudyActionResult(state)
        return StudyActionResult(
            state = state.copy(
                wallet = state.wallet.add(achievement.reward),
                inventory = state.inventory.addReward(achievement.reward),
                claimedAchievementIds = state.claimedAchievementIds + id,
                recentEvents = state.recentEvents.addEvent(
                    StudyEventType.Achievement,
                    achievement.title,
                    achievement.reward.title,
                ),
            ),
            reward = achievement.reward,
        )
    }

    fun refreshShopIfNeeded(state: StudyState, date: LocalDate = LocalDate.now(), random: Random = Random.Default): StudyState {
        val dateText = date.toString()
        if (state.shopDate == dateText && state.shopItems.size == 3) return state
        // The first daily shelf must be reproducible: the screen may render it before it is
        // persisted, and its item IDs still need to match the first purchase request.
        return createShopShelf(state, dateText, Random(dateText.hashCode()))
    }

    private fun createShopShelf(state: StudyState, dateText: String, random: Random): StudyState {
        val pool = listOf(
            StudyShopItemType.DouyinFragment to 7,
            StudyShopItemType.TheaterFragment to 7,
            StudyShopItemType.GameFragment to 3,
            StudyShopItemType.VideoFragment to 3,
            StudyShopItemType.AnimeFragment to 1,
            StudyShopItemType.SingleDrawTicket to 74,
        )
        val items = (1..3).map { slot ->
            val type = weighted(pool, random)
            type.toShopItem("$dateText-$slot-$type")
        }
        return state.copy(shopDate = dateText, shopItems = items, purchasedShopItemIds = emptySet())
    }

    fun manualRefreshShop(state: StudyState, date: LocalDate = LocalDate.now(), random: Random = Random.Default): StudyState {
        val dateText = date.toString()
        if (state.manualShopRefreshDate == dateText) return state
        return createShopShelf(
            state = state.copy(shopDate = null, manualShopRefreshDate = dateText),
            dateText = dateText,
            random = random,
        )
    }

    fun buyShopItem(state: StudyState, itemId: String): StudyActionResult {
        val item = state.shopItems.firstOrNull { it.id == itemId } ?: return StudyActionResult(state)
        if (item.id in state.purchasedShopItemIds || state.wallet.kudos < item.price) return StudyActionResult(state)
        val reward = item.toReward()
        return StudyActionResult(
            state = state.copy(
                wallet = state.wallet.copy(kudos = state.wallet.kudos - item.price).add(reward),
                inventory = state.inventory.addReward(reward),
                purchasedShopItemIds = state.purchasedShopItemIds + item.id,
                recentEvents = state.recentEvents.addEvent(StudyEventType.Shop, "神秘商店", "购买 ${item.title}"),
            ),
            reward = reward,
        )
    }

    fun applyInactivityPenalty(state: StudyState): StudyActionResult {
        val penalty = when {
            state.inactiveStudyDays >= 3 -> 100
            state.inactiveStudyDays == 2 -> 50
            else -> 0
        }
        if (penalty == 0) return StudyActionResult(state)
        return StudyActionResult(
            state = state.copy(
                wallet = state.wallet.copy(kudos = (state.wallet.kudos - penalty).coerceAtLeast(0)),
                recentEvents = state.recentEvents.addEvent(StudyEventType.Penalty, "学习中断惩罚", "-$penalty 夸夸值"),
            ),
            reward = StudyReward(kudos = -penalty, title = "惩罚 -$penalty"),
        )
    }

    fun selectCompanion(state: StudyState, assistantId: String): StudyState {
        return state.copy(
            selectedAssistantId = assistantId,
            recentEvents = state.recentEvents.addEvent(StudyEventType.Fragment, "学习陪伴角色", "已切换今天陪你学习的角色"),
        )
    }

    fun redeemEntertainment(
        state: StudyState,
        rewardType: StudyEntertainmentReward,
    ): StudyActionResult {
        val available = when (rewardType) {
            StudyEntertainmentReward.Douyin -> state.inventory.douyinFragments
            StudyEntertainmentReward.Theater -> state.inventory.theaterFragments
            StudyEntertainmentReward.Game -> state.inventory.gameFragments
            StudyEntertainmentReward.Video -> state.inventory.videoFragments
            StudyEntertainmentReward.Anime -> state.inventory.animeFragments
        }
        if (available < 1) return StudyActionResult(state)
        val inventory = when (rewardType) {
            StudyEntertainmentReward.Douyin -> state.inventory.copy(
                douyinFragments = state.inventory.douyinFragments - 1,
            )
            StudyEntertainmentReward.Theater -> state.inventory.copy(
                theaterFragments = state.inventory.theaterFragments - 1,
            )
            StudyEntertainmentReward.Game -> state.inventory.copy(
                gameFragments = state.inventory.gameFragments - 1,
            )
            StudyEntertainmentReward.Video -> state.inventory.copy(
                videoFragments = state.inventory.videoFragments - 1,
            )
            StudyEntertainmentReward.Anime -> state.inventory.copy(
                animeFragments = state.inventory.animeFragments - 1,
            )
        }
        val rewardTitle = when (rewardType) {
            StudyEntertainmentReward.Douyin -> "抖音时长券已使用 · 20分钟"
            StudyEntertainmentReward.Theater -> "剧场碎片已使用 · 1章"
            StudyEntertainmentReward.Game -> "游戏畅玩券已使用 · 120分钟"
            StudyEntertainmentReward.Video -> "视频解锁卡已使用"
            StudyEntertainmentReward.Anime -> "番剧兑换券已使用 · 3小时"
        }
        return StudyActionResult(
            state = state.copy(
                inventory = inventory,
                recentEvents = state.recentEvents.addEvent(
                    StudyEventType.Entertainment,
                    rewardTitle,
                    "使用 1 张${rewardType.fragmentLabel()}",
                ),
            ),
            reward = StudyReward(title = rewardTitle),
        )
    }

    fun useUniversalNormalFragment(state: StudyState, key: String): StudyActionResult {
        if (state.inventory.universalNormalFragments <= 0 || !key.startsWith("normal:")) return StudyActionResult(state)
        if (state.inventory.isNormalFragmentFull(key)) {
            return StudyActionResult(
                state = state.copy(
                    wallet = state.wallet.copy(kudos = state.wallet.kudos + UNIVERSAL_NORMAL_OVERFLOW_KUDOS),
                    inventory = state.inventory.copy(
                        universalNormalFragments = state.inventory.universalNormalFragments - 1,
                    ),
                    recentEvents = state.recentEvents.addEvent(
                        StudyEventType.Fragment,
                        "碎片溢出转换",
                        "${normalTitle(key)} 已满，转换为夸夸值 $UNIVERSAL_NORMAL_OVERFLOW_KUDOS",
                    ),
                ),
                reward = StudyReward(
                    kudos = UNIVERSAL_NORMAL_OVERFLOW_KUDOS,
                    title = "碎片已满，转换为夸夸值 $UNIVERSAL_NORMAL_OVERFLOW_KUDOS",
                ),
            )
        }
        val inventory = state.inventory.copy(
            universalNormalFragments = state.inventory.universalNormalFragments - 1,
            normalFragments = state.inventory.normalFragments.plusCount(key, 1),
        ).refreshUnlockStats().first
        return StudyActionResult(
            state = state.copy(
                inventory = inventory,
                stats = state.stats.copy(
                    unlockedOutfitSets = inventory.unlockedOutfits.size,
                    unlockedTheaters = state.stats.unlockedTheaters,
                ),
                recentEvents = state.recentEvents.addEvent(StudyEventType.Fragment, "使用通用普通碎片", normalTitle(key)),
            ),
            reward = StudyReward(title = "已补到 ${normalTitle(key)}"),
        )
    }

    fun bestNormalFragmentTarget(state: StudyState): String? {
        return outfitNames.flatMap { outfit ->
            outfitParts.map { part -> "normal:$outfit:$part" }
        }.filter { key ->
            !state.inventory.isNormalFragmentFull(key)
        }.maxByOrNull { key -> state.inventory.normalFragments[key] ?: 0 }
    }

    fun addTask(state: StudyState, title: String, nowMillis: Long = System.currentTimeMillis()): StudyState {
        val clean = title.trim()
        if (clean.isBlank()) return state
        val id = "task-$nowMillis-${state.tasks.size}"
        return state.copy(tasks = state.tasks + StudyTask(id = id, title = clean, createdAt = nowMillis))
    }

    fun deleteTask(state: StudyState, id: String): StudyState {
        return state.copy(tasks = state.tasks.filterNot { it.id == id })
    }

    fun clearGeneratedSchedule(state: StudyState, date: LocalDate): StudyState {
        return state.copy(generatedSchedules = state.generatedSchedules - date.toString())
    }

    fun saveGeneratedSchedule(
        state: StudyState,
        date: LocalDate,
        schedule: List<StudyScheduleBlock>,
    ): StudyState {
        val cleanSchedule = schedule
            .filter { it.time.isNotBlank() && it.title.isNotBlank() && it.detail.isNotBlank() }
            .take(12)
        if (cleanSchedule.isEmpty()) return state
        return state.copy(generatedSchedules = state.generatedSchedules + (date.toString() to cleanSchedule))
    }

    fun normalTitle(key: String): String {
        val parts = key.split(":")
        return if (parts.size >= 3) "${parts[1]}-${parts[2]}" else key
    }

    fun rareTitle(key: String): String {
        return key.removePrefix("rare:")
    }

    private fun mysteryBox(random: Random): StudyMysteryBoxReward {
        val kudos = weighted(listOf(15 to 40, 25 to 30, 50 to 15, 100 to 4, 200 to 1), random)
        return StudyMysteryBoxReward(kudos = kudos)
    }

    private fun drawOne(random: Random): StudyDrawResult {
        val roll = random.nextDouble()
        return when {
            roll < 0.9215 -> {
                val outfit = outfitNames[random.nextInt(outfitNames.size)]
                val part = outfitParts[random.nextInt(outfitParts.size)]
                StudyDrawResult(StudyRarity.Normal, "normal:$outfit:$part", "$outfit-$part 碎片")
            }
            roll < 0.9815 -> drawRare(random)
            roll < 0.9965 -> if (random.nextDouble() < 0.8) {
                StudyDrawResult(StudyRarity.Epic, "epic:game", "游戏畅玩券 · 120分钟", StudyFragmentType.Game)
            } else {
                StudyDrawResult(StudyRarity.Epic, "epic:video", "视频解锁卡", StudyFragmentType.Video)
            }
            else -> StudyDrawResult(StudyRarity.Rainbow, "rainbow:anime", "番剧兑换券 · 3小时", StudyFragmentType.Anime)
        }
    }

    private fun drawRare(random: Random): StudyDrawResult = if (random.nextDouble() < (5.0 / 6.0)) {
        StudyDrawResult(StudyRarity.Rare, "rare:douyin", "抖音时长券 · 20分钟", StudyFragmentType.Douyin)
    } else {
        StudyDrawResult(StudyRarity.Rare, "rare:theater", "剧场碎片", StudyFragmentType.Theater)
    }

    private fun <T> weighted(items: List<Pair<T, Int>>, random: Random): T {
        val total = items.sumOf { it.second }
        var roll = random.nextInt(total)
        for ((item, weight) in items) {
            if (roll < weight) return item
            roll -= weight
        }
        return items.last().first
    }
}

private operator fun StudyReward.plus(other: StudyReward): StudyReward {
    val title = listOf(this.title, other.title).filter { it.isNotBlank() }.joinToString(" + ")
    return StudyReward(
        kudos = kudos + other.kudos,
        mysteryBoxKudos = mysteryBoxKudos + other.mysteryBoxKudos,
        singleDrawTickets = singleDrawTickets + other.singleDrawTickets,
        tenDrawTickets = tenDrawTickets + other.tenDrawTickets,
        purpleDrawTickets = purpleDrawTickets + other.purpleDrawTickets,
        universalNormalFragments = universalNormalFragments + other.universalNormalFragments,
        douyinFragments = douyinFragments + other.douyinFragments,
        theaterFragments = theaterFragments + other.theaterFragments,
        gameFragments = gameFragments + other.gameFragments,
        videoFragments = videoFragments + other.videoFragments,
        animeFragments = animeFragments + other.animeFragments,
        title = title,
    )
}

private fun StudyWallet.add(reward: StudyReward): StudyWallet {
    val positiveKudos = reward.kudos.coerceAtLeast(0)
    return copy(
        kudos = (kudos + reward.kudos).coerceAtLeast(0),
        totalKudosEarned = totalKudosEarned + positiveKudos,
        singleDrawTickets = singleDrawTickets + reward.singleDrawTickets,
        tenDrawTickets = tenDrawTickets + reward.tenDrawTickets,
        purpleDrawTickets = purpleDrawTickets + reward.purpleDrawTickets,
    )
}

private fun StudyInventory.addReward(reward: StudyReward): StudyInventory {
    return copy(
        universalNormalFragments = universalNormalFragments + reward.universalNormalFragments,
        douyinFragments = douyinFragments + reward.douyinFragments,
        theaterFragments = theaterFragments + reward.theaterFragments,
        gameFragments = gameFragments + reward.gameFragments,
        videoFragments = videoFragments + reward.videoFragments,
        animeFragments = animeFragments + reward.animeFragments,
    )
}

private fun String.toLocalDateOrToday(): LocalDate =
    toLocalDateOrNull() ?: LocalDate.now()

private fun String.toLocalDateOrNull(): LocalDate? =
    runCatching { LocalDate.parse(this) }.getOrNull()

private fun StudyInventory.unlockFirstIncompleteOutfit(): StudyInventory {
    val target = StudyRules.outfitNames.firstOrNull { outfit ->
        normalFragments.normalOutfitTotal(outfit) < StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT
    } ?: return this
    val completedFragments = StudyRules.outfitParts.fold(normalFragments) { current, part ->
        val key = "normal:$target:$part"
        val missing = StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT - current.normalOutfitTotal(target)
        if (missing <= 0) {
            current
        } else {
            current.plusCount(key, missing)
        }
    }
    return copy(normalFragments = completedFragments)
}

private fun StudyInventory.addDrawResult(result: StudyDrawResult): StudyInventory {
    return when (result.rarity) {
        StudyRarity.Normal -> {
            if (result.fragmentKey.startsWith("normal:universal:")) {
                val amount = result.fragmentKey.substringAfterLast(":").toIntOrNull() ?: 1
                copy(universalNormalFragments = universalNormalFragments + amount)
            } else {
                copy(normalFragments = normalFragments.plusCount(result.fragmentKey, 1))
            }
        }
        StudyRarity.Rare, StudyRarity.Epic, StudyRarity.Rainbow -> when (result.fragmentType) {
            StudyFragmentType.Douyin -> copy(douyinFragments = douyinFragments + 1)
            StudyFragmentType.Theater -> copy(theaterFragments = theaterFragments + 1)
            StudyFragmentType.Game -> copy(gameFragments = gameFragments + 1)
            StudyFragmentType.Video -> copy(videoFragments = videoFragments + 1)
            StudyFragmentType.Anime -> copy(animeFragments = animeFragments + 1)
            null -> this
        }
    }
}

internal fun StudyState.migrateLegacyEntertainmentFragments(): StudyState {
    if (
        inventory.legacySpecialStoryFragments == 0 &&
        inventory.universalRareFragments == 0 &&
        inventory.universalEpicFragments == 0 &&
        inventory.epicFragments == 0 &&
        inventory.rainbowFragments == 0
    ) return this
    return copy(
        inventory = inventory.copy(
            epicFragments = 0,
            rainbowFragments = 0,
            legacySpecialStoryFragments = 0,
            universalRareFragments = 0,
            universalEpicFragments = 0,
        ),
    )
}

private fun StudyEntertainmentReward.fragmentLabel(): String = when (this) {
    StudyEntertainmentReward.Douyin -> "抖音时长券"
    StudyEntertainmentReward.Theater -> "剧场碎片"
    StudyEntertainmentReward.Game -> "游戏畅玩券"
    StudyEntertainmentReward.Video -> "视频解锁卡"
    StudyEntertainmentReward.Anime -> "番剧兑换券"
}

private fun StudyInventory.refreshUnlockStats(): Pair<StudyInventory, Pair<Int, Int>> {
    val newlyUnlockedOutfits = StudyRules.outfitNames.filter { outfit ->
        normalFragments.normalOutfitTotal(outfit) >= StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT
    }.toSet()
    val allUnlockedOutfits = unlockedOutfits + newlyUnlockedOutfits
    return copy(unlockedOutfits = allUnlockedOutfits, unlockedTheaters = emptySet()) to
        (allUnlockedOutfits.size to 0)
}

private fun Map<String, Int>.plusCount(key: String, count: Int): Map<String, Int> {
    return this + (key to ((this[key] ?: 0) + count))
}

private fun StudyInventory.isNormalFragmentFull(key: String): Boolean {
    val outfit = key.split(":").getOrNull(1) ?: return false
    return normalFragments.normalOutfitTotal(outfit) >= StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT
}

private fun Map<String, Int>.normalOutfitTotal(outfit: String): Int {
    val prefix = "normal:$outfit:"
    return entries.sumOf { (key, count) -> if (key.startsWith(prefix)) count else 0 }
}

private fun List<StudyEvent>.addEvent(type: StudyEventType, title: String, detail: String): List<StudyEvent> {
    val event = StudyEvent(
        id = "event-${System.currentTimeMillis()}-${size}",
        type = type,
        title = title,
        detail = detail,
        createdAt = System.currentTimeMillis(),
    )
    return (listOf(event) + this).take(40)
}

private fun StudyShopItemType.toShopItem(id: String): StudyShopItem {
    return when (this) {
        StudyShopItemType.UniversalNormalFragment -> StudyShopItem(id, this, "通用普通碎片 x1", 120)
        StudyShopItemType.DouyinFragment -> StudyShopItem(id, this, "抖音时长券20分钟 x1", 160)
        StudyShopItemType.TheaterFragment -> StudyShopItem(id, this, "剧场碎片 x1", 160)
        StudyShopItemType.GameFragment -> StudyShopItem(id, this, "游戏畅玩券120分钟 x1", 400)
        StudyShopItemType.VideoFragment -> StudyShopItem(id, this, "视频解锁卡 x1", 400)
        StudyShopItemType.AnimeFragment -> StudyShopItem(id, this, "番剧兑换券3小时 x1", 800)
        StudyShopItemType.SingleDrawTicket -> StudyShopItem(id, this, "单抽券 x1", 80)
    }
}

private fun StudyShopItem.toReward(): StudyReward {
    return when (type) {
        StudyShopItemType.UniversalNormalFragment -> StudyReward(universalNormalFragments = 1, title = title)
        StudyShopItemType.DouyinFragment -> StudyReward(douyinFragments = 1, title = title)
        StudyShopItemType.TheaterFragment -> StudyReward(theaterFragments = 1, title = title)
        StudyShopItemType.GameFragment -> StudyReward(gameFragments = 1, title = title)
        StudyShopItemType.VideoFragment -> StudyReward(videoFragments = 1, title = title)
        StudyShopItemType.AnimeFragment -> StudyReward(animeFragments = 1, title = title)
        StudyShopItemType.SingleDrawTicket -> StudyReward(singleDrawTickets = 1, title = title)
    }
}
