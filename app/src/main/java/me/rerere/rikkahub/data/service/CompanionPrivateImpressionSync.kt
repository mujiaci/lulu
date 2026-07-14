package me.rerere.rikkahub.data.service

import android.util.Log
import me.rerere.rikkahub.data.companion.CompanionRuntime
import me.rerere.rikkahub.data.companion.CompanionTurnMutation

suspend fun MemoryBankService.syncCompanionPrivateImpression(
    companionRuntime: CompanionRuntime,
    assistantId: String,
    nowMillis: Long = System.currentTimeMillis(),
) {
    if (assistantId.isBlank()) return
    val previous = companionRuntime.snapshot(assistantId).privateImpression
    val next = runCatching {
        buildStoredPrivateImpression(
            assistantId = assistantId,
            previous = previous,
            nowMillis = nowMillis,
        )
    }.onFailure { error ->
        Log.w("CompanionImpression", "Failed to build stored private impression", error)
    }.getOrNull() ?: return
    if (next == previous) return
    runCatching {
        companionRuntime.applyTurn(
            CompanionTurnMutation(
                assistantId = assistantId,
                privateImpression = next,
                nowMillis = nowMillis,
            ),
        )
    }.onFailure { error ->
        Log.w("CompanionImpression", "Failed to persist stored private impression", error)
    }
}
