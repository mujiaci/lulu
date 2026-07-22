package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import me.rerere.rikkahub.data.db.entity.MemoryBankEntity
import me.rerere.rikkahub.data.db.entity.MemoryExtractionBatchEntity
import me.rerere.rikkahub.data.db.entity.MemoryExtractionCheckpointEntity
import me.rerere.rikkahub.data.db.entity.MemoryGraphEdgeEntity


@Dao
interface MemoryBankDAO {
    // ===== MemoryBank CRUD =====

    @Insert
    suspend fun insertMemory(memory: MemoryBankEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMemoryGraphEdge(edge: MemoryGraphEdgeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExtractionCheckpoint(checkpoint: MemoryExtractionCheckpointEntity)

    @Query(
        """
        SELECT * FROM memory_extraction_checkpoint
        WHERE assistant_id = :assistantId AND conversation_id = :conversationId
        """,
    )
    suspend fun getExtractionCheckpoint(
        assistantId: String,
        conversationId: String,
    ): MemoryExtractionCheckpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExtractionBatch(batch: MemoryExtractionBatchEntity)

    @Query("SELECT * FROM memory_extraction_batch WHERE batch_id = :batchId")
    suspend fun getExtractionBatch(batchId: String): MemoryExtractionBatchEntity?

    @Query(
        """
        SELECT * FROM memory_extraction_batch
        WHERE assistant_id = :assistantId AND conversation_id = :conversationId
        ORDER BY batch_start_sequence ASC, created_at ASC
        """,
    )
    suspend fun getExtractionBatches(
        assistantId: String,
        conversationId: String,
    ): List<MemoryExtractionBatchEntity>

    @Query(
        """
        SELECT * FROM memory_extraction_batch
        WHERE assistant_id = :assistantId
        ORDER BY conversation_id ASC, batch_start_sequence ASC, created_at ASC
        """,
    )
    suspend fun getExtractionBatchesByAssistant(
        assistantId: String,
    ): List<MemoryExtractionBatchEntity>

    @Query("DELETE FROM memory_extraction_checkpoint WHERE assistant_id = :assistantId AND conversation_id = :conversationId")
    suspend fun deleteExtractionCheckpoint(assistantId: String, conversationId: String)

    @Update
    suspend fun updateMemory(memory: MemoryBankEntity)

    @Delete
    suspend fun deleteMemory(memory: MemoryBankEntity)

    @Query("DELETE FROM memory_bank WHERE id = :id")
    suspend fun deleteMemoryById(id: Int)

    @Query("DELETE FROM memory_graph_edge WHERE source_memory_id = :id OR target_memory_id = :id")
    suspend fun deleteMemoryGraphEdgesForMemory(id: Int)

    @Query(
        """
        DELETE FROM memory_graph_edge
        WHERE source_memory_id IN (SELECT id FROM memory_bank WHERE assistant_id = :assistantId)
            OR target_memory_id IN (SELECT id FROM memory_bank WHERE assistant_id = :assistantId)
        """
    )
    suspend fun deleteMemoryGraphEdgesForAssistant(assistantId: String)

    @Query("DELETE FROM memory_bank WHERE assistant_id = :assistantId")
    suspend fun deleteMemoriesByAssistant(assistantId: String)

    @Query("DELETE FROM memory_extraction_checkpoint WHERE assistant_id = :assistantId")
    suspend fun deleteExtractionCheckpointsByAssistant(assistantId: String)

    @Query("DELETE FROM memory_extraction_batch WHERE assistant_id = :assistantId")
    suspend fun deleteExtractionBatchesByAssistant(assistantId: String)

    @Query("DELETE FROM memory_graph_edge")
    suspend fun deleteAllMemoryGraphEdges()

    @Query("DELETE FROM memory_bank")
    suspend fun deleteAllMemories()

    @Query("DELETE FROM memory_extraction_checkpoint")
    suspend fun deleteAllExtractionCheckpoints()

    @Query("DELETE FROM memory_extraction_batch")
    suspend fun deleteAllExtractionBatches()

    @Query("SELECT * FROM memory_bank WHERE id = :id")
    suspend fun getMemoryById(id: Int): MemoryBankEntity?

    @Query("SELECT * FROM memory_bank ORDER BY created_at DESC")
    suspend fun getAllMemories(): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE type = :type ORDER BY created_at DESC")
    suspend fun getMemoriesByType(type: String): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE type = :type AND deprecated = 0 ORDER BY created_at DESC LIMIT :limit")
    suspend fun getMemoriesByTypeLimit(type: String, limit: Int): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE assistant_id = :assistantId ORDER BY created_at DESC")
    suspend fun getMemoriesByAssistant(assistantId: String): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE assistant_id = :assistantId AND type = :type AND deprecated = 0 ORDER BY created_at DESC LIMIT :limit")
    suspend fun getMemoriesByAssistantAndTypeLimit(assistantId: String, type: String, limit: Int): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE assistant_id = :assistantId AND type = :type AND date_group = :dateGroup AND deprecated = 0 ORDER BY created_at DESC")
    suspend fun getMemoriesByAssistantTypeAndDateGroup(assistantId: String, type: String, dateGroup: String): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE type = :type AND date_group = :dateGroup AND deprecated = 0 ORDER BY created_at DESC")
    suspend fun getMemoriesByTypeAndDateGroup(type: String, dateGroup: String): List<MemoryBankEntity>

    @Query("SELECT DISTINCT assistant_id FROM memory_bank WHERE assistant_id IS NOT NULL")
    suspend fun getDistinctAssistantIds(): List<String>

    @Query("SELECT * FROM memory_bank WHERE date_group = :dateGroup ORDER BY created_at DESC")
    suspend fun getMemoriesByDateGroup(dateGroup: String): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE date_group = :dateGroup AND type = :type ORDER BY created_at DESC")
    suspend fun getMemoriesByDateGroupAndType(dateGroup: String, type: String): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE vector_status = :status")
    suspend fun getMemoriesByVectorStatus(status: String): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE vector_status = 'pending' AND vector_retry_count < :maxRetry ORDER BY COALESCE(occurred_at, created_at) DESC LIMIT :limit")
    suspend fun getPendingVectorMemories(maxRetry: Int, limit: Int = 50): List<MemoryBankEntity>

    @Query("SELECT COUNT(*) FROM memory_bank WHERE type = 'message' AND created_at > :sinceTimestamp")
    suspend fun getMessageCountSince(sinceTimestamp: Long): Int

    @Query("SELECT COUNT(*) FROM memory_bank WHERE type = 'message'")
    suspend fun getTotalMessageCount(): Int

    @Query("SELECT COUNT(*) FROM memory_bank")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM memory_bank WHERE type = :type")
    suspend fun getCountByType(type: String): Int

    @Query("SELECT COUNT(*) FROM memory_bank WHERE vector_status = :status")
    suspend fun getCountByVectorStatus(status: String): Int

    @Query("SELECT COUNT(*) FROM memory_bank WHERE deprecated = 1")
    suspend fun getDeprecatedCount(): Int

    @Query("SELECT COUNT(*) FROM memory_bank WHERE assistant_id = :assistantId AND deprecated = 1")
    suspend fun getDeprecatedCountByAssistant(assistantId: String): Int

    @Query("SELECT COUNT(*) FROM memory_bank WHERE assistant_id = :assistantId")
    suspend fun getCountByAssistant(assistantId: String): Int

    @Query("SELECT COUNT(*) FROM memory_bank WHERE assistant_id = :assistantId AND type = :type")
    suspend fun getCountByAssistantAndType(assistantId: String, type: String): Int

    @Query("SELECT COUNT(*) FROM memory_bank WHERE assistant_id = :assistantId AND vector_status = :status")
    suspend fun getCountByAssistantAndVectorStatus(assistantId: String, status: String): Int

    @Query("SELECT * FROM memory_bank WHERE deprecated = 0 ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecentMemories(limit: Int): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE deprecated = 1 ORDER BY corrected_at DESC, created_at DESC LIMIT :limit")
    suspend fun getDeprecatedMemories(limit: Int): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE deprecated = 1 AND assistant_id = :assistantId ORDER BY corrected_at DESC, created_at DESC LIMIT :limit")
    suspend fun getDeprecatedMemoriesByAssistant(assistantId: String, limit: Int): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE deprecated = 0 AND content LIKE '%' || :keyword || '%' ORDER BY created_at DESC LIMIT :limit")
    suspend fun searchMemoriesByKeyword(keyword: String, limit: Int = 20): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE deprecated = 1 AND content LIKE '%' || :keyword || '%' ORDER BY corrected_at DESC, created_at DESC LIMIT :limit")
    suspend fun searchDeprecatedMemoriesByKeyword(keyword: String, limit: Int = 20): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE deprecated = 1 AND assistant_id = :assistantId AND content LIKE '%' || :keyword || '%' ORDER BY corrected_at DESC, created_at DESC LIMIT :limit")
    suspend fun searchDeprecatedMemoriesByAssistantAndKeyword(
        assistantId: String,
        keyword: String,
        limit: Int = 20,
    ): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE deprecated = 0 AND content LIKE '%' || :keyword || '%' AND type = :type ORDER BY created_at DESC LIMIT :limit")
    suspend fun searchMemoriesByKeywordAndType(keyword: String, type: String, limit: Int = 20): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE deprecated = 0 AND assistant_id = :assistantId AND content LIKE '%' || :keyword || '%' AND type = :type ORDER BY created_at DESC LIMIT :limit")
    suspend fun searchMemoriesByAssistantKeywordAndType(
        assistantId: String,
        keyword: String,
        type: String,
        limit: Int = 20,
    ): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE deprecated = 0 AND pinned = 1 ORDER BY importance DESC, created_at DESC LIMIT :limit")
    suspend fun getPinnedRecallMemories(limit: Int): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE deprecated = 0 AND importance >= :minImportance ORDER BY importance DESC, created_at DESC LIMIT :limit")
    suspend fun getImportantRecallMemories(minImportance: Int, limit: Int): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE deprecated = 0 AND pinned = 1 AND (assistant_id IS NULL OR assistant_id = :assistantId) ORDER BY importance DESC, created_at DESC LIMIT :limit")
    suspend fun getPinnedRecallMemoriesForAssistant(assistantId: String, limit: Int): List<MemoryBankEntity>

    @Query("SELECT * FROM memory_bank WHERE deprecated = 0 AND importance >= :minImportance AND (assistant_id IS NULL OR assistant_id = :assistantId) ORDER BY importance DESC, created_at DESC LIMIT :limit")
    suspend fun getImportantRecallMemoriesForAssistant(assistantId: String, minImportance: Int, limit: Int): List<MemoryBankEntity>

    @Query("SELECT DISTINCT date_group FROM memory_bank WHERE date_group IS NOT NULL ORDER BY date_group DESC LIMIT :limit")
    suspend fun getRecentDateGroups(limit: Int): List<String>

    @Query(
        """
        UPDATE memory_bank
        SET last_recalled_at = :recalledAt,
            recall_count = recall_count + 1
        WHERE id IN (:ids)
        """
    )
    suspend fun markMemoriesRecalled(ids: List<Int>, recalledAt: Long)

    @Query("UPDATE memory_bank SET related_memory_ids_json = :relatedMemoryIdsJson WHERE id = :id")
    suspend fun updateRelatedMemoryIds(id: Int, relatedMemoryIdsJson: String?)

    @Query(
        """
        UPDATE memory_graph_edge
        SET weight = MIN(:maxWeight, weight + :delta),
            co_occurrence_count = co_occurrence_count + 1,
            last_reinforced_at = :reinforcedAt
        WHERE source_memory_id = :sourceId AND target_memory_id = :targetId
        """
    )
    suspend fun reinforceMemoryGraphEdge(
        sourceId: Int,
        targetId: Int,
        delta: Double,
        maxWeight: Double,
        reinforcedAt: Long,
    )

    @Query(
        """
        SELECT * FROM memory_graph_edge
        WHERE source_memory_id IN (:sourceIds)
            AND weight >= :minWeight
        ORDER BY weight DESC, last_reinforced_at DESC
        LIMIT :limit
        """
    )
    suspend fun getMemoryGraphEdgesFromSources(
        sourceIds: List<Int>,
        minWeight: Double,
        limit: Int,
    ): List<MemoryGraphEdgeEntity>

    @Query(
        """
        UPDATE memory_bank
        SET deprecated = 1,
            deprecated_reason = :deprecatedReason,
            superseded_by_memory_id = :supersededByMemoryId,
            corrected_at = :correctedAt,
            memory_updated_at = COALESCE(:correctedAt, memory_updated_at)
        WHERE id = :id
        """
    )
    suspend fun markMemoryDeprecated(
        id: Int,
        deprecatedReason: String?,
        supersededByMemoryId: String?,
        correctedAt: Long?,
    )

    @Query("UPDATE memory_bank SET vector_status = :status, vector_retry_count = :retryCount WHERE id = :id")
    suspend fun updateVectorStatus(id: Int, status: String, retryCount: Int)

    @Query(
        """
        UPDATE memory_bank
        SET vector_status = :status,
            vector_retry_count = :retryCount,
            embedding_vector_json = :vectorJson,
            embedding_model_id = :modelId,
            embedding_dimensions = :dimensions
        WHERE id = :id
        """
    )
    suspend fun updateVectorResult(
        id: Int,
        status: String,
        retryCount: Int,
        vectorJson: String?,
        modelId: String?,
        dimensions: Int?,
    )
}
