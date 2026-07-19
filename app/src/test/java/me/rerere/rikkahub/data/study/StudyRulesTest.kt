package me.rerere.rikkahub.data.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

class StudyRulesTest {
    @Test
    fun `sign in gives base streak milestone rewards once per day`() {
        val day1 = LocalDate.of(2026, 6, 28)
        val afterDay1 = StudyRules.signIn(StudyState(), day1)
        val afterDay2 = StudyRules.signIn(afterDay1.state, day1.plusDays(1))
        val afterDay3 = StudyRules.signIn(afterDay2.state, day1.plusDays(2))
        val afterDay5 = StudyRules.signIn(
            StudyRules.signIn(afterDay3.state, day1.plusDays(3)).state,
            day1.plusDays(4),
        )
        val duplicate = StudyRules.signIn(afterDay5.state, day1.plusDays(4))

        assertEquals(50, afterDay1.reward.kudos)
        assertEquals(50, afterDay2.reward.kudos)
        assertEquals(50, afterDay3.reward.kudos)
        assertEquals(50, afterDay5.reward.kudos)
        assertEquals(0, duplicate.reward.kudos)
        assertEquals(5, afterDay5.state.signInStreak)
    }

    @Test
    fun `role confirmed early sleep grants five hundred kudos once per day`() {
        val date = LocalDate.of(2026, 7, 14)
        val first = StudyRules.claimSleepHabitReward(
            state = StudyState(today = date.toString()),
            habit = StudySleepHabit.EarlySleep,
            nowMillis = millisAt(2026, 7, 14, 10, 0),
            zoneId = TEST_ZONE,
            assistantName = "露露",
            decisionReason = "你一点二十就睡了，按你的作息确实算早",
            reportedTime = LocalTime.of(1, 20),
        )
        val duplicate = StudyRules.claimSleepHabitReward(
            state = first.state,
            habit = StudySleepHabit.EarlySleep,
            nowMillis = millisAt(2026, 7, 14, 10, 5),
            zoneId = TEST_ZONE,
            assistantName = "露露",
        )

        assertEquals(500, first.reward.kudos)
        assertEquals(500, first.state.wallet.kudos)
        assertEquals(500, first.state.wallet.totalKudosEarned)
        assertEquals(0, duplicate.reward.kudos)
        assertEquals(500, duplicate.state.wallet.kudos)
        assertTrue(StudyRules.hasClaimedSleepHabitReward(duplicate.state, StudySleepHabit.EarlySleep, date))
        assertTrue(first.state.recentEvents.first().detail.contains("露露"))
    }

    @Test
    fun `role confirmed early rise grants one ten draw ticket once per day`() {
        val date = LocalDate.of(2026, 7, 14)
        val first = StudyRules.claimSleepHabitReward(
            state = StudyState(today = date.toString()),
            habit = StudySleepHabit.EarlyRise,
            nowMillis = millisAt(2026, 7, 14, 10, 0),
            zoneId = TEST_ZONE,
            assistantName = "露露",
            decisionReason = "你九点二十已经起床，按你的作息算早",
            reportedTime = LocalTime.of(9, 20),
        )
        val duplicate = StudyRules.claimSleepHabitReward(
            state = first.state,
            habit = StudySleepHabit.EarlyRise,
            nowMillis = millisAt(2026, 7, 14, 10, 5),
            zoneId = TEST_ZONE,
            assistantName = "露露",
        )

        assertEquals(1, first.reward.tenDrawTickets)
        assertEquals(1, first.state.wallet.tenDrawTickets)
        assertEquals(0, duplicate.reward.tenDrawTickets)
        assertEquals(1, duplicate.state.wallet.tenDrawTickets)
        assertTrue(StudyRules.hasClaimedSleepHabitReward(duplicate.state, StudySleepHabit.EarlyRise, date))
    }

    @Test
    fun `early sleep reward is rejected while user is still chatting at four in the morning`() {
        val date = LocalDate.of(2026, 7, 14)
        val result = StudyRules.claimSleepHabitReward(
            state = StudyState(today = date.toString()),
            habit = StudySleepHabit.EarlySleep,
            nowMillis = millisAt(2026, 7, 14, 4, 0),
            zoneId = TEST_ZONE,
            assistantName = "露露",
            decisionReason = "用户说自己睡得很早",
            reportedTime = LocalTime.of(1, 10),
        )

        assertFalse(result.granted)
        assertEquals(0, result.state.wallet.kudos)
        assertTrue(result.reason.contains("凌晨"))
    }

    @Test
    fun `sleep reward is rejected when role provides no judgment reason`() {
        val result = StudyRules.claimSleepHabitReward(
            state = StudyState(today = "2026-07-14"),
            habit = StudySleepHabit.EarlyRise,
            nowMillis = millisAt(2026, 7, 14, 8, 0),
            zoneId = TEST_ZONE,
            assistantName = "露露",
            reportedTime = LocalTime.of(8, 30),
        )

        assertFalse(result.granted)
        assertTrue(result.reason.contains("判断"))
    }

