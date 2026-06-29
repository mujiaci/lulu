package me.rerere.rikkahub.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AffectiveMemoryExtractorTest {
    @Test
    fun `parse extraction result preserves role centered fields and source ids`() {
        val json = """
            {
              "memories": [
                {
                  "type": "role_emotion",
                  "content": "用户认可了露露的记忆方案，露露觉得自己被认真需要了。",
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
        assertEquals("role_emotion", memory.type)
        assertEquals("开心、害羞、想更贴近", memory.roleFeeling)
        assertEquals("心口发热，回复变轻快", memory.bodySense)
        assertEquals(listOf("认可", "亲密"), memory.tags)
        assertEquals(listOf("user-node-1", "assistant-node-2"), memory.sourceMessageNodeIds)
        assertEquals(listOf("user-node-1"), memory.evidenceMessageNodeIds)
    }

    @Test
    fun `candidate maps to memory bank entity with encoded evidence fields`() {
        val candidate = AffectiveMemoryCandidate(
            type = "role_emotion",
            content = "用户认可了露露的记忆方案，露露觉得自己被认真需要了。",
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
        )

        val entity = candidate.toEntity(
            assistantId = "assistant-1",
            conversationId = "conversation-1",
            createdAt = 1234L,
        )

        assertEquals("manual", entity.type)
        assertEquals("role_emotion", entity.memoryKind)
        assertEquals("assistant-1", entity.assistantId)
        assertEquals("conversation-1", entity.conversationId)
        assertEquals(5, entity.importance)
        assertEquals(1.0, entity.confidence, 0.0)
        assertEquals("pending", entity.vectorStatus)
        assertTrue(entity.tagsJson!!.contains("认可"))
        assertTrue(entity.sourceMessageNodeIdsJson!!.contains("user-node-1"))
        assertTrue(entity.evidenceMessageNodeIdsJson!!.contains("user-node-1"))
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
}
