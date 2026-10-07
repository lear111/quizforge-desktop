import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {startFrameClient} from '../src/extensions/frame-client.js';
import {validateArguments,validateMethodArguments} from '../src/extensions/protocol.js';
import {connectFrame} from '../src/extensions/frame-host.js';
import {createSimplePageClient} from '../src/extensions/simple-client.js';

function client(){
  const posted=[],listeners=new Map(),timers=new Map();let timer=0;
  const parent={postMessage:value=>posted.push(value)};
  class Node {
    constructor(tag){this.tag=tag;this.dataset={};this.children=[];this.events=new Map();}
    setAttribute(){} contains(){return true;} getBoundingClientRect(){return {height:1};}
    append(...nodes){for(const node of nodes){node.parent=this;this.children.push(node);}}
    replaceChildren(){this.children=[];}
    querySelector(selector){return this.children.find(node=>selector===node.tag||selector==='[data-qf-error]'&&Object.hasOwn(node.dataset,'qfError'))||null;}
    addEventListener(name,fn){this.events.set(name,fn);}removeEventListener(name,fn){if(this.events.get(name)===fn)this.events.delete(name);}
    remove(){if(this.parent)this.parent.children=this.parent.children.filter(node=>node!==this);}
  }
  const root=new Node('section');root.height=1;root.getBoundingClientRect=()=>({height:root.height});
  const window={addEventListener:(name,fn)=>listeners.set(name,fn)};
  const context=vm.createContext({window,parent,document:{querySelector:()=>root,addEventListener(){},createElement:tag=>new Node(tag)},
    crypto:{getRandomValues:bytes=>bytes},ResizeObserver:class{observe(){} disconnect(){}},MutationObserver:class{observe(){} disconnect(){}},
    setTimeout:(fn,delay)=>{timers.set(++timer,{fn,delay});return timer;},clearTimeout:id=>timers.delete(id)});
  vm.runInContext(`(${startFrameClient.toString()})({session:'session',mode:'EDITOR',layout:{},ui:{},layoutState:{}},()=>{},()=>{},()=>{},${validateArguments.toString()},${createSimplePageClient.toString()});`,context);
  function reply(message,source=parent){listeners.get('message')({source,data:{channel:'qf-type-frame',session:'session',kind:'reply',...message}});}
  return {context,posted,timers,root,reply,state:()=>listeners.get('message')({source:parent,data:{channel:'qf-type-frame',session:'session',kind:'state',interaction:'INTERACT'}}),
    flush:()=>listeners.get('message')({source:parent,data:{channel:'qf-type-frame',session:'session',kind:'flush',id:'flush'}}),
    error:event=>listeners.get('error')(event),rejection:event=>listeners.get('unhandledrejection')(event),close:()=>listeners.get('pagehide')(),run:code=>vm.runInContext(code,context),requests:()=>posted.filter(m=>m.kind==='request')};
}
const tick=async()=>{for(let i=0;i<24;i++)await Promise.resolve();};
const contextReply={ok:true,data:{contextId:'context',revision:0,permissions:{editQuestion:true},question:{data:{}},attempt:null}};
async function register(c){
  const registration=c.run('window.QF.page.register({onLoad(){}})');await tick();
  c.reply({id:c.requests().at(-1).id,reply:contextReply});await registration;
}

test('flush waits for page loading and rendering before sending its final geometry',async()=>{
  const c=client();
  c.run('this.loading=window.QF.page.register({onLoad(){return window.QF.content.renderAsync(window.QF.dom.root,{kind:"TEXT",text:"updated"});}});window.__qfStart(loading);');
  await tick();c.reply({id:c.requests().at(-1).id,reply:contextReply});await tick();c.flush();await tick();
  assert.equal(c.posted.some(m=>m.kind==='flushed'),false);
  c.root.height=240;c.reply({id:c.requests().at(-1).id,reply:{ok:true,data:{kind:'TEXT',text:'updated'}}});await tick();
  const geometry=c.posted.findIndex(m=>m.kind==='geometry'&&m.height===240);
  const receipt=c.posted.findIndex(m=>m.kind==='flushed');
  assert.ok(geometry>=0);assert.ok(receipt>geometry);c.close();
});

