package me.rerere.rikkahub.data.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudyStorePersistenceTest {
    @Test
    fun `safe decoder preserves explicit entertainment fragments and ignores obsolete balances`() {
        val raw = """
            {
              "today": "2026-07-03",
              "wallet": {"kudos": 345, "totalKudosEarned": 700, "singleDrawTickets": 2, "tenDrawTickets": 1},
              "inventory": {
                "normalFragments": {"normal:星穹图书馆:专属碎片": 3},
                "rareFragments": {},
                "epicFragments": 1,
                "specialStoryFragments": 2,
                "universalNormalFragments": 4,
                "universalRareFragments": 1,
                "universalEpicFragments": 1,
                "rainbowFragments": 9,
                "douyinFragments": 1,
                "theaterFragments": 2,
                "gameFragments": 3,
                "videoFragments": 4,
                "animeFragments": 5,
                "unlockedOutfits": [],
                "unlockedTheaters": [],
                "unopenedMysteryBoxes": []
              },
              "stats": {"totalPomodoros": 6, "totalTasksCompleted": 8, "totalStudyMinutes": 150, "unlockedOutfitSets": 0, "unlockedTheaters": 0, "mcdonaldsRedeemed": 1},
              "futureFieldFromNewBuild": "must not break old local data"
            }
        """.trimIndent()

        val decoded = decodeStudyStateOrNull(raw)

        requireNotNull(decoded)
        assertEquals(345, decoded.wallet.kudos)
        assertEquals(3, decoded.inventory.normalFragments.getValue("normal:星穹图书馆:专属碎片"))
        assertEquals(1, decoded.inventory.douyinFragments)
        assertEquals(2, decoded.inventory.theaterFragments)
        assertEquals(3, decoded.inventory.gameFragments)
        assertEquals(4, decoded.inventory.videoFragments)
        assertEquals(5, decoded.inventory.animeFragments)
        assertEquals(6, decoded.stats.totalPomodoros)
        assertEquals(8, decoded.stats.totalTasksCompleted)
        assertEquals(150, decoded.stats.totalStudyMinutes)
        assertEquals(0, decoded.pendingRewardMinutes)
        assertEquals(0, decoded.wallet.purpleDrawTickets)
    }

    @Test
    fun `safe decoder does not turn corrupt study data into an empty state`() {
        assertNull(decodeStudyStateOrNull("{not-json"))
    }

    @Test
    fun `legacy universal purple and gold shop entries migrate to explicit fragments`() {
        val raw = """
            {
              "today": "2026-07-11",
              "shopDate": "2026-07-11",
              "shopItems": [
                {"id":"rare","type":"UniversalRareFragment","title":"旧紫色碎片","price":160},
                {"id":"epic","type":"UniversalEpicFragment","title":"旧金色碎片","price":400}
              ]
            }
        """.trimIndent()

        val decoded = decodeStudyStateOrNull(raw)

        requireNotNull(decoded)
        assertEquals(StudyShopItemType.TheaterFragment, decoded.shopItems[0].type)
        assertEquals(StudyShopItemType.VideoFragment, decoded.shopItems[1].type)
    }
}
