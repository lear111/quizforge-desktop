import { readChoiceQuestion } from '../choice/contract.js';
import { mountCompositeSelection } from '../composite-selection/renderer.js';
export const readingRenderer = Object.freeze({
  id: 'builtin.reading.v1', questionType: 'READING', selectionMode: 'COMPOSITE_SINGLE', label: '阅读理解',
  parse(q) {
    if (!Array.isArray(q.presentation?.items) || !q.presentation.items.length) throw new TypeError('Reading items are missing');
    const items = q.presentation.items.map(item => {
      if (typeof item.id !== 'string' || !item.id.trim() || !Number.isInteger(item.number) || item.number < 1) throw new TypeError('Invalid reading item identity');
      const child = readChoiceQuestion({ ...q, selectionMode: 'SINGLE', prompt: item.prompt, options: item.options,
        selectedOptionIds: q.selectedOptionIds.filter(id => item.options.some(o => o.id === id)) }, { selectionMode: 'SINGLE' });
      return { id: item.id, number: item.number, prompt: child.prompt, options: child.options };
    });
    if (new Set(items.map(i => i.id)).size !== items.length) throw new TypeError('Reading child IDs must be unique');
    const all = readChoiceQuestion({ ...q, selectionMode: 'MULTIPLE', options: items.flatMap(i => i.options) }, { selectionMode: 'MULTIPLE' });
    return { ...all, selectionMode: 'COMPOSITE_SINGLE', presentation: { items } };
  },
  mount: mountCompositeSelection
});
