import test from 'node:test';
import assert from 'node:assert/strict';
import {bundle, compiled} from './fixtures/html-choice.js';

const extension = compiled('true-false');
const invoke = (operation, input) => JSON.parse(extension.rules.invoke('TRUE_FALSE', operation, JSON.stringify(input)));
const create = () => invoke('createDraft', {ids: {question: 'q_judgment', opts: ['opt_yes', 'opt_no']}});

test('judgment full template allocates IDs and supports both correct-answer values', () => {
  const question = create();
  assert.deepEqual(invoke('validate', {question}).errors, []);
  assert.equal(question.id, 'q_judgment');
  assert.deepEqual(question.payload.options.map(o => o.content.text), ['正确', '错误']);
  for (const id of ['opt_yes', 'opt_no']) {
    question.answerSpec.correctOptionIds = [id];
    const right = invoke('grade', {question, answer: {selectedOptionIds: [id]}, maxScore: 2.5});
    assert.equal(right.status, 'CORRECT'); assert.equal(right.score, 2.5);
    const wrong = invoke('grade', {question, answer: {selectedOptionIds: [id === 'opt_yes' ? 'opt_no' : 'opt_yes']}, maxScore: 2.5});
    assert.equal(wrong.status, 'INCORRECT'); assert.equal(wrong.score, 0);
  }
});

test('judgment rejects extra choices, altered fixed labels and invalid selections', () => {
  const question = create();
  assert.equal(invoke('validateAnswer', {question, answer: {selectedOptionIds: []}}).empty, true);
  assert.ok(invoke('validateAnswer', {question, answer: {selectedOptionIds: ['opt_unknown']}}).errors.length);
  assert.equal(invoke('validateAnswer', {question, answer: {}}).empty, true);
  for (const answer of [{selectedOptionIds: null}, {selectedOptionIds: ['opt_yes', 'opt_no']}]) {
    assert.throws(() => invoke('validateAnswer', {question, answer}), /selectedOptionIds/);
  }
  question.payload.options[0].content.text = '自定义选项';
  assert.ok(invoke('validate', {question}).errors.length);
  question.payload.options.push({id:'opt_extra', content:{kind:'TEXT', text:'错误'}});
  assert.throws(() => invoke('validate', {question}), /options/);
});

test('judgment duplication remaps answer references, snapshot and outline remain one question', () => {
  const question = create(); question.answerSpec.correctOptionIds = ['opt_no'];
  const copy = invoke('duplicate', {question, ids: {question: 'q_copy', opts: ['opt_copy_yes', 'opt_copy_no']}});
  assert.deepEqual(copy.answerSpec.correctOptionIds, ['opt_copy_no']);
  assert.deepEqual(invoke('validate', {question: copy}).errors, []);
  assert.equal(invoke('snapshot', {question: copy}).maxScore, 1);
  assert.deepEqual(invoke('targets', {question: copy}).targets, [{id:'q_copy', number:1, label:'', locked:false, gradable:true}]);
});

test('judgment compiles independent editor/practice pages and uses existing choice answer contract', () => {
  const source = bundle('true-false');
  assert.equal(extension.editors.length, 1); assert.equal(extension.renderers.length, 1);
  assert.equal(source.manifest.types[0].id, 'TRUE_FALSE');
  assert.equal(source.assets[0].defaultQuestion.payload.kind, 'CHOICE');
  assert.equal(source.assets[0].defaultQuestion.analysis.kind, 'TEXT');
});
