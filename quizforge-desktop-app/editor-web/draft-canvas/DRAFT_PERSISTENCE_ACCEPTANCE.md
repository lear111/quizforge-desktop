# Draft Persistence v1 acceptance — 2026-10-03

## Scope and baseline

Baseline HEAD: `55f6be0e91670dd1358f48bd237d00ccd93a088a` (`feat: add draft canvas and shared practice contract v1`). Initial `git status --short` was empty. Stashes `wip-before-generic-rich-content-editor` and `wip-essay-before-richcontent-foundation` were preserved. This milestone has no commit or push.

Only Active Draft persistence, restore, atomic frozen snapshots, empty Retry and the acknowledged Submit barrier were implemented. The Shared Practice SINGLE_CHOICE development entry consumes the new service; all seven supported Core submit types share the freeze transaction. No History UI/replay or other frontend renderer migration is included. No human Workspace was created, and no existing user database was deleted/rebuilt. V1–V5 and workspace asset-index migrations are unchanged.

## Reproducible verification

Java 21: `C:\Program Files\Android\Android Studio\jbr`. Commands run from the repository root:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:Path = $env:JAVA_HOME + '\bin;' + $env:Path
npm.cmd --prefix quizforge-desktop-app/editor-web/draft-canvas test
npm.cmd --prefix quizforge-desktop-app/editor-web/draft-canvas run build
mvn.cmd -B '-Dquizforge.build.directory=target/draft-persistence' '-Dtest=DraftCanvasValueTest,DraftCanvasPersistenceTest,DraftCanvasDocumentTest,DraftLifecycleContractTest,PracticePersistenceIntegrationTest,SharedPracticeAdapterTest,SharedPracticeCanvasWebViewTest,SharedPracticeContractWebViewTest,PracticeMutationQueueTest,DraftCanvasWebViewTest,DraftPersistenceWebViewTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn.cmd -B '-Dquizforge.build.directory=target/draft-persistence' test
# Isolated recheck of the full-run startup timeout; original reports remain untouched.
mvn.cmd -B '-Dquizforge.build.directory=target/draft-persistence-startup-recheck' '-Dtest=DesktopStartupTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

| Gate | Result |
| --- | --- |
| Frontend tests | 42 passed; 0 failed/skipped |
| Local bundle build | Passed; generated resources updated |
| Targeted Java reactor | 87 passed: Core 3, Infrastructure 49, Desktop 35; 0 failures/errors/skips |
| Full Java reactor | 679 executed: 678 passed, 0 assertion failures, 1 DesktopStartupTest TimeoutException, 0 skipped; BUILD FAILURE |
| Isolated startup recheck | 1 passed; 0 failures/errors/skips; BUILD SUCCESS |

Full run totals: Core 73/0 errors, Infrastructure 324/0 errors, Desktop 282/1 error. It completed in 36:19 at 2026-10-03 11:00:23 +08:00. The only error is the existing `DesktopStartupTest` FutureTask.get(40 seconds), before the Draft tests run. The timeout and original assertions are unchanged. No full-green claim is made from passing subsets or overwritten XML reports; the new-process recheck uses a separate build directory.

The isolated new-process startup recheck passed and completed at 2026-10-03 11:05:33 +08:00 (reactor total 03:46, including fresh compilation). All original startup assertions and the 40-second wait are preserved. The original full run remains recorded as BUILD FAILURE; no second complete full run was performed. The one timeout was not reproduced in this recheck, and its intermittent cause is not established.

The first targeted native run exposed a WebKit timer binding error. DraftAutosave now calls timers through arrow wrappers; rebuilt bundles and the subsequent targeted run passed. The old native Retry assertion was updated from retained ink to the newly required empty Canvas, while preserving its answer, attempt-history and pointer regression checks.

New tests cover immutable collections and geometry/version validation; strict codec round trip and rejection; active overwrite/reopen; FK/cascade; duplicate append and SQL UPDATE rejection; V5→V6 with old data/checksums preserved; all seven submit types; snapshot INSERT failure and active DELETE failure rolling back the whole submission; no-active/older attempts; debounce, sequenced save errors and immediate last-stroke Submit. There are 27 new Java test cases (3 Core, 21 Infrastructure, 3 Desktop) and 5 new frontend cases.

