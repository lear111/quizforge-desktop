# Draft Canvas Contract v1

This phase fixes internal contracts before persistence. It does not save Drafts to SQLite, add migrations or Attempt fields, connect History, or migrate any other question type. Existing standalone Canvas and SINGLE_CHOICE development entries remain available.

## Implemented versus future integration

| Contract | Implemented now | Deferred SQLite / History integration |
| --- | --- | --- |
| Logical layout | Fixed document width, shared World transform, explicit zoom/pan/fit controls | Content/assets/fonts must be available for historical replay |
| Geometry | Explicit JS schema and deeply immutable Java DTO; v1 validation and old POC upgrade | Store canonical JSON and validate versions when reading |
| Lifecycle | Pure executable future specification and tests | Connect actual submit/retry transaction to active/frozen Draft repositories |
| Ordering | JS single flight and response watermarks; Java FX-thread FIFO mutation queue | Async scheduling, reconnect/ack recovery and durable cross-process ordering |

**The live POC still retains ink on submit and Retry.** `DraftLifecycleContract` is a tested specification, not a live coordinator. The UI states this temporary behavior. No historical DraftSnapshot is created at runtime in this phase.

## World and Screen Coordinates

World coordinates are the only persisted geometry: card x/y/width, pen width and stroke point x/y. Viewport x/y are camera offsets in World units. Screen coordinates are relative to the viewport element, not absolute monitor/client/page coordinates:

```text
screen = (world + viewportOffset) * zoom
world  = screen / zoom - viewportOffset
screenPointer = event.client - viewport.getBoundingClientRect().origin
```

JS applies `translate(viewport.x * zoom, viewport.y * zoom) scale(zoom)` to the common World parent of live DOM and SVG. Device pixel ratio, monitor scaling and window dimensions are not written into the document. Pan converts screen delta to World offset by dividing by zoom. Zoom preserves the chosen screen anchor's World point, and changes only the camera. Undo/redo affects strokes; viewport changes do not rewrite stroke history.

## Fixed logical layout

- New documents start with `questionCard = {x:120, y:70, width:720}`.
- The stored width is the document's fixed logical width. Importing an older snapshot with another positive width preserves it; resize never derives width from the window.
- The renderer sets width, min-width and max-width from this stored value; the card is an absolute World child with no responsive max-width or viewport-based font sizing.
- A narrow window clips the viewport; users pan or zoom. Footer controls offer zoom in/out and “适应窗口”, which adjusts the camera, not card width.
- Toolbars/footer may wrap to fit their window. This does not change card logical text layout.
- `layoutVersion: "1"` identifies the current card/renderer/CSS rules: fixed stored border-box width, normal live DOM content flow, current bundled font stack and styles. New card defaults use 720 World units and 28 units of padding. Practice responses may change card height/content; ink is anchored to World geometry, not individual text spans.

Tests measure actual `offsetWidth`, individual word Range line indices and normalized x/y/width, plus SVG rendered `getBoundingClientRect()` bounds relative to the card and local `getBBox()` geometry. Narrow/wide windows, pan and zoom must preserve these values. Screenshots are supplementary evidence only.

JavaFX WebKit's `getScreenCTM()` omitted the ancestor CSS scale in the measured test: at zoom0.65 its a/d remained1, while card width was468 (=720×0.65), SVG rendered width was74.75 (=115×0.65), and normalized relative position was correctly65/60. The acceptance test therefore checks actual rendered bounds and word ranges; CTM is retained as a diagnostic, not treated as the painted position.

## DraftCanvasDocument v1

```json
{
  "schemaVersion": "1.0",
  "layoutVersion": "1",
  "viewport": { "x": -25.5, "y": 17.25, "zoom": 1.5 },
  "questionCard": { "x": 120, "y": 70, "width": 720 },
  "strokes": [{
    "id": "shared-contract-stroke-1",
    "tool": "PEN",
    "color": "#7054a5",
    "width": 2.4,
    "points": [{ "x": 150.25, "y": 100.5, "pressure": 0.5 },
               { "x": 240.75, "y": 125.5, "pressure": 0.8 }]
  }]
}
```

