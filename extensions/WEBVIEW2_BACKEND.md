# WebView2 学习界面后端

Windows 正式练习、草稿、题目编辑与历史题卡已默认接入 WebView2，直接渲染并处理输入，去掉这些页面的工作 JVM 与 PNG 画面转发。题型仍使用既有 SDK，答案、评分、草稿和题库编辑仍由现有 Java/Core 服务管理。历史列表、作答切换、大纲、来源栏与统计卡片继续使用宿主控件。

## 正常应用

在仓库根目录运行 `./tools/Start-QuizForge.ps1`，重新启动后打开题库即可使用。脚本会在原生 DLL 缺失或源码更新时构建 DLL；DLL 正在被旧实例使用时，需要先关闭旧实例。

原生库查找不依赖 Maven 的当前工作目录：默认从工作目录和桌面模块代码位置向上查找 `target/webview2-native/quizforge_webview2.dll`，也支持安装目录中的 `native/quizforge_webview2.dll`。显式配置 `-Dquizforge.webview2.library=绝对路径` 或环境变量 `QUIZFORGE_WEBVIEW2_LIBRARY` 时，以指定位置为准；无效的显式路径会报错，不会偷偷改用其他 DLL。

启动时先显示工作区加载界面，再后台扫描已安装包。扫描仍检查包完整性和样例，完成后登记当前版本及历史冻结版本；此阶段不启动规则工作进程。只有实际调用某个版本的规则时才启动该版本的沙箱，所以已安装的历史版本不会全部拖慢启动。首次规则调用仍可能等待冷启动，开发环境通过 Maven 构建的时间也不属于此优化范围。

`PracticeLearningSurface` 是宿主与浏览器后端的共同接口。Windows 默认选择 `WebView2LearningSurface`，其他平台暂时保留 JavaFX 后端。诊断时可在 JVM 参数中显式设置 `-Dquizforge.learning.backend=javafx`。WebView2 启动失败会报告错误，不自动改用权限边界不同的后端。

同一练习标签页复用浏览器实例；切题只替换题目 iframe 和数据。上一题、下一题由可信网页绘制并通过宿主导航接口执行。切换到其他标签页时隐藏原生子窗口，返回时恢复同一实例。切题先等待保存屏障；关闭标签页、切换工作区及退出应用异步等待草稿保存，避免阻塞 JavaFX 线程。题型 UI 偏好、来源接口、权限撤销和拓展 HTML 开发热更新使用数据消息通道。

练习恢复时按当前题库、题目数据及实际规则版本缓存已校验的逻辑快照，避免每次保存与切题重新执行整库快照规则；数据库记录仍逐条比较，替换题库或规则定义会失效。练习大纲使用已恢复的冻结编号，编辑大纲仍依据编辑后的数据更新。旧尝试浏览器改为首次查看时创建，随后复用，因此第一次查看旧尝试仍有浏览器启动开销。

切题失败后恢复被暂停的保存队列，保留原错误；如果 Core 已切到另一题，则加载其数据，避免旧页面对新题身份写入。规则进程冷启动失败允许下一次调用重新启动，同样保留超时和隔离限制。此轮没有替换规则引擎，首次规则进程初始化、整库首次校验和沙箱运行时复制的开销仍存在。

本轮通过六项定向测试（逻辑快照缓存两项、切题失败恢复及冻结大纲三项、实际规则进程启动失败后重试一项）。独立构建的真实 WebView2 使用两个外部 2.3.2 包和隔离数据库，连续六次同题型/不同题型切换约 263–452 ms；扩展发起导航及注入导航失败后再次切换通过，选择答案保留。记录为 `target/navigation-verification.json`。这是单次开发环境验证，包含检查轮询开销，没有旧版本耗时基线，也没有测量总内存下降；未运行全量测试。

## 独立验证入口

先独立构建 `extensions/dist/` 中的外部安装包（见 [流程说明](README.md)），然后在仓库根目录运行：

```powershell
./tools/Start-WebView2-Practice.ps1
```

首次需要 Node/npm、JDK、Maven、Visual Studio C++ Build Tools x64 和已安装的 WebView2 Runtime。原生构建脚本下载固定版本的微软 WebView2 SDK，仅缓存到 `target/webview2-native`。Java 与前端仍沿用现有项目构建工具。

