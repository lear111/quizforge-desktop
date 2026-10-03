# Shared Renderer Coverage v1 验收

日期：2026-10-03（Asia/Shanghai）。基线：`b4d44d8bf15445ce59da231f5c43d85cc1572193`。开始时工作树干净，两个原有 stash 保留。本次没有 commit、push、stash 或修改用户原有题库。

## 正式范围

静态 `QuestionRendererRegistry` 注册 SINGLE_CHOICE、MULTIPLE_CHOICE、READING、CLOZE、MATCHING、TRANSLATION、ESSAY。五种新增题型按上述顺序接入并逐阶段验证。所有题型复用 Shared Runtime、Bridge、FIFO identity/operationSeq 队列、Canvas、500ms autosave、submit flush barrier 和 SQLite repositories。

公共 shell + sealed type presentation；题型只提供展示、语义 intent、Core 结果投影及 stable target。完整合同见 [SHARED_RENDERER_CONTRACT.md](SHARED_RENDERER_CONTRACT.md)。

- READING：passage、多小题独立单选、部分答案，父答案为 option ID 集合。
- CLOZE：stable blank、内联选择与下方选项同步；重复 `{{n}}` 不产生新的业务身份。
- MATCHING：既有八位置/固定提示语义，普通答案可重复，提示字母不可再选；Core 校验和判分。
- TRANSLATION：多句多行 TEXT，300ms 输入 debounce，关闭/提交时 flush；UNSCORED 的 score 保留 null。
- ESSAY：多行正式 TEXT answer，NORMAL 与 DRAFT 双向同步，保留既有富文本入口；草稿纸独立。

一张 Question = 一个 PracticeSessionQuestion = 一张 Active Draft；一次提交 = 一个 Attempt = 一个 Frozen DraftSnapshot。子题只使用通用 `focusTarget(stableId)` 定位，不创建子 Draft/Attempt。

## 自动验证证据

日志位于仓库忽略目录 `target/`，不提交测试运行产物。

| 阶段 | Java targeted | 日志 |
| --- | ---: | --- |
| READING + Choice regression | 11/11 | coverage-reading-targeted.log |
| CLOZE + previous regression | 14/14 | coverage-cloze-targeted.log |
| MATCHING + previous regression | 16/16 | coverage-matching-targeted.log |
| TRANSLATION + previous regression | 18/18 | coverage-translation-targeted.log |
| ESSAY + previous regression | 22/22 | coverage-essay-targeted.log |
| NORMAL Essay TEXT + native input + Essay regression | 5/5 | coverage-native-regression.log |
| 真正大纲分派 + composite WebKit lifecycles | 3/3 | coverage-outline-regression.log |

Frontend targeted 五家族最终 18/18；全量 75/75，0 failure/skip（`coverage-frontend-full.log`）；`npm run build` 成功（`coverage-frontend-build.log`），三份 JS bundle 与 shared CSS 已生成。

组合 targeted 日志 `coverage-final-targeted.log` 为 43 cases、42 pass、1 failure：旧 native mouse eraser 用例一度未擦掉笔迹。相同用例在不改源码、不延长 timeout 的独立回归中通过，见 `coverage-native-regression.log`。不能把这份组合日志表述为 43/43。

此前一次全量测试为补齐 NORMAL 作文 TEXT 入口而主动中止，原日志保留为 `coverage-full-before-normal-text.log`，不计作全量成功。

首次完整 Maven 运行的 721 cases 中有 3 个旧预期失败（`coverage-full-before-legacy-expectations.log`）：完形小题由父跳转改为 stable target；TEXT 作文已支持 DRAFT，不再自动退回 NORMAL；READING 已注册，未知题型用 UNKNOWN 验证。相应用例已更新，原错误日志保留。修正后的相关 targeted regression 18/18 通过（`coverage-legacy-regression.log`，03:02 min），包括真实 WebKit、History 和正式 Practice host。最终完整 `mvn test`：BUILD SUCCESS，721/721，0 failures/errors/skips；Core 73、Infrastructure 331、Desktop 317，总耗时 24:05 min（`coverage-full-maven.log`）。原 native mouse 用例在此最终全量中 4/4 通过。文档 66 个本地链接存在，`git diff --check` 通过。

