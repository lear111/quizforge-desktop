const feedback = new Set(['NONE', 'CORRECT', 'INCORRECT']);
export function textContent(value, name) {
  if (value?.kind !== 'TEXT') throw new TypeError(`Unsupported content: ${name} supports TEXT only in v1`);
  if (typeof value.text !== 'string') throw new TypeError(`${name}.text must be text`);
  return { kind: 'TEXT', text: value.text };
}
export function optionIds(value, available, name) {
  if (!Array.isArray(value) || new Set(value).size !== value.length || value.some(v => !available.has(v)))
    throw new TypeError(`${name} must contain unique known option ids`);
  return [...value];
}
export function readChoiceQuestion(q, definition) {
  if (q.selectionMode !== undefined && q.selectionMode !== definition.selectionMode)
    throw new TypeError('Choice selectionMode does not match question type');
  if (!Array.isArray(q.options) || q.options.length < 2) throw new TypeError('Question options are missing');
  const options = q.options.map(o => {
    if (typeof o.id !== 'string' || !o.id.trim()) throw new TypeError('option.id is required');
    if (!feedback.has(o.feedback)) throw new TypeError('Unknown authoritative option feedback');
    return { id: o.id, content: textContent(o.content, 'option.content'), feedback: o.feedback };
  });
  const available = new Set(options.map(o => o.id));
  if (available.size !== options.length) throw new TypeError('Option ids must be unique');
  const selected = optionIds(q.selectedOptionIds, available, 'selectedOptionIds');
  if (definition.selectionMode === 'SINGLE' && selected.length > 1)
    throw new TypeError('SINGLE_CHOICE cannot display multiple selected options');
  return { prompt: textContent(q.prompt, 'prompt'), options, selectedOptionIds: selected,
    selectionMode: definition.selectionMode, available };
}
