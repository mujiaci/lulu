package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_25_26 : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `memory_graph_edge` (
                `source_memory_id` INTEGER NOT NULL,
                `target_memory_id` INTEGER NOT NULL,
                `weight` REAL NOT NULL DEFAULT 0.0,
                `co_occurrence_count` INTEGER NOT NULL DEFAULT 0,
                `created_at` INTEGER NOT NULL,
                `last_reinforced_at` INTEGER NOT NULL,
                PRIMARY KEY(`source_memory_id`, `target_memory_id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_memory_graph_edge_source_weight`
            ON `memory_graph_edge` (`source_memory_id`, `weight`)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_memory_graph_edge_target`
            ON `memory_graph_edge` (`target_memory_id`)
            """.trimIndent(),
        )
    }
}
