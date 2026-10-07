# QuizForge V2 新人指南（HTML SDK 2.3）

2026-10-07。当前提供单选、多选、判断、完形、阅读、排序、翻译、作文八种外部扩展。应用不预装题型，导入并授权后使用；缺少对应扩展时保留数据并提示安装。新页面使用 [SDK 2.3 精简接口](../extensions/SIMPLE_PAGE_API.md)。

## 模块

| 模块 | 职责 |
| --- | --- |
| quizforge-core | 题库通用模型、内容、工作区和练习事务；扩展规则接口、缺失类型的数据兼容 |
| quizforge-infrastructure | ZIP/JSON 文件、资源、SQLite、版本化扩展包安装与开发目录 |
| quizforge-desktop-app | JavaFX 宿主、WebView 页面适配、公共富文本和白板、编辑/练习/历史入口 |
| extensions/packages | 八种外部题型的 HTML、页面 JS、默认 JSON、规则、Schema、样式和真实样例题库 |
| extensions/tools | 独立离线打包工具 |

先阅读 [代码地图与五条业务链路](code-guide.md)，再沿具体功能定位文件。桌面共用的练习适配、显示模型和内容解析在 `desktop/learning`，WebView2 后端在 `desktop/browser/webview2`，JavaFX 练习后端在 `desktop/browser/javafx`；`desktop/poc/sharedpractice` 只保留独立演示入口。对应测试按学习适配和浏览器后端放在同名测试包中。

## 先运行

在仓库根目录构建网页资源，再使用 `tools/Start-QuizForge.ps1` 启动。应用不附带题型包；先独立打包，再在“题型扩展”中导入、确认权限并重启。真实空配置测试见 [扩展安装流程](../extensions/README.md)。启动器使用 `target/launcher` 隔离 IDE 输出；更换删除过的 Java 类后先 clean install，避免旧 class 残留。

```powershell
node quizforge-desktop-app/editor-web/draft-canvas/scripts/build.mjs
mvn.cmd -pl quizforge-desktop-app -am '-Dquizforge.build.directory=target/launcher' -DskipTests clean install
```

新题型开发从 [开发指南](../extensions/DEVELOPMENT_GUIDE.md) 和 [现行接口](../extensions/SIMPLE_PAGE_API.md) 开始。判断题为直接维护 JS 的 11 文件模板，其余七型使用源码构建。只需要编辑器和练习两个页面；公共内容、题库保存、作答记录、白板和统计由宿主管理，历史复用练习页。默认内容单独放 default.json，与 .qbank 单条 Question 同构；每包还需独立样例题库。

所有已安装扩展均需通过“题型扩展 → 加载开发目录…”显式接入，之后 HTML、页面 JS、CSS 保存才会更新主浏览区。更新保留作答、编辑草稿和白板状态；评分逻辑继续使用安装版本。“独立开发预览…”用于尚未安装的新题型及规则、Schema、默认模板的测试。

## 通用流程

- 题库仍是 ZIP，manifest.json 描述资源，bank.json 保存题目；参见 [题库格式](qbank-format.md)。
- 新建题目通过已安装扩展的模板创建，宿主重新分配 ID。
- 编辑保存经过页面 flush、题目验证和题库写入；编辑页修改只先落入内存草稿。
- 正式题库编辑外壳使用共享 HTML，导航、增删复制及来源通过 QF.requestAction 请求，切题保留 WebView 并准备对应隔离页面。编辑草稿用 QF.save editDraft，正式题库保存用 edit 或 requestAction saveBank；上一题/下一题由宿主保留。
- 用户作答通过 QF.save draft/submit 请求通用事务保存，提交调用独立规则引擎，宿主验证和持久化分值。
- 草稿与练习使用同一题卡；历史重放冻结版本与作答，禁止写入。
- 缺失扩展显示提示，保留底层数据；不要恢复旧的题型专用回退实现。

旧题型的专项快照、原生题卡和废弃编辑/作答入口已清理。当前扩展的数据结构位于 `question/model/choice` 和 `question/model/extension`，旧专项存储模型及 `question/compat` 已删除，只接受 CHOICE/EXTENSION 封装；页面与快照的 JSON 桥位于 `question/codec`；`question/type` 只保留题型契约和扩展执行适配。历史答案解码仍用于读写已有记录；具体用途见 [代码地图中的保留说明](code-guide.md#保留下来的题型数据为何还在-core)。通用作答使用 `saveExtensionDraft`，精简页面通过 QF.save 发出意图，导航模型不拥有题型私有答案。

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

扩展可调用 QF.ui.configure 选择信息、默认操作、来源、大纲等公共 UI；精简练习页默认无宿主题卡外壳，白色圆角卡由扩展 CSS 提供。配置只影响显示，不改变历史/预览权限和保存评分事务。可选项见 [现行页面配置](../extensions/SIMPLE_PAGE_API.md#5-页面配置辅助接口与默认白板)。

自己的按钮绑定 QF.save 或 QF.requestAction，通过 onLoad 的 permissions/grantedPermissions 决定是否可用；精简页不暴露旧业务命名空间。白板默认使用应用工具与右上角入口，page.configure useDraft 可禁用。暂时失去能力只禁用控件，避免题卡布局抖动。

初始宽度、对齐和留白通过 QF.page.configure initialLayout 或 QF.layout.configure 声明；编辑、练习、预览均可使用。宿主执行接口、保存和权限检查。已保存的草稿几何优先，字段范围见 [布局接口](../extensions/SIMPLE_PAGE_API.md#5-页面配置辅助接口与默认白板)。
