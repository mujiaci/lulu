package me.rerere.rikkahub.data.service

import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.dao.MemoryBankDAO
import me.rerere.rikkahub.data.db.entity.MemoryBankEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryBankServiceExtractionTest {
    @Test
    fun `save extracted memories inserts normalized candidates and returns generated ids`() = runBlocking {
        val dao = RecordingMemoryBankDAO()
        val service = MemoryBankService(
            memoryBankDAO = dao,
            okHttpClient = null,
            context = null,
        )
        val candidate = AffectiveMemoryCandidate(
            type = "role_emotion",
            content = "Lulu felt seen after the user praised her memory design.",
            roleFeeling = "happy, shy, wants to move closer",
            bodySense = "warm chest, lighter voice",
            relationshipEffect = "trust increased",
            importance = 5,
            confidence = 0.91,
            tags = listOf("praise", "intimacy"),
            embeddingText = "user praised lulu memory design lulu felt seen trust increased",
            sourceMessageNodeIds = listOf("user-node-1", "assistant-node-2"),
            evidenceMessageNodeIds = listOf("user-node-1"),
        )

        val saved = service.saveExtractedMemories(
            candidates = listOf(candidate),
            assistantId = "assistant-1",
            conversationId = "conversation-1",
            createdAt = 1234L,
        )

        assertEquals(1, saved.size)
        assertEquals(100, saved.single().id)
        assertEquals(1, dao.inserted.size)
        val inserted = dao.inserted.single()
        assertEquals("manual", inserted.type)
        assertEquals("role_emotion", inserted.memoryKind)
        assertEquals("assistant-1", inserted.assistantId)
        assertEquals("conversation-1", inserted.conversationId)
        assertEquals(1234L, inserted.createdAt)
        assertEquals("pending", inserted.vectorStatus)
        assertTrue(inserted.tagsJson!!.contains("praise"))
        assertTrue(inserted.sourceMessageNodeIdsJson!!.contains("user-node-1"))
    }

    @Test
    fun `build recall context marks injected memories as recalled`() = runBlocking {
        val dao = RecordingMemoryBankDAO(
            assistantMemories = listOf(
                MemoryBankEntity(
                    id = 7,
                    content = "用户正在写论文大纲，希望露露帮她拆成更小的步骤。",
                    memoryKind = "user_preference",
                    assistantId = "assistant-1",
                    importance = 5,
                    createdAt = 300L,
                ),
                MemoryBankEntity(
                    id = 8,
                    content = "用户喜欢雨天窝在床上聊天。",
                    memoryKind = "user_preference",
                    assistantId = "assistant-1",
                    importance = 3,
                    createdAt = 200L,
                ),
            )
        )
        val service = MemoryBankService(
            memoryBankDAO = dao,
            okHttpClient = null,
            context = null,
        )

        val context = service.buildRecallContext(
            assistantId = "assistant-1",
            query = "论文大纲卡住了",
        )

        assertTrue(context.contains("拆成更小的步骤"))
        assertTrue(context.contains("雨天窝在床上聊天"))
        assertEquals(listOf(7, 8), dao.recalledIds)
        assertEquals("""["8"]""", dao.relatedMemoryUpdates[7])
        assertEquals("""["7"]""", dao.relatedMemoryUpdates[8])
        assertTrue(dao.recalledAt > 0L)
    }

    @Test
    fun `light maintenance deprecates lower scored near duplicate vector memory`() = runBlocking {
        val dao = RecordingMemoryBankDAO(
            recentMemories = listOf(
                MemoryBankEntity(
                    id = 1,
                    content = "用户正在写论文大纲，希望露露帮她拆成更小的步骤。",
                    memoryKind = "user_preference",
                    embeddingVectorJson = encodeMemoryVector(listOf(1f, 0f, 0f)),
                    importance = 5,
                    confidence = 0.9,
                    createdAt = 200L,
                ),
                MemoryBankEntity(
                    id = 2,
                    content = "用户论文大纲卡住了，需要露露温柔地一步步梳理。",
                    memoryKind = "user_preference",
                    embeddingVectorJson = encodeMemoryVector(listOf(0.96f, 0.04f, 0f)),
                    importance = 2,
                    confidence = 0.7,
                    createdAt = 100L,
                ),
                MemoryBankEntity(
                    id = 3,
                    content = "露露答应下次继续检查参考文献格式。",
                    memoryKind = "promise",
                    embeddingVectorJson = encodeMemoryVector(listOf(0f, 1f, 0f)),
                    importance = 3,
                    createdAt = 50L,
                ),
            )
        )
        val service = MemoryBankService(
            memoryBankDAO = dao,
            okHttpClient = null,
            context = null,
        )

        val result = service.runLightMaintenance()

        assertEquals(1, result.deprecatedDuplicateCount)
        assertEquals(2, dao.deprecatedUpdates.single().id)
        assertEquals("1", dao.deprecatedUpdates.single().supersededByMemoryId)
    }
}