test('save commands wait for draft writes and reject duplicate transitions as busy',async()=>{
  const c=client();await register(c);
  const write=c.run("window.QF.save({purpose:'editDraft',data:{questionData:{value:'latest'}}})");await tick();
  const save=c.run("window.QF.requestAction({action:'saveBank'})");
  assert.equal((await c.run("window.QF.requestAction({action:'saveBank'})")).error.code,'BUSY');
  assert.deepEqual(c.requests().map(m=>m.method),['page.load','page.save']);
  c.reply({id:c.requests().at(-1).id,reply:{ok:true,data:{contextId:'context',revision:1}}});await write;await tick();
  assert.deepEqual(c.requests().map(m=>m.method),['page.load','page.save','page.action']);
  assert.equal(c.requests().at(-1).args[0].revision,1);
  c.reply({id:c.requests().at(-1).id,reply:{ok:true,data:{contextId:'context',revision:2,saved:true}}});assert.equal((await save).data.saved,true);c.close();
});
test('invalid JSON data is rejected locally without sending a request',async()=>{
  const c=client();
  for(const expression of ['Infinity','NaN','1n','()=>{}','new Date()']){
    assert.equal((await c.run(`window.QF.save({purpose:'editDraft',data:{questionData:{value:${expression}}}})`)).error.code,'INVALID_ARGUMENT');
  }
  assert.equal((await c.run('(()=>{const a={};a.self=a;return window.QF.save(a)})()')).error.code,'INVALID_ARGUMENT');
  assert.equal(c.requests().length,0);c.close();
});
test('only compact APIs and public components are exposed to package scripts',()=>{
  const c=client();
  assert.deepEqual(Array.from(c.run('Object.keys(window.QF).sort()')),['content','dom','ids','layout','page','requestAction','save','ui']);
  for(const name of ['host','editor','bank','answer','practice','navigation','sources','learning','whiteboard'])assert.equal(c.run(`window.QF.${name}`),undefined);
  c.close();
});
test('rich editor follows permission changes, recovers from a denied format edit and disposes pending callbacks',async()=>{
  const c=client();
  c.run("this.container=document.createElement('div');this.changes=[];this.editor=window.QF.content.mountEditor(container,{value:{kind:'TEXT',text:'initial'},onChange:value=>changes.push(value)});window.__qfStart(Promise.resolve());");
  const contextReply=enabled=>({ok:true,data:{permissions:{editQuestion:enabled}}});
  c.reply({id:c.requests().at(-1).id,reply:contextReply(true)});await tick();
  assert.equal(c.run("container.children[0].children[0].disabled"),false);
  c.state();await tick();c.reply({id:c.requests().at(-1).id,reply:contextReply(false)});await tick();
  assert.equal(c.run("container.children[0].children[0].disabled"),true);
  assert.equal(c.run("container.children[1].disabled"),true);
  c.state();await tick();c.reply({id:c.requests().at(-1).id,reply:contextReply(true)});await tick();
  c.run("container.children[1].events.get('click')()");
  c.reply({id:c.requests().at(-1).id,reply:{ok:false,error:{code:'PERMISSION_DENIED',message:'permission changed'}}});await tick();
  c.flush();await tick();assert.equal(c.posted.find(m=>m.kind==='flushed').error,undefined);
  assert.equal(c.run('changes.length'),0);
  c.run("container.children[1].events.get('click')()");const request=c.requests().at(-1);
  c.run('editor.destroy();editor.destroy();');
  c.reply({id:request.id,reply:{ok:true,data:{kind:'TEXT',text:'late'}}});await tick();
  assert.equal(c.run('changes.length'),0);assert.equal(c.run('container.children.length'),0);
  const count=c.requests().length;c.state();await tick();assert.equal(c.requests().length,count);c.close();
});
test('component requests resolve typed failures on timeout, malformed replies and page closure',async()=>{
  const c=client();let result=c.run('window.QF.content.renderAsync(window.QF.dom.root,{kind:"TEXT",text:"test"})');
  const failure=promise=>promise.then(()=>assert.fail('expected failure'),error=>error.message);
  const timeout=[...c.timers.values()].find(t=>t.delay===30000);timeout.fn();assert.match(await failure(result),/超时/);
  result=c.run('window.QF.content.renderAsync(window.QF.dom.root,{kind:"TEXT",text:"test"})');c.reply({id:c.requests().at(-1).id,reply:{unexpected:true}});assert.match(await failure(result),/格式无效/);
  result=c.run('window.QF.content.renderAsync(window.QF.dom.root,{kind:"TEXT",text:"test"})');c.close();assert.match(await failure(result),/已关闭/);
  assert.match(await failure(c.run('window.QF.content.renderAsync(window.QF.dom.root,{kind:"TEXT",text:"test"})')),/已关闭/);
});
test('forged sources cannot complete a request; acknowledgement follows delivery',async()=>{
  const c=client(),result=c.run('window.QF.content.renderAsync(window.QF.dom.root,{kind:"TEXT",text:"test"})'),id=c.requests().at(-1).id;let completed=false;
  result.then(()=>completed=true);c.reply({id,reply:{ok:true,data:'forged'}},{});await tick();assert.equal(completed,false);
  c.reply({id,reply:{ok:true,data:{kind:'TEXT',text:'real'}}});assert.equal(await result,c.root);
  assert.equal(c.posted.some(m=>m.kind==='reply-ack'),false);
  [...c.timers.values()].filter(t=>t.delay===0).forEach(t=>t.fn());assert.equal(c.posted.some(m=>m.kind==='reply-ack'&&m.id===id),true);c.close();
});
test('host data validation bounds depth and keeps a detached argument snapshot',()=>{
  const source=[{value:[1,2]}],copy=validateArguments(source);source[0].value.push(3);assert.deepEqual(copy,[{value:[1,2]}]);
  let deep={};for(let i=0;i<34;i++)deep={next:deep};assert.throws(()=>validateArguments([deep]),/嵌套/);
  assert.throws(()=>validateArguments([NaN]));assert.throws(()=>validateArguments([{}, {}, {}, {}, {}]));
});
test('capability argument shapes reject extra parameters and invalid indices',()=>{
  validateMethodArguments('practice.submit',[]);validateMethodArguments('editor.update',[{prompt:{kind:'TEXT',text:''}}]);
  for(const [method,args]of [['practice.submit',[1]],['answer.update',[null]],['navigation.goTo',[-1]],['sources.open',[1.5]],['page.attempt',['']],['page.save',[null]]])assert.throws(()=>validateMethodArguments(method,args));
});

