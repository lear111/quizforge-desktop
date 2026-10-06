# Shared Practice UI — common contract and migration notes

> 2026-10-06 迁移状态：题型来自用户安装的 HTML SDK 2 扩展，当前示例为单选、多选、判断题。正式 Java 适配和 DTO 已从 `poc/sharedpractice` 移至 `desktop/learning`，JavaFX 练习页面移至 `desktop/browser/javafx`，Windows 默认使用 WebView2。本文后续 Choice DTO、七题型覆盖和独立运行说明记录早期阶段；当前入口见 [代码地图](../../../docs/code-guide.md)，接口以 [SDK](../../../extensions/SDK_README.md) 为准。

The common learning surface shares one card between PRACTICE and DRAFT; installed extensions provide question UI. The original isolated launchers and JavaFX comparison fixtures remain available. [Shared Learning Surface v1](SHARED_LEARNING_SURFACE.md), [Practice Draft Mode v1](PRACTICE_DRAFT_MODE.md), and [History Draft Replay v1](HISTORY_DRAFT_REPLAY.md) record earlier lifecycle and geometry contracts.

Shared Runtime mounts installed HTML extension renderers. Core owns authoritative business state and grading validation; extension pages own presentation and semantic answer collection. Historical renderer evidence is retained in [SHARED_RENDERER_ACCEPTANCE.md](SHARED_RENDERER_ACCEPTANCE.md).
Current layout, geometry, sequence and lifecycle rules are fixed in [DRAFT_CANVAS_CONTRACT.md](DRAFT_CANVAS_CONTRACT.md).

## Ownership

| Layer | Files | Responsibility |
| --- | --- | --- |
| Canvas | `src/canvas/core.js`, `src/model.js`, `src/style.css` | World coordinates, viewport, live DOM object geometry, strokes, tools, undo/redo and Draft JSON |
| Shared Runtime | `src/shared/runtime/question-runtime.js`, `src/practice/contract.js` | Validate envelope; confirmation, submit/retry, operation ordering, lifecycle and semantic intents |
| Question Renderer | `src/shared/renderer/registry.js`, `src/extensions/sdk.js`, `extensions/packages/` at repository root | Installed HTML SDK 2 question UI and semantic answer intents |
| JS bridge | `src/bridge/practice.js`, `autosave.js` | `ready()`, sequenced semantic transport and acknowledged Draft autosave |
| Composition | `src/shared-practice-app.js`, `shared-practice.html` | Mount Practice DOM in Canvas; gate answer interaction on INTERACT |
| Java adapter | `io.quizforge.desktop.learning.SharedPracticeAdapter`, `SharedPracticeViewModel`, `SharedContent` | Map successful Core snapshot to explicit DTO; call existing `PracticeSessionService` |
| Java host | `browser/webview2/WebView2LearningSurface`, `WebView2PracticeSession`; `browser/javafx/SharedPracticeCanvasWebView` | Page loading, event identity validation, authoritative refresh, lifecycle and failure display |
| Development data | `SharedPracticeExample`, `SharedPracticeCanvasLauncher` | Read actual QBank; use existing SQLite Practice implementation with isolated durable database |

The stroke model does not know question types, attempts or sessions. Its existing v1 `questionCard` geometry key remains for compatibility; it describes the supplied live object only.

## Explicit JSON contract

```json
{
  "schemaVersion": "1.0",
  "session": { "sessionId": "...", "bankAssetId": "...", "bankContentId": "qfb:v2:..." },
  "question": {
    "sessionQuestionId": "...", "questionId": "q_demo_arraylist_structure",
    "type": "SINGLE_CHOICE", "selectionMode": "SINGLE", "index": 0, "total": 4,
    "prompt": { "kind": "TEXT", "text": "ArrayList 的底层结构是什么？" },
    "options": [
      { "id": "opt_demo_01_a", "content": { "kind": "TEXT", "text": "可扩容数组" }, "feedback": "NONE" },
      { "id": "opt_demo_01_b", "content": { "kind": "TEXT", "text": "双向链表" }, "feedback": "NONE" },
      { "id": "opt_demo_01_c", "content": { "kind": "TEXT", "text": "红黑树" }, "feedback": "NONE" }
    ],
    "selectedOptionIds": [], "state": "UNANSWERED", "maxScore": 1, "result": null
  }
}
```

IDs are stable data identifiers; Java class names and repository rows are absent. `index` is zero based and `total` refers to the entire source bank. MULTIPLE retains type `MULTIPLE_CHOICE`, selectionMode `MULTIPLE` and the complete selectedOptionIds set. Seven built-in TEXT types enable the formal learning surface; the JSON example above illustrates Choice.

Before submission `result` is null and all option feedback is NONE. A successful submission supplies:

```json
{
  "status": "INCORRECT", "score": 0, "maxScore": 1,
  "attemptId": "...", "attemptNo": 1, "attemptMode": "INITIAL",
  "correctOptionIds": ["opt_demo_01_a"],
  "analysis": { "kind": "TEXT", "text": "Core snapshot analysis" }
}
```

