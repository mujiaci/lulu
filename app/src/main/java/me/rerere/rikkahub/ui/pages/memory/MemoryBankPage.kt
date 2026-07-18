package me.rerere.rikkahub.ui.pages.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.DatabaseSync
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.data.db.entity.MemoryBankEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.service.MemoryBankService
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.utils.JsonInstant
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemoryBankPage(
    onBack: () -> Unit,
    onOpenSource: (conversationId: String, nodeId: String?) -> Unit,
) {
    val vm: MemoryBankVM = koinViewModel()
    val memories by vm.memories.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val selectedType by vm.selectedType.collectAsStateWithLifecycle()
    val selectedAssistantId by vm.selectedAssistantId.collectAsStateWithLifecycle()
    val assistantIds by vm.assistantIds.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val maintenanceMessage by vm.maintenanceMessage.collectAsStateWithLifecycle()
    val reorganizationProgress by vm.reorganizationProgress.collectAsStateWithLifecycle()
    val embeddingModels = remember(settings.providers) { vm.embeddingModels(settings) }
    val assistantLabels = remember(assistantIds, settings.assistants) {
        buildMemoryAssistantLabels(assistantIds, settings.assistants)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showDeleteDialog by remember { mutableStateOf<MemoryBankEntity?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var editMemory by remember { mutableStateOf<MemoryBankEntity?>(null) }
    var correctionMemory by remember { mutableStateOf<MemoryBankEntity?>(null) }
    var showFullRebuildDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text("记忆库")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(HugeIcons.ArrowLeft01, contentDescription = "返回")
                    }
                },
                actions = {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    IconButton(onClick = { vm.rebuildIndex() }) {
                        Icon(HugeIcons.Database02, contentDescription = "重建向量索引")
                    }
                    IconButton(onClick = { vm.processPendingVectors() }) {
                        Icon(HugeIcons.DatabaseSync, contentDescription = "处理待向量化记忆")
                    }
                    IconButton(onClick = { vm.runLightMaintenance() }) {
                        Icon(HugeIcons.Tools, contentDescription = "轻量维护")
                    }
                    IconButton(onClick = { showClearAllDialog = true }) {
                        Icon(HugeIcons.Delete02, contentDescription = "清除长期记忆")
                    }
                    IconButton(onClick = { vm.loadMemories() }) {
                        Icon(HugeIcons.Refresh01, contentDescription = "刷新")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 统计卡片 - 消息只显示条数
            item {
                StatsRow(stats)
            }

            maintenanceMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            item {
                MemoryEmbeddingConfigCard(
                    enabled = settings.memoryEmbeddingConfig.enabled,
                    selectedModelId = settings.memoryEmbeddingConfig.modelId,
                    dimensions = settings.memoryEmbeddingConfig.dimensions,
                    models = embeddingModels,
                    onEnabledChange = vm::setMemoryEmbeddingEnabled,
                    onModelSelected = vm::setMemoryEmbeddingModel,
                    onDimensionsChange = vm::setMemoryEmbeddingDimensions,
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("整理聊天记忆", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "每次只处理一个完整批次，最新 10 条不会被抽走，也不会重复整理已经完成的批次。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                enabled = !reorganizationProgress.running,
                                onClick = vm::repairMemoriesFromHistory,
                            ) {
                                Icon(HugeIcons.Refresh01, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("整理最近一批")
                            }
                            TextButton(
                                enabled = !reorganizationProgress.running,
                                onClick = vm::continueHistoricalMemoryRepair,
                            ) {
                                Text("继续补齐旧记录")
                            }
                            TextButton(
                                enabled = !reorganizationProgress.running,
                                onClick = { showFullRebuildDialog = true },
                            ) {
                                Text("完整重建")
                            }
                        }
                        if (reorganizationProgress.message.isNotBlank()) {
                            Text(
                                text = reorganizationProgress.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (reorganizationProgress.failedBatches > 0) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                        if (reorganizationProgress.running) {
                            Text(
                                "进度：${reorganizationProgress.currentConversation}/${reorganizationProgress.totalConversations} 个对话，已完成 ${reorganizationProgress.completedBatches} 批",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("查看哪个角色的记忆", style = MaterialTheme.typography.titleSmall)
                    AssistantFilterRow(
                        selectedAssistantId = selectedAssistantId,
                        assistantIds = assistantIds,
                        assistantLabels = assistantLabels,
                        onAssistantSelected = { vm.setSelectedAssistantId(it) },
                    )
                }
            }

            item {
                MemoryBankLegend()
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 类型筛选
            item {
                TypeFilterRow(
                    selectedType = selectedType,
                    onTypeSelected = { vm.setSelectedType(it) }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { vm.setSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索记忆...") },
                    leadingIcon = {
                        Icon(HugeIcons.Search01, contentDescription = null)
                    },
                    singleLine = true,
                )
            }

            items(memories, key = { it.id }) { memory ->
                MemoryCard(
                    memory = memory,
                    assistantLabels = assistantLabels,
                    onDelete = { showDeleteDialog = memory },
                    onOpenSource = onOpenSource,
                    onEdit = { editMemory = memory },
                    onTogglePinned = { vm.setPinned(memory, !memory.pinned) },
                    onCorrect = { correctionMemory = memory },
                )
            }

            if (memories.isEmpty() && !loading) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("这个角色还没有长期记忆", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "系统只保存明确偏好、边界、纠正、承诺和重要共同事件；普通寒暄不会为了凑数写进来。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = vm::repairMemoriesFromHistory) {
                                Icon(HugeIcons.Refresh01, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("整理最近一批")
                            }
                        }
                    }
                }
            }
        }
    }

    // 删除确认对话框
    showDeleteDialog?.let { memory ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("删除记忆") },
            text = { Text("确定要删除这条记忆吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMemory(memory.id)
                    showDeleteDialog = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (showFullRebuildDialog) {
        AlertDialog(
            onDismissRequest = { showFullRebuildDialog = false },
            title = { Text("完整重建记忆？") },
            text = {
                Text("这会重新扫描当前筛选角色的全部完整批次，用于修复旧版本的时间和投影错误。已有聊天不会被删除，但可能调用多次模型。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showFullRebuildDialog = false
                    vm.rebuildAllHistoricalMemories()
                }) { Text("开始重建") }
            },
            dismissButton = {
                TextButton(onClick = { showFullRebuildDialog = false }) { Text("取消") }
            },
        )
    }

    if (showClearAllDialog) {
        val clearingAll = selectedAssistantId == null
        val selectedName = selectedAssistantId?.let { assistantLabels[it] } ?: "当前角色"
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text(if (clearingAll) "清除全部长期记忆" else "清除角色长期记忆") },
            text = {
                Text(
                    if (clearingAll) {
                        "将不可撤销地删除记忆库中的全部长期记忆。角色人设、世界书、聊天记录和考研计划不会受影响。"
                    } else {
                        "将不可撤销地删除“$selectedName”的全部长期记忆。角色人设、世界书、聊天记录和考研计划不会受影响。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearLongTermMemories()
                    showClearAllDialog = false
                }) {
                    Text("清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    editMemory?.let { memory ->
        EditMemoryDialog(
            memory = memory,
            onDismiss = { editMemory = null },
            onConfirm = {
                vm.updateMemory(it)
                editMemory = null
            },
        )
    }

    correctionMemory?.let { memory ->
        CorrectMemoryDialog(
            memory = memory,
            onDismiss = { correctionMemory = null },
            onConfirm = { reason, supersededBy ->
                vm.markDeprecated(memory, reason, supersededBy)
                correctionMemory = null
            },
        )
    }
}

@Composable
private fun EditMemoryDialog(
    memory: MemoryBankEntity,
    onDismiss: () -> Unit,
    onConfirm: (MemoryBankEntity) -> Unit,
) {
    var content by remember(memory.id) { mutableStateOf(memory.content) }
    var title by remember(memory.id) { mutableStateOf(memory.title.orEmpty()) }
    var memoryKind by remember(memory.id) { mutableStateOf(memory.memoryKind.orEmpty()) }
    var importance by remember(memory.id) { mutableStateOf(memory.importance.toString()) }
    var confidence by remember(memory.id) { mutableStateOf("%.2f".format(memory.confidence)) }
    var tagsJson by remember(memory.id) { mutableStateOf(memory.tagsJson.orEmpty()) }
    var embeddingText by remember(memory.id) { mutableStateOf(memory.embeddingText.orEmpty()) }
    val contentChanged = content != memory.content || embeddingText != memory.embeddingText.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑记忆") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("内容") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = memoryKind,
                    onValueChange = { memoryKind = it },
                    label = { Text("记忆类型") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = importance,
                        onValueChange = { importance = it },
                        label = { Text("重要度 1-5") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = confidence,
                        onValueChange = { confidence = it },
                        label = { Text("可信度 0-1") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = tagsJson,
                    onValueChange = { tagsJson = it },
                    label = { Text("标签 JSON") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = embeddingText,
                    onValueChange = { embeddingText = it },
                    label = { Text("向量文本") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = content.isNotBlank(),
                onClick = {
                    val updated = memory.copy(
                        content = content.trim(),
                        title = title.trim().takeIf { it.isNotBlank() },
                        memoryKind = memoryKind.trim().takeIf { it.isNotBlank() },
                        importance = importance.toIntOrNull()?.coerceIn(1, 5) ?: memory.importance,
                        confidence = confidence.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: memory.confidence,
                        tagsJson = tagsJson.trim().takeIf { it.isNotBlank() },
                        embeddingText = embeddingText.trim().takeIf { it.isNotBlank() },
                        embeddingVectorJson = if (contentChanged) null else memory.embeddingVectorJson,
                        embeddingModelId = if (contentChanged) null else memory.embeddingModelId,
                        embeddingDimensions = if (contentChanged) null else memory.embeddingDimensions,
                        vectorStatus = if (contentChanged) "pending" else memory.vectorStatus,
                        vectorRetryCount = if (contentChanged) 0 else memory.vectorRetryCount,
                    )
                    onConfirm(updated)
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun CorrectMemoryDialog(
    memory: MemoryBankEntity,
    onDismiss: () -> Unit,
    onConfirm: (reason: String, supersededByMemoryId: String?) -> Unit,
) {
    var reason by remember(memory.id) { mutableStateOf(memory.deprecatedReason.orEmpty()) }
    var supersededBy by remember(memory.id) { mutableStateOf(memory.supersededByMemoryId.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修正记忆") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = memory.content,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("失效原因") },
                    placeholder = { Text("例如：用户已澄清、重复记忆、旧设定失效") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = supersededBy,
                    onValueChange = { supersededBy = it },
                    label = { Text("修正为的记忆 ID（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        reason.ifBlank { "manual_correction" },
                        supersededBy.trim().takeIf { it.isNotBlank() },
                    )
                },
            ) {
                Text("标记失效")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun MemoryEmbeddingConfigCard(
    enabled: Boolean,
    selectedModelId: kotlin.uuid.Uuid?,
    dimensions: Int?,
    models: List<me.rerere.ai.provider.Model>,
    onEnabledChange: (Boolean) -> Unit,
    onModelSelected: (kotlin.uuid.Uuid?) -> Unit,
    onDimensionsChange: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("记忆向量化", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "使用 Provider 里的 Embedding 模型处理待向量化记忆",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }

            if (models.isEmpty()) {
                Text(
                    text = "暂无 Embedding 模型。请先在 Provider 设置里新增模型并选择 Embedding 类型。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                val options = listOf<me.rerere.ai.provider.Model?>(null) + models
                val selectedModel = models.firstOrNull { it.id == selectedModelId }
                Select(
                    options = options,
                    selectedOption = selectedModel,
                    onOptionSelected = { model -> onModelSelected(model?.id) },
                    optionToString = { model -> model?.displayName?.ifBlank { model.modelId } ?: "未选择模型" },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = dimensions?.toString().orEmpty(),
                onValueChange = onDimensionsChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("向量维度（可选）") },
                singleLine = true,
            )
        }
    }
}

@Composable
private fun StatsRow(stats: MemoryBankService.MemoryStats) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard("总计", stats.total, Modifier.weight(1f))
        StatCard("消息", stats.messageCount, Modifier.weight(1f))
        StatCard("固定", stats.manualCount, Modifier.weight(1f))
        StatCard("失效", stats.deprecatedCount, Modifier.weight(1f), MaterialTheme.colorScheme.surfaceVariant)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard("已向量化", stats.vectorizedCount, Modifier.weight(1f), MaterialTheme.colorScheme.primaryContainer)
        StatCard("待处理", stats.pendingCount, Modifier.weight(1f), MaterialTheme.colorScheme.tertiaryContainer)
        StatCard("失败", stats.failedCount, Modifier.weight(1f), MaterialTheme.colorScheme.errorContainer)
    }

}

@Composable
private fun MemoryBankLegend() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("按钮：重建向量索引 / 处理待向量化 / 轻量维护合并重复 / 刷新列表", style = MaterialTheme.typography.bodySmall)
            Text("分类：消息来自对话抽取；手动是你或插件主动保存的记忆；失效是被替换或废弃的旧记忆。", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatCard(label: String, count: Int, modifier: Modifier = Modifier, containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(count.toString(), style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantFilterRow(
    selectedAssistantId: String?,
    assistantIds: List<String>,
    assistantLabels: Map<String, String>,
    onAssistantSelected: (String?) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedAssistantId == null,
            onClick = { onAssistantSelected(null) },
            label = { Text("全部角色") },
        )
        assistantIds.forEach { id ->
            FilterChip(
                selected = selectedAssistantId == id,
                onClick = { onAssistantSelected(id) },
                label = { Text(assistantLabels[id] ?: id.shortAssistantId()) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeFilterRow(
    selectedType: String,
    onTypeSelected: (String) -> Unit,
) {
    val types = listOf(
        "" to "全部",
        "message" to "消息",
        "manual" to "固定记忆",
        "deprecated" to "失效",
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        types.forEach { (value, label) ->
            FilterChip(
                selected = selectedType == value,
                onClick = { onTypeSelected(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun MemoryCard(
    memory: MemoryBankEntity,
    assistantLabels: Map<String, String>,
    onDelete: () -> Unit,
    onOpenSource: (conversationId: String, nodeId: String?) -> Unit,
    onEdit: () -> Unit,
    onTogglePinned: () -> Unit,
    onCorrect: () -> Unit,
) {
    val sourceNodeId = remember(memory.sourceMessageNodeIdsJson) { memory.firstSourceNodeId() }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (memory.vectorStatus) {
                "done" -> MaterialTheme.colorScheme.surface
                "pending" -> MaterialTheme.colorScheme.tertiaryContainer
                "failed" -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = when (memory.type) {
                            "message" -> MaterialTheme.colorScheme.primaryContainer
                            "manual" -> MaterialTheme.colorScheme.inversePrimary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = when (memory.type) {
                                "message" -> "消息"
                                "manual" -> "固定记忆"
                                else -> memory.type
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }

                    if (memory.vectorStatus == "done") {
                        Icon(
                            HugeIcons.Database02,
                            contentDescription = "已向量化",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    if (memory.deprecated) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.extraSmall,
                        ) {
                            Text(
                                text = "已失效",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }

                    if (memory.pinned) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.extraSmall,
                        ) {
                            Text(
                                text = "置顶",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }

                    val displayTime = memory.occurredAt ?: memory.createdAt
                    val timeStr = remember(displayTime) {
                        SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                            .format(Date(displayTime))
                    }
                    Text(
                        text = "发生 $timeStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Show assistant ID if available
                    if (memory.assistantId != null) {
                        Text(
                            text = assistantLabels[memory.assistantId] ?: memory.assistantId.shortAssistantId(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = memory.content,
                    style = MaterialTheme.typography.bodySmall,
                )

                Spacer(modifier = Modifier.height(6.dp))

                MemoryMetaInfo(memory)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (memory.conversationId != null) {
                    TextButton(onClick = { onOpenSource(memory.conversationId, sourceNodeId) }) {
                        Text("原文")
                    }
                }
                TextButton(onClick = onTogglePinned) {
                    Text(if (memory.pinned) "取消置顶" else "置顶")
                }
                if (!memory.deprecated) {
                    TextButton(onClick = onCorrect) {
                        Text("修正")
                    }
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        HugeIcons.PencilEdit01,
                        contentDescription = "编辑",
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        HugeIcons.Delete02,
                        contentDescription = "删除",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryMetaInfo(memory: MemoryBankEntity) {
    val chips = buildList {
        memory.memoryKind?.takeIf { it.isNotBlank() }?.let { add("类型：$it") }
        add("重要度：${memory.importance}")
        add("可信度：${"%.2f".format(memory.confidence)}")
        if (memory.recallCount > 0) add("召回：${memory.recallCount}")
        memory.lastRecalledAt?.let { add("上次召回：${formatShortTime(it)}") }
        memory.sourceMessageAt?.let { add("得知于：${formatShortTime(it)}") }
        memory.extractedAt.takeIf { it > 0L }?.let { add("整理于：${formatShortTime(it)}") }
        memory.relatedMemoryIdsJson?.takeIf { it.isNotBlank() && it != "[]" }?.let { add("关联：$it") }
        memory.sourceMessageNodeIdsJson?.takeIf { it.isNotBlank() && it != "[]" }?.let { add("来源：$it") }
        memory.evidenceMessageNodeIdsJson?.takeIf { it.isNotBlank() && it != "[]" }?.let { add("证据：$it") }
        memory.supersededByMemoryId?.takeIf { it.isNotBlank() }?.let { add("修正为：#$it") }
        memory.deprecatedReason?.takeIf { it.isNotBlank() }?.let { add("原因：$it") }
        memory.correctedAt?.let { add("修正时间：${formatShortTime(it)}") }
    }
    if (chips.isEmpty()) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        chips.forEach { chip ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.extraSmall,
            ) {
                Text(
                    text = chip,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatShortTime(timestamp: Long): String =
    SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun MemoryBankEntity.firstSourceNodeId(): String? =
    runCatching {
        JsonInstant.decodeFromString<List<String>>(sourceMessageNodeIdsJson.orEmpty()).firstOrNull()
    }.getOrNull()

internal fun buildMemoryAssistantLabels(
    assistantIds: List<String>,
    assistants: List<Assistant>,
): Map<String, String> {
    val assistantNames = assistants.associate { assistant ->
        assistant.id.toString() to assistant.name.trim()
    }
    return assistantIds.associateWith { id ->
        assistantNames[id]?.takeIf { it.isNotBlank() } ?: id.shortAssistantId()
    }
}

private fun String.shortAssistantId(): String = take(8)
