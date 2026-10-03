import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { readHistoryReplay } from '../src/history-replay.js';

const text = value => ({ kind: 'TEXT', text: value });
const document = () => JSON.parse(readFileSync(new URL('./fixtures/document-v1.json', import.meta.url), 'utf8'));
function card(attemptId = 'attempt-A', selected = 'b') {
  return { schemaVersion: '1.0', session: { sessionId: 'archive', bankAssetId: 'bank', bankContentId: 'frozen-revision' },
    question: { sessionQuestionId: 'sq', questionId: 'q', type: 'SINGLE_CHOICE', index: 0, total: 2,
      prompt: text('Frozen question'), options: ['a', 'b'].map(id => ({ id, content: text(id),
        feedback: id === 'a' ? 'CORRECT' : selected === id ? 'INCORRECT' : 'NONE' })),
      selectedOptionIds: [selected], state: 'SUBMITTED', maxScore: 2,
      result: { status: selected === 'a' ? 'CORRECT' : 'INCORRECT', score: selected === 'a' ? 2 : 0,
        maxScore: 2, attemptId, attemptNo: attemptId === 'attempt-A' ? 1 : 2,
        attemptMode: attemptId === 'attempt-A' ? 'INITIAL' : 'RETRY', correctOptionIds: ['a'], analysis: text('Frozen analysis') } } };
}
test('history binds each submitted Attempt to its own detached card and document', () => {
  const a=document(), b=document(); a.strokes[0].id='ink-A'; b.strokes[0].id='ink-B';
  const first=readHistoryReplay(card(),a), second=readHistoryReplay(card('attempt-B','a'),b);
  assert.equal(first.viewModel.question.result.attemptId,'attempt-A');
  assert.equal(second.viewModel.question.result.attemptId,'attempt-B');
  assert.deepEqual(first.viewModel.question.selectedOptionIds,['b']);
  assert.deepEqual(second.viewModel.question.selectedOptionIds,['a']);
  assert.equal(first.document.strokes[0].id,'ink-A');assert.equal(second.document.strokes[0].id,'ink-B');
  first.document.strokes[0].id='local';assert.equal(a.strokes[0].id,'ink-A');
});
test('history cannot project an active or unsubmitted card', () => {
  const value=card();value.question.state='DRAFT';value.question.result=null;
  value.question.options.forEach(option => option.feedback='NONE');
  assert.throws(() => readHistoryReplay(value,document()),/submitted Attempt/);
});
test('unsupported schema/layout and malformed frozen JSON are rejected without upgrading', () => {
  for (const field of ['schemaVersion','layoutVersion']) {
    const source=document();source[field]='future';assert.throws(() => readHistoryReplay(card(),source));
    assert.equal(source[field],'future');
    delete source[field];assert.throws(() => readHistoryReplay(card(),source));
  }
  assert.throws(() => readHistoryReplay(card(),'{invalid'));
});
test('history retains logical width, card position and world geometry', () => {
  const source=document();source.questionCard={ x:185,y:91,width:600 };
  const replay=readHistoryReplay(JSON.stringify(card()),JSON.stringify(source));
  assert.deepEqual(replay.document,source);
});
test('history does not silently drop unknown frozen fields', () => {
  const source=document();source.futureInk={version:2};
  assert.throws(() => readHistoryReplay(card(),source));assert.deepEqual(source.futureInk,{version:2});
});
test('historical revision mode and the Core-issued score are display facts', () => {
  const value=card();value.question.result.attemptMode='REVISION';value.question.result.score=0.75;
  const replay=readHistoryReplay(value,document());assert.equal(replay.viewModel.question.result.score,0.75);
  assert.equal(replay.viewModel.question.result.attemptMode,'REVISION');
});
