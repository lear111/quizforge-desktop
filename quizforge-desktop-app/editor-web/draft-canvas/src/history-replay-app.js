import { mountDraftCanvas } from './canvas/core.js';
import './extensions/sdk.js';
import { mountSharedQuestionRuntime } from './shared/runtime/question-runtime.js';
import { readHistoryReplay } from './history-replay.js';
import { createDraftCanvasDocument } from './canvas/document.js';
import {createPageActions} from './learning/page-actions.js';

const object = document.querySelector('#question-card');
const canvas = mountDraftCanvas(object, { accessMode: 'READ_ONLY' });
const atomicTransitions=Boolean(window.chrome?.webview);
let pendingReplay=null,heldSize=null;
function releaseSize(){if(heldSize){Object.assign(object.style,heldSize);heldSize=null;}}
function restoreReplay(){
  releaseSize();
  if(pendingReplay){canvas.loadDraft(JSON.stringify(pendingReplay.document));pendingReplay=null;}
  canvas.setLearningMode(learningMode);
}
const pageActions=createPageActions(()=>window.pageHost);window.qfPageActions=pageActions;
const card = mountSharedQuestionRuntime(object, () => { throw new Error('History cannot mutate Practice'); }, () => false, {
  atomicTransitions,beforeReveal:restoreReplay,
  pageState:questionId=>pageActions.getState(questionId),pageCommand:(questionId,action,argument)=>pageActions.request(questionId,action,argument),
  boardState:canvas.uiState,boardCommand:canvas.command,subscribeHost:canvas.onUiChange,
  layoutHost:{configure:canvas.configureLayout,getState:canvas.layoutState},
  readOnly: true, focusTarget: node => canvas.focusElement(node),
  onUiChanged(preferences,q) {if(q)window.historyHost?.uiPreferences?.(q.sessionQuestionId,JSON.stringify(preferences));}
});
let destroyed = false, learningMode = 'PRACTICE';
canvas.setLearningMode(learningMode);
window.draftCanvas = canvas;
window.historyDraftReplay = Object.freeze({
  loadHistoryDraft(viewModel, document) {
    if (destroyed) throw new Error('History Draft page closed');
    // Validate both before replacing the visible attempt; never upgrade or rewrite snapshots.
    const replay = readHistoryReplay(viewModel, document);
    if(atomicTransitions){
      if(!heldSize){heldSize={height:object.style.height,minHeight:object.style.minHeight,overflow:object.style.overflow};object.style.height=object.offsetHeight+'px';object.style.minHeight=object.style.height;object.style.overflow='hidden';}
      pendingReplay=replay;
    }
    try{card.loadHistory(replay.viewModel);}
    catch(error){pendingReplay=null;releaseSize();throw error;}
    if(!atomicTransitions){pendingReplay=replay;restoreReplay();}
    return card.whenRendered();
  },
  setLearningMode(mode) {
    if (destroyed) throw new Error('History page closed');
    learningMode = mode;if(!pendingReplay)canvas.setLearningMode(mode);
  },
  bindHost() {const notify=()=>{if(!destroyed)window.historyHost.ready();};return window.__qfExtensionsReady?window.__qfExtensionsReady.then(notify):notify();},
  clear() { if(destroyed)return;pendingReplay=null;releaseSize();card.clear();canvas.loadDraft(JSON.stringify(createDraftCanvasDocument())); },
  whenRendered:card.whenRendered,
  focusTarget: card.focusTarget,
  refreshInteraction:card.refreshInteraction,
  getViewState: card.getViewState,
  diagnostics() { return { accessMode: 'READ_ONLY', destroyed, canvas: canvas.diagnostics() }; },
  destroy() { if (destroyed) return; destroyed = true; card.destroy(); canvas.destroy();pageActions.destroy(); }
});
document.querySelector('.toolbar').setAttribute('aria-label','历史草稿工具，只读');
document.querySelector('#mode-help').textContent = '拖动平移，使用缩放查看题卡和笔迹。';
