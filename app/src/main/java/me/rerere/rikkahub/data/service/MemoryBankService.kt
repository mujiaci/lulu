package me.rerere.rikkahub.data.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.dao.MemoryBankDAO
import me.rerere.rikkahub.data.db.entity.MemoryBankEntity
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MemoryBankService(
    private val memoryBankDAO: MemoryBankDAO,
    private val okHttpClient: OkHttpClient?,
    private val context: Context?
) {
    data class MemoryStats(
        val total: Int = 0,
        val messageCount: Int = 0,
        val summaryCount: Int = 0,
        val manualCount: Int = 0,
        val vectorizedCount: Int = 0,
        val pendingCount: Int = 0,
        val failedCount: Int = 0,
    )

    val recallCount: Int = 5

    suspend fun getAssistantIds(): List<String> = withContext(Dispatchers.IO) {
        memoryBankDAO.getDistinctAssistantIds()
    }

    suspend fun getStats(assistantId: String? = null): MemoryStats = withContext(Dispatchers.IO) {
        val total = if (assistantId != null) {
            memoryBankDAO.getCountByAssistant(assistantId)
        } else {
            memoryBankDAO.getTotalCount()
        }
        val messageCount = if (assistantId != null) {
            memoryBankDAO.getCountByAssistantAndType(assistantId, "message")
        } else {
            memoryBankDAO.getCountByType("message")
        }
        val summaryCount = memoryBankDAO.getSummaryCount()
        val manualCount = if (assistantId != null) {
            memoryBankDAO.getCountByAssistantAndType(assistantId, "manual")
        } else {
            memoryBankDAO.getCountByType("manual")
        }
        val vectorizedCount = if (assistantId != null) {
            0
        } else {
            memoryBankDAO.getCountByVectorStatus("done")
        }
        val pendingCount = if (assistantId != null) {
            0
        } else {
            memoryBankDAO.getCountByVectorStatus("pending")
        }
        val failedCount = if (assistantId != null) {
            0
        } else {
            memoryBankDAO.getCountByVectorStatus("failed")
        }
        MemoryStats(
            total = total,
            messageCount = messageCount,
            summaryCount = summaryCount,
            manualCount = manualCount,
            vectorizedCount = vectorizedCount,
            pendingCount = pendingCount,
            failedCount = failedCount
        )
    }

    suspend fun getTodayPhaseSummaries(assistantId: String? = null): List<MemoryBankEntity> =
        withContext(Dispatchers.IO) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            if (assistantId != null) {
                memoryBankDAO.getMemoriesByAssistantTypeAndDateGroup(assistantId, "phase_summary", today)
            } else {
                memoryBankDAO.getMemoriesByTypeAndDateGroup("phase_summary", today)
            }
        }

    suspend fun getDailySummaries(assistantId: String? = null): List<MemoryBankEntity> =
        withContext(Dispatchers.IO) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            if (assistantId != null) {
                memoryBankDAO.getMemoriesByAssistantTypeAndDateGroup(assistantId, "daily_summary", today)
            } else {
                memoryBankDAO.getMemoriesByTypeAndDateGroup("daily_summary", today)
            }
        }

    suspend fun searchMemories(
        keyword: String = "",
        type: String = "",
        limit: Int = 100,
        assistantId: String? = null
    ): List<MemoryBankEntity> = withContext(Dispatchers.IO) {
        if (keyword.isNotBlank() && type.isNotBlank()) {
            memoryBankDAO.searchMemoriesByKeywordAndType(keyword, type, limit)
        } else if (keyword.isNotBlank()) {
            memoryBankDAO.searchMemoriesByKeyword(keyword, limit)
        } else if (type.isNotBlank() && assistantId != null) {
            memoryBankDAO.getMemoriesByAssistantAndTypeLimit(assistantId, type, limit)
        } else if (type.isNotBlank()) {
            memoryBankDAO.getMemoriesByTypeLimit(type, limit)
        } else {
            memoryBankDAO.getRecentMemories(limit)
        }
    }

    suspend fun deleteMemory(id: Int) = withContext(Dispatchers.IO) {
        memoryBankDAO.deleteMemoryById(id)
    }

    suspend fun rebuildIndex() {
        // No-op: vector index removed. Structured embedding fields are stored for the next memory phase.
    }

    suspend fun processPendingVectors() {
        // No-op: vector processing is restored in a later phase after structured memory extraction lands.
    }

    suspend fun saveManualMemory(content: String): MemoryBankEntity = withContext(Dispatchers.IO) {
        val entity = MemoryBankEntity(
            content = content,
            type = "manual"
        )
        val id = memoryBankDAO.insertMemory(entity).toInt()
        entity.copy(id = id)
    }

    suspend fun saveExtractedMemories(
        candidates: List<AffectiveMemoryCandidate>,
        assistantId: String?,
        conversationId: String?,
        createdAt: Long = System.currentTimeMillis(),
    ): List<MemoryBankEntity> = withContext(Dispatchers.IO) {
        candidates
            .map { it.normalized() }
            .filter { it.content.isNotBlank() }
            .map { candidate ->
                val entity = candidate.toEntity(
                    assistantId = assistantId,
                    conversationId = conversationId,
                    createdAt = createdAt,
                )
                val id = memoryBankDAO.insertMemory(entity).toInt()
                entity.copy(id = id)
            }
    }

    suspend fun recallMemories(query: String, count: Int): List<MemoryBankEntity> = withContext(Dispatchers.IO) {
        if (query.isNotBlank()) {
            memoryBankDAO.searchMemoriesByKeyword(query, count)
        } else {
            memoryBankDAO.getRecentMemories(count)
        }
    }

    suspend fun buildRecallContext(assistantId: String?): String = withContext(Dispatchers.IO) {
        val memories = buildList {
            if (assistantId != null) {
                addAll(memoryBankDAO.getPinnedRecallMemoriesForAssistant(assistantId, 3))
                addAll(memoryBankDAO.getImportantRecallMemoriesForAssistant(assistantId, minImportance = 4, limit = 5))
            } else {
                addAll(memoryBankDAO.getPinnedRecallMemories(3))
                addAll(memoryBankDAO.getImportantRecallMemories(minImportance = 4, limit = 5))
            }
            addAll(getTodayPhaseSummaries(assistantId).take(2))
            addAll(getDailySummaries(assistantId).take(2))
            if (assistantId != null) {
                addAll(memoryBankDAO.getMemoriesByAssistantAndTypeLimit(assistantId, "manual", 3))
                addAll(memoryBankDAO.getMemoriesByAssistantAndTypeLimit(assistantId, "message", 3))
            }
            addAll(memoryBankDAO.getMemoriesByTypeLimit("manual", 3))
        }
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<MemoryBankEntity> { it.pinned }
                    .thenByDescending { it.importance }
                    .thenByDescending { it.createdAt }
            )

        buildMemoryRecallContext(memories)
    }
}

