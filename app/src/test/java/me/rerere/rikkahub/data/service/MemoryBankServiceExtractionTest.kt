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
}

private class RecordingMemoryBankDAO : MemoryBankDAO {
    val inserted = mutableListOf<MemoryBankEntity>()

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
    override suspend fun getMemoriesByAssistant(assistantId: String): List<MemoryBankEntity> = unsupported()
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
    override suspend fun getRecentMemories(limit: Int): List<MemoryBankEntity> = emptyList()
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
    override suspend fun updateVectorStatus(id: Int, status: String, retryCount: Int) = unsupported()

    private fun unsupported(): Nothing = error("Unexpected DAO call")
}
