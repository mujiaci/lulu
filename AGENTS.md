# Repository Guidelines

本文档面向贡献者，概述本仓库的模块结构、开发流程与提交规范，便于快速上手并保持一致的协作质量。

## Build, Test, and Development Commands

使用 Android Studio 或命令行 Gradle：

```bash
./gradlew assembleDebug          # 构建 Debug APK
./gradlew test                   # 运行所有模块的 JVM 单元测试
./gradlew connectedDebugAndroidTest  # 运行设备/模拟器上的仪器测试
./gradlew lint                   # 运行 Android Lint
```

构建应用需要在 `app/` 下提供 `google-services.json`（用于 Firebase）。

## Coding Style & Naming Conventions

本仓库使用 `.editorconfig` 统一格式：

- Kotlin/Gradle 脚本：4 空格缩进，最大行长 120。
- XML/JSON：2 空格缩进。
- Markdown/YAML：2 空格缩进，允许尾随空格（用于对齐）。

命名习惯：模块名为小写目录（如 `ai/`、`tts/`），Kotlin 类遵循 PascalCase，测试类以 `*Test` 结尾。

## Testing Guidelines

测试框架以 JUnit/AndroidX Test 为主。未设定强制覆盖率门槛，但新逻辑应配套新增/更新测试。测试文件命名建议：

- 单元测试：`FooTest.kt`
- 仪器测试：`FooInstrumentedTest.kt` 或 `*Test.kt`

## Module Structure

- **app**: Main application module with UI, ViewModels, and core logic
- **ai**: AI SDK abstraction layer for different providers (OpenAI, Google, Anthropic)
- **common**: Common utilities and extensions
- **document**: Document parsing module for handling PDF, DOCX, and PPTX files
- **highlight**: Code syntax highlighting implementation
- **search**: Search functionality SDK (Exa, Tavily, Zhipu)
- **tts**: Text-to-speech implementation for different providers
- **web**: Embedded web server module that provides Ktor server startup function and hosts static frontend build files (
  built from web-ui/ React project)

## Concepts

- **Assistant**: An assistant configuration with system prompts, model parameters, and conversation isolation. Each
  assistant maintains its own settings including temperature, context size, custom headers, tools, memory options, regex
  transformations, and prompt injections (mode/lorebook). Assistants provide isolated chat environments with specific
  behaviors and capabilities. (app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt)

- **Conversation**: A persistent conversation thread between the user and an assistant. Each conversation maintains a
  list of MessageNodes in a tree structure to support message branching, along with metadata like title, creation time,
  and pin status. Conversations can be truncated at a specific index and maintain chat suggestions. (
  app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt)

- **UIMessage**: A platform-agnostic message abstraction that encapsulates chat messages with different types of content
  parts (text, images, documents, reasoning, tool calls/results, etc.). Each message has a role (USER, ASSISTANT,
  SYSTEM, TOOL), creation timestamp, model ID, token usage information, and optional annotations. UIMessages support
  streaming updates through chunk merging. (ai/src/main/java/me/rerere/ai/ui/Message.kt)

- **MessageNode**: A container holding one or more UIMessages to implement message branching functionality. Each node
  maintains a list of alternative messages and tracks which message is currently selected (selectIndex). This enables
  users to regenerate responses and switch between different conversation branches, creating a tree-like conversation
  structure. (app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt)

- **Message Transformer**: A pipeline mechanism for transforming messages before sending to AI providers (
  InputMessageTransformer) or after receiving responses (OutputMessageTransformer). Transformers can modify message
  content, add metadata, apply templates, handle special tags, convert formats, and perform OCR. Common transformers
  include:
  - TemplateTransformer: Apply Pebble templates to user messages with variables like time/date
  - ThinkTagTransformer: Extract `<think>` tags and convert to reasoning parts
  - RegexOutputTransformer: Apply regex replacements to assistant responses
  - DocumentAsPromptTransformer: Convert document attachments to text prompts
  - Base64ImageToLocalFileTransformer: Convert base64 images to local file references
  - OcrTransformer: Perform OCR on images to extract text

  Output transformers support `visualTransform()` for UI display during streaming and `onGenerationFinish()` for final
  processing after generation completes.
  (app/src/main/java/me/rerere/rikkahub/data/ai/transformers/Transformer.kt)

## Internationalization

- String resources located in `app/src/main/res/values-*/strings.xml`
- Use `stringResource(R.string.key_name)` in Compose
- Page-specific strings should use page prefix (e.g., `setting_page_`)
- If the user does not explicitly request localization, prioritize implementing functionality without considering
  localization. (e.g `Text("Hello world")`)
- For `locale-tui` operations, use the `locale-tui-localization` skill.

## Product Invariants: Persona, Continuity, and Regression Safety

以下规则适用于所有角色相关功能（聊天、电话、主动消息、通知、状态、记忆、工具、游戏、TTS、图像提示等），优先级高于局部文案便利：

- **先读规范再改代码**：每个代码任务开始前必须阅读本文件；若子目录存在更具体的 `AGENTS.md`，同时阅读并遵守。
- **用户人设是最高约束**：角色名、核心人设、关系类型、世界观、语言风格与明确边界由用户定义。运行时状态、关系数值、陪伴目标和场景提示只能在人设内部调节，不能覆盖人设。
- **零默认性格倾向**：不得默认温柔、亲密、恋爱、顺从、朋友式、撒娇、关心或“轻声陪伴”。称呼、情绪、冲突方式、幽默、主动程度和距离感必须来自具体人设及真实关系证据。
- **所有角色出口一致**：任何会以角色名义展示、朗读、通知或写入记忆的内容都必须经过同一套人设与边界约束。技术失败使用系统层中立提示，禁止伪装成角色台词。
- **禁止固定角色兜底**：电话开场、生成失败、游戏回应、主动消息、哄睡、提醒等不得内置看似由角色说出的固定句子。可以使用不冒充角色的系统状态，或在保留人设上下文后重试。
- **陪伴感不等于温柔措辞**：通过连续记忆、稳定立场、真实行动、适时主动、尊重边界、兑现承诺和有证据的共同经历建立活人感。角色可以冷淡、严厉、矛盾、克制或非人，但必须前后一致。
- **事实与想象分离**：只有真实消息、用户资料、已执行工具结果和已记录生活事件可以作为事实。不得声称完成未执行的动作，不得用关系分数虚构亲密历史。用户最新纠正始终覆盖旧推断。

### Change Safety Gate

任何功能改进在提交前必须执行：

1. 先定位现有行为、调用方、持久化路径和失败路径，避免只修 UI 表面。
2. 保持改动聚焦，禁止顺手重写无关模块；保留用户已有行为和数据兼容性。
3. 新增或更新覆盖正常、失败、取消、重试、空数据和旧数据迁移的测试。
4. 对角色功能至少用差异明显的多种人设做回归思考：冷淡/严厉、普通朋友、亲密关系、敌对或非人角色。测试不得只使用“温柔伴侣”样例。
5. 构建或测试不可运行时，必须明确说明未验证项；不能把静态检查写成“测试通过”。
6. 推送前检查最终 diff，排除大面积换行、格式噪声、调试代码、提示词泄漏与固定角色话术。
7. 不得以“改善体验”为由删除、绕过或弱化原本正常的功能；发现回归风险时优先采用可回退的小步实现。

角色相关改动建议检索：`陪伴`、`温柔`、`亲密`、`朋友`、`宝贝`、`主人`、`轻声`、`我在`、`接住`、`照看`。出现这些词不一定错误，但必须能由具体人设或用户证据解释，不能成为系统默认。
