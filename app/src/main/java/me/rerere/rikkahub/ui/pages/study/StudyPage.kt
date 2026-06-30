package me.rerere.rikkahub.ui.pages.study

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.AiMagic
import me.rerere.hugeicons.stroke.BookOpen02
import me.rerere.hugeicons.stroke.Chart
import me.rerere.hugeicons.stroke.Clapping01
import me.rerere.hugeicons.stroke.Clock02
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Favourite
import me.rerere.hugeicons.stroke.InLove
import me.rerere.hugeicons.stroke.Package
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.VolumeHigh
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.study.StudyAchievement
import me.rerere.rikkahub.data.study.StudyDrawResult
import me.rerere.rikkahub.data.study.StudyEvent
import me.rerere.rikkahub.data.study.StudyInventory
import me.rerere.rikkahub.data.study.StudyRarity
import me.rerere.rikkahub.data.study.StudyRules
import me.rerere.rikkahub.data.study.StudyShopItem
import me.rerere.rikkahub.data.study.StudyState
import me.rerere.rikkahub.data.study.StudyTask
import me.rerere.rikkahub.data.study.SuperMomentChoice
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.uuid.Uuid

private enum class StudySection(val label: String) {
    Today("今日"),
    Gacha("抽卡"),
    Collection("收藏"),
    Achievements("成就"),
    Shop("商店"),
    Guide("说明"),
}

