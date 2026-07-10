package me.rerere.rikkahub.data.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionWakeGoalTest {
    @Test
    fun `wake goal completes when user replies after target`() {
        assertTrue(
            shouldCompleteWakeGoal(
                wakeTargetAt = 1_000L,
                latestUserMessageAt = 1_100L,
                perceivedUserState = "uncertain",
            )
        )
    }

    @Test
    fun `wake goal continues while user is not confirmed awake`() {
        assertFalse(
            shouldCompleteWakeGoal(
                wakeTargetAt = 1_000L,
                latestUserMessageAt = 900L,
                perceivedUserState = "asleep",
            )
        )
    }

    @Test
    fun `wake goal accepts current perception that user is awake`() {
        assertTrue(
            shouldCompleteWakeGoal(
                wakeTargetAt = 1_000L,
                latestUserMessageAt = null,
                perceivedUserState = "awake",
            )
        )
    }

    @Test
    fun `sleep supervision stops quietly when screen is off and there is no new message`() {
        assertTrue(
            shouldCompleteSleepSupervision(
                wakeTargetAt = 10_000L,
                nowMillis = 2_000L,
                observationDueAt = 1_500L,
                latestUserMessageAt = 1_000L,
                screenInteractive = false,
                perceivedUserState = null,
            )
        )
    }

    @Test
    fun `sleep supervision keeps checking while phone remains active`() {
        assertFalse(
            shouldCompleteSleepSupervision(
                wakeTargetAt = 10_000L,
                nowMillis = 2_000L,
                observationDueAt = 1_500L,
                latestUserMessageAt = 1_800L,
                screenInteractive = true,
                perceivedUserState = "awake",
            )
        )
    }
}
