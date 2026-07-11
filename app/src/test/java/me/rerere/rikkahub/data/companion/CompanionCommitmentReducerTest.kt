package me.rerere.rikkahub.data.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionCommitmentReducerTest {
    @Test
    fun `commitment follows successful lifecycle`() {
        var commitments = listOf(commitment(status = CompanionCommitmentStatus.PROPOSED))

        commitments = transition(commitments, CompanionCommitmentStatus.ACTIVE, 20L)
        commitments = transition(commitments, CompanionCommitmentStatus.DUE, 30L)
        commitments = transition(commitments, CompanionCommitmentStatus.EXECUTING, 40L)
        commitments = transition(commitments, CompanionCommitmentStatus.FULFILLED, 50L)

        assertEquals(CompanionCommitmentStatus.FULFILLED, commitments.single().status)
        assertEquals(50L, commitments.single().resolvedAt)
    }

    @Test
    fun `failed commitment can be scheduled and retried`() {
        var commitments = listOf(commitment(status = CompanionCommitmentStatus.EXECUTING))

        commitments = transition(commitments, CompanionCommitmentStatus.FAILED, 30L)
        commitments = CompanionCommitmentReducer.apply(
            current = commitments,
            changes = listOf(
                CompanionCommitmentChange.Transition(
                    assistantId = ASSISTANT_ID,
                    commitmentId = COMMITMENT_ID,
                    status = CompanionCommitmentStatus.RETRY_SCHEDULED,
                    reason = "稍后重试",
                    nextDueAt = 500L,
                ),
            ),
            nowMillis = 40L,
        )
        commitments = transition(commitments, CompanionCommitmentStatus.DUE, 500L)

        assertEquals(CompanionCommitmentStatus.DUE, commitments.single().status)
        assertEquals(500L, commitments.single().dueAt)
        assertEquals(null, commitments.single().resolvedAt)
    }

    @Test
    fun `invalid transition is ignored`() {
        val result = transition(
            commitments = listOf(commitment(status = CompanionCommitmentStatus.ACTIVE)),
            status = CompanionCommitmentStatus.FULFILLED,
            nowMillis = 20L,
        )

        assertEquals(CompanionCommitmentStatus.ACTIVE, result.single().status)
    }

    @Test
    fun `unrelated new commitment never removes active commitment`() {
        val current = commitment(id = "wake", subjectKey = "wake:08:00")
        val other = commitment(id = "exam", subjectKey = "exam:2026-12-20")

        val result = CompanionCommitmentReducer.apply(
            current = listOf(current),
            changes = listOf(CompanionCommitmentChange.Upsert(other)),
            nowMillis = 20L,
        )

        assertEquals(setOf("wake", "exam"), result.map { it.id }.toSet())
        assertEquals(2, result.count { it.status == CompanionCommitmentStatus.ACTIVE })
    }

    @Test
    fun `new version of same subject supersedes old commitment`() {
        val current = commitment(id = "old", subjectKey = "wake:08:00")
        val replacement = commitment(id = "new", subjectKey = " wake : 08:00 ", dueAt = 200L)

        val result = CompanionCommitmentReducer.apply(
            current = listOf(current),
            changes = listOf(CompanionCommitmentChange.Upsert(replacement)),
            nowMillis = 20L,
        )

        assertEquals(2, result.size)
        assertEquals(CompanionCommitmentStatus.SUPERSEDED, result.single { it.id == "old" }.status)
        assertEquals(CompanionCommitmentStatus.ACTIVE, result.single { it.id == "new" }.status)
    }

    @Test
    fun `technical planner details never enter commitment state`() {
        val result = CompanionCommitmentReducer.apply(
            current = emptyList(),
            changes = listOf(
                CompanionCommitmentChange.Upsert(
                    commitment(
                        promise = "我会晚点再来看看。\nget_app_usage suggested args={}",
                    ),
                ),
            ),
            nowMillis = 20L,
        )

        assertEquals("我会晚点再来看看。", result.single().promise)
    }

    private fun transition(
        commitments: List<CompanionCommitment>,
        status: CompanionCommitmentStatus,
        nowMillis: Long,
    ): List<CompanionCommitment> = CompanionCommitmentReducer.apply(
        current = commitments,
        changes = listOf(
            CompanionCommitmentChange.Transition(
                assistantId = ASSISTANT_ID,
                commitmentId = COMMITMENT_ID,
                status = status,
                reason = "test transition",
            ),
        ),
        nowMillis = nowMillis,
    )

    private fun commitment(
        id: String = COMMITMENT_ID,
        assistantId: String = ASSISTANT_ID,
        subjectKey: String = "wake:08:00",
        dueAt: Long = 100L,
        status: CompanionCommitmentStatus = CompanionCommitmentStatus.ACTIVE,
        promise: String = "早上叫醒用户",
    ): CompanionCommitment = CompanionCommitment(
        id = id,
        assistantId = assistantId,
        subjectKey = subjectKey,
        promise = promise,
        dueAt = dueAt,
        status = status,
        actionPlan = CompanionActionPlan(type = CompanionActionType.ALARM, toolName = "set_alarm"),
        createdAt = 10L,
        updatedAt = 10L,
    )

    private companion object {
        const val ASSISTANT_ID = "assistant-a"
        const val COMMITMENT_ID = "commitment-1"
    }
}
