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
    fun `commitment keeps structured responsibility and complete transition history`() {
        val original = commitment(status = CompanionCommitmentStatus.PROPOSED).copy(
            promisorId = "assistant-a",
            beneficiary = "user",
            responsibility = "每天监督学习",
            schedule = CompanionCommitmentSchedule(
                timeDescription = "每天 20:00",
                frequency = "每天",
                condition = "用户尚未完成复习",
            ),
            executionMethod = "发送提醒并确认结果",
        )
        var result = CompanionCommitmentReducer.apply(
            current = emptyList(),
            changes = listOf(CompanionCommitmentChange.Upsert(original)),
            nowMillis = 10L,
        )
        result = transition(result, CompanionCommitmentStatus.ACTIVE, 20L)
        result = transition(result, CompanionCommitmentStatus.DUE, 30L)
        val commitment = result.single()
        assertEquals("assistant-a", commitment.promisorId)
        assertEquals("每天", commitment.schedule.frequency)
        assertEquals("用户尚未完成复习", commitment.schedule.condition)
        assertEquals(
            listOf(
                CompanionCommitmentStatus.PROPOSED,
                CompanionCommitmentStatus.ACTIVE,
                CompanionCommitmentStatus.DUE,
            ),
            commitment.history.map { it.toStatus },
        )
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

    @Test
    fun `one message with several explicit responsibilities becomes separate commitments`() {
        val result = CompanionCommitmentReducer.apply(
            current = emptyList(),
            changes = listOf(
                CompanionCommitmentChange.Upsert(
                    commitment(
                        id = "supervision",
                        subjectKey = "daily-supervision",
                        promise = "监督起床、监督睡觉、监督学习",
                    ).copy(
                        responsibility = "监督起床、监督睡觉、监督学习",
                        schedule = CompanionCommitmentSchedule(frequency = "每天"),
                    ),
                ),
            ),
            nowMillis = 20L,
        )

        assertEquals(3, result.size)
        assertEquals(
            setOf("监督起床", "监督睡觉", "监督学习"),
            result.map { it.responsibility }.toSet(),
        )
        assertEquals(setOf("每天"), result.map { it.schedule.frequency }.toSet())
        assertEquals(3, result.map { it.subjectKey }.distinct().size)
    }

    @Test
    fun `ordinary enumeration inside one promise is not split accidentally`() {
        val result = CompanionCommitmentReducer.apply(
            current = emptyList(),
            changes = listOf(
                CompanionCommitmentChange.Upsert(
                    commitment(promise = "准备书本、耳机和水杯"),
                ),
            ),
            nowMillis = 20L,
        )

        assertEquals(1, result.size)
        assertEquals("准备书本、耳机和水杯", result.single().promise)
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
