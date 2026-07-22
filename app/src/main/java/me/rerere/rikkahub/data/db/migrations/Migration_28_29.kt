package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_28_29 : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `memory_extraction_batch` (
                `batch_id` TEXT NOT NULL,
                `assistant_id` TEXT NOT NULL,
                `conversation_id` TEXT NOT NULL,
                `branch_id` TEXT NOT NULL,
                `batch_start_sequence` INTEGER NOT NULL,
                `batch_end_sequence` INTEGER NOT NULL,
                `start_source_node_id` TEXT NOT NULL,
                `end_source_node_id` TEXT NOT NULL,
                `source_node_ids_json` TEXT NOT NULL,
                `source_started_at` INTEGER NOT NULL,
                `source_ended_at` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `attempt_count` INTEGER NOT NULL,
                `last_error` TEXT,
                `model_id` TEXT,
                `extraction_version` INTEGER NOT NULL,
                `generated_memory_ids_json` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `completed_at` INTEGER,
                PRIMARY KEY(`batch_id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_memory_extraction_batch_assistant_id_conversation_id` " +
                "ON `memory_extraction_batch` (`assistant_id`, `conversation_id`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_memory_extraction_batch_status` " +
                "ON `memory_extraction_batch` (`status`)",
        )
    }
}
