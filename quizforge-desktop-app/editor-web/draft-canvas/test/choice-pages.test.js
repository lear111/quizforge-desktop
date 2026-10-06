import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {bundle} from './fixtures/html-choice.js';

const clone=value=>JSON.parse(JSON.stringify(value));
const ok=data=>({ok:true,data:clone(data)});
const deferred=()=>{let resolve;const promise=new Promise(r=>resolve=r);return {promise,resolve};};
const tick=async()=>{for(let i=0;i<12;i++)await Promise.resolve();};

class Node {
  constructor(tag='div'){this.tag=tag;this.dataset={};this.children=[];this.listeners=new Map();this.hidden=false;this.disabled=false;this.value='';this.checked=false;
    this.classes=new Set();this.classList={toggle:(name,on)=>on?this.classes.add(name):this.classes.delete(name)};}
  setAttribute(){}
  append(...nodes){for(const node of nodes){node.parent=this;this.children.push(node);}}
  replaceChildren(...nodes){this.children=[];this.append(...nodes);}
  matches(selector){return selector.split(',').some(s=>{s=s.trim();return s.startsWith('[data-')?Object.hasOwn(this.dataset,s.slice(6,-1).replace(/-([a-z])/g,(_,c)=>c.toUpperCase())):this.tag===s;});}
  querySelectorAll(selector){return this.children.flatMap(n=>[...(n.matches(selector)?[n]:[]),...n.querySelectorAll(selector)]);}
  querySelector(selector){return this.querySelectorAll(selector)[0]||null;}
  closest(selector){return this.matches(selector)?this:this.parent?.closest(selector)||null;}
  focus(){this.focused=true;}
  addEventListener(name,fn){this.listeners.set(name,fn);}
  removeEventListener(name,fn){if(this.listeners.get(name)===fn)this.listeners.delete(name);}
  fire(name){return this.listeners.get(name)?.({target:this,key:'',preventDefault(){}});}
}

async function page(slug,mode='editor'){
  const source=bundle(slug),asset=source.assets[0],root=new Node(),nodes=new Map(),writes=[],notifications=[],subscriptions=[];
  let question=clone(asset.defaultQuestion),answer={},result=null,delayedRead;
  const caps={editQuestion:true,editAnswer:true,submit:true,retry:true,viewSources:true,manageSources:true};
  const context={mode:mode==='editor'?'EDITOR':'PRACTICE',capabilities:caps,permissions:{granted:source.manifest.types[0].permissions}};
  const $=selector=>{
    if(!nodes.has(selector)){
      const name=selector.slice(6,-1).replace(/-([a-z])/g,(_,c)=>c.toUpperCase());
      const node=new Node(['data-prompt','data-source-link','data-score'].includes(selector.slice(1,-1))?'textarea':'div');node.dataset[name]='';
      if(name.startsWith('answer')&&['answerTrue','answerFalse'].includes(name)||name.startsWith('correct')){
        node.tag='input';const label=new Node('label');label.append(node);const feedback=new Node();feedback.className='qf-option-feedback';label.append(feedback);
        label.querySelector=s=>s==='.qf-option-feedback'?feedback:Node.prototype.querySelector.call(label,s);root.append(label);
      }else root.append(node);
      nodes.set(selector,node);
    }
    return nodes.get(selector);
  };
  const write=(kind,value)=>{const d=deferred();writes.push({kind,value:clone(value),finish(success=true){
    if(success){if(kind==='editor')question={...question,...clone(value)};else answer=clone(value);d.resolve(ok(kind==='editor'?question:null));}
    else d.resolve({ok:false,error:{code:'STORAGE_FAILED',message:'save failed'}});
  }});return d.promise;};
  const QF={dom:{$,root,on:(node,name,fn)=>node.addEventListener(name,fn)},ids:{create:()=>`opt_new_${writes.length}`},
    layout:{configure(){}},ui:{configure(){},notify:message=>notifications.push(message)},
    host:{getContext:async()=>ok(context),subscribe:fn=>subscriptions.push(fn)},
    bank:{getState:async()=>ok({index:0,count:1,editable:true,types:[],sources:[]})},
    navigation:{getState:async()=>ok({sources:[]})},sources:{},
    editor:{getData:async()=>ok(question),update:patch=>write('editor',patch)},
    answer:{get:async()=>{if(delayedRead){const d=delayedRead;delayedRead=null;return d.promise;}return ok(answer);},update:value=>write('answer',value)},
    practice:{getQuestion:async()=>ok(question),getResult:async()=>ok(result),getState:async()=>ok({index:0,total:1,maxScore:1,state:result?'SUBMITTED':Object.keys(answer).length?'DRAFT':'UNANSWERED'})},
    content:{render(){},mountEditor(node){node.append(new Node('textarea'));}}};
  const sandbox=vm.createContext({QF,document:{createElement:tag=>new Node(tag)},Option:class extends Node{constructor(text,value){super('option');this.textContent=text;this.value=value;}}});
  await vm.runInContext(`(async()=>{${mode==='editor'?asset.editorSource:asset.rendererSource}\n})()`,sandbox);
  return {$,root,writes,notifications,caps,context,subscriptions,get question(){return question;},get answer(){return answer;},
    delayRead(){const d=deferred();delayedRead=d;return d;},setResult(value){result=value;},refresh:()=>subscriptions.at(-1)()};
}