实际 JDK：`C:\Program Files\Java\jdk-25.0.4.1`（25.0.4.1）；正式 Maven compiler release 仍为 21，未修改 pom 或加入 Java 25 专属 API。

### 各类型至少 17 类覆盖映射

下面是验证类别，不是人为拆成 17 个重复单测。参数化真实 WebKit 生命周期同时覆盖多个相互依赖的步骤。

| 类别 | READING / CLOZE / MATCHING | TRANSLATION / ESSAY |
| --- | --- | --- |
| 1 Registry lookup | renderer-contract.test.js、各 family JS tests | 同左 |
| 2 Active render | SharedRendererCoverageWebViewTest 三类型 | SharedTextRendererCoverageWebViewTest 两类型 |
| 3 Semantic intent | 同上 + 对应 Shared*AdapterTest | 同左 |
| 4 Answer roundtrip | 各 Shared*AdapterTest SQLite | 同左，TEXT/null document |
| 5 Reopen restore | Shared*AdapterTest + WebView lifecycle | 同左 + Normal Essay 双向恢复 |
| 6 Canvas restore | WebView lifecycle + persistence tests | 同左 + close-save pending TEXT |
| 7 Submit | WebView lifecycle | 同左，输入 debounce 前立即提交 |
| 8 Core result | Adapter 与 WebView 检查 Core 得分 | Core UNSCORED、nullable score/max |
| 9 Retry appropriate | Core Retry，活动答案与纸清空 | 同左，不引入新的评分/Revision 规则 |
| 10 Final-stroke barrier | submit 前末笔保存入 snapshot | 同左 + pending text flush |
| 11 Frozen snapshot | repository 中实际 snapshot | 同左 |
| 12 History result | 历史投影对应 frozen Attempt | 同左 + references/UNSCORED |
| 13 History Draft replay | 真正 HistoryDraftWebView 加载 | 同左 |
| 14 Readonly | 禁用输入，无 submit/retry，伪造 change 不改变 | readonly textarea 与无 mutation listeners |
| 15 Attempt 1/2 isolation | 两个 Attempt 的答案与笔迹 | 同左 |
| 16 Old snapshot immutability | 第二次提交/历史来回加载后比较 | 同左 |
| 17 Destroy/cleanup | renderer destroy + Java host destroy | 同左 + text timer cleanup |
| Composite target/identity | stable child focus + 真正 outline dispatch + parent ID | Translation stable target；Essay 正式 answer 与 Canvas 独立 |

SharedRendererContentBoundaryTest 对五类型共 10 cases 验证 unsupported content 和旧 Attempt 没有 Snapshot 保留 RESULT。原 SINGLE/MULTIPLE tests 保留；全文回归仍包含既有 JavaFX NORMAL/RESULT、Canvas/native pointer、persistence 和 History suites。

## 真实界面人工验收

只使用既有 **Step 3 Live Acceptance** workspace：`b84a311b-a902-48c3-91e6-7aecfdc77b1c`。新增自有 TEXT 题库：`question-banks/SharedRendererCoverageAcceptance.qbank`，assetId=`qb_shared_coverage_live`，包含五父题、12 个需要回答的大纲编号。没有创建新 workspace，没有直接写 SQLite。

题库由现有 Core 定义与 QBankPackageWriter 生成并校验往返，新的业务 IDs 与既有题库隔离。所有练习、提交、重试、归档和历史切换均通过正式应用 UI 完成。

人工 A–P 各类型均完成：打开；NORMAL 部分作答；DRAFT 同步；不同区域两处笔迹；退出；重入恢复；首次提交/结果；Core 重试；不同回答与一处笔迹的第二次提交；History Attempt 1/Draft 1；Attempt 2/Draft 2；切回 Frozen 1 保持原样。

