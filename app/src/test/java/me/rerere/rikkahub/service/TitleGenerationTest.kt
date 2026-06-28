package me.rerere.rikkahub.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleGenerationTest {
    @Test
    fun `shouldAutoGenerateTitle should not generate title for blank titles`() {
        assertFalse(shouldGenerateTitle(title = "", force = false))
    }

    @Test
    fun `shouldGenerateTitle should allow forced title generation`() {
        assertTrue(shouldGenerateTitle(title = "Existing title", force = true))
    }
}
