package me.rerere.rikkahub.ui.pages.cihai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.cihai.CihaiEntry
import me.rerere.rikkahub.data.cihai.CihaiEntryKind
import me.rerere.rikkahub.data.cihai.CihaiStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.living.LivingPresenceStore
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CihaiPage(onBack: () -> Unit) {
    val settings = LocalSettings.current
    val store = koinInject<CihaiStore>()
    val livingPresenceStore = koinInject<LivingPresenceStore>()
    val state by store.state.collectAsState()
    val livingState by livingPresenceStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    val fallbackAssistant = settings.getCurrentAssistant()
    val selectedAssistantId = state.selectedAssistantId
        .takeIf { id -> settings.assistants.any { it.id.toString() == id } }
        ?: fallbackAssistant.id.toString()
    val selectedAssistant = settings.assistants.firstOrNull { it.id.toString() == selectedAssistantId }
        ?: fallbackAssistant
    var selectedTab by remember { mutableIntStateOf(0) }
    val sections = visibleCihaiSections()
    val concernCards = buildLivingIntentCards(
        intents = livingState.activeIntents,
        selectedAssistantId = selectedAssistantId,
    )

    LaunchedEffect(selectedAssistantId) {
        if (state.selectedAssistantId != selectedAssistantId) {
            store.selectAssistant(selectedAssistantId)
        }
    }

    Scaffold(containerColor = CustomColors.topBarColors.containerColor) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "辞海",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "返回",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onBack).padding(8.dp),
                )
            }
            Text(
                text = "这里放挂心任务、第一人称心迹和记忆沉淀；记忆会按轮次自动整理，阅读保留独立入口。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AssistantSelector(
                selectedAssistantId = selectedAssistantId,
                onSelect = { id -> scope.launch { store.selectAssistant(id) } },
            )
            Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    UIAvatar(
                        name = selectedAssistant.name,
                        value = selectedAssistant.avatar,
                        modifier = Modifier.size(48.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(selectedAssistant.name.ifBlank { "当前角色" }, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "当前查看这个角色的辞海：她在挂心什么、没说出口什么、准备怎样继续感知。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            TabRow(selectedTabIndex = selectedTab) {
                sections.forEachIndexed { index, section ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(section.label, maxLines = 1) },
                    )
                }
            }
            val selectedSection = sections[selectedTab]
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (selectedSection) {
                    CihaiSection.CONCERNS -> {
                        if (concernCards.isEmpty()) {
                            item(key = "empty-concerns") {
                                EmptyCihaiSection(
                                    title = "现在没有挂心任务",
                                    body = "这里以后只放持续照看的事，比如考试、起床、身体状态、DDL 或学习节奏。",
                                )
                            }
                        } else {
                            item(key = "concern-title") {
                                Text(
                                    text = "挂心中",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            items(concernCards, key = { it.id }) { card ->
                                ConcernCard(card)
                            }
                        }
                    }
                    else -> {
                        val entries = entriesForCihaiSection(
                            entries = state.entries,
                            selectedAssistantId = selectedAssistantId,
                            section = selectedSection,
                        )
                        if (entries.isEmpty()) {
                            item(key = "empty-${selectedSection.name}") {
                                EmptyCihaiSection(
                                    title = selectedSection.emptyTitle,
                                    body = selectedSection.emptyBody,
                                )
                            }
                        } else {
                            items(entries, key = { it.id }) { entry ->
                                EntryCard(
                                    entry = entry,
                                    onDelete = { scope.launch { store.deleteEntry(entry.id) } },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal enum class CihaiSection(
    val label: String,
    val entryKind: CihaiEntryKind?,
    val emptyTitle: String,
    val emptyBody: String,
) {
    CONCERNS(
        label = "挂心",
        entryKind = null,
        emptyTitle = "现在没有挂心任务",
        emptyBody = "这里以后只放持续照看的事，比如考试、起床、身体状态、DDL 或学习节奏。",
    ),
    INNER_JOURNAL(
        label = "心迹",
        entryKind = CihaiEntryKind.INNER_JOURNAL,
        emptyTitle = "还没有心迹",
        emptyBody = "这里只放角色第一人称没说出口的心理活动，不放行动记录。",
    ),
    REFLECTION(
        label = "沉淀",
        entryKind = CihaiEntryKind.REFLECTION,
        emptyTitle = "还没有记忆沉淀",
        emptyBody = "这里只放多轮判断之后整理出的经验，供之后相似情境复用。",
    ),
}

internal fun visibleCihaiSections(): List<CihaiSection> = listOf(
    CihaiSection.CONCERNS,
    CihaiSection.INNER_JOURNAL,
    CihaiSection.REFLECTION,
)

internal fun entriesForCihaiSection(
    entries: List<CihaiEntry>,
    selectedAssistantId: String,
    section: CihaiSection,
): List<CihaiEntry> {
    val kind = section.entryKind ?: return emptyList()
    return entries.filter { entry ->
        entry.assistantId == selectedAssistantId && entry.kind == kind
    }
}

@Composable
private fun ConcernCard(card: LivingIntentCardModel) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = card.statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Text(
                text = card.nextPerceptionText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = listOf(card.eventLine, card.goalLine).joinToString("\n"),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = card.perceptionLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyCihaiSection(
    title: String,
    body: String,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AssistantSelector(
    selectedAssistantId: String,
    onSelect: (String) -> Unit,
) {
    val settings = LocalSettings.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(settings.assistants, key = { it.id.toString() }) { assistant ->
            FilterChip(
                selected = assistant.id.toString() == selectedAssistantId,
                onClick = { onSelect(assistant.id.toString()) },
                label = { Text(assistant.name.ifBlank { "角色" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
private fun EntryCard(entry: CihaiEntry, onDelete: () -> Unit) {
    val displayBody = remember(entry.content, entry.kind) { entry.displayBody() }
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(HugeIcons.Delete01, contentDescription = "删除")
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = if (entry.memorySaved) "已入记忆" else "待记忆",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = displayBody,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
            if (entry.emotion.isNotBlank()) {
                Text(
                    text = "情绪：${entry.emotion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatTime(entry.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun CihaiEntry.displayBody(): String {
    val raw = content.trim()
    val thought = raw.extractTraceSection("Thought:", "Action:")
    val decision = raw.extractTraceSection("Decision:", "Capability requests:")
    val intention = raw.extractTraceSection("Intention=", "Observation=")
        .ifBlank { raw.extractTraceSection("Intention=", "Decision=") }
    return when (kind) {
        CihaiEntryKind.INNER_JOURNAL -> thought.ifBlank { raw }.cleanTraceForUser()
        CihaiEntryKind.ACTION_LOG -> buildList {
            intention.cleanTraceForUser().takeIf { it.isNotBlank() }?.let { add("我想做的是：$it") }
            decision.cleanTraceForUser().takeIf { it.isNotBlank() }?.let { add("这次决定：$it") }
            if (isEmpty()) add(raw.cleanTraceForUser())
        }.joinToString("\n\n")
        else -> raw.cleanTraceForUser()
    }.ifBlank { "这条记录还没有可展示的内容。" }
}

private fun String.extractTraceSection(start: String, end: String): String {
    val startIndex = indexOf(start, ignoreCase = true)
    if (startIndex < 0) return ""
    val bodyStart = startIndex + start.length
    val endIndex = indexOf(end, startIndex = bodyStart, ignoreCase = true).takeIf { it >= 0 } ?: length
    return substring(bodyStart, endIndex).trim()
}

private fun String.cleanTraceForUser(): String =
    lineSequence()
        .map { it.trim() }
        .filter { line ->
            line.isNotBlank() &&
                !line.startsWith("Structured seven-layer trace", ignoreCase = true) &&
                !line.startsWith("Seven-layer Living Presence trace", ignoreCase = true) &&
                !line.startsWith("Source:", ignoreCase = true) &&
                !line.startsWith("Action:", ignoreCase = true) &&
                !line.startsWith("Observation:", ignoreCase = true) &&
                !line.startsWith("Perception=", ignoreCase = true) &&
                !line.startsWith("Appraisal=", ignoreCase = true) &&
                !line.startsWith("Belief=", ignoreCase = true) &&
                !line.startsWith("Motive=", ignoreCase = true) &&
                !line.startsWith("Capability requests:", ignoreCase = true) &&
                !line.startsWith("Allowed actions:", ignoreCase = true)
        }
        .joinToString("\n")
        .replace(Regex("""\b(get_[a-z_]+|set_alarm|today_schedule|today_study_plan)\b"""), "")
        .replace(Regex("""\s{2,}"""), " ")
        .trim()
