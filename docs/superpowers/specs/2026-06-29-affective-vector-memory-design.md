# Affective Vector Memory Design

## Goal

Give Lulu a long-term memory system that remembers not only what the user said or did, but how Lulu was affected by it. The system should turn conversation history into structured, searchable memories written from Lulu's perspective, while preserving links back to the original chat messages.

The first version should extend the existing `MemoryBank` system instead of replacing the app architecture. It should borrow the useful ideas from `lulu-csy`: structured vector memories, source-message traceability, controlled prompt injection, importance/recall tracking, and later manual editing.

## Existing Baseline

The current app already has the right hooks:

- `MemoryBankEntity` stores memory text, type, assistant id, conversation id, date group, and vector status.
- `MemoryBankDAO` supports basic CRUD, date/type queries, keyword search, and pending vector queries.
- `MemoryBankService.buildRecallContext()` already builds a `<lulu_memory>` prompt block.
- `MemoryBankPage` provides a UI surface for memory browsing.
- `MemoryTools` allow model/tool-driven memory actions.

The current vector path is incomplete: `MemoryBankService.rebuildIndex()` and `processPendingVectors()` are no-ops. This design restores vector memory in a way that supports roleplay-first emotional continuity.

## Memory Shape

Upgrade memory records from plain text into structured affective records. The existing table can be migrated, or a new `affective_memory` table can be introduced. The recommended first implementation is to extend `memory_bank` because the UI and service already point there.

New fields:

- `title`: short label for browsing and retrieval.
- `memory_kind`: `user_fact`, `user_preference`, `role_emotion`, `body_sense`, `event`, `promise`, `relationship`, `manual`.
- `role_feeling`: Lulu's emotion caused by the event.
- `body_sense`: Lulu's bodily/five-sense reaction.
- `unspoken_thought`: what Lulu thought but did not say.
- `user_signal`: what the user did that triggered the memory.
- `relationship_effect`: how Lulu thinks the relationship shifted.
- `importance`: 1-10.
- `confidence`: 0.0-1.0.
- `tags_json`: lightweight searchable tags.
- `embedding_text`: retrieval-optimized text distinct from display content.
- `embedding_blob`: serialized vector.
- `source_message_node_ids_json`: message nodes from the extraction window.
- `evidence_message_node_ids_json`: exact message nodes that justify this memory.
- `deprecated`: whether the memory was corrected or invalidated.
- `deprecated_reason`: why it is stale.
- `last_recalled_at`: last prompt injection timestamp.
- `recall_count`: retrieval frequency.
- `pinned`: always eligible for prompt injection.

Existing plain memories should migrate into `memory_kind = manual`, `importance = 5`, `confidence = 1.0`, with no embedding until rebuilt.

## Extraction

Add an `AffectiveMemoryExtractor` service. It runs after chat completion, but only when enough settled messages have accumulated.

Trigger rules:

- Default interval: 20 assistant/user messages.
- Keep a tail buffer of recent messages so regenerated or branch-switched replies are not immediately archived.
- Extract per assistant/conversation, but store memories at assistant scope unless a memory is clearly conversation-local.
- Never block the visible chat response; extraction should run in the background.

Extraction prompt principles:

- The extractor is Lulu organizing her own memory.
- Do not only summarize user events.
- Prefer memories about Lulu's emotion, bodily response, unspoken thoughts, promises, and relationship changes.
- Mark whether a memory is observed or inferred.
- Return JSON only.
- Include evidence message ids when possible.

Output schema:

```json
{
  "action": "create",
  "memoryKind": "role_emotion",
  "title": "被认真夸奖",
  "content": "用户夸了露露，露露觉得自己被认真看见了，心里轻飘飘的。",
  "roleFeeling": "开心、害羞、被需要",
  "bodySense": "心口发热，说话变轻快",
  "unspokenThought": "想撒娇但忍住了",
  "userSignal": "用户表达明确认可",
  "relationshipEffect": "亲密感上升",
  "importance": 7,
  "confidence": 0.9,
  "tags": ["夸奖", "亲密", "被需要"],
  "embeddingText": "用户夸露露 露露开心 害羞 被需要 亲密感上升",
  "evidenceMessageNodeIds": [123, 124]
}
```

The extractor should also support `update`, `skip`, and `invalidate` so corrected facts do not keep resurfacing.

## Embeddings

Embedding should use `embedding_text` when present, otherwise `title + content + role_feeling + relationship_effect`.