```powershell
# 已完成构建时直接启动
./tools/Start-WebView2-Practice.ps1 -SkipBuild

# 实际浏览器输入与保存验证，完成后自动关闭
./tools/Start-WebView2-Practice.ps1 -SkipBuild -Verify

# 正式 PracticeSurfaceHost、正式透明窗口样式的集成验证
./tools/Start-WebView2-Practice.ps1 -Product -SkipBuild -Verify

# 正式 QuestionBankEditorView、独立测试题库的编辑与保存验证
./tools/Start-WebView2-Practice.ps1 -Editor -SkipBuild -Verify

# 正式 PracticeHistoryDetailView、独立 SQLite 归档记录的只读回放验证
./tools/Start-WebView2-Practice.ps1 -History -SkipBuild -Verify
```

普通验证入口每次生成三个单选题和独立 SQLite 练习库，放在 `target/webview2-acceptance/<UUID>`。`-Product` 入口使用正式练习组件，生成单选、多选、单选三张题卡，放在 `target/webview2-product-acceptance/<UUID>`。`-Editor` 使用正式编辑组件，在 `target/webview2-editor-acceptance/<UUID>` 生成独立题库并保存编辑结果。`-History` 在 `target/webview2-history-acceptance/<UUID>` 创建真实 SQLite 归档：同一单选题两次提交、多选题未提交草稿及一道未作答题。四个入口都不读取、修改用户工作区或现有 `.qbank`。它们通过显式 `--extensions=<目录>` 参数读取外部包并执行测试授权；PowerShell 工具的 `-ExtensionsDirectory` 默认为 `extensions/dist/`。这是验收驱动，正式 DesktopApplication 不调用它，也不自动安装或授权题型。每次验收生成 `extension-imports.json`，记录空配置、导入、重启和包哈希。

正式编辑页复用可信 HTML 编辑外壳和题型 iframe，整个页面统一滚动。富文本编辑与资源解析通过异步消息调用已有宿主功能；Ctrl＋S、切题、复制、删除与来源引用仍走原有权限及数据校验。切题和保存等待前序编辑；返回练习、关闭标签页及退出先等待编辑草稿写回。兼容已有同步 Java 保存入口时使用有界 JavaFX 嵌套事件循环，让浏览器回执继续被处理。

历史页使用 `HistoryLearningSurface` 与独立只读浏览器。保留既有 `HistoryDraftAdapter` 的冻结投影，浏览器没有活动练习 runtime、数据库引用或保存桥。只开放历史题卡导航、练习/草稿查看模式及来源打开；答案更新、提交、重试、题目编辑与保存均被拒绝。切题与切换作答等待真实 iframe 就绪，并按选择版本忽略过期回执。统计卡片显示时隐藏原生窗口，返回题卡复用浏览器；标签页隐藏、返回列表及关闭时沿用宿主生命周期。

## 结构

### 审查修复：校验、消息与临时目录（2026-10-06）

- 可信顶层页使用独立、可终止的 Schema Worker，编译/校验有截止时间和复杂度预算；拓展 iframe 不获得 Worker 或原生桥能力。规则进程的超时不再被误当成页面 Schema 保护，详见 [数据校验](DATA_VALIDATION.md)。
- Java ↔ 原生 ↔ 可信页面双向消息采用透明分块。单块数据最多 1 Mi 字符，逻辑消息最多 128 Mi 字符，校验分片序号、总数、总长度和 30 秒过期；实际原生物理消息仍受 8 Mi 字符限制。支持大文档、图片及编辑回传，不再通过整体放宽原生单条上限传输。损坏或超限消息产生错误并丢弃组装，不关闭整个浏览器。
- 编辑、练习和历史的临时目录由 `WebView2TemporaryDirectories` 管理。关闭等待原生控制器释放和浏览器进程退出，然后异步删除，文件占用时重试；应用启动回收遗留目录。跨进程文件锁、活跃浏览器检查、目录年龄及严格路径/链接检查保护仍在使用的目录；正式工作区、扩展和历史存档不在清理范围。
- 本次原生修改需要重建 DLL 并重新启动。正在使用旧 DLL 的实例不会被自动终止；独立验收使用单独构建的 DLL，不覆盖运行中的文件。

