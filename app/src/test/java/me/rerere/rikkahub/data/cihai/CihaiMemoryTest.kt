package me.rerere.rikkahub.data.cihai

import org.junit.Assert.assertEquals
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
    fun `journal entry becomes pending vector memory candidate`() {
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

        assertEquals("cihai_journal", memory.memoryKind)
        assertEquals("pending", memory.vectorStatus)
        assertTrue(memory.embeddingText!!.contains("担心但克制"))
        assertTrue(memory.tagsJson!!.contains("辞海"))
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
    fun `silent judgement creates journal entry for cihai memory`() {
        val entry = CihaiEntry.fromSilentJudgment(
            assistantId = "lulu",
            assistantName = "露露",
            reason = "滚动判断：用户已经 25 分钟没有回复，先观察，不机械追问。",
            userText = "我先去处理点事",
            createdAt = 1_700_000_000_000L,
        )

        assertEquals(CihaiEntryKind.INNER_JOURNAL, entry.kind)
        assertTrue(entry.title.contains("露露"))
        assertTrue(entry.content.contains("不机械追问"))
        assertTrue(entry.toMemoryCandidate().embeddingText!!.contains("滚动判断"))
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
    fun `silent presence action can read a user book while not disturbing`() {
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
                actionHintNames = listOf("WRITE_JOURNAL", "READ_BOOK"),
                books = listOf(book),
                createdAt = 1_700_000_000_000L,
            )
        )

        assertEquals(
            listOf(CihaiEntryKind.INNER_JOURNAL, CihaiEntryKind.ACTION_LOG, CihaiEntryKind.READING_NOTE),
            result.entries.map { it.kind },
        )
        assertTrue(result.entries[1].content.contains("读"))
        assertTrue(result.entries[2].sourceTitle!!.contains("陪伴方法"))
        assertTrue(result.updatedBook!!.progressPercent > book.progressPercent)
        assertTrue(result.entries.all { it.toMemoryCandidate().type.startsWith("cihai_") })
    }

    @Test
    fun `silent presence action still records action log when no book is available`() {
        val result = planCihaiSilentPresence(
            CihaiSilentPresenceInput(
                assistantId = "lulu",
                assistantName = "露露",
                reason = "主动判断后决定不打扰。",
                userText = "",
                actionHintNames = listOf("WRITE_JOURNAL", "READ_BOOK"),
                books = emptyList(),
                createdAt = 1_700_000_000_000L,
            )
        )

        assertEquals(
            listOf(CihaiEntryKind.INNER_JOURNAL, CihaiEntryKind.ACTION_LOG),
            result.entries.map { it.kind },
        )
        assertEquals(null, result.updatedBook)
        assertTrue(result.entries[1].content.contains("没有可读材料"))
    }

    @Test
    fun `silent presence action can create reflection for next rolling judgement`() {
        val result = planCihaiSilentPresence(
            CihaiSilentPresenceInput(
                assistantId = "lulu",
                assistantName = "露露",
                reason = "事件进入多次判断系统，本轮选择等待和整理记忆。",
                userText = "我先忙三个小时",
                actionHintNames = listOf("WRITE_JOURNAL", "MEMORY_REFLECT"),
                books = emptyList(),
                createdAt = 1_700_000_000_000L,
            )
        )

        assertEquals(
            listOf(CihaiEntryKind.INNER_JOURNAL, CihaiEntryKind.ACTION_LOG, CihaiEntryKind.REFLECTION),
            result.entries.map { it.kind },
        )
        assertTrue(result.entries.last().content.contains("下一轮判断"))
        assertTrue(result.entries.last().toMemoryCandidate().type == "cihai_reflection")
    }
}
