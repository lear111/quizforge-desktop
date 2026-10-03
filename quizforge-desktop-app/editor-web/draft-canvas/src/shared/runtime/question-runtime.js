import { readPractice } from '../../practice/contract.js';
import { QuestionRendererRegistry } from '../renderer/registry.js';
import { RendererMode, element } from '../renderer/contract.js';
import { OperationOrdering, operationSeq } from '../../bridge/ordering.js';
import '../../practice/style.css';

/** All displayed practice state comes from Java. Local state is limited to request/confirmation UI. */
export function mountSharedQuestionRuntime(root, sendIntent, interactionAllowed = () => true, hooks = {}) {
  const readOnly = hooks.readOnly === true;
  const mode = readOnly ? RendererMode.READ_ONLY_HISTORY : RendererMode.ACTIVE;
  let renderer = null;
  let authoritative = null, busy = false, destroyed = false, confirmation = false, error = '';
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
    renderer?.setInteractionMode(busy || authoritative.question.state === 'SUBMITTED' || confirmation ? 'DISABLED' : 'INTERACT');
    const submit = root.querySelector('#practice-submit'), retry = root.querySelector('#practice-retry');
    if (submit) submit.disabled = busy || !authoritative.question.selectedOptionIds.length;
    if (retry) retry.disabled = busy;
    root.querySelectorAll('#practice-confirmation button').forEach(button => { button.disabled = busy; });
    const panel = root.querySelector('#practice-confirmation');
    if (panel) panel.hidden = !confirmation;
    const message = root.querySelector('#practice-error');
    if (message) { message.textContent = error; message.hidden = !error; }
    root.setAttribute('aria-busy', String(busy));
  }
  function render(vm) {
    renderer?.destroy();
    const fragment = document.createDocumentFragment(), q = vm.question;
    const definition = QuestionRendererRegistry.require(q.type);
    const heading = element('div', 'card-heading');
    heading.append(element('span', 'tag', definition.label), element('span', '', `第 ${q.index + 1} / ${q.total} 题`));
    const state = element('span', 'practice-state', { UNANSWERED: '未作答', DRAFT: '已选择', SUBMITTED: '已提交', RETRYING: '重试中' }[q.state]);
    state.id = 'practice-state'; state.dataset.state = q.state;
    const meta = element('div', 'practice-meta'); meta.append(element('span', 'practice-score', `分值：${q.maxScore}`), state);
    fragment.append(heading, meta);
    const form = element('form'); form.id = 'practice-form';
    renderer = definition.mount(form, q, { mode, contentRoot: fragment, canInteract: () => !busy && !confirmation && interactionAllowed(),
      answerChanged: intent => { emit('ANSWER_CHANGED', intent.selectedOptionIds).catch(() => {}); } });
    if (!readOnly) {
    const actions = element('div', 'practice-actions');
    const button = element('button', q.state === 'SUBMITTED' ? 'practice-retry' : 'practice-submit', q.state === 'SUBMITTED' ? '↻  重试' : '✓  提交答案');
    button.id = q.state === 'SUBMITTED' ? 'practice-retry' : 'practice-submit';
    button.type = q.state === 'SUBMITTED' ? 'button' : 'submit'; actions.appendChild(button); form.appendChild(actions);
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
    root.replaceChildren(fragment); root.dataset.practiceState = q.state;
  }
  function renderAuthoritative(next, seq) {
    const focus = root.contains(document.activeElement) ? document.activeElement.id : null;
    render(next); authoritative = next;
    if (seq !== undefined) ordering.complete(seq, true);
    busy = false; confirmation = false; error = ''; controls();
    if (focus && interactionAllowed()) root.querySelector(`#${focus}`)?.focus();
  }
  function parse(value) {
    try { return readPractice(value); }
    catch (failure) {
      const message = element('p', 'practice-error', failure.message); message.id = 'practice-unsupported'; message.setAttribute('role', 'alert');
      renderer?.destroy(); renderer = null; root.replaceChildren(message); throw failure;
    }
  }
  function loadPractice(value) {
    alive();
    // A page is one operation scope. Recreating the page is required for another question/session.
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
      } else renderAuthoritative(next, seq);
      hooks.onResponse?.(response, next);
      settled();
    } else {
      if (typeof response.error?.message !== 'string' || !response.error.message.trim())
        throw new TypeError('Failed response requires an error message');
      ordering.complete(seq, false);
      // No permanent optimistic state and no failed response replacing authoritative Practice state.
      busy = false; confirmation = false; error = response.error.message; controls();
      settled(new Error(response.error.message));
    }
    return true;
  }
  function emit(type, selectedOptionIds, document) {
    if (readOnly || destroyed || busy || !authoritative || (!['DRAFT_CHANGED', 'SUBMIT'].includes(type) && !interactionAllowed())) { controls(); return Promise.reject(new Error('Practice mutation unavailable')); }
    const seq = ordering.begin();
    const reply = new Promise((resolve, reject) => { pendingReply = { resolve, reject }; });
    busy = true; error = ''; controls();
    const event = { type, sessionId: authoritative.session.sessionId,
      sessionQuestionId: authoritative.question.sessionQuestionId, operationSeq: seq };
    if (type === 'ANSWER_CHANGED') event.selectedOptionIds = selectedOptionIds;
    if (type === 'DRAFT_CHANGED') event.document = document;
    try { sendIntent(event); }
    catch (failure) {
      if (ordering.complete(seq, false)) {
        busy = false; confirmation = false; error = failure.message || '操作未完成，请重试。'; controls(); settled(failure);
      }
    }
    return reply;
  }
  const submit = event => {
    if (event.target.id !== 'practice-form') return;
    event.preventDefault();
    if (readOnly) return; // Also prevent native form navigation on a read-only history card.
    if (busy || !interactionAllowed() || !authoritative?.question.selectedOptionIds.length) return;
    confirmation = true; controls(); root.querySelector('#practice-confirm-submit')?.focus();
  };
  const click = event => {
    const button = event.target.closest('button');
    if (!button || !root.contains(button) || busy || !interactionAllowed()) return;
    if (button.id === 'practice-retry') emit('RETRY').catch(() => {});
    else if (button.id === 'practice-confirm-submit') {
      confirmation = false;
      // Lock canvas edits throughout capture/save ACK/submit, including asynchronous transports.
      hooks.lockSubmit?.();
      Promise.resolve().then(() => hooks.beforeSubmit?.()).then(() => {
        busy = false; return emit('SUBMIT');
      }).catch(failure => { busy = false; error = failure.message; controls(); })
        .finally(() => hooks.afterSubmit?.());
    }
    else if (button.id === 'practice-cancel-submit') { confirmation = false; controls(); }
  };
  root.addEventListener('submit', submit);
  if (!readOnly) root.addEventListener('click', click);
  return Object.freeze({
    loadPractice, refreshPractice, applyResponse,
    loadHistory(value) {
      alive(); if (!readOnly) throw new Error('History loading requires a read-only card');
      const next = parse(value);
      if (next.question.state !== 'SUBMITTED') throw new Error('History requires a submitted Attempt');
      renderAuthoritative(next);
    },
    whenIdle: idle,
    async saveDraft(document) { await idle(); return emit('DRAFT_CHANGED', undefined, document); },
    getOperationState() { alive(); return ordering.getState(); },
    getViewState() { alive(); return authoritative ? JSON.parse(JSON.stringify(authoritative)) : null; },
    destroy() { if (destroyed) return; destroyed = true; settled(new Error('Practice closed')); renderer?.destroy(); renderer = null; root.removeEventListener('submit', submit); root.removeEventListener('click', click); }
  });
}
