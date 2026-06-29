package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.runtime.Composable
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation

@Composable
fun TTSAutoPlay(vm: ChatVM, setting: Settings, conversation: Conversation) {
    // Keep this composable as a no-op so old auto-play settings cannot race the manual play button.
    Unit
}
