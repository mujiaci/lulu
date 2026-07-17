package me.rerere.rikkahub.ui.pages.assistant.detail

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.forAssistant
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.service.ProactiveMessageService
import me.rerere.rikkahub.data.service.ProactiveMessageWorker
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.ai.ReasoningButton
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.TagsInput
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.hooks.heroAnimation
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.toFixed
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt
import me.rerere.rikkahub.data.model.Tag as DataTag

@Composable
fun AssistantBasicPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.assistant_page_tab_basic))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        AssistantBasicContent(
            modifier = Modifier.padding(innerPadding),
            assistant = assistant,
            providers = providers,
            tags = tags,
            onUpdate = { vm.update(it) },
            vm = vm
        )
    }
}

@Composable
internal fun AssistantBasicContent(
    modifier: Modifier = Modifier,
    assistant: Assistant,
    providers: List<me.rerere.ai.provider.ProviderSetting>,
    tags: List<DataTag>,
    onUpdate: (Assistant) -> Unit,
    vm: AssistantDetailVM
) {
    val context = LocalContext.current
    val historyClearState by vm.historyClearState.collectAsStateWithLifecycle()
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var memoryIntervalInput by remember(assistant.memoryExtractionInterval) {
        mutableStateOf(assistant.memoryExtractionInterval.toString())
    }
    val faceReferencePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.setFaceReferenceImage(assistant, it) }
    }

    RikkaConfirmDialog(
        show = showClearHistoryDialog,
        title = "清除角色历史",
        confirmText = "清除",
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            showClearHistoryDialog = false
            vm.clearAssistantHistory()
        },
        onDismiss = { showClearHistoryDialog = false },
        text = {
            Text("会不可撤销地清除这个角色的聊天与电话、收藏、长期记忆、辞海日记、关系、挂心、承诺和未触发的主动提醒。人设、头像、模型设置、世界书及考研计划会保留。")
        },
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            UIAvatar(
                value = assistant.avatar,
                name = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                onUpdate = { avatar ->
                    onUpdate(
                        assistant.copy(
                            avatar = avatar
                        )
                    )
                },
                modifier = Modifier
                    .size(80.dp)
                    .heroAnimation("assistant_${assistant.id}")
            )
        }

        Card(
            colors = CustomColors.cardColorsOnSurfaceContainer
        ) {
            FormItem(
                label = {
                    Text("角色姓名 / 昵称")
                },
                description = {
                    Text("这里改名后，桌面、聊天页、头像和角色选择器会同步显示。")
                },
                modifier = Modifier.padding(8.dp),

            ) {
                OutlinedTextField(
                    value = assistant.name,
                    onValueChange = {
                        onUpdate(
                            assistant.copy(
                                name = it
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()

            FormItem(
                label = {
                    Text("角色资料 / 设定备注")
                },
                description = {
                    Text("和提示词页的系统人设同步，用来写角色身份、关系、语气和互动边界。")
                },
                modifier = Modifier.padding(8.dp),
            ) {
                OutlinedTextField(
                    value = assistant.systemPrompt,
                    onValueChange = {
                        onUpdate(
                            assistant.copy(
                                systemPrompt = it
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                )
            }

            HorizontalDivider()

            FormItem(
                label = {
                    Text("角色外貌 / 生图参考")
                },
                description = {
                    Text("写这个角色稳定的外貌、发色、瞳色、体型、气质和标志物。考研奖励生图会优先使用这里。")
                },
                modifier = Modifier.padding(8.dp),
            ) {
                OutlinedTextField(
                    value = assistant.appearancePrompt,
                    onValueChange = {
                        onUpdate(
                            assistant.copy(
                                appearancePrompt = it
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                )
            }

            HorizontalDivider()

            FormItem(
                label = {
                    Text("角色脸部参考图")
                },
                description = {
                    Text("用于生图时尽量锁住脸型、眼睛、发型和气质；如果站子或模型不支持图片参考，会退化为文字提示。")
                },
                modifier = Modifier.padding(8.dp),
            ) {
                val faceReference = assistant.faceReferenceImage
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (faceReference != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(96.dp),
                        ) {
                            AsyncImage(
                                model = faceReference,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { faceReferencePicker.launch("image/*") }) {
                            Text(if (faceReference == null) "上传脸图" else "更换脸图")
                        }
                        if (faceReference != null) {
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    onUpdate(assistant.copy(faceReferenceImage = null))
                                },
                            ) {
                                Text("删除")
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            FormItem(
                label = {
                    Text(stringResource(R.string.assistant_page_tags))
                },
                modifier = Modifier.padding(8.dp),
            ) {
                TagsInput(
                    value = assistant.tags,
                    tags = tags,
                    onValueChange = { tagIds, tagList ->
                        vm.updateTags(tagIds, tagList)
                    },
                )
            }

            HorizontalDivider()

            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_use_assistant_avatar))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_use_assistant_avatar_desc))
                },
                tail = {
                    Switch(
                        checked = assistant.useAssistantAvatar,
                        onCheckedChange = {
                            onUpdate(
                                assistant.copy(
                                    useAssistantAvatar = it
                                )
                            )
                        }
                    )
                }
            )
        }

        Card(
            colors = CustomColors.cardColorsOnSurfaceContainer
        ) {
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_chat_model))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_chat_model_desc))
                },
                content = {
                    ModelSelector(
                        modelId = assistant.chatModelId,
                        providers = providers,
                        type = ModelType.CHAT,
                        onSelect = {
                            onUpdate(
                                assistant.copy(
                                    chatModelId = it.id
                                )
                            )
                        },
                    )
                }
            )
            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_temperature))
                },
                description = {
                    Text(
                        text = buildAnnotatedString {
                            append(stringResource(R.string.assistant_page_temperature_warning))
                        }
                    )
                },
                tail = {
                    Switch(
                        checked = assistant.temperature != null,
                        onCheckedChange = { enabled ->
                            onUpdate(
                                assistant.copy(
                                    temperature = if (enabled) 1.0f else null
                                )
                            )
                        }
                    )
                }
            ) {
                if (assistant.temperature != null) {
                    var temperatureInput by remember(assistant.id) {
                        mutableStateOf(assistant.temperature.toString())
                    }
                    val temperatureValue = temperatureInput.toFloatOrNull()
                    OutlinedTextField(
                        value = temperatureInput,
                        onValueChange = { value ->
                            temperatureInput = value
                            value.toFloatOrNull()?.takeIf { it in 0f..2f }?.let { temperature ->
                                onUpdate(
                                    assistant.copy(
                                        temperature = temperature
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = temperatureValue == null || temperatureValue !in 0f..2f,
                        supportingText = {
                            Text("0 - 2")
                        }
                    )
                }
            }
            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_top_p))
                },
                description = {
                    Text(
                        text = buildAnnotatedString {
                            append(stringResource(R.string.assistant_page_top_p_warning))
                        }
                    )
                },
                tail = {
                    Switch(
                        checked = assistant.topP != null,
                        onCheckedChange = { enabled ->
                            onUpdate(
                                assistant.copy(
                                    topP = if (enabled) 1.0f else null
                                )
                            )
                        }
                    )
                }
            ) {
                assistant.topP?.let { topP ->
                    var topPInput by remember(assistant.id) {
                        mutableStateOf(topP.toString())
                    }
                    val topPValue = topPInput.toFloatOrNull()
                    OutlinedTextField(
                        value = topPInput,
                        onValueChange = { value ->
                            topPInput = value
                            value.toFloatOrNull()?.takeIf { it in 0f..1f }?.let { nextTopP ->
                                onUpdate(
                                    assistant.copy(
                                        topP = nextTopP
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = topPValue == null || topPValue !in 0f..1f,
                        supportingText = {
                            Text("0 - 1")
                        }
                    )
                }
            }
            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_context_message_size))
                },
                description = {
                    Text(
                        text = stringResource(R.string.assistant_page_context_message_desc),
                    )
                }
            ) {
                Slider(
                    value = assistant.contextMessageSize.toFloat(),
                    onValueChange = {
                        onUpdate(
                            assistant.copy(
                                contextMessageSize = it.roundToInt()
                            )
                        )
                    },
                    valueRange = 0f..512f,
                    steps = 0,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = if (assistant.contextMessageSize > 0) stringResource(
                        R.string.assistant_page_context_message_count,
                        assistant.contextMessageSize
                    ) else stringResource(R.string.assistant_page_context_message_unlimited),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f),
                )
            }
            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text("记忆总结间隔") },
                description = {
                    Text("最近 10 条聊天会保留为当前上下文，不参与总结。可读取的旧聊天达到这个数值时，角色才会整理一次长期记忆。0 表示关闭自动整理。")
                },
            ) {
                Slider(
                    value = assistant.memoryExtractionInterval.coerceIn(0, 100).toFloat(),
                    onValueChange = { value ->
                        val interval = value.roundToInt()
                        memoryIntervalInput = interval.toString()
                        onUpdate(assistant.copy(memoryExtractionInterval = interval))
                    },
                    valueRange = 0f..100f,
                    steps = 99,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = memoryIntervalInput,
                    onValueChange = { value ->
                        memoryIntervalInput = value
                        value.toIntOrNull()?.coerceIn(0, 500)?.let { interval ->
                            onUpdate(assistant.copy(memoryExtractionInterval = interval))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("可读取旧聊天达到多少条时提取") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    supportingText = {
                        Text(
                            if (assistant.memoryExtractionInterval <= 0) {
                                "已关闭自动整理"
                            } else {
                                "当前：${assistant.memoryExtractionInterval} 条；加上最近保留的 10 条，总聊天达到 ${assistant.memoryExtractionInterval + 10} 条时首次整理。"
                            },
                        )
                    },
                )
            }
            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_stream_output))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_stream_output_desc))
                },
                tail = {
                    Switch(
                        checked = assistant.streamOutput,
                        onCheckedChange = {
                            onUpdate(
                                assistant.copy(
                                    streamOutput = it
                                )
                            )
                        }
                    )
                }
            )
            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text("语音自动播放")
                },
                description = {
                    Text("开启后，这个角色每次回复完成会自动生成并播放语音；关闭后只在你点消息下方的声音按钮时生成。")
                },
                tail = {
                    Switch(
                        checked = assistant.autoPlayVoice,
                        onCheckedChange = {
                            onUpdate(
                                assistant.copy(
                                    autoPlayVoice = it
                                )
                            )
                        }
                    )
                }
            )
            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text("MiniMax 音色 Voice ID")
                },
                description = {
                    Text("只在当前 TTS 服务选择 MiniMax 时生效。留空就使用全局 MiniMax 音色。")
                },
            ) {
                OutlinedTextField(
                    value = assistant.ttsVoiceId,
                    onValueChange = {
                        onUpdate(assistant.copy(ttsVoiceId = it.trim()))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("例如 female-shaonv") },
                    singleLine = true,
                )
            }
            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_thinking_budget))
                },
            ) {
                ReasoningButton(
                    reasoningLevel = assistant.reasoningLevel,
                    onUpdateReasoningLevel = { level ->
                        onUpdate(assistant.copy(reasoningLevel = level))
                    }
                )
            }
            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_max_tokens))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_max_tokens_desc))
                }
            ) {
                OutlinedTextField(
                    value = assistant.maxTokens?.toString() ?: "",
                    onValueChange = { text ->
                        val tokens = if (text.isBlank()) {
                            null
                        } else {
                            text.toIntOrNull()?.takeIf { it > 0 }
                        }
                        onUpdate(
                            assistant.copy(
                                maxTokens = tokens
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(stringResource(R.string.assistant_page_max_tokens_no_limit))
                    },
                    supportingText = {
                        if (assistant.maxTokens != null) {
                            Text(stringResource(R.string.assistant_page_max_tokens_limit, assistant.maxTokens))
                        } else {
                            Text(stringResource(R.string.assistant_page_max_tokens_no_token_limit))
                        }
                    }
                )
            }
        }

        Card(
            colors = CustomColors.cardColorsOnSurfaceContainer
        ) {
            val proactiveSetting = assistant.proactiveMessageSetting.forAssistant(assistant.id.toString())
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text("主动消息") },
                description = { Text("每个角色独立设置。开启后，这个角色会按自己的节奏主动来找你。") },
                tail = {
                    Switch(
                        checked = proactiveSetting.enabled,
                        onCheckedChange = { enabled ->
                            val next = proactiveSetting.copy(enabled = enabled)
                            onUpdate(assistant.copy(proactiveMessageSetting = next))
                            if (enabled) {
                                ProactiveMessageService.scheduleNext(context, next)
                            } else {
                                ProactiveMessageService.cancel(context)
                            }
                        }
                    )
                },
            )
            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text("自然节奏") },
                description = { Text("根据最近聊天、角色挂心、承诺和关系状态决定下一次判断，不按固定分钟循环发消息。") },
                tail = {
                    Switch(
                        checked = proactiveSetting.naturalScheduling,
                        onCheckedChange = { enabled ->
                            onUpdate(
                                assistant.copy(
                                    proactiveMessageSetting = proactiveSetting.copy(naturalScheduling = enabled),
                                ),
                            )
                        },
                    )
                },
            )
            if (!proactiveSetting.naturalScheduling) {
                HorizontalDivider()
                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = { Text("最小间隔（分钟）") },
                    description = { Text("主动消息随机间隔的下限。") },
                ) {
                    OutlinedTextField(
                        value = proactiveSetting.minIntervalMinutes.toString(),
                        onValueChange = { value ->
                            value.toIntOrNull()
                                ?.takeIf { it > 0 }
                                ?.let { minutes ->
                                    onUpdate(
                                        assistant.copy(
                                            proactiveMessageSetting = proactiveSetting.copy(
                                                minIntervalMinutes = minutes,
                                                maxIntervalMinutes = proactiveSetting.maxIntervalMinutes.coerceAtLeast(minutes),
                                            )
                                        )
                                    )
                                }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
                HorizontalDivider()
                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = { Text("最大间隔（分钟）") },
                    description = { Text("主动消息随机间隔的上限，必须大于等于最小间隔。") },
                ) {
                    OutlinedTextField(
                        value = proactiveSetting.maxIntervalMinutes.toString(),
                        onValueChange = { value ->
                            value.toIntOrNull()
                                ?.takeIf { it >= proactiveSetting.minIntervalMinutes }
                                ?.let { minutes ->
                                    onUpdate(
                                        assistant.copy(
                                            proactiveMessageSetting = proactiveSetting.copy(maxIntervalMinutes = minutes)
                                        )
                                    )
                                }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                HorizontalDivider()
                val hasExactAlarm = ProactiveMessageWorker.canScheduleExactAlarms(context)
                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = { Text("精确闹钟权限") },
                    description = {
                        Text(if (hasExactAlarm) "已允许精确调度。" else "未允许时会用备用调度，时间可能不够准。")
                    },
                    tail = {
                        if (!hasExactAlarm) {
                            TextButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.fromParts("package", context.packageName, null)
                                            }
                                        )
                                    }
                                }
                            ) {
                                Text("设置")
                            }
                        }
                    },
                )
            }
            HorizontalDivider()
            val isIgnoringBattery = ProactiveMessageWorker.isIgnoringBatteryOptimizations(context)
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text("电池优化") },
                description = {
                    Text(if (isIgnoringBattery) "已忽略电池优化，后台触发更稳定。" else "建议允许忽略电池优化，否则后台主动消息可能被系统限制。")
                },
                tail = {
                    if (!isIgnoringBattery) {
                        TextButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                    )
                                }.onFailure {
                                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                }
                            }
                        ) {
                            Text("授权")
                        }
                    }
                },
            )
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text("清除角色记录")
                },
                description = {
                    Text(
                        when (val state = historyClearState) {
                            HistoryClearState.Idle -> "清除这个角色产生的全部交互记录，保留人设、世界书与考研计划。"
                            HistoryClearState.Running -> "正在清除角色记录…"
                            HistoryClearState.Success -> "角色记录已清除，人设、世界书与考研计划均已保留。"
                            is HistoryClearState.Failed -> state.message
                        }
                    )
                },
                tail = {
                    TextButton(
                        enabled = historyClearState !is HistoryClearState.Running,
                        onClick = { showClearHistoryDialog = true },
                    ) {
                        Text(if (historyClearState is HistoryClearState.Running) "清除中" else "清除")
                    }
                },
            )
        }

        Card(
            colors = CustomColors.cardColorsOnSurfaceContainer
        ) {
            BackgroundPicker(
                modifier = Modifier.padding(8.dp),
                background = assistant.background,
                backgroundOpacity = assistant.backgroundOpacity,
                onUpdate = { background ->
                    onUpdate(
                        assistant.copy(
                            background = background
                        )
                    )
                }
            )

            if (assistant.background != null) {
                val backgroundOpacity = assistant.backgroundOpacity.coerceIn(0f, 1f)
                HorizontalDivider()
                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = {
                        Text(stringResource(R.string.assistant_page_background_opacity))
                    },
                    description = {
                        Text(stringResource(R.string.assistant_page_background_opacity_desc))
                    }
                ) {
                    Slider(
                        value = backgroundOpacity,
                        onValueChange = {
                            onUpdate(
                                assistant.copy(
                                    backgroundOpacity = it.toFixed(2).toFloatOrNull()?.coerceIn(0f, 1f) ?: 1.0f
                                )
                            )
                        },
                        valueRange = 0f..1f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(
                            R.string.assistant_page_background_opacity_value,
                            (backgroundOpacity * 100).roundToInt()
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}
