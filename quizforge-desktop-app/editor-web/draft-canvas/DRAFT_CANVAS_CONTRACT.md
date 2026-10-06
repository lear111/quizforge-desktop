# Draft Canvas Contract v1

Draft Persistence v1 connects these contracts to the existing Practice SQLite lifecycle. [History Draft Replay v1](HISTORY_DRAFT_REPLAY.md) adds the formal read-only Attempt projection; other frontend question types remain deferred. Geometry and lifecycle semantics below are unchanged. See [DRAFT_PERSISTENCE.md](DRAFT_PERSISTENCE.md) for tables, transactions and the bridge barrier.

## Implemented versus future integration

| Contract | Implemented now | Deferred work |
| --- | --- | --- |
| Logical layout | Fixed document width, shared World transform, explicit zoom/pan/fit controls | Content/assets/fonts must be available for historical replay |
| Geometry | Explicit JS schema, shared Core value objects, strict Infrastructure codec, canonical SQLite JSON, fixed-width History replay | Cross-platform font binary stability |
| Lifecycle | Active/frozen repositories, atomic freeze/remove on submit, empty Retry, read-only Attempt replay; pure specification remains a regression reference | Replay of other question types |
| Ordering | JS single flight and response watermarks; Java FX-thread FIFO mutation queue | Async scheduling, reconnect/ack recovery and durable cross-process ordering |

**The live Shared Practice now restores/saves Active Draft and freezes it on successful submission.** Submit clears live/active geometry; Retry starts with the default empty canvas. `DraftLifecycleContract` remains a pure reference model; runtime coordination uses PracticeSessionService and SQLite transactions.

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

The canonical parser requires all shown fields. Coordinates must be finite numbers; widths/zoom positive; pressure in [0,1]; strokes nonempty point arrays with unique nonblank IDs, PEN tool and hexadecimal colors. JS normalization projects only contract fields; the Infrastructure persisted codec rejects unknown fields rather than losing data; DOM/SVG markup, answer/result state, tool mode, undo history, event counters and operation sequences are excluded.

### Java / JS boundary

The floating whiteboard toolbar adds optional `texts` and `paper` fields to schema 1.0/layout 1. `texts` stores plain World annotations `{id,x,y,width,size,color,text}` with unique IDs and at most 10000 characters per item; `paper` stores `{color,pattern}` where pattern is PLAIN, DOTS, LINES or GRID. Missing extensions retain the original five-field JSON encoding. Straight lines and rectangles use ordinary PEN point arrays. Undo includes annotations and paper; camera changes remain independent. These fields are part of active/frozen draft snapshots, never formal answers.

- JS `src/canvas/document.js`: `parseDraftCanvasDocument`, `createDraftCanvasDocument`, `upgradePocDraft`.
- Core `practice.draft.DraftCanvasDocument`: immutable record with nested Viewport/QuestionCard/Stroke/Point, `createEmpty`, `withViewport`. Infrastructure `DraftCanvasJsonCodec`: `decode`, `encode`, explicit `upgradePoc`. The desktop adapter consumes the one Core model.
- Both tests read `test/fixtures/document-v1.json`. Java lists and points are immutable; JS normalized values are detached copies.
- Strict canonical parsing never silently defaults width/color/pressure. Only the named old-POC upgrade path recognizes an absent layoutVersion and supplies layout 1 plus the original missing defaults (width720, color#7660ab, pressure0.5). An explicit unsupported/null layoutVersion is rejected. Development JSON import uses that compatibility boundary; future stored canonical snapshots must use strict parsing.
- Java additionally rejects duplicate JSON keys and trailing tokens. JS JSON.parse collapses duplicate keys; duplicate-key raw input is not canonical interchange. The Java persistence codec rejects it before database storage.

## Draft lifecycle — runtime and executable reference

`DraftLifecycleContract` is separate from Practice grading/state transitions. Its methods take external successful Core signals and external Attempt IDs. It never creates Attempts, judges answers, modifies Core or accesses a database.

| Event | Active working Draft | Historical frozen Draft |
| --- | --- | --- |
| Create/edit | Mutable working slot containing detached document values | Existing snapshots unchanged |
| Submit success | Freeze the current value, then remove the active slot | Append immutable snapshot associated with the Core-issued Attempt ID |
| Submit failure | Same active document | No new/changed snapshot |
| Retry after submission | Empty answer and a new empty canvas; fresh default camera/card and empty ink | Previous Attempts and snapshots remain unchanged |

Frozen snapshots retain the document's schemaVersion, layoutVersion, viewport, card geometry and ink. Duplicate Attempt snapshot IDs cannot overwrite an existing snapshot. Later edits replace the active document value and cannot mutate frozen lists/points.

**REVISION differs from RETRY.** Revision concerns correcting/amending a prior submission; Retry starts a fresh answer and fresh canvas while preserving history. No Revision Draft copy policy is added. Existing Core Revision behavior is not changed.

Submit finishes/captures the active gesture/document at the submission boundary and awaits its save ACK. The Attempt, immutable captured Draft and active-row removal commit atomically; failure preserves active state. The bridge locks edits during this barrier. The reference model has no persistence behavior; runtime freezes within the existing SQLite transaction.

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

Answer events are not coalesced. ANSWER_CHANGED #41 and ANSWER_CHANGED #42 execute in that order. Completed Canvas changes are coalesced for 500ms before DRAFT_CHANGED is issued through the same sequence channel. Submit captures the latest geometry, awaits its DRAFT_CHANGED ACK, then issues SUBMIT. SUBMIT/RETRY are barriers and are never merged with other events. Old answer SUCCESS/ERROR cannot overwrite SUBMITTED; old submit SUCCESS/ERROR cannot overwrite RETRYING.

The queue and sequence watermark are in memory only. They provide local ordering, not durable idempotency, network reconnect, lost-response replay or a distributed transaction. Future transport must preserve the single-flight request contract or add an explicit ordering/recovery protocol. No Tablet/LAN/Extension implementation is present.

## Persistence integration

The domain/repository/transaction integration is implemented in Draft Persistence v1. See [DRAFT_PERSISTENCE.md](DRAFT_PERSISTENCE.md). [History Draft Replay v1](HISTORY_DRAFT_REPLAY.md) uses frozen question/answer metadata and attemptId snapshots, strict version checks, original World geometry and a read-only History surface. Shared CSS and font stack are reused; long-term font binary stability and replay for other question types remain unresolved.

## Verification

See [DRAFT_PERSISTENCE_ACCEPTANCE.md](DRAFT_PERSISTENCE_ACCEPTANCE.md) for current results and `CONTRACT_HARDENING_ACCEPTANCE.md` for the earlier contract stage. Tests cover Java/JS JSON interoperability, schema/layout versions, lifecycle, fixed-width DOM layout under window resize/pan/zoom, stale/duplicate sequences, SQLite rollback, autosave/submit barrier, reopen and native Canvas/Practice regressions.
