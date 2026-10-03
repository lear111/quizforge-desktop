# Practice Draft Mode v1 验收

日期：2026-10-03。基线 HEAD：`55f6be0e91670dd1358f48bd237d00ccd93a088a`。本里程碑保持原有未提交修改与两个 stash，没有 commit / push。

## 自动验证

仓库根目录，JBR 21：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
npm.cmd --prefix quizforge-desktop-app/editor-web/draft-canvas test
npm.cmd --prefix quizforge-desktop-app/editor-web/draft-canvas run build
mvn.cmd -B '-Dquizforge.build.directory=target/draft-mode-final' '-Dtest=PracticeDraftModeUiTest,DraftPersistenceWebViewTest,SharedPracticeAdapterTest,PracticeMutationQueueTest,DraftCanvasValueTest,DraftCanvasPersistenceTest,ActivePracticeSessionIntegrationTest,PersistentPracticeRuntimeIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn.cmd -B '-Dquizforge.build.directory=target/draft-mode-final' '-Dtest=PracticeDraftModeUiTest,EssayEditorUiTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn.cmd -B '-Dquizforge.build.directory=target/draft-mode-final' test
```

- Frontend：42 / 42 通过；本地 bundle 构建成功。日志：`target/draft-mode-frontend-test.log`、`target/draft-mode-build.log`。
- 首轮 Targeted Java：130 / 130 通过（Core 3、Infrastructure 109、Desktop 18），`BUILD SUCCESS`。日志：`target/draft-mode-targeted-verified.log`。
- 修复全量发现的旧 RICH 预览导航回归后，定向 UI 回归 20 / 20 通过（正式 Draft 6、EssayEditor 14），`BUILD SUCCESS`。日志：`target/draft-mode-preview-recheck.log`。原选择中增加的第六个正式用例也在最终全量中通过。
- 最终全量 Maven：685 / 685 通过（Core 73、Infrastructure 324、Desktop 288），Failures 0、Errors 0、Skipped 0，`BUILD SUCCESS`；耗时 10:27，完成于 2026-10-03 13:25:03 +08:00。日志：`target/draft-mode-full-complete.log`。
- 最终全量的 `DesktopStartupTest` 通过（1 / 1，7.518 秒），前一轮也通过（34.14 秒）；未重复上一阶段的启动超时，40 秒阈值未改动。

原生 JavaFX 测试未提高 timeout。测试使用临时 Workspace/SQLite，人工验收使用下述既有 Workspace。

本轮发现并修复了加载完成判断中的回归：WebKit 对本地 URL 的规范化使严格地址匹配不可靠，改为忽略已释放的空页，并在加载成功后校验实际 JS bridges。POC 冷启动也出现过 20 秒初始化超时；线程采样显示 WebView 构造处的本地 classpath 文件读取。测试现在先初始化原生窗口，再附加 WebView，保持每步原有 20 秒限制；三个 POC 持久化用例和六个正式页面用例最终全部通过。诊断及修复前日志保存在 ignored `target/draft-mode-{thread-diagnostic.txt,load-recheck.log,targeted-final.log}`。

补充 UI 回归中的 `PracticeWorkflowUiTest.editSaveThenPracticeRunsRevisionSyncAndPreservesOnlyNonSemanticAnswers` 曾出现既有 FX 调度的 40 秒等待超时，保持超时值不变，串行独立复跑该用例通过（1 / 1，16.61 秒；`target/draft-mode-recheck-serial.log`）。它与上一阶段的 `DesktopStartupTest` 不是同一个测试；最终全量的 PracticeWorkflow 26 / 26 通过。

中间全量发现 `EssayEditorUiTest.richChoiceOptionsInMixedBankRenderWithoutTextCast` 的真实导航回归：旧 RICH 预览不属于持久化 Practice 题目，不能对它调用 goTo(-1)。已保留原预览渲染，隐藏不适用的草稿入口，并修正混合题库的显示位置映射。`MarkdownFileUiTest` 的页头按钮断言同步更新为编辑、历史、草稿三项。最终这两个类分别 14 / 14、12 / 12 通过。发现确定回归后的中间全量被停止，最终全量完整执行。

`PracticeDraftModeUiTest` 覆盖：

| 正式页面场景 | 验证 |
| --- | --- |
| NORMAL → DRAFT → NORMAL | 当前题、Session 不变，无新窗口；B 带入，Web 改 C 后 JavaFX 显示 C |
| 重复切换与关闭重开 | 同一个 WebView，单次选择只发送一次 event，关闭时 destroy，重开恢复 Draft |
| 大纲和箭头切题 | 保存旧题后加载新题；Q1/Q2 隔离，切回恢复笔迹和 viewport |
| 保存失败 | SQLite 注入失败；退出、导航、关闭均被阻止，内存笔迹和当前题不变，解除失败后保存成功 |
| 最后一笔立即提交 | 在 debounce 之前提交；Frozen Snapshot 包含最后一笔，Active 已删除，JavaFX 显示真实结果 |
| Retry / 再次提交 | 空画布、空答案；旧 attempt/snapshot 不变，新 attempt 冻结独立草稿 |
| 混合题库 | 单选题共享同一正式 Surface；作文入口隐藏，返回单选恢复各自 Draft |
| 旧 RICH 选择题预览 | 原渲染保留，入口隐藏；不误加载另一道题的 Draft，返回可作答题恢复原笔迹 |

## 固定人工 Workspace

- `Step 3 Live Acceptance`，ID `b84a311b-a902-48c3-91e6-7aecfdc77b1c`。
- 既有题库：`question-banks/PracticeOutlineAcceptance.qbank`，没有创建新的人工作区或题库。
- 正式应用 `DesktopApplication`，使用当前编译类与本地资源，未调用 Draft/Shared Practice POC Launcher。
- Workspace 数据库：`C:\Users\wangg\.quizforge\workspaces\b84a311b-a902-48c3-91e6-7aecfdc77b1c\.quizforge\quizforge.db`。
- 用第 38 / 39 题作为相邻 Q1 / Q2；这是原题库未作答位置，保留此前已有 attempts 与题库文件。

通过 computer-use 原生鼠标操作、每步观察截图：

1. NORMAL 第 38 题选择 B，点击右上角草稿；仍是第 38 题且 B 已选择，Workspace/大纲不变。
2. Draft 改成 C，画两笔，平移视口到 `x=-97, y=-33, zoom=1`；退出后 JavaFX 显示 C。
3. 重进草稿；两笔和视口完整恢复。
4. 大纲切到第 39 题，空画布；画独立一笔，切回第 38 题恢复两笔。数据库同一 Session 两个 `session_question_id` 分别保存 2 / 1 笔。
5. 第 38 题增加第三笔，交互模式提交并二次确认。冻结文档含三笔，正式结果回答错误、0 / 1；退出 NORMAL 仍显示同一真实结果。
6. 重新进入，点击 Retry；肉眼空画布、空选择，Active Draft 行删除；原冻结快照所有字段不变。
7. 返回 NORMAL 并关闭本次启动的正式验收应用。

快照：`pa_eabe96f4-0eb8-4dc0-bef6-0b5743d56874`，Frozen Draft 三笔，SHA-256：

```text
7aabe78e4fc5a5836d4cb66228233e9197725e32051ad72378e09529f431f833
```

Retry 前后数据库比较确认该快照完全相同。只读验证证据保存在 ignored `target/draft-mode-manual-{A,isolation,frozen,result}.json`。

每个原生输入动作后必须刷新截图，人工提交包含工具切换和确认，因此不声称人工操作在 500ms debounce 之前完成；零等待的最后一笔 Submit barrier 由真实 WebKit 集成测试覆盖。

## 限制与阶段边界

- 只支持 SINGLE_CHOICE。共享卡片目前使用 TEXT 契约；其余六种题型仍由原 JavaFX / 富文本渲染器处理。
- 每个打开的 Practice 标签页最多一个懒创建 WebView；跨题复用 WebView 对象，但释放并重新加载 JS page scope 以隔离事件序号。
- Canvas 保留 World 中固定题卡逻辑宽度；较窄浏览区可平移、缩放或“适应窗口”。
- SUBMITTED 时 Active Draft 已删除；冻结笔迹留在 AttemptDraftSnapshot，ACTIVE 结果卡不会作为 History Replay 显示旧笔迹。
- History Draft Replay 未实现，History 两个视图没有修改；下一阶段从 attemptId 的冻结快照接只读渲染，不读取当前题库和 Active Draft。

## 本阶段文件

本里程碑相对开始时工作树修改已有文件 25 个，新增文件 5 个。

```text
M README.md
M docs/new-developer-guide.md
M quizforge-core/src/main/java/io/quizforge/core/practice/PersistentPracticeRuntime.java
M quizforge-core/src/main/java/io/quizforge/core/practice/PracticeSessionService.java
M quizforge-desktop-app/editor-web/draft-canvas/DRAFT_PERSISTENCE.md
M quizforge-desktop-app/editor-web/draft-canvas/README.md
M quizforge-desktop-app/editor-web/draft-canvas/SHARED_PRACTICE.md
M quizforge-desktop-app/editor-web/draft-canvas/src/practice/renderer.js
M quizforge-desktop-app/editor-web/draft-canvas/src/shared-practice-app.js
M quizforge-desktop-app/src/main/java/io/quizforge/desktop/poc/sharedpractice/PracticeMutationQueue.java
M quizforge-desktop-app/src/main/java/io/quizforge/desktop/poc/sharedpractice/SharedPracticeAdapter.java
M quizforge-desktop-app/src/main/java/io/quizforge/desktop/poc/sharedpractice/SharedPracticeCanvasWebView.java
M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/file/FileHeader.java
M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/file/FilePane.java
M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/file/FileView.java
M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/file/FileViewerRouter.java
M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/file/QuestionBankFileView.java
M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/practice/MixedQuestionPracticeView.java
M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/practice/QuestionBankPracticeView.java
M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/shared/QuestionPracticeLayout.java
M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/shell/MainWorkspaceView.java
M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/workspace/WorkspaceTabManager.java
M quizforge-desktop-app/src/main/resources/editor/draft-canvas/shared-practice.js
M quizforge-desktop-app/src/test/java/io/quizforge/desktop/poc/sharedpractice/DraftPersistenceWebViewTest.java
M quizforge-desktop-app/src/test/java/io/quizforge/desktop/ui/shell/MarkdownFileUiTest.java
A quizforge-desktop-app/editor-web/draft-canvas/PRACTICE_DRAFT_MODE.md
A quizforge-desktop-app/editor-web/draft-canvas/PRACTICE_DRAFT_MODE_ACCEPTANCE.md
A quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/practice/PracticeSurfaceHost.java
A quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/practice/PracticeSurfaceMode.java
A quizforge-desktop-app/src/test/java/io/quizforge/desktop/ui/shell/PracticeDraftModeUiTest.java
```

Git 工作树包含前一阶段的未提交文件，完整状态见下。开始前所有 39 个未提交路径已备份到 ignored `target/draft-mode-preexisting/`，用于区分原有修改与本里程碑增量，不改变 Git stash。

## git status --short

```text
 M README.md
 M docs/new-developer-guide.md
 M quizforge-core/src/main/java/io/quizforge/core/port/PracticeTransaction.java
 M quizforge-core/src/main/java/io/quizforge/core/practice/PersistentPracticeRuntime.java
 M quizforge-core/src/main/java/io/quizforge/core/practice/PracticeSessionService.java
 M quizforge-desktop-app/editor-web/draft-canvas/DRAFT_CANVAS_CONTRACT.md
 M quizforge-desktop-app/editor-web/draft-canvas/README.md
 M quizforge-desktop-app/editor-web/draft-canvas/SHARED_PRACTICE.md
 M quizforge-desktop-app/editor-web/draft-canvas/src/canvas/core.js
 M quizforge-desktop-app/editor-web/draft-canvas/src/practice/renderer.js
 M quizforge-desktop-app/editor-web/draft-canvas/src/shared-practice-app.js
 D quizforge-desktop-app/src/main/java/io/quizforge/desktop/poc/draftcanvas/contract/DraftCanvasDocument.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/poc/draftcanvas/contract/DraftLifecycleContract.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/poc/sharedpractice/PracticeMutationQueue.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/poc/sharedpractice/SharedPracticeAdapter.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/poc/sharedpractice/SharedPracticeCanvasLauncher.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/poc/sharedpractice/SharedPracticeCanvasWebView.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/poc/sharedpractice/SharedPracticeExample.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/file/FileHeader.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/file/FilePane.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/file/FileView.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/file/FileViewerRouter.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/file/QuestionBankFileView.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/practice/MixedQuestionPracticeView.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/practice/QuestionBankPracticeView.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/shared/QuestionPracticeLayout.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/shell/MainWorkspaceView.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/workspace/WorkspaceTabManager.java
 M quizforge-desktop-app/src/main/resources/editor/draft-canvas/draft-canvas.js
 M quizforge-desktop-app/src/main/resources/editor/draft-canvas/shared-practice.js
 M quizforge-desktop-app/src/test/java/io/quizforge/desktop/poc/draftcanvas/contract/DraftCanvasDocumentTest.java
 M quizforge-desktop-app/src/test/java/io/quizforge/desktop/poc/draftcanvas/contract/DraftLifecycleContractTest.java
 M quizforge-desktop-app/src/test/java/io/quizforge/desktop/poc/sharedpractice/SharedPracticeCanvasWebViewTest.java
 M quizforge-desktop-app/src/test/java/io/quizforge/desktop/ui/shell/MarkdownFileUiTest.java
 M quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/persistence/practice/SqlitePracticeTransaction.java
 M quizforge-infrastructure/src/test/java/io/quizforge/infrastructure/PracticePersistenceIntegrationTest.java
 M tools/Start-SharedPracticeCanvas.ps1
