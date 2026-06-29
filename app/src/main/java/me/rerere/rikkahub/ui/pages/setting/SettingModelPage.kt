package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.text.KeyboardOptions
import me.rerere.ai.core.ReasoningLevel
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.Earth
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.FileZip
import me.rerere.hugeicons.stroke.Image01
import me.rerere.hugeicons.stroke.Mortarboard01
import me.rerere.hugeicons.stroke.Message01
import me.rerere.hugeicons.stroke.MessageMultiple01
import me.rerere.hugeicons.stroke.Notebook01
import me.rerere.hugeicons.stroke.Tools
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.ModelType
import kotlin.uuid.Uuid
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_COMPRESS_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_OCR_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_SUGGESTION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TITLE_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TRANSLATION_PROMPT
import me.rerere.rikkahub.ui.components.ai.ReasoningButton
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingModelPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.setting_model_page_title))
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
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            defaultModelSettingSections().forEach { section ->
                item {
                    when (section) {
                        ModelSettingSection.CHAT -> DefaultChatModelSetting(settings = settings, vm = vm)
                        ModelSettingSection.MEMORY_EMBEDDING -> DefaultMemoryEmbeddingModelSetting(settings = settings, vm = vm)
                        ModelSettingSection.MEMORY_RERANK -> DefaultMemoryRerankModelSetting(settings = settings, vm = vm)
                        ModelSettingSection.MEMORY_EXTRACTION -> DefaultMemoryExtractionModelSetting(settings = settings, vm = vm)
                        ModelSettingSection.IMAGE_GENERATION -> DefaultImageGenerationModelSetting(settings = settings, vm = vm)
                        ModelSettingSection.SUGGESTION -> DefaultSuggestionModelSetting(settings = settings, vm = vm)
                        ModelSettingSection.TRANSLATION -> DefaultTranslationModelSetting(settings = settings, vm = vm)
                        ModelSettingSection.OCR -> DefaultOcrModelSetting(settings = settings, vm = vm)
                        ModelSettingSection.COMPRESS -> DefaultCompressModelSetting(settings = settings, vm = vm)
                        ModelSettingSection.TITLE_SUMMARY -> DefaultTitleModelSetting(settings = settings, vm = vm)
                    }
                }
            }
        }
    }
}

internal enum class ModelSettingSection {
    CHAT,
    MEMORY_EMBEDDING,
    MEMORY_RERANK,
    MEMORY_EXTRACTION,
    IMAGE_GENERATION,
    SUGGESTION,
    TRANSLATION,
    OCR,
    COMPRESS,
    TITLE_SUMMARY,
}

internal fun defaultModelSettingSections(): List<ModelSettingSection> = listOf(
    ModelSettingSection.CHAT,
    ModelSettingSection.MEMORY_EMBEDDING,
    ModelSettingSection.MEMORY_RERANK,
    ModelSettingSection.MEMORY_EXTRACTION,
    ModelSettingSection.IMAGE_GENERATION,
    ModelSettingSection.SUGGESTION,
    ModelSettingSection.TRANSLATION,
    ModelSettingSection.OCR,
    ModelSettingSection.COMPRESS,
)

