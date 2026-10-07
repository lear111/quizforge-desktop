import test from 'node:test';
import assert from 'node:assert/strict';
import {observeOptions,fitOptions} from '../../../../extensions/shared/page.js';
test('option layout defers grid changes and ignores height-only observer notifications',()=>{
  const original=new Map(['document','getComputedStyle','ResizeObserver','requestAnimationFrame','cancelAnimationFrame'].map(k=>[k,globalThis[k]]));
  const pending=new Map();let next=0,callback,disconnected=false,probes=0,writes=0,columns='';
  const label={textContent:'one line'},row={querySelector:()=>label};
  const root={children:[row],clientWidth:1000,style:{get gridTemplateColumns(){return columns;},set gridTemplateColumns(v){writes++;columns=v;}}};
  globalThis.document={body:{append(){probes++;}},fonts:{ready:Promise.resolve()},createElement:()=>({style:{},remove(){probes--;},getBoundingClientRect:()=>({width:120})})};
  globalThis.getComputedStyle=()=>({font:'16px Arial',letterSpacing:'normal',wordSpacing:'normal'});
  globalThis.requestAnimationFrame=fn=>{pending.set(++next,fn);return next;};globalThis.cancelAnimationFrame=id=>pending.delete(id);
  globalThis.ResizeObserver=class{constructor(fn){callback=fn;}observe(){}disconnect(){disconnected=true;}};
  try{
    const observer=observeOptions(root);assert.match(columns,/repeat\(4/);assert.equal(probes,0);assert.equal(writes,1);
    callback([{contentRect:{width:1000,height:30}}]);assert.equal(writes,1);assert.equal(pending.size,1);
    for(const [id,fn] of pending){pending.delete(id);fn();}assert.equal(writes,1);
    callback([{contentRect:{width:1000,height:200}}]);assert.equal(pending.size,0);
    root.clientWidth=500;callback([{contentRect:{width:500,height:200}}]);assert.equal(writes,1);
    for(const [id,fn] of pending){pending.delete(id);fn();}assert.match(columns,/repeat\(2/);assert.equal(writes,2);
    root.clientWidth=200;fitOptions(root);assert.match(columns,/repeat\(1/);
    callback([{contentRect:{width:200,height:200}}]);observer.disconnect();assert.equal(pending.size,0);assert.equal(disconnected,true);
  }finally{for(const [key,value] of original)if(value===undefined)delete globalThis[key];else globalThis[key]=value;}
});