function dispatcher(invoke,onFailure=()=>{}){
  const posted=[],listeners=new Map(),timers=new Map();let timer=0;
  const frame={style:{},setAttribute(){},contentWindow:{postMessage:value=>posted.push(value)},remove(){this.removed=true;}};
  const page={style:{},append(){},classList:{add(){}}};
  const context=vm.createContext({document:{createElement:()=>frame},window:{addEventListener:(n,f)=>listeners.set(n,f),removeEventListener:n=>listeners.delete(n)},
    frameDocument:()=>'',setTimeout:(fn,delay)=>{timers.set(++timer,{fn,delay});return timer;},clearTimeout:id=>timers.delete(id),invoke});
  vm.runInContext(`this.dispatcher=(${connectFrame.toString()});this.validateArguments=${validateArguments.toString()};`,context);
  const host=context.dispatcher(page,'',{html:'',styles:'',boot:{session:'session'},invoke,onReady(){},onFailure,getState:()=>({layoutState:{}}),interaction:()=> 'INTERACT'});
  const send=(message,source=frame.contentWindow)=>listeners.get('message')?.({source,data:{channel:'qf-type-frame',session:'session',...message}});
  // Arguments must belong to the dispatcher's realm, just as structured cloning does in a browser.
  const request=(id,method,args=[],source)=>send({kind:'request',id,method,args:vm.runInContext(JSON.stringify(args),context)},source);
  return {host,posted,frame,send,request,timers};
}