| 类型 | 初次 NORMAL 答案 | 首次结果 | 第二次答案 | 第二次结果 | 两次纸面 |
| --- | --- | --- | --- | --- | --- |
| READING | 第 1 小题 A | 2/4 | 第 2 小题 C | 2/4 | 2 strokes / 1 stroke |
| CLOZE | 第 1 空 A，正文重复标签同步 | 2/4 | 第 2 空 C | 2/4 | 2 / 1 |
| MATCHING | 第 2 位置 B，固定 A/D/F | 2/10 | 第 3 位置 C，固定 A/D/F | 2/10 | 2 / 1 |
| TRANSLATION | 第 1 句两行 TEXT | UNSCORED，null/4 | 第 2 句不同两行 TEXT | UNSCORED，null/4 | 2 / 1 |
| ESSAY | 两行 TEXT attempt one | UNSCORED，null/5 | 不同两行 TEXT attempt two | UNSCORED，null/5 | 2 / 1 |

阅读/完型/翻译大纲小题 target 在 DRAFT 与 History 中定位同父卡。人工发现 CLOZE 大纲原分派仅调用父题 jump，已补齐通用 itemJump 路由；添加 actual outline regression，3/3 通过，重启正式应用后复测第二空定位成功。

作文首次 NORMAL TEXT → DRAFT → 退出 NORMAL → 重入完整恢复；原“编辑作答”仍存在。重试中关闭后重新启动正式应用，恢复同一 session 和 RETRYING，不生成新父题身份。

通过正式汇总“重新练习”归档；归档 session=`ps_61e4e87e-8876-4642-a494-5fbe670ccbff`。新活动 session 是现有流程产生的下一轮，未用于扩充验收结果。归档五父题恰好 10 Attempts/10 Frozen Snapshots，没有子题 Attempt/Draft。

### 只读持久化核验

仅查询此验收 asset/session。使用 Python sqlite `mode=ro` 读取 `.quizforge/quizforge.db`，提交后、历史浏览前后记录 `target/coverage-live-before-history.json` 与 `target/coverage-live-after-history.json`。

逐父题比较：Attempt 1 完整 evidence（answer/result/score/max/snapshot SHA-256）不变；第二次不同答案及 snapshot；笔迹数量 2/1；主观题所有 score=null。核验输出 `READONLY LIVE EVIDENCE: PASS`。

History 所有类型都用同一个 Shared Renderer：只读题目答案、对应 frozen strokes、Core result/analysis/reference。只提供 Pan/Zoom/Fit；未出现 Submit/Retry 或画笔/橡皮/autosave 入口。

## 复现命令

```powershell
# 仓库根目录 quizforge_V2；运行真实应用与 native tests 时应分开
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.4.1'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn test '-Dtest=SharedReadingAdapterTest,SharedClozeAdapterTest,SharedMatchingAdapterTest,SharedTranslationAdapterTest,SharedEssayAdapterTest,SharedRendererCoverageWebViewTest,SharedTextRendererCoverageWebViewTest,SharedRendererContentBoundaryTest,SharedEssayNormalTextTest,SharedPracticeAdapterTest,SharedMultipleChoiceAdapterTest' '-Dsurefire.failIfNoSpecifiedTests=false'
# editor-web/draft-canvas
npm test
npm run build
# 返回仓库根目录
mvn test
```

## 限制与后续

只保证 TEXT。RICH/DOCUMENT/IMAGE/AUDIO/VIDEO 及已有 document answer 在 Shared 面明确 unsupported，不 silently strip；既有 NORMAL/RESULT 内容能力继续保留。旧 Attempt 缺 DraftSnapshot 时留在 RESULT，不显示假的空白草稿。

Translation/Essay 未评分，不实现 AI 或人工 grading。REVISING/REVISION 兼容既有 Core 状态；没有额外设计 Revision 工作流。

