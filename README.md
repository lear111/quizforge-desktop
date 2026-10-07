# QuizForge Desktop V2

> 当前阶段（2026-10-07）：HTML SDK 2.3，提供单选、多选、判断、完形、阅读、排序、翻译、作文八种外部示例包，需用户导入并授权。新页面使用精简加载、保存、操作接口；Windows 正式练习、草稿、题目编辑与历史题卡使用 WebView2。当前接口见 [SDK 2.3 参考](extensions/SIMPLE_PAGE_API.md)，业务入口见 [代码地图](docs/code-guide.md)。

Java 21 + JavaFX 本地桌面题库应用，独立于 V1。支持工作区文件管理、Markdown 编辑与命名来源引用；题目的编辑与练习由安装的 HTML 扩展提供。宿主管理作答保存、公共白板、提交确认、重做和归档历史，统计统一使用得分。AI 仅保留设置、凭据和连接测试。

## 构建与运行

在本目录使用 JDK 21 与 Maven：

```powershell
mvn.cmd -pl quizforge-desktop-app -am '-Dquizforge.build.directory=target/launcher' -DskipTests clean install
.\Start-QuizForge.cmd
```

根目录统一使用 `.cmd` 启动入口：`Start-QuizForge.cmd` 普通启动，`Start-QuizForge-LiveUi.cmd` 开发启动，两者均可双击。共用的启动实现位于 `tools/Start-QuizForge.ps1`，参数由 `.cmd` 原样传入，例如 `.\Start-QuizForge.cmd -LiveCss`。

Windows 学习与题目编辑后端需要 WebView2 Runtime；源码启动器在 DLL 缺失或更新时使用 Visual Studio C++ Build Tools x64 构建原生适配器。历史列表、作答切换、大纲和统计沿用宿主控件，历史题卡与只读白板使用 WebView2。详见 [WebView2 后端与验证说明](extensions/WEBVIEW2_BACKEND.md)。

普通启动使用包内 Canvas 页面，不要求 Node/npm。修改 Canvas 前端时：

```powershell
Set-Location quizforge-desktop-app/editor-web/canvas
npm ci
npm run build
# 回到项目根目录后启动开发模式
Set-Location ../../..
.\Start-QuizForge-LiveUi.cmd
```

LiveUi 合并 Java、CSS 与 Vite 更新，需要支持增强类重定义的 JBR 21。普通 JDK 21 可用于 Maven 编译与普通启动。详见 [开发热更新说明](docs/development-live-update.md)。

正式练习与草稿共用 HTML 扩展页面，草稿在练习页上叠加公共白板。八种题型均需安装对应外部包后执行；缺少对应包时显示提示，底层题库及资源保留。题卡样式由扩展决定，提交冻结、重试、历史与统计由宿主管理。练习保留白板背景及已有笔迹，首次纸张为纯白无纹理。

历史详情按当前 Attempt 显示“草稿 / 返回结果”，冻结题目、答案和笔迹一起回放。只读白板的平移缩放不写库；无快照的旧历史保留原结果。当前调用链见 [代码地图](docs/code-guide.md)，早期契约见 [History Replay](quizforge-desktop-app/editor-web/draft-canvas/HISTORY_DRAFT_REPLAY.md)。

HTML SDK 2.3 使用 `QF.page.register` 加载、`QF.save` 保存、`QF.requestAction` 请求操作，公共内容、DOM、UI 和布局保留为辅助能力。八种扩展各含独立 editor/practice 页面、default.json、Schema、同步规则和真实样例题库。源码位于 `extensions/packages/`；判断题 2.3.1 为 11 文件手写模板，另外七型 2.3.0 使用源码构建流程。完整空配置流程见 [外部拓展测试](extensions/README.md)，开发见 [指南](extensions/DEVELOPMENT_GUIDE.md)，具体契约见 [接口参考](extensions/SIMPLE_PAGE_API.md)。

## 模块与目录

