package me.rerere.rikkahub.data.starwish

import kotlinx.serialization.Serializable

@Serializable
data class StarWishState(
    val customOutfitPrompts: Map<String, StarWishOutfitPrompts> = emptyMap(),
    val imageLaunches: List<StarWishImageLaunch> = emptyList(),
    val theaterChapters: Map<String, List<StarWishTheaterChapter>> = emptyMap(),
    val theaterGuides: Map<String, StarWishTheaterGuide> = emptyMap(),
    val customTheaters: List<StarWishTheaterSeed> = emptyList(),
    val hiddenScrollTitles: Set<String> = emptySet(),
    val hiddenTheaterTitles: Set<String> = emptySet(),
    val hiddenImageLaunchIds: Set<String> = emptySet(),
    val hiddenGeneratedImageIds: Set<Int> = emptySet(),
    val customVideos: List<StarWishVideoItem> = emptyList(),
    val unlockedVideoIds: Set<String> = emptySet(),
    val hiddenVideoIds: Set<String> = emptySet(),
    val lastSection: String = "Scrolls",
)

@Serializable
data class StarWishOutfitPrompts(
    val solo: String,
    val interaction: String,
)

@Serializable
data class StarWishImageLaunch(
    val id: String,
    val outfit: String,
    val prompt: String,
    val createdAt: Long,
)

@Serializable
data class StarWishTheaterChapter(
    val id: String,
    val theater: String,
    val chapter: Int,
    val title: String,
    val content: String,
    val userInfluence: String = "",
    val createdAt: Long,
)

@Serializable
data class StarWishTheaterSeed(
    val id: String,
    val title: String,
    val prompt: String,
    val createdAt: Long = 0L,
)

@Serializable
data class StarWishTheaterGuide(
    val overview: String = "",
    val chapters: List<String> = List(6) { "" },
    val wordCount: String = "1200-2200",
) {
    fun normalized(): StarWishTheaterGuide = copy(
        overview = overview.trim(),
        chapters = chapters.ifEmpty { List(6) { "" } }.map { it.trim() },
        wordCount = wordCount.trim().ifBlank { "1200-2200" },
    )
}

@Serializable
data class StarWishVideoItem(
    val id: String,
    val title: String,
    val uri: String,
    val builtIn: Boolean = false,
    val createdAt: Long = 0L,
)

data class StarWishGeneratedImage(
    val id: Int,
    val outfit: String,
    val filePath: String,
    val prompt: String,
    val createdAt: Long,
    val fromStarWish: Boolean = true,
)

data class StarWishVideoUnlockResult(
    val starWishState: StarWishState,
    val studyState: me.rerere.rikkahub.data.study.StudyState,
    val video: StarWishVideoItem?,
    val consumedFragment: Boolean,
)
