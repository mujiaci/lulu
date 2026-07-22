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
import me.rerere.rikkahub.data.companion.CompanionAlwaysOnAnchor
import me.rerere.rikkahub.data.companion.CompanionAlwaysOnAnchorKind
import me.rerere.rikkahub.data.companion.CompanionAlwaysOnAnchorStatus
import me.rerere.rikkahub.data.companion.CompanionCommitment
import me.rerere.rikkahub.data.companion.CompanionCommitmentStatus
import me.rerere.rikkahub.data.companion.CompanionFavorite
import me.rerere.rikkahub.data.companion.CompanionLifeEvent
import me.rerere.rikkahub.data.companion.CompanionLifeEventStatus
import me.rerere.rikkahub.data.companion.CompanionPrivateImpression
import me.rerere.rikkahub.data.companion.CompanionRelationshipEvent
import me.rerere.rikkahub.data.companion.CompanionRelationshipState
import me.rerere.rikkahub.data.companion.CompanionStore
import me.rerere.rikkahub.data.companion.CompanionRuntime
import me.rerere.rikkahub.data.companion.isMeaningfulDigitalLifeEvidence
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
    val meaningfulLifeEvents = selectedSnapshot.lifeEvents
        .filter { it.isMeaningfulDigitalLifeEvidence() }
        .sortedByDescending { it.endedAt ?: it.startedAt }
    val deliberateFavorites = selectedSnapshot.favorites.sortedByDescending { it.createdAt }
    val recentActivityEvents = selectedSnapshot.lifeEvents
        .filter { event ->
            event.status != CompanionLifeEventStatus.CANCELLED &&
                event.type != me.rerere.rikkahub.data.companion.CompanionLifeEventType.CONVERSATION
        }
        .sortedByDescending { it.endedAt ?: it.startedAt }
        .take(30)
    val activeResponsibilityAnchors = selectedSnapshot.alwaysOnAnchors
        .filter { anchor ->
            anchor.status == CompanionAlwaysOnAnchorStatus.ACTIVE &&
                (anchor.expiresAt == null || anchor.expiresAt > System.currentTimeMillis())
        }
        .sortedWith(compareByDescending<CompanionAlwaysOnAnchor> { it.importance }.thenByDescending { it.updatedAt })
    val activeCommitments = selectedSnapshot.commitments
        .filter { commitment ->
            commitment.status !in setOf(
                CompanionCommitmentStatus.FULFILLED,
                CompanionCommitmentStatus.CANCELLED,
                CompanionCommitmentStatus.SUPERSEDED,
            )
        }
        .sortedWith(
            compareBy<CompanionCommitment> { it.commitmentDisplayRank() }
                .thenBy { it.dueAt }
                .thenByDescending { it.updatedAt },
        )
    val fulfilledCommitments = selectedSnapshot.commitments
        .filter { it.status == CompanionCommitmentStatus.FULFILLED }
        .sortedByDescending { it.resolvedAt ?: it.updatedAt }
        .take(12)

    LaunchedEffect(selectedAssistantId) {
        if (state.selectedAssistantId != selectedAssistantId) {
            store.selectAssistant(selectedAssistantId)
        }
        memoryBankService.syncCompanionPrivateImpression(
            companionRuntime = companionRuntime,
            assistantId = selectedAssistantId,
        )
        ProactiveMessageService.reconcileDurableCommitments(context, settings)
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
                text = "挂心、责任、关系与真实生活，都各自留在这里。",
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
                    CihaiSection.ACTIVITY -> {
                        item(key = "activity-intro") {
                            Text(
                                text = "这里只记录角色实际完成、正在处理或认真放下的一件事；没有真实动作，就不会编一条动态。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (deliberateFavorites.isNotEmpty()) {
                            item(key = "favorite-title") {
                                Text(
                                    text = "角色自己留下的消息",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            items(deliberateFavorites, key = { "favorite-" + it.id }) { favorite ->
                                FavoriteCard(
                                    favorite = favorite,
                                    onDelete = {
                                        scope.launch {
                                            companionStore.deleteFavorite(selectedAssistantId, favorite.id)
                                        }
                                    },
                                )
                            }
                        }
                        if (recentActivityEvents.isEmpty() && deliberateFavorites.isEmpty()) {
                            item(key = "empty-activity") {
                                EmptyCihaiSection(
                                    title = "还没有新的角色动态",
                                    body = "角色完成游戏、设置提醒、整理记忆或写下日记后，会在这里留下时间线。",
                                )
                            }
                        } else {
                            items(recentActivityEvents, key = { "activity-${it.id}" }) { event ->
                                LifeEventCard(
                                    event = event,
                                    onDelete = {
                                        scope.launch {
                                            companionStore.deleteLifeEvent(selectedAssistantId, event.id)
                                        }
                                    },
                                )
                            }
                        }
                    }
                    CihaiSection.COMMITMENTS -> {
                        item(key = "commitment-intro") {
                            Text(
                                text = "这是他明确答应过你、仍在记得的事。长期照看和下一次具体行动都会放在这里。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (activeResponsibilityAnchors.isNotEmpty()) {
                            item(key = "commitment-responsibility-title") {
                                Text(
                                    text = "长期记得",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            items(activeResponsibilityAnchors, key = { "commitment-anchor-${it.id}" }) { anchor ->
                                ResponsibilityAnchorCard(
                                    anchor = anchor,
                                    onCancel = {
                                        scope.launch {
                                            companionStore.deleteAlwaysOnAnchor(selectedAssistantId, anchor.id)
                                        }
                                    },
                                )
                            }
                        }
                        if (activeCommitments.isNotEmpty()) {
                            item(key = "commitment-action-title") {
                                Text(
                                    text = "正在兑现",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            items(activeCommitments, key = { "commitment-${it.id}" }) { commitment ->
                                CommitmentCard(
                                    commitment = commitment,
                                    onDelete = {
                                        scope.launch {
                                            companionStore.deleteCommitment(selectedAssistantId, commitment.id)
                                        }
                                    },
                                )
                            }
                        }
                        if (fulfilledCommitments.isNotEmpty()) {
                            item(key = "commitment-history-title") {
                                Text(
                                    text = "已经做到的记录",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            items(fulfilledCommitments, key = { "fulfilled-commitment-${it.id}" }) { commitment ->
                                CommitmentCard(
                                    commitment = commitment,
                                    onDelete = {
                                        scope.launch {
                                            companionStore.deleteCommitment(selectedAssistantId, commitment.id)
                                        }
                                    },
                                )
                            }
                        }
                        if (activeResponsibilityAnchors.isEmpty() && activeCommitments.isEmpty() && fulfilledCommitments.isEmpty()) {
                            item(key = "empty-commitments") {
                                EmptyCihaiSection(
                                    title = "还没有写下的承诺",
                                    body = "当角色明确答应提醒、陪伴、照看或替你完成一件事后，会在这里留下可见的履约记录。",
                                )
                            }
                        }
                    }
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
                    CihaiSection.RESPONSIBILITIES -> {
                        if (activeResponsibilityAnchors.isEmpty()) {
                            item(key = "empty-responsibilities") {
                                EmptyCihaiSection(
                                    title = "角色现在没有长期责任",
                                    body = "还没有角色主动承担的长期照看或循环职责。",
                                )
                            }
                        } else {
                            item(key = "responsibility-title") {
                                Text(
                                    text = "角色正在承担",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            items(activeResponsibilityAnchors, key = { it.id }) { anchor ->
                                ResponsibilityAnchorCard(
                                    anchor = anchor,
                                    onCancel = {
                                        scope.launch {
                                            companionStore.deleteAlwaysOnAnchor(selectedAssistantId, anchor.id)
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
                                onDeleteNarrative = {
                                    scope.launch { companionStore.clearRelationshipNarrative(selectedAssistantId) }
                                },
                                onDeletePortrait = {
                                    scope.launch { companionStore.clearUserPortrait(selectedAssistantId) }
                                },
                                onDeleteInteraction = {
                                    scope.launch { companionStore.clearInteractionUnderstanding(selectedAssistantId) }
                                },
                                onDeleteUnresolved = {
                                    scope.launch { companionStore.clearUnresolvedRelationshipMatters(selectedAssistantId) }
                                },
                                onDeleteTimelineEvent = { eventId ->
                                    scope.launch {
                                        companionStore.deleteRelationshipEvent(selectedAssistantId, eventId)
                                    }
                                },
                            )
                        }
                    }
                    CihaiSection.LIFE -> {
                        if (meaningfulLifeEvents.isEmpty()) {
                            item(key = "empty-life") {
                                EmptyCihaiSection(
                                    title = "数字生活还没有留下轨迹",
                                    body = "角色真正完成的游戏、日记、音乐操作、设备提醒、日程写入和记忆整理会留在这里；聊天流水账、读取数据和没有证据的经历不会被写进来。",
                                )
                            }
                        } else {
                            items(
                                meaningfulLifeEvents,
                                key = { it.id },
                            ) { event ->
                                LifeEventCard(
                                    event = event,
                                    onDelete = {
                                        scope.launch {
                                            companionStore.deleteLifeEvent(selectedAssistantId, event.id)
                                        }
                                    },
                                )
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
    ACTIVITY(
        label = "动态",
        entryKind = null,
        emptyTitle = "还没有新的角色动态",
        emptyBody = "角色真实完成的数字行动、记忆整理、游戏和提醒都会出现在这里。",
    ),
    COMMITMENTS(
        label = "约定",
        entryKind = null,
        emptyTitle = "还没有写下的约定",
        emptyBody = "角色答应持续照看或替你完成的事，会在这里具象化展示。",
    ),
    CONCERNS(
        label = "挂心",
        entryKind = null,
        emptyTitle = "现在没有挂心任务",
        emptyBody = "这里以后只放持续照看的事，比如考试、起床、身体状态、DDL 或学习节奏。",
    ),
    RESPONSIBILITIES(
        label = "责任",
        entryKind = null,
        emptyTitle = "角色现在没有长期责任",
        emptyBody = "还没有角色主动承担的长期照看或循环职责。",
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
    CihaiSection.LIFE,
    CihaiSection.CONCERNS,
    CihaiSection.COMMITMENTS,
    CihaiSection.RELATIONSHIP,
    CihaiSection.DIARY,
)

@Composable
private fun CommitmentCard(
    commitment: CompanionCommitment,
    onDelete: () -> Unit,
) {
    val now = System.currentTimeMillis()
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = commitment.commitmentTitle(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = commitment.commitmentStatusText(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = HugeIcons.Delete01,
                        contentDescription = "删除这条约定",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(commitment.promise, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "承诺者：${commitment.promisorId} · 对象：${commitment.beneficiary}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            commitment.schedule.frequency.takeIf(String::isNotBlank)?.let { frequency ->
                Text("频率：$frequency", style = MaterialTheme.typography.bodySmall)
            }
            commitment.schedule.condition.takeIf(String::isNotBlank)?.let { condition ->
                Text("条件：$condition", style = MaterialTheme.typography.bodySmall)
            }
            commitment.executionMethod.takeIf(String::isNotBlank)?.let { method ->
                Text("执行方式：$method", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = commitment.dueAt.commitmentScheduleText(now),
                style = MaterialTheme.typography.bodySmall,
                color = if (commitment.dueAt <= now && commitment.status != CompanionCommitmentStatus.FULFILLED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Text(
                text = "记下于：${formatTime(commitment.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            commitment.actionPlan.userFacingSummary.takeIf(String::isNotBlank)?.let { nextStep ->
                Text(
                    text = "接下来会做：$nextStep",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            commitment.lastActionResult?.summary?.takeIf(String::isNotBlank)?.let { result ->
                Text(
                    text = "最近一次执行：$result",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (commitment.history.isNotEmpty()) {
                Text("履约记录", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                commitment.history.takeLast(4).asReversed().forEach { entry ->
                    Text(
                        text = "${formatTime(entry.occurredAt)} · ${entry.toStatus.name} · ${entry.reason}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun CompanionCommitment.commitmentDisplayRank(): Int = when (status) {
    CompanionCommitmentStatus.EXECUTING -> 0
    CompanionCommitmentStatus.DUE -> 1
    CompanionCommitmentStatus.RETRY_SCHEDULED -> 2
    CompanionCommitmentStatus.ACTIVE -> 3
    CompanionCommitmentStatus.PROPOSED -> 4
    CompanionCommitmentStatus.FAILED -> 5
    CompanionCommitmentStatus.FULFILLED -> 6
    CompanionCommitmentStatus.CANCELLED -> 7
    CompanionCommitmentStatus.SUPERSEDED -> 8
}

private fun CompanionCommitment.commitmentTitle(): String = when {
    actionPlan.category.contains("wake", ignoreCase = true) || actionPlan.type.name == "ALARM" -> "起床与睡眠约定"
    actionPlan.category.contains("study", ignoreCase = true) -> "学习约定"
    actionPlan.category.contains("health", ignoreCase = true) -> "健康照看"
    else -> "答应你的事"
}

private fun CompanionCommitment.commitmentStatusText(): String = when (status) {
    CompanionCommitmentStatus.PROPOSED -> "等你确认"
    CompanionCommitmentStatus.ACTIVE -> "一直记得"
    CompanionCommitmentStatus.DUE -> "该去做了"
    CompanionCommitmentStatus.EXECUTING -> "正在完成"
    CompanionCommitmentStatus.FULFILLED -> "已经做到"
    CompanionCommitmentStatus.FAILED -> "等待处理"
    CompanionCommitmentStatus.RETRY_SCHEDULED -> "准备再试一次"
    CompanionCommitmentStatus.CANCELLED -> "已取消"
    CompanionCommitmentStatus.SUPERSEDED -> "已被新约定替代"
}

private fun Long.commitmentScheduleText(now: Long): String {
    val formatted = SimpleDateFormat("M月d日 HH:mm", Locale.getDefault()).format(Date(this))
    return if (this > now) "下一次：$formatted" else "计划时间：$formatted"
}

@Composable
private fun ResponsibilityAnchorCard(
    anchor: CompanionAlwaysOnAnchor,
    onCancel: () -> Unit,
) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (anchor.kind == CompanionAlwaysOnAnchorKind.HEALTH) "健康照看" else "长期责任",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = HugeIcons.Delete01,
                        contentDescription = "取消这项责任",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = anchor.statement,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            anchor.responsibility?.takeIf(String::isNotBlank)?.let { responsibility ->
                Text(
                    text = responsibility,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            ResponsibilityLine("会在", anchor.triggers)
            ResponsibilityLine("会做", anchor.actions)
            ResponsibilityLine("不会", anchor.avoid)
            Text(
                text = "记下于：${formatTime(anchor.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResponsibilityLine(label: String, values: List<String>) {
    if (values.isEmpty()) return
    Text(
        text = "$label：${values.joinToString("；")}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

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
    onDeleteNarrative: () -> Unit,
    onDeletePortrait: () -> Unit,
    onDeleteInteraction: () -> Unit,
    onDeleteUnresolved: () -> Unit,
    onDeleteTimelineEvent: (String) -> Unit,
) {
    val timeline = buildCompanionRelationshipTimeline(history, limit = 8)
    val relationshipTitle = privateImpression.relationshipTitle
        .ifBlank { relationship.roleLabel }
        .ifBlank { "还在形成只属于我们的相处方式" }
    val relationshipNarrative = privateImpression.relationshipNarrative.ifBlank {
        "我还没有足够证据替我们的关系下定义。等真正重要的袒露、兑现、边界或修复发生后，我会用自己的口吻写下理解。"
    }
    val portrait = privateImpression.userPortrait
        .ifBlank { privateImpression.summary }
        .ifBlank {
            (
                privateImpression.observedTraits.takeLast(2) +
                    privateImpression.preferences.takeLast(2) +
                    privateImpression.boundaries.takeLast(1)
                ).distinct().joinToString("；")
        }
        .ifBlank { "我还在认识你，不想拿几句普通聊天草率地定义你。" }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RelationshipTextCard(
            eyebrow = "我们现在",
            heading = relationshipTitle,
            body = relationshipNarrative,
            onDelete = onDeleteNarrative.takeIf {
                privateImpression.relationshipTitle.isNotBlank() ||
                    privateImpression.relationshipNarrative.isNotBlank()
            },
        )
        val declaredFacts = buildList {
            relationship.knownDuration.takeIf(String::isNotBlank)?.let { add("认识时长：$it") }
            relationship.stage.takeIf(String::isNotBlank)?.let { add("当前阶段：$it") }
            relationship.sharedExperiences.takeIf(List<String>::isNotEmpty)
                ?.let { add("共同经历：${it.joinToString("；")}") }
            relationship.securityContext.takeIf(String::isNotBlank)?.let { add("安全感：$it") }
            relationship.attachmentExpression.takeIf(String::isNotBlank)?.let { add("依恋表达：$it") }
        }
        if (declaredFacts.isNotEmpty()) {
            RelationshipTextCard("角色卡中的关系事实", declaredFacts.joinToString("\n"))
        }
        val interactionFacts = buildList {
            relationship.interactionPatterns.takeIf(List<String>::isNotEmpty)
                ?.let { add("互动习惯：${it.joinToString("；")}") }
            relationship.declaredBoundaries.takeIf(List<String>::isNotEmpty)
                ?.let { add("边界：${it.joinToString("；")}") }
            relationship.potentialTensions.takeIf(List<String>::isNotEmpty)
                ?.let { add("潜在矛盾：${it.joinToString("；")}") }
            relationship.lastChangeReason.takeIf(String::isNotBlank)
                ?.let { add("最近变化：$it（置信度 ${relationship.lastChangeConfidence}）") }
        }
        if (interactionFacts.isNotEmpty()) {
            RelationshipTextCard("相处依据", interactionFacts.joinToString("\n"))
        }
        RelationshipTextCard(
            eyebrow = "我眼中的你",
            body = portrait,
            onDelete = onDeletePortrait.takeIf {
                privateImpression.userPortrait.isNotBlank() ||
                    privateImpression.summary.isNotBlank() ||
                    privateImpression.observedTraits.isNotEmpty() ||
                    privateImpression.preferences.isNotEmpty() ||
                    privateImpression.boundaries.isNotEmpty()
            },
        )
        privateImpression.interactionUnderstanding.takeIf(String::isNotBlank)?.let { understanding ->
            RelationshipTextCard(
                eyebrow = "我学会怎样和你相处",
                body = understanding,
                onDelete = onDeleteInteraction,
            )
        }

        Text(
            text = "我们之间的重要片段",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (timeline.isEmpty()) {
            Text(
                text = "还没有真正改变我们关系的片段。普通寒暄和聊天次数不会被拿来假装关系升级。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            timeline.forEach { item ->
                RelationshipTimelineEntry(
                    item = item,
                    onDelete = { onDeleteTimelineEvent(item.id) },
                )
            }
        }

        privateImpression.unresolvedMatters.takeIf(List<String>::isNotEmpty)?.let { matters ->
            RelationshipTextCard(
                eyebrow = "还没有说开的事",
                body = matters.takeLast(3).joinToString("；"),
                onDelete = onDeleteUnresolved,
                warning = true,
            )
        }
    }
}

@Composable
private fun RelationshipTextCard(
    eyebrow: String,
    body: String,
    heading: String = "",
    onDelete: (() -> Unit)? = null,
    warning: Boolean = false,
) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = eyebrow,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                onDelete?.let { delete ->
                    IconButton(onClick = delete) {
                        Icon(
                            imageVector = HugeIcons.Delete01,
                            contentDescription = "删除$eyebrow",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            heading.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun FavoriteCard(
    favorite: CompanionFavorite,
    onDelete: () -> Unit,
) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "收藏的消息",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = HugeIcons.Delete01,
                        contentDescription = "删除这条收藏",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text("为什么留下：${favorite.reason}", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "当时的感受：${favorite.feeling}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "消息 ID：${favorite.messageId} · ${formatTime(favorite.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LifeEventCard(
    event: CompanionLifeEvent,
    onDelete: () -> Unit,
) {
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
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = HugeIcons.Delete01,
                        contentDescription = "删除这条生活记录",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
    me.rerere.rikkahub.data.companion.CompanionLifeEventType.UNSENT_NOTE -> "未发送便签"
    me.rerere.rikkahub.data.companion.CompanionLifeEventType.FAVORITE_ORGANIZATION -> "整理收藏"
    me.rerere.rikkahub.data.companion.CompanionLifeEventType.EXPERIENCE_REVIEW -> "回顾经历"
    me.rerere.rikkahub.data.companion.CompanionLifeEventType.CONCERN_ORGANIZATION -> "整理关注"
    me.rerere.rikkahub.data.companion.CompanionLifeEventType.REPLAY_REVIEW -> "观看回放"
    me.rerere.rikkahub.data.companion.CompanionLifeEventType.SHARED_PLAN -> "共同计划"
    me.rerere.rikkahub.data.companion.CompanionLifeEventType.COMMITMENT_REVIEW -> "承诺复盘"
    me.rerere.rikkahub.data.companion.CompanionLifeEventType.STATE_REVIEW -> "状态整理"
    me.rerere.rikkahub.data.companion.CompanionLifeEventType.WAITING -> "自主等待"
}

@Composable
private fun RelationshipTimelineEntry(
    item: CompanionRelationshipTimelineItem,
    onDelete: () -> Unit,
) {
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
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = HugeIcons.Delete01,
                        contentDescription = "删除这段关系记录",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item.detail?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
