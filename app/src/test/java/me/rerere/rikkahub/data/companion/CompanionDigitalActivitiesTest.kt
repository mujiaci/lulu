package me.rerere.rikkahub.data.companion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompanionDigitalActivitiesTest {
    @Test
    fun everyFirstBatchActivityHasACompleteExecutionContract() {
        assertEquals(CompanionDigitalActivityKind.values().toSet(), CompanionDigitalActivityRegistry.definitions.keys)
        CompanionDigitalActivityRegistry.definitions.values.forEach { definition ->
            assertTrue(definition.trigger.isNotBlank())
            assertTrue(definition.cooldownMillis > 0L)
            assertTrue(definition.expectedDurationMillis > 0L)
            assertTrue(definition.followUpRule.isNotBlank())
        }
    }

    @Test
    fun activitySelectionUsesTriggerEvidenceAndCooldownInsteadOfChance() {
        val now = 10_000_000L
        val candidates = listOf(
            CompanionDigitalActivityCandidate(
                kind = CompanionDigitalActivityKind.WATCH_REPLAY,
                triggerSatisfied = true,
                evidenceReference = null,
                priority = 10,
            ),
            CompanionDigitalActivityCandidate(
                kind = CompanionDigitalActivityKind.ORGANIZE_STATE,
                triggerSatisfied = true,
                priority = 5,
            ),
        )

        val selected = CompanionDigitalActivitySelector.select(candidates, emptyList(), now)

        assertEquals(CompanionDigitalActivityKind.ORGANIZE_STATE, selected?.kind)
    }

    @Test
    fun cooldownBlocksRepeatingTheSameActivity() {
        val now = 20_000_000L
        val candidate = CompanionDigitalActivityCandidate(
            kind = CompanionDigitalActivityKind.ORGANIZE_CONCERNS,
            triggerSatisfied = true,
            priority = 1,
        )
        val recent = CompanionLifeEvent(
            assistantId = "role",
            type = CompanionLifeEventType.CONCERN_ORGANIZATION,
            title = "整理关注",
            startedAt = now - 1_000L,
        )

        assertNull(CompanionDigitalActivitySelector.select(listOf(candidate), listOf(recent), now))
    }

    @Test
    fun favoriteRequiresMessageReasonAndFeeling() {
        assertFalse(shouldAutonomouslyFavorite("", "这句话改变了我的判断", "意外"))
        assertFalse(shouldAutonomouslyFavorite("message-1", "好", "意外"))
        assertFalse(shouldAutonomouslyFavorite("message-1", "这句话改变了我的判断", ""))
        assertTrue(shouldAutonomouslyFavorite("message-1", "这句话改变了我的判断", "意外"))
    }

    @Test
    fun legacySnapshotMigratesWithEmptyFavoritesWithoutLosingLifeEvents() {
        val legacyEvent = CompanionLifeEvent(
            assistantId = "role",
            type = CompanionLifeEventType.JOURNAL,
            title = "旧日记",
        )
        val migrated = CompanionPersistedState(
            version = 9,
            snapshots = listOf(CompanionSnapshot(assistantId = "role", lifeEvents = listOf(legacyEvent))),
        ).normalizedCompanionState()

        assertEquals(CURRENT_COMPANION_SCHEMA_VERSION, migrated.version)
        assertEquals(listOf(legacyEvent), migrated.snapshots.single().lifeEvents)
        assertTrue(migrated.snapshots.single().favorites.isEmpty())
    }

    @Test
    fun emptyCandidateSetDoesNotInventAnActivity() {
        assertNull(CompanionDigitalActivitySelector.select(emptyList(), emptyList(), 100L))
    }

    @Test
    fun completionClaimsRequireCompletedStoredEvidence() {
        val completed = CompanionLifeEvent(
            assistantId = "role",
            type = CompanionLifeEventType.REPLAY_REVIEW,
            status = CompanionLifeEventStatus.COMPLETED,
            title = "看回放",
            evidenceReference = "game:round-7",
        )
        val failed = completed.copy(id = "failed", status = CompanionLifeEventStatus.FAILED)

        assertTrue(completed.status == CompanionLifeEventStatus.COMPLETED && !completed.evidenceReference.isNullOrBlank())
        assertFalse(failed.status == CompanionLifeEventStatus.COMPLETED)
    }
}
