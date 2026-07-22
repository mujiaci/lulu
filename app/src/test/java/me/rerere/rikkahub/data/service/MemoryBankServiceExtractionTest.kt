package me.rerere.rikkahub.data.service

import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.dao.MemoryBankDAO
import me.rerere.rikkahub.data.db.entity.MemoryBankEntity
import me.rerere.rikkahub.data.db.entity.MemoryExtractionBatchEntity
import me.rerere.rikkahub.data.db.entity.MemoryExtractionBatchStatus
import me.rerere.rikkahub.data.db.entity.MemoryExtractionCheckpointEntity
import me.rerere.rikkahub.data.db.entity.MemoryGraphEdgeEntity
import me.rerere.rikkahub.data.companion.CompanionCommitmentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryBankServiceExtractionTest {
    @Test
    fun `processed memory node ids merge without duplicates and keep recent limit`() {
        val merged = mergeProcessedMemoryNodeIds(
            existing = listOf("node-1", "node-2", "node-3"),
            incoming = listOf("node-2", "node-4", "node-5"),
            limit = 4,
        )

        assertEquals(linkedSetOf("node-2", "node-3", "node-4", "node-5"), merged)
    }

    @Test
    fun `extraction checkpoint survives even when no memory row was inserted`() = runBlocking {
        val dao = RecordingMemoryBankDAO()
        val service = MemoryBankService(dao, okHttpClient = null, context = null)

        service.markExtractionProcessed(
            assistantId = "assistant-1",
            conversationId = "conversation-1",
            sourceNodeIds = listOf("node-1", "node-2"),
            now = 123L,
        )

        assertEquals(setOf("node-1", "node-2"), service.getProcessedSourceNodeIds("assistant-1", "conversation-1"))
        assertEquals(123L, dao.extractionCheckpoint?.updatedAt)
    }

    @Test
    fun `memory extraction batch persists attempts failure and completion`() = runBlocking {
        val dao = RecordingMemoryBankDAO()
        val service = MemoryBankService(dao, okHttpClient = null, context = null)
        val first = service.beginExtractionBatch(
            "assistant-1", "conversation-1", "selected", 21, 40,
            listOf("node-21", "node-40"), 100L, 200L, "model-1", now = 300L,
        )
        assertEquals(MemoryExtractionBatchStatus.PROCESSING.name, first.status)
        assertEquals(1, first.attemptCount)
        val failed = service.failExtractionBatch(first.batchId, "invalid_json", now = 400L)
        assertEquals(MemoryExtractionBatchStatus.FAILED_RETRYABLE.name, failed.status)
        assertEquals("invalid_json", failed.lastError)
        val retry = service.beginExtractionBatch(
            "assistant-1", "conversation-1", "selected", 21, 40,
            listOf("node-21", "node-40"), 100L, 200L, "model-1", now = 500L,
        )
        assertEquals(2, retry.attemptCount)
        val completed = service.completeExtractionBatch(
            retry.batchId, MemoryExtractionBatchStatus.SUCCESS_WITH_MEMORIES, listOf(7, 8), now = 600L,
        )
        assertEquals(MemoryExtractionBatchStatus.SUCCESS_WITH_MEMORIES.name, completed.status)
        assertTrue(completed.generatedMemoryIdsJson.contains("7"))
        assertEquals(600L, completed.completedAt)
    }

    @Test
    fun `third failed memory extraction attempt requires manual review`() = runBlocking {
        val dao = RecordingMemoryBankDAO()
        val service = MemoryBankService(dao, okHttpClient = null, context = null)
        var batch = service.beginExtractionBatch(
            "assistant-1", "conversation-1", "selected", 1, 20,
            listOf("node-1", "node-20"), 10L, 20L, "model-1",
        )
        repeat(2) {
            service.failExtractionBatch(batch.batchId, "network_error")
            batch = service.beginExtractionBatch(
                "assistant-1", "conversation-1", "selected", 1, 20,
                listOf("node-1", "node-20"), 10L, 20L, "model-1",
            )
        }
        val failed = service.failExtractionBatch(batch.batchId, "network_error")
        assertEquals(MemoryExtractionBatchStatus.FAILED_MANUAL_REVIEW.name, failed.status)
        assertEquals(3, failed.attemptCount)
    }

    @Test
    fun `branch aware checkpoint only returns successful batches on selected path`() = runBlocking {
        val dao = RecordingMemoryBankDAO()
        val service = MemoryBankService(dao, okHttpClient = null, context = null)
        val branchA = service.beginExtractionBatch(
            "assistant-1", "conversation-1", "branch-a", 1, 12,
            listOf("node-1", "node-12"), 10L, 20L, "model-1",
        )
        service.completeExtractionBatch(
            branchA.batchId,
            MemoryExtractionBatchStatus.SUCCESS_EMPTY,
            emptyList(),
        )
        val branchB = service.beginExtractionBatch(
            "assistant-1", "conversation-1", "branch-b", 1, 12,
            listOf("node-1", "node-12"), 10L, 20L, "model-1",
        )
        service.completeExtractionBatch(
            branchB.batchId,
            MemoryExtractionBatchStatus.SUCCESS_EMPTY,
            emptyList(),
        )

        val processed = service.getProcessedSourceNodeIds(
            assistantId = "assistant-1",
            conversationId = "conversation-1",
            selectedBranchIdAtSequence = { "branch-a" },
        )

        assertEquals(setOf("node-1", "node-12"), processed)
        assertEquals(2, dao.extractionBatches.size)
    }

    @Test
    fun `selected branch mutation invalidates batch and deprecates generated memories`() = runBlocking {
        val dao = RecordingMemoryBankDAO(
            assistantMemories = listOf(
                MemoryBankEntity(
                    id = 8,
                    assistantId = "assistant-1",
                    conversationId = "conversation-1",
                    sourceMessageNodeIdsJson = """["node-5"]""",
                ),
            ),
        )
        val service = MemoryBankService(dao, okHttpClient = null, context = null)
        val batch = service.beginExtractionBatch(
            "assistant-1", "conversation-1", "branch-a", 1, 12,
            listOf("node-1", "node-12"), 10L, 20L, "model-1",
        )
        service.completeExtractionBatch(
            batch.batchId,
            MemoryExtractionBatchStatus.SUCCESS_WITH_MEMORIES,
            listOf(7),
        )

        val result = service.invalidateExtractionBatches(
            assistantId = "assistant-1",
            conversationId = "conversation-1",
            affectedSequence = 5,
            invalidateFollowing = false,
            selectedBranchIdAtSequence = { "branch-a" },
            fallbackSourceNodeIds = listOf("node-5"),
            reason = "source_message_edited",
            now = 300L,
        )

        assertEquals(1, result.invalidatedBatchCount)
        assertEquals(2, result.deprecatedMemoryCount)
        assertEquals(
            MemoryExtractionBatchStatus.INVALIDATED.name,
            dao.extractionBatches.getValue(batch.batchId).status,
        )
        assertEquals(setOf(7, 8), dao.deprecatedUpdates.map { it.id }.toSet())
        assertTrue(dao.deprecatedUpdates.all { it.deprecatedReason == "source_message_edited" })
    }

    @Test
    fun `assistant memory stats include vector states`() = runBlocking {
        val dao = RecordingMemoryBankDAO(
            assistantMemories = listOf(
                MemoryBankEntity(id = 1, assistantId = "assistant-1", vectorStatus = "done"),
                MemoryBankEntity(id = 2, assistantId = "assistant-1", vectorStatus = "pending"),
                MemoryBankEntity(id = 3, assistantId = "assistant-1", vectorStatus = "failed"),
                MemoryBankEntity(id = 4, assistantId = "assistant-2", vectorStatus = "done"),
            ),
        )
        val service = MemoryBankService(dao, okHttpClient = null, context = null)

        val stats = service.getStats("assistant-1")

        assertEquals(1, stats.vectorizedCount)
        assertEquals(1, stats.pendingCount)
        assertEquals(1, stats.failedCount)
    }

    @Test
    fun `stored durable memories backfill private impression`() = runBlocking {
        val dao = RecordingMemoryBankDAO(
            assistantMemories = listOf(
                MemoryBankEntity(
                    id = 1,
                    assistantId = "assistant-1",
                    content = "用户不喜欢没有证据的生活故事",
                    memoryKind = "user_boundary",
                    importance = 5,
                    confidence = 1.0,
                    createdAt = 10L,
                ),
                MemoryBankEntity(
                    id = 2,
                    assistantId = "assistant-1",
                    content = "用户更喜欢直接落地",
                    memoryKind = "user_preference",
                    importance = 4,
                    confidence = 0.9,
                    createdAt = 20L,
                ),
                MemoryBankEntity(
                    id = 3,
                    assistantId = "assistant-1",
                    content = "已被纠正的旧印象",
                    memoryKind = "user_fact",
                    deprecated = true,
                    importance = 5,
                    confidence = 1.0,
                    createdAt = 30L,
                ),
            ),
        )
        val service = MemoryBankService(dao, okHttpClient = null, context = null)

        val impression = service.buildStoredPrivateImpression(
            assistantId = "assistant-1",
            previous = me.rerere.rikkahub.data.companion.CompanionPrivateImpression(),
            nowMillis = 100L,
        )

        assertEquals(listOf("用户更喜欢直接落地"), impression.preferences)
        assertEquals(listOf("用户不喜欢没有证据的生活故事"), impression.boundaries)
        assertTrue(impression.observedTraits.isEmpty())
        assertEquals(100L, impression.updatedAt)
    }

    @Test
    fun `delete all memories removes graph edges before memory rows`() = runBlocking {
        val dao = RecordingMemoryBankDAO()
        val service = MemoryBankService(
            memoryBankDAO = dao,
            okHttpClient = null,
            context = null,
        )

        service.deleteAllMemories()

        assertEquals(listOf("edges", "checkpoints", "batches", "memories"), dao.deleteAllCalls)
    }

    @Test
    fun `save extracted memories inserts normalized candidates and returns generated ids`() = runBlocking {
        val dao = RecordingMemoryBankDAO()
        val service = MemoryBankService(
            memoryBankDAO = dao,
            okHttpClient = null,
            context = null,
        )
        val candidate = AffectiveMemoryCandidate(
            type = "relationship",
            content = "I remember the user praised my memory design, and I felt trusted.",
            roleFeeling = "happy, shy, wants to move closer",
            bodySense = "warm chest, lighter voice",
            userSignal = "The user explicitly praised the memory design.",
            relationshipEffect = "trust increased",
            importance = 5,
            confidence = 0.91,
            tags = listOf("praise", "intimacy"),
            embeddingText = "user praised lulu memory design lulu felt seen trust increased",
            sourceMessageNodeIds = listOf("user-node-1", "assistant-node-2"),
            evidenceMessageNodeIds = listOf("user-node-1"),
        )

        val saved = service.saveExtractedMemories(
            candidates = listOf(candidate),
            assistantId = "assistant-1",
            conversationId = "conversation-1",
            createdAt = 1234L,
        )

        assertEquals(1, saved.size)
        assertEquals(100, saved.single().id)
        assertEquals(1, dao.inserted.size)
        val inserted = dao.inserted.single()
        assertEquals("message", inserted.type)
        assertEquals("relationship", inserted.memoryKind)
        assertEquals("assistant-1", inserted.assistantId)
        assertEquals("conversation-1", inserted.conversationId)
        assertEquals(1234L, inserted.createdAt)
        assertEquals("pending", inserted.vectorStatus)
        assertTrue(inserted.tagsJson!!.contains("praise"))
        assertTrue(inserted.sourceMessageNodeIdsJson!!.contains("user-node-1"))
    }

    @Test
    fun `save extracted memories skips vocabulary drills without affective summary`() = runBlocking {
        val dao = RecordingMemoryBankDAO()
        val service = MemoryBankService(
            memoryBankDAO = dao,
            okHttpClient = null,
            context = null,
        )

        val saved = service.saveExtractedMemories(
            candidates = listOf(
                AffectiveMemoryCandidate(
                    type = "event",
                    content = "abandon ability absent absorb abstract abuse access accident account accuse achieve acquire adapt address adjust",
                    importance = 2,
                )
            ),
            assistantId = "assistant-1",
            conversationId = "conversation-1",
            createdAt = 1234L,
        )

        assertEquals(emptyList<MemoryBankEntity>(), saved)
        assertEquals(0, dao.inserted.size)
    }

    @Test
    fun `save extracted memory batch links cooccurring memories into graph`() = runBlocking {
        val dao = RecordingMemoryBankDAO()
        val service = MemoryBankService(
            memoryBankDAO = dao,
            okHttpClient = null,
            context = null,
        )

        val saved = service.saveExtractedMemories(
            candidates = listOf(
                AffectiveMemoryCandidate(
                    type = "user_preference",
                    content = "I remember the user prefers quiet company while concentrating.",
                    importance = 3,
                    embeddingText = "silent judgement concern",
                    userSignal = "The user asked not to be interrupted while working.",
                    sourceMessageNodeIds = listOf("user-node-1"),
                    evidenceMessageNodeIds = listOf("user-node-1"),
                ),
                AffectiveMemoryCandidate(
                    type = "promise",
                    content = "I promised to check in after the user's study session.",
                    importance = 3,
                    embeddingText = "silent action next judgement",
                    userSignal = "The user accepted a later check-in.",
                    sourceMessageNodeIds = listOf("assistant-node-2"),
                    evidenceMessageNodeIds = listOf("user-node-1"),
                ),
            ),
            assistantId = "assistant-1",
            conversationId = null,
            createdAt = 1234L,
        )

        assertEquals(listOf(100, 101), saved.map { it.id })
        assertEquals(setOf(100 to 101, 101 to 100), dao.reinforcedGraphEdges.map { it.sourceId to it.targetId }.toSet())
        assertTrue(dao.relatedMemoryUpdates[100]!!.contains("101"))
        assertTrue(dao.relatedMemoryUpdates[101]!!.contains("100"))
    }

    @Test
    fun `save extracted memories rejects raw meta reflection`() = runBlocking {
        val dao = RecordingMemoryBankDAO()
        val service = MemoryBankService(
            memoryBankDAO = dao,
            okHttpClient = null,
            context = null,
        )
        val rawToolDump = """
            Seven-layer trace: 情境感知-意义评估-状态保持-审议决策-行为实现-人格表达-经验沉淀。
            Perception=Perception layer before seven-layer deliberation:
            requested_tools=today_schedule, calendar_tool, get_app_usage;
            tool_observations=tool_result[today_schedule]={
              "success":true,
              "source":"study_app_local_store",
              "undone_tasks":[{"title":"复盘刑法学第 1 章：听众合法硕刑法课程 75-90 分钟"}]
            }
        """.trimIndent()

        val saved = service.saveExtractedMemories(
            candidates = listOf(
                AffectiveMemoryCandidate(
                    type = "cihai_reflection",
                    content = rawToolDump,
                    roleFeeling = "我重新判断后选择不立刻打扰用户。",
                    unspokenThought = "我先不去吵你，但会记得你有起床和学习安排。",
                    importance = 3,
                )
            ),
            assistantId = "assistant-1",
            conversationId = null,
            createdAt = 1234L,
        )

        assertEquals(emptyList<MemoryBankEntity>(), saved)
        assertEquals(0, dao.inserted.size)
    }

    @Test
    fun `save extracted memories deduplicates normalized content before insert`() = runBlocking {
        val dao = RecordingMemoryBankDAO()
        val service = MemoryBankService(
            memoryBankDAO = dao,
            okHttpClient = null,
            context = null,
        )
        val first = AffectiveMemoryCandidate(
            type = "user_boundary",
            content = "我记得她晚上十点后不希望我打电话。",
            userSignal = "用户明确说明边界",
            sourceMessageNodeIds = listOf("user-node-1"),
            evidenceMessageNodeIds = listOf("user-node-1"),
        )
        val duplicate = first.copy(content = " 我记得她晚上十点后，不希望我打电话。 ")

        val saved = service.saveExtractedMemories(
            candidates = listOf(first, duplicate),
            assistantId = "assistant-1",
            conversationId = "conversation-1",
            createdAt = 1234L,
        )

        assertEquals(1, saved.size)
        assertEquals(1, dao.inserted.size)
    }

    @Test
    fun `build recall context marks injected memories as recalled`() = runBlocking {
        val dao = RecordingMemoryBankDAO(
            assistantMemories = listOf(
                MemoryBankEntity(
                    id = 7,
                    content = "用户正在写论文大纲，希望露露帮她拆成更小的步骤。",
                    memoryKind = "user_preference",
                    assistantId = "assistant-1",
                    importance = 5,
                    createdAt = 300L,
                ),
                MemoryBankEntity(
                    id = 8,
                    content = "用户写论文大纲卡住时，喜欢雨天窝在床上聊天。",
                    memoryKind = "user_preference",
                    assistantId = "assistant-1",
                    importance = 3,
                    createdAt = 200L,
                ),
            )
        )
        val service = MemoryBankService(
            memoryBankDAO = dao,
            okHttpClient = null,
            context = null,
        )

        val context = service.buildRecallContext(
            assistantId = "assistant-1",
            query = "",
        )

        assertTrue(context.contains("拆成更小的步骤"))
        assertTrue(context.contains("雨天窝在床上聊天"))
        assertEquals(listOf(7, 8), dao.recalledIds)
        assertEquals("""["8"]""", dao.relatedMemoryUpdates[7])
        assertEquals("""["7"]""", dao.relatedMemoryUpdates[8])
        assertEquals(setOf(7 to 8, 8 to 7), dao.reinforcedGraphEdges.map { it.sourceId to it.targetId }.toSet())
        assertTrue(dao.reinforcedGraphEdges.all { it.delta > 0.025 && it.maxWeight == 1.5 })
        assertTrue(dao.recalledAt > 0L)
    }

    @Test
    fun `recall excludes promise memories whose runtime commitments are terminal`() = runBlocking {
        val dao = RecordingMemoryBankDAO(
            assistantMemories = listOf(
                MemoryBankEntity(
                    id = 1,
                    content = "I promised to remind her to rest tonight.",
                    memoryKind = "promise",
                    assistantId = "assistant-1",
                    sourceMessageNodeIdsJson = """["message-active"]""",
                    importance = 5,
                ),
                MemoryBankEntity(
                    id = 2,
                    content = "I promised to check an old task, but it is finished.",
                    memoryKind = "promise",
                    assistantId = "assistant-1",
                    sourceMessageNodeIdsJson = """["message-done"]""",
                    importance = 5,
                ),
            ),
        )
        val service = MemoryBankService(dao, okHttpClient = null, context = null)

        val context = service.buildRecallContext(
            assistantId = "assistant-1",
            commitmentStatusesBySourceId = mapOf(
                "message-active" to CompanionCommitmentStatus.ACTIVE,
                "message-done" to CompanionCommitmentStatus.FULFILLED,
            ),
        )

        assertTrue(context.contains("remind her to rest"))
        assertFalse(context.contains("check an old task"))
    }

    @Test
    fun `light maintenance deprecates lower scored near duplicate vector memory`() = runBlocking {
        val dao = RecordingMemoryBankDAO(
            recentMemories = listOf(
                MemoryBankEntity(
                    id = 1,
                    content = "用户正在写论文大纲，希望露露帮她拆成更小的步骤。",
                    memoryKind = "user_preference",
                    embeddingVectorJson = encodeMemoryVector(listOf(1f, 0f, 0f)),
                    importance = 5,
                    confidence = 0.9,
                    createdAt = 200L,
                ),
                MemoryBankEntity(
                    id = 2,
                    content = "用户论文大纲卡住了，需要露露温柔地一步步梳理。",
                    memoryKind = "user_preference",
                    embeddingVectorJson = encodeMemoryVector(listOf(0.96f, 0.04f, 0f)),
                    importance = 2,
                    confidence = 0.7,
                    createdAt = 100L,
                ),
                MemoryBankEntity(
                    id = 3,
                    content = "露露答应下次继续检查参考文献格式。",
                    memoryKind = "promise",
                    embeddingVectorJson = encodeMemoryVector(listOf(0f, 1f, 0f)),
                    importance = 3,
                    createdAt = 50L,
                ),
            )
        )
        val service = MemoryBankService(
            memoryBankDAO = dao,
            okHttpClient = null,
            context = null,
        )

        val result = service.runLightMaintenance()

        assertEquals(1, result.deprecatedDuplicateCount)
        assertEquals(2, dao.deprecatedUpdates.single().id)
        assertEquals("1", dao.deprecatedUpdates.single().supersededByMemoryId)
    }

    @Test
    fun `search memories applies assistant filter when keyword and type are provided`() = runBlocking {
        val dao = RecordingMemoryBankDAO(
            recentMemories = listOf(
                MemoryBankEntity(
                    id = 1,
                    assistantId = "assistant-1",
                    type = "manual",
                    content = "用户喜欢露露主动一点。",
                ),
                MemoryBankEntity(
                    id = 2,
                    assistantId = "assistant-2",
                    type = "manual",
                    content = "用户喜欢露露主动一点。",
                ),
            )
        )
        val service = MemoryBankService(
            memoryBankDAO = dao,
            okHttpClient = null,
            context = null,
        )

        val result = service.searchMemories(
            keyword = "主动",
            type = "manual",
            assistantId = "assistant-1",
        )

        assertEquals(listOf(1), result.map { it.id })
        assertEquals("assistant-1", dao.lastAssistantKeywordTypeSearch?.assistantId)
    }

    @Test
    fun `search memories keeps assistant filter without keyword or type`() = runBlocking {
        val dao = RecordingMemoryBankDAO(
            assistantMemories = listOf(
                MemoryBankEntity(id = 1, assistantId = "assistant-1", content = "one"),
                MemoryBankEntity(id = 2, assistantId = "assistant-2", content = "two"),
            ),
        )
        val service = MemoryBankService(dao, okHttpClient = null, context = null)

        val result = service.searchMemories(assistantId = "assistant-1")

        assertEquals(listOf(1), result.map { it.id })
    }

    @Test
    fun `daily archive and covered atomic memories are mutually exclusive in recall`() {
        val memories = listOf(
            MemoryBankEntity(
                id = 10,
                content = "这一天的派生摘要",
                type = "daily_summary",
                memoryKind = "daily_archive",
                sourceMemoryIdsJson = """["1","2"]""",
                importance = 5,
            ),
            MemoryBankEntity(id = 1, content = "原子记忆一", importance = 4),
            MemoryBankEntity(id = 2, content = "原子记忆二", importance = 4),
            MemoryBankEntity(id = 3, content = "另一条独立记忆", importance = 3),
        )

        val selected = selectMemoryRecallItems(memories, maxItems = 4)

        assertTrue(selected.any { it.id == 10 })
        assertTrue(selected.any { it.id == 3 })
        assertFalse(selected.any { it.id == 1 || it.id == 2 })
    }

    @Test
    fun `derived daily archive persists source memory ids`() {
        val zone = java.time.ZoneId.of("UTC")
        val dayOne = java.time.Instant.parse("2026-07-20T10:00:00Z").toEpochMilli()
        val now = java.time.Instant.parse("2026-07-22T10:00:00Z").toEpochMilli()
        val archives = buildMissingDailyMemoryArchives(
            memories = listOf(
                MemoryBankEntity(id = 1, content = "记忆一", assistantId = "assistant-1", createdAt = dayOne),
                MemoryBankEntity(id = 2, content = "记忆二", assistantId = "assistant-1", createdAt = dayOne + 1_000),
            ),
            nowMillis = now,
            zoneId = zone,
        )

        assertEquals("""["1","2"]""", archives.single().sourceMemoryIdsJson)
        assertEquals(now, archives.single().memoryCreatedAt)
        assertEquals(now, archives.single().memoryUpdatedAt)
    }

    @Test
    fun `extracted memory distinguishes inferred event and record times`() {
        val entity = AffectiveMemoryCandidate(
            type = "relationship",
            content = "她明确说这件事对关系很重要",
            userSignal = "用户明确说明",
            sourceMessageNodeIds = listOf("node-1"),
            sourceMessageAtMillis = 100L,
        ).toEntity(
            assistantId = "assistant-1",
            conversationId = "conversation-1",
            createdAt = 500L,
        )

        assertEquals(100L, entity.sourceMessageAt)
        assertEquals(100L, entity.occurredAt)
        assertTrue(entity.occurredAtInferred)
        assertEquals(500L, entity.extractedAt)
        assertEquals(500L, entity.memoryCreatedAt)
        assertEquals(500L, entity.memoryUpdatedAt)
    }
}

