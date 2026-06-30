package me.rerere.rikkahub.ui.pages.voicecall

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.rerere.asr.ASRStatus
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft02
import me.rerere.hugeicons.stroke.Call02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Moon02
import me.rerere.hugeicons.stroke.PlayCircle
import me.rerere.hugeicons.stroke.StopCircle
import me.rerere.hugeicons.stroke.TransactionHistory
import me.rerere.hugeicons.stroke.VolumeHigh
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.getAssistantTTSProvider
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.voicecall.VoiceCallLine
import me.rerere.rikkahub.data.voicecall.VoiceCallRepository
import me.rerere.rikkahub.data.voicecall.VoiceCallRole
import me.rerere.rikkahub.data.voicecall.VoiceCallSession
import me.rerere.rikkahub.data.voicecall.VoiceCallStatus
import me.rerere.rikkahub.data.voicecall.hasUserFacingContent
import me.rerere.rikkahub.ui.components.ui.FloatingWindow
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.components.message.extractSpeakableRoleText
import me.rerere.rikkahub.ui.components.message.splitIntoVisualBubbles
import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.hooks.CustomTtsState
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.service.VoiceCallForegroundService
import me.rerere.rikkahub.service.ChatService
import me.rerere.tts.model.PlaybackStatus
import me.rerere.tts.provider.TTSProviderSetting
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.uuid.Uuid

private enum class CallStage {
    Idle,
    Connecting,
    Active,
    Ended,
}

