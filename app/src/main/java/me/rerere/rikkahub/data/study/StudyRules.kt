package me.rerere.rikkahub.data.study

import java.time.LocalDate
import kotlin.math.max
import kotlin.random.Random

object StudyRules {
    const val SINGLE_DRAW_COST = 100
    const val TEN_DRAW_COST = 800

    val outfitNames = listOf(
        "晨光书桌",
        "海盐自习",
        "樱花讲义",
        "星轨图书馆",
        "暖灯错题",
        "雨后窗边",
        "云朵白衬",
        "深夜冲刺",
        "青柠计划",
        "金榜花火",
    )

    val outfitParts = listOf("服装", "饰品", "发型", "场景", "光影氛围", "动态姿势")

    val theaterNames = listOf(
        "晚自习约定",
        "错题本私语",
        "雨天图书馆",
        "清晨第一杯水",
        "背单词小胜利",
        "月光下的计划",
        "倒计时拥抱",
        "考场前夜",
        "便利店热牛奶",
        "天台吹风",
        "上岸后的信",
        "星穹彼岸",
    )

    val levels = listOf(
        StudyLevel(1, 80, "启程", StudyReward(universalNormalFragments = 1, title = "通用普通碎片 x1")),
        StudyLevel(2, 200, "稳定", StudyReward(universalNormalFragments = 1, title = "通用普通碎片 x1")),
        StudyLevel(3, 400, "微光", StudyReward(universalNormalFragments = 2, title = "通用普通碎片 x2")),
        StudyLevel(4, 800, "靠近", StudyReward(universalRareFragments = 1, title = "通用稀有碎片 x1")),
        StudyLevel(5, 1_500, "热望", StudyReward(singleDrawTickets = 2, title = "单抽券 x2")),
        StudyLevel(6, 2_500, "长跑", StudyReward(universalRareFragments = 1, title = "通用稀有碎片 x1")),
        StudyLevel(7, 4_000, "星火", StudyReward(tenDrawTickets = 1, title = "十连抽券 x1")),
        StudyLevel(8, 6_500, "破晓", StudyReward(universalEpicFragments = 1, title = "通用史诗碎片 x1")),
        StudyLevel(9, 10_000, "执灯", StudyReward(tenDrawTickets = 1, title = "十连抽券 x1")),
        StudyLevel(10, 15_000, "织梦者", StudyReward(universalEpicFragments = 1, title = "通用史诗碎片 x1 + 称号「织梦者」")),
        StudyLevel(11, 22_000, "星桥", StudyReward(universalRareFragments = 3, title = "通用稀有碎片 x3")),
        StudyLevel(12, 32_000, "远航", StudyReward(tenDrawTickets = 2, title = "十连抽券 x2")),
        StudyLevel(13, 45_000, "回响", StudyReward(universalEpicFragments = 2, title = "通用史诗碎片 x2")),
        StudyLevel(14, 60_000, "满愿", StudyReward(title = "任意完整套装一套")),
        StudyLevel(15, 80_000, "星穹彼岸", StudyReward(title = "称号「星穹彼岸」+ 永久单抽9折")),
    )

    val achievements = listOf(
        StudyAchievement("first_companion", "初识陪伴", "累计完成10个番茄钟", StudyReward(singleDrawTickets = 1, title = "单抽券 x1")),
        StudyAchievement("warm_start", "热身完成", "累计完成3个番茄钟", StudyReward(kudos = 80, title = "夸夸值 80")),
        StudyAchievement("todo_slayer", "清单杀手", "累计完成30项待办", StudyReward(kudos = 150, title = "夸夸值 150")),
        StudyAchievement("task_spark", "清单起势", "累计完成10项待办", StudyReward(singleDrawTickets = 1, title = "单抽券 x1")),
        StudyAchievement("perfect_3", "连续全清3天", "连续3天待办全清", StudyReward(universalNormalFragments = 2, title = "通用普通碎片 x2")),
        StudyAchievement("perfect_7", "连续全清7天", "连续7天待办全清", StudyReward(tenDrawTickets = 1, title = "十连抽券 x1")),
        StudyAchievement("deep_work_10h", "坐稳书桌", "累计学习时长10小时", StudyReward(kudos = 180, title = "夸夸值 180")),
        StudyAchievement("time_traveler", "时光旅人", "累计学习时长50小时", StudyReward(kudos = 300, title = "夸夸值 300")),
        StudyAchievement("first_outfit", "第一套装", "解锁第一套普通套装", StudyReward(universalRareFragments = 1, title = "通用稀有碎片 x1")),
        StudyAchievement("outfit_collector", "套装收藏家", "解锁任意3套普通套装", StudyReward(universalRareFragments = 2, title = "稀有碎片 x2")),
        StudyAchievement("theater_open", "剧场开幕", "解锁第一部小剧场", StudyReward(singleDrawTickets = 3, title = "单抽券 x3")),
        StudyAchievement("lucky_drawer", "好运初现", "累计获得20个抽卡碎片", StudyReward(kudos = 120, title = "夸夸值 120")),
        StudyAchievement("epic_touch", "金光一闪", "获得第一枚麦当劳碎片", StudyReward(universalEpicFragments = 1, title = "通用史诗碎片 x1")),
        StudyAchievement("mcdonalds_arrival", "麦门降临", "首次兑换麦当劳", StudyReward(kudos = 500, title = "夸夸值 500")),
    )

