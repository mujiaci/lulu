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
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.BookOpen02
import me.rerere.hugeicons.stroke.Bookshelf02
import me.rerere.hugeicons.stroke.Bot
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.BubbleChat
import me.rerere.hugeicons.stroke.Bug01
import me.rerere.hugeicons.stroke.Chart
import me.rerere.hugeicons.stroke.Favourite
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.hugeicons.stroke.Setting07
import me.rerere.hugeicons.stroke.User
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.desktop.DesktopStore
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import java.time.Duration
import java.time.Instant
import kotlin.uuid.Uuid

@Composable
fun DesktopPage() {
    val navController = LocalNavController.current
    val settings = LocalSettings.current
    val conversationRepository = koinInject<ConversationRepository>()
    val settingsStore = koinInject<SettingsStore>()
    val desktopStore = koinInject<DesktopStore>()
    val storedAppOrder by desktopStore.appOrder.collectAsState()
    val scope = rememberCoroutineScope()
    val currentAssistant = settings.getCurrentAssistant()
    var note by remember { mutableStateOf("今天也想被好好陪着。") }

    fun openAssistantChat(assistant: Assistant) {
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
    }

    val apps = remember {
        listOf(
            DesktopApp("记忆", HugeIcons.Brain02, "memory") { navController.navigate(Screen.MemoryBank) },
            DesktopApp("露露日记", HugeIcons.BookOpen02, "cihai") { navController.navigate(Screen.Cihai) },
            DesktopApp("阅读", HugeIcons.Bookshelf02, "reading") { navController.navigate(Screen.CihaiReading) },
            DesktopApp("考研", HugeIcons.BookOpen02, "study") { navController.navigate(Screen.Study) },
            DesktopApp("星愿馆", HugeIcons.Bookshelf02, "starwish") { navController.navigate(Screen.StarWish) },
            DesktopApp("游戏", HugeIcons.Puzzle, "game") { navController.navigate(Screen.GameHub) },
            DesktopApp("聊天", HugeIcons.BubbleChat, "chat") { navController.navigate(Screen.ChatRooms) },
            DesktopApp("我的", HugeIcons.User, "user") { navController.navigate(Screen.UserProfile) },
            DesktopApp("缓存统计", HugeIcons.Chart, "stats") { navController.navigate(Screen.Stats) },
            DesktopApp("收藏", HugeIcons.Favourite, "favorite") { navController.navigate(Screen.Favorite) },
            DesktopApp("日志", HugeIcons.Bug01, "logs") { navController.navigate(Screen.Log) },
            DesktopApp("角色", HugeIcons.Bot, "assistant") { navController.navigate(Screen.Assistant) },
            DesktopApp("设置", HugeIcons.Setting07, "setting") { navController.navigate(Screen.Setting) },
        )
    }
    val orderedApps = remember(apps, storedAppOrder) {
        val appByKey = apps.associateBy { it.key }
        storedAppOrder.mapNotNull { appByKey[it] } + apps.filterNot { it.key in storedAppOrder }
    }
    val appGridState = rememberLazyGridState()
    val haptic = LocalHapticFeedback.current
    val reorderableState = rememberReorderableLazyGridState(appGridState) { from, to ->
        val next = orderedApps.toMutableList().apply {
            this[to.index] = this[from.index].also { this[from.index] = this[to.index] }
        }
        scope.launch {
            desktopStore.setAppOrder(next.map { it.key })
        }
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
                onOpenChat = { openAssistantChat(currentAssistant) },
            )
            LazyVerticalGrid(
                modifier = Modifier.fillMaxWidth().weight(1f),
                state = appGridState,
                columns = GridCells.Adaptive(minSize = 72.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp),
            ) {
                gridItems(orderedApps, key = { it.key }) { app ->
                    ReorderableItem(
                        state = reorderableState,
                        key = app.key,
                    ) { isDragging ->
                        DesktopAppIcon(
                            app = app,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .scale(if (isDragging) 0.92f else 1f)
                                .longPressDraggableHandle(
                                    onDragStarted = {
                                        haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                    },
                                    onDragStopped = {
                                        haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                    },
                                ),
                        )
                    }
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
                    text = "聊天",
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
    val settingsStore = koinInject<SettingsStore>()
    val scope = rememberCoroutineScope()

    fun updateProfile(
        nickname: String? = null,
        profile: String? = null,
        appearance: String? = null,
    ) {
        scope.launch {
            settingsStore.update { current ->
                current.copy(
                    displaySetting = current.displaySetting.copy(
                        userNickname = nickname ?: current.displaySetting.userNickname,
                        userProfile = profile ?: current.displaySetting.userProfile,
                        userAppearancePrompt = appearance ?: current.displaySetting.userAppearancePrompt,
                    )
                )
            }
        }
    }

    Scaffold(containerColor = CustomColors.topBarColors.containerColor) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.height(40.dp))
            UIAvatar(
                name = settings.displaySetting.userNickname.ifBlank { "我" },
                value = settings.displaySetting.userAvatar,
                modifier = Modifier.size(96.dp),
            )
            Text(
                text = settings.displaySetting.userNickname.ifBlank { "我的资料" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    OutlinedTextField(
                        value = settings.displaySetting.userNickname,
                        onValueChange = { updateProfile(nickname = it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("我的昵称") },
                        supportingText = { Text("角色会用这个名字称呼你。") },
                    )
                    OutlinedTextField(
                        value = settings.displaySetting.userProfile,
                        onValueChange = { updateProfile(profile = it) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 8,
                        label = { Text("我的个人资料") },
                        supportingText = { Text("可以写身份、目标、偏好、身体状态、学习习惯。") },
                    )
                    OutlinedTextField(
                        value = settings.displaySetting.userAppearancePrompt,
                        onValueChange = { updateProfile(appearance = it) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        label = { Text("我的外貌 / 互动生图参考") },
                        supportingText = { Text("星愿馆互动图和聊天稳定设定会使用这里。") },
                    )
                    Text(
                        text = "这里保存的是用户资料层。每次聊天会作为稳定资料注入，不需要再去设置页找。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
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
            Row(
                modifier = Modifier.clickable(onClick = onOpenChat),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UIAvatar(
                    name = assistant.name.ifBlank { "露露" },
                    value = assistant.avatar,
                    modifier = Modifier.size(58.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = assistant.name.ifBlank { "露露" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "点我聊天",
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
                label = { Text("今日心情") },
            )
        }
    }
}

@Composable
private fun DesktopAppIcon(app: DesktopApp, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = app.onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = app.icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = app.label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
                name = assistant.name.ifBlank { "露露" },
                value = assistant.avatar,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = assistant.name.ifBlank { "露露" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = conversation?.title?.ifBlank { "点开继续聊天" } ?: "还没有聊天记录",
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
        duration.toMinutes() < 1 -> "刚刚"
        duration.toHours() < 1 -> "${duration.toMinutes()}分钟前"
        duration.toDays() < 1 -> "${duration.toHours()}小时前"
        else -> "${duration.toDays()}天前"
    }
}
