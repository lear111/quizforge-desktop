# QuizForge V2 新人指南（HTML SDK 2 阶段）

当前提供三个普通外部 HTML SDK 2 扩展示例：单选、多选和判断题。应用不预装题型，导入后才能使用。其他题型的数据仍可读，但没有新版扩展时不能编辑或作答。

## 模块

| 模块 | 职责 |
| --- | --- |
| quizforge-core | 题库通用模型、内容、工作区和练习事务；扩展规则接口、缺失类型的数据兼容 |
| quizforge-infrastructure | ZIP/JSON 文件、资源、SQLite、版本化扩展包安装与开发目录 |
| quizforge-desktop-app | JavaFX 宿主、WebView 页面适配、公共富文本和白板、编辑/练习/历史入口 |
| extensions/packages | 三份外部题型的 HTML、页面 JS、默认 JSON、逻辑、Schema、样式 |
| extensions/tools | 独立离线打包工具 |

先阅读 [代码地图与五条业务链路](code-guide.md)，再沿具体功能定位文件。桌面共用的练习适配、显示模型和内容解析在 `desktop/learning`，WebView2 后端在 `desktop/browser/webview2`，JavaFX 练习后端在 `desktop/browser/javafx`；`desktop/poc/sharedpractice` 只保留独立演示入口。对应测试按学习适配和浏览器后端放在同名测试包中。

## 先运行

在仓库根目录构建网页资源，再使用 `tools/Start-QuizForge.ps1` 启动。应用不附带题型包；先独立打包，再在“题型扩展”中导入、确认权限并重启。真实空配置测试见 [扩展安装流程](../extensions/README.md)。启动器使用 `target/launcher` 隔离 IDE 输出；更换删除过的 Java 类后先 clean install，避免旧 class 残留。

```powershell
node quizforge-desktop-app/editor-web/draft-canvas/scripts/build.mjs
mvn.cmd -pl quizforge-desktop-app -am '-Dquizforge.build.directory=target/launcher' -DskipTests clean install
```

新题型开发从 [SDK 2 开发说明](../extensions/SDK_README.md) 开始。只需要编辑器和练习两个页面；富文本、题库保存、作答记录、草稿白板、统计及历史由宿主管理。页面中不用写参考题目的默认内容，默认内容单独放 default.json，并与 .qbank 单条 Question 保持一致。

所有已安装扩展均需通过“题型扩展 → 加载开发目录…”显式接入，之后 HTML、页面 JS、CSS 保存才会更新主浏览区。更新保留作答、编辑草稿和白板状态；评分逻辑继续使用安装版本。“独立开发预览…”用于尚未安装的新题型及规则、Schema、默认模板的测试。

## 通用流程

- 题库仍是 ZIP，manifest.json 描述资源，bank.json 保存题目；参见 [题库格式](qbank-format.md)。
- 新建题目通过已安装扩展的模板创建，宿主重新分配 ID。
- 编辑保存经过页面 flush、题目验证和题库写入；编辑页修改只先落入内存草稿。
- 正式题库编辑外壳已经使用共享 HTML：保存、导航、增删复制及引用来源均通过 JSON 宿主命令执行，切题复用同一 WebView。默认提供公共按钮；扩展可用 QF.ui.configure 隐藏后设计自己的控件。正式保存用 QF.bank.save，编辑草稿保存用 QF.editor.save；上一题/下一题由宿主保留。
- 用户作答通过 QF.answer 请求通用事务保存，提交调用独立规则引擎，宿主验证和持久化分值。
- 草稿与练习使用同一题卡；历史重放冻结版本与作答，禁止写入。
- 缺失扩展显示提示，保留底层数据；不要恢复旧的题型专用回退实现。

旧题型的专项快照、原生题卡和废弃编辑/作答入口已清理。当前扩展的数据结构位于 `question/model/choice` 和 `question/model/extension`，旧文件的值类型位于 `question/compat`，页面与快照的 JSON 桥位于 `question/codec`；`question/type` 只保留题型契约和扩展执行适配。历史答案解码仍用于读写已有记录；具体用途见 [代码地图中的保留说明](code-guide.md#保留下来的题型数据为何还在-core)。通用作答使用 `saveExtensionDraft`，页面通过 QF.answer 发出意图，导航模型不拥有题型私有答案。

## 定向验证

根据改动选择对应检查。下面第一条 Java 命令适合共用内容投影与 JavaFX 操作队列的结构整理；完整浏览器行为使用 [WebView2 验收入口](../extensions/WEBVIEW2_BACKEND.md)，需要先按说明准备原生运行环境。

```powershell
mvn.cmd -pl quizforge-desktop-app -am '-Dquizforge.build.directory=target/learning-check' '-Dtest=SharedExtensionContentTest,PracticeMutationQueueTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
node --test extensions/tools/pack.test.mjs
npm --prefix quizforge-desktop-app/editor-web/draft-canvas test
mvn.cmd -pl quizforge-desktop-app -am '-Dtest=ExtensionPackageStoreTest,ExtensionDevelopmentSourceTest,PracticeSummaryTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

这些命令覆盖各自涉及的层，不表示已经完成整个应用的回归。旧题型行为测试仍有迁移项，不能用编译成功代替浏览器验收。

共享编辑外壳见 `editor-web/draft-canvas/src/learning/editor-shell.html`、`.css`、`.js`（均在 `quizforge-desktop-app` 下）；Java 平台适配在 `QuestionBankEditorView` 与 `ExtensionEditorFields`。浏览区滚动容器和工作区窗口仍由 JavaFX 管理。

扩展可在 editor.js / practice.js 调用 `QF.ui.configure({...})` 选择题卡外壳、信息、默认操作、来源、大纲和白板工具等公共 UI，未配置时保持默认。配置仅影响显示，历史/网页只读权限与保存、评分事务不变。可选项及自定义按钮示例见 [SDK 公共 UI 配置](../extensions/SDK_README.md#32-扩展选择公共-ui)。

自己的公共 UI 绑定 `QF.bank`、`QF.navigation`、`QF.sources`、`QF.practice`、`QF.learning`、`QF.whiteboard`；读取状态后决定按钮是否可用。白板工具区用 `QF.ui.mountControls` 标记以保持绘画时可点击。详见 [公共 UI 功能接口与示例](../extensions/PUBLIC_UI_API.md)。

题卡宽度、最大宽度、初始对齐和浏览区留白通过 `QF.layout.configure` 声明；编辑页、练习页和预览都可使用。展示与交互优先由拓展实现，宿主负责执行接口、状态保存和权限检查。已有草稿的尺寸、位置和笔迹优先保留，字段与恢复规则见 [布局接口](../extensions/PUBLIC_UI_API.md#8-题卡与浏览区布局)。
