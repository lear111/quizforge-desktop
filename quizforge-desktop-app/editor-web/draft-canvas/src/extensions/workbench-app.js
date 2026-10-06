import {compileQuestionExtension,flushExtensionEditor} from './sdk.js';
import {prepareWorkbenchState,submitWorkbenchAnswer,workbenchProjection,normalizeWorkbenchAnswer,invokeWorkbenchRule} from './workbench-state.js';
import {validateWorkbenchRenderers} from './workbench-validation.js';
import {element,RendererMode} from '../shared/renderer/contract.js';
import {readContent,renderContent} from '../shared/renderer/content.js';
import '../practice/style.css';
import './workbench.css';
import {replaceChildrenRetainingFrames} from './protocol.js';

let active=null;
const container=document.querySelector('#extension-workbench');
function host(){return window.workbenchHost||window.editorHost;}
function resolveContent(content){const result=host()?.resolveContent?.(JSON.stringify(content));return result?JSON.parse(result):readContent(content,'workbench content');}
function editContent(content){const result=host()?.editContent?.(JSON.stringify(content));return result?JSON.parse(result):content;}
async function createCandidate(bundle,previous,options){
  // Local development may exercise declared capabilities against this disposable test session only.
  const grantedPermissions=Object.fromEntries(bundle.manifest.types.map(type=>[type.id,type.permissions||[]]));
  const compiled=compileQuestionExtension({...bundle,grantedPermissions});let state;
  try{await compiled.ready;state=await prepareWorkbenchState(compiled,previous,options);await validateWorkbenchRenderers(compiled,state,resolveContent);}
  catch(error){compiled.rules.destroy?.();throw error;}
  const shell=element('section','extension-workbench-shell'),navigation=element('nav','workbench-tabs'),card=element('div','workbench-card'),message=element('p','workbench-message');
  shell.append(navigation,message,card);message.textContent=state.notice;
  let instance=null,editor=false,destroyed=false;
  const tabLabels={editor:'编辑',practice:'练习',result:'结果',history:'历史',preview:'网页预览'};
  const setError=error=>{message.textContent=error.message||String(error);message.dataset.error='true';};
  function snapshotEditor(){if(editor&&instance?.getDraft)state.question=instance.getDraft();}
  async function flush(){if(instance?.hasInitializationError?.()){snapshotEditor();return;}if(editor){await flushExtensionEditor(instance,()=>{snapshotEditor();});}else await instance?.flushAnswer?.();}
  async function render(){
    snapshotEditor();instance?.destroy?.();instance=null;editor=false;replaceChildrenRetainingFrames(card);
    navigation.querySelectorAll('button[data-tab]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.tab===state.tab)));
    if(state.tab==='editor'){
      const definition=compiled.editors.find(d=>d.questionType===state.type);if(!definition)throw new TypeError('扩展尚未提供编辑器');
      editor=true;instance=definition.mount(card,state.question,{element,renderContent,readContent,resolveContent,editContent,reloadPage:render,changed(q){state.question=JSON.parse(JSON.stringify(q));}});return;
    }
    const readonly=['result','history','preview'].includes(state.tab),definition=compiled.renderers.find(d=>d.questionType===state.type);
    let value=state;
    if(state.tab==='history'){
      if(!state.history.length){card.append(element('p','','暂无测试历史。请在练习页提交一次答案。'));return;}
      value=state.history[state.history.length-1];
    }
    if(state.tab==='result'&&!state.result){card.append(element('p','','暂无测试结果。请先在练习页提交答案。'));return;}
    const data=state.tab==='preview'?{question:state.question,answer:{},result:null}:value;
    let dto=await workbenchProjection(compiled,state,resolveContent,data);
    const parsed=definition.parse(dto);dto={...dto,...parsed};
    const heading=element('div','card-heading');heading.append(element('span','tag',definition.label||compiled.manifest.types.find(t=>t.id===state.type).label));
    const form=element('form'),contentRoot=document.createDocumentFragment();contentRoot.append(heading);
    let submit;
    instance=definition.mount(form,dto,{layoutRoot:card,mode:readonly?RendererMode.READ_ONLY_HISTORY:RendererMode.ACTIVE,preview:state.tab==='preview',contentRoot,
      reloadPage:render,
      canInteract:()=>!readonly&&!state.result,
      async answerChanged(intent){if(readonly||state.result)throw new Error('此页只读');
        const answer=normalizeWorkbenchAnswer(compiled,state,intent),checked=await invokeWorkbenchRule(compiled,state.type,'validateAnswer',{question:state.question,answer});
        if(checked.errors?.length)throw new TypeError(checked.errors.join('; '));
        dto=await workbenchProjection(compiled,state,resolveContent,{...state,answer});const parsed=definition.parse(dto);
        state.answer=answer;instance.update({...dto,...parsed});if(submit)submit.disabled=!instance.hasAnswer();},answerEdited(){if(submit)submit.disabled=!instance.hasAnswer();}});
    instance.setReadOnly(readonly);instance.setInteractionMode(readonly||state.result?'DISABLED':'INTERACT');contentRoot.append(form,instance.renderResult());card.append(contentRoot);
    if(state.tab==='practice'){
      const actions=element('div','practice-actions');submit=element('button','practice-submit',state.result?'重新测试':'提交测试答案');submit.type='button';submit.disabled=!state.result&&!instance.hasAnswer();actions.append(submit);card.append(actions);
      submit.addEventListener('click',()=>{
        if(state.result){state.answer={};state.result=null;render();return;}
        Promise.resolve(instance.flushAnswer?.()).then(async()=>{state.answer=normalizeWorkbenchAnswer(compiled,state,instance.getAnswerIntent());await submitWorkbenchAnswer(compiled,state);return render();}).catch(setError);
      });
    }
    form.addEventListener('submit',event=>event.preventDefault());
  }
  for(const [tab,label]of Object.entries(tabLabels)){const button=element('button','',label);button.type='button';button.dataset.tab=tab;navigation.append(button);button.addEventListener('click',()=>flush().then(async()=>{state.tab=tab;return render();}).catch(setError));}
  if(compiled.manifest.types.length>1){
    const select=element('select');select.id='workbench-type';select.setAttribute('aria-label','预览题型');
    for(const type of compiled.manifest.types){const option=element('option','',type.label);option.value=type.id;select.append(option);}select.value=state.type;navigation.prepend(select);
    const drafts=new Map();
    select.addEventListener('change',()=>{const selected=select.value;flush().then(async()=>{
      drafts.set(state.type,JSON.parse(getState()));const next=await prepareWorkbenchState(compiled,drafts.get(selected)||null,{type:selected,revision:state.revision});
      instance?.destroy?.();instance=null;editor=false;state=next;return render();
    }).catch(error=>{select.value=state.type;setError(error);});});
  }
  await render();
  function getState(){snapshotEditor();return JSON.stringify(state);}
  return {shell,get state(){return state;},compiled,flush,getState,destroy(){if(destroyed)return;destroyed=true;instance?.destroy?.();compiled.rules.destroy?.();shell.remove();}};
}
window.extensionWorkbench=Object.freeze({
  async load(bundle,previous=null,options={}){
    const candidate=await createCandidate(typeof bundle==='string'?JSON.parse(bundle):bundle,previous,options);
    const staging=element('div');staging.style.cssText=`position:absolute;left:0;top:0;width:${container.clientWidth||window.innerWidth}px;visibility:hidden;pointer-events:none;`;staging.append(candidate.shell);container.append(staging);
    try{
      await candidate.flush();active?.destroy();active=candidate;
      // Reparenting a live iframe reloads its document and resets the SDK session.
      // Keep the staging ancestor in place when committing the ready preview.
      for(const node of [...container.childNodes])if(node!==staging)node.remove();
      staging.removeAttribute('style');staging.style.display='contents';
    }
    catch(error){candidate.destroy();throw error;}
    finally{if(active!==candidate)staging.remove();}
    return JSON.stringify({status:'READY',revision:candidate.state.revision,type:candidate.state.type});
  },
  getState(){if(!active)throw new Error('Workbench has not loaded an extension');return active.getState();},
  async flush(){if(!active)throw new Error('Workbench is not loaded');await active.flush();return active.getState();},
  flushToHost(id){this.flush().then(state=>host()?.flushed?.(id,state),failure=>host()?.flushFailed?.(id,failure.message||String(failure)));},
  showError(message){const node=container.querySelector('.workbench-message');if(node){node.textContent=message;node.dataset.error='true';}},
  diagnostics(){return active?JSON.stringify({revision:active.state.revision,type:active.state.type,tab:active.state.tab,historyCount:active.state.history.length}):'null';},
  destroy(){active?.destroy();active=null;}
});