?? quizforge-core/src/main/java/io/quizforge/core/port/ActiveDraftCanvasRepository.java
?? quizforge-core/src/main/java/io/quizforge/core/port/AttemptDraftSnapshotRepository.java
?? quizforge-core/src/main/java/io/quizforge/core/practice/draft/
?? quizforge-core/src/test/java/io/quizforge/core/practice/draft/
?? quizforge-desktop-app/editor-web/draft-canvas/DRAFT_PERSISTENCE.md
?? quizforge-desktop-app/editor-web/draft-canvas/DRAFT_PERSISTENCE_ACCEPTANCE.md
?? quizforge-desktop-app/editor-web/draft-canvas/PRACTICE_DRAFT_MODE.md
?? quizforge-desktop-app/editor-web/draft-canvas/PRACTICE_DRAFT_MODE_ACCEPTANCE.md
?? quizforge-desktop-app/editor-web/draft-canvas/src/bridge/autosave.js
?? quizforge-desktop-app/editor-web/draft-canvas/test/autosave.test.js
?? quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/practice/PracticeSurfaceHost.java
?? quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/practice/PracticeSurfaceMode.java
?? quizforge-desktop-app/src/test/java/io/quizforge/desktop/poc/sharedpractice/DraftPersistenceWebViewTest.java
?? quizforge-desktop-app/src/test/java/io/quizforge/desktop/ui/shell/PracticeDraftModeUiTest.java
?? quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/persistence/practice/DraftCanvasJsonCodec.java
?? quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/persistence/practice/SqliteActiveDraftCanvasRepository.java
?? quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/persistence/practice/SqliteAttemptDraftSnapshotRepository.java
?? quizforge-infrastructure/src/main/resources/db/migration/V6__draft_canvas.sql
?? quizforge-infrastructure/src/test/java/io/quizforge/infrastructure/DraftCanvasPersistenceTest.java
```
