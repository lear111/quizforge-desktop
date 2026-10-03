import { readChoiceQuestion } from '../choice/contract.js';
import { mountChoiceRenderer } from '../choice/renderer.js';
export const multipleChoiceRenderer = Object.freeze({
  id: 'builtin.multiple-choice.v1', questionType: 'MULTIPLE_CHOICE', selectionMode: 'MULTIPLE', label: '多选题',
  parse(question) { return readChoiceQuestion(question, this); },
  mount(form, question, capabilities) { return mountChoiceRenderer(form, question, { ...capabilities, definition: this }); }
});
