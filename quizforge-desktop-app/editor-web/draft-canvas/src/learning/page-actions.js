/** Scoped native page operations. No filesystem paths or arbitrary native commands. */
export function createPageActions(getHost) {
  const pending=new Map();let sequence=0,closed=false;
  const unavailable=()=>({ok:false,error:{code:'CAPABILITY_UNAVAILABLE',message:'此宿主未提供该操作',retryable:false}});
  const api={
    getState(questionId){try{const host=getHost();return closed||!host?.state?unavailable():JSON.parse(host.state(questionId));}catch(error){return {ok:false,error:{code:'HOST_FAILED',message:error.message,retryable:false}};}},
    request(questionId,action,argument=null){
      const host=getHost();if(closed||!host?.request)return Promise.resolve(unavailable());
      const id=String(++sequence);
      return new Promise(resolve=>{
        const timeout=setTimeout(()=>{pending.delete(id);resolve({ok:false,error:{code:'HOST_TIMEOUT',message:'宿主操作未响应',retryable:false}});},15000);
        pending.set(id,{resolve,timeout});
        try{host.request(questionId,id,action,JSON.stringify(argument));}
        catch(error){clearTimeout(timeout);pending.delete(id);resolve({ok:false,error:{code:'HOST_FAILED',message:error.message,retryable:false}});}
      });
    },
    replyFromJson(value){const reply=JSON.parse(value),entry=pending.get(reply.id);if(!entry)return;clearTimeout(entry.timeout);pending.delete(reply.id);entry.resolve(reply.reply);},
    destroy(){closed=true;for(const entry of pending.values()){clearTimeout(entry.timeout);entry.resolve({ok:false,error:{code:'PAGE_CLOSED',message:'页面已关闭',retryable:false}});}pending.clear();}
  };
  return Object.freeze(api);
}