@Composable
private fun DefaultMemoryEmbeddingModelSetting(
    settings: Settings,
    vm: SettingVM
) {
    var showModal by remember { mutableStateOf(false) }
    val config = settings.memoryEmbeddingConfig

    ModelFeatureCard(
        title = {
            Text(
                stringResource(R.string.setting_model_page_memory_embedding_model),
                maxLines = 1
            )
        },
        description = {
            Text(stringResource(R.string.setting_model_page_memory_embedding_model_desc))
        },
        icon = {
            Icon(HugeIcons.Database02, null)
        },
        actions = {
            Switch(
                checked = config.enabled,
                onCheckedChange = { enabled ->
                    vm.updateSettings(
                        settings.copy(
                            memoryEmbeddingConfig = config.copy(enabled = enabled)
                        )
                    )
                }
            )
            Box(modifier = Modifier.weight(1f)) {
                ModelSelector(
                    modelId = config.modelId,
                    type = ModelType.EMBEDDING,
                    onSelect = { model ->
                        vm.updateSettings(
                            settings.copy(
                                memoryEmbeddingConfig = config.copy(
                                    modelId = model.id.takeUnless { model.modelId.isBlank() }
                                )
                            )
                        )
                    },
                    providers = settings.providers,
                    allowClear = true,
                    modifier = Modifier.wrapContentWidth()
                )
            }
            IconButton(
                onClick = {
                    showModal = true
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors()
            ) {
                Icon(HugeIcons.Tools, null)
            }
        }
    )

    if (showModal) {
        ModalBottomSheet(
            onDismissRequest = {
                showModal = false
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FormItem(
                    label = {
                        Text(stringResource(R.string.setting_model_page_memory_embedding_enabled))
                    },
                    description = {
                        Text(stringResource(R.string.setting_model_page_memory_embedding_enabled_desc))
                    },
                    tail = {
                        Switch(
                            checked = config.enabled,
                            onCheckedChange = { enabled ->
                                vm.updateSettings(
                                    settings.copy(
                                        memoryEmbeddingConfig = config.copy(enabled = enabled)
                                    )
                                )
                            }
                        )
                    }
                )

                FormItem(
                    label = {
                        Text(stringResource(R.string.setting_model_page_memory_rerank_candidates))
                    },
                    description = {
                        Text(stringResource(R.string.setting_model_page_memory_rerank_candidates_desc))
                    }
                ) {
                    OutlinedTextField(
                        value = config.rerankCandidateCount.toString(),
                        onValueChange = { value ->
                            vm.updateSettings(
                                settings.copy(
                                    memoryEmbeddingConfig = config.copy(
                                        rerankCandidateCount = parseMemoryRerankCandidateCountInput(value)
                                    )
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }

                FormItem(
                    label = {
                        Text(stringResource(R.string.setting_model_page_memory_embedding_dimensions))
                    },
                    description = {
                        Text(stringResource(R.string.setting_model_page_memory_embedding_dimensions_desc))
                    }
                ) {
                    OutlinedTextField(
                        value = config.dimensions?.toString().orEmpty(),
                        onValueChange = { value ->
                            vm.updateSettings(
                                settings.copy(
                                    memoryEmbeddingConfig = config.copy(
                                        dimensions = parseMemoryEmbeddingDimensionsInput(value)
                                    )
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }

                FormItem(
                    label = {
                        Text(stringResource(R.string.setting_model_page_memory_embedding_batch_size))
                    },
                    description = {
                        Text(stringResource(R.string.setting_model_page_memory_embedding_batch_size_desc))
                    }
                ) {
                    OutlinedTextField(
                        value = config.batchSize.toString(),
                        onValueChange = { value ->
                            vm.updateSettings(
                                settings.copy(
                                    memoryEmbeddingConfig = config.copy(
                                        batchSize = parseMemoryEmbeddingBatchSizeInput(value)
                                    )
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }

                MemoryEngineDiagnostics(
                    lines = buildMemoryEngineDiagnostics(
                        enabled = config.enabled,
                        embeddingModel = settings.memoryModelName(config.modelId),
                        rerankModel = settings.memoryModelName(config.rerankModelId),
                        extractionModel = settings.memoryModelName(config.extractionModelId),
                        candidateCount = config.rerankCandidateCount,
                    )
                )
            }
        }
    }
}

@Composable
private fun DefaultMemoryRerankModelSetting(
    settings: Settings,
    vm: SettingVM
) {
    val config = settings.memoryEmbeddingConfig
    ModelFeatureCard(
        title = {
            Text(stringResource(R.string.setting_model_page_memory_rerank_model), maxLines = 1)
        },
        description = {
            Text(stringResource(R.string.setting_model_page_memory_rerank_model_desc))
        },
        icon = {
            Icon(HugeIcons.Database02, null)
        },
        actions = {
            Box(modifier = Modifier.weight(1f)) {
                ModelSelector(
                    modelId = config.rerankModelId,
                    type = ModelType.RERANK,
                    onSelect = { model ->
                        vm.updateSettings(
                            settings.copy(
                                memoryEmbeddingConfig = config.copy(
                                    rerankModelId = model.id.takeUnless { model.modelId.isBlank() }
                                )
                            )
                        )
                    },
                    providers = settings.providers,
                    modifier = Modifier.wrapContentWidth()
                )
            }
        }
    )
}

@Composable
private fun DefaultMemoryExtractionModelSetting(
    settings: Settings,
    vm: SettingVM
) {
    val config = settings.memoryEmbeddingConfig
    ModelFeatureCard(
        title = {
            Text(stringResource(R.string.setting_model_page_memory_extraction_model), maxLines = 1)
        },
        description = {
            Text(stringResource(R.string.setting_model_page_memory_extraction_model_desc))
        },
        icon = {
            Icon(HugeIcons.Mortarboard01, null)
        },
        actions = {
            Box(modifier = Modifier.weight(1f)) {
                ModelSelector(
                    modelId = config.extractionModelId,
                    type = ModelType.CHAT,
                    onSelect = { model ->
                        vm.updateSettings(
                            settings.copy(
                                memoryEmbeddingConfig = config.copy(
                                    extractionModelId = model.id.takeUnless { model.modelId.isBlank() }
                                )
                            )
                        )
                    },
                    providers = settings.providers,
                    allowClear = true,
                    modifier = Modifier.wrapContentWidth()
                )
            }
        }
    )
}

@Composable
private fun DefaultImageGenerationModelSetting(
    settings: Settings,
    vm: SettingVM
) {
    ModelFeatureCard(
        title = {
            Text(stringResource(R.string.setting_model_page_image_generation_model), maxLines = 1)
        },
        description = {
            Text(stringResource(R.string.setting_model_page_image_generation_model_desc))
        },
        icon = {
            Icon(HugeIcons.Image01, null)
        },
        actions = {
            Box(modifier = Modifier.weight(1f)) {
                ModelSelector(
                    modelId = settings.imageGenerationModelId,
                    type = ModelType.IMAGE,
                    onSelect = { model ->
                        vm.updateSettings(
                            settings.copy(
                                imageGenerationModelId = model.id
                            )
                        )
                    },
                    providers = settings.providers,
                    modifier = Modifier.wrapContentWidth()
                )
            }
        }
    )
}

private fun Settings.memoryModelName(modelId: Uuid?): String? =
    modelId
        ?.let { findModelById(it) }
        ?.let { model -> model.displayName.ifBlank { model.modelId } }

@Composable
private fun MemoryEngineDiagnostics(lines: List<String>) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = CustomColors.listItemColors.containerColor
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.setting_model_page_memory_engine_diagnostics),
                style = MaterialTheme.typography.titleSmall,
            )
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.82f),
                )
            }
        }
    }
}

internal fun parseMemoryEmbeddingDimensionsInput(value: String): Int? =
    value.trim().toIntOrNull()?.takeIf { it > 0 }

internal fun parseMemoryEmbeddingBatchSizeInput(value: String): Int =
    (value.trim().toIntOrNull() ?: 1).coerceIn(1, 64)

internal fun parseMemoryRerankCandidateCountInput(value: String): Int =
    (value.trim().toIntOrNull() ?: 5).coerceIn(5, 50)

internal fun buildMemoryEngineDiagnostics(
    enabled: Boolean,
    embeddingModel: String?,
    rerankModel: String?,
    extractionModel: String?,
    candidateCount: Int,
): List<String> {
    val mode = if (enabled) "本地向量库：已启用" else "本地向量库：未启用"
    val embedding = "Embedding：${embeddingModel?.takeIf { it.isNotBlank() } ?: "未配置"}"
    val rerank = "Reranker：${rerankModel?.takeIf { it.isNotBlank() } ?: "未配置，将使用本地混合排序"}"
    val extraction = "记忆抽取：${extractionModel?.takeIf { it.isNotBlank() } ?: "未单独配置，将使用当前聊天模型"}"
    val candidates = "重排序候选：${candidateCount.coerceIn(5, 50)} 条"
    val backend = "Backend / Vector Index：未接入，当前使用设备本地 Room 向量字段"
    return listOf(mode, embedding, rerank, extraction, candidates, backend)
}

@Composable
private fun DefaultTranslationModelSetting(
    settings: Settings,
    vm: SettingVM
) {
    var showModal by remember { mutableStateOf(false) }
    ModelFeatureCard(
        title = {
            Text(
                stringResource(R.string.setting_model_page_translate_model),
                maxLines = 1
            )
        },
        description = {
            Text(stringResource(R.string.setting_model_page_translate_model_desc))
        },
        icon = {
            Icon(HugeIcons.Earth, null)
        },
        actions = {
            Box(modifier = Modifier.weight(1f)) {
                ModelSelector(
                    modelId = settings.translateModeId,
                    type = ModelType.CHAT,
                    onSelect = {
                        vm.updateSettings(
                            settings.copy(
                                translateModeId = it.id
                            )
                        )
                    },
                    providers = settings.providers,
                    modifier = Modifier.wrapContentWidth()
                )
            }
            IconButton(
                onClick = {
                    showModal = true
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors()
            ) {
                Icon(HugeIcons.Tools, null)
            }
        }
    )

    if (showModal) {
        ModalBottomSheet(
            onDismissRequest = {
                showModal = false
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FormItem(
                    label = {
                        Text(stringResource(R.string.assistant_page_thinking_budget))
                    },
                ) {
                    ReasoningButton(
                        reasoningLevel = ReasoningLevel.fromBudgetTokens(settings.translateThinkingBudget),
                        onUpdateReasoningLevel = {
                            vm.updateSettings(settings.copy(translateThinkingBudget = it.budgetTokens))
                        }
                    )
                }

                FormItem(
                    label = {
                        Text(stringResource(R.string.setting_model_page_prompt))
                    },
                    description = {
                        Text(stringResource(R.string.setting_model_page_translate_prompt_vars))
                    }
                ) {
                    OutlinedTextField(
                        value = settings.translatePrompt,
                        onValueChange = {
                            vm.updateSettings(
                                settings.copy(
                                    translatePrompt = it
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 10,
                    )
                    TextButton(
                        onClick = {
                            vm.updateSettings(
                                settings.copy(
                                    translatePrompt = DEFAULT_TRANSLATION_PROMPT
                                )
                            )
                        }
                    ) {
                        Text(stringResource(R.string.setting_model_page_reset_to_default))
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultSuggestionModelSetting(
    settings: Settings,
    vm: SettingVM
) {
    var showModal by remember { mutableStateOf(false) }
    ModelFeatureCard(
        title = {
            Text(
                text = stringResource(R.string.setting_model_page_suggestion_model),
                maxLines = 1
            )
        },
        description = {
            Text(stringResource(R.string.setting_model_page_suggestion_model_desc))
        },
        icon = {
            Icon(HugeIcons.MessageMultiple01, null)
        },
        actions = {
            Box(modifier = Modifier.weight(1f)) {
                ModelSelector(
                    modelId = settings.suggestionModelId,
                    type = ModelType.CHAT,
                    onSelect = {
                        vm.updateSettings(
                            settings.copy(
                                suggestionModelId = it.id
                            )
                        )
                    },
                    providers = settings.providers,
                    allowClear = true,
                    modifier = Modifier.wrapContentWidth()
                )
            }
            IconButton(
                onClick = {
                    showModal = true
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors()
            ) {
                Icon(HugeIcons.Tools, null)
            }
        }
    )

    if (showModal) {
        ModalBottomSheet(
            onDismissRequest = {
                showModal = false
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FormItem(
                    label = {
                        Text(stringResource(R.string.setting_model_page_prompt))
                    },
                    description = {
                        Text(stringResource(R.string.setting_model_page_suggestion_prompt_vars))
                    }
                ) {
                    OutlinedTextField(
                        value = settings.suggestionPrompt,
                        onValueChange = {
                            vm.updateSettings(
                                settings.copy(
                                    suggestionPrompt = it
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 8
                    )
                    TextButton(
                        onClick = {
                            vm.updateSettings(
                                settings.copy(
                                    suggestionPrompt = DEFAULT_SUGGESTION_PROMPT
                                )
                            )
                        }
                    ) {
                        Text(stringResource(R.string.setting_model_page_reset_to_default))
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultTitleModelSetting(
    settings: Settings,
    vm: SettingVM
) {
    var showModal by remember { mutableStateOf(false) }
    ModelFeatureCard(
        title = {
            Text(stringResource(R.string.setting_model_page_title_model), maxLines = 1)
        },
        description = {
            Text(stringResource(R.string.setting_model_page_title_model_desc))
        },
        icon = {
            Icon(HugeIcons.Notebook01, null)
        },
        actions = {
            Box(modifier = Modifier.weight(1f)) {
                ModelSelector(
                    modelId = settings.titleModelId,
                    type = ModelType.CHAT,
                    onSelect = {
                        vm.updateSettings(
                            settings.copy(
                                titleModelId = it.id
                            )
                        )
                    },
                    providers = settings.providers,
                    allowClear = true,
                    modifier = Modifier.wrapContentWidth()
                )
            }
            IconButton(
                onClick = {
                    showModal = true
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors()
            ) {
                Icon(HugeIcons.Tools, null)
            }
        }
    )

    if (showModal) {
        ModalBottomSheet(
            onDismissRequest = {
                showModal = false
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FormItem(
                    label = {
                        Text(stringResource(R.string.setting_model_page_prompt))
                    },
                    description = {
                        Text(stringResource(R.string.setting_model_page_suggestion_prompt_vars))
                    }
                ) {
                    OutlinedTextField(
                        value = settings.titlePrompt,
                        onValueChange = {
                            vm.updateSettings(
                                settings.copy(
                                    titlePrompt = it
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 8
                    )
                    TextButton(
                        onClick = {
                            vm.updateSettings(
                                settings.copy(
                                    titlePrompt = DEFAULT_TITLE_PROMPT
                                )
                            )
                        }
                    ) {
                        Text(stringResource(R.string.setting_model_page_reset_to_default))
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultChatModelSetting(
    settings: Settings,
    vm: SettingVM
) {
    ModelFeatureCard(
        icon = {
            Icon(HugeIcons.Message01, null)
        },
        title = {
            Text(stringResource(R.string.setting_model_page_chat_model), maxLines = 1)
        },
        description = {
            Text(stringResource(R.string.setting_model_page_chat_model_desc))
        },
        actions = {
            Box(modifier = Modifier.weight(1f)) {
                ModelSelector(
                    modelId = settings.chatModelId,
                    type = ModelType.CHAT,
                    onSelect = {
                        vm.updateSettings(
                            settings.copy(
                                chatModelId = it.id
                            )
                        )
                    },
                    providers = settings.providers,
                    modifier = Modifier.wrapContentWidth()
                )
            }
        }
    )
}

@Composable
private fun DefaultOcrModelSetting(
    settings: Settings,
    vm: SettingVM
) {
    var showModal by remember { mutableStateOf(false) }
    ModelFeatureCard(
        title = {
            Text(
                stringResource(R.string.setting_model_page_ocr_model),
                maxLines = 1
            )
        },
        description = {
            Text(stringResource(R.string.setting_model_page_ocr_model_desc))
        },
        icon = {
            Icon(HugeIcons.View, null)
        },
        actions = {
            Box(modifier = Modifier.weight(1f)) {
                ModelSelector(
                    modelId = settings.ocrModelId,
                    type = ModelType.CHAT,
                    onSelect = {
                        vm.updateSettings(
                            settings.copy(
                                ocrModelId = it.id
                            )
                        )
                    },
                    providers = settings.providers,
                    modifier = Modifier.wrapContentWidth()
                )
            }
            IconButton(
                onClick = {
                    showModal = true
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors()
            ) {
                Icon(HugeIcons.Tools, null)
            }
        }
    )

    if (showModal) {
        ModalBottomSheet(
            onDismissRequest = {
                showModal = false
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FormItem(
                    label = {
                        Text(stringResource(R.string.setting_model_page_prompt))
                    },
                    description = {
                        Text(stringResource(R.string.setting_model_page_ocr_prompt_vars))
                    }
                ) {
                    OutlinedTextField(
                        value = settings.ocrPrompt,
                        onValueChange = {
                            vm.updateSettings(
                                settings.copy(
                                    ocrPrompt = it
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 10,
                    )
                    TextButton(
                        onClick = {
                            vm.updateSettings(
                                settings.copy(
                                    ocrPrompt = DEFAULT_OCR_PROMPT
                                )
                            )
                        }
                    ) {
                        Text(stringResource(R.string.setting_model_page_reset_to_default))
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultCompressModelSetting(
    settings: Settings,
    vm: SettingVM
) {
    var showModal by remember { mutableStateOf(false) }
    ModelFeatureCard(
        title = {
            Text(
                stringResource(R.string.setting_model_page_compress_model),
                maxLines = 1
            )
        },
        description = {
            Text(stringResource(R.string.setting_model_page_compress_model_desc))
        },
        icon = {
            Icon(HugeIcons.FileZip, null)
        },
        actions = {
            Box(modifier = Modifier.weight(1f)) {
                ModelSelector(
                    modelId = settings.compressModelId,
                    type = ModelType.CHAT,
                    onSelect = {
                        vm.updateSettings(
                            settings.copy(
                                compressModelId = it.id
                            )
                        )
                    },
                    providers = settings.providers,
                    modifier = Modifier.wrapContentWidth()
                )
            }
            IconButton(
                onClick = {
                    showModal = true
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors()
            ) {
                Icon(HugeIcons.Tools, null)
            }
        }
    )

    if (showModal) {
        ModalBottomSheet(
            onDismissRequest = {
                showModal = false
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FormItem(
                    label = {
                        Text(stringResource(R.string.setting_model_page_prompt))
                    },
                    description = {
                        Text(stringResource(R.string.setting_model_page_compress_prompt_vars))
                    }
                ) {
                    OutlinedTextField(
                        value = settings.compressPrompt,
                        onValueChange = {
                            vm.updateSettings(
                                settings.copy(
                                    compressPrompt = it
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 10,
                    )
                    TextButton(
                        onClick = {
                            vm.updateSettings(
                                settings.copy(
                                    compressPrompt = DEFAULT_COMPRESS_PROMPT
                                )
                            )
                        }
                    ) {
                        Text(stringResource(R.string.setting_model_page_reset_to_default))
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelFeatureCard(
    modifier: Modifier = Modifier,
    description: @Composable () -> Unit = {},
    icon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = CustomColors.listItemColors.containerColor
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    ProvideTextStyle(MaterialTheme.typography.titleMedium) {
                        title()
                    }
                    ProvideTextStyle(
                        MaterialTheme.typography.bodySmall.copy(
                            color = LocalContentColor.current.copy(alpha = 0.6f)
                        )
                    ) {
                        description()
                    }
                }
                Box(
                    modifier = Modifier
                        .size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions()
            }
        }
    }
}
