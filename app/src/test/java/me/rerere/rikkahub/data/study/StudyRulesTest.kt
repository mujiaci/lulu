package me.rerere.rikkahub.data.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
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
    fun `pomodoro completion gives fixed kudos and stores an unopened mystery box`() {
        val result = StudyRules.completePomodoro(
            state = StudyState(today = "2026-06-30"),
            minutes = 18,
            random = Random(1),
        )

        assertEquals(50, result.reward.kudos)
        assertTrue(result.reward.mysteryBoxKudos in listOf(15, 25, 50, 100, 200))
        assertEquals(50, result.state.wallet.kudos)
        assertEquals(1, result.state.inventory.unopenedMysteryBoxes.size)
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
        val completed = StudyRules.completePomodoro(
            state = StudyState(today = "2026-06-30"),
            minutes = 25,
            random = Random(1),
        )

        val opened = StudyRules.openMysteryBox(completed.state)

        assertEquals(0, opened.state.inventory.unopenedMysteryBoxes.size)
        assertEquals(50 + opened.reward.kudos, opened.state.wallet.kudos)
        assertEquals(opened.reward.universalNormalFragments, opened.state.inventory.universalNormalFragments)
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
    fun `daily shop no longer sells universal normal fragments directly`() {
        val state = StudyState(wallet = StudyWallet(kudos = 1_000))

        val refreshed = StudyRules.refreshShopIfNeeded(state, LocalDate.of(2026, 7, 6), Random(8))

        assertEquals(3, refreshed.shopItems.size)
        assertTrue(refreshed.shopItems.none { it.type == StudyShopItemType.UniversalNormalFragment })
    }

    @Test
    fun `mystery boxes make universal normal fragments uncommon`() {
        val none = StudyRules.completePomodoro(
            state = StudyState(today = "2026-07-06"),
            minutes = 25,
            random = FixedDrawRandom(ints = mutableListOf(0, 69)),
        )
        val one = StudyRules.completePomodoro(
            state = StudyState(today = "2026-07-06"),
            minutes = 25,
            random = FixedDrawRandom(ints = mutableListOf(0, 70)),
        )
        val two = StudyRules.completePomodoro(
            state = StudyState(today = "2026-07-06"),
            minutes = 25,
            random = FixedDrawRandom(ints = mutableListOf(0, 99)),
        )

        assertEquals(0, none.reward.universalNormalFragments)
        assertEquals(1, one.reward.universalNormalFragments)
        assertEquals(2, two.reward.universalNormalFragments)
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

        assertTrue(synced.tasks.any { it.title.contains("刑法1入口") })
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
    fun `data loss compensation grants one ten draw ticket once without clearing study assets`() {
        val state = StudyState(
            wallet = StudyWallet(kudos = 123, totalKudosEarned = 456, singleDrawTickets = 1),
            inventory = StudyInventory(
                normalFragments = mapOf("normal:星穹图书馆:专属碎片" to 2),
                epicFragments = 1,
            ),
            stats = StudyStats(totalPomodoros = 3, totalTasksCompleted = 4, totalStudyMinutes = 75),
            internalTestGrantVersion = StudyRules.OFFICIAL_ECONOMY_RESET_VERSION,
        )

        val compensated = StudyRules.grantDataLossCompensation(state)
        val duplicate = StudyRules.grantDataLossCompensation(compensated)

        assertEquals(123, compensated.wallet.kudos)
        assertEquals(456, compensated.wallet.totalKudosEarned)
        assertEquals(1, compensated.wallet.singleDrawTickets)
        assertEquals(1, compensated.wallet.tenDrawTickets)
        assertEquals(2, compensated.inventory.normalFragments.getValue("normal:星穹图书馆:专属碎片"))
        assertEquals(1, compensated.inventory.epicFragments)
        assertEquals(3, compensated.stats.totalPomodoros)
        assertEquals(StudyRules.DATA_LOSS_COMPENSATION_VERSION, compensated.internalTestGrantVersion)
        assertEquals(compensated, duplicate)
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
    fun `drawing a completed normal outfit part converts overflow into kudos`() {
        val key = "normal:${StudyRules.outfitNames.first()}:${StudyRules.outfitParts.first()}"
        val state = StudyState(
            wallet = StudyWallet(kudos = StudyRules.SINGLE_DRAW_COST),
            inventory = StudyInventory(normalFragments = mapOf(key to StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT)),
        )
        val result = StudyDrawResult(StudyRarity.Normal, key, StudyRules.normalTitle(key))

        val drawn = StudyRules.draw(state, count = 1, random = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextInt(until: Int): Int = 0
        })

        assertEquals(StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT, drawn.state.inventory.normalFragments[key])
        assertEquals(1, drawn.results.size)
        assertEquals(result.fragmentKey, drawn.results.first().fragmentKey)
        assertEquals(100, drawn.state.wallet.kudos)
    }

    @Test
    fun `draw pool has purple gold and rainbow entertainment fragments`() {
        val state = StudyState(wallet = StudyWallet(singleDrawTickets = 3))
        val purple = StudyRules.draw(state, count = 1, random = FixedDrawRandom(doubles = mutableListOf(0.95))).state
        val gold = StudyRules.draw(purple, count = 1, random = FixedDrawRandom(doubles = mutableListOf(0.98))).state
        val rainbow = StudyRules.draw(gold, count = 1, random = FixedDrawRandom(doubles = mutableListOf(0.995))).state

        assertEquals(1, purple.inventory.universalRareFragments)
        assertEquals(1, gold.inventory.epicFragments)
        assertEquals(1, rainbow.inventory.rainbowFragments)
    }

    @Test
    fun `draw pool keeps universal normal fragments scarce`() {
        val state = StudyState(wallet = StudyWallet(singleDrawTickets = 2))
        val universal = StudyRules.draw(
            state,
            count = 1,
            random = FixedDrawRandom(doubles = mutableListOf(0.039), ints = mutableListOf(99)),
        ).state
        val outfit = StudyRules.draw(
            universal,
            count = 1,
            random = FixedDrawRandom(doubles = mutableListOf(0.04), ints = mutableListOf(0, 0)),
        ).state

        assertEquals(1, universal.inventory.universalNormalFragments)
        assertEquals(1, outfit.inventory.universalNormalFragments)
        assertEquals(1, outfit.inventory.normalFragments.values.sum())
    }

    @Test
    fun `entertainment rewards consume their matching fragment`() {
        val state = StudyState(
            inventory = StudyInventory(
                universalRareFragments = 1,
                epicFragments = 1,
                rainbowFragments = 1,
            ),
        )

        val douyin = StudyRules.redeemEntertainment(state, StudyEntertainmentReward.Douyin)
        val game = StudyRules.redeemEntertainment(douyin.state, StudyEntertainmentReward.Game)
        val anime = StudyRules.redeemEntertainment(game.state, StudyEntertainmentReward.Anime)

        assertEquals(0, anime.state.inventory.universalRareFragments)
        assertEquals(0, anime.state.inventory.epicFragments)
        assertEquals(0, anime.state.inventory.rainbowFragments)
        assertEquals("抖音时间已兑换", douyin.reward.title)
        assertEquals("游戏时间已兑换", game.reward.title)
        assertEquals("动漫时间已兑换", anime.reward.title)
    }

    private class FixedDrawRandom(
        private val doubles: MutableList<Double> = mutableListOf(),
        private val ints: MutableList<Int> = mutableListOf(),
    ) : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(): Double = doubles.removeAt(0)
        override fun nextInt(until: Int): Int = (ints.removeAt(0).takeIf { it >= 0 } ?: 0) % until
    }
}
