import test from 'node:test';
import assert from 'node:assert/strict';
import {nativePage} from '../src/extensions/native-page.js';

function fixture(){
  const saved=Object.fromEntries(['window','document','ResizeObserver','setInterval','clearInterval'].map(key=>[key,globalThis[key]]));
  const calls=[],nodes=[];
  class Node{constructor(tag){this.tag=tag;this.style={};this.dataset={};this.listeners=new Map();this.children=[];this.clientWidth=720;this.clientHeight=400;this.isConnected=true;this.value='';nodes.push(this);}setAttribute(){}append(...children){this.children.push(...children);}addEventListener(type,fn){this.listeners.set(type,fn);}getBoundingClientRect(){return {left:10,top:20,width:360,height:200};}focus(){}blur(){}setPointerCapture(){}releasePointerCapture(){}fire(type,value={}){this.listeners.get(type)?.({type,preventDefault(){},stopPropagation(){},target:this,...value});}}
  globalThis.document={createElement:tag=>new Node(tag)};
  globalThis.ResizeObserver=class{observe(){}disconnect(){calls.push(['disconnect']);}};
  globalThis.setInterval=()=>1;globalThis.clearInterval=()=>{};
  globalThis.window={dispatchEvent(){},nativePagesHost:{open:(...args)=>calls.push(['open',...args]),send:(...args)=>calls.push(['send',...args]),input:(...args)=>calls.push(['input',...args]),viewport:(...args)=>calls.push(['viewport',...args]),visibleBounds:()=>'{"top":0,"height":800}',closePage:session=>calls.push(['close',session])}};
  const boot={session:'native-page-test-session',relayWheel:true},messages=[],failures=[];
  const page=nativePage(boot,'<p>Never execute here</p>',event=>messages.push(event),error=>failures.push(error));
  return {page,boot,calls,nodes,messages,failures,restore(){page.destroy();Object.assign(globalThis,saved);},receive(event,session=boot.session){window.qfNativePages.receiveFromJson(JSON.stringify({session,event}));}};
}
test('native page transports owned messages, drops stale sessions, and closes its process once',()=>{
  const f=fixture();try{
    f.page.start();assert.equal(f.calls[0][0],'open');assert.equal(f.calls[0][2],'<p>Never execute here</p>');
    f.page.frame.contentWindow.postMessage({kind:'state'});assert.equal(JSON.parse(f.calls.find(c=>c[0]==='send')[2]).kind,'state');
    f.receive({kind:'page',message:{channel:'qf-type-frame',session:'wrong',kind:'ready'}});assert.equal(f.messages.length,0);
    f.receive({kind:'page',message:{channel:'qf-type-frame',session:f.boot.session,kind:'ready'}});assert.equal(f.messages.length,1);
    f.receive({kind:'failure',code:'EXTENSION_TIMEOUT',message:'stopped'});assert.equal(f.failures[0].code,'EXTENSION_TIMEOUT');
    f.page.destroy();f.page.destroy();assert.equal(f.calls.filter(c=>c[0]==='close').length,1);
    f.receive({kind:'page',message:{channel:'qf-type-frame',session:f.boot.session,kind:'ready'}});assert.equal(f.messages.length,1);
  }finally{f.restore();}
});
test('native input maps zoomed coordinates, commits IME once, and leaves drawing gestures to the board',()=>{
  const f=fixture();try{
    f.page.start();const frame=f.page.frame,input=f.nodes.find(n=>n.tag==='textarea');
    frame.fire('pointerdown',{clientX:190,clientY:120,button:0,buttons:1,pointerId:1});
    const pointer=JSON.parse(f.calls.find(c=>c[0]==='input')[2]);assert.deepEqual([pointer.x,pointer.y],[360,200]);
    input.fire('compositionstart');input.value='中文';input.fire('input');input.fire('compositionend');input.fire('input');
    const text=f.calls.filter(c=>c[0]==='input').map(c=>JSON.parse(c[2])).filter(e=>e.type==='text');assert.deepEqual(text,[{type:'text',text:'中文'}]);
    f.page.state('PEN');const count=f.calls.filter(c=>c[0]==='input').length;frame.fire('pointerdown',{clientX:20,clientY:30,buttons:1});assert.equal(f.calls.filter(c=>c[0]==='input').length,count);
  }finally{f.restore();}
});
