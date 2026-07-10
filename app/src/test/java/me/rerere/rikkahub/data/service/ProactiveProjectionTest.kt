package me.rerere.rikkahub.data.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProactiveProjectionTest {
    @Test
    fun `only the cleared assistant owns its proactive projection`() {
        assertTrue(shouldResetProactiveProjection("assistant-a", "assistant-a"))
        assertFalse(shouldResetProactiveProjection("assistant-b", "assistant-a"))
        assertFalse(shouldResetProactiveProjection(null, "assistant-a"))
        assertFalse(shouldResetProactiveProjection("assistant-a", ""))
    }
}
