import { readingRenderer } from '../reading/index.js';
import { mountCompositeSelection } from '../composite-selection/renderer.js';
import { element } from '../../shared/renderer/contract.js';
/** Marker numbers are display addresses only; Core supplies the stable blank IDs. */
function renderPrompt(prompt, q, select) {
  const marker = /(\\)?\{\{([1-9][0-9]*)}}/g; let end = 0;
  for (const match of q.prompt.text.matchAll(marker)) {
    prompt.append(document.createTextNode(q.prompt.text.slice(end,match.index))); end=match.index+match[0].length;
    if (match[1]) { prompt.append(document.createTextNode(match[0].slice(1))); continue; }
    const item=q.presentation.items.find(i=>i.number===Number(match[2]));
    if (!item) throw new TypeError('Unknown inline blank');
    const input=element('select','inline-blank');input.dataset.childId=item.id;input.setAttribute('aria-label',`第 ${item.number} 空`);
    input.append(new Option(`${item.number}._______`,''));
    item.options.forEach(o=>input.append(new Option(`${item.number}.${o.content.text}`,o.id)));
    prompt.append(input);
  } prompt.append(document.createTextNode(q.prompt.text.slice(end)));
}
export const clozeRenderer=Object.freeze({
 id:'builtin.cloze.v1',questionType:'CLOZE',selectionMode:'COMPOSITE_SINGLE',label:'完形填空',
 parse(q) { const parsed=readingRenderer.parse(q);return {...parsed,selectionMode:'COMPOSITE_SINGLE'}; },
 mount(form,q,context) { return mountCompositeSelection(form,q,{...context,renderPrompt}); }
});
