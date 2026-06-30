package me.rerere.rikkahub.ui.pages.starwish

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiImage
import me.rerere.hugeicons.stroke.AiMagic
import me.rerere.hugeicons.stroke.BookOpen02
import me.rerere.hugeicons.stroke.CircleLock01
import me.rerere.hugeicons.stroke.Image03
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Play
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.starwish.StarWishGeneratedImage
import me.rerere.rikkahub.data.starwish.StarWishImageLaunch
import me.rerere.rikkahub.data.starwish.StarWishOutfitPrompts
import me.rerere.rikkahub.data.starwish.StarWishRules
import me.rerere.rikkahub.data.starwish.StarWishScroll
import me.rerere.rikkahub.data.study.StudyRules
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

private enum class StarWishSection(val label: String) {
    Scrolls("画卷"),
    Theaters("小剧场"),
}

private enum class ScrollSubsection(val label: String) {
    Images("图片"),
    Prompts("提示词"),
}

@Composable
fun StarWishPage(vm: StarWishVM = koinViewModel()) {
    val navController = LocalNavController.current
    val state by vm.state.collectAsStateWithLifecycle()
    val generatedImages by vm.generatedImages.collectAsStateWithLifecycle()
    val studyState by vm.studyState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var section by remember { mutableStateOf(StarWishSection.Scrolls) }
    var scrollSubsection by remember { mutableStateOf(ScrollSubsection.Prompts) }
    var selectedScroll by remember { mutableStateOf<StarWishScroll?>(null) }
    var selectedTheater by remember { mutableStateOf<String?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("星愿馆") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = StarWishColors.paper,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(StarWishColors.paper),
            contentPadding = padding + PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { StarWishHero(section = section, onSection = { section = it }) }
            when (section) {
                StarWishSection.Scrolls -> {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ScrollSubsection.entries.forEach {
                                FilterChip(
                                    selected = scrollSubsection == it,
                                    onClick = { scrollSubsection = it },
                                    label = { Text(it.label) },
                                )
                            }
                        }
                    }
                    when (scrollSubsection) {
                        ScrollSubsection.Images -> {
                            if (state.imageLaunches.isEmpty()) {
                                item {
                                    StarWishEmptyCard(
                                        title = "还没有画卷图片",
                                        subtitle = "点亮任意套装后，在提示词里点“生成双图”，这里会留下从星愿馆发起的记录。",
                                        icon = HugeIcons.Image03,
                                        onClick = { scrollSubsection = ScrollSubsection.Prompts },
                                    )
                                }
                            } else {
                                item {
                                    Button(onClick = vm::refreshGeneratedImages, modifier = Modifier.fillMaxWidth()) {
                                        Text("刷新图片")
                                    }
                                }
                                if (generatedImages.isNotEmpty()) {
                                    items(generatedImages) { image ->
                                        StarWishGeneratedImageRow(image = image)
                                    }
                                } else {
                                    items(state.imageLaunches) { launch ->
                                        StarWishImageLaunchRow(launch = launch)
                                    }
                                }
                                item {
                                    StarWishEmptyCard(
                                        title = "查看图片库",
                                        subtitle = "已生成出的图片会保存在生图页图库里。",
                                        icon = HugeIcons.AiImage,
                                        onClick = { navController.navigate(Screen.ImageGen()) },
                                    )
                                }
                            }
                        }
                        ScrollSubsection.Prompts -> {
                            items(StarWishRules.scrolls) { scroll ->
                                val index = StarWishRules.scrolls.indexOf(scroll)
                                val outfit = StudyRules.outfitNames.getOrNull(index) ?: scroll.title
                                val unlocked = StarWishRules.isScrollUnlocked(studyState, scroll)
                                val launches = state.imageLaunches.count { it.outfit == outfit }
                                StarWishListRow(
                                    title = scroll.title,
                                    subtitle = if (unlocked) "已点亮 · 已生成 $launches 次" else "未解锁 · 去考研 App 收集完整套装",
                                    unlocked = unlocked,
                                    progress = outfitProgress(outfit, studyState.inventory.normalFragments),
                                    icon = HugeIcons.AiImage,
                                    onClick = { if (unlocked) selectedScroll = scroll },
                                )
                            }
                        }
                    }
                }
                StarWishSection.Theaters -> {
                    items(StarWishRules.theaters) { theater ->
                        val unlocked = StarWishRules.isTheaterUnlocked(studyState, theater)
                        val credits = StarWishRules.chapterCredits(studyState, theater)
                        val chapters = state.theaterChapters[theater].orEmpty().size
                        StarWishListRow(
                            title = theater,
                            subtitle = if (unlocked) "已点亮 · 章节 $chapters/$credits" else "未解锁 · 收集 5 枚同名剧场碎片",
                            unlocked = unlocked,
                            progress = ((studyState.inventory.rareFragments["rare:$theater"] ?: 0).coerceAtMost(5)) / 5f,
                            icon = HugeIcons.BookOpen02,
                            onClick = { if (unlocked) selectedTheater = theater },
                        )
                    }
                }
            }
        }
    }

    selectedScroll?.let { scroll ->
        val index = StarWishRules.scrolls.indexOf(scroll)
        val outfit = StudyRules.outfitNames.getOrNull(index) ?: scroll.title
        val saved = state.customOutfitPrompts[outfit]
        val prompts = saved ?: StarWishOutfitPrompts(scroll.soloPrompt, scroll.interactionPrompt)
        ScrollDetailDialog(
            scroll = scroll,
            outfit = outfit,
            prompts = prompts,
            launches = state.imageLaunches.filter { it.outfit == outfit },
            onDismiss = { selectedScroll = null },
            onSave = { vm.savePrompts(outfit, it) },
            onCopy = {
                clipboard.setText(AnnotatedString(it))
                scope.launch { snackbarHostState.showSnackbar("提示词已复制") }
            },
            onGenerate = {
                val prompt = prompts.solo + "\n\n互动版：\n" + prompts.interaction
                vm.recordImageLaunch(outfit, prompt)
                selectedScroll = null
                navController.navigate(Screen.ImageGen(initialPrompt = prompt, count = 2, autoGenerate = true))
            },
        )
    }

    selectedTheater?.let { theater ->
        val credits = StarWishRules.chapterCredits(studyState, theater)
        val chapters = state.theaterChapters[theater].orEmpty()
        TheaterDetailDialog(
            theater = theater,
            credits = credits,
            chapters = chapters,
            onDismiss = { selectedTheater = null },
            onCreateChapter = { vm.createNextChapter(theater) },
        )
    }
}

