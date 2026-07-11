package me.rerere.rikkahub.data.starwish

import me.rerere.rikkahub.data.study.StudyInventory
import me.rerere.rikkahub.data.study.StudyState
import kotlin.random.Random
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

    @Test
    fun drawOpeningAnimationsAreNotExposedAsStarWishVideos() {
        val openingUris = setOf(
            "raw:star_wish_rainbow_draw",
            "raw:star_wish_epic_draw",
            "raw:star_wish_rare_draw",
        )

        assertTrue(StarWishRules.builtInVideos.none { video ->
            video.uri in openingUris
        })
    }

    @Test
    fun videoUnlockChoosesRandomLockedVideoAndUnlocksIt() {
        val starWishState = StarWishState(
            customVideos = listOf(video("a"), video("b")),
        )
        val studyState = StudyState(inventory = StudyInventory(videoFragments = 1))

        val unlock = StarWishRules.unlockNextVideo(starWishState, studyState, FixedIntRandom(1))

        assertEquals("b", unlock.video?.id)
        assertEquals(setOf("b"), unlock.starWishState.unlockedVideoIds)
        assertEquals(0, unlock.studyState.inventory.videoFragments)
        assertTrue(unlock.consumedFragment)
    }

    @Test
    fun videoUnlockPrioritizesRemainingLockedVideoBeforeRandomUnlockedReplay() {
        val starWishState = StarWishState(
            customVideos = listOf(video("a"), video("b")),
            unlockedVideoIds = setOf("a"),
        )
        val studyState = StudyState(inventory = StudyInventory(videoFragments = 1))

        val unlock = StarWishRules.unlockNextVideo(starWishState, studyState, FixedIntRandom(0))

        assertEquals("b", unlock.video?.id)
        assertEquals(setOf("a", "b"), unlock.starWishState.unlockedVideoIds)
        assertEquals(0, unlock.studyState.inventory.videoFragments)
        assertTrue(unlock.consumedFragment)
    }

    @Test
    fun videoUnlockReplaysRandomVideoWhenAllVideosAreUnlocked() {
        val starWishState = StarWishState(
            customVideos = listOf(video("a"), video("b")),
            unlockedVideoIds = setOf("a", "b"),
        )
        val studyState = StudyState(inventory = StudyInventory(videoFragments = 0))

        val replay = StarWishRules.unlockNextVideo(starWishState, studyState, FixedIntRandom(1))

        assertEquals("b", replay.video?.id)
        assertEquals(starWishState.unlockedVideoIds, replay.starWishState.unlockedVideoIds)
        assertEquals(0, replay.studyState.inventory.videoFragments)
        assertTrue(!replay.consumedFragment)
    }

    @Test
    fun theaterGuideKeepsAdditionalChaptersBeyondSix() {
        val guide = StarWishTheaterGuide(
            chapters = (1..8).map { "chapter-$it" },
        ).normalized()

        assertEquals(8, guide.chapters.size)
        assertEquals("chapter-8", guide.chapters.last())
    }

    @Test
    fun theaterChapterPromptIncludesPreviousChapterBodyAndRoleRules() {
        val seed = StarWishTheaterSeed(
            id = "test",
            title = "测试小剧场",
            prompt = "总剧情",
        )
        val previous = StarWishTheaterChapter(
            id = "chapter-1",
            theater = seed.title,
            chapter = 1,
            title = "第一章",
            content = "上一章完整正文，需要被下一章承接。",
            createdAt = 1L,
        )

        val prompt = StarWishRules.theaterChapterPrompt(
            seed = seed,
            previousChapters = listOf(previous),
            chapter = 2,
            guide = StarWishTheaterGuide(chapters = (1..8).map { "chapter-$it" }),
        )

        assertTrue(prompt.contains("8 章"))
        assertTrue(prompt.contains("上一章完整正文，需要被下一章承接。"))
        assertTrue(prompt.contains("女主就是用户本人"))
        assertTrue(prompt.contains("名字含露"))
    }

    private fun assertPromptComplete(prompt: String, label: String) {
        listOf("服装", "饰品", "背景", "姿势", "表情", "光影", "画风", "画质").forEach { phrase ->
            assertTrue(prompt.contains(phrase), "$label should include $phrase")
        }
        assertTrue(prompt.contains("露露"), "$label should name Lulu")
        assertTrue(prompt.contains("男生") || prompt.contains("恋人"), "$label should preserve Lulu as a male companion")
    }

    private fun video(id: String) = StarWishVideoItem(
        id = id,
        title = id,
        uri = "file:///$id.mp4",
    )

    private class FixedIntRandom(private val value: Int) : Random() {
        override fun nextBits(bitCount: Int): Int = value
        override fun nextInt(until: Int): Int = value % until
    }
}
