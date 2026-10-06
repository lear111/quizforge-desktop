import test from 'node:test';
import assert from 'node:assert/strict';
import { readPractice } from '../src/practice/contract.js';
import { practiceChannel } from '../src/bridge/practice.js';
import { card, installChoices } from './fixtures/html-choice.js';
installChoices();
test('HTML contract detaches snapshot and removes host internals',()=>{
  const source=card();source.internalRepository='private';const parsed=readPractice(source);
  assert.equal(parsed.internalRepository,undefined);parsed.question.presentation.question.prompt.text='changed';
  assert.notEqual(source.question.prompt.text,'changed');assert.equal(parsed.question.selectionMode,'EXTENSION');
});
test('unsubmitted HTML presentation cannot expose reference or result',()=>{
  const source=card();source.question.presentation.reference={answerSpec:{}};
  assert.throws(()=>readPractice(source),/exposes its answer/);source.question.presentation.reference=null;
  source.question.result={};assert.throws(()=>readPractice(source),/must not expose/);
});
test('contract rejects unknown option identities and unsupported type/version',()=>{
  for(const mutate of [v=>v.schemaVersion='2.0',v=>v.question.type='ESSAY',v=>v.question.presentation.answer.selectedOptionIds=['missing']]){
    const value=card();mutate(value);assert.throws(()=>readPractice(value));
  }
});
test('retry preserves authoritative state and selected answer',()=>{
  const value=card('SINGLE_CHOICE',['opt_template_a']);value.question.state='RETRYING';
  assert.equal(readPractice(value).question.state,'RETRYING');assert.deepEqual(readPractice(value).question.selectedOptionIds,['opt_template_a']);
});
test('bridge sends semantic envelope and propagates disconnected host failures',()=>{
  let received,ready=0;const channel=practiceChannel(()=>({onEvent:json=>received=JSON.parse(json),ready:()=>ready++}));
  const event={type:'ANSWER_CHANGED',sessionId:'session',sessionQuestionId:'sq',operationSeq:1,answer:{selectedOptionIds:['opt_template_b']}};
  channel.ready();channel.send(event);assert.equal(ready,1);assert.deepEqual(received,event);
  assert.throws(()=>practiceChannel(()=>null).send(event),/尚未就绪/);
});
