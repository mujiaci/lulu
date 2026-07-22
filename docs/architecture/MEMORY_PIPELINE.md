# 记忆流水线

> 本文件描述 `master` 当前真实实现与尚未完成的迁移。产品约束以 `COMPANION_REBUILD_PLAN.md` 为准。

## 当前调用链

| 阶段 | 入口 | 当前行为 |
|---|---|---|
| 自动触发 | `ChatService` 普通聊天和通话完成路径 | 回复落库后异步尝试提取；批次大小读取当前角色的 `memoryExtractionInterval` |
| 手动整理 | `ChatService.reorganizeAffectiveMemories` | 可整理最近批、继续历史或完整重建；逐会话处理并显示进度 |
| 批次规划 | `AffectiveMemoryExtractionPlanner.kt` | 先保留最新10条，再按用户配置值切连续完整窗口；历史空洞重建整个标准窗口 |
| checkpoint 读取 | `MemoryBankService.getProcessedSourceNodeIds` | 普通提取以当前选中路径上成功批次为准；不同分支不会共用完成状态，旧 checkpoint 仅保留兼容用途 |
| 确定性提取 | `buildDeterministicMemoryCandidates` | 只处理明确偏好、边界和纠正；它可以先保存，但不能替语义提取宣告批次成功 |
| 语义提取 | `AffectiveMemoryExtractor` | 模型输出 JSON；解析后校验类型、来源证据、用户信号和耐久性 |
| 格式修复与重试 | `parseExtractionResult`、`retryTransientMemoryExtraction` | JSON 仅执行一次去不可见字符/尾逗号的保守修复；网络、连接、限流和服务暂不可用最多累计尝试三次 |
| 三态判定 | `classifySemanticMemoryExtraction` | `SUCCESS_WITH_MEMORIES`、`SUCCESS_EMPTY`、`FAILED_RETRYABLE` |
| 保存 | `MemoryBankService.saveExtractedMemories` | 标准化、按内容去重、写入来源节点和事件/提取时间 |
| checkpoint 推进 | `MemoryBankService.markExtractionProcessed` | 只在语义成功有记忆或模型明确返回空列表时推进 |
| 批次生命周期 | `memory_extraction_batch`、`MemoryBankService.begin/complete/fail/invalidateExtractionBatch` | 持久化处理范围、分支、状态、尝试次数、脱敏错误类别、模型、版本、生成记忆 ID 和完成时间；第三次失败进入人工检查 |
| 后处理 | `MemoryBankService` 与 `CompanionRuntime` | 纠正旧记忆、更新私人印象/关系事件、生成向量并执行维护 |

## 连续批次规则

设用户配置批次大小为 `B`，保护区为 `T`（当前 `T=10`）：

1. 将当前分支中有效的用户/角色消息映射为逻辑消息。
2. 去掉最新 `T` 条。
3. 从第一条起按 `B` 切分，只保留长度恰好为 `B` 的窗口。
4. checkpoint 只决定哪个完整窗口待处理，不能把两个窗口的残片拼接。
5. 旧 checkpoint 在窗口内部有空洞时，重建整个窗口。

例如 `B=20` 时，正式窗口是 `1～20`、`21～40`、`41～60`；只有用户选择20时才使用这些边界。

## 分支身份与失效

- 分支指纹只计算到当前批次终点，因此继续追加新消息不会让已经完成的旧批次失去身份。
- 同一节点范围切换到另一条候选消息时会形成独立批次身份，不会继承旧分支的成功状态。
- 编辑或切换选中消息只失效覆盖首个变化位置的相关批次。
- 删除整个节点会改变后续序号和窗口边界，因此从首个变化位置向后失效。
- 失效批次写为 `INVALIDATED`，其生成记忆及旧版仅带来源节点的相关记忆会被标记退役；checkpoint 同步移除对应来源。
- 手动完整重建会先清除 checkpoint、失效旧批次并退役旧生成记忆，再重新提取。


## checkpoint 安全规则

| 语义模型结果 | 确定性结果 | 保存 | 推进 |
|---|---|---|---|
| 成功且至少一条通过校验 | 任意 | 保存通过校验的候选 | 是 |
| 成功且明确返回空列表 | 无 | 不保存 | 是 |
| 调用/解析失败 | 有或无 | 可保存确定性候选 | 否 |
| 返回非空但全部未通过校验 | 有或无 | 可保存确定性候选 | 否 |

最后一行很重要：本地规则抓到一条偏好，不能掩盖语义模型对情绪、关系和共同经历的失败。

## 时间语义

当前记忆区分：

- `sourceMessageAt`：证据消息时间；
- `occurredAt`：事件实际发生时间，无法确定时回退到来源消息时间；
- `extractedAt`：本次提取或重建时间；
- `createdAt`：旧查询兼容字段，当前跟随事件发生时间。

模型提示中的“今天、昨天、三天前”必须相对于来源消息时间解释，不能相对于重新整理的时间解释。

## 当前可见性

`MemoryReorganizationProgress` 已显示：

- 当前模式与角色；
- 会话总数和当前位置；
- 已完成/失败批次数；
- 当前批起止时间；
- 修复的旧时间行数；
- 当前提示信息。

## 尚未完成

下列内容仍属于后续 P1，不能误认为已经实现：

- 记忆管理页读取批次表并展示 App 重启后仍存在的失败重试队列；
- 角色级保护区设置；
- 记忆页的失败/空结果/失效批次列表和手动重试；
- 派生日总结与原子记忆互斥召回。

## 回归入口

当前相关单元测试：

- `AffectiveMemoryExtractionPlannerTest`：用户配置批次、保护区、连续窗口、历史空洞、稳定分支身份与首个变化位置；
- `AffectiveMemoryExtractorTest`：解析、耐久性校验、确定性规则与三态 checkpoint 判定；
- `MemoryBankServiceExtractionTest`：保存、分支级成功状态、批次失效、旧记忆退役、checkpoint 合并、召回与时间修复。

完整验证仍以 GitHub Actions 的单元测试与 `assembleRelease` 为准。
