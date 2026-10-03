import { readChoiceQuestion } from '../choice/contract.js';
import { mountChoiceRenderer } from '../choice/renderer.js';
export const singleChoiceRenderer = Object.freeze({
  id: 'builtin.single-choice.v1', questionType: 'SINGLE_CHOICE', selectionMode: 'SINGLE', label: '单选题',
  parse(question) { return readChoiceQuestion(question, this); },
  mount(form, question, capabilities) { return mountChoiceRenderer(form, question, { ...capabilities, definition: this }); }
});
