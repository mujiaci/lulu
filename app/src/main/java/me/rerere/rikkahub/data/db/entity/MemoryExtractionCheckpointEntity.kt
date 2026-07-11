package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "memory_extraction_checkpoint",
    primaryKeys = ["assistant_id", "conversation_id"],
)
data class MemoryExtractionCheckpointEntity(
    @ColumnInfo("assistant_id")
    val assistantId: String,

    @ColumnInfo("conversation_id")
    val conversationId: String,

    @ColumnInfo("processed_source_node_ids_json")
    val processedSourceNodeIdsJson: String,

    @ColumnInfo("updated_at")
    val updatedAt: Long,
)
