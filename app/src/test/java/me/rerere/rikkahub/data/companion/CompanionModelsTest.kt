package me.rerere.rikkahub.data.companion

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionModelsTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `snapshot serialization keeps assistants isolated`() {
        val state = CompanionPersistedState(
            snapshots = listOf(
                CompanionSnapshot.empty("assistant-a"),
                CompanionSnapshot.empty("assistant-b"),
            ),
        )

        val decoded = json.decodeFromString<CompanionPersistedState>(json.encodeToString(state))

        assertEquals(
            setOf("assistant-a", "assistant-b"),
            decoded.snapshots.map { it.assistantId }.toSet(),
        )
    }

    @Test
    fun `default relationship is role neutral`() {
        val relationship = CompanionRelationshipState()

        assertEquals(0f, relationship.closeness)
        assertEquals(0.5f, relationship.trust)
        assertEquals(0.5f, relationship.reliability)
        assertEquals(0f, relationship.unresolvedTension)
        assertTrue(relationship.roleLabel.isBlank())
    }

    @Test
    fun `concerns and commitments survive serialization`() {
        val snapshot = CompanionSnapshot(
            assistantId = "assistant-a",
            concerns = listOf(
                CompanionConcern(
                    id = "concern-exam",
                    assistantId = "assistant-a",
                    subjectKey = "exam:2026-12-20",
                    event = "用户明早参加考试",
                    goal = "到点确认用户已经醒来",
                    importance = 5,
                    nextPerceptionAt = 1_800_000_000_000L,
                ),
            ),
            commitments = listOf(
                CompanionCommitment(
                    id = "commitment-wakeup",
                    assistantId = "assistant-a",
                    subjectKey = "wake:2026-12-20T08:00",
                    promise = "早上八点叫醒用户",
                    dueAt = 1_800_000_000_000L,
                    status = CompanionCommitmentStatus.ACTIVE,
                    actionPlan = CompanionActionPlan(
                        type = CompanionActionType.ALARM,
                        toolName = "set_alarm",
                    ),
                ),
            ),
        )

        val decoded = json.decodeFromString<CompanionSnapshot>(json.encodeToString(snapshot))

        assertEquals("exam:2026-12-20", decoded.concerns.single().subjectKey)
        assertEquals(CompanionCommitmentStatus.ACTIVE, decoded.commitments.single().status)
        assertEquals(CompanionActionType.ALARM, decoded.commitments.single().actionPlan.type)
    }

    @Test
    fun `state history survives serialization`() {
        val snapshot = CompanionSnapshot(
            assistantId = "assistant-a",
            stateHistory = listOf(
                CompanionStateHistoryEntry(
                    id = "state-1",
                    state = CompanionState(statusText = "在等用户", innerThought = "我先不催。", updatedAt = 100L),
                    recordedAt = 100L,
                )
            ),
        )

        val decoded = json.decodeFromString<CompanionSnapshot>(json.encodeToString(snapshot))

        assertEquals("在等用户", decoded.stateHistory.single().state.statusText)
        assertEquals(100L, decoded.stateHistory.single().recordedAt)
    }
}
