/** Runs entirely inside the sandbox. This function must not capture any host objects. */
export function startFrameClient(boot,renderContent,configureLayout,configureUi,validateArguments) {
  const root=document.querySelector('.qf-extension-page');
  const pending=new Set(),writes=new Set(),requests=new Map(),singleFlights=new Map(),subscriptions=new Set(),controls=new Set(),disposers=new Set();
  let sequence=0,lastError=null,closed=false,ready,layout=boot.layout,ui=boot.ui,layoutState=boot.layoutState,interaction='INTERACT';
  const clone=value=>value==null?value:JSON.parse(JSON.stringify(value));
  const ok=data=>({ok:true,data:clone(data)});
  const fail=(code,message)=>({ok:false,error:{code,message,retryable:false}});
  const send=value=>parent.postMessage({...value,channel:'qf-type-frame',session:boot.session},'*');
  // Offscreen WebKit's native popup is outside the captured page. Keep ordinary select markup usable.
  if(boot.nativePage){
    let popup,selected,padding;
    const close=()=>{if(popup){root.style.paddingBottom=padding;popup.remove();}popup=null;selected=null;};
    const open=select=>{
      if(select===selected){close();return;}close();selected=select;
      const r=select.getBoundingClientRect();popup=document.createElement('div');popup.setAttribute('role','listbox');
      popup.style.cssText=`position:absolute;z-index:2147483647;left:${r.left}px;top:${r.bottom}px;min-width:${r.width}px;max-height:240px;overflow:auto;background:white;color:#282432;border:1px solid #ded8e8;border-radius:6px;box-shadow:0 4px 16px #0002;padding:4px;`;
      for(const option of select.options){const button=document.createElement('button');button.type='button';button.textContent=option.textContent;button.disabled=option.disabled;button.setAttribute('role','option');button.setAttribute('aria-selected',String(option.selected));button.style.cssText='display:block;width:100%;text-align:left;border:0;padding:6px 10px;background:'+(option.selected?'#eee8f8':'transparent');button.addEventListener('click',()=>{select.value=option.value;close();select.dispatchEvent(new Event('input',{bubbles:true}));select.dispatchEvent(new Event('change',{bubbles:true}));});popup.append(button);}
      padding=root.style.paddingBottom;root.style.paddingBottom=(parseFloat(getComputedStyle(root).paddingBottom)||0)+240+'px';root.append(popup);
    };
    document.addEventListener('pointerdown',event=>{
      const select=event.target.closest('select');
      if(select&&!select.disabled&&!select.multiple&&select.size<=1){event.preventDefault();select.focus();open(select);}
      else if(popup&&!popup.contains(event.target))close();
    },true);
    document.addEventListener('keydown',event=>{if(event.key==='Escape')close();if(event.key==='Enter'&&event.target.matches('select:not([multiple])')){event.preventDefault();open(event.target);}},true);
  }
  function notify(message){let node=root.querySelector('[data-qf-error]');if(!node){node=document.createElement('p');node.dataset.qfError='';node.setAttribute('role','alert');root.append(node);}node.textContent=String(message||'');node.hidden=!message;}
  function track(value){const promise=Promise.resolve(value);pending.add(promise);promise.catch(error=>{lastError=error;notify(error.message);}).finally(()=>pending.delete(promise));return promise;}
  function request(method,args=[]){
    if(closed)return Promise.resolve(fail('PAGE_CLOSED','题型页面已关闭'));
    try{args=validateArguments(args);}catch(error){return Promise.resolve(fail('INVALID_ARGUMENT',error.message));}
    const id=String(++sequence);
    return new Promise(resolve=>{
      const timeout=setTimeout(()=>{requests.delete(id);resolve(fail('SDK_TIMEOUT','题型接口请求超时：'+method));},boot.nativePage?60000:30000);
      requests.set(id,{resolve,timeout});
      try{send({kind:'request',id,method,args});}catch(error){clearTimeout(timeout);requests.delete(id);resolve(fail('INVALID_ARGUMENT',error.message));}
    });
  }
  const barriers=new Set(['editor.save','bank.save','bank.addQuestion','bank.duplicateQuestion','bank.deleteQuestion','navigation.goTo','navigation.previous','navigation.next','practice.submit','practice.retry','answer.flush']);
  const reads=new Set(['editor.getData','answer.get','practice.getState','practice.getResult']);
  const remote=(method,tracked=false)=>(...args)=>{
    let key;
    try{args=validateArguments(args);key=method+JSON.stringify(args);}catch(error){return Promise.resolve(fail('INVALID_ARGUMENT',error.message));}
    if(barriers.has(method)&&singleFlights.has(key))return singleFlights.get(key);
    // Reads must observe preceding writes, including after a rejected update. A save still stops on failure.
    const value=barriers.has(method)||(reads.has(method)&&writes.size)?Promise.all([...writes]).then(replies=>
      (barriers.has(method)&&replies.find(reply=>!reply.ok))||request(method,args)):request(method,args);
    if(tracked){writes.add(value);value.finally(()=>writes.delete(value));track(value);}
    if(barriers.has(method)){singleFlights.set(key,value);value.finally(()=>singleFlights.delete(key));}
    return value;
  };
  function own(node){if(!root.contains(node))throw new TypeError('节点必须属于当前题型页面');return node;}
  function on(node,event,listener){own(node).addEventListener(event,listener);return()=>node.removeEventListener(event,listener);}
  async function flush(){await ready;while(pending.size)await Promise.all([...pending]);if(lastError)throw lastError;}
  function configure(kind,patch){
    if(closed)return fail('PAGE_CLOSED','题型页面已关闭');
    try {
      const next=kind==='layout'?configureLayout(layout,patch):configureUi(ui,patch,boot.mode);
      if(kind==='layout')layout=next;else ui=next;
      track(request(kind+'.configure',[patch]).then(reply=>{if(!reply.ok)throw new Error(reply.error.message);if(kind==='layout'){layout=reply.data;layoutState.configuration=clone(layout);}else ui=reply.data;}));
      return ok(next);
    }catch(error){return fail(kind==='layout'?'INVALID_LAYOUT':'INVALID_UI',error.message);}
  }
  const QF=Object.freeze({
    dom:Object.freeze({root,$:selector=>root.querySelector(selector),on}),
    host:Object.freeze({getContext:remote('host.getContext'),subscribe(listener){subscriptions.add(listener);return()=>subscriptions.delete(listener);}}),
    ids:Object.freeze({create(prefix='opt_'){const bytes=new Uint32Array(4);crypto.getRandomValues(bytes);return prefix+Array.from(bytes,n=>n.toString(16).padStart(8,'0')).join('');}}),
    editor:Object.freeze({getData:remote('editor.getData'),update:remote('editor.update',true),save:remote('editor.save')}),
    bank:Object.freeze({getState:remote('bank.getState'),save:remote('bank.save'),addQuestion:remote('bank.addQuestion'),duplicateQuestion:remote('bank.duplicateQuestion'),deleteQuestion:remote('bank.deleteQuestion')}),
    navigation:Object.freeze({getState:remote('navigation.getState'),goTo:remote('navigation.goTo'),previous:remote('navigation.previous'),next:remote('navigation.next')}),
    sources:Object.freeze({list:remote('sources.list'),add:remote('sources.add'),remove:remote('sources.remove'),open:remote('sources.open')}),
    learning:Object.freeze({getMode:remote('learning.getMode'),setMode:remote('learning.setMode'),toggleMode:remote('learning.toggleMode')}),
    whiteboard:Object.freeze({getState:remote('whiteboard.getState'),setTool:remote('whiteboard.setTool'),undo:remote('whiteboard.undo'),redo:remote('whiteboard.redo'),clear:remote('whiteboard.clear'),setAppearance:remote('whiteboard.setAppearance'),setZoom:remote('whiteboard.setZoom'),zoomBy:remote('whiteboard.zoomBy')}),
    practice:Object.freeze({getState:remote('practice.getState'),getQuestion:remote('practice.getQuestion'),getResult:remote('practice.getResult'),submit:remote('practice.submit'),retry:remote('practice.retry')}),
    answer:Object.freeze({get:remote('answer.get'),update:remote('answer.update',true),flush:remote('answer.flush')}),
    content:Object.freeze({
      render(node,content){own(node);const revision=String(++sequence);node.dataset.qfContentRevision=revision;track(request('content.resolve',[content]).then(reply=>{if(!reply.ok)throw new Error(reply.error.message);if(node.dataset.qfContentRevision===revision){node.replaceChildren();renderContent(node,reply.data);}}));return node;},
      mountEditor(node,{value,onChange,formatting=true}){
        own(node);if(boot.mode!=='EDITOR')throw new TypeError('富文本编辑器只能用于编辑模式');
        let content=clone(value||{kind:'TEXT',text:''}),disposed=false,canEdit=false,permissionRevision=0,removeInput,button,removeButton;
        const body=document.createElement('div');body.className='qf-rich-editor-body';node.append(body);
        function repaint(){
          removeInput?.();body.replaceChildren();
          if(content.kind==='TEXT'){
            const input=document.createElement('textarea');input.rows=4;input.value=content.text||'';input.disabled=!canEdit;body.append(input);
            removeInput=on(input,'input',()=>{if(disposed||!canEdit)return;content={kind:'TEXT',text:input.value};track(onChange(clone(content)));});
          }else QF.content.render(body,content);
        }
        async function refreshPermission(){
          const revision=++permissionRevision,reply=await QF.host.getContext();
          if(disposed||closed||revision!==permissionRevision)return;
          canEdit=reply.ok&&reply.data.capabilities.editQuestion;
          const input=body.querySelector('textarea');if(input)input.disabled=!canEdit;if(button)button.disabled=!canEdit;
        }
        if(formatting){
          button=document.createElement('button');button.type='button';button.className='extension-editor-action';button.textContent='编辑格式';button.disabled=true;node.append(button);
          removeButton=on(button,'click',()=>{
            if(disposed||!canEdit)return;button.disabled=true;
            track(request('content.edit',[content]).then(reply=>{
              if(disposed||closed)return;
              if(!reply.ok){notify(reply.error.message);return;}
              if(reply.data){content=clone(reply.data);repaint();return onChange(clone(content));}
            }).finally(()=>{if(!disposed&&!closed)button.disabled=!canEdit;}));
          });
        }
        const unsubscribe=QF.host.subscribe(refreshPermission);
        function destroy(){if(disposed)return;disposed=true;unsubscribe();removeInput?.();removeButton?.();body.dataset.qfContentRevision='disposed';body.remove();button?.remove();disposers.delete(destroy);}
        disposers.add(destroy);repaint();track(refreshPermission());return {getValue:()=>clone(content),destroy};
      }
    }),
    ui:Object.freeze({configure:patch=>configure('ui',patch),getConfiguration:()=>ok(ui),notify,mountControls(node){own(node);node.dataset.qfHostControls='';controls.add(node);measure();},mountActions(node){own(node).dataset.qfActions='';}}),
    layout:Object.freeze({configure:patch=>configure('layout',patch),getConfiguration:()=>ok(layout),getState:()=>ok(layoutState)})
  });
  // Prevent browser forms from navigating the child document.
  document.addEventListener('submit',event=>event.preventDefault());
  document.addEventListener('keydown',event=>{
    if(boot.mode==='EDITOR'&&(event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='s'){
      event.preventDefault();QF.bank.save();
    }
  });
  let measuredHeight=-1,lastControls='';
  function measure(){
    if(closed)return;const height=Math.ceil(root.getBoundingClientRect().height);
    const regions=Array.from(controls).filter(node=>node.isConnected&&!node.hidden).map(node=>{const r=node.getBoundingClientRect();return {x:r.left,y:r.top,width:r.width,height:r.height};});
    const encoded=JSON.stringify(regions);
    if(height!==measuredHeight||encoded!==lastControls){measuredHeight=height;lastControls=encoded;send({kind:'geometry',height,controls:regions});}
  }
  const resize=new ResizeObserver(measure);resize.observe(root);
  const mutations=new MutationObserver(measure);mutations.observe(root,{subtree:true,childList:true,attributes:true,characterData:true});
  if(boot.relayWheel)document.addEventListener('wheel',event=>{event.preventDefault();send({kind:'wheel',x:event.clientX,y:event.clientY,deltaX:event.deltaX,deltaY:event.deltaY,deltaMode:event.deltaMode,ctrlKey:event.ctrlKey});},{passive:false});
  window.addEventListener('message',event=>{
    const message=event.data;if(event.source!==parent||message?.channel!=='qf-type-frame'||message.session!==boot.session)return;
    if(message.kind==='reply'){
      const entry=requests.get(message.id);
      if(entry){clearTimeout(entry.timeout);requests.delete(message.id);if(message.layoutState)layoutState=message.layoutState;
        const reply=message.reply;
        entry.resolve(reply&&typeof reply.ok==='boolean'&&(reply.ok||typeof reply.error?.code==='string')?reply:fail('INVALID_REPLY','宿主返回格式无效'));
      }
      // Let awaiting extension handlers finish their microtasks before retiring this document.
      setTimeout(()=>send({kind:'reply-ack',id:message.id}),0);return;
    }
    if(message.kind==='closing'){
      closed=true;for(const entry of requests.values()){clearTimeout(entry.timeout);entry.resolve(fail('PAGE_CLOSED','题型页面已关闭'));}requests.clear();
      setTimeout(()=>send({kind:'close-ack'}),0);return;
    }
    if(message.kind==='state'){layoutState=message.layoutState||layoutState;interaction=message.interaction;if(interaction!=='INTERACT')document.activeElement?.blur();for(const listener of subscriptions)track(Promise.resolve().then(listener));return;}
    if(message.kind==='blur'){document.activeElement?.blur();return;}
    if(message.kind==='flush'){flush().then(()=>{measure();send({kind:'flushed',id:message.id});},error=>send({kind:'flushed',id:message.id,error:error.message}));return;}
    if(message.kind==='control-click'&&interaction!=='INTERACT'){
      const target=document.elementFromPoint(message.x,message.y);
      if(target&&Array.from(controls).some(node=>node.contains(target)))target.closest('button,input,select')?.click();return;
    }
    if(message.kind==='focus'){const target=message.id===boot.questionId?root:root.querySelector('[data-target-id="'+CSS.escape(message.id)+'"]');target?.scrollIntoView({block:'nearest'});return;}
  });
  window.addEventListener('pagehide',()=>{closed=true;resize.disconnect();mutations.disconnect();for(const dispose of disposers)dispose();subscriptions.clear();for(const entry of requests.values()){clearTimeout(entry.timeout);entry.resolve(fail('PAGE_CLOSED','题型页面已关闭'));}requests.clear();});
  // The package script is executed by a nonce-authorized script in this document only.
  Object.defineProperty(window,'QF',{value:QF,writable:false,configurable:false});
  let reportedFailure=false;
  const reportFailure=error=>{if(closed||reportedFailure)return;reportedFailure=true;send({kind:'failed',error:String(error?.message||error||'题型页面执行失败').slice(0,1024)});};
  window.addEventListener('error',event=>reportFailure(event.error||event.message));
  window.addEventListener('unhandledrejection',event=>{event.preventDefault();reportFailure(event.reason);});
  window.__qfStart=promise=>{ready=track(promise);ready.then(async()=>{try{await flush();measure();root.dataset.qfReady='true';send({kind:'ready'});}catch(error){send({kind:'failed',error:error.message});}},error=>send({kind:'failed',error:error.message}));};
  measure();
}
