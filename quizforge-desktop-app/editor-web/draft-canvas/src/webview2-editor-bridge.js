import {createNativeTransport} from './native-transport.js';
// Trusted top-level transport; opaque type frames can only use the existing SDK dispatcher.
const transport=createNativeTransport(window.chrome.webview);
const send=message=>transport.postMessage(message);
let sequence=0,initialized=false,closing=false;
const calls=new Map(),permissions=[];
const questionId=()=>window.qfEditorShell?.getState()?.question?.id??'';
function call(method,encoded){
  // Flush hooks may still commit pending edits while the visible page is inert.
  // The Java owner rejects all new calls after the final flush is applied.
  const id=String(++sequence);
  return new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>{calls.delete(id);reject(new Error('编辑接口等待超时'));},15000);
    calls.set(id,{resolve,reject,timer});send({kind:'editor-call',id,method,questionId:questionId(),encoded});
  });
}
const host=Object.freeze({
  viewportHeight:()=>innerHeight,
  changed:encoded=>call('changed',encoded),
  editContent:encoded=>call('editContent',encoded),
  resolveContent:encoded=>call('resolveContent',encoded),
  configureUi:(id,encoded)=>send({kind:'configure-ui',questionId:id,encoded}),
  flushed:(id,draft)=>send({kind:'flushed',id,draft}),
  flushFailed:(id,message)=>send({kind:'flush-failed',id,message}),
  reloadFailed:message=>send({kind:'reload-failed',message}),
  contentHeight:()=>{},
  request:(requestId,encoded)=>send({kind:'bank-request',requestId,encoded})
});
window.editorHost=host;window.extensionEditorHost=host;window.bankEditorHost=host;
let changes=Promise.resolve();
transport.addEventListener('message',event=>{
  const message=event.data;
  if(message.kind==='editor-reply'){
    const pending=calls.get(message.id);if(!pending)return;
    clearTimeout(pending.timer);calls.delete(message.id);
    if(message.reply.ok)pending.resolve(message.reply.data);
    else{const error=new Error(message.reply.error.message);Object.assign(error,message.reply.error);pending.reject(error);}return;
  }
  if(message.kind==='permissions'){
    if(initialized)window.questionExtensions.updatePermissions(message.value);else permissions.push(message.value);return;
  }
  // Replies must be delivered while a flush waits for SDK operations.
  if(message.kind==='shell'&&message.method==='replyFromJson'){window.qfEditorShell.replyFromJson(JSON.stringify(message.value));return;}
  changes=changes.then(async()=>{
    switch(message.kind){
      case 'bootstrap':
        for(const bundle of message.packages)await window.questionExtensions.install(bundle);
        initialized=true;for(const value of permissions)window.questionExtensions.updatePermissions(value);permissions.length=0;
        await window.qfEditorShell.updateFromJson(JSON.stringify(message.state));
        if(message.state.editable){await window.extensionEditorFlush();}
        send({kind:'editor-ready'});break;
      case 'shell':
        if(!['updateFromJson','errorFromJson'].includes(message.method))throw new Error('未知编辑页面指令');
        await window.qfEditorShell[message.method](JSON.stringify(message.value));break;
      case 'flush':window.extensionEditorFlushToHost(message.id);break;
      case 'development':await window.questionExtensions.replaceDevelopment(message.bundle);break;
      case 'closing':closing=message.value;document.querySelector('#qf-editor-shell').inert=closing;break;
      default:throw new Error('未知编辑宿主指令');
    }
  }).catch(error=>send({kind:'page-error',message:error.message}));
});
window.addEventListener('pagehide',()=>{
  for(const pending of calls.values()){clearTimeout(pending.timer);pending.reject(new Error('编辑页面已关闭'));}calls.clear();
});
send({kind:'boot'});
