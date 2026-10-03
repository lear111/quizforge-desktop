# Draft Persistence v1

This milestone implements Active Draft + immutable Attempt DraftSnapshot under the existing Practice service/SQLite transaction. It does not add History UI/replay, other frontend renderers, network transports or grading behavior.

The following milestone now uses this persistence from the formal Practice browsing area: [Practice Draft Mode v1](PRACTICE_DRAFT_MODE.md). The isolated POC launcher below remains a development tool; the formal surface uses the current Workspace database and Session.

## Shared model and strict codec

`quizforge-core/practice/draft/DraftCanvasDocument` is the single formal Java model. Nested Viewport, QuestionCard, Stroke and Point are immutable records with finite/positive geometry, supported version validation, unique stroke IDs and defensive collections. ActiveDraftCanvas carries sessionQuestionId/document/updatedAt; AttemptDraftSnapshot carries attemptId/document/createdAt. Core has no Jackson, JavaFX, WebView or JS dependency.

Infrastructure `DraftCanvasJsonCodec` encodes/decodes canonical v1. It rejects missing/unknown fields, unknown schema/layout, malformed JSON, duplicate keys, trailing tokens and string-to-number coercion. The explicit old-POC upgrade helper is not used for database input. Versions exist only in document_json; no competing version columns. World geometry and viewport offsets remain separate from device pixels.

## V6 schema

V1–V5 are unchanged. `V6__draft_canvas.sql` creates:

| Table | Identity / ownership | Data |
| --- | --- | --- |
| practice_draft_canvas | session_question_id PK → practice_session_question(id), ON DELETE CASCADE | document_json, updated_at |
| attempt_draft_snapshot | attempt_id PK → question_attempt(id), ON DELETE CASCADE | document_json, created_at |

The snapshot port has only find/append. Duplicate append fails; a SQLite BEFORE UPDATE trigger rejects modification even through direct SQL. Parent deletion still performs the required cascade. Workspace asset-index `workspace.db` is unchanged; no database is deleted/rebuilt.

## Service and atomic lifecycle

PracticeSessionService.loadActiveDraftCanvas/saveActiveDraftCanvas take sessionId, expectedContentId and questionId. They enforce ACTIVE session, current question/view, revision match and question existence. Save also rejects SUBMITTED questions; Retry/Revision working states follow existing answer rules. Geometry save touches activity time but does not manufacture an answer or change answer state.

Both repository ports belong to PracticeTransaction.Repositories. SqlitePracticeTransaction constructs all five repositories from one JDBC transaction connection. Every supported submit branch calls the same appendAttemptWithDraft helper:

1. Append Core-issued QuestionAttempt.
2. Find ActiveDraftCanvas by sessionQuestionId.
3. If present, append AttemptDraftSnapshot using that immutable document and Attempt timestamp.
4. Delete Active Draft, update submitted answer state/activity and commit.

Failure at any step rolls back all writes, including Attempt/Snapshot and question state. Absent Active Draft does not create a forced empty snapshot. Retry clears answer and deletes Active Draft in the same transaction; row absence is logical default empty Canvas. Previous Attempts/snapshots are not copied, updated or deleted. findAttemptDraftSnapshot exposes the historical data for future History work.

## Autosave and acknowledged Submit barrier

Canvas change subscriptions notify completed pen/eraser/pan gestures, undo/redo/clear, zoom/fit and JSON import; they do not notify each pointermove. Shared Practice DraftAutosave coalesces these changes for 500ms and emits DRAFT_CHANGED through the existing sequenced mutation channel. ACK is returned only after Core transaction commit. An ERROR keeps dirty geometry and does not advance authoritative state; it is displayed and can be retried.

Confirm Submit locks canvas edits and other practice intents, finishes/captures the latest gesture, cancels debounce and awaits flushPendingDraft. The flush drains changes through matching successful ACKs. Only then is SUBMIT sent. Save failure prevents SUBMIT and restores editing. Java then freezes the durable Active row atomically. Successful Submit/Retry responses reset the live Canvas and pending autosave under a muted restore path, so reset does not recreate Active rows. Submitted Canvas is not editable.

Operation sequences remain monotonic/page-local and single flight; Java uses its existing FIFO. DRAFT_CHANGED ACK does not replace the card DOM or overwrite a newer local canvas edit. Submit/Retry are barriers. Transport remains local synchronous JavaFX; async/lost-ACK/reconnect recovery is not implemented.

## Reopen and close

Initial bridge ready loads Practice through Core, then loads Active Draft or default empty geometry from SQLite. Restore does not autosave. The launcher now retains an isolated database at `target/draft-persistence-acceptance/practice.db` by default; `-Database` selects another explicit path. No new human Workspace is created. Existing temporary test contexts remain scoped to their own directories.

Close also captures unfinished/latest geometry on the FX thread and saves through Core if it differs from the last persisted value. Save failure keeps the window open and reports the error. No browser localStorage is used. A process crash before the 500ms save can still lose unsaved changes; successful close/submit capture does not depend on that timer firing.

## History read-only projection

[History Draft Replay v1](HISTORY_DRAFT_REPLAY.md) now reads the immutable snapshot by `attemptId` through `PracticeHistoryService.loadDraftReplay`. Its formal RESULT/DRAFT surface reuses Canvas Core, the SINGLE_CHOICE renderer and exact Practice CSS, retains stored geometry, supports viewing pan/zoom and never reads or saves Active Draft. Legacy Attempts without snapshots keep their ordinary result page. Active Draft remains mutable working state; AttemptDraftSnapshot remains immutable historical state; Replay is only a read-only projection.

See [DRAFT_PERSISTENCE_ACCEPTANCE.md](DRAFT_PERSISTENCE_ACCEPTANCE.md) for measured tests and native UI/database evidence.
