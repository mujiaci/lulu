package me.rerere.rikkahub.data.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        assertEquals(25, afterDay1.reward.kudos)
        assertEquals(25, afterDay2.reward.kudos)
        assertEquals(50, afterDay3.reward.kudos)
        assertEquals(75, afterDay5.reward.kudos)
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

        assertEquals(100, first.reward.kudos)
        assertFalse(first.state.superMomentAvailable)
        assertEquals(100, second.reward.kudos)
        assertTrue(second.state.superMomentAvailable)
        assertEquals(200, second.state.wallet.kudos)
    }

    @Test
    fun `pomodoro completion gives fixed kudos and a mystery box reward`() {
        val result = StudyRules.completePomodoro(
            state = StudyState(today = "2026-06-30"),
            random = Random(1),
        )

        assertEquals(50, result.reward.kudos)
        assertTrue(result.reward.mysteryBoxKudos in listOf(15, 25, 50, 100, 200))
        assertEquals(50 + result.reward.mysteryBoxKudos, result.state.wallet.kudos)
        assertEquals(1, result.state.stats.totalPomodoros)
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

        assertEquals(300, claimed.state.wallet.kudos)
        assertEquals(1, claimed.state.wallet.tenDrawTickets)
        assertEquals(5, claimed.state.inventory.universalNormalFragments)
        assertEquals(0, duplicate.reward.kudos)
        assertFalse(claimed.state.superMomentAvailable)
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
                mcdonaldsRedeemed = 1,
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
}
