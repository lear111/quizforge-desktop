# 题型拓展开发指南

> 2026-10-06 · 当前实现：HTML SDK 2.1、安装包格式 2。本文按实际开发顺序组织；未实现的能力见最后一节。

## 1. 开发范围与阅读路线

一个题型需要两个页面：**编辑页面**与**练习页面**，以及默认题目 JSON、数据 Schema、校验与评分规则。草稿是在练习页外叠加白板；历史复用练习页，读取保存的尝试并禁止写入。无需为草稿、历史再各做一套页面。

应用管理题库、正式保存、评分事务、尝试记录、白板持久化、权限和题目导航。普通新题型应只改自己的拓展目录。需要尚未开放的宿主能力时，先讨论 SDK，不能通过修改应用绕过接口。

建议顺序：

1. 读本文，明确数据、页面与应用的分工。
2. 读 [判断题模板说明](QUESTION_TEMPLATE.md)，再读 `packages/true-false/` 中的注释源码。
3. 按本文第 3～8 节复制模板、替换数据和交互、预览、打包、安装。
4. 需要具体参数时查 [SDK 已实现接口](SDK_README.md)、[公共 UI 与权限](PUBLIC_UI_API.md)、[数据校验](DATA_VALIDATION.md)。

其他说明：[安装与空配置测试](README.md)、[Windows 浏览器边界](WEBVIEW2_BACKEND.md)、[规则运行隔离](RUNTIME_ISOLATION.md)。`docs/题型扩展接口设计.md` 是目标设计，不能把其中的待实现接口当成可调用 API。

## 2. 文件结构与阅读顺序

开发目录可以位于仓库外，例如 `D:/QuizForgeExtensions/my-type/`；仓库内可放在 `extensions/packages/my-type/`。应用不会自动安装或监听这个目录。

```text
my-type/
  manifest.json
  default.json
  question.schema.json
  answer.schema.json
  editor.html
  editor.js
  practice.html
  practice.js
  style.css
  type.js
```

| 文件 | 负责什么 | 阅读或修改重点 |
| --- | --- | --- |
| `manifest.json` | 包身份、题型 ID、版本、入口文件、申请权限 | 决定应用如何找到页面和规则 |
| `default.json` | 新建题目的完整默认 Question | 与 `.qbank` 的 `bank.json → questions[]` 中一条题目同构 |
| `question.schema.json` | 完整题目的结构约束 | 与默认数据、编辑更新同步修改 |
| `answer.schema.json` | 用户作答对象的结构约束 | 不是标准答案 `answerSpec` 的 Schema |
| `editor.html` / `editor.js` | 编辑结构、读取与更新题目、编辑操作 | 使用 `QF.editor`；正式保存使用 `QF.bank.save()` |
| `practice.html` / `practice.js` | 练习结构、保存回答、提交、结果、只读展示 | 依据宿主能力刷新控件，支持恢复答案 |
| `style.css` | 两页视觉、响应式和状态样式 | 页面内样式；题卡初始几何由 `QF.layout` 声明 |
| `type.js` | 校验与同步评分 | 独立规则运行时，无 DOM、无页面输入框 |

HTML 是片段，不需要自己创建 WebView、iframe、消息桥或完整 HTML 文档。不要添加 `<script>`、外链资源或 `onclick`；JS/CSS 由清单引用，事件通过页面 JS 绑定。宿主会注入对应运行时的 `QF`。

## 3. 从判断题复制：先改身份

复制整个 `packages/true-false/` 源码目录，保留它作为参照。建议为新包和新题型使用自己的发布者命名空间，例如：

```text
扩展 ID：example.short-answer
题型 ID：example.short-answer
包版本：1.0.0
数据版本：1
```

包可以声明多个题型，所以扩展 ID 与题型 ID 不要求相同。以下位置必须一致：

