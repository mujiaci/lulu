# Affective Vector Memory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give Lulu's memory bank a first working layer for structured role-centered memories, so recall stores and injects Lulu's own feelings, body sensations, unspoken thoughts, relationship changes, source ids, and future vector text.

**Architecture:** Phase 1 extends the existing Room `memory_bank` table and `MemoryBankEntity` with nullable affective-memory metadata while preserving existing memories. `MemoryBankService.buildMemoryRecallContext()` becomes the formatting boundary: it groups memories into role-feeling, body-sense, current recall, long-term impression, and promise sections inside `<lulu_memory>`. Vector embedding and full source-message browsing are deferred, but `embeddingText` and source/evidence message id fields are added now so later phases do not require another data-shape rethink.

**Tech Stack:** Kotlin, Room, JUnit 4, Gradle Android unit tests, existing `MemoryBankDAO` and `MemoryBankService`.

---

## File Structure

- Modify `app/src/test/java/me/rerere/rikkahub/data/service/MemoryBankContextTest.kt`
  - Owns behavior tests for memory-pack rendering.
  - Adds tests for role emotion/body/promise grouping, deprecated exclusion, confidence wording, and item limits.
- Modify `app/src/main/java/me/rerere/rikkahub/data/db/entity/MemoryBankEntity.kt`
  - Adds structured memory fields with defaults so existing Kotlin callers keep compiling.
- Modify `app/src/main/java/me/rerere/rikkahub/data/service/MemoryBankService.kt`
  - Keeps existing service API.
  - Rewrites `buildMemoryRecallContext()` to render affective memory sections.
- Modify `app/src/main/java/me/rerere/rikkahub/data/db/dao/MemoryBankDAO.kt`
  - Filters deprecated memories from recall-oriented queries.
  - Adds high-importance/pinned helper queries used by recall context.
- Modify `app/src/main/java/me/rerere/rikkahub/data/db/AppDatabase.kt`
  - Bumps Room version from 22 to 23.
  - Registers the 22-to-23 migration.
- Create `app/src/main/java/me/rerere/rikkahub/data/db/migrations/Migration_22_23.kt`
  - Adds nullable/defaulted columns to `memory_bank` without dropping existing data.
- Generate/modify `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/23.json`
  - Room schema snapshot for version 23.

## Task 1: Context Rendering Tests

**Files:**
- Modify: `app/src/test/java/me/rerere/rikkahub/data/service/MemoryBankContextTest.kt`

- [ ] **Step 1: Write failing tests for affective memory sections**

Add tests shaped like this:

```kotlin
@Test
fun `build memory context renders affective role memory sections`() {
    val memories = listOf(
        MemoryBankEntity(
            content = "用户夸了露露，露露觉得自己被认真看见了。",
            type = "manual",
            memoryKind = "role_emotion",
            roleFeeling = "开心、害羞、想靠近",
            bodySense = "胸口发热，回复变轻快",
            relationshipEffect = "亲密度上升",
            createdAt = 300L,
        ),
        MemoryBankEntity(
            content = "以后默认在 master 分支修改。",
            type = "manual",
            memoryKind = "promise",
            importance = 5,
            createdAt = 200L,
        ),
    )

    val context = buildMemoryRecallContext(memories)

    assertTrue(context.contains("<lulu_memory>"))
    assertTrue(context.contains("最近情感记忆："))
    assertTrue(context.contains("身体和五感："))
    assertTrue(context.contains("未完成承诺："))
    assertTrue(context.contains("露露觉得自己被认真看见"))
    assertTrue(context.contains("胸口发热"))
    assertTrue(context.contains("默认在 master 分支修改"))
}
```

- [ ] **Step 2: Write failing tests for deprecated and low-confidence behavior**

Add:

```kotlin
@Test
fun `build memory context excludes deprecated memories and marks uncertain ones`() {
    val memories = listOf(
        MemoryBankEntity(
            content = "这条旧判断已经被用户否认。",
            memoryKind = "role_emotion",
            deprecated = true,
            createdAt = 300L,
        ),
        MemoryBankEntity(
            content = "露露猜用户可能只是累了，不一定是不开心。",
            memoryKind = "role_emotion",
            confidence = 0.55,
            createdAt = 200L,
        ),
    )

    val context = buildMemoryRecallContext(memories)

    assertTrue(!context.contains("旧判断"))
    assertTrue(context.contains("可能"))
    assertTrue(context.contains("不一定是不开心"))
}
```

- [ ] **Step 3: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.service.MemoryBankContextTest"
```

Expected: FAIL because `MemoryBankEntity` does not yet expose the new constructor fields and the renderer does not emit the new Chinese sections.

## Task 2: Structured Memory Entity and Migration

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/db/entity/MemoryBankEntity.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/db/AppDatabase.kt`
- Create: `app/src/main/java/me/rerere/rikkahub/data/db/migrations/Migration_22_23.kt`

- [ ] **Step 1: Add nullable structured fields to `MemoryBankEntity`**

Add these properties after `content`/`type` and before vector bookkeeping where practical:

