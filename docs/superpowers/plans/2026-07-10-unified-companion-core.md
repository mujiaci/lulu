# Unified Companion Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a role-neutral persisted companion core for relationship state, concerns, commitments, and embodied state, then give existing living-presence events stable per-assistant identities.

**Architecture:** New pure Kotlin reducers own deterministic lifecycle rules, while a DataStore-backed `CompanionStore` is the single persisted snapshot per assistant. Existing Lulu and living-presence types remain readable through adapters during migration, but new core types contain no fixed role behavior. The current chat and proactive services are not fully migrated in this plan; they receive compatibility entry points in the next plan.

**Tech Stack:** Kotlin, kotlinx.serialization, Android DataStore Preferences, coroutines `StateFlow`, JUnit.

---

## File Structure

- Create `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionModels.kt`: role-neutral persisted contracts and enums.
- Create `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionRelationshipReducer.kt`: idempotent relationship event reduction.
- Create `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionConcernReducer.kt`: concern identity, merge, completion, and ordering.
- Create `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionCommitmentReducer.kt`: commitment lifecycle and replacement rules.
- Create `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionStore.kt`: persisted snapshots and atomic updates.
- Create `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionLegacyAdapter.kt`: one-way legacy imports.
- Modify `app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt`: register `CompanionStore`.
- Modify `app/src/main/java/me/rerere/rikkahub/service/LivingPresenceEvent.kt`: add stable subject identity and assistant-safe legacy merge.
- Modify `app/src/main/java/me/rerere/rikkahub/service/RollingJudgmentLoop.kt`: persist legacy `subjectKey` for migration.
- Add focused tests under `app/src/test/java/me/rerere/rikkahub/data/companion/` and update `LivingPresenceEventTest.kt`.

### Task 1: Role-Neutral Persisted Contracts

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionModels.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/companion/CompanionModelsTest.kt`

- [ ] **Step 1: Write serialization and default-state tests**

Create tests that instantiate two assistants and prove snapshots, relationships, concerns, and commitments retain distinct `assistantId` values after JSON round-trip:

```kotlin
class CompanionModelsTest {
    @Test
    fun `snapshot serialization keeps assistants isolated`() {
        val state = CompanionPersistedState(
            snapshots = listOf(
                CompanionSnapshot.empty("assistant-a"),
                CompanionSnapshot.empty("assistant-b"),
            ),
        )
        val decoded = JsonInstant.decodeFromString<CompanionPersistedState>(
            JsonInstant.encodeToString(state),
        )
        assertEquals(setOf("assistant-a", "assistant-b"), decoded.snapshots.map { it.assistantId }.toSet())
    }
}
```

- [ ] **Step 2: Run the focused test and confirm the missing types fail compilation**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.companion.CompanionModelsTest"
```

Expected: compilation fails because `CompanionPersistedState` does not exist.

- [ ] **Step 3: Implement complete persisted contracts**

Define serializable contracts with defaults for migration:

```kotlin
@Serializable
data class CompanionPersistedState(
    val version: Int = CURRENT_COMPANION_SCHEMA_VERSION,
    val snapshots: List<CompanionSnapshot> = emptyList(),
    val appliedRelationshipEventIds: List<String> = emptyList(),
)

@Serializable
data class CompanionSnapshot(
    val assistantId: String,
    val state: CompanionState = CompanionState(),
    val relationship: CompanionRelationshipState = CompanionRelationshipState(),
    val concerns: List<CompanionConcern> = emptyList(),
    val commitments: List<CompanionCommitment> = emptyList(),
    val updatedAt: Long = 0L,
) {
    companion object {
        fun empty(assistantId: String) = CompanionSnapshot(assistantId = assistantId)
    }
}
```

Also define:

- `CompanionState`
- `CompanionRelationshipState`
- `CompanionRelationshipEvent` and `CompanionRelationshipEventKind`
- `CompanionConcern` and `CompanionConcernStatus`
- `CompanionCommitment`, `CompanionCommitmentStatus`, and `CompanionActionPlan`

All numeric relationship dimensions use `0f..1f`; default values are neutral and do not assume a relationship archetype.

- [ ] **Step 4: Run the focused test when the environment permits**

Expected: PASS. If Gradle exceeds the agreed lightweight verification budget, record the timeout and continue with static checks.

- [ ] **Step 5: Commit the contracts**

```powershell
git add app/src/main/java/me/rerere/rikkahub/data/companion/CompanionModels.kt app/src/test/java/me/rerere/rikkahub/data/companion/CompanionModelsTest.kt
git commit -m "Add role-neutral companion contracts"
```

### Task 2: Relationship Event Reducer

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionRelationshipReducer.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/companion/CompanionRelationshipReducerTest.kt`

- [ ] **Step 1: Write reducer tests**

Cover:

```kotlin
@Test fun `same relationship event is applied once`()
@Test fun `relationship deltas are clamped to zero and one`()
@Test fun `events cannot update another assistant`()
@Test fun `repair lowers unresolved tension without forcing closeness`()
```

The idempotency test applies the same event twice and expects unchanged state on the second application.

- [ ] **Step 2: Implement the pure reducer**

Use this public contract:

```kotlin
data class CompanionRelationshipReduction(
    val relationship: CompanionRelationshipState,
    val appliedEventIds: Set<String>,
)