private class RecordingMemoryBankDAO(
    private val assistantMemories: List<MemoryBankEntity> = emptyList(),
    private val recentMemories: List<MemoryBankEntity> = emptyList(),
) : MemoryBankDAO {
    val inserted = mutableListOf<MemoryBankEntity>()
    val recalledIds = mutableListOf<Int>()
    val relatedMemoryUpdates = mutableMapOf<Int, String?>()
    val deprecatedUpdates = mutableListOf<DeprecatedMemoryUpdate>()
    var recalledAt: Long = 0L

    override suspend fun insertMemory(memory: MemoryBankEntity): Long {
        inserted += memory
        return (99 + inserted.size).toLong()
    }

    override suspend fun updateMemory(memory: MemoryBankEntity) = unsupported()
    override suspend fun deleteMemory(memory: MemoryBankEntity) = unsupported()
    override suspend fun deleteMemoryById(id: Int) = unsupported()
    override suspend fun getMemoryById(id: Int): MemoryBankEntity? = unsupported()
    override suspend fun getAllMemories(): List<MemoryBankEntity> = unsupported()
    override suspend fun getMemoriesByType(type: String): List<MemoryBankEntity> = unsupported()
    override suspend fun getMemoriesByTypeLimit(type: String, limit: Int): List<MemoryBankEntity> = emptyList()
    override suspend fun getMemoriesByAssistant(assistantId: String): List<MemoryBankEntity> =
        assistantMemories.filter { it.assistantId == assistantId }
    override suspend fun getMemoriesByAssistantAndTypeLimit(
        assistantId: String,
        type: String,
        limit: Int,
    ): List<MemoryBankEntity> = emptyList()

    override suspend fun getMemoriesByAssistantTypeAndDateGroup(
        assistantId: String,
        type: String,
        dateGroup: String,
    ): List<MemoryBankEntity> = emptyList()

    override suspend fun getMemoriesByTypeAndDateGroup(type: String, dateGroup: String): List<MemoryBankEntity> =
        emptyList()

    override suspend fun getDistinctAssistantIds(): List<String> = emptyList()
    override suspend fun getMemoriesByDateGroup(dateGroup: String): List<MemoryBankEntity> = unsupported()
    override suspend fun getMemoriesByDateGroupAndType(
        dateGroup: String,
        type: String,
    ): List<MemoryBankEntity> = unsupported()

    override suspend fun getMemoriesByVectorStatus(status: String): List<MemoryBankEntity> = unsupported()
    override suspend fun getPendingVectorMemories(maxRetry: Int, limit: Int): List<MemoryBankEntity> = unsupported()
    override suspend fun getMessageCountSince(sinceTimestamp: Long): Int = unsupported()
    override suspend fun getTotalMessageCount(): Int = 0
    override suspend fun getTotalCount(): Int = 0
    override suspend fun getCountByType(type: String): Int = 0
    override suspend fun getSummaryCount(): Int = 0
    override suspend fun getCountByVectorStatus(status: String): Int = 0
    override suspend fun getCountByAssistant(assistantId: String): Int = 0
    override suspend fun getCountByAssistantAndType(assistantId: String, type: String): Int = 0
    override suspend fun getRecentMemories(limit: Int): List<MemoryBankEntity> = recentMemories.take(limit)
    override suspend fun searchMemoriesByKeyword(keyword: String, limit: Int): List<MemoryBankEntity> = emptyList()
    override suspend fun searchMemoriesByKeywordAndType(
        keyword: String,
        type: String,
        limit: Int,
    ): List<MemoryBankEntity> = emptyList()

    override suspend fun getPinnedRecallMemories(limit: Int): List<MemoryBankEntity> = emptyList()
    override suspend fun getImportantRecallMemories(minImportance: Int, limit: Int): List<MemoryBankEntity> =
        emptyList()

    override suspend fun getPinnedRecallMemoriesForAssistant(
        assistantId: String,
        limit: Int,
    ): List<MemoryBankEntity> = emptyList()

    override suspend fun getImportantRecallMemoriesForAssistant(
        assistantId: String,
        minImportance: Int,
        limit: Int,
    ): List<MemoryBankEntity> = emptyList()

    override suspend fun getRecentDateGroups(limit: Int): List<String> = emptyList()
    override suspend fun markMemoriesRecalled(ids: List<Int>, recalledAt: Long) {
        recalledIds += ids
        this.recalledAt = recalledAt
    }

    override suspend fun updateRelatedMemoryIds(id: Int, relatedMemoryIdsJson: String?) {
        relatedMemoryUpdates[id] = relatedMemoryIdsJson
    }

    override suspend fun markMemoryDeprecated(
        id: Int,
        deprecatedReason: String?,
        supersededByMemoryId: String?,
        correctedAt: Long?,
    ) {
        deprecatedUpdates += DeprecatedMemoryUpdate(
            id = id,
            deprecatedReason = deprecatedReason,
            supersededByMemoryId = supersededByMemoryId,
            correctedAt = correctedAt,
        )
    }

    override suspend fun updateVectorStatus(id: Int, status: String, retryCount: Int) = unsupported()
    override suspend fun updateVectorResult(
        id: Int,
        status: String,
        retryCount: Int,
        vectorJson: String?,
        modelId: String?,
        dimensions: Int?,
    ) = unsupported()

    private fun unsupported(): Nothing = error("Unexpected DAO call")
}

private data class DeprecatedMemoryUpdate(
    val id: Int,
    val deprecatedReason: String?,
    val supersededByMemoryId: String?,
    val correctedAt: Long?,
)
