# 题型扩展 SDK 2：两个页面和默认 JSON

> 2026-10-06 当前实现。已接入单选、多选、判断题、主浏览区页面热更新和共享 HTML 题库编辑外壳；旧 SDK 1 包不再加载。全局 AI 接口、异步评分和模型评价持久化仍待实现。

本文作为接口与运行边界参考。第一次开发先按 [拓展开发指南](DEVELOPMENT_GUIDE.md) 操作；以 [带注释判断题模板](QUESTION_TEMPLATE.md) 为起点。新对话可使用开发指南最后一节的提示词。

规则由低权限工作进程执行。Windows 编辑、练习、草稿、历史页面通过 WebView2 隔离 iframe 直接渲染；其他平台暂保留原浏览器后端。HTML/CSS/JS 和 QF 接口写法保持不变，白板与数据写入由主应用管理。应用不预装题型，也不自动连接源码目录；完整开发、导入与空配置测试见 [流程说明](README.md)。后端边界见 [WebView2](WEBVIEW2_BACKEND.md)，规则故障与恢复见 [运行故障隔离](RUNTIME_ISOLATION.md)。

## 1. 从可运行题型开始

复制 `packages/true-false/`（通用风格模板）、`packages/single-choice/` 或 `packages/multiple-choice/` 到自己的目录。判断题的页面结构与视觉约定见 [QUESTION_TEMPLATE.md](QUESTION_TEMPLATE.md)。每份扩展包含：

```text
my-type/
  manifest.json
  default.json
  editor.html
  editor.js
  practice.html
  practice.js
  type.js
  question.schema.json
  answer.schema.json
  style.css
```

HTML 写页面结构；页面脚本写数据绑定和点击事件；`default.json` 写默认展示数据。模板是 `.qbank` 的 `bank.json → questions[]` 中一条完整 Question，字段和保存结构一致。创建或复制题目时宿主分配新题目和选项 ID，并改写对应标准答案引用。

当前单选、多选使用公共功能接口：隐藏对应默认 UI，在自己的 HTML 中提供保存、新增、复制、删除确认、来源、提交确认和重试。草稿默认保留原来的右上角入口与悬浮白板工具，拓展可以通过 `draftToggle`、`draftToolbar`、`draftZoom` 决定是否显示。上一题/下一题保留宿主控件。实现位置与接口对应关系见 [选择题接口示例](packages/README.md)。

单选、多选沿用 `CHOICE` payload/answerSpec；其他外部数据使用 `{"kind":"EXTENSION","data":{...}}`。不要随意改动既有稳定题型 ID。自定义扩展及题型应采用自己的发布者命名空间。

## 2. 清单与打包

清单 `packageFormatVersion` 与 `sdkApiMajor` 都为 2。每个 `types[]` 项声明 `id`、`label`、`family`、`dataVersion`、`capabilities`、`permissions`，以及 `defaultQuestion`、`editor`、`editorScript`、`renderer`、`rendererScript`、`rules`、`questionSchema`、`answerSchema` 和 `styles` 的本地路径。`capabilities` 表示支持哪些界面，`permissions` 声明要调用的受保护功能，不能相互替代。

当前宿主 SDK 为 **2.1**，扩展包自身的 `version` 与 SDK 版本独立。顶层可用 `minSdkApiMinor` 声明最低 SDK 次版本，例如 `"sdkApiMajor":2,"minSdkApiMinor":1` 要求 SDK 2.1 或更高的兼容 2.x 宿主。省略时为 0，已有 SDK 2 包继续可用。主版本和包格式必须一致；未来主版本、包格式或过高次版本在安装、开发目录和网页加载时均拒绝执行，并提示更新应用；旧主版本提示更新扩展。次版本仅添加兼容能力，不改变已有接口含义；破坏性变更应升级主版本。

例如只需编辑、作答、提交和重试的题型可以声明：

```json
"permissions": ["question.edit", "answer.write", "practice.submit", "practice.retry"]
```

