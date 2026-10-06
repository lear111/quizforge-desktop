import {createNativeTransport} from './native-transport.js';
import './style.css';
import './extensions/sdk.js';
import {mountSharedLearningSurface} from './learning/surface.js';
import {practiceChannel} from './bridge/practice.js';
import {mountNavigation} from './learning/navigation.js';
import {mountSummary} from './learning/summary.js';

// This file is a trusted top-level host asset, never part of an extension package.
const transport=createNativeTransport(window.chrome.webview);
const send=message=>transport.postMessage(message);
let nativePageState=null,nativeQuestionId=null;
window.pageHost=Object.freeze({
  state(questionId){return JSON.stringify(questionId===nativeQuestionId&&nativePageState?{ok:true,data:nativePageState}:{ok:false,error:{code:'CAPABILITY_UNAVAILABLE',message:'此宿主未提供页面操作',retryable:false}});},
  request(questionId,id,action,encoded){send({kind:'page-request',questionId,id,action,argument:JSON.parse(encoded)});}
});
const pendingFlushes=new Map();let sequence=0,active=false;
let initialized=false;const permissionUpdates=[];
let editor=null,editorPanel=null,editorQuestion=null;
function showEditor(){
  if(editorPanel)return;
  editorQuestion=structuredClone(window.__qfVerificationTemplate);
  editorPanel=document.createElement('section');editorPanel.style.cssText='position:fixed;inset:0;overflow:auto;background:#f5f4f7;padding:24px;z-index:1000';
  const close=document.createElement('button');close.textContent='返回练习（编辑验证不写入题库）';close.onclick=hideEditor;
  const root=document.createElement('main');root.style.cssText='max-width:760px;margin:20px auto;background:white;padding:24px;border-radius:14px';
  editorPanel.append(close,root);document.body.append(editorPanel);
  window.editorHost={changed:encoded=>{editorQuestion=JSON.parse(encoded);}};
  editor=window.questionExtensions.mountEditor('SINGLE_CHOICE',root,editorQuestion);
}
function hideEditor(){editor?.destroy();editor=null;editorPanel?.remove();editorPanel=null;delete window.editorHost;}
// Trusted native verification only. Opaque extension frames cannot access this object.
window.webview2Verification=Object.freeze({showEditor,hideEditor,editorQuestion:()=>editorQuestion});
window.practiceHost=Object.freeze({
  onEvent:encoded=>send({kind:'practice-event',event:JSON.parse(encoded)}),
  ready:()=>{},
  uiPreferences:(questionId,encoded)=>send({kind:'preferences',questionId,value:JSON.parse(encoded)}),
  onFlushCompleted(id,success,message){const entry=pendingFlushes.get(id);if(!entry)return;pendingFlushes.delete(id);success?entry.resolve():entry.reject(new Error(message));}
});
const surface=mountSharedLearningSurface(document.querySelector('#question-card'),practiceChannel(()=>window.practiceHost));
window.sharedPractice=surface.practice;window.draftCanvas=surface.canvas;
let showingSummary=false;
const chromeAction=action=>send({kind:'chrome-action',action,questionId:showingSummary?'__summary':surface.practice.getViewState()?.question.sessionQuestionId});
const navigation=mountNavigation(chromeAction),summary=mountSummary(()=>chromeAction('restart'));
let summaryBusy=false,summaryAttemptBusy=false;
function flush(){const id=String(++sequence);return new Promise((resolve,reject)=>{
  const timer=setTimeout(()=>{pendingFlushes.delete(id);reject(new Error('保存等待超时'));},10000);
  pendingFlushes.set(id,{resolve(){clearTimeout(timer);resolve();},reject(error){clearTimeout(timer);reject(error);}});
  surface.practice.flushToHost(id);
});}
let changes=Promise.resolve();
transport.addEventListener('message',event=>{
  const message=event.data;
  // Data, never arbitrary scripts. Commands are only sent by the native top-level host.
  if(message.kind==='response'){surface.practice.applyResponse(JSON.stringify(message.response));return;}
  if(message.kind==='page-state'){nativePageState=message.state;nativeQuestionId=message.questionId;surface.practice.refreshInteraction();return;}
  if(message.kind==='page-reply'){window.qfPageActions.replyFromJson(JSON.stringify(message.reply));return;}
  if(message.kind==='permissions'){if(!initialized)permissionUpdates.push(message.value);else window.questionExtensions.updatePermissions(message.value);return;}
  if(message.kind==='chrome'||message.kind==='attempt-chrome'){
    navigation.update(message);
    if(message.kind==='chrome')summaryBusy=Boolean(message.busy);else summaryAttemptBusy=Boolean(message.busy);
    summary.setBusy(summaryBusy||summaryAttemptBusy);return;
  }
  changes=changes.then(async()=>{
    switch(message.kind){
      case 'bootstrap':
        window.__qfVerificationTemplate=message.packages.flatMap(bundle=>bundle.assets).find(asset=>asset.typeId==='SINGLE_CHOICE')?.defaultQuestion;
        for(const bundle of message.packages)await window.questionExtensions.install(bundle);
        initialized=true;for(const value of permissionUpdates)window.questionExtensions.updatePermissions(value);permissionUpdates.length=0;
        surface.practice.loadPractice(JSON.stringify(message.viewModel));
        surface.practice.restoreDraft(message.draft);surface.practice.setLearningMode('PRACTICE');active=true;break;
      case 'replace':
        surface.practice.replaceCurrent({...message.draft,viewModel:message.viewModel});active=true;break;
      case 'summary':
        active=false;showingSummary=true;surface.practice.suspendCurrent();summary.show(message.summary);break;
      case 'flush':await flush();return;
      case 'suspend':surface.practice.suspendCurrent();active=false;return;
      case 'focus':surface.practice.focusTarget(message.targetId);return;
      case 'development':await window.questionExtensions.replaceDevelopment(message.bundle);return;
      case 'navigate':
        if(!active)throw new Error('题目尚未就绪');
        await flush();surface.practice.suspendCurrent();active=false;
        send({kind:'navigate-ready',index:message.index});return;
      case 'mode':
        // The Java owner already completed the save barrier before changing modes.
        surface.practice.setLearningMode(message.mode);send({kind:'mode-ready',mode:message.mode});return;
      case 'resume':surface.practice.setLearningMode(message.mode);active=true;break;
      case 'close':
        hideEditor();if(active)await flush();send({kind:'close-ready'});return;
      default: throw new Error('未知宿主指令');
    }
    // Real frame readiness, rather than merely having received the model.
    await surface.practice.whenRendered();
    if(message.kind==='replace'||message.kind==='resume'){showingSummary=false;summary.hide();}
    send({kind:'rendered',index:surface.practice.getViewState().question.index});
  }).then(()=>{if(message.commandId)send({kind:'command-complete',id:message.commandId,ok:true});})
    .catch(error=>{if(message.commandId)send({kind:'command-complete',id:message.commandId,ok:false,message:error.message});else send({kind:'page-error',message:error.message});});
});
send({kind:'boot'});