@Composable
private fun StarWishHero(section: StarWishSection, onSection: (StarWishSection) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.76f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(StarWishHeroBrush())
                .padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("把学习里抽到的愿望，收进这里。", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("画卷保存套装提示词与生图入口；小剧场保存已解锁剧情和续写资格。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StarWishSection.entries.forEach {
                        FilterChip(selected = section == it, onClick = { onSection(it) }, label = { Text(it.label) })
                    }
                }
            }
        }
    }
}

@Composable
private fun StarWishListRow(
    title: String,
    subtitle: String,
    unlocked: Boolean,
    progress: Float,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = unlocked, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (unlocked) Color.White.copy(alpha = 0.86f) else StarWishColors.locked,
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (unlocked) StarWishColors.mistBlue else Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (unlocked) icon else HugeIcons.CircleLock01,
                            contentDescription = null,
                            tint = if (unlocked) StarWishColors.inkBlue else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (unlocked) "进入" else "锁定", style = MaterialTheme.typography.labelMedium, color = if (unlocked) StarWishColors.inkBlue else MaterialTheme.colorScheme.outline)
            }
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StarWishImageLaunchRow(launch: StarWishImageLaunch) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.86f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = StarWishColors.mistBlue,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = HugeIcons.Image03,
                        contentDescription = null,
                        tint = StarWishColors.inkBlue,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(launch.outfit, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("已发起双图生成 · ${launch.createdAt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StarWishGeneratedImageRow(image: StarWishGeneratedImage) {
    var showPreview by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.86f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = File(image.filePath),
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showPreview = true },
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(image.outfit, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("已生成图片 · ${image.createdAt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(image.prompt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (showPreview) {
        ImagePreviewDialog(
            images = listOf(image.filePath),
            onDismissRequest = { showPreview = false },
        )
    }
}

@Composable
private fun StarWishEmptyCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.78f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = StarWishColors.mistBlue,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = StarWishColors.inkBlue, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ScrollDetailDialog(
    scroll: StarWishScroll,
    outfit: String,
    prompts: StarWishOutfitPrompts,
    launches: List<me.rerere.rikkahub.data.starwish.StarWishImageLaunch>,
    onDismiss: () -> Unit,
    onSave: (StarWishOutfitPrompts) -> Unit,
    onCopy: (String) -> Unit,
    onGenerate: () -> Unit,
) {
    var solo by remember(outfit) { mutableStateOf(prompts.solo) }
    var interaction by remember(outfit) { mutableStateOf(prompts.interaction) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onGenerate) {
                Icon(HugeIcons.AiMagic, null)
                Spacer(Modifier.width(8.dp))
                Text("生成双图")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("收起") }
        },
        title = { Text(scroll.title) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(520.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { onCopy(solo) }, label = { Text("复制独美") }, leadingIcon = { Icon(HugeIcons.PencilEdit01, null) })
                        AssistChip(onClick = { onCopy(interaction) }, label = { Text("复制互动") }, leadingIcon = { Icon(HugeIcons.Image03, null) })
                    }
                }
                item {
                    OutlinedTextField(
                        value = solo,
                        onValueChange = {
                            solo = it
                            onSave(StarWishOutfitPrompts(solo, interaction))
                        },
                        label = { Text("独美版提示词") },
                        minLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = interaction,
                        onValueChange = {
                            interaction = it
                            onSave(StarWishOutfitPrompts(solo, interaction))
                        },
                        label = { Text("互动版提示词") },
                        minLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text("图片记录", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (launches.isEmpty()) {
                        Text("还没有从星愿馆发起过生成。点击生成双图后，会跳到生图页并预填提示词。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        launches.take(5).forEach {
                            Text("· ${it.outfit} · ${it.createdAt}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun TheaterDetailDialog(
    theater: String,
    credits: Int,
    chapters: List<me.rerere.rikkahub.data.starwish.StarWishTheaterChapter>,
    onDismiss: () -> Unit,
    onCreateChapter: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onCreateChapter, enabled = chapters.size < credits) {
                Icon(HugeIcons.Play, null)
                Spacer(Modifier.width(8.dp))
                Text(if (chapters.isEmpty()) "生成第一章" else "续写下一章")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("收起") } },
        title = { Text(theater) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("章节资格 $credits 次 · 已生成 ${chapters.size} 章", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (chapters.isEmpty()) {
                    Text("解锁后可以生成第一章；之后每满 5 枚同名剧场碎片，就获得一次续写资格。")
                }
                chapters.forEach {
                    Surface(color = StarWishColors.mistBlue.copy(alpha = 0.55f), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(it.title, fontWeight = FontWeight.SemiBold)
                            Text(it.content, maxLines = 4, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
    )
}

private fun outfitProgress(outfit: String, fragments: Map<String, Int>): Float {
    val count = StudyRules.outfitParts.sumOf { part -> (fragments["normal:$outfit:$part"] ?: 0).coerceAtMost(4) }
    return count / 24f
}

private object StarWishColors {
    val paper = Color(0xFFF4F7F8)
    val mistBlue = Color(0xFFDDECF2)
    val inkBlue = Color(0xFF2E596B)
    val locked = Color(0xFFE7EAEC)
}

private fun StarWishHeroBrush(): Brush = Brush.linearGradient(
    listOf(Color(0xFFEAF3F6), Color(0xFFF8FAF7), Color(0xFFE9E5F1))
)
