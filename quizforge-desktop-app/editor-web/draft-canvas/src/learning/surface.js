import { mountDraftCanvas } from '../canvas/core.js';
import { mountSharedQuestionRuntime } from '../shared/runtime/question-runtime.js';
import { DraftAutosave } from '../bridge/autosave.js';
import {createPageActions} from './page-actions.js';

/** One live card, annotation layer and World; mode controls capabilities only. */
export function mountSharedLearningSurface(object, channel) {
const canvas = mountDraftCanvas(object);
const pageActions=createPageActions(()=>window.pageHost);window.qfPageActions=pageActions;
let autosave, restored = false, mute = false, submitting = false, transitioning = false;
const atomicTransitions=Boolean(window.chrome?.webview);
let pendingRestore=null;
let heldCardSize=null;
function holdCardSize(){
  if(!atomicTransitions||heldCardSize)return;
  heldCardSize={height:object.style.height,minHeight:object.style.minHeight,overflow:object.style.overflow};
  object.style.height=object.offsetHeight+'px';object.style.minHeight=object.style.height;object.style.overflow='hidden';
}
function releaseCardSize(){
  if(!heldCardSize)return;
  Object.assign(object.style,heldCardSize);heldCardSize=null;
}
let learningMode = 'DRAFT', initializeViewport = false;
let submissionIdle = Promise.resolve(), finishSubmission;
const editable = () => practice.getViewState()?.question.state !== 'SUBMITTED';
function restoreDraft(value) {
  const data=typeof value==='string'?JSON.parse(value):value;
  const document=data.document || data;
  initializeViewport=data.hasSavedDraft===false && editable();
  mute = true;
  try { canvas.loadDraft(JSON.stringify(document),{hasSavedDraft:data.hasSavedDraft!==false}); autosave.reset(canvas.getDraft()); restored = true; canvas.setEditable(editable()); }
  finally { mute = false; }
}
const practice = mountSharedQuestionRuntime(object, channel.send, () => canvas.diagnostics().mode === 'INTERACT' && !submitting && !transitioning, {
  atomicTransitions,
  beforeReveal(){
    releaseCardSize();
    if(pendingRestore){const value=pendingRestore;pendingRestore=null;restoreDraft(value);}
    setLearningMode(learningMode);
  },
  pageState:questionId=>pageActions.getState(questionId),pageCommand:(questionId,action,argument)=>pageActions.request(questionId,action,argument),
  boardState:canvas.uiState,boardCommand:canvas.command,subscribeHost:canvas.onUiChange,
  layoutHost:{configure:canvas.configureLayout,getState:canvas.layoutState},
  onUiChanged(preferences,question) {
    if(question)window.practiceHost?.uiPreferences?.(question.sessionQuestionId,JSON.stringify(preferences));
  },
  focusTarget(node) { canvas.focusElement(node); },
  lockSubmit() { submitting = true; submissionIdle = new Promise(resolve => { finishSubmission = resolve; }); canvas.setEditable(false); canvas.setNavigationLocked(true); },
  async beforeSubmit() { autosave.changed(canvas.getDraft()); await autosave.flush(); },
  afterSubmit() { submitting = false; canvas.setEditable(editable() && !transitioning); canvas.setNavigationLocked(transitioning); practice.refreshInteraction(); finishSubmission?.(); },
  onResponse(response) {
    if(response.operationType==='DRAFT_CHANGED')canvas.markSavedGeometry();
    if (response.draftDocument) {restoreDraft({document:response.draftDocument,hasSavedDraft:response.operationType!=='RETRY'}); if(learningMode==='DRAFT')setLearningMode('DRAFT');}
  }
});
// Tool changes affect answer capabilities even when the question data has not changed.
canvas.onModeChange(() => practice.refreshInteraction());
autosave = new DraftAutosave(document => practice.saveDraft(document,{initialLayout:learningMode==='PRACTICE'}));
canvas.onChange(json => { if (restored && !mute && editable()) autosave.changed(json); });

function setLearningMode(next) {
  releaseCardSize();
  canvas.setLearningMode(next,{initializeViewport:next==='DRAFT' && initializeViewport});
  if(next==='DRAFT')initializeViewport=false;
  learningMode=next;transitioning=false;canvas.setEditable(editable());canvas.setNavigationLocked(false);
  practice.resumePresentation();
  practice.refreshInteraction();
}

const api = Object.freeze({ ...practice, restoreDraft, isSubmitting: () => submitting,
  setLearningMode,
  learningMode: () => learningMode,
  suspendCurrent() { practice.suspendPresentation();holdCardSize();transitioning = true; canvas.setEditable(false); canvas.setNavigationLocked(true); practice.refreshInteraction(); if(!atomicTransitions)object.style.visibility = 'hidden'; },
  replaceCurrent(value) {
    const data = typeof value === 'string' ? JSON.parse(value) : value;
    if(atomicTransitions){pendingRestore=data;practice.replacePractice(data.viewModel);}
    else{practice.replacePractice(data.viewModel);restoreDraft(data);setLearningMode(learningMode);object.style.visibility='';}
  },
  refreshCurrent(value) { practice.refreshPractice(value); transitioning = false; canvas.setEditable(editable());practice.resumePresentation(); practice.refreshInteraction(); },
  flushToHost(id) {
    practice.suspendPresentation();
    holdCardSize();
    transitioning = true; canvas.setEditable(false); canvas.setNavigationLocked(true);
    practice.refreshInteraction();
    Promise.resolve().then(async () => {
      await submissionIdle;
      await practice.whenIdle();
      // Submitted geometry has already been frozen/removed by Core.
      if (editable()) { autosave.changed(canvas.getDraft()); await autosave.flush(); }
    }).then(() => window.practiceHost.onFlushCompleted(id, true, ''), failure => {
      releaseCardSize();
      transitioning = false; canvas.setEditable(editable()); canvas.setNavigationLocked(false);
      practice.resumePresentation();
      practice.refreshInteraction();
      window.practiceHost.onFlushCompleted(id, false, failure.message || '草稿保存失败');
    });
  },
  flushPendingDraft() { autosave.changed(canvas.getDraft()); return autosave.flush(); },
  autosaveState: () => autosave.state(), bindHost: channel.ready,
  diagnostics: () => ({ ...channel.diagnostics(), ...practice.runtimeDiagnostics(), canvasHandlerCount: canvas.diagnostics().handlerCount }) });
canvas.addDisposer(() => { autosave.destroy(); practice.destroy();pageActions.destroy(); });
return Object.freeze({ canvas, practice: api });

}
