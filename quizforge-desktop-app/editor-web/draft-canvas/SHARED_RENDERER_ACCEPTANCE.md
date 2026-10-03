# Shared Renderer v1 / MULTIPLE_CHOICE Acceptance

验收日期：2026-10-03。正式基线 HEAD `55f6be0e91670dd1358f48bd237d00ccd93a088a`。开始时已有 75 行折叠的 `git status --short`；本次在原有未提交工作之上追加，没有 commit/push/stash，也没有撤销继承改动。开始时文件副本与状态保存在忽略目录 `target/renderer-v1-preexisting/`。

## 自动覆盖

| 请求编号 | 检查 | 自动证据 |
| --- | --- | --- |
| 1–3 | SINGLE/MULTIPLE 静态解析；unknown unsupported | `renderer-contract.test.js`；`MultipleChoiceDraftUiTest` unsupported UI |
| 4–5 | destroy；ACTIVE/READ_ONLY 能力 | `renderer-contract.test.js`；`MultipleChoiceDraftUiTest` 旧控件失效、正式 host 释放、History 不发事件 |
| 6–9 | SINGLE 选择、提交/重试、History readonly、Draft replay | `SharedPracticeAdapterTest`、`SharedPracticeCanvasWebViewTest`、`HistoryDraftReplayUiTest` |
| 10–13 | MULTIPLE 空、A、A+B、取消 A | `renderer-contract.test.js`、`MultipleChoiceDraftUiTest` |
| 14–15 | Core draft round trip、关闭后重开恢复 | `SharedMultipleChoiceAdapterTest`、`MultipleChoiceDraftUiTest` |
| 16–17 | 提交与真实 Core 结果 | 同上，SQLite 事务与正式 WebView 的结果一致 |
| 18–20 | Retry 清选择/Canvas，第二次创建 RETRY Attempt | 同上，验证旧快照未变 |
| 21–23 | History 多选、readonly、每次 Attempt 独立快照 | 同上，正式 HistorySurfaceHost 使用同一 renderer |
| 24–26 | RESULT/DRAFT 双向切换与旧快照不变 | `MultipleChoiceDraftUiTest`、`HistoryDraftReplayUiTest` |
| 27 | SINGLE/MULTIPLE 每题隔离 | `MultipleChoiceDraftUiTest`，复用 host 与持久化键 |
| 28 | autosave debounce 前末笔立即 submit 仍冻结 | `MultipleChoiceDraftUiTest`；已有 Draft persistence barrier 测试 |
| 29 | History World 几何保持 | `renderer-contract.test.js`、`MultipleChoiceDraftUiTest`，检查 strokes/questionCard；查看缩放不发修改事件 |

另外覆盖 unknown option ID、SINGLE 多选拒绝、selectionMode 矛盾、未知 prompt/option kind 明确失败、PEN 下合成 answer change 不生效、提交前不泄露答案与解析。

## 命令与环境

项目 source/target 仍为 Java 21。实际本机运行使用 `C:\Program Files\Java\jdk-25.0.4.1` 的 Oracle JDK 25.0.4.1；没有改 pom 或提高 timeout。旧 JBR 21 曾在上一里程碑发生 native window 符号加载问题，不能将该环境错误当作本次代码回归。

```powershell
# repo root
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.4.1'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"

# quizforge-desktop-app/editor-web/draft-canvas
npm test
npm run build

# repo root
mvn '-Dtest=MultipleChoiceDraftUiTest,SharedMultipleChoiceAdapterTest,SharedPracticeAdapterTest,SharedPracticeCanvasWebViewTest,PracticeDraftModeUiTest,HistoryDraftReplayUiTest,DraftCanvasPersistenceTest,HistoryDraftReplayIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn test
```

Frontend：57/57 通过；三个本地 bundle 构建成功。日志 `target/renderer-v1-frontend.log`、`target/renderer-v1-build.log`。