`schemaVersion` identifies the data structure; `layoutVersion` identifies layout rules. They are validated separately, never inferred from one another. Only schema 1.0/layout 1 are implemented. Unknown versions fail before replacing state; this is the compatibility decision point for future replay, not a multi-version renderer.

The canonical parser requires all shown fields. Coordinates must be finite numbers; widths/zoom positive; pressure in [0,1]; strokes nonempty point arrays with unique nonblank IDs, PEN tool and hexadecimal colors. Normalization projects only contract fields; DOM/SVG markup, answer/result state, tool mode, undo history, event counters and operation sequences are excluded.

### Java / JS boundary

- JS `src/canvas/document.js`: `parseDraftCanvasDocument`, `createDraftCanvasDocument`, `upgradePocDraft`.
- Java `poc.draftcanvas.contract.DraftCanvasDocument`: explicit record and nested Viewport/QuestionCard/Stroke/Point, `parse`, `toJson`, `createEmpty`, `withViewport`, `upgradePoc`.
- Both tests read `test/fixtures/document-v1.json`. Java lists and points are immutable; JS normalized values are detached copies.
- Strict canonical parsing never silently defaults width/color/pressure. Only the named old-POC upgrade path recognizes an absent layoutVersion and supplies layout 1 plus the original missing defaults (width720, color#7660ab, pressure0.5). An explicit unsupported/null layoutVersion is rejected. Development JSON import uses that compatibility boundary; future stored canonical snapshots must use strict parsing.
- Java additionally rejects duplicate JSON keys and trailing tokens. JS JSON.parse collapses duplicate keys; duplicate-key input is not canonical interchange and must be rejected by the Java persistence boundary in a future phase.

## Future Draft lifecycle — executable specification only

`DraftLifecycleContract` is separate from Practice grading/state transitions. Its methods take external successful Core signals and external Attempt IDs. It never creates Attempts, judges answers, modifies Core or accesses a database.

| Event | Active working Draft | Historical frozen Draft |
| --- | --- | --- |
| Create/edit | Mutable working slot containing detached document values | Existing snapshots unchanged |
| Submit success | Freeze the current value, then remove the active slot | Append immutable snapshot associated with the Core-issued Attempt ID |
| Submit failure | Same active document | No new/changed snapshot |
| Retry after submission | Empty answer and a new empty canvas; fresh default camera/card and empty ink | Previous Attempts and snapshots remain unchanged |

Frozen snapshots retain the document's schemaVersion, layoutVersion, viewport, card geometry and ink. Duplicate Attempt snapshot IDs cannot overwrite an existing snapshot. Later edits replace the active document value and cannot mutate frozen lists/points.

**REVISION differs from RETRY.** Revision concerns correcting/amending a prior submission; Retry starts a fresh answer and fresh canvas while preserving history. This phase implements no Revision Draft copy policy. Existing Core Revision behavior is not changed.

Next-phase submit integration must finish/capture the active gesture/document at the submission boundary. The Attempt, immutable captured Draft and active-row removal must commit atomically; failure must preserve active state. An async implementation must prevent editing that would be lost while that captured submission is in flight, or explicitly reconcile an active revision. The current pure model has no async/persistence behavior.

## Operation ordering contract

A WebView page is one fixed Practice session/question scope. Initial `loadPractice(viewModelJson)` happens once; a new scope recreates the page and sequence state. Java/Core remains authoritative. There is no optimistic permanent SUBMITTED state.

JS sends:

```json
{"type":"ANSWER_CHANGED","sessionId":"...","sessionQuestionId":"...",
 "operationSeq":41,"selectedOptionIds":["..."]}
```

SUBMIT and RETRY omit selectedOptionIds and carry the same stable identity fields plus their own sequence. Sequences are positive JS-safe integers (1 through 9007199254740991), strictly increasing within this page scope.

Java responds once through `applyResponse(responseJson)`:

```json
{"operationSeq":41,"status":"SUCCESS","viewModel":{"schemaVersion":"1.0","session":{},"question":{}},"error":null}
```

The illustrated viewModel placeholder is replaced by the complete existing Shared Practice DTO. ERROR uses the same operationSeq and the pre-operation authoritative ViewModel, with `error: {"message":"..."}`. The renderer does not replace its authoritative state for an ERROR.

- `lastAppliedSeq` is the last **handled response** watermark, including a failure.
- `lastAuthoritativeSeq` advances only after applying a successful authoritative ViewModel.
- Response seq <= lastAppliedSeq is ignored before its payload/error touches DOM, checked selection, busy state or confirmation.
- While an operation is in flight, only its matching sequence can complete it; stale or unexpected responses cannot release that request.
- With no request pending, a fresh sequenced host response can be applied; its sequence advances the next local issue counter. This also supports direct host bridge verification.
- An ERROR consumes a transport sequence to prevent older successes from reviving old state, but does not advance authoritative state. A new user retry gets a new sequence.
- A local transport exception releases that single flight as a failed transport sequence and restores displayed controls from the current authoritative DTO. No permanent optimistic result is synthesized.
- Initial state loads are not an unsequenced response escape hatch: the renderer rejects a second load on the same page.

### Serialization and barriers

UI mutations are single flight: radio/submit/retry controls wait for completion. Java requires the JavaFX thread and drains one `PracticeMutationQueue` FIFO. Nested bridge events enqueue behind the current mutation; state is read at execution time. Duplicate/stale request sequences never call Core again. Invalid/missing sequences cannot mutate Core.

No debounce/coalescing is implemented. Therefore ANSWER_CHANGED #41, ANSWER_CHANGED #42, SUBMIT #43 execute in that order. SUBMIT/RETRY are barriers and are never merged with answer events. Old answer SUCCESS/ERROR cannot overwrite SUBMITTED; old submit SUCCESS/ERROR cannot overwrite RETRYING.

The queue and sequence watermark are in memory only. They provide local ordering, not durable idempotency, network reconnect, lost-response replay or a distributed transaction. Future transport must preserve the single-flight request contract or add an explicit ordering/recovery protocol. No Tablet/LAN/Extension implementation is present.

## Next SQLite integration points — planned, not implemented

1. Promote platform-neutral geometry/lifecycle value types into the appropriate domain module; keep JSON parsing/storage in infrastructure. Preserve schema/layout validation and immutable copies.
2. Define active Draft identity by the real sessionQuestionId and an immutable Attempt DraftSnapshot association by external attemptId. Add explicit repository ports for active and frozen documents rather than putting DOM state into PracticePayload.
3. Extend `PracticeTransaction.Repositories` and `SqlitePracticeTransaction` for these repositories. Add the actual tables/migration only in that next task.
4. Integrate `PracticeSessionService.submitAnswer` inside its existing transaction: append Attempt, persist captured frozen Draft, remove active Draft, then commit. Retry clears the existing Core answer and creates a new empty active Draft in the same transaction. Failed operations roll back all rows together.
5. Adapter/bridge then exchange actual active Draft changes under the mutation ordering/barrier rules. Persist no viewport pixel coordinates or stroke SVG. Coordinate Canvas edit capture with submission before clearing live ink.
6. Replay will need canonical document version checks together with the corresponding immutable Practice question/content/assets. History UI and replay fallbacks remain outside this phase.

## Verification

See `CONTRACT_HARDENING_ACCEPTANCE.md` for measured results. Tests cover Java/JS JSON interoperability, schema/layout versions, pure lifecycle, fixed-width DOM layout under window resize/pan/zoom, stale/duplicate sequences, real Core failure rollback, and original native Canvas/Practice interaction regressions.
