package me.rerere.rikkahub.ui.pages.study

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.media.MediaMetadataRetriever
import android.widget.VideoView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.AiMagic
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.BookOpen02
import me.rerere.hugeicons.stroke.Chart
import me.rerere.hugeicons.stroke.Clapping01
import me.rerere.hugeicons.stroke.Clock02
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Favourite
import me.rerere.hugeicons.stroke.Package
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.StopCircle
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.PomodoroTheme
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getAssistantTTSProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.study.DailyStudyPlan
import me.rerere.rikkahub.data.study.StudyAchievement
import me.rerere.rikkahub.data.study.StudyScheduleBlock
import me.rerere.rikkahub.data.study.StudyDrawResult
import me.rerere.rikkahub.data.study.StudyEntertainmentReward
import me.rerere.rikkahub.data.study.ExamStudyPlan
import me.rerere.rikkahub.data.study.StudyEvent
import me.rerere.rikkahub.data.study.StudyInventory
import me.rerere.rikkahub.data.study.StudyMysteryBoxReward
import me.rerere.rikkahub.data.study.StudyRarity
import me.rerere.rikkahub.data.study.StudyRules
import me.rerere.rikkahub.data.study.StudySleepHabit
import me.rerere.rikkahub.data.study.StudyShopItem
import me.rerere.rikkahub.data.study.StudyState
import me.rerere.rikkahub.data.study.StudyTip
import me.rerere.rikkahub.data.study.StudyTask
import me.rerere.rikkahub.data.study.StudyTaskSource
import me.rerere.rikkahub.data.study.SuperMomentChoice
import me.rerere.rikkahub.data.starwish.StarWishRules
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.resolveAppVideoUri
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.time.LocalDate
import kotlin.uuid.Uuid

private enum class StudySection(val label: String) {
    Companion("陪伴"),
    Today("今日"),
    Plan("计划"),
    Gacha("抽卡"),
    Collection("收藏"),
    Achievements("成就"),
    Shop("商店"),
    Guide("说明"),
}

private enum class CollectionSection(val label: String) {
    Scrolls("已解锁画卷"),
    Theaters("小剧场"),
}

private enum class PlanView(val label: String) {
    Weekly("周计划"),
    Monthly("月计划"),
}

private enum class DailyDashboardView(val label: String) {
    Tasks("待办"),
    Plan("今日计划"),
    Tomorrow("明日待办"),
    Tips("Tips"),
}

private const val DEFAULT_RAINBOW_DRAW_VIDEO_URI = "raw:star_wish_rainbow_draw"
private const val DEFAULT_EPIC_DRAW_VIDEO_URI = "raw:star_wish_epic_draw"
private const val DEFAULT_RARE_DRAW_VIDEO_URI = "raw:star_wish_rare_draw"

