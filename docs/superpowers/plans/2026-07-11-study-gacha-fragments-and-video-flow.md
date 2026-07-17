# Study Gacha Fragments and Video Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace generic entertainment fragments with five purpose-specific fragments, add purple/gold/rainbow full-screen draw animations, guarantee video-fragment playback, and raise the broadcast-exercise planning preference to 70%.

**Architecture:** Keep draw economics in `StudyRules`, video catalog selection/unlocking in `StarWishRules`, and UI sequencing in a pure `DrawRevealFlow` state machine. The Compose dialog owns exactly one active `VideoView`; after an opening video it releases the player and renders a cached final-frame bitmap behind cards and reward videos.

**Tech Stack:** Kotlin, kotlinx.serialization, Jetpack Compose Material 3, Android `VideoView`, `MediaMetadataRetriever`, DataStore, JUnit 4.

---

## File map

- Modify `app/src/main/java/me/rerere/rikkahub/data/study/StudyModels.kt`: define explicit fragment kinds and inventory/reward fields.
- Modify `app/src/main/java/me/rerere/rikkahub/data/study/StudyRules.kt`: split draw results, update rewards, redemptions, achievements, shop items, and persistence cleanup.
- Modify `app/src/main/java/me/rerere/rikkahub/data/study/StudyStore.kt`: replace legacy entertainment migration with deletion of obsolete balances.
- Modify `app/src/main/java/me/rerere/rikkahub/data/starwish/StarWishRules.kt`: select and unlock video rewards using `videoFragments`.
- Modify `app/src/main/java/me/rerere/rikkahub/ui/pages/study/StudyVM.kt`: coordinate a draw with `StarWishStore` and attach reserved videos to reveal items.
- Modify `app/src/main/java/me/rerere/rikkahub/ui/pages/study/DrawRevealFlow.kt`: add purple opening and pending reward-video sequencing.
- Modify `app/src/main/java/me/rerere/rikkahub/ui/pages/study/StudyPage.kt`: use one full-screen video layer and a bitmap opening backdrop; update inventory UI.
- Modify `app/src/main/java/me/rerere/rikkahub/ui/pages/starwish/StarWishVM.kt`: spend explicit theater/video fragments.
- Modify `app/src/main/java/me/rerere/rikkahub/ui/pages/starwish/StarWishPage.kt`: display explicit theater/video balances.
- Modify `app/src/main/java/me/rerere/rikkahub/data/study/ExamStudyPlan.kt`: state the 70% broadcast-exercise weight.
- Replace/add raw MP4 resources under `app/src/main/res/raw/`.
- Update focused tests under `app/src/test/java/me/rerere/rikkahub/`.

### Task 1: Introduce the explicit fragment schema and discard obsolete balances

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/study/StudyModels.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/study/StudyStore.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/study/StudyStorePersistenceTest.kt`

- [ ] **Step 1: Write a failing persistence test**

Replace the legacy migration assertion with a test that decodes both old and new fields and proves old balances are discarded:

```kotlin
@Test
fun `obsolete generic entertainment fragments are discarded while explicit fragments persist`() {
    val raw = """
        {
          "inventory": {
            "epicFragments": 8,
            "rainbowFragments": 7,
            "universalRareFragments": 6,
            "universalEpicFragments": 5,
            "douyinFragments": 1,
            "theaterFragments": 2,
            "gameFragments": 3,
            "videoFragments": 4,
            "animeFragments": 5
          }
        }
    """.trimIndent()

    val decoded = decodeStudyStateOrNull(raw)

    requireNotNull(decoded)
    assertEquals(1, decoded.inventory.douyinFragments)
    assertEquals(2, decoded.inventory.theaterFragments)
    assertEquals(3, decoded.inventory.gameFragments)
    assertEquals(4, decoded.inventory.videoFragments)
    assertEquals(5, decoded.inventory.animeFragments)
}
```

- [ ] **Step 2: Run the persistence test and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.study.StudyStorePersistenceTest"
```

