package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "memory_graph_edge",
    primaryKeys = ["source_memory_id", "target_memory_id"],
)
data class MemoryGraphEdgeEntity(
    @ColumnInfo("source_memory_id")
    val sourceMemoryId: Int,

    @ColumnInfo("target_memory_id")
    val targetMemoryId: Int,

    @ColumnInfo("weight", defaultValue = "0.0")
    val weight: Double = 0.0,

    @ColumnInfo("co_occurrence_count", defaultValue = "0")
    val coOccurrenceCount: Int = 0,

    @ColumnInfo("created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo("last_reinforced_at")
    val lastReinforcedAt: Long = createdAt,
)