@Composable
fun StudyPage(vm: StudyVM = koinViewModel()) {
    val navController = LocalNavController.current
    val settings = LocalSettings.current
    val state by vm.state.collectAsStateWithLifecycle()
    val isGeneratingSchedule by vm.isGeneratingSchedule.collectAsStateWithLifecycle()
    val companionAssistant = remember(settings.assistants, settings.assistantId, state.selectedAssistantId) {
        val selected = state.selectedAssistantId
        settings.assistants.firstOrNull { it.id.toString() == selected }
            ?: settings.getCurrentAssistant()
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var section by remember { mutableStateOf(StudySection.Companion) }
    var newTask by remember { mutableStateOf("") }
    var drawDialog by remember { mutableStateOf<List<StudyDrawReveal>?>(null) }
    var pendingBoxDialog by remember { mutableStateOf(false) }
    var boxDialog by remember { mutableStateOf<StudyMysteryBoxReward?>(null) }
    var showSuperDialog by remember { mutableStateOf(false) }
    var showLevelDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val isGachaSection = section == StudySection.Gacha
    val pageColor = if (isGachaSection) StudyColors.starryPage else StudyColors.page

    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                is StudyEffect.Message -> snackbarHostState.showSnackbar(effect.text)
                StudyEffect.MysteryBoxReady -> pendingBoxDialog = true
                is StudyEffect.MysteryBox -> boxDialog = effect.reward
                is StudyEffect.DrawResults -> drawDialog = effect.results
                StudyEffect.SuperMomentReady -> showSuperDialog = true
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(if (isGachaSection) "星夜来信" else "考研") },
                navigationIcon = {
                    if (isGachaSection) {
                        Surface(
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(48.dp)
                                .clickable { navController.popBackStack() },
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(HugeIcons.ArrowLeft01, contentDescription = "返回")
                            }
                        }
                    } else {
                        BackButton()
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = if (isGachaSection) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = StudyColors.starryPage,
                        scrolledContainerColor = StudyColors.starryPageDeep,
                        navigationIconContentColor = Color.White,
                        titleContentColor = Color.White,
                    )
                } else {
                    CustomColors.topBarColors
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = pageColor,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pageColor),
        ) {
            if (isGachaSection) {
                StarryLetterBackdrop(Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to StudyColors.starryPage.copy(alpha = 0.32f),
                                0.55f to Color.Transparent,
                                1f to StudyColors.starryPageDeep.copy(alpha = 0.72f),
                            ),
                        ),
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding + PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    SectionChips(
                        selected = section,
                        onSelected = { section = it },
                        dark = isGachaSection,
                    )
                }
                when (section) {
                    StudySection.Companion -> {
                        item {
                            StudyHero(
                                state = state,
                                assistant = companionAssistant,
                                assistants = settings.assistants,
                                onSignIn = vm::signIn,
                                onPomodoro = { navController.navigate(Screen.StudyPomodoro) },
                                onOpenLevel = { showLevelDialog = true },
                                onSelectCompanion = { vm.selectCompanion(it.id.toString()) },
                            )
                        }
                        item {
                            SleepHabitRewardCard(
                                state = state,
                                assistantName = companionAssistant.name.ifBlank { "当前角色" },
                            )
                        }
                        item { RecentEventsCard(events = state.recentEvents) }
                    }
                    StudySection.Today -> {
                        item {
                            DailyStudyDashboard(
                                tasks = state.tasks,
                                assistantName = companionAssistant.name.ifBlank { "当前角色" },
                                generatedSchedule = state.generatedSchedules[LocalDate.now().toString()],
                                isGeneratingSchedule = isGeneratingSchedule,
                                newTask = newTask,
                                onNewTask = { newTask = it },
                                onAdd = {
                                    vm.addTask(newTask)
                                    newTask = ""
                                },
                                onGenerateSchedule = vm::generateTodaySchedule,
                                onToggle = vm::toggleTask,
                                onDelete = vm::deleteTask,
                            )
                        }
                        item {
                            TodayProgressCard(
                                state = state,
                                onClaimNormal = { vm.claimSuperMoment(SuperMomentChoice.NormalFragments) },
                                onClaimRare = { vm.claimSuperMoment(SuperMomentChoice.RareFragment) },
                            )
                        }
                    }
                    StudySection.Plan -> {
                        item {
                            StudyCard {
                                PlanOverviewContent()
                            }
                        }
                    }
                    StudySection.Gacha -> {
                        item {
                            GachaCard(
                                state = state,
                                onSingle = { vm.draw(1) },
                                onTen = { vm.draw(10) },
                                onPurple = vm::drawPurpleTicket,
                            )
                        }
                    }
                    StudySection.Collection -> {
                        item {
                            CollectionCard(
                                inventory = state.inventory,
                                onUseUniversalNormalTarget = vm::applyUniversalNormal,
                                onOpenMysteryBox = { vm.openMysteryBox(it) },
                                onRedeemDouyin = { vm.redeemEntertainment(StudyEntertainmentReward.Douyin) },
                                onRedeemGame = {
                                    vm.redeemEntertainment(StudyEntertainmentReward.Game)
                                    navController.navigate(Screen.GameHub)
                                },
                                onRedeemAnime = { vm.redeemEntertainment(StudyEntertainmentReward.Anime) },
                                onOpenStarWish = { navController.navigate(Screen.StarWish) },
                                onOpenImageGen = { outfit ->
                                    val scroll = StarWishRules.scrollForOutfit(outfit)
                                    val prompt = StarWishRules.imagePromptForCompanion(
                                        basePrompt = scroll.soloPrompt,
                                        assistant = companionAssistant,
                                        interaction = false,
                                        userNickname = settings.displaySetting.userNickname,
                                        userProfile = settings.displaySetting.userProfile,
                                        userAppearancePrompt = settings.displaySetting.userAppearancePrompt,
                                    )
                                    navController.navigate(
                                        Screen.ImageGen(
                                            initialPrompt = prompt,
                                            count = 1,
                                            autoGenerate = false,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                    StudySection.Achievements -> {
                        item {
                            AchievementCard(
                                state = state,
                                onClaim = vm::claimAchievement,
                            )
                        }
                    }
                    StudySection.Shop -> {
                        item {
                            ShopCard(
                                state = state,
                                onRefresh = vm::refreshShop,
                                onBuy = vm::buyShopItem,
                            )
                        }
                    }
                    StudySection.Guide -> {
                        item { StudyGuideCard() }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            vm.syncToday()
            delay(60_000)
        }
    }

    boxDialog?.let { reward ->
        MysteryBoxCelebration(
            reward = reward,
            onDismissRequest = { boxDialog = null },
        )
    }

    if (pendingBoxDialog) {
        MysteryBoxPendingDialog(
            onOpen = {
                pendingBoxDialog = false
                vm.openMysteryBox()
            },
            onKeep = { pendingBoxDialog = false },
        )
    }

    drawDialog?.let { results ->
        DrawResultCelebration(
            results = results,
            onDismissRequest = { drawDialog = null },
        )
    }

    if (showSuperDialog) {
        SuperMomentCelebration(
            assistant = companionAssistant,
            onDismissRequest = { showSuperDialog = false },
            onClaimNormal = {
                showSuperDialog = false
                vm.claimSuperMoment(SuperMomentChoice.NormalFragments)
            },
            onClaimRare = {
                showSuperDialog = false
                vm.claimSuperMoment(SuperMomentChoice.RareFragment)
            },
        )
    }

    if (showLevelDialog) {
        LevelDialog(
            state = state,
            onClaimLevel = vm::claimLevel,
            onDismissRequest = { showLevelDialog = false },
        )
    }
}

@Composable
fun StudyPomodoroPage() {
    val navController = LocalNavController.current
    val settings = LocalSettings.current
    val settingsStore = koinInject<SettingsStore>()
    val scope = rememberCoroutineScope()
    val assistant = settings.getCurrentAssistant()
    var minutes by remember { mutableIntStateOf(25) }
    var customMinutes by remember { mutableStateOf("") }
    var taskText by remember { mutableStateOf("") }
    var showThemePicker by remember { mutableStateOf(false) }
    val voiceEnabled = settings.pomodoroVoiceEnabled
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("番茄钟") },
                navigationIcon = { BackButton() },
                actions = {
                    TextButton(onClick = { showThemePicker = true }) { Text("配色") }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = StudyColors.page,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                CompanionPrepCard(
                    assistant = assistant,
                    voiceEnabled = voiceEnabled,
                    onVoiceToggle = { enabled ->
                        scope.launch {
                            settingsStore.update { current ->
                                current.copy(pomodoroVoiceEnabled = enabled)
                            }
                        }
                    },
                )
            }
            item {
                OutlinedTextField(
                    value = taskText,
                    onValueChange = { taskText = it },
                    label = { Text("这一轮要完成什么") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                DurationCard(
                    selectedMinutes = minutes,
                    customMinutes = customMinutes,
                    onSelect = {
                        minutes = it
                        customMinutes = ""
                    },
                    onCustom = {
                        customMinutes = it.filter(Char::isDigit).take(3)
                        customMinutes.toIntOrNull()?.takeIf { value -> value > 0 }?.let { minutes = it }
                    },
                )
            }
            item {
                Button(
                    onClick = {
                        navController.navigate(
                            Screen.StudyPomodoroFocus(
                                minutes = minutes.coerceAtLeast(1),
                                task = taskText.trim(),
                                imageEnabled = false,
                                voiceEnabled = voiceEnabled,
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(HugeIcons.Play, null)
                    Spacer(Modifier.width(8.dp))
                    Text("开始陪学")
                }
            }
        }
    }
    if (showThemePicker) {
        PomodoroThemePickerDialog(
            selected = settings.pomodoroTheme,
            onSelect = { theme ->
                scope.launch { settingsStore.update { it.copy(pomodoroTheme = theme) } }
            },
            onDismiss = { showThemePicker = false },
        )
    }
}

@Composable
fun StudyPomodoroFocusPage(
    minutes: Int,
    task: String,
    imageEnabled: Boolean,
    voiceEnabled: Boolean,
    vm: StudyVM = koinViewModel(),
) {
    val settings = LocalSettings.current
    val assistant = settings.getCurrentAssistant()
    val chatService: ChatService = koinInject()
    val conversationRepository = koinInject<ConversationRepository>()
    val settingsStore = koinInject<SettingsStore>()
    val tts = LocalTTSState.current
    val scope = rememberCoroutineScope()
    val safeMinutes = minutes.coerceAtLeast(1)
    val totalSeconds = safeMinutes * 60
    var remainingSeconds by remember(safeMinutes) { mutableIntStateOf(totalSeconds) }
    var finished by remember { mutableStateOf(false) }
    var studyConversationId by remember { mutableStateOf<Uuid?>(null) }
    var chatText by remember { mutableStateOf("") }
    var userLine by remember { mutableStateOf("") }
    var coachReply by remember { mutableStateOf("") }
    var waitingReply by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    val focusPalette = focusPalette(settings.pomodoroTheme)
    val studiedSeconds = (totalSeconds - remainingSeconds).coerceIn(0, totalSeconds)

    fun finishPomodoro(early: Boolean) {
        if (finished) return
        finished = true
        val elapsedSeconds = (totalSeconds - remainingSeconds).coerceIn(0, totalSeconds)
        val recordedMinutes = elapsedSeconds.toRecordedMinutes()
        if (recordedMinutes > 0) {
            vm.completePomodoro(recordedMinutes)
        }
        val line = if (early) {
            if (recordedMinutes > 0) {
                "这一轮先收住，已经学习了 ${studyDurationText(elapsedSeconds)}。奖励按实际学习时长记好了。"
            } else {
                "这一轮还没正式开始计时，先不记奖励。重新开一轮也来得及。"
            }
        } else {
            "这一轮完成了，已经学习了 ${studyDurationText(totalSeconds)}。你真的坐住了，奖励我已经替你收好啦。"
        }
        coachReply = line
        if (voiceEnabled) {
            scope.launch {
                tts.speak(
                    text = line,
                    flushCalled = true,
                    providerOverride = settings.getAssistantTTSProvider(assistant.id),
                )
            }
        }
    }

    LaunchedEffect(safeMinutes) {
        val target = conversationRepository.getRecentConversations(assistant.id, limit = 1)
            .firstOrNull()
            ?: Conversation.ofId(
                id = Uuid.random(),
                assistantId = assistant.id,
                newConversation = true,
        )
        studyConversationId = target.id
        chatService.initializeConversation(target.id)
        waitingReply = true
        scope.launch {
            val line = runCatching {
                chatService.sendVoiceCallTurn(
                    conversationId = target.id,
                    text = buildPomodoroOpeningPrompt(task),
                    visibleUserText = "开始番茄钟：${task.ifBlank { "这一轮学习" }}",
                )
            }.getOrNull() ?: buildEncourageLine(task, assistant)
            coachReply = line
            waitingReply = false
            if (voiceEnabled) {
                tts.speak(
                    text = line,
                    flushCalled = true,
                    providerOverride = settings.getAssistantTTSProvider(assistant.id),
                )
            }
        }
        while (!finished && remainingSeconds > 0) {
            delay(1_000)
            if (!finished) {
                remainingSeconds -= 1
            }
        }
        if (!finished && remainingSeconds <= 0) {
            finishPomodoro(early = false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(focusBrush(settings.pomodoroTheme)),
    ) {
        TextButton(
            onClick = { showThemePicker = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 52.dp, end = 16.dp)
                .height(48.dp)
                .widthIn(min = 92.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = focusPalette.primaryText),
        ) {
            Text("配色", fontWeight = FontWeight.SemiBold)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(focusPalette.topGlow, Color.Transparent, focusPalette.bottomGlow)
                    )
                )
                .padding(horizontal = 22.dp, vertical = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.7f))
            PomodoroTimerCircle(
                timeText = secondsText(remainingSeconds),
                task = task.ifBlank { "专注这一轮" },
                progress = remainingSeconds.toFloat() / totalSeconds.coerceAtLeast(1),
                palette = focusPalette,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "已学习 ${studyDurationText(studiedSeconds)}",
                color = focusPalette.secondaryText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedButton(
                onClick = { finishPomodoro(early = true) },
                enabled = !finished,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = focusPalette.primaryText),
            ) {
                Icon(HugeIcons.StopCircle, null)
                Spacer(Modifier.width(8.dp))
                Text("提前结束")
            }
            Spacer(Modifier.height(34.dp))
            if (waitingReply || coachReply.isNotBlank()) {
                Text(
                    text = if (waitingReply) "正在回复..." else coachReply,
                    style = MaterialTheme.typography.titleMedium,
                    color = focusPalette.primaryText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            FocusChatPanel(
                userLine = userLine,
                chatText = chatText,
                assistantName = assistant.name.ifBlank { "当前角色" },
                palette = focusPalette,
                onChatChange = { chatText = it },
                onSend = {
                    val text = chatText.trim()
                    if (text.isNotBlank()) {
                        userLine = text
                        chatText = ""
                        waitingReply = true
                        scope.launch {
                            val conversationId = studyConversationId
                            val line = if (conversationId == null) {
                                buildEncourageLine(task, assistant)
                            } else {
                                chatService.sendVoiceCallTurn(
                                    conversationId = conversationId,
                                    text = buildStudyChatPrompt(text, task),
                                    visibleUserText = text,
                                ) ?: buildEncourageLine(task, assistant)
                            }
                            coachReply = line
                            waitingReply = false
                            if (voiceEnabled) {
                                tts.speak(
                                    text = line,
                                    flushCalled = true,
                                    providerOverride = settings.getAssistantTTSProvider(assistant.id),
                                )
                            }
                        }
                    }
                },
            )
        }
    }
    if (showThemePicker) {
        PomodoroThemePickerDialog(
            selected = settings.pomodoroTheme,
            onSelect = { theme ->
                scope.launch { settingsStore.update { it.copy(pomodoroTheme = theme) } }
            },
            onDismiss = { showThemePicker = false },
        )
    }
}

@Composable
private fun StudyHero(
    state: StudyState,
    assistant: Assistant,
    assistants: List<Assistant>,
    onSignIn: () -> Unit,
    onPomodoro: () -> Unit,
    onOpenLevel: () -> Unit,
    onSelectCompanion: (Assistant) -> Unit,
) {
    val daysLeft = ExamStudyPlan.daysLeft()
    val currentMilestone = ExamStudyPlan.currentMilestone()
    val studyTimeOverview = StudyRules.studyTimeOverview(state)
    val professionalTargetScore =
        ExamStudyPlan.professionalFoundationTargetScore + ExamStudyPlan.professionalComprehensiveTargetScore
    var showCompanionPicker by remember { mutableStateOf(false) }
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = StudyColors.hero),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(heroBrush())
                .padding(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    UIAvatar(
                        assistant.name,
                        assistant.avatar,
                        Modifier.size(58.dp),
                        onClick = { showCompanionPicker = true },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${assistant.name}陪你备考", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("点头像可以换陪你学习的角色。今天的待办和番茄钟会同步给 TA。", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    HeroMetric("规划倒计时", "${daysLeft}天", Modifier.weight(1f))
                    HeroMetric("夸夸值", state.wallet.kudos.toString(), Modifier.weight(1f))
                    HeroMetric("Lv", StudyRules.currentLevel(state).level.toString(), Modifier.weight(1f), onOpenLevel)
                }
                Text(
                    text = ExamStudyPlan.examDateNotice,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    HeroMetric("川大目标", "${ExamStudyPlan.scuSafeTargetScore}分", Modifier.weight(1f))
                    HeroMetric(
                        "专业课目标",
                        "$professionalTargetScore/300",
                        Modifier.weight(1f),
                    )
                }
                Text(
                    text = currentMilestone,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    HeroMetric(
                        "今日学习",
                        formatStudyTimeMetric(studyTimeOverview.todayMinutes, studyTimeOverview.todayPomodoros),
                        Modifier.weight(1f),
                    )
                    HeroMetric(
                        "本周学习",
                        formatStudyTimeMetric(studyTimeOverview.weekMinutes, studyTimeOverview.weekPomodoros),
                        Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(onClick = onSignIn, modifier = Modifier.weight(1f)) {
                        Icon(HugeIcons.Clapping01, null)
                        Spacer(Modifier.width(6.dp))
                        Text("签到")
                    }
                    Button(onClick = onPomodoro, modifier = Modifier.weight(1f)) {
                        Icon(HugeIcons.Clock02, null)
                        Spacer(Modifier.width(6.dp))
                        Text("番茄钟")
                    }
                }
            }
        }
    }
    if (showCompanionPicker) {
        AlertDialog(
            onDismissRequest = { showCompanionPicker = false },
            title = { Text("选择今天陪你学习的角色") },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(360.dp),
                ) {
                    items(assistants) { item ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectCompanion(item)
                                    showCompanionPicker = false
                                },
                            color = if (item.id == assistant.id) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.62f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                UIAvatar(item.name, item.avatar, Modifier.size(42.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.name.ifBlank { "未命名角色" }, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (item.id == assistant.id) "正在陪你学习" else "切换为今日陪伴",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCompanionPicker = false }) { Text("收起") } },
        )
    }
}

@Composable
private fun HeroMetric(label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = Color.White.copy(alpha = 0.42f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionChips(
    selected: StudySection,
    onSelected: (StudySection) -> Unit,
    dark: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        StudySection.entries.forEach { section ->
            FilterChip(
                selected = selected == section,
                onClick = { onSelected(section) },
                label = { Text(section.label) },
                colors = if (dark) {
                    FilterChipDefaults.filterChipColors(
                        containerColor = Color.White.copy(alpha = 0.06f),
                        labelColor = Color.White.copy(alpha = 0.72f),
                        selectedContainerColor = Color(0xFFD9C5FF).copy(alpha = 0.20f),
                        selectedLabelColor = Color.White,
                    )
                } else {
                    FilterChipDefaults.filterChipColors()
                },
            )
        }
    }
}

@Composable
private fun TodayProgressCard(
    state: StudyState,
    onClaimNormal: () -> Unit,
    onClaimRare: () -> Unit,
) {
    val total = state.tasks.size
    val done = state.tasks.count { it.done }
    val progress = if (total == 0) 0f else done.toFloat() / total
    val progressPercent = (progress * 100).toInt().coerceIn(0, 100)
    StudyCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("今日进度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("计划下面单独看进度：$done/$total", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(color = StudyColors.hero.copy(alpha = 0.78f), shape = CircleShape) {
                Text(
                    "$progressPercent%",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = StudyColors.goldText,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (state.superMomentAvailable) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onClaimNormal, modifier = Modifier.fillMaxWidth()) { Text("领取十连券 x1") }
            }
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DailyStudyDashboard(
    tasks: List<StudyTask>,
    assistantName: String,
    generatedSchedule: List<StudyScheduleBlock>?,
    isGeneratingSchedule: Boolean,
    newTask: String,
    onNewTask: (String) -> Unit,
    onAdd: () -> Unit,
    onGenerateSchedule: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    val today = LocalDate.now()
    val todayPlan = ExamStudyPlan.todayPlan(today)
    val tomorrowPlan = ExamStudyPlan.todayPlan(today.plusDays(1))
    val schedule = generatedSchedule ?: ExamStudyPlan.todaySchedule(today)
    val tips = ExamStudyPlan.todayTips(today)

    var dashboardView by remember { mutableStateOf(DailyDashboardView.Tasks) }

    StudyCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            DailyDashboardView.entries.forEach { view ->
                FilterChip(
                    selected = dashboardView == view,
                    onClick = { dashboardView = view },
                    label = {
                        Text(
                            text = view.label,
                            maxLines = 1,
                        )
                    },
                )
            }
        }
        when (dashboardView) {
            DailyDashboardView.Tasks -> TaskContent(
                tasks = tasks,
                assistantName = assistantName,
                newTask = newTask,
                onNewTask = onNewTask,
                onAdd = onAdd,
                onToggle = onToggle,
                onDelete = onDelete,
            )
            DailyDashboardView.Plan -> TodayPlanContent(
                todayPlan = todayPlan,
                schedule = schedule,
                generatedByAi = generatedSchedule != null,
                isGeneratingSchedule = isGeneratingSchedule,
                onGenerateSchedule = onGenerateSchedule,
            )
            DailyDashboardView.Tomorrow -> TomorrowPlanContent(tomorrowPlan = tomorrowPlan)
            DailyDashboardView.Tips -> StudyTipsContent(tips = tips)
        }
    }
}

@Composable
private fun TaskContent(
    tasks: List<StudyTask>,
    assistantName: String,
    newTask: String,
    onNewTask: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    val planCount = tasks.count { it.source == StudyTaskSource.Plan }
    val donePlanCount = tasks.count { it.source == StudyTaskSource.Plan && it.done }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("待办", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (planCount > 0) {
                    Text("计划任务 $donePlanCount/$planCount，手动任务 ${tasks.size - planCount} 个", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newTask,
                onValueChange = onNewTask,
                label = { Text("新增学习任务") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            IconButton(onClick = onAdd) { Icon(HugeIcons.Add01, "添加") }
        }
        if (tasks.isEmpty()) {
            Text("先写下今天最重要的 3-5 件事。${assistantName}会按人设和约定陪你保持节奏。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        tasks.forEach { task ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = task.done, onCheckedChange = { onToggle(task.id, it) })
                Column(Modifier.weight(1f)) {
                    if (task.source == StudyTaskSource.Plan) {
                        Text("计划", style = MaterialTheme.typography.labelSmall, color = StudyColors.blue)
                    }
                    Text(
                        text = task.title,
                        textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    )
                }
                IconButton(onClick = { onDelete(task.id) }) {
                    Icon(HugeIcons.Delete01, "删除")
                }
            }
        }
    }
}
@Composable
private fun TodayPlanCard(
    todayPlan: DailyStudyPlan?,
    schedule: List<StudyScheduleBlock>,
) {
    StudyCard {
        TodayPlanContent(
            todayPlan = todayPlan,
            schedule = schedule,
            generatedByAi = false,
            isGeneratingSchedule = false,
            onGenerateSchedule = {},
        )
    }
}

@Composable
private fun TodayPlanContent(
    todayPlan: DailyStudyPlan?,
    schedule: List<StudyScheduleBlock>,
    generatedByAi: Boolean,
    isGeneratingSchedule: Boolean,
    onGenerateSchedule: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(HugeIcons.Clock02, null, tint = StudyColors.blue)
            Column(Modifier.weight(1f)) {
                Text("今日计划", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (generatedByAi) "已按今日待办重新生成" else todayPlan?.title ?: "今天先守住最小学习闭环",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onGenerateSchedule, enabled = !isGeneratingSchedule) {
                if (isGeneratingSchedule) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(HugeIcons.AiMagic, null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(if (isGeneratingSchedule) "生成中" else "生成计划表")
            }
        }
        schedule.forEach { block ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    block.time,
                    style = MaterialTheme.typography.labelMedium,
                    color = StudyColors.blue,
                    modifier = Modifier.width(82.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(block.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(block.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TomorrowPlanContent(
    tomorrowPlan: DailyStudyPlan?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(HugeIcons.BookOpen02, null, tint = StudyColors.purple)
            Column(Modifier.weight(1f)) {
                Text("明日待办", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(tomorrowPlan?.title ?: "明天先保留弹性，不提前制造压力", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (tomorrowPlan == null) {
            Text("还没有明天的预制计划。今晚收尾时只写明天第一步。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            tomorrowPlan.tasks.forEachIndexed { index, task ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Surface(shape = CircleShape, color = StudyColors.softBlue, modifier = Modifier.size(26.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = StudyColors.blue)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(task.kind.label, style = MaterialTheme.typography.labelSmall, color = StudyColors.purple)
                        Text(task.title, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Text(
                "明日待办只是预览；今晚收尾时再决定明天第一步，不把焦虑提前搬到今天。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StudyTipsCard(tips: List<StudyTip>) {
    StudyCard {
        StudyTipsContent(tips = tips)
    }
}

@Composable
private fun StudyTipsContent(tips: List<StudyTip>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(HugeIcons.AiMagic, null, tint = StudyColors.goldText)
            Column(Modifier.weight(1f)) {
                Text("tips", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("按今天任务给你提效，不照搬经验帖强度。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        tips.forEach { tip ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tip.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(tip.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PlanOverviewContent() {
    var planView by remember { mutableStateOf(PlanView.Weekly) }
    val today = LocalDate.now()
    val week = ExamStudyPlan.weekForDate(today) ?: ExamStudyPlan.weeklyPlans.firstOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            PlanView.entries.forEach { view ->
                FilterChip(
                    selected = planView == view,
                    onClick = { planView = view },
                    label = { Text(view.label) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        when (planView) {
            PlanView.Weekly -> {
                week?.let {
                    Text(it.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(it.dateRange, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    it.tasks.forEach { task ->
                        Text("· $task", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } ?: Text("本周计划待生成", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PlanView.Monthly -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("7-12月总计划", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    ExamStudyPlan.monthlyPlans.forEach { month ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${month.month} · ${month.focus}", fontWeight = FontWeight.SemiBold)
                            month.tasks.forEach { task ->
                                Text("· $task", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelDialog(
    state: StudyState,
    onClaimLevel: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val level = StudyRules.currentLevel(state)
    val next = StudyRules.levels.firstOrNull { it.level == level.level + 1 }
    val claimable = StudyRules.claimableLevels(state)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { TextButton(onClick = onDismissRequest) { Text("收起") } },
        title = { Text("等级进度") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(HugeIcons.Chart, null, tint = StudyColors.goldText)
                    Column(Modifier.weight(1f)) {
                        Text("Lv${level.level} ${level.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("累计夸夸值 ${state.wallet.totalKudosEarned}")
                    }
                }
                next?.let {
                    val span = (it.threshold - level.threshold).coerceAtLeast(1)
                    val current = (state.wallet.totalKudosEarned - level.threshold).coerceIn(0, span)
                    LinearProgressIndicator(progress = { current.toFloat() / span }, modifier = Modifier.fillMaxWidth())
                    Text("距离 Lv${it.level} 还差 ${(it.threshold - state.wallet.totalKudosEarned).coerceAtLeast(0)} 累计夸夸值")
                } ?: Text("你已经抵达星穹彼岸")

                Text("可领取奖励", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (claimable.isEmpty()) {
                    Text("暂时没有新的等级奖励。继续完成待办和番茄钟吧。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                claimable.take(5).forEach {
                    AssistChip(onClick = { onClaimLevel(it.level) }, label = { Text("领取 Lv${it.level}：${it.reward.title}") })
                }

                Text("等级奖励表", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                StudyRules.levels.forEach {
                    Text("Lv${it.level} ${it.title} · ${it.threshold} · ${it.reward.title}", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
    )
}

@Composable
private fun SuperMomentCelebration(
    assistant: Assistant,
    onDismissRequest: () -> Unit,
    onClaimNormal: () -> Unit,
    onClaimRare: () -> Unit,
) {
    val pulse by rememberInfiniteTransition(label = "super-moment").animateFloat(
        initialValue = 0.88f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "super-moment-pulse",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(superMomentBrush())
            .padding(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                repeat(7) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.30f + it * 0.06f),
                        modifier = Modifier.size(((12 + it * 3) * pulse).dp),
                    ) {}
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("超神时刻", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Color.White)
                Text("今日全清", style = MaterialTheme.typography.headlineMedium, color = Color.White.copy(alpha = 0.92f))
                Text(
                    "${assistant.name}看见你把今天全部拿下了。奖励固定发放十连券 x1。",
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(12) {
                        Surface(
                            shape = CircleShape,
                            color = listOf(Color.White, StudyColors.goldText, StudyColors.purple)[it % 3].copy(alpha = 0.78f),
                            modifier = Modifier.size(((10 + it % 4 * 5) * pulse).dp),
                        ) {}
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onClaimNormal, modifier = Modifier.fillMaxWidth()) {
                    Text("领取十连券 x1")
                }
                TextButton(onClick = onDismissRequest, modifier = Modifier.fillMaxWidth()) {
                    Text("先等等", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun DrawResultCelebration(
    results: List<StudyDrawReveal>,
    onDismissRequest: () -> Unit,
) {
    val drawResults = remember(results) { results.map { it.result } }
    val isSingleDraw = drawResults.size == 1
    val best = drawResults.maxByOrNull { it.rarity.weight }?.rarity ?: StudyRarity.Normal
    var revealState by remember(results) { mutableStateOf(DrawRevealFlow.start(drawResults)) }
    var playedRewardVideoIndexes by remember(results) { mutableStateOf(emptySet<Int>()) }
    var skipAllRequested by remember(results) { mutableStateOf(false) }
    val currentReveal = results.getOrNull(revealState.index)
    val current = currentReveal?.result
    val haptic = LocalHapticFeedback.current
    var cardRevealReady by remember(results) { mutableStateOf(true) }
    val hasOpeningVideo = remember(drawResults) {
        drawResults.any {
            it.rarity == StudyRarity.Rainbow || it.rarity == StudyRarity.Epic || it.rarity == StudyRarity.Rare
        }
    }
    val openingVideoUri = when (revealState.phase) {
        DrawRevealPhase.RainbowOpeningVideo -> DEFAULT_RAINBOW_DRAW_VIDEO_URI
        DrawRevealPhase.EpicOpeningVideo -> DEFAULT_EPIC_DRAW_VIDEO_URI
        DrawRevealPhase.RareOpeningVideo -> DEFAULT_RARE_DRAW_VIDEO_URI
        else -> when {
            drawResults.any { it.rarity == StudyRarity.Rainbow } -> DEFAULT_RAINBOW_DRAW_VIDEO_URI
            drawResults.any { it.rarity == StudyRarity.Epic } -> DEFAULT_EPIC_DRAW_VIDEO_URI
            drawResults.any { it.rarity == StudyRarity.Rare } -> DEFAULT_RARE_DRAW_VIDEO_URI
            else -> null
        }
    }
    val rewardVideoPending = revealState.phase == DrawRevealPhase.Card &&
        currentReveal?.video != null &&
        revealState.index !in playedRewardVideoIndexes
    val showOpeningBackdrop = hasOpeningVideo &&
        revealState.phase != DrawRevealPhase.Summary &&
        revealState.phase != DrawRevealPhase.Done
    val rewardVideoUri = currentReveal?.video?.uri
        ?.takeIf { revealState.phase == DrawRevealPhase.RewardVideo }
    val transition = rememberInfiniteTransition(label = "draw-result")
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (current?.rarity == StudyRarity.Epic) 520 else 780),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "draw-pulse",
    )
    LaunchedEffect(revealState.index, revealState.phase, current?.rarity) {
        if (revealState.phase != DrawRevealPhase.Card || current == null) {
            cardRevealReady = true
            return@LaunchedEffect
        }
        cardRevealReady = false
        when (current.rarity) {
            StudyRarity.Normal -> {
                haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                delay(120)
            }
            StudyRarity.Rare -> {
                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                delay(360)
            }
            StudyRarity.Epic -> {
                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                delay(120)
                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                delay(480)
            }
            StudyRarity.Rainbow -> {
                repeat(3) {
                    haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    delay(150)
                }
                delay(420)
            }
        }
        cardRevealReady = true
    }
    fun nextPendingRewardVideoIndex(): Int? {
        val startIndex = revealState.index.coerceAtLeast(0)
        return results
            .withIndex()
            .firstOrNull { (index, reveal) ->
                index >= startIndex &&
                    reveal.video != null &&
                    index !in playedRewardVideoIndexes
            }
            ?.index
    }
    fun skipAll() {
        skipAllRequested = true
        val nextRewardVideoIndex = nextPendingRewardVideoIndex()
        revealState = if (nextRewardVideoIndex != null) {
            revealState.copy(index = nextRewardVideoIndex, phase = DrawRevealPhase.RewardVideo)
        } else {
            DrawRevealFlow.skip(revealState, drawResults)
        }
    }
    fun finishRewardVideo() {
        playedRewardVideoIndexes = playedRewardVideoIndexes + revealState.index
        val remaining = results.indices.firstOrNull { index ->
            results[index].video != null && index !in playedRewardVideoIndexes
        }
        revealState = if (skipAllRequested && remaining != null) {
            revealState.copy(index = remaining, phase = DrawRevealPhase.RewardVideo)
        } else if (skipAllRequested) {
            DrawRevealFlow.summary(revealState)
        } else {
            revealState.copy(phase = DrawRevealPhase.Card)
        }
    }
    fun closeCurrentVideo() {
        if (revealState.phase == DrawRevealPhase.RewardVideo) {
            finishRewardVideo()
            return
        }
        revealState = when (revealState.phase) {
            DrawRevealPhase.RainbowOpeningVideo -> DrawRevealFlow.videoFinished(revealState, drawResults)
            DrawRevealPhase.EpicOpeningVideo -> DrawRevealFlow.videoFinished(revealState, drawResults)
            DrawRevealPhase.RareOpeningVideo -> DrawRevealFlow.videoFinished(revealState, drawResults)
            DrawRevealPhase.RewardVideo -> revealState
            else -> DrawRevealFlow.skip(revealState, drawResults)
        }
    }
    LaunchedEffect(revealState.index, revealState.phase, currentReveal?.video?.uri, playedRewardVideoIndexes) {
        if (rewardVideoPending) {
            delay(420)
            revealState = revealState.copy(phase = DrawRevealPhase.RewardVideo)
        }
    }
    LaunchedEffect(revealState.phase) {
        if (revealState.phase == DrawRevealPhase.Done) {
            if (isSingleDraw) {
                onDismissRequest()
            } else {
                revealState = DrawRevealFlow.summary(revealState)
            }
        }
    }
    Dialog(
        onDismissRequest = { skipAll() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (showOpeningBackdrop) {
                        Modifier
                    } else {
                        Modifier.background(drawFullscreenBrush(current?.rarity ?: best))
                    }
                ),
        ) {
            if (showOpeningBackdrop && openingVideoUri != null) {
                DrawOpeningVideoLayer(
                    videoUri = openingVideoUri,
                    shouldPlay = revealState.phase == DrawRevealPhase.RainbowOpeningVideo ||
                        revealState.phase == DrawRevealPhase.EpicOpeningVideo ||
                        revealState.phase == DrawRevealPhase.RareOpeningVideo,
                    onFinished = {
                        if (revealState.phase == DrawRevealPhase.RainbowOpeningVideo ||
                            revealState.phase == DrawRevealPhase.EpicOpeningVideo ||
                            revealState.phase == DrawRevealPhase.RareOpeningVideo
                        ) {
                            revealState = DrawRevealFlow.videoFinished(revealState, drawResults)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (revealState.phase == DrawRevealPhase.RainbowOpeningVideo ||
                    revealState.phase == DrawRevealPhase.EpicOpeningVideo ||
                    revealState.phase == DrawRevealPhase.RareOpeningVideo
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.04f)),
                    )
                }
            }
            if (rewardVideoUri != null) {
                DrawRewardVideoLayer(
                    videoUri = rewardVideoUri,
                    playbackKey = "${revealState.index}:reward",
                    onFinished = {
                        finishRewardVideo()
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (revealState.phase == DrawRevealPhase.Summary) {
                DrawResultSummary(
                    results = drawResults,
                    onDismissRequest = onDismissRequest,
                    modifier = Modifier.align(Alignment.Center),
                )
                return@Box
            }
            IconButton(
                onClick = { closeCurrentVideo() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 18.dp, end = 14.dp)
                    .background(Color.Black.copy(alpha = 0.42f), CircleShape),
            ) {
                Text("×", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            }
            if (revealState.phase != DrawRevealPhase.RainbowOpeningVideo &&
                revealState.phase != DrawRevealPhase.EpicOpeningVideo &&
                revealState.phase != DrawRevealPhase.RareOpeningVideo &&
                revealState.phase != DrawRevealPhase.RewardVideo
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 28.dp)
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Spacer(Modifier.weight(1f))
                    AnimatedContent(
                        targetState = revealState.index to revealState.phase,
                        transitionSpec = {
                            val direction = if (targetState.first >= initialState.first) 1 else -1
                            val enter = fadeIn(tween(220)) +
                                slideInHorizontally(tween(360)) { width -> width * direction }
                            val exit = fadeOut(tween(180)) +
                                slideOutHorizontally(tween(300)) { width -> -width * direction }
                            enter togetherWith exit
                        },
                        label = "draw-card-fade",
                    ) { (index, phase) ->
                        val cardResult = if (phase == DrawRevealPhase.Card) drawResults.getOrNull(index) else null
                        if (cardResult == null) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.size(width = 236.dp, height = 316.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "愿光正在汇聚",
                                        color = Color.White.copy(alpha = 0.86f),
                                        fontWeight = FontWeight.Black,
                                    )
                                }
                            }
                        } else {
                            DrawRevealCard(result = cardResult, pulse = pulse)
                        }
                    }
                    Spacer(Modifier.weight(0.8f))
                    if (revealState.index < drawResults.lastIndex) {
                        Button(
                            onClick = { revealState = DrawRevealFlow.next(revealState, drawResults) },
                            enabled = revealState.phase == DrawRevealPhase.Card && !rewardVideoPending && cardRevealReady,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("下一个")
                        }
                        OutlinedButton(
                            onClick = { skipAll() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("跳过全部")
                        }
                    } else {
                        Button(
                            onClick = {
                                if (isSingleDraw) {
                                    onDismissRequest()
                                } else {
                                    revealState = DrawRevealFlow.summary(revealState)
                                }
                            },
                            enabled = revealState.phase == DrawRevealPhase.Card && !rewardVideoPending && cardRevealReady,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (isSingleDraw) "收下" else "查看结果")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawOpeningVideoLayer(
    videoUri: String,
    shouldPlay: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (shouldPlay) {
        DrawVideoLayer(
            videoUri = videoUri,
            playbackKey = "draw-opening:$videoUri",
            shouldPlay = true,
            freezeAtEnd = true,
            onFinished = onFinished,
            modifier = modifier,
        )
    } else {
        DrawVideoFrozenFrame(
            videoUri = videoUri,
            modifier = modifier,
        )
    }
}

private fun formatStudyTimeMetric(minutes: Int, pomodoros: Int): String =
    "${minutes}分 · ${pomodoros}个"

@Composable
private fun DrawRewardVideoLayer(
    videoUri: String,
    playbackKey: String,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DrawVideoLayer(
        videoUri = videoUri,
        playbackKey = playbackKey,
        shouldPlay = true,
        freezeAtEnd = false,
        onFinished = onFinished,
        modifier = modifier,
    )
}

@Composable
private fun DrawVideoLayer(
    videoUri: String,
    playbackKey: String,
    shouldPlay: Boolean,
    freezeAtEnd: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var videoView by remember(videoUri, playbackKey) { mutableStateOf<VideoView?>(null) }
    var completed by remember(videoUri, playbackKey) { mutableStateOf(false) }
    fun VideoView.freezeAtEnd() {
        seekTo((duration - 80).coerceAtLeast(0))
        pause()
    }
    fun VideoView.loadDrawVideo() {
        tag = "$videoUri#$playbackKey"
        completed = false
        setVideoURI(resolveAppVideoUri(context, videoUri))
        setOnPreparedListener { player ->
            player.isLooping = false
            runCatching {
                player.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            }
            if (shouldPlay) {
                start()
            } else {
                freezeAtEnd()
            }
        }
        setOnCompletionListener {
            if (!completed) {
                completed = true
                if (freezeAtEnd) {
                    freezeAtEnd()
                } else {
                    pause()
                }
                onFinished()
            }
        }
        setOnErrorListener { _, _, _ ->
            if (!completed) {
                completed = true
                onFinished()
            }
            true
        }
    }
    DisposableEffect(videoUri, playbackKey) {
        onDispose {
            videoView?.stopPlayback()
            videoView = null
        }
    }
    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { viewContext ->
            VideoView(viewContext).apply {
                videoView = this
                loadDrawVideo()
            }
        },
        update = { view ->
            if (videoView !== view) videoView = view
            if (view.tag != "$videoUri#$playbackKey") {
                view.stopPlayback()
                view.loadDrawVideo()
            } else if (shouldPlay && !completed && !view.isPlaying) {
                view.start()
            } else if (!shouldPlay && !completed && view.duration > 0) {
                view.freezeAtEnd()
            }
        },
    )
}

@Composable
private fun DrawVideoFrozenFrame(
    videoUri: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var bitmap by remember(videoUri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(videoUri) {
        bitmap = withContext(Dispatchers.IO) {
            loadVideoLastFrame(context, videoUri)
        }
    }
    val frame = bitmap
    if (frame != null) {
        Image(
            bitmap = frame.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.background(Color.Black),
        )
    } else {
        Box(
            modifier = modifier.background(drawFullscreenBrush(StudyRarity.Rainbow)),
        )
    }
}

private fun loadVideoLastFrame(context: Context, videoUri: String): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, resolveAppVideoUri(context, videoUri))
        val durationMs = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?: 0L
        val frameTimeUs = (durationMs - 80L).coerceAtLeast(0L) * 1_000L
        retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.getFrameAtTime()
    } catch (_: Throwable) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

@Composable
private fun DrawResultSummary(
    results: List<StudyDrawResult>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.White.copy(alpha = 0.9f),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 10.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(18.dp),
    ) {
        Column(
            modifier = Modifier
                .background(drawCardBrush(results.maxByOrNull { it.rarity.weight }?.rarity ?: StudyRarity.Normal))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "抽卡结果",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = Color.White,
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                results.chunked(5).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        row.forEach { result ->
                            DrawResultSquare(
                                result = result,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(5 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Button(onClick = onDismissRequest, modifier = Modifier.fillMaxWidth()) {
                Text("收下")
            }
        }
    }
}

@Composable
private fun DrawResultSquare(
    result: StudyDrawResult,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.White.copy(alpha = 0.88f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.aspectRatio(1f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(drawCardBrush(result.rarity))
                .padding(6.dp),
        ) {
            Text(
                result.title,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
            if (result.alreadyFull) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Text(
                        text = "碎片已满",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawRevealCard(result: StudyDrawResult, pulse: Float) {
    val borderColor = when (result.rarity) {
        StudyRarity.Normal -> Color.White.copy(alpha = 0.42f)
        StudyRarity.Rare -> Color(0xFFE0C7FF)
        StudyRarity.Epic -> Color(0xFFFFE4A3)
        StudyRarity.Rainbow -> Color(0xFFE8FFFF)
    }
    val borderWidth = when (result.rarity) {
        StudyRarity.Normal -> 1.dp
        StudyRarity.Rare -> 2.dp
        StudyRarity.Epic -> 3.dp
        StudyRarity.Rainbow -> 4.dp
    }
    Surface(
        color = Color.White.copy(alpha = 0.9f),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 8.dp,
        border = BorderStroke(borderWidth, borderColor),
        modifier = Modifier.size(width = 236.dp, height = 316.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(drawCardBrush(result.rarity))
                .padding(18.dp),
        ) {
            if (result.rarity != StudyRarity.Normal) {
                Canvas(Modifier.fillMaxSize()) {
                    val points = listOf(
                        0.12f to 0.16f,
                        0.78f to 0.12f,
                        0.34f to 0.30f,
                        0.86f to 0.44f,
                        0.18f to 0.58f,
                        0.70f to 0.70f,
                        0.42f to 0.86f,
                    )
                    val visiblePoints = when (result.rarity) {
                        StudyRarity.Rare -> points.take(3)
                        StudyRarity.Epic -> points.take(5)
                        StudyRarity.Rainbow -> points
                        StudyRarity.Normal -> emptyList()
                    }
                    visiblePoints.forEachIndexed { index, (x, y) ->
                        drawCircle(
                            color = Color.White.copy(alpha = 0.38f + (index % 3) * 0.14f),
                            radius = (2.5f + (index % 3) * 2f) * pulse,
                            center = Offset(size.width * x, size.height * y),
                        )
                    }
                }
            }
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.82f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size((62 * pulse).dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        when (result.rarity) {
                            StudyRarity.Epic -> "金"
                            StudyRarity.Rainbow -> "彩"
                            else -> result.rarity.label.take(1)
                        },
                        color = rarityColor(result.rarity),
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Column(
                modifier = Modifier.align(Alignment.BottomStart),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    result.rarity.label,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    result.title,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                )
                if (result.alreadyFull) {
                    Text(
                        text = "该蓝色碎片已集满，已为你展示本次抽取",
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

private fun drawFullscreenBrush(rarity: StudyRarity): Brush = when (rarity) {
    StudyRarity.Normal -> Brush.verticalGradient(listOf(Color(0xFF1F3D54), Color(0xFF5A8296), Color(0xFF0F1B2B)))
    StudyRarity.Rare -> Brush.verticalGradient(listOf(Color(0xFF251D52), Color(0xFF8067B7), Color(0xFF120D2C)))
    StudyRarity.Epic -> Brush.verticalGradient(listOf(Color(0xFF3A2400), Color(0xFFFFB938), Color(0xFF6F2E00)))
    StudyRarity.Rainbow -> Brush.verticalGradient(listOf(Color(0xFF07111F), Color(0xFF182B40), Color(0xFF05070D)))
}

@Composable
private fun MysteryBoxCelebration(reward: StudyMysteryBoxReward, onDismissRequest: () -> Unit) {
    val rarity = when (reward.kudos) {
        15, 25 -> StudyRarity.Normal
        50 -> StudyRarity.Rare
        else -> StudyRarity.Epic
    }
    val transition = rememberInfiniteTransition(label = "mystery-box")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { TextButton(onClick = onDismissRequest) { Text("收下") } },
        title = { Text("盲盒打开啦") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(drawBrush(rarity), RoundedCornerShape(18.dp))
                    .padding(18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.78f), modifier = Modifier.size((78 * pulse).dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("+${reward.kudos}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = rarityColor(rarity))
                        }
                    }
                    Text(mysteryBoxText(reward.kudos), color = Color.White, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(7) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.55f),
                                modifier = Modifier.size(((7 + it % 3 * 4) * pulse).dp),
                            ) {}
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun MysteryBoxPendingDialog(
    onOpen: () -> Unit,
    onKeep: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text("盲盒待开启") },
        text = {
            Text("番茄钟奖励已经放进收藏背包。现在开启就能看到奖励；不想开的话，之后也可以在收藏背包里打开。")
        },
        confirmButton = {
            Button(onClick = onOpen) {
                Text("开启盲盒")
            }
        },
        dismissButton = {
            TextButton(onClick = onKeep) {
                Text("先放背包")
            }
        },
    )
}

@Composable
private fun GachaCard(
    state: StudyState,
    onSingle: () -> Unit,
    onTen: () -> Unit,
    onPurple: () -> Unit,
) {
    val singleCost = if (StudyRules.hasSingleDrawDiscount(state)) StudyRules.DISCOUNT_SINGLE_DRAW_COST else StudyRules.SINGLE_DRAW_COST
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x55101427)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(590.dp)
                .clip(RoundedCornerShape(28.dp)),
        ) {
            StarryLetterBackdrop(Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.62f to Color(0x22101427),
                            1f to Color(0xEE0B0E1D),
                        ),
                    ),
            )
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                    ) {
                        Text(
                            "✦ 限定卡池",
                            color = Color(0xFFF4E8C9),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "夸夸值 ${state.wallet.kudos} · 券可抽 ${state.wallet.singleDrawTickets + state.wallet.tenDrawTickets * 10} 次",
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(
                    "星夜来信",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "把今天认真生活的痕迹，折进一封只属于你的信。",
                    color = Color(0xFFD9D8EA),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StarryRarityPill("紫", "8%", Color(0xFFC5A5FF))
                    StarryRarityPill("金", "1.5%", Color(0xFFF2D18A))
                    StarryRarityPill("彩", "0.35%", Color(0xFF8DE0DC))
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    StarryLetterSeal()
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FragmentWalletPill("抖音20分", state.inventory.douyinFragments, Color(0xFFC5A5FF))
                    FragmentWalletPill("剧场", state.inventory.theaterFragments, Color(0xFFD9B7FF))
                    FragmentWalletPill("游戏120分", state.inventory.gameFragments, Color(0xFFF2D18A))
                    FragmentWalletPill("视频卡", state.inventory.videoFragments, Color(0xFFFFDFA3))
                    FragmentWalletPill("番剧3小时", state.inventory.animeFragments, Color(0xFF8DE0DC))
                }
                Text(
                    "紫色：抖音20分钟 5.5% / 剧场 2.5% · 金色：游戏120分钟 / 视频",
                    color = Color.White.copy(alpha = 0.64f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                if (state.wallet.purpleDrawTickets > 0) {
                    Button(
                        onClick = onPurple,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF7D5BB3),
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text("今日零紫安全抽 · ${state.wallet.purpleDrawTickets}张", fontWeight = FontWeight.Bold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onSingle,
                        border = BorderStroke(1.dp, Color(0xFFD7C8F1)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Text("单抽 · $singleCost")
                    }
                    Button(
                        onClick = onTen,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE4C47F),
                            contentColor = Color(0xFF211A2E),
                        ),
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Text("十连 · ${StudyRules.TEN_DRAW_COST}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepHabitRewardCard(
    state: StudyState,
    assistantName: String,
) {
    val today = LocalDate.now()
    val earlySleepClaimed = StudyRules.hasClaimedSleepHabitReward(
        state = state,
        habit = StudySleepHabit.EarlySleep,
        date = today,
    )
    val earlyRiseClaimed = StudyRules.hasClaimedSleepHabitReward(
        state = state,
        habit = StudySleepHabit.EarlyRise,
        date = today,
    )
    StudyCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("作息任务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "个人标准：约01:30前睡、09:30前起。告诉 $assistantName 具体时间，由 TA 结合对话判断。每天每项一次。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        SleepHabitRewardRow(
            title = "昨晚早睡",
            reward = "+${StudyRules.EARLY_SLEEP_KUDOS} 夸夸值",
            claimed = earlySleepClaimed,
        )
        SleepHabitRewardRow(
            title = "今天早起",
            reward = "十连抽券 ×${StudyRules.EARLY_RISE_TEN_DRAW_TICKETS}",
            claimed = earlyRiseClaimed,
        )
    }
}

@Composable
private fun SleepHabitRewardRow(
    title: String,
    reward: String,
    claimed: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (claimed) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    reward,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (claimed) "今天已领取" else "等你告诉 TA",
                style = MaterialTheme.typography.labelLarge,
                color = if (claimed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StarryLetterBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(Brush.verticalGradient(listOf(Color(0xFF161B38), Color(0xFF34264E), Color(0xFF111427))))) {
        drawCircle(Color(0x33FFF4D6), radius = size.minDimension * 0.22f, center = Offset(size.width * 0.82f, size.height * 0.18f))
        val stars = listOf(
            0.10f to 0.17f, 0.22f to 0.10f, 0.34f to 0.23f, 0.48f to 0.13f,
            0.66f to 0.27f, 0.88f to 0.34f, 0.13f to 0.43f, 0.72f to 0.48f,
            0.42f to 0.57f, 0.91f to 0.62f,
        )
        stars.forEachIndexed { index, (x, y) ->
            drawCircle(
                color = if (index % 3 == 0) Color(0xFFF4D99C) else Color.White.copy(alpha = 0.78f),
                radius = if (index % 3 == 0) 3.2f else 2f,
                center = Offset(size.width * x, size.height * y),
            )
        }
        stars.zipWithNext().take(6).forEach { (from, to) ->
            drawLine(
                color = Color(0x335F7FE0),
                start = Offset(size.width * from.first, size.height * from.second),
                end = Offset(size.width * to.first, size.height * to.second),
                strokeWidth = 1.4f,
            )
        }
    }
}

@Composable
private fun StarryLetterSeal() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFF2E8DD).copy(alpha = 0.94f),
        shadowElevation = 18.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
        modifier = Modifier.size(width = 236.dp, height = 146.dp).rotate(-3f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawLine(Color(0x334A3658), Offset(0f, 0f), Offset(size.width / 2f, size.height * 0.62f), 2f)
                drawLine(Color(0x334A3658), Offset(size.width, 0f), Offset(size.width / 2f, size.height * 0.62f), 2f)
            }
            Surface(
                shape = CircleShape,
                color = Color(0xFF7B3F63),
                border = BorderStroke(2.dp, Color(0xFFE0B887)),
                modifier = Modifier.size(58.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("✦", color = Color(0xFFFFE4AA), style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}

@Composable
private fun StarryRarityPill(label: String, chance: String, color: Color) {
    Surface(shape = RoundedCornerShape(999.dp), color = color.copy(alpha = 0.14f), border = BorderStroke(1.dp, color.copy(alpha = 0.52f))) {
        Text("$label $chance", color = color, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

@Composable
private fun FragmentWalletPill(label: String, count: Int, color: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color.Black.copy(alpha = 0.20f), border = BorderStroke(1.dp, color.copy(alpha = 0.32f))) {
        Text("$label $count", color = Color.White.copy(alpha = 0.88f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
    }
}

@Composable
private fun CollectionCard(
    inventory: StudyInventory,
    onUseUniversalNormalTarget: (String) -> Unit,
    onOpenMysteryBox: (Int) -> Unit,
    onRedeemDouyin: () -> Unit,
    onRedeemGame: () -> Unit,
    onRedeemAnime: () -> Unit,
    onOpenStarWish: () -> Unit,
    onOpenImageGen: (String) -> Unit,
) {
    var collectionSection by remember { mutableStateOf(CollectionSection.Scrolls) }
    var selectedOutfit by remember { mutableStateOf<String?>(null) }
    var pendingNormalTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    StudyCard {
        Text("收藏背包", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (inventory.unopenedMysteryBoxes.isNotEmpty()) {
            Surface(color = StudyColors.hero.copy(alpha = 0.72f), shape = MaterialTheme.shapes.medium) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(HugeIcons.Package, null, tint = StudyColors.goldText)
                    Column(Modifier.weight(1f)) {
                        Text("未开启盲盒", fontWeight = FontWeight.SemiBold)
                        Text(
                            "还有 ${inventory.unopenedMysteryBoxes.size} 个番茄钟盲盒可以打开",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(onClick = { onOpenMysteryBox(0) }) {
                        Text("开启")
                    }
                }
            }
        }
        Surface(color = StudyColors.softBlue.copy(alpha = 0.92f), shape = MaterialTheme.shapes.medium) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(HugeIcons.Package, null, tint = StudyColors.blue)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("通用普通碎片", fontWeight = FontWeight.SemiBold)
                    Text("点开画卷部件后可指定补 1 片", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${inventory.universalNormalFragments}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = StudyColors.blue)
            }
        }
        EntertainmentRewardRow(
            label = "抖音时长券 · 20分钟",
            count = inventory.douyinFragments,
            color = StudyColors.purple,
            actions = listOf("刷抖音" to onRedeemDouyin),
        )
        EntertainmentRewardRow(
            label = "剧场碎片",
            count = inventory.theaterFragments,
            color = StudyColors.purple,
            actions = listOf("小剧场" to onOpenStarWish),
        )
        EntertainmentRewardRow(
            label = "游戏畅玩券 · 120分钟",
            count = inventory.gameFragments,
            color = StudyColors.goldText,
            actions = listOf("玩游戏" to onRedeemGame),
        )
        EntertainmentRewardRow(
            label = "视频解锁卡",
            count = inventory.videoFragments,
            color = StudyColors.goldText,
            actions = listOf("视频馆" to onOpenStarWish),
        )
        EntertainmentRewardRow(
            label = "番剧兑换券 · 3小时",
            count = inventory.animeFragments,
            color = Color(0xFF23C8B8),
            actions = listOf("看动漫" to onRedeemAnime),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = collectionSection == CollectionSection.Scrolls,
                onClick = { collectionSection = CollectionSection.Scrolls },
                label = { Text("已解锁画卷 ${inventory.unlockedOutfits.size}/${StudyRules.outfitNames.size}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = collectionSection == CollectionSection.Theaters,
                onClick = { collectionSection = CollectionSection.Theaters },
                label = { Text("小剧场 ${StudyRules.theaterNames.size}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.weight(1f),
            )
        }
        CollectionProgressList(
            inventory = inventory,
            section = collectionSection,
            selectedOutfit = selectedOutfit,
            onSelectOutfit = { selectedOutfit = if (selectedOutfit == it) null else it },
            onUseUniversalNormalTarget = { key, label -> pendingNormalTarget = key to label },
            onOpenImageGen = onOpenImageGen,
        )
    }
    pendingNormalTarget?.let { (key, label) ->
        AlertDialog(
            onDismissRequest = { pendingNormalTarget = null },
            title = { Text("使用通用普通碎片？") },
            text = {
                Text(
                    if ((inventory.normalFragments[key] ?: 0) >= StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT) {
                        "$label 已经满 ${StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT} 片，继续使用会转换成100夸夸值。"
                    } else {
                        "要给 $label 增加 1 个碎片吗？"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUseUniversalNormalTarget(key)
                        pendingNormalTarget = null
                    },
                ) { Text("使用") }
            },
            dismissButton = { TextButton(onClick = { pendingNormalTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun EntertainmentRewardRow(
    label: String,
    count: Int,
    color: Color,
    actions: List<Pair<String, () -> Unit>>,
) {
    Surface(color = Color.White.copy(alpha = 0.72f), shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold, color = color)
                Text("$count 枚", style = MaterialTheme.typography.bodySmall)
            }
            actions.forEach { (title, action) ->
                TextButton(onClick = action, enabled = count > 0 || title in setOf("小剧场", "AI 视频")) {
                    Text(title)
                }
            }
        }
    }
}

@Composable
private fun DrawPoolChip(label: String, value: String, color: Color) {
    Surface(color = Color.White.copy(alpha = 0.78f), shape = CircleShape) {
        Text(
            text = "$label $value",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CollectionProgressList(
    inventory: StudyInventory,
    section: CollectionSection,
    selectedOutfit: String?,
    onSelectOutfit: (String) -> Unit,
    onUseUniversalNormalTarget: (String, String) -> Unit,
    onOpenImageGen: (String) -> Unit,
) {
    when (section) {
        CollectionSection.Scrolls -> {
            Text("画卷收集进度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            StudyRules.outfitNames.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { outfit ->
                        val fragmentCount = inventory.normalOutfitTotal(outfit)
                            .coerceAtMost(StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT)
                        OutfitSummaryTile(
                            outfit = outfit,
                            fragmentCount = fragmentCount,
                            unlocked = outfit in inventory.unlockedOutfits,
                            selected = selectedOutfit == outfit,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectOutfit(outfit) },
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            selectedOutfit?.let { outfit ->
                val fragmentCount = inventory.normalOutfitTotal(outfit)
                    .coerceAtMost(StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT)
                val completedParts = if (fragmentCount >= StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT) 1 else 0
                OutfitProgressCard(
                    outfit = outfit,
                    fragmentCount = fragmentCount,
                    completedParts = completedParts,
                    inventory = inventory,
                    onUseUniversalNormalTarget = onUseUniversalNormalTarget,
                    onOpenImageGen = onOpenImageGen,
                )
            }
        }
        CollectionSection.Theaters -> {
            Text("小剧场进度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("剧场碎片专门用于小剧场。去星愿馆选择任意小剧场，花 1 枚生成或续写 1 章。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            CollectionProgressRow(
                title = "当前剧场碎片",
                detail = "${inventory.theaterFragments} 个 · 每 1 个可兑换 1 章",
                progress = inventory.theaterFragments.coerceAtMost(1).toFloat(),
                unlocked = inventory.theaterFragments >= 1,
            )
            StudyRules.theaterNames.forEach { theater ->
                CollectionProgressRow(
                    title = theater,
                    detail = "候选剧情",
                    progress = 0f,
                    unlocked = inventory.theaterFragments >= 1,
                )
            }
        }
    }
}

private fun StudyInventory.normalOutfitTotal(outfit: String): Int {
    val prefix = "normal:$outfit:"
    return normalFragments.entries.sumOf { (key, count) -> if (key.startsWith(prefix)) count else 0 }
}

@Composable
private fun OutfitSummaryTile(
    outfit: String,
    fragmentCount: Int,
    unlocked: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val progress = (fragmentCount / StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT.toFloat()).coerceIn(0f, 1f)
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = when {
            selected -> StudyColors.hero.copy(alpha = 0.88f)
            unlocked -> StudyColors.softBlue.copy(alpha = 0.88f)
            else -> Color.White.copy(alpha = 0.58f)
        },
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(42.dp),
                    strokeWidth = 4.dp,
                    color = if (unlocked) StudyColors.goldText else StudyColors.blue,
                    trackColor = Color.White.copy(alpha = 0.62f),
                )
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f)) {
                Text(outfit, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (unlocked) "已解锁" else "$fragmentCount/${StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OutfitProgressCard(
    outfit: String,
    fragmentCount: Int,
    completedParts: Int,
    inventory: StudyInventory,
    onUseUniversalNormalTarget: (String, String) -> Unit,
    onOpenImageGen: (String) -> Unit,
) {
    val unlocked = outfit in inventory.unlockedOutfits
    Surface(
        color = if (unlocked) StudyColors.hero.copy(alpha = 0.72f) else StudyColors.softBlue.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(outfit, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (unlocked) {
                            "完整画卷已解锁"
                        } else {
                            "$completedParts/1 专属碎片 · $fragmentCount/${StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT} 片"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (unlocked) {
                        "已解锁"
                    } else {
                        "${(fragmentCount * 100 / StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT).coerceIn(0, 100)}%"
                    },
                    color = StudyColors.goldText,
                )
            }
            LinearProgressIndicator(
                progress = { fragmentCount / StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            if (unlocked) {
                FilledTonalButton(onClick = { onOpenImageGen(outfit) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(HugeIcons.AiMagic, null)
                    Spacer(Modifier.width(8.dp))
                    Text("用这套造型去生图")
                }
            }
            StudyRules.outfitParts.forEach { part ->
                val key = "normal:$outfit:$part"
                val count = inventory.normalOutfitTotal(outfit)
                    .coerceAtMost(StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT)
                CollectionProgressRow(
                    title = part,
                    detail = "$count/${StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT}",
                    progress = count / StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT.toFloat(),
                    unlocked = count >= StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT,
                    enabled = inventory.universalNormalFragments > 0,
                    onClick = if (inventory.universalNormalFragments > 0) {
                        { onUseUniversalNormalTarget(key, "$outfit · $part") }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun CollectionProgressRow(
    title: String,
    detail: String,
    progress: Float,
    unlocked: Boolean,
    action: (@Composable () -> Unit)? = null,
    enabled: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = if (onClick != null) {
            Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(4.dp)
        } else {
            Modifier
        },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (unlocked) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (unlocked) StudyColors.goldText else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (action != null) {
                Spacer(Modifier.width(6.dp))
                action()
            } else if (onClick != null && enabled) {
                Spacer(Modifier.width(6.dp))
                Text("点按使用通用", style = MaterialTheme.typography.labelSmall, color = StudyColors.blue)
            }
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AchievementCard(state: StudyState, onClaim: (String) -> Unit) {
    val claimable = StudyRules.claimableAchievements(state).map { it.id }.toSet()
    StudyCard {
        Text("成就墙", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        StudyRules.achievements
            .sortedBy { it.id in state.claimedAchievementIds }
            .forEach { achievement ->
            AchievementRow(
                achievement = achievement,
                claimed = achievement.id in state.claimedAchievementIds,
                claimable = achievement.id in claimable,
                onClaim = { onClaim(achievement.id) },
            )
        }
    }
}

@Composable
private fun AchievementRow(achievement: StudyAchievement, claimed: Boolean, claimable: Boolean, onClaim: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(HugeIcons.Favourite, null, tint = if (claimed || claimable) StudyColors.goldText else MaterialTheme.colorScheme.outline)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(achievement.title, fontWeight = FontWeight.SemiBold)
            Text("${achievement.condition} · ${achievement.reward.title}", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onClaim, enabled = claimable && !claimed) {
            Text(if (claimed) "已领" else "领取")
        }
    }
}

@Composable
private fun ShopCard(state: StudyState, onRefresh: () -> Unit, onBuy: (StudyShopItem) -> Unit) {
    val canRefresh = state.manualShopRefreshDate != state.today
    StudyCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("神秘商店", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onRefresh, enabled = canRefresh) {
                Text(if (canRefresh) "刷新一次" else "今日已刷新")
            }
        }
        Text("每天自动刷新 3 件商品；手动刷新每天最多一次。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.shopItems.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(HugeIcons.Package, null, tint = StudyColors.blue)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, fontWeight = FontWeight.SemiBold)
                    Text("${item.price} 夸夸值", style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { onBuy(item) },
                    enabled = item.id !in state.purchasedShopItemIds && state.wallet.kudos >= item.price,
                ) { Text(if (item.id in state.purchasedShopItemIds) "已购" else "购买") }
            }
        }
    }
}

@Composable
private fun StudyGuideCard() {
    StudyCard {
        Text("奖励系统说明", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        GuideBlock(
            title = "每日获取",
            lines = listOf(
                "签到：每天固定 50 夸夸值。",
                "昨晚早睡：按你的作息，约01:30前入睡并经角色认可，可发 ${StudyRules.EARLY_SLEEP_KUDOS} 夸夸值。",
                "今天早起：按你的作息，约09:30前起床并经角色认可，可发十连抽券 x${StudyRules.EARLY_RISE_TEN_DRAW_TICKETS}。",
                "累计学习每满 ${StudyRules.STUDY_REWARD_INTERVAL_MINUTES} 分钟：${StudyRules.STUDY_REWARD_KUDOS} 夸夸值；不足部分跨番茄保留。",
                "完成 1 项待办：50 夸夸值。",
                "今日待办全清：触发超神时刻，固定给十连券 x1。",
                "普通卡池连续 ${StudyRules.NON_NORMAL_PITY_DRAW_COUNT} 抽没有紫/金/彩：第 ${StudyRules.NON_NORMAL_PITY_DRAW_COUNT} 抽直接保底紫色。",
            ),
        )
        GuideBlock(
            title = "抽卡与收藏",
            lines = listOf(
                "单抽 ${StudyRules.SINGLE_DRAW_COST} 夸夸值。",
                "十连 ${StudyRules.TEN_DRAW_COST} 夸夸值。",
                "画卷碎片已经集满后再抽到同名蓝色碎片，仍会在抽卡结果中展示，并标记为“碎片已满”；不会重复计入，也不返夸夸值、抽卡券或其他资源。",
                "蓝色画卷专属碎片 90.15%；紫色 8%（抖音20分钟 5.5% / 剧场碎片 2.5%）。",
                "金色 1.5%（游戏120分钟 1.2% / 视频解锁卡 0.3%）；彩色番剧3小时 0.35%。",
                "硬保底：连续 ${StudyRules.NON_NORMAL_PITY_DRAW_COUNT} 抽没有出现紫/金/彩时，第 ${StudyRules.NON_NORMAL_PITY_DRAW_COUNT} 抽必为紫色。",
                "卡池、等级和神秘商店都不再产出通用碎片；旧存档中的通用碎片仍可使用。",
                "每套画卷需要 10 个专属碎片；通用普通碎片可以补任意一套未满画卷。",
                "娱乐券抽到即拥有；剧场碎片每枚可生成或续写小剧场 1 章。",
            ),
        )
        GuideBlock(
            title = "每日抽数估算",
            lines = listOf(
                "学习2小时约得2400夸夸值，可抽3次十连；学习3小时约得3600夸夸值，可抽4次十连加4次单抽。",
                "超神 5 天给 5 张十连券；等级、成就和商店会追加抽卡券。",
                "按100抽估算：蓝色约90，紫色约8，金色约1至2，彩色约0至1。",
                "普通图片专属碎片只来自抽卡；新奖励不再产生通用普通碎片。",
            ),
        )
        GuideBlock(
            title = "惩罚机制",
            lines = listOf(
                "连续 2 天没有番茄钟或待办完成：扣 50 夸夸值。",
                "连续 3 天及以上：每天扣 100 夸夸值。",
                "恢复学习行为后连续未学习计数清零；夸夸值不会变成负数。",
            ),
        )
        GuideBlock(
            title = "陪伴机制",
            lines = listOf(
                "番茄钟开始前可选择语音鼓励。",
                "番茄钟里可以和角色轻声聊天。",
                "番茄钟结束后按实际累计学习时长发放夸夸值。",
                "作息奖励按你的个人基线判断；角色必须知道具体时间，描述含糊会追问，明显矛盾或太晚会拒绝。",
            ),
        )
        GuideBlock(
            title = "当前已落地",
            lines = listOf(
                "签到、作息任务、待办、番茄钟、盲盒、惩罚、抽卡、超神、等级、成就、商店都已接入本地状态。",
                "收藏已按 20 套画卷、每套 10 个专属碎片展示。",
                "旧存档通用普通碎片仍可自动补最佳目标，也可在收藏里指定画卷；新系统不再产出。",
                "娱乐券与剧场碎片均按用途独立保存，卡池内不存在任何通用碎片。",
                "Lv14 会自动补齐一套未完成画卷；已解锁画卷可以直接跳到生图页。",
                "番茄钟已接入角色陪伴、语音鼓励和轻聊天。",
                "更深的角色主动督学、画卷提示词自动带入、星愿馆视频收藏柜可以作为后续增强。",
            ),
        )
    }
}

@Composable
private fun GuideBlock(title: String, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        lines.forEach { line ->
            Text("· $line", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecentEventsCard(events: List<StudyEvent>) {
    StudyCard {
        Text("奖励记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (events.isEmpty()) {
            Text("完成一个待办或番茄钟后，这里会亮起来。")
        }
        events.take(6).forEach { event ->
            Text("· ${event.title} ${event.detail}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CompanionPrepCard(
    assistant: Assistant,
    voiceEnabled: Boolean,
    onVoiceToggle: (Boolean) -> Unit,
) {
    StudyCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("开始一轮番茄钟", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${assistant.name}会在倒计时里陪你轻声聊天。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = voiceEnabled, onClick = { onVoiceToggle(!voiceEnabled) }, label = { Text("语音鼓励") })
        }
    }
}

@Composable
private fun DurationCard(
    selectedMinutes: Int,
    customMinutes: String,
    onSelect: (Int) -> Unit,
    onCustom: (String) -> Unit,
) {
    StudyCard {
        Text("选择时长", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(25, 40, 50, 90).forEach { minutes ->
                FilterChip(selected = selectedMinutes == minutes, onClick = { onSelect(minutes) }, label = { Text("${minutes}分钟") })
            }
        }
        OutlinedTextField(
            value = customMinutes,
            onValueChange = onCustom,
            label = { Text("自定义分钟") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val rewardBlocks = selectedMinutes.coerceAtLeast(0) / StudyRules.STUDY_REWARD_INTERVAL_MINUTES
        Text(
            "本轮预计 +${rewardBlocks * StudyRules.STUDY_REWARD_KUDOS} 夸夸值 · ${rewardBlocks} 次单抽进度",
            color = StudyColors.purple,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PomodoroTimerCircle(
    timeText: String,
    task: String,
    progress: Float,
    palette: PomodoroFocusPalette,
) {
    Box(
        modifier = Modifier
            .size(246.dp)
            .clip(CircleShape)
            .background(palette.timerSurface),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.size(222.dp),
            strokeWidth = 5.dp,
            color = palette.ring,
            trackColor = palette.track,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.displayLarge,
                color = palette.primaryText,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = task,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.secondaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 36.dp),
            )
        }
    }
}

@Composable
private fun FocusChatPanel(
    userLine: String,
    chatText: String,
    assistantName: String,
    palette: PomodoroFocusPalette,
    onChatChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (userLine.isNotBlank()) {
            Text(
                "我：$userLine",
                style = MaterialTheme.typography.bodyLarge,
                color = palette.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            color = palette.inputSurface,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = chatText,
                    onValueChange = onChatChange,
                    placeholder = { Text("跟${assistantName}说一句...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = RoundedCornerShape(20.dp),
                )
                Surface(
                    color = if (chatText.isNotBlank()) palette.action else palette.actionMuted,
                    shape = CircleShape,
                ) {
                    IconButton(onClick = onSend, enabled = chatText.isNotBlank()) {
                        Icon(HugeIcons.ArrowUp02, null, tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.72f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

private data class PomodoroFocusPalette(
    val background: List<Color>,
    val topGlow: Color,
    val bottomGlow: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val ring: Color,
    val track: Color,
    val timerSurface: Color,
    val inputSurface: Color,
    val action: Color,
    val actionMuted: Color,
)

private fun focusPalette(theme: PomodoroTheme): PomodoroFocusPalette = when (theme) {
    PomodoroTheme.CLOUD -> PomodoroFocusPalette(
        background = listOf(Color(0xFFF4F6F6), Color(0xFFEEF3F4), Color(0xFFF2F3F1)),
        topGlow = Color.White.copy(alpha = 0.18f),
        bottomGlow = Color(0xFF5C6B7D).copy(alpha = 0.06f),
        primaryText = Color(0xFF35434D),
        secondaryText = Color(0xFF667782),
        ring = Color(0xFF7895A6),
        track = Color.White.copy(alpha = 0.34f),
        timerSurface = Color.White.copy(alpha = 0.24f),
        inputSurface = Color(0xFFFFF8FB).copy(alpha = 0.92f),
        action = StudyColors.blue,
        actionMuted = StudyColors.softBlue,
    )
    PomodoroTheme.MIDNIGHT -> PomodoroFocusPalette(
        background = listOf(Color(0xFF111827), Color(0xFF172033), Color(0xFF0F172A)),
        topGlow = Color(0xFF88A9C0).copy(alpha = 0.12f),
        bottomGlow = Color.Black.copy(alpha = 0.18f),
        primaryText = Color(0xFFE8EEF5),
        secondaryText = Color(0xFFB2C1CF),
        ring = Color(0xFF88A9C0),
        track = Color.White.copy(alpha = 0.12f),
        timerSurface = Color(0xFF253247).copy(alpha = 0.72f),
        inputSurface = Color(0xFF1C2738).copy(alpha = 0.94f),
        action = Color(0xFF668EAA),
        actionMuted = Color(0xFF34485B),
    )
}

@Composable
private fun PomodoroThemePickerDialog(
    selected: PomodoroTheme,
    onSelect: (PomodoroTheme) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("专注氛围") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    PomodoroTheme.CLOUD to "云雾原版",
                    PomodoroTheme.MIDNIGHT to "深夜墨蓝",
                ).forEach { (theme, label) ->
                    val palette = focusPalette(theme)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                onSelect(theme)
                                onDismiss()
                            },
                        color = palette.background[1],
                        border = BorderStroke(
                            width = if (selected == theme) 2.dp else 1.dp,
                            color = if (selected == theme) palette.ring else palette.track,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(34.dp),
                                shape = CircleShape,
                                color = palette.timerSurface,
                                border = BorderStroke(3.dp, palette.ring),
                            ) {}
                            Text(
                                text = label,
                                color = palette.primaryText,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected == theme) Text("已选择", color = palette.secondaryText)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

private object StudyColors {
    val page = Color(0xFFF7F3EA)
    val starryPage = Color(0xFF151831)
    val starryPageDeep = Color(0xFF090C1B)
    val hero = Color(0xFFFFE6B8)
    val softBlue = Color(0xFFDCECF4)
    val blue = Color(0xFF3D7EA6)
    val purple = Color(0xFF8067B7)
    val goldText = Color(0xFF9B6B10)
}

private fun heroBrush(): Brush = Brush.linearGradient(
    listOf(Color(0xFFFFE5AE), Color(0xFFE2F0F7), Color(0xFFFFF8D8))
)

private fun focusBrush(theme: PomodoroTheme): Brush = Brush.verticalGradient(
    focusPalette(theme).background,
)

private fun rarityColor(rarity: StudyRarity): Color = when (rarity) {
    StudyRarity.Normal -> StudyColors.blue
    StudyRarity.Rare -> StudyColors.purple
    StudyRarity.Epic -> StudyColors.goldText
    StudyRarity.Rainbow -> Color(0xFF23C8B8)
}

private val StudyRarity.weight: Int
    get() = when (this) {
        StudyRarity.Normal -> 1
        StudyRarity.Rare -> 2
        StudyRarity.Epic -> 3
        StudyRarity.Rainbow -> 4
    }

private fun drawResultTitle(best: StudyRarity, count: Int): String {
    return when (best) {
        StudyRarity.Rainbow -> "彩色光芒亮起来了"
        StudyRarity.Epic -> "金光炸开了"
        StudyRarity.Rare -> if (count >= 10) "十连有好东西" else "紫色碎片出现"
        StudyRarity.Normal -> if (count >= 10) "十连结果" else "抽卡结果"
    }
}

private fun superMomentBrush(): Brush = Brush.linearGradient(
    listOf(Color(0xFFFFC857), Color(0xFFFF7AA2), Color(0xFF7C6BFF))
)

private fun gachaBrush(): Brush = Brush.linearGradient(
    listOf(Color(0xFF6F8FA6), Color(0xFF8067B7), Color(0xFFE0A72E))
)

private fun drawBrush(rarity: StudyRarity): Brush = when (rarity) {
    StudyRarity.Normal -> Brush.linearGradient(listOf(Color(0xFF8CC7D8), Color(0xFF6F8FA6)))
    StudyRarity.Rare -> Brush.linearGradient(listOf(Color(0xFF8067B7), Color(0xFFB88BCE)))
    StudyRarity.Epic -> Brush.linearGradient(listOf(Color(0xFFFFC857), Color(0xFFFF8F5A), Color(0xFFFFF2B3)))
    StudyRarity.Rainbow -> Brush.linearGradient(
        listOf(Color(0xFF5DE0E6), Color(0xFFFF6B9A), Color(0xFFFFD166), Color(0xFF9B5DE5)),
    )
}

private fun drawCardBrush(rarity: StudyRarity): Brush = when (rarity) {
    StudyRarity.Rainbow -> Brush.linearGradient(
        listOf(Color(0xFF163B52), Color(0xFF7D4A91), Color(0xFFC06B78), Color(0xFF1E5B63)),
    )
    else -> drawBrush(rarity)
}

private fun mysteryBoxText(kudos: Int): String = when (kudos) {
    15 -> "柔光蓝，星点飘浮。获得 15 夸夸值。"
    25 -> "流光蓝，光带环绕。获得 25 夸夸值。"
    50 -> "幽雅紫，花瓣光晕。获得 50 夸夸值。"
    100 -> "暖金亮起来了。获得 100 夸夸值。"
    else -> "璨金粒子炸开。获得 200 夸夸值。"
}

private fun secondsText(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "%02d:%02d".format(safe / 60, safe % 60)
}

private fun Int.toRecordedMinutes(): Int {
    return (this.coerceAtLeast(0) / 60).coerceAtLeast(0)
}

private fun studyDurationText(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / 60
    val restSeconds = safe % 60
    return when {
        safe < 60 -> "${restSeconds}秒"
        restSeconds == 0 -> "${minutes}分钟"
        else -> "${minutes}分${restSeconds}秒"
    }
}

private fun buildEncourageLine(taskText: String, assistant: Assistant): String {
    val target = taskText.ifBlank { "这一轮任务" }
    return "${assistant.name.ifBlank { "当前角色" }}：先不想那么远，我们只把“$target”往前推一点点。"
}

private fun buildStudyChatPrompt(userText: String, taskText: String): String {
    val target = taskText.ifBlank { "这一轮学习任务" }
    return "我正在番茄钟学习，任务是“$target”。我想对你说：$userText\n请按你的角色人设自然回复，短一点，并保持角色自己的关系边界和表达方式。只输出你要说出口的话。"
}

private fun buildPomodoroOpeningPrompt(taskText: String): String {
    val target = taskText.ifBlank { "这一轮学习任务" }
    return "用户刚打开番茄钟专注页，准备开始“$target”。请按你的角色人设和关系边界给一句非常短的鼓励，不要预设监督职责或身体距离。不要解释任务，不要输出提示词。"
}
