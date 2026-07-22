# Lulu 代码索引

> 本文件由 `scripts/generate_code_index.py` 自动生成。请勿手工维护统计区；架构决策写入同目录的人工文档。

## 使用顺序

1. 先读根目录 `AGENTS.md` 与 `docs/architecture/COMPANION_REBUILD_PLAN.md`。
2. 用本页定位功能模块和关键文件。
3. 需要逐文件符号时读取 `docs/architecture/code-index.json`。
4. 修改后运行 `python3 scripts/generate_code_index.py`；`master` 上也会由 Actions 自动刷新。

## 索引状态

- 基准提交：`ac12b3f27b0aec5b1bed89bba363b726ffa14927`
- 分支：`master`
- 已索引文件：1093
- 已索引代码/文本行：261209
- 已发现类、接口、对象、函数及 Composable：5268

## 模块概览

| 模块 | 文件数 |
|---|---:|
| `app` | 630 |
| `web-ui` | 131 |
| `document` | 76 |
| `ai` | 47 |
| `speech` | 38 |
| `search` | 30 |
| `plugins` | 25 |
| `docs` | 23 |
| `common` | 21 |
| `root` | 11 |
| `.agents` | 10 |
| `website` | 10 |
| `highlight` | 9 |
| `.github` | 7 |
| `web` | 7 |
| `.claude` | 5 |
| `locale-tui` | 4 |
| `study-plans` | 4 |
| `money-lab` | 3 |
| `gradle` | 2 |

## 文件类型

| 扩展名 | 文件数 |
|---|---:|
| `.kt` | 698 |
| `.tsx` | 74 |
| `.java` | 64 |
| `.md` | 58 |
| `.json` | 57 |
| `.ts` | 42 |
| `.xml` | 37 |
| `.pro` | 15 |
| `.kts` | 11 |
| `.js` | 9 |
| `.html` | 7 |
| `.yml` | 6 |
| `.css` | 4 |
| `.yaml` | 3 |
| `.properties` | 3 |
| `.sql` | 3 |
| `.toml` | 2 |

## 功能入口

### AI与上下文

- `ai/README.md`
- `ai/build.gradle.kts`
- `ai/consumer-rules.pro`
- `ai/proguard-rules.pro`
- `ai/src/androidTest/java/me/rerere/ai/ExampleInstrumentedTest.kt`
- `ai/src/main/AndroidManifest.xml`
- `ai/src/main/java/me/rerere/ai/core/MessageRole.kt`
- `ai/src/main/java/me/rerere/ai/core/Reasoning.kt`
- `ai/src/main/java/me/rerere/ai/core/Tool.kt`
- `ai/src/main/java/me/rerere/ai/core/Usage.kt`
- `ai/src/main/java/me/rerere/ai/provider/Model.kt`
- `ai/src/main/java/me/rerere/ai/provider/Provider.kt`
- `ai/src/main/java/me/rerere/ai/provider/ProviderManager.kt`
- `ai/src/main/java/me/rerere/ai/provider/ProviderSetting.kt`
- `ai/src/main/java/me/rerere/ai/provider/providers/ClaudeProvider.kt`
- `ai/src/main/java/me/rerere/ai/provider/providers/GoogleProvider.kt`
- `ai/src/main/java/me/rerere/ai/provider/providers/OpenAIProvider.kt`
- `ai/src/main/java/me/rerere/ai/provider/providers/ProviderMessageUtils.kt`
- `ai/src/main/java/me/rerere/ai/provider/providers/openai/ChatCompletionsAPI.kt`
- `ai/src/main/java/me/rerere/ai/provider/providers/openai/OpenAIImpl.kt`
- `ai/src/main/java/me/rerere/ai/provider/providers/openai/OpenAIPromptCaching.kt`
- `ai/src/main/java/me/rerere/ai/provider/providers/openai/ResponseAPI.kt`
- `ai/src/main/java/me/rerere/ai/provider/providers/vertex/ServiceAccountTokenProvider.kt`
- `ai/src/main/java/me/rerere/ai/registry/ModelDsl.kt`
- `ai/src/main/java/me/rerere/ai/registry/ModelRegistry.kt`
- `ai/src/main/java/me/rerere/ai/ui/Image.kt`
- `ai/src/main/java/me/rerere/ai/ui/Message.kt`
- `ai/src/main/java/me/rerere/ai/util/ErrorParser.kt`
- `ai/src/main/java/me/rerere/ai/util/FileEncoder.kt`
- `ai/src/main/java/me/rerere/ai/util/Json.kt`
- `ai/src/main/java/me/rerere/ai/util/KeyRoulette.kt`
- `ai/src/main/java/me/rerere/ai/util/Request.kt`
- `ai/src/main/java/me/rerere/ai/util/SSE.kt`
- `ai/src/main/java/me/rerere/ai/util/Serializer.kt`
- `ai/src/test/java/me/rerere/ai/ExampleUnitTest.kt`
- `ai/src/test/java/me/rerere/ai/ModelRegistryTest.kt`
- `ai/src/test/java/me/rerere/ai/provider/providers/ClaudeProviderMessageTest.kt`
- `ai/src/test/java/me/rerere/ai/provider/providers/ClaudeProviderPromptCacheTest.kt`
- `ai/src/test/java/me/rerere/ai/provider/providers/GoogleProviderMessageTest.kt`
- `ai/src/test/java/me/rerere/ai/provider/providers/OpenAIImageTransportTest.kt`
- ……另有 89 个文件，见 `code-index.json`