| 修改处 | 字段 |
| --- | --- |
| `manifest.json` | 顶层 `id`、`name`、`version`；`types[].id`、`label`、`family`、`dataVersion` |
| `default.json` | `type` |
| `question.schema.json` | 对 `type` 的 `const` |
| `type.js` | `QF.defineQuestionType({type: ...})` |
| 两页 JS / HTML | 题型名称、输入控件、提示与结果展示 |

当前 `packageFormatVersion:2`、`sdkApiMajor:2`；可以设置 `minSdkApiMinor:1` 要求 SDK 2.1。包 `version`、题目 `dataVersion` 和 SDK 版本是不同概念：包版本用于发布，数据版本标识数据契约，SDK 版本声明宿主兼容性。数据版本字段本身不会自动迁移旧数据。

`capabilities` 声明编辑、练习、历史、预览等支持场景；`permissions` 是需要用户批准的操作权限。授权还受当前模式和宿主能力限制，不能互相替代。

只做编辑、作答、提交、重试的页面可申请：

```json
["question.edit", "answer.write", "practice.submit", "practice.retry"]
```

如果调用 `QF.bank.save()`、导航、来源或白板接口，再按 [权限表](PUBLIC_UI_API.md#9-扩展权限) 增加对应权限。判断题模板保留了自定义保存、增删、来源等 UI，因此申请较多权限；简单新题型可以恢复默认公共 UI，并删除不需要的按钮、调用和权限。

## 4. 先定义三种数据

### 4.1 完整题目与默认模板

`default.json` 是完整题目，不能只写页面展示字段。自定义题目使用公共 Question 外壳，私有字段放在 `payload.data` 和 `answerSpec.data` 中。例如普通文本简答题：

```json
{
  "id": "q_template",
  "type": "example.short-answer",
  "stimulusRefs": [],
  "prompt": {"kind": "TEXT", "text": "法国的首都叫什么？"},
  "payload": {
    "kind": "EXTENSION",
    "data": {"placeholder": "请输入你的回答"}
  },
  "answerSpec": {
    "kind": "EXTENSION",
    "data": {"referenceAnswer": "巴黎"}
  },
  "scoreSpec": {"defaultMaxScore": 5},
  "evaluationSpec": null,
  "analysis": {"kind": "TEXT", "text": "法国的首都是巴黎。"},
  "sourceRefs": []
}
```

`prompt` 和 `analysis` 使用宿主内容结构；普通文本为 `TEXT`。富文本应通过 `QF.content.mountEditor/render` 接入内容契约。不要把全部自定义数据随意放在公共顶层字段中。

应用依据题目的 `type` 查找已安装拓展；找不到时保留原数据并提示缺少拓展。新建时应用分配题目 ID，复制时也重新分配身份。判断题沿用已有 `CHOICE` 契约：选项 ID 和正确答案引用会同步替换。新题型应优先使用通用 `EXTENSION` 契约。

### 4.2 用户作答

标准答案属于题目中的 `answerSpec`；用户回答属于单独的作答对象。本例可以设计成：

```json
{"text": "巴黎"}
```

保存的字段由拓展明确提交。应用不会从 DOM、输入框名称或样式推断应该保存什么。`{}` 是宿主保留的未作答状态；读取时用 `answer.text || ''` 等方式处理。其他作答必须符合 `answer.schema.json`。

### 4.3 Schema 与业务校验

使用宿主支持的 Draft-07 Schema。完整题目由 `question.schema.json` 校验，作答由 `answer.schema.json` 校验；空答案、错误处理和支持的关键字见 [数据校验](DATA_VALIDATION.md)。本例作答 Schema：

```json
{
  "type": "object",
  "required": ["text"],
  "additionalProperties": false,
  "properties": {"text": {"type": "string", "maxLength": 10000}}
}
```

复制判断题后必须替换题目 Schema 的固定选项约束、作答 Schema 的 `selectedOptionIds` 和规则中的选项判断。Schema 约束结构；`validate` / `validateAnswer` 增加业务检查。拓展不能取消宿主校验。

## 5. 编辑页：数据绑定与正式保存

1. `QF.editor.getData()` 读取完整题目。
2. 用输入控件的 `value` 设置初始值。
3. 在输入事件中用 `QF.editor.update(patch)` 更新题目。
4. 检查返回的 `ok`；失败提示错误，应用保留上次接受的数据。
5. 正式保存调用 `QF.bank.save()`，或者保留默认保存按钮。

```js
const input = QF.dom.$('[data-prompt]');
const reply = await QF.editor.getData();
if (!reply.ok) throw new Error(reply.error.message);
input.value = reply.data.prompt.text;
QF.dom.on(input, 'input', async () => {
  const saved = await QF.editor.update({prompt: {kind: 'TEXT', text: input.value}});
  if (!saved.ok) QF.ui.notify(saved.error.message);
});
```

`update` 是顶层合并：更新 `payload` 或 `answerSpec` 时，应提交需要保留的完整子对象，避免丢失其他字段。不能更改当前题目 `id`、`type`。

`QF.editor.save()` 同步编辑草稿，不等于写入 `.qbank`。`QF.bank.save()` 等待输入更新完成并保存整个题库。富文本编辑器的 `onChange` 也必须调用相应更新接口，挂载编辑器本身不代表保存。

保存、复制、删除等异步操作应暂时禁用按钮。刷新 UI 前记录输入值；不要因重新渲染让当前输入、光标或中文输入法组合文本丢失。

现有三份 2.1.1 模板在发送 patch 前先更新本地题目，再用递增版本检查回执；旧回执不会覆盖较新的编辑。单选/多选设置正确答案只更新选中样式，不重建文本输入框。接口失败用 notify 显示，并读取宿主保留的数据；不要把业务失败 Reply 抛成脚本异常。分值为空、非有限数或不大于零时不写入，上次有效分值保留。控件禁用状态应同时检查当前能力、操作中状态与对应权限。

## 6. 练习页：作答、提交、结果与只读

练习页的基本流程：

```text
practice.getQuestion() → 填入题干
answer.get()          → 恢复回答
answer.update(answer) → 保存正在填写的回答
practice.submit()     → 应用校验、评分、保存尝试和草稿快照
practice.getResult()  → 展示权威得分与参考答案
practice.retry()      → 保留旧记录，开始空白作答与草稿
```

`answer.update()` 保存当前作答草稿，不会生成已提交的尝试记录；新记录在提交成功时创建。无需单独的 `saveAttempt()`。重试不是直接评分，重试后的再次提交才产生新记录。

提交前 `getQuestion()` 不包含正确答案和解析；提交后从 `getResult().reference` 读取。不要把标准答案写死在 HTML、页面脚本中，或在页面里计算一个分数冒充提交结果。

使用 `QF.host.subscribe(refresh)` 订阅变化，重新读取 `answer`、`context`、`result`。页面以宿主返回的数据为准，避免用本地变量模拟提交状态。文本输入题需要协调尚未完成的答案写入与异步刷新，避免旧读取覆盖新输入；不要简单地在每次订阅中重建整个回答框。现有三份模板用刷新版本丢弃旧读取，并在答案写入尚未完成时保留正在操作的选择；最后一次写入完成后读取权威状态。权限撤销或转为只读会取消正在显示的提交确认。

| 场景 | 页面要求 |
| --- | --- |
| 正在作答 | 按 `context.capabilities.editAnswer` 启用输入 |
| 已提交 | 保留用户选择或回答，展示评分和参考内容；按能力显示重试 |
| 重试 | 读取清空后的答案，恢复作答；不覆盖旧尝试 |
| 历史 / 查看旧尝试 | 同一练习页，显示保存的回答与结果；禁止作答、提交、重试 |
| 网页预览 | 只读展示，不能依赖桌面导航、题库和白板能力 |

UI 的隐藏/禁用是展示行为，宿主仍执行权限检查。不要只根据 `mode === 'PRACTICE'` 判断可写，应查看 `capabilities`。

## 7. 规则：校验与同步评分

`type.js` 使用独立规则运行时的 `QF.defineQuestionType`，没有页面版 `QF.editor/answer/dom`。最小逻辑示例：

```js
QF.defineQuestionType({
  type: 'example.short-answer',
  validate(question) {
    return question.answerSpec.data.referenceAnswer.trim() ? [] : ['请填写参考答案'];
  },
  validateAnswer(question, answer) {
    const valid = typeof answer.text === 'string';
    return {errors: valid ? [] : ['回答必须是文本'], empty: valid && !answer.text.trim()};
  },
  grade(ctx) {
    const right = ctx.answer.text.trim() === ctx.question.answerSpec.data.referenceAnswer.trim();
    return ctx.reportResult({score: right ? ctx.maxScore : 0});
  }
});
```

这是“去掉首尾空格后完全匹配”的教学评分，不是语义评分。评分读取应用提供的冻结题目、答案和 `maxScore`；每次只能报告一次，分数必须在 0～满分内，`null` 表示未评分。报告只暂存结果，应用校验和事务成功后才保存尝试。不能返回 Promise。

默认模板创建、复制、快照和普通单题大纲由适配层提供。复合题可使用可选 `targets(question)` 声明子题，具体返回结构见数据校验文档。通常无需自己写生命周期或通用适配器。

## 8. UI、布局与白板

公共 UI 默认显示。可以先保留保存、来源、提交、重试等默认控件，只制作自己的题目字段与回答区。判断题模板演示了隐藏默认控件后自行绑定接口的完整做法。

```js
QF.ui.configure({submit: false, retry: false, confirmation: false});
QF.dom.on(QF.dom.$('[data-submit]'), 'click', () => QF.practice.submit());
QF.dom.on(QF.dom.$('[data-retry]'), 'click', () => QF.practice.retry());
```

使用 `confirmation:false` 时，自行决定是否绘制确认步骤；提交事务仍由宿主执行。只隐藏自己已经替换的默认 UI，避免出现两套按钮。上一题/下一题强制保留，工作区、标签栏和整轮统计页仍由应用管理。

`QF.layout.configure` 配置 `cardWidth`、`maxCardWidth`、`horizontalAlign`、`verticalAlign`、`padding`。默认模板编辑卡顶部排列，练习卡居中；已保存的草稿位置和尺寸优先恢复。详细取值见公共 UI 文档。

白板默认保留右上角入口、悬浮工具和缩放，页面不用自己保存笔迹。自定义白板工具区使用 `QF.ui.mountControls(node)` 标记，避免被画笔/拖动拦截；普通答题区不要用这个标记绕过白板交互。历史可以查看和缩放，不允许修改保存的草稿。

## 9. 预览、安装、热更新与发布

### 从源码预览

“题型扩展 → 独立开发预览…”选择新题型源码。它使用临时测试上下文；规则、Schema、默认数据变化可在这里检查。没有正式题库命令，不写正式作答，试答使用开发窗口的测试控件。

### 打包

在仓库根目录：

```powershell
node extensions/tools/pack.mjs D:/QuizForgeExtensions/my-type D:/QuizForgeExtensions/example.short-answer-1.0.0.qfext
```

可把 `pack.mjs` 单独复制到开发目录使用，只需 Node，不要求 Maven/npm 项目。输出必须位于源码目录外。打包成功不等于安装校验通过，宿主仍验证 Schema、模板及权限。

### 正式安装与主浏览区热更新

1. “题型扩展 → 安装扩展”选择 `.qfext`，检查身份、版本和权限并确认。
2. 重启后新增菜单提供该题型。新建测试题库，编辑、保存、提交和重试。
3. “加载开发目录…”连接同 ID、同版本、清单一致的源码。HTML、页面 JS、CSS 保存后实时刷新，保留已有作答与白板。
4. “停止实时预览”恢复安装版本。开发目录不会改写已安装包。

| 修改内容 | 生效方式 |
| --- | --- |
| 两页 HTML、页面 JS、CSS | 显式连接开发目录后主浏览区热更新 |
| `type.js`、Schema、默认 JSON、清单或权限 | 独立开发预览测试；正式使用需升级版本、重新打包安装并重启 |
| 应用公共外壳或 SDK | 属于应用开发，需要对应构建和重启，不能当作题型页面热更新 |

同一扩展 ID、同一包版本不能覆盖不同内容。**仅添加代码注释也会改变包内容和 SHA-256**；若重新发布，也要使用新包版本。新版本重新确认权限，旧历史使用冻结的版本与数据。不要直接覆盖安装目录或删除历史依赖包。

## 10. 最小检查清单

- [ ] 身份在清单、默认数据、Schema、规则中一致。
- [ ] 默认 Question、修改后的 Question、作答都符合 Schema。
- [ ] 新建和复制不依赖写死的题目/选项 ID。
- [ ] 输入能保存；切题、切换模式、重新打开后能恢复。
- [ ] 未作答不能提交；正确、错误或部分得分与规则一致。
- [ ] 提交后保留回答，重试开始新作答，旧记录可查看。
- [ ] 历史和预览无法写入，参考答案只在允许时显示。
- [ ] 草稿切换、画笔/拖动和题目交互遵循宿主能力。
- [ ] 权限不足或接口失败有提示，失败不伪装成保存成功。
- [ ] 正式安装、授权、重启后在测试题库里完成一轮流程。

不要为新题型跑整个应用全量测试；先验证该拓展的数据、规则与真实页面，遇到具体风险再扩大检查范围。

## 11. 当前未实现或未开放的能力

- 全局 AI 接口与异步评分：不可自行发网络请求或写模型凭据绕过应用配置。
- 任意 `feedback` 的桌面历史持久化：目前不能承诺模型评价会保存和回放。
- `QF.attempts.*`：尚未开放。应用内部提供尝试切换，拓展不能直接调用该命名空间。
- 单独的 `saveAttempt()`：未提供；提交成功由应用统一创建记录。
- 数据版本自动迁移、完整跨平台实现、Hub 上传/账户服务：不能由现有示例推定已经完成。

拓展页面处于隔离 iframe 中，经 SDK 调用白名单能力。不能读取主程序 DOM、Java 对象、题库文件或全局配置，也不能直接导航、下载或弹窗。具体沙箱和平台边界以运行隔离文档为准。

## 12. 新对话启动提示词

复制下面内容，替换题型需求即可。新对话先核对代码和文档，不把前一段对话当成已实施的功能。

```text
我们继续开发 QuizForge 外部题型拓展。
仓库：C:\Users\wangg\OneDrive\Desktop\QuizForge\quizforge_V2

先读取：
1. extensions/DEVELOPMENT_GUIDE.md
2. extensions/SDK_README.md
3. extensions/QUESTION_TEMPLATE.md
4. extensions/packages/true-false/ 的带注释源码
需要参数时再查 PUBLIC_UI_API.md 和 DATA_VALIDATION.md。

先检查 git status，保留已有改动。当前应用没有内置题型；以判断题作为视觉和接口流程模板。
请只在新的拓展目录开发，不改应用主体、不自动安装授权、不改正式题库、不提交或 push。
先明确题目 JSON、作答 JSON、Schema、编辑/练习交互和评分规则，再实现。
草稿和历史复用练习页；历史只读。提交和记录保存由宿主管理，不能自己写数据库。
AI、异步评分和 QF.attempts 接口尚未实现；需要这些能力先说明，不要伪造 API。
使用现有 pack.mjs 打包到源码目录外，先做定向检查，再按独立开发预览和正式导入流程验证。

本次新题型需求：
- 发布者与题型 ID：待填写
- 开发目录：待填写
- 题干格式：待填写
- 编辑字段与作答方式：待填写
- 参考答案/解析格式：待填写
- 评分规则与分值：待填写
- 布局与交互要求：待填写
```
