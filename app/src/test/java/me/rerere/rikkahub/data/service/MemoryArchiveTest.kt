package me.rerere.rikkahub.data.service

import me.rerere.rikkahub.data.db.entity.MemoryBankEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MemoryArchiveTest {
    private val zone = ZoneId.of("UTC")

    @Test
    fun `closed day memories become one evidence linked daily archive`() {
        val day = LocalDate.of(2026, 7, 10)
        val memories = listOf(
            memory(1, "她明确说不喜欢空话", day, 9),
            memory(2, "一起调整了考研计划", day, 20),
        )
        val now = LocalDate.of(2026, 7, 12).atStartOfDay(zone).toInstant().toEpochMilli()

        val archives = buildMissingDailyMemoryArchives(memories, nowMillis = now, zoneId = zone)

        assertEquals(1, archives.size)
        assertEquals("daily_summary", archives.single().type)
        assertEquals("daily_archive", archives.single().memoryKind)
        assertEquals("2026-07-10", archives.single().dateGroup)
        assertTrue(archives.single().content.contains("她明确说不喜欢空话"))
        assertTrue(archives.single().relatedMemoryIdsJson.orEmpty().contains("1"))
    }

    @Test
    fun `daily archives from a closed month become one monthly archive`() {
        val memories = listOf(
            dailyArchive(11, "2026-06-03", "记住了一个重要边界"),
            dailyArchive(12, "2026-06-18", "完成了一次关系修复"),
        )
        val now = LocalDate.of(2026, 7, 12).atStartOfDay(zone).toInstant().toEpochMilli()

        val archives = buildMissingMonthlyMemoryArchives(memories, nowMillis = now, zoneId = zone)

        assertEquals(1, archives.size)
        assertEquals("phase_summary", archives.single().type)
        assertEquals("monthly_archive", archives.single().memoryKind)
        assertEquals("2026-06", archives.single().dateGroup)
        assertTrue(archives.single().content.contains("重要边界"))
        assertTrue(archives.single().content.contains("关系修复"))
    }

    private fun memory(id: Int, content: String, date: LocalDate, hour: Int) = MemoryBankEntity(
        id = id,
        assistantId = "assistant-a",
        content = content,
        memoryKind = "shared_event",
        importance = 4,
        createdAt = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
    )

    private fun dailyArchive(id: Int, date: String, content: String) = MemoryBankEntity(
        id = id,
        assistantId = "assistant-a",
        content = content,
        type = "daily_summary",
        memoryKind = "daily_archive",
        importance = 4,
        dateGroup = date,
        createdAt = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli(),
    )
}
