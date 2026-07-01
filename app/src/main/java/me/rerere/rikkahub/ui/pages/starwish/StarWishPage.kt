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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Favourite
import me.rerere.hugeicons.stroke.Image03
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Play
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.starwish.StarWishGeneratedImage
import me.rerere.rikkahub.data.starwish.StarWishImageLaunch
import me.rerere.rikkahub.data.starwish.StarWishOutfitPrompts
import me.rerere.rikkahub.data.starwish.StarWishRules
import me.rerere.rikkahub.data.starwish.StarWishScroll
import me.rerere.rikkahub.data.starwish.StarWishTheaterSeed
import me.rerere.rikkahub.data.study.StudyRules
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

private enum class StarWishSection(val label: String) {
    Scrolls("画卷"),
    Theaters("小剧场"),
    SpecialStories("特殊剧情"),
    Video("视频"),
}

private enum class ScrollSubsection(val label: String) {
    Images("图片"),
    Prompts("提示词"),
}

@Composable
fun StarWishPage(vm: StarWishVM = koinViewModel()) {
    val navController = LocalNavController.current
    val settings = LocalSettings.current
    val state by vm.state.collectAsStateWithLifecycle()
    val generatedImages by vm.generatedImages.collectAsStateWithLifecycle()
    val studyState by vm.studyState.collectAsStateWithLifecycle()
    val mcpDiagnostic by vm.mcpDiagnostic.collectAsStateWithLifecycle()
    val videoModelStatus by vm.videoModelStatus.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var section by remember { mutableStateOf(StarWishSection.Scrolls) }
    var scrollSubsection by remember { mutableStateOf(ScrollSubsection.Prompts) }
    var selectedScroll by remember { mutableStateOf<Pair<String, StarWishScroll>?>(null) }
    var showAddTheater by remember { mutableStateOf(false) }
    var showAddSpecialStory by remember { mutableStateOf(false) }
    val companionAssistant = remember(settings.assistants, settings.assistantId, studyState.selectedAssistantId) {
        val selected = studyState.selectedAssistantId
        settings.assistants.firstOrNull { it.id.toString() == selected }
            ?: settings.getCurrentAssistant()
    }
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
                                        subtitle = "点亮任意画卷后，在提示词里选择独美或互动生成，这里会留下从星愿馆发起的记录。",
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
                            items(StudyRules.outfitNames) { outfit ->
                                val scroll = StarWishRules.scrollForOutfit(outfit)
                                val unlocked = StarWishRules.scrollUnlockedForOutfit(studyState, outfit)
                                val launches = state.imageLaunches.count { it.outfit == outfit }
                                StarWishListRow(
                                    title = outfit,
                                    subtitle = if (unlocked) "已点亮 · 已生成 $launches 次" else "未解锁 · 去考研 App 收集完整套装",
                                    unlocked = unlocked,
                                    progress = outfitProgress(outfit, studyState.inventory.normalFragments),
                                    icon = HugeIcons.AiImage,
                                    onClick = { if (unlocked) selectedScroll = outfit to scroll },
                                )
                            }
                        }
                    }
                }
                StarWishSection.Theaters -> {
                    item {
                        TheaterWalletCard(
                            rareFragments = studyState.inventory.universalRareFragments,
                            onAdd = { showAddTheater = true },
                        )
                    }
                    items(StarWishRules.allTheaters(state.customTheaters)) { theater ->
                        val credits = StarWishRules.chapterCredits(studyState)
                        val chapters = state.theaterChapters[theater.title].orEmpty()
                            .filterNot { it.isPromptPlaceholder(theater) }
                            .size
                        val canCreate = studyState.inventory.universalRareFragments >= StarWishRules.RARE_FRAGMENTS_PER_CHAPTER
                        val hasChapter = chapters > 0
                        StarWishListRow(
                            title = theater.title,
                            subtitle = if (hasChapter) "已生成 $chapters 章 · 再花 10 稀有碎片续写" else "候选剧场 · 花 10 稀有碎片生成第一章",
                            unlocked = canCreate || hasChapter,
                            progress = (studyState.inventory.universalRareFragments.coerceAtMost(StarWishRules.RARE_FRAGMENTS_PER_CHAPTER)) / StarWishRules.RARE_FRAGMENTS_PER_CHAPTER.toFloat(),
                            icon = HugeIcons.BookOpen02,
                            onClick = { if (canCreate || hasChapter) navController.navigate(Screen.StarWishTheater(theater.title)) },
                        )
                    }
                }
                StarWishSection.SpecialStories -> {
                    item {
                        SpecialStoryWalletCard(
                            fragments = studyState.inventory.specialStoryFragments,
                            onAdd = { showAddSpecialStory = true },
                        )
                    }
                    items(StarWishRules.allSpecialStories(state.customSpecialStories)) { story ->
                        val chapters = state.specialStoryChapters[story.title].orEmpty()
                            .filterNot { it.isPromptPlaceholder(story) }
                            .size
                        val canCreate = studyState.inventory.specialStoryFragments >= StarWishRules.SPECIAL_FRAGMENTS_PER_CHAPTER
                        val hasChapter = chapters > 0
                        StarWishListRow(
                            title = story.title,
                            subtitle = if (hasChapter) {
                                "已生成 $chapters 章 · 再花 2 特殊剧情碎片续写"
                            } else {
                                "候选特殊剧情 · 花 2 特殊剧情碎片生成第一章"
                            },
                            unlocked = canCreate || hasChapter,
                            progress = studyState.inventory.specialStoryFragments.coerceAtMost(StarWishRules.SPECIAL_FRAGMENTS_PER_CHAPTER) /
                                StarWishRules.SPECIAL_FRAGMENTS_PER_CHAPTER.toFloat(),
                            icon = HugeIcons.Favourite,
                            onClick = { if (canCreate || hasChapter) navController.navigate(Screen.StarWishSpecialStory(story.title)) },
                        )
                    }
                }
                StarWishSection.Video -> {
                    item {
                        VideoRewardCard(
                            epicFragments = studyState.inventory.epicFragments,
                            redeemed = studyState.stats.videoRewardsRedeemed,
                            videoModelStatus = videoModelStatus,
                            onRedeem = vm::redeemVideo,
                            onOpenImageGen = { navController.navigate(Screen.ImageGen()) },
                        )
                    }
                    item {
                        McDonaldsMcpCard(
                            mcpCode = state.mcdonaldsMcpCode,
                            mcpDiagnostic = mcpDiagnostic,
                            onSave = vm::saveMcdonaldsMcpCode,
                            onInstallMcp = vm::installMcdonaldsMcp,
                        )
                    }
                }
            }
        }
    }

    selectedScroll?.let { (outfit, scroll) ->
        val saved = state.customOutfitPrompts[outfit]
        val prompts = saved ?: StarWishOutfitPrompts(
            solo = StarWishRules.imagePromptForCompanion(scroll.soloPrompt, companionAssistant, interaction = false),
            interaction = StarWishRules.imagePromptForCompanion(scroll.interactionPrompt, companionAssistant, interaction = true),
        )
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
            onGenerate = { prompt, isInteraction ->
                val finalPrompt = StarWishRules.imagePromptForCompanion(
                    basePrompt = prompt,
                    assistant = companionAssistant,
                    interaction = isInteraction,
                )
                vm.recordImageLaunch(outfit, finalPrompt)
                selectedScroll = null
                navController.navigate(Screen.ImageGen(initialPrompt = finalPrompt, count = 1, autoGenerate = false))
            },
        )
    }

    if (showAddTheater) {
        AddTheaterDialog(
            onDismiss = { showAddTheater = false },
            onAdd = { title, prompt ->
                vm.addCustomTheater(title, prompt)
                showAddTheater = false
            },
        )
    }

    if (showAddSpecialStory) {
        AddTheaterDialog(
            onDismiss = { showAddSpecialStory = false },
            onAdd = { title, prompt ->
                vm.addCustomSpecialStory(title, prompt)
                showAddSpecialStory = false
            },
        )
    }
}

