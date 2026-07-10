package me.rerere.rikkahub.data.companion

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.utils.JsonInstant

internal fun CompanionCommitment.isWakeGoal(): Boolean = actionPlan.category == "wake"

internal fun CompanionCommitment.isSleepSupervisionGoal(): Boolean =
    actionPlan.category == "sleep_supervision"

internal fun CompanionCommitment.wakeTargetAtOrNull(): Long? = actionPlan.argumentsJson
    .parseCompanionActionArguments()
    ?.get("wakeTargetAt")
    ?.jsonPrimitive
    ?.contentOrNull
    ?.toLongOrNull()

internal fun CompanionCommitment.retryMinutesOrDefault(): Int = actionPlan.argumentsJson
    .parseCompanionActionArguments()
    ?.get("retryMinutes")
    ?.jsonPrimitive
    ?.contentOrNull
    ?.toIntOrNull()
    ?.coerceIn(1, 60)
    ?: if (isWakeGoal()) 5 else 15

private fun String.parseCompanionActionArguments(): JsonObject? = runCatching {
    JsonInstant.parseToJsonElement(ifBlank { "{}" }) as? JsonObject
}.getOrNull()
