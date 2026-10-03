import test from 'node:test';
import assert from 'node:assert/strict';
import { readPractice } from '../src/practice/contract.js';
import { practiceChannel } from '../src/bridge/practice.js';

const text = value => ({ kind: 'TEXT', text: value });
function initial() {
  return { schemaVersion: '1.0', session: { sessionId: 's', bankAssetId: 'b', bankContentId: 'c' },
    question: { sessionQuestionId: 'sq', questionId: 'q', type: 'SINGLE_CHOICE', index: 0, total: 4,
      prompt: text('Question'), options: ['a', 'b', 'c'].map(id => ({ id, content: text(id), feedback: 'NONE' })),
      selectedOptionIds: [], state: 'UNANSWERED', maxScore: 2, result: null } };
}

test('explicit contract preserves actual option count and detaches its snapshot', () => {
  const source = initial(); source.internalRepository = 'must not cross contract';
  const view = readPractice(JSON.stringify(source));
  assert.equal(view.question.options.length, 3);
  assert.equal(view.internalRepository, undefined);
  view.question.options[0].content.text = 'changed';
  assert.equal(source.question.options[0].content.text, 'a');
});

test('unsubmitted contract rejects result and feedback leaks', () => {
  const source = initial(); source.question.options[0].feedback = 'CORRECT';
  assert.throws(() => readPractice(source), /must not expose/);
  source.question.options[0].feedback = 'NONE'; source.question.result = {};
  assert.throws(() => readPractice(source), /must not expose/);
});

test('contract displays the supplied score without independently judging answers', () => {
  const source = initial(); source.question.state = 'SUBMITTED'; source.question.selectedOptionIds = ['b'];
  source.question.result = { status: 'INCORRECT', score: 0.75, maxScore: 2, attemptId: 'attempt',
    attemptNo: 2, attemptMode: 'RETRY', correctOptionIds: ['a'], analysis: text('Explanation') };
  source.question.options[0].feedback = 'CORRECT'; source.question.options[1].feedback = 'INCORRECT';
  const view = readPractice(source);
  assert.equal(view.question.result.score, 0.75);
  assert.equal(view.question.result.attemptMode, 'RETRY');
});

test('unknown types, versions, duplicate IDs and multiple single-choice answers are rejected', () => {
  for (const mutate of [v => v.schemaVersion = '2.0', v => v.question.type = 'MULTIPLE_CHOICE',
    v => v.question.options[1].id = 'a', v => v.question.selectedOptionIds = ['a', 'b'],
    v => v.question.selectedOptionIds = ['unknown']]) {
    const source = initial(); mutate(source); assert.throws(() => readPractice(source));
  }
});

test('retry snapshot may show either empty or chosen answers while retaining Core RETRYING', () => {
  const source = initial(); source.question.state = 'RETRYING';
  assert.deepEqual(readPractice(source).question.selectedOptionIds, []);
  source.question.selectedOptionIds = ['c'];
  assert.equal(readPractice(source).question.state, 'RETRYING');
});

test('channel sends only the semantic envelope and propagates disconnected host errors', () => {
  let received, ready = 0;
  const channel = practiceChannel(() => ({ onEvent: json => received = JSON.parse(json), ready: () => ready++ }));
  const event = { type: 'ANSWER_CHANGED', sessionId: 's', sessionQuestionId: 'sq', operationSeq: 1, selectedOptionIds: ['b'] };
  channel.ready(); channel.send(event);
  assert.equal(ready, 1); assert.deepEqual(received, event); assert.equal(channel.diagnostics().sent, 1);
  assert.throws(() => practiceChannel(() => null).send(event), /尚未就绪/);
  assert.throws(() => practiceChannel(() => ({ onEvent() { throw new Error('host failed'); } })).send(event), /host failed/);
});
