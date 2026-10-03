import { textContent } from '../choice/contract.js';
import { mountTextAnswers } from '../text-answer/renderer.js';
export const translationRenderer=Object.freeze({
 id:'builtin.translation.v1',questionType:'TRANSLATION',selectionMode:'TEXT_FIELDS',label:'翻译',
 parse(q) {
   if(!Array.isArray(q.presentation?.items) || !q.presentation.items.length)throw new TypeError('Missing translation items');
   const ids=new Set();const items=q.presentation.items.map(i=>{
     if(typeof i.id!=='string'||!i.id.trim()||ids.has(i.id)||!Number.isInteger(i.number)||typeof i.text!=='string')throw new TypeError('Invalid translation identity');ids.add(i.id);
     if(q.state!=='SUBMITTED' && i.reference!=null)throw new TypeError('Unsubmitted reference answer');
     return {...i,answer:textContent(i.answer,'text answer'),reference:i.reference==null?null:textContent(i.reference,'reference')};
   });
   return {prompt:textContent(q.prompt,'prompt'),options:[],available:new Set(),selectedOptionIds:[],presentation:{items},selectionMode:'TEXT_FIELDS'};
 },
 mount:mountTextAnswers
});
