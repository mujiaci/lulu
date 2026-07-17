package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_24_25 : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `embedding_vector_json` TEXT")
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `embedding_model_id` TEXT")
        db.execSQL("ALTER TABLE `memory_bank` ADD COLUMN `embedding_dimensions` INTEGER")
    }
}
