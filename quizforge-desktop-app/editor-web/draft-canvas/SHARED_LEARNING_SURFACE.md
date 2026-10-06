# Shared Learning Surface Unification v1

> 2026-10-04 迁移状态：当前只启用新版 HTML SDK 2 单选/多选，旧题型专项实现已删除。本文公共白板、状态、事务和历史契约继续适用；七题型覆盖描述属于此前阶段。当前开发入口与 API 以仓库 extensions/SDK_README.md 为准。

正式题库练习与历史详情使用 Shared Learning Surface，覆盖七种内置题型的 TEXT、RICH 和 Canvas DOCUMENT 题干、选项、参考答案与解析。Java 21 编译基线不变；历史的两种视图共用只读 Renderer、题卡 DOM 和注释层。

富文本通过同一题卡 DOM 显示，保留字体、字号、颜色、段落对齐、表格及内嵌图片；不额外创建 WebView。文档使用连续阅读布局，去除纸张分页及页边距，表格与图片适应题卡宽度。完形标签和翻译句子标记可跨样式片段解析。资源读取来自快照拥有的字节，历史回放不读取当前题库。译文新输入仍为多行文本；已有 DOCUMENT 译文可只读展示，未修改的答案文档保留。

作文正式答案在同一 WebView 内复用现有 Canvas Editor，按需加载一次本地 `essay-editor.js`。默认可以直接输入 Arial / 16px / 黑色文字，粘贴为纯文本；通过“编辑格式”显示字体、字号、颜色、字形及段落对齐工具，关闭工具栏保留已有格式。正常输入不会继承已选加粗等字形，也不清除已有文档的格式。ANSWER_CHANGED 携带 `essayText` 和完整 `essayDocument`，经现有 EssayPracticeAnswer / Core 事务保存；切题、关闭、提交前 flush，History 与提交后的文档只读，Retry 清空。格式开关只控制编辑能力，不重建编辑器。Draft 的笔迹和视口继续单独保存。页内 CSP 仅为原生引擎增加本地 blob/data Worker 能力，网络连接仍禁止。

完形正文空位使用紧凑的 `1._______` 文字按钮，继承原段落字体和行高；已选单词带下划线，并与下方单选项共用同一答案状态。选项弹层挂载到 body，以屏幕坐标定位、边缘翻转和滚动显示，不进入 World 排版，不撑开题卡。提交及历史只读时禁用空位，显示 Core 提供的反馈；换题、销毁、切换工具或视口时关闭弹层并清理监听。

```text
Practice page → PracticeSurfaceHost → one SharedPracticeCanvasWebView
                                      → mountSharedLearningSurface
                                         ├── QuestionRenderer / shared runtime
                                         ├── AnnotationLayer
                                         ├── World / Viewport
                                         └── ModePolicy
```

## 能力与生命周期

| 模式 | 答案 | 笔迹 | 视口 | 工具箱 |
| --- | --- | --- | --- | --- |
| PRACTICE（打开默认） | 可选择、输入、提交、重试 | 可见；禁止新增、擦除、清空、Undo/Redo | 系统计算居中视口；长题允许阅读滚动 | 隐藏 |
| DRAFT | Interact 下作答，保留 Submit/Retry | 未提交时可画、擦除、Undo/Redo/Clear | 用户 Pan/Zoom/Fit；恢复已保存视口 | 保留现有工具 |
| HISTORY / PRACTICE（默认） | 归档答案/结果，只读 | 归档笔迹和白板文本可见，只读 | 使用与正式练习相同的固定视口及阅读滚动 | 隐藏 |
| HISTORY / DRAFT | 与默认历史视图相同，只读 | 同一份笔迹和白板文本，只读 | 恢复归档视口，可拖动及 Ctrl+滚轮缩放 | 仅拖动和缩放 |

提交后 DRAFT 不可改笔迹，但仍可查看、缩放和重试。Submit/transition barrier 期间视口也暂时锁定。Revision 保留 Core 现有规则；没有新增复制旧草稿。

历史详情不再使用旧 JavaFX 结果题卡。进入即加载冻结题卡和注释，`草稿 / 返回练习` 只切换显示视口，不替换 DOM 或移除笔迹。已提交答案绑定当次 AttemptDraftSnapshot；未提交题目读取已归档 sessionQuestion 的最终草稿（服务先检查归档状态和所属题库，归档后 Core 禁止再修改）。没有白板记录时使用空注释层，不伪造提交；缺少新版内容快照或损坏格式显示不可用，不回退当前题库或旧结果卡。作答次数切换、题目大纲与来源跳转保留。

切换 PRACTICE ⇄ DRAFT 只调用能力切换。同一 World、Renderer、`#question-card` 和 `#practice-form` DOM 留在原处；不存在第二份题卡状态。尚未发送的文本输入先 flush，ANSWER_CHANGED ACK 保留当前 Renderer/DOM；提交和重试的结构性结果更新可重建 Renderer。换题可以替换 Renderer，但复用 WebView 和 JS page scope。

## 两套视口

DraftCanvasDocument v1 的 `viewport` 继续表示用户 draftViewport；序列化 schema 不变。

临时 practiceViewport 根据容器尺寸、card `{x,y,width}` 与实时内容高度计算：左右居中，短题卡上下居中，长题卡保留顶部 20px 阅读间距，zoom 不超过 1。阅读滚动和同父题 focusTarget 只改临时视口。不会写入 DraftCanvasDocument、改变逻辑题卡宽度、移动 world 笔迹或产生空草稿写库。