本次定向 Java 检查 22 项通过，覆盖 Schema 复杂度、数据保存校验、分片组装及目录回收。真实 WebView2 验证了 9 Mi 字符内容双向无损传输、损坏分片后继续响应、Worker 校验与编辑页面加载，并确认关闭后浏览器进程退出；结果在 `target/browser-transport-check/large-message-verification.json`。正式练习组件 38 项验收全部通过，结果在 `target/webview2-product-acceptance/78153fd9-dbaa-4723-ae11-667032d40bcf/verification.json`。未运行全量测试。

- `quizforge-desktop-app/native/webview2/quizforge_webview2.cpp`：独立 COM STA 线程、WebView2 控件、原生子窗口、资源拦截与有界消息队列。由 WebView2 原生绘制并处理输入，不定时截图。
- `browser/PracticeLearningSurface`、`browser/webview2/WebView2LearningSurface`：正式练习组件的浏览器接口、页面命令、权限与开发更新、异步保存及关闭生命周期。
- `browser/webview2/WebView2Browser`：JavaFX 区域与 HWND 的尺寸绑定、异步消息读取、可信宿主诊断。缩放按原生客户区和 JavaFX Scene 的比例换算。
- `browser/webview2/WebView2PracticeSession`：串行后台队列，调用现有 Core 练习、评分、SQLite 和草稿服务，校验会话、题目及操作序号。
- `browser/webview2/WebView2EditorSurface`、`src/webview2-editor-bridge.js`：正式编辑浏览器及异步 JSON 消息通道，调用已有编辑模型、富文本窗口、资源与文件保存接口。
- `browser/HistoryLearningSurface`、`browser/webview2/WebView2HistorySurface`、`src/webview2-history-bridge.js`：冻结历史的只读浏览器接口、导航与查看消息，复用历史白板及题卡渲染。
- `src/webview2-practice-app.js`：可信宿主页，只接受明确的数据消息。复用既有题型 SDK、练习 runtime 和白板。拓展页面使用 `sandbox="allow-scripts"` iframe，不授予同源权限。
- `WebView2Verification`：宿主侧诊断与浏览器输入验证；调用的脚本和 CDP 接口不暴露给拓展。

浏览器控件与环境在一次窗口会话中复用。切题只刷新拓展 iframe 和题目数据，不创建新的页面 JVM。规则仍使用已实现的独立沙箱进程；评分与保存在后台队列执行，不等待 JavaFX UI 线程。

固定导航由可信宿主统一绘制：上一题/下一题位于浏览区左右居中，上一次尝试/下一次尝试位于上下居中，均为白底、带阴影的圆角矩形箭头按钮。练习、草稿和历史浏览使用同一份 `learning/navigation.js` 与 CSS；草稿顶部按钮避开绘画工具栏。尝试按钮继续调用宿主的尝试选择流程，保留只读回放、切换前保存和忙碌状态保护。没有可切换的题目或尝试时隐藏对应方向按钮。

本次固定导航改动已完成前端构建与 Java 编译，真实浏览器的练习/历史页面均通过四个按钮的样式、位置及点击消息检查。正式练习组件 39 项验收通过，其中上下尝试箭头直接通过浏览器点击触发，验证记录回放与当前作答恢复。结果在 `target/navigation-preview/product-verification.json`，页面样式截图在同目录；未运行全量测试。

## 切换优化（2026-10-06）：历史、编辑与尝试

历史题卡也使用隐藏准备与整体替换：不在切换前清空或隐藏旧内容，等待目标 iframe 的初始化、订阅刷新、最终高度和布局帧完成，再同步恢复该记录的草稿。加载回执绑定此次渲染的 Promise，不能用旧 iframe 的 ready 标记提前完成。清空时取消待显示的题卡，避免迟到初始化重新显示旧记录。

编辑器保留旧编辑内容，新编辑 iframe 在相同宽度的隐藏区域准备；题号、题型、来源与公共 UI 在新编辑器就绪后更新。导航仍先等待输入写入和保存屏障；旧 iframe 仅保留已受理导航的回执通道。待显示页面不能执行写入、保存或导航，公共 UI 和布局配置先暂存，清空及关闭会撤销待显示页面。编辑内容保持自然高度，由整个编辑页面滚动。

当前作答与旧尝试使用不同浏览器时，目标浏览器先在同一窗口的屏外区域完成布局和渲染，原浏览器保持显示；完成后切换可见页面。旧尝试之间复用只读浏览器并使用题卡整体替换。保存和只读边界保持不变，切换失败恢复原显示页面。

