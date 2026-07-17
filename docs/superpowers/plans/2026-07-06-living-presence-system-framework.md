# Living Presence System Framework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the old seven-layer/fixed-cadence living presence system with the approved perception-first concern system.

**Architecture:** Keep compatibility with existing store and proactive services while changing the public and model contract from `LivingIntent`/`nextEvaluateAt`/fixed cadence toward `ConcernCard`/`nextPerceptionAt`/dynamic judgment. The first implementation pass updates core data aliases, planner prompts, visible Cihai cards, action names, and tests without a full service rewrite.

**Tech Stack:** Kotlin, kotlinx.serialization, Android DataStore, Compose UI helpers, JUnit.

---

### Task 1: Core Contract Renaming And Data Shape

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/service/RollingJudgmentLoop.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/service/RollingJudgmentLoopTest.kt`

- [ ] Add `nextPerceptionAt` and `perceptionCadence` compatibility properties on `LivingIntent`.
- [ ] Rename the public architecture trace from seven-layer wording to perception-first wording.
- [ ] Remove `MEMORY_UPDATE` from action pool and replace old public action names with `WRITE_DIARY` and `SCHEDULE_NEXT_PERCEPTION`.
- [ ] Keep deprecated aliases only where needed for decoding or old callers, but do not expose them in prompts/cards/action pools.

### Task 2: Model Prompt And Parser

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/service/LivingJudgmentModelPlanner.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/service/LivingJudgmentModelPlannerTest.kt`

- [ ] Rewrite prompt to require full `PerceptionPacket` style input and persona/context in every judgment.
- [ ] Replace `traitMotive` and `situationalMotive` requirements with `meaningToRole`, `intention`, and first-person `thought`.
- [ ] Parse `nextPerceptionDelayMinutes` while accepting old `nextEvaluateDelayMinutes` as a migration fallback.
- [ ] Normalize old action names into the new action pool.

### Task 3: Concern Card UI

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/cihai/LivingPresenceCards.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/ui/pages/cihai/LivingPresenceCardsTest.kt`

- [ ] Change user-visible cards from belief/motive/intention/cadence to event/goal/next perception.
- [ ] Sort by `nextPerceptionAt`.
- [ ] Keep card construction compatible with current `LivingIntent` storage.

### Task 4: Planner And Memory Policy

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/service/LivingPresencePlanner.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/cihai/CihaiModels.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/service/LivingPresencePlannerTest.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/cihai/CihaiMemoryTest.kt`

- [ ] Update proactive plan reason text to the new perception-first framework.
- [ ] Remove public `MEMORY_UPDATE` and `JOURNAL_WRITE` wording from planner action pools.
- [ ] Add explicit Cihai memory policy for recent context, unsummarized context, and 60-entry summary threshold.

### Task 5: Lightweight Verification And Publish

**Files:**
- Verify modified files only.

- [ ] Run focused tests for affected classes if the machine can handle them.
- [ ] Run `git diff --check`.
- [ ] Confirm branch is `master`.
- [ ] Stage only files changed for this task, preserving unrelated deletions.
- [ ] Commit and push to `origin/master`.
