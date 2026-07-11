package me.rerere.rikkahub.data.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudyStorePersistenceTest {
    @Test
    fun `safe decoder preserves local study wallet inventory and stats across schema changes`() {
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
                "unlockedOutfits": [],
                "unlockedTheaters": [],
                "unopenedMysteryBoxes": []
              },
              "stats": {"totalPomodoros": 6, "totalTasksCompleted": 8, "totalStudyMinutes": 150, "unlockedOutfitSets": 0, "unlockedTheaters": 0, "mcdonaldsRedeemed": 1},
              "futureFieldFromNewBuild": "must not break old local data"
            }
        """.trimIndent()

        val decoded = decodeStudyStateOrNull(raw)?.migrateLegacyEntertainmentFragments()

        requireNotNull(decoded)
        assertEquals(345, decoded.wallet.kudos)
        assertEquals(3, decoded.inventory.normalFragments.getValue("normal:星穹图书馆:专属碎片"))
        assertEquals(3, decoded.inventory.epicFragments)
        assertEquals(6, decoded.stats.totalPomodoros)
        assertEquals(8, decoded.stats.totalTasksCompleted)
        assertEquals(150, decoded.stats.totalStudyMinutes)
    }

    @Test
    fun `safe decoder does not turn corrupt study data into an empty state`() {
        assertNull(decodeStudyStateOrNull("{not-json"))
    }
}
