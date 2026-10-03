# Shared Practice SINGLE_CHOICE acceptance — 2026-10-03

Baseline HEAD: `9650171f7e6d6bd3b1676be195456861e72b38e9`.

The previous POC was copied into ignored `target/shared-practice-baseline/` before extraction. Original standalone entry and its tests remain. Existing stashes remain; no commit or push.

## Source and isolation

- Actual package: `examples/step7-practice/Java集合练习.qbank` (read only).
- First question: `q_demo_arraylist_structure`; three options, correct option `opt_demo_01_a`, maximum 1.
- Existing Core and SQLite Practice APIs supply the session, draft, grading and Attempts.
- Integration tests use JUnit temporary databases; launcher uses its own temporary directory and removes it on close.
- No fixed Workspace asset, migration, formal Practice route or History change.

## Verification

| Verification | Result |
| --- | --- |
| Frontend Node tests | 21 passed: original 15 geometry/model tests + 6 contract/channel tests |
| Local bundle build | Passed, original and Shared Practice bundles |
| Java targeted tests | 15 passed: original WebView 3 + Shared Adapter 8 + Shared WebView 4; 0 failures/errors/skips (01:31 min) |
| Full Maven suite | BUILD SUCCESS; 635 tests: Core 70, Infrastructure 303, Desktop 262; 0 failures/errors/skips; 12:23 min, finished 2026-10-03 02:08:28 +08:00 |

Logs are ignored build artifacts: `target/shared-practice-targeted.log`, `target/shared-practice-full-test.log`.
Native screenshot output: `quizforge-desktop-app/target/shared-practice-poc/native-final.png`.

## Interactive acceptance checklist

Use `tools/Start-SharedPracticeCanvas.ps1` from the repository root. The launcher is an isolated development window, not the formal Practice screen.

1. Read the real ArrayList SINGLE_CHOICE prompt.
2. INTERACT: select B; state becomes DRAFT with B selected.
3. PEN: draw across the card/radio; selection stays B.
4. INTERACT: selection remains B.
5. Submit and confirm; Core gives INCORRECT / 0 out of 1.
6. Retry; selected answers and result clear, state becomes RETRYING; ink remains.
7. Select A and confirm submission; Core gives CORRECT / 1 out of 1.
8. The existing Core has two distinct Attempts, INITIAL then RETRY; INITIAL remains unchanged.
9. PAN: card and ink move together; ERASER removes ink without changing practice state.

This checklist is exercised by native JavaFX Robot tests. Human acceptance is separate and must not be inferred from automated results.

Native test observed trusted pointerdown/move/up. It submitted B (INCORRECT, 0/1, INITIAL), retried with an empty selection while retaining ink, then submitted A (CORRECT, 1/1, RETRY). The original Attempt compared equal before/after the second submission. Bridge tests also exercised invalid/stale events and SQLite write-then-fail rollback with restored DRAFT UI and no Attempt.

Full-suite native screenshot was visually inspected: live prompt/radios, correct option green feedback, retained card ink, result/analysis and Retry all visible. Source resources matched the compiled JavaFX resources by SHA-256. Human mouse acceptance remains pending; the actual mouse sequence above was automated by Robot.

After testing, the isolated launcher was opened for user acceptance at 02:09 +08:00. Verified PID 8892, title `QuizForge — Shared Practice UI v1 / SINGLE_CHOICE`, Responding=true. Original standalone POC launcher remains available.

## Files added or changed in this phase

- Changed previous POC files: `src/app.js`, `scripts/build.mjs`, `README.md`, generated `draft-canvas.js`. Remaining 14 backed-up files are byte-identical; none are missing.
- Added frontend: `shared-practice.html`, `src/shared-practice-app.js`, `src/canvas/core.js`, `src/practice/{contract.js,renderer.js,style.css}`, `src/bridge/practice.js`, `test/practice.test.js`.
- Added docs: this file and `SHARED_PRACTICE.md`.
- Added Java: `SharedPracticeViewModel`, `SharedPracticeAdapter`, `SharedPracticeExample`, `SharedPracticeCanvasWebView`, `SharedPracticeCanvasLauncher` in the isolated `poc.sharedpractice` package.
- Added Java tests: `SharedPracticeAdapterTest`, `SharedPracticeCanvasWebViewTest`.
- Added generated local resources: `shared-practice.html`, `shared-practice.js`, `shared-practice.css`.
- Added script: `tools/Start-SharedPracticeCanvas.ps1`.
- `.gitignore` and `tools/Start-DraftCanvas.ps1` remain previous-phase changes; no new commit, stash or push.

## Limits and next-phase risks

- Only a single current SINGLE_CHOICE is rendered; no navigation or other type migration.
- TEXT reflects the current plain-text Core choice snapshot; rich content is deferred.
- Card is live DOM, so confirmation/results can change its height. Ink stays at World coordinates; it does not follow individual text reflow.
- Retry retains strokes in this phase, as the UI states. There is no Draft Canvas/Attempt persistence or snapshot link.
- Temporary Practice data is ephemeral; closing the launcher ends this development session.
- Core calls are synchronous on the JavaFX thread in this narrow local slice; slow I/O would need an ordered request pipeline and stale-response handling.
- Later phases must define version negotiation, attempt/Draft lifecycle, live object identity/navigation and text reflow anchoring before expanding types/platforms.