前端 42 项定向检查及两个 Java 宿主的编译通过。独立真实 WebView2 检查使用用户已安装的单选、多选、判断题 2.1.0 页面，覆盖历史切题/重复加载、冻结位置、只读拒绝、编辑器跨题型切换、切换前保存及拓展导航回执；逐帧检查未出现空白题卡，显示前已完成异步订阅刷新。验证程序位于 `target/switch-preview/SecondarySwitchPreview.java`，结果在同目录的 `secondary-verification.json`。

另用实际 `PracticeSurfaceHost`、Core 与隔离 SQLite，配合可控的浏览器就绪回执，验证当前→历史、历史→历史、历史→当前及加载失败恢复共四条流程；确认保存先于加载、目标就绪前保留原浏览器、未提交答案不变，以及失败时释放准备区。该检查使用测试 Java 规则和浏览器替身，不覆盖原生窗口绘制，程序为 `target/switch-preview/AttemptSwitchWorkflow.java`。两次完整沙箱验收均停在题型规则启动超时，未进入正式练习界面的检查，不能视为端到端验收通过。没有运行全量测试。

## 本机验证结果（2026-10-05）

原生 DLL、前端 bundle 和 Java 构建通过。SDK 生命周期、练习/草稿切换及原生页面桥的现有 19 项定向检查通过；WebView2 独立入口的 15 项集成检查通过：

- 不加载 PNG 页面桥，拓展 iframe 不能访问宿主 DOM。
- 浏览器点击触发原有选项事件并保存到 Core；画笔、手型拖动后能继续选择其他选项。
- 练习模式保留笔迹；切题、返回后恢复答案和草稿。
- 提交调用既有隔离评分服务；重试、中文编辑提交和编辑器滚动正常。
- JavaFX 队列仍可处理导航任务。

这次浏览器就绪为 2351.5 ms，下一题渲染为 167.4 ms，返回为 199.4 ms。验收驱动另测下一题完成为 252.4 ms（包含驱动轮询）。这些是单次本机数据，且不包含规则进程初始化，不能视为应用冷启动或长期性能基准。

结果保存在 `target/webview2-acceptance/a1edd439-08d9-4aeb-a74e-cf79485ab6af/verification.json`。`selectAndPersist` 包含驱动等待和 iframe 目标定位，不能作为纯粹的选项保存延迟。选择与中文提交通过可信宿主调用 iframe 的 CDP 输入接口；白板绘画与拖动通过 Windows 原生鼠标输入，验证会临时移动光标。所有诊断接口均未发布给拓展。输入验证未覆盖真实 IME 候选窗。

正式 `PracticeSurfaceHost` 集成入口另通过 20 项检查，覆盖上述主要链路，以及真实多选题型加载、原生子窗口随标签页隐藏/恢复、隐藏后保存及关闭前异步保存屏障。使用与正式应用一致的透明窗口样式，未运行全量测试。进入编辑/历史、返回练习的页面生命周期也已接入异步保存与关闭解除；此验收使用正式练习组件，未覆盖整个工作区的所有 UI 流程。

该次选择并持久化为 308.0 ms，切到下一题且宿主导航解除等待为 355.9 ms，均包含验收驱动等待，不能作为纯业务延迟。结果：`target/webview2-product-acceptance/0d349e21-32de-44b6-9dc8-5ded0f1a0826/verification.json`。

后续增加正式练习中的尝试切换：宿主先保存当前输入，再暂停可写练习页，按需复用只读历史浏览器展示指定尝试；返回时恢复原练习浏览器与当前答案、草稿，不新增扩展 SDK 接口。切题退出尝试回放，历史记录自身仍完全只读。扩充后的正式组件验证通过 32 项检查，包括旧尝试答案及笔迹、只读写入拒绝、暂停页面拒绝迟到写入、当前未提交内容恢复、继续编辑、再次提交和重试、回放中切题及关闭保存。结果：`target/webview2-product-acceptance/4af67d4d-c1ac-4401-ac51-714780a22c0e/verification.json`。此次未运行全量测试，其他平台的兼容后端仅完成编译检查。

