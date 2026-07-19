package me.rerere.rikkahub.ui.pages.voicecall

import android.media.RingtoneManager
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
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
import me.rerere.rikkahub.data.voicecall.ProactiveCallManager
import me.rerere.rikkahub.data.voicecall.VoiceCallLine
import me.rerere.rikkahub.data.voicecall.VoiceCallRepository
import me.rerere.rikkahub.data.voicecall.VoiceCallRole
import me.rerere.rikkahub.data.voicecall.VoiceCallSession
import me.rerere.rikkahub.data.voicecall.VoiceCallStatus
import me.rerere.rikkahub.data.voicecall.hasUserFacingContent
import me.rerere.rikkahub.data.ai.transformers.sanitizeLuluVisibleExpression
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
    Ringing,
    Connecting,
    Active,
    Ended,
}

@Composable
fun VoiceCallPage(
    conversationId: String,
    assistantId: String,
    sessionId: String? = null,
    incomingCall: Boolean = false,
    autoStart: Boolean = false,
    incomingReason: String = "",
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
    val incomingRingtone = remember(context, assistant?.proactiveCallSetting?.ringtoneUri) {
        runCatching {
            val uri = assistant?.proactiveCallSetting?.ringtoneUri
                ?.takeIf(String::isNotBlank)
                ?.let(Uri::parse)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            RingtoneManager.getRingtone(context.applicationContext, uri)
        }.getOrNull()
    }
    val ttsProvider = remember(settings, assistant) {
        assistant?.let { settings.getAssistantTTSProvider(it.id) }
    }
    val assistantName = assistant?.name?.ifBlank { "对方" } ?: "对方"
    val isHistoryOnly = sessionId != null
    var session by remember(sessionId, conversationId, assistantId) { mutableStateOf<VoiceCallSession?>(null) }
    var stage by remember(sessionId, incomingCall) {
        mutableStateOf(
            when {
                isHistoryOnly -> CallStage.Ended
                incomingCall -> CallStage.Ringing
                else -> CallStage.Idle
            },
        )
    }
    var showMiniWindow by remember { mutableStateOf(false) }
    var sleepMode by remember { mutableStateOf(false) }
    var sleepMinutes by remember { mutableLongStateOf(20L) }
    var lastTranscript by remember { mutableStateOf("") }
    var assistantTurnInProgress by remember { mutableStateOf(false) }
    var silenceJob by remember { mutableStateOf<Job?>(null) }
    var sleepJob by remember { mutableStateOf<Job?>(null) }
    var assistantGenerationJob by remember { mutableStateOf<Job?>(null) }
    var transcriptRevision by remember { mutableLongStateOf(0L) }
    var userTurnSubmitting by remember { mutableStateOf(false) }
    val latestSession by rememberUpdatedState(session)
    val latestStage by rememberUpdatedState(stage)

    fun saveLine(line: VoiceCallLine, speak: Boolean = false) {
        val current = session ?: return
        val updated = repository.appendLine(current, line)
        session = updated
        if (speak) {
            val speakableText = line.text.cleanRoleLineForUser().extractSpeakableRoleText()
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

    fun assistantSay(
        text: String,
        replayable: Boolean = true,
        speak: Boolean = true,
    ) {
        saveLine(
            VoiceCallLine(
                role = VoiceCallRole.Assistant,
                text = text,
                replayable = replayable,
            ),
            speak = speak,
        )
        if (!speak) assistantTurnInProgress = false
    }

    fun createStreamSpeaker(buffer: StringBuilder): suspend (String) -> Unit {
        var firstSegment = true
        return { segment ->
            val speakable = segment.cleanRoleLineForUser().extractSpeakableRoleText()
            if (speakable.isNotBlank()) {
                buffer.append(segment)
                tts.speak(
                    text = speakable,
                    flushCalled = firstSegment,
                    providerOverride = ttsProvider,
                )
                firstSegment = false
            }
        }
    }

    fun startCall() {
        if (isHistoryOnly || stage !in setOf(CallStage.Idle, CallStage.Ringing)) return
        VoiceCallForegroundService.start(context.applicationContext, assistantName)
        stage = CallStage.Connecting
        assistantGenerationJob?.cancel()
        assistantGenerationJob = scope.launch {
            assistantTurnInProgress = true
            val streamedOpening = StringBuilder()
            val streamSpeaker = createStreamSpeaker(streamedOpening)
            val recentOpenings = repository.recentAssistantOpenings(
                conversationId = conversationId,
                assistantId = assistantId,
            )
            val opening = try {
                chatService.sendVoiceCallTurn(
                    conversationId = Uuid.parse(conversationId),
                    text = buildVoiceCallOpeningPrompt(
                        assistantName = assistantName,
                        recentOpenings = recentOpenings,
                        variationSeed = System.currentTimeMillis(),
                        incomingReason = incomingReason.takeIf(String::isNotBlank),
                    ),
                    visibleUserText = null,
                    onPartialReply = streamSpeaker,
                ).takeIf(::isUsableVoiceCallReply)
                    ?: if (streamedOpening.isEmpty()) chatService.sendVoiceCallTurn(
                        conversationId = Uuid.parse(conversationId),
                        text = buildVoiceCallOpeningPrompt(
                            assistantName = assistantName,
                            recentOpenings = recentOpenings,
                            variationSeed = System.currentTimeMillis() + 1L,
                            retry = true,
                            incomingReason = incomingReason.takeIf(String::isNotBlank),
                        ),
                        visibleUserText = null,
                        onPartialReply = streamSpeaker,
                    ).takeIf(::isUsableVoiceCallReply) else null
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
            stage = CallStage.Active
            if (opening != null) {
                assistantSay(
                    text = opening,
                    replayable = true,
                    speak = streamedOpening.isEmpty(),
                )
            } else if (streamedOpening.isNotEmpty()) {
                assistantSay(
                    text = streamedOpening.toString(),
                    replayable = true,
                    speak = false,
                )
            } else {
                assistantTurnInProgress = false
                saveLine(
                    VoiceCallLine(
                        role = VoiceCallRole.System,
                        text = "开场回复生成失败，已恢复倾听。",
                        replayable = false,
                    ),
                )
            }
        }
    }

    fun startListening() {
        if (isHistoryOnly || sleepMode || stage != CallStage.Active || assistantTurnInProgress || isSpeaking) return
        if (userTurnSubmitting || (asrState.status != ASRStatus.Idle && asrState.status != ASRStatus.Error)) return
        lastTranscript = ""
        transcriptRevision++
        asr.start { transcript ->
            if (userTurnSubmitting || assistantTurnInProgress || stage != CallStage.Active) return@start
            val text = transcript.trim()
            if (text.isBlank() || text == lastTranscript) return@start
            lastTranscript = text
            transcriptRevision++
            val scheduledRevision = transcriptRevision
            silenceJob?.cancel()
            silenceJob = scope.launch {
                delay(voiceCallEndOfSpeechDelayMillis(text))
                if (!shouldCommitVoiceTranscript(
                        scheduledRevision = scheduledRevision,
                        currentRevision = transcriptRevision,
                        userTurnSubmitting = userTurnSubmitting,
                        stageActive = stage == CallStage.Active,
                        transcript = lastTranscript,
                    )
                ) return@launch

                val finalText = lastTranscript.trim()
                userTurnSubmitting = true
                assistantTurnInProgress = true
                asr.stop()
                saveLine(VoiceCallLine(role = VoiceCallRole.User, text = finalText))
                val streamedReply = StringBuilder()
                val streamSpeaker = createStreamSpeaker(streamedReply)
                val reply = try {
                    chatService.sendVoiceCallTurn(
                        conversationId = Uuid.parse(conversationId),
                        text = "${finalText}\n\n$VOICE_CALL_REPLY_PROMPT",
                        visibleUserText = finalText,
                        onPartialReply = streamSpeaker,
                    ).takeIf(::isUsableVoiceCallReply)
                        ?: if (streamedReply.isEmpty()) chatService.sendVoiceCallTurn(
                            conversationId = Uuid.parse(conversationId),
                            text = "${finalText}\n\n$VOICE_CALL_RETRY_PROMPT",
                            visibleUserText = null,
                            onPartialReply = streamSpeaker,
                        ).takeIf(::isUsableVoiceCallReply) else null
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    null
                } finally {
                    userTurnSubmitting = false
                }
                if (reply != null) {
                    assistantSay(text = reply, replayable = true, speak = streamedReply.isEmpty())
                } else if (streamedReply.isNotEmpty()) {
                    assistantSay(text = streamedReply.toString(), replayable = true, speak = false)
                } else {
                    assistantTurnInProgress = false
                    saveLine(
                        VoiceCallLine(
                            role = VoiceCallRole.System,
                            text = "这一轮回复生成失败，已恢复倾听。",
                            replayable = false,
                        ),
                    )
                }
            }
        }
    }

    fun endCall() {
        transcriptRevision++
        userTurnSubmitting = true
        silenceJob?.cancel()
        sleepJob?.cancel()
        assistantGenerationJob?.cancel()
        asr.stop()
        tts.stop()
        val ended = session?.let { repository.endSession(it) }
        session = ended ?: session?.copy(status = VoiceCallStatus.Ended, endedAt = System.currentTimeMillis())
        VoiceCallForegroundService.stop(context.applicationContext)
        stage = CallStage.Idle
        userTurnSubmitting = false
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

    LaunchedEffect(stage) {
        if (stage == CallStage.Ringing) {
            runCatching {
                incomingRingtone?.play()
            }
        } else {
            runCatching { incomingRingtone?.stop() }
        }
    }

    LaunchedEffect(autoStart, incomingCall, session?.id) {
        if (autoStart && incomingCall && session != null && stage == CallStage.Ringing) {
            ProactiveCallManager.markAnswered(context, assistantId)
            startCall()
        }
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
            var index = 0
            while (isActive && System.currentTimeMillis() < deadline) {
                if (stage != CallStage.Active || !sleepMode) return@launch
                val segment = try {
                    chatService.sendVoiceCallTurn(
                        conversationId = Uuid.parse(conversationId),
                        text = buildSleepTalkPrompt(
                            assistantName = assistantName,
                            sequence = index,
                        ),
                        visibleUserText = null,
                    ).takeIf(::isUsableVoiceCallReply)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    null
                }
                if (segment == null) {
                    delay(1_200)
                    continue
                }
                saveAssistantSleepLine(segment)
                segment.cleanRoleLineForUser().extractSpeakableRoleText().takeIf { it.isNotBlank() }?.let {
                    speakInSegments(tts, it, ttsProvider)
                }
                delay(500)
                index++
            }
            if (stage == CallStage.Active && sleepMode) endCall()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { incomingRingtone?.stop() }
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
            TopAppBar(
                title = { Text(if (isHistoryOnly) "通话记录" else "") },
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFFF8F3),
                    scrolledContainerColor = Color(0xFFF7F0EC),
                    titleContentColor = Color(0xFF303744),
                    navigationIconContentColor = Color(0xFF303744),
                    actionIconContentColor = Color(0xFF303744),
                ),
            )
        },
        containerColor = Color(0xFFFFF8F3),
    ) { padding ->
        if (currentSession == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("准备通话中...")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFFFF8F3), Color(0xFFF4EFF8), Color(0xFFEAF2F6)),
                    ),
                )
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                shape = RoundedCornerShape(32.dp),
                color = Color.White.copy(alpha = 0.72f),
                shadowElevation = 5.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Box(
                        modifier = Modifier.size(122.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(Color(0x66C7AEDF), Color(0x33AFC9DC), Color.Transparent),
                                    ),
                                ),
                        )
                        Surface(
                            modifier = Modifier.size(108.dp),
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.92f),
                            shadowElevation = 3.dp,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                UIAvatar(
                                    name = assistantName,
                                    value = assistant?.avatar ?: Avatar.Dummy,
                                    modifier = Modifier.size(100.dp).clip(CircleShape),
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "语音通话",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF7B6D89),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = assistantName,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF303744),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFFE9E3F2),
                        ) {
                            Text(
                                text = statusText(
                                    stage = stage,
                                    asrStatus = asrState.status,
                                    isSpeaking = isSpeaking,
                                    assistantTurnInProgress = assistantTurnInProgress,
                                    sleepMode = sleepMode,
                                    isHistoryOnly = isHistoryOnly,
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF625B70),
                            )
                        }
                    }
                }
            }
            if (stage == CallStage.Ringing) {
                Surface(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    color = Color.White.copy(alpha = 0.76f),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Text(
                                "语音来电",
                                color = Color(0xFF303744),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "接听后会从最近的聊天和共同经历自然继续。",
                                color = Color(0xFF747D8E),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            } else if (stage == CallStage.Idle && !isHistoryOnly) {
                IdleCallPanel(
                    assistantName = assistantName,
                    onStartCall = { startCall() },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                CallContentCard(
                    stage = stage,
                    isHistoryOnly = isHistoryOnly,
                    assistantName = assistantName,
                    session = currentSession,
                    onStartCall = { startCall() },
                    onReplay = { line ->
                        line.text.cleanRoleLineForUser().extractSpeakableRoleText().takeIf { it.isNotBlank() }?.let {
                            scope.launch { speakInSegments(tts, it, ttsProvider) }
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }

            if (!isHistoryOnly) {
                if (stage == CallStage.Ringing) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                ProactiveCallManager.markDeclined(context, assistantId)
                                endCall()
                                navController.popBackStack()
                            },
                            modifier = Modifier.weight(1f).height(58.dp),
                        ) {
                            Icon(HugeIcons.Cancel01, contentDescription = null)
                            Text("拒绝")
                        }
                        Button(
                            onClick = {
                                ProactiveCallManager.markAnswered(context, assistantId)
                                startCall()
                            },
                            modifier = Modifier.weight(1f).height(58.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6F91B2),
                                contentColor = Color.White,
                            ),
                        ) {
                            Icon(HugeIcons.Call02, contentDescription = null)
                            Text("接听")
                        }
                    }
                } else {
                    if (sleepMode) {
                        SleepModePanel(
                            enabled = sleepMode,
                            minutes = sleepMinutes,
                            onEnabledChange = { sleepMode = it },
                            onMinutesChange = { sleepMinutes = it },
                        )
                    } else {
                        FilterChip(
                            selected = false,
                            onClick = { sleepMode = true },
                            label = { Text("哄睡模式") },
                            leadingIcon = {
                                Icon(HugeIcons.Moon02, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                        )
                    }
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
                        val canInterruptAssistant =
                            stage in setOf(CallStage.Connecting, CallStage.Active) &&
                                !sleepMode &&
                                (isSpeaking || assistantTurnInProgress)
                        FilledTonalButton(
                            onClick = {
                                if (canInterruptAssistant) {
                                    silenceJob?.cancel()
                                    assistantGenerationJob?.cancel()
                                    tts.stop()
                                    assistantTurnInProgress = false
                                } else if (asrState.status == ASRStatus.Idle || asrState.status == ASRStatus.Error) {
                                    startListening()
                                } else {
                                    asr.stop()
                                }
                            },
                            enabled = asrState.status == ASRStatus.Listening || canStartListening || canInterruptAssistant,
                        ) {
                            Icon(HugeIcons.VolumeHigh, contentDescription = null)
                            Text(
                                when {
                                    canInterruptAssistant -> "打断并说话"
                                    asrState.status == ASRStatus.Listening -> "停止倾听"
                                    else -> "开始倾听"
                                },
                            )
                        }
                        FilledIconButton(
                            onClick = { endCall() },
                            enabled = stage !in setOf(CallStage.Idle, CallStage.Ringing),
                            modifier = Modifier.size(58.dp),
                        ) {
                            Icon(HugeIcons.Cancel01, contentDescription = null)
                        }
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
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.78f),
            contentColor = Color(0xFF303744),
        ),
    ) {
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("随时都可以聊一会儿", style = MaterialTheme.typography.titleMedium, color = Color(0xFF303744))
        Spacer(Modifier.height(8.dp))
        Text("呼叫 $assistantName", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF747D8E))
        Spacer(Modifier.height(28.dp))
        Surface(
            modifier = Modifier.size(96.dp).clickable(onClick = onStartCall),
            shape = CircleShape,
            color = Color(0xFFDCE8FF),
            contentColor = Color(0xFF365682),
            shadowElevation = 12.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(HugeIcons.Call02, contentDescription = "开始通话", modifier = Modifier.size(42.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("开始通话", style = MaterialTheme.typography.labelLarge, color = Color(0xFF596579))
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
    val displayText = remember(line.text, line.role) {
        if (line.role == VoiceCallRole.Assistant) line.text.cleanRoleLineForUser() else line.text
    }
    val segments = remember(displayText, line.role) {
        if (line.role == VoiceCallRole.Assistant) displayText.splitIntoVisualBubbles() else emptyList()
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
            if (!isUser && line.replayable && displayText.isNotBlank()) {
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
                    TranscriptSegmentBubble(text = displayText, isUser = isUser)
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
    val color = if (isUser) Color(0xFFDCE8FF) else Color(0xFFFFF9F4)
    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(color)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF303744))
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
    if (stage == CallStage.Ringing) return "正在呼叫你"
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

private fun buildSleepTalkPrompt(
    assistantName: String,
    sequence: Int,
): String =
    """
    你正在以用户设定的“$assistantName”角色进行一通持续的语音通话，用户开启了哄睡模式。
    最高优先级：始终遵守该角色原本的人设、关系类型、边界、世界观和说话方式；“哄睡”只是当前场景，不能把角色改写成默认温柔、亲密或恋爱型陪伴者。
    结合此前聊天与本次电话已经发生的内容，自然接着说一小段适合该角色的睡前话。可以安静、讲故事、闲聊或停顿，但不要重复上一段。
    这是第${sequence + 1}段。只输出真正说出口的话，1到3句，不要动作、心理、旁白、标签或后台说明。
    """.trimIndent()

private fun miniStatusText(stage: CallStage, isSpeaking: Boolean): String {
    if (isSpeaking) return "正在说话"
    return when (stage) {
        CallStage.Idle -> "待机"
        CallStage.Ringing -> "来电中"
        CallStage.Connecting -> "接通中"
        CallStage.Active -> "通话中"
        CallStage.Ended -> "已挂断"
    }
}

internal fun buildVoiceCallOpeningPrompt(
    assistantName: String,
    recentOpenings: List<String> = emptyList(),
    variationSeed: Long = 0L,
    retry: Boolean = false,
    incomingReason: String? = null,
): String {
    val recent = recentOpenings
        .take(6)
        .joinToString("\n") { "- ${it.take(180)}" }
        .ifBlank { "- 无" }
    val callOrigin = if (incomingReason.isNullOrBlank()) {
        "这是用户刚打来的一通语音电话，现在已经接通。"
    } else {
        "这是你根据自己的判断主动打给用户的一通语音电话，用户刚刚接听。你决定来电时的内部理由是：$incomingReason。理由只帮助你保持动机连续，不要求逐字说出。"
    }
    return """
        $callOrigin
        你是用户设定的“$assistantName”。最高优先级是完整遵守该角色原本的人设、关系类型、边界、世界观和说话方式；电话场景不能把角色改写成默认温柔、亲密或恋爱型陪伴者。
        请结合跨聊天与电话的最近上下文，像同一个人自然接起电话。若上次有未说完的话、明确立场或承诺，可以顺势承接，但不要复述记忆资料。
        主动说第一句话，1到2句，只输出真正说出口的话，不要动作、心理、环境、标签或后台说明。
        最近用过的开场如下，避免相同句式、相同问法和相同节奏：
        $recent
        变化种子：$variationSeed。重试：$retry。
    """.trimIndent()
}

private const val VOICE_CALL_REPLY_PROMPT =
    "保持用户设定的人设、关系类型和说话方式，承接刚才与更早的有效上下文；直接回应用户最后一句。只输出1到3句真正说出口的话，不要动作、心理、环境、感受、标签或后台说明。"

private const val VOICE_CALL_RETRY_PROMPT =
    "上一轮电话回复生成不完整。保持原人设与连续上下文，直接回应用户刚才那句话；只输出1到2句说出口的话，不要解释故障，不要让用户重复，也不要说没听清。"

internal fun isUsableVoiceCallReply(text: String?): Boolean {
    val clean = text?.cleanRoleLineForUser().orEmpty()
    return clean.isNotBlank() &&
        clean != "（本轮回复生成不完整，请重试）"
}

internal fun shouldCommitVoiceTranscript(
    scheduledRevision: Long,
    currentRevision: Long,
    userTurnSubmitting: Boolean,
    stageActive: Boolean,
    transcript: String,
): Boolean =
    scheduledRevision == currentRevision &&
        !userTurnSubmitting &&
        stageActive &&
        transcript.isNotBlank()

internal fun voiceCallEndOfSpeechDelayMillis(transcript: String): Long {
    val clean = transcript.trim()
    if (clean.lastOrNull() in setOf('。', '！', '？', '.', '!', '?')) return 1_250L
    return when {
        clean.length <= 4 -> 1_900L
        clean.length <= 12 -> 1_750L
        else -> 1_600L
    }
}

private fun String.cleanRoleLineForUser(): String =
    sanitizeLuluVisibleExpression(this)
        .lineSequence()
        .map { it.trim() }
        .filter { line ->
            line.isNotBlank() &&
                !line.startsWith("inner_voice", ignoreCase = true) &&
                !line.startsWith("inner voice", ignoreCase = true) &&
                !line.startsWith("description", ignoreCase = true) &&
                !line.startsWith("thought", ignoreCase = true)
        }
        .joinToString("\n")
        .trim()


private fun formatTime(value: Long): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(value))
}
