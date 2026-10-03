# History Draft Replay v1 — acceptance

Date: 2026-10-03. Repository: `C:\Users\wangg\OneDrive\Desktop\QuizForge\quizforge_V2`.
HEAD: `55f6be0e91670dd1358f48bd237d00ccd93a088a`. No commit, push, stash or new migration.
Both original stashes remain: `wip-before-generic-rich-content-editor`, `wip-essay-before-richcontent-foundation`.
Inherited uncommitted work was backed up under ignored `target/history-replay-preexisting` before changes.

## Automatic verification

PowerShell; initial targeted tests and formal manual acceptance used Android Studio JBR 21. The final full run uses the existing Oracle JDK 25.0.4.1 with compiler release 21. Test assertions, timeouts and suite selection are unchanged.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
# From quizforge-desktop-app/editor-web/draft-canvas
npm test
npm run build
# From repository root
mvn '-Dtest=HistoryDraftReplayIntegrationTest,HistoryDraftReplayUiTest,PracticeHistoryUiTest,PracticeDraftModeUiTest,DraftCanvasPersistenceTest,PracticePersistenceIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
# Final full verification runtime (already installed; no project runtime configuration change)
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
mvn test
```

Frontend: **48/48 passed**, local build succeeded. Native History HTML links the existing `shared-practice.css`; it has no independent stylesheet.

Java targeted: SQLite replay **7/7**, native History replay **3/3**, existing History **15/15**, Practice Draft **6/6**, Draft persistence **21/21**, Practice persistence **28/28**. The final native replay rerun includes frozen question content remaining unchanged after editing the current bank.

Initial regression tests exposed native form navigation on a read-only card and a stale WebKit function reference. Both were repaired. A subsequent failed legacy test was an invalid test setup reusing an existing Active Draft; its independent-bank fixture now proves real no-snapshot behavior. Neither issue was hidden by relaxing assertions or raising timeouts.

The initial JBR 21 full run executed 695 tests: 693 passed, with failures in the existing native pointer test and one pasted-image painting check. The image test subsequently passed all 14 cases. Isolated pointer reruns then exposed a Windows native window creation timeout; a temporary thread dump located it in native symbol resolution for `WinWindow._createWindow`, before Canvas JS interaction. Diagnostic source changes were reverted. The unchanged original DraftCanvasWebViewTest passed all three tests on the installed JDK 25.0.4.1 (`target/history-replay-native-jdk25.log`). These earlier failures are retained in logs, not counted as a successful full gate.

Final full Maven result: **BUILD SUCCESS, 695/695 passed**, zero failures/errors/skips: Core 73, Infrastructure 331, Desktop 291. Completed 2026-10-03 16:24:17 +08:00, 28:06 elapsed. Log: `target/history-replay-full-final.log`. This full run includes the final header width and WebView reference-release fixes, all existing native pointer/image checks and all new History replay tests.

### Requirement coverage

| Requirement | Evidence |
| --- | --- |
| 1–2: attemptId lookup and independent A/B documents | HistoryDraftReplayIntegrationTest, native HistoryDraftReplayUiTest |
| 3–4: mode changes retain selected Attempt | native HistoryDraftReplayUiTest; manual A–D |
| 5: READ_ONLY tools | native direct PEN/ERASER/INTERACT calls reject; buttons disabled/hidden |
| 6: no DRAFT_CHANGED | native onChange counter remains zero after load/zoom/fit/toggles |
| 7: no Active save | History service receives a failing ActiveDraft repository; bridge has no save method |
| 8: no Answer mutation | disabled radios; synthetic change/submit leaves authoritative answer and Attempt unchanged |
| 9–10: Frozen and Active unchanged | byte-identical SQLite row assertions; manual hash/timestamp comparison |
| 11–12: legacy normal RESULT, no fake draft | native no-snapshot entry hidden, WebView stays uncreated; manual Q1/Q39 |
| 13–15: bad schema/layout/malformed | parameterized SQLite tests, frontend strict tests, corrupt JSON native UI |
| 16–17: width/geometry survives resize | native 720px width and world geometry unchanged; frontend document checks |
| 18: question switch no old ink | native C/empty switch; manual Q38→Q39 returns RESULT |
| 19: Attempt switch no old ink | native SVG counts 3→1→3; manual A/B/C |
| 20: destroy | native back-to-list and closeAll release JS page and bridge; formal manual return/close |
| 21: no duplicate handlers | one WebView across toggles; read-only notification counter stays zero; native single selection changes |

Version errors leave original JSON bytes unchanged. Unknown fields also reject rather than being silently projected away. Result rendering never depends on decoding the draft.

## Native manual acceptance

Existing **Step 3 Live Acceptance**, ID `b84a311b-a902-48c3-91e6-7aecfdc77b1c`.
Bank: `question-banks/PracticeOutlineAcceptance.qbank`. No new human Workspace or bank content edit.

Both required prior records existed, so no synthetic attempts were created:

| Q38 Attempt | Identity | Answer / score | Draft |
| --- | --- | --- | --- |
| INITIAL #1 | `pa_eabe96f4-0eb8-4dc0-bef6-0b5743d56874` | C / 0 of 1 | 3 strokes; original viewport -97,-33,1 |
| RETRY #2 | `pa_cb4038c3-bf49-450e-baf1-2ca969ac2ca2` | B / 0 of 1 | 1 stroke; its own viewport 176,26,1 |

The round was ACTIVE when inspected. Through the existing formal **last question → summary → 重新练习 → confirmation** flow it was archived as `ps_3f18af06-0072-45c4-8d97-6573f491dd94`, preserving old attempts and snapshots. The ordinary flow also started its next empty practice round.

- A: formal History list → archived round → Q38 → previous Attempt: RESULT showed INITIAL/C; Draft showed its three original strokes.
- B: next Attempt in DRAFT: RETRY/B, its single circle stroke; no INITIAL lines remained.
- C: previous Attempt restored INITIAL three strokes and original viewport.
- D: 返回结果 retained INITIAL #1/C.
- E: older Q1 attempts without snapshots kept normal results and hid the Draft entry. Q39, with unsubmitted Active ink but no frozen Attempt snapshot, also kept ordinary RESULT; Q38→Q39 removed the old draft view.
- Fit and native mouse Pan worked in READ_ONLY; there was no Pen, Eraser, Submit or Retry control.
- Returned to History list and closed the acceptance process normally.

After view pan/zoom, selection and mode changes, both `practice_draft_canvas` and `attempt_draft_snapshot` IDs, raw document SHA-256 and timestamps matched exactly. One Active row and three Frozen rows were preserved. Ignored evidence: `target/history-replay-manual-db-before.json`, `...-after.json`.

INITIAL JSON SHA-256: `7aabe78e4fc5a5836d4cb66228233e9197725e32051ad72378e09529f431f833`.
RETRY JSON SHA-256: `0af52f76689e438c257ae9a6fb9ab35fc153de46946da0ec9dbde1d91b656e79`.

The first manual run exposed a squeezed toggle label. The final header reserves the toggle's preferred width and truncates the title on one line. The final formal UI check on the installed JDK 25.0.4.1 confirmed complete **草稿 / 返回结果** labels in the existing workspace. Q38 RETRY still showed its single stroke and original viewport; returning to RESULT retained Attempt #2/B. Returned to History list, then closed only the acceptance process (PID 19688) through its window control. No additional archive or submitted attempt was created. Active/Frozen row hashes, timestamps and Q38 attempts remained identical (`target/history-replay-manual-final-before.json`, `...-after.json`).

## Limits

Only SINGLE_CHOICE + TEXT. Same Practice CSS, font stack, renderer and logical card width do not freeze installed font binaries or future WebKit/layout behavior. Unsupported versions/fields fail explicitly without rewrite. Local view pan/zoom is reset on reload; no historical editing, save, continuation, new migration or other question-type migration.

Architecture, read-only capabilities and future renderer interface boundary: [HISTORY_DRAFT_REPLAY.md](HISTORY_DRAFT_REPLAY.md).

## Files and git status

Against the pre-task byte backup: 17 modified files, 14 added files for this milestone. Inherited changes remain in the worktree; the deleted old DraftCanvasDocument path is inherited from the Core move.

### Modified in this milestone

- `README.md`
- `docs/new-developer-guide.md`
- `quizforge-core/src/main/java/io/quizforge/core/practice/PracticeHistoryDetail.java`
- `quizforge-core/src/main/java/io/quizforge/core/practice/PracticeHistoryService.java`
- `quizforge-desktop-app/editor-web/draft-canvas/DRAFT_CANVAS_CONTRACT.md`
- `quizforge-desktop-app/editor-web/draft-canvas/README.md`
- `quizforge-desktop-app/editor-web/draft-canvas/SHARED_PRACTICE.md`
- `quizforge-desktop-app/editor-web/draft-canvas/scripts/build.mjs`
- `quizforge-desktop-app/editor-web/draft-canvas/src/canvas/core.js`
- `quizforge-desktop-app/editor-web/draft-canvas/src/practice/contract.js`
- `quizforge-desktop-app/editor-web/draft-canvas/src/practice/renderer.js`
- `quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/file/QuestionBankFileView.java`
- `quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/history/PracticeHistoryDetailView.java`
- `quizforge-desktop-app/src/main/resources/editor/draft-canvas/draft-canvas.js`
- `quizforge-desktop-app/src/main/resources/editor/draft-canvas/shared-practice.js`
- `quizforge-desktop-app/editor-web/draft-canvas/DRAFT_PERSISTENCE.md`
- `quizforge-desktop-app/editor-web/draft-canvas/PRACTICE_DRAFT_MODE.md`

### Added in this milestone

- `quizforge-core/src/main/java/io/quizforge/core/practice/HistoryDraftReplay.java`
- `quizforge-desktop-app/editor-web/draft-canvas/HISTORY_DRAFT_REPLAY.md`
- `quizforge-desktop-app/editor-web/draft-canvas/HISTORY_DRAFT_REPLAY_ACCEPTANCE.md`
- `quizforge-desktop-app/editor-web/draft-canvas/src/history-replay-app.js`
- `quizforge-desktop-app/editor-web/draft-canvas/src/history-replay.js`
- `quizforge-desktop-app/editor-web/draft-canvas/test/history-replay.test.js`
- `quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/history/HistoryDraftAdapter.java`
- `quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/history/HistoryDraftWebView.java`
- `quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/history/HistorySurfaceHost.java`
- `quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/history/HistorySurfaceMode.java`
- `quizforge-desktop-app/src/main/resources/editor/draft-canvas/history-replay.html`
- `quizforge-desktop-app/src/main/resources/editor/draft-canvas/history-replay.js`
- `quizforge-desktop-app/src/test/java/io/quizforge/desktop/ui/shell/HistoryDraftReplayUiTest.java`
- `quizforge-infrastructure/src/test/java/io/quizforge/infrastructure/HistoryDraftReplayIntegrationTest.java`

### git status --short

```text
 M README.md
 M docs/new-developer-guide.md
 M quizforge-core/src/main/java/io/quizforge/core/port/PracticeTransaction.java
 M quizforge-core/src/main/java/io/quizforge/core/practice/PersistentPracticeRuntime.java
 M quizforge-core/src/main/java/io/quizforge/core/practice/PracticeHistoryDetail.java
 M quizforge-core/src/main/java/io/quizforge/core/practice/PracticeHistoryService.java
 M quizforge-core/src/main/java/io/quizforge/core/practice/PracticeSessionService.java
 M quizforge-desktop-app/editor-web/draft-canvas/DRAFT_CANVAS_CONTRACT.md
 M quizforge-desktop-app/editor-web/draft-canvas/README.md
 M quizforge-desktop-app/editor-web/draft-canvas/SHARED_PRACTICE.md
 M quizforge-desktop-app/editor-web/draft-canvas/scripts/build.mjs
 M quizforge-desktop-app/editor-web/draft-canvas/src/canvas/core.js
 M quizforge-desktop-app/editor-web/draft-canvas/src/practice/contract.js
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
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/history/PracticeHistoryDetailView.java
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
?? quizforge-core/src/main/java/io/quizforge/core/practice/HistoryDraftReplay.java
?? quizforge-core/src/main/java/io/quizforge/core/practice/draft/
?? quizforge-core/src/test/java/io/quizforge/core/practice/draft/
?? quizforge-desktop-app/editor-web/draft-canvas/DRAFT_PERSISTENCE.md
?? quizforge-desktop-app/editor-web/draft-canvas/DRAFT_PERSISTENCE_ACCEPTANCE.md
?? quizforge-desktop-app/editor-web/draft-canvas/HISTORY_DRAFT_REPLAY.md
?? quizforge-desktop-app/editor-web/draft-canvas/HISTORY_DRAFT_REPLAY_ACCEPTANCE.md
?? quizforge-desktop-app/editor-web/draft-canvas/PRACTICE_DRAFT_MODE.md
?? quizforge-desktop-app/editor-web/draft-canvas/PRACTICE_DRAFT_MODE_ACCEPTANCE.md
?? quizforge-desktop-app/editor-web/draft-canvas/src/bridge/autosave.js
?? quizforge-desktop-app/editor-web/draft-canvas/src/history-replay-app.js
?? quizforge-desktop-app/editor-web/draft-canvas/src/history-replay.js
?? quizforge-desktop-app/editor-web/draft-canvas/test/autosave.test.js
?? quizforge-desktop-app/editor-web/draft-canvas/test/history-replay.test.js
?? quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/history/HistoryDraftAdapter.java
?? quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/history/HistoryDraftWebView.java
?? quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/history/HistorySurfaceHost.java
?? quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/history/HistorySurfaceMode.java
?? quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/practice/PracticeSurfaceHost.java
?? quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/practice/PracticeSurfaceMode.java
?? quizforge-desktop-app/src/main/resources/editor/draft-canvas/history-replay.html
?? quizforge-desktop-app/src/main/resources/editor/draft-canvas/history-replay.js
?? quizforge-desktop-app/src/test/java/io/quizforge/desktop/poc/sharedpractice/DraftPersistenceWebViewTest.java
?? quizforge-desktop-app/src/test/java/io/quizforge/desktop/ui/shell/HistoryDraftReplayUiTest.java
?? quizforge-desktop-app/src/test/java/io/quizforge/desktop/ui/shell/PracticeDraftModeUiTest.java
?? quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/persistence/practice/DraftCanvasJsonCodec.java
?? quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/persistence/practice/SqliteActiveDraftCanvasRepository.java
?? quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/persistence/practice/SqliteAttemptDraftSnapshotRepository.java
?? quizforge-infrastructure/src/main/resources/db/migration/V6__draft_canvas.sql
?? quizforge-infrastructure/src/test/java/io/quizforge/infrastructure/DraftCanvasPersistenceTest.java
?? quizforge-infrastructure/src/test/java/io/quizforge/infrastructure/HistoryDraftReplayIntegrationTest.java
```
