package me.rerere.rikkahub.ui.pages.worldbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject

@Composable
fun WorldbookPage(
    onBack: () -> Unit,
    settingsStore: SettingsStore = koinInject(),
) {
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var editing by remember { mutableStateOf<Lorebook?>(null) }
    var creating by remember { mutableStateOf(false) }

    fun updateLorebooks(transform: (List<Lorebook>) -> List<Lorebook>) {
        scope.launch {
            settingsStore.update { current -> current.copy(lorebooks = transform(current.lorebooks)) }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("世界书") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(HugeIcons.ArrowLeft01, contentDescription = "返回")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(HugeIcons.Add01, contentDescription = "添加世界书")
            }
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (settings.lorebooks.isEmpty()) {
                item {
                    Text(
                        text = "还没有世界书。点击加号添加时代、历史、社会常态或交际规则。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
            }
            items(settings.lorebooks, key = { it.id }) { book ->
                Card(
                    onClick = { editing = book },
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(book.name.ifBlank { "未命名世界书" }, style = MaterialTheme.typography.titleMedium)
                            Text(
                                book.simpleContent().ifBlank { "暂无内容" }.take(120),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("全局", style = MaterialTheme.typography.labelSmall)
                            Switch(
                                checked = book.globalApply,
                                onCheckedChange = { checked ->
                                    updateLorebooks { books ->
                                        books.map { if (it.id == book.id) it.copy(globalApply = checked) else it }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    val dialogBook = editing ?: Lorebook().takeIf { creating }
    dialogBook?.let { book ->
        WorldbookEditorDialog(
            book = book,
            isNew = creating,
            onDismiss = {
                editing = null
                creating = false
            },
            onSave = { saved ->
                updateLorebooks { books ->
                    if (creating) books + saved else books.map { if (it.id == saved.id) saved else it }
                }
                editing = null
                creating = false
            },
            onDelete = {
                updateLorebooks { books -> books.filterNot { it.id == book.id } }
                editing = null
                creating = false
            },
        )
    }
}

@Composable
private fun WorldbookEditorDialog(
    book: Lorebook,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Lorebook) -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember(book.id) { mutableStateOf(book.name) }
    var content by remember(book.id) { mutableStateOf(book.simpleContent()) }
    var globalApply by remember(book.id) { mutableStateOf(book.globalApply) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "添加世界书" else "编辑世界书") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("世界设定") },
                    minLines = 6,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("全局应用")
                    Switch(checked = globalApply, onCheckedChange = { globalApply = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && content.isNotBlank(),
                onClick = { onSave(book.withSimpleContent(title.trim(), content.trim(), globalApply)) },
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (!isNew) {
                    TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

private fun Lorebook.simpleContent(): String =
    entries.firstOrNull { it.constantActive }?.content.orEmpty()

private fun Lorebook.withSimpleContent(title: String, content: String, global: Boolean): Lorebook {
    val existing = entries.firstOrNull { it.constantActive }
    val simpleEntry = (existing ?: PromptInjection.RegexInjection()).copy(
        name = title,
        enabled = true,
        priority = existing?.priority ?: 0,
        position = InjectionPosition.AFTER_SYSTEM_PROMPT,
        content = content,
        role = MessageRole.USER,
        keywords = emptyList(),
        constantActive = true,
    )
    return copy(
        name = title,
        enabled = true,
        globalApply = global,
        entries = entries.filterNot { it.id == existing?.id } + simpleEntry,
    )
}
