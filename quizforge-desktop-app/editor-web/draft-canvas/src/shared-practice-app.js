import './style.css';
import { mountDraftCanvas } from './canvas/core.js';
import { mountSharedQuestionRuntime } from './shared/runtime/question-runtime.js';
import { practiceChannel } from './bridge/practice.js';
import { DraftAutosave } from './bridge/autosave.js';

const object = document.querySelector('#question-card');
const canvas = mountDraftCanvas(object);
const channel = practiceChannel(() => window.practiceHost);
let autosave, restored = false, mute = false, submitting = false, transitioning = false;
let submissionIdle = Promise.resolve(), finishSubmission;
const editable = () => practice.getViewState()?.question.state !== 'SUBMITTED';
function restoreDraft(value) {
  mute = true;
  try { canvas.loadDraft(value); autosave.reset(canvas.getDraft()); restored = true; canvas.setEditable(editable()); }
  finally { mute = false; }
}
const practice = mountSharedQuestionRuntime(object, channel.send, () => canvas.diagnostics().mode === 'INTERACT' && !submitting && !transitioning, {
  focusTarget(node) { canvas.focusElement(node); },
  lockSubmit() { submitting = true; submissionIdle = new Promise(resolve => { finishSubmission = resolve; }); canvas.setEditable(false); },
  async beforeSubmit() { autosave.changed(canvas.getDraft()); await autosave.flush(); },
  afterSubmit() { submitting = false; canvas.setEditable(editable() && !transitioning); finishSubmission?.(); },
  onResponse(response) { if (response.draftDocument) restoreDraft(JSON.stringify(response.draftDocument)); }
});
autosave = new DraftAutosave(document => practice.saveDraft(document));
canvas.onChange(json => { if (restored && !mute && editable()) autosave.changed(json); });
window.draftCanvas = canvas;
window.sharedPractice = Object.freeze({ ...practice, restoreDraft, isSubmitting: () => submitting,
  refreshCurrent(value) { practice.refreshPractice(value); transitioning = false; canvas.setEditable(editable()); },
  flushToHost(id) {
    transitioning = true; canvas.setEditable(false);
    Promise.resolve().then(async () => {
      await submissionIdle;
      await practice.whenIdle();
      // Submitted geometry has already been frozen/removed by Core.
      if (editable()) { autosave.changed(canvas.getDraft()); await autosave.flush(); }
    }).then(() => window.practiceHost.onFlushCompleted(id, true, ''), failure => {
      transitioning = false; canvas.setEditable(editable());
      window.practiceHost.onFlushCompleted(id, false, failure.message || '草稿保存失败');
    });
  },
  flushPendingDraft() { autosave.changed(canvas.getDraft()); return autosave.flush(); },
  autosaveState: () => autosave.state(), bindHost: channel.ready, diagnostics: channel.diagnostics });
canvas.addDisposer(() => { autosave.destroy(); practice.destroy(); });
document.querySelector('#mode-help').textContent = '交互：选择答案、提交或重试。';