object CompanionRelationshipReducer {
    fun apply(
        assistantId: String,
        current: CompanionRelationshipState,
        appliedEventIds: Set<String>,
        events: List<CompanionRelationshipEvent>,
        nowMillis: Long,
    ): CompanionRelationshipReduction
}
```

Filter by matching assistant, discard blank IDs, apply events in timestamp order, clamp all dimensions, and retain at most the latest 2,000 event IDs.

- [ ] **Step 3: Run the focused reducer test when possible**

Run the single test class and expect all four cases to pass.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/java/me/rerere/rikkahub/data/companion/CompanionRelationshipReducer.kt app/src/test/java/me/rerere/rikkahub/data/companion/CompanionRelationshipReducerTest.kt
git commit -m "Add idempotent relationship reducer"
```

### Task 3: Concern Identity And Lifecycle

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionConcernReducer.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/companion/CompanionConcernReducerTest.kt`

- [ ] **Step 1: Write lifecycle tests**

Prove:

- Same `assistantId + subjectKey` updates one concern.
- Different assistants with the same subject remain separate.
- Two deadlines with different `subjectKey` values remain separate.
- A completed concern is not accidentally reopened without an explicit `REOPEN` change.
- Due concerns sort by importance and `nextPerceptionAt`.

- [ ] **Step 2: Implement normalized subject keys and changes**

```kotlin
sealed interface CompanionConcernChange {
    data class Upsert(val concern: CompanionConcern) : CompanionConcernChange
    data class Complete(val assistantId: String, val concernId: String, val reason: String) : CompanionConcernChange
    data class Cancel(val assistantId: String, val concernId: String, val reason: String) : CompanionConcernChange
    data class Reopen(val concern: CompanionConcern) : CompanionConcernChange
}
```

`upsert` matches only active concerns with identical assistant and normalized subject. Preserve the original ID and source IDs while updating event, goal, importance, and next perception.

- [ ] **Step 3: Run focused tests when possible and commit**

```powershell
git add app/src/main/java/me/rerere/rikkahub/data/companion/CompanionConcernReducer.kt app/src/test/java/me/rerere/rikkahub/data/companion/CompanionConcernReducerTest.kt
git commit -m "Add assistant-safe concern lifecycle"
```

### Task 4: Commitment State Machine

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionCommitmentReducer.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/companion/CompanionCommitmentReducerTest.kt`

- [ ] **Step 1: Write state-machine tests**

Cover these transitions:

```text
PROPOSED -> ACTIVE -> DUE -> EXECUTING -> FULFILLED
                                     -> FAILED -> RETRY_SCHEDULED -> DUE
ACTIVE -> CANCELLED
ACTIVE -> SUPERSEDED
```

Also prove an unrelated new commitment never removes an existing active one.

- [ ] **Step 2: Implement validated changes**

```kotlin
sealed interface CompanionCommitmentChange {
    data class Upsert(val commitment: CompanionCommitment) : CompanionCommitmentChange
    data class Transition(
        val assistantId: String,
        val commitmentId: String,
        val status: CompanionCommitmentStatus,
        val reason: String,
        val nextDueAt: Long? = null,
    ) : CompanionCommitmentChange
}
```

Only allow transitions from the table above. `Upsert` with the same assistant and subject supersedes the old active commitment and keeps both records for audit; unrelated commitments remain untouched.

- [ ] **Step 3: Run focused tests when possible and commit**

```powershell
git add app/src/main/java/me/rerere/rikkahub/data/companion/CompanionCommitmentReducer.kt app/src/test/java/me/rerere/rikkahub/data/companion/CompanionCommitmentReducerTest.kt
git commit -m "Add durable commitment state machine"
```

### Task 5: Atomic Companion Store

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionStore.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/companion/CompanionStateNormalizationTest.kt`

- [ ] **Step 1: Write normalization tests**

Test the pure `normalized()` function separately from Android DataStore:

- duplicate assistant snapshots merge without losing active concerns or commitments;
- snapshots are ordered deterministically;
- terminal records are retained within bounded history limits;
- blank assistant IDs are discarded.

- [ ] **Step 2: Implement DataStore persistence**

Follow the existing `LivingPresenceStore` pattern with a separate `companion_runtime` Preferences DataStore. Public API:

```kotlin
class CompanionStore(context: Context, scope: AppScope, json: Json = JsonInstant) {
    val state: StateFlow<CompanionPersistedState>
    suspend fun update(transform: (CompanionPersistedState) -> CompanionPersistedState)
    suspend fun updateSnapshot(assistantId: String, transform: (CompanionSnapshot) -> CompanionSnapshot)
    fun snapshot(assistantId: String): CompanionSnapshot
}
```

Every write normalizes and clamps persisted state. Do not silently overwrite malformed JSON; log and fall back for the read while preserving the stored raw preference until a successful write.

- [ ] **Step 3: Register the store in Koin**

Add one singleton in `DataSourceModule.kt` using the existing `AppScope` binding.

- [ ] **Step 4: Run static imports/build checks and commit**

```powershell
git add app/src/main/java/me/rerere/rikkahub/data/companion/CompanionStore.kt app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt app/src/test/java/me/rerere/rikkahub/data/companion/CompanionStateNormalizationTest.kt
git commit -m "Persist unified companion snapshots"
```

### Task 6: One-Way Legacy Import

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionLegacyAdapter.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/companion/CompanionLegacyAdapterTest.kt`