Feedback is NONE/CORRECT/INCORRECT and is mapped in Java from existing Core data. JS applies supplied display feedback and score; it never generates grading or Attempts. Content DTOs retain kind/text and carry native document data plus owned images for RICH/DOCUMENT. Missing resources and unknown types remain explicit. New essay/translation input is multiline text; existing document answers are retained when unchanged and rendered after submission or in history.

## Adapter and bridge

- `SharedPracticeAdapter(service, snapshot)`; `snapshot()`, `viewModel()`, `viewModelJson()`.
- `answerChanged(Set<String>)` → `saveDraft`; `submit()` → `submitAnswer`; `retry()` → `retryQuestion`.
- `loadDraft()` / `saveDraft(DraftCanvasDocument)` → Core Active Draft Canvas service.
- Java → JS: initial `loadPractice(json)` once; subsequent `applyResponse(json)` carries operationSeq/status/viewModel/error atomically.
- JS → retained `window.practiceHost`: `ready()`, `onEvent(json)` only.
- Event: `{type, sessionId, sessionQuestionId, operationSeq}`; ANSWER_CHANGED carries `selectedOptionIds`, DRAFT_CHANGED carries canonical `document`.
- `DRAFT_CHANGED` may include boolean `initialLayout:true` for a new card's extension-declared geometry. In Practice, Java accepts only a bounded initial width/camera when no saved draft exists (or an identical idempotent document). Card World x/y stay at their defaults; strokes/text/paper cannot be added, and an existing document cannot be changed. This does not enable annotation editing in Practice.
- Whitelisted types: ANSWER_CHANGED, DRAFT_CHANGED, SUBMIT, RETRY. Java validates identity and Core validates actual business rules.
- Responses include `operationType`; successful SUBMIT includes the latest frozen `draftDocument`; RETRY includes an empty document for a muted reset.

Lifecycle: page loads → Java attaches host → `bindHost()` → host `ready()` → Java `loadPractice` and `restoreDraft` from Core/SQLite.
Adapter replaces its snapshot only after a successful Core call and DTO mapping. Core operation failure keeps the prior authoritative DTO, restores checked radio selection and displays a sequenced retryable error. JS rejects stale response/error sequences; single flight and the Java FIFO queue serialize mutation barriers. Frontend request/confirmation state is local UI only.

## State flow

```text
UNANSWERED → choose → DRAFT → confirm submit → SUBMITTED + INITIAL result
SUBMITTED → retry → RETRYING + empty selected IDs + no result
RETRYING → choose → RETRYING → confirm submit → SUBMITTED + RETRY result
```

These are existing Core transitions. Attempts and their DraftSnapshots append. Submit first captures and awaits the latest Draft save, then Core freezes it and removes Active Draft atomically. Retry clears working answer/geometry without changing old Attempts or snapshots. See [DRAFT_PERSISTENCE.md](DRAFT_PERSISTENCE.md).

## Events and coordinates

The card is live DOM inside `#world`; SVG ink and card share one transform:
`screen = (world + viewportOffset) * zoom`.
INTERACT passes native interaction to Practice. PEN/PAN/ERASER capture viewport pointer gestures and prevent underlying controls from receiving them. Practice also checks current Canvas mode before sending intents. Switching to drawing blurs card inputs. ERASER touches strokes only. No card rasterization.

## Run

From the repository root, with Java 21 and Maven/Node installed:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\tools\Start-SharedPracticeCanvas.ps1
# Optional retained isolated database: -Database 'C:\path\to\practice.db'
# Optional read-only input: -QuestionBank 'C:\path\to\example.qbank'
```

No Vite server or CDN. The script builds both local bundles and Java in isolated target output. Default source is `examples/step7-practice/Java集合练习.qbank`. No user Workspace is created or edited. The default isolated database is `target/draft-persistence-acceptance/practice.db`; it uses formal Flyway migrations and is retained when the window closes. Reopening restores the same Active Practice and Canvas. Explicit `-Database` selects another database; no database is deleted/rebuilt. JSON import changes are autosaved in this entry.

## Verification commands

```powershell
npm.cmd --prefix quizforge-desktop-app/editor-web/draft-canvas test
npm.cmd --prefix quizforge-desktop-app/editor-web/draft-canvas run build
mvn.cmd -B -pl quizforge-desktop-app -am '-Dquizforge.build.directory=target/draft-poc' '-Dtest=SharedPracticeAdapterTest,SharedPracticeCanvasWebViewTest,DraftCanvasWebViewTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn.cmd -B '-Dquizforge.build.directory=target/draft-poc' test
```

Tests use actual QBank packages and temporary SQLite Practice storage. Failure injection occurs after Core transaction writes, verifying rollback and no fake submitted UI. Native JavaFX Robot tests use real mouse events, verify trusted pointers, and check aligned card/ink PAN plus frozen ink and empty Canvas after RETRY. See `SHARED_PRACTICE_ACCEPTANCE.md` for measured results and limitations.

Current persistence verification is recorded in [DRAFT_PERSISTENCE_ACCEPTANCE.md](DRAFT_PERSISTENCE_ACCEPTANCE.md); earlier acceptance files describe historical stages.
