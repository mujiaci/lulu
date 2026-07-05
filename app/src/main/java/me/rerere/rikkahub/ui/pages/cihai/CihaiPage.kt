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
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CihaiPage(onBack: () -> Unit) {
    val settings = LocalSettings.current
    val store = koinInject<CihaiStore>()
    val state by store.state.collectAsState()
    val scope = rememberCoroutineScope()
    val fallbackAssistant = settings.getCurrentAssistant()
    val selectedAssistantId = state.selectedAssistantId
        .takeIf { id -> settings.assistants.any { it.id.toString() == id } }
        ?: fallbackAssistant.id.toString()
    val selectedAssistant = settings.assistants.firstOrNull { it.id.toString() == selectedAssistantId }
        ?: fallbackAssistant
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        CihaiEntryKind.INNER_JOURNAL,
        CihaiEntryKind.ACTION_LOG,
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
                    text = "露露日记",
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
                text = "这里只放角色没有说出口的心声，以及她在后台做出的行动选择。阅读已经挪到独立入口。",
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
                            text = "当前查看这个角色的活人感日记。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, kind ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(kind.label, maxLines = 1) },
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    state.entries.filter {
                        it.assistantId == selectedAssistantId && it.kind == tabs[selectedTab]
                    },
                    key = { it.id },
                ) { entry ->
                    EntryCard(entry)
                }
            }
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
private fun EntryCard(entry: CihaiEntry) {
    val displayBody = remember(entry.content, entry.kind) { entry.displayBody() }
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
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
