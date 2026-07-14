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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.cihai.CihaiEntry
import me.rerere.rikkahub.data.cihai.CihaiEntryKind
import me.rerere.rikkahub.data.cihai.CihaiStore
import me.rerere.rikkahub.data.companion.CompanionSnapshot
import me.rerere.rikkahub.data.companion.CompanionGoal
import me.rerere.rikkahub.data.companion.CompanionGoalStatus
import me.rerere.rikkahub.data.companion.CompanionLifeEvent
import me.rerere.rikkahub.data.companion.CompanionLifeEventStatus
import me.rerere.rikkahub.data.companion.CompanionNeuroState
import me.rerere.rikkahub.data.companion.CompanionPrivateImpression
import me.rerere.rikkahub.data.companion.CompanionRelationshipEvent
import me.rerere.rikkahub.data.companion.CompanionRelationshipState
import me.rerere.rikkahub.data.companion.CompanionStore
import me.rerere.rikkahub.data.companion.CompanionRuntime
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.service.ProactiveMessageService
import me.rerere.rikkahub.data.service.MemoryBankService
import me.rerere.rikkahub.data.service.syncCompanionPrivateImpression
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
    val context = LocalContext.current
    val settings = LocalSettings.current
    val store = koinInject<CihaiStore>()
    val companionStore = koinInject<CompanionStore>()
    val companionRuntime = koinInject<CompanionRuntime>()
    val memoryBankService = koinInject<MemoryBankService>()
    val state by store.state.collectAsState()
    val companionState by companionStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    val fallbackAssistant = settings.getCurrentAssistant()
    val selectedAssistantId = state.selectedAssistantId
        .takeIf { id -> settings.assistants.any { it.id.toString() == id } }
        ?: fallbackAssistant.id.toString()
    val selectedAssistant = settings.assistants.firstOrNull { it.id.toString() == selectedAssistantId }
        ?: fallbackAssistant
    var selectedTab by remember { mutableIntStateOf(0) }
    val sections = visibleCihaiSections()
    val selectedSnapshot = companionState.snapshots
        .firstOrNull { it.assistantId == selectedAssistantId }
        ?: CompanionSnapshot.empty(selectedAssistantId)
    val concernCards = buildCompanionConcernCards(snapshot = selectedSnapshot)

    LaunchedEffect(selectedAssistantId) {
        if (state.selectedAssistantId != selectedAssistantId) {
            store.selectAssistant(selectedAssistantId)
        }
        memoryBankService.syncCompanionPrivateImpression(
            companionRuntime = companionRuntime,
            assistantId = selectedAssistantId,
        )
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
                text = "这里放挂心任务和角色主动写下的第一人称日记；长期记忆会从真实事件中自动整理。",
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
                                ConcernCard(
                                    card = card,
                                    onDelete = {
                                        scope.launch {
                                            companionStore.deleteConcernSubjects(
                                                assistantId = selectedAssistantId,
                                                subjectKeys = card.subjectKeys,
                                            )
                                            ProactiveMessageService.reconcileDurableCommitments(context, settings)
                                        }
                                    },
                                )
                            }
                        }
                    }
                    CihaiSection.RELATIONSHIP -> {
                        item(key = "relationship-overview") {
                            RelationshipOverview(
                                relationship = selectedSnapshot.relationship,
                                history = selectedSnapshot.relationshipHistory,
                                privateImpression = selectedSnapshot.privateImpression,
                                neuroState = selectedSnapshot.neuroState,
                                goals = selectedSnapshot.goals,
                            )
                        }
                    }
                    CihaiSection.LIFE -> {
                        if (selectedSnapshot.lifeEvents.isEmpty()) {
                            item(key = "empty-life") {
                                EmptyCihaiSection(
                                    title = "数字生活还没有留下轨迹",
                                    body = "角色真正完成的聊天、工具行动、音乐、日记、记忆整理和自主等待会留在这里；没有证据的经历不会被写进来。",
                                )
                            }
                        } else {
                            items(
                                selectedSnapshot.lifeEvents.sortedByDescending { it.endedAt ?: it.startedAt },
                                key = { it.id },
                            ) { event ->
                                LifeEventCard(event)
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
    RELATIONSHIP(
        label = "关系",
        entryKind = null,
        emptyTitle = "关系还在形成",
        emptyBody = "这里会呈现信任、亲近、履约和边界默契。",
    ),
    LIFE(
        label = "生活",
        entryKind = null,
        emptyTitle = "数字生活还没有留下轨迹",
        emptyBody = "只有角色在 App 内真实完成的行动才会出现在这里。",
    ),
    DIARY(
        label = "日记",
        entryKind = CihaiEntryKind.DIARY,
        emptyTitle = "还没有日记",
        emptyBody = "这里放角色按自己口吻写下的第一人称日记，主要记录真实感受和没说出口的想法。",
    ),
}

internal fun visibleCihaiSections(): List<CihaiSection> = listOf(
    CihaiSection.CONCERNS,
    CihaiSection.RELATIONSHIP,
    CihaiSection.LIFE,
    CihaiSection.DIARY,
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
private fun ConcernCard(card: CompanionConcernCardModel, onDelete: () -> Unit) {
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
                TextButton(onClick = onDelete) {
                    Icon(
                        HugeIcons.Delete01,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("删除")
                }
            }
            Text(
                text = card.nextPerceptionText,
                style = MaterialTheme.typography.labelMedium,
                color = if (card.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Text(
                text = card.eventText,
                style = MaterialTheme.typography.bodyMedium,
            )
            card.goalText.takeIf(String::isNotBlank)?.let { goal ->
                Text(
                    text = goal,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            card.commitmentText?.let { commitment ->
                Text(
                    text = "答应你的事：$commitment",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RelationshipOverview(
    relationship: CompanionRelationshipState,
    history: List<CompanionRelationshipEvent>,
    privateImpression: CompanionPrivateImpression,
    neuroState: CompanionNeuroState,
    goals: List<CompanionGoal>,
) {
    val timeline = buildCompanionRelationshipTimeline(history)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = relationship.roleLabel.ifBlank { "正在形成彼此舒服的相处方式" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = relationshipSummary(relationship),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PrivateImpressionCard(privateImpression)
        CompanionGoalsCard(goals)
        NeuroStateCard(neuroState)
        RelationshipDimension("信任", relationship.trust, "愿不愿意把真实想法交给彼此")
        RelationshipDimension("亲近", relationship.closeness, "相处时自然靠近、分享和惦记的程度")
        RelationshipDimension("说到做到", relationship.reliability, "答应的事是否能稳定落地")
        RelationshipDimension("边界默契", relationship.boundaryConfidence, "是否理解哪些方式让彼此舒服")
        RelationshipDimension(
            label = "未解开的心结",
            value = relationship.unresolvedTension,
            description = "还需要说开、道歉或重新理解的部分；越低越轻松",
            inverseTone = true,
        )
        relationship.lastMeaningfulInteractionAt?.let { time ->
            Text(
                text = "最近一次明显影响关系：${formatTime(time)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "最近变化",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (timeline.isEmpty()) {
            Text(
                text = "还没有形成可记录的关系变化。之后的重要袒露、边界、承诺和修复会留在这里。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            timeline.forEach { item -> RelationshipTimelineEntry(item) }
        }
    }
}

@Composable
private fun CompanionGoalsCard(goals: List<CompanionGoal>) {
    val activeGoals = goals
        .filter { it.status == CompanionGoalStatus.ACTIVE }
        .sortedByDescending { it.updatedAt }
        .take(6)
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("角色自己的目标", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "这些目标属于数字生命自己；进度只能由真实生活事件和证据推进。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (activeGoals.isEmpty()) {
                Text(
                    "还没有正在推进的自主目标。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                activeGoals.forEach { goal ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                goal.title,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${(goal.progress.coerceIn(0f, 1f) * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { goal.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivateImpressionCard(impression: CompanionPrivateImpression) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("角色眼中的你", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (
                impression.summary.isBlank() &&
                impression.observedTraits.isEmpty() &&
                impression.preferences.isEmpty() &&
                impression.boundaries.isEmpty()
            ) {
                Text(
                    "还没有形成有证据的私密印象。重要偏好、边界和关系变化会从长期记忆中逐渐沉淀。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                impression.summary.takeIf(String::isNotBlank)?.let { summary ->
                    Text(summary, style = MaterialTheme.typography.bodyMedium)
                }
                ImpressionLine("观察", impression.observedTraits.takeLast(3))
                ImpressionLine("偏好", impression.preferences.takeLast(3))
                ImpressionLine("边界", impression.boundaries.takeLast(3))
                ImpressionLine("最近变化", impression.recentChanges.takeLast(3))
            }
        }
    }
}

@Composable
private fun ImpressionLine(label: String, values: List<String>) {
    if (values.isEmpty()) return
    Text(
        text = "$label：${values.joinToString("；")}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NeuroStateCard(state: CompanionNeuroState) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("数字神经状态", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "数值只由真实事件、关系变化和时间衰减更新，模型只能理解和表达，不能随意改写。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NeuroDimension("期待", state.dopamine)
            NeuroDimension("稳定", state.serotonin)
            NeuroDimension("压力", state.cortisol, inverseTone = true)
            NeuroDimension("亲近", state.oxytocin)
            NeuroDimension("专注", state.norepinephrine)
            NeuroDimension("能量", state.energy)
        }
    }
}

@Composable
private fun NeuroDimension(label: String, value: Float, inverseTone: Boolean = false) {
    val normalized = value.coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(
                text = "${(normalized * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = if (inverseTone && normalized >= 0.65f) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
        LinearProgressIndicator(progress = { normalized }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun LifeEventCard(event: CompanionLifeEvent) {
    val statusText = when (event.status) {
        CompanionLifeEventStatus.PLANNED -> "计划中"
        CompanionLifeEventStatus.RUNNING -> "进行中"
        CompanionLifeEventStatus.COMPLETED -> "已完成"
        CompanionLifeEventStatus.FAILED -> "未完成"
        CompanionLifeEventStatus.CANCELLED -> "已取消"
    }
    val statusColor = when (event.status) {
        CompanionLifeEventStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
        CompanionLifeEventStatus.RUNNING -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Surface(shape = RoundedCornerShape(999.dp), color = statusColor) {
                    Text(statusText, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            event.summary.takeIf(String::isNotBlank)?.let { summary ->
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = event.type.lifeEventLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatTime(event.endedAt ?: event.startedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun me.rerere.rikkahub.data.companion.CompanionLifeEventType.lifeEventLabel(): String = when (this) {
    me.rerere.rikkahub.data.companion.CompanionLifeEventType.CONVERSATION -> "对话"
    me.rerere.rikkahub.data.companion.CompanionLifeEventType.PROACTIVE_MESSAGE -> "主动联系"
    me.rerere.rikkahub.data.companion.CompanionLifeEventType.TOOL_ACTION -> "数字行动"
    me.rerere.rikkahub.data.companion.CompanionLifeEventType.MEMORY_REVIEW -> "记忆"
    me.rerere.rikkahub.data.companion.CompanionLifeEventType.STUDY_REVIEW -> "学习计划"
    me.rerere.rikkahub.data.companion.CompanionLifeEventType.JOURNAL -> "日记"
    me.rerere.rikkahub.data.companion.CompanionLifeEventType.MUSIC -> "音乐"
    me.rerere.rikkahub.data.companion.CompanionLifeEventType.GAME -> "游戏"
    me.rerere.rikkahub.data.companion.CompanionLifeEventType.REFLECTION -> "整理想法"
    me.rerere.rikkahub.data.companion.CompanionLifeEventType.WAITING -> "自主等待"
}

@Composable
private fun RelationshipTimelineEntry(item: CompanionRelationshipTimelineItem) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatTime(item.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item.detail?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item.deltaText.takeIf(String::isNotBlank)?.let { delta ->
                Text(
                    text = delta,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun RelationshipDimension(
    label: String,
    value: Float,
    description: String,
    inverseTone: Boolean = false,
) {
    val normalized = value.coerceIn(0f, 1f)
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    text = "${(normalized * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (inverseTone && normalized >= 0.5f) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            LinearProgressIndicator(
                progress = { normalized },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun relationshipSummary(relationship: CompanionRelationshipState): String = when {
    relationship.unresolvedTension >= 0.6f -> "现在有一些没有解开的情绪，角色会更谨慎地靠近和修复。"
    relationship.trust >= 0.75f && relationship.closeness >= 0.65f -> "彼此已经很熟悉，很多关心和表达可以更自然。"
    relationship.trust >= 0.6f -> "信任正在稳定形成，角色会逐渐更自然地记住和回应重要的事。"
    else -> "关系还在慢慢建立，角色会通过尊重边界和兑现承诺来积累信任。"
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
