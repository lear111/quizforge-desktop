/** Development-only rules runner. Package rules never execute in the host page. */
export function isolatedRules(bundle){
  if(window.nativeRulesHost)return nativeRules(window.nativeRulesHost);
  let frame=null,session=null,ready=null,seq=0,closed=false;const requests=new Map();
  function receive(event){const m=event.data;if(closed||event.source!==frame?.contentWindow||m?.channel!=='qf-rules-frame'||m.session!==session)return;const entry=requests.get(m.id);if(entry){clearTimeout(entry.timeout);requests.delete(m.id);m.error?entry.reject(new Error(m.error)):entry.resolve(m.value);}}
  function request(method,args){const id=String(++seq);return new Promise((resolve,reject)=>{const timeout=setTimeout(()=>{requests.delete(id);reject(new Error('题型规则执行超时'));},15000);requests.set(id,{resolve,reject,timeout});frame.contentWindow.postMessage({channel:'qf-rules-frame',session,id,method,args},'*');});}
  function start(){if(ready)return ready;
    const bytes=new Uint32Array(4);crypto.getRandomValues(bytes);session=Array.from(bytes,n=>n.toString(16)).join('-');
    frame=document.createElement('iframe');frame.hidden=true;frame.setAttribute('sandbox','allow-scripts');frame.title='题型测试规则';document.body.append(frame);window.addEventListener('message',receive);
    const init=`(${globalThis.QuestionRules.runtimeSource})(window);const registry=window.QuestionRules;const session=${JSON.stringify(session)};`+
      bundle.assets.map(asset=>`registry.installDefaultQuestion(${JSON.stringify(asset.typeId)},${JSON.stringify(asset.defaultQuestion)});((QF)=>{\n${asset.rulesSource}\n})({defineQuestionType:d=>registry.defineQuestionType(d)});`).join('\n')+
      `window.addEventListener('message',event=>{const m=event.data;if(event.source!==parent||m?.channel!=='qf-rules-frame'||m.session!==session)return;let value,error;try{if(m.method==='invoke')value=registry.invoke(...m.args);else if(m.method==='ready')value=true;else throw Error('未知规则接口');}catch(e){error=e.message;}parent.postMessage({channel:'qf-rules-frame',session,id:m.id,value,error},'*');});`;
    frame.srcdoc=`<!doctype html><meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'nonce-qf-isolated-page-v1'; connect-src 'none'; frame-src 'none'; base-uri 'none'; form-action 'none'"><meta http-equiv="Content-Security-Policy" content="script-src 'unsafe-inline'"><script nonce="qf-isolated-page-v1">${init.replace(/<\/script/gi,'<\\/script')}</script>`;
    ready=new Promise((resolve,reject)=>{frame.addEventListener('load',()=>request('ready',[]).then(resolve,reject),{once:true});});return ready;
  }
  return Object.freeze({async invoke(...args){if(closed)throw Error('规则页面已关闭');await start();return request('invoke',args);},destroy(){closed=true;window.removeEventListener('message',receive);frame?.remove();for(const entry of requests.values()){clearTimeout(entry.timeout);entry.reject(new Error('规则页面已关闭'));}requests.clear();}});
}

/** Desktop development uses an async bridge to a worker process, never an iframe grading loop. */
export function nativeRules(host){
  let closed=false,seq=0;const requests=new Map();
  const endpoint=Object.freeze({replyFromJson(encoded){
    if(closed)return;const reply=JSON.parse(encoded),entry=requests.get(reply.id);if(!entry)return;
    clearTimeout(entry.timeout);requests.delete(reply.id);
    if(reply.error){const error=new Error(reply.error.message);error.code=reply.error.code;entry.reject(error);}else entry.resolve(reply.value);
  }});
  window.qfNativeRules=endpoint;
  return Object.freeze({invoke(type,operation,input){
    if(closed)return Promise.reject(new Error('开发规则页面已关闭'));
    return new Promise((resolve,reject)=>{
      const id=String(++seq),timeout=setTimeout(()=>{requests.delete(id);reject(new Error('开发规则请求超时，请读取状态后重试'));},60000);
      requests.set(id,{resolve,reject,timeout});
      try{host.request(id,type,operation,typeof input==='string'?input:JSON.stringify(input));}
      catch(error){clearTimeout(timeout);requests.delete(id);reject(error);}
    });
  },destroy(){
    closed=true;for(const entry of requests.values()){clearTimeout(entry.timeout);entry.reject(new Error('开发规则页面已关闭'));}requests.clear();
    if(window.qfNativeRules===endpoint)delete window.qfNativeRules;
  }});
}
