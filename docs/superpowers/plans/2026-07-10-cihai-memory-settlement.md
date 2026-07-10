# Cihai Memory Settlement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decouple visible Cihai records from long-term memory and persist only evidence-backed, deduplicated batch summaries.

**Architecture:** Persist lightweight per-assistant queue items beside Cihai state, select due batches with pure reducers, and consolidate each batch through the configured extraction model. Validate model candidates locally before writing them to MemoryBank, then atomically complete or retry the queue.

**Tech Stack:** Kotlin, kotlinx.serialization, DataStore Preferences, existing GenerationHandler and MemoryBankService, JUnit 4.

---

### Task 1: Define queue and settlement contracts

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/cihai/CihaiModels.kt`
- Create: `app/src/main/java/me/rerere/rikkahub/data/cihai/CihaiMemorySettlement.kt`
- Modify: `app/src/test/java/me/rerere/rikkahub/data/cihai/CihaiMemoryTest.kt`

- [ ] Add failing tests for per-assistant batch selection, threshold triggering, age triggering, and retry eligibility.
- [ ] Run the focused test command and confirm the new expectations fail or record the existing unrelated test compilation blocker.
- [ ] Add serializable queue items, settlement disposition, policy, and pure selection functions.
- [ ] Add failing tests for evidence validation, forbidden trace content, unsupported relationship claims, and normalized duplicate summaries.
- [ ] Implement the local quality gate and Cihai settlement prompt/parser contract.
- [ ] Re-run the focused tests and inspect failures.
- [ ] Commit the pure settlement contracts.

### Task 2: Make CihaiStore queue updates atomic

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/cihai/CihaiStore.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/cihai/CihaiMemoryTest.kt`

- [ ] Add reducer tests for add-and-enqueue, successful completion, Cihai-only completion, retry backoff, and deletion cleanup.
- [ ] Replace separate entry writes with one add-and-enqueue state update.
- [ ] Add atomic settlement completion and retry operations.
- [ ] Normalize queue entries against existing Cihai entries and enforce bounded storage.
- [ ] Re-run focused tests and commit store behavior.

### Task 3: Consolidate due batches through the extraction model

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/cihai/CihaiService.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/service/MemoryBankService.kt`

- [ ] Add a MemoryBank query that returns already processed `cihai:<entryId>` evidence for one assistant.
- [ ] Replace `addEntryAndRemember` with `addEntry`, which persists first and only attempts a due batch.
- [ ] Resolve the assistant and extraction model without assuming the Lulu persona.
- [ ] Generate one structured consolidation result per due batch and pass it through the local quality gate.
- [ ] Save accepted candidates in one call, complete all reviewed queue entries, and process vectors opportunistically.
- [ ] On failure, preserve the queue and apply bounded retry metadata.
- [ ] Commit service integration.

### Task 4: Wire lifecycle and user-visible status

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/ai/tools/LocalTools.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/RikkaHubApp.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/cihai/CihaiPage.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/di/AppModule.kt` only if constructor dependencies change.

- [ ] Update diary and silent-presence callers to the new add-only API.
- [ ] Trigger due queue maintenance during application startup without blocking startup.
- [ ] Display `待沉淀`, `已沉淀`, or `仅保留在辞海` from the new disposition.
- [ ] Remove obsolete direct-memory conversion code and misleading method names.
- [ ] Commit lifecycle and UI wiring.

### Task 5: Verify and publish

**Files:**
- Modify: this plan, checking completed steps.

- [ ] Run the focused Cihai test command and capture any pre-existing compilation blockers.
- [ ] Run `./gradlew :app:compileDebugKotlin --console=plain`.
- [ ] Search for `addEntryAndRemember`, direct Cihai `saveExtractedMemories`, and stale status strings.
- [ ] Review `git diff --check`, `git status`, and the final commit range.
- [ ] Mark this plan complete, commit verification metadata, and push `master` to `origin`.

