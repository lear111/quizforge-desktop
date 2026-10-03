import { readChoiceQuestion } from '../choice/contract.js';
import { RendererMode, requireRendererMode, element } from '../../shared/renderer/contract.js';
import { resultSection } from '../../shared/renderer/result.js';
export const matchingRenderer=Object.freeze({
 id:'builtin.matching.v1',questionType:'MATCHING',selectionMode:'ASSIGNMENT',label:'段落排序',
 parse(q) {
   const all=readChoiceQuestion({...q,selectionMode:'MULTIPLE',selectedOptionIds:[]},{selectionMode:'MULTIPLE'});
   const p=q.presentation;
   if(!Array.isArray(p?.slots) || !p.slots.length || typeof p.assignments!=='object' || !p.assignments)throw new TypeError('Missing matching presentation');
   const ids=new Set();const slots=p.slots.map(s=>{
     if(typeof s.id!=='string' || !s.id.trim() || ids.has(s.id) || !Number.isInteger(s.number) || typeof s.locked!=='boolean')throw new TypeError('Invalid assignment slot');ids.add(s.id);
     for(const id of [s.givenOptionId,s.selectedOptionId,s.correctOptionId])if(id!=null&&!all.available.has(id))throw new TypeError('Unknown assignment option');
     if(!['NONE','CORRECT','INCORRECT'].includes(s.feedback) || q.state!=='SUBMITTED' && s.feedback!=='NONE')throw new TypeError('Invalid slot feedback');
     if(q.state!=='SUBMITTED' && s.correctOptionId!=null)throw new TypeError('Unsubmitted correct assignment');
     return {...s};
   });
   const assignments={...p.assignments};for(const [id,option] of Object.entries(assignments))if(!ids.has(id)||!all.available.has(option))throw new TypeError('Unknown assignment');
   return {...all,selectionMode:'ASSIGNMENT',presentation:{slots,assignments}};
 },
 mount(form,initial,{mode,contentRoot,canInteract,answerChanged}) {
   requireRendererMode(mode);const history=mode===RendererMode.READ_ONLY_HISTORY;let q=initial,readOnly=history,interaction='INTERACT',destroyed=false;
   const prompt=element('p','practice-prompt',q.prompt.text);prompt.id='practice-prompt';contentRoot.append(prompt);
   const area=element('div','assignment-slots');const reserved=new Set(q.presentation.slots.filter(s=>s.locked).map(s=>s.givenOptionId));
   q.presentation.slots.forEach((slot,n)=>{
     const label=element('label',`assignment-slot feedback-${slot.feedback.toLowerCase()}`);label.dataset.targetId=slot.id;label.tabIndex=-1;
     label.append(element('span','',`${slot.number}.`));const select=element('select');select.dataset.slotId=slot.id;select.id=`assignment-slot-${n}`;select.setAttribute('aria-label',`第 ${slot.number} 个位置`);
     select.append(new Option('—',''));q.options.forEach(o=>{const option=new Option(o.content.text,o.id);option.disabled=reserved.has(o.id);select.append(option);});label.append(select);area.append(label);
   });form.append(area);
   function alive(){if(destroyed)throw new Error('Question renderer destroyed');}
   function sync(){area.querySelectorAll('select').forEach(input=>{const slot=q.presentation.slots.find(s=>s.id===input.dataset.slotId);input.value=slot.locked?slot.givenOptionId:(q.presentation.assignments[slot.id]||'');input.disabled=slot.locked||readOnly||interaction!=='INTERACT'||q.state==='SUBMITTED';});}
   function getAnswerIntent(){alive();const assignments={};area.querySelectorAll('select').forEach(i=>{if(!q.presentation.slots.find(s=>s.id===i.dataset.slotId).locked&&i.value)assignments[i.dataset.slotId]=i.value;});return {assignments};}
   const change=event=>{if(!event.target.matches('select'))return;if(destroyed||readOnly||interaction!=='INTERACT'||q.state==='SUBMITTED'||!canInteract()){sync();return;}Promise.resolve(answerChanged(getAnswerIntent())).catch(()=>{});};
   if(!history)form.addEventListener('change',change);sync();
   return Object.freeze({
     update(next){alive();q=next;sync();},getAnswerIntent,
     hasAnswer(){alive();return Object.keys(q.presentation.assignments).length>0;},
     focusTarget(id){alive();const node=Array.from(area.children).find(n=>n.dataset.targetId===id);if(node)node.focus({preventScroll:true});return node||null;},
     setInteractionMode(next){alive();if(!['INTERACT','DISABLED'].includes(next))throw new TypeError('Unknown interaction mode');interaction=next;sync();},
     setReadOnly(value){alive();if(history&&!value)throw new Error('History capability cannot be upgraded');readOnly=Boolean(value);sync();},
     renderResult(){alive();return resultSection(q.result);},
     destroy(){if(destroyed)return;destroyed=true;form.removeEventListener('change',change);area.querySelectorAll('select').forEach(i=>i.disabled=true);}
   });
 }
});