| 模块 | 职责 | 项目依赖 |
| --- | --- | --- |
| quizforge-core | 通用模型、扩展规则契约、内容/资源、业务流程与端口 | 无 |
| quizforge-infrastructure | 本地文件与 ZIP、SQLite/Flyway、Windows DPAPI、DeepSeek HTTP | core |
| quizforge-desktop-app | JavaFX 交互、内容组件和 Spring 组合入口 | core、infrastructure |

题目按 model/type/content/resource/source/service 分工。当前题型界面、模板、Schema 和评分规则位于 `extensions/packages`；原专项快照、题卡和废弃编辑/作答入口已删除。Core 保留题库编解码所需的值类型，其中 `CHOICE` 结构也供当前单选、多选、判断扩展使用。练习导航模型仅恢复通用状态，题型答案统一走扩展事务。桌面共用练习适配位于 `learning`，浏览器后端位于 `browser`，界面位于 `ui`，扩展管理与沙箱位于 `extension`，开发刷新位于 `dev`。`poc/sharedpractice` 仅保留独立演示。旧 extension-api、default-extensions、Material 和标准文档生成链路已退出构建。

## 文件与数据

全局数据默认在 `%USERPROFILE%\.quizforge`，可用 JVM 属性 `-Dquizforge.dataDir=<path>` 指定独立目录。全局 quizforge.db 保存工作区登记与 AI 配置；密钥通过 Windows DPAPI 加密到 secrets/，配置只保存凭据引用。

新工作区创建 sources/、documents/、question-banks/ 和 .quizforge/。默认文件夹只是分类，资产可放在工作区其他目录。已有 materials/ 等目录和文件保留；列出最近工作区不重建缺失目录，实际打开检查 manifest 与根目录。

工作区 .quizforge/workspace.db 是可重建资产索引，.quizforge/quizforge.db 保存练习与历史。索引重扫不会删除作答；重复 assetId 的冲突题库禁止打开可写练习。

`.qbank` 是 ZIP，包含 manifest.json、bank.json、resources/。manifest.json 保存 schemaVersion 2.0、资产 ID、标题和资源表，bank.json 保存 stimuli/questions 及稳定题目/选项 ID。读取器把两部分合并为 QuestionBank 逻辑模型。TEXT 为直接文本，RICH 为既有结构化富文本，DOCUMENT 引用 resources/ 下的 Canvas 原生 JSON；资源由 ID 映射到包内路径与哈希。图片采用现有内容节点或 Canvas 内嵌图片；音频、视频和新的 RESOURCE discriminator 尚未实现。

普通 Markdown 首次用于来源时登记 quizforge 身份，用户选择正文块并创建 qf:anchor。旧 study-document 与 qf:id 仍可读。源码类型已改为 REGISTERED_MARKDOWN，索引适配器保留历史 STANDARD_DOCUMENT 数据库文本，既有 SQL 迁移不改写。

练习与历史统一显示“得分 / 总分”：得分累计当前已提交的最新评分结果，总分按全部题目的分值计算，多小题题型按小题计分，锁定提示不计分。草稿、重试中及未评分题不累计得分；旧历史缺少分值信息时显示“—”。主观题可保存参考答案与评分指导，目前提交结果为 UNSCORED，人工/AI 评分流程未实现。

## 开发文档

新增题型从独立 HTML 扩展模板开始，不需要为题型增加 Java 页面分支。各题型的产品要求见题型设计说明；缺少已安装扩展时只保留数据，不回退到旧组件。人工/AI 评分、页面直传分数和独立图片/视频公共接口仍未实现。

完整目录见 [开发文档导航](docs/README.md)。新人先读 [运行与接入指南](docs/new-developer-guide.md)，再沿 [代码地图与五条业务链路](docs/code-guide.md) 阅读实现。开发新题型使用 [五步模板](docs/templates/new-question-type.md)。题库协议统一维护在 [文件格式与内容资源](docs/qbank-format.md)，验证按新人指南选择与改动对应的入口。

每次修改同步实际行为、协议/兼容说明和相关测试。业务事实的保存放在核心流程与事务中，界面不建立第二条保存旁路。新增媒体、作答结构或评分机制时先扩展共享协议和历史基础，再接题型组件。
