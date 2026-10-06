import { readPractice } from '../../practice/contract.js';
import { QuestionRendererRegistry } from '../renderer/registry.js';
import { RendererMode, element } from '../renderer/contract.js';
import { OperationOrdering, operationSeq } from '../../bridge/ordering.js';
import {defaultUi,applyPracticeUi,applyCanvasUi} from '../ui/preferences.js';
import '../../practice/style.css';
import {replaceChildrenRetainingFrames} from '../../extensions/protocol.js';

/** All displayed practice state comes from Java. Local state is limited to request/confirmation UI. */
export function mountSharedQuestionRuntime(root, sendIntent, interactionAllowed = () => true, hooks = {}) {
  const readOnly = hooks.readOnly === true;
  const mode = readOnly ? RendererMode.READ_ONLY_HISTORY : RendererMode.ACTIVE;
  let renderer = null;
  let rendererMountCount = 0;
  let authoritative = null, busy = false, destroyed = false, confirmation = false, error = '';
  let ui=defaultUi('PRACTICE'),renderScope=null,uiQuestion=null,submitting=false;
  let body=root,rendered=Promise.resolve(),pendingRender=null;
  const atomic=hooks.atomicTransitions===true;
  function applyUi() {
    applyPracticeUi(body,ui);
    if(!atomic || renderScope?.committed){
      root.classList.toggle('qf-bare-card',!ui.card);applyCanvasUi(root,ui);
      hooks.onUiChanged?.(ui,uiQuestion);
    }
  }
  const ordering = new OperationOrdering();
  let pendingReply = null; const idleWaiters = [];
  function settled(failure) {
    const reply = pendingReply; pendingReply = null;
    if (reply) failure ? reply.reject(failure) : reply.resolve();
    idleWaiters.splice(0).forEach(resolve => resolve());
  }
  function idle() { return busy ? new Promise(resolve => idleWaiters.push(resolve)) : Promise.resolve(); }
  function alive() { if (destroyed) throw new Error('Shared Practice UI has been destroyed'); }
  function controls() {
    if (!authoritative) return;
    renderer?.update(authoritative.question);
    renderer?.setReadOnly(readOnly);
    // Prepare the final UI geometry while the staging body is inert. Writes remain host-gated.
    const allowed = atomic&&renderScope&&!renderScope.committed ? true : interactionAllowed();
    renderer?.setInteractionMode(busy || submitting || !allowed || authoritative.question.state === 'SUBMITTED' || confirmation ? 'DISABLED' : 'INTERACT');
    const submit = body.querySelector('#practice-submit'), retry = body.querySelector('#practice-retry');
    if (submit) submit.disabled = busy || submitting || !allowed || !(renderer?.hasAnswer?.() ?? authoritative.question.selectedOptionIds.length);
    if (retry) retry.disabled = busy || submitting || !allowed;
    body.querySelectorAll('#practice-confirmation button').forEach(button => { button.disabled = busy || !allowed; });
    const panel = body.querySelector('#practice-confirmation');
    if (panel) panel.hidden = !confirmation;
    const message = body.querySelector('#practice-error');
    if (message) { message.textContent = error; message.hidden = !error || !ui.errors; }
    root.setAttribute('aria-busy', String(busy));
  }
  function render(vm) {
    pendingRender?.cancel();pendingRender=null;
    const previous=renderer,previousBody=body;
    const initialNodes=atomic&&previousBody===root?Array.from(root.childNodes):[];
    if(!atomic)previous?.destroy();
    const scope={committed:!atomic};renderScope=scope;ui=defaultUi('PRACTICE');
    if(atomic){
      body=element('div','qf-card-body');body.style.display='flow-root';
      body.style.visibility='hidden';body.inert=true;
      if(previousBody!==root){
        const padding=getComputedStyle(root);
        body.style.position='absolute';body.style.left=padding.paddingLeft;body.style.right=padding.paddingRight;body.style.top=padding.paddingTop;
        previousBody.inert=true;
      }
      root.dataset.qfSwitching='true';
    }
    const fragment = document.createDocumentFragment(), q = vm.question;
    uiQuestion=q;
    const definition = QuestionRendererRegistry.require(q.type,q.rendererVersion||q.presentation?.extensionVersion);
    const heading = element('div', 'card-heading');
    heading.append(element('span', 'tag', definition.label), element('span', 'question-position', `第 ${q.index + 1} / ${q.total} 题`));
    const state = element('span', 'practice-state', { UNANSWERED: '未作答', DRAFT: '已选择', SUBMITTED: '已提交', RETRYING: '重试中', REVISING: '修订中' }[q.state]);
    state.id = 'practice-state'; state.dataset.state = q.state;
    const meta = element('div', 'practice-meta'); meta.append(element('span', 'practice-score', `分值：${q.maxScore ?? '未设置'}`), state);
    fragment.append(heading, meta);
    const form = element('form'); form.id = 'practice-form';
    renderer = definition.mount(form, q, { mode, contentRoot: fragment,
      isCurrent:()=>!destroyed&&renderScope===scope,
      isReady:()=>!atomic||scope.committed,
      canInteract: () => renderScope===scope&&!busy && !submitting && !confirmation && (atomic&&!scope.committed||interactionAllowed()),
      ...(hooks.pageState?{pageState:()=>hooks.pageState(q.sessionQuestionId),pageCommand:(action,argument)=>{
        if(atomic&&!scope.committed)throw new Error('题卡正在准备');
        return hooks.pageCommand(q.sessionQuestionId,action,argument);
      }}:{}),
      ...(hooks.boardState?{boardState:hooks.boardState,boardCommand:(...args)=>{
        if(atomic&&!scope.committed)throw new Error('题卡正在准备');
        return hooks.boardCommand(...args);
      },subscribeHost:hooks.subscribeHost}:{}),
      ...(hooks.layoutHost?{layoutHost:{getState:hooks.layoutHost.getState,configure(patch,options){
        if(renderScope!==scope)return;
        if(!atomic||scope.committed)return hooks.layoutHost.configure(patch,options);
        scope.layout=[patch,options];
      }}}:{}),
      async reloadPage(){
        await idle();if(destroyed||renderScope!==scope)return;
        render(authoritative);controls();
      },
      uiChanged(preferences){if(destroyed || renderScope!==scope)return;ui=preferences;applyUi();},
      answerChanged: intent => emit('ANSWER_CHANGED', intent), requestSubmit,
      requestRetry: () => emit('RETRY'),
      answerEdited: () => { const button=root.querySelector('#practice-submit'); if(button)button.disabled=busy||!renderer.hasAnswer(); } });
    rendererMountCount++;
    if (!readOnly) {
    const actions = element('div', 'practice-actions');
    const button = element('button', q.state === 'SUBMITTED' ? 'practice-retry' : 'practice-submit', q.state === 'SUBMITTED' ? '↻  重试' : '✓  提交答案');
    button.id = q.state === 'SUBMITTED' ? 'practice-retry' : 'practice-submit';
    button.type = q.state === 'SUBMITTED' ? 'button' : 'submit'; actions.appendChild(button); (form.querySelector('[data-qf-actions]') || form).appendChild(actions);
    const confirm = element('section', 'practice-confirmation'); confirm.id = 'practice-confirmation'; confirm.hidden = true;
    confirm.appendChild(element('p', '', '确认提交这道题的答案吗？提交后需重试才能重新作答。'));
    const confirmActions = element('div', 'practice-actions');
    for (const [id, text, cls] of [['practice-cancel-submit', '继续作答', ''], ['practice-confirm-submit', '确认提交', 'practice-submit']]) {
      const b = element('button', cls, text); b.id = id; b.type = 'button'; confirmActions.appendChild(b);
    }
    confirm.appendChild(confirmActions); form.appendChild(confirm);
    }
    fragment.appendChild(form);
    fragment.appendChild(renderer.renderResult());
    const message = element('p', 'practice-error'); message.id = 'practice-error'; message.setAttribute('role', 'alert'); message.hidden = true;
    fragment.appendChild(message);
    fragment.appendChild(element('p', 'practice-stage-note', readOnly ? '历史草稿 · 只读' : '草稿自动保存；提交后冻结，重试从空白草稿开始。'));
    if(atomic){
      body.appendChild(fragment);root.appendChild(body);
      const nextBody=body,nextRenderer=renderer;
      let cancelled=false;
      const cleanup=()=>{
        const closing=previous?.destroy();
        if(previousBody===root){initialNodes.forEach(node=>node.remove());return;}
        // Preserve reply delivery for navigation initiated by the retiring extension.
        if(previousBody.querySelector('.qf-frame-retiring')){
          previousBody.dataset.qfRetiredRoot='';previousBody.style.cssText='position:absolute;visibility:hidden;pointer-events:none';
          previousBody.querySelectorAll('[id]').forEach(node=>node.removeAttribute('id'));
          Promise.resolve(closing).finally(()=>previousBody.remove());
        }else previousBody.remove();
      };
      pendingRender={cancel(){cancelled=true;cleanup();nextRenderer.destroy();nextBody.remove();}};
      const reveal=()=>{
        if(cancelled||destroyed||renderScope!==scope)return;
        scope.committed=true;pendingRender=null;
        if(scope.layout)hooks.layoutHost.configure(...scope.layout);
        nextBody.style.position='';nextBody.style.left='';nextBody.style.right='';nextBody.style.top='';
        nextBody.style.visibility='';nextBody.inert=false;
        cleanup();root.dataset.practiceState=q.state;delete root.dataset.qfSwitching;
        applyUi();hooks.beforeReveal?.();controls();
      };
      // Ready publishes host state again. Drain that refresh and its geometry before replacing the visible card.
      rendered=Promise.resolve(nextRenderer.ready).then(()=>nextRenderer.flushAnswer?.())
        .then(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))))
        .then(reveal,error=>{reveal();throw error;});
      rendered.catch(()=>{});
    }else{replaceChildrenRetainingFrames(root,fragment);root.dataset.practiceState=q.state;}
    applyUi();
  }
  function renderAuthoritative(next, seq, preserveRenderer = false) {
    const focused = root.contains(document.activeElement) ? document.activeElement : null;
    const focus = focused?.id, selection=focused?.selectionStart==null?null:[focused.selectionStart,focused.selectionEnd];
    if (preserveRenderer && renderer && authoritative?.question.sessionQuestionId === next.question.sessionQuestionId) {
      const state = body.querySelector('#practice-state'); state.dataset.state = next.question.state;
      state.textContent = { UNANSWERED:'未作答', DRAFT:'已选择', SUBMITTED:'已提交', RETRYING:'重试中', REVISING:'修订中' }[next.question.state];
      root.dataset.practiceState = next.question.state;
      const action=body.querySelector('#practice-submit,#practice-retry');
      if(action){const submitted=next.question.state==='SUBMITTED';action.id=submitted?'practice-retry':'practice-submit';action.className=action.id;action.type=submitted?'button':'submit';action.textContent=submitted?'↻  重试':'✓  提交答案';}
    } else render(next);
    authoritative = next;
    if (seq !== undefined) ordering.complete(seq, true);
    busy = false; confirmation = false; error = ''; controls();
    if (focus && interactionAllowed()) {const node=document.getElementById(focus);node?.focus();if(selection&&node?.setSelectionRange)node.setSelectionRange(...selection);}
  }
  function parse(value) {
    try { return readPractice(value); }
    catch (failure) {
      const message = element('p', 'practice-error', failure.message); message.id = 'practice-unsupported'; message.setAttribute('role', 'alert');
      pendingRender?.cancel();pendingRender=null;renderScope=null;body=root;delete root.dataset.qfSwitching;
      renderer?.destroy(); renderer = null; replaceChildrenRetainingFrames(root,message); throw failure;
    }
  }
  function loadPractice(value) {
    alive();
    // Initial load only. Question navigation uses an idle scope replacement in the same page.
    if (authoritative) throw new Error('Practice is already loaded for this page');
    renderAuthoritative(parse(value));
  }
  function refreshPractice(value) {
    alive();
    const next = parse(value);
    if (busy || !authoritative || next.session.sessionId !== authoritative.session.sessionId
      || next.question.sessionQuestionId !== authoritative.question.sessionQuestionId)
      throw new Error('Refresh requires the same idle practice question');
    renderAuthoritative(next);
  }
  function applyResponse(value) {
    alive();
    if (readOnly) throw new Error('History card has no mutation responses');
    const response = typeof value === 'string' ? JSON.parse(value) : value;
    const seq = operationSeq(response?.operationSeq);
    // Check the watermark before inspecting stale payloads or touching DOM, errors or busy state.
    if (!ordering.canApply(seq)) return false;
    if (!['SUCCESS', 'ERROR'].includes(response.status)) throw new TypeError('Unknown operation response status');
    const next = readPractice(response.viewModel);
    if (!authoritative || next.session.sessionId !== authoritative.session.sessionId
      || next.question.sessionQuestionId !== authoritative.question.sessionQuestionId)
      throw new Error('Operation response identity does not match this page');
    if (response.status === 'SUCCESS') {
      if (response.error != null) throw new TypeError('Successful response must not contain an error');
      if (response.operationType === 'DRAFT_CHANGED') {
        ordering.complete(seq, true); busy = false; error = ''; controls();
      } else renderAuthoritative(next, seq, ['ANSWER_CHANGED','SUBMIT','RETRY'].includes(response.operationType));
      hooks.onResponse?.(response, next);
      settled();
    } else {
      if (typeof response.error?.message !== 'string' || !response.error.message.trim())
        throw new TypeError('Failed response requires an error message');
      ordering.complete(seq, false);
      // No permanent optimistic state and no failed response replacing authoritative Practice state.
      busy = false; confirmation = false; error = response.error.message; renderer?.rejectAnswer?.(); controls();
      const failure = new Error(response.error.message);
      if(typeof response.error.code==='string')failure.code=response.error.code;
      if(Array.isArray(response.error.issues))failure.issues=JSON.parse(JSON.stringify(response.error.issues));
      settled(failure);
    }
    return true;
  }
  function emit(type, answerIntent, document, initialLayout = false) {
    if (readOnly || destroyed || busy || !authoritative || (type === 'RETRY' && !interactionAllowed())) { controls(); return Promise.reject(new Error('Practice mutation unavailable')); }
    const seq = ordering.begin();
    const reply = new Promise((resolve, reject) => { pendingReply = { resolve, reject }; });
    busy = true; error = ''; controls();
    const event = { type, sessionId: authoritative.session.sessionId,
      sessionQuestionId: authoritative.question.sessionQuestionId, operationSeq: seq };
    if (type === 'ANSWER_CHANGED') Object.assign(event, answerIntent);
    if (type === 'DRAFT_CHANGED') {event.document = document;if(initialLayout)event.initialLayout=true;}
    try { sendIntent(event); }
    catch (failure) {
      if (ordering.complete(seq, false)) {
        busy = false; confirmation = false; error = failure.message || '操作未完成，请重试。'; controls(); settled(failure);
      }
    }
    return reply;
  }
  function requestSubmit() {
    if (readOnly || busy || submitting || !interactionAllowed() || !(renderer?.hasAnswer?.() ?? authoritative?.question.selectedOptionIds.length))
      throw new Error('Practice submission unavailable');
    if (!ui.confirmation) return performSubmit().then(()=>({confirmationRequired:false}));
    confirmation = true; controls(); root.querySelector('#practice-confirm-submit')?.focus();
    return { confirmationRequired: true };
  }
  const submit = event => {
    if (event.target.id !== 'practice-form') return;
    event.preventDefault();
    if (readOnly) return; // Also prevent native form navigation on a read-only history card.
    if (busy || !interactionAllowed() || !(renderer?.hasAnswer?.() ?? authoritative?.question.selectedOptionIds.length)) return;
    Promise.resolve(requestSubmit()).catch(()=>{});
  };
  function performSubmit() {
    if(submitting) return Promise.reject(new Error('答案正在提交'));
    confirmation=false;submitting=true;controls();
    return Promise.resolve().then(()=>hooks.lockSubmit?.()).then(()=>renderer?.flushAnswer?.()).then(()=>idle()).then(()=>hooks.beforeSubmit?.()).then(()=>{
      busy=false;return emit('SUBMIT');
    }).catch(failure=>{busy=false;error=failure.message;controls();throw failure;})
      .finally(()=>{submitting=false;hooks.afterSubmit?.();controls();});
  }
  const click = event => {
    const button = event.target.closest('button');
    if (!button || !root.contains(button) || busy || !interactionAllowed()) return;
    if (button.id === 'practice-retry') emit('RETRY').catch(() => {});
    else if (button.id === 'practice-confirm-submit') {
      performSubmit().catch(()=>{});
    }
    else if (button.id === 'practice-cancel-submit') { confirmation = false; controls(); }
  };
  root.addEventListener('submit', submit);
  if (!readOnly) root.addEventListener('click', click);
  let reload = Promise.resolve();
  const reloadExtension = event => {
    if (!authoritative || !event.detail.types.includes(authoritative.question.type)) return;
    reload = reload.then(async () => {
      const currentRenderer = renderer;
      if (!currentRenderer?.hasInitializationError?.()) await currentRenderer?.flushAnswer?.();
      await idle();
      if (destroyed || currentRenderer !== renderer) return;
      // Rebuild only the card DOM. Ordering, answers, confirmation and World geometry survive.
      render(authoritative); controls();
    }).catch(failure => { if (!destroyed) { error = '实时预览更新失败：' + failure.message; controls(); } });
  };
  window.addEventListener('qf-extension-updated', reloadExtension);
  return Object.freeze({
    loadPractice, refreshPractice, applyResponse,
    whenRendered:()=>rendered,
    clear(){
      alive();pendingRender?.cancel();pendingRender=null;renderScope=null;
      renderer?.destroy();renderer=null;authoritative=null;uiQuestion=null;body=root;rendered=Promise.resolve();
      replaceChildrenRetainingFrames(root);delete root.dataset.qfSwitching;delete root.dataset.practiceState;
    },
    suspendPresentation(){alive();renderer?.suspendPresentation?.();body.inert=true;},
    resumePresentation(){alive();body.inert=atomic&&renderScope&&!renderScope.committed;renderer?.resumePresentation?.();},
    refreshInteraction() { alive(); controls(); },
    replacePractice(value) {
      alive(); if (readOnly || busy) throw new Error('Question replacement requires an idle active runtime');
      renderAuthoritative(parse(value));
    },
    loadHistory(value) {
      alive(); if (!readOnly) throw new Error('History loading requires a read-only card');
      const next = parse(value);
      if (!['SUBMITTED', 'UNANSWERED', 'DRAFT', 'RETRYING'].includes(next.question.state)) throw new Error('Unsupported archived question state');
      renderAuthoritative(next);
    },
    focusTarget(targetId) { alive(); if (typeof targetId !== 'string' || !targetId.trim()) throw new TypeError('Target ID required'); const node = renderer?.focusTarget?.(targetId); if (node) hooks.focusTarget?.(node); return Boolean(node); },
    async whenIdle() { if(!renderer?.hasInitializationError?.())await renderer?.flushAnswer?.(); await idle(); },
    pendingAnswerIntent() { alive(); return renderer?.pendingAnswerIntent?.() ?? null; },
    async saveDraft(document,{initialLayout=false}={}) { await idle(); return emit('DRAFT_CHANGED', undefined, document, initialLayout); },
    getOperationState() { alive(); return ordering.getState(); },
    getViewState() { alive(); return authoritative ? JSON.parse(JSON.stringify(authoritative)) : null; },
    runtimeDiagnostics() { return { rendererInstanceCount: renderer ? 1 : 0, rendererMountCount, runtimeHandlerCount: destroyed ? 0 : readOnly ? 1 : 2 }; },
    destroy() { if (destroyed) return; destroyed = true; pendingRender?.cancel();pendingRender=null;settled(new Error('Practice closed')); renderer?.destroy(); renderer = null; root.removeEventListener('submit', submit); root.removeEventListener('click', click); window.removeEventListener('qf-extension-updated', reloadExtension); }
  });
}
