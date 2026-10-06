# Practice Draft Mode v1 (transitional legacy)

> 2026-10-04 迁移状态：当前只启用新版 HTML SDK 2 单选/多选，旧题型专项实现已删除。本文公共白板、状态、事务和历史契约继续适用；七题型覆盖描述属于此前阶段。当前开发入口与 API 以仓库 extensions/SDK_README.md 为准。

本页记录旧的 NORMAL JavaFX / DRAFT WebView 设计，供旧 fixture 对照。正式默认已由 [Shared Learning Surface Unification v1](SHARED_LEARNING_SURFACE.md) 取代：七种 TEXT 题型共用 WebView / Renderer / DOM，仅切换 PRACTICE / DRAFT capability。

正式入口：QuizForge Desktop 打开题库，浏览当前 SINGLE_CHOICE / MULTIPLE_CHOICE + TEXT，右上角 **草稿 / 退出草稿**。切换只替换当前浏览区域，不创建 Stage、Workspace 或 Practice Session。开发 POC 启动脚本仍保留。两种题型由静态注册表和共享 Runtime 管理，见 [SHARED_RENDERER_CONTRACT.md](SHARED_RENDERER_CONTRACT.md)。

## UI 结构

```text
FilePane / QuestionBankFileView
  FileHeader                 草稿按钮，与编辑、历史操作并列
  QuestionPracticeLayout / MixedQuestionPracticeView
    readerColumn
      PracticeSurfaceHost    当前标签页拥有，NORMAL / DRAFT 不持久化
        normal               原 JavaFX Practice + ScrollPane
        draft                一个复用的 SharedPracticeCanvasWebView
        navigation / error   原导航命令、非破坏性错误
    QuestionOutlineView      原有大纲与原 Session
```

仅两种 TEXT Choice 显示入口。其他五种题型和结果汇总页隐藏入口；从 DRAFT 导航到这些页面时，保存旧题成功后恢复 NORMAL。原 JavaFX 编辑、练习及 History RESULT 页面继续使用现有渲染器。

## 唯一状态来源与切换

- `PersistentPracticeRuntime` 持有原有 Session，`refresh()` 经 `PracticeSessionService.loadActiveSession` 读取同一 ACTIVE，不创建 Session、不改变当前位置。
- 嵌入的 `SharedPracticeAdapter(runtime)` 从这一 runtime 生成 SharedPracticeViewModel；ANSWER_CHANGED / SUBMIT / RETRY / DRAFT_CHANGED 都回到同一 Core 服务。
- NORMAL → DRAFT：刷新 Core，加载当前卡片与按 sessionQuestionId 查到的 Active Draft；缺失则空白。复用 WebView 对象；同题刷新保留 operationSeq，题目/Session 变化则释放旧 JS scope 后重新加载本地页面。
- DRAFT → NORMAL：禁止交互，结束当前手势，等待正在执行的 Submit 与卡片 mutation，再 flush DraftAutosave；收到 SQLite ACK 后刷新 runtime 和 JavaFX，切回 NORMAL。
- flush 失败：恢复画布交互，留在当前 DRAFT 和当前题，保留内存笔迹，错误显示在 Surface Host 中。关闭页、替换预览页、关闭 Workspace 也有保存预检；失败不释放页面。

## 导航与隔离

大纲、上一题、下一题进入同一个导航 barrier：flush 旧题 → ACK → 释放旧页面 scope → 原 Core 导航命令 → 新题卡 + 新题 Active Draft。SQLite 主键仍是 `session_question_id`。两题互不共享画布；切回恢复其笔迹、viewport 和题卡几何。

DOM 不进入 Draft JSON。题目快照、作答和草稿文档仍分别由 QuestionSnapshot、draftAnswer、ActiveDraftCanvas 表示。Surface Host 不维护另一套 currentQuestion/答案。

旧 RICH 选择题预览不在现有可作答 Session 中，继续使用原富文本渲染器，草稿入口隐藏。导航到预览先保存原题，保留 Core 当前可作答题；返回真实可作答题时再按其 Session 状态恢复，避免显示另一题的草稿。

## Submit / Retry

继续使用共享卡片现有二次确认和 Draft 保存 barrier。确认提交后，Core 在同一 SQLite 事务追加 QuestionAttempt 和 AttemptDraftSnapshot，并删除 Active Draft；前端显示真实结果。Retry 调用原 Core 命令，答案与当前画布清空，旧 attempt 和 frozen snapshot 不变。

退出/导航会等待进行中的 Submit 结束。提交进行中关闭页面会被保护，避免释放桥接后继续提交。

## 生命周期

每个打开的 Practice 标签页最多懒创建一个 Draft WebView。NORMAL 隐藏、DRAFT 显示；重复切换不注册重复桥接事件。切题释放旧 JS scope 和 operationSeq 队列；页面关闭、预览页替换、题库重新打开或 Workspace 切换释放 JS bridge 和 WebEngine 页面。

窗口退出先通过 MainWorkspaceView.prepareExit 的保存检查。Tab close/closeAll 同样预检。正常页面尚未进入 Draft 时不创建 WebView。

## 验证

`PracticeDraftModeUiTest` 使用正式 FilePane/Shell、真实 WebKit、Core 和临时 SQLite，覆盖状态同步、反复切换、逐题隔离、失败退出/导航/关闭、立即提交最后笔迹、Retry 与旧快照不变。

详细命令、结果、固定人工 Workspace 和限制见 [PRACTICE_DRAFT_MODE_ACCEPTANCE.md](PRACTICE_DRAFT_MODE_ACCEPTANCE.md)。

## History Replay 边界

后续已接入 [History Draft Replay v1](HISTORY_DRAFT_REPLAY.md)：正式历史详情按 `attemptId` 读取不可变冻结快照，RESULT / DRAFT 原地切换，复用同一 Canvas Core、单选 renderer 和 CSS。History 仅是只读投影，不访问 Active Draft、不自动保存、不重新答题；本页说明的 Active Practice 语义继续沿用。
