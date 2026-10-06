import {isolatedContentSource,renderContent} from '../shared/renderer/content.js';
import {isolatedUiSource,configureUi} from '../shared/ui/preferences.js';
import {isolatedLayoutSource,configureLayout} from '../shared/ui/layout.js';
import {startFrameClient} from './frame-client.js';
import {validateArguments} from './protocol.js';
import {nativePage} from './native-page.js';

const nonce='qf-isolated-page-v1';
const escapeScript=source=>source.replace(/<\/script/gi,'<\\/script');
export function frameDocument(html,styles,source,boot){
  const policy=`default-src 'none'; script-src 'nonce-${nonce}'; style-src 'unsafe-inline'; img-src data:; connect-src 'none'; font-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'`;
  // Intersect with inline-only script policy: a valid nonce alone also authorizes external scripts.
  const helpers=isolatedContentSource()+'\n'+isolatedUiSource()+'\n'+isolatedLayoutSource()+'\n'+validateArguments.toString();
  const init=helpers+'\n('+startFrameClient.toString()+')('+JSON.stringify(boot).replace(/</g,'\\u003c')+','+renderContent.name+','+configureLayout.name+','+configureUi.name+','+validateArguments.name+');';
  return `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="${policy}"><meta http-equiv="Content-Security-Policy" content="script-src 'unsafe-inline'"><style>html,body{margin:0;padding:0;overflow:hidden}body,.qf-extension-page{display:flow-root}*{box-sizing:border-box}[hidden]{display:none!important}.shared-content{white-space:pre-wrap;overflow-wrap:anywhere}.document-content{white-space:normal}.document-content img{max-width:100%;height:auto}.document-content table{border-collapse:collapse}.document-content td{border:1px solid #ded8e8;padding:6px}${styles.replace(/<\/style/gi,'<\\/style')}</style><body><section class="qf-extension-page">${html}</section><script nonce="${nonce}">${escapeScript(init)}</script><script nonce="${nonce}">window.__qfStart((async(QF)=>{\n${escapeScript(source)}\n})(window.QF));</script></body></html>`;
}