@Composable
fun VoiceCallPage(
    conversationId: String,
    assistantId: String,
    sessionId: String? = null,
) {
    val navController = LocalNavController.current
    val settings = LocalSettings.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember(context) { VoiceCallRepository(context.applicationContext) }
    val chatService: ChatService = koinInject()
    val tts = LocalTTSState.current
    val asr = LocalASRState.current
    val asrState by asr.state.collectAsState()
    val isSpeaking by tts.isSpeaking.collectAsState()
    val scope = rememberCoroutineScope()
    val assistant = remember(settings, assistantId) {
        runCatching { settings.getAssistantById(Uuid.parse(assistantId)) }.getOrNull()
    }
    val ttsProvider = remember(settings, assistant) {
        assistant?.let { settings.getAssistantTTSProvider(it.id) }
    }
    val assistantName = assistant?.name?.ifBlank { "Lulu" } ?: "Lulu"
    val isHistoryOnly = sessionId != null
    var session by remember(sessionId, conversationId, assistantId) { mutableStateOf<VoiceCallSession?>(null) }
    var stage by remember(sessionId) { mutableStateOf(if (isHistoryOnly) CallStage.Ended else CallStage.Idle) }
    var showMiniWindow by remember { mutableStateOf(false) }
    var sleepMode by remember { mutableStateOf(false) }
    var sleepMinutes by remember { mutableLongStateOf(20L) }
    var lastTranscript by remember { mutableStateOf("") }
    var assistantTurnInProgress by remember { mutableStateOf(false) }
    var silenceJob by remember { mutableStateOf<Job?>(null) }
    var sleepJob by remember { mutableStateOf<Job?>(null) }
    val latestSession by rememberUpdatedState(session)
    val latestStage by rememberUpdatedState(stage)

    fun saveLine(line: VoiceCallLine, speak: Boolean = false) {
        val current = session ?: return
        val updated = repository.appendLine(current, line)
        session = updated
        if (speak) {
            val speakableText = line.text.extractSpeakableRoleText()
            if (speakableText.isBlank()) {
                assistantTurnInProgress = false
            } else {
                scope.launch {
                    assistantTurnInProgress = true
                    try {
                        speakInSegments(tts, speakableText, ttsProvider)
                    } finally {
                        assistantTurnInProgress = false
                    }
                }
            }
        }
    }

    fun saveAssistantSleepLine(text: String) {
        saveLine(
            VoiceCallLine(
                role = VoiceCallRole.Assistant,
                text = text,
                replayable = true,
            ),
            speak = false,
        )
    }

    fun assistantSay(text: String, replayable: Boolean = true) {
        saveLine(
            VoiceCallLine(
                role = VoiceCallRole.Assistant,
                text = text,
                replayable = replayable,
            ),
            speak = true,
        )
    }

    fun startCall() {
        if (isHistoryOnly || stage != CallStage.Idle) return
        VoiceCallForegroundService.start(context.applicationContext, assistantName)
        stage = CallStage.Connecting
        scope.launch {
            assistantTurnInProgress = true
            try {
                val opening = chatService.sendVoiceCallTurn(
                    conversationId = Uuid.parse(conversationId),
                    text = buildVoiceCallOpeningPrompt(assistantName),
                    visibleUserText = null,
                )
                stage = CallStage.Active
                assistantSay(
                    text = opening?.takeIf { it.isNotBlank() }
                        ?: "${assistantName}接到电话了。我在这里陪着你，慢慢说就好。",
                    replayable = true,
                )
            } catch (_: Throwable) {
                stage = CallStage.Active
                assistantSay(
                    text = "${assistantName}接到电话了。我在这里陪着你，慢慢说就好。",
                    replayable = true,
                )
            }
        }
    }

    fun startListening() {
        if (isHistoryOnly || sleepMode || stage != CallStage.Active || assistantTurnInProgress || isSpeaking) return
        if (asrState.status != ASRStatus.Idle && asrState.status != ASRStatus.Error) return
        lastTranscript = ""
        asr.start { transcript ->
            val text = transcript.trim()
            if (text.isBlank() || text == lastTranscript) return@start
            lastTranscript = text
            silenceJob?.cancel()
            silenceJob = scope.launch {
                delay(2_000)
                asr.stop()
                val finalText = lastTranscript.trim()
                if (finalText.isNotBlank()) {
                    saveLine(VoiceCallLine(role = VoiceCallRole.User, text = finalText))
                    assistantTurnInProgress = true
                    val reply = try {
                        chatService.sendVoiceCallTurn(
                            conversationId = Uuid.parse(conversationId),
                            text = "$finalText\n\n$VOICE_CALL_REPLY_PROMPT",
                            visibleUserText = finalText,
                        )
                    } catch (_: Throwable) {
                        null
                    }
                    assistantSay(
                        text = reply ?: "我刚刚有点没接住，你再轻轻说一遍，好不好？",
                        replayable = true,
                    )
                }
            }
        }
    }

    fun endCall() {
        silenceJob?.cancel()
        sleepJob?.cancel()
        asr.stop()
        tts.stop()
        val ended = session?.let { repository.endSession(it) }
        session = ended ?: session?.copy(status = VoiceCallStatus.Ended, endedAt = System.currentTimeMillis())
        VoiceCallForegroundService.stop(context.applicationContext)
        stage = CallStage.Idle
        session = repository.createSession(
            conversationId = conversationId,
            assistantId = assistantId,
            assistantName = assistantName,
            initialLines = emptyList(),
            persistImmediately = false,
        )
    }

    LaunchedEffect(sessionId, conversationId, assistantId) {
        session = sessionId
            ?.let { repository.getSession(it) }
            ?: repository.createSession(
                conversationId = conversationId,
                assistantId = assistantId,
                assistantName = assistantName,
                initialLines = emptyList(),
                persistImmediately = false,
            )
    }

    LaunchedEffect(isSpeaking, stage, sleepMode, assistantTurnInProgress, asrState.status) {
        if (shouldStartVoiceCallListening(
                stageActive = stage == CallStage.Active,
                isHistoryOnly = isHistoryOnly,
                sleepMode = sleepMode,
                assistantTurnInProgress = assistantTurnInProgress,
                isSpeaking = isSpeaking,
                asrStatus = asrState.status,
            )
        ) {
            delay(350)
            startListening()
        }
    }

    LaunchedEffect(sleepMode, sleepMinutes, stage) {
        sleepJob?.cancel()
        if (!sleepMode || stage != CallStage.Active) return@LaunchedEffect
        asr.stop()
        val current = session ?: return@LaunchedEffect
        val updated = repository.replaceSession(current.copy(sleepMode = true))
        session = updated
        sleepJob = scope.launch {
            val deadline = System.currentTimeMillis() + sleepMinutes * 60_000
            val segments = buildSleepTalkSegments(assistantName)
            var index = 0
            while (isActive && System.currentTimeMillis() < deadline) {
                if (stage != CallStage.Active || !sleepMode) return@launch
                val segment = segments[index % segments.size]
                saveAssistantSleepLine(segment)
                segment.extractSpeakableRoleText().takeIf { it.isNotBlank() }?.let {
                    tts.speak(it, flushCalled = true, providerOverride = ttsProvider)
                }
                waitForTtsPlayback(tts)
                delay(180)
                index++
            }
            if (stage == CallStage.Active && sleepMode) endCall()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            silenceJob?.cancel()
            sleepJob?.cancel()
            if (!isHistoryOnly) asr.stop()
            if (!isHistoryOnly) VoiceCallForegroundService.stop(context.applicationContext)
            if (!isHistoryOnly && latestStage != CallStage.Ended) {
                latestSession?.let {
                    if (it.hasUserFacingContent()) {
                        repository.endSession(it)
                    } else {
                        repository.deleteSession(it.id)
                    }
                }
            }
        }
    }

    if (showMiniWindow && !isHistoryOnly) {
        FloatingWindow(tag = "voice_call_mini", visibility = true) {
            MiniCallWindow(
                assistantName = assistantName,
                stage = stage,
                isSpeaking = isSpeaking || assistantTurnInProgress,
                onOpen = { showMiniWindow = false },
                onEnd = { endCall() },
            )
        }
    }

    val currentSession = session
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(if (isHistoryOnly) "通话记录" else "语音通话") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(HugeIcons.ArrowLeft02, contentDescription = null)
                    }
                },
                actions = {
                    if (!isHistoryOnly) {
                        IconButton(onClick = { showMiniWindow = !showMiniWindow }) {
                            Icon(HugeIcons.Call02, contentDescription = null)
                        }
                    }
                    IconButton(
                        onClick = {
                            navController.navigate(
                                Screen.VoiceCallHistory(
                                    conversationId = conversationId,
                                    assistantId = assistantId,
                                )
                            )
                        }
                    ) {
                        Icon(HugeIcons.TransactionHistory, contentDescription = null)
                    }
                },
                colors = CustomColors.topBarColors,
                scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(),
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        if (currentSession == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("准备通话中...")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            UIAvatar(
                name = assistantName,
                value = assistant?.avatar ?: Avatar.Dummy,
                modifier = Modifier.size(84.dp),
            )
            Text(
                text = assistantName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = statusText(
                    stage = stage,
                    asrStatus = asrState.status,
                    isSpeaking = isSpeaking,
                    assistantTurnInProgress = assistantTurnInProgress,
                    sleepMode = sleepMode,
                    isHistoryOnly = isHistoryOnly,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            CallContentCard(
                stage = stage,
                isHistoryOnly = isHistoryOnly,
                assistantName = assistantName,
                session = currentSession,
                onStartCall = { startCall() },
                onReplay = { line ->
                    line.text.extractSpeakableRoleText().takeIf { it.isNotBlank() }?.let {
                        scope.launch { speakInSegments(tts, it, ttsProvider) }
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            if (!isHistoryOnly) {
                SleepModePanel(
                    enabled = sleepMode,
                    minutes = sleepMinutes,
                    onEnabledChange = { sleepMode = it },
                    onMinutesChange = { sleepMinutes = it },
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val canStartListening = shouldStartVoiceCallListening(
                        stageActive = stage == CallStage.Active,
                        isHistoryOnly = isHistoryOnly,
                        sleepMode = sleepMode,
                        assistantTurnInProgress = assistantTurnInProgress,
                        isSpeaking = isSpeaking,
                        asrStatus = asrState.status,
                    )
                    FilledTonalButton(
                        onClick = {
                            if (asrState.status == ASRStatus.Idle || asrState.status == ASRStatus.Error) {
                                startListening()
                            } else {
                                asr.stop()
                            }
                        },
                        enabled = asrState.status == ASRStatus.Listening || canStartListening,
                    ) {
                        Icon(HugeIcons.VolumeHigh, contentDescription = null)
                        Text(if (asrState.status == ASRStatus.Listening) "停止倾听" else "开始倾听")
                    }
                    FilledIconButton(
                        onClick = { endCall() },
                        enabled = stage != CallStage.Idle,
                        modifier = Modifier.size(58.dp),
                    ) {
                        Icon(HugeIcons.Cancel01, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceCallHistoryPage(
    conversationId: String,
    assistantId: String,
) {
    val navController = LocalNavController.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember(context) { VoiceCallRepository(context.applicationContext) }
    var sessions by remember(conversationId, assistantId) {
        mutableStateOf(
            repository.getSessions().filter {
                it.conversationId == conversationId && it.assistantId == assistantId && it.hasUserFacingContent()
            }
        )
    }

    LaunchedEffect(conversationId, assistantId) {
        sessions = repository.getSessions().filter {
            it.conversationId == conversationId && it.assistantId == assistantId && it.hasUserFacingContent()
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Call history") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(HugeIcons.ArrowLeft02, contentDescription = null)
                    }
                },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("还没有通话记录")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(sessions, key = { it.id }) { item ->
                VoiceCallHistoryItem(
                    session = item,
                    onClick = {
                        navController.navigate(
                            Screen.VoiceCall(
                                conversationId = conversationId,
                                assistantId = assistantId,
                                sessionId = item.id,
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun CallContentCard(
    stage: CallStage,
    isHistoryOnly: Boolean,
    assistantName: String,
    session: VoiceCallSession,
    onStartCall: () -> Unit,
    onReplay: (VoiceCallLine) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier, colors = CustomColors.cardColorsOnSurfaceContainer) {
        when {
            stage == CallStage.Idle && !isHistoryOnly -> IdleCallPanel(
                assistantName = assistantName,
                onStartCall = onStartCall,
            )

            stage == CallStage.Connecting && !isHistoryOnly -> ConnectingPanel(assistantName = assistantName)

            else -> TranscriptList(session = session, onReplay = onReplay)
        }
    }
}

@Composable
private fun IdleCallPanel(
    assistantName: String,
    onStartCall: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            HugeIcons.Call02,
            contentDescription = null,
            modifier = Modifier.size(54.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(14.dp))
        Text("准备呼叫 $assistantName", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(18.dp))
        FilledTonalButton(onClick = onStartCall) {
            Icon(HugeIcons.Call02, contentDescription = null)
            Text("开始通话")
        }
    }
}

@Composable
private fun ConnectingPanel(assistantName: String) {
    val transition = rememberInfiniteTransition(label = "calling_pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(760),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "calling_pulse_scale",
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(76.dp).scale(pulse).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                HugeIcons.Call02,
                contentDescription = null,
                modifier = Modifier.size(38.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("正在呼叫 $assistantName...", style = MaterialTheme.typography.titleMedium)
        Text(
            "正在接通",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TranscriptList(
    session: VoiceCallSession,
    onReplay: (VoiceCallLine) -> Unit,
) {
    val listState = rememberLazyListState()
    val visibleTranscript = remember(session.transcript) {
        session.transcript.filter { it.role != VoiceCallRole.System }
    }
    LaunchedEffect(visibleTranscript.size) {
        if (visibleTranscript.isNotEmpty()) {
            listState.animateScrollToItem(visibleTranscript.lastIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(visibleTranscript) { line ->
            TranscriptLine(line = line, onReplay = { onReplay(line) })
        }
        if (visibleTranscript.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "等待通话内容...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TranscriptLine(
    line: VoiceCallLine,
    onReplay: () -> Unit,
) {
    val isUser = line.role == VoiceCallRole.User
    val segments = remember(line.text, line.role) {
        if (line.role == VoiceCallRole.Assistant) line.text.splitIntoVisualBubbles() else emptyList()
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = when (line.role) {
                VoiceCallRole.User -> "我"
                VoiceCallRole.Assistant -> "对方"
                VoiceCallRole.System -> "系统"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isUser && line.replayable && line.text.isNotBlank()) {
                IconButton(onClick = onReplay, modifier = Modifier.size(34.dp)) {
                    Icon(HugeIcons.PlayCircle, contentDescription = null)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (line.role == VoiceCallRole.Assistant && segments.isNotEmpty()) {
                    segments.forEach { segment ->
                        TranscriptSegmentBubble(text = segment, isUser = false)
                    }
                } else {
                    TranscriptSegmentBubble(text = line.text, isUser = isUser)
                }
            }
        }
    }
}

@Composable
private fun TranscriptSegmentBubble(
    text: String,
    isUser: Boolean,
) {
    val color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(color)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SleepModePanel(
    enabled: Boolean,
    minutes: Long,
    onEnabledChange: (Boolean) -> Unit,
    onMinutesChange: (Long) -> Unit,
) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(HugeIcons.Moon02, contentDescription = null)
                    Text("哄睡通话模式", fontWeight = FontWeight.Medium)
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10L, 20L, 30L, 45L).forEach { item ->
                    FilterChip(
                        selected = minutes == item,
                        onClick = { onMinutesChange(item) },
                        label = { Text("${item}m") },
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceCallHistoryItem(
    session: VoiceCallSession,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (session.sleepMode) HugeIcons.Moon02 else HugeIcons.Call02, contentDescription = null)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(session.assistantName, style = MaterialTheme.typography.titleSmall)
                Text(
                    formatTime(session.startedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${session.transcript.count { it.role != VoiceCallRole.System }} 条记录",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MiniCallWindow(
    assistantName: String,
    stage: CallStage,
    isSpeaking: Boolean,
    onOpen: () -> Unit,
    onEnd: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(HugeIcons.Call02, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(assistantName, style = MaterialTheme.typography.labelLarge)
                Text(
                    miniStatusText(stage, isSpeaking),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEnd, modifier = Modifier.size(34.dp)) {
                Icon(HugeIcons.StopCircle, contentDescription = null)
            }
        }
    }
}

private fun statusText(
    stage: CallStage,
    asrStatus: ASRStatus,
    isSpeaking: Boolean,
    assistantTurnInProgress: Boolean,
    sleepMode: Boolean,
    isHistoryOnly: Boolean,
): String {
    if (isHistoryOnly) return "已保存的通话记录"
    if (stage == CallStage.Idle) return "准备通话"
    if (sleepMode) return "哄睡中"
    if (stage == CallStage.Connecting) return "正在接通"
    if (stage == CallStage.Ended) return "已挂断"
    if (isSpeaking || assistantTurnInProgress) return "正在说话"
    return when (asrStatus) {
        ASRStatus.Connecting -> "正在准备麦克风"
        ASRStatus.Listening -> "正在倾听"
        ASRStatus.Stopping -> "正在思考"
        ASRStatus.Error -> "麦克风异常"
        ASRStatus.Idle -> "准备倾听"
    }
}

internal fun shouldStartVoiceCallListening(
    stageActive: Boolean,
    isHistoryOnly: Boolean,
    sleepMode: Boolean,
    assistantTurnInProgress: Boolean,
    isSpeaking: Boolean,
    asrStatus: ASRStatus,
): Boolean =
    stageActive &&
        !isHistoryOnly &&
        !sleepMode &&
        !assistantTurnInProgress &&
        !isSpeaking &&
        (asrStatus == ASRStatus.Idle || asrStatus == ASRStatus.Error)

private suspend fun waitForTtsPlayback(tts: CustomTtsState) {
    var observedActive = false
    repeat(1_200) {
        val status = tts.playbackState.value.status
        val speaking = tts.isSpeaking.value
        if (speaking || status == PlaybackStatus.Playing || status == PlaybackStatus.Buffering) {
            observedActive = true
        }
        if (observedActive && !speaking && status != PlaybackStatus.Playing && status != PlaybackStatus.Buffering) {
            return
        }
        delay(250)
    }
}

private suspend fun speakInSegments(
    tts: CustomTtsState,
    text: String,
    providerOverride: TTSProviderSetting? = null,
) {
    val segments = text.splitIntoVisualBubbles().filter { it.isNotBlank() }
    segments.forEachIndexed { index, segment ->
        tts.speak(segment, flushCalled = index == 0, providerOverride = providerOverride)
        waitForTtsPlayback(tts)
        delay(120)
    }
}

private fun buildSleepTalkSegments(assistantName: String): List<String> {
    return listOf(
        "${assistantName}在这里陪着你。你不需要回答，闭上眼睛听就好。",
        "今晚可以不用再那么用力了。你是安全的，也有人好好惦记着你。",
        "轻轻吸一口气，停一小会儿，再慢慢呼出去。",
        "今天已经撑过来了，这样就够了。把身体的重量交给床吧。",
        "想象一个暖暖的小房间，窗外有很轻的雨声。",
        "被子拉到肩膀旁边，旁边还有一盏很柔和的小灯。",
        "如果脑子里还有没做完的事，就先让它们在门口等一等。",
        "不需要急着睡着，也不需要回应我。",
        "额头放松一点，下巴松开一点，肩膀也慢慢落下来。",
        "手可以不用攥着了，胸口可以软一点，腿和脚都变得沉沉的。",
        "我给你讲一个小画面。我们晚上沿着很安静的湖边散步。",
        "水面那边有暖暖的窗灯，每一步都很慢，很轻。",
        "现在你不用表现得很好，也不用解释什么，更不用证明自己有用。",
        "被在乎不是要靠努力换来的。你只是待在这里，也很珍贵。",
        "如果有念头冒出来，就让它像一小片云一样飘过去。",
        "你可以回到我的声音里，回到枕头上，回到下一次慢慢的呼吸里。",
        "如果困意来了，就顺着它走。",
        "如果睡意还远，我也不会催你。",
        "我会轻轻地、近近地陪着你。没有刺耳的东西，也没有着急的事。",
        "只剩下暖和、安全，还有有人在你身边停着的感觉。",
    )
}

private fun miniStatusText(stage: CallStage, isSpeaking: Boolean): String {
    if (isSpeaking) return "正在说话"
    return when (stage) {
        CallStage.Idle -> "待机"
        CallStage.Connecting -> "接通中"
        CallStage.Active -> "通话中"
        CallStage.Ended -> "已挂断"
    }
}

private fun buildVoiceCallOpeningPrompt(assistantName: String): String =
    """
    这是一个来自用户的语音电话，现在电话已经接通。
    你是$assistantName，请你主动先开口和用户说第一句话，不要等用户先说话。
    请只输出你要说出口的话，不要复述这段说明，不要输出动作、心理、环境、感受，也不要加标签。
    """.trimIndent()

private const val VOICE_CALL_REPLY_PROMPT =
    "请只输出你要说出口的话，不要输出动作、心理、环境、感受，也不要加标签。"


private fun formatTime(value: Long): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(value))
}