复用固定逻辑宽度与自然增长高度，长卡可用 Pan/Zoom/Fit 或 target 定位；不保证所有内容同时装入窄视口。尚未进入 RICH/DOCUMENT、平板、Extension、LAN、Hub 或云端。

后续 Shared Learning UI 可围绕真实内容能力的合同、公共题卡排版/可访问性/键盘交互及跨平台 host 适配单独立项；需要明确新范围。本里程碑不开展这些功能。

## 文件清单与冻结摘要

以下为本次工作树文件清单。运行日志与手工验收临时工具位于忽略目录 target，不进入源码清单。


| type | Attempt 1 ID | Frozen 1 SHA-256 | Frozen 2 SHA-256 |
| --- | --- | --- | --- |
| READING | `pa_75f4b7e2-d9de-492f-9f11-3c2e98e745da` | `9318c7990f6e6d4ccbe3a4da74d5d552e140152d9ae3fa41f70c4d5aef77edeb` | `ed494321cd32eafd244b41ad29663e8332dd6bfcfe8e72fe6e6c6c30d31c5e8d` |
| CLOZE | `pa_a661301b-d5eb-4d52-b758-c2c8188ca51e` | `c6f6acc6e06a135f93575ea8bab245f114c28bbf5e016a33b29c605ae150ae09` | `a01aae564f541ec922557cf7bcb8b2c6033a85563f63feed80fe6d195a41c4cd` |
| MATCHING | `pa_7cdbb2c4-48d1-4ddd-9367-330529a658e4` | `4d6374077e812a2e2c1e65f40e42ad86265a8f1d6896956ea89da7cf16d270d6` | `3a6b35d15b621748980f268c1cc76d7537af366aa69aa177904428c9b33bca6d` |
| TRANSLATION | `pa_ad070eb6-98be-4c81-89b3-7dd818747979` | `093d7e523d1cc820085e4c2b852b3fe273f94232b11e178e934a6cf074d7e6a0` | `34f7a4e703a98e670baa9b70d54910eb4a15a2b3e2666c130c9086ffa287c70e` |
| ESSAY | `pa_e890a4c7-7fb0-420e-a14e-b9b2dbbb6a02` | `4cf73c63ad5f8b37df39fcdaabca9238a645dc4bf3cb263e3f2b7960169e6d0c` | `5d569b13461f7b8e3d4ef7e566fcd187f273f3029035dcb069d5cac86e7d0665` |

