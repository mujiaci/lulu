package me.rerere.rikkahub.ui.pages.cihai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.document.DocxParser
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.rikkahub.data.cihai.CihaiBook
import me.rerere.rikkahub.data.cihai.CihaiService
import me.rerere.rikkahub.data.cihai.CihaiStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject

@Composable
fun CihaiReadingPage(onBack: () -> Unit) {
    val settings = LocalSettings.current
    val store = koinInject<CihaiStore>()
    val service = koinInject<CihaiService>()
    val state by store.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val fallbackAssistant = settings.getCurrentAssistant()
    val selectedAssistantId = state.selectedAssistantId
        .takeIf { id -> settings.assistants.any { it.id.toString() == id } }
        ?: fallbackAssistant.id.toString()
    val books = state.books.filter { it.assistantId == selectedAssistantId }
    var selectedBookId by remember { mutableStateOf<String?>(null) }
    var selectedPage by remember { mutableIntStateOf(0) }
    var importError by remember { mutableStateOf("") }
    val selectedBook = books.firstOrNull { it.id == selectedBookId } ?: books.firstOrNull()
    val pages = remember(selectedBook?.id, selectedBook?.content) {
        selectedBook?.content.orEmpty().toBookPages()
    }
    val chapters = remember(selectedBook?.id, selectedBook?.content) {
        selectedBook?.content.orEmpty().detectChapterRanges()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importError = ""
            runCatching {
                val imported = withContext(Dispatchers.IO) {
                    context.readBookFromUri(uri, selectedAssistantId)
                }
                service.addBook(imported)
                selectedBookId = imported.id
                selectedPage = 0
            }.onFailure { error ->
                importError = error.message ?: "导入失败"
            }
        }
    }

    LaunchedEffect(selectedAssistantId) {
        if (state.selectedAssistantId != selectedAssistantId) {
            store.selectAssistant(selectedAssistantId)
        }
    }
    LaunchedEffect(selectedBook?.id, pages.size) {
        selectedPage = selectedPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    }

    Scaffold(containerColor = CustomColors.topBarColors.containerColor) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "阅读",
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
            }
            item {
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text("书架", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "上传 txt 或 docx；目录只负责跳转，正文可以连续下拉阅读。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Button(
                                onClick = {
                                    picker.launch(
                                        arrayOf(
                                            "text/plain",
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                        )
                                    )
                                },
                            ) {
                                Text("上传")
                            }
                        }
                        if (importError.isNotBlank()) {
                            Text(
                                text = importError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            if (books.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(books, key = { it.id }) { book ->
                            FilterChip(
                                selected = book.id == selectedBook?.id,
                                onClick = {
                                    selectedBookId = book.id
                                    selectedPage = 0
                                },
                                label = {
                                    Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                            )
                        }
                    }
                }
            }
            selectedBook?.let { book ->
                item {
                    ReadingBookSurface(
                        book = book,
                        pages = pages,
                        chapters = chapters,
                        selectedPage = selectedPage,
                        onPageSelect = { selectedPage = it },
                        onDelete = {
                            scope.launch {
                                store.deleteBook(book.id)
                                selectedBookId = null
                                selectedPage = 0
                            }
                        },
                        onRead = {
                            scope.launch { service.readBookAndRemember(book) }
                        },
                    )
                }
            } ?: item {
                Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
                    Text(
                        text = "还没有阅读材料。先上传一本 txt 或 docx。",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadingBookSurface(
    book: CihaiBook,
    pages: List<String>,
    chapters: List<ChapterRange>,
    selectedPage: Int,
    onPageSelect: (Int) -> Unit,
    onDelete: () -> Unit,
    onRead: () -> Unit,
) {
    val pageListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "进度 ${book.progressPercent}% · ${formatReadingTime(book.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(HugeIcons.Delete01, contentDescription = "删除")
                }
                Button(onClick = onRead, enabled = book.progressPercent < 100) {
                    Text(if (book.progressPercent < 100) "让角色读一段" else "已读完")
                }
            }
            if (chapters.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(chapters, key = { it.title + it.pageIndex }) { chapter ->
                        FilterChip(
                            selected = selectedPage == chapter.pageIndex,
                            onClick = {
                                onPageSelect(chapter.pageIndex)
                                scope.launch { pageListState.animateScrollToItem(chapter.pageIndex) }
                            },
                            label = { Text(chapter.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
            if (pages.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pageGroups(pages.size), key = { it.first }) { range ->
                        FilterChip(
                            selected = selectedPage + 1 in range,
                            onClick = {
                                val pageIndex = range.first - 1
                                onPageSelect(pageIndex)
                                scope.launch { pageListState.animateScrollToItem(pageIndex) }
                            },
                            label = { Text("${range.first}~${range.last}页") },
                        )
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                LazyColumn(
                    state = pageListState,
                    modifier = Modifier.fillMaxWidth().height(520.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(pages.size, key = { it }) { pageIndex ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "第 ${pageIndex + 1} 页 / 共 ${pages.size.coerceAtLeast(1)} 页",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = pages.getOrNull(pageIndex).orEmpty().ifBlank { "这一页没有内容。" },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ChapterRange(
    val title: String,
    val pageIndex: Int,
)

private fun Context.readBookFromUri(uri: Uri, assistantId: String): CihaiBook {
    val name = displayName(uri).ifBlank { "未命名阅读材料" }
    val mime = contentResolver.getType(uri).orEmpty()
    val content = when {
        name.endsWith(".docx", ignoreCase = true) ||
            mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> {
            val file = File.createTempFile("cihai-reading-", ".docx", cacheDir)
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                } ?: error("无法读取文件")
                readDocxWithTextFallback(file)
            } finally {
                file.delete()
            }
        }
        else -> contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("无法读取文件")
    }.trim()
    require(content.isNotBlank()) { "文件内容为空" }
    return CihaiBook(
        assistantId = assistantId,
        title = name.removeSuffix(".txt").removeSuffix(".docx"),
        content = content,
    )
}

private fun readDocxWithTextFallback(file: File): String =
    runCatching { DocxParser.parse(file) }.getOrElse { docxError ->
        file.readPlainTextFallback()
            ?: error(
                "docx 解析失败：请确认这是标准 .docx 文件；如果它是旧 .doc、微信/WPS 导出的非标准文档，" +
                    "请先另存为标准 .docx 或 .txt 后再上传。原因：${docxError.message}"
            )
    }

private fun File.readPlainTextFallback(): String? {
    val bytes = readBytes()
    if (bytes.isOleDocument() || bytes.isZipDocument()) return null
    return listOf(Charsets.UTF_8, Charset.forName("GB18030"), Charsets.UTF_16LE, Charsets.UTF_16BE)
        .asSequence()
        .map { charset -> bytes.toString(charset).trim('\uFEFF').trim() }
        .firstOrNull { it.looksLikeReadableText() }
}

private fun ByteArray.isZipDocument(): Boolean =
    size >= 4 && this[0] == 0x50.toByte() && this[1] == 0x4B.toByte()

private fun ByteArray.isOleDocument(): Boolean {
    val oleHeader = byteArrayOf(
        0xD0.toByte(),
        0xCF.toByte(),
        0x11,
        0xE0.toByte(),
        0xA1.toByte(),
        0xB1.toByte(),
        0x1A,
        0xE1.toByte(),
    )
    return size >= oleHeader.size && oleHeader.indices.all { this[it] == oleHeader[it] }
}

private fun String.looksLikeReadableText(): Boolean {
    if (isBlank()) return false
    val sample = take(4000)
    val replacementCount = sample.count { it == '\uFFFD' }
    val controlCount = sample.count { Character.isISOControl(it) && it != '\n' && it != '\r' && it != '\t' }
    if (replacementCount > sample.length / 20 || controlCount > sample.length / 20) return false
    val punctuation = "，。！？；：、,.!?;:()（）《》“”\"'[]【】-—… "
    val readableCount = sample.count { it.isLetterOrDigit() || it.isWhitespace() || it in punctuation }
    return readableCount >= sample.length * 3 / 5
}

private fun Context.displayName(uri: Uri): String {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return cursor.getString(index).orEmpty()
        }
    }
    return uri.lastPathSegment.orEmpty()
}

private fun String.toBookPages(pageSize: Int = 900): List<String> =
    trim()
        .chunked(pageSize)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .ifEmpty { listOf("") }

private fun String.detectChapterRanges(pageSize: Int = 900): List<ChapterRange> =
    lineSequence()
        .runningFold(0 to "") { acc, line -> acc.first + line.length + 1 to line }
        .drop(1)
        .mapNotNull { (offset, line) ->
            val title = line.trim().takeIf {
                it.matches(Regex("""^(第.{1,9}[章节回篇部].*|#{1,3}\s+.+)$"""))
            }
            title?.let { ChapterRange(it.removePrefix("#").trim().take(18), (offset / pageSize).coerceAtLeast(0)) }
        }
        .take(20)
        .toList()

private fun pageGroups(pageCount: Int): List<IntRange> =
    (1..pageCount).chunked(10).map { it.first()..it.last() }

private fun formatReadingTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
