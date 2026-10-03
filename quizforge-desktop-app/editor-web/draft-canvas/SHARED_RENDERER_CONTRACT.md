# Shared Question Renderer Contract v1

本阶段支持 `SINGLE_CHOICE + TEXT` 和 `MULTIPLE_CHOICE + TEXT`，用于正式 Active DRAFT 和 History DRAFT。NORMAL / History RESULT 继续使用原 JavaFX 页面。当前注册表是静态内置映射，没有插件注册、动态加载或 `.qfx`。

## 分层与所有权

```text
Java Surface Host / semantic Bridge
  Shared Runtime (src/shared/runtime/question-runtime.js)
    QuestionRendererRegistry
      SINGLE_CHOICE   → Choice family, SINGLE, radio
      MULTIPLE_CHOICE → Choice family, MULTIPLE, checkbox
  Canvas Core / World geometry / DraftAutosave
```

| 层 | 职责 |
| --- | --- |
| Core `QuestionTypeDefinition` | 题型业务结构、答案约束、判分和题库校验；Practice service 管理状态、Attempts 与事务 |
| Java ViewModel / adapter | 将 Core 的真实 type、答案、结果映射成展示 DTO；提交前不暴露正确答案或解析 |
| Shared Runtime / Bridge | 加载、身份校验、操作顺序、二次确认、提交/重试命令、错误和销毁；协调 autosave ACK barrier |
| Question Renderer | 消费结构化内容 DTO，创建题干和选项 DOM，收集语义答案、应用可交互/只读能力，展示 Core 结果 |
| Canvas Core | 同一 World 中的卡片几何和笔迹、工具、视口；不认识业务题型或判分规则 |

**Renderer ≠ QuestionTypeDefinition。** Renderer 负责共享界面的交互与展示，不能创建 Attempt、计算分数或直接访问持久化。这个边界可为将来的 Extension Runtime 提供基础；本阶段没有实现扩展运行时。

## 定义与实例合同

`src/shared/renderer/contract.js` 定义 capability mode。`registry.js` 通过 `require(questionType)` 返回冻结的内置定义：

```js
{
  id: 'builtin.multiple-choice.v1',
  questionType: 'MULTIPLE_CHOICE',
  selectionMode: 'MULTIPLE',
  label: '多选题',
  parse(question),
  mount(form, question, { mode, contentRoot, canInteract, answerChanged })
}
```

`mount` 返回实例：

| 操作 | 约定 |
| --- | --- |
| `update(question)` | 应用完整的权威题目 DTO，恢复选择和 disabled 状态 |
| `getAnswerIntent()` | 仅返回 `{ selectedOptionIds: [...] }`，不带答案判定或 DOM 信息 |
| `setInteractionMode('INTERACT' / 'DISABLED')` | 提交、确认或宿主禁止交互时禁用控件；Canvas 工具另由 `canInteract()` 守卫 |
| `setReadOnly(boolean)` | History 创建时锁定只读，不能随后升级为 ACTIVE |
| `renderResult()` | 展示宿主供应的 result/score/feedback/analysis，不在 JS 判分 |
| `destroy()` | 移除 answer listener，禁用旧控件；重复销毁安全 |

Runtime 在刷新/替换 renderer 前销毁旧实例；关闭时同时释放根部 submit/click listener。History 使用同一 Choice renderer，只将 mode 设置为 `READ_ONLY_HISTORY`，没有第二套题型实现。

## 静态 registry 与 Choice family

`src/renderer/single-choice/index.js` 和 `multiple-choice/index.js` 各自保留 type/id。两者共享 `choice/contract.js` 和 `choice/renderer.js`；SINGLE 使用 radio 且最多选一项，MULTIPLE 使用 checkbox 且答案为唯一已知 option ID 的集合。

未知 type 抛出 `Unsupported question type`，加载处显示明确错误并移除旧题控件。没有 SINGLE fallback。未知内容 kind 抛出 `Unsupported content`，不会压平成字符串、丢弃节点或转换为图片。Java 正式入口也只为两种受支持 TEXT 题型启用 Draft。

## MULTIPLE ViewModel

沿用 schemaVersion `1.0`；新增 `selectionMode` 为兼容的展示字段。没有该字段的旧 SINGLE DTO 仍可按真实 type 解析，显式矛盾的 mode 会被拒绝。

```json
{
  "schemaVersion": "1.0",
  "session": { "sessionId": "...", "bankAssetId": "qb_...", "bankContentId": "qfb:v2:..." },
  "question": {
    "sessionQuestionId": "...", "questionId": "q_...",
    "type": "MULTIPLE_CHOICE", "selectionMode": "MULTIPLE", "index": 0, "total": 1,
    "prompt": { "kind": "TEXT", "text": "选择所有适用的描述" },
    "options": [
      { "id": "opt_a", "content": { "kind": "TEXT", "text": "A 内容" }, "feedback": "NONE" },
      { "id": "opt_b", "content": { "kind": "TEXT", "text": "B 内容" }, "feedback": "NONE" },
      { "id": "opt_c", "content": { "kind": "TEXT", "text": "C 内容" }, "feedback": "NONE" },
      { "id": "opt_d", "content": { "kind": "TEXT", "text": "D 内容" }, "feedback": "NONE" }
    ],
    "selectedOptionIds": ["opt_a", "opt_c"], "state": "DRAFT", "maxScore": 1, "result": null
  }
}
```

提交前 result 为 null、feedback 为 NONE，不传 correctOptionIds/analysis。提交后 Core 提供真实 status/score/maxScore/Attempt identity 和正确答案/解析。Java 不认识 radio、checkbox 或 DOM selector，只处理 type 和 selectedOptionIds。

## Active / History 与 Canvas

Active 的选择通过 `ANSWER_CHANGED` 回到原 Core draftAnswer；`SUBMIT` 等待末笔捕获及保存 ACK 后，在同一事务冻结 AttemptDraftSnapshot。`RETRY` 清空工作答案及当前画布，旧 Attempt 和快照不变。每题隔离键仍为 sessionQuestionId，冻结键为 attemptId。

History adapter 从选中 Attempt 的冻结题目、答案和结果构造 DTO，从相同 attemptId 读取 DraftSnapshot。不能使用最新题库或 Active Draft 覆盖历史。

History renderer 不订阅 answer change，不显示 Submit/Retry；Runtime 不发送 mutation；只读页面只暴露 ReadyHost，不创建 Practice channel 或 autosave。Canvas READ_ONLY 仅允许平移、缩放、Fit，不允许绘画、擦除或导入，不将查看视口变化保存回数据库。

保留 Draft document `1.0` / layout `1` 以及 World card/ink 共用变换。原 SINGLE DOM 层级与 CSS 保留，checkbox 复用同一选项样式。没有新的草稿格式、表、migration 或 Canvas 实现。

验收证据与 29 项覆盖见 [SHARED_RENDERER_ACCEPTANCE.md](SHARED_RENDERER_ACCEPTANCE.md)。
