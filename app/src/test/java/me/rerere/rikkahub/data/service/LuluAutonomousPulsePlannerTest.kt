package me.rerere.rikkahub.data.service

import me.rerere.rikkahub.data.datastore.ProactiveMessageSetting
import me.rerere.rikkahub.data.model.LuluEnergy
import me.rerere.rikkahub.data.model.LuluMood
import me.rerere.rikkahub.data.model.LuluState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class LuluAutonomousPulsePlannerTest {
    private val assistantId = Uuid.parse("11111111-2222-3333-4444-555555555555")
    private val setting = ProactiveMessageSetting(
        enabled = true,
        minIntervalMinutes = 30,
        maxIntervalMinutes = 90,
    )

    @Test
    fun `pending thoughts make next pulse sooner`() {
        val plan = LuluAutonomousPulsePlanner.planNext(
            input = LuluAutonomousPulseInput(
                setting = setting,
                state = LuluState(
                    assistantId = assistantId,
                    mood = LuluMood.SOFT,
                    energy = LuluEnergy.NORMAL,
                ),
                minutesSinceLastChat = 50,
                pendingThoughtCount = 2,
            )
        )

        assertEquals(18, plan.delayMinutes)
        assertTrue(plan.reason.contains("pending_thought"))
    }

    @Test
    fun `long silence with enough energy checks back soon`() {
        val plan = LuluAutonomousPulsePlanner.planNext(
            input = LuluAutonomousPulseInput(
                setting = setting,
                state = LuluState(
                    assistantId = assistantId,
                    mood = LuluMood.HAPPY,
                    energy = LuluEnergy.HIGH,
                ),
                minutesSinceLastChat = 160,
                pendingThoughtCount = 0,
            )
        )

        assertEquals(12, plan.delayMinutes)
        assertTrue(plan.reason.contains("long_silence"))
    }

    @Test
    fun `sleepy or resting state uses a slower pulse`() {
        val plan = LuluAutonomousPulsePlanner.planNext(
            input = LuluAutonomousPulseInput(
                setting = setting,
                state = LuluState(
                    assistantId = assistantId,
                    mood = LuluMood.CALM,
                    energy = LuluEnergy.SLEEPY,
                ),
                minutesSinceLastChat = 20,
                pendingThoughtCount = 0,
            )
        )

        assertEquals(90, plan.delayMinutes)
        assertTrue(plan.reason.contains("low_energy"))
    }

    @Test
    fun `targeted trigger keeps normal background pulse away`() {
        val plan = LuluAutonomousPulsePlanner.planNext(
            input = LuluAutonomousPulseInput(
                setting = setting,
                state = LuluState(assistantId = assistantId),
                minutesSinceLastChat = 40,
                pendingThoughtCount = 3,
                activeTargetedTriggerMillis = 1_700_000_000_000L,
                nowMillis = 1_699_996_760_000L,
            )
        )

        assertEquals(84, plan.delayMinutes)
        assertTrue(plan.reason.contains("targeted_active"))
    }
}