2026-10-06 正式练习进一步改为准备完成后再显示新题卡：切题等待期间保留旧题卡画面、固定尺寸并禁止交互，新 iframe 在隐藏区域完成初始化及测量后一次性替换，题卡与草稿同时恢复。旧页面立即失去当前题目能力，保留已受理导航操作的回执通道直至交付，不积累题目页面缓存。正式组件通过 38 项实际窗口检查，其中新增同题型及不同题型的逐帧画面保留、尺寸稳定与旧页面释放检查；原尝试回放、草稿保存及关闭屏障检查继续通过。结果：`target/webview2-product-acceptance/9e96789c-3064-41f1-969b-57c8595f55a6/verification.json`。前端权限、渲染协议与 SDK 生命周期定向检查 22 项通过；前端构建及 Java 编译通过，未运行全量测试。该优化目前启用于 Windows 正式练习浏览器，历史回放与其他平台兼容后端仍沿用既有显示流程。新 iframe 的初始化耗时仍存在，未实现同题型页面复用。

随后修复切题期间提交按钮先消失再出现的问题：单选、多选和判断题模板 2.1.2 将按钮可见性与临时交互能力分开；原题卡锁定时只禁用操作，不删除按钮。宿主在 iframe 首次 ready 后等待订阅刷新完成，接收最终尺寸，再显示新题卡。前端定向检查 29 项通过；独立真实 WebView2 窗口验证三个题型切换期间的按钮保留、高度稳定、最终异步刷新完成和持续可见画面。该检查使用隔离数据，未操作用户工作区，未运行全量测试。安装版需要导入 2.1.2 包；宿主脚本更新需要重新打开应用。

实际安装的 `quizforge.builtin.* 2.1.0` 仍使用临时能力决定按钮可见性，因此继续补上宿主侧兼容处理：在保存及切题前，保留当前 iframe 的展示状态、暂停其状态通知，并将题卡设为 inert；等待期间的 UI 能力读取沿用保留画面的状态，但作答、提交及重试仍依据当前交互状态和权限独立校验。权限撤销不能因保留画面而失效。新题卡保持独立初始化，完成刷新与测量后整体替换；取消切换或返回当前题卡时恢复通知和交互。没有修改已安装扩展、题库或历史记录，也不依赖升级扩展包。宿主前端更新需要重新打开应用。

该兼容修复通过 32 项定向检查，包含保留画面时拒绝写入、权限撤销和历史只读检查。真实 WebView2 窗口直接读取实际安装的 2.1.0 页面源码作为隔离测试数据：修改前复现提交按钮消失，内容高度由 515 降到 479；修改后三个题型均保持按钮和高度，最终异步刷新完成后再显示新题卡，锁定期间写入被拒绝，返回当前题卡可恢复交互。前后日志分别为 `target/switch-preview/legacy-before.log` 和 `target/switch-preview/legacy-after.log`；未运行全量测试。

正式 `QuestionBankEditorView` 另通过 17 项集成检查：隔离 iframe、真实中文输入写回 Core、多选答案修改、切题保留未保存内容、复制/删除、权限撤销拒绝写入、开发页面更新保留编辑草稿、整个编辑页滚动、iframe 内 Ctrl＋S、实际题库 JSON 保存、同步 Java 保存入口、隐藏标签页后异步保存及返回复用浏览器。最终构建结果：`target/webview2-editor-acceptance/7d3f9200-7dda-44df-9967-7435925b3999/verification.json`。前端消息、权限及 SDK 生命周期的 17 项定向检查通过。未运行全量测试；本次输入使用真实 Windows 鼠标与浏览器中文文本注入，未验证 IME 候选窗，也未自动操作独立富文本编辑窗口。

正式 `PracticeHistoryDetailView` 通过 23 项集成检查，覆盖两次提交的独立答案、判分与笔迹、冻结文本与纸张样式、未提交多选草稿、未作答题、练习/草稿切换、标签页隐藏/返回、分值卡及上一题、权限撤销与页面开发更新。验证直接发送的 iframe 原生导航消息不被受理，并确认作答、提交、重试、编辑和保存均被拒绝。前后对比五张 SQLite 表的全部记录，练习状态、作答与草稿均未改变。历史数据的 7 项前端检查通过。结果：`target/webview2-history-acceptance/dd7b7f49-443e-4747-978c-f07c8c84332c/verification.json`；同目录保留 `records-before.json` 与 `records-after.json`。未运行全量测试；该入口使用正式历史组件，未覆盖所有工作区、来源跳转和窗口故障场景。

定向前端检查命令（在 `quizforge-desktop-app/editor-web/draft-canvas` 运行）：

