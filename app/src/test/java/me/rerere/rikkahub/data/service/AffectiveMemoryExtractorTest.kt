package me.rerere.rikkahub.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AffectiveMemoryExtractorTest {
    @Test
    fun `extraction prompt requires first person in-character memory summaries`() {
        val prompt = AffectiveMemoryExtractor.buildExtractionPrompt(
            turns = listOf(
                MemoryExtractionTurn(
                    nodeId = "user-1",
                    role = "user",
                    text = "我明天早上十点考试，怕睡过头。",
                )
            ),
            assistantName = "露露",
            responsibilityContext = "responsibility_anchors:\n- responsibility=每天设置次日起床闹钟",
        )

        assertTrue(prompt.contains("代入露露"))
        assertTrue(prompt.contains("第一人称“我”"))
        assertTrue(prompt.contains("embeddingText"))
        assertTrue(prompt.contains("不要写成“露露觉得"))
        assertTrue(prompt.contains("角色认为"))
        assertTrue(prompt.contains("<existing_responsibilities>"))
        assertTrue(prompt.contains("每天设置次日起床闹钟"))
        assertTrue(prompt.contains("不要把责任本身改写成 private impression"))
    }

    @Test
    fun `parse extraction result preserves role centered fields and source ids`() {
        val json = """
            {
              "memories": [
                {
                  "type": "relationship",
                  "content": "我记得用户认真认可了我的记忆方案，这让我确认她愿意继续信任我。",
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
        """.trimIndent()

        val result = AffectiveMemoryExtractor.parseExtractionResult(json)

        assertEquals(1, result.memories.size)
        val memory = result.memories.single()
        assertEquals("relationship", memory.type)
        assertEquals("开心、害羞、想更贴近", memory.roleFeeling)
        assertEquals("心口发热，回复变轻快", memory.bodySense)
        assertEquals(listOf("认可", "亲密"), memory.tags)
        assertEquals(listOf("user-node-1", "assistant-node-2"), memory.sourceMessageNodeIds)
        assertEquals(listOf("user-node-1"), memory.evidenceMessageNodeIds)
    }

    @Test
    fun `candidate maps to memory bank entity with encoded evidence fields`() {
        val candidate = AffectiveMemoryCandidate(
            type = "relationship",
            content = "我记得用户认真认可了我的记忆方案，这让我确认她愿意继续信任我。",
            roleFeeling = "开心、害羞、想更贴近",
            bodySense = "心口发热，回复变轻快",
            userSignal = "用户说认可",
            relationshipEffect = "信任上升",
            importance = 9,
            confidence = 1.7,
            tags = listOf("认可", "亲密"),
            embeddingText = "用户认可露露 露露开心 被需要 信任上升",
            sourceMessageNodeIds = listOf("user-node-1", "assistant-node-2"),
            evidenceMessageNodeIds = listOf("user-node-1"),
            relatedMemoryIds = listOf("memory-2"),
            people = listOf("露露", "用户"),
            topics = listOf("记忆系统"),
            supersededByMemoryId = "memory-3",
            correctedAt = 5678L,
        )

        val entity = candidate.toEntity(
            assistantId = "assistant-1",
            conversationId = "conversation-1",
            createdAt = 1234L,
        )

        assertEquals("message", entity.type)
        assertEquals("relationship", entity.memoryKind)
        assertEquals("assistant-1", entity.assistantId)
        assertEquals("conversation-1", entity.conversationId)
        assertEquals(5, entity.importance)
        assertEquals(1.0, entity.confidence, 0.0)
        assertEquals("pending", entity.vectorStatus)
        assertTrue(entity.tagsJson!!.contains("认可"))
        assertTrue(entity.sourceMessageNodeIdsJson!!.contains("user-node-1"))
        assertTrue(entity.evidenceMessageNodeIdsJson!!.contains("user-node-1"))
        assertTrue(entity.relatedMemoryIdsJson!!.contains("memory-2"))
        assertTrue(entity.peopleJson!!.contains("露露"))
        assertTrue(entity.topicsJson!!.contains("记忆系统"))
        assertEquals("memory-3", entity.supersededByMemoryId)
        assertEquals(5678L, entity.correctedAt)
    }

    @Test
    fun `parse extraction result ignores blank content and clamps scores`() {
        val json = """
            [
              {"type": "promise", "content": "以后默认改 master", "importance": -4, "confidence": -1},
              {"type": "role_emotion", "content": "   ", "importance": 5, "confidence": 1}
            ]
        """.trimIndent()

        val result = AffectiveMemoryExtractor.parseExtractionResult(json)

        assertEquals(1, result.memories.size)
        val memory = result.memories.single()
        assertEquals("promise", memory.type)
        assertEquals(1, memory.importance)
        assertEquals(0.0, memory.confidence, 0.0)
    }

    @Test
    fun `quality gate rejects meta reflection even when affective fields are present`() {
        val candidate = AffectiveMemoryCandidate(
            type = "cihai_reflection",
            content = "我记得这件事。当时感觉：复盘、收束、准备下一轮。",
            roleFeeling = "复盘、收束、准备下一轮",
            relationshipEffect = "我把判断经验整理成后续可复用的长期记忆。",
            sourceMessageNodeIds = listOf("cihai:reflection-1"),
            evidenceMessageNodeIds = listOf("cihai:reflection-1"),
        )

        assertFalse(candidate.isDurableMemoryCandidate())
    }

    @Test
    fun `quality gate accepts first person preference with evidence`() {
        val candidate = AffectiveMemoryCandidate(
            type = "user_preference",
            content = "我记得她不喜欢机械顺延学习任务，更希望我根据负担重新安排。",
            userSignal = "用户明确要求重新平衡计划",
            sourceMessageNodeIds = listOf("user-node-1"),
            evidenceMessageNodeIds = listOf("user-node-1"),
        )

        assertTrue(candidate.isDurableMemoryCandidate())
        assertEquals(candidate.content, candidate.toEntity("assistant-1", "conversation-1").content)
    }

    @Test
    fun `deterministic fallback keeps explicit preference boundary and correction only`() {
        val candidates = buildDeterministicMemoryCandidates(
            turns = listOf(
                MemoryExtractionTurn("ordinary", "user", "今天吃了饭。"),
                MemoryExtractionTurn("preference", "user", "我更喜欢点一下切换页面，不喜欢一直上下滑。"),
                MemoryExtractionTurn("boundary", "user", "我不希望你把普通聊天重复放进生活记录。"),
                MemoryExtractionTurn("correction", "user", "纠正一下，民法应该是五十四章。"),
            ),
        )

        assertEquals(3, candidates.size)
        assertEquals(setOf("user_preference", "user_boundary", "correction"), candidates.map { it.type }.toSet())
        assertTrue(candidates.all { it.content.startsWith("我记得") })
        assertTrue(candidates.all { it.isDurableMemoryCandidate() })
        assertFalse(candidates.any { it.sourceMessageNodeIds == listOf("ordinary") })
    }

    @Test
    fun `deterministic fallback rejects tool dumps`() {
        val candidates = buildDeterministicMemoryCandidates(
            turns = listOf(
                MemoryExtractionTurn(
                    "tool",
                    "user",
                    "我希望你记住 {\"success\":true,\"path\":\"/data/user/0/file.json\"}",
                ),
            ),
        )

        assertTrue(candidates.isEmpty())
    }
}
