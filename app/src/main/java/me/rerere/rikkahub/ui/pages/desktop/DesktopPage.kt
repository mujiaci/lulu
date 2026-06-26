package me.rerere.rikkahub.ui.pages.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.BookOpen02
import me.rerere.hugeicons.stroke.Bot
import me.rerere.hugeicons.stroke.BubbleChat
import me.rerere.hugeicons.stroke.Chart
import me.rerere.hugeicons.stroke.Favourite
import me.rerere.hugeicons.stroke.Setting07
import me.rerere.hugeicons.stroke.User
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject
import java.time.Duration
import java.time.Instant
import kotlin.uuid.Uuid

@Composable
fun DesktopPage() {
    val navController = LocalNavController.current
    val settings = LocalSettings.current
    val currentAssistant = settings.getCurrentAssistant()
    var note by remember { mutableStateOf("I want to be gently accompanied today.") }
    val apps = remember {
        listOf(
            DesktopApp("Study", HugeIcons.BookOpen02, "study") {},
            DesktopApp("Chat", HugeIcons.BubbleChat, "chat") { navController.navigate(Screen.ChatRooms) },
            DesktopApp("User", HugeIcons.User, "user") { navController.navigate(Screen.UserProfile) },
            DesktopApp("Stats", HugeIcons.Chart, "stats") { navController.navigate(Screen.Stats) },
            DesktopApp("Saved", HugeIcons.Favourite, "favorite") { navController.navigate(Screen.Favorite) },
            DesktopApp("Roles", HugeIcons.Bot, "assistant") { navController.navigate(Screen.Assistant) },
            DesktopApp("Settings", HugeIcons.Setting07, "setting") { navController.navigate(Screen.Setting) },
        )
    }

    Scaffold(containerColor = CustomColors.topBarColors.containerColor) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.height(22.dp))
            DesktopHero(
                assistant = currentAssistant,
                note = note,
                onNoteChange = { note = it },
                onOpenChat = { navController.navigate(Screen.ChatRooms) },
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth(),
                userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp),
            ) {
                items(apps, key = { it.key }) { app ->
                    DesktopAppIcon(app)
                }
            }
        }
    }
}

@Composable
fun ChatRoomsPage() {
    val navController = LocalNavController.current
    val settings = LocalSettings.current
    val conversationRepository = koinInject<ConversationRepository>()
    val settingsStore = koinInject<SettingsStore>()
    val scope = rememberCoroutineScope()
    val latestConversations = remember { mutableStateMapOf<Uuid, Conversation?>() }

    LaunchedEffect(settings.assistants) {
        settings.assistants.forEach { assistant ->
            latestConversations[assistant.id] = conversationRepository.getRecentConversations(assistant.id, limit = 1)
                .firstOrNull()
        }
    }

    Scaffold(containerColor = CustomColors.topBarColors.containerColor) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = "Chat",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
            items(settings.assistants, key = { it.id.toString() }) { assistant ->
                val conversation = latestConversations[assistant.id]
                ChatRoomRow(
                    assistant = assistant,
                    conversation = conversation,
                    onClick = {
                        scope.launch {
                            settingsStore.updateAssistant(assistant.id)
                            val target = conversationRepository.getRecentConversations(assistant.id, limit = 1)
                                .firstOrNull()
                                ?: Conversation.ofId(
                                    id = Uuid.random(),
                                    assistantId = assistant.id,
                                    newConversation = true,
                                )
                            navController.navigate(Screen.Chat(target.id.toString()))
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun UserProfilePage() {
    val settings = LocalSettings.current
    Scaffold(containerColor = CustomColors.topBarColors.containerColor) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.height(40.dp))
            UIAvatar(
                name = settings.displaySetting.userNickname.ifBlank { "Me" },
                value = settings.displaySetting.userAvatar,
                modifier = Modifier.size(96.dp),
            )
            Text(
                text = settings.displaySetting.userNickname.ifBlank { "My profile" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Name", style = MaterialTheme.typography.labelMedium)
                    Text(settings.displaySetting.userNickname.ifBlank { "No nickname yet" })
                    Text("Profile", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "This is the first profile entry. Birthday, goals, preferences, and health summaries can be added later.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopHero(
    assistant: Assistant,
    note: String,
    onNoteChange: (String) -> Unit,
    onOpenChat: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().height(250.dp),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UIAvatar(
                    name = assistant.name.ifBlank { "Lulu" },
                    value = assistant.avatar,
                    modifier = Modifier.size(58.dp),
                    onClick = onOpenChat,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = assistant.name.ifBlank { "Lulu" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Tap to chat",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3,
                label = { Text("Today") },
            )
        }
    }
}

@Composable
private fun DesktopAppIcon(app: DesktopApp) {
    Column(
        modifier = Modifier.clickable(onClick = app.onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = app.icon,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChatRoomRow(
    assistant: Assistant,
    conversation: Conversation?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UIAvatar(
                name = assistant.name.ifBlank { "Assistant" },
                value = assistant.avatar,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = assistant.name.ifBlank { "Assistant" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = conversation?.title?.ifBlank { "Tap to continue" } ?: "No messages yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = conversation?.updateAt?.let { relativeTime(it) } ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class DesktopApp(
    val label: String,
    val icon: ImageVector,
    val key: String,
    val onClick: () -> Unit,
)

private fun relativeTime(instant: Instant): String {
    val duration = Duration.between(instant, Instant.now()).abs()
    return when {
        duration.toMinutes() < 1 -> "now"
        duration.toHours() < 1 -> "${duration.toMinutes()}m"
        duration.toDays() < 1 -> "${duration.toHours()}h"
        else -> "${duration.toDays()}d"
    }
}
