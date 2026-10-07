/** Serialized into the opaque sandbox: no trusted closures or native objects. */
export function createSimplePageClient({request,track,configure,boot,notify,validate=value=>value}) {
  let context=null,hooks=null,tail=Promise.resolve(),loading=null,closed=false,counter=0,preparing=null,acting=false,reloadPending=false,barrierPending=false,writeFailure=null;
  const copy=value=>JSON.parse(JSON.stringify(value));
  const fail=(code,message)=>({ok:false,error:{code,message}});
  async function reload(){
    if(closed||!hooks)return;
    if(loading){reloadPending=true;return loading;}
    loading=(async()=>{
      await tail;
      const reply=await request('page.load');if(!reply.ok)throw new Error(reply.error.message);
      if(closed)return;
      context=reply.data;
      const result=await hooks.onLoad(copy(context));
      if(result?.ok===false)throw new Error(result.error?.message||'题目数据无法渲染');
    })().finally(()=>{loading=null;if(reloadPending&&!closed){reloadPending=false;reload().catch(e=>notify(e.message));}});
    return loading;
  }
  function send(method,input,barrier=false){
    if(closed)return Promise.resolve(fail('CONTEXT_EXPIRED','页面已关闭'));
    let payload;
    try{payload=copy(validate(input));}catch(error){return Promise.resolve(fail('INVALID_ARGUMENT',error.message));}
    const operation=tail.then(async()=>{
      if(!context)return fail('CONTEXT_EXPIRED','请先注册页面加载入口');
      const supplied=payload.requestId!=null;
      const r={...payload,requestId:payload.requestId||boot.session+':'+(++counter),contextId:payload.contextId||context.contextId,revision:supplied?payload.revision:context.revision};
      const reply=await request(method,[r]);
      if(method==='page.save'&&!barrier)writeFailure=reply.ok?null:reply.error;
      if(reply.ok&&context?.contextId===reply.data.contextId){context.revision=reply.data.revision;if(method==='page.save'&&r.purpose==='draft'&&context.attempt)context.attempt.answer=copy(r.data.answer);}
      return reply;
    });
    // Commands that ask the native host to flush this page must not wait for themselves.
    if(method==='page.save'&&!barrier){tail=operation.then(()=>{},()=>{});return track(operation);}
    return operation;
  }
  async function prepare(){
    if(acting)return;
    if(preparing)return preparing;
    preparing=(async()=>{
      await tail;
      const result=await hooks?.onBeforeLeave?.({reason:'navigation'});
      if(result?.ok===false)throw Object.assign(new Error(result.error.message),{code:result.error.code});
      const pending=result?.data?.pendingSave;
      if(pending){if(!['draft','editDraft'].includes(pending.purpose))throw new Error('离开页面只允许保存草稿');const reply=await send('page.save',pending);if(!reply.ok)throw Object.assign(new Error(reply.error.message),{code:reply.error.code});}
      if(writeFailure)throw Object.assign(new Error(writeFailure.message),{code:writeFailure.code});
    })().finally(()=>{preparing=null;});return preparing;
  }
  return Object.freeze({
    async register(value){
      if(hooks)throw new TypeError('页面只能注册一次');
      if(!value||typeof value.onLoad!=='function')throw new TypeError('必须声明 onLoad');
      for(const name of ['onBeforeLeave','onDispose'])if(value[name]!==undefined&&typeof value[name]!=='function')throw new TypeError(name+' 必须是函数');
      hooks=Object.freeze({onLoad:value.onLoad,onBeforeLeave:value.onBeforeLeave,onDispose:value.onDispose});await reload();return copy(context);
    },
    async save(input){
      if(!['edit','submit'].includes(input?.purpose))return acting?fail('BUSY','操作正在完成'):send('page.save',input);
      if(barrierPending)return fail('BUSY','操作正在完成');
      barrierPending=true;try{await prepare();acting=true;return await send('page.save',input,true);}catch(error){return fail(error.code||'BEFORE_LEAVE_FAILED',error.message);}finally{acting=false;barrierPending=false;}
    },
    async requestAction(input){
      if(barrierPending)return fail('BUSY','操作正在完成');
      barrierPending=true;try{await prepare();acting=true;return await send('page.action',input);}catch(error){return fail(error.code||'BEFORE_LEAVE_FAILED',error.message);}finally{acting=false;barrierPending=false;}
    },
    configure(options){
      if(!options||typeof options!=='object'||Array.isArray(options))return fail('INVALID_REQUEST','页面配置必须是对象');
      if(Object.keys(options).some(k=>!['useDraft','card','initialLayout'].includes(k)))return fail('INVALID_REQUEST','未知页面配置');
      for(const field of ['useDraft','card'])if(options[field]!=null&&typeof options[field]!=='boolean')return fail('INVALID_REQUEST','页面开关必须是布尔值');
      let result={ok:true,data:null};
      if(options.useDraft!=null){result=configure('ui',{draftToggle:options.useDraft,draftToolbar:options.useDraft,draftZoom:options.useDraft});if(!result.ok)return result;}
      if(options.card!=null){result=configure('ui',{card:options.card});if(!result.ok)return result;}
      if(options.initialLayout){result=configure('layout',options.initialLayout);if(!result.ok)return result;}
      return result;
    },
    reload,prepare,
    async dispose(){closed=true;await hooks?.onDispose?.();hooks=null;context=null;}
  });
}