遗漏或空数组默认拒绝所有受保护调用；未知权限拒绝打包和安装。完整权限及接口映射见 [权限表](PUBLIC_UI_API.md#9-扩展权限)。安装界面先校验包，再让用户确认或取消各项权限。授权由宿主保存，绑定扩展 ID、版本和包 SHA-256；新版本重新确认，包内声明无法自行授权。应用不随附题型包，也不自动授权；所有题型均通过相同的导入与权限确认流程。

两个 HTML 入口是片段，不含 script、iframe、外链资源或 onclick；事件放入声明的 JS。宿主默认提供公共题卡外壳、提交确认、重试和白板工具；扩展可配置显示哪些公共 UI。HTML 页面通过 `data-qf-actions` 容器放置默认按钮，也可以通过 `QF.ui.mountActions(node)` 标记此容器。

```powershell
node extensions/tools/pack.mjs ./my-type ./publisher.my-type-1.0.0.qfext
```

输出必须在源码目录外。工具验证本地资源和路径，拒绝符号链接，生成确定性 ZIP 及 SHA-256；打包只需要 Node，无需 Maven 或 npm 依赖。开发者可复制 `pack.mjs` 独立使用。

应用“题型扩展 → 加载开发目录”将相同 ID、版本和清单的已安装题型源码连接到主浏览区。应用不会自动连接任何源码目录，必须由用户显式选择。保存 HTML、页面 JS、CSS 后，当前编辑、练习、草稿、历史与只读预览页面自动刷新；保留当前作答、编辑草稿和白板内容，更新不改写安装包。可通过“停止实时预览”恢复安装版本。

主浏览区的评分规则、Schema 和默认模板继续使用安装版本，以保留在途练习与历史的版本约束。开发新题型或修改这些逻辑时，使用“独立开发预览…”：它提供编辑、试答、结果、历史及只读预览；规则变更重置其测试作答，测试数据不写入正式题库。正式通过“安装扩展”导入新版本 `.qfext`，重启生效。

主浏览区热更新沿用安装包的授权，要求清单完全一致，修改权限需安装新版本。独立开发预览只在其临时测试上下文允许清单声明的功能，不开放正式题库操作，不写入正式授权记录。

已安装扩展的每个版本在“题型扩展”列表中提供“权限…”入口，显示当前授权，可逐项撤销/恢复，也可取消全部或恢复全部声明权限。保存后直接更新正在打开的编辑、练习和历史页面的宿主权限，不重建 iframe，不清空作答和草稿。旧请求如果已开始执行，可能继续完成；排队但尚未执行的受保护请求会按新权限判定。权限保存后源码热更新仍沿用新授权，不会重新授予已撤销项。新版本安装仍在重启后启用。

## 3. 已实现的页面接口

异步接口返回 `{ok:true,data:...}` 或 `{ok:false,error:{code,message,retryable:false}}`。页面不直接操作题库文件、数据库、全局模型配置或练习轮次。

| 接口                                                                                            | 作用                                                                                                                           |
| ----------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `QF.dom.root` / `QF.dom.$(selector)`                                                        | 当前扩展页面节点及查询                                                                                                         |
| `QF.dom.on(node,event,handler)`                                                               | 注册事件，由宿主在销毁页面时清理                                                                                               |
| `QF.host.getContext()`                                                                        | 模式、状态、能力、`permissions:{declared,granted}` 及 `sdk:{apiMajor,apiMinor,packageFormatVersion}`，当前 AI 能力为 false |
| `QF.host.subscribe(handler)`                                                                  | 权威作答、交互能力和白板状态变化后刷新展示                                                                                     |
| `QF.ids.create(prefix)`                                                                       | 编辑时创建选项等身份                                                                                                           |
| `QF.editor.getData()` / `update(patch)`                                                     | 读取完整题目、合并编辑；不能改题目身份和题型                                                                                   |
| `QF.editor.save()`                                                                            | 刷新页面中的编辑草稿；真正保存题库由外层编辑宿主完成                                                                           |
| `QF.bank.save()`                                                                              | 正式编辑会话中等待当前页面输入完成，再调用宿主保存整个题库                                                                     |
| `QF.bank.getState()` / `addQuestion(type)` / `duplicateQuestion()` / `deleteQuestion()` | 编辑会话题目列表、模板新增、复制及删除当前整张题卡                                                                             |
| `QF.navigation.getState()` / `goTo(index)` / `previous()` / `next()`                    | 正式编辑、练习及历史的题目列表与导航；index 从 0 开始，编辑和练习先完成保存屏障                                                |
| `QF.sources.list()` / `open(index)`                                                         | 当前页面的来源展示状态及打开来源；历史使用冻结来源                                                                             |
| `QF.sources.add(link)` / `remove(index)`                                                    | 仅正式编辑会话解析添加 Source Anchor 或移除引用                                                                                |
| `QF.practice.getState()`                                                                      | 当前公开题目信息、状态、分值及结果                                                                                             |
| `QF.practice.getQuestion()`                                                                   | 作答用题目，提交前不含标准答案与解析                                                                                           |
| `QF.practice.getResult()`                                                                     | 提交后的权威结果和 reference；提交前为空                                                                                       |
| `QF.practice.submit()` / `retry()`                                                          | 正式练习宿主的提交确认和重试；开发窗口使用其测试按钮                                                                           |
| `QF.answer.get()` / `update(answer)` / `flush()`                                          | 读取作答、请求保存、等待保存完成                                                                                               |
| `QF.content.render(node,content)`                                                             | 渲染宿主 TEXT/RICH/DOCUMENT 内容                                                                                               |
| `QF.content.mountEditor(node,{value,onChange,formatting})`                                    | 编辑页挂载宿主内容编辑器；返回 getValue()/destroy()，自动响应编辑权限变化；格式调整打开公共富文本编辑窗口                                                                       |
| `QF.ui.mountActions(node)` / `notify(message)`                                              | 公共操作区、显示消息                                                                                                           |
| `QF.ui.configure(patch)` / `getConfiguration()`                                             | 同步配置、读取当前页面的默认公共 UI 可见性；返回相同 Reply 结构                                                                |
| `QF.ui.mountControls(node)`                                                                   | 标记自己的宿主工具栏，在画笔/拖动模式下仍可点击                                                                                |
| `QF.layout.configure(patch)` / `getConfiguration()` / `getState()`                        | 同步声明题卡宽度、最大宽度、水平/垂直对齐及浏览区留白；读取声明和实际布局                                                      |
| `QF.learning.getMode()` / `setMode(mode)` / `toggleMode()`                                | 正式桌面练习、历史的草稿/练习模式查看与切换                                                                                    |
| `QF.whiteboard.getState()` / `setTool(tool)` / `undo()` / `redo()` / `clear()`        | 白板能力状态、工具及编辑历史操作                                                                                               |
| `QF.whiteboard.setAppearance(patch)` / `setZoom(value)` / `zoomBy(factor)`                | 白板底色、样式及缩放                                                                                                           |

练习和草稿复用同一个页面，白板叠加与模式切换由宿主处理。历史和网页预览复用该页并禁止写入。题型不要通过重建主页面模拟作答轮次。

正式练习的“上一次尝试／下一次尝试／返回当前作答”由宿主统一提供，无需题型新增页面或接口。离开当前作答前先等待答案和草稿保存；查看过去的尝试时复用历史只读渲染，显示该次保存的答案、分数和白板快照。返回当前未提交作答后恢复输入，已提交的当前作答仍通过 `QF.practice.retry()` 开始重试。切换尝试不会新增记录、重新评分或覆盖当前草稿；正式尝试仍只在提交成功时由 Core 创建。

题型的展示和交互优先在拓展 HTML/CSS/JS 中实现；公共 UI 可通过配置选择显示，布局通过 `QF.layout` 声明。宿主只执行布局、持久化和权限边界。已保存草稿的几何不被布局热更新覆盖，详细字段与恢复规则见 [布局接口](PUBLIC_UI_API.md#8-题卡与浏览区布局)。

Windows 正式练习切题时，宿主保留旧题卡及其尺寸，暂停交互，同时在同一个浏览器里准备隐藏的新题卡。新页面完成 SDK 初始化、内容渲染和尺寸测量后，再一次性替换可见题卡、恢复该题草稿并释放旧页面。准备中的页面可以读取自己的展示数据和配置 UI，但不能修改答案、提交、重试或操作宿主导航及白板；被替换页面的接口失效，已受理导航请求仍交付回执。这个过程无需拓展适配新接口，当前仍每次创建新的隔离 iframe，尚未按题型缓存或复用页面。

读取 `editor.getData`、`answer.get`、`practice.getState/getResult` 会等待本页此前发出的题目/答案更新，即使更新被拒绝仍可读取保留状态。保存与提交继续遇到前序失败就停止；读取等待不代替页面的刷新版本检查，也不保证多个读取构成原子快照。

公共内容编辑器可调用 `destroy()` 提前卸载，页面销毁也会自动释放。卸载后迟到的格式编辑结果不再触发 onChange；权限拒绝显示错误提示，后续授权恢复仍可继续编辑和保存。`QF.dom.on` 负责监听绑定，不等待任意事件回调中的后续异步工作；题目/答案更新应立即调用 SDK，保存/导航由宿主屏障完成，不能依赖延迟定时器发送更新。

### 3.1 共享编辑外壳与平台适配

正式题库编辑的保存、上一题/下一题、添加、复制、删除确认和引用来源区由公共 HTML 外壳提供。题型可保留默认操作，也可隐藏后用 SDK 绑定自己的控件；上一题/下一题始终由宿主提供。切题保留同一个 WebView 和外壳，替换题型内容；整页继续由外层浏览区滚动。

外壳位于 `quizforge-desktop-app/editor-web/draft-canvas/src/learning/editor-shell.html`，对应 `.css` 与 `.js`。页面与平台通过 JSON 请求/响应通信；每次命令绑定当前 questionId，平台校验操作、题目身份和索引，再调用现有题库及来源服务。命令开始先等待题型 flush；失败保留当前输入并显示错误。

`QF.bank` 与来源修改仅接入正式桌面题库编辑；导航、来源读取/打开和学习模式切换已接入正式练习及历史。用 `QF.host.getContext()` 的 `saveBank`、`navigate`、`manageSources`、`viewSources`、`whiteboard`、`changeLearningMode` 判断宿主是否提供这些能力。历史只能查看；网页预览没有桌面导航和白板，独立开发窗口没有正式题库命令。这些接口为 SDK 2 新增能力，不要求已有扩展修改清单。

`QF.editor.save()` 保存编辑草稿；`QF.bank.save()` 执行正式题库保存。公共保存按钮已自动调用后者。保存成功后桌面按原流程重新打开题库，不改变 `.qbank` 格式。

题型页面的热更新范围保持不变；共享外壳源码属于应用前端，修改它目前需要重新构建前端资源并重新打开应用。工作区、标签栏及练习/历史外围入口仍有原生宿主实现，并未全部迁为 HTML。

### 3.2 扩展选择公共 UI

在 `editor.js` 或 `practice.js` 初始化时调用；没有声明的项默认为 `true`。配置属于当前页面，不保存到题库，不会沿用到下一题型。修改页面脚本可随开发目录热更新。

```js
// editor.js：隐藏默认保存和来源区，使用自己的保存按钮。
QF.ui.configure({ save:false, sources:false });
QF.dom.on(QF.dom.$('[data-save]'), 'click', () => QF.bank.save());

// practice.js：自己的提交按钮，省略宿主提交确认。
QF.ui.configure({ submit:false, retry:false, confirmation:false, note:false });
QF.dom.on(QF.dom.$('[data-submit]'), 'click', () => QF.practice.submit());
QF.dom.on(QF.dom.$('[data-retry]'), 'click', () => QF.practice.retry());
```

| 页面                          | 可配置项                                                                                                                                                                                               |
| ----------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 编辑                          | `title`、`save`、`position`、`typeLabel`、`add`、`duplicate`、`delete`、`sources`、`outline`、`errors`                                                                             |
| 练习 / 草稿 / 历史 / 只读预览 | `card`、`typeLabel`、`position`、`score`、`state`、`submit`、`retry`、`confirmation`、`note`、`sources`、`outline`、`draftToggle`、`draftToolbar`、`draftZoom`、`errors` |

`card:false` 移除默认白色背景、边框、阴影和内边距，保留白板中题卡的位置与尺寸；扩展可在自己的 CSS 中设计外壳。`errors` 控制公共页面操作错误区。原生大纲、来源区和草稿切换按钮只在提供这些组件的正式桌面宿主生效；网页预览没有这些组件。独立开发窗口外围的试答/结果控制不属于此配置。

`QF.ui.configure({navigation:false})`、未知项或非布尔值返回 `INVALID_UI`，整次配置不生效。上一题/下一题不可通过此 API 关闭；在首末题仍遵循正常的边界状态。工作区、标签栏、历史列表及整轮统计页由应用管理。

隐藏默认按钮不会改变能力：自己的保存仍经过 flush 与题库验证，提交仍保存草稿并调用宿主评分，重试仍创建新作答状态；历史和网页预览始终禁止提交和重试。`confirmation:false` 只省略确认面板，不省略提交事务。配置不提供新的文件、AI 或白板操作权限。

### 3.3 自己打造公共 UI

题目增删复制、导航与来源、自定义提交/分值区、模式切换，以及白板工具和缩放均可通过 SDK 绑定自己的 HTML 控件。完整的参数、模式限制与工具栏示例见 [公共 UI 功能接口](PUBLIC_UI_API.md)。自己的画笔/选择按钮所在工具区需用 `QF.ui.mountControls(node)` 标记，避免画笔模式把按钮点击当成笔迹。

## 4. 同步评分

Schema、编辑更新、作答及规则返回值由宿主统一校验；扩展自行返回通过不能绕过检查。方言、本地引用、空答案与错误字段说明见 [宿主数据校验](DATA_VALIDATION.md)。

`type.js` 注册校验及评分函数：

```js
QF.defineQuestionType({
  type: 'publisher.example',
  validate(question) { return []; },
  validateAnswer(question, answer) { return {errors: [], empty: false}; },
  grade(ctx) {
    const score = /* 根据 ctx.question 与 ctx.answer 判分 */ 0;
    return ctx.reportResult({score});
  }
});
```

评分在独立规则引擎中运行，不通过题卡 DOM 判分。`ctx.maxScore` 由冻结的题目快照提供；`reportResult` 只暂存评分，宿主校验成功后才提交记录。每次只能上报一次；分值必须在 0 到最大分之间。分数为空表示未评分。当前单选、多选为全对得满分，否则 0 分。

模板创建、复制、大纲定位和内部 mount/update/destroy 协议由适配层提供，扩展作者无需手写整套生命周期。复合题后续可通过逻辑定义的 `targets(question)` 声明小题目标。

当前只支持同步 grade；返回 Promise 会报错。`feedback` 暂不保存到桌面历史，AI 调用及异步评价事务将按 [接口设计](../docs/题型扩展接口设计.md) 后续接入。

## 5. 网页预览

构建后打开 `dist/preview.html` 查看单选、多选的只读题卡；对应 `.bundle.json` 可供 Hub 宿主加载：

```js
await questionExtensions.install(bundle);
await questionPreview.showQuestion(question);
```

同一份 practice.html 用于桌面和网页展示。这里不包含 Hub 上传、账户和下载服务。缺少资源时宿主需提供公开展示内容；不把桌面路径或 Java 句柄交给网页。

## 6. 当前边界

HTML/CSS/JS 运行在宿主创建的 `sandbox="allow-scripts"` iframe 中，不开放 `allow-same-origin`。拓展的 DOM、样式和全局变量属于自己的页面，不能直接读取宿主 DOM 或 Java 桥对象。SDK 经消息通道调用宿主白名单方法；宿主核对窗口来源、页面会话和当前能力。切题或热更新后旧通道作废。

题卡高度由页面报告，外层浏览区负责滚动。练习滚轮与 Ctrl＋滚轮转交宿主；草稿画笔和拖动由宿主管理。通过 `QF.ui.mountControls` 标记的工具区在画笔、拖动模式下仍能点击。页面源码保持原来的 HTML/CSS/JS 与 `QF.*` 调用方式，不需要开发者自己创建 iframe 或消息协议。

提交和重试在同一道题的页面实例内更新权威状态；切题等页面替换先撤销旧能力，再交付已受理操作的回执。题目和答案更新按顺序执行，保存/提交先等待此前更新；相同操作在等待期间合并，避免双击产生重复事务。异步传输错误统一返回错误码，参数经过 JSON 和接口参数类型校验。具体返回约定见 [公共 UI 接口](PUBLIC_UI_API.md#1-返回与更新)。

开发预览中的 `type.js` 也在独立沙箱中执行；正式桌面评分继续使用既有独立规则引擎和冻结快照。开发工作台的 `extensionWorkbench.load()` 现在返回 Promise；完成后再读取工作台状态。

Windows 正式练习、草稿、题目编辑和历史题卡使用 WebView2 与不授予同源权限的 iframe；权限校验、存储和规则执行仍由宿主管理。沿用 Chromium 默认沙箱并禁止外部导航、下载、弹窗及设备权限，不向题型 iframe 注册原生宿主对象或消息处理器，只受理可信顶层页的消息。历史页还拒绝作答、提交、重试、题目编辑与保存。这与旧页面后端的 AppContainer 权限边界不同，浏览器异常退出和死循环恢复仍待完善。规则进程及仍使用旧后端的页面保留 AppContainer 隔离；普通 AppContainer 可读取 Windows 默认开放的部分系统资源，并非严格路径白名单。其他桌面平台的低权限后端、资源配额及对外分发审查尚待完善。不得在包内存放凭据或绕过宿主的模型配置。详见 [WebView2 后端](WEBVIEW2_BACKEND.md)和[原后端运行故障隔离](RUNTIME_ISOLATION.md)。

生产程序保留原题型的数据模型及文件解码器，以便读取保留题库；原题型不提供默认渲染、编辑或判分回退。当前仅安装的 SDK 2 扩展可以执行。
