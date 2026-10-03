import { mountDraftCanvas } from './canvas/core.js';
import { mountSharedQuestionRuntime } from './shared/runtime/question-runtime.js';
import { readHistoryReplay } from './history-replay.js';

const object = document.querySelector('#question-card');
const canvas = mountDraftCanvas(object, { accessMode: 'READ_ONLY' });
const card = mountSharedQuestionRuntime(object, () => { throw new Error('History cannot mutate Practice'); }, () => false, { readOnly: true, focusTarget: node => canvas.focusElement(node) });
let destroyed = false;
window.draftCanvas = canvas;
window.historyDraftReplay = Object.freeze({
  loadHistoryDraft(viewModel, document) {
    if (destroyed) throw new Error('History Draft page closed');
    // Validate both before replacing the visible attempt; never upgrade or rewrite snapshots.
    const replay = readHistoryReplay(viewModel, document);
    card.loadHistory(replay.viewModel);
    canvas.loadDraft(JSON.stringify(replay.document));
  },
  bindHost() { if (!destroyed) window.historyHost.ready(); },
  focusTarget: card.focusTarget,
  getViewState: card.getViewState,
  diagnostics() { return { accessMode: 'READ_ONLY', destroyed, canvas: canvas.diagnostics() }; },
  destroy() { if (destroyed) return; destroyed = true; card.destroy(); canvas.destroy(); }
});
document.querySelector('.toolbar strong').textContent = '历史草稿 · 只读';
document.querySelector('#mode-help').textContent = '拖动平移，使用缩放查看题卡和笔迹。';
