package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_bank")
data class MemoryBankEntity(
    @PrimaryKey(true)
    val id: Int = 0,

    @ColumnInfo("content")
    val content: String = "",

    /** Legacy bucket: message, phase_summary, daily_summary, manual. */
    @ColumnInfo("type")
    val type: String = "message",

    @ColumnInfo("title")
    val title: String? = null,

    /** Role-centered kind: role_emotion, body_sense, promise, relationship, user_preference. */
    @ColumnInfo("memory_kind")
    val memoryKind: String? = null,

    @ColumnInfo("role_feeling")
    val roleFeeling: String? = null,

    @ColumnInfo("body_sense")
    val bodySense: String? = null,

    @ColumnInfo("unspoken_thought")
    val unspokenThought: String? = null,

    @ColumnInfo("user_signal")
    val userSignal: String? = null,

    @ColumnInfo("relationship_effect")
    val relationshipEffect: String? = null,

    @ColumnInfo("importance", defaultValue = "3")
    val importance: Int = 3,

    @ColumnInfo("confidence", defaultValue = "1.0")
    val confidence: Double = 1.0,

    @ColumnInfo("tags_json")
    val tagsJson: String? = null,

    @ColumnInfo("embedding_text")
    val embeddingText: String? = null,

    @ColumnInfo("embedding_vector_json")
    val embeddingVectorJson: String? = null,

    @ColumnInfo("embedding_model_id")
    val embeddingModelId: String? = null,

    @ColumnInfo("embedding_dimensions")
    val embeddingDimensions: Int? = null,

    @ColumnInfo("source_message_node_ids_json")
    val sourceMessageNodeIdsJson: String? = null,

    @ColumnInfo("evidence_message_node_ids_json")
    val evidenceMessageNodeIdsJson: String? = null,

    @ColumnInfo("related_memory_ids_json")
    val relatedMemoryIdsJson: String? = null,

    @ColumnInfo("people_json")
    val peopleJson: String? = null,

    @ColumnInfo("topics_json")
    val topicsJson: String? = null,

    @ColumnInfo("deprecated", defaultValue = "0")
    val deprecated: Boolean = false,

    @ColumnInfo("deprecated_reason")
    val deprecatedReason: String? = null,

    @ColumnInfo("superseded_by_memory_id")
    val supersededByMemoryId: String? = null,

    @ColumnInfo("corrected_at")
    val correctedAt: Long? = null,

    @ColumnInfo("last_recalled_at")
    val lastRecalledAt: Long? = null,

    @ColumnInfo("recall_count", defaultValue = "0")
    val recallCount: Int = 0,

    @ColumnInfo("pinned", defaultValue = "0")
    val pinned: Boolean = false,

    @ColumnInfo("conversation_id")
    val conversationId: String? = null,

    @ColumnInfo("assistant_id")
    val assistantId: String? = null,

    @ColumnInfo("role")
    val role: String? = null,

    @ColumnInfo("created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo("date_group")
    val dateGroup: String? = null,

    @ColumnInfo("vector_status")
    val vectorStatus: String = "pending",

    @ColumnInfo("vector_retry_count")
    val vectorRetryCount: Int = 0,
)
