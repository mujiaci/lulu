package me.rerere.rikkahub.data.service

import me.rerere.rikkahub.data.companion.CompanionConcernStatus
import me.rerere.rikkahub.data.companion.CompanionSnapshot
import me.rerere.rikkahub.data.datastore.ProactiveMessageSetting
import java.util.concurrent.TimeUnit

data class CompanionAutonomousPulseInput(
    val setting: ProactiveMessageSetting,
    val snapshot: CompanionSnapshot,
    val minutesSinceLastChat: Long,
    val activeTargetedTriggerMillis: Long = 0L,
    val nowMillis: Long = System.currentTimeMillis(),
)

data class CompanionAutonomousPulsePlan(
    val delayMinutes: Int,
    val reason: String,
)

object CompanionAutonomousPulsePlanner {
    fun planNext(input: CompanionAutonomousPulseInput): CompanionAutonomousPulsePlan {
        val minMinutes = input.setting.minIntervalMinutes.coerceAtLeast(1)
        val maxMinutes = input.setting.maxIntervalMinutes.coerceAtLeast(minMinutes)
        val targetedDelay = input.activeTargetedTriggerMillis
            .takeIf { it > input.nowMillis }
            ?.let { ((it - input.nowMillis) / 60_000L).toInt().coerceAtLeast(1) }
        if (targetedDelay != null) {
            return CompanionAutonomousPulsePlan(
                delayMinutes = targetedDelay + if (input.setting.naturalScheduling) 15 else minMinutes,
                reason = "targeted_active",
            )
        }

        val activeWorkCount = input.snapshot.activeWorkCount(input.nowMillis)
        if (input.setting.naturalScheduling) {
            val naturalDelay = when {
                input.snapshot.relationship.unresolvedTension >= 0.6f -> 180..300
                activeWorkCount > 0 && input.minutesSinceLastChat >= 45 -> 8..18
                activeWorkCount > 0 -> 18..35
                input.minutesSinceLastChat >= 360 -> 25..50
                input.minutesSinceLastChat >= 120 -> 18..40
                input.minutesSinceLastChat >= 45 -> 55..100
                else -> 100..180
            }.stableMinute(input)
            return CompanionAutonomousPulsePlan(
                delayMinutes = naturalDelay,
                reason = "natural;${buildReason(input, activeWorkCount)}",
            )
        }
        val desired = when {
            input.snapshot.relationship.unresolvedTension >= 0.6f -> maxMinutes
            activeWorkCount > 0 -> when {
                input.minutesSinceLastChat >= 45 -> minMinutes - 12
                else -> minMinutes - 5
            }
            input.minutesSinceLastChat >= 120 -> minMinutes - 18
            else -> (minMinutes + maxMinutes) / 2
        }
        return CompanionAutonomousPulsePlan(
            delayMinutes = desired.coerceIn(minOf(8, maxMinutes), maxMinutes),
            reason = buildReason(input, activeWorkCount),
        )
    }

    fun triggerTimeMillis(
        input: CompanionAutonomousPulseInput,
        plan: CompanionAutonomousPulsePlan,
    ): Long = input.nowMillis + TimeUnit.MINUTES.toMillis(plan.delayMinutes.toLong())

    private fun buildReason(input: CompanionAutonomousPulseInput, activeWorkCount: Int): String = buildList {
        when {
            input.snapshot.relationship.unresolvedTension >= 0.6f -> add("relationship_tension")
            activeWorkCount > 0 -> add("active_work")
            input.minutesSinceLastChat >= 120 -> add("long_silence")
            else -> add("steady_background")
        }
        add("silence=${input.minutesSinceLastChat}")
        add("active=$activeWorkCount")
    }.joinToString(";")

    private fun CompanionSnapshot.activeWorkCount(nowMillis: Long): Int =
        concerns.count { concern ->
            concern.status == CompanionConcernStatus.ACTIVE &&
                concern.nextPerceptionAt?.let { it <= nowMillis } == true
        }

    private fun IntRange.stableMinute(input: CompanionAutonomousPulseInput): Int {
        if (first >= last) return first
        val seed = input.nowMillis / 60_000L +
            input.snapshot.updatedAt / 60_000L +
            input.minutesSinceLastChat.coerceAtMost(10_000L)
        return first + Math.floorMod(seed, (last - first + 1).toLong()).toInt()
    }

}