- [ ] **Step 1: Write migration tests**

Prove:

- Existing `LuluState` maps to a generic `CompanionState` without hardcoding the new assistant name.
- Existing relationship labels map to approximate numeric values once.
- Existing `LivingIntent` maps to a concern with the same assistant and stable subject.
- Importing the same legacy data twice produces the same snapshot.

- [ ] **Step 2: Implement pure adapters**

```kotlin
fun LuluState.toCompanionState(): CompanionState
fun LuluState.toCompanionRelationshipState(): CompanionRelationshipState
fun LivingIntent.toCompanionConcern(): CompanionConcern
fun importLegacyCompanionSnapshot(
    assistantId: String,
    current: CompanionSnapshot,
    legacyStates: List<LuluState>,
    legacyIntents: List<LivingIntent>,
): CompanionSnapshot
```

Adapters are one-way. New core writes must never update legacy structures.

- [ ] **Step 3: Run focused tests when possible and commit**

```powershell
git add app/src/main/java/me/rerere/rikkahub/data/companion/CompanionLegacyAdapter.kt app/src/test/java/me/rerere/rikkahub/data/companion/CompanionLegacyAdapterTest.kt
git commit -m "Add companion legacy import adapters"
```

### Task 7: Repair Legacy Event Identity During Migration

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/service/LivingPresenceEvent.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/service/RollingJudgmentLoop.kt`
- Modify: `app/src/test/java/me/rerere/rikkahub/service/LivingPresenceEventTest.kt`

- [ ] **Step 1: Add regression tests for the existing bug**

Create two events with the same kind but different assistants and assert two intents remain. Create two deadline events for one assistant with different target times and subjects and assert two intents remain.

- [ ] **Step 2: Add compatible subject identity**

Add defaulted `subjectKey: String = ""` to `LivingPresenceEvent` and `LivingIntent`. Compute it from explicit target/deadline plus a normalized meaningful text prefix. Existing serialized values decode with a fallback derived from kind and creation time.

- [ ] **Step 3: Restrict legacy merge identity**

Replace the old match:

```kotlin
intent.kind == eventKind
```

with:

```kotlin
intent.kind == eventKind &&
    intent.assistantId == event.assistantId &&
    intent.subjectKey == event.subjectKey
```

Blank legacy assistant IDs only match blank event IDs; do not treat blank as a wildcard during merge.

- [ ] **Step 4: Run the focused regression test when possible and commit**

```powershell
git add app/src/main/java/me/rerere/rikkahub/service/LivingPresenceEvent.kt app/src/main/java/me/rerere/rikkahub/service/RollingJudgmentLoop.kt app/src/test/java/me/rerere/rikkahub/service/LivingPresenceEventTest.kt
git commit -m "Isolate living presence event identities"
```

### Task 8: Lightweight Verification And Next-Plan Handoff

**Files:**
- Verify all files in this plan.
- Create next plan: `docs/superpowers/plans/2026-07-10-unified-companion-chat-proactive.md`

- [ ] **Step 1: Run static verification**

```powershell
git diff --check HEAD~7..HEAD
rg -n "CompanionSnapshot|CompanionConcern|CompanionCommitment" app/src/main/java app/src/test/java
rg -n "existingIntents.firstOrNull" app/src/main/java/me/rerere/rikkahub/service/LivingPresenceEvent.kt
```

- [ ] **Step 2: Attempt one focused Gradle test batch only if compilation is warm**

Use a bounded timeout. Do not repeatedly restart Gradle on this machine. Record timeout or failure accurately.

- [ ] **Step 3: Confirm unrelated localization changes remain unstaged unless intentionally committed separately**

```powershell
git status --short
```

- [ ] **Step 4: Write the chat/proactive migration plan**

The next plan must cover:

- `CompanionPerceptionAssembler` and `CompanionRuntime` interfaces.
- ChatService integration.
- ProactiveMessageService integration.
- replacing queue clearing with durable commitments;
- action result feedback;
- state UI compatibility;
- deletion of `LuluIntentPlanner` only after production references reach zero.

## Self-Review

- Spec coverage: this plan covers the role-neutral contracts, relationship reducer, concern identity, commitment lifecycle, persistence, legacy import, and immediate cross-assistant event bug. Chat/proactive and memory migrations are deliberately assigned to subsequent plans because they are independent high-risk subsystems.
- Placeholder scan: no task depends on undefined behavior; lifecycle transitions, merge keys, commands, paths, and public signatures are explicit.
- Type consistency: all reducers operate on the types defined in Task 1; `CompanionSnapshot` is the only per-assistant persisted aggregate; `CompanionStore` stores `CompanionPersistedState` only.
