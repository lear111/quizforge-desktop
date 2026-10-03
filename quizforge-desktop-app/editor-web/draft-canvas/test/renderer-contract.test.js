import test from 'node:test';
import assert from 'node:assert/strict';
import { QuestionRendererRegistry } from '../src/shared/renderer/registry.js';
import { RendererMode, requireRendererMode } from '../src/shared/renderer/contract.js';
import { readPractice } from '../src/practice/contract.js';
import { readHistoryReplay } from '../src/history-replay.js';
import { readFileSync } from 'node:fs';
const text = text => ({ kind: 'TEXT', text });
function view(type = 'MULTIPLE_CHOICE', selected = []) {
  return { schemaVersion: '1.0', session: { sessionId:'s', bankAssetId:'b', bankContentId:'c' },
    question: { type, sessionQuestionId:'sq', questionId:'q', index:0, total:2, prompt:text('Actual question'),
      options:['a','b','c','d'].map(id => ({id,content:text(id),feedback:'NONE'})),
      selectedOptionIds:selected, state:selected.length ? 'DRAFT' : 'UNANSWERED', maxScore:2, result:null } };
}
for (const [type, mode] of [['SINGLE_CHOICE','SINGLE'],['MULTIPLE_CHOICE','MULTIPLE']]) {
  test(`${type} resolves a static definition with common renderer operations`, () => {
    const definition = QuestionRendererRegistry.require(type);
    assert.equal(definition.questionType,type);assert.equal(definition.selectionMode,mode);
    assert.equal(typeof definition.mount,'function'); assert.equal(typeof definition.parse,'function');
    const vm = readPractice(view(type));assert.equal(vm.question.type,type);assert.equal(vm.question.selectionMode,mode);
  });
}
test('registry never silently falls back for unknown types', () => {
  assert.deepEqual(QuestionRendererRegistry.types,['SINGLE_CHOICE','MULTIPLE_CHOICE']);
  assert.throws(() => readPractice(view('READING')),/Unsupported question type/);
});
test('capabilities explicitly distinguish ACTIVE and READ_ONLY_HISTORY', () => {
  assert.equal(requireRendererMode(RendererMode.ACTIVE),'ACTIVE');
  assert.equal(requireRendererMode(RendererMode.READ_ONLY_HISTORY),'READ_ONLY_HISTORY');
  assert.throws(() => requireRendererMode('EDITABLE_HISTORY'),/capability/);
});
test('multiple semantic selections allow empty, A, A+B and deselection', () => {
  for (const selected of [[],['a'],['a','b'],['b']]) assert.deepEqual(readPractice(view('MULTIPLE_CHOICE',selected)).question.selectedOptionIds,selected);
  assert.throws(() => readPractice(view('MULTIPLE_CHOICE',['a','a'])),/unique/);
  assert.throws(() => readPractice(view('MULTIPLE_CHOICE',['unknown'])),/known/);
});
test('selectionMode mismatches reject while old single v1 documents remain compatible', () => {
  const bad=view();bad.question.selectionMode='SINGLE';assert.throws(()=>readPractice(bad),/does not match/);
  assert.equal(readPractice(view('SINGLE_CHOICE',['a'])).question.selectionMode,'SINGLE');
  assert.throws(()=>readPractice(view('SINGLE_CHOICE',['a','c'])),/multiple selected/);
});
test('unsupported prompt, option and analysis content is explicit', () => {
  for (const field of ['prompt','option','analysis']) {
    const vm=view(); if(field==='prompt') vm.question.prompt={kind:'RICH',document:{}}; else vm.question.options[0].content={kind:'IMAGE',id:'image'};
    if(field==='analysis') {
      vm.question.options[0].content=text('a');vm.question.state='SUBMITTED';
      vm.question.result={status:'CORRECT',score:2,maxScore:2,attemptId:'attempt',attemptNo:1,attemptMode:'INITIAL',correctOptionIds:['a'],analysis:{kind:'DOCUMENT',resourceId:'resource'}};
    }
    assert.throws(()=>readPractice(vm),/Unsupported content/);
  }
});
test('multiple history keeps submitted selections, Core score and frozen geometry detached', () => {
  const vm=view('MULTIPLE_CHOICE',['a','c']);vm.question.state='SUBMITTED';
  vm.question.result={status:'INCORRECT',score:0.75,maxScore:2,attemptId:'attempt',attemptNo:2,attemptMode:'RETRY',correctOptionIds:['b','d'],analysis:text('From Core')};
  const doc=JSON.parse(readFileSync(new URL('./fixtures/document-v1.json',import.meta.url),'utf8'));
  const replay=readHistoryReplay(vm,doc);
  assert.deepEqual(replay.viewModel.question.selectedOptionIds,['a','c']);assert.equal(replay.viewModel.question.result.score,.75);
  assert.deepEqual(replay.document,doc);replay.document.questionCard.width=100;assert.notEqual(doc.questionCard.width,100);
});
test('multiple unsubmitted state cannot leak result feedback or correctness', () => {
  const vm=view();vm.question.options[0].feedback='CORRECT';assert.throws(()=>readPractice(vm),/must not expose/);
});
