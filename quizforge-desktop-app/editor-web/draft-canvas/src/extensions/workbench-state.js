const clone=value=>JSON.parse(JSON.stringify(value));
const tabs=new Set(['editor','practice','result','history','preview']);
/** Workbench uses the same answer DTO as installed HTML pages. */
export function normalizeWorkbenchAnswer(compiled,state,intent={}) {return clone(intent.answer||{});}
export async function invokeWorkbenchRule(compiled,type,operation,input){return JSON.parse(await compiled.rules.invoke(type,operation,JSON.stringify(input)));}
export async function prepareWorkbenchState(compiled,previous,options={}){
  const old=typeof previous==='string'?JSON.parse(previous):previous;
  const type=options.type||old?.type||compiled.manifest.types[0]?.id;
  const sameType=old?.type===type;
  if(!compiled.manifest.types.some(t=>t.id===type))throw new TypeError('Workbench type is not in the extension manifest');
  let question;
  if(old?.type===type&&old.question)question=clone(old.question);
  else question=await invokeWorkbenchRule(compiled,type,'createDraft',{ids:{question:'q_workbench',item:'item_workbench',options:['opt_workbench_a','opt_workbench_b','opt_workbench_c','opt_workbench_d']}});
  const validation=await invokeWorkbenchRule(compiled,type,'validate',{question});
  if(validation.errors?.length)throw new TypeError(`题目数据与扩展不匹配：${validation.errors.join('; ')}`);
  const answer=options.resetAnswers?{}:clone(old?.type===type?old.answer||{}:{});
  const checked=await invokeWorkbenchRule(compiled,type,'validateAnswer',{question,answer});
  if(checked.errors?.length)throw new TypeError(`作答数据与扩展不匹配：${checked.errors.join('; ')}`);
  return {schemaVersion:1,type,question,answer,result:options.resetAnswers||!sameType?null:clone(old?.result||null),
    history:options.resetAnswers||!sameType?[]:clone(old?.history||[]),tab:tabs.has(old?.tab)?old.tab:'editor',
    revision:options.revision??old?.revision??'',notice:options.resetAnswers?'规则已更新，测试答案和结果已清空；题目草稿已保留。':''};
}
export async function submitWorkbenchAnswer(compiled,state){
  const snapshot=await invokeWorkbenchRule(compiled,state.type,'snapshot',{question:state.question});
  const input={question:state.question,answer:state.answer,maxScore:snapshot.maxScore??state.question.maxScore??state.question.scoreSpec?.defaultMaxScore??null};
  const validation=await invokeWorkbenchRule(compiled,state.type,'validate',{question:state.question});
  if(validation.errors?.length)throw new TypeError(validation.errors.join('; '));
  const answer=await invokeWorkbenchRule(compiled,state.type,'validateAnswer',input);
  if(answer.errors?.length||answer.empty)throw new TypeError(answer.errors?.join('; ')||'请先填写测试答案');
  const graded=await invokeWorkbenchRule(compiled,state.type,'grade',input);
  if(!['CORRECT','INCORRECT','UNSCORED'].includes(graded.status))throw new TypeError('扩展规则返回了无效判分状态');
  state.result={...graded,attemptId:`workbench-${state.history.length+1}`,attemptNo:state.history.length+1,attemptMode:'INITIAL'};
  state.history.push(clone({question:state.question,answer:state.answer,result:state.result}));state.tab='result';
  return state.result;
}
export async function workbenchProjection(compiled,state,resolveContent,value=state,showReference=false){
  const source=clone(value.question),{answerSpec,analysis,...publicQuestion}=source;
  function tree(node){if(Array.isArray(node))return node.map(tree);if(node&&typeof node==='object'){if(['TEXT','RICH','DOCUMENT'].includes(node.kind))return node.kind==='DOCUMENT'&&node.document?node:resolveContent(node);return Object.fromEntries(Object.entries(node).map(([k,v])=>[k,tree(v)]));}return node;}
  const type=compiled.manifest.types.find(t=>t.id===state.type),snapshot=await invokeWorkbenchRule(compiled,state.type,'snapshot',{question:source});
  const submitted=Boolean(value.result),revealed=submitted||showReference;
  return {questionId:source.id,sessionQuestionId:source.id,type:state.type,index:0,total:1,prompt:tree(source.prompt||{kind:'TEXT',text:''}),
    options:[],selectedOptionIds:[],state:submitted?'SUBMITTED':Object.keys(value.answer||{}).length?'DRAFT':'UNANSWERED',
    maxScore:snapshot.maxScore??source.maxScore??source.scoreSpec?.defaultMaxScore??null,
    presentation:{extensionId:compiled.manifest.id,extensionVersion:compiled.manifest.version,dataVersion:type.dataVersion,
      question:tree(publicQuestion),answer:tree(clone(value.answer||{})),reference:revealed?tree({answerSpec,analysis}):null,...(snapshot.data?{data:tree(snapshot.data)}:{})},
    result:submitted?{...clone(value.result),correctOptionIds:[],analysis:tree(analysis||{kind:'TEXT',text:''})}:null};
}
