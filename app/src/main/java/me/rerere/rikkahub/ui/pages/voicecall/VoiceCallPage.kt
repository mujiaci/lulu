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
import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.hooks.CustomTtsState
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.tts.model.PlaybackStatus
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
    val tts = LocalTTSState.current
    val asr = LocalASRState.current
    val asrState by asr.state.collectAsState()
    val isSpeaking by tts.isSpeaking.collectAsState()
    val scope = rememberCoroutineScope()
    val assistant = remember(settings, assistantId) {
        runCatching { settings.getAssistantById(Uuid.parse(assistantId)) }.getOrNull()
    }
    val assistantName = assistant?.name?.ifBlank { "Lulu" } ?: "Lulu"
    val isHistoryOnly = sessionId != null
    var session by remember(sessionId, conversationId, assistantId) { mutableStateOf<VoiceCallSession?>(null) }
    var stage by remember(sessionId) { mutableStateOf(if (isHistoryOnly) CallStage.Ended else CallStage.Idle) }
    var showMiniWindow by remember { mutableStateOf(false) }
    var sleepMode by remember { mutableStateOf(false) }
    var sleepMinutes by remember { mutableLongStateOf(20L) }
    var lastTranscript by remember { mutableStateOf("") }
    var silenceJob by remember { mutableStateOf<Job?>(null) }
    var sleepJob by remember { mutableStateOf<Job?>(null) }
    val latestSession by rememberUpdatedState(session)
    val latestStage by rememberUpdatedState(stage)

    fun saveLine(line: VoiceCallLine, speak: Boolean = false) {
        val current = session ?: return
        val updated = repository.appendLine(current, line)
        session = updated
        if (speak) tts.speak(line.text, flushCalled = true)
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
        stage = CallStage.Connecting
        scope.launch {
            delay(1_800)
            stage = CallStage.Active
            assistantSay(
                text = "I picked up. I am here with you. Take your time and talk to me when you are ready.",
                replayable = false,
            )
        }
    }

    fun startListening() {
        if (isHistoryOnly || sleepMode || stage != CallStage.Active) return
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
                    assistantSay(buildAssistantReply(finalText))
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
        stage = CallStage.Ended
        if (ended == null) navController.popBackStack()
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

    LaunchedEffect(isSpeaking, stage, sleepMode) {
        if (!isSpeaking && stage == CallStage.Active && !sleepMode) {
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
                assistantSay(segments[index % segments.size])
                waitForTtsPlayback(tts)
                delay(700)
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
                isSpeaking = isSpeaking,
                onOpen = { showMiniWindow = false },
                onEnd = { endCall() },
            )
        }
    }

    val currentSession = session
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(if (isHistoryOnly) "Call record" else "Voice call") },
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
                Text("Preparing call...")
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
                text = statusText(stage, asrState.status, isSpeaking, sleepMode, isHistoryOnly),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            CallContentCard(
                stage = stage,
                isHistoryOnly = isHistoryOnly,
                assistantName = assistantName,
                session = currentSession,
                onStartCall = { startCall() },
                onReplay = { line -> tts.speak(line.text, flushCalled = true) },
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
                    FilledTonalButton(
                        onClick = {
                            if (asrState.status == ASRStatus.Idle || asrState.status == ASRStatus.Error) {
                                startListening()
                            } else {
                                asr.stop()
                            }
                        },
                        enabled = stage == CallStage.Active && !sleepMode,
                    ) {
                        Icon(HugeIcons.VolumeHigh, contentDescription = null)
                        Text(if (asrState.status == ASRStatus.Listening) "Stop listen" else "Listen")
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
                Text("No call history yet")
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
        Text("Ready to call $assistantName", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(18.dp))
        FilledTonalButton(onClick = onStartCall) {
            Icon(HugeIcons.Call02, contentDescription = null)
            Text("Start call")
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
        Text("Calling $assistantName...", style = MaterialTheme.typography.titleMedium)
        Text(
            "Connecting",
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
    LaunchedEffect(session.transcript.size) {
        if (session.transcript.isNotEmpty()) {
            listState.animateScrollToItem(session.transcript.lastIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(session.transcript) { line ->
            TranscriptLine(line = line, onReplay = { onReplay(line) })
        }
        if (session.transcript.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No transcript yet",
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
    val color = when (line.role) {
        VoiceCallRole.User -> MaterialTheme.colorScheme.primaryContainer
        VoiceCallRole.Assistant -> MaterialTheme.colorScheme.secondaryContainer
        VoiceCallRole.System -> Color.Transparent
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = when (line.role) {
                VoiceCallRole.User -> "Me"
                VoiceCallRole.Assistant -> "Assistant"
                VoiceCallRole.System -> "System"
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
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(color)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(line.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
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
                    Text("Sleep call mode", fontWeight = FontWeight.Medium)
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
                "${session.transcript.count { it.role != VoiceCallRole.System }} lines",
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
                    if (isSpeaking) "Speaking" else stage.name.lowercase(),
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
    sleepMode: Boolean,
    isHistoryOnly: Boolean,
): String {
    if (isHistoryOnly) return "Saved call record"
    if (stage == CallStage.Idle) return "Ready"
    if (sleepMode) return "Sleep mode"
    if (stage == CallStage.Connecting) return "Connecting"
    if (stage == CallStage.Ended) return "Ended"
    if (isSpeaking) return "Speaking"
    return when (asrStatus) {
        ASRStatus.Connecting -> "Preparing microphone"
        ASRStatus.Listening -> "Listening"
        ASRStatus.Stopping -> "Thinking"
        ASRStatus.Error -> "Mic error"
        ASRStatus.Idle -> "Ready"
    }
}

private fun buildAssistantReply(userText: String): String {
    val trimmed = userText.take(80)
    return "I heard you say: $trimmed. I am staying with you, and I will answer more naturally once this call is connected to the chat model."
}

private suspend fun waitForTtsPlayback(tts: CustomTtsState) {
    var observedActive = false
    repeat(1_200) {
        val status = tts.playbackState.value.status
        if (status == PlaybackStatus.Playing || status == PlaybackStatus.Buffering) {
            observedActive = true
        }
        if (observedActive && status != PlaybackStatus.Playing && status != PlaybackStatus.Buffering) {
            return
        }
        delay(250)
    }
}

private fun buildSleepTalkSegments(assistantName: String): List<String> {
    return listOf(
        "$assistantName is here. You do not need to say anything now. Let your shoulders loosen, let your breathing slow down, and just listen. You are safe, you are loved, and nothing urgent needs you right now.",
        "Breathe in slowly, and breathe out even slower. I will stay beside you. You can let the day fall away piece by piece, like setting down a heavy bag at the door.",
        "Close your eyes if you want to. Imagine a warm little light near your pillow. It is quiet, soft, and steady. I am speaking gently, and you only need to rest.",
        "If any thought comes by, you do not need to chase it. Let it pass. Come back to my voice, come back to the blanket, come back to this small safe room.",
        "You have done enough for today. Even if something was unfinished, it can wait. Right now your only job is to be held by the dark and slowly drift down.",
        "I am proud of you for making it here. You can unclench your hands, relax your jaw, and let your breathing become lazy and deep.",
        "Think of a quiet night road with warm windows far away. Nothing is asking for you. The world is soft at the edges, and sleep is allowed to come closer.",
        "I will keep talking softly. You do not have to answer me. Just receive this: you matter, you are wanted, and you can rest without earning it.",
        "Let your body become heavier. Your feet, your knees, your shoulders, your face. Everything can sink a little deeper into the bed.",
        "If you are still awake, that is okay too. We are not forcing sleep. We are just making a kind place for sleep to arrive when it wants.",
    )
}

private fun formatTime(value: Long): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(value))
}
