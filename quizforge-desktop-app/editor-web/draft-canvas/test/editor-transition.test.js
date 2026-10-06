import test from 'node:test';
import assert from 'node:assert/strict';
import {stageEditor} from '../src/extensions/editor-transition.js';

function deferred(){let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b;});return {promise,resolve,reject};}
const tick=async()=>{for(let i=0;i<10;i++)await Promise.resolve();};
function environment(){
  const saved={document:globalThis.document,getComputedStyle:globalThis.getComputedStyle,requestAnimationFrame:globalThis.requestAnimationFrame},frames=[];
  class Node{
    style={};dataset={};children=[];inert=false;
    append(node){node.parent=this;this.children.push(node);}
    remove(){this.removed=true;}
    querySelector(){return this.retiring?{}:null;}
    querySelectorAll(){return [];}
  }
  globalThis.document={createElement:()=>new Node()};globalThis.getComputedStyle=()=>({paddingLeft:'8px',paddingRight:'8px',paddingTop:'8px'});
  globalThis.requestAnimationFrame=fn=>frames.push(fn);
  const root=new Node(),old=new Node();root.append(old);
  let destroyed=0,paused=0;
  return {root,old,previous:{body:old,instance:{suspendPresentation(){paused++;},destroy(){destroyed++;return Promise.resolve();}}},frames,
    get destroyed(){return destroyed;},get paused(){return paused;},restore(){Object.assign(globalThis,saved);}};
}
test('editor retains old content until ready, subscription flush and both layout frames complete',async()=>{
  const f=environment(),ready=deferred(),flush=deferred();let readyToWrite,committed=0;
  try{
    const stage=stageEditor(f.root,f.previous,(body,isReady)=>{readyToWrite=isReady;return {ready:ready.promise,flush:()=>flush.promise,destroy(){}};},()=>committed++);
    assert.equal(f.paused,1);assert.equal(f.old.removed,undefined);assert.equal(readyToWrite(),false);
    ready.resolve();await tick();assert.equal(f.frames.length,0);assert.equal(f.destroyed,0);
    flush.resolve();await tick();assert.equal(f.old.removed,undefined);f.frames.shift()();await tick();assert.equal(committed,0);
    f.frames.shift()();await stage.ready;assert.equal(committed,1);assert.equal(f.destroyed,1);assert.equal(readyToWrite(),true);
    assert.equal(f.old.removed,true);assert.equal(stage.body.style.visibility,'');
  }finally{f.restore();}
});
test('cancelled editor cannot reveal itself after a later initialization completes',async()=>{
  const f=environment(),ready=deferred();let committed=0,destroyed=0;
  try{
    const stage=stageEditor(f.root,f.previous,()=>({ready:ready.promise,destroy(){destroyed++;}}),()=>committed++);
    stage.cancel();ready.resolve();await tick();f.frames.shift()();await tick();f.frames.shift()();await stage.ready;
    assert.equal(committed,0);assert.equal(destroyed,1);assert.equal(f.destroyed,0);assert.equal(f.old.removed,undefined);
  }finally{f.restore();}
});
test('old editor stays connected until its accepted navigation reply has been delivered',async()=>{
  const f=environment(),reply=deferred();f.old.retiring=true;f.previous.instance.destroy=()=>reply.promise;
  try{
    const stage=stageEditor(f.root,f.previous,()=>({ready:Promise.resolve(),destroy(){}}),()=>{});
    await tick();f.frames.shift()();await tick();f.frames.shift()();await stage.ready;
    assert.equal(f.old.removed,undefined);assert.equal(f.old.dataset.qfRetiredRoot,'');
    reply.resolve();await tick();assert.equal(f.old.removed,true);
  }finally{f.restore();}
});