### 主动代理

- `app/src/main/java/me/rerere/rikkahub/data/datastore/ProactiveMessageSetting.kt`
- `app/src/main/java/me/rerere/rikkahub/data/service/NotificationListenerService.kt`
- `app/src/main/java/me/rerere/rikkahub/data/service/ProactiveMessageService.kt`
- `app/src/main/java/me/rerere/rikkahub/data/service/ProactiveMessageWorker.kt`
- `app/src/main/java/me/rerere/rikkahub/data/voicecall/ProactiveCallManager.kt`
- `app/src/main/java/me/rerere/rikkahub/service/ProactiveReminderPlanner.kt`
- `app/src/main/java/me/rerere/rikkahub/service/ProactiveToolPlanner.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProactiveMessagePage.kt`
- `app/src/main/java/me/rerere/rikkahub/utils/NotificationUtil.kt`
- `app/src/test/java/me/rerere/rikkahub/data/datastore/ProactiveCallSettingTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/service/ProactiveMessageAssemblyTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/service/ProactiveMessageContextTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/service/ProactiveMessageWorkerTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/service/ProactiveProjectionTest.kt`
- `app/src/test/java/me/rerere/rikkahub/service/ProactiveReminderPlannerTest.kt`
- `app/src/test/java/me/rerere/rikkahub/service/ProactiveToolPlannerTest.kt`
- `docs/superpowers/plans/2026-07-10-unified-companion-chat-proactive.md`

### 关系与承诺

- `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionCommitmentReducer.kt`
- `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionConcernReducer.kt`
- `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionRelationshipBehavior.kt`
- `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionRelationshipInitialization.kt`
- `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionRelationshipReducer.kt`
- `app/src/main/java/me/rerere/rikkahub/data/service/AffectiveRelationshipEvents.kt`
- `app/src/main/java/me/rerere/rikkahub/service/CompanionRelationshipPolicy.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/cihai/CompanionConcernCards.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/cihai/CompanionRelationshipTimeline.kt`
- `app/src/test/java/me/rerere/rikkahub/data/companion/CompanionCommitmentReducerTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/companion/CompanionConcernReducerTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/companion/CompanionRelationshipBehaviorTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/companion/CompanionRelationshipInitializationTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/companion/CompanionRelationshipReducerTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/service/AffectiveRelationshipEventsTest.kt`
- `app/src/test/java/me/rerere/rikkahub/ui/pages/cihai/CompanionConcernCardsTest.kt`
- `app/src/test/java/me/rerere/rikkahub/ui/pages/cihai/CompanionRelationshipTimelineTest.kt`

### 工具

- `app/src/main/java/me/rerere/rikkahub/data/ai/tools/LocalTools.kt`
- `app/src/main/java/me/rerere/rikkahub/data/ai/tools/SearchTools.kt`
- `app/src/main/java/me/rerere/rikkahub/data/ai/tools/SkillsTools.kt`
- `app/src/main/java/me/rerere/rikkahub/data/ai/tools/SystemTools.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/provider/PluginToolProvider.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageTools.kt`

### 插件

