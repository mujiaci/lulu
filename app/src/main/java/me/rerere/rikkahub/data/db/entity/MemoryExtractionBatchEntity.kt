package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MemoryExtractionBatchStatus {
    PENDING,
    PROCESSING,
    SUCCESS_WITH_MEMORIES,
    SUCCESS_EMPTY,
    FAILED_RETRYABLE,
    FAILED_MANUAL_REVIEW,
    INVALIDATED,
}

@Entity(
    tableName = "memory_extraction_batch",
    indices = [
        Index(value = ["assistant_id", "conversation_id"]),
        Index(value = ["status"]),
    ],
)
data class MemoryExtractionBatchEntity(
    @PrimaryKey @ColumnInfo("batch_id") val batchId: String,
    @ColumnInfo("assistant_id") val assistantId: String,
    @ColumnInfo("conversation_id") val conversationId: String,
    @ColumnInfo("branch_id") val branchId: String,
    @ColumnInfo("batch_start_sequence") val batchStartSequence: Int,
    @ColumnInfo("batch_end_sequence") val batchEndSequence: Int,
    @ColumnInfo("start_source_node_id") val startSourceNodeId: String,
    @ColumnInfo("end_source_node_id") val endSourceNodeId: String,
    @ColumnInfo("source_node_ids_json") val sourceNodeIdsJson: String,
    @ColumnInfo("source_started_at") val sourceStartedAt: Long,
    @ColumnInfo("source_ended_at") val sourceEndedAt: Long,
    @ColumnInfo("status") val status: String,
    @ColumnInfo("attempt_count") val attemptCount: Int,
    @ColumnInfo("last_error") val lastError: String?,
    @ColumnInfo("model_id") val modelId: String?,
    @ColumnInfo("extraction_version") val extractionVersion: Int,
    @ColumnInfo("generated_memory_ids_json") val generatedMemoryIdsJson: String,
    @ColumnInfo("created_at") val createdAt: Long,
    @ColumnInfo("updated_at") val updatedAt: Long,
    @ColumnInfo("completed_at") val completedAt: Long?,
)
