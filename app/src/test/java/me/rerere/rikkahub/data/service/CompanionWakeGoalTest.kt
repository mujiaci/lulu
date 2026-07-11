package me.rerere.rikkahub.data.service

import me.rerere.rikkahub.data.companion.CompanionContextFact
import org.junit.Assert.assertEquals
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
                latestDeviceActivityAt = null,
                screenInteractive = false,
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
                latestDeviceActivityAt = null,
                screenInteractive = false,
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
                latestDeviceActivityAt = null,
                screenInteractive = true,
                perceivedUserState = "awake",
            )
        )
    }

    @Test
    fun `wake goal does not trust model awake state while screen is off`() {
        assertFalse(
            shouldCompleteWakeGoal(
                wakeTargetAt = 1_000L,
                latestUserMessageAt = null,
                latestDeviceActivityAt = null,
                screenInteractive = false,
                perceivedUserState = "awake",
            )
        )
    }

    @Test
    fun `wake goal completes after foreground app activity following target`() {
        assertTrue(
            shouldCompleteWakeGoal(
                wakeTargetAt = 1_000L,
                latestUserMessageAt = null,
                latestDeviceActivityAt = 1_100L,
                screenInteractive = true,
                perceivedUserState = "uncertain",
            )
        )
    }

    @Test
    fun `latest foreground activity is read from passive app usage`() {
        val activityAt = latestForegroundUsageAt(
            listOf(
                CompanionContextFact(
                    key = "perception.get_app_usage",
                    value = """{"success":true,"apps":[{"last_used_at_millis":900},{"last_used_at_millis":1200}]}""",
                    observedAt = 1_300L,
                )
            )
        )

        assertEquals(1_200L, activityAt)
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
