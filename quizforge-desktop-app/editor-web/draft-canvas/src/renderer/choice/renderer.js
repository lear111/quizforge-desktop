import { RendererMode, requireRendererMode, element } from '../../shared/renderer/contract.js';

/** Choice DOM/answer semantics only. Runtime owns submission, ordering, persistence and lifecycle. */
export function mountChoiceRenderer(form, initial, { mode, definition, contentRoot, canInteract, answerChanged }) {
  requireRendererMode(mode);
  const history = mode === RendererMode.READ_ONLY_HISTORY;
  let question = initial, readOnly = history, interaction = 'INTERACT', destroyed = false;
  const prompt = element('p', 'practice-prompt', question.prompt.text); prompt.id = 'practice-prompt';
  const options = element('fieldset', 'practice-options');
  options.appendChild(element('legend', '', '选择答案'));
  question.options.forEach((option, index) => {
    const label = element('label', `practice-option feedback-${option.feedback.toLowerCase()}`);
    label.dataset.optionId = option.id;
    const input = element('input'); input.type = definition.selectionMode === 'MULTIPLE' ? 'checkbox' : 'radio';
    input.name = 'practice-answer'; input.value = option.id; input.id = `practice-option-${index}`;
    label.append(input, element('span', 'practice-option-text', `${String.fromCharCode(65 + index)}. ${option.content.text}`));
    options.appendChild(label);
  });
  contentRoot.appendChild(prompt); form.appendChild(options);
  function alive() { if (destroyed) throw new Error('Question renderer destroyed'); }
  function synchronize() {
    options.querySelectorAll('input').forEach(input => {
      input.checked = question.selectedOptionIds.includes(input.value);
      input.disabled = readOnly || interaction !== 'INTERACT' || question.state === 'SUBMITTED';
      input.closest('.practice-option').classList.toggle('selected', input.checked);
    });
  }
  function getAnswerIntent() {
    alive(); return { selectedOptionIds: Array.from(options.querySelectorAll('input:checked'), input => input.value) };
  }
  const change = event => {
    if (!event.target.matches('input[name=practice-answer]')) return;
    if (destroyed || readOnly || interaction !== 'INTERACT' || question.state === 'SUBMITTED' || !canInteract()) {
      synchronize(); return;
    }
    answerChanged(getAnswerIntent());
  };
  // History has no answer event subscription at all, even if synthetic events are dispatched.
  if (!history) form.addEventListener('change', change);
  synchronize();
  return Object.freeze({
    update(next) { alive(); question = next; synchronize(); },
    getAnswerIntent,
    setInteractionMode(next) { alive(); if (!['INTERACT', 'DISABLED'].includes(next)) throw new TypeError('Unknown interaction mode'); interaction = next; synchronize(); },
    setReadOnly(value) { alive(); if (history && !value) throw new Error('History capability cannot be upgraded'); readOnly = Boolean(value); synchronize(); },
    renderResult() {
      alive(); const result = element('section', 'practice-result'); result.id = 'practice-result'; result.hidden = !question.result;
      const r = question.result;
      if (r) {
        result.append(element('p', `practice-result-${r.status.toLowerCase()}`, r.status === 'CORRECT' ? '回答正确' : '回答错误'),
          element('p', 'practice-earned-score', `得分：${r.score} / ${r.maxScore}`));
        const labels = r.correctOptionIds.map(id => String.fromCharCode(65 + question.options.findIndex(o => o.id === id))).join('、');
        result.appendChild(element('p', 'practice-correct-answer', `正确答案：${labels}`));
        if (r.analysis.text) result.append(element('strong', '', '答案与解析'), element('p', 'practice-analysis', r.analysis.text));
      }
      return result;
    },
    destroy() { if (destroyed) return; destroyed = true; form.removeEventListener('change', change); options.querySelectorAll('input').forEach(i => i.disabled = true); }
  });
}