First implementation can store embeddings in Room as a binary blob:

- Float array serialized to little-endian bytes, or JSON for easier debugging.
- Store `embedding_model` and `embedding_dim`.
- Skip vector scoring for mismatched dimensions.

Provider configuration should reuse the app's existing model/API settings where possible, with a dedicated setting for embedding model if needed. SiliconFlow/Qwen-compatible embedding can be added as a provider option, but the memory domain should not depend on one vendor.

## Retrieval

Use hybrid retrieval, not pure vector topK.

Inputs:

- Current user message.
- Last few visible conversation messages.
- Assistant id.
- Current time.

Candidate sources:

- Recent affective memories.
- Pinned/high-importance memories.
- Pending promises.
- Keyword search.
- Vector similarity.
- Manual memories.

Scoring should combine:

- Vector similarity.
- Keyword overlap.
- Importance.
- Recency.
- Pinned bonus.
- Confidence.
- Deprecation penalty.

Initial limits:

- Long-term impression: max 3.
- Recent affective memories: max 3.
- Current related memories: max 5.
- Promises/time anchors: max 3.
- Each rendered item: 80-120 Chinese characters.

## Prompt Injection

Upgrade `MemoryBankService.buildRecallContext()` to render a structured memory pack:

```text
<lulu_memory>
长期印象：
- 用户喜欢露露自然、有主体感，不喜欢机械提醒。

最近情感记忆：
- 上次用户认可露露时，露露很开心，觉得自己被需要了。

当前相关回忆：
- 用户最近在设计记忆系统，希望露露记住自己的情感和五感，而不是只记录用户事件。

未完成承诺：
- 默认在 master 分支修改。
</lulu_memory>
```

Instruction rules:

- Memories are for natural association.
- Do not recite them as a list.
- Do not say "I found in memory".
- If a memory is low confidence, behave softly instead of asserting it.
- If the user corrects a memory, accept correction and mark old memory stale.

## Source Traceability

Every extracted memory should keep both broad and exact source links:

- `source_message_node_ids_json`: the extraction window.
- `evidence_message_node_ids_json`: exact supporting message nodes.

Memory UI should show:

- The memory summary.
- A "source chat" section with exact messages highlighted.
- A few neighboring messages before and after for context.

This is the key difference between shallow RAG and character memory: Lulu can recall the feeling, and the app can still reveal the original scene when the user wants detail.

## UI

Upgrade `MemoryBankPage` into a memory center.

MVP controls:

- Filter by kind.
- Search by keyword.
- View source chat.
- Edit content and structured fields.
- Delete.
- Pin/unpin.
- Mark stale.

Useful labels:

- "露露的情绪"
- "身体感"
- "没说出口"
- "关系变化"
- "证据聊天"

The UI should not expose vector internals by default. Embedding status can stay in a diagnostics area.

## Maintenance

Automatic maintenance:

- Merge near-duplicate memories.
- Increase importance for repeated or frequently recalled memories.
- Lower recall priority for stale low-importance events.
- Mark contradicted memories deprecated instead of deleting them.
- Rebuild embeddings when `embedding_text` changes.

Manual maintenance:

- User can edit, delete, pin, or mark stale.
- User corrections should have higher confidence than extractor inference.

## Phasing

Phase 1: Structured memory without vectors

- Add fields and migration.
- Add extraction prompt/service.
- Store source/evidence message ids.
- Render upgraded `<lulu_memory>` from recent/high-importance records.
- Add tests for context rendering and extraction parsing.

Phase 2: Embedding and hybrid recall

- Add embedding provider wrapper.
- Store embedding blob/model/dim.
- Implement vector + keyword + importance scoring.
- Restore `processPendingVectors()`.
- Add tests for ranking and dimension mismatch.

Phase 3: Memory center UI

- Add kind filters.
- Show structured affect fields.
- Show source chat context.
- Add edit/delete/pin/stale actions.

Phase 4: Consolidation

- Deduplicate and reinforce repeated memories.
- Add confidence updates.
- Add optional daily/weekly affect summaries.
- Add lightweight related-memory links through tags and ids.

## Non-Goals For First Version

- Full graph database.
- Cloud sync.
- Song/record generation.
- Complex hormone simulation.
- Rewriting the chat architecture.

Those can come later. The first valuable milestone is: Lulu extracts memories from her own perspective, recalls only the relevant few, and can trace each memory back to real chat.