@Composable
fun StarWishTheaterPage(
    theaterTitle: String,
    special: Boolean = false,
    vm: StarWishVM = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val studyState by vm.studyState.collectAsStateWithLifecycle()
    val isGeneratingChapter by vm.isGeneratingChapter.collectAsStateWithLifecycle()
    val chapterError by vm.chapterError.collectAsStateWithLifecycle()
    val theater = if (special) {
        StarWishRules.allSpecialStories(state.customSpecialStories).firstOrNull { it.title == theaterTitle }
    } else {
        StarWishRules.allTheaters(state.customTheaters).firstOrNull { it.title == theaterTitle }
    }
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(theaterTitle) },
                navigationIcon = { BackButton() },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = StarWishColors.paper,
    ) { padding ->
        if (theater == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("这个小剧场暂时找不到了")
            }
        } else {
            TheaterDetailContent(
                theater = theater,
                credits = if (special) {
                    studyState.inventory.specialStoryFragments / StarWishRules.SPECIAL_FRAGMENTS_PER_CHAPTER
                } else {
                    StarWishRules.chapterCredits(studyState)
                },
                rareFragments = if (special) studyState.inventory.specialStoryFragments else studyState.inventory.universalRareFragments,
                chapters = if (special) state.specialStoryChapters[theater.title].orEmpty() else state.theaterChapters[theater.title].orEmpty(),
                isGenerating = isGeneratingChapter,
                error = chapterError,
                modifier = Modifier.padding(padding).padding(horizontal = 16.dp, vertical = 14.dp),
                costPerChapter = if (special) StarWishRules.SPECIAL_FRAGMENTS_PER_CHAPTER else StarWishRules.RARE_FRAGMENTS_PER_CHAPTER,
                fragmentLabel = if (special) "特殊剧情碎片" else "稀有碎片",
                onCreateChapter = { influence ->
                    if (special) vm.createNextSpecialStoryChapter(theater.title, influence) else vm.createNextChapter(theater.title, influence)
                },
                onDeleteChapter = { chapterId ->
                    if (special) vm.deleteSpecialStoryChapter(theater.title, chapterId) else vm.deleteChapter(theater.title, chapterId)
                },
            )
        }
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
private fun TheaterWalletCard(
    rareFragments: Int,
    onAdd: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.82f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = CircleShape, color = StarWishColors.mistBlue, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(HugeIcons.BookOpen02, null, tint = StarWishColors.inkBlue)
                }
            }
            Column(Modifier.weight(1f)) {
                Text("稀有碎片 $rareFragments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("10 个稀有碎片可生成或续写 1 章。自定义剧场会先锁定，兑换后点亮。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onAdd) { Text("添加") }
        }
    }
}

