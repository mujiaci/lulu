package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class PerformanceTiming(
    val id: String = UUID.randomUUID().toString(),
    val stage: String,
    val durationMillis: Long,
    val detail: String = "",
    val recordedAtMillis: Long = System.currentTimeMillis(),
)

object PerformanceMonitor {
    private const val MAX_RECORDS = 500

    private val _timings = MutableStateFlow<List<PerformanceTiming>>(emptyList())
    val timings: StateFlow<List<PerformanceTiming>> = _timings.asStateFlow()

    fun record(stage: String, durationMillis: Long, detail: String = "") {
        if (durationMillis < 0L) return
        val timing = PerformanceTiming(
            stage = stage,
            durationMillis = durationMillis,
            detail = detail,
        )
        _timings.update { current -> (listOf(timing) + current).take(MAX_RECORDS) }
    }

    fun recordNanos(stage: String, startedAtNanos: Long, detail: String = "") {
        record(stage, ((System.nanoTime() - startedAtNanos) / 1_000_000L).coerceAtLeast(0L), detail)
    }

    fun clear() {
        _timings.value = emptyList()
    }
}
