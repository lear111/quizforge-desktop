# QuizForge Desktop V2

Java 21 + JavaFX 本地桌面题库应用，独立于 V1。支持工作区文件管理、Markdown 编辑与命名来源引用，以及单选、多选、完形填空、阅读理解、段落排序、翻译、作文的编辑与练习。作答支持草稿、提交确认、重做和归档历史，统计统一使用得分。AI 仅保留设置、凭据和连接测试。

## 构建与运行

在本目录使用 JDK 21 与 Maven：

```powershell
mvn test
mvn install -DskipTests
mvn -pl quizforge-desktop-app javafx:run
# 或使用项目启动器
.\Start-QuizForge.cmd
```

根目录统一使用 `.cmd` 启动入口：`Start-QuizForge.cmd` 普通启动，`Start-QuizForge-LiveUi.cmd` 开发启动，两者均可双击。共用的启动实现位于 `tools/Start-QuizForge.ps1`，参数由 `.cmd` 原样传入，例如 `.\Start-QuizForge.cmd -LiveCss`。

普通启动使用包内 Canvas 页面，不要求 Node/npm。修改 Canvas 前端时：

```powershell
Set-Location quizforge-desktop-app/editor-web/canvas
npm ci
npm run build
# 回到项目根目录后启动开发模式
Set-Location ../../..
.\Start-QuizForge-LiveUi.cmd
```

LiveUi 合并 Java、CSS 与 Vite 更新，需要支持增强类重定义的 JBR 21。普通 JDK 21 可用于 Maven 编译与普通启动。详见 [开发热更新说明](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/development-live-update.md)。

Practice Draft Mode v1 已嵌入正式浏览区：当前七种正式 TEXT 题型右上角点击“草稿 / 退出草稿”，原地切换 JavaFX 题卡与 Draft Canvas。沿用当前 Session、作答状态及 Workspace SQLite，切题前等待保存确认，逐题恢复笔迹与视口。提交冻结、重试清空继续复用 Core 持久化流程。详见 [正式模式说明](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/editor-web/draft-canvas/PRACTICE_DRAFT_MODE.md) 和 [验收记录](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/editor-web/draft-canvas/PRACTICE_DRAFT_MODE_ACCEPTANCE.md)。独立开发入口 `.\tools\Start-SharedPracticeCanvas.ps1` 仍保留，默认使用 `target/draft-persistence-acceptance/practice.db`，可通过 `-Database` 指定隔离路径；正式页面不依赖它。

History Draft Replay v1 已接入正式历史详情：按当前 Attempt 显示“草稿 / 返回结果”，冻结题目、答案和笔迹一起回放。只读 Canvas 与 Practice 共用渲染器和 CSS，平移缩放不写库；无快照的旧历史继续显示原结果。详见 [History Replay 架构](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/editor-web/draft-canvas/HISTORY_DRAFT_REPLAY.md)。

Shared Renderer Coverage v1 已接入七种正式题型：SINGLE_CHOICE、MULTIPLE_CHOICE、READING、CLOZE、MATCHING、TRANSLATION、ESSAY。各题型在 Active 与只读 History 复用同一 Runtime 和 renderer，父题共用一个 Draft，内部小题由通用 focusTarget 定位。Core 仍负责判分与 Attempts，翻译/作文保持 UNSCORED，Shared 本轮仅保证 TEXT。详见 [Renderer Contract](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/editor-web/draft-canvas/SHARED_RENDERER_CONTRACT.md) 与 [验收](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/editor-web/draft-canvas/SHARED_RENDERER_COVERAGE_ACCEPTANCE.md)。

## 模块与目录

| 模块 | 职责 | 项目依赖 |
| --- | --- | --- |
| quizforge-core | 通用模型、具体题型规则、内容/资源、业务流程与端口 | 无 |
| quizforge-infrastructure | 本地文件与 ZIP、SQLite/Flyway、Windows DPAPI、DeepSeek HTTP | core |
| quizforge-desktop-app | JavaFX 交互、内容组件和 Spring 组合入口 | core、infrastructure |

题目按 model/type/content/resource/source/service 分工。客观题在 objective/choice、cloze、reading、matching，主观题在 subjective/essay、translation；桌面题型组件使用对应包结构。桌面 UI 按 shell/workspace/file/markdown/question/content/ai/shared 组织，开发刷新在 dev。旧 extension-api、default-extensions、Material 和标准文档生成链路已退出构建。

## 文件与数据

全局数据默认在 `%USERPROFILE%\.quizforge`，可用 JVM 属性 `-Dquizforge.dataDir=<path>` 指定独立目录。全局 quizforge.db 保存工作区登记与 AI 配置；密钥通过 Windows DPAPI 加密到 secrets/，配置只保存凭据引用。

新工作区创建 sources/、documents/、question-banks/ 和 .quizforge/。默认文件夹只是分类，资产可放在工作区其他目录。已有 materials/ 等目录和文件保留；列出最近工作区不重建缺失目录，实际打开检查 manifest 与根目录。

工作区 .quizforge/workspace.db 是可重建资产索引，.quizforge/quizforge.db 保存练习与历史。索引重扫不会删除作答；重复 assetId 的冲突题库禁止打开可写练习。

`.qbank` 是 ZIP，包含 manifest.json、bank.json、resources/。manifest.json 保存 schemaVersion 2.0、资产 ID、标题和资源表，bank.json 保存 stimuli/questions 及稳定题目/选项 ID。读取器把两部分合并为 QuestionBank 逻辑模型。TEXT 为直接文本，RICH 为既有结构化富文本，DOCUMENT 引用 resources/ 下的 Canvas 原生 JSON；资源由 ID 映射到包内路径与哈希。图片采用现有内容节点或 Canvas 内嵌图片；音频、视频和新的 RESOURCE discriminator 尚未实现。

普通 Markdown 首次用于来源时登记 quizforge 身份，用户选择正文块并创建 qf:anchor。旧 study-document 与 qf:id 仍可读。源码类型已改为 REGISTERED_MARKDOWN，索引适配器保留历史 STANDARD_DOCUMENT 数据库文本，既有 SQL 迁移不改写。

练习与历史统一显示“得分 / 总分”：得分累计当前已提交的最新评分结果，总分按全部题目的分值计算，多小题题型按小题计分，锁定提示不计分。草稿、重试中及未评分题不累计得分；旧历史缺少分值信息时显示“—”。主观题可保存参考答案与评分指导，目前提交结果为 UNSCORED，人工/AI 评分流程未实现。

## 开发文档

本阶段补齐考研英语题型、按实际顺序展开小题的大纲、整张题卡拖动排序，以及 Canvas 对齐、首行缩进和只读预览缓存。小作文复用 `ESSAY`；人工/AI 评分仍未实现。可直接打开 `examples/qbank-v2` 中四个新增题型的示例包验证编辑、练习与历史。

完整目录见 [开发文档导航](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/README.md)。新人从 [项目结构与新增题型指南](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/new-developer-guide.md) 开始，实施新题型时填写 [五步模板](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/templates/new-question-type.md)。题库协议统一维护在 [文件格式与内容资源](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/qbank-format.md)。

每次修改同步实际行为、协议/兼容说明和相关测试。业务事实的保存放在核心流程与事务中，界面不建立第二条保存旁路。新增媒体、作答结构或评分机制时先扩展共享协议和历史基础，再接题型组件。
