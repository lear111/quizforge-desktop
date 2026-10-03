import test from 'node:test';
import assert from 'node:assert/strict';
import { readPractice } from '../src/practice/contract.js';
import { QuestionRendererRegistry } from '../src/shared/renderer/registry.js';
const text = text => ({kind:'TEXT',text});
function vm(selected=[]) {
 const items=[1,2].map(n=>({id:`item-${n}`,number:n,prompt:text(`Question ${n}`),options:['a','b','c','d'].map(id=>({id:`${n}-${id}`,content:text(id),feedback:'NONE'}))}));
 return {schemaVersion:'1.0',session:{sessionId:'s',bankAssetId:'b',bankContentId:'c'},question:{type:'READING',questionId:'q',sessionQuestionId:'sq',index:0,total:1,prompt:text('Passage'),options:items.flatMap(i=>i.options),presentation:{items},selectedOptionIds:selected,state:selected.length?'DRAFT':'UNANSWERED',maxScore:4,result:null}};
}
test('Reading registry and structured parent/children preserve stable IDs',()=>{const v=readPractice(vm());assert.equal(QuestionRendererRegistry.require('READING').selectionMode,'COMPOSITE_SINGLE');assert.deepEqual(v.question.presentation.items.map(i=>i.id),['item-1','item-2']);assert.equal(v.question.sessionQuestionId,'sq');});
test('Reading accepts independent partial answers but one per child',()=>{assert.deepEqual(readPractice(vm(['1-a','2-c'])).question.selectedOptionIds,['1-a','2-c']);assert.throws(()=>readPractice(vm(['1-a','1-b'])),/multiple selected/);assert.throws(()=>readPractice(vm(['unknown'])),/known/);});
test('Reading never strips unsupported child or passage content',()=>{const v=vm();v.question.presentation.items[0].prompt={kind:'RICH'};assert.throws(()=>readPractice(v),/Unsupported content/);});
test('Reading child IDs are unique independently of displayed numbers',()=>{const v=vm();v.question.presentation.items[1].id='item-1';assert.throws(()=>readPractice(v),/unique/);});
test('Reading history displays the supplied partial Core score and answers',()=>{const v=vm(['1-a']);v.question.state='SUBMITTED';v.question.result={status:'INCORRECT',score:2,maxScore:4,attemptId:'a1',attemptNo:1,attemptMode:'INITIAL',correctOptionIds:['1-a','2-d'],analysis:text('Core analysis')};assert.equal(readPractice(v).question.result.score,2);});
