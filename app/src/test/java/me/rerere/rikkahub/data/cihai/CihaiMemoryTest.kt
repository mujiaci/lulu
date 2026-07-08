package me.rerere.rikkahub.data.cihai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CihaiMemoryTest {
    @Test
    fun `cihai memory context keeps recent and unsummarized entries with sixty entry threshold`() {
        val entries = (1..75).map { index ->
            CihaiEntry(
                assistantId = "lulu",
                kind = CihaiEntryKind.INNER_JOURNAL,
                title = "entry-$index",
                content = "content-$index",
                createdAt = index.toLong(),
                memorySaved = index <= 10,
            )
        }

        val context = buildCihaiMemoryContext(entries)

        assertEquals(60, context.recentEntries.size)
        assertEquals("entry-16", context.recentEntries.first().title)
        assertEquals(60, context.unsummarizedEntries.size)
        assertEquals("entry-11", context.unsummarizedEntries.first().title)
        assertTrue(context.shouldSummarize)
    }

    @Test
    fun `cihai inner entry becomes pending vector memory candidate without diary kind`() {
        val entry = CihaiEntry(
            assistantId = "lulu",
            kind = CihaiEntryKind.INNER_JOURNAL,
            title = "她很久没回我",
            content = "我想再问一句，但她可能正在忙，我先写下来，晚点再判断。",
            emotion = "担心但克制",
            createdAt = 1_700_000_000_000L,
        )

        val memory = entry.toMemoryCandidate().toEntity(
            assistantId = "lulu",
            conversationId = null,
            createdAt = entry.createdAt,
        )
        val candidate = entry.toMemoryCandidate()

        assertEquals("cihai_inner", memory.memoryKind)
        assertEquals("pending", memory.vectorStatus)
        assertTrue(memory.embeddingText!!.contains("担心但克制"))
        assertTrue(memory.tagsJson!!.contains("辞海"))
        assertTrue(candidate.relationshipEffect!!.startsWith("我在用户沉默时"))
        assertTrue(candidate.embeddingText!!.contains("我应该参考这次判断"))
        assertTrue(!candidate.relationshipEffect!!.contains("角色在用户"))
    }

    @Test
    fun `cihai diary entry uses diary memory kind and first person relationship effect`() {
        val entry = CihaiEntry(
            assistantId = "lulu",
            kind = CihaiEntryKind.DIARY,
            title = "露露的日记",
            content = "我其实还是有点惦记她，看到她回消息以后，心里松了一下，但还是想晚点再确认她没有睡回去。",
            emotion = "松一口气、继续惦记",
            createdAt = 1_700_000_000_000L,
        )

        val candidate = entry.toMemoryCandidate()

        assertEquals("cihai_diary", candidate.type)
        assertTrue(candidate.relationshipEffect!!.startsWith("我用日记"))
        assertEquals(entry.content, candidate.unspokenThought)
        assertTrue(candidate.tags.contains("日记"))
    }

    @Test
    fun `reading note keeps source book and reflection in memory text`() {
        val entry = CihaiEntry(
            assistantId = "lulu",
            kind = CihaiEntryKind.READING_NOTE,
            title = "读《亲密关系》第三章",
            content = "看到压力下的人会先冻结，我更明白她卡住时不该只催。",
            sourceTitle = "亲密关系",
            createdAt = 1_700_000_000_000L,
        )

        val candidate = entry.toMemoryCandidate()

        assertEquals("cihai_reading", candidate.type)
        assertTrue(candidate.content.contains("亲密关系"))
        assertTrue(candidate.embeddingText!!.contains("不该只催"))
        assertTrue(candidate.tags.contains("阅读"))
    }

    @Test
    fun `silent judgement creates cihai inner entry without writing diary content`() {
        val entry = CihaiEntry.fromSilentJudgment(
            assistantId = "lulu",
            assistantName = "露露",
            reason = "感知层：用户说自己明天早上考试，最近上下文里压力偏高。意义评估层：考试对学生很重要，错过会有严重后果。判断层：今晚先确认她有没有睡下，明早再重新感知是否醒来。",
            userText = "我明天早上10点要起床考试",
            createdAt = 1_700_000_000_000L,
        )

        assertEquals(CihaiEntryKind.INNER_JOURNAL, entry.kind)
        assertTrue(entry.title.contains("露露"))
        assertTrue(entry.content.length in 100..500)
        assertTrue(entry.content.startsWith("我"))
        assertFalse(entry.content.contains("用户刚才说过："))
        assertTrue(entry.content.contains("感知"))
        assertTrue(entry.content.contains("评估"))
        assertTrue(entry.content.contains("判断"))
        assertTrue(entry.content.contains("考试"))
        assertTrue(entry.toMemoryCandidate().embeddingText!!.contains("明早"))
    }

    @Test
    fun `book creates reading reflection entry and advances progress`() {
        val book = CihaiBook(
            assistantId = "lulu",
            title = "亲密关系",
            content = "第一段。".repeat(80),
            progressPercent = 0,
        )

        val result = book.readNextReflection(nowMillis = 1_700_000_000_000L)

        assertTrue(result.entry.title.contains("亲密关系"))
        assertEquals(CihaiEntryKind.READING_NOTE, result.entry.kind)
        assertTrue(result.entry.content.contains("我读到"))
        assertTrue(result.updatedBook.progressPercent > book.progressPercent)
        assertEquals(1_700_000_000_000L, result.updatedBook.lastReadAt)
    }

    @Test
    fun `silent presence planner can read a user book without creating formal diary`() {
        val book = CihaiBook(
            assistantId = "lulu",
            title = "陪伴方法",
            content = "人在忙的时候，陪伴者要降低打扰强度，保留观察和温柔的后续判断。".repeat(40),
            progressPercent = 0,
        )

        val result = planCihaiSilentPresence(
            CihaiSilentPresenceInput(
                assistantId = "lulu",
                assistantName = "露露",
                reason = "用户已经 25 分钟没有回复，当前没有危险信号，先不打扰。",
                userText = "我先忙一下",
                actionHintNames = listOf("WRITE_DIARY", "READ_BOOK"),
                books = listOf(book),
                createdAt = 1_700_000_000_000L,
            )
        )

        assertEquals(
            listOf(CihaiEntryKind.INNER_JOURNAL, CihaiEntryKind.READING_NOTE),
            result.entries.map { it.kind },
        )
        assertTrue(result.entries.none { it.kind == CihaiEntryKind.DIARY })
        assertTrue(result.entries[1].sourceTitle!!.contains("陪伴方法"))
        assertTrue(result.updatedBook!!.progressPercent > book.progressPercent)
        assertTrue(result.entries.all { it.toMemoryCandidate().type.startsWith("cihai_") })
    }

    @Test
    fun `silent presence journal hints never create formal diary entries`() {
        val result = planCihaiSilentPresence(
            CihaiSilentPresenceInput(
                assistantId = "lulu",
                assistantName = "露露",
                reason = "后台感知选择 PASS/WAIT，只更新内部心迹，不写正式日记。",
                userText = "我先忙一会儿",
                actionHintNames = listOf("WRITE_DIARY", "WRITE_JOURNAL", "MEMORY_REFLECT"),
                books = emptyList(),
                createdAt = 1_700_000_000_000L,
            )
        )

        assertFalse(result.entries.any { it.kind == CihaiEntryKind.DIARY })
        assertEquals(
            listOf(CihaiEntryKind.INNER_JOURNAL, CihaiEntryKind.REFLECTION),
            result.entries.map { it.kind },
        )
        assertTrue(result.entries.first().content.contains("不写正式日记"))
    }

    @Test
    fun `silent presence records inner journal instead of formal diary when no book is available`() {
        val result = planCihaiSilentPresence(
            CihaiSilentPresenceInput(
                assistantId = "lulu",
                assistantName = "露露",
                reason = "主动判断后决定不打扰。",
                userText = "",
                actionHintNames = listOf("WRITE_DIARY", "READ_BOOK"),
                books = emptyList(),
                createdAt = 1_700_000_000_000L,
            )
        )

        assertEquals(
            listOf(CihaiEntryKind.INNER_JOURNAL),
            result.entries.map { it.kind },
        )
        assertTrue(result.entries.none { it.kind == CihaiEntryKind.DIARY })
        assertEquals(null, result.updatedBook)
    }

    @Test
    fun `silent presence action can create reflection for next rolling judgement`() {
        val result = planCihaiSilentPresence(
            CihaiSilentPresenceInput(
                assistantId = "lulu",
                assistantName = "露露",
                reason = "事件进入多次判断系统，本轮选择等待和整理记忆。",
                userText = "我先忙三个小时",
                actionHintNames = listOf("WRITE_DIARY", "MEMORY_REFLECT"),
                books = emptyList(),
                createdAt = 1_700_000_000_000L,
            )
        )

        assertEquals(
            listOf(CihaiEntryKind.INNER_JOURNAL, CihaiEntryKind.REFLECTION),
            result.entries.map { it.kind },
        )
        assertTrue(result.entries.last().content.contains("下一轮判断"))
        assertTrue(result.entries.last().toMemoryCandidate().type == "cihai_reflection")
    }
}
