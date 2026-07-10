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

- [x] Add failing tests for per-assistant batch selection, threshold triggering, age triggering, and retry eligibility.
- [x] Attempt the focused test command and retain the known unrelated test-source compilation blocker.
- [x] Add serializable queue items, settlement disposition, policy, and pure selection functions.
- [x] Add failing tests for evidence validation, forbidden trace content, unsupported relationship claims, and normalized duplicate summaries.
- [x] Implement the local quality gate and Cihai settlement prompt/parser contract.
- [x] Stop further Gradle runs after the user requested lightweight verification only.
- [x] Commit the pure settlement contracts.

### Task 2: Make CihaiStore queue updates atomic

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/cihai/CihaiStore.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/cihai/CihaiMemoryTest.kt`

- [x] Add reducer tests for add-and-enqueue, successful completion, Cihai-only completion, retry backoff, and deletion cleanup.
- [x] Replace separate entry writes with one add-and-enqueue state update.
- [x] Add atomic settlement completion and retry operations.
- [x] Normalize queue entries against existing Cihai entries and enforce bounded storage.
- [x] Perform static review and commit store behavior.

### Task 3: Consolidate due batches through the extraction model

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/cihai/CihaiService.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/service/MemoryBankService.kt`

- [x] Add a MemoryBank query that returns already processed `cihai:<entryId>` evidence for one assistant.
- [x] Replace `addEntryAndRemember` with `addEntry`, which persists first and only attempts a due batch.
- [x] Resolve the assistant and extraction model without assuming the Lulu persona.
- [x] Generate one structured consolidation result per due batch and pass it through the local quality gate.
- [x] Save accepted candidates in one call, complete all reviewed queue entries, and leave vectors to existing maintenance.
- [x] On failure, preserve the queue and apply bounded retry metadata.
- [x] Commit service integration.

### Task 4: Wire lifecycle and user-visible status

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/ai/tools/LocalTools.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/RikkaHubApp.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/cihai/CihaiPage.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/di/AppModule.kt` only if constructor dependencies change.

- [x] Update diary and silent-presence callers to the new add-only API.
- [x] Trigger due queue maintenance during application startup without blocking startup.
- [x] Display `待沉淀`, `已沉淀`, or `仅保留在辞海` from the new disposition.
- [x] Remove obsolete direct-memory conversion code and misleading method names.
- [x] Commit lifecycle and UI wiring.

### Task 5: Verify and publish

**Files:**
- Modify: this plan, checking completed steps.

- [x] Skip further Gradle test runs per the user's low-spec device constraint; existing unrelated blockers remain documented.
- [x] Skip the full Kotlin compile per the user's explicit request.
- [x] Search for `addEntryAndRemember`, direct Cihai `saveExtractedMemories`, and stale status strings.
- [x] Review `git diff --check`, `git status`, and the final commit range.
- [x] Mark this plan complete, commit verification metadata, and push `master` to `origin`.

Verification used for this phase: static source review, targeted `rg` checks, `git diff --check`, and commit-by-commit diff inspection. Device behavior verification is intentionally delegated to the user.
