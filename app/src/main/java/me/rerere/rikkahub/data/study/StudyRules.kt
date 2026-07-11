package me.rerere.rikkahub.data.study

import java.time.LocalDate
import kotlin.math.max
import kotlin.random.Random

object StudyRules {
    const val SINGLE_DRAW_COST = 100
    const val DISCOUNT_SINGLE_DRAW_COST = 100
    const val TEN_DRAW_COST = 800
    const val TASK_COMPLETE_KUDOS = 50
    const val POMODORO_KUDOS = 50
    const val OFFICIAL_ECONOMY_RESET_VERSION = 2
    const val DATA_LOSS_COMPENSATION_VERSION = 3
    const val NORMAL_FRAGMENTS_PER_OUTFIT = 10
    private const val OVERFLOW_NORMAL_FRAGMENT_KUDOS = 100
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
        StudyLevel(1, 80, "启程", StudyReward(universalNormalFragments = 1, title = "通用普通碎片 x1")),
        StudyLevel(2, 200, "稳定", StudyReward(universalNormalFragments = 1, title = "通用普通碎片 x1")),
        StudyLevel(3, 400, "微光", StudyReward(universalNormalFragments = 2, title = "通用普通碎片 x2")),
        StudyLevel(4, 800, "靠近", StudyReward(douyinFragments = 1, title = "抖音碎片 x1")),
        StudyLevel(5, 1_500, "热望", StudyReward(singleDrawTickets = 2, title = "单抽券 x2")),
        StudyLevel(6, 2_500, "长跑", StudyReward(theaterFragments = 1, title = "剧场碎片 x1")),
        StudyLevel(7, 4_000, "星火", StudyReward(tenDrawTickets = 1, title = "十连抽券 x1")),
        StudyLevel(8, 6_500, "破晓", StudyReward(gameFragments = 1, title = "游戏碎片 x1")),
        StudyLevel(9, 10_000, "执灯", StudyReward(tenDrawTickets = 1, title = "十连抽券 x1")),
        StudyLevel(10, 15_000, "织梦者", StudyReward(videoFragments = 1, title = "视频碎片 x1 + 称号「织梦者」")),
        StudyLevel(11, 22_000, "星桥", StudyReward(theaterFragments = 3, title = "剧场碎片 x3")),
        StudyLevel(12, 32_000, "远航", StudyReward(tenDrawTickets = 2, title = "十连抽券 x2")),
        StudyLevel(13, 45_000, "回响", StudyReward(videoFragments = 2, title = "视频碎片 x2")),
        StudyLevel(14, 60_000, "满愿", StudyReward(title = "任意完整画卷一套")),
        StudyLevel(15, 80_000, "星穹彼岸", StudyReward(title = "称号「星穹彼岸」")),
    )

    val achievements = listOf(
        StudyAchievement("first_companion", "初识陪伴", "累计完成10个番茄钟", StudyReward(singleDrawTickets = 1, title = "单抽券 x1")),
        StudyAchievement("warm_start", "热身完成", "累计完成3个番茄钟", StudyReward(kudos = 50, title = "夸夸值 50")),
        StudyAchievement("todo_slayer", "清单杀手", "累计完成30项待办", StudyReward(kudos = 100, title = "夸夸值 100")),
        StudyAchievement("task_spark", "清单起势", "累计完成10项待办", StudyReward(singleDrawTickets = 1, title = "单抽券 x1")),
        StudyAchievement("perfect_3", "连续全清3天", "连续3天待办全清", StudyReward(universalNormalFragments = 2, title = "通用普通碎片 x2")),
        StudyAchievement("perfect_7", "连续全清7天", "连续7天待办全清", StudyReward(tenDrawTickets = 1, title = "十连抽券 x1")),
        StudyAchievement("deep_work_10h", "坐稳书桌", "累计学习时长10小时", StudyReward(kudos = 100, title = "夸夸值 100")),
        StudyAchievement("time_traveler", "时光旅人", "累计学习时长50小时", StudyReward(kudos = 200, title = "夸夸值 200")),
        StudyAchievement("first_outfit", "第一画卷", "解锁第一套普通画卷", StudyReward(douyinFragments = 1, title = "抖音碎片 x1")),
        StudyAchievement("outfit_collector", "画卷收藏家", "解锁任意3套普通画卷", StudyReward(theaterFragments = 2, title = "剧场碎片 x2")),
        StudyAchievement("theater_open", "剧场开幕", "攒够一次小剧场章节兑换", StudyReward(singleDrawTickets = 2, title = "单抽券 x2")),
        StudyAchievement("lucky_drawer", "好运初现", "累计获得20个抽卡碎片", StudyReward(kudos = 80, title = "夸夸值 80")),
        StudyAchievement("epic_touch", "金光亮起", "获得第一枚金色碎片", StudyReward(singleDrawTickets = 1, kudos = 80, title = "单抽券 x1 + 夸夸值 80")),
        StudyAchievement("mcdonalds_arrival", "第一支视频", "首次解锁视频奖励", StudyReward(kudos = 300, title = "夸夸值 300")),
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
        val reward = StudyReward(tenDrawTickets = 1, title = "数据保护补偿：十连抽券 x1")
        return state.copy(
            wallet = state.wallet.add(reward),
            internalTestGrantVersion = DATA_LOSS_COMPENSATION_VERSION,
            recentEvents = state.recentEvents.addEvent(
                StudyEventType.Fragment,
                "数据保护补偿",
                "十连抽券 x1；之后更新会继续保留夸夸值、碎片、待办完成和番茄记录",
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

    fun toggleTask(
        state: StudyState,
        taskId: String,
        done: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): StudyActionResult {
        val index = state.tasks.indexOfFirst { it.id == taskId }
        if (index < 0 || state.tasks[index].done == done) return StudyActionResult(state)
        val nextTasks = state.tasks.toMutableList()
        nextTasks[index] = nextTasks[index].copy(done = done, completedAt = if (done) nowMillis else null)
        val reward = if (done) {
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
                stats = if (done) state.stats.copy(totalTasksCompleted = state.stats.totalTasksCompleted + 1) else state.stats,
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

    fun completePomodoro(
        state: StudyState,
        minutes: Int = 25,
        random: Random = Random.Default,
    ): StudyActionResult {
        val box = mysteryBox(random)
        val date = state.today.ifBlank { LocalDate.now().toString() }
        val todayRecord = state.dailyStudyRecords[date] ?: StudyDailyRecord()
        val reward = StudyReward(
            kudos = POMODORO_KUDOS,
            mysteryBoxKudos = box.kudos,
            universalNormalFragments = box.universalNormalFragments,
            title = "番茄钟 +$POMODORO_KUDOS + 盲盒待开启",
        )
        return StudyActionResult(
            state = state.copy(
                wallet = state.wallet.add(StudyReward(kudos = POMODORO_KUDOS)),
                inventory = state.inventory.copy(
                    unopenedMysteryBoxes = state.inventory.unopenedMysteryBoxes + box,
                ),
                inactiveStudyDays = 0,
                lastStudyDate = date,
                stats = state.stats.copy(
                    totalPomodoros = state.stats.totalPomodoros + 1,
                    totalStudyMinutes = state.stats.totalStudyMinutes + minutes,
                ),
                dailyStudyRecords = state.dailyStudyRecords + (
                    date to todayRecord.copy(
                        pomodoros = todayRecord.pomodoros + 1,
                        studyMinutes = todayRecord.studyMinutes + minutes,
                    )
                    ),
                recentEvents = state.recentEvents
                    .addEvent(StudyEventType.Pomodoro, "番茄钟完成", "获得 $POMODORO_KUDOS 夸夸值")
                    .addEvent(
                        StudyEventType.MysteryBox,
                        "盲盒待开启",
                        "已放进收藏背包，可以现在开，也可以之后再开",
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
            universalNormalFragments = box.universalNormalFragments,
            title = buildString {
                append("盲盒 +${box.kudos} 夸夸值")
                if (box.universalNormalFragments > 0) {
                    append(" + 通用普通碎片 x${box.universalNormalFragments}")
                }
            },
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
        var overflowKudos = 0
        val results = buildList {
            repeat(drawCount) {
                val result = drawOne(random)
                if (result.rarity == StudyRarity.Normal && inventory.isNormalFragmentFull(result.fragmentKey)) {
                    overflowKudos += OVERFLOW_NORMAL_FRAGMENT_KUDOS
                } else {
                    inventory = inventory.addDrawResult(result)
                }
                add(result)
            }
        }
        val refreshed = inventory.refreshUnlockStats()
        return StudyDrawActionResult(
            state = state.copy(
                wallet = nextWallet.copy(kudos = nextWallet.kudos + overflowKudos),
                inventory = refreshed.first,
                stats = state.stats.copy(
                    unlockedOutfitSets = refreshed.second.first,
                    unlockedTheaters = state.stats.unlockedTheaters,
                ),
                recentEvents = state.recentEvents.addEvent(
                    StudyEventType.Draw,
                    if (drawCount == 10) "十连抽" else "单抽",
                    "获得 ${results.size} 个碎片" +
                        if (overflowKudos > 0) "，溢出转换夸夸值 $overflowKudos" else "",
                ),
            ),
            results = results,
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
        val refreshed = when (level) {
            14 -> state.inventory.unlockFirstIncompleteOutfit()
            else -> state.inventory.addReward(item.reward)
        }.refreshUnlockStats()
        return StudyActionResult(
            state = state.copy(
                wallet = state.wallet.add(item.reward),
                inventory = refreshed.first,
                stats = state.stats.copy(
                    unlockedOutfitSets = refreshed.second.first,
                    unlockedTheaters = state.stats.unlockedTheaters,
                ),
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
                "warm_start" -> state.stats.totalPomodoros >= 3
                "todo_slayer" -> state.stats.totalTasksCompleted >= 30
                "task_spark" -> state.stats.totalTasksCompleted >= 10
                "perfect_3" -> state.longestPerfectStreak >= 3
                "perfect_7" -> state.longestPerfectStreak >= 7
                "deep_work_10h" -> state.stats.totalStudyMinutes >= 600
                "time_traveler" -> state.stats.totalStudyMinutes >= 3_000
                "first_outfit" -> state.stats.unlockedOutfitSets >= 1
                "outfit_collector" -> state.stats.unlockedOutfitSets >= 3
                "theater_open" -> state.inventory.theaterFragments >= 1 || state.stats.unlockedTheaters >= 1
                "lucky_drawer" -> state.inventory.normalFragments.values.sum() +
                    state.inventory.universalNormalFragments +
                    state.inventory.douyinFragments + state.inventory.theaterFragments +
                    state.inventory.gameFragments + state.inventory.videoFragments +
                    state.inventory.animeFragments >= 20
                "epic_touch" -> state.inventory.gameFragments + state.inventory.videoFragments >= 1
                "mcdonalds_arrival" -> state.stats.videoRewardsRedeemed >= 1
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
        val pool = listOf(
            StudyShopItemType.UniversalRareFragment to 12,
            StudyShopItemType.UniversalEpicFragment to 3,
            StudyShopItemType.SingleDrawTicket to 85,
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
        return refreshShopIfNeeded(
            state = state.copy(shopDate = null, manualShopRefreshDate = dateText),
            date = date,
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
        val rewardTitle = "${rewardType.label}时间已兑换"
        return StudyActionResult(
            state = state.copy(
                inventory = inventory,
                recentEvents = state.recentEvents.addEvent(
                    StudyEventType.Entertainment,
                    rewardTitle,
                    "消耗 1 枚${rewardType.fragmentLabel()}碎片",
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
                    wallet = state.wallet.copy(kudos = state.wallet.kudos + OVERFLOW_NORMAL_FRAGMENT_KUDOS),
                    inventory = state.inventory.copy(
                        universalNormalFragments = state.inventory.universalNormalFragments - 1,
                    ),
                    recentEvents = state.recentEvents.addEvent(
                        StudyEventType.Fragment,
                        "碎片溢出转换",
                        "${normalTitle(key)} 已满，转换为夸夸值 $OVERFLOW_NORMAL_FRAGMENT_KUDOS",
                    ),
                ),
                reward = StudyReward(kudos = OVERFLOW_NORMAL_FRAGMENT_KUDOS, title = "碎片已满，转换为夸夸值 $OVERFLOW_NORMAL_FRAGMENT_KUDOS"),
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
        val normalFragments = weighted(listOf(0 to 70, 1 to 28, 2 to 2), random)
        return StudyMysteryBoxReward(kudos = kudos, universalNormalFragments = normalFragments)
    }

    private fun drawOne(random: Random): StudyDrawResult {
        val roll = random.nextDouble()
        return when {
            roll < 0.04 -> {
                val amount = if (random.nextInt(100) < 5) 2 else 1
                StudyDrawResult(StudyRarity.Normal, "normal:universal:$amount", "通用普通碎片 x$amount")
            }
            roll < 0.93 -> {
                val outfit = outfitNames[random.nextInt(outfitNames.size)]
                val part = outfitParts[random.nextInt(outfitParts.size)]
                StudyDrawResult(StudyRarity.Normal, "normal:$outfit:$part", "$outfit-$part 碎片")
            }
            roll < 0.97 -> if (random.nextInt(2) == 0) {
                StudyDrawResult(StudyRarity.Rare, "rare:douyin", "抖音碎片", StudyFragmentType.Douyin)
            } else {
                StudyDrawResult(StudyRarity.Rare, "rare:theater", "剧场碎片", StudyFragmentType.Theater)
            }
            roll < 0.99 -> if (random.nextInt(2) == 0) {
                StudyDrawResult(StudyRarity.Epic, "epic:game", "游戏碎片", StudyFragmentType.Game)
            } else {
                StudyDrawResult(StudyRarity.Epic, "epic:video", "视频碎片", StudyFragmentType.Video)
            }
            else -> StudyDrawResult(StudyRarity.Rainbow, "rainbow:anime", "动漫碎片", StudyFragmentType.Anime)
        }
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
    StudyEntertainmentReward.Douyin -> "抖音"
    StudyEntertainmentReward.Theater -> "剧场"
    StudyEntertainmentReward.Game -> "游戏"
    StudyEntertainmentReward.Video -> "视频"
    StudyEntertainmentReward.Anime -> "动漫"
}

private fun StudyInventory.refreshUnlockStats(): Pair<StudyInventory, Pair<Int, Int>> {
    val unlockedOutfits = StudyRules.outfitNames.filter { outfit ->
        normalFragments.normalOutfitTotal(outfit) >= StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT
    }.toSet()
    return copy(unlockedOutfits = unlockedOutfits, unlockedTheaters = emptySet()) to
        (unlockedOutfits.size to 0)
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
        StudyShopItemType.UniversalRareFragment -> StudyShopItem(id, this, "剧场碎片 x1", 160)
        StudyShopItemType.UniversalEpicFragment -> StudyShopItem(id, this, "视频碎片 x1", 400)
        StudyShopItemType.SingleDrawTicket -> StudyShopItem(id, this, "单抽券 x1", 80)
    }
}

private fun StudyShopItem.toReward(): StudyReward {
    return when (type) {
        StudyShopItemType.UniversalNormalFragment -> StudyReward(universalNormalFragments = 1, title = title)
        StudyShopItemType.UniversalRareFragment -> StudyReward(theaterFragments = 1, title = title)
        StudyShopItemType.UniversalEpicFragment -> StudyReward(videoFragments = 1, title = title)
        StudyShopItemType.SingleDrawTicket -> StudyReward(singleDrawTickets = 1, title = title)
    }
}