Java targeted：两个已通过的最终运行合计覆盖 54 个不同用例。`target/renderer-v1-targeted-final.log` 中 infra 28 个 + desktop 24 个已通过；新 adapter 两个用例的 metadata 样本修正后单独运行全部通过，日志 `target/renderer-v1-adapter-final.log`。中途失败来自测试样本（原样本只有三选项、History 列表布局时序、新样本缺题库/题目 ID 前缀）；已修正，没有放宽业务断言或 timeout。

全量 `mvn test`：700/700 通过，0 failures / 0 errors / 0 skipped；BUILD SUCCESS，耗时 23:04 min，2026-10-03 18:29:35 +08:00 完成。日志 `target/renderer-v1-full.log`。本次加载的 JS/CSS 与最终生成资源 SHA-256 一致。

## 正式人工验收

使用既有 **Step 3 Live Acceptance** Workspace：`b84a311b-a902-48c3-91e6-7aecfdc77b1c`。没有创建新 Workspace。使用正式 QBankPackageWriter 创建独立验收文件 `question-banks/SharedMultipleChoiceAcceptance.qbank`（4 个 TEXT 选项，正确 A+C），未修改其他题库。

| 步骤 | 观察结果 |
| --- | --- |
| 1–2 | NORMAL 打开真实 MULTIPLE_CHOICE，原 JavaFX 复选框；右上角进入 Shared DRAFT |
| 3–4 | 选择 A+C，卡上画一笔，Canvas 计数 1 |
| 5–7 | 退出后 NORMAL 仍为 A+C；再进入恢复 A+C 和原笔迹 |
| 8–10 | 改为 B+D，再画最后一笔，随即切回交互并提交/确认；Core 错误 0/1，冻结 2 笔 |
| 11–13 | Retry 后四项均未选，Canvas 0 笔 |
| 14 | 重新选 A+C，画不同的一笔，再提交/确认；Core 正确 1/1、RETRY |
| 15–16 | 正式结果卡→重新练习→归档→历史详情；第 1/2 次结果为 B+D，草稿回放为 B+D + 两笔交叉线 |
| 17–19 | 第 2/2 次结果为 A+C；对应草稿为 A+C + 一笔斜线；切回结果仍保持第 2 次；历史复选框不可修改、仅提供 PAN/Zoom/Fit |
| 20 | 第一次 Frozen Snapshot 在第二次提交和两份 History 回放后内容与哈希均保持不变 |

人工操作由正式 UI 完成；数据库仅以 SQLite `mode=ro` 读取作证，没有 SQL 写入归档或答案。鼠标验收遵循二次确认；真正小于 500ms debounce 的最后一笔竞态由自动 WebView barrier 用例补充验证。

Session `ps_5e7bb6cb-d688-4862-aab2-2a3354bcf757` 已通过正式重新练习流程归档，产生新的空 ACTIVE 属于原有行为。

| Attempt | 模式 | 答案 | 得分 | Frozen strokes | SHA-256 |
| --- | --- | --- | --- | --- | --- |
| `pa_2acec03f-c34c-4c98-8827-5909fd55d4ef` | INITIAL | B+D | 0/1 | 2 | `91154fb75fefdbf982fa443ce3d68de78b7d6b8b8993e422a8a43793a244263e` |
| `pa_1f044d77-a357-4020-b68a-39c858ff3d05` | RETRY | A+C | 1/1 | 1 | `cce04e47131e84a836d0f15289f4511cff7e1dbf1eb610fd43b2ec01c49a8c7d` |

两份 World questionCard 均为 `(x=120, y=70, width=720)`；历史查看缩放到 120% 后数据库 frozen document 未变。只读证据位于 `target/renderer-v1-first-snapshot.json`、`renderer-v1-second-snapshot.json`、`renderer-v1-history-snapshot.json`，正式启动日志为 `renderer-v1-formal.*.log`。

## 限制与下一步