private class RecordingMemoryBankDAO(
    private val assistantMemories: List<MemoryBankEntity> = emptyList(),
    private val recentMemories: List<MemoryBankEntity> = emptyList(),
) : MemoryBankDAO {
    val inserted = mutableListOf<MemoryBankEntity>()
    val recalledIds = mutableListOf<Int>()
    val relatedMemoryUpdates = mutableMapOf<Int, String?>()
    val insertedGraphEdges = mutableListOf<MemoryGraphEdgeEntity>()
    val reinforcedGraphEdges = mutableListOf<ReinforcedGraphEdge>()
    val deprecatedUpdates = mutableListOf<DeprecatedMemoryUpdate>()
    val deleteAllCalls = mutableListOf<String>()
    var lastAssistantKeywordTypeSearch: AssistantKeywordTypeSearch? = null
    var recalledAt: Long = 0L
    var extractionCheckpoint: MemoryExtractionCheckpointEntity? = null
    val extractionBatches = linkedMapOf<String, MemoryExtractionBatchEntity>()

    override suspend fun insertMemory(memory: MemoryBankEntity): Long {
        inserted += memory
        return (99 + inserted.size).toLong()
    }

    override suspend fun insertMemoryGraphEdge(edge: MemoryGraphEdgeEntity): Long {
        insertedGraphEdges += edge
        return insertedGraphEdges.size.toLong()
    }

    override suspend fun upsertExtractionCheckpoint(checkpoint: MemoryExtractionCheckpointEntity) {
        extractionCheckpoint = checkpoint
    }

    override suspend fun getExtractionCheckpoint(
        assistantId: String,
        conversationId: String,
    ): MemoryExtractionCheckpointEntity? = extractionCheckpoint?.takeIf {
        it.assistantId == assistantId && it.conversationId == conversationId
    }

    override suspend fun upsertExtractionBatch(batch: MemoryExtractionBatchEntity) {
        extractionBatches[batch.batchId] = batch
    }

    override suspend fun getExtractionBatch(batchId: String): MemoryExtractionBatchEntity? =
        extractionBatches[batchId]

    override suspend fun getExtractionBatches(
        assistantId: String,
        conversationId: String,
    ): List<MemoryExtractionBatchEntity> = extractionBatches.values.filter {
        it.assistantId == assistantId && it.conversationId == conversationId
    }

    override suspend fun getExtractionBatchesByAssistant(
        assistantId: String,
    ): List<MemoryExtractionBatchEntity> = extractionBatches.values.filter {
        it.assistantId == assistantId
    }


    override suspend fun updateMemory(memory: MemoryBankEntity) = unsupported()
    override suspend fun deleteMemory(memory: MemoryBankEntity) = unsupported()
    override suspend fun deleteMemoryById(id: Int) = unsupported()
    override suspend fun deleteMemoryGraphEdgesForMemory(id: Int) = unsupported()
    override suspend fun deleteMemoryGraphEdgesForAssistant(assistantId: String) = unsupported()
    override suspend fun deleteMemoriesByAssistant(assistantId: String) = unsupported()
    override suspend fun deleteExtractionCheckpointsByAssistant(assistantId: String) = unsupported()
    override suspend fun deleteExtractionBatchesByAssistant(assistantId: String) = unsupported()
    override suspend fun deleteExtractionCheckpoint(assistantId: String, conversationId: String) = unsupported()
    override suspend fun deleteAllMemoryGraphEdges() {
        deleteAllCalls += "edges"
    }

    override suspend fun deleteAllExtractionCheckpoints() {
        deleteAllCalls += "checkpoints"
        extractionCheckpoint = null
    }

    override suspend fun deleteAllExtractionBatches() {
        deleteAllCalls += "batches"
        extractionBatches.clear()
    }

    override suspend fun deleteAllMemories() {
        deleteAllCalls += "memories"
    }
    override suspend fun getMemoryById(id: Int): MemoryBankEntity? = unsupported()
    override suspend fun getAllMemories(): List<MemoryBankEntity> = unsupported()
    override suspend fun getMemoriesByType(type: String): List<MemoryBankEntity> = unsupported()
    override suspend fun getMemoriesByTypeLimit(type: String, limit: Int): List<MemoryBankEntity> = emptyList()
    override suspend fun getMemoriesByAssistant(assistantId: String): List<MemoryBankEntity> =
        assistantMemories.filter { it.assistantId == assistantId }
    override suspend fun getMemoriesByAssistantAndTypeLimit(
        assistantId: String,
        type: String,
        limit: Int,
    ): List<MemoryBankEntity> = emptyList()

    override suspend fun getMemoriesByAssistantTypeAndDateGroup(
        assistantId: String,
        type: String,
        dateGroup: String,
    ): List<MemoryBankEntity> = emptyList()

    override suspend fun getMemoriesByTypeAndDateGroup(type: String, dateGroup: String): List<MemoryBankEntity> =
        emptyList()

    override suspend fun getDistinctAssistantIds(): List<String> = emptyList()
    override suspend fun getMemoriesByDateGroup(dateGroup: String): List<MemoryBankEntity> = unsupported()
    override suspend fun getMemoriesByDateGroupAndType(
        dateGroup: String,
        type: String,
    ): List<MemoryBankEntity> = unsupported()

    override suspend fun getMemoriesByVectorStatus(status: String): List<MemoryBankEntity> = unsupported()
    override suspend fun getPendingVectorMemories(maxRetry: Int, limit: Int): List<MemoryBankEntity> = unsupported()
    override suspend fun getMessageCountSince(sinceTimestamp: Long): Int = unsupported()
    override suspend fun getTotalMessageCount(): Int = 0
    override suspend fun getTotalCount(): Int = 0
    override suspend fun getCountByType(type: String): Int = 0
    override suspend fun getCountByVectorStatus(status: String): Int = 0
    override suspend fun getDeprecatedCount(): Int = recentMemories.count { it.deprecated }
    override suspend fun getDeprecatedCountByAssistant(assistantId: String): Int =
        recentMemories.count { it.assistantId == assistantId && it.deprecated }

    override suspend fun getCountByAssistant(assistantId: String): Int = 0
    override suspend fun getCountByAssistantAndType(assistantId: String, type: String): Int = 0
    override suspend fun getCountByAssistantAndVectorStatus(assistantId: String, status: String): Int =
        assistantMemories.count { it.assistantId == assistantId && it.vectorStatus == status }
    override suspend fun getRecentMemories(limit: Int): List<MemoryBankEntity> = recentMemories.take(limit)
    override suspend fun getDeprecatedMemories(limit: Int): List<MemoryBankEntity> =
        recentMemories.filter { it.deprecated }.take(limit)

    override suspend fun getDeprecatedMemoriesByAssistant(
        assistantId: String,
        limit: Int,
    ): List<MemoryBankEntity> = recentMemories.filter { it.assistantId == assistantId && it.deprecated }.take(limit)

    override suspend fun searchMemoriesByKeyword(keyword: String, limit: Int): List<MemoryBankEntity> = emptyList()
    override suspend fun searchDeprecatedMemoriesByKeyword(
        keyword: String,
        limit: Int,
    ): List<MemoryBankEntity> = recentMemories.filter { it.deprecated && keyword in it.content }.take(limit)

    override suspend fun searchDeprecatedMemoriesByAssistantAndKeyword(
        assistantId: String,
        keyword: String,
        limit: Int,
    ): List<MemoryBankEntity> =
        recentMemories.filter { it.assistantId == assistantId && it.deprecated && keyword in it.content }.take(limit)

    override suspend fun searchMemoriesByKeywordAndType(
        keyword: String,
        type: String,
        limit: Int,
    ): List<MemoryBankEntity> = emptyList()

    override suspend fun searchMemoriesByAssistantKeywordAndType(
        assistantId: String,
        keyword: String,
        type: String,
        limit: Int,
    ): List<MemoryBankEntity> {
        lastAssistantKeywordTypeSearch = AssistantKeywordTypeSearch(assistantId, keyword, type)
        return recentMemories
            .filter {
                it.assistantId == assistantId &&
                    it.type == type &&
                    !it.deprecated &&
                    it.content.contains(keyword)
            }
            .take(limit)
    }

    override suspend fun getPinnedRecallMemories(limit: Int): List<MemoryBankEntity> = emptyList()
    override suspend fun getImportantRecallMemories(minImportance: Int, limit: Int): List<MemoryBankEntity> =
        emptyList()

    override suspend fun getPinnedRecallMemoriesForAssistant(
        assistantId: String,
        limit: Int,
    ): List<MemoryBankEntity> = emptyList()

    override suspend fun getImportantRecallMemoriesForAssistant(
        assistantId: String,
        minImportance: Int,
        limit: Int,
    ): List<MemoryBankEntity> = emptyList()

    override suspend fun getRecentDateGroups(limit: Int): List<String> = emptyList()
    override suspend fun markMemoriesRecalled(ids: List<Int>, recalledAt: Long) {
        recalledIds += ids
        this.recalledAt = recalledAt
    }

    override suspend fun updateRelatedMemoryIds(id: Int, relatedMemoryIdsJson: String?) {
        relatedMemoryUpdates[id] = relatedMemoryIdsJson
    }

    override suspend fun reinforceMemoryGraphEdge(
        sourceId: Int,
        targetId: Int,
        delta: Double,
        maxWeight: Double,
        reinforcedAt: Long,
    ) {
        reinforcedGraphEdges += ReinforcedGraphEdge(sourceId, targetId, delta, maxWeight, reinforcedAt)
    }

    override suspend fun getMemoryGraphEdgesFromSources(
        sourceIds: List<Int>,
        minWeight: Double,
        limit: Int,
    ): List<MemoryGraphEdgeEntity> = emptyList()

    override suspend fun markMemoryDeprecated(
        id: Int,
        deprecatedReason: String?,
        supersededByMemoryId: String?,
        correctedAt: Long?,
    ) {
        deprecatedUpdates += DeprecatedMemoryUpdate(
            id = id,
            deprecatedReason = deprecatedReason,
            supersededByMemoryId = supersededByMemoryId,
            correctedAt = correctedAt,
        )
    }

    override suspend fun updateVectorStatus(id: Int, status: String, retryCount: Int) = unsupported()
    override suspend fun updateVectorResult(
        id: Int,
        status: String,
        retryCount: Int,
        vectorJson: String?,
        modelId: String?,
        dimensions: Int?,
    ) = unsupported()

    private fun unsupported(): Nothing = error("Unexpected DAO call")
}

private data class DeprecatedMemoryUpdate(
    val id: Int,
    val deprecatedReason: String?,
    val supersededByMemoryId: String?,
    val correctedAt: Long?,
)

private data class ReinforcedGraphEdge(
    val sourceId: Int,
    val targetId: Int,
    val delta: Double,
    val maxWeight: Double,
    val reinforcedAt: Long,
)

private data class AssistantKeywordTypeSearch(
    val assistantId: String,
    val keyword: String,
    val type: String,
)
