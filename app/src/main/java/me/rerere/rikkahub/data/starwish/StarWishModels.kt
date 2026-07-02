package me.rerere.rikkahub.data.starwish

import kotlinx.serialization.Serializable

@Serializable
data class StarWishState(
    val customOutfitPrompts: Map<String, StarWishOutfitPrompts> = emptyMap(),
    val imageLaunches: List<StarWishImageLaunch> = emptyList(),
    val theaterChapters: Map<String, List<StarWishTheaterChapter>> = emptyMap(),
    val specialStoryChapters: Map<String, List<StarWishTheaterChapter>> = emptyMap(),
    val customTheaters: List<StarWishTheaterSeed> = emptyList(),
    val customSpecialStories: List<StarWishTheaterSeed> = emptyList(),
    val hiddenScrollTitles: Set<String> = emptySet(),
    val hiddenTheaterTitles: Set<String> = emptySet(),
    val hiddenSpecialStoryTitles: Set<String> = emptySet(),
    val hiddenImageLaunchIds: Set<String> = emptySet(),
    val hiddenGeneratedImageIds: Set<Int> = emptySet(),
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

data class StarWishGeneratedImage(
    val id: Int,
    val outfit: String,
    val filePath: String,
    val prompt: String,
    val createdAt: Long,
)