for(const slug of ['single-choice','multiple-choice']){
  test(`${slug}: rapid edits to different options keep both texts and selecting an answer preserves input nodes`,async()=>{
    const p=await page(slug),rows=p.$('[data-options]').children;
    const first=rows[0].querySelector('textarea'),second=rows[1].querySelector('textarea');
    first.value='第一项';const a=first.fire('input');second.value='第二项';const b=second.fire('input');
    assert.equal(p.writes[1].value.payload.options[0].content.text,'第一项');
    p.writes[0].finish();await a;p.writes[1].finish();await b;
    assert.deepEqual(p.question.payload.options.slice(0,2).map(o=>o.content.text),['第一项','第二项']);
    const correct=rows[1].querySelector('input');correct.checked=true;const c=correct.fire('change');
    p.writes[2].finish();await c;
    assert.equal(p.$('[data-options]').children[0].querySelector('textarea'),first);
    assert.equal(p.$('[data-options]').children[1].querySelector('textarea'),second);
  });
}

for(const slug of ['single-choice','multiple-choice','true-false']){
  test(`${slug}: rejected edits notify without crashing and invalid scores never write zero`,async()=>{
    const p=await page(slug),input=p.$('[data-prompt]'),initialScore=p.question.scoreSpec.defaultMaxScore;input.value='unsaved';const operation=input.fire('input');
    p.writes[0].finish(false);await operation;assert.deepEqual(p.notifications,['save failed']);
    const score=p.$('[data-score]');for(const value of ['','0','-1','Infinity']){score.value=value;await score.fire('input');}
    assert.equal(p.writes.length,1);assert.equal(p.question.scoreSpec.defaultMaxScore,initialScore);
    input.value='recovered';const retry=input.fire('input');p.writes[1].finish();await retry;assert.equal(p.question.prompt.text,'recovered');
    p.caps.editQuestion=false;await p.refresh();assert.equal(input.disabled,true);
    p.context.permissions.granted=[];await p.refresh();assert.equal(p.$('[data-bank-save]').disabled,true);assert.equal(p.$('[data-bank-delete]').disabled,true);
  });
  test(`${slug}: an older refresh cannot replace a click; retry and revoked submit clear confirmation`,async()=>{
    const p=await page(slug,'practice'),inputs=slug==='true-false'?[p.$('[data-answer-true]'),p.$('[data-answer-false]')]:p.$('[data-options]').querySelectorAll('input');
    const pendingRead=p.delayRead(),old=p.refresh();await tick();
    inputs[1].checked=true;const change=inputs[1].fire('change');
    pendingRead.resolve(ok({}));await old;assert.equal(inputs[1].checked,true);
    p.writes[0].finish();await change;assert.equal(inputs[1].checked,true);assert.equal(p.$('[data-submit-answer]').disabled,false);
    await p.$('[data-submit-answer]').fire('click');await tick();assert.equal(p.$('[data-answer-confirm]').hidden,false);
    p.caps.submit=false;await p.refresh();assert.equal(p.$('[data-answer-confirm]').hidden,true);assert.equal(p.$('[data-confirm-answer]').disabled,true);
    p.setResult({status:'CORRECT',score:1,maxScore:1});await p.refresh();assert.equal(inputs[1].checked,true);assert.equal(p.$('[data-result]').hidden,false);
    p.setResult(null);await p.refresh();assert.equal(p.$('[data-result]').hidden,true);
  });
  test(`${slug}: history and preview keep saved selections visible while disabling edits and submission`,async()=>{
    const p=await page(slug,'practice'),inputs=slug==='true-false'?[p.$('[data-answer-true]'),p.$('[data-answer-false]')]:p.$('[data-options]').querySelectorAll('input');
    inputs[0].checked=true;const change=inputs[0].fire('change');p.writes[0].finish();await change;
    p.caps.editAnswer=false;p.caps.submit=false;p.caps.retry=false;
    for(const mode of ['HISTORY','PREVIEW']){
      p.context.mode=mode;await p.refresh();assert.equal(inputs[0].checked,true);assert.equal(inputs[0].disabled,true);
      assert.equal(p.$('[data-submit-answer]').hidden,true);assert.equal(p.$('[data-retry-answer]').hidden,true);
    }
  });
  test(`${slug}: temporary interaction locks retain actions; revoked permissions hide them`,async()=>{
    const p=await page(slug,'practice'),submit=p.$('[data-submit-answer]'),retry=p.$('[data-retry-answer]');
    assert.equal(submit.hidden,false);
    p.caps.submit=false;p.caps.editAnswer=false;await p.refresh();
    assert.equal(submit.hidden,false);assert.equal(submit.disabled,true);
    p.caps.submit=true;await p.refresh();assert.equal(submit.hidden,false);
    p.context.permissions.granted=p.context.permissions.granted.filter(name=>name!=='practice.submit');
    await p.refresh();assert.equal(submit.hidden,true);
    p.setResult({status:'CORRECT',score:1,maxScore:1});p.caps.retry=false;await p.refresh();
    assert.equal(submit.hidden,true);assert.equal(retry.hidden,false);assert.equal(retry.disabled,true);
    p.context.permissions.granted=p.context.permissions.granted.filter(name=>name!=='practice.retry');
    await p.refresh();assert.equal(retry.hidden,true);
  });
}

test('multiple-choice: an intermediate write notification preserves a second unsaved selection',async()=>{
  const p=await page('multiple-choice','practice'),inputs=p.$('[data-options]').querySelectorAll('input');
  inputs[0].checked=true;const first=inputs[0].fire('change');
  inputs[1].checked=true;const second=inputs[1].fire('change');
  p.writes[0].finish();await first;await p.refresh();assert.equal(inputs[1].checked,true);
  p.writes[1].finish();await second;assert.equal(p.answer.selectedOptionIds.length,2);
});
