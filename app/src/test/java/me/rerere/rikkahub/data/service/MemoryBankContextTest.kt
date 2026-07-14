package me.rerere.rikkahub.data.service

import me.rerere.rikkahub.data.db.entity.MemoryBankEntity
import me.rerere.rikkahub.data.db.entity.MemoryGraphEdgeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryBankContextTest {
    @Test
    fun `build memory context renders affective role memory sections`() {
        val memories = listOf(
            MemoryBankEntity(
                content = "用户夸了露露，露露觉得自己被认真看见了。",
                type = "manual",
                memoryKind = "role_emotion",
                roleFeeling = "开心、害羞、想靠近",
                bodySense = "胸口发热，回复变轻快",
                relationshipEffect = "亲密度上升",
                createdAt = 300L,
            ),
            MemoryBankEntity(
                content = "以后默认在 master 分支修改。",
                type = "manual",
                memoryKind = "promise",
                importance = 5,
                createdAt = 200L,
            ),
        )

        val context = buildMemoryRecallContext(memories)

        assertTrue(context.contains("<lulu_memory>"))
        assertTrue(context.contains("最近情感记忆："))
        assertTrue(context.contains("身体和五感："))
        assertTrue(context.contains("未完成承诺："))
        assertTrue(context.contains("露露觉得自己被认真看见"))
        assertTrue(context.contains("代入当前角色"))
        assertTrue(context.contains("第一人称"))
        assertTrue(context.contains("我当时的感觉：开心、害羞、想靠近"))
        assertTrue(context.contains("我的身体感：胸口发热，回复变轻快"))
        assertTrue(context.contains("我的关系判断：亲密度上升"))
        assertTrue(!context.contains("露露当时的感觉"))
        assertTrue(context.contains("胸口发热"))
        assertTrue(context.contains("默认在 master 分支修改"))
    }

    @Test
    fun `build memory context excludes deprecated memories and marks uncertain ones`() {
        val memories = listOf(
            MemoryBankEntity(
                content = "这条旧判断已经被用户否认。",
                memoryKind = "role_emotion",
                deprecated = true,
                createdAt = 300L,
            ),
            MemoryBankEntity(
                content = "露露猜用户可能只是累了，不一定是不开心。",
                memoryKind = "role_emotion",
                confidence = 0.55,
                createdAt = 200L,
            ),
        )

        val context = buildMemoryRecallContext(memories)

        assertTrue(!context.contains("旧判断"))
        assertTrue(context.contains("可能"))
        assertTrue(context.contains("不一定是不开心"))
    }

    @Test
    fun `build memory context renders legacy summaries as factual archive sections`() {
        val memories = listOf(
            MemoryBankEntity(content = "今天上午她说论文写不下去了", type = "phase_summary", createdAt = 300L),
            MemoryBankEntity(content = "她最近在准备考研", type = "daily_summary", createdAt = 200L),
            MemoryBankEntity(content = "她不喜欢太硬的打卡提醒", type = "manual", createdAt = 100L),
        )

        val context = buildMemoryRecallContext(memories)

        assertTrue(context.contains("<lulu_memory>"))
        assertTrue(context.contains("月度核心记忆"))
        assertTrue(context.contains("每日归档"))
        assertTrue(context.contains("长期印象"))
        assertTrue(context.contains("她不喜欢太硬的打卡提醒"))
        assertTrue(context.contains("今天上午她说论文写不下去了"))
        assertTrue(context.contains("她最近在准备考研"))
        assertTrue(context.contains("不要逐条复述"))
    }

    @Test
    fun `build memory context returns blank when there are no memories`() {
        assertEquals("", buildMemoryRecallContext(emptyList()))
    }

    @Test
    fun `build memory context trims long content and limits item count`() {
        val memories = (1..8).map { index ->
            MemoryBankEntity(
                content = "memory-$index " + "x".repeat(180),
                type = "manual",
                createdAt = index.toLong(),
            )
        }

        val context = buildMemoryRecallContext(memories, maxItems = 3, maxContentLength = 24)

        assertTrue(context.contains("memory-8"))
        assertTrue(context.contains("memory-6"))
        assertTrue(!context.contains("memory-5"))
        assertTrue(context.contains("..."))
    }

    @Test
    fun `build memory context boosts memories related to current query`() {
        val memories = listOf(
            MemoryBankEntity(
                content = "露露被夸以后觉得很开心。",
                memoryKind = "role_emotion",
                importance = 5,
                createdAt = 500L,
            ),
            MemoryBankEntity(
                content = "用户最近在准备论文大纲，卡住时希望露露轻一点陪她梳理。",
                memoryKind = "user_preference",
                importance = 2,
                topicsJson = """["论文","大纲"]""",
                createdAt = 100L,
            ),
            MemoryBankEntity(
                content = "露露答应帮用户继续看论文大纲，不要突然忘掉这件事。",
                memoryKind = "promise",
                importance = 3,
                createdAt = 50L,
            ),
            MemoryBankEntity(
                content = "用户喜欢雨天窝在床上聊天。",
                memoryKind = "user_preference",
                importance = 4,
                createdAt = 400L,
            ),
        )

        val context = buildMemoryRecallContext(
            memories = memories,
            query = "论文大纲写不下去",
            maxItems = 2,
        )

        assertTrue(context.contains("论文大纲"))
        assertTrue(context.contains("答应帮用户继续看论文大纲"))
        assertTrue(!context.contains("雨天窝在床上"))
    }

    @Test
    fun `build memory context can rank memories by vector similarity`() {
        val memories = listOf(
            MemoryBankEntity(
                content = "露露记得用户要的是轻一点的陪伴，不是催促。",
                memoryKind = "user_preference",
                embeddingVectorJson = encodeMemoryVector(listOf(1f, 0f, 0f)),
                importance = 1,
                createdAt = 10L,
            ),
            MemoryBankEntity(
                content = "用户喜欢雨天窝在床上聊天。",
                memoryKind = "user_preference",
                embeddingVectorJson = encodeMemoryVector(listOf(0f, 1f, 0f)),
                importance = 5,
                createdAt = 100L,
            ),
        )

        val context = buildMemoryRecallContext(
            memories = memories,
            queryVector = listOf(1f, 0f, 0f),
            maxItems = 1,
        )

        assertTrue(context.contains("轻一点的陪伴"))
        assertTrue(!context.contains("雨天窝在床上"))
    }

    @Test
    fun `build memory context removes near duplicate vector memories`() {
        val memories = listOf(
            MemoryBankEntity(
                content = "用户正在写论文大纲，希望露露帮她拆成更小的步骤。",
                memoryKind = "user_preference",
                embeddingVectorJson = encodeMemoryVector(listOf(1f, 0f, 0f)),
                importance = 5,
                createdAt = 300L,
            ),
            MemoryBankEntity(
                content = "用户论文大纲卡住了，需要露露温柔地一步步梳理。",
                memoryKind = "user_preference",
                embeddingVectorJson = encodeMemoryVector(listOf(0.96f, 0.04f, 0f)),
                importance = 3,
                createdAt = 200L,
            ),
            MemoryBankEntity(
                content = "露露答应下次继续检查参考文献格式。",
                memoryKind = "promise",
                embeddingVectorJson = encodeMemoryVector(listOf(0f, 1f, 0f)),
                importance = 3,
                createdAt = 100L,
            ),
        )

        val context = buildMemoryRecallContext(
            memories = memories,
            query = "论文大纲",
            queryVector = listOf(1f, 0f, 0f),
            maxItems = 2,
        )

        assertTrue(context.contains("拆成更小的步骤"))
        assertTrue(!context.contains("温柔地一步步梳理"))
        assertTrue(context.contains("参考文献格式"))
    }

    @Test
    fun `select memory recall items returns the exact memories used in context`() {
        val memories = listOf(
            MemoryBankEntity(
                id = 1,
                content = "用户正在写论文大纲，希望露露帮她拆成更小的步骤。",
                memoryKind = "user_preference",
                importance = 5,
                createdAt = 300L,
            ),
            MemoryBankEntity(
                id = 2,
                content = "用户喜欢雨天窝在床上聊天。",
                memoryKind = "user_preference",
                importance = 5,
                createdAt = 200L,
            ),
            MemoryBankEntity(
                id = 3,
                content = "这条旧记忆已经被否认。",
                memoryKind = "role_emotion",
                deprecated = true,
                createdAt = 400L,
            ),
        )

        val selected = selectMemoryRecallItems(
            memories = memories,
            query = "论文大纲卡住了",
            maxItems = 1,
        )

        assertEquals(listOf(1), selected.map { it.id })
    }

    @Test
    fun `select memory recall items uses dynamic top k from best vector similarity`() {
        val vectors = listOf(
            listOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            listOf(0.8f, 0.6f, 0f, 0f, 0f, 0f, 0f, 0f),
            listOf(0.75f, 0f, 0.66f, 0f, 0f, 0f, 0f, 0f),
            listOf(0.7f, 0f, 0f, 0.714f, 0f, 0f, 0f, 0f),
            listOf(0.65f, 0f, 0f, 0f, 0.76f, 0f, 0f, 0f),
            listOf(0.6f, 0f, 0f, 0f, 0f, 0.8f, 0f, 0f),
            listOf(0.55f, 0f, 0f, 0f, 0f, 0f, 0.84f, 0f),
            listOf(0.5f, 0f, 0f, 0f, 0f, 0f, 0f, 0.866f),
        )
        val memories = vectors.mapIndexed { index, vector ->
            MemoryBankEntity(
                id = index + 1,
                content = "memory-${index + 1}",
                memoryKind = "user_preference",
                embeddingVectorJson = encodeMemoryVector(vector),
                createdAt = index.toLong(),
            )
        }

        val selected = selectMemoryRecallItems(
            memories = memories,
            queryVector = listOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        )

        assertEquals(6, selected.size)
    }

    @Test
    fun `select memory recall items keeps eight memories without an embedding model`() {
        val memories = (1..12).map { index ->
            MemoryBankEntity(
                id = index,
                content = "memory-$index",
                memoryKind = "user_preference",
                createdAt = index.toLong(),
            )
        }

        val selected = selectMemoryRecallItems(memories = memories)

        assertEquals(8, selected.size)
    }

    @Test
    fun `select memory recall items expands dynamic top k when vector match is weak`() {
        val vectors = listOf(
            listOf(0.49f, 1.0f, 0.0f),
            listOf(0.49f, 0.0f, 1.0f),
            listOf(0.49f, -1.0f, 0.0f),
            listOf(0.49f, 0.0f, -1.0f),
            listOf(0.49f, 0.7f, 0.7f),
            listOf(0.49f, -0.7f, 0.7f),
            listOf(0.49f, 0.7f, -0.7f),
            listOf(0.49f, -0.7f, -0.7f),
        )
        val memories = vectors.mapIndexed { index, vector ->
            MemoryBankEntity(
                id = index + 1,
                content = "memory-${index + 1}",
                memoryKind = "user_preference",
                embeddingVectorJson = encodeMemoryVector(vector),
                createdAt = index.toLong(),
            )
        }

        val selected = selectMemoryRecallItems(
            memories = memories,
            queryVector = listOf(1f, 0f, 0f),
        )

        assertEquals(8, selected.size)
    }

    @Test
    fun `select memory recall items expands to a related memory`() {
        val memories = listOf(
            MemoryBankEntity(
                id = 1,
                content = "用户正在写论文大纲，希望露露帮她拆成更小的步骤。",
                memoryKind = "user_preference",
                relatedMemoryIdsJson = """["2"]""",
                importance = 5,
                createdAt = 300L,
            ),
            MemoryBankEntity(
                id = 2,
                content = "露露答应过下次继续检查参考文献格式。",
                memoryKind = "promise",
                importance = 1,
                createdAt = 100L,
            ),
            MemoryBankEntity(
                id = 3,
                content = "用户喜欢雨天窝在床上聊天。",
                memoryKind = "user_preference",
                importance = 4,
                createdAt = 200L,
            ),
        )

        val selected = selectMemoryRecallItems(
            memories = memories,
            query = "论文大纲卡住了",
            maxItems = 1,
        )

        assertEquals(listOf(1, 2), selected.map { it.id })
    }

    @Test
    fun `select memory recall items expands through weighted graph memory edges`() {
        val memories = listOf(
            MemoryBankEntity(
                id = 1,
                content = "用户正在写论文大纲，希望露露帮她拆成更小的步骤。",
                memoryKind = "user_preference",
                importance = 5,
                createdAt = 300L,
            ),
            MemoryBankEntity(
                id = 2,
                content = "露露记得她一写论文就容易紧张，先把语气放软。",
                memoryKind = "role_emotion",
                importance = 1,
                createdAt = 100L,
            ),
            MemoryBankEntity(
                id = 3,
                content = "用户喜欢雨天窝在床上聊天。",
                memoryKind = "user_preference",
                importance = 4,
                createdAt = 200L,
            ),
        )

        val selected = selectMemoryRecallItems(
            memories = memories,
            query = "论文大纲卡住了",
            maxItems = 1,
            graphEdges = listOf(
                MemoryGraphEdgeEntity(
                    sourceMemoryId = 1,
                    targetMemoryId = 2,
                    weight = 2.2,
                    coOccurrenceCount = 4,
                    createdAt = 10L,
                    lastReinforcedAt = 20L,
                ),
            ),
            nowMillis = 20L,
        )

        assertEquals(listOf(1, 2), selected.map { it.id })
    }

    @Test
    fun `stale graph edges do not pull unrelated memories back into recall`() {
        val day = 24L * 60L * 60L * 1_000L
        val memories = listOf(
            MemoryBankEntity(id = 1, content = "current topic", importance = 5, createdAt = 300L),
            MemoryBankEntity(id = 2, content = "old association", importance = 1, createdAt = 100L),
        )

        val selected = selectMemoryRecallItems(
            memories = memories,
            query = "current topic",
            maxItems = 1,
            graphEdges = listOf(
                MemoryGraphEdgeEntity(
                    sourceMemoryId = 1,
                    targetMemoryId = 2,
                    weight = 0.2,
                    lastReinforcedAt = 0L,
                ),
            ),
            nowMillis = 180L * day,
        )

        assertEquals(listOf(1), selected.map { it.id })
    }

    @Test
    fun `apply rerank results reorders candidate memories before final selection`() {
        val memories = listOf(
            MemoryBankEntity(id = 1, content = "memory-a", createdAt = 300L),
            MemoryBankEntity(id = 2, content = "memory-b", createdAt = 200L),
            MemoryBankEntity(id = 3, content = "memory-c", createdAt = 100L),
        )

        val reranked = applyMemoryRerankResults(
            memories = memories,
            results = listOf(
                MemoryRerankResult(index = 2, relevanceScore = 0.95),
                MemoryRerankResult(index = 0, relevanceScore = 0.70),
            ),
        )

        assertEquals(listOf(3, 1, 2), reranked.map { it.id })
    }

    @Test
    fun `first memory query selects the earliest relevant memory`() {
        val memories = listOf(
            MemoryBankEntity(id = 1, content = "第一次聊考研时决定先准备英语", createdAt = 100L),
            MemoryBankEntity(id = 2, content = "后来聊考研时开始准备专业课", createdAt = 200L),
            MemoryBankEntity(id = 3, content = "最近聊考研时调整了学习顺序", createdAt = 300L),
        )

        val selected = selectMemoryRecallItems(
            memories = memories,
            query = "我们第一次聊考研是什么时候",
            maxItems = 1,
            nowMillis = 1_000L,
        )

        assertEquals(listOf(1), selected.map { it.id })
    }

    @Test
    fun `rare query terms outweigh common background terms`() {
        val memories = buildList {
            add(MemoryBankEntity(id = 1, content = "考研时约定过蓝莓协议", importance = 2, createdAt = 100L))
            repeat(8) { index ->
                add(
                    MemoryBankEntity(
                        id = index + 2,
                        content = "考研复习安排第${index + 1}次调整",
                        importance = 4,
                        createdAt = 200L + index,
                    ),
                )
            }
        }

        val selected = selectMemoryRecallItems(
            memories = memories,
            query = "考研 蓝莓协议",
            maxItems = 1,
            nowMillis = 1_000L,
        )

        assertEquals(listOf(1), selected.map { it.id })
    }
}
