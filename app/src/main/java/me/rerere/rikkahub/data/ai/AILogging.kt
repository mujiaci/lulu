package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.core.merge
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import kotlin.uuid.Uuid

sealed class AILogging {
    data class Generation(
        val id: Uuid = Uuid.random(),
        val params: TextGenerationParams,
        val messages: List<UIMessage>,
        val sentMessages: List<UIMessage> = messages,
        val breakdown: GenerationTokenBreakdown? = null,
        val providerSetting: ProviderSetting,
        val stream: Boolean,
        val createdAtMillis: Long = System.currentTimeMillis(),
        val finishedAtMillis: Long? = null,
        val usage: TokenUsage? = null,
        val error: String? = null,
    ) : AILogging()
}

data class GenerationTokenBreakdown(
    val sections: List<GenerationTokenSection>,
    val toolNames: List<String>,
    val details: List<GenerationTokenDetail> = emptyList(),
) {
    val estimatedTokens: Int = sections.sumOf { it.estimatedTokens }
}

data class GenerationTokenSection(
    val label: String,
    val estimatedTokens: Int,
    val messageCount: Int = 0,
    val charCount: Int = 0,
)

data class GenerationTokenDetail(
    val label: String,
    val category: String,
    val estimatedTokens: Int,
    val charCount: Int = 0,
)

private const val MAX_LOGS = 32

class AILoggingManager {
    private val logs = MutableStateFlow<List<AILogging>>(emptyList())

    fun getLogs(): StateFlow<List<AILogging>> = logs

    fun addLog(log: AILogging) {
        logs.value = logs.value + log
        if (logs.value.size > MAX_LOGS) {
            logs.value = logs.value.drop(1)
        }
    }

    fun updateGenerationUsage(id: Uuid, usage: TokenUsage) {
        logs.value = logs.value.map { log ->
            if (log is AILogging.Generation && log.id == id) {
                log.copy(usage = log.usage.merge(usage))
            } else {
                log
            }
        }
    }

    fun finishGeneration(id: Uuid, error: String? = null) {
        logs.value = logs.value.map { log ->
            if (log is AILogging.Generation && log.id == id) {
                log.copy(
                    finishedAtMillis = System.currentTimeMillis(),
                    error = error,
                )
            } else {
                log
            }
        }
    }

    fun clearLogs() {
        logs.value = emptyList()
    }
}
