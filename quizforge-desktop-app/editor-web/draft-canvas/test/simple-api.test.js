import test from 'node:test';
import assert from 'node:assert/strict';
import {createSimpleApi} from '../src/extensions/simple-api.js';
import {createSimplePageClient} from '../src/extensions/simple-client.js';

test('registration rejects malformed optional hooks before loading and snapshots valid hooks',async()=>{
  let loads=0,leaves=0,disposed=0;
  const client=createSimplePageClient({request:async()=>{loads++;return {ok:true,data:{contextId:'ctx',revision:0}};},track:p=>p,configure(){},boot:{session:'test'},notify(){}});
  for(const name of ['onBeforeLeave','onDispose'])for(const value of [null,false,42,'callback',{}]){
    await assert.rejects(client.register({onLoad(){},[name]:value}),new RegExp(name));
  }
  assert.equal(loads,0);
  const hooks={onLoad(){},onBeforeLeave(){leaves++;},onDispose(){disposed++;}};
  await client.register(hooks);hooks.onBeforeLeave='invalid';hooks.onDispose=0;
  await client.prepare();await client.dispose();assert.equal(leaves,1);assert.equal(disposed,1);
});
import {connectPage} from '../../../../extensions/shared/page-client.js';
const copy=v=>structuredClone(v),ok=data=>({ok:true,data}),fail=code=>({ok:false,error:{code,message:code}});
function host(options={}){
  const calls=[];let active=true;
  const api=createSimpleApi({contextId:'ctx',getContext:async()=>({navigation:{questions:[{id:'q',index:0}]}}),
    isCurrent:()=>active,invoke:async(method,args)=>{calls.push([method,args]);return options.invoke?options.invoke(method,args):ok({});}});
  return {api,calls,close(){active=false;},request:(purpose,data,revision=0,requestId='r')=>({contextId:'ctx',revision,requestId,purpose,data})};
}
test('save validates identity, revision, payload and does not accept page-injected scores',async()=>{
  const h=host();
  assert.equal((await h.api.save({...h.request('draft',{answer:{}}),contextId:'other'})).error.code,'CONTEXT_EXPIRED');
  assert.equal((await h.api.save(h.request('draft',{answer:{}},99,'bad-version'))).error.code,'REVISION_CONFLICT');
  assert.equal((await h.api.save(h.request('submit',{answer:{},score:100},0,'score'))).error.code,'INVALID_REQUEST');
  assert.equal(h.calls.length,0);
  h.close();assert.equal((await h.api.load()).error.code,'CONTEXT_EXPIRED');
});
test('save retains permission failures and retries cannot duplicate an accepted mutation',async()=>{
  const h=host({invoke:async method=>method==='editor.update'?fail('PERMISSION_DENIED'):ok({})});
  assert.equal((await h.api.save(h.request('edit',{questionData:{}}))).error.code,'PERMISSION_DENIED');
  assert.deepEqual(h.calls.map(c=>c[0]),['editor.update']);
  const normal=host(),r=normal.request('submit',{answer:{text:'answer'}});
  const [a,b]=await Promise.all([normal.api.save(r),normal.api.save(copy(r))]);
  assert.deepEqual(a,b);assert.equal(a.data.revision,1);
  assert.deepEqual(normal.calls.map(c=>c[0]),['answer.update','practice.submit']);
  assert.equal((await normal.api.save({...r,data:{answer:{text:'different'}}})).error.code,'INVALID_REQUEST');
  assert.equal((await normal.api.save({...r,requestId:'other'})).error.code,'REVISION_CONFLICT');
});
test('editDraft stages data, edit persists it, and unknown operations never reach native code',async()=>{
  const h=host();await h.api.save(h.request('editDraft',{questionData:{prompt:'x'}}));
  await h.api.save(h.request('edit',{questionData:{prompt:'y'}},1,'second'));
  assert.deepEqual(h.calls.map(c=>c[0]),['editor.update','editor.update','bank.save']);
  const r={contextId:'ctx',revision:2,requestId:'action',action:'downloadFile',params:{}};
  assert.equal((await h.api.action(r)).error.code,'ACTION_UNAVAILABLE');assert.equal(h.calls.length,3);
});
test('attempt navigation and question management are routed through existing capabilities',async()=>{
  const h=host();let revision=0;
  for(const [action,params,method] of [['goToQuestion',{questionId:'q'},'navigation.goTo'],['goToAttempt',{direction:'current'},'page.attempt'],['addQuestion',{type:'NEW'},'page.add'],['deleteQuestion',{},'bank.deleteQuestion'],['moveQuestion',{beforeQuestionId:null},'page.move']]){
    const reply=await h.api.action({contextId:'ctx',revision,requestId:String(revision),action,params});
    assert.equal(reply.ok,true);revision=reply.data.revision;assert.equal(h.calls.at(-1)[0],method);
  }
});

