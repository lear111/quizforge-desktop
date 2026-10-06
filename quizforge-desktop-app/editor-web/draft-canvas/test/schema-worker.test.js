import test from 'node:test';
import assert from 'node:assert/strict';
import {Worker as ThreadWorker} from 'node:worker_threads';
import {build} from 'esbuild';
import {createWorkerValidation} from '../src/extensions/schema-worker-client.js';
import {compileDataValidation,DataValidationError} from '../src/extensions/data-validation.js';
import {bundle} from './fixtures/html-choice.js';

const generated=await build({entryPoints:[new URL('../src/extensions/schema-worker.js',import.meta.url).pathname.replace(/^\/(\w:)/,'$1')],bundle:true,write:false,format:'iife',target:'es2020'});
const source=generated.outputFiles[0].text;
function factory(code=source) {
  return () => {
    const thread=new ThreadWorker(`const {parentPort}=require('node:worker_threads');global.self=globalThis;self.postMessage=m=>parentPort.postMessage(m);${code}\nparentPort.on('message',data=>self.onmessage({data}));`,{eval:true});
    const worker={postMessage:m=>thread.postMessage(m),terminate:()=>thread.terminate()};
    thread.on('message',data=>worker.onmessage?.({data}));thread.on('error',error=>worker.onerror?.(error));return worker;
  };
}
const input=()=>{const b=bundle();return {type:b.manifest.types[0],asset:b.assets[0]};};

test('real worker validates both directions without executing schemas on the main thread',async()=>{
  const {type,asset}=input(),validator=createWorkerValidation(type,asset,{workerFactory:factory()});
  try {
    await validator.question(asset.defaultQuestion);
    await validator.answer({selectedOptionIds:[]});
    await assert.rejects(validator.answer({selectedOptionIds:[42]}),DataValidationError);
    await assert.rejects(validator.output('grade',{status:'CORRECT',score:999},{maxScore:2}),DataValidationError);
  }finally{validator.destroy();}
});
test('review regex and patternProperties reject promptly in the actual worker',async()=>{
  for(const key of ['pattern','patternProperties']) {
    const {type,asset}=input();
    asset.answerSchemaSource=JSON.stringify(key==='pattern'?{type:'object',properties:{value:{type:'string',pattern:'^(a+)+$'}}}:{type:'object',patternProperties:{'^(a+)+$':{type:'string'}}});
    const validator=createWorkerValidation(type,asset,{workerFactory:factory()});
    try{await assert.rejects(validator.answer({value:'a'.repeat(40)+'!'}),/groups and alternation/);}finally{validator.destroy();}
  }
});
test('a stuck validation is terminated while the main event loop remains responsive',async()=>{
  const {type,asset}=input();let ticks=0;
  const validator=createWorkerValidation(type,asset,{workerFactory:factory(`self.onmessage=({data:m})=>{if(m.operation==='compile')self.postMessage({id:m.id,value:true});else while(true){}};`),timeoutMs:120});
  const heartbeat=setInterval(()=>ticks++,10);
  try {
    await validator.ready;
    await assert.rejects(validator.answer({value:'anything'}),/time budget/);
    assert.ok(ticks>=3,'main thread must keep responding while validation loops');
    await assert.rejects(validator.answer({}),/closed/);
  }finally{clearInterval(heartbeat);validator.destroy();}
});
test('shutdown rejects pending requests and absent Worker never enables synchronous fallback',async()=>{
  const {type,asset}=input();
  const closed=createWorkerValidation(type,asset,{workerFactory:factory(`self.onmessage=()=>{};`)});
  const result=assert.rejects(closed.question(asset.defaultQuestion),/closed/);closed.destroy();await result;
  const absent=createWorkerValidation(type,asset,{workerFactory:()=>{throw new Error('Worker unavailable');}});
  await assert.rejects(absent.answer({}),/Worker unavailable/);
});
test('retirement lets existing pages finish but releases the worker after the last page',async()=>{
  const {type,asset}=input(),validator=createWorkerValidation(type,asset,{workerFactory:factory()});
  try {await validator.ready;validator.retain();validator.retire();await validator.answer({});validator.release();await assert.rejects(validator.answer({}),/closed/);}
  finally{validator.destroy();}
});
test('browser and native profiles bound regexes and repeated-reference DAGs',()=>{
  const {type,asset}=input();
  for(const pattern of ['a+b','^(a|b)$','^a+a+$','^a{1025}$','^[a&&b]+$','^[[a]]+$']) {
    asset.answerSchemaSource=JSON.stringify({type:'object',properties:{value:{pattern}}});
    assert.throws(()=>compileDataValidation(type,asset),DataValidationError);
  }
  asset.answerSchemaSource=JSON.stringify({type:'object',properties:{value:{pattern:'^[A-Z]+$'}}});
  const safe=compileDataValidation(type,asset);safe.answer({value:'ABC'});assert.throws(()=>safe.answer({value:'ABC!'}),DataValidationError);
  const definitions={leaf:{type:'string'}};
  for(let i=0;i<16;i++)definitions['n'+i]={allOf:[{$ref:'#/definitions/'+(i?'n'+(i-1):'leaf')},{$ref:'#/definitions/'+(i?'n'+(i-1):'leaf')}]};
  asset.answerSchemaSource=JSON.stringify({definitions,properties:{value:{$ref:'#/definitions/n15'}}});
  assert.throws(()=>compileDataValidation(type,asset),/complexity budget/);
});