    @Test
    fun `personal sleep baseline accepts one twenty but rejects two oclock`() {
        val accepted = StudyRules.claimSleepHabitReward(
            state = StudyState(today = "2026-07-14"),
            habit = StudySleepHabit.EarlySleep,
            nowMillis = millisAt(2026, 7, 14, 10, 0),
            zoneId = TEST_ZONE,
            assistantName = "露露",
            decisionReason = "一点二十已经比你的平时作息早",
            reportedTime = LocalTime.of(1, 20),
        )
        val rejected = StudyRules.claimSleepHabitReward(
            state = StudyState(today = "2026-07-14"),
            habit = StudySleepHabit.EarlySleep,
            nowMillis = millisAt(2026, 7, 14, 10, 0),
            zoneId = TEST_ZONE,
            assistantName = "露露",
            decisionReason = "用户要求奖励",
            reportedTime = LocalTime.of(2, 0),
        )

        assertTrue(accepted.granted)
        assertFalse(rejected.granted)
        assertTrue(rejected.reason.contains("01:30"))
    }

    @Test
    fun `personal wake baseline accepts nine twenty but rejects ten oclock`() {
        val accepted = StudyRules.claimSleepHabitReward(
            state = StudyState(today = "2026-07-14"),
            habit = StudySleepHabit.EarlyRise,
            nowMillis = millisAt(2026, 7, 14, 10, 30),
            zoneId = TEST_ZONE,
            assistantName = "露露",
            decisionReason = "九点二十已经达到你的早起标准",
            reportedTime = LocalTime.of(9, 20),
        )
        val rejected = StudyRules.claimSleepHabitReward(
            state = StudyState(today = "2026-07-14"),
            habit = StudySleepHabit.EarlyRise,
            nowMillis = millisAt(2026, 7, 14, 10, 30),
            zoneId = TEST_ZONE,
            assistantName = "露露",
            decisionReason = "用户要求奖励",
            reportedTime = LocalTime.of(10, 0),
        )

        assertTrue(accepted.granted)
        assertFalse(rejected.granted)
        assertTrue(rejected.reason.contains("09:30"))
    }

    @Test
    fun `completing a daily task gives kudos and can trigger super moment`() {
        val state = StudyState(
            today = "2026-06-30",
            tasks = listOf(
                StudyTask(id = "a", title = "English"),
                StudyTask(id = "b", title = "Politics"),
            )
        )

        val first = StudyRules.toggleTask(state, "a", true)
        val second = StudyRules.toggleTask(first.state, "b", true)

        assertEquals(50, first.reward.kudos)
        assertFalse(first.state.superMomentAvailable)
        assertEquals(50, second.reward.kudos)
        assertTrue(second.state.superMomentAvailable)
        assertEquals(100, second.state.wallet.kudos)
    }

    @Test
    fun `task completion reward is granted only once after reopening`() {
        val state = StudyState(
            today = "2026-07-11",
            tasks = listOf(StudyTask(id = "law", title = "Law chapter one")),
        )

        val first = StudyRules.toggleTask(state, "law", true, nowMillis = 100L)
        val reopened = StudyRules.toggleTask(first.state, "law", false, nowMillis = 200L)
        val completedAgain = StudyRules.toggleTask(reopened.state, "law", true, nowMillis = 300L)

        assertEquals(50, first.reward.kudos)
        assertEquals(0, completedAgain.reward.kudos)
        assertEquals(50, completedAgain.state.wallet.kudos)
        assertEquals(1, completedAgain.state.stats.totalTasksCompleted)
        assertTrue(completedAgain.state.tasks.single().completionRewardClaimed)
    }

    @Test
    fun `pomodoro completion settles kudos by accumulated five minute blocks`() {
        val result = StudyRules.completePomodoro(
            state = StudyState(today = "2026-06-30"),
            minutes = 18,
            random = Random(1),
        )

        assertEquals(300, result.reward.kudos)
        assertEquals(300, result.state.wallet.kudos)
        assertEquals(3, result.state.pendingRewardMinutes)
        assertEquals(0, result.state.inventory.unopenedMysteryBoxes.size)
        assertEquals(1, result.state.stats.totalPomodoros)
        assertEquals(18, result.state.stats.totalStudyMinutes)
    }

    @Test
    fun `pomodoro completion records daily and weekly study time`() {
        val monday = StudyRules.completePomodoro(
            state = StudyState(today = "2026-07-06"),
            minutes = 25,
            random = Random(1),
        ).state
        val tuesday = StudyRules.completePomodoro(
            state = monday.copy(today = "2026-07-07"),
            minutes = 15,
            random = Random(2),
        ).state
        val overview = StudyRules.studyTimeOverview(tuesday, LocalDate.of(2026, 7, 7))

        assertEquals(15, overview.todayMinutes)
        assertEquals(1, overview.todayPomodoros)
        assertEquals(40, overview.weekMinutes)
        assertEquals(2, overview.weekPomodoros)
    }

