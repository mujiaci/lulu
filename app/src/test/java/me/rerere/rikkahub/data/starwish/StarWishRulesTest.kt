package me.rerere.rikkahub.data.starwish

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StarWishRulesTest {
    @Test
    fun builtInScrollsExposeTwentyCompleteSoloAndInteractionPrompts() {
        assertEquals(20, StarWishRules.scrolls.size)

        StarWishRules.scrolls.forEach { scroll ->
            assertTrue(scroll.title.isNotBlank(), "Scroll title should not be blank")
            assertPromptComplete(scroll.soloPrompt, "solo prompt for ${scroll.title}")
            assertPromptComplete(scroll.interactionPrompt, "interaction prompt for ${scroll.title}")
        }

        val interactionCatalog = StarWishRules.scrolls.joinToString("\n") { it.interactionPrompt }
        listOf("第一视角", "第三视角", "壁咚", "坐在露露腿上", "摸我的头", "捏我的脸", "托我的下巴").forEach { phrase ->
            assertTrue(
                interactionCatalog.contains(phrase),
                "Interaction prompt catalog should include $phrase",
            )
        }
    }

    private fun assertPromptComplete(prompt: String, label: String) {
        listOf("服装", "饰品", "背景", "姿势", "表情", "光影", "画风", "画质").forEach { phrase ->
            assertTrue(prompt.contains(phrase), "$label should include $phrase")
        }
        assertTrue(prompt.contains("露露"), "$label should name Lulu")
        assertTrue(prompt.contains("男生") || prompt.contains("恋人"), "$label should preserve Lulu as a male companion")
    }
}
