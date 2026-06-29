package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_22_23 : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `title` TEXT")
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `memory_kind` TEXT")
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `role_feeling` TEXT")
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `body_sense` TEXT")
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `unspoken_thought` TEXT")
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `user_signal` TEXT")
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `relationship_effect` TEXT")
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `importance` INTEGER NOT NULL DEFAULT 3")
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `confidence` REAL NOT NULL DEFAULT 1.0")
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `tags_json` TEXT")
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `embedding_text` TEXT")
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `source_message_node_ids_json` TEXT")
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `evidence_message_node_ids_json` TEXT")
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `deprecated` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `deprecated_reason` TEXT")
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `last_recalled_at` INTEGER")
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `recall_count` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0")
    }
}
