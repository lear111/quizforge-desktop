# History Learning Surface

> 2026-10-04 迁移状态：当前只启用新版 HTML SDK 2 单选/多选，旧题型专项实现已删除。本文公共白板、状态、事务和历史契约继续适用；七题型覆盖描述属于此前阶段。当前开发入口与 API 以仓库 extensions/SDK_README.md 为准。

历史详情复用正式练习的七种题型 Renderer、富文本、题卡 DOM、World 和注释层。旧 JavaFX 历史结果题卡已移除。默认打开 PRACTICE 视图，笔迹和白板文本立即可见；右上角“草稿 / 返回练习”只切换视口能力，不更换题卡、不清除注释。

| 历史视图 | 题卡与注释 | 视口 | 工具 |
| --- | --- | --- | --- |
| PRACTICE（内部 HistorySurfaceMode.RESULT） | 只读 | 使用快照保存的纸张底色和纹理，固定居中，长题阅读滚动 | 隐藏 |
| DRAFT | 相同 DOM 和 World 内容，只读 | 恢复快照视口，允许拖动及 Ctrl+滚轮缩放 | 只读拖动、缩放 |

## 数据来源

- 已提交题目：冻结内容快照、当前选中的 Attempt 答案/结果/得分、该 Attempt 的 DraftSnapshot。不同作答次数独立读取，不覆盖其他次数。
- 未提交题目：冻结内容快照、归档时的正式答案草稿、该 sessionQuestion 的最终白板草稿。`PracticeHistoryService.loadFinalDraftReplay` 先检查 session 已归档、题库和题目归属；归档后 Core 禁止修改，重开练习创建新的 sessionQuestion，不复用旧草稿。
- 没有保存过白板的题目：新 Renderer 搭配空注释层，保留真正的未提交状态，不伪造 Attempt。
- 缺少新版内容快照、损坏或不支持的格式：显示不可用，不回退到旧题卡，不用当前 `.qbank` 内容修补历史。

```text
PracticeHistoryDetailView: 题目 / 作答次数 / 大纲 / 来源导航
  HistoryDraftAdapter: 归档身份及题目、答案、结果投影
    PracticeHistoryService: 校验归属，读取归档草稿
  HistorySurfaceHost: 一个 WebView，两种只读显示视口
    HistoryDraftWebView: 本地 history-replay.html，仅 ReadyHost.ready()
      readHistoryReplay → Shared Runtime → 七种静态 Renderer
                       + Canvas Core(READ_ONLY)
```

历史默认页和草稿页均使用 `shared-practice.css`。提交反馈、选中标记、完形空位、排序反馈及富文本答案沿用正式题卡，Radio / checkbox / textarea 只读，没有提交、重试或格式编辑功能。前后题箭头在两个视图中均位于视口两侧。

最后一道题右侧箭头进入本轮得分统计页，展示归档得分、总分及正确/错误/未作答/未评分数量。统计页沿用前一题的纸张背景与居中布局，只显示上一题箭头，返回最后一道题；隐藏草稿开关和作答次数控件。大纲仍可直接返回任意题目。统计读取 `PracticeHistoryDetail.summary`，不随所选 Attempt 改变，也不根据当前题库重算。

## 生命周期与只读边界

模式切换仅调用 `canvas.setLearningMode`，不重建 DOM、不重新读取快照。切题或切作答次数立即清空旧题卡和注释，然后加载对应内容；selectionVersion 阻止延迟回调串题。每个历史详情只创建一个 WebView，返回列表、关闭 Tab 和切工作区时销毁。

History 不暴露 Practice mutation bridge，不创建 DraftAutosave，不写答案、笔迹或快照。查看中的拖动和缩放只修改内存 camera。Canvas 的 `READ_ONLY` 始终禁止绘画、擦除、文本编辑、纸张编辑、撤销、清空和导入；PRACTICE 支持只读题卡内容及阅读滚动，DRAFT 支持拖动和缩放。

文档保持 schemaVersion=1.0/layoutVersion=1，沿用 V6 数据库，没有新增 migration。JSON 保留并校验 strokes、texts、paper、questionCard、viewport，未知字段不会被静默丢弃或重写。题干、选项和文档资源来自归档内容快照，不依赖当前题库。