仅两种内置 Choice + TEXT；RICH/DOCUMENT/其他资源与其他题型明确不支持 Shared renderer。NORMAL 与 History RESULT 仍为既有 JavaFX；没有历史编辑、扩展插件、跨设备或同步。未改 Java 21 正式基线；本机 JDK 21 的 native UI 重跑仍需独立环境验证。

建议下一种为 READING：文章加多道单选子题可以复用 SINGLE 的 Choice 控件和答案集合语义，先验证组合题的子题身份及 Core 分项结果；无需先引入正文空位解析与弹层交互。本次未实现。

## 本次文件范围

相对本次开始时的状态，修改 22 个既有文件、新增 12 个文件。下列变更包括重新生成的本地资源，不将继承的其他未提交文件算成本次实现。

### 修改

- `README.md`
- `docs/new-developer-guide.md`
- `quizforge-desktop-app/editor-web/draft-canvas/HISTORY_DRAFT_REPLAY.md`
- `quizforge-desktop-app/editor-web/draft-canvas/PRACTICE_DRAFT_MODE.md`
- `quizforge-desktop-app/editor-web/draft-canvas/README.md`
- `quizforge-desktop-app/editor-web/draft-canvas/SHARED_PRACTICE.md`
- `quizforge-desktop-app/editor-web/draft-canvas/scripts/build.mjs`
- `quizforge-desktop-app/editor-web/draft-canvas/src/history-replay-app.js`
- `quizforge-desktop-app/editor-web/draft-canvas/src/practice/contract.js`
- `quizforge-desktop-app/editor-web/draft-canvas/src/practice/renderer.js`
- `quizforge-desktop-app/editor-web/draft-canvas/src/practice/style.css`
- `quizforge-desktop-app/editor-web/draft-canvas/src/shared-practice-app.js`
- `quizforge-desktop-app/editor-web/draft-canvas/src/style.css`
- `quizforge-desktop-app/editor-web/draft-canvas/test/practice.test.js`
- `quizforge-desktop-app/src/main/java/io/quizforge/desktop/poc/sharedpractice/SharedPracticeViewModel.java`
- `quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/history/HistoryDraftAdapter.java`
- `quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/practice/PracticeSurfaceHost.java`
- `quizforge-desktop-app/src/main/resources/editor/draft-canvas/draft-canvas.css`
- `quizforge-desktop-app/src/main/resources/editor/draft-canvas/history-replay.js`
- `quizforge-desktop-app/src/main/resources/editor/draft-canvas/shared-practice.css`
- `quizforge-desktop-app/src/main/resources/editor/draft-canvas/shared-practice.js`
- `quizforge-desktop-app/src/test/java/io/quizforge/desktop/ui/shell/PracticeDraftModeUiTest.java`

### 新增

- `quizforge-desktop-app/editor-web/draft-canvas/SHARED_RENDERER_ACCEPTANCE.md`
- `quizforge-desktop-app/editor-web/draft-canvas/SHARED_RENDERER_CONTRACT.md`
- `quizforge-desktop-app/editor-web/draft-canvas/src/renderer/choice/contract.js`
- `quizforge-desktop-app/editor-web/draft-canvas/src/renderer/choice/renderer.js`
- `quizforge-desktop-app/editor-web/draft-canvas/src/renderer/multiple-choice/index.js`
- `quizforge-desktop-app/editor-web/draft-canvas/src/renderer/single-choice/index.js`
- `quizforge-desktop-app/editor-web/draft-canvas/src/shared/renderer/contract.js`
- `quizforge-desktop-app/editor-web/draft-canvas/src/shared/renderer/registry.js`
- `quizforge-desktop-app/editor-web/draft-canvas/src/shared/runtime/question-runtime.js`
- `quizforge-desktop-app/editor-web/draft-canvas/test/renderer-contract.test.js`
- `quizforge-desktop-app/src/test/java/io/quizforge/desktop/poc/sharedpractice/SharedMultipleChoiceAdapterTest.java`
- `quizforge-desktop-app/src/test/java/io/quizforge/desktop/ui/shell/MultipleChoiceDraftUiTest.java`
