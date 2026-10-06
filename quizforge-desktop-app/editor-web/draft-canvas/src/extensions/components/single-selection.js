import {element, RendererMode} from '../../shared/renderer/contract.js';
import {resultSection} from '../../shared/renderer/result.js';

/** Shared radio controls. Extensions supply values, answer mapping and their reference mapping. */
export function mountSingleSelection(form, initial, caps, config) {
  let question=initial, readOnly=caps.mode===RendererMode.READ_ONLY_HISTORY, disabled=false;
  const row=element('div','practice-options');form.append(row);
  const inputs=config.options.map(option=>{
    const label=element('label','practice-option'),input=element('input');
    input.type='radio';input.name=`answer-${question.questionId}`;input.value=String(option.value);
    label.append(input,element('span','practice-option-text',option.label));row.append(label);
    return {input,label,value:option.value};
  });
  function sync(){
    const selected=config.selected(question),correct=config.correct?.(question);
    for(const item of inputs){
      const checked=Object.is(selected,item.value);item.input.checked=checked;
      item.input.disabled=readOnly||disabled||question.state==='SUBMITTED';
      item.label.classList.toggle('selected',checked);
      item.label.classList.toggle('feedback-correct',question.state==='SUBMITTED'&&Object.is(correct,item.value));
      item.label.classList.toggle('feedback-incorrect',question.state==='SUBMITTED'&&checked&&!Object.is(correct,item.value));
    }
  }
  const change=event=>{
    const item=inputs.find(item=>item.input===event.target);
    if(!item||readOnly||disabled||question.state==='SUBMITTED'||!caps.canInteract()){sync();return;}
    Promise.resolve().then(()=>caps.answerChanged({answer:config.answer(item.value)})).catch(sync);
  };
  row.addEventListener('change',change);sync();
  return {
    update(next){question=next;sync();},
    getAnswerIntent(){const item=inputs.find(item=>item.input.checked);return {answer:item?config.answer(item.value):{}};},
    hasAnswer(){return inputs.some(item=>item.input.checked);},
    rejectAnswer:sync,
    setReadOnly(value){readOnly=readOnly||value;sync();},
    setInteractionMode(mode){disabled=mode==='DISABLED';sync();},
    focusTarget(id){if(id===(config.targetId||question.questionId)){row.tabIndex=-1;row.focus({preventScroll:true});return row;}return null;},
    renderResult(){return resultSection(question.result);},
    destroy(){row.removeEventListener('change',change);}
  };
}