@Composable
private fun SpecialStoryWalletCard(
    fragments: Int,
    onAdd: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.82f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = CircleShape, color = Color(0xFFF5DDEC), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(HugeIcons.Favourite, null, tint = Color(0xFF8A3E67))
                }
            }
            Column(Modifier.weight(1f)) {
                Text("特殊剧情碎片 $fragments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("2 个特殊剧情碎片兑换 1 章。这里放更贴 XP、更有奖励感的专属剧情。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onAdd) { Text("添加") }
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
    onGenerate: (String, Boolean) -> Unit,
) {
    var solo by remember(outfit) { mutableStateOf(prompts.solo) }
    var interaction by remember(outfit) { mutableStateOf(prompts.interaction) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onGenerate(interaction, true) }) {
                    Icon(HugeIcons.Image03, null)
                    Spacer(Modifier.width(6.dp))
                    Text("生成互动")
                }
                Button(onClick = { onGenerate(solo, false) }) {
                    Icon(HugeIcons.AiMagic, null)
                    Spacer(Modifier.width(6.dp))
                    Text("生成独美")
                }
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
                        Text("还没有从星愿馆发起过生成。点生成独美或生成互动后，会跳到生图页并预填单条提示词。", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun VideoRewardCard(
    epicFragments: Int,
    redeemed: Int,
    videoModelStatus: String,
    onRedeem: () -> Unit,
    onOpenImageGen: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.84f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = Color(0xFFFFE7A8), modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(HugeIcons.Play, null, tint = Color(0xFF9B6B10), modifier = Modifier.size(24.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("视频奖励", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("视频碎片 $epicFragments/2 · 已兑换 $redeemed 次", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Surface(color = StarWishColors.mistBlue.copy(alpha = 0.72f), shape = RoundedCornerShape(14.dp)) {
                Text(
                    "$videoModelStatus\n2 个视频碎片可兑换 1 次视频生成机会。这里先留视频生成入口，具体生成参数可以继续接你的视频模型 API。",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = StarWishColors.inkBlue,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onRedeem,
                    enabled = epicFragments >= 2,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("兑换视频机会")
                }
                TextButton(
                    onClick = onOpenImageGen,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("打开生成入口")
                }
            }
        }
    }
}

@Composable
private fun McDonaldsMcpCard(
    mcpCode: String,
    mcpDiagnostic: String,
    onSave: (String) -> Unit,
    onInstallMcp: (String) -> Unit,
) {
    var code by remember(mcpCode) { mutableStateOf(mcpCode) }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.84f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = Color(0xFFFFE7A8), modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("M", color = Color(0xFF9B6B10), fontWeight = FontWeight.Black)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("麦当劳 MCP 接口", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("普通工具连接，不再作为抽卡奖励", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedTextField(
                value = code,
                onValueChange = {
                    code = it
                    onSave(it)
                },
                label = { Text("麦当劳 MCP Token") },
                supportingText = { Text("可直接粘贴 token；保存时会自动写成 Authorization: Bearer <token>") },
                placeholder = { Text("把你的 MCP 码粘到这里") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onInstallMcp(code.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("保存并接通")
                }
            }
            Surface(color = StarWishColors.mistBlue.copy(alpha = 0.72f), shape = RoundedCornerShape(14.dp)) {
                Text(
                    "服务地址和鉴权头会自动配置好，你只需要填 MCP 码。$mcpDiagnostic",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = StarWishColors.inkBlue,
                )
            }
        }
    }
}

@Composable
private fun TheaterDetailContent(
    theater: StarWishTheaterSeed,
    credits: Int,
    rareFragments: Int,
    chapters: List<me.rerere.rikkahub.data.starwish.StarWishTheaterChapter>,
    isGenerating: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
    costPerChapter: Int = StarWishRules.RARE_FRAGMENTS_PER_CHAPTER,
    fragmentLabel: String = "稀有碎片",
    onCreateChapter: (String) -> Unit,
    onDeleteChapter: (String) -> Unit,
) {
    val visibleChapters = remember(theater, chapters) { chapters.filterNot { it.isPromptPlaceholder(theater) } }
    var influence by remember(theater.title, visibleChapters.size) { mutableStateOf("") }
    var showGuide by remember { mutableStateOf(false) }
    var showCatalog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 178.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$fragmentLabel $rareFragments · 可兑换 $credits 章 · 已生成 ${visibleChapters.size} 章",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box {
                        IconButton(onClick = { showGuide = true }) {
                            Icon(HugeIcons.MoreVertical, contentDescription = "剧情指导")
                        }
                        DropdownMenu(expanded = showGuide, onDismissRequest = { showGuide = false }) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        StarWishRules.theaterGuide(theater),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                                onClick = { showGuide = false },
                            )
                        }
                    }
                }
            }
            if (visibleChapters.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.78f))) {
                        Text(
                            "还没有正文。花 $costPerChapter 个$fragmentLabel 生成第一章；之后每次再花 $costPerChapter 个$fragmentLabel 续写下一章。",
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(visibleChapters) {
                Surface(color = Color.White.copy(alpha = 0.86f), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(it.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onDeleteChapter(it.id) }) {
                                Icon(HugeIcons.Delete01, contentDescription = "删除章节")
                            }
                        }
                        if (it.userInfluence.isNotBlank()) {
                            Text("你的影响：${it.userInfluence}", style = MaterialTheme.typography.bodySmall, color = StarWishColors.inkBlue)
                        }
                        Text(it.content, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Box(modifier = Modifier.align(Alignment.BottomStart)) {
            Surface(
                color = Color.White.copy(alpha = 0.92f),
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 2.dp,
            ) {
                TextButton(onClick = { showCatalog = true }) {
                    Icon(HugeIcons.BookOpen02, null)
                    Spacer(Modifier.width(6.dp))
                    Text("目录")
                }
            }
            DropdownMenu(expanded = showCatalog, onDismissRequest = { showCatalog = false }) {
                if (visibleChapters.isEmpty()) {
                    DropdownMenuItem(text = { Text("暂无章节") }, onClick = { showCatalog = false })
                } else {
                    visibleChapters.forEachIndexed { index, chapter ->
                        DropdownMenuItem(
                            text = { Text(chapter.title) },
                            onClick = {
                                showCatalog = false
                                scope.launch { listState.animateScrollToItem(index + 1) }
                            },
                        )
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = StarWishColors.paper.copy(alpha = 0.96f),
            tonalElevation = 2.dp,
        ) {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = influence,
                    onValueChange = { influence = it },
                    label = { Text(if (visibleChapters.isEmpty()) "给第一章一点方向（可选）" else "我想影响下一章（可选）") },
                    placeholder = { Text("例如：让露臣这章彻底低头，顺便狠狠打脸恶人") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        onCreateChapter(influence)
                        influence = ""
                    },
                    enabled = !isGenerating && rareFragments >= costPerChapter,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(HugeIcons.Play, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            isGenerating -> "生成中..."
                            visibleChapters.isEmpty() -> "生成第一章"
                            else -> "续写下一章"
                        }
                    )
                }
            }
        }
    }
}

private fun me.rerere.rikkahub.data.starwish.StarWishTheaterChapter.isPromptPlaceholder(
    seed: StarWishTheaterSeed,
): Boolean {
    val clean = content.trim()
    return clean == seed.prompt.trim() ||
        clean.startsWith("总设定：") ||
        clean.startsWith("你是一个擅长") ||
        clean.contains("硬性要求：") ||
        clean.contains("请根据下面设定生成")
}

@Composable
private fun AddTheaterDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onAdd(title, prompt) }, enabled = title.isNotBlank() && prompt.isNotBlank()) {
                Text("添加")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("添加小剧场") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("添加后会出现在小剧场列表里；未花稀有碎片生成章节前，它仍然只是候选。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("剧情提示词") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

private fun outfitProgress(outfit: String, fragments: Map<String, Int>): Float {
    val prefix = "normal:$outfit:"
    val count = fragments.entries.sumOf { (key, value) ->
        if (key.startsWith(prefix)) value else 0
    }.coerceAtMost(StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT)
    return count / StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT.toFloat()
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