```text
 M README.md
 M docs/new-developer-guide.md
 M quizforge-core/src/main/java/io/quizforge/core/practice/PersistentPracticeRuntime.java
 M quizforge-desktop-app/editor-web/draft-canvas/SHARED_RENDERER_CONTRACT.md
 M quizforge-desktop-app/editor-web/draft-canvas/scripts/build.mjs
 M quizforge-desktop-app/editor-web/draft-canvas/src/canvas/core.js
 M quizforge-desktop-app/editor-web/draft-canvas/src/history-replay-app.js
 M quizforge-desktop-app/editor-web/draft-canvas/src/practice/contract.js
 M quizforge-desktop-app/editor-web/draft-canvas/src/practice/style.css
 M quizforge-desktop-app/editor-web/draft-canvas/src/renderer/choice/renderer.js
 M quizforge-desktop-app/editor-web/draft-canvas/src/shared-practice-app.js
 M quizforge-desktop-app/editor-web/draft-canvas/src/shared/renderer/contract.js
 M quizforge-desktop-app/editor-web/draft-canvas/src/shared/renderer/registry.js
 M quizforge-desktop-app/editor-web/draft-canvas/src/shared/runtime/question-runtime.js
 M quizforge-desktop-app/editor-web/draft-canvas/test/renderer-contract.test.js
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/poc/sharedpractice/SharedPracticeAdapter.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/poc/sharedpractice/SharedPracticeCanvasWebView.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/poc/sharedpractice/SharedPracticeViewModel.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/history/HistoryDraftAdapter.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/history/HistoryDraftWebView.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/history/HistorySurfaceHost.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/history/PracticeHistoryDetailView.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/practice/MixedQuestionPracticeView.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/practice/PracticeSurfaceHost.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/shared/QuestionOutlineView.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/subjective/essay/EssayAnswerPane.java
 M quizforge-desktop-app/src/main/resources/editor/draft-canvas/draft-canvas.js
 M quizforge-desktop-app/src/main/resources/editor/draft-canvas/history-replay.js
 M quizforge-desktop-app/src/main/resources/editor/draft-canvas/shared-practice.css
 M quizforge-desktop-app/src/main/resources/editor/draft-canvas/shared-practice.js
 M quizforge-desktop-app/src/test/java/io/quizforge/desktop/poc/sharedpractice/SharedMultipleChoiceAdapterTest.java
 M quizforge-desktop-app/src/test/java/io/quizforge/desktop/ui/question/history/HistoryReadingViewTest.java
 M quizforge-desktop-app/src/test/java/io/quizforge/desktop/ui/question/subjective/essay/EssayCardViewTest.java
 M quizforge-desktop-app/src/test/java/io/quizforge/desktop/ui/shell/MultipleChoiceDraftUiTest.java
 M quizforge-desktop-app/src/test/java/io/quizforge/desktop/ui/shell/PracticeDraftModeUiTest.java
?? quizforge-desktop-app/editor-web/draft-canvas/SHARED_RENDERER_COVERAGE_ACCEPTANCE.md
?? quizforge-desktop-app/editor-web/draft-canvas/src/renderer/cloze/index.js
?? quizforge-desktop-app/editor-web/draft-canvas/src/renderer/composite-selection/renderer.js
?? quizforge-desktop-app/editor-web/draft-canvas/src/renderer/essay/index.js
?? quizforge-desktop-app/editor-web/draft-canvas/src/renderer/matching/index.js
?? quizforge-desktop-app/editor-web/draft-canvas/src/renderer/reading/index.js
?? quizforge-desktop-app/editor-web/draft-canvas/src/renderer/text-answer/renderer.js
?? quizforge-desktop-app/editor-web/draft-canvas/src/renderer/translation/index.js
?? quizforge-desktop-app/editor-web/draft-canvas/src/shared/renderer/result.js
?? quizforge-desktop-app/editor-web/draft-canvas/test/cloze.test.js
?? quizforge-desktop-app/editor-web/draft-canvas/test/essay.test.js
?? quizforge-desktop-app/editor-web/draft-canvas/test/matching.test.js
?? quizforge-desktop-app/editor-web/draft-canvas/test/reading.test.js
?? quizforge-desktop-app/editor-web/draft-canvas/test/translation.test.js
?? quizforge-desktop-app/src/test/java/io/quizforge/desktop/poc/sharedpractice/SharedClozeAdapterTest.java
?? quizforge-desktop-app/src/test/java/io/quizforge/desktop/poc/sharedpractice/SharedEssayAdapterTest.java
?? quizforge-desktop-app/src/test/java/io/quizforge/desktop/poc/sharedpractice/SharedEssayNormalTextTest.java
?? quizforge-desktop-app/src/test/java/io/quizforge/desktop/poc/sharedpractice/SharedMatchingAdapterTest.java
?? quizforge-desktop-app/src/test/java/io/quizforge/desktop/poc/sharedpractice/SharedReadingAdapterTest.java
?? quizforge-desktop-app/src/test/java/io/quizforge/desktop/poc/sharedpractice/SharedRendererContentBoundaryTest.java
?? quizforge-desktop-app/src/test/java/io/quizforge/desktop/poc/sharedpractice/SharedRendererCoverageWebViewTest.java
?? quizforge-desktop-app/src/test/java/io/quizforge/desktop/poc/sharedpractice/SharedTextRendererCoverageWebViewTest.java
?? quizforge-desktop-app/src/test/java/io/quizforge/desktop/poc/sharedpractice/SharedTranslationAdapterTest.java
```
