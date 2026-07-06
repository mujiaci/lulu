package me.rerere.rikkahub.ui.pages.cihai

import me.rerere.rikkahub.data.cihai.CihaiEntryKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CihaiPageEntryKindsTest {
    @Test
    fun `cihai page exposes first person action and memory sediment entries`() {
        val kinds = visibleCihaiEntryKinds()

        assertTrue(CihaiEntryKind.INNER_JOURNAL in kinds)
        assertTrue(CihaiEntryKind.ACTION_LOG in kinds)
        assertTrue(CihaiEntryKind.REFLECTION in kinds)
        assertFalse(CihaiEntryKind.READING_NOTE in kinds)
    }
}
