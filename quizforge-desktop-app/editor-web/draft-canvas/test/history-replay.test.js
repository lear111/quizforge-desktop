import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { readHistoryReplay } from '../src/history-replay.js';
import {card as htmlCard, installChoices} from './fixtures/html-choice.js';
installChoices();

const text = value => ({ kind: 'TEXT', text: value });
const document = () => JSON.parse(readFileSync(new URL('./fixtures/document-v1.json', import.meta.url), 'utf8'));
function card(attemptId = 'attempt-A', selected = 'b') {
  const result={status:selected==='a'?'CORRECT':'INCORRECT',score:selected==='a'?1:0,maxScore:1,
    attemptId,attemptNo:attemptId==='attempt-A'?1:2,attemptMode:attemptId==='attempt-A'?'INITIAL':'RETRY',
    correctOptionIds:['opt_template_a'],analysis:text('Frozen analysis')};
  return htmlCard('SINGLE_CHOICE',['opt_template_'+selected],result);
}

test('history binds each submitted Attempt to its own detached card and document', () => {
  const a=document(), b=document(); a.strokes[0].id='ink-A'; b.strokes[0].id='ink-B';
  const first=readHistoryReplay(card(),a), second=readHistoryReplay(card('attempt-B','a'),b);
  assert.equal(first.viewModel.question.result.attemptId,'attempt-A');
  assert.equal(second.viewModel.question.result.attemptId,'attempt-B');
  assert.deepEqual(first.viewModel.question.selectedOptionIds,['opt_template_b']);
  assert.deepEqual(second.viewModel.question.selectedOptionIds,['opt_template_a']);
  assert.equal(first.document.strokes[0].id,'ink-A');assert.equal(second.document.strokes[0].id,'ink-B');
  first.document.strokes[0].id='local';assert.equal(a.strokes[0].id,'ink-A');
});
test('read-only history can display archived unsubmitted answers without inventing a result', () => {
  const value=card();value.question.state='DRAFT';value.question.result=null;
  value.question.presentation.reference=null;
  const replay=readHistoryReplay(value,document());
  assert.equal(replay.viewModel.question.state,'DRAFT');assert.equal(replay.viewModel.question.result,null);
  assert.deepEqual(replay.document,document());
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

test('history preserves frozen whiteboard text and paper and rejects unknown annotation fields',()=>{
  const source=document();source.texts=[{id:'text-1',x:40,y:80,width:260,size:20,color:'#34313b',text:'Frozen notes'}];source.paper={color:'#fff7dc',pattern:'LINES'};
  const replay=readHistoryReplay(card(),source);assert.deepEqual(replay.document,source);
  replay.document.texts[0].text='Changed detached copy';assert.equal(source.texts[0].text,'Frozen notes');
  source.texts[0].futureText=true;assert.throws(()=>readHistoryReplay(card(),source),/Unknown frozen Draft field/);
});
test('historical revision mode and the Core-issued score are display facts', () => {
  const value=card();value.question.result.attemptMode='REVISION';value.question.result.score=0.75;
  const replay=readHistoryReplay(value,document());assert.equal(replay.viewModel.question.result.score,0.75);
  assert.equal(replay.viewModel.question.result.attemptMode,'REVISION');
});