    @Test
    fun `opening a stored mystery box applies its reward`() {
        val completed = StudyState(
            today = "2026-06-30",
            inventory = StudyInventory(
                unopenedMysteryBoxes = listOf(StudyMysteryBoxReward(kudos = 50)),
            ),
        )

        val opened = StudyRules.openMysteryBox(completed)

        assertEquals(0, opened.state.inventory.unopenedMysteryBoxes.size)
        assertEquals(opened.reward.kudos, opened.state.wallet.kudos)
        assertEquals(opened.reward.universalNormalFragments, opened.state.inventory.universalNormalFragments)
    }

    @Test
    fun `legacy mystery boxes cannot grant universal fragments`() {
        val state = StudyState(
            inventory = StudyInventory(
                unopenedMysteryBoxes = listOf(StudyMysteryBoxReward(kudos = 50, universalNormalFragments = 2)),
            ),
        )

        val opened = StudyRules.openMysteryBox(state)

        assertEquals(50, opened.reward.kudos)
        assertEquals(0, opened.reward.universalNormalFragments)
        assertEquals(0, opened.state.inventory.universalNormalFragments)
        assertFalse(opened.reward.title.contains("通用"))
    }

    @Test
    fun `universal fragments are no longer configured as new rewards`() {
        assertTrue(StudyRules.levels.none { it.reward.universalNormalFragments > 0 })
        assertTrue(StudyRules.achievements.none { it.reward.universalNormalFragments > 0 })
        val superMomentReward = StudyRules.claimSuperMoment(
            StudyState(),
            SuperMomentChoice.NormalFragments,
        ).reward
        assertEquals(0, superMomentReward.universalNormalFragments)
    }

    @Test
    fun `draws consume coupons before kudos and add fragments`() {
        val state = StudyState(
            wallet = StudyWallet(kudos = 800, singleDrawTickets = 1, tenDrawTickets = 1)
        )

        val single = StudyRules.draw(state, count = 1, random = Random(3))
        val ten = StudyRules.draw(single.state, count = 10, random = Random(4))
        val paidTen = StudyRules.draw(ten.state, count = 10, random = Random(5))

        assertEquals(800, single.state.wallet.kudos)
        assertEquals(0, single.state.wallet.singleDrawTickets)
        assertEquals(800, ten.state.wallet.kudos)
        assertEquals(0, ten.state.wallet.tenDrawTickets)
        assertEquals(0, paidTen.state.wallet.kudos)
        assertEquals(21, paidTen.results.size + ten.results.size + single.results.size)
    }

    @Test
    fun `single draw costs one hundred even after discount milestone`() {
        val state = StudyState(
            wallet = StudyWallet(kudos = 200, totalKudosEarned = 80_000)
        )

        val drawn = StudyRules.draw(state, count = 1, random = Random(3))

        assertEquals(100, StudyRules.SINGLE_DRAW_COST)
        assertEquals(100, StudyRules.DISCOUNT_SINGLE_DRAW_COST)
        assertEquals(100, drawn.state.wallet.kudos)
        assertEquals(1, drawn.results.size)
    }

    @Test
    fun `ten draw costs eight hundred kudos`() {
        val state = StudyState(wallet = StudyWallet(kudos = 900))

        val drawn = StudyRules.draw(state, count = 10, random = Random(4))

        assertEquals(800, StudyRules.TEN_DRAW_COST)
        assertEquals(100, drawn.state.wallet.kudos)
        assertEquals(10, drawn.results.size)
    }

    @Test
    fun `regular pool forces a purple result before thirty consecutive normal draws`() {
        val alwaysNormal = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextDouble(): Double = 0.0
            override fun nextInt(until: Int): Int = 0
        }
        var state = StudyState(wallet = StudyWallet(tenDrawTickets = 4))
        val results = buildList {
            repeat(4) {
                val draw = StudyRules.draw(state, count = 10, random = alwaysNormal)
                state = draw.state
                addAll(draw.results)
            }
        }