    fun rolloverToDate(state: StudyState, date: LocalDate = LocalDate.now()): StudyState {
        val dateText = date.toString()
        if (state.today == dateText) return state
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
        )
        return applyInactivityPenalty(rolled).state
    }

    fun signIn(state: StudyState, date: LocalDate = LocalDate.now()): StudyActionResult {
        val dateText = date.toString()
        if (state.lastSignInDate == dateText) {
            return StudyActionResult(state)
        }
        val previous = state.lastSignInDate?.let(LocalDate::parse)
        val streak = if (previous == date.minusDays(1)) state.signInStreak + 1 else 1
        val kudos = when {
            streak >= 5 -> 75
            streak == 3 -> 50
            else -> 25
        }
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
        val reward = if (done) StudyReward(kudos = 100, title = "完成待办 +100") else StudyReward()
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
        val reward = StudyReward(kudos = 50, mysteryBoxKudos = box, title = "番茄钟 +50，盲盒 +$box")
        val totalReward = StudyReward(kudos = reward.kudos + reward.mysteryBoxKudos)
        return StudyActionResult(
            state = state.copy(
                wallet = state.wallet.add(totalReward),
                inactiveStudyDays = 0,
                lastStudyDate = state.today,
                stats = state.stats.copy(
                    totalPomodoros = state.stats.totalPomodoros + 1,
                    totalStudyMinutes = state.stats.totalStudyMinutes + minutes,
                ),
                recentEvents = state.recentEvents
                    .addEvent(StudyEventType.Pomodoro, "番茄钟完成", "获得 50 夸夸值")
                    .addEvent(StudyEventType.MysteryBox, "盲盒开启", "获得 $box 夸夸值"),
            ),
            reward = reward,
        )
    }

    fun draw(state: StudyState, count: Int, random: Random = Random.Default): StudyDrawActionResult {
        val drawCount = if (count >= 10) 10 else 1
        val nextWallet = when {
            drawCount == 1 && state.wallet.singleDrawTickets > 0 ->
                state.wallet.copy(singleDrawTickets = state.wallet.singleDrawTickets - 1)
            drawCount == 10 && state.wallet.tenDrawTickets > 0 ->
                state.wallet.copy(tenDrawTickets = state.wallet.tenDrawTickets - 1)
            state.wallet.kudos >= if (drawCount == 10) TEN_DRAW_COST else SINGLE_DRAW_COST ->
                state.wallet.copy(kudos = state.wallet.kudos - if (drawCount == 10) TEN_DRAW_COST else SINGLE_DRAW_COST)
            else -> return StudyDrawActionResult(state, emptyList())
        }
        var inventory = state.inventory
        val results = buildList {
            repeat(drawCount) {
                val result = drawOne(random)
                inventory = inventory.addDrawResult(result)
                add(result)
            }
        }
        val refreshed = inventory.refreshUnlockStats()
        return StudyDrawActionResult(
            state = state.copy(
                wallet = nextWallet,
                inventory = refreshed.first,
                stats = state.stats.copy(
                    unlockedOutfitSets = refreshed.second.first,
                    unlockedTheaters = refreshed.second.second,
                ),
                recentEvents = state.recentEvents.addEvent(
                    StudyEventType.Draw,
                    if (drawCount == 10) "十连抽" else "单抽",
                    "获得 ${results.size} 个碎片",
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
            SuperMomentChoice.NormalFragments -> StudyReward(universalNormalFragments = 5, title = "通用普通碎片 x5")
            SuperMomentChoice.RareFragment -> StudyReward(universalRareFragments = 1, title = "通用稀有碎片 x1")
        }
        val fixed = StudyReward(kudos = 200, tenDrawTickets = 1, title = "十连抽券 x1 + 夸夸值 200")
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
                "theater_open" -> state.stats.unlockedTheaters >= 1
                "lucky_drawer" -> state.inventory.normalFragments.values.sum() +
                    state.inventory.rareFragments.values.sum() + state.inventory.epicFragments >= 20
                "epic_touch" -> state.inventory.epicFragments >= 1
                "mcdonalds_arrival" -> state.stats.mcdonaldsRedeemed >= 1
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
            StudyShopItemType.UniversalNormalFragment to 65,
            StudyShopItemType.UniversalRareFragment to 12,
            StudyShopItemType.UniversalEpicFragment to 3,
            StudyShopItemType.SingleDrawTicket to 20,
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

    fun redeemMcDonalds(state: StudyState): StudyActionResult {
        if (state.inventory.epicFragments < 2) return StudyActionResult(state)
        return StudyActionResult(
            state = state.copy(
                inventory = state.inventory.copy(epicFragments = state.inventory.epicFragments - 2),
                stats = state.stats.copy(mcdonaldsRedeemed = state.stats.mcdonaldsRedeemed + 1),
                recentEvents = state.recentEvents.addEvent(
                    StudyEventType.McDonalds,
                    "麦当劳奖励",
                    "角色帮你安排一顿小奖励",
                ),
            ),
            reward = StudyReward(title = "麦当劳点餐机会 x1"),
        )
    }

    fun useUniversalNormalFragment(state: StudyState, key: String): StudyActionResult {
        if (state.inventory.universalNormalFragments <= 0 || !key.startsWith("normal:")) return StudyActionResult(state)
        val inventory = state.inventory.copy(
            universalNormalFragments = state.inventory.universalNormalFragments - 1,
            normalFragments = state.inventory.normalFragments.plusCount(key, 1),
        ).refreshUnlockStats().first
        return StudyActionResult(
            state = state.copy(
                inventory = inventory,
                stats = state.stats.copy(
                    unlockedOutfitSets = inventory.unlockedOutfits.size,
                    unlockedTheaters = inventory.unlockedTheaters.size,
                ),
                recentEvents = state.recentEvents.addEvent(StudyEventType.Fragment, "使用通用普通碎片", normalTitle(key)),
            ),
            reward = StudyReward(title = "已补到 ${normalTitle(key)}"),
        )
    }

    fun useUniversalRareFragment(state: StudyState, key: String): StudyActionResult {
        if (state.inventory.universalRareFragments <= 0 || !key.startsWith("rare:")) return StudyActionResult(state)
        val inventory = state.inventory.copy(
            universalRareFragments = state.inventory.universalRareFragments - 1,
            rareFragments = state.inventory.rareFragments.plusCount(key, 1),
        ).refreshUnlockStats().first
        return StudyActionResult(
            state = state.copy(
                inventory = inventory,
                stats = state.stats.copy(
                    unlockedOutfitSets = inventory.unlockedOutfits.size,
                    unlockedTheaters = inventory.unlockedTheaters.size,
                ),
                recentEvents = state.recentEvents.addEvent(StudyEventType.Fragment, "使用通用稀有碎片", rareTitle(key)),
            ),
            reward = StudyReward(title = "已补到 ${rareTitle(key)}"),
        )
    }

    fun useUniversalEpicFragment(state: StudyState): StudyActionResult {
        if (state.inventory.universalEpicFragments <= 0) return StudyActionResult(state)
        return StudyActionResult(
            state = state.copy(
                inventory = state.inventory.copy(
                    universalEpicFragments = state.inventory.universalEpicFragments - 1,
                    epicFragments = state.inventory.epicFragments + 1,
                ),
                recentEvents = state.recentEvents.addEvent(StudyEventType.Fragment, "使用通用史诗碎片", "麦当劳碎片 +1"),
            ),
            reward = StudyReward(title = "麦当劳碎片 +1"),
        )
    }

    fun bestNormalFragmentTarget(state: StudyState): String? {
        return outfitNames.flatMap { outfit ->
            outfitParts.map { part -> "normal:$outfit:$part" }
        }.filter { key ->
            (state.inventory.normalFragments[key] ?: 0) < 4
        }.maxByOrNull { key -> state.inventory.normalFragments[key] ?: 0 }
    }

    fun bestRareFragmentTarget(state: StudyState): String? {
        return theaterNames.map { "rare:$it" }
            .filter { key -> (state.inventory.rareFragments[key] ?: 0) < 5 }
            .maxByOrNull { key -> state.inventory.rareFragments[key] ?: 0 }
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

    fun normalTitle(key: String): String {
        val parts = key.split(":")
        return if (parts.size >= 3) "${parts[1]}-${parts[2]}" else key
    }

    fun rareTitle(key: String): String {
        return key.removePrefix("rare:")
    }

    private fun mysteryBox(random: Random): Int {
        return weighted(listOf(15 to 40, 25 to 30, 50 to 15, 100 to 4, 200 to 1), random)
    }

    private fun drawOne(random: Random): StudyDrawResult {
        val roll = random.nextDouble()
        return when {
            roll < 0.85 -> {
                val outfit = outfitNames[random.nextInt(outfitNames.size)]
                val part = outfitParts[random.nextInt(outfitParts.size)]
                StudyDrawResult(StudyRarity.Normal, "normal:$outfit:$part", "$outfit-$part 碎片")
            }
            roll < 0.97 -> {
                val theater = theaterNames[random.nextInt(theaterNames.size)]
                StudyDrawResult(StudyRarity.Rare, "rare:$theater", "$theater 剧场碎片")
            }
            else -> StudyDrawResult(StudyRarity.Epic, "epic:mcdonalds", "麦当劳碎片")
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
        universalRareFragments = universalRareFragments + other.universalRareFragments,
        universalEpicFragments = universalEpicFragments + other.universalEpicFragments,
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
        universalRareFragments = universalRareFragments + reward.universalRareFragments,
        universalEpicFragments = universalEpicFragments + reward.universalEpicFragments,
    )
}

private fun StudyInventory.addDrawResult(result: StudyDrawResult): StudyInventory {
    return when (result.rarity) {
        StudyRarity.Normal -> copy(normalFragments = normalFragments.plusCount(result.fragmentKey, 1))
        StudyRarity.Rare -> copy(rareFragments = rareFragments.plusCount(result.fragmentKey, 1))
        StudyRarity.Epic -> copy(epicFragments = epicFragments + 1)
    }
}

private fun StudyInventory.refreshUnlockStats(): Pair<StudyInventory, Pair<Int, Int>> {
    val unlockedOutfits = StudyRules.outfitNames.filter { outfit ->
        StudyRules.outfitParts.all { part -> (normalFragments["normal:$outfit:$part"] ?: 0) >= 4 }
    }.toSet()
    val unlockedTheaters = StudyRules.theaterNames.filter { theater ->
        (rareFragments["rare:$theater"] ?: 0) >= 5
    }.toSet()
    return copy(unlockedOutfits = unlockedOutfits, unlockedTheaters = unlockedTheaters) to
        (unlockedOutfits.size to unlockedTheaters.size)
}

private fun Map<String, Int>.plusCount(key: String, count: Int): Map<String, Int> {
    return this + (key to ((this[key] ?: 0) + count))
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
        StudyShopItemType.UniversalNormalFragment -> StudyShopItem(id, this, "通用普通碎片 x1", 90)
        StudyShopItemType.UniversalRareFragment -> StudyShopItem(id, this, "通用稀有碎片 x1", 280)
        StudyShopItemType.UniversalEpicFragment -> StudyShopItem(id, this, "通用史诗碎片 x1", 750)
        StudyShopItemType.SingleDrawTicket -> StudyShopItem(id, this, "单抽券 x1", 90)
    }
}

private fun StudyShopItem.toReward(): StudyReward {
    return when (type) {
        StudyShopItemType.UniversalNormalFragment -> StudyReward(universalNormalFragments = 1, title = title)
        StudyShopItemType.UniversalRareFragment -> StudyReward(universalRareFragments = 1, title = title)
        StudyShopItemType.UniversalEpicFragment -> StudyReward(universalEpicFragments = 1, title = title)
        StudyShopItemType.SingleDrawTicket -> StudyReward(singleDrawTickets = 1, title = title)
    }
}
