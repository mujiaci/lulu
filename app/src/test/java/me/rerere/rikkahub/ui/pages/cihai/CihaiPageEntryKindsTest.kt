package me.rerere.rikkahub.ui.pages.cihai

import me.rerere.rikkahub.data.cihai.CihaiEntryKind
import me.rerere.rikkahub.data.cihai.CihaiEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CihaiPageEntryKindsTest {
    @Test
    fun `cihai page exposes distinct concern journal action and memory sections`() {
        val sections = visibleCihaiSections()

        assertEquals(
            listOf("挂心", "心迹", "行动", "沉淀"),
            sections.map { it.label },
        )
        assertEquals(null, CihaiSection.CONCERNS.entryKind)
        assertEquals(CihaiEntryKind.INNER_JOURNAL, CihaiSection.INNER_JOURNAL.entryKind)
        assertEquals(CihaiEntryKind.ACTION_LOG, CihaiSection.ACTION_LOG.entryKind)
        assertEquals(CihaiEntryKind.REFLECTION, CihaiSection.REFLECTION.entryKind)
        assertFalse(sections.any { it.entryKind == CihaiEntryKind.READING_NOTE })
    }

    @Test
    fun `cihai sections filter different entry kinds instead of showing the same list`() {
        val entries = listOf(
            entry("journal", "lulu", CihaiEntryKind.INNER_JOURNAL),
            entry("action", "lulu", CihaiEntryKind.ACTION_LOG),
            entry("reflection", "lulu", CihaiEntryKind.REFLECTION),
            entry("reading", "lulu", CihaiEntryKind.READING_NOTE),
            entry("other", "other", CihaiEntryKind.INNER_JOURNAL),
        )

        assertEquals(
            listOf("journal"),
            entriesForCihaiSection(entries, "lulu", CihaiSection.INNER_JOURNAL).map { it.id },
        )
        assertEquals(
            listOf("action"),
            entriesForCihaiSection(entries, "lulu", CihaiSection.ACTION_LOG).map { it.id },
        )
        assertEquals(
            listOf("reflection"),
            entriesForCihaiSection(entries, "lulu", CihaiSection.REFLECTION).map { it.id },
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
}
