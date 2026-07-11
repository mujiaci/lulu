package me.rerere.rikkahub.data.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionStateNormalizationTest {
    @Test
    fun `duplicate snapshots merge without losing active work`() {
        val concern = CompanionConcern(
            id = "concern-1",
            assistantId = "assistant-a",
            subjectKey = "exam:one",
            event = "考试",
            goal = "按时参加",
        )
        val commitment = CompanionCommitment(
            id = "commitment-1",
            assistantId = "assistant-a",
            subjectKey = "wake:one",
            promise = "叫醒用户",
            dueAt = 100L,
            status = CompanionCommitmentStatus.ACTIVE,
        )
        val state = CompanionPersistedState(
            snapshots = listOf(
                CompanionSnapshot(assistantId = "assistant-a", concerns = listOf(concern), updatedAt = 10L),
                CompanionSnapshot(assistantId = "assistant-a", commitments = listOf(commitment), updatedAt = 20L),
            ),
        )

        val normalized = state.normalizedCompanionState()

        assertEquals(1, normalized.snapshots.size)
        assertEquals("concern-1", normalized.snapshots.single().concerns.single().id)
        assertEquals("commitment-1", normalized.snapshots.single().commitments.single().id)
    }

    @Test
    fun `normalization removes blank assistants and orders snapshots`() {
        val state = CompanionPersistedState(
            snapshots = listOf(
                CompanionSnapshot.empty("assistant-b"),
                CompanionSnapshot.empty(""),
                CompanionSnapshot.empty("assistant-a"),
            ),
        )

        val normalized = state.normalizedCompanionState()

        assertEquals(listOf("assistant-a", "assistant-b"), normalized.snapshots.map { it.assistantId })
    }

    @Test
    fun `normalization clamps relationship dimensions`() {
        val snapshot = CompanionSnapshot(
            assistantId = "assistant-a",
            relationship = CompanionRelationshipState(
                trust = -1f,
                closeness = 4f,
                unresolvedTension = -3f,
            ),
        )

        val relationship = CompanionPersistedState(snapshots = listOf(snapshot)).normalizedCompanionState()
            .snapshots.single().relationship

        assertEquals(0f, relationship.trust)
        assertEquals(1f, relationship.closeness)
        assertEquals(0f, relationship.unresolvedTension)
    }

    @Test
    fun `normalization bounds relationship event history`() {
        val normalized = CompanionPersistedState(
            appliedRelationshipEventIds = (1..2_100).map { "event-$it" },
        ).normalizedCompanionState()

        assertEquals(2_000, normalized.appliedRelationshipEventIds.size)
        assertTrue("event-2100" in normalized.appliedRelationshipEventIds)
        assertTrue("event-1" !in normalized.appliedRelationshipEventIds)
    }

    @Test
    fun `clearing an assistant removes only its runtime snapshot`() {
        val state = CompanionPersistedState(
            snapshots = listOf(
                CompanionSnapshot.empty("assistant-a"),
                CompanionSnapshot.empty("assistant-b"),
            ),
            appliedRelationshipEventIds = listOf("event-a", "event-b"),
        )

        val cleared = state.withoutAssistant("assistant-a")

        assertEquals(listOf("assistant-b"), cleared.snapshots.map { it.assistantId })
        assertEquals(listOf("event-a", "event-b"), cleared.appliedRelationshipEventIds)
    }

    @Test
    fun `normalization cleans technical text from current and historical state`() {
        val technicalState = CompanionState(
            statusText = "副 API 判断中",
            innerThought = "planner fallback result",
            mindState = "本地规划兜底",
            selfScene = "刚刚做了一次后台判断，状态栏留下了结果。",
            updatedAt = 100L,
        )
        val snapshot = CompanionSnapshot(
            assistantId = "assistant-a",
            state = technicalState,
            stateHistory = listOf(
                CompanionStateHistoryEntry(
                    id = "history-1",
                    state = technicalState,
                    recordedAt = 100L,
                )
            ),
        )

        val normalized = CompanionPersistedState(snapshots = listOf(snapshot))
            .normalizedCompanionState()
            .snapshots.single()

        assertEquals("安静留意", normalized.state.statusText)
        assertEquals("", normalized.state.innerThought)
        assertEquals("安静留意着现在的变化", normalized.state.mindState)
        assertEquals("暂时没有开口，只把注意力留在接下来的变化上。", normalized.state.selfScene)
        assertEquals(normalized.state.statusText, normalized.stateHistory.single().state.statusText)
        assertEquals(normalized.state.selfScene, normalized.stateHistory.single().state.selfScene)
    }
}