Expected: compilation fails because the five explicit inventory properties do not exist.

- [ ] **Step 3: Add explicit fragment types and fields**

In `StudyModels.kt`, add:

```kotlin
@Serializable
enum class StudyFragmentType(val label: String, val rarity: StudyRarity) {
    Douyin("抖音碎片", StudyRarity.Rare),
    Theater("剧场碎片", StudyRarity.Rare),
    Game("游戏碎片", StudyRarity.Epic),
    Video("视频碎片", StudyRarity.Epic),
    Anime("动漫碎片", StudyRarity.Rainbow),
}
```

Change `StudyInventory` to contain:

```kotlin
val douyinFragments: Int = 0,
val theaterFragments: Int = 0,
val gameFragments: Int = 0,
val videoFragments: Int = 0,
val animeFragments: Int = 0,
```

Remove `epicFragments`, `rainbowFragments`, `universalRareFragments`, `universalEpicFragments`, and the legacy special-story balance. Add the same five counters to `StudyReward`. Extend `StudyDrawResult` with:

```kotlin
val fragmentType: StudyFragmentType? = null,
```

Keep `fragmentType = null` for normal outfit/universal-normal results.

- [ ] **Step 4: Remove the obsolete migration pipeline**

Delete `migrateLegacyEntertainmentFragments()` and remove every invocation from `StudyStore`. Because `studyJson` already has `ignoreUnknownKeys = true`, old serialized fields are ignored and new counters default to zero.

- [ ] **Step 5: Run the persistence test and verify GREEN**

Run the Task 1 test command. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit the schema change**

```powershell
git add app/src/main/java/me/rerere/rikkahub/data/study/StudyModels.kt app/src/main/java/me/rerere/rikkahub/data/study/StudyStore.kt app/src/test/java/me/rerere/rikkahub/data/study/StudyStorePersistenceTest.kt
git commit -m "refactor: replace generic entertainment fragments"
```

### Task 2: Split draw outcomes and update the study economy

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/study/StudyRules.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/study/StudyRulesTest.kt`

- [ ] **Step 1: Write failing tests for explicit draw outcomes**

Add deterministic tests using the existing fixed-random helpers:

```kotlin
@Test
fun `purple draw splits into douyin and theater fragments`() {
    val douyin = StudyRules.draw(affordableState(), 1, SequenceRandom(double = 0.95, int = 0))
    val theater = StudyRules.draw(affordableState(), 1, SequenceRandom(double = 0.95, int = 1))

    assertEquals(StudyFragmentType.Douyin, douyin.results.single().fragmentType)
    assertEquals(StudyFragmentType.Theater, theater.results.single().fragmentType)
}

@Test
fun `gold draw splits into game and video fragments`() {
    val game = StudyRules.draw(affordableState(), 1, SequenceRandom(double = 0.98, int = 0))
    val video = StudyRules.draw(affordableState(), 1, SequenceRandom(double = 0.98, int = 1))

    assertEquals(StudyFragmentType.Game, game.results.single().fragmentType)
    assertEquals(StudyFragmentType.Video, video.results.single().fragmentType)
}

@Test
fun `rainbow draw always grants anime fragment`() {
    val result = StudyRules.draw(affordableState(), 1, SequenceRandom(double = 0.995, int = 0))

    assertEquals(StudyFragmentType.Anime, result.results.single().fragmentType)
    assertEquals(1, result.state.inventory.animeFragments)
}
```

Adapt helper names to the existing test utilities rather than adding a second random framework.

- [ ] **Step 2: Run `StudyRulesTest` and verify RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.study.StudyRulesTest"
```

Expected: failures because draw results still use generic entertainment keys and balances.

- [ ] **Step 3: Implement the 50/50 internal splits**

In `drawOne`, preserve current rarity thresholds and use one `nextInt(2)` after a rare or epic hit:

