# Memory, Worldbook, and Presence Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore trustworthy character memory and time-aware companionship while adding a usable worldbook workflow and removing redundant tool/context noise.

**Architecture:** Keep `CompanionRuntime` as the companion state authority, reuse the existing lorebook persistence model, and introduce focused coordinators for destructive reset and concern scheduling. Passive perception is assembled before generation; only side-effecting operations remain model tools.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Koin, Kotlin coroutines/Flow, existing AI transformer and local-tool infrastructure.

---

### Task 1: Lock the behavioral contracts in focused tests

**Files:**
- Create or modify focused tests under `app/src/test/java/me/rerere/rikkahub/`
- Test memory quality, reset scope, concern scheduling, lorebook selection, and study-plan dates

- [ ] Add a failing test that rejects `cihai_reflection` and evidence-free meta summaries.
- [ ] Add a failing test that accepts natural first-person memories backed by a user message or tool result.
- [ ] Add a failing test for reset scope: role-owned data is selected while persona, lorebooks, study plan, and user profile are excluded.
- [ ] Add failing pure tests for alarm concern creation and overdue concern advancement.
- [ ] Add failing tests for global plus mounted lorebook resolution without duplicates.
- [ ] Add failing assertions for the rebalanced July 10-17 study plan.
- [ ] Run only the smallest relevant test task if Gradle configuration is already warm; otherwise preserve tests for CI/device verification.

### Task 2: Repair long-term memory ingestion

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/cihai/CihaiModels.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/cihai/CihaiService.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/service/AffectiveMemoryExtractor.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/service/MemoryBankService.kt`
- Modify DAO queries only where write-time deduplication needs them

- [ ] Remove fixed reflection entry creation from `MEMORY_REFLECT` silent presence work.
- [ ] Implement a single allowed-type and evidence quality gate before persistence.
- [ ] Reject tool dumps, framework narration, generic waiting text, and meta reflection.
- [ ] Store natural first-person content without labeled emotional/relationship suffixes.
- [ ] Add write-time idempotency by assistant, normalized content, and evidence identity.
- [ ] Run targeted tests or static searches, then `git diff --check`.
- [ ] Commit and push the memory-ingestion repair to `master`.

### Task 3: Implement both destructive-clear flows

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/memory/MemoryBankPage.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/memory/MemoryBankVM.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantBasicPage.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantDetailVM.kt`
- Create: focused role-data reset coordinator in the data/service layer
- Modify: relevant Room DAOs and repositories for role-owned delete operations

- [ ] Add memory-page confirmation and selected-assistant/all-long-term-memory deletion.
- [ ] Inventory every role-owned table and expose explicit delete-by-assistant or delete-by-conversation operations.
- [ ] Implement the reset coordinator with deterministic ordering and clear failure reporting.
- [ ] Delete conversations/calls, memory graph, cihai queues, runtime state, concerns, commitments, legacy state, presence intents, and attached favorites.
- [ ] Preserve assistant configuration, persona, avatar, model settings, mounted lorebooks, lorebook data, study plan, user profile, and unrelated app data.
- [ ] Update confirmation copy to state what is deleted and retained.
- [ ] Run targeted checks, `git diff --check`, commit, and push.

### Task 4: Make alarms and calendar events part of companionship

**Files:**
- Modify the local tool execution result bridge around `set_alarm` and `calendar_tool`
- Modify companion runtime concern/commitment repositories and background pulse service
- Modify `LivingPresenceCards.kt` only for accurate status rendering

- [ ] Parse successful tool arguments/results into a stable event subject key.
- [ ] Create or update a `CompanionConcern` and `CompanionCommitment` for important scheduled events.
- [ ] Add target-relative perception times before and after the event.
- [ ] Consume due concerns in the existing background pulse and run unified perception.
- [ ] Require each due concern outcome to remind, reschedule, complete, or cancel.
- [ ] Include current absolute time, timezone, overdue commitments, and recent explicit plans in the perception packet.
- [ ] Add regression checks for a 07:30 wake-up request and stale 09:30 sleep language.
- [ ] Run targeted checks, `git diff --check`, commit, and push.

### Task 5: Move passive perception out of model tools

**Files:**
- Modify companion perception packet assembly
- Modify local-tool registration/filtering and tool descriptions
- Modify chat, voice, and proactive generation entry points only where they build tool lists

- [ ] Classify read-only perception tools separately from side-effecting active tools.
- [ ] Collect permitted battery, usage, health, time, location, weather, and notification data before each generation path.
- [ ] Serialize a concise structured perception section and omit unavailable values honestly.
- [ ] Remove passive tools from model-visible tool definitions.
- [ ] Compress active tool descriptions to purpose, parameters, and side effects.
- [ ] Confirm MCP, plugin, and skill tools remain dynamic and are not described as a fixed count.
- [ ] Run targeted searches, `git diff --check`, commit, and push.

### Task 6: Add the worldbook app and role mounting

**Files:**
- Modify existing `Lorebook` model and settings serialization compatibly
- Create Compose pages/components under the existing desktop-app/page structure
- Modify desktop navigation/screen registration
- Modify assistant detail page mounting controls
- Modify `PromptInjectionTransformer` lorebook resolution

- [ ] Add `globalApply: Boolean = false` with backward-compatible defaults.
- [ ] Add desktop “世界书” navigation and a list/editor flow with add, edit, delete, title, body, and global toggle.
- [ ] Persist simple body content as a constant-active entry while preserving advanced entries.
- [ ] Add direct multi-select mounting on the role page.
- [ ] Resolve global and mounted lorebooks as a deduplicated ordered list.
- [ ] Inject world rules alongside, not in place of, the assistant persona.
- [ ] Review compact and narrow layouts for clipping and destructive-action affordances.
- [ ] Run targeted checks, `git diff --check`, commit, and push.

### Task 7: Finish diary, favorites, settings, and study-plan polish

**Files:**
- Modify local tools around `write_lulu_journal`
- Add a favorite-message active tool using existing favorite repository/adapter APIs
- Modify settings navigation to remove the duplicate role entry
- Modify: `app/src/main/java/me/rerere/rikkahub/data/study/ExamStudyPlan.kt`

- [ ] Remove the 100-500 character diary constraint and require natural first-person, non-template writing.
- [ ] Add a tool that favorites an existing conversation node by stable identifiers.
- [ ] Keep the favorite decision principle-based and free of keyword rules.
- [ ] Remove only the settings-page role entry; retain the desktop entry.
- [ ] Redistribute July 9 unfinished work across July 10-12, with lighter recovery/verification through July 17.
- [ ] Verify study tasks remain app-level data independent of assistant reset.
- [ ] Run targeted checks, `git diff --check`, commit, and push.

### Task 8: Final lightweight audit

**Files:**
- Review all files changed by Tasks 2-7

- [ ] Re-read the approved requirements against the final diff.
- [ ] Search for remaining fixed reflection text, `100-500`, duplicate settings role entry, and passive-tool descriptions.
- [ ] Run `git diff --check` and inspect `git status --short --branch`.
- [ ] Do not run a full Gradle build or instrumented test on this machine.
- [ ] Push the last verified commit to `origin/master` and report device-test priorities.
