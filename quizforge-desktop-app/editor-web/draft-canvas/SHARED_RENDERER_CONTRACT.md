# Shared Question Renderer Contract v1 / Coverage v1

> 2026-10-04 迁移状态：当前只启用新版 HTML SDK 2 单选/多选，旧题型专项实现已删除。本文公共白板、状态、事务和历史契约继续适用；七题型覆盖描述属于此前阶段。当前开发入口与 API 以仓库 extensions/SDK_README.md 为准。

七种正式题型在正式 PRACTICE / DRAFT 中使用同一个 Shared Learning Surface；History DRAFT 复用 Renderer 和只读 Canvas，History RESULT 保留现有页面。旧 JavaFX Practice 是显式 transitional legacy。见 [Shared Learning Surface v1](SHARED_LEARNING_SURFACE.md)。内容支持 TEXT、RICH 和 DOCUMENT；未知内容或缺失资源明确显示 `Unsupported content`。

作文 PRACTICE / DRAFT 共用同一多行 TEXT 输入与 Core 正式答案；原 JavaFX 富文本输入入口留在 legacy 代码中。已有 DOCUMENT 答案在提交后及历史中按原结构显示；未修改文本时保存保留原文档。草稿笔迹不作为作文答案。

## 分层

```text
Core QuestionTypeDefinition / PracticeSessionService
    ↓ authoritative ViewModel / semantic Bridge
Shared Runtime
    ↓ QuestionRendererRegistry
    ├── SINGLE_CHOICE
    ├── MULTIPLE_CHOICE
    ├── READING
    ├── CLOZE
    ├── MATCHING
    ├── TRANSLATION
    └── ESSAY
Draft Canvas / World / DraftAutosave
History Replay / immutable AttemptDraftSnapshot
```

| 层 | 职责 |
| --- | --- |
| Core QuestionTypeDefinition | 数据、题库校验、答案约束和业务判分 |
| PracticeSessionService | 权威答案、状态转换、Attempt、SQLite 事务 |
| Shared ViewModel | 公共 shell + sealed、题型独立 presentation；Active / History 共用 frozen-content projection |
| Shared Question Renderer | 跨平台展示、语义 answer intent、只读结果、小题 focus target |
| Draft Canvas | 与题型无关的草稿纸、World 坐标、固定逻辑宽度、笔迹、平移、缩放 |
| History Replay | 选中 Attempt 的题目/答案/结果 + 对应 Frozen DraftSnapshot 的只读投影 |

注册表是静态内置映射。没有动态加载或外部注册。

## 公共 shell 与题型 presentation

保持 `schemaVersion: 1.0`。公共 shell 包含 sessionId、bankAssetId/contentId、sessionQuestionId、questionId、type、index/total、TEXT prompt、state、maxScore、result。旧 Choice DTO 和 selectionMode 兼容。复合题、排序和文本题的结构放在各自 presentation 中，不把所有类型的数据塞进万能 payload。

| type | family | presentation / answer intent |
| --- | --- | --- |
| SINGLE_CHOICE | SINGLE | options / `{selectedOptionIds}`；一个 radio |
| MULTIPLE_CHOICE | MULTIPLE | options / `{selectedOptionIds}`；多个 checkbox |
| READING | COMPOSITE_SINGLE | passage + items(id, number, prompt, options)；每个 item 独立单选，父答案仍是 option ID 集合 |
| CLOZE | COMPOSITE_SINGLE | passage + blank children；正文 `{{n}}` 显示内联选择框，重复标签同步同一个 blank ID |
| MATCHING | ASSIGNMENT | slots(id, number, locked, givenOptionId, selectedOptionId, feedback) + assignments；intent `{assignments: {blankId: optionId}}` |
| TRANSLATION | TEXT_FIELDS | items(id, number, sentence, TEXT answer, submitted reference)；intent `{textAnswers: {itemId: text}}` |
| ESSAY | LONG_TEXT | TEXT formal answer + submitted reference；intent `{essayText}`，与草稿纸 JSON 无关 |

显示编号只用于展示/查找 stable target，不用于业务身份。Matching 的三处固定提示由 Core 提供；普通选项可重复，固定提示字母不可再选，宿主 Core 仍执行最终校验。JS 不判分。

