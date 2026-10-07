import { QuestionRendererRegistry } from '../shared/renderer/registry.js';

/** Resolve display content recursively; persistence data and the caller's input stay detached. */
export function resolvePreviewContentTree(value,resolveContent) {
  if(Array.isArray(value))return value.map(item=>resolvePreviewContentTree(item,resolveContent));
  if(value&&typeof value==='object') {
    const content=value.kind==='TEXT'&&typeof value.text==='string'
      ||value.kind==='RICH'&&value.document&&typeof value.document==='object'
      ||value.kind==='DOCUMENT'&&typeof value.resourceId==='string'&&typeof value.text==='string';
    if(content)return resolveContent(value);
    return Object.fromEntries(Object.entries(value).map(([key,item])=>[key,resolvePreviewContentTree(item,resolveContent)]));
  }
  return value;
}

export function projectQuestionPreview(question,options={}) {
  const definition=QuestionRendererRegistry.require(question.type,options.extensionVersion);
  const check=definition.validateQuestion?.(question);
  return check?.then?check.then(()=>projectValidatedQuestion(question,options,definition)):projectValidatedQuestion(question,options,definition);
}
function projectValidatedQuestion(question,options,definition) {
  if(definition.projectPreview)return {...definition.projectPreview(question,options),rendererVersion:definition.extensionVersion};
  const snapshot=definition.snapshotQuestion?.(question)||{};
  return snapshot?.then?snapshot.then(value=>projectSnapshot(question,options,definition,value)):projectSnapshot(question,options,definition,snapshot);
}
function projectSnapshot(question,options,definition,snapshot) {
  const source=JSON.parse(JSON.stringify(question));
  const {answerSpec,analysis,...publicQuestion}=source;
  if(snapshot.publicPayload)publicQuestion.payload=snapshot.publicPayload;
  const manifest=QuestionRendererRegistry.extensions().find(m=>m.id===definition.extensionId&&m.version===definition.extensionVersion);
  const type=manifest.types.find(t=>t.id===source.type),show=options.showAnswers===true;
  const resolveContent=options.resolveContent||((value)=>value||{kind:'TEXT',text:''});
  const resolvedQuestion=resolvePreviewContentTree(publicQuestion,resolveContent);
  const maximum=snapshot.maxScore??source.maxScore??source.scoreSpec?.defaultMaxScore??null;
  return {questionId:source.id,sessionQuestionId:source.id,type:source.type,index:0,total:1,prompt:resolvedQuestion.prompt||resolveContent({kind:'TEXT',text:''}),
    maxScore:maximum,state:show?'SUBMITTED':'UNANSWERED',selectedOptionIds:[],options:[],
    presentation:{extensionId:manifest.id,extensionVersion:manifest.version,dataVersion:type.dataVersion,question:resolvedQuestion,
      answer:{},reference:show?resolvePreviewContentTree({answerSpec,analysis},resolveContent):null,
      ...(options.data===undefined?{}:{data:resolvePreviewContentTree(JSON.parse(JSON.stringify(options.data)),resolveContent)})},
    result:show?{status:'UNSCORED',score:null,maxScore:maximum,analysis:resolveContent(analysis||{kind:'TEXT',text:''}),correctOptionIds:[],attemptId:'preview',attemptNo:1,attemptMode:'INITIAL'}:null};
}
