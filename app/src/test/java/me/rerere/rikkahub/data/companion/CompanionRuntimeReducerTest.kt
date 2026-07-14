package me.rerere.rikkahub.data.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionRuntimeReducerTest {
    @Test
    fun `completed life events update neuro state once and remain idempotent`() {
        val event = CompanionLifeEvent(
            id = "music-event",
            assistantId = ASSISTANT_A,
            type = CompanionLifeEventType.MUSIC,
            title = "操作了手机里的音乐",
            evidenceReference = "tool-call-1",
            startedAt = 100L,
            endedAt = 100L,
            createdAt = 100L,
        )

        val first = reduceCompanionRuntimeState(
            current = CompanionPersistedState(),
            mutation = CompanionTurnMutation(
                assistantId = ASSISTANT_A,
                lifeEvents = listOf(event),
                nowMillis = 100L,
            ),
        )
        val duplicate = reduceCompanionRuntimeState(
            current = first.persistedState,
            mutation = CompanionTurnMutation(
                assistantId = ASSISTANT_A,
                lifeEvents = listOf(event),
                nowMillis = 100L,
            ),
        )

        assertEquals(listOf("music-event"), duplicate.snapshot.lifeEvents.map { it.id })
        assertTrue(first.snapshot.neuroState.dopamine > 0.5f)
        assertEquals(first.snapshot.neuroState, duplicate.snapshot.neuroState)
        val authenticLifeGoal = duplicate.snapshot.goals.single { it.id.endsWith(":goal:authentic-life") }
        assertTrue(authenticLifeGoal.progress > 0f)
        assertEquals(listOf("music-event"), authenticLifeGoal.evidenceEventIds)
    }

    @Test
    fun `meaningful state changes are appended to state history without timestamp duplicates`() {
        val firstState = CompanionState(statusText = "在等你回话", innerThought = "我想再等等。", updatedAt = 100L)
        val first = reduceCompanionRuntimeState(
            current = CompanionPersistedState(),
            mutation = CompanionTurnMutation(assistantId = ASSISTANT_A, state = firstState, nowMillis = 100L),
        )
        val sameContent = reduceCompanionRuntimeState(
            current = first.persistedState,
            mutation = CompanionTurnMutation(
                assistantId = ASSISTANT_A,
                state = firstState.copy(updatedAt = 200L),
                nowMillis = 200L,
            ),
        )
        val changed = reduceCompanionRuntimeState(
            current = sameContent.persistedState,
            mutation = CompanionTurnMutation(
                assistantId = ASSISTANT_A,
                state = firstState.copy(statusText = "准备提醒你休息", updatedAt = 300L),
                nowMillis = 300L,
            ),
        )

        assertEquals(1, first.snapshot.stateHistory.size)
        assertEquals(1, sameContent.snapshot.stateHistory.size)
        assertEquals(2, changed.snapshot.stateHistory.size)
        assertEquals("准备提醒你休息", changed.snapshot.stateHistory.last().state.statusText)
    }

    @Test
    fun `same follow up draft produces stable concern and commitment identity`() {
        val draft = CompanionFollowUpDraft(
            assistantId = ASSISTANT_A,
            category = "schedule",
            reason = "check the user's schedule",
            sourceText = "class starts at eight",
            dueAt = 500L,
            sourceConversationId = "conversation-1",
            sourceMessageId = "message-1",
            preferredToolNames = listOf("calendar_tool"),
        )

        val firstConcern = draft.toConcern(nowMillis = 100L)
        val secondConcern = draft.toConcern(nowMillis = 200L)
        val commitment = draft.toCommitment(nowMillis = 100L)
        val rescheduled = draft.copy(dueAt = 900L).toCommitment(nowMillis = 200L)

        assertEquals(firstConcern.id, secondConcern.id)
        assertEquals(firstConcern.subjectKey, commitment.subjectKey)
        assertEquals(commitment.id, rescheduled.id)
        assertEquals(commitment.subjectKey, rescheduled.subjectKey)
        assertEquals(CompanionActionType.CHECK_IN, commitment.actionPlan.type)
        assertEquals(listOf("calendar_tool"), commitment.actionPlan.preferredToolNames)
    }

    @Test
    fun `rescheduling an existing follow up preserves the same concern and commitment identity`() {
        val first = CompanionFollowUpDraft(
            assistantId = ASSISTANT_A,
            category = "wake",
            reason = "wake the user",
            sourceText = "明天七点叫我起床",
            dueAt = 500L,
            sourceConversationId = "conversation-1",
        )
        val existing = snapshot(commitments = listOf(first.toCommitment(100L)))
        val second = first.copy(
            reason = "move the wake time",
            sourceText = "改到八点",
            dueAt = 900L,
            sourceMessageId = "message-2",
        )
        val reconciled = reconcileCompanionFollowUpDrafts(
            drafts = listOf(second),
            snapshot = existing,
            latestUserText = "改到八点叫我",
        ).single()

        assertEquals(first.toCommitment(100L).id, reconciled.toCommitment(200L).id)
        assertEquals(first.toConcern(100L).subjectKey, reconciled.toConcern(200L).subjectKey)
        assertEquals(900L, reconciled.toCommitment(200L).dueAt)
    }

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
    fun `begin execution recovers an interrupted stale commitment`() {
        val stale = commitment(
            status = CompanionCommitmentStatus.EXECUTING,
            attemptCount = 1,
        ).copy(updatedAt = 100L)

        val reduced = beginCompanionCommitment(
            current = persisted(snapshot(commitments = listOf(stale))),
            assistantId = ASSISTANT_A,
            commitmentId = stale.id,
            nowMillis = 400_000L,
        )

        val recovered = reduced.snapshot.commitments.single()
        assertEquals(CompanionCommitmentStatus.EXECUTING, recovered.status)
        assertEquals(2, recovered.attemptCount)
        assertNotNull(reduced.affectedCommitment)
    }

    @Test
    fun `begin execution does not duplicate a fresh executing commitment`() {
        val executing = commitment(status = CompanionCommitmentStatus.EXECUTING).copy(updatedAt = 100L)

        val reduced = beginCompanionCommitment(
            current = persisted(snapshot(commitments = listOf(executing))),
            assistantId = ASSISTANT_A,
            commitmentId = executing.id,
            nowMillis = 200L,
        )

        assertNull(reduced.affectedCommitment)
        assertEquals(CompanionCommitmentStatus.EXECUTING, reduced.snapshot.commitments.single().status)
    }

    @Test
    fun `successful execution stores result and fulfills commitment`() {
        val executing = commitment(status = CompanionCommitmentStatus.EXECUTING, attemptCount = 1)
        val concern = CompanionConcern(
            id = "concern-1",
            assistantId = ASSISTANT_A,
            subjectKey = executing.subjectKey,
            event = "follow up",
            goal = "follow up",
        )
        val result = CompanionActionResult(
            success = true,
            summary = "message delivered",
            completedAt = 300L,
        )

        val reduced = finishCompanionCommitment(
            current = persisted(snapshot(commitments = listOf(executing)).copy(concerns = listOf(concern))),
            assistantId = ASSISTANT_A,
            commitmentId = executing.id,
            result = result,
        )

        val fulfilled = reduced.snapshot.commitments.single()
        assertEquals(CompanionCommitmentStatus.FULFILLED, fulfilled.status)
        assertEquals(result, fulfilled.lastActionResult)
        assertEquals(300L, fulfilled.resolvedAt)
        assertEquals(0.53f, reduced.snapshot.relationship.reliability)
        assertEquals(0.51f, reduced.snapshot.relationship.trust)
        assertEquals(1, reduced.persistedState.appliedRelationshipEventIds.size)
        assertEquals(1, reduced.snapshot.relationshipHistory.size)
        assertEquals(
            CompanionRelationshipEventKind.COMMITMENT_FULFILLED,
            reduced.snapshot.relationshipHistory.single().kind,
        )
        assertEquals(CompanionConcernStatus.COMPLETED, reduced.snapshot.concerns.single().status)
    }

    @Test
    fun `user evidence fulfills a commitment while wake execution is running`() {
        val executing = commitment(status = CompanionCommitmentStatus.EXECUTING, attemptCount = 1)
        val concern = concernFor(executing)

        val reduced = fulfillCompanionCommitmentFromEvidence(
            current = persisted(snapshot(commitments = listOf(executing)).copy(concerns = listOf(concern))),
            assistantId = ASSISTANT_A,
            commitmentId = executing.id,
            summary = "User sent a message after the wake target",
            completedAt = 300L,
        )

        val fulfilled = reduced.snapshot.commitments.single()
        assertEquals(CompanionCommitmentStatus.FULFILLED, fulfilled.status)
        assertEquals(true, fulfilled.lastActionResult?.success)
        assertEquals(300L, fulfilled.resolvedAt)
        assertEquals(CompanionConcernStatus.COMPLETED, reduced.snapshot.concerns.single().status)
        assertEquals(0.53f, reduced.snapshot.relationship.reliability)
    }

    @Test
    fun `user evidence starts and fulfills a due active commitment atomically`() {
        val active = commitment(status = CompanionCommitmentStatus.ACTIVE)

        val reduced = fulfillCompanionCommitmentFromEvidence(
            current = persisted(snapshot(commitments = listOf(active))),
            assistantId = ASSISTANT_A,
            commitmentId = active.id,
            summary = "User activity confirmed after target time",
            completedAt = 300L,
        )

        val fulfilled = reduced.snapshot.commitments.single()
        assertEquals(CompanionCommitmentStatus.FULFILLED, fulfilled.status)
        assertEquals(0, fulfilled.attemptCount)
        assertEquals(300L, fulfilled.resolvedAt)
    }

    @Test
    fun `user evidence fulfills a wake retry before its next scheduled attempt`() {
        val retryScheduled = commitment(
            status = CompanionCommitmentStatus.RETRY_SCHEDULED,
            attemptCount = 1,
        ).copy(dueAt = 500L)

        val reduced = fulfillCompanionCommitmentFromEvidence(
            current = persisted(snapshot(commitments = listOf(retryScheduled))),
            assistantId = ASSISTANT_A,
            commitmentId = retryScheduled.id,
            summary = "User replied before the next wake retry",
            completedAt = 300L,
        )

        val fulfilled = reduced.snapshot.commitments.single()
        assertEquals(CompanionCommitmentStatus.FULFILLED, fulfilled.status)
        assertEquals(1, fulfilled.attemptCount)
        assertEquals(300L, fulfilled.resolvedAt)
    }

    @Test
    fun `user can cancel a commitment while execution is running`() {
        val executing = commitment(status = CompanionCommitmentStatus.EXECUTING, attemptCount = 1)

        val reduced = cancelCompanionCommitment(
            current = persisted(snapshot(commitments = listOf(executing))),
            assistantId = ASSISTANT_A,
            commitmentId = executing.id,
            reason = "User cancelled wake-up supervision",
            nowMillis = 300L,
        )

        assertEquals(CompanionCommitmentStatus.CANCELLED, reduced.snapshot.commitments.single().status)
        assertEquals(executing.id, reduced.affectedCommitment?.id)
    }

    @Test
    fun `failed execution stores error and schedules retry`() {
        val executing = commitment(status = CompanionCommitmentStatus.EXECUTING, attemptCount = 1)
        val concern = concernFor(executing)
        val result = CompanionActionResult(
            success = false,
            summary = "provider unavailable",
            completedAt = 300L,
        )

        val reduced = finishCompanionCommitment(
            current = persisted(snapshot(commitments = listOf(executing)).copy(concerns = listOf(concern))),
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
        assertEquals(0.47f, reduced.snapshot.relationship.reliability)
        assertEquals(0.49f, reduced.snapshot.relationship.trust)
        assertEquals(0.02f, reduced.snapshot.relationship.unresolvedTension)
        assertEquals(CompanionConcernStatus.ACTIVE, reduced.snapshot.concerns.single().status)
        assertEquals(900L, reduced.snapshot.concerns.single().nextPerceptionAt)
    }

    @Test
    fun `continuing wake execution reschedules without relationship penalty`() {
        val executing = commitment(status = CompanionCommitmentStatus.EXECUTING, attemptCount = 1)
        val concern = concernFor(executing)
        val result = CompanionActionResult(
            success = true,
            summary = "wake message sent but user is not confirmed awake",
            completedAt = 300L,
        )

        val reduced = continueCompanionCommitment(
            current = persisted(snapshot(commitments = listOf(executing)).copy(concerns = listOf(concern))),
            assistantId = ASSISTANT_A,
            commitmentId = executing.id,
            result = result,
            nextDueAt = 600L,
        )

        val continued = reduced.snapshot.commitments.single()
        assertEquals(CompanionCommitmentStatus.RETRY_SCHEDULED, continued.status)
        assertEquals(600L, continued.dueAt)
        assertEquals(0.5f, reduced.snapshot.relationship.reliability)
        assertEquals(0.5f, reduced.snapshot.relationship.trust)
        assertEquals(CompanionConcernStatus.ACTIVE, reduced.snapshot.concerns.single().status)
        assertEquals(600L, reduced.snapshot.concerns.single().nextPerceptionAt)
    }

    @Test
    fun `final execution failure cancels the matching concern`() {
        val executing = commitment(status = CompanionCommitmentStatus.EXECUTING, attemptCount = 3)
        val concern = concernFor(executing)
        val result = CompanionActionResult(
            success = false,
            summary = "retry limit reached",
            completedAt = 300L,
        )

        val reduced = finishCompanionCommitment(
            current = persisted(snapshot(commitments = listOf(executing)).copy(concerns = listOf(concern))),
            assistantId = ASSISTANT_A,
            commitmentId = executing.id,
            result = result,
        )

        assertEquals(CompanionCommitmentStatus.FAILED, reduced.snapshot.commitments.single().status)
        assertEquals(CompanionConcernStatus.CANCELLED, reduced.snapshot.concerns.single().status)
        assertEquals("retry limit reached", reduced.snapshot.concerns.single().completedReason)
    }

    @Test
    fun `cancelling an active commitment records an explicit reason`() {
        val active = commitment()
        val concern = concernFor(active)

        val reduced = cancelCompanionCommitment(
            current = persisted(snapshot(commitments = listOf(active)).copy(concerns = listOf(concern))),
            assistantId = ASSISTANT_A,
            commitmentId = active.id,
            reason = "proactive messaging disabled",
            nowMillis = 250L,
        )

        val cancelled = reduced.snapshot.commitments.single()
        assertEquals(CompanionCommitmentStatus.CANCELLED, cancelled.status)
        assertEquals("proactive messaging disabled", cancelled.statusReason)
        assertEquals(250L, cancelled.resolvedAt)
        assertEquals(CompanionConcernStatus.CANCELLED, reduced.snapshot.concerns.single().status)
        assertEquals("proactive messaging disabled", reduced.snapshot.concerns.single().completedReason)
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
        val persistedAssistantB = reduced.persistedState.snapshots.single { it.assistantId == ASSISTANT_B }
        assertEquals(assistantB, persistedAssistantB.copy(goals = emptyList()))
        assertEquals(defaultCompanionGoals(ASSISTANT_B).map { it.id }, persistedAssistantB.goals.map { it.id })
        assertEquals(emptyList<CompanionConcern>(), reduced.snapshot.concerns)
        assertEquals(emptyList<CompanionCommitment>(), reduced.snapshot.commitments)
        assertEquals(0.5f, reduced.snapshot.relationship.trust)
        assertNotNull(reduced.persistedState.snapshots.singleOrNull { it.assistantId == ASSISTANT_A })
    }

    @Test
    fun `global next commitment selects earliest work across assistants`() {
        val assistantA = snapshot(
            assistantId = ASSISTANT_A,
            commitments = listOf(commitment(assistantId = ASSISTANT_A).copy(dueAt = 500L)),
        )
        val assistantB = snapshot(
            assistantId = ASSISTANT_B,
            commitments = listOf(commitment(id = "b", assistantId = ASSISTANT_B).copy(dueAt = 300L)),
        )

        val next = selectNextCompanionCommitment(
            snapshots = listOf(assistantA, assistantB),
            nowMillis = 200L,
        )

        assertEquals("b", next?.id)
        assertEquals(ASSISTANT_B, next?.assistantId)
    }

    @Test
    fun `global next commitment includes stale interrupted execution`() {
        val stale = commitment(status = CompanionCommitmentStatus.EXECUTING)
            .copy(dueAt = 100L, updatedAt = 100L)

        val next = selectNextCompanionCommitment(
            snapshots = listOf(snapshot(commitments = listOf(stale))),
            nowMillis = 400_000L,
        )

        assertEquals(stale.id, next?.id)
    }

    @Test
    fun `global next commitment schedules fresh execution at recovery boundary`() {
        val executing = commitment(status = CompanionCommitmentStatus.EXECUTING)
            .copy(dueAt = 100L, updatedAt = 100L)

        val next = selectNextCompanionCommitment(
            snapshots = listOf(snapshot(commitments = listOf(executing))),
            nowMillis = 200L,
        )

        assertEquals(executing.id, next?.id)
        assertEquals(300_100L, next?.dueAt)
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

    private fun concernFor(commitment: CompanionCommitment) = CompanionConcern(
        id = "concern-${commitment.id}",
        assistantId = commitment.assistantId,
        subjectKey = commitment.subjectKey,
        event = "follow up",
        goal = "follow up",
        nextPerceptionAt = commitment.dueAt,
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