@Composable
fun StudyPage(vm: StudyVM = koinViewModel()) {
    val navController = LocalNavController.current
    val settings = LocalSettings.current
    val assistant = settings.getCurrentAssistant()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var section by remember { mutableStateOf(StudySection.Today) }
    var newTask by remember { mutableStateOf("") }
    var drawDialog by remember { mutableStateOf<List<StudyDrawResult>?>(null) }
    var boxDialog by remember { mutableStateOf<Int?>(null) }
    var showMcDonaldsDialog by remember { mutableStateOf(false) }
    var showSuperDialog by remember { mutableStateOf(false) }
    var showLevelDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                is StudyEffect.Message -> snackbarHostState.showSnackbar(effect.text)
                is StudyEffect.MysteryBox -> boxDialog = effect.kudos
                is StudyEffect.DrawResults -> drawDialog = effect.results
                StudyEffect.McDonaldsRedeemed -> showMcDonaldsDialog = true
                StudyEffect.SuperMomentReady -> showSuperDialog = true
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("考研") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = StudyColors.page,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(StudyColors.page),
            contentPadding = padding + PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SectionChips(selected = section, onSelected = { section = it })
            }
            when (section) {
                StudySection.Today -> {
                    item {
                        StudyHero(
                            state = state,
                            assistant = assistant,
                            onSignIn = vm::signIn,
                            onPomodoro = { navController.navigate(Screen.StudyPomodoro) },
                            onOpenLevel = { showLevelDialog = true },
                        )
                    }
                    item {
                        TaskCard(
                            tasks = state.tasks,
                            newTask = newTask,
                            onNewTask = { newTask = it },
                            onAdd = {
                                vm.addTask(newTask)
                                newTask = ""
                            },
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
                    item { InactivityStatusCard(state) }
                    item { DailyIncomeCard() }
                    item { RecentEventsCard(events = state.recentEvents) }
                }
                StudySection.Gacha -> {
                    item {
                        GachaCard(
                            state = state,
                            onSingle = { vm.draw(1) },
                            onTen = { vm.draw(10) },
                        )
                    }
                }
                StudySection.Collection -> {
                    item {
                        CollectionCard(
                            inventory = state.inventory,
                            onUseUniversalNormal = vm::applyBestUniversalNormal,
                            onUseUniversalNormalTarget = vm::applyUniversalNormal,
                            onUseUniversalRare = vm::applyBestUniversalRare,
                            onUseUniversalRareTarget = vm::applyUniversalRare,
                            onUseUniversalEpic = vm::applyUniversalEpic,
                            onRedeemMcDonalds = vm::redeemMcDonalds,
                            onOpenImageGen = { navController.navigate(Screen.ImageGen) },
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

    boxDialog?.let { kudos ->
        MysteryBoxCelebration(
            kudos = kudos,
            onDismissRequest = { boxDialog = null },
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
            assistant = assistant,
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

    if (showMcDonaldsDialog) {
        McDonaldsCelebration(onDismissRequest = { showMcDonaldsDialog = false })
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
    val assistant = settings.getCurrentAssistant()
    var minutes by remember { mutableIntStateOf(25) }
    var customMinutes by remember { mutableStateOf("") }
    var taskText by remember { mutableStateOf("") }
    var imageEnabled by remember { mutableStateOf(true) }
    var voiceEnabled by remember { mutableStateOf(true) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("番茄钟") },
                navigationIcon = { BackButton() },
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
                    imageEnabled = imageEnabled,
                    voiceEnabled = voiceEnabled,
                    onImageToggle = { imageEnabled = it },
                    onVoiceToggle = { voiceEnabled = it },
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
                                imageEnabled = imageEnabled,
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
    val tts = LocalTTSState.current
    val scope = rememberCoroutineScope()
    val safeMinutes = minutes.coerceAtLeast(1)
    var remainingSeconds by remember(safeMinutes) { mutableIntStateOf(safeMinutes * 60) }
    var finished by remember { mutableStateOf(false) }
    var studyConversationId by remember { mutableStateOf<Uuid?>(null) }
    var chatText by remember { mutableStateOf("") }
    var userLine by remember { mutableStateOf("") }
    var coachReply by remember { mutableStateOf(buildEncourageLine(task, assistant)) }
    var waitingReply by remember { mutableStateOf(false) }

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
        if (voiceEnabled) tts.speak(coachReply, flushCalled = true)
        while (remainingSeconds > 0) {
            delay(1_000)
            remainingSeconds -= 1
        }
        if (!finished) {
            finished = true
            vm.completePomodoro(safeMinutes)
            val line = "这一轮完成了。你真的坐住了，奖励我已经替你收好啦。"
            coachReply = line
            if (voiceEnabled) tts.speak(line, flushCalled = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(focusBrush()),
    ) {
        if (imageEnabled) {
            CompanionBackdrop(assistant = assistant)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.12f), Color.Transparent, Color.Black.copy(alpha = 0.28f))
                    )
                )
                .padding(horizontal = 18.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(42.dp))
            Text(
                text = secondsText(remainingSeconds),
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = task.ifBlank { "专注这一轮" },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.86f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            FocusChatPanel(
                assistant = assistant,
                userLine = userLine,
                reply = coachReply,
                waitingReply = waitingReply,
                chatText = chatText,
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
                                ) ?: buildEncourageLine(task, assistant)
                            }
                            coachReply = line
                            waitingReply = false
                            if (voiceEnabled) tts.speak(line, flushCalled = true)
                        }
                    }
                },
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun StudyHero(
    state: StudyState,
    assistant: Assistant,
    onSignIn: () -> Unit,
    onPomodoro: () -> Unit,
    onOpenLevel: () -> Unit,
) {
    val daysLeft = remember { ChronoUnit.DAYS.between(LocalDate.now(), nextExamDate()).coerceAtLeast(0) }
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
                    UIAvatar(assistant.name, assistant.avatar, Modifier.size(58.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${assistant.name}陪你备考", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("今天也不是一个人硬撑。把清单交给我，我们一点点赢。", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    HeroMetric("倒计时", "${daysLeft}天", Modifier.weight(1f))
                    HeroMetric("夸夸值", state.wallet.kudos.toString(), Modifier.weight(1f))
                    HeroMetric("Lv", StudyRules.currentLevel(state).level.toString(), Modifier.weight(1f), onOpenLevel)
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
private fun SectionChips(selected: StudySection, onSelected: (StudySection) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StudySection.entries.forEach { section ->
            FilterChip(selected = selected == section, onClick = { onSelected(section) }, label = { Text(section.label) })
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
    StudyCard {
        Text("今日进度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text("$done / $total 个待办完成", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.superMomentAvailable) {
            Text("超神时刻已点亮", style = MaterialTheme.typography.titleSmall, color = StudyColors.goldText)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onClaimNormal, modifier = Modifier.weight(1f)) { Text("普通 x5") }
                OutlinedButton(onClick = onClaimRare, modifier = Modifier.weight(1f)) { Text("稀有 x1") }
            }
        } else {
            Text("全清今日待办后，解锁十连券、200夸夸值和自选碎片。")
        }
    }
}

@Composable
private fun DailyIncomeCard() {
    StudyCard {
        Text("全清收益参考", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("按 4 个待办 + 5 个番茄钟估算：", color = MaterialTheme.colorScheme.onSurfaceVariant)
        IncomeLine("待办", "4 x 100 = 400")
        IncomeLine("番茄钟", "5 x 50 = 250")
        IncomeLine("连续签到第 5 天起", "75")
        IncomeLine("盲盒期望", "约 106")
        IncomeLine("超神固定奖励", "200 + 十连券 x1")
        Text("日合计约 1031 夸夸值，再加 1 张十连券。", fontWeight = FontWeight.SemiBold, color = StudyColors.goldText)
    }
}

@Composable
private fun InactivityStatusCard(state: StudyState) {
    val text = when {
        state.inactiveStudyDays <= 0 -> "今天节奏安全。完成任意待办或番茄钟都会继续保持。"
        state.inactiveStudyDays == 1 -> "已经 1 天没有记录学习行为，明天再断就会开始扣夸夸值。"
        state.inactiveStudyDays == 2 -> "连续 2 天未学习，今日已进入 -50 规则。完成学习后计数会清零。"
        else -> "连续 ${state.inactiveStudyDays} 天未学习，当前处于每日 -100 规则。"
    }
    StudyCard {
        Text("学习连续性", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun IncomeLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TaskCard(
    tasks: List<StudyTask>,
    newTask: String,
    onNewTask: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    StudyCard {
        Text("今日待办", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
            Text("先写下今天最重要的 3-5 件事。露露会帮你守住节奏。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        tasks.forEach { task ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = task.done, onCheckedChange = { onToggle(task.id, it) })
                Text(
                    text = task.title,
                    modifier = Modifier.weight(1f),
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { onDelete(task.id) }) {
                    Icon(HugeIcons.Delete01, "删除")
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
                    "${assistant.name}看见你把今天全部拿下了。十连券、200 夸夸值，还有自选碎片都亮起来了。",
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
                    Text("选择普通碎片 x5")
                }
                OutlinedButton(onClick = onClaimRare, modifier = Modifier.fillMaxWidth()) {
                    Text("选择稀有碎片 x1")
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
    results: List<StudyDrawResult>,
    onDismissRequest: () -> Unit,
) {
    val best = results.maxByOrNull { it.rarity.weight }?.rarity ?: StudyRarity.Normal
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { TextButton(onClick = onDismissRequest) { Text("放进背包") } },
        title = { Text(drawResultTitle(best, results.size)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(drawBrush(best), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(drawResultSubtitle(best), color = Color.White.copy(alpha = 0.92f), fontWeight = FontWeight.SemiBold)
                results.forEach { result ->
                    Surface(
                        color = Color.White.copy(alpha = if (result.rarity == best) 0.88f else 0.66f),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(shape = CircleShape, color = rarityColor(result.rarity), modifier = Modifier.size(12.dp)) {}
                            Text("${result.rarity.label} · ${result.title}", modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun MysteryBoxCelebration(kudos: Int, onDismissRequest: () -> Unit) {
    val rarity = when {
        kudos >= 100 -> StudyRarity.Epic
        kudos >= 50 -> StudyRarity.Rare
        else -> StudyRarity.Normal
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
                            Text("+$kudos", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = rarityColor(rarity))
                        }
                    }
                    Text(mysteryBoxText(kudos), color = Color.White, fontWeight = FontWeight.SemiBold)
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
private fun McDonaldsCelebration(onDismissRequest: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "mcdonalds")
    val pulse by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(760), RepeatMode.Reverse),
        label = "pulse",
    )
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { Button(onClick = onDismissRequest) { Text("好耶，去确认") } },
        title = { Text("麦门奖励仪式启动") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(drawBrush(StudyRarity.Epic), RoundedCornerShape(18.dp))
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.82f), modifier = Modifier.size((92 * pulse).dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("M", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = StudyColors.goldText)
                    }
                }
                Text("角色帮你安排一顿小奖励啦。", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(
                    "这里先扣除 2 个麦当劳碎片；真实点餐仍需要你自己确认、自己支付。",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
    )
}

@Composable
private fun GachaCard(state: StudyState, onSingle: () -> Unit, onTen: () -> Unit) {
    val singleCost = if (StudyRules.hasSingleDrawDiscount(state)) StudyRules.DISCOUNT_SINGLE_DRAW_COST else StudyRules.SINGLE_DRAW_COST
    StudyCard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gachaBrush(), RoundedCornerShape(18.dp))
                .padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(HugeIcons.AiMagic, null, tint = Color.White)
                    Column {
                        Text("奖励抽卡", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("普通套装 85% · 小剧场 12% · 麦当劳 3%", color = Color.White.copy(alpha = 0.84f))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DrawPoolChip("套装", "85%", StudyColors.blue)
                    DrawPoolChip("剧场", "12%", StudyColors.purple)
                    DrawPoolChip("麦当劳", "3%", StudyColors.goldText)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onSingle, modifier = Modifier.weight(1f)) {
                Text("单抽 $singleCost / 券${state.wallet.singleDrawTickets}")
            }
            Button(onClick = onTen, modifier = Modifier.weight(1f)) {
                Text("十连 800 / 券${state.wallet.tenDrawTickets}")
            }
        }
        Text("麦当劳碎片是角色奖励仪式：集齐后由角色帮你安排，最后仍由你确认和支付。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CollectionCard(
    inventory: StudyInventory,
    onUseUniversalNormal: () -> Unit,
    onUseUniversalNormalTarget: (String) -> Unit,
    onUseUniversalRare: () -> Unit,
    onUseUniversalRareTarget: (String) -> Unit,
    onUseUniversalEpic: () -> Unit,
    onRedeemMcDonalds: () -> Unit,
    onOpenImageGen: () -> Unit,
) {
    StudyCard {
        Text("收藏背包", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            InventoryMetric("普通碎片", inventory.normalFragments.values.sum().toString(), Modifier.weight(1f))
            InventoryMetric("剧场碎片", inventory.rareFragments.values.sum().toString(), Modifier.weight(1f))
            InventoryMetric("麦当劳", "${inventory.epicFragments}/2", Modifier.weight(1f))
        }
        Text("通用普通 ${inventory.universalNormalFragments} · 通用稀有 ${inventory.universalRareFragments} · 通用史诗 ${inventory.universalEpicFragments}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onUseUniversalNormal,
                enabled = inventory.universalNormalFragments > 0,
                modifier = Modifier.weight(1f),
            ) { Text("补套装") }
            OutlinedButton(
                onClick = onUseUniversalRare,
                enabled = inventory.universalRareFragments > 0,
                modifier = Modifier.weight(1f),
            ) { Text("补剧场") }
            OutlinedButton(
                onClick = onUseUniversalEpic,
                enabled = inventory.universalEpicFragments > 0,
                modifier = Modifier.weight(1f),
            ) { Text("补麦当劳") }
        }
        Button(onClick = onRedeemMcDonalds, enabled = inventory.epicFragments >= 2, modifier = Modifier.fillMaxWidth()) {
            Icon(HugeIcons.InLove, null)
            Spacer(Modifier.width(8.dp))
            Text("兑换一次麦当劳点餐仪式")
        }
        Text("已解锁套装 ${inventory.unlockedOutfits.size}/10 · 已解锁剧场 ${inventory.unlockedTheaters.size}/12")
        CollectionProgressList(
            inventory = inventory,
            onUseUniversalNormalTarget = onUseUniversalNormalTarget,
            onUseUniversalRareTarget = onUseUniversalRareTarget,
            onOpenImageGen = onOpenImageGen,
        )
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
    onUseUniversalNormalTarget: (String) -> Unit,
    onUseUniversalRareTarget: (String) -> Unit,
    onOpenImageGen: () -> Unit,
) {
    Text("套装进度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    StudyRules.outfitNames.forEach { outfit ->
        val fragmentCount = StudyRules.outfitParts.sumOf { part ->
            (inventory.normalFragments["normal:$outfit:$part"] ?: 0).coerceAtMost(4)
        }
        val completedParts = StudyRules.outfitParts.count { part ->
            (inventory.normalFragments["normal:$outfit:$part"] ?: 0) >= 4
        }
        OutfitProgressCard(
            outfit = outfit,
            fragmentCount = fragmentCount,
            completedParts = completedParts,
            inventory = inventory,
            onUseUniversalNormalTarget = onUseUniversalNormalTarget,
            onOpenImageGen = onOpenImageGen,
        )
    }

    Text("小剧场进度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    StudyRules.theaterNames.forEach { theater ->
        val count = (inventory.rareFragments["rare:$theater"] ?: 0).coerceAtMost(5)
        CollectionProgressRow(
            title = theater,
            detail = if (theater in inventory.unlockedTheaters) "已解锁" else "$count/5 碎片",
            progress = count / 5f,
            unlocked = theater in inventory.unlockedTheaters,
            action = if (count < 5 && inventory.universalRareFragments > 0) {
                { TextButton(onClick = { onUseUniversalRareTarget("rare:$theater") }) { Text("用通用") } }
            } else {
                null
            },
        )
    }

}

@Composable
private fun OutfitProgressCard(
    outfit: String,
    fragmentCount: Int,
    completedParts: Int,
    inventory: StudyInventory,
    onUseUniversalNormalTarget: (String) -> Unit,
    onOpenImageGen: () -> Unit,
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
                        if (unlocked) "完整套装已解锁" else "$completedParts/6 部件 · $fragmentCount/24 碎片",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(if (unlocked) "已解锁" else "${(fragmentCount * 100 / 24).coerceIn(0, 100)}%", color = StudyColors.goldText)
            }
            LinearProgressIndicator(progress = { fragmentCount / 24f }, modifier = Modifier.fillMaxWidth())
            if (unlocked) {
                FilledTonalButton(onClick = onOpenImageGen, modifier = Modifier.fillMaxWidth()) {
                    Icon(HugeIcons.AiMagic, null)
                    Spacer(Modifier.width(8.dp))
                    Text("用这套造型去生图")
                }
            }
            StudyRules.outfitParts.forEach { part ->
                val key = "normal:$outfit:$part"
                val count = (inventory.normalFragments[key] ?: 0).coerceAtMost(4)
                CollectionProgressRow(
                    title = part,
                    detail = "$count/4",
                    progress = count / 4f,
                    unlocked = count >= 4,
                    action = if (count < 4 && inventory.universalNormalFragments > 0) {
                        { TextButton(onClick = { onUseUniversalNormalTarget(key) }) { Text("用通用") } }
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
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            }
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun InventoryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = StudyColors.softBlue, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AchievementCard(state: StudyState, onClaim: (String) -> Unit) {
    val claimable = StudyRules.claimableAchievements(state).map { it.id }.toSet()
    StudyCard {
        Text("成就墙", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        StudyRules.achievements.forEach { achievement ->
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
                "签到：25 夸夸值；连续第 3 天为 50，第 5 天起为 75。",
                "完成 1 个番茄钟：50 夸夸值 + 1 个盲盒。",
                "完成 1 项待办：100 夸夸值。",
                "今日待办全清：触发超神时刻，固定给十连券 x1 + 200 夸夸值。",
            ),
        )
        GuideBlock(
            title = "盲盒概率",
            lines = listOf(
                "15 夸夸值：40% · 柔光蓝",
                "25 夸夸值：30% · 流光蓝",
                "50 夸夸值：15% · 幽雅紫",
                "100 夸夸值：4% · 暖金",
                "200 夸夸值：1% · 璨金",
            ),
        )
        GuideBlock(
            title = "抽卡与收藏",
            lines = listOf(
                "单抽 ${StudyRules.SINGLE_DRAW_COST} 夸夸值，Lv15 后单抽 ${StudyRules.DISCOUNT_SINGLE_DRAW_COST}。",
                "十连 ${StudyRules.TEN_DRAW_COST} 夸夸值。",
                "普通套装 85%，小剧场 12%，麦当劳碎片 3%。",
                "每套普通套装有 6 个部件，每个部件 4 个同名碎片。",
                "每部小剧场需要 5 个同名碎片。",
            ),
        )
        GuideBlock(
            title = "每周 5 天全清模拟",
            lines = listOf(
                "约 5155 夸夸值，可折算 51 次单抽。",
                "超神 5 天给 5 张十连券；等级和成就还会追加抽卡券。",
                "按 114 抽估算：普通碎片约 97，稀有约 14，史诗约 3。",
                "如果超神都选普通碎片，还会额外获得 25 个通用普通碎片。",
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
                "学习前可选择陪伴画面和语音鼓励。",
                "番茄钟里可以和角色轻声聊天。",
                "番茄钟完成后会自动发放夸夸值和盲盒奖励。",
                "麦当劳兑换是角色奖励仪式：由角色帮你安排，最后仍由你确认和支付。",
            ),
        )
        GuideBlock(
            title = "当前已落地",
            lines = listOf(
                "签到、待办、番茄钟、盲盒、惩罚、抽卡、超神、等级、成就、商店都已接入本地状态。",
                "收藏已按 10 套套装、每套 6 部件、每部件 4 碎片展示。",
                "通用碎片可以自动补最佳目标，也可以在收藏里指定补某个部件或剧场。",
                "Lv14 会自动补齐一套未完成套装；已解锁套装可以直接跳到生图页。",
                "番茄钟已接入角色陪伴、语音鼓励和轻聊天。",
                "更深的角色主动督学、套装提示词自动带入、真实麦当劳点餐接口可以作为后续增强。",
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
    imageEnabled: Boolean,
    voiceEnabled: Boolean,
    onImageToggle: (Boolean) -> Unit,
    onVoiceToggle: (Boolean) -> Unit,
) {
    StudyCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            UIAvatar(assistant.name, assistant.avatar, Modifier.size(54.dp))
            Column(Modifier.weight(1f)) {
                Text("${assistant.name}坐到你旁边了", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("学习时她会安静陪着，累了可以说一句。")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = imageEnabled, onClick = { onImageToggle(!imageEnabled) }, label = { Text("陪伴画面") })
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
    }
}

@Composable
private fun CompanionBackdrop(assistant: Assistant) {
    Box(modifier = Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.30f)),
                contentAlignment = Alignment.Center,
            ) {
                UIAvatar(assistant.name, assistant.avatar, Modifier.size(132.dp))
            }
            Text(
                text = "她在桌边陪你把这一轮守完。",
                color = Color.White.copy(alpha = 0.88f),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color.Black.copy(alpha = 0.18f))
                    .padding(12.dp),
            )
        }
    }
}

@Composable
private fun FocusChatPanel(
    assistant: Assistant,
    userLine: String,
    reply: String,
    waitingReply: Boolean,
    chatText: String,
    onChatChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                UIAvatar(assistant.name, assistant.avatar, Modifier.size(32.dp))
                Text(assistant.name, style = MaterialTheme.typography.titleSmall)
            }
            if (userLine.isNotBlank()) Text("我：$userLine", style = MaterialTheme.typography.bodySmall)
            Text(if (waitingReply) "正在轻声回复..." else reply)
            OutlinedTextField(
                value = chatText,
                onValueChange = onChatChange,
                label = { Text("学累了可以跟她说一句") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            FilledTonalButton(onClick = onSend, modifier = Modifier.fillMaxWidth()) {
                Icon(HugeIcons.VolumeHigh, null)
                Spacer(Modifier.width(8.dp))
                Text("发送并播报")
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

private object StudyColors {
    val page = Color(0xFFF7F3EA)
    val hero = Color(0xFFFFE6B8)
    val softBlue = Color(0xFFDCECF4)
    val blue = Color(0xFF3D7EA6)
    val purple = Color(0xFF8067B7)
    val goldText = Color(0xFF9B6B10)
}

private fun heroBrush(): Brush = Brush.linearGradient(
    listOf(Color(0xFFFFE5AE), Color(0xFFE2F0F7), Color(0xFFFFF8D8))
)

private fun focusBrush(): Brush = Brush.verticalGradient(
    listOf(Color(0xFF6F8FA6), Color(0xFFE1CDA6), Color(0xFFB88B8F))
)

private fun rarityColor(rarity: StudyRarity): Color = when (rarity) {
    StudyRarity.Normal -> StudyColors.blue
    StudyRarity.Rare -> StudyColors.purple
    StudyRarity.Epic -> StudyColors.goldText
}

private val StudyRarity.weight: Int
    get() = when (this) {
        StudyRarity.Normal -> 1
        StudyRarity.Rare -> 2
        StudyRarity.Epic -> 3
    }

private fun drawResultTitle(best: StudyRarity, count: Int): String {
    return when (best) {
        StudyRarity.Epic -> "金光炸开了"
        StudyRarity.Rare -> if (count >= 10) "十连有好东西" else "稀有碎片出现"
        StudyRarity.Normal -> if (count >= 10) "十连结果" else "抽卡结果"
    }
}

private fun drawResultSubtitle(best: StudyRarity): String {
    return when (best) {
        StudyRarity.Epic -> "麦当劳碎片到手，角色奖励仪式又近一步。"
        StudyRarity.Rare -> "小剧场碎片亮起来了，剧情正在靠近。"
        StudyRarity.Normal -> "套装碎片收进背包，离完整造型更近一点。"
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
}

private fun mysteryBoxText(kudos: Int): String = when (kudos) {
    15 -> "柔光蓝，星点飘浮。获得 15 夸夸值。"
    25 -> "流光蓝，光带环绕。获得 25 夸夸值。"
    50 -> "幽雅紫，花瓣光晕。获得 50 夸夸值。"
    100 -> "暖金亮起来了。获得 100 夸夸值。"
    else -> "璨金粒子炸开。获得 200 夸夸值。"
}

private fun nextExamDate(today: LocalDate = LocalDate.now()): LocalDate {
    val current = LocalDate.of(today.year, 12, 21)
    return if (today <= current) current else LocalDate.of(today.year + 1, 12, 21)
}

private fun secondsText(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "%02d:%02d".format(safe / 60, safe % 60)
}

private fun buildEncourageLine(taskText: String, assistant: Assistant): String {
    val target = taskText.ifBlank { "这一轮任务" }
    return "${assistant.name}小声说：先不想那么远，我们只把“$target”往前推一点点。"
}

private fun buildStudyChatPrompt(userText: String, taskText: String): String {
    val target = taskText.ifBlank { "这一轮学习任务" }
    return "我正在番茄钟学习，任务是“$target”。我想对你说：$userText\n请按你的角色人设自然回复，短一点，像真的在旁边陪我学习。只输出你要说出口的话。"
}