每道题没有草稿记录时，首次进入 DRAFT 从 practiceViewport 初始化并保存用户视口。悬浮工具栏不占布局高度，因此题卡保持练习时的屏幕位置。已有记录时直接恢复保存的 viewport，包括关闭后重新打开，不重新居中。Java→JS 的恢复载荷通过 `hasSavedDraft` 区分缺失记录与合法的零偏移视口。

练习末尾的统计卡片在现有学习浏览器中显示，沿用前一题的纸张底色和纹理，卡片居中。上一题箭头复用视口边缘导航，返回最后一道题；统计页隐藏下一题箭头。进入统计页后题目 scope 已挂起，关闭页面不会再读取或保存挂起的题目答案。

## 悬浮白板工具栏 v1

左上撤销/重做，中上选择（INTERACT）、拖动（手型图标，PAN）、画笔、橡皮、直线、矩形和文本，右上“更多”，右下单一缩放控件。拖动模式在题卡和文本上也移动整张白板，拖动中按动画帧合并视口绘制，避免每个 pointermove 深复制整份笔迹。Ctrl+滚轮围绕鼠标位置缩放，缩放百分比可恢复 100%。没有可见的保存、导出或 JSON 工具。工具栏悬浮于视口，不改变 World 原点。

更多包含浅灰、白色、纸黄色、米色、浅绿底色及空白/横线/点式/格子样式。纸张纹理随 camera 平移和缩放。直线和矩形保存为普通 PEN 折线，沿用撤销、橡皮和旧笔迹读取。点击文本工具后在白板点选位置输入，Ctrl+Enter 或失焦完成，Escape 取消；选择模式下可拖动、双击编辑、Delete 删除文本。

PRACTICE 与 DRAFT 共用当前草稿的 `paper` 设置；历史两种模式使用对应快照的相同设置。未保存 `paper` 时统一显示纯白空白背景，不补写或改写旧文档。模式切换只改变视口与操作能力，不覆盖纸张底色或隐藏纹理。

DraftCanvasDocument 保持 schemaVersion=1.0/layoutVersion=1，新增可选 `texts`（id,x,y,width,size,color,text）和 `paper`（color,pattern）。旧文档无扩展字段仍按原样解码和编码；文本和纸张随 ActiveDraft 与不可变 AttemptSnapshot 保存，历史只读显示。新增注释不写入正式答案。撤销/重做同时覆盖笔迹、文本及纸张设置，不恢复 camera。

题卡与 SVG 共用 `screen = (world + viewportOffset) * zoom`。题卡附近的笔迹保持几何关系；远端笔迹在 PRACTICE 中可能不在屏幕内，但不会丢失。

## Draft source 与事务

`PersistentPracticeRuntime.loadDisplayedDraftCanvas` / `SharedPracticeAdapter.loadDisplayedDraft`：

- UNANSWERED / DRAFT / RETRYING / REVISING：ActiveDraftCanvas，缺失时为空。
- SUBMITTED：当前题最新 AttemptDraftSnapshot，缺失时为空。
- Retry：新 working answer 与 visible ink 清空；旧 Attempt/Snapshot 不变。

提交：pending answer → final stroke / draft flush → ACK → Core submit transaction → Attempt + immutable Snapshot + delete Active → authoritative response 恢复 Frozen doc。保持 operationSeq、FIFO 和 session/question 身份检查。PRACTICE 无修改不额外写库。

导航先 flush、隐藏旧 card、切 Core 当前题，再原子替换新题 DTO 与该题文档并显示；避免旧笔迹短暂出现在新题上。题目之间保留当前 mode；关闭后重开默认 PRACTICE，恢复该题权威答案和草稿。

同父题 Outline 使用 stable target ID，不新建题卡、WebView、Draft identity。PRACTICE focus 调整临时阅读视口；DRAFT focus 按原合同调整并保存 draftViewport。

Tab/QBank 关闭、切换 Workspace、Application exit 调用 prepareClose/saveBeforeClose/destroy。加载中可取消关闭；真正保存或提交中仍有保护。销毁移除 Worker listener、JS bridge/runtime/canvas handlers 与 ResizeObserver、清空待处理 callback、释放 Host 引用并加载空页。

## 诊断与 legacy

`SharedPracticeCanvasWebView.liveViewCount()`、`sharedPractice.diagnostics()`、`draftCanvas.diagnostics()` 提供 WebView、renderer instance/mount、bridge handler/binding、runtime/canvas handler 数量。可选 `-Dquizforge.learning.diagnostics=true` 输出模式切换资源计数，不输出用户答案。50 次切换必须保持计数不变。

旧 JavaFX QuestionBankPracticeView、各 QuestionCardView、QuestionPracticeLayout、LegacyPracticeSurfaceHost / NormalPracticeSurface / DraftPracticeSurface / PracticeSurfaceMode 暂留，属于 transitional legacy。`-Dquizforge.practice.legacyUi=true` 仅显式选择比较/回归路径；正式应用不设此属性。旧测试 fixture 显式启用 legacy；SharedLearningSurfaceWebViewTest / SharedLearningUiTest 测真正生产默认路径。

后续 cleanup 应单独检查旧 router 分支、legacy host/surface、题卡及 fixture 的依赖；本阶段不删除。未知内容或缺失文档资源仍使用显式 unsupported/transitional 处理。本阶段没有做完整 Memory Audit。

验收记录见 [SHARED_LEARNING_ACCEPTANCE.md](SHARED_LEARNING_ACCEPTANCE.md)。
