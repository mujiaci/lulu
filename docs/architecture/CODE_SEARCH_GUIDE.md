# 角色系统代码搜索指引

> 本文件只记录已经由仓库内容确认的入口。发现新调用点后增量补充，禁止凭文件名猜测架构。

## 当前已确认入口

| 领域 | 文件 | 当前职责/已确认结构 |
|---|---|---|
| 角色模型与世界书 | `app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt` | `Assistant.memoryExtractionInterval` 为角色级用户设置（新角色默认 20，0 关闭自动提取）；`lorebookIds`；`Lorebook.globalApply`；`RegexInjection.constantActive`；触发匹配函数 |
| 会话与分支 | `app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt` | `Conversation.messageNodes`、当前选择分支、`MessageNode.selectIndex` 与消息时间 |
| 记忆服务 | `app/src/main/java/me/rerere/rikkahub/data/service/MemoryBankService.kt` | 记忆搜索、保存、向量化、维护、纠正、归档、checkpoint 相关逻辑 |
| 记忆触发与执行 | `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt` | 普通聊天/通话后的自动提取、历史重整、模型调用、三态结果判定和 checkpoint 推进 |
| 每日定时任务 | `app/src/main/java/me/rerere/rikkahub/data/service/DailySummaryService.kt` | `daily_cron` 调度与插件事件；注意它不等同于记忆系统的派生 daily summary |
| 上下文/token 调试 | `GenerationHandler.kt`、`AILogging.kt`、`DeveloperPage.kt` | 按场景记录分层 token、模型与耗时；仅保留最近32次脱敏内存记录，不保存正文或完整提示词 |
| 记忆批次 UI | `MemoryBankVM.kt`、`MemoryBankPage.kt` | 展示分支级成功点、稳定区、剩余消息、持久失败/空结果/失效批次与手动重试 |
| 主动消息 | `app/src/main/java/me/rerere/rikkahub/data/service/ProactiveMessageService.kt` | 主动判断、生成和发送的主要服务；下一阶段需拆出实际调用链 |

## 下一轮必须定位的调用链

### 记忆

检索并确认：

- `MemoryExtractionCheckpointEntity`
- `MemoryBankDAO` 中 checkpoint、批次与记忆查询
- 触发自动提取的调用方
- `AffectiveMemoryExtractionPlanner.kt`：按角色配置切连续标准批次；保护区当前默认 10
- 提取模型调用、JSON 解析与验证
- checkpoint 推进条件
- 消息编辑、删除、切换分支后的失效路径
- 记忆管理页面及其 ViewModel
- daily/monthly archive 的默认召回入口

### 世界书与上下文

检索并确认：

- `globalApply` 的所有读取方
- `lorebookIds`、`getTriggeredInjections`、`extractContextForMatching`
- 普通聊天的 prompt/context 装配入口
- 主动消息是否另行装配上下文
- 通话、日记、游戏、状态和关系判断是否绕过普通聊天入口
- token usage 的现有数据结构与 UI 展示入口

### 主动代理

检索并确认：

- `ProactiveMessageService` 的调度入口
- Worker、Alarm、Service、通知与前台启动路径
- 意图模型为空时的本地兜底
- 主动消息发送后的打开/回复/拒绝反馈
- 用户活动、用户回复、普通角色回复和主动消息的时间戳写入点
- 主动来电与主动文字消息的共享/分离逻辑

### 关系、承诺与状态

检索并确认：

- `data/companion` 下所有模型与存储入口
- `CompanionCommitmentStatus`
- `CompanionPrivateImpression`
- 神经递质字段的声明、持久化、提示词和决策读取点
- 关系页/辞海页的 ViewModel 与数据库来源
- 承诺抽取、拆分、更新、暂停、取消和历史记录
- 未解决关注和生活锚点是否已有相近结构

## 修改前检查清单

1. 阅读根 `AGENTS.md`，并查找目标子目录内更具体的 `AGENTS.md`。
2. 画出页面/调度入口 → ViewModel/Service → Repository/DAO → Entity → 模型调用 → 失败路径。
3. 先补能复现当前错误的测试，再修改实现。
4. 检查旧数据兼容、分支消息、编辑/删除、取消、重试和空结果。
5. 检查所有角色出口，避免只修普通聊天而漏掉主动消息或通话。
6. 最终 diff 排除格式噪声、调试私密内容和固定角色话术。

## 构建与回归命令

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest
```

设备测试依赖模拟器/真机；构建还可能依赖 `app/google-services.json`。无法运行时应记录具体原因，不得标记为通过。