The real WebView immediate-submit test adds ink and confirms submission before waiting for debounce. It checks durable Snapshot A includes the final stroke, active rows disappear, Retry is empty, Snapshot B is independent and A stays unchanged. The failure test injects a real SQLite save trigger: no Attempt/Snapshot is created, dirty ink stays, operation sequence consumes the failure without advancing authoritative state, then a successful acknowledged retry permits submission. Pure frontend tests also hold the save ACK unresolved to verify Submit cannot cross the barrier early.

## Native interactive acceptance

Agent operated the visible JavaFX window using native mouse actions. This is UI/database acceptance evidence, not a claim that the human user performed the steps. Isolated retained database: `target/draft-persistence-acceptance/native-20261003.db`. Input QBank: read-only `examples/step7-practice/Java集合练习.qbank`. The existing normal QuizForge window was left untouched.

1. Opened SINGLE_CHOICE Shared Practice, drew two strokes, panned and zoomed.
2. SQLite stored both strokes, viewport `(-121.66666666666663, -84.66666666666663, 1.2)` and card `(120, 70, 720)`.
3. Closed normally and launched a new Java process with the same database. Both strokes and viewport restored visually; stored JSON matched the pre-close document exactly.
4. Selected A, added a third stroke, confirmed Submit. Live Canvas became empty and Active Draft was removed; INITIAL Attempt froze three strokes with the original camera/card.
5. Clicked Retry. Answer selection and Canvas were empty with default viewport `(0, 0, 1)`.
6. Selected B, drew a different single horizontal stroke, confirmed Submit. RETRY Attempt froze only that stroke; live Canvas again became empty.
7. Verified both immutable snapshots and closed the acceptance window normally.

Native tool calls include an inspection between actions, so they do not establish a sub-500ms last-stroke-to-submit interval. The immediate timing/ACK requirement is verified by the automated real WebView and frontend tests described above.

## Database evidence

| Field | Snapshot A | Snapshot B |
| --- | --- | --- |
| Attempt ID | `pa_c347d2d1-5209-43ed-b99b-eb12b78e1b31` | `pa_253907b1-b44b-483e-a97c-b38ddaaa4538` |
| Attempt no / mode | 1 / INITIAL | 2 / RETRY |
| Stroke count | 3 | 1 |
| Viewport | (-121.66666666666663, -84.66666666666663, 1.2) | (0, 0, 1) |
| Card | (120, 70, 720) | (120, 70, 720) |
| Canonical JSON SHA-256 | `cf5b119ea86526cf917f60e87742658b3521791b4308295e10967e7185f73505` | `d6b3e5abfb48da27416354115261f5f241dc49f72516d9211f1d3d81470233fd` |

Snapshot A raw JSON stayed exactly equal to the captured first-submit document after Retry and second submission. Final Active Draft count: **0**. `PRAGMA foreign_key_check`: **no rows**. Flyway versions **1–6** are successful. Isolated artifacts `before-reopen.json`, `snapshot-A.json`, `snapshot-B.json`, `database-evidence.json` remain under ignored `target/draft-persistence-acceptance/`.

Logs: `target/draft-persistence-frontend-test.log`, `target/draft-persistence-targeted.log`, `target/draft-persistence-full-test.log`, `target/draft-persistence-startup-recheck.log`; original XML reports in each module's `target/draft-persistence/surefire-reports/`. Full-count evidence is retained in `target/draft-persistence-acceptance/full-test-evidence.json`. Recheck XML goes to `target/draft-persistence-startup-recheck/surefire-reports/`. These generated outputs and the acceptance database are not source files for commit.

## Limits and exact next History work

The normal Practice UI has not been replaced; the persistent Canvas is still the isolated SINGLE_CHOICE entry. Core freeze is type independent. Only schema 1.0/layout 1 are supported. Async transport, reconnect/lost-ACK recovery and durable cross-process operation ordering remain deferred. A crash before the 500ms autosave may lose unsaved ink; successful close and Submit capture the latest document synchronously/with ACK. No Revision copy policy is introduced. Historical replay requires stable question content, assets, fonts and layout compatibility in addition to stored geometry.

Next milestone: read snapshot by Attempt ID, resolve immutable question content/assets/fonts, validate schema/layout, render World geometry with stored card width, provide read-only viewport pan/zoom, and handle old Attempts without a snapshot. No replay control may write Active Draft or mutate historical snapshots.