- `app/src/main/java/me/rerere/rikkahub/plugin/data/PluginDataStore.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/di/PluginModule.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/loader/LoadedPlugin.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/loader/PluginLoader.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/loader/PluginSandbox.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/manager/PluginManager.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/model/PluginInfo.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/model/PluginManifest.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/model/PluginUI.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/provider/PluginToolProvider.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/repository/PluginRepository.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/scanner/PluginScanner.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/ui/PluginDetailPage.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/ui/PluginManagePage.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/ui/PluginUIDeclarativePage.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/ui/PluginViewModel.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/webview/MusicPlayerService.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/webview/PluginWebViewPage.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/webview/PomodoroTimerService.kt`
- `plugins/example/weather/main.js`
- `plugins/example/weather/manifest.json`
- `plugins/moments/main.js`
- `plugins/moments/manifest.json`
- `plugins/moments/supabase_schema.sql`
- `plugins/moments/ui/index.html`
- `plugins/supabase_memory/README.md`
- `plugins/supabase_memory/main.js`
- `plugins/supabase_memory/manifest.json`
- `plugins/supabase_memory/supabase_schema.sql`
- `plugins/共读/main.js`
- `plugins/共读/manifest.json`
- `plugins/共读/reader.css`
- `plugins/共读/reader.html`
- `plugins/共读/reader.js`
- `plugins/橘市商业街/main.js`
- `plugins/橘市商业街/manifest.json`
- `plugins/橘市商业街/supabase_schema.sql`
- `plugins/橘市商业街/ui/index.html`
- `plugins/番茄钟/main.js`
- `plugins/番茄钟/manifest.json`
- ……另有 4 个文件，见 `code-index.json`

### 数据库

- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/1.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/10.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/11.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/12.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/13.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/14.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/15.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/16.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/17.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/18.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/19.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/2.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/20.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/21.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/22.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/23.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/24.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/25.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/26.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/27.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/3.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/4.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/5.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/6.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/7.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/8.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/9.json`
- `app/src/androidTest/java/me/rerere/rikkahub/data/db/migrations/Migration_11_12_Test.kt`
- `app/src/main/java/me/rerere/rikkahub/data/datastore/migration/PreferenceStoreV1Migration.kt`
- `app/src/main/java/me/rerere/rikkahub/data/datastore/migration/PreferenceStoreV2Migration.kt`
- `app/src/main/java/me/rerere/rikkahub/data/datastore/migration/PreferenceStoreV3Migration.kt`
- `app/src/main/java/me/rerere/rikkahub/data/datastore/migration/PreferenceStoreV4Migration.kt`
- `app/src/main/java/me/rerere/rikkahub/data/datastore/migration/SettingsJsonMigrator.kt`
- `app/src/main/java/me/rerere/rikkahub/data/db/AppDatabase.kt`
- `app/src/main/java/me/rerere/rikkahub/data/db/DatabaseMigrationTracker.kt`
- `app/src/main/java/me/rerere/rikkahub/data/db/dao/ConversationDAO.kt`
- `app/src/main/java/me/rerere/rikkahub/data/db/dao/FavoriteDAO.kt`
- `app/src/main/java/me/rerere/rikkahub/data/db/dao/GenMediaDAO.kt`
- `app/src/main/java/me/rerere/rikkahub/data/db/dao/ManagedFileDAO.kt`
- `app/src/main/java/me/rerere/rikkahub/data/db/dao/MemoryBankDAO.kt`
- ……另有 35 个文件，见 `code-index.json`

### 构建与发布

- `.github/workflows/apply-memory-diagnostics-ui.yml`
- `.github/workflows/code-index.yml`
- `.github/workflows/deploy-website.yml`
- `.github/workflows/release.yml`
- `ai/build.gradle.kts`
- `app/baselineprofile/build.gradle.kts`
- `app/build.gradle.kts`
- `build.gradle.kts`
- `common/build.gradle.kts`
- `document/build.gradle.kts`
- `highlight/build.gradle.kts`
- `search/build.gradle.kts`
- `settings.gradle.kts`
- `speech/build.gradle.kts`
- `web/build.gradle.kts`

### 电话与语音

- `app/src/main/java/me/rerere/rikkahub/data/voicecall/ProactiveCallManager.kt`
- `app/src/main/java/me/rerere/rikkahub/data/voicecall/VoiceCallModels.kt`
- `app/src/main/java/me/rerere/rikkahub/data/voicecall/VoiceCallRepository.kt`
- `app/src/main/java/me/rerere/rikkahub/data/voicecall/VoiceCallStreaming.kt`
- `app/src/main/java/me/rerere/rikkahub/service/VoiceCallForegroundService.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/voicecall/VoiceCallPage.kt`
- `app/src/test/java/me/rerere/rikkahub/data/voicecall/VoiceCallRepositorySummaryTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/voicecall/VoiceCallStreamingTest.kt`
- `app/src/test/java/me/rerere/rikkahub/service/VoiceCallChatSyncTest.kt`
- `app/src/test/java/me/rerere/rikkahub/ui/VoiceCallStateTest.kt`
- `speech/build.gradle.kts`
- `speech/consumer-rules.pro`
- `speech/proguard-rules.pro`
- `speech/src/androidTest/java/me/rerere/tts/ExampleInstrumentedTest.kt`
- `speech/src/main/AndroidManifest.xml`
- `speech/src/main/java/me/rerere/asr/ASRController.kt`
- `speech/src/main/java/me/rerere/asr/ASRProviderSetting.kt`
- `speech/src/main/java/me/rerere/asr/ASRState.kt`
- `speech/src/main/java/me/rerere/asr/AudioAmplitude.kt`
- `speech/src/main/java/me/rerere/asr/EmojiStrip.kt`
- `speech/src/main/java/me/rerere/asr/providers/OpenAIRealtimeASRController.kt`
- `speech/src/main/java/me/rerere/asr/providers/SiliconFlowASRController.kt`
- `speech/src/main/java/me/rerere/asr/providers/VolcengineASRController.kt`
- `speech/src/main/java/me/rerere/tts/controller/AudioPlayer.kt`
- `speech/src/main/java/me/rerere/tts/controller/TextChunker.kt`
- `speech/src/main/java/me/rerere/tts/controller/TtsAudioCache.kt`
- `speech/src/main/java/me/rerere/tts/controller/TtsController.kt`
- `speech/src/main/java/me/rerere/tts/controller/TtsSynthesizer.kt`
- `speech/src/main/java/me/rerere/tts/model/PlaybackState.kt`
- `speech/src/main/java/me/rerere/tts/model/TTSRequest.kt`
- `speech/src/main/java/me/rerere/tts/model/TTSResponse.kt`
- `speech/src/main/java/me/rerere/tts/provider/TTSManager.kt`
- `speech/src/main/java/me/rerere/tts/provider/TTSProvider.kt`
- `speech/src/main/java/me/rerere/tts/provider/TTSProviderSetting.kt`
- `speech/src/main/java/me/rerere/tts/provider/providers/GeminiTTSProvider.kt`
- `speech/src/main/java/me/rerere/tts/provider/providers/GroqTTSProvider.kt`
- `speech/src/main/java/me/rerere/tts/provider/providers/MiMoTTSProvider.kt`
- `speech/src/main/java/me/rerere/tts/provider/providers/MiniMaxTTSProvider.kt`
- `speech/src/main/java/me/rerere/tts/provider/providers/OpenAITTSProvider.kt`
- `speech/src/main/java/me/rerere/tts/provider/providers/QwenTTSProvider.kt`
- ……另有 8 个文件，见 `code-index.json`

### 界面

- `ai/src/main/java/me/rerere/ai/ui/Image.kt`
- `ai/src/main/java/me/rerere/ai/ui/Message.kt`
- `ai/src/test/java/me/rerere/ai/ui/MessageTest.kt`
- `ai/src/test/java/me/rerere/ai/ui/ToolApprovalStateTest.kt`
- `app/src/main/java/me/rerere/rikkahub/di/ViewModelModule.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/ui/PluginDetailPage.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/ui/PluginManagePage.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/ui/PluginUIDeclarativePage.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/ui/PluginViewModel.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/webview/PluginWebViewPage.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/activity/SafeModeActivity.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/activity/ShortcutHandlerActivity.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/AsrButton.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/AssistantPicker.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/AttachmentChips.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ChatInput.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/CompressContextDialog.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/CropLauncher.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ExtensionContent.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/FilesPicker.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/McpPicker.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ModelList.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ProviderBalanceText.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ReasoningPicker.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/SearchPicker.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/easteregg/EmojiBurst.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessage.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageActions.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageAvatar.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageBranch.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageCopySheet.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageCot.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageNerdLine.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageReasoning.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageTools.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageTranslation.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/message/RoleReplySegments.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/nav/BackButton.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/richtext/HighlightCodeBlock.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/richtext/LatexText.kt`
- ……另有 244 个文件，见 `code-index.json`

### 记忆

- `.github/workflows/apply-memory-diagnostics-ui.yml`
- `app/src/main/java/me/rerere/rikkahub/data/ai/prompts/TitleSummary.kt`
- `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionAlwaysOnMemory.kt`
- `app/src/main/java/me/rerere/rikkahub/data/db/dao/MemoryBankDAO.kt`
- `app/src/main/java/me/rerere/rikkahub/data/db/dao/MemoryDAO.kt`
- `app/src/main/java/me/rerere/rikkahub/data/db/entity/MemoryBankEntity.kt`
- `app/src/main/java/me/rerere/rikkahub/data/db/entity/MemoryEntity.kt`
- `app/src/main/java/me/rerere/rikkahub/data/db/entity/MemoryExtractionBatchEntity.kt`
- `app/src/main/java/me/rerere/rikkahub/data/db/entity/MemoryExtractionCheckpointEntity.kt`
- `app/src/main/java/me/rerere/rikkahub/data/db/entity/MemoryGraphEdgeEntity.kt`
- `app/src/main/java/me/rerere/rikkahub/data/service/AffectiveMemoryExtractionPlanner.kt`
- `app/src/main/java/me/rerere/rikkahub/data/service/AffectiveMemoryExtractor.kt`
- `app/src/main/java/me/rerere/rikkahub/data/service/DailySummaryService.kt`
- `app/src/main/java/me/rerere/rikkahub/data/service/MemoryBankService.kt`
- `app/src/main/java/me/rerere/rikkahub/data/service/MemoryVector.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/memory/MemoryBankPage.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/memory/MemoryBankVM.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/memory/MemoryDiagnostics.kt`
- `app/src/test/java/me/rerere/rikkahub/data/companion/CompanionAlwaysOnMemoryTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/db/entity/MemoryGraphEdgeEntityTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/service/AffectiveMemoryExtractionPlannerTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/service/AffectiveMemoryExtractorTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/service/MemoryArchiveTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/service/MemoryBankContextTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/service/MemoryBankServiceExtractionTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/service/MemoryVectorTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/voicecall/VoiceCallRepositorySummaryTest.kt`
- `app/src/test/java/me/rerere/rikkahub/ui/pages/memory/MemoryAssistantLabelTest.kt`
- `app/src/test/java/me/rerere/rikkahub/ui/pages/memory/MemoryDiagnosticsTest.kt`
- `app/src/test/java/me/rerere/rikkahub/ui/pages/setting/MemoryEmbeddingConfigInputTest.kt`
- `docs/architecture/MEMORY_PIPELINE.md`
- `docs/superpowers/plans/2026-06-29-affective-memory-extraction.md`
- `docs/superpowers/plans/2026-06-29-affective-vector-memory.md`
- `docs/superpowers/plans/2026-07-10-cihai-memory-settlement.md`
- `docs/superpowers/plans/2026-07-10-memory-worldbook-presence-recovery.md`
- `docs/superpowers/specs/2026-07-10-cihai-memory-settlement-design.md`
- `docs/superpowers/specs/2026-07-10-memory-worldbook-presence-recovery-design.md`
- `plugins/supabase_memory/README.md`
- `plugins/supabase_memory/main.js`
- `plugins/supabase_memory/manifest.json`
- ……另有 1 个文件，见 `code-index.json`

## 关键符号快速入口

| 文件 | 符号 |
|---|---|
| `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt` | `Assistant` |
| `app/src/main/java/me/rerere/rikkahub/data/ai/CompanionContextEnvelope.kt` | `CompanionContextEnvelope` |
| `app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt` | `GenerationHandler` |
| `app/src/main/java/me/rerere/rikkahub/data/db/AppDatabase.kt` | `AppDatabase` |
| `app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt` | `Assistant` |
| `app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt` | `Conversation` |
| `app/src/main/java/me/rerere/rikkahub/data/service/MemoryBankService.kt` | `MemoryBankService` |
| `app/src/main/java/me/rerere/rikkahub/data/service/ProactiveMessageService.kt` | `ProactiveMessageService` |
| `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt` | `ChatService` |
| `app/src/main/java/me/rerere/rikkahub/web/routes/ConversationRoutes.kt` | `Conversation` |

## 维护约束

- 自动生成提交使用 `[skip ci]`，避免索引工作流循环触发。
- 二进制、构建产物、依赖缓存和生成目录不会进入索引。
- 本页是导航，不替代测试、数据库迁移说明或任务账本。
- 若索引基准提交落后于当前 `master`，先重新生成再据此修改代码。
