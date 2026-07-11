package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_26_27 : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `memory_extraction_checkpoint` (
                `assistant_id` TEXT NOT NULL,
                `conversation_id` TEXT NOT NULL,
                `processed_source_node_ids_json` TEXT NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`assistant_id`, `conversation_id`)
            )
            """.trimIndent(),
        )
    }
}
