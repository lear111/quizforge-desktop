import {prepareWorkbenchState,workbenchProjection} from './workbench-state.js';
import {element,RendererMode} from '../shared/renderer/contract.js';

/** Probe every declared renderer before accepting a source revision into the visible workbench. */
export async function validateWorkbenchRenderers(compiled,state,resolveContent){
  for(const type of compiled.manifest.types){
    const sample=type.id===state.type?state:await prepareWorkbenchState(compiled,null,{type:type.id});
    const definition=compiled.renderers.find(d=>d.questionType===type.id);
    const original=await workbenchProjection(compiled,sample,resolveContent,{question:sample.question,answer:{},result:null});
    const q={...original,...definition.parse(original)},form=element('form'),contentRoot=document.createDocumentFragment();
    let instance;
    try {
      instance=definition.mount(form,q,{mode:RendererMode.READ_ONLY_HISTORY,preview:true,contentRoot,
        canInteract:()=>false,answerChanged:()=>Promise.reject(new Error('Renderer probe is read-only')),answerEdited(){}});
      for(const method of ['update','getAnswerIntent','hasAnswer','setReadOnly','setInteractionMode','renderResult','destroy'])
        if(typeof instance?.[method]!=='function')throw new TypeError(`Renderer is missing ${method}: ${type.id}`);
      instance.setReadOnly(true);instance.setInteractionMode('DISABLED');instance.renderResult();
    }finally{instance?.destroy?.();}
  }
}
