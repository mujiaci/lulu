package me.rerere.rikkahub.ui.pages.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.dao.ConversationDAO
import me.rerere.rikkahub.data.db.dao.MessageNodeDAO
import me.rerere.rikkahub.data.db.dao.getMessageCountPerDay
import me.rerere.rikkahub.data.db.dao.getTokenStats
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.ai.ApiUsageRecord
import me.rerere.rikkahub.data.ai.ApiUsageStore
import me.rerere.rikkahub.data.ai.ApiUsageSummary
import me.rerere.rikkahub.data.ai.summarizeApiUsage
import me.rerere.rikkahub.data.voicecall.VoiceCallRepository
import me.rerere.rikkahub.data.voicecall.VoiceCallStatsSummary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class AppStats(
    val isLoading: Boolean = true,
    val totalConversations: Int = 0,
    val totalMessages: Int = 0,
    val totalPromptTokens: Long = 0L,
    val totalCompletionTokens: Long = 0L,
    val totalCachedTokens: Long = 0L,
    val cacheRecords: List<ApiUsageRecord> = emptyList(),
    val cacheSummaries: List<ApiUsageSummary> = emptyList(),
    val voiceCallStats: VoiceCallStatsSummary = VoiceCallStatsSummary(),
    val conversationsPerDay: Map<LocalDate, Int> = emptyMap(),
    val launchCount: Int = 0,
)

class StatsVM(
    private val conversationDAO: ConversationDAO,
    private val messageNodeDAO: MessageNodeDAO,
    private val settingsStore: SettingsStore,
    private val voiceCallRepository: VoiceCallRepository,
    private val apiUsageStore: ApiUsageStore,
) : ViewModel() {

    private val _stats = MutableStateFlow(AppStats())
    val stats = _stats.asStateFlow()

    init {
        viewModelScope.launch { loadStats() }
    }

    private suspend fun loadStats() {
        delay(50)

        val today = LocalDate.now()

        // 热力图起始日期（52 周前的周日），格式 "yyyy-MM-dd" 直接与 JSON 中的 LocalDateTime 前缀比较
        val startDate = today
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            .minusWeeks(52)
            .toString()

        // 基于用户消息的 createdAt 统计每日活跃消息数，SQLite 侧 GROUP BY，返回 ≤371 行
        val conversationsPerDay = withContext(Dispatchers.IO) {
            messageNodeDAO
                .getMessageCountPerDay(startDate)
                .mapNotNull { entry ->
                    runCatching { LocalDate.parse(entry.day) to entry.count }.getOrNull()
                }
                .toMap()
        }

        val totalConversations = conversationDAO.countAll()

        // json_each() + json_extract() 在 SQLite 侧聚合，不再加载完整 JSON 到 Kotlin
        val tokenStats = messageNodeDAO.getTokenStats()
        val cacheRecords = apiUsageStore.state.value.records
        val cacheSummaries = cacheRecords.summarizeApiUsage()
        val voiceCallStats = withContext(Dispatchers.IO) {
            voiceCallRepository.getSummary()
        }

        val launchCount = settingsStore.settingsFlow.value.launchCount

        _stats.value = AppStats(
            isLoading = false,
            totalConversations = totalConversations,
            totalMessages = tokenStats.totalMessages,
            totalPromptTokens = tokenStats.promptTokens,
            totalCompletionTokens = tokenStats.completionTokens,
            totalCachedTokens = cacheRecords.sumOf { it.cachedTokens }.takeIf { cacheRecords.isNotEmpty() }
                ?: tokenStats.cachedTokens,
            cacheRecords = cacheRecords,
            cacheSummaries = cacheSummaries,
            voiceCallStats = voiceCallStats,
            conversationsPerDay = conversationsPerDay,
            launchCount = launchCount,
        )
    }
}
