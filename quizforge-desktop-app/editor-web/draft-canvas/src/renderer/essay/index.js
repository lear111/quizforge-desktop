import { textContent } from '../choice/contract.js';
import { mountTextAnswers } from '../text-answer/renderer.js';
export const essayRenderer=Object.freeze({
 id:'builtin.essay.v1',questionType:'ESSAY',selectionMode:'LONG_TEXT',label:'作文题',
 parse(q) {
   if(q.state!=='SUBMITTED' && q.presentation?.reference!=null)throw new TypeError('Unsubmitted reference answer');
   return {prompt:textContent(q.prompt,'prompt'),options:[],available:new Set(),selectedOptionIds:[],selectionMode:'LONG_TEXT',
     presentation:{answer:textContent(q.presentation?.answer,'essay answer'),reference:q.presentation?.reference==null?null:textContent(q.presentation.reference,'reference')}};
 },
 mount(form,q,context) {
   return mountTextAnswers(form,q,{...context,fields:[{id:q.questionId,number:1,text:'正式答案',answer:q.presentation.answer,reference:q.presentation.reference}],
     plainPrompt:true,answerPlaceholder:'在这里输入作文',intent:inputs=>({essayText:inputs[0].value})});
 }
});