internal fun buildMemoryRecallContext(
    memories: List<MemoryBankEntity>,
    maxItems: Int = 6,
    maxContentLength: Int = 120,
): String {
    val selected = memories
        .filter { it.content.isNotBlank() && !it.deprecated }
        .sortedWith(
            compareByDescending<MemoryBankEntity> { it.pinned }
                .thenByDescending { it.importance }
                .thenByDescending { it.createdAt }
        )
        .take(maxItems)
    if (selected.isEmpty()) return ""

    val sections = listOf(
        "长期印象" to selected.filter { it.recallSectionTitle() == "长期印象" },
        "最近情感记忆" to selected.filter { it.recallSectionTitle() == "最近情感记忆" },
        "身体和五感" to selected.filter { it.bodySense.isNotBlankValue() || it.recallSectionTitle() == "身体和五感" },
        "当前相关回忆" to selected.filter { it.recallSectionTitle() == "当前相关回忆" },
        "关系变化" to selected.filter { it.recallSectionTitle() == "关系变化" },
        "未完成承诺" to selected.filter { it.recallSectionTitle() == "未完成承诺" },
    ).filter { (_, items) -> items.isNotEmpty() }

    return buildString {
        appendLine("<lulu_memory>")
        appendLine("这些是露露此刻自然想起的记忆，只作为联想参考。不要逐条复述，也不要说“我查到记忆”。")
        sections.forEach { (title, items) ->
            appendLine("$title：")
            items.forEach { memory ->
                appendLine("- ${memory.toRecallLine(maxContentLength)}")
            }
        }
        append("</lulu_memory>")
    }
}

private fun MemoryBankEntity.recallSectionTitle(): String = when (memoryKind ?: type) {
    "role_emotion" -> "最近情感记忆"
    "body_sense" -> "身体和五感"
    "promise" -> "未完成承诺"
    "relationship" -> "关系变化"
    "user_preference", "manual" -> "长期印象"
    "phase_summary", "daily_summary" -> "当前相关回忆"
    else -> "当前相关回忆"
}

private fun MemoryBankEntity.toRecallLine(maxContentLength: Int): String {
    val parts = buildList {
        add(content.trim())
        roleFeeling?.takeIf { it.isNotBlank() }?.let { add("露露当时的感觉：$it") }
        bodySense?.takeIf { it.isNotBlank() }?.let { add("身体感：$it") }
        unspokenThought?.takeIf { it.isNotBlank() }?.let { add("没说出口的想法：$it") }
        relationshipEffect?.takeIf { it.isNotBlank() }?.let { add("关系判断：$it") }
    }
    val prefix = if (confidence < 0.7) "可能：" else ""
    return (prefix + parts.joinToString("；")).ellipsize(maxContentLength)
}

private fun String?.isNotBlankValue(): Boolean = this != null && isNotBlank()

private fun String.ellipsize(maxLength: Int): String {
    if (length <= maxLength) return this
    return take(maxLength).trimEnd() + "..."
}
