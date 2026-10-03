import { RendererMode, requireRendererMode, element } from '../../shared/renderer/contract.js';
import { resultSection } from '../../shared/renderer/result.js';
/** TEXT answers are formal answers, independent of Draft Canvas geometry. */
export function mountTextAnswers(form,initial,{mode,contentRoot,canInteract,answerChanged,answerEdited,fields:providedFields,plainPrompt=false,answerPlaceholder='在这里输入译文',intent}) {
 requireRendererMode(mode);const history=mode===RendererMode.READ_ONLY_HISTORY;let q=initial,readOnly=history,interaction='INTERACT',destroyed=false,timer=null,dirty=false,saving=false,inFlight=null;
 const fields=providedFields || q.presentation.items;
 const prompt=element('p','practice-prompt');prompt.id='practice-prompt';
 // Sequential markers are presentation addresses. Stable target identity comes from Core items.
 const marker=/(\\)?\{\{([\s\S]*?)}}/g;let end=0,ordinal=0;
 for(const m of (plainPrompt?[]:q.prompt.text.matchAll(marker))){
   prompt.append(document.createTextNode(q.prompt.text.slice(end,m.index)));end=m.index+m[0].length;
   if(m[1]){prompt.append(document.createTextNode(m[0].slice(1)));continue;}
   const item=fields[ordinal++];if(!item)throw new TypeError('Translation target missing');const sentence=element('u','translation-sentence',`${item.number}. ${m[2]}`);sentence.dataset.targetId=item.id;prompt.append(sentence);
 }prompt.append(document.createTextNode(q.prompt.text.slice(end)));contentRoot.append(prompt);
 const area=element('div','text-answer-items');
 fields.forEach((item,n)=>{const section=element('section','text-answer-item');section.dataset.targetId=item.id;section.tabIndex=-1;
   const label=element('label','',`${item.number}. ${item.text}`);const input=element('textarea');input.id=`text-answer-${n}`;input.dataset.itemId=item.id;input.rows=5;input.value=item.answer.text;input.placeholder=answerPlaceholder;label.htmlFor=input.id;section.append(label,input);
   if(item.reference?.text && q.state==='SUBMITTED')section.append(element('p','practice-reference',`参考答案：${item.reference.text}`));area.append(section);
 });form.append(area);
 function alive(){if(destroyed)throw new Error('Question renderer destroyed');}
 function sync(){area.querySelectorAll('textarea').forEach(input=>{if(!dirty&&!saving)input.value=(q.presentation.items?.find(i=>i.id===input.dataset.itemId)?.answer || q.presentation.answer).text;input.disabled=readOnly||interaction!=='INTERACT'||q.state==='SUBMITTED';});}
 function getAnswerIntent(){alive();if(intent)return intent(Array.from(area.querySelectorAll('textarea')));const textAnswers={};area.querySelectorAll('textarea').forEach(i=>{if(i.value.trim())textAnswers[i.dataset.itemId]=i.value;});return {textAnswers};}
 function flushAnswer(){alive();clearTimeout(timer);timer=null;if(saving)return inFlight;if(!dirty)return Promise.resolve();const intent=getAnswerIntent();dirty=false;saving=true;inFlight=Promise.resolve(answerChanged(intent)).finally(()=>{saving=false;inFlight=null;});return inFlight;}
 const input=event=>{if(!event.target.matches('textarea'))return;if(destroyed||readOnly||interaction!=='INTERACT'||q.state==='SUBMITTED'||!canInteract()){sync();return;}dirty=true;answerEdited?.();clearTimeout(timer);timer=setTimeout(()=>{flushAnswer().catch(()=>{});},300);};
 if(!history)form.addEventListener('input',input);sync();
 return Object.freeze({
   update(next){alive();q=next;sync();},getAnswerIntent,flushAnswer,
   pendingAnswerIntent(){alive();return dirty?getAnswerIntent():null;},
   rejectAnswer(){alive();dirty=false;sync();},
   hasAnswer(){alive();return Array.from(area.querySelectorAll('textarea')).some(i=>i.value.trim());},
   focusTarget(id){alive();const node=Array.from(area.children).find(n=>n.dataset.targetId===id);if(node)node.focus({preventScroll:true});return node||null;},
   setInteractionMode(next){alive();if(!['INTERACT','DISABLED'].includes(next))throw new TypeError('Unknown interaction mode');interaction=next;sync();},
   setReadOnly(value){alive();if(history&&!value)throw new Error('History capability cannot be upgraded');readOnly=Boolean(value);sync();},
   renderResult(){alive();return resultSection(q.result);},
   destroy(){if(destroyed)return;destroyed=true;clearTimeout(timer);form.removeEventListener('input',input);area.querySelectorAll('textarea').forEach(i=>i.disabled=true);}
 });
}
