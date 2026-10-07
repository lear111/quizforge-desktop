import test from 'node:test';
import assert from 'node:assert/strict';
import {createHtmlRenderer} from '../src/extensions/html-ui.js';
import {createPermissionPolicy} from '../src/extensions/permissions.js';
import {RendererMode} from '../src/shared/renderer/contract.js';

const tick=async()=>{for(let i=0;i<12;i++)await Promise.resolve();};
async function fixture(readOnly=false){
  const saved={window:globalThis.window,document:globalThis.document},listeners=new Map(),posted=[];
  class Node {
    constructor(tag){this.tag=tag;this.style={};this.dataset={};this.children=[];this.classList={add(){}};
      if(tag==='iframe')this.contentWindow={postMessage:message=>posted.push(message)};}
    setAttribute(){} append(...nodes){for(const node of nodes){node.parent=this;this.children.push(node);}}
    querySelector(){return null;} closest(){return null;}
    addEventListener(){} removeEventListener(){} remove(){this.removed=true;}
  }
  globalThis.window={addEventListener:(name,listener)=>listeners.set(name,listener),removeEventListener:name=>listeners.delete(name)};
  let rootFrame;
  globalThis.document={createElement:tag=>{const node=new Node(tag);if(tag==='iframe')rootFrame=node;return node;}};
  let allowed=true,mutations=0,request=0;
  const permissions=['answer.write','practice.submit','practice.retry'],policy=createPermissionPolicy(permissions,permissions);
  const initial={state:'UNANSWERED',presentation:{question:{id:'q'},answer:{}}};
  const definition=createHtmlRenderer({id:'TEST',pageApi:'simple',dataVersion:1,permissions},{rendererHtml:'<p>Question</p>',stylesSource:'',rendererSource:''},permissions,policy,
    {answer:async()=>{},question:async()=>{},retain(){},release(){}});
  const renderer=definition.mount(new Node('div'),initial,{mode:readOnly?RendererMode.READ_ONLY_HISTORY:RendererMode.ACTIVE,
    canInteract:()=>allowed,pageState:()=>({ok:true,data:{}}),requestSubmit:()=>mutations++,answerChanged:()=>mutations++,requestRetry:()=>mutations++,
    layoutHost:{configure(){},getState:()=>({})}});
  await tick();
  // Find the opaque frame through the renderer's original root, without executing package code.
  return {renderer,policy,posted,initial,lock(){allowed=false;renderer.setInteractionMode('DISABLED');},unlock(){allowed=true;renderer.setInteractionMode('INTERACT');},
    get mutations(){return mutations;},
    async call(method,args=[]){
      const node=nodesFrame();const session=/"session":"([^"]+)"/.exec(node.srcdoc)[1],id=String(++request);
      await listeners.get('message')({source:node.contentWindow,data:{channel:'qf-type-frame',session,kind:'request',id,method,args}});
      const reply=posted.find(message=>message.kind==='reply'&&message.id===id).reply;
      await listeners.get('message')({source:node.contentWindow,data:{channel:'qf-type-frame',session,kind:'reply-ack',id}});
      return reply;
    },
    async save(purpose){
      const loaded=await this.call('page.load');
      return this.call('page.save',[{requestId:'save-'+request,contextId:loaded.data.contextId,revision:loaded.data.revision,purpose,data:{answer:{selectedOptionIds:[]}}}]);
    },
    async destroy(){await renderer.destroy();Object.assign(globalThis,saved);}
  };
  function nodesFrame(){return rootFrame;}
}

test('retained card keeps its displayed capabilities while actual writes stay locked',async()=>{
  const f=await fixture();try{
    assert.equal((await f.call('page.load')).data.permissions.submit,true);
    f.renderer.suspendPresentation();const notifications=f.posted.filter(m=>m.kind==='state').length;
    f.lock();f.renderer.update(f.initial);
    assert.equal(f.posted.filter(m=>m.kind==='state').length,notifications);
    assert.equal((await f.call('page.load')).data.permissions.submit,true);
    for(const purpose of ['draft','submit']){
      assert.equal((await f.save(purpose)).error.code,'READ_ONLY');
    }
    assert.equal(f.mutations,0);
    f.renderer.resumePresentation();assert.equal((await f.call('page.load')).data.permissions.submit,false);
    f.unlock();assert.equal((await f.call('page.load')).data.permissions.submit,true);
  }finally{await f.destroy();}
});

test('presentation suspension cannot preserve revoked permissions',async()=>{
  const f=await fixture();try{
    f.renderer.suspendPresentation();f.lock();f.policy.update([]);
    assert.equal((await f.call('page.load')).data.permissions.submit,false);
    assert.equal((await f.save('submit')).error.code,'PERMISSION_DENIED');assert.equal(f.mutations,0);
  }finally{await f.destroy();}
});

test('history presentation suspension never upgrades readonly capabilities',async()=>{
  const f=await fixture(true);try{
    f.renderer.suspendPresentation();f.lock();
    assert.equal((await f.call('page.load')).data.permissions.submit,false);
    assert.equal((await f.save('draft')).error.code,'READ_ONLY');assert.equal(f.mutations,0);
  }finally{await f.destroy();}
});

test('forged requests cannot reach private host services or removed APIs',async()=>{
  const f=await fixture();try{
    for(const method of ['answer.update','editor.update','practice.submit','page.add','page.attempt','host.getContext','whiteboard.clear']){
      assert.equal((await f.call(method,[{}])).error.code,'UNKNOWN_METHOD',method);
    }
    assert.equal(f.mutations,0);
  }finally{await f.destroy();}
});