test('failed or timed out frames are removed and lose their capabilities',async()=>{
  let calls=0;const errors=[];
  const d=dispatcher(()=>{calls++;return {ok:true,data:null};},error=>errors.push(error.message));
  await d.send({kind:'failed',error:'broken script'});
  assert.equal(d.frame.removed,true);assert.deepEqual(errors,['broken script']);
  await d.request('1','page.save',[{purpose:'draft',data:{answer:{value:1}}}]);assert.equal(calls,0);
  await assert.rejects(d.host.flush(),/已关闭/);await d.host.destroy();
  const stalled=dispatcher(()=>{},error=>errors.push(error.message));
  [...stalled.timers.values()].find(timer=>timer.delay===15000).fn();
  assert.equal(stalled.frame.removed,true);assert.match(errors.at(-1),/加载超时/);await stalled.host.destroy();
});

test('runtime errors and unhandled rejections notify the trusted parent',()=>{
  const c=client();
  // Runtime events are registered independently of page initialization.
  c.run("window.QF.ui.getConfiguration();");
  // The mock window stores the browser event handlers on the client harness.
  c.error({error:{message:'click handler failed'}});
  assert.equal(c.posted.find(m=>m.kind==='failed').error,'click handler failed');
  c.close();
  const other=client();let prevented=false;
  other.rejection({reason:new Error('async handler failed'),preventDefault(){prevented=true;}});
  assert.equal(prevented,true);assert.equal(other.posted.find(m=>m.kind==='failed').error,'async handler failed');other.close();
});
test('dispatcher rejects forged sources and malformed parameters before invoking capabilities',async()=>{
  let count=0;const d=dispatcher(()=>{count++;return {ok:true,data:null};});
  await d.request('1','page.save',[],{});assert.equal(count,0);assert.equal(d.posted.length,0);
  await d.send({kind:'request',id:'1',method:'page.save',args:'invalid'});
  assert.equal(d.posted.at(-1).reply.error.code,'INVALID_ARGUMENT');assert.equal(count,0);await d.host.destroy();
});
test('revocation cancels queued writes and retains only accepted operation receipts',async()=>{
  let finish;const calls=[];const d=dispatcher((method,args)=>{calls.push(args);return new Promise(resolve=>{finish=resolve;});});
  const first=d.request('1','page.save',[{purpose:'draft',data:{answer:{value:1}}}]),second=d.request('2','page.save',[{purpose:'draft',data:{answer:{value:2}}}]);await tick();assert.equal(calls.length,1);
  const closing=d.host.destroy();await d.send({kind:'reply-ack',id:'1'});assert.equal(d.frame.removed,undefined);
  await d.request('3','page.save',[{purpose:'draft',data:{answer:{value:3}}}]);assert.equal(d.posted.at(-1).reply.error.code,'PAGE_CLOSED');
  finish({ok:true,data:{saved:true}});await Promise.all([first,second]);assert.equal(calls.length,1);
  assert.equal(d.posted.find(m=>m.id==='1').reply.ok,true);assert.equal(d.posted.find(m=>m.id==='2').reply.error.code,'PAGE_CLOSED');
  await d.send({kind:'reply-ack',id:'1'});await d.send({kind:'reply-ack',id:'2'});await closing;assert.equal(d.frame.removed,true);
});

test('a failed frame cannot execute writes that were still waiting behind an accepted write',async()=>{
  let finish;const calls=[];const d=dispatcher((method,args)=>{calls.push(args);return new Promise(resolve=>{finish=resolve;});});
  const first=d.request('1','page.save',[{purpose:'draft',data:{answer:{value:1}}}]),second=d.request('2','page.save',[{purpose:'draft',data:{answer:{value:2}}}]);await tick();
  assert.equal(calls.length,1);await d.send({kind:'failed',error:'page stopped'});assert.equal(d.frame.removed,true);
  finish({ok:true,data:{saved:true}});await Promise.all([first,second]);assert.equal(calls.length,1);await d.host.destroy();
});
