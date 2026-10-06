import {createNativeTransport} from './native-transport.js';
import {mountNavigation} from './learning/navigation.js';
import {mountSummary} from './learning/summary.js';
// Trusted read-only host transport. Type frames see only the existing SDK dispatcher.
const transport=createNativeTransport(window.chrome.webview),send=message=>transport.postMessage(message);
let pageState=null,questionId=null,initialized=false;
const permissions=[];
window.pageHost=Object.freeze({
  state(id){return JSON.stringify(id===questionId&&pageState?{ok:true,data:pageState}:{ok:false,error:{code:'STALE_PAGE',message:'历史题目已切换',retryable:false}});},
  request(id,requestId,action,encoded){send({kind:'page-request',questionId:id,id:requestId,action,argument:JSON.parse(encoded)});}
});
window.historyHost=Object.freeze({
  ready:()=>send({kind:'history-ready'}),
  uiPreferences:(id,encoded)=>send({kind:'preferences',questionId:id,value:JSON.parse(encoded)})
});
const navigation=mountNavigation(action=>send({kind:'chrome-action',action,questionId}));
const summary=mountSummary(null);
let changes=Promise.resolve();
transport.addEventListener('message',event=>{
  const message=event.data;
  // Replies and policy updates must not wait behind rendering or frame initialization.
  if(message.kind==='page-reply'){window.qfPageActions.replyFromJson(JSON.stringify(message.reply));return;}
  if(message.kind==='permissions'){if(initialized)window.questionExtensions.updatePermissions(message.value);else permissions.push(message.value);return;}
  if(message.kind==='page-state'){pageState=message.state;questionId=message.questionId;window.historyDraftReplay.refreshInteraction();return;}
  if(message.kind==='chrome'||message.kind==='attempt-chrome'){navigation.update(message);return;}
  changes=changes.then(async()=>{
    switch(message.kind){
      case 'bootstrap':
        for(const bundle of message.packages)await window.questionExtensions.install(bundle);
        initialized=true;for(const value of permissions)window.questionExtensions.updatePermissions(value);permissions.length=0;
        window.historyDraftReplay.bindHost();break;
      case 'load':
        pageState=message.state;questionId=message.questionId;
        window.historyDraftReplay.setLearningMode(message.mode);
        await window.historyDraftReplay.loadHistoryDraft(message.card,message.document);break;
      case 'summary':summary.show(message.summary);window.historyDraftReplay.clear();questionId='__summary';pageState=null;break;
      case 'mode':window.historyDraftReplay.setLearningMode(message.mode);break;
      case 'clear':questionId=null;pageState=null;summary.hide();window.historyDraftReplay.clear();break;
      case 'focus':window.historyDraftReplay.focusTarget(message.targetId);break;
      case 'development':await window.questionExtensions.replaceDevelopment(message.bundle);break;
      default:throw new Error('未知历史页面指令');
    }
    if(message.kind==='load')summary.hide();
    if(message.commandId)send({kind:'command-complete',id:message.commandId,ok:true});
  }).catch(error=>send(message.commandId?{kind:'command-complete',id:message.commandId,ok:false,message:error.message}:{kind:'page-error',message:error.message}));
});
send({kind:'boot'});
