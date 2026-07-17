package me.rerere.rikkahub.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryVectorTest {
    @Test
    fun `cosine similarity prefers aligned vectors`() {
        val query = listOf(1f, 0f, 0f)

        val aligned = cosineSimilarity(query, listOf(0.9f, 0.1f, 0f))
        val unrelated = cosineSimilarity(query, listOf(0f, 1f, 0f))

        assertTrue(aligned > unrelated)
        assertEquals(0.0, unrelated, 0.0)
    }

    @Test
    fun `cosine similarity returns zero for mismatched or empty vectors`() {
        assertEquals(0.0, cosineSimilarity(emptyList(), listOf(1f)), 0.0)
        assertEquals(0.0, cosineSimilarity(listOf(1f), listOf(1f, 0f)), 0.0)
        assertEquals(0.0, cosineSimilarity(listOf(0f, 0f), listOf(1f, 0f)), 0.0)
    }
}
