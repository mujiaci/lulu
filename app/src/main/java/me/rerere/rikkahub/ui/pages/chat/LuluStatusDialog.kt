package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.companion.CompanionCommitment
import me.rerere.rikkahub.data.companion.CompanionCommitmentStatus
import me.rerere.rikkahub.data.companion.CompanionConcern
import me.rerere.rikkahub.data.companion.CompanionConcernStatus
import me.rerere.rikkahub.data.companion.cleanCompanionHumanText
import me.rerere.rikkahub.data.companion.CompanionSnapshot
import me.rerere.rikkahub.data.companion.CompanionStateHistoryEntry
import me.rerere.rikkahub.data.model.Assistant
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun LuluStatusDialog(
    assistant: Assistant,
    snapshot: CompanionSnapshot,
    onDismissRequest: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val state = snapshot.state
    val activeCommitments = snapshot.commitments
        .filter { it.status.isVisibleInStatus() }
        .take(3)
    // A scheduled follow-up is persisted as both a concern (for perception) and
    // a commitment (for execution).  The two records intentionally share a
    // subjectKey, but showing both cards makes one promise look duplicated.
    // Keep the executable commitment card in the status view and only show
    // concerns that do not already have a visible commitment counterpart.
    val visibleCommitmentSubjects = activeCommitments
        .map { it.subjectKey }
        .toSet()
    val activeConcerns = snapshot.concerns
        .filter { it.status == CompanionConcernStatus.ACTIVE }
        .filterNot { concern ->
            concern.subjectKey in visibleCommitmentSubjects || activeCommitments.any { commitment ->
                val concernGoal = concern.goal.cleanCompanionHumanText("")
                val sameHumanPromise = concernGoal.isNotBlank() &&
                    concernGoal == commitment.promise.cleanCompanionHumanText("")
                val nextPerceptionAt = concern.nextPerceptionAt
                val sameDueWindow = nextPerceptionAt != null &&
                    abs(commitment.dueAt - nextPerceptionAt) <= 2 * 60_000L
                sameHumanPromise && sameDueWindow
            }
        }
        .take(3)
    val stateChips = buildList {
        state.mood.takeIf(String::isNotBlank)?.let { add("心情" to it) }
        state.bodyState.takeIf(String::isNotBlank)?.let { add("身体" to it) }
        state.mindState.takeIf(String::isNotBlank)?.let { add("精神" to it) }
        state.activityMode.takeIf(String::isNotBlank)?.let { add("状态" to it) }
        snapshot.relationship.roleLabel.takeIf(String::isNotBlank)?.let { add("关系" to it) }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = assistant.name.ifBlank { "角色" },
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = state.statusText.ifBlank { "正在陪着你" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("现在") },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("历史") },
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (selectedTab == 0) {
                        item {
                            StatusSection(
                                title = "心里想着",
                                text = state.innerThought.ifBlank { "此刻还没有留下明确的心声。" },
                            )
                        }
                        if (stateChips.isNotEmpty()) {
                            item {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    stateChips.forEach { (label, value) ->
                                        StatusChip(label = label, value = value)
                                    }
                                }
                            }
                        }
                        state.selfScene.takeIf(String::isNotBlank)?.let { scene ->
                            item { StatusSection(title = "此刻", text = scene) }
                        }
                        if (activeConcerns.isNotEmpty()) {
                            item { SectionTitle("正在挂心") }
                            items(activeConcerns, key = { it.id }) { concern -> ConcernRow(concern) }
                        }
                        if (activeCommitments.isNotEmpty()) {
                            item { SectionTitle("答应你的事") }
                            items(activeCommitments, key = { it.id }) { commitment -> CommitmentRow(commitment) }
                        }
                        item {
                            val updatedAt = maxOf(snapshot.updatedAt, state.updatedAt)
                            Text(
                                text = formatStateTime(updatedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            )
                        }
                    } else if (snapshot.stateHistory.isEmpty()) {
                        item {
                            Text(
                                text = "还没有可回看的状态变化。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(
                            items = snapshot.stateHistory.asReversed(),
                            key = { it.id },
                        ) { entry ->
                            StateHistoryRow(entry)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun StateHistoryRow(entry: CompanionStateHistoryEntry) {
    val state = entry.state
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = state.statusText.ifBlank { state.activityMode.ifBlank { "一次状态变化" } },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        state.innerThought.takeIf(String::isNotBlank)?.let { thought ->
            Text(
                text = thought,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val details = listOfNotNull(
            state.mood.takeIf(String::isNotBlank)?.let { "心情：$it" },
            state.bodyState.takeIf(String::isNotBlank)?.let { "身体：$it" },
            state.mindState.takeIf(String::isNotBlank)?.let { "精神：$it" },
        )
        if (details.isNotEmpty()) {
            Text(
                text = details.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = formatAbsoluteTime(entry.recordedAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider()
    }
}

@Composable
private fun StatusSection(title: String, text: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SectionTitle(title)
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ConcernRow(concern: CompanionConcern) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = concern.event.cleanCompanionHumanText("正在继续留意这件事。"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            concern.goal.takeIf(String::isNotBlank)?.let { goal ->
                Text(
                    text = goal.cleanCompanionHumanText(""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            concern.nextPerceptionAt?.let { nextPerceptionAt ->
                Text(
                    text = formatNextPerception(nextPerceptionAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun CommitmentRow(commitment: CompanionCommitment) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = commitment.promise.cleanCompanionHumanText("我会在合适的时候再确认这件事。"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatCommitmentTime(commitment.dueAt),
                style = MaterialTheme.typography.labelSmall,
                color = if (commitment.dueAt <= System.currentTimeMillis()) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

@Composable
private fun StatusChip(label: String, value: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = "$label：$value",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

private fun CompanionCommitmentStatus.isVisibleInStatus(): Boolean = this in setOf(
    CompanionCommitmentStatus.PROPOSED,
    CompanionCommitmentStatus.ACTIVE,
    CompanionCommitmentStatus.DUE,
    CompanionCommitmentStatus.EXECUTING,
    CompanionCommitmentStatus.RETRY_SCHEDULED,
)

private fun formatStateTime(timeMillis: Long): String {
    if (timeMillis <= 0L) return "还没有状态记录"
    return "更新于 ${formatAbsoluteTime(timeMillis)}"
}

private fun formatNextPerception(timeMillis: Long): String = if (timeMillis <= System.currentTimeMillis()) {
    "正在等待下一次确认"
} else {
    "下次留意：${formatAbsoluteTime(timeMillis)}"
}

private fun formatCommitmentTime(timeMillis: Long): String = if (timeMillis <= System.currentTimeMillis()) {
    "等待她确认 · 原定 ${formatAbsoluteTime(timeMillis)}"
} else {
    "约定时间：${formatAbsoluteTime(timeMillis)}"
}

private fun formatAbsoluteTime(timeMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")
    return Instant.ofEpochMilli(timeMillis)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}
