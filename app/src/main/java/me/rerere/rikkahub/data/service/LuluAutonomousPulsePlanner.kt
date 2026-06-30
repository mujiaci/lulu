package me.rerere.rikkahub.data.service

import me.rerere.rikkahub.data.datastore.ProactiveMessageSetting
import me.rerere.rikkahub.data.model.LuluEnergy
import me.rerere.rikkahub.data.model.LuluMode
import me.rerere.rikkahub.data.model.LuluMood
import me.rerere.rikkahub.data.model.LuluState
import java.util.concurrent.TimeUnit

data class LuluAutonomousPulseInput(
    val setting: ProactiveMessageSetting,
    val state: LuluState,
    val minutesSinceLastChat: Long,
    val pendingThoughtCount: Int,
    val activeTargetedTriggerMillis: Long = 0L,
    val nowMillis: Long = System.currentTimeMillis(),
)

data class LuluAutonomousPulsePlan(
    val delayMinutes: Int,
    val reason: String,
)

object LuluAutonomousPulsePlanner {
    fun planNext(input: LuluAutonomousPulseInput): LuluAutonomousPulsePlan {
        val minMinutes = input.setting.minIntervalMinutes.coerceAtLeast(1)
        val maxMinutes = input.setting.maxIntervalMinutes.coerceAtLeast(minMinutes)
        val targetedDelay = input.activeTargetedTriggerMillis
            .takeIf { it > input.nowMillis }
            ?.let { ((it - input.nowMillis) / 60_000L).toInt().coerceAtLeast(1) }
        if (targetedDelay != null) {
            val delay = (targetedDelay + minMinutes).coerceIn(minMinutes, maxMinutes)
            return LuluAutonomousPulsePlan(
                delayMinutes = delay,
                reason = "targeted_active",
            )
        }

        val desired = when {
            input.state.energy == LuluEnergy.SLEEPY ||
                input.state.energy == LuluEnergy.LOW ||
                input.state.mode == LuluMode.RESTING -> maxMinutes
            input.pendingThoughtCount > 0 -> when {
                input.minutesSinceLastChat >= 45 -> minMinutes - 12
                else -> minMinutes - 5
            }
            input.minutesSinceLastChat >= 120 &&
                input.state.energy in setOf(LuluEnergy.NORMAL, LuluEnergy.HIGH) -> minMinutes - 18
            input.state.mood == LuluMood.LONELY -> minMinutes - 10
            input.state.mode == LuluMode.LEARNING -> minMinutes + 20
            else -> ((minMinutes + maxMinutes) / 2)
        }
        return LuluAutonomousPulsePlan(
            delayMinutes = desired.coerceIn(8, maxMinutes),
            reason = buildReason(input),
        )
    }

    fun triggerTimeMillis(
        input: LuluAutonomousPulseInput,
        plan: LuluAutonomousPulsePlan,
    ): Long = input.nowMillis + TimeUnit.MINUTES.toMillis(plan.delayMinutes.toLong())

    private fun buildReason(input: LuluAutonomousPulseInput): String = buildList {
        when {
            input.state.energy == LuluEnergy.SLEEPY ||
                input.state.energy == LuluEnergy.LOW ||
                input.state.mode == LuluMode.RESTING -> add("low_energy")
            input.pendingThoughtCount > 0 -> add("pending_thought")
            input.minutesSinceLastChat >= 120 -> add("long_silence")
            input.state.mood == LuluMood.LONELY -> add("lonely")
            input.state.mode == LuluMode.LEARNING -> add("learning_scene")
            else -> add("steady_background")
        }
        add("silence=${input.minutesSinceLastChat}")
        add("pending=${input.pendingThoughtCount}")
    }.joinToString(";")
}
