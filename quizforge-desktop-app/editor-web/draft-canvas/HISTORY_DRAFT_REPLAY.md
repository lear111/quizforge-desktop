# History Draft Replay v1

正式题库的 **历史记录 → 详情** 中，在当前 Attempt 存在冻结草稿时显示 **草稿 / 返回结果**。浏览区域原地切换 `HistorySurfaceMode.RESULT / DRAFT`，不创建 Stage 或新 Workspace。支持 `SINGLE_CHOICE / MULTIPLE_CHOICE + TEXT`；其他题型保留原历史展示。两者通过 [Shared Renderer Contract](SHARED_RENDERER_CONTRACT.md) 使用与 Active 相同的题型实现。

## 三种状态

| 状态 | 身份 | 所有者 | 读写规则 |
| --- | --- | --- | --- |
| Active Draft | `sessionQuestionId` | 当前练习 | mutable working state，保留原自动保存、提交和重试语义 |
| Attempt DraftSnapshot | `attemptId` | 已提交作答 | immutable historical state，提交事务冻结后不可覆盖 |
| History Replay | 当前选中的 `attemptId` | 历史详情页 | read-only projection，查看中的平移缩放只保留在 WebView 内存 |

```text
Question
  Attempt #1 → AttemptDraftSnapshot #1
  Attempt #2 → AttemptDraftSnapshot #2
  Attempt #3 → AttemptDraftSnapshot #3
```

不能使用 `Question → DraftSnapshot` 模型。缺失快照不是空白草稿：隐藏入口，不创建假 document。

## 数据与 capability 边界

```text
PracticeHistoryDetailView：既有题目/Attempt 上下次按钮
  HistoryDraftAdapter：只持有冻结 detail、History service 和 bankAssetId
    PracticeHistoryService.loadDraftReplay(bankAssetId, sessionId, sessionQuestionId, attemptId)
      校验归档轮次、题库、题目、Attempt 归属
      AttemptDraftSnapshotRepository.find(attemptId)
        DraftCanvasDocument v1.0 / layout v1
    SharedPracticeViewModel：冻结 stem/options/correct IDs/analysis + 选中 Attempt 的 answer/result/score
  HistorySurfaceHost：RESULT / DRAFT、懒加载和选择版本隔离
    HistoryDraftWebView：本地 history-replay.html；仅 ReadyHost.ready()
      readHistoryReplay → Shared Runtime → static renderer registry → Choice renderer(READ_ONLY_HISTORY)
                       + Canvas Core(READ_ONLY)
```

JavaFX View 不查询 SQLite。History service 不访问 `activeDrafts`，Adapter 不持有 current bank 或 Practice runtime；不会以当前 `.qbank` 替换历史题干、答案或分数。无新增 SQLite migration，沿用 V6。

History 页面不暴露 `practiceHost`、`sharedPractice`、保存、提交、重试 bridge，也不创建 Practice channel / DraftAutosave。Radio / checkbox disabled，无 Submit/Retry 按钮，无 Answer change listener。表单只拦截原生提交导航，不生成业务 intent。

Canvas `READ_ONLY` 只允许 PAN、Zoom、Fit；拒绝 PEN/ERASER/INTERACT，隐藏并禁用 Clear、Undo/Redo、JSON 导入导出，拦截 Ctrl+Z，所有文档变化都不通知 `onChange`。查看时 `viewport` 可变，始终不发送 DRAFT_CHANGED，不调用 `saveActiveDraftCanvas`，不写 Active 或 Frozen 表。

## 切换和生命周期

- `RESULT → DRAFT → RESULT` 不改变当前 Attempt。Attempt 控件在 DRAFT 时移到视口上方，RESULT 时回到原结果卡位置。
- 切 Attempt/Question：立即清空旧题卡和 SVG 笔迹，递增 selection version，然后载入新 Attempt 的卡片及草稿；延迟 ready 回调不得覆盖后续选择。
- 新选择有快照则保持 DRAFT；缺失则回 RESULT 并隐藏入口。损坏或不支持的快照显示错误，仍可返回 RESULT。
- 每个 History Detail 懒创建一个 WebView；切模式、Attempt、Question 复用。返回列表、关闭 Tab、切 Workspace、替换题库/归档详情时 destroy JS bridge、清页面并释放详情引用，不 flush、不保存。

## 几何与严格格式

题卡使用快照的 `questionCard.width / x / y`、原 world-coordinate strokes 和 viewport。窗口 resize 不重新计算逻辑宽度；适配使用平移缩放。

History HTML 直接引用 `shared-practice.css`，Canvas Core 和题卡 renderer 与 Practice 共用；无 History CSS 副本。JSON 在 Java codec 和 History JS 入口都校验格式与版本，额外字段也报错；不升级、不丢弃字段、不重写快照。解码失败不影响原 RESULT 渲染。

## 限制与后续接口

当前只覆盖 SINGLE_CHOICE + TEXT。共享基础字体栈与 CSS 不等于固定字体二进制；系统字体、WebKit 和未来 renderer 排版变化仍可能影响笔迹与文字的相对位置。本阶段未解决长期跨平台排版一致性。

后续类型迁移需要明确 renderer 的 `loadActive / loadHistory`、`setReadOnly`、`getViewState`、`destroy` 接口，以及内容资源解析和 mutation capability 的边界。Canvas 继续只负责 world geometry/tools，题型 renderer 负责语义与卡片布局。本阶段不实施其他六种题型迁移。

测试和正式工作区验收见 [HISTORY_DRAFT_REPLAY_ACCEPTANCE.md](HISTORY_DRAFT_REPLAY_ACCEPTANCE.md)。
