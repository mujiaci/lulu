package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_23_24 : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `related_memory_ids_json` TEXT")
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `people_json` TEXT")
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `topics_json` TEXT")
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `superseded_by_memory_id` TEXT")
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `corrected_at` INTEGER")
    }
}
