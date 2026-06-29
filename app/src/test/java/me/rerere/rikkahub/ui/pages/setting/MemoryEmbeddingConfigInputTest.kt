package me.rerere.rikkahub.ui.pages.setting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryEmbeddingConfigInputTest {
    @Test
    fun parseMemoryEmbeddingDimensionsInputAllowsBlankOrPositiveInteger() {
        assertNull(parseMemoryEmbeddingDimensionsInput(""))
        assertNull(parseMemoryEmbeddingDimensionsInput("  "))
        assertNull(parseMemoryEmbeddingDimensionsInput("0"))
        assertNull(parseMemoryEmbeddingDimensionsInput("-12"))
        assertEquals(1536, parseMemoryEmbeddingDimensionsInput("1536"))
    }

    @Test
    fun parseMemoryEmbeddingBatchSizeInputClampsToServiceRange() {
        assertEquals(1, parseMemoryEmbeddingBatchSizeInput(""))
        assertEquals(1, parseMemoryEmbeddingBatchSizeInput("0"))
        assertEquals(12, parseMemoryEmbeddingBatchSizeInput("12"))
        assertEquals(64, parseMemoryEmbeddingBatchSizeInput("200"))
    }

    @Test
    fun parseMemoryRerankCandidateCountInputClampsToServiceRange() {
        assertEquals(5, parseMemoryRerankCandidateCountInput(""))
        assertEquals(5, parseMemoryRerankCandidateCountInput("0"))
        assertEquals(12, parseMemoryRerankCandidateCountInput("12"))
        assertEquals(50, parseMemoryRerankCandidateCountInput("200"))
    }

    @Test
    fun buildMemoryEngineDiagnosticsSummarizesLocalPipelineStatus() {
        val lines = buildMemoryEngineDiagnostics(
            enabled = true,
            embeddingModel = "Qwen/Qwen3-Embedding-8B",
            rerankModel = "Qwen/Qwen3-Reranker-8B",
            extractionModel = "gpt-4o-mini",
            candidateCount = 20,
        )

        assertTrue(lines.any { it.contains("本地向量库") })
        assertTrue(lines.any { it.contains("Qwen/Qwen3-Embedding-8B") })
        assertTrue(lines.any { it.contains("Qwen/Qwen3-Reranker-8B") })
        assertTrue(lines.any { it.contains("gpt-4o-mini") })
    }

    @Test
    fun defaultModelSettingSectionsExposeMemoryAndImageEntrancesWithoutTitleSummary() {
        val sections = defaultModelSettingSections()

        assertTrue(ModelSettingSection.MEMORY_RERANK in sections)
        assertTrue(ModelSettingSection.MEMORY_EXTRACTION in sections)
        assertTrue(ModelSettingSection.IMAGE_GENERATION in sections)
        assertTrue(ModelSettingSection.TITLE_SUMMARY !in sections)
    }
}
