package me.rerere.rikkahub.ui.pages.cihai

import me.rerere.rikkahub.data.cihai.CihaiEntryKind
import me.rerere.rikkahub.data.cihai.CihaiEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class CihaiPageEntryKindsTest {
    @Test
    fun `cihai page exposes five distinct user facing sections`() {
        val sections = visibleCihaiSections()

        assertEquals(
            listOf("生活", "挂心", "约定", "关系", "日记"),
            sections.map { it.label },
        )
        assertEquals(null, CihaiSection.CONCERNS.entryKind)
        assertEquals(null, CihaiSection.RESPONSIBILITIES.entryKind)
        assertEquals(null, CihaiSection.RELATIONSHIP.entryKind)
        assertEquals(null, CihaiSection.LIFE.entryKind)
        assertEquals(CihaiEntryKind.DIARY, CihaiSection.DIARY.entryKind)
        assertFalse(sections.any { it.entryKind == CihaiEntryKind.INNER_JOURNAL })
        assertFalse(sections.any { it.entryKind == CihaiEntryKind.ACTION_LOG })
        assertFalse(sections.any { it.entryKind == CihaiEntryKind.READING_NOTE })
        assertFalse(sections.any { it.entryKind == CihaiEntryKind.REFLECTION })
    }

    @Test
    fun `cihai sections filter different entry kinds instead of showing the same list`() {
        val entries = listOf(
            entry("diary", "lulu", CihaiEntryKind.DIARY),
            entry("journal", "lulu", CihaiEntryKind.INNER_JOURNAL),
            entry("action", "lulu", CihaiEntryKind.ACTION_LOG),
            entry("reflection", "lulu", CihaiEntryKind.REFLECTION),
            entry("reading", "lulu", CihaiEntryKind.READING_NOTE),
            entry("other", "other", CihaiEntryKind.INNER_JOURNAL),
        )

        assertEquals(
            listOf("diary"),
            entriesForCihaiSection(entries, "lulu", CihaiSection.DIARY).map { it.id },
        )
        assertEquals(
            emptyList<String>(),
            entriesForCihaiSection(entries, "lulu", CihaiSection.CONCERNS).map { it.id },
        )
    }

    private fun entry(id: String, assistantId: String, kind: CihaiEntryKind): CihaiEntry =
        CihaiEntry(
            id = id,
            assistantId = assistantId,
            kind = kind,
            title = id,
            content = id,
        )
    }
