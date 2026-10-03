# Shared Practice UI v1 — SINGLE_CHOICE

Isolated development slice. The original `index.html` / `DraftCanvasLauncher` POC remains available.
This is not the default QuizForge Practice screen. No Core, formal Practice UI, migrations or History code was changed.
Current layout, geometry, sequence and future lifecycle rules are fixed in [DRAFT_CANVAS_CONTRACT.md](DRAFT_CANVAS_CONTRACT.md).

## Ownership

| Layer | Files | Responsibility |
| --- | --- | --- |
| Canvas | `src/canvas/core.js`, `src/model.js`, `src/style.css` | World coordinates, viewport, live DOM object geometry, strokes, tools, undo/redo and Draft JSON |
| Practice | `src/practice/contract.js`, `renderer.js`, `style.css` | Validate explicit display DTO; prompt, radios, result, confirmation, retry; send semantic intents |
| JS bridge | `src/bridge/practice.js` | `ready()` and JSON semantic event transport |
| Composition | `src/shared-practice-app.js`, `shared-practice.html` | Mount Practice DOM in Canvas; gate answer interaction on INTERACT |
| Java adapter | `io.quizforge.desktop.poc.sharedpractice.SharedPracticeAdapter`, `SharedPracticeViewModel` | Map successful Core snapshot to explicit DTO; call existing `PracticeSessionService` |
| Java host | `SharedPracticeCanvasWebView` | Local bundle loading, retained narrow host object, event identity validation, authoritative refresh and failure display |
| Development data | `SharedPracticeExample`, `SharedPracticeCanvasLauncher` | Read actual QBank; use existing SQLite Practice implementation with isolated temporary database |

The stroke model does not know question types, attempts or sessions. Its existing v1 `questionCard` geometry key remains for compatibility; it describes the supplied live object only.

## Explicit JSON contract

```json
{
  "schemaVersion": "1.0",
  "session": { "sessionId": "...", "bankAssetId": "...", "bankContentId": "qfb:v2:..." },
  "question": {
    "sessionQuestionId": "...", "questionId": "q_demo_arraylist_structure",
    "type": "SINGLE_CHOICE", "index": 0, "total": 4,
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

IDs are stable data identifiers; Java class names and repository rows are absent. `index` is zero based and `total` refers to the entire source bank. The slice displays one real SINGLE_CHOICE, not the other bank questions.

Before submission `result` is null and all option feedback is NONE. A successful submission supplies:

```json
{
  "status": "INCORRECT", "score": 0, "maxScore": 1,
  "attemptId": "...", "attemptNo": 1, "attemptMode": "INITIAL",
  "correctOptionIds": ["opt_demo_01_a"],
  "analysis": { "kind": "TEXT", "text": "Core snapshot analysis" }
}
```

Feedback is NONE/CORRECT/INCORRECT and is mapped in Java from existing Core data. JS applies supplied display feedback and score; it never generates grading or Attempts. v1 uses TEXT because the existing choice Practice snapshot is plain text. Rich content and other types are deferred.

## Adapter and bridge

- `SharedPracticeAdapter(service, snapshot)`; `snapshot()`, `viewModel()`, `viewModelJson()`.
- `answerChanged(Set<String>)` → `saveDraft`; `submit()` → `submitAnswer`; `retry()` → `retryQuestion`.
- Java → JS: initial `loadPractice(json)` once; subsequent `applyResponse(json)` carries operationSeq/status/viewModel/error atomically.
- JS → retained `window.practiceHost`: `ready()`, `onEvent(json)` only.
- Event: `{type, sessionId, sessionQuestionId, operationSeq}`; ANSWER_CHANGED additionally carries `selectedOptionIds`.
- Whitelisted types: ANSWER_CHANGED, SUBMIT, RETRY. Java validates identity and Core validates actual business rules.

Lifecycle: page loads → Java attaches host → `bindHost()` → host `ready()` → Java `loadPractice`.
Adapter replaces its snapshot only after a successful Core call and DTO mapping. Core operation failure keeps the prior authoritative DTO, restores checked radio selection and displays a sequenced retryable error. JS rejects stale response/error sequences; single flight and the Java FIFO queue serialize mutation barriers. Frontend request/confirmation state is local UI only.

## State flow

```text
UNANSWERED → choose → DRAFT → confirm submit → SUBMITTED + INITIAL result
SUBMITTED → retry → RETRYING + empty selected IDs + no result
RETRYING → choose → RETRYING → confirm submit → SUBMITTED + RETRY result
```

These are existing Core transitions. Attempts append; the first immutable Attempt is retained after retry submission. Confirmation precedes submission. Retry retains strokes and viewport in this phase; formal Practice/Draft persistence and stroke reset coordination are next-phase work, explicitly noted in the UI and code.
The pure lifecycle specification now defines future success-freeze/remove and Retry-empty-canvas behavior. It is not wired into this live POC; see the contract document for that implemented/planned distinction.

## Events and coordinates

The card is live DOM inside `#world`; SVG ink and card share one transform:
`screen = (world + viewportOffset) * zoom`.
INTERACT passes native interaction to Practice. PEN/PAN/ERASER capture viewport pointer gestures and prevent underlying controls from receiving them. Practice also checks current Canvas mode before sending intents. Switching to drawing blurs card inputs. ERASER touches strokes only. No card rasterization.

## Run

From the repository root, with Java 21 and Maven/Node installed:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\tools\Start-SharedPracticeCanvas.ps1
# Optional read-only input: -QuestionBank 'C:\path\to\example.qbank'
```

No Vite server or CDN. The script builds both local bundles and Java in isolated target output. Default source is `examples/step7-practice/Java集合练习.qbank`. No user Workspace is created or edited. A temporary Practice database uses existing migrations, is removed when the launcher closes, and never stores Draft Canvas data. JSON export/import handles ink independently.

## Verification commands

```powershell
npm.cmd --prefix quizforge-desktop-app/editor-web/draft-canvas test
npm.cmd --prefix quizforge-desktop-app/editor-web/draft-canvas run build
mvn.cmd -B -pl quizforge-desktop-app -am '-Dquizforge.build.directory=target/draft-poc' '-Dtest=SharedPracticeAdapterTest,SharedPracticeCanvasWebViewTest,DraftCanvasWebViewTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn.cmd -B '-Dquizforge.build.directory=target/draft-poc' test
```

Tests use actual QBank packages and temporary SQLite Practice storage. Failure injection occurs after Core transaction writes, verifying rollback and no fake submitted UI. Native JavaFX Robot tests use real mouse events, verify trusted pointers, and check aligned card/ink PAN plus retained ink after RETRY. See `SHARED_PRACTICE_ACCEPTANCE.md` for measured results and limitations.
