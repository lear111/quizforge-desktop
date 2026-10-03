# Draft Canvas Contract v1 Hardening acceptance — 2026-10-03

Baseline HEAD: `9650171f7e6d6bd3b1676be195456861e72b38e9`.
Current task builds on the prior uncommitted Canvas and Shared Practice POCs. 39 original files were backed up under ignored `target/contract-hardening-baseline/` before edits. No stash/commit/push, file removal, migration, Core Attempt change or History integration.

## Contract changes

- JS `src/canvas/document.js` and Java `poc.draftcanvas.contract.DraftCanvasDocument` define explicit schema1.0/layout1 geometry. Both use `test/fixtures/document-v1.json`.
- `src/model.js` preserves the stored logical card width, provides anchor-preserving zoom and explicit legacy POC import compatibility.
- Canvas rendering pins card width/min-width/max-width to stored World width. Footer zoom/fit controls change the camera only.
- JS `OperationOrdering` enforces single flight and separate response/authoritative watermarks. Renderer uses one sequenced `applyResponse` envelope and rejects old success/error before DOM changes.
- Java `PracticeMutationQueue` serializes mutation events, including nested calls. Duplicate/old request sequences never execute Core again.
- `DraftLifecycleContract` is an executable future specification ONLY. It tests immutable submission freeze, active removal, failure preservation and fresh empty Retry. Live POC retains ink; it is not wired to this specification.

Detailed rules and next persistence integration points: [DRAFT_CANVAS_CONTRACT.md](DRAFT_CANVAS_CONTRACT.md).

## Measured verification

| Gate | Result |
| --- | --- |
| Frontend tests | 37 passed, 0 failures |
| Frontend local build | Passed; both standalone and Shared Practice bundles |
| Java targeted tests | 32 passed; 0 failures/errors/skips; 01:42 min, finished 2026-10-03 02:43:21 +08:00 |
| Full Maven suite | 652 passed (Core70 / Infrastructure303 / Desktop279), 0 failures/errors/skips; BUILD SUCCESS; 12:30 min, finished 2026-10-03 02:56:16 +08:00 |

Commands from the repository root:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
npm.cmd --prefix quizforge-desktop-app/editor-web/draft-canvas test
npm.cmd --prefix quizforge-desktop-app/editor-web/draft-canvas run build
mvn.cmd -B -pl quizforge-desktop-app -am '-Dquizforge.build.directory=target/draft-poc' '-Dtest=DraftCanvasDocumentTest,DraftLifecycleContractTest,PracticeMutationQueueTest,SharedPracticeContractWebViewTest,SharedPracticeAdapterTest,SharedPracticeCanvasWebViewTest,DraftCanvasWebViewTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn.cmd -B '-Dquizforge.build.directory=target/draft-poc' test
```

Ignored logs: `target/contract-hardening-targeted.log`, `target/contract-hardening-full-test.log`, `target/contract-layout-debug.log`.

Final counts were independently summed from 82 Surefire XML reports (Core20 / Infrastructure24 / Desktop38); totals agree with the reactor log.

## Layout evidence

Test inputs include a multiline text probe in the actual card DOM, width720, card World180/95, and an ascending stroke245/155→360/175. Tests compare per-word Range line indices and logical positions, card offsetWidth, rendered SVG bounds and local SVG geometry, plus serialized geometry.

Window widths430 and1180, camera pan−83/+37, zoom0.65 and1.6 must leave serialized card/stroke geometry and logical word lines unchanged. Actual normalized stroke/card offset is65/60. No screenshot comparison is used as the sole layout evidence.

Full-run measurements: actual viewport widths1000→414→1164; logical card width720 throughout; long-text probe14 lines throughout. At zoom1.6 the rendered card width is1152 (=720×1.6), and normalized stroke offset65/60 is retained within0.2 tolerance. Every word's normalized position and line index is compared, not just the total height.

The first check used SVG getScreenCTM and failed at zoom0.65. Cross-checks found CTM a/d=1 while actual DOM/SVG bounds scaled correctly: card468wide and stroke74.75wide, normalized offset65/60. The check now measures actual rendered bounds, with CTM diagnostic retained. This is recorded explicitly rather than weakening relative-position or line-layout assertions.

Subsequent runs exposed invalid cached JSObject function references during both close and getDraft. Shared host function calls now resolve through live page globals; JSON/string payloads are passed as data through a transient window argument, not interpolated into code. Cleanup retains idempotent destruction and guaranteed page unload; all original regression checks remain enabled.

## Ordering and lifecycle evidence

- Pure ordering tests reject stale responses while a newer operation is pending; failure advances lastAppliedSeq but not lastAuthoritativeSeq.
- Actual WebView/Core tests execute choose#1, submit#2, retry#3, then replay old SUCCESS/ERROR envelopes and duplicate request events; submitted/retrying state remains authoritative.
- Existing injected Core transaction failure rolls back writes, leaves DRAFT/selection intact, emits matching sequence error and creates no fake Attempt.
- Pure future lifecycle tests verify deep immutability, freeze/removal after success, unchanged active state after failure, and fresh empty Retry preserving previous snapshots.
- Native mouse regressions continue to cover INTERACT/PEN/PAN/ERASER and INITIAL+RETRY Attempts. No touch/pen hardware or manual acceptance is claimed.

## Files changed in this phase

New frontend: `src/canvas/document.js`, `src/bridge/ordering.js`, `test/document.test.js`, `test/ordering.test.js`, `test/fixtures/document-v1.json`.

Changed frontend: `src/model.js`, `src/canvas/core.js`, `src/bridge/practice.js`, `src/practice/renderer.js`, `src/style.css`, both HTML entries, existing model/practice tests. Both generated HTML/JS/CSS resource sets rebuilt.

New Java: `DraftCanvasDocument`, `DraftLifecycleContract` in `poc.draftcanvas.contract`; `PracticeMutationQueue` in `poc.sharedpractice`. New tests: Document8, Lifecycle5, Queue2, Contract WebView2.

Changed Java: `SharedPracticeCanvasWebView`, `SharedPracticeCanvasWebViewTest` for sequenced envelopes.

Docs: added this file and `DRAFT_CANVAS_CONTRACT.md`; updated README and SHARED_PRACTICE architecture text. Prior acceptance records remain historical stage reports.

## Limits

- Schema1.0/layout1 only; old POC absent-layout upgrade is explicit, not an unknown-version renderer.
- Fixed width prevents viewport-induced reflow, but content/result/confirmation changes can alter height and text layout. Ink does not anchor to words.
- Font availability/engine differences across devices are not solved by fixed width; future replay needs content/assets/fonts and layout compatibility handling.
- Runtime still retains ink on Retry. Lifecycle model is only the next-stage contract; no Draft persistence/snapshot attachment exists.
- Ordering is page-local, synchronous on JavaFX, in memory; no distributed idempotency, lost-ack/reconnect recovery or asynchronous worker execution.
- Java rejects duplicate JSON keys; JS JSON.parse collapses them. Future persisted canonical input must pass the Java validation boundary.
