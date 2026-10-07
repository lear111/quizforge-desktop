// Package-local helpers. All application communication uses the three public entrances.
export async function connectPage(QF) {
  let context,question,answer={},acceptedQuestion,acceptedAnswer={},writes=0;
  const listeners=new Set(),copy=v=>v==null?v:JSON.parse(JSON.stringify(v)),ok=data=>Promise.resolve({ok:true,data:copy(data)});
  const api={
    question:()=>ok(question),
    answer:()=>ok(answer),
    result:()=>ok(context.attempt?.result||null),
    state:()=>ok({...context.navigation,sources:context.sources,types:context.types,editable:context.permissions.manageQuestions,learningMode:context.learningMode.toUpperCase()}),
    practiceState:()=>ok({...context.navigation,type:context.question.type,state:context.attempt?.status.toUpperCase(),maxScore:context.question.maxScore,result:context.attempt?.result}),
    host:()=>ok({mode:context.mode.toUpperCase(),capabilities:{...context.permissions,editAnswer:context.permissions.writeAnswer},permissions:{granted:context.grantedPermissions}}),
    subscribe(fn){listeners.add(fn);return()=>listeners.delete(fn);},
    async edit(patch){question={...question,...copy(patch)};const candidate=copy(question);writes++;try{const reply=await QF.save({purpose:'editDraft',data:{questionData:candidate}});if(reply.ok)acceptedQuestion=candidate;else if(writes===1)question=copy(acceptedQuestion);return reply.ok?ok(question):reply;}finally{writes--; }},
    async write(value){answer=copy(value);const candidate=copy(answer);writes++;try{const reply=await QF.save({purpose:'draft',data:{answer:candidate}});if(reply.ok)acceptedAnswer=candidate;else if(writes===1)answer=copy(acceptedAnswer);return reply;}finally{writes--; }},
    commit:()=>QF.save({purpose:'edit',data:{questionData:question}}),
    submit:()=>QF.save({purpose:'submit',data:{answer}}),
    action:(action,params={})=>QF.requestAction({action,params}),
    get context(){return context;}
  };
  await QF.page.register({
    async onLoad(value){context=value;if(!writes){question=acceptedQuestion=copy(value.question.data);answer=acceptedAnswer=copy(value.attempt?.answer||{});}for(const fn of listeners)await fn();},
    // Changes are sent immediately; the SDK waits for accepted saves before navigation.
    onBeforeLeave:()=>({ok:true,data:{pendingSave:null}}),
    onDispose:()=>listeners.clear()
  });
  return api;
}
