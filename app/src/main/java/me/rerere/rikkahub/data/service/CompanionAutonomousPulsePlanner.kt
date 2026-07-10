package me.rerere.rikkahub.data.service

import me.rerere.rikkahub.data.companion.CompanionCommitmentStatus
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
            val delay = (targetedDelay + minMinutes).coerceIn(minMinutes, maxMinutes)
            return CompanionAutonomousPulsePlan(
                delayMinutes = delay,
                reason = "targeted_active",
            )
        }

        val activeWorkCount = input.snapshot.activeWorkCount(input.nowMillis)
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
                (concern.nextPerceptionAt == null || concern.nextPerceptionAt <= nowMillis)
        } +
            commitments.count { commitment ->
                commitment.dueAt <= nowMillis && commitment.status in ACTIONABLE_COMMITMENT_STATUSES
            }

    private val ACTIONABLE_COMMITMENT_STATUSES = setOf(
        CompanionCommitmentStatus.ACTIVE,
        CompanionCommitmentStatus.DUE,
        CompanionCommitmentStatus.EXECUTING,
        CompanionCommitmentStatus.RETRY_SCHEDULED,
    )
}