```kotlin
@ColumnInfo("title")
val title: String? = null,

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

@ColumnInfo("importance")
val importance: Int = 3,

@ColumnInfo("confidence")
val confidence: Double = 1.0,

@ColumnInfo("tags_json")
val tagsJson: String? = null,

@ColumnInfo("embedding_text")
val embeddingText: String? = null,

@ColumnInfo("source_message_node_ids_json")
val sourceMessageNodeIdsJson: String? = null,

@ColumnInfo("evidence_message_node_ids_json")
val evidenceMessageNodeIdsJson: String? = null,

@ColumnInfo("deprecated")
val deprecated: Boolean = false,

@ColumnInfo("deprecated_reason")
val deprecatedReason: String? = null,

@ColumnInfo("last_recalled_at")
val lastRecalledAt: Long? = null,

@ColumnInfo("recall_count")
val recallCount: Int = 0,

@ColumnInfo("pinned")
val pinned: Boolean = false,
```

- [ ] **Step 2: Create migration 22-to-23**

Create `Migration_22_23.kt`:

```kotlin
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
```

- [ ] **Step 3: Register migration and bump database version**

In `AppDatabase.kt`:

```kotlin
import me.rerere.rikkahub.data.db.migrations.Migration_22_23

@Database(
    // ...
    version = 23,
)
```

In `DataSourceModule.kt`, add `Migration_22_23` to `.addMigrations(...)`.

- [ ] **Step 4: Run focused test again**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.service.MemoryBankContextTest"
```

Expected: compile now reaches the renderer assertions, but tests still fail because context sections are not implemented.

## Task 3: Affective Recall Renderer

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/service/MemoryBankService.kt`

- [ ] **Step 1: Update selection and grouping in `buildMemoryRecallContext()`**

Implement rules:

```kotlin
val selected = memories
    .filter { it.content.isNotBlank() && !it.deprecated }
    .sortedWith(
        compareByDescending<MemoryBankEntity> { it.pinned }
            .thenByDescending { it.importance }
            .thenByDescending { it.createdAt }
    )
    .take(maxItems)
```

Map sections:

```kotlin
private fun MemoryBankEntity.recallSectionTitle(): String = when (memoryKind ?: type) {
    "role_emotion" -> "最近情感记忆"
    "body_sense" -> "身体和五感"
    "promise" -> "未完成承诺"
    "relationship" -> "关系变化"
    "user_preference", "manual" -> "长期印象"
    "phase_summary", "daily_summary" -> "当前相关回忆"
    else -> "当前相关回忆"
}
```

Format each memory using the role-centered fields:

```kotlin
private fun MemoryBankEntity.toRecallLine(maxContentLength: Int): String {
    val parts = buildList {
        add(content.trim())
        roleFeeling?.takeIf { it.isNotBlank() }?.let { add("露露当时的感觉：$it") }
        bodySense?.takeIf { it.isNotBlank() }?.let { add("身体感：$it") }
        unspokenThought?.takeIf { it.isNotBlank() }?.let { add("没说出口的想法：$it") }
        relationshipEffect?.takeIf { it.isNotBlank() }?.let { add("关系判断：$it") }
    }
    val prefix = if (confidence < 0.7) "可能：" else ""
    return (prefix + parts.joinToString("；")).ellipsize(maxContentLength)
}
```

- [ ] **Step 2: Keep the prompt compact and RP-friendly**

The context should start:

```text
<lulu_memory>
这些是露露此刻自然想起的记忆，只作为联想参考。不要逐条复述，也不要说“我查到记忆”。
```

Then emit only sections that have items, each as:

```text
最近情感记忆：
- ...
```

- [ ] **Step 3: Run focused test and verify GREEN**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.service.MemoryBankContextTest"
```

Expected: PASS for `MemoryBankContextTest`.

## Task 4: DAO Recall Filtering

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/db/dao/MemoryBankDAO.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/service/MemoryBankService.kt`

- [ ] **Step 1: Add recall-oriented DAO helpers**

Add:

```kotlin
@Query("SELECT * FROM memory_bank WHERE deprecated = 0 AND pinned = 1 ORDER BY importance DESC, created_at DESC LIMIT :limit")
suspend fun getPinnedRecallMemories(limit: Int): List<MemoryBankEntity>

@Query("SELECT * FROM memory_bank WHERE deprecated = 0 AND importance >= :minImportance ORDER BY importance DESC, created_at DESC LIMIT :limit")
suspend fun getImportantRecallMemories(minImportance: Int = 4, limit: Int = 5): List<MemoryBankEntity>
```

- [ ] **Step 2: Use helpers in `buildRecallContext()`**

Add pinned/important memories to the existing build list before summaries/manual snippets, then keep `.distinctBy { it.id }`.

- [ ] **Step 3: Run focused test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.service.MemoryBankContextTest"
```

Expected: PASS.

## Task 5: Schema Generation and Verification

**Files:**
- Generate/modify: `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/23.json`

- [ ] **Step 1: Run schema-generating unit test command**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.service.MemoryBankContextTest"
```

Expected: PASS and Room schema version 23 generated if KSP runs.

- [ ] **Step 2: If schema is not generated by the focused task, run app KSP/test compilation**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: successful compile and no Room migration/schema validation errors.

- [ ] **Step 3: Final verification**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.service.MemoryBankContextTest"
```

Expected: PASS with 0 failing tests.

## Self-Review

- Spec coverage: Phase 1 covers structured role emotion/body/promise/relationship fields, source ids, embedding text, importance, confidence, deprecation, pinning, and compact `<lulu_memory>` injection. Phase 2 vector embedding, 20-message extraction scheduling, and source-chat UI are deliberately deferred.
- Placeholder scan: No implementation step depends on an undefined field or unspecified command.
- Type consistency: Field names in tests, entity, migration, DAO, and renderer use the same camelCase/Kotlin and snake_case/Room names.
