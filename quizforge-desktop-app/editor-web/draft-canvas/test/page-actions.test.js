import test from 'node:test';
import assert from 'node:assert/strict';
import {createPageActions} from '../src/learning/page-actions.js';

test('native replies resolve only their matching request with scoped arguments',async()=>{
  const sent=[];
  const actions=createPageActions(()=>({state:id=>JSON.stringify({ok:true,data:{id,index:1}}),request:(...args)=>sent.push(args)}));
  assert.equal(actions.getState('q_one').data.id,'q_one');
  const first=actions.request('q_one','navigate',2),second=actions.request('q_one','learning.mode','DRAFT');
  assert.deepEqual(sent.map(args=>[args[0],args[2],JSON.parse(args[3])]),[['q_one','navigate',2],['q_one','learning.mode','DRAFT']]);
  actions.replyFromJson(JSON.stringify({id:'unknown',reply:{ok:true}}));
  actions.replyFromJson(JSON.stringify({id:sent[1][1],reply:{ok:false,error:{code:'BUSY'}}}));
  actions.replyFromJson(JSON.stringify({id:sent[0][1],reply:{ok:true,data:{index:2}}}));
  assert.equal((await first).data.index,2);assert.equal((await second).error.code,'BUSY');
  actions.destroy();
});
test('missing or released native host cannot leave requests hanging',async()=>{
  const unavailable=createPageActions(()=>null);assert.equal((await unavailable.request('q','navigate',1)).error.code,'CAPABILITY_UNAVAILABLE');
  const closed=createPageActions(()=>({request(){}}));const pending=closed.request('q','navigate',1);closed.destroy();
  assert.equal((await pending).error.code,'PAGE_CLOSED');
  const broken=createPageActions(()=>({state(){throw Error('released');},request(){throw Error('released');}}));
  assert.equal(broken.getState('q').error.code,'HOST_FAILED');
  assert.equal((await broken.request('q','navigate',1)).error.code,'HOST_FAILED');broken.destroy();
});