/** Only this host-side dispatcher owns capability closures and native bridges. */
export function connectFrame(page,source,{html,styles,boot,invoke,onReady,onFailure,onGeometry,getState,interaction}){
  const documentSource=frameDocument(html,styles,source,{...boot,nativePage:Boolean(window.nativePagesHost)});
  let native;
  const frame=window.nativePagesHost?(native=nativePage(boot,documentSource,event=>receive(event),error=>failFrame(error))).frame:document.createElement('iframe');frame.className='qf-type-frame';frame.title=boot.mode==='EDITOR'?'题型编辑页面':'题型练习页面';
  frame.setAttribute('sandbox','allow-scripts');frame.setAttribute('referrerpolicy','no-referrer');
  frame.style.cssText='display:block;position:relative;overflow:hidden;width:100%;height:1px;border:0;';
  page.style.cssText='position:relative;min-width:0;';page.append(frame);
  let closed=false,retiring=false,loaded=false,seq=0,lastRequest=0,writeQueue=Promise.resolve(),retirement,finishRetirement,retirementTimer;
  const waiting=new Map(),inFlight=new Map(),regions=[];
  const send=message=>{if(!closed)frame.contentWindow?.postMessage({...message,channel:'qf-type-frame',session:boot.session},'*');};
  const state=()=>{if(retiring||closed)return;const value=getState();send({kind:'state',...value});controls();};
  function controls(){native?.state(interaction());for(const region of regions)region.style.display=interaction()==='INTERACT'?'none':'block';}
  function setGeometry(message){
    if(!Number.isFinite(message.height)||message.height<0||message.height>200000)return;
    const height=Math.max(1,message.height);frame.style.height=height+'px';
    regions.splice(0).forEach(node=>node.remove());
    for(const rect of Array.isArray(message.controls)?message.controls.slice(0,32):[]){
      if(![rect.x,rect.y,rect.width,rect.height].every(Number.isFinite)||rect.width<=0||rect.height<=0)continue;
      const x=Math.max(0,rect.x),y=Math.max(0,rect.y),w=Math.min(rect.width,frame.clientWidth-x),h=Math.min(rect.height,height-y);if(w<=0||h<=0)continue;
      const overlay=document.createElement('div');overlay.dataset.qfHostControls='';overlay.style.cssText=`position:absolute;left:${x}px;top:${y}px;width:${w}px;height:${h}px;pointer-events:auto;`;
      overlay.addEventListener('pointerdown',event=>event.stopPropagation());overlay.addEventListener('click',event=>{event.stopPropagation();const r=overlay.getBoundingClientRect();send({kind:'control-click',x:x+(event.clientX-r.left)*w/r.width,y:y+(event.clientY-r.top)*h/r.height});});page.append(overlay);regions.push(overlay);
    }
    controls();onGeometry?.();
  }
  async function receive(event){
    const m=event.data;if(closed||event.source!==frame.contentWindow||m?.channel!=='qf-type-frame'||m.session!==boot.session)return;
    if(m.kind==='request'){
      if(typeof m.id!=='string'||!/^\d+$/.test(m.id)||!Number.isSafeInteger(Number(m.id))||Number(m.id)<=lastRequest)return;
      lastRequest=Number(m.id);
      if(retiring){send({kind:'reply',id:m.id,reply:failure('PAGE_CLOSED','题型页面已关闭')});return;}
      let args;
      try{if(typeof m.method!=='string')throw new TypeError('接口名称无效');args=validateArguments(m.args);}
      catch(error){send({kind:'reply',id:m.id,reply:failure('INVALID_ARGUMENT',error.message)});return;}
      inFlight.set(m.id,false);
      const execute=()=>retiring||closed?failure('PAGE_CLOSED','题型页面已关闭'):invoke(m.method,args);
      let reply;
      try{
        if(['editor.update','answer.update'].includes(m.method)){const operation=writeQueue.then(execute);writeQueue=operation.catch(()=>{});reply=await operation;}
        else reply=await execute();
      }catch(error){reply=failure('SDK_FAILED',error.message);}
      inFlight.set(m.id,true);send({kind:'reply',id:m.id,reply,layoutState:getState().layoutState});return;
    }
    if(m.kind==='reply-ack'){if(inFlight.get(m.id)===true)inFlight.delete(m.id);if(retiring&&!inFlight.size)closeFrame();return;}
    if(m.kind==='close-ack'&&retiring){closeFrame();return;}
    if(retiring)return;
    if(m.kind==='geometry'){setGeometry(m);return;}
    if(m.kind==='ready'){if(!loaded){loaded=true;clearTimeout(startup);onReady();state();}return;}
    if(m.kind==='failed'){failFrame(new Error(String(m.error||'题型页面加载失败').slice(0,1024)));return;}
    if(m.kind==='flushed'){const entry=waiting.get(m.id);if(entry){clearTimeout(entry.timeout);waiting.delete(m.id);m.error?entry.reject(new Error(m.error)):entry.resolve();}return;}
    if(m.kind==='wheel'&&boot.relayWheel&&[m.x,m.y,m.deltaX,m.deltaY].every(Number.isFinite)){
      const r=frame.getBoundingClientRect(),scale=frame.clientWidth?r.width/frame.clientWidth:1;
      page.dispatchEvent(new WheelEvent('wheel',{bubbles:true,cancelable:true,clientX:r.left+m.x*scale,clientY:r.top+m.y*scale,deltaX:m.deltaX,deltaY:m.deltaY,deltaMode:[0,1,2].includes(m.deltaMode)?m.deltaMode:0,ctrlKey:m.ctrlKey===true}));
    }
  }
  function failure(code,message){return {ok:false,error:{code,message,retryable:false}};}
  function failFrame(error){
    if(closed)return;
    // Revoke the page before rendering recovery UI. A failed page cannot keep sending writes.
    closeFrame();onFailure(error);
  }
  function closeFrame(){
    if(closed)return;closed=true;clearTimeout(startup);clearTimeout(retirementTimer);window.removeEventListener('message',receive);
    for(const entry of waiting.values()){clearTimeout(entry.timeout);entry.reject(new Error('题型页面已关闭'));}waiting.clear();
    regions.splice(0).forEach(node=>node.remove());native?.destroy();frame.remove();finishRetirement?.();
  }
  window.addEventListener('message',receive);
  const startup=setTimeout(()=>{if(!loaded&&!closed)failFrame(new Error('隔离题型页面加载超时'));},native?60000:15000);
  if(native)native.start();else frame.srcdoc=documentSource;
  return {
    state,
    flush(){if(closed)return Promise.reject(new Error('题型页面已关闭'));const id=String(++seq);return new Promise((resolve,reject)=>{const timeout=setTimeout(()=>{waiting.delete(id);reject(new Error('题型页面刷新超时'));},15000);waiting.set(id,{resolve,reject,timeout});send({kind:'flush',id});});},
    focus(id){send({kind:'focus',id});return page;},
    destroy(){
      if(retirement)return retirement;
      if(closed)return Promise.resolve();
      retiring=true;page.classList.add('qf-frame-retiring');
      retirement=new Promise(resolve=>{finishRetirement=resolve;});
      clearTimeout(startup);
      if(!inFlight.size){closeFrame();return retirement;}
      // Accepted operations retain only their reply channel; new capabilities are revoked immediately.
      retirementTimer=setTimeout(()=>{
        send({kind:'closing'});
        retirementTimer=setTimeout(closeFrame,1000);
      },15000);
      return retirement;
    }
  };
}
