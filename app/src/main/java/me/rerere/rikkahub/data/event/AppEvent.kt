package me.rerere.rikkahub.data.event

sealed class AppEvent {
    data class Speak(val text: String) : AppEvent()
    data class EmojiSelected(val emoji: String) : AppEvent()
}
