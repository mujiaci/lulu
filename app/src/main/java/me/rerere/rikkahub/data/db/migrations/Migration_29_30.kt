package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_29_30 : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE memory_bank ADD COLUMN source_memory_ids_json TEXT")
        db.execSQL(
            "ALTER TABLE memory_bank ADD COLUMN occurred_at_inferred INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE memory_bank ADD COLUMN memory_created_at INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE memory_bank ADD COLUMN memory_updated_at INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            """
            UPDATE memory_bank
            SET source_memory_ids_json = related_memory_ids_json
            WHERE type IN ('daily_summary', 'phase_summary')
               OR memory_kind IN ('daily_archive', 'monthly_archive')
            """.trimIndent(),
        )
        db.execSQL(
            """
            UPDATE memory_bank
            SET memory_created_at = CASE
                    WHEN extracted_at > 0 THEN extracted_at
                    ELSE created_at
                END,
                memory_updated_at = CASE
                    WHEN corrected_at IS NOT NULL AND corrected_at > 0 THEN corrected_at
                    WHEN extracted_at > 0 THEN extracted_at
                    ELSE created_at
                END,
                occurred_at_inferred = CASE
                    WHEN occurred_at IS NULL OR occurred_at <= 0 THEN 1
                    ELSE 0
                END
            """.trimIndent(),
        )
    }
}
