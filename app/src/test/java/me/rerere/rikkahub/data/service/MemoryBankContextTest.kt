package me.rerere.rikkahub.data.service

import me.rerere.rikkahub.data.db.entity.MemoryBankEntity
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
    fun `build memory context groups legacy memory types for natural recall`() {
        val memories = listOf(
            MemoryBankEntity(content = "今天上午她说论文写不下去了", type = "phase_summary", createdAt = 300L),
            MemoryBankEntity(content = "她最近在准备考研", type = "daily_summary", createdAt = 200L),
            MemoryBankEntity(content = "她不喜欢太硬的打卡提醒", type = "manual", createdAt = 100L),
        )

        val context = buildMemoryRecallContext(memories)

        assertTrue(context.contains("<lulu_memory>"))
        assertTrue(context.contains("当前相关回忆"))
        assertTrue(context.contains("长期印象"))
        assertTrue(context.contains("她不喜欢太硬的打卡提醒"))
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
}
