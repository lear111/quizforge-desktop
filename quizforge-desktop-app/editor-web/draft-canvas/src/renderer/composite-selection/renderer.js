import { RendererMode, requireRendererMode, element } from '../../shared/renderer/contract.js';
import { resultSection } from '../../shared/renderer/result.js';
/** Independent single selections within one parent identity and operation scope. */
export function mountCompositeSelection(form, initial, { mode, contentRoot, canInteract, answerChanged, renderPrompt }) {
  requireRendererMode(mode);
  const history = mode === RendererMode.READ_ONLY_HISTORY;
  let question = initial, readOnly = history, interaction = 'INTERACT', destroyed = false;
  const prompt = element('p', 'practice-prompt');
  if (renderPrompt) renderPrompt(prompt,question); else prompt.textContent=question.prompt.text; prompt.id = 'practice-prompt'; contentRoot.append(prompt);
  const groups = element('div', 'composite-items');
  question.presentation.items.forEach((item, n) => {
    const section = element('section', 'composite-item'); section.dataset.targetId = item.id; section.tabIndex = -1;
    section.append(element('h3', '', `${item.number}. ${item.prompt.text}`));
    const field = element('fieldset', 'practice-options'); field.append(element('legend', '', `第 ${item.number} 题`));
    item.options.forEach((option, i) => {
      const label = element('label', `practice-option feedback-${option.feedback.toLowerCase()}`); label.dataset.optionId = option.id;
      const input = element('input'); input.type = 'radio'; input.name = `practice-child-${n}`; input.value = option.id; input.id = `practice-child-${n}-${i}`;
      label.append(input, element('span', 'practice-option-text', `${String.fromCharCode(65 + i)}. ${option.content.text}`)); field.append(label);
    }); section.append(field); groups.append(section);
  }); form.append(groups);
  function alive() { if (destroyed) throw new Error('Question renderer destroyed'); }
  function sync() { groups.querySelectorAll('input').forEach(input => {
    input.checked = question.selectedOptionIds.includes(input.value); input.disabled = readOnly || interaction !== 'INTERACT' || question.state === 'SUBMITTED';
    input.closest('label').classList.toggle('selected', input.checked);
  });
    prompt.querySelectorAll('select[data-child-id]').forEach(input=>{
      const item=question.presentation.items.find(i=>i.id===input.dataset.childId);
      input.value=item.options.find(o=>question.selectedOptionIds.includes(o.id))?.id || '';
      input.disabled=readOnly || interaction!=='INTERACT' || question.state==='SUBMITTED';
    });
  }
  function getAnswerIntent() { alive(); return { selectedOptionIds: Array.from(groups.querySelectorAll('input:checked'), i => i.value) }; }
  const change = event => {
    if (!event.target.matches('input[type=radio],select[data-child-id]')) return;
    if (destroyed || readOnly || interaction !== 'INTERACT' || question.state === 'SUBMITTED' || !canInteract()) { sync(); return; }
    if(event.target.matches('select[data-child-id]')) {
      const item=question.presentation.items.find(i=>i.id===event.target.dataset.childId);
      groups.querySelectorAll('input').forEach(input=>{if(item.options.some(o=>o.id===input.value))input.checked=input.value===event.target.value;});
    }
    Promise.resolve(answerChanged(getAnswerIntent())).catch(()=>{});
  };
  if (!history) {form.addEventListener('change', change);prompt.addEventListener('change',change);} sync();
  return Object.freeze({
    update(next) { alive(); question = next; sync(); }, getAnswerIntent,
    hasAnswer() { alive(); return question.selectedOptionIds.length > 0; },
    focusTarget(id) { alive(); const node = Array.from(groups.children).find(n => n.dataset.targetId === id); if (!node) return null; node.focus({ preventScroll: true }); return node; },
    setInteractionMode(next) { alive(); if (!['INTERACT','DISABLED'].includes(next)) throw new TypeError('Unknown interaction mode'); interaction = next; sync(); },
    setReadOnly(value) { alive(); if (history && !value) throw new Error('History capability cannot be upgraded'); readOnly = Boolean(value); sync(); },
    renderResult() { alive(); return resultSection(question.result); },
    destroy() { if (destroyed) return; destroyed = true; form.removeEventListener('change', change); prompt.removeEventListener('change',change); prompt.querySelectorAll('select').forEach(i=>i.disabled=true); groups.querySelectorAll('input').forEach(i => i.disabled = true); }
  });
}
