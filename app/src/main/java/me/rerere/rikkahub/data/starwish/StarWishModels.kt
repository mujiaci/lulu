package me.rerere.rikkahub.data.starwish

import kotlinx.serialization.Serializable

@Serializable
data class StarWishState(
    val customOutfitPrompts: Map<String, StarWishOutfitPrompts> = emptyMap(),
    val imageLaunches: List<StarWishImageLaunch> = emptyList(),
    val theaterChapters: Map<String, List<StarWishTheaterChapter>> = emptyMap(),
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
    val createdAt: Long,
)

data class StarWishGeneratedImage(
    val id: Int,
    val outfit: String,
    val filePath: String,
    val prompt: String,
    val createdAt: Long,
)
