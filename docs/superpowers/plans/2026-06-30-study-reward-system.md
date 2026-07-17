# Study Reward System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the complete Lulu postgraduate-study reward system inside the existing Android app.

**Architecture:** Add a focused study domain with serializable state, a pure rules engine, DataStore persistence, one ViewModel, and a rebuilt Compose study experience. The UI keeps existing study routes but replaces temporary in-memory state with persistent full-system behavior.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Kotlin serialization, Android DataStore Preferences, Koin ViewModel, JUnit.

---

### Task 1: Study Domain

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/data/study/StudyModels.kt`
- Create: `app/src/main/java/me/rerere/rikkahub/data/study/StudyRules.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/study/StudyRulesTest.kt`

- [ ] Add tests for sign-in, task reward, mystery box bounds, gacha cost and odds buckets, level lookup, super moment, achievements, shop refresh, and inactivity penalties.
- [ ] Implement the serializable study state and pure rule functions.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.study.StudyRulesTest"`.

### Task 2: Persistence And ViewModel

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/data/study/StudyStore.kt`
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/study/StudyVM.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/di/ViewModelModule.kt`

- [ ] Add DataStore persistence for `StudyState`.
- [ ] Add `StudyVM` actions for sign-in, add/update/delete/toggle task, pomodoro completion, draw, claim super moment, claim achievement, buy shop item, redeem McDonald's, and refresh daily state.
- [ ] Register `StudyStore` and `StudyVM` in Koin.

### Task 3: Complete Study UI

**Files:**
- Replace: `app/src/main/java/me/rerere/rikkahub/ui/pages/study/StudyPage.kt`

- [ ] Rebuild the dashboard with companion hero, kudos, level, daily progress, sign-in, super moment, and quick actions.
- [ ] Add tabs/sections for tasks, gacha, collection, achievements, shop, and event log.
- [ ] Keep pomodoro setup and focus mode with assistant chat.
- [ ] Show reward dialogs for mystery boxes, gacha results, and super moment.

### Task 4: Verification

**Files:**
- All touched files.

- [ ] Run focused unit tests.
- [ ] Run `./gradlew :app:compileDebugKotlin`.
- [ ] Fix compile or test failures.
