import { readPractice } from './contract.js';
import { OperationOrdering, operationSeq } from '../bridge/ordering.js';
import './style.css';

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}
/** All displayed practice state comes from Java. Local state is limited to request/confirmation UI. */
export function mountPracticeCard(root, sendIntent, interactionAllowed = () => true) {
  let authoritative = null, busy = false, destroyed = false, confirmation = false, error = '';
  const ordering = new OperationOrdering();
  function alive() { if (destroyed) throw new Error('Shared Practice UI has been destroyed'); }
  function controls() {
    if (!authoritative) return;
    root.querySelectorAll('input[name=practice-answer]').forEach(input => {
      input.checked = authoritative.question.selectedOptionIds.includes(input.value);
      input.closest('.practice-option').classList.toggle('selected', input.checked);
      input.disabled = busy || authoritative.question.state === 'SUBMITTED' || confirmation;
    });
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
    const fragment = document.createDocumentFragment(), q = vm.question;
    const heading = element('div', 'card-heading');
    heading.append(element('span', 'tag', '单选题'), element('span', '', `第 ${q.index + 1} / ${q.total} 题`));
    const state = element('span', 'practice-state', { UNANSWERED: '未作答', DRAFT: '已选择', SUBMITTED: '已提交', RETRYING: '重试中' }[q.state]);
    state.id = 'practice-state'; state.dataset.state = q.state;
    const meta = element('div', 'practice-meta'); meta.append(element('span', 'practice-score', `分值：${q.maxScore}`), state);
    const prompt = element('p', 'practice-prompt', q.prompt.text); prompt.id = 'practice-prompt';
    fragment.append(heading, meta, prompt);
    const form = element('form'); form.id = 'practice-form';
    const options = element('fieldset', 'practice-options');
    const legend = element('legend', '', '选择答案'); options.appendChild(legend);
    q.options.forEach((option, index) => {
      const label = element('label', `practice-option feedback-${option.feedback.toLowerCase()}`);
      label.dataset.optionId = option.id;
      const radio = element('input'); radio.type = 'radio'; radio.name = 'practice-answer'; radio.value = option.id;
      radio.id = `practice-option-${index}`;
      label.append(radio, element('span', 'practice-option-text', `${String.fromCharCode(65 + index)}. ${option.content.text}`));
      options.appendChild(label);
    });
    form.appendChild(options);
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
    confirm.appendChild(confirmActions); form.appendChild(confirm); fragment.appendChild(form);
    const result = element('section', 'practice-result'); result.id = 'practice-result'; result.hidden = !q.result;
    if (q.result) {
      // Display the Core-issued result; never compare the user's answer to generate a grade.
      result.append(element('p', `practice-result-${q.result.status.toLowerCase()}`, q.result.status === 'CORRECT' ? '回答正确' : '回答错误'),
        element('p', 'practice-earned-score', `得分：${q.result.score} / ${q.result.maxScore}`));
      const labels = q.result.correctOptionIds.map(id => String.fromCharCode(65 + q.options.findIndex(option => option.id === id))).join('、');
      result.appendChild(element('p', 'practice-correct-answer', `正确答案：${labels}`));
      if (q.result.analysis.text) result.append(element('strong', '', '答案与解析'), element('p', 'practice-analysis', q.result.analysis.text));
    }
    fragment.appendChild(result);
    const message = element('p', 'practice-error'); message.id = 'practice-error'; message.setAttribute('role', 'alert'); message.hidden = true;
    fragment.appendChild(message);
    // Next phase: coordinate stroke reset with formal Practice persistence. No temporary DB coupling here.
    fragment.appendChild(element('p', 'practice-stage-note', '本阶段重试会保留白板笔迹。'));
    root.replaceChildren(fragment); root.dataset.practiceState = q.state;
  }
  function renderAuthoritative(next, seq) {
    const focus = root.contains(document.activeElement) ? document.activeElement.id : null;
    render(next); authoritative = next;
    if (seq !== undefined) ordering.complete(seq, true);
    busy = false; confirmation = false; error = ''; controls();
    if (focus && interactionAllowed()) root.querySelector(`#${focus}`)?.focus();
  }
  function loadPractice(value) {
    alive();
    // A page is one operation scope. Recreating the page is required for another question/session.
    if (authoritative) throw new Error('Practice is already loaded for this page');
    renderAuthoritative(readPractice(value));
  }
  function applyResponse(value) {
    alive();
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
      renderAuthoritative(next, seq);
    } else {
      if (typeof response.error?.message !== 'string' || !response.error.message.trim())
        throw new TypeError('Failed response requires an error message');
      ordering.complete(seq, false);
      // No permanent optimistic state and no failed response replacing authoritative Practice state.
      busy = false; confirmation = false; error = response.error.message; controls();
    }
    return true;
  }
  function emit(type, selectedOptionIds) {
    if (destroyed || busy || !authoritative || !interactionAllowed()) { controls(); return; }
    const seq = ordering.begin();
    busy = true; error = ''; controls();
    const event = { type, sessionId: authoritative.session.sessionId,
      sessionQuestionId: authoritative.question.sessionQuestionId, operationSeq: seq };
    if (type === 'ANSWER_CHANGED') event.selectedOptionIds = selectedOptionIds;
    try { sendIntent(event); }
    catch (failure) {
      if (ordering.complete(seq, false)) {
        busy = false; confirmation = false; error = failure.message || '操作未完成，请重试。'; controls();
      }
    }
  }
  const change = event => {
    if (event.target.matches('input[name=practice-answer]')) emit('ANSWER_CHANGED', [event.target.value]);
  };
  const submit = event => {
    if (event.target.id !== 'practice-form') return;
    event.preventDefault();
    if (busy || !interactionAllowed() || !authoritative?.question.selectedOptionIds.length) return;
    confirmation = true; controls(); root.querySelector('#practice-confirm-submit')?.focus();
  };
  const click = event => {
    const button = event.target.closest('button');
    if (!button || !root.contains(button) || busy || !interactionAllowed()) return;
    if (button.id === 'practice-retry') emit('RETRY');
    else if (button.id === 'practice-confirm-submit') { confirmation = false; emit('SUBMIT'); }
    else if (button.id === 'practice-cancel-submit') { confirmation = false; controls(); }
  };
  root.addEventListener('change', change); root.addEventListener('submit', submit); root.addEventListener('click', click);
  return Object.freeze({
    loadPractice, applyResponse,
    getOperationState() { alive(); return ordering.getState(); },
    getViewState() { alive(); return authoritative ? JSON.parse(JSON.stringify(authoritative)) : null; },
    destroy() { if (destroyed) return; destroyed = true; root.removeEventListener('change', change); root.removeEventListener('submit', submit); root.removeEventListener('click', click); }
  });
}
