package me.rerere.rikkahub.ui.pages.game

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.asr.ASRStatus
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft02
import me.rerere.hugeicons.stroke.MagicWand01
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Voice
import me.rerere.hugeicons.stroke.VolumeHigh
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.ApiUsageSource
import me.rerere.rikkahub.data.ai.ApiUsageStore
import me.rerere.rikkahub.data.ai.transformers.transformMessages
import me.rerere.rikkahub.data.companion.CompanionLifeEvent
import me.rerere.rikkahub.data.companion.CompanionLifeEventSource
import me.rerere.rikkahub.data.companion.CompanionLifeEventStatus
import me.rerere.rikkahub.data.companion.CompanionLifeEventType
import me.rerere.rikkahub.data.companion.CompanionPerceptionInput
import me.rerere.rikkahub.data.companion.CompanionRuntime
import me.rerere.rikkahub.data.companion.CompanionStore
import me.rerere.rikkahub.data.companion.CompanionTurnMutation
import me.rerere.rikkahub.data.companion.toPromptContext
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.math.abs
import kotlin.random.Random
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameHubPage() {
    val navController = LocalNavController.current
    val settings = LocalSettings.current
    val companionStore = koinInject<CompanionStore>()
    val companionState by companionStore.state.collectAsState()
    val latestSignalEvent = companionState.snapshots
        .asSequence()
        .flatMap { snapshot -> snapshot.lifeEvents.asSequence() }
        .filter { event ->
            event.type == CompanionLifeEventType.GAME &&
                event.status == CompanionLifeEventStatus.COMPLETED &&
                event.detailsJson.contains("\"game\":\"signal_hunt\"")
        }
        .maxByOrNull { it.endedAt ?: it.startedAt }
    val latestSignalAssistant = latestSignalEvent?.let { event ->
        settings.assistants.firstOrNull { it.id.toString() == event.assistantId }
    }
    val games = remember {
        listOf(
            GameTile(
                title = "信号追踪",
                subtitle = "选择一个角色，一起在 3×3 信号网格里找线索",
                enabled = true,
                onClick = { navController.navigate(Screen.SignalHuntGame()) },
            ),
            GameTile(
                title = "满分男",
                subtitle = "轮流描述和猜分，角色会按自己的人设参与",
                enabled = true,
                onClick = { navController.navigate(Screen.PerfectManGame) },
            ),
            GameTile(
                title = "一起猜拳",
                subtitle = "你和角色同时出手，对局会成为共同经历",
                enabled = true,
                onClick = { navController.navigate(Screen.QuickCompanionGame("rock_paper_scissors")) },
            ),
            GameTile(
                title = "骰子对决",
                subtitle = "双方各掷一次骰子，看看这一轮谁的点数更高",
                enabled = true,
                onClick = { navController.navigate(Screen.QuickCompanionGame("dice_duel")) },
            ),
            GameTile(
                title = "井字棋",
                subtitle = "你执 X、角色执 O，角色会取胜也会主动拦截",
                enabled = true,
                onClick = { navController.navigate(Screen.QuickCompanionGame("tic_tac_toe")) },
            ),
        ) + List(3) { index ->
            GameTile(
                title = "待解锁游戏 ${index + 1}",
                subtitle = "后续继续扩展",
                enabled = false,
                onClick = {},
            )
        }
    }
    Scaffold(
        containerColor = GameColors.background,
        topBar = {
            TopAppBar(
                title = { Text("游戏") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(HugeIcons.ArrowLeft02, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GameHero()
            if (latestSignalEvent != null) {
                SignalHuntRecordCard(
                    assistantName = latestSignalAssistant?.name?.ifBlank { "某个角色" } ?: "某个角色",
                    event = latestSignalEvent,
                    onClick = { navController.navigate(Screen.SignalHuntGame(latestSignalEvent.id)) },
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth(),
                userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 18.dp),
            ) {
                items(games, key = { it.title }) { game ->
                    GameTileCard(game)
                }
            }
        }
    }
}

@Composable
private fun SignalHuntRecordCard(
    assistantName: String,
    event: CompanionLifeEvent,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        color = GameColors.accent.copy(alpha = 0.10f),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${assistantName}刚刚玩过信号追踪", fontWeight = FontWeight.SemiBold, color = GameColors.accent)
            Text(event.summary.ifBlank { "点这里观看这一局的完整探测路线。" }, style = MaterialTheme.typography.bodySmall)
            Text("点击观看记录 →", style = MaterialTheme.typography.labelLarge, color = GameColors.accent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalHuntGamePage(recordId: String? = null) {
    val navController = LocalNavController.current
    val settings = LocalSettings.current
    val companionStore = koinInject<CompanionStore>()
    val companionState by companionStore.state.collectAsState()
    val replayEvent = recordId?.let { id ->
        companionState.snapshots.asSequence()
            .flatMap { snapshot -> snapshot.lifeEvents.asSequence() }
            .firstOrNull { event -> event.id == id }
    }
    val replay = remember(replayEvent?.id, replayEvent?.detailsJson) {
        replayEvent?.let { event ->
            parseSignalHuntReplay(
                event = event,
                assistantName = settings.assistants.firstOrNull { it.id.toString() == event.assistantId }
                    ?.name
                    ?.ifBlank { "某个角色" }
                    ?: "某个角色",
            )
        }
    }
    val isWatching = replay != null
    var selectedAssistantId by remember { mutableStateOf(settings.assistantId.toString()) }
    val selectedAssistant = settings.assistants.firstOrNull { it.id.toString() == selectedAssistantId }
        ?: settings.getCurrentAssistant()
    var signalCells by remember { mutableStateOf(emptySet<Int>()) }
    var moves by remember { mutableStateOf(emptyList<SignalHuntMove>()) }
    var started by remember { mutableStateOf(false) }
    var replayStep by remember(replay?.sessionId) { mutableIntStateOf(replay?.moves?.size ?: 0) }
    val visibleReplayMoves = replay?.moves?.take(replayStep).orEmpty()
    val activeMoves = if (isWatching) visibleReplayMoves else moves
    val gameOver = !isWatching && moves.size >= SIGNAL_HUNT_MAX_MOVES

    fun startGame() {
        signalCells = (0..8).shuffled().take(SIGNAL_HUNT_SIGNAL_COUNT).toSet()
        moves = emptyList()
        started = true
    }

    LaunchedEffect(replay?.sessionId) {
        if (isWatching && replay != null) {
            replayStep = 0
            while (replayStep < replay.moves.size) {
                delay(700)
                replayStep += 1
            }
        }
    }

    Scaffold(
        containerColor = GameColors.background,
        topBar = {
            TopAppBar(
                title = { Text(if (isWatching) "观看信号追踪" else "一起玩：信号追踪") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(HugeIcons.ArrowLeft02, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (isWatching && replay != null) {
                SignalHuntReplaySummary(replay = replay, visibleMoves = visibleReplayMoves)
            } else {
                Text("选择陪你玩的角色", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(settings.assistants, key = { it.id }) { assistant ->
                        FilterChip(
                            selected = assistant.id.toString() == selectedAssistantId,
                            onClick = { selectedAssistantId = assistant.id.toString() },
                            label = { Text(assistant.name.ifBlank { "未命名角色" }) },
                        )
                    }
                }
                Text(
                    "本局由 ${selectedAssistant.name.ifBlank { "当前角色" }} 陪你一起找信号。每局最多探测 5 格，找到 3 个信号就完成。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SignalHuntBoard(
                moves = activeMoves,
                signalCells = if (isWatching && replayStep >= (replay?.moves?.size ?: 0)) {
                    replay?.moves.orEmpty().filter { it.foundSignal }.map { it.cell }.toSet()
                } else emptySet(),
                enabled = !isWatching && started && !gameOver,
                onCellClick = { cell ->
                    if (cell !in moves.map { it.cell }) {
                        val found = cell in signalCells
                        val streak = if (found && moves.lastOrNull()?.foundSignal == true) 2 else 1
                        moves = moves + SignalHuntMove(cell, found, if (found) 20 + (streak - 1) * 5 else 0)
                    }
                },
            )
            if (isWatching && replay != null) {
                Text("${replay.assistantName}的路线已播放完毕。你也可以返回上一页，选择任意角色开启新的一局。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val found = moves.count { it.foundSignal }
                Text("本局进度：找到 $found/$SIGNAL_HUNT_SIGNAL_COUNT 个信号 · 已探测 ${moves.size}/$SIGNAL_HUNT_MAX_MOVES 格")
                Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(if (moves.isEmpty()) HugeIcons.Play else HugeIcons.Refresh03, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (moves.isEmpty()) "开始这一局" else if (gameOver) "再来一局" else "重置本局")
                }
                if (gameOver) {
                    Text(
                        "这一局结束啦。${selectedAssistant.name.ifBlank { "角色" }}会陪你记住这条路线。",
                        color = GameColors.success,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SignalHuntReplaySummary(replay: SignalHuntReplay, visibleMoves: List<SignalHuntMove>) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("${replay.assistantName}的信号追踪记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("得分 ${replay.score}/${replay.maxScore} · 找到 ${visibleMoves.count { it.foundSignal }}/${SIGNAL_HUNT_SIGNAL_COUNT} 个信号")
            Text(if (visibleMoves.size < replay.moves.size) "正在按角色当时的顺序播放探测路线…" else replay.resultText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SignalHuntBoard(
    moves: List<SignalHuntMove>,
    signalCells: Set<Int>,
    enabled: Boolean,
    onCellClick: (Int) -> Unit,
) {
    val moveByCell = moves.associateBy { it.cell }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { column ->
                    val cell = row * 3 + column
                    val move = moveByCell[cell]
                    val revealedSignal = cell in signalCells || move?.foundSignal == true
                    Surface(
                        modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(16.dp)).then(
                            if (enabled && move == null) Modifier.clickable { onCellClick(cell) } else Modifier,
                        ),
                        color = when {
                            move?.foundSignal == true || cell in signalCells -> Color(0xFFD9F1E5)
                            move != null -> Color(0xFFE8EAF0)
                            else -> Color.White
                        },
                        tonalElevation = 2.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                when {
                                    revealedSignal -> "✦\n信号"
                                    move != null -> "已探测"
                                    enabled -> "?"
                                    else -> "·"
                                },
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontWeight = FontWeight.SemiBold,
                                color = if (revealedSignal) GameColors.success else GameColors.soft,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class SignalHuntMove(val cell: Int, val foundSignal: Boolean, val points: Int)

private data class SignalHuntReplay(
    val sessionId: String,
    val assistantName: String,
    val score: Int,
    val maxScore: Int,
    val resultText: String,
    val moves: List<SignalHuntMove>,
)

private fun parseSignalHuntReplay(event: CompanionLifeEvent, assistantName: String): SignalHuntReplay? = runCatching {
    val json = JsonInstant.parseToJsonElement(event.detailsJson).jsonObject
    val moves = json["moves"]?.jsonArray.orEmpty().mapNotNull { item ->
        val obj = item.jsonObject
        val cell = obj["cell"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
        SignalHuntMove(
            cell = cell,
            foundSignal = obj["found_signal"]?.jsonPrimitive?.booleanOrNull == true,
            points = obj["points"]?.jsonPrimitive?.intOrNull ?: 0,
        )
    }
    if (moves.isEmpty()) return@runCatching null
    SignalHuntReplay(
        sessionId = json["session_id"]?.jsonPrimitive?.contentOrNull ?: event.id,
        assistantName = assistantName,
        score = json["score"]?.jsonPrimitive?.intOrNull ?: 0,
        maxScore = json["max_score"]?.jsonPrimitive?.intOrNull ?: 75,
        resultText = json["result"]?.jsonPrimitive?.contentOrNull ?: event.summary,
        moves = moves,
    )
}.getOrNull()

private const val SIGNAL_HUNT_SIGNAL_COUNT = 3
private const val SIGNAL_HUNT_MAX_MOVES = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfectManGamePage() {
    val navController = LocalNavController.current
    val asr = LocalASRState.current
    val tts = LocalTTSState.current
    val settings = LocalSettings.current
    val settingsStore = koinInject<SettingsStore>()
    val providerManager = koinInject<ProviderManager>()
    val apiUsageStore = koinInject<ApiUsageStore>()
    val companionRuntime = koinInject<CompanionRuntime>()
    val scope = rememberCoroutineScope()
    val asrState by asr.state.collectAsState()
    var round by remember { mutableIntStateOf(1) }
    var targetScore by remember { mutableIntStateOf(Random.nextInt(0, 11)) }
    var phase by remember { mutableStateOf(PerfectManPhase.UserGuesses) }
    var generatedPrompt by remember { mutableStateOf("") }
    var userDescription by remember { mutableStateOf("") }
    var guessText by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<RoundResult?>(null) }
    var opponentLine by remember { mutableStateOf(GAME_WAITING_MARKER) }
    var isGenerating by remember { mutableStateOf(false) }
    var opponentVoiceEnabled by remember { mutableStateOf(true) }
    var listeningTarget by remember { mutableStateOf<VoiceInputTarget?>(null) }
    var selectedPlayerAssistantId by remember { mutableStateOf<String?>(settings.assistantId.toString()) }
    val selectedPlayer = settings.assistants.firstOrNull { it.id.toString() == selectedPlayerAssistantId }

    val isListening = asrState.status != ASRStatus.Idle && asrState.status != ASRStatus.Error

    fun speak(text: String) {
        if (
            opponentVoiceEnabled &&
            text.isNotBlank() &&
            !text.startsWith("（")
        ) {
            tts.speak(text)
        }
    }

    fun stopVoiceInput() {
        asr.stop()
        listeningTarget = null
    }

    fun startVoiceInput(target: VoiceInputTarget) {
        if (isListening) {
            stopVoiceInput()
            return
        }
        listeningTarget = target
        asr.start { transcript ->
            val clean = transcript.trim()
            if (clean.isBlank()) return@start
            when (target) {
                VoiceInputTarget.Flaw -> userDescription = clean
                VoiceInputTarget.Guess -> guessText = clean.filter { it.isDigit() || it == '.' }
            }
        }
    }

    fun nextRound() {
        tts.stop()
        stopVoiceInput()
        val nextPhase = if (phase == PerfectManPhase.UserGuesses) {
            PerfectManPhase.PartnerGuesses
        } else {
            PerfectManPhase.UserGuesses
        }
        round += 1
        targetScore = Random.nextInt(0, 11)
        phase = nextPhase
        generatedPrompt = ""
        userDescription = ""
        guessText = ""
        result = null
        opponentLine = GAME_WAITING_MARKER
    }

    suspend fun generatePerfectManText(prompt: String, fallback: String): String {
        return runCatching {
            val settings = settingsStore.settingsFlow.first()
            val player = selectedPlayerAssistantId
                ?.let { id -> settings.assistants.firstOrNull { it.id.toString() == id } }
                ?: return@runCatching fallback
            val model = settings.findModelById(player.chatModelId ?: settings.chatModelId)
                ?.takeIf { it.type == ModelType.CHAT }
                ?: return@runCatching fallback
            val providerSetting = model.findProvider(settings.providers) ?: return@runCatching fallback
            val provider = providerManager.getProviderByType(providerSetting)
            val playerPrompt = player?.let { assistant ->
                buildString {
                    appendLine("你现在扮演坐在用户对面一起玩游戏的“${assistant.name.ifBlank { "玩家" }}”。")
                    appendLine("该角色的核心人设、关系类型、世界观、语言习惯与边界是最高约束。游戏场景只能提供事实和轮次目标，不能把角色改写成默认友好、爱吐槽、活泼或亲密的玩家。只输出这个角色当面真正会说出口的话。")
                    if (assistant.systemPrompt.isNotBlank()) {
                        appendLine("角色人设：")
                        appendLine(assistant.systemPrompt)
                    }
                    if (assistant.appearancePrompt.isNotBlank()) {
                        appendLine("角色外貌：")
                        appendLine(assistant.appearancePrompt)
                    }
                }.trim()
            }.orEmpty()
            val companionContext = player?.let { assistant ->
                companionRuntime.perception(
                    CompanionPerceptionInput(
                        assistantId = assistant.id.toString(),
                        assistantName = assistant.name,
                        persona = assistant.systemPrompt,
                        nowMillis = System.currentTimeMillis(),
                    ),
                ).toPromptContext()
            }.orEmpty()
            val messages = buildList {
                add(UIMessage.system(
                    "你正在参与“满分男”游戏，不是主持人、裁判或旁白。" +
                        "所选角色的人设与关系边界拥有最高优先级；不得默认友善、吐槽、犹豫、活泼或亲密。" +
                        "只输出该角色会当面说出口的话，不播报后台规则。内容简短、可供猜测，不要色情，不要羞辱真实群体。",
                ))
                if (playerPrompt.isNotBlank()) add(UIMessage.system(playerPrompt))
                if (companionContext.isNotBlank()) add(UIMessage.system(companionContext))
                add(UIMessage.user(prompt))
            }.let { baseMessages ->
                if (player == null) {
                    baseMessages
                } else {
                    transformMessages(
                        messages = baseMessages,
                        assistant = player,
                        modeInjections = settings.modeInjections,
                        lorebooks = settings.lorebooks,
                    )
                }
            }
            val chunk = provider.generateText(
                providerSetting = providerSetting,
                messages = messages,
                params = TextGenerationParams(
                    model = model,
                    temperature = 0.8f,
                    topP = 0.9f,
                    maxTokens = 500,
                    reasoningLevel = ReasoningLevel.OFF,
                ),
            )
            chunk.usage?.let { usage ->
                apiUsageStore.record(
                    source = ApiUsageSource.GAME,
                    title = "满分男：${player.name.ifBlank { "当前角色" }}",
                    model = model.displayName.ifBlank { model.modelId },
                    provider = providerSetting.name.ifBlank { providerSetting.id.toString() },
                    usage = usage,
                )
            }
            chunk.choices.firstOrNull()?.message?.toText()?.trim()?.takeIf { it.isNotBlank() } ?: fallback
        }.getOrElse {
            if (it is CancellationException) throw it
            fallback
        }
    }

    suspend fun recordCompletedRound(
        playerAssistantId: String?,
        completedRound: Int,
        completedPhase: PerfectManPhase,
        roundResult: RoundResult,
    ) {
        val assistantId = playerAssistantId?.takeIf(String::isNotBlank) ?: return
        val nowMillis = System.currentTimeMillis()
        val phaseLabel = if (completedPhase == PerfectManPhase.UserGuesses) "角色出题" else "角色猜分"
        runCatching {
            companionRuntime.applyTurn(
                CompanionTurnMutation(
                    assistantId = assistantId,
                    lifeEvents = listOf(
                        CompanionLifeEvent(
                            id = "perfect-man:$assistantId:$completedRound:$nowMillis",
                            assistantId = assistantId,
                            type = CompanionLifeEventType.GAME,
                            status = CompanionLifeEventStatus.COMPLETED,
                            title = "一起玩完了一轮满分男",
                            summary = "$phaseLabel，第 $completedRound 轮相差 ${roundResult.diff} 分。",
                            source = CompanionLifeEventSource.CHAT,
                            evidenceReference = "perfect-man-round:$completedRound:$nowMillis",
                            importance = 3,
                            startedAt = nowMillis,
                            endedAt = nowMillis,
                            createdAt = nowMillis,
                        ),
                    ),
                    nowMillis = nowMillis,
                ),
            )
        }
    }

    fun startUserGuessRound() {
        if (isGenerating) return
        isGenerating = true
        opponentLine = GAME_GENERATING_MARKER
        scope.launch {
            val generated = generatePerfectManText(
                prompt = "你现在负责出题。隐藏分数是 $targetScore/10，但绝对不能暴露分数。" +
                    "以当前角色自己的方式描述这个人，让用户猜他实际几分；不要求固定开头、吐槽或友好语气。" +
                    "只输出角色台词，2-4 句。",
                fallback = GAME_REPLY_FAILURE_MARKER,
            )
            generatedPrompt = generated.takeUnless { it == GAME_REPLY_FAILURE_MARKER }.orEmpty()
            opponentLine = generated
            speak(generated)
            isGenerating = false
        }
    }

    fun submitPartnerGuessRound() {
        val description = userDescription.trim()
        if (description.isBlank() || isGenerating) return
        val playerAssistantId = selectedPlayer?.id?.toString()
        val completedRound = round
        val completedPhase = phase
        isGenerating = true
        opponentLine = GAME_GENERATING_MARKER
        scope.launch {
            val reply = generatePerfectManText(
                prompt = "你现在坐在用户对面猜分。真实分数是 $targetScore/10，只用来校准你的猜测。" +
                    "用户给你的描述是：$description\n" +
                    "请以当前角色自己的判断方式回应并给出猜分，不得强制吐槽、犹豫、友好或亲密。" +
                    "最后必须明确写出“我猜：X分”，X 是 0-10 的整数。" +
                    "只输出角色台词，不要说后台校准分。",
                fallback = GAME_REPLY_FAILURE_MARKER,
            )
            if (reply == GAME_REPLY_FAILURE_MARKER) {
                generatedPrompt = ""
                opponentLine = reply
                isGenerating = false
                return@launch
            }
            val guess = Regex("""(\d{1,2})\s*分""").find(reply)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?.coerceIn(0, 10)
            if (guess == null) {
                generatedPrompt = ""
                opponentLine = GAME_REPLY_FORMAT_FAILURE_MARKER
                isGenerating = false
                return@launch
            }
            generatedPrompt = reply
            opponentLine = reply
            val nextResult = RoundResult(
                guess = guess,
                score = targetScore,
                success = abs(guess - targetScore) <= 1,
                diff = abs(guess - targetScore),
            )
            result = nextResult
            speak(reply)
            recordCompletedRound(playerAssistantId, completedRound, completedPhase, nextResult)
            isGenerating = false
        }
    }

    fun submitGuess() {
        val guess = guessText.trim().toFloatOrNull()?.toInt()?.coerceIn(0, 10) ?: return
        if (isGenerating || generatedPrompt.isBlank()) return
        val playerAssistantId = selectedPlayer?.id?.toString()
        val completedRound = round
        val completedPhase = phase
        val diff = abs(guess - targetScore)
        isGenerating = true
        opponentLine = GAME_GENERATING_MARKER
        scope.launch {
            val nextResult = RoundResult(guess = guess, score = targetScore, success = diff <= 1, diff = diff)
            val reply = generatePerfectManText(
                prompt = "你刚才给用户描述的是：$generatedPrompt\n" +
                    "用户猜这个男的是 $guess 分，真实分数是 $targetScore/10，差值 $diff 分。" +
                    "以当前角色自己的方式回应用户，并明确说出真实分数和差值。" +
                    "是否肯定、讽刺、克制或直接只由角色人设和当前关系决定，不得默认夸奖或轻松吐槽。" +
                    "只输出角色会说出口的话，不要写标题，不要用系统播报口吻。",
                fallback = GAME_REPLY_FAILURE_MARKER,
            )
            result = nextResult
            opponentLine = reply
            speak(reply)
            recordCompletedRound(playerAssistantId, completedRound, completedPhase, nextResult)
            isGenerating = false
        }
    }

    Scaffold(
        containerColor = GameColors.background,
        topBar = {
            TopAppBar(
                title = { Text("满分男") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(HugeIcons.ArrowLeft02, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = ::nextRound) {
                        Icon(HugeIcons.Refresh03, contentDescription = "下一轮")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RoundHeader(round = round, phase = phase)
            PerfectManPlayerSelector(
                selectedPlayer = selectedPlayer,
                assistants = settings.assistants,
                onSelect = { selectedPlayerAssistantId = it },
            )
            OpponentSeatCard(
                line = opponentLine,
                speakingEnabled = opponentVoiceEnabled,
                onSpeak = { speak(opponentLine) },
            )
            VoiceSettingsCard(
                opponentVoiceEnabled = opponentVoiceEnabled,
                onOpponentVoiceEnabledChange = {
                    opponentVoiceEnabled = it
                    if (!it) tts.stop()
                },
            )
            PerfectManActionCard(
                phase = phase,
                score = targetScore,
                promptReady = generatedPrompt.isNotBlank(),
                result = result,
                description = userDescription,
                onDescriptionChange = { userDescription = it },
                onExample = { userDescription = PerfectManExamples.random() },
                guessText = guessText,
                onGuessTextChange = { guessText = it.filter { char -> char.isDigit() || char == '.' }.take(2) },
                listeningTarget = listeningTarget,
                isListening = isListening,
                onVoiceDescription = { startVoiceInput(VoiceInputTarget.Flaw) },
                onVoiceGuess = { startVoiceInput(VoiceInputTarget.Guess) },
                isGenerating = isGenerating,
                onStartPrompt = ::startUserGuessRound,
                onSubmitDescription = ::submitPartnerGuessRound,
                onSubmitGuess = ::submitGuess,
                onNextRound = ::nextRound,
            )
        }
    }
}

@Composable
private fun GameHero() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(GameColors.heroBrush)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.22f), modifier = Modifier.size(54.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(HugeIcons.Puzzle, contentDescription = null, tint = Color.White)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "游戏馆",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text("和角色一起玩，完成的对局会进入真实共同经历。", color = Color.White.copy(alpha = 0.84f))
            }
        }
    }
}

@Composable
private fun GameTileCard(game: GameTile) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .then(if (game.enabled) Modifier.clickable(onClick = game.onClick) else Modifier),
        color = if (game.enabled) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        tonalElevation = if (game.enabled) 4.dp else 1.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(shape = CircleShape, color = GameColors.accent.copy(alpha = if (game.enabled) 0.18f else 0.08f)) {
                Icon(
                    imageVector = if (game.enabled) HugeIcons.MagicWand01 else HugeIcons.Puzzle,
                    contentDescription = null,
                    tint = if (game.enabled) GameColors.accent else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(10.dp).size(24.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(game.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    game.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RoundHeader(round: Int, phase: PerfectManPhase) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("第 $round 轮", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                if (phase == PerfectManPhase.UserGuesses) "对面描述，我来猜分。" else "我来描述，对面猜分。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PerfectManPlayerSelector(
    selectedPlayer: Assistant?,
    assistants: List<Assistant>,
    onSelect: (String?) -> Unit,
) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("选择玩家", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                Text(
                    selectedPlayer?.name?.takeIf { it.isNotBlank() } ?: "未选择角色",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(assistants, key = { it.id.toString() }) { assistant ->
                    FilterChip(
                        selected = selectedPlayer?.id == assistant.id,
                        onClick = { onSelect(assistant.id.toString()) },
                        label = {
                            Text(
                                assistant.name.ifBlank { "玩家" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OpponentSeatCard(
    line: String,
    speakingEnabled: Boolean,
    onSpeak: () -> Unit,
) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = GameColors.accent.copy(alpha = 0.16f),
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(HugeIcons.MagicWand01, contentDescription = null, tint = GameColors.accent)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("对面玩家", style = MaterialTheme.typography.labelLarge, color = GameColors.accent)
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onSpeak, enabled = speakingEnabled && line.isNotBlank()) {
                Icon(HugeIcons.VolumeHigh, contentDescription = "播放对面玩家的话")
            }
        }
    }
}

@Composable
private fun VoiceSettingsCard(
    opponentVoiceEnabled: Boolean,
    onOpponentVoiceEnabledChange: (Boolean) -> Unit,
) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(HugeIcons.VolumeHigh, contentDescription = null, tint = GameColors.accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("对方语音", fontWeight = FontWeight.SemiBold)
            }
            Switch(checked = opponentVoiceEnabled, onCheckedChange = onOpponentVoiceEnabledChange)
        }
    }
}

@Composable
private fun PerfectManActionCard(
    phase: PerfectManPhase,
    score: Int,
    promptReady: Boolean,
    result: RoundResult?,
    description: String,
    onDescriptionChange: (String) -> Unit,
    onExample: () -> Unit,
    guessText: String,
    onGuessTextChange: (String) -> Unit,
    listeningTarget: VoiceInputTarget?,
    isListening: Boolean,
    onVoiceDescription: () -> Unit,
    onVoiceGuess: () -> Unit,
    isGenerating: Boolean,
    onStartPrompt: () -> Unit,
    onSubmitDescription: () -> Unit,
    onSubmitGuess: () -> Unit,
    onNextRound: () -> Unit,
) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                actionTitle(phase, promptReady),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            if (phase == PerfectManPhase.PartnerGuesses) {
                Surface(
                    color = GameColors.accent.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("本轮分数", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$score / 10", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    }
                }
            }

            when {
                result != null -> {
                    ResultInline(result = result)
                    Button(onClick = onNextRound, modifier = Modifier.fillMaxWidth()) {
                        Icon(HugeIcons.Refresh03, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("下一轮")
                    }
                }

                phase == PerfectManPhase.UserGuesses && !promptReady -> {
                    Button(
                        onClick = onStartPrompt,
                        enabled = !isGenerating,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(HugeIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isGenerating) "对面正在想" else "开始")
                    }
                }

                phase == PerfectManPhase.UserGuesses -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = guessText,
                            onValueChange = onGuessTextChange,
                            modifier = Modifier.weight(1f),
                            label = { Text("0-10 分") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        IconButton(onClick = onVoiceGuess) {
                            Icon(
                                HugeIcons.Voice,
                                contentDescription = if (listeningTarget == VoiceInputTarget.Guess && isListening) {
                                    "停止听写"
                                } else {
                                    "语音猜分"
                                },
                            )
                        }
                    }
                    Button(
                        onClick = onSubmitGuess,
                        enabled = !isGenerating && guessText.trim().toFloatOrNull() != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(HugeIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isGenerating) "对面正在回应" else "发送分数")
                    }
                }

                else -> {
                    OutlinedTextField(
                        value = description,
                        onValueChange = onDescriptionChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        label = { Text("这是一个满分男，但是...") },
                        placeholder = { Text("例如：10天不洗脚，也不洗澡。") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(onClick = onVoiceDescription, modifier = Modifier.weight(1f)) {
                            Icon(HugeIcons.Voice, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (listeningTarget == VoiceInputTarget.Flaw && isListening) "停止听写" else "语音输入")
                        }
                        OutlinedButton(onClick = onExample, modifier = Modifier.weight(1f)) {
                            Icon(HugeIcons.Sparkles, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("随机缺点")
                        }
                    }
                    Button(
                        onClick = onSubmitDescription,
                        enabled = !isGenerating && description.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(HugeIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isGenerating) "对方正在想" else "发送给对方")
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultInline(result: RoundResult) {
    Surface(
        color = if (result.success) GameColors.success.copy(alpha = 0.14f) else GameColors.soft.copy(alpha = 0.18f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (result.success) "差值 ${result.diff} 分，算默契。" else "差值 ${result.diff} 分。",
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "真实分：${result.score}，猜分：${result.guess}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun actionTitle(phase: PerfectManPhase, promptReady: Boolean): String =
    when (phase) {
        PerfectManPhase.UserGuesses -> if (promptReady) "我猜分" else "开始这一轮"
        PerfectManPhase.PartnerGuesses -> "我来描述"
    }

private const val GAME_WAITING_MARKER = "（选择角色并开始这一轮）"
private const val GAME_GENERATING_MARKER = "（正在生成角色回应）"
private const val GAME_REPLY_FAILURE_MARKER = "（本轮角色回复生成失败，请重试）"
private const val GAME_REPLY_FORMAT_FAILURE_MARKER = "（本轮角色回复缺少有效分数，请重试）"

private enum class VoiceInputTarget {
    Flaw,
    Guess,
}

private enum class PerfectManPhase {
    UserGuesses,
    PartnerGuesses,
}

private data class RoundResult(
    val guess: Int,
    val score: Int,
    val success: Boolean,
    val diff: Int,
)

private data class GameTile(
    val title: String,
    val subtitle: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

private val PerfectManExamples = listOf(
    "10天不洗脚，也不洗澡。",
    "每次约会都要先讲半小时自己的梦。",
    "微信回复很快，但每句话都带工作总结格式。",
    "长得像建模脸，但是吃饭会把香菜当主菜。",
    "情绪稳定到吵架时会拿白板画流程图。",
    "很会做饭，但所有菜都坚持放薄荷。",
    "记得所有纪念日，但礼物永远买同款保温杯。",
    "声音特别好听，但睡前故事只讲刑法案例。",
)

internal object GameColors {
    val background = Color(0xFFF8F4F0)
    val accent = Color(0xFF8B3D5E)
    val success = Color(0xFF2E8B68)
    val soft = Color(0xFF6F6A87)
    val heroBrush = Brush.linearGradient(listOf(Color(0xFF8B3D5E), Color(0xFFBD7E64), Color(0xFF4D314E)))
}
