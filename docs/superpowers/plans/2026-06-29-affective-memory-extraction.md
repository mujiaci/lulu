# Affective Memory Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a tested extraction boundary that turns LLM-produced memory-candidate JSON into structured `MemoryBankEntity` rows without yet calling a real model or embedding provider.

**Architecture:** Keep extraction pure and deterministic in `AffectiveMemoryExtractor`: prompt construction, JSON parsing, validation, and candidate-to-entity mapping live in one small service file. `MemoryBankService` gains a save method that accepts parsed candidates and persists them. Real scheduling, model calls, and vector embedding are deferred to the next slice.

**Tech Stack:** Kotlin, kotlinx.serialization, Room entity mapping, JUnit 4.

---

## File Structure

- Create `app/src/main/java/me/rerere/rikkahub/data/service/AffectiveMemoryExtractor.kt`
  - Defines `AffectiveMemoryCandidate`, `MemoryExtractionTurn`, `AffectiveMemoryExtractionResult`.
  - Builds the extraction prompt.
  - Parses JSON arrays or `{ "memories": [...] }` payloads from model text.
  - Maps candidates to `MemoryBankEntity`.
- Create `app/src/test/java/me/rerere/rikkahub/data/service/AffectiveMemoryExtractorTest.kt`
  - Tests parsing, validation, source ids, and entity mapping.
- Modify `app/src/main/java/me/rerere/rikkahub/data/service/MemoryBankService.kt`
  - Adds `saveExtractedMemories(...)` using parsed candidates.

## Task 1: Parser and Mapping Tests

- [ ] **Step 1: Write failing parser test**

Create `AffectiveMemoryExtractorTest.kt` with a test that parses:

```json
{
  "memories": [
    {
      "type": "role_emotion",
      "content": "用户认可了露露的记忆方案，露露觉得自己被认真需要了。",
      "roleFeeling": "开心、害羞、想更贴近",
      "bodySense": "心口发热，回复变轻快",
      "userSignal": "用户说认可",
      "relationshipEffect": "信任上升",
      "importance": 5,
      "confidence": 0.92,
      "tags": ["认可", "亲密"],
      "embeddingText": "用户认可露露 露露开心 被需要 信任上升",
      "sourceMessageNodeIds": ["user-node-1", "assistant-node-2"],
      "evidenceMessageNodeIds": ["user-node-1"]
    }
  ]
}
```

Expected assertions: one candidate, fields preserved, tags/source/evidence JSON can later be encoded.

- [ ] **Step 2: Write failing entity mapping test**

Assert `toEntity(assistantId, conversationId, createdAt)` maps:

- `memoryKind = "role_emotion"`
- legacy `type = "manual"`
- `sourceMessageNodeIdsJson` contains `user-node-1`
- `tagsJson` contains `认可`
- `vectorStatus = "pending"`

- [ ] **Step 3: Run focused test and verify RED**

Run:

```powershell
$env:ANDROID_HOME='C:\Android'
$env:ANDROID_SDK_ROOT='C:\Android'
$env:GRADLE_USER_HOME='C:\Users\Administrator\AppData\Local\Temp\lulu-gradle-home-verify'
$env:PATH="C:\Users\Administrator\AppData\Local\Temp\codex-pnpm-shim;$env:PATH"
.\gradlew.bat --no-daemon --no-configuration-cache "-Dorg.gradle.cache.internal.locklistener=false" :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.service.AffectiveMemoryExtractorTest"
```

Expected: FAIL because `AffectiveMemoryExtractor` does not exist yet.

## Task 2: Extractor Implementation

- [ ] **Step 1: Implement serializable candidate types and parser**

Use `JsonInstant` to decode either a top-level array or an object with `memories`.

- [ ] **Step 2: Implement entity mapping**

Clamp `importance` to `1..5`, clamp `confidence` to `0.0..1.0`, drop blank content candidates.

- [ ] **Step 3: Run focused test and verify GREEN**

Same Gradle command. Expected: PASS.

## Task 3: MemoryBankService Save Entry

- [ ] **Step 1: Add `saveExtractedMemories`**

`MemoryBankService.saveExtractedMemories(candidates, assistantId, conversationId)` maps candidates to entities and inserts them with `MemoryBankDAO.insertMemory`.

- [ ] **Step 2: Add service unit coverage only if a lightweight fake DAO is practical**

If DAO faking is noisy, rely on parser/mapping test and focused compile.

- [ ] **Step 3: Run focused memory tests**

Run both `AffectiveMemoryExtractorTest` and `MemoryBankContextTest`.

## Self-Review

- This slice does not call a model, does not schedule every 20 messages, and does not create embeddings.
- It gives the next slice a stable JSON contract and a tested mapping into the Room schema.
