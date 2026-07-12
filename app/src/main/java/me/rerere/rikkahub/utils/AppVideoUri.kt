package me.rerere.rikkahub.utils

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import me.rerere.rikkahub.R

fun resolveAppVideoUri(context: Context, uri: String): Uri {
    if (!uri.startsWith(RAW_VIDEO_PREFIX)) return uri.toUri()
    val rawName = uri.removePrefix(RAW_VIDEO_PREFIX).substringBeforeLast('.')
    val knownRawId = when (rawName) {
        "star_wish_rainbow_draw" -> R.raw.star_wish_rainbow_draw
        "star_wish_epic_draw" -> R.raw.star_wish_epic_draw
        "star_wish_rare_draw" -> R.raw.star_wish_rare_draw
        else -> 0
    }
    if (knownRawId != 0) return rawResourceUri(context, knownRawId)
    val rawId = context.resources.getIdentifier(rawName, "raw", context.packageName)
    if (rawId != 0) return rawResourceUri(context, rawId)
    return "android.resource://${context.packageName}/raw/$rawName".toUri()
}

private fun rawResourceUri(context: Context, rawId: Int): Uri =
    "android.resource://${context.packageName}/$rawId".toUri()

private const val RAW_VIDEO_PREFIX = "raw:"
