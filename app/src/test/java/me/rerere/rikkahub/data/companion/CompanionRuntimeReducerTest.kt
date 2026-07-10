package me.rerere.rikkahub.data.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CompanionRuntimeReducerTest {
    @Test
    fun `unrelated chat leaves existing commitments untouched`() {
        val existing = commitment(id = "existing", subjectKey = "deadline:first")
        val current = persisted(snapshot(commitments = listOf(existing)))

        val reduced = reduceCompanionRuntimeState(
            current = current,
            mutation = CompanionTurnMutation(
                assistantId = ASSISTANT_A,
                state = CompanionState(statusText = "still here", updatedAt = 200L),
                nowMillis = 200L,
            ),
        )

        assertEquals(listOf("existing"), reduced.snapshot.commitments.map { it.id })
        assertEquals(CompanionCommitmentStatus.ACTIVE, reduced.snapshot.commitments.single().status)
    }

    @Test
    fun `accepted commitment becomes active in the same mutation`() {
        val reduced = reduceCompanionRuntimeState(
            current = CompanionPersistedState(),
            mutation = CompanionTurnMutation(
                assistantId = ASSISTANT_A,
                acceptedCommitments = listOf(
                    commitment(
                        id = "new",
                        subjectKey = "deadline:new",
                        status = CompanionCommitmentStatus.PROPOSED,
                    ),
                ),
                nowMillis = 200L,
            ),
        )

        assertEquals(CompanionCommitmentStatus.ACTIVE, reduced.snapshot.commitments.single().status)
    }

    @Test
    fun `begin execution advances active commitment through due to executing`() {
        val current = persisted(snapshot(commitments = listOf(commitment())))

        val reduced = beginCompanionCommitment(
            current = current,
            assistantId = ASSISTANT_A,
            commitmentId = "commitment-1",
            nowMillis = 200L,
        )

        val executing = reduced.snapshot.commitments.single()
        assertEquals(CompanionCommitmentStatus.EXECUTING, executing.status)
        assertEquals(1, executing.attemptCount)
    }

    @Test
    fun `begin execution refuses a commitment before its due time`() {
        val future = commitment().copy(dueAt = 500L)

        val reduced = beginCompanionCommitment(
            current = persisted(snapshot(commitments = listOf(future))),
            assistantId = ASSISTANT_A,
            commitmentId = future.id,
            nowMillis = 200L,
        )

        assertEquals(CompanionCommitmentStatus.ACTIVE, reduced.snapshot.commitments.single().status)
        assertNull(reduced.affectedCommitment)
    }

    @Test
    fun `successful execution stores result and fulfills commitment`() {
        val executing = commitment(status = CompanionCommitmentStatus.EXECUTING, attemptCount = 1)
        val result = CompanionActionResult(
            success = true,
            summary = "message delivered",
            completedAt = 300L,
        )

        val reduced = finishCompanionCommitment(
            current = persisted(snapshot(commitments = listOf(executing))),
            assistantId = ASSISTANT_A,
            commitmentId = executing.id,
            result = result,
        )

        val fulfilled = reduced.snapshot.commitments.single()
        assertEquals(CompanionCommitmentStatus.FULFILLED, fulfilled.status)
        assertEquals(result, fulfilled.lastActionResult)
        assertEquals(300L, fulfilled.resolvedAt)
    }

    @Test
    fun `failed execution stores error and schedules retry`() {
        val executing = commitment(status = CompanionCommitmentStatus.EXECUTING, attemptCount = 1)
        val result = CompanionActionResult(
            success = false,
            summary = "provider unavailable",
            completedAt = 300L,
        )

        val reduced = finishCompanionCommitment(
            current = persisted(snapshot(commitments = listOf(executing))),
            assistantId = ASSISTANT_A,
            commitmentId = executing.id,
            result = result,
            retryAt = 900L,
        )

        val retry = reduced.snapshot.commitments.single()
        assertEquals(CompanionCommitmentStatus.RETRY_SCHEDULED, retry.status)
        assertEquals(900L, retry.dueAt)
        assertEquals(result, retry.lastActionResult)
        assertNull(retry.resolvedAt)
    }

    @Test
    fun `runtime mutations ignore records owned by another assistant`() {
        val assistantB = snapshot(assistantId = ASSISTANT_B)
        val reduced = reduceCompanionRuntimeState(
            current = persisted(assistantB),
            mutation = CompanionTurnMutation(
                assistantId = ASSISTANT_A,
                concernChanges = listOf(
                    CompanionConcernChange.Upsert(
                        CompanionConcern(
                            id = "foreign-concern",
                            assistantId = ASSISTANT_B,
                            subjectKey = "foreign",
                            event = "foreign",
                            goal = "foreign",
                        ),
                    ),
                ),
                acceptedCommitments = listOf(commitment(assistantId = ASSISTANT_B)),
                relationshipEvents = listOf(
                    CompanionRelationshipEvent(
                        id = "foreign-event",
                        assistantId = ASSISTANT_B,
                        sourceId = "source-b",
                        kind = CompanionRelationshipEventKind.MANUAL,
                        trustDelta = 0.4f,
                        evidence = "foreign",
                        createdAt = 200L,
                    ),
                ),
                nowMillis = 200L,
            ),
        )

        assertEquals(2, reduced.persistedState.snapshots.size)
        assertEquals(assistantB, reduced.persistedState.snapshots.single { it.assistantId == ASSISTANT_B })
        assertEquals(emptyList<CompanionConcern>(), reduced.snapshot.concerns)
        assertEquals(emptyList<CompanionCommitment>(), reduced.snapshot.commitments)
        assertEquals(0.5f, reduced.snapshot.relationship.trust)
        assertNotNull(reduced.persistedState.snapshots.singleOrNull { it.assistantId == ASSISTANT_A })
    }

    private fun commitment(
        id: String = "commitment-1",
        assistantId: String = ASSISTANT_A,
        subjectKey: String = "deadline:one",
        status: CompanionCommitmentStatus = CompanionCommitmentStatus.ACTIVE,
        attemptCount: Int = 0,
    ) = CompanionCommitment(
        id = id,
        assistantId = assistantId,
        subjectKey = subjectKey,
        promise = "follow up",
        dueAt = 100L,
        status = status,
        attemptCount = attemptCount,
        createdAt = 10L,
        updatedAt = 10L,
    )

    private fun snapshot(
        assistantId: String = ASSISTANT_A,
        commitments: List<CompanionCommitment> = emptyList(),
    ) = CompanionSnapshot(
        assistantId = assistantId,
        commitments = commitments,
        updatedAt = 10L,
    )

    private fun persisted(snapshot: CompanionSnapshot) = CompanionPersistedState(
        snapshots = listOf(snapshot),
    )

    private companion object {
        const val ASSISTANT_A = "assistant-a"
        const val ASSISTANT_B = "assistant-b"
    }
}