```kotlin
roll < 0.97 -> when (random.nextInt(2)) {
    0 -> StudyDrawResult(StudyRarity.Rare, "rare:douyin", "抖音碎片", StudyFragmentType.Douyin)
    else -> StudyDrawResult(StudyRarity.Rare, "rare:theater", "剧场碎片", StudyFragmentType.Theater)
}
roll < 0.99 -> when (random.nextInt(2)) {
    0 -> StudyDrawResult(StudyRarity.Epic, "epic:game", "游戏碎片", StudyFragmentType.Game)
    else -> StudyDrawResult(StudyRarity.Epic, "epic:video", "视频碎片", StudyFragmentType.Video)
}
else -> StudyDrawResult(StudyRarity.Rainbow, "rainbow:anime", "动漫碎片", StudyFragmentType.Anime)
```

Update `StudyInventory.addDrawResult` to increment the exact counter.

- [ ] **Step 4: Replace generic reward and redemption references**

Expand `StudyEntertainmentReward` to `Douyin`, `Theater`, `Game`, `Video`, and `Anime`, and consume only the matching counter. Replace old level, achievement, shop, internal grant, compensation, and achievement-condition fields with explicit fields. Use deterministic fixed rewards (for example level 4 Douyin, level 6 Theater, level 8 Game, level 10 Video) and do not recreate a user-selectable generic fragment.

- [ ] **Step 5: Run `StudyRulesTest` and verify GREEN**

Run the Task 2 command. Expected: `BUILD SUCCESSFUL` with all study-rule tests passing.

- [ ] **Step 6: Commit the economy change**

```powershell
git add app/src/main/java/me/rerere/rikkahub/data/study/StudyRules.kt app/src/test/java/me/rerere/rikkahub/data/study/StudyRulesTest.kt
git commit -m "feat: split entertainment fragment rewards"
```

### Task 3: Reserve and unlock videos for video-fragment draw results

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/starwish/StarWishRules.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/study/StudyVM.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/di/ViewModelModule.kt` only if constructor registration requires an explicit declaration
- Test: `app/src/test/java/me/rerere/rikkahub/data/starwish/StarWishRulesTest.kt`

- [ ] **Step 1: Write failing video reservation tests**

Replace gold-fragment tests with explicit video-fragment tests and add a no-video retention case:

```kotlin
@Test
fun videoFragmentUnlockPrioritizesRandomLockedVideo() {
    val state = StarWishState(customVideos = listOf(video("a"), video("b")))
    val study = StudyState(inventory = StudyInventory(videoFragments = 1))

    val result = StarWishRules.unlockNextVideo(state, study, FixedIntRandom(1))

    assertEquals("b", result.video?.id)
    assertEquals(setOf("b"), result.starWishState.unlockedVideoIds)
    assertEquals(0, result.studyState.inventory.videoFragments)
    assertTrue(result.consumedFragment)
}

@Test
fun videoFragmentIsRetainedWhenNoVisibleVideoExists() {
    val study = StudyState(inventory = StudyInventory(videoFragments = 1))

    val result = StarWishRules.unlockNextVideo(StarWishState(), study, FixedIntRandom(0))

    assertEquals(null, result.video)
    assertEquals(1, result.studyState.inventory.videoFragments)
}
```

- [ ] **Step 2: Run `StarWishRulesTest` and verify RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.starwish.StarWishRulesTest"
```

Expected: compile failures on `videoFragments`.

- [ ] **Step 3: Update `StarWishRules`**

Change `unlockNextVideo` to consume `videoFragments`. Keep locked-video priority and random replay behavior. Change theater chapter affordability helpers to use `theaterFragments`.

- [ ] **Step 4: Coordinate draw and Star Wish state in `StudyVM`**

Inject `StarWishStore` into `StudyVM`. After `StudyRules.draw`, walk results in order. For each `StudyFragmentType.Video`, call `StarWishRules.unlockNextVideo` against the latest in-memory study/star-wish states, persist both returned states, and build:

```kotlin
StudyDrawReveal(result = drawResult, video = unlock.video)
```

Non-video results use `video = null`. A missing video leaves the newly drawn `videoFragments` balance untouched. Persist the final `StarWishState` once per draw batch.

