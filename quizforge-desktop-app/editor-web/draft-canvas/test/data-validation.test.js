import test from 'node:test';
import assert from 'node:assert/strict';
import {bundle} from './fixtures/html-choice.js';
import {compileQuestionExtension} from '../src/extensions/sdk.js';
import {compileDataValidation,checkedRules,DataValidationError} from '../src/extensions/data-validation.js';
import {prepareWorkbenchState,submitWorkbenchAnswer} from '../src/extensions/workbench-state.js';

const source=()=>bundle(),asset=value=>value.assets[0],type=value=>value.manifest.types[0];
const invoke=(compiled,operation,input)=>JSON.parse(compiled.rules.invoke('SINGLE_CHOICE',operation,JSON.stringify(input)));
test('schema mismatches reject templates, questions and answers despite permissive extension validation',()=>{
  const value=source();asset(value).questionSchemaSource=JSON.stringify({type:'object',properties:{prompt:{type:'object',properties:{text:{type:'string'}}}}});
  const compiled=compileQuestionExtension(value),q=structuredClone(asset(value).defaultQuestion);
  q.prompt.text=42;assert.throws(()=>invoke(compiled,'validate',{question:q}),error=>error.code==='DATA_VALIDATION_FAILED'&&error.issues[0].path==='/question/prompt/text');
  for(const answer of [{selectedOptionIds:['opt_a','opt_a']},{selectedOptionIds:[42]},{selectedOptionIds:[],unexpected:true}])
    assert.throws(()=>invoke(compiled,'validateAnswer',{question:asset(value).defaultQuestion,answer}),DataValidationError);
  assert.deepEqual(invoke(compiled,'validateAnswer',{question:asset(value).defaultQuestion,answer:{}}),{errors:[],empty:true});
  assert.throws(()=>invoke(compiled,'grade',{question:asset(value).defaultQuestion,answer:{},maxScore:2}),/write an answer/);
  asset(value).defaultQuestion.prompt.text=42;assert.throws(()=>compileQuestionExtension(value),DataValidationError);
});
test('local reference schemas work without changing input; unsafe or unsupported schemas fail closed',()=>{
  const value=source();asset(value).answerSchemaSource=JSON.stringify({type:'object',required:['value'],definitions:{letter:{enum:['A','B']}},properties:{value:{$ref:'#/definitions/letter'}},additionalProperties:false});
  const validator=compileDataValidation(type(value),asset(value)),answer={value:'A'};validator.answer(answer);assert.deepEqual(answer,{value:'A'});
  assert.throws(()=>validator.answer({value:'C'}),DataValidationError);
  for(const schema of [{type:'typo'},{$ref:'https://example.invalid/schema'},{$ref:'file:///secret'},{$ref:'#/missing'},{definitions:{self:{$ref:'#/definitions/self'}}},{$schema:'https://json-schema.org/draft/2020-12/schema'},{minLenght:1}]){
    asset(value).answerSchemaSource=JSON.stringify(schema);assert.throws(()=>compileDataValidation(type(value),asset(value)),DataValidationError);
  }
  asset(value).answerSchemaSource=JSON.stringify({type:'object',properties:{value:{type:'string',default:'untouched'}}});
  const unchanged={other:1};compileDataValidation(type(value),asset(value)).answer(unchanged);assert.deepEqual(unchanged,{other:1});
});
test('malicious rule outputs cannot grade beyond the frozen maximum or forge validation and target shapes',async()=>{
  const value=source(),validation=compileDataValidation(type(value),asset(value)),q=asset(value).defaultQuestion;
  const input={question:q,answer:{selectedOptionIds:[q.payload.options[0].id]},maxScore:2};
  for(const [operation,output] of [['validate',{errors:[123]}],['validateAnswer',{errors:[],empty:'false'}],['grade',{status:'CORRECT',score:999,maxScore:2}],['grade',{status:'CORRECT',score:1}],['grade',{status:'INCORRECT',score:2}],['grade',{status:'UNSCORED',score:0}],['grade',{status:'CORRECT',score:2,maxScore:'2'}],['snapshot',{maxScore:-1}],['targets',{targets:[{id:'one',number:1},{id:'two',number:1}]}]]){
    const rules=checkedRules({invoke:()=>Promise.resolve(JSON.stringify(output))},new Map([[type(value).id,validation]]));
    await assert.rejects(async()=>rules.invoke(type(value).id,operation,JSON.stringify(input)),DataValidationError);
  }
});
test('invalid grading leaves workbench result and history unchanged and valid grading still succeeds',async()=>{
  const value=source();
  const normal=compileQuestionExtension(source()),state=await prepareWorkbenchState(normal);
  state.answer={selectedOptionIds:[state.question.payload.options[0].id]};
  const validation=compileDataValidation(type(value),asset(value));
  const bad={...normal,rules:checkedRules({invoke:(type,operation,input)=>operation==='grade'?JSON.stringify({status:'CORRECT',score:999,maxScore:2}):normal.rules.invoke(type,operation,input)},new Map([[type(value).id,validation]]))};
  const before=structuredClone(state);await assert.rejects(submitWorkbenchAnswer(bad,state),DataValidationError);assert.deepEqual(state,before);
  await submitWorkbenchAnswer(normal,state);assert.equal(state.history.length,1);assert.equal(state.result.status,'CORRECT');
});