test('unsupported target focus and conflicting navigation parameters fail without navigating',async()=>{
  const h=host();
  const request={contextId:'ctx',revision:0,requestId:'focus',action:'goToQuestion',params:{questionId:'q',targetId:'part'}};
  assert.equal((await h.api.action(request)).error.code,'ACTION_UNAVAILABLE');
  assert.equal((await h.api.action({...request,requestId:'conflict',params:{questionId:'q',direction:'next'}})).error.code,'INVALID_REQUEST');
  assert.equal(h.calls.length,0);
});
function clientHost(){
  let client,revision=0,answer={},mode='practice',dirty=0;
  const pending=new Set(),calls=[];
  const context=()=>({contextId:'ctx',revision,mode,learningMode:'practice',question:{id:'q',type:'TEST',data:{id:'q',prompt:{text:'original'}}},attempt:{status:'draft',answer},navigation:{index:0,count:1},permissions:{writeAnswer:true,manageQuestions:true},grantedPermissions:[],types:[],sources:[]});
  client=createSimplePageClient({boot:{session:'test'},configure:()=>ok({}),notify(){},
    track(p){pending.add(p);p.finally(()=>pending.delete(p));return p;},
    async request(method,args){calls.push([method,args]);if(method==='page.load')return ok(context());
      const r=args[0];assert.equal(r.revision,revision,'writes must use the latest acknowledgement');
      if(r.purpose==='submit'||r.purpose==='edit'||method==='page.action'){
        // A native save/navigation flush must not wait on the request invoking it.
        await client.prepare();await Promise.all([...pending]);
      }
      if(r.data?.answer)answer=copy(r.data.answer);revision++;return ok({contextId:'ctx',revision});}
  });
  return {client,calls,pending,context,setMode(v){mode=v;},setDirty(v){dirty=v;},dirty:()=>dirty};
}
test('client drains dirty inputs before commit/submit without a recursive flush deadlock',{timeout:1500},async()=>{
  const h=clientHost();let pendingSave=null;
  await h.client.register({onLoad(){},onBeforeLeave:()=>({ok:true,data:{pendingSave}})});
  pendingSave={purpose:'draft',data:{answer:{text:'last input'}}};
  assert.equal((await h.client.save({purpose:'submit',data:{answer:{text:'last input'}}})).ok,true);
  pendingSave=null;
  assert.equal((await h.client.save({purpose:'edit',data:{questionData:{id:'q'}}})).ok,true);
  assert.equal((await h.client.requestAction({action:'goToQuestion',params:{direction:'next'}})).ok,true);
  assert.deepEqual(h.calls.filter(c=>c[0]!=='page.load').map(c=>c[1][0].purpose||'action'),['draft','submit','edit','action']);
});
test('client refuses beforeLeave failures and can configure default draft usage',async()=>{
  const h=clientHost();await h.client.register({onLoad(){},onBeforeLeave:()=>fail('FORM_INVALID')});
  assert.equal((await h.client.requestAction({action:'retry'})).error.code,'FORM_INVALID');
  await assert.rejects(h.client.prepare(),/FORM_INVALID/);
  assert.equal(h.calls.length,1);
  assert.equal(h.client.configure({useDraft:false,card:false}).ok,true);
  assert.equal(h.client.configure({download:true}).ok,false);
  await h.client.dispose();assert.equal((await h.client.save({purpose:'draft',data:{answer:{}}})).error.code,'CONTEXT_EXPIRED');
});

test('a rejected draft prevents navigation until a successful write recovers it',async()=>{
  let accepted=false,revision=0;const calls=[];
  const client=createSimplePageClient({boot:{session:'failure'},track:p=>p,notify(){},configure:()=>ok({}),
    async request(method,args){calls.push(method);if(method==='page.load')return ok({contextId:'ctx',revision,attempt:{answer:{}}});
      if(method==='page.save'&&!accepted)return fail('STORAGE_FAILED');
      return ok({contextId:'ctx',revision:++revision});}});
  await client.register({onLoad(){},onBeforeLeave:()=>({ok:true,data:{pendingSave:null}})});
  assert.equal((await client.save({purpose:'draft',data:{answer:{text:'first'}}})).ok,false);
  assert.equal((await client.requestAction({action:'goToQuestion',params:{direction:'next'}})).error.code,'STORAGE_FAILED');
  assert.equal(calls.includes('page.action'),false);
  accepted=true;assert.equal((await client.save({purpose:'draft',data:{answer:{text:'retry'}}})).ok,true);
  assert.equal((await client.requestAction({action:'goToQuestion',params:{direction:'next'}})).ok,true);
});
test('package helper restores accepted inputs on storage rejection',async()=>{
  let hooks,accepted=true;
  const context={mode:'editor',learningMode:'practice',question:{type:'TEST',maxScore:1,data:{id:'q',prompt:{text:'original'}}},attempt:null,navigation:{},permissions:{},sources:[],types:[],grantedPermissions:[]};
  const page=await connectPage({page:{async register(v){hooks=v;await hooks.onLoad(copy(context));}},save:async()=>accepted?ok({}):fail('STORAGE_FAILED')});
  await page.edit({prompt:{text:'accepted'}});accepted=false;
  assert.equal((await page.edit({prompt:{text:'rejected'}})).ok,false);
  assert.equal((await page.question()).data.prompt.text,'accepted');
});
