// This module is part of the trusted shell. No package JavaScript executes in this WebView.
const sessions=new Map();
function endpoint(){
  if(!window.qfNativePages)window.qfNativePages=Object.freeze({receiveFromJson(encoded){
    const {session,event}=JSON.parse(encoded);sessions.get(session)?.receive(event);
  }});
}
export function nativePage(boot,documentSource,onMessage,onFailure){
  const host=window.nativePagesHost;endpoint();
  const frame=document.createElement('div'),image=document.createElement('img'),input=document.createElement('textarea');
  frame.dataset.qfRemotePage=boot.session;frame.setAttribute('role','group');
  frame.style.cssText='position:relative;overflow:hidden;width:100%;height:1px;';
  image.draggable=false;image.alt='';image.style.cssText='position:absolute;left:0;top:0;width:100%;pointer-events:none;user-select:none;';
  input.setAttribute('aria-label','题卡文字输入');input.setAttribute('autocomplete','off');input.setAttribute('autocapitalize','off');input.spellcheck=false;
  input.style.cssText='position:absolute;left:0;top:0;width:1px;height:1px;opacity:0;padding:0;border:0;resize:none;';
  frame.append(image,input);
  let closed=false,started=false,lastViewport='',composing=false,interaction='INTERACT';
  // A private identity token preserves the existing source/session checks without executing a package locally.
  const port={postMessage(message){if(!closed)host.send(boot.session,JSON.stringify(message));}};frame.contentWindow=port;
  frame.srcdoc=documentSource; // Authoring diagnostics only; never parsed or executed in the shell.
  const sendInput=event=>{if(!closed&&started)host.input(boot.session,JSON.stringify(event));};
  function viewport(){
    if(closed||!started||!frame.isConnected)return;
    const r=frame.getBoundingClientRect(),scale=r.width/frame.clientWidth||1;
    const bounds=JSON.parse(host.visibleBounds()),top=Math.max(0,bounds.top-r.top);
    const width=Math.max(1,Math.min(2048,Math.round(frame.clientWidth)));
    const offset=Math.max(0,Math.min(200000,Math.floor(top/scale)));
    const height=Math.max(1,Math.min(1536,Math.ceil(Math.min(frame.clientHeight-offset,(bounds.height+200)/scale))));
    const value=[width,height,offset].join(':');if(value!==lastViewport){lastViewport=value;host.viewport(boot.session,width,height,offset);}
  }
  const modifiers=e=>({shiftKey:e.shiftKey,ctrlKey:e.ctrlKey,altKey:e.altKey,metaKey:e.metaKey});
  for(const type of ['pointerdown','pointermove','pointerup','pointercancel'])frame.addEventListener(type,e=>{
    if(interaction!=='INTERACT'||e.target===input)return;
    if(type==='pointerdown'){viewport();input.focus({preventScroll:true});frame.setPointerCapture?.(e.pointerId);}
    const r=frame.getBoundingClientRect();
    sendInput({type:type==='pointermove'&&e.buttons?'pointerdrag':type==='pointercancel'?'pointerup':type,x:(e.clientX-r.left)*frame.clientWidth/r.width,y:(e.clientY-r.top)*frame.clientHeight/r.height,button:e.button,buttons:e.buttons,clickCount:e.detail||1,...modifiers(e)});
    if(type==='pointerup'||type==='pointercancel')frame.releasePointerCapture?.(e.pointerId);
    e.preventDefault();e.stopPropagation();
  });
  for(const type of ['keydown','keyup'])input.addEventListener(type,e=>{
    if(e.isComposing||composing)return;
    if((e.ctrlKey||e.metaKey)&&e.code==='KeyV')return; // The local paste/input commits once, through text.
    sendInput({type,key:e.key,code:e.code,...modifiers(e)});
    // Printable text and IME commits arrive through input; navigation keys run only in the worker.
    if(e.key.length!==1&&!['Shift','Control','Alt','Meta'].includes(e.key)||(e.ctrlKey||e.metaKey)&&['KeyA','KeyC','KeyX'].includes(e.code))e.preventDefault();
    e.stopPropagation();
  });
  input.addEventListener('compositionstart',()=>{composing=true;});
  input.addEventListener('compositionend',()=>{composing=false;if(input.value){sendInput({type:'text',text:input.value});input.value='';}});
  input.addEventListener('input',()=>{if(!composing&&input.value){sendInput({type:'text',text:input.value});input.value='';}});
  input.addEventListener('blur',()=>sendInput({type:'blur',session:boot.session}));
  frame.addEventListener('wheel',e=>{
    if(!boot.relayWheel)return;
    e.preventDefault();const r=frame.getBoundingClientRect();
    onMessage({source:port,data:{kind:'wheel',channel:'qf-type-frame',session:boot.session,x:(e.clientX-r.left)*frame.clientWidth/r.width,y:(e.clientY-r.top)*frame.clientHeight/r.height,deltaX:e.deltaX,deltaY:e.deltaY,deltaMode:e.deltaMode,ctrlKey:e.ctrlKey}});
  },{passive:false});
  const entry={receive(event){
    if(closed)return;
    if(event.kind==='failure'){onFailure(Object.assign(new Error(event.message),{code:event.code}));return;}
    if(event.kind==='image'){
      if(typeof event.png!=='string'||!Number.isFinite(event.offset)||!Number.isFinite(event.height))return;
      image.style.top=event.offset+'px';image.style.height=event.height+'px';image.src='data:image/png;base64,'+event.png;return;
    }
    if(event.kind==='page'){
      const data=event.message;if(data?.session!==boot.session||data?.channel!=='qf-type-frame')return;
      onMessage({source:port,data});
      // Allows trusted developer tools to observe the same opaque page messages as on the web host.
      window.dispatchEvent(new MessageEvent('message',{source:null,data}));
    }
  }};
  sessions.set(boot.session,entry);
  const resize=new ResizeObserver(viewport);resize.observe(frame);
  const timer=setInterval(viewport,250);
  return {frame,start(){started=true;host.open(boot.session,documentSource,Math.max(1,Math.min(2048,Math.round(frame.clientWidth||720))));viewport();},
    state(value){interaction=value;frame.style.cursor=value==='INTERACT'?'auto':'';if(value!=='INTERACT')input.blur();},
    destroy(){if(closed)return;closed=true;clearInterval(timer);resize.disconnect();sessions.delete(boot.session);host.closePage(boot.session);}};
}