- [ ] **Step 5: Run focused Star Wish and study tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.starwish.StarWishRulesTest" --tests "me.rerere.rikkahub.data.study.StudyRulesTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit video reservation**

```powershell
git add app/src/main/java/me/rerere/rikkahub/data/starwish/StarWishRules.kt app/src/main/java/me/rerere/rikkahub/ui/pages/study/StudyVM.kt app/src/main/java/me/rerere/rikkahub/di/ViewModelModule.kt app/src/test/java/me/rerere/rikkahub/data/starwish/StarWishRulesTest.kt
git commit -m "feat: reserve videos for video fragment draws"
```

### Task 4: Extend the pure reveal state machine

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/study/DrawRevealFlow.kt`
- Modify: `app/src/test/java/me/rerere/rikkahub/ui/pages/study/DrawRevealFlowTest.kt`

- [ ] **Step 1: Add failing priority and queue tests**

Add `RareOpeningVideo` coverage and explicit pending-video behavior:

```kotlin
@Test
fun purpleOnlyBatchStartsWithPurpleOpening() {
    val state = DrawRevealFlow.start(listOf(draw(StudyRarity.Normal), draw(StudyRarity.Rare)))
    assertEquals(DrawRevealPhase.RareOpeningVideo, state.phase)
}

@Test
fun openingPriorityIsRainbowThenEpicThenRare() {
    assertEquals(DrawRevealPhase.RainbowOpeningVideo, DrawRevealFlow.start(listOf(draw(StudyRarity.Rare), draw(StudyRarity.Epic), draw(StudyRarity.Rainbow))).phase)
    assertEquals(DrawRevealPhase.EpicOpeningVideo, DrawRevealFlow.start(listOf(draw(StudyRarity.Rare), draw(StudyRarity.Epic))).phase)
    assertEquals(DrawRevealPhase.RareOpeningVideo, DrawRevealFlow.start(listOf(draw(StudyRarity.Rare))).phase)
}

@Test
fun skipAllReturnsEveryUnplayedRewardVideoIndexInDrawOrder() {
    assertEquals(listOf(1, 3), DrawRevealFlow.pendingRewardVideoIndexes(setOf(1, 3), emptySet()))
    assertEquals(listOf(3), DrawRevealFlow.pendingRewardVideoIndexes(setOf(1, 3), setOf(1)))
}
```

- [ ] **Step 2: Run `DrawRevealFlowTest` and verify RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "me.rerere.rikkahub.ui.pages.study.DrawRevealFlowTest"
```

- [ ] **Step 3: Implement the pure transitions**

Add `RareOpeningVideo` to `DrawRevealPhase` and `openingVideoPhases`. Select opening phase using `Rainbow > Epic > Rare`. Add a pure ordered set-difference helper for reward video indices so the Compose layer never reimplements queue selection.

- [ ] **Step 4: Run `DrawRevealFlowTest` and verify GREEN**

Run the Task 4 command. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the state machine**

```powershell
git add app/src/main/java/me/rerere/rikkahub/ui/pages/study/DrawRevealFlow.kt app/src/test/java/me/rerere/rikkahub/ui/pages/study/DrawRevealFlowTest.kt
git commit -m "feat: sequence three-tier draw reveals"
```

### Task 5: Install the three animations and enforce one active player