```powershell
node --test test/page-actions.test.js test/sdk-lifecycle.test.js test/learning-mode.test.js
# 编辑迁移的消息、权限及生命周期检查
node --test test/page-actions.test.js test/sdk-lifecycle.test.js test/permissions.test.js
# 冻结历史数据与草稿回放检查
node --test test/history-replay.test.js
```

### 2026-10-06 人工审查后的切换与历史栏修正

- 切题保存、加载和显示使用一个忙碌区间，导航在此期间保留节点和可见性，最后统一更新可用方向。编辑页在显示新内容之前应用扩展 UI 设置，避免默认工具栏先出现再隐藏。
- Windows 练习和历史的分值卡在原浏览器中显示，上一题箭头保留同一节点。当前作答回放返回复用已保存的题卡；存在作答记录时提前初始化独立只读回放浏览器。
- 未改变题库版本的编辑往返复用编辑与练习浏览器；每个文件页最多保留一条已打开历史详情，打开其他记录或关闭文件时释放。离开的可写页面仍保持保存屏障，返回相应模式才解除。
- 历史详情使用顶部文件栏显示“返回历史记录”、历史标题和草稿开关，删除额外标题栏、作答次数文字及未提交草稿按钮。上下尝试箭头继续保留未提交状态的入口。
- 前端构建、Java 编译和 6 项生命周期检查通过；隔离真实 WebView2 验证编辑切题、保存与退休页面回执、历史切题，以及总结往返保留导航节点，共 10 项检查通过。检查程序为 `target/switch-preview/CurrentSwitchPreview.java`。辅助 Core 流程替身检查最初因创建无关浏览器超时，调整夹具仅注入浏览器替身后，尝试往返与加载失败恢复 4 条流程通过；没有执行全量测试或整个工作区模式往返验收。首次打开尚未初始化的页面仍需要浏览器启动，不承诺零延迟。

## 当前限制与安全边界

- 主页面以虚拟 HTTPS 地址加载，仅允许三个明确列出的构建资源；其他资源请求返回 403。没有本地 HTTP 服务或任意目录映射。
- 禁止主页面外部导航、frame 外部导航、弹窗、下载和设备权限，关闭宿主对象、默认脚本弹窗、浏览器快捷键及开发者工具 UI。
- Java 只接收可信顶层页面来源的 WebMessage。没有给 iframe 注册原生消息处理器。WebView2 的内置 `chrome.webview` 对象可能存在于子框架，但宿主不受理子框架直接发出的消息；已验证伪造导航不能跳转历史题卡。拓展仍通过原有 SDK dispatcher 调用允许的接口。
- 保留 Chromium 默认沙箱，不使用 `--no-sandbox`。浏览器自身有可信 broker；这与此前把整个 JavaFX/WebKit JVM 放入自定义 AppContainer 的实现不同，不能宣称二者具有完全相同的操作系统权限边界。
- 尚未实现恶意页面死循环恢复、异常浏览器退出恢复及完整请求源分类限制。验证完成不等于允许直接上线第三方拓展。
- 编辑器检查的中文提交使用浏览器 `Input.insertText`。真实 Windows 输入法候选窗、触控笔压感、复杂触摸和辅助功能需后续真实设备检查。
- 使用原生窗口承载整个学习区，白板和题卡同在浏览器内，避免白板覆盖浏览器子控件。已处理标签页显示/隐藏与窗口尺寸绑定；窗口重建、JavaFX 弹出菜单与原生窗口的遮挡、不同 DPI 屏幕切换仍需后续验证。
- 正式标签页当前在系统临时目录 `QuizForge-webview2/learning-*`（练习）、`editor-*`（编辑）或 `history-*`（历史）创建浏览器配置与三个可信资源副本。关闭时等待浏览器退出后回收配置，启动时回收安全确认的遗留目录；无法确认退出或仍被占用时保留并稍后重试。原生 DLL 的安装包分发尚未实现，当前使用仓库构建脚本。
- 规则工作进程按包版本首次使用时启动；浏览器就绪计时不包括此前的规则初始化，不能当成整个应用冷启动时长。

## 后续接入

Windows 正式练习、草稿、题目编辑与历史题卡已接入。独立拓展开发预览仍使用原后端。临时配置回收已接入；后续完善浏览器故障恢复、原生 DLL 分发及设备验证。其他平台提供对应浏览器后端，共享前端及 SDK 协议。

参考：[微软进程模型](https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/process-model)、[安全要求](https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/security)、[原生窗口承载](https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/windowed-vs-visual-hosting)。
