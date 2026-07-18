package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_27_28 : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `source_message_at` INTEGER")
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `occurred_at` INTEGER")
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `extracted_at` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """
            UPDATE `memory_bank`
            SET `occurred_at` = `created_at`,
                `extracted_at` = `created_at`
            WHERE `occurred_at` IS NULL OR `extracted_at` = 0
            """.trimIndent(),
        )
    }
}