        assertEquals(40, results.size)
        assertEquals(1, results.count { it.rarity == StudyRarity.Rare })
        assertEquals(0, results.count { it.rarity == StudyRarity.Epic || it.rarity == StudyRarity.Rainbow })
        assertEquals(10, state.drawsSinceNonNormal)
    }

    @Test
    fun `level lookup uses cumulative kudos and unclaimed level rewards`() {
        val state = StudyState(wallet = StudyWallet(totalKudosEarned = 4_000))

        assertEquals(7, StudyRules.currentLevel(state).level)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), StudyRules.claimableLevels(state).map { it.level })
    }

    @Test
    fun `super moment grants fixed reward and selected bonus once`() {
        val state = StudyState(
            superMomentAvailable = true,
            wallet = StudyWallet(kudos = 100),
        )

        val claimed = StudyRules.claimSuperMoment(state, SuperMomentChoice.NormalFragments)
        val duplicate = StudyRules.claimSuperMoment(claimed.state, SuperMomentChoice.RareFragment)

        assertEquals(100, claimed.state.wallet.kudos)
        assertEquals(1, claimed.state.wallet.tenDrawTickets)
        assertEquals(0, claimed.state.inventory.universalNormalFragments)
        assertEquals(0, duplicate.reward.kudos)
        assertFalse(claimed.state.superMomentAvailable)
    }

    @Test
    fun `official economy marker never clears local study assets`() {
        val state = StudyState(
            wallet = StudyWallet(kudos = 999, totalKudosEarned = 1_500, singleDrawTickets = 3, tenDrawTickets = 2),
            inventory = StudyInventory(
                normalFragments = mapOf("normal:星穹图书馆:专属碎片" to 4),
                rareFragments = mapOf("rare:any" to 2),
                epicFragments = 3,
                rainbowFragments = 2,
                universalNormalFragments = 5,
                universalRareFragments = 4,
                universalEpicFragments = 1,
                unlockedOutfits = setOf("星穹图书馆"),
                unopenedMysteryBoxes = listOf(StudyMysteryBoxReward(50, 2)),
            ),
            claimedLevelRewards = setOf(1, 2),
            claimedAchievementIds = setOf("warm_start"),
            shopDate = "2026-07-02",
            shopItems = listOf(StudyShopItem("a", StudyShopItemType.SingleDrawTicket, "单抽券 x1", 80)),
            purchasedShopItemIds = setOf("a"),
            internalTestGrantVersion = 1,
        )

        val reset = StudyRules.resetEconomyForOfficialStart(state)
        val duplicate = StudyRules.resetEconomyForOfficialStart(reset)

        assertEquals(999, reset.wallet.kudos)
        assertEquals(1_500, reset.wallet.totalKudosEarned)
        assertEquals(3, reset.wallet.singleDrawTickets)
        assertEquals(2, reset.wallet.tenDrawTickets)
        assertEquals(4, reset.inventory.normalFragments.getValue("normal:星穹图书馆:专属碎片"))
        assertEquals(2, reset.inventory.rareFragments.getValue("rare:any"))
        assertEquals(3, reset.inventory.epicFragments)
        assertEquals(2, reset.inventory.rainbowFragments)
        assertEquals(5, reset.inventory.universalNormalFragments)
        assertEquals(4, reset.inventory.universalRareFragments)
        assertEquals(1, reset.inventory.universalEpicFragments)
        assertTrue(reset.inventory.unlockedOutfits.contains("星穹图书馆"))
        assertEquals(1, reset.inventory.unopenedMysteryBoxes.size)
        assertTrue(reset.claimedLevelRewards.containsAll(setOf(1, 2)))
        assertTrue(reset.claimedAchievementIds.contains("warm_start"))
        assertEquals("2026-07-02", reset.shopDate)
        assertEquals(1, reset.shopItems.size)
        assertEquals(StudyRules.OFFICIAL_ECONOMY_RESET_VERSION, reset.internalTestGrantVersion)
        assertEquals(reset, duplicate)
    }

    @Test
    fun `achievement rewards are claimable after conditions are met`() {
        val state = StudyState(
            stats = StudyStats(
                totalPomodoros = 10,
                totalTasksCompleted = 30,
                totalStudyMinutes = 3_000,
                unlockedOutfitSets = 3,
                unlockedTheaters = 1,
                videoRewardsRedeemed = 1,
            ),
            longestPerfectStreak = 7,
        )

        val claimable = StudyRules.claimableAchievements(state).map { it.id }.toSet()

        assertTrue("first_companion" in claimable)
        assertTrue("todo_slayer" in claimable)
        assertTrue("perfect_7" in claimable)
        assertTrue("time_traveler" in claimable)
        assertTrue("outfit_collector" in claimable)
        assertTrue("theater_open" in claimable)
        assertTrue("mcdonalds_arrival" in claimable)
    }

    @Test
    fun `shop refresh returns three items and buying applies cost and reward`() {
        val state = StudyState(wallet = StudyWallet(kudos = 1_000))
        val refreshed = StudyRules.refreshShopIfNeeded(state, LocalDate.of(2026, 6, 30), Random(8))
        val item = refreshed.shopItems.first()
        val bought = StudyRules.buyShopItem(refreshed, item.id)

        assertEquals(3, refreshed.shopItems.size)
        assertEquals(1_000 - item.price, bought.state.wallet.kudos)
        assertTrue(bought.state.purchasedShopItemIds.contains(item.id))
    }

    @Test
    fun `daily shop no longer offers universal normal fragments`() {
        val refreshed = StudyRules.refreshShopIfNeeded(
            StudyState(wallet = StudyWallet(kudos = 1_000)),
            LocalDate.of(2026, 7, 6),
            FixedDrawRandom(ints = mutableListOf(0, 0, 0)),
        )
        assertEquals(3, refreshed.shopItems.size)
        assertTrue(refreshed.shopItems.none { it.type == StudyShopItemType.UniversalNormalFragment })
    }

    @Test
    fun `daily shop uses only supported purchasable item types`() {
        val allowed = setOf(
            StudyShopItemType.DouyinFragment,
            StudyShopItemType.TheaterFragment,
            StudyShopItemType.GameFragment,
            StudyShopItemType.VideoFragment,
            StudyShopItemType.AnimeFragment,
            StudyShopItemType.SingleDrawTicket,
        )

        val refreshed = StudyRules.refreshShopIfNeeded(
            StudyState(),
            LocalDate.of(2026, 7, 11),
            Random(18),
        )

        assertTrue(refreshed.shopItems.all { it.type in allowed })
    }

    @Test
    fun `study minutes keep remainder and grant daily purple safety after two hours`() {
        val first = StudyRules.completePomodoro(
            state = StudyState(
                today = "2026-07-06",
                dailyPurpleDrawDate = "2026-07-06",
                dailyDrawCount = 30,
            ),
            minutes = 3,
        )
        val second = StudyRules.completePomodoro(first.state, minutes = 2)
        val longStudy = StudyRules.completePomodoro(second.state, minutes = 115)

        assertEquals(0, first.reward.kudos)
        assertEquals(3, first.state.pendingRewardMinutes)
        assertEquals(100, second.reward.kudos)
        assertEquals(0, second.state.pendingRewardMinutes)
        assertEquals(1, longStudy.reward.purpleDrawTickets)
        assertEquals(1, longStudy.state.wallet.purpleDrawTickets)
    }

    @Test
    fun `inactivity penalty never makes kudos negative`() {
        val state = StudyState(
            wallet = StudyWallet(kudos = 80),
            inactiveStudyDays = 2,
        )

        val penalized = StudyRules.applyInactivityPenalty(state)

        assertEquals(30, penalized.state.wallet.kudos)
        val zero = StudyRules.applyInactivityPenalty(penalized.state.copy(inactiveStudyDays = 3))
        assertEquals(0, zero.state.wallet.kudos)
    }

    @Test
    fun `daily rollover uses last study date instead of cumulative pomodoro totals`() {
        val state = StudyState(
            today = "2026-06-28",
            wallet = StudyWallet(kudos = 200),
            stats = StudyStats(totalPomodoros = 9),
            lastStudyDate = "2026-06-20",
            inactiveStudyDays = 1,
        )

        val next = StudyRules.rolloverToDate(state, LocalDate.of(2026, 6, 29))

        assertEquals("2026-06-29", next.today)
        assertEquals(2, next.inactiveStudyDays)
        assertEquals(150, next.wallet.kudos)
    }

    @Test
    fun `syncing changed plan tasks clears stale generated schedule`() {
        val date = LocalDate.of(2026, 7, 3)
        val staleSchedule = listOf(StudyScheduleBlock("09:30-10:00", "旧计划", "旧的低负荷安排"))
        val state = StudyState(
            today = date.toString(),
            activePlanDate = date.toString(),
            tasks = listOf(
                StudyTask(
                    id = "plan-${date}-0",
                    title = "旧计划｜今天完全休息",
                    source = StudyTaskSource.Plan,
                ),
            ),
            generatedSchedules = mapOf(date.toString() to staleSchedule),
        )

        val synced = StudyRules.syncPlanTasks(state, date)

        assertTrue(synced.tasks.any { it.title.contains("刑法学第 1 章") })
        assertFalse(synced.generatedSchedules.containsKey(date.toString()))
    }

    @Test
    fun `syncing plan tasks preserves completion only when the task title is unchanged`() {
        val date = LocalDate.of(2026, 7, 3)
        val completedAt = 1_700_000_123_000L
        val firstPlanTitle = ExamStudyPlan.todayPlan(date)!!.tasks.first().let { "${it.kind.label}｜${it.title}" }
        val state = StudyState(
            today = date.toString(),
            activePlanDate = date.toString(),
            wallet = StudyWallet(kudos = 320, totalKudosEarned = 900, tenDrawTickets = 2),
            inventory = StudyInventory(normalFragments = mapOf("normal:星穹图书馆:专属碎片" to 4)),
            stats = StudyStats(totalPomodoros = 5, totalTasksCompleted = 8, totalStudyMinutes = 125),
            tasks = listOf(
                StudyTask(
                    id = "plan-${date}-0",
                    title = firstPlanTitle,
                    done = true,
                    completedAt = completedAt,
                    source = StudyTaskSource.Plan,
                ),
            ),
        )

        val synced = StudyRules.syncPlanTasks(state, date)
        val firstPlanTask = synced.tasks.first { it.id == "plan-${date}-0" }

        assertTrue(firstPlanTask.done)
        assertEquals(completedAt, firstPlanTask.completedAt)
        assertEquals(320, synced.wallet.kudos)
        assertEquals(2, synced.wallet.tenDrawTickets)
        assertEquals(4, synced.inventory.normalFragments.getValue("normal:星穹图书馆:专属碎片"))
        assertEquals(5, synced.stats.totalPomodoros)
    }

    @Test
    fun `rebalanced task at the same index does not inherit an old completion`() {
        val date = LocalDate.of(2026, 7, 11)
        val state = StudyState(
            today = date.toString(),
            activePlanDate = date.toString(),
            tasks = listOf(
                StudyTask(
                    id = "plan-${date}-0",
                    title = "背诵｜旧的法理第 2 章任务",
                    done = true,
                    completedAt = 1_700_000_123_000L,
                    source = StudyTaskSource.Plan,
                ),
            ),
        )

        val synced = StudyRules.syncPlanTasks(state, date)
        val firstPlanTask = synced.tasks.first { it.id == "plan-${date}-0" }

        assertFalse(firstPlanTask.done)
        assertNull(firstPlanTask.completedAt)
        assertTrue(firstPlanTask.title.contains("法理第 1 章"))
    }

    @Test
    fun `fragment reset compensation grants sixty draws once and clears only image fragments`() {
        val state = StudyState(
            wallet = StudyWallet(kudos = 123, totalKudosEarned = 456, singleDrawTickets = 1, tenDrawTickets = 2),
            inventory = StudyInventory(
                normalFragments = mapOf("normal:星穹图书馆:专属碎片" to 2),
                rareFragments = mapOf("rare:legacy-picture" to 3),
                universalNormalFragments = 4,
                epicFragments = 1,
                douyinFragments = 2,
                videoFragments = 1,
                unlockedOutfits = setOf("星穹图书馆"),
                unopenedMysteryBoxes = listOf(StudyMysteryBoxReward(kudos = 50, universalNormalFragments = 2)),
            ),
            stats = StudyStats(totalPomodoros = 3, totalTasksCompleted = 4, totalStudyMinutes = 75),
            internalTestGrantVersion = StudyRules.OFFICIAL_ECONOMY_RESET_VERSION,
        )

        val compensated = StudyRules.grantDataLossCompensation(state)
        val earnedAfterMigration = compensated.copy(
            inventory = compensated.inventory.copy(
                normalFragments = mapOf("normal:云上列车:专属碎片" to 1),
            ),
        )
        val duplicate = StudyRules.grantDataLossCompensation(earnedAfterMigration)

        assertEquals(123, compensated.wallet.kudos)
        assertEquals(456, compensated.wallet.totalKudosEarned)
        assertEquals(1, compensated.wallet.singleDrawTickets)
        assertEquals(8, compensated.wallet.tenDrawTickets)
        assertTrue(compensated.inventory.normalFragments.isEmpty())
        assertTrue(compensated.inventory.rareFragments.isEmpty())
        assertEquals(0, compensated.inventory.universalNormalFragments)
        assertEquals(1, compensated.inventory.epicFragments)
        assertEquals(2, compensated.inventory.douyinFragments)
        assertEquals(1, compensated.inventory.videoFragments)
        assertTrue("星穹图书馆" in compensated.inventory.unlockedOutfits)
        assertEquals(0, compensated.inventory.unopenedMysteryBoxes.single().universalNormalFragments)
        assertEquals(3, compensated.stats.totalPomodoros)
        assertEquals(StudyRules.DATA_LOSS_COMPENSATION_VERSION, compensated.internalTestGrantVersion)
        assertEquals(earnedAfterMigration, duplicate)
        assertEquals(1, duplicate.inventory.normalFragments.getValue("normal:云上列车:专属碎片"))
    }

    @Test
    fun `interrupted pomodoro compensation grants 400 kudos once`() {
        val state = StudyState(wallet = StudyWallet(kudos = 80, totalKudosEarned = 120))

        val compensated = StudyRules.grantPomodoroInterruptionCompensation(state)
        val duplicate = StudyRules.grantPomodoroInterruptionCompensation(compensated)

        assertEquals(480, compensated.wallet.kudos)
        assertEquals(520, compensated.wallet.totalKudosEarned)
        assertEquals(
            StudyRules.POMODORO_INTERRUPTION_COMPENSATION_VERSION,
            compensated.pomodoroInterruptionCompensationVersion,
        )
        assertEquals(compensated, duplicate)
        assertEquals("番茄钟中断补偿", compensated.recentEvents.last().title)
    }

    @Test
    fun `forty draw bad luck compensation grants four ten draw tickets once`() {
        val state = StudyState(wallet = StudyWallet(tenDrawTickets = 1))

        val compensated = StudyRules.grantGachaBadLuckCompensation(state)
        val duplicate = StudyRules.grantGachaBadLuckCompensation(compensated)

        assertEquals(5, compensated.wallet.tenDrawTickets)
        assertEquals(
            StudyRules.GACHA_BAD_LUCK_COMPENSATION_VERSION,
            compensated.gachaBadLuckCompensationVersion,
        )
        assertEquals(compensated, duplicate)
        assertEquals("40抽全蓝体验补偿", compensated.recentEvents.last().title)
    }

    @Test
    fun `perfect streak only increments once per day`() {
        val state = StudyState(
            today = "2026-06-30",
            tasks = listOf(
                StudyTask(id = "a", title = "English"),
                StudyTask(id = "b", title = "Politics"),
            ),
        )

        val first = StudyRules.toggleTask(state, "a", true).state
        val fullClear = StudyRules.toggleTask(first, "b", true).state
        val unchecked = StudyRules.toggleTask(fullClear, "b", false).state
        val clearedAgain = StudyRules.toggleTask(unchecked, "b", true).state

        assertEquals(1, fullClear.perfectStreak)
        assertEquals("2026-06-30", fullClear.lastPerfectDate)
        assertEquals(1, clearedAgain.perfectStreak)
        assertEquals(1, clearedAgain.longestPerfectStreak)
    }

    @Test
    fun `universal fragments can fill the closest normal outfit part`() {
        val key = "normal:${StudyRules.outfitNames.first()}:${StudyRules.outfitParts.first()}"
        val state = StudyState(
            inventory = StudyInventory(
                normalFragments = mapOf(key to 3),
                universalNormalFragments = 1,
            )
        )

        val used = StudyRules.useUniversalNormalFragment(state, key)

        assertEquals(4, used.state.inventory.normalFragments[key])
        assertEquals(0, used.state.inventory.universalNormalFragments)
    }

    @Test
    fun `overflowing a completed normal outfit part converts universal fragment into kudos`() {
        val key = "normal:${StudyRules.outfitNames.first()}:${StudyRules.outfitParts.first()}"
        val state = StudyState(
            wallet = StudyWallet(kudos = 20),
            inventory = StudyInventory(
                normalFragments = mapOf(key to StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT),
                universalNormalFragments = 1,
            )
        )

        val used = StudyRules.useUniversalNormalFragment(state, key)

        assertEquals(StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT, used.state.inventory.normalFragments[key])
        assertEquals(0, used.state.inventory.universalNormalFragments)
        assertEquals(120, used.state.wallet.kudos)
    }

    @Test
    fun `drawing a completed normal outfit part still reports the result`() {
        val key = "normal:${StudyRules.outfitNames.first()}:${StudyRules.outfitParts.first()}"
        val state = StudyState(
            wallet = StudyWallet(kudos = StudyRules.SINGLE_DRAW_COST),
            inventory = StudyInventory(normalFragments = mapOf(key to StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT)),
        )
        val drawn = StudyRules.draw(state, count = 1, random = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextInt(until: Int): Int = 0
        })

        assertEquals(StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT, drawn.state.inventory.normalFragments[key])
        assertEquals(1, drawn.results.size)
        assertTrue(drawn.results.single().alreadyFull)
        assertEquals(0, drawn.state.wallet.kudos)
        assertEquals(1, drawn.state.dailyDrawCount)
    }

    @Test
    fun `ten draw reports every full fragment and still counts the attempts`() {
        val outfit = StudyRules.outfitNames.first()
        val key = "normal:$outfit:${StudyRules.outfitParts.first()}"
        val state = StudyState(
            wallet = StudyWallet(kudos = StudyRules.TEN_DRAW_COST),
            inventory = StudyInventory(normalFragments = mapOf(key to StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT)),
        )
        val alwaysSameFullFragment = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextDouble(): Double = 0.0
            override fun nextInt(until: Int): Int = 0
        }

        val drawn = StudyRules.draw(state, count = 10, random = alwaysSameFullFragment)

        assertEquals(0, drawn.state.wallet.kudos)
        assertEquals(10, drawn.results.size)
        assertTrue(drawn.results.all { it.alreadyFull })
        assertEquals(0, drawn.state.inventory.douyinFragments)
        assertEquals(10, drawn.state.dailyDrawCount)
    }

    @Test
    fun `draw pool has purple gold and rainbow entertainment fragments`() {
        val state = StudyState(wallet = StudyWallet(singleDrawTickets = 3))
        val purple = StudyRules.draw(state, count = 1, random = FixedDrawRandom(doubles = mutableListOf(0.95, 0.0))).state
        val gold = StudyRules.draw(purple, count = 1, random = FixedDrawRandom(doubles = mutableListOf(0.99, 0.99))).state
        val rainbow = StudyRules.draw(gold, count = 1, random = FixedDrawRandom(doubles = mutableListOf(0.999))).state

        assertEquals(1, purple.inventory.douyinFragments)
        assertEquals(1, gold.inventory.videoFragments)
        assertEquals(1, rainbow.inventory.animeFragments)
    }

    @Test
    fun `purple and gold draws split between their explicit reward types`() {
        var state = StudyState(wallet = StudyWallet(singleDrawTickets = 4))

        state = StudyRules.draw(
            state,
            count = 1,
            random = FixedDrawRandom(doubles = mutableListOf(0.95, 0.0)),
        ).state
        state = StudyRules.draw(
            state,
            count = 1,
            random = FixedDrawRandom(doubles = mutableListOf(0.95, 0.99)),
        ).state
        state = StudyRules.draw(
            state,
            count = 1,
            random = FixedDrawRandom(doubles = mutableListOf(0.99, 0.0)),
        ).state
        state = StudyRules.draw(
            state,
            count = 1,
            random = FixedDrawRandom(doubles = mutableListOf(0.99, 0.99)),
        ).state

        assertEquals(1, state.inventory.douyinFragments)
        assertEquals(1, state.inventory.theaterFragments)
        assertEquals(1, state.inventory.gameFragments)
        assertEquals(1, state.inventory.videoFragments)
    }

    @Test
    fun `purple draw gives theater a two point five percent share of the full pool`() {
        var state = StudyState(wallet = StudyWallet(singleDrawTickets = 2))

        state = StudyRules.draw(
            state,
            count = 1,
            random = FixedDrawRandom(doubles = mutableListOf(0.95, 0.68)),
        ).state
        state = StudyRules.draw(
            state,
            count = 1,
            random = FixedDrawRandom(doubles = mutableListOf(0.95, 0.70)),
        ).state

        assertEquals(1, state.inventory.douyinFragments)
        assertEquals(1, state.inventory.theaterFragments)
    }

    @Test
    fun `daily purple safety ticket grants one purple reward and is consumed`() {
        val state = StudyState(
            today = "2026-07-12",
            wallet = StudyWallet(purpleDrawTickets = 1),
        )

        val drawn = StudyRules.drawPurpleTicket(
            state,
            random = FixedDrawRandom(doubles = mutableListOf(0.99)),
        )

        assertEquals(0, drawn.state.wallet.purpleDrawTickets)
        assertEquals(1, drawn.state.inventory.theaterFragments)
        assertEquals(1, drawn.state.dailyPurpleDrawCount)
        assertEquals(StudyRarity.Rare, drawn.results.single().rarity)
    }

    @Test
    fun `draw pool never grants universal fragments`() {
        val state = StudyState(
            wallet = StudyWallet(singleDrawTickets = 2),
            inventory = StudyInventory(universalNormalFragments = 3),
        )
        val first = StudyRules.draw(
            state,
            count = 1,
            random = FixedDrawRandom(doubles = mutableListOf(0.0), ints = mutableListOf(0, 0)),
        ).state
        val outfit = StudyRules.draw(
            first,
            count = 1,
            random = FixedDrawRandom(doubles = mutableListOf(0.04), ints = mutableListOf(0, 0)),
        ).state

        assertEquals(3, first.inventory.universalNormalFragments)
        assertEquals(3, outfit.inventory.universalNormalFragments)
        assertEquals(2, outfit.inventory.normalFragments.values.sum())
    }

    @Test
    fun `clearing image fragments does not relock completed images after later draws`() {
        val state = StudyState(
            wallet = StudyWallet(singleDrawTickets = 1),
            inventory = StudyInventory(unlockedOutfits = setOf("星穹图书馆")),
        )

        val drawn = StudyRules.draw(
            state,
            count = 1,
            random = FixedDrawRandom(doubles = mutableListOf(0.0), ints = mutableListOf(1, 0)),
        ).state

        assertTrue("星穹图书馆" in drawn.inventory.unlockedOutfits)
        assertEquals(1, drawn.inventory.normalFragments.values.sum())
    }

    @Test
    fun `entertainment rewards consume their matching fragment`() {
        val state = StudyState(
            inventory = StudyInventory(
                douyinFragments = 1,
                gameFragments = 1,
                animeFragments = 1,
            ),
        )

        val douyin = StudyRules.redeemEntertainment(state, StudyEntertainmentReward.Douyin)
        val game = StudyRules.redeemEntertainment(douyin.state, StudyEntertainmentReward.Game)
        val anime = StudyRules.redeemEntertainment(game.state, StudyEntertainmentReward.Anime)

        assertEquals(0, anime.state.inventory.douyinFragments)
        assertEquals(0, anime.state.inventory.gameFragments)
        assertEquals(0, anime.state.inventory.animeFragments)
        assertEquals("抖音时长券已使用 · 20分钟", douyin.reward.title)
        assertEquals("游戏畅玩券已使用 · 120分钟", game.reward.title)
        assertEquals("番剧兑换券已使用 · 3小时", anime.reward.title)
    }

    private fun millisAt(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, TEST_ZONE).toInstant().toEpochMilli()

    private class FixedDrawRandom(
        private val doubles: MutableList<Double> = mutableListOf(),
        private val ints: MutableList<Int> = mutableListOf(),
    ) : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(): Double = doubles.removeAt(0)
        override fun nextInt(until: Int): Int = (ints.removeAt(0).takeIf { it >= 0 } ?: 0) % until
    }

    private companion object {
        val TEST_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
    }

    @Test
    fun `level rewards grant only kudos and draw tickets`() {
        assertTrue(StudyRules.levels.all { level ->
            val reward = level.reward
            reward.douyinFragments == 0 &&
                reward.theaterFragments == 0 &&
                reward.gameFragments == 0 &&
                reward.videoFragments == 0 &&
                reward.animeFragments == 0 &&
                reward.universalNormalFragments == 0 &&
                reward.universalRareFragments == 0 &&
                reward.universalEpicFragments == 0
        })
        assertTrue(StudyRules.levels.count { it.reward.tenDrawTickets > 0 } >= 10)
        assertTrue(StudyRules.levels.count { it.reward.kudos > 0 } >= 10)
    }

    @Test
    fun `expanded milestones unlock new achievements`() {
        val state = StudyState(
            stats = StudyStats(
                totalPomodoros = 100,
                totalTasksCompleted = 100,
                totalStudyMinutes = 12_000,
                unlockedOutfitSets = 10,
            ),
            longestPerfectStreak = 30,
        )

        val ids = StudyRules.claimableAchievements(state).map { it.id }.toSet()

        assertTrue("pomodoro_100" in ids)
        assertTrue("tasks_100" in ids)
        assertTrue("perfect_30" in ids)
        assertTrue("study_200h" in ids)
        assertTrue("outfits_10" in ids)
    }

}
