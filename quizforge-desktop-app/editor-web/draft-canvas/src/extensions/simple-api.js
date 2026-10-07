/** The small public API dispatches through existing, permission-checked host capabilities. */
export function createSimpleApi({contextId, getContext, invoke, isCurrent=()=>true}) {
  let revision=0, tail=Promise.resolve();
  const receipts=new Map();
  const failure=(code,message)=>({ok:false,error:{code,message}});
  const own=(value,keys)=>Object.keys(value).every(key=>keys.includes(key));
  async function load(){if(!isCurrent())return failure('CONTEXT_EXPIRED','题目已切换或页面已关闭');return {ok:true,data:{...await getContext(),contextId,revision}};}
  function check(request){
    if(!request||typeof request!=='object'||Array.isArray(request))return failure('INVALID_REQUEST','请求必须是对象');
    if(!isCurrent()||request.contextId!==contextId)return failure('CONTEXT_EXPIRED','题目已切换或页面已关闭');
    if(!Number.isSafeInteger(request.revision)||request.revision!==revision)return failure('REVISION_CONFLICT','页面数据版本已改变');
    if(typeof request.requestId!=='string'||!request.requestId||request.requestId.length>160)return failure('INVALID_REQUEST','缺少有效请求身份');
  }
  function write(kind,request,execute){
    const key=request?.requestId,signature=JSON.stringify({kind,request});
    if(receipts.has(key)){const prior=receipts.get(key);return prior.signature===signature?prior.promise:Promise.resolve(failure('INVALID_REQUEST','请求身份不能重复用于不同内容'));}
    // Evicted successful requests have older revisions, so a replay cannot write again.
    if(receipts.size>=1024){for(const [id,entry] of receipts){if(entry.done){receipts.delete(id);break;}}if(receipts.size>=1024)return Promise.resolve(failure('BUSY','待处理请求过多'));}
    const promise=tail.then(async()=>{
      const invalid=check(request);if(invalid)return invalid;
      const reply=await execute(request);if(!reply.ok)return reply;
      revision++;return {ok:true,data:{...reply.data,contextId,revision}};
    }).catch(error=>failure(error.code||'HOST_ACTION_FAILED',error.message));
    tail=promise.then(()=>{});const entry={signature,promise,done:false};receipts.set(key,entry);promise.finally(()=>{entry.done=true;});return promise;
  }
  async function call(name,...args){return invoke(name,args);}
  function save(request){return write('save',request,async r=>{
    if(!own(r,['requestId','contextId','revision','purpose','data'])||!r.data||typeof r.data!=='object'||Array.isArray(r.data))return failure('INVALID_REQUEST','保存请求格式不合法');
    if(r.purpose==='edit'||r.purpose==='editDraft'){
      if(!own(r.data,['questionData']))return failure('INVALID_REQUEST','编辑保存只接受题目数据');
      const update=await call('editor.update',r.data.questionData);if(!update.ok)return update;
      if(r.purpose==='edit'){const saved=await call('bank.save');if(!saved.ok)return saved;}
      return {ok:true,data:{status:r.purpose==='edit'?'saved':'editing'}};
    }
    if(r.purpose==='draft'||r.purpose==='submit'){
      if(!own(r.data,['answer']))return failure('INVALID_REQUEST','当前版本由规则评分，不接受页面直接注入分数');
      const update=await call('answer.update',r.data.answer);if(!update.ok)return update;
      if(r.purpose==='submit'){const submitted=await call('practice.submit');if(!submitted.ok)return submitted;return submitted;}
      return {ok:true,data:{status:'draft'}};
    }
    return failure('INVALID_REQUEST','未知保存用途');
  });}
  function action(request){return write('action',request,async r=>{
    if(!own(r,['requestId','contextId','revision','action','params']))return failure('INVALID_REQUEST','操作请求格式不合法');
    const p=r.params||{};if(typeof p!=='object'||Array.isArray(p))return failure('INVALID_REQUEST','操作参数必须是对象');
    const context=await getContext(),nav=context.navigation;
    switch(r.action){
      case 'addQuestion':if(!own(p,['type','position'])||p.position&&p.position!=='afterCurrent')break;return call('page.add',{type:p.type,position:'afterCurrent'});
      case 'deleteQuestion':if(Object.keys(p).length)break;return call('bank.deleteQuestion');
      case 'duplicateQuestion':if(Object.keys(p).length)break;return call('bank.duplicateQuestion');
      case 'saveBank':if(Object.keys(p).length)break;return call('bank.save');
      case 'moveQuestion':if(!own(p,['beforeQuestionId'])||p.beforeQuestionId!==null&&typeof p.beforeQuestionId!=='string')break;return call('page.move',p);
      case 'goToQuestion':{
        if(!own(p,['direction','questionId','targetId']))break;
        if(p.targetId!=null)return failure('ACTION_UNAVAILABLE','当前版本尚未开放小题定位');
        if(p.direction&&p.questionId)break;
        if(p.direction&&!['previous','next'].includes(p.direction))break;
        if(p.direction==='previous')return call('navigation.previous');
        if(p.direction==='next')return call('navigation.next');
        const target=nav.questions?.find(q=>q.id===p.questionId);if(!target)return failure('ACTION_UNAVAILABLE','目标题目不存在');
        return call('navigation.goTo',target.index);
      }
      case 'setLearningMode':if(!own(p,['mode'])||!['practice','draft'].includes(p.mode))break;return call('learning.setMode',p.mode.toUpperCase());
      case 'retry':if(Object.keys(p).length)break;return call('practice.retry');
      case 'addSource':if(!own(p,['link']))break;return call('sources.add',p.link);
      case 'removeSource':if(!own(p,['index']))break;return call('sources.remove',p.index);
      case 'openSource':if(!own(p,['index']))break;return call('sources.open',p.index);
      case 'goToAttempt':if(!own(p,['direction'])||!['previous','next','current'].includes(p.direction))break;return call('page.attempt',p.direction);
      case 'returnToCurrentAttempt':if(Object.keys(p).length)break;return call('page.attempt','current');
      default:return failure('ACTION_UNAVAILABLE','此宿主尚未提供该操作');
    }
    return failure('INVALID_REQUEST','操作参数不合法');
  });}
  return Object.freeze({load,save,action,invalidate(){revision++;}});
}