提交前 result=null，feedback=NONE；Reading/Cloze 不发送正确答案，Matching 只发送固定提示，Translation/Essay 不发送参考答案或解析。提交后显示 Core 原有结果；Translation/Essay `UNSCORED` 保留 null score，不变成 0 分。maxScore 也保留原有 nullable 语义。

## Renderer 实例合同

`parse(question)` 校验受支持的展示内容；`mount(form, question, context)` 创建实例。

| 方法 | 约定 |
| --- | --- |
| update(question) | 应用 Core 权威状态；不能重新判分 |
| getAnswerIntent() | 返回题型所属的语义答案，不带 DOM、显示编号或评分 |
| hasAnswer() | 有正式答案时允许打开提交确认；不要求全部做完 |
| setInteractionMode(INTERACT / DISABLED) | 控制交互，绘图工具仍由 canInteract 守卫 |
| setReadOnly(boolean) | History 创建时锁定只读，不能升级为 Active |
| renderResult() | 只展示宿主供应的 result/score/feedback/analysis/reference |
| focusTarget(stableId) | 定位父题卡内部的 target；找不到返回 null，无状态转换 |
| flushAnswer()（文本家族） | 把尚未 debounce 的文本送给共享队列，等待 Core ACK |
| pendingAnswerIntent()（文本家族） | 宿主同步 close-save guard 捕获未发送的文本 |
| destroy() | 清理监听、文本定时器、禁用旧控件，重复销毁安全 |

Runtime 对外提供通用 `focusTarget(targetId)`。Java 大纲将小题编号解析为 stable ID；同父题只平移视口，不 unload/reload Runtime，不换 sessionQuestionId 或 Draft。History 也使用同一接口，查看视口变化不写回冻结快照。

## 身份、持久化和提交 barrier

```text
Question Card = PracticeSessionQuestion = Active Draft Canvas
Submit = QuestionAttempt = Frozen DraftSnapshot
```

ReadingItem / ClozeBlank / MatchingSlot / TranslationItem 不生成子级 Draft 或 Attempt。一张题卡允许自然增高，Canvas 继续固定 logical width 并使用既有 World 变换。

`ANSWER_CHANGED` / `DRAFT_CHANGED` / `SUBMIT` / `RETRY` 继续经过同一个带 operationSeq 和父题身份校验的 FIFO 队列。Canvas autosave 保持 500ms debounce。文本输入使用 300ms debounce，提交/离开先 flush 文本并等待 ACK，再捕获末笔、flush Canvas 并等待 ACK，最后调用 Core Submit。宿主同步关闭 guard 也保存尚未 debounce 的正式 TEXT 答案。

Retry 使用现有 Core 命令：清正式工作答案与 Active Canvas，旧 Attempt 和 Frozen Snapshot 保持不变。Core 已有 REVISING 状态/REVISION Attempt 可被合同投影；本轮不发明新的 Revision 入口或工作流。

## History / 兼容

History 只读取选中 attemptId 对应的 frozen question、answer、result 和 DraftSnapshot；不读取当前题库来覆盖它。七种题型共用 Active renderer，mode=`READ_ONLY_HISTORY`。没有 answer listener、Submit/Retry、Practice channel 或 autosave；Canvas 只允许 Pan/Zoom/Fit/target focus。

旧 Attempt 没有 DraftSnapshot 时保留 RESULT，不制造空草稿。RICH 和 DOCUMENT 显示使用快照内的结构及资源字节；已有 Canvas document answer 可只读回放。旧快照缺失资源字节时明确提示不可用，不读取当前题库补造历史内容。RESULT 保留原有页面。

富文本 DTO 保留 `kind`、`text`，额外提供 `document`；RICH 可提供 `images`（资源 ID 到自有 data URI）。DOCUMENT 的 `document` 为 Canvas 原生 JSON；RICH 为原有 blocks 结构。`choicePresentation` 与其他题型的 `*Presentation` 同样冻结显示资源；不参与逻辑题目相等性比较。SchemaVersion 仍为 1.0，TEXT DTO 兼容。

实现和验证证据见 [SHARED_RENDERER_COVERAGE_ACCEPTANCE.md](SHARED_RENDERER_COVERAGE_ACCEPTANCE.md)。上一阶段 Choice 验收保留在 [SHARED_RENDERER_ACCEPTANCE.md](SHARED_RENDERER_ACCEPTANCE.md)。