**Files:**
- Replace: `app/src/main/res/raw/star_wish_epic_draw.mp4`
- Create: `app/src/main/res/raw/star_wish_rare_draw.mp4`
- Replace: `app/src/main/res/raw/star_wish_rainbow_draw.mp4`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/study/StudyPage.kt`

- [ ] **Step 1: Copy the confirmed animation files**

Copy with PowerShell (binary assets are not edited through `apply_patch`):

```powershell
Copy-Item -LiteralPath 'C:\Users\Administrator\Documents\Tencent Files\1187725424\FileRecv\MobileFile\1783688323279..mp4' -Destination 'app\src\main\res\raw\star_wish_epic_draw.mp4' -Force
Copy-Item -LiteralPath 'C:\Users\Administrator\Documents\Tencent Files\1187725424\FileRecv\MobileFile\1783344254557..mp4' -Destination 'app\src\main\res\raw\star_wish_rare_draw.mp4' -Force
Copy-Item -LiteralPath 'C:\Users\Administrator\Documents\Tencent Files\1187725424\FileRecv\MobileFile\1783688327753..mp4' -Destination 'app\src\main\res\raw\star_wish_rainbow_draw.mp4' -Force
```

- [ ] **Step 2: Add the purple URI and reduce playback to a single video layer**

Add:

```kotlin
private const val DEFAULT_RARE_DRAW_VIDEO_URI = "raw:star_wish_rare_draw"
```

Replace simultaneous opening/reward video composables with a single selected playback request:

```kotlin
val activeVideo = when (revealState.phase) {
    DrawRevealPhase.RareOpeningVideo -> DrawPlayback.Opening(DEFAULT_RARE_DRAW_VIDEO_URI)
    DrawRevealPhase.EpicOpeningVideo -> DrawPlayback.Opening(DEFAULT_EPIC_DRAW_VIDEO_URI)
    DrawRevealPhase.RainbowOpeningVideo -> DrawPlayback.Opening(DEFAULT_RAINBOW_DRAW_VIDEO_URI)
    DrawRevealPhase.RewardVideo -> rewardVideoUri?.let(DrawPlayback::Reward)
    else -> null
}
```

Render exactly one `DrawVideoLayer` when `activeVideo != null`. When an opening completes, load/cache its final frame bitmap and release the `VideoView`; when a reward video completes, release it and restore the cached opening bitmap. Do not retain a paused `VideoView` as a backdrop.

- [ ] **Step 3: Route normal and skip-all navigation through the pending-video queue**

When a card with `video != null` becomes current, enter `RewardVideo` once. “跳过全部” selects the first pending reward-video index; each completion selects the next pending index; only an empty queue enters `Summary`. Close and load-error callbacks mark the current index played and continue with the same helper.

- [ ] **Step 4: Verify resource packaging and Kotlin compilation**

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:processDebugResources
```

Expected: `BUILD SUCCESSFUL`; Android resource linking recognizes all three raw resources.

- [ ] **Step 5: Commit playback and assets**

```powershell
git add app/src/main/res/raw/star_wish_epic_draw.mp4 app/src/main/res/raw/star_wish_rare_draw.mp4 app/src/main/res/raw/star_wish_rainbow_draw.mp4 app/src/main/java/me/rerere/rikkahub/ui/pages/study/StudyPage.kt
git commit -m "feat: add full-screen three-tier draw animations"
```

### Task 6: Update inventory, redemption, and Star Wish UI

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/study/StudyPage.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/study/StudyVM.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/starwish/StarWishVM.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/starwish/StarWishPage.kt`

- [ ] **Step 1: Replace generic inventory cards**

Show five purpose-specific balances and actions:

```kotlin
FragmentInventoryCard("抖音碎片", inventory.douyinFragments, StudyColors.purple, listOf("刷抖音" to onRedeemDouyin))
FragmentInventoryCard("剧场碎片", inventory.theaterFragments, StudyColors.purple, listOf("进入小剧场" to onOpenStarWish))
FragmentInventoryCard("游戏碎片", inventory.gameFragments, StudyColors.goldText, listOf("玩游戏" to onRedeemGame))
FragmentInventoryCard("视频碎片", inventory.videoFragments, StudyColors.goldText, listOf("进入视频馆" to onOpenStarWish))
FragmentInventoryCard("动漫碎片", inventory.animeFragments, rarityColor(StudyRarity.Rainbow), listOf("看动漫" to onRedeemAnime))
```

Remove UI that offers a choice between two uses from one generic balance.

- [ ] **Step 2: Update Star Wish consumers**

Theater creation spends `theaterFragments`; video unlocking spends `videoFragments`. Update copy, progress, enabled-state, and error-message logic accordingly. Keep direct replay of already unlocked videos free.

- [ ] **Step 3: Compile the application UI**

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL` and no remaining production references to removed generic fields.

- [ ] **Step 4: Search for obsolete production references**

```powershell
rg -n "universalRareFragments|universalEpicFragments|epicFragments|rainbowFragments|通用紫色|通用金色" app/src/main/java
```

Expected: no matches, except explicitly documented serialized legacy names if retained in a compatibility test only.

- [ ] **Step 5: Commit UI integration**

```powershell
git add app/src/main/java/me/rerere/rikkahub/ui/pages/study/StudyPage.kt app/src/main/java/me/rerere/rikkahub/ui/pages/study/StudyVM.kt app/src/main/java/me/rerere/rikkahub/ui/pages/starwish/StarWishVM.kt app/src/main/java/me/rerere/rikkahub/ui/pages/starwish/StarWishPage.kt
git commit -m "feat: expose purpose-specific entertainment fragments"
```

### Task 7: Raise broadcast exercise preference to 70%

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/study/ExamStudyPlan.kt`
- Modify: `app/src/test/java/me/rerere/rikkahub/data/study/ExamStudyPlanTest.kt`

- [ ] **Step 1: Write the failing prompt contract test**

```kotlin
@Test
fun `light exercise guidance gives broadcast exercise seventy percent preference`() {
    assertTrue(ExamStudyPlan.studyHabitReference.contains("70%"))
    assertTrue(ExamStudyPlan.studyHabitReference.contains("第八套广播体操"))
    assertTrue(ExamStudyPlan.studyHabitReference.contains("其余轻运动共享 30%"))
}
```

- [ ] **Step 2: Run `ExamStudyPlanTest` and verify RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.study.ExamStudyPlanTest"
```

- [ ] **Step 3: Update the planning guidance**

Change the movement paragraph to explicitly say that when a light-activity block is needed, the eighth broadcast exercise receives about 70% preference, while hand dance, walking in place, window-standing, joint mobility, and weather-appropriate walking share 30%; health and weather constraints may override the preference.

- [ ] **Step 4: Run `ExamStudyPlanTest` and verify GREEN**

Run the Task 7 test command. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the planning preference**

```powershell
git add app/src/main/java/me/rerere/rikkahub/data/study/ExamStudyPlan.kt app/src/test/java/me/rerere/rikkahub/data/study/ExamStudyPlanTest.kt
git commit -m "feat: prioritize broadcast exercise in study plans"
```

### Task 8: Full regression verification

**Files:**
- Verify all modified files

- [ ] **Step 1: Run all focused unit tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.study.StudyStorePersistenceTest" --tests "me.rerere.rikkahub.data.study.StudyRulesTest" --tests "me.rerere.rikkahub.data.starwish.StarWishRulesTest" --tests "me.rerere.rikkahub.ui.pages.study.DrawRevealFlowTest" --tests "me.rerere.rikkahub.data.study.ExamStudyPlanTest"
```

Expected: all selected tests pass.

- [ ] **Step 2: Run app unit tests and debug build**

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` with zero failing tests.

- [ ] **Step 3: Inspect the final diff without touching unrelated work**

```powershell
git status --short
git diff --check
git diff --stat origin/master...HEAD
```

Confirm the pre-existing edits in `ProactiveMessageService.kt` and `ProactiveMessageAssemblyTest.kt` were neither staged nor modified by this feature.

- [ ] **Step 4: Manual device acceptance check**

On an emulator or device, force/debug draws for purple, gold, and rainbow and verify: full-screen playback; priority `彩 > 金 > 紫`; final-frame background; video reward full-screen cover; return to the background; multiple video fragments all play under “跳过全部”; a playback error cannot trap the dialog.

- [ ] **Step 5: Commit any verification-only corrections**

If verification required corrections, repeat the relevant RED/GREEN test cycle, then commit only those feature files with:

```powershell
git commit -m "fix: stabilize gacha video reveal flow"
```
